<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

See `orientation-player.md` and `build-env.md`.

## Project shape

Java 25; Minecraft 26.1.1 (unobfuscated, no mappings) on Fabric and NeoForge; builds, server GameTests, and `/ssclienttest` pass on both loaders; package `com.github.crittscott.somestacks`, id `somestacks`.

`common` folds into loader JARs. `PlatformServices.Backend` supplies paths, versions, networking and edit authority; loaders own adapters. `CommonRegistry` supplies lookups.

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
| `ServerConfig`, loader config adapters | Immutable per-world policy, deny sets, native NeoForge TOML or Fabric JSON persistence |
| `WorldEdits`, `EditAuthority` | World edits, automation authority, adjacent-target protection |
| `StackInteractions`, `ServerGestureState` | Vanilla block-use interpretation and gesture state |
| `network/*` | Gesture-state and server-to-client synchronization payloads |
| `renderconfig/*` | Shared render override types, JSON validation, and codecs |
| `client/*` | Renderers, profiles, measurement, bar textures, gesture rules, local profile-authoring commands |
| `SomeStacksServer`, `SsCommand`, `RenderGalleryGenerator` | Common lifecycle, server administration and galleries |
| Loader modules | Registration, transport, callbacks, rendering glue, native automation views |

## Loader integration

| Concern | NeoForge | Fabric |
| --- | --- | --- |
| Metadata | `META-INF/neoforge.mods.toml` | `fabric.mod.json`, mixin config |
| Entrypoint | `SomeStacksNeoForge` | `SomeStacksFabric` plus client entrypoint |
| Networking | versioned payload registrar | payload registry plus a configuration-phase `ProtocolPkt` version marker |
| Gestures | interaction/input events | callbacks plus air-click mixin |
| Automation | whole-run `ResourceHandler<ItemResource>` | generic Transfer API `Storage<ItemVariant>` |
| Edit protection | vanilla plus place/break events | vanilla and break callbacks |

## Runtime and movement model

A maximal vertical run is central. Block entities own local slots; server-only `StackRun` owns resolution, slots, headroom, publication and comparators. Structural edits invalidate per-tick resolution caches.

Block entities do not tick. Mutations batch client, light, comparator, and settlement work; cached fill supplies run signals. `preRemoveSideEffects` drops contents and cascades; block removal hooks then repair surviving runs.

Movement models:

- `StoragePile` is a flat inventory from the base up. Settlement groups exact identities, sorts distinct groups using precomputed fields, restores legal stack sizes, packs down, and removes unneeded top blocks unless permanent or protected.
- `SinglesColumn` is positional. Extraction shifts one visual column down across rotation-aware seams, transports item rotation, preserves unrelated gaps, and does not collapse above a broken block.
- `BarColumn` is positional and overlap-supported. Player extraction and block removal cascade; automation extraction moves the topmost bar into the opened position instead.

Persistence uses `ValueInput`/`ValueOutput` and registry-aware `ItemStack.CODEC`. `StackDataMigration.readTag` preserves raw disk/wire types at the boundary. Encode failure aborts serialization with slot/location context. All types save `DataVersion` and `Items`; Storage adds `Permanent`, and Singles adds `CubeRotations`. Storage and Singles layout rotation is the outer block's `horizontal_facing`; Singles item rotation remains block-entity data. `Permanent` is server-only and is stripped from update tags.

`StackDataMigration` upgrades saved items from the preceding 26.1 (0.8.3) release through DataFixerUpper; absent `DataVersion` reads as current. Unreadable entries, missing-slot entries, and non-compound list values persist in `SetAside` and retry each load. Warnings identify dimension and position; failed retries warn once per location per session. Client updates require current format, bounded inventory, compound entries with unique integer slots, valid Singles rotations, and no server-only fields; rejection changes nothing. Loader update hooks and Fabric's client load branch share validation. Deferred migration marks dirty on attachment. Permanence belongs to the base; block rotation is local; Singles item rotation travels.

## Admission, automation, and edits

Storage takes nonempty items; Bar takes the reloadable `somestacks:ingots` tag; Singles takes allowed non-Bar items. `disable_mods` applies to gestures and automation, while `disable_items` applies only to player deposits; galleries skip both. Internal settlement, gravity, and backfill never reapply admission rules.

Loader-native views adapt `StackRunItemAccess` and span the run plus one headroom block while growth is allowed. Storage slots use item limits; Singles and Bar hold one item. Both loaders keep native transaction adapters per block entity as attachments and share the base ledger. Acceptance reads staged contents through `StackRunItemAccess.acceptsAt`; growth checks read the live world. Root/outer commit applies net removals first, then net additions bottom-up. Both loaders allow one structural extraction position and exclude other pending positions and later insertion. NeoForge prepares growth through native placement events, restores it silently, then installs/fills/publishes at commit. `RunEdit` refuses reentrant automation mutations.

