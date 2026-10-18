# Backport report: bringing 1.21.1 in line with 1.21.4

## Starting point

`../1.21.4` grew directly from this repo. Its history contains this branch's HEAD (`58aab63 Update changelog`), and everything since then is a 1.21.4 commit. The full delta can therefore be read with `git -C ../1.21.4 log 58aab63..HEAD` and diffed file by file. Those commits fall into two groups:

| Group | Commits | Bring back? |
| --- | --- | --- |
| Version ports | `4e443ba` (build retarget to 1.21.3), `45db548` (1.21.3 port + migration redesign), `c93aa8d` (build to 1.21.4), `684d8a2` (1.21.4 item-model pipeline) | Only the build template, the migration design, and possibly the protection-API switch. The Minecraft API changes stay out. |
| Architecture and behavior | `a8693ad` … `f7ea6da` (about 25 commits, 2026-10-03 to 10-10) | Yes, apart from the behavior changes listed under Decisions. |

1.21.1 is much closer to 1.21.4 than 1.20.1 was: both use Data Components, both have NeoForge, and both have the same networking payload model, Transfer API, capabilities, and NeoForge `TriState` events. **Recommendation: copy the 1.21.4 source trees and port them down to 1.21.1 APIs, rather than replaying the architecture onto the 1.21.1 code.** The downward API port is small and well-defined (see "1.21.1 API differences"). The architecture delta is large: about 60 files exist only here, about 70 only in 1.21.4, and nearly all shared files changed. The one thing that would weaken this recommendation is deciding to keep many 1.21.1 behaviors (see Decisions), because each kept behavior has to be put back into 1.21.4 code.

## Decisions needed first

1.21.4 contains deliberate behavior changes on top of the architecture. For 1.20.1 you kept most of the 1.21.1-era behaviors but adopted Forge TOML and native block-use gestures. Each row below needs a yes or no.

| 1.21.4 change | 1.20.1 choice | Note for 1.21.1 |
| --- | --- | --- |
| Gestures go through vanilla block-use plus a `GestureStatePkt`. The action packets (`DepositPkt`, `ExtractPkt`, `PlaceAndDepositPkt`, rotate/toggle packets, `RightClickBlockSuppressor`, `GestureThrottle`, `PlayerReach`) are removed and the server selects every cell. | Adopted | Architectural; adopt. |
| Forge/NeoForge policy moves to native SERVER TOML (`ForgeServerConfig`, `NeoForgeServerConfig`); Fabric keeps JSON (`JsonServerConfig`), reread by `/ss reload`. | Forge TOML adopted | Existing `<world>/serverconfig/somestacks-server.json` files on Forge/NeoForge would need migrating or would be abandoned (see Migration). |
| The configurable `compatibility.ingots` list and `/ss ingot` are replaced by the `somestacks:ingots` tag alone, which now also covers bricks. `ServerConfigMigration` logs leftover custom entries. | Kept patterns | 1.21.1 already ships `data/somestacks/tags/item/ingots.json`, so the switch is cheap. |
| Server render overrides (`server_item_overrides`, `ServerOverridesLoader`, `RenderOverridePkt`, `WriteOverridesPkt`) are removed; servers distribute profiles through a server resource pack. | Kept | Works on 1.21.1. |
| `/ss item` and `/ss write` become client-local and open to anyone (`ClientRenderCommands`, `ClientRenderCommandSyntax`, Fabric forwarding of server subcommands). | Kept server-side | Follows from removing server overrides. |
| Data-pack `somestacks_sounds/*.json` is replaced by registered `SoundEvent`s and `assets/somestacks/sounds.json` with subtitles. | Kept | Works on 1.21.1. |
| Block layout `Rotation` NBT becomes the `horizontal_facing` block state, so stacks rotate with structures. | Kept NBT | Migration converts the old `Rotation` value (1.21.4 already does this). |
| Fabric FTB Chunks support is replaced by Common Protection API, and NeoForge drops FTB Chunks. | Kept FTB/OPAC | FTB Chunks has a 1.21.1 build (2101.1.21), which is why 1.21.4 had to switch. I haven't checked whether `eu.pb4:common-protection-api:1.0.0` resolves on 1.21.1. |
| `disable_items` defaults to `evilcraft:broom_part`, and a crashing 2d model draws a `stack_cube_error` cube. | — | The error cube must be redone against 1.21.1's measurer (see below). |
| Galleries order mods and items by id. | — | Introduced in the 1.21.4 port commit; easy to carry. |

