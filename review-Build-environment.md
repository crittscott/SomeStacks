# Code Review: Build Environment — Completed

Scope: `settings.gradle`, root `build.gradle`, `gradle.properties`,
`common|fabric|forge|neoforge/build.gradle`, per-loader properties, the wrapper,
`.gitignore` / `.gitattributes`, `build-env/`, loader metadata templates, `pack.mcmeta`,
`build-env.md`, and build-facing claims in `README.md`.

This is the final reassessment after executing the remediation plan.

## Verdict

The build-environment findings are resolved. The active four-module Architectury Loom build remains
on Minecraft 1.21.1 and Java 21, produces Fabric, Forge, and NeoForge artifacts, and now has one
shared loader-build helper for the formerly duplicated mechanics. The retained `build-env/` tree is
a byte-for-byte snapshot of the active build files rather than a foreign mod skeleton.

No correctness issue from the review remains open. A few template features were deliberately kept
because they are valid supported workflows, and Windows/Dropbox can still cause a transient output
JAR lock; neither is a build-configuration defect.

## Resolution of the findings

| Original finding | Resolution |
| --- | --- |
| Foreign and inconsistently named `build-env/` | **Fixed.** The tree mirrors Some Stacks, including the regenerated wrapper, and the documentation consistently names and defines it. |
| Triplicated loader build logic | **Fixed.** Root `configureLoaderBuild` owns the common/shadow configurations, structure generation, generated GameTest resources, Shadow classifier, and remap input. Loader-specific transforms, metadata, dependencies, and runs remain local. |
| Stale `.gitattributes` rules | **Fixed.** The comment-only/stale file was removed because the referenced datagen tree does not exist. |
| `build-env.md` drift | **Fixed.** It documents the snapshot workflow, shared helper, uniform GameTest classpath, Fabric-only Mixin, repository scope, wrapper validation, pack formats, JDK discovery, and observed Dropbox lock behavior. |
| Asymmetric GameTest classpaths | **Fixed and tested.** All loaders explicitly include common main output at runtime. |
| Hardcoded GameTest TOML values | **Fixed.** Loader ranges and license are expanded from root properties; processed resources contain the expected concrete values. |
| Non-reproducible Forge/NeoForge timestamp | **Fixed.** The changing `Implementation-Timestamp` manifest entry was removed. |
| Old Forge Maven host | **Fixed.** Plugin resolution uses `https://maven.minecraftforge.net/`. |
| FTB repository on every module | **Fixed.** It is limited to Fabric and NeoForge, matching the dependency declarations. |
| Missing Fabric platform property | **Fixed.** `fabric/gradle.properties` explicitly declares `loom.platform=fabric`. |
| Wrapper validation keys absent | **Fixed.** The Gradle 9.5.1 wrapper was regenerated and now has URL validation plus retry settings. |
| One pack format for resources and data | **Fixed.** Minecraft 1.21.1 reports resource format 34 and data format 48; `pack.mcmeta` declares primary format 34 with `supported_formats: [34, 48]`. All loader GameTest servers accept it. |
| README build drift | **Fixed.** It names all three loaders, removes the Architectury API runtime claim, includes NeoForge automation, and removes obsolete Open Parties and Claims support. |
| Obsolete `.gitignore` alias | **Fixed.** The unused `/functional-gradle-files/` rule was removed. |

## Deliberate retention decisions

- `maven-publish` stays because it provides a valid `publishToMavenLocal` workflow even without a
  remote publishing repository.
- `idea`, `eclipse`, and sources JAR generation stay because the repository has both generated
  IntelliJ and Eclipse launch configurations, and sources artifacts remain useful to developers.
- `org.gradle.daemon=false` stays as an explicit repository policy. It is a performance tradeoff,
  not stale or incorrect configuration.
- GameTest world deletion and XML reporting were not added. All three suites repeatedly discover,
  execute, and terminate correctly with the existing world handling; adding destructive cleanup is
  not justified by a reproduced failure.

## Verification

- `.\gradlew wrapper --gradle-version 9.5.1`: passed.
- Fabric GameTest server: **74/74 required tests passed**.
- Forge GameTest server: **82/82 required tests passed**.
- NeoForge GameTest server: **82/82 required tests passed**.
- Fabric and NeoForge release tasks completed during the final aggregate build.
- Forge release build completed on isolated retry after Dropbox temporarily locked its output JAR.
- Processed Forge GameTest metadata contains loader range `[52,53)`, license `GNU GPLv3`, and
  dependency version `[0.8.0]`.
- Processed NeoForge GameTest metadata contains loader range `[1,)`, license `GNU GPLv3`, and
  dependency version `[0.8.0]`.
- Forge and NeoForge release manifests contain no `Implementation-Timestamp`.

## Snapshot maintenance rule

`build-env/` is a mirror, not a second build. Make and verify changes in the active root first,
then copy the corresponding build script, module property, or wrapper file into `build-env/` and
confirm byte equality.
