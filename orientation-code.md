<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

Current subsystem ownership, persistence, loader boundaries, and invariants. `orientation-player.md` covers observable behavior; `build-env.md` covers the build.

## Project shape

Some Stacks is a Java 21 Minecraft 1.21.4 mod under `com.github.crittscott.somestacks`, mod id `somestacks`.

`common` folds into each loader JAR and is not a runtime artifact. It has no loader imports; `CommonRegistry`, `EditAuthority`, networking callbacks, client gesture adapters, automation adapters, and `PlatformServices` are the loader seams installed during startup. Each loader supplies registry handles before common world objects exist.

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
| `StackBlockEntity`, `block/*StackBE` | Shared local inventory, NBT, synchronization, lighting and batching; per-type shapes, item rotations and run resolution |
| `StackRun`, `StackRunItemAccess`, `StoragePile`, `SinglesColumn`, `BarColumn` | Shared run resolution, slots, publication and comparators; distinct mutation, growth and cleanup |
| `StackItemStorage`, `SlotAccess` | Loader-neutral fixed-slot storage contract |
| `CubeGrid`, `util/*CubeIdx`, `ViewRay` | Shared cube-grid coordinates plus per-type geometry, support, seams and targeting |
| `ServerConfig` | Per-world policy, deny sets, resolved ingot membership |
| `WorldEdits`, `EditAuthority`, `AdjacentEdits` | World edits, automation authority, adjacent-target protection |
| `StackInteractions`, `ServerGestureState` | Server interpretation of vanilla block-use packets and synchronized gesture state |
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
| Edit protection | vanilla plus place/break events | vanilla plus place/break events | vanilla, destination callback, Common Protection API, break callbacks |

The selected loader build is required on client and server. Loaders own transport, registration and callbacks; packet codecs, gestures, commands, rendering, and storage mechanics remain in `common`. Forge and NeoForge enforce the shared protocol version through their channels; Fabric checks a configuration-phase marker in both directions.

## Runtime and movement model

A maximal contiguous vertical run of one stack type is the central abstraction. Block entities own local slots; server-only runs coordinate cross-block operations. `StackRun` owns resolution, flattened slots, headroom, publication and comparators; the three concrete runs own their movement models. Resolution is cached per game tick and invalidated by structural changes.

Block entities do not tick. `StackBlockEntity` owns local persistence and a nesting-safe batch depth; mutations schedule one deferred pass on the run's bottom block that coalesces client updates, lighting, comparator publication, and Storage settlement. Every block in a run reports the same run-wide comparator signal.

The three movement models are distinct:

- `StoragePile` is a flat inventory from the base up. Settlement groups exact identities, restores legal stack sizes, sorts, packs down, and removes unneeded top blocks unless permanent or protected.
- `SinglesColumn` is positional. Extraction shifts one visual column down across rotation-aware seams, transports item rotation, preserves unrelated gaps, and does not collapse above a broken block.
- `BarColumn` is positional and overlap-supported. Player extraction and block removal cascade; automation extraction moves the topmost bar into the opened position instead.

`StackItemStorage` persists local inventory through block-entity NBT using Data Components and a `HolderLookup.Provider`. All types save `DataVersion` and `Items`; Storage adds `Permanent`, and Singles adds `CubeRotations`. Storage and Singles layout rotation is the outer block's `horizontal_facing`; Singles item rotation remains block-entity data. `Permanent` is server-only and is stripped from update tags.

`StackDataMigration` runs first in every `loadAdditional`. A tag without `DataVersion` is 1.21.1 (3955); older item tags run through vanilla's DataFixerUpper and are restamped. It also consumes the preceding release's block-entity `Rotation` into block state. Items that cannot load go to a saved `SetAside` list, are retried on every load, and are logged. Update tags carry `DataVersion` but omit `SetAside`. Permanence is pile-wide but stored on the base; block rotations are local; Singles item rotation travels with the item.

## Admission, automation, and edits

Storage accepts ordinary nonempty items; Bar accepts the configured ingot list; Singles accepts allowed non-Bar items. `disable_mods` applies to gestures and automation, while `disable_items` applies only to player deposits. Internal settlement, gravity, and backfill never reapply admission rules.

Every loader-native view adapts `StackRunItemAccess` and spans the whole run plus one headroom block while growth is allowed. Storage slots use item stack limits; Singles and Bar slots hold one item. Forge and NeoForge each have one generic `RunItemHandler`; Fabric has one generic transaction adapter without per-type dispatch. Fabric stages mutations until outer transaction commit and permits one structural extraction position per transaction. `RunEdit` refuses reentrant automation mutations.

