# Craft of the Mine (CotM)

Craft of the Mine brings Breath-of-the-Wild-style movement to Minecraft: you climb walls, jump from ledge to ledge, glide on a
paraglider and manage a stamina wheel. It also adds an altar for growing that wheel, bullet time for bows and crossbows, and personal
plots reached through shrine terminals. It is one small jar per Minecraft version for NeoForge, with no other mods needed.

This is an unofficial fan project. It is not affiliated with or endorsed by Nintendo or Mojang.

## What it adds

* **Wall climbing.** Hold the climb key next to a wall, climb in any direction, hop between holds, jump over ledges, turn corners.
* **Paraglider.** Open it in the air, steer with the camera, and learn new fabrics by crafting them in a menu beside your inventory.
* **Stamina.** A wheel drawn on screen that climbing and gliding spend, kept honest by the server.
* **Stamina altar.** Offer valuables to grow the wheel, up to three full rings.
* **Bullet time.** A slow fall and slowed mobs while you aim a bow or crossbow in the air.
* **Shrine terminals and plots.** Claim a plot island and travel there and back with a short ride.

The full description of every feature and its numbers is in the [player guide](docs/GUIDE.md).

## Downloads

Download the jar for your Minecraft version from the [Releases page](../../releases/latest). Each release lists what changed (the full
history is in the [changelog](CHANGELOG.md)) and a SHA-256 checksum of every jar. You can also build the jars yourself (see below).

| Minecraft | Jar | Java |
|-----------|-----|------|
| 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11 | `CraftOfTheMine-1.0.0+mc<version>.jar` | 21 |
| 26.1.x, 26.2, 26.3 | `CraftOfTheMine-1.0.0+mc<version>.jar` | 25 |

Only these stable versions are built. Pick the jar that matches your game exactly.

## Installation

1. Install NeoForge for your Minecraft version.
2. Put the matching jar in your `mods` folder.
3. On a server, put the **same** jar on the server too: the mod uses its own network messages, and a client and server with
   different versions refuse each other's connection.

No other mods are needed. Worlds keep the mod's data (stamina upgrades, fabrics, altar credit) when moved between versions, but
Minecraft itself does not support loading a world on an older version, so back up before you downgrade.

## Credits

Made by AbrahamYB, with Claude (Anthropic's AI assistant) writing much of the code.

* **AbrahamYB** designed the mod and tuned how it feels: how climbing, ledge jumps, gliding and bullet time should move, which ideas
  stay and which go, the look of the fabrics, altar and ride, and all the playtesting that decided what was right.
* **Claude** wrote and ported the code (one source tree for ten Minecraft versions), built the automated tests and screenshot
  harness, and ran the security reviews of the server checks.

Every behaviour in the mod was chosen and checked by playing it, not just generated.

### How it was tuned

The numbers were found by playing, one change at a time (the commit history shows the steps):

* Falls went from vanilla to 20% slower, then 35%, and finally back to vanilla gravity with only bullet time slowing a fall.
* The glider shortcut opens after a 2-block fall, a plain jump only after 4.5 blocks; a wall grab needs the wall within 0.30 blocks.
* Glider ribbons, tassels and wind trails were redone several times until they followed the real speed instead of looking busy.
* Bullet time first faked the slowdown on the client; it was rebuilt so the server really slows mobs (1 tick in 7, within 8 chunks).
* A wall marker, a permanent slow-fall and the shift-sprint climb were tried and removed.

## Getting help and reporting issues

If something breaks or behaves oddly, please open an issue on the [issue tracker](../../issues). The templates ask for what is
usually needed: the mod version, the NeoForge version, what you did, what you expected and the log (`logs/latest.log`). Feature ideas
are welcome there too.

## Building from sources

The project uses Gradle and Stonecutter (one source tree, ten versions). It needs a JDK 21, and a JDK 25 for the 26.x versions.

```
./gradlew build              # every version; jars are in versions/<version>/build/libs
./gradlew :1.21.1:build      # one version
./gradlew runClient          # a development client
./gradlew runGameTestServer  # the headless game tests
```

On Windows use `gradlew.bat`. More about the development setup (hot swap, deploy, the screenshot client) is in
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Contributing

Bug reports and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for how the code is organised and what is
expected of a change.

## License

Copyright (c) 2026 AbrahamYB. All rights reserved. See [LICENSE.md](LICENSE.md).
