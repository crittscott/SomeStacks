# Code review 3

## Scope and verdict

This is a static review against `living-spec.md` and the project principles in `CLAUDE.md`. I read all Java sources, the build/metadata files, and the relevant resources. I did not compile, run, or test the mod, as required by `CLAUDE.md`. I also parsed all JSON resources successfully and checked all 29,799 bundled item-render entries for invalid ids, modes, scales, offsets, and duplicate item ids; that check found no schema errors or duplicates.

The broad architecture is recognizably Minecraft/Forge: the server owns mutation, block entities own persistent inventory, the client sends intent packets, blocks use block-entity renderers, capabilities expose automation, registration uses `DeferredRegister`, and there are no ticking block entities. The interaction-rule and spatial-index types earn their keep because ordering and shared geometry are real contracts.

The implementation is not ready as-is, however. There are several item-loss/world-mutation paths, packet handlers trust remote clients in the wrong places, two important block-entity state transitions do not publish their complete result, dynamic collision is not declared in the block properties, and resource-configured sounds cannot work on a dedicated server. The server/admin story also needs a permission and reload pass.

## Findings

### 1. Critical: a rejected Storage item can create an empty tower, and one rejection path deletes items

Locations: `StorageStackBE.java:120-147`, `StorageStackBE.java:382-407`, and `PileItemHandler.java:29-49,72-75`.

`StorageStackBE.deposit` never rejects an invalid item up front. It relies on the local `ItemStackHandler` to reject insertion, but then treats the still-nonempty input as overflow and recursively creates a Storage Stack above. With Storage creation enabled, a capability insertion of an item from a disabled mod can therefore create empty Storage blocks all the way to the build limit and return the original item unchanged. `PileItemHandler.isItemValid` actively advertises that every item is valid, making this easy for normal automation to attempt.

There is also an item-loss case in the partial-stack loop. It calculates `can`, calls `insertItem`, ignores that call's remainder, and shrinks `from` by `can` unconditionally. If an existing partial stack later becomes invalid because its namespace was added to `disable_mods`, the handler rejects the insertion while `mergeIntoHandler` deletes those items and reports them as moved.

Reject with `isValidStorageItem(fromHand)` at the entrance to `deposit`, make the capability's `isItemValid` agree, and calculate moved count from the actual insertion remainder in both loops. The simulated capability result must also model the same pile-wide operation as the real insertion; its current requested-slot-only simulation violates `IItemHandler`'s contract.

### 2. Critical: the C2S packets are not authorized as player interactions

Locations: `ModNetworking.java:23-40`; all handlers in `PlaceAndDepositPkt`, `DepositPkt`, `ExtractPkt`, `RotateBlockPkt`, `RotateItemPkt`, and `TogglePermanentPkt`.

The channel does not pin message directions, and the mutation handlers generally validate only a loaded position and a matching block entity. They do not validate vanilla/Forge reach, line of interaction, spawn/build permission, or the gesture's server-observable prerequisites. In particular:

- `DepositPkt` can mutate any loaded Storage Stack without even requiring the player to look at it.
- `ExtractPkt` has no `isLoaded` check before `getBlockEntity`; depending on the `Level` path, arbitrary positions can cause chunk access/loading. It also permits remote theft from any reachable loaded block entity.
- Rotate packets do not require sneaking or the appropriate torch. `TogglePermanentPkt` does not require an empty hand.
- `PlaceAndDepositPkt` calls `mayUseItemAt`, but still has no reach check and accepts the packet's face as truth.
- Because these are independent custom packets, a claim/protection mod canceling the ordinary server-side right-click does not necessarily prevent the custom mutation.

This is the place to distrust input: the client is not one of our own functions. Register every packet with an explicit `PLAY_TO_SERVER` or `PLAY_TO_CLIENT` direction, and put the common loaded/reach/permission checks in one small packet-boundary helper. Validate held item, hand, and sneaking state for the torch operations. Programmatic player placement should also go through the appropriate Forge placement hook/event so protection and logging mods can observe or veto it.

This is not an argument for defensive checks throughout the interior. Once a packet has been validated and an authoritative block-entity method is called, the repeated internal checks can be reduced.

