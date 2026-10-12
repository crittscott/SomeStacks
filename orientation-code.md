<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — code orientation

See `orientation-player.md` for behavior; `build-env.md` for the build.

## Project shape

Java 17 / Minecraft 1.20.1; package `com.github.crittscott.somestacks`, id `somestacks`. Baselines: Forge 47.4.10, Fabric Loader 0.19.3 / API 0.92.7+1.20.1. Architectury is build-time only.

`common` folds into each loader JAR without loader imports. `CommonRegistry`, `EditAuthority`, networking callbacks, and gesture/storage adapters provide loader seams. Entry points install services/registries before common logic.

Release JARs: `<loader>/build/libs/somestacks-<loader>-1.20.1-<version>.jar`; Fabric uses Loom-remapped mixins without a runtime refmap.

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
| `WorldEdits`, `EditAuthority` | World edits and loader protection seam |
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
| Gestures | Interaction and input events | Interaction callbacks plus air-click mixin |
| Rendering | Shared BERs through registration/render seams | Shared BERs through Renderer API-aware seams |
| Automation | Whole-run `IItemHandler` capabilities | Whole-run Transfer API `Storage<ItemVariant>` providers |
| Player protection | Vanilla plus interaction/place events | Vanilla plus `UseBlockCallback` |
| Automated edits | Vanilla plus place/break events | Vanilla plus removal break callbacks; growth optionally queries FTB Chunks/OPAC |

## Runtime and movement model

`StackBlockEntity` owns slots, NBT, nested batches, and per-tick run caching. Server-only `StackRun` shares vertical bounds, flat slots, headroom, fill, comparators, and publication; concrete runs own movement. Structural edits invalidate bounds within the tick.

Block entities do not tick. Mutations schedule the base block for updates, light, comparators, and Storage settlement. Local fill contributions refresh on edits/load. All blocks are waterloggable, hold derived local light, and report the same run signal.

The movement models are distinct:

- `StoragePile` merges deposits, fills bottom-up, and grows. Settlement groups exact identities, precomputes item/damage/NBT sort keys once per group, restores legal stack sizes, sorts with descending-count ties, packs, and trims empty tops unless permanent/protected. Extraction schedules settlement; breaking drops local contents and separates runs.
- `SinglesColumn` inserts into one empty supported cell. Extraction shifts one visual column down across rotated seams, carrying item rotation and preserving unrelated gaps. Cleanup trims empty tops; breaking leaves the column above.
- `BarColumn` uses positional overlap support and alternating axes. Player extraction/removal cascades unsupported bars; automation backfills from the topmost bar. Insertion names an empty supported position.

`StackItemStorage` persists local inventory in block-entity NBT:

| Type | Saved data |
| --- | --- |
| Storage | `Items`, block `Rotation`, `Permanent` |
| Singles | `Items`, block `Rotation`, one `CubeRotations` entry per cell |
| Bar | `Items` |

Storage permanence logically belongs to the base and propagates during settlement. Storage/Singles block rotation is local to each block; Singles item rotation travels with the item through gravity.

## Admission, automation, and edits

Storage accepts ordinary nonempty items. Bar accepts items resolved from configured ingot-tag patterns; Singles accepts allowed non-Bar items. `disable_mods` applies to player deposits and automation; `disable_items` only to player deposits. Policy changes do not invalidate stored contents, and internal settlement/gravity/backfill do not reapply admission rules.

Forge's per-entity `RunItemHandler` uses `LazyOptional` invalidation. Views span the run plus headroom below maximum height; Storage respects item limits, Singles/Bar hold one item. Fabric's `StackBlockEntityMixin`/`FabricStorageOwner` caches one adapter per entity; all sides/blocks share the base ledger. Commit applies staging; abort restores snapshots; Singles/Bar allow one extraction position per transaction. `RunEdit` refuses reentrant automation.

Growth and cleanup pass through `WorldEdits`. Both loaders use an automation actor and apply build limits, replaceability, obstruction, border, and spawn checks. Forge uses 1.20.1 `FakePlayerFactory` and fires place/break events for growth/removal. `WorldEdits.placeChecked` currently publishes placement with `UPDATE_ALL` before protection acceptance and restores the previous block state on rejection.

Fabric removal fires `PlayerBlockBreakEvents`. There is no generic Fabric automation placement event; growth instead consults optional FTB Chunks and Open Parties and Claims through `FtbChunksProtection` and `OpacProtection`. Each mod's types stay inside its compat class, gated by loaded-mod checks; neither is touched when absent. Other Fabric claim mods are not consulted for growth.

