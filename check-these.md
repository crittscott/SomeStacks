# Review fixes: handoff for the 26.* projects

This is the standalone running record of review findings verified in `26.3`, their full original issue text, and the fixes actually implemented. Other `26.*` projects are candidates for the same changes, but have not been reviewed or changed as part of this batch. Check each target's code and loader APIs before porting a fix.

## How to use this handoff

- Read the target project's `AGENTS.md` and `CLAUDE.md` before editing it. Its `orientation-player.md`, `orientation-code.md`, and `orientation-build.md` describe its current state; the code takes precedence.
- Each issue below contains the original finding in full, so its review report is not needed. The verification and resolution immediately above the quotation govern what to port. Original actions, severity, confidence, and performance claims are retained as review evidence, not adopted requirements.
- Paths in original findings are relative to the version project's root. Original line numbers describe the reviewed code before the fixes and may be stale; locate the named class and method. Deleted files may still exist in an unpatched target.
- IDs combine the review category and its original finding number: `DC` means dead code/duplication, `EF` efficiency, and `EW` expected vanilla/loader mechanisms. Gaps in numbering belong to the original reports. Retain these IDs when a later report overlaps an issue.
- Preserve partial and rejected dispositions. Do not apply an original recommendation that the verification narrowed or rejected. Deferred items remain explicit decisions, not completed fixes.
- In `26.3`, the user runs Gradle, builds, tests, Git, and GitHub operations. Do not decompile or unarchive anything, inspect bytecode, or read decompiled/remapped Minecraft or loader sources or Loom caches. Verify edits by reading the code; request target API information from the user or permitted published documentation when needed.
- Add later batches to this file with their issue text, verification, implemented mechanism, exceptions, and validation. Do not replace existing decisions with links to review reports.

## Batch 1: dead code, efficiency, and expected way

Validation: the user reports that builds and GameTests succeed after this batch. The agent did not run builds or tests. A result for the development-client `/ssclienttest` checks has not been reported. Performance improvements are inferred from eliminated work, not benchmarked. This validation applies to `26.3`; validate each other target separately.

The three original reports contributed 23 numbered findings. Their complete finding text is copied below, including recommendations that verification qualified or rejected. Report summaries with references to absent findings are not additional verified issues.

### Dead code and duplication

#### DC-01: Two client click rules do nothing, and copy the server's targeting logic

**Verified finding:** The two client deposit rules repeated targeting work but had the same cancellation result as the generic modified-click rule.

**Fix and disposition:** Deleted `DepositIntoClickedStackRule` and `DepositIntoAdjacentStackRule`. Removed the client context's unused targeting data and updated both loaders' callers. Gesture checks now assert cancellation rather than which deposit rule won. Target and cell selection remain server-side.

**Original issue text (verbatim):**

> ### 1. Two client click rules do nothing, and copy the server's targeting logic
> - **Severity:** Medium
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/client/interaction/DepositIntoClickedStackRule.java:18-54; common/src/main/java/com/github/crittscott/somestacks/client/interaction/DepositIntoAdjacentStackRule.java:12-26; common/src/main/java/com/github/crittscott/somestacks/client/interaction/InteractionRuleRegistry.java:30-31; duplicate of common/src/main/java/com/github/crittscott/somestacks/server/StackInteractions.java:180-198
> - **In plain English:** When you click with the modifier key held, the game on your computer runs two "should this go into the clicked stack or next to it?" checks. Both answers lead to exactly the same result, because the server makes the real decision. The checks also repeat the server's own aiming calculation, but aim a slightly different way.
> - **Consequence:** A maintainer who changes how deposits target cells has to keep two copies in sync, and the client copy already uses a different ray (`ViewRays.of`, the look direction) from the server (`ViewRays.through`, the vanilla hit point). Only the gametests (`ClientGestureChecks`) can tell which rule won, so the work is wasted on every modified right-click and the tests pin down behavior that doesn't exist.
> - **Action:** Delete `DepositIntoClickedStackRule` and `DepositIntoAdjacentStackRule` and remove them from `BLOCK_RULES`. `PlaceAdjacentGenericRule` already matches every click they match and has the same effect (`cancelEvent()`). Update `ClientGestureChecks` to assert that the click is cancelled, not which rule claimed it.
> - **Technical detail:** `PlaceAdjacentGenericRule.matches` is `isVDown && !isShift && hasItemInHand && canPlaceOrDeposit`. Both deposit rules require those same four conditions plus more, and all three `execute` methods only call `ctx.cancelEvent()`. The production callers (`ClientEvents`, `FabricClientEvents`, `MinecraftMixin`) ignore the rule returned by `processBlockRules`, so it makes no difference which of the three wins.

#### DC-05: Saved-data field names live in many places, and some checks can never trigger

**Verified finding:** Persisted field names were repeated across storage, block entities, and migration.

**Fix and disposition:** Exposed the owning constants and used them in migration field collection, saving, and client validation. Retained rejection of top-level `SetAside`: malformed wire input can contain it, and removing it from raw field collection would hide it from validation. The review's claim that this check cannot trigger was rejected.

**Original issue text (verbatim):**

> ### 5. Saved-data field names live in many places, and some checks can never trigger
> - **Severity:** Medium
> - **Confidence:** Likely
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/block/StackDataMigration.java:34-36, 70-72; common/src/main/java/com/github/crittscott/somestacks/block/StackBlockEntity.java:31, 376-380, 405; common/src/main/java/com/github/crittscott/somestacks/block/StorageStackBE.java:32; common/src/main/java/com/github/crittscott/somestacks/block/SinglesStackBE.java:39; common/src/main/java/com/github/crittscott/somestacks/util/StackItemStorage.java:34-35
> - **In plain English:** The names under which a stack saves its contents ("Items", "Permanent", "DataVersion" and so on) are typed out separately in five files. One list also names a field at the wrong level, so the check for it never fires.
> - **Consequence:** Adding or renaming a saved field means finding every copy. If `PERSISTED_FIELDS` is missed, `readTag` silently drops the field on load and the player's world loses that data. A maintainer reading `loadClientUpdate` would believe it guards against a top-level `SetAside`, but no such field exists.
> - **Action:** Make the tag-name constants public on their owning classes, and build `PERSISTED_FIELDS` from them, or let each subtype contribute its own names. Replace the `"Permanent"` and `"DataVersion"` literals with those constants. Use one data-version mechanism (`NbtUtils.addCurrentDataVersion`/`getDataVersion`, or `SharedConstants` with a constant name) in save, client validation and migration. Drop `"SetAside"` from `PERSISTED_FIELDS` and the top-level `tag.contains(TAG_SET_ASIDE)` check, because `SetAside` only ever appears nested inside `Items`.
> - **Technical detail:** `StackItemStorage.serializeNBT` puts `SetAside` inside the inventory compound, which `StackBlockEntity` stores under `Items`. So `PERSISTED_FIELDS`' `"SetAside"` entry and `loadClientUpdate`'s top-level `SetAside` test never match. The nested test in `deserializeClientUpdate` is the one that works. `TAG_ITEMS` is defined twice (StackBlockEntity.java:31 and StackItemStorage.java:34) for two different levels of nesting. Confidence is "Likely" only because I'm relying on the migration comment that the 0.8.3 format matches the current layout.

#### DC-07: Methods and overloads in shipped code that only tests call, or nothing calls

**Verified finding:** Some helpers were unused, and some convenience overloads existed only for tests.

**Fix and disposition:** Deleted `AutomationActor.is`, the automation-only `WorldEdits.isProtected` overload, the five-argument `placeChecked`, the one-argument pile permanence setter, and the gallery permission-level getter. Tests now supply final collision shapes and explicit actors, or read immutable settings. The reviewed Storage three-argument and Bar one-argument extraction overloads were already absent. Retained useful test seams: render override isolation, config-path inspection, synchronized-state inspection, and serialization probes.

**Original issue text (verbatim):**

> ### 7. Methods and overloads in shipped code that only tests call, or nothing calls
> - **Severity:** Low
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/server/AutomationActor.java:19 (`is`, no callers); common/src/main/java/com/github/crittscott/somestacks/server/WorldEdits.java:36 (`isProtected(ServerLevel, BlockPos)`, no callers) and :104 (5-arg `placeChecked`, tests only); common/src/main/java/com/github/crittscott/somestacks/block/StoragePile.java:123 (`setPermanent(boolean)`, tests only); common/src/main/java/com/github/crittscott/somestacks/block/StorageStackBE.java:155 (3-arg `extractAt`, tests only); common/src/main/java/com/github/crittscott/somestacks/block/BarStackBE.java:174 (`extractAt(int)`, tests only); common/src/main/java/com/github/crittscott/somestacks/ServerConfig.java:140 (`galleryRequiredPermissionLevel`, tests only); neoforge/src/main/java/com/github/crittscott/somestacks/NeoForgeServerConfig.java:14 (`path()`, tests only); common/src/main/java/com/github/crittscott/somestacks/client/ClientRenderToolState.java:23 (`disabledMods()`, tests only); common/src/main/java/com/github/crittscott/somestacks/client/ItemRenderOverrides.java:86-125 (`Sandbox`, tests only); common/src/main/java/com/github/crittscott/somestacks/util/StackItemStorage.java:173 (public `serializeNBT(HolderLookup.Provider)`, tests only)
> - **In plain English:** Some code paths in the released mod are never used while playing. They exist only so the automated tests have something to call, or they're simply leftovers.
> - **Consequence:** Maintainers keep these alternate entry points working and documented, and they suggest calling patterns the game never uses. For example, the 5-arg `placeChecked` skips the final collision shape that every real caller passes.
> - **Action:** Delete `AutomationActor.is` and `WorldEdits.isProtected(ServerLevel, BlockPos)`. For the test-only overloads, change the tests to call the real entry point (passing `null` or the explicit argument) and delete the overloads. Move `Sandbox` behind a package-private hook or into the gametest source set if the test structure allows it. Make the public `StackItemStorage.serializeNBT(HolderLookup.Provider)` non-public.
> - **Technical detail:** I verified the call sites with a whole-tree search over `common`, `fabric` and `neoforge` (`main` and `gametest`).

#### DC-10: `StackInteractions` repeats the same per-type branch for deposit, placement and extraction

**Verified finding:** Successful interaction feedback repeated the same playback and extraction completion code.

**Fix and disposition:** Added `StackSounds.play` and shared successful extraction feedback through `finishExtraction`. Kept separate mutation branches for the three movement models. Sound volume and resource-pack behavior did not change.

**Original issue text (verbatim):**

> ### 10. `StackInteractions` repeats the same per-type branch for deposit, placement and extraction
> - **Severity:** Low
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/server/StackInteractions.java:200-249, 279-335, 338-397, 399-417
> - **In plain English:** For each of the three stack types, the server code separately writes out "put it in, play the sound, notify sensors", and the same again for taking out.
> - **Consequence:** There are nine near-identical `playSound(..., StackSounds.VOLUME, 1.0f)` calls and three copies of the extract/give/emit sequence. A change to feedback (pitch, sculk event, hand handling) can easily miss a type.
> - **Action:** Give `BlockType` (or the block entity) its deposit and extract sound accessors, add a `StackSounds.play(level, pos, sound)` helper, and collapse the per-type branches into one path that differs only in the cell-tracing call. Consider moving the 0.5 volume into each event's `"volume"` in `assets/somestacks/sounds.json` and passing `1.0f`, which is how vanilla tunes per-event loudness and lets resource packs set it.
> - **Technical detail:** `extract()`'s Singles and Bar branches are identical apart from the trace method and the sound. `placeAndDeposit` uses three separate `switch (type)` statements that could be a single per-type descriptor.

#### DC-12: Comments describe code that isn't there

**Verified finding:** Some comments and log messages described removed behavior or the wrong method.

**Fix and disposition:** Corrected Singles deposit-support documentation, removed obsolete synchronized/server override descriptions from render-profile documentation and warnings, and corrected the storage loader count. Bar deposit documentation and annotation, orphan ceiling comments, and the `SlotAccess` adapter description were already corrected or accurate.

**Original issue text (verbatim):**

