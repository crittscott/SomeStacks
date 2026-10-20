<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

## Project shape

Java 25; Minecraft 26.1.1 (unobfuscated, no mappings) on Fabric and NeoForge; package `com.github.crittscott.somestacks`, id `somestacks`.

`common` folds into loader JARs. `PlatformServices.Backend` supplies paths, versions, networking and edit authority; loaders own adapters. `CommonRegistry` supplies lookups. NeoForge glue uses loader prefixes; platform services sit beside entrypoints.

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
| `StackItemStorage`, `SlotAccess` | Mutable local storage and a read-only slot view |
| `CubeGrid`, `util/*CubeIdx`, `ViewRay` | Shared cube-grid coordinates and ray selection plus per-type geometry, support and seams |
| `ServerConfig`, loader config adapters | Per-world policy and native TOML/Fabric JSON persistence |
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

A maximal vertical run is central. Block entities own local slots; server-only `StackRun` owns resolution, slots, headroom, publication and comparators. All members share one resolved run per tick; structural edits invalidate it.

Block entities do not tick. Mutations batch publication and settlement; cached slot contributions supply comparators. `preRemoveSideEffects` drops contents and cascades; block removal hooks then repair surviving runs.

Movement models:

- `StoragePile` is a flat inventory from the base up. Settlement groups exact identities, sorts distinct groups by item id, damage, and lazily memoized component text, restores legal stack sizes, packs down, and removes unneeded top blocks unless permanent or protected.
- `SinglesColumn` is positional. Extraction shifts one visual column down across rotation-aware seams, transports item rotation, preserves unrelated gaps, and does not collapse above a broken block.
- `BarColumn` is positional and overlap-supported. Player extraction and block removal cascade; automation extraction moves the topmost bar into the opened position instead.

Persistence uses registry-aware codecs and `ValueInput`/`ValueOutput`. Raw field collection preserves malformed disk/wire types. Encode failure aborts with slot/location context. Saved fields: `DataVersion`, `Items`, Storage `Permanent`, Singles `CubeRotations`. Layout rotation uses native `horizontal_facing`; Singles item rotation stays in entity data. Update tags strip `Permanent`.

`StackDataMigration` upgrades saved items from the preceding 26.1 (0.8.3) release through DataFixerUpper; absent `DataVersion` reads as current. Unreadable entries persist verbatim in `SetAside` and retry each load. Warnings identify dimension/position; failed retries warn once per location/session. Client ingress atomically validates format, bounds, unique integer slots, Singles rotations, and server-only fields. Fabric guards native `loadWithComponents` with a client-only mixin; NeoForge registers subclasses with checked native overrides. Both preflight owned data before inherited components load. Migration marks dirty on attachment. Local storage has immediate mutations and save/load methods; run simulation and loader transactions remain. Permanence belongs to the base. Rotation reads are pure; setters update native facing. Storage inherits neighboring facing before placement; adoption retains permanence. Singles item rotation travels.

## Admission, automation, and edits

Storage takes nonempty items; Bar takes the reloadable `somestacks:ingots` tag; Singles takes allowed non-Bar items. `disable_mods` applies to gestures and automation, while `disable_items` applies only to player deposits; galleries skip both. Internal settlement, gravity, and backfill never reapply admission rules.

Loader-native views adapt `StackRunItemAccess` over the run plus one headroom block. Storage uses item limits; Singles/Bar hold one item. Native attachments share the resolved base entity's ledger; bulk operations delegate once. `acceptsAt` checks staged support before growth. Bulk offers share a scoped permission query; height, enablement, replaceability and collision stay live, and commit rechecks authority. Root/outer commits remove first, then insert bottom-up. Positional extraction permits one position without other pending positions or later insertion. NeoForge prepares growth through native placement events, restores it silently, then installs/fills/publishes at commit. `RunEdit` refuses reentry.

`WorldEdits` resolves nullable actors centrally and checks build limits, replaceability, obstruction, border, spawn and loader authority. Automated edits use the `[SomeStacks]` identity. Player cleanup retains the player; deferred Storage settlement uses automation for automated or mixed causes. NeoForge and Fabric use fake-player factories. NeoForge captures tentative placement, fires native events, silently restores denied snapshots and block entities, and publishes accepted edits through the loader. Removal fires native break events. Fabric placement and growth use vanilla checks. Removal fires the `PlayerBlockBreakEvents` lifecycle. Outer edits emit `BLOCK_PLACE`/`BLOCK_DESTROY`; player content and rotation edits emit `BLOCK_CHANGE` while the stack survives.

