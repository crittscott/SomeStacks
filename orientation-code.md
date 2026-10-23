<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

See `orientation-player.md` and `orientation-build.md`.

## Project shape

Java 21; Minecraft 1.21.8 on Fabric, Forge, and NeoForge; package `com.github.crittscott.somestacks`, id `somestacks`.

`common` folds into each loader JAR. Forge/NeoForge outer classes use loader prefixes; platform services sit beside entrypoints. `PlatformServices.Backend` supplies paths, versions, networking and edit authority; loaders own gesture/automation adapters. `CommonRegistry` supplies identifiers/lookups.

Three blocks; no block items, menus, or recipes:

`ShapedStackBlock`: Singles/Bar use content-shaped targeting/outlines/collision, full-block interaction, and empty visual shapes.

| Block | Block entity | Local storage |
| --- | --- | --- |
| `storage_stack_block` | `stack_be` | 27 item stacks |
| `singles_stack_block` | `singles_stack_be` | 64 single items in a 4 x 4 x 4 grid |
| `bar_stack_block` | `bar_stack_be` | 64 single items in eight alternating layers |

## Code map

| Area | Responsibility |
| --- | --- |
| `StackBlock`, `ShapedStackBlock`, `block/*StackBlock` | State, waterlogging, use, shapes, removal, comparators and publication |
| `StackBlockEntity`, `block/*StackBE` | Inventory, NBT, sync, lighting, batching, shapes, runs |
| `StackRun`, `StackRunItemAccess`, `StoragePile`, `SinglesColumn`, `BarColumn` | Runs, slots, publication, comparators, growth, cleanup |
| `StackItemStorage`, `SlotAccess` | Mutable local storage and read-only slot view |
| `CubeGrid`, `util/*CubeIdx`, `ViewRay` | Grid coordinates, ray selection, geometry, support and seams |
| `ServerConfig`, loader config adapters | Per-world policy and native TOML/Fabric TOML persistence |
| `WorldEdits`, `EditAuthority` | World edits, actor authority, neighbor protection |
| `StackInteractions`, `ServerGestureState`, `StackGesture` | Block-use interpretation, gesture intent and state |
| `network/*` | Gesture-state and policy payloads |
| `renderconfig/*` | Render override types, validation, and codecs |
| `client/*` | Rendering, profiles, measurement, textures, gestures, commands |
| `SomeStacksServer`, `SsCommand`, `RenderGalleryGenerator` | Lifecycle, policy publication and logging, administration, galleries |
| Loader modules | Registration, transport, callbacks, rendering glue, native automation views |

## Loader integration

| Concern | Forge | NeoForge | Fabric |
| --- | --- | --- | --- |
| Metadata | `META-INF/mods.toml` | `META-INF/neoforge.mods.toml` | `fabric.mod.json`, mixin config |
| Entrypoint | `SomeStacksForge` | `SomeStacksNeoForge` | `SomeStacksFabric` plus client entrypoint |
| Networking | versioned `SimpleChannel` | versioned payload registrar | payload registry plus a configuration-phase `ProtocolPkt` version marker |
| Gestures | interaction/input events | interaction/input events | callbacks plus air-click mixin |
| Automation | generic whole-run `IItemHandler` | generic whole-run `IItemHandler` | generic Transfer API `Storage<ItemVariant>` |
| Edit protection | vanilla plus place/break events | vanilla plus place/break events | vanilla, required Common Protection API destination queries, break callbacks |

Forge uses EventBus 7 and `AttachCapabilitiesEvent.BlockEntities`.

## Runtime and movement model

A maximal vertical run of one type is central. Block entities own local slots; server-only `StackRun` owns resolution, slots, headroom, publication and comparators. Concrete runs own movement. All members share one per-tick resolved run; structural edits invalidate affected caches.

`StackBlock` owns lifecycle and comparator plumbing with Storage settlement hooks. Block entities do not tick. Mutations batch client, light, comparator, and Storage settlement work. Blocks report run signals from per-slot fill fractions cached on mutation/load. `preRemoveSideEffects` drops contents and cascades; block removal hooks then repair surviving runs.

Movement models:

- `StoragePile` is a flat inventory from the base up. Settlement groups exact identities, sorts distinct groups by item id, damage, and lazily memoized component text, restores legal stack sizes, packs down, and removes unneeded top blocks unless permanent or protected.
- `SinglesColumn` is positional. Extraction shifts one visual column down across rotation-aware seams, transports item rotation, preserves unrelated gaps, and does not collapse above a broken block.
- `BarColumn` is positional and overlap-supported. Player extraction and block removal cascade; automation extraction moves the topmost bar into the opened position instead.

Persistence uses `ValueInput`/`ValueOutput` and registry-aware `ItemStack.CODEC`. `StackDataMigration.readTag` preserves raw disk/wire types at the boundary. Encode failure aborts serialization with slot and location context. All types save `DataVersion` and `Items`; Storage adds `Permanent`, and Singles adds `CubeRotations`. Storage and Singles layout rotation is the outer block's `horizontal_facing`; Singles item rotation remains block-entity data. `Permanent` is server-only and is stripped from update tags.

`StackDataMigration` upgrades saved items through DataFixerUpper; absent `DataVersion` means 1.21.1 (3955), and legacy `Rotation` becomes block state. Unreadable, slotless, and non-compound entries persist in `SetAside` and retry each load. Over-cap Storage entries split into free slots, then `SetAside`. `ServerConfigMigration` logs custom 1.21.1 `compatibility.ingots` entries at server start. Client updates need current format, unique compound slots, valid Singles rotations, and no server-only fields; rejection changes nothing and warns once per connection. Validation precedes inherited component loading: a Fabric client mixin guards `loadWithComponents`, and registered Forge/NeoForge subclasses override native update hooks. Deferred migration marks dirty on level attachment. Permanence belongs to the base; block rotation is local; item rotation travels with Singles.