There is also a migration question: which version counts as "the immediately preceding released version" for this line? See Migration.

## Build environment

Take `new-build-env/build-env/` as the template, keeping the 1.21.1 version values.

| Item | Change |
| --- | --- |
| Plugins | Loom `1.17.491 → 1.17.493`, Architectury plugin `3.5.169 → 3.5.170`. Shadow 9.4.3 and Gradle 9.5.1 are unchanged. Drop the `eclipse`, `idea`, and `maven-publish` plugins. |
| `settings.gradle` | Include loaders from `enabled_platforms`; remove the NeoForged plugin repo (NeoForge maven moves into `neoforge/build.gradle`). |
| Root `build.gradle` | Replace `configureLoaderBuild` with `configureGameTests` (gametest source set over `common/src/gametest/java` and `resources`, a single shared fixture, `gametestJavadoc`). Add `sharedModProperties` and `generateDocs`. Declare JSR-305 once for every module. Move the common/shadowBundle configurations and the shadowJar/remapJar wiring into each loader script, and drop the custom Forge manifest. |
| `gradle.properties` | Restructure to match: add `mod_homepage`, `mod_license` as SPDX (`GPL-3.0-only`), and `enabled_platforms` near the top; remove `org.gradle.daemon=false`. Keep `minecraft_version=1.21.1`, Parchment `1.21.1 / 2024.11.17`, `forge_compile_version=1.21.1-52.1.16` with range `[52.0.0,53)` (the early-52.0.x jopt-simple GameTest trap still applies), and NeoForge `21.1.100`. Fabric API stays `0.102.0+1.21.1`. Replace `ftb_chunks_*` with `common_protection_api_version` only if the CPA decision is yes. |
| Fabric Loader | Currently `0.16.0`; 1.21.4 uses 0.19.5 and 1.20.1 moved to 0.19.3. Check whether Loom's current Mixin remapping path, which new Fabric scripts use without the legacy annotation processor, needs a newer loader before you lower or raise this. |
| Loader scripts | Each one calls `configureGameTests`, adds a `clientGameTests` run (for `/ssclienttest`), and expands metadata from `sharedModProperties`. Fabric gets the `withCommonProtectionApi` switch and gametest entrypoint expansion (or keeps FTB, per the decision). As in 1.20.1, use dedicated `run-gametest` / `run-client-gametest` directories instead of the template's deletion of `fabric/run/world`. |
| Fixtures | Merge the three loader `src/gametest/fixtures/somestacks_empty.nbt.b64` copies into `common/src/gametest/fixtures/`. The structure path `data/somestacks/structure/` is already correct for 1.21. |
| Metadata | Update `fabric.mod.json`, `mods.toml`, `neoforge.mods.toml`, and the gametest copies to use the shared placeholder names (homepage, sources URL, license). |
| Snapshot | Replace `build-env/` with the new contents, then delete `new-build-env/`. |
| IDE runs | Regenerate `.idea/runConfigurations` to pick up the client GameTest runs. |

## Project rules and docs

- Replace `CLAUDE.md` (v9) with v15 and add/replace `AGENTS.md`, `code-review.md`, and `gradlews.ps1` from `new-build-env/`. v15 adds the migration rule, "the mod acts as the player", one home per concept, lang-file text, the gametest-javadoc rule, and unwrapped Markdown.
- Replace `as-built.md` and `player-view.md` with `orientation-code.md` (≤ 12k characters) and `orientation-player.md` (≤ 15k characters), with the fixed headers. Write them against the finished 1.21.1 code, using the 1.21.4 files as the starting text.
- Rewrite `build-env.md` in the new form: snapshot table, build shape, exact versions, authorities.
- Commit generated Javadoc under `docs/javadoc/` through `generateDocs`, as 1.21.4 does.
- Update the CHANGELOG and README at the end.

