# Issues to check in other Minecraft versions

This records the fixes made in the 1.21.4 repository during the review cleanup and subsequent GameTest troubleshooting. For each other Minecraft version, check whether the issue exists before applying the remedy. Class names, loader facilities, advancement formats, and API signatures may differ. The code in that checkout is authoritative; this is a checklist, not a requirement to reproduce this checkout's implementation verbatim.

Review references below identify the original findings. The inventory-copying finding appeared in two reviews and is recorded once here. Quoted project instructions and user feedback are included where relevant; the review findings themselves are not attributed to the user.

## 1. Prepared item displays retained every distinct stack indefinitely

**Issue:** `CubeRenderHelper.PREPARED` was an unbounded static map keyed by item, component patch, and count. Historical stacks retained their prepared models or captured quads until a later rendering call noticed profile invalidation. Removing a displayed item or leaving the area did not evict its entry. Resource reload invalidated profiles but did not immediately release the prepared entries.

**Fix:** Replaced the map with an access-ordered `LinkedHashMap` that evicts its eldest entry above the named `MAX_PREPARED_ITEMS` capacity of 2,048. Kept components and count in the key. Added an immediate `PREPARED.clear()` in `onResourceReload` and corrected the cache documentation to account for eviction. No runtime measurements or particular lag threshold were established.

**Reference:** `review-Efficiency.md`, finding 1; `CubeRenderHelper`.

## 2. Each slot mutation rescanned the whole block for comparator fill

**Issue:** `StackBlockEntity.onContentsChanged(slot)` recalculated every local slot's comparator fraction before checking whether publication was being batched. Multi-slot settlement, gravity, and cascades repeatedly recounted intermediate inventories.

**Fix:** Added a per-slot contribution array. A mutation refreshes only the changed slot's fraction and marks the cached total dirty. The next comparator read sums those cached fractions and caches the result; subsequent reads reuse it until another mutation. Disk and accepted client snapshot loads rebuild all contributions. This implementation uses on-demand summation rather than repeatedly subtracting and adding floating-point fractions, avoiding cumulative rounding drift while preserving correct reads during batches.

**Project instruction:** “Work happens when something changes, not on a schedule.”

**Reference:** `review-Efficiency.md`, finding 2; `StackBlockEntity`.

## 3. A Bar insertion probe copied all 64 occupancy positions

**Issue:** `BarColumn.positionAccepts` built a whole-block occupancy snapshot before even rejecting an occupied target. `BarCubeIdx.isGrounded` also copied all positions when support depended only on the preceding layer or the block seam.

**Fix:** Reject occupied targets by reading the target slot directly. The live-slot support check now reads only the preceding eight-position layer. Existing-block insertion and player deposit checks request a seam only for bottom-layer positions. Fresh-block support checks directly test the bottom layer and seam without allocating an empty 64-position snapshot. Kept occupancy snapshots for cascade and proposed-state calculations that actually need them.

**Reference:** `review-Efficiency.md`, finding 3; `BarColumn`, `BarCubeIdx`, `BarStackBE.canDepositAt`.

## 4. Fabric used a custom mixin solely to own an automation adapter

**Issue:** `StackBlockEntityMixin` injected a cached `FabricRunItemStorage` field into the mod's own block entity class. Adapter ownership depended on that mixin, a `FabricStorageOwner` bridge interface, three sided registrations, and a separate base-ledger lookup.

**Fix:** Registered one transient `AttachmentType<FabricRunItemStorage>` through Fabric's `AttachmentRegistry`. `FabricItemStorage.of` retrieves or creates the adapter attached to its owner. All three sided registrations and the run's base-ledger lookup use that accessor, preserving one adapter per block entity and a shared transaction ledger for all views of a run. Deleted the bridge interface, ownership mixin, and its mixin configuration entry. The attachment is not persisted, synchronized, or copied.

**Project instruction:** “Boring beats clever. Do things the vanilla, Forge, NeoForge, and Fabric way: if Minecraft or the loader has a facility for a goal, use it”.

