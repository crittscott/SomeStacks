<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

See `orientation-player.md` for behavior; `build-env.md` for the build.

## Project shape

Java 17 / Minecraft 1.20.1; package `com.github.crittscott.somestacks`, id `somestacks`. Baselines: Forge 47.4.10, Fabric Loader 0.19.3 / API 0.92.7+1.20.1. Architectury is build-time only.

`common` folds into each loader JAR without loader imports; loader seams install before common initialization. Fabric mixins are Loom-remapped without a runtime refmap.

Three blocks and block entity types; no block items, menus, recipes, or portable containers:

| Block | Block entity | Local storage |
| --- | --- | --- |
| `storage_stack_block` | `stack_be` | 27 item stacks |
| `singles_stack_block` | `singles_stack_be` | 64 single items in a 4 x 4 x 4 grid |
| `bar_stack_block` | `bar_stack_be` | 64 bars in eight alternating layers |

## Code map

| Area | Responsibility |
| --- | --- |
| `common/.../block/*StackBlock` | State, shapes, waterlogging, removal, comparators, scheduled publication, gesture `use` |
| `StackBlockEntity`, `block/*StackBE` | Local inventory, NBT, sync, light, nested batching, rotations, run caches |
| `StackRun`, `StackRunItemAccess`, `StoragePile`, `SinglesColumn`, `BarColumn` | Run bounds, flat slots, headroom, comparators, publication, concrete movement |
| `StackItemStorage`, `SlotAccess` | Loader-neutral fixed-slot storage and automation contract |
| `CubeGrid`, `util/*CubeIdx`, `ViewRay(s)` | Cached cell geometry/rotation, support, seams, targeting |
| `ServerConfig` | Immutable `Settings`, session reader/saver, deny sets; loader-owned persistence |
| `PlatformServices`, `ServerLifecycle` | Loader paths/version/transport/authority; shared session and sync |
| `renderconfig` | Server-safe profiles, modes, offsets, JSON codec |
| `WorldEdits`, `EditAuthority` | Placement queries and transactions, adjacent protection, automated removal |
| `StackInteractions`, `ServerGestureState` | Gestures, hit-point targeting, synced mode/modifier |
| Loader entry points, registries, network | Registration (incl. sounds), lifecycle, services, transport |
| Forge `RunItemHandler`, Fabric Transfer adapter | Whole-run native storage views |
| `common/.../client` | Renderers, profiles, measurement, bar textures, `ClientRenderCommands` |
| `SsCommand`, `RenderGalleryGenerator` | Server administration, galleries |

## Loader integration

| Concern | Forge | Fabric |
| --- | --- | --- |
| Metadata | `META-INF/mods.toml` | `fabric.mod.json`, `somestacks.mixins.json` |
| Entrypoints | `SomeStacks`, client `ClientSetup` | `SomeStacksFabric`, `SomeStacksFabricClient` |
| Networking | `SimpleChannel` protocol 4; mismatch rejected | Play networking; `protocol_4` handshake gates play packets |
| Gestures | `Block.use`; LOWEST `RightClickBlock` for sneaking/adjacent | `Block.use`; late `UseBlockCallback` for sneaking/adjacent; air-click mixin |
| Client commands | `RegisterClientCommandsEvent` | `ClientCommandRegistrationCallback` plus server pass-through |
| Rendering | Shared BERs through registration/render seams | Shared BERs through Renderer API-aware seams |
| Automation | Whole-run `IItemHandler` capabilities | Whole-run Transfer API `Storage<ItemVariant>` providers |
| Server policy | Native `SERVER` `ForgeConfigSpec` / TOML / reload events | World JSON via `FabricServerConfig` |
| Player protection | Vanilla plus interaction/place events | Vanilla plus `UseBlockCallback`; CPA for adjacent deposits |
| Automated edits | Vanilla plus place/break events | Vanilla, break callbacks, CPA/FTB/OPAC placement queries |

## Runtime and movement model

