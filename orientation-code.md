<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

See `orientation-player.md` for behavior; `build-env.md` for the build.

## Project shape

Java 21, Minecraft 1.21.4; package `com.github.crittscott.somestacks`, mod id `somestacks`.

`common` folds into each loader JAR without loader imports. One `PlatformServices.Backend` supplies paths, versions, networking and edit authority. Gestures and automation adapters remain loader-owned. `CommonRegistry` provides identifiers and post-registration lookups.

Three registered blocks and block entity types; no block items, menus, or recipes:

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
| `ServerConfig`, loader config adapters | Immutable per-world policy, deny sets, native Forge/NeoForge TOML or Fabric JSON persistence |
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
| Edit protection | vanilla plus place/break events | vanilla plus place/break events | vanilla, required Common Protection API queries, break callbacks |

Matching loader builds are required on both sides. Forge/NeoForge enforce the protocol through channels; Fabric checks a configuration-phase marker in both directions.

## Runtime and movement model

A maximal contiguous vertical run of one type is the central abstraction. Block entities own local slots; server-only `StackRun` owns resolution, flattened slots, headroom, publication and comparators. Concrete runs own movement. Resolution caches per tick and invalidates on structural changes.

Block entities do not tick. `StackBlockEntity` owns local persistence and a nesting-safe batch depth; mutations schedule one deferred pass on the run's bottom block that coalesces client updates, lighting, comparator publication, and Storage settlement. Every block in a run reports the same run-wide comparator signal, computed from cached per-block fill contributions refreshed on mutation and NBT load.

The three movement models are distinct:

- `StoragePile` is a flat inventory from the base up. Settlement groups exact identities, sorts distinct groups using precomputed fields, restores legal stack sizes, packs down, and removes unneeded top blocks unless permanent or protected.
- `SinglesColumn` is positional. Extraction shifts one visual column down across rotation-aware seams, transports item rotation, preserves unrelated gaps, and does not collapse above a broken block.
- `BarColumn` is positional and overlap-supported. Player extraction and block removal cascade; automation extraction moves the topmost bar into the opened position instead.

`StackItemStorage` persists local inventory through block-entity NBT using Data Components and a `HolderLookup.Provider`. All types save `DataVersion` and `Items`; Storage adds `Permanent`, and Singles adds `CubeRotations`. Storage and Singles layout rotation is the outer block's `horizontal_facing`; Singles item rotation remains block-entity data. `Permanent` is server-only and is stripped from update tags.

Saved-world loading starts with `StackDataMigration`: absent `DataVersion` means 1.21.1 (3955), older items pass through DataFixerUpper, and legacy `Rotation` becomes block state. Unreadable items remain in `SetAside`, logged and retried each load. Client updates require current format, bounded inventory, unique valid slots, exact valid Singles rotations, and no server-only fields. Invalid updates leave client state untouched without migration, retention or logs. Permanence belongs to the pile base; block rotations are local; Singles item rotation travels with the item.

## Admission, automation, and edits

Storage accepts ordinary nonempty items; Bar accepts the reloadable `somestacks:ingots` item tag; Singles accepts allowed non-Bar items. `disable_mods` applies to gestures and automation, while `disable_items` applies only to player deposits. Internal settlement, gravity, and backfill never reapply admission rules.

Every loader-native view adapts `StackRunItemAccess` and spans the whole run plus one headroom block while growth is allowed. Storage slots use item stack limits; Singles and Bar slots hold one item. Forge and NeoForge have minimal native `RunItemHandler` wrappers over the shared common `RunItemHandlerBase`; Fabric has one generic transaction adapter without per-type dispatch. Fabric retains one adapter and indexed slot-view cache per block entity. All views of a run use its bottom block's transaction ledger, stage mutations until outer commit, and permit one structural extraction position per transaction. `RunEdit` refuses reentrant automation mutations.

