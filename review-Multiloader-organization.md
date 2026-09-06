# Targeted review: multiloader organization

Scope: where code lives across `common`, `forge`, `fabric`, `neoforge` — is the split
in the right place, is anything stranded in a loader that belongs in common, and is any
"push it to common" effort misplaced. Reviewed as the tree stands; no git history consulted.

## Verdict

The `common` / loader boundary itself is in good shape. ~85% of the Java (11.4k of ~13.7k
main lines) is in `common`, `common` carries no loader imports (only the compile-time
`@ExpectPlatform` annotation), and the seam pattern (interface + static holder + setter,
installed from each entry point) is applied consistently and without over-engineering. There
is no visible case of a herculean push into `common` — nobody has contorted shared code to
erase a few lines of duplication.

The organizational debt is almost entirely on the other axis: **`neoforge` is a near
line-for-line transliteration of `forge`**, and a few things that the common seams were built
to hold have drifted back out into a loader. Details below, most useful first.

## Findings

### 1. Forge bypasses its own common render-packet seam (misplacement + dead wiring)

`common` provides `ClientRenderPacketSink` + `DefaultClientRenderPacketHandler` +
`ClientRenderPacketSink.apply(...)` precisely so the three S2C render packets
(`ConfigSyncPkt`, `RenderOverridePkt`, `WriteOverridesPkt`) are decoded once, in common.

- **Fabric** uses it: `FabricClientNetworking` registers `ClientRenderPacketSink::apply`.
- **NeoForge** uses it: `ClientSetup.deliverRenderPacket` → `ClientRenderPacketSink.apply`.
- **Forge** does **not**. `ForgePacketHandlers.handleConfigSync / handleRenderOverride /
  handleWriteOverrides` re-implement the whole body inline (DistExecutor + `StackState`
  writes + `ItemRenderOverrides` mutation + reset/put/dump branching), ~40 lines duplicated
  from `DefaultClientRenderPacketHandler` and `ClientRenderPacketSink.apply`.

Consequences: `forge/ClientSetup` line 29 calls
`ClientRenderPacketSink.setHandler(new DefaultClientRenderPacketHandler())`, but nothing on
Forge ever calls `.apply(...)`, so that handler is instantiated and never invoked — dead
wiring that reads as if the seam is in use. Forge also silently loses the
`serverOverrides` caching in `ClientRenderPacketSink.apply(ConfigSyncPkt)` that lets a late
`setHandler` re-apply. This is the clearest "wrong place" in the tree: shared logic that
already exists in `common`, re-derived in a loader. Route Forge through the same seam
NeoForge uses (it already has the `ForgePacketHandlers` indirection to host a
`deliverRenderPacket`-style dispatch).

### 2. `forge` and `neoforge` are twins (~1,000+ duplicated lines)

`neoforge/src/main` is 1,372 lines; the large majority is `forge/src/main` with
`net.minecraftforge.*` rewritten to `net.neoforged.*`. Identical-filename pairs and their
line counts (forge/neoforge):

| File | forge/neo | Nature of the difference |
| --- | --- | --- |
| `client/measure/ModelMeasurer` | 211/204 | comments only; logic identical |
| `block/BarColumnHandler` | 127/109 | import + `ItemHandlerHelper.copyStackWithSize` vs `stack.copyWithCount` |
| `block/SinglesColumnHandler` | 128/109 | same |
| `block/PileItemHandler` | 127/110 | same |
| `client/ClientEvents` | 127/117 | `Event.Result.DENY` vs `TriState.FALSE`; import |
| `server/Protection` | 167/96 | `MinecraftForge.EVENT_BUS`/`NeoForge.EVENT_BUS`, `Event.Result` vs `TriState`; forge copy carries the full javadoc, neo copy is stripped |
| `server/RightClickBlockSuppressor` | 67/64 | event-bus handle + result enum |
| `ModRegistry` | 96/83 | `DeferredRegister.create(ForgeRegistries…)` + `RegistryObject` vs `DeferredRegister.createBlocks` + `DeferredBlock` |
| `client/ClientSetup` | 56/72 | neo also hosts `deliverRenderPacket` (see finding 1) |
| `network/ModNetworking` | 71/67 | genuinely different: `SimpleChannel`/`ChannelBuilder` vs `PayloadRegistrar`/`RegisterPayloadHandlersEvent` |
| `client/KeyMappings` | 23/23 | forge passes `KeyConflictContext.IN_GAME`; otherwise identical |

