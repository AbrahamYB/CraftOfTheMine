package com.botwclimb.dev;

import com.botwclimb.BotwClimb;
import com.botwclimb.client.shrine.MudHandClient;
import com.botwclimb.common.shrine.PlotData;
import com.botwclimb.common.shrine.PlotTravel;
import com.botwclimb.common.shrine.PlotWorld;
import com.botwclimb.network.TerminalActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Set;

/**
 * Dev only (run: gradlew runAutoClient, which sets -Dbotwclimb.autotest): joins the world in run-auto, builds a flat pad with a
 * terminal, rides to plot 1 and back, and saves a screenshot of chosen ticks of each ride to run-auto/screenshots, then quits. It
 * is how the ride's look is checked without anyone at the keyboard.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class AutoShots {
    private static final boolean ON = Boolean.getBoolean("botwclimb.autotest");
    private static final boolean EVERY = Boolean.getBoolean("botwclimb.record"); // -Precord: one frame per tick, before, during and after both rides (for a video)
    private static final boolean COLORS = Boolean.getBoolean("botwclimb.colors"); // -Pcolors: a colour per body part on the default skins, to read a pose from a frame (1.21.1 only)
    private static int recN;
    private static final boolean SCREENS = Boolean.getBoolean("botwclimb.screens"); // -Pscreens: the terminal screen as host, joiner and guest, with pretend friends, then quit
    private static final boolean OUTLINE = Boolean.getBoolean("botwclimb.outline"); // -Poutline: straight to a plot corner (no rides) to film the outline, then quit
    private static final boolean SHADOW = Boolean.getBoolean("botwclimb.shadow"); // -Pshadow: close-ups of the terminal's and altar's shadow on a red floor
    private static final Set<Integer> SHOTS = java.util.stream.IntStream.rangeClosed(1, 140).filter(i -> i % 3 == 1).boxed().collect(java.util.stream.Collectors.toSet());

    private static int stage, wait, startTick;
    private static int guideScene, guideFrame;
    private static long rideStart = -1;
    private static String prefix = "up";
    private static boolean shotNow;
    private static String shotName;

    private AutoShots() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ON) return;
        if (EVERY) com.botwclimb.client.shrine.totk.TotkRide.recordClock += 1f;
        Minecraft mc = Minecraft.getInstance();
        AutoConfirm.pump(mc);
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        if (mc.player.isDeadOrDying()) { // a world saved on the death screen: come back to life first
            mc.player.respawn();
            return;
        }
        wait++;
        var server = mc.getSingleplayerServer();
        switch (stage) {
            case 0 -> { // let the world settle, then lay the pad and press the button for plot 1
                if (COLORS && wait == 5) colorSkin();
                if (wait < 150) return;
                if (Boolean.getBoolean("botwclimb.guide")) { // -Pguide: film our guide scenes, three frames per tick (a 60 fps video)
                    stage = 55;
                    wait = 0;
                    return;
                }
                if (SCREENS) {
                    stage = 40;
                    wait = 0;
                    return;
                }
                if (OUTLINE) {
                    com.botwclimb.client.ClientConfig.set(com.botwclimb.client.ClientConfig.plotOutlineValue(), true);
                    server.execute(AutoShots::toOutline);
                    stage = 30;
                    wait = 0;
                    return;
                }
                if (SHADOW) {
                    server.execute(AutoShots::prepareShadow);
                    stage = 20;
                    wait = 0;
                    return;
                }
                server.execute(AutoShots::prepare);
                stage = 1;
                wait = 0;
            }
            case 1 -> {
                if (wait >= 10 && wait < 40) rec();
                if (wait < 40) return;
                server.execute(() -> ride(0));
                stage = 2;
                wait = 0;
                rideStart = -1;
                prefix = "up";
            }
            case 2, 4 -> record(mc);
            case 3 -> { // arrived in the plot: wait for the set-down to finish, then press Return at the island terminal
                if (wait == 20) server.execute(AutoShots::toEdge); // look at the plot's outline from near its corner
                if (wait == 60) snap("plot_outline.png");
                if (wait == 64) mc.setScreen(new com.botwclimb.client.shrine.TerminalScreen(
                        new com.botwclimb.network.TerminalOpenPayload(PlotWorld.terminalPos(0), true, true, true, true, java.util.List.of(new com.botwclimb.network.TerminalOpenPayload.Host(java.util.UUID.randomUUID(), "Friend")))));
                if (wait == 70) snap("plot_screen.png");
                if (wait == 74) mc.setScreen(null);
                if (wait == 76) server.execute(AutoShots::toTerminal);
                if (wait == 78) mc.setScreen(new com.botwclimb.client.BotwConfigScreen(null));
                if (wait == 84) snap("config_screen.png");
                if (wait == 88) mc.setScreen(null);
                if (wait == 89) mc.setScreen(new com.botwclimb.client.altar.AltarScreen(PlotWorld.terminalPos(0)));
                if (wait == 95) snap("altar_screen.png");
                if (wait == 98) mc.setScreen(null);
                if (wait >= 99) rec();
                if (wait < 100) return;
                server.execute(() -> ride(PlotTravel.RETURN));
                stage = 4;
                wait = 0;
                rideStart = -1;
                prefix = "down";
            }
            case 20 -> { // shadow close-ups: a view from above at the base's wall, a low one, the altar, then quit
                if (wait == 30) server.execute(() -> look(1.6, 0.5, -90f, 45f));
                if (wait == 45) snap("shadow_a.png");
                if (wait == 50) server.execute(() -> look(0.3, 0.5, -90f, 28f));
                if (wait == 65) snap("shadow_b.png");
                if (wait == 70) server.execute(() -> look(-1.4, 0.5, 90f, 45f));
                if (wait == 85) snap("shadow_c.png");
                if (wait == 90) server.execute(() -> look(-2.2, 2.2, 135f, 30f));
                if (wait == 105) snap("shadow_d.png");
                if (wait == 110) server.execute(() -> look(2.4, 4.5, -90f, 30f, 3));
                if (wait == 125) snap("shadow_e.png");
                if (wait == 130) server.execute(() -> look(3.2, 5.5, 90f, 30f, 3));
                if (wait == 145) snap("shadow_f.png");
                if (wait == 160) server.execute(AutoShots::altarItems); // the altar as an item: hotbar, hand, on the ground, at 0, 3 and 8 upgrades
                if (wait == 175) snap("altar_0.png");
                if (wait == 180) server.execute(() -> player().setData(BotwClimb.STAMINA_UPGRADES, 3));
                if (wait == 195) snap("altar_3.png");
                if (wait == 200) server.execute(() -> player().setData(BotwClimb.STAMINA_UPGRADES, 8));
                if (wait == 215) snap("altar_8.png");
                if (wait == 225) {
                    mc.stop();
                    stage = 6;
                }
            }
            case 40 -> { // one screen every 12 ticks: open, let it draw, snap
                var pos = PlotWorld.terminalPos(0);
                java.util.function.Function<String, com.botwclimb.network.TerminalOpenPayload.Host> h = n -> new com.botwclimb.network.TerminalOpenPayload.Host(java.util.UUID.nameUUIDFromBytes(n.getBytes()), n);
                if (wait == 2) mc.setScreen(new com.botwclimb.client.shrine.TerminalScreen(new com.botwclimb.network.TerminalOpenPayload(pos, false, false, false, false, java.util.List.of()))); // alone: no plot yet, nobody open
                if (wait == 10) snap("ui_1_alone.png");
                if (wait == 14) mc.setScreen(new com.botwclimb.client.shrine.TerminalScreen(new com.botwclimb.network.TerminalOpenPayload(pos, true, true, true, true, java.util.List.of()))); // the host, on their plot with friends on
                if (wait == 22) snap("ui_2_host.png");
                if (wait == 26) mc.setScreen(new com.botwclimb.client.shrine.TerminalScreen(new com.botwclimb.network.TerminalOpenPayload(pos, true, false, false, false, java.util.List.of(h.apply("Alex"), h.apply("Steve"), h.apply("Sam"), h.apply("AVeryLongName_99"), h.apply("Noor"), h.apply("Kai"), h.apply("Zuri"))))); // a friend on the ground: seven open plots, so the box scrolls
                if (wait == 34) snap("ui_3_join.png");
                if (wait == 35 || wait == 36) mc.screen.mouseScrolled(mc.getWindow().getGuiScaledWidth() / 2 + 54, mc.getWindow().getGuiScaledHeight() / 2 + 8, 0, -1); // two notches down the friends box
                if (wait == 42) snap("ui_3b_scrolled.png");
                if (wait == 46) mc.setScreen(new com.botwclimb.client.shrine.TerminalScreen(new com.botwclimb.network.TerminalOpenPayload(pos, true, false, true, false, java.util.List.of(h.apply("Alex"))))); // a guest inside Alex's plot
                if (wait == 54) snap("ui_4_guest.png");
                if (wait == 58) mc.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true)); // the pause menu with our button beside Report Bugs
                if (wait == 66) snap("ui_5_pause.png");
                if (wait == 68) mc.setScreen(new com.botwclimb.client.ModMenu.Menu(mc.screen));
                if (wait == 76) snap("ui_6_menu.png");
                if (wait == 78) mc.setScreen(new com.botwclimb.client.altar.AltarScreen(PlotWorld.terminalPos(0)));
                if (wait == 86) snap("ui_7_altar.png");
                if (wait == 90) mc.stop();
            }
            case 55 -> { // each scene from the start, its clock a third of a tick per frame, then a pause on its last frame
                var scenes = com.botwclimb.client.guide.GuideScenes.all();
                if (wait == 1) {
                    guideScene = Integer.getInteger("botwclimb.guide.first", 0);
                    guideFrame = 0;
                    com.botwclimb.client.guide.GuideScreen.clockOverride = 0f;
                    mc.setScreen(new com.botwclimb.client.guide.GuideScreen(scenes, guideScene, null));
                }
                if (wait < 4) return;
                float t = guideFrame / 3f;
                com.botwclimb.client.guide.GuideScreen.clockOverride = t;
                snap(String.format("guide%d_%04d.png", guideScene, guideFrame));
                guideFrame++;
                if (t > scenes.get(guideScene).length() + 20) {
                    if (++guideScene >= scenes.size()) {
                        mc.stop();
                        return;
                    }
                    guideFrame = 0;
                    com.botwclimb.client.guide.GuideScreen.clockOverride = 0f;
                    mc.setScreen(new com.botwclimb.client.guide.GuideScreen(scenes, guideScene, null));
                }
            }
            case 30 -> { // the outline: hold the view while it is filmed, a still at the end, then quit
                if (wait == 300) snap("plot_outline.png");
                if (wait == 310) mc.stop();
            }
            case 5 -> {
                if (wait < 36) rec();
                if (wait < 40) return;
                BotwClimb.LOGGER.info("AutoShots finished");
                mc.stop();
                stage = 6;
            }
            default -> { }
        }
    }

    private static void record(Minecraft mc) {
        if (Boolean.getBoolean("botwclimb.levelcam")) mc.player.setXRot(6f); // the third-person view follows the pitch: nearly level shows a pose as it reads from the side
        MudHandClient.State s = MudHandClient.get(mc.player.getId());
        if (s != null && rideStart < 0) rideStart = s.since();
        if (rideStart < 0) return;
        int elapsed = (int) (mc.level.getGameTime() - rideStart);
        if (s == null && elapsed > 20) { // the ride is over
            stage = stage == 2 ? 3 : 5;
            wait = 0;
            return;
        }
        if (EVERY) {
            rec();
            return;
        }
        if (SHOTS.contains(elapsed) && !shotNow && elapsed != startTick) {
            startTick = elapsed;
            shotNow = true;
            shotName = prefix + "_" + String.format("%03d", elapsed) + ".png";
        }
    }

    /**
     * Replaces every default skin (in memory only, nothing on disk) with one that has a colour per body part: head red, body green, right arm
     * blue, left arm yellow, right leg magenta, left leg cyan, and nothing on the outer layers.
     */
    private static void colorSkin() {
        //? if <1.21.2 {
        int[][] parts = {{0, 0, 32, 16, 0xE02020}, {16, 16, 40, 32, 0x20C040}, {40, 16, 56, 32, 0x2060F0}, {32, 48, 48, 64, 0xF0E020}, {0, 16, 16, 32, 0xE020E0}, {16, 48, 32, 64, 0x20E0E0}};
        for (String model : new String[] {"wide", "slim"}) {
            for (String name : new String[] {"alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri"}) {
                var img = new com.mojang.blaze3d.platform.NativeImage(64, 64, true);
                for (int[] r : parts) {
                    int abgr = 0xFF000000 | (r[4] & 255) << 16 | (r[4] >> 8 & 255) << 8 | (r[4] >> 16 & 255);
                    for (int x = r[0]; x < r[2]; x++) for (int y = r[1]; y < r[3]; y++) img.setPixelRGBA(x, y, abgr);
                }
                Minecraft.getInstance().getTextureManager().register(net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/player/" + model + "/" + name + ".png"),
                        new net.minecraft.client.renderer.texture.DynamicTexture(img));
            }
        }
        //?}
    }

    /** The number of the next recorded frame (to line sounds up with the video). */
    public static int frame() {
        return recN;
    }

    private static void rec() {
        if (!EVERY || shotNow) return;
        shotNow = true;
        shotName = String.format("rec_%04d.png", recN++);
    }

    private static void snap(String name) {
        shotNow = true;
        shotName = name;
    }

    private static ServerPlayer player() {
        return Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().get(0);
    }

    /** Into plot 0 (built if need be) just outside a corner, flying, looking along two sides of the outline. */
    private static void toOutline() {
        ServerPlayer p = player();
        var level = p.level().getServer().getLevel(PlotWorld.DIM);
        if (level == null) return;
        if (!PlotWorld.isBuilt(level, 0)) PlotWorld.buildIsland(level, 0);
        var t = PlotWorld.terminalPos(0);
        p.getAbilities().mayfly = p.getAbilities().flying = true;
        p.onUpdateAbilities();
        com.botwclimb.common.Compat.teleport(p, level, t.getX() - 30.5, t.getY() + 2, t.getZ() - 29.5, -80f, 12f); // by a corner, looking down a whole side (64 blocks) to see the far marks
    }

    private static void toEdge() {
        var t = PlotWorld.terminalPos(0);
        player().setNoGravity(true);
        player().getAbilities().mayfly = player().getAbilities().flying = true; // held up there for the shot (no gravity alone was undone by the client)
        player().onUpdateAbilities();
        com.botwclimb.common.Compat.teleport(player(), com.botwclimb.common.Compat.level(player()), t.getX() - 28.5, t.getY() + 5, t.getZ() - 36.5, -20f, 40f); // just outside a corner, looking along two sides of the outline
    }

    private static void toTerminal() {
        var t = PlotWorld.terminalPos(0);
        player().setNoGravity(false);
        player().teleportTo(t.getX() - 2.5, t.getY(), t.getZ() + 0.5);
    }

    @SubscribeEvent
    public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        if (!ON || !shotNow || Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen) return; // after the whole frame, GUI included
        shotNow = false;
        Minecraft mc = Minecraft.getInstance();
        com.botwclimb.client.ClientCompat.screenshot(mc.gameDirectory, shotName);
    }

    /** A flat stone pad with air above it and a terminal beside the player, wherever in the world they are. */
    private static void prepare() {
        ServerPlayer p = Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().get(0);
        if (com.botwclimb.common.Compat.level(p) != p.level().getServer().overworld()) com.botwclimb.common.Compat.teleport(p, p.level().getServer().overworld(), 0.5, 100, 0.5, 0f, 0f); // a world saved inside the plot starts from the overworld
        ServerLevel level = com.botwclimb.common.Compat.level(p);
        BlockPos base = p.blockPosition();
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent(); // the test world keeps its inventory between runs: nothing held in the rider's hand
        if (EVERY) // a frame for every tick even on a slow machine: the game runs at a quarter speed while it is recorded (the video plays one frame per tick)
            level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "tick rate 5");
        if (Boolean.getBoolean("botwclimb.armor")) { // -Parmor: leggings and boots, to see them bend with the knees
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_LEGGINGS));
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_BOOTS));
        }
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            level.setBlock(base.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            for (int y = 0; y <= 70; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlock(base.offset(3, 0, 0), BotwClimb.TERMINAL_BLOCK.get().defaultBlockState(), 3);
        level.setDayTime(6000);
        if (Boolean.getBoolean("botwclimb.night")) // -Pnight: midnight, to hold the ride's glow against footage shot in dark rooms (a command: setDayTime is gone in 26.x)
            level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "time set midnight");
        p.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        p.setYRot(-90f);
    }

    /** A red concrete floor (so a shadow shows), a terminal three blocks east and an altar three blocks west of the pad's middle. */
    private static void prepareShadow() {
        ServerPlayer p = Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().get(0);
        ServerLevel level = com.botwclimb.common.Compat.level(p);
        BlockPos base = p.blockPosition();
        p.setGameMode(GameType.CREATIVE);
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            level.setBlock(base.offset(x, -1, z), floor().defaultBlockState(), 2);
            for (int y = 0; y <= 10; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlock(base.offset(3, 0, 0), BotwClimb.TERMINAL_BLOCK.get().defaultBlockState(), 3);
        level.setBlock(base.offset(-3, 0, 0), BotwClimb.ALTAR_BLOCK.get().defaultBlockState(), 3);
        // a terminal on a one-block pillar, and an altar on a corner of a small plateau: no shadow should hang over the air beside them
        for (int y = -1; y <= 2; y++) level.setBlock(base.offset(6, y, 4), floor().defaultBlockState(), 2);
        level.setBlock(base.offset(6, 3, 4), BotwClimb.TERMINAL_BLOCK.get().defaultBlockState(), 3);
        for (int y = -1; y <= 1; y++) level.setBlock(base.offset(-1, y, 6), floor().defaultBlockState(), 2);
        for (int x = -2; x <= 0; x++) for (int z = 5; z <= 7; z++) level.setBlock(base.offset(x, 2, z), floor().defaultBlockState(), 2);
        level.setBlock(base.offset(0, 3, 5), BotwClimb.ALTAR_BLOCK.get().defaultBlockState(), 3);
        level.setDayTime(6000);
        shadowBase = base;
    }

    private static BlockPos shadowBase = BlockPos.ZERO;

    private static void altarItems() {
        ServerPlayer p = player();
        look(0.3, 0.5, -90f, 25f);
        p.setData(BotwClimb.STAMINA_UPGRADES, 0);
        for (int i = 0; i < 3; i++) p.getInventory().setItem(i, new net.minecraft.world.item.ItemStack(BotwClimb.ALTAR_ITEM.get()));
        var level = com.botwclimb.common.Compat.level(p);
        var drop = new net.minecraft.world.entity.item.ItemEntity(level, shadowBase.getX() + 1.9, shadowBase.getY() + 0.2, shadowBase.getZ() + 0.5, new net.minecraft.world.item.ItemStack(BotwClimb.ALTAR_ITEM.get()));
        drop.setPickUpDelay(32767);
        drop.setDeltaMovement(0, 0, 0);
        level.addFreshEntity(drop);
    }

    private static net.minecraft.world.level.block.Block floor() { // red, so a shadow shows (26.2 renamed it, so those versions use stone)
        //? if >=26.2 {
        /*return Blocks.SMOOTH_STONE;
        *///?} else {
        return Blocks.RED_WOOL;
        //?}
    }

    private static void look(double dx, double dz, float yaw, float pitch) {
        look(dx, dz, yaw, pitch, 0);
    }

    private static void look(double dx, double dz, float yaw, float pitch, int dy) {
        ServerPlayer p = player();
        p.setNoGravity(true);
        com.botwclimb.common.Compat.teleport(p, com.botwclimb.common.Compat.level(p), shadowBase.getX() + 0.5 + dx, shadowBase.getY() + dy, shadowBase.getZ() + dz, yaw, pitch);
    }

    private static void ride(int target) {
        ServerPlayer p = Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().get(0);
        BlockPos terminal;
        if (target == PlotTravel.RETURN) {
            int plot = PlotData.get(p.level().getServer()).plot(p.getUUID());
            terminal = PlotWorld.terminalPos(plot);
        } else {
            terminal = p.blockPosition().offset(3, 0, 0);
        }
        PlotTravel.handle(p, new TerminalActionPayload(terminal, target));
    }
}
