# Some Stacks — review findings, fix plan, and progress

Handoff document. Purpose: let a new conversation continue the remediation work without
re-deriving context. Read this together with the companion files in the repo root:

- `living-spec.md` — architecture / behavior spec (source of truth for intended behavior).
- `code-review-merged.md` — the merged upstream review this work responded to.
- `claude-code-review.md` — the independent, line-verified review; **full detail for every
  finding below lives there**. This document condenses it and adds a staged plan + status.

Reviewed against `main` @ f8c5f71. **Stages 1, 2, and 3 are done and committed** (Stage 1
`08070ce`, Stage 2 `9f784cb`, Stage 3 `0ea58c8`); **Stage 4 is done and uncommitted**. Because of
those edits, line numbers below have drifted; treat every line reference as a starting
point, not an exact anchor, and re-locate against current code. The per-stage "COMPLETE" sections
lower down record exactly what changed. Stages 5–7 are not started.

## Project constraints (from CLAUDE.md — obey these)

- Unreleased MC 1.20.1 / Forge 47.4.0 mod, Java 17, Parchment. No legacy/back-compat: when a
  data format changes, migrate all stored data at change time (deterministic tools preferred).
- Do things "the Forge/Minecraft way." An expert modder should recognize the idioms.
- Do NOT build/compile/run (no `gradlew`, no game runs) unless the user explicitly asks. Verify
  by reading and reasoning. The user runs builds and tests.
- Do NOT touch git or GitHub unless explicitly asked. Commit messages must be succinct and
  interpretable without the conversation.
- Comments describe current code only — no history, no "changed from…", no future hypotheticals.

## Trust model (the spine of all findings)

The code is **under-guarded at the remote/untrusted boundary and over-guarded after it**.
Validate once at the boundary (C2S packets, config files, resource files, third-party
renderers), then treat this mod's own block entities/capabilities as invariants and fail loudly
on internal violations. Adopt this framing everywhere.

---

## Findings (condensed — full writeups in `claude-code-review.md`)

### Critical
1. **Storage deposit never rejects invalid items → empty towers + item deletion + sim/exec
   mismatch.** `StorageStackBE.deposit` didn't validate at entry, so a disabled-mod item's
   untouched remainder was treated as overflow and built empty blocks to build height;
   `mergeIntoHandler` counted predicted (not actual) moves and could delete items;
   `PileItemHandler` simulate disagreed with execute. **FIXED in Stage 1.**
2. **`ExtractPkt` crashes the server for Storage slot indices 27–63.** Common validation
   allowed 0–63; the Storage 27-slot handler throws for 27–63 on the server thread. One packet
   from a modified client = crash. **FIXED in Stage 1.**
3. **C2S mutations trust client intent.** No packet directions registered; missing reach,
   loaded-position (ExtractPkt reads BE before an isLoaded check → forced chunk load), gesture
   prerequisites (sneak/torch/empty-hand), and Forge placement/protection hooks. `setBlock`
   bypasses `EntityPlaceEvent`; extraction mutates before any veto point and the suppressor
   cancels the follow-up event. **Stage 2.**

### High
4. **Singles/Bar omit `.dynamicShape()`** yet derive collision from BE contents → cached stale
   shapes. Add the flag; keep the per-BE shape caches. **Stage 3.**
5. **Pile cleanup splits a contiguous pile.** Repack removes empty blocks from the top of the
   *processed window* (default 3) even when occupied blocks sit above; player extract removes a
   locally-empty block without checking for a Storage block above. Rule: never remove an empty
   Storage block when a Storage block sits directly above it. **Stage 3.**
6. **Repack leaves light + comparator stale.** `suppressSync` also bypasses light recalc and
   neighbor updates; finalize each repacked block once (light, `updateNeighborsAt`, dirty,
   update). **Stage 3.**
7. **Singles cascade syncs mid-mutation and mismanages rotations.** Per-slot sync during the
   cascade; rotations moved after with no final sync; emptied cells keep old rotations (next
   deposit inherits orientation). Batch + finalize once; clear rotations on emptied cells.
   **Stage 4.**
