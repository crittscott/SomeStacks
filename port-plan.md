# Some Stacks: Minecraft 1.21.6 to 1.21.8 port

The repository contains the 1.21.6 implementation for Fabric, Forge, and NeoForge. The copied baseline's compilation and runtime checks remain pending. Treat dependency resolution, source compilation, and runtime verification as separate milestones. Follow `CLAUDE.md`; builds and test launches are performed by the user unless explicitly delegated.

## 1. Get Gradle to sync for 1.21.8

- Update `gradle.properties`: Minecraft target and compatibility range, Forge and NeoForge dependencies and compatibility ranges, Fabric API, and the Parchment mapping coordinate.
- Verify published artifacts before selecting exact dependency pins. Do not assume Parchment's version matches Minecraft's.
- Initially retain Java 21 and the existing Gradle, Architectury Loom, Architectury plugin, and Shadow versions. Change tooling only where dependency resolution or sync requires it.
- Check IntelliJ's Gradle JVM and wrapper selection. Resolve configuration errors, including GameTest run definitions and Forge's EventBus validator.
- Keep the `build-env/` reference snapshot and `build-env.md` synchronized with active build changes.

Completion criterion: IntelliJ successfully imports `common`, `fabric`, `forge`, and `neoforge` with the 1.21.8 dependencies and run configurations. Successful sync does not establish that source compilation succeeds.

Status: complete. The user confirmed IntelliJ Gradle sync succeeds. Dependency pins and runtime ranges are updated, and the build snapshot and orientation documents are synchronized. `gradlew.bat help --console=plain` also succeeded under Java 21 for all four modules without changes to Gradle or its plugins. Published pins are Forge `1.21.8-58.1.0`, NeoForge `21.8.57`, Fabric API `0.136.1+1.21.8`, and Parchment `1.21.8:2025.09.14`. The shell's default `JAVA_HOME` points to Java 17; the configuration check used Java 21 only for that process, without changing machine settings. No production compilation or test launch was performed.

## 2. Port common production code

- Address actual API differences in block hooks, block-entity persistence, networking, and rendering.
- Inspect `ItemCapture`, `CubeRenderHelper`, and the three block-entity renderers closely because they interact directly with Minecraft's rendering internals.
- Keep inventory and movement logic unchanged unless an API change requires otherwise.

Completion criterion: common production code is adapted to the selected 1.21.8 APIs, with loader-specific differences kept in the loader modules.

Status: source/API review complete; no common production source changes are required by the reviewed Minecraft API differences. Compared Mojang's published plain-text [1.21.6 mappings](https://piston-data.mojang.com/v1/objects/848855615bc81e3db1c85e69b6afb150807a1261/client.txt) and [1.21.8 mappings](https://piston-data.mojang.com/v1/objects/bdeb624c3aefba11d9d40f34bc96176350b549b6/client.txt), covering 118 imported game classes and 316 mapping classes including nested types, then checked game-wide signature differences for indirectly referenced APIs. Reviewed block interaction, waterlogging, shape and removal hooks; value-based persistence and current-version item migration; payload codecs; item capture, quad projection, and all three block-entity renderers. The removed `ItemStackRenderState` model-identity members are unused; the added atlas method and music-disc item require no adaptation. Inventory, movement, serialization, and rendering code remain unchanged. This establishes no identified source adaptation is needed, not successful compilation or runtime behavior; those checks remain in phase 5. Loader-added block-entity update hooks remain subject to phase 3's loader API review. No game JARs or decompiled/remapped game or Forge sources were inspected, and no builds or tests were run.

## 3. Port loader adapters

- Check registration, interaction cancellation, networking, reload listeners, and automation on each loader.
- Check Forge's typed event buses, EventBus validator, and synthetic automation player.
- Check Forge and NeoForge placement snapshots, publication, and denied-edit rollback.
- Check Fabric's `startUseItem` mixin, Transfer API transactions, and optional Common Protection API integration.

Completion criterion: each loader's production adapters use the selected dependency APIs and retain the existing interaction, automation, and protection behavior.

