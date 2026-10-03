# Some Buckets Build Environment

This document is an orientation to the repository's build environment: its entry points, module layout, version authorities, dependency baselines, and packaging flow. It describes the setup as it exists. It is not build history, a troubleshooting log, release documentation, or a conversation.

`build-env/` is a reference snapshot of every checked-in Gradle build input, including the wrapper. It preserves repository-relative paths so that files can be compared or restored without guessing where they belong. The active files at the repository root and under `common/`, `fabric/`, `forge/`, `neoforge/`, and `gradle/` remain authoritative; Gradle does not read the copies. Keep the snapshot and this description synchronized whenever an active build file changes.

The snapshot should contain only manually maintained files that define or launch this Gradle build: Gradle scripts, Gradle properties, wrapper launchers, and wrapper files. It should not contain caches, generated output, IDE state, run directories, resolved dependency JARs, mod source or resources (including the access widener), loader manifests, release notes, or personal convenience scripts such as `gradlews.ps1`. The wrapper JAR is the sole binary because it is itself a checked-in build launcher.

## Snapshot contents

| Active path | Reference copy |
| --- | --- |
| `settings.gradle` | `build-env/settings.gradle` |
| `build.gradle` | `build-env/build.gradle` |
| `gradle.properties` | `build-env/gradle.properties` |
| `common/build.gradle` | `build-env/common/build.gradle` |
| `fabric/build.gradle` | `build-env/fabric/build.gradle` |
| `fabric/gradle.properties` | `build-env/fabric/gradle.properties` |
| `forge/build.gradle` | `build-env/forge/build.gradle` |
| `forge/gradle.properties` | `build-env/forge/gradle.properties` |
| `neoforge/build.gradle` | `build-env/neoforge/build.gradle` |
| `neoforge/gradle.properties` | `build-env/neoforge/gradle.properties` |
| `gradlew` | `build-env/gradlew` |
| `gradlew.bat` | `build-env/gradlew.bat` |
| `gradle/wrapper/gradle-wrapper.properties` | `build-env/gradle/wrapper/gradle-wrapper.properties` |
| `gradle/wrapper/gradle-wrapper.jar` | `build-env/gradle/wrapper/gradle-wrapper.jar` |

## Build shape

The project is a Groovy-DSL Gradle build with `common`, `fabric`, `forge`, and `neoforge` subprojects. The root build applies Architectury Loom and the Architectury plugin to each subproject and establishes shared Minecraft mappings and Java settings. The build declares no Maven publication; the modules expose no stable public API and nothing consumes them through Maven. `common` is transformed for Fabric, Forge, and NeoForge; each loader module bundles its transformed common output with Shadow and then remaps the resulting production JAR. The Fabric JAR is also the Quilt artifact; there is no separate Quilt subproject or production JAR.

`common` declares the access widener `common/src/main/resources/somebuckets.accesswidener` through `loom.accessWidenerPath`; Fabric and NeoForge point their own `loom.accessWidenerPath` at common's; NeoForge, which reads access transformers rather than wideners, also names that widener in `atAccessWideners` so its development runs apply it. Forge keeps its one common entry in `accesstransformer.cfg` beside three Forge-only client entries because Loom cannot convert into a JAR that already contains an access transformer; `verifyCommonAccessMirror`, wired into `check`, rejects drift between the common widener and that mirror.

Fabric Loom uses the legacy Mixin annotation processor and writes the fixed `somebuckets.refmap.json` refmap. Forge and NeoForge use their loader-specific Loom setup without that Fabric-only Mixin block.

Each loader module compiles against common through the `common` configuration. Fabric also places it on its runtime and development classpaths; Forge and NeoForge runs receive common only through their `loom.mods` source sets, because a second copy would split its packages across two modules. Fabric additionally compiles against the Common Protection API (`modCompileOnly`) so claim mods that ship it can be consulted when present, and puts it on its development runs (`modLocalRuntime`) so that integration is exercised there; both declarations are non-transitive, and the API is neither bundled nor required at runtime. Fabric, Forge, and NeoForge each call the root `configureGameTests` helper, which creates a `gametest` source set over the shared scenarios and structure resources and registers `gametestJavadoc`; each loader wires that source set into a `runGameTestServer` run. Forge and NeoForge have data runs and loader-native `GlobalLootModifierProvider` implementations that derive checked-in add-table modifier resources under `src/generated/resources` from the common loot manifest. Fabric clears only its development GameTest world before a GameTest server run. All three loader modules are implemented runtime mods; `common` is transformed for each and bundled into its production JAR.

On Windows, `gradlew.bat` is the normal entry point; `gradlew` is the POSIX launcher. The wrapper selects the Gradle distribution, while the launcher selects its host JVM from the machine's Java configuration. The root build declares a Java 21 toolchain, which Gradle applies by convention to compilation, Javadoc, and Gradle-launched Java executions.

