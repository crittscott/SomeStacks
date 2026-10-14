# Backport plan: bring 1.20.1 behavior in line with 1.21.4

Source: `version-differences.md`. Every claim there was checked against both trees (`1.20.1/` and `../1.21.4/`). This file records the verification results, the owner's decisions, and the order of work. All phases are done.

All three versions will eventually ship as 0.8.3, but only after they are as equivalent as possible. 1.20.1 is already at 0.8.3. No earlier 1.20.1 version exists, so no saved-data or config migration is needed: formats change in place.

## 1. Verification results

| Item | Verdict | Notes |
| --- | --- | --- |
| 1.1 BLOCK_CHANGE on use | Confirmed | 1.21.4 `StackInteractions` emits after deposit, extraction (only if the same block entity survives), block rotation, item rotation, and the permanence toggle. Place-and-deposit emits only `BLOCK_PLACE`. |
| 1.2 Waterlogged removal | Confirmed | 1.20.1 `WorldEdits.removeChecked` uses `setBlock(AIR)`; 1.21.4 uses `removeBlock(pos, false)`. |
| 1.3 Cleanup actor | Confirmed, minor correction | Plumbing is as described. `BarStackBE.collapseAbove(level, removed, drops)` has no actor parameter, so removal after a player breaks a Bar block always goes to automation. |
| 1.4 Unreadable saved items | Confirmed | 1.20.1 `StackItemStorage.deserializeNBT` uses `ItemStack.of` and drops failures; 1.21.4 keeps `SetAside` and reports via `SomeStacksServer.reportSavedItems`. |
| 1.5 Bar admission | Confirmed, correction | The 1.21.4 tag has 29 entries: three optional tags (`#forge:ingots`, `#c:ingots`, `#c:bricks`), the vanilla ingots/bricks listed explicitly, and about 19 modded items. It includes `minecraft:resin_brick`, which does not exist in 1.20.1. |
| 1.6 Facing in block state | Confirmed | `StackBlock.HORIZONTAL_FACING` on Storage/Singles with `rotate`/`mirror`. 1.21.4's NBT-to-state conversion is 1.21.1 migration and is not ported. |
| 1.7 Grown block facing | Claim corrected | 1.21.4 does not leave the grown block at `NORTH`. `StorageStackBlock.onPlace` calls `StoragePile.adoptNeighbourState`, which copies the block directly below, which is the old top. 1.20.1 does the same and then overwrites it with the base's rotation. The versions differ only when blocks in one pile were rotated individually. Decision: keep 1.20.1. |
| 1.8 Gesture routing | Confirmed | 1.21.4 `StackBlock.useItemOn`/`useWithoutItem` → `handleExistingStack`. Loader events handle only `handleSneakingRotation` and `handleAdjacentClick`. 1.20.1 has `GestureThrottle`, `withinReach`, and `ViewRays.of`. |
| 1.9 Fabric air-click | Confirmed | 1.20.1 `MinecraftMixin` requires both hands empty. |
| 2.1 `/ss reload` | Confirmed | 1.21.4 runs `ServerConfig.reload()` → `ConfigSyncPkt.rebuildCurrent()` → sync all. |
| 2.2 TOML reload resync | Confirmed | 1.20.1 resyncs only when an enable flag changes. |
| 2.3 Default `disable_items` | Confirmed | Not rechecked whether `evilcraft:broom_part` exists in 1.20.1 EvilCraft. It is harmless if it doesn't. |
| 2.4 TOML comments | Confirmed | |
| 2.6 Galleries | Confirmed | Sorting, `isItemDisabled` filtering, and the one-line summary log are in 1.21.4. 1.20.1 `ServerLifecycle` calls `RenderGalleryGenerator.clear()` on start and stop. |
| 2.7 Help | Confirmed | 1.21.4 `SsHelp.usages` reads the Brigadier tree plus `ClientRenderCommandSyntax`. Decision: keep 1.20.1's lang-key usage lines. |
| 3.1 Client `/ss item`/`write` | Confirmed | 1.21.4 registers through Forge `RegisterClientCommandsEvent` and Fabric `ClientCommandRegistrationCallback`. Both APIs exist on 1.20.1 baselines. |
| 3.2 Server override layer | Confirmed | `ServerOverridesLoader` and override fields in `ConfigSyncPkt` exist only in 1.20.1. |
| 3.3 Error cube | Confirmed | The 1.21.4 path depends on `ItemCapture`, which is built on 1.21.4 item-model capture. The behavior (catch, remember, draw error texture, log once) is portable, but the capture mechanism is not. 1.20.1 also lacks `textures/block/stack_cube.png`. |
| 3.4 Bar tint | Confirmed | 1.20.1 `BarTextureStore` uses `model.getParticleIcon()`. |
| 3.5 Measured-cache logs | Confirmed | 1.20.1 has a per-item `debug` line at `AutoRenderProfiles:279`. |
| 4.1 Automation identity | Confirmed | 1.20.1 Forge `FakePlayerFactory.getMinecraft`, Fabric `FakePlayer.get(level)`. |
| 4.2 / 4.3 Fabric protection | Confirmed | 1.21.4 `FabricProtectionQueries` / `CommonProtectionQueries`, `EditAuthority.mayPlace`, and `WorldEdits.mayPlace` used by `canGrow`. |
| 4.4 Forge place event | Confirmed | 1.20.1 follows Forge's own convention (`onMultiBlockPlace` for more than one snapshot). Decision: keep 1.20.1. |
| 5.1 Sounds | Confirmed | |
| 5.4 Lang | Confirmed | 1.20.1 also has `command.help.reload.detail.2`, which 1.21.4 lacks. |
| 6 Client update validation | Mislabeled | It is not version-forced: 1.20.1 receives updates through `BlockEntity.load` and Forge `handleUpdateTag`/`onDataPacket`, and can validate there. Decision: port it. |

