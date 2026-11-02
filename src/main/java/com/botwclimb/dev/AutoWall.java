package com.botwclimb.dev;

import com.botwclimb.BotwClimb;
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

/**
 * Dev only (gradlew runAutoClient -Pwall): stands the player in front of a stone wall, 0.2 blocks off it, and saves screenshots of the
 * grab marker opening, then moves them 4 blocks back (no marker, and Shift + Space must jump), logs how high that jump got, and quits.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class AutoWall {
    private static final boolean ON = Boolean.getBoolean("botwclimb.autowall");
    private static int t;
    private static BlockPos base;
    private static double startY, peak;
    private static String shot;

    private AutoWall() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ON) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        if (mc.player.isDeadOrDying()) { mc.player.respawn(); return; }
        t++;
        var server = mc.getSingleplayerServer();
        if (t == 148) server.execute(AutoWall::build);
        if (t >= 151 && t <= 162) shot = "wall_in_" + t + ".png"; // the marker opening
        if (t == 260) shot = "wall_close.png";
        if (t == 300) server.execute(() -> player().teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 3.5)); // 4 blocks off the wall, still facing it
        if (t == 320) { // marker gone: a jump with Shift held (set on the key mappings: no modifier reaches the game this way, so it is the plain-jump case only)
            startY = mc.player.getY();
            peak = startY;
            BotwClimb.LOGGER.info("[autowall] at the jump: onGround {} y {}", mc.player.onGround(), startY);
            mc.options.keyShift.setDown(true);
            mc.options.keyJump.setDown(true);
        }
        if (t > 320 && t < 340) {
            peak = Math.max(peak, mc.player.getY());
            if (t % 4 == 0) BotwClimb.LOGGER.info("[autowall] t={} y+{} onGround {} jumpKey {}", t, mc.player.getY() - startY, mc.player.onGround(), mc.options.keyJump.isDown());
        }
        if (t == 340) {
            BotwClimb.LOGGER.info("[autowall] shift + jump in the open: peak {} blocks above the start", peak - startY);
            mc.options.keyShift.setDown(false);
            mc.options.keyJump.setDown(false);
        }
        if (t == 345) shot = "wall_open.png";
        if (t == 350) server.execute(() -> player().teleportTo(base.getX() + 0.5, base.getY(), base.getZ() - 0.5)); // back against the wall
        if (t == 360) { // grab it on the client only, in third person, to see the climbing pose (the server will correct it a moment later)
            var d = mc.player.getData(BotwClimb.CLIMB);
            d.dir = net.minecraft.core.Direction.NORTH;
            d.setState(com.botwclimb.common.climb.ClimbState.CLIMBING);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        }
        if (t >= 361 && t <= 366) shot = "wall_climb_" + t + ".png";
        if (t == 400) {
            BotwClimb.LOGGER.info("AutoWall finished");
            mc.stop();
        }
    }

    private static ServerPlayer player() {
        return Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().get(0);
    }

    private static void build() {
        ServerPlayer p = player();
        if (com.botwclimb.common.Compat.level(p) != p.level().getServer().overworld()) com.botwclimb.common.Compat.teleport(p, p.level().getServer().overworld(), 0.5, 100, 0.5, 0f, 0f);
        ServerLevel level = com.botwclimb.common.Compat.level(p);
        base = p.blockPosition();
        p.setGameMode(GameType.SURVIVAL);
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            level.setBlock(base.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            for (int y = 0; y <= 12; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        for (int x = -6; x <= 6; x++) for (int y = 0; y <= 8; y++) level.setBlock(base.offset(x, y, -2), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        level.setDayTime(6000);
        com.botwclimb.common.Compat.teleport(p, level, base.getX() + 0.5, base.getY(), base.getZ() - 0.5 + 0.0, 180f, 5f); // the wall's face is at z = base - 1; 0.3 half width + 0.2 gap
    }

    @SubscribeEvent
    public static void frame(RenderGuiEvent.Post event) {
        if (!ON || shot == null) return;
        Minecraft mc = Minecraft.getInstance();
        com.botwclimb.client.ClientCompat.screenshot(mc.gameDirectory, shot);
        shot = null;
    }
}
