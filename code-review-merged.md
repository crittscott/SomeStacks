# Merged code review

This document merges `code-review-1.md`, `code-review-2.md`, and `code-review-3.md`, removing overlapping findings while retaining the distinct details and recommendations from each report. It is a synthesis of those reports only: no finding was independently rechecked, and the project was not built, run, or tested for this merge.

## Verdict

The broad architecture is recognizably Minecraft/Forge and mostly coherent with `living-spec.md`: the server owns mutations, block entities own persistent inventory, registration uses `DeferredRegister`, update NBT and Forge item capabilities are used, client-only rendering is separated appropriately, interaction-rule ordering is explicit, and there are no ticking block entities. The interaction-rule and spatial-index types are justified because ordering and shared geometry are genuine contracts.

The implementation is not ready to release or to call server-friendly. The principal blockers are Storage capability item loss/world mutation, an under-validated C2S surface, pile-contiguity failures, incomplete synchronization of derived state, dynamic collision not being declared, and client-owned sound configuration being consumed by the logical server. The admin workflow also needs proper permissions, reload tooling, diagnostics, and bounded test generation.

The important trust distinction is:

- C2S packets, config files, resource files, and third-party renderers are untrusted boundaries and need validation.
- Once validated code is operating on a block entity, capability, or function created by this mod, repeated checks for the mod's own invariants are unnecessary. Internal failures should generally fail loudly.

## Findings

### Critical — rejected Storage items can create empty towers or be deleted

Locations: `block/StorageStackBE.java:120-147,382-407` and `block/PileItemHandler.java:29-49,72-75`.

`StorageStackBE.deposit` does not reject an invalid Storage item at its boundary. Its local `ItemStackHandler` rejects an item from a disabled mod, but `deposit` interprets the unchanged remainder as overflow, creates a Storage Stack above, and recurses. A single automation insertion can therefore create empty Storage blocks up to the build limit while returning the original item. `PileItemHandler.isItemValid` advertises every item as valid, making this path available through normal automation.

There is also an item-loss path after an administrator disables a namespace whose partial stacks already exist. `mergeIntoHandler` computes how many items should merge, ignores the remainder returned by `handler.insertItem`, then shrinks the source and counts the predicted amount as moved. When the handler rejects the now-disabled item, those items disappear even though existing disabled contents are supposed to remain extractable.

The capability's simulation also disagrees with execution. Simulation considers only the requested local slot, while real insertion ignores that slot, searches the whole pile, and may create or use blocks above. Automation can be told that a valid insertion fails, be given the wrong accepted count, or choose a slot on semantics the real call does not honor.

Reject invalid input once at the entrance to `deposit`, leave the caller's stack unchanged, make `PileItemHandler.isItemValid` use the same predicate, and calculate moved counts from actual insertion remainders. Simulation and execution must model the same pile-wide operation. If ordinary slot semantics are preferable, expose those through the capability and keep pile-wide behavior on the player path.

### Critical — C2S mutations are not authorized as player interactions

Locations: `network/ModNetworking.java:23-40`; the handlers in `PlaceAndDepositPkt`, `DepositPkt`, `ExtractPkt`, `RotateBlockPkt`, `RotateItemPkt`, and `TogglePermanentPkt`; and `server/RightClickBlockSuppressor.java:31,51-53`.

The packets mutate server state, but they trust client-supplied intent in places where the client is not trustworthy:

- Message directions are not pinned at registration.
- The handlers do not consistently enforce loaded positions, normal interaction reach, line/target selection, spawn/build permission, or protection-mod authorization.
- `DepositPkt` can mutate any loaded Storage Stack without requiring the player to look at it.
- `ExtractPkt` calls `getBlockEntity` before an `isLoaded`/`hasChunkAt` check, allowing attacker-selected chunk access or loading. It also trusts the client-selected cell instead of recomputing the authoritative hit.
- The rotation packets do not require the specified sneaking/torch state, and `TogglePermanentPkt` does not require the specified empty hand.
- `PlaceAndDepositPkt` calls `mayUseItemAt`, but has no reach check, accepts the supplied face as truth, and uses direct `setBlock` rather than the normal obstruction, world-border, place-hook, and logging/protection path.
- Deposit, extraction, rotation, and toggling do not run an equivalent Forge interaction/authorization hook. Extraction mutates first, after which `RightClickBlockSuppressor` cancels the later vanilla event at high priority; downstream protection listeners may never receive a useful veto point.

