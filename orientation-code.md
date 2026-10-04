<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

Subsystem ownership, persistent data, loader boundaries, and invariants the code currently maintains. `orientation-player.md` covers observable behavior; `build-env.md` covers the build.

## Project shape

Some Stacks is a Java 21 mod for Minecraft 1.21.4 under `com.github.crittscott.somestacks`, mod id `somestacks`. Architectury is build-time only; no loader has an Architectury API runtime dependency.

`common` folds into each loader JAR and is not a runtime artifact. It has no loader imports; `CommonRegistry`, `EditAuthority`, networking callbacks, client gesture adapters, automation adapters, and `PlatformServices` are the loader seams. Loader seams are installed during loader startup. Each loader supplies registry handles before common world objects exist. Release JARs are under `<loader>/build/libs/`.

Three blocks and block entity types are registered, with no block items, menus, recipes, or portable containers:

| Block | Block entity | Local storage |
| --- | --- | --- |
| `storage_stack_block` | `stack_be` | 27 item stacks |
| `singles_stack_block` | `singles_stack_be` | 64 single items in a 4 x 4 x 4 grid |
| `bar_stack_block` | `bar_stack_be` | 64 single items in eight alternating layers |

## Code map

| Area | Responsibility |
| --- | --- |
| `StackBlock`, `ShapedStackBlock`, `block/*StackBlock` | Shared state/waterlogging/use plumbing, dynamic occupied shapes, per-type removal, comparators, scheduled publication |
| `StackBlockEntity`, `block/*StackBE` | Shared local inventory, NBT, synchronization, lighting and nested batching; per-type rotations, shapes and run caches |
| `StackRunItemAccess`, `StoragePile`, `SinglesColumn`, `BarColumn` | Common automation contract plus distinct run-wide growth, mutation, fill, cleanup and structure |
| `StackItemStorage`, `SlotAccess` | Loader-neutral fixed-slot storage contract |
| `util/*CubeIdx`, `ViewRay` | Geometry, rotation, support, seams, targeting |
| `ServerConfig` | Per-world policy, deny sets, resolved ingot membership |
| `WorldEdits`, `EditAuthority`, `AdjacentEdits` | World edits, automation authority, adjacent-target protection |
| `StackInteractions`, `ServerGestureState` | Server interpretation of vanilla block-use packets and synchronized gesture state |
| `network/*` | Gesture-state and server-to-client synchronization payloads |
| `client/*` | Renderers, profiles, measurement, bar textures, gesture rules |
| `SsCommand`, `RenderGalleryGenerator` | Administration, profile authoring, galleries |
| Loader modules | Registration, transport, callbacks, rendering glue, native automation views |

## Loader integration

| Concern | Forge | NeoForge | Fabric |
| --- | --- | --- | --- |
| Metadata | `META-INF/mods.toml` | `META-INF/neoforge.mods.toml` | `fabric.mod.json`, mixin config |
| Entrypoint | `SomeStacks` | `SomeStacksNeoForge` | `SomeStacksFabric` plus client entrypoint |
| Networking | `SimpleChannel` | payload registrar | payload registry plus `ProtocolPkt` handshake |
| Gestures | interaction/input events | interaction/input events | callbacks plus air-click mixin |
| Automation | generic whole-run `IItemHandler` | generic whole-run `IItemHandler` | generic Transfer API `Storage<ItemVariant>` |
| Automated edits | vanilla plus place/break events | vanilla plus place/break events | vanilla, break callback, Common Protection API growth check |

The selected loader build is required on client and server. Loaders own transport and callbacks; packet codecs, gesture interpretation, gesture rules, rendering, commands, and storage mechanics remain in `common`. Fabric's mixins use Loom's current remapping path without the legacy annotation processor.

## Runtime and movement model

A maximal contiguous vertical run of one stack type is the central abstraction. Block entities own local slots; the server-only `StoragePile`, `SinglesColumn`, and `BarColumn` coordinate cross-block operations and hold a `ServerLevel`. Run resolution is cached per game tick and invalidated by structural changes.

Block entities do not tick. `StackBlockEntity` owns local persistence and a nesting-safe batch depth; mutations schedule one deferred pass on the run's bottom block that coalesces client updates, lighting, comparator publication, and Storage settlement. Every block in a run reports the same run-wide comparator signal.

The three movement models are distinct:

- `StoragePile` is a flat inventory from the base up. Settlement groups exact identities, restores legal stack sizes, sorts, packs down, and removes unneeded top blocks unless permanent or protected.
- `SinglesColumn` is positional. Extraction shifts one visual column down across rotation-aware seams, transports item rotation, preserves unrelated gaps, and does not collapse above a broken block.
- `BarColumn` is positional and overlap-supported. Player extraction and block removal cascade; automation extraction moves the topmost bar into the opened position instead.

`StackItemStorage` persists local inventory through each block entity's NBT methods using Data Components and a `HolderLookup.Provider`. Storage saves `DataVersion`, `Items`, `Rotation`, and `Permanent`; Singles adds `CubeRotations`; Bar saves `DataVersion` and `Items`.

`StackDataMigration` runs first in every `loadAdditional`. A tag without `DataVersion` is 1.21.1 (3955); older item tags run through vanilla's DataFixerUpper and are restamped. Items that fail to parse or have no free slot go to a saved `SetAside` list, are retried on every load, and are logged with the block position. Update tags carry `DataVersion` but omit `SetAside`. Permanence is pile-wide but stored on the base; block rotations are local; Singles item rotation travels with the item.