> ### 12. Comments describe code that isn't there
> - **Severity:** Low
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/block/SinglesStackBE.java:212-217; common/src/main/java/com/github/crittscott/somestacks/block/BarStackBE.java:211-216 (also `@Nullable` on a `boolean` return); common/src/main/java/com/github/crittscott/somestacks/block/StoragePile.java:87; common/src/main/java/com/github/crittscott/somestacks/block/SinglesColumn.java:93; common/src/main/java/com/github/crittscott/somestacks/block/BarColumn.java:103; common/src/main/java/com/github/crittscott/somestacks/renderconfig/OverrideJsonCodec.java:16, 29, 45, 52, 58; common/src/main/java/com/github/crittscott/somestacks/util/StackItemStorage.java:21; common/src/main/java/com/github/crittscott/somestacks/util/SlotAccess.java:9
> - **In plain English:** Several notes in the code are left over from removed features: a description attached to the wrong method, notes floating above nothing, and log messages about "synchronized overrides" the mod no longer has.
> - **Consequence:** A maintainer reading `canDepositAt` is told it returns a support array "or null". A player or admin who sees the "Dropping out of range scale ... from a synchronized override" warning goes looking for a network feature that doesn't exist, when the real cause is a measured or user value.
> - **Action:** Give `canDepositAt` its own javadoc and remove the `@Nullable`. Delete the three orphan "configured ceiling" comments. Rewrite the `OverrideJsonCodec` comments and log messages to name the actual sources (user file, bundled resources, measured cache). Change "all three loaders" to the two loaders and fix the `SlotAccess` adapter claim.
> - **Technical detail:** The javadoc above both `canDepositAt` methods is the text belonging to `supportSeamBeneath`. The three orphan comments sit directly above the `columnHasRoomFor` javadoc, each with nothing between it and the next comment.

#### DC-14: Registered objects have two homes

**Verified finding:** Common code looked up registered values while loader registries retained some unread references.

**Fix and disposition:** Removed unread sound holder fields while retaining the registration calls. Kept loader block references needed for block-entity registration and native deferred registration. Registry supplier installation was not introduced. Hot-path type lookup is addressed by EW-10.

**Original issue text (verbatim):**

> ### 14. Registered objects have two homes
> - **Severity:** Low
> - **Confidence:** Likely
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/CommonRegistry.java:34-100; neoforge/src/main/java/com/github/crittscott/somestacks/ModRegistry.java:45-62; fabric/src/main/java/com/github/crittscott/somestacks/FabricRegistry.java:113-130
> - **In plain English:** Each loader registers the blocks and sounds and holds on to them, but the shared code ignores those references and looks each one up in the game's registry again every time it needs it.
> - **Consequence:** The sound and block holders on both loaders are written once and never read. Every `BlockType.getBlock()`, `CommonRegistry.*Sound()` and BE constructor call does a registry hash lookup, including inside per-slot loops such as `BlockType.of`. A maintainer adding a registered object has to update three files.
> - **Action:** Have each loader hand its registered references (or NeoForge `DeferredHolder`s used as `Supplier`s) to `CommonRegistry` once at registration, so that common code reads fields. Alternatively, keep the lookup and delete the unused loader-side holder fields.
> - **Technical detail:** `ModRegistry.*_SOUND`, `*_STACK_BLOCK` and the matching `FabricRegistry` fields are never referenced outside their own class, except `BAR_STACK_BLOCK` in one NeoForge gametest.

#### DC-16: Small hand-written versions of vanilla utilities

**Verified finding:** Config code duplicated namespace validation, a clamp helper, and a pile-height limit alias; gesture decoding repeated enum lookup.

**Fix and disposition:** Consolidated packet namespace validation, used `Mth.clamp` and `ServerConfig.MAX_PILE_HEIGHT`, and routed validated gesture ordinals through `StackMode.fromOrdinal`. Kept the existing namespace API call and VarInt-size helper rather than substituting APIs whose target-version signatures were not established. Receive-side namespace length was already bounded by `readUtf` and constructor validation; no missing length guard was found.

**Original issue text (verbatim):**

> ### 16. Small hand-written versions of vanilla utilities
> - **Severity:** Low
> - **Confidence:** Likely
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/network/ConfigSyncPkt.java:95-118, 222-229, 238-250, 261-273; common/src/main/java/com/github/crittscott/somestacks/client/ItemRenderOverrides.java:269; fabric/src/main/java/com/github/crittscott/somestacks/JsonServerConfig.java:44, 238-240; common/src/main/java/com/github/crittscott/somestacks/network/GestureStatePkt.java:24-30
> - **In plain English:** A few small helpers are written out by hand even though the game already provides them, and some are written several times.
> - **Consequence:** Namespace validation is done three different ways in `ConfigSyncPkt` alone (the length check is missing from `readNamespaces`), so the rules for a valid mod name can differ between sender and receiver.
> - **Action:** Use `Identifier.isValidNamespace` instead of `Identifier.tryBuild(ns, "validation") != null` (4 sites). Use `VarInt.getByteSize` instead of `varIntSize`, `Mth.clamp` instead of the private `clamp`, and `ServerConfig.MAX_PILE_HEIGHT` directly instead of the `MAX_PILE_HEIGHT_LIMIT` alias. Put the namespace check in one method. Use `StackMode.fromOrdinal` (with bounds) or `ByteBufCodecs.idMapper` in `GestureStatePkt`.
> - **Technical detail:** I'm confident these vanilla helpers exist under these names, but I haven't checked them for 26.3, hence "Likely".

### Efficiency

#### EF-01: Growth permission is re-checked once for every empty slot above a run

**Verified finding:** Headroom capacity scans repeated growth authority checks for candidate slots; positional runs checked growth before support.

**Fix and disposition:** Moved staged support checks ahead of growth in Singles and Bar. Shared shape-independent growth preflight in `StackRun` and cached automation permission only within a scoped bulk insertion query. Height, enablement, and replaceability still read current state; collision remains per position. Later operations and commit placement retain authorization checks. No tick-wide permission cache was added.

**Original issue text (verbatim):**

> ### 1. Growth permission is re-checked once for every empty slot above a run
> - **Severity:** Medium
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/block/StoragePile.java:260-269, common/src/main/java/com/github/crittscott/somestacks/block/SinglesColumn.java:147-162, common/src/main/java/com/github/crittscott/somestacks/block/BarColumn.java:419-430, fabric/src/main/java/com/github/crittscott/somestacks/server/FabricEditAuthority.java:40-48, neoforge/src/main/java/com/github/crittscott/somestacks/block/NeoForgeRunResourceHandler.java:130-142
> - **In plain English:** When a hopper or pipe tries to push an item into a full stack, the mod checks every empty spot in the imaginary block above it. For each spot it asks again whether the stack may grow there: is the space protected, is an entity in the way, and does any claim mod object. The answer is the same for every spot, but the mod asks up to 27 times for Storage and up to 64 times for Singles or Bar.
> - **Consequence:** A server admin with a bank of hoppers feeding stacks that cannot grow pays for dozens of protection checks, fake-player lookups, entity-collision queries and (on Fabric) `UseBlockCallback` invocations per hopper attempt. Vanilla hoppers retry a failed push every tick, so this cost repeats every tick. It shows up as tick time on busy automation servers.
> - **Action:** Memoize the shape-independent part of `canGrow` (height limit, enable flag, build height, replaceability, `WorldEdits.mayPlace`) on the `StackRun` instance, which already lives only for the current tick and is invalidated by structural edits. Keep only the per-slot `isUnobstructed(shape)` check per call. In `SinglesColumn.acceptsAt` and `BarColumn.acceptsAt`, run the cheap support test before `canGrow`. That way unsupported headroom cells (48 of 64 for Singles, most for Bar) never reach the world checks.
> - **Technical detail:** `StorageUtil.insertStacking` (Fabric) and `NeoForgeRunResourceHandler.insert(resource, ...)` walk every advertised slot, including the headroom block. Each empty headroom slot calls `acceptsAt`, which calls `canGrow(...)`, which calls `WorldEdits.mayPlace` and `WorldEdits.isUnobstructed`. `mayPlace` resolves `automationActor(level)` (`FakePlayer.get` / `FakePlayerFactory.get`), runs `level.mayInteract`, and on Fabric fires `UseBlockCallback.EVENT` inside `WorldEdits.consulting`. In Singles, `acceptsAt` calls `canGrow(SinglesCubeIdx.shapeFor(slot, 0))` before `cellSupported`, and any headroom cell above the bottom layer is unsupported by definition. Bar has the same ordering.

#### EF-02: Automation views re-resolve the run and the shared ledger on every slot access

**Verified finding:** Automation slot access repeatedly located the base block and its transaction ledger.

**Fix and disposition:** Added direct base-entity access to resolved runs, removing redundant world lookups. Base adapters return their own ledger without another attachment lookup. Bulk insertion, Fabric extraction, and Fabric iteration delegate to the base ledger once. Retained external slot views still resolve the current run when used. Native transaction staging and rollback remain loader-specific.

**Original issue text (verbatim):**

> ### 2. Automation views re-resolve the run and the shared ledger on every slot access
> - **Severity:** Medium
> - **Confidence:** Certain
> - **Where:** fabric/src/main/java/com/github/crittscott/somestacks/block/FabricRunItemStorage.java:102-128, 247-325; neoforge/src/main/java/com/github/crittscott/somestacks/block/NeoForgeRunResourceHandler.java:37-61, 92-142
> - **In plain English:** When a pipe reads what is inside a stack, the mod works out again, for every single slot, which stack block is the "bottom" one that keeps the shared bookkeeping. It looks that block up in the world each time.
> - **Consequence:** A storage network or hopper scanning an 8-high Storage pile (243 advertised slots) makes several hundred world block-entity lookups and attachment lookups per scan. Several of these happen per slot, because `isResourceBlank`, `getResource`, `getAmount` and `insert` each call `ledger()` again. Server admins with item networks attached to tall piles pay this every scan.
> - **Action:** Get the base block entity from the already-cached `StackRun` (`blocks.get(0)`) rather than `level.getBlockEntity(run.basePos())`. Resolve the ledger once at the top of each bulk operation (`insert(resource, ...)`, `extract(resource, ...)`, `iterator()`), not inside every per-slot read. Alternatively, cache the ledger alongside the per-tick run cache in `StackBlockEntity`.
> - **Technical detail:** Fabric `RunSlot.insert` calls `ledger()`, then `currentStack` calls `ledger()` again, then `acceptedAt` calls `itemRun()`. On NeoForge, `insert(resource, ...)` calls `current(slot)` in both passes, each of which calls `ledger()`, and then `insert(slot, ...)` calls `ledger()` and `current()` again. Every `ledger()` does `itemRun()`, `getLevel().getBlockEntity(basePos)` (a chunk lookup) and `getAttachedOrCreate`/`getData`.

#### EF-03: Every Storage settle re-stringifies item components to sort them

**Verified finding:** Every settlement group eagerly converted nonempty item-component patches to strings for sorting.

**Fix and disposition:** Sort keys retain the patch and create a memoized string only when item ID and damage tie and both patches are nonempty. Existing ordering, identity grouping, and settlement behavior are preserved.

**Original issue text (verbatim):**

> ### 3. Every Storage settle re-stringifies item components to sort them
> - **Severity:** Medium
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/util/StackSort.java:33-37, common/src/main/java/com/github/crittscott/somestacks/block/StoragePile.java:376-423
> - **In plain English:** Each time anything goes into or out of a Storage pile, including every hopper transfer, the whole pile is re-sorted on the next tick. To sort, the mod turns each item type's extra data (enchantments, names, shulker contents, book text) into a long piece of text, even when the item name alone would decide the order.
> - **Consequence:** Piles holding component-heavy items (filled shulker boxes, written books, enchanted gear) make the server build large strings on every hopper transfer. That is wasted CPU and garbage for admins running automated sorting into Storage.
> - **Action:** Make the comparison lazy. Compare item id, then damage, and build the component string only when both tie and both patches are non-empty. For example, keep the `DataComponentPatch` in `SortKey` and call `toString()` inside `compareTo` only on a tie, memoized per group.
> - **Technical detail:** `SettlementGroup` computes `StackSort.key(stack)` for every distinct group on every `settle()`, and `key` calls `patch.toString()` unconditionally for non-empty patches. `settle()` is scheduled by every `extract`/`insertAt`/`deposit` through `markDirtyBy`, so automated traffic drives it once per transfer tick.

