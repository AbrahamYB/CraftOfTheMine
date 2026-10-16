# BOTW Climb – manual test plan

Run with `gradlew runClient` (add `-Dbotwclimb.debug=true` to the client JVM args to draw the planned jump path as
end-rod particles). Use creative mode + a stone brick/stone build area. Third person (F5) for animation checks.

Legend: `#` stone, `P` the player hanging on the wall, side view, wall is on the right.
Start climbing: **hold the Climb key (default Shift + Space, rebind in Options > Controls > BOTW Climb; combos with Shift/Ctrl/Alt are allowed) for 0.6 s while next to a wall**. Space and walking into a wall never start a climb.
Controls while hanging: W/S up/down, A/D sideways, Space (release it first, then press) = climb jump, **Sneak or Sprint = let go**.

Automatic geometry check of the jump planner (no game window needed):
`gradlew runGameTestServer` – 7 headless tests (open wall, 1-block lip, lip on a wall top, lip out of reach,
3-block block, 2-block-deep lip, low roof). All must pass.

## TEST 1 – basic wall
Build a 12-high, 5-wide wall. Stand next to it and hold the Climb key for 0.6 s.
Expected: Space only does a normal jump; after 0.6 s of holding the key you grab the wall, then W climbs straight up at a slow constant speed, A/D shuffle sideways, S climbs
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
While hanging (also mid-jump): press Shift; hold Ctrl (sprint); double-tap W.
Expected: you drop immediately and the climbing pose disappears. While Shift or Ctrl is still held you cannot re-grab.
Release the key and walk into the wall again to re-grab.

## TEST 12 – corners
Climb sideways around the outside edge of a 2-wide pillar and into the inside corner of an L-shaped wall.
Expected: you keep climbing round both; the body turns to the new wall face. At the free end of a wall you let go.

---

# Paraglider

Shortcut: **hold Shift, then press Space and keep both held for 0.4 s** while airborne (rebind in Options > Controls > BOTW Climb > "Toggle Paraglider";
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
