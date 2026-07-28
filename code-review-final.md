# Some Stacks — Review findings

Working record of a code review of the whole of `src/main/java` against `living-spec.md`. Findings
that are settled are summarised in the tables; findings still needing a decision are written out in
full below, each one self-contained.

No build or test run was performed for any of this, including the coverage assessment in 7.1, which
comes from reading the test sources. Where something was confirmed by playing, it says so.

---

## Settled

### Fixed

| # | Finding | Resolution |
|---|---|---|
| 2.1 | Every item-handler call re-resolved the whole run from the world, over a slot count that advertised unbuilt height | Two changes, both measured against a Refined Storage external storage polling each type once a tick. Advertising the real slots plus one block of headroom cut a height-1 run from 1,025 walks a tick to 258. Resolving the run once a tick and holding it took all three types to **exactly 1 walk per tick** — 164/258/514 handler calls each served by one walk. Roughly 122,000 block-entity lookups a second down to 360, and 18,700 list allocations a second down to 60. Behaviour verified in game: growth into a full pile, mid-run break settling, Bar collapse |
| 2.2 | `ss test` / `ss testingot` overwrote a region of the world with none of the protection checks a placement gesture answers to, gated only on the render allow list | Moved to operator permission (`isPlayer && isAdmin`), `checkAllowed` dropped from the four wall executors. The `ss gen` docs claimed the walls stayed on the allow list; corrected |
| 2.3 | Simulated capability insertion credited growth for every remaining level after testing only the first, so it could promise more than the commit would place | Credits the one tested position and stops. One block holds a whole stack, so a single insertion never needs a second and nothing is lost |
| 2.4 | Rotate block, rotate item and toggle permanent mutated blocks with no protection consult at all | All three now run `Protection.isProtected` and `Protection.mayInteract`, matching deposit and extract |
| 3.1 | A comparator against a pile block whose own slots didn't change never learned the pile's value had | The settle compares the pile's signal before and after and, on a change, notifies every block of the run via `updateNeighbourForOutputSignal` |
| 3.2 | Config reload baked the ingot tags on Forge's file-watcher thread, walking data pack state the server thread rewrites | Whole bake hops to the server thread; `ingotGeneration` is an `AtomicInteger` |
| 3.3 | `BarStackBE.beginBatch` omits a `batchTouched` reset the other two have, and must, because batches nest there — but it reads as a slip | Comment explaining why, per your call. No code change |
| 3.5 | `ss item` checked scale only for being positive, which infinity passes; offsets were unchecked | Ranges are schema constants in `OverrideJsonCodec` (scale 0.01–20, offset ±1) and declared on the Brigadier arguments. Verified against the 46,440-entry corpus: nothing shipped falls outside them |
| 3.6 | The right-click event fired for protection mods described a fabricated main-hand hit at the block centre | Carries the gesture's hand and the face and point the player's own ray meets, computed server-side |
| 4.1 | Rotating an empty Singles cell left a facing behind for whatever was deposited there next, against a stated spec invariant | Empty-slot check, placed after the suppression mark so the gesture still claims the click |
| 4.2 | The measured render cache survived a resource-pack change across restarts | Records the enabled pack list; a cache written under a different set is dropped whole |
| 6 | Six points where `living-spec.md` had drifted from the code | All corrected, along with every behaviour change above |
| 7 | No test sources at all | A suite now exists, split at the registry boundary: 47 JUnit tests under `src/test/java` run by `build`, 50 Forge GameTests run by `runGameTestServer`. Two of the four cases I named are pinned properly, one is pinned by a test that would have passed with the bug present, and one is not pinned at all; see 7.1. `living-spec.md` documents the split and the boundary rule under *Automated verification* |
| 3.7a | The extraction handlers' own occupancy and hand-compatibility checks were the load-bearing consequence of leaving 3.7 alone, and were untested | Five GameTests added to `PacketBoundaryGameTests`, driving `handleStorageExtract` / `handleSinglesExtract` / `handleBarExtract` directly: empty cell, hand holding something else, full hand of the same item, a refused Bar extraction leaving the block standing rather than cascading, and indices outside each type's own slots. The three handlers dropped `private` to package-private for the same-package test class; no logic changed. All five pass |
| 3.7b | `ExtractPkt.handle` bounded the client's index with a literal `64` — the value of `SinglesStackBE.SLOTS` and `BarStackBE.SLOTS` written out, and too loose for Storage's 27 | The literal is gone. Each handler bounds against its own type's `SLOTS`: Singles and Bar check at the top, Storage was already covered by `extractAt`'s check against `items.getSlots()`. No behaviour changes — the old literal was correct for every index a client could send — but the bound is now derived where the type is known, and every handler is total, so the out-of-range test covers all three types rather than Storage alone |

### Closed without change

