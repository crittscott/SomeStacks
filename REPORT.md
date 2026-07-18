# SomeStacks — Repo Study

## What it is
A **Minecraft Forge mod** (`somestacks`, "Some Stacks", v1.1.0) for **MC 1.20.1 / Forge 47.4.0**, Java 17, Parchment mappings. Author `crittscott`. Tagline: *"Just stack it up!"* It adds decorative-but-functional storage blocks that visually display the actual items stacked inside them — you right-click items onto a block and they appear as a 3D pile of mini item models. Package root: [src/main/java/com/github/crittscott/somestacks/](src/main/java/com/github/crittscott/somestacks/).

Note: `.git` exists despite the harness flagging "not a git repo." History was not inspected. There's also a parallel `bin/` tree that's a stale compiled copy of `src/`.

## The three block types (the core concept)
All three are `EntityBlock`s with `ENTITYBLOCK_ANIMATED` render, a light-emission blockstate property driven by their contents, and a custom BlockEntityRenderer that draws each contained item as a scaled mini-model in a grid cell.

| Block | BE slots | Grid | Constraint | Special behavior |
|---|---|---|---|---|
| **Storage Stack** ([StorageStackBE](src/main/java/com/github/crittscott/somestacks/block/StorageStackBE.java)) | 27 | 3×3×3 cube | any item (minus disabled) | Vertical **piles**: deposits overflow upward into/auto-creating a stacked neighbor; whole pile auto-sorts & consolidates; emits a comparator/analog signal from fill level; empty non-"permanent" blocks self-delete |
| **Singles Stack** ([SinglesStackBE](src/main/java/com/github/crittscott/somestacks/block/SinglesStackBE.java)) | — | — | — | Display-shelf variant |
| **Bar Stack** ([BarStackBE](src/main/java/com/github/crittscott/somestacks/block/BarStackBE.java)) | 64, **1 item/slot** | 4×4×4 | **only `forge:ingots`** ([ModTags](src/main/java/com/github/crittscott/somestacks/ModTags.java)) | Physical **stacking with gravity**: bars must be "grounded"; pulling a bar out drops any now-unsupported bars; per-item `VoxelShape` collision cache |

[ModRegistry](src/main/java/com/github/crittscott/somestacks/ModRegistry.java) registers all three blocks + BE types via `DeferredRegister`.

## Interaction system (client-side, rule-based)
The cleverest design piece. [ClientEvents](src/main/java/com/github/crittscott/somestacks/client/ClientEvents.java) hooks Forge's `PlayerInteractEvent` (empty / item / block click) at `HIGHEST` priority and feeds an [InteractionContext](src/main/java/com/github/crittscott/somestacks/client/interaction/InteractionContext.java) through a **chain-of-responsibility** [registry](src/main/java/com/github/crittscott/somestacks/client/interaction/InteractionRuleRegistry.java) of `InteractionRule`s (first match wins):
- `ModeCycleRule` (cycles the 4 stack modes via the **`V` keybind** — STORAGE / SINGLES / BAR / TOGGLE_PERMANENT, [StackMode](src/main/java/com/github/crittscott/somestacks/util/StackMode.java))
- `TogglePermanentRule`, `RotateBlockWithRedstoneTorchRule`, `RotateItemWithSoulTorchRule`, `DepositIntoClickedStackRule`, `DepositIntoAdjacentStackRule`, `PlaceAdjacentGenericRule`, `ExtractionRule`

Rules only decide intent client-side; the real mutation goes to the server as packets. [ModNetworking](src/main/java/com/github/crittscott/somestacks/network/ModNetworking.java) registers 8 message types (Deposit, Extract, PlaceAndDeposit, RotateBlock, RotateItem, TogglePermanent, ConfigSync, RenderOverride). There's also a server-side `RightClickBlockSuppressor`.

## Config & compatibility (the mod's real engineering challenge)
This mod's hard problem is **working across hundreds of other mods' items** without crashing. The [TODO.txt](TODO.txt) is essentially a 340-line mod-compatibility checklist, noting that e.g. evilcraft *crashes* on block break and chemlib is unsupported.

[ServerConfig](src/main/java/com/github/crittscott/somestacks/ServerConfig.java) (Forge config spec, synced to clients on login/reload via `ConfigSyncPkt`) exposes:
- Pile sort cooldown/max-stacks
- Enable/disable each of the 3 blocks
- **Disable-mods** list (defaults: `spartanfire`, `spartanweaponry` — both flagged as lag-causing in the TODO) and disable-items list
- **Render-mode overrides** — server-enforced render settings for "crash-prone items," which override resource-pack JSON.

## The data-driven render/texture layer (where the repetitive JSON lives)
Two `SimplePreparableReloadListener`s load per-mod JSON from `assets/somestacks/`:

1. **[ItemRenderOverrides](src/main/java/com/github/crittscott/somestacks/client/ItemRenderOverrides.java)** ← the **118 files** in [item_render_overrides/](src/main/resources/assets/somestacks/item_render_overrides/) (one per mod: `create.json`, `aether.json`, …). Each is a flat map of `"modid:item": { mode?, scale?, offset? }` — all fields optional. `mode` ∈ `2d/3d/block/gui` ([RenderMode](src/main/java/com/github/crittscott/somestacks/client/RenderMode.java)); controls how that item is drawn in a cube. Precedence: **server overrides > JSON > default**. Most entries are tiny (e.g. spawn eggs → `2d`, doors → `3d` scale 0.5).

2. **[BarTextureStore](src/main/java/com/github/crittscott/somestacks/client/BarTextureStore.java)** ← [textures/bars/*.json](src/main/resources/assets/somestacks/textures/bars/default.json). Maps each ingot to a base bar texture + tint. Notable: if tint is omitted it **auto-computes** one by loading the item's actual sprite via `NativeImage`, averaging opaque pixels, and brightening 10% — so unconfigured modded ingots still get a sensible bar color.

## Utilities & commands
- [util/](src/main/java/com/github/crittscott/somestacks/util/): `*CubeIdx` (index↔xyz + rotation/grounding math per block type), [ItemOps](src/main/java/com/github/crittscott/somestacks/util/ItemOps.java) (merge/drop/disabled-mod checks), [StackSort](src/main/java/com/github/crittscott/somestacks/util/StackSort.java) (sort by registry id → damage → NBT → count, partials first).
- `command/`: `ItemCommand`, `ModCommand`, `TestCommand` + `ItemTester`/`TestModsConfig` — dev tooling for batch-testing items across mods.

## Observations
- **Dead code / band-aids:** `StorageStackBER` has `RENDER_MARGIN_OFFSET = 0.0f/16.0f` with a TODO calling it "an unnecessary band aid." `mergeIntoHandler` in StorageStackBE has a self-described `// no-op: ensure valid slot` line.
- **Stray file:** a Dropbox "conflicted copy" stats JSON under [run/saves/](run/saves/) — leftover from playtesting, not source.
- **`meancolor.nb`** at root is a Mathematica notebook — presumably prototyping the tint-averaging algorithm now implemented in `BarTextureStore`.
- The 118 override files are repetitive by design (one flat map per supported mod); structure is uniform.
