# Some Stacks As-Built Orientation

Orientation to subsystem ownership, persistent data, loader boundaries, and maintenance invariants.
`player-view.md` covers observable behavior; code wins when either document disagrees. Keep only
ownership pointers and cross-module invariants, current rather than historical, below 150 lines / 12k characters.

## Project shape

Targets Minecraft 1.21.1 and Java 21. Loader baselines are Fabric Loader 0.16.0 with Fabric API
0.102.0+1.21.1, Forge 52.0.0, and NeoForge 21.1.100; the Forge dev/build pin stays at 52.1.16.
Architectury is build-time only; no loader has an Architectury API runtime dependency. Mod id is `somestacks`; the root package is
`com.github.crittscott.somestacks`. Toolchain details are in `build-env.md`.

`common` folds into each loader JAR and is not a runtime artifact. It has no loader imports;
`CommonRegistry`, `EditAuthority`, networking callbacks, client gesture adapters, automation
adapters, and `PlatformPaths` are the loader seams. Each loader supplies registry handles before
common world objects exist. Release JARs are under `<loader>/build/libs/`.

Three blocks and block entity types are registered, with no block items, menus, recipes, or portable
containers:

| Block | Block entity | Local storage |
| --- | --- | --- |
| `storage_stack_block` | `stack_be` | 27 item stacks |
| `singles_stack_block` | `singles_stack_be` | 64 single items in a 4 x 4 x 4 grid |
| `bar_stack_block` | `bar_stack_be` | 64 single items in eight alternating layers |

## Code map

| Area | Responsibility |
| --- | --- |
| `block/*StackBlock` | State, shape, waterlogging, removal, comparators, scheduled publication |
| `block/*StackBE` | Local inventory, NBT, rotations, selected cells, run caches |
| `StoragePile`, `SinglesColumn`, `BarColumn` | Run-wide growth, mutation, fill, cleanup, structure |
| `StackItemStorage`, `SlotAccess` | Loader-neutral fixed-slot storage contract |
| `util/*CubeIdx`, `ViewRay` | Geometry, rotation, support, seams, targeting |
| `ServerConfig` | Per-world policy, deny sets, resolved ingot membership |
| `WorldEdits`, `EditAuthority` | World edits and loader protection seam |
| `network/*` | Payloads and shared request validation |
| `client/*` | Renderers, profiles, measurement, bar textures, gesture rules |
| `SsCommand`, `RenderGalleryGenerator` | Administration, profile authoring, galleries |
| Loader modules | Registration, transport, callbacks, rendering glue, native automation views |

## Loader integration

| Concern | Forge | NeoForge | Fabric |
| --- | --- | --- | --- |
| Metadata | `META-INF/mods.toml` | `META-INF/neoforge.mods.toml` | `fabric.mod.json`, mixin config |
| Entrypoint | `SomeStacks` | `SomeStacksNeoForge` | `SomeStacksFabric` plus client entrypoint |
| Networking | `SimpleChannel` | payload registrar | payload registry plus `ProtocolPkt` handshake |
| Gestures | interaction/input events | interaction/input events | callbacks plus air-click mixin |
| Automation | whole-run `IItemHandler` | whole-run `IItemHandler` | Transfer API `Storage<ItemVariant>` |
| Automated edits | vanilla plus place/break events | vanilla plus place/break events | vanilla, break callback, FTB Chunks growth check |

The selected loader build is required on client and server. Loaders own transport and callbacks;
packet codecs, request handlers, gesture rules, rendering, commands, and storage mechanics remain in
`common`. Fabric's remapped JAR carries `somestacks.mixins.json` without a runtime refmap.

## Runtime and movement model

A maximal contiguous vertical run of one stack type is the central abstraction. Block entities own
local slots; `StoragePile`, `SinglesColumn`, and `BarColumn` coordinate cross-block operations. Run
resolution is cached per game tick and invalidated by structural changes.

Block entities do not tick. Mutations schedule one deferred pass on the run's bottom block that
coalesces client updates, lighting, comparator publication, and Storage settlement. Every block in a
run reports the same run-wide comparator signal.

The three movement models must remain distinct:

- `StoragePile` is a flat inventory from the base up. Settlement groups exact identities, restores
  legal stack sizes, sorts, packs down, and removes unneeded top blocks unless permanent or protected.
- `SinglesColumn` is positional. Extraction shifts one visual column down across rotation-aware seams,
  transports item rotation, preserves unrelated gaps, and does not collapse above a broken block.
- `BarColumn` is positional and overlap-supported. Player extraction and block removal cascade;
  automation extraction moves the topmost bar into the opened position instead.

