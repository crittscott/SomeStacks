<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

See `orientation-player.md` and `orientation-build.md`.

## Project shape

Java 21; Minecraft 1.21.6; stages 1–6 implemented; compilation/runtime checks pending; package `com.github.crittscott.somestacks`, id `somestacks`.

`common` folds into each loader JAR. `PlatformServices.Backend` supplies paths, versions, networking and edit authority; loaders own gesture/automation adapters. `CommonRegistry` supplies identifiers/lookups.

Three blocks; no block items, menus, or recipes:

`ShapedStackBlock`: Singles/Bar use content-shaped targeting/outlines/collision, full-block interaction, and empty visual shapes. Rays pass through gaps; empty stacks have no outline.

| Block | Block entity | Local storage |
| --- | --- | --- |
| `storage_stack_block` | `stack_be` | 27 item stacks |
| `singles_stack_block` | `singles_stack_be` | 64 single items in a 4 x 4 x 4 grid |
| `bar_stack_block` | `bar_stack_be` | 64 single items in eight alternating layers |

## Code map

| Area | Responsibility |
| --- | --- |
| `StackBlock`, `ShapedStackBlock`, `block/*StackBlock` | State, waterlogging, use, shapes, removal, comparators and publication |
| `StackBlockEntity`, `block/*StackBE` | Inventory, NBT, sync, lighting, batching, shapes, rotations and runs |
| `StackRun`, `StackRunItemAccess`, `StoragePile`, `SinglesColumn`, `BarColumn` | Runs, slots, publication, comparators, mutation, growth and cleanup |
| `StackItemStorage`, `SlotAccess` | Loader-neutral fixed-slot storage contract |
| `CubeGrid`, `util/*CubeIdx`, `ViewRay` | Shared cube-grid coordinates and ray selection plus per-type geometry, support and seams |
| `ServerConfig`, loader config adapters | Immutable per-world policy, deny sets, native Forge/NeoForge TOML or Fabric JSON persistence |
| `WorldEdits`, `EditAuthority` | World edits, automation authority, adjacent-target protection |
| `StackInteractions`, `ServerGestureState` | Vanilla block-use interpretation and gesture state |
| `network/*` | Gesture-state and server-to-client synchronization payloads |
| `renderconfig/*` | Shared render override types, JSON validation, and codecs |
| `client/*` | Renderers, profiles, measurement, bar textures, gesture rules, local profile-authoring commands |
| `SomeStacksServer`, `SsCommand`, `RenderGalleryGenerator` | Common lifecycle, server administration and galleries |
| Loader modules | Registration, transport, callbacks, rendering glue, native automation views |

## Loader integration

| Concern | Forge | NeoForge | Fabric |
| --- | --- | --- | --- |
| Metadata | `META-INF/mods.toml` | `META-INF/neoforge.mods.toml` | `fabric.mod.json`, mixin config |
| Entrypoint | `SomeStacks` | `SomeStacksNeoForge` | `SomeStacksFabric` plus client entrypoint |
| Networking | versioned `SimpleChannel` | versioned payload registrar | payload registry plus a configuration-phase `ProtocolPkt` version marker |
| Gestures | interaction/input events | interaction/input events | callbacks plus air-click mixin |
| Automation | generic whole-run `IItemHandler` | generic whole-run `IItemHandler` | generic Transfer API `Storage<ItemVariant>` |
| Edit protection | vanilla plus place/break events | vanilla plus place/break events | vanilla and break callbacks; optional Common Protection API queries |

Forge uses EventBus 7 typed buses, `BusGroup`, boolean cancellation, `Result` denial, and `AttachCapabilitiesEvent.BlockEntities`; development enables event validation.

## Runtime and movement model

A maximal vertical run of one type is central. Block entities own local slots; server-only `StackRun` owns resolution, slots, headroom, publication and comparators. Concrete runs own movement. Structural edits invalidate per-tick resolution caches.

Block entities do not tick. Mutations batch client, light, comparator, and Storage settlement work. Blocks report the run's signal using fill cached on mutation/load. `preRemoveSideEffects` drops contents and cascades; block removal hooks then repair surviving runs.

Movement models:

- `StoragePile` is a flat inventory from the base up. Settlement groups exact identities, sorts distinct groups using precomputed fields, restores legal stack sizes, packs down, and removes unneeded top blocks unless permanent or protected.
- `SinglesColumn` is positional. Extraction shifts one visual column down across rotation-aware seams, transports item rotation, preserves unrelated gaps, and does not collapse above a broken block.
- `BarColumn` is positional and overlap-supported. Player extraction and block removal cascade; automation extraction moves the topmost bar into the opened position instead.

Persistence uses `ValueInput`/`ValueOutput` and registry-aware `ItemStack.CODEC`. `StackDataMigration.readTag` preserves raw disk/wire types at the boundary. Encode failure aborts serialization with slot and location context. All types save `DataVersion` and `Items`; Storage adds `Permanent`, and Singles adds `CubeRotations`. Storage and Singles layout rotation is the outer block's `horizontal_facing`; Singles item rotation remains block-entity data. `Permanent` is server-only and is stripped from update tags.

`StackDataMigration` upgrades saved items through DataFixerUpper; absent `DataVersion` means 1.21.1 (3955), and legacy `Rotation` becomes block state. Unreadable entries, missing-slot entries, and non-compound list values persist in `SetAside` and retry each load. `ServerConfigMigration` logs custom 1.21.1 `compatibility.ingots` entries at server start. Warnings identify dimension and position; failed retries warn once per location per session. Client updates require current format, bounded inventory, compound entries with unique integer slots, valid Singles rotations, and no server-only fields; rejection changes nothing. Loader update hooks and Fabric's client load branch share validation. Deferred migration marks dirty on level attachment. Permanence belongs to the base; block rotation is local; item rotation travels with Singles.

