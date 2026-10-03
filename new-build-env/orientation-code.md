<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Buckets — code orientation

Build structure, ownership, persisted state, loader seams, and invariants the code currently maintains. `orientation-player.md` covers observable behavior.

## Repository map

Some Buckets is a Java 21 mod for Minecraft 1.21.4 under `com.github.crittscott.somebuckets`, mod id `somebuckets`.

| Module | Ownership |
| --- | --- |
| `common` | Loader-neutral items, transaction sequencing, state, protection, client item models and renderers, shared resources and GameTest scenarios |
| `forge`, `neoforge` | Parallel loader peers for registration, capabilities, events, client type registration and fluid facts, config, loot, and test discovery |
| `fabric` | Fabric registration, Transfer API, callbacks, mixins, client type registration and fluid facts, config, loot injection, policy networking, and test discovery |

Architectury Loom transforms `common` into each loader jar; `common` is not a runtime mod, and Forge and NeoForge share no code directly. Loader-owned classes carry `Forge`, `NeoForge`, or `Fabric` prefixes. Common production Java has no loader runtime imports except the cross-remapped client `@Environment`. The common `somebuckets.accesswidener` opens `ItemEntity.target` so Junk intake can honor its reserved recipient; NeoForge converts it to an access transformer; Forge mirrors it in its own access transformer, checked during `check`. There are no blocks, block entities, menus, or saved-world objects; item components hold bucket state. The only custom gameplay payload is Fabric's Source Bucket policy snapshot.

## Subsystem ownership

| Area | Primary owner |
| --- | --- |
| Item identities, all capacities, creative variants | `BucketDefinitions`, `CreativeBucketCatalog` |
| Shared item base: stack size, decode-time restoration and validation | `SomeBucketItem` |
| Fluid container rules, naming, milking; Source policy | `FluidBucketItem` (`BBItem`, `SBItem`), `config/SBPolicy` |
| Furnace-fuel hooks | Fabric `FuelValuesMixin`; Forge `ForgeFuel` and item shells; NeoForge item shells |
| Big/Huge and Source world transactions, world pickup, water placement | `fluid/FluidTransactions` |
| Junk/Trash behavior | `JBItem` with its `intake` rule overridden by `TBItem`; layout state in `BucketState` |
| Mob behavior and tint identity | `MBItem`, `client/MobEggColors` |
| Serialization, validation, admission | `BucketState`, `ModDataComponentTypes` |
| Saved-format upgrades | `util/BucketStateMigration` |
| Loader server primitives | `platform/BucketOperations` plus each loader implementation |
| Held transfer, milk, and hand settlement | `interaction/HeldTransfers` |
| Dispensers and vanilla cauldrons | `interaction/Dispensers`, `interaction/Cauldrons`, registered by `SomeBuckets.registerBehaviors` |
| Authorization | `common/.../protection` |
| Client loader seam and fluid appearance | `client/ClientPlatform` |
| Item rendering | `items/*.json`; `client/FluidBucketModel`, `JunkContentsRenderer`, `MobEggColors.Tint`, registered by id from `ClientModelTypes` |
| Structure loot | `data/somebuckets/loot_table/inject/*.json`, `somebuckets/bucket_loot.json`, `BucketLootTables` |

## Cross-loader seams

Each loader installs `BucketOperations`, then calls `SomeBuckets.registerBehaviors` once items exist. Implementations provide the automation player, a sided `BlockFluidStore`, arbitrary-fluid placement, checked world placement (`placeChecked`), sounds, one held fluid move (`moveHeldFluid`), the milk fluid, fluid identity, inventory detection, item pickup and toss events, and Forge-event adaptation. `FluidTransactions` and `HeldTransfers` own sequencing, protection, and accounting. Block-store transfers call `FluidBucketItem`'s `acceptable`/`insert` and `extractable`/`extract` rules directly, acting on what a store actually moved: a take credits what fits, any accepted fill debits one unit; loader item storages only convert units around them, so finite versus infinite output is an item rule. Milk stays in milk mode; where a loader milk fluid is enabled, these rules and `FluidBucketItem.exposedFluid` exchange milk as that fluid.

`StoredFluid` is the common value; its variant data is a `DataComponentPatch` persisted with the item's registry context. `ForgeFluidStacks`, `NeoForgeFluidStacks`, and `FabricFluidVariants` convert only at loader boundaries; Forge's fluid tag travels as the patch's `custom_data`. World pickup, including aquatic Mob Bucket water, always goes through `FluidTransactions`. Vanilla-rule world placement (`FluidTransactions.emptyFluid`) serves Mob Bucket water and Fabric's arbitrary-fluid output, which debits through its transactional item storage; Forge and NeoForge place through their own fluid types.

`Dispensers` moves the loader's automation player to the dispenser as the context actor. Forge's `ForgeAutomationPlayer` is a cached plain `ServerPlayer` (Forge 54 has no fake-player type). `player()` is the real user for statistics, criteria, feedback, and protection events; null for automation. `Protections.mayModify` applies vanilla block-use gates to players and the world border to automation; `mayInteractWithEntity` checks only the border; `mayRemove` adds the loader's player break check (Fabric also consults Common Protection API when present). World placement runs under `placeChecked`: for a real player, Forge and NeoForge capture its block snapshots (suspending capture armed around `useOn`), post the place event for the placed block, and restore on refusal; Fabric consults Common Protection API first. `FluidTransactions.placeFluidChecked` clears a replaced block without drops, dropping it once the placement stands. Tanks and Source Bucket cauldrons run from `useOn`, behind block interaction. Each client installs `ClientPlatform` with the loader's fluid facts, config directory, and name; it derives the fluid model layer, the `/sb fluids` sample, and the Big/Huge bar color it installs into `BBItem`, which keeps the default on a dedicated server. `ClientPlatform.ColorReloadListener` preloads egg and source-fluid bar colors on resource reload. `FluidDiagnostics.commandTree` is the single Brigadier `/sb` tree adapted to each client command source.

