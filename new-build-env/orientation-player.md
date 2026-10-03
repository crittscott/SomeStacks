<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Buckets — player-facing behavior

What a player currently observes. `orientation-code.md` covers the code structure.

Six items in one creative tab. Each stacks to 16 while empty and to 1 once it holds content. Using a stack of empties fills one, which goes to the inventory or drops at the player's feet; creative mode keeps the empties, as vanilla does. Contents stay with the item when it is moved, dropped, or carried through death. Rarity colors: Junk common (white); Big and Huge uncommon (yellow); Source, Trash, and Mob rare (aqua).

## Crafting

| Item | Recipe |
| --- | --- |
| **Big Bucket** | 8 vanilla buckets in a ring |
| **Huge Bucket** | 8 empty Big Buckets in a ring |
| **Junk Bucket** | Chest with iron on the left, right, and below |
| **Trash Bucket** | Junk Bucket + Enderman Spawn Egg + Ender Eye, shapeless |
| **Source Bucket** | Trash Bucket + Netherite Block, shapeless |
| **Mob Bucket** | Empty Source Bucket + any spawn egg, shapeless |

Recipes that consume a Some Buckets item require it to be empty. Spawn eggs are ingredients only; they do not configure the result.

## Structure loot

Each roll is independent, so a container can receive several buckets.

| Item | Chance | Locations |
| --- | --- | --- |
| **Junk Bucket** | 2% | All village chests |
| **Big Bucket** | 5% | Every vanilla structure container except village chests |
| **Source Bucket** | 10% | All bastion chests |
| **Source Bucket** | 5% | Buried treasure, all shipwreck chests, and both underwater-ruin chests |
| **Trash Bucket** | 5% | End City treasure and all stronghold chests |
| **Mob Bucket** | 5% | End City treasure and all stronghold chests |
| **Huge Powder Snow Bucket** | 5% | Igloo chests and Ancient City ice boxes |

Big Bucket locations include the jungle-temple dispenser. Bonus chests, archaeology, fishing, entity drops, and non-structure loot are excluded. Loot buckets are empty except the Huge Bucket, which holds 64 powder snow.

## Big and Huge Buckets

Big holds 8 units, Huge 64. A unit is 1,000 mB of fluid, one powder-snow block, or one milking. A bucket holds one content type at a time.

They collect and place fluid source blocks (including water from waterlogged blocks), powder snow, water, lava, and powder snow in cauldrons, and fluid in blocks exposing a loader fluid tank (Forge/NeoForge fluid capability or Fabric Transfer API storage). They also milk adult cows. Every world, cauldron, and tank operation moves one unit. Flowing fluids cannot be collected. Fluids with no placeable block can move between tanks but not into the world. Modded fluids use the loader's placement, vaporization, block-state, and empty-sound behavior where provided. On Forge and NeoForge, when another mod (such as Create) enables the loader's milk fluid, tanks, pipes, and machines exchange milk with Big, Huge, and Source Buckets as that fluid; it stays drinkable milk in the bucket, and Big and Huge exchange it only in whole buckets.

An empty bucket collects, a full one places, and a partial one collects compatible content if it can and otherwise places one unit. Placement follows vanilla waterlogging, replaceable-block, and ultra-warm evaporation rules. For powder snow, sneaking while targeting a powder-snow block places instead of collecting, so a partial bucket can build outward.

Using a milk-filled bucket on air drinks one unit and clears potion effects. Sneak-use on air empties the whole bucket without confirmation.

A lava-filled bucket burns 20,000 ticks with vanilla fuel values and returns with one unit removed. Fabric and NeoForge follow the current vanilla lava-bucket fuel value; Forge uses 20,000 ticks because its item fuel hook has no current fuel-values context. The name, tooltip, and a colored durability-style bar show contents and fill level.

In a dispenser, the bucket stays in the dispenser and acts on the block in front, one unit per pulse. An empty bucket can collect powder snow, but a powder-snow-filled one places instead of collecting more and does not fill an empty cauldron.

## Source Bucket

An infinite source and sink for one server-allowed fluid or milk. The default allowlist is water, lava, and milk; the server can remove these or add registered modded fluids.

An empty Source Bucket is assigned by collecting an allowed source block, draining a compatible tank or cauldron, receiving a held transfer, or milking a cow. Once assigned, it places, supplies, or accepts that content indefinitely: milk can be drunk repeatedly, and lava is permanent furnace fuel. Taking or placing never changes the assignment.

