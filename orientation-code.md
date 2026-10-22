<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

## Project shape

Java 17 / Minecraft 1.20.1; package `com.github.crittscott.somestacks`, id `somestacks`. Baselines: Forge 47.4.10, Fabric Loader 0.19.3 / API 0.92.7+1.20.1. Architectury is build-time.

`common` bundles into each loader JAR; loader seams install before common initialization. Fabric mixins use Loom remapping without a refmap.

Three block/entity types; no items, menus, or recipes:

| Block | Block entity | Local storage |
| --- | --- | --- |
| `storage_stack_block` | `stack_be` | 27 item stacks |
| `singles_stack_block` | `singles_stack_be` | 64 single items in a 4 x 4 x 4 grid |
| `bar_stack_block` | `bar_stack_be` | 64 bars in eight alternating layers |

## Code map

| Area | Responsibility |
| --- | --- |
| `common/.../block/*StackBlock` | Shapes, waterlogging, removal, comparators, publication, gesture `use` |
| `StackBlockEntity`, `block/*StackBE` | Local inventory, NBT, sync, light, nested batching, rotations, run caches |
| `StackRun`, `StackRunItemAccess`, `StoragePile`, `SinglesColumn`, `BarColumn` | Run bounds, flat slots, headroom, comparators, publication, concrete movement |
| `StackItemStorage` | Block-local slots, admission, simulation, and NBT persistence |
| `CubeGrid`, `util/*CubeIdx`, `ViewRay(s)` | Cached cell geometry/rotation, support, seams, targeting |
| `ServerConfig` | Immutable `Settings`, session reader/saver, deny sets; loader-owned persistence |
| `PlatformServices`, `ServerLifecycle` | Loader paths/version/transport/authority; shared session and sync |
| `renderconfig` | Server-safe profiles, modes, offsets, JSON codec |
| `WorldEdits`, `EditAuthority` | Placement queries and transactions, adjacent protection, automated removal |
| `StackInteractions`, `ServerGestureState` | Gestures, hit-point targeting, synced mode/modifier |
| Loader entry points, registries, network | Native registration; common IDs and block properties; lifecycle/transport |
| Forge `RunItemHandler`, Fabric Transfer adapter | Whole-run native storage views |
| `common/.../client` | Renderers, profiles, measurement, bar textures, `ClientRenderCommands` |
| `SsCommand`, `RenderGalleryGenerator` | Server administration, galleries |

## Loader integration

| Concern | Forge | Fabric |
| --- | --- | --- |
| Metadata | `META-INF/mods.toml` | `fabric.mod.json`, `somestacks.mixins.json` |
| Entrypoints | `SomeStacks`, client `ClientSetup` | `SomeStacksFabric`, `SomeStacksFabricClient` |
| Networking | `SimpleChannel` protocol 4; mismatch rejected | Play networking; `protocol_4` handshake gates play packets; missing-mod disconnect text has an English fallback |
| Gestures | `Block.use`; LOWEST `RightClickBlock` for sneaking/adjacent | `Block.use`; late `UseBlockCallback` for sneaking/adjacent; air-click mixin |
| Client commands | `RegisterClientCommandsEvent` | `ClientCommandRegistrationCallback` plus server pass-through |
| Rendering | Shared BERs through registration/render seams | Shared BERs through Renderer API-aware seams |
| Automation | Whole-run `IItemHandler` capabilities | Whole-run Transfer API `Storage<ItemVariant>` providers |
| Server policy | Native `SERVER` `ForgeConfigSpec` / TOML / reload events | World JSON via `FabricServerConfig` |
| Player protection | Vanilla plus interaction/place events | Vanilla plus `UseBlockCallback`; CPA for adjacent deposits |
| Automated edits | Vanilla plus place/break events | Vanilla, break callbacks, CPA/FTB/OPAC placement queries |

## Runtime and movement

`StackBlockEntity` owns slots, advertised capacity, NBT, batches, and one-tick run caches. `StackRun` owns bounds, headroom, fill, comparators, and publication; concrete runs own movement. Structural edits invalidate bounds immediately.

