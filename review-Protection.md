# Targeted Code Review — Protection & Claims

Scope: whether SomeStacks honors world protection (world border, spawn protection),
and third‑party claim / logging mods, for every path that writes to the world.
Reviewed the project as it currently stands on the `1.21.1` branch across `common`,
`forge`, `neoforge`, and `fabric`. No changes made.

## Verdict

The protection story is, on the whole, well thought out and deliberately built,
not accreted. Every player mutation is funneled through custom packets that consult
`PlayerEdits`, and every automation‑driven edit goes through `WorldEdits`; the mod's
own block `use` / `useItemOn` handlers are inert (`CONSUME`), so there is no
un‑gated vanilla interaction path into stack contents. Forge and NeoForge get full
interaction‑event, block‑place‑event and block‑break‑event integration plus
same‑tick suppression of the trailing vanilla click. The design fails safe
(over‑consult rather than under‑consult) in the places where it is imperfect.

The material weaknesses are all on **Fabric**, and all already acknowledged in code
comments: there is no generic claim hook for automated block *placement* on that
loader, so only FTB Chunks is consulted. Everything else below is hardening,
consistency, or informational.

## How protection is honored (map of the mechanism)

- **Player gestures** — `DepositPkt`, `ExtractPkt`, `PlaceAndDepositPkt`,
  `RotateBlockPkt`, `RotateItemPkt`, `TogglePermanentPkt`. Each runs
  `PacketBoundary.validate` (real sender, one gesture/tick via `GestureThrottle`,
  non‑spectator, loaded target, within reach) and then a `PlayerEdits` consult:
  - `claimInteraction` — block‑access permission (world border + spawn protection +
    `RightClickBlock` / `UseBlockCallback` with `getUseBlock() != DENY`).
  - `claimItemUse` — the above **plus** held‑item permission (`getUseItem() != DENY`);
    used by deposits, which spend the hand straight off the click.
  - `claimPlacement` — both the clicked‑against position and the filled position,
    interaction event on the former, block‑place event (later, via `placeChecked`)
    on the latter.
  - On Forge/NeoForge a successful claim marks `RightClickBlockSuppressor` for the
    claimed position so the trailing vanilla `ServerboundUseItemOnPacket` in the
    same tick cannot also act there. Consults run first, mark is placed last, mark
    lives one tick, cleared on logout.
  - On Fabric there is no server mark; `FabricClientEvents` returns
    `InteractionResult.FAIL` client‑side so the vanilla packet is never sent.
- **Automation (column growth / settle / collapse / trim)** — `WorldEdits`:
  - `placeChecked` — build height, entity obstruction (`isUnobstructed`), then the
    loader placement veto (`EntityPlaceEvent` on Forge/NeoForge; FTB Chunks direct
    on NeoForge/Fabric), with rollback of the block if vetoed after `setBlock`.
  - `removeChecked` — world border + spawn protection against the automation actor,
    then the loader break veto (`BlockEvent.BreakEvent` on Forge/NeoForge,
    `PlayerBlockBreakEvents.BEFORE` on Fabric).
  - Growth's `canGrow` predicate (shared by simulation and commit in
    `BarColumn` / `SinglesColumn` / `StoragePile`) includes
    `WorldEdits.isProtected(...)` against the correct actor — the depositing player
    when there is one, else the level automation actor, which is never op‑exempt
    from spawn protection.
  - Removal loops (`trimEmptyTop`, `removeEmptyBelow`, the bar cascade) stop at the
    first refused removal and keep the run's in‑memory model in step with what is
    left standing.

## Strengths

- **Single choke point per direction.** Nothing writes stack contents or places /
  removes a stack block outside `PlayerEdits` / `WorldEdits`, except the op‑gated
  gallery command (see F7). The `*Block` classes' `use` / `useItemOn` return
  `CONSUME` server‑side, so a plain right‑click cannot reach contents.
- **Correct actor for spawn protection.** Automation is attributed to a
  non‑operator actor and the code comments explicitly reason about op‑exemption;
  player‑attributed growth is checked against the real player.
- **Simulation and commit share one predicate.** A simulated capability insert
  cannot promise a growth that the commit's protection check would refuse, and
  `placeChecked` re‑checks and rolls back regardless.
- **Deposit is gated as an item use, not a bare interaction** — matching what
  vanilla applies to item‑driven interaction; permission to reach the block is
  correctly treated as insufficient.
- **First deposit is validated before the world is mutated** in
  `PlaceAndDepositPkt`, so `EntityPlaceEvent` listeners never see a placement for a
  block that is then rolled back.
