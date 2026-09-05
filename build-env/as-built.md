# Some Buckets As-Built Orientation

Orientation to the repository's build structure, subsystem ownership, persistent data, cross-loader boundaries, and maintenance invariants. `player-view.md` covers observable behavior. Not a spec, not a prose restatement of the code; the code wins when they disagree. No history.

Length budget: 150 lines / 12k characters. If an edit pushes past that, cut something — don't append.

Per-sentence test: every sentence either (a) names the file/class to open to change a behavior, or (b) names an invariant not visible from any single file — a cross-module contract, an ordering requirement, a "keep these in sync". Sentences that only restate what the code does get deleted, as do enumerations of a method's branches or steps. Update in place.

This is an orientation to the current code, not a history, conversation, or prose rendering of the implementation. It describes what exists, not necessarily what should exist, and is not a design specification.

## Project shape

Identity: mod id `somegoogly`, package `com.github.crittscott.somegoogly`, version `0.8.1`, Java 21, Minecraft 1.21.1. Loader and API versions are pinned in the Gradle scripts and must stay aligned; Architectury is a compile-time annotation and transformation dependency, not a runtime dependency on any loader.

The Gradle project has four modules; `common` is transformed into all three loader artifacts.

| Source tree | Responsibility |
| --- | --- |
| `common/src/main/java` | Shared gameplay, state, codecs, services, networking, rendering, picker |
| `common/src/main/resources` | Assets, recipes, eye definitions, language, structure fixture |
| `common/src/gametest/java` | Shared GameTest assertions |
| `fabric/src/main` | Fabric entry points, callbacks, configuration, adapters, Mixins, metadata |
| `fabric/src/gametest` | Fabric wrappers and discovery metadata |
| `forge/src/main` | Forge bootstrap, events, native config, adapters, client integration, GeckoLib bridge, metadata, Access Transformer |
| `forge/src/gametest` | Forge wrappers, persistence proof, dev-mod entry point, discovery metadata |
| `neoforge/src/main` | NeoForge bootstrap, events, native config, adapters, client integration, GeckoLib bridge, metadata, Access Transformer |
| `neoforge/src/gametest` | NeoForge wrappers, persistence proof, dev-mod entry point, discovery metadata |

Common main imports no loader or GeckoLib type; differences pass through project-owned adapters or six Architectury `@ExpectPlatform` methods. Loader packages stay disjoint from common packages so Forge sees no split package.

Subsystem ownership: the server owns eligibility, persistent eye state, item actions, behaviors, datapack definitions, picker authorization, and world mutation; the client owns rendering, model attachment, pupil motion, inspection, and picker UI and editing state.

Registered content is declared once in `ContentRegistrar` — two items, one `DataComponentType`, one creative tab, two recipe serializers, and nothing else (no blocks, entities, menus, particles, or sounds). Optometrist is a data-driven enchantment from the common data pack. Fabric binds these handles through native registries; NeoForge and Forge bind the same handles through native deferred registers.

## Configuration and eye definitions

`ServerConfig` and `ClientConfig` hold the schema and expose validated values as `ConfigValue<T>`. Fabric reads its client TOML directly and loads the world's server TOML through `ServerConfigFile`. NeoForge and Forge carry native CLIENT and SERVER specs whose values must be copied into `ConfigValue<T>` on load and reload, and SERVER unload must restore defaults, or the storage and runtime representations diverge.

Server-config keys, defaults, ranges, list validators, and section names must stay aligned across `ServerConfigFile`, `ForgeServerConfig`, and `NeoForgeServerConfig`.

Eye definitions are server datapack resources at `data/<namespace>/eyes/*.json`, modeled by `EyeConfigModel`. Reload resolves and validates exactly one version per entity type, canonically encodes the resolved set, then atomically swaps `ServerEyeConfigs`; failure at any stage keeps the previous set. The resolved set is pushed to clients, so `ClientEyeConfigs` never selects a version itself. Size and geometry limits are enforced at three points that must stay aligned: datapack reload, picker export, and network decode.

Change eligibility knobs (spawn and harvest chances, entity overrides, behavior toggles, the spawn-all gate) in `ServerConfig`; change local eye visibility in `ClientConfig`.

Player-visible strings are translatable `Component`s keyed in `assets/somegoogly/lang/en_us.json`, with identifiers, counts, and paths passed as translation arguments. Logs, command literals, config comments, and schema keys are plain strings and are not translated.

## Entity and item state

`EyeState` is the entity-eye-state boundary; its `Snapshot` is the whole unit for server transitions, tracking sync, and client packet application, and partial updates are not a supported path. NBT access goes through `EntityPersistentData`: native persistent compounds on NeoForge and Forge, `EntityPersistentDataMixin` on Fabric.

Persistent entity keys:

- `somegoogly:hasGooglyEyes` — whether the entity has eyes;
- `somegoogly:eyeVariantRoll` — stable placement-variant roll;
- `somegoogly:eyeOverrides` — optional shared appearance overrides.

Related mutations flush as one full-snapshot sync. The eye-state key and variant roll are initialized together only when absent, preserving the natural-eyes decision across persistence and transfer. `EyeState.initialize` and `EyeStateSync.sendTo` skip an absent snapshot; mid-life mutations always send.

Eye item stacks carry `AppearanceOverride` in the registered `somegoogly:eye_properties` component; harvesting copies the first configured eye's effective appearance, and crafting and Slimy Eye application preserve that component while leaving other stack components untouched. Item stacks never carry placement geometry.

