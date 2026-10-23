<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

## Project shape

Java 25; Minecraft 26.3 (unobfuscated, no mappings) on Fabric and NeoForge; package `com.github.crittscott.somestacks`, id `somestacks`.

`common` folds into loader JARs. NeoForge glue uses loader prefixes; platform services sit beside entrypoints. `PlatformServices.Backend` supplies paths, versions, networking and edit authority; loaders own adapters. `CommonRegistry` supplies lookups.

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
| `ServerConfig`, loader config adapters | Per-world policy and native TOML/Fabric JSON persistence |
| `WorldEdits`, `EditAuthority` | World edits, automation authority, adjacent-target protection |
| `StackInteractions`, `ServerGestureState` | Vanilla block-use interpretation and gesture state |
| `network/*` | Gesture-state and server-to-client synchronization payloads |
| `renderconfig/*` | Shared render override types, JSON validation, and codecs |
| `client/*` | Rendering, profiles, measurement, textures, client gestures and commands |
| `SomeStacksServer`, `SsCommand`, `RenderGalleryGenerator` | Server lifecycle, administration and galleries |
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

A maximal vertical run is central. Block entities own local slots; server-only `StackRun` owns resolution, slots, headroom, publication and comparators. All members share one per-tick resolved run; structural edits invalidate affected caches.

Block entities do not tick. Mutations batch publication and settlement; cached slot contributions supply comparators. Removal drops local contents and cascades, then repairs surviving runs.

Movement models:

- `StoragePile` is a flat inventory from the base up. Settlement groups exact identities, sorts distinct groups using item id, damage, and lazily memoized component text, restores legal stack sizes, packs down, and removes unneeded top blocks unless permanent or protected.
- `SinglesColumn` is positional. Extraction shifts one visual column down across rotation-aware seams, transports item rotation, preserves unrelated gaps, and does not collapse above a broken block.
- `BarColumn` is positional and overlap-supported. Player extraction and block removal cascade; automation extraction moves the topmost bar into the opened position instead.

Persistence uses `ValueInput`/`ValueOutput` and registry-aware `ItemStack.CODEC`. `StackDataMigration.readTag` preserves raw disk/wire types at the boundary. Encode failure aborts serialization with slot/location context. All types save `DataVersion` and `Items`; Storage adds `Permanent`, and Singles adds `CubeRotations`. Storage and Singles layout rotation is the outer block's `horizontal_facing`; Singles item rotation remains block-entity data. `Permanent` is server-only and is stripped from update tags.

`StackDataMigration` owns version upgrades, raw SetAside retention/retries, Storage overflow splitting, and session diagnostics. It upgrades the preceding 26.1 (0.8.3) release; missing DataVersion means current. Failures retain their raw tags and original slots. Entities defer reporting/dirty marking until attachment. Client ingress rejects malformed/version-mismatched inventories, duplicate/noninteger slots, invalid rotations, and server-only fields atomically before inherited component loading. Fabric guards native loading with a mixin; NeoForge uses registered subclasses. Rejection logs once per connection. Permanence belongs to the base; layout rotation is block state; Singles item rotation travels with contents.

## Admission, automation, and edits

Storage takes nonempty items; Bar takes the reloadable `somestacks:ingots` tag; Singles takes allowed non-Bar items. `disable_mods` applies to gestures and automation, while `disable_items` applies only to player deposits; galleries skip both. Internal settlement, gravity, and backfill never reapply admission rules.

Native views expose `StackRunItemAccess` plus one headroom block. Storage capacity is min(64, item limit); positional slots hold one item. Transient base-block adapters share `RunTransferLedger` staging, rollback snapshots, extraction restrictions, and ordered replay. Native transaction participation stays in each loader. `acceptsAt` checks staged support before growth; bulk insertion shares a permission query, keeps collision per position, and rechecks authority at commit. Root/outer commits remove first, then insert bottom-up with fresh run resolution. Positional extraction permits one position without other pending positions or later insertion. NeoForge prepares growth through native placement events, restores it silently, then installs/fills/publishes at commit. `RunEdit` blocks reentrant mutations.