**Cross-version check:** Confirm that the target Fabric API supplies the attachment facility and the required signatures. Do not introduce Fabric dependencies into common code.

**Reference:** `review-Expected-way.md`, finding 1; `FabricItemStorage`, `FabricRunItemStorage`, `somestacks.mixins.json`.

## 5. Inventory copying duplicated Minecraft's own operation

**Issue:** `StackItemStorage.copyWithSize` copied a stack and changed its count, duplicating `ItemStack.copyWithCount` at four insertion/extraction call sites.

**Fix:** Replaced the four calls with `copyWithCount` on their source stacks and deleted the helper. Existing inventory ownership and remainder behavior remain in their original branches.

**Reference:** `review-Expected-way.md`, finding 2, and `review-Reinvention.md`, finding 2; `StackItemStorage`.

## 6. Storage settlement invented a fallback maximum stack size

**Issue:** Settlement used `Math.max(1, group.model.getMaxStackSize())`, silently substituting one for a nonpositive Minecraft stack limit. Other inventory paths used the supplied limit directly, so settlement contained an isolated defensive policy.

**Fix:** Use `group.model.getMaxStackSize()` directly when splitting settled item groups.

**Project instruction:** “When assessing code for safety issues, do not worry that Minecraft or the loader itself may misbehave; it is not our job to protect against every conceivable error.”

**Reference:** `review-Guarding.md`, finding 1; `StoragePile.settle`.

## 7. Command completion contained an unused implementation

**Issue:** `SsCommand.suggestItems` had no callers, while registered item arguments already used Brigadier resource arguments or the active `suggestEntries` path. `ARG_ENTRY` was also unused, and the `Collectors` import served only the disconnected helper.

**Fix:** Deleted `suggestItems`, `ARG_ENTRY`, and the unused import. Kept the registered argument and suggestion paths.

**Reference:** `review-Dead-code.md`, finding 1; `SsCommand`.

## 8. Unreachable geometry helpers duplicated active entry points

**Issue:** Private `SinglesCubeIdx.getCubeBox` was unused and was the sole caller of `CubeGrid.worldBoxAtVisual`. Active targeting and shapes already used rotation-aware grid methods.

**Fix:** Deleted both disconnected methods and the now-unused `AABB` import in `SinglesCubeIdx`. Kept `worldBox`, `localBox`, and `localShape`.

**Reference:** `review-Dead-code.md`, finding 2; `SinglesCubeIdx`, `CubeGrid`.

## 9. Test-only extraction overloads hid the cleanup actor

**Issue:** `StorageStackBE.extractAt(int, int, ItemStack)` and `BarStackBE.extractAt(int)` merely forwarded to actor-aware overloads with `null`. Only tests used these extra signatures, concealing that cleanup was attributed to automation.

**Fix:** Deleted both overloads and changed their common and loader-local test callers to pass `null` explicitly. Kept the actor-aware implementations and their extraction documentation. Retained Singles' corresponding overload because `SinglesColumn.extract` actually uses it in production automation.

**Project instruction:** “Each concept has one home. A behavior lives in exactly one place”.

**Reference:** `review-Legacy.md`, finding 1; `StorageStackBE`, `BarStackBE`, Storage/Bar/protection GameTests.

## 10. Singles layout rotation documentation promised nonexistent batching

**Issue:** `SinglesStackBE.setRotation` said publication waited while a batch was open. In fact, it invalidated the shape and called `setBlockRotation`, whose server-side block-state update does not consult inventory batching. Per-item rotation has a different batching rule.

**Fix:** Corrected the Javadoc to describe immediate server-side outer block rotation, including during inventory batches, and linked to `setBlockRotation`. Did not change rotation behavior to match the incorrect comment.

**Reference:** `review-Javadocs.md`, finding 1; `SinglesStackBE.setRotation`, `StackBlockEntity`.

## 11. Shared APIs lacked nontrivial operating contracts

**Issue:** Configuration lifecycle methods, run-wide automation methods, and the grid constructor left important rules unstated, requiring callers to infer them from implementations.