#### EF-04: Each slot write fires vanilla comparator updates on top of the mod's own batched comparator publication

**Verified finding:** Slot mutations call `setChanged`, and runs separately publish comparator changes.

**Fix and disposition:** The two mod call paths are confirmed, but duplicate native comparator notification in the target version was not independently established. Deferred: no dirty-marking or comparator publication change was made. Confirm the target native contract before selecting a single notification mechanism.

**Original issue text (verbatim):**

> ### 4. Each slot write fires vanilla comparator updates on top of the mod's own batched comparator publication
> - **Severity:** Medium
> - **Confidence:** Likely
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/block/StackBlockEntity.java:399-410, common/src/main/java/com/github/crittscott/somestacks/block/StackRun.java:302-313
> - **In plain English:** The mod saves up comparator updates and sends them once per tick, only when the signal really changes. But every individual slot change also triggers Minecraft's own "tell nearby comparators" step, so the saving-up mostly doesn't help.
> - **Consequence:** A settle that rewrites many slots, or a Bar cascade, sends one neighbor-notification round per slot changed, even though the signal is published once afterwards. The batching machinery adds code without delivering its intended saving, which misleads maintainers about where comparator updates come from.
> - **Action:** Pick one mechanism. Either rely on vanilla `setChanged` for comparator notification and delete `publishComparatorSignal`/`exchangePublishedSignal`, or mark the chunk dirty without the neighbor update in `onContentsChanged` (`level.blockEntityChanged(pos)`) and keep the run-wide batched publication as the single path. The second fits the run-wide signal better.
> - **Technical detail:** `items.onContentsChanged` calls `setChanged()`, and vanilla `BlockEntity.setChanged` calls `level.updateNeighbourForOutputSignal(pos, block)` for non-air states. `StackRun.publishComparatorSignal` then repeats the notification for every block in the run once the base's signal changes.

#### EF-05: Bar renderer rebuilds its whole presentation every frame

**Verified finding:** Bar render extraction resolved textures and sprites and rebuilt presentation lists every frame.

**Fix and disposition:** Cached prepared Bar presentation in the block entity's render cache. Content updates discard it; a resource-generation stamp expires retained block caches after reload. Clearing the sprite-region map alone is not treated as sufficient invalidation. Extended `/ssclienttest` to check reuse and both invalidation boundaries.

**Original issue text (verbatim):**

> ### 5. Bar renderer rebuilds its whole presentation every frame
> - **Severity:** Low
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/client/BarStackBER.java:154-176
> - **In plain English:** Storage and Singles stacks remember how to draw themselves until their contents change. Bar stacks redo all of that preparation for every bar on every frame.
> - **Consequence:** A player looking at a wall of full Bar stacks pays, per block per frame, for 64 texture-store lookups (registry key and hash maps), 64 atlas sprite lookups, and several list allocations. This costs frame time and creates garbage-collector pressure on the client.
> - **Action:** Cache the prepared `List<BarRenderData>` in the block entity's existing `renderCache` slot, cleared by `loadClientUpdate`, exactly as `CubeRenderHelper.GridCache` does for Storage and Singles. Clear it on resource reload together with `SPRITE_REGIONS`.
> - **Technical detail:** `extractRenderState` calls `BarTextureStore.getTexture(stack)` and `getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(...)` for each occupied slot each frame, then builds an `ArrayList` and `List.copyOf`. None of it depends on camera or partial tick.

#### EF-06: Bar renderer computes its own light value instead of using the render state's

**Verified finding:** Bar and grid render preparation explicitly looked up light after superclass render-state extraction.

**Fix and disposition:** Removed the redundant light lookup and Bar's private light field. Both presentation paths now use inherited `lightCoords`; frame lighting remains refreshed independently of cached presentation.

**Original issue text (verbatim):**

> ### 6. Bar renderer computes its own light value instead of using the render state's
> - **Severity:** Low
> - **Confidence:** Likely
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/client/BarStackBER.java:143, 162, 185
> - **In plain English:** The game already works out how brightly lit each block is before drawing it. The Bar renderer looks it up a second time.
> - **Consequence:** There is a duplicate light lookup per Bar block per frame, and a maintainer sees a second, non-standard light field.
> - **Action:** Delete `BarRenderState.light` and use the `lightCoords` field that `BlockEntityRenderer.super.extractRenderState` already fills on `BlockEntityRenderState`.
> - **Technical detail:** `state.light = LightCoordsUtil.getLightCoords(be.getLevel(), be.getBlockPos())` runs right after the super call that sets the vanilla equivalent. `CubeRenderHelper.extractGridItems` (line 271) has the same duplication for Storage and Singles.

#### EF-07: Every block of a run resolves and caches its own copy of the run

**Verified finding:** Each member block cached a separately resolved copy of its whole run.

**Fix and disposition:** Cached one resolved run instance on every current member for the same tick. Retained structural invalidation for affected runs and added a shared-instance check after growth. This removes repeated traversal; whole-run comparator fill summation still exists.

**Original issue text (verbatim):**

> ### 7. Every block of a run resolves and caches its own copy of the run
> - **Severity:** Low
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/block/StackBlockEntity.java:435-446, common/src/main/java/com/github/crittscott/somestacks/block/StackRun.java:160-174, 302-313
> - **In plain English:** When a tall stack's comparator signal changes, every block in it is told. Each block then walks the whole stack separately to find out what it belongs to.
> - **Consequence:** For a run of height N, one signal change costs about N² block-entity lookups. That is 64 at the default height of 8, and about 4,000 at the configurable maximum of 64. Admins who raise the height limit pay for it on every comparator change.
> - **Action:** In `resolveRun`, after building the block list, store the same `StackRun` instance in every member's `cachedRun`/`cachedRunTick`. One walk then serves the whole run for that tick.
> - **Technical detail:** `publishComparatorSignal` calls `updateNeighbourForOutputSignal` for each block, comparators call `getAnalogOutputSignal`, and that calls `X.at(level, pos)`, which calls that block's own `itemRun()`. That block's cache is empty, so it calls `resolveBlocks`, which walks down and then up the run.

#### EF-08: Shape rebuilding merges cell boxes one at a time

**Verified finding:** Singles and Bar shape rebuilding repeatedly optimized a growing union with `Shapes.or`.

**Fix and disposition:** Accumulated boxes with `Shapes.joinUnoptimized` and optimized once after the loop. Existing occupancy/rotation shape caches and invalidation remain in place.

**Original issue text (verbatim):**

> ### 8. Shape rebuilding merges cell boxes one at a time
> - **Severity:** Low
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/block/SinglesStackBE.java:124-136, common/src/main/java/com/github/crittscott/somestacks/block/BarStackBE.java:112-123
> - **In plain English:** To work out the outline and collision of a Singles or Bar stack, the mod adds each filled cell to the shape one at a time. It fully tidies the shape after every addition instead of once at the end.
> - **Consequence:** Rebuilding a full block does up to 64 optimized voxel merges of growing size, on both server and client, after every content change. This is a small but avoidable cost during automated filling and emptying.
> - **Action:** Accumulate with `Shapes.joinUnoptimized(shape, cell, BooleanOp.OR)` and call `.optimize()` once at the end, which is the vanilla idiom for multi-part unions.
> - **Technical detail:** `Shapes.or` is `join(..., BooleanOp.OR)`, which optimizes on each call. The shape cache is correctly invalidated only on change, so this matters only per rebuild.

### Expected vanilla and loader mechanisms

#### EW-02: Stack `use` methods always report "handled," even when nothing happened

**Verified finding:** Stack block-use methods ignored the interaction handler's unhandled result and always consumed the click.

**Fix and disposition:** Native use methods consume handled server gestures and return `PASS` otherwise. Recognized client gestures remain consumed by loader callbacks. Ordinary extraction attempts still consume the click even when no item moves. Extended existing interaction checks for off-hand use, unhandled modified empty-hand clicks, occupied-hand permanence mode, and empty extraction attempts.

**Original issue text (verbatim):**

> ### 2. Stack `use` methods always report "handled," even when nothing happened
> - **Severity:** Medium
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/block/StackBlock.java:104-123; common/src/main/java/com/github/crittscott/somestacks/server/StackInteractions.java:42-54, 154-194
> - **In plain English:** When you right-click a stack in a way that does nothing (for example, holding the stack key with an item in hand while in "toggle permanent" mode), the game is still told the click was used up. So the item in your hand, or the item in your other hand, never gets its normal turn.
> - **Consequence:** Players see clicks swallowed with no effect, and off-hand items (food, a shield, a torch) cannot be used while aimed at a stack whenever the gesture falls through. Other mods that rely on a block passing unused clicks also never see them.
> - **Action:** Use the boolean `handleExistingStack` returns. In `useItemOn`, return `InteractionResult.TRY_WITH_EMPTY_HAND` (or `PASS`) when it returns false; in `useWithoutItem`, return `PASS`. Return `SUCCESS` or `CONSUME` only when a gesture actually ran.
> - **Technical detail:** Both overrides compute `StackInteractions.handleExistingStack(...)` on the server and discard its result, then return `CONSUME` unconditionally. `applyExistingStack` returns false for modifier-plus-item in `TOGGLE_PERMANENT` mode, for modifier with an empty hand outside that mode, for sneaking with empty hands, and for any non-main hand. Vanilla's contract is that a non-consuming result lets `ServerPlayerGameMode.useItemOn` continue to the item's `useOn` and then to the other hand.

#### EW-03: Block rotation goes through a block-entity side channel instead of `level.setBlock`

**Verified finding:** Reading rotation could apply a pending block-entity rotation and mutate the world. Storage joining a pile could also change facing after placement.

**Fix and disposition:** Removed pending rotation state and application hooks. Rotation getters are pure; setters change native facing through `level.setBlock`. `StackPlacement.stateFor` chooses neighboring Storage facing before placement. Pile adoption retains permanence without a second facing edit. Singles shape invalidation remains, and growth checks now cover inherited facing.

**Original issue text (verbatim):**

> ### 3. Block rotation goes through a block-entity side channel instead of `level.setBlock`
> - **Severity:** Medium
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/block/StackBlockEntity.java:35, 181-193, 245-251, 275-295, 352; common/src/main/java/com/github/crittscott/somestacks/block/StorageStackBE.java:86-88, 104-108; common/src/main/java/com/github/crittscott/somestacks/block/SinglesStackBE.java:101-104
> - **In plain English:** Which way a stack faces is ordinary block data, like a furnace's facing. Here, turning it is routed through the block's storage object, which remembers a "rotation to apply later" and writes it into the world the next time anyone even asks which way the block faces.
> - **Consequence:** Reading the rotation (`getRotation()`, which the renderer and ray tracing call) can change the world on the server, so a read can trigger block updates, neighbor notifications, and a save. The maintainer has to reason about when a pending rotation lands. An expert expects a rotate to be one `setBlock` call.
> - **Action:** Delete `pendingBlockRotation`, `setBlockRotation`, and `applyPendingBlockRotation`. Make `blockRotation()` a pure read of `HORIZONTAL_FACING`. In `StackInteractions.rotateBlock`, call `level.setBlock(pos, state.setValue(StackBlock.HORIZONTAL_FACING, next), Block.UPDATE_ALL)`. For a block joining a pile, compute the pile's facing before placement and pass it into the placed state (`StackPlacement.stateFor`) instead of adopting it afterward. Keep the `getRotation`/`setRotation` pair on the base class only, instead of identical copies on Storage and Singles.
> - **Technical detail:** `blockRotation()` calls `applyPendingBlockRotation()`, which may call `level.setBlock(..., UPDATE_ALL)` and `setChanged()`. `setLevel` and `loadClientUpdate` also manage the pending field. `StoragePile.adoptNeighbourState` → `adoptPileState` → `setBlockRotation` runs from `onPlace`, so the placement immediately causes a second `setBlock` with full updates.

#### EW-05: `/ss reload` and native reloads leave clients' command lists stale

