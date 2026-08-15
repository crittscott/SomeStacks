# Some Stacks Build Environment

This records the toolchain and dependency versions used by this repository, based on the active
Gradle configuration, this machine's installed JDKs and Gradle cache, and the current IntelliJ
project settings. See [as-built.md](as-built.md) for the runtime architecture; this file covers the
environment that builds and launches it.

The `working-build-env/` directory is not part of the active Gradle build. The root
`settings.gradle` includes only `common`, `forge`, and `fabric`; declarations under
`working-build-env/` are therefore not authoritative for this project.

## Toolchain

| Tool | Active declaration | Present in this machine's environment |
| --- | --- | --- |
| Gradle | Wrapper pinned in `gradle/wrapper/gradle-wrapper.properties` | **9.5.1** |
| Java toolchain | Language version 17; source/target 17; `--release 17` | Temurin **17.0.15+6** from `JAVA_HOME` |
| IntelliJ Gradle JVM | `.idea/gradle.xml` sets `gradleJvm="21"` | Temurin **21.0.2+13** |
| Architectury Loom | `1.17.491` | 1.17.491 cached |
| Architectury Plugin | `3.5.169` | 3.5.169 cached |
| Shadow (`com.gradleup.shadow`) | `9.4.3` | 9.4.3 cached |
| Mixin patched by Loom | transitive | `dev.architectury:mixin-patched:0.8.5.12` cached |

The command-line environment resolves `java` through:

```text
C:\Users\Dad\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.15.6-hotspot
```

The machine also has Temurin 21.0.2+13, Oracle JDK 17.0.9+11, and a Java 8 JRE installed. IntelliJ
uses JDK 21 to run Gradle, while the project toolchain forces compilation and every Gradle
`JavaExec` task, including Minecraft development runs, onto Java 17. Command-line wrapper
invocations use the Java 17 `JAVA_HOME`. There is no global `gradle` command; the wrapper is the
build entry point.

`gradle.properties` gives Gradle 3 GiB with `org.gradle.jvmargs=-Xmx3G` and disables the persistent
daemon with `org.gradle.daemon=false`. It does not enable parallel execution. Loom and the
Architectury Plugin are pinned to numbered versions rather than moving snapshot aliases.

## Minecraft and library versions

| Component | Build version | Declared runtime constraint |
| --- | --- | --- |
| Minecraft | **1.20.1** | exactly `[1.20.1]` on Forge; exactly `1.20.1` on Fabric |
| Mappings | Mojang official plus Parchment **2023.09.03-1.20.1** | development only |
| Forge | **1.20.1-47.4.10** | Forge `[47.4.10,48)`; FML `[47,48)` |
| Fabric Loader | **0.19.3** | `>=0.19.3` |
| Fabric API | **0.92.11+1.20.1** | `>=0.92.11+1.20.1` |
| Architectury API | **9.2.14** | `>=9.2.14` / `[9.2.14,)` |
| JUnit | BOM **5.10.2**, Jupiter | common-module tests only |
| JSR-305 | **3.0.2** | compile-only annotation dependency |

`forge_compile_version` is both Forge's compile dependency and the minimum accepted Forge runtime.
The production metadata requires Architectury API on both loaders and Fabric API on Fabric. There
are no optional third-party mod integrations or third-party mod repositories in the active build.

Plugin resolution uses the Fabric, Architectury, and Forge Maven repositories plus the Gradle
Plugin Portal. Subprojects use the Architectury and Parchment repositories. All active version pins
are in the root build scripts and `gradle.properties`; there is no `buildSrc`, version catalog, or
dependency locking.

## Module and packaging setup

The active build has three subprojects:

```text
common/   shared source and resources; transformed into each loader artifact
forge/    Forge entry points and integration
fabric/   Fabric entry points and integration
```

Both loader modules compile against `common` through Architectury's `common` configuration and
bundle its transformed production output through `shadowBundle`. Development runs instead group
the common and loader source sets into one logical mod under `loom.mods.main`. The production
artifacts are:

```text
forge/build/libs/somestacks-forge-<version>.jar
fabric/build/libs/somestacks-fabric-<version>.jar
```

The `*-dev-shadow.jar` and `*-sources.jar` files in those directories are development artifacts,
not release JARs. Common's transformed JARs are intermediate inputs to the loader builds.

## Current IntelliJ run configurations

`.idea/runConfigurations/` currently contains six Architectury-generated application runs:

- `Minecraft Client (:forge)` and `Minecraft Server (:forge)`
- `Game Test Server (:forge)`
- `Minecraft Client (:fabric)` and `Minecraft Server (:fabric)`
- `Game Test Server (:fabric)`

All six launch through `dev.architectury.transformer.TransformerRuntime`. Forge uses
`BootstrapLauncher`; Fabric uses Knot. There are no plain Fabric Loom runs or data-generation run in
the current project.

The Forge GameTest run is configured by `forge/build.gradle`. It loads ordinary Forge and common
production output as `somestacks`, and `forge/src/gametest` as the separate development-only
`somestacks_gametest` mod. The run enables the `somestacks` GameTest namespace. Its empty test
structure is stored as the textual fixture
`forge/src/gametest/fixtures/somestacks_empty.nbt.b64`; `generateGameTestStructures` decodes it into
the build directory before GameTest resources are processed.

Fabric's GameTest run is configured the same way by `fabric/build.gradle`: `fabric/src/gametest`
becomes the separate development-only `somestacks_gametest` mod, and the run passes
`-Dfabric-api.gametest`. Its empty test structure uses the same fixture mechanism as Forge's, from
its own checked-in Base64 fixture at `fabric/src/gametest/fixtures/somestacks_empty.nbt.b64`.

## Environment traps

- **Compile-only dependencies from `common` do not automatically reach loader compilation.**
  Architectury's `common` and `shadowBundle` configurations carry common output, not all of its
  dependency declarations. Fabric therefore redeclares JSR-305 for `javax.annotation.Nullable`;
  Forge currently receives it through its dependency graph.
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
  `forge/src/gametest/fixtures`; the decoded file under `forge/build/generated` is disposable build
  output.
- **Forge and Fabric register GameTest classes differently.** Forge discovers test methods by
  scanning the loaded mod for classes annotated `@GameTestHolder`; Fabric instead requires each
  class to implement `FabricGameTest` and to be listed under a `fabric-gametest` entrypoint in the
  dev-mod's own `fabric.mod.json`. A new Fabric GameTest class that is not added to that entrypoint
  list will not run.
- **`working-build-env/` is an inactive reference tree.** Changing files there does not change the
  root build.

## Build and test commands

Use the wrapper from the repository root in PowerShell:

```powershell
.\gradlew build
.\gradlew :forge:build
.\gradlew :fabric:build
.\gradlew :common:test
.\gradlew :forge:runGameTestServer
.\gradlew :fabric:runGameTestServer
```

The root `build` covers the configured subproject builds and common JUnit tests. The Forge and
Fabric GameTest servers are separate development runs, each covering only its own loader.
