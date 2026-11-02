# Contributing

Thanks for wanting to help. Small, focused changes are easiest to review: one fix or one feature per pull request.

## Getting set up

You need a JDK 21. Run `./gradlew runClient` for a development client, and `./gradlew runGameTestServer` for the headless tests
(close the client first, and keep client-only mods such as Sodium out of `run/mods`). [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)
describes hot swapping code into a running client and the screenshot client.

## How the code is laid out

All code is under `src/main/java/com/botwclimb`:

| Folder | What lives there |
|---|---|
| `common/` | Logic shared by client and server: climbing (`climb`), the glider and its fabrics (`paraglider`), the altar (`altar`), bullet time (`bullettime`), plots (`shrine`). The server is the authority for anything that matters. |
| `client/` | Input, camera, animation, screens, rendering and effects. Never referenced from `common/` except through a client-only call site. |
| `network/` | One class per network message, registered in `NetworkHandler`. A client and server must run the same jar. |
| `mixin/` | The few mixins, listed in `botwclimb.mixins.json`. |
| `dev/` | Game tests and the screenshot client. They are not meant to ship behaviour. |

Assets and data live in `src/main/resources` under the `botwclimb` namespace. Scripts that generate textures are in `tools/`.

## Before you open a pull request

* The game tests pass (`./gradlew runGameTestServer`), and a new behaviour has a test where one is practical.
* The change is described: what it fixes or adds and how you checked it in the game.
* Code reads like the code around it: same naming, short comments that say why, no leftover debug output.
* Anything that changes a network message, a registered block or item, or saved data says so, because it needs a matching jar on the server.

## Reporting bugs

Use the issue templates. The most useful reports include the mod and NeoForge versions, exact steps and `logs/latest.log`.
