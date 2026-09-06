# Targeted code review: dead code and duplication

Scope: the mod's own sources under `common/`, `forge/`, `fabric/`, `neoforge/`
(`src/main`, with `src/gametest` consulted only to confirm whether something is
reachable). `other/` (bundled FTB Chunks API) was ignored. No git history was
consulted.

Bottom line: there is very little truly dead code. There is a large amount of
structural duplication among the three stack types (Storage / Singles / Bar),
concentrated in three parallel class families. The Bar and Singles variants in
particular are near-identical twins.

---

## 1. Duplication (the substantive finding)

### 1.1 The three "run / pile" classes

`common/.../block/BarColumn.java` (509 lines), `SinglesColumn.java` (442),
`StoragePile.java` (605).

`BarColumn` and `SinglesColumn` are near-verbatim copies of each other. The
following methods are identical modulo the substitutions
`BarStackBE`↔`SinglesStackBE`, `BarCubeIdx`↔`SinglesCubeIdx`,
`BAR_STACK_BLOCK`↔`SINGLES_STACK_BLOCK`, `enableBarStackBlock`↔
`enableSinglesStackBlock`:

`at`, `resolve`, `invalidateAround`, `invalidateRun`, `publishAround`,
`publishAt`, `markDirtyAt`, `maxHeight`, `columnHasRoomFor`, `runLength`,
`height`, `totalSlots`, `advertisedSlots`, `getSlot`, `handlerOf`, `fillLevel`,
`comparatorSignal`, `publishComparatorSignal`, `markDirty`, `publishPending`,
`insertOneAt`, `canGrow`, `grow`, `topPos`.

That is roughly 300 of ~450 lines shared byte-for-byte (including doc comments)
between the two. The genuinely type-specific parts are small: `positionAccepts`
vs `cellAccepts` and `seamUnder` (Bar has no rotation; Singles does), and
`extract` (Bar does topmost-bar backfill + `trimEmptyTop`; Singles delegates the
column shift to `SinglesStackBE.extractAt`). Bar additionally has
`topmostOccupied`, `blockOf`.

`StoragePile` shares the same skeleton for another ~120 lines: `at`, `resolve`,
`invalidateAround`, `invalidateRun`, `markDirtyAt`, `maxHeight`,
`columnHasRoomFor`, `runLength`, `height`, `totalSlots`, `advertisedSlots`,
`getSlot`, `handlerOf`, `comparatorSignal`, `publishComparatorSignal`,
`markDirty`, `canGrow`, `grow`, `topPos`, `trimEmptyTop` are all the same shape.
Storage diverges more (bag semantics, `settle`/`consolidate`, `deposit`,
`permanent` flag).

None of the three shares a base class or helper; each holds its own
`Level level` + `List<XxxStackBE> blocks` and reimplements the walk. A single
`abstract StackRun<BE>` (or a shared `StackRunSupport` holding `level`+`blocks`
plus the comparator/publish/dirty/grow/resolve machinery) would remove the bulk
and is the idiomatic Minecraft-modding shape for "N block variants, one
behavior."

### 1.2 The three block-entity classes

`common/.../block/BarStackBE.java` (480), `SinglesStackBE.java` (543),
`StorageStackBE.java` (342). All three extend `BlockEntity` directly with no
shared base. Repeated in all three with only type substitution:

- the anonymous `StackItemStorage(SLOTS){ onContentsChanged / getSlotLimit /
  isItemValid }` field
- `column()` / `pile()` per-tick cache + `invalidateColumn()` /
  `invalidatePile()`
- `exchangePublishedSignal`, `isEmpty`, `syncToClients`, `beginBatch` /
  `endBatch`, `schedulePublish`, `publishIfPending`, `getItems`
- `getUpdatePacket`, `getUpdateTag`, `loadAdditional` / `saveAdditional` bodies
- `computeShape` / `getCachedShape` (Bar and Singles only)

### 1.3 The block classes

`BarStackBlock.java` (222) and `SinglesStackBlock.java` (208) are ~90% identical
— a full `diff` is dominated by type-name substitution. The only real behavioral
difference is Bar's cascade-collapse handling inside `onRemove` (`BarDropBatch`,
`wasRemovedByCascade`, `collapseAbove`); Singles just calls
`ItemOps.dropAllItems`. `StorageStackBlock.java` (175) shares the state-def,
`getFluidState`, `getRenderShape`, `newBlockEntity`, `tick`, comparator, and
`onPlace`/`onRemove` skeleton but diverges on shapes and light overrides.

### 1.4 `SinglesCubeIdx` vs `StorageCubeIdx` geometry helpers

`rotateXYZ(int,int,int,int)`, `xyzFromIndex(int)`, and `startPixel(int)` are
duplicated near-verbatim between `common/.../util/SinglesCubeIdx.java` and
`common/.../util/StorageCubeIdx.java`; they differ only in the `MAX_COORD` /
`GRID_EDGE` / `STARTS` constants and cell size. The rotation switch body is
character-for-character the same in both (and the doc comment above it too).

### 1.5 `EMPTY_HAND_RULES` and `ITEM_RULES` are identical

