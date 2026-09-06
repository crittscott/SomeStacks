# Targeted code review: magic numbers, magic strings, i18n, and class-name references

Scope: `common/`, `forge/`, `fabric/`, `neoforge/` main sources. `other/` (bundled FTB
Chunks) and `build/` were excluded. This is an assessment only; no code was changed.

## Executive summary

The codebase is, on the whole, unusually disciplined here:

* **All user-facing text is localized.** Every player-visible string routes through
  `Component.translatable`, and every key resolves in
  `common/src/main/resources/assets/somestacks/lang/en_us.json` (140+ entries, including
  command help, disconnect reasons, and mode names). No hardcoded English UI text was
  found in Java.
* **No fully-qualified class names in code bodies.** A sweep for `net.minecraft.*`,
  `net.minecraftforge.*`, `net.neoforged.*`, `com.mojang.*` outside `import` lines
  returned nothing. The only unavoidable string class reference is the Mixin target
  (`method = "startUseItem"` in `MinecraftMixin`), which the Mixin API requires.
* **Most magic numbers are already extracted** into documented `private static final`
  constants — `CubeRenderHelper`, `AutoRenderProfiles`, `ServerConfig`, `ItemOps`,
  `GestureThrottle`, `ViewRays`, `PacketBoundary`, `RenderGalleryGenerator`, and the
  packet classes are all exemplary.

What remains is a set of smaller, mostly cosmetic issues concentrated in a few areas.
None is a correctness risk. They are listed below roughly in descending order of how
often the value recurs.

---

## Magic numbers

### 1. "16 pixels per block" divisor — pervasive, no constant

`/ 16.0` / `/ 16.0f` (model-pixels-to-block-fraction) appears ~40 times across:

* `client/BarStackBER.java` (6), `client/StorageStackBER.java` (3),
  `client/SinglesStackBER.java` (3), `client/CubeRenderHelper.java` (1)
* `util/BarCubeIdx.java` (12), `util/SinglesCubeIdx.java` (10),
  `util/StorageCubeIdx.java` (6)

`BarCubeIdx` has a comment ("Bar geometry is expressed in model pixels (1/16 of a
block)") but no named constant. A shared `PIXELS_PER_BLOCK = 16.0` (or a
`px(double)` helper) would remove the most-repeated bare literal in the mod. The
per-cube-index classes already name every *other* geometry value (`CELL_PIXELS`,
`BAR_HEIGHT`, `EW_STARTS_X`, `STARTS`, …), so 16 is the odd one out.

### 2. Quarter-turn rotation: `% 4` and `* 90` — no shared constant

The "4 rotations / 90° per rotation" idea is spelled with bare literals in ~11 places:

* `block/StorageStackBE.java:127,162`, `block/SinglesStackBE.java:153,173`
  (`rotation % 4`)
* `network/RotateBlockPkt.java:76,80`, `network/RotateItemPkt.java:79`
  (`(getRotation() + 1) % 4`)
* `util/SinglesCubeIdx.java:128,257`, `util/StorageCubeIdx.java:80`
  (`switch (rotation % 4)`, `(4 - blockRotation % 4) % 4`)
* `client/SinglesStackBER.java:59` — `Axis.YP.rotationDegrees(cubeRotation * 90)`
  (bare `90`, whereas `RotateBlockPkt`/`RotateItemPkt` name it `DEGREES_PER_ROTATION`)

`DEGREES_PER_ROTATION = 90` is defined *twice* (once in each rotate packet) and not
reused by the renderer. A single `Rotations` constant pair (`COUNT = 4`,
`DEGREES = 90`) in `util` would unify all of this. Note the `4` here is semantically
"number of quarter turns," distinct from `SinglesCubeIdx.GRID_EDGE` which also happens
to be 4 — worth keeping them as separate named values so the coincidence is not load-
bearing.

### 3. Comparator conversion constant `14.0` — duplicated in 3 files

`Mth.floor(fill * 14.0) + 1` appears verbatim in
`block/StoragePile.java:233`, `block/BarColumn.java:234`, and
`block/SinglesColumn.java:225`. Each has its own paragraph of Javadoc explaining
"vanilla's container conversion." This is vanilla's
`AbstractContainerMenu` redstone formula; a shared
`comparatorSignal(double fill)` helper (or at least a named `SIGNAL_STEPS = 14`)
would collapse three copies of both the literal and the prose into one.

### 4. `client/BarStackBER.java` — sprite-grid layout numbers

`emitBar` maps a bar sprite laid out on a "32-unit grid":

```
region(u0, v0, uRange, vRange, 0f, 0f,  24f, 12f)   // top/bottom
region(u0, v0, uRange, vRange, 0f, 12f, 24f, 20f)   // long side
region(u0, v0, uRange, vRange, 0f, 20f, 12f, 28f)   // end cap
```

