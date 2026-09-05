# Some Stacks As-Built Orientation

Orientation to the repository's build structure, subsystem ownership, persistent data, cross-loader boundaries, and maintenance invariants. `player-view.md` covers observable behavior. Not a spec, not a prose restatement of the code; the code wins when they disagree. No history.

Length budget: 150 lines / 12k characters. If an edit pushes past that, cut something — don't append.

Per-sentence test: every sentence either (a) names the file/class to open to change a behavior, or (b) names an invariant not visible from any single file — a cross-module contract, an ordering requirement, a "keep these in sync". Sentences that only restate what the code does get deleted, as do enumerations of a method's branches or steps. Update in place.

This is an orientation to the current code, not a history, conversation, or prose rendering of the implementation. It describes what exists, not necessarily what should exist, and is not a design specification.

## Project shape

Targets Minecraft 1.21.1 and Java 21. Build baselines: Fabric Loader 0.19.3 / Fabric API
0.116.15+1.21.1, Forge 52.1.16, NeoForge 21.1.248. Architectury is a build-time plugin and
transformation dependency only; no loader carries an Architectury API runtime dependency. Mod id
`somestacks`, root package `com.github.crittscott.somestacks`. Toolchain detail is in `build-env.md`.

Only `common` and `fabric` are ported to 1.21.1. `forge` and `neoforge` are wired into the build
but their source still targets 1.20.1 and does not compile, so every "Forge" description below is of
that unported source; `neoforge` is a build-script skeleton plus one `@ExpectPlatform` impl.

`common` is not a runtime artifact; its Java and resources fold into each loader JAR and hold no
Forge or Fabric imports. Loader services enter through seams: `CommonRegistry`, `EditAuthority`,
networking callbacks, client gesture adapters, the automation adapters, and the `@ExpectPlatform`
helper `PlatformPaths` (one `PlatformPathsImpl` per loader). Release JARs are per loader under
`<loader>/build/libs/`; root `build/libs` is not an output. Fabric's remapped JAR also carries
`somestacks.mixins.json`; Loom's `remapJar` writes mixin references statically, so the config
declares no runtime refmap.

Three blocks and their block entity types are registered; no block items, menus, recipes, or
portable containers.

| Block | Block entity | Local storage |
| --- | --- | --- |
| `storage_stack_block` | `stack_be` | 27 item stacks |
| `singles_stack_block` | `singles_stack_be` | 64 single items, 4 x 4 x 4 grid |
| `bar_stack_block` | `bar_stack_be` | 64 single items, eight alternating bar layers |

## Code map

| Area | Responsibility |
| --- | --- |
| `block/*StackBlock` | Block state, shape, waterlogging, removal, comparators, scheduled publication |
| `block/*StackBE` | Local inventory, NBT, rotations, ray-selected cells, run caches |
| `StoragePile`, `SinglesColumn`, `BarColumn` | Run-wide growth, mutation, fill, cleanup, structural rules |
| `StackItemStorage`, `SlotAccess` | Loader-neutral fixed-slot storage and automation contract |
| `util/*CubeIdx`, `ViewRay` | Cell geometry, rotation, support, seams, targeting |
| `ServerConfig` | Per-world policy, deny sets, resolved ingot membership |
| `WorldEdits`, `EditAuthority` | World-edit mechanics and loader protection seam |
| `network/*` | Packet payloads and shared request validation |
| `client/*` | Block entity renderers, item profiles, model measurement, bar textures |
| `SsCommand`, `RenderGalleryGenerator` | Administration, render-profile authoring, galleries |
| Loader entry points / registries / network / client packages | Registration, lifecycle, native transport, renderer and gesture glue |
| Forge capability / Fabric Transfer API adapters | Whole-run loader-native views over common storage |

Each loader's registry adapter supplies common registry handles before common world objects exist.
Both install event-backed player and automation authorities; on Fabric these are
`FabricPlayerEditAuthority` and `FabricEditAuthority`, firing `UseBlockCallback` and
`PlayerBlockBreakEvents`.

## Loader integration

| Concern | Forge | Fabric |
| --- | --- | --- |
| Metadata | `META-INF/mods.toml` | `fabric.mod.json`, `somestacks.mixins.json` |
| Entrypoints | `SomeStacks` (+ `ClientSetup`) | `SomeStacksFabric`, `SomeStacksFabricClient` |
| Networking | `SimpleChannel` | `CustomPacketPayload` via `PayloadTypeRegistry` |
| Protocol gate | channel version check | `ProtocolPkt` handshake gates all play packets |
| Gestures | Forge interaction/input events | interaction callbacks + air-click mixin |
| Automation | whole-run `IItemHandler` | whole-run Transfer API `Storage<ItemVariant>` |
| Automated edits | vanilla + Forge place/break events | vanilla; `PlayerBlockBreakEvents` on removal; FTB Chunks on growth |