Each subproject has the Java plugin's standard production-source `javadoc` task. Each loader also has a `gametestJavadoc` task over its complete GameTest source set, including the shared package-private scenarios under `common/src/gametest/java`. The root `generateDocs` task derives its loader list from `enabled_platforms`, depends on every module's `javadoc` and each loader's `gametestJavadoc`, and synchronizes their HTML output into the committed `docs/javadoc/<module>/` and `docs/javadoc/<loader>-gametest/` trees; separate sections are required because loader modules contain classes with overlapping fully qualified names. `docs/index.html` is the hand-maintained landing page and `docs/.nojekyll` makes GitHub Pages serve the generated files unchanged. Neither file is a Gradle build input, and generated documentation is intentionally excluded from `build-env/`. The build does not produce Javadoc JARs.

## Exact build versions

| Component | Exact version or coordinate | Build role |
| --- | --- | --- |
| Gradle | `9.5.1` (`gradle-9.5.1-bin.zip`) | Wrapper-selected build engine |
| Architectury Loom | `1.17.493` | Minecraft development, mappings, runs, transforms, and remapping |
| Architectury Gradle plugin | `3.5.170` | Common/Fabric/Forge/NeoForge project organization |
| GradleUp Shadow plugin | `9.4.3` | Bundles transformed common output into loader JARs |
| Java toolchain level | `21` | Compilation, Javadoc, and Java execution |
| Minecraft | `1.21.4` | Compile and runtime target |
| Mojang mappings | Official mappings for `1.21.4` | Base mapping layer; no separate mapping version is declared |
| Parchment mappings | `org.parchmentmc.data:parchment-1.21.4:2025.03.23@zip` | Layer over the official mappings |
| Forge | `net.minecraftforge:forge:1.21.4-54.1.0` | Exact Forge compile and development-run baseline |
| NeoForge | `net.neoforged:neoforge:21.4.139` | Exact NeoForge compile and development-run baseline |
| Fabric Loader | `net.fabricmc:fabric-loader:0.16.9` | Fabric loader dependency; also supplies the common annotation dependency |
| Fabric API | `net.fabricmc.fabric-api:fabric-api:0.110.5+1.21.4` | Fabric runtime and development API; used by the Fabric artifact on Quilt |
| Common Protection API | `eu.pb4:common-protection-api:1.0.0` | Fabric compile-only optional claim-mod integration, also on Fabric development runs |
| JSR 305 annotations | `com.google.code.findbugs:jsr305:3.0.2` | Compile-only nullability annotations, declared once for every module |

The Java setting is exact only at the language/toolchain-major level. The repository does not pin a JDK vendor, distribution, or patch release, and it does not pin the host JVM that runs Gradle. Gradle core plugins such as `base` and `java` use Gradle `9.5.1` and therefore have no separate declared version.

## Artifact and runtime version declarations

These values do not select build tools, but they are versioned inputs consumed by resource expansion and are relevant when reproducing the produced artifacts.

| Subject | Declaration |
| --- | --- |
| Some Buckets artifact | `0.8.2` |
| Fabric, Forge, and NeoForge GameTest support mods | `1.0.0` |
| Minecraft compatibility | exactly `1.21.4`; Forge and NeoForge syntax `[1.21.4]`, Fabric syntax `=1.21.4` |
| Forge compatibility | `[54.1.0,55)` |
| Forge JavaFML loader compatibility | `[54,55)` |
| NeoForge compatibility | `[21.4.139,22)` |
| NeoForge JavaFML loader compatibility | `[1,)` |
| Fabric Loader compatibility | `>=0.16.9` |
| Fabric Java compatibility | `>=21` |
| Fabric API runtime declaration | `>=0.110.5+1.21.4`; compilation uses `0.110.5+1.21.4` |
| Quilt compatibility | the Fabric artifact is expected to run through Quilt's Fabric compatibility |

## Resolution and version authorities

`gradle.properties` is the authority for the Minecraft, mapping, loader, API, compatibility, integration, and mod versions. The root `build.gradle` pins the three external Gradle plugins and JSR 305, and `gradle/wrapper/gradle-wrapper.properties` pins Gradle itself. The loader scripts consume the root properties rather than restating dependency versions.

Plugin resolution uses Fabric Maven, Architectury Maven, Forge Maven, and the Gradle Plugin Portal. Every module declares Parchment Maven; the Fabric module also declares Nucleoid Maven (for the Common Protection API), and the NeoForge module also declares NeoForge Maven; Loom supplies its standard Minecraft repositories. There is no Gradle version catalog, dependency-lock state, dependency-verification metadata, exact JDK distribution, or wrapper-distribution checksum in the repository. Consequently, the table above records every exact version deliberately declared by the build, but it is not a lock of every transitive artifact selected by Gradle and Loom.
