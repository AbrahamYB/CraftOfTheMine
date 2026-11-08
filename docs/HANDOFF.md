# Handoff: Craft of the Mine (CotM)

A summary of the whole working session so far (Oct 16 to Nov 3, 2026), for whoever picks the project up next (a person or another
Claude session). It records what the project is, how it is built and tested, what was decided and why, what is done, and what is open.
Numbers and behaviours of the features themselves are in [GUIDE.md](GUIDE.md); the dev setup is in [DEVELOPMENT.md](DEVELOPMENT.md).

## 1. What this is

A NeoForge mod, display name **Craft of the Mine (CotM)**, mod id **`botwclimb`** (never change the id: worlds, config, data paths and
saved attachments are keyed on it). Breath-of-the-Wild-style movement in Minecraft:

* wall climbing, climb jumps, ledge and long ledge jumps, corner slides, a stamina cost and the stamina wheel HUD;
* a paraglider with fabrics that are learned by crafting in a menu beside the inventory (no cloth item, learn on craft);
* the stamina altar (block, offering screen, glowing rings and runes, Sheikah glyph particles);
* bullet time for bows and crossbows (slow fall; mobs near the shooter really run at about 1 tick in 11 on the server);
* shrine terminals and per-player plot islands in their own dimension, reached by a mud-cocoon ride or a Tears of the Kingdom ride;
* a settings screen, `/cotm` admin commands, a Climb Speed enchantment.

Owner: AbrahamYB on GitHub (`AbrahamYB/CraftOfTheMine`, public, "All rights reserved" placeholder license). They play and test in a
**Prism Launcher** instance on **NeoForge 21.1.235, Minecraft 1.21.1**. They want short, honest answers and things done, not asked.

## 2. Repository state

| Branch | Layout | State |
|---|---|---|
| `main` | single project, 1.21.1 only | what the GitHub release `mc1.21.1-1.0.0` was built from; last commit README downloads link |
| `stonecutter` | one source tree built for several Minecraft versions | everything below under "Multi-version"; **not merged**, pushed to origin |

Do day-to-day work on `stonecutter` (it still builds 1.21.1 exactly like `main`). Merge it to `main` only after the user agrees.

Local only (never commit): `local.properties` (quickWorld, window, `prismMods`, `jbr`, optional `prismVersion`), `versions/*/run*`,
`run*/`, `build/`, `concept/sheet_*.png` (possible game-footage frames, left out of the public repo on purpose), and
`.github/workflows/build.yml` (GitHub refused it: the login lacks the `workflow` scope; `gh auth refresh -h github.com -s workflow` fixes that).

## 3. How to build, test, run

Run from the project root with `gradlew.bat` (Windows). On the `stonecutter` branch every task is per version: `:1.21.1:...`,
`:1.21.4:...`, `:1.21.11:...`, `:26.1:...`; the versions are listed in `settings.gradle`.

* `:V:build` builds `versions/V/build/libs/CraftOfTheMine-1.0.0+mcV.jar` and, via `deploy`, copies it into the Prism mods folder, but
  only when `V` equals `prismVersion` in `local.properties` (default 1.21.1). Standing preference: **deploy to Prism after every change**
  (saved in memory as `feedback_always_deploy_prism`).
* `:V:runGameTestServer` runs the headless tests (63 on 1.21.1, 1.21.3 and 1.21.4, 64 on every later version). Close any client first, and keep client-only
  mods (Sodium and the like) out of `run/mods` on the old single-project layout.
* `:V:runClient` is the dev client (debug port 5005, JetBrains Runtime for hot swap when `jbr=` is set). `gradlew hotswap` pushes code
  into a running client (method bodies; JBR also allows adding methods).
* `:V:runAutoClient` (add `-Pwall` for the wall/jump/climb-pose scene) starts a second client in `versions/V/run-auto`, plays a scripted
  scene and writes screenshots to `run-auto/screenshots`. It needs a world copy in `run-auto/saves/AutoTest` and
  `pauseOnLostFocus:false` in its `options.txt` (otherwise it freezes whenever its window loses focus). Screenshots are taken after the
  whole frame (`RenderFrameEvent.Post`). **Never inject real keyboard input** (a `java.awt.Robot` experiment toggled the user's physical
  Shift; saved as memory `feedback_no_physical_key_tests`). Set `KeyMapping` state or call controllers inside the game instead.