**Verified finding:** Reload paths did not refresh command trees when gallery visibility or permission changed; command and native reload logic diverged.

**Fix and disposition:** Added one synchronous server-thread `reloadPolicy` operation used directly by `/ss reload` and scheduled by native callbacks. Changed gallery enablement or permission resends commands to connected players. Changed client settings rebuild and send the config snapshot; unchanged policy does no synchronization work. Extended existing config checks for gallery enablement, permission changes, and disabling after reload.

**Original issue text (verbatim):**

> ### 5. `/ss reload` and native reloads leave clients' command lists stale
> - **Severity:** Medium
> - **Confidence:** Likely
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/command/SsCommand.java:100-103, 472-479; common/src/main/java/com/github/crittscott/somestacks/SomeStacksServer.java:76-88
> - **In plain English:** Whether players see the gallery commands depends on a config setting. When an admin turns galleries on or off and reloads, players' command suggestions don't update until they reconnect.
> - **Consequence:** After enabling galleries or changing `required_permission_level`, permitted players see `/ss gallery` as unknown (red, no completion) until they relog. After disabling, they still see it offered. Admins reasonably conclude the reload didn't work. Separately, `/ss reload` bypasses `onPolicyReloaded`, so it always rebuilds and resends and logs nothing, while the native reload path skips unchanged policy and logs.
> - **Action:** After any policy change that affects a `requires` predicate, call `server.getCommands().sendCommands(player)` for every online player, as vanilla does after op changes. Route `/ss reload` through the same method as native reloads (`SomeStacksServer.onPolicyReloaded`, or a synchronous version of it) so there is one reload path.
> - **Technical detail:** `galleryTree` uses `.requires(source -> ... ServerConfig.galleryEnabled() && ServerConfig.galleryPermission().check(...))`. Brigadier `requires` predicates are evaluated when the command tree is sent at login. `SsCommand.reload` calls `ServerConfig.reload()`, `ConfigSyncPkt.rebuildCurrent()`, and `syncAllPlayers` directly.

#### EW-06: Inventory save layout departs from vanilla's container shape

**Verified finding:** Saved inventory uses a nested `Items` compound rather than a conventional top-level item list.

**Fix and disposition:** Layout confirmed; external tool failures were not demonstrated. Deferred as a separate format/migration decision. This batch preserves the saved layout, unreadable-entry retention, migration behavior, and atomic client validation. If changed later, convert the immediately preceding release's saved form in the migration layer and retain raw unreadable entries.

**Original issue text (verbatim):**

> ### 6. Inventory save layout departs from vanilla's container shape
> - **Severity:** Medium
> - **Confidence:** Likely
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/util/StackItemStorage.java:152-196; common/src/main/java/com/github/crittscott/somestacks/block/StackBlockEntity.java:300-326, 377-389; common/src/main/java/com/github/crittscott/somestacks/block/StackDataMigration.java:36-47, 75-78
> - **In plain English:** Vanilla chests save their items as a list called `Items`. Stacks save a box called `Items` that holds another list also called `Items`. Tools and mods that know how to read a chest's contents can't read a stack's.
> - **Consequence:** `/data get block` output, NBT editors, and inventory-inspecting mods (tooltips, look-at overlays, schematic and structure tools) that read the vanilla `Items` list find a compound and show nothing. The maintainer also carries a custom `Codec` wrapper, a raw pass-through codec in `readTag`, and two different ways of stamping `DataVersion`.
> - **Action:** Save the live items as a top-level `Items` list of `ItemStackWithSlot` through `output.list("Items", ItemStackWithSlot.CODEC)`, with `SetAside` as a sibling top-level list. Read the same way, keeping the raw set-aside retry. Add a migration-layer conversion for the released nested form (per CLAUDE.md, migration from the preceding release only). Use one `DataVersion` writer (`NbtUtils.addCurrentDataVersion` or `output.putInt`), not both.
> - **Technical detail:** `saveAdditional` stores `StackItemStorage.codec(slots)`, which encodes `serializeNBT` → `{Items:[{Slot:int,...}], SetAside:[...]}` under the key `Items`. Vanilla `ItemStackWithSlot` uses a byte `Slot`. `StackDataMigration.stampVersion` uses `NbtUtils.addCurrentDataVersion`, while `saveAdditional` writes `DataVersion` with `putInt`.

#### EW-08: Fabric block entity types use the deprecated Fabric builder

**Verified finding:** Fabric uses `FabricBlockEntityTypeBuilder`.

**Fix and disposition:** Its alleged deprecation for the pinned target was not established; builder use alone is not a verified defect. No change. Do not infer Fabric constructor availability from NeoForge's constructor use.

**Original issue text (verbatim):**

> ### 8. Fabric block entity types use the deprecated Fabric builder
> - **Severity:** Low
> - **Confidence:** Likely
> - **Where:** fabric/src/main/java/com/github/crittscott/somestacks/FabricRegistry.java:44-56
> - **In plain English:** The two loaders create the same three block-storage types in two different ways. One of them uses an old helper that Fabric now tells people to stop using.
> - **Consequence:** Deprecation warnings appear, and the helper may be removed in a future Fabric API update during a port. The two registries also look different for no loader reason.
> - **Action:** Use `new BlockEntityType<>(BarStackBE::new, Set.of(BAR_STACK_BLOCK))` (and the same for the other two), matching `ModRegistry`.
> - **Technical detail:** `FabricBlockEntityTypeBuilder` has been deprecated since vanilla made the `BlockEntityType` constructor public. NeoForge's `ModRegistry` already uses the constructor.

#### EW-09: Fabric config is hand-parsed field by field, and its loader re-installs itself

**Verified finding:** Fabric config loading installed its backend and published settings internally, duplicating the facade's work.

**Fix and disposition:** `JsonServerConfig.load`, `loadFor`, and `reload` now return settings without installation or application. The facade owns publication. Fixture setup explicitly installs/applies its backend and verifies that loading itself changes neither. Retained independent field fallback, clamping, and malformed-list-member filtering; a whole-record codec rewrite was not required.

**Original issue text (verbatim):**

> ### 9. Fabric config is hand-parsed field by field, and its loader re-installs itself
> - **Severity:** Low
> - **Confidence:** Likely
> - **Where:** fabric/src/main/java/com/github/crittscott/somestacks/JsonServerConfig.java:45-73, 75-129, 131-170, 180-236
> - **In plain English:** The Fabric settings file is read and written by roughly 150 lines of code that handle each setting individually. Minecraft has a standard way to describe a settings shape once and read and write it automatically.
> - **Consequence:** Every new setting means adding a key constant, a parse line, a save line, a `Settings` argument, and a NeoForge spec entry, and nothing checks that the reader and writer agree. `load()` also calls `ServerConfig.install(this)` and `ServerConfig.apply(...)` from inside the backend, and then `ServerConfig.loadFor` applies the result again, so the backend and the facade each do the other's job.
> - **Action:** Define one `Codec<Settings>` with `RecordCodecBuilder` (nested `piles`, `stacks`, `compatibility`, and `render_gallery` groups, `optionalFieldOf` with defaults, and `Codec.intRange` for clamps) and read and write it through `JsonOps`, as `renderconfig/OverrideJsonCodec` already does for render overrides. Make `load` return `Settings` without calling `install` or `apply`.
> - **Technical detail:** `intOr`, `boolOr`, `stringListOr`, and `obj` reimplement codec field decoding with logging. If per-field fallback logging must be kept, `DataResult` partial results with `resultOrPartial(LOGGER::warn)` provide it.

#### EW-10: Stack type lookup scans the registry instead of asking the block

**Verified finding:** `BlockType.of` scanned enum values and performed registry lookups to classify a block.

**Fix and disposition:** Added `StackBlock.type`, implemented by each stack block, and classified through `instanceof StackBlock`. Registered-instance lookups remain where an actual registered object is needed.

**Original issue text (verbatim):**

> ### 10. Stack type lookup scans the registry instead of asking the block
> - **Severity:** Low
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/util/BlockType.java:17-33; common/src/main/java/com/github/crittscott/somestacks/CommonRegistry.java:35-105
> - **In plain English:** To find out what kind of stack a block is, the code looks up all three stack blocks by name and compares, every time, instead of asking the block what it is.
> - **Consequence:** Interaction, placement, and protection paths (`StackInteractions`, `StackBlock.use*`) do up to three registry lookups per call, and there is a second source of truth beside the class hierarchy. An expert expects `state.getBlock() instanceof StackBlock sb ? sb.type() : null`.
> - **Action:** Add `abstract BlockType type()` to `StackBlock`, implemented by each subclass. Make `BlockType.of` use `instanceof StackBlock`. Keep `CommonRegistry` lookups only where common code truly needs the registered instance (for example, creating states and comparator updates).
> - **Technical detail:** `BlockType.of` iterates `values()` and calls `getBlock()`, which calls `BuiltInRegistries.BLOCK.getValue(id)` each time.

#### EW-11: A thread-local guard on the single-threaded server, among three different re-entry guards

**Verified finding:** The server-only consultation guard used `ThreadLocal` despite its single-threaded call paths.

**Fix and disposition:** Replaced it with a plain boolean and documented the separate scopes of consultation, loader snapshot placement, and transfer-commit guards. Kept these guards distinct because they prevent different forms of reentry.

**Original issue text (verbatim):**

> ### 11. A thread-local guard on the single-threaded server, among three different re-entry guards
> - **Severity:** Low
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/server/WorldEdits.java:26, 58-72; neoforge/src/main/java/com/github/crittscott/somestacks/server/NeoForgeEditAuthority.java:43-48; common/src/main/java/com/github/crittscott/somestacks/block/RunEdit.java
> - **In plain English:** Three separate "don't do this twice at once" switches exist, written three different ways, and one of them is built for multi-threaded code that isn't present here.
> - **Consequence:** A `ThreadLocal` tells a reader that this code runs on several threads, which sends maintainers looking for concurrency that doesn't exist. Three styles of the same idea make it hard to see which guards cover which re-entry.
> - **Action:** Replace the `ThreadLocal<Boolean>` with a plain static boolean, the same style as `RunEdit`, and document all three guards together in one place.
> - **Technical detail:** `consultingAdjacent` is only read and written on the server thread (from `handleExistingStack`, `handleAdjacentClick`, `FabricEditAuthority.mayPlace`, and `mayUseAdjacent`).

### Carry-forward checks for other 26.* projects

- Match the affected behavior and call sites before applying these changes; line numbers and already-completed cleanup differ between projects.
- Preserve permission callbacks, actor attribution, staged support, cross-block transaction sharing, rollback, and structural cache invalidation when porting the automation changes.
- Keep native facing inheritance before placement and preserve Storage permanence and Singles rotation-aware support.
- Tie prepared Bar presentation to both content updates and resource reloads, while continuing to refresh lighting each frame.
- Update existing GameTests and orientation descriptions alongside each port. Build and GameTest success reported here applies to `26.3`; validate each other target separately, including its client checks.
- EF-04 and EW-06 remain deferred. EW-08 is unconfirmed. The qualified or rejected parts of DC-05, DC-07, DC-12, DC-14, DC-16, and EW-09 must not become automatic porting tasks.

### Implementation and verification map

All paths below are relative to the target version project's root. Use the fixed `26.3` implementations as examples when the target still contains the reviewed issue; no review report is needed to identify the implementation.

