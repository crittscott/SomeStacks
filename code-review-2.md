# Code Review 2

## Verdict

The product architecture is coherent and mostly matches `living-spec.md`: the server owns mutations, the block entities own their invariants, the interaction rule order is explicit, there are no ticking block entities, and the render-override layering has a clear reason to exist. The implementation is not ready to release, however. There are several concrete server-safety and data-integrity defects, followed by a smaller set of Forge-convention, synchronization, and performance problems.

The most important distinction for this review is that packet input is not “ours.” A client can construct any registered C2S packet. Bounds, chunk-presence, reach, permissions, and world-derived invariants belong at that boundary. In contrast, once code has established that it is operating on one of our own block entities, repeatedly asking our own optional capability whether our own handler exists is unnecessary guarding and should be removed.

## Critical and high-priority findings

### 1. A capability insertion of a disabled-mod item can build an empty tower and can also delete items

`PileItemHandler.isItemValid` unconditionally returns `true` (`block/PileItemHandler.java:73-74`), and its real insertion sends every stack to `StorageStackBE.deposit` (`block/PileItemHandler.java:46-49`). `deposit` checks only whether the input is empty before trying the local handler and then creating/descending into the block above (`block/StorageStackBE.java:120-143`). It never applies `isValidStorageItem` at its boundary.

For a disabled-mod item and an empty Storage Stack, the local `ItemStackHandler` rejects every insertion. The input remains nonempty, so `deposit` creates a Storage Stack above and calls the same operation again. This repeats to the build limit, leaving a column of empty blocks from one automation insertion.

There is a second loss case after an admin disables a mod whose partial stacks already exist. `mergeIntoHandler` recognizes the existing and incoming items as mergeable, ignores the remainder returned by `handler.insertItem`, and shrinks the input/counts it as moved anyway (`block/StorageStackBE.java:384-393`). The internal handler rejects that now-disabled item, so the incoming items disappear while the capability reports success. This directly conflicts with the specified rule that existing disabled contents remain extractable; changing a config must not turn later capability calls into item deletion.

Reject invalid input once, at the start of `deposit`, leaving the caller's stack unchanged. Make `PileItemHandler.isItemValid` delegate to the same predicate, and never shrink an input by more than the amount actually accepted by `insertItem`.

### 2. The Storage capability's simulation does not describe its real operation

The simulated insertion considers only the requested local slot (`block/PileItemHandler.java:30-45`), while the real insertion ignores that slot, fills all local partial/empty slots, and can overflow into blocks above (`block/PileItemHandler.java:46-49`). Automation commonly simulates before performing an insertion. Consequently it can reject valid pile overflow, advertise the wrong accepted count, or choose a different slot based on behavior the real call does not have.

This is not Forge-style `IItemHandler` behavior. If a “pile port” deliberately ignores the requested slot, simulation must run the same pile-wide algorithm without mutation and `isItemValid` must state whether the item can ever be accepted. Otherwise expose ordinary slot semantics through the capability and keep pile behavior solely on the player path. The current hybrid is surprising to other mods.

### 3. `ExtractPkt` trusts an index range larger than Storage actually has

The handler permits every index from 0 through 63 (`network/ExtractPkt.java:51`) before the Storage path reads the capability slot (`network/ExtractPkt.java:64-66`). Storage exposes 27 slots. An index from 27 through 63 therefore reaches `ItemStackHandler.getStackInSlot` through `PileItemHandler.getStackInSlot` and can throw on the server thread before `StorageStackBE.extractAt` gets a chance to perform its correct 27-slot validation.

Validate against the selected block entity's slot count, or better, call the block entity's already-validating extraction operation first and avoid the redundant capability read. This is required packet validation, not over-guarding.

### 4. The custom packets bypass normal interaction reach and server-side protection hooks

None of the mutation packets checks that the player is within normal interaction reach of the supplied position. `DepositPkt`, `RotateBlockPkt`, `RotateItemPkt`, and `TogglePermanentPkt` check only that the target chunk is loaded; `ExtractPkt` does not even do that. A modified client can deposit, extract, rotate, or toggle any loaded stack. Placement calls `mayUseItemAt` (`network/PlaceAndDepositPkt.java:73`) but still has no reach check and performs the placement with a direct `setBlock` (`network/PlaceAndDepositPkt.java:168`) rather than the normal placement/event path.

