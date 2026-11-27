<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

## Project shape

Java 21; Minecraft 1.21.11 on Forge (pinned 61.1.5), NeoForge, and Fabric; package `com.github.crittscott.somestacks`, id `somestacks`.

`common` folds into loader JARs. Loader glue uses loader prefixes; platform services sit beside entrypoints. `PlatformServices.Backend` supplies paths, versions, networking and edit authority; loaders own adapters. `CommonRegistry` supplies lookups.

`StackBlock` owns lifecycle/comparator plumbing with Storage settlement hooks; `StackRun` shares checked growth installation and top trimming. Geometry supplies one support predicate per positional type for live, staged, and snapshot occupancy. Three blocks; no block items, menus, or recipes:

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
| `StackItemStorage`, `SlotAccess` | Mutable local storage and read-only slot view |
| `CubeGrid`, `util/*CubeIdx`, `ViewRay` | Grid coordinates, ray selection, geometry, support and seams |
| `ServerConfig`, `TomlPolicyFile`, `ServerConfigMigration`, loader config adapters | Policy, its `Setting` table, persistence, JSON migration |
| `WorldEdits`, `EditAuthority` | World edits, automation authority, adjacent-target protection |
| `StackInteractions`, `StackGesture`, `ServerGestureState` | Vanilla block-use interpretation and gesture state |
| `network/*` | Gesture-state and server-to-client synchronization payloads |
| `renderconfig/*` | Shared render override types, JSON validation, and codecs |
| `client/*` | Rendering, profiles, measurement, textures, client gestures and commands |
| `SomeStacksServer`, `SsCommand`, `RenderGalleryGenerator` | Server lifecycle, administration and galleries |
| Loader modules | Registration, transport, callbacks, rendering glue, native automation views |

## Loader integration

| Concern | Forge | NeoForge | Fabric |
| --- | --- | --- | --- |
| Metadata | `META-INF/mods.toml` | `META-INF/neoforge.mods.toml` | `fabric.mod.json`, mixin config |
| Entrypoint | `SomeStacksForge` | `SomeStacksNeoForge` | `SomeStacksFabric` plus client entrypoint |
| Networking | versioned `SimpleChannel` | versioned payload registrar | payload registry plus a configuration-phase `ProtocolPkt` version marker |
| Gestures | interaction/input events | interaction/input events | callbacks plus air-click mixin |
| Automation | whole-run `IItemHandler` | whole-run `ResourceHandler<ItemResource>` | Transfer API `Storage<ItemVariant>` |
| Edit protection | vanilla plus place/break events | vanilla plus place/break events | vanilla, Common Protection API queries, and break callbacks |

Forge: EventBus 7 `BusGroup`, boolean cancellation, `Result` denial.

## Runtime and movement model

A maximal vertical run is central. Block entities own local slots; server-only `StackRun` owns resolution, slots, headroom, publication and comparators. All members share one per-tick resolved run; structural edits invalidate affected caches.

Block entities do not tick. Mutations batch publication and settlement; cached slot contributions supply comparators. Removal drops local contents and cascades, then repairs surviving runs.

Movement models:

- `StoragePile` is a flat inventory from the base up. Settlement groups exact identities, sorts groups by item id, damage, and lazily memoized component text, restores legal stack sizes, writes only changed slots, packs down, and removes unneeded top blocks unless permanent or protected.
- `SinglesColumn` is positional. Extraction shifts one visual column down across rotation-aware seams, transports item rotation, preserves unrelated gaps, and does not collapse above a broken block.
- `BarColumn` is positional and overlap-supported. Player extraction and block removal cascade; automation extraction moves the topmost bar into the opened position instead.

Persistence uses `ValueInput`/`ValueOutput` and registry-aware `ItemStack.CODEC`. `StackDataMigration.readTag` preserves raw disk/wire types at the boundary. Encode failure aborts serialization with slot/location context. All types save `DataVersion` and `Items`; Storage adds `Permanent`, and Singles adds `CubeRotations`. Storage and Singles layout rotation is the outer block's `horizontal_facing`; Singles item rotation remains block-entity data. Update tags omit `Permanent`, set-aside data, container contents, and book pages.

`StackDataMigration` owns version upgrades, raw SetAside retention/retries, Storage overflow splitting, and session diagnostics. It upgrades the preceding 1.21.8 release through DataFixerUpper; missing DataVersion means current. Failures retain their raw tags and original slots. Entities defer reporting/dirty marking until attachment. Client ingress rejects malformed/version-mismatched inventories, duplicate/noninteger slots, invalid rotations, and server-only fields atomically before inherited component loading. Fabric guards native loading with a mixin; Forge and NeoForge use registered subclasses overriding their native update hooks. Rejection logs once.

## Admission, automation, and edits

Storage takes nonempty items; Bar takes the reloadable `somestacks:ingots` tag; Singles takes allowed non-Bar items. `disable_mods` applies to gestures and automation, while `disable_items` applies only to player deposits; galleries skip both. Internal settlement, gravity, and backfill never reapply admission rules.

