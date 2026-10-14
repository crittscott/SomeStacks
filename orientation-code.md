<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

See `orientation-player.md` for behavior; `build-env.md` for the build.

## Project shape

Java 17 / Minecraft 1.20.1; package `com.github.crittscott.somestacks`, id `somestacks`. Baselines: Forge 47.4.10, Fabric Loader 0.19.3 / API 0.92.7+1.20.1. Architectury is build-time only.

`common` folds into each loader JAR without loader imports. Loader seams install before common initialization.

Fabric uses Loom-remapped mixins without a runtime refmap.

Three blocks and block entity types; no block items, menus, recipes, or portable containers:

| Block | Block entity | Local storage |
| --- | --- | --- |
| `storage_stack_block` | `stack_be` | 27 item stacks |
| `singles_stack_block` | `singles_stack_be` | 64 single items in a 4 x 4 x 4 grid |
| `bar_stack_block` | `bar_stack_be` | 64 single items in eight alternating layers |

## Code map

| Area | Responsibility |
| --- | --- |
| `common/.../block/*StackBlock` | State, shapes, waterlogging, removal, comparators, scheduled publication |
| `StackBlockEntity`, `block/*StackBE` | Local inventory, NBT, sync, light, nested batching, rotations, run caches |
| `StackRun`, `StackRunItemAccess`, `StoragePile`, `SinglesColumn`, `BarColumn` | Run bounds, flat slots, headroom, comparators, publication, concrete movement |
| `StackItemStorage`, `SlotAccess` | Loader-neutral fixed-slot storage and automation contract |
| `CubeGrid`, `util/*CubeIdx`, `ViewRay` | Cached cell geometry/rotation, support, seams, targeting |
| `ServerConfig` | Immutable `Settings`, session saver, deny/ingot sets; loader-owned persistence |
| `PlatformServices`, `ServerLifecycle` | Loader paths/version/transport/authority; shared session and sync |
| `renderconfig` | Server-safe profiles, modes, offsets, JSON codec |
| `WorldEdits`, `EditAuthority` | Native placement transactions, adjacent protection, automated removal |
| `StackInteractions`, `ServerGestureState` | Native block-use gestures, server targeting, synchronized mode/modifier state |
| Loader entry points / registries | Registration, lifecycle, common service installation |
| Loader network packages | Native transport around common validation and payloads |
| Forge `RunItemHandler`, Fabric Transfer adapter | Whole-run native storage views |
| `common/.../client` | Renderers, profiles, model measurement, bar textures |
| Loader client packages | Renderer registration, gestures, keys, events, packet transport |
| `SsCommand`, `RenderGalleryGenerator` | Administration, profile authoring, galleries |

## Loader integration

| Concern | Forge | Fabric |
| --- | --- | --- |
| Metadata | `META-INF/mods.toml` | `fabric.mod.json`, `somestacks.mixins.json` |
| Entrypoints | `SomeStacks`, client `ClientSetup` | `SomeStacksFabric`, `SomeStacksFabricClient` |
| Networking | `SimpleChannel`; compatibility rejects version mismatch | `ServerPlayNetworking` / `ClientPlayNetworking`; versioned handshake gates play packets |
| Gestures | Native server `RightClickBlock` at LOWEST priority | Native server `UseBlockCallback` after the default phase; client air-click mixin |
| Rendering | Shared BERs through registration/render seams | Shared BERs through Renderer API-aware seams |
| Automation | Whole-run `IItemHandler` capabilities | Whole-run Transfer API `Storage<ItemVariant>` providers |
| Server policy | Native `SERVER` `ForgeConfigSpec` / TOML / reload events | World JSON via `FabricServerConfig` |
| Player protection | Vanilla plus interaction/place events | Vanilla plus `UseBlockCallback` |
| Automated edits | Vanilla plus place/break events | Vanilla plus removal break callbacks; growth optionally queries FTB Chunks/OPAC |

