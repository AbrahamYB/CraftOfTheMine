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
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.Set;

/**
 * Dev only (run: gradlew runAutoClient, which sets -Dbotwclimb.autotest): joins the world in run-auto, builds a flat pad with a
 * terminal, rides to plot 1 and back, and saves a screenshot of chosen ticks of each ride to run-auto/screenshots, then quits. It
 * is how the ride's look is checked without anyone at the keyboard.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class AutoShots {
    private static final boolean ON = Boolean.getBoolean("botwclimb.autotest");
    private static final boolean STYLE_TOTK = "totk".equals(System.getProperty("botwclimb.style"));
    private static final Set<Integer> SHOTS = java.util.stream.IntStream.rangeClosed(1, 140).filter(i -> i % 3 == 1).boxed().collect(java.util.stream.Collectors.toSet());

    private static int stage, wait, startTick;
    private static long rideStart = -1;
    private static String prefix = "up";
    private static boolean shotNow;
    private static String shotName;

    private AutoShots() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ON) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        if (mc.player.isDeadOrDying()) { // a world saved on the death screen: come back to life first
            mc.player.respawn();
            return;
        }
        if (STYLE_TOTK && wait == 0) com.botwclimb.client.ClientConfig.set(com.botwclimb.client.ClientConfig.rideStyleValue(), com.botwclimb.client.ClientConfig.RideStyle.TOTK);
        wait++;
        var server = mc.getSingleplayerServer();
        switch (stage) {
            case 0 -> { // let the world settle, then lay the pad and press the button for plot 1
                if (wait < 150) return;
                server.execute(AutoShots::prepare);
                stage = 1;
                wait = 0;
            }
            case 1 -> {
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
                        new com.botwclimb.network.TerminalOpenPayload(PlotWorld.terminalPos(0), 3, 0, true, 1, java.util.List.of(new com.botwclimb.network.TerminalOpenPayload.Host(java.util.UUID.randomUUID(), "Friend", 0)))));
                if (wait == 70) snap("plot_screen.png");
                if (wait == 74) mc.setScreen(null);
                if (wait == 76) server.execute(AutoShots::toTerminal);
                if (wait == 78) mc.setScreen(new com.botwclimb.client.BotwConfigScreen(null));
                if (wait == 84) snap("config_screen.png");
                if (wait == 88) mc.setScreen(null);
                if (wait < 100) return;
                server.execute(() -> ride(PlotTravel.RETURN));
                stage = 4;
                wait = 0;
                rideStart = -1;
                prefix = "down";
            }
            case 5 -> {
                if (wait < 40) return;
                BotwClimb.LOGGER.info("AutoShots finished");
                mc.stop();
                stage = 6;
            }
            default -> { }
        }
    }

    private static void record(Minecraft mc) {
        MudHandClient.State s = MudHandClient.get(mc.player.getId());
        if (s != null && rideStart < 0) rideStart = s.since();
        if (rideStart < 0) return;
        int elapsed = (int) (mc.level.getGameTime() - rideStart);
        if (s == null && elapsed > 20) { // the ride is over
            stage = stage == 2 ? 3 : 5;
            wait = 0;
            return;
        }
        if (SHOTS.contains(elapsed) && !shotNow && elapsed != startTick) {
            startTick = elapsed;
            shotNow = true;
            shotName = prefix + "_" + String.format("%03d", elapsed) + ".png";
        }
    }

    private static void snap(String name) {
        shotNow = true;
        shotName = name;
    }

    private static ServerPlayer player() {
        return Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().get(0);
    }

    private static void toEdge() {
        var t = PlotWorld.terminalPos(0);
        player().setNoGravity(true);
        com.botwclimb.common.Compat.teleport(player(), player().serverLevel(), t.getX() + 0.5, t.getY() + 40, -30.5, 0f, 52f); // high above the void side, looking over the whole island
    }

    private static void toTerminal() {
        var t = PlotWorld.terminalPos(0);
        player().setNoGravity(false);
        player().teleportTo(t.getX() - 2.5, t.getY(), t.getZ() + 0.5);
    }

    @SubscribeEvent
    public static void screenFrame(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
        if (!ON || !shotNow || event.getScreen() instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen) return;
        shotNow = false;
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, shotName, mc.getMainRenderTarget(), msg -> { });
    }

    @SubscribeEvent
    public static void frame(RenderGuiEvent.Post event) {
        if (!ON || !shotNow || Minecraft.getInstance().screen != null) return;
        shotNow = false;
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, shotName, mc.getMainRenderTarget(), msg -> { });
    }

    /** A flat stone pad with air above it and a terminal beside the player, wherever in the world they are. */
    private static void prepare() {
        ServerPlayer p = Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().get(0);
        if (p.serverLevel() != p.server.overworld()) com.botwclimb.common.Compat.teleport(p, p.server.overworld(), 0.5, 100, 0.5, 0f, 0f); // a world saved inside the plot starts from the overworld
        ServerLevel level = p.serverLevel();
        BlockPos base = p.blockPosition();
        p.setGameMode(GameType.SURVIVAL);
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            level.setBlock(base.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            for (int y = 0; y <= 70; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlock(base.offset(3, 0, 0), BotwClimb.TERMINAL_BLOCK.get().defaultBlockState(), 3);
        level.setDayTime(6000);
        p.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        p.setYRot(-90f);
    }

    private static void ride(int target) {
        ServerPlayer p = Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().get(0);
        BlockPos terminal;
        if (target == PlotTravel.RETURN) {
            int plot = PlotData.get(p.server).plot(p.getUUID(), 0);
            terminal = PlotWorld.terminalPos(plot);
        } else {
            terminal = p.blockPosition().offset(3, 0, 0);
        }
        PlotTravel.handle(p, new TerminalActionPayload(terminal, target));
    }
}