This also bypasses the Forge events on which spawn protection and claim/protection mods expect to act. Deposits and placements can occur with no server-side `RightClickBlock`/place event. Extraction is worse: it mutates first, then `RightClickBlockSuppressor` cancels the later vanilla event at `HIGHEST` priority (`server/RightClickBlockSuppressor.java:31,51-53`), preventing downstream protection listeners from seeing a useful event at all.

Every C2S mutation needs a loaded-chunk check, a normal reach check, and server-side authorization. Placement should honor collision/unobstructed checks and the Forge block-place hook; interactions should be routed through or explicitly post the Forge interaction hook before mutation so claim mods can deny them. Packet directions should also be declared when registering messages, making the trust boundary explicit.

### 5. `ExtractPkt` can load or generate attacker-selected chunks

It calls `level.getBlockEntity(msg.pos)` immediately (`network/ExtractPkt.java:48`) without the `level.isLoaded`/`hasChunkAt` guard used by every other position-bearing packet. Forge's networking guidance specifically warns that trusting packet positions this way can force arbitrary chunk loading/generation and damage server performance and storage.

Reject the packet unless the target chunk is already present before any state or block-entity lookup. This should be fixed even if the general reach/permission work is done, because it is the cheap first boundary check.

### 6. Cross-block grounding is conditional on a client-supplied face

The server checks support beneath a newly placed Singles or Bar Stack only when `msg.face == Direction.UP` (`network/PlaceAndDepositPkt.java:91,117`). Whether the target is above another stack is a fact about the world, not about the face byte supplied by the client. A modified packet can choose another face and bypass the cross-block support invariant; a legitimate placement route that arrives at the same target by another clicked face can also skip it.

Whenever the target's lower neighbor is the same stack type, validate the first item's support against that lower block. Do not gate the invariant on packet narration.

### 7. Storage repacking leaves emitted light and comparator neighbors stale

During repacking, every participating handler sets `suppressSync`, which suppresses not just packets but also light recalculation and neighbor notification (`block/StorageStackBE.java:29-44,327`). The final batch step merely calls `setChanged` and `syncToClients` (`block/StorageStackBE.java:359-361`). When contents move between blocks, their `LIGHT_LEVEL` states still describe the pre-pack inventories, and comparators adjacent to those blocks are not told to recalculate.

Batching is the right server-friendly idea, but the batch must finish with one authoritative post-update per affected block: recalculate/set light, notify comparator neighbors, mark dirty, and send one client update. Centralize that in a method rather than relying on the per-slot callback for some paths and a partial hand-written substitute for another.

## Medium-priority findings

### 8. Server-config reload synchronization is registered on the wrong event bus

`ModConfigEvent.Reloading` is registered on `MinecraftForge.EVENT_BUS` (`SomeStacks.java:39`), but Forge config loading/reloading events are mod-bus events. The handler at `SomeStacks.java:47-54` therefore does not perform the intended broadcast. Changed enable flags and freshly read admin render overrides reach players at login, but not at the specified server-config reload sync point.

Register this listener with `modBus.addListener(this::onConfigReload)`. The official Forge 1.20.1 config documentation explicitly requires `ModConfigEvent.Loading/Reloading` listeners on the mod event bus.

### 9. Singles gravity changes rotation state after the last synchronization

Each source extraction and target insertion synchronizes through `onContentsChanged`, and only afterward does `cascadeUnsupportedBlocks` move the corresponding `cubeRotations` entries (`block/SinglesStackBE.java:176-192`). There is no final `setChanged`/sync after those array writes. The previous inventory callback happens to make the block entity dirty, so a later save will usually persist the new rotations, but the client receives stale rotation data. With one moved item, it receives no packet containing that move's rotation; with several, the final move is always missing.

Batch the column shift, move item and rotation state together, then dirty/synchronize once. This also avoids several full 64-slot block-entity update packets for one extraction.

### 10. Reloadable sounds work differently on an integrated server and a dedicated server

`SoundConfig` is registered only by physical-client setup (`client/ClientSetup.java:39`) and mutates static fields in `ModSounds`. The actual `playSound` calls occur in server packet handlers (for example `network/ExtractPkt.java:87,124,150`). An integrated server shares those client-mutated statics; a dedicated server never runs the listener and keeps the Java fallbacks. This is observable with the bundled data: it selects `minecraft:block.wood.break` (`resources/assets/somestacks/sounds/default.json:4,8,12`), while all three dedicated-server fallback fields are `SoundEvents.WOOL_BREAK` (`ModSounds.java:38,40,42`).