`StackBlockEntity` owns slots, NBT, batches, and one-tick shared run caches. `StackRun` owns bounds, headroom, fill, comparators, and publication; concrete runs own movement. Structural edits invalidate bounds immediately.

Block entities do not tick. Mutations schedule the base for publication and Storage settlement. Blocks are waterloggable, hold derived local light, and share the run's comparator signal.

The movement models are distinct:

- `StoragePile` merges, fills bottom-up, and grows. Settlement groups exact identities, restores legal sizes, sorts with descending-count ties, packs, and trims empty tops unless permanent/protected. Extraction schedules settlement; breaking drops local contents and splits runs.
- `SinglesColumn` inserts into one empty supported cell. Extraction shifts one visual column down across rotated seams, carrying item rotation. Cleanup trims empty tops; breaking leaves the column above.
- `BarColumn` uses positional overlap support and alternating axes. Player extraction/removal cascades unsupported bars; automation backfills from the topmost bar. Insertion names an empty supported position.

`StackItemStorage` persists local inventory in block-entity NBT stamped with `DataVersion`:

| Type | Saved data |
| --- | --- |
| Storage | `Items`, `Permanent` |
| Singles | `Items`, one `CubeRotations` entry per cell |
| Bar | `Items` |

Unreadable, out-of-range, or duplicate-slot entries stay verbatim in `Items.SetAside`, retried each load and logged per location (`ServerLifecycle.reportSavedItems`).

Storage permanence belongs to the base and propagates during settlement. Storage/Singles block rotation is the `horizontal_facing` state; `rotate`/`mirror` turn it, and Storage growth copies the base's facing. Singles item rotation travels through gravity.

Client `load` calls `loadClientUpdate`, which applies an update only if wholly valid: current `DataVersion`, no server-only fields, well-formed slots/items/rotations. Update tags strip `Permanent` and `SetAside`.

## Admission, automation, and edits

Storage accepts ordinary nonempty items. Bar accepts members of the `somestacks:ingots` item tag (`BarStackBE.INGOTS`); Singles accepts allowed non-Bar items. `disable_mods` applies to player deposits and automation; `disable_items` only to player deposits. Policy changes never invalidate stored contents; internal moves skip admission.

Forge's per-entity `RunItemHandler` uses `LazyOptional` invalidation. Fabric's `StackBlockEntityMixin`/`FabricStorageOwner` caches one adapter per entity; every side/block shares the base ledger. Singles/Bar allow one extraction position per transaction. Views span the run plus headroom below maximum height. `RunEdit` refuses reentrant automation.

`WorldEdits` gates growth/cleanup on height, replaceability, completed-shape obstruction, border, and spawn; `canGrow` also asks `EditAuthority.mayPlace`. Automation acts as `AutomationActor.PROFILE` through each loader's fake player. Forge uses place/break events, snapshot rollback, and `onPlace`/`markAndNotifyBlock` publication, refusing reentrancy and foreign capture.

Fabric removal fires `PlayerBlockBreakEvents`. Lacking a placement event, Fabric `mayPlace` ANDs the mod-gated `CommonProtectionQueries`, `FtbChunksProtection`, and `OpacProtection`, checked before `setBlock`; adjacent deposits also ask `canInteractBlock`.

Checked placement/removal emits `BLOCK_PLACE`/`BLOCK_DESTROY` only on success. Removal uses `removeBlock` (fluid stays) with one actor, old state, and entity; Fabric sends `CANCELED` on a `BEFORE` veto, `AFTER` on success. Cleanup from one player's extraction or toggle uses that player (Storage via `noteCleanupActor` until settlement); otherwise automation. `StackInteractions` emits `BLOCK_CHANGE` after successful gestures unless extraction removed the block.

## Player interaction and networking

Existing-stack gestures run from each block's `use` (`handleExistingStack`): permanence, deposit or top-face placement, extraction. Sneaking torch rotation and clicks on other blocks run from the loader event, since vanilla skips `use` when sneaking with an item. `ClientGestures` sends changed mode/modifier state before block-use packets. Reconnect resets sync; logout/start/stop clear state and `RotationSoundThrottle`. Fabric's air-click mixin checks only the main hand.