This is not defensive programming against Forge failure; the remote client is an unreliable interface. Register explicit `PLAY_TO_SERVER`/`PLAY_TO_CLIENT` directions and use one small packet-boundary path for loaded position, reach, permission/protection, and packet-specific gesture prerequisites. Recompute gameplay-relevant targeting from server world state. Player-driven placement should run the Forge placement contract so protection and logging mods can observe or veto it. After that boundary, dispatch directly to authoritative block-entity operations rather than repeating their internal checks.

### High — `ExtractPkt` accepts indices outside Storage's slot range

Location: `network/ExtractPkt.java:48-66`.

The common validation permits indices `0..63`, but Storage exposes 27 slots. The Storage branch reads the capability slot before calling `StorageStackBE.extractAt`, so indices 27 through 63 can reach `ItemStackHandler.getStackInSlot` out of range on the server thread. Dispatch to the block entity's already-validating extraction operation or validate against the selected block entity's actual slot count. The packet does not need its redundant pre-read.

### High — Singles and Bar omit `dynamicShape()` for block-entity-dependent geometry

Locations: `ModRegistry.java:35-49`, `SinglesStackBlock.java:55-71`, and `BarStackBlock.java:55-71`.

Both blocks derive outline and collision shapes from block-entity contents, but their block properties omit `.dynamicShape()`. Minecraft can cache non-dynamic state geometry, so shape queries may use a result computed without the current block entity. Declare `.dynamicShape()` for both blocks. Their block-entity-local union caches remain useful for avoiding repeated 64-cell union work.

### High — pile cleanup can split a contiguous Storage pile

Locations: `block/StorageStackBE.java:270-302,323-373` and `network/ExtractPkt.java:81-92`.

`resortAndPackPile` processes at most the configured window, then removes empty blocks from the top of that list without checking for a Storage Stack immediately above the window. In a taller pile, consolidation can empty and delete the highest processed block while occupied, unprocessed blocks remain above it. This leaves a floating upper pile with a separate base and cooldown.

Player extraction has a related failure: after a throttled or no-op repack, `ExtractPkt` removes the clicked Storage block merely because that local block entity is empty, not because it is the true pile top. Capability extraction can instead leave an empty temporary block indefinitely when cooldown prevents the only cleanup attempt.

Only remove an empty Storage block when no Storage Stack exists directly above it. Contiguity cleanup must be correct independently of cooldown, and the top of a limited processing window must not be treated as the top of the pile. The corresponding boundary rule should also be made explicit in `living-spec.md` because “remove from the top of the processed window” is otherwise incompatible with a non-capacity-limiting window.

### High — Storage repacking leaves light and comparator state stale

Location: `block/StorageStackBE.java:29-45,326-363`.

Repacking enables `suppressSync`, clears and rewrites participating inventories, and finishes with only `setChanged` and client update tags. The suppression also bypasses light recalculation and neighbor notification. Sorting can therefore move luminous items and fill levels between blocks while their `LIGHT_LEVEL` states and adjacent comparator observations remain based on the old inventories.

Finish each batch with one authoritative contents-changed operation per affected block: recalculate and set light, notify comparator neighbors, mark dirty, and send one coherent block-entity update.

### High — Singles cascades publish inventory changes before rotation changes

Location: `block/SinglesStackBE.java:31-46,162-195`.

Each cascade extraction/insertion runs `onContentsChanged` and synchronizes before `cubeRotations` is moved. No final dirty/sync follows the rotation assignments, so clients receive the new inventory with stale rotations. Empty cells also retain old rotations, allowing a later unrelated deposit to inherit another item's orientation. Raw capability extraction has the same metadata-alignment problem.

Batch item and rotation movement together, clear rotations when cells become empty, then mark dirty and synchronize once after the final coherent state exists.

### High — resource-configured sounds work only by integrated-server accident

Locations: `client/ClientSetup.java:36-40`, `SoundConfig.java:45-51`, `ModSounds.java:13-42`, and the server-side `playSound` calls in the mutation packets.

`SoundConfig` is installed only as a physical-client resource listener but mutates static `ModSounds` fields that the logical server reads. An integrated server shares those statics; a dedicated server never runs the listener and keeps Java defaults. A client resource pack also cannot choose a different event after the server has already selected the `SoundEvent` id.