## Admission, automation, and edits

Storage accepts ordinary nonempty items; Bar accepts the reloadable `somestacks:ingots` item tag; Singles accepts allowed non-Bar items. `disable_mods` applies to gestures and automation, while `disable_items` applies only to player deposits; galleries skip both. Internal settlement, gravity, and backfill never reapply admission rules.

Loader-native views adapt `StackRunItemAccess` and span the run plus one headroom block while growth is allowed. Storage holds min(64, item limit); Singles and Bar hold one item. Forge/NeoForge use native `RunItemHandler` wrappers over common `RunItemHandlerBase`; Fabric keeps one transaction adapter per block entity in an unsaved attachment. Fabric run views share the bottom block's ledger, judge support before growth against staged contents via `acceptsAt`, share one automation growth-permission query per bulk insertion, and on outer commit apply removals, then additions in ascending slot order. A staged Singles/Bar extraction blocks insertion and needs no other pending change. `RunEdit` refuses reentrant automation mutations.

`WorldEdits` resolves nullable actors and checks build limits, replaceability, obstruction, border, spawn and loader authority. Automated edits use the `[SomeStacks]` identity. Player cleanup retains the player; deferred Storage settlement uses automation for automated or mixed causes. Forge caches a connection-safe actor per dimension; NeoForge and Fabric use fake-player factories. Forge/NeoForge fire native placement events against captured snapshots, silently restore denied state, and publish accepted changes. Removal fires native break events. Fabric queries Common Protection API for player/automation placement, neighbor use, and removal, around native break callbacks; it fires no synthetic use callbacks. Removal applies vanilla game-mode restrictions first. Placement completion (`setPlacedBy`, `BLOCK_PLACE`) lives in `WorldEdits`. Outer edits emit `BLOCK_PLACE`/`BLOCK_DESTROY`; player content and rotation edits emit `BLOCK_CHANGE` while the stack survives.

## Player interaction and networking

`StackGesture` classifies main-hand intent for client and server: permanence, block rotation, item rotation, deposit/placement, then extraction; cell and destination selection are server-only. Loader callbacks supply hit classification. Forge/NeoForge cancellation and Fabric `SUCCESS` suppress local use while retaining vanilla block-use packets. A payload sends mode and modifier state only when changed.

The only play-phase server-to-client payload is one bounded `ConfigSyncPkt` snapshot (protocol 6; 256 unique namespaces of up to 256 characters per list), sent only when the normalized snapshot changes. `ClientServerSettings` publishes it immutably; `SomeStacksClient` owns join/disconnect/stopping. `/ss item` and `/ss write` act locally.

`StackBlock.useItemOn`/`useWithoutItem` handle non-sneaking clicks; loader hooks handle sneaking torch rotation and neighbor actions. `StackInteractions` validates hand, spectator/protection state, item, support, enablement, height, obstruction, and authority. New placement resolves replaceable targets through `BlockPlaceContext`; placement plus first deposit is atomic and fires `PLACED_BLOCK`. Unhandled uses pass; plain extraction stays consumed. Rotation reads are pure; edits set native facing, inherited before Storage placement. Cells are selected server-side along a ray through the vanilla hit.

Neighbor deposits consult Forge/NeoForge right-click events or Fabric Common Protection API; a guard prevents recursive gestures. Brigadier handles commands; Fabric forwards unclaimed client `ss` forms.

## Configuration and presentation

`ServerConfig` publishes immutable policy. Forge/NeoForge use native SERVER configs (`ForgeConfigSpec`/`ModConfigSpec`); TOML overrides live in `<world>/serverconfig/`; `TomlPolicyFile` reads Fabric TOML with per-field defaults. `/ss deny` and `/ss gen` save through the backend. Backends share common keys/defaults and return settings; the facade applies them. `/ss reload` and native config events share one server-thread refresh that resyncs only changed client settings and resends command trees on gallery access changes. Bar/Singles classification uses the `somestacks:ingots` tag.

Extensions are listed in `orientation-player.md`.

`GridStackBER` renders Storage/Singles through `CubeGrid` and `CubeRenderHelper`; Bar uses resource-defined cuboids and tints, and its contents drop as item entities regardless of block drops. Storage/Singles profile precedence is user, resource pack, then measurement. `ItemCapture` records baked quads, tints, and optional bounds. Each distinct stack (item, components, count) prepares its model or Z-facing 2-D plates once in a bounded LRU until eviction or profile/resource change, so time-dependent models freeze; a throwing 2-D capture draws `stack_cube_error` until reload. Renderers use vanilla's frame light. Bar caches cuboids, UVs, and tints on the entity until a content update or resource reload. `measured_cache.json` is keyed by format version, resource packs, and owning-mod versions. Resources target pack format 63.

2-D selection: thinnest/longest axis ratio. Cache: 3.

## GameTests

`somestacks_gametest` is excluded from `build`. Shared checks: `common/src/gametest`, applying policy in memory; JSON config tests are Fabric-only. Loader wrappers use Fabric entrypoints or Forge/NeoForge deferred functions/test-instance JSON, with `AutomationAndPersistence` names. Test data format: 80. The gallery-budget check runs alone in its own test environment; a regular player checks the placed-block criterion fixture. `/ssclienttest` (aim at a stack; release Shift/V) also checks profile layers and commands, Bar presentation caching, and native client update ingress.

Fabric GameTests install a Common Protection API provider and reject gameplay callbacks during destination queries.


