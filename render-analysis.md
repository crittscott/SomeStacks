# Render Analysis

## Problem statement

> Every item for every mod must be put into a Stack before one can reliably set its rendering type and parameters. All the 3D types often, as can be seen from the configs, require custom scaling and shifting. Moreover, some items crash if given one render type rather than another, in ways I do not know how to predict.
>
> As it is, while the mod works fine for configured items, the effort of configuring them is so great that most people will not use the mod. I need to find a way to, ideally, not have to do per-item configurations, or, at least, to greatly reduce the number of special cases.
>
> Study the rendering system. What can we do? Can we not work out how large an item will be? Where it will be centered?

## Conclusion

For ordinary baked item models, the mod can calculate the model's rendered geometric bounds and use them to derive centering and uniform scaling automatically. This should remove most of the current per-item configuration burden.

A universal static solution is not possible for arbitrary custom item renderers. They execute unrestricted rendering code and expose no contract for bounds, center, or safe rendering contexts. Those items need either a measured snapshot of their normal GUI appearance or a conservative 2D fallback.

The practical goal should therefore be:

- Automatically measure and fit normal baked models.
- Automatically turn flat models into cropped stack-face icons.
- Render custom or uncertain models through a safe GUI snapshot path.
- Retain explicit configuration only for aesthetic exceptions and a small quarantine list of genuinely unsafe items.

This will not eliminate every exception, but it should reduce thousands of item-specific settings to a comparatively small set.

## Why the present classifier is insufficient

[`CubeRenderHelper`](src/main/java/com/github/crittscott/somestacks/client/CubeRenderHelper.java) currently uses `BakedModel.isGui3d()` to select a rendering mode. That method is only a flatness and GUI-lighting hint. It does not report:

- the model's dimensions;
- the center of its visible geometry;
- whether the model is safe in a particular display context;
- whether the item uses a custom renderer;
- which representation is visually appropriate on a stack cube.

The four current modes also invoke materially different rendering paths:

- `3d` uses the full item renderer in the `FIXED` display context.
- `gui` uses the full item renderer in the `GUI` context and adds a hard-coded counter-rotation.
- `block` bypasses the item representation and renders the default block state, without the normal world and model data that some block renderers expect.
- `2d` bypasses most of the item renderer and reads only `model.getQuads(null, null, random)` into one render type.

These are not interchangeable presentations of one known geometry. They can select different models, transforms, render passes, render types, or custom code. That explains why a mode that works for one item can be wrong or unsafe for another.

Forge's normal item-rendering path applies the display-context transform, accepts a possibly different model returned by that transform, iterates render passes and render types, and then renders either baked quads or a custom `BlockEntityWithoutLevelRenderer`. Relevant Forge documentation and source patches are:

