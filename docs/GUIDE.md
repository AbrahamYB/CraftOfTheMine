# Craft of the Mine: player guide

Everything the mod adds and how it behaves. Numbers here are the defaults in the code (`ClimbConstants`, `ParagliderConstants`, `BulletConstants`).

Craft of the Mine plays like Breath of the Wild movement in Minecraft: wall climbing with ledge jumps, a paraglider with craftable fabrics, a stamina wheel and altar, bullet time with bows and crossbows, and personal plots reached through shrine terminals. One jar, no dependencies. The mod id is still `botwclimb` (worlds, config and the jar's contents keep that name); the mod is called Craft of the Mine (CotM).

**Climbing.** Hold the Climb key (default Shift + Space, rebindable in Options > Controls, combos allowed) for 0.2 s next to a wall to
grab it. W/A/S/D move along it at one constant speed; Sneak lets go. Space is a climbing jump (A/D alone hop sideways, W with A/D go
diagonally). With W held, Space under a block or at the top of a wall is a ledge jump: hang from the edge, then pull up and stand
(2 and 3 blocks deep overhangs get a long horizontal jump; 2x2 and deeper are refused). Taking damage while on a wall knocks you off.

**Stamina.** Wheels of 750 units, up to three (2250): the first wheel is free and each of 8 upgrades adds a quarter wheel (187.5 units). Climbing drains 36.5/s while moving; a wall jump costs 22.5% of a wheel, a ledge
jump 13%, a long ledge jump 17.5%; gliding drains it too. Running out leaves you exhausted until the wheel is full again. It recovers
whenever you are not climbing or gliding. The server keeps its own, more generous account so a modified client cannot climb or glide forever.

**Stamina altar** (craft: 8 obsidian around 1 emerald; it breaks like an ender chest: any pickaxe, and it drops itself). Offer valuables for upgrades:
diamond is 2 points, other items are listed in `data/botwclimb/data_maps/item/altar_value.json` (change it with a datapack), and tools
and armor are worth their materials (diamond gear by its recipe, netherite plus 3 / 4, plus 2 for a level 4-5 enchantment, scaled by
durability, over half needed). The 8 upgrades cost 10, 14, 20, 28, 38, 50, 64 and 80 points; extra points are kept as credit, and
after the last upgrade credit can be taken back as an item. The altar's rings and runes light up per player, and finishing all 8
plays a short blackout and then glows cyan for good.

**Camera.** While climbing the camera is locked to third person, 25% farther back, with a smooth pull out and back (F5 is ignored
meanwhile); your own perspective is restored afterwards. While gliding the same view is used unless you turn "Third person while gliding"
off in the settings, in which case the glider leaves your camera alone.

**Climb Speed enchantment** (boots, enchanting table only, needs the top slot): 25% faster climbing and 15% faster wall jumps.

**Bullet time.** Holding a bow or crossbow in the air slows the fall and really slows the mobs around you (they get about 1 tick in 7 within 8 chunks of you, so what you see is where they are and arrows hit what is drawn); other players near you are slowed down too (their walking, falling, jumping and hitting), but never creative players, riders or Create contraptions. Shots cost stamina. See `common/bullettime` and `client/bullettime`.

**Shrine terminals and plots.** A shrine terminal (creative inventory or `/give`) lists the plots, lets you claim one and travel to it. The plot
dimension is a void with a grass island per plot, and only a plot's owner (or a friend they have opened it to) can build in it. Riding
there and back plays the Tears of the Kingdom travel. `/plot return` is the way out if the
terminal is somehow gone. The terminal of a plot cannot be broken or blown up.

**Settings.** Mods list > Craft of the Mine > Config: the ride's motion blur (1.21.1 only), the plot outline and
the glider's automatic third person. They are saved in `config/botwclimb-client.toml`.

**Keys.** Options > Controls > Craft of the Mine: the climb key and the glider shortcut, both Shift + Space by default. Next to a wall you
are facing, holding it climbs; in the air after a fall of 2 blocks or more it opens the glider; otherwise it is an ordinary jump (so
bridging and pillaring with Shift held still work). A plain jump after a fall of 4.5 blocks or more also opens the glider.

Commands (op only, root `/cotm`, short for Craft of the Mine): `/cotm stamina <0-8|reset>` sets capacity upgrades, `/cotm paraglider fabric <id>` picks a fabric,
`/cotm cloth unlock <fabric|all>` and `/cotm cloth reset` for fabrics. These were `/botwclimb ...` before the rename.
Block tag `botwclimb:non_climbable` lets blocks opt out. See `TESTING.md` for the manual test plan.

## Paraglider fabrics

Every paraglider fabric except the ordinary one is learned from the fabric menu: the button beside the inventory opens it, a click
on a fabric shows its recipe (2 wool, 2 string and a dye, a trim and an item of its own, chosen by how it looks), and "Craft and
learn" uses the items up and teaches the fabric on the spot. There is no cloth item, so nothing can be passed to another player.
Recipes are in `FabricRegistry`. Learned fabrics are saved with the player; the rest are dimmed. Admins can unlock them with the `/cotm cloth` commands above.
