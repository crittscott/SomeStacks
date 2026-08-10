# Transitioning Some Stacks to Forge + Fabric with Architectury API

## Purpose

This document is the migration brief for converting **Some Stacks** — currently a single-module
Minecraft 1.20.1, Forge 47.4.0 mod built with ForgeGradle 6.x, mod id `somestacks`, root package
`com.github.crittscott.somestacks` — into a three-module Forge/Fabric project:

```text
common/   shared source and resources
forge/    Forge build and loader-specific entrypoint
fabric/   Fabric build and loader-specific entrypoint
```

The target is **Forge and Fabric only**. Architectury is used as a small cross-loader runtime API;
**Architectury Loom is not used**. NeoForge and Quilt are not targets. Moving the Forge module from
ForgeGradle to ModDevGradle LegacyForge (see "Final toolchain" below) is itself part of this
migration, not a pre-existing state to preserve.

Where a value is specific to Some Stacks, it is given directly — including the multiloader toolchain
versions in "Final toolchain" below, reused unchanged from a sibling mod's (Some Buckets') working
Forge/Fabric build on this exact Minecraft version rather than guessed. Where a value is not yet
decided for Some Stacks, it is left as a placeholder; fill it in once chosen, and do the same for any
other mod this document is later adapted to.

## Git setup for long-lived Minecraft-version branches

### Intended branch model

Treat every supported Minecraft version as an independent, long-lived product line:

```text
main                 frozen historical Forge baseline
1.20.1               maintained Forge/Fabric build for Minecraft 1.20.1
1.21.1               maintained Forge/Fabric build for Minecraft 1.21.1
future-version       another branch created when that port begins
```

`main` is intentionally stale. Do not merge version branches back into it, and do not use it as an
integration branch. Its purpose is to retain the shared historical baseline from which the version
branches descended.

Some Stacks starts with two version branches, `1.20.1` and `1.21.1`, both created directly from
`main`. Its remote is `origin` (confirmed with `git remote -v`); today only `main` exists.
`<remote>` in the commands below means `origin`.

### One-time Git identity and safety setup

Check whether Git already knows the name and email to place on commits:

```powershell
git config --global user.name
git config --global user.email
```

If either is missing, set it once:

```powershell
git config --global user.name "Your Name"
git config --global user.email "your-address@example.com"
```

Make new repositories use `main`, and make `git pull` refuse to create an accidental merge commit:

```powershell
git config --global init.defaultBranch main
git config --global pull.ff only
```

The second setting means that a divergent pull stops and asks for a deliberate decision instead of
silently making the branch history more complicated.

### Preserve the original Forge baseline

Before starting the first multiloader conversion, make sure the working Forge state is committed:

```powershell
git status
git add <specific-files>
git diff --cached
git commit -m "Preserve working Forge baseline"
```

Tag that exact historical state so it remains easy to find even after branches move:

```powershell
git tag -a <minecraft-version>-forge-only -m "Working Forge-only baseline"
git push <remote> <minecraft-version>-forge-only
```

Do not reuse that tag name for a later release. Tags should identify permanent points in history.

### Create and publish the starting version branches

Start with a clean working tree:

```powershell
git status
```

Create both starting branches from the preserved baseline and publish them:

```powershell
git switch main
git switch -c 1.20.1
git push -u <remote> 1.20.1

git switch main
git switch -c 1.21.1
git push -u <remote> 1.21.1
```

`-u` records the upstream branch. After that, ordinary `git pull` and `git push` know which remote
branch to use.

If a branch already exists remotely but not locally, use:

```powershell
git fetch <remote>
git switch --track <remote>/1.20.1
```

Never run the branch-creation command again merely because a branch is not currently checked
out. List local and remote branches first:

```powershell
git branch -vv
git branch --all
```

After creation, `1.20.1` and `1.21.1` are independent. Commits made on either branch do not appear
on the other unless explicitly copied or merged.

### Create a later Minecraft-version branch

For a version added after the starting pair, normally begin from the most complete prior version
branch rather than from `main`, because that preserves the multiloader architecture and current
features. Make sure the older branch is clean and current, then branch from it:

```powershell
git switch <source-version>
git pull --ff-only
git switch -c <new-version>
git push -u <remote> <new-version>
```

Do not create `<new-version>` from stale `main` once a prior branch already has a completed
multiloader conversion; that would discard the conversion and force it to be rebuilt.

### Daily workflow on a version branch

Before editing, switch to the intended Minecraft version and confirm it:

```powershell
git switch 1.21.1
git status --short --branch
git pull --ff-only
```

After editing and testing:

```powershell
git status --short
git diff
git add <specific-files>
git diff --cached
git commit -m "Concise description of the completed change"
git push
```

Prefer `git add <specific-files>` over `git add .` while learning Git. Reviewing `git diff --cached`
before every commit prevents build outputs, run files, secrets, or unrelated edits from entering the
commit accidentally.

Commit only after the change represents a coherent checkpoint. Small, focused commits are easier
to copy to another version and easier to undo.

### Carry a fix between version branches

Do not routinely merge whole Minecraft-version branches into one another after they have diverged.
Minecraft API changes make such merges progressively noisier and can silently restore code meant
for the other version.

For a small fix that genuinely applies to both versions, commit and test it on one branch, note the
commit hash, and cherry-pick it onto the other:

```powershell
git log -1 --oneline
git switch 1.20.1
git pull --ff-only
git cherry-pick <commit-hash>
```

Resolve any conflicts in the context of the destination Minecraft version, then compile and test
that version before pushing:

```powershell
.\gradlew.bat :common:compileJava :forge:compileJava :fabric:compileJava
git push
```

If the implementation differs substantially between Minecraft versions, make a separate commit on
each branch instead of forcing a cherry-pick.

### Release tags

Create release tags on the version branch, with the Minecraft version in the tag name:

```powershell
git switch 1.20.1
git tag -a 1.20.1-v1.0.0 -m "Some Stacks 1.0.0 for Minecraft 1.20.1"
git push <remote> 1.20.1-v1.0.0
```

This avoids ambiguous tags such as `v1.0.0` when several Minecraft branches release the same mod
version.

### GitHub default branch versus `main`

A frozen `main` does not have to remain the GitHub default branch. Two workable policies are:

1. Make the newest supported Minecraft branch the GitHub default and change the default when a new
   version becomes primary.
2. Leave `main` as the default, but put a prominent notice in its README directing users to the
   maintained version branches.

The first policy is less confusing for visitors and pull requests. Either policy still permits
`main` itself to remain frozen.

### Safe recovery habits

When uncertain, stop and inspect before changing history:

```powershell
git status
git diff
git diff --cached
git branch -vv
git log --graph --decorate --oneline --all -20
```

If unfinished work prevents a branch switch, the simplest safe choice is usually to finish a small
checkpoint commit on the current branch. A named stash is also possible:

```powershell
git stash push -m "unfinished work before switching versions"
git stash list
```

Restore it only on the intended branch with `git stash pop`.

Until the consequences are fully understood, do not use:

- `git reset --hard`;
- `git clean -fd`;
- forced pushes;
- rebasing a branch that other machines or people may already use;
- broad restore/checkout commands intended to discard files.

Git commits, branches, tags, and the reflog make most ordinary mistakes recoverable. Destructive
commands and force-pushing are what usually turn a small mistake into data loss.

## Final toolchain

| Module | Build tooling | Runtime/API dependencies |
|---|---|---|
| `common` | ModDevGradle LegacyForge in MCP/common mode | Generic Architectury API at compile time |
| `forge` | ModDevGradle LegacyForge | Forge Architectury API |
| `fabric` | Fabric Loom | Fabric Loader, Fabric API, Fabric Architectury API |

`net.neoforged.moddev.legacyforge` is the Gradle plugin used to build **Forge** here. Its package
name does not mean that the project targets NeoForge.

Fabric Loom is still required to build the Fabric module. The excluded tool is
`architectury-loom`, not Fabric's own Loom plugin.

All three modules must use the same mappings. Some Stacks currently builds against Mojang official
mappings with Parchment `2023.09.03-1.20.1` via ForgeGradle's Parchment Librarian plugin; carry the
same official + Parchment `2023.09.03-1.20.1` layer into `common`, `forge`, and `fabric` so shared
Minecraft source (and any future shared mixins) stay portable.

### Confirmed compatible versions

These are confirmed working together on Minecraft 1.20.1 + Forge 47.4.0 — verified by a successful
Gradle sync and build of another mod's (Some Buckets') Forge/Fabric conversion on this exact
toolchain, not by inspection alone. That working `build.gradle`/`gradle.properties` set is checked
into this repo at `functional-gradle-files/` for direct comparison while adapting the steps below;
its `common/forge/fabric` build files are complete, but the `buildSrc` convention-plugin files they
depend on (`multiloader-common.gradle`, `multiloader-loader.gradle` behind the `id
'multiloader-common'`/`id 'multiloader-loader'` plugin ids) are not present in that directory, so
step 4's convention-plugin content below is templated guidance, not yet verified line-for-line
against the working reference. Obtain those `buildSrc` files from the Some Buckets repository before
treating step 4 as confirmed, or write and verify them fresh for Some Stacks.

| Property (`gradle.properties` unless noted) | Value |
| --- | --- |
| Gradle wrapper (`gradle/wrapper/gradle-wrapper.properties`) | `8.11` |
| `moddevgradle_legacyforge_version` | `2.0.77` |
| `fabric_loom_version` | `1.9.2` |
| `fabric_loader_version` | `0.16.9` |
| `fabric_version` (Fabric API) | `0.92.2+1.20.1` |
| `architectury_api_version` | `9.2.14` |
| `architectury_version_range` (for `mods.toml`'s dependency `versionRange`) | `[9.2.14,)` |
| `org.gradle.toolchains.foojay-resolver-convention` (`settings.gradle` plugin) | `0.8.0` |

`fabric-loom 1.9.2` specifically requires Gradle 8.11 or newer. A ForgeGradle-era wrapper (Some
Stacks currently ships 8.8, and so did Some Buckets' pre-conversion baseline) fails Gradle's own
plugin-classpath resolution before either loader plugin runs, with an error naming the required
minimum version — see "Bump the Gradle wrapper" below.

## Non-negotiable design decisions

1. Loader-neutral code belongs in `common`.
2. Forge and Fabric entrypoints and event registration belong in their loader modules.
3. Common Java and resources are compiled directly into each loader jar; the common jar is not a
   distributable mod.
4. Do not add `implementation project(":common")` to Fabric or another redundant project
   dependency to Forge. The shared loader convention already wires in `common`.
5. Architectury API is a mandatory runtime dependency because common code directly calls it.
6. Forge-only access widening cannot be relied upon by common or Fabric code. Use a shared mixin
   accessor when common code needs a private Minecraft member.
7. Networking uses each loader's native API — Forge `SimpleChannel` on `forge`, Fabric
   `ServerPlayNetworking`/`ClientPlayNetworking` on `fabric` — sharing only packet codecs and
   server-side validation logic from `common`. There is no cross-loader networking library
   dependency; see "Networking across loaders" below.
8. Server configuration is a hand-rolled, loader-neutral reader/writer in `common`, not
   `ForgeConfigSpec` and not a third-party config library. Forge's automatic file-watch hot-reload of
   the server TOML is a documented Forge-only convenience, not reproduced on Fabric; see "Server
   configuration across loaders" below.
9. Forge's `IItemHandler` capability view classes (`*ColumnHandler`, `PileItemHandler`) remain
   Forge-only. Fabric gets its own Transfer API adapter as new code, not a mechanical port; see
   "Item-handler capability and automation" below.
10. `Protection`'s re-firing of Forge events for claim/logging-mod compatibility is Forge-only.
    Fabric ships without an equivalent integration at first; this is a documented, accepted gap, not
    a blocking requirement — see "Protection and cross-loader parity" below.

## Recommended project layout

```text
build.gradle
settings.gradle
gradle.properties
buildSrc/
  build.gradle
  src/main/groovy/
    multiloader-common.gradle
    multiloader-loader.gradle
common/
  build.gradle
  src/main/java/
  src/main/resources/
    <mod-id>.mixins.json
    pack.mcmeta
forge/
  build.gradle
  src/main/java/          Forge entrypoint and Forge-only glue
  src/main/resources/
    META-INF/mods.toml
fabric/
  build.gradle
  src/main/java/          Fabric entrypoint and Fabric-only glue
  src/main/resources/
    fabric.mod.json
```

## Migration sequence

### 1. Preserve a working Forge baseline

Before restructuring, tag or commit the working pure-Forge project. Keep it available for
behavioral comparisons. A Gradle sync after restructuring proves only that Gradle can configure
the projects; it does not prove that Java compiles or that either loader starts.

### 2. Bump the Gradle wrapper

Do this before the first sync attempt, not after chasing a confusing failure. `fabric-loom 1.9.2`
requires Gradle 8.11 or newer; a ForgeGradle-era wrapper is typically far older. Edit
`gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11-bin.zip
```

`gradlew`/`gradlew.bat` do not need to change — they just bootstrap whatever version this file points
to. If the wrapper is too old, Gradle fails while resolving the plugin classpath itself, before either
loader plugin or any project build script runs, with an error naming the minimum Gradle version the
Fabric Loom version requires.

### 3. Establish root plugin and repository management

`settings.gradle` should include only the intended modules:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { name = 'Forge'; url = 'https://maven.minecraftforge.net/' }
        maven { name = 'ModDevGradle'; url = 'https://maven.neoforged.net/releases' }
        maven { name = 'Fabric'; url = 'https://maven.fabricmc.net/' }
        maven { name = 'Sponge Snapshots'; url = 'https://repo.spongepowered.org/repository/maven-public/' }
    }
}