Status: source/API review complete; no loader adapter changes were required by the reviewed 1.21.8 APIs. Reviewed registration and lifecycle/config wiring, client/server interaction cancellation, networking, renderer/reload registration, automation views, placement snapshots, rollback, and removal protection. Forge already uses EventBus 7 typed buses, boolean cancellation, `Result`, and `AttachCapabilitiesEvent.BlockEntities`; its synthetic automation actor uses the unchanged Minecraft player API. Forge's update hooks take `ValueInput` plus registry context, while NeoForge's take `ValueInput` alone; the common block entity already supplies both signatures. Fabric's `startUseItem` mixin target is unchanged, and its published API supports the existing payload registrations, callback phases, fake-player factory, and Transfer API snapshot/commit methods. The optional Common Protection API remains pinned to the same version and isolated behind its presence check. NeoForge still supports the existing client-handler registrar overload, so its receiver bridge remains unchanged. Compilation, event discovery, dedicated-server class loading, transaction behavior, and protection/rollback behavior remain unverified until phase 5; no builds or tests were run.

Review references: [Fabric API 0.136.1+1.21.8 Javadoc](https://maven.fabricmc.net/docs/fabric-api-0.136.1+1.21.8/), [Forge 1.21.8 published API source](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.21.8/src/main/java/net/minecraftforge/common/extensions/IForgeBlockEntity.java), [NeoForge 1.21.8 block-entity extension](https://raw.githubusercontent.com/neoforged/NeoForge/1.21.8/src/main/java/net/neoforged/neoforge/common/extensions/IBlockEntityExtension.java), [NeoForge payload registrar](https://raw.githubusercontent.com/neoforged/NeoForge/1.21.8/src/main/java/net/neoforged/neoforge/network/registration/PayloadRegistrar.java), and [NeoForge 21.8.57 release notes](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.8.57/neoforge-21.8.57-changelog.txt). Loader source references are the published 1.21.8 branches; source review does not substitute for compiling against the exact Maven artifacts. No game archives or decompiled/remapped game or Forge sources were inspected.

## 4. Update resources and saved-world verification

- Update resource-pack format from 63 to 64 and Forge/NeoForge GameTest data-pack format from 80 to 81, reflecting the [1.21.7 format changes](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-7).
- Review production and GameTest metadata for the target Minecraft and loader versions.
- Verify a 1.21.6 save loads and resaves correctly, preserving items, rotations, permanence, and set-aside entries. The existing migration code derives its target data version from the running game.
- Add or adapt focused GameTests where needed to cover migration from the preceding release.

Completion criterion: resources and metadata target 1.21.8, and saved-world migration preserves the preceding release's player data.

## 5. Compile and verify runtime behavior

- Have the user compile production sources for all three loaders and address reported failures.
- Compile GameTest sources separately; ordinary `build` excludes the GameTest support mods.
- Run the loader GameTest suites, including Fabric with and without Common Protection API.
- Verify dedicated-server startup, multiplayer synchronization, rendering and resource reloads, gestures, automation, and protection denial and rollback.
- Distinguish inherited baseline failures from changes introduced by the port when diagnosing failures.

Completion criterion: production and GameTest compilation succeed for all loaders, the test suites pass, and client/server smoke checks confirm the port's behavior.

## 6. Finish documentation and packaging

- Update the orientation snapshots and release documentation to describe the verified 1.21.8 implementation. Update snapshots as their described implementation changes, respecting their fixed headers and size limits.
- Verify each loader's packaged artifact, expanded metadata, and bundled common output.
- Regenerate committed Javadoc when the user runs the documentation task.
- Confirm the final build inputs match `build-env/` and the version declarations in `build-env.md`.

Completion criterion: loader artifacts and repository documentation consistently describe the verified 1.21.8 port.

## Expected scope and uncertainties

The code already uses the 1.21.6 serialization and rendering APIs, and [Minecraft 1.21.8's release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-8) describe a graphics hotfix. A relatively small source port is expected. Exact dependency compatibility and the copied baseline's unverified compilation and runtime behavior remain uncertainties until sync, compilation, and testing establish them.