| # | Finding | Why |
|---|---|---|
| 1.1 | Claimed a failed Bar placement beneath an existing column would collapse it | **Not a defect.** Tested both routes I proposed. Clicking any face of a Bar Stack goes to `DepositIntoClickedStackRule`, which I missed sits ahead of the placement rule; clicking a neighbouring block places the held item normally. The rollback path exists but nothing reaches it. My analysis was wrong twice — treat the whole finding as withdrawn |
| 3.4 | Packet-level support prechecks duplicate what the block entity already enforces | Your call to leave it. It was only ever a simplification, and the one thing that made it urgent — feeding the rollback in 1.1 — is gone with 1.1 |
| 5 | ~120 lines of mechanical duplication across `StoragePile`, `SinglesColumn`, `BarColumn` and their three handlers | Your call, and the reasoning is better than mine: 120 lines out of 10,000 to keep the main behaviour centralised is a fine trade, and deduplicating buys indirection |
| 3.7 | Extraction uses the cell index the packet names, where deposit recomputes its target from the player's eye position and look direction and ignores what the client claimed | **Deliberate, and left as is.** The exposure is nil: every cell reachable this way is inside a block the player may already interact with, and the handlers grant only what is there — no duplication, no protection bypass. What a modified client gains is picking a cell its view ray did not hit. Recomputing server-side would trade that for a real failure mode: a player whose aim drifts between the click and the tick the packet lands on extracts a different cell, and Bar extraction drops every bar the removal leaves unsupported, so extraction's wrong answer is destructive where deposit's is cosmetic. My original suggestion — validate the named cell against the cells the server's ray intersects — is the most code of the three options rather than the least, since `traceCubes` returns one index and all three `CubeIdx` classes would need a new intersected-set API. The decision makes the handlers' own occupancy and hand-compatibility checks load-bearing, which is what 3.7a pins |
| 2.1b | The capability handler's `insertItem` ignores its `slot` argument, so reading and extraction are positional while insertion is not. Predicted to cost one whole-run planning pass per advertised slot under a caller that loops them | **Measured, and it does not happen.** A hopper jammed against a full 8-block Bar column issues 1,024 `getStackInSlot` and 1,027 `getSlots` calls a tick and **zero** insertions: it reads the slots, finds no room and gives up without ever calling insert, so the planning pass is never entered. Two spark profiles agreed beforehand, showing the mod at 0.05% with no insert frames at all. My cost estimate was arithmetic per attempt, and there are no attempts. The original title, "slot-directed insertion is not honoured", was withdrawn as misleading — the player deposit path targets a named cell through `depositAt` and was never affected. What survives is a contract deviation with no measured cost: a caller that sums simulated per-slot capacity over-counts, since each simulation independently plans over the whole run. That is recorded in the three handler javadocs, which had separately gone stale under 2.1, claiming a slot count and a fixed handler shape that stopped being true when `getSlots()` became `advertisedSlots()`. The probe also confirmed 2.1 from the other side: all 2,051 handler calls a tick were served by a single world-walk |

### Deferred

| # | Finding | Note |
|---|---|---|
| 4.4 | `SinglesStackBE` runs two batching mechanisms side by side — `beginBatch`/`endBatch`, and raw `suppressSync` assignments in `extractAt` and `drawDownColumn` that publish through a different route | Set aside pending your read that this is the start of a complexity problem in that class. I think that read is right; the two mechanisms are the concrete shape of it |
| 4.5 | Storage extraction publishes once immediately and again after the next-tick settle — two block updates, two light recalculations and two neighbour updates per extraction | Folded into 4.4 as the same underlying issue |

---

## Open

### 4.3 The render fallback constructs an exception every frame

`CubeRenderHelper.renderBlockItem` draws a block item by rendering its block form. Some mods' blocks
throw when rendered without level context — the comment names Immersive Engineering multiblocks — so
the call is wrapped in a `try`/`catch` that falls back to ordinary item rendering.

The catch should stay. That is a third-party model failing, which is exactly the known-unreliable
external interface the project rules say to guard against, and removing it trades a working fallback
for a client crash caused by another mod.

The problem is that nothing remembers the failure. A wall of stacks containing fifty such items
constructs and throws fifty exceptions per frame, forever, and capturing a stack trace is the
expensive part of an exception. Recording which items failed for the current resource generation and
going straight to the fallback afterwards makes it a one-off. `CubeRenderHelper` already has a cache
cleared on resource reload to hang that off.

Second, smaller point: the catch calls `popPose()` and then renders the fallback. If the throw
happened after the block renderer pushed transforms internally, that `popPose` pops the wrong entry
and everything drawn afterwards that frame is displaced. Snapshotting the pose depth before the call
and unwinding to it would make the fallback safe regardless of where the throw came from.

### 4.6 Thirteen small items, still too compressed to judge

You were right that a table of one-liners is not something anyone can make decisions from. The three
I would defend as worth the space:

- **The two interaction rule lists are identical.** `EMPTY_HAND_RULES` and `ITEM_RULES` both hold a
  single `ModeCycleRule`, and the two context factories build identical objects from different event
  types. Separately, the empty-hand handler calls `setCanceled` on `RightClickEmpty`, which Forge
  documents as non-cancellable — that would throw if reached, and I could not confirm reachability
  without decompiling Forge. Collapsing the duplication removes the question along with the
  duplication.
- **Five registered sound events have no audio files.** `sounds.json` declares six; only
  `bar_extract.ogg` exists. None of the six are played anyway — `StackSoundData` maps every action
  to vanilla wood sounds. The missing files log a warning each on every client resource reload.
