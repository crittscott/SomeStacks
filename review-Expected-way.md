# Code review: "Expected-way"

Scope: does SomeStacks use the standard facilities each loader (Forge, Fabric,
NeoForge) and vanilla Minecraft provide, rather than hand-rolled substitutes?
Minecraft 1.21.1, Architectury multi-loader layout (`common` + `forge` + `fabric`
+ `neoforge`). Assessment only, no changes made. Git history not consulted.

## Overall

The mod is, for the most part, built the way an experienced modder would expect.
Registration, block-entity sync, scheduled ticks, capability/transfer exposure,
client renderers, key mappings, reload listeners, commands, and the three
networking stacks are all done through the intended APIs. The deviations that
exist are mostly deliberate cross-loader compromises, and the code documents them.
The findings below are ordered roughly by how much they cut against "the
Forge/Fabric/Minecraft way."

## What already follows the expected way (no action)

- **Registration.** Forge `DeferredRegister` on `ForgeRegistries`; NeoForge
  `DeferredRegister.Blocks`/`DeferredBlock`/`DeferredHolder`; Fabric
  `Registry.register` + `FabricBlockEntityTypeBuilder`. All idiomatic for their
  loader.
- **Block entity client sync.** `getUpdatePacket` =
  `ClientboundBlockEntityDataPacket.create(this)`, `getUpdateTag` = `saveAdditional`,
  `level.sendBlockUpdated(..., UPDATE_ALL)`. Textbook.
- **The "settle" pass** is a block *scheduled tick* (`level.scheduleTick(base,
  block, 1)` + `Block.tick` override), not a per-tick `BlockEntityTicker`. This is
  a real vanilla mechanism and the server-friendly choice; good.
- **Light level** carried as a blockstate `IntegerProperty` so lighting updates
  travel the normal blockstate path. Correct.
- **NeoForge capabilities:** `RegisterCapabilitiesEvent.registerBlockEntity(
  Capabilities.ItemHandler.BLOCK, type, ...)` — exactly right.
- **Fabric transfer:** `ItemStorage.SIDED.registerForBlockEntity` with a
  `SnapshotParticipant` that stages edits in the transaction and applies them in
  `onFinalCommit`. This is the correct, and frequently gotten-wrong, Fabric
  Transfer API pattern; it is done carefully here.
- **Forge networking:** `ChannelBuilder…simpleChannel()` +
  `.protocol(NetworkProtocol.PLAY).serverbound()/.clientbound().addMain(...)` with
  an exact protocol-version handshake. The Forge 1.21.1 SimpleChannel idiom.
- **NeoForge networking:** `RegisterPayloadHandlersEvent` + `PayloadRegistrar`
  + `playToServer`/`playToClient`, relying on NeoForge's automatic
  disconnect for an unregistered non-optional payload instead of a bespoke
  handshake. That *is* the NeoForge way.
- **Fabric networking:** `PayloadTypeRegistry` + `ServerPlayNetworking` /
  `ClientPlayNetworking` global receivers, hopping to the main thread with
  `server.execute` / `client.execute`.
- **Client renderers** are `BlockEntityRenderer` implementations using
  `PoseStack` / `MultiBufferSource` / `BlockRenderDispatcher` /
  `LevelRenderer.getLightColor`, and hand `3d`/`gui`/`block` render modes back to
  vanilla's own item renderers. `ClientRenderPlatform.renderPasses` deliberately
  routes multi-layer items through the loader's item renderer.
- **Key mappings:** `RegisterKeyMappingsEvent` (Forge/NeoForge),
  `KeyBindingHelper.registerKeyBinding` (Fabric).
- **Reload listeners:** `RegisterClientReloadListenersEvent` /
  `ResourceManagerHelper`; `StackSoundData extends SimpleJsonResourceReloadListener`
  registered through `AddReloadListenerEvent` / server-data `ResourceManagerHelper`.
- **Commands** are pure Brigadier on `CommandSourceStack`, registered from
  `RegisterCommandsEvent` / `CommandRegistrationCallback`, with
  `SharedSuggestionProvider` suggestions and permission-level `requires` gates.
- **Mod metadata** is in the right place for 1.21.1: Forge `META-INF/mods.toml`,
  NeoForge `META-INF/neoforge.mods.toml`, `fabric.mod.json`; `pack_format: 34`.
- **NBT item store** (`StackItemStorage`) reproduces Forge `ItemStackHandler`'s
  `{Size, Items:[{Slot,…}]}` shape and insert/extract semantics — a reasonable
  loader-neutral stand-in given `common` has no Forge API.

## Findings

### 1. Server config is a hand-rolled JSON reader instead of the loader config system — Medium

`ServerConfig` is a bespoke Gson reader/writer. `SomeStacks`/`SomeStacksNeoForge`
load it from `event.getServer().getWorldPath(LevelResource.ROOT)
.resolve("serverconfig")/somestacks-server.json` on `ServerAboutToStartEvent`;
Fabric does the same on `SERVER_STARTING`.

