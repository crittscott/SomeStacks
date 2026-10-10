# Pack authors

Some Stacks is extensible from data packs and resource packs. Nothing here requires a Java addon.

## Quick map

| What | Where | Side |
| --- | --- | --- |
| Which items a Bar Stack accepts | `data/somestacks/tags/item/ingots.json` | server (data pack) |
| Sounds per stack type and action | `assets/somestacks/sounds.json` | client (resource pack) |
| Bar textures and tints | `assets/<ns>/textures/bars/*.json` | client (resource pack) |
| How items are drawn in cells | `assets/<ns>/item_render_overrides/*.json` | client (resource pack) |

A server that wants every player to see the same thing ships the client-side files in its server resource pack.

## Bar-valid items (data pack)

A Bar Stack holds exactly the items in the `somestacks:ingots` item tag. The shipped tag pulls in `#c:ingots`, `#forge:ingots`, and `#c:bricks` plus a list of vanilla and modded ingots and bricks, all optional. Extend it with `data/somestacks/tags/item/ingots.json` in your pack:

```json
{
  "replace": false,
  "values": [
    "yourmod:mithril_ingot",
    { "id": "#othermod:alloys", "required": false }
  ]
}
```

Mark entries from mods that may be absent `"required": false`; a missing required entry makes the whole tag fail to load. `"replace": true` discards the shipped list.

Anything a Bar Stack accepts, a Singles Stack refuses: the two rules are complements. A `/reload` picks up your changes. Items already stored stay where they are.

## Sounds (resource pack)

Every action plays a sound event the mod registers, each with its own subtitle:

| Type | Sound events |
| --- | --- |
| Storage | `somestacks:block.storage_stack.deposit`, `.extract`, `.rotate` |
| Singles | `somestacks:block.singles_stack.deposit`, `.extract`, `.rotate`, `.rotate_item` |
| Bar | `somestacks:block.bar_stack.deposit`, `.extract` |

By default they play the vanilla wood place, break, and hit sounds. Replace any of them the ordinary way, with `assets/somestacks/sounds.json` in a resource pack:

```json
{
  "block.bar_stack.deposit": {
    "replace": true,
    "sounds": [{ "name": "minecraft:block.metal.place", "type": "event" }]
  }
}
```

Rotation is limited to one sound per player every four ticks, because the gesture is free and repeatable; the rotation itself is never withheld.

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

1. User overrides — `config/somestacks/item_overrides.json`
2. Resource pack overrides — `assets/<ns>/item_render_overrides/*.json`, including the server's resource pack
3. Automatic measurement

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

An item whose model throws while being drawn in `2d` mode shows a cube marked with a red "ERR" instead, until the next resource reload, and the client log names the item. Giving it a different mode in an override is usually the fix.

### Authoring workflow

The `/ss` command exists mostly for this loop. `/ss item` and `/ss write` run on your own client and need no permission; the galleries need the server to enable them.

1. `/ss gallery <modid>` — build a wall of Storage Stacks holding that namespace's items, so you can see everything at once. `/ss ingotgallery <modid>` does the same for Bar Stacks.
2. `/ss item <item> <mode> [<scale> [<x> <y> [<z>]]]` — correct one item. It applies to your view immediately, in memory.
3. `/ss item <item> reset` — drop your entry and go back to whatever the layer below gave it.
4. `/ss write changed` — save your entries to `config/somestacks/item_overrides.json`, which your client loads at startup. Only entries you set are ever written.
5. `/ss write <modid>` — dump a *complete* resolved profile for every item of that namespace to `config/somestacks/generated_overrides/<namespace>.json`, measuring whatever no layer configures.

Nothing reads `generated_overrides` back. A file there is a starting point: correct it by hand and drop it into a resource pack's `item_render_overrides`.

The dump runs on your own client and holds it busy until it finishes, so name a namespace — or keep a review session's mods in the gen mod list and use `/ss write list` — rather than reaching for `all` in a large pack.

## Sharing profiles from a server

Put `item_render_overrides` files, and bar mappings if you like, in the server's resource pack. Players get them like any other pack content. A player's own `item_overrides.json` still wins on that player's screen.