## Admission, automation, and edits

Storage accepts ordinary nonempty items; Bar accepts the configured ingot list; Singles accepts allowed non-Bar items. `disable_mods` applies to gestures and automation, while `disable_items` applies only to player deposits. Internal settlement, gravity, and backfill never reapply admission rules.

Every loader-native view adapts `StackRunItemAccess` and spans the whole run plus one headroom block while growth is allowed. Storage slots use item stack limits; Singles and Bar slots hold one item. Forge and NeoForge each have one generic `RunItemHandler`; Fabric has one generic transaction adapter without per-type dispatch. Fabric stages mutations until outer transaction commit and permits one structural extraction position per transaction. `RunEdit` refuses reentrant automation mutations.

Growth and cleanup use `WorldEdits` with the shared `[SomeStacks]` automation identity and check build limits, replaceability, obstruction, border, spawn, and loader authority. Forge owns an explicit per-dimension actor cache cleared on level unload and server stop; NeoForge and Fabric use their fake-player factories. Forge and NeoForge fire native place/break events. Fabric fires `PlayerBlockBreakEvents` on removal and routes optional Common Protection API growth checks only through `CommonProtectionCheck`. Successful outer-block placement/removal also invokes the vanilla placement callback and emits `BLOCK_PLACE`/`BLOCK_DESTROY` game events; internal cells and bars remain block-entity contents, not world blocks.

## Player interaction and networking

Shared client rules recognize permanence, block rotation, item rotation, deposit, placement, and extraction in that order. They suppress local vanilla block/item behavior without replacing the click: Forge/NeoForge cancellation and Fabric `SUCCESS` still send the normal block-use packet. A small payload sends placement mode and modifier-down state only when that state changes.

Non-sneaking existing-stack clicks run from `StackBlock.useItemOn`/`useWithoutItem`; loader server hooks handle sneaking torch rotation plus placement or deposit reached through a neighboring non-stack block. Both pass the actual vanilla `BlockHitResult` to `StackInteractions`, after vanilla has supplied target, reach pacing, and the ordinary interaction event. Common handling validates main hand, spectator/protection state, held item, support, type enablement, height, obstruction, and edit authority. Placement plus first deposit is one transaction. Cell selection for deposit, extraction, and Singles item rotation is recomputed server-side by extending a ray through the vanilla hit location; no client cell index is accepted.

The real loader event covers the block the player clicked. When that click reaches a Singles or Bar Stack through a neighboring block, `AdjacentEdits` additionally consults protection at the destination with a recursion guard. There are no synthetic repeats for ordinary stack clicks, no trailing-click suppressor, and no per-gesture mutation packets.

## Configuration and presentation

World policy is `<world>/serverconfig/somestacks-server.json`, owned by `ServerConfig` and loaded atomically from defaults at server startup. Bad fields are reported and skipped independently, so one wrong type cannot leave a partially applied or previous world's configuration. `/ss deny`, `/ss ingot`, and `/ss gen` save immediately. `/ss reload` rereads policy JSON, reloads server render overrides, and resyncs players. Ingot-list entries are either a `#`-prefixed item-tag pattern (`*` allowed, matched against whole tag names) or a bare item id, and resolve again on configuration or tag reload.

Extension points are the configured ingot list; registered `somestacks:block.*` sounds through ordinary resource-pack `sounds.json`; `assets/<namespace>/item_render_overrides/*.json`; `assets/<namespace>/textures/bars/*.json`; and `config/somestacks/server_item_overrides/*.json`.

All types use block entity renderers. `CubeRenderHelper` renders Storage and Singles item models; Bar uses fixed cuboids with resource-defined textures and tints. Storage/Singles profile precedence is server, user, resource pack, then measurement. `ItemCapture` records resolved baked quads, layer tints, and vertex-written bounds for measurement and 2-D projection; Bar auto-tint reads the first captured quad. `measured_cache.json` is keyed by format version, resource packs, and owning-mod versions. Bar appearance is client-only. Galleries spread work across ticks but bypass normal placement protection.

## GameTests

There is no JUnit or production `test` source set. Each loader has a development-only `somestacks_gametest` mod under `<loader>/src/gametest` and runs it with `:<loader>:runGameTestServer`; no suite is part of `build`. Neutral assertions, shared vanilla-interaction and protection scenarios, and the shared Base64 empty-structure fixture live under `common/src/gametest`; assertions use `CommonRegistry`. Native `IItemHandler` and Transfer API assertions stay loader-local. Forge and NeoForge use annotated holders; Fabric uses `fabric-gametest` entrypoints. NeoForge holders disable class-name template prefixes. Loader-native event and synthetic-player assertions remain loader-specific. Distinct otherwise-identical test stacks use `CUSTOM_DATA`.

## Conventions the code currently follows

- Persistent state is block-local; run behavior lives in the pile/column layer.
- The three movement models stay separate, including Singles' rotation-aware item transport.
- Internal movement never revalidates stored items.
- Forge/NeoForge simulation and Fabric commit feasibility rules match.
- Structural edits go through `WorldEdits`; sync, light, comparator work, and settlement are batched.
- Gesture state is untrusted input; every vanilla server interaction revalidates the world, hand, item, geometry, and edit authority.
- Render-profile precedence is fixed; the measurement format version changes when fitting semantics change.
- Storage and Singles render scales are separate in `CubeRenderHelper`.
