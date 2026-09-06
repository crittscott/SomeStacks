# Targeted code review: loader divergence

Scope: the `common`, `forge`, `fabric`, and `neoforge` source trees as they stand.
Question asked: does the mod diverge between loaders only where it must, or is there
residue of an earlier single-loader (Forge-only) state, and copy-paste that has drifted?

Bottom line: the architecture is sound. `common` owns the mechanics; each loader module
is a thin adapter installed through a small set of seams (`WorldEdits.setAuthority`,
`PlayerEdits.setAuthority`, `PlayerReach.setProvider`, `CommandNetwork.setHandler`,
`ClientRenderPlatform.setBackend`, `ModelMeasurement.setBackend`, `ClientGestures.setSender`,
`CommonRegistry`, `PlatformPaths` `@ExpectPlatform`). Most divergence is genuine platform
divergence. But there is a clear pocket of Forge-origin residue in the client-bound packet
path, one triplicated helper, some stale pre-NeoForge documentation, and a large volume of
Forge/NeoForge classes that are byte-identical except for a package prefix.

---

## 1. Divergence that is correct and necessary

These differ by loader for real reasons and are handled cleanly:

- **Registration**: `ModRegistry` (Forge `DeferredRegister`/`RegistryObject`), `ModRegistry`
  (NeoForge `DeferredRegister.Blocks`/`DeferredHolder`), `FabricRegistry` (direct
  `Registry.register`). All three converge on `CommonRegistry`'s `Supplier` fields via an
  identical `static{}`/`init()` shape. Good.
- **Networking transport**: Forge `SimpleChannel` + exact protocol-version gate; NeoForge
  `PayloadRegistrar`; Fabric `PayloadTypeRegistry` + an explicit `ProtocolPkt` handshake
  (needed only on Fabric, correctly referenced only from `common` + `fabric`). The packet
  records and stream codecs themselves are shared.
- **Edit authority**: the `EditAuthority` / `PlayerEditAuthority` split isolates exactly the
  loader-specific decisions — who the automation actor is, and which veto event fires
  (`ForgeEventFactory.onBlockPlace` / `EventHooks.onBlockPlace` / no generic Fabric event,
  Forge/NeoForge break event vs Fabric `PlayerBlockBreakEvents`). The Fabric class documents
  why placement protection has to consult FTB Chunks directly. This is model divergence done
  right.
- **Item exposure**: Forge `IItemHandler` via `AttachCapabilitiesEvent` + `LazyOptional`,
  NeoForge `IItemHandler` via `RegisterCapabilitiesEvent.registerBlockEntity`, Fabric
  `ItemStorage.SIDED` + a transaction-based `FabricRunItemStorage`. Genuinely three
  different APIs.
- **Client reload listeners**: Fabric needs `IdentifiableResourceReloadListener`, so
  `FabricItemRenderOverrides` / `FabricBarTextureStore` / `FabricStackSoundData` /
  `FabricRenderCacheReloadListener` are thin identity subclasses of the shared classes;
  Forge and NeoForge register the shared classes directly. Correct and minimal.
- **`MinecraftMixin`**: Fabric-only, fills the missing empty-hand open-air right-click
  callback. Necessary; Forge/NeoForge get it from `PlayerInteractEvent.RightClickEmpty`.
- **`RightClickEmpty` handling**: Forge cancels, NeoForge notes the event is a pure
  notification and does not. Real platform behavior difference, documented inline.
- **Config tag defaults**: `ServerConfig.useCommonIngotTagDefaults()` is called by Fabric
  and NeoForge (which use `c:` tags) and deliberately not by Forge (which keeps `forge:`
  tags). Documented.
- **`PlatformPathsImpl`** trio: three one-line `@ExpectPlatform` bodies. Minimal.

---

## 2. Mono-loader residue (Forge was here first)

### 2.1 Forge client-bound render packets bypass the shared sink

