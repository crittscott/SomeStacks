# Some Stacks: Minecraft 1.21.8 to 1.21.9 port

This repository is a copy of the 1.21.8 repository. References to 1.21.6 and earlier port work in the copied documents describe that repository's history. The task here is to port the copied implementation to 1.21.9 for Fabric, Forge, and NeoForge, as an intermediate step toward 1.21.11. Earlier milestones do not establish completion of any phase below.

Follow `CLAUDE.md`. The user runs Gradle sync, builds, and tests unless explicitly delegated. Do not decompile, unarchive, or inspect prohibited cached game or Forge sources. Use published API documentation for signatures that cannot be established from this repository. Treat dependency resolution, source compilation, and runtime verification as separate milestones.

## 1. Get Gradle to sync for 1.21.9

- Update `gradle.properties` with Minecraft 1.21.9, matching published Parchment mappings, Forge, NeoForge, and Fabric API coordinates, and corresponding runtime compatibility ranges. Verify published artifacts before selecting exact pins; do not assume Parchment's version matches Minecraft's.
- Check Fabric Loader and optional Common Protection API dependencies against the selected baseline.
- Retain Java 21 and initially retain the current Gradle wrapper, Architectury Loom, Architectury plugin, and Shadow versions. Change tooling only where target support or sync requires it.
- Verify Architectury Loom support for the selected loaders, particularly NeoForge's revised FML startup system. Recommendations for ModDevGradle, NeoGradle, or Fabric Loom are not direct substitutions for this build.
- Use the Gradle wrapper and Java 21 as IntelliJ's Gradle JVM. Resolve configuration and dependency errors across all four modules, including GameTest run definitions, common transforms, and Forge's EventBus validator.
- Synchronize every changed build input into `build-env/` and update `build-env.md`.

Completion criterion: IntelliJ successfully reloads the complete Gradle project with `common`, `fabric`, `forge`, and `neoforge` present and targeting 1.21.9. A configuration-only `gradlew.bat help --console=plain` check is available for the user if useful. Successful sync does not establish successful source compilation.

Status: complete. The user confirmed IntelliJ Gradle sync succeeds for the 1.21.9 configuration. Pins are Forge `1.21.9-59.0.5`, NeoForge `21.9.16-beta`, Fabric API `0.134.1+1.21.9`, and Parchment `1.21.9:2025.10.05`. Java 21, Fabric Loader `0.19.5`, Common Protection API `1.0.0`, and the existing Gradle/plugins are retained. The build snapshot matches active inputs. Source compilation and runtime verification belong to later phases.

## 2. Port shared block entity rendering

- Convert `StorageStackBER`, `SinglesStackBER`, and `BarStackBER` to render-state extraction and queued submission. Extract item render states, rotations, lighting, and geometry needed for drawing; submission should consume prepared state without reading live inventories or the level.
- Port `CubeRenderHelper`, `ItemCapture`, model measurement, and Bar tint derivation together with the renderers. Their immediate rendering, intercepted vertex output, and baked-quad access require review beyond changing renderer signatures.
- Adapt ordinary item and block submission first, then flat-art capture and projection, automatic measurement, and custom Bar geometry. Cover all four display modes: `2d`, `3d`, `gui`, and `block`.
- Review camera-relative face selection, lighting, transforms, tinting, reload invalidation, and captured geometry lifetime. Deferred submissions must not retain mutable scratch data that another capture overwrites.
- Keep rendering common where APIs are common; isolate differences only where a loader requires them. Remove obsolete rendering paths.

Completion criterion: the renderers and capture/measurement pipeline use the target APIs with extraction and submission responsibilities separated. Visual correctness remains subject to phase 6.