`common/.../client/interaction/InteractionRuleRegistry.java` lines 17-23 define
two separate lists that are both exactly `List.of(new ModeCycleRule())`, fed by
two separate entry points (`processEmptyHandRules`, `processItemRules`). They are
called from different event sites but do the same thing; one list/method would
serve. (See also 4.1 — the class javadoc no longer matches these lists.)

### 1.6 Loader pairs (lower priority — inherent to multi-loader)

Most Forge/NeoForge file pairs differ only by API namespace
(`net.minecraftforge.*` vs `net.neoforged.*`) and are expected duplication. Two
are thinner than they need to be, though:

- `forge/.../server/ForgePlayerEditAuthority.java` and
  `neoforge/.../server/NeoForgePlayerEditAuthority.java` are pure 1:1 delegation
  wrappers around their respective static `Protection` class (differ only in
  whitespace, class name, one javadoc line). On Fabric the same interface is
  implemented directly with the real logic (`FabricPlayerEditAuthority`). The
  Forge/NeoForge `Protection` classes could implement `PlayerEditAuthority`
  directly and drop the wrapper.
- `forge/.../block/{BarColumnHandler,SinglesColumnHandler,PileItemHandler}` carry
  long class-level javadoc that the NeoForge copies trimmed to a sentence; the
  two sets are otherwise the same logic. Not a bug, just noting the doc drift.

---

## 2. Dead code

### 2.1 `BarColumn.height()` and `SinglesColumn.height()` — unused

`public int height()` in `common/.../block/BarColumn.java:167` and
`common/.../block/SinglesColumn.java:157` have no callers anywhere in `main` or
`gametest`. (`StoragePile.height()` *is* used — from `StoragePileChecks` and the
`ProtectionGameTests`.) Safe to delete the two column copies.

### 2.2 `InteractionContext.getHand()` — unused

`common/.../client/interaction/InteractionContext.java:165`. The `hand` field is
used (via `isMainHand()`), but the public `getHand()` getter has no callers
(`ctx.getHand()` / `context.getHand()` appear nowhere; the `evt.getHand()` hits
are on the platform events). Safe to delete.

---

## 3. Over-exposed / structurally inconsistent (not dead, but adjacent)

- `BarColumn.topmostOccupied()` (`BarColumn.java:305`) is `public` but its only
  caller is `BarColumn.extract` in the same file. Should be `private`.
- `publishComparatorSignal()` is package-private in `BarColumn` / `SinglesColumn`
  but `private` in `StoragePile`; `markDirty()` is package-private in the columns
  and `public` in `StoragePile`. Same method, three different visibilities — a
  symptom of the copy-paste in section 1.1.
- `ClientRenderPacketSink.Handler` (`common/.../client/ClientRenderPacketSink.java`)
  is a three-method interface with exactly one implementation,
  `DefaultClientRenderPacketHandler`, installed identically in all three loaders
  (`ClientRenderPacketSink.setHandler(new DefaultClientRenderPacketHandler())`).
  The `handler` field / `setHandler` plumbing and the "retain until the loader's
  rendering layer is installed" buffering of `serverOverrides` are speculative:
  `setHandler` always runs during client setup before any packet arrives. The
  sink could call the override layer directly. Judgment call — keep if it's
  wanted as a client/dedicated-server seam, but today it is indirection with one
  target.

---

## 4. Minor / stale comments

### 4.1 `InteractionRuleRegistry` class javadoc is stale

`common/.../client/interaction/InteractionRuleRegistry.java:5-13` describes "the
item gestures from the most specific to the least: the torch gestures precede
deposit, deposit into the clicked stack precedes deposit into its neighbor…" —
that ordering is `BLOCK_RULES`, not the `ITEM_RULES` list the paragraph reads as
being about. After 1.5 the doc should describe the block list and the (single)
air-click rule.

### 4.2 `AutoRenderProfiles.compute` orphaned comment

`common/.../client/measure/AutoRenderProfiles.java:220-222` — the comment
"Blocks whose art lies in the horizontal plane read as a one-pixel edge when
projected flat, so they are presented the way an inventory slot shows them" sits
directly above `float scaleFactor = fitScaleFactor(stack);`, which is about
button sizing. The comment belongs above the `wantsGuiPresentation` branch a few
lines down.

### 4.3 `BarCubeIdx.barHeight(int y)` ignores its parameter

`common/.../util/BarCubeIdx.java:270` — `public static double barHeight(int y)`
always returns `BAR_HEIGHT`. Not dead (called from `BarStackBER` and internally)
and the signature parallels `barWidth(y)` / `barDepth(y)` which do use `y`, so
this is arguably deliberate symmetry; flagging only because a reader will wonder.

---

## What is NOT a problem

- The `client/measure` package (`AutoRenderProfiles`, `ModelMeasurement`,
  `ModelMeasurer`, `RenderGalleryGenerator`) looks like dev tooling but is live:
  `AutoRenderProfiles.get` feeds `ItemRenderOverrides.resolve`, which every BER
  uses each frame.
- `RenderMode.GUI` / `CubeRenderHelper.renderGuiItem` are reachable via config
  and `ss` commands, not dead.
- `ProtocolPkt` is Fabric-only by design (Forge/NeoForge get the handshake from
  the loader); not dead.
- The `util` helpers I spot-checked (`ItemOps`, `ViewRays`, `StackPlacement`,
  `PlayerReach`, `StackSort`, `StackMode`, `BlockType`) are all referenced.
