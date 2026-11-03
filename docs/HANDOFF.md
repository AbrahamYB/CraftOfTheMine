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
* bullet time for bows and crossbows (slow fall, slowed mobs on the shooter's screen);
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
`:1.21.4:...`, `:1.21.11:...`.

* `:V:build` builds `versions/V/build/libs/CraftOfTheMine-1.0.0+mcV.jar` and, via `deploy`, copies it into the Prism mods folder, but
  only when `V` equals `prismVersion` in `local.properties` (default 1.21.1). Standing preference: **deploy to Prism after every change**
  (saved in memory as `feedback_always_deploy_prism`).
* `:V:runGameTestServer` runs the headless tests (55 on 1.21.1 and 1.21.4, 56 on 1.21.11). Close any client first, and keep client-only
  mods (Sodium and the like) out of `run/mods` on the old single-project layout. A bullet-time test is flaky (one of two arrow/lag tests
  fails now and then); rerun before believing it.
* `:V:runClient` is the dev client (debug port 5005, JetBrains Runtime for hot swap when `jbr=` is set). `gradlew hotswap` pushes code
  into a running client (method bodies; JBR also allows adding methods).
* `:V:runAutoClient` (add `-Pwall` for the wall/jump/climb-pose scene) starts a second client in `versions/V/run-auto`, plays a scripted
  scene and writes screenshots to `run-auto/screenshots`. It needs a world copy in `run-auto/saves/AutoTest` and
  `pauseOnLostFocus:false` in its `options.txt` (otherwise it freezes whenever its window loses focus). Screenshots are taken after the
  whole frame (`RenderFrameEvent.Post`). **Never inject real keyboard input** (a `java.awt.Robot` experiment toggled the user's physical
  Shift; saved as memory `feedback_no_physical_key_tests`). Set `KeyMapping` state or call controllers inside the game instead.
* `tools/errsym.py <javac output>` lists compile errors with the missing symbol; handy when porting.
* Gradle needs `systemProp.javax.net.ssl.trustStoreType=WINDOWS-ROOT` in `~/.gradle/gradle.properties` on this machine (corporate-style
  certificate revocation failures otherwise); `curl` needs `--ssl-no-revoke`.

## 4. Multi-version design (Stonecutter 0.9.8, Groovy scripts)

Goal from the user: **one release per Minecraft line from 1.20 up to 1.21.11**, each "working perfectly", without a pile of jars.
The plan agreed: a jar can cover neighbouring versions when the code they use is the same; real breaks get their own build.

How it works:

* `settings.gradle` lists the versions; `versions/V/gradle.properties` holds that version's Minecraft, NeoForge, Java and (optional)
  Parchment values; `build.gradle` is one shared script that runs per version; `stonecutter.gradle` marks the active version.
* Source is shared in `src/`. Version differences use Stonecutter comments, written in the 1.21.1 ("active") state:
  `//? if >=1.21.2 {` newer code inside `/* ... *///?} else {` older code `//?}`. Never put a `/** */` comment or another conditional
  inside a commented-out branch (block comments do not nest). For a version-only subscriber class, make the annotation itself
  conditional: an `@EventBusSubscriber` class with no `@SubscribeEvent` method **crashes mod loading** (this broke the 1.21.1 jar
  between 9:55 and 11:26 on Nov 2; fixed and redeployed).
* Global token replacements live in `build.gradle` (`replacements { string { ... } }`, active from 1.21.11): `ResourceLocation` to
  `Identifier`, moved classes, `RenderType.x(` to `RenderTypes.x(`, `.location()` to `.identifier()`, `g.renderTooltip(` to
  `g.setTooltipForNextFrame(`, the particle base class, and the test annotations.
* Differences that need real code sit behind two helper classes so call sites stay clean: `common/Compat.java` (teleport, registries,
  holders, inventory items, permissions, attachment codecs, names, spawn, arrow damage) and `client/ClientCompat.java` (partial tick,
  movement input flags, blit/sprite, pose helpers, tooltips, packets to the server, window handle, keys, screenshots, outer model
  layers, level-render events). `client/RenderData.java` and `client/RenderStateSetup.java` attach the living entity (and the HUD-rider
  mark) to the render state from 1.21.2 on. `client/GuiShapes.java` draws the wheel's triangle strips (immediately before 1.21.6, as a
  submitted GUI element after). `common/Nbt.java` wraps the NBT reads/writes that changed in 1.21.11. `common/PlayerStateSync.java` +
  `network/PlayerStatePayload` replace NeoForge's attachment sync (removed in NeoForge 21.4); they run on every version.
