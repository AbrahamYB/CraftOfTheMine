package com.botwclimb.client.shrine;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.shrine.PlotWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * The plot outline, switched from the terminal inside a plot: where the island ends is drawn as a glowing green line on the
 * ground, tall posts at the corners and short ticks at every chunk border along it (the 16 chunks of the plot). It is drawn after the clouds so it never cuts into them.
 * Purely a picture on this client.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class PlotOutline {
    /** The island edge, measured from its middle block: the 64 blocks from -32 up to (not including) +32. */
    private static final float LOW = -PlotWorld.HALF, HIGH = PlotWorld.HALF;
    private static final float LINE = 0.09f, GLOW = 0.45f, POST = 24f, TICK = 1.3f, TICK_EVERY = 16f;

    private PlotOutline() {}

    public static boolean on() {
        return com.botwclimb.client.ClientConfig.plotOutline();
    }

    public static void toggle() {
        com.botwclimb.client.ClientConfig.set(com.botwclimb.client.ClientConfig.plotOutlineValue(), !on());
    }

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        if (!on() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level.dimension() != PlotWorld.DIM) return;
        int plot = PlotWorld.plotAt(mc.player.blockPosition());
        Vec3 cam = event.getCamera().getPosition();
        float x0 = PlotWorld.ORIGIN_X + plot * PlotWorld.SPACING + LOW, x1 = PlotWorld.ORIGIN_X + plot * PlotWorld.SPACING + HIGH, z0 = PlotWorld.ORIGIN_Z + LOW, z1 = PlotWorld.ORIGIN_Z + HIGH, y = PlotWorld.Y + 1f;
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        // the line on the ground: a bright thin one over a wider soft one, round all four sides
        for (int side = 0; side < 4; side++) {
            boolean alongX = side < 2;
            float fixed = side == 0 ? z0 : side == 1 ? z1 : side == 2 ? x0 : x1;
            float from = alongX ? x0 : z0, to = alongX ? x1 : z1;
            strip(vc, m, alongX, fixed, from - GLOW, to + GLOW, GLOW, y + 0.01f, 60);
            strip(vc, m, alongX, fixed, from - LINE, to + LINE, LINE, y + 0.02f, 235);
            for (float t = from + TICK_EVERY; t < to - 0.01f; t += TICK_EVERY) post(vc, m, alongX ? t : fixed, alongX ? fixed : t, y, TICK, 0.05f, 200);
        }
        for (float cx : new float[] {x0, x1}) for (float cz : new float[] {z0, z1}) post(vc, m, cx, cz, y, POST, 0.07f, 235);
        buffers.endBatch(RenderType.debugQuads());
        ps.popPose();
    }

    /** A flat strip along one side, {@code half} wide to each side of the line, at height y. */
    private static void strip(VertexConsumer vc, Matrix4f m, boolean alongX, float fixed, float from, float to, float half, float y, int alpha) {
        if (alongX) {
            vertex(vc, m, from, y, fixed - half, alpha);
            vertex(vc, m, to, y, fixed - half, alpha);
            vertex(vc, m, to, y, fixed + half, alpha);
            vertex(vc, m, from, y, fixed + half, alpha);
        } else {
            vertex(vc, m, fixed - half, y, from, alpha);
            vertex(vc, m, fixed + half, y, from, alpha);
            vertex(vc, m, fixed + half, y, to, alpha);
            vertex(vc, m, fixed - half, y, to, alpha);
        }
    }

    /** A thin square post, bright at the foot and fading toward the top. */
    private static void post(VertexConsumer vc, Matrix4f m, float x, float z, float y, float height, float half, int alpha) {
        for (int face = 0; face < 2; face++) {
            float dx = face == 0 ? half : 0, dz = face == 0 ? 0 : half;
            vertex(vc, m, x - dx, y, z - dz, alpha);
            vertex(vc, m, x + dx, y, z + dz, alpha);
            vertex(vc, m, x + dx, y + height, z + dz, 0);
            vertex(vc, m, x - dx, y + height, z - dz, 0);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, int alpha) {
        vc.addVertex(m, x, y, z).setColor(0, 242, 90, alpha);
    }
}
