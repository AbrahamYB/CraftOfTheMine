# Craft of the Mine (CotM)

Craft of the Mine brings Breath-of-the-Wild-style movement to Minecraft: you climb walls, jump from ledge to ledge, glide on a
paraglider and manage a stamina wheel. It also adds an altar for growing that wheel, bullet time for bows and crossbows, and personal
plots reached through shrine terminals. It is one small jar for NeoForge, with no other mods needed.

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

There are no published releases yet. Until there are, build the jar yourself (see below). Releases will be listed on this
repository's Releases page, and every change is described in the [changelog](CHANGELOG.md).

## Installation

1. Install NeoForge 21.1.x for Minecraft 1.21.1 (the mod was developed on 21.1.259 and is built to load on any 21.1 release).
2. Put the jar in your `mods` folder.
3. On a server, put the **same** jar on the server too: the mod uses its own network messages, and a client and server with
   different versions refuse each other's connection.

Java 21 is required, the same as Minecraft 1.21.1.

## Getting help and reporting issues

If something breaks or behaves oddly, please open an issue on the [issue tracker](../../issues). The templates ask for what is
usually needed: the mod version, the NeoForge version, what you did, what you expected and the log (`logs/latest.log`). Feature ideas
are welcome there too.

## Building from sources

The project uses Gradle and needs a JDK 21.

```
./gradlew build              # the jar is written to build/libs
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