| Area | Implementation to inspect | Existing checks to adapt or run |
| --- | --- | --- |
| Native use results and client cancellation | `common/src/main/java/com/github/crittscott/somestacks/block/StackBlock.java`; `client/interaction/InteractionRuleRegistry.java` and `InteractionContext.java`; both loaders' client callbacks and Fabric's `MinecraftMixin` | Shared `InteractionChecks.interactionReadsOnlyTheMainHand`, `ClientGestureChecks.blockRulesRespectPrecedenceAndHands`, and `fullTopFacesFallThroughToPlacement`; `/ssclienttest` for actual loader callbacks |
| Native facing and Storage inheritance | `block/StackBlockEntity.java`, `StorageStackBE.java`, `SinglesStackBE.java`, `StoragePile.java`; `util/StackPlacement.java` | Shared `InteractionChecks.rotationValidatesHeldItemAndBlockType`, `StoragePileChecks.fullPileGrowsByOneBlock`, Singles seam/rotation checks, and persistence checks |
| Reload and config ownership | `SomeStacksServer.reloadPolicy`, `command/SsCommand.reload`, Fabric's `JsonServerConfig` | Shared `ConfigurationChecks.serverCommandsEnforcePermissionsAndPersistEdits`; Fabric `ConfigurationGameTests` config fixtures; manually confirm connected clients' gallery suggestions refresh after enablement/permission edits |
| Shared run resolution and growth queries | `block/StackBlockEntity.itemRun/cacheRun`; `StackRun` constructor, `baseBlockEntity`, `GrowthQuery`, and `canGrowBlock`; positional `acceptsAt` methods | Shared pile growth checks; both loaders' transfer tests for staged support, slot replacement, rollback, cross-block sharing, growth, movement, and protection vetoes |
| Loader ledger access | Fabric `block/FabricRunItemStorage`; NeoForge `block/NeoForgeRunResourceHandler` | The same native transfer/protection suites; retain current structure resolution for externally retained slot views |
| Sort order | `util/StackSort.SortKey`; `block/StoragePile` settlement groups | `StackSortChecks.comparatorOrdersEveryIdentityComponent`; `StoragePileChecks.settleConsolidatesExactIdentityAndPacksDown` |
| Prepared rendering and light | `client/BarStackBER`; `CubeRenderHelper`; `block/StackBlockEntity.loadClientUpdate` | `/ssclienttest`: `ClientCallbackChecks.checkBarPresentationCache` covers repeated frames, resource invalidation, and client inventory updates; existing grid submission and resin tint checks remain |
| Occupied shapes | `block/SinglesStackBE.computeShape`; `BarStackBE.computeShape` | Existing geometry, selection, collision, occupancy, and Singles rotation checks |
| Small cleanup and validation | `server/StackSounds`, `StackInteractions`, `WorldEdits`, `AutomationActor`; `network/ConfigSyncPkt` and `GestureStatePkt`; registration classes; saved-field constants | Existing interaction, protection, malformed-packet, synchronization, persistence, and config tests; keep useful test isolation and raw malformed-field rejection |

Except where a loader is named, implementation paths in the table start beneath `common/src/main/java/com/github/crittscott/somestacks/`. Shared check classes start beneath `common/src/gametest/java/com/github/crittscott/somestacks/gametest/`; loader checks use that loader's `src/gametest` tree. Update each target's affected orientation and player/admin documentation, keeping the orientation size limits and fixed headers intact.

## Batch 2: guarding, legacy, and loader divergence — implementation

Status: the surviving changes are implemented in `26.3`; qualified exclusions remain unchanged as recorded below. Source inspection checked changed call arities, project-owned imports, mixin registration, and renamed test-instance references. No builds, GameTests, client checks, or Git operations were run. Batch 1's successful builds and GameTests do not validate Batch 2.

All 15 numbered findings from the three reports are copied below. Summaries refer to additional, absent finding numbers; those references do not create extra approved work. Pending rotation and several comment/overload fixes overlap batch 1. The verification and proposed action above each quotation qualify the original recommendation.

### Guarding

#### GD-03: NeoForge listeners check for cancellation they can never receive

**Status:** Implemented.

**Verification:** The three NeoForge subscribers do not opt into canceled events. The event-bus annotation defaults receiveCanceled to false. Direct test invocations currently create uncanceled events.

**Planned action:** Remove cancellation checks inside these subscribers. Retain cancellation checks after explicitly posting destination-consultation events: those inspect the result and are necessary. Preserve side checks, useBlock/useItem denial handling, priorities, and cancellation results.

**Implementation result:** Removed the redundant isCanceled checks from NeoForgeClientEvents and NeoForgeStackInteractionEvents. Retained integrated-server side checks, priorities, useBlock/useItem decisions, cancellation results, and explicit protection-event result checks.

**Original issue text (verbatim):**

> ### 3. NeoForge listeners check for cancellation they can never receive
> - **Severity:** Low
> - **Confidence:** Certain
> - **Where:** neoforge/src/main/java/com/github/crittscott/somestacks/server/StackInteractionEvents.java:19-21; neoforge/src/main/java/com/github/crittscott/somestacks/client/ClientEvents.java:64, 86
> - **In plain English:** The code asks "did another mod already block this click?" inside handlers that NeoForge never calls when another mod has blocked the click.
> - **Consequence:** A reader assumes these handlers see cancelled events and may build logic around that, for example expecting the LOWEST-priority server handler to observe other mods' vetoes through `isCanceled()`. In fact the event bus filters cancelled events out before they arrive.
> - **Action:** Delete the `isCanceled()` checks. If seeing cancelled events is ever wanted, use `@SubscribeEvent(receiveCanceled = true)`.
> - **Technical detail:** None of these `@SubscribeEvent` annotations sets `receiveCanceled`, which defaults to false, so the bus never delivers a cancelled `RightClickBlock` or `RightClickItem` to them.

#### GD-04: Gesture handlers re-check permissions vanilla already enforced on the same click

**Status:** Retained pending target-contract evidence.

**Verification:** Clicked-position and spectator checks remain. Normal packet handling may duplicate some of them, but the handlers are also called directly by shared tests, and loader callbacks precede spectator handling. The exact target packet/loader permission ordering has not been independently established from permitted source.

**Planned action:** Keep destination authorization and callback spectator checks. Do not remove clicked-position checks or the native-use spectator gate merely on the report's assertion. Revisit only with the target entry-path contract and coverage showing ordinary clicks, spectator clicks, protection vetoes, and reentrant consultations still behave correctly.

**Implementation result:** No permission or spectator checks were removed. Clicked-position checks, destination authorization, and consultation guards remain.

**Original issue text (verbatim):**

> ### 4. Gesture handlers re-check permissions vanilla already enforced on the same click
> - **Severity:** Low
> - **Confidence:** Likely
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/server/StackInteractions.java:133 (`player.isSpectator() || WorldEdits.isProtected(player, pos)` in `applyExistingStack`), 75 (`isProtected(player, pos)` in `handleSneakingRotation`), 111 (`isProtected(player, clickedPos)` in `handleAdjacentClick`)
> - **In plain English:** Before your click on a block reaches the mod, the game has already checked that you're not a spectator and are allowed to touch that block (spawn protection, world border). The mod checks the same things again.
> - **Consequence:** A maintainer can't tell which checks protect against something real. The spectator check in the loader callbacks is real, because those events fire before vanilla's spectator branch. The checks on the clicked position are not. Keeping both makes it harder to see which protection actually applies to the neighboring destination block, which is the one vanilla did *not* check.
> - **Action:** Remove `player.isSpectator()` and `isProtected(player, pos)` from `applyExistingStack`, which is reached only from `Block.useItemOn` and `useWithoutItem`. Remove the `isProtected` check on the clicked position from `handleSneakingRotation` and `handleAdjacentClick`. Keep the spectator checks in those two loader-callback paths, and keep every check on the destination position (`mayUseAdjacent`, and `isProtected` plus `mayUseItemAt` in `placeAndDeposit`).
> - **Technical detail:** `ServerGamePacketListenerImpl.handleUseItemOn` gates on `level.mayInteract(player, pos)` before calling `ServerPlayerGameMode.useItemOn`. The loader events (NeoForge `RightClickBlock`, Fabric `UseBlockCallback`) fire inside that method, so after the gate. Spectators return from `useItemOn` before `Block.useItemOn`/`useWithoutItem` is called. The `consultingAdjacent` re-fire already returns early through `isConsultingAdjacent()`.

#### GD-05: Deposit, extract, and rotate paths check the same conditions two or three times

**Status:** Implemented.

**Verification:** Deposit and rotation dispatch compare BlockType and then the corresponding block-entity class. Singles/Bar deposit callers duplicate index/empty checks owned by canDepositAt. Storage extraction substitutes ItemStack.EMPTY for an already-empty hand.

**Planned action:** Resolve the block entity once and dispatch by its class in deposit and block rotation. Let depositAt/canDepositAt own positional admission checks, and pass the hand stack directly to extraction. Preserve policy, protection, support, creative-hand handling, feedback, and ordinary extraction consumption.

**Implementation result:** Deposit and block rotation resolve one block entity and dispatch by class. Deposit callers delegate range/empty/support decisions to depositAt, and Storage extraction receives the original hand stack directly. Per-type mutation behavior and feedback remain.

**Original issue text (verbatim):**

> ### 5. Deposit, extract, and rotate paths check the same conditions two or three times
> - **Severity:** Low
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/server/StackInteractions.java:211-213, 224-225, 238-239 (`type == X && getBlockEntity(pos) instanceof XBE`), 228, 241 (`index >= 0 && getStackInSlot(index).isEmpty()` before `depositAt`, which runs `canDepositAt` with the same checks), 347-351 (`handStack.isEmpty() ? ItemStack.EMPTY : handStack`), 401-407 (`type == ... && instanceof ...` in `rotateBlock`)
> - **In plain English:** To decide what kind of stack you clicked, the code checks both the block's name and the kind of storage inside it, which always agree. It also checks that a slot is empty and on the board, then calls a method that checks the same things again.
> - **Consequence:** Every change to deposit rules has to be made consistently in up to three places, or the outer and inner checks drift apart. A future edit that relaxes `canDepositAt` would silently do nothing because the caller still refuses.
> - **Action:** Dispatch on the block entity alone (`if (level.getBlockEntity(pos) instanceof StorageStackBE storage)`, and so on) and drop the `BlockType` lookup in `deposit` and `rotateBlock`. Call `depositAt(index, handStack)` directly and let `canDepositAt` own the index and empty-slot checks. Pass `handStack` straight to `extractAt`.
> - **Technical detail:** Each stack block creates exactly one block-entity class, so the type and the `instanceof` are equivalent. `SinglesStackBE.canDepositAt`/`BarStackBE.canDepositAt` already test `index >= 0 && index < SLOTS && getStackInSlot(index).isEmpty()`. `ItemOps.canTakeIntoHand` treats any empty stack as empty, so substituting `ItemStack.EMPTY` changes nothing.

#### GD-06: Item encoding failure is wrapped twice and checks an impossible output shape

**Status:** No change under the qualified scope.

**Verification:** Encoding is wrapped at slot level and again at block location level. Those layers supply different context, not duplicate information. The explicit compound-shape branch checks an invariant of the nonempty item codec.

**Planned action:** Use a direct compound cast once the codec map-output contract is established for the target. Retain slot and dimension/position context and the original cause; do not introduce a new exception framework just to shorten the chain. Never turn encode failure into partial inventory serialization. No save-diagnostic change is required solely because there are two wrappers.

**Implementation result:** Kept the slot-level encoding exception, block-location wrapper, original cause, and explicit output-shape check. The target codec contract needed to justify the cast was not established; exception-chain length alone did not warrant a new mechanism. Serialization still fails without publishing a partial inventory.

**Original issue text (verbatim):**

> ### 6. Item encoding failure is wrapped twice and checks an impossible output shape
> - **Severity:** Low
> - **Confidence:** Likely
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/util/StackItemStorage.java:181-191; common/src/main/java/com/github/crittscott/somestacks/block/StackBlockEntity.java:406-411
> - **In plain English:** When saving a stack's items, the code is ready for two failures. One is an item that can't be written, and it reports that twice, in two different places. The other is the game's item writer producing something other than a record, which it never does.
> - **Consequence:** A save failure produces a doubly wrapped exception (`IllegalStateException` → `IllegalStateException` → cause), which is noisier for the maintainer reading a crash report. The `instanceof CompoundTag` branch is dead code that implies `ItemStack.CODEC` might emit a non-map.
> - **Action:** Add the slot and location context in one place. `StackBlockEntity.saveAdditional` knows the position, so it can include the slot if `serializeNBT` passes it in the exception message. Replace the `instanceof` check with a plain `(CompoundTag)` cast.
> - **Technical detail:** `ItemStack.CODEC` is a map codec over a record, so `ops.convertTo(NbtOps.INSTANCE, encoded)` always yields a `CompoundTag` for a non-empty stack, and empty stacks are skipped before encoding.

