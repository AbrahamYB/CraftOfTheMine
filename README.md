# BOTW Climb (NeoForge 1.21.1)

Breath-of-the-Wild style wall climbing and nothing else: hold the Climb key (default Shift + Space, rebindable, combos allowed) for 0.6 s next to any solid wall to climb it, shuffle sideways, and press Space for a
climb jump that can swing round a ~1-block lip. No stamina, no HUD, no dependencies.
Sneak or sprint lets go. See `TESTING.md` for the test plan.

* `gradlew build` – jar in `build/libs`
* `gradlew runClient` / `runServer` – dev run (add `-Dbotwclimb.debug=true` to draw jump paths)
* `gradlew runGameTestServer` – headless jump-planner geometry tests
* Block tag `botwclimb:non_climbable` lets blocks opt out.
