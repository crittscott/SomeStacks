# Some Stacks Build Environment

This records the toolchain and dependency versions used by this repository, based on the active
Gradle configuration, this machine's installed JDKs and Gradle cache, and the current IntelliJ
project settings. See [as-built.md](as-built.md) for the runtime architecture; this file covers the
environment that builds and launches it.

The `build-env/` directory is a synchronized reference snapshot of this repository's build scripts
and wrapper. It is not part of the active Gradle build: the root `settings.gradle` includes
`common`, `forge`, `fabric`, and `neoforge`, while nothing includes `build-env/`. Make build changes
in the active root files first, verify them there, and then refresh the matching files under
`build-env/` so the snapshot does not drift.

All four modules — `common`, `fabric`, `forge`, and `neoforge` — are ported to Minecraft 1.21.1 and
compile. `forge` keeps the classic Forge API; `neoforge` is the parallel port on the NeoForge
equivalents and carries a full loader source tree, not a skeleton. Each loader also builds a
development-only `somestacks_gametest` suite.

## Toolchain

| Tool | Active declaration |
| --- | --- |
| Gradle | Regenerated wrapper pinned at **9.5.1**, with distribution-URL validation enabled |
| Java toolchain | Language version 21; source/target 21; `--release 21` |
| IntelliJ Gradle JVM | `.idea/gradle.xml` sets `gradleJvm="21"` |
| Architectury Loom | `1.17.491` |
| Architectury Plugin | `3.5.169` |
| Shadow (`com.gradleup.shadow`) | `9.4.3` |

The project toolchain forces compilation and every Gradle `JavaExec` task, including Minecraft
development runs, onto Java 21, so a JDK 21 must be discoverable. The wrapper itself may launch on a
Gradle-compatible JVM: on this machine `JAVA_HOME` points to JDK 17 and Gradle discovers the JDK 21
toolchain separately. IntelliJ runs Gradle with its configured JDK 21. There is no global `gradle`
command; the wrapper is the build entry point.

`gradle.properties` gives Gradle 3 GiB with `org.gradle.jvmargs=-Xmx3G`, disables the persistent
daemon with `org.gradle.daemon=false`, and enables parallel execution. Loom and the Architectury
Plugin are pinned to numbered versions rather than moving snapshot aliases.

## Minecraft and library versions

| Component | Build version | Declared runtime constraint |
| --- | --- | --- |
| Minecraft | **1.21.1** | exactly `[1.21.1]` on Forge and NeoForge; exactly `1.21.1` on Fabric |
| Mappings | Mojang official plus Parchment **2024.11.17-1.21.1** | development only |
| Forge | **1.21.1-52.1.16** | Forge `[52.0.0,53)`; FML `[52,53)` |
| NeoForge | **21.1.100** | NeoForge `[21.1.100,22)`; JavaFML `[1,)` |
| Fabric Loader | **0.16.0** | `>=0.16.0` |
| Fabric API | **0.102.0+1.21.1** | `>=0.102.0+1.21.1` |
| FTB Chunks (Fabric/NeoForge) | **2101.1.21** | `[2101,2102)`, optional compile-only |
| JSR-305 | **3.0.2** | compile-only annotation dependency |

There is no JUnit suite; all automated testing is the per-loader GameTest suites.
`neoforge_compile_version` is both the compile dependency and the minimum accepted runtime.
`forge_compile_version` stays at the newest 52.x because early 52.0.x userdev omits jopt-simple
from the dev module path and breaks `runGameTestServer`; the mod only uses classic Forge API from
52.0.0, so `forge_version_range` declares the wider `[52.0.0,53)`. Architectury is a build-time
dependency only: the
Architectury Plugin and Loom supply `@ExpectPlatform` / `@Environment` transformation, and no loader
carries an Architectury API runtime dependency. FTB Chunks is the only optional third-party mod
integration and is never bundled. Open Parties and Claims support was removed with the 1.21.1 port
(no 1.21.1 build exists).

Plugin resolution uses the Fabric, Architectury, Forge, and NeoForged Maven repositories plus the
Gradle Plugin Portal. All subprojects additionally use the Architectury, NeoForged, and Parchment
repositories; the FTB repository is limited to Fabric and NeoForge, the two modules that declare
FTB Chunks. All active version pins are in the root build scripts and `gradle.properties`
(`maven_group`, `archives_name`, `forge_loader_version_range`, `neoforge_*` among them); there is no
`buildSrc`, version catalog, or dependency locking.

## Module and packaging setup

The active build has four subprojects:

```text
common/     shared source and resources; transformed into each loader artifact
forge/      Forge entry points and integration
fabric/     Fabric entry points and integration
neoforge/   NeoForge entry points and integration
```

Each loader module compiles against `common` through Architectury's `common` configuration and
bundles its transformed production output through `shadowBundle`. The root
`configureLoaderBuild` helper owns those configurations, GameTest structure generation, generated
resource registration, Shadow classification, and remap input wiring. Development runs group the
common and loader source sets into one logical mod under `loom.mods.main`. The production artifacts
are:

```text
forge/build/libs/somestacks-forge-<version>.jar
fabric/build/libs/somestacks-fabric-<version>.jar
neoforge/build/libs/somestacks-neoforge-<version>.jar
```

Common's only Architectury API usage, the config-directory lookup, resolves through the
`@ExpectPlatform` helper `com.github.crittscott.somestacks.PlatformPaths`, with a per-loader
`PlatformPathsImpl`.

The common `pack.mcmeta` declares resource-pack format 34 and a supported range of 34-48 so the same
built-in pack is accepted as Minecraft 1.21.1 resource content (34) and data content (48).

The `*-dev-shadow.jar` and `*-sources.jar` files in those directories are development artifacts,
not release JARs. Common's transformed JARs are intermediate inputs to the loader builds.

## Current IntelliJ run configurations

`.idea/runConfigurations/` currently contains nine Architectury-generated application runs — a
client, a server, and a Game Test Server for each of `:forge`, `:fabric`, and `:neoforge`.

All nine launch through `dev.architectury.transformer.TransformerRuntime`. Forge and NeoForge use
`cpw.mods.bootstraplauncher.BootstrapLauncher`; Fabric uses Knot. There are no plain Fabric Loom
runs or data-generation run in the current project.

Each loader's GameTest run makes `<loader>/src/gametest` a separate development-only
`somestacks_gametest` mod and adds `common/src/gametest/java` as an extra source directory. Forge
and NeoForge enable their GameTest namespace through
`forge.enabledGameTestNamespaces` / `neoforge.enabledGameTestNamespaces`; Fabric passes
`-Dfabric-api.gametest`. Each loader keeps its own checked-in Base64 fixture at
`<loader>/src/gametest/fixtures/somestacks_empty.nbt.b64`, which `generateGameTestStructures` decodes
into the build directory before GameTest resources are processed.

Fabric's empty-hand air-click hook is a Fabric-only Mixin declared by `somestacks.mixins.json`, so
only Fabric carries that Mixin configuration and refmap handling. All three GameTest source sets
explicitly include `project(':common').sourceSets.main.output` on their runtime classpath; the full
suites pass with that uniform setup.

## Environment traps

- **Compile-only dependencies from `common` do not automatically reach loader compilation.**
  Architectury's `common` and `shadowBundle` configurations carry common output, not all of its
  dependency declarations. Fabric therefore redeclares JSR-305 for `javax.annotation.Nullable`;
  Forge and NeoForge receive it through their dependency graphs.
- **Development must expose common and loader output as one logical mod.** Each loader's
  `loom.mods.main` includes both source sets. Adding common as a separate runtime mod can produce
  duplicate loading or Forge JPMS split-package failures; production bundling belongs in
  `shadowBundle`.
- **A loader's `gametest` source set needs main output on both classpaths.** Main's dependency
  classpath alone does not contain the mod's own compiled classes. Each loader also names common
  main output explicitly on its GameTest runtime classpath.
- **Forge scans GameTests only from registered Loom mod output.** The `somestacks_gametest`
  `loom.mods` entry, its `mods.toml`, stub `@Mod` class, and `pack.mcmeta` are all part of making the
  custom source set visible to FML and making its structure resource load. A launch that discovers
  zero tests can otherwise look like a successful run.
- **The generated GameTest NBT is not a source file.** Edit the Base64 fixture under
  `<loader>/src/gametest/fixtures`; the decoded file under `<loader>/build/generated` is disposable
  build output.
- **The loaders register GameTest classes differently.** Forge and NeoForge discover test methods by
  scanning the loaded mod for classes annotated `@GameTestHolder`; NeoForge holders additionally need
  `@PrefixGameTestTemplate(false)` and a bare fixture path so the id is not prefixed with the holder
  namespace and class name. Fabric instead requires each class to implement `FabricGameTest` and to
  be listed under a `fabric-gametest` entrypoint in the dev-mod's own `fabric.mod.json`; a new Fabric
  GameTest class not added to that entrypoint list will not run.
- **`build-env/` is an inactive reference snapshot.** Changing files there does not change the root
  build. Change and verify the active files first, then synchronize the snapshot.
- **Dropbox can briefly lock freshly written JARs on Windows.** An aggregate `build` may fail while
  replacing a loader's final JAR even though configuration and compilation succeeded. Retrying the
  affected `:<loader>:build` after the sync/indexing lock clears has succeeded consistently.

## Build and test commands

Use the wrapper from the repository root in PowerShell:

```powershell
.\gradlew build
.\gradlew :fabric:build
.\gradlew :fabric:runGameTestServer
```

`.\gradlew build` compiles every subproject and produces all three loader artifacts;
`.\gradlew :<loader>:build` builds one. There is no JUnit suite and no `test` task — all automated
testing is the per-loader GameTest suites, each run through its own `:<loader>:runGameTestServer`,
none of which is part of `build`.
