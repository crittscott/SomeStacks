# Player guide

Some Stacks has no screens and no recipes. You place storage by depositing into it, and you remove it by emptying it.

## The stack modifier key

`V` by default, rebindable under **Options → Controls → Some Stacks → Stack Modifier (Hold)**. It is a *held* modifier, not a toggle: every gesture below is performed while the key is down.

## Placement modes

Hold `V` and right-click the air to cycle through:

1. **Storage Stack**
2. **Singles Stack**
3. **Bar Stack**
4. **Toggle Permanent**

The current mode is shown above your hotbar. Stack types the server has disabled are skipped by the cycle; Toggle Permanent is always available.

The mode decides only what gets *placed*. Depositing into a stack that already exists always goes into that stack, whatever mode you are in.

## Depositing

Hold `V` with something in your hand and right-click:

- **a stack** — the items go into it.
- **any other block** — if a Singles or Bar Stack sits on the face you clicked, the items go into that. Otherwise a stack of the selected type is created in that empty space and takes the first deposit.

If you click the **top face** of a Singles or Bar Stack and the cell you are aiming at cannot take the item — it is occupied, or nothing is under it to hold it up — the gesture places a new stack on top instead and deposits there. That is how you build a column upward by hand.

A deposit that would create a block only happens if the deposit itself would succeed, so you never end up with an empty block you did not ask for.

In **creative mode**, depositing does not spend the stack in your hand, the same way placing a block does not.

## Taking things back

Right-click a stack with **no `V` and no Shift**. The nearest item along your line of sight comes out.

- **Storage** gives you as much as your hand will take.
- **Singles** and **Bar** give you one item.

Your hand must be empty, or already hold the same item with room left.

A plain right-click on a stack is consumed by the block, so whatever you are holding is never used against it. Sneak if you want the normal interaction.

## Storage Stacks

Twenty-seven ordinary item stacks drawn as a 3×3×3 grid with gaps between them.

A **vertical run of Storage Stacks is one pile** — one inventory over its whole height. Deposits fill it from the bottom upward no matter which block you aimed at, and the pile grows a block upward when it runs out of room.

Shortly after any change, the pile **settles**:

- compatible stacks merge, by exact item, damage, and NBT;
- everything packs down toward the base and sorts;
- empty blocks at the top remove themselves.

So a pile shrinks back into the ground as you empty it. If you would rather it stayed:

**Toggle Permanent** — select that mode, hold `V`, and right-click a Storage Stack with an **empty hand**. The whole pile stops removing itself. Do it again to turn it back off.

## Singles Stacks

Sixty-four items, one per cell, in a 4×4×4 grid. Anything a Bar Stack accepts, a Singles Stack refuses.

**Support:** every item above the bottom layer needs an item directly below it. The bottom layer of the lowest block in a run rests on the world; the bottom layer of a block stacked on another rests on that block's top layer. Support is followed in *visual* columns, so it works correctly even between two blocks that have been rotated differently.

**Gravity:** take an item out and everything above it in that visual column drops one layer, pulling items across block boundaries as needed. Per-item rotations come down with them. Blocks emptied at the top remove themselves.

**Rotation:** shift + right-click with a **redstone torch** turns the whole block 90°. Shift + right-click an item with a **soul torch** turns that one item 90°. Item rotations are saved per cell.

## Bar Stacks

Sixty-four ingots rendered as bars: eight layers of eight, each layer laid across the one beneath it.

Which items count as ingots is a server setting (see [Server administration](server-admin.md)) — by default, everything under the `forge:ingots` tags on Forge or `c:ingots` on Fabric, plus the mod's own `somestacks:ingots` tag, which a data pack can extend.

**Support:** every bar above the bottom layer must overlap a bar in the layer below.

**Collapse:** when *you* pull a bar out, everything it was holding up falls and drops as items. Breaking or replacing a Bar Stack collapses the column above it the same way. (Automation is gentler — see [Automation](automation.md).)

Bar Stacks have no rotation gesture; bars are fixed to their layer.

## Other behaviour worth knowing

- **Light.** A cell holding a block that glows contributes a quarter of that block's light, and the block's total is capped at 15. A stack of glowstone is a lamp.
- **Water.** All three types are waterloggable, and a stack placed into water keeps the water.
- **Shape.** Singles and Bar Stacks are shaped by what they hold, so an empty one shows no outline — but you can still click and break it. Neither one suffocates you, and mobs will not path through them.
- **Breaking.** Breaking a stack drops what that block holds. There is no block item to pick back up.
- **Height.** Piles and columns are limited to 8 blocks by default. The server can change this.