**Fix:** Documented backend installation and world-load ordering; applying versus saving or reloading policy; backend-specific file/published-value loading; `reload()` returning backend availability rather than parse success; and callers' responsibility for validating `Settings`. Documented JSON loader installation/default-file behavior. Added handler contracts for bottom-up run positions, headroom, unavailable-run results, live read-only contents, simulation, input ownership, remainders, reentrant committed edits, and admission-only `isItemValid`. Documented `CubeGrid` model-pixel units, axis-start count, caller responsibilities, and explicit constructor rejection conditions.

**Reference:** `review-Javadocs.md`, finding 2; `ServerConfig`, `JsonServerConfig`, `RunItemHandlerBase`, `CubeGrid`.

## 12. Bar/Singles classification documentation described the wrong authority

**Issue:** Singles documentation referred to a configured ingot list even though classification reads the reloadable `somestacks:ingots` item tag. The `INGOTS` field comment also described namespace filtering performed by the following method, not by tag membership.

**Fix:** Changed Singles documentation to identify the data-pack tag. Gave `INGOTS` a classification-only field comment and `isValidBarItem` its own contract covering empty-stack and disabled-namespace rejection.

**Reference:** `review-Javadocs.md`, finding 3; `SinglesStackBE.isValidSinglesItem`, `BarStackBE.INGOTS`, `BarStackBE.isValidBarItem`.

## 13. Removed height accessors left orphan Javadocs

**Issue:** `StoragePile`, `SinglesColumn`, and `BarColumn` each contained a standalone height-ceiling comment immediately followed by another Javadoc for the boolean `columnHasRoomFor` method. The height comment documented no declaration.

**Fix:** Deleted the three orphan comments and retained the placement predicate contracts.

**Reference:** `review-Javadocs.md`, finding 4.

## 14. A Bar deposit predicate had a seam-returning contract and nullable annotation

**Issue:** Adjacent to the reviewed Bar support code, `canDepositAt` had a Javadoc describing a returned support seam and an `@Nullable` annotation despite returning primitive `boolean`.

**Fix:** Documented the actual valid/empty/supported-position predicate and removed the nullable annotation. Expanded the actual `supportSeamBeneath` contract to explain its occupancy array and the meaning of a null seam.

**Reference:** `BarStackBE.canDepositAt`, `BarStackBE.supportSeamBeneath`; additional documentation correction found during implementation.

## 15. World-specific policy readiness had no common startup milestone

**Issue:** Loader initialization messages preceded world policy loading. `SomeStacksServer.onServerStarting` loaded policy and built the cached login snapshot without announcing completion, leaving admins without confirmation of effective enable flags and height.

**Fix:** Added one common INFO message immediately after `ConfigSyncPkt.rebuildCurrent`, reporting policy/snapshot readiness, the three effective stack enable flags, and maximum pile height. Did not add per-player, per-packet, or per-interaction logging.

**Project instruction:** “logs record milestones, not traffic”.

**Reference:** `review-Logging.md`, finding 1; `SomeStacksServer.onServerStarting`.

## 16. Targeting and rendering repeated cell dimensions

**Issue:** Storage's cell width and Singles' cell width appeared independently in geometry construction and renderer calculations. Adjusting a width required coordinated edits. No existing visual mismatch was demonstrated. The magic-numbers review retained this claim in its summary but had no numbered finding 1 body.

**Fix:** Introduced `CELL_PIXELS` constants in `StorageCubeIdx` and `SinglesCubeIdx`; grid construction and `CubeRenderHelper` scale/size calculations now derive from them. Preserved the existing dimensions and presentation.

**Reference:** `review-Magic-numbers.md`, summary and drift text; `StorageCubeIdx`, `SinglesCubeIdx`, `CubeRenderHelper`.

## 17. Ordinary type references used full package names throughout code

**Issue:** Production and GameTest expressions repeatedly spelled out types such as `Function`, `Map`, `IdentityHashMap`, `BlockType`, `ModConfig`, and configuration adapters instead of using imports. This obscured the actual operations, especially in native configuration tests.

