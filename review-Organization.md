# Code Review: Organization

Scope: `common/src/main` (plus the loader item-handler wrappers). Focus: scattered
functionality that could be consolidated into fewer, thicker classes. No code changed.

## Summary

The mod has three stack families (Storage, Singles, Bar). Each family is implemented as a
fully independent triple — `*StackBlock`, `*StackBE`, and a run aggregate
(`StoragePile` / `SinglesColumn` / `BarColumn`) — plus a per-family geometry helper
(`StorageCubeIdx` / `SinglesCubeIdx` / `BarCubeIdx`). **There is no shared base type
anywhere in this hierarchy** (confirmed: every `*Block` is `extends Block`, every `*BE` is
`extends BlockEntity`, every handler is a bare `implements IItemHandler`). The result is
that a large amount of mechanically identical plumbing — run resolution, cache
invalidation, comparator publication, deferred-tick scheduling, advertised-slot math,
empty-block trimming, batch/sync bookkeeping, NBT round-tripping, update packets — is
written out three times, and in the Bar/Singles case very nearly line-for-line.

Separately, the "which of the three kinds is this" decision is re-made by hand
(`if (be instanceof StorageStackBE) … else if SinglesStackBE … else if BarStackBE`) in at
least six places, because there is no strategy object that bundles a kind's BE class,
trace functions, validity predicate, deposit sound, and run accessor.

The client `interaction` package and the render/measure classes are, by contrast, well
organized and are not flagged here.

---

## Finding 1 — The three run aggregates are ~60% identical boilerplate

Files:
- `common/.../block/StoragePile.java` (605 lines)
- `common/.../block/SinglesColumn.java` (442 lines)
- `common/.../block/BarColumn.java` (509 lines)

`BarColumn` and `SinglesColumn` are close to a copy/paste of each other; `StoragePile`
shares the same skeleton with a different fill/insert/settle core. The following members
are identical (or identical bar a block constant / BE type) across all three:

| member | notes |
|---|---|
| `at(Level, BlockPos)` | identical except the BE cast |
| `resolve(Level, BlockPos)` | identical except the BE type |
| `invalidateAround` / `invalidateRun` | identical (the javadoc in `SinglesColumn`/`BarColumn` even says "see `StoragePile.invalidateAround` for the reasoning") |
| `markDirtyAt` | identical |
| `maxHeight()` | all three return `ServerConfig.maxPileHeight()` |
| `columnHasRoomFor` / `runLength` | identical except the block constant |
| `height` / `totalSlots` / `advertisedSlots` | identical |
| `getSlot` / `handlerOf` | identical |
| `comparatorSignal()` | identical: `fill > 0 ? Mth.floor(fill * 14) + 1 : 0` |
| `publishComparatorSignal()` | identical except the block constant |
| `markDirty()` (scheduleTick on the base) | identical |
| `topPos()` | identical |
| `trimEmptyTop()` | identical skeleton; Storage adds an `isPermanent()` guard |

What genuinely differs is the storage model: `fillLevel()` (occupancy vs. summed stack
fractions), insertion (`insertOneAt` structural/grounded vs. `insertAt` + base-up
`deposit`), extraction (top-of-column backfill vs. visual-column shift-down vs. plain +
`settle`), and Storage's `permanent` flag / `settle` / `consolidate` / `adoptNeighbourState`.

Suggested consolidation: an abstract `StackRun` holding `Level level` and
`List<? extends AbstractStackBE> blocks`, owning every row of the table above, with
abstract hooks for `fillLevel()`, insertion, extraction, and `publishPending`/`settle`.
`BarColumn` and `SinglesColumn` could then share an intermediate `StructuralColumn`
carrying the grounded-insert + grow-one-block logic, leaving each leaf class only its
geometry-specific extraction. Rough saving: 300–400 lines and, more importantly, one
place to reason about comparator/tick/invalidation semantics instead of three.

Minor: `StoragePile` keeps a `base` field that is always `blocks.get(0).getBlockPos()`;
the other two do without it. Pick one convention in the shared base.

---

## Finding 2 — The three block entities repeat the same lifecycle machinery

Files:
- `common/.../block/StorageStackBE.java` (342)
- `common/.../block/SinglesStackBE.java` (543)
- `common/.../block/BarStackBE.java` (480)

Identical or near-identical across all three:

- the `suppressSync` / `batchTouched` / `publishPending` / `publishedSignal` fields and
  their contract
- the `cachedColumn`/`cachedPile` + `cachedColumnTick` one-tick cache, its accessor, and
  `invalidateColumn()` / `invalidatePile()`
