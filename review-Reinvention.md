# Targeted Code Review — Reinvention

Scope: do we duplicate Minecraft/Forge/NeoForge/Fabric functionality, and do we fire the
events other mods expect for what we do? Assessed against the tree as it stands. No changes made.

## Verdict

The mod is disciplined about the *world-state* half of the platform: it drives block updates
through `setBlock(..., UPDATE_ALL)`, exposes comparator output the vanilla way, and routes its
own automated growth/removal through real break/place events. The *interaction* half is almost
entirely rebuilt in-mod, and that reconstruction is where the reinvention — and the missing
events — concentrate. A few loader shims also hand-roll things the loader already provides.

---

## 1. The interaction pipeline is reimplemented wholesale — HIGH

The three stack blocks' own `useWithoutItem` / `useItemOn` do nothing but return
`SUCCESS`/`CONSUME` (`BarStackBlock:142-151`, `StorageStackBlock:118-128`,
`SinglesStackBlock:140-150`). All real interaction runs through a parallel stack:

- client cancels `PlayerInteractEvent.RightClickBlock` / `UseBlockCallback`
  (`forge/.../client/ClientEvents.java`, `neoforge/.../client/ClientEvents.java`,
  `fabric/.../client/FabricClientEvents.java`), plus a Fabric `MinecraftMixin` to synthesize
  the empty-hand air click Fabric lacks;
- a client-side ordered rule engine (`client/interaction/*`, `InteractionRuleRegistry`) that
  re-traces the player's view ray against sub-cell geometry (`ViewRays`, `*CubeIdx.traceCubes`);
- seven bespoke C2S packets (`network/*Pkt.java`) carrying the gesture;
- a shared server boundary (`network/PacketBoundary`) that re-implements: one-interaction-per-tick
  pacing (`server/GestureThrottle`), spectator rejection, chunk-loaded check, and **reach**
  (`PacketBoundary.withinReach` + `util/PlayerReach` + `util/ViewRays`);
- per-loader protection re-consults (`server/Protection` on Forge/NeoForge,
  `server/FabricPlayerEditAuthority`) that *synthesize and post* a
  `PlayerInteractEvent.RightClickBlock` / invoke `UseBlockCallback` so claim/logging mods still
  see something;
- trailing-vanilla-click suppression via a per-player, tick-scoped mark map
  (`server/RightClickBlockSuppressor`, Forge + NeoForge copies);
- creative "don't consume the held item" handling, re-done in every deposit handler
  (`DepositPkt.apply`, `PlaceAndDepositPkt.apply`);
- waterlog-on-placement (`util/StackPlacement`), because no `BlockPlaceContext` is ever built and
  `getStateForPlacement` never runs;
- placement obstruction / build-height / replaceability / `mayInteract` (`server/WorldEdits`).

This is a large surface, and most of it is genuinely *forced* by the core design: vanilla hands
a block interaction a single `BlockHitResult` against the outline shape, not a caller-controlled
ray into a 4×4×4 of sub-cells, and it has no notion of modifier-key gesture modes. Given that,
rebuilding targeting + dispatch is defensible. What is worth being explicit about is the cost:

### Events / hooks that the gesture path does NOT fire

- **Statistics**: `Stats.ITEM_USED`, `Stats.BLOCK_MINED`, custom use counts — never incremented
  for deposit/extract/place/rotate.
- **Advancement criteria**: `CriteriaTriggers.PLACED_BLOCK`, `ITEM_USED_ON_BLOCK`,
  `CONSUME_ITEM`, `INVENTORY_CHANGED` (the last does fire indirectly when the hand stack is
  written back, but not reliably for merges) — no trigger for placing a stack or pulling items.
- **`PlayerInteractEvent.RightClickItem`** (mode-cycle) is consumed with no vanilla-side
  equivalent — acceptable, but note a modpack that keys off right-click-item won't see it.
- **Item cooldowns / `ItemStack.use` side effects** — a deposited item never has `use`/`finishUsingItem`
  called; only relevant if someone stores a usable item, but the asymmetry with vanilla placement
  is real.
- No `BlockEntity`-open analog: there is no container, so no `PlayerContainerEvent` — fine, but
  automation/inspection mods that watch container opens will not see stack access.

### Events that ARE fired correctly (good)