`common` provides a complete loader-neutral client-bound handling path:
`ClientRenderPacketSink` (retains `ConfigSyncPkt` / `RenderOverridePkt` / `WriteOverridesPkt`
state and applies it) plus `DefaultClientRenderPacketHandler`.

- **Fabric** uses it: `FabricClientNetworking` registers `ClientRenderPacketSink::apply` for
  all three payloads.
- **NeoForge** uses it: `ClientSetup.deliverRenderPacket` routes to
  `ClientRenderPacketSink.apply`.
- **Forge** does not. `ForgePacketHandlers.handleConfigSync` / `handleRenderOverride` /
  `handleWriteOverrides` re-implement the bodies of `ClientRenderPacketSink.apply` and
  `DefaultClientRenderPacketHandler` inline (same `StackState.setBlockEnabled` calls, same
  `ItemRenderOverrides.putUser` / `removeUser` / `handleWriteRequest` / `handleDumpRequest`,
  same `OverrideJsonCodec.sanitize`, same `RenderMode.fromString`).

Compounding it: `forge/.../client/ClientSetup.java:29` calls
`ClientRenderPacketSink.setHandler(new DefaultClientRenderPacketHandler())` — the exact line
Fabric and NeoForge use — but nothing on Forge ever calls `ClientRenderPacketSink.apply(...)`,
so on Forge that handler is only ever touched by the one-time empty
`setServerOverrides(Map.of())` inside `setHandler`. The line makes Forge look wired into the
shared sink when it is not; the real work is the duplicated code in `ForgePacketHandlers`.

This is the clearest single piece of residue: `ForgePacketHandlers` predates the
`ClientRenderPacketSink` abstraction that was later carved out for Fabric and reused by
NeoForge, and Forge was never migrated. Forge's `SimpleChannel` registers per-type handlers,
so the three client-bound entries can each call the typed `ClientRenderPacketSink.apply`
overload directly (behind the existing `DistExecutor` client guard), after which
`ForgePacketHandlers` keeps only the six server-bound unwrappers and the dead `setHandler`
line in `ClientSetup` becomes a live one.

### 2.2 `buildConfigSync()` is triplicated

Identical private method in three places:

- `forge/.../SomeStacks.java:118`
- `neoforge/.../SomeStacksNeoForge.java:114`
- `fabric/.../network/FabricNetworking.java:72`

```java
return new ConfigSyncPkt(
        ServerConfig.enableStorageStackBlock(),
        ServerConfig.enableSinglesStackBlock(),
        ServerConfig.enableBarStackBlock(),
        ServerOverridesLoader.load());
```

Only the broadcast around it is loader-specific (`PacketDistributor.ALL.noArg()` /
`sendToAllPlayers` / per-player loop). The packet construction belongs in `common` — a
`ConfigSyncPkt.current()` factory, or a method on `ServerOverridesLoader`. `ConfigSyncPkt`
already has sibling factories (`ConfigSyncPkt.java:90`, `:105`), so this one just never made
it back to `common` when the second and third loaders were added.

### 2.3 Stale pre-NeoForge documentation

- `common/.../SomeStacksCommon.java` — "registry names, logging, and metadata remain
  identical on Forge and Fabric."
- `common/.../CommonRegistry.java` — "The loader's own registration glue (Forge's
  `ModRegistry`, or its Fabric equivalent) ..."

Both predate the NeoForge module and the class-doc on `ModRegistry` (NeoForge) even points
back at the two-loader phrasing. Cosmetic, but it is literal residue of the mono-/dual-loader
state.

### 2.4 Automation fake-player identity differs by loader for no reason

- `ForgeEditAuthority.PROFILE` — `UUID.nameUUIDFromBytes("somestacks:automation" ...)`,
  name `"[SomeStacks]"`.
- `FabricEditAuthority.PROFILE` — `UUID.nameUUIDFromBytes("somestacks:fabric_automation" ...)`,
  name `"[SomeStacks]"`.