A sided block store is authoritative even when it refuses. NeoForge and Fabric exclude vanilla cauldrons from block-fluid lookup, so common `Cauldrons` owns them on every loader: Big and Huge through interaction maps, Source Buckets and dispensers through `take`/`place`. A modded cauldron without a store is an ordinary block to every bucket.

## Persistent and network state

`BucketState` is the sole bucket-state reader/writer. `ModDataComponentTypes` defines persistent and stream codecs for `fluid_content`, `milk_amount`, `powder_units`, `captured_mobs`, and `junk_contents`; loader registration only registers those instances. Fluid, milk, powder, and mobs are mutually exclusive; junk is independent. Mutators preserve unrelated components, canonicalize empty state, and maintain the derived `MAX_STACK_SIZE` and milk `CONSUMABLE` components.

Structural codecs bound finite amounts, whole-bucket milk, powder units, and mob snapshots; the fluid network codec rejects the empty fluid. `BucketState` adds enclosing-item capacity, exclusivity, and nested-container rejection. Admission runs once, whenever a stack is decoded (`verifyComponentsAfterLoad`), without the loader item-inventory lookup: it first restores readable set-aside junk entries while the bucket has room, then removes malformed owned components rather than clamping them; setters enforce the same invariants on every write.

`CapturedMobs` holds the entity type and full FIFO entity snapshots, persisted and synchronized in full, as vanilla does for containers and entity buckets.

`BucketStateMigration` wraps the persistent codecs of `fluid_content`, `captured_mobs`, and `junk_contents`, upgrading raw data before the current-format codec decodes it, so conversion happens wherever a stack is decoded, including inside other mods' storage. Those components carry a `schema` field (absent means the 1.21.1 release); `captured_mobs` and `junk_contents` also carry the Minecraft `data_version` of their embedded entity and item-stack data (absent means 1.21.1), which is run through vanilla's data fixer, entity snapshots with a temporary `id`. Encoding writes only current stamps. A released Forge fluid variant, a raw tag, becomes `minecraft:custom_data` (`BucketOperations.releasedFluidVariantIsRawTag`). Milk and powder components are bare integers and unversioned. Entries that do not decode to storable stacks move to `set_aside`; they are retried on every decode, keep the bucket non-empty, and appear only in the tooltip.

Inventory insertion and FIFO extraction mutate identically on client and server; menu authority corrects mispredictions.

## Configuration and data

`SBPolicy` is the resolved immutable Source Bucket allowlist. Forge and NeoForge register `ModConfig.Type.SERVER`; the loader synchronizes it to clients and supports the documented per-world `serverconfig/somebuckets-server.toml` override behavior. Config load/reload events refresh `SBPolicy` on both logical sides.

Fabric's server-owned global `config/somebuckets-server.json` loads at server start and `/reload`. `FabricSBPolicyPayload` sends resolved ids and milk permission on join and after a reload that changed them; clients apply it on the client thread, reset on disconnect, and never read their local JSON as remote policy.

Recipes, tags, translations, sounds, models, and textures are shared. Each structure-loot roll is a data-pack `somebuckets:inject/<reward>` loot table; classpath `somebuckets/bucket_loot.json` maps each to its target tables. Fabric adds nested-table pools at runtime; Forge and NeoForge data providers generate checked-in add-table global modifiers under `src/generated/resources` (Forge's own `somebuckets:add_table`, NeoForge's `neoforge:add_table`) from that manifest. Client `MobEggColors` reloads `assets/somebuckets/mob_egg_colors.json`, merged across resource packs, and consults it before the spawn egg's item-definition constant tints; egg colors exist only in client resources.

## GameTests

Cross-loader scenarios and the shared structure fixture live under `common/src/gametest`; loader trees provide discovery wrappers and native-API cases, and each loader's `every_shared_scenario_has_a_loader_wrapper` fails on an unwrapped scenario.

## Conventions the code currently follows

- Keep ids, capacities, components, sounds, creative variants, and loot policy in shared authorities; give loader-constrained fuel hooks one authority per loader API.
- Route persisted state through `BucketState`; apply `SBPolicy` to every Source input and output.
- Preview, authorize the exact target (`mayRemove`; `mayModify` plus `placeChecked` for placement), then mutate; debit after a placement stands.
- Run player intake into an empty bucket through `HeldTransfers.fillFromHand`, and every held transfer, including off-hand priority, through `HeldTransfers`: Some Buckets container to other first.
- Transform one dispenser item per pulse and remove Mob snapshots only after world insertion succeeds.
- Milk and feed through the animal's own interaction; automation never feeds an untamed tamable.
- Emit one correctly positioned sound per success, none when the loader declares none; loader utility exclusions alone justify `notifyActor`.
- Award `ITEM_USED` by hand only where vanilla does not: item `use`, held transfers, and cauldron interaction-map handlers.
- Check live Mob eligibility at release; never capture a leashed mob or another's owned mob.
- Register `ClientModelTypes` before the first client resource load; render caches live in baked models or reload listeners.
- Build Forge spawn-egg ingredient matches lazily after other mods register items.
- Log milestones and anomalies via `SomeBuckets.LOGGER`, never per interaction; `/sb` findings go to feedback and reports.