Plus differently-named twins with the same shape: `Forge/NeoForgeCommandNetwork`,
`Forge/NeoForgeEditAuthority`, `Forge/NeoForgePlayerEditAuthority`, `forge/PlatformPathsImpl`
vs `neoforge/PlatformPathsImpl` (identical but for `net.minecraftforge.fml` →
`net.neoforged.fml`).

Genuinely loader-divergent (duplication is justified): `ModNetworking` (different transport
APIs), capability exposure (`ForgeCapabilityAttachment` via `AttachCapabilitiesEvent` +
`LazyOptional` vs `NeoForgeItemHandlers` via `RegisterCapabilitiesEvent.registerBlockEntity`),
and the fake-player construction in `EditAuthority` (Forge 1.21.1 dropped `FakePlayerFactory`,
NeoForge kept it).

Everything else in the table is package-rename duplication. This is consistent with the
project's stated preference for "some duplication over one more small module," and with the
memory note not to rewrite Forge's classic API to the NeoForge model — so this is a
deliberate cost, not an accident. But it is a large and growing one, and two items are worth
carving out regardless of whether a shared Forge-like source set is ever adopted:

- **`ForgeRenderPlatform` and `NeoForgeRenderPlatform` are byte-identical** except the class
  name and `net.minecraftforge.fml.ModList` vs `net.neoforged.fml.ModList`. Both bodies are
  otherwise pure vanilla (`Minecraft.getInstance().getItemColors()`,
  `model.getRenderPasses(...)`). Only the `ModList` line needs a loader.
- **`ModelMeasurer`** (~205 lines each) differs only in comments and the
  `IClientItemExtensions` import namespace.

If a `forgeLike` shared source set compiled into both loaders is off the table, at minimum
these two, plus `BarColumnHandler`/`SinglesColumnHandler`/`PileItemHandler` whose only real
divergence is one helper call, could be reduced by pulling their bodies into a shared static
helper in `common` that the thin loader class delegates to (the handlers already do this
pattern for their column logic via `BarColumn`/`SinglesColumn`/`StoragePile`).

### 3. GameTest suites duplicate the same way

`forge/src/gametest` and `neoforge/src/gametest` repeat finding 2 at test level. The
per-area `*GameTests` wrappers are near-identical (`@GameTestHolder(SomeStacks.MODID)` vs
`@GameTestHolder(…NeoForge.MODID)` + `@PrefixGameTestTemplate(false)`, and the event-bus
handle). Several full test bodies that as-built.md says must stay loader-native because they
"call `IItemHandler`" — `automationBackfillsWithoutDropping`,
`capabilityInsertionAnswersForThePositionItIsGiven`, `backfillKeepsABarItWouldNoLongerAccept`,
`capabilitySimulationDoesNotMutateExtraction`, `refusedRemovalStillLetsTheBarsAboveComeDown`
in `BarColumnGameTests`, and their peers elsewhere — are in fact verbatim between forge and
neoforge, because Forge's and NeoForge's `IItemHandler` / `ItemHandlerHelper` are
API-identical. The "keep it loader-native" rule genuinely separates Fabric (Transfer API) from
the Forge-likes; it does not justify the forge/neoforge split. These bodies could move to the
shared `*Checks` classes with the handler acquired through `GameTestSupport.capability(...)`
(which each loader already provides), leaving only the Fabric copies loader-native.

### 4. FTB Chunks protection story is inconsistent across loaders

- `forge`: no FTB integration at all — no dependency, no `FtbChunksProtection` class.
  Growth-time claim checks rely entirely on `ForgeEventFactory.onBlockPlace` firing
  `BlockEvent.EntityPlaceEvent`.