- `PlayerInteractEvent.RightClickBlock` re-posted server-side for every gesture, at the real
  look-hit position (`Protection.lookHit`) — claim/logging mods are covered on Forge/NeoForge.
- `UseBlockCallback` re-invoked on Fabric for the same purpose.
- Automated **removal** → `BlockEvent.BreakEvent` (Forge), `PlayerBlockBreakEvents.BEFORE`
  (Fabric/NeoForge via `WorldEdits.removeChecked` → `EditAuthority.vetoesRemoval`).
- Player- and automation-driven **placement** → `ForgeEventFactory.onBlockPlace` /
  `BlockEvent.EntityPlaceEvent` (Forge/NeoForge), FTB Chunks consulted directly on Fabric
  (`WorldEdits.placeChecked` → `EditAuthority.preparePlacement`).
- World border / spawn protection via `ServerLevel.mayInteract` (`WorldEdits.isProtected`).
- Comparators via `Level.updateNeighbourForOutputSignal` (`StoragePile.publishComparatorSignal`
  and the column equivalents) — the comparator-aware update, not a plain neighbor poke. Correct.
- Dynamic light through a real blockstate `IntegerProperty` wired to
  `Properties.lightLevel(...)` in all three registries — the idiomatic pattern, not a reinvention.

**Recommendation**: none required for correctness, but the "no stats / no advancements" gap
should be a conscious, documented product decision rather than an accident. If any of those
matter, the deposit/extract/place handlers are the single choke points to add them.

---

## 2. `ServerConfig` hand-rolls JSON config instead of the loader's config system — MEDIUM

`ServerConfig` is a bespoke Gson reader/writer with its own defaulting, clamping, list editing,
and save-on-edit (`ServerConfig.java`). Its own doc admits "no automatic file-watch reload
behind this hand-rolled reader/writer."

On Forge and NeoForge this duplicates `ModConfigSpec` / `ModConfig`, which gives comments,
range validation, per-world server configs, `ModConfigEvent` reload, and file watching for
free. The multiloader *common* module genuinely can't reference those — but the project already
has the pattern to solve exactly this: `PlayerReach.setProvider`, `WorldEdits.setAuthority`,
`PlayerEdits.setAuthority`. Each loader entry point could own a `ModConfigSpec` and push values
into a common holder, the same way. As written, an admin on Forge gets a raw JSON file with no
comments and must restart for manual edits — not "the Forge way."

Fabric has no first-party config, so a hand-rolled reader *there* is normal.

---

## 3. `ForgeEditAuthority` builds a bare `ServerPlayer` instead of a FakePlayer — MEDIUM (verify)

`ForgeEditAuthority` (`forge/.../server/ForgeEditAuthority.java:25-36`) constructs a raw
`new ServerPlayer(server, level, PROFILE, ClientInformation.createDefault())` per level in a
`WeakHashMap`, with the comment "Forge 1.21.1 no longer ships a FakePlayer helper."

The NeoForge sibling uses `FakePlayerFactory.getMinecraft(level)`
(`neoforge/.../server/NeoForgeEditAuthority.java:21,39`). `net.minecraftforge.common.util.FakePlayerFactory`
existed through 1.20.x and the project's own notes say Forge 1.21.1 keeps the classic API, so
the "no longer ships" claim is worth verifying against the actual Forge 1.21.1 jar. If the
helper is present, this is a reinvention that should be dropped:

- many mods (and Forge's own hooks) branch on `instanceof FakePlayer`; a bare `ServerPlayer`
  is invisible to all of them, so protection/claim mods may treat automation edits as
  un-attributed rather than as `[SomeStacks]`;
- a raw `ServerPlayer` is a heavy object not on any player list, with connection/advancement
  fields left in a default state — `FakePlayerFactory` exists precisely to make that safe and
  shared.

---

## 4. Fabric ignores the interaction-range attributes — LOW

`PlayerReach` defaults to `instabuild ? 5.0 : 4.5` and expects a loader to override it. Forge
and NeoForge call `PlayerReach.setProvider(p -> p.blockInteractionRange())`
(`SomeStacks.java:52`, `SomeStacksNeoForge.java:49`); **Fabric never sets a provider** (confirmed
by grep). So on Fabric the mod's reach check and its view-ray length ignore
`BLOCK_INTERACTION_RANGE` / any reach-modifying enchant or mod, and use a hardcoded constant.
Vanilla 1.21.1 has the attribute; Fabric should wire `Player.blockInteractionRange()` the same
way the other two do.

---

## 5. Loader-neutral `IItemHandler` / `ItemStackHandler` reimplementation — LOW (acceptable)

`util/SlotAccess` re-declares Forge's `IItemHandler` contract and `util/StackItemStorage`
re-implements `ItemStackHandler` (insert/extract semantics + `{Size, Items:[{Slot,...}]}` NBT).
This is explicitly deliberate and documented, and is unavoidable in a common module that also
targets Fabric (which has no `IItemHandler` at all). NBT shape is matched so saves migrate.
Verdict: justified duplication, leave it. The small hand-rolled inventory helpers in
`ItemOps` (`mergeIntoStack`, `canTakeIntoHand`, `giveToPlayerOrDrop`) are in the same category —
`ItemHandlerHelper.giveItemToPlayer` exists on Forge but not commonly, so a shared util is fine.

---

## 6. Forge and NeoForge modules carry near-verbatim source copies — LOW

`Protection.java`, `RightClickBlockSuppressor.java`, `client/ClientEvents.java`, and the
`PlayerEditAuthority` impls differ between `forge/` and `neoforge/` only by import package
(`net.minecraftforge` ↔ `net.neoforged`) and `Event.Result` ↔ `TriState`. NeoForge additionally
carries its own `FtbChunksProtection` (Forge relies on the block-place event instead). This is
inherent to the ForgeGradle/MDG split rather than a design fault, but it is a standing
maintenance multiplier — a fix to the interaction-suppression logic has to land three times
(counting Fabric's variant). Worth noting; not worth forcing a shared module for.

---

## 7. Smaller items

- **`StackSoundData`** (`server/StackSoundData.java`) is a bespoke
  `SimpleJsonResourceReloadListener` with its own pack-merge/override-ordering (namespace sort,
  per-action layering). Vanilla has no "sound per mod action" facility so this isn't reinventing
  one, but the merge/precedence logic is hand-rolled where a plain data file per block could do.
  Minor.
- **Client render config has two parallel hand-rolled IO paths**: `ItemRenderOverrides` reads a
  `config/somestacks/item_overrides.json` directly off disk (`ItemRenderOverrides.ensureUserFileLoaded`)
  *and* consumes resource-pack `item_render_overrides/*.json` through a reload listener, *and*
  receives a server-synced layer. Three sources, two bespoke Gson loaders (this one and
  `ServerConfig`), each with its own error handling. The 2D face-projection rendering in
  `CubeRenderHelper` itself is real novel work, not reinvention — but the config plumbing around
  it is a second hand-rolled config stack.
- **`useWithoutItem` / `useItemOn` unconditionally return `SUCCESS`/`CONSUME`** even when no
  gesture rule matched. Harmless given the mod is required on both sides, but it means the block
  swings the arm / plays the hand animation for clicks the mod ignored.
- **`GestureThrottle` / `RightClickBlockSuppressor` static maps** are cleared on logout on all
  three loaders (Forge/NeoForge `PlayerLoggedOutEvent`, Fabric `DISCONNECT`), so no leak — noted
  only because it's adjacent to the reinvented pacing.

---

## Summary table

| Area | Severity | Reinvention? | Events OK? |
|---|---|---|---|
| Interaction dispatch (client rules + packets + server re-consult) | HIGH | Yes, largely forced by design | RightClickBlock/UseBlockCallback re-fired; **no stats/advancements** |
| `ServerConfig` JSON reader/writer | MEDIUM | Yes on Forge/NeoForge (`ModConfigSpec`) | n/a |
| `ForgeEditAuthority` bare `ServerPlayer` | MEDIUM | Yes if `FakePlayerFactory` exists on 1.21.1 (verify) | placement/break events fire, but not seen as FakePlayer |
| Fabric reach not wired to attribute | LOW | — | — |
| `SlotAccess` / `StackItemStorage` | LOW | Yes, but justified by multiloader | NBT-compatible |
| Forge/NeoForge duplicated source | LOW | Copy-paste, inherent to split | — |
| Sound data / render-override config plumbing | LOW | Partial | — |
| Comparator output, dynamic light, automated place/break events | — | No — done the idiomatic way | Yes |
