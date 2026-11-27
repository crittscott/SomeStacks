<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

Behavior: `orientation-player.md`; build: `orientation-build.md`.

## Project shape

Java 21, Minecraft 1.21.4; package `com.github.crittscott.somestacks`, id `somestacks`.

`common` ships in each loader JAR without loader imports. `PlatformServices.Backend` supplies paths, versions, networking and edit authority. Loaders own gestures and automation adapters; `CommonRegistry` supplies identifiers and lookups.

Blocks and local storage:

`ShapedStackBlock` gives Singles/Bar occupied-content targeting, outlines and collision; targeting passes through empty space.

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
| `ServerConfig`, `TomlPolicyFile`, `ServerConfigMigration`, loader config adapters | Per-world policy, its `Setting` declaration, deny sets, loader persistence, JSON migration |
| `WorldEdits`, `EditAuthority` | World edits, automation authority, adjacent-target protection |
| `StackInteractions`, `ServerGestureState`, `StackGestures` | Vanilla block-use interpretation, gesture state, shared gesture predicates |
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

Loader builds must match; Fabric checks both ways.

## Runtime and movement model

Block entities own local slots, cell boxes, deposit, and extraction; `ViewRay` holds the cell walks. Server-only `StackRun` resolves maximal same-type vertical runs, flat slots, headroom, the one growth gate, publication and comparators. Concrete runs own movement. Resolution caches per tick until structural edits.

Block entities do not tick. `StackBlockEntity` owns persistence and nested batching; mutations schedule one base pass for client updates, lighting, comparators, and Storage settlement. Mutations refresh one cached slot fraction; reads sum cached fractions on demand, and loads rebuild them.

Movement models:

- `StoragePile` is a flat inventory from the base up. Settlement groups exact identities, sorts groups by keys comparing component text only on ties, restores legal stack sizes, writes only changed slots, packs down, and removes unneeded top blocks unless permanent or protected.
- `SinglesColumn` is positional. Extraction shifts one visual column down across rotation-aware seams, transports item rotation, preserves unrelated gaps, and does not collapse above a broken block.
- `BarColumn` is positional and overlap-supported. Player extraction and block removal cascade; automation extraction moves the topmost bar into the opened position instead.

`StackItemStorage` persists local inventory through block-entity NBT using Data Components and a `HolderLookup.Provider`. All save `DataVersion` and `Items`; Storage adds `Permanent`, Singles `CubeRotations`. Layout rotation uses `horizontal_facing`; Singles item rotation is block-entity data. Update tags omit `Permanent`, set-aside data, container contents, and book pages.

`StackDataMigration` upgrades saved items through DataFixerUpper; absent `DataVersion` means 1.21.1 (3955), and legacy `Rotation` becomes block state. Unreadable entries persist in `SetAside` and retry each load. `ServerConfigMigration` converts world JSON policy to TOML on Fabric (original kept as `.migrated`) and logs custom `compatibility.ingots` entries at start on every loader. Warnings identify dimension and position; failed retries warn once per location per session. Client updates require current format, bounded inventory, unique valid slots/rotations, and no server-only fields; rejection changes nothing. Permanence belongs to the base; item rotation travels with Singles.

## Admission, automation, and edits

Storage accepts ordinary nonempty items; Bar accepts the reloadable `somestacks:ingots` item tag; Singles accepts allowed non-Bar items. `disable_mods` applies to gestures and automation, while `disable_items` applies only to player deposits; galleries skip both. Settlement and backfill never reapply admission.

Loader-native views adapt `StackRunItemAccess` and span the run plus one headroom block while growth is allowed. Storage slots use item limits; Singles and Bar hold one item. Forge/NeoForge use native `RunItemHandler` wrappers over common `RunItemHandlerBase`; Fabric owns one transaction adapter per block entity through a transient Data Attachment, shared by sided lookups and base-ledger resolution. Run views share the bottom block's transaction ledger, stage mutations until outer commit, and permit one structural extraction position per transaction. `RunEdit` refuses reentrant automation mutations.