### 3. High: `ExtractPkt` accepts Storage indices that its first lookup cannot handle

Location: `ExtractPkt.java:48-66`.

The common check permits indices `0..63`, then the Storage branch reads that index through a 27-slot handler before calling `StorageStackBE.extractAt`. A packet with an index from 27 through 63 can therefore reach `ItemStackHandler.getStackInSlot` out of range on the server thread. The block entity's own extraction method already has the correct range check, but the premature capability lookup bypasses it.

Dispatch to the authoritative block-entity extraction method and let it validate its own slot range. The packet does not need to inspect the slot first; its hand-compatibility check is already repeated inside `StorageStackBE.extractAt`.

### 4. High: Singles and Bar have block-entity-dependent collision without `dynamicShape`

Locations: `ModRegistry.java:35-49`, `SinglesStackBlock.java:55-71`, and `BarStackBlock.java:55-71`.

Both blocks derive outline and collision from block-entity contents, but their properties omit `.dynamicShape()`. Minecraft assumes non-dynamic block-state geometry is safe to cache, and some collision/shape queries can consequently use a state-level shape computed without the block entity rather than the current inventory shape. The block-entity-local cache does not replace the required block property.

Declare `.dynamicShape()` for Singles and Bar. Keeping the block-entity-local union cache is still sensible; it avoids rebuilding the 64-shape union on every query.

### 5. High: Storage repacking leaves light and comparator observers stale

Location: `StorageStackBE.java:326-363`.

Repacking sets `suppressSync` while it clears and rewrites every handler. `onContentsChanged` therefore skips client sync, neighbor notification, and light-state calculation. The `finally` block only marks the block entities changed and sends update tags. It never recalculates each block's `LIGHT_LEVEL` and never calls `updateNeighborsAt` for comparator consumers.

Sorting can move luminous items and fill levels between blocks, so both values can be wrong after a successful repack even though the rendered contents update. Finish the batch with one authoritative “contents changed” operation per affected block: recalculate light, update the light property if necessary, notify comparator neighbors, mark dirty, and send one block-entity update.

### 6. High: a Singles cascade sends packets before moving the per-item rotations

Location: `SinglesStackBE.java:162-195`, especially `188-193`.

Each handler extraction/insertion invokes `onContentsChanged` and synchronizes immediately. The code moves `cubeRotations` only after those callbacks, then performs no final `setChanged`/sync. The client therefore sees the item move but retains the old rotation array until some later unrelated update. Persistence happens to be likely because earlier item changes dirty the block entity, but the published state is incomplete.

Batch the column shift, move item and rotation state together, then mark dirty and sync once. Also reset rotation state when a slot becomes empty. At present, extracting an item with nothing above can leave its rotation in the empty slot, and the next deposited item inherits it. Raw capability extraction has the same stale-rotation problem and needs either a small wrapper or a carefully defined `onContentsChanged` policy.

### 7. High: sound resource configuration only affects an integrated server

Locations: `ClientSetup.java:36-40`, `SoundConfig.java:45-51`, and the server calls to `level.playSound` in the three mutation packets.

`SoundConfig` is registered only as a client resource reload listener, but it mutates static `ModSounds` fields that the logical server uses when it sends sounds. This works accidentally in single-player because client and integrated server share one JVM. A dedicated server never runs `SoundConfig`, so every configured mapping remains at the hard-coded vanilla default. Client resource packs also cannot directly choose the `SoundEvent` id already selected in a server sound packet.

Choose one coherent ownership model. Either make sound choice server data/config and load it on the dedicated server, or send a stack-action packet and let each client choose/play its resource-configured sound locally. The latter matches the living specification's client resource-reload language.

There is related dead scaffolding: the six `RegistryObject<SoundEvent>` values in `ModSounds.java:13-34` are never registered on the mod bus, none is used by the runtime fields, and the orphan `bar_extract.ogg` has no `sounds.json` registration. Wire this path completely or remove it.

### 8. High: the config reload listener is on the wrong event bus, and its reload path scales with player count

Locations: `SomeStacks.java:29-40,47-62`.

`ModConfigEvent.Reloading` is a mod-bus event, but `onConfigReload` is added to `MinecraftForge.EVENT_BUS`. The intended live update of block-enable flags and server render overrides therefore is not reliably registered in the Forge-prescribed place. Register it on the `modBus` already available in the constructor and restrict it to this mod's server config.

