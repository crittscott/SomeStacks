# Code review

Scope: the implementation under `src/main`, reviewed against `living-spec.md` and the project principles in `CLAUDE.md`. Findings are ordered by severity. I did not build or run the mod, per project instructions.

## Findings

### High — C2S interaction packets do not enforce the server's interaction boundary

The custom packets are server-authoritative in the sense that mutation happens on the server, but they still trust too much client-supplied intent.

- `ExtractPkt.handle` calls `level.getBlockEntity(msg.pos)` without first checking that the position is loaded (`network/ExtractPkt.java:45-49`). A malicious client can submit arbitrary positions and make `getBlockEntity` load/generate chunks. Forge's SimpleImpl guidance specifically warns about this class of packet vulnerability.
- None of the deposit, extraction, rotation, or permanent-toggle handlers checks interaction distance. A modified client can operate on any loaded stack in the player's current dimension. Storage deposit is especially direct: after `isLoaded`, it deposits without even using the player's ray (`network/DepositPkt.java:52-81`). Extraction trusts the client-selected slot rather than verifying that it is the nearest occupied cell hit by the server-side ray (`network/ExtractPkt.java:51-59`).
- `PlaceAndDepositPkt` checks `mayUseItemAt`, but direct `level.setBlock` does not run the normal Forge right-click/place hooks or the complete vanilla placement checks (`network/PlaceAndDepositPkt.java:67-75,166-169`). Deposit, extract, rotate, and toggle do not perform an equivalent permission check at all. Claim/protection/logging mods and server administration tools that rely on Forge interaction or placement events can therefore be bypassed.
- The generic `index < 64` check is wrong for Storage's 27-slot handler. Indices 27 through 63 pass validation and then reach `getStackInSlot`, which throws (`network/ExtractPkt.java:51,53-66`).
- Packet directions are not declared at registration (`network/ModNetworking.java:23-40`), so direction is implicit rather than enforced by the channel.

