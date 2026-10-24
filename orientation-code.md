<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

Behavior: `orientation-player.md`; build: `orientation-build.md`.

## Project shape

Java 21, Minecraft 1.21.1; package `com.github.crittscott.somestacks`, id `somestacks`.

`common` ships in each loader JAR without loader imports. `PlatformServices.Backend` supplies paths, versions, networking and edit authority. Loaders own gestures and automation adapters; `CommonRegistry` supplies identifiers and lookups.

`ShapedStackBlock` gives Singles/Bar occupied-content targeting, outlines and collision, full-block interaction, and empty visual shapes. Targeting passes through empty space; empty stacks have no outline.

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
| `ServerConfig`, loader config adapters | Immutable per-world policy, deny sets, native Forge/NeoForge TOML or Fabric TOML persistence |
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

Fabric checks both protocol markers.

## Runtime and movement model

Block entities own local slots. `StackRun` resolves maximal same-type runs, slots, headroom, publication and comparators; concrete runs own movement. Resolution caches per tick until structural edits.

Block entities do not tick. `StackBlockEntity` owns persistence and nested batching; mutations schedule one pass on the run's base for client updates, lighting, comparators, and Storage settlement. Every run block reports the same comparator signal. Mutations refresh one cached slot fraction; reads sum cached fractions on demand, and loads rebuild them.

Movement models:

- `StoragePile` is a flat inventory from the base up. Settlement groups exact identities, sorts distinct groups using precomputed fields, restores legal stack sizes, packs down, and removes unneeded top blocks unless permanent or protected.
- `SinglesColumn` is positional. Extraction shifts one visual column down across rotation-aware seams, transports item rotation, preserves unrelated gaps, and does not collapse above a broken block.
- `BarColumn` is positional and overlap-supported. Player extraction and block removal cascade; automation extraction moves the topmost bar into the opened position instead.

`StackItemStorage` persists local inventory through block-entity NBT using Data Components and a `HolderLookup.Provider`. All save `DataVersion` and `Items`; Storage adds `Permanent`, Singles `CubeRotations`. Storage/Singles layout rotation uses `horizontal_facing`; Singles item rotation is block-entity data. `Permanent` is server-only and is stripped from update tags.

`StackDataMigration` upgrades saved items through DataFixerUpper; unversioned component items use 1.21.1 (3955), legacy byte-`Count` items use 1.20.1 (3465), and legacy `Rotation` becomes block state after chunk attachment, without loading chunks from load callbacks. Unreadable entries persist in `SetAside`, retry each load, and prevent automatic block removal. `ServerConfigMigration` converts the preceding release's world JSON policy to TOML before native config loading, retaining the original as `.migrated` and reporting custom ingot entries for data-pack transfer. Warnings identify dimension and position; failed retries warn once per location per session. Client updates require current format, bounded inventory, unique valid slots/rotations, and no server-only fields; rejection changes nothing. Permanence belongs to the base; Singles item rotation travels with its item.

## Admission, automation, and edits

Storage accepts ordinary nonempty items; Bar accepts the reloadable `somestacks:ingots` item tag; Singles accepts allowed non-Bar items. `disable_mods` applies to gestures and automation, while `disable_items` applies only to player deposits; galleries skip both. Internal settlement, gravity, and backfill never reapply admission rules.

Loader-native views adapt `StackRunItemAccess` and span the run plus one headroom block while growth is allowed. Storage slots use item limits; Singles and Bar hold one item. Forge/NeoForge use native `RunItemHandler` wrappers over common `RunItemHandlerBase`; Fabric owns one transaction adapter per block entity through a transient Data Attachment, shared by sided lookups and base-ledger resolution. Run views share the bottom block's transaction ledger, stage mutations until outer commit, and permit one structural extraction position per transaction. `RunEdit` refuses reentrant automation mutations.