`WorldEdits` checks build limits, replaceability, obstruction, border, spawn and loader authority. Automated edits use the `[SomeStacks]` identity. Player cleanup retains the player; deferred Storage settlement uses automation for automated or mixed causes. NeoForge and Fabric use fake-player factories. NeoForge captures tentative placement in loader snapshots, fires native placement events before publishing, restores block-entity data on denial without neighbor updates, and publishes accepted changes through the loader facility. Removal fires native break events. Fabric placement and growth use vanilla checks. Removal fires the `PlayerBlockBreakEvents` lifecycle. Outer edits emit `BLOCK_PLACE`/`BLOCK_DESTROY`; player content and rotation edits emit `BLOCK_CHANGE` while the stack survives.

## Player interaction and networking

Main-hand client rules recognize permanence, block rotation, item rotation, deposit, placement, and extraction in order. Loader callbacks supply hit classification. NeoForge cancellation and Fabric `SUCCESS` suppress local use while retaining vanilla block-use packets. A payload sends mode and modifier state only when changed.

The only play-phase server-to-client payload is cached configuration. Decoding bounds packet bytes; staging bounds bytes/chunks and rejects duplicates, inconsistent metadata and empty middle chunks. Rejection/disconnect clear staging; complete totals publish enable flags and namespace lists atomically. `/ss item` and `/ss write` act locally; no server packet requests client writes.

`StackBlock.useItemOn`/`useWithoutItem` handle non-sneaking clicks; loader server hooks handle sneaking torch rotation and actions through neighboring non-stack blocks. Both pass vanilla's `BlockHitResult` to `StackInteractions` after ordinary interaction checks. Common handling checks hand, spectator/protection state, item, support, policy, height, obstruction, and authority. Placement plus first deposit is atomic and fires vanilla's `PLACED_BLOCK` advancement trigger; automated growth does not. Deposit, extraction, and Singles item rotation select cells server-side along a ray through the vanilla hit; no client cell index is accepted.

NeoForge consults destination right-click events for neighbor deposits, honoring cancellation and block/item-use denial; a guard prevents duplicate gestures. Fabric consults destination block-use callbacks. One authority per loader handles placement and destination permission. Commands use Brigadier; Fabric forwards unclaimed client `ss` forms.

## Configuration and presentation

`ServerConfig` publishes immutable policy. NeoForge uses a native SERVER config (`ModConfigSpec`); load/reload events apply policy and resync. Commented TOML supports `<world>/serverconfig/somestacks-server.toml` overrides. Fabric's `JsonServerConfig` loads `<world>/serverconfig/somestacks-server.json` from defaults and reports malformed fields independently. `/ss deny` and `/ss gen` save through the backend. `/ss reload` refreshes policy and resyncs players; Fabric rereads JSON, while NeoForge uses native config events. Bar/Singles classification uses the `somestacks:ingots` tag.

Extensions: `somestacks:ingots` data-pack tag; registered `somestacks:block.*` sounds through ordinary resource-pack `sounds.json`; `assets/<namespace>/item_render_overrides/*.json`; and `assets/<namespace>/textures/bars/*.json`. Servers distribute profiles through resource packs.

BERs extract presentation before queued submission: Storage/Singles use `CubeGrid`/`CubeRenderHelper`; Bar uses prepared cuboids, UVs and tints. Profiles resolve user, resource pack, then measurement. `ItemCapture` owns submitted quads, tints, and render types; 2-D plates draw with the item's own render type, which binds the item atlas; measurement visits vanilla model extents. Special features disqualify automatic flat projection. Matching items share presentation per extraction. Throwing 2-D capture draws `stack_cube_error` until reload. Block mode submits native block features; non-model block forms use FIXED. Bar UVs cache per sprite; auto-tint reads the first quad. Measured cache keys format, packs and mod versions. The combined mod pack declares data format 101.1; galleries bypass protection.

Automatic Bar tints resolve on first render after item models are ready; mapped items retain their authored textures, and resource reload clears computed tints. `/ssclienttest` checks resin's orange tint with default resources.

2-D selection: thinnest/longest axis ratio. Cache: 4.

## GameTests

`somestacks_gametest` is excluded from `build`. Shared checks: `common/src/gametest`; loader wrappers use Fabric entrypoints or NeoForge deferred functions with test-instance JSON. Test data format: 101.1. The empty structure fixture is upgraded by vanilla's structure data fixing on load. Both loaders' transfer tests cover transactions, growth, staged support, movement, and slot replacement; NeoForge also covers vetoes. Shared config checks set policy in memory; Fabric tests JSON loading and persistence, NeoForge native TOML persistence. For `/ssclienttest`, aim at a stack; release Shift/V. It also checks capture ownership, special geometry, GUI extents, and inventory-independent grid submission.