The reload loop also calls `sendConfigSync` once per player, and `sendConfigSync` calls `ServerOverridesLoader.load` each time. A reload with N players reparses all override files N times on the server thread. Load once, construct one immutable payload/map, and distribute it to all players.

Admins also have no direct command that reloads `server_item_overrides`. Editing those files alone does not cause a Forge config event. Add an operator/console-capable reload command with a success/failure count; relying on reconnecting players, restarting, or touching an unrelated TOML value is not an adequate admin workflow.

### 9. High: a limited repack can split one contiguous pile into two piles

Location: `StorageStackBE.java:270-302,365-373`.

After processing at most `max_stacks_per_resort`, the code deletes empty temporary blocks from the top of that limited list without checking whether another Storage Stack exists immediately above the window. If the pile is taller than the window, consolidation can empty the window's top block and delete it while an unprocessed occupied Storage Stack remains above. The result is a floating upper pile, a new independent base/cooldown, and a gap in what was one pile.

This also exposes a conflict in the living specification: “remove from the top of the processed window” is incompatible with “maximum window is a performance control, not a capacity limit” if removal may sever the structure. Do not delete the window's top block while a Storage Stack exists directly above it. Update the specification with the resulting boundary rule.

### 10. Medium: cross-block support is conditioned on how the target was selected, not on the world state

Location: `PlaceAndDepositPkt.java:91-164`.

Support in the top layer of an existing Singles/Bar block is checked only when the packet's face is `UP`. A legitimate player can place into the same target position by clicking the side of a neighboring block; a forged packet can simply lie about the face. In both cases the new block is above an existing same-type stack but the cross-block support invariant is skipped.

Derive the requirement from `msg.pos.below()` being the same stack type, independent of `msg.face`. The rotated Singles coordinate conversion used by the current check is otherwise a reasonable way to compare the new visual cell with the lower block's top layer.

### 11. Medium: capability lifecycle uses `setRemoved` instead of Forge's capability hooks

Locations: `StorageStackBE.java:53,232-236`, `SinglesStackBE.java:58,241-245`, and `BarStackBE.java:57,205-209`.

Forge's expected block-entity pattern is to invalidate exposed `LazyOptional`s in `invalidateCaps` (calling `super`) and recreate/revive them when the block entity is revived. These classes keep a final optional and invalidate it only from `setRemoved`. That is less interoperable with Forge lifecycle calls and can leave an invalid final capability if the same block-entity instance is revived.

Use the Forge hooks rather than treating vanilla removal as the capability lifecycle. This is a case where following the platform convention is simpler than maintaining a parallel lifecycle.

### 12. Medium: destructive/admin operations bypass the controls administrators normally expect

Locations: `SsCommand.java:49-79` and `TestWallGenerator.java:69-164`, plus direct `setBlock` placement in `PlaceAndDepositPkt.java:166-242` and Storage overflow in `StorageStackBE.java:127-146`.

The entire `ss` tree requires only creative mode. `ss test all` can replace a very large area directly, ignores protection/place events, and does not require operator permission. A creative builder on a protected server may therefore have substantially more destructive authority through this command than through ordinary play. Require an operator permission level for the destructive test commands (while leaving personal render-authoring commands creative-only if desired), and consider a dry-run/size report before `test all`.

Normal custom placement likewise uses `setBlock` directly and does not emit the placement event protection/logging mods expect. Spawn/build checks and Forge event integration should apply to the player-driven placement. Automatic Storage overflow is a different operation, but admins should be aware that it also mutates the world outside player placement hooks; the existing enable flag is the only control over that behavior.

### 13. Medium: cascades and pile operations produce avoidable server/network work

Locations: `SinglesStackBE.java:31-45,176-195`, `BarStackBE.java:30-45,150-170`, and `StorageStackBE.java:120-150,255-324`.

Every individual shift/removal recalculates light across 64 slots and sends a block update. A large Bar collapse can therefore send dozens of full block-entity updates for one click. Singles can send up to two updates per moved cell before its final metadata is even correct. These are natural batch operations and should publish once.