## Admission, automation, and edits

Storage accepts ordinary nonempty items; Bar accepts the reloadable `somestacks:ingots` item tag; Singles accepts allowed non-Bar items. `disable_mods` applies to gestures and automation, while `disable_items` applies only to player deposits; galleries skip both. Internal settlement, gravity, and backfill never reapply admission rules.

Loader-native views adapt `StackRunItemAccess` and span the run plus one headroom block while growth is allowed. Storage slots use item limits; Singles and Bar hold one item. Forge/NeoForge use native `RunItemHandler` wrappers over common `RunItemHandlerBase`; Fabric uses one cached transaction adapter per block entity. Run views share the bottom block's transaction ledger, stage mutations until outer commit, and permit one structural extraction position per transaction. `RunEdit` refuses reentrant automation mutations.

`WorldEdits` checks build limits, replaceability, obstruction, border, spawn and loader authority. Automated edits use the `[SomeStacks]` identity. Player cleanup retains the player; deferred Storage settlement uses automation for automated or mixed causes. Forge caches a connection-safe actor per dimension; NeoForge and Fabric use fake-player factories. Forge/NeoForge capture tentative placement in loader snapshots, fire native placement events before publishing, restore block-entity data on denial without neighbor updates, and publish accepted changes through the loader facility. Removal fires native break events. Fabric uses Common Protection API placement queries only when installed; without it, vanilla checks govern placement and growth. Removal fires the `PlayerBlockBreakEvents` lifecycle. Outer edits emit `BLOCK_PLACE`/`BLOCK_DESTROY`; player content and rotation edits emit `BLOCK_CHANGE` while the stack survives.

## Player interaction and networking

Main-hand client rules recognize permanence, block rotation, item rotation, deposit, placement, and extraction in order. Loader callbacks supply hit classification. Forge/NeoForge cancellation and Fabric `SUCCESS` suppress local use while retaining vanilla block-use packets. A payload sends mode and modifier state only when changed.

The only play-phase server-to-client payload is a cached config snapshot. Decoding bounds packet bytes; staging bounds bytes/chunks and rejects duplicates, inconsistent metadata and empty middle chunks. Rejection/disconnect clear staging; complete totals publish enable flags and namespace lists atomically. `/ss item` and `/ss write` act locally; no server packet requests client writes.

`StackBlock.useItemOn`/`useWithoutItem` handle non-sneaking clicks; loader server hooks handle sneaking torch rotation and actions through neighboring non-stack blocks. Both pass vanilla's `BlockHitResult` to `StackInteractions` after ordinary interaction checks. Common handling validates hand, spectator/protection state, item, support, enablement, height, obstruction, and authority. Placement plus first deposit is atomic. Deposit, extraction, and Singles item rotation select cells server-side along a ray through the vanilla hit; no client cell index is accepted.

Forge/NeoForge consult destination right-click events for neighbor Singles/Bar deposits, honoring cancellation and block/item-use denial; a scoped guard prevents duplicate gestures. Fabric consults destination block-use callbacks and optional Common Protection API. One authority per loader handles placement and destination permission. Commands share Brigadier syntax; Fabric forwards unclaimed client `ss` forms.

## Configuration and presentation

`ServerConfig` publishes immutable policy. Forge/NeoForge use native SERVER configs (`ForgeConfigSpec`/`ModConfigSpec`); load/reload events apply policy and resync. Commented TOML supports `<world>/serverconfig/somestacks-server.toml` overrides. Fabric loads `<world>/serverconfig/somestacks-server.json` from defaults and reports malformed fields independently. `/ss deny` and `/ss gen` save through the backend. `/ss reload` refreshes policy and syncs changed client settings; Fabric rereads JSON, while Forge/NeoForge use native config events. Bar/Singles classification uses the `somestacks:ingots` tag.

Extensions: `somestacks:ingots` data-pack tag; registered `somestacks:block.*` sounds through ordinary resource-pack `sounds.json`; `assets/<namespace>/item_render_overrides/*.json`; and `assets/<namespace>/textures/bars/*.json`. Servers distribute profiles through resource packs.

Block entity renderers use `CubeGrid` and `CubeRenderHelper` for Storage/Singles item models; Bar uses resource-defined cuboids and tints. Storage/Singles profile precedence is user, resource pack, then measurement. Resolved profiles cache per item until override change or reload. `ItemCapture` records baked quads, tints, and optional bounds. Measurement uses bounds; 2-D projection captures quads only and shares immutable results by item, components, and count within each block render pass; a throwing capture draws `stack_cube_error` until reload. Bar UV regions cache per atlas sprite until reload. Bar auto-tint reads the first quad. `measured_cache.json` is keyed by format version, resource packs, and owning-mod versions. Resources target pack format 63; galleries bypass protection.

2-D selection: thinnest/longest axis ratio. Cache: 3.

## GameTests

`somestacks_gametest` is excluded from `build`. Shared checks: `common/src/gametest`; loader wrappers use Fabric entrypoints, Forge annotations, or NeoForge deferred functions/test-instance JSON. Test data format: 80. JSON/references checked; compilation/discovery pending. For `/ssclienttest`, aim at a stack; release Shift/V.

`-PwithCommonProtectionApi=true` adds the compile-only Fabric integration to development runs with provider-backed GameTests; default tests cover its absence.

