# Code Review: Efficiency

Scope: time and space cost of the mod as it currently stands, server and client. Focus on
per-tick work, wide-area searches, and allocation in hot paths.

## Verdict

The server-side architecture is disciplined. There is no per-tick polling of the world, no
chunk-wide entity or block-entity scan, and no giant-AABB search: the one entity query
(`WorldEdits.isUnobstructed`) is a single-block box run only during automated growth. Pile and
column bounds are resolved by walking the world, but the result is cached per block entity per
tick and invalidated precisely on place/remove, so a machine walking a 200-slot handler resolves
the run once and then hits O(1) accessors. Sorting is not done every tick by design — it is
attached to a coalesced, change-driven scheduled tick.

The real costs are:

1. The settle/publish pass re-scans, re-consolidates, and re-sorts the **entire** pile every time
   it runs, and under sustained automation it runs every tick — this is the one place the
   "do we sort every tick" worry actually bites.
2. The three block-entity renderers allocate and hash per stored slot, per frame, without
   exploiting that a stack block almost always holds one or a few distinct item types.
3. The comparator output path recomputes a full-pile fill scan on every poll instead of returning
   the signal value the pile already maintains.

Details below, ranked by expected impact.

---

## 1. `settle()` reprocesses the whole pile on every scheduled tick

`StoragePile.settle()` (`common/.../block/StoragePile.java:500`) on each run:

- copies every non-empty slot of the whole pile into a new `ArrayList` (`:501-507`),
- `consolidate()` builds two `LinkedHashMap`s keyed by `StackKey`, re-cuts every total into
  stacks, appends to another `ArrayList`, and calls `out.sort(StackSort.COMPARATOR)` (`:575-604`),
- rewrites every slot of every block (guarded by `setSlotIfChanged`), then walks the column again
  for `publishIfPending` and `publishComparatorSignal`.

`markDirty()` (`:481`) schedules a tick at now+1 only if one is not already scheduled. That
prevents double-scheduling *within* a tick; it does not debounce across ticks. A pile that is fed
by a hopper or pipe every tick therefore gets a full settle every tick: for a full 8-high Storage
pile that is a 216-slot scan plus two hashmaps plus a `Collections.sort` of up to 216 elements,
every tick, per actively-fed pile. A storage room with many such piles turns this into steady
main-thread cost. `BarColumn`/`SinglesColumn` publish passes are lighter (no consolidate/sort) but
have the same "runs every tick under load" property.

Compounding it, `StackSort.COMPARATOR` (`common/.../util/StackSort.java:18`) calls
`BuiltInRegistries.ITEM.getKey(...)` on **both** operands on every comparison, and for
component-bearing stacks calls `DataComponentPatch.toString()` on both and compares the strings.
That is a registry map lookup (and possibly NBT-ish string building) inside an O(n log n) sort,
redone from scratch each settle.

Directions to consider (no change made):

- Track whether the pile's contents actually changed since the last settle (a dirty flag or a
  content generation counter bumped from `onContentsChanged`), and make `settle()` return early
  when nothing changed — a pile being *read* every tick, or fed items it is already full of,
  then costs nothing.
- Alternatively debounce: schedule the settle a few ticks out and coalesce a burst into one pass.
- In `consolidate`, decorate-sort: resolve each stack's sort key once into a small record and sort
  that, so the registry lookup and any patch stringification happen O(n), not O(n log n).

## 2. Block-entity renderers allocate and hash per slot, per frame

`StorageStackBER.render` (`common/.../client/StorageStackBER.java:39`), `SinglesStackBER.render`
(`:40`), and `BarStackBER.render` (`common/.../client/BarStackBER.java:44`) each loop all 27/64
slots for every stack block in view, every frame. Per occupied slot they call index helpers that
each allocate a fresh `int[3]`:

- `StorageCubeIdx.xyzFromIndex` (`:95`) and `rotateXYZ` (`:79`) — two arrays per slot per frame;
  `SinglesCubeIdx` is analogous plus a per-item rotation; `BarCubeIdx.xyzFromIndex`
  (`common/.../util/BarCubeIdx.java:253`) — one array per bar per frame.

The domain is tiny and fixed: index 0..63 and rotation 0..3. These are pure functions that could
be precomputed into static lookup tables once, removing the per-frame garbage entirely. The
`CubeRenderHelper.DIRECTIONS` field (`:57`, with its comment about `Direction.values()` cloning)
shows the pattern the codebase already uses elsewhere.

Per-slot lookups that ignore item repetition:

- `CubeRenderHelper.renderItemInCube` → `ItemRenderOverrides.resolve`
  (`common/.../client/ItemRenderOverrides.java:143`): one `BuiltInRegistries.ITEM.getKey` plus up
  to three `HashMap.get` per slot per frame, and `new RenderProfile(...)` allocated on the
  override path (`:164`). `AutoRenderProfiles.get` is already an `Item`-keyed cache; the layered
  `resolve` result could be cached the same way and invalidated on override/resource reload.
- `BarStackBER` per bar: `BarTextureStore.getTexture` (`:311`) does a registry key + map lookup,
  then `Minecraft.getInstance().getTextureAtlas(BLOCK_ATLAS).apply(texture)` (`:49-51`) resolves
  the sprite again — up to 64 times for a block that usually holds one ingot type. Resolving once
  per distinct item encountered in the render loop (or caching the `TextureAtlasSprite` on
  `BarTextureData` at reload) removes almost all of it.

Note: the 2D projection path in `CubeRenderHelper` itself is already well optimized (plate cache,
visible-face culling, no per-vertex vector allocation) and is deliberately left alone.

## 3. Comparator output recomputes a full scan instead of using the maintained value

`StorageStackBlock.getAnalogOutputSignal` (`common/.../block/StorageStackBlock.java:110`) →
`StoragePile.comparatorSignal()` (`:231`) → `fillLevel()` (`:263`) walks every slot of the pile
(`getSlot(i)`, with `totalSlots()` re-evaluated in the loop condition and again in each
`getSlot` bounds check) summing count fractions. `BarColumn.fillLevel` (`:209`) and
`SinglesColumn.fillLevel` (`:200`) do the same over up to 512 slots.

This is only invoked on comparator update events, not every tick, so impact is modest — but the
pile/column already maintains the last published signal (`publishedSignal` /
`exchangePublishedSignal`, set during `publishComparatorSignal`). The block path could return that
cached value (recomputing only when it has never been published) so the value has a single source
of truth and comparator reads are O(1). At minimum, hoist `totalSlots()` to a local in the three
`fillLevel` loops.

## 4. Minor

- `ClientGestures.cycleMode` (`common/.../client/ClientGestures.java:38`) calls
  `StackMode.values()` inside the loop; `values()` clones the array each call. Cache it in a
  static field (same reasoning as `CubeRenderHelper.DIRECTIONS`). Keypress-frequency only.
- `BarCubeIdx.traceAllPositions` (`:197`) allocates an `ArrayList<Hit>`, autoboxes a `double`
  distance per hit, and does a full `Comparator.comparingDouble` sort, when the result is just
  "last empty slot before the first occupied one." `getBarBox` also allocates an `AABB` per slot.
  `traceCubes` (`:227`) and `StorageCubeIdx.traceCubes` (`:40`) have the same per-slot `AABB`
  allocation. All interaction-frequency (right-click / attack), so low priority, but the list +
  boxing + sort in `traceAllPositions` is avoidable with a single nearest-scan.
- `BarCubeIdx.shapeFor` (`:169`) and `SinglesCubeIdx.shapeFor` build a fresh `VoxelShape` each
  call. Fine inside the cached `computeShape`; also called from `positionAccepts`/`canGrow` on the
  automation growth path. Bar shapes are rotation-free and could be a static 64-entry table;
  Singles would need 64×4. Low value given growth frequency.
- `ServerConfig.isModDisabled` (`:417`) does `namespace.toLowerCase(Locale.ROOT)` per call, and
  `ItemOps.isItemFromDisabledMod` does a registry key lookup per call. These sit on the deposit
  validity path (`isValidBarItem` / `isValidStorageItem`), which is per deposit call, not per
  slot, so acceptable — worth knowing if that ever moves into a per-slot loop.

## 5. Space — reviewed, largely inherent

- Every `StackItemStorage` holds a full `ItemStack[SLOTS]` (27 or 64 refs) and
  `SinglesStackBE.cubeRotations` is an always-allocated `int[64]`. This is inherent to
  position-is-identity semantics (a slot index is a place, not a bag entry). The arrays are
  filled with the shared `ItemStack.EMPTY` singleton, so the cost is N references plus one int
  array per Singles block — modest, and not worth trading the model for.
- `getUpdateTag`/`saveAdditional` re-serialize a block's entire item array on every block sync.
  `StoragePile.setSlotIfChanged` keeps unchanged blocks from syncing at all during a settle, so
  only genuinely-changed blocks pay this. Acceptable.
- `StoragePile`/`BarColumn`/`SinglesColumn` instances are short-lived (one per resolve, cached for
  one tick) and small (a `List` of block-entity references). No concern.
