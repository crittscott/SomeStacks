<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

See `orientation-player.md` and `orientation-build.md`.

## Project shape

Java 25; Minecraft 26.3 (unobfuscated, no mappings) on Fabric and NeoForge; package `com.github.crittscott.somestacks`, id `somestacks`.

`common` folds into loader JARs. NeoForge outer-layer classes use loader prefixes; both platform-service implementations sit beside their entrypoints. `PlatformServices.Backend` supplies paths, versions, networking and edit authority; loaders own adapters. `CommonRegistry` supplies lookups.

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
| Edit protection | vanilla plus place/break events | vanilla and break callbacks |

## Runtime and movement model

A maximal vertical run is central. Block entities own local slots; server-only `StackRun` owns resolution, slots, headroom, publication and comparators. All members share one per-tick resolved run; structural edits invalidate affected caches.

Block entities do not tick. Mutations batch client, light, comparator, and settlement work; cached per-slot fill fractions supply run signals. `preRemoveSideEffects` drops contents and cascades; block removal hooks then repair surviving runs.

Movement models:

- `StoragePile` is a flat inventory from the base up. Settlement groups exact identities, sorts distinct groups using item id, damage, and lazily memoized component text, restores legal stack sizes, packs down, and removes unneeded top blocks unless permanent or protected.
- `SinglesColumn` is positional. Extraction shifts one visual column down across rotation-aware seams, transports item rotation, preserves unrelated gaps, and does not collapse above a broken block.
- `BarColumn` is positional and overlap-supported. Player extraction and block removal cascade; automation extraction moves the topmost bar into the opened position instead.

Persistence uses `ValueInput`/`ValueOutput` and registry-aware `ItemStack.CODEC`. `StackDataMigration.readTag` preserves raw disk/wire types at the boundary. Encode failure aborts serialization with slot/location context. All types save `DataVersion` and `Items`; Storage adds `Permanent`, and Singles adds `CubeRotations`. Storage and Singles layout rotation is the outer block's `horizontal_facing`; Singles item rotation remains block-entity data. `Permanent` is server-only and is stripped from update tags.

`StackDataMigration` upgrades the preceding 26.1 (0.8.3) release's items through DataFixerUpper; absent `DataVersion` means current. Unreadable or malformed entries persist verbatim in `SetAside` and retry on load. Warnings identify dimension/position; failed retries warn once per location/session. Client updates require current version, bounded inventories, unique integer slots, valid Singles rotations, and no server-only fields; rejection changes nothing. Client ingress validates before inherited component loading: Fabric guards `loadWithComponents` with a client mixin; registered NeoForge entity subclasses override native hooks with compile-time checks. Accepted updates use the shared client load branch. Deferred migration marks dirty on attachment. Permanence belongs to the base; block rotation is local; Singles item rotation travels.

## Admission, automation, and edits

Storage takes nonempty items; Bar takes the reloadable `somestacks:ingots` tag; Singles takes allowed non-Bar items. `disable_mods` applies to gestures and automation, while `disable_items` applies only to player deposits; galleries skip both. Internal settlement, gravity, and backfill never reapply admission rules.

Loader-native views adapt `StackRunItemAccess` and span the run plus one headroom block while growth is allowed. Storage slots use item limits; Singles and Bar hold one item. Both loaders keep native transaction adapters in transient block-entity attachments and share the base ledger. Insertion is judged against staged contents through `StackRunItemAccess.acceptsAt`; support precedes growth checks. Bulk insertion shares one automation growth-permission query, while collision remains per position and commit rechecks placement authority. Base journals are obtained from the resolved member list. Slot edits apply at root/outer commit: removals, then insertions bottom-up. Both allow one structural extraction position, without other pending positions or later insertion. NeoForge checks staged support and prepares growth through native placement events, restores it silently, then installs/fills/publishes at commit. `RunEdit` refuses reentrant automation mutations.

