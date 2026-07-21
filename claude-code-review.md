# Some Stacks — independent code review

Reviewer pass against `main` @ f8c5f71 (2026-07-20). Every claim below was checked line-by-line against the code as it stands; the git history was not consulted. This review was written to stand on its own, but where it confirms, corrects, or extends `code-review-merged.md`, it says so.

## Verdict

The architecture matches `living-spec.md` and is sound: server-authoritative mutation, non-ticking block entities, `DeferredRegister`, explicit interaction-rule ordering, shared spatial-index contracts. The merged review's findings are almost all real — I confirmed them against the code. The blockers are the Storage capability path (item deletion and world-mutation-to-build-limit), a C2S packet surface that trusts the client too much (including one remotely triggerable server crash), and derived state (light, comparator, rotations, shapes) that goes stale after batch operations. None of these are hard fixes; they are boundary and finalization discipline.

The trust model to adopt — validate hard at the remote boundary, then treat this mod's own block entities and capabilities as invariants — is correct, and the codebase currently has it backwards: under-guarded at the packet edge, over-guarded in internal code that already holds a concrete block entity.

---

## Critical

### 1. Storage deposit never rejects invalid items — builds empty towers and deletes items

`block/StorageStackBE.java`, `block/PileItemHandler.java`. The worst bug in the codebase; three interlocking defects.

**Tower creation.** `StorageStackBE.deposit` (line 120) does not check `isValidStorageItem` at entry. The inner `ItemStackHandler.isItemValid` rejects disabled-mod items, so `mergeIntoHandler` moves nothing — and `deposit` then interprets the untouched remainder as *overflow*: it creates a new Storage Stack above (lines 137-145) and recurses. Every recursion fails identically and creates another block, up to build height. `PileItemHandler.isItemValid` (line 73) returns `true` unconditionally, so any hopper or pipe holding a disabled-mod item triggers this on its first insertion attempt.

**Item deletion.** In `mergeIntoHandler` (lines 383-394), the partial-fill loop ignores the remainder returned by `handler.insertItem`, then unconditionally runs `from.shrink(can); moved += can`. If a partial stack of a now-disabled item already exists (admin disabled the mod after players stored some), the insert is rejected but the source is shrunk anyway — the items cease to exist. This directly violates the documented rule that existing disabled contents remain extractable.

**Simulation lies.** `PileItemHandler.insertItem` (lines 29-50) simulates a plain per-slot insert (without even a validity check) but executes a pile-wide `deposit` that ignores the requested slot entirely. Automation that simulates then executes gets different answers from each call.

**Fix.** Reject at the entrance: `if (!isValidStorageItem(fromHand)) return 0;` in `deposit`. Make `PileItemHandler.isItemValid` delegate to the same predicate. In `mergeIntoHandler`, compute `moved` from actual insertion remainders, never from predictions. Make simulate and execute model the same operation (simulate the pile-wide deposit, or expose plain slot semantics through the capability and keep pile-wide behavior on the player path — either is defensible; pick one). While there, delete the dead `sim` stack and the no-op `extractItem(i, 0, true)` at lines 388-390, and the unused `deposited` local in `PileItemHandler.insertItem`.

### 2. `ExtractPkt` crashes the server with a Storage index in 27–63

`network/ExtractPkt.java`. Critical rather than high: a remote, trivially triggered server crash. `handle` (line 51) validates `0 <= index < 64` for all three block types, then `handleStorageExtract` (lines 65-66) calls `getStackInSlot(index)` through `PileItemHandler` → `getSlotDirect` → the 27-slot `ItemStackHandler`, whose `validateSlotIndex` throws `RuntimeException` for 27–63. The exception is thrown inside `enqueueWork` on the server thread — a modified client kills the server with one packet.

**Fix.** Drop the packet-side pre-read entirely and dispatch to `StorageStackBE.extractAt`, which already bounds-checks against the real slot count and validates contents and hand compatibility. The handler's job is sender/position/reach validation, not repeating the block entity's checks.

### 3. The C2S boundary trusts client intent

All six mutation packets; `network/ModNetworking.java`; `server/RightClickBlockSuppressor.java`.