Choose one ownership model. Either make sound selection server-owned data/config and synchronize it, or send an action-sound message and let each client resolve its resource configuration locally. The latter most directly matches the living specification's client resource-reload language.

There is also a parallel dead sound path: six `RegistryObject<SoundEvent>` values are never registered or used, and the orphan OGG has no normal namespace-root `sounds.json`. Wire one sound system completely and remove the other.

### High — config reload synchronization is on the wrong bus and lacks an admin workflow

Locations: `SomeStacks.java:29-40,47-62`.

`ModConfigEvent.Reloading` is a mod-bus event, but `onConfigReload` is attached to `MinecraftForge.EVENT_BUS`. The intended live broadcast of enable flags and server render overrides therefore does not run through the prescribed Forge path. Register it on the existing `modBus` and restrict it to this mod's server config.

The reload path also calls `sendConfigSync` once per player, and that method reparses the override directory each time. Load once, build one immutable payload, and distribute it to the player list.

Editing `server_item_overrides` does not itself cause a Forge config event, and administrators have no direct reload command. Provide an operator/console-capable reload-and-rebroadcast command that reports success counts and parse failures.

### Medium — cross-block support depends on a client-supplied face

Location: `network/PlaceAndDepositPkt.java:91-164`.

The first item in a newly placed Singles or Bar block is checked against the block below only when `msg.face == Direction.UP`. Support is a property of the target world state, not of the face supplied by the client. Legitimate side-selected placement and forged packets can reach the same target while skipping the lower-block invariant.

Whenever `msg.pos.below()` is the same stack type, apply the cross-block support rule independently of `msg.face`. The duplicated Bar footprint-overlap calculation should use the same geometry operation as ordinary Bar grounding so the contracts cannot drift.

### Medium — capability lifecycle bypasses Forge's designated hooks

Locations: `block/StorageStackBE.java:53,232-236`, `block/SinglesStackBE.java:58,241-245`, and `block/BarStackBE.java:57,205-209`.

All three block entities keep a final `LazyOptional` and invalidate it from `setRemoved`. Forge's expected lifecycle is `invalidateCaps`, including `super.invalidateCaps()`, with recreation/revival when an instance becomes live again. Use the Forge hooks instead of coupling capability invalidation to a vanilla removal path.

### Medium — destructive test commands lack expected administrative controls

Locations: `command/SsCommand.java:49-79` and `command/TestWallGenerator.java:69-164`.

The entire `ss` tree requires only a creative `ServerPlayer`. `ss test all` can replace a large floor and thousands of blocks, so a creative builder can use it as a world-overwrite/lag tool while a survival operator or console cannot use administrative functions. Split permissions by subcommand: personal render-authoring can remain creative-only, while destructive test generation should require a normal operator level and may remain player-only if it needs a player-relative origin. A dry-run or size report would be useful before `test all`.

The success message counts registry items even when placements fail. Report successfully inserted items rather than the expected corpus size. Administrators should also be told that automatic Storage overflow mutates the world outside player placement hooks; its enable flag is currently the only direct control over that behavior.

### Medium — batchable operations cause avoidable server and network work

Locations: `block/StorageStackBE.java:120-151,255-324`, `block/SinglesStackBE.java:31-45,176-195`, `block/BarStackBE.java:30-45,150-170`, and `command/TestWallGenerator.java:69-164`.

- Storage overflow recursively calls `deposit` up the pile, and every successful frame calls `resortAndPackPile` on unwind. Each attempt can walk to the base before discovering the cooldown, making insertion into a tall full pile quadratic. Let the outermost operation own one post-deposit repack.
- Singles and Bar cascades recalculate light across 64 slots and send full block-entity updates for individual cell movements. Batch the mutation and publish once.
- Bar's repeated `do/while` collapse scan is unnecessary when indices are processed bottom-up and support depends only on the layer already updated below.
- `ss test all` lays floors and blocks with broad update flags and invokes normal deposit/sync behavior for nearly 30,000 corpus items in one server tick. Give generation a direct batched population path, schedule a bounded number of placements per tick, and report progress/completion.

The no-ticker architecture and configured pile window/cooldown are good foundations, but these event-driven bursts defeat part of their server benefit.

