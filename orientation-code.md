<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

## Project shape

Java 25; Minecraft 26.1 (unobfuscated, no mappings) on Fabric and NeoForge; package `com.github.crittscott.somestacks`, id `somestacks`.

`common` folds into loader JARs. `PlatformServices.Backend` supplies paths, versions, networking and edit authority; loaders own adapters. `CommonRegistry` supplies lookups. NeoForge glue uses loader prefixes; platform services sit beside entrypoints.

`StackBlock` owns shared lifecycle/comparator plumbing; `StackRun` shares growth and top trimming; geometry has one support predicate per positional type.

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
| `StackBlockEntity`, `block/*StackBE` | Inventory, NBT, sync, lighting, batching, shapes, rotations and runs |
| `StackRun`, `StackRunItemAccess`, `StoragePile`, `SinglesColumn`, `BarColumn` | Runs, slots, publication, comparators, mutation, growth and cleanup |
| `StackItemStorage`, `SlotAccess` | Mutable local storage and a read-only slot view |
| `CubeGrid`, `util/*CubeIdx`, `ViewRay` | Grid coordinates, ray selection, geometry, support and seams |
| `ServerConfig`, loader config adapters | Per-world policy and native TOML/Fabric TOML persistence |
| `WorldEdits`, `EditAuthority` | World edits, automation authority, adjacent-target protection |
| `StackInteractions`, `ServerGestureState` | Vanilla block-use interpretation and gesture state |
| `network/*` | Gesture-state and server-to-client synchronization payloads |
| `renderconfig/*` | Shared render override types, JSON validation, and codecs |
| `client/*` | Renderers, profiles, measurement, bar textures, client gestures and settings, local profile-authoring commands |
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
| Edit protection | vanilla plus place/break events | vanilla, Common Protection API destination queries, and break callbacks |

## Runtime and movement model

A maximal vertical run is central. Block entities own local slots; server-only `StackRun` owns resolution, slots, headroom, publication and comparators. All members share one resolved run per tick; structural edits invalidate it.

Block entities do not tick. Mutations batch publication and settlement; cached slot contributions feed comparators. `preRemoveSideEffects` drops contents and cascades; block removal hooks then repair surviving runs.

Movement models:

- `StoragePile` is a flat inventory from the base up. Settlement groups exact identities, sorts distinct groups by item id, damage, and lazily memoized component text, restores legal stack sizes, packs down, and removes unneeded top blocks unless permanent or protected.
- `SinglesColumn` is positional. Extraction shifts one visual column down across rotation-aware seams, transports item rotation, preserves unrelated gaps, and does not collapse above a broken block.
- `BarColumn` is positional and overlap-supported. Player extraction and block removal cascade; automation extraction moves the topmost bar into the opened position instead.

Persistence uses registry-aware codecs and `ValueInput`/`ValueOutput`. Raw field collection preserves malformed disk/wire types. Encode failure aborts with context. Saved fields: `DataVersion`, `Items`, Storage `Permanent`, Singles `CubeRotations`. Layout rotation uses native `horizontal_facing`; Singles item rotation stays in entity data. Update tags strip `Permanent`.

`StackDataMigration` upgrades saved items from the preceding 1.21.11 (0.8.3) release through DataFixerUpper; absent `DataVersion` reads as current. It also owns `SetAside` retention, retries, and load reporting; unreadable entries persist verbatim and retry each load. Warnings identify dimension/position; failed retries warn once per location/session. Client ingress atomically validates format, bounds, unique integer slots, Singles rotations, and server-only fields. Fabric guards native `loadWithComponents` with a client-only mixin; NeoForge uses checked subclass overrides. Both preflight owned data before inherited components; rejection logs once per connection. Over-cap Storage entries split into free slots, then SetAside. Migration marks dirty on attachment. Permanence belongs to the base. Rotation reads are pure; setters update native facing, inherited before Storage placement; adoption retains permanence. Singles item rotation travels.

## Admission, automation, and edits

Storage takes nonempty items; Bar takes the reloadable `somestacks:ingots` tag; Singles takes allowed non-Bar items. `disable_mods` applies to gestures and automation, while `disable_items` applies only to player deposits; galleries skip both. Internal settlement, gravity, and backfill never reapply admission rules.

Loader-native views adapt `StackRunItemAccess` over the run plus one headroom block. Storage capacity is min(64, item limit); Singles/Bar hold one item. Native adapters share the base block's `RunTransferLedger`; unresolved runs expose no slots. `acceptsAt` checks staged support before growth. Bulk offers share a scoped permission query; height, enablement, replaceability and collision stay live, and commit rechecks authority. Root/outer commits remove first, then insert bottom-up. Positional extraction permits one position without other pending positions or later insertion. NeoForge prepares growth through native placement events, restores it silently, then installs/fills/publishes at commit. `RunEdit` refuses reentry.