rootProject.name = '<mod-id>'
include 'common'
include 'forge'
include 'fabric'
```

The root `build.gradle` declares, but does not apply, the loader build plugins:

```groovy
plugins {
    id 'fabric-loom' version "${fabric_loom_version}" apply(false)
    id 'net.neoforged.moddev.legacyforge' version "${moddevgradle_legacyforge_version}" apply(false)
}
```

Centralize compatible Minecraft, mapping, loader, API, and optional-integration versions in
`gradle.properties`. Do not assume that plugin or library versions from one Minecraft branch work
on another.

**`pluginManagement.repositories` only resolves Gradle plugins, not regular dependencies.** A
Maven repository listed there — Sponge's snapshot repository above is the recurring example, needed
for `org.spongepowered:mixin:<version>-SNAPSHOT:processor` — is invisible to a module's own
`dependencies {}` block. Any repository an actual compile-time or runtime dependency needs must be
added again to the project-level `repositories {}` blocks in step 4, even though it looks redundant
next to the identical URL already in `settings.gradle`. Missing this produces a
`Could not find <group>:<artifact>:<version>` resolution failure that lists several repositories it
searched, none of which is the one that was only declared for plugin resolution.

### 4. Create shared Gradle conventions

The common convention plugin should provide ordinary Java-library configuration, repositories,
Java toolchain configuration, and resource expansion.

The loader convention plugin (applied to both `forge` and `fabric`) sets shared project identity, adds
any repository an optional integration or Architectury needs, and wires `common`'s source and
resources into the loader's own compilation:

```groovy
version = rootProject.property('mod_version')
group = rootProject.property('mod_group_id')

base {
    archivesName = "${rootProject.property('mod_id')}-${project.name}"
}

repositories {
    // Not implied by the Forge/Fabric loader plugins for regular dependency resolution — only
    // the common convention plugin (step 4's other half, for `common/build.gradle`) declares this
    // by default. Small compile-only utility libraries (see "Common compile-time-only annotations"
    // below) need it here too.
    mavenCentral()
    maven { url = 'https://maven.architectury.dev/' }
    // Add every other optional-integration repository every loader module needs here, once,
    // instead of duplicating it in forge/build.gradle and fabric/build.gradle separately. The
    // old single-module build.gradle had these in its one repositories{} block; splitting into
    // modules is an easy place to silently drop them, which fails dependency resolution during
    // sync on any modCompileOnly/modImplementation line that needs them.
}

configurations {
    commonJava { canBeResolved = true }
    commonResources { canBeResolved = true }
}

dependencies {
    commonJava project(path: ':common', configuration: 'commonJava')
    commonResources project(path: ':common', configuration: 'commonResources')
}

tasks.named('compileJava', JavaCompile) {
    dependsOn(configurations.commonJava)
    source(configurations.commonJava)
}

processResources {
    dependsOn(configurations.commonResources)
    from(configurations.commonResources)
}

tasks.matching { it.name == 'sourcesJar' }.configureEach {
    from(configurations.commonJava)
}
```

**Plugin order matters.** `base { archivesName = ... }` needs the `base` extension, which the loader
plugin (`fabric-loom` or `net.neoforged.moddev.legacyforge`) brings in in the course of applying the
Java plugin — the convention plugin does not provide it itself. Apply the loader plugin *before* this
convention plugin in each loader module's `plugins {}` block (see steps 6 and 7 below). Applying them
in the other order fails with `Could not find method base() ... on project ':forge'` (or `:fabric`),
because the convention script's `base {}` block runs before anything has applied the Java plugin.

An earlier version of this loader convention also declared `compileOnly(project(':common')) {
capabilities { requireCapability "$group:$mod_id" } }`, intended to pull `common`'s own compile-only
dependencies (like Architectury) into the loader module transitively. Drop it: it requires `common` to
publish a matching capability that nothing here declares, and it is redundant besides — every loader
module already redeclares its own optional dependencies directly (see "Optional integrations" below).
A sync with this block removed and nothing put in its place still succeeds.

`common` itself exposes its source directories as consumable artifacts. This lives directly in
`common/build.gradle`, not in the shared common convention plugin, since only `common` needs it:

```groovy
configurations {
    commonJava {
        canBeResolved = false
        canBeConsumed = true
    }
    commonResources {
        canBeResolved = false
        canBeConsumed = true
    }
}

artifacts {
    commonJava sourceSets.main.java.sourceDirectories.singleFile
    commonResources sourceSets.main.resources.sourceDirectories.singleFile
}
```

This project compiles common source into each loader rather than embedding or shipping a separate
common jar. Consequently, adding `implementation project(":common")` creates an unnecessary
runtime common jar and risks duplicate classes.

### 5. Configure the common module

For Some Stacks, with `minecraft_version=1.20.1`, `parchment_minecraft=1.20.1`, and
`parchment_version=2023.09.03` in `gradle.properties`:

```groovy
plugins {
    id 'multiloader-common'
    id 'net.neoforged.moddev.legacyforge'
}