Forge and NeoForge both ship a first-class server-config facility
(`ModConfigSpec` + `ModContainer/ModLoadingContext.registerConfig(
ModConfig.Type.SERVER, spec)`). For `Type.SERVER` it already provides everything
this class re-implements by hand: per-world placement under `serverconfig/`,
range/validation enforcement, automatic re-read on `/reload`, config-screen
integration, and automatic sync of the SERVER config to joining clients (which
would subsume `ConfigSyncPkt`'s stack-enable booleans). The mod instead
duplicates the world path, the range clamping (`clamp`, `Math.max(1, …)`), the
"edit then save immediately" flow, and a `/ss reload` that re-broadcasts.

The counter-arguments are legitimate and worth stating: (a) Fabric has no
built-in config, so a shared implementation keeps one code path; (b) the
command-driven, live-re-baked *list* editing (`/ss deny`, `/ss ingot`, …, with
`bakeServerLists`/`rebakeIngotTags`) genuinely exceeds what `ModConfigSpec`
does comfortably. A reasonable middle position for the Forge/NeoForge side is a
`ModConfigSpec` for the scalar values (`max_pile_height`, the three enable flags,
the `render_gallery` numbers/level) with the pattern lists kept custom. As it
stands this is the single largest "not the Forge way" item, and per the project's
own "if Forge/Minecraft has a facility, use it" principle it deserves a conscious
decision rather than drift.

Sub-point: `ServerConfig.save()` writes directly over the file with
`Files.writeString`; a crash mid-write truncates the config. The loader config
system (NightConfig) writes atomically. Low severity, easy to fix with a
write-temp-then-move if the custom reader stays.

### 2. Forge and Fabric build a bare `ServerPlayer` as the automation actor instead of a fake-player helper — Medium

`NeoForgeEditAuthority` does this the expected way:
`FakePlayerFactory.getMinecraft(level)`. But `ForgeEditAuthority` and
`FabricEditAuthority` both do:

```java
new ServerPlayer(server, level, PROFILE, ClientInformation.createDefault())
```

kept in a `WeakHashMap<ServerLevel, ServerPlayer>`. The `ForgeEditAuthority`
Javadoc asserts "Forge 1.21.1 no longer ships a FakePlayer helper" — that is
almost certainly incorrect; Forge has shipped
`net.minecraftforge.common.util.FakePlayerFactory` / `FakePlayer` for many years
and the NeoForge side (a fork of the same code) still has it. Consequences of a
raw `ServerPlayer`:

- Its `connection` is null. Any protection/logging listener that dereferences
  `player.connection` (or calls something that does) on the `BlockEvent.BreakEvent`
  / `onBlockPlace` path will NPE.
- Many claim, anti-grief, and anti-cheat mods special-case
  `player instanceof FakePlayer` (to allow or to log machine edits). A raw
  `ServerPlayer` is invisible to that check, so the automation actor behaves
  inconsistently between NeoForge (recognized) and Forge/Fabric (not).

Fabric has no first-party fake player; the common community choice is
`FakePlayerFactory` from Fabric-side compat libs, or at minimum a subclass that
stubs `connection` and is documented as a fake. But the Forge path should use
`FakePlayerFactory` and match NeoForge. Verify the Forge API name against Forge's
own published sources before acting.

### 3. Custom packets replace vanilla interaction, then the server re-fires `PlayerInteractEvent.RightClickBlock` by hand — structural, informational

The gesture model is: the client cancels the vanilla interaction in a
`PlayerInteractEvent.RightClick*` / `UseBlockCallback` handler and sends a bespoke
packet; the block's `useWithoutItem` / `useItemOn` are overridden to no-ops
(`CONSUME`/`SUCCESS`); the server handler then *constructs and posts a
`PlayerInteractEvent.RightClickBlock`* (`Protection.rightClickBlock`) so claim and
logging mods still see something, and `RightClickBlockSuppressor` suppresses the
trailing real vanilla click that arrives in the same tick.

This is a lot of machinery to stand in for what vanilla and the loaders do for
free when a block simply handles `useItemOn`/`use` and returns
`CONSUME`/`SUCCESS`: the interaction events fire naturally, with a real hit
result and real cancellation-result plumbing, and there is no trailing click to
suppress. Manually posting `PlayerInteractEvent.RightClickBlock` from server code
is fragile — listeners can reasonably assume a genuine click context, a non-null
ray hit, correct `getUseBlock`/`getUseItem` defaults, `setCancellationResult`
semantics, etc.

I am not calling for a rewrite — the design buys the mod fine control over
multi-position protection consults (`claimInteraction`/`claimItemUse`/
`claimPlacement` over an array of positions), reach re-validation, and
per-tick gesture throttling that would be awkward to hang off `useItemOn`. But
this is the part of the codebase that most works *against* the grain of the
platform, the `Protection` + `RightClickBlockSuppressor` pair is duplicated
per-loader, and the correctness of the whole thing rests on an undocumented
ordering guarantee (mod packet enqueued before the vanilla
`ServerboundUseItemOnPacket` in the same tick). Worth a deliberate revisit of
whether the block-`use` path could carry more of this.

### 4. Packet serialization is hand-written `buf.writeX`/`readX` rather than composed `StreamCodec`s — Low/Medium

Every payload defines `STREAM_CODEC = StreamCodec.ofMember(Pkt::encode,
Pkt::decode)` with imperative `encode`/`decode` methods. On 1.21 the expected
style is composition: `StreamCodec.composite(...)` with `BlockPos.STREAM_CODEC`,
`ByteBufCodecs.*`, `ByteBufCodecs.optional`, `ByteBufCodecs.collection`, etc.
`ConfigSyncPkt` in particular hand-rolls boolean-prefixed optionals for three
fields and a manual size-prefixed map with a manual `MAX_OVERRIDE_ENTRIES`
guard — all of which `ByteBufCodecs.map` / `.optional` / a bounded collection
codec express declaratively and less error-prone. Not wrong, but not how a
1.21 packet is normally written.

### 5. Shared stream codecs typed over `FriendlyByteBuf` force an unchecked cast on Forge — Low

`DepositPkt.STREAM_CODEC` etc. are declared `StreamCodec<FriendlyByteBuf, T>`.
Fabric's `PayloadTypeRegistry` and NeoForge's `PayloadRegistrar` accept that via
`? super RegistryFriendlyByteBuf`, but Forge's `addMain` wants the invariant
`StreamCodec<RegistryFriendlyByteBuf, T>`, so `ModNetworking.registryCodec` does
an unchecked double-cast (documented, and safe because these codecs only touch
the buffer as a plain `FriendlyByteBuf`). Since 1.21's `ByteBufCodecs`/
`StreamCodec.composite` world is built around `RegistryFriendlyByteBuf` anyway,
declaring the shared `STREAM_CODEC` as `StreamCodec<RegistryFriendlyByteBuf, T>`
removes the cast on Forge and costs nothing on the other two. Minor.

### 6. Fabric empty-hand air click is handled twice — Low

`FabricClientEvents` registers `UseItemCallback` and, when the hand is empty,
runs `processEmptyHandRules`. `MinecraftMixin` *also* injects into
`Minecraft.startUseItem` @HEAD and runs `processEmptyHandRules` for the
both-hands-empty + `HitResult.MISS` case. If both fire for the same click the
rules run twice (currently harmless because the only empty-hand rule is the mode
cycle, which is idempotent-ish, but it is a latent double-trigger). Worth
confirming exactly which vanilla path each covers and collapsing to one, or
documenting why both are needed. The mixin itself is fine as a concept — Fabric
has no `RightClickEmpty` equivalent, the injection is client-only, minimal, and
`@HEAD` — this is just about the overlap with the callback.

### 7. `CommonRegistry` is mutable public static fields wired up per loader — Low

`public static Supplier<Block> STORAGE_STACK_BLOCK;` etc., assigned from each
loader's `ModRegistry`/`FabricRegistry` static initializer. This is a common
Architectury-style bridge, but it is unguarded global mutable state; a
missing/late `init()` yields NPEs deep in common code. An `@ExpectPlatform`
accessor (already used for `PlatformPaths`) or a single `set`-once holder with a
null check would be a little more defensive. Not urgent.

## Minor notes

- **Block drops.** `onRemove` calls `ItemOps.dropAllItems(...)` directly instead
  of a loot table. Vanilla's expected drop path is a `minecraft:block/...` loot
  table even for special cases. Defensible here because the blocks have no
  `BlockItem` and cannot be re-obtained, so there is nothing a loot table would
  add; noted only for completeness.
- **No `BlockItem` / creative tab / recipes.** Deliberate and documented in
  `ModRegistry`. Correct call for this design; not a finding.
- **`ServerConfig` off-thread claim.** Javadoc says rebakes "can run off the main
  thread"; the callers (`TagsUpdatedEvent`, `END_DATA_PACK_RELOAD`,
  `ServerAboutToStartEvent`) are all on the server thread. The `volatile` sets +
  `AtomicInteger ingotGeneration` are harmless but the stated rationale does not
  currently apply.
- **`DistExecutor.unsafeRunWhenOn`** in `ForgePacketHandlers` for client-only
  payload handling is the accepted Forge sidedness guard; `safeRunWhenOn` has its
  own classloading caveats, so this is fine. NeoForge's `clientReceiver`
  indirection + `FMLEnvironment.dist` check is likewise fine.
- **`RenderGalleryGenerator`** (not read in depth) is tick-budgeted via
  `render_gallery.placements_per_tick` and a `ServerTickEvent`/`END_SERVER_TICK`
  drain — the server-friendly approach for a bulk world edit. The `/ss gallery`
  tree is also correctly gated behind an off-by-default flag plus a configurable
  permission level.
- **Forge capability attachment** (`AttachCapabilitiesEvent` + `LazyOptional` +
  `event.addListener(lazy::invalidate)`) is the correct classic-API pattern for
  Forge 1.21.1, consistent with this fork keeping the classic capability API.