### Medium — rotating Storage needlessly triggers pile repacking

Location: `network/RotateBlockPkt.java:47-52`.

After changing only visual rotation, the packet calls `resortAndPackPile`. Rotation does not change inventory order or violate a pile invariant, but this call can consume the base cooldown and rewrite or synchronize several blocks. Remove it; `setRotation` already marks and synchronizes the block entity.

### Medium — the 2D renderer ignores Forge render passes

Locations: `client/measure/ModelMeasurer.java:85` and `client/CubeRenderHelper.java:141`.

Measurement iterates `model.getRenderPasses`, but the actual 2D path asks only the root model for one unculled quad list. Forge multi-pass items can measure correctly and then render with missing layers. Iterate the resolved model's render passes and project quads from each pass. Where supported items depend on other render types, honor the model's Forge render types rather than forcing every pass into one cutout buffer.

### Medium — odd Bar layers use side and end texture regions backwards

Location: `client/BarStackBER.java:75-119,126-133`.

The odd-layer branch identifies Z faces as short ends and X faces as long sides, but common emission always applies `long*` to Z and `end*` to X. Odd layers therefore stretch long-side art over ends and end art over sides. Select UV regions per face based on rotation or swap the odd-branch assignments.

ARGB support also conflicts with `RenderType.solid()`: authored alpha is not blended on the solid path. Either render translucent bars appropriately or document/reject non-opaque alpha.

### Medium — exact-tag stacks are not reliably consolidated

Locations: `util/StackSort.java:12-32` and `block/StorageStackBE.java:341-353,419-452`.

The comparator distinguishes only tag presence, not tag contents, then orders by count. Different tagged variants can interleave matching variants, while `consolidate` compares only with the immediately preceding output. Compatible item/tag stacks can remain separated, wasting slots and making outcomes count-dependent.

Group by exact item/tag identity before count, use an exact-key consolidation map, or use a straightforward nested search over the small configured repack window. Do not invent an elaborate general NBT ordering merely for this. Also remove one of the two consecutive sorts: the input is sorted before `consolidate`, and `consolidate` sorts it again.

### Medium — compatibility metadata advertises more than the actual baseline

Locations: `gradle.properties:14,18-20` and the expanded dependencies in `mods.toml:47-67`.

The project targets Minecraft 1.20.1 and Forge 47.4.0 with no backward-compatibility goal, but metadata declares Minecraft `[1.20.1,1.21)`, Forge `[47,)`, and loader `[47,)`. That advertises older Forge 47 releases and later Minecraft 1.20 releases that the reports say were not reviewed. Pin Minecraft to the 1.20.1 interval and use 47.4.0 as the Forge minimum, normally with an upper major bound.

The build retains Forge template/publishing boilerplate and dynamic plugin versions such as `1.+` and `[6.0,6.2)`. Removing unused Eclipse, publishing, data-generation, and GameTest scaffolding and pinning plugin versions would make this unreleased mod simpler and more reproducible.

### Low — model measurement and override validation have edge-case gaps

Locations: `client/measure/ModelMeasurer.java:57-85` and `OverrideJsonCodec`.

`ModelMeasurer` captures `isGui3d` before `ForgeHooksClient.handleCameraTransforms` can substitute a model, so bounds may come from the substituted model while flat/volumetric classification comes from the original. Read `isGui3d` after assigning the returned model. The hard-coded `true` passed to `getRenderPasses` should also match the actual render path's graphics/fabulous choice.

`OverrideJsonCodec` should reject non-finite scale and offset components. A `NaN` can evade `scale <= 0`, and non-finite admin data can poison pose matrices.

### Low — administrator configuration is ambiguous and reparsed on hot paths

Locations: `client/ClientEvents.java:81-88`, `ServerConfig.java:55-69`, `util/ItemOps.java:99-103,139-159`, and `command/SsCommand.java:157-179`.

- The client mode cycler selects Storage even when the synchronized Storage enable flag is false, while skipping disabled Singles and Bar. Treat all three modes consistently.
- Config validators accept any string. Invalid item IDs are reparsed and silently swallowed on every deposit, so administrators receive no indication that a blacklist entry is ineffective.
- Disabled namespaces are repeatedly scanned case-insensitively, while test-command filtering uses case-sensitive `contains`.