* `tools/errsym.py <javac output>` lists compile errors with the missing symbol; handy when porting.
* `tools/screenshots_all.ps1 <versions>` runs the harness for several versions in a row (one at a time; no other Gradle task meanwhile) and
  `python tools/shots_sheet.py <version>` makes a contact sheet of the key frames (needs Pillow). The harness joins `run-auto/saves/AutoTest`
  by itself: `dev/AutoConfirm` answers the "back up this world?" screens that 26.x shows for a world saved by an older version.
* Gradle needs `systemProp.javax.net.ssl.trustStoreType=WINDOWS-ROOT` in `~/.gradle/gradle.properties` on this machine (corporate-style
  certificate revocation failures otherwise); `curl` needs `--ssl-no-revoke`.

## 4. Multi-version design (Stonecutter 0.9.8, Groovy scripts)

Goal from the user: every Minecraft version after 1.21.1 that has a stable NeoForge build, each "working perfectly" (no beta versions, no
minor versions the neighbouring build already covers). Built so far (Nov 7): **1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11,
26.1, 26.2, 26.3** (1.21.2, 1.21.6, 1.21.7 and 1.21.9 have only beta NeoForge builds, so they are skipped).

How it works:

* `settings.gradle` lists the versions; `versions/V/gradle.properties` holds that version's Minecraft, NeoForge, Java and (optional)
  Parchment values; `build.gradle` is one shared script that runs per version; `stonecutter.gradle` marks the active version.
* Source is shared in `src/`. Version differences use Stonecutter comments, written in the 1.21.1 ("active") state:
  `//? if >=1.21.2 {` newer code inside `/* ... *///?} else {` older code `//?}`. Never put a `/** */` comment or another conditional
  inside a commented-out branch (block comments do not nest). For a version-only subscriber class, make the annotation itself
  conditional: an `@EventBusSubscriber` class with no `@SubscribeEvent` method **crashes mod loading** (this broke the 1.21.1 jar
  between 9:55 and 11:26 on Nov 2; fixed and redeployed).
* Global token replacements live in `build.gradle` (`replacements { string { ... } }`, one group per Minecraft generation, each with its
  own `direction = eval(current.version, ...)`): >=1.21.5 the test annotations and `GameTestHelper.fail` (1.21.5 to 1.21.8 take a
  Component); >=1.21.6 `g.renderTooltip(` to `g.setTooltipForNextFrame(`; >=1.21.9 `PlayerRenderState` to `AvatarRenderState`, the
  particle base class, `ReceivingLevelScreen`, `PlayerSkin`; 1.21.9 to 1.21.10 `g.renderOutline(` to `g.submitOutline(`; >=1.21.11
  `ResourceLocation` to `Identifier`, moved classes, `RenderType.x(` to `RenderTypes.x(`, `.location()` to `.identifier()`; >=26.1 the
  GUI class (`GuiGraphics` to `GuiGraphicsExtractor`), its method names, `LightTexture`, screen method names, `BlockEvent.BreakEvent`;
  >=26.2 items (`Items.WHITE_WOOL` to `Items.WOOL.white()`), `EntityType.COW` to `EntityTypes.COW`, `mc.screen`/`setScreen` moved to
  `mc.gui`; >=26.3 the GLFW to SDL key constants (see `ClientCompat.KEY_*`), pose stack rotation, container screen fields.
  Replacements are applied to the original text at once, not one after another, so a rule's output must already contain every other
  rename (see the `extractRenderState(GuiGraphicsExtractor ...` rules). Never put a trailing comment on a `//? if` line.
* Differences that need real code sit behind two helper classes so call sites stay clean: `common/Compat.java` (teleport, registries,
  holders, inventory items, permissions, attachment codecs, names, spawn, arrow damage) and `client/ClientCompat.java` (partial tick,
  movement input flags, blit/sprite, pose helpers, tooltips, packets to the server, window handle, keys, screenshots, outer model
  layers, level-render events). `client/RenderData.java` and `client/RenderStateSetup.java` attach the living entity (and the HUD-rider
  mark) to the render state from 1.21.2 on. `client/GuiShapes.java` draws the wheel's triangle strips (immediately before 1.21.6, as a
  submitted GUI element after). `common/Nbt.java` wraps the NBT reads/writes that changed in 1.21.5. `client/Buffers` stands in for the `MultiBufferSource` that 26.2 removed;
  from 26.2 the wind trails and plot outline are submitted through `SubmitCustomGeometryEvent`. `common/PlayerStateSync.java` +
  `network/PlayerStatePayload` replace NeoForge's attachment sync (removed in NeoForge 21.4); they run on every version.
