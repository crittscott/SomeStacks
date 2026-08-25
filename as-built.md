# Some Stacks As-Built Orientation

This guide identifies the code boundaries and invariants a maintainer should understand before
changing Some Stacks. It is deliberately selective. `player-view.md` describes observable
behavior; the code is authoritative when either document is wrong.

It should not contain history and it is not part of a conversation with the user. It should describe the code as it is. It is not a prose version of the code, it is an orientation.

This document describes what is, not necessarily what is desired. Do not take is to be a driving design document.

## Project shape

Some Stacks targets Minecraft 1.20.1 and Java 17, with Forge 47.4.10 and Fabric Loader 0.19.3 /
Fabric API 0.92.7+1.20.1 build baselines. Both loaders require Architectury API 9.2.14. The mod id
is `somestacks` and the root package is `com.github.crittscott.somestacks`. See
`build-env.md` for the complete toolchain, dependency constraints, and build commands.

The Fabric API baseline is currently lowered from 0.92.11+1.20.1 to 0.92.7+1.20.1 as an in-progress
test of compatibility with a modpack pinned to the older version. Revert `fabric_api_version` in
`gradle.properties` if the test does not pan out.

The implementation is split between three modules:

```text
common/   loader-neutral mechanics, rendering, commands, and shared resources
forge/    Forge registration, lifecycle, networking, gestures, and automation adapter
fabric/   Fabric registration, lifecycle, networking, gestures, mixin, and automation adapter
```

`common` is not a separate runtime artifact. Its Java and resources are included in both loader
JARs. Common source contains no Forge or Fabric imports; loader services enter through small seams
such as `CommonRegistry`, `EditAuthority`, networking callbacks, client gesture adapters, and the
loader-native automation adapters.

Release artifacts are loader-local:

```text
forge/build/libs/somestacks-forge-<version>.jar
fabric/build/libs/somestacks-fabric-<version>.jar
```

The root `build/libs` directory is not a current loader output. Each release JAR contains common
classes and resources plus its loader metadata. Fabric's remapped JAR also contains
`somestacks.mixins.json` and the expanded shared `pack.mcmeta`. Mixin string references are remapped
statically into the class files by Loom's `remapJar` step; the config declares no runtime refmap,
since none is produced or needed.

The mod registers three blocks and their block entity types, but no block items, menus, recipes, or
portable containers.

| Block | Block entity | Local storage | Purpose |
| --- | --- | ---: | --- |
| `storage_stack_block` | `stack_be` | 27 item stacks | Dense bulk storage |
| `singles_stack_block` | `singles_stack_be` | 64 single items | Supported 4 x 4 x 4 display grid |
| `bar_stack_block` | `bar_stack_be` | 64 single items | Eight alternating layers of bars |

## Code map

| Area | Responsibility |
| --- | --- |
| `common/.../block/*StackBlock` | Block state, shape, waterlogging, removal, comparators, and scheduled publication |
| `common/.../block/*StackBE` | Local inventory, NBT, rotations, ray-selected cells, and run caches |
| `StoragePile`, `SinglesColumn`, `BarColumn` | Run-wide growth, mutation, fill, cleanup, and structural rules |
| `StackItemStorage`, `SlotAccess` | Loader-neutral fixed-slot storage and automation contract |
| `util/*CubeIdx`, `ViewRay` | Cell geometry, rotation, support, seams, and targeting |
| `ServerConfig` | Per-world policy, deny sets, and resolved ingot membership |
| `WorldEdits`, `EditAuthority` | Shared world-edit mechanics and loader protection seam |
| Loader entry points and registries | Registry population, lifecycle wiring, and common service installation |
| Loader network packages | Native packet transport around common request validation and payloads |
| Forge capability / Fabric Transfer API adapters | Whole-run loader-native views over common storage |
| `common/.../client` | Block entity renderers, item profiles, model measurement, and bar textures |
| Loader client packages | Renderer registration, gestures, keys, client events, and packet transport |
| `SsCommand`, `RenderGalleryGenerator` | Administration, render-profile authoring, and galleries |

Each loader entry point registers the blocks and block entities, initializes native networking and
automation, and attaches lifecycle listeners. Both loaders install event-backed player and
automation authorities: Forge's fire Forge's own interaction/place/break events, Fabric's
`FabricPlayerEditAuthority` and `FabricEditAuthority` fire Fabric API's `UseBlockCallback` and
`PlayerBlockBreakEvents`. Each loader's registry adapter supplies the common registry handles
before common world objects are created.