`WorldEdits.mayPlace` is the one placement gate (build height, replaceability, the actor's `mayUseItemAt`, border, spawn, loader queries); `placeChecked` adds obstruction for every placement. Automated edits use the `[SomeStacks]` identity. Player cleanup, including the Bar collapse or Storage trimming a player's break causes, retains the player; deferred Storage settlement uses automation for automated or mixed causes. Forge caches a connection-safe actor per dimension; NeoForge and Fabric use fake-player factories. Forge/NeoForge place under snapshot capture, fire native place events before publishing, and restore block-entity data on denial without neighbor updates. Removal fires native break events. Fabric adds optional Common Protection API placement queries. Removal fires the `PlayerBlockBreakEvents` lifecycle. Outer edits emit `BLOCK_PLACE`/`BLOCK_DESTROY`; player content and rotation edits emit `BLOCK_CHANGE` while the stack survives.

## Player interaction and networking

Main-hand client rules claim permanence, rotations, modified-item clicks, and extraction via the server's `StackGestures` predicates. Forge/NeoForge client block listeners run lowest and sync even canceled clicks; cancellation and Fabric `SUCCESS` suppress local use but keep vanilla use packets. A payload sends mode (stable id) and modifier when changed. Published policy moves a disabled selected mode on.

The sole play-phase server-to-client payload is a cached config snapshot. Staging bounds bytes/chunks and rejects duplicates, inconsistent metadata and empty middle chunks. Rejection/disconnect clear staging; complete totals publish atomically. `/ss item` and `/ss write` act locally.

Non-sneaking stack clicks run from `StackBlock.useItemOn`/`useWithoutItem`; loader server hooks handle sneaking torch rotation and placement or deposit via a neighboring non-stack block. Both pass vanilla's `BlockHitResult` to `StackInteractions`, after target, reach pacing, and the ordinary interaction event. Common handling validates hand, spectator/protection, item, support, enablement, height, obstruction and authority through one deposit, extraction, and placement path. Placement plus first deposit is one transaction; successful player creation fires `PLACED_BLOCK` with the original held stack, while pile growth does not. Cell selection is recomputed server-side along a ray through the vanilla hit location; no client cell index is accepted.

Forge/NeoForge consult a destination right-click event for neighbor deposits into Singles/Bar, honoring cancellation and denial; a scoped guard keeps consultation from running gestures. Fabric consults destination block-use callbacks and, when installed, Common Protection API for neighbor deposits. Each loader has one placement/destination authority. Client commands and server help share server-safe Brigadier syntax; Fabric's client `ss` node passes unclaimed forms to the server.

## Configuration and presentation

`ServerConfig` publishes immutable policy. Forge/NeoForge register `ForgeConfigSpec`/`ModConfigSpec` SERVER configs; native load/reload events apply policy and resync changes. Commented TOML supports `<world>/serverconfig/somestacks-server.toml` overrides. Native specs and `TomlPolicyFile` build from `ServerConfig.Setting`. Fabric creates a missing `config/somestacks-server.toml`, reads only the selected file, and reports malformed fields by file. `/ss deny` and `/ss gen` save through the backend. `/ss reload` refreshes policy and syncs changed client settings; Fabric rereads TOML, while Forge/NeoForge use native config events. Bar/Singles classification uses the `somestacks:ingots` tag.

Resource extension paths are listed in `orientation-player.md`.

Block entity renderers use `CubeGrid` and `CubeRenderHelper` for Storage/Singles item models; Bar uses resource-defined cuboids and tints. Storage/Singles profile precedence is user, resource pack, then measurement. Resolved profiles cache per item until override change/reload. `ItemCapture` records baked quads, tints, and optional bounds. A 2,048-entry access-ordered cache holds prepared models or Z-facing plates by item/components/count and clears on reload; a throwing 2-D capture draws `stack_cube_error` until reload. Bar UV regions cache per atlas sprite until reload. Bar auto-tint reads the first quad on first render. `measured_cache.json` is keyed by format version, resource packs, and owning-mod versions. `/ss write changed` keeps malformed user-file entries and refuses while the file is unreadable. Appearance is client resource data; galleries bypass placement protection.

## GameTests

Development-only `somestacks_gametest` is excluded from `build`. Shared checks in `common/src/gametest` click through vanilla block use (`InteractionChecks.use`) and reach automation through each loader's `GameTestScaffold.Automation`; native checks are loader-local. In `runClientGameTests`, aim at a stack, release Shift/modifier, and run `/ssclienttest` to check callbacks and profile precedence/invalidation. The gallery budget check uses an isolated GameTest batch.

Fabric compiles against Common Protection API. `-PwithCommonProtectionApi=true` adds it to development runs and enables provider-backed GameTests; otherwise absence checks run.