- `neoforge`: fires `EventHooks.onBlockPlace` **and** additionally consults
  `FtbChunksProtection.prevents(...)` directly in `NeoForgeEditAuthority.preparePlacement`.
- `fabric`: no place event exists, so `FtbChunksProtection` is the only growth-time check.

Forge and NeoForge use the same event mechanism, yet NeoForge adds a direct FTB consult that
Forge omits. Either the NeoForge direct consult is redundant (FTB Chunks' NeoForge build
vetoes the place event) or Forge has a claim-protection gap on automated growth. Pick one
story and make the two Forge-likes match. Separately, `fabric/FtbChunksProtection` and
`neoforge/FtbChunksProtection` have byte-identical `prevents(...)` bodies (only `isLoaded()`
differs); small, and neither has a `common` home since `common` has no FTB dependency, but
note it if a shared Forge-like set appears.

### 5. `common`'s ingot-tag default is Forge-flavored

`ServerConfig.ingotTagsRaw` defaults to `["forge:ingots*", "somestacks:ingots"]` in `common`.
Fabric and NeoForge each call `ServerConfig.useCommonIngotTagDefaults()` from their entry
point to swap in `c:ingots*`; Forge deliberately does not. This works, but it leaves a
loader-specific default baked into `common` and makes two of three loaders responsible for
remembering an override call at startup. Cleaner: no convention default in `common`, each
loader declares its own (Forge → `forge:`, Fabric/NeoForge → `c:`).

### 6. Stale rationale in `common/build.gradle`

The `modImplementation "net.fabricmc:fabric-loader"` block is commented "to use the Fabric
`@Environment` annotations, which get remapped to the correct annotations on each platform."
There are zero `@Environment` usages in `common/src/main`. Client-only classes are separated
by package and gated by each loader (`DistExecutor` on Forge, `FMLEnvironment.dist` on
NeoForge, entrypoint split on Fabric). Either the dependency is still needed for another
reason and the comment should say so, or the stated purpose is dead.

## Not problems — do not spend effort here

- **The seam pattern.** `ClientRenderPlatform`, `ClientGestures.Sender`, `CommandNetwork`,
  `EditAuthority`, `PlayerEditAuthority`, `ClientRenderPacketSink`, `ModelMeasurement`,
  `PlatformPaths` — interface in `common`, impl per loader, installed at startup. This is the
  idiomatic Architectury-without-runtime approach and it is not over-built. One
  `@ExpectPlatform` (`PlatformPaths`) alongside the setter style is fine.
- **Fabric reload-listener subclasses.** `FabricItemRenderOverrides`,
  `FabricBarTextureStore`, `FabricStackSoundData` are 15-line shells adding
  `IdentifiableResourceReloadListener` identity that Fabric's API requires. Minimal and
  correct; do not try to fold these away.
- **The `common` client package.** BERs, `CubeRenderHelper`, `measure/*`, `interaction/*`,
  `BarTextureStore`, `ItemRenderOverrides` are all loader-neutral (vanilla + MC client) and
  correctly shared. The interaction-rule registry with per-loader `InteractionContext`
  adapters is a clean split.
- **Per-loader networking.** `ModNetworking` on each side is genuinely different transport
  API; keeping three copies is right.
- **Keeping Forge on the classic API.** Per the project's own standing decision.

## Suggested priority

1. Finding 1 — route Forge through `ClientRenderPacketSink`; delete the inline duplicate and
   the dead `setHandler` call, or wire the call up. Small, unambiguous.
2. Finding 4 — resolve the FTB/Forge vs FTB/NeoForge asymmetry (correctness question, not
   just tidiness).
3. Findings 5 / 6 — trivial cleanups.
4. Findings 2 / 3 — the big one. Decide explicitly: accept the forge/neoforge twin cost as
   policy, or introduce a shared Forge-like source set. If accepting it, still pull the
   handful of bodies whose only divergence is an import (`*RenderPlatform`, `ModelMeasurer`,
   the three item handlers, the `IItemHandler`-facing GameTest bodies) into shared helpers.