Choose one authoritative design. Either make sound selection server-loadable and sync it, or send an action-sound message to tracking clients and let each client resolve its resource configuration. The present cross-side static is not a valid multiplayer boundary.

The same area contains dead implementation: `ModSounds.SOUND_EVENTS` and its six `RegistryObject`s are never registered on the mod bus or used, and the lone OGG has no ordinary `assets/somestacks/sounds.json` definition. Remove them if custom registered sounds are no longer part of the product.

### 11. `ss test all` performs its worst-case work in one server tick and synchronizes every item insertion

The command lays the entire floor with `UPDATE_ALL`, places every block with `UPDATE_ALL`, and calls normal `StorageStackBE.deposit` once per test item (`command/TestWallGenerator.java:123,137,157-161`). Each ordinary deposit can dirty/sync, update neighbors, recalculate light, and consider pile sorting. For the thousands of items explicitly anticipated by the specification, this is a large main-thread spike, neighbor-update storm, and network burst; it risks a watchdog timeout before the deliberate client rendering load even begins.

Give the generator a direct batched population path that writes one block entity and synchronizes it once. For `all`, schedule a bounded number of placements per tick (and report progress/completion) rather than doing the whole modpack synchronously. This is the largest server-friendliness issue outside packet abuse.

### 12. Rotating Storage needlessly triggers a pile repack

After changing the visual rotation, `RotateBlockPkt` calls `sbe.resortAndPackPile()` (`network/RotateBlockPkt.java:47-52`). Rotation does not alter inventory order or violate a pile invariant, and the specification lists repacking after deposit/extraction, not rotation. This call can consume the base cooldown, rewrite/synchronize several blocks, and remove empty temporary blocks in response to a cosmetic action.

Remove it. `setRotation` already marks and synchronizes the block entity.

### 13. The 2D renderer ignores Forge render passes

The measurement path correctly iterates `model.getRenderPasses(stack, true)` (`client/measure/ModelMeasurer.java:85`), but the actual 2D render path asks only the root model for one unculled quad list (`client/CubeRenderHelper.java:141`). Forge multi-pass item models can therefore measure as complete but render with missing layers in `2d` mode.

Iterate the resolved model's Forge render passes and project the unculled quads from each pass, applying each quad's tint as now. If supported items depend on non-cutout render types, honor the model's Forge render types as well instead of forcing every pass into one cutout buffer.

### 14. Measurement records `isGui3d` before Forge can substitute the model

`ModelMeasurer` reads `model.isGui3d()` at line 60 and then lets `ForgeHooksClient.handleCameraTransforms` return a possibly different model at line 68. Bounds use the substituted model, but flat/volumetric classification uses the original one's flag. That contradicts the stated guarantee that model substitution is honored and can select `2d`/`3d` incorrectly for an item whose transform substitutes a model with a different `isGui3d` value.

Read `isGui3d` from the returned model after camera transforms.

### 15. Capability lifecycle uses `setRemoved` instead of Forge's capability hooks

All three block entities own a final `LazyOptional` and invalidate it in `setRemoved` (`StorageStackBE.java:53,233-235`; `SinglesStackBE.java:58,242-244`; `BarStackBE.java:57,206-208`). Forge's supported lifecycle is `invalidateCaps`, with recreation through the corresponding revive/load lifecycle when an instance can become live again. The current approach also misses lifecycle routes that invalidate capabilities without calling the subclass's hand-written `setRemoved` path.

Override `invalidateCaps`, call `super`, and invalidate there. Make the optional recreatable if the Forge lifecycle can revive the block entity. This is chiefly a Forge-convention correction, but capability consumers depend on it.

## Low-priority correctness and maintainability findings

### 16. Admin blacklist typos are silently ignored on every deposit attempt

`DISABLE_ITEMS` validates only that entries are strings (`ServerConfig.java:63-69`). `ItemOps.isItemDisabled` reparses every configured string for every check and silently catches invalid resource locations (`util/ItemOps.java:149-158`). An admin who mistypes an ID receives neither a config rejection nor a log message and believes the blacklist is active.

Validate/parse these values when the config loads or reloads, log invalid entries once, and keep a parsed set for runtime membership checks. The same pattern can normalize the disabled-mod set once rather than scanning strings case-insensitively on every handler validation.

### 17. Internal code repeatedly treats our own capability as optional and duplicates authoritative checks