- `exchangePublishedSignal(int)` — byte-identical
- `beginBatch()` / `endBatch()` — identical (the one wrinkle is `BarStackBE` deliberately
  not clearing `batchTouched`, with a comment explaining the nesting)
- `schedulePublish()` — identical shape
- `publishIfPending()` — identical shape; only the `LIGHT_LEVEL` property constant differs
- `isEmpty()` / `syncToClients()` — identical
- `getUpdatePacket()` / `getUpdateTag()` — identical
- the anonymous `StackItemStorage` subclass with the `onContentsChanged` →
  `suppressSync ? batchTouched : schedulePublish` body — identical between Bar and Singles,
  near-identical in Storage
- `loadAdditional` / `saveAdditional` for `TAG_ITEMS` — identical; Singles/Storage bolt on
  rotation (and Storage `permanent`, Singles `cubeRotations`)

An `AbstractStackBE` base could own all of the above, with subclasses adding shape caching
(Bar/Singles), layout rotation (Singles/Storage), per-item rotation (Singles), the
`permanent` flag (Storage), and their distinct extraction cascades. This pairs naturally
with the `StackRun` base in Finding 1 (`StackRun` would hold `List<AbstractStackBE>` and
call the shared `beginBatch`/`endBatch`/`publishIfPending`/`exchangePublishedSignal`
through the base type instead of three near-clones).

---

## Finding 3 — The three world-facing blocks repeat state, shapes, and hooks

Files: `StorageStackBlock.java` (175), `SinglesStackBlock.java` (208),
`BarStackBlock.java` (222).

`SinglesStackBlock` and `BarStackBlock` are ~95% identical: same `LIGHT_LEVEL` +
`WATERLOGGED` properties and `registerDefaultState`, same `createBlockStateDefinition`,
`getFluidState`, `updateShape`, `getRenderShape`, `getShape`/`getCollisionShape` delegating
to `be.getCachedShape()`, `getInteractionShape` (full cube), `getVisualShape` (empty),
`isPathfindable` (false), `useWithoutItem`/`useItemOn`, `hasAnalogOutputSignal`,
`getAnalogOutputSignal`, and the `tick` / `onPlace` / `onRemove` structure. `StorageStackBlock`
shares all the block-state/fluid/use/comparator members and differs only in keeping a
full-block shape and in its `onRemove` (drop own contents, mark neighbours) / `tick`
(settle at base).

Suggested consolidation: an `AbstractStackBlock` base carrying the properties, fluid
logic, use handlers, and comparator wiring, plus a shared `onPlace`/`onRemove` template
with a couple of protected hooks. `SinglesStackBlock` and `BarStackBlock` could then be a
few dozen lines each.

---

## Finding 4 — The three cube-geometry helpers duplicate the grid math

Files: `common/.../util/StorageCubeIdx.java` (102), `SinglesCubeIdx.java` (282),
`BarCubeIdx.java` (332).

- `rotateXYZ(int,int,int,int)` is **byte-for-byte identical** between `SinglesCubeIdx` and
  `StorageCubeIdx` (same 4-case switch, same `MAX_COORD = GRID_EDGE - 1`).
- `xyzFromIndex(int)` is the same layer/row/col decomposition in all three (only
  `GRID_EDGE` / `LAYER_SIZE` differ, and those are constants).
- The `private record Hit(int index, double distance)` plus the "clip every box, sort by
  distance" loop in `traceCubes` / `traceAllPositions` is duplicated between `BarCubeIdx`
  and `SinglesCubeIdx`, and `traceCubes` alone is a third near-copy in `StorageCubeIdx`.
- `occupancyOf(SlotAccess)` is identical between `BarCubeIdx` and `SinglesCubeIdx`.

A small `GridIdx` helper (grid edge as a field or ctor arg) could own `xyzFromIndex`,
`rotateXYZ`, `occupancyOf`, the `Hit` record, and a generic
`traceNearest(ray, pos, indexToBox)` / `traceForDeposit(...)`. Bar's alternating-layer bar
geometry and Storage's gapped 3×3×3 stay in their own classes as the `indexToBox`
supplier.

---

## Finding 5 — No "stack kind" strategy; instanceof chains are scattered

The same three-way dispatch on BE type recurs, each site re-deriving the per-kind trace
call, validity predicate, and sound:

- `network/DepositPkt.java` — `apply()` has the full `STORAGE / SINGLES / BAR` branch with
  per-branch `traceAllPositions` + `depositAt`/`deposit` + `playSound`
- `network/PlaceAndDepositPkt.java` — three `switch (msg.blockType)` blocks
  (`columnFull`, `depositIndex`, `finalCollision`, the place/deposit/sound switch) plus
  `firstDepositWouldSucceed`
