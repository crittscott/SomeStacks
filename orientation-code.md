<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

## Project shape

Java 17 / Minecraft 1.20.1; package `com.github.crittscott.somestacks`, id `somestacks`. Baselines: Forge 47.4.10, Fabric Loader 0.19.3 / API 0.92.7+1.20.1.

`common` bundles in both loaders; platform services install before initialization. Fabric mixins have no refmap.

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
| `StackRun`, `StoragePile`, `SinglesColumn`, `BarColumn` | Run bounds, flat slots, headroom, comparators, publication, concrete movement |
| `StackItemStorage` | Block-local slots, admission, simulation, and NBT persistence |
| `CubeGrid`, `util/*CubeIdx`, `ViewRay` | Cached cell geometry/rotation, support, seams, targeting |
| `ServerConfig` | Immutable `Settings`, session reader/saver, deny sets; loader-owned persistence |
| `PlatformServices`, `ServerLifecycle` | Loader paths/version/transport/authority; shared session and sync |
| `client/renderconfig` | Client profiles, modes, offsets, JSON codec |
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
| Networking | `SimpleChannel` protocol 5; mismatch rejected | Protocol 5 login query; client JOIN rejects absent/mismatched handshake; English disconnect fallback |
| Gestures | `Block.use`; LOWEST `RightClickBlock` for sneaking/adjacent | `Block.use`; late `UseBlockCallback` for sneaking/adjacent; air-click mixin |
| Client commands | `RegisterClientCommandsEvent` | `ClientCommandRegistrationCallback` plus server pass-through |
| Rendering | Shared BERs through registration/render seams | Shared BERs through Renderer API-aware seams |
| Automation | Whole-run `IItemHandler` capabilities | Whole-run Transfer API `Storage<ItemVariant>` providers |
| Server policy | Native `SERVER` `ForgeConfigSpec` / TOML / reload events | TOML via `FabricServerConfig` |
| Player protection | Vanilla plus interaction/place events | Vanilla plus `UseBlockCallback`; CPA for adjacent deposits |
| Automated edits | Vanilla plus place/break events | Vanilla, break callbacks, CPA/FTB/OPAC placement queries |

## Runtime and movement

`StackBlockEntity` owns slots, capacity, NBT, batches, and one-tick run caches. `StackRun` owns bounds, headroom, fill, comparators, and publication; concrete runs own movement. Structural edits invalidate bounds.

Entities do not tick. Mutations schedule publication and Storage settlement. Comparators read the run; light and waterlogging are local.

- `StoragePile` fills bottom-up and grows. Settlement groups read-only inputs in one identity map, copies legal output pieces, sorts cached keys/counts through `StackSort.comparePieces`, packs, and trims empty tops unless permanent/protected. Extraction schedules settlement; breaking drops local contents and splits runs. Publication skips piles emptied by trimming.
- `SinglesColumn` inserts into one empty supported cell. Extraction shifts one visual column down across rotated seams, carrying item rotation. Cleanup trims empty tops; breaking leaves the column above.
- `BarColumn` uses positional overlap support and alternating axes. Player extraction/removal cascades unsupported bars; automation backfills from the topmost bar. Insertion names an empty supported position.

`Containers.dropItemStack` drops contents regardless of `doTileDrops`. `BarDropBatch` groups exact identities per block; the helper may split them into entities.

`StackItemStorage` persists local inventory in block-entity NBT stamped with `DataVersion`:

| Type | Saved data |
| --- | --- |
| Storage | `Items`, `Permanent` |
| Singles | `Items`, one `CubeRotations` entry per cell |
| Bar | `Items` |

Unreadable, out-of-range, or duplicate-slot entries stay verbatim in `Items.SetAside`, retried each load and logged per location (`ServerLifecycle.reportSavedItems`).

Storage permanence follows the base. Storage/Singles use `horizontal_facing`; getters are pure. Storage growth places the base facing; gesture joins prefer below, then above. Native placement keeps supplied facing; `onPlace` inherits permanence only. Singles gravity preserves item rotation.

Client `load` calls `loadClientUpdate`, which requires current `DataVersion`, well-formed slots/items/rotations, and no nested recovery data or Storage permanence. Unrelated metadata is ignored; Storage owns its permanence validation. Update tags strip `Permanent` and `SetAside`.

## Admission, automation, and edits

`BlockType` owns admission and policy messages: Storage takes nonempty items; Bar requires `BarStackBE.INGOTS`; Singles excludes it. Mod denial applies to players/automation; exact-item denial applies to players. Internal moves and saves bypass admission.

Forge `RunItemHandler` uses `LazyOptional` invalidation. Fabric caches adapters per entity through `FabricStorageOwner`; all sides/blocks share the base ledger. Singles/Bar permit one extraction position per transaction. Views include headroom below maximum height. Fabric sweeps resolve one run/ledger; retained views resolve on access. Growth checks last one insertion sweep: Storage reuses obstruction; Singles/Bar check each candidate shape. Commit re-resolves and validates each mutation. `RunEdit` refuses reentrant automation.