**Fix:** Added ordinary imports and replaced unnecessary qualified references across common and loader code/tests. Retained useful nested references and qualified Javadoc links. Consolidated and ordered imports in edited files.

**Reference:** `review-Magic-numbers.md`, finding 2.

## 18. Custom player placement omitted the placed-block advancement trigger

**Issue:** The player gesture path placed a block, ran loader placement hooks, emitted the placement game event, and made its first deposit without notifying the advancement criterion. Loader placement events and vibration game events do not replace the advancement notification.

**Fix:** In `StackInteractions.placeAndDeposit`, copy the original held stack before placement/deposit can mutate it and invoke `CriteriaTriggers.PLACED_BLOCK.trigger(player, pos, originalHeldStack)` after successful placement and deposit. Kept the call in the player creation path, outside general world editing and pile growth. Added a development-only advancement fixture requiring a Storage block and an original offer of exactly two Dirt. The shared check covers blocked placement, successful creation that empties the hand, and growth of a full pile without a creation award; all loaders register it.

**Project instruction:** “Anything the mod does to the world on a player's behalf passes the same permission checks and fires the same events vanilla would if the player did it by hand”.

**Cross-version check:** Verify the target version's trigger signature and advancement predicate format. The added regression case exercises Storage; the production notification follows the shared placement switch for all three stack types.

**Reference:** `review-Reinvention.md`, finding 1; `StackInteractions`, `InteractionChecks.playerPlacementAwardsCriterionAfterDeposit`, `gametest_place_storage.json`.

## 19. Client profile precedence and invalidation had no behavior check

**Issue:** Tests checked override JSON decoding but did not exercise the actual `ItemRenderOverrides` layer selection or its resolved-profile cache. Reversed precedence and stale results after edits, removal, or resource replacement could pass the suite.

**Fix:** Added `RenderProfileChecks`, invoked by `/ssclienttest`. It resolves deliberately different complete user/resource profiles, changes a user profile, checks a mode-only user entry uses default scale/offset rather than lower-layer fields, removes the user entry, and applies a replacement resource profile. Each transition resolves again. The check restores the prior resource map and selected in-memory user entry without writing settings. Updated client test messages and reproduction Javadocs. The test reads the private user entry reflectively only to preserve it for restoration; mutations use the real lifecycle methods.

**Project instruction:** “every player-visible behavior has a gametest whose javadoc tells a person how to reproduce it in-game”.

**Reference:** `review-Test-completeness.md`, finding 1; `RenderProfileChecks`, `ClientCallbackChecks`, development-mod language resource.

## 20. The gallery pacing test did not measure the configured budget

**Issue:** The test assumed the default placement budget and checked only that a sufficiently large gallery took multiple ticks. It did not detect a hard-coded budget, excessive per-tick work, or separate budgets for queued jobs.

**Fix:** Replaced delayed timing assertions with synchronous calls to the real scheduler using a nondefault budget of four. Two separated one-stack jobs each require nine floor placements and one stack placement. Exact floor counts, stack presence, and completion are checked after each call, including the floor-to-stack transition and the tick shared between jobs. All loader delegates put this test in a separate native GameTest batch. Cleanup drains its work and restores policy in `finally`, including restoration if draining throws.

**Reference:** `review-Test-completeness.md`, finding 2; `RenderGalleryChecks.queuedStorageGallerySpreadsWorkAcrossTicks`, loader `RenderGalleryGameTests` delegates.

## 21. Eager Bar auto-tinting depended on resource reload order

**Issue:** `BarTextureStore.apply` captured item models and sprite colors while applying resource data. The inspected registrations did not establish that the new models and atlas were ready first. This began as an open ordering question, not a reproduced rendering failure.

**Fix:** Resource application now stores the mappings and clears tint caches without capturing models. `getTexture` derives an authored mapping's requested auto-tint on first render and replaces that entry with its resolved tint. Unmapped items retain their existing first-use tint cache. Explicit authored colors remain final. This removes model capture from the resource-application stage rather than relying on unverified loader ordering. Updated logging and documentation accordingly.