## Runtime and movement model

`StackBlockEntity` owns slots, NBT, batches, and one-tick shared run caches. `StackRun` owns bounds, headroom, fill, comparators, and publication; concrete runs own movement. Structural edits invalidate bounds immediately.

Block entities do not tick. Mutations schedule the base for publication and Storage settlement. Fill updates per changed slot, including in batches, and rebuilds on load. Blocks are waterloggable, hold derived local light, and share the run's comparator signal.

The movement models are distinct:

- `StoragePile` merges, fills bottom-up, and grows. Settlement groups exact identities, caches item/damage/NBT sort keys, restores legal sizes, sorts with descending-count ties, packs, and trims empty tops unless permanent/protected. Extraction schedules settlement; breaking drops local contents and splits runs.
- `SinglesColumn` inserts into one empty supported cell. Extraction shifts one visual column down across rotated seams, carrying item rotation and preserving unrelated gaps. Cleanup trims empty tops; breaking leaves the column above.
- `BarColumn` uses positional overlap support and alternating axes. Player extraction/removal cascades unsupported bars; automation backfills from the topmost bar. Insertion names an empty supported position.

`StackItemStorage` persists local inventory in block-entity NBT:

| Type | Saved data |
| --- | --- |
| Storage | `Items`, block `Rotation`, `Permanent` |
| Singles | `Items`, block `Rotation`, one `CubeRotations` entry per cell |
| Bar | `Items` |

Storage permanence belongs to the base and propagates during settlement. Storage/Singles block rotation is local; Singles item rotation travels through gravity.

## Admission, automation, and edits

Storage accepts ordinary nonempty items. Bar accepts items resolved from configured ingot-tag patterns; Singles accepts allowed non-Bar items. `disable_mods` applies to player deposits and automation; `disable_items` only to player deposits. Policy changes do not invalidate stored contents, and internal settlement/gravity/backfill do not reapply admission rules.

Forge's per-entity `RunItemHandler` uses `LazyOptional` invalidation. Fabric's `StackBlockEntityMixin`/`FabricStorageOwner` caches one adapter per entity; every side/block shares the base ledger. Commit applies staging; abort restores snapshots; Singles/Bar allow one extraction position per transaction. Views span the run plus headroom below maximum height. `RunEdit` refuses reentrant automation.

Singles/Bar insertion probes read the target slot and only its immediate supporting positions; rotated Singles seams map visual columns between blocks. Bar support candidates cache fixed footprint overlaps. Collapse retains mutable occupancy snapshots.

`WorldEdits` gates growth/cleanup on height, replaceability, native completed-shape obstruction, border, and spawn checks. Forge uses `FakePlayerFactory`, place/break events, snapshot rollback, and accepted `onPlace`/`markAndNotifyBlock` publication; reentrancy and foreign capture/restoration are refused. Fabric uses `FakePlayer.get(level)` and optional placement protection.

Fabric removal fires `PlayerBlockBreakEvents`. Growth has no generic placement event; `FtbChunksProtection`/`OpacProtection` query optional FTB Chunks/Open Parties and Claims. Their types stay in loaded-mod-gated compat classes. Other Fabric claim mods are not consulted for growth.

Checked placement/removal emits `BLOCK_PLACE`/`BLOCK_DESTROY` only on success, with actor and resulting/old state. Removal retains one actor, old state, and entity; Fabric sends `CANCELED` on a `BEFORE` veto and `AFTER` on successful removal.

## Player interaction and networking

Gesture order: permanence, block rotation, item rotation, deposit, placement, extraction. `ClientGestures` sends changed mode/modifier state before block-use packets. Reconnect resets sync; logout/start/stop clear state/pacing. Fabric retains its air-click mixin.

