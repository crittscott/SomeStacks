# Review: Magic-numbers

## Summary
Most policy limits, inventory dimensions, and player messages already have named constants or language entries. The main remaining issue is duplicated presentation values: the renderer and both model measurers separately encode the same GUI angles, while rendering also repeats cell sizes owned by the geometry classes. Command usage text and two fully qualified class references need smaller cleanups.

## Findings

### 1. GUI presentation angles have three separate homes
- **Severity:** Medium
- **Confidence:** Certain
- **Where:** common/src/main/java/com/github/crittscott/somestacks/client/CubeRenderHelper.java:186, common/src/main/java/com/github/crittscott/somestacks/client/CubeRenderHelper.java:187, forge/src/main/java/com/github/crittscott/somestacks/client/measure/ModelMeasurer.java:60, forge/src/main/java/com/github/crittscott/somestacks/client/measure/ModelMeasurer.java:61, fabric/src/main/java/com/github/crittscott/somestacks/client/measure/FabricModelMeasurer.java:48, fabric/src/main/java/com/github/crittscott/somestacks/client/measure/FabricModelMeasurer.java:49
- **In plain English:** The mod tilts inventory-style item pictures before displaying them, and it measures those pictures at the same tilt to decide their size. The same two angles are written independently in three places.
- **Consequence:** A maintainer changing the presentation must find and update all three copies. Updating only the display makes automatic sizing measure a different orientation, producing incorrectly sized or centered items; updating only one measurer makes the two loaders disagree.
- **Action:** Put named GUI counter-rotation angles in CubeRenderHelper and use them in the renderer and both loader measurers. Keep the loader-specific model transformation code in its current modules.
- **Technical detail:** renderGuiItem and each measure(..., counterRotate) branch apply the same Y rotation followed by X rotation using independent literals. These values describe one shared presentation choice and must stay identical for the measured bounds to match the rendered pose.

### 2. Rendering repeats geometry's cell sizes
- **Severity:** Medium
- **Confidence:** Certain
- **Where:** common/src/main/java/com/github/crittscott/somestacks/util/StorageCubeIdx.java:31, common/src/main/java/com/github/crittscott/somestacks/util/SinglesCubeIdx.java:44, common/src/main/java/com/github/crittscott/somestacks/client/CubeRenderHelper.java:81, common/src/main/java/com/github/crittscott/somestacks/client/CubeRenderHelper.java:84
- **In plain English:** The classes defining the actual Storage and Singles cells name their sizes, but the drawing code independently writes those sizes again. The drawing code's scale relationship therefore depends on remembering which numbers match the cells.
- **Consequence:** Changing a cell dimension requires a second, easily missed edit in the renderer. Missing it changes how much space the item pictures fill without matching the targeting and collision geometry, and automatic fitting inherits the wrong local cell size.
- **Action:** Expose the existing cell-size constants from StorageCubeIdx and SinglesCubeIdx and derive STORAGE_CELL_RENDER_SCALE and CELL_LOCAL_SIZE from those constants. Name the renderer's model-unit size separately so the formulas distinguish cell geometry from rendering units.
- **Technical detail:** STORAGE_CELL_RENDER_SCALE embeds the Storage/Singles cell-size ratio as literals even though the same sizes already exist as CELL_PIXELS in the geometry classes. CELL_LOCAL_SIZE separately embeds the Singles cell size; AutoRenderProfiles.fit uses this constant when fitting model bounds.

### 3. Help usage lines bypass the language file
- **Severity:** Low
- **Confidence:** Certain
- **Where:** common/src/main/java/com/github/crittscott/somestacks/command/SsHelp.java:47, common/src/main/java/com/github/crittscott/somestacks/command/SsHelp.java:48, common/src/main/java/com/github/crittscott/somestacks/command/SsHelp.java:56, common/src/main/java/com/github/crittscott/somestacks/command/SsHelp.java:57, common/src/main/java/com/github/crittscott/somestacks/command/SsHelp.java:66, common/src/main/java/com/github/crittscott/somestacks/command/SsHelp.java:71, common/src/main/java/com/github/crittscott/somestacks/command/SsHelp.java:78, common/src/main/java/com/github/crittscott/somestacks/command/SsHelp.java:83, common/src/main/java/com/github/crittscott/somestacks/command/SsHelp.java:84, common/src/main/java/com/github/crittscott/somestacks/command/SsHelp.java:90, common/src/main/java/com/github/crittscott/somestacks/command/SsHelp.java:91, common/src/main/java/com/github/crittscott/somestacks/command/SsHelp.java:96, common/src/main/java/com/github/crittscott/somestacks/command/SsHelp.java:103, common/src/main/java/com/github/crittscott/somestacks/command/SsHelp.java:175, common/src/main/resources/assets/somestacks/lang/en_us.json:77
- **In plain English:** Command explanations can be translated, but their usage examples are fixed in Java. A language pack cannot translate explanatory placeholders such as “item,” “mode,” “scale,” or “command.”
- **Consequence:** Players using a translated language pack still see English argument labels in help. Translators also cannot adjust the usage presentation without changing the mod's Java code.
- **Action:** Give each help usage form a language key and store those keys in Topic instead of display strings. Resolve each usage with Component.translatable, preserving the actual command words and syntax in every language entry.
- **Technical detail:** The existing somestacks.command.help.usage translation contains only spacing and a substitution. Passing usage as a String does not translate the contents of Topic.usages; it merely inserts the Java literal into the translated wrapper.

### 4. Two class references unnecessarily bypass imports
- **Severity:** Low
- **Confidence:** Certain
- **Where:** fabric/src/main/java/com/github/crittscott/somestacks/server/FabricEditAuthority.java:32, fabric/src/main/java/com/github/crittscott/somestacks/network/FabricClientNetworking.java:44
- **In plain English:** Two Fabric files write out a class's full package name inside ordinary code instead of listing it with the other imports.
- **Consequence:** Maintainers looking at those files' import lists miss dependencies used in the method and actor declaration. The long references also interrupt otherwise conventional declarations without resolving any naming conflict.
- **Action:** Import java.util.UUID in FabricEditAuthority and java.util.function.Consumer in FabricClientNetworking, then use UUID and Consumer at the existing call sites.
- **Technical detail:** Neither file defines or imports a conflicting UUID or Consumer. These are the two fully qualified Java class references found in production expressions and declarations across common, Forge, and Fabric source.

## Drift from the expected way
The drift is limited to shared presentation values retaining independent copies (findings 1 and 2), help examples escaping the otherwise consistent language-key approach (finding 3), and two ordinary Java dependencies bypassing imports (finding 4). These need conventional constants, language entries, and imports; no new configuration subsystem or loader abstraction is necessary.
