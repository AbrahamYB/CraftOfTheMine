# Handoff: Craft of the Mine (CotM)

Current state for whoever picks the project up (a person or another Claude session): what it is, how to build and test it, the design
choices and their reasons, and what is open. Feature numbers are in [GUIDE.md](GUIDE.md); dev setup is in [DEVELOPMENT.md](DEVELOPMENT.md).

## 1. What this is

A NeoForge mod, display name **Craft of the Mine (CotM)**, mod id **`botwclimb`** (never change it: worlds, config, data paths and saved
attachments are keyed on it). Breath-of-the-Wild-style movement: wall climbing and climb/ledge jumps with a stamina wheel, a paraglider
with fabrics learned by crafting, the stamina altar, bullet time for bows and crossbows, shrine terminals with per-player plot islands in
their own dimension, a settings screen, `/cotm` admin commands and a Climb Speed enchantment.

Owner: AbrahamYB (`AbrahamYB/CraftOfTheMine`, public, "All rights reserved" placeholder license). They play in a **Prism Launcher**
instance on NeoForge 21.1.235, Minecraft 1.21.1.

## 2. Repository

`main` is the only branch. One source tree builds ten Minecraft versions with Stonecutter 0.9.8: **1.21.1, 1.21.3, 1.21.4, 1.21.5,
1.21.8, 1.21.10, 1.21.11, 26.1, 26.2, 26.3** (versions with only beta NeoForge builds are skipped). **One jar per version, except 1.21.3 and
1.21.4, whose classes compile to identical bytecode: the 1.21.4 project ships `mc1.21.3-1.21.4` (`jar_label`, widened ranges) and the 1.21.3
project is only built and tested.** To merge more versions, compare the jars (same classes and same resources apart from `mods.toml`); the
other neighbours differ in 12 to 143 classes because of renamed Minecraft classes, so they need those classes made version-neutral first. The newest GitHub release (see the Releases page) has
one jar per version. Not started: 1.20.x (old Forge-style NeoForge, a rewrite rather than a port).

Local only, never commit: `local.properties`, `versions/*/run*`, `run*/`, `build/`, `release/` (built jars), `tools/memwatch-out/`, and
`.github/workflows/build.yml` (GitHub refuses it: the login lacks the `workflow` scope; `gh auth refresh -h github.com -s workflow` fixes
that). `concept/sheet_*.png` (possible game footage) went into the public repo by mistake on Nov 8; do not add more. The repo is public:
check assets before adding any.

## 3. Build, test, run

Run from the project root with `gradlew.bat`. Every task is per version (`:1.21.1:...`, `:26.3:...`; the list is in `settings.gradle`).

* `:V:build` builds `versions/V/build/libs/CraftOfTheMine-<mod version>+mcV.jar` and copies it into the Prism mods folder, but only when `V`
  equals `prismVersion` in `local.properties` (default 1.21.1). Deploy to Prism after every change.
* `:V:runGameTestServer` runs the headless tests (66 or 67 per version). Close any client first. Run it on every version you touched.
* `:V:runClient` is the dev client (debug port 5005; JetBrains Runtime for hot swap when `jbr=` is set). `gradlew hotswap` pushes code into
  a running client.