`WorldEdits` resolves nullable actors and checks build limits, replaceability, obstruction, border, spawn and loader authority. Both loaders use fake players with the `[SomeStacks]` automation identity. Removal applies vanilla game-mode block restrictions before loader hooks. Cleanup retains a responsible player; automated or mixed Storage cleanup uses automation. NeoForge fires placement events against captured snapshots, restores denied block/entity state silently, and publishes accepted edits. Fabric uses Common Protection API for player/automation placement, neighbor use, and removal queries, with native break callbacks around removals. It sends no synthetic use callbacks. NeoForge bulk offers coalesce repeated growth vetoes within the offer and retry authority on the next offer. Placement completion lives in `WorldEdits`. Player creation fires `PLACED_BLOCK`. Edits emit `BLOCK_PLACE`/`BLOCK_DESTROY`, or `BLOCK_CHANGE` for surviving player content/rotation edits.

## Player interaction and networking

`StackGesture` shares pure main-hand intent between client and server: permanence, block rotation, item rotation, modified deposit/placement, then extraction; targeting stays server-side. NeoForge cancellation and Fabric `SUCCESS` suppress local use while retaining vanilla block-use packets. A payload sends mode and modifier state only when changed.

`ClientServerSettings` publishes immutable policy; `SomeStacksClient` owns shared lifecycle and preserves policy on join. Fabric sends one bounded `ConfigSyncPkt`; NeoForge applies native SYNCED config with no clientbound custom payload. Policy lists bound namespace size/count and reject malformed ingress. Protocol 6 covers both formats. Disconnect clears settings and diagnostics. Client render commands never accept remote write requests.

`StackBlock.useItemOn`/`useWithoutItem` handle non-sneaking clicks; loader hooks handle sneaking torch rotation and neighbor actions. `StackInteractions` validates hand, spectator/protection state, items, support, policy, height, obstruction, and authority. Ordinary new placement resolves context-sensitive replacement through `BlockPlaceContext`; overflow and neighbor deposits retain their target resolution. `StackBlock` constructs state for both player placement and growth. Placement plus first deposit is atomic. Unhandled uses pass through; plain extraction attempts remain consumed. Rotation reads are pure; edits use native facing, inherited before Storage placement. Cell selection follows the server ray through the vanilla hit; clients send no cell index.

Neighbor deposits consult NeoForge right-click events or Fabric Common Protection API queries. Guards prevent recursive gestures; each loader owns placement authority. Brigadier handles commands; Fabric forwards unclaimed client `ss` forms.

## Configuration and presentation

`ServerConfig` publishes immutable policy. NeoForge uses native SYNCED `ModConfigSpec` with the explicit `somestacks-server.toml` filename and loader-managed world overrides; Fabric reads `<world>/serverconfig/somestacks-server.json` with per-field defaults. Backends share common setting keys/defaults and return settings; the facade applies them. `/ss deny` and `/ss gen` persist edits. Reloads and list edits share server-thread publication/logging. Fabric sends only changed normalized snapshots; NeoForge owns native sync. Gallery enablement/permission changes resend command trees. Policy logs include gallery access and deny-list counts.


BERs prepare presentation before queued submission using frame light. Storage/Singles share `GridStackBER`, `CubeGrid`, and `CubeRenderHelper`, caching until content, rotation, or profile changes; time-dependent models freeze. Bar caches geometry/UVs/tints until content or resource-generation changes. Profiles resolve user, resource pack, then measurement. `ItemCapture` owns quads, tints, and render types; measurement uses vanilla extents. Special features disqualify automatic 2-D projection. Item-atlas art caches 2,048 recent stacks and submits once per render type/block; failures draw stack_cube_error until reload. Block mode submits native features, falling back to FIXED for non-model blocks. Bar entry codecs and native file discovery retain pack paths. Measured caches key format, packs, and mod versions. Data format: 121.0.

Bar tints resolve on first render; authored colors stay and reload clears computed tints. Client smoke tests cover resin tint.

2-D selection uses thinnest/longest axis ratio; cache format 4.

## GameTests

`somestacks_gametest` is excluded from build. Fabric entrypoints and NeoForge deferred functions/JSON wrap common scenarios. Tests cover placement/protection, block-drop-disabled Bar conservation, Adventure cleanup, vegetation/snow replacement, persistence/migration, capacity, native transactions, support/movement, vetoes, and policy sync. `/ssclienttest` checks callbacks, capture/geometry, GUI extents, cache invalidation, queued rendering, and atomic update rejection including inherited components. Gallery-budget tests use a separate environment.
