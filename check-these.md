# Review fixes: verification and port plan for 1.21.8

This verifies, against the current 1.21.8 code, the review findings fixed in `26.3` (see `../26.3/check-these.md` for each finding's full text, verification, and disposition) and lays out how to port them. IDs match that file. Status: all four batches ported (see the port records); builds, GameTests, and client checks pending.

## Source of the port

Port from the 1.21.11 port commit (`../1.21.11`, `cf7af69`, recorded in `../1.21.11/check-these.md`), not from the four 26.3 commits. 1.21.11 is the closer template: it keeps Forge, pins Common Protection API 1.0.0, uses mapped names, keeps the chunk-free `ConfigSyncPkt` on all loaders (NW-01 not ported), and already has hand-written Forge counterparts.

Measured closeness: of the 204 non-Javadoc files the 1.21.11 port touched, 103 are identical (ignoring whitespace) between 1.21.8 now and 1.21.11 just before the port (`27d1b45`). Most of the rest differ only by API names (`ResourceLocation`/`Identifier`, render and event types). Generated `docs/javadoc` is excluded: regenerate it with `generateDocs` after the port.

Method:

1. Export `git -C ../1.21.11 diff 27d1b45 cf7af69 -- . ':!docs/javadoc'` and apply it to 1.21.8 with rejects (for example `git apply --reject`, working tree only). This touches Git tooling, so the user runs it or explicitly authorizes it.
2. Hand-merge rejected hunks against 1.21.8 code, applying the 1.21.8 decisions below.
3. Hand-write the files whose 1.21.8 structure differs (see "Hand-written in 1.21.8").
4. Update orientations, `docs/`, and `build-env/`; the user builds and runs GameTests and `/ssclienttest` on all three loaders.

## Verification summary

Every finding was checked in 1.21.8 by locating its pre-fix code. Unless noted, the issue is present exactly as it was in 1.21.11 before its port, and the plan is to port it as 1.21.11 did.

| Finding | Present in 1.21.8 | Evidence | Plan |
| --- | --- | --- | --- |
| DC-01 | Yes | `DepositIntoClickedStackRule`, `DepositIntoAdjacentStackRule` exist | Superseded by OR-01 (package deleted) |
| DC-05 | Yes | `"DataVersion"`/`"Items"` literals in `StackBlockEntity`, `StackDataMigration` | Port |
| DC-07 | Yes | `AutomationActor.is`, two `isProtected`, two `placeChecked` overloads | Port |
| DC-10 | Yes | Repeated sound playback in `StackInteractions` | Port |
| DC-12 | Likely | Same comment text as 1.21.11 pre-port | Port the corrected comments; check each hunk |
| DC-14 | Yes | Forge and NeoForge `RegistryObject`/`DeferredHolder<SoundEvent>` fields | Port, both loaders |
| DC-16 | Yes | `ResourceLocation.tryBuild(namespace, "validation")` | Port |
| EF-01 | Yes | Per-slot `canGrow` → `WorldEdits.mayPlace` in all three runs | Port |
| EF-02 | Partly | `RunItemHandlerBase` and Fabric resolve `itemRun()` per access | Port `StackRun.baseBlockEntity`; Fabric ledger access as 1.21.11; no NeoForge ledger (see below) |
| EF-03 | Yes | `StackSort.SortKey` stores `patch.toString()` eagerly | Port |
| EF-04 | — | Deferred in 26.3 | Not ported |
| EF-05 | Yes | `BarStackBER.render` resolves texture data and sprite every frame | Hand-written (old BER API) |
| EF-06 | Yes | `LevelRenderer.getLightColor` in `BarStackBER` and `CubeRenderHelper` | Hand-written: use the `light` argument vanilla passes to `render` |
| EF-07 | Yes | Per-entity `cachedRun`/`cachedRunTick` | Port |
| EF-08 | Yes | `Shapes.or` in `BarStackBE`/`SinglesStackBE.computeShape` | Port |
| EW-02 | Yes | `StackBlock` use methods always return `SUCCESS`/`CONSUME` | Port |
| EW-03 | Yes | `pendingBlockRotation`/`applyPendingBlockRotation` | Port |
| EW-05 | Yes | No `reloadPolicy`; no command-tree resend | Port, including Forge config events |
| EW-06, EW-08 | — | Deferred / unconfirmed in 26.3 | Not ported |
| EW-09 | Yes | `JsonServerConfig` calls `ServerConfig.install`/`apply` | Port |
| EW-10 | Yes | `BlockType.of` scans `values()` | Port |
| EW-11 | Yes | `ThreadLocal<Boolean> consultingAdjacent` | Port |
| GD-03 | Yes | `isCanceled()` checks in NeoForge `ClientEvents`/`StackInteractionEvents` | Port (NeoForge only, as 1.21.11) |
| GD-04, GD-06, GD-07 | — | Retained in 26.3 | Not ported |
| GD-05 | Yes | Same dispatch as 1.21.11 pre-port | Port |
| GD-08 | Yes | `supportSeamBeneath(@Nullable Level, …)` | Port |
| GD-09 | Yes | `static boolean reload()` | Port |
| LG-02 | Yes | 140 `insertItem(…, false)` calls | Port; `RunItemHandlerBase` keeps run-level `simulate` |
| LG-04 | Yes | `StoragePile.editor` and per-site null-actor choices | Port |
| LG-05 | Yes | `automationSlotLimit`/`acceptsAutomation` forwarders used by `RunItemHandlerBase` | Port; base uses `localSlotLimit`/`isStoredItemValid` |
| LG-07 | Yes | `ClientRenderPacketSink`, `enableStack` | Port (then OR-08 replaces it) |
| LD-02 | Yes | Both loaders' hook overloads in common `StackBlockEntity` | Port `ForgeStackBlockEntities` and `NeoForgeStackBlockEntities`, Fabric client-update mixin |
| LD-03 | Yes | NeoForge hardcodes defaults and key strings | Port, both Forge and NeoForge, plus `ServerConfigMigration` key use |
| LD-06 | Yes | Generic `ModRegistry`, `ModNetworking`, `ClientEvents`, `ClientSetup`, `KeyMappings`, `StackInteractionEvents`; `MODID` aliases | Port the renames on Forge and NeoForge, as 1.21.11 |
| LD-07 | Yes | `CapabilityAndPersistenceChecks`/`GameTests` | Port; also rename 1.21.8's `unversioned_save_upgrades_items_on_load.json` instances |
| LOG-02 | Yes | No rejection log in `StackBlockEntity` | Port |
| LOG-04 | Yes | `logPolicy` omits gallery access and list counts | Port |
| LOG-05 | Yes | `warn(…, e.getMessage())` in `BarTextureStore`, `ItemRenderOverrides` | Port |
| MN-01 | Yes | `StackBlockEntity` storage slot limit `return 64` beside item-max settlement | Port, with 1.21.8 migration (below) |
| MN-06 | Yes | Dummy identifiers for validation | Port with `ResourceLocation.isValidNamespace` |
| MN-07 | Yes | Literal minimum bounds | Port |
| MN-09 | Yes | Parallel shape/box construction in `CubeGrid`, `BarCubeIdx` | Port |
| MN-10 | Yes | Literal `fill * 14.0` | Port the named bound only |
| MN-11 | Yes | Literal UV grid, GUI angles, rotation length, seed | Port; renderer literals hand-written with EF-05 |
| MN-12 | Yes | `setBlock(…, 3)` in tests | Port |
| ML-01 | No | Fabric is the only transactional adapter; Forge and NeoForge already share `RunItemHandlerBase` | Not applicable; see below |
| ML-02 | Yes | Cleanup/growth-protection bodies copied into each loader's `ProtectionGameTests` | Port, Forge too (as 1.21.11) |
| ML-03 | — | Resolved by EW-09 | Nothing extra |
| ML-05 | Yes | NeoForge static `clientReceiver` | As 1.21.11: keep the hook, installed with `ClientServerSettings::apply` |
| ML-08 | No | Forge is a supported module | Not applicable |
| NW-01 | No (as 1.21.11) | Forge and NeoForge use SERVER configs, which are not synced | Not ported; all loaders keep one `ConfigSyncPkt` |
| NW-02 | Yes | Chunk/generation staging in `ConfigSyncPkt` | Port |
| NW-03 | Yes | Static hook | As 1.21.11 (see ML-05) |
| NW-04 | Yes | `SsCommand.syncClientToolState` | Port |
| NW-05 | Yes | Hand-written gesture codec | Port (`ByteBufCodecs.idMapper`) |
| OR-01 | Yes | `client/interaction` rule package | Port |
| OR-03 | Yes | `tick`/`getAnalogOutputSignal` repeated in each block | Port |
| OR-04 | Yes | Per-run growth/trim | Port |
| OR-05 | Yes | `isGroundedIn` | Port |
| OR-08 | Yes | `StackState`, `ClientRenderToolState` | Port, Forge `ClientSetup` lifecycle too |
| OR-10 | Yes | `SetAside` handling in `StackItemStorage` | Port; keep 1.21.8's DataFixerUpper upgrade from 3955 |
| OR-11 | — | Sanitation retained in 26.3 | Port only the `RenderMode` comment |
| OR-12, OR-16, RI-08 | — | Retained in 26.3 | Not ported |
| OR-13 | Yes | `ViewRays`, `RotationSoundThrottle`, `StackPlacement` | Port |
| OR-14 | Yes | `StorageStackBER`, `SinglesStackBER` | Hand-written `GridStackBER` (old BER API) |
| OR-15 | Partly | Lifecycle duplication present | Port as 1.21.11 |
| PR-01 | Yes | Common Protection API optional and compile-only | Port as 1.21.11: required, bundled 1.0.0 |
| PR-02 | Yes | `FabricEditAuthority` fires synthetic `UseBlockCallback` | Port as 1.21.11 |
| PR-04 | No | No transactional bulk offer or tentative growth on Forge or NeoForge | Not applicable; see below |
| PR-05 | Yes | No `blockActionRestricted` in `WorldEdits` | Port |
| RI-01 | Yes | `Block.popResource` in `BarDropBatch` | Port |
| RI-02 | Yes | `StackPlacement` documents that no `BlockPlaceContext` is built | Port |
| RI-05 | Yes | `setPlacedBy` in `WorldEdits.placeChecked` | Port `completePlacement`; both item-handler loaders complete through `placeChecked` |
| RI-07 | Yes | `listResources` in `BarTextureStore`, `ItemRenderOverrides` | Port `FileToIdConverter`; keep Fabric's reload-listener wrappers |

## Decisions specific to 1.21.8

- **NeoForge automation is `IItemHandler`.** 1.21.8 NeoForge has no transactional `ResourceHandler`. Its `RunItemHandler` and Forge's both extend common `RunItemHandlerBase`. So every 1.21.11 NeoForge transaction change applies to neither loader here. The 1.21.11 Forge `RunItemHandler` changes (LG-02, LG-05, EF-02) go into `RunItemHandlerBase` once.
- **ML-01:** Fabric is the only adapter with staging, so there is no duplicated bookkeeping. Keep Fabric's staging in `FabricRunItemStorage`, and do not add a common `RunTransferLedger` that only Fabric would use. Port only the EF-01/EF-02/LG-02 adjustments into the Fabric adapter. If hand-merging 1.21.11's ledger-based adapter proves much simpler, the alternative is to put the ledger in the Fabric module. That is a user decision.
- **PR-04:** Not applicable, as for Forge in 1.21.11. A denied hopper growth on Forge or NeoForge is re-queried on each `insertItem` commit, and there is no bulk offer to scope a coalescing cache to.
- **MN-01 migration:** The immediately preceding release is 0.8.2 (1.21.1), whose saves have no `DataVersion` (treated as 3955). Split over-cap Storage entries from those saves, after the existing DataFixerUpper upgrade.
- **Protocol:** 1.21.8 is at 5; bump to 6 for NW-02/NW-05.
- **Common Protection API:** 1.21.8 already pins 1.0.0 with `GameProfile` signatures, matching 1.21.11. Remove `-PwithCommonProtectionApi`, `CommonProtectionQueries`, `FabricProtectionQueries`, and `NoProtectionApiGameTests`. Use `modImplementation(include(...))` (remapping Loom). Make `ProtectionApiGameTests` always an entrypoint, and update `fabric.mod.json` and `build-env`.
- **Game-rule id:** `BarColumnChecks.contentsDropWithBlockDropsDisabled` (RI-01) must use 1.21.8's `doTileDrops`, not 1.21.11's `minecraft:block_drops`.
- **RI-02 fixture:** 1.21.8's placed-block criterion test is `interactiongametests/player_placement_awards_criterion_after_deposit`, not 1.21.11's `protectiongametests/player_placement_and_growth_award_placed_block`. Check whether it clicks replaceable air; if so, seed a solid support as 1.21.11 did.
- **Forge Adventure cleanup (PR-05):** Expect the same limitation as 1.21.11, where Forge's connectionless test player cannot change game mode. Run that scenario on NeoForge and Fabric only unless Forge 58 proves otherwise.

## Hand-written in 1.21.8

1.21.8 predates render states and submission. Its BERs implement `render(be, partialTick, PoseStack, MultiBufferSource, light, overlay, cameraPos)`. These files cannot take the 1.21.11 hunks:

- `client/BarStackBER` (EF-05, EF-06, MN-11): Cache prepared per-slot presentation (texture data, sprite, boxes, tint) on the block entity. Discard it on content load and when a resource-generation stamp changes after reload. Use the `light` argument each frame.
- `client/GridStackBER` replaces `StorageStackBER`/`SinglesStackBER` (OR-14). It provides Storage/Singles factories and reads rotations from the entity being rendered, not a constructor-bound callback. Register it in all three loaders.
- `client/CubeRenderHelper` (EF-06, 692 differing lines from 1.21.11): Take the frame light as a parameter instead of looking it up. Keep 1.21.8's existing per-stack LRU presentation cache.
- `ClientCallbackChecks` (279 lines): Adapt the Bar presentation-cache and client-ingress (LD-02) checks to the 1.21.8 renderer and cache.
- `FabricRunItemStorage` and `FabricTransferGameTests`: Apply per ML-01 above.
- Common GameTests with larger differences (`InteractionChecks`, `ProtectionChecks`, `AutomationAndPersistenceChecks`, `ConfigurationChecks`): hand-merge them.

## Not ported, as in 26.3 and 1.21.11

EF-04 and EW-06 (deferred); EW-08 (unconfirmed); GD-04, GD-06, GD-07, ML-03, OR-11's sanitation deletion, OR-12, OR-16, RI-08, MN-10's float-helper substitution; and the qualified or rejected parts of DC-05, DC-07, DC-12, DC-14, DC-16, and EW-09. Also not applicable here: ML-01, ML-08, NW-01, PR-04.

## Suggested order

Follow the 26.3 batch order, so each stage can be built before the next:

1. Batch 1 (DC, EF, EW), including the hand-written Bar/grid renderer work.
2. Batch 2 (GD, LG, LD), including the Forge/NeoForge `*StackBlockEntities` and the Fabric mixin.
3. Batch 3 (LOG, MN, ML, NW), including the protocol bump and the MN-01 migration from 3955.
4. Batch 4 (OR, PR, RI), including the Common Protection API build change.

The user builds and runs server GameTests after each batch, and `/ssclienttest` on all three clients after batches 1, 2, and 4.

## Batch 1 port record

Batch 1 is 26.3 commit `1a60a5c`. Its diff, minus Javadoc HTML, `check-these.md`, the orientations, the renderers, and NeoForge's transactional handler, was applied with GNU `patch` (fuzz 2) to the working tree. No Git commands changed anything. Rejected hunks were merged by hand, and the fuzzed hunks were reviewed. No builds, GameTests, or `/ssclienttest` runs were performed.

Differences from 26.3:

- **DC-07:** `ServerConfig.galleryRequiredPermissionLevel` is kept. In 1.21.8 it is the production accessor `SsCommand` uses for `hasPermission(int)`; 26.3 deleted it only because a separate `galleryPermission()` existed there.
- **EW-03:** Rotation getters are pure, and setters write native facing through `level.setBlock`. 1.21.8 also migrates the 1.21.1 block-entity `Rotation` field, so a migration-only `pendingLegacyRotation` remains. It is applied when the save is loaded and when the entity attaches to a server level, but never from a getter.
- **EF-01/EF-02:** These apply to `StackRun` and Fabric as in 26.3. The Forge and NeoForge `IItemHandler` views use the shared resolved run (EF-07) and growth preflight (EF-01). They have no bulk insert to scope a `GrowthQuery` to, so no handler code changed.
- **EF-05/EF-06 (hand-written):** `StackBlockEntity` gained a client-only `renderCache`, cleared by `loadClientUpdate`. `BarStackBER` caches per-slot bounds, layer, UV regions, and texture data, stamped with a resource generation that `onResourceReload` bumps. Bar and grid rendering use the `light` argument vanilla passes. `CubeRenderHelper.renderGridItems` takes that light and no longer takes a position.
- **`/ssclienttest`:** `ClientCallbackChecks.checkBarPresentationCache` draws a scratch Bar Stack into a discarded `MultiBufferSource.immediate` buffer. It checks reuse, invalidation after a resource reload, and invalidation after a client update.
- **Forge counterparts:** `ClientEvents` uses the reduced `InteractionContext` factories (DC-01). `ModRegistry` sound holders are removed while registration remains (DC-14). Forge config events already route through `SomeStacksServer.onPolicyReloaded`, now `reloadPolicy` (EW-05).
- **Tests:** Permissions use 1.21.8's integer levels (0/4). `NoProtectionApiGameTests` passes `Shapes.block()` to the remaining `placeChecked` overload.
- **Orientations:** `orientation-code.md` and `orientation-player.md` are updated and trimmed to their limits. `docs/server-admin.md` describes the changed-only resync.

Batch 1 checks when building:

- `MultiBufferSource.immediate(ByteBufferBuilder)` and `ByteBufferBuilder(int)` exist in 1.21.8.
- The 1.21.1 `Rotation` migration still converts on load (the unversioned save upgrade GameTests).
- Unhandled modified clicks pass through on all three loaders.

## Batch 2 port record

Batch 2 is 26.3 commit `fcd9a2b`. As in batch 1, its diff, minus Javadoc HTML, `check-these.md`, the orientations, and NeoForge's transactional handler and growth placement, was applied with GNU `patch` (fuzz 2), including git renames. Rejected hunks were merged by hand. No builds, tests, or Git operations were performed.

Differences from 26.3:

- **LG-02/LG-05:** `SlotAccess` is a read-only view. `StackItemStorage` owns the two-argument `insertItem`/`extractItem` and `save`/`load`. Every local call that passed `simulate=false` was updated across all four modules. The Forge/NeoForge `IItemHandler` keeps run-level simulation, and its shared `RunItemHandlerBase` uses `localSlotLimit`/`isStoredItemValid`.
- **LD-02:** `isValidClientUpdate` and the shared metadata check keep 1.21.8's rejection of the legacy `Rotation` field.
  - Both loaders' hook overloads are gone from common `StackBlockEntity`.
  - `NeoForgeStackBlockEntities` overrides `handleUpdateTag(ValueInput)`/`onDataPacket(Connection, ValueInput)`.
  - The new `ForgeStackBlockEntities`, copied from 1.21.11, overrides `handleUpdateTag(ValueInput, Provider)`/`onDataPacket(Connection, ValueInput, Provider)`.
  - Both registries register these subclasses.
  - Fabric's `StackBlockEntityClientUpdateMixin` uses 1.21.8's `isClientSide` field.
  - All three `ClientCallbackGameTests` supply native entry points to `ClientCallbackChecks.checkClientUpdateIngress`.
- **LD-03:** `ForgeServerConfig` and `NeoForgeServerConfig` both take section/key names and defaults from `ServerConfig`, and share `onConfigChanged`. The permission range is `0..Commands.LEVEL_OWNERS`. Fabric's JSON backend and `ServerConfigMigration` use the shared section/key names.
- **LD-06:** NeoForge classes are renamed as in 26.3; NeoForge keeps its SERVER config without an explicit filename. Forge counterparts follow 1.21.11:
  - `ForgeRegistry`, `ForgeNetworking`, `ForgeClientEvents`, `ForgeClientSetup`, `ForgeKeyMappings`, and `ForgeStackInteractionEvents`.
  - The entrypoint is `SomeStacksForge`, and `ForgePlatformServices` is in the root package.
  - Both `MODID` aliases are removed.
- **LD-07:** Shared checks and all three wrappers use `AutomationAndPersistence` names. The Forge and NeoForge test-instance directories, JSON function ids, and registrations are renamed, including 1.21.8's `unversioned_save_upgrades_items_on_load`.
- **LG-07:** Forge's packet handler and client setup use `ConfigSyncReceiver`.
- **LG-04:** `NoProtectionApiGameTests` uses the level-aware `mayPlace`.
- **GD-03:** NeoForge only, as in 1.21.11. Retained `isCanceled` checks inspect explicitly posted events.

Batch 2 checks when building:

- The NeoForge 21.8 and Forge 58.1 hook signatures match the subclass overrides.
- `BlockEntity.loadWithComponents(ValueInput)` is the mixin target in 1.21.8.
- `/ssclienttest` passes the ingress check on all three clients, including rejection of inherited `components`.
- GameTest discovery finds every renamed Forge/NeoForge `automationandpersistencegametests` function.

## Batch 3 port record

Batch 3 is 26.3 commit `d182fd3`, applied the same way. Left out of the patch, besides docs and orientations: `RunTransferLedger` and Fabric's ledger rewrite (ML-01 does not apply), and NeoForge's transactional handler. Also left out were the NeoForge networking, client-setup, entrypoint, and platform-service hunks that delete the clientbound payload (NW-01 is not ported). No builds, tests, or Git operations were performed.

Differences from 26.3:

- **NW-01/NW-03/ML-05:** All three loaders still send `ConfigSyncPkt`.
  - Forge and NeoForge `sendConfig` now return `true`, so `/ss reload` reports the players sent.
  - NeoForge keeps its static receiver hook, installed with `ConfigSyncReceiver::apply`.
- **NW-02:** `ConfigSyncPkt` is 26.3's (and 1.21.11's) five-field record with `ResourceLocation` names. `ConfigSyncNetwork` documents delivery on every loader.
- **NW-04/LOG-04:** `SomeStacksServer` builds the snapshot at server start and clears it on stop. `logPolicy` reads 1.21.8's integer gallery permission.
- **MN-01:** `normalizeStorageInventory` runs after the existing DataFixerUpper upgrade, so it also splits over-cap stacks from 1.21.1 saves (unversioned, 3955). Storage `localSlotLimit` is `STORAGE_SLOT_LIMIT`.
- **MN-07:** 1.21.8 already derives its gallery permission default from `Commands.LEVEL_ADMINS`, so only the minimum-bound constants were added, in all three backends.
- **LOG-02:** The rejection warning uses `dimension().location()` and still treats legacy `Rotation` as server-only.
- **ML-02:** Seven duplicated scenarios now delegate to `ProtectionChecks` in all three loaders' wrappers. NeoForge's and Forge's `offerAt` use `IItemHandler` simulation.
  - The Forge and NeoForge Bar refusal registrations and JSON moved to `protectiongametests`.
