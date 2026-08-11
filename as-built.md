# Some Stacks As-Built Orientation

This guide identifies the code boundaries and invariants a maintainer should understand before
changing Some Stacks. It is deliberately selective. `player-view.md` describes observable
behavior; the code is authoritative when either document is wrong.

## Project shape

Some Stacks targets Minecraft 1.20.1 and Java 17, with Forge 47.4.0 and Fabric Loader 0.16.9 / Fabric
API 0.92.2 targets. The mod id is `somestacks` and the root package is
`com.github.crittscott.somestacks`.

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
automation, and attaches lifecycle listeners. Forge installs its event-backed edit authority;
Fabric uses the shared vanilla authority. Each loader's registry adapter supplies the common
registry handles before common world objects are created.

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
place or break events for claim and logging integrations. Fabric has no general claim-event hook in
the initial port and therefore applies only the vanilla checks.

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

Measured profiles are cached under `config/somestacks`. Bar appearance data is client-only and is
not synchronized by the server.

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
- Batch synchronization, lighting, comparator work, and Storage settlement through scheduled ticks.
- Validate every client request independently of gesture recognition.
- Preserve render-profile precedence across files, commands, and synchronization.
