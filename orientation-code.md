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
| `ServerConfig` | Immutable `Settings`, JSON `Persistence`, deny/ingot sets |
| `PlatformServices`, `ServerLifecycle` | Loader paths/version/transport/authority; shared session and sync |
| `renderconfig` | Server-safe profiles, modes, offsets, JSON codec |
| `WorldEdits`, `EditAuthority` | Native placement transactions, adjacent protection, automated removal |
| `StackInteractions`, `ServerGestureState` | Native block-use gestures, server targeting, synchronized mode/modifier state |
| Loader entry points / registries | Registration, lifecycle, common service installation |
| Loader network packages | Native transport around common validation and payloads |
| `RunItemHandlerBase`, Forge `RunItemHandler`, Fabric Transfer adapter | Whole-run native storage views |
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
| Player protection | Vanilla plus interaction/place events | Vanilla plus `UseBlockCallback` |
| Automated edits | Vanilla plus place/break events | Vanilla plus removal break callbacks; growth optionally queries FTB Chunks/OPAC |

## Runtime and movement model

`StackBlockEntity` owns slots, NBT, batches, and per-tick run caches. A resolved run is cached on every member for that tick. `StackRun` shares bounds, flat slots, headroom, fill, comparators, and publication; concrete runs own movement. Structural edits invalidate bounds immediately.

Block entities do not tick. Mutations schedule the base block for updates, light, comparators, and Storage settlement. Local fill contributions update from the changed slot, including inside batches, and rebuild on load. All blocks are waterloggable, hold derived local light, and report the same run signal.

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

`WorldEdits` gates growth/cleanup on build limits, replaceability, obstruction, border, and spawn checks. Forge retains `FakePlayerFactory` and place/break events. Placement captures snapshots, restores denied state/NBT, and publishes accepted `onPlace`/`markAndNotifyBlock`; reentrancy and foreign capture/restoration are refused. Fabric queries optional protection before placement.

Fabric removal fires `PlayerBlockBreakEvents`. Growth has no generic placement event; `FtbChunksProtection`/`OpacProtection` query optional FTB Chunks/Open Parties and Claims. Their types stay in loaded-mod-gated compat classes. Other Fabric claim mods are not consulted for growth.

## Player interaction and networking

Gesture order: permanence, block rotation, item rotation, deposit, placement, extraction. `ClientGestures` sends changed mode/modifier state before block-use packets. Reconnect resets sync; logout/start/stop clear state/pacing. Fabric retains its air-click mixin.

`StackInteractions.handleBlockClick` runs after native protection listeners, validating pacing, spectator status, loaded targets, reach, main hand, prerequisites, admission, support, and permissions. First placement/deposit is atomic. Protocol 3 uses `GestureStatePkt` instead of action packets; decoding rejects invalid modes.

Deposit, placement, extraction, and Singles item rotation all trace the server player's current view. Client hit coordinates do not select cells.

Client SUCCESS consumes local use and retains block-use packets. Server callbacks consume recognized gestures even on admission failure; ordinary sneaking/off-hand actions do not mutate stacks. Forge item denial prevents deposits/placement while permitted block access can extract/rotate. Adjacent deposits consult both positions under a gesture recursion guard.

## Configuration and presentation

`ServerLifecycle` loads/syncs policy and clears sessions. `ServerConfig.Settings` copies lists; commands replace snapshots, and `Persistence` owns JSON. `/ss deny`, `ingot`, and `gen` save immediately. `/ss reload` refreshes render overrides and resyncs; policy file edits need restart.

Galleries default to disabled/permission 3, spread work across ticks, and bypass placement protection. Other command permissions are fixed.

Ingot patterns match full tag names with `*`; membership rebuilds on config/tag changes. Shared resources: `common/src/main/resources`. Extension points:

- Item tags selected by `ingot_tags`, including `somestacks:ingots`, define Bar admission.
- `data/<namespace>/somestacks_sounds/*.json` defines action sounds.
- `assets/<namespace>/item_render_overrides/*.json` defines Storage/Singles profiles.
- `assets/<namespace>/textures/bars/*.json` defines Bar textures/tints.
- `config/somestacks/server_item_overrides/*.json` defines server-imposed profiles.

`CubeGrid` caches cell rotations, boxes, and shapes; spacing, scales, and seams stay type-specific. BERs use `CubeRenderHelper` for cubes and cuboids for Bar. Bar UV regions cache by sprite identity and clear on texture reload. Profiles use immutable `RenderOffset`; precedence is server -> user -> resource pack -> measurement. `ItemRenderOverrides` caches profiles, invalidated by sync, edits, and corpus/model reload. Bar appearance is client-only.

Flat models use 2-D; others use 3-D. Forge measures all passes/custom vertices; custom output uses 3-D. Fabric measures non-custom quads regardless of `isVanillaAdapter`; missing quads use 3-D ItemRenderer.

`measured_cache.json` keys measurements by format, packs, and owning-mod versions; mismatches/reload invalidate them. `/ss item` edits memory; `/ss write changed` saves; namespace forms generate output-only profiles.

## Maintenance boundaries

State is local; runs own movement. Internal moves bypass admission. `WorldEdits` owns structural edits; scheduled ticks batch publication/settlement.

Protection types stay gated. Server validation is independent of client recognition. Measurement changes increment cache format. Storage has its own render scale; Singles cells tile edge to edge.

## Tests

Root `build` includes common JUnit; loader `test` sets are empty. GameTests use `runGameTestServer`.

`configureGameTests` combines common/loader tests in dev-only `somestacks_gametest`; a shared Base64 fixture generates `data/somestacks/structures/`.

`GameTestScaffold`/`*Checks` hold shared assertions via `CommonRegistry`. Forge uses `@GameTestHolder`; Fabric uses `FabricGameTest`/`fabric-gametest` and `FakePlayer`. Native storage assertions stay loader-local.

`ProtectionGameTests` stays loader-local: both test adjacent denial; Forge also tests split permissions, rollback/publication, and placement reentrancy. Fabric GameTests use `fabric/run-gametest`.

`StackFoundationChecks` covers nested publication/fill, shared caches/expiry, same-tick bounds/headroom/splits, growth, loaded fill/shapes, comparator endpoints, and shared mutations.

`FabricTransferGameTests` checks deferred commit, nested rollback, shared sided/multi-block counts and capacity, and run-wide structural limits.

`ServiceAndConfigChecks` covers services, logout pacing, and policy publication. `InteractionChecks`/`ClientGestureChecks` cover gestures, server rays, rejection, hands, ordering, sync, and logout. `runClientGameTests` uses `run-client-gametest`; `/ssclienttest` checks local callback consumption.