* Per-version files that replace shared ones of the same path: `versions/V/resources/` (recipe and item model formats from 1.21.4;
  access transformers; `META-INF/accesstransformer.cfg` for 1.21.11 adds `ClientInput.moveVector`, `AbstractArrow.baseDamage`,
  `GuiGraphics.guiRenderState`).
* Tests: the 1.21.5+ test framework has no `@GameTest`; `dev/GameTest`, `GameTestHolder`, `PrefixGameTestTemplate` and
  `dev/TestRegistration` re-create it (reflection over the annotated methods) from 1.21.11.

Verified (Nov 7): all ten versions compile and pass their game tests, and the screenshot harness (`runAutoClient`, and `-Pwall`) was run
on every one of them and the frames reviewed (climb pose, wall marker, plot outline, terminal and settings screens, the ride's void and
helix). What that found and fixed: the void quads of 1.21.6+ were culled (wrong winding, now ordered in `GuiShapes`), the helix is now
projected by hand into the void on 1.21.6+ (`TotkRide.Flat`), `MotionBlur` crashed mod loading on 1.21.5+ (annotation without a listener).
Not checked: real multiplayer, and a real Prism install on anything but 1.21.1. Worlds saved on 1.21.5 or older lose their stamina
upgrades / unlocks on 1.21.6+ (attachment format), so the harness world shows one wheel there instead of three: not a drawing bug. 1.21.1 jar is deployed to Prism. Note that
NeoForge 21.5 and later refuse an `@EventBusSubscriber` class with no listener (the `MotionBlur` class did this on every version from
1.21.5; the headless tests cannot see it, only a client start does).

Known gaps / decisions on the new versions:

* Motion blur of the ride is off from 1.21.2 (post-effect format changed; its shaders are excluded from those jars).
* From 1.21.6 the HUD "void" is drawn as GUI quads and the 3D helix in it is projected by hand into more GUI quads (`TotkRide.Flat`).
  From 1.21.2 vanilla's `eyes` render type blends with alpha instead of adding, so the ride's glow uses the additive lightning type there
  (`TotkRide.glow()`; `quad` writes each glow quad three times, narrowing, to feather the edges, and `disc` is a fan with a clear rim).
  On 1.21.6+ the void also covers the hotbar, which 1.21.1 leaves visible.
* 26.x plot dimension: the dimension type format changed; `versions/26.x/resources/data/botwclimb/dimension_type/plot.json` pins noon with
  static attributes instead of `fixed_time`.
* 1.21.6 and later save attachments with a different codec shape, so stamina upgrades, fabric unlocks and altar credit from a world made on an
  older version are skipped (logged, not fatal).
* Not started: 1.20.x and 1.21 (1.21 is covered by the 1.21.1 jar once its range is widened). 1.20.1 is old Forge-style NeoForge 47.1, a
  rewrite more than a port; 20.2 to 20.6 were not looked at.
* Release naming (Sodium-style): tag `mc<mc>-<mod version>`, title "Craft of the Mine X for Minecraft Y", jar
  `CraftOfTheMine-<ver>+mc<mc>.jar`. Only `mc1.21.1-1.0.0` exists (jar named `CraftOfTheMine-1.0.0.jar`).

## 5. Decisions and the reasons (so they are not redone)