8. **Sounds: logical server consumes client-owned resource data.** `SoundConfig` is a
   client-only reload listener but writes static `ModSounds` fields the server reads → dedicated
   servers play Java defaults. Also a fully dead second sound system (`ModSounds.SOUND_EVENTS`
   `DeferredRegister` never registered; orphan `bar_extract.ogg`). Pick one owner; delete the
   other. **Stage 5.**
9. **Config-reload sync is on the wrong bus.** `ModConfigEvent.Reloading` is a mod-bus event but
   `onConfigReload` is on `MinecraftForge.EVENT_BUS` → never fires. `sendConfigSync` reparses the
   override dir per-player. No operator reload command. Also `MinecraftForge.EVENT_BUS.register(this)`
   registers zero handlers. **Stage 5.**

### Medium
10. **Cross-block support depends on client-supplied face.** `PlaceAndDepositPkt` checks the
    lower block only when `msg.face == UP`. Also (found here, not in merged review) the Singles
    pre-check traces with the lower block's rotation while the real deposit traces with rotation
    0 → tests a different cell when rotated. Bar re-implements footprint overlap inline instead
    of using `BarCubeIdx`. **Stage 6.**
11. **`ss` permissions + disabled-mod case mismatch.** Whole `ss` tree is creative-only (no
    operator/console); runtime uses `equalsIgnoreCase` while `ss test` uses case-sensitive
    `contains`. With finding 1, a wrong-case config entry made every deposit build empty towers.
    Split permissions; normalize namespaces at load. **Stage 6.**
12. **Event-driven bursts undercut the no-ticker design.** Storage overflow recursion re-walks to
    base each frame (O(depth²)); Singles/Bar cascades publish per cell; Bar `do/while` rescan is
    unnecessary (index order = layer order); `ss test all` ~30k deposits in one tick. Batch /
    schedule. **Stage 4 (cascade) + Stage 7 (test gen).**
13. **Rotating Storage triggers a pile repack** (`RotateBlockPkt:52`) — rotation is visual-only;
    delete the call. **Stage 6.**
14. **2D renderer ignores Forge render passes.** Measurement iterates `getRenderPasses`; the draw
    path reads only the root model → multi-pass items render with missing layers. **Stage 7.**
15. **Odd Bar layers apply side/end UV regions backwards** (`BarStackBER.emitBar`) — rotated
    branch assigns the same values, so it's a near no-op and stretches side art over ends. ARGB
    alpha < 255 emitted into `RenderType.solid()` won't blend. **Stage 7.**
16. **Consolidation unreliable + redundant sort.** `StackSort` orders by tag *presence* then
    count; `consolidate` merges only into the previous output; input is sorted twice. Use an
    exact item/damage/tag key map. **Stage 4 or 7.**
17. **Capability lifecycle + internal round-trips.** BEs invalidate in `setRemoved` instead of
    `invalidateCaps`/`reviveCaps` (leaks across chunk unload); internal code fetches the optional
    capability where it already holds the concrete BE. Give BEs direct accessors. **Stage 7.**
18. **Metadata over-advertises.** MC `[1.20.1,1.21)`, Forge `[47,)`, floating plugin versions,
    unused template scaffolding. Pin them. **Stage 7.**

### Low / cleanup (Stage 7)
- `ModelMeasurer` reads `isGui3d` before model substitution; hard-coded `true` fabulous flag.
- `OverrideJsonCodec` accepts non-finite scale/offset (`NaN` evades `scale <= 0`).
- Blacklist ids reparsed and swallowed on every deposit (`ItemOps.isItemDisabled`).
- Mode cycler favors Storage regardless of its synced enable flag.
- `BarTextureStore` logs thousands of INFO lines per reload.
- `RightClickBlockSuppressor` stores a same-tick marker in persistent player NBT.
- Dead code: unregistered `PlaceAdjacentToStackRule` (forbidden Shift+V gesture);
  `InteractionRule.getName`; `InteractionContext.isSinglesOrBarStackAbove`/`getAbovePos`;
  `BarCubeIdx.indexFromLocal` + axis helpers; `BlockType.getDepositSound`/`getExtractSound`/
  `toStackMode`; empty `ClientEvents.init`; three `models/item/*.json` (storage one parents a
  nonexistent model). Bar torch rules consume no-op gestures.

---

## Seven-stage fix order

1. **Storage deposit rejection, moved-count accounting, capability sim/exec coherence; ExtractPkt
   crash.** (Findings 1, 2.) — **DONE (see "Stage 1 — COMPLETE").**