- **34 `LOGGER.debug` calls across 12 files**, mostly one-line "Executing XRule" traces. Two build
  their arguments eagerly on every right-click regardless of log level.

The remaining ten are cosmetic: `noLootTable()`, a `mineable/pickaxe` tag, block models using
`oak_planks` so a metal-coloured block breaks into plank particles, `BarTextureStore` reading a PNG
on the render thread the first time it sees a bar item, `StackSort` returning 0 on a null registry
key and so breaking comparator transitivity (`StackSortGameTests` now pins the ordering of every
identity component, but not that case, which needs an unregistered item), three `grow()` methods
re-checking a block entity they
just created, the three `localSlot` client fallbacks,
`BarCubeIdx.barHeight(int)` ignoring its parameter, `PlaceAndDepositPkt` decoding `BlockType` by raw
ordinal while decoding its other two enums with `readEnum`, dead methods (`StoragePile.basePos()`,
`height()` on all three run classes), player-visible strings using `Component.literal` instead of
translation keys, and `RenderMode`/`ItemRenderConfig`/`RenderProfile` living in the `client` package
while server-side code imports them.

Say which of those you want written up properly and I will.

### 7.1 Two of the four regression guards from finding 7 are not pinned by the suite

The suite is real and the boundary is the right one. Taking the four cases I named in order.

**1, cross-rotation Singles seam support — pinned, twice.** `SinglesCubeIdxTest`
(`visualAndStorageColumnsAreExactInverses`, `topLayerIsReportedInVisualCoordinates`,
`stackedBottomLayerUsesVisualSeamColumn`) pins the frame algebra, and `SinglesColumnGameTests`
(`differentlyRotatedBlocksShareVisualSeamColumns`,
`extractionDrawsAcrossDifferentlyRotatedBlockBoundary`) pins it in the world across a rotation
mismatch. This was the case I most wanted pinned and it is the best covered thing in the mod.

**3, capability simulate/commit agreement — pinned, and the case as I named it no longer exists.**
`StoragePileGameTests.obstructionPreventsGrowthAndSimulationReportsNoRoom` asserts the simulated and
committed remainders are equal against an obstruction, and
`capabilitySimulationDoesNotMutateAnyStack` / `capabilitySimulationDoesNotMutateExtraction` cover the
no-side-effects half. The obstruction sits at the *first* growth position rather than the second,
which is correct now: after 2.3 the plan credits exactly one position, and one block holds a whole
stack, so a single insertion never reaches a second position. There is no second-position case left
to guard.

**2, the comparator — covered by a test that would have passed before the fix.**
`StoragePileGameTests.comparatorReadsTheWholePileFromEveryBlock` asserts that
`getAnalogOutputSignal` returns the pile's value from both blocks of a pile. That was never the bug.
3.1 was that a block whose own slots an edit did not touch never told its neighbours to read again —
the *notification*, not the answer. Reading the signal directly bypasses exactly the mechanism that
was broken, so this test passes with 3.1 reverted.

What would fail: place a two-block pile, put a real comparator against the upper block, edit the
lower block's contents, run a tick so the scheduled settle fires, and read the comparator's own
output. Removing the `updateNeighbourForOutputSignal` call from the settle must turn that red.

**4, protection veto on every mutation packet — not covered.** `ProtectionGameTests` exercises
`Protection.placeChecked`, `Protection.mayInteract` and `RightClickBlockSuppressor` directly, and
those three tests are worth having. But none of them dispatches a packet handler, and 2.4 was not a
bug in the helpers — it was that `RotateBlockPkt`, `RotateItemPkt` and `TogglePermanentPkt` never
called them. A test of the helpers in isolation passes with 2.4 fully present.

What would fail: register a `RightClickBlock` listener that denies, then dispatch each of the six
server-bound mutation handlers against a placed stack and assert the world is unchanged — contents,
rotation and permanent flag. One test over a list of six, and it is the only one of these four gaps
that guards a fix rather than a behaviour.

**A smaller one alongside it.** 4.1 — rotating an empty Singles cell leaving a facing behind — has
no test either. `SinglesColumnGameTests.extractionShiftsOneColumnAndCarriesItemRotation` pins that
rotation travels with an item, but nothing dispatches `RotateItemPkt` at an empty index and asserts
the cell stays unrotated. It belongs in the same packet-level test class as the veto case, and it is
the half of 3.7 that is already fixed.

---

## Provenance

The findings came from two independent passes over the same code — one mine, one a merge of three
Codex passes — and every claim from the other pass was re-verified against the source before landing
here. Two of its stated mechanisms turned out to be wrong while the finding stood (3.5's route to a
bad float was integer overflow, not `NaN`, which Brigadier's number reader cannot accept), and one
claimed bug did not exist (a rotated-Singles frame error in 3.4). One of mine did not exist either,
and worse, since it sat at the top of the list: 1.1. A second of mine, 2.1b, was real in the code
and absent in the world; only a counting probe settled it, and it is the reason the numbers in that
row are measured rather than derived.