* Per-version files that replace shared ones of the same path: `versions/V/resources/` (recipe and item model formats from 1.21.4;
  access transformers; `META-INF/accesstransformer.cfg` for 1.21.11 adds `ClientInput.moveVector`, `AbstractArrow.baseDamage`,
  `GuiGraphics.guiRenderState`).
* Tests: the 1.21.5+ test framework has no `@GameTest`; `dev/GameTest`, `GameTestHolder`, `PrefixGameTestTemplate` and
  `dev/TestRegistration` re-create it (reflection over the annotated methods) from 1.21.11.

Verified so far (Nov 2): 1.21.1, 1.21.4 and 1.21.11 compile, pass their game tests, and run the screenshot harness (climb pose, wall
jump, ride scenes, terminal, settings screen rendered). 1.21.1 jar is deployed to Prism.

Known gaps / decisions on the new versions:

* Motion blur of the ride is off from 1.21.2 (post-effect format changed; its shaders are excluded from those jars).
* 1.21.11: the 3D helix in the HUD "void" is not drawn (no way found to swap the projection); the rest of the void is drawn as GUI
  quads. Its HUD frames were not reviewed visually.
* 1.21.11 saves attachments with a different codec shape, so stamina upgrades, fabric unlocks and altar credit from a world made on an
  older version are skipped (logged, not fatal).
* Not started: 1.20.1 (old Forge-style NeoForge 47.1, a rewrite more than a port: no data attachments, different networking and saved
  data), 1.20.2 to 1.20.6, 1.21, 1.21.3, 1.21.5, 1.21.8, 1.21.10. NeoForge stable versions that exist (from its maven):
  1.20.1 (forge 47.1.x), 20.2, 20.4, 20.6, 21.0, 21.1, 21.3, 21.4, 21.5, 21.8, 21.10, 21.11 (21.2, 21.6, 21.7, 21.9 are beta only;
  20.3 and 20.5 beta only). Expected generations (unverified, from knowledge, confirm by compiling): 1.20.1 / 1.20.2-1.20.4 /
  1.20.5-1.20.6 / 1.21-1.21.1 / 1.21.2-1.21.4 / 1.21.5-1.21.11 (probably split further around 1.21.5, 1.21.6 and 1.21.9).
* Release naming (Sodium-style): tag `mc<mc>-<mod version>`, title "Craft of the Mine X for Minecraft Y", jar
  `CraftOfTheMine-<ver>+mc<mc>.jar`. Only `mc1.21.1-1.0.0` exists (jar named `CraftOfTheMine-1.0.0.jar`).

## 5. Decisions and the reasons (so they are not redone)

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

## 6. Open items

1. **Duplicate dive animations.** The user reported "two duplicates on the diving animations both down and up" (the ride). Harness
   frames on 1.21.1, 1.21.4 and 1.21.11 show a single rider each way; the cause was not found. Ask for a screenshot and whether it was
   the Prism build. Relevant code: `client/shrine/totk/TotkRide.java` (`pose`, `layer`, `rider`, `drawVoid`), `MudHandLayer`, `TotkSpin`.
2. Review the 1.21.11 HUD frames (stamina wheel, void, rider) and the altar block entity renderer there.
3. Continue the ports in order of value (suggested: 1.21 with 1.21.1, then 1.21.5-1.21.10, then 1.20.x), widening the NeoForge and
   Minecraft version ranges in `versions/V/gradle.properties` only after testing on that exact version.
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