`WorldEdits` uses `PlatformServices.editAuthority()` and checks height, replaceability, obstruction, border, and spawn; `canGrow` requires the actor's build ability and asks `EditAuthority.mayPlace`. Null edit actors resolve in `WorldEdits` to `AutomationActor.PROFILE` through loader fake players. Forge uses place/break events, snapshot rollback, and `onPlace`/`markAndNotifyBlock` publication, refusing reentrancy and foreign capture.

Fabric removal asks Common Protection API, then `PlayerBlockBreakEvents`. Fabric `mayPlace` ANDs the mod-gated `CommonProtectionQueries`, `FtbChunksProtection`, and `OpacProtection` before `setBlock`; adjacent deposits also ask `canInteractBlock`.

Checked edits emit `BLOCK_PLACE`/`BLOCK_DESTROY` only on success. Removal uses `removeBlock` (fluid stays) with the original actor/state/entity; Fabric sends `CANCELED` on query/event veto, `AFTER` on success. Cleanup from one player's extraction or toggle uses that player (Storage via `noteCleanupActor` until settlement); otherwise automation. `StackInteractions` emits `BLOCK_CHANGE` after successful gestures unless extraction removed the block.

## Player interaction and networking

Existing stacks handle permanence, deposits/top placement, and extraction through `Block.use`/`handleExistingStack`. Loader events handle sneaking torch rotation and clicks on other blocks. `ClientGestures` sends changed mode/modifier state before block-use packets. Reconnect resets sync; logout/start/stop clear gesture state and `StackSounds` pacing through shared lifecycle cleanup. Fabric's air-click mixin checks only the main hand. Modifier air clicks return `CONSUME` (Fabric: early `stack_modifier` phase); the server refuses main-hand item use while the modifier is down.

Vanilla checks reach and loaded chunks; `StackInteractions` validates hand, spectator, prerequisites, admission, support, and permissions, and traces cells along `ViewRay.through` (eye through the received hit point). First placement/deposit is atomic.

Client SUCCESS sends packets. Stacks consume server use; sneaking/off-hand use does not mutate. Forge item-only denial permits gestures. Adjacent deposits check both positions and guard recursion.

`SomeStacksCommon.PROTOCOL_VERSION` versions both transports. Fabric negotiates during login and resets acceptance per connection. `ConfigSyncPkt` sends flags and both namespace lists in one packet; network-thread decoding rejects invalid counts/namespaces, duplicates, and trailing data. Client-thread `ClientServerSettings` replaces one immutable snapshot for cycling and authoring commands. Gesture handlers ignore disconnected players.

## Configuration and presentation

`Settings` bounds numbers; `ServerConfig.start` installs loader persistence/sync using `Keys`. Forge publishes TOML on the server thread; Fabric rereads TOML on `/ss reload`. Fabric logs read failures with file/key and defaults in use, successful default-file creation, and numeric corrections. Decode exceptions select all defaults; commands save active settings over the file. Publication normalizes namespaces and omits malformed names; excess over 256 names of 256 characters retains prior policy (startup defaults). Only changed client policy syncs; login always sends it. Gallery access changes refresh connected command trees. Policy summaries log at startup, explicit reload, and changed Forge file reloads, not list edits or unchanged watcher publications.

Galleries default off/permission 3; build by id across ticks, skip denied items, bypass protection, clear on stop.

Resources (`common/src/main/resources`):

- The `somestacks:ingots` item tag defines Bar admission; galleries read it on demand.
- `assets/somestacks/sounds.json` maps the nine registered `somestacks:block.<type>.<action>` events.
- `assets/<namespace>/item_render_overrides/*.json` defines Storage/Singles profiles.
- `assets/<namespace>/textures/bars/*.json` defines Bar textures/tints.

`CubeGrid` caches geometry/quarter turns; setters normalize rotations, updates reject invalid ones. `BarCubeIdx` shares rendering/targeting shapes; Singles/Bar unions optimize per rebuild. Profile precedence: user -> pack -> measurement; offsets use `RenderOffset`. Failed flat capture logs once and draws `stack_cube_error` until reload. Measurement exceptions log once per item per resource-load cycle with context/throwable; empty geometry stays quiet. Bar caches sprites/UVs; derived tints multiply the first GUI quad's sprite and tint. Bar mappings use `SimpleJsonResourceReloadListener` with sorted parsing in `apply`; render overrides skip absent namespaces before parsing. Warnings identify files; reload/edits invalidate caches.

Flat models use 2-D, others 3-D. Forge measures vertices; Fabric measures quads or ItemRenderer output.

`measured_cache.json` keys by format/packs/mod versions; changes invalidate it. `/ss item` edits memory; `/ss write changed` saves; namespace forms write output-only profiles.

## Tests

`somestacks_gametest`: shared/loader scenarios.

Shared checks cover persistence, movement, rotations, gestures, policy/network validation, growth permissions, placement facing, and Bar drops without tile drops. Forge adds TOML persistence; Fabric adds Transfer transactions and login validation. Protection checks cover denial, shapes, rollback/reentrancy, and break context; `-PwithCommonProtectionApi=true` selects provider-backed checks.