## Source architecture to bring over

From 1.21.4 (most of it version-neutral):

- **Blocks and block entities:** `StackBlock`, `ShapedStackBlock` (occupied-content outline, targeting, and collision; full-block interaction shape; empty visual shape) and `StackBlockEntity` (persistence, nested batching, sync, light, fill contributions).
- **Runs:** `StackRun` and `StackRunItemAccess` own resolution, flat slots, headroom, publication, and comparators; `StoragePile`, `SinglesColumn`, and `BarColumn` keep only movement. Settlement sort keys are precomputed.
- **Automation:** `RunItemHandlerBase` in common plus one `RunItemHandler` per Forge and NeoForge replace `PileItemHandler`, `SinglesColumnHandler`, and `BarColumnHandler`. Fabric gets `FabricStorageOwner` + `StackBlockEntityMixin` for one cached adapter per block entity and a ledger shared across the run.
- **Platform seam:** `PlatformServices.Backend`, with `*PlatformServices` per loader, replaces `PlatformPaths`/`PlatformPathsImpl`, `ClientRenderPlatform`/`*RenderPlatform` (see the rendering caveat), `*CommandNetwork`, and `*PlayerEditAuthority`. `SomeStacksServer` provides the shared lifecycle.
- **Interaction:** `StackInteractions`, `ServerGestureState`, `RotationSoundThrottle`, the loader `StackInteractionEvents` / `FabricStackInteractionEvents`, and destination-protection consultation for neighbor deposits.
- **Edits:** `WorldEdits` with snapshot placement on Forge/NeoForge, `AutomationActor` (`[SomeStacks]` identity), player versus automation cleanup actors, and `BLOCK_PLACE`/`BLOCK_DESTROY`/`BLOCK_CHANGE` game events. The current Forge 1.21.1 authority already builds its own `GameProfile` actor, so it matches 1.21.4's approach.
- **Networking:** `ConfigSyncNetwork` (bounded, chunked, atomic config snapshot), `GestureStatePkt`, and Fabric's configuration-phase `ProtocolPkt` marker. `PacketBoundary` and its tests are removed.
- **Shared types:** the `renderconfig` package (`ItemRenderConfig`, `RenderMode`, `OverrideJsonCodec`, immutable `RenderOffset`), `CubeGrid`, `QuarterTurns`, and a consolidated `CommonRegistry` with typed lookups.
- **Config:** immutable `ServerConfig` policy plus a loader persistence backend (form per the TOML decision).
- **Logging:** SLF4J, milestone-only output, and migration accounting at shutdown.
- **Caching:** resolved profiles per item and Bar UV regions per sprite.

## 1.21.1 API differences (porting 1.21.4 code down)

The compiler has the final say. Revert these 1.21.2–1.21.4 changes:

| 1.21.4 form | 1.21.1 form | Where |
| --- | --- | --- |
| `Properties.setId(key)` | no id on properties | `FabricRegistry`, Forge/NeoForge `ModRegistry` |
| `new BlockEntityType<>(factory, Set.of(...))` | `BlockEntityType.Builder.of(...).build(null)` | Forge/NeoForge `ModRegistry` |
| `useItemOn` returns `InteractionResult` (`SUCCESS_SERVER`, `TRY_WITH_EMPTY_HAND`) | `ItemInteractionResult` (`PASS_TO_DEFAULT_BLOCK_INTERACTION` etc.) | `StackBlock` and the three block classes |
| `updateShape(state, LevelReader, ScheduledTickAccess, …, RandomSource)` | `updateShape(state, direction, neighborState, LevelAccessor, pos, neighborPos)` | `StackBlock` |
| `registry.getValue(id)`, `lookupOrThrow`, newer tag enumeration | `registry.get(id)`, `registryOrThrow`, 1.21.1 tag API | `CommonRegistry`, `AutoRenderProfiles`, `RenderGalleryGenerator`, `BarTextureStore` |
| `level.getMinY()` | `getMinBuildHeight()` | `ProtectionChecks`, loader `ProtectionGameTests` |
| `ItemCapture` over the 1.21.4 item-model resolver | keep 1.21.1's per-loader `ModelMeasurer` / `FabricModelMeasurer`, `ItemRenderer.getModel`, `BakedModel.getRenderPasses` | `CubeRenderHelper`, `ModelMeasurement`, `BarTextureStore`, loader client setup |
| `NativeImage` pixel access as of 1.21.4 | 1.21.1 `getPixelRGBA` (channel order differs) | `BarTextureStore` |
| current `DataVersion` | 3955 | `StackDataMigration` |