Validate and normalize ids and namespaces on config load/reload, log or reject bad entries once, retain parsed sets for runtime checks, and use one case policy for enforcement and commands.

### Low — Bar texture reload logging is excessive

Location: `client/BarTextureStore.java:117-270`.

Reload logs every mapping and many intermediate auto-tint details at INFO. With the bundled corpus this can produce thousands of routine lines and unnecessary formatting work. Keep one summary at INFO, move diagnostics to DEBUG, and reserve WARN for concise failures. This is client-side rather than a dedicated-server cost, but it still adds reload latency and log churn.

## Simplicity and over-guarding

The code is under-guarded at the remote boundary and over-guarded after it reaches trusted internal code. Validate packet/config/resource/mod-compatibility input once, then make block-entity operations direct and authoritative.

Recommended simplifications:

- Stop retrieving an optional Forge capability from a known Some Stacks block entity in internal renderers, ray tracers, commands, and packet paths. Expose narrow domain or read-only slot operations; keep the capability wrapper for external automation.
- Remove packet-side occupancy/grounding/hand checks that are immediately repeated by `depositAt` after the packet boundary has validated sender, position, reach, permission, type, and index.
- Ordinary deposit and placement duplicate most Singles/Bar deposit flow. One small server-side helper would remove real duplication without creating a framework.
- When code successfully places one of this mod's own registered blocks, treat the expected block entity as an invariant. `TestWallGenerator.fillStack` and Storage overflow currently log/return if their own block entity is absent, potentially leaving a partially completed mutation. Fail loudly instead.
- Remove the unused `sim` stack and zero-count simulated extraction in `StorageStackBE.mergeIntoHandler`, and the unused `deposited` local in `PileItemHandler.insertItem`.
- Remove `MinecraftForge.EVENT_BUS.register(this)`; the class has no annotated instance handlers.
- Remove empty `ClientEvents.init` and its call.
- Remove the unregistered `PlaceAdjacentToStackRule`, which encodes the forbidden Shift+V gesture.
- Remove unused `InteractionRule.getName`, `InteractionContext.isSinglesOrBarStackAbove/getAbovePos`, `BarCubeIdx.indexFromLocal` and its axis helpers, and `BlockType` sound getters/`toStackMode`.
- Remove the unused block-item model files; Storage's item model also points to nonexistent `somestacks:block/stack_block`.
- The Bar torch rules intentionally consume no-op gestures. Unless that behavior has product value, match only stack types with a corresponding server operation.
- `RightClickBlockSuppressor` stores a same-tick transient marker in persistent player NBT. Use short-lived server state instead of saving transient bookkeeping.

The ordered interaction-rule registry is not abstraction for its own sake: first-match order is product behavior. The spatial-index utilities are likewise justified because rendering, ray selection, support, and collision share exact coordinate contracts. No broader framework is needed; the useful changes are a small packet-boundary helper, direct block-entity methods, coherent batch finalization, and removal of dead parallel paths.

## Admin-tool assessment

The mod exposes the right categories of control: stack enable flags, disabled namespaces and item ids, pile cooldown/window settings, synchronized server render overrides, and test-wall tooling. It does not yet provide the operational behavior an administrator would expect:

1. An operator/console command to reload server render overrides and rebroadcast configuration, with counts and parse errors.
2. Operator permission and preferably a dry-run/size report for destructive test commands.
3. Reach, spawn/build, placement, claim/protection, and logging integration for player-triggered custom mutations.
4. One-time blacklist validation with visible diagnostics and consistent normalization.
5. Batched or scheduled test generation and cascade/repack publication to avoid server-thread stalls and packet bursts.

These are targeted operational fixes, not a reason to add an administrative framework.

## Suggested fix order

1. Fix Storage rejection, actual moved-count accounting, capability simulation, and the Storage extraction index crash.
2. Add explicit packet directions and reach/permission/gesture/Forge-event validation at the C2S boundary.
3. Add `.dynamicShape()`, prevent limited-window pile splitting, and correct Storage batch finalization.
4. Batch Singles rotation/cascade state and Bar collapse publication.
5. Correct dedicated-server sound ownership and config reload registration/tooling.
6. Remove face-dependent support, unnecessary rotation repacking, and hot-path config parsing.
7. Address test generation, rendering, metadata, logging, and dead-code cleanup.
