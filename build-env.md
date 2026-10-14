# Some Stacks Build Environment

This records the toolchain and dependency versions used by this repository, based on the active
Gradle configuration and the recorded machine and IntelliJ settings. Build declarations do not
imply that the updated plugins or wrapper have been verified by a build. See
[orientation-code.md](orientation-code.md) for the runtime architecture; this file covers the environment that
builds and launches it.

The `working-build-env/` directory is not part of the active Gradle build. The root
`settings.gradle` includes only `common`, `forge`, and `fabric`; declarations under
`working-build-env/` are therefore not authoritative for this project.

## Toolchain

| Tool | Active declaration | Present in this machine's environment |
| --- | --- | --- |
| Gradle | Wrapper pinned in `gradle/wrapper/gradle-wrapper.properties` | **9.5.1** |
| Java toolchain | Language version 17; source/target 17; `--release 17` | Temurin **17.0.15+6** from `JAVA_HOME` |
| Recorded IntelliJ Gradle JVM | JDK 21; local IntelliJ settings are not present in this checkout | Temurin **21.0.2+13** |
| Architectury Loom | `1.17.493` | Recorded Stage 6 verification; historical evidence unavailable here |
| Architectury Plugin | `3.5.170` | Recorded Stage 6 verification; historical evidence unavailable here |
| Shadow (`com.gradleup.shadow`) | `9.4.3` | 9.4.3 cached |
| Mixin patched by Loom | transitive | `dev.architectury:mixin-patched:0.8.5.12` cached |

The command-line environment resolves `java` through:

```text
C:\Users\Dad\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.15.6-hotspot
```

The machine also has Temurin 21.0.2+13, Oracle JDK 17.0.9+11, and a Java 8 JRE installed. The recorded IntelliJ setup uses JDK 21 to run Gradle; no local IntelliJ configuration is available to verify that setting. The project toolchain forces compilation and every Gradle `JavaExec` task, including Minecraft development runs, onto Java 17. Command-line wrapper invocations use the Java 17 `JAVA_HOME`. There is no global `gradle` command; the wrapper is the build entry point.

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
| Fabric API | **0.92.7+1.20.1** | `>=0.92.7+1.20.1` |
| JUnit | BOM **5.10.2**, Jupiter | common and Fabric JSON-persistence tests |
| JSR-305 | **3.0.2** | compile-only annotation dependency |

`forge_compile_version` is both Forge's compile dependency and the minimum accepted Forge runtime.
Fabric requires Fabric API. Neither loader depends on Architectury API at runtime; Architectury Loom and the Architectury Plugin remain build tooling. Loader-backed PlatformServices supplies configuration paths, mod versions, config transport, and automation authority.
Fabric has optional compile-only integrations with FTB Chunks **2001.3.8** and Open Parties and
Claims **fabric-1.20.1-0.29.2**, resolved through FTB and Modrinth Maven repositories. Neither is
bundled or required at runtime; their protection adapters are used only when installed.

The Fabric API baseline is currently lowered from 0.92.11+1.20.1 to 0.92.7+1.20.1 as an in-progress
test of compatibility with a modpack pinned to the older version. Revert `fabric_api_version` in
`gradle.properties` if the test does not pan out.

Plugin resolution uses the Fabric, Architectury, and Forge Maven repositories plus the Gradle
Plugin Portal. Subprojects use the Architectury and Parchment repositories; Fabric additionally
uses FTB and Modrinth for its optional integrations. All active version pins are in the root build scripts and `gradle.properties`; there is no `buildSrc`, version catalog, or
dependency locking.

## Module and packaging setup