Entities do not tick. Mutations schedule base publication and Storage settlement. Blocks share a run comparator signal, with local light and waterlogging.

- `StoragePile` fills bottom-up and grows. Settlement groups read-only inputs in one identity map, copies legal output pieces, sorts cached keys/counts through `StackSort.comparePieces`, packs, and trims empty tops unless permanent/protected. Extraction schedules settlement; breaking drops local contents and splits runs. A resolved pile can be empty after trimming its last block; publication then does nothing.
- `SinglesColumn` inserts into one empty supported cell. Extraction shifts one visual column down across rotated seams, carrying item rotation. Cleanup trims empty tops; breaking leaves the column above.
- `BarColumn` uses positional overlap support and alternating axes. Player extraction/removal cascades unsupported bars; automation backfills from the topmost bar. Insertion names an empty supported position.

Contents drop through `Containers.dropItemStack` regardless of `doTileDrops`. `BarDropBatch` groups exact identities per originating block; the helper may split packed stacks into several entities.

`StackItemStorage` persists local inventory in block-entity NBT stamped with `DataVersion`:

| Type | Saved data |
| --- | --- |
| Storage | `Items`, `Permanent` |
| Singles | `Items`, one `CubeRotations` entry per cell |
| Bar | `Items` |

Unreadable, out-of-range, or duplicate-slot entries stay verbatim in `Items.SetAside`, retried each load and logged per location (`ServerLifecycle.reportSavedItems`).

Settlement propagates Storage permanence from the base. Storage/Singles use `horizontal_facing` for structure transforms; Storage growth copies it. Singles gravity preserves item rotation.

Client `load` calls `loadClientUpdate`, which requires current `DataVersion`, well-formed slots/items/rotations, and no nested recovery data or Storage permanence. Unrelated metadata is ignored; Storage owns its permanence validation. Update tags strip `Permanent` and `SetAside`.

## Admission, automation, and edits

Storage accepts ordinary nonempty items. Bar accepts members of the `somestacks:ingots` item tag (`BarStackBE.INGOTS`); Singles accepts allowed non-Bar items. `disable_mods` applies to player deposits and automation; `disable_items` only to player deposits. Policy changes never invalidate stored contents; internal moves skip admission.

Forge `RunItemHandler` uses `LazyOptional` invalidation. Fabric caches adapters per entity through `FabricStorageOwner`; all sides/blocks share the base ledger. Singles/Bar permit one extraction position per transaction. Views include headroom below maximum height. Fabric sweeps resolve one run/ledger; retained views resolve on access. Growth checks last one insertion sweep: Storage reuses obstruction; Singles/Bar check each candidate shape. Commit re-resolves and validates each mutation. `RunEdit` refuses reentrant automation.

`WorldEdits` uses `PlatformServices.editAuthority()` and checks height, replaceability, obstruction, border, and spawn; `canGrow` asks `EditAuthority.mayPlace`. Null edit actors resolve in `WorldEdits` to `AutomationActor.PROFILE` through loader fake players. Forge uses place/break events, snapshot rollback, and `onPlace`/`markAndNotifyBlock` publication, refusing reentrancy and foreign capture.

Fabric removal asks Common Protection API, then `PlayerBlockBreakEvents`. Fabric `mayPlace` ANDs the mod-gated `CommonProtectionQueries`, `FtbChunksProtection`, and `OpacProtection` before `setBlock`; adjacent deposits also ask `canInteractBlock`.

Checked edits emit `BLOCK_PLACE`/`BLOCK_DESTROY` only on success. Removal uses `removeBlock` (fluid stays) with the original actor/state/entity; Fabric sends `CANCELED` on query/event veto, `AFTER` on success. Cleanup from one player's extraction or toggle uses that player (Storage via `noteCleanupActor` until settlement); otherwise automation. `StackInteractions` emits `BLOCK_CHANGE` after successful gestures unless extraction removed the block.

## Player interaction and networking

