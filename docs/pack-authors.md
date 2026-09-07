# Pack authors

Some Stacks is extensible from data packs, resource packs, and the server config folder. Nothing here requires a Java addon.

## Quick map

| What | Where | Side |
| --- | --- | --- |
| Which items a Bar Stack accepts | `data/<ns>/tags/item/…` + the `ingots` config | server |
| Sounds per stack type and action | `data/<ns>/somestacks_sounds/*.json` | server (data pack) |
| Bar textures and tints | `assets/<ns>/textures/bars/*.json` | client (resource pack) |
| How items are drawn in cells | `assets/<ns>/item_render_overrides/*.json` | client (resource pack) |
| Server-imposed render overrides | `config/somestacks/server_item_overrides/*.json` | server, synced to clients |

## Bar-valid items (data pack)

A Bar Stack holds whatever the server's `ingots` list resolves to. The mod ships an item tag for packs to extend:

`data/<yourpack>/tags/item/ingots.json`, added to `somestacks:ingots`:

```json
{
  "replace": false,
  "values": [
    "yourmod:mithril_ingot",
    "yourmod:adamant_ingot"
  ]
}
```

Anything a Bar Stack accepts, a Singles Stack refuses — the two rules are complements. The item set is rebuilt on every tag reload, so a `/reload` picks up your changes.

## Sounds (data pack)

`data/<ns>/somestacks_sounds/<name>.json` maps each stack type's actions to sound events. This is server-side data: the server resolves it, plays it, and broadcasts it.

```json
{
  "storage_stack_block": {
    "deposit": "minecraft:block.wood.place",
    "extract": "minecraft:block.wood.break",
    "rotate": "minecraft:block.wood.hit"
  },
  "singles_stack_block": {
    "deposit": "minecraft:block.amethyst_block.place",
    "rotate_item": "minecraft:block.amethyst_block.chime"
  },
  "bar_stack_block": {
    "deposit": "minecraft:block.metal.place",
    "extract": "minecraft:block.metal.break"
  }
}
```

Each type carries only the actions its gestures reach:

| Type | Actions |
| --- | --- |
| `storage_stack_block` | `deposit`, `extract`, `rotate` |
| `singles_stack_block` | `deposit`, `extract`, `rotate`, `rotate_item` |
| `bar_stack_block` | `deposit`, `extract` |

**Layering.** Files layer one action at a time, so naming a single action leaves that type's other actions standing. The mod's own namespace holds the bundled defaults and is the base layer; every other namespace applies over it, and among those the file whose id sorts last wins.

**Entries name sounds that already exist.** The mod registers no sound events of its own, so an entry must name something already in the sound registry. Unknown types and actions are ignored with a warning, and anything missing or unresolvable falls back to the bundled wood sounds.

Deposit and extraction are heard every time. Rotation is limited to one sound per player every four ticks, with slight pitch variation, because the gesture is free and repeatable — the rotation itself is never withheld.

## Bar textures (resource pack)

`assets/<ns>/textures/bars/<name>.json` maps items to the texture and tint their bars are drawn with. A Bar Stack draws a fixed cuboid from this data rather than the item's own model.

```json
{
  "yourmod:mithril_ingot": "minecraft:block/iron_block",

  "yourmod:adamant_ingot": {
    "texture": "somestacks:block/minecraft/base_ingot",
    "tint": "#3ba55d"
  },

  "yourmod:plain_ingot": {
    "texture": "somestacks:block/minecraft/base_ingot",
    "tint": "#ffffff"
  }
}
```

- A **bare string** names a texture and asks for the tint to be **computed** from the item's own sprite and registered colour.
- An **object** may carry `texture` and `tint`. A mapping that carries a tint is settled by it — including a white one, which is how you say *draw this texture as it is*. A mapping with no tint gets a computed one.
- An item with **no mapping at all** uses the base ingot texture with an automatically derived tint where one can be found.

This data is **not synchronized**. Players with different resource packs may see different bar appearances, which is fine — it is presentation only.

## Item render profiles

Storage and Singles Stacks draw stored items inside their cells. Each item resolves to a profile: a **mode**, a **scale**, and an **offset**.

| Mode | Presentation |
| --- | --- |
| `2d` | Flat item art projected onto the visible faces of a small cell background. |
| `3d` | The item renderer in `FIXED` context. |
| `gui` | The item renderer in `GUI` context. |
| `block` | A `BlockItem`'s own block state, falling back to `3d` for block-entity-rendered or failing models. |

Profiles resolve by precedence, first match wins:

1. Server overrides — `config/somestacks/server_item_overrides/*.json`
2. User overrides — `config/somestacks/item_overrides.json`
3. Resource pack overrides — `assets/<ns>/item_render_overrides/*.json`
4. Automatic measurement

### Override file format

Resource-override files are **named for the namespace of the items they configure** — `create.json` configures `create:` items.

```json
{
  "yourmod:awkward_item": {
    "mode": "2d",
    "scale": 0.8,
    "offset": [0.0, 0.1, 0.0]
  },
  "yourmod:just_needs_shrinking": {
    "scale": 0.6
  }
}
```

Every field is optional, but **an entry owns the whole presentation**: an omitted scale is `1` and an omitted offset is zero, rather than falling through to a lower layer. An omitted `mode` is the sole exception — it is measured.

`scale` ranges from `0.01` to `20`. Each `offset` component ranges from `-1` to `1`. Values outside those ranges are rejected.

### Measurement and its cache

Items that no override layer configures are measured from their model. Results are cached in `config/somestacks/measured_cache.json` and invalidated by a relevant mod-version change, a change of selected resource packs between sessions, or a manual resource reload mid-session.

### Authoring workflow

The `/ss` command exists mostly for this loop. In game, as an operator:

1. `/ss gallery <modid>` — build a wall of Storage Stacks holding that namespace's items, so you can see everything at once. `/ss ingotgallery <modid>` does the same for Bar Stacks.
2. `/ss item <item> <mode> [<scale> [<x> <y> [<z>]]]` — correct one item. It applies to your view immediately, in memory.
3. `/ss item <item> reset` — drop your entry and go back to whatever the layer below gave it.
4. `/ss write changed` — save your entries to `config/somestacks/item_overrides.json`, which your client loads at startup. Only entries you set are ever written.
5. `/ss write <modid>` — dump a *complete* resolved profile for every item of that namespace to `config/somestacks/generated_overrides/<namespace>.json`, measuring whatever no layer configures.

Nothing reads `generated_overrides` back. A file there is a starting point: correct it by hand and drop it into a resource pack's `item_render_overrides`, or into a server's `server_item_overrides`.

The dump runs on your own client and holds it busy until it finishes, so name a namespace — or keep a review session's mods in the gen mod list and use `/ss write list` — rather than reaching for `all` in a large pack.

## Server-imposed overrides

`config/somestacks/server_item_overrides/*.json` uses the same format and takes precedence over everything a client has locally. The server sends these to each player on login; `/ss reload` re-reads the folder and pushes it to every connected player again.

Use this when a pack needs every player to see the same thing regardless of what they have in their own config.