**Reference:** `review-Expected-way.md`, open question 1; `BarTextureStore.apply`, `BarTextureStore.getTexture`.

## 22. The new client check created a split package between Java modules

**Issue:** The first implementation put `RenderProfileChecks` in the production `com.github.crittscott.somestacks.client` package even though it belonged to the separate GameTest mod. Compilation succeeded, but NeoForge's module resolution rejected the two modules exporting that package before any tests could run.

**User feedback:** “builds succeed. gametests fail”. The supplied log reported: `Modules somestacks_gametest and somestacks export package com.github.crittscott.somestacks.client to module mixin_synthetic`.

**Fix:** Moved `RenderProfileChecks` into `com.github.crittscott.somestacks.gametest`, imported the production types, and removed the obsolete import from `ClientCallbackChecks`. Its subclass relationship already provides the protected resource-application access it needs. A source-package comparison found no remaining production/GameTest package overlap. The user's next log showed the server running the tests, confirming that the startup obstruction was cleared.

**Cross-version check:** Keep separate development mods out of production packages wherever Java modules enforce package ownership. Do not change the build to merge the mods merely to accommodate this test class.

## 23. The advancement check used an automation fake player's tracker

**Issue:** The new placement-advancement check used the loader's general fake-player factory. NeoForge automation fake players have specialized advancement behavior, making that actor unsuitable for testing ordinary player criterion awards.

**User feedback:** The next supplied NeoForge run completed 124 tests with one failure: `playerplacementawardscriterionafterdeposit` — `Player placement did not award the original-offer criterion`.

**Fix:** Gave this shared test its own regular `ServerPlayer`, using the already-established constructor pattern and suppressing unobserved action-bar messages. Removed its player-factory parameter and updated all three loader delegates. Added a direct placed-block control trigger against the fixture before testing real placement, so fixture/tracker failures have a distinct assertion. Stop the tracker listening and clear gesture/hand state in `finally`. Production placement code was not changed in this follow-up.

**Cross-version check:** Use an actor with ordinary advancement tracking for criterion tests. Retain loader fake players where automation identity is actually the behavior being tested.

**Verification limit:** The harness change was source-inspected; no successful rerun after this change has been supplied or performed by the agent.

## 24. Orientation snapshots needed to describe the resulting implementation

**Issue:** The existing snapshots would otherwise continue to describe unbounded preparation lifetime and omit attachment ownership, lazy tinting, advancement notifications, and expanded development checks. The build snapshot also incorrectly said Fabric neighbor deposits required direct clicks without Common Protection API, although the current authority consulted destination block-use callbacks.

**Fix:** Updated all three orientation files to reflect the code, including cached comparator fractions, bounded displays, transient attachments, player creation criteria, first-render tinting, client profile checks, and the isolated gallery-budget batch. Corrected the Fabric neighbor-deposit description. Trimmed prose to keep each file within its existing content limit while preserving the fixed headers. No active build files or `build-env` inputs changed.

**Project instruction:** “The code is the truth” and “After a change alters what a file describes, update that file in place to match the code.”

## 25. Windows-default text decoding damaged two existing comments during scripted editing

**Issue:** An editing script initially read some files using Windows' default text encoding and wrote UTF-8. Existing em dashes in `StoragePile` and `BarStackBE` became mojibake. This was an editing mistake introduced during this work, not an original mod defect.

**Fix:** Restored the affected comment text to valid UTF-8 and used explicit UTF-8 reads/writes for subsequent scripts. Other-version cleanup scripts should specify their encoding rather than rely on the host default.

## Verification record

The user reported successful builds. After the split-package correction, the supplied NeoForge run reached all 124 GameTests and reported only the advancement check as failing. The advancement harness was then changed as described above; its result remains unconfirmed. Client `/ssclienttest` results have not been supplied. The agent did not compile, build, run the project, or execute GameTests. Verification performed here was source inspection, edited-JSON parsing, package-overlap inspection, and orientation size checks.

No saved-world format changes, migration changes, build-system changes, commits, or GitHub operations were part of these fixes.