`WorldEdits` checks build limits, replaceability, vanilla obstruction, border, spawn and loader authority. Automated edits use the shared `[SomeStacks]` identity. Player cleanup retains the player; deferred Storage settlement uses automation for automated or mixed causes. Forge caches a connection-safe actor per dimension; NeoForge and Fabric use fake-player factories. Forge/NeoForge capture tentative placement with loader snapshots, fire native placement events before publishing updates, restore block-entity data on denial without notifying neighbors, and publish accepted changes through the loader transaction facility. Removal fires native break events. Fabric queries required Common Protection API before crediting growth or placing; removal fires the `PlayerBlockBreakEvents` lifecycle. Outer edits emit `BLOCK_PLACE`/`BLOCK_DESTROY`; successful player content and rotation edits emit `BLOCK_CHANGE` only while the clicked stack survives.

## Player interaction and networking

Shared client rules recognize permanence, block rotation, item rotation, deposit, placement, and extraction in that order. They suppress local vanilla block/item behavior without replacing the click: Forge/NeoForge cancellation and Fabric `SUCCESS` still send the normal block-use packet. A small payload sends placement mode and modifier-down state only when that state changes.

The only play-phase server-to-client payload is a cached config snapshot. Decoding bounds packet bytes; staging bounds generation bytes/chunks and rejects duplicates, inconsistent metadata and empty intermediate chunks. Rejection/disconnect clear staging; complete totals publish enable flags and namespace lists atomically. `/ss item` and `/ss write` act locally; no server packet requests client writes.

Non-sneaking existing-stack clicks run from `StackBlock.useItemOn`/`useWithoutItem`; loader server hooks handle sneaking torch rotation plus placement or deposit reached through a neighboring non-stack block. Both pass the actual vanilla `BlockHitResult` to `StackInteractions`, after vanilla has supplied target, reach pacing, and the ordinary interaction event. Common handling validates main hand, spectator/protection state, held item, support, type enablement, height, obstruction, and edit authority. Placement plus first deposit is one transaction. Cell selection for deposit, extraction, and Singles item rotation is recomputed server-side by extending a ray through the vanilla hit location; no client cell index is accepted.

The real event covers the clicked block; no right-click events are fabricated. Fabric queries Common Protection API for neighbor deposits into Singles/Bar; Forge/NeoForge require direct clicks. One authority per loader handles placement and destination permission. Client commands and server help share a server-safe Brigadier syntax tree.

## Configuration and presentation

`ServerConfig` publishes an immutable policy snapshot. Forge registers `ForgeConfigSpec` and NeoForge registers `ModConfigSpec` as SERVER configs; native load/reload events apply policy on the server thread and resync changed state. Their TOML can be overridden in `<world>/serverconfig/somestacks-server.toml`. Fabric installs `JsonServerConfig`, loading `<world>/serverconfig/somestacks-server.json` from defaults and reporting malformed fields independently. `/ss deny` and `/ss gen` save through the active backend. `/ss reload` refreshes the current policy and resyncs players; Fabric rereads JSON, while Forge/NeoForge receive file edits through their native lifecycle. Bar/Singles classification uses holder membership in one `somestacks:ingots` tag, with no config selector or custom tag rebake.

Extension points are the `somestacks:ingots` data-pack tag; registered `somestacks:block.*` sounds through ordinary resource-pack `sounds.json`; `assets/<namespace>/item_render_overrides/*.json`; and `assets/<namespace>/textures/bars/*.json`. Servers distribute render profiles through ordinary server resource packs.

Block entity renderers use `CubeGrid` and `CubeRenderHelper` for Storage/Singles item models; Bar uses resource-defined cuboids and tints. Storage/Singles profile precedence is user, resource pack, then measurement. Complete resolved profiles cache per item and invalidate on override changes or resource reload. `ItemCapture` records baked quads, tints, and optional bounds. Measurement uses bounds; 2-D projection captures quads only and shares immutable results by item, components, and count within each block render pass. `CubeGrid` precomputes rotated indices; Bar UV regions cache per atlas sprite until reload. Bar auto-tint reads the first quad. `measured_cache.json` is keyed by format version, resource packs, and owning-mod versions. All render overrides and Bar appearance are client resource data. Galleries spread work across ticks but bypass normal placement protection.

## GameTests

`somestacks_gametest` is development-only, excluded from `build`. Shared scenarios live in `common/src/gametest`; native checks stay loader-local.