`WorldEdits` resolves nullable actors and checks build limits, replaceability, obstruction, border, spawn and loader authority. Both loaders use fake players with the `[SomeStacks]` automation identity. Cleanup retains a responsible player; automated or mixed Storage cleanup uses automation. NeoForge fires placement events against captured snapshots, restores denied block/entity state silently, and publishes accepted edits. Fabric consults block-use callbacks for automation and native break callbacks for removal. Player creation fires `PLACED_BLOCK`. Edits emit `BLOCK_PLACE`/`BLOCK_DESTROY`, or `BLOCK_CHANGE` for surviving player content/rotation edits.

## Player interaction and networking

Main-hand client rules recognize permanence, block rotation, item rotation, generic deposit/placement cancellation, and extraction in order; cell and destination selection run only on the server. Loader callbacks supply hit classification. NeoForge cancellation and Fabric `SUCCESS` suppress local use while retaining vanilla block-use packets. A payload sends mode and modifier state only when changed.

`ConfigSyncReceiver` stages cached configuration, the only play-phase server-to-client payload. Decoding bounds packet bytes; staging bounds bytes/chunks and rejects duplicates, inconsistent metadata and empty middle chunks. Rejection/disconnect clear staging; complete totals publish enable flags and namespace lists atomically. `/ss item` and `/ss write` act locally; no server packet requests client writes.

`StackBlock.useItemOn`/`useWithoutItem` handle non-sneaking clicks; loader hooks handle sneaking torch rotation and neighbor actions. `StackInteractions` validates hand, spectator/protection state, items, support, policy, height, obstruction, and authority. Placement plus first deposit is atomic. Unhandled uses pass through; plain extraction attempts remain consumed. Rotation reads are pure; edits use native facing, inherited before Storage placement. Cell selection follows the server ray through the vanilla hit; clients send no cell index.

Neighbor deposits consult NeoForge right-click events or Fabric block-use callbacks. Guards prevent recursive gestures; each loader owns placement authority. Brigadier handles commands; Fabric forwards unclaimed client `ss` forms.

## Configuration and presentation

`ServerConfig` publishes immutable policy. NeoForge uses native SYNCED `ModConfigSpec` and `<world>/serverconfig/somestacks-server.toml`; Fabric reads `<world>/serverconfig/somestacks-server.json` with per-field defaults. Backends share common setting keys/defaults and return settings; the facade applies them. `/ss deny` and `/ss gen` persist edits. `/ss reload` and native callbacks share server-thread refresh: changed client settings resync players; gallery enablement/permission changes resend command trees.


BERs prepare presentation before queued submission and use inherited frame light. Storage/Singles use `CubeGrid`/`CubeRenderHelper` and cache resolved models until content, rotation, or profile changes; time-dependent models freeze. Bar caches cuboids, UVs, and tints until a content update or resource-generation change. Profiles resolve user, resource pack, then measurement. `ItemCapture` owns quads, tints, and render types; measurement visits vanilla model extents. Special features disqualify automatic flat projection. 2-D art uses the item atlas and render type, caches the 2,048 most recent stacks until reload, and submits once per render type per block. Capture failure draws `stack_cube_error` until reload. Block mode submits native block features; non-model blocks use FIXED. Bar UVs cache per sprite; auto-tint reads the first quad. Measured cache keys format, packs and mod versions. Combined mod data format: 121.0; galleries bypass protection.

Automatic Bar tints resolve on first render, once models are ready; authored colors stay; reload clears computed tints. `/ssclienttest` checks resin's default orange tint.

2-D selection uses thinnest/longest axis ratio; cache format 4.

## GameTests

`somestacks_gametest` is excluded from `build`. Shared checks live in `common/src/gametest`; Fabric uses entrypoints and NeoForge deferred functions plus test-instance JSON. Both wrappers and shared checks use `AutomationAndPersistence` names. Test data format: 121.0; vanilla data fixing upgrades the empty structure. Native transfer suites cover staged support, replacement, rollback, cross-block sharing, growth, and movement; NeoForge also covers vetoes. JSON config tests are Fabric-only. The gallery-budget test has its own environment. `/ssclienttest` requires aiming at a stack with Shift/V released; it checks callbacks, capture ownership, special geometry, GUI extents, profile/cache invalidation, prepared submission, and valid/rejected native updates including inherited components on client-attached entities.