- **NW-02 and MN-01 tests on Forge:** Forge drops the three obsolete chunk/budget scenarios, with their registrations and JSON. It adds the two Storage cap/migration wrappers, registrations, and JSON. Registrations, wrapper methods, and JSON instances were cross-checked: Forge 123, NeoForge 122.

Batch 3 checks when building:

- `ResourceLocation.isValidNamespace` and `ByteBufCodecs.stringUtf8(...).apply(ByteBufCodecs.list(...))` exist in 1.21.8.
- Forge's `SimpleChannel` `addMain(ConfigSyncPkt.class, ConfigSyncPkt.STREAM_CODEC, â€¦)` accepts the composite codec.
- Clients on all three loaders receive policy at login and after `/ss reload`, list edits, and file edits.
- `storageDiskMigrationPreservesOverCapContents` passes, and a 1.21.1 world with over-64 Storage stacks splits on load.

## Batch 4 port record

Batch 4 is 26.3 commit `d459280`, applied the same way. Left out of the patch, besides docs and orientations:
- build inputs (`build-env`, `gradle.properties`, `fabric/build.gradle`, `fabric.mod.json`)
- `FabricEditAuthority`
- the 26.3 grid renderers
- NeoForge's transactional handler, growth placement, and transfer tests

No builds, tests, or Git operations were performed.

