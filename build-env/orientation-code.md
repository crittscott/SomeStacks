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
| `StackBlock`, `block/*StackBlock` | Base: light, water, strength, `use`, comparators, breaker; subclasses: shapes, removal, publication |
| `StackBlockEntity`, `block/*StackBE` | Local inventory, NBT, sync, light, nested batching, rotations, run caches |
| `StackRun`, `StoragePile`, `SinglesColumn`, `BarColumn` | Run bounds, flat slots, headroom, growth gate, comparators, publication, movement |
| `StackItemStorage` | Block-local slots, admission, simulation, and NBT persistence |
| `CubeGrid`, `util/*CubeIdx`, `ViewRay` | Cell geometry/rotation, support; `ViewRay` cell targeting |
| `ServerConfig` | Immutable `Settings`, `Setting` file declaration, reader/saver, deny sets |
| `PlatformServices`, `ServerLifecycle` | Loader paths/version/reach/transport/authority; shared session and sync |
| `client/renderconfig` | Client profiles, modes, offsets, JSON codec |
| `WorldEdits`, `EditAuthority` | Placement queries and transactions, adjacent protection, automated removal |
| `StackInteractions`, `StackGestures`, `ServerGestureState` | Gestures, click classification, targeting, synced mode/modifier |
| Loader entry points, registries, network | Native registration; common IDs and block properties; lifecycle/transport |
| Forge/Fabric `*RunItemStorage` | Whole-run native storage views |
| `common/.../client` | Renderers, profiles, measurement, bar textures, `ClientRenderCommands` |
| `SsCommand`, `RenderGalleryGenerator` | Server administration, galleries |

## Loader integration

| Concern | Forge | Fabric |
| --- | --- | --- |
| Metadata | `META-INF/mods.toml` | `fabric.mod.json`, `somestacks.mixins.json` |
| Entrypoints | `SomeStacks`, client `ForgeClientRendering` | `SomeStacksFabric`, client `SomeStacksFabricClient` |
| Networking | `SimpleChannel` protocol 5; mismatch rejected | Protocol 5 login query; client JOIN rejects absent/mismatched handshake; English disconnect fallback |
| Gestures | `Block.use`; LOWEST `RightClickBlock` for sneaking/adjacent | `Block.use`; late `UseBlockCallback` for sneaking/adjacent; air-click mixin |
| Client commands | `RegisterClientCommandsEvent` | `ClientCommandRegistrationCallback` plus server pass-through |
| Rendering | Shared BERs through registration/render seams | Shared BERs through Renderer API-aware seams |
| Automation | Whole-run `IItemHandler` capabilities | Whole-run Transfer API `Storage<ItemVariant>` providers |
| Server policy | Native `SERVER` `ForgeConfigSpec` / TOML / reload events | `FabricServerConfig`/`TomlPolicyFile`; JSON migration |
| Player protection | Vanilla plus interaction/place events | Vanilla plus `UseBlockCallback`; CPA for adjacent deposits |
| Automated edits | Vanilla plus place/break events | Vanilla, break callbacks, CPA/FTB/OPAC placement queries |

## Runtime and movement

Entities do not tick. Mutations schedule publication and Storage settlement; structural edits invalidate cached run bounds. Comparators read the run; light and waterlogging are local.

- `StoragePile` fills bottom-up and grows. Settlement groups slots uncopied through `ItemOps.consolidate` (vanilla item-and-tags identity), sorts through `StackSort.comparePieces`, copies only rewritten slots, packs, and trims empty tops unless permanent/protected. Extraction schedules settlement; breaking drops local contents and splits runs. Publication skips piles emptied by trimming.
- `SinglesColumn` inserts into one empty supported cell. Extraction shifts one visual column down across rotated seams, carrying item rotation. Cleanup trims empty tops; breaking leaves the column above.
- `BarColumn` uses positional overlap support and alternating axes. Player extraction/removal cascades unsupported bars; automation backfills from the topmost bar. Insertion names an empty supported position.

`Containers.dropItemStack` drops contents regardless of `doTileDrops`; `BarStackBE.DropBatch` consolidates copied drops per block.

`StackItemStorage` persists local inventory in block-entity NBT stamped with `DataVersion`:

| Type | Saved data |
| --- | --- |
| Storage | `Items`, `Permanent` |
| Singles | `Items`, one `CubeRotations` entry per cell |
| Bar | `Items` |

Unreadable, out-of-range, or duplicate-slot entries stay verbatim in `Items.SetAside`, retried each load, logged per location.

Storage permanence follows the base. Storage/Singles use `horizontal_facing`; getters are pure. Storage growth keeps the base facing; gesture joins prefer below, then above. Native placement keeps its facing; `onPlace` inherits permanence only. Singles gravity preserves item rotation.

Client `load` uses `loadClientUpdate`: current `DataVersion`, well-formed slots/items/rotations, no recovery data or Storage permanence; other metadata is ignored. Update tags strip `Permanent` and `SetAside`.

## Admission, automation, and edits

`BlockType` owns admission and policy messages: Storage takes nonempty items; Bar requires `BarStackBE.INGOTS`; Singles excludes it. Mod denial applies to players/automation; exact-item denial applies to players. Internal moves and saves bypass admission.

Adapters read `advertisedStack` (run, else local slot) and `slotLimit`/`admits`; Fabric admits once per call and stages what the run accepts. `ForgeCapabilityAttachment` uses `LazyOptional` invalidation. Fabric caches adapters per entity (`FabricStorageOwner`); all sides/blocks share the base ledger. Singles/Bar allow one extraction position per transaction. Views include headroom below maximum height; retained Fabric views resolve on access. Growth checks last one insertion sweep: Storage reuses obstruction; Singles/Bar check each candidate shape. Commit re-resolves and validates each mutation. `RunEdit` refuses reentrant automation.

