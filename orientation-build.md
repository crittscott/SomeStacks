<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — build orientation

This document is an orientation to the repository's build environment: its entry points, module layout, version authorities, dependency baselines, and packaging flow. It describes the setup as it exists. It is not build history, a troubleshooting log, release documentation, or a conversation.

`build-env/` is a reference snapshot of every checked-in Gradle build input, including the wrapper. It preserves repository-relative paths so that files can be compared or restored without guessing where they belong. The active files at the repository root and under `common/`, `fabric/`, `forge/`, and `gradle/` remain authoritative; Gradle does not read the copies. Keep the snapshot and this description synchronized whenever an active build file changes.

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
| `forge/build.gradle` | `build-env/forge/build.gradle` |
| `forge/gradle.properties` | `build-env/forge/gradle.properties` |
| `gradlew` | `build-env/gradlew` |
| `gradlew.bat` | `build-env/gradlew.bat` |
| `gradle/wrapper/gradle-wrapper.properties` | `build-env/gradle/wrapper/gradle-wrapper.properties` |
| `gradle/wrapper/gradle-wrapper.jar` | `build-env/gradle/wrapper/gradle-wrapper.jar` |

## Build shape

The project is a Groovy-DSL Gradle build with `common`, `fabric`, and `forge` subprojects; `settings.gradle` includes the loaders listed in `enabled_platforms`. The root build applies Architectury Loom and the Architectury plugin to each subproject and establishes shared Minecraft mappings and Java settings. The build declares no Maven publication; the modules expose no stable public API and nothing consumes them through Maven. `common` is transformed for Fabric and Forge; each loader module bundles its transformed common output with Shadow and then remaps the resulting production JAR. Every module declares `withSourcesJar()`; each loader's `sourcesJar` also unpacks common's sources JAR, because the production JAR bundles common. Every artifact's base name carries the loader and Minecraft version, `somestacks-<loader>-<minecraft>`. Architectury is build-time only; no loader depends on Architectury API at runtime.

`common` has no resource expansion. Each loader expands its own metadata (`fabric.mod.json`, `META-INF/mods.toml`) from the root `sharedModProperties` plus its loader-specific ranges, and expands its GameTest mod's metadata separately.

Fabric uses Loom's current Mixin remapping path without the legacy annotation processor. Forge uses its loader-specific Loom setup.

The common module declares Fabric Loader as compile-only to resolve Minecraft's environment annotations; it is not bundled or added as a runtime requirement for Forge.

Each loader module compiles against common through the `common` configuration. Fabric also places it on its runtime and development classpaths; Forge runs receive common only through their `loom.mods` source sets, because a second copy would split its packages across two modules. Fabric declares three optional claim integrations as compile-only dependencies: FTB Chunks and Open Parties and Claims through `modCompileOnly`, and Common Protection API through non-transitive `modCompileOnly`; `-PwithCommonProtectionApi=true` adds Common Protection API to development runtime through `modRuntimeOnly`. Fabric metadata suggests all three without requiring or bundling them. Fabric placement consults each installed integration (`CommonProtectionQueries`, `FtbChunksProtection`, `OpacProtection`) before placing a block; neighboring-stack deposits also consult Fabric block-use callbacks.

Fabric and Forge each call the root `configureGameTests` helper, which creates a `gametest` source set over the shared scenarios in `common/src/gametest/java`, registers `generateGameTestStructures` to decode the checked-in `common/src/gametest/fixtures/somestacks_empty.nbt.b64` into the source set's generated resources under `data/somestacks/structures/`, and registers `gametestJavadoc`. Each loader maps that source set to a separate `somestacks_gametest` development mod and provides `runClientGameTests` for the local `/ssclienttest` callback smoke command. That command checks a main/off-hand block-use callback while aimed at a stack with Shift and the Stack Modifier released. The same source set is wired into a `runGameTestServer` run: Forge enables the `somestacks` GameTest namespace, and Fabric passes `-Dfabric-api.gametest` with a report file and clears its development GameTest world before each run. Fabric's GameTest metadata includes absence-specific checks by default and provider-backed checks when `-PwithCommonProtectionApi=true` is set.

On Windows, `gradlew.bat` is the normal entry point; `gradlew` is the POSIX launcher. The wrapper selects the Gradle distribution, while the launcher selects its host JVM from the machine's Java configuration. Use Java 17 or newer for the Gradle host JVM, including IntelliJ's Gradle JVM setting, and select the Gradle wrapper in IntelliJ. The root build declares a Java 17 toolchain, which Gradle applies by convention to compilation, Javadoc, and Gradle-launched Java executions; that toolchain does not select the host JVM.