* **Bullet time slows the mobs on the server, it no longer fakes it on the client.** Before (2026-11-06) the client drew mobs where a slowed
  clock said they were and the server rewound hitboxes to match (backup of that code: `backup/bullettime-lagpicture-2026-11-06/`). It
  could never line the drawn mob up with the real one (hitboxes, melee, catch-up jumps). Now `common/bullettime/BulletSlow` cancels
  `EntityTickEvent.Pre` for mobs near a shooter on every tick that is not theirs (1 in `BulletConstants.PERIOD`, 7) within `BulletConstants.RADIUS` (128 blocks, 8 chunks) of the shooter, keeps hurt timers on
  the real clock, and sends the slowed ids to clients, where `client/bullettime/BulletTime` only glides the picture. `SlowedPathMixin`
  stretches the path timeout. It affects every non-player mob in range; Create contraptions, seats, riders and namespaces create/sable/simulated/aeronautics/offroad are exempt (`BulletSlow.eligible`). Other PLAYERS cannot have their ticks skipped (they move on their own client), so `BulletSlow.applyToPlayer` puts transient attribute modifiers on them (movement, gravity, attack speed, jump, block breaking at 1/PERIOD); creative and spectator players, riders and the shooter are exempt. The older lag-picture system (`BulletShots` + `BulletPositionsPayload`) is kept but unused: the client no longer draws players from a slowed clock, the heartbeat reports lag 0. From 26.3 `Entity.tickCount` is advanced inside the skipped tick, so `BulletSlow` advances it itself. The idea was studied from the Awakened mod (license
  All Rights Reserved: nothing copied, no dependency; its Curios and PlayerAnimationLib are for other abilities).

* **Shift + Space is both the climb key and the glider shortcut.** NeoForge hands a key press only to bindings matching the held
  modifiers, so vanilla jump never saw Shift + Space (bridging and pillaring broke). `ClientClimbController` gives the jump back when a
  combo binding is down; a wall grab needs the wall within 0.30 blocks and within 60 degrees of the facing direction; the glider
  shortcut needs a fall of 2 blocks, a plain jump 4.5.
* Gravity is vanilla (an early permanent slow-fall modifier was removed; the code strips the saved modifier from old players).
* The altar is an ender-chest-strength block (any pickaxe, drops itself); its glow is baked into small textures, one quad per ring
  quarter and rune side (thousands of per-cell quads lagged mining).
* The wall "blob" marker was tried and removed at the user's request. Do not re-add.
* Admin commands moved from `/botwclimb` to `/cotm` (no alias).
* GitHub: contribution graph uses author dates. The repo history was rebuilt as ten commits dated by the day their files were last
  edited (Oct 17 to Oct 31), force-pushed with the user's explicit approval. GitHub's profile may keep counting the replaced commits for
  a while; a Support request is the fix if it does not settle. Do not fake dates.
* Do not publish `concept/sheet_*` or the CI workflow without the user's say-so; the repo is public, so check assets before adding any.

## 5b. Security and abuse audit (Nov 7)

Every client-to-server payload was read and the server checks re-derived. Found and fixed:

* **Bullet time was spoofable.** The client's "I am airborne with a bow" flag was believed, so a modified client could hold bullet time on
  while standing on the ground, for free and for ever, slowing every mob and player within 128 blocks. Now `BulletSlow.accept` checks the
  world itself (`reallyInTheAir`: falling per the server's own fall counter, nothing solid within 0.2 under the feet, not riding/swimming/on a
  ladder) and keeps a server budget (`BulletConstants.SERVER_BUDGET` 400 ticks, `SERVER_REFILL` 0.5 per resting tick). The shooter's slow fall
  (`BulletFall`) uses the same check, so it cannot be used for a super-jump.
* **Hit rewind and position leak.** The client-reported lag (up to 30 s) rewound other players' hitboxes for bullet arrows, and the server sent
  real positions of every player within 144 blocks (invisible ones too) to any client in bullet time. Players are slowed for real now, so
  `BulletShotPayload.lag` is ignored (`BulletShots.lag` is no longer called), arrows are not tagged, and nothing is tracked or sent. The old
  code stays in `BulletShots` only for its tests.
* **Packets to players without the mod.** The payloads are optional (so a player without the mod can join), but NeoForge throws when a payload
  is sent to a connection that does not know it, which would have broken the server tick whenever such a player was near a climber or glider.
  All server-to-client sends go through `common/Net` (`toPlayer`, `toWatchers`), which skips connections without the channel.
* **Fly check.** A validated climb or glide hangs in the air as far as vanilla's fly check can tell (a dedicated server with allow-flight off
  kicks after 80 ticks); both now reset `aboveGroundTickCount` while the server's own checks allow them. Climb fall protection now applies only
  while the wall is really held (it used to last up to 70 ticks after a claim without one).

Checked and fine: altar offers (slot, count, item, distance, block all re-checked; the price search is bounded and runs at most once per
upgrade), refunds (only at the last upgrade, value from the data map, credit zeroed), cloth crafting and fabric picks (index and unlock
checked), terminal actions (distance, block, targets validated in the codec and again), commands (all need game-master permission except
`/plot return`, which only works inside the plot dimension), plot rules (terminal cannot be broken or blown up; `/plot return` is the way out).

