# Issues fixed in the 26.1 branch: check the other 26.x repos

Each item below was a confirmed issue in the 26.1 code and has been fixed there. The other 26.x repos share almost the same code, so each issue probably exists there too. For each item, first confirm that it applies (the **Check** line), then apply the fix in that version's idiom. Class and method names are from 26.1. Where an API differs in your version, use that version's equivalent and keep the intent.

All of these were built and passed both loaders' GameTests on 26.1.

Two review findings were deliberately left unfixed; do not port them:

- Fabric automated growth does not consult `UseBlockCallback` for claim protection. This is intentional: Fabric has no placement event, and firing a block-use event for a synthetic placement would be unexpected.
- There are no GameTests for the client profile commands and save/load cycle. These were deferred.

Two items were reviewed and rejected; leave them alone:

- Trimming the long Javadocs on `RunEdit` and `StoragePile.insertAt`.
- Changing Bar collapse under protection. Bars drop on support loss even when the block's removal is vetoed; this is deliberate and commented in `BarStackBE.cascadeFrom`.

## 1. Fabric transactions ignored their own staged changes (highest priority)

**Check:** In `FabricRunItemStorage`, `RunSlot.insert` decides whether a slot can accept an item by calling the live `run.insertAt(slot, stack, true)`, often through a `canInsert` helper. That check reads live block-entity slots, not the transaction's staged contents. As a result, one Fabric transaction cannot do either of these:

- fill a Singles/Bar layer and then the layer above it;
- extract a Storage slot and insert a different item into it.

NeoForge can do both.

**Fix:**

- **Shared check.** Add `int acceptsAt(int slot, ItemStack stack, IntFunction<ItemStack> contents)` to `StackRunItemAccess`. It answers whether the position would accept the item if every run position held what `contents` reports. Growth checks (`canGrow`) still read the live world.
  - Implement it in `StoragePile` (replaces `roomAt`), `SinglesColumn` (replaces `cellAccepts`/`seamUnder`) and `BarColumn` (replaces `positionAccepts`/`seamUnder`).
  - `insertAt(..., simulate)` now starts with `acceptsAt(slot, stack, this::getSlot)` and returns early on simulate or zero.
- **Singles support.** Reject an occupied target first.
  - Above the bottom layer, the supporting cell is `slot - LAYER_SIZE` in the same block.
  - The bottom layer of block 0 is grounded.
  - The bottom layer of a higher block maps its storage column through `visualColumnFromStorage(column, thisRotation)`, then `storageColumnFromVisual(visual, lowerBlockRotation)`, to the lower block's top-layer cell.
  - A headroom block has rotation 0.
- **Bar support.** Add a static table `BarCubeIdx.SUPPORTERS[index]`, exposed as `supportersOf(index)`. For each position it lists the overlapping positions in the layer beneath; for layer 0, those are top-layer positions of the block below.
  - `acceptsAt` checks only those positions through `contents`.
  - Rewrite `isGrounded`, `isGroundedIn` and `seamSupports` to use the table instead of rebuilding footprints and full occupancy arrays.
- **Fabric adapter:**
  - Replace `canInsert` with `acceptedAt(slot, stack) = run.acceptsAt(slot, stack, this::currentStack)`, and clamp the amount to it.
  - Refuse any insertion while `structuralExtractionSlot >= 0`.
  - Refuse a structural extraction when another slot has a staged net change. Add an `otherSlotPending(slot)` helper that compares `staged` against `originals` with `ItemStack.matches`.
  - Rewrite `onFinalCommit` to match NeoForge's `onRootCommit`: first extract every net removal, then insert net additions in ascending slot order. Throw `IllegalStateException` if a committed extract or insert moves a different amount, and also on a reentrant `RunEdit.begin()` failure. Resolve `blockEntity.itemRun()` afresh for each operation, because growth changes the run.
  - Delete `commitInsert` and `commitExtract`.
- **NeoForge adapter.** In `NeoForgeRunResourceHandler.insert`, replace the support check and the duplicated growth checks (enabled, height, build height, replaceable, `mayPlace`, unobstructed) with `inserted = min(inserted, run.acceptsAt(index, resource.toStack(inserted), this::current))`. Keep the `NeoForgeGrowthPlacement.prepare` step. Delete `supported`, `rotation`, `shape`, `enabled` and `slotsPerBlock`.
- **Tests.** Add four Fabric tests to `FabricTransferGameTests`, ported from `NeoForgeTransferGameTests`:
  - `headroomRollbackRestoresWaterAndCommitGrowsEachType`
  - `stagedSupportFillsCellsAndHeadroomBottomUp`
  - `positionalExtractionRollsBackAndCommitsMovement`, which also asserts that insertion is refused during a pending structural extraction
  - `storageSlotReplacementUsesStagedCapacity`