and `region()` divides by `32f` twice inline. There is a good explanatory comment, but
24/12/20/28/32 are passed positionally with no names. Given the block's model geometry
(6×2×3 / 3×2×6 bars) these are derivable; named constants
(`SPRITE_GRID = 32f`, and the three `UvRegion` rectangles as `static final`) would make
the mapping checkable against the texture.

### 5. `client/CubeRenderHelper.java` — vertex-format internals

* `emitQuads`: `int base = i * 8;` then `vertices[base + 4]`, `vertices[base + 5]`.
  The `8` is `DefaultVertexFormat.BLOCK`'s int stride and 4/5 are the U/V slots. The
  surrounding comment explains winding, not the layout. A short `// stride 8 ints:
  x,y,z,color,u,v,light,normal` or named offsets would help the next reader.
* `for (int i = 0; i < 4; i++)` — 4 vertices per quad, bare, in two places
  (`emitQuads`, `emitBackgroundFace` writes 4 explicitly).
* `RANDOM.setSeed(42L)` twice (lines 319, 322). 42 is a conventional MC model-random
  seed; a `MODEL_RANDOM_SEED` constant would signal that it is deliberate and must
  match between the two calls.

### 6. `util/PlayerReach.java` — reach distances inline

```java
private static Provider provider = player -> player.getAbilities().instabuild ? 5.0 : 4.5;
```

The creative/survival reach values (5.0 / 4.5) are bare literals in a lambda. These are
the fallback used before a loader installs its attribute-based provider; still worth
`CREATIVE_REACH` / `SURVIVAL_REACH` constants, especially as `ViewRays.BLOCK_CROSSING`
right next door is fully named and documented.

### 7. `client/BarTextureStore.java` — color math

* `if (a > 127)` (line 262) — alpha "at least half opaque" threshold, unnamed.
* Mixed conventions: `analyzePixelsForTint` uses `255` and `Math.min(255, …)` while the
  rest of the file (and `CubeRenderHelper`) uses `0xFF`. Pick one.
* The 16/8/24-shift + `& 0xFF` unpack/pack pattern is repeated in `BarTextureData`
  (4 accessors), `multiplyColors`, `analyzePixelsForTint`, and again in
  `CubeRenderHelper.emitQuads`. Idiomatic, but a tiny shared `ARGB` helper would remove
  ~6 copies. Low priority.
* `parseColor`: `length() == 6` / `== 8` for RRGGBB vs AARRGGBB — acceptable as-is.

---

## Magic strings

### 8. Brigadier argument names repeated as literals — `command/SsCommand.java`

Argument/key names are typed as string literals at every `Commands.argument(...)` and
every `…ArgumentType.get…(ctx, "…")` call site:

* `"modid"` ~10×, `"item"` ~8×, `"mode"` 4×, `"scale"` 4×, `"x"`/`"y"`/`"z"` 3× each,
  `"tag"` 4×, `"command"` 2× (`SsHelp`).

A typo in one of the paired `argument("x")` / `getFloat(ctx, "x")` uses fails only at
runtime. The label translation keys in the same file are already hoisted to constants
(`GEN_MODS_LABEL`, `DISABLED_ITEMS_LABEL`, …); the arg names deserve the same
(`ARG_MODID`, `ARG_ITEM`, …). This is the single largest cluster of magic strings in
the mod.

### 9. Sub-command name table duplicated between `SsCommand` and `SsHelp`

`SsHelp.Topic` hard-codes each sub-command's name (`"item"`, `"gallery"`,
`"ingotgallery"`, `"write"`, `"reload"`, `"gen"`, `"deny"`, `"ingot"`, `"help"`) and
its usage syntax (`"/ss item <item> <mode> [<scale> [<x> <y> [<z>]]]"`, …). These must
be kept in lockstep by hand with the actual `Commands.literal(...)` tree in
`SsCommand`. Nothing enforces the correspondence. The class Javadoc acknowledges the
coupling ("`SsHelp` carries each subcommand's forms"), so it is deliberate, but it is a
maintenance hazard worth flagging.

Related i18n note: those `/ss …` **usage strings are not in `en_us.json`** — they are
emitted as the argument to `somestacks.command.help.usage`. Command syntax is
conventionally left untranslated, so this is defensible, but it is the one place
user-visible text lives in Java rather than the lang file. If the project wants strict
"all display text is a translation key," these are the exceptions.

### 10. Config sub-folder name `"somestacks/…"` instead of `MODID`

`SomeStacksCommon.MODID = "somestacks"` exists and is used correctly for every
`ResourceLocation`. But the per-mod config directory is built from a raw literal in
four places:

* `client/ItemRenderOverrides.java:53` — `"somestacks/item_overrides.json"`
* `client/ItemRenderOverrides.java:54` — `"somestacks/generated_overrides"`
* `client/measure/AutoRenderProfiles.java:197` — `"somestacks/measured_cache.json"`
* `ServerOverridesLoader.java:25` — `"somestacks/server_item_overrides"`

`PlatformPaths.configFolder().resolve(MODID).resolve("…")` would tie them to the
existing constant.

### 11. JSON field-name literals — inconsistent handling

* `util/OverrideJsonCodec.java` does this right: `FIELD_MODE`, `FIELD_SCALE`,
  `FIELD_OFFSET` constants, used on both read and write.
* `client/BarTextureStore.java` uses inline `"texture"`, `"tint"`, `"_comment"` in
  `parseEntry` / `prepare`.
* `client/measure/AutoRenderProfiles.java` uses inline `"format"`, `"packs"`,
  `"versions"`, `"entries"` — each appears in both `saveCache` and `loadCacheOnce`, so
  a read/write drift is possible.

Same schema-key concern as the Brigadier names: worth constants for the read/write
pairs.

### 12. Keybind translation keys duplicated across loaders

`"key.somestacks.stack_mode"` and `"key.categories.somestacks"` are typed as literals
in `forge/.../client/KeyMappings.java`, `fabric/.../client/FabricKeyMappings.java`, and
`neoforge/.../client/KeyMappings.java` (three copies each). Both keys exist in
`en_us.json`. A pair of constants in a common client class would remove the
triplication.

### 13. Gesture trigger items not centralized

The rotate gestures are defined by `Items.REDSTONE_TORCH` / `Items.SOUL_TORCH` in the
client rule (`client/interaction/RotateBlockWithRedstoneTorchRule.java`,
`RotateItemWithSoulTorchRule.java`) and independently re-checked in the server handler
(`network/RotateBlockPkt.java:59`, `network/RotateItemPkt.java:62`). These are typed
constants rather than strings, so it is mild, but the client and server "what item arms
this gesture" facts are not shared — a change needs edits in two files that do not
reference each other.

---

## Areas checked and found clean

* User-facing messages, disconnect reasons, command output, mode/state names, help
  text — all translation keys, all present in `en_us.json`.
* `RenderMode` (`2d`/`3d`/`block`/`gui`), `StackMode`, `BlockType` — string ids and
  translation keys held on the enum, resolved via `getId()` / `getTranslationKey()`,
  never re-typed at call sites.
* NBT tag names — `TAG_ITEMS`, `TAG_ROTATION`, `TAG_PERMANENT` constants in
  `StorageStackBE` (and siblings).
* Packet channel/type ids — built from `MODID` + a single literal path each; protocol
  version bump handled by renaming the id (`"protocol_2"`).
* Bounds and tuning values — `OverrideJsonCodec` (`MIN_SCALE`/`MAX_SCALE`/…),
  `ServerConfig` (`MAX_PILE_HEIGHT_LIMIT`, `GALLERY_PERMISSION_LEVEL_MAX`,
  defaults), `AutoRenderProfiles` (`TARGET_FILL`, `FLAT_RATIO`, `MIN_EXTENT`,
  `BUTTON_FIT_SCALE`, `CACHE_FORMAT_VERSION`), `ItemOps` (`MAX_LIGHT_LEVEL`,
  `LIGHT_SHARE_DIVISOR`), `GestureThrottle` (`ROTATION_SOUND_INTERVAL`),
  `PacketBoundary` (`REACH_PADDING`), `ViewRays` (`BLOCK_CROSSING`),
  `RenderGalleryGenerator` (`MOD_SPACING`), `ConfigSyncPkt` (`MAX_OVERRIDE_ENTRIES`) —
  all named and documented.
* `CubeRenderHelper` geometry — `FACE_OFFSET`, `FACE_DEPTH`, `ART_INSET`,
  `CUBE_INSET`, `CELL_RENDER_SCALE`, `STORAGE_CELL_RENDER_SCALE`, `CELL_LOCAL_SIZE`,
  `CELL_LOCAL_CENTRE` — a model of how to do this. (Its `8` and `4` vertex-format
  literals in item #5 are the only gap.)

---

## Suggested priority

1. **#8** (Brigadier arg-name constants) — highest churn, real typo risk.
2. **#1** (`PIXELS_PER_BLOCK`) — most-repeated literal, trivial fix.
3. **#3** (`14.0` comparator helper) and **#2** (rotation constants) — dedupe.
4. **#10 / #11 / #12** — small consistency fixes.
5. Everything else — cosmetic, do opportunistically.
