# Some Stacks Build Environment

This document is an orientation to the repository's build environment: its entry points, module layout, version authorities, dependency baselines, and packaging flow. It describes the setup as it exists. It is not build history, a troubleshooting log, release documentation, or a conversation.

`build-env/` is a reference snapshot of every checked-in Gradle build input, including the wrapper. It preserves repository-relative paths so that files can be compared or restored without guessing where they belong. The active files at the repository root and under `common/`, `fabric/`, `neoforge/`, and `gradle/` remain authoritative; Gradle does not read the copies. Keep the snapshot and this description synchronized whenever an active build file changes.

The snapshot should contain only manually maintained files that define or launch this Gradle build: Gradle scripts, Gradle properties, wrapper launchers, and wrapper files. It should not contain caches, generated output, IDE state, run directories, resolved dependency JARs, mod source or resources, GameTest fixtures, loader manifests, release notes, or personal convenience scripts such as `gradlews.ps1`. The wrapper JAR is the sole binary because it is itself a checked-in build launcher.

## Snapshot contents

| Active path | Reference copy |
| --- | --- |
| `settings.gradle` | `build-env/settings.gradle` |
| `build.gradle` | `build-env/build.gradle` |
| `gradle.properties` | `build-env/gradle.properties` |
| `common/build.gradle` | `build-env/common/build.gradle` |
| `fabric/build.gradle` | `build-env/fabric/build.gradle` |
| `fabric/gradle.properties` | `build-env/fabric/gradle.properties` |
| `neoforge/build.gradle` | `build-env/neoforge/build.gradle` |
| `neoforge/gradle.properties` | `build-env/neoforge/gradle.properties` |
| `gradlew` | `build-env/gradlew` |
| `gradlew.bat` | `build-env/gradlew.bat` |
| `gradle/wrapper/gradle-wrapper.properties` | `build-env/gradle/wrapper/gradle-wrapper.properties` |
| `gradle/wrapper/gradle-wrapper.jar` | `build-env/gradle/wrapper/gradle-wrapper.jar` |

## Build shape

The project is a Groovy-DSL Gradle build with `common`, `fabric`, and `neoforge` subprojects; `settings.gradle` includes the loaders listed in `enabled_platforms`. The root build applies Architectury Loom's no-remap plugin (`dev.architectury.loom-no-remap`) and the Architectury plugin to each subproject and establishes the shared Minecraft dependency and Java settings. Minecraft 26.1.1 is not obfuscated, so the build declares no mappings and performs no remapping; sources compile directly against Mojang's names. The build declares no Maven publication; the modules expose no stable public API and nothing consumes them through Maven. `common` is transformed for Fabric and NeoForge; each loader module bundles its transformed common output with Shadow, and the shadow JAR (no classifier) is the production artifact, while the plain `jar` task's output carries the `dev` classifier. Every artifact's base name carries the loader and Minecraft version, `somestacks-<loader>-<minecraft>`. Architectury is build-time only; no loader depends on Architectury API at runtime.

`common` has no resource expansion. Each loader expands its own metadata (`fabric.mod.json`, `META-INF/neoforge.mods.toml`) from the root `sharedModProperties` plus its loader-specific ranges, and expands its GameTest mod's metadata separately.

The common module declares Fabric Loader as compile-only to resolve Minecraft's environment annotations; it is not bundled or added as a runtime requirement for NeoForge.

Each loader module compiles against common through the `common` configuration, which depends on the plain `:common` project. Fabric also places it on its runtime and development classpaths; NeoForge runs receive common only through their `loom.mods` source sets, because a second copy would split its packages across two modules.

Fabric and NeoForge each call the root `configureGameTests` helper, which creates a `gametest` source set over the shared scenarios in `common/src/gametest/java`, registers `generateGameTestStructures` to decode the checked-in `common/src/gametest/fixtures/somestacks_empty.nbt.b64` into the source set's generated resources, and registers `gametestJavadoc`. Each loader maps that source set to a separate `somestacks_gametest` development mod and provides `runClientGameTests` for the local `/ssclienttest` callback smoke command. The same source set is wired into a `runGameTestServer` run: NeoForge uses its native GameTest server launch template and sets `neoforge.enableGameTest` in both test runs; Fabric passes `-Dfabric-api.gametest` with a report file and clears its development GameTest world before each run. Fabric uses its own `GameTest` annotation on ordinary entrypoint objects listed in its GameTest metadata. NeoForge registers `Consumer<GameTestHelper>` functions through its native `DeferredRegister` API, with checked-in `data/somestacks/test_instance` JSON files supplying each function's structure, environment, and timeout. The shared empty structure keeps its `somestacks:somestacks_empty` identifier and the tests use `minecraft:default`. The common pack, which carries both assets and data, and the NeoForge GameTest pack declare `min_format`/`max_format` `[101, 1]`, the 26.1.1 data-pack format 101.1; third-party resource packs for this target use format 84. Fabric packages its test data through the development mod, with no separate test pack declaration.

