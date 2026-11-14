# Changelog

All notable changes to Craft of the Mine (CotM). The mod id stays `botwclimb`, so saved worlds, the config file and data pack paths
keep working across the rename. Versions are numbered like old Minecraft: 1.0, 1.1, 1.2, and so on. Use the same version on a server
and on every client that joins it.

## 1.2

### Added
* **One jar for two Minecraft versions.** Minecraft 1.21.3 and 1.21.4 compile to byte for byte the same code in this mod, so a single
  file (`CraftOfTheMine-1.2+mc1.21.3-1.21.4.jar`) now serves both. That makes nine jars instead of ten. The other neighbouring versions
  cannot share a file: Minecraft itself changed (rendering in 1.21.2, saved data and input in 1.21.5, rendering and input again in
  1.21.9, the drawing pipeline in 26.2), and the jars differ in 12 to 143 classes.
* **The server checks wall climbing itself.** Until now a modified client could report climbing at any speed, or start a climb jump or
  a ledge hop from nothing, and the server believed it. Now, while a player is on a held wall, the server watches how far they move
  each tick. More than 0.7 blocks for more than 3 ticks in a row ends the climb (normal climbing is far slower). A climb jump that stays
  outside the area a real jump can reach for more than 8 ticks in a row is ended. A jump or ledge hop may only start from a wall the
  player held within the last 10 ticks. Every check counts consecutive ticks, never a single one, so a lag spike or a burst of
  delayed packets cannot trip it and a player on a bad connection is not pulled back. When it does end a climb, `latest.log` says so
  ("let go"). These limits are tested with unit tests; they have not been tried on a real laggy connection yet.

### Changed
* **Bullet time is now really slowed by the server, the way the Awakened mod does slow motion.** Mobs within 8 chunks of a shooter in
  bullet time get 1 tick in 7, about 14% speed, so their movement, hurt timers, pathing and hitboxes all slow together and a hit lands
  where you see it. Other players cannot have ticks skipped, so they are slowed with temporary speed modifiers instead. The method was
  studied from that mod and written from scratch; none of its code or files are used. This replaces the first build, which drew mobs
  and players in the past on your screen and rewound hitboxes on the server: mobs looked half stuck and hits did not line up. The
  rewrite was in 1.0; this release finishes it. The old code is removed, the server checks that the shooter is really in the air,
  and a heartbeat may be half a second old before bullet time stops, so a slow connection no longer makes it flicker on and off.
  Bullet time also lingers about a quarter of a second after you let go, so it does not cut off mid-shot.
* **The terminal ride is now only the Tears of the Kingdom travel** (you rise into the void and come back down on the return trip). The
  older mud-cocoon ride, where mud wound round you and the ground opened, is gone together with its settings (ride style, speed lines,
  ground opening) and its textures. An old config file simply ignores the removed settings. The motion blur setting stays (1.21.1 only,
  because newer versions changed the post-effect format).
* **The shrine terminal is darker.** Its stone and gold textures were flat and bright, so the game's face shading had nothing to work
  with and the block looked lit from every side. The stone is now 62% and the gold 80% of the old brightness, so top, sides and
  bottom read differently. Not yet checked in a running game.

### Removed
* The first bullet time's "lag picture" code (the part that drew other players late and rewound arrow hits) and the two network
  messages it used. Since the rewrite nothing called it. The unused `backup/` copy is gone as well; git history keeps all of it.

## 1.1

Fixes for the stamina wheel. First published as 1.0.1 and renamed 1.1. It adds one network message, so server and clients must match.

### Fixed
* **The wheel no longer pops up and refills when you join.** Every join started a fresh wheel at full and the HUD treated that as
  "stamina just recovered", so it popped up and animated a refill even when you were standing still. Now the server sends your saved
  level on join, and the wheel only appears when you are using it (climbing, gliding, or not full).
* **The level survives disconnecting.** The server saves how much is left and whether you were out of breath, and sends it back
  when you rejoin. Disconnect halfway up a wall or empty and you return with the same amount, and the wheel shows again because
  you are mid-action. Dying still refills it. A server that does not have this version cannot save it.
* **The wheel no longer sticks on screen after alt-tab, a pause, a respawn or a world change.** Its fade and recharge timers counted the
  player's ticks, which stop while the game is paused or the player is replaced, so it froze half shown or stayed up for minutes. The
  timers now use real time, and the wheel resets when the player entity changes.

## 1.0