## Loader integration

| Concern | Forge | Fabric |
| --- | --- | --- |
| Metadata | `META-INF/mods.toml` | `fabric.mod.json` and `somestacks.mixins.json` |
| Entrypoints | `SomeStacks`, with `ClientSetup` on the client | `SomeStacksFabric` and `SomeStacksFabricClient` |
| Networking | `SimpleChannel` | `ServerPlayNetworking` and `ClientPlayNetworking` |
| Protocol | Channel compatibility rejects a version mismatch | A versioned handshake channel gates all play packets |
| Gestures | Forge interaction and input events | Fabric interaction callbacks plus the air-click mixin |
| Rendering | Shared BERs through Forge registration and render seams | Shared BERs through Fabric registration and Renderer API-aware seams |
| Automation | Whole-run `IItemHandler` capabilities | Whole-run Transfer API `Storage<ItemVariant>` providers |
| Player protection | Vanilla checks plus Forge interaction/place events | Vanilla checks plus Fabric API `UseBlockCallback` |
| Automated edits | Vanilla checks plus Forge place/break events | Vanilla checks; removal fires Fabric API `PlayerBlockBreakEvents`; growth optionally consults FTB Chunks/OPAC |

Both builds are required on the client and server. The loaders own transport and callbacks, but
packet codecs, request handlers, gesture rules, rendering, commands, and storage mechanics remain
shared.

## Runtime model

The central abstraction is the maximal contiguous vertical run of one stack type. A block entity
owns only its local slots; pile and column objects coordinate operations that cross block
boundaries. Run resolution is cached for the current game tick and invalidated by structural
changes.

Block entities do not tick. Mutations schedule work on the bottom block of the run. That deferred
pass coalesces client updates, lighting, and comparator publication; Storage also settles during
the pass.

All blocks are waterloggable. Their block state stores a derived light value based on local
contents. Comparator strength is calculated over the entire run, so every block in a run reports
the same signal.

### Persistent state

Inventory data is stored by `StackItemStorage` in block entity NBT.

| Type | Saved data |
| --- | --- |
| Storage | `Items`, block `Rotation`, and `Permanent` |
| Singles | `Items`, block `Rotation`, and one `CubeRotations` entry per cell |
| Bar | `Items` |

Storage permanence belongs logically to the base block and is propagated during settlement.
Storage and Singles block rotations are local to each block. A Singles item's rotation moves with
the item through gravity and across block seams.

## Run behavior

### Storage

A Storage pile presents one flat inventory ordered from its base upward. Player deposits merge
compatible partial stacks and then use empty slots, growing the run when necessary. Settlement
groups exact stack identities, restores legal stack sizes, sorts the contents, and packs them
downward. Empty top blocks are removed unless the pile is permanent or removal is refused.

Player extraction targets one rendered cell and then schedules settlement. Breaking a block drops
only that block's local contents; any surviving runs settle independently.

### Singles

A Singles column is positional. Each occupied cell needs the cell immediately below it, with
rotation-aware mapping across block seams. Insertion accepts only the named empty, supported cell;
headroom slots may grow the column.

Extraction shifts the selected visual column down one layer while preserving unrelated gaps.
Cross-seam moves translate between the blocks' rotations and carry item rotation. Empty blocks are
removed only from the top. Breaking a Singles block does not collapse the column above it.

### Bar

A Bar column is also positional. Adjacent layers run along alternating axes, and a bar is supported
when its footprint overlaps a bar in the layer below.

Player extraction and block removal cascade through bars that lose support. Automation extraction
instead moves the topmost bar into the opened position, preserving the structure. Insertion accepts
only a named empty, supported position.

These distinctions are intentional: Storage behaves like a settling bag, Singles applies gravity
to one visible column, and Bar uses structural support.

## Admission and automation

Storage accepts ordinary nonempty items. Bar accepts items resolved from configured ingot tags;
Singles accepts allowed items that are not Bar items.

The two deny lists have different scope:

- `disable_mods` applies to player deposits and automated insertion.
- `disable_items` applies only to player deposits.

Policy changes do not invalidate existing contents. Internal settlement, gravity, and backfill must
continue to move stored items without reapplying admission rules.