`StackInteractions.handleBlockClick` runs after native protection listeners, validating pacing, spectator status, loaded targets, reach, main hand, prerequisites, admission, support, and permissions. First placement/deposit is atomic. Protocol 3 uses `GestureStatePkt` instead of action packets; decoding rejects invalid modes.

Deposit, placement, extraction, and Singles item rotation all trace the server player's current view. Client hit coordinates do not select cells.

Client SUCCESS consumes use while retaining block-use packets. Server callbacks consume recognized gestures even on admission failure; ordinary sneaking/off-hand actions do not mutate stacks. Forge item denial blocks deposits/placement but can allow extraction/rotation. Adjacent deposits consult both positions under a recursion guard.

`ClientRenderPacketSink` ignores rejected counts; valid empty payloads clear overrides or request a user-layer write.

## Configuration and presentation

`ServerLifecycle` syncs policy and clears sessions. Loaders install immutable `Settings` and a saver. Forge's native `SERVER` TOML reloads on the server thread and resyncs changed enable flags; Fabric JSON edits need restart. List commands save through the loader; `/ss reload` refreshes render overrides and sync, not policy.

Galleries default to disabled/permission 3, spread work across ticks, and bypass placement protection. Other command permissions are fixed.

Ingot patterns match full tag names with `*`; membership rebuilds on config/tag changes. Shared resources: `common/src/main/resources`. Extension points:

- Item tags selected by `ingot_tags`, including `somestacks:ingots`, define Bar admission.
- `data/<namespace>/somestacks_sounds/*.json` defines action sounds.
- `assets/<namespace>/item_render_overrides/*.json` defines Storage/Singles profiles.
- `assets/<namespace>/textures/bars/*.json` defines Bar textures/tints.
- `config/somestacks/server_item_overrides/*.json` defines server-imposed profiles.

`CubeGrid` caches rotations, boxes, and shapes. BERs use `CubeRenderHelper` for cubes and cuboids for Bar. Bar UVs cache by sprite identity until texture reload. Profiles use immutable `RenderOffset`; precedence is server -> user -> resource pack -> measurement. `ItemRenderOverrides` caches profiles until sync, edits, or corpus/model reload. Bar appearance is client-only.

Flat models use 2-D; others 3-D. Forge measures all passes/custom vertices; custom output uses 3-D. Fabric measures non-custom quads regardless of `isVanillaAdapter`; missing quads use 3-D ItemRenderer.

`measured_cache.json` keys by format, packs, and mod versions; mismatch/reload invalidates it. `/ss item` edits memory; `/ss write changed` saves; namespace forms generate output-only profiles.

## Maintenance boundaries

Protection types stay gated; server validation is independent. Measurement changes increment cache format. Storage has its own scale; Singles cells tile.

## Tests

Root `build` runs common and Fabric JUnit; Forge has none. `configureGameTests` combines shared/loader scenarios in test-only `somestacks_gametest`, generating structures from Base64. Registration: Forge `@GameTestHolder`, Fabric `FabricGameTest`/`fabric-gametest`.

Shared checks cover batching, caches, bounds, movement, persistence, fill/shapes, comparators, split runs, gestures, rays, rejection, and policy. Forge adds TOML saves/reloads; Fabric adds Transfer commit/rollback and capacity. Protection checks cover adjacent denial, partial shapes, Forge rollback/reentrancy, and Fabric removal vetoes.

`WorldEditChecks` observes placement/growth/cleanup game events. Protection tests check silent rejection/rollback and Fabric break-callback order, context, and failed mutation.

`PacketCodecTest` checks rejection, empty payloads, and wire sanitization. `RenderGalleryChecks` checks command gates/deny edits in `somestacks_commands`, intercepting saves with a test-only Bar tag. Shared fixtures use `GameTestScaffold`/`CommonRegistry`.

`runGameTestServer` runs loader scenarios; Fabric uses `fabric/run-gametest`. `runClientGameTests` uses `run-client-gametest`; `/ssclienttest` checks local callback consumption.
