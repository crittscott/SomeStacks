# Code Review: Build Environment

Scope: `settings.gradle`, root `build.gradle`, `gradle.properties`, `common|fabric|forge|neoforge/build.gradle`,
per-loader `gradle.properties`, the wrapper, `.gitignore` / `.gitattributes`, the in-tree `build-env/`
tree, and the metadata templates the build expands. Assessed as the tree stands today.

## Verdict

The active four-module Architectury Loom setup is fundamentally sound and recognizably "the expected
way": Loom + `architectury-plugin` + `com.gradleup.shadow`, `common` transformed per loader through
`transformProductionX` / `shadowBundle`, `loom.mods.main` grouping for dev runs, Mojang + Parchment
layered mappings, Java 21 toolchain enforced everywhere. A modder would read it and nod.

But cruft has accumulated, and it is concentrated in three places: (1) an entire foreign mod's build
skeleton committed under `build-env/`, referenced by two other names elsewhere; (2) per-loader build
logic that has been copy-pasted into three files where a shared block would do — and where the
`build-env/` reference actually *did* factor it out; (3) stale `.gitattributes` / `.gitignore` /
`build-env.md` entries describing a layout that no longer exists.

## What is done well

- **`pack.mcmeta` expansion lives once in `common/build.gradle`**, with a comment explaining why it is
  not repeated per loader. Correct and deliberately DRY.
- **Loader metadata is placeholder-driven** from `gradle.properties` (`mods.toml`,
  `neoforge.mods.toml`, `fabric.mod.json`), so version bumps happen in one file.
- **`forge_compile_version` / `neoforge_compile_version` doubling as the runtime floor** is stated in
  comments and kept consistent with the `*_version_range` values.
- **Toolchain is pinned hard**: `--release 21`, `sourceCompatibility`/`targetCompatibility`, and a
  `JavaExec` launcher override so dev runs cannot drift onto another JDK. Plugins are pinned to exact
  versions, not snapshot aliases.
- **GameTest wiring is real**: decoded NBT fixture treated as build output from a checked-in Base64
  source, custom `gametest` source set registered as its own Loom mod so FML/Fabric actually
  discovers the tests. The `build-env.md` "environment traps" section is a genuinely useful capture
  of the non-obvious parts.
- **Per-loader FTB Chunks handling is internally consistent**: `modCompileOnly` declared only in
  `fabric` and `neoforge`, and `FtbChunksProtection.java` exists only in those two. Forge relies on
  its event bus and carries no FTB dependency — a reasonable, matched difference.

## Findings

### 1. `build-env/` is a second mod's build tree, committed, and misnamed in two other places

`build-env/` contains a complete Gradle skeleton — `settings.gradle`, root + four module
`build.gradle`, per-loader `gradle.properties`, `gradlew`, `gradlew.bat`, and a **binary
`gradle-wrapper.jar`** — for a *different* mod: `rootProject.name = 'somebuckets'`,
`maven_group = com.github.crittscott.somebuckets`, `archives_name = somebuckets`,
`mod_description = Get you some buckets!`, fixture path `common/src/gametest/fixtures/empty_9x6x9.nbt.b64`,
`Base64.getMimeDecoder()` instead of `getDecoder()`. It is tracked by git and inert (the root
`settings.gradle` never includes it).

It is referred to by three non-matching names:

| Location | Name used |
| --- | --- |
| actual directory (tracked) | `build-env/` |
| `.gitignore` line 35 | `/functional-gradle-files/` |
| `build-env.md` (×3, incl. the "traps" list) | `working-build-env/` |

So the `.gitignore` rule intended to keep this tree out of version control does not match it, and the
document that explains it points at a path that does not exist.

Worse, `build-env/` is not a clean "golden template" or an "old copy" — it has diverged from the
active build in *both* directions (see finding 2 and 4). A maintainer treating it as a reference
would copy `somebuckets` identifiers, the wrong fixture name, and `getMimeDecoder()` into the live
build.

Recommendation: decide what this tree is for. If it is a reference, it should be a single accurate
one (right mod name, right paths, no wrapper jar, one agreed directory name matching `.gitignore` and
`build-env.md`). If it is dead, delete it and the two stale references.

### 2. Per-loader build logic is triplicated where a shared block belongs

`fabric/build.gradle`, `forge/build.gradle`, and `neoforge/build.gradle` are ~95% identical. The
following blocks are byte-for-byte copies in all three:

