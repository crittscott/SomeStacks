# Targeted Code Review: Networking

Scope: the mod's custom packet layer as it currently stands — the shared payloads in
`common/.../network/`, the three loader bindings (`forge`, `fabric`, `neoforge`), the client
gesture senders, and the server-side validation chain. Block-entity client sync is touched on
only where it is driven by a packet handler.

## Verdict

The C2S (client-to-server) surface is in good shape. Every mutating packet runs through one
shared `PacketBoundary.validate` chain plus a per-target protection claim, decoders are written
defensively for hostile input, and a per-tick gesture throttle caps abuse. I did not find an
item-dupe, reach-bypass, or cross-dimension vector.

The weaker areas are all on the S2C side and in efficiency, not C2S security:

1. **Efficiency (main issue):** every deposit/extract/rotate re-serializes the *entire*
   block entity (27 slots for Storage) to every tracking client, and a pile "settle" can fan
   that out across a whole column in a single tick.
2. **Malicious/compromised server:** the render-config S2C packets drive **client-side disk
   writes** and a **full item-registry scan** with no rate limit and no client-side gate.
3. Assorted smaller inefficiencies (int vs varint lengths, redundant protection lookups,
   per-login disk read).

Details below.

---

## 1. Architecture overview

- One logical channel. Payload classes (`*Pkt`) are shared in `common` and carry their own
  `Type` + `StreamCodec`; each loader binds them:
  - Forge: `SimpleChannel` with `networkProtocolVersion(2)` + `exact(2)` on both sides,
    mod required on both ends.
  - Fabric: `PayloadTypeRegistry` + an explicit empty `ProtocolPkt` (`protocol_2`) handshake;
    both the client (`ClientPlayConnectionEvents.JOIN`) and the server
    (`ServerPlayConnectionEvents.JOIN`) disconnect a peer that cannot receive/send the mod's
    payloads.
  - NeoForge: `PayloadRegistrar` non-optional payloads; NeoForge disconnects a peer missing a
    matching registration.
- 6 C2S packets: `PlaceAndDepositPkt`, `DepositPkt`, `TogglePermanentPkt`, `RotateBlockPkt`,
  `RotateItemPkt`, `ExtractPkt`.
- 4 S2C packets: `ProtocolPkt` (Fabric only), `ConfigSyncPkt`, `RenderOverridePkt`,
  `WriteOverridesPkt`.
- All handlers are dispatched onto the receiving side's main thread (Fabric
  `player.server.execute`, Forge `addMain`, NeoForge `enqueueWork` / `context.enqueueWork`),
  so the static maps in `GestureThrottle` and the mutable state in `ItemRenderOverrides` are
  main-thread-confined. No thread-safety problem found.

---

## 2. Malicious client (C2S) — solid

### 2.1 Shared validation chain

`PacketBoundary.validate(sp, pos)` is run first by every C2S handler:

- non-null sender (loader adapters already null-check `ctx.getSender()` /
  `context.player() instanceof ServerPlayer`);
- `GestureThrottle.claimTick` — one gesture per player per game tick, spent even if later
  checks fail (good: rejection is not a free retry within the tick);
- spectator rejected;
- `sp.serverLevel().isLoaded(pos)` — bounds an out-of-world / unloaded-chunk `BlockPos` and
  prevents forcing chunk loads or touching ungenerated terrain;
- `withinReach` — `PlayerReach.blockReach(sp)` (4.5 / 5.0 creative) + 1.0 padding, squared
  distance from eye position. Matches vanilla server-side interaction slack.

Then each handler adds a `PlayerEdits.claim*` call that consults the protection authority at
the stack position **and** at any secondary position the packet names (e.g. `DepositPkt`
consults both `pos` and `clickedPos`). This is the right shape — a gesture that straddles a
protection boundary is refused.

### 2.2 Per-packet input handling

- **`ExtractPkt`** — `index` is attacker-controlled but every consumer range-checks it:
  `StorageStackBE.extractAt` (`index < 0 || index >= items.getSlots()`),
  `handleSinglesExtract` / `handleBarExtract` (`< 0 || >= SLOTS`). Extraction also honors
  `ItemOps.canTakeIntoHand` and `maxCount = maxStackSize - count`, so no over-stuffing the
  hand and no type-mixing.
- **`DepositPkt`** — `clickedPos` must equal `pos` or be Manhattan-distance 1 from it;
  anything else returns. Server recomputes the target cell from the player's *server-side*
  view ray (`ViewRays.of(sp)`), never trusting a client cell index. Creative deposits work
  from a `held.copy()` and never write the hand back — no creative dupe.
- **`PlaceAndDepositPkt`** — the only block-creating packet and the most thoroughly guarded:
  both enum fields arrive as bytes, are range-checked in `decode` (`BlockType.fromOrdinal`,
  `faceFromOrdinal` both return null out of range), and a null is carried through and dropped
  in `handleServer` rather than thrown on the netty thread. Then: replaceability,
  `mayUseItemAt`, disabled-mod/disabled-item, `blockType.isEnabled()`, full-column height
  check, server-side deposit-cell trace, a dry-run `firstDepositWouldSucceed`, and finally
  `WorldEdits.placeChecked` (entity obstruction + block-place event) before any mutation.
  Ordering is correct: the world is only mutated once every gate has passed, so a failed
  deposit cannot leave an empty block or fire a place event for a block that gets rolled back.