`WorldEdits` checks build limits, replaceability, vanilla obstruction, border, spawn and loader authority. Automated edits use the shared `[SomeStacks]` identity. Player cleanup retains the player; deferred Storage settlement uses automation for automated or mixed causes. Forge caches a connection-safe actor per dimension; NeoForge and Fabric use fake-player factories. Forge/NeoForge fire native place/break events. Fabric consults the destination callback and Common Protection API before placement; removal fires the `PlayerBlockBreakEvents` lifecycle. Outer edits emit `BLOCK_PLACE`/`BLOCK_DESTROY`; successful player content and rotation edits emit one `BLOCK_CHANGE`.

## Player interaction and networking

Shared client rules recognize permanence, block rotation, item rotation, deposit, placement, and extraction in that order. They suppress local vanilla block/item behavior without replacing the click: Forge/NeoForge cancellation and Fabric `SUCCESS` still send the normal block-use packet. A small payload sends placement mode and modifier-down state only when that state changes.

The only play-phase server-to-client payload is a cached config snapshot. It is split into bounded chunks; the client stages one generation and publishes enable flags, server render overrides and render-tool namespace lists only after the declared totals arrive. `/ss item` and `/ss write` are client command branches and perform their file changes directly, so no server packet can request a client write.

Non-sneaking existing-stack clicks run from `StackBlock.useItemOn`/`useWithoutItem`; loader server hooks handle sneaking torch rotation plus placement or deposit reached through a neighboring non-stack block. Both pass the actual vanilla `BlockHitResult` to `StackInteractions`, after vanilla has supplied target, reach pacing, and the ordinary interaction event. Common handling validates main hand, spectator/protection state, held item, support, type enablement, height, obstruction, and edit authority. Placement plus first deposit is one transaction. Cell selection for deposit, extraction, and Singles item rotation is recomputed server-side by extending a ray through the vanilla hit location; no client cell index is accepted.

The real loader event covers the clicked block. `AdjacentEdits` recursion-guards an additional destination check for Singles/Bar reached through a neighbor and for Fabric placement; only `PASS` authorizes it. There are no synthetic repeats for ordinary stack clicks, trailing-click suppressor, or per-gesture mutation packets.

## Configuration and presentation

World policy is `<world>/serverconfig/somestacks-server.json`, loaded atomically from defaults by `ServerConfig`; bad fields are independently reported and skipped. `/ss deny`, `/ss ingot`, and `/ss gen` save immediately. `/ss reload` rereads policy and server render overrides, then resyncs players. Ingot entries are `#`-prefixed tag patterns (`*` allowed) or bare item ids and resolve again on configuration or tag reload.

Extension points are the configured ingot list; registered `somestacks:block.*` sounds through ordinary resource-pack `sounds.json`; `assets/<namespace>/item_render_overrides/*.json`; `assets/<namespace>/textures/bars/*.json`; and `config/somestacks/server_item_overrides/*.json`.

All types use block entity renderers. `CubeGrid` and `CubeRenderHelper` place Storage and Singles item models; Bar uses resource-defined cuboids and tints. Storage/Singles profile precedence is server, user, resource pack, then measurement. `ItemCapture` records baked quads, tints, and bounds for measurement and 2-D projection; Bar auto-tint reads the first quad. `measured_cache.json` is keyed by format version, resource packs, and owning-mod versions. Bar appearance is client-only. Galleries spread work across ticks but bypass normal placement protection.

## GameTests

There is no JUnit or production `test` source set. Each loader has a development-only `somestacks_gametest`, run with `:<loader>:runGameTestServer` and excluded from `build`. Neutral scenarios live in `common/src/gametest`; loader-native automation and events stay local. Fabric additionally covers transaction behavior.

## Conventions the code currently follows

- Persistent state is block-local; run behavior lives in the pile/column layer.
- The three movement models stay separate, including Singles' rotation-aware item transport.
- Internal movement never revalidates stored items.
- Forge/NeoForge simulation and Fabric commit feasibility rules match.
- Structural edits go through `WorldEdits`; sync, light, comparator work, and settlement are batched.
- Gesture state is untrusted input; every vanilla server interaction revalidates the world, hand, item, geometry, and edit authority.
- Render-profile precedence is fixed; the measurement format version changes when fitting semantics change.
- Storage and Singles render scales are separate in `CubeRenderHelper`.
