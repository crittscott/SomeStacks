# Targeted review: Legacy support, backward compatibility, function layering

Scope: the mod's own source in `common/`, `forge/`, `fabric/`, `neoforge/` (main source sets).
Not inspected: git history, `other/` (vendored FTB Chunks API), build environment, other review files.

Question asked: does the codebase carry legacy support, maintain backward compatibility with
older versions, or layer functions on functions (`myFunc1` just calls `myFunc2`)?

Short answer: there is almost no data-format legacy, which is good. There is one explicit
backward-compatibility claim that should not exist, one dead compatibility-era fallback, and one
feature (client render-packet handling) that is over-layered *and* simultaneously duplicated
inline on Forge so that the layers it added are unused there.

---

## Backward compatibility

### 1. `StackItemStorage` commits, in writing, to loading pre-existing world saves

`common/.../util/StackItemStorage.java` class javadoc:

> Loader-neutral replacement for Forge's `ItemStackHandler`, matching its insert/extract semantics
> and its NBT shape (`{Size, Items:[{Slot, ...stack}]}`) **exactly, so existing world saves keep
> loading.**

For an unreleased mod with a stated no-legacy / no-backward-compatibility policy, "so existing
world saves keep loading" is a legacy justification for a design choice. Matching `ItemStackHandler`'s
*semantics* so the loader capability adapters stay thin is a legitimate goal; preserving its
on-disk shape for the benefit of saves that (per project policy) do not exist is not.

Concrete residue of that decision:

- `serializeNBT` writes `TAG_SIZE` (`"Size"`), but `deserializeNBT` never reads it — it iterates
  the `Items` list and range-checks each `Slot` against `stacks.length`. `Size` is written purely
  to mirror `ItemStackHandler`'s format. It is a vestigial field.