Taken from the finished 1.21.11 port where 1.21.8 matched 1.21.11's pre-port state, with `Identifier` mapped back to `ResourceLocation`:
- **Blocks:** `BarStackBlock`, `SinglesStackBlock`, and `StorageStackBlock` (OR-03). Their only 1.21.8 differences were the code batch 4 moves into `StackBlock`.
- **Fabric (PR-01/PR-02):** `fabric/build.gradle` and `build-env/fabric/build.gradle`, `fabric.mod.json`, `FabricEditAuthority`, `ProtectionGameTests`, and `ProtectionApiGameTests`.
  - Common Protection API 1.0.0 is required and bundled through `modImplementation(include(...))`.
  - `CommonProtectionQueries`, `FabricProtectionQueries`, and `NoProtectionApiGameTests` are deleted.
  - The GameTest metadata always lists `ProtectionApiGameTests`.
  - 1.21.11's `playerPlacementAndGrowthAwardPlacedBlock` wrapper was dropped: 1.21.8 keeps its own criterion test in `InteractionGameTests`.
- **Forge client (OR-01/OR-08/OR-14):** `ForgeClientEvents`, `ForgeClientSetup`, and `ForgePacketHandlers`, which use `StackGesture`, `SomeStacksClient`, `ClientServerSettings`, and `GridStackBER`.