`StackItemStorage` persists local inventory through each block entity's NBT methods using Data
Components and a `HolderLookup.Provider`. Pre-1.21 data is not migrated. Storage saves `Items`,
`Rotation`, and `Permanent`; Singles adds `CubeRotations`; Bar saves `Items`. Permanence is pile-wide
but stored on the base; block rotations are local; Singles item rotation travels with the item.

## Admission, automation, and edits

Storage accepts ordinary nonempty items; Bar accepts the configured ingot list; Singles accepts
allowed non-Bar items. `disable_mods` applies to gestures and automation, while `disable_items` applies only
to player deposits. Internal settlement, gravity, and backfill never reapply admission rules.

Every loader-native view spans the whole run plus one headroom block while growth is allowed. Storage
slots use item stack limits; Singles and Bar slots hold one item. Fabric stages mutations until outer
transaction commit and permits one structural extraction position per transaction. `RunEdit` refuses
reentrant automation mutations.

Growth and cleanup use `WorldEdits` with an automation actor and check build limits, replaceability,
obstruction, border, spawn, and loader authority. Forge and NeoForge fire native place/break events.
Fabric fires `PlayerBlockBreakEvents` on removal and routes optional FTB Chunks growth checks only
through `FtbChunksProtection`.

## Player interaction and networking

Shared gesture rules recognize permanence, block rotation, item rotation, deposit, placement, and
extraction in that order. Placement mode is client state sent with the placement request.

The server treats every payload as a request. Common validation covers sender state, per-tick pacing,
loaded chunks, and reach; each operation then validates hand, target, index, support, type enablement,
height, and edit authority. Placement and first deposit are one transaction. Deposit and placement
recompute ray targets server-side; extraction and Singles item rotation accept a client index only
after range and occupancy checks.

Forge and NeoForge protection events run before `RightClickBlockSuppressor` consumes the trailing
vanilla click. Fabric runs its edit checks before consuming the interaction. Adjacent Singles and Bar
deposits validate both the clicked position and destination.

## Configuration and presentation

World policy is `<world>/serverconfig/somestacks-server.json`, owned by `ServerConfig` and loaded at
server startup. `/ss deny`, `/ss ingot`, and `/ss gen` save immediately. `/ss reload` reloads server
render overrides and resyncs players but does not reread policy JSON. Ingot-list entries are either a
`#`-prefixed item-tag pattern (`*` allowed, matched against whole tag names) or a bare item id, and
resolve again on configuration or tag reload.

Extension points are the configured ingot list; `data/<namespace>/somestacks_sounds/*.json`;
`assets/<namespace>/item_render_overrides/*.json`; `assets/<namespace>/textures/bars/*.json`; and
`config/somestacks/server_item_overrides/*.json`.

All types use block entity renderers. `CubeRenderHelper` renders Storage and Singles item models;
Bar uses fixed cuboids with resource-defined textures and tints. Storage/Singles profile precedence is
server, user, resource pack, then measurement. `AutoRenderProfiles` uses the 2-D projector only for
flat geometry it can reproduce; Fabric models with no ordinary quads stay on the 3-D renderer path.
`measured_cache.json` is keyed by format version, resource packs, and owning-mod versions. Bar
appearance is client-only. Galleries spread work across ticks but bypass normal placement protection.

## Maintenance and tests

- Keep persistent state block-local and run behavior in the pile/column layer.
- Preserve the three movement models and Singles' rotation-aware item transport.
- Do not revalidate stored items during internal movement.
- Keep Forge/NeoForge simulation and Fabric commit feasibility rules aligned.
- Route structural edits through `WorldEdits`; batch sync, light, comparator work, and settlement.
- Validate every client request independently of client gesture recognition.
- Preserve render-profile precedence and bump the measurement format when fitting semantics change.
- Keep Storage and Singles render scales separate in `CubeRenderHelper`.

There is no JUnit or production `test` source set. Each loader has a development-only
`somestacks_gametest` mod under `<loader>/src/gametest` and runs it with
`:<loader>:runGameTestServer`; no suite is part of `build`. Neutral assertions live in
`common/src/gametest` and use `CommonRegistry`; native `IItemHandler` and Transfer API assertions stay
loader-local. Forge and NeoForge use annotated holders; Fabric uses `fabric-gametest` entrypoints.
NeoForge holders disable class-name template prefixes. Synthetic-player and protection tests remain
loader-specific where their APIs differ. Distinct otherwise-identical test stacks use `CUSTOM_DATA`.
