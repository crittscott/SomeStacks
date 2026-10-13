# Review: Reinvention

## Summary
The main departures are concentrated in the world-edit boundary: direct block changes omit parts of ordinary placement and removal behavior. The most important correction is to preserve water during automatic removal and publish the notifications that vanilla and Fabric normally provide. Fabric's automation actor and the shared obstruction test also replace facilities already available from the platform.

## Findings

### 1. Automatic removal deletes the water inside a waterlogged stack
- **Severity:** High
- **Confidence:** Certain
- **Where:** common/src/main/java/com/github/crittscott/somestacks/server/WorldEdits.java:140; common/src/main/java/com/github/crittscott/somestacks/block/StoragePile.java:409; common/src/main/java/com/github/crittscott/somestacks/block/SinglesStackBE.java:311,325; common/src/main/java/com/github/crittscott/somestacks/block/BarColumn.java:307; common/src/main/java/com/github/crittscott/somestacks/block/BarStackBE.java:227
- **In plain English:** Emptying an underwater stack can remove its water along with the stack. Breaking an ordinary waterlogged block leaves the water behind.
- **Consequence:** Players lose a water source when extraction, settlement, or a bar collapse removes a waterlogged block, potentially changing nearby water flow.
- **Action:** Retain the existing permission/event checks, then use `level.removeBlock(pos, false)` instead of replacing the block with air; the vanilla removal facility restores the position's fluid state. Add a shared GameTest that waterlogs each stack type, empties it through its normal extraction/cleanup path, and checks that source water remains.
- **Technical detail:** All listed cleanup/cascade paths reach `WorldEdits.removeChecked`, which unconditionally calls `setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)`. The three blocks expose source water through `getFluidState` when `WATERLOGGED` is true, but that fluid is discarded by the forced air replacement. None of these callers restores it.

### 2. Structural edits omit vanilla vibration events
- **Severity:** High
- **Confidence:** Likely
- **Where:** common/src/main/java/com/github/crittscott/somestacks/server/WorldEdits.java:123,140; forge/src/main/java/com/github/crittscott/somestacks/server/ForgeEditAuthority.java:87; fabric/src/main/java/com/github/crittscott/somestacks/server/FabricEditAuthority.java:49; common/src/main/java/com/github/crittscott/somestacks/server/StackInteractions.java:232
- **In plain English:** Placing a stack or removing an emptied stack changes the world without the usual placement or destruction vibration. Nearby sculk sensors and vibration-sensitive creatures cannot react as they would to ordinary block placement and removal.
- **Consequence:** Players building sculk mechanisms see stack placement and cleanup remain silent to those mechanisms even though ordinary block edits are detected; mods listening for those same vanilla events also miss the edits.
- **Action:** After accepted structural placement emit `GameEvent.BLOCK_PLACE` with the actual placer and resulting block state; after successful checked removal emit `GameEvent.BLOCK_DESTROY` with the automation actor and captured old state. Put notification at the shared successful-edit boundary, and do not emit it for vetoed or rolled-back edits. Add a GameTest listener that verifies one event for accepted placement/removal and none for a rejected edit.
- **Technical detail:** Both loader placement implementations end in state mutation/publication, and removal ends in a state replacement. No production code references `GameEvent` or `gameEvent`; block callbacks publish inventory/comparator/run changes, not vibrations. Forge's place/break events and Fabric's use/break callbacks are separate from Minecraft's vibration events. The absence is verified in project code; the confidence reflects reliance on known vanilla behavior rather than inspection of prohibited Minecraft/Forge sources.

