# Code Review: Test Completeness

Scope: the GameTest suites under `common/src/gametest` and each loader's `*/src/gametest`. There is
no JUnit suite; these are the whole of the mod's automated testing. Assessed as the project stands,
not its history.

## Summary verdict

The suite is broad, well-organized, and close to complete for server-side behavior. Loader-neutral
assertion logic lives in one `*Checks` class per area with thin per-loader `@GameTest` delegates, and
the loader-native surface (Forge `IItemHandler`, Fabric Transfer API) is tested in parallel. Forge
and NeoForge run 82 tests each; Fabric runs 74, and the 8 it omits are documented (Forge
event-bus / claim paths with no Fabric counterpart).

What is covered well:

- Bar and Singles support, gravity, grounding, footprint/seam overlap, player vs. automated
  extraction, cross-block cascade and drop consolidation, tag-preservation on collapse.
- Comparator output for all three block types: zero reserved for empty, whole-run reads from every
  block, and follow-through when a run loses a block.
- Persistence: update-tag round-trips for items, both rotation axes, and permanence; cached-shape
  invalidation.
- Capability / Transfer-API parity: presence on every side, advertised headroom, per-slot
  addressing, walk-fills-from-bottom, and `simulate` non-mutation.
- Packet boundary for **deposit**, **place-and-deposit**, and **extract**: reach, main-hand-only
  gesture reads, empty cells, wrong/full hands, out-of-range indices, refused-extraction leaving the
  block standing.
- Protection and growth: build height, entity obstruction, world border, waterlogging, same-tick
  click suppression, mod-driven removal under a refused break, and simulation/commit agreement.
- `ItemOps`, `StackSort` comparator ordering, and the render-gallery world builder (Storage kind).

The suite is **not** meaningfully over-complete. The only real excess is noted below.

## Gaps, in rough priority order

### 1. Rotate/toggle gesture packet handlers are untested end-to-end

`RotateBlockPkt.handleServer`, `RotateItemPkt.handleServer`, and `TogglePermanentPkt.handleServer`
have no coverage at the handler level. Only their *effects* (`setRotation`, `setCubeRotation`,
`setPermanent`) are round-tripped by the persistence tests, set directly. The deposit/place/extract
packet boundary is thoroughly tested; the rotate/toggle boundary is not tested at all, even though
`PacketBoundaryGameTests` is the natural home and delegates already exist for the analogous packets.

Invariants each handler's own comments call out, currently unverified:

- Rotate-block / rotate-item claim the click to suppress the vanilla torch placement — the same
  claim/suppression contract that *is* tested for deposit and place.
- `RotateItemPkt`: an empty target cell still claims the click but must not leave an orientation a
  later deposit inherits ("Empty cells carry no orientation").
- `TogglePermanentPkt`: the one gesture that consults protection *without* claiming, and requires an
  empty main hand.
- Wrong gesture item in hand is rejected (redstone torch vs. soul torch vs. empty).

### 2. `OverrideJsonCodec` has zero coverage

Loader-neutral pure logic: `parse`, `toJson`, `entryToJson`, `sanitize`, `inRange`. It enforces the
inclusive scale/offset bounds, skips malformed entries and fields, and carries an explicit invariant
that `sanitize` must match `parse`'s bounds because network overrides bypass `parse` at the packet
boundary (`RenderOverridePkt` / `WriteOverridesPkt`). This is the mechanism behind the "preserve
render-profile precedence across files, commands, and sync" maintenance invariant. A round-trip and a
boundary/clamp test are cheap and need no live level.

### 3. `ServerConfig` has zero coverage

458 lines of admin-facing config: glob baking (`forge:ingots*`), mod/item disable lists, and
clamping of `max_pile_height` and `required_permission_level` to their ceilings. `load(Path)` and
the `matches*` predicates are testable. The ingot-tag resolution that drives
`BarStackBE.isValidBarItem` is used by every Bar test through `firstBarItem()` but never asserted:
nothing checks that a configured ingot tag actually admits or excludes a specific item, or that a
`deny mod` / disabled-item entry removes one. "Server friendly" and useful admin controls are stated
project principles, which makes this a substantive hole.

### 4. `GestureThrottle` has zero coverage

One-gesture-per-player-per-tick plus the 4-tick rotation-sound interval. Small, deterministic, only
needs `getGameTime`. The per-tick throttle is a stated packet-boundary guarantee ("custom packets
bypass vanilla's interaction pacing. The server therefore restores that limit").

### 5. `RenderGalleryChecks` under-exercises its own subject

Only `Kind.STORAGE` is tested; `SINGLES` and `BAR` gallery kinds are not. The test's own javadoc says
the build is "spread across ticks by the configured placement limit," but it enqueues 11 items
against a default limit of 64, so nothing spreads and there is no partial-progress assertion. Either
enqueue enough to cross a tick boundary or drop the claim.

## Mild over-completeness

Growth refusal is tested three ways per stack type — world border, obstructing entity, and (Storage
only) a solid block above — for what is essentially one invariant: simulation and commit agree that a
position growth cannot use is unavailable. That is nine growth-refusal tests where roughly five would
carry the same signal, since the obstacle type mostly selects which upstream predicate says "no"
rather than exercising distinct mod code. Defensible, and cheap, but this is the suite's most
redundant corner. Re-asserting `simulate=true` non-mutation across many capability tests is similar
but negligible.

## Not counted against completeness

- Client-only surfaces — `CubeRenderHelper`, `ModelMeasurement`, `AutoRenderProfiles`,
  `BarTextureStore`, the client interaction rules, the BERs — are outside a server-only GameTest
  suite by construction. `OverrideJsonCodec` and `ServerConfig` are the exceptions because they are
  loader-neutral and server-side despite feeding rendering.
- Fabric's smaller test count is deliberate and documented.
- The Forge and NeoForge suites are identical modulo API names; that is appropriate, not wasteful.
- The `comparator*` trio replicated across Bar / Singles / Storage is justified — each block type has
  a separate signal implementation.