Reference: [NeoForge 1.21.9 rendering migration](https://neoforged.net/news/21.9release/).

Status: implementation complete; compilation and visual verification pending. The three BERs now extract prepared state and submit native item/block features or captured custom geometry. Grid submission reads no live inventory or level; camera-relative visibility is computed in block-local space. Item capture records submitted quads/tints and uses vanilla model extents rather than intercepting an immediate draw. Capture results own their data, and matching grid contents share prepared presentation within one extraction. Bar textures/UVs/tints are resolved before submission. Measurement cache format is 4. Block mode uses native deferred block submission; the old synchronous per-cell draw-exception fallback is removed, while non-model block forms still use FIXED item presentation. `/ssclienttest` now covers capture isolation, special features, GUI extents, and grid submission after inventory mutation and state reuse. Published Fabric API Javadoc and NeoForge rendering documentation guided the port; no Gradle invocation, build, or test was run.

## 3. Replace NeoForge's automation adapter

- Replace NeoForge's `IItemHandler` implementation and capability registration with `ResourceHandler<ItemResource>` and the corresponding capability for the selected baseline.
- Implement native transaction staging and rollback for whole-run access, including insertion into headroom, growth, positional extraction, and deferred publication. Aborted transactions must not leave inventory or world edits behind.
- Use the existing Fabric adapter's staging approach as a reference for shared run state, outer commit, rollback, and structural extraction restrictions. Keep loader-native transaction types in their own modules; share mechanics only where appropriate.
- Keep Forge's adapter on Forge's own API. Update NeoForge capability tests to exercise its replacement API.

Completion criterion: NeoForge exposes the target capability with correct commit/rollback behavior, whole-run visibility, growth, and protection checks. No obsolete NeoForge adapter remains.

Reference: [NeoForge transfer migration](https://neoforged.net/news/21.9-transfer-rework/).

## 4. Update loader and client integration

- Replace string key categories in all three loaders with the target category representation and registration, updating language keys where needed.
- Update `FMLEnvironment.dist` in `SomeStacksNeoForge` to its replacement accessor and review other affected FML integration.
- Adapt Fabric resource-listener registration and ordering for render overrides, Bar textures, measurement, and cache invalidation.
- Check input callbacks and Fabric's `startUseItem` mixin target against the target APIs.
- Review registration, networking, config lifecycle, interaction cancellation, protection events, placement snapshots, accepted publication, and denied-edit rollback against the selected loader versions. Change what the target APIs require.
- Verify optional Common Protection API integration remains isolated behind its presence check and uses the selected API correctly.

Completion criterion: each loader's production integration uses its selected APIs, including client registration and dedicated-server-safe initialization.

References: [Fabric 1.21.9 porting notes](https://fabricmc.net/2025/09/23/1219.html) and [NeoForge 1.21.9 release notes](https://neoforged.net/news/21.9release/).

## 5. Review shared gameplay, persistence, and resources

- Check block interaction, removal, waterlogging, collision and selection shapes, item serialization, and block-entity updates against 1.21.9. Adapt inventory and movement logic where required by the port.
- Retain the existing `ValueInput`/`ValueOutput` and runtime `DataVersion` approach where supported. A target-version change alone does not justify a new saved-data format.
- Support worlds from the immediately preceding released mod version through the dedicated migration layer. Preserve unreadable entries, item components, rotations, permanence, and set-aside data; write only the current format after migration.
- Update resource and GameTest pack metadata from their checked-in formats to the target formats, verifying metadata schema changes. Review production and GameTest manifests and version expansion.
- Convert repository resources in place if target formats require changes.

Completion criterion: gameplay and persistence use the target APIs, resource declarations target 1.21.9, and focused migration checks cover the preceding released mod version.

## 6. Port GameTests and verify compilation and runtime behavior

- Have the user compile production sources across all three loaders and address reported failures.
- Adapt and compile GameTest source sets separately; ordinary `build` excludes the GameTest support mods. Check registration, discovery, test-instance JSON, and run configuration.
- Have the user run loader GameTest suites, including Fabric both with and without Common Protection API.
- Verify dedicated-server startup and multiplayer synchronization on each loader.
- Exercise all four display modes, Bar geometry and tinting, automatic measurement, render-profile commands, reloads, gestures, and key rebinding.
- Verify protected edits, atomic placement and first deposit, automation commit/rollback, growth, settlement, gravity, backfill, collapse, and denied-edit restoration.
- Load and resave a world from the preceding released mod version; confirm saved contents and migration set-aside entries survive.
- Distinguish inherited baseline failures from failures introduced by this port when diagnosing results.

Completion criterion: production and GameTest compilation succeed for each loader, suites pass, and client/server smoke checks confirm the port's behavior.

## 7. Finish documentation and packaging

- Update orientation snapshots as implementation changes, preserving fixed headers and size limits. They describe the implementation rather than prescribing behavior.
- Record verification only after the user's results establish it. Update release documentation for the completed 1.21.9 port.
- Verify each loader's packaged artifact, expanded metadata, bundled common output, and absence of GameTest support mods from production packaging.
- Regenerate committed Javadoc when the user runs the documentation task.
- Confirm active build inputs match `build-env/` and version declarations in `build-env.md`.

Completion criterion: artifacts and repository documentation consistently describe the verified 1.21.9 implementation.

## Expected scope and uncertainties

The largest confirmed source changes are rendering and NeoForge's transfer API. Key categories, Fabric resource loading, and NeoForge FML access also have documented changes affecting this code. Exact dependency pins, Architectury Loom support for the selected loaders, remaining adaptations, and runtime correctness must be established during the corresponding phases. This plan does not assume that 1.21.11 APIs apply to 1.21.9.