- **Same‑tick suppression is carefully ordered and bounded** — consults before
  mark, one‑tick lifetime, per‑player, cleared on logout; covered by Forge/NeoForge
  game tests (`sameTickSuppressionVetoesOnlyTheMarkedPosition`,
  `suppressionExpiresOnTheNextTick`, `depositConsultsTheAdjacentBlockThatWasActuallyClicked`).
- **`GestureThrottle`** restores vanilla's one‑interaction‑per‑tick pacing that the
  custom packets would otherwise bypass, which also bounds the rate of the
  synthetic events discussed in F2/F8.

## Findings

### F1 — Fabric: automated placement consults only FTB Chunks (Medium)

`FabricEditAuthority.preparePlacement` returns a veto that checks **only**
`FtbChunksProtection.prevents(...)`. Fabric API has no generic "a block was placed"
event, so on a Fabric server running any other claim mod (or with FTB Chunks
absent), automated column growth can place a stack block inside a claim — including
growth that climbs past a claim boundary the depositing player is not a member of.

Removal is not affected (Fabric's `PlayerBlockBreakEvents.BEFORE` is a real generic
hook). The player‑gesture path is also largely covered because
`FabricPlayerEditAuthority` fires `UseBlockCallback`, which Fabric claim mods do
implement; the gap is specifically **placement** of a *new* block by automation and
by `claimPlacement`'s filled position.

This is documented in the class Javadoc and in `ProtectionGameTests` ("Fabric has
no general claim-event hook … not duplicated here"), and the project's own memory
notes Fabric parity is intentionally partial. Recorded here as the single largest
real protection gap so it is visible in one place. If Fabric parity is later
wanted, the pragmatic options are a small allow‑list of directly‑consulted claim
mods (as done for FTB) or adopting a common Fabric protection API.

### F2 — Synthetic interaction event fired before the target is confirmed to be a stack block (Low→Medium)

`RotateBlockPkt` and `RotateItemPkt` confirm the block / block‑entity type **before**
calling `PlayerEdits.claimInteraction`. `DepositPkt`, `ExtractPkt`, and
`TogglePermanentPkt` do **not** — they run the `PlayerEdits` consult (which posts a
synthetic `PlayerInteractEvent.RightClickBlock` on Forge/NeoForge, or invokes
`UseBlockCallback` on Fabric) at `msg.pos` first, and only afterward check whether
`msg.pos` actually holds a `StorageStackBE` / `SinglesStackBE` / `BarStackBE`.

Consequences of a modified client sending these packets for arbitrary reachable
positions (rate‑limited to one/tick by `GestureThrottle`):

- Anti‑grief / logging mods that listen to the interaction event record phantom
  "player right‑clicked X" entries at other players' containers or machines. The
  Forge `Protection` Javadoc explicitly notes the event carries the truthful
  clicked position, so these are logged as real.
- `RightClickBlockSuppressor` gets marked for that position, silently eating the
  same player's own next legitimate right‑click there in that tick (self‑grief
  only).
- A third‑party listener that *acts* on the event (rare) could be triggered — the
  general risk in F8, here reachable at attacker‑chosen coordinates.

`DepositPkt`'s adjacent branch additionally consults `msg.clickedPos`, an
arbitrary Manhattan‑distance‑1 neighbor, widening the reachable set slightly.

This fails safe (it over‑consults) and is not a claim bypass, but it is an
inconsistency with the rotate packets and easy to close by reordering the
type check ahead of the claim, as those two already do.
(`PlaceAndDepositPkt` legitimately must consult an arbitrary clicked position —
that is how vanilla placement works too — so it is not part of this finding.)

### F3 — Fabric automation actor is a raw `ServerPlayer`, not the Fabric API `FakePlayer` (Low)

`FabricEditAuthority.automationActor` builds `new ServerPlayer(server, level,
PROFILE, ClientInformation.createDefault())` and caches it per level. Fabric API
ships `net.fabricmc.fabric.api.entity.FakePlayer`, and the mod's own Fabric game
tests use `FakePlayer.get(level)`. Claim / protection mods that special‑case
`instanceof FakePlayer` (common, e.g. to always deny fake‑player edits or to check
fake‑player UUID membership) will not recognize this actor, and constructing a bare
`ServerPlayer` server‑side is heavier and more side‑effect‑prone than the API
helper. NeoForge correctly uses `FakePlayerFactory.getMinecraft(level)`.

Practical effect on protection is usually the safe direction (an unknown non‑member
UUID tends to be denied inside claims), but recognition is not guaranteed.
Forge's `ForgeEditAuthority` does the same raw construction with the comment that
Forge 1.21.1 ships no FakePlayer helper — not verified here per project rules; if a
helper is in fact available it should be preferred there too.

### F4 — Forge authority does not directly consult FTB Chunks the way NeoForge/Fabric do (Low, consistency)