Bar's repeated `do/while` scan is also unnecessary: indices are already traversed bottom-up, and support depends only on the layer below, so a bar removed earlier in the same pass is visible when the next layer is checked.

Storage overflow recurses upward, and every successful recursion frame calls `resortAndPackPile`. The cooldown prevents repeated repacks, but each frame may still scan down to find the base, producing quadratic block-state work in a tall full pile. Have the outermost operation own the single post-deposit repack.

The test-wall generator compounds this by calling normal deposit/sync behavior once per generated item. `ss test all` covers nearly 30,000 corpus items in this project; direct batched population and scheduled generation would be much friendlier to a real server.

### 14. Medium: odd Bar layers use the side/end texture regions backwards

Location: `BarStackBER.java:75-119,126-133`.

The odd-layer branch correctly comments that Z faces are the short ends and X faces are the long sides, and it assigns `end*` and `long*` UV ranges accordingly. The common emission code always applies `long*` to Z and `end*` to X. Odd layers therefore stretch the long-side art over the ends and the end art over the long sides.

Select the UV set per face based on `rotated`, or swap the odd-branch assignments. Also reconcile ARGB support with `RenderType.solid()`: alpha in an authored tint is not blended by the solid render path, despite the documented RGB/ARGB schema. Either render translucent bars appropriately or document/reject non-opaque alpha.

### 15. Medium: exact-tag stacks are not necessarily consolidated

Locations: `StackSort.java:12-32` and `StorageStackBE.java:341-353,419-452`.

The comparator distinguishes only tag presence, not tag contents, then orders by count. Different tagged variants can interleave matching variants, while `consolidate` only compares the current stack with the immediately preceding output stack. Matching item/tag stacks are therefore sometimes left unconsolidated, wasting slots and making packing/removal outcomes depend on counts.

Group by exact item/tag identity before count, or consolidate through an exact-key map. Also remove one of the two consecutive sorts: `allItems` is sorted before `consolidate`, and `consolidate` sorts it again.

### 16. Medium: the declared compatibility ranges exceed the actual baseline

Locations: `gradle.properties:14,18-20` and the expanded dependencies in `mods.toml:47-67`.

The project says Minecraft 1.20.1 and Forge 47.4.0 with no backward-compatibility goal, but metadata declares Minecraft `[1.20.1,1.21)`, Forge `[47,)`, and loader `[47,)`. That advertises older 47.x Forge releases and later 1.20.x Minecraft releases that this binary is not reviewed against. Pin Minecraft to the 1.20.1 interval and make 47.4.0 the Forge minimum (normally with an upper major bound).

The build file also retains a large amount of Forge template/publishing boilerplate and dynamic plugin versions (`1.+`, `[6.0,6.2)`). For a small unreleased mod, removing unused Eclipse, publishing, data-generation, and GameTest scaffolding and pinning plugin versions would make the build more reproducible and much easier to audit.

### 17. Low: measurement reads `isGui3d` before model substitution

Location: `ModelMeasurer.java:57-74`.

`gui3d` is captured from the model before `ForgeHooksClient.handleCameraTransforms`, even though that call may substitute a different model. Flat/volumetric classification can therefore use the placeholder model's flag rather than the model whose quads were measured, contrary to the living specification. Read `model.isGui3d()` after the returned transformed model is assigned. The hard-coded `true` passed to `getRenderPasses` at line 85 should also match the actual render path's fabulous/graphics choice.

`OverrideJsonCodec` should additionally reject non-finite scale/offset components. Gson can produce `NaN`/infinite floats from permissive/string input, and `scale <= 0` does not reject `NaN`; malformed admin data can then poison pose matrices.

### 18. Low: the Bar texture reload path is excessively noisy

Location: `BarTextureStore.java:117-145,147-270`.

Applying mappings logs every mapping at INFO, and auto-tinting logs nearly every intermediate object, file, image dimension, pixel count, average, and result. The bundled mapping file is large enough for a resource reload to emit thousands of log lines. Keep one summary at INFO and move at most one concise per-failure message to WARN/DEBUG. This work is client-side, so it is not a dedicated-server cost, but it is still avoidable reload latency and log churn.

### 19. Low: malformed blacklist entries are silently ignored on every deposit