`WorldEdits.mayPlace` is the one placement gate: height, replaceability, `mayUseItemAt` for the main-hand item, border/spawn, `EditAuthority.mayPlace`; `placeChecked` adds obstruction, and gestures and `StackRun.canGrowSpace` ask it first. Null edit actors resolve in `WorldEdits` to `AutomationActor.PROFILE` through loader fake players. Forge uses place/break events, snapshot rollback, and `onPlace`/`markAndNotifyBlock` publication, refusing reentrancy and foreign capture.

Fabric removal asks Common Protection API, then `PlayerBlockBreakEvents`. Fabric `mayPlace` ANDs the mod-gated `CommonProtectionQueries`, `FtbChunksProtection`, and `OpacProtection`; `place` only sets the block; adjacent deposits also ask `canInteractBlock`.

Checked edits emit `BLOCK_PLACE`/`BLOCK_DESTROY` only on success. Removal uses `removeBlock` (fluid stays) with the original actor/state/entity; Fabric sends `CANCELED` on veto, `AFTER` on success. Cleanup from one player's extraction, toggle, or break (`playerWillDestroy`) uses that player (Storage via `noteCleanupActor`); otherwise automation. `StackInteractions` emits `BLOCK_CHANGE` after successful gestures unless extraction removed the block.

## Player interaction and networking

`Block.use`/`handleExistingStack` handles permanence, deposit/top placement, and extraction; loader events handle sneaking torch rotation and other-block clicks. Client rules and server actions share `StackGestures`. `ClientGestures` sends changed mode/modifier state before block-use packets; Forge's LOWEST client block listener syncs even when canceled. Reconnect resets sync; logout/start/stop clear gesture state and rotation-sound pacing. With the modifier down the client consumes main-hand item use (cycling the mode at air); the server refuses it. Fabric's air-click mixin checks main hand, destroying, and busy hands.

`StackInteractions` checks hand, spectator, and permissions, then has one deposit and one extract path through `StackBlockEntity` (`depositFromHand`, `extractIntoHand`, targets along `ViewRay.through`); `StackBlock.firstDepositShape` preflights new blocks, so first placement/deposit is atomic. `BlockType` picks sounds from `CommonRegistry.Sound`. Stacks consume server use; sneaking/off-hand use does not mutate. Adjacent deposits check both positions and guard recursion.

`SomeStacksCommon.PROTOCOL_VERSION` versions both transports; `StackMode` and Forge messages have explicit wire ids. Fabric negotiates during login and resets acceptance per connection. `ConfigSyncPkt` sends flags and both namespace lists; network-thread decoding rejects invalid counts/namespaces, duplicates, and trailing data. Client-thread `ClientServerSettings` replaces one immutable snapshot and advances a newly disabled selected mode. Gesture handlers ignore disconnected players.

## Configuration and presentation

`Settings` bounds numbers; `ServerConfig.Setting` declares each file field once, and Forge's spec and Fabric's `TomlPolicyFile` build from it. Forge publishes TOML on the server thread; Fabric rereads its world or instance file on `/ss reload`, warnings naming file and key. Decode exceptions use all defaults; commands save active settings. Publication normalizes namespaces, omits malformed names, and keeps prior policy past 256 names of 256 characters. Only changed client policy syncs (one broadcast); login always sends it. Gallery access changes refresh command trees. Policy summaries (gallery included) log at startup, explicit reload, and changed Forge file reloads.

Galleries default off/permission 3; build by id across ticks, skip denied items, bypass protection, clear on stop.

Resources (`common/src/main/resources`):

- The `somestacks:ingots` item tag defines Bar admission; galleries read it on demand.
- `assets/somestacks/sounds.json` maps the `CommonRegistry.Sound` events.
- `assets/<namespace>/item_render_overrides/*.json` defines Storage/Singles profiles.
- `assets/<namespace>/textures/bars/*.json` defines Bar textures/tints.

`CubeGrid` caches geometry/quarter turns; setters normalize rotations, updates reject invalid ones. `BarCubeIdx` shares rendering/targeting shapes; Singles/Bar unions optimize per rebuild. Profile precedence: user -> pack -> measurement; offsets use `RenderOffset`. Failed flat capture logs once and draws `stack_cube_error` until reload. Measurement exceptions log once per item per reload with context; empty geometry stays quiet. Bar caches sprites/UVs; derived tints multiply the first GUI quad's sprite and tint. Bar mappings use `SimpleJsonResourceReloadListener`, parsing sorted in `apply`; overrides skip absent namespaces. Warnings name files; reload/edits invalidate caches.

Flat models use 2-D, others 3-D. Measurers share quad bounds; Forge probes custom renderers, Fabric falls back.

`measured_cache.json` keys by format/packs/mod versions. `/ss item` edits memory; `/ss write changed` saves, writing malformed user-file entries back verbatim and refusing (return 0) while the file is unreadable; namespace forms write output-only profiles.

## Tests

`somestacks_gametest`: shared/loader scenarios.

Shared checks cover persistence, movement, rotations, gestures, policy/network validation, growth/placement permissions, facing, and Bar drops without tile drops. Forge adds config persistence; Fabric adds TOML/JSON migration, Transfer transactions, and login validation. Protection checks cover denial, shapes, rollback/reentrancy, and break context; `-PwithCommonProtectionApi=true` selects provider-backed checks.