**Rendering caveat:** 1.21.4 removed the per-loader render platform because `ItemCapture` replaced it. On 1.21.1 a loader seam for model access is still needed. Fold it into the `PlatformServices` client side rather than keeping `ClientRenderPlatform` as a separate seam, and port the error cube and resolved-profile caching onto that path. Keep the measured-cache format consistent with the 1.21.1 measurer.

## Migration

v15 requires a dedicated migration layer for the immediately preceding release. Adopt 1.21.4's `StackDataMigration`: write `DataVersion` on every save, upgrade items through DataFixerUpper with `RegistryOps`, mark the block entity dirty, keep unreadable entries in `SetAside` with a retry on each load, and warn with dimension and position. Delete `LEGACY_ITEM_DATA_VERSION`, `isLegacyItemTag`, `upgradeLegacyItemTag`, and the `boolean` return of `StackItemStorage.deserializeNBT`.

**Owner question:** is the preceding release 1.20.1 (0.8.x) or the 1.21.1 0.8.2 release? Neither writes `DataVersion`, so absent `DataVersion` is ambiguous here. 1.21.4 could assume 3955. Here the migration must tell the two apart by shape (legacy `Count`/`tag` means 3465; `count`/`components` means 3955), as the current code does. Supporting both is easy if both are wanted.

Other migrations depend on the decisions:

- `Rotation` NBT → `horizontal_facing` block state (if adopted).
- 1.21.1 `compatibility.ingots` custom entries → log them, as `ServerConfigMigration` does (if adopted).
- Forge/NeoForge JSON policy → TOML. 1.21.4 did not convert this. If TOML is adopted here, either convert `somestacks-server.json` on first start or accept that it is abandoned and log it.

## GameTests

Move to the 1.21.4 layout: shared scenarios in `common/src/gametest` (`AutomationChecks`, `InteractionChecks`, `ProtectionChecks`, `SynchronizationChecks`, `ClientGestureChecks`, `ClientCallbackChecks`, plus the expanded persistence and policy checks) and a gametest `lang/en_us.json`. Loader holders: `InteractionGameTests` and `ClientCallbackGameTests` everywhere; on Fabric, `FabricTransferGameTests`, `StorageAndPersistenceGameTests`, and either `ProtectionApiGameTests`/`NoProtectionApiGameTests` (CPA) or kept FTB tests. Remove `PacketBoundaryChecks` and the loader `PacketBoundaryGameTests`. Add a migration test that loads a block entity tag with no `DataVersion` in each recognized legacy shape. Each gametest needs javadoc that tells a person how to reproduce it in-game.

## Suggested order

1. Settle the decisions and the migration question.
2. Replace the rules, docs scaffolding, and build environment; get Gradle to sync on 1.21.1.
3. Copy the 1.21.4 `common`, `fabric`, `forge`, and `neoforge` sources and resources. Port them down to 1.21.1 APIs and restore the 1.21.1 rendering seam. Put back any behaviors you decided to keep, using 1.20.1 as the reference for how they look on the new architecture.
4. Write the migration layer and its tests.
5. Bring the GameTests across and fix them to 1.21.1 behavior.
6. You build and run the three GameTest suites and the client callback smoke test.
7. Write the orientation docs, `build-env.md`, the `build-env/` snapshot, the CHANGELOG, and the README.