On Windows, `gradlew.bat` is the normal entry point; `gradlew` is the POSIX launcher. The wrapper selects the Gradle distribution, while the launcher selects its host JVM from the machine's Java configuration. Use Java 25 for the Gradle host JVM, including IntelliJ's Gradle JVM setting, and select the Gradle wrapper in IntelliJ. The root build declares a Java 25 toolchain, which Gradle applies by convention to compilation, Javadoc, and Gradle-launched Java executions; that toolchain does not select the host JVM.

Each subproject has the Java plugin's standard production-source `javadoc` task. The root `generateDocs` task derives its loader list from `enabled_platforms`, depends on every module's `javadoc` and each loader's `gametestJavadoc`, and synchronizes their HTML output into the committed `docs/javadoc/<module>/` and `docs/javadoc/<loader>-gametest/` trees; separate sections are required because loader modules contain classes with overlapping fully qualified names. Generated documentation is intentionally excluded from `build-env/`. The build does not produce Javadoc JARs.

The build targets Minecraft 26.1.1 with Java 25 on Fabric and NeoForge.

## Exact build versions

| Component | Exact version or coordinate | Build role |
| --- | --- | --- |
| Gradle | `9.5.1` (`gradle-9.5.1-bin.zip`) | Wrapper-selected build engine |
| Architectury Loom (no-remap) | `1.17.493` | Minecraft development, runs, and platform transforms without remapping |
| Architectury Gradle plugin | `3.5.170` | Common/Fabric/NeoForge project organization |
| GradleUp Shadow plugin | `9.4.3` | Bundles transformed common output into loader JARs |
| Java toolchain level | `25` | Compilation, Javadoc, and Java execution |
| Minecraft | `com.mojang:minecraft:26.1.1` | Compile and runtime target; unobfuscated, no mappings |
| NeoForge | `net.neoforged:neoforge:26.1.1.15-beta` | Exact NeoForge compile and development-run baseline |
| Fabric Loader | `net.fabricmc:fabric-loader:0.19.5` | Fabric loader dependency |
| Fabric API | `net.fabricmc.fabric-api:fabric-api:0.145.4+26.1.1` | Fabric runtime and development API |
| JSR 305 annotations | `com.google.code.findbugs:jsr305:3.0.2` | Compile-only nullability annotations, declared once for every module |

The Java setting is exact only at the language/toolchain-major level. The repository does not pin a JDK vendor, distribution, or patch release, and it does not pin the host JVM that runs Gradle. Gradle core plugins such as `base` and `java` use Gradle `9.5.1` and therefore have no separate declared version.

Published version references: [NeoForge Maven metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml) and [Fabric API Maven metadata](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml). Artifact publication does not establish successful Gradle resolution or runtime compatibility.

## Artifact and runtime version declarations

These values do not select build tools, but they are versioned inputs consumed by resource expansion and are relevant when reproducing the produced artifacts.

| Subject | Declaration |
| --- | --- |
| Some Stacks artifact | `0.8.3` |
| Fabric and NeoForge GameTest support mods | the Some Stacks artifact version |
| Minecraft compatibility | exactly `26.1.1`; NeoForge syntax `[26.1.1]`, Fabric syntax `26.1.1` |
| NeoForge compatibility | `[26.1.1.15-beta,26.1.2)` |
| NeoForge JavaFML loader compatibility | `[1,)` |
| Fabric Loader compatibility | `>=0.19.5` |
| Fabric API runtime declaration | `>=0.145.4+26.1.1`; compilation uses `0.145.4+26.1.1` |

## Resolution and version authorities

`gradle.properties` is the authority for the Minecraft, loader, API, compatibility, and mod versions. The root `build.gradle` pins the three external Gradle plugins and JSR 305, and `gradle/wrapper/gradle-wrapper.properties` pins Gradle itself. The loader scripts consume the root properties for the game and loader dependencies.

Plugin resolution uses Fabric Maven, Architectury Maven, NeoForge Maven, and the Gradle Plugin Portal. The NeoForge module also declares NeoForge Maven for dependencies; Loom supplies its standard Minecraft and Fabric repositories. There is no Gradle version catalog, dependency-lock state, dependency-verification metadata, exact JDK distribution, or wrapper-distribution checksum in the repository. Consequently, the table above records every exact version deliberately declared by the build, but it is not a lock of every transitive artifact selected by Gradle and Loom.
