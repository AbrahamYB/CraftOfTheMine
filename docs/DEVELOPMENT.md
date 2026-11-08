# Development

## Gradle tasks

* `gradlew :V:build` (V = a version such as 1.21.1; plain `build` does every version) - jar in `versions/V/build/libs`
* `gradlew :V:runClient` / `:V:runServer` - dev run (add `-Dbotwclimb.debug=true` to draw jump paths and log state changes)
* `gradlew :V:runGameTestServer` - headless game tests (close the client first; client-only mods such as Sodium must not be in `run/mods`)
* `gradlew deploy` - copies the built jar of the version named by `prismVersion` (default 1.21.1) into the launcher mods folder named by `prismMods` in `local.properties` (also runs after `build`)
* `gradlew :V:runAutoClient` (add `-Pwall` for the wall check) - a second client that plays a scripted scene and saves screenshots to `run-auto/screenshots`
* Optional `local.properties` (not in git): `quickWorld=<save folder>` boots straight into that world, `window=1280x720` sets the window size.

## Updating the running dev client without a restart

`gradlew runClient` opens a debug port (5005). After a code change, `gradlew hotswap` compiles and pushes the changed classes into the
running client (`tools/HotSwap.java`). On a normal JVM only method bodies swap; on the JetBrains Runtime (set `jbr=<its folder>` in
`local.properties` and `org.gradle.java.installations.paths=<that folder>` in `~/.gradle/gradle.properties`) a swap may also add and
remove methods and fields. Registrations (new blocks, items, particles, network messages) and brand-new classes' registrations still
need a restart (the tool says so). Changed textures, models and lang files: press F3+T in the game.

The manual test plan is in [TESTING.md](../TESTING.md); the automated checks are the headless game tests under `src/main/java/com/botwclimb/dev`.