- `network/ExtractPkt.java` — `handleServer` instanceof chain + three `handle*Extract`
  methods that differ only in the trace/limit rule and the sound
- `client/interaction/ExtractionRule.java` — instanceof chain to pick the `traceCubes`
- `client/interaction/DepositIntoClickedStackRule.java` — instanceof chain re-implementing
  "trace target cell, is it empty, is it grounded" for Singles and Bar
- `block/FabricRunItemStorage.java` — `liveStack`, `canInsert`, `isValid`, `commitInsert`,
  `commitExtract`, `getSlotCount` each carry their own `instanceof StorageStackBE / SinglesStackBE / else Bar`

`BlockType` (`util/BlockType.java`) already exists as the enum of the three kinds and
already maps kind → block and kind → enabled-flag. It is the natural home for (or seam to)
a `StackKind` strategy that also exposes: the BE class / a `resolveRun(level,pos)`, the
deposit-trace function, the extract-trace function, `isValidItem`, and the deposit/extract
`SoundEvent`. Most of the branch bodies above collapse to one call through that object.

`DepositPkt` and `PlaceAndDepositPkt` also share the "creative works from a copy, write the
hand back unless creative" dance (`returnToHand` / the inline `if (!creative) setItemInHand`)
and the `checkDisabledModAndNotify` + `checkDisabledItemAndNotify` pair — worth a shared
helper alongside the strategy.

---

## Finding 6 — Loader item-handler wrappers are triplicated per loader

- `forge/.../block/{BarColumnHandler,SinglesColumnHandler,PileItemHandler}.java`
- `neoforge/.../block/{BarColumnHandler,SinglesColumnHandler,PileItemHandler}.java`
- `fabric/.../block/FabricRunItemStorage.java` — **one** class for all three, dispatching
  on BE type

Within each of Forge and NeoForge the three handlers are the same class with the run type
swapped: `getSlots` → `run.advertisedSlots()`, `getStackInSlot` → `run.getSlot`,
`insertItem`/`extractItem` wrapped in `RunEdit.begin()/end()`, identical `localSlot`
fallback. Forge's `BarColumnHandler` and `SinglesColumnHandler` differ only in type names
and one javadoc paragraph.

Fabric already demonstrates that one wrapper with a `BlockType`/strategy switch is enough.
Forge and NeoForge could each drop to a single `RunItemHandler` taking the BE plus its
`StackKind`. (Per the repo's standing guidance not to rewrite the loader modules'
networking/caps/events wholesale, treat this as a low-priority, self-contained collapse —
it does not change the capability-registration model, only the number of wrapper classes.)

---

## Smaller notes

- `command/SsCommand.java` (767) — large but it is mostly a Brigadier tree; the
  `Selection` / `ItemSelection` records and their `select*` builders share a shape
  (resolve entries, split into disabled/unusable/kept, fail-or-return) that could be one
  generic helper, but the payoff is modest. Not urgent.
- `client/interaction/` — 12 files for the rule chain. This is on the "atomized" side, but
  each rule's `matches`/`execute` split is clean and the per-rule javadoc documents chain
  precedence, so it reads well. If trimming, the three near-trivial senders
  (`TogglePermanentRule`, `RotateBlockWithRedstoneTorchRule`, `PlaceAdjacentGenericRule`)
  are the only candidates to fold together. Low priority.
- Per-loader `client/ClientEvents.java` (Forge/NeoForge) share the entire
  `onRightClickBlock` body barring the `Event.Result` vs `TriState` enum. That divergence
  is a real API difference; leave it unless a common helper taking the built
  `InteractionContext` and returning a "cancel?" verdict proves clean.

## What not to consolidate

- Loader-specific event/registration/networking glue beyond Finding 6 — the API surfaces
  genuinely differ and the repo guidance is to keep those modules thin ports.
- The measurement/override rendering classes (`ItemRenderOverrides`,
  `AutoRenderProfiles`, `ModelMeasurement`, `CubeRenderHelper`) — each owns one coherent
  concern already.
- `StackItemStorage` / `SlotAccess` — already the single shared storage primitive.

## Priority order

1. Finding 5 (`StackKind` strategy) — highest leverage; unblocks cleanups in 5 files and
   is additive rather than a hierarchy rewrite.
2. Findings 1 + 2 (`StackRun` + `AbstractStackBE` bases) — biggest raw reduction; do them
   together since the run holds a `List` of the BE base.
3. Finding 3 (`AbstractStackBlock`).
4. Finding 4 (`GridIdx`).
5. Finding 6 (loader handler collapse) — small, isolated, low risk.