`WorldEdits` resolves nullable actors centrally and checks build limits, replaceability, obstruction, border, spawn and loader authority. Automated edits use the `[SomeStacks]` identity. Player cleanup retains the player; deferred Storage settlement uses automation for automated or mixed causes. NeoForge and Fabric use fake-player factories. NeoForge captures tentative placement, fires native events, silently restores denials, and publishes accepted edits. Removal applies vanilla game-mode restrictions, then native break events. NeoForge coalesces repeated growth vetoes per bulk offer. Fabric queries Common Protection API for placement, neighbor use, and removal, then fires `PlayerBlockBreakEvents`. `WorldEdits` owns placement completion. Player stack creation fires vanilla's `PLACED_BLOCK` criterion. Outer edits emit `BLOCK_PLACE`/`BLOCK_DESTROY`; player content and rotation edits emit `BLOCK_CHANGE` while the stack survives.

## Player interaction and networking

`StackGesture` shares pure main-hand intent between client and server: permanence, block rotation, item rotation, modified deposit/placement, then extraction. Targeting stays server-side. Loaders classify hits. NeoForge cancellation and Fabric `SUCCESS` suppress local use while retaining vanilla block-use packets. A payload sends mode and modifier state only when changed.

`ClientServerSettings` atomically publishes one bounded `ConfigSyncPkt` snapshot; `SomeStacksClient` owns shared join/disconnect/stopping, and disconnect restores defaults. Fabric sends it as a play payload; NeoForge derives it from native config sync. Protocol 6. `/ss item` and `/ss write` act locally; no server packet requests client writes.

`StackBlock.useItemOn`/`useWithoutItem` handle non-sneaking clicks; loader hooks handle sneaking torch rotation and neighbor actions, forwarding vanilla hits. `StackInteractions` checks hand, spectator, protection, item, support, policy, height, and obstruction; per-type deposit/rotation dispatch reads one entity. New placement replaces contextually via `BlockPlaceContext`; `StackBlock` builds placement and growth state. Placement plus first deposit is atomic. Native use returns `PASS` for unhandled gestures; plain extraction attempts remain consumed. Cells are selected server-side along a ray through the vanilla hit; clients send no cell index.

Neighbor deposits consult NeoForge right-click events or Fabric Common Protection API queries. Guards suppress recursive gestures. Loader authorities own placement permission. Brigadier handles commands; Fabric forwards unclaimed client `ss` forms.

## Configuration and presentation

`ServerConfig` owns setting keys/defaults and publishes immutable policy. Backends return settings; the facade applies them. NeoForge config events share one handler. NeoForge uses a native synced SERVER `ModConfigSpec`; client load/reload events publish policy. Commented TOML supports `<world>/serverconfig/somestacks-server.toml` overrides. `TomlPolicyFile` reads Fabric TOML from `config/somestacks-server.toml`. `/ss deny` and `/ss gen` save through the backend. Reloads and list edits share server-thread `publishPolicy`, which resends command trees on gallery access changes and Fabric snapshots only when changed. 

BERs extract presentation before queued submission using inherited frame light. Storage/Singles share `GridStackBER`, `CubeGrid`, and `CubeRenderHelper`; models cache until content, rotation or profile changes, freezing time-dependent models. Bar caches cuboids, UVs and tints until content or resource generation changes. Profiles resolve user, pack, then measurement. `ItemCapture` owns quads/tints/render types; special features disqualify automatic flat projection. 2-D art caches 2,048 recent stacks and submits once per render type/block. Failed captures draw `stack_cube_error` until reload. Block mode submits native features, falling back to FIXED for non-model blocks. Bar UVs cache per sprite; auto-tint reads the first quad. Listeners use `FileToIdConverter`; Bar entries use codecs. Measured caches key format, packs and mod versions. The combined mod pack declares data format 101.1; galleries bypass protection.

Automatic Bar tints resolve on first render; authored colors stay; reload clears computed tints. `/ssclienttest` checks resin's orange tint with default resources.

2-D axis-ratio selection; cache format 4.

## GameTests

`somestacks_gametest` is excluded from `build`. Fabric entrypoints and NeoForge deferred functions/JSON wrap `common/src/gametest` checks. Test data format: 101.1. Loader transfer tests cover staged support, slot replacement, rollback, growth, and movement; NeoForge also covers vetoes. TOML config and protection-provider tests are Fabric-only; shared tests cover drop-rule-off Bars, Adventure cleanup, and grass/snow replacement. `AutomationAndPersistenceChecks` cover persistence. `/ssclienttest` (aim at a stack, release Shift/V) checks native client updates and inherited-component rejection, capture/GUI/profile/grid behavior, and Bar reuse/invalidation. The gallery-budget check runs in its own environment.