- `NeoForgeEditAuthority` — `FakePlayerFactory.getMinecraft(level)` (loader-provided).

Forge and Fabric build the same synthetic `ServerPlayer` in a `WeakHashMap` the same way, but
seed the UUID from different strings. Nothing keys on that UUID, so it is harmless, but it is
a copy-and-tweak artifact rather than a decision — the two hand-rolled actors should share a
seed (and ideally the `WeakHashMap`/`computeIfAbsent` body, see 3.3).

### 2.5 NeoForge's single `clientReceiver` + `instanceof` dispatch

`neoforge/.../network/ModNetworking.java` funnels all three client-bound payloads through one
`Consumer<CustomPacketPayload>` that `ClientSetup.deliverRenderPacket` demultiplexes with an
`instanceof` chain back to the typed `ClientRenderPacketSink.apply` overloads. NeoForge's
`registrar.playToClient` takes a per-type handler, so this indirection is avoidable — each
payload can register straight to its `ClientRenderPacketSink.apply` overload the way Fabric
does. Minor, but it is extra scaffolding that only exists because the client-only delivery
was bolted on rather than registered per-type.

---

## 3. Forge/NeoForge near-duplicate classes

Forge and NeoForge kept the same class *names* in different packages
(`net.minecraftforge.*` vs `net.neoforged.*`), so a shared implementation is often not
possible without another shim. The project's stated preference ("some duplication is better
than one more small class", and the standing note that the Forge module should keep the
classic API) argues for tolerating most of this. Listing it so the volume is visible and the
one or two worthwhile consolidations are on record.

### 3.1 `client/measure/ModelMeasurer.java` — ~200 lines, one meaningful difference

The Forge and NeoForge copies are identical line-for-line except:
`import net.minecraftforge.client.extensions.common.IClientItemExtensions;` vs
`import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;`
(plus a few trimmed Javadoc words). The only loader-specific call is
`IClientItemExtensions.of(stack).getCustomRenderer()` in `probeCustomRenderer`. Everything
else — `getModel`, `applyTransform`, the `-0.5` shift, quad walking, the
`BoundsCollector`/`CapturingBufferSource`/`CapturingConsumer` inner classes — is vanilla API.
This is the strongest case in the codebase for pulling the body into `common` behind a
one-method seam (`ModelMeasurement.Backend` already exists; it just needs a
`customRenderer(stack)` accessor). ~180 duplicated lines collapse to a stub each.

### 3.2 `server/Protection.java` + `server/RightClickBlockSuppressor.java`

`Protection` is structurally identical across Forge/NeoForge; the real differences are
`Event.Result.DENY` vs `TriState.FALSE`, `MinecraftForge.EVENT_BUS` vs `NeoForge.EVENT_BUS`,
and the `PlayerInteractEvent` package. The `claim(...)` ordering logic, `claimPlacement`, and
`lookHit` (a `ViewRays`/`getInteractionShape().clip(...)` block with a center fallback) are
verbatim. `RightClickBlockSuppressor` is likewise identical but for the two enum constants
and `@Mod.EventBusSubscriber` vs `@EventBusSubscriber`. `lookHit` is also duplicated a third
time inside `FabricPlayerEditAuthority`. A shared `lookHit` helper in `common` (it is pure
vanilla API) is a safe, small win; the event-firing halves reasonably stay per-loader.

Note also the Forge `Protection` carries a long, careful class Javadoc explaining the
displaced-vanilla-click mechanism; the NeoForge copy was trimmed to a pointer. If the
mechanism matters (it does), the explanation should not live in only one of the two.

### 3.3 `block/PileItemHandler` · `block/BarColumnHandler` · `block/SinglesColumnHandler`