- `SlotAccess.java` javadoc carries the same framing ("matching Forge's `IItemHandler` contract
  exactly so the loader-specific capability views built over it stay thin adapters"). The
  thin-adapter rationale is sound; only `StackItemStorage`'s "existing world saves keep loading"
  clause is the legacy one.

Recommendation: drop the "existing world saves keep loading" clause from the javadoc, and either
stop writing `Size` or add a one-line note that it is written only for external tools that expect
the vanilla-style shape.

### 2. `PlayerEdits.VanillaPlayerEditAuthority` is a dead compatibility-era default

`common/.../server/PlayerEdits.java` initializes its authority field to
`new VanillaPlayerEditAuthority()`. All three loaders install their own implementation during
setup before any gesture packet can be handled:

- `SomeStacksFabric`  -> `PlayerEdits.setAuthority(new FabricPlayerEditAuthority())`
- `SomeStacks` (Forge) -> `PlayerEdits.setAuthority(new ForgePlayerEditAuthority())`
- `SomeStacksNeoForge` -> `PlayerEdits.setAuthority(new NeoForgePlayerEditAuthority())`

So `VanillaPlayerEditAuthority` is the live authority only in the window between class-load and
entrypoint init — effectively never during gameplay. Its javadoc, "Fabric's initial parity level:
vanilla world, spawn, and build permissions only," documents a past state of the Fabric port that
no longer holds (Fabric now ships `FabricPlayerEditAuthority`). That is legacy commentary
referencing what the code used to be.

It is also inconsistent with the sibling facade `WorldEdits`, whose `authority` field has no
default and stays null until `setAuthority` runs.

Recommendation: remove the default (match `WorldEdits`), or if a pre-init safety net is wanted,
rename it to something not tied to the Fabric port's history and give it a present-tense comment.

### 3. `AutoRenderProfiles` cache versioning — defensible, noted for completeness

`common/.../client/measure/AutoRenderProfiles.java` has `CACHE_FORMAT_VERSION = 1`,
`isCurrentCacheFormat(...)`, and a documented list of invalidation triggers including
"A measurement-format change rejects the previous algorithm's entries." No code reads an older
format — a version mismatch simply discards the file and re-measures. This is cache-busting for a
client-side derived cache that persists across mod updates, not legacy support, and is reasonable.
The only smell is a version constant and a documented scenario for a value that has only ever
been `1`. Leave it; flagged only so it is not mistaken for a gap.

---

## Function layering / speculative indirection

### 4. Client render-packet handling: over-layered abstraction that Forge bypasses

The intended path for the three render-related S2C packets (`ConfigSyncPkt`, `RenderOverridePkt`,
`WriteOverridesPkt`) is:

```
ClientRenderPacketSink (static facade, retains state until handler installed)
  -> ClientRenderPacketSink.Handler  (interface, "loader-specific services")
    -> DefaultClientRenderPacketHandler  (the only implementation)
      -> ItemRenderOverrides / StackState  (static state)
```

Problems:

- **`Handler` has exactly one implementation** across all loaders, `DefaultClientRenderPacketHandler`,
  and every loader instantiates it identically. The impl is pure pass-through: `setServerOverrides`
  forwards one call, `setUserOverride` does `isReset` / `RenderMode.fromString` / `sanitize` then
  forwards, `writeOverrides` branches on `isEmpty()` then forwards. There is nothing loader-specific
  in it. The interface + `Default...` class buy no variation.

- **Forge never calls `ClientRenderPacketSink.apply(...)`.** `forge/.../network/ForgePacketHandlers`
  re-implements all three behaviors inline (`handleConfigSync`, `handleRenderOverride`,
  `handleWriteOverrides`) behind `DistExecutor`, duplicating the `StackState.setBlockEnabled`
  toggles, the `isReset` / `RenderMode.fromString` / `OverrideJsonCodec.sanitize` logic, and the
  empty-vs-namespace dump branch. `forge/.../client/ClientSetup` still calls
  `ClientRenderPacketSink.setHandler(new DefaultClientRenderPacketHandler())` — it registers a
  handler that is never dispatched to (the `setHandler` call's only effect on Forge is one
  `setServerOverrides(Map.of())` on an empty map).

- NeoForge (`neoforge/.../client/ClientSetup#deliverRenderPacket`) and Fabric
  (`FabricClientNetworking`) both route through `ClientRenderPacketSink.apply`. Only Forge —
  structurally a near-clone of the NeoForge module — diverges, and its inline copy also lacks the
  retained-`serverOverrides` handshake the Sink provides.

Net: one feature, four layers, one real implementation, plus a parallel hand-maintained copy of
the logic on one loader. Pick one:

- collapse `Handler` / `DefaultClientRenderPacketHandler` into `ClientRenderPacketSink` (it is the
  sole impl and the deferral state already lives in the Sink), and have Forge dispatch through
  `ClientRenderPacketSink.apply` like NeoForge and Fabric, deleting the `ForgePacketHandlers`
  render branches; or
- if the `Handler` seam is meant to stay, at minimum route Forge through it so the duplication and
  the unused `setHandler` call go away.

### 5. `InteractionRuleRegistry` — two identical rule lists, three parallel entry points

`common/.../client/interaction/InteractionRuleRegistry.java`:

- `EMPTY_HAND_RULES` and `ITEM_RULES` are both `List.of(new ModeCycleRule())` — the same single
  rule twice.
- `processEmptyHandRules`, `processItemRules`, `processBlockRules` are three public methods that
  each delegate to `processFirstMatch(<list>, ctx)`.

The ordered-list / first-match / `matches` + `execute` machinery is justified for `BLOCK_RULES`
(7 rules with real precedence). For the empty-hand and held-item cases it is one rule, and the
same rule, wrapped in list plumbing and a dedicated dispatch method each. The empty-hand and item
paths could share a single entry point.

### 6. Cosmetic one-line wrappers (individually not worth a change)

- `WorldEdits.authority()` — private method whose whole body is `return authority;`.
- `AutoRenderProfiles.modVersion(ns)` — private one-liner wrapping
  `ClientRenderPlatform.modVersion(ns)`.
- `StorageStackBE.deposit(ItemStack)` -> `deposit(ItemStack, null)` — this one is a normal
  default-argument overload and is fine; listed only so the pattern is accounted for.

---

## Cross-module duplication (inherent to the multiloader setup, flagged not condemned)

`forge/` and `neoforge/` carry near-verbatim copies of `BarColumnHandler`,
`SinglesColumnHandler`, `PileItemHandler`, and also `ClientEvents`, `ClientSetup`,
`RightClickBlockSuppressor`, `Protection` / `FtbChunksProtection`, `ModRegistry`, `KeyMappings`,
`ModelMeasurer`. The item-handler trio differs only by the `IItemHandler` import package and a
single call (`ItemHandlerHelper.copyStackWithSize` vs `stack.copyWithCount`).

This follows from a hand-rolled multiloader with no shared Forge-family source set, and the
project's "do it the Forge/Minecraft way" principle argues against pulling in Architectury to
dedupe it. Not a defect. It is, however, the single largest block of duplicated logic in the
tree, and the Forge/NeoForge halves are close enough that a shared source set both modules
compile would remove most of it. Worth a conscious decision rather than letting the two copies
drift (finding 4 is an example of drift that already happened).

---

## What is clean (stated so the review is not just a defect list)

- No `@Deprecated` anywhere in the mod's own code.
- No NBT or JSON schema-migration code, no version gates on stored data, no legacy config keys.
  `ServerConfig`, `ServerOverridesLoader`, and `OverrideJsonCodec` each read exactly one current
  format and log-and-skip anything malformed — the no-legacy stance the project asks for.
- Block-entity `loadAdditional` methods guard with `tag.contains(...)`, but that is for
  freshly-created block entities, not for reading an older layout.
- The loader-facade pattern (`WorldEdits`/`EditAuthority`, `PlayerEdits`/`PlayerEditAuthority`,
  `CommandNetwork`/`Handler`, `ClientRenderPlatform`/`Backend`, `ModelMeasurement`/`Backend`) is
  standard multiloader practice, and every one of those except the render-packet sink (finding 4)
  has genuine per-loader implementations behind it.
- `RenderMode.fromString`, `BlockType.fromOrdinal`, and the `Direction` ordinal decoding in the
  packets are wire-protocol parsing with range checks, not legacy shims.
- `forge` `ModNetworking` pins `PROTOCOL = 2` with an exact-version test on both sides — the
  opposite of legacy tolerance.

## Minor: parallel structure to keep an eye on

`SsHelp` (`Topic` enum: usage strings, permission `Gate`, detail keys) is a hand-maintained
description of the `SsCommand` brigadier tree. It is a second source of truth for every
subcommand's forms and permission level, kept in sync by hand. Not legacy; a maintenance cost
that grows with the command surface.