- **`RotateBlockPkt` / `RotateItemPkt`** — require the specific torch item in the main hand,
  verify the target block/BE type, and range-check `slotIndex` against `SinglesStackBE.SLOTS`.
  They deliberately still `claimInteraction` on an empty cell so the vanilla torch placement
  is suppressed; the rotation itself no-ops. Reasonable.
- **`TogglePermanentPkt`** — requires an empty main hand and a Storage block; deliberately
  does not claim the click.

### 2.3 Minor C2S notes

- **Redundant protection lookup in `TogglePermanentPkt.handleServer`:** it calls
  `WorldEdits.isProtected(sp, pos)` and then `PlayerEdits.mayInteract(sp, pos)`, and in the
  bundled `VanillaPlayerEditAuthority` the latter is exactly `!isProtected(...)`. Two lookups
  for one decision. Harmless, but tidy it to one call.
- `GestureThrottle` keys two `HashMap`s by player UUID and relies on the logout/disconnect
  hook (`SomeStacks`, `SomeStacksNeoForge`, `SomeStacksFabric` all call
  `GestureThrottle.clear`) to evict. That hook is reliable in practice; the maps are not
  bounded independently, but the exposure is negligible.
- `ExtractPkt.handleStorageExtract` / `handleSinglesExtract` / `handleBarExtract` are
  `public static` and take `Player` rather than `ServerPlayer`. Not reachable maliciously
  (nothing routes to them but `handleServer`), but they are wider than they need to be.
- Reach is checked only for the primary `pos`; secondary positions (`clickedPos`, the
  `msg.pos`/`clickedPos` neighbor in `DepositPkt`, the `clicked` back-off in
  `PlaceAndDepositPkt`) are bounded by the distance-1 / face-relative constraints rather than
  their own reach test. That is sound given those constraints, but it is load-bearing — any
  future packet that names a second position must keep an equivalent bound.

---

## 3. Malicious / compromised server (S2C) — the softer surface

You normally trust the server you connect to, and a vanilla server can already lag a client.
But the render-config packets do two things vanilla S2C traffic does not, with no throttle and
no client-side gate:

### 3.1 Server-triggered client disk writes

`WriteOverridesPkt` handlers call `ItemRenderOverrides.handleWriteRequest()` (writes
`config/somestacks/item_overrides.json`) or `handleDumpRequest(namespaces)` (writes one
`config/somestacks/generated_overrides/<ns>.json` per namespace). A server can send this
unsolicited and repeatedly. Path traversal is defended (`ResourceLocation.tryBuild` validates
each namespace, plus a `file.startsWith(GENERATED_DIR)` re-check after `normalize()`), and
`MAX_NAMESPACES = 4096` bounds one packet — so the blast radius is "repeated writes inside the
mod's own config dir," not arbitrary file write. Still, unsolicited disk writes driven by a
remote peer are surprising behavior and worth a deliberate decision (e.g. only honor a
`WriteOverridesPkt` within N seconds of the client having caused an `/ss write`, or at least
log it).

### 3.2 Full item-registry scan on the client thread

`handleDumpRequest` iterates **all** of `BuiltInRegistries.ITEM`, calls
`ItemRenderOverrides.resolve(new ItemStack(item))` for every item whose namespace matches
(which forces auto-profile *measurement* for anything not already cached), then writes files —
all on the client's main/render thread. One malicious packet naming many populated namespaces
is a multi-second client stall; a stream of them is a sustained freeze. `ConfigSyncPkt` decode
allows up to `MAX_OVERRIDE_ENTRIES = 65536` override entries and rebuilds the client's synced
override map each time it arrives; cheap per entry, but again unbounded in frequency.

### 3.3 No rejection of S2C payloads a vanilla/other-mod client shouldn't act on

This is inherent to S2C (the server already decided), and the *server* command tree gates
`ss item` / `ss write` behind permission level 2, so this is not a privilege bug — just noting
that the client applies whatever arrives.

### Recommendation for 3.x

Add a small client-side guard on the three render S2C packets: a minimum interval between
honored `WriteOverridesPkt`/dump requests, and ideally a "did the local player just invoke
`/ss write`" latch. Keep the existing path-containment checks.

---

## 4. Efficiency and network spam

### 4.1 Whole-block-entity resync per gesture (top concern)

`StorageStackBE.getUpdateTag` = `saveAdditional`, which writes **all 27 slots**
(`items.serializeNBT`, full item components) plus rotation and the permanent flag. Every
`syncToClients()` call does `level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL)`,
i.e. a full `ClientboundBlockEntityDataPacket` (plus a block-state update / re-render) to
every tracking player. `SinglesStackBE` and `BarStackBE` are the same pattern.