Forge attaches an `IItemHandler` capability and Fabric registers a Transfer API
`Storage<ItemVariant>` for each common block entity. From any block, the loader-native view
represents the entire run plus one block of potential headroom when growth is allowed. Storage
slots accept normal stack sizes; Singles and Bar slots accept one item. Fabric stages mutations in
the caller's transaction and commits structural changes only when the outer transaction commits.
Singles and Bar allow one structural extraction position per Fabric transaction. `RunEdit`
prevents reentrant automation mutations.

Growth and automatic cleanup pass through `WorldEdits`. Both loaders apply build limits,
replaceability, obstruction, border, and spawn checks using an automation actor. Forge also fires
place and break events for claim and logging integrations, covering growth and removal alike.
Fabric fires the matching break event (`PlayerBlockBreakEvents`) for automation-driven removal, but
has no generic placement event; on growth, `FabricEditAuthority` instead consults FTB Chunks and
Open Parties and Claims directly, through `FtbChunksProtection` and `OpacProtection`, when either is
present as an optional compile-time dependency. Each mod's types are referenced only inside its own
compat class, gated on the mod being loaded, so a server running neither is unaffected and never
touches either mod's classes. No other Fabric claim mod is consulted.

## Player interaction and networking

The shared client gesture rules recognize permanence, block rotation, item rotation, deposit,
placement, and extraction in that order. Loader event glue supplies clicks, keys, and native packet
transport. Placement mode is client state sent with a placement request; Fabric's air-click mixin
captures the modifier gesture that has no equivalent Fabric API callback.

The server treats every client message as a request. Common checks cover sender state, per-tick
gesture pacing, loaded chunks, and reach; each operation then validates its hand, target, index,
support, and edit permissions. Placement and its first deposit are one transaction.

Deposit and placement recompute ray targets on the server. Extraction and Singles item rotation use
a client-supplied cell index, which is checked for range and occupancy but is not reconstructed from
the server ray.

Custom gestures displace the ordinary Minecraft use action. Forge protection events are consulted
before `RightClickBlockSuppressor` marks the trailing vanilla click for cancellation. Fabric
performs its available vanilla edit checks before consuming the interaction. Adjacent Singles and
Bar deposits validate both the clicked block and the destination.

## Configuration and data

World policy is stored at:

```text
<world>/serverconfig/somestacks-server.json
```

`ServerConfig` owns maximum run height, type enable flags, deny lists, ingot-tag patterns, and
gallery settings. It loads at server startup. `/ss deny`, `/ss ingot`, and `/ss gen` update and save
their lists immediately. `/ss reload` reloads server render overrides and resynchronizes players;
it does not reread the world-policy JSON.

`render_gallery.enabled` and `render_gallery.required_permission_level` gate `/ss gallery` and
`/ss ingotgallery` themselves, on top of the vanilla permission check every other subcommand answers
to. Both default to the safer state: gallery access off, and the level that would apply once enabled
set to 3. Neither has a command editor; like `piles.max_pile_height` and the stack-type enable flags,
they are read from `ServerConfig` and only ever change through a direct file edit.

Ingot patterns match complete item-tag names and may contain `*`. The resolved item set is rebuilt
when relevant configuration changes and when item tags reload.

Shared resources are under `common/src/main/resources`. The main extension points are:

- item tags selected by `ingot_tags` for Bar admission;
- `data/<namespace>/somestacks_sounds/*.json` for action sounds;
- `assets/<namespace>/item_render_overrides/*.json` for Storage and Singles profiles;
- `assets/<namespace>/textures/bars/*.json` for Bar textures and tints;
- `config/somestacks/server_item_overrides/*.json` for server-imposed render profiles.

## Client presentation

All three types use block entity renderers. Storage and Singles render stored item models through
`CubeRenderHelper`; Bar renders fixed cuboids using resource-defined textures and tints.

Storage and Singles profiles select a render mode, scale, and offset. Profile precedence is:

1. Server overrides
2. User overrides
3. Resource-pack overrides
4. Automatic model measurement

Automatic measurement distinguishes geometry that the shared 2-D projector can reproduce from
geometry that only the loader's item renderer can draw. Projectable models use 2-D when their baked
model reports a flat GUI presentation or their measured bounds are flat; other models use 3-D.
Forge measures all render passes and probes custom-renderer vertices for 3-D fitting, but custom
renderer output is not eligible for the 2-D projector. Fabric measures ordinary baked quads from
every non-custom model without treating a non-vanilla Renderer API adapter as inherently 3-D. A
Fabric model that exposes no ordinary quads remains on the 3-D ItemRenderer path, where its enhanced
item-quad output can still be drawn instead of producing an empty 2-D projection.