Both builds are required on client and server. Loaders own transport and callbacks; packet codecs,
request handlers, gesture rules, rendering, commands, and storage mechanics stay in `common`.

## Runtime model

The central abstraction is the maximal contiguous vertical run of one stack type. A block entity
owns only its local slots; `StoragePile`, `SinglesColumn`, and `BarColumn` coordinate cross-block
operations. Run resolution is cached per game tick and invalidated by structural changes.

Block entities do not tick. Mutations schedule one deferred pass on the run's bottom block that
coalesces client updates, lighting, comparator publication, and Storage settlement. Comparator
strength is computed over the whole run, so every block in it reports the same signal. All blocks
are waterloggable and store a derived light value from local contents.

`StackItemStorage` holds inventory in block entity NBT. Serialization runs through Data Components:
`StackItemStorage.serializeNBT`/`deserializeNBT` and each block entity's
`loadAdditional`/`saveAdditional`/`getUpdateTag` take a `HolderLookup.Provider`. Pre-1.21 saved data
is not migrated. Saved keys: Storage `Items` + block `Rotation` + `Permanent`; Singles the same plus
one `CubeRotations` entry per cell; Bar `Items` only. Storage permanence belongs to the base block
and propagates during settlement; Storage and Singles block rotations are block-local; a Singles
item's rotation travels with the item through gravity and across seams.

## Run behavior

The three movement models are deliberately different and must stay distinct:

- **Storage** (`StoragePile`): one flat inventory from the base up. Settlement groups exact
  identities, restores legal stack sizes, sorts, and packs downward; empty top blocks go unless the
  pile is permanent or removal is refused. Breaking a block drops only that block's contents.
- **Singles** (`SinglesColumn`): positional. Each occupied cell needs the one directly below, with
  rotation-aware seam mapping that carries item rotation. Extraction shifts one visual column down a
  layer and preserves unrelated gaps; breaking a block does not collapse the column above.
- **Bar** (`BarColumn`): positional. A bar is supported when its footprint overlaps a bar below.
  Player extraction and block removal cascade through unsupported bars; automation extraction
  instead moves the topmost bar into the opened position.

## Admission and automation

Storage accepts ordinary nonempty items; Bar accepts items resolved from configured ingot tags;
Singles accepts allowed non-Bar items. `disable_mods` applies to player deposits and automated
insertion; `disable_items` to player deposits only. Policy changes never invalidate stored
contents, and internal settlement, gravity, and backfill must not reapply admission rules.

Forge attaches an `IItemHandler` capability and Fabric registers a Transfer API
`Storage<ItemVariant>` per block entity. From any block the loader-native view spans the whole run
plus one headroom block while growth is allowed; Storage slots take normal stack sizes, Singles and
Bar one item. Fabric stages mutations in the caller's transaction and commits structural changes
only on outer commit, allowing one structural extraction position per transaction for Singles and
Bar. `RunEdit` blocks reentrant automation mutations.

Growth and cleanup route through `WorldEdits` with an automation actor (build limits,
replaceability, obstruction, border, spawn). Forge also fires place and break events. Fabric fires
`PlayerBlockBreakEvents` for automation removal but has no placement event; on growth
`FabricEditAuthority` consults FTB Chunks through `FtbChunksProtection` when that optional
compile-time dependency is present, and FTB Chunks types are referenced nowhere else. Open Parties
and Claims support was removed with the 1.21.1 port.

## Player interaction and networking

Shared client gesture rules recognize permanence, block rotation, item rotation, deposit,
placement, and extraction, in that order. Shared packet classes in `common/.../network` implement
`CustomPacketPayload`, each with a `StreamCodec` over its byte-buffer encode/decode. Placement mode
is client state sent with the placement request; Fabric's air-click mixin captures the modifier
gesture that has no Fabric API callback.

The server treats every message as a request: common checks cover sender state, per-tick pacing,
loaded chunks, and reach, then each operation validates hand, target, index, support, and edit
permission. Placement and its first deposit are one transaction. Deposit and placement recompute
ray targets server-side; extraction and Singles item rotation trust a client-supplied cell index
checked only for range and occupancy. Custom gestures displace the vanilla use action: Forge
consults protection events before `RightClickBlockSuppressor` cancels the trailing vanilla click,
Fabric runs vanilla edit checks before consuming the interaction. Adjacent Singles and Bar deposits
validate both the clicked block and the destination.

## Configuration and data