#### GD-07: Side and attachment checks for things vanilla already guarantees

**Status:** Retained; blanket removal rejected.

**Verification:** Storage onPlace retains a side check; both render-preparation paths allow a missing level; NeoForge capture callers check snapshot availability before reading snapshot zero. Local capture returns setBlock's boolean, not a mod-owned guarantee that a snapshot was appended. The grid helper explicitly declares a nullable level.

**Planned action:** Keep snapshot checks until the target capture guarantee is established. Keep the onPlace side check until its target hook contract is established. Renderer null handling may be tightened only by explicitly changing the helper's supported attachment contract and its callers; it is not proven dead merely from the normal renderer path. Do not remove these checks as one batch.

**Implementation result:** Existing onPlace side checks, nullable renderer-level handling, and NeoForge snapshot-availability checks remain. The new Fabric client-update mixin has a separate necessary side/attachment gate so disk loads and unrelated block entities keep native behavior.

**Original issue text (verbatim):**

> ### 7. Side and attachment checks for things vanilla already guarantees
> - **Severity:** Low
> - **Confidence:** Likely
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/block/StorageStackBlock.java:98 (`!level.isClientSide()` in `onPlace`); common/src/main/java/com/github/crittscott/somestacks/client/BarStackBER.java:67 and common/src/main/java/com/github/crittscott/somestacks/client/CubeRenderHelper.java:252 (`level == null` inside block-entity rendering); neoforge/src/main/java/com/github/crittscott/somestacks/server/NeoForgeEditAuthority.java:54 and neoforge/src/main/java/com/github/crittscott/somestacks/server/NeoForgeGrowthPlacement.java:100 (`snapshots.isEmpty()` after a capture that returned true)
> - **In plain English:** The code asks "am I on the client?" in a hook the game only runs on the server. It asks "is this block in a world?" while drawing a block that is in the world. And it asks "was anything recorded?" right after the game said it recorded the change.
> - **Consequence:** Each one suggests to a reader that the impossible case can happen, which sends future debugging in the wrong direction and adds branches that tests never reach.
> - **Action:** Remove these checks. `StoragePile.at` already refuses client levels, so the `onPlace` body needs no extra check.
> - **Technical detail:** `LevelChunk.setBlockState` invokes `onPlace` only on the server. Block-entity renderers are fed from the client level's loaded block entities, which always have a level. With `captureBlockSnapshots` set, a `setBlock` that returns true has recorded a snapshot.

#### GD-08: Nullability annotations and docs that contradict the code

**Status:** Implemented surviving corrections.

**Verification:** Storage extractAt still marks playerHand nullable but passes it to canTakeIntoHand, which dereferences it. Both supportSeamBeneath helpers accept a nullable Level and return nullable seam arrays. Current seam callers supply attached entities or explicit live levels. Copied canDepositAt documentation and Bar's primitive-return annotation were already corrected; the reviewed extraction convenience overload is absent.

**Planned action:** Make playerHand nonnullable. Give seam helpers an explicit live-level input contract and remove nullable input handling after checking every caller; preserve the nullable returned seam because no same-type block below is normal. Retain the corrected documentation and avoid recreating already-deleted overloads.

**Implementation result:** Storage extraction no longer annotates playerHand nullable. Both supportSeamBeneath inputs require a live level and no longer substitute missing levels for world support; returned seams remain nullable when there is no same-type block below. Previously corrected docs/overloads remain corrected or absent.

**Original issue text (verbatim):**

> ### 8. Nullability annotations and docs that contradict the code
> - **Severity:** Low
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/block/StorageStackBE.java:155, 160 (`@Nullable ItemStack playerHand`, which then goes into `canTakeIntoHand` and throws a NullPointerException if null); common/src/main/java/com/github/crittscott/somestacks/block/BarStackBE.java:211-216 (`@Nullable` on a `boolean` method, with javadoc copied from `supportSeamBeneath`); common/src/main/java/com/github/crittscott/somestacks/block/SinglesStackBE.java:212-217 (same copied javadoc); BarStackBE.java:233 and SinglesStackBE.java:234 (`@Nullable Level level` plus a `level != null` check, though every caller passes a live level)
> - **In plain English:** Some labels in the code say "this might be missing" about things that are never missing, or that would crash if they were.
> - **Consequence:** A maintainer trusting `@Nullable ItemStack playerHand` could pass null and crash the server on a player click. The copied javadoc on `canDepositAt` describes a different method entirely.
> - **Action:** Remove `@Nullable` from `playerHand`, `canDepositAt`, and the `level` parameter of `supportSeamBeneath`. Drop the `level != null` test there. Rewrite the `canDepositAt` javadoc to describe its own result (whether the cell is in range, empty, and supported). Delete the unused two-argument `StorageStackBE.extractAt(int, int, ItemStack)` overload if nothing calls it; only the four-argument form is used in main code.
> - **Technical detail:** `ItemOps.canTakeIntoHand(hand, offer)` calls `hand.isEmpty()` unconditionally. `supportSeamBeneath` is called from `canDepositAt` (with `this.level`, on an attached server block entity), from `supportsFreshDeposit` (with a server `Level`), and from `extractAt` after `Objects.requireNonNull(level)`.

#### GD-09: Startup-order and value-range guards against our own code

**Status:** Implemented surviving startup cleanup.

**Verification:** Both entrypoints install platform services and server-config backends before their use. ServerConfig.reload returns backend availability, not parsing success. Tests consume that boolean, although production callers do not. ServerConfig.save also reports genuine backend persistence failure. The settlement stack-size clamp is already absent.

**Planned action:** Make reload void and use the installed backend directly; adjust tests to assert resulting policy. Remove no-backend fallback behavior from save while retaining its meaningful persistence-success result. Simplify platform installation/access under the established startup contract without pretending null checks validate third-party input. Do not remove backend I/O failure reporting.

**Implementation result:** ServerConfig.reload is void and directly uses the installed backend; tests inspect resulting policy. save directly delegates while retaining the persistence-success boolean. PlatformServices installation/access no longer assert against the established startup order. Genuine backend file-read/write reporting remains; settlement already used the item maximum directly.

**Original issue text (verbatim):**

> ### 9. Startup-order and value-range guards against our own code
> - **Severity:** Low
> - **Confidence:** Likely
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/ServerConfig.java:59-64 (`backend == null` checks; the `reload()` boolean is ignored by both callers, SsCommand.java:474 and SomeStacksServer.java:79); common/src/main/java/com/github/crittscott/somestacks/PlatformServices.java:277-288 (double-install check and three `requireNonNull` calls); common/src/main/java/com/github/crittscott/somestacks/block/StoragePile.java:391 (`Math.max(1, group.model.getMaxStackSize())`)
> - **In plain English:** The code guards against the mod forgetting to set itself up at launch, and against an item whose stack size is zero. Neither can happen.
> - **Consequence:** The ignored `reload()` return value suggests that a failed reload is handled somewhere, but it isn't. The `Math.max(1, ...)` hints that a stack limit of zero is possible and could hang settlement, which sends a reader looking for a problem that vanilla rules out.
> - **Action:** Make `ServerConfig.reload()` and `save()` call the backend directly. Make `reload()` return `void`. Reduce `PlatformServices.install` to an assignment. Use `group.model.getMaxStackSize()` directly in settlement.
> - **Technical detail:** Both loader entrypoints install `PlatformServices` and `ServerConfig` in their constructors before any world or command code can run. Vanilla validates `DataComponents.MAX_STACK_SIZE` to 1..99, so `getMaxStackSize()` is never below 1.

### Legacy and layered functions

#### LG-02: Slot storage still copies Forge's item-handler API, including an unused simulate mode

**Status:** Implemented.

**Verification:** Across common, Fabric, and NeoForge main and GameTest sources, all 112 insertItem calls and all 12 extractItem calls pass false. StackItemStorage is the sole SlotAccess implementation, and there are no external getSlotLimit or isItemValid calls. The old three-loader comment was already fixed in batch 1.

**Planned action:** Remove simulation only from local SlotAccess/StackItemStorage mutation methods and update all callers. Keep StackRunItemAccess simulation and native loader transactions: those are used. Reduce SlotAccess to a read-only slot view; change mutation-bearing uses such as StackRun.handlerOf to StackItemStorage. Keep admission/limit hooks protected on the mutable storage. Rename NBT save/load methods consistently without changing the persisted envelope or recovery behavior.

**Implementation result:** SlotAccess now exposes only getSlots/getStackInSlot. StackItemStorage owns immediate two-argument insertItem/extractItem, protected admission/limit hooks, and save/load names. Updated all 124 local mutation calls and mutation-bearing StackRun.handlerOf. Run-level simulation and native transaction journals remain. The nested saved envelope and unreadable-entry recovery are unchanged.

**Original issue text (verbatim):**

> ### 2. Slot storage still copies Forge's item-handler API, including an unused simulate mode
> - **Severity:** Medium
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/util/SlotAccess.java:33, :42, :45, :48; common/src/main/java/com/github/crittscott/somestacks/util/StackItemStorage.java:21, :74-108, :112-138, :140-148, :173-212; all callers pass `false`: BarColumn.java:153, :269, :273; SinglesColumn.java:132; StoragePile.java:166, :239, :311; BarStackBE.java:158, :187, :323; SinglesStackBE.java:169, :199, :260, :299; StorageStackBE.java:178
> - **In plain English:** Each stack's internal item list is modeled on an old Forge interface. That interface has a "just pretend" mode for checking whether an insert or removal would work. Nothing in the mod or its tests ever uses that mode, because both loaders now do their own trial runs.
> - **Consequence:** Every new contributor has to understand and keep correct a dual-mode insert/extract path with half of it dead. The `simulate` branches in `insertItem`/`extractItem` are untested, so a later edit can break them without anyone noticing. `getSlotLimit`/`isItemValid` are public interface methods that nothing outside `StackItemStorage` calls, which makes the contract look larger than it is. The class comment still says "all three loaders", though Forge is no longer a target.
> - **Action:** Remove the `simulate` parameter and its branches from `SlotAccess.insertItem`/`extractItem` and `StackItemStorage`. Drop `getSlotLimit` and `isItemValid` from `SlotAccess`, keeping them as protected hooks on `StackItemStorage`, where `StackBlockEntity` overrides them. Since `StackItemStorage` is the only implementation, consider shrinking `SlotAccess` to the read-only view (`getSlots`, `getStackInSlot`) that `ItemOps`, `*CubeIdx`, `ViewRay`, `BarDropBatch` and `CubeRenderHelper` actually use. Rename the Forge `INBTSerializable`-style `serializeNBT`/`deserializeNBT` to plain save/load names. Fix the "all three loaders" comment.
> - **Technical detail:** `grep` finds no `insertItem(..., true)` or `extractItem(..., true)` anywhere in main or gametest sources. It also finds no external call to `.getSlotLimit(` or `.isItemValid(`. Loader transactions stage through `StackRunItemAccess.acceptsAt` and their own journals, not through slot-level simulation.

#### LG-04: "No player means automation" is decided again at every removal site

**Status:** Implemented.

**Verification:** Singles, Bar, and Storage cleanup sites repeat null-actor selection between WorldEdits overloads. Storage growth also resolves the player/automation choice locally. Shared growth preflight already exists from batch 1.

**Planned action:** Resolve a nullable actor once inside actor-aware WorldEdits removal and placement-query entry points that receive the level explicitly. Update all cleanup and native growth callers; remove forwarding overloads, Singles' local forwarding helper, and redundant ternaries/editor selection where the new boundary covers them. Preserve Storage's mixed-cause attribution policy and pass a resolved actor to actual native placement and callbacks.

**Implementation result:** WorldEdits resolves nullable actors centrally for mayPlace, placeChecked, and removeChecked. Updated every growth/removal caller, removed the automation-only overloads, StoragePile.editor, Singles forwarding helper, and repeated cleanup ternaries. Storage mixed-cause tracking and native event attribution remain intact.

**Original issue text (verbatim):**