The first full release. It replaces the one-version `mc1.21.1-1.0.0` build (1.21.1 only, with the old client-side bullet time) and the
ten-version `v1.0.0`; both were numbered 1.0.0 but were different builds, so they are now this one release.

### Added
* **Ten Minecraft versions from one source tree**: 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1, 26.2 and 26.3 (NeoForge
  only; versions with just beta NeoForge builds, like 1.21.2, are skipped). Saved data is written the same way on every version, so a
  world moved up a version keeps its stamina upgrades, fabrics and altar credit. Minecraft cannot load a world on an older version,
  so back up before downgrading.
* **Wall climbing.** Face a wall within about 0.3 blocks (and within 60 degrees of looking at it, so walls behind or beside you do not
  count) and hold the climb key to grab it. Climbing costs stamina. You can hop up, make a climb jump, a ledge jump and a long ledge
  jump, and slide round corners.
* **The paraglider.** Shift + Space during an Elytra flight switches to it, keeping your Elytra speed; after a fall of at least 2
  blocks the glider shortcut opens it, and a plain jump does so after 4.5 blocks. There are 16 Tears of the Kingdom fabrics, drawn
  at 64x with tassels and ribbons that wave with your speed and wind trails behind the canopy. Fabrics are learned by crafting them
  in a menu beside the inventory, and each player keeps their chosen one.
* **The stamina wheel and the stamina altar.** The wheel shows what climbing and gliding cost. Offer items at the altar to grow it. The
  altar breaks like an ender chest (any pickaxe) and drops itself.
* **The Climb Speed enchantment** for boots.
* **Bullet time** for bows and crossbows: aim in the air to slow the world while you shoot, at a stamina cost per shot. See the rewrite
  below.
* **Shrine terminals and plot islands.** A terminal sends you on a ride to your own private plot island and back (a mud-cocoon ride or a
  Tears of the Kingdom ride; the mud one was removed in 1.2). Plot owners can open it to friends, and `/plot return` brings you home from
  inside the plot.
* **A settings screen** (Mods list > Craft of the Mine > Config), including whether the glider switches the camera to third person.
* **`/cotm` admin commands**: `stamina`, `cloth` and `paraglider`, for game masters.

### Bullet time rewrite
* The first build faked bullet time on the client: your screen drew mobs and players in the past and the server rewound their hitboxes
  to match. Mobs looked half stuck and hits did not line up with the picture.
* The server now really slows mobs, using the approach of the Awakened mod (studied and written from scratch, no code used). Within 8
  chunks of a shooter in bullet time a mob gets 1 tick in 7 (about 14% speed), its hurt timers stay on the real clock, and its
  pathfinding timeout is stretched so it does not give up. The client only smooths the picture. Other players cannot have ticks skipped,
  so they get temporary speed modifiers; creative and spectator players, riders, the shooter and Create contraptions are left alone.
* The shooter falls slowly while aiming in mid-air, using the same server check.

### Security passes (two rounds)
Every message a client can send was re-checked on the server instead of trusted:
* **Bullet time** needs the player really in the air (falling, nothing solid just below, not riding, swimming or on a ladder) and has a
  budget of 400 ticks that refills while resting, so a modified client cannot hold it forever.
* **Glider speed and stamina regeneration** are checked by the server; a glide ends if you sink faster than 1 block per tick.
* **Altar offers** re-check slot, count, item, distance and the block; refunds are capped.
* **Plots**: an island is built once, so nothing a player dug out is refilled; a world holds at most 2000 plots; a broken saved entry
  no longer breaks the others; plots close to friends when the owner logs out; other players' entities are protected; falling out of the
  world brings you back; the terminal cannot be broken, blown up or pushed.
* **Messages to players without the mod** all go through one guard that skips them, so a vanilla client can still join.

### Changed
* The mod is called Craft of the Mine (CotM) now; the commands moved from `/botwclimb` to `/cotm`.
* Gravity is vanilla again; only bullet time slows a fall.
* The camera pulls out 25% when you climb.
* Known gaps on newer versions: the ride's motion blur is off from 1.21.2 (Minecraft changed the post-effect format), and from 1.21.6 the
  ride's void is drawn as flat GUI quads and covers the hotbar.

### Fixed
* Shift + Space did nothing as a jump. NeoForge only gave the key press to the climb and glider bindings, so it is now handed back to
  vanilla when neither applies.
* Letting go of W at the top of a wall hop dropped you instead of finishing the climb.
* A wall behind or beside you no longer counts as grabbable; you must be facing it.
