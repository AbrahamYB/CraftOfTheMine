# Craft of the Mine (CotM)

NeoForge mod (id `botwclimb`, never change it), built for several Minecraft versions from one source tree with Stonecutter.

Read [docs/HANDOFF.md](docs/HANDOFF.md) first: project state, how to build/test/run each version, the multi-version design, decisions
and their reasons, open items, and how this user likes to work. Player-facing description: [docs/GUIDE.md](docs/GUIDE.md).

Quick rules:
* Work on the `main` branch. Tasks are per version, for example `gradlew.bat :1.21.1:build`, `:1.21.4:runGameTestServer`.
* Version differences go through `common/Compat.java` and `client/ClientCompat.java` or Stonecutter `//? if` comments; keep the
  active (1.21.1) state compiling after every change and run the game tests on every version you touched.
* Deploy the 1.21.1 jar to Prism after a change (`:1.21.1:build`). Never press real keys from a harness. Do not publish, force-push
  or delete anything without the user's explicit yes.