Known and left alone (design or vanilla's job): plot guests can build and break in a plot its owner opened to friends; a terminal ride can be
used to leave a fight; movement is client-authoritative as in vanilla, so fly and speed cheats are vanilla's anti-cheat's problem (the mod adds
none); a client without NeoForge cannot join a 1.21.1 server (NeoForge 21.1 syncs our attachments itself); worlds from 1.21.5 or older lose
stamina upgrades on 1.21.6+.

### Second audit round (Nov 7, evening): seven review agents, code reading only

Fixed: bullet-time budget now charges the ticks a heartbeat buys (a heartbeat every 5 ticks no longer refills it) and the shooter must really be falling on every tick; the slow fall uses the same budget; a glide closes when the player plunges faster than 1 block per tick (fall damage kept); server stamina only recovers on the ground; `/cotm` and `/cotm cloth` always need op; terminal block cannot be pushed (`Compat.IMMOVABLE`, renamed in 26.3); ride ends on respawn; altar refund capped at 2304 and rejects NaN; repeat fabric picks and spammed rejected climb claims no longer broadcast; climb states can no longer be chained in open air (no jump -> ledge, no free regrab from a ledge, grace only for a fresh hold); plot data survives a broken entry (skipped, numbers unique and in range), plots capped at `MAX_PLOTS` 2000, island built once (`built` flag, no more clearing a plot after a one-block change), plots close to friends when the owner logs out and on server start, other players' entities in a plot are protected, void fall in a plot returns the player, Create fake players count as the owner, no rides while mounted or carrying; client: terminal list capped at 16, bullet-time position map bounded, stale ride entries expire.

Still open (found, not fixed): climb jump/ledge entry is not validated against the world and the jump envelope only logs (see ServerClimb: needs the entry checks and a hard envelope, tested against recorded legitimate traces); wall climb speed is not capped; bullet-time scan cost per shooter (no cap on shooters, no setting to leave other players alone); item pickup and farmland in plots; altar screen rebuilds its refund list every frame; TotkSpin pose can be left unbalanced if another mod cancels the render event; `TerminalRidePayload` values are used unchecked by the client; stamina account resets on relog.

Nothing here was run against real players or a real second client; every finding came from reading the code, and only the fixes above have tests (the plot-data corruption test is new).

## 6. Open items

1. **Duplicate dive animations.** The user reported "two duplicates on the diving animations both down and up" (the ride). Harness
   frames on 1.21.1, 1.21.4 and 1.21.11 show a single rider each way; the cause was not found. Ask for a screenshot and whether it was
   the Prism build. Relevant code: `client/shrine/totk/TotkRide.java` (`pose`, `layer`, `rider`, `drawVoid`), `MudHandLayer`, `TotkSpin`.
2. Frames of the stamina wheel, void, rider and altar renderer were reviewed on every version on Nov 7 (see below); real multiplayer was not tried.
3. Widen the NeoForge and Minecraft version ranges in `versions/V/gradle.properties` only after testing on that exact version; consider 1.20.x.
4. Per-version release jars and a README compatibility table; put the Minecraft version in published file names.
5. Merge `stonecutter` into `main`, update `CHANGELOG.md`, `docs/DEVELOPMENT.md` (still describes the single-project tasks) and the
   README install section when more than one version is released.
6. Pending user-facing clarifications: whether to pick an open license; whether to rename nothing else (repo is already
   `CraftOfTheMine`).

## 7. Working agreements with this user

* Answer plainly, name what was not verified, no long recaps. Use `/ponytail`-style minimal changes.
* Deploy the jar to Prism after changes; relaunch the dev client only when needed (hot swap otherwise); say so when a restart is needed.
* Ask before anything outward-facing that cannot be undone (publishing, force-pushing, deleting); the user does answer, and has
  approved those explicitly when it mattered.
* They test by playing; visual claims should be backed by harness screenshots where possible. The harness cannot press the real
  Shift + Space, so the combo itself still needs their hands.
* Memory notes live under `C:\Users\suchy\.claude\projects\C--Users-suchy-Documents-Claude-BOTW-MC-MOD\memory\` (hot swap preference,
  always deploy, screenshot harness, no physical key tests).
