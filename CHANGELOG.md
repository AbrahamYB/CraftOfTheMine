# Changelog

All notable changes to Craft of the Mine (CotM). The mod id stays `botwclimb`, so saved worlds, the config file and data pack paths
keep working across the rename. Versions are numbered like old Minecraft: 1.0, 1.1, 1.2, and so on.

## 1.2

### Added
* One jar now serves Minecraft 1.21.3 and 1.21.4 (`mc1.21.3-1.21.4`; their code is byte for byte the same), so there are nine jars
  instead of ten.
* The server checks wall climbing itself (see Fixed).

### Changed
* The terminal ride is the Tears of the Kingdom travel only. The mud cocoon ride, its settings (ride style, speed lines, ground
  opening) and its textures are gone; an old config file simply ignores them.
* The shrine terminal's stone and gold are darker, so it has more shade and no longer looks lit from everywhere.
* Bullet time tolerates a slow connection better: a heartbeat may be half a second old before it stops, and it lingers a moment longer
  after you let go.

### Fixed
* A modified client can no longer climb at any speed: on a held wall the server caps the speed (0.7 blocks per tick for more than 3
  ticks in a row), ends a climb jump that stays outside its reach for more than 8 ticks, and starts a jump or ledge only from a wall held
  in the last 10 ticks. Every check counts consecutive ticks, so a lag spike or a slow connection does not rubber-band a real player.

### Removed
* The old client-side "lag picture" of bullet time (drawing other players late and rewinding arrow hits) and its two network messages.
  It had been unused since bullet time moved to the server in 1.0. The unused `backup/` copy of it is gone too (git history keeps it).

## 1.1

Fixes for the stamina wheel (first published as 1.0.1, renamed 1.1).

### Fixed
* The stamina wheel no longer pops up and refills when you join a world or server while standing still.
* The wheel's level is kept when you disconnect and comes back as it was (including being empty and refilling). Dying still refills it.
  The server saves it; both sides need this version (it adds one network message).
* The wheel no longer freezes on screen when the game pauses (alt-tab) or stays up for minutes after a respawn, world change or rejoin.
  Its timing now follows real time instead of the player's tick count.

## 1.0

The first full release. It replaces the one-version `mc1.21.1-1.0.0` build (1.21.1 only, with the old client-side bullet time) and the
ten-version `v1.0.0`; both were numbered 1.0.0 but were different builds, so they are now this one release.

### Added
* Builds for Minecraft 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1, 26.2 and 26.3, all from one source tree
  (Stonecutter). A world moves up a version without losing stamina upgrades, fabrics or altar credit; Minecraft cannot load a world on
  an older version, so back up before downgrading.
* Wall climbing with a stamina cost, climb jumps, ledge jumps and long ledge jumps, and sliding round corners.
* A paraglider, opened from an Elytra flight (Shift + Space) or after a fall, with 16 Tears of the Kingdom fabrics (data driven, 64x
  textures with tassels, ribbons and wind trails that follow your speed). Fabrics are learned by crafting them in a menu beside the
  inventory; each player keeps their chosen fabric.
* The stamina wheel and the stamina altar (offer items to grow the wheel).
* The Climb Speed enchantment for boots.
* Bullet time for bows and crossbows (see "Bullet time rewrite" below).
* Shrine terminals and plot islands, with a mud-cocoon ride and a Tears of the Kingdom ride between them (the mud ride was removed in 1.2).
* A settings screen (Mods list > Craft of the Mine > Config), including whether the glider switches the camera to third person.
* The `/cotm` commands for admins (`stamina`, `cloth`, `paraglider`) and `/plot return`.

### Bullet time rewrite
* The first build faked bullet time on the client: your screen drew mobs and players in the past and the server rewound their hitboxes
  to match. Mobs looked half stuck and hits did not line up with the picture.
* Now the server really slows mobs: within 8 chunks of a shooter in bullet time a mob gets 1 tick in 7 (about 14% speed), keeps its hurt
  timers on the real clock, and its pathfinding timeout is stretched. The client only smooths the picture. Players cannot have ticks
  skipped, so other players are slowed with temporary attribute modifiers; creative, spectating and riding players, the shooter and
  Create contraptions are left alone.
* The shooter falls slowly while aiming in mid-air, using the same server check.

### Security passes (two rounds)
* The server no longer trusts any client claim. Bullet time checks that the player really is in the air (falling, nothing solid just
  below, not riding, swimming or on a ladder) and has a budget (400 ticks, refilled by resting). Glider speed, stamina regeneration,
  altar offers (slot, count, item, distance and block are re-checked, refunds are capped), plot travel and saved plot data are all
  checked by the server too.
* Every server-to-client message goes through one guard that skips players without the mod, so a vanilla client can still join.
* Plots: an island is built once (nothing a player dug out is refilled), at most 2000 per world, other players' entities are protected,
  a broken saved entry no longer breaks the rest, plots close to friends when the owner logs out, the terminal cannot be broken, blown
  up or pushed, and falling out of the world brings you back.

### Changed
* The mod is now called Craft of the Mine (CotM). The commands moved from `/botwclimb` to `/cotm`.
* Gravity is vanilla again; only bullet time slows a fall.
* The stamina altar breaks like an ender chest (any pickaxe) and drops itself.
* The glider shortcut opens the glider only after a fall of at least 2 blocks, and a plain jump after 4.5 blocks.
* The camera pulls out 25% when you climb.
* Known gaps on newer versions: the ride's motion blur is off from 1.21.2 (Minecraft changed the post effect format); from 1.21.6 the
  ride's void is drawn as flat GUI quads and covers the hotbar.

### Fixed
* Shift + Space did nothing as a jump. NeoForge gave the key press only to the climb and glider bindings, so it is handed back to
  vanilla when neither applies.
* Letting go of W at the top of a wall hop dropped you instead of finishing the climb.
* A wall behind or beside you no longer counts as grabbable; you must be facing it (within 60 degrees).