Measured profiles are cached at `config/somestacks/measured_cache.json`. The cache records its
measurement-format version, selected resource packs, and owning-mod versions; a mismatch rejects
the affected cached measurements. Bar appearance data is client-only and is not synchronized by the
server.

`/ss` includes policy-list editing, render-profile authoring, and gallery generation. Gallery jobs
are spread across server ticks, but they write their display area directly and do not use ordinary
placement protection.

## Maintenance invariants

- Keep persistent state block-local and run-wide behavior in the pile or column layer.
- Preserve the different Storage, Singles, and Bar movement models.
- Preserve rotation-aware Singles seam mapping and item-rotation transport.
- Do not revalidate owned items during internal movement.
- Keep Forge simulation and Fabric transactional commit subject to the same feasibility rules.
- Route structural world edits through `WorldEdits` and the installed authority.
- Keep FTB Chunks and Open Parties and Claims references confined to `FtbChunksProtection` and
  `OpacProtection` respectively, each gated on the mod being loaded; a server running neither must
  never touch either mod's classes.
- Batch synchronization, lighting, comparator work, and Storage settlement through scheduled ticks.
- Validate every client request independently of gesture recognition.
- Preserve render-profile precedence across files, commands, and synchronization.
- Keep automatic 2-D selection conditional on geometry the shared projector can actually draw; a
  Fabric model's `isVanillaAdapter` value describes its rendering API, not its visual dimensionality.
- Increment the measured-profile cache format whenever profile-selection or fitting semantics change.
- Read the gallery commands' enablement and permission level from `ServerConfig`
  (`render_gallery.enabled`, `render_gallery.required_permission_level`) rather than a hardcoded
  constant; they are the one pair of subcommands gated by config instead of a fixed vanilla level.
- Keep Storage's `STORAGE_CELL_RENDER_SCALE` separate from Singles' cell-render scale in
  `CubeRenderHelper`; Singles' cells tile edge to edge with no gap to absorb if the two are unified.
- Keep GameTest logic that touches no loader-native storage API in `common/src/gametest`, keyed off
  `CommonRegistry`; keep `IItemHandler`- and Transfer-API-facing assertions in each loader's own
  test file. Do not force a shared body onto a test that differs between loaders, as
  `ProtectionGameTests` does.

## Build and test layout

The root `build` lifecycle builds all three subprojects, runs the JUnit suite under
`common/src/test`, and produces both loader JARs. The conventional Forge and Fabric `test` source
sets are empty. Test classes and dependencies are not included in the production JARs.

Forge's in-game tests live under `forge/src/gametest`, outside the production source set. Gradle
loads them as the separate development-only `somestacks_gametest` mod, generates their empty NBT
structure from the checked-in Base64 fixture, and runs them through `:forge:runGameTestServer`.
Fabric mirrors this under `fabric/src/gametest`, registering test classes through a `fabric-gametest`
entrypoint list in the dev-mod's own `fabric.mod.json` in place of Forge's per-class annotations.

Both loaders' `gametest` source sets also pull in `common/src/gametest/java` as an extra source
directory, added in each loader's `build.gradle`; `common`'s own build does not compile it. A
`GameTestScaffold` class and one `*Checks` class per test area hold the assertion logic that touches
no loader-native storage API, resolving blocks and block entities through `CommonRegistry` rather
than either loader's own registry. Each loader keeps one thin `@GameTest`-annotated class per area —
Forge's static methods under `@GameTestHolder`, Fabric's instance methods implementing
`FabricGameTest` — that delegates into the shared checks; where a test exercises `IItemHandler` or
the Transfer API directly, it stays loader-native instead, with `FakePlayer` standing in for
`FakePlayerFactory` on Fabric. `ProtectionGameTests` is not shared at all: several of its
same-named tests touch loader-native storage or differ in fake-player hand-reset behavior between
loaders, so both loaders keep an independent file, using the shared scaffold only for its
loader-neutral helpers. The Fabric suite also omits the Forge-only claim/event-bus protection tests;
a Fabric hook now exists to exercise them (`FabricPlayerEditAuthority`, `FabricEditAuthority`), but
the tests themselves have not been ported yet. Neither GameTest suite is part of `build`; each is
invoked separately through its own loader's `runGameTestServer` task.
