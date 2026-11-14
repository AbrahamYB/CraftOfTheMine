# Changelog

All notable changes to Craft of the Mine (CotM). The mod id stays `botwclimb`, so saved worlds, the config file and data pack paths
keep working across the rename. Versions are numbered like old Minecraft: 1.0, 1.1, 1.2, and so on. Use the same version on a server
and on every client that joins it.

## 1.2

### Changed
* **The shrine terminal casts shadows.** Darker textures alone left it looking flat and unlike the world around it. It now has a soft
  shadow on the ground around its base, a shadow on the base's top where the floating top overhangs, a gradient down its pillar just
  under the top, and darker sides toward the ground. These are drawn as part of the block's model, so they work on every version.

## 1.1

The first update. It fixes the stamina wheel, adds server checks on climbing, and finishes bullet time.

### Added
* **One jar for two Minecraft versions.** Minecraft 1.21.3 and 1.21.4 compile to byte for byte the same code in this mod, so a single
  file (`CraftOfTheMine-1.1+mc1.21.3-1.21.4.jar`) serves both. That makes nine jars instead of ten. The other neighbouring versions
  cannot share a file: Minecraft itself changed (rendering in 1.21.2, saved data and input in 1.21.5, rendering and input again in
  1.21.9, the drawing pipeline in 26.2), and the jars differ in 12 to 143 classes.
* **The server checks wall climbing itself.** A modified client could report climbing at any speed, or start a climb jump or a ledge hop
  from nothing, and the server believed it. Now, while a player is on a held wall, the server watches how far they move each tick.
  More than 0.7 blocks for more than 3 ticks in a row ends the climb (normal climbing is far slower). A climb jump that stays outside
  the area a real jump can reach for more than 8 ticks in a row is ended. A jump or ledge hop may only start from a wall the player held
  within the last 10 ticks. Every check counts consecutive ticks, never a single one, so a lag spike or a slow connection does not
  pull a real player back. When it does end a climb, `latest.log` says so ("let go").

### Changed
* **Bullet time is slowed by the server, the way the Awakened mod does slow motion.** Mobs within 8 chunks of a shooter in bullet time
  get 1 tick in 7, about 14% speed, so their movement, hurt timers, pathing and hitboxes all slow together and a hit lands where you see
  it. Other players cannot have ticks skipped, so they are slowed with temporary speed modifiers. The method was studied from that mod
  and written from scratch; none of its code or files are used. The server checks that the shooter is really in the air, and a
  heartbeat may be half a second old before bullet time stops, so a slow connection no longer makes it flicker. Bullet time also lingers
  about a quarter of a second after you let go, so it does not cut off mid-shot.
* **The terminal ride is the Tears of the Kingdom travel only.** The older mud-cocoon ride and its settings (ride style, speed lines,
  ground opening) and textures are gone; an old config file simply ignores them. The motion blur setting stays (1.21.1 only).
* **The shrine terminal is darker.** Its stone and gold textures were flat and bright, so the block looked lit from every side. The
  stone is now 62% and the gold 80% of the old brightness.

### Fixed
* **The stamina wheel no longer pops up and refills when you join.** Every join started a fresh wheel at full and the HUD treated that as
  "stamina just recovered". Now the server sends your saved level on join, and the wheel only appears when you are using it.
* **The level survives disconnecting.** The server saves how much is left and whether you were out of breath, and sends it back when you
  rejoin. Disconnect halfway up a wall or empty and you return with the same amount, and the wheel shows again because you are
  mid-action. Dying still refills it. Both sides need this version (it adds a network message).
* **The wheel no longer sticks on screen after alt-tab, a pause, a respawn or a world change.** Its timers counted the player's ticks,
  which stop while the game is paused or the player is replaced. They now use real time.

## 1.0

The first release: climbing, the paraglider with 16 fabrics, the stamina wheel and altar, bullet time, shrine terminals and plot islands,
for NeoForge on Minecraft 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1.2, 26.2 and 26.3. See the [README](README.md) and
the [player guide](docs/GUIDE.md) for what everything does.