`WorldEdits` checks build limits, replaceability, obstruction, border, spawn and loader authority. Automated edits use the `[SomeStacks]` identity. Player cleanup retains the player; deferred Storage settlement uses automation for automated or mixed causes. Fabric/Forge cache a `SyntheticPlayer` per dimension with a packet listener that drops outbound packets; NeoForge uses its fake-player factory. Actors clear on server stop. Forge/NeoForge capture tentative placement in loader snapshots, fire native placement events before publishing, restore block-entity data on denial without neighbor updates, and publish accepted changes through the loader facility. Removal fires native break events. Fabric uses optional Common Protection API placement, interaction, and break queries, plus direct FTB Chunks placement queries. NeoForge additionally retains direct FTB Chunks placement checks. Removal fires the `PlayerBlockBreakEvents` lifecycle. Outer edits emit `BLOCK_PLACE`/`BLOCK_DESTROY`; player content and rotation edits emit `BLOCK_CHANGE` while the stack survives.

## Player interaction and networking

Main-hand client rules recognize permanence, block rotation, item rotation, deposit, placement, and extraction in order. Loader callbacks supply hit classification. Forge/NeoForge cancellation and Fabric `SUCCESS` suppress local use while retaining vanilla block-use packets. A payload sends mode and modifier state only when changed.

The sole play-phase server-to-client payload is a cached config snapshot. Decoding bounds packet bytes; staging bounds bytes/chunks and rejects duplicates, inconsistent metadata and empty middle chunks. Rejection/disconnect clear staging; complete totals publish enable flags and namespace lists atomically. `/ss item` and `/ss write` act locally; no server packet requests client writes.

Non-sneaking stack clicks run from `StackBlock.useItemOn`/`useWithoutItem`; loader server hooks handle sneaking torch rotation plus placement or deposit reached through a neighboring non-stack block. Both pass vanilla's `BlockHitResult` to `StackInteractions`, after target, reach pacing, and the ordinary interaction event. Common handling validates hand, spectator/protection state, item, support, enablement, height, obstruction and authority. Placement plus first deposit is one transaction; successful player creation fires `PLACED_BLOCK` with the original held stack, while pile growth does not. Cell selection for deposit, extraction, and Singles item rotation is recomputed server-side by extending a ray through the vanilla hit location; no client cell index is accepted.

Forge/NeoForge consult destination right-click events for neighbor deposits, honoring cancellation and block/item denial. Fabric uses destination callbacks plus optional protection queries. A scoped guard prevents consultation from executing gestures. Local commands execute their syntax tree; server help uses translated usage forms; Fabric passes unclaimed `ss` forms to the server.

## Configuration and presentation

`ServerConfig` publishes immutable policy. Forge/NeoForge register `ForgeConfigSpec`/`ModConfigSpec` SERVER configs; native load/reload events apply policy and resync changes. Commented TOML supports `<world>/serverconfig/somestacks-server.toml` overrides. Fabric loads `config/somestacks-server.toml` from defaults and reports malformed fields independently. `/ss deny` and `/ss gen` save through the backend. `/ss reload` refreshes policy and syncs changed client settings; Fabric rereads TOML, while Forge/NeoForge use native config events. Bar/Singles classification uses the `somestacks:ingots` tag.

Resource extension paths are listed in `orientation-player.md`.

Block entity renderers use `CubeGrid` and `CubeRenderHelper` for Storage/Singles item models; Bar uses resource-defined cuboids and tints. Storage/Singles profile precedence is user, resource pack, then measurement. Resolved profiles cache per item until override change/reload. `ItemCapture` captures the 1.21.1 item renderer into an isolated pose and records baked quads, tints, and optional bounds. Measurement uses bounds; A 2,048-entry access-ordered cache holds prepared baked models or Z-facing plates by item/components/count and clears immediately on reload; a throwing 2-D capture draws `stack_cube_error` until reload. Bar UV regions cache per atlas sprite until reload. Bar auto-tint reads the first quad on first render, after models and sprites are ready. `measured_cache.json` uses format 3, resource packs, and owning-mod versions. Appearance is client resource data; galleries bypass placement protection.

## GameTests

`somestacks_gametest` is development-only; shared checks are in `common/src/gametest`, native checks in loaders. In `runClientGameTests`, aim at a stack, release Shift/modifier, and run `/ssclienttest` to check callbacks and profile precedence/invalidation. The gallery budget check uses an isolated GameTest batch.