2. **C2S boundary:** explicit packet directions, isLoaded-before-BE-access, reach, gesture
   prerequisites, Forge placement/protection hooks. (Finding 3.) — **DONE (see "Stage 2 —
   COMPLETE"). Protection consult = E1 (spawn protection + world border); E2 is the noted
   upgrade path.**
3. **`.dynamicShape()`; prevent limited-window pile splitting; Storage batch finalization
   (light/comparator).** (Findings 4, 5, 6.) — **DONE (see "Stage 3 — COMPLETE").**
4. **Singles cascade rotation/state batching; Bar collapse single pass.** (Findings 7, 12, 16.)
   — **DONE (see "Stage 4 — COMPLETE").**
5. **Dedicated-server sound ownership + delete dead sound system; config-reload bus + operator
   reload command.** (Findings 8, 9.)
6. **Face-independent cross-block support (+ Singles rotation bug); `ss` permissions + case
   normalization; remove rotation-triggered repack.** (Findings 10, 11, 13.)
7. **Rendering (14, 15), consolidation (16), capability lifecycle (17), metadata (18), logging,
   and dead-code cleanup.**

---

## Stage 1 — COMPLETE and confirmed working in-game

Goal: fix findings 1 and 2 with minimal, independently reviewable steps. All changes verified by
reasoning; the pipe auto-expand behavior was verified in-world by the user.

### Files changed

**`network/ExtractPkt.java` — `handleStorageExtract`**
Removed the capability pre-read and the `canTakeIntoHand` pre-check. Now computes
`maxCanTake` (`Integer.MAX_VALUE` for an empty hand, else free hand space) and dispatches to
`sbe.extractAt(...)`, which already bounds-checks against the real 27-slot count and validates
contents + hand compatibility. Fixes the 27–63 out-of-range server crash. Singles/Bar branches
were left untouched (their handlers are 64 slots, so the same indices are in range).

**`block/StorageStackBE.java`**
- `deposit`: added an entry guard `if (!isValidStorageItem(fromHand)) return 0;` — closes both
  the empty-tower path and the item-deletion path (returns before overflow/merge).
- `mergeIntoHandler`: partial-fill loop now derives `moved` from the actual `insertItem`
  remainder, not the predicted `can`. Removed the dead `sim` stack and the no-op
  `extractItem(i, 0, true)`.
- Added `simulateDeposit(ItemStack)` + private `simulateAbsorb` + `fillLocally`: a read-only twin
  of `deposit` that models the same local fill (partials → empties) and the same upward overflow
  (into an existing Storage block above, or a would-be-created block when the space is
  replaceable, in-bounds, and creation is enabled). Co-located with `deposit` so the two can't
  drift.
- Added `canOverflowUpward()`: true when a Storage block sits above, or the space above is
  replaceable + within build height + `ENABLE_STORAGE_STACK_BLOCK`. Used by the capability to
  decide whether to advertise overflow headroom.

**`block/PileItemHandler.java`**
- `isItemValid`: delegates to `StorageStackBE.isValidStorageItem` (was `return true`).
- `insertItem`: empty guard; `simulate` branch returns the remainder from `simulateDeposit`;
  removed the unused `deposited` local.
- Virtual overflow slots (see below): `getSlots()` returns `LOCAL_SLOTS (27)`, or
  `LOCAL_SLOTS + OVERFLOW_SLOTS (54)` while `canOverflowUpward()`. `getStackInSlot` and
  `extractItem` guard `slot >= LOCAL_SLOTS` → treat virtual slots as empty / non-extractable.

### Design decision: capability simulate coherence (Option A/B) — chosen: model pile-wide

Simulate now models the real pile-wide deposit (`simulateDeposit`), not just the local block.
This makes simulate and execute agree, per the spec's pile-wide capability intent.

### The pipe auto-expand thread — important lesson for future capability work

Symptom: a Mekanism pipe filled the first block then stopped; hand deposits auto-expanded.

**Root cause (verified):** Mekanism — and AE2, and most cable/storage-bus mods — do **not** call
`insertItem(…, simulate=true)`. They read the handler's slots directly
(`getSlots`/`getStackInSlot`/`getSlotLimit`/`isItemValid`) and replay insertion themselves so
they can track items already in flight. A fixed 27-slot handler that's full advertises zero
capacity, so they stop before ever reaching the `insertItem` overflow. Fixing `simulate`
(Option B) only helps automation that actually calls it (vanilla hoppers/droppers,
`ItemHandlerHelper`).

**Working solution (Option 1, confirmed in-game):** while the pile can grow, advertise an extra
block's worth of **always-empty virtual overflow slots** (27..53) so capacity-checking
automation sees headroom. Inserting into any slot still runs `deposit`, so the advertised room
becomes a real block. Safe because internal consumers don't use the advertised slot count: the
renderer (`StorageStackBER`) and ray-tracer (`StorageCubeIdx.traceCubes`) hardcode
`for (i = 0; i < 27; …)`, and comparator fill / light / `isEmpty` read the raw 27-slot handler
directly.

**Known soft edge:** a full pile capped by a solid block several blocks up still advertises room
from the bottom block (its immediate neighbor above is a Storage block, so `canOverflowUpward` is
true); those items bounce back through the pipe — no loss, just wasted traffic. Making it exact
would require walking the whole column on every capacity check; not worth it unless it bites.
`OVERFLOW_SLOTS` (currently 27) is a single tunable constant if throughput is too bursty/tricky.

### Suggested commit message for Stage 1 (code only — not the review docs)

```
Fix Storage deposit/extract safety and auto-expand piles for automation

- Reject invalid items at deposit entry so rejected inserts no longer
  create empty overflow blocks or delete the source stack
- Dispatch Storage extraction through extractAt, fixing an out-of-range
  crash on client-supplied slot indices 27-63
- Make PileItemHandler.isItemValid and simulate model the real pile-wide
  deposit, including upward overflow, so simulate and execute agree
- Advertise a block of virtual overflow slots while the pile can grow, so
  capacity-checking automation (pipes, storage buses) expands the pile
  the way hand deposits do
- Fix mergeIntoHandler moved-count accounting and drop dead simulation code
```

---

## Stage 2 — COMPLETE (verified by reading; not yet built/run)

Goal: fix finding 3 — move gesture/reach/loaded/protection enforcement from the client-only
interaction rules onto the server. Protection option **E1** (server-authoritative consults:
spawn protection + world border) was chosen over E2 (re-posting `RightClickBlock` for full
claim-mod parity); E2 is the noted upgrade path if third-party claim-mod veto on deposit/extract
is ever needed.

### Files changed

**`network/ModNetworking.java`** — every `registerMessage` now passes an explicit
`Optional<NetworkDirection>`. C2S (`PLAY_TO_SERVER`): PlaceAndDeposit, Deposit, TogglePermanent,
RotateBlock, RotateItem, Extract. S2C (`PLAY_TO_CLIENT`): ConfigSync, RenderOverride,
WriteOverrides. Forge now rejects wrong-direction packets before any handler runs. (Direction
verified from send sites: the three S2C packets are sent via `PacketDistributor.PLAYER`;
WriteOverrides is server→client despite the name — it asks the client to write its override file.)

**`network/PacketBoundary.java`** (new, package-private) — the shared boundary:
- `validate(ctx, pos)`: sender non-null → `serverLevel().isLoaded(pos)` → `withinReach`. Returns
  the `ServerPlayer` or null.
- `withinReach`: `Vec3.atCenterOf(pos)` vs eye position, bound = `sp.getBlockReach() + 1.0`
  (the +1 mirrors vanilla's server-side interaction slack; uses the Forge reach attribute so
  creative/modified reach is respected).
- `holdsInMainHand(item)`, `mainHandEmpty()`: server-verifiable gesture prerequisites.
- `isProtected(sp, pos)`: world border + `MinecraftServer.isUnderSpawnProtection` (which already
  exempts operators and non-overworld dims). True = block the edit.
- `placeBlockChecked(sp, pos, state, face)`: `BlockSnapshot.create` → `setBlock` → fire
  `ForgeEventFactory.onBlockPlace`; on veto, `snapshot.restore(true, false)` and return false.
  Standard `BlockItem#place` pattern, so claim/protection/logging mods can veto or record.

**All six C2S handlers** now open with `PacketBoundary.validate(...)`, replacing the ad-hoc
sender/isLoaded checks. This fixes the `ExtractPkt` missing-`isLoaded` (finding 3b, forced chunk
load) and adds a uniform reach check (3c) everywhere.

**Protection consults (E1):** `isProtected` gates the three world-content edits before any
mutation — `DepositPkt`, `ExtractPkt` (before dispatch, hence before the suppressor call), and
`PlaceAndDepositPkt` (in addition to `onBlockPlace` via `placeBlockChecked`). Rotation and
toggle-permanent are visual/metadata only and are **not** protection-gated (reach already blocks
remote use) — a deliberate scope choice.

**Held-item prerequisites (3d):** `RotateBlockPkt` requires a redstone torch in main hand,
`RotateItemPkt` a soul torch, `TogglePermanentPkt` an empty main hand — mirroring the client
rules. Key state (V/sneak) is not transmittable and is intentionally not faked; deposit/extract's
server-verifiable parts (non-empty hand / hand compatibility in `extractAt`) were already present.

### Explicitly deferred (not Stage 2)
- `PlaceAndDepositPkt` `msg.face == UP` cross-block support + Singles rotation bug → finding 10 /
  Stage 6. Left untouched.
- `RightClickBlockSuppressor` NBT-in-player-persistent-data → Stage 7 low. Stage 2 only relies on
  its *ordering* (protection now runs before mutation); no storage change made.

### Suggested commit message for Stage 2 (code only)

```
Harden C2S packet boundary

- Register explicit NetworkDirection on every packet so wrong-direction
  packets are rejected before handlers run
- Add PacketBoundary: shared sender/loaded/reach validation, held-item
  gesture prerequisites, and spawn-protection/world-border consults
- Route all six mutation packets through it, fixing ExtractPkt's missing
  isLoaded check (forced chunk load) and adding uniform reach checks
- Fire EntityPlaceEvent on stack placement so protection mods can veto
- Require the gesture's held item server-side for rotate/toggle packets
```

---

## Stage 3 — COMPLETE (verified by reading; not yet built/run)

Goal: fix findings 4, 5, 6 — stale collision shapes, pile-severing block removal, and stale
light/comparator state after repack.

### Files changed

**`ModRegistry.java`** (finding 4) — added `.dynamicShape()` to the Singles and Bar block
`Properties`. Without it, `BlockStateBase.initCache` bakes a per-blockstate collision shape once
against `EmptyBlockGetter` (no BE → `getShape` returns `Shapes.empty()`), and since contents
change only the `LIGHT_LEVEL` property the cache never rebuilds. With the flag, vanilla always
calls `getShape` live and hits the per-BE lazy `cachedShape` (kept as-is — nulled on content
change, recomputed on demand). Storage is untouched (it keeps the vanilla full-block shape).

**`block/StorageStackBE.java`** (findings 5, 6):
- New private `finalizeAfterBatch()`: pushes contents to clients, `updateNeighborsAt` (comparator
  refresh), and recomputes `LIGHT_LEVEL`, setting it only when it changed. `onContentsChanged`'s
  non-suppressed path now delegates to it, so the per-edit settle logic lives in exactly one
  place. The repack `finally` calls `setChanged()` + `finalizeAfterBatch()` per window block
  instead of the old bare `setChanged()` + `syncToClients()`, so a sort that relocates glowstone
  or changes a block's fill re-lights and re-notifies comparators (finding 6).
- New `hasStorageBlockAbove()`: true when a Storage Stack sits directly above. The repack
  removal loop now breaks on `!isEmpty || isPermanent || hasStorageBlockAbove`, so it never
  deletes a window-top empty block that still has occupied Storage above it (finding 5, site A).

**`network/ExtractPkt.java`** (finding 5, site B) — `handleStorageExtract`'s `shouldRemove` now
also requires `!sbe.hasStorageBlockAbove()`, so pulling the last local item from a mid-pile block
(while the repack cooldown throttles consolidation) no longer severs the pile.

**`living-spec.md`** — repack step 5 and the empty/broken-blocks bullet now state the
never-remove-under-a-stack rule instead of describing the old severing behavior as intended.

### Design decision
Consolidated the light/comparator/sync logic into `finalizeAfterBatch()` and pointed both the
normal per-edit path and the repack at it (rather than duplicating it), so the two cannot drift.
The review flags this same finalize pattern for reuse in the Stage 4 Singles/Bar cascade fixes.

### Suggested commit message for Stage 3 (code only)

```
Fix stale shapes, pile severing, and stale light after repack

- Mark Singles and Bar blocks dynamicShape so collision is served live
  from block-entity contents instead of a stale per-state cache
- Add finalizeAfterBatch and route both the per-edit path and pile repack
  through it, so repack refreshes light and comparators, not just sync
- Never remove an empty Storage block while a Storage block sits directly
  above it, at both the repack and player-extract removal sites
```

---

## Stage 4 — COMPLETE (verified by reading; not yet built/run)

Goal: fix finding 7, the cascade half of finding 12, and finding 16 — mid-mutation publication and
rotation mismanagement in the Singles/Bar cascades, the quadratic repack walk on Storage overflow,
and unreliable consolidation.

### Files changed

**`block/SinglesStackBE.java`** (findings 7, 12) — added `suppressSync` + a private
`finalizeAfterBatch()` (sync + light recompute; no `updateNeighborsAt`, since only Storage has
comparator output). `onContentsChanged` keeps `setChanged()` and the `cachedShape` invalidation
unconditional and delegates the rest to `finalizeAfterBatch()` when not suppressed. `extractAt`
now wraps the extraction and the column cascade in one suppressed batch and finalizes once — which
also publishes the rotations the cascade moves, previously written after the last sync and
therefore never sent. `cascadeUnsupportedBlocks` ends with a new `clearEmptyCubeRotations()` sweep,
so every empty cell — including the extracted one, which was never cleared — holds rotation 0 and
the next deposit cannot inherit an orientation. `setRotation`/`setCubeRotation` respect the
suppression flag.

Also fixed here, found by in-world testing of the above: `saveAdditional`/`load` passed the
`cubeRotations` array to and from NBT **by reference** (`IntArrayTag` keeps the array it is given,
`getIntArray` hands it back). An integrated server does not serialize its block entity update
packets, so the client's `SinglesStackBE` shared the server's array. Rotations therefore changed on
the client the instant the server wrote them, while its items waited for the end-of-tick packet —
a frame drawn in that window showed the pre-cascade item layout with post-cascade rotations, seen
in testing as a one-frame unrotated top item. Both directions now clone. This is the only array
stored in NBT in the mod.

**`block/BarStackBE.java`** (findings 7, 12) — same `suppressSync` + `finalizeAfterBatch()` and the
same batched `extractAt`. `removeUnsupportedBlocks` is now a single ascending pass: slot index is
layer-major (`index / 8` is the layer) and `BarCubeIdx.isGrounded` consults only the layer below,
which the pass has already settled, so the `do/while` rescan could never find work. The method also
returns early off-server, fixing a client path that deleted bars without dropping them (the old
`anyRemoved` flag was set inside the server-only branch, so it was already dead).

**`block/StorageStackBE.java`** (findings 12, 16):
- `deposit` split into the public entry and a private `deposit(ItemStack, boolean ownsRepack)`.
  Overflow frames pass `false`, so a deposit that spills up a tall pile runs `findPileBase()` once
  instead of once per block touched (the cooldown previously rejected the extra calls only *after*
  each had walked to the base).
- `consolidate` rewritten to total the input by an exact `StackKey(item, damage, tag)` and re-cut
  each total into whole stacks plus one remainder. The old version merged only into the previous
  output element, so compatible stacks the comparator happened to separate stayed separate.
- Dropped the pre-sort in `resortAndPackPile`; `consolidate` sorts its output once, and with a key
  map the input order is irrelevant.

**`util/StackSort.java`** (finding 16) — the comparator now orders output only. Tag *presence* was
replaced by a deterministic comparison of tag contents (untagged first, then tagged in stable
order), and "partials first" — which existed to help the old adjacent merge — became "fullest
first", so each item's run ends on its single partial stack. `StorageStackBE` is its only consumer.

**`living-spec.md`, `REPORT.md`** — repack steps 2-3 now describe identity-based consolidation,
Bar gravity is a single pass, Singles gravity clears rotations on emptied cells, and a new
"Cascade publication" note records the settle-once rule.

### Design decision
Singles and Bar each carry their own `finalizeAfterBatch()` rather than sharing one with Storage:
each references its own block class's `LIGHT_LEVEL` property, and Storage additionally notifies
neighbors for its comparator output. Three short methods with one shape beat one method
parameterized over a property and a comparator flag.

### Not in Stage 4
The repack window (`PILE_SORT_MAX_STACKS`) still bounds how much of a tall pile one repack touches;
that is a server-performance control, not a defect, and it is the cause of the "only draws from the
bottom three" observation below. `ss test all` burst generation remains Stage 7.

### Suggested commit message for Stage 4 (code only)

```
Batch Singles/Bar cascades and consolidate by exact item identity

- Suppress per-slot sync during Singles and Bar gravity and settle once,
  so cascaded item rotations reach clients and light is recomputed once
- Clear the rotation of every emptied Singles cell so the next deposit
  does not inherit the previous occupant's orientation
- Collapse unsupported bars in one ascending pass and stop deleting bars
  client-side without dropping them
- Walk to the pile base once per deposit instead of once per overflowed
  block
- Consolidate a repack by exact item/damage/tag identity rather than by
  adjacency in a sorted list, and sort the result once
- Copy the Singles rotation array in and out of NBT, so an integrated
  server and its client stop sharing one array
```

---

## In-world observations from testing (map to later stages — NOT Stage 1 regressions)

- **Removing from a 4-high pile only draws from the bottom three.** Repack window
  (`PILE_SORT_MAX_STACKS`, default 3) plus downward packing concentrates items in the bottom of
  the window; the 4th block falls outside it. The pile-severing half (finding 5) was fixed in
  Stage 3 and consolidation (finding 16) in Stage 4; what remains is the window itself, a
  deliberate performance control. Diagnostic: raise `max_stacks_per_resort` above the pile height.
- **Pipe removing the last item leaves the empty block regardless of permanent status.**
  Capability extract (`extractFromSlot`) deliberately does not do the player-path block removal;
  the only cleanup is repack removing empty non-permanent blocks at the window top. Finding 5
  (never-remove-under-a-stack) is fixed in Stage 3, but the underlying gap remains: a
  non-permanent block emptied by capability extract at the window top can still linger when the
  cooldown throttles the repack, because capability extract has no player-path removal of its own.
  If this still bites in testing, revisit as its own item (add a post-extract cleanup on the
  capability path).
- **Singles/Bar cannot grow beyond the first layer by hand.** Interaction-rule ordering:
  `DepositIntoClickedStackRule` precedes `PlaceAdjacentGenericRule`, so V-right-clicking the top
  of a stack always deposits into it (fails when the column is full) and never falls through to
  placing a block above. Singles/Bar don't overflow upward, so there's no fallback — and the
  `PlaceAndDepositPkt` `msg.face == UP` cross-block-support code is therefore effectively
  unreachable for the "place above an existing stack" case. **Pre-existing, not in the original
  7 stages.** This is an interaction/packet redesign (let a full-column deposit fall through to
  place-above), related to finding 10. **Recommend adding to Stage 6** (or its own stage) if
  vertical hand-growth of Singles/Bar is desired.

---

## Status summary

- Stage 1: **done**, pipe auto-expand confirmed working in-game, committed as `08070ce`.
- Stage 2: **done** (finding 3, protection option E1), confirmed working in-game, committed as
  `9f784cb`. See the Stage 2 section above.
- Stage 3: **done** (findings 4, 5, 6), confirmed working in-game, committed as `0ea58c8`. See
  the Stage 3 section above.
- Stage 4: **done** (finding 7, the cascade half of finding 12, and finding 16) plus the
  Singles rotation-array aliasing bug that in-world testing surfaced. See the Stage 4 section
  above. Retest the rotated-column extraction to confirm the one-frame flash is gone.
- Stages 5–7: not started. **Stage 5 is next** (findings 8, 9): dedicated-server sound ownership
  and the config-reload bus.
- Open addition to consider: Singles/Bar vertical hand-growth (interaction redesign, related to
  finding 10 / Stage 6).
- Verification to date is by reading + the user's in-world testing. Nothing has been built or run
  in this environment. `living-spec.md` was updated alongside Stages 2 and 3; `fix-plan.md` is the
  progress log and is tracked but conceptually separate from the code commits.