`EyeItemService` owns authorization, mutation, drops, and durability for Slimy Eye use and both harvest paths. Loader adapters run entity interaction after protection listeners (Forge/NeoForge `LOWEST`, Fabric a late callback phase). Applying a Slimy Eye to another player also requires server PvP and `canHarmPlayer`.

Every successful Slimy Eye application emits `GameEvent.ENTITY_INTERACT`; every successful interactive, self, or kill harvest emits `GameEvent.SHEAR`. Refused operations emit neither.

## Eligibility and behaviors

`ServerServices.onLivingEntityLoaded` owns natural eye initialization; once an entity is initialized, later configuration changes never revisit it.

`EyeBehaviors` is the ambient-behavior catalog. A `BehaviorInstance` (id, duration, seed, elapsed) is the only per-behavior state sent, and clients derive the animation from it, so no per-tick animation packets exist. `ServerBehaviorScheduler` schedules ambient behaviors for watched eyed entities and takes damage, heal, and trade triggers from loader adapters. `ClientEyeRuntime` holds transient pupil and behavior state for rendered entities and is never persisted.

## Rendering and attachment

`ClientRenderLayers` installs the normal and picker layers on compatible living renderers, guarding against duplicates and reinstalling when renderer state is replaced. `LayerGooglyEyes` must be ordered before the slime outer layer. `ClientEyeConfigs` caches the resolved eye view per age and placement variant; `ServerEyeConfigs` is the uncached server-side path. `EyeRenderTransforms` owns render rotations; `EyePlacement` owns pupil-plane projection.

Attachment resolvers (definition token to model part or bone) cache by model identity and clear on renderer or runtime reset.

Fabric uses the common 1.21.1 Access Widener; Forge and NeoForge copy `gradle/accesstransformer.cfg` into their resources. These two 36-rule access representations must stay aligned.

GeckoLib is optional: common code goes through the `GeckoCompat` bridge, which probes for GeckoLib before touching typed code, and a failed layer attach must not block mod load. The typed GeckoLib layer and bone code is one shared source tree at `gecko/src/main/java`, `srcDir`-ed into every loader's main sourceSet; only `GeckoCompatImpl` stays per-loader.

`GooglyEyeItemRenderer` draws the 3D Googly Eye; `EyeItemProperties.SLIMY_EYE_IRIS_TINT_INDEX` must match the Slimy Eye model's `layer2`.

## Networking

Five packet classes implement Minecraft's typed `CustomPacketPayload` contract directly: eye definitions, entity eye state, behavior triggers, picker freeze, and picker export. Their ids embed network version `11`; any incompatible wire change requires bumping it. Forge and NeoForge register a required native channel/version, while Fabric checks at play join that each endpoint declared the expected versioned payload and disconnects an absent or incompatible peer.

`NetworkTransport` contains only sends and client receive handoff; `NetworkTracking` abstracts loader-specific tracking-player fanout. Serverbound handlers receive the authenticated `ServerPlayer` and re-check authorization. Eye-state packets include entity id and UUID; packets that precede entity creation wait in a bounded UUID-keyed map cleared on disconnect, preventing numeric-id reuse from applying stale state.

## Picker and commands

Client picker code owns drafts and previews; the server owns mob freezing, spawning, movement, and world export. Spawn and mob-pose operations are server Brigadier commands; only freeze selection and client-authored export cross custom payloads. `ModelPartVocabulary` supplies one attachment grammar to live editing and bulk export.

`PickerFreezeService` saves and restores each mob's prior `NoAI` value and reconciles freeze markers on mob load, player logout, and server stop; only the owning editor may hold a lock. Picker requests are rate-limited; spawn-all additionally requires creative mode, explicit server enablement, and a server-wide cooldown. World export is confined to the generated datapack directory and triggers a reload, so it alone among the picker verbs requires permission level 2 on top of creative; client export-all writes only under the game-directory export tree.

The client and server own disjoint branches of one `/sg` Brigadier tree: local editing stays client-side, while admin, spawn, spawn-all, and mob-pose commands are server-side. Fabric explicitly forwards those server branches because its matching client root otherwise captures them.

## Loader integration

Fabric Mixins cover persistent data, reactions, trades, shears-kill drops, and renderer reload where callbacks are absent; Mixins and the Access Widener target 1.21.1 exactly. Fabric configuration has no file watching.

NeoForge: common registration runs once from the `@Mod` constructor, and client services must be attached to the correct bus (mod versus game).

Forge's required `PayloadChannel` uses network version 11 and marks payloads handled.

NeoForge and Forge both isolate physical-client bootstrap from dedicated-server bootstrap.

## Automated verification

Shared assertions live in `common/src/gametest/java`; each loader wraps them and adds one persistence test, so its annotated count stays at shared + 1. Required-client rejection, `/sg` server commands, and plain-shears self-damage require manual client testing.

## Operational boundaries

- Optional renderer integrations log recoverable linkage, reflection, construction, and invocation failures once per affected operation and omit eyes when reliable attachment geometry cannot be produced; third-party model changes can still silently invalidate bundled tokens or placement geometry without throwing.
- Wire compatibility is the protocol-version number, not the display version, and pre-release data and protocol formats have no compatibility layer.
- `build-env/` is not a build input; it copies the root and module build scripts verbatim, and every edit to a build script must be mirrored there.