Existing stacks handle permanence, deposits/top placement, and extraction through `Block.use`/`handleExistingStack`. Loader events handle sneaking torch rotation and clicks on other blocks. `ClientGestures` sends changed mode/modifier state before block-use packets. Reconnect resets sync; logout/start/stop clear gesture and sound state. Fabric's air-click mixin checks only the main hand. Modifier air clicks return `CONSUME` (Fabric: early `stack_modifier` phase); the server refuses main-hand item use while the modifier is down.

Vanilla checks reach and loaded chunks; `StackInteractions` validates hand, spectator, prerequisites, admission, support, and permissions, and traces cells along `ViewRays.through` (eye through the received hit point). First placement/deposit is atomic.

Client SUCCESS still sends the packet. Stack blocks consume every server use; sneaking/off-hand use does not mutate stacks. Forge item-only denial allows existing-stack gestures. Adjacent deposits check both positions, guarding recursion.

`SomeStacksCommon.PROTOCOL_VERSION` supplies both transports' wire version. `ConfigSyncPkt` carries byte-bounded generation chunks (flags, `gen_mods`, `disable_mods`), rejecting invalid counts/namespaces. `ClientRenderPacketSink` publishes complete generations to `StackState`/`ClientRenderToolState`; invalid sequences throw and clear staging, also cleared on disconnect.

## Configuration and presentation

`ServerLifecycle` sends `ConfigSyncPkt.snapshot` on login/broadcast and clears sessions. `ServerConfig.Settings` normalizes numeric ranges; loaders install world persistence via `ServerConfig.start` and share `ServerConfig.Keys`. Forge TOML reload publishes on the server thread; Fabric rereads JSON on `/ss reload`. List edits save through the loader; `gen_mods`/`disable_mods` changes resync clients.

Galleries default off/permission 3, build incrementally in id order, skip denied items, bypass protection, and clear between sessions.

Resources (`common/src/main/resources`):

- The `somestacks:ingots` item tag defines Bar admission; galleries read it on demand.
- `assets/somestacks/sounds.json` maps the nine registered `somestacks:block.<type>.<action>` events.
- `assets/<namespace>/item_render_overrides/*.json` defines Storage/Singles profiles.
- `assets/<namespace>/textures/bars/*.json` defines Bar textures/tints.

`CubeGrid` caches geometry and defines quarter turns; setters normalize rotations, client updates reject invalid ones; `BarCubeIdx` shares cached boxes/shapes across rendering and targeting. Singles/Bar unions optimize once per rebuild. All three renderers use the dispatcher's supplied packed light. Storage/Singles use `CubeRenderHelper`. Bar caches sprites/UVs, transforms scalar vertices/normals, and resolves textures/tints per item; resource reload clears client caches. Profiles use `RenderOffset`; precedence: user -> pack -> measurement. A `2d` model that throws while its art is gathered (before drawing) joins `FLAT_CAPTURE_FAILURES` and draws `stack_cube_error` until reload, logged once. Derived Bar tints multiply the first GUI quad's sprite by its tint. `ItemRenderOverrides` caches profiles until edits or corpus/model reload.

Flat models use 2-D; others 3-D. Forge measures passes/custom vertices; Fabric measures quads with a 3-D ItemRenderer fallback.

`measured_cache.json` keys by format, packs, and mod versions; mismatch/reload invalidates it. Client-local `/ss item` edits memory; `/ss write changed` saves; namespace forms write output-only profiles.

## Tests

`somestacks_gametest` combines shared/loader scenarios.

Shared checks cover batching, geometry, persistence, movement, rotation, gestures, numeric policy ranges, and Bar drops with block drops disabled. Forge adds TOML saves/reloads; Fabric covers Transfer transactions/capacity, partial-first insertion, and retained views after base removal. Protection checks cover adjacent denial, partial shapes, rollback/reentrancy, and break context; `-PwithCommonProtectionApi=true` selects provider-backed checks.

`WorldEditChecks`: edits; `RenderGalleryChecks`: commands.