- [Baked models](https://docs.minecraftforge.net/en/1.20.1/rendering/modelloaders/bakedmodel/)
- [Model transforms](https://docs.minecraftforge.net/en/1.20.1/rendering/modelloaders/transform/)
- [Forge ItemRenderer patch](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.20.x/patches/minecraft/net/minecraft/client/renderer/entity/ItemRenderer.java.patch)
- [Custom item renderers / BEWLR](https://docs.minecraftforge.net/en/1.20.1/items/bewlr/)

## What the configuration corpus indicates

The current compatibility JSON files contain 3,350 item entries. Of those:

- 2,915 specify an explicit scale, about 87 percent;
- 318 specify a nonzero positional offset;
- 2,003 contain transform data without an explicit render mode;
- the most common scale values are `0.5`, `0.7`, and `0.6`.

The dominant maintenance problem is therefore fitting, not merely selecting among the current modes. Automatic bounds-based fitting should produce a much larger reduction in configuration than another heuristic mode classifier.

## Measuring ordinary baked models

For a non-custom baked model, the client already has enough information to calculate an axis-aligned bounding box in the exact item display context that will be rendered.

The measurement procedure should mirror the normal item renderer:

1. Resolve the actual model from the `ItemStack`, including damage- and NBT-dependent item overrides. See [item overrides](https://docs.minecraftforge.net/en/1.20.1/rendering/modelloaders/itemoverrides/).
2. Apply the `FIXED` display transform to a fresh pose stack.
3. Retain the model returned by `applyTransform`, because Forge permits that call to substitute another model.
4. Apply the same `-0.5` origin translation used by the item renderer.
5. Visit every render pass, render type, face-specific quad group, and unculled (`null` direction) quad group that the normal renderer would visit.
6. Transform every quad vertex through the resulting pose.
7. Accumulate the minimum and maximum transformed `x`, `y`, and `z` coordinates.

For the resulting bounds:

```text
center = (minimum + maximum) / 2

scale = min(
    targetWidth  / width,
    targetHeight / height,
    targetDepth  / depth
)

translation = targetCenter - scale * center
```

Dimensions near zero must be excluded from the scale calculation so that a flat model does not produce an infinite or extreme factor. Scaling should remain uniform; independent axis scaling would distort models.

The bounds are geometric rather than strictly visual. A generated sprite can contain transparent portions inside its quads, so its quad bounds may be much larger than its visible pixels. Flat items will benefit from an alpha-aware crop of their texture or rendered snapshot.

Measurement must also be attached to the resolved stack variant, not only to the item registry ID. Damage, NBT, predicates, or other stack state can select different geometry.

## Proposed automatic render profiles

Each item-stack variant should resolve to an `AutoRenderProfile` describing the safest known presentation and any derived transform. The broad classification should be:

| Item representation | Automatic treatment |
| --- | --- |
| Non-custom volumetric baked model | Measure transformed quad bounds, uniformly fit them, and render normally in `FIXED` context. |
| Non-custom flat baked model | Produce an alpha-cropped icon and place it on the stack cube's visible faces. |
| BEWLR or other custom-rendered model | Capture its normal GUI representation once into an offscreen texture, crop it, and use the result as a safe icon. |
| Analysis or rendering failure | Use the particle sprite or missing texture, record the failure, and do not probe the item again every frame. |

Profile resolution should follow this order:

1. Server safety override.
2. Authored known-good override.
3. Cached automatic profile.
4. Fresh automatic analysis.
5. Safe icon fallback.

Automatically derived profiles belong on the client. They should be invalidated on resource reload and cached using the resolved model or a sufficiently complete stack-variant key.

Explicit configuration should override automatic analysis, but lack of configuration should no longer mean that the mod must guess between several dangerous rendering paths every frame.

## The custom-renderer boundary

`BakedModel.isCustomRenderer()` delegates rendering to `BlockEntityWithoutLevelRenderer.renderByItem`. Such a renderer is arbitrary code. It provides no general API for asking:

- what bounds it will draw;
- where its result is centered;
- whether it is safe in `GUI`, `FIXED`, or another context;
- whether it depends on time, world state, NBT, animation, or external renderer state.

A recording `MultiBufferSource` could measure cooperative custom renderers by intercepting submitted vertices. This is useful as a later enhancement, but it is not a reliable first-line solution:

- it executes the custom renderer merely to inspect it;
- fitting may require a second rendering pass;
- a renderer may bypass the supplied buffers;
- the renderer may still crash in an unsuitable context;
- an exception can leave rendering state damaged for the rest of the frame.

A GUI snapshot is safer because it asks an item for the representation it normally supplies in inventories, does so once outside the stack-cell rendering loop, and thereafter treats the result as an ordinary texture. It is still not mathematically guaranteed for hostile custom code, so failure isolation and fallback remain necessary.

## Crash avoidance

The mod should stop probing arbitrary rendering modes until one happens to work. Catching an exception is not sufficient protection because rendering code can mutate pose, buffer, shader, or global graphics state before failing.

The automatic policy should instead be conservative:

- Never select `block` automatically. It changes from item rendering to default-block-state rendering and can omit context required by dynamic block models.
- Determine custom rendering with `model.isCustomRenderer()` after applying the relevant transform. This is more accurate than comparing renderer classes through the present `hasBEWLR` heuristic.
- Use native `FIXED` rendering only for non-custom models whose geometry has been successfully measured.
- Send custom, dynamic, or low-confidence models to the GUI-snapshot path.
- Keep a small explicit quarantine for known crashers.
- Persist or cache a local failure marker so a failed analysis is not repeated every frame or every time the item enters view.

The mod should also restore its own pose and render state in `finally` blocks around any external rendering call. This limits damage but does not make arbitrary probing safe.

## Suggested implementation sequence

### 1. Add measurement without changing behavior

Implement `AutoRenderProfile` for non-custom baked models and expose diagnostic output containing:

- resolved model identity;
- flat versus volumetric classification;
- transformed minimum and maximum coordinates;
- calculated center;
- calculated uniform scale and translation;
- reason for refusing automatic 3D rendering, if any.

Initially, keep the existing configured rendering behavior. This phase lets the calculated values be compared with the compatibility corpus without making visual regressions harder to diagnose.

### 2. Compare automatic results with configured walls

Use representative compatibility walls to compare calculated transforms with the authored values. The comparison should distinguish:

- values needed merely to fit the model;
- adjustments made for a preferred visual composition;
- true safety exceptions;
- cases where transparent padding makes geometric bounds misleading.

This will establish an appropriate target volume and padding factor for stack cells.

### 3. Enable automatic profiles when no override exists

Once bounds are trustworthy, use them for unconfigured, non-custom baked models. Keep explicit entries authoritative.

### 4. Add cropped GUI snapshots

Render flat and custom items into an offscreen target in their normal GUI context, determine the nontransparent pixel rectangle, add a small margin, and use the cropped result as the stack icon. Cache snapshots and rebuild them after resource reload.

### 5. Reduce the compatibility data

After in-game comparison, remove entries that only reproduce the automatic fit. Retain entries for:

- deliberate artistic composition;
- models whose visual bounds differ materially from geometric bounds;
- dynamic variants that cannot share a profile;
- renderer quarantine and crash avoidance.

## Expected result

The strongest immediate improvement is automatic transformed-quad measurement for ordinary baked models. It directly addresses the explicit scaling required by roughly 87 percent of the current configuration entries and can also derive most centering offsets.

The remaining hard boundary is custom rendering. No general static analysis can determine the output of arbitrary renderer code, but a cached GUI snapshot provides a safe and visually faithful default for most such items. Together, measured 3D profiles and snapshot-based icons should make unconfigured items work acceptably by default while reducing manual configuration to genuine exceptions.