Three `IItemHandler` implementations, each duplicated Forge/NeoForge. Differences per pair:
the `IItemHandler` import package, and `ItemHandlerHelper.copyStackWithSize(stack, n)` (Forge)
vs `stack.copyWithCount(n)` (NeoForge — the vanilla 1.21 method, which Forge could also call).
Because both loaders ship their *own* `IItemHandler` interface, a single shared class is not
possible without an adapter layer; this duplication is close to unavoidable given the
"keep the classic Forge API" constraint. Worth noting only that Forge's use of
`ItemHandlerHelper.copyStackWithSize` where `ItemStack.copyWithCount` would do is the kind of
gratuitous difference that makes the pair look more divergent than it is.

### 3.4 Smaller identical-but-for-package pairs

- `client/KeyMappings.java` — identical except the `KeyConflictContext` import package.
- `client/ForgeRenderPlatform` / `NeoForgeRenderPlatform` — identical except `ModList` import.
- `client/ClientEvents.java` — the six `sendX` methods differ only as
  `ModNetworking.CHANNEL.send(pkt, PacketDistributor.SERVER.noArg())` vs
  `PacketDistributor.sendToServer(pkt)`; the interaction-event handlers are identical apart
  from the `RightClickEmpty` behavior note (3.5) and `Event.Result.DENY` vs `TriState.FALSE`.
- `command/ForgeCommandNetwork` / `NeoForgeCommandNetwork` — identical bar the
  `PacketDistributor` call style and the entry-point class name.
- `server/FtbChunksProtection.java` — the Fabric and NeoForge copies are identical except
  `FabricLoader.getInstance().isModLoaded("ftbchunks")` vs `ModList.get().isLoaded("ftbchunks")`.
  Forge has no copy. Note the asymmetry worth a second look: NeoForge consults FTB Chunks
  *both* through `EventHooks.onBlockPlace` and directly, while Forge relies on the place
  event alone. If FTB Chunks' NeoForge build really fails to fire the event this is correct;
  if it was just never revisited for Forge, Forge automation is under-protected against FTB
  claims. (Forge has no `ftbchunks` compile dependency today, so closing it would mean adding
  the same optional integration Forge currently lacks.)

---

## 4. Structural inconsistency between loaders

Not residue exactly, but the three modules solve the same wiring differently:

- `ClientGestures.Sender` is implemented by `ClientEvents` on Forge and NeoForge (one class
  is both the interaction-event listener and the packet sender) but by a dedicated
  `FabricClientNetworking` on Fabric, with `FabricClientEvents` separate.
- Client init is two classes on Forge/NeoForge (`ClientSetup` + `ClientEvents`) and four on
  Fabric (`FabricClientRendering`, `FabricKeyMappings`, `FabricClientNetworking`,
  `FabricClientEvents`), driven from `SomeStacksFabricClient`.

Each is internally coherent and partly forced by how each loader registers keybindings and
client networking (mod-bus event vs direct call), so this is acceptable — noted only so the
inconsistency is a known quantity rather than a surprise.

---

## 5. Suggested priority

1. **2.1** — migrate Forge's client-bound render handlers onto `ClientRenderPacketSink` and
   make the dead `setHandler` line live. Removes a real duplication of behavior and a
   misleading no-op.
2. **2.2** — hoist `buildConfigSync()` into `common`.
3. **3.1** — pull `ModelMeasurer`'s body into `common` behind a `customRenderer(stack)` seam.
4. **2.3 / 2.4** — fix the stale "Forge and Fabric" class docs; unify the automation
   fake-player seed.
5. **3.2** — share `lookHit` (also removes the third copy in `FabricPlayerEditAuthority`);
   restore the trimmed `Protection` Javadoc on the NeoForge side or move it somewhere shared.
6. **2.5 / 3.3 / 3.4** — lower value; touch only if the surrounding code is being worked on
   anyway. The `IItemHandler` triplication (3.3) is effectively structural given the
   keep-classic-Forge-API constraint and is not worth an adapter layer on its own.