- **Docs.** Update the orientation files: both loaders now share the exclusion rules and the commit order.

## 2. Creating a stack did not fire the vanilla placement advancement trigger

**Check:** grep the production code for `CriteriaTriggers`; there are no hits. `StackInteractions.placeAndDeposit` places the block through `WorldEdits.placeChecked` and deposits the item, but never fires `PLACED_BLOCK`.

**Fix:** In `placeAndDeposit`, take `ItemStack placedWith = handStack.copy()` after a successful `placeChecked` and before the deposit consumes the stack. After `returnToHand`, call `CriteriaTriggers.PLACED_BLOCK.trigger(player, pos, placedWith)`. Do this only on the player gesture path, so automated growth does not award it. There is no GameTest for this yet.

## 3. Fabric used a mixin to cache the transfer adapter

**Check:** Look for `fabric/.../mixin/StackBlockEntityMixin.java`, which only adds a lazily created `FabricRunItemStorage` field, plus a `FabricStorageOwner` interface and casts to it in `FabricItemStorage` and `FabricRunItemStorage.ledger()`.

**Fix:**

- In `FabricItemStorage`, register `AttachmentType<FabricRunItemStorage> RUN_STORAGE = AttachmentRegistry.create(Identifier.fromNamespaceAndPath(MODID, "run_storage"))`. It is neither saved nor synced.
- Add `static FabricRunItemStorage of(StackBlockEntity be)` returning `be.getAttachedOrCreate(RUN_STORAGE, () -> new FabricRunItemStorage(be))`.
- Use `of(...)` in the three `ItemStorage.SIDED` providers and in `ledger()`.
- Delete the mixin and the interface, and remove the `"mixins": ["StackBlockEntityMixin"]` entry from `somestacks.mixins.json`. Keep the client `MinecraftMixin`.

## 4. Fabric's JSON config lived in common and was used as a NeoForge test fixture

**Check:** `JsonServerConfig` is in `common/src/main`, and `ConfigurationChecks` constructs it directly. Those shared tests run on NeoForge too, where they replace the native config backend with JSON.

**Fix:**

- Move `JsonServerConfig` to `fabric/src/main` (same package) and update its class Javadoc.
- In `ConfigurationChecks`:
  - Replace the JSON fixtures with an in-memory `policy(maxHeight, storage, singles, bars, disabledMods, disabledItems)` helper built from `ServerConfig.defaults()`. Use `ServerConfig.apply(...)` to set it, and restore `ServerConfig.settings()` in `finally`.
  - Rewrite `serverCommandsEnforcePermissionsAndPersistEdits` to work with whichever backend is installed:
    1. Run the commands.
    2. Apply a blank policy in memory.
    3. Run `ss reload` without checking its return value, which is the synced-player count (0 in GameTests).
    4. Assert that the edits came back.
    5. In `finally`, restore with `apply(original)` followed by `ServerConfig.save()`.
- Move `serverConfigLoadsBoundsAndLists` into Fabric's `ConfigurationGameTests` with its JSON helpers. Add a Fabric `jsonServerPolicyPersistsCommandLists` test (deny command, then read the JSON file) to mirror NeoForge's existing `nativeServerPolicyPersistsCommandLists`.
- On NeoForge, remove the delegate, its registration in `SomeStacksGameTests`, and `test_instance/configurationgametests/server_config_loads_bounds_and_lists.json`.

## 5. Redundant Fabric packet-send wrapper

**Check:** `FabricNetworking.send(player, packet)` only forwards to `ServerPlayNetworking.send`, and its only caller is `FabricPlatformServices.sendConfig`.

**Fix:** Call `ServerPlayNetworking.send` directly in `sendConfig`, and delete `FabricNetworking.send`.

## 6. Dead code

**Check and fix:**

- `SsCommand`: delete `suggestItems`, the `ARG_ENTRY` constant and the `java.util.stream.Collectors` import. None of them is referenced.
- `SinglesCubeIdx.getCubeBox` and `CubeGrid.worldBoxAtVisual` are unreachable; delete both, plus the `AABB` import in `SinglesCubeIdx`.
- After item 1, `SinglesCubeIdx.occupancyOf` has no callers; delete it.