legacyForge {
    mcpVersion = minecraft_version
    parchment {
        minecraftVersion = parchment_minecraft
        mappingsVersion = parchment_version
    }
}

dependencies {
    compileOnly 'org.spongepowered:mixin:0.8.5'
    modCompileOnly "dev.architectury:architectury:${architectury_api_version}"
}
```

The common module still needs a mapped Minecraft compile environment. It is not a plain Java
module. Applying LegacyForge in `mcpVersion` mode supplies that environment without turning
`common` into a distributable Forge mod.

Some Stacks has no mixins today, so the `org.spongepowered:mixin` compile dependency has nothing to
back it yet. Include it anyway — it costs nothing until a mixin class exists — rather than adding it
reactively later once a private-member access problem (see "Mixins and private Minecraft members"
below) forces the issue.

### 6. Configure Forge

The Forge module applies LegacyForge first, then the shared loader convention (see the plugin-order
note in step 4):

```groovy
plugins {
    id 'net.neoforged.moddev.legacyforge'
    id 'multiloader-loader'
}

legacyForge {
    version = "${minecraft_version}-${forge_version}"
    parchment {
        minecraftVersion = parchment_minecraft
        mappingsVersion = parchment_version
    }
    runs {
        client { client() }
        server { server() }
    }
    mods {
        "${mod_id}" { sourceSet sourceSets.main }
    }
}

dependencies {
    modImplementation "dev.architectury:architectury-forge:${architectury_api_version}"
    annotationProcessor 'org.spongepowered:mixin:0.8.5-SNAPSHOT:processor'
}

