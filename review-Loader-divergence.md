# Review: Loader-divergence

## Summary
Most loader differences reflect real differences in registration, events, protection, networking, and transactional automation. Two pieces of loader-neutral behavior remain copied into both loader modules: ordinary model geometry measurement and block property definitions. Consolidate those pieces without combining the loaders' native integration paths.

## Findings

### 1. Ordinary model measurement is implemented twice
- **Severity:** Medium
- **Confidence:** Certain
- **Where:** forge/src/main/java/com/github/crittscott/somestacks/client/measure/ModelMeasurer.java:77, forge/src/main/java/com/github/crittscott/somestacks/client/measure/ModelMeasurer.java:99, forge/src/main/java/com/github/crittscott/somestacks/client/measure/ModelMeasurer.java:132, fabric/src/main/java/com/github/crittscott/somestacks/client/measure/FabricModelMeasurer.java:60, fabric/src/main/java/com/github/crittscott/somestacks/client/measure/FabricModelMeasurer.java:82, fabric/src/main/java/com/github/crittscott/somestacks/client/measure/FabricModelMeasurer.java:100
- **In plain English:** Both versions independently calculate the size of ordinary item models using the same procedure. A correction to how item corners are measured must be made twice even though the calculation does not depend on the loader.
- **Consequence:** A maintainer fixing vertex decoding, random-seed handling, transformed bounds, or directional model traversal must find and update two implementations; missing one makes automatically chosen item sizes differ between loaders. The native differences in model transforms and custom rendering do not require duplicating those calculations.
- **Action:** Put ordinary quad traversal, vertex transformation, and a reusable bounds collector in the existing common `ModelMeasurement` class. Have each loader supply its transformed pose and model passes; keep Forge camera transforms and custom-renderer probing, Fabric fallback behavior, and each loader's result selection local.
- **Technical detail:** Both `collectQuads` methods decode four vertices at stride eight, transform them by the pose matrix, and feed identical min/max collectors. Both `measureQuads` methods traverse six directions plus the unculled group with seed `42L`. Forge additionally iterates render passes, which can remain a loader-selected input to a shared geometry operation. Its custom vertex-capturing consumer can use the same collector without moving Forge types into common.

### 2. Loader registries also own identical block behavior settings
- **Severity:** Low
- **Confidence:** Certain
- **Where:** forge/src/main/java/com/github/crittscott/somestacks/ModRegistry.java:39, forge/src/main/java/com/github/crittscott/somestacks/ModRegistry.java:49, forge/src/main/java/com/github/crittscott/somestacks/ModRegistry.java:59, fabric/src/main/java/com/github/crittscott/somestacks/FabricRegistry.java:23, fabric/src/main/java/com/github/crittscott/somestacks/FabricRegistry.java:32, fabric/src/main/java/com/github/crittscott/somestacks/FabricRegistry.java:41
- **In plain English:** Each stack's material, breaking strength, piston behavior, light, and changing shape are defined separately in the Forge and Fabric registration files. These settings currently agree, but the loader does not require separate definitions.
- **Consequence:** Changing a stack's block properties requires editing two registration files; overlooking one leaves the same stack with different behavior in the two releases. The common block class does not reveal its complete property definition because part of that definition lives in both loader modules.
- **Action:** Give each existing common stack block class a factory for its `BlockBehaviour.Properties`, and have both registries call that factory. Keep deferred registration, direct registration, and block-entity builders in their current loader modules; no new registration abstraction is needed.
- **Technical detail:** The three property chains are duplicated verbatim across registries, including `.dynamicShape()` for Singles/Bar, map color, `.noOcclusion()`, piston blocking, strength, and the light-property function. Every type used by these chains is already available in common. Only the mechanism and timing of registry insertion require loader-specific code.

## Drift from the expected way
The remaining unnecessary divergence is localized to shared calculations and block definitions left inside native integration classes (findings 1 and 2). The reviewed native integration paths otherwise use the distinct loader mechanisms their jobs require; merging protection transactions, item-storage adapters, or event registration would obscure those differences.
