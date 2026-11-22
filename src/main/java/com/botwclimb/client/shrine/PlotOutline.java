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
 * The plot outline, switched from the terminal inside a plot: where the island ends is drawn on the ground as the terminal's
 * glyph band, a silver border with a dark teal inlay and green glyph marks along it, with a silver tile at each corner. It is drawn after the clouds so it never cuts into them.
 * Purely a picture on this client.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class PlotOutline {
    /** The island edge, measured from its middle block: the 64 blocks from -32 up to (not including) +32. */
    private static final float LOW = -PlotWorld.HALF, HIGH = PlotWorld.HALF;
    /** Half-widths of the silver band, its teal inlay and the glyph marks; the corner tile's half size. */
    private static final float BAND = 0.26f, INLAY = 0.17f, MARK = 0.045f, TILE = 0.55f;
    /** How fast the writing drifts along the band (blocks a second). */
    private static final float DRIFT = 0.6f;
    /** How far outside the plot's edge (across the ground) the outline still shows. */
    private static final float NEAR = 16f;
    private static final int BAND_RGB = 0xE8EEF2, BAND_EDGE = 0xA9B3BB, NOTCH_RGB = 0x6C7883, INLAY_MID = 0x1B5A50, INLAY_RGB = 0x14423B, MARK_RGB = 0x9FF5C8, DOT_RGB = 0x5FE3A1;
    /** The glyph marks along the band: lengths of mark, gap, mark, gap... in blocks, repeated (uneven, so it reads as writing, not a dashed line). */
    private static final float[] GLYPHS = {0.35f, 0.2f, 0.7f, 0.25f, 0.2f, 0.2f, 0.5f, 0.3f, 0.15f, 0.25f, 0.45f, 0.35f};

    private PlotOutline() {}

    public static boolean on() {
        return com.botwclimb.client.ClientConfig.plotOutline();
    }

    public static void toggle() {
        com.botwclimb.client.ClientConfig.set(com.botwclimb.client.ClientConfig.plotOutlineValue(), !on());
    }

    //? if >=26.2 {
    /*@SubscribeEvent
    public static void onSubmit(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent event) {
        if (!on()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level.dimension() != PlotWorld.DIM) return;
        for (int pass = 0; pass < PASSES; pass++) {
            int p = pass;
            event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderType.debugQuads(),
                    (pose, vc) -> draw(mc, com.botwclimb.client.ClientCompat.poseStackOf(pose), vc, p));
        }
    }
    *///?} else if >=1.21.8 {
    /*@SubscribeEvent
    public static void draw(RenderLevelStageEvent.AfterWeather event) {
        if (on()) render(event);
    }
    *///?} else {
    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        if (on() && event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) render(event);
    }
    //?}

    //? if >=26.2 {
    /*// 26.2 has no immediate buffers: the geometry is submitted from onSubmit
    *///?} else {
    private static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level.dimension() != PlotWorld.DIM) return;
        com.botwclimb.client.Buffers buffers = com.botwclimb.client.ClientCompat.immediateBuffers(mc);
        for (int pass = 0; pass < PASSES; pass++) { // each layer its own batch, so a later one is always drawn over an earlier one
            draw(mc, com.botwclimb.client.ClientCompat.levelPose(event), buffers.getBuffer(RenderType.debugQuads()), pass);
            buffers.endBatch(RenderType.debugQuads());
        }
    }
    //?}

    /** The layers, drawn one after another: the type is see-through, so within one batch its pieces are sorted back to front by their middles, and a band's long side (its middle half a plot away) was drawn over every mark beyond that. */
    private static final int PASSES = 3;

    private static void draw(Minecraft mc, PoseStack ps, VertexConsumer vc, int pass) {
        int plot = PlotWorld.plotAt(mc.player.blockPosition());
        Vec3 cam = com.botwclimb.client.ClientCompat.cameraPos();
        float x0 = PlotWorld.ORIGIN_X + plot * PlotWorld.SPACING + LOW, x1 = PlotWorld.ORIGIN_X + plot * PlotWorld.SPACING + HIGH, z0 = PlotWorld.ORIGIN_Z + LOW, z1 = PlotWorld.ORIGIN_Z + HIGH, y = PlotWorld.Y + 1f;
        double px = mc.player.getX(), pz = mc.player.getZ();
        if (px < x0 - NEAR || px > x1 + NEAR || pz < z0 - NEAR || pz > z1 + NEAR) return; // only on the plot or just off its edge, however far you can see
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        // the writing is one line running round the whole edge: its pattern is stretched a hair so a whole number of repeats fits the
        // way round, so it meets itself with no seam, and it creeps slowly round the plot
        float side = x1 - x0, round = 4f * side, raw = 0f;
        for (float g : GLYPHS) raw += g;
        float scale = round / Math.round(round / raw) / raw, pattern = raw * scale;
        float secs = (net.minecraft.Util.getMillis() % 3_600_000L) / 1000f, shift = com.botwclimb.client.ClientConfig.outlineMotion() ? (secs * DRIFT) % pattern : 0f;
        // the band round all four sides, 0.04 above the grass; what lies on top of something is 0.03 above it, so far off (where depth is coarse) it still wins
        // and does not vanish into the layer under it; side by side pieces that do not overlap share a height
        // the sides in order round the loop: z0 east, x1 south, z1 west, x0 north
        for (int s = 0; s < 4; s++) {
            boolean alongX = s % 2 == 0, back = s >= 2;
            float fixed = s == 0 ? z0 : s == 1 ? x1 : s == 2 ? z1 : x0;
            float from = alongX ? x0 : z0, to = alongX ? x1 : z1;
            if (pass == 0) for (int e = -1; e <= 1; e += 2) { // shaded, not flat: the silver bright by the inlay and darker at its outer edge, the inlay a touch lighter down its middle
                shade(vc, m, alongX, fixed, from - BAND, to + BAND, e * INLAY, e * BAND, BAND_RGB, BAND_EDGE, y + 0.04f);
                shade(vc, m, alongX, fixed, from - INLAY, to + INLAY, 0f, e * INLAY, INLAY_MID, INLAY_RGB, y + 0.04f);
            }
            if (pass == 1) for (float n = from + 1f; n < to - 0.5f; n += 1f) // engraved notches across the silver, one a block, as on the terminal's rim
                for (int e = -1; e <= 1; e += 2) shadeAcross(vc, m, alongX, fixed, n - 0.05f, n + 0.05f, e * INLAY, e * BAND, NOTCH_RGB, y + 0.07f);
            float g0 = s * side, lo = TILE + 0.2f, hi = side - TILE - 0.2f; // distances along the loop, and the part of this side clear of the corner tiles
            float t = (float) Math.floor((g0 + shift) / pattern) * pattern - shift - g0; // where the pattern last began, measured from this side's start
            for (int k = 0; pass == 1 && t < hi; k++) {
                float len = GLYPHS[k % GLYPHS.length] * scale, a = Math.max(t, lo), b = Math.min(t + len, hi);
                if (k % 2 == 0 && b > a) {
                    float mid = (a + b) * 0.5f, mx = alongX ? (back ? to - mid : from + mid) : fixed, mz = alongX ? fixed : (back ? to - mid : from + mid);
                    float far = (float) Math.sqrt((mx - cam.x) * (mx - cam.x) + (y - cam.y) * (y - cam.y) + (mz - cam.z) * (mz - cam.z));
                    float half = Math.min(INLAY, MARK * Math.max(1f, far / 12f)); // further off the marks widen a little (never past the inlay), so they do not thin out to nothing
                    strip(vc, m, alongX, fixed, back ? to - b : from + a, back ? to - a : from + b, half, y + 0.07f, MARK_RGB, 230);
                }
                t += len;
            }
        }
        // a silver tile at each corner with the inlay and a green stone in it, over the band
        if (pass == 2) for (float cx : new float[] {x0, x1}) for (float cz : new float[] {z0, z1}) {
            frame(vc, m, cx, cz, TILE - 0.12f, TILE, BAND_RGB, BAND_EDGE, y + 0.1f); // the silver frame, shaded like the band
            frame(vc, m, cx, cz, 0.16f, TILE - 0.12f, INLAY_MID, INLAY_RGB, y + 0.1f); // the inlay, lighter toward the stone
            for (int q = 0; q < 4; q++) { // a notch in the middle of each side of the frame
                float ox = q == 0 ? TILE - 0.06f : q == 1 ? -(TILE - 0.06f) : 0f, oz = q == 2 ? TILE - 0.06f : q == 3 ? -(TILE - 0.06f) : 0f;
                float hx = q < 2 ? 0.06f : 0.05f, hz = q < 2 ? 0.05f : 0.06f;
                vertex(vc, m, cx + ox - hx, y + 0.13f, cz + oz - hz, NOTCH_RGB, 240);
                vertex(vc, m, cx + ox + hx, y + 0.13f, cz + oz - hz, NOTCH_RGB, 240);
                vertex(vc, m, cx + ox + hx, y + 0.13f, cz + oz + hz, NOTCH_RGB, 240);
                vertex(vc, m, cx + ox - hx, y + 0.13f, cz + oz + hz, NOTCH_RGB, 240);
            }
            square(vc, m, cx, cz, 0.16f, y + 0.13f, DOT_RGB, 240);
        }
        ps.popPose();
    }

    /** A flat strip along one side, {@code half} wide to each side of the line, at height y. */
    private static void strip(VertexConsumer vc, Matrix4f m, boolean alongX, float fixed, float from, float to, float half, float y, int rgb, int alpha) {
        if (alongX) {
            vertex(vc, m, from, y, fixed - half, rgb, alpha);
            vertex(vc, m, to, y, fixed - half, rgb, alpha);
            vertex(vc, m, to, y, fixed + half, rgb, alpha);
            vertex(vc, m, from, y, fixed + half, rgb, alpha);
        } else {
            vertex(vc, m, fixed - half, y, from, rgb, alpha);
            vertex(vc, m, fixed + half, y, from, rgb, alpha);
            vertex(vc, m, fixed + half, y, to, rgb, alpha);
            vertex(vc, m, fixed - half, y, to, rgb, alpha);
        }
    }

    /** A strip along a side between two offsets from the line, its colour going smoothly from c0 at the first to c1 at the second. */
    private static void shade(VertexConsumer vc, Matrix4f m, boolean alongX, float fixed, float from, float to, float d0, float d1, int c0, int c1, float y) {
        if (alongX) {
            vertex(vc, m, from, y, fixed + d0, c0, 235);
            vertex(vc, m, to, y, fixed + d0, c0, 235);
            vertex(vc, m, to, y, fixed + d1, c1, 235);
            vertex(vc, m, from, y, fixed + d1, c1, 235);
        } else {
            vertex(vc, m, fixed + d0, y, from, c0, 235);
            vertex(vc, m, fixed + d1, y, from, c1, 235);
            vertex(vc, m, fixed + d1, y, to, c1, 235);
            vertex(vc, m, fixed + d0, y, to, c0, 235);
        }
    }

    /** A square ring round (x, z) from half-size {@code in} (colour cIn) out to {@code out} (colour cOut), shaded smoothly between them. */
    private static void frame(VertexConsumer vc, Matrix4f m, float x, float z, float in, float out, int cIn, int cOut, float y) {
        float[][] c = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int i = 0; i < 4; i++) {
            float[] a = c[i], b = c[(i + 1) % 4];
            vertex(vc, m, x + a[0] * in, y, z + a[1] * in, cIn, 240);
            vertex(vc, m, x + b[0] * in, y, z + b[1] * in, cIn, 240);
            vertex(vc, m, x + b[0] * out, y, z + b[1] * out, cOut, 240);
            vertex(vc, m, x + a[0] * out, y, z + a[1] * out, cOut, 240);
        }
    }

    /** One notch: a short piece between two offsets from the line, in one colour. */
    private static void shadeAcross(VertexConsumer vc, Matrix4f m, boolean alongX, float fixed, float from, float to, float d0, float d1, int rgb, float y) {
        shade(vc, m, alongX, fixed, from, to, d0, d1, rgb, rgb, y);
    }

    /** A flat square centred on (x, z), {@code half} to each side, at height y. */
    private static void square(VertexConsumer vc, Matrix4f m, float x, float z, float half, float y, int rgb, int alpha) {
        strip(vc, m, true, z, x - half, x + half, half, y, rgb, alpha);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, int rgb, int alpha) {
        vc.addVertex(m, x, y, z).setColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha);
    }
}
