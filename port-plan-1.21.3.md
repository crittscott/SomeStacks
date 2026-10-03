# Port plan: 1.21.1 code to 1.21.3

The build environment already targets 1.21.3 and Gradle syncs cleanly. The source is still the 1.21.1 version of the mod. This plan covers the code port. `new-build-env/` is left alone for now.

Do the steps in order.

## 1. Replace FTB Chunks with Common Protection API

FTB Chunks has no 1.21.3 release. The build already declares `eu.pb4:common-protection-api` (Fabric `modCompileOnly` + `modLocalRuntime`, non-transitive).

- Delete `fabric/.../server/FtbChunksProtection.java` and `neoforge/.../server/FtbChunksProtection.java`.
- NeoForge: remove the FTB branch and the startup log line from `NeoForgeEditAuthority`. `EventHooks.onBlockPlace` remains the placement check.
- Fabric: add one Common Protection API holder class, guarded by a mod-loaded check, that asks the API whether the automation actor may place at the position. Use it in `FabricEditAuthority.preparePlacement`, and update that class's javadoc and startup log. Write it against the API's own published source.
- `fabric.mod.json`: remove `"suggests": {"ftbchunks": "*"}`.
- Gametests: check the Fabric `ProtectionGameTests` for anything that assumed FTB.

## 2. Rewrite world migration (1.21.1 to 1.21.3 only)

Problems with the current `StackItemStorage.deserializeNBT`:

- Vanilla's chunk DataFixerUpper does not fix block entity types it doesn't know, so items inside our block entities are never upgraded unless we do it ourselves.
- Detection is by shape ("no `count` means 1.20.1, data version 3465"). Tags saved by 1.21.1 skip the fixer and go straight to `ItemStack.parse`, so the item-component changes made in 1.21.2 never get applied.
- `ItemStack.parse(...).orElse(ItemStack.EMPTY)` silently discards unreadable stacks.
- The migration is mixed into the general storage class, and it also runs on client sync (`getUpdateTag` → `loadAdditional`).

Target design:

- Every block entity save writes a `DataVersion` (vanilla `NbtUtils` data-version helpers).
- A dedicated migration class runs on load, before `StackItemStorage` sees the tag:
  - It reads `DataVersion`. If absent, it treats the data as 1.21.1 (data version 3955), since 1.21.1 never wrote one.
  - If that version is older than current, it runs each item tag through the DataFixerUpper (`References.ITEM_STACK`, `RegistryOps` over `NbtOps`) from that version to current, preserving `Slot`, and marks the block entity dirty.
  - An item tag that still fails to parse is set aside and logged, never dropped.
- Delete the 1.20.1 path: `LEGACY_ITEM_DATA_VERSION`, `isLegacyItemTag`, `upgradeLegacyItemTag`, and the `boolean` return of `deserializeNBT`.
- `Rotation`, `Permanent`, and `CubeRotations` are plain ints and booleans and need no migration.
- Add a gametest that loads a 1.21.1-format block entity tag without `DataVersion`, containing an item with a component changed in 1.21.2, and checks that the item is upgraded and the block entity is marked for saving.

## 3. Mechanical API changes (1.21.2/1.21.3)

These are expected from the 1.21.2 changes but not verified against sources; the compiler is the authority.

| Change | Where |
| --- | --- |
| `BlockBehaviour.Properties` needs `setId(ResourceKey<Block>)` | Fabric `FabricRegistry`, Forge and NeoForge `ModRegistry` |
| `BlockEntityType.Builder.of(...).build(null)` removed; use the `BlockEntityType` constructor | Forge and NeoForge `ModRegistry` |
| `useItemOn` returns `InteractionResult`; `ItemInteractionResult` removed; result constants changed | `StorageStackBlock`, `SinglesStackBlock`, `BarStackBlock` |
| New `updateShape` parameter list (`LevelReader`, `ScheduledTickAccess`, ..., `RandomSource`); the water tick is scheduled through `ScheduledTickAccess` | the three `*StackBlock` |
| Registry lookup changes: `get(ResourceLocation)` becomes `getValue`, tag enumeration and lookup changed, `registryOrThrow` becomes `lookupOrThrow` | `ServerConfig` (ingot resolution), `BarTextureStore`, `AutoRenderProfiles`, `StackSoundData`, `ConfigurationChecks` |
| `getMinBuildHeight` becomes `getMinY` | the three loader `ProtectionGameTests` |
| `NativeImage.getPixelRGBA` likely renamed, possibly with a different channel order | `BarTextureStore.analyzePixelsForTint` |
| Check `ItemRenderer.getModel` and `BakedModel.getRenderPasses` (expected unchanged until 1.21.4) | `CubeRenderHelper`, the loader `ModelMeasurer`s, `FabricModelMeasurer`, `BarTextureStore` |

Expected unchanged: `onRemove`, `isPathfindable`, `getAnalogOutputSignal`, the annotation-based GameTest framework, Fabric `FakePlayer`, NeoForge `FakePlayerFactory`, and Forge `SimpleChannel`.

## 4. Documentation cleanup

- Review the comments that name "Forge 1.21.1" (`ForgeEditAuthority`, Forge gametest `GameTestSupport`).
- Update `orientation-code.md` and `orientation-player.md` for the protection change (FTB Chunks → Common Protection API) and the new migration model.
- Update README and CHANGELOG references to FTB Chunks.
