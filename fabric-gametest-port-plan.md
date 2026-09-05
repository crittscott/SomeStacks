# Fabric GameTest port to 1.21.1 — working plan

Multi-session plan. Update the **Progress** boxes as phases complete. Delete this file once
`:fabric:runGameTestServer` is green and `as-built.md` records the new state.

Scope: the **Fabric** GameTest suite only. Forge/NeoForge gametests and `common/src/test`
(JUnit) are explicitly out of scope (see bottom).

Do not build/run yourself — ask the user to run the Gradle steps and report output.

## Current state (gap analysis)

The Fabric GameTest code is further along than "not ported" implies, but none of it has been
compiled or run against 1.21.1, and it was never finished as a set.

| Piece | State |
|---|---|
| `fabric/build.gradle` gametest wiring (`sourceSets.gametest` pulling `common/src/gametest/java`, `loom.runs.gameTestServer`, `generateGameTestStructures`) | Present, **unchanged since 1.20.1**. Never re-resolved under Loom 1.17.491 / 1.21.1. |
| `common/src/gametest/*Checks.java` (9 files, loader-neutral) | 5 files got Data Components edits in `388b766`; the other 4 read clean. **Never compiled against the ported `common/src/main`.** Shared with the still-1.20.1 Forge/NeoForge suites. |
| `fabric/src/gametest/*GameTests.java` + `FabricGameTestSupport` + `fabric.mod.json` | **Untouched by every porting commit.** Reads as 1.21.1-style (Transfer API, `CustomPacketPayload` packets, `FakePlayer`, `FabricGameTest` v1) but written by analogy, unverified. |
| `fabric/src/gametest/fixtures/somestacks_empty.nbt.b64` | DataVersion **3465 (1.20.1)**; 1.21.1 is 3955. Identical copies in `forge/` and `neoforge/`. |

## Phases

### Phase 0 — Prove the harness resolves — Progress: [x]

Done 2026-09-05: all three loaders' `runGameTestServer` resolve; build script and
`loom.runs.gameTestServer` accepted under Loom 1.17.491 / 1.21.1. `net.fabricmc.fabric.api.
gametest.v1.FabricGameTest` and the `fabric-gametest` entrypoint list resolved without change.
Compilation now proceeds into Phase 1 code errors.


- User runs `:fabric:compileGametestJava` (expect compile errors; goal is that Gradle/Loom
  *accepts the build script* — the `sourceSets.gametest` and `loom.runs.gameTestServer`
  blocks — under 1.21.1).
- **Highest-risk wiring item:** `net.fabricmc.fabric.api.gametest.v1.FabricGameTest` and the
  `fabric-gametest` entrypoint list in `fabric/src/gametest/resources/fabric.mod.json`.
  Confirm the class/module name and `@GameTest` attribute names (`template`, `timeoutTicks`)
  against Fabric API `0.116.15+1.21.1` specifically. Adjust the entrypoint key or the
  interface if the API moved. This is the one item not settleable by reading this repo.

### Phase 1 — Compile `common/src/gametest` against ported `common/src/main` — Progress: [x]

Confirmed 2026-09-05: `:fabric:compileGametestJava` succeeds after the single
`getAnalogOutputSignal` fix. None of the residual-risk items below actually broke.


Full read-audit done 2026-09-05 (see session log). Every `somestacks` API the checks call
still exists with the same signature — the mod's `common` surface was ported in place and the
tests use it verbatim. The Data Components migration for the checks was done in `388b766`.
**Only real break found: `Block.getAnalogOutputSignal` protected access (3 sites, fixed).**

Residual risk — assessed valid at 1.21.1, unconfirmed without a compile; if the recompile
throws, look here first:

- `CustomData.update(DataComponentType, ItemStack, Consumer<CompoundTag>)` — `BarColumnChecks`
  lines 214, 216. Believed present since 1.20.5. Fallback: build the tag then
  `stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))`.
- `Level.getMinBuildHeight()` — `ProtectionGameTests` ~line 52. Present at 1.21.1, renamed
  `getMinY()` at 1.21.2.
- `EntityType.COW.create(Level)` — `ProtectionGameTests.putCowIn`. Present at 1.21.1, gains an
  `EntitySpawnReason` arg at 1.21.2.
- `GameTestAssertException(String)` — `GameTestScaffold`. Present at 1.21.1 (Component-based
  ctor is 1.21.5).
