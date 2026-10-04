<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

Subsystem ownership, persistent data, loader boundaries, and invariants the code currently maintains. `orientation-player.md` covers observable behavior; `build-env.md` covers the build.

## Project shape

Some Stacks is a Java 21 mod for Minecraft 1.21.4 under `com.github.crittscott.somestacks`, mod id `somestacks`. Development builds against Forge 54.1.18, NeoForge 21.4.158, and Fabric Loader 0.19.5 with Fabric API 0.119.4+1.21.4. Architectury is build-time only; no loader has an Architectury API runtime dependency.

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
| `WorldEdits`, `EditAuthority` | World edits and loader protection seam |
| `network/*` | Payloads and shared request validation |
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

The selected loader build is required on client and server. Loaders own transport and callbacks; packet codecs, request handlers, gesture rules, rendering, commands, and storage mechanics remain in `common`. Fabric's mixins build with the legacy annotation processor into the fixed `somestacks.refmap.json` refmap.

## Runtime and movement model

A maximal contiguous vertical run of one stack type is the central abstraction. Block entities own local slots; the server-only `StoragePile`, `SinglesColumn`, and `BarColumn` coordinate cross-block operations and hold a `ServerLevel`. Run resolution is cached per game tick and invalidated by structural changes.

Block entities do not tick. `StackBlockEntity` owns local persistence and a nesting-safe batch depth; mutations schedule one deferred pass on the run's bottom block that coalesces client updates, lighting, comparator publication, and Storage settlement. Every block in a run reports the same run-wide comparator signal.

The three movement models are distinct:

- `StoragePile` is a flat inventory from the base up. Settlement groups exact identities, restores legal stack sizes, sorts, packs down, and removes unneeded top blocks unless permanent or protected.
- `SinglesColumn` is positional. Extraction shifts one visual column down across rotation-aware seams, transports item rotation, preserves unrelated gaps, and does not collapse above a broken block.
- `BarColumn` is positional and overlap-supported. Player extraction and block removal cascade; automation extraction moves the topmost bar into the opened position instead.

`StackItemStorage` persists local inventory through each block entity's NBT methods using Data Components and a `HolderLookup.Provider`. Storage saves `DataVersion`, `Items`, `Rotation`, and `Permanent`; Singles adds `CubeRotations`; Bar saves `DataVersion` and `Items`.

`StackDataMigration` runs first in every `loadAdditional`. A tag without `DataVersion` is 1.21.1 (3955); an older tag has each stored item tag run through vanilla's DataFixerUpper (`References.ITEM_STACK`), is restamped, and marks the block entity dirty. That flag is a no-op during chunk load, when the block entity has no level yet, so an unsaved chunk simply re-migrates on its next load. Item tags that fail to parse or have no free slot go to a `SetAside` list in the storage tag, are saved back, retried on every load, and logged with the block position. Update tags carry `DataVersion` but omit `SetAside`. Permanence is pile-wide but stored on the base; block rotations are local; Singles item rotation travels with the item.

## Admission, automation, and edits

Storage accepts ordinary nonempty items; Bar accepts the configured ingot list; Singles accepts allowed non-Bar items. `disable_mods` applies to gestures and automation, while `disable_items` applies only to player deposits. Internal settlement, gravity, and backfill never reapply admission rules.

Every loader-native view adapts `StackRunItemAccess` and spans the whole run plus one headroom block while growth is allowed. Storage slots use item stack limits; Singles and Bar slots hold one item. Forge and NeoForge each have one generic `RunItemHandler`; Fabric has one generic transaction adapter without per-type dispatch. Fabric stages mutations until outer transaction commit and permits one structural extraction position per transaction. `RunEdit` refuses reentrant automation mutations.

Growth and cleanup use `WorldEdits` with an automation actor and check build limits, replaceability, obstruction, border, spawn, and loader authority. Forge and NeoForge fire native place/break events. Fabric uses Fabric API's `FakePlayer`, fires `PlayerBlockBreakEvents` on removal, and routes optional Common Protection API growth checks only through `CommonProtectionCheck`.

## Player interaction and networking

Shared gesture rules recognize permanence, block rotation, item rotation, deposit, placement, and extraction in that order. Placement mode is client state sent with the placement request.

The server treats every payload as a request. Decoders reject invalid enum ids and bounded collection sizes before dispatch. Common validation covers sender state, per-tick pacing, loaded chunks, and reach; each operation then validates hand, target, index, support, type enablement, height, and edit authority. Placement and first deposit are one transaction. Deposit and placement recompute ray targets server-side; extraction and Singles item rotation accept a client index only after range and occupancy checks.

`EventPlayerEditAuthority` owns the common protection-check ordering and view-hit calculation. Forge and NeoForge supply their native right-click event and mark `RightClickBlockSuppressor` only after every check succeeds; Fabric supplies `UseBlockCallback` and needs no trailing-click mark. Adjacent Singles and Bar deposits validate both the clicked position and destination.

## Configuration and presentation

World policy is `<world>/serverconfig/somestacks-server.json`, owned by `ServerConfig` and loaded at server startup. `/ss deny`, `/ss ingot`, and `/ss gen` save immediately. `/ss reload` reloads server render overrides and resyncs players but does not reread policy JSON. Ingot-list entries are either a `#`-prefixed item-tag pattern (`*` allowed, matched against whole tag names) or a bare item id, and resolve again on configuration or tag reload.

Extension points are the configured ingot list; `data/<namespace>/somestacks_sounds/*.json`; `assets/<namespace>/item_render_overrides/*.json`; `assets/<namespace>/textures/bars/*.json`; and `config/somestacks/server_item_overrides/*.json`.

All types use block entity renderers. `CubeRenderHelper` renders Storage and Singles item models; Bar uses fixed cuboids with resource-defined textures and tints. Storage/Singles profile precedence is server, user, resource pack, then measurement. `ItemCapture` resolves a stack through vanilla's item model resolver and records the draw: baked quads with their resolved layer tint, plus bounds of anything written vertex by vertex. Measurement and the `2d` projector both read it; only items drawn entirely as baked quads may use the 2-D projector, and Bar auto-tint reads the first captured quad's sprite and tint. `measured_cache.json` is keyed by format version, resource packs, and owning-mod versions. Bar appearance is client-only. Galleries spread work across ticks but bypass normal placement protection.

## GameTests

There is no JUnit or production `test` source set. Each loader has a development-only `somestacks_gametest` mod under `<loader>/src/gametest` and runs it with `:<loader>:runGameTestServer`; no suite is part of `build`. Neutral assertions, shared protection scenarios, and the shared Base64 empty-structure fixture live under `common/src/gametest`; assertions use `CommonRegistry`. Native `IItemHandler` and Transfer API assertions stay loader-local. Forge and NeoForge use annotated holders; Fabric uses `fabric-gametest` entrypoints. NeoForge holders disable class-name template prefixes. Loader-native event and synthetic-player assertions remain loader-specific. Distinct otherwise-identical test stacks use `CUSTOM_DATA`.

## Conventions the code currently follows

- Persistent state is block-local; run behavior lives in the pile/column layer.
- The three movement models stay separate, including Singles' rotation-aware item transport.
- Internal movement never revalidates stored items.
- Forge/NeoForge simulation and Fabric commit feasibility rules match.
- Structural edits go through `WorldEdits`; sync, light, comparator work, and settlement are batched.
- Every client request is validated independently of client gesture recognition.
- Render-profile precedence is fixed; the measurement format version changes when fitting semantics change.
- Storage and Singles render scales are separate in `CubeRenderHelper`.