Right-click on a non-air target places the assigned fluid. Sneak-right-click removes one collectible source unit if the target holds the assigned fluid; other fluids, or fluid not collectible as one unit, are not taken. The same applies to cauldrons and fluid tanks. Machines transfer up to one bucket unit per operation through the loader's fluid API; held-item transfers can fill the receiver to capacity in one use.

Sneak-use on air resets an assigned bucket to empty when no held transfer occurs; normal use on air leaves it unchanged. If the server removes its content from the allowlist, the bucket keeps its identity but is inert until reset.

In a dispenser, an assigned bucket removes matching collectible fluid in front; otherwise it places, even into a different world fluid, so normal reactions occur (lava into water makes obsidian). This also applies to cauldrons and tanks. An empty one can milk an adult cow in front.

## Junk Bucket

A portable FIFO container for nine item stacks.

- Use on air: collect eligible dropped items within about 1.5 blocks.
- Sneak-use on a block: eject the oldest stack beside it. Sneak-use on air: throw it from the player.
- In an inventory, right-click between bucket, cursor, and slots to insert or remove stacks. A stack of empty buckets must first be split to one.
- Use on an animal: offer it suitable stored food; the animal's own rules decide, as with the food in hand (breeding, growth, healing).

Compatible stacks merge first. Fresh drops wait out their pickup delay. Player intake follows vanilla pickup rules: it leaves items dropped for another player, honors Forge/NeoForge pickup-event vetoes, and counts toward picked-up statistics. Dispensers, like hoppers, ignore item targets. The tooltip and bar show occupied entries; collecting and ejecting play sounds. A saved entry that no longer loads (for example, an item from a removed mod) is set aside: the tooltip reports it, it keeps the bucket from counting as empty, and it returns to the bucket on a later load once it loads again and there is room.

Stored items render protruding from the opening, oldest in front, with the layout randomized on each insert and normal models, tint, and glint preserved.

Junk Buckets cannot store Junk or Trash Buckets, bundles, shulker boxes, other items that opt out of container storage, or modded item-inventory containers such as backpacks. Big, Huge, Source, and Mob Buckets store with contents intact.

In a dispenser: feed an animal in front that accepts stored food (never an untamed tamable), else collect eligible items, else eject the oldest stack. An animal or item that cannot currently be processed blocks ejection.

## Trash Bucket

A one-stack Junk Bucket. Incoming items merge if they fit; otherwise the stored stack is destroyed and replaced by the incoming item, up to its stack limit, and the excess stays where it was. World use and dispensers process one item entity at a time. Gestures, ejection, feeding, storage restrictions, and dispenser priorities match the Junk Bucket. The tooltip reads `Stacks: n / 1`, and the art shows a black void. Collecting plays a water-evaporating sound; ejecting plays it reversed.

## Mob Bucket

Holds up to eight mobs of one exact entity type. Using it on an eligible mob captures it with state intact (health, name, age, inventory, UUID); afterward only that type is accepted until empty. Sneak-use on a block releases the oldest mob into the adjacent space.

Not capturable: players, non-mob entities, passengers, vehicles carrying passengers, leashed mobs, mobs owned by another player, and types in the `somebuckets:mb_blacklist` tag (shipped: Ender Dragon, Wither).

Aquatic mobs need water at release: the bucket waterlogs a suitable block or places a water source. Release fails if the mob does not fit or water cannot be provided; in ultra-warm dimensions the water evaporates but the mob is still released. A mob stays stored until it actually enters the world, and gets a new UUID if its saved one is in use. Capturing an aquatic mob removes the water source it occupies, with the normal pickup sound and game event, and fails if the block refuses, so release-and-recapture creates no water.

The tooltip shows type and count, and the bucket is tinted with the spawn-egg colors. A bundled override table, extendable by resource packs, covers entities whose eggs report no usable colors (currently The Bumblezone's bee queen and variant bee and Wilder Nature's animals).

In a dispenser: capture an eligible mob in front; any mob remaining there blocks release; otherwise release the oldest mob.

## Held-container transfers

Using a Big, Huge, or Source Bucket on air with a fluid container in the other hand transfers between them; a targeted block takes precedence. The other container may be a vanilla bucket or any item exposing the loader's fluid storage API. Milk moves between Some Buckets containers and vanilla milk buckets, and, where the loader milk fluid is enabled, to and from any other fluid container.