Each subproject has the Java plugin's standard production-source `javadoc` task. The root `generateDocs` task derives its loader list from `enabled_platforms`, depends on every module's `javadoc` and each loader's `gametestJavadoc`, and synchronizes their HTML output into the committed `docs/javadoc/<module>/` and `docs/javadoc/<loader>-gametest/` trees; separate sections are required because loader modules contain classes with overlapping fully qualified names. Generated documentation is intentionally excluded from `build-env/`. The build does not produce Javadoc JARs.

The root `collectJars` task copies each enabled loader's already-built production jar (`<archivesName>-<version>.jar` in its `build/libs`, not the dev-shadow or sources jars) into `../jars`, the directory shared by all version checkouts, for installing into a local launcher. It builds nothing and skips jars that have not been built; it overwrites same-named jars and leaves other versions' jars alone.

## Exact build versions

| Component | Exact version or coordinate | Build role |
| --- | --- | --- |
| Gradle | `9.5.1` (`gradle-9.5.1-bin.zip`) | Wrapper-selected build engine |
| Architectury Loom | `1.17.493` | Minecraft development, mappings, runs, transforms, and remapping |
| Architectury Gradle plugin | `3.5.170` | Common/Fabric/Forge project organization |
| GradleUp Shadow plugin | `9.4.3` | Bundles transformed common output into loader JARs |
| Java toolchain level | `17` | Compilation, Javadoc, and Java execution |
| Minecraft | `1.20.1` | Compile and runtime target |
| Mojang mappings | Official mappings for `1.20.1` | Base mapping layer; no separate mapping version is declared |
| Parchment mappings | `org.parchmentmc.data:parchment-1.20.1:2023.09.03@zip` | Layer over the official mappings |
| Forge | `net.minecraftforge:forge:1.20.1-47.4.10` | Exact Forge compile and development-run baseline |
| Fabric Loader | `net.fabricmc:fabric-loader:0.19.3` | Fabric loader dependency |
| Fabric API | `net.fabricmc.fabric-api:fabric-api:0.92.7+1.20.1` | Fabric runtime and development API |
| FTB Chunks | `dev.ftb.mods:ftb-chunks-fabric:2001.3.8` | Optional Fabric claim API; compile-only |
| Open Parties and Claims | `maven.modrinth:open-parties-and-claims:fabric-1.20.1-0.29.2` | Optional Fabric claim API; compile-only |
| Common Protection API | `eu.pb4:common-protection-api:1.0.0` | Optional Fabric protection-query API; development runtime only with `-PwithCommonProtectionApi=true` |
| JSR 305 annotations | `com.google.code.findbugs:jsr305:3.0.2` | Compile-only nullability annotations, declared once for every module |

The Java setting is exact only at the language/toolchain-major level. The repository does not pin a JDK vendor, distribution, or patch release, and it does not pin the host JVM that runs Gradle. Gradle core plugins such as `base` and `java` use Gradle `9.5.1` and therefore have no separate declared version.

## Artifact and runtime version declarations

These values do not select build tools, but they are versioned inputs consumed by resource expansion and are relevant when reproducing the produced artifacts.

| Subject | Declaration |
| --- | --- |
| Some Stacks artifact | `0.8.3` |
| Fabric and Forge GameTest support mods | the Some Stacks artifact version |
| Minecraft compatibility | exactly `1.20.1`; Forge syntax `[1.20.1]`, Fabric syntax `1.20.1` |
| Forge compatibility | `[47.4.10,48)` |
| Forge JavaFML loader compatibility | `[47,48)` |
| Fabric Loader compatibility | `>=0.19.3` |
| Java compatibility (Fabric metadata) | `>=17` |
| Common Protection API suggested version | `>=1.0.0`; compilation uses `1.0.0` |
| FTB Chunks and Open Parties and Claims suggested versions | any (`*`) |
| Fabric API runtime declaration | `>=0.92.7+1.20.1`; compilation uses `0.92.7+1.20.1` |

## Resolution and version authorities

`gradle.properties` is the authority for the Minecraft, mapping, loader, API, compatibility, integration, and mod versions. The root `build.gradle` pins the three external Gradle plugins and JSR 305, and `gradle/wrapper/gradle-wrapper.properties` pins Gradle itself. The loader scripts consume the root properties rather than restating dependency versions.

Plugin resolution uses Fabric Maven, Architectury Maven, Forge Maven, and the Gradle Plugin Portal. Every module declares Parchment Maven; the Fabric module also declares FTB Maven (for FTB Chunks), Modrinth Maven restricted to the `maven.modrinth` group (for Open Parties and Claims), and Nucleoid Maven (for the Common Protection API); Loom supplies its standard Minecraft repositories. There is no Gradle version catalog, dependency-lock state, dependency-verification metadata, exact JDK distribution, or wrapper-distribution checksum in the repository. Consequently, the table above records every exact version deliberately declared by the build, but it is not a lock of every transitive artifact selected by Gradle and Loom.
