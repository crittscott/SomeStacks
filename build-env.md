# Some Stacks Build Environment

This records the toolchain and dependency versions used by this repository, based on the active
Gradle configuration, this machine's installed JDKs and Gradle cache, and the current IntelliJ
project settings. See [as-built.md](as-built.md) for the runtime architecture; this file covers the
environment that builds and launches it.

The `working-build-env/` directory is not part of the active Gradle build. The root
`settings.gradle` includes `common`, `forge`, `fabric`, and `neoforge`; declarations under
`working-build-env/` are therefore not authoritative for this project.

All four modules — `common`, `fabric`, `forge`, and `neoforge` — are ported to Minecraft 1.21.1 and
compile. `forge` keeps the classic Forge API; `neoforge` is the parallel port on the NeoForge
equivalents and carries a full loader source tree, not a skeleton. Each loader also builds a
development-only `somestacks_gametest` suite.

## Toolchain

| Tool | Active declaration |
| --- | --- |
| Gradle | Wrapper pinned in `gradle/wrapper/gradle-wrapper.properties` at **9.5.1** |
| Java toolchain | Language version 21; source/target 21; `--release 21` |
| IntelliJ Gradle JVM | `.idea/gradle.xml` sets `gradleJvm="21"` |
| Architectury Loom | `1.17.491` |
| Architectury Plugin | `3.5.169` |
| Shadow (`com.gradleup.shadow`) | `9.4.3` |

The project toolchain forces compilation and every Gradle `JavaExec` task, including Minecraft
development runs, onto Java 21, so a JDK 21 must be discoverable (IntelliJ already runs Gradle on
one; command-line wrapper invocations need `JAVA_HOME` or `org.gradle.java.home` pointed at a 21).
There is no global `gradle` command; the wrapper is the build entry point.

`gradle.properties` gives Gradle 3 GiB with `org.gradle.jvmargs=-Xmx3G`, disables the persistent
daemon with `org.gradle.daemon=false`, and enables parallel execution. Loom and the Architectury
Plugin are pinned to numbered versions rather than moving snapshot aliases.

## Minecraft and library versions

| Component | Build version | Declared runtime constraint |
| --- | --- | --- |
| Minecraft | **1.21.1** | exactly `[1.21.1]` on Forge and NeoForge; exactly `1.21.1` on Fabric |
| Mappings | Mojang official plus Parchment **2024.11.17-1.21.1** | development only |
| Forge | **1.21.1-52.1.16** | Forge `[52.1.16,53)`; FML `[52,53)` |
| NeoForge | **21.1.248** | NeoForge `[21.1.248,22)`; JavaFML `[1,)` |
| Fabric Loader | **0.19.3** | `>=0.19.3` |
| Fabric API | **0.116.15+1.21.1** | `>=0.116.15+1.21.1` |
| FTB Chunks (Fabric/NeoForge) | **2101.1.21** | `[2101,2102)`, optional compile-only |
| JSR-305 | **3.0.2** | compile-only annotation dependency |

There is no JUnit suite; all automated testing is the per-loader GameTest suites.
`forge_compile_version` / `neoforge_compile_version` are each both the compile dependency and the
minimum accepted runtime for that loader. Architectury is a build-time dependency only: the
Architectury Plugin and Loom supply `@ExpectPlatform` / `@Environment` transformation, and no loader
carries an Architectury API runtime dependency. FTB Chunks is the only optional third-party mod
integration and is never bundled. Open Parties and Claims support was removed with the 1.21.1 port
(no 1.21.1 build exists).

Plugin resolution uses the Fabric, Architectury, Forge, and NeoForged Maven repositories plus the
Gradle Plugin Portal. Subprojects additionally use the NeoForged, Parchment, and FTB Maven
repositories. All active version pins are in the root build scripts and `gradle.properties`
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
bundles its transformed production output through `shadowBundle`. Development runs instead group
the common and loader source sets into one logical mod under `loom.mods.main`. The production
artifacts are:

```text
forge/build/libs/somestacks-forge-<version>.jar
fabric/build/libs/somestacks-fabric-<version>.jar
neoforge/build/libs/somestacks-neoforge-<version>.jar
```

Common's only Architectury API usage, the config-directory lookup, resolves through the
`@ExpectPlatform` helper `com.github.crittscott.somestacks.PlatformPaths`, with a per-loader
`PlatformPathsImpl`.

The `*-dev-shadow.jar` and `*-sources.jar` files in those directories are development artifacts,
not release JARs. Common's transformed JARs are intermediate inputs to the loader builds.

## Current IntelliJ run configurations

`.idea/runConfigurations/` currently contains nine Architectury-generated application runs — a
client, a server, and a Game Test Server for each of `:forge`, `:fabric`, and `:neoforge`.

All nine launch through `dev.architectury.transformer.TransformerRuntime`. Forge and NeoForge use
`cpw.mods.bootstraplauncher.BootstrapLauncher`; Fabric uses Knot. There are no plain Fabric Loom
runs or data-generation run in the current project.

Each loader's GameTest run is configured by its own `build.gradle` the same way: `<loader>/src/gametest`
becomes the separate development-only `somestacks_gametest` mod, and `common/src/gametest/java` is
added as an extra source directory. Forge and NeoForge enable their GameTest namespace through
`forge.enabledGameTestNamespaces` / `neoforge.enabledGameTestNamespaces`; Fabric passes
`-Dfabric-api.gametest`. Each loader keeps its own checked-in Base64 fixture at
`<loader>/src/gametest/fixtures/somestacks_empty.nbt.b64`, which `generateGameTestStructures` decodes
into the build directory before GameTest resources are processed.

## Environment traps

- **Compile-only dependencies from `common` do not automatically reach loader compilation.**
  Architectury's `common` and `shadowBundle` configurations carry common output, not all of its
  dependency declarations. Fabric therefore redeclares JSR-305 for `javax.annotation.Nullable`;
  Forge and NeoForge receive it through their dependency graphs.
- **Development must expose common and loader output as one logical mod.** Each loader's
  `loom.mods.main` includes both source sets. Adding common as a separate runtime mod can produce
  duplicate loading or Forge JPMS split-package failures; production bundling belongs in
  `shadowBundle`.
- **The Forge `gametest` source set needs main output on both classpaths.** Main's dependency
  classpath alone does not contain the mod's own compiled classes. The explicit
  `sourceSets.main.output` additions in `forge/build.gradle` are required.
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
- **`working-build-env/` is an inactive reference tree.** Changing files there does not change the
  root build.

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