Hand-written or merged for 1.21.8:
- **OR-14:** `GridStackBER` implements 1.21.8's `render(be, partialTick, pose, buffers, light, overlay, cameraPos)`. Its Storage/Singles factories read layout and item rotation from the rendered entity each frame. All three loaders register it; `StorageStackBER`/`SinglesStackBER` are deleted.
- **OR-05:** `BarCubeIdx`/`SinglesCubeIdx` gained the shared `isSupported` predicates, replacing `isGroundedIn`. `BarColumn.acceptsAt` and `SinglesColumn.supporterOf` evaluate staged contents through them.
- **OR-10:** `StackBlockEntity` loads through `StackDataMigration.load`, then still converts the legacy 1.21.1 `Rotation` field. Saved-item reporting moved from `SomeStacksServer` to `StackDataMigration`, which uses `dimension().location()`.
- **OR-08:** NeoForge keeps its clientbound payload (NW-01 not ported); its static receiver installs `ClientServerSettings::apply`. NeoForge and Forge use `SomeStacksClient` join, disconnect, and stopping.
- **RI-01:** The disabled-block-drops scenario uses 1.21.8's `gamerule doTileDrops`. Forge and NeoForge register it with JSON.
- **RI-02/PR-05:** Forge registers the vegetation-replacement scenario. Adventure cleanup runs on NeoForge and Fabric only, as in 1.21.11.
- **RI-07:** `BarTextureStore` decodes entries through its codec and both listeners use `FileToIdConverter`. Fabric's identifiable reload-listener wrappers are unchanged.
- **Orientations:** `orientation-build.md` describes the required, bundled API. Code and player orientations are updated within their limits.

