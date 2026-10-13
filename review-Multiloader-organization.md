# Review: Multiloader-organization

## Summary
The review found one remaining boundary cleanup: two pieces of Forge-only adapter behavior live in common even though Fabric never uses them. Move those pieces into Forge and remove the empty item-handler inheritance layer. No player-visible failure caused by the common/loader split was identified in the reviewed production integration paths.

## Findings

### 1. Forge-only adapter behavior still lives in common
- **Severity:** Low
- **Confidence:** Certain
- **Where:** common/src/main/java/com/github/crittscott/somestacks/block/RunItemHandlerBase.java:8; forge/src/main/java/com/github/crittscott/somestacks/block/RunItemHandler.java:6; common/src/main/java/com/github/crittscott/somestacks/client/CubeRenderHelper.java:265; forge/src/main/java/com/github/crittscott/somestacks/client/ForgeRenderPlatform.java:15; forge/src/main/java/com/github/crittscott/somestacks/client/measure/ModelMeasurer.java:83
- **In plain English:** The shared part of the mod contains machinery that only Forge uses to move items and choose rendering layers. Fabric has its own machinery for both, so these shared pieces do not actually save any duplicated behavior.
- **Consequence:** When a maintainer changes Forge automation, they must navigate an empty Forge class and its separate common superclass to find the implementation. When changing Forge rendering, they must also edit the shared cell renderer to update a rule that Fabric never calls; both arrangements obscure which loader owns the behavior.
- **Action:** Move the complete implementation of `RunItemHandlerBase` into Forge's `RunItemHandler`, make that class directly implement `IItemHandler`, and delete the common superclass. Move `fabulousFlag` into `ForgeRenderPlatform` and update its own call and `ModelMeasurer`'s call; remove the helper and its now-unused `HalfTransparentBlock`, `StainedGlassPaneBlock`, and `Block` imports from `CubeRenderHelper`.
- **Technical detail:** `RunItemHandler` contains only a constructor and extends `RunItemHandlerBase`; a source reference search finds no other consumer of that base. Fabric's `FabricRunItemStorage` implements its transaction-based adapter directly using `StackRunItemAccess` and never uses the base. Likewise, `CubeRenderHelper.fabulousFlag` is called only by `ForgeRenderPlatform.renderPasses` and Forge's `ModelMeasurer`; Fabric's render-pass backend simply returns `List.of(model)`. Keep the actually shared run mutations and rendering projection in common, while locating the native Forge adapter and Forge model-pass selection rule in the Forge module.

## Drift from the expected way
Finding 1 is localized residue of placing loader adapter internals in common: neither the Forge item-handler superclass nor its rendering flag is shared with Fabric. The correction requires moving existing code and eliminating one class, without adding another abstraction or forcing the distinct storage APIs into a common adapter.
