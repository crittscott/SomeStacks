# Port plan: Minecraft 1.21.11 → 26.1

## Decisions

- Target exactly Minecraft `26.1`. Each later 26.x version gets its own port.
- Fabric and NeoForge only. Forge is dropped: the `forge` module, its metadata, GameTest wrappers and test-instance JSON, and every Forge mention in docs are deleted, not disabled.
- 26.1 is unobfuscated: no mappings, no Parchment, no remapping.
- The 1.21.11 release (0.8.3) ships, so it is the immediately preceding release that world migration reads.
- Common Protection API has no 26.1 build, so the Fabric integration is deleted: the dependency, Nucleoid Maven, `-PwithCommonProtectionApi`, the metadata `suggests` entry, the integration code, and the provider-backed GameTests. Fabric placement and growth use vanilla checks; neighbor deposits consult Fabric block-use callbacks. It can return as new work when a 26.x build exists.

## Target versions

| Component | Version |
| --- | --- |
| Java toolchain and Gradle JVM | 25 (local: `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`) |
| Gradle | 9.5.1 (retain unless sync says otherwise) |
| Architectury Loom | `dev.architectury.loom-no-remap` 1.17.493 |
| Architectury plugin | 3.5.170 |
| Shadow | 9.4.3 |
| Minecraft | `26.1` |
| NeoForge | latest `26.1.0.x` (currently `26.1.0.19-beta`); range `[26.1.0.19-beta,26.1.1)` or similar |
| Fabric Loader | 0.19.5 (confirm minimum for 26.1) |
| Fabric API | latest `+26.1` (currently `0.145.1+26.1`) |

## Phase 1: Gradle sync

1. `settings.gradle`: drop the Forge Maven plugin repo; add NeoForge Maven to plugin repos if Loom needs it.
2. `gradle.properties`: `enabled_platforms=fabric,neoforge`; `minecraft_version=26.1`, `minecraft_version_range=[26.1]`; new NeoForge and Fabric API versions and ranges; remove `parchment_*`, `common_protection_api_version`, and all `forge_*` properties.
3. Root `build.gradle`: switch to `dev.architectury.loom-no-remap`; remove the `mappings` layer, the Parchment repository, and `silentMojangMappingsLicense()`; toolchain 25.
4. `fabric/build.gradle`: `modImplementation` → `implementation`; remove the Common Protection API dependencies, Nucleoid repository, `withCommonProtectionApi` property, and the GameTest entrypoint expansion that depends on it; replace the `remapJar` input wiring so the shadow jar is the production jar.
5. `neoforge/build.gradle`: same jar wiring change.
6. Verify the `namedElements` and `transformProduction*` common configurations still exist under no-remap; adjust names if sync reports otherwise.
7. Delete the `forge/` module.
8. IntelliJ: set the Gradle JVM to JDK 25; sync.
9. Update `build-env/` snapshot and `build-env.md`.

Done when IntelliJ sync succeeds.

## Phase 2: Metadata and packs

- `fabric.mod.json` (main and GameTest): Minecraft, loader, and Fabric API dependency versions; remove the Common Protection API `suggests` entry and the conditional GameTest entrypoints.
- `somestacks.mixins.json`: `compatibilityLevel` for Java 25.
- `neoforge.mods.toml` (main and GameTest): Minecraft and NeoForge ranges.
- `pack.mcmeta` in common and the NeoForge GameTest pack: 26.1 data/resource formats; update the resource-pack format mentioned in docs.

## Phase 3: Common code (~16k lines)

Compile and fix 26.1 API changes, in rough order of risk:

1. Client rendering: BERs, `ItemCapture`, `CubeRenderHelper`, render-state/submission, model measurement, bar textures.
2. Blocks, block entities, shapes, `ValueInput`/`ValueOutput` persistence.
3. Networking payloads and configuration sync.
4. Commands, config, sounds, tags, registry lookups.
5. Remove Forge-only code paths and comments from common (anything referencing `IItemHandler`, `SimpleChannel`, EventBus, etc.).

## Phase 4: Loader modules

- Fabric (~3k lines): Fabric API signatures now use Mojang names and some modules/classes were renamed; recheck the air-click mixin target; Transfer API storage; delete the Common Protection API integration so the Fabric edit authority uses vanilla checks and block-use callbacks only.
- NeoForge (~3k lines): `ResourceHandler<ItemResource>` transactions, events, registration, config, payload registrar.

## Phase 5: World migration

- `StackDataMigration` reads the 1.21.11 (0.8.3) format and upgrades items from its `DataVersion` via DataFixerUpper. Remove 1.21.8 as a supported source.
- Check whether the 1.21.11 → 26.1 block-entity or block-state format needs anything beyond item fixing.
- Keep the `SetAside` behavior for unreadable entries.
- Update migration GameTests and their fixtures to 1.21.11 inputs.

## Phase 6: GameTests

- Delete Forge wrappers and test-instance JSON; remove Forge from `configureGameTests` callers.
- Port the Fabric `@GameTest` entrypoints and NeoForge deferred functions/test-instance JSON.
- Delete `ProtectionApiGameTests`; fold the API-absence checks into the ordinary Fabric tests as plain vanilla-authority tests.
- Port the `/ssclienttest` smoke command.
- Regenerate or verify the empty structure fixture's format.

## Phase 7: Docs

- `orientation-code.md`, `orientation-player.md`: remove Forge and Common Protection API, update version and pack formats, migration source.
- `build-env.md`, `README.md`, `CHANGELOG.md`, `AGENTS.md`/`CLAUDE.md` mentions of Forge.
- Regenerate `docs/javadoc` (drop `forge` and `forge-gametest` trees) when you run `generateDocs`.

## Open items

- Whether Architectury Loom no-remap still provides `transformProduction*` configurations and `platformSetupLoomIde()` unchanged.
- Fabric Loader minimum for 26.1.