### 3. Fabric only sends the permission half of a block-break notification
- **Severity:** High
- **Confidence:** Certain
- **Where:** fabric/src/main/java/com/github/crittscott/somestacks/server/FabricEditAuthority.java:59; common/src/main/java/com/github/crittscott/somestacks/server/WorldEdits.java:137,140; common/src/main/java/com/github/crittscott/somestacks/server/EditAuthority.java:29
- **In plain English:** Fabric mods can approve or refuse an automatic stack removal, but never receive the normal follow-up telling them that it happened or was canceled.
- **Consequence:** Server mods that record successful breaks or react after a break miss stack cleanup and cascade removals. Their before-break checks still run, making the missing completion particularly misleading to integrations that track an edit across callbacks.
- **Action:** Extend the edit-authority boundary to carry the old block state and block entity through removal and notify completion. On Fabric, invoke `PlayerBlockBreakEvents.AFTER` after a successful removal and `PlayerBlockBreakEvents.CANCELED` when `BEFORE` refuses it; preserve the original actor, state, and entity in those callbacks. Do not report a successful break when the world mutation fails.
- **Technical detail:** `FabricEditAuthority.vetoesRemoval` invokes only `PlayerBlockBreakEvents.BEFORE`. `WorldEdits.removeChecked` returns immediately on a veto, otherwise performs the mutation and returns its result without a loader completion callback. There are no `AFTER` or `CANCELED` invocations elsewhere in production code, so ordinary removal never finishes the Fabric event sequence.

### 4. Fabric constructs an automation player instead of using Fabric's fake player
- **Severity:** Medium
- **Confidence:** Certain
- **Where:** fabric/src/main/java/com/github/crittscott/somestacks/server/FabricEditAuthority.java:31,35,38; fabric/src/gametest/java/com/github/crittscott/somestacks/gametest/ProtectionGameTests.java:46
- **In plain English:** Automatic stack edits use a homemade pretend player on Fabric, while the project's Fabric tests use the loader's provided pretend player.
- **Consequence:** Integrations that recognize Fabric API fake players cannot recognize this actor through its native type, and the protection tests exercise a different kind of actor from production automation. Maintainers must manage actor identity and lifetime themselves despite an existing loader facility.
- **Action:** Replace the custom profile, weak-map cache, and direct `new ServerPlayer(...)` construction with `net.fabricmc.fabric.api.entity.FakePlayer.get(level)`, the facility already used by this project's Fabric GameTests. Use that actor consistently for growth and removal, and include a test exercising `WorldEdits.automationActor` directly.
- **Technical detail:** `automationActor` caches plain `ServerPlayer` instances under a custom profile. The Fabric baseline demonstrably exposes `FakePlayer.get(ServerLevel)` because current GameTests import and invoke it. The production actor therefore duplicates a loader facility and is not a Fabric `FakePlayer` instance.

### 5. The obstruction check reimplements vanilla's entity/shape test
- **Severity:** Medium
- **Confidence:** Likely
- **Where:** common/src/main/java/com/github/crittscott/somestacks/server/WorldEdits.java:83
- **In plain English:** Stack placement uses its own version of Minecraft's check for whether an entity occupies the space being filled.
- **Consequence:** Maintainers must keep the entity filtering and shape-intersection behavior aligned with Minecraft independently, affecting player placement and automatic growth of all three stack types. Any change to the native obstruction policy must be repeated here or stacks will disagree with other blocks.
- **Action:** Keep translating the completed local shape to world coordinates, then delegate to `level.isUnobstructed((Entity) null, worldShape)`. This retains the special completed shape needed by Singles and Bar while removing the duplicate entity scan and predicate.
- **Technical detail:** The method builds a world-space shape, scans its bounding box, filters removed/non-building entities, and uses `Shapes.joinIsNotEmpty(..., BooleanOp.AND)` to test intersections. The native entity/voxel-shape obstruction overload performs this responsibility; passing the completed shape avoids relying on the newly placed empty block entity's collision shape. The exact platform overload is known from Minecraft API usage, not rechecked against prohibited sources.

## Drift from the expected way
The drift is concentrated in `WorldEdits` and its loader authorities rather than spread throughout the movement models. Direct state mutation replaces ordinary fluid-preserving removal and omits structural notifications (findings 1–3); Fabric's actor and the shared obstruction predicate also duplicate platform facilities (findings 4–5).
