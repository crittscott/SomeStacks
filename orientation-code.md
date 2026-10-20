<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md Â§ Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks â€” code orientation

See `orientation-player.md` and `orientation-build.md`.

## Project shape

Java 25; Minecraft 26.3 (unobfuscated, no mappings) on Fabric and NeoForge; package `com.github.crittscott.somestacks`, id `somestacks`.

`common` folds into loader JARs. NeoForge glue uses loader prefixes; platform services sit beside entrypoints. `PlatformServices.Backend` supplies paths, versions, networking and edit authority; loaders own adapters. `CommonRegistry` supplies lookups.

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

`StackDataMigration` upgrades the preceding 26.1 (0.8.3) release's items through DataFixerUpper and splits over-cap Storage entries into legal local slots, retaining excess in `SetAside`; absent `DataVersion` means current. Unreadable or malformed entries persist verbatim in `SetAside` and retry on load. Warnings identify dimension/position; failed retries warn once per location/session. Client updates require current version, bounded inventories, unique integer slots, valid Singles rotations, and no server-only fields; rejection changes nothing and logs its reason once per client connection, including preflight rejection before inherited components load. Client ingress validates before inherited component loading: Fabric guards `loadWithComponents` with a client mixin; registered NeoForge entity subclasses override native hooks with compile-time checks. Accepted updates use the shared client load branch. Deferred migration marks dirty on attachment. Permanence belongs to the base; block rotation is local; Singles item rotation travels.

## Admission, automation, and edits

Storage takes nonempty items; Bar takes the reloadable `somestacks:ingots` tag; Singles takes allowed non-Bar items. `disable_mods` applies to gestures and automation, while `disable_items` applies only to player deposits; galleries skip both. Internal settlement, gravity, and backfill never reapply admission rules.

Native views expose `StackRunItemAccess` plus one headroom block. Storage capacity is min(64, item limit); positional slots hold one item. Transient base-block adapters share `RunTransferLedger` staging, rollback snapshots, extraction restrictions, and ordered replay. Native transaction participation stays in each loader. `acceptsAt` checks staged support before growth; bulk insertion shares a permission query, keeps collision per position, and rechecks authority at commit. Root/outer commits remove first, then insert bottom-up with fresh run resolution. Positional extraction permits one position without other pending positions or later insertion. NeoForge prepares growth through native placement events, restores it silently, then installs/fills/publishes at commit. `RunEdit` blocks reentrant mutations.

`WorldEdits` resolves nullable actors and checks build limits, replaceability, obstruction, border, spawn and loader authority. Both loaders use fake players with the `[SomeStacks]` automation identity. Cleanup retains a responsible player; automated or mixed Storage cleanup uses automation. NeoForge fires placement events against captured snapshots, restores denied block/entity state silently, and publishes accepted edits. Fabric consults block-use callbacks for automation and native break callbacks for removal. Player creation fires `PLACED_BLOCK`. Edits emit `BLOCK_PLACE`/`BLOCK_DESTROY`, or `BLOCK_CHANGE` for surviving player content/rotation edits.

## Player interaction and networking

Main-hand rules order permanence, block rotation, item rotation, modified-click cancellation, then extraction; targeting stays server-side. NeoForge cancellation and Fabric `SUCCESS` suppress local use while retaining vanilla block-use packets. A payload sends mode and modifier state only when changed.

`ConfigSyncReceiver` publishes a complete validated client policy. Fabric sends one composite-codec `ConfigSyncPkt`; NeoForge feeds the same snapshot from native SYNCED config load/reload events and registers no custom clientbound payload. Each list allows 256 unique ASCII namespaces of up to 256 characters (about 130 KiB total); invalid/excess entries warn and are omitted. Disconnect resets policy and rejection diagnostics. Protocol 6 covers both wire formats. `/ss item` and `/ss write` act locally; no server packet requests client writes.

`StackBlock.useItemOn`/`useWithoutItem` handle non-sneaking clicks; loader hooks handle sneaking torch rotation and neighbor actions. `StackInteractions` validates hand, spectator/protection state, items, support, policy, height, obstruction, and authority. Placement plus first deposit is atomic. Unhandled uses pass through; plain extraction attempts remain consumed. Rotation reads are pure; edits use native facing, inherited before Storage placement. Cell selection follows the server ray through the vanilla hit; clients send no cell index.

Neighbor deposits consult NeoForge right-click events or Fabric block-use callbacks. Guards prevent recursive gestures; each loader owns placement authority. Brigadier handles commands; Fabric forwards unclaimed client `ss` forms.

## Configuration and presentation

`ServerConfig` publishes immutable policy. NeoForge uses native SYNCED `ModConfigSpec` with the explicit `somestacks-server.toml` filename and loader-managed world overrides; Fabric reads `<world>/serverconfig/somestacks-server.json` with per-field defaults. Backends share common setting keys/defaults and return settings; the facade applies them. `/ss deny` and `/ss gen` persist edits. Reloads and list edits share server-thread publication/logging. Fabric sends only changed normalized snapshots; NeoForge owns native sync. Gallery enablement/permission changes resend command trees. Policy logs include gallery access and deny-list counts.


BERs prepare presentation before queued submission and use inherited frame light. Storage/Singles use `CubeGrid`/`CubeRenderHelper` and cache resolved models until content, rotation, or profile changes; time-dependent models freeze. Bar caches cuboids, UVs, and tints until a content update or resource-generation change. Profiles resolve user, resource pack, then measurement. `ItemCapture` owns quads, tints, and render types; measurement visits vanilla model extents. Special features disqualify automatic flat projection. 2-D art uses the item atlas and render type, caches the 2,048 most recent stacks until reload, and submits once per render type per block. Capture failure draws `stack_cube_error` until reload. Block mode submits native block features; non-model blocks use FIXED. Bar UVs cache per sprite; tint reads the first quad. Measured caches key format/packs/mod versions. Data format: 121.0.

Automatic Bar tints resolve on first render, once models are ready; authored colors stay; reload clears computed tints. `/ssclienttest` checks resin's default orange tint.

2-D selection uses thinnest/longest axis ratio; cache format 4.

## GameTests

`somestacks_gametest` is excluded from `build`. Common checks use Fabric entrypoints or NeoForge deferred functions/test-instance JSON; persistence wrappers share `AutomationAndPersistence` names. Test data format is 121.0. Protection checks take native removal observers and accepted-count offers. Capacity/migration checks cover Storage limits, fullness, and retained overflow. Native transfer suites cover support, replacement, rollback, shared staging, growth, movement, and NeoForge vetoes. Fabric owns JSON config tests; gallery-budget tests have a separate environment. `/ssclienttest`, aimed at a stack with Shift/V released, checks callbacks, capture/geometry, GUI extents, cache invalidation, queued rendering, and atomic native update rejection including inherited components.
