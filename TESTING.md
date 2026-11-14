# Craft of the Mine – manual test plan

Run with `gradlew :1.21.1:runClient` (add `-Dbotwclimb.debug=true` to the client JVM args to draw the planned jump path as
end-rod particles). Use creative mode + a stone brick/stone build area. Third person (F5) for animation checks.

Legend: `#` stone, `P` the player hanging on the wall, side view, wall is on the right.
Start climbing: **hold the Climb key (default Shift + Space, rebind in Options > Controls > Craft of the Mine; combos with Shift/Ctrl/Alt are allowed) for 0.2 s while next to a wall**. Walking into a wall never starts a climb; Space alone is a normal jump, except with W held under a block, where it is the ledge jump.
Controls while hanging: W/S up/down, A/D sideways, Space (release it first, then press) = climb jump, **Sneak = let go** (sprint no longer affects climbing).

Automatic geometry check of the jump planner (no game window needed):
`gradlew :1.21.1:runGameTestServer` – 66 headless tests (jump planning, ledge and long jumps, stamina, enchantment, server checks, packets). All must pass. Close the client first.

## TEST 1 – basic wall
Build a 12-high, 5-wide wall. Stand next to it and hold the Climb key for 0.2 s.
Expected: Space only does a normal jump; after 0.2 s of holding the key you grab the wall, then W climbs straight up at a slow constant speed, A/D shuffle sideways, S climbs
down, S at the floor lets go.

## TEST 2 – normal climb into a lip
```
 P|##      lip block sticking 1 block out of the wall, directly above the player's head
  |#
  |#
```
Climb up with W only.
Expected: you stop under the lip. You never pass or clip into it.

## TEST 3 – climb jump over a one-block lip
Same build, wall continues above the lip. Hang just below the lip (head within ~0.5 block of its underside), tap Space.
Expected: short crouch, then a fast launch up, you swing slightly out from the wall, pass the outer edge and the
top of the lip, come back in and re-grab the wall above it, and keep climbing with W. Animation: load, strong
two-arm upward reach, tuck while clearing the lip, re-grab. Variant: wall ends at the lip row – you finish standing
on top of the lip.

## TEST 4 – large obstruction
```
 ####      3 thick x 3 tall block hanging out of the wall above you
 ####
 ####
 P|#
```
Also try a 2-block-deep lip and a lip you are 4+ blocks below.
Expected: the jump never gets past. You hop (blocked by the ceiling), then either hang on again if the wall is still
there, or fall. No teleport, no noclip, no crawling along the underside.

## TEST 5 – collision
Put a roof one block above your head that extends several blocks outward, hang under it and press Space repeatedly.
Expected: your hitbox never enters the roof; the jump is only a tiny hop.

## TEST 6 – jump on an open wall
Halfway up a tall wall press Space once.
Expected: crouch, fast rise of about 3.5 blocks (much faster than W-climbing), you re-attach higher and can continue.

## TEST 7 – hold Space
Hold Space on a tall wall.
Expected: exactly one jump. The next jump needs Space released and pressed again (and the short cooldown over).

## TEST 8 – animation (third person, then F1 off to see yourself)
* idle on the wall: body against the wall, arms up gripping, one arm higher than the other, feet toward the wall
* W: alternating diagonal limbs (left arm + right leg, then right arm + left leg); stop and the limbs stop cycling
* S: same pose but lower reach, the cycle runs the other way (no "reaching up" while descending)
* A / D: arms reach toward the travel direction, torso leans that way
* Space: compress (arms pulled down, legs tucked) -> launch (both arms stretched up, body slightly taller)
  -> air (arms up, legs tucked, body leaning out when swinging round a lip) -> re-grab (one hand first) -> normal cycle
* Look around with the mouse: the camera/head moves freely (head turn limited to 70 degrees from the wall); the body stays
  turned to the wall.

## TEST 9 – other player (dedicated server)
Start `gradlew runServer` (accept the EULA yourself), join with two clients. Player A climbs, shuffles, jump-climbs.
Expected: player B sees the same climbing pose, limb cycling driven by A's movement, and the jump animation.
Also: B joins *after* A is already on the wall – A must already look like they are climbing.