`NeoForgeEditAuthority.preparePlacement` and `FabricEditAuthority.preparePlacement`
both call `FtbChunksProtection.prevents(...)` in addition to (or instead of) the
event. `ForgeEditAuthority` relies solely on `ForgeEventFactory.onBlockPlace`
firing `BlockEvent.EntityPlaceEvent`, trusting FTB Chunks to hook it. That is
probably sufficient on Forge, but the three loaders now disagree on whether FTB is
belt‑and‑suspenders or event‑only. Worth aligning, or documenting why Forge is
event‑only.

### F5 — `TogglePermanentPkt` reports the interaction twice to listeners (Low)

`TogglePermanentPkt` deliberately does not claim/suppress (empty‑hand gesture,
absorbed by the block's `use`). It still calls `PlayerEdits.mayInteract`, which
posts `RightClickBlock`. The trailing vanilla `ServerboundUseItemOnPacket` for the
same click is *not* suppressed, so it posts `RightClickBlock` again. Claim/logging
mods therefore see two interaction events per toggle gesture. Cosmetic for
protection (both are allowed), but noisy for logging mods.

### F6 — Fabric's trailing‑click suppression is client‑side only (Low)

On Fabric the vanilla interaction that would follow a gesture is stopped by
`FabricClientEvents` returning `FAIL` on the client, so nothing suppresses it
server‑side. A modified client can send the mod's gesture packet and still let the
vanilla interaction run. For stack blocks the vanilla `useItemOn` returns
`CONSUME`, so it is harmless; for `PlaceAndDepositPkt` clicked against a non‑stack
block, the vanilla placement of the held item can also proceed (through vanilla's
own protection), letting one click both place a block and start a stack. Both
outputs are paid for from the hand, so this is a minor griefing / awkwardness
concern, not duplication. Forge/NeoForge are not exposed because their suppression
is server‑side.

### F7 — Gallery generator bypasses all protection (Informational)

`RenderGalleryGenerator` writes floor and stack blocks with raw
`level.setBlock(...)`, no `WorldEdits`, no claim/border/spawn consult. It is gated
behind `/ss render_gallery`, which requires permission level
`render_gallery.required_permission_level` (default 3) and
`render_gallery.enabled` (default off), and the code comment acknowledges it
"overwrite[s] blocks without" regard. Acceptable for an admin/debug tool; noted so
it is not mistaken for a covered path.

### F8 — Synthetic events used as permission queries can trigger side‑effecting listeners (Informational)

The whole `PlayerEdits` consult layer works by *posting* `RightClickBlock` /
`UseBlockCallback` / `BlockEvent` and reading back the veto flags. This is the
pragmatic norm and the code comments call out the logging case, but any listener
that performs an action from the event object rather than only vetoing (some
pipe/automation mods, note‑block‑style modded blocks) will act on the phantom
interaction. Bounded to one/tick/player by `GestureThrottle`. Inherent to the
approach; listed so the trade‑off is on record. F2 is the subset of this that is
reachable at attacker‑chosen coordinates.

## Untested areas

- The FTB Chunks direct‑consult path (`FtbChunksProtection.prevents`) has no game
  test on any loader (FTB is not a test dependency). The Forge/NeoForge suites do
  exercise `EntityPlaceEvent` / `BreakEvent` / `RightClickBlock` denial with
  ad‑hoc listeners, which is the closest proxy.
- Spawn protection specifically is not staged in game tests (it is
  `DedicatedServer`‑only); only the world‑border half of the shared predicate is
  covered. Noted in the test comments.
- Fabric has no equivalent of the Forge/NeoForge
  `sameTick…` / `depositConsultsTheAdjacentBlockThatWasActuallyClicked` /
  `placementHonorsUseItemDeny…` tests, by design (no counterpart mechanism).

## Explicitly checked, not an issue

- Spectators are rejected at `PacketBoundary.validate`.
- Reach is enforced on the primary position (attribute + 1.0 vanilla slack); the
  deposit's adjacent position is bound to distance 1 from it.
- Creative deposits work from a copied hand stack — no protection relevance, and no
  duplication (the stack contents are the only new copy, matching vanilla creative
  placement).
- `GestureThrottle` and `RightClickBlockSuppressor` per‑player maps are cleared on
  logout on all three loaders.
- `placeChecked` rolls the block back to its previous state if the placement veto
  fires after `setBlock`, so a vetoed growth leaves no orphan block.
- Growth/removal model bookkeeping stays consistent with the world when a removal
  is refused mid‑loop (verified in `settleKeepsAnEmptyTopBlockWhoseRemovalIsRefused`
  and the `trimEmptyTop` / `removeEmptyBelow` break‑on‑refusal logic).