- Unused `net.minecraft.nbt.CompoundTag` import left in `BarColumnChecks` after the `388b766`
  Data Components pass — warning only, not touched (surgical-changes rule).

Earlier checklist (all verified present, kept for reference):

- `CapabilityAndPersistenceChecks` — already on `loadCustomOnly(getUpdateTag(registries),
  registries)` + `CustomData`. Verify `StorageStackBE.getUpdateTag(HolderLookup.Provider)`
  (present at `StorageStackBE.java:336`) is custom-only so `loadCustomOnly` round-trips it.
- `BarColumnChecks` — now uses `CustomData`; the `net.minecraft.nbt.CompoundTag` import is
  likely dead (warning) — remove while there.
- `GameTestScaffold`, `ItemOpsChecks`, `PacketBoundaryChecks`, `RenderGalleryChecks`,
  `SinglesColumnChecks`, `StackSortChecks`, `StoragePileChecks` — confirm each
  `common/src/main` symbol still exists with the same signature: `SlotAccess.getSlots /
  getStackInSlot`, `StackItemStorage.setStackInSlot`, `ExtractPkt.handleStorage/Singles/
  BarExtract`, `PacketBoundary.withinReach / mainHandEmpty / holdsInMainHand`,
  `RenderGalleryGenerator.enqueue / Plan / Result / Kind`, `*StackBE.depositAt / extractAt /
  getItems / setRotation / setCubeRotation`, `SinglesCubeIdx.*`, `ViewRay`.
- 1.21.1 vanilla API to spot-check: `Level.getMinBuildHeight()` (exists in 1.21.1, gone in
  1.21.2 — fine), `EntityType.COW.create(Level)` (fine in 1.21.1),
  `GameTestHelper.absolutePos / runAfterDelay / assertBlockPresent / setBlock / succeed`.

### Phase 2 — Compile `fabric/src/gametest` — Progress: [x]

Confirmed 2026-09-05: compiles clean with no edits — the Fabric suite, `FabricGameTestSupport`,
`fabric.mod.json` entrypoints, and Transfer API usage were all already 1.21.1-shaped.


- `FabricGameTestSupport` — verify the Transfer API surface: `ItemStorage.SIDED.find(
  ServerLevel, BlockPos, BlockState, BlockEntity, Direction)`, `SlottedStorage` /
  `SingleSlotStorage` / `Transaction.openOuter`. Stable 1.20.1→1.21.1; low risk.
- The 9 `*GameTests` classes are thin delegators plus inline Transfer-API tests in
  `CapabilityAndPersistence`, `BarColumn`, `SinglesColumn`, `StoragePile`, `Protection`.
  Verify `FakePlayer.get(ServerLevel)`, `player.getAbilities().instabuild`,
  `player.setItemInHand`, and packet entry points `DepositPkt.apply`,
  `PlaceAndDepositPkt.apply`, `WorldEdits.placeChecked`, `BlockType.STORAGE_STACK` against
  current `common/src/main`.
- Fix compile errors from Phases 1–2.

### Phase 3 — Structure fixture — Progress: [x] (path fix; b64 left as-is)

Done 2026-09-05. The real issue was **not** the DataVersion — 1.21 renamed the data-pack
folder `structures` → `structure` (singular; part of the 24w21a folder singularization).
All three `build.gradle` `gameTestStructureOutput` paths pointed at
`data/somestacks/structures/…` and vanilla `StructureUtils` in 1.21.1 looks under
`data/somestacks/structure/…`. Fixed the path in `fabric/`, `forge/`, `neoforge/`
`build.gradle`.

The `somestacks_empty.nbt.b64` is a **gzipped** NBT (magic `1f8b`), inflates to 114 bytes,
`DataVersion 3465` (1.20.1). Left as-is: it is an empty template, so DataFixerUpper upfixes it
with nothing to migrate. Regenerate only if a load/DFU error appears in a later run.

`forge/src/gametest/resources/pack.mcmeta` still has `pack_format: 15` (1.20.1) — bump to 34
when the Forge gametest suite is ported (out of scope here; Fabric's dev mod uses
`fabric.mod.json` and needs no `pack.mcmeta`).

### Phase 4 — Run and triage — Progress: [~]

- 2026-09-05: First `:fabric:runGameTestServer` — 74 tests discovered and started; server
  crashed immediately with `Missing test structure: somestacks:somestacks_empty` (the Phase 3
  folder-rename bug). Path fixed; awaiting re-run.