## TEST 10 – wall ends
Jump near the end of a short wall that has no ledge/top to grab (e.g. a 1x1 pillar in the sky 2 blocks above you,
or a wall that ends with a roof over it).
Expected: you go up as far as the hop allows and then fall normally (and take fall damage).

## TEST 11 – letting go
While hanging (also mid-jump): press Shift.
Expected: you drop immediately and the climbing pose disappears. While Shift is still held you cannot re-grab. Sprint (Ctrl) has no effect on climbing.
Release the key and walk into the wall again to re-grab.

## TEST 12 – corners
Climb sideways around the outside edge of a 2-wide pillar and into the inside corner of an L-shaped wall.
Expected: you keep climbing round both; the body turns to the new wall face. At the free end of a wall you let go.

---

# Paraglider

Shortcut: **hold Shift, then press Space and keep both held for 0.4 s** while airborne (rebind in Options > Controls > Craft of the Mine > "Toggle Paraglider";
the order Shift -> Space is enforced, any Shift/Ctrl/Alt + key combo can be bound). Next to a climbable wall the same
chord is the climb key instead, and the glider never opens while climbing.
Automatic checks (`gradlew runGameTestServer`): chord ordering and one-press consumption, Elytra/climb/airborne rules,
catch from a fast fall, smooth acceleration, cruise speed, gradual deceleration.

1. Plain Space: normal jump. Nothing else happens.
2. Fall (jump off a hill), hold Shift first, then press Space: the glider opens.
3. Press Space first, then Shift: nothing opens.
4. Keep holding Shift+Space: exactly one toggle (no open/close flicker).
5. Keep Shift held, release Space, press Space again: closes. Press again: reopens. Falling continues in between.
6. Wear an Elytra but do not fly. Fall, hold Shift, wait a few ticks, press Space once.
   Expected: glider opens, `isFallFlying` stays false (F3 shows no gliding pose), no jump effect, the Elytra does NOT start.
   Keep Space held for a second, then release Shift, then Space: still no Elytra. The next separate Space press behaves
   like vanilla (it starts the Elytra while falling).
7. Elytra not worn: step 6 behaves the same; plain Space while gliding does nothing special.
8. Start flying with the Elytra (plain Space while falling, also with a firework rocket boost), then press Shift+Space:
   it switches to the paraglider on the press (no 0.4 s wait), the Elytra and the rocket boost stop, momentum is kept
   and bleeds off to glide speed. The glider must stay open (no short animation then back to Elytra).
9. Glide with no keys: slow, almost straight-down descent (~3 blocks/s). W: gradual acceleration to ~5 blocks/s, no climbing
   even when looking up. Release W: speed fades gradually. A/D/S steer relative to the camera.
10. Turn: the canopy and body bank into the turn (max ~15 degrees), the body lags slightly, a very small passive sway is visible.
11. Third person: both arms hold the struts above the head, the canopy faces the travel direction (not the camera), legs hang
    and trail. Look around freely with the mouse.
12. Open the glider from a long fall: the fall is caught within about 5 ticks, no upward bounce.
13. Land: the glider closes at once. Close it high up: normal falling resumes and fall damage applies.
14. Fly into a wall: you stop (no noclip). Enter water: the glider closes.
15. While climbing: Shift+Space does not open the glider. After falling off the wall it works again.
16. Dedicated server, two players: the other player sees the glider open, the hanging pose, the heading, bank and close.

17. Gliding (opened from an Elytra flight or a fall) with an Elytra equipped: Shift + Space in the air hands the flight back
    to the Elytra on the press, speed kept. Repeating it swaps again; each swap needs a fresh press.
18. Switch from a rocket-boosted Elytra: the glider starts at the rocket speed and bleeds off to cruise (~6 blocks/s) over a few seconds.

# Paraglider fabrics

`/cotm paraglider fabric <id>` (tab-completes) sets the fabric of the player running it; other players see it too.
Ids: ordinary, zonai, royal_hyrulean, sheikah, gerudo, zora, goron, yiga, cece, addison, chuchu, aerocuda, korok,
champion, majora, demon_king.
19. Open the glider, run the command for each id and look at the canopy from below and above (third person): the design
    reads the right way round on both faces, left/right wings match the reference (Zonai: cream left, slate right),
    tassels and the two long ribbons have the fabric's own colours; `addison` has no ribbons.
