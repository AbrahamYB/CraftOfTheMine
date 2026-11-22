# Changelog

All notable changes to Craft of the Mine (CotM). The mod id stays `botwclimb`, so saved worlds, the config file and data pack paths
keep working across the rename. Versions are numbered like old Minecraft: 1.0, 1.1, 1.2, and so on. Use the same version on a server
and on every client that joins it.

## 1.2

The guide update. It adds a guide inside the game, gives the altar a new look and new values, and rebuilds the terminal ride.
**This version is only for Minecraft 1.21.1 so far;** the other versions follow in a later release.

### Added
* **A guide inside the game, modelled on Create's Ponder.** The idea and the layout come from Ponder, the guide in the Create mod
  (by the Create Team): a small scene built on a plate, blocks dropping in, notes with a line to what they point at, a timeline with
  marks, and an "Up next" box at the end. The look and all of the code are our own; nothing from Create is copied or included. There
  are three lessons:
  * **The altar.** What it is and what an upgrade adds to your Stamina Bar. Then the altar is right-clicked and a table, "What
    offerings are worth", pops up. Its items are thrown onto the altar one by one, most valuable first, and the Stamina Bar fills by
    each item's real value until it is full; whatever is left stays in the list. Under the list are the points each of the 8 upgrades
    costs. Two notes at the end explain what tools and armor are worth.
  * **The shrine terminal.** Picking your plot or a friend's, and the ride up.
  * **Your plot.** The island only you and your friends can build on, and how to get back down.

  The buttons are back, replay, pause, close and next; click the timeline to jump, and drag the scene to turn it. Open the guide from
  the mod menu, or hover the altar or terminal item in your inventory and hold W for a moment (its tooltip says so).
* **A mod menu button, after the one in Create.** Create puts a goggles button beside the pause menu's buttons; this mod now has one
  too, a small Stamina Bar icon to the right of "Report Bugs" on the pause menu and "Multiplayer" on the title screen. It opens the
  mod's own menu: Configure, Guide, Report Issues (opens this project's GitHub issues page, after asking) and Return to Menu. The menu
  credits Create.
* **A friends list on the terminal.** The terminal shows everyone online whose plot is open to friends, with their skin's face, and
  takes you to theirs with one click. On your own plot a switch opens it to friends or closes it again.
* **Sound effects for the terminal ride,** made from scratch: the terminal opening, the light spiralling up, the rush through the
  void, the jump and the landing, all with subtitles. The ride's sound keeps playing when the ride moves you to another world, where
  the game would normally cut every sound off.
* **A "Moving outline" setting.** The writing on the plot outline drifts slowly around the plot; turn this off to keep it still.

### Changed
* **The altar is a low stand.** The full-height dark block looked heavy and out of place next to the terminal. It is now half a block
  tall: a base with a raised plate on top, in worn grey silver, with a carved ring on each side that lights up with your first
  upgrade. Clicking and walking into it match the new shape.
* **The altar's rings fill from the inside out:** the inner ring for upgrades 1 to 4, then the outer ring for 5 to 8, the same order
  as the Stamina Bar. The altar in your hand or inventory shows your progress too.
* **The altar stays green when you finish.** Buying the last upgrade used to turn its lights off for 5 seconds and back on in cyan.
  That is gone; it simply stays green. The server and every client need this version, because the message that announced the last
  upgrade was removed.
* **Some offerings are worth a different amount:** Elytra 40 → 20, Dragon Egg 60 → 45, Trident 6 → 8, Amethyst Shard 0.04 → 0.5.
  A data pack can still change any value.
* **Enchanted tridents are worth more.** A level IV or V enchantment already added 2 points to tools and armor; now it does for a
  trident too (Impaling IV or V). An enchanted book is still worth nothing, so a single book cannot be offered for points.
* **The terminal always looks switched on.** It used to sit dark and light up only for a ride; the dark look and the code for it are
  gone.
* **The terminal and altar menus are frosted glass.** The solid black panels hid the world behind them. They are now see-through
  smoked glass with a thin silver edge and translucent green buttons, over the game's blurred background. The text in the altar's
  item list has a shadow, so it stays readable over the glass.
* **One plot per player.** Each player used to get two. In a world from before, every player keeps their first plot; the second
  belongs to no one now, and its number is never handed out again.
* **The plot outline looks like the terminal:** a silver border with a dark teal inlay and green marks that drift along it like
  writing, with a silver tile at each corner. It is drawn after the clouds, so they never cut through it.
* **The terminal ride is rebuilt after Tears of the Kingdom,** worked out frame by frame from footage of the real travel.
  * Going up, you float up through spiralling ribbons, stop as if you hit something, and the view cuts to the void as you climb.
    At the plot you crouch in a ripple of light, spring up and land.
  * Coming home, a marker of wavering cyan rings appears around you, you sink to the waist, turn once and are pulled under, fall
    through the void turning, and land with a glow and green slashes rising around you.
  * For these poses the player model now has elbows and knees, and leggings and boots bend with them. Outside the ride the arms and
    legs stay straight and look exactly as before.
* **The "stamina wheel" is now called the Stamina Bar,** in the game and in the docs.
* **The altar casts soft shadows** on the ground around it, like the terminal (below). A side over a drop or a hole gets no shadow,
  so none hangs in the air.
* **The shrine terminal casts shadows.** Darker textures alone left it looking flat and unlike the world around it. It now has a soft
  shadow on the ground around its base, a shadow on the base's top where the floating top overhangs, a gradient down its pillar just
  under the top, and darker sides toward the ground. These are drawn as part of the block's model, so they work on every version.

### Fixed
* **No fall sound when a ride lets go.** The ride carries you down without gravity, but the game still counted it as a fall and
  played the thud at the end.
* **No camera jumps during a ride.** The view now eases out to third person and back the way it does for climbing, starts just behind
  your head rather than inside it, and holds still when the ride moves you to another world.
* **The ride waits for the world to load** before dropping you home. On a slow computer you could land before the world was there.
* **The rider faces where they look.** The game's own body turn lagged up to 50 degrees behind the view and showed the rider
  sideways; the body now comes round after the view and settles facing it.
* **The rider no longer flickers into a crouch.** Inside the ground the game picked a crouch or a crawl each tick, which made the
  rider jump for a frame.
* **The void shows through the hotbar.** Its see-through slots showed the world behind the void instead of the void.

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