> ### 4. "No player means automation" is decided again at every removal site
> - **Severity:** Low
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/block/SinglesStackBE.java:359-364; common/src/main/java/com/github/crittscott/somestacks/block/BarStackBE.java:276-278; common/src/main/java/com/github/crittscott/somestacks/block/StoragePile.java:340-342, :445-447; common/src/main/java/com/github/crittscott/somestacks/server/WorldEdits.java:46, :140-142
> - **In plain English:** When the mod removes an emptied stack block, it has to say whether a player or the mod's own automation did it. Four places each work out that answer separately, with the same one-liner.
> - **Consequence:** If attribution rules change (for example, to record the last real player for mixed causes, as `StorageStackBE.noteCleanupActor` already does for settles), each copy must be found and changed. A missed copy would make protection and logging mods see the wrong actor for some stack types only.
> - **Action:** Give `WorldEdits.removeChecked` and `WorldEdits.mayPlace` a single signature that takes `@Nullable ServerPlayer actor` and resolves `automationActor(level)` inside. Then delete the `ServerLevel`-only overloads, `SinglesStackBE.removeChecked`, `StoragePile.editor`, and the inline ternaries.
> - **Technical detail:** Each site is `actor == null ? WorldEdits.removeChecked(level, pos) : WorldEdits.removeChecked(actor, level, pos)`. The `ServerLevel` overload itself is just `removeChecked(automationActor(level), level, pos)`.

#### LG-05: Thin pass-through methods and two homes for the data version stamp

**Status:** Implemented surviving consolidation.

**Verification:** The public capacity/admission methods forward to protected hooks, and SsCommand.isPlayer only calls source.isPlayer. Data-version key literals were consolidated in batch 1. Migration stamping and ordinary ValueOutput saving target different representations and occur at different stages.

**Planned action:** Expose one capacity method and one admission method per block entity; update subtype overrides, internal storage, and both loader adapters. Inline SsCommand.isPlayer. Keep native NBT migration stamping and ValueOutput saving with the shared key; do not route typed saving through extra raw-tag machinery merely to make both use the same writer.

**Implementation result:** Made localSlotLimit and isStoredItemValid the public block-entity capacity/admission methods used internally and by both loader adapters; removed automationSlotLimit/acceptsAutomation forwarders and updated overrides/tests. Inlined source.isPlayer. Kept the shared DataVersion key and representation-appropriate migration and ValueOutput writers.

**Original issue text (verbatim):**

> ### 5. Thin pass-through methods and two homes for the data version stamp
> - **Severity:** Low
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/block/StackBlockEntity.java:198-205, :376, :405; common/src/main/java/com/github/crittscott/somestacks/block/StackDataMigration.java:35, :70-72; common/src/main/java/com/github/crittscott/somestacks/command/SsCommand.java:200-202
> - **In plain English:** Some functions do nothing but call another function under a different name. Separately, the "which game version wrote this" stamp on saved data is written two different ways in two different files.
> - **Consequence:** `automationSlotLimit()` and `localSlotLimit()` are the same value under two names, and so are `acceptsAutomation()` and `isStoredItemValid()`. Readers have to check that they really are identical. Writing `DataVersion` with `output.putInt(...)` in `saveAdditional` and with `NbtUtils.addCurrentDataVersion` in `StackDataMigration.stampVersion`, plus the `"DataVersion"` literal repeated in `loadClientUpdate` and `PERSISTED_FIELDS`, means a change to the stamp has three places to miss.
> - **Action:** Make `localSlotLimit()` and `isStoredItemValid()` public and delete `automationSlotLimit()`/`acceptsAutomation()`, updating the two loader adapters. Replace `SsCommand.isPlayer(source)` with `source.isPlayer()`. Define the `DataVersion` key once in `StackDataMigration`. Use vanilla `NbtUtils.addCurrentDataVersion`/`getDataVersion` consistently, writing the stamp through one method.
> - **Technical detail:** `automationSlotLimit` is called from FabricRunItemStorage.java:153, :157, :332 and NeoForgeRunResourceHandler.java:82, :84, :88. `acceptsAutomation` is called from FabricRunItemStorage.java:149 and NeoForgeRunResourceHandler.java:76.

#### LG-07: Names left over from earlier designs

**Status:** Implemented.

**Verification:** ClientRenderPacketSink receives and stages configuration, not render overrides. Packet/receiver enableStack and StackState.enableStackBlock refer specifically to Storage.

**Planned action:** Rename the receiver ConfigSyncReceiver and use Storage-specific flag names consistently in sender, receiver, client state, registrations, and tests. Preserve packet field order, wire representation, byte/count bounds, chunk staging, atomic publication, and disconnect cleanup. This finding does not authorize a protocol redesign.

**Implementation result:** Renamed ClientRenderPacketSink to ConfigSyncReceiver and Storage flags to enableStorage/enableStorageBlock across production and tests. Packet type ID, field order, binary encoding, bounds, generation/chunk staging, atomic publication, and disconnect handling remain unchanged.

**Original issue text (verbatim):**

> ### 7. Names left over from earlier designs
> - **Severity:** Low
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/client/ClientRenderPacketSink.java:11, :15; common/src/main/java/com/github/crittscott/somestacks/network/ConfigSyncPkt.java:22; common/src/main/java/com/github/crittscott/somestacks/client/StackState.java:13
> - **In plain English:** Some names describe what the code used to do. The class that receives the server's settings is called a "render packet sink". The Storage Stack is still called just "Stack" in the settings sync.
> - **Consequence:** Someone searching for the config-sync receiver won't find it by name. They might also assume a render-related packet still exists, which is exactly the removed feature from finding 6. `enableStack` next to `enableSingles`/`enableBar` reads as a fourth, generic toggle.
> - **Action:** Rename `ClientRenderPacketSink` to something like `ConfigSyncReceiver`. Rename `enableStack`/`enableStackBlock` to `enableStorage`/`enableStorageBlock` in `ConfigSyncPkt`, `ClientRenderPacketSink` and `StackState`. These are code-only renames; the wire layout does not change.
> - **Technical detail:** `ClientRenderPacketSink.apply(ConfigSyncPkt)` only stages config chunks and publishes `StackState` flags and `ClientRenderToolState` lists.

### Loader divergence

#### LD-02: NeoForge-only block-entity update hooks live in common code and give the two loaders different client update paths

**Status:** Implemented guarded native ingress and client-path checks; runtime validation pending.

**Verification:** Common StackBlockEntity declares NeoForge hook names without compile-time override checking. Shared tests invoke them on Fabric and do not establish coverage of Fabric's actual client load branch. NeoForge's published 26.3.x IBlockEntityExtension defaults for both hooks call loadWithComponents.

**Planned action:** First separate shared validation tests from loader ingress tests. Exercise accepted and malformed chunk/block updates on actual client-attached entities. Then unify ingress through the native client load path if rejection still leaves inventory, subtype metadata, and inherited component/attachment state unchanged. If native loading mutates inherited state before validation, retain validation at a loader-specific ingress boundary with compile-time override checking rather than deleting it. Simply replacing hook tests with loadClientUpdate calls does not close the coverage gap.

**Implementation result:** Removed NeoForge-named hooks from common StackBlockEntity. NeoForgeRegistry registers NeoForgeStackBlockEntities.Storage/Singles/Bar subclasses whose @Override hooks validate before loadWithComponents. Common stack blocks create entities through registered types so live placement also receives those subclasses. Fabric uses StackBlockEntityClientUpdateMixin at loadWithComponents HEAD, client-side only. Both boundaries call the shared nonmutating validator; accepted input then uses native inherited loading and the common client load branch. Shared server tests call shared validation only. /ssclienttest invokes native initial/subsequent entry points on registered client-attached scratch entities for all types, checks valid inventory/component replacement, and rejects malformed version/inventory/server-only data without changing inventory, inherited components, or prepared presentation.

**Original issue text (verbatim):**

> ### 2. NeoForge-only block-entity update hooks live in common code and give the two loaders different client update paths
> - **Severity:** Medium
> - **Confidence:** Likely
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/block/StackBlockEntity.java:341-372; common/src/gametest/java/com/github/crittscott/somestacks/gametest/CapabilityAndPersistenceChecks.java:307-309; common/src/gametest/java/com/github/crittscott/somestacks/gametest/SynchronizationChecks.java:345-347, 391-393
> - **In plain English:** When the server tells a client what is in a stack, the two loaders take different routes into the same checking code. The shared tests use NeoForge's route even when they run on Fabric, so Fabric's real route is never tested.
> - **Consequence:** On Fabric, updates arrive through `loadAdditional`'s client branch, which also calls `super.loadAdditional`. On NeoForge, the `handleUpdateTag`/`onDataPacket` overrides skip `super.loadAdditional`, so anything the base class loads (components, attachments) is handled differently on each loader. The overrides have no `@Override` because common code cannot see NeoForge. If NeoForge renames or re-signatures either hook, nothing fails to compile and the override silently stops applying. The shared sync tests would still pass because they call the methods directly. A maintainer who breaks Fabric's client branch also gets no failing test, because the tests never go through it.
> - **Action:** Delete `handleUpdateTag` and `onDataPacket` from `StackBlockEntity`. NeoForge's default `IBlockEntityExtension` hooks load through `loadWithComponents` → `loadAdditional`, which reaches the same client branch Fabric uses, so both loaders share one path. Change the shared checks to call `loadClientUpdate` (the shared validation) in place of the two hooks.
> - **Technical detail:** The javadoc at line 364 says it directly: "NeoForge chunk-update hook; vanilla/Fabric loads use the client branch of loadAdditional." The client branch at lines 344-349 already does `loadClientUpdate` and then `super.loadAdditional(input)` on success. The NeoForge hooks call only `loadClientUpdate`. Confidence is Likely, not Certain, only because I have not checked NeoForge's default hook bodies here.

#### LD-03: Server-config defaults and key names are defined separately in each loader's backend

**Status:** Implemented.

**Verification:** Fabric obtains fallbacks from ServerConfig.defaults, while NeoForge hardcodes several booleans and empty lists. They currently agree, but future shared-default changes would diverge. Section/key strings are duplicated. NeoForge onLoading and onReloading repeat their bodies.

**Planned action:** Make every NeoForge default derive from one ServerConfig.defaults snapshot. Put the existing section and key names in common constants and reference them from both backends. Factor the matching NeoForge config-event body. Preserve all file names, setting names, per-field fallback/clamping, list filtering, and loader-native config lifecycle.

**Implementation result:** Moved all 15 config section/key constants to ServerConfig and used them in both backends. Every NeoForge default comes from one ServerConfig.defaults snapshot. onLoading/onReloading share onConfigChanged. Existing JSON/TOML names, filenames, ranges, per-field fallback, list filtering, and native config lifecycle remain.

**Original issue text (verbatim):**

> ### 3. Server-config defaults and key names are defined separately in each loader's backend
> - **Severity:** Medium
> - **Confidence:** Certain
> - **Where:** neoforge/src/main/java/com/github/crittscott/somestacks/NeoForgeServerConfig.java:35-79, 85-99; fabric/src/main/java/com/github/crittscott/somestacks/JsonServerConfig.java:21-44, 48-50; common/src/main/java/com/github/crittscott/somestacks/ServerConfig.java:504-517
> - **In plain English:** The default settings and setting names are written in more than one place. Changing a default in the shared code changes it on Fabric but not on NeoForge.
> - **Consequence:** If a maintainer changes a default in `ServerConfig.defaults()` (for example, enabling galleries or adding a mod to `disable_mods`), Fabric servers pick it up and NeoForge servers keep the old value. Server admins on the two loaders then get different out-of-the-box behavior with nothing to show why. Renaming a key means editing two hand-typed string sets and keeping them identical by eye.
> - **Action:** In `NeoForgeServerConfig`, take every default from `ServerConfig.defaults()`: `enableStorageStackBlock()`, `enableSinglesStackBlock()`, `enableBarStackBlock()`, `galleryEnabled()`, `disableMods()`, `genMods()`, `genItems()`, as `max_pile_height`, `disable_items`, permission level and placements already do. Move the section and key names (`piles`, `max_pile_height`, `stacks`, `enable_*`, `compatibility`, `render_gallery`, and so on) into `ServerConfig` constants and use them in both backends. While there, merge the identical `onLoading`/`onReloading` bodies into one private method.
> - **Technical detail:** NeoForge hardcodes `.define("enable_storage_stack_block", true)` and the other enable flags (lines 42, 45, 48), `.define("enabled", false)` (66), and `List.<String>of()` for `disable_mods`, `gen_mods`, and `gen_items` (55, 76, 79). Fabric builds every fallback from `ServerConfig.defaults()` (line 50) but declares its own `SECTION_*`/`KEY_*` strings (24-39), which duplicate NeoForge's literals.