* `:V:runAutoClient` (add `-Pwall` for the wall/jump/climb-pose scene) starts a second client in `versions/V/run-auto`, plays a scripted
  scene and writes screenshots to `run-auto/screenshots`. It needs a world copy in `run-auto/saves/AutoTest` and `pauseOnLostFocus:false`
  in its `options.txt`. **Never inject real keyboard input** (a `java.awt.Robot` test once toggled the user's physical Shift): set
  `KeyMapping` state or call controllers inside the game.
* `tools/screenshots_all.ps1 <versions>` runs the harness for several versions in a row; `python tools/shots_sheet.py <version>` makes a
  contact sheet (needs Pillow). `tools/errsym.py <javac output>` lists compile errors by missing symbol.
* Gradle needs `systemProp.javax.net.ssl.trustStoreType=WINDOWS-ROOT` in `~/.gradle/gradle.properties` on this machine; `curl` needs
  `--ssl-no-revoke`.

## 4. Multi-version design

* `settings.gradle` lists the versions; `versions/V/gradle.properties` holds that version's Minecraft, NeoForge, Java and optional
  Parchment values; `build.gradle` is one shared script run per version; `stonecutter.gradle` marks the active version (1.21.1).
* Source is shared in `src/`. Differences use Stonecutter comments written in the 1.21.1 state: `//? if >=1.21.2 {` newer code inside
  `/* ... *///?} else {` older code `//?}`. Block comments do not nest, so never put a `/** */` comment or another conditional inside a
  commented-out branch, and never a trailing comment on a `//?` line.
* An `@EventBusSubscriber` class with no `@SubscribeEvent` method **crashes mod loading** (NeoForge 21.5+; the headless tests cannot see
  it, only a client start does). Make the annotation itself conditional for version-only subscribers.
* Token replacements for renamed classes and methods live in `build.gradle` (`replacements { string { ... } }`, one group per Minecraft
  generation). They are applied to the original text at once, not one after another, so a rule's output must already contain every other
  rename.
* Differences that need real code sit behind helpers so call sites stay clean: `common/Compat.java` (teleport, registries, inventory,
  permissions, attachment codecs, names, spawn, arrow damage, push reaction, overlay messages), `client/ClientCompat.java` (partial tick,
  input, blit, tooltips, packets, keys, screenshots, render layers), `client/RenderData` and `RenderStateSetup` (living entity on the
  render state from 1.21.2), `client/GuiShapes` (wheel triangle strips), `common/Nbt` (NBT reads/writes changed in 1.21.5),
  `client/Buffers` (stands in for `MultiBufferSource`, removed in 26.2), `common/PlayerStateSync` + `network/PlayerStatePayload` (replace
  NeoForge's attachment sync, removed in 21.4; run on every version).
* Per-version files that replace shared ones: `versions/V/resources/` (recipe and item model formats, access transformers; 26.x plot
  dimension type with static attributes instead of `fixed_time`). The 1.21.5+ test framework has no `@GameTest`; `dev/GameTest` and
  `dev/TestRegistration` re-create it from 1.21.11.
* Attachments are saved as `{value: x}` on every version (`Compat.attach`); older bare values still load. So a world moves up a version
  without losing stamina upgrades, fabrics or altar credit. Minecraft cannot load a world on an older version.
* Known per-version gaps: ride motion blur is off from 1.21.2 (post-effect format changed); from 1.21.6 the ride's void is drawn as GUI
  quads with the helix projected by hand (`TotkRide.Flat`) and covers the hotbar; from 1.21.2 vanilla's `eyes` render type blends instead
  of adding, so the ride glow uses the additive lightning type (`TotkRide.glow()`).
* Verified: all ten versions compile, pass their game tests, and were run through the screenshot harness. Not checked: real multiplayer
  and a real Prism install on anything but 1.21.1.

## 5. Decisions and reasons

* **Bullet time is slowed on the server, not faked on the client.** `common/bullettime/BulletSlow` cancels `EntityTickEvent.Pre` for
  mobs within 128 blocks of a shooter on every tick but 1 in 7, keeps hurt timers on the real clock and sends the slowed ids to clients,
  where `client/bullettime/BulletTime` only glides the picture. `SlowedPathMixin` stretches the path timeout. Players cannot have ticks
  skipped, so `BulletSlow.applyToPlayer` puts transient attribute modifiers on them; creative, spectator, riders and the shooter are
  exempt, and so are Create contraptions and similar namespaces (`BulletSlow.eligible`). From 26.3 `Entity.tickCount` is advanced inside
  the skipped tick, so `BulletSlow` advances it itself. The idea was studied from the Awakened mod (All Rights Reserved): nothing copied,
  no dependency, never bundle its jars. The old client-side "lag picture" (drawing other players late and rewinding hits) was removed in 1.2; git history has it.
* **Shift + Space is both the climb key and the glider shortcut.** NeoForge gives a key press only to bindings matching the held
  modifiers, so `ClientClimbController` hands the jump back to vanilla when a combo binding is down. A wall grab needs the wall within
  0.30 blocks and 60 degrees of the facing direction; the glider shortcut needs a fall of 2 blocks, a plain jump 4.5.
* Gravity is vanilla; only bullet time slows a fall. The altar is ender-chest strength (any pickaxe, drops itself) and its glow is baked
  into small textures (per-cell quads lagged mining). The wall "blob" marker was removed at the user's request: do not re-add. Admin
  commands are `/cotm` (no `/botwclimb` alias).
* The repo history was rebuilt once (ten commits dated by file edit day, force-pushed with the user's explicit approval). Do not fake
  dates.

## 6. Security model

The server never takes a client claim at face value; every client-to-server payload has its checks re-derived on the server.

* Bullet time: `BulletSlow.accept` checks the world itself (`reallyInTheAir`: falling per the server's fall counter, nothing solid within
  0.2 under the feet, not riding, swimming or on a ladder) and keeps a budget (`SERVER_BUDGET` 400 ticks, `SERVER_REFILL` 0.5 per resting
  tick, charged by the ticks each heartbeat buys). The shooter's slow fall (`BulletFall`) uses the same check. Client-reported lag is
  ignored and no positions are sent.
* All server-to-client sends go through `common/Net` (`toPlayer`, `toWatchers`), which skips connections without the channel: the
  payloads are optional so a player without the mod can join, and NeoForge throws on sending an unknown payload.
* Climb: `ServerClimb` is a state machine (NONE, CLIMBING, CLIMB_JUMP, LEDGE_TRANSITION) with grace and bad-tick counters; states cannot
  be chained in open air; server stamina regenerates only on the ground. A glide closes when the player sinks faster than 1 block per tick.
* Altar offers re-check slot, count, item, distance and block; refunds are capped at 2304 and reject NaN. Commands need game-master
  permission (`/cotm`, `/cotm cloth`), except `/plot return`, which only works inside the plot dimension.
* Plots: saved data survives a broken entry, plot numbers are unique and in range, at most `MAX_PLOTS` 2000, an island is built once
  (`built` flag), plots close to friends when the owner logs out and on server start, other players' entities are protected, falling out
  of the world returns the player, Create fake players count as the owner, no rides while mounted or carrying. The terminal cannot be
  broken, blown up or pushed (`Compat.IMMOVABLE`); the ride ends on respawn.
* By design or left to vanilla: plot guests can build in a plot the owner opened to friends; a terminal ride can leave a fight; movement
  is client-authoritative as in vanilla (fly and speed cheats are vanilla anti-cheat's job); a client without NeoForge cannot join a
  1.21.1 server.

## 7. Open items

1. **Climb checks (done in 1.2, only unit-tested).** `ServerClimb` caps wall speed (0.7 blocks/tick for 4 ticks in a row), ends a jump that
   stays outside its reach for 8 ticks, and starts a jump or ledge only from a wall held within the last 10 ticks, so a slow connection
   does not rubber-band. Not yet checked: a real laggy connection (try it with a network limiter), and whether the thresholds ever trip
   on a legitimate climb (watch `latest.log` for "let go").
2. **Bullet time cost.** The scan cost grows per shooter (no cap on shooters), there is no setting to leave other players alone, and pets
   have no ownership check.
3. **Plots.** Item pickup and farmland trampling are not guarded.
4. **Small:** the altar screen rebuilds its refund list every frame; `TotkSpin` can leave its pose unbalanced if another mod cancels the
   render event; the client uses `TerminalRidePayload` values unchecked; the server stamina account resets on relog; the cosmetic mixins
   (`SoundEngineMixin`, `WorldDimensionsMixin`, `PlayerModelMixin`) can stop startup on a renamed target (`defaultRequire: 1`); `/plot` is
   a generic command name.
5. **Never tested live:** a second real player, an old-format save, a real Prism install on versions other than 1.21.1.
6. **Duplicate dive animations.** The user once reported duplicated dive poses in the ride; the harness shows one rider each way and the
   cause was not found. Ask for a screenshot and the Prism build. Code: `client/shrine/totk/TotkRide.java`, `MudHandLayer` (the ride's render layer, the name is a leftover), `TotkSpin`.
7. Widen the version ranges in `versions/V/gradle.properties` only after testing that exact version. Decide on a license.

## 8. Working with this user

* Short, honest answers; name what was not verified. Minimal changes. Do the work, do not ask, except before anything outward-facing that
  cannot be undone (publishing, force-pushing, deleting): that needs an explicit yes.
* Deploy the jar to Prism after changes; use hot swap instead of restarting the dev client where possible.
* They test by playing; back visual claims with harness screenshots. The harness cannot press the real Shift + Space.
* Release naming: tag `v<mod version>` (old-Minecraft style numbers: 1.1, 1.2, ...; the first release used `mc1.21.1-1.0.0`); **bump `mod_version` in `gradle.properties` for every release, never re-release a number**, jar `CraftOfTheMine-<ver>+mc<mc>.jar`, SHA-256 of every
  jar in the notes.
* Memory notes: `C:\Users\suchy\.claude\projects\C--Users-suchy-Documents-Claude-BOTW-MC-MOD\memory\`.