This is not guarding against Forge failure; the network peer is an untrusted interface. Use one small common validation path for C2S block operations: correct direction, loaded position, correct per-type index range, normal reach, and server permission/protection integration. Placement also needs the normal obstruction/world-border/place-event contract. Recompute any gameplay-relevant selected cell on the server. [Forge's networking guidance](https://docs.minecraftforge.net/en/1.21.x/networking/simpleimpl/) explicitly says to treat server-bound packet data defensively.

### High — capability insertion of a disabled-mod item can build a column of empty Storage Stacks

`StorageStackBE.deposit` never performs its own `isValidStorageItem` check (`block/StorageStackBE.java:120-147`). The local `ItemStackHandler` rejects a disabled-mod item, but `deposit` interprets the unchanged remainder as overflow, creates a new Storage Stack above, and recursively tries again. This repeats through replaceable space until placement fails, leaving every newly created block empty.

The exposed wrapper makes this path likely: `PileItemHandler.isItemValid` unconditionally returns `true` (`block/PileItemHandler.java:72-75`). Player packets pre-check disabled mods, but capability callers do not, and the living spec says the disabled-mod rule applies to Storage contents generally.

Reject an invalid stack once, at the start of `StorageStackBE.deposit`, and have `PileItemHandler.isItemValid` delegate to the same predicate. Do not create an overflow block until the item has passed that invariant.

The same wrapper also violates the `IItemHandler` simulation contract. Simulated insertion examines only the requested local slot (`block/PileItemHandler.java:29-45`), while real insertion ignores that slot and searches the whole pile, including upward overflow (`:47-49`). Automation can be told "nothing fits" when another slot would accept the item, or be told that one local slot is the whole available capacity when real insertion would create/use blocks above. Simulation and execution need to model the same operation.

### High — pile cleanup can remove a block from underneath unprocessed Storage Stacks

`resortAndPackPile` collects at most the configured window around the initiating block, then removes empty blocks from the top of that list without checking whether another Storage Stack exists immediately above the list (`block/StorageStackBE.java:323-369`). With a pile taller than `max_stacks_per_resort`, repacking the lower window can empty its highest processed block and delete it while occupied, unprocessed stack blocks remain above. The pile is severed into two independent piles and the upper section is left floating.

There is a second version of the same invariant violation in player extraction: after a throttled/no-op repack, `ExtractPkt` removes the clicked Storage block merely because that local BE is empty (`network/ExtractPkt.java:81-92`). It does not require the block to be the actual pile top. Capability extraction can instead leave an empty temporary block indefinitely when the cooldown prevents the only repack (`block/StorageStackBE.java:182-193`).

Only delete an empty Storage block when there is no Storage Stack directly above it. If packing is windowed, the top of the window is not necessarily the top of the pile. Empty-block cleanup should preserve contiguity independently of cooldown.

### High — batch repacking leaves light and comparator state stale

Ordinary slot changes update neighbors and recompute the `LIGHT_LEVEL` block state in `onContentsChanged` (`block/StorageStackBE.java:31-45`). Repacking deliberately sets `suppressSync`, clears and rewrites all affected inventories, then only calls `setChanged` and `syncToClients` (`:326-362`). It never performs the suppressed light recalculation or comparator-neighbor update after the batch.

For example, moving a luminous BlockItem from an upper Storage Stack into a lower one can leave the upper block emitting its old light and the lower block emitting its old light. Comparator output is calculated from the new inventory when queried, but neighboring comparators are not reliably told that the local fill changed. Use one explicit end-of-batch reconciliation per affected BE: mark dirty, recompute light, notify comparator output with Minecraft's comparator-specific neighbor update, and send one BE update packet.

### High — server-config reload synchronization is registered on the wrong event bus

`SomeStacks` adds `onConfigReload(ModConfigEvent.Reloading)` to `MinecraftForge.EVENT_BUS` (`SomeStacks.java:39`). Forge config events must be registered on the mod event bus, which is already available as `modBus` at line 30. Consequently, changing the enable flags or server render-override files will not execute the documented broadcast path; reconnect/restart behavior can hide the defect.

Register that listener with `modBus.addListener`. [Forge's 1.20.1 configuration documentation](https://docs.minecraftforge.net/en/1.20.1/misc/config/) states this requirement directly. `MinecraftForge.EVENT_BUS.register(this)` at line 36 is also redundant because the class has no annotated instance handlers.

### Medium — cross-block grounding is incorrectly conditioned on the clicked face

The first item in a newly placed Singles or Bar block is checked against the block below only when `msg.face == Direction.UP` (`network/PlaceAndDepositPkt.java:91-164`). Grounding is a property of the target position, not of which neighboring face produced that target. A player can place into a position above an existing same-type stack by clicking the side of another adjacent block; that legitimate path skips the lower-block support check.

Whenever `msg.pos.below()` contains the same stack type, apply the cross-block support rule. The duplicated Bar footprint-overlap calculation at lines 127-156 should use the same geometry operation as normal Bar grounding so the two contracts cannot drift.

### Medium — Singles per-item rotations become stale after extraction

During a column shift, each inventory extraction/insertion fires `onContentsChanged` and sends an update before `cubeRotations` is changed (`block/SinglesStackBE.java:31-46,176-194`). The final rotation assignments are not followed by `setChanged`/`syncToClients`, so the client commonly keeps the old rotation metadata after gravity. Persistence happens to become dirty because of the preceding inventory writes, but network state is wrong.

An extracted top item also leaves its old rotation in the now-empty slot, and `depositAt` does not reset it (`:133-159,162-194`). A later, unrelated item placed in that cell inherits the previous item's rotation, contrary to rotations belonging to individual items. Update inventory and rotation metadata as one batch, clear rotation when a slot becomes empty, and synchronize once after the final state is coherent. Raw capability operations need to keep the metadata aligned too, even though they intentionally bypass player grounding/cascade rules.

### Medium — resource-configured sounds only appear to work in an integrated server

`SoundConfig` is registered only from client setup (`client/ClientSetup.java:36-40`) and mutates static `ModSounds.*` fields. The logical server chooses the sound in the C2S packet handlers (`network/DepositPkt.java:80-130`, `network/ExtractPkt.java:83-150`). On a dedicated server, client resource reloads cannot change those server fields, so the server always sends the Java defaults. In single player, shared static state between logical sides can make this look functional; Forge specifically identifies cross-side static state as an error ([sides documentation](https://docs.minecraftforge.net/en/1.20.x/concepts/sides/)).

There is also an unused `DeferredRegister` containing six custom sound events (`ModSounds.java:13-34`): it is never attached to the mod bus, none of its `RegistryObject`s is used, and the bundled OGG has no standard namespace-root `sounds.json` definition.

The simplest Minecraft-native design is to register and play stable Some Stacks sound events and define them with standard `assets/somestacks/sounds.json`, allowing resource packs to replace their audio. If administrators must select arbitrary event IDs, make that server-owned configuration and synchronize it deliberately. Remove the parallel unused sound system rather than keeping both.

### Medium — hot operations generate avoidable repeated server work and packets

The no-ticker architecture is good, but several event-driven operations are much more expensive than their bounded work requires.

- Overflow deposit recursively calls `deposit` on every block traversed, and every successful recursive frame calls `resortAndPackPile` on unwind (`block/StorageStackBE.java:120-151`). `resortAndPackPile` walks to the base before checking the cooldown (`:257-267,304-317`), making insertion into a tall, full pile quadratic in pile height even when all but the first repack are immediately throttled. Use an internal deposit routine and repack once at the outer operation.
- A Bar cascade can remove dozens of items. Every `items.extractItem` triggers a full BE update packet and a 64-slot light scan before the cascade completes (`block/BarStackBE.java:30-44,150-169`). Batch the cascade and send/recalculate once, as Storage already attempts to do for repacking.
- A config reload loops over players and calls `sendConfigSync`, which reparses the entire server override directory separately for every player (`SomeStacks.java:47-62`). Load once, build one immutable packet payload, and send it to the player list.
- `BarTextureStore` logs the full 402-entry map at INFO and emits multiple INFO lines for each of 331 auto-tint attempts (`client/BarTextureStore.java:117-194,221-267`). This is client-side rather than server-side, but it turns every resource reload into hundreds or thousands of routine log lines. Keep one summary at INFO and details at DEBUG.

### Medium — consolidation does not group equal tagged stacks

`StackSort.COMPARATOR` compares item id, damage, whether a tag exists, and count, but never tag contents (`util/StackSort.java:17-31`). `StorageStackBE.consolidate` only attempts to merge the current stack with the immediately previous output stack (`block/StorageStackBE.java:419-449`). Different tagged variants of the same item can interleave after sorting, leaving compatible stacks separated and unconsolidated.

Because a repack handles a small configured window (81 source slots by default), a straightforward nested search for a compatible partial output stack is simpler and safer than inventing a general NBT ordering. This is a good place for the hammer, not a more elaborate comparator abstraction.

### Medium — the destructive test-wall command is available to every creative player

The root command requires only a `ServerPlayer` in creative mode (`command/SsCommand.java:49-53`). `ss test all` can synchronously replace a large floor and thousands of block positions (`command/TestWallGenerator.java:69-96,147-164`). On a multiplayer server, any creative player can use it as a world-overwrite/lag tool, while an operator in survival cannot use even the administrative parts.

Split permissions by subcommand. Per-player render authoring can keep its intended creative requirement; world-mutating `ss test` should require the normal operator permission level (and may reasonably remain player-only because its origin is player-relative). Its success message also counts all registry items even when placements fail (`TestWallGenerator.java:88-93`), so report successfully inserted items rather than expected items.

### Low — capability lifecycle should use Forge's designated hook

All three block entities invalidate their `LazyOptional` from `setRemoved` (`block/StorageStackBE.java:232-236`, `block/SinglesStackBE.java:241-245`, `block/BarStackBE.java:205-209`). Forge's capability lifecycle contract is `invalidateCaps`, including `super.invalidateCaps()`. Use that hook instead of coupling invalidation to one removal method. [Forge capability documentation](https://docs.minecraftforge.net/en/1.21.x/datastorage/capabilities/) shows this pattern.

### Low — admin configuration has avoidable ambiguity

- The client mode cycler deliberately selects Storage even when the synchronized Storage enable flag is false (`client/ClientEvents.java:81-88`), while it skips disabled Singles and Bar. The admin's disable setting therefore leaves users in a mode whose placement packets are silently refused. Treat all three flags consistently.
- Config validators accept every string (`ServerConfig.java:55-69`). Invalid item IDs are reparsed and silently swallowed on every deposit (`util/ItemOps.java:139-159`), giving administrators no indication that a blacklist entry is ineffective. Validate resource locations/mod namespaces when the config loads, normalize once, and log or reject bad entries once.
- Command filtering uses case-sensitive `contains` (`command/SsCommand.java:157-179`) while actual disabled-mod enforcement is case-insensitive (`util/ItemOps.java:99-103`). One normalized set should serve both.

## Simplicity and over-guarding

The ordered interaction-rule list is a reasonable amount of structure because first-match ordering is a real product rule and an explicit extension point. It does not need another framework. It does need dead paths removed:

- `PlaceAdjacentToStackRule` is unregistered and encodes the now-forbidden Shift+V placement gesture.
- `ClientEvents.init` is empty.
- `InteractionContext.isSinglesOrBarStackAbove`, `BarCubeIdx.indexFromLocal` and its axis helpers, `BlockType.getDepositSound`, `getExtractSound`, and `toStackMode` have no callers.
- The unused custom sound registry/assets described above should either become the one real sound system or be deleted.
- `StorageStackBE.mergeIntoHandler` creates an unused `sim` stack and calls `extractItem(i, 0, true)` as a no-op "ensure valid slot" (`block/StorageStackBE.java:388-391`). Both lines should go.

There is also genuine over-guarding around invariants the mod itself creates. For example, `TestWallGenerator.fillStack` successfully places its own Storage block and then logs/returns if its own BE is missing, leaving the replacement block behind (`command/TestWallGenerator.java:122-134`). Storage overflow similarly places its own registered block and silently skips deposit if its BE is not the expected type (`block/StorageStackBE.java:137-145`). Those are not unreliable external interfaces; use the known type and fail loudly if the invariant is broken. The same principle applies to repeatedly retrieving these BEs' own guaranteed item capabilities in internal code and renderers.

By contrast, range/type checks on C2S data, JSON/filesystem error handling, and fallbacks around third-party item/block renderers are appropriate. Those are actually unreliable boundaries; removing them would not be simplification.

## Overall assessment

The broad architecture is sound: server-owned inventories, no continuously ticking BEs, Forge item capabilities, deferred block/BE registration, tags, update NBT, and client-only rendering are all appropriate choices. The living spec is unusually effective at making the intended contracts reviewable.

The implementation is not ready to call server-friendly yet. The main blockers are packet authorization/protection integration, the Storage capability overflow bug, pile-contiguity cleanup, stale derived world state after batch mutation, and the nonfunctional config-reload path. The sound path and capability lifecycle also need to be brought back to the normal Forge model. These fixes do not require a new abstraction layer: a few explicit invariant checks, one batch-finalization method, and removal of dead parallel systems are sufficient.

## Read-only verification performed

- Read all Java source and the relevant resources/configuration; did not read any other code-review file.
- Parsed all 192 JSON resources successfully.
- Checked all 179 bundled item-render-override files (29,799 entries) for the documented mode/scale/offset shape; no schema violations or duplicate item IDs were found.
- Did not invoke Gradle, compile, run tests, or touch ForgeGradle caches, as required by `CLAUDE.md`.