Big and Huge transfer as much as the receiver accepts. A Source Bucket fills containers without loss (Big and Huge to capacity), accepts compatible fluid without change, and can be assigned by a transfer. Source transfers obey the allowlist; Big and Huge transfers do not. With a multi-item held stack, as many as possible are processed; one result stack stays in hand, the rest go to the inventory, and only overflow drops at the player's feet.

## Land claims

Protection follows vanilla. Player block edits (fluid, powder snow, cauldrons, tanks, mob release, ejection against a block) are subject to spawn protection, the world border, and adventure-mode rules. Entity interactions (milking, feeding, capture, item vacuuming) are subject only to the world border. Dispensers act through a stable automation-player identity named `[SomeBuckets]` and, like vanilla dispensers, answer only to the world border.

Claim mods are consulted through the loader events they already watch:

- Cauldron and tank use: block-interaction event (`RightClickBlock` on Forge/NeoForge, `UseBlockCallback` on Fabric).
- Player world fluid or powder-snow pickup, including aquatic-capture water: block-break event (`BlockEvent.BreakEvent` on Forge/NeoForge, `PlayerBlockBreakEvents.BEFORE` on Fabric).
- Player world fluid and powder-snow placement, including aquatic-release water: block-place event on Forge/NeoForge, reporting the fluid or powder snow placed. A refusal undoes the placement, leaves the bucket and any Mob Bucket mob unchanged, and drops nothing from a replaced plant.
- Forge also posts `FillBucketEvent` for player world fluid use.
- Player capture, milking, and feeding: entity-interaction event.
- Player Junk/Trash intake: Forge/NeoForge item-pickup event.
- Fabric: Patbox's Common Protection API, when present, also checks player fluid pickup and fluid and powder-snow placement.

Any denial prevents the operation. Dispensers post none of these, as in vanilla; claim mods that guard dispensers firing across a claim border, such as Open Parties and Claims, still apply. Owned mobs are captured only by their owner and never by dispensers. Dispensers never feed an untamed tamable, so they cannot tame one.

## Configuration and data packs

Forge and NeoForge keep the Source Bucket allowlist in the world save's `serverconfig/somebuckets-server.toml`, with the documented per-world override behavior, and sync it to clients. Fabric has no per-world config, so it uses a global, server-owned `config/somebuckets-server.json` and syncs the resolved policy on join and reload. The default:

```toml
allowedContents = ["minecraft:water", "minecraft:lava", "somebuckets:milk"]
```

Fabric's JSON has the same `allowedContents` array. Registered fluid ids may be added; `somebuckets:milk` stands for milk, which is not a loader fluid. An empty list disables all Source contents. Unknown ids are ignored and logged. `/reload` applies changes without a restart on every loader.

Data packs can replace or remove all six recipes, tune or disable each loot roll through its `somebuckets:inject/<reward>` loot table, change which tables Forge's and NeoForge's loot modifiers target, and extend `somebuckets:mb_blacklist`. Rolls are still added when a data pack replaces the target table. Custom recipe ingredients: `somebuckets:empty_bucket` and `somebuckets:spawn_egg`.

Resource packs can replace item definitions, models, textures, and Mob Bucket egg-color overrides. Every loader clips the fluid's animated still texture to the bucket's content mask and applies its runtime color, preserving NBT-dependent variant colors. No advancements or JEI integration.

## Diagnostics

Two operator commands run on the client that types them and write plain-text reports to that client's `config/somebuckets/`, overwriting the previous run; on a dedicated server an operator runs them from a connected client. Findings go to command feedback and the report, never the log. Each report starts with a `PROBLEMS` section, then lists every entry.

- `/sb eggs` records every entity type's two Mob Bucket tint colors, flagging capturable types with no spawn egg, eggs whose item definition supplies no colors, and eggs whose colors are identical or hueless. Override-table types report the override and are not flagged; blacklisted types are annotated.
- `/sb fluids` walks every source fluid through the Big and Source bar-color path: still texture, averaged base color, loader tint, final bar color, world collectability, and vanilla bucket item. It flags fluids that fall back to the default color (no still texture or no readable sprite image) and tints that collapse to near-black. Flowing and aliased fluids are skipped and counted.

## Current limitations (observed, not planned work)

- Empty Junk and Mob Buckets share the plain bucket texture.
- A Big Bucket of powder snow uses the vanilla-sized powder-snow bucket texture.
- The Mods screen description is the placeholder `Get you some buckets!`.
- In creative mode, some modded tanks may intercept a normal use and drain without filling a Big Bucket; survival use and creative sneak-use work in the observed case.