`syncToClients()` is called on essentially every mutation: deposit, extract, rotate, light
change. So a single extracted item = the entire block's inventory NBT on the wire, per
viewer. At the gesture cap (20/s server-side, ~5/s from a held right-click) that is a
meaningful stream for one player at one block, and it scales with the number of nearby
players.

Worse, the pile/column **settle** after a bottom extraction (`StoragePile` / `SinglesColumn` /
`BarColumn` packing contents down over a gap) marks and re-syncs multiple blocks in the
column in the same tick — a burst of full-BE packets proportional to column height for one
click.

Options, roughly in order of value:
- Send a compact delta (changed slot index + stack) instead of the whole tag for the common
  deposit/extract case; keep the full tag only for `getUpdateTag` (initial chunk send).
- Coalesce a settle's per-block syncs into one update pass at end of tick.
- At minimum, skip the `sendBlockUpdated` block-state half when only BE contents changed and
  the block state is identical (it already passes `state, state`).

This is the item I would fix first — it is the actual "do we spam the network" answer.

### 4.2 `ConfigSyncPkt` cost

- `ServerOverridesLoader.load()` is called inside `buildConfigSync()`, which Forge/NeoForge
  invoke **per player login** (`onPlayerLogin` → `sendConfigSync`) — a disk read of the
  override directory on every join. `/ss reload` broadcasts are fine (Forge/NeoForge build one
  packet for `PacketDistributor.ALL`; Fabric builds one packet then loops the send). Cache the
  loaded overrides and re-read only on `/ss reload`.
- The payload itself can be large if an admin has populated server overrides for a whole
  modpack (thousands of `ResourceLocation` + mode string + up to 4 floats each), sent in full
  to every player on join and on every reload. No delta, no size budget. Probably acceptable
  for the intended use, but note it.

### 4.3 Wire-format nits

- `ConfigSyncPkt` and `WriteOverridesPkt` write collection sizes with `buf.writeInt` rather
  than `writeVarInt`/`writeCollection`. Minecraft convention is varint lengths; costs ~3
  bytes per length field and reads slightly against "the Minecraft way."
- `ConfigSyncPkt` encodes each optional field as a `boolean` presence flag + value. Fine, but
  `writeOptional` / a small bitmask would be more idiomatic and smaller.
- `RenderOverridePkt` always carries a fixed `float scale` + `float[3] offset` even for a
  `reset` packet on the encode side it's skipped, but the class still holds dummy values;
  minor.

### 4.4 Client send behavior

Client gesture rules (`ExtractionRule`, `DepositIntoClickedStackRule`,
`PlaceAdjacentGenericRule`, the two rotate rules) send exactly one packet per matched
right-click event and cancel the event so vanilla item use does not also fire. `RotateItemPkt`
is sent somewhat speculatively (even when the view ray hit no item, by design, to suppress
torch placement) — at most ~5/s and idempotent server-side, so acceptable. There is no
client-side per-tick dedupe, but the server throttle makes that non-critical. No evidence of
unconditional per-tick sends or continuous-hold streams.

---

## 5. Protocol / handshake

Three different mechanisms (Forge channel version `exact(2)`; Fabric explicit `ProtocolPkt`
+ two-sided `canSend` disconnect; NeoForge non-optional payload registration). All three
achieve "mod required and protocol-exact on both sides," and the `ProtocolPkt` id is
namespaced with a version suffix (`protocol_2`) so a wire-format bump forces a clean reject.
This is correct. The divergence is a maintenance cost, not a bug — just remember that a
protocol change now means touching all three, plus renaming `ProtocolPkt`'s id, plus bumping
Forge's `PROTOCOL` constant. The comment in Forge `ModNetworking` about positional
registration order per direction is the sharp edge: inserting a payload anywhere but the end
of its block silently renumbers the rest.

---

## 6. Prioritized recommendations

1. **Stop resending the whole block entity per gesture (4.1).** Delta the common
   deposit/extract path; coalesce settle syncs. Biggest real-world network win.
2. **Guard the render S2C packets client-side (3.1 / 3.2):** rate-limit honored
   `WriteOverridesPkt` and dump requests, ideally latch them to a recent local `/ss write`.
   Keep the path checks.
3. **Cache `ServerOverridesLoader.load()` (4.2):** read on `/ss reload`, not on every login.
4. Switch length fields to varint / `StreamCodec` collection helpers (4.3).
5. Collapse the double protection lookup in `TogglePermanentPkt` (2.3).
6. Narrow the `public static` extract helpers to `ServerPlayer` / package-private (2.3).

## 7. What I checked and found clean

- No item duplication via creative, partial merges, or drop-on-full paths in `ExtractPkt` /
  `DepositPkt` / `PlaceAndDepositPkt`.
- No reach bypass, unloaded-chunk touch, or cross-dimension targeting.
- Hostile decode input: enum ordinals, collection sizes, slot indices, and `BlockPos` are all
  bounded or null-guarded; decoders do not throw on the netty thread.
- Handlers run on the main thread on all three loaders; shared mutable state is not raced.
- Handshake rejects a non-mod or wrong-protocol peer on both sides on all three loaders.