## 2. Owner decisions

- **1.7:** keep 1.20.1. A grown Storage block copies the base's facing.
- **2.6 gallery cleanup:** keep 1.20.1's clearing on server start and stop.
- **4.1:** `[SomeStacks]` through `FakePlayerFactory.get(level, PROFILE)` / `FakePlayer.get(level, PROFILE)`.
- **4.2 / 4.3:** add Common Protection API alongside the direct FTB Chunks and Open Parties and Claims integrations.
- **4.4 (D3):** keep 1.20.1's multi-snapshot event.
- **5.2 / 5.3:** no changes to render-profile JSON or Bar mappings.
- **Section 6 (D1):** port client update validation.
- **D4:** with gestures in `Block.use`, a Forge listener that denies item use but allows block use no longer blocks a deposit into an existing stack, or a placement started by clicking one. This matches 1.21.4. A click on a non-stack block still goes through the loader event and keeps its item check.
- **D5:** remove `GestureThrottle`, as 1.21.4 does.
- **D6:** help usage stays in lang keys (`somestacks.command.help.<cmd>.usage.N`). The content follows 1.21.4.
- **D7:** Forge policy stays native `SERVER` TOML; Fabric stays world JSON.
- **No migration:** there is no earlier 1.20.1 version, so nothing converts old NBT or config.

## 3. Work order

Each step ends with GameTests (javadoc gives in-game reproduction steps), lang changes, and updates to `orientation-player.md` / `orientation-code.md`. Out of scope: 5.2, 5.3, and the items kept as 1.20.1 above.

### Phase 1: small independent changes

**Status: done.**

1. **4.1 Automation identity.** Add common `server/AutomationActor` (profile `[SomeStacks]`, UUID from `somestacks:automation`). Forge: `FakePlayerFactory.get(level, PROFILE)`. Fabric: `FakePlayer.get(level, PROFILE)`. Test: the actor's name and UUID on both loaders.
2. **1.2 Water survives removal.** `WorldEdits.removeChecked` → `level.removeBlock(pos, false)`. Test: empty a waterlogged temporary Storage block; water source remains.
3. **1.3 Cleanup actor.** Port `WorldEdits.removeChecked(actor, level, pos)`, `StorageStackBE.noteCleanupActor`/`takeCleanupActor`, `StoragePile.markDirtyBy`/`setPermanent(permanent, actor)`/`trimEmptyTop(actor)`, and `SinglesStackBE.extractAt(index, actor)`/`BarStackBE.extractAt(index, actor)` through their removal paths. Reorder to `afterRemoval` before the game event, as in 1.21.4. Tests: Fabric break callbacks and `BLOCK_DESTROY` carry the player for player-caused trims; mixed causes fall back to automation.
4. **1.1 BLOCK_CHANGE.** Add `emitBlockChange`/`emitSurvivingBlockChange` to `StackInteractions`. Test: a sculk sensor triggers after deposit, extraction, both rotations, and the toggle, and not when the last Single/Bar is extracted (destroy only).
5. **1.9 Fabric air-click.** Drop the off-hand check in `MinecraftMixin`. A client behavior; covered by the javadoc'd manual scenario.
6. **2.3 Default `disable_items`.** Add `ServerConfig.DEFAULT_DISABLE_ITEMS = ["evilcraft:broom_part"]` and use it on both loaders.
7. **2.6 Galleries.** Sort items by id, sort `list` namespaces, filter `disable_items` from every form and `/ss gallery items`, drop empty namespaces in `all`, and log a single "skipped N of M" summary. Keep `clear()` on start and stop. Remove the now-unused `skip.from_disabled_mods` key if nothing references it.
8. **3.5 Measured-cache logs.** Info-level milestones on load, discard, and reload; remove the per-item debug line.