Mechanical checks after the port:
- no reject files remain
- every project import resolves
- no references remain to removed classes
- every Forge/NeoForge registration has one wrapper method and one JSON instance (125 each)
- every shared check that a wrapper calls exists

Batch 4 checks when building:

- Common Protection API 1.0.0 is nested in the remapped Fabric production jar, and its `GameProfile` query signatures match `FabricEditAuthority`.
- `GridStackBER`'s generic `BlockEntityRendererProvider` factories register on all three loaders.
- `ClientPlayerNetworkEvent.LoggingIn` exists on NeoForge 21.8 and Forge 58.1.
- `/ssclienttest` passes on all three clients.
- The vegetation, snow, waterlogging, Bar-drop, and Adventure scenarios pass.

## Verify when building and testing

- `ResourceLocation.isValidNamespace`, `ByteBufCodecs.idMapper`, `Shapes.joinUnoptimized`, `FileToIdConverter`, and `Player.blockActionRestricted` exist in 1.21.8 with the signatures used.
- Forge 58.1's `handleUpdateTag(ValueInput, Provider)`/`onDataPacket(Connection, ValueInput, Provider)` and NeoForge 21.8's `handleUpdateTag(ValueInput)`/`onDataPacket(Connection, ValueInput)` match the subclass overrides. The current common hook pairs suggest these shapes.
- Fabric's client-update mixin target `BlockEntity.loadWithComponents(ValueInput)` matches 1.21.8.
- The `light` argument passed to 1.21.8 BERs equals the light the renderers now look up.
- Rejected client updates stay atomic on each loader's native path, and clients receive policy at login and after `/ss reload`.