## Player interaction and networking

Main-hand gestures recognize permanence, block rotation, item rotation, deposit, placement, then extraction. Loaders supply clicks/keys/transport; placement packets carry mode. Fabric uses an air-click mixin.

Requests validate sender, pacing, chunks, reach, hand, target, index, support, and permission. First placement/deposit is atomic.

Deposit/placement recompute ray targets server-side. Extraction and Singles item rotation use a client-supplied index checked for range and occupancy, without reconstructing the server ray.

Custom gestures displace ordinary use. Forge consults protection events before `RightClickBlockSuppressor` marks the trailing vanilla click for cancellation. Fabric performs its available vanilla edit checks before consuming interaction. Adjacent Singles/Bar deposits validate both clicked block and destination.

## Configuration and presentation

`ServerLifecycle` loads world-policy JSON at startup, syncs login/reload, clears pacing on logout, and clears policy, pacing, and gallery state on stop. `ServerConfig.Settings` copies lists; command edits replace snapshots, and `Persistence` owns JSON. `/ss deny`, `ingot`, and `gen` save immediately. `/ss reload` refreshes render overrides and resyncs, without rereading policy; file edits need restart.

Gallery commands use policy enablement (false) and permission (3), without a command editor; other permissions are fixed. Gallery jobs spread across ticks but bypass placement protection.

Ingot patterns match full tag names with `*`; membership rebuilds on config/tag changes. Shared resources: `common/src/main/resources`. Extension points:

- Item tags selected by `ingot_tags`, including `somestacks:ingots`, define Bar admission.
- `data/<namespace>/somestacks_sounds/*.json` defines action sounds.
- `assets/<namespace>/item_render_overrides/*.json` defines Storage/Singles profiles.
- `assets/<namespace>/textures/bars/*.json` defines Bar textures/tints.
- `config/somestacks/server_item_overrides/*.json` defines server-imposed profiles.

`CubeGrid` caches Storage/Singles rotations, boxes, and shapes for targeting/rendering; spacing, scales, and seam mapping stay type-specific. BERs use `CubeRenderHelper` for cubes and textured cuboids for Bar. Bar UV regions cache by sprite identity and clear on texture reload. `renderconfig` profiles use immutable `RenderOffset`; precedence is server -> user -> resource pack -> measurement. Bar appearance is client-only. `ItemRenderOverrides` caches profiles per item; server sync, local edits, corpus/model reload invalidate them through private-map entry points.

Flat GUI/geometry models use 2-D; others use 3-D. Forge measures all passes/custom vertices, with custom output restricted to 3-D. Fabric measures non-custom quads regardless of `isVanillaAdapter`; models without them use 3-D ItemRenderer.

`config/somestacks/measured_cache.json` records format version, selected packs, and owning-mod versions; mismatches reject affected measurements. Resource reload also invalidates measurements. `/ss item` edits the local in-memory layer; `/ss write changed` saves explicit changes, while namespace forms generate output-only profiles.

## Maintenance boundaries

State is block-local; runs own movement. Internal moves bypass admission; automation shares feasibility rules. `WorldEdits` handles structural edits; scheduled ticks batch publication and settlement.

Optional protection references stay in gated compat classes. Request validation is independent of gesture recognition. Profile precedence spans files, commands, and sync. Measurement semantic changes increment cache format. `STORAGE_CELL_RENDER_SCALE` differs from Singles' scale, whose cells tile edge to edge.

## GameTests

Root `build` includes common JUnit tests; loader `test` sets are empty. GameTests are development-only and run through each loader's `runGameTestServer`.

`configureGameTests` combines common/loader tests in dev-only `somestacks_gametest`; a shared Base64 fixture generates `data/somestacks/structures/`.

`GameTestScaffold`/`*Checks` hold loader-neutral assertions via `CommonRegistry`. Forge uses `@GameTestHolder`; Fabric uses `FabricGameTest`/`fabric-gametest`. Native storage assertions stay loader-local; Fabric uses `FakePlayer` instead of `FakePlayerFactory`.

`ProtectionGameTests` stays loader-local for native storage/hand-reset differences. Fabric omits Forge claim/event-bus tests. Fabric GameTests use `fabric/run-gametest`, separate from `fabric/run`.

`StackFoundationChecks` covers nested publication, same-tick bounds/headroom, loaded fill/shapes, and shared mutations through both loaders.

`FabricTransferGameTests` checks deferred commit, nested rollback, shared sided/multi-block counts and capacity, and run-wide structural limits.

`ServiceAndConfigChecks` covers services, logout pacing, and immutable policy publication.