## 7. Registry membership checks over trusted registrations

**Check:** `CommonRegistry.require` calls `registry.containsKey(id)` before every `getValue`. Separately, `BarTextureStore.calculateTintFromItemTexture` checks `BuiltInRegistries.ITEM.containsKey(itemLoc)` for an identifier that came from a live stack.

**Fix:** Rename `require` to `lookup` with the body `return registry.getValue(id);`. Remove the `containsKey` branch in the tint helper, but keep its exception recovery.

## 8. Private replacement for `ItemStack.copyWithCount`

**Check:** `StackItemStorage.copyWithSize` exists.

**Fix:** Replace its four call sites with `stack.copyWithCount(n)` and delete the helper.

## 9. `ServerConfig.ListSetting` dispatched on repeated string names

**Check:** `ListSetting` is a class with a `name` field, and its `set` method compares `name.equals("disable_mods")` and so on.

**Fix:**

- Make it an enum (`DISABLE_MODS`, `DISABLE_ITEMS`, `GEN_MODS`, `GEN_ITEMS`) that keeps the getter function.
- In `set`, copy the four lists from the current settings, `switch (this)` to replace one of them, then build a new `Settings`.
- Remove the old `public static final ListSetting` fields, and update references across `SsCommand`, `ConfigSyncPkt` and the tests from `ServerConfig.X` to `ServerConfig.ListSetting.X`.

## 10. Fully qualified class names in code

**Check:** grep for inline package-qualified types in these places:

- `SomeStacksNeoForge`: `ModContainer`, `ModConfig`
- `NeoForgeServerConfig`: `ModConfig`, `Path`
- `ServerConfig`: `Function`
- `BarStackBER`: `Map`, `IdentityHashMap`
- `BarStackBE`: `TagKey`, `Item`, `Registries`, `Identifier`
- `StackBlock`: `BlockType`
- `StoragePile`: `Comparator`

**Fix:** Import them and use simple names. In `BarStackBE.INGOTS`, use `SomeStacksCommon.MODID` instead of `"somestacks"`.

## 11. Logging gaps

**Check and fix:**

- `ModelMeasurement.measure` catches exceptions silently. Log a warning in the catch with the item id, `context.getSerializedName()`, the fallback (the default 3-D profile) and the exception. `AutoRenderProfiles` caches the result per item until the next resource reload, so this logs only once per item and context in each reload cycle.
- No log message shows when server policy takes effect. In `SomeStacksServer`, add a `logPolicy(action)` INFO line listing which stack types are enabled and the maximum run height. Call it after `onServerStarting` loads policy, and inside `onPolicyReloaded` after a changed policy is applied and synced.
- `BarTextureStore` per-entry warnings do not name the file. Add `fileLocation` to both of them.

## 12. Item-stack ownership missing from the storage contracts

**Check:** The Javadocs on `SlotAccess.getStackInSlot`, `insertItem` and `extractItem`, `StackItemStorage.setStackInSlot`, and `StackRunItemAccess.getSlot`/`insertAt` say nothing about aliasing.

**Fix:** Documentation only. State that:

- returned stacks are the live stored objects: callers must not modify them, and must copy them before keeping or editing;
- a committed insertion, and `setStackInSlot`, may keep the caller's stack object, so the caller must give it up or pass a copy;
- extraction results belong to the caller.

## 13. Loader sources JARs omitted common's sources

**Check:** Neither `fabric/build.gradle` nor `neoforge/build.gradle` configures `sourcesJar`.

**Fix:** Add this after `shadowJar` in both files, and in their `build-env/` copies:

```groovy
// The production jar bundles common, so the sources jar carries common's sources as well.
sourcesJar {
    def commonSources = project(':common').tasks.named('sourcesJar')
    dependsOn commonSources
    from commonSources.flatMap { it.archiveFile }.map { zipTree(it) }
}
```

Mention it in `build-env.md`.

## After porting

- Update `orientation-code.md` and `orientation-player.md` to cover:
  - the shared transaction rules;
  - attachments on both loaders;
  - the `PLACED_BLOCK` trigger;
  - Fabric-only JSON config tests.
- Line endings: the 26.1 repo has CRLF working copies with `core.autocrlf`. Normalize any files a script edits.