Locations: `ServerConfig.java:57-69` and `ItemOps.java:139-159`.

The config validator accepts any string. Each player deposit reparses every disabled item id, catches invalid `ResourceLocation`s, and silently skips them. An admin can believe an item is disabled when the entry is ineffective, with no diagnostic. Validate and normalize the lists when the config loads, log invalid entries once, and keep parsed ids/namespaces for the hot path. Use the same case policy in `ss test` filtering as in deposit validation.

## Simplicity and over-guarding

The code is under-guarded at the remote-client boundary but over-guarded and repetitive after it crosses into trusted code. The right simplification is to validate once at the packet/config/mod-compatibility boundary, then make the block-entity operations direct and authoritative.

Concrete cleanup candidates:

- `DepositPkt` and `PlaceAndDepositPkt` check empty/occupied/grounded state immediately before `depositAt` repeats those checks. Call the block entity once after server targeting.
- Both placement and ordinary deposit duplicate nearly the entire Singles/Bar deposit sequence. One small server-side helper would remove real duplication without introducing a framework.
- `StorageStackBE.mergeIntoHandler` creates an unused `sim` stack and makes an `extractItem(i, 0, true)` no-op “to ensure valid slot.” Remove both.
- `PileItemHandler.insertItem` stores an unused `deposited` value.
- `StorageStackBE` sorts the same list twice.
- `BarStackBE.removeUnsupportedBlocks` repeats a scan that bottom-up ordering already makes complete.
- `MinecraftForge.EVENT_BUS.register(this)` registers no annotated instance handlers; the same constructor separately adds the actual listeners.
- `ClientEvents.init()` is empty.
- `PlaceAdjacentToStackRule` is not registered and contradicts the no-Shift placement rule if it ever is registered.
- `InteractionContext.isSinglesOrBarStackAbove`, `getAbovePos`, `InteractionRule.getName`, `BarCubeIdx.indexFromLocal` and its axis-search helpers, and `BlockType`'s sound getters are unused.
- The unregistered custom sound objects, orphan OGG, and `models/item/storage_stack_block.json` pointing at nonexistent `somestacks:block/stack_block` are dead/incomplete resources. There are no block items, so all three item model files are presently unnecessary.
- The Bar torch rules intentionally consume no-op gestures. Unless that behavior has product value, match only the block types with a corresponding server operation instead of documenting dead clicks.
- `RightClickBlockSuppressor` stores a same-tick transient marker in persistent player NBT. A short-lived server-side map/state record is a clearer fit and avoids saving stale bookkeeping indefinitely.

The interaction-rule registry itself is not abstraction for abstraction's sake: first-match ordering is semantic and the classes keep gestures isolated. The three spatial-index utilities are likewise justified because rendering, ray selection, support, and collision must share exact coordinates. The avoidable complexity is mainly duplicated packet code, dead helpers/scaffolding, and defensive branches around capabilities/block entities that this mod itself just created.

## Admin-tool assessment

The cooldown, repack window, enable flags, disabled namespaces, disabled item ids, synced server render overrides, and test-wall tooling cover the right categories. The missing pieces are operational rather than another layer of configuration:

1. An operator/console command to reload server render overrides and rebroadcast config, with counts and parse failures.
2. Operator permission (and preferably a dry-run size) for destructive `ss test` commands.
3. Packet/placement integration that respects reach, spawn/build permission, and protection/logging mods.
4. One-time validation and clear diagnostics for blacklist entries.
5. Batched/scheduled test generation and collapse/repack updates so an admin tool does not cause a long server-thread stall or packet burst.

Those changes would make the mod materially more server-friendly without adding an enterprise-style abstraction layer.

## Suggested fix order

1. Fix Storage rejection/capability semantics and the Storage extraction index crash.
2. Add packet directions, reach/permission validation, and Forge placement/protection integration.
3. Add `.dynamicShape()`, then fix repack light/comparator batching and Singles rotation batching.
4. Correct dedicated-server sound ownership and config reload registration/reload tooling.
5. Prevent limited-window pile splitting and remove the face-dependent support bypass.
6. Batch collapse/test-wall work, then address rendering, metadata, admin diagnostics, and dead code.

