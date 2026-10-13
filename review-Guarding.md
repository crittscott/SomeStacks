# Review: Guarding

## Summary
Unnecessary defensive behavior is limited to a few local fallbacks rather than spread throughout the interaction and storage code. The clearest cleanup is to stop treating failures in the mod's own bounded pixel loop as an expected texture problem. Storage settlement also substitutes a made-up capacity for an invalid item capacity, although the surrounding storage code trusts that same Minecraft value.

## Findings

### 1. Pixel analysis catches failures that its caller has already ruled out
- **Severity:** Low
- **Confidence:** Certain
- **Where:** common/src/main/java/com/github/crittscott/somestacks/client/BarTextureStore.java:253, common/src/main/java/com/github/crittscott/somestacks/client/BarTextureStore.java:290
- **In plain English:** After a texture has loaded, the code calculates its average color by walking its pixels. It nevertheless treats any failure in that calculation as an ordinary reason to draw a white bar.
- **Consequence:** A maintainer changing the averaging code can introduce a calculation or indexing mistake that is converted into a white tint and a generic warning, instead of exposing the broken calculation directly. The extra fallback also makes the tint code appear to require protection against Minecraft returning unreadable pixels from a successfully loaded image.
- **Action:** Remove the `try`/`catch` inside `analyzePixelsForTint`, keeping its ordinary no-opaque-pixels result. Retain the image-loading fallback and protection around third-party model/color providers; those are separate external inputs.
- **Technical detail:** `averageSpriteColor` obtains an image through `NativeImage.read`, rejects a missing image, calls `analyzePixelsForTint` before closing it, and closes it in `finally`. The analysis loop bounds both coordinates by that live image's dimensions and performs only pixel reads and local arithmetic; division is already protected by `count == 0`. There is no third-party callback or file access inside the caught region. The blanket `catch (Exception)` therefore protects the local implementation and Minecraft's image access rather than an identified unreliable interface.

### 2. Settlement invents a capacity when Minecraft's item capacity is invalid
- **Severity:** Low
- **Confidence:** Likely
- **Where:** common/src/main/java/com/github/crittscott/somestacks/block/StoragePile.java:439
- **In plain English:** When sorting stored items, the mod changes an item capacity below one into a capacity of one. Depositing, extracting, and calculating pile fullness use the item's real capacity instead.
- **Consequence:** A maintainer tracing an item-capacity problem has to account for an extra settlement-only interpretation of that item. This fallback does not make an invalid item safe: insertion still uses the original capacity, and pile fullness still divides by it.
- **Action:** Replace `Math.max(1, model.getMaxStackSize())` with `model.getMaxStackSize()`, trusting the valid item capacity consistently with the rest of storage. Do not introduce a separate recovery path unless a specific supported item implementation is known to violate that contract.
- **Technical detail:** `consolidate` operates on nonempty stored stacks and re-cuts each group's count using `max`. `StoragePile.roomAt` and `fillExisting`, `StackItemStorage.insertItem` and `extractItem`, and `StackBlockEntity.refreshComparatorContribution` use `getMaxStackSize()` directly. The clamp only defends this one loop against an invalid capacity; it is not validation at a network or persistence boundary and cannot repair the underlying item contract.

## Drift from the expected way
The drift is narrow: a blanket recovery around local pixel arithmetic (finding 1) and a settlement-only replacement for Minecraft's item capacity (finding 2). Neither warrants a broader defensive framework; removing the extra fallbacks restores the same trust already used by the surrounding code.