- No packet registers a direction (`ModNetworking` lines 23-40). Use the `registerMessage` overload taking `Optional.of(NetworkDirection.PLAY_TO_SERVER/CLIENT)` so a client cannot feed S2C packets to the server at all.
- `ExtractPkt` (line 48) calls `level.getBlockEntity(msg.pos)` with **no `isLoaded` check** — on `ServerLevel` that loads (or generates) the chunk, so a client can force arbitrary chunk loading. The other packets do check `isLoaded`; this is an omission.
- **No packet checks reach.** Deposit, extract, rotate, and toggle-permanent all operate on any loaded position the client names. `DepositPkt` will deposit into a Storage Stack a thousand blocks away.
- Gesture prerequisites are client-only: the rotation packets do not require sneaking or a torch in hand, and `TogglePermanentPkt` does not require an empty hand, so a modified client can invoke any operation without its gesture.
- `PlaceAndDepositPkt` (line 168) places via raw `setBlock` after only `canBeReplaced` + `mayUseItemAt`. It never fires `BlockEvent.EntityPlaceEvent` (the `ForgeEventFactory.onBlockPlace` / `BlockSnapshot` path), so claim/protection/logging mods can neither veto nor record the placement. Extraction similarly mutates before any hook fires; `RightClickBlockSuppressor` (line 31) then cancels the follow-up vanilla event at `HIGHEST` priority — which is exactly the event protection mods listen to.

**Fix.** One small shared boundary helper: sender non-null → position loaded → within reach (`player.getEyePosition().distanceToSqr(center) <= reach²`) → packet-specific gesture prerequisites → protection hook (`onBlockPlace` for placement; for deposit/extract, fire `PlayerInteractEvent.RightClickBlock` yourself or at minimum consult `mayInteract`). Then call the block entity's authoritative operation directly. This is the untrusted-remote-interface case the project principles explicitly carve out; it is not over-guarding.

---

## High

### 4. Singles and Bar omit `dynamicShape()`

`ModRegistry.java` lines 35-49; `block/SinglesStackBlock.java`; `block/BarStackBlock.java`. Both blocks are built without `.dynamicShape()`, yet `getShape`/`getCollisionShape` read the block entity's `getCachedShape()`. Without the flag, vanilla caches shapes per block *state* (`BlockStateBase.initCache`), and since contents do not change the state (only `LIGHT_LEVEL` does), collision can be served from a cache computed with different contents. Add `.dynamicShape()` to both; keep the per-BE `cachedShape` unions, which remain the right way to avoid recomputing the 64-cell union.

### 5. Pile cleanup deletes blocks that are not the pile top

`block/StorageStackBE.java` lines 365-373; `network/ExtractPkt.java` lines 89-92. Two forms:

- `resortAndPackPile` removes empty non-permanent blocks from the top of the *processed window* (max 3 blocks by default). In a taller pile, consolidation empties the window's top block and deletes it while occupied blocks sit above — the pile is severed, and the upper fragment gets its own base and cooldown.
- `handleStorageExtract` removes the clicked block whenever its own 27 slots are empty, without checking for a Storage Stack directly above. Extract the last local item from a mid-pile block (repack throttled by cooldown) and the pile splits.

**Fix.** One rule at both sites: never remove an empty Storage block if `level.getBlockState(pos.above())` is a Storage Stack. It is cheap and independent of cooldown and window size. State it in `living-spec.md`, since the current spec text ("removes empty temporary blocks from the top of the processed window") describes the bug as if it were behavior.

### 6. Repacking leaves light and comparator state stale

`block/StorageStackBE.java` lines 29-45, 326-373. During repack, `suppressSync` (line 35) bypasses not just client sync but also the `LIGHT_LEVEL` recalculation and `updateNeighborsAt` that live in `onContentsChanged`. The `finally` block only does `setChanged` + `syncToClients`. Sorting can move glowstone from block A to block B: both keep their old light states and neighboring comparators never re-read.

**Fix.** Finish each repacked block with one authoritative finalize: recompute `LIGHT_LEVEL` and set it if changed, `updateNeighborsAt`, `setChanged`, one `sendBlockUpdated`. Reuse this pattern for the Singles/Bar batch fixes below.

### 7. Singles cascade syncs mid-mutation and mismanages rotations

