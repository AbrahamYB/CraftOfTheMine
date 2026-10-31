package com.botwclimb.client.shrine.totk;

import com.botwclimb.BotwClimb;
import com.botwclimb.client.shrine.MudHandClient;
import com.botwclimb.common.shrine.TerminalRide;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Tears of the Kingdom look of the terminal ride (ClientConfig.RideStyle.TOTK), after two screen recordings of its Ascend
 * travel (beat list and measurements: notes/totk-ride.md). The mod calls these hooks, and only these, while that style is chosen.
 * <p>
 * The beats: soft green sparks and a glow gather at the rider's feet (phase 1) while two big ribbons, each a bundle of thin
 * strands, grow into a double helix round them; a hard cut into a dark teal void with a soft column of light, drifting stars,
 * flame-like spikes (going up) or hanging drips (coming home) and the green double helix (drawn on the HUD so that the real
 * world, the world change and the loading screen are all hidden behind it); then a hard cut back to the world with, going to a
 * plot, a pale haze, and a glow on the ground that throws curved green slashes and sparks. There are no rings or bubbles.
 * <p>
 * Clock: everything on the HUD runs on wall-clock ticks (50 ms), because the game tick stands still during the loading screen.
 * The world layer uses game time, so that other players' rides can be watched too.
 */
