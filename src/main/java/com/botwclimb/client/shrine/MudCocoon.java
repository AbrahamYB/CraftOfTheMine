package com.botwclimb.client.shrine;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.shrine.CocoonMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws the magic mud: a smooth cocoon round the rider that opens like a flower and snaps shut, the ring of mud the ground
 * opens into when the rider comes down, and the streaks that trail a fast rider. All of it is geometry written straight into the
 * vertex buffer each frame; nothing is a block. Coordinates are blocks above the rider's feet, y up (the layer sets that up).
 */
public final class MudCocoon {
    /** The plain mud of the ground (the pit and its lip), the wrapped dark strips of the cocoon, and the green that glows between them. */
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "textures/entity/mud_hand.png");
    public static final ResourceLocation COCOON = ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "textures/entity/mud_cocoon.png");
    public static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "textures/entity/mud_cocoon_glow.png");
    public static final ResourceLocation STREAK = ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "textures/entity/streak.png");
    /** Radius of the cocoon, and the height of its middle: a round shell, a little bigger than the rider. */
    public static final float RADIUS = 1.2f, MIDDLE = 0.9f;
    private static final int STACKS = 24, COLUMNS = 6;
    /** How far the wrapped strips stand out of the shell, as a share of its radius. */
    private static final double RIDGE = 0.045;

    private MudCocoon() {}

    /**
     * @param wrap    0..1, how much of the strips have been wound on: they climb from the feet in the direction of the painted strips,
     *                loose and wide at first and pulled tight as the last of them lands
     * @param stretch above 0 pulls it long and thin (fast), below 0 squashes it short and wide
     */
    public static void cocoon(PoseStack ps, VertexConsumer vc, int light, float wrap, float stretch) {
        PoseStack.Pose pose = ps.last();
        float loose = 1 + 0.3f * (1 - wrap) * (1 - wrap);
        float rxz = RADIUS * loose * (1 - 0.1f * stretch), ry = RADIUS * loose * (1 + 0.35f * stretch);
        double[] p = new double[3], n = new double[3];
        double petalWidth = 2 * Math.PI / CocoonMath.PETALS, colWidth = petalWidth / COLUMNS;
        for (int j = 0; j < CocoonMath.PETALS; j++) {
            double mid = j * petalWidth;
            for (int c = 0; c < COLUMNS; c++) {
                double l0 = mid - petalWidth / 2 + c * colWidth, l1 = l0 + colWidth;
                double lc = (((l0 + l1) / 2) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
                for (int i = 0; i < STACKS; i++) {
                    double f0 = Math.PI * i / STACKS, f1 = Math.PI * (i + 1) / STACKS;
                    // the wound-on edge follows the strips: everything at or below this strip line is covered
                    if (wrap < 1f && (f0 + f1) / (2 * Math.PI) - 0.5 * lc / (2 * Math.PI) > -0.5 + 1.5 * wrap) continue;
                    corner(pose, vc, light, f0, l0, mid, 0, rxz, ry, p, n);
                    corner(pose, vc, light, f0, l1, mid, 0, rxz, ry, p, n);
                    corner(pose, vc, light, f1, l1, mid, 0, rxz, ry, p, n);
                    corner(pose, vc, light, f1, l0, mid, 0, rxz, ry, p, n);
                }
            }
        }
    }

    private static void corner(PoseStack.Pose pose, VertexConsumer vc, int light, double phi, double lam, double petal, float open,
                               float rxz, float ry, double[] p, double[] n) {
        CocoonMath.point(phi, lam, petal, open, rxz, ry, rxz, MIDDLE, RIDGE, p);
        CocoonMath.normal(phi, lam, petal, open, rxz, ry, rxz, RIDGE, n);
        vc.addVertex(pose, (float) p[0], (float) p[1], (float) p[2]).setColor(255, 255, 255, 255)
                .setUv((float) (lam / (2 * Math.PI)), (float) (phi / Math.PI)).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, (float) n[0], (float) n[1], (float) n[2]);
    }

    /** Radius of the opened ground (the blocks really are taken away, see GroundHole). */
    public static final float PIT_RADIUS = 3.0f;

    /** The radius of the carved ground for a given opening, in blocks. */
    public static float pitRadius(float hole) {
        return PIT_RADIUS * hole;
    }

    /** How far the spilled liquid reaches when there is most of it, and how it is cut up. */
    private static final float SPILL_RADIUS = 2.5f;
    private static final int SPILL_SEGMENTS = 64;
    private static final float[] SPILL_RINGS = {0f, 0.12f, 0.25f, 0.4f, 0.55f, 0.7f, 0.85f, 1f};

    /**
     * A small uneven pool of the cocoon's own liquid on the ground round the rider's feet: the cocoon's texture itself, but
     * stirred, so its strips swirl and fold like something poured. Its outline is broad and messy with a few tongues, and both the
     * outline and the swirls are different every ride (the seed). It spreads out, then drains toward the rider's feet, unevenly
     * (the thin parts give way first), as the strips wind up.
     *
     * @param amount 0..1, how much of it is left
     * @param ground height of the ground surface (negative while the rider is above it)
     * @param time   game time in ticks, for the slow flow
     * @param seed   different for every ride
     */
    public static void spill(PoseStack ps, VertexConsumer vc, int light, float amount, float ground, float time, float seed) {
        PoseStack.Pose pose = ps.last();
        float[] edge = new float[SPILL_SEGMENTS + 1];
        float[] tongue = new float[3];
        for (int j = 0; j < 3; j++) tongue[j] = frac(Mth.sin(seed * (j + 1) * 12.9898f) * 43758.547f) * Mth.TWO_PI;
        for (int i = 0; i <= SPILL_SEGMENTS; i++) {
            float a = (i % SPILL_SEGMENTS) * Mth.TWO_PI / SPILL_SEGMENTS;
            float lobe = 0.62f + 0.18f * Mth.sin(a + seed) + 0.12f * Mth.sin(2 * a + seed * 2.3f) + 0.08f * Mth.sin(3 * a + seed * 3.7f) + 0.05f * Mth.sin(5 * a + seed * 5.1f);
            for (float t : tongue) { // a few long tongues of it, where it ran
                float d = Mth.wrapDegrees((a - t) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
                lobe += 0.32f * (float) Math.exp(-d * d / 0.07f);
            }
            lobe = Math.min(lobe, 1.15f);
            float ripple = 1 + 0.02f * Mth.sin(3 * a + time * 0.3f) + 0.015f * Mth.sin(5 * a - time * 0.45f);
            float left = Mth.clamp(amount * 1.6f - (1.1f - lobe) * 1.2f, 0f, 1f); // the thin parts drain first
            edge[i] = SPILL_RADIUS * lobe * ripple * left * (float) Math.sqrt(Math.min(1f, amount * 6f)); // and it all fades in from nothing
        }
        for (int i = 0; i < SPILL_SEGMENTS; i++) {
            float a0 = i * Mth.TWO_PI / SPILL_SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / SPILL_SEGMENTS;
            for (int ring = 0; ring + 1 < SPILL_RINGS.length; ring++) {
                float p0 = SPILL_RINGS[ring], p1 = SPILL_RINGS[ring + 1];
                spillVertex(pose, vc, light, ground, a0, edge[i] * p0, dome(p0, amount), seed, time);
                spillVertex(pose, vc, light, ground, a1, edge[i + 1] * p0, dome(p0, amount), seed, time);
                spillVertex(pose, vc, light, ground, a1, edge[i + 1] * p1, dome(p1, amount), seed, time);
                spillVertex(pose, vc, light, ground, a0, edge[i] * p1, dome(p1, amount), seed, time);
            }
        }
    }

    private static float frac(float v) {
        return v - Mth.floor(v);
    }

    /** How high the liquid stands at a fraction of the way out: a gentle dome, thickest in the middle. */
    private static float dome(float rho, float amount) {
        return 0.02f + 0.06f * amount * (1 - rho * rho);
    }

    /** One corner: the texture is looked up at a point stirred about by a few swirls, so the strips bend into marbling. */
    private static void spillVertex(PoseStack.Pose pose, VertexConsumer vc, int light, float ground, float angle, float radius, float height, float seed, float time) {
        float x = Mth.cos(angle) * radius, z = Mth.sin(angle) * radius;
        float flow = time * 0.012f;
        float wx = 0.55f * Mth.sin(z * 1.9f + seed * 1.3f + flow) + 0.3f * Mth.sin(x * 3.1f - z * 2.3f + seed * 2.1f) + 0.18f * Mth.sin(z * 5.3f + x * 1.7f + seed * 0.7f - flow);
        float wz = 0.55f * Mth.sin(x * 1.7f + seed * 0.9f - flow) + 0.3f * Mth.sin(z * 2.9f + x * 2.1f + seed * 3.3f) + 0.18f * Mth.sin(x * 4.9f - z * 1.3f + seed * 1.9f + flow);
        vc.addVertex(pose, x, ground + height, z).setColor(255, 255, 255, 255).setUv((x + wx) * 0.22f + 0.5f, (z + wz) * 0.22f + 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }

    /** One upward-facing quad at height y; the four corners are given as x, z pairs. */
    private static void flat(PoseStack.Pose pose, VertexConsumer vc, int light, int shade, float y,
                             float ax, float az, float bx, float bz, float cx, float cz, float dx, float dz) {
        float[][] c = {{ax, az}, {bx, bz}, {cx, cz}, {dx, dz}};
        for (float[] v : c) {
            vc.addVertex(pose, v[0], y, v[1]).setColor(shade, shade, shade, 255).setUv(v[0] * 0.2f + 0.5f, v[1] * 0.2f + 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        }
    }

    /**
     * Thin pale streaks that trail a fast rider, fading away from the cocoon: below it while rising, above it while coming down.
     *
     * @param strength 0..1, how fast the rider is going
     * @param below    true to trail underneath
     */
    public static void streaks(PoseStack ps, VertexConsumer vc, float strength, boolean below) {
        PoseStack.Pose pose = ps.last();
        int count = 9;
        for (int k = 0; k < count; k++) {
            float a = k * (Mth.TWO_PI / count) + k * 0.7f;
            float radius = 0.35f + 0.65f * ((k * 37 % 10) / 10f);
            float x = Mth.cos(a) * radius, z = Mth.sin(a) * radius;
            float length = (1.2f + 3.8f * strength) * (0.55f + 0.45f * ((k * 53 % 10) / 10f));
            float start = below ? MIDDLE - RADIUS * 0.9f : MIDDLE + RADIUS * 0.9f, end = start + (below ? -length : length);
            int alpha = (int) (150 * Mth.clamp(strength, 0f, 1f));
            for (int plane = 0; plane < 2; plane++) {
                float dx = plane == 0 ? 0.025f : 0, dz = plane == 0 ? 0 : 0.025f;
                streakVertex(pose, vc, x - dx, start, z - dz, alpha);
                streakVertex(pose, vc, x + dx, start, z + dz, alpha);
                streakVertex(pose, vc, x + dx, end, z + dz, 0);
                streakVertex(pose, vc, x - dx, end, z - dz, 0);
            }
        }
    }

    private static void streakVertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, int alpha) {
        vc.addVertex(pose, x, y, z).setColor(232, 240, 252, alpha).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0).setNormal(pose, 0, 1, 0);
    }
}