`block/SinglesStackBE.java` lines 162-195. In `cascadeUnsupportedBlocks`, every `extractItem`/`insertItem` pair fires `onContentsChanged` → full client sync and light rewrite, *then* the rotation arrays are moved with no `setChanged`/sync afterward. Consequences: clients receive N intermediate updates whose rotations are stale (the final rotation state is only persisted/synced if something else later dirties the BE); and `extractAt` (lines 162-174) never clears `cubeRotations[index]` when nothing cascades into it, so the next item deposited there silently inherits the previous item's orientation. Raw capability extraction bypasses the cascade entirely and leaves rotations attached to emptied slots the same way.

**Fix.** Suppress per-slot sync during the cascade (same pattern as Storage's `suppressSync`, plus the finalize from finding 6), move items and rotations together, zero the rotation of every slot that ends empty, then mark dirty and sync once.

### 8. Sounds: the logical server consumes client-owned resource data

`client/SoundConfig.java`; `client/ClientSetup.java` line 39; `ModSounds.java`; the server-side `playSound` calls in the mutation packets. `SoundConfig` is registered only as a **client** reload listener but writes static `ModSounds` fields that server-side packet handlers read (`level.playSound(null, …)`). Singleplayer works only because the integrated server shares the statics; a dedicated server always plays the Java-default sounds, and no client resource pack can ever change what a server-selected `SoundEvent` id plays.

**Fix.** Pick an owner. Cleanest match to the spec's "client resource reload" language: have the server send a tiny action packet (or let the S2C block-entity update trigger it) and let each client resolve and play its own configured sound. Alternatively make it server config and drop the resource files. Also: `ModSounds.SOUND_EVENTS` is a `DeferredRegister` that is **never registered to the mod bus** — six `RegistryObject`s that would throw if dereferenced — and `sounds/bar_extract.ogg` has no `sounds.json` referencing it. Delete the entire dead half.

### 9. Config reload sync never fires — listener is on the wrong bus

`SomeStacks.java` lines 29-40, 47-62. `ModConfigEvent.Reloading` implements `IModBusEvent`; line 39 adds `onConfigReload` to `MinecraftForge.EVENT_BUS`, where it is never posted. The advertised "re-sync flags and overrides on server config reload" behavior does not exist at runtime. Move it to `modBus`. Also confirmed: `sendConfigSync` re-reads and re-parses the whole `server_item_overrides` directory once **per player** (lines 56-63); load once, build one payload, send to all. And since editing that folder fires no config event at all, an operator `ss reload`-style command (op-level, console-capable, reporting parse failures) is the missing admin workflow. Finally, `MinecraftForge.EVENT_BUS.register(this)` at line 36 registers an object with zero `@SubscribeEvent` methods — delete it.

---

## Medium

### 10. Cross-block support depends on the client-supplied face — plus a rotation bug the merged review missed

`network/PlaceAndDepositPkt.java` lines 91-164. The lower-block support check runs only when `msg.face == Direction.UP`. The face is client data; a forged or side-face packet reaches the same position and skips the invariant. Check "is the block below the same stack type" from world state, always.

Additionally (my finding): the Singles pre-check is internally inconsistent. Line 98 traces with `ssbeBelow.getRotation()`, but the actual deposit after placement traces with rotation `0` (line 189, correct for a fresh block). When the lower block is rotated, the support check tests a different cell than the one that will be filled, and the `lowerIndex = 3*16 + z*4 + x` column mapping does not account for the mismatch either. The Bar branch re-implements footprint overlap inline (lines 132-156) instead of using `BarCubeIdx`'s own footprint/overlap helpers — expose those and use one geometry implementation so the contracts cannot drift.

### 11. `ss` permissions and the disabled-mod case mismatch combine badly

`command/SsCommand.java`; `util/ItemOps.java`. `SsCommand` (line 52) gates everything on creative: any creative builder can floor over a region with `ss test all`, and no operator or console can run anything. Runtime enforcement compares namespaces with `equalsIgnoreCase` (`ItemOps` line 101) while the test command filters with case-sensitive `contains` (`SsCommand` line 157). If an admin writes `"SpartanFire"` in the config, `ss test spartanfire` is not blocked, every deposit is rejected by `isItemValid` — and via finding 1, **each rejected item builds an empty Storage tower to build height**. Split permissions (render authoring creative, test generation op-level ≥ 2), and normalize the config to lowercase once at load.

One stale claim in the merged review: it says the success message "counts registry items even when placements fail." Partially outdated — `SsCommand.generate` (lines 204-207) now counts actual placed stacks and warns on failures. But `totalItems` is still the corpus size, not deposited count (deposits that fail inside `fillStack` at lines 136-139 are invisible), so the residue of the claim stands.

### 12. Event-driven bursts undercut the no-ticker design

- Storage overflow recursion: each frame of `deposit` (lines 149-151) calls `resortAndPackPile` on unwind; each call walks to the pile base before the cooldown check. Tall pile → O(depth²) walks per insertion. Let only the outermost call repack.
- Singles/Bar cascades: full 64-slot light recalculation and a full BE update packet per moved cell (fixed by finding 7's batching).
- `BarStackBE.removeUnsupportedBlocks` (lines 150-170): the `do/while` full rescan is unnecessary — slot index order *is* layer order (`idx/8`), and support depends only on the layer below, so one bottom-up pass suffices. Note also: if it ever ran where `level == null`/client, the item is extracted but never dropped — extraction and dropping should sit inside the same guard.
- `ss test all`: ~30k deposits, each with full sync flags, in one server tick (`TestWallGenerator.generate` lines 69-97). Fill the handler directly (it is a review tool; it does not need deposit semantics), or schedule N placements per tick.

### 13. Rotating a Storage block triggers a repack

`network/RotateBlockPkt.java` line 52. Rotation is visual-only for Storage; the call burns the pile cooldown and can rewrite several blocks. Delete it — `setRotation` already syncs.

### 14. The 2D renderer ignores Forge render passes

`client/measure/ModelMeasurer.java` line 85; `client/CubeRenderHelper.java` line 141. Measurement iterates `getRenderPasses`, but the actual draw path (`render2DItemCube`) asks only the root model for unculled quads and pushes everything into one cutout buffer. Multi-pass items measure right and render with missing layers. Iterate the resolved model's passes in the render path too.

### 15. Odd Bar layers apply side/end texture regions backwards

`client/BarStackBER.java` lines 75-145. In `emitBar`, the rotated branch's comments say "Z faces are now ends / X faces are now long sides," but it assigns the *same values to the same variable names* as the even branch, and emission unconditionally puts `long*` on Z faces and `end*` on X faces (lines 127-133). Net effect: apart from the top-face rotation, the rotated branch is a no-op, and odd-layer bars stretch side art across their ends. Swap the assignments in the rotated branch (put the end region in `longU*` and vice versa), or select UVs per face by orientation. Separately, ARGB tints with alpha < 255 are emitted into `RenderType.solid()`, which does not blend — either route translucent tints to a translucent buffer or reject non-opaque alpha in `parseColor`.

### 16. Tag-exact consolidation is unreliable, and one sort is redundant

`util/StackSort.java`; `block/StorageStackBE.java`. `StackSort.COMPARATOR` (lines 27-31) orders by tag *presence* then count, so two different-NBT variants of the same item interleave by count, and `consolidate` (lines 432-444) merges only into the immediately preceding output stack — compatible stacks separated by an interloper stay split. Given the small window (≤ 3 blocks, 81 slots), the simple fix is an exact-key map (`item + damage + tag`) → merge, then sort once. Also: the input is sorted at line 341 and `consolidate` sorts it *again* at line 421 — drop one.

### 17. Capability lifecycle and internal capability round-trips

`block/StorageStackBE.java`, `block/SinglesStackBE.java`, `block/BarStackBE.java`. All three BEs invalidate their `LazyOptional` in `setRemoved` (e.g. Storage lines 232-236); Forge's contract is `invalidateCaps()`/`reviveCaps()` with `super` calls — chunk unload invalidates through `invalidateCaps`, not `setRemoved`, so the current code leaks holders' cached optionals across unloads. Separately, the mod's own code goes through `getCapability(...).orElse(null)` everywhere it already holds a concrete BE — renderers (`SinglesStackBER` line 26), ray tracers, `onRemove`, packet handlers. For Storage this even means the renderer and drop path read through the pile-aware wrapper. Give the BEs direct read accessors and keep the capability strictly for external automation.

### 18. Version metadata over-advertises

`gradle.properties` lines 14-20; `build.gradle` lines 5-6. Declares Minecraft `[1.20.1,1.21)`, Forge `[47,)`, loader `[47,)` for a mod developed against 1.20.1/47.4.0 only, and uses floating plugin versions (`[6.0,6.2)`, `1.+`) plus unused eclipse/maven-publish/data-gen/GameTest template scaffolding. Pin `[1.20.1]` and `[47.4.0,48)`, pin the plugins, delete the boilerplate you do not use.

---

## Low / cleanup

- **Measurement edge cases.** `client/measure/ModelMeasurer.java` lines 57-68 read `isGui3d()` *before* `handleCameraTransforms` can substitute the model, so flat/volumetric classification can describe a different model than the one measured; re-read after substitution. The hard-coded `true` fabulous flag at line 85 should match the real render path.
- **Non-finite override values.** `util/OverrideJsonCodec.java` lines 69-71 — `NaN <= 0` is false, so a `NaN` scale passes; offsets are unchecked. Apply `Float.isFinite` to all four numbers; this is synced admin data that ends up in pose matrices.
- **Blacklist hot-path parsing.** `util/ItemOps.java` lines 149-159 (`isItemDisabled`) constructs `ResourceLocation`s from config strings and swallows failures on *every deposit*. Parse the lists once on load into `Set`s, log bad entries once.
- **Mode cycler favoritism.** `client/ClientEvents.java` line 86 breaks unconditionally on `STORAGE_STACK` without consulting the synced flag it checks for Singles/Bar. Treat all three alike (the server still refuses placement, but the UX is inconsistent).
- **BarTextureStore logging.** `client/BarTextureStore.java` lines 138-144 log every mapping and ~10 INFO lines per auto-tinted item on every resource reload. One INFO summary; the rest DEBUG.
- **Suppressor persistence.** `server/RightClickBlockSuppressor.java` lines 26-28 store a same-tick marker in `player.getPersistentData()`, which is written into the player's save NBT. A `Map<UUID, (tick,pos)>` in the class (cleared on read/mismatch) is the right shape.
- **Dead code, all verified dead.** Unregistered `PlaceAdjacentToStackRule` (encodes the forbidden Shift+V gesture — delete, do not register); `InteractionRule.getName`; `InteractionContext.isSinglesOrBarStackAbove`/`getAbovePos`; `BarCubeIdx.indexFromLocal` + its four private axis helpers; `BlockType.getDepositSound`/`getExtractSound`/`toStackMode`; empty `ClientEvents.init()` and its call; the three `models/item/*.json` (no block items are registered, and `item/storage_stack_block.json` parents nonexistent `somestacks:block/stack_block`).
- **Bar torch no-ops.** Both torch rules match Bar Stacks (`RotateBlockWithRedstoneTorchRule` line 13 via `isAnyStackBlock`, `RotateItemWithSoulTorchRule` lines 26-27 explicitly), cancel the click, and the server has no corresponding Bar operation. Match only blocks with a server operation.

---

## Corrections to `code-review-merged.md`

1. **ExtractPkt index bug severity.** It is a one-packet remote server crash, not merely an out-of-range read — critical, not high.
2. **`ss test` success message.** Partially stale; stack counting and failure warnings now exist (finding 11). The `totalItems`-vs-deposited discrepancy remains.
3. **`TestWallGenerator.fillStack` "fail loudly".** The log-and-return the review criticizes is real, but the larger problem with `fillStack` is that it feeds `deposit` unvalidated (finding 11's tower chain); fix that first.
4. The review's trust-model framing (validate hard at the remote boundary, then trust internal invariants) is correct and worth adopting verbatim.

---

## Suggested fix order

1. Storage deposit boundary rejection, remainder-based accounting, capability sim/exec coherence (1); ExtractPkt crash (2).
2. Packet boundary helper: directions, isLoaded-before-BE-access, reach, gesture prereqs, placement/protection hooks (3).
3. `dynamicShape()` (4); never-remove-under-a-stack rule (5); repack finalization (6).
4. Singles cascade/rotation batching (7); Bar collapse single pass (12).
5. Sound ownership + delete the dead sound half (8); config reload bus + admin reload command (9).
6. Face-independent support with the rotation fix (10); `ss` permissions + case normalization (11); rotation-repack removal (13).
7. Rendering (14, 15), consolidation (16), lifecycle/metadata/logging/dead code (17, 18, lows).