#### LD-06: NeoForge classes are named and wired as if they were the whole mod

**Status:** Implemented naming cleanup; registration-style rewrite excluded.

**Verification:** Several NeoForge classes have generic names while Fabric counterparts have loader prefixes; the MODID alias and loader-named platform-service subpackages exist. Mixed annotation/explicit registration is present, but ClientEvents.init only installs the packet sender and does not register its annotated listeners twice. Both registration styles are supported loader mechanisms.

**Planned action:** Rename the generic NeoForge outer-layer classes with NeoForge prefixes, remove the redundant MODID alias, and place both platform-service implementations consistently beside their loader entrypoints. Update all production/test references. Keep sender initialization distinct from listener registration. Do not rewrite every registration solely to enforce one syntax; preserve event buses, priorities, cancellation filtering, and physical-client isolation.

**Implementation result:** Renamed ModRegistry, ModNetworking, ClientEvents, ClientSetup, KeyMappings, and StackInteractionEvents with NeoForge prefixes. Removed SomeStacksNeoForge.MODID. Both platform-service classes now sit in the root package beside their entrypoints. Updated source/test references, retaining distinct sender installation, native listener styles, event buses, priorities, and physical-client isolation.

**Original issue text (verbatim):**

> ### 6. NeoForge classes are named and wired as if they were the whole mod
> - **Severity:** Low
> - **Confidence:** Certain
> - **Where:** neoforge/src/main/java/com/github/crittscott/somestacks/ModRegistry.java; neoforge/.../network/ModNetworking.java; neoforge/.../client/ClientEvents.java:24, 28-30; neoforge/.../client/ClientSetup.java; neoforge/.../client/KeyMappings.java; neoforge/.../server/StackInteractionEvents.java:13; neoforge/.../SomeStacksNeoForge.java:29 (`MODID` alias, used 6 times in the module); fabric/.../fabric/FabricPlatformServices.java and neoforge/.../neoforge/NeoForgePlatformServices.java (package placement)
> - **In plain English:** The NeoForge code uses generic names like "ClientEvents" and "ModRegistry", while the matching Fabric code says "Fabric" in its names. That is left over from when the mod only ran on NeoForge, and it makes NeoForge-only code look like shared code.
> - **Consequence:** Both modules use the same packages as common code, so `com.github.crittscott.somestacks.client.ClientEvents` and `...client.KeyMappings` look like shared classes. A maintainer searching common code for where gestures are wired, or porting a change from Fabric's `FabricClientEvents`, has no naming cue for the counterpart. `SomeStacksNeoForge.MODID` is a second name for `SomeStacksCommon.MODID` that Fabric doesn't have. Within NeoForge, listeners are attached three ways (`@EventBusSubscriber` on `ClientEvents` and `StackInteractionEvents`, `addListener` in the entry point and `ClientSetup`, and `ClientEvents.init()` called by hand on a class that is also an annotated subscriber), so finding every handler takes three kinds of search.
> - **Action:** Rename the NeoForge classes to match the Fabric prefix convention: `NeoForgeRegistry`, `NeoForgeNetworking`, `NeoForgeClientEvents`, `NeoForgeClientSetup`, `NeoForgeKeyMappings`, `NeoForgeStackInteractionEvents`. Delete `SomeStacksNeoForge.MODID` and use `SomeStacksCommon.MODID`. Move the two `*PlatformServices` classes into the root package beside the other loader entry classes (or move all loader-only classes into `fabric`/`neoforge` subpackages, but pick one). Choose one listener-registration style for NeoForge, either `@EventBusSubscriber` throughout or `addListener` throughout.
> - **Technical detail:** Fabric names: `FabricRegistry`, `FabricNetworking`, `FabricClientEvents`, `FabricClientRendering`, `FabricKeyMappings`, `FabricStackInteractionEvents`, `FabricEditAuthority`. NeoForge already prefixes `NeoForgeEditAuthority`, `NeoForgeServerConfig`, `NeoForgeRunResourceHandler`, and `NeoForgeGrowthPlacement`, so the unprefixed ones are the older NeoForge-only layer.

#### LD-07: NeoForge vocabulary ("capability", "item handler") in common code and shared tests

**Status:** Implemented.

**Verification:** Common documentation still uses capability/item-handler vocabulary for loader-neutral automation. The shared persistence-check Javadoc names a wrapper that matches NeoForge but not Fabric. NeoForge test identifiers derive from its wrapper name and have checked-in JSON instances.

**Planned action:** Use loader-neutral automation-view terminology where it describes shared behavior; retain loader-specific terminology for actual native APIs. Rename the shared checks and both wrappers to matching AutomationAndPersistence names, mechanically updating NeoForge function/test-instance identifiers and JSON paths together. Keep serialized production registry IDs, including stack_be, unchanged.

**Implementation result:** Changed common descriptions to automation-view terminology while retaining native API terminology where appropriate. Shared checks and both wrappers use AutomationAndPersistence names. Updated NeoForge function references and renamed the matching test-instance directory/JSON IDs together. Production registry IDs, including stack_be, are unchanged.

**Original issue text (verbatim):**

> ### 7. NeoForge vocabulary ("capability", "item handler") in common code and shared tests
> - **Severity:** Low
> - **Confidence:** Certain
> - **Where:** common/src/main/java/com/github/crittscott/somestacks/block/BarStackBE.java:35-36, 98; SinglesStackBE.java:32-33, 66, 244; StorageStackBE.java:21, 65; StoragePile.java:31, 61; RunEdit.java:14; util/StackPlacement.java:19; common/src/gametest/java/com/github/crittscott/somestacks/gametest/CapabilityAndPersistenceChecks.java:62-66; SinglesColumnChecks.java:28; StoragePileChecks.java:29
> - **In plain English:** Shared code and shared tests use NeoForge's word for automation access ("capability"). Fabric doesn't use that word, and one comment points to a Fabric test file that doesn't exist under that name.
> - **Consequence:** The shared check class `CapabilityAndPersistenceChecks` says each loader has a `CapabilityAndPersistenceGameTests`, but Fabric's wrapper is `StorageAndPersistenceGameTests`. A maintainer following the javadoc to the Fabric counterpart won't find it. Comments like "a machine reading this block's item handler" describe a NeoForge/Forge interface the mod no longer exposes on either loader.
> - **Action:** In common code, replace "capability" and "item handler" with a loader-neutral phrase such as "automation view" (the term `orientation-code.md` already uses). Rename `CapabilityAndPersistenceChecks` to `AutomationAndPersistenceChecks` (or similar), and give the NeoForge and Fabric wrappers the same matching name.
> - **Technical detail:** NeoForge's own wrapper `CapabilityAndPersistenceGameTests` and test-instance folder `capabilityandpersistencegametests` would need renaming together with their JSON test instances under `neoforge/src/gametest/resources/data/somestacks/test_instance/`.

### Execution sequence and remaining validation

1. Simplify local storage and its block-entity contract (LG-02, the surviving parts of LG-05), then remove duplicated gesture admission checks and fix nullability (GD-05, GD-08). Update every main/GameTest caller. Preserve run-level simulation, native transactions, support, item ownership, change notification, and the saved format.
2. Consolidate actor selection in WorldEdits (LG-04). Check player cleanup, automated cleanup, mixed-cause Storage settlement, growth queries, and loader veto attribution.
3. Centralize config keys/defaults and simplify established startup contracts (LD-03, GD-09). Test both backends against common defaults, preserve malformed-field fallback, keep persistence failure reporting, and keep the unified reload behavior from batch 1.
4. Perform code-only naming/documentation cleanup (LG-07, LD-06, LD-07). Update both loaders and all matching test JSON/functions mechanically. Remove redundant subscriber cancellation checks (GD-03) while retaining explicit event-result checks and side/priority semantics.
5. Resolve native client ingress and add actual path coverage before removing common NeoForge hook names (LD-02). Check initial chunk updates and subsequent block updates with valid and malformed data on both clients; keep rejected updates atomic across all affected state.
6. Leave unestablished clicked-position, side, render-attachment, and snapshot guarantees alone (GD-04, GD-07). GD-06 allows only a narrow codec-invariant cleanup; keep contextual save-failure diagnostics. Do not redesign config synchronization, merge native transaction journals, rename saved registry IDs, or change the inventory format from report summaries or open questions alone.
7. The architecture orientation and this handoff reflect the implementation. The user must run both loaders' builds and GameTests and the development-client `/ssclienttest` checks. For LD-02, server GameTests alone do not exercise the client-only mixin, native overrides, or inherited-component rejection. No runtime success is claimed for Batch 2.

Published API evidence: [NeoForge event cancellation contract](https://docs.neoforged.net/docs/concepts/events/), [SubscribeEvent receiveCanceled default](https://raw.githubusercontent.com/neoforged/Bus/main/src/main/java/net/neoforged/bus/api/SubscribeEvent.java), and [NeoForge 26.3.x block-entity extension defaults](https://raw.githubusercontent.com/neoforged/NeoForge/26.3.x/src/main/java/net/neoforged/neoforge/common/extensions/IBlockEntityExtension.java). The extension source confirms the published branch's hook bodies; client loading and rejection behavior still require the target ingress checks described above.

### Batch 2 implementation locations and validation handoff

- Local storage: `common/src/main/java/com/github/crittscott/somestacks/util/SlotAccess.java`, `StackItemStorage.java`, `block/StackBlockEntity.java`, and `StackRun.java`. Mutation and save/load call sites were updated in all three modules' main/GameTest source sets.
- Actor attribution: `server/WorldEdits.java`, shared run/column/block-entity cleanup and growth, and NeoForge's `NeoForgeGrowthPlacement.prepare`.
- Configuration: common `ServerConfig` section/key constants and defaults, Fabric `JsonServerConfig`, NeoForge `NeoForgeServerConfig`, and common `PlatformServices` startup contracts. Config fixture tests no longer interpret reload availability as read success.
- Native client ingress: common `StackBlockEntity.isValidClientUpdate` and `StackItemStorage` share validation rules between a pure preflight and atomic inventory replacement. Fabric's `mixin/StackBlockEntityClientUpdateMixin.java` is registered in `fabric/src/main/resources/somestacks.mixins.json`. NeoForge's `block/NeoForgeStackBlockEntities.java` supplies compile-checked hooks through the registered factories; both loaders' common blocks instantiate through registered BlockEntityTypes.
- Client validation: shared `ClientCallbackChecks.checkClientUpdateIngress`, with a `ClientUpdate` callback supplied by each loader's `ClientCallbackGameTests`. Fabric invokes native `loadWithComponents`; NeoForge invokes `IBlockEntityExtension.handleUpdateTag/onDataPacket` on registered client-attached entities. These are entry-point checks on scratch entities, not fabricated network traffic. The fixtures carry changed native `components` alongside invalid stack data, and compare complete saved state and render-cache identity after rejection.
- Naming: use `ConfigSyncReceiver`, `NeoForgeRegistry`, `NeoForgeNetworking`, `NeoForgeClientEvents`, `NeoForgeClientSetup`, `NeoForgeKeyMappings`, `NeoForgeStackInteractionEvents`, and root-package `FabricPlatformServices`/`NeoForgePlatformServices`. Test checks/wrappers are `AutomationAndPersistenceChecks`/`AutomationAndPersistenceGameTests`; NeoForge resources are under `test_instance/automationandpersistencegametests`.
- First run builds and server GameTests for both loaders. Then run `/ssclienttest` on each development client while aiming at a stack with Shift and the modifier released. Confirm ordinary login/chunk loading and later inventory updates still display correctly, and repeat client checks after resource reload. Report these results separately from Batch 1.