20. Textures live in `src/main/resources/assets/botwclimb/textures/entity/paraglider/<id>.png` and are generated by
    `python tools/make_fabrics.py [preview.png]` (preview.png = all canopies flat). Adding a fabric = one draw function +
    one table row there + one `add(...)` line in `FabricRegistry`.

## Fabric side menu (inventory)
21. Carry or wear an Elytra and open the inventory. A panel as tall as the screen sits beside the inventory with all 16
    fabrics, each with a small canopy picture and its name. The current fabric has a gold outline.
22. Scroll the mouse wheel over the panel to see every fabric; clicks in the panel never reach the slots behind it.
    Left-click a fabric: it becomes yours at once (the panel stays open), and the glider uses it when you open it.
    The Elytra item is unchanged and other players wearing the same Elytra keep their own fabric. Your fabric is saved
    with your character (relog, death). There is no keybind and no middle-click; creative cannot clone items from it.
23. The panel only shows while an inventory-style screen is open and you have an Elytra in it; it disappears otherwise.
    The Fabric key over anything that is not an Elytra does nothing. Middle-click (pick block) is NOT used, so creative never clones items because of this.

## Falling and the glider
24. Falls are 35% slower than before (gravity -35% for everyone, including other players).
25. Walk off a ledge of 2+ blocks and press Space once: the paraglider opens (not the Elytra). A plain jump on the
    ground or a small step does not open it. The Shift+Space chord still switches Elytra <-> glider as before.

## Rocket momentum (paraglider)
26. Fly Elytra with a rocket (flight 1, then 2, then 3), open the glider with Space while falling: the speed carries over
    the same at the start (right at the switch you keep the Elytra speed, not a slower one); flight 1 fades in about 6 s,
    flight 2 in about 8.4 s, flight 3 in about 10.7 s.
    Turns and steering still work during the fade.
27. The whole underside of the glider (canopy and both wings) is uniformly dark; no bright patches show through.

## Shrine terminal and plots
`gradlew runGameTestServer` checks the plot data, the island, the return landing and that the dimension files decode; the ride between
worlds itself needs the game (relaunch the client: new blocks, packets and a dimension).
28. Right-click a terminal: a screen with two plot cards opens (no ride yet). Plot 1 says Claim, no Return button.
29. Click Claim on Plot 1: the ride starts, you rise into the void, and you come down in front of the terminal on a
    grass island in empty sky. Third person the whole time; no controls until you land.
30. On the island: right-click the terminal. Plot 1 says "You are here" (cannot be clicked), Plot 2 says Claim, Return is shown.
    Return takes you back to exactly where you stood at the overworld terminal.
31. Claim Plot 2: a different island. Go back and forth: both islands keep what you built. Break the island terminal (survival
    or creative): it will not break; `/fill` over it removes it, and it is put back the next time you arrive.
32. Break the overworld terminal while you are in a plot, then Return: you land at the world spawn. `/plot return` does the same
    without a ride (only works inside a plot).
33. Quit the game mid-ride, load the world again: you are not floating, and you stand on the ground under where you stopped.

Ride visuals without a person at the keyboard: `gradlew runAutoClient` starts a second game in `run-auto` (own copy of the world, no Sodium),
rides to plot 1 and back by itself and saves frames to `run-auto/screenshots` (`python tools/sheet.py up 1 36 out.png` makes a contact sheet).
Needs `run-auto/saves/AutoTest` (copy a world there once). Quits when done.

## The ride
Ride to a plot and back (or `./gradlew runAutoClient`, only with no other game window open). Look for: green sparks and a ring/glyph while gathering; two cyan ribbons crossing in the room (the screen dimmed), turning green; a hard cut into a dark teal void with bright green spikes, drifting stars and a double helix, with the rider tumbling in it; the loading screen never showing terrain; a hard cut back with a pale haze (trip) or a cyan glow and swirling green sparks (Return); on a trip, a cyan bubble round the rider that pops. Details and known timing limits: notes/totk-ride.md.
