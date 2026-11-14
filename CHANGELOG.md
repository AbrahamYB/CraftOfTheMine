# Changelog

All notable changes to Craft of the Mine (CotM). The mod id stays `botwclimb`, so saved worlds, the config file and data pack paths
keep working across the rename.

## 1.2

### Added
* One jar now serves Minecraft 1.21.3 and 1.21.4 (`mc1.21.3-1.21.4`), so there are nine jars instead of ten.

### Changed
* The terminal ride is the Tears of the Kingdom travel only. The mud cocoon ride, its settings (ride style, speed lines, ground
  opening) and its textures are gone; an old config file simply ignores them.
* The shrine terminal's stone and gold are darker, so it has more shade and no longer looks lit from everywhere.
* Bullet time tolerates a slow connection better: a heartbeat may be half a second old before it stops.

### Fixed
* The server now checks wall climbing itself: a top speed on a held wall, a jump may not leave its reach for more than a few ticks, and a
  jump or ledge must start from a held wall. Each check allows a few ticks of lag, so a slow connection does not rubber-band.
* Removed the unused lag-picture code of bullet time (and its two network messages).

## 1.1

Fixes for the stamina wheel (this was first published as 1.0.1).

### Fixed
* The stamina wheel no longer pops up and refills when you join a world or server while standing still.
* The wheel's level is kept when you disconnect and comes back as it was (including being empty and refilling). Dying still refills it.
  The server saves it; both sides need this version.
* The wheel no longer freezes on screen when the game pauses (alt-tab) or stays up for minutes after a respawn, world change or rejoin.

## 1.0.0

### Added
* Builds for Minecraft 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1, 26.2 and 26.3.
* Bullet time slows mobs for real on the server (1 tick in 7 within 8 chunks) and slows other players while you aim.
* Wall climbing with a stamina cost, climb jumps, ledge jumps and long ledge jumps, and sliding round corners.
* A paraglider with fabrics that are learned by crafting them in a menu beside the inventory.
* The stamina wheel and the stamina altar (offer items to grow the wheel).
* The Climb Speed enchantment for boots.
* Bullet time for bows and crossbows.
* Shrine terminals and plot islands, with a mud-cocoon ride and a Tears of the Kingdom ride between them (the mud ride was removed in 1.2).
* A settings screen (Mods list > Craft of the Mine > Config), including whether the glider switches the camera to third person.
* The `/cotm` commands for admins (`stamina`, `cloth`, `paraglider`) and `/plot return`.

### Changed
* The mod is now called Craft of the Mine (CotM). The commands moved from `/botwclimb` to `/cotm`.
* Gravity is vanilla again; only bullet time slows a fall.
* The stamina altar breaks like an ender chest (any pickaxe) and drops itself.
* The glider shortcut opens the glider only after a fall of at least 2 blocks, and a plain jump after 4.5 blocks.
* The camera pulls out 25% when you climb.

### Fixed
* Shift + Space did nothing as a jump. NeoForge gave the key press only to the climb and glider bindings, so it is handed back to
  vanilla when neither applies.
* Letting go of W at the top of a wall hop dropped you instead of finishing the climb.
* A wall behind or beside you no longer counts as grabbable; you must be facing it (within 60 degrees).