Native views expose `StackRunItemAccess` plus one headroom block. Storage capacity is min(64, item limit); positional slots hold one item. Forge's `RunItemHandler` simulates or commits each call immediately. NeoForge/Fabric transient base-block adapters share `RunTransferLedger` staging, rollback snapshots, extraction restrictions, and ordered replay; native transaction participation stays in each loader. `acceptsAt` checks staged support before growth; bulk insertion shares a permission query, keeps collision per position, and rechecks authority at commit. Root/outer commits remove first, then insert bottom-up with fresh run resolution. NeoForge prepares growth through native placement events, restores it silently, then installs/fills/publishes at commit; bulk offers coalesce repeated growth vetoes within the offer. `RunEdit` blocks reentrant mutations.

`WorldEdits.mayPlace` is the one placement and growth gate (build height, replaceability, the actor's `mayUseItemAt`, border, spawn, loader authority); `placeChecked` adds obstruction. Automation uses the `[SomeStacks]` identity: Forge caches a connection-safe actor per dimension; NeoForge and Fabric use fake-player factories. Removal applies vanilla game-mode restrictions first. Cleanup retains a responsible player, including the collapse or trimming a player's break causes; automated or mixed Storage cleanup uses automation. Forge/NeoForge fire place events against snapshots and restore denied state. Fabric queries Common Protection API for placement, neighbor use, and removal around native break callbacks. Placement completion, including `PLACED_BLOCK`, lives in `WorldEdits`. Edits emit `BLOCK_PLACE`/`BLOCK_DESTROY`, or `BLOCK_CHANGE` for surviving player content/rotation edits.

## Player interaction and networking

`StackGesture` shares pure main-hand intent between client and server: permanence, block rotation, item rotation, modified deposit/placement, then extraction; targeting stays server-side. Forge/NeoForge handle block clicks at `LOWEST` (Forge syncs state at `HIGHEST`); cancellation and Fabric `SUCCESS` keep vanilla use packets. A payload sends mode (stable id) and modifier when changed; published policy moves a disabled mode on.

`ClientServerSettings` publishes immutable policy; `SomeStacksClient` owns shared join/disconnect/stopping lifecycle. All three loaders deliver one bounded `ConfigSyncPkt` snapshot at login and when policy changes. Policy lists bound namespace size/count. Protocol 6. Disconnect clears settings and diagnostics. Client render commands never accept remote write requests.

`StackBlock.useItemOn`/`useWithoutItem` handle non-sneaking clicks; loader hooks handle sneaking torch rotation and neighbor actions. `StackInteractions` validates hand, spectator/protection state, items, support, policy, height, obstruction, and authority. Ordinary new placement resolves context-sensitive replacement through `BlockPlaceContext`; overflow and neighbor deposits retain their target resolution. `StackBlock` constructs state for both player placement and growth. Placement plus first deposit is atomic. Unhandled uses pass through; plain extraction attempts remain consumed. Rotation reads are pure; edits use native facing, inherited before Storage placement. Cell selection follows the server ray through the vanilla hit; clients send no cell index.

Neighbor deposits consult Forge/NeoForge right-click events or Fabric Common Protection API queries. Guards prevent recursive gestures; each loader owns placement authority. Brigadier handles commands; Fabric forwards unclaimed client `ss` forms.

## Configuration and presentation

`ServerConfig` publishes immutable policy. Forge/NeoForge use native SERVER configs (`ForgeConfigSpec`/`ModConfigSpec`) with `<world>/serverconfig/somestacks-server.toml`; specs and `TomlPolicyFile` (Fabric) build from `ServerConfig.Setting`. Backends share common setting keys/defaults and return settings; the facade applies them. `/ss deny` and `/ss gen` persist edits. Reloads and list edits share server-thread publication/logging: changed client snapshots resync players; gallery enablement/permission changes resend command trees. Policy logs include gallery access and deny-list counts.

BERs prepare presentation before queued submission using frame light. Storage/Singles share `GridStackBER`, `CubeGrid`, and `CubeRenderHelper`, caching until content, rotation, or profile changes; time-dependent models freeze. Bar caches geometry/UVs/tints until content or resource-generation changes. Profiles resolve user, resource pack, then measurement. `ItemCapture` owns quads, tints, and render types; measurement uses vanilla extents. Special features disqualify automatic 2-D projection. Item-atlas art caches 2,048 recent stacks and submits once per render type/block; failures draw stack_cube_error until reload. Block mode submits native features, falling back to FIXED for non-model blocks. Bar entry codecs and native file discovery retain pack paths. Measured caches key format, packs, and mod versions. `/ss write changed` keeps malformed entries and refuses an unreadable file. Data format: 94.1.

2-D selection uses thinnest/longest axis ratio; cache format 4.

## GameTests

`somestacks_gametest` is excluded from build. Fabric entrypoints and Forge/NeoForge deferred functions/JSON wrap common scenarios, which click through vanilla block use and reach automation through `GameTestScaffold.Automation`. Fabric installs a test Common Protection API provider. `/ssclienttest` checks callbacks, capture/geometry, GUI extents, cache invalidation, queued rendering, and atomic update rejection including inherited components. Gallery-budget tests run alone.