Vanilla checks reach and loaded chunks; `StackInteractions` validates hand, spectator, prerequisites, admission, support, and permissions, and traces cells along `ViewRays.through` (eye through the received hit point). First placement/deposit is atomic.

Client SUCCESS consumes use but still sends the packet. Stack blocks consume every server use; sneaking/off-hand use does not mutate stacks. Forge item-only denial does not block existing-stack gestures. Adjacent deposits consult both positions, guarding recursion.

`ConfigSyncPkt` is one byte-bounded chunk of a generation (flags, `gen_mods`, `disable_mods`); decoding rejects bad counts/namespaces. `ClientRenderPacketSink` publishes to `StackState`/`ClientRenderToolState` only at a complete last chunk; bad sequences throw and clear staging, as does disconnect.

## Configuration and presentation

`ServerLifecycle` sends a fresh `ConfigSyncPkt.snapshot` on login/broadcast and clears sessions. Loaders install a policy reader and saver through `ServerConfig.start`. Forge's `SERVER` TOML reloads on the server thread and resyncs on any change; Fabric rereads JSON on `/ss reload`. List commands save through the loader, resyncing after `gen_mods`/`disable_mods` edits.

Galleries default to disabled/permission 3, spread work across ticks, bypass placement protection, list items in id order without denied items, and clear on server start/stop.

Resources: `common/src/main/resources`. Extension points:

- The `somestacks:ingots` item tag defines Bar admission; galleries read it on demand.
- `assets/somestacks/sounds.json` maps the nine registered `somestacks:block.<type>.<action>` events.
- `assets/<namespace>/item_render_overrides/*.json` defines Storage/Singles profiles.
- `assets/<namespace>/textures/bars/*.json` defines Bar textures/tints.

`CubeGrid` caches rotations, boxes, and shapes. BERs draw via `CubeRenderHelper`; Bar UVs cache per sprite. Profiles use immutable `RenderOffset`; precedence is user -> resource pack -> measurement. A `2d` model that throws while its art is gathered (before drawing) joins `FLAT_CAPTURE_FAILURES` and draws `stack_cube_error` until reload, logged once. Derived Bar tints multiply the first GUI quad's sprite by its tint. `ItemRenderOverrides` caches profiles until edits or corpus/model reload. Bar appearance is client-only.

Flat models use 2-D; others 3-D. Forge measures all passes/custom vertices; custom output uses 3-D. Fabric measures non-custom quads; missing quads use 3-D ItemRenderer.

`measured_cache.json` keys by format, packs, and mod versions; mismatch/reload invalidates it. Client-local `/ss item` edits memory; `/ss write changed` saves; namespace forms write output-only profiles.

## Tests

Root `build` runs common and Fabric JUnit; Forge has none. `configureGameTests` combines shared/loader scenarios in test-only `somestacks_gametest`, generating structures from Base64.

Shared checks cover batching, caches, bounds, movement, saves, set-aside recovery, client updates, structure rotation, fill/shapes, comparators, gestures via `gameMode.useItemOn`, hit-point targeting, Bar admission, policy, and reload. Forge adds TOML saves/reloads; Fabric adds Transfer commit/rollback and capacity. Protection checks cover adjacent denial, partial shapes, silent rejection, Forge block-vs-item denial and rollback/reentrancy, and Fabric removal vetoes/break context; `-PwithCommonProtectionApi=true` swaps `NoProtectionApiGameTests` for `ProtectionApiGameTests`.

`WorldEditChecks` covers game events, automation identity, cleanup water/actors. `PacketCodecTest` checks chunking, atomic publication, staging, and decoder bounds; `CommandRegistrationTest` both command trees; `BundledResourceTest` sounds/subtitles. `RenderGalleryChecks` checks command gates/deny edits, intercepting policy saves.

`runGameTestServer` runs loader scenarios; Fabric uses `fabric/run-gametest`. `runClientGameTests` uses `run-client-gametest`; `/ssclienttest` checks local callback consumption.