## Player interaction and networking

Main-hand client rules recognize permanence, block rotation, item rotation, generic modified-item cancellation, then extraction. Targeting stays server-side. Loaders classify hits. NeoForge cancellation and Fabric `SUCCESS` suppress local use while retaining vanilla block-use packets. A payload sends mode and modifier state only when changed.

`ConfigSyncReceiver` stages the chunked clientbound config snapshot; Storage enable flags are named explicitly. Decoding bounds packet bytes; staging bounds bytes/chunks and rejects duplicates, inconsistent metadata and empty middle chunks. Rejection/disconnect clear staging; complete totals publish enable flags and namespace lists atomically. `/ss item` and `/ss write` act locally; no server packet requests client writes.

`StackBlock.useItemOn`/`useWithoutItem` handle non-sneaking clicks; loader server hooks handle sneaking torch rotation and actions through neighboring non-stack blocks. Both forward vanilla hits. `StackInteractions` retains hand, spectator, protection, item, support, policy, height, and obstruction checks; per-type deposit/rotation dispatch reads one entity. Placement plus first deposit is atomic and fires vanilla's `PLACED_BLOCK` advancement trigger; automated growth does not. Native use returns `PASS` for unhandled gestures; plain extraction attempts remain consumed. Deposit, extraction, and Singles item rotation select cells server-side along a ray through the vanilla hit; no client cell index is accepted.

Neighbor deposits consult NeoForge right-click events or Fabric block-use callbacks. Guards suppress recursive gestures. Loader authorities own placement permission. Brigadier handles commands; Fabric forwards unclaimed client `ss` forms.

## Configuration and presentation

`ServerConfig` owns setting keys/defaults and publishes immutable policy. Backends return settings; the facade applies them. NeoForge config events share one handler. NeoForge uses a native SERVER config (`ModConfigSpec`); load/reload events apply policy and resync. Commented TOML supports `<world>/serverconfig/somestacks-server.toml` overrides. Fabric returns per-field validated settings from `<world>/serverconfig/somestacks-server.json`; the facade owns publication. `/ss deny` and `/ss gen` save through the backend. Command and native reloads share server-thread `reloadPolicy`; gallery visibility/permission changes resend command trees, and changed client fields rebuild/send the cached config sequence. 

Resource-pack and data-pack extension paths are listed in `orientation-player.md`.

BERs extract presentation before queued submission using inherited frame light. Storage/Singles share `CubeGrid`/`CubeRenderHelper`; models cache until content, rotation or profile changes, freezing time-dependent models. Bar caches cuboids, UVs and tints until content or resource generation changes. Profiles resolve user, pack, then measurement. `ItemCapture` owns quads/tints/render types; special features disqualify automatic flat projection. 2-D art caches 2,048 recent stacks until reload and submits once per render type/block on the item atlas. Failed captures draw `stack_cube_error` until reload. Block mode submits native features, falling back to FIXED for non-model blocks. Bar UVs cache per sprite; auto-tint reads the first quad. Measured caches key format, packs and mod versions. The combined mod pack declares data format 101.1; galleries bypass protection.

Automatic Bar tints resolve on first render, once models are ready; authored colors stay; reload clears computed tints. `/ssclienttest` checks resin's default orange tint.

2-D axis-ratio selection; cache format 4.

## GameTests

`somestacks_gametest` is excluded from `build`. Shared checks: `common/src/gametest`; loader wrappers use Fabric entrypoints or NeoForge deferred functions with test-instance JSON. Test data format: 101.1. Vanilla data fixing upgrades the empty structure fixture. Both loaders' transfer tests cover transactions, growth, staged support, movement, slot replacement; NeoForge also covers vetoes. Shared config checks set policy in memory; Fabric tests JSON load/save, NeoForge native TOML persistence. `AutomationAndPersistenceChecks` and matching loader wrappers cover persistence. `/ssclienttest` exercises native initial/subsequent client updates, including inherited-component rejection; aim at a stack and release Shift/V. It checks capture/GUI/profile/grid behavior and Bar presentation reuse plus content/resource invalidation. The gallery-budget check runs in its own environment.