jar {
    finalizedBy('reobfJar')
    manifest.attributes(['MixinConfigs': "${mod_id}.mixins.json"])
}
```

The `client`/`server` run pair above and the `finalizedBy('reobfJar')` line are confirmed by the
working Some Buckets reference (`functional-gradle-files/forge/build.gradle`). Some Stacks' current
single-module `build.gradle` also configures `gameTestServer` and `data` (datagen) runs — the Some
Buckets reference build never exercised either of those under ModDevGradle LegacyForge, so treat
carrying them forward as unverified until tried, not as a confirmed step; see "Verification order"
and consult https://docs.neoforged.net/toolchain/docs/plugins/mdg/ first. Some Stacks' current jar
excludes (`gametest/**`, `PacketBoundaryGameTests*`, the generated empty-template NBT) also need to
move into this module's `jar {}` block — the reference build carries the equivalent
Some-Buckets-specific excludes here, confirming that this exclusion list stays with `forge`, not the
shared convention.

Do not add another `compileOnly project(":common")`; the shared loader convention already does
that.

### 7. Configure Fabric

The Fabric module applies Fabric Loom first, then the shared loader convention (see the plugin-order
note in step 4):

```groovy
plugins {
    id 'fabric-loom'
    id 'multiloader-loader'
}

dependencies {
    minecraft "com.mojang:minecraft:${minecraft_version}"
    mappings loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${parchment_minecraft}:${parchment_version}@zip")
    }
    modImplementation "net.fabricmc:fabric-loader:${fabric_loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${fabric_version}"
    modImplementation "dev.architectury:architectury-fabric:${architectury_api_version}"
}

loom {
    mixin {
        defaultRefmapName.set("${mod_id}.refmap.json")
    }
    runs {
        client {
            client()
            runDir('run')
        }
        server {
            server()
            runDir('run')
        }
    }
}
```

The `server` run above is confirmed by the working Some Buckets reference
(`functional-gradle-files/fabric/build.gradle`) — worth keeping since Some Stacks is required on the
server, not just the client.

Do not add `implementation project(":common")`. Common source and resources already enter the
Fabric compilation through `multiloader-loader`.

## Moving the Java code

Audit every import before moving a class.

Move to `common` when a class uses:

- Java APIs;
- vanilla `net.minecraft` APIs available under the shared mappings;
- Architectury APIs deliberately chosen as the cross-loader abstraction;
- optional-library APIs that are correctly isolated and supplied compile-only.

Keep in a loader module when a class uses:

- Forge or Fabric entrypoint annotations/interfaces;
- loader event buses or callbacks;
- loader-specific client command APIs;
- loader-only registration, networking, configuration, or lifecycle APIs.

Replace simple Forge platform calls directly with Architectury API where possible:

```java
Platform.isModLoaded("some_mod")
Platform.getGameFolder()
```

These replace common uses of Forge `ModList.isLoaded(...)` and `FMLPaths.GAMEDIR`. A custom
`@ExpectPlatform` bridge is unnecessary when Architectury API already provides the operation.

Do not move code mechanically merely because it compiled under Forge. Forge may patch Minecraft
visibility or behavior in ways that a neutral/common Minecraft artifact and Fabric do not share.

### Some Stacks package-by-package starting point

This is a first-pass classification of Some Stacks' current packages and central classes (see
`as-built.md`'s code map for what each one owns), based on the Forge-specific APIs each is already
known to use. It is a starting point for the file-by-file audit above, not a substitute for it — a
package listed as a common candidate can still contain individual classes that need to move or
split. It also assumes the networking, config, capability, and protection decisions recorded above
and detailed in the sections that follow this table.

| Package/class | Known Forge-specific dependency | Starting classification |
| --- | --- | --- |
| `SomeStacks`, `ModRegistry` | `@Mod`, mod event bus, `DeferredRegister`/`RegistryObject`, `DistExecutor`, `ModLoadingContext` | Loader-specific entrypoint/registration glue |
| `ServerConfig`, `ServerOverridesLoader` | `ForgeConfigSpec`, `ModConfigEvent` file-watch reload | Loader-specific today; moves to a common hand-rolled reader/writer (see "Server configuration across loaders") |
| `network/` (`ModNetworking`, the nine packet classes) | Forge `SimpleChannel`, `NetworkRegistry`, `PacketDistributor` | Channel wiring stays loader-specific per loader's own networking API; packet payloads/codecs and server-side validation logic are common candidates (see "Networking across loaders") |
| `block/*StackBlock` | Vanilla waterlogging/shape/comparator APIs | Common candidate |
| `block/*StackBE` | `ForgeCapabilities.ITEM_HANDLER` capability registration | Persistence/NBT and local slot logic are common candidates; capability exposure is loader-specific |
| `block/StoragePile`, `block/SinglesColumn`, `block/BarColumn` | Growth is attributed via `FakePlayer` through `server.Protection` | Core run logic (settlement, gravity, support) is a common candidate; audit the `FakePlayer`-attributed growth call at the seam with `Protection` |
| `block/*ColumnHandler`, `block/PileItemHandler` | Forge `IItemHandler` | Loader-specific capability view; Fabric needs its own Transfer API adapter as new code (see "Item-handler capability and automation") |
| `util/StorageCubeIdx`, `SinglesCubeIdx`, `BarCubeIdx`, `ViewRay`, `ItemOps`, `BlockType` | None found; vanilla/Java geometry and item utilities | Common candidates |
| `client/interaction/` | Client-only gesture recognition | Likely common candidate; audit each rule class for incidental Forge input-API imports |
| `client/KeyMappings` | `net.minecraftforge.client.settings.KeyConflictContext` | Loader-specific; Fabric registers a plain vanilla `KeyMapping` via `KeyBindingHelper` |
| `client/ClientSetup`, `client/ClientEvents` | Forge client registration events (BER, key mapping, color handler registration) | Registration is loader-specific; behavior they call can still be common |
| `client/*BER`, `CubeRenderHelper` | Vanilla `BlockEntityRenderer` | Common candidate |
| `client/ItemRenderOverrides`, `client/measure/` | Mirrors Forge item rendering (FIXED/GUI context, custom renderers) | Needs explicit audit — Forge and Fabric item-rendering pipelines diverge; may not be a clean common candidate |
| `client/BarTextureStore` | Resource-pack JSON, vanilla item color registry | Likely common candidate |
| `server/Protection` | `ForgeHooks`, `ForgeEventFactory`, `FakePlayerFactory`, `PlayerInteractEvent`, `BlockEvent`, `MinecraftForge.EVENT_BUS` | Loader-specific by construction; Forge-only for now (see "Protection and cross-loader parity") |
| `server/RightClickBlockSuppressor`, `server/GestureThrottle` | Forge event listener | Loader-specific; logic can be common |
| `server/StackSoundData`, `server/StackSounds` | Data-pack driven, vanilla `PreparableReloadListener` | Common candidate |
| `command/SsCommand`, `command/RenderGalleryGenerator` | Registers on vanilla `CommandDispatcher<CommandSourceStack>` — not a client command | Strong common candidate; only the `RegisterCommandsEvent`/`CommandRegistrationCallback` registration call site is loader-specific |
| `gametest/` | Forge's GameTest integration; already excluded from the release jar | Lowest priority — leave Forge-only unless dual-loader test coverage is explicitly wanted |
| `src/test` (JUnit) | None found | Move to `common`'s test source set |

### Networking across loaders

Some Stacks' `network/ModNetworking` is a single Forge `SimpleChannel` registering nine messages in
strict positional id order, with a two-sided exact protocol-version string match (`"2"`). There is no
cross-loader networking library dependency here: `forge` keeps `SimpleChannel`, and `fabric` gets its
own registration using Fabric API's `ServerPlayNetworking`/`ClientPlayNetworking`.

Move to `common`:

- each packet's field data and its `encode`/`decode` codec logic;
- the server-side validation shared by every C2S handler (sender reality check, one-gesture-per-tick,
  spectator rejection, reach/loaded-target checks, and each packet's own hand/block/index/adjacency
  rules).

Keep loader-specific:

- channel/message registration itself (`SimpleChannel.registerMessage` vs. Fabric's payload
  registration and `ServerPlayNetworking.registerGlobalReceiver`/`ClientPlayNetworking.registerGlobalReceiver`);
- the send call sites (`CHANNEL.send(PacketDistributor...)` vs. Fabric's `ServerPlayNetworking.send`/
  `ClientPlayNetworking.send`).

`SimpleChannel`'s strict two-sided protocol-version match has no drop-in Fabric equivalent. If that
exact-match behavior is still wanted, add a small explicit handshake (e.g., a version packet checked
on login) rather than assuming Fabric's own loader/mod version negotiation covers it.

### Server configuration across loaders

`ServerConfig`/`ServerOverridesLoader` use `ForgeConfigSpec`, which has no Fabric equivalent, and
Architectury does not ship a config API. The chosen approach is a small, loader-neutral
reader/writer in `common` — not `ForgeConfigSpec`, not a third-party config library — that both
loaders call from their own lifecycle hooks to load, save, and rebake the same values `ServerConfig`
owns today (max pile height, the three stack-type enable flags, `disable_mods`/`disable_items`,
`ingot_tags`, and the render-gallery settings).

`ModConfigEvent`'s automatic file-watch hot-reload of the server TOML is a Forge-specific convenience
and is not reproduced on Fabric; on both loaders the supported reload path stays what it already is
for the override JSON files: an explicit `/ss reload`. Forge's own config-file watcher can still be
left in place as an additional, Forge-only convenience on top of the shared reader/writer.

The list-editing commands (`/ss deny`, `/ss ingot`, `/ss gen`) write through this common
representation and immediately rebake the lookup sets, the same as they do today through the Forge
config value.

### Item-handler capability and automation

Forge's `IItemHandler` capability is Some Stacks' central automation feature — it spans every block
entity, the `*ColumnHandler`/`PileItemHandler` view classes, and the run-mutation logic on
`StoragePile`/`SinglesColumn`/`BarColumn`. This is a materially bigger seam than a typical single
fluid-handler class family: expect it to be the largest single piece of new code in the whole port,
not a mechanical move, and to be scoped and estimated on its own before the mechanical porting steps
begin.

Keep in `common`: the imperative run-mutation methods themselves (insert/extract-by-position,
growth, settlement, gravity, support checks) on `StoragePile`, `SinglesColumn`, and `BarColumn`,
independent of any capability type.

Keep loader-specific: the capability *view* over those methods. `forge` keeps
`ForgeCapabilities.ITEM_HANDLER` and the existing `*ColumnHandler`/`PileItemHandler` classes.
`fabric` needs a new adapter per stack type built on Fabric's Transfer API (`Storage<ItemVariant>`
and its transactional `insert`/`extract`), matching the same slot semantics documented in
`as-built.md` (Storage: 27-per-block ordinary slots; Singles/Bar: one physical position per handler
slot) but expressed through Fabric's transaction model instead of Forge's simulate/insert/extract
calls.

### Protection and cross-loader parity

`server/Protection` deliberately re-fires vanilla/Forge events (`RightClickBlock`,
`EntityPlaceEvent`, `BreakEvent`, world border, spawn protection) after every custom-packet edit, so
Forge claim and logging mods that hook those standard events observe and can refuse Some Stacks'
world edits exactly as they would a vanilla interaction. This is the mod's primary third-party
compatibility mechanism, and it is Forge-specific by construction — Fabric has no single standard
event bus that claim mods generally hook.

The accepted approach: `Protection` and its event re-firing stay Forge-only. Fabric ships without an
equivalent claim/logging-mod integration at first. This is a documented, known compatibility gap, not
a blocker on the rest of the port — world border, spawn protection, and build-height checks (which
are vanilla, not Forge-specific) still apply on both loaders. Revisit Fabric protection-mod
integration as a separate, later increment if it turns out to matter to players.

## Entrypoints and client commands

Entrypoints remain loader-specific:

- Forge uses `@Mod` and Forge event registration.
- Fabric uses `ClientModInitializer` and Fabric callbacks.

Client command dispatchers are also different. Forge's client command event uses a dispatcher
typed around vanilla `CommandSourceStack`; Fabric API uses `FabricClientCommandSource` and
`ClientCommandManager`. A single shared Brigadier tree may therefore fail on generic types even
when the command behavior is identical.

Keep the actual work in common methods, but build a thin command tree and feedback adapter in each
loader module when the source types differ.

None of this applies to Some Stacks' `/ss` command today: `SsCommand` registers on the plain vanilla
`CommandDispatcher<CommandSourceStack>` that both Forge's `RegisterCommandsEvent` and Fabric's
`CommandRegistrationCallback` hand you, because it is an ordinary server command, not a client
command. The whole command tree can therefore move to `common` as-is; only the one-line registration
call site differs per loader. Apply the dispatcher-mismatch guidance above only if a client-only
command is added later.

For a client-only mod:

- set Fabric's `"environment"` to `"client"`;
- ensure Forge client event classes are registered only on the client distribution;
- classify client-only mixins as client mixins if dedicated-server compatibility matters.

## Mixins and private Minecraft members

Put the shared mixin configuration in `common/src/main/resources`. The loader convention copies it
into both loader resource outputs.

The Forge module must configure its refmap and annotation processor. Fabric Loom must configure the
same refmap name. Both loader metadata files must reference the mixin configuration as required by
their loader.

Some Stacks currently has no mixins, no access transformer (the one in the ForgeGradle template is
commented out), and no reflection-based field access — an audit of `src/main/java` found none. This
class of bug is still worth watching for during the port, because it surfaces only when Forge's
compile environment happens to expose or widen a member that is private in the common/Fabric
environment. A known example of this failure mode: direct access to `EntityRenderDispatcher.renderers`
compiles under pure Forge but fails once code moves into the shared compile environment, where the
field is private. The portable fix is a shared mixin accessor:

```java
public interface EntityRenderDispatcherAccessor {
    Map<EntityType<?>, EntityRenderer<?>> getRenderers();
}
```

A mixin implements the accessor by shadowing the field, and common code casts the dispatcher to
the accessor. If moving a Some Stacks class into `common` turns up a similar private-member access
that only worked because of a Forge patch, use this same pattern for that field or method. A Forge
access transformer is not a cross-loader solution.

## Optional integrations

`compileOnly` or `modCompileOnly` does **not** make a library a hard runtime dependency. It makes
the API available to the Java compiler without bundling or requiring the mod at runtime.

Because each loader compiles the raw common sources, every compiling module must have the
appropriate optional API on its own compile classpath. Some Stacks has no optional integrations
today — no `ModList`/`FMLPaths` compatibility checks and no compat-style package exist in the
current codebase (`server/Protection`'s Forge-event re-firing is a generic compatibility mechanism,
not an integration with a specific mod; see "Protection and cross-loader parity"). This section is
therefore reference guidance for if/when one is added, not a worked example against existing code.

If an optional integration is added later, declare it only on the modules that import its API:

```groovy
// forge/build.gradle
modCompileOnly "<group>:<artifact>-forge:${some_integration_version}"

// fabric/build.gradle
modCompileOnly "<group>:<artifact>-fabric:${some_integration_version}"
```

The two loader declarations are intentional: `forge` and `fabric` are separate Java compilations.
Verify both artifacts exist for the target Minecraft version and expose an equivalent API before
committing to a Fabric adapter.

Keep the runtime metadata optional:

- Forge: `mandatory=false`.
- Fabric: place it under `suggests`, not `depends`.

Runtime code must still guard optional integration. The Forge entrypoint would use Architectury's
loader check:

```java
if (Platform.isModLoaded("some_mod")) {
    // Enter the optional integration.
}
```

Avoid optional types in always-loaded class signatures, superclasses, static fields, or entrypoint
classes. Isolate direct imports in a bridge/extraction class that is touched only after the
`isModLoaded` guard, or use reflection when no compile-time dependency is desirable — a generic
registry in `common`, with the "which optional integrations to bootstrap" decision left in each
loader's entrypoint.

## Common compile-time-only annotations

Forge-originated Minecraft source commonly uses `javax.annotation.Nullable`/`@Nonnull` (JSR-305) for
null-checking; it is not a vanilla or Forge-specific package, but Forge's own toolchain (ModDevGradle
or, previously, ForgeGradle) resolves it transitively, so a Forge-only codebase rarely declares it
directly. Fabric Loom's dependency set does not include it, and — as with any optional
integration — only `common`'s *source* is pulled into `forge`/`fabric`, not `common`'s dependency
declarations, so each loader module must resolve it independently:

```groovy
// common/build.gradle: needed for common's own compilation
compileOnly 'com.google.code.findbugs:jsr305:3.0.2'

// forge/build.gradle: usually unnecessary — already resolves transitively through ModDevGradle
// fabric/build.gradle: needed; Fabric Loom does not provide it
compileOnly 'com.google.code.findbugs:jsr305:3.0.2'
```

This requires `mavenCentral()` on the resolving module's `repositories {}` (see step 4's loader
convention) — Fabric Loom's own default repositories do not include it either. The symptom without
this dependency is a Fabric-only compile failure reading `package javax.annotation does not exist`
followed by a cascade of `cannot find symbol: class Nullable` errors on every use, even though the
same source compiles cleanly for `common` and `forge`.

If a library has materially different APIs on Forge and Fabric, do not pretend that one shared
implementation is portable. Define a common interface and put its implementations in the loader
modules.

## Loader metadata

Architectury API is a real runtime dependency when common code directly calls `Platform`.

Fabric `fabric.mod.json` should contain dependencies equivalent to:

```json
"depends": {
  "fabricloader": ">=<compatible-version>",
  "minecraft": "<compatible-range>",
  "java": ">=<required-version>",
  "architectury": ">=<compatible-version>",
  "fabric-api": "*"
}
```

Forge `mods.toml` should include:

```toml
[[dependencies.<mod-id>]]
    modId="architectury"
    mandatory=true
    versionRange="[<compatible-version>,)"
    ordering="NONE"
    side="BOTH"
```

Parameterize these versions through resource expansion rather than maintaining unrelated hardcoded
copies. Include every placeholder in the `processResources` input-property map so Gradle correctly
invalidates the task when a version changes.

## Failure modes encountered

| Symptom | Cause | Resolution |
|---|---|---|
| `CONFIGURE FAILED`: `Plugin net.fabricmc:fabric-loom:X.Y.Z requires at least Gradle 8.11. This build uses Gradle 8.8` | The project's Gradle wrapper predates Fabric Loom's minimum supported Gradle version | Bump `distributionUrl` in `gradle/wrapper/gradle-wrapper.properties` (see step 2) |
| `Could not find method base() for arguments [...] on project ':forge'` (or `:fabric`) while applying `multiloader-loader` | The loader convention plugin (which calls `base { archivesName = ... }`) was applied before the loader plugin that provides the `base` extension | List the loader plugin (`net.neoforged.moddev.legacyforge` / `fabric-loom`) before `multiloader-loader` in that module's `plugins {}` block |
| `Could not resolve dev.architectury:architectury-forge:...` (or an FTB Chunks/other optional-integration coordinate) during sync | The old single-module `build.gradle`'s `repositories {}` block (e.g. `maven.architectury.dev`, `maven.ftb.dev/releases`) was not carried into the new per-module/convention repositories when splitting into `common`/`forge`/`fabric` | Add the missing repositories to the shared loader convention plugin (or per-module if not shared) — see step 4 |
| Gradle sync succeeds but compilation fails | Sync configures projects; it does not compile every source set | Run all three `compileJava` tasks explicitly |
| Common fails on a private Minecraft field | Forge had hidden a visibility problem through patches/access changes | Add a shared mixin accessor |
| Loader compilation cannot find optional-library classes | Loader compilation ingests common source but does not inherit common's compile-only dependencies | Add the platform's `modCompileOnly` dependency to each loader |
| Concern that `modCompileOnly` makes a mod mandatory | Compile classpath and runtime metadata were conflated | Keep `modCompileOnly`; mark metadata optional and guard at runtime |
| Forge shows `:common` twice | Both the convention plugin and `forge/build.gradle` add it | Remove the module-local duplicate |
| Fabric runtime contains a common project jar | `implementation project(":common")` was added despite source merging | Remove it; compile common source directly into Fabric |
| Fabric and Forge command code will not share | Their client command source types and builders differ | Share command behavior, not necessarily the command tree |
| Common resources are absent from loader output | Only Java source was wired between modules | Add `commonResources` and merge it in `processResources` |
| Mixins compile but fail at runtime | Mixin config/refmap/manifest wiring differs by loader | Configure Forge AP + manifest and Fabric Loom refmap; inspect final jars |
| Architectury classes are missing in a production instance | Platform artifact was on the development classpath but absent from metadata/install | Declare Architectury mandatory in both loader metadata files |
| A common module is treated as plain Java | It lacks a mapped Minecraft dependency | Give common a mapped Minecraft compile environment via ModDevGradle |
| Build files drift between Minecraft branches | Plugin/API/mapping versions were copied without compatibility checks | Resolve versions independently for each Minecraft branch |
| `Could not find <group>:<artifact>:<version>-SNAPSHOT` (commonly the Mixin annotation processor) even though the same URL appears in `settings.gradle` | The repository was declared only under `pluginManagement.repositories`, which resolves Gradle plugins, not project dependencies | Add the same repository to the project-level `repositories {}` block that needs it (step 4) — see the note there |
| `forge:compileJava` succeeds but `fabric:compileJava` fails on the same common source with `package javax.annotation does not exist` / `cannot find symbol: class Nullable` | `javax.annotation.Nullable`/`@Nonnull` (JSR-305), pervasive in Forge-originated source, resolves transitively through ModDevGradle on Forge but has no equivalent transitive path through Fabric Loom | Add `compileOnly 'com.google.code.findbugs:jsr305:3.0.2'` (or whichever version) to the Fabric module, and `mavenCentral()` to its resolving `repositories {}` if not already present — see "Common compile-time-only annotations" |

## Verification order

Use the wrapper from PowerShell on Windows.

Before any of the steps below, confirm a bare sync succeeds — `./gradlew help` or opening the project
in the IDE is enough. Sync failures (wrong Gradle wrapper version, plugin order, missing repositories)
are a distinct, earlier failure class from compilation failures, and are worth ruling out on their own
before moving any Java. See "Failure modes encountered" for the specific errors this produces.

First prove all Java compilations:

```powershell
.\gradlew.bat :common:compileJava :forge:compileJava :fabric:compileJava
```

Then prove resource processing:

```powershell
.\gradlew.bat :forge:processResources :fabric:processResources
```

Inspect these directories and confirm that metadata, `pack.mcmeta`, and the mixin configuration are
present and expanded:

```text
forge/build/resources/main/
fabric/build/resources/main/
```

Build both distributable jars:

```powershell
.\gradlew.bat :forge:build :fabric:build
```

Expected jar locations:

```text
forge/build/libs/<mod-id>-forge-<minecraft-version>-<mod-version>.jar
fabric/build/libs/<mod-id>-fabric-<minecraft-version>-<mod-version>.jar
```

Do not distribute the `-sources.jar` files or temporary Loom files.

Finally run both clients and both dedicated servers independently — Some Stacks requires the mod on
the server, and both server runs are part of the confirmed working reference build
(`functional-gradle-files/*/build.gradle`):

```powershell
.\gradlew.bat :forge:runClient
.\gradlew.bat :forge:runServer
.\gradlew.bat :fabric:runClient
.\gradlew.bat :fabric:runServer
```

Test at least:

- loader startup with Architectury API installed;
- startup without every optional integration;
- startup with each optional integration installed;
- command registration and feedback;
- mixin application without refmap errors;
- files/configuration/output paths;
- behavior comparison with the preserved pure-Forge baseline.

## Checklist for the Some Stacks migration

- [ ] Minecraft version decided: `1.20.1`, targeting Forge/Fabric only.
- [ ] Versions recorded: Forge `47.4.0`, official + Parchment `2023.09.03-1.20.1`, ModDevGradle
      LegacyForge `2.0.77`, Fabric Loom `1.9.2`, Fabric Loader `0.16.9`, Fabric API `0.92.2+1.20.1`,
      Architectury API `9.2.14` — reused unchanged from the confirmed compatible set (see "Confirmed
      compatible versions"); re-verify with a real sync once this mod's modules exist.
- [ ] Preserve a working Forge baseline (tag `1.20.1-forge-only` or similar) before restructuring.
- [ ] Bump the Gradle wrapper from `8.8` to `8.11` (required by Fabric Loom `1.9.2`).
- [ ] Create `common`, `forge`, and `fabric` modules.
- [ ] Add shared common/loader convention plugins, with the loader plugin applied before the loader
      convention plugin in each module's `plugins {}` block.
- [ ] Carry every non-default repository (Architectury, etc.) from the old single-module
      `build.gradle` into the shared conventions or per-module `repositories {}` blocks.
- [ ] Use identical Mojang + Parchment mappings everywhere.
- [ ] Confirm `./gradlew` syncs cleanly with all three modules empty of Java/resources, before moving
      any code.
- [ ] Decide and document the networking approach — per-loader native APIs (Forge `SimpleChannel`,
      Fabric `ServerPlayNetworking`/`ClientPlayNetworking`) sharing codecs and validation from
      `common` (see "Networking across loaders").
- [ ] Decide and document the config approach — a hand-rolled, loader-neutral reader/writer in
      `common` replacing `ForgeConfigSpec`, with Forge's file-watch hot-reload kept as a Forge-only
      convenience (see "Server configuration across loaders").
- [ ] Decide and document the item-handler/automation approach — common run-mutation methods on
      `StoragePile`/`SinglesColumn`/`BarColumn`, with `forge`'s existing `IItemHandler` view classes
      kept and a new Fabric Transfer API adapter built per stack type (see "Item-handler capability
      and automation").
- [ ] Decide and document Fabric protection/claim-mod parity — accepted as a known gap for the first
      Fabric release; `Protection`'s Forge-event re-firing stays Forge-only (see "Protection and
      cross-loader parity").
- [ ] Move loader-neutral code into `common` per the package table above — block state/shape classes,
      block entity persistence and local slot logic, `*Pile`/`*Column` run logic, the `util/`
      geometry and item classes, client interaction rules, BERs and `CubeRenderHelper`,
      `StackSoundData`/`StackSounds`, and the `/ss` command tree. Registration glue, config I/O,
      network channel wiring, capability views, key mapping registration, and `Protection` remain
      loader-owned. `gametest/` remains Forge-only.
- [ ] Create loader-specific entrypoints and event/callback registration — both entrypoints register
      blocks/block entities, config load/bake, login sync, reload listeners, command registration,
      and client lifecycle hooks (BER, key mapping, item color).
- [ ] Confirm no client-only command dispatcher split is needed — `/ss` registers on vanilla
      `CommandDispatcher<CommandSourceStack>` on both loaders, so this is not applicable unless a
      client-only command is added later.
- [ ] Replace Forge-only access widening used by common code with shared mixin accessors, if the
      audit turns any up — none are known today; no common code is expected to touch a private
      Minecraft member.
- [ ] Wire common resources into both loader outputs — shared assets/data, `pack.mcmeta`, and sounds
      are merged; loader-specific metadata (`mods.toml` vs. `fabric.mod.json`) stays split.
- [ ] Add Architectury platform artifacts and mandatory loader metadata — `forge/mods.toml` and the
      new `fabric/fabric.mod.json` both declare `architectury` as a mandatory dependency.
- [ ] Confirm there are no optional integrations to declare yet (none exist in the current codebase);
      revisit "Optional integrations" if one is added.
- [ ] Obtain or write and verify the `buildSrc` convention-plugin files (`multiloader-common.gradle`,
      `multiloader-loader.gradle`) — not present in `functional-gradle-files/`, so step 4's content is
      still templated guidance rather than a confirmed carry-over.
- [ ] Verify `gameTestServer` and `data` (datagen) run configuration under ModDevGradle LegacyForge —
      unlike the rest of the toolchain, this was never exercised by the Some Buckets reference build
      and Some Stacks depends on both (`gametest/`'s GameTest suite and the datagen `data` run).
- [ ] Remove redundant `project(":common")` dependencies — none should be introduced.
- [ ] Recompile all three modules explicitly after the functional Fabric port.
- [ ] Process and inspect both resource outputs.
- [ ] Build and inspect both final jars.
- [ ] Run and behavior-test both loader clients and both dedicated servers, including the Fabric
      protection-parity gap noted above.

## Instructions when importing this document into another conversation

Treat this document as the architectural baseline, not as permission to copy version numbers.
Before editing the target mod:

1. inspect its current Gradle files, source imports, resources, optional integrations, mixins, and
   loader hooks;
2. identify its Minecraft version and resolve compatible dependency/plugin versions;
3. produce a file-by-file migration map showing what belongs in `common`, `forge`, and `fabric`;
4. preserve optional behavior and avoid introducing new hard dependencies;
5. implement in checkpoints and run the verification sequence above after each structural stage.

Do not introduce Architectury Loom, NeoForge, Quilt, a separate distributable common jar, or
Forge-only access mechanisms in shared code unless the target project's requirements explicitly
change.