- User runs `:fabric:runGameTestServer`.
- Expect behavioral failures independent of compilation: settlement timing, comparator math,
  the `outsideTheBorder` world-border dance, `runAfterDelay` tick budgets
  (`RenderGalleryGameTests` uses `timeoutTicks = 100`).
- For each: decide whether the expectation is stale for 1.21.1 behavior or the mod regressed
  during the port. Fix in the shared `*Checks` file or the Fabric wrapper as appropriate.

### Phase 5 — Docs — Progress: [ ]

- Update `as-built.md` §"Build and test layout": today it says the Forge/Fabric `test` source
  sets are empty and only `:fabric:build` succeeds. Once the Fabric gametests run, record that
  `:fabric:runGameTestServer` is green and `common/src/gametest` is on 1.21.1, while
  Forge/NeoForge gametests and `common/src/test` remain unported.
- Delete this plan file.

## Out of scope (flag for later, separate effort)

- Forge + NeoForge gametest suites (`@GameTestHolder` / static-method style, `IItemHandler`).
  The NeoForge suite has no source yet — only `neoforge/build.gradle` wiring and the fixture.
- `common/src/test` JUnit suite — still 1.20.1 API.

## Session log

- 2026-09-05: Plan written. No code changes yet.
- 2026-09-05: Phase 0 done — harnesses resolve. Phase 1 started. First 1.21.1 break:
  `Block.getAnalogOutputSignal(BlockState, Level, BlockPos)` is now `protected` in
  `BlockBehaviour`, so external callers must go through the public delegating
  `BlockState.getAnalogOutputSignal(Level, BlockPos)`. Fixed in `GameTestScaffold.signalAt`
  and `StoragePileChecks.comparatorReadsTheWholePileFromEveryBlock` (3 sites).
- 2026-09-05: Full read-audit of all 9 `*Checks.java` + all 9 fabric `*GameTests.java` +
  `FabricGameTestSupport` against `common/src/main` and 1.21.1 vanilla / Fabric API. Grep-
  verified every `somestacks` symbol the tests call: `*StackBE` (deposit/depositAt/extractAt/
  pile/column/getItems/get*Rotation/set*Rotation/getCachedShape/SLOTS/isValid*Item),
  `StoragePile` (at/height/maxHeight/getSlot/settle/setPermanent/comparatorSignal),
  `Singles/BarColumn.maxHeight`, `StackItemStorage` ctor + insert/extract/get/setStackInSlot,
  `SlotAccess`, `ItemOps` (canTakeIntoHand/mergeIntoStack/isHandlerEmpty/
  calculateLightLevelFromItems), `StackSort.COMPARATOR`, `SinglesCubeIdx` (indexFromColumn/
  storageColumnFromVisual/traceAllPositions 4-arg), `BarCubeIdx` (isGroundedIn/seamSupports),
  `ViewRay(Vec3,Vec3)`, `BlockType.STORAGE_STACK`, `DepositPkt`/`PlaceAndDepositPkt`/
  `ExtractPkt`/`PacketBoundary`/`WorldEdits.placeChecked`/`RenderGalleryGenerator` (enqueue/
  Plan/Result/Kind), `StorageStackBlock.WATERLOGGED`, `CommonRegistry.*` (`Supplier<Block>`).
  All match. No further compile breaks identified by reading. Main uses
  `ItemStack.isSameItemSameComponents` for identity; `RegistryAccess` implements
  `HolderLookup.Provider` at 1.21.1 so `registryAccess()` feeds `getUpdateTag`/`loadCustomOnly`.
  Next: user re-runs `:fabric:compileGametestJava`; triage against the residual-risk list in
  Phase 1.
- 2026-09-05: `:fabric:compileGametestJava` clean (common + fabric gametest both compile; the
  `getAnalogOutputSignal` fix was the only code change needed). `:fabric:runGameTestServer`
  then started 74 tests and crashed on `Missing test structure: somestacks:somestacks_empty`.
  Cause: 1.21's data-pack folder rename `structures` → `structure`. Fixed the
  `gameTestStructureOutput` path in all three loaders' `build.gradle`. Fixture b64 confirmed
  gzipped NBT, DataVersion 3465 — left as-is (empty template, DFU handles it). Noted stale
  `pack_format: 15` in `forge/src/gametest/resources/pack.mcmeta` for the later Forge port.
  Next: re-run `:fabric:runGameTestServer`; begin per-test behavioral triage.
