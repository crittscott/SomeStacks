# Review fixes ported to 1.21.11

This records how the review findings verified and fixed in `26.3` (see `../26.3/check-these.md` for each finding's full text, verification, and disposition) were ported to this project. IDs match that file. Only differences from the 26.3 disposition are spelled out; every other finding was ported as 26.3 implemented it.

## Method and validation

The four 26.3 review commits (batches 1–4) were exported as diffs and applied here, with rejected hunks merged by hand against the 1.21.11 code. Forge-side counterparts were written by hand, since 26.3 has no Forge module. No builds, GameTests, client runs, or Git operations were performed. Compilation, server GameTests on all three loaders, and `/ssclienttest` on all three development clients are still required.

Each test function registered on Forge and NeoForge was checked against exactly one test-instance JSON file and one wrapper method (Forge 125, NeoForge 134). Every shared check that a loader wrapper calls was checked to exist.

## Decisions specific to 1.21.11

- **PR-01/PR-02:** Common Protection API is now required and bundled on Fabric, using the already-pinned 1.0.0 (`modImplementation(include(...))`, non-transitive; the remapped production jar nests it). The queries use the 1.0.0 `GameProfile` signatures, not 2.0.0's `NameAndId`. The `-PwithCommonProtectionApi` switch, `CommonProtectionQueries`, `FabricProtectionQueries`, and `NoProtectionApiGameTests` are gone; `ProtectionApiGameTests` is always an entrypoint and its test provider also answers `canBreakBlock`. Tests that expected synthetic `UseBlockCallback` consultation now assert that none fires.
- **MN-01:** Storage slots cap at min(64, item maximum). Migration from 1.21.8 saves splits over-cap entries into free slots, then `SetAside`.
- **NW-01:** not ported. NeoForge 21.11 SERVER-config live-reload sync is not established, so all three loaders keep one bounded `ConfigSyncPkt` snapshot. NW-02, NW-04, and NW-05 are ported. NW-03: NeoForge keeps its static client receiver hook, now installed with `ClientServerSettings::apply`; per the 26.3 verification, no early-delivery defect was established.
- **ML-08:** not applicable. Forge is still a supported module here.

## Forge counterparts

- **DC-14:** Forge sound holders removed; registrations retained.
- **EW-02 / OR-01:** `ForgeClientEvents` uses `ClientGestures.processClick` and `StackGesture.consumesLocalUse`; the `client/interaction` rule package is deleted.
- **EW-05 / LD-03 / MN-07:** `ForgeServerConfig` takes section/key names and defaults from `ServerConfig`, and its Loading and Reloading events share `onConfigChanged`.
- **LG-02 / LG-05:** local storage lost `simulate`. Forge's `RunItemHandler` keeps `IItemHandler` simulation at the run level and uses `localSlotLimit`/`isStoredItemValid`.
- **LD-02:** `ForgeStackBlockEntities` subclasses override Forge's `handleUpdateTag(ValueInput, Provider)` and `onDataPacket(Connection, ValueInput, Provider)`, validate first, then call `loadWithComponents`; `ForgeRegistry` registers them. Forge's `/ssclienttest` wrapper passes its native entry points.
- **LD-06:** renamed to `ForgeRegistry`, `ForgeNetworking`, `ForgeClientEvents`, `ForgeClientSetup`, `ForgeKeyMappings`, and `ForgeStackInteractionEvents`. `ForgePlatformServices` moved to the root package, the entrypoint is now `SomeStacksForge`, and the `MODID` alias was removed.
- **LD-07:** Forge persistence tests and test-instance JSON are renamed to `automationandpersistencegametests`.
- **LG-07 / OR-08:** Forge's packet handler applies `ClientServerSettings`. `ForgeClientSetup` uses the `SomeStacksClient` join, disconnect, and stopping operations.
- **ML-02:** Forge's cleanup, refused-removal, and growth-protection scenarios delegate to `ProtectionChecks`, through `observeRemoval` and a new `GameTestSupport.offerAt`. The refused Bar removal registration and JSON moved to `protectiongametests`.
- **MN-01 / NW-02 tests:** Forge registers the two new Storage cap and migration scenarios, and drops the three obsolete chunk/budget scenarios and their JSON.
- **OR-14:** Forge registers `GridStackBER::storage`/`::singles`.
- **PR-05 / RI-01 / RI-02:** the common `WorldEdits` game-mode gate, item-entity Bar drops, and `BlockPlaceContext` placement apply on Forge. New Forge wrappers and JSON cover vegetation replacement and drops with block drops disabled. The Adventure cleanup scenario runs only on NeoForge and Fabric: Forge's connectionless test player cannot change game mode, because `setGameMode` broadcasts a player-info update that reads `player.connection`.
- **RI-02 test fixture:** the 1.21.11-only `playerPlacementAndGrowthAwardPlacedBlock` check now seeds a solid support. Vanilla placement context lets a click on replaceable air replace that air, so the old fixture placed one block too low.
- **RI-05:** Forge placement already completes through `WorldEdits.completePlacement` via `placeChecked`. Forge has no tentative growth placement, so PR-04 and ML-01 are NeoForge/Fabric only.

## API adaptations from 26.3 code

- Imports and calls were adapted to the 1.21.11 APIs: `BlockEvent.BreakEvent` (NeoForge 21.11), `net.minecraft.advancements.CriteriaTriggers`, `net.minecraft.client.renderer.state.CameraRenderState`, `pose.mulPose(Axis…rotationDegrees)`, and `GridStackBER` without 26.3's `BlockModelResolver`.
- Light: Bar and grid preparation now read `BlockEntityRenderState.lightCoords` instead of a separate `LevelRenderer.getLightColor` lookup.
- Protocol version 6; this replaces 1.21.11's former 5.

## Verify when building and testing

- `BlockEntityRenderState.lightCoords` is the field name in 1.21.11.
- Forge 61.1.5's `IForgeBlockEntity` hook signatures match the `ForgeStackBlockEntities` overrides, and NeoForge 21.11's `handleUpdateTag(ValueInput)`/`onDataPacket(Connection, ValueInput)` match `NeoForgeStackBlockEntities`.
- `Identifier.isValidNamespace`, `ByteBufCodecs.idMapper`, `Shapes.joinUnoptimized`, `FileToIdConverter`, and `Player.blockActionRestricted` exist with the signatures used.
- The `gamerule minecraft:block_drops` command in `BarColumnChecks.contentsDropWithBlockDropsDisabled` matches 1.21.11's game-rule id.
- Common Protection API 1.0.0 provides `canBreakBlock(Level, BlockPos, GameProfile, Player)`, and Loom's `include` nests it into the remapped production jar.
- Rejected client updates remain atomic on each loader's native path (`/ssclienttest`), and dedicated plus integrated clients receive policy at login and after `/ss reload`.

## Not ported, as in 26.3

EF-04 and EW-06 (deferred); EW-08 (unconfirmed); GD-04, GD-06, GD-07, ML-03, OR-11's sanitation deletion, OR-12, OR-16, RI-08, MN-10's float-helper substitution; and the qualified or rejected parts of DC-05, DC-07, DC-12, DC-14, DC-16, and EW-09.