World policy lives at `<world>/serverconfig/somestacks-server.json`. `ServerConfig` owns maximum
run height, type enable flags, deny lists, ingot-tag patterns, and gallery settings, and loads at
server startup. `/ss deny`, `/ss ingot`, and `/ss gen` save immediately. `/ss reload` reloads
server render overrides and resyncs players but does not reread the policy JSON.
`piles.max_pile_height`, the stack-type enable flags, `render_gallery.enabled`, and
`render_gallery.required_permission_level` have no command editor and change only by direct file
edit; the last two gate `/ss gallery` and `/ss ingotgallery` on top of the vanilla permission
check. Ingot patterns match complete item-tag names, may contain `*`, and their resolved set is
rebuilt on config change and tag reload.

Shared resources are under `common/src/main/resources`. Extension points:

- item tags selected by `ingot_tags` for Bar admission;
- `data/<namespace>/somestacks_sounds/*.json` for action sounds;
- `assets/<namespace>/item_render_overrides/*.json` for Storage and Singles profiles;
- `assets/<namespace>/textures/bars/*.json` for Bar textures and tints;
- `config/somestacks/server_item_overrides/*.json` for server-imposed render profiles.

## Client presentation

All three types use block entity renderers. Storage and Singles render item models through
`CubeRenderHelper`; Bar renders fixed cuboids from resource-defined textures and tints. Storage and
Singles profiles carry a render mode, scale, and offset, resolved in precedence order: server
overrides, user overrides, resource-pack overrides, automatic model measurement.

Automatic measurement uses the shared 2-D projector only for geometry it can reproduce (flat GUI
presentation or flat measured bounds) and 3-D otherwise; a Fabric model's `isVanillaAdapter`
reports its rendering API, not its dimensionality, so a Fabric model exposing no ordinary quads
stays on the 3-D `ItemRenderer` path. Measured profiles are cached at
`config/somestacks/measured_cache.json`, keyed by measurement-format version, selected resource
packs, and owning-mod versions; a mismatch rejects the affected entries. Bar appearance data is
client-only and unsynchronized. `/ss` gallery jobs spread across ticks but write their display area
directly, bypassing ordinary placement protection.

## Maintenance invariants

- Keep persistent state block-local; keep run-wide behavior in the pile or column layer.
- Preserve the distinct Storage, Singles, and Bar movement models and Singles' rotation-aware seam
  mapping with item-rotation transport.
- Do not revalidate owned items during internal movement.
- Hold Forge simulation and Fabric transactional commit to the same feasibility rules.
- Route structural world edits through `WorldEdits` and the installed authority.
- Confine FTB Chunks references to `FtbChunksProtection`, gated on the mod being loaded.
- Batch synchronization, lighting, comparator work, and Storage settlement through the scheduled tick.
- Validate every client request independently of gesture recognition.
- Preserve render-profile precedence across files, commands, and sync; bump the `measured_cache.json`
  format version whenever profile-selection or fitting semantics change.
- Read gallery enablement and permission level from `ServerConfig`, not a constant.
- Keep Storage's `STORAGE_CELL_RENDER_SCALE` separate from Singles' cell-render scale in
  `CubeRenderHelper`; Singles' cells tile edge to edge with no gap to absorb.
- Keep loader-neutral GameTest logic in `common/src/gametest` keyed off `CommonRegistry`; keep
  `IItemHandler`- and Transfer-API-facing assertions in each loader's own test file.

## Build and test layout

Root `build` compiles every subproject, runs the `common/src/test` JUnit suite, and produces each
loader JAR, but only `:fabric:build` currently succeeds; the Forge and Fabric `test` source sets are
empty and `common/src/test` still holds 1.20.1 API. Test code is not in the production JARs.

Each loader's in-game tests live under `<loader>/src/gametest` as a development-only
`somestacks_gametest` mod with its own Base64 NBT fixture, run through `:<loader>:runGameTestServer`;
Forge discovers `@GameTestHolder` classes, Fabric uses a `fabric-gametest` entrypoint list in the
dev mod's `fabric.mod.json`. `common/src/gametest/java` is added as an extra source directory by
each loader's `build.gradle`; `common`'s own build ignores it. Assertion logic that touches no
loader-native storage API lives in `GameTestScaffold` and one `*Checks` class per area, resolving
blocks and block entities through `CommonRegistry`; each loader keeps a thin per-area `@GameTest`
class delegating to those checks. A test that calls `IItemHandler` or the Transfer API directly
stays loader-native, with `FakePlayer` replacing `FakePlayerFactory` on Fabric. `ProtectionGameTests`
is loader-specific in both modules and shares only the scaffold's neutral helpers; the Fabric copy
omits the unported Forge claim/event-bus tests, whose hooks `FabricPlayerEditAuthority` and
`FabricEditAuthority` already exist. Neither suite is part of `build`; each runs through its own
`runGameTestServer`. A test needing distinct but otherwise identical stacks attaches a `CUSTOM_DATA`
component; the checked-in 1.21.1 structure fixture may still need regeneration.