- the `generateGameTestStructures` task registration (source path, output path, `doLast` decode);
- `sourceSets.gametest.resources { srcDir layout.buildDirectory.dir('generated/gametest/resources') }`;
- the `configurations { common { ... }; compileClasspath.extendsFrom common; shadowBundle { ... } }` block;
- `shadowJar { configurations = [...shadowBundle]; archiveClassifier = 'dev-shadow' }`;
- `remapJar { inputFile.set shadowJar.archiveFile }`.

Additionally the `shadowJar { manifest { attributes([...]) } }` block is identical between `forge`
and `neoforge`.

Only genuinely loader-specific bits remain: `architectury { forge()/fabric()/neoForge() }`, the
`runs.gameTestServer` template (`-Dfabric-api.gametest` vs `forge/neoforge.enabledGameTestNamespaces`),
the loader dependency line, the `transformProductionX` configuration name, and the
`processResources` / `processGametestResources` `filesMatching` target
(`fabric.mod.json` vs `META-INF/mods.toml` vs `META-INF/neoforge.mods.toml`).

The `build-env/` reference had the structure-generation logic factored into a root
`ext.configureGameTestStructures` closure called as `rootProject.configureGameTestStructures(project)`
from each loader. The active project inlined it into each of the three files. That is a regression
away from the shared form.

Recommendation: hoist the shared blocks into the root `subprojects { }` (or a
`subprojects { plugins.withId('com.gradleup.shadow') { ... } }` guard) — the structure task, the
`shadowBundle`/`common` configurations, the `dev-shadow` classifier, and the `remapJar` rewiring. It
removes roughly 40 duplicated lines and makes the actual per-loader differences legible.

### 3. `.gitattributes` describes a datagen tree that does not exist

```
src/generated/**/.cache/cache text eol=lf
src/generated/**/*.json text eol=lf
```

There is no `src/generated/` anywhere in the project, and `build-env.md` states there is no
data-generation run. This is leftover from a Forge MDK layout the project no longer uses. Harmless
but pure noise.

### 4. `build-env.md` drift

Beyond the `working-build-env/` naming error (finding 1):

- The document is the stated authority on "per-loader differences" but does not mention that **Fabric
  alone runs a Mixin** (`somestacks.mixins.json`, `MinecraftMixin`, referenced from
  `fabric.mod.json`) and therefore alone pulls in the Mixin annotation processor and refmap
  handling. `as-built.md` does note it; `build-env.md` should too, since it changes Fabric's build.