The active mod version is **0.8.3**, declared in `gradle.properties`. `settings.gradle` always
includes `common` and selects the loader modules from `enabled_platforms` (currently
`fabric,forge`); only those two loader names are accepted. The active build has three subprojects:

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
forge/build/libs/somestacks-forge-1.20.1-<version>.jar
fabric/build/libs/somestacks-fabric-1.20.1-<version>.jar
```

The `*-dev-shadow.jar` and `*-sources.jar` files in those directories are development artifacts,
not release JARs. Common's transformed JARs are intermediate inputs to the loader builds.

## Current IntelliJ run configurations

This checkout has no `.idea/runConfigurations/` directory. The active Gradle scripts declare ordinary client/server runs and GameTest runs for both loaders; a Gradle refresh generates the IntelliJ configurations:

- `Minecraft Client (:forge)` and `Minecraft Server (:forge)`
- `Game Test Server (:forge)`
- `Minecraft Client (:fabric)` and `Minecraft Server (:fabric)`
- `Game Test Server (:fabric)`

Architectury development runs launch through `dev.architectury.transformer.TransformerRuntime`. Forge uses `BootstrapLauncher`; Fabric uses Knot. There is no data-generation run in the current project.

The Forge GameTest run is configured by `forge/build.gradle`. It loads ordinary Forge and common
production output as `somestacks`, and `forge/src/gametest` as the separate development-only
`somestacks_gametest` mod. The run enables the `somestacks` GameTest namespace. Its empty test
structure is stored as the shared textual fixture
`common/src/gametest/fixtures/somestacks_empty.nbt.b64`; the root `configureGameTests` helper
registers each loader's source set and `generateGameTestStructures` task. Each task decodes the
fixture into its own build directory under `data/somestacks/structures/` before GameTest resources
are processed.

Fabric's GameTest run is configured the same way by `fabric/build.gradle`: `fabric/src/gametest`
becomes the separate development-only `somestacks_gametest` mod, and the run passes
`-Dfabric-api.gametest`. It uses the same shared fixture and root setup helper as Forge. Its
dedicated working directory is `fabric/run-gametest`; `runGameTestServer` clears only the `world` directory inside it before
launch and writes its report to `fabric/build/gametest-report.xml`. Ordinary development runs
continue to use `fabric/run`. Existing IntelliJ configurations must be regenerated by a Gradle
refresh to pick up the new GameTest directory.

The active loader scripts also declare `Client Game Tests` / `runClientGameTests` for each loader, using `run-client-gametest` and the development-only test mod. A Gradle refresh generates those additional IntelliJ configurations. In a test client, join a world, aim at any stack, release Shift and the Stack Modifier, and run `/ssclienttest` to check the actual local callback's consumption without performing a server gesture. The Fabric test mod allows both client and server environments; its client entrypoint registers only the smoke command. Shared development-only language resources accompany both loaders' tests and are excluded from release JARs.

## Environment traps

- **Compile-only dependencies from `common` do not automatically reach loader compilation.**
  Architectury's `common` and `shadowBundle` configurations carry common output, not all of its
  dependency declarations. The root build therefore declares JSR-305 for every module.
- **Development must expose common and loader output as one logical mod.** Each loader's
  `loom.mods.main` includes both source sets. Adding common as a separate runtime mod can produce
  duplicate loading or Forge JPMS split-package failures; production bundling belongs in
  `shadowBundle`.
- **The Forge `gametest` source set needs main output on both classpaths.** Main's dependency
  classpath alone does not contain the mod's own compiled classes. The explicit
  `main.output` additions in the root `configureGameTests` helper are required.
- **GameTest Java packages must stay separate from production packages.** Forge loads the test mod and production mod as separate JPMS modules; a shared package prevents launch. Foundation checks live under `com.github.crittscott.somestacks.gametest` and use test-only reflection for package-private batching controls.
- **Forge scans GameTests only from registered Loom mod output.** The `somestacks_gametest`
  `loom.mods` entry, its `mods.toml`, stub `@Mod` class, and `pack.mcmeta` are all part of making the
  custom source set visible to FML and making its structure resource load. A launch that discovers
  zero tests can otherwise look like a successful run.
- **The generated GameTest NBT is not a source file.** Edit the Base64 fixture under
  `common/src/gametest/fixtures`; the decoded files under each loader's `build/generated` are
  disposable build output.
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
.\gradlew :fabric:test
.\gradlew :forge:runGameTestServer
.\gradlew :fabric:runGameTestServer
.\gradlew :forge:runClientGameTests
.\gradlew :fabric:runClientGameTests
.\gradlew generateDocs
```

The root `build` covers the configured subproject builds, common JUnit tests, and Fabric JSON-persistence JUnit tests. The Forge and
Fabric GameTest servers are separate development runs, each covering only its own loader.

## Metadata, documentation, and build snapshot

The root `sharedModProperties` map supplies common metadata expansion values. Loader scripts add
only their compatibility constraints; common still expands `pack.mcmeta`. Forge and Fabric
GameTest metadata retain their own expansion blocks and remain outside production resources.
The build has no Maven publication configuration, and Forge's manifest has no build timestamp.

Each module retains the standard production `javadoc` task. Each selected loader also has
`gametestJavadoc`, including shared scenarios and its own test holders. The root `generateDocs`
task synchronizes those outputs into `docs/javadoc/common`, `docs/javadoc/<loader>`, and
`docs/javadoc/<loader>-gametest`. Documentation generation is separate from `build`.

`build-env/` is a reference snapshot of the active root and module Gradle scripts, properties,
and wrapper inputs; it excludes inactive NeoForge files. It is not a standalone source tree.
The wrapper properties retain Gradle 9.5.1 and specify URL validation, zero retries, and a 500 ms retry backoff. The snapshot matches the active build files byte-for-byte, including the wrapper JAR. The recorded Stage 6/backport history and comparison with the 1.21.4 reference cannot be independently verified from this checkout.

Existing results from October 13, 2026 show all 69 common JUnit tests, 4 Fabric JSON-persistence JUnit tests, 98 Fabric GameTests, and 99 Forge GameTests passing. Both loader release JARs are present; their contents were not inspected. Ordinary client/server checks and documentation generation remain unverified. These results were read from existing reports and logs; no builds or tests were run during this documentation check.