### Phase 2: persistence and the client trust boundary

**Status: done.** Saves now carry `DataVersion`, which the client check requires. `Items.Size` is no longer written. The client path is the single `load` override: every loader's client update hook reaches it, so Forge needs no separate `handleUpdateTag`/`onDataPacket` override. The build and GameTests have not been run yet.

9. **1.4 Set-aside items.** Port `StackItemStorage` `SetAside` and `LoadResult`, plus a server-side `reportSavedItems` (dimension and position, logged once per location), in `ServerLifecycle`. On 1.20.1, a stack can't be parsed when `ItemStack.of` yields empty for a nonempty `id`, or when parsing throws (malformed id). Catch the exception and set the tag aside. Test: an unknown item id survives save and load verbatim, and comes back when the id resolves.
10. **Section 6 client update validation.** Port `loadClientUpdate`, `StackItemStorage.deserializeClientUpdate`, and `stripServerOnlyUpdateData`. The client path in `load` (Fabric) and Forge `handleUpdateTag`/`onDataPacket` checks `DataVersion`, rejects `SetAside`/`Permanent`/`Rotation`, decodes into a scratch array, then commits. Update tags strip `Permanent` and `SetAside`. Extend `PacketCodecTest` or add GameTests for rejected and accepted update tags.
11. **1.6 Facing in block state.** Add `HORIZONTAL_FACING` to Storage and Singles, with `rotate`/`mirror` overrides. Port `blockRotation`/`setBlockRotation`/`pendingBlockRotation` (deferred application until the block entity has a server level), and stop saving `Rotation` in NBT. Blockstate JSON `""` variants still cover every state. Keep 1.7: `StoragePile.grow` sets the grown block's facing from the base after placement. Tests: rotation persists as state; a structure rotated 90° turns the layout; growth copies the base's facing.

### Phase 3: Bar admission and policy

**Status: done.** `ServerConfig.start` takes a loader policy reader, and `ServerConfig.reload()` uses it. The gallery's Bar index reads the tag on each call, so there are no tag-reload hooks. The gallery command GameTest uses the vanilla ingots in place of its test-only tag. `docs/` was updated as well. The build and GameTests have not been run yet.

12. **1.5 Tag-only Bar admission.** `BarStackBE.INGOTS` = `somestacks:ingots`, and `isValidBarItem` uses `stack.is(INGOTS)`. Remove `ingot_tags` (`Settings`, Forge spec, Fabric JSON), the baking and its tag-reload hook, `/ss ingot`, and the related lang and help. Port the 1.21.4 tag file without `minecraft:resin_brick`. Gallery Bar index: compute from the tag, as 1.21.4 does. Tests: vanilla ingots, brick, and nether brick are Bar items on both loaders; a non-tag item is refused.
13. **2.4 TOML comments.** Use the 1.21.4 comment text (written after step 12 so it describes the tag).
14. **2.1 / 2.2 Reload.** `/ss reload` reloads policy (Forge: current TOML values; Fabric: reread JSON), rebuilds the sync snapshot, and resyncs. A Forge TOML reload resyncs on any policy change. Update `command.reloaded` and the reload help strings. Tests: a reload changes the policy, and a changed policy resyncs.

### Phase 4: interaction model

**Status: done.** Each stack block's `use` calls `handleExistingStack`; there is no shared stack-block base class. `GestureThrottle` became `RotationSoundThrottle`, which keeps only the rotation-sound limit. Gesture GameTests now click through vanilla's `ServerPlayerGameMode.useItemOn`, so the loader event and `Block.use` both run. The build and GameTests have not been run yet.

