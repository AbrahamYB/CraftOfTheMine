# Craft of the Mine (CotM) – NeoForge 1.21.1

Breath-of-the-Wild style movement: wall climbing, a paraglider, a stamina wheel and a bullet-time bow. One jar, no dependencies.

**Climbing.** Hold the Climb key (default Shift + Space, rebindable in Options > Controls, combos allowed) for 0.2 s next to a wall to
grab it. W/A/S/D move along it at one constant speed; Sneak lets go. Space is a climbing jump (A/D alone hop sideways, W with A/D go
diagonally). With W held, Space under a block or at the top of a wall is a ledge jump: hang from the edge, then pull up and stand
(2 and 3 blocks deep overhangs get a long horizontal jump; 2x2 and deeper are refused). Taking damage while on a wall knocks you off.

**Stamina.** Wheels of 750 units, up to three (2250): the first wheel is free and each of 8 upgrades adds a quarter wheel (187.5 units). Climbing drains 36.5/s while moving; a wall jump costs 22.5% of a wheel, a ledge
jump 13%, a long ledge jump 17.5%; gliding drains it too. Running out leaves you exhausted until the wheel is full again. It recovers
whenever you are not climbing or gliding. The server keeps its own, more generous account so a modified client cannot climb or glide forever.

**Stamina altar** (craft: 8 obsidian around 1 emerald; mine with a diamond pickaxe). Offer valuables for upgrades:
diamond is 2 points, other items are listed in `data/botwclimb/data_maps/item/altar_value.json` (change it with a datapack), and tools
and armor are worth their materials (diamond gear by its recipe, netherite plus 3 / 4, plus 2 for a level 4-5 enchantment, scaled by
durability, over half needed). The 8 upgrades cost 10, 14, 20, 28, 38, 50, 64 and 80 points; extra points are kept as credit, and
after the last upgrade credit can be taken back as an item. The altar's rings and runes light up per player, and finishing all 8
plays a short blackout and then glows cyan for good.

**Camera.** While climbing or gliding the camera is locked to third person, 30% farther back, with a smooth pull out and back
(F5 is ignored meanwhile); your own perspective is restored afterwards.

**Climb Speed enchantment** (boots, enchanting table only, needs the top slot): 25% faster climbing and 15% faster wall jumps.

**Bullet time.** Holding a bow in the air slows nearby mobs on your screen; shots cost stamina. See `client/bullettime`.

Dev commands (op only): `/botwclimb stamina <0-10>` sets capacity upgrades, `/botwclimb paraglider fabric <id>` picks a fabric.
Block tag `botwclimb:non_climbable` lets blocks opt out. See `TESTING.md` for the manual test plan.

* `gradlew build` - jar in `build/libs`
* `gradlew runClient` / `runServer` - dev run (add `-Dbotwclimb.debug=true` to draw jump paths and log state changes)
* `gradlew runGameTestServer` - headless game tests (close the client first)
* Optional `local.properties` (not in git): `quickWorld=<save folder>` boots straight into that world, `window=1280x720` sets the window size.

## Paraglider fabrics

Every paraglider fabric except the ordinary one is learned from the fabric menu: the button beside the inventory opens it, a click
on a fabric shows its recipe (2 wool, 2 string and a dye, a trim and an item of its own, chosen by how it looks), and "Craft and
learn" uses the items up and teaches the fabric on the spot. There is no cloth item, so nothing can be passed to another player.
Recipes are in `FabricRegistry`. Learned fabrics are saved with the player; the rest are dimmed. Admins:
`/botwclimb cloth unlock <fabric|all>` and `/botwclimb cloth reset`.

## Updating the running dev client without a restart

`gradlew runClient` opens a debug port (5005). After a code change, `gradlew hotswap` compiles and pushes the changed classes into the
running client (`tools/HotSwap.java`). On a normal JVM only method bodies swap; on the JetBrains Runtime (set `jbr=<its folder>` in
`local.properties` and `org.gradle.java.installations.paths=<that folder>` in `~/.gradle/gradle.properties`) a swap may also add and
remove methods and fields. Registrations (new blocks, items, particles, network messages) and brand-new classes' registrations still
need a restart (the tool says so). Changed textures, models and lang files: press F3+T in the game.