public final class TotkRide {
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "textures/totk/glow.png");
    /** Ticks into phase 2 at which the footage cuts from the world (ribbons) to the void; a Return has no ribbons, it cuts as the sinking starts. */
    private static final float CUT_UP = 10f, CUT_HOME = TerminalRide.SINK_DELAY;
    /** The void ends (hard cut back to the world) when the rider is this close above the ground they arrive on. */
    private static final float LAND_HEIGHT = 4.5f;

    /** Where the void ends: going to a plot the rider comes into view just above the ground; coming home the void lasts until they stand on the floor. */
    private static float land() {
        return kind == 1 ? 0.6f : LAND_HEIGHT;
    }

    // state of the rider's own ride, kept by sync()
    private static boolean voidOn, drawingHud;
    private static float voidClk, cutClk = -1e9f, phaseClk, lastRide = -1e9f;
    private static int phase;
    private static long rideSince = -1;
    private static int kind;

    /** Per rider: {angle, degrees per tick} of the spin that winds the ribbons up. */
    private static final java.util.Map<Integer, float[]> SPIN = new java.util.HashMap<>();

    /** Degrees per tick the rider turns at the quickest (360 / 7 = a turn every 2.6 seconds). */
    private static final float SPIN_MAX = 7f;

    /** The rider's orientation in the void going up: the baked lean plus a gentle warp (a small wobble about two axes). */
    /** The rider's orientation in the void going up: upright, head up, facing away from the camera. No tilt, no turn. */
    private static Quaternionf diveRotation(float t) {
        return new Quaternionf().rotateZ((float) Math.PI);
    }

    /** Where the rider is on the screen (middle, as the third-person camera holds them) and how big (pixels per block, as a share of the height). */
    private static final float RIDER_Y = 0.6f, RIDER_SIZE = 0.14f;

    /**
     * The rider's turn inside the void, kept on the wall clock: the game's own ticks and partial-tick fractions stand still or jump
     * about during the world change and the loading screen, and a turn measured with them rattles. Starts from where the world's turn
     * had got to at the cut.
     */
    private static float voidAngle, voidAngleClk, voidAngleFor = Float.NaN;

    /** True while the rider is being drawn into the void on the HUD (that draw goes through the player renderer, whose events must leave it alone). */
    static boolean drawingHud() {
        return drawingHud;
    }

    // ------------------------------------------------------------------------------------------------ the dive (going to a plot)

    /** One sample per tick of the dive cycle: tilt (degrees), right arm, left arm, right leg, left leg, body lean, right leg out, left leg out (radians). Baked in Blender, see tools/blender_dive.py. */
    private static float[][] diveSamples;
    private static int divePeriod = 20;
    private static final float[][] DIVE_FALLBACK = {{0f, -3.05f, -3.05f, 0f, 0f, 0f, 0f, 0f, 0.08f, -0.08f}, {0f, -3.05f, -3.05f, 0f, 0f, 0f, 0f, 0f, 0.08f, -0.08f}};

    private static void loadDive() {
        if (diveSamples != null) return;
        try (var in = TotkRide.class.getResourceAsStream("/assets/botwclimb/totk/dive.json")) {
            var o = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var arr = o.getAsJsonArray("samples");
            float[][] out = new float[arr.size()][];
            for (int i = 0; i < out.length; i++) {
                var e = arr.get(i).getAsJsonObject();
                out[i] = new float[] {e.get("tilt").getAsFloat(), e.get("armR").getAsFloat(), e.get("armL").getAsFloat(), e.get("legR").getAsFloat(), e.get("legL").getAsFloat(), e.get("lean").getAsFloat(), e.get("legRz").getAsFloat(), e.get("legLz").getAsFloat(), e.get("armRz").getAsFloat(), e.get("armLz").getAsFloat()};
            }
            divePeriod = o.get("period").getAsInt();
            diveSamples = out;
        } catch (Exception e) {
            BotwClimb.LOGGER.warn("The Tears of the Kingdom dive could not be read; the rider holds still", e);
            diveSamples = DIVE_FALLBACK;
            divePeriod = 1;
        }
    }

    /** The dive at a time in ticks (it loops): {tilt, armR, armL, legR, legL, lean, legRz, legLz, armRz, armLz}. */
    static float[] dive(float ticks) {
        loadDive();
        float t = ((ticks % divePeriod) + divePeriod) % divePeriod;
        int i = (int) t, j = Math.min(i + 1, diveSamples.length - 1);
        float f = t - i;
        float[] out = new float[10];
        for (int k = 0; k < 10; k++) out[k] = diveSamples[Math.min(i, diveSamples.length - 1)][k] * (1 - f) + diveSamples[j][k] * f;
        return out;
    }

    /**
     * Poses a rider going to a plot (called from the player model mixin): in the world, once the ribbons start, the right arm is
     * stretched up and the legs sway; in the void (the rider drawn on the HUD) the whole dive plays. Anyone else is left alone.
     */
    public static void pose(net.minecraft.client.model.PlayerModel<?> m, AbstractClientPlayer p, float pt) {
        if (com.botwclimb.client.ClientConfig.style() != com.botwclimb.client.ClientConfig.RideStyle.TOTK) return;
        MudHandClient.State s = MudHandClient.get(p.getId());
        if (s == null) return;
        if (s.kind() == 1) { // the Return: dragged down, arms trailing up behind, legs together, everything lagging the pull
            if (!drawingHud) return;
            float t = clk() - voidClk, y = yank(t); // surprised and struggling: arms and legs scrabble a little, harder on every tug
            float a1 = (float) Math.sin(t * 0.9f), a2 = (float) Math.sin(t * 1.1f + 1.7f), a3 = (float) Math.sin(t * 0.7f + 0.6f);
            m.rightArm.xRot = -2.1f;
            m.leftArm.xRot = -2.1f;
            m.rightArm.zRot = 0.5f + (0.14f + 0.1f * y) * a2; // only sideways: the arms fan in and out, never forward and back
            m.leftArm.zRot = -0.5f - (0.14f + 0.1f * y) * a1;
            m.rightLeg.xRot = 0f;
            m.leftLeg.xRot = 0f;
            m.rightLeg.zRot = -0.05f;
            m.leftLeg.zRot = 0.05f;
            m.body.xRot = 0f;
            m.rightSleeve.copyFrom(m.rightArm);
            m.leftSleeve.copyFrom(m.leftArm);
            m.rightPants.copyFrom(m.rightLeg);
            m.leftPants.copyFrom(m.leftLeg);
            m.jacket.copyFrom(m.body);
            return;
        }
        if (s.kind() != 0) return;
        float[] d;
        if (drawingHud) {
            d = dive(clk() - voidClk); // the baked hold: right arm up, legs hanging, a slow breath
        } else {
            float now = p.level().getGameTime() + pt;
            if (!(s.phase() == 1 && now - s.since() > 28f || s.phase() == 2)) return;
            d = dive(now); // the same hold in the world
        }
        m.rightArm.xRot = d[1];
        m.rightArm.zRot = d[8]; // the arms sweep out and back with the dive
        m.leftArm.zRot = d[9];
        m.leftArm.xRot = d[2];
        m.rightLeg.xRot = d[3];
        m.leftLeg.xRot = d[4];
        m.body.xRot = d[5];
        m.rightLeg.zRot = d[6];
        m.leftLeg.zRot = d[7];
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
        m.jacket.copyFrom(m.body);
    }

    private TotkRide() {}

    /** Where the rider's middle is on the screen, in pixels; on the Return they are hauled in from below and tugged down on every pull. */
    private static float riderY(int h, float t) {
        return h * (RIDER_Y + (kind == 1 ? 0.012f * yank(t) + 0.3f * (1f - smooth(t / 12f)) : 0f));
    }

    /** The Return's pull: glowing cords from the rider's feet down to the bottom of the screen, snapping taut on every tug. */
    private static void tethers(VertexConsumer vc, Matrix4f m, int w, int h, float t) {
        float y0 = riderY(h, t) + h * 0.13f, pull = 0.35f + 0.65f * yank(t);
        for (int c = -2; c <= 2; c++) {
            float x0 = w * 0.5f + c * w * 0.006f, prevX = x0, prevY = y0;
            for (int i = 1; i <= 24; i++) {
                float f = i / 24f, y = y0 + (h - y0) * f;
                float x = w * 0.5f + c * w * (0.006f + 0.03f * f) + (1f - pull) * w * 0.02f * (float) Math.sin(f * 9f - t * 0.6f + c);
                float hw = w * (0.004f + 0.003f * f) * (c == 0 ? 1.6f : 1f);
                int col = argb((int) ((c == 0 ? 230 : 120) * (1f - 0.6f * f) * pull), c == 0 ? 220 : 90, 255, c == 0 ? 245 : 215);
                tri(vc, m, prevX - hw, prevY, col, prevX + hw, prevY, col, x + hw, y, col, x - hw, y, col);
                prevX = x; prevY = y;
            }
        }
    }

    /** The haul on the Return: a sharp tug that eases off, 0..1, repeating about every 0.8 s. */
    private static float yank(float t) {
        float ph = (t % 16f) / 16f;
        return ph < 0.12f ? ph / 0.12f : 1f - smooth((ph - 0.12f) / 0.88f);
    }

    /** The angle in degrees the rider's model is turned by this frame (0 for anyone who is not spinning). */
    public static float spin(int entityId, float partial) {
        float[] v = SPIN.get(entityId);
        return v == null ? 0f : v[0] + v[1] * partial;
    }

    private static float clk() {
        return net.minecraft.Util.getMillis() / 50f;
    }

    /** Follows the rider's own ride: when its phase began and when the void starts and ends. Safe to call as often as needed. */
    private static MudHandClient.State sync(Minecraft mc) {
        float c = clk();
        boolean loading = mc.screen instanceof ReceivingLevelScreen;
        MudHandClient.State s = mc.player == null ? null : MudHandClient.get(mc.player.getId());
        if (s == null) {
            if (loading && c - lastRide < 160f) { // the world change: the rider has no state for a moment, but the void must not drop
                if (!voidOn) { voidOn = true; voidClk = c; }
            } else {
                voidOn = false;
                phase = 0;
            }
            return null;
        }
        lastRide = c;
        if (s.since() != rideSince) {
            rideSince = s.since();
            voidOn = false;
            cutClk = -1e9f;
            phase = 0;
        }
        kind = s.kind();
        if (s.phase() != phase) {
            phase = s.phase();
            phaseClk = c;
        }
        float tp = c - phaseClk, h = (float) (mc.player.getY() - s.startY());
        boolean want = phase == 3 || phase == 2 && tp >= (kind == 1 ? CUT_HOME : CUT_UP) || phase == 4 && h > land() || loading;
        if (want && !voidOn) {
            voidOn = true;
            voidClk = c;
        } else if (!want && voidOn) {
            voidOn = false;
            cutClk = c;
        }
        return s;
    }

    // ------------------------------------------------------------------------------------------------ world layer

    /** Drawn for every player riding a terminal (the rider and anyone watching), in the same space as the mud layer: origin at the feet, y up. */
    public static void layer(PoseStack ps, MultiBufferSource buffer, int light, AbstractClientPlayer p, MudHandClient.State s, float partial) {
        if (drawingHud) return; // the rider drawn into the void on the HUD must not carry the world effects with them
        float now = p.level().getGameTime() + partial, t = now - s.since(), tp = now - s.phaseSince();
        float h = (float) (Mth.lerp(partial, p.yo, p.getY()) - s.startY());
        boolean home = s.kind() == 1;
        ps.pushPose();
        ps.translate(0, 1.501, 0);
        ps.scale(-1, -1, 1);
        VertexConsumer vc = buffer.getBuffer(RenderType.eyes(GLOW));
        PoseStack.Pose pose = ps.last();
        switch (s.phase()) {
            case 1 -> { // a soft glow gathers at the feet; the ribbons grow in round the rider over the last ticks
                if (home) whirl(vc, pose, now, smooth(t / 6f), smooth(t / 42f), 1f); // the exit marker on the ground, round the rider's feet
                else disc(vc, pose, 0.03f, 1.0f + 0.2f * (float) Math.sin(now * 0.25f), 90, 255, 200, (int) (150 * smooth((t - 4f) / 24f)));
                float len = home ? 42f : 44f, k = smooth((t - (len - 16f)) / 14f);
                if (!home && k > 0.01f) helix(vc, pose, home ? -now : now, k, 70f, 235f, 235f);
            }
            case 2 -> {
                float cut = home ? CUT_HOME : CUT_UP, k = smooth(tp / cut);
                if (home) whirl(vc, pose, now, 1f, 1f, 1f - k * 0.3f);
                else disc(vc, pose, 0.03f, 1.2f, 70, 235, 235, (int) (170 * (1f - k * 0.5f)));
                if (!home) helix(vc, pose, now, 1f, Mth.lerp(k, 70f, 80f), Mth.lerp(k, 235f, 255f), Mth.lerp(k, 235f, 90f));
            }
            case 4 -> {
                if (h <= land() + 0.5f) landing(vc, pose, Math.max(0f, (land() - h) / 1.2f), home, s);
            }
            case 5 -> landing(vc, pose, 3.5f + tp, home, s);
            default -> { }
        }
        ps.popPose();
    }

    /** Arrival: a glow on the ground that throws curved green slashes and sparks; going to a plot it waits a moment first, then bursts. */
    private static void landing(VertexConsumer vc, PoseStack.Pose pose, float age, boolean home, MudHandClient.State s) {
        if (home || age >= 8f) {
            float a0 = home ? age : age - 8f, a = 1f - smooth((a0 - 4f) / 18f);
            disc(vc, pose, 0.03f, 0.5f + 0.9f * smooth(a0 / 8f), 120, 255, 230, (int) (240 * a));
            swirl(vc, pose, a0, 1.0f, (int) (255 * (1f - smooth((a0 - 14f) / 10f))));
        } else {
            disc(vc, pose, 0.03f, 0.8f + 0.05f * (float) Math.sin(age), 90, 255, 210, 150);
        }
    }

    // ------------------------------------------------------------------------------------------------ geometry

    private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float w, int r, int g, int b, int a) {
        vc.addVertex(pose, x, y, z).setColor(r, g, b, Mth.clamp(a, 0, 255)).setUv(u, w).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    /** A quad, written both ways round (the glow type culls back faces). */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float[] p, int r, int g, int b, int a, float u0, float u1) {
        v(vc, pose, p[0], p[1], p[2], u0, 0.5f, r, g, b, a);
        v(vc, pose, p[3], p[4], p[5], u1, 0.5f, r, g, b, a);
        v(vc, pose, p[6], p[7], p[8], u1, 0.5f, r, g, b, a);
        v(vc, pose, p[9], p[10], p[11], u0, 0.5f, r, g, b, a);
        v(vc, pose, p[9], p[10], p[11], u0, 0.5f, r, g, b, a);
        v(vc, pose, p[6], p[7], p[8], u1, 0.5f, r, g, b, a);
        v(vc, pose, p[3], p[4], p[5], u1, 0.5f, r, g, b, a);
        v(vc, pose, p[0], p[1], p[2], u0, 0.5f, r, g, b, a);
    }

    /**
     * The exit marker of a Return, copied from down.mov (0 - 2.8 s): two thin concentric cyan rings on the ground round the rider's
     * feet, their edges ragged and sparkling like disturbed water, thicker and brighter in a few places (which creep round the ring),
     * inside a faint blue pool, and a soft glow just outside. The rings draw slowly in toward the rider while it lasts.
     *
     * @param grow     0..1, how far it has appeared (it opens out from the middle)
     * @param contract 0..1, how far the rings have drawn in
     * @param fade     0..1, overall strength
     */
    private static void whirl(VertexConsumer vc, PoseStack.Pose pose, float now, float grow, float contract, float fade) {
        float k = 1f - 0.42f * contract;
        disc(vc, pose, 0.022f, 1.9f * k * grow, 30, 150, 200, (int) (60 * fade * grow));   // the soft glow outside
        disc(vc, pose, 0.026f, 1.3f * k * grow, 40, 190, 230, (int) (70 * fade * grow));   // the pool
        for (int ring = 0; ring < 2; ring++) {
            float base = (ring == 0 ? 1.32f : 0.82f) * k * grow, seed = ring * 2.7f;
            int n = 72;
            for (int i = 0; i < n; i++) {
                float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
                float r0 = base * (1f + 0.05f * (float) Math.sin(a0 * 3 + now * 0.25f + seed) + 0.035f * (float) Math.sin(a0 * 7 - now * 0.4f + seed)),
                        r1 = base * (1f + 0.05f * (float) Math.sin(a1 * 3 + now * 0.25f + seed) + 0.035f * (float) Math.sin(a1 * 7 - now * 0.4f + seed));
                // thickness: thin everywhere, swelling into blobs in a few places that creep round
                float blob = (float) Math.pow(0.5 + 0.5 * Math.sin(a0 * 4 - now * (ring == 0 ? 0.11f : -0.14f) + seed), 3);
                float w = 0.02f + 0.075f * blob;
                float tw = 0.65f + 0.35f * (float) Math.sin(a0 * 11 + now * 0.7f + seed); // sparkle
                int al = (int) ((120 + 135 * blob) * tw * fade * grow);
                int r = (int) (60 + 70 * blob), g = (int) (225 + 30 * blob), b = 245;
                quad(vc, pose, new float[]{(r0 - w) * (float) Math.cos(a0), 0.03f, (r0 - w) * (float) Math.sin(a0), (r0 + w) * (float) Math.cos(a0), 0.03f, (r0 + w) * (float) Math.sin(a0),
                        (r1 + w) * (float) Math.cos(a1), 0.03f, (r1 + w) * (float) Math.sin(a1), (r1 - w) * (float) Math.cos(a1), 0.03f, (r1 - w) * (float) Math.sin(a1)}, r, g, b, al, 0f, 1f);
            }
            for (int d = 0; d < 36; d++) { // droplets thrown just off the ring, sparkling
                float an = hash(d + ring * 50, 71) * Mth.TWO_PI, off = (hash(d, 72) - 0.5f) * 0.28f, rr = base + off;
                float sz = 0.012f + 0.03f * hash(d, 73);
                float x = rr * (float) Math.cos(an), z = rr * (float) Math.sin(an);
                int al = (int) (200 * (0.5f + 0.5f * (float) Math.sin(now * 0.6f + d * 2.1f)) * fade * grow * (1f - Math.abs(off) * 2.5f));
                quad(vc, pose, new float[]{x - sz, 0.035f, z - sz, x + sz, 0.035f, z - sz, x + sz, 0.035f, z + sz, x - sz, 0.035f, z + sz}, 150, 250, 255, al, 0.3f, 0.7f);
            }
        }
    }

    /** A flat glowing blob on the ground (the soft texture does the fading). */
    private static void disc(VertexConsumer vc, PoseStack.Pose pose, float y, float rad, int r, int g, int b, int a) {
        if (a <= 2) return;
        float[] p = {-rad, y, -rad, rad, y, -rad, rad, y, rad, -rad, y, rad};
        for (int i = 0; i < 4; i++) { // full texture, whole square
            v(vc, pose, p[i * 3], p[i * 3 + 1], p[i * 3 + 2], i == 0 || i == 3 ? 0f : 1f, i < 2 ? 0f : 1f, r, g, b, a);
        }
        for (int i = 3; i >= 0; i--) {
            v(vc, pose, p[i * 3], p[i * 3 + 1], p[i * 3 + 2], i == 0 || i == 3 ? 0f : 1f, i < 2 ? 0f : 1f, r, g, b, a);
        }
    }

    /**
     * The double helix in the world: two big ribbons crossing round the rider, each a bright core in a soft halo with a handful of
     * thin strands wound beside it, cyan turning to green. {@code grow} (0..1) is how much of it has unwound upward.
     */
    private static void helix(VertexConsumer vc, PoseStack.Pose pose, float now, float grow, float r, float g, float b) {
        int n = 96;
        float height = 11f * grow;
        for (int rib = 0; rib < 2; rib++) {
            for (int st = -1; st < 5; st++) { // -1 is the halo, 0 the core, the rest a few strands wound close to it
                boolean halo = st < 0, core = st == 0;
                float offA = st <= 0 ? 0f : (st - 2.5f) * 0.045f * (rib == 0 ? 1 : -1), offR = st <= 0 ? 0f : 0.07f * (float) Math.sin(st * 2.3f);
                float wd0 = halo ? 0.24f : core ? 0.075f : 0.02f;
                int al = halo ? 55 : core ? 255 : 190;
                int cr = halo ? (int) (r * 0.45f) : core ? (int) (150 + r * 0.4f) : (int) (r * 0.8f + 30), cg = halo ? (int) (g * 0.95f) : 255, cb = halo ? (int) (b * 0.7f) : core ? 225 : (int) (b * 0.9f + 20);
                for (int i = 0; i < n; i++) {
                    float[] flat = new float[12], up = new float[12];
                    float wdMid = 0f;
                    for (int e = 0; e < 2; e++) {
                        float y = -0.6f + (i + e) * height / n;
                        float[] pt = strand(rib, y, now, offA, offR, st);
                        // thick where it is thick, thin where it is thin: the width wanders along the strand and over time
                        float wd = wd0 * (0.55f + 0.9f * (0.5f + 0.5f * (float) Math.sin(y * 2.1f + st * 1.3f + rib * 2f - now * 0.17f)));
                        wdMid = wd;
                        int o = e == 0 ? 0 : 6;
                        flat[o] = pt[0] - pt[2] * wd; flat[o + 1] = y; flat[o + 2] = pt[1] - pt[3] * wd;
                        flat[o + 3] = pt[0] + pt[2] * wd; flat[o + 4] = y; flat[o + 5] = pt[1] + pt[3] * wd;
                        up[o] = pt[0]; up[o + 1] = y - wd; up[o + 2] = pt[1];
                        up[o + 3] = pt[0]; up[o + 4] = y + wd; up[o + 5] = pt[1];
                    }
                    float fade = 1f - (float) i / n * 0.6f; // thinner air higher up
                    int alpha = (int) (al * fade * Math.min(1f, grow * 3f));
                    quad(vc, pose, new float[]{flat[0], flat[1], flat[2], flat[3], flat[4], flat[5], flat[9], flat[10], flat[11], flat[6], flat[7], flat[8]}, cr, cg, cb, alpha, halo ? 0.2f : 0f, halo ? 0.8f : 1f);
                    quad(vc, pose, new float[]{up[0], up[1], up[2], up[3], up[4], up[5], up[9], up[10], up[11], up[6], up[7], up[8]}, cr, cg, cb, alpha, halo ? 0.2f : 0f, halo ? 0.8f : 1f);
                }
            }
        }
        // the grain: a great many tiny specks, densest against the ribbon and thinning out away from it, so together they read as a
        // pixelated gradient hugging the edge (on both sides), not as bits lying about
        for (int rib = 0; rib < 2; rib++) {
            for (int d = 0; d < 1100; d++) {
                float y = -0.6f + height * hash(d + rib * 1500, 61);
                float[] pt = strand(rib, y, now, 0f, 0f, 0);
                float u = hash(d, 62), side = hash(d, 66) < 0.5f ? -1f : 1f;
                float out = side * (0.05f + 0.3f * u * u * u); // cubed: most are very close to the ribbon
                float size = 0.004f + 0.012f * hash(d, 63) * hash(d, 63);
                float x = pt[0] + pt[2] * out, z = pt[1] + pt[3] * out, yy = y + (hash(d, 65) - 0.5f) * 0.12f;
                int a = (int) ((40 + 190 * (1f - u * u)) * (0.55f + 0.45f * (float) Math.sin(now * 0.5f + d * 1.3f)) * Math.min(1f, grow * 3f));
                quad(vc, pose, new float[]{x - size, yy - size, z, x + size, yy - size, z, x + size, yy + size, z, x - size, yy + size, z}, 110, 255, 150, a, 0.3f, 0.7f);
                quad(vc, pose, new float[]{x, yy - size, z - size, x, yy - size, z + size, x, yy + size, z + size, x, yy + size, z - size}, 110, 255, 150, a, 0.3f, 0.7f);
            }
        }
    }

    /**
     * Where a strand of a ribbon is at height y: {x, z, radial direction x, radial direction z}. The ribbons are not even: the angle
     * and radius wander along their length and drift in time, so the loops swell and pinch the way the real ones do.
     */
    private static float[] strand(int rib, float y, float now, float offA, float offR, int st) {
        float an = rib * (float) Math.PI + y * 0.62f - now * 0.3f + offA + 0.15f * (float) Math.sin(y * 1.7f + st)
                + 0.11f * (float) Math.sin(y * 2.9f + st * 1.9f + rib) + 0.07f * (float) Math.sin(y * 5.3f + now * 0.25f + st * 3.7f);
        float rad = 1.5f + 0.3f * (float) Math.sin(y * 0.5f + now * 0.15f) + offR + 0.22f * (float) Math.sin(y * 1.3f + rib * 2f - now * 0.1f) + 0.08f * (float) Math.sin(y * 4.1f + st);
        float cx = (float) Math.cos(an), cz = (float) Math.sin(an);
        return new float[] {rad * cx, rad * cz, cx, cz};
    }

    /** Curved green slashes lying round the feet, quick and tapered at both ends, with the green fading to cyan outward (the sparks of the landing). */
    private static void swirl(VertexConsumer vc, PoseStack.Pose pose, float age, float rad, int a) {
        if (a <= 2) return;
        for (int arc = 0; arc < 5; arc++) {
            float baseR = rad * (0.55f + 0.18f * arc), start = arc * 1.3f + age * 0.35f * (arc % 2 == 0 ? 1 : -1), span = 1.6f + 0.3f * (arc % 2);
            float rr = baseR * (1f + 0.15f * (float) Math.sin(age * 0.3f + arc));
            int n = 12;
            for (int i = 0; i < n; i++) {
                float[] p = new float[12];
                float f0 = (float) i / n, f1 = (float) (i + 1) / n;
                for (int e = 0; e < 2; e++) {
                    float f = e == 0 ? f0 : f1;
                    float an = start + span * f, taper = (float) Math.sin(f * Math.PI), wd = 0.17f * taper;
                    float y = 0.05f + 0.35f * taper * (0.6f + 0.4f * smooth(age / 10f)) + arc * 0.04f, cx = (float) Math.cos(an), cz = (float) Math.sin(an);
                    int o = e == 0 ? 0 : 6;
                    p[o] = (rr - wd) * cx; p[o + 1] = y; p[o + 2] = (rr - wd) * cz;
                    p[o + 3] = (rr + wd) * cx; p[o + 4] = y + 0.04f; p[o + 5] = (rr + wd) * cz;
                }
                float k = arc / 4f;
                quad(vc, pose, new float[]{p[0], p[1], p[2], p[3], p[4], p[5], p[9], p[10], p[11], p[6], p[7], p[8]},
                        (int) Mth.lerp(k, 90f, 70f), 255, (int) Mth.lerp(k, 130f, 235f), (int) (a * (float) Math.sin((f0 + f1) * 0.5f * Math.PI)), 0f, 1f);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ HUD

    /** Screen-space effects of the rider's own ride (white-outs, fades, speed lines), after the HUD; also called over the world-loading screen. */
    public static void hud(GuiGraphics g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        MudHandClient.State s = sync(mc);
        float c = clk();
        int w = g.guiWidth(), h = g.guiHeight();
        if (s == null && !voidOn) return;
        Matrix4f m = g.pose().last().pose();
        if (voidOn) {
            float age = c - voidClk;
            if (voidAngleFor != voidClk) { // a new void: carry on from the world's turn
                voidAngleFor = voidClk;
                voidAngle = mc.player == null ? 0f : spin(mc.player.getId(), 0f);
                voidAngleClk = c;
            }
            voidAngleClk = c;
            drawVoid(g, m, w, h, age, c);
            g.flush();
            if (kind != 1) drawHelix3d(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), c, kind == 1 ? -1f : 1f);
            if (!(mc.screen instanceof ReceivingLevelScreen) && mc.player != null) rider(g, mc, w, h, partial);
            return;
        }
        if (s == null) return;
        float tp = c - phaseClk, after = c - cutClk;
        float dim = 0f, haze = 0f, tint = 0f, flash = 0f;
        switch (s.phase()) {
            case 1 -> dim = s.kind() == 1 ? 0.15f * smooth((tp - 20f) / 20f) : 0.45f * smooth((tp - 6f) / 8f); // the scene darkens while the glyphs gather
            case 2 -> dim = s.kind() == 1 ? 0.15f : 0.45f * (1f - 0.7f * smooth(tp / CUT_UP)); // the ribbons arrive in a dim room
            default -> { }
        }
        if (cutClk > 0 && after < 60f) { // back in the world: a pale haze that thins out, over a lasting green tint
            if (s.kind() == 0) {
                haze = 0.55f * (1f - smooth(after / 8f));
                tint = 0.3f * (1f - smooth(after / 45f));
            } else {
                flash = 0.35f * (1f - smooth(after / 5f));
            }
        }
        if (dim > 0.002f) g.fill(0, 0, w, h, argb((int) (dim * 255), 0, 12, 10));
        if (haze > 0.002f) g.fillGradient(0, 0, w, h, argb((int) (haze * 255), 200, 235, 205), argb((int) (haze * 200), 170, 220, 190));
        if (tint > 0.002f) g.fill(0, 0, w, h, argb((int) (tint * 255), 50, 120, 80));
        if (flash > 0.002f) g.fill(0, 0, w, h, argb((int) (flash * 255), 150, 255, 235));
        if (phase == 2 && kind == 0 && tp < CUT_UP) { // the ribbons' glow bleeding over the room
            float k = 0.12f * smooth(tp / 4f);
            g.fillGradient(0, 0, w, h, argb((int) (k * 255), 60, 230, 220), argb(0, 60, 230, 220));
        }
    }

    private static int argb(int a, int r, int g, int b) {
        return Mth.clamp(a, 0, 255) << 24 | r << 16 | g << 8 | b;
    }

    private static float hash(int i, int salt) {
        int n = i * 73856093 ^ salt * 19349663;
        n = (n << 13) ^ n;
        return ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / (float) 0x7fffffff;
    }

    /** One screen quad with a colour at each corner. The gui type culls back faces, so the corners are written counter-clockwise whichever way they were given. */
    private static void tri(VertexConsumer vc, Matrix4f m, float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        float area = x0 * y1 - x1 * y0 + x1 * y2 - x2 * y1 + x2 * y3 - x3 * y2 + x3 * y0 - x0 * y3;
        if (area <= 0) { // the order vanilla's own fills use: top-left, bottom-left, bottom-right, top-right
            vc.addVertex(m, x0, y0, 0).setColor(c0);
            vc.addVertex(m, x1, y1, 0).setColor(c1);
            vc.addVertex(m, x2, y2, 0).setColor(c2);
            vc.addVertex(m, x3, y3, 0).setColor(c3);
        } else {
            vc.addVertex(m, x3, y3, 0).setColor(c3);
            vc.addVertex(m, x2, y2, 0).setColor(c2);
            vc.addVertex(m, x1, y1, 0).setColor(c1);
            vc.addVertex(m, x0, y0, 0).setColor(c0);
        }
    }

    /** A soft elliptical blob, brightest at its middle and gone at its edge. */
    private static void radial(VertexConsumer vc, Matrix4f m, float cx, float cy, float rx, float ry, int colour) {
        int edge = colour & 0x00FFFFFF, n = 20;
        for (int i = 0; i < n; i++) {
            double a0 = i * 2 * Math.PI / n, a1 = (i + 1) * 2 * Math.PI / n;
            tri(vc, m, cx, cy, colour, cx + rx * (float) Math.cos(a0), cy + ry * (float) Math.sin(a0), edge, cx + rx * (float) Math.cos(a1), cy + ry * (float) Math.sin(a1), edge, cx, cy, colour);
        }
    }

    /** A band of light that is brightest down its middle and fades to nothing at both sides (no hard edges). */
    private static void softColumn(VertexConsumer vc, Matrix4f m, float cx, float half, int h, int colour) {
        int edge = colour & 0x00FFFFFF;
        tri(vc, m, cx - half, 0, edge, cx, 0, colour, cx, h, colour, cx - half, h, edge);
        tri(vc, m, cx, 0, colour, cx + half, 0, edge, cx + half, h, edge, cx, h, colour);
    }

    /** The void: dark teal, a burst at the cut (spikes rising from below going up, drips hanging from above coming home), soft light, stars, the double helix. */
    private static void drawVoid(GuiGraphics g, Matrix4f m, int w, int h, float age, float time) {
        // base: black at the cut, teal within 7 ticks (footage: (0,70,60) at rest)
        float bk = smooth(age / 7f), dir = kind == 1 ? -1f : 1f;
        // everything is raw quads in one buffer, drawn in this order (no fills: they may or may not flush the buffer, and the order must be certain)
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        int base = argb(255, 0, (int) (bk * 44), (int) (bk * 40));
        tri(vc, m, 0, 0, base, w, 0, base, w, h, base, 0, h, base);
        float cx = w / 2f;
        // darker toward the edges
        int shade = argb(150, 0, 10, 9), none0 = argb(0, 0, 10, 9);
        tri(vc, m, 0, 0, shade, w * 0.3f, 0, none0, w * 0.3f, h, none0, 0, h, shade);
        tri(vc, m, w, 0, shade, w * 0.7f, 0, none0, w * 0.7f, h, none0, w, h, shade);
        softColumn(vc, m, cx, w * 0.2f, h, argb(70, 40, 230, 170));
        for (int i = 0; i < 9; i++) { // faint streaks of light drifting in the column
            float x = cx + (hash(i, 41) - 0.5f) * w * 0.34f + (float) Math.sin(time * 0.02f + i) * w * 0.01f, hw = w * (0.012f + 0.03f * hash(i, 42));
            softColumn(vc, m, x, hw, h, argb((int) (18 + 30 * hash(i, 43)), 70, 255, 200));
        }
        if (kind == 1) tethers(vc, m, w, h, time - voidClk);
        float burst = 1f - smooth(age / 9f);
        if (burst > 0.01f) softColumn(vc, m, cx, w * 0.3f, h, argb((int) (160 * burst), 90, 255, 220));
        // stars: every size and brightness, each with its own glow, twinkling out of step
        for (int i = 0; i < 150; i++) {
            float drift = hash(i, 2) + dir * time * 0.029f * (kind == 1 ? 2.4f : 1f) * (0.7f + 0.6f * hash(i, 3)); // measured: the stars cross about 0.6 screens a second, down going up and up going home
            float x = hash(i, 1) * w, y = (drift - (float) Math.floor(drift)) * h;
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(time * (0.1f + 0.2f * hash(i, 6)) + i * 1.7f));
            float sz = 0.6f + 2.4f * hash(i, 4) * hash(i, 4) * hash(i, 4), mix = hash(i, 7);
            int r = (int) (120 + 90 * mix), gr = 255, b = (int) (170 + 70 * mix), a = (int) ((50 + 190 * hash(i, 8)) * tw);
            if (sz > 1.3f) radial(vc, m, x, y, sz * 5f, sz * 5f, argb(a / 4, r, gr, b)); // the bigger ones glow
            float tail = kind == 1 ? sz * 9f : 0f; // going down fast, the stars stretch into streaks trailing below them
            tri(vc, m, x - sz, y - sz, argb(a, r, gr, b), x + sz, y - sz, argb(a, r, gr, b), x + sz, y + sz + tail, argb(0, r, gr, b), x - sz, y + sz + tail, argb(0, r, gr, b));
        }
        // the burst at the cut, as in the footage: a bright blob of light at the middle, black smoke between the flames, and a dense
        // crowd of thin jagged flames along the edge the rider is leaving from (the bottom going up, the top coming home), which
        // shrink back and slide away over about a second
        float blob = 1f - smooth(age / 7f);
        if (blob > 0.01f) radial(vc, m, cx, h * (kind == 1 ? 0.7f : 0.25f), w * 0.2f, h * 0.35f, argb((int) (230 * blob), 190, 255, 240));
        float smoke = 1f - smooth((age - 1f) / 6f);
        if (smoke > 0.01f) {
            for (int i = 0; i < 10; i++) {
                float x = (i < 5 ? 0.04f + 0.2f * hash(i, 51) : 0.76f + 0.2f * hash(i, 51)) * w, y = h * (0.35f + 0.4f * hash(i, 52));
                radial(vc, m, x, y, w * (0.05f + 0.06f * hash(i, 53)), h * (0.08f + 0.1f * hash(i, 54)), argb((int) (215 * smoke), 0, 10, 8));
            }
        }
        float shrink = 1f - smooth((age - 3f) / 16f);
        if (shrink > 0.01f) {
            boolean top = kind == 1;
            float slide = age * h * 0.03f; // the flames slide away from the edge they stand on
            for (int i = 0; i < 64; i++) {
                float side = hash(i, 11), x = (side < 0.5f ? side * 0.62f : 1f - (1f - side) * 0.62f) * w; // crowded toward both sides, clear in the middle
                float bw = w * (0.004f + 0.009f * hash(i, 12)), len = h * (0.12f + 0.5f * hash(i, 13)) * shrink;
                float yb = top ? -slide : h + slide, sgn = top ? 1f : -1f; // sgn: which way the flame points
                int a = (int) (235 * Math.min(1f, shrink * 1.4f));
                int bright = argb(a, 120, 255, 200), tip = argb(0, 150, 255, 225);
                tri(vc, m, x - bw, yb, bright, x + bw, yb, bright, x + bw * 0.12f + bw * 0.5f * (hash(i, 14) - 0.5f), yb + sgn * len, tip, x - bw * 0.12f, yb + sgn * len, tip);
                float x2 = x + bw * (hash(i, 15) - 0.5f) * 3f, len2 = len * 0.55f; // a shorter second flame leaning off the first, so the edge is jagged
                tri(vc, m, x2 - bw * 0.7f, yb, bright, x2 + bw * 0.7f, yb, bright, x2 + bw * 0.1f, yb + sgn * len2, tip, x2 - bw * 0.1f, yb + sgn * len2, tip);
            }
        }
    }

    /**
     * The same 3D double helix as in the world, put into the void: drawn with its own perspective camera 7 blocks away (the
     * flat gui projection is swapped for it and put back), wide enough to fill about 40 % of the screen's width, much taller than
     * the screen, and turning slowly. {@code dir} makes it creep the way the rider travels.
     */
    private static void drawHelix3d(Minecraft mc, int w, int h, float time, float dir) {
        com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float) Math.toRadians(70), (float) w / h, 0.05f, 100f), com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        var mv = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        com.mojang.blaze3d.systems.RenderSystem.clear(256, Minecraft.ON_OSX); // the world's depth must not hide it
        PoseStack ps = new PoseStack();
        ps.translate(0, 0, -7f);
        ps.scale(1.8f, 1f, 1.8f);
        ps.translate(0, -13f + dir * (time * 0.01f % 5f), 0); // 26 blocks long, middle at the screen's middle; it creeps up to 5 blocks the way the rider travels, never far enough to show an end
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(GLOW));
        helix(vc, ps.last(), time * 0.2f, 2.4f, 80f, 255f, 150f);
        buffers.endBatch(RenderType.eyes(GLOW));
        mv.popMatrix();
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        com.mojang.blaze3d.systems.RenderSystem.restoreProjectionMatrix();
    }

    /** The rider, tumbling in the middle of the helix (the real model, drawn small on the HUD). */
    private static void rider(GuiGraphics g, Minecraft mc, int w, int h, float partial) {
        var p = mc.player;
        float yBody = p.yBodyRot, yBodyO = p.yBodyRotO, yHead = p.yHeadRot, yHeadO = p.yHeadRotO, yRot = p.getYRot(), yRotO = p.yRotO, xRot = p.getXRot(), xRotO = p.xRotO;
        int hurt = p.hurtTime;
        try {
            drawingHud = true;
            // exactly as the world showed them: seen from behind (the camera rides behind the rider), the same size and place on the
            // screen, and the same turning, carried on from where the world left it; nothing but the turn changes
            p.yBodyRot = p.yBodyRotO = p.yHeadRot = p.yHeadRotO = 0f;
            p.setYRot(0f);
            p.yRotO = 0f;
            p.setXRot(0f);
            p.xRotO = 0f;
            p.hurtTime = 0; // no red flash
            Quaternionf rot = kind == 1 ? new Quaternionf().rotateZ((float) Math.PI)
                    : diveRotation(clk() - voidClk); // going up: no turn, they dive
            InventoryScreen.renderEntityInInventory(g, w * 0.5f, riderY(h, clk() - voidClk), h * RIDER_SIZE, new Vector3f(0, p.getBbHeight() / 2f + 0.0625f, 0), rot, null, p);
        } catch (RuntimeException e) {
            // the model is a nicety; the void works without it
        } finally {
            p.yBodyRot = yBody; p.yBodyRotO = yBodyO; p.yHeadRot = yHead; p.yHeadRotO = yHeadO;
            p.setYRot(yRot); p.yRotO = yRotO; p.setXRot(xRot); p.xRotO = xRotO;
            p.hurtTime = hurt;
            drawingHud = false;
        }
    }

    // ------------------------------------------------------------------------------------------------ ticks and camera

    /** Once per client tick, for anything that has to be stepped (particles, changes to what the rider sees of the world). */
    public static void tick(Minecraft mc) {
        sync(mc);
        if (mc.level == null) return;
        var rand = mc.level.random;
        long now = mc.level.getGameTime();
        SPIN.keySet().removeIf(id -> MudHandClient.get(id) == null); // a ride that ended stands the rider straight again
        for (var e : MudHandClient.all().entrySet()) {
            Entity who = mc.level.getEntity(e.getKey());
            MudHandClient.State s = e.getValue();
            if (who == null) continue;
            float t = now - s.since(), tp = now - s.phaseSince();
            double x = who.getX(), y = who.getY(), z = who.getZ();
            boolean home = s.kind() == 1;
            float len = home ? 42f : 44f; // the spin begins with the ribbons, gets quicker, and stops when the world is left
            float omega = 0f; // nobody spins any more
            float[] sp = SPIN.computeIfAbsent(e.getKey(), id -> new float[2]);
            sp[1] = omega;
            sp[0] = omega == 0f ? 0f : sp[0] + omega;
            switch (s.phase()) {
                case 1 -> {
                    if (t > 4f) { // green sparks winding up the rider, and cyan motes rising from the feet
                        for (int i = 0; i < 1 + (int) (t / 14f); i++) {
                            double an = t * 0.5 + i * 2.1, r = 0.6 + 0.2 * rand.nextDouble();
                            mc.level.addParticle(dust(0.3f, 1f, 0.5f), x + Math.cos(an) * r, y + (t * 0.05 + i * 0.3) % 2.0, z + Math.sin(an) * r, 0, 0.03, 0);
                        }
                        if (t % 2 == 0) {
                            double an = rand.nextDouble() * 6.283, r = 0.3 + rand.nextDouble() * 1.0;
                            mc.level.addParticle(dust(0.3f, 0.95f, 1f), x + Math.cos(an) * r, y + 0.1, z + Math.sin(an) * r, 0, 0.05, 0);
                        }
                    }
                }
                case 2 -> {
                    if (!home && tp < CUT_UP) {
                        for (int i = 0; i < 3; i++) {
                            double an = rand.nextDouble() * 6.283;
                            mc.level.addParticle(dust(0.35f, 1f, 0.9f), x + Math.cos(an) * 1.2, y + rand.nextDouble() * 2.0, z + Math.sin(an) * 1.2, 0, 0.1, 0);
                        }
                    }
                }
                case 5 -> {
                    for (int i = 0; i < 3 && tp < 12f; i++) {
                        double an = rand.nextDouble() * 6.283, r = 0.3 + rand.nextDouble() * 0.8;
                        mc.level.addParticle(dust(0.3f, 1f, 0.5f), x + Math.cos(an) * r, y + 0.1, z + Math.sin(an) * r, Math.cos(an + 1.57) * 0.12, 0.05 + rand.nextDouble() * 0.05, Math.sin(an + 1.57) * 0.12);
                    }
                }
                default -> { }
            }
        }
    }

    private static DustParticleOptions dust(float r, float g, float b) {
        return new DustParticleOptions(new Vector3f(r, g, b), 1f);
    }

    /** The field of view the camera should use this frame; return {@code fov} unchanged for no effect. */
    public static float fov(float fov, float partial) {
        Minecraft mc = Minecraft.getInstance();
        sync(mc);
        float c = clk();
        if (voidOn) return fov * (1f + 0.15f * (1f - smooth((c - voidClk) / 8f)));
        if (phase == 2 && kind == 0) return fov * (1f + 0.12f * smooth((c - phaseClk) / CUT_UP));
        return fov;
    }

    /** 0..1: how strong the full-screen motion blur should be this frame (0 = off). */
    public static float blur(float partial) {
        sync(Minecraft.getInstance());
        float c = clk();
        if (phase == 2 && kind == 0 && !voidOn) return 0.5f * smooth((c - phaseClk) / CUT_UP);
        return 0f;
    }

    private static float smooth(float u) {
        u = Mth.clamp(u, 0f, 1f);
        return u * u * (3 - 2 * u);
    }
}