15. **1.8 Gestures through the block.** Override `Block.use` (the 1.20.1 equivalent of `useItemOn`/`useWithoutItem`) in a shared stack-block base and call `handleExistingStack` with `ViewRays.through(player, hit.getLocation())`. The Forge `RightClickBlock` and Fabric `UseBlockCallback` handlers keep only the sneaking rotation and adjacent clicks. Remove `GestureThrottle` (D5), `withinReach`, and the extra loaded checks; vanilla's use-packet validation covers reach and loaded chunks. Keep `PlayerReach` for ray length. Re-check the client-side `ClientGestures` consumption against the new server path. Tests: existing gesture tests rerouted; hit-point targeting under a stale server view; D4 item-denial behavior.

### Phase 5: client tools and appearance

**Status: done.** The command syntax lives in `ClientRenderCommands`; there is no separate syntax class, because help usage stays in lang keys (D6). `ConfigSyncPkt.snapshot` builds a new generation for each login or broadcast instead of caching one. `/ss gen` and `/ss deny` edits to `gen_mods` or `disable_mods` resync players, because a Forge TOML save that matches the running policy does not. The protocol is 4 on both loaders. Bar tints use the existing `ClientRenderPlatform.renderPasses` seam, so no loader code was added. `item.detail.4`, `reload.summary`, and `command.reloaded` no longer mention server overrides, unlike 1.21.4's text. The build and GameTests have not been run yet.

16. **3.2 Remove server render profiles.** Delete `ServerOverridesLoader`, its fields in `ConfigSyncPkt`, and the server layer in `ItemRenderOverrides` (order: user > resource pack > measurement).
17. **3.1 Client-side `/ss item` and `/ss write`.** Port `ClientRenderCommands`, `ClientRenderCommandSyntax`, and `ClientRenderToolState`. Register through Forge `RegisterClientCommandsEvent` and Fabric `ClientCommandRegistrationCallback`, with pass-through for server `/ss` forms. `ConfigSyncPkt` carries `gen_mods`/`disable_mods` in byte-bounded chunks. Delete `RenderOverridePkt`, `WriteOverridesPkt`, their handlers, and the server command branches. Bump the protocol version.
18. **2.7 / 5.4 Help and lang.** Keep the lang-key usage mechanism (D6). Align the content with 1.21.4: drop the `ingot` entry and the "operator, in game only" gate, rewrite the `item`/`write` usage lines for the client commands, and align the `reload` and `write.detail.4` strings and `command.reloaded`.
19. **3.3 Error cube.** Copy `stack_cube_error.png`. Wrap the 1.20.1 2d capture in a catch that records the item in `FLAT_CAPTURE_FAILURES`, draws the error texture, logs once, and clears on resource reload.
20. **3.4 Bar tint.** Derive from the first quad the item draws in the GUI context times that quad's `ItemColors` tint. Each loader gets the quads through its own model access (Forge render passes, Fabric `getQuads`), next to the existing measurers.

### Phase 6: sounds

**Status: done.** The sound suppliers are `CommonRegistry` fields assigned by each loader, the same way as the blocks. The build has not been run yet.

21. **5.1 Registered sound events.** Register nine `SoundEvent`s (`somestacks:block.<type>.<action>`) through Forge `DeferredRegister` and Fabric `Registry.register`. Add `assets/somestacks/sounds.json` and the `subtitles.somestacks.*` keys. Delete `StackSoundData`, `FabricStackSoundData`, and `data/somestacks/somestacks_sounds/`. Callers use the `CommonRegistry` accessors.

### Phase 7: Fabric protection

**Status: done.** CPA 1.0.0 (the same version as 1.21.4) is `modCompileOnly`; `-PwithCommonProtectionApi=true` adds it to the runtime and swaps `NoProtectionApiGameTests` for `ProtectionApiGameTests`. The build and GameTests have not been run yet.

22. **4.2 / 4.3 Common Protection API alongside FTB/OPAC.** Add `EditAuthority.mayPlace` (Forge: true) and `WorldEdits.mayPlace`. Fabric `mayPlace` ANDs FTB Chunks, OPAC, and CPA. `place` calls it before `setBlock`, and `mayUseAdjacent` adds `CommonProtection.canInteractBlock`. Keep CPA types in a loaded-mod-gated class. `canGrow` calls `mayPlace`, so claimed headroom isn't advertised. Add CPA as `modCompileOnly` (version to confirm for 1.20.1) and to `suggests`. Tests: with and without CPA, following 1.21.4's gametest entrypoints.