- It says the fabric gametest classpath work is symmetric with forge/neoforge ("configured ... the
  same way"). It is not — see finding 5.

### 5. GameTest classpath handling is half-ported across loaders

`fabric/build.gradle` gametest source set:

```
runtimeClasspath += sourceSets.main.runtimeClasspath + sourceSets.main.output +
        project(':common').sourceSets.main.output
```

`forge/build.gradle` and `neoforge/build.gradle` gametest source set:

```
runtimeClasspath += sourceSets.main.runtimeClasspath + sourceSets.main.output
```

Only Fabric adds `project(':common').sourceSets.main.output` explicitly (and the `build-env/`
reference adds it to Fabric's *compile* classpath as well). Note the `common` configuration only
feeds `compileClasspath` (`compileClasspath.extendsFrom common`), not `runtimeClasspath`, so for
Forge/NeoForge the common classes reach the gametest run only via the `loom.mods.main` grouping.
This asymmetry reads like a fix applied to Fabric where something broke and not revisited for the
other two. It should be confirmed intentional (and if so, commented) or made uniform.

The `build-env/` reference also deletes `run/world` before `runGameTestServer` and emits a
`gametest-report.xml`; the active build does neither. If the stale-world cleanup was dropped
deliberately, fine; if not, it is a real test-flakiness hazard.

### 6. GameTest metadata hardcodes values the main templates parameterize

`forge/src/gametest/resources/META-INF/mods.toml`:

```
loaderVersion="[52,53)"
license="GNU GPLv3"
```

`neoforge/src/gametest/resources/META-INF/neoforge.mods.toml`:

```
loaderVersion="[1,)"
license="GNU GPLv3"
```

The production `mods.toml` / `neoforge.mods.toml` use `${forge_loader_version_range}` /
`${neoforge_loader_version_range}` / `${mod_license}`. The dev-only gametest copies inline literals
instead, so a loader-range or license change silently leaves them stale. `processGametestResources`
already runs an `expand` on these files (for `${mod_version}`); adding the other three placeholders
is nearly free.

### 7. Non-reproducible jars, on two loaders only

`forge` and `neoforge` `shadowJar` manifests set:

```
'Implementation-Timestamp': new Date().format("yyyy-MM-dd'T'HH:mm:ssZ")
```

That makes every build produce a different artifact hash. Fabric's manifest has no such block. For
an unreleased mod this is low-stakes, but it is an unnecessary reproducibility break and another
forge/neoforge-only divergence with no stated reason.

### 8. Dead template configuration

- `maven-publish` is applied to every subproject with a `mavenJava` publication, including `common`
  (which `build-env.md` itself calls a non-runtime intermediate). No publish repository is
  configured and `build-env.md` says none is intended. The whole `publishing { }` block and the
  root `apply plugin: 'maven-publish'` are inert.
- Root `build.gradle` applies `eclipse` and `idea`; `forge/.eclipse/configurations/*.launch` files
  sit on disk. If Eclipse is not a supported dev environment (IntelliJ run configs and
  `.idea/gradle.xml` suggest it is not), this is stale.
- `withSourcesJar()` on `common` produces a sources jar for a module that is never published or
  shipped.

None of this breaks anything; it is MDK-template residue that adds surface area.

### 9. Smaller items

- **Forge Maven URL is the old one.** `settings.gradle` uses
  `https://files.minecraftforge.net/maven/`; the current host is `https://maven.minecraftforge.net/`.
  It resolves today via redirect but should be updated.
- **FTB Maven repo is added to all four subprojects** in the root `subprojects.repositories` block,
  though only `fabric` and `neoforge` declare an FTB dependency. Harmless, slightly wider than needed.
- **`fabric` has no `gradle.properties` with `loom.platform=fabric`.** `forge` and `neoforge` have
  one. Fabric is Loom's default so it works, but the asymmetry is a small readability cost.
- **`gradle/wrapper/gradle-wrapper.properties` lacks `validateDistributionUrl=true`** (and the
  `retries` / `retryBackOffMs` keys) that a newer `gradle wrapper` writes — the `build-env/` copy
  has them. Minor; regenerate the wrapper to pick up the checksum-validation line.
- **`org.gradle.daemon=false`** in the root `gradle.properties`. This is unusual for a dev
  environment and costs JVM startup on every invocation. If it is a workaround for a Loom/daemon
  interaction it deserves a comment; otherwise the daemon is the expected default.
- **`common/src/main/resources/pack.mcmeta` uses a single `pack_format` of 34** for a pack that
  carries data-pack content (structures, and whatever else common ships). 34 is the 1.21.1
  *resource*-pack number; data-pack is 48. Minecraft is lenient here and most mods get away with one
  number, but a `pack.mcmeta` with `pack` + an explicit `data`/`assets` overlay is the precise form.
- **README is stale about the build.** The badges say "Loaders: Fabric + Forge" and "Requires:
  Architectury API"; the project now has three loaders and (per `build-env.md` / `as-built.md`) no
  loader carries an Architectury API runtime dependency.

## Per-loader differences: are they reasonable?

| Difference | Reasonable? |
| --- | --- |
| Fabric-only `MinecraftMixin` (empty-hand air-click callback) | Yes — Fabric lacks the event Forge/NeoForge expose; the mixin lives in `fabric/` source, not `common/`. |
| FTB Chunks compile dep on `fabric` + `neoforge` only | Yes — matched by `FtbChunksProtection.java` presence; Forge uses its event bus. |
| Fabric redeclares `jsr305`; Forge/NeoForge do not | Yes, and it is commented. This is a real Loom/Architectury gap. |
| `mods.toml` (Forge) vs `neoforge.mods.toml` (NeoForge) schema, `mandatory` vs `type="required"` | Yes — these are the loaders' actual formats for 1.21.1. |
| GameTest registration: `@GameTestHolder` scan (Forge) vs `+@PrefixGameTestTemplate(false)` (NeoForge) vs `FabricGameTest` + `fabric-gametest` entrypoint list (Fabric) | Yes — dictated by each loader; the entrypoint list in `fabric/src/gametest/resources/fabric.mod.json` is a known maintenance point and is called out in `build-env.md`. |
| `shadowJar` manifest present on Forge/NeoForge, absent on Fabric | Not clearly justified — see finding 7. |
| Common output added to gametest classpath on Fabric only | Not clearly justified — see finding 5. |
| Structure-gen logic inlined per loader instead of shared | No — see finding 2. |

## Suggested priority

1. Resolve `build-env/` (finding 1) — it is the largest and most misleading chunk of cruft, and it
   drags `.gitignore` and `build-env.md` inconsistencies with it.
2. De-duplicate the loader scripts (finding 2), which also forces finding 5 and 7 into the open.
3. Parameterize the gametest tomls (finding 6) and delete the stale `.gitattributes` lines
   (finding 3).
4. Clean the template residue (finding 8) and the small items (finding 9) opportunistically.