Examples include all three renderers and ray tracers retrieving an optional handler from a known Some Stacks block entity, plus packet handlers that check index, occupancy, and grounding and then call `depositAt`, which repeats those checks (`network/DepositPkt.java:84-101,109-126`; `network/PlaceAndDepositPkt.java:186-230`). This produces lambda workarounds such as one-element boolean arrays and makes it harder to see which layer actually owns an invariant.

Expose narrow package/public domain operations or read-only slot access from each block entity for our own code. Keep the capability wrapper solely as the Forge-facing automation interface. Let the block entity perform occupancy/support/item validation once. Packet handlers should validate untrusted facts (sender, loaded chunk, reach, permission, packet index/type) and then call that operation. That is simpler without weakening safety.

### 18. There is a visible tail of dead scaffolding

Concrete unused pieces include:

- `MinecraftForge.EVENT_BUS.register(this)` even though `SomeStacks` has no annotated instance subscribers (`SomeStacks.java:36`);
- empty `ClientEvents.init` and its call (`client/ClientEvents.java:32`, `client/ClientSetup.java:23`);
- the unregistered `PlaceAdjacentToStackRule`, which encodes the expressly unsupported Shift+V placement gesture (`client/interaction/PlaceAdjacentToStackRule.java:6`);
- unused `InteractionRule.getName`, `InteractionContext.isSinglesOrBarStackAbove/getAbovePos`, and `BlockType.getDepositSound/getExtractSound/toStackMode`;
- unused item models despite the deliberate absence of block items; the Storage item model also names nonexistent parent `somestacks:block/stack_block`.

Delete these rather than preserving hypothetical extension points. The ordered rule registry itself is justified because first-match order is product behavior; the unused shadow rule and context APIs are not.

### 19. Bar texture reload logging is far too verbose for normal INFO logs

Every resource reload logs every final mapping and a long multi-line trace for every auto-tint calculation (`client/BarTextureStore.java:138-185,212-266`). The bundled mapping corpus is large, so normal client logs are flooded and string formatting work is done even when nothing is wrong.

Keep one INFO summary and move per-item diagnostics to DEBUG. Failures can remain WARN.

## Overall answers to the design questions

- **Expected Minecraft/Forge way:** Deferred block/block-entity registration, server-owned state, update NBT, Forge item handlers, client-only renderer setup, resource reload listeners, and the first-match interaction list are sound choices. The main non-Forge behavior is the custom packet path bypassing reach/interaction/place hooks, followed by the mismatched `IItemHandler` simulation and nonstandard capability lifecycle.
- **Over-guarding:** Yes, internally. Capability optionals and duplicate BE/type/invariant checks are protecting against failures of our own registration and our own methods. Remove those by giving block entities direct domain APIs. Do not remove packet, config-file, or resource-file validation; those are genuinely untrusted boundaries, and packet validation is currently insufficient rather than excessive.
- **Simplicity:** The central architecture is understandable, but dead sound registration, dead interaction/context APIs, repeated packet branches, and internal capability plumbing obscure it. A small shared packet precondition helper (loaded/reach/permission) and authoritative BE operations would remove more code than they add. Avoid a broader framework.
- **Server friendliness:** The no-ticker design and pile-window/cooldown controls are good. The remote packet surface, invalid-item tower, synchronous `ss test all`, per-slot BE synchronization during cascades, and unnecessary rotation repack are not server-friendly.
- **Admin tools:** Enable flags, blacklists, pile controls, server-authoritative render overrides, reload sync, and the creative `ss` workflow cover the right needs. Reload sync currently does not fire, claim/protection mods can be bypassed, blacklist typos are invisible, and `ss test all` needs batching/progress to be safe on an actual server.

## Verification and references

I reviewed all Java sources and the relevant resources against `living-spec.md`. I also parsed every resource JSON file and checked every bundled item-render override for the documented mode, positive-scale, and three-component-offset constraints; those checks passed. Per `CLAUDE.md`, I did not compile, run Gradle, or launch tests.

Forge references used for convention checks:

- Config reload event bus: <https://docs.minecraftforge.net/en/1.20.1/misc/config/#configuration-events>
- Defensive packet position handling: <https://docs.minecraftforge.net/en/1.20.x/networking/simpleimpl/#handling-packets>
- Capability invalidation: <https://docs.minecraftforge.net/en/1.20.x/datastorage/capabilities/#exposing-a-capability>
