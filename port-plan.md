# Port plan: Minecraft 1.21.9 → 1.21.11

Claude edits code and build files only. Claude does not run Gradle tasks (sync, build, runs, GameTests); the user runs all of them. Each stage ends with a **Checkpoint** naming what the user should run and what should succeed at that point.

## Stage 1 — Gradle sync

Version bumps only; the build structure (Architectury Loom, Shadow, GameTest wiring) carries over unchanged.

| Property / pin | 1.21.9 | 1.21.11 |
| --- | --- | --- |
| `minecraft_version` / `minecraft_version_range` | `1.21.9` / `[1.21.9]` | `1.21.11` / `[1.21.11]` |
| `parchment_minecraft` / `parchment_version` | `1.21.9` / `2025.10.05` | `1.21.11` / `2025.12.20` |
| `forge_compile_version` | `1.21.9-59.0.5` | `1.21.11-61.2.4` |
| `forge_version_range` / `forge_loader_version_range` | `[59.0.5,60)` / `[59,60)` | `[61.2.4,62)` / `[61,62)` |
| `neoforge_compile_version` / `neoforge_version_range` | `21.9.16-beta` / `[21.9.16-beta,21.10)` | `21.11.48` / `[21.11.48,21.12)` |
| `fabric_api_version` | `0.134.1+1.21.9` | `0.141.6+1.21.11` |
| `fabric_loader_version` | `0.19.5` | keep; move to `0.20.0` only if Fabric API requires it |
| EventBus validator (`forge/build.gradle`) | `7.0-beta.10` | the EventBus 7.0.x release Forge 61 uses (stable `7.0.6` is published) |
| Loom `1.17.493`, Architectury plugin `3.5.170`, Shadow `9.4.3`, Gradle `9.5.1` | — | keep (already latest) |
| Common Protection API | `1.0.0` | keep for now; `2.0.0` exists but its Minecraft target is unconfirmed. Compile-only and non-transitive, so it does not block sync |

Also update the `build-env/` snapshot and `build-env.md` to match.

Likely sync failures: Loom support for Forge 61 / NeoForge 21.11 userdev, or Parchment artifact resolution.

**Checkpoint:** IntelliJ Gradle sync succeeds. Do not build yet; source will not compile.

Status: complete. The user confirmed IntelliJ Gradle sync succeeds. Forge's EventBus validator is pinned to `7.0.6`.

## Stage 2 — Compile `common`

- `ResourceLocation` → `Identifier` (Mojang's 1.21.11 rename): mechanical rename across all modules (~26 files).
- Permission system rework: `hasPermission(int)` and permission-level handling in `SsCommand` and the gallery permission config move to the new permission API.
- Other renames/moves the compiler reports (e.g. `Util`).
- Rendering: render-state/submission pipeline, `ItemCapture`, measurement, and bar cuboids; expected to be smaller than the 1.21.9 changes.
- Pack metadata: data-pack format 88 and resource-pack format 69 move to the 1.21.11 values in the common pack and the Forge/NeoForge test packs.

**Checkpoint:** `:common:compileJava` succeeds. Loader modules are still expected to fail.

Status: edits complete, awaiting user compile. `ResourceLocation`/`ResourceLocationArgument`/`ResourceKey#location` renamed in all modules. Admin commands check `Commands.LEVEL_GAMEMASTERS` against `source.permissions()`; the gallery's 0-4 config level indexes vanilla's five `Commands.LEVEL_*` checks in `ServerConfig` (Forge/NeoForge config specs use its constants). The Storage/Singles background and Bar cuboids use `RenderTypes.solidMovingBlock()`; 2-D plates are submitted with the render type the item model used (item atlas), reading `BakedQuad#position`/`packedUV`. Packs declare `[94, 1]`. GameTests: `Cow` package move and `LevelBasedPermissionSet` sources. APIs were checked against the 1.21.11 primer and NeoForge's 1.21.11 branch.

## Stage 3 — Loader modules

- Fabric: Fabric API 0.134 → 0.141 changes (rendering hooks, `ResourceLoader`, Transfer API, networking), air-click mixin target, fake-player factory.
- Forge 59 → 61: EventBus 7 event names, capability attachment, `SimpleChannel`, `mods.toml` ranges.
- NeoForge 21.9 → 21.11: `ResourceHandler` transfer API, payload registrar, fake players, config spec.
- Key-mapping categories (13 files reference `KeyMapping`).
- GameTest sources compile on each loader.

**Checkpoint:** full `build` succeeds on all three loaders, plus `compileGametestJava` for each loader.

Status: complete. All three loaders build, server GameTests pass, and Javadoc generates. Forge's conflict-context `KeyMapping` constructor now takes an `InputConstants.Key` and a sort order. Forge is pinned to `61.1.5` (range `[61.1.5,62)`): from 61.1.6 (ForgeDev 7) Architectury Loom 1.17's dev runtime crashes in vanilla bootstrap (`NoSuchMethodError` on `ByteBufCodecs$11.<init>`); revisit when Loom supports the new userdev. Stage 2 already renamed `Identifier` in loader modules and aligned both TOML configs with `ServerConfig`'s permission constants. Fabric API 0.141.6 (Fabric's 1.21.11 branch) keeps every class and signature used here (`FabricBlockEntityTypeBuilder`, `FakePlayer.get`, `ResourceLoader.registerReloader`, `PlayerBlockBreakEvents`, `KeyBindingHelper`, Transfer API, payload registry) and needs loader ≥ 0.17.3, so `0.19.5` stays. NeoForge 21.11 keeps `ResourceHandler`/`SnapshotJournal`/`TransferPreconditions`, `Capabilities.Item.BLOCK`, `FakePlayerFactory`, `BlockSnapshot`, `EventHooks.onBlockPlace`, Level snapshot fields, `RightClickBlock`/`BreakEvent` constructors, `PayloadRegistrar`, `RegisterKeyMappingsEvent.registerCategory`, `AddClientReloadListenersEvent`, and `ModConfigSpec.defineListAllowEmpty`. Forge's 60/61 changelogs list no break in the APIs used (the `AttachCapabilitiesEvent` generic removal does not affect the `BlockEntities` sub-event). The 1.21.11 primer's GameTestHelper and Player changes touch no call in the loader GameTests. Common Protection API stays `modCompileOnly`, so Loom remaps its signatures to `Identifier`. Risks the published notes cannot settle: Forge's `DistExecutor` and `Mod.EventBusSubscriber.Bus`, both long deprecated.

## Stage 4 — Migration and data

- `StackDataMigration`: the preceding release becomes 1.21.9; update its DataFixerUpper source version and the comments describing the format it reads.
- Test-instance JSON and the GameTest structure fixture: check for format changes; convert with a script if needed.

**Checkpoint:** `build` again, then `runGameTestServer` on all three loaders.

## Stage 5 — Runtime verification

All run by the user:

1. GameTests pass on all loaders (including Fabric with `-PwithCommonProtectionApi=true`).
2. `runClientGameTests` and `/ssclienttest`.
3. Load a world saved by the 1.21.9 build on each loader.

Then update `orientation-code.md`, `orientation-player.md`, and `build-env.md` to describe the 1.21.11 state.
