package com.botwclimb.client.shrine.totk;

import com.botwclimb.BotwClimb;
import com.botwclimb.client.shrine.MudHandClient;
import com.botwclimb.common.shrine.TerminalRide;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
 * The Tears of the Kingdom look of the terminal ride, after two screen recordings of its Ascend
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
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "textures/totk/glow.png"),
            STRAND = ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "textures/totk/strand.png");
    /** Ticks into phase 2 at which the footage cuts from the world (ribbons) to the void; a Return has no ribbons, it cuts as the sinking starts. */
    private static final float CUT_UP = TerminalRide.FLOAT_TICKS, CUT_HOME = TerminalRide.SINK_DELAY;
    /** Where the void ends, in blocks above the ground the rider arrives on: both ways it lasts until they are all but standing on it. */
    private static float land() {
        return kind == 1 ? TerminalRide.HOME_SLOW : 0.6f; // coming home the rider is seen drifting down the last blocks in the world, still in the falling pose
    }

    // state of the rider's own ride, kept by sync()
    private static boolean voidOn, drawingHud;
    private static float voidClk, cutClk = -1e9f, phaseClk, lastRide = -1e9f;
    private static int phase;
    private static long rideSince = -1;
    private static int kind;

    /** The rider's orientation in the void going up: upright, head up, facing away from the camera. No tilt, no turn, no shake. */
    private static Quaternionf diveRotation(float t) {
        return new Quaternionf().rotateZ((float) Math.PI); // upright: no tilt
    }

    /** Where the rider is on the screen (middle, as the third-person camera holds them) and how big (pixels per block, as a share of the height). */
    private static final float RIDER_Y = 0.6f, RIDER_SIZE = 0.161f; // 15 % bigger than at first, against the spiral

    /**
     * The rider's turn inside the void, kept on the wall clock: the game's own ticks and partial-tick fractions stand still or jump
     * about during the world change and the loading screen, and a turn measured with them rattles. Starts from where the world's turn
     * had got to at the cut.
     */
    private static float voidAngle, voidAngleClk, voidAngleFor = Float.NaN;

    /**
     * The two lights on the void's rider, or null outside that drawing: both come from the side of the nearer ribbon of the spiral, which
     * turns round the rider (the same turn as the void's helix), so the lit side sweeps round them and the far side falls into shadow.
     */
    public static org.joml.Vector3f[] hudLight() {
        if (!drawingHud) return null;
        // two lamps, one per ribbon, each circling at its own speed and height: every face of the body (back, sides, the tops and fronts of
        // the raised arms, the kicking legs) catches them at its own moment, so the light moves over the body in patches instead of
        // switching the whole of it on and off
        float t = clk();
        float a0 = t * 0.11f, a1 = -t * 0.083f + 2.1f;
        org.joml.Vector3f d0 = new org.joml.Vector3f((float) Math.cos(a0), -0.2f - 0.6f * (float) Math.sin(t * 0.05f), (float) Math.sin(a0)).normalize();
        org.joml.Vector3f d1 = new org.joml.Vector3f((float) Math.cos(a1), 0.3f - 0.6f * (float) Math.sin(t * 0.037f + 1f), (float) Math.sin(a1)).normalize();
        return new org.joml.Vector3f[] {d0, d1};
    }

    /** True while the rider is being drawn into the void on the HUD (that draw goes through the player renderer, whose events must leave it alone). */
    /** The render type the ride's glowing geometry is drawn with. */
    public static RenderType glowType() {
        return glow();
    }

    /**
     * The glow's render type. Up to 1.21.1 the "eyes" type adds its light to the picture; from 1.21.2 it blends with alpha and the ride's
     * light came out thin and pale, so there it is the additive lightning type (no texture: see {@link #quad}, which feathers the edges).
     */
    //? if <1.21.2 {
    private static final boolean EYES = true;
    //?} else {
    /*private static final boolean EYES = false;
    *///?}

    /** A colour of light at the strength {@code a} (0..255) asks for: the "eyes" type (up to 1.21.1) adds its light whole and ignores alpha. */
    private static int lit(int c, int a) {
        return EYES ? c * Mth.clamp(a, 0, 255) / 255 : c;
    }

    private static RenderType glow() {
        //? if >=1.21.2 {
        /*return RenderType.lightning();
        *///?} else {
        return RenderType.eyes(GLOW);
        //?}
    }

    public static boolean drawingHud() {
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
    public static void pose(net.minecraft.client.model.PlayerModel m, AbstractClientPlayer p, float pt, boolean hud) {
        MudHandClient.State s = MudHandClient.get(p.getId());
        if (s == null) return;
        if (s.kind() != 0 && s.kind() != 1) return;
        boolean home = s.kind() == 1;
        if (hud) { // drawn into the void the head looks straight ahead, a little up; in the world it follows the player's view (vanilla turns it), the body coming round after it (TotkSpin)
            m.head.xRot = HEAD_UP;
            m.head.yRot = 0f;
            com.botwclimb.client.ClientCompat.syncOuterLayers(m);
        }
        float cutIn, legClock; // ticks since the cut into the void (negative before it), and the clock the legs kick on
        if (hud) {
            cutIn = clk() - voidClk;
            legClock = cutIn;
        } else {
            float now = p.level().getGameTime() + pt, tp = now - s.since();
            if (s.phase() == 1) cutIn = tp - (home ? TerminalRide.GRAB_HOME : 44f) - (home ? CUT_HOME : CUT_UP);
            else if (s.phase() == 2) cutIn = now - s.phaseSince() - (home ? CUT_HOME : CUT_UP);
            else {
                if (s.phase() >= 4) { // phase 4 in the world is the last tick or two above the floor
                    float a = s.phase() == 4 ? 0f : now - s.phaseSince();
                    if (home && (s.phase() == 4 || a < SPIN_STOP)) { // still descending and turning: the falling pose from the void, legs kicking
                        float[] l = rideLegs(m, LEGS_LET_GO, kick(now, 14f, 0.3f), now, 1f);
                        apply(m, ARMS_OPEN, l, 0f, 0.22f);
                    } else if (home) landed(m, (a - SPIN_STOP) / HOME_PACE); // the landing starts once the turn has stopped
                    else arrive(m, a);
                }
                return;
            }
            legClock = now;
        }
        float[] a, l;
        float bodyX, kneeOut = 0f, legApart = 0f;
        if (home && hud) { // falling through the helix: the prayer and the raised right knee are held at first, then both let go slowly (they finish letting go on the ground, see landed)
            float rel = smooth((cutIn - 8.5f) / 17f);
            a = mix(ARMS_PRAY, ARMS_OPEN, rel);
            l = rideLegs(m, mix(LEGS_HELD, LEGS_LET_GO, rel), kick(legClock, 14f, 0.3f), legClock, 1f);
            kneeOut = 0.45f * (1f - 0.5f * rel);
            bodyX = 0f;
        } else if (home) { // waist deep in the marker, leaning forward on the hands and looking out; then the twist, during which they straighten and the arms go up into the prayer
            float grab = s.phase() == 1 ? smooth((p.level().getGameTime() + pt - s.since() - 3.4f) / 17f) : 1f, lean = grab * (1f - smooth((cutIn + CUT_HOME) / (CUT_HOME * 0.5f)));
            if (grab <= 0f) return;
            a = mix(mix(ARMS_REST, ARMS_HOLD, grab), ARMS_PRAY, smooth((cutIn + CUT_HOME - 1f) / 5f)); // the arms shoot up as the twist starts (0.25 s), so it reads clearly
            l = null; // the legs are in the ground
            bodyX = 0.5f * lean;
        } else { // going up (up.mov): crouch, lift off with the arms up and the right knee raised, hold it until the rumble, then dive and swim
            float lift = cutIn + CUT_UP; // ticks since the lift-off
            if (lift < -6f) return;
            // the knee-bend is held until the rumble (the rider stalls under the void as if it caught them); there they pull the arms and
            // legs in and push out into the swim with the hands together, and swim on from there through the whole void
            float c = crouch(lift), held = smooth((lift - 2f) / 5f), sw = lift - SWIM_AT;
            float pull = smooth(sw / PULL) * (1f - smooth((sw - PULL) / PULL));
            a = mix(arms(cutIn), ARMS_PULL, pull);
            float[] set = mix(mix(LEGS_REST, LEGS_CROUCH, c), LEGS_HELD, held);
            if (com.botwclimb.client.PlayerJoints.joint(m.rightLeg) == null) { set[0] *= 0.6f; set[2] *= 0.6f; } // no knees (1.21.9 and later): a straight leg held that far out would be a kick
            float tuck = smooth((cutIn + 18f) / 10f), pu = Mth.clamp((cutIn + 7f) / 14f, 0f, 1f), push = pu * pu * pu, kicks = smooth((cutIn - 7f) / 6f); // the raised knee is held a while after the rumble, then the knees come up high and a little wide, a beat, then both legs drive down together, gathering speed hard to the end (a push, not an ease); the push runs through the cut into the void, half of it on each side, and runs on into the swim
            l = mix(mix(mix(set, LEGS_PULL, tuck), LEGS_PUSH, push), kick(legClock, 20f, 0.95f), kicks); // then both legs kick back and forth, as in swimming
            l[1] += 0.35f * kicks; l[3] += 0.35f * kicks; // swimming the knees stay a little bent, never quite straight
            float give = 0.3f * ring(cutIn, 7f, 8f, 3f) * (1f - kicks); // the knees give a little as the push ends
            l[1] += give; l[3] += give;
            legApart = 0.25f * tuck * (1f - push) * (1f - kicks) + 0.18f * kicks; // drawn up the knees go out to the sides, the push brings the legs together; swimming they are a little apart
            kneeOut = Math.max(0.45f * held, 0.45f * tuck) * (1f - push); // the bent knee points out; drawn up both knees turn well out, until the push
            bodyX = 0.2f * c;
        }
        apply(m, a, l, bodyX, kneeOut);
        if (legApart != 0f) { // vanilla sets the legs' roll afresh every frame
            m.rightLeg.zRot = Math.max(legApart, 0f);
            m.leftLeg.zRot = -Math.abs(legApart); // negative legApart: only the left leg angles out
            com.botwclimb.client.ClientCompat.syncOuterLayers(m);
        }
    }

    /** Puts a pose on the model: arms, legs (null leaves them alone), the body's pitch, and how far the right knee points out. */
    private static void apply(net.minecraft.client.model.PlayerModel m, float[] a, float[] l, float bodyX, float kneeOut) {
        setArms(m, a);
        if (l != null) {
            m.rightLeg.xRot = l[0];
            knee(m.rightLeg, l[1]);
            m.leftLeg.xRot = l[2];
            knee(m.leftLeg, l[3]);
            // the raised knee points a little outward, as in the footage, so it shows from behind (vanilla puts the legs' turn, depth and height back every frame)
            if (kneeOut != 0f && com.botwclimb.client.PlayerJoints.joint(m.rightLeg) != null) { m.rightLeg.yRot = kneeOut; m.leftLeg.yRot = -0.5f * kneeOut; } // the left knee a little out too, so its bend shows from behind
            // the body leans about the neck, which swings its lower end back: the legs go with it, or the hips would stick out behind them
            float back = 12f * (float) Math.sin(bodyX), up = 12f * (1f - (float) Math.cos(bodyX));
            m.rightLeg.z += back;
            m.leftLeg.z += back;
            m.rightLeg.y -= up;
            m.leftLeg.y -= up;
        }
        m.body.xRot = bodyX; // vanilla sets the body's pitch afresh every frame
        POSED.add(m);
        com.botwclimb.client.ClientCompat.syncOuterLayers(m);
        com.botwclimb.client.PlayerJoints.sync(m);
    }

    // A rider arriving at a plot (the footage sketches): crouched in a ripple on the ground, they spring straight up as a cat does, high,
    // the body upright, the legs straight with the toes pointed and the arms down at the sides; near the top the arms open out to the
    // sides and stay out while they fall, the knees softening; they land in a squat as the arms drop to the sides, then stand up as normal.
    /** Ticks into the set-down at which the rider springs up out of the crouch, and lands again. */
    private static final float SPRING = 20f, LANDS = 44f; // they land sunk in, wait while the white of the void's end fades (0.4 s) and a moment more (0.6 s), then jump
    /** The rider's head pitch all ride long: straight ahead, a little up, whatever the player's own pitch. */
    private static final float HEAD_UP = -0.12f;
    private static final float[] LEGS_SPRING = {0.05f, 0.15f, 0.1f, 0.35f}, // rising: straight down, the toes pointed, the left knee a little soft
            LEGS_TUCK = {-0.15f, 0.35f, 0.05f, 0.15f}, // falling: one knee a little bent, the other leg straight
            LEGS_ALIGHT = {-1.0f, 1.5f, -0.7f, 1.3f}; // the landing squat
    private static final float[] ARMS_SPRING = {0.25f, 0.15f, 0f, 0f, 0.25f, -0.15f, 0f, 0f, 0f}, // rising: down at the sides, a little out and back
            ARMS_TUCK = {0f, 1.45f, -0.5f, 0f, 0f, -1.45f, -0.5f, 0f, 0f}; // from the top down: out to the sides at shoulder height, the forearms a little forward
    //? if <1.21.9 {
    private static final boolean KNEES = true;
    //?} else {
    /*private static final boolean KNEES = false;
    *///?}

    /** Blocks above the ground the arriving rider's jump has them: sudden off the ground and down again in a curve. */
    private static boolean jumpRising(float a) {
        float u = (a - SPRING - 1f) / (LANDS - SPRING - 1f);
        return u >= 0f && u < 0.4f; // the first tick of the jump too: with u = 0 left out it fell through to the landing branch and drew one frame standing out of the hole
    }

    private static float jump(float a) {
        float u = (a - SPRING - 1f) / (LANDS - SPRING - 1f), top = 0.4f, high = 1.7f; // a quick spring up and a longer fall (jumpRising and sunk use the same top and height)
        if (u <= 0f || u >= 1f) return 0f;
        float d = u < top ? 1f - u / top : (u - top) / (1f - top);
        return high * (1f - d * d);
    }

    /** The arriving rider's pose a ticks into the set-down: {4 legs, 9 arms, body pitch, head pitch}. Without knees the thighs swing less. */
    private static float[] arrivePose(float a) {
        // Not keyframes: each part moves on its own clock and lags the body (the legs trail the push, the arms float and overshoot, the
        // landing sinks past the squat and springs back), so the rider moves through the sketches' poses instead of snapping between them.
        float settle = 1f; // out of the void straight into the crouch, no pull into it
        float coil = smooth((a - SPRING + 4f) / 4f) * (1f - smooth((a - SPRING) / 1.5f)); // sinks a little deeper just before the push
        float spring = smooth((a - SPRING) / 2f), stretch = smooth((a - SPRING - 1f) / 5f); // the body goes first, the legs straighten after
        float open = smooth((a - SPRING - 4f) / 10f), soft = smooth((a - SPRING - 10f) / 12f);
        float squat = smooth((a - LANDS + 1f) / 3f), stand = smooth((a - LANDS - 5f) / 12f);
        float air = spring * (1f - squat), drag = Mth.clamp((jump(a + 0.5f) - jump(a - 0.5f)) / 0.38f, -1f, 1f); // + rising, - falling
        float sink = Mth.clamp(squat + 0.3f * ring(a, LANDS - 1f, 9f, 4f), 0f, 1.3f) * (1f - stand); // past the squat and back up

        float[] crouch = LEGS_REST; // not a crouch: sucked into the ground to the waist (see sunk), as in the marker before a Return, the legs straight and out of sight
        float[] l = mix(mix(mix(mix(crouch, LEGS_SPRING, stretch * 0.6f + spring * 0.4f), LEGS_TUCK, soft), LEGS_ALIGHT, sink), LEGS_REST, stand);
        float sway = (float) Math.sin(a * 0.3f) * air;
        l[0] += (-0.12f * soft - 0.08f * sway) * air; // the legs drift apart while they fall, one ahead and one behind
        l[2] += (0.1f * soft + 0.06f * sway) * air;
        l[1] += 0.35f * Math.max(0f, -drag) * air + 0.4f * Math.max(0f, drag) * (1f - stretch); // knees fold a little as they trail
        l[3] += 0.45f * Math.max(0f, -drag) * air + 0.4f * Math.max(0f, drag) * (1f - stretch);
        if (!KNEES) { l[0] *= 0.4f; l[2] *= 0.4f; }

        float armsDown = smooth((a - SPRING - 11f) / 15f); // from the top of the jump the arms sink with the fall, reaching the sides just after the feet land
        float press = smooth((a - SPRING + 9f) / 6f); // just before the jump the hands come in off the rim, close together in front, and hold there a moment (3 ticks) before they push down for the jump
        float[] arms = mix(mix(mix(mix(ARMS_CROUCH, ARMS_PRESS, press), ARMS_SPRING, spring), ARMS_TUCK, open), ARMS_REST, armsDown);
        float lift = 0.25f * ring(a, SPRING + 12f, 16f, 7f) * (1f - armsDown); // the arms swing up a little past level and settle
        float floatR = 0.1f * (float) Math.sin(a * 0.33f) * open * air, floatL = 0.1f * (float) Math.sin(a * 0.33f + 1.7f) * open * air;
        arms[0] += 0.3f * Math.max(0f, drag) * air - 0.15f * soft * air; // pushed back by the rise, reaching forward a little while falling
        arms[4] += 0.3f * Math.max(0f, drag) * air - 0.05f * soft * air;
        arms[1] += lift + floatR;
        arms[5] -= lift + floatL;
        arms[2] -= 0.2f * open * air * (0.5f + 0.5f * (float) Math.sin(a * 0.27f)); // the forearms keep moving
        arms[6] -= 0.2f * open * air * (0.5f + 0.5f * (float) Math.sin(a * 0.27f + 2.1f));

        float body = 0.5f * settle * (1f + 0.2f * coil) * (1f - spring) // leaning forward as in the marker before a Return, deeper in the coil
                + (-0.1f * Math.max(0f, drag) + 0.12f * soft) * air // arched back on the way up, tipping forward on the way down
                + 0.35f * sink;
        float[] q = new float[14];
        System.arraycopy(l, 0, q, 0, 4);
        System.arraycopy(arms, 0, q, 4, 9);
        q[13] = body;
        return q;
    }

    /** A wobble that starts at the tick start and dies away: 0 before it, a sine of the period shrinking over decay ticks. */
    private static float ring(float a, float start, float period, float decay) {
        float u = a - start;
        return u <= 0f ? 0f : (float) (Math.sin(u * 2 * Math.PI / period) * Math.exp(-u / decay));
    }

    private static void arrive(net.minecraft.client.model.PlayerModel m, float a) {
        float[] q = arrivePose(a);
        if (a > LANDS + 18f) return;
        apply(m, java.util.Arrays.copyOfRange(q, 4, 13), java.util.Arrays.copyOfRange(q, 0, 4), q[13], 0f);
        float hang = 1f - smooth((a - SPRING + 3f) / 4f); // sunk in, the legs hang straight from the hips as in the marker before a Return (which leaves them to vanilla), not swung back with the lean; they go with it from just before the spring
        if (hang > 0f) {
            float back = 12f * (float) Math.sin(q[13]) * hang, up = 12f * (1f - (float) Math.cos(q[13])) * hang;
            m.rightLeg.z -= back; m.leftLeg.z -= back;
            m.rightLeg.y += up; m.leftLeg.y += up;
            com.botwclimb.client.ClientCompat.syncOuterLayers(m);
            com.botwclimb.client.PlayerJoints.sync(m);
        }
    }

    /** Blocks the feet of a pose come up from where straight legs put them (the lower foot counts): the model is lowered by this to stand on the ground. */
    private static float legDrop(float[] q) {
        float r = KNEES ? 6f * (float) Math.cos(q[0]) + 6f * (float) Math.cos(q[0] + q[1]) : 12f * (float) Math.cos(q[0]);
        float l = KNEES ? 6f * (float) Math.cos(q[2]) + 6f * (float) Math.cos(q[2] + q[3]) : 12f * (float) Math.cos(q[2]);
        return (12f - Math.max(r, l) + 12f * (1f - (float) Math.cos(q[13]))) / 16f * 0.9375f;
    }

    private static void setArms(net.minecraft.client.model.PlayerModel m, float[] a) {
        m.rightArm.xRot = a[0];
        m.rightArm.zRot = a[1];
        elbow(m.rightArm, a[2], a[3]);
        m.leftArm.xRot = a[4];
        m.leftArm.zRot = a[5];
        elbow(m.leftArm, a[6], a[7]);
        if (com.botwclimb.client.PlayerJoints.joint(m.rightArm) != null) { // the shoulders lift with the arms (vanilla puts y back every frame)
            m.rightArm.y += a[8];
            m.leftArm.y += a[8];
        }
    }

    /**
     * The legs of a rider in the void: the raised right knee stays as set and only sways with the kick, the left leg takes the share k of
     * the kick. Without knees (1.21.9 and later) a straight leg held out for the whole ride would be a kick, so there both legs swing.
     */
    private static float[] rideLegs(net.minecraft.client.model.PlayerModel m, float[] set, float[] kick, float clock, float k) {
        if (com.botwclimb.client.PlayerJoints.joint(m.rightLeg) == null) return mix(new float[] {set[0] * 0.6f, set[1], set[2] * 0.6f, set[3]}, kick, k);
        float ph = clock * (float) (2 * Math.PI) / 20f;
        return new float[] {set[0] + 0.12f * k * (float) Math.sin(ph), set[1] + 0.25f * k * (float) Math.sin(ph + 1f), Mth.lerp(k, set[2], kick[2]), Mth.lerp(k, set[3], kick[3])};
    }

    /**
     * A Return rider set down, over the 0.9 s the ride still holds them: the arms and the raised knee finish letting go. The right hand is
     * still over the head with the left forearm held out in front, then both forearms come down in front, then the arms hang.
     */
    private static void landed(net.minecraft.client.model.PlayerModel m, float t) {
        float c = 1f - smooth(t / 14f);
        boolean knees = com.botwclimb.client.PlayerJoints.joint(m.rightLeg) != null;
        if (c > 0f) {
            m.rightLeg.xRot = Mth.lerp(c, m.rightLeg.xRot, knees ? LEGS_LET_GO[0] : -0.3f);
            m.leftLeg.xRot = Mth.lerp(c, m.leftLeg.xRot, -0.3f);
            knee(m.rightLeg, LEGS_LET_GO[1] * c);
            knee(m.leftLeg, 0.6f * c);
            m.body.xRot = 0.15f * c;
        }
        setArms(m, mix(mix(mix(ARMS_OPEN, ARMS_LAND, smooth(t / 5f)), ARMS_LOWER, smooth((t - 5f) / 6f)), ARMS_REST, smooth((t - 11f) / 7f)));
        POSED.add(m);
        com.botwclimb.client.ClientCompat.syncOuterLayers(m);
        com.botwclimb.client.PlayerJoints.sync(m);
    }

    /** {right thigh, right knee, left thigh, left knee} in radians: a negative thigh swings forward, a positive knee folds the shin back. */
    private static final float[] LEGS_REST = {0f, 0f, 0f, 0f},
            LEGS_CROUCH = {-0.85f, 0.85f, -0.5f, 1.0f}, // the right foot set forward with the shin upright, the left knee bent with the foot behind it
            LEGS_HELD = {-1.4f, 2.1f, -0.45f, 0.9f}, // lifted off: the right thigh level in front with the shin tucked back under it, the left leg hanging with its knee bent too
            LEGS_PULL = {-1.55f, 2.2f, -1.5f, 2.2f}, // pulled in before the push into the swim: both knees drawn up high
            LEGS_PUSH = {0.35f, 0.1f, 0.35f, 0.1f}, // the end of the push: driven down and back, together, nearly straight
            LEGS_LET_GO = {-0.8f, 1.2f, 0f, 0.2f}; // coming home: how far the raised knee has come down by the time the rider is set on the ground

    /**
     * The legs of a rider in the air, as {right thigh, right knee, left thigh, left knee}: each thigh swings forward and back (never
     * sideways), the two a half cycle apart, and the knee bends as the thigh comes forward, so the shin and foot trail behind it.
     */
    private static float[] kick(float ticks, float period, float swing) {
        float ph = ticks * (float) (2 * Math.PI) / period;
        float[] out = new float[4];
        for (int side = 0; side < 2; side++) {
            float forward = 0.5f - 0.5f * (float) Math.sin(ph + side * (float) Math.PI); // 1 = thigh fully forward
            out[side * 2] = -0.05f - swing * (2f * forward - 1f);
            out[side * 2 + 1] = 0.25f + 0.75f * forward * forward;
        }
        return out;
    }

    private static void knee(net.minecraft.client.model.geom.ModelPart leg, float x) {
        var j = com.botwclimb.client.PlayerJoints.joint(leg);
        if (j != null) j.xRot = x;
    }

    private static void elbow(net.minecraft.client.model.geom.ModelPart arm, float x, float z) {
        var j = com.botwclimb.client.PlayerJoints.joint(arm);
        if (j != null) { j.xRot = x; j.zRot = z; }
    }

    // The arm poses, read from the footage (up.mov): the reach is straight up; the pull back is still straight and raised, tilted back behind
    // the head; in the prayer the upper arms are upright beside the head and the forearms lean in so the hands meet a little above it.
    private static final float UP = -3.1415927f;
    // {right upper x, z, elbow x, z, left upper x, z, elbow x, z, shoulder lift in pixels (negative is up)}: the elbow turns the forearm against
    // the upper arm (z negative bends the right forearm toward the middle). The lift of 4 puts the arm on top of the shoulder; the forearms then rest on the head's top corners (1 pixel into them).
    private static final float[] ARMS_REST = {0f, 0.05f, 0f, 0f, 0f, -0.05f, 0f, 0f, 0f},
            ARMS_REACH = {UP, 0f, 0f, 0f, UP, 0f, 0f, 0f, 0f},
            ARMS_BACK = {UP - 0.55f, 0f, 0f, 0f, UP - 0.55f, 0f, 0f, 0f, 0f},
            ARMS_PRAY = {UP, 0.34f, 0f, -0.08f, UP, -0.34f, 0f, 0.08f, -6f}, // reaching high, nearly straight, leaning in so the hands all but touch above the head (positive right z leans in on this model)
            ARMS_PULL = {UP + 0.9f, -0.7f, 0f, -1.6f, UP + 0.9f, 0.7f, 0f, 1.6f, -2f}, // pulled in: the elbows out and down, the forearms folded in, the hands at the head
            ARMS_HOLD = {-0.4f, 0.85f, -0.3f, -0.5f, -0.4f, -0.85f, -0.3f, 0.5f, 0f}, // out to both sides and a little forward, the elbows soft so the forearms curve in, the hands on the marker's rim
            // coming home, as the arms let go of the prayer: opened into a hoop over the head; then set down, the right hand still over the head
            // and the left forearm held out in front; then both forearms coming down in front
            ARMS_OPEN = {UP, -0.45f, 0f, -1.0f, UP, 0.45f, 0f, 1.0f, -2f},
            ARMS_LAND = {UP + 0.2f, 0f, 0.6f, -0.3f, 0.2f, 0f, -1.4f, 0f, 0f},
            ARMS_LOWER = {0.3f, 0.1f, -0.9f, 0f, 0.3f, -0.1f, -0.9f, 0f, 0f},
            // then in the crouch
            ARMS_PRESS = {-0.5f, 0.5f, -0.35f, -1.25f, -0.5f, -0.5f, -0.35f, 1.25f, 0f}, // the press before the jump: upper arms out and a little forward, forearms bent well in, so the hands close the space in front, ready to push
            ARMS_CROUCH = ARMS_HOLD; // as when waist deep in the marker before a Return: out to both sides and a little forward, as if the hands were on its rim

    /** How long the pull into the swim takes, and then the push out into it. */
    private static final float PULL = 8f;
    /** Ticks after the lift-off the pull into the swim starts: at the rumble (see sounds), when the rider stalls under the void. Until then the knee-bend is held. */
    private static final float SWIM_AT = TerminalRide.FLOAT_RISE - 2;

    /** Models the ride has posed and not yet put back. */
    private static final java.util.Set<Object> POSED = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /**
     * Puts back what the ride's pose leaves on a model: vanilla never resets a part's roll or the elbows, so they would stay on every player
     * drawn with that model afterwards. Called before anything else poses the model.
     */
    public static void unpose(net.minecraft.client.model.PlayerModel m) {
        if (!POSED.remove(m)) return;
        com.botwclimb.client.PlayerJoints.straighten(m);
    }

    /**
     * The arms of a rider going up, at a time counted from the cut into the void (negative before it): as the rider lifts off they reach
     * straight up (0.25 s), pull back (0.3 s) and fold into the prayer (0.4 s), all in the first second of the float; the prayer is held
     * through the float and the void to the end.
     */
    static float[] arms(float cutIn) {
        float lift = cutIn + CUT_UP; // ticks since the lift-off
        float[] a = mix(ARMS_REST, ARMS_REACH, smooth((lift - 2f) / 5f));
        a = mix(a, ARMS_BACK, smooth((lift - 7f) / 6f));
        return mix(a, ARMS_PRAY, smooth((lift - 13f) / 8f));
    }

    private static float[] mix(float[] a, float[] b, float k) {
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] + (b[i] - a[i]) * k;
        return out;
    }

    private TotkRide() {}

    /** Where the rider's middle is on the screen, in pixels; on the Return they come in from the bottom of the screen (footage: at the middle after 0.3 s). */
    private static float riderY(int h, float t) {
        if (kind != 1) return h * RIDER_Y;
        return h * RIDER_Y * 1.1f; // where the rider is going up, 10 % lower
    }

    /** Blocks the drawn model of a rider is lowered by: the dip of a Return's twist, or the crouch before an ascent. */

    /** Blocks a Return rider is drawn sunk into the ground while they wait in the marker: to the waist. */
    private static final float WAIST = 0.8f;

    public static float sunk(net.minecraft.world.entity.Entity p, float partial) {
        MudHandClient.State s = MudHandClient.get(p.getId());
        if (s == null) return 0f;
        float now = p.level().getGameTime() + partial, t = now - s.since(), tp = now - s.phaseSince();
        if (s.kind() == 0 && s.phase() >= 4) { // arriving at a plot: crouched on the ground, then the jump (the crouch's lowering eases off in the air)
            float a = s.phase() == 4 ? 0f : tp, up = jump(a);
            if (a < SPRING + 1f) return WAIST; // sunk in, exactly as deep as the marker before a Return
            if (jumpRising(a)) return WAIST * (1f - up / 1.7f) - up; // one curve from waist deep up to the top: being pulled out of the ground and the jump are a single motion, not one stacked on the other (that stuttered)
            return legDrop(arrivePose(a)) * (1f - Math.min(1f, up / 0.3f)) - up; // the fall, and the landing's squat as the feet reach the ground
        }
        if (s.phase() > 2) return 0f;
        if (s.kind() == 1) {
            if (s.phase() == 1) return WAIST * smooth((t - 3.4f) / 17f); // drawn down to the waist in under a second, as if something below pulls
            float x = Mth.clamp(tp / CUT_HOME, 0f, 1f);
            return WAIST + 0.6f * x * x; // the twist drags them on down from the waist to the shoulders, faster as it goes
        }
        return 0.11f * crouch(s.phase() == 1 ? t - 44f : tp); // going up: the knees bend before the lift (the crouched legs are 1.8 pixels shorter)
    }

    /** 0..1: the semi-crouch before an ascent at a time counted from the lift-off (footage, up.mov 4.12 - 4.25 s), gone as the rider lifts off. */
    private static float crouch(float lift) {
        return smooth((lift + 6f) / 5f) * (1f - smooth((lift - 1f) / 4f));
    }

    /** Each Return rider's turn when they touched down, so the landing can slow it from there. */
    private static final java.util.Map<Integer, Float> LAST_SPIN = new java.util.HashMap<>();
    /** Ticks after a Return rider touches down that their turn takes to stop; the landing pose and glow follow only then. */
    public static final float SPIN_STOP = 8.5f;
    /** How much quicker than it was first made the whole Return plays (its own clocks are divided by this): 15% less time. */
    private static final float HOME_PACE = 0.85f;
    /** Radians a tick a Return rider turns while falling, in the void and out of it, in step with the spiral round them. */
    private static final float HOME_TURN = 0.7f;
    /** Out of the void, drifting the last blocks down, the rider turns at 5/8 of that: the drop there is short, so the void's speed spun them far too often. */
    private static final float HOME_TURN_LAND = HOME_TURN * 5f / 8f;

    /** The angle in degrees the rider's model is turned by this frame (0 for anyone who is not spinning). */
    public static float spin(int entityId, float partial) {
        MudHandClient.State s = MudHandClient.get(entityId);
        Minecraft mc = Minecraft.getInstance();
        if (s == null || s.kind() != 1 || mc.level == null) return 0f;
        float tp = mc.level.getGameTime() + partial - s.phaseSince();
        if (s.phase() == 4) { // out of the void: still turning about their own axis, slower than in it, until they land
            float ang = (float) Math.toDegrees(HOME_TURN_LAND * tp);
            LAST_SPIN.put(entityId, ang);
            return ang;
        }
        if (s.phase() == 5) { // landed: the turn slows and stops facing the way they faced, a whole number of turns on (0.6 s)
            float base = LAST_SPIN.getOrDefault(entityId, 0f), end = (float) Math.ceil(base / 360f) * 360f; // the next whole turn, no extra one
            return Mth.lerp(smooth(tp / SPIN_STOP), base, end);
        }
        if (s.phase() != 2) return 0f;
        // one whole turn as the pull drags them under: it winds up over the first half and then turns steadily right up to the cut, where the
        // void's own turning takes over (no stop in between; the footage does it in 0.17 s, which at 20 frames a second reads as a glitch)
        float x = Mth.clamp((mc.level.getGameTime() + partial - s.phaseSince()) / CUT_HOME, 0f, 1f);
        return 360f * (x < 0.5f ? x * x / 0.75f : (x - 0.25f) / 0.75f);
    }

    /** While a video is recorded (-Precord) the effects run on this clock, which the harness steps once per tick: each saved frame is then exactly 1/20 s on, however long saving the last one took. */
    public static float recordClock;
    private static final boolean RECORDING = Boolean.getBoolean("botwclimb.record");

    private static float clk() {
        return RECORDING ? recordClock : net.minecraft.Util.getMillis() / 50f;
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
        boolean want = phase == 3 || phase == 2 && (tp >= (kind == 1 ? CUT_HOME : CUT_UP) || kind == 0 && mc.player.getDeltaMovement().y > 0.4) || phase == 4 && h > land() || loading; // going up, also the moment the climb starts: the server counts from when it began the phase, which is a little before this client heard of it, so the climb could show a frame or two in the world before the cut (a jump of the rider and the spiral)
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
    public static void layer(PoseStack ps, com.botwclimb.client.Buffers buffer, int light, AbstractClientPlayer p, MudHandClient.State s, float partial, boolean hud) {
        if (hud) return; // the rider drawn into the void on the HUD must not carry the world effects with them
        float now = p.level().getGameTime() + partial, t = now - s.since(), tp = now - s.phaseSince();
        float h = (float) (Mth.lerp(partial, p.yo, p.getY()) - s.startY());
        boolean home = s.kind() == 1;
        ps.pushPose();
        ps.translate(0, 1.501, 0);
        ps.scale(-1, -1, 1);
        VertexConsumer vc = buffer.getBuffer(glow());
        PoseStack.Pose pose = ps.last();
        switch (s.phase()) {
            case 1 -> { // a soft glow gathers at the feet; the ribbons grow in round the rider over the last ticks
                if (!home) { // the glow lies on the ground, not at the rider's feet (they crouch, then float up)
                    disc(vc, ground(ps, p, partial, h), 0f, 1.0f + 0.2f * (float) Math.sin(now * 0.25f), 90, 255, 200, (int) (150 * smooth((t - 4f) / 24f)));
                    ps.popPose();
                }
            }
            case 2 -> {
                float cut = home ? CUT_HOME : CUT_UP, k = smooth(tp / cut);
                if (!home) {
                    disc(vc, ground(ps, p, partial, h), 0f, 1.2f, 70, 235, 235, (int) (170 * (1f - k * 0.5f)));
                    ps.popPose();
                }
            }
            case 4 -> {
                // coming home nothing lands yet: the glow waits for the turn to stop (phase 5)
            }
            case 5 -> {
                if (home && tp >= SPIN_STOP) { // the landing glow once the turn has stopped
                    float age = 3.5f + (tp - SPIN_STOP) / HOME_PACE;
                    swirl(buffer.getBuffer(markerType()), pose, age - 2f, 1.0f, swirlAlpha(age), true); // the strands' colour, blended, so they stay green on a bright floor
                    landing(buffer.getBuffer(glow()), pose, age); // then their light (a buffer is fetched again after another type was used)
                }
                else if (tp >= LANDS) { // arriving at a plot: the swirls play once the jump has set them down
                    PoseStack.Pose g = ground(ps, p, partial, h);
                    arrival(buffer.getBuffer(markerType()), g, tp - LANDS, true); // the strands' colour, blended, so they stay green on a bright floor
                    arrival(buffer.getBuffer(glow()), g, tp - LANDS, false); // then their light
                    ps.popPose();
                }
            }
            default -> { }
        }
        ps.popPose();
    }

    /**
     * Opens a copy of the layer's space moved to the ground the ride began on; the caller pops it. Things on the ground used to sit at the
     * rider's feet, and the feet are not still: a held rider's height wavers by a few hundredths of a block, and the model is lowered on
     * purpose, which put them under the grass on some frames (they flickered). 0.9375 is the scale the player model is drawn at.
     */
    private static PoseStack.Pose ground(PoseStack ps, AbstractClientPlayer p, float partial, float h) {
        ps.pushPose();
        ps.translate(0f, (sunk(p, partial) - h) / 0.9375f + 0.03f, 0f);
        return ps.last();
    }

    /** The render type of the Return's exit marker: blended over the ground, not added to it, so its cyan stays deep on a bright floor. */
    public static RenderType markerType() {
        return RenderType.entityTranslucentEmissive(STRAND); // white: the grey glow texture darkened the ground under the marker like a shadow
    }

    /**
     * The render type of the strands' coloured sheaths: blended, so the colour holds on a bright sky. It writes no depth, and the ribbons are
     * drawn after the clouds (see {@link TotkRibbons}), so where strands cross they blend into each other instead of cutting each other off,
     * and the clouds can no longer paint over them. The texture is a strand with a crisp edge; the softness comes from each strand's glow.
     */
    public static RenderType bodyType() {
        return RenderType.entityTranslucentEmissive(STRAND);
    }

    /**
     * The ribbons round every rider going up who is in the world (phases 1 and 2), fixed to the ground the ride began on, so the rider floats
     * up through them and passes their crossings. {@code ps} is the level's pose (camera at the origin); {@code body} picks the sheaths
     * (drawn with {@link #bodyType}) or the glow, cores and grain (drawn with {@link #glowType}).
     */
    public static void ribbons(PoseStack ps, VertexConsumer vc, boolean body) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = com.botwclimb.client.ClientCompat.partialTick(false), now = mc.level.getGameTime() + partial;
        net.minecraft.world.phys.Vec3 cam = com.botwclimb.client.ClientCompat.cameraPos();
        for (var e : MudHandClient.all().entrySet()) {
            MudHandClient.State s = e.getValue();
            boolean up = s.kind() == 0 && s.phase() <= 2, home = s.kind() == 1 && s.phase() >= 4; // going up before the void; coming home after it
            if (!up && !home) continue;
            Entity p = mc.level.getEntity(e.getKey());
            if (p == null) continue;
            if (home && s.phase() == 4 && p == mc.player && voidOn) continue; // still in the void: it has its own spiral there
            // going up they grow in over the last ticks of the gathering; coming home the spiral is still turning round the rider as they
            // come out of the void, and once they land it winds away from the top down to the ground over 0.55 s
            float k = up ? (s.phase() == 1 ? smooth((now - s.since() - 28f) / 14f) : 1f) : (s.phase() == 4 ? 1f : 1f - smooth((now - s.phaseSince()) / 11f));
            if (k <= 0.01f) continue;
            ps.pushPose();
            ps.translate(Mth.lerp(partial, p.xo, p.getX()) - cam.x, s.startY() + 0.03 - cam.y, Mth.lerp(partial, p.zo, p.getZ()) - cam.z);
            ps.scale(0.9375f, 0.9375f, 0.9375f); // the size they had when drawn with the player model
            helix(vc, ps.last(), now * (home ? 0.3f : 0.2f), k, body ? 2 : 1); // coming home it turns faster, with the rider
            ps.popPose();
        }
    }

    /** The Return's exit marker, drawn for the rider and anyone watching in the same space as {@link #layer}; it lies still while the rider twists. */
    public static void marker(PoseStack ps, VertexConsumer vc, AbstractClientPlayer p, MudHandClient.State s, float partial) {
        float now = p.level().getGameTime() + partial, t = now - s.since(), tp = now - s.phaseSince();
        float h = (float) (Mth.lerp(partial, p.yo, p.getY()) - s.startY());
        ps.pushPose();
        ps.translate(0, 1.501, 0);
        ps.scale(-1, -1, 1);
        ps.mulPose(Axis.YP.rotationDegrees(-spin(p.getId(), partial)));
        if (s.kind() == 0) { // arriving at a plot: the rider comes down into a ripple and crouches in it; it fades as they spring out
            float a = s.phase() == 4 ? 0f : tp;
            whirl(vc, ground(ps, p, partial, h), now, s.phase() == 4 ? smooth((3f - h) / 2.4f) : 1f, 1f - smooth((a - SPRING - 12f) / 10f)); // lasts well past the spring
        } else if (s.phase() == 1) whirl(vc, ground(ps, p, partial, h), now, smooth(t / 6f), 1f);
        else whirl(vc, ground(ps, p, partial, h), now, 1f - 0.15f * smooth(tp / CUT_HOME), 1f); // it draws in a little as the rider is pulled under
        ps.popPose();
        ps.popPose();
    }

    /** A Return's arrival (footage, down.mov 3.9 - 5.6 s): a white-cyan glow on the floor first, then the slashes swirl up out of it, then both fade. */
    private static void landing(VertexConsumer vc, PoseStack.Pose pose, float age) {
        disc(vc, pose, 0.03f, 0.8f + 1.0f * smooth(age / 7f), 40, 230, 150, (int) (130 * smooth(age / 3f) * (1f - smooth((age - 14f) / 10f)))); // a soft green glow on the floor, no white
        swirl(vc, pose, age - 2f, 1.0f, swirlAlpha(age), false);
    }

    private static int swirlAlpha(float age) {
        return (int) (255 * (1f - smooth((age - 16f) / 9f)));
    }

    /**
     * A rider arriving at a plot has landed from their jump (the user's reference image, measured against the drawn skeleton): big green
     * ribbons, each a bundle of strands like the spiral's, sweep round the rider 1 - 1.4 blocks out from the body, from the feet up to
     * about 2.4 blocks (well over the head), two arcing over it. They are thick, with ragged flame-like tongues off their edges, a light
     * core down each strand, cyan at the feet and green above, and green dust round them. They unwind from one end, turn slowly round the
     * rider, then each comes apart along a front that runs along it into flakes that drift up and out. {@code body}: the blended colour
     * pass; otherwise the light (glow, cores, dust and flakes).
     */
    private static void arrival(VertexConsumer vc, PoseStack.Pose pose, float age, boolean body) {
        if (age > ARRIVAL_END) return;
        if (!body) disc(vc, pose, 0.03f, 1.4f, 40, 200, 170, (int) (130 * smooth(age / 2f) * (1f - smooth((age - 6f) / 10f))));
        float form = smooth(age / 4f), turn = age * 0.05f;
        int n = 32;
        for (int k = 0; k < 8; k++) {
            float[] rb = ribbon(k, turn);
            float a0 = rb[0], sweep = rb[1], y0 = rb[2], y1 = rb[3], rad = rb[4], gone = 10f + 6f * hash(k, 97);
            for (int st = 0; st < 4; st++) {
                int seed = k * 7 + st;
                float side = hash(seed, 71) * 2f - 1f, thick = hash(seed, 72);
                float wd0 = 0.045f + 0.09f * thick * thick; // a few thick strands among thinner ones
                float[] q = new float[12];
                for (int i = 0; i < n; i++) {
                    float mid = (i + 0.5f) / n;
                    if (mid > form) break; // still forming: it unwinds from its start
                    float broke = gone + 8f * mid + 2f * (float) Math.sin(mid * 4f + k); // the front of the break-up runs along the ribbon
                    float xMid = 0f, yMid = 0f, zMid = 0f, an0 = 0f;
                    for (int e = 0; e < 2; e++) {
                        float u = (float) (i + e) / n;
                        float off = side * (0.6f + 0.4f * (float) Math.sin(u * 5f + st * 1.9f + age * 0.15f)); // strands pinch together and fan apart along it
                        float an = a0 + sweep * u + 0.05f * off + 0.08f * (float) Math.sin(u * 6f + k * 3f + age * 0.25f) + 0.035f * (float) Math.sin(u * 19f + seed + age * 0.5f); // kinks, like a flame's
                        float r = rad + 0.15f * (float) Math.sin(u * Math.PI) + 0.06f * off + 0.04f * (float) Math.sin(u * 9f + st + age * 0.3f);
                        float y = Mth.lerp(u, y0, y1) + 0.13f * off + 0.08f * (float) Math.sin(u * 4f + k + age * 0.2f) + 0.05f * (float) Math.sin(u * 23f + seed * 1.3f + age * 0.6f) + 0.025f * (float) Math.sin(u * 41f + seed);
                        float wd = wd0 * (float) Math.pow(Math.sin(u * Math.PI), 0.6) * (0.75f + 0.25f * (float) Math.sin(u * 11f + seed));
                        float cx = (float) Math.cos(an), cz = (float) Math.sin(an);
                        float x = r * cx, z = r * cz, ox = 0.45f * cx * wd, oy = 0.9f * wd, oz = 0.45f * cz * wd; // the width stands up, leaning out a little
                        if (e == 0) { q[0] = x - ox; q[1] = y - oy; q[2] = z - oz; q[3] = x + ox; q[4] = y + oy; q[5] = z + oz; an0 = an; }
                        else { q[9] = x - ox; q[10] = y - oy; q[11] = z - oz; q[6] = x + ox; q[7] = y + oy; q[8] = z + oz; }
                        xMid += x * 0.5f; yMid += y * 0.5f; zMid += z * 0.5f;
                    }
                    float gk = smooth((yMid - 0.15f) / 0.5f); // cyan at the feet, green from the shins up
                    float al = (1f - smooth((age - broke) / 7f)) * smooth(age / 1.5f);
                    if (al > 0.01f) {
                        if (body) quadOnce(vc, pose, widen(q, 0.4f), (int) Mth.lerp(gk, 20f, 55f), (int) Mth.lerp(gk, 200f, 215f), (int) Mth.lerp(gk, 210f, 35f), (int) (235 * al), 0f, 1f); // vivid yellow-green (the image), cyan at the feet
                        else {
                            int ga = (int) (40 * al), ca = (int) (190 * al);
                            quad(vc, pose, widen(q, 2.4f), lit((int) Mth.lerp(gk, 30f, 70f), ga), lit(225, ga), lit((int) Mth.lerp(gk, 230f, 40f), ga), ga, 0f, 1f); // its glow
                            quad(vc, pose, widen(q, -0.6f), lit((int) Mth.lerp(gk, 120f, 150f), ca), lit(255, ca), lit((int) Mth.lerp(gk, 250f, 110f), ca), ca, 0f, 1f); // the hot core, narrow, pale only at its middle
                        }
                        int h = seed * 41 + i;
                        if (i % 3 == 1 && hash(h, 74) > 0.35f) { // a ragged tongue off the edge, leaning up (or down) and back along the ribbon
                            float len = (0.12f + 0.22f * hash(h, 75)) * (0.5f + 0.5f * thick), up = hash(h, 76) < 0.6f ? 1f : -1f, sg = Math.signum(sweep);
                            float tx = -(float) Math.sin(an0) * sg, tz = (float) Math.cos(an0) * sg; // along the ribbon
                            float bx = (q[3] + q[0]) * 0.5f, by = up > 0 ? q[4] : q[1], bz = (q[5] + q[2]) * 0.5f, w = 0.025f + 0.02f * thick;
                            float[] tg = {bx - tx * w, by, bz - tz * w, bx + tx * w, by, bz + tz * w,
                                    bx - tx * len * 0.6f, by + up * len, bz - tz * len * 0.6f, bx - tx * len * 0.6f, by + up * len, bz - tz * len * 0.6f};
                            int ta = (int) (120 * al);
                            if (body) quadOnce(vc, pose, tg, 55, 215, 35, (int) (200 * al), 0f, 1f);
                            else quad(vc, pose, tg, lit(120, ta), lit(255, ta), lit(150, ta), ta, 0f, 1f);
                        }
                    }
                    if (!body && st == 0 && i % 3 == 1) flake(vc, pose, xMid, yMid, zMid, an0, age - broke, k * 97 + i, 60, 240, (int) Mth.lerp(gk, 160f, 100f));
                }
            }
        }
        if (body) return;
        for (int d = 0; d < 260; d++) { // green dust round the ribbons
            float[] rb = ribbon(d % 8, turn);
            float u = hash(d, 61) * form, an = rb[0] + rb[1] * u;
            float r = rb[4] + 0.15f * (float) Math.sin(u * Math.PI) + (hash(d, 62) - 0.5f) * 0.5f;
            float y = Mth.lerp(u, rb[2], rb[3]) + (hash(d, 63) - 0.5f) * 0.4f + age * 0.012f, size = 0.012f + 0.016f * hash(d, 64);
            float x = r * (float) Math.cos(an), z = r * (float) Math.sin(an);
            int a = (int) (200 * smooth(age / 2f) * (0.5f + 0.5f * (float) Math.sin(age * 0.6f + d * 1.7f)) * (1f - smooth((age - 14f - 10f * hash(d, 65)) / 8f)));
            if (a > 2) quad(vc, pose, new float[]{x - size, y - size, z, x + size, y - size, z, x + size, y + size, z, x - size, y + size, z}, lit(40, a), lit(255, a), lit(110, a), a, 0.3f, 0.7f);
        }
        for (int d = 0; d < 6; d++) flake(vc, pose, 0f, 0f, 0f, 0f, age - 4f - 10f * hash(d, 95), 5000 + d, 70, 240, 110); // a few loose flakes from the start
    }

    /** Arrival ribbon k: {start angle, sweep, start height, end height, radius}. Ribbons 1 and 5 arc over the head. */
    private static float[] ribbon(int k, float turn) {
        boolean over = k == 1 || k == 5;
        float y0 = over ? 1.55f + 0.3f * hash(k, 93) : 0.05f + 0.9f * hash(k, 93);
        float y1 = over ? 1.9f + 0.5f * hash(k, 94) : Mth.clamp(y0 + 0.5f + 1.3f * hash(k, 94), 0.4f, 2.3f);
        return new float[] {k * 0.785f + 0.6f * hash(k, 91) + turn * (0.7f + 0.6f * hash(k, 96)), (1.7f + 1.3f * hash(k, 92)) * (k % 3 == 0 ? -1f : 1f), y0, y1, 1.0f + 0.4f * hash(k, 95)};
    }

    /** Ticks after a rider's landing at a plot when the last flake of the arrival's flames is gone (its set-down lasts this long past the landing). */
    private static final float ARRIVAL_END = 44f;

    /**
     * One flake the arrival's flames come apart into: a small elongated green fleck that drifts up and away from the body, turning, and fades
     * over half a second. {@code t} is ticks since it came off (nothing before 0). A fleck with no position (x, y, z all 0) starts at a
     * random place round the body.
     */
    private static void flake(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float an, float t, int seed, int r, int g, int b) {
        if (t < 0f || t > 20f) return;
        if (x == 0f && y == 0f && z == 0f) {
            an = hash(seed, 81) * Mth.TWO_PI;
            float rr = 0.4f + 0.5f * hash(seed, 82);
            x = rr * (float) Math.cos(an);
            z = rr * (float) Math.sin(an);
            y = 0.2f + 1.5f * hash(seed, 83);
        }
        float out = 0.015f * t * (0.5f + hash(seed, 84)), lift = 0.02f * t * (0.5f + hash(seed, 85));
        x += (float) Math.cos(an) * out;
        z += (float) Math.sin(an) * out;
        y += lift;
        int a = (int) (170 * smooth(t / 2f) * (1f - smooth((t - 8f) / 12f)));
        float size = 0.03f + 0.02f * hash(seed, 87);
        dot(vc, pose, x, y, z, size, r, g, b, a);
    }

    /** A soft round mote: bright at its middle and clear at its rim, on three crossed planes so it is round from any side (every version). */
    private static void dot(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float size, int r, int g, int b, int a) {
        if (a <= 2) return;
        int n = 8;
        for (int plane = 0; plane < 3; plane++) {
            for (int i = 0; i < n; i++) {
                double t0 = i * 2 * Math.PI / n, t1 = (i + 1) * 2 * Math.PI / n;
                float c0 = size * (float) Math.cos(t0), s0 = size * (float) Math.sin(t0), c1 = size * (float) Math.cos(t1), s1 = size * (float) Math.sin(t1);
                float ax = plane == 0 ? c0 : plane == 1 ? 0 : c0, ay = plane == 2 ? 0 : s0, az = plane == 0 ? 0 : plane == 1 ? c0 : s0;
                float bx = plane == 0 ? c1 : plane == 1 ? 0 : c1, by = plane == 2 ? 0 : s1, bz = plane == 0 ? 0 : plane == 1 ? c1 : s1;
                v(vc, pose, x, y, z, 0.5f, 0.5f, r, g, b, a);
                v(vc, pose, x + ax, y + ay, z + az, 0.5f, 0.5f, r, g, b, 0);
                v(vc, pose, x + bx, y + by, z + bz, 0.5f, 0.5f, r, g, b, 0);
                v(vc, pose, x, y, z, 0.5f, 0.5f, r, g, b, a);
                v(vc, pose, x, y, z, 0.5f, 0.5f, r, g, b, a); // the other way round too (the glow type culls back faces)
                v(vc, pose, x + bx, y + by, z + bz, 0.5f, 0.5f, r, g, b, 0);
                v(vc, pose, x + ax, y + ay, z + az, 0.5f, 0.5f, r, g, b, 0);
                v(vc, pose, x, y, z, 0.5f, 0.5f, r, g, b, a);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ geometry

    private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float w, int r, int g, int b, int a) {
        vc.addVertex(pose, x, y, z).setColor(r, g, b, Mth.clamp(a, 0, 255)).setUv(u, w).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    /**
     * A glowing quad. From 1.21.2 the glow type has no texture to soften the sides, so the quad is written three times, each narrower
     * and stronger than the last: the light is faint at the edges and brightest down the middle.
     */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float[] p, int r, int g, int b, int a, float u0, float u1) {
        //? if >=1.21.2 {
        /*float[] narrow = {1f, 0.62f, 0.3f}, weight = {0.3f, 0.35f, 0.45f};
        for (int i = 0; i < 3; i++) {
            float[] q = new float[12];
            for (int c = 0; c < 4; c++) { // a corner moves toward the middle of its own edge (corners 0-1 and 2-3 are the two ends)
                int mate = c ^ 1, base = (c < 2 ? 0 : 6);
                for (int k = 0; k < 3; k++) {
                    float mid = (p[c * 3 + k] + p[mate * 3 + k]) * 0.5f;
                    q[c * 3 + k] = mid + (p[c * 3 + k] - mid) * narrow[i];
                }
            }
            quadOnce(vc, pose, q, r, g, b, (int) (a * weight[i]), u0, u1);
        }
        if (true) return;
        *///?}
        quadOnce(vc, pose, p, r, g, b, a, u0, u1);
    }

    /** A quad (corners 0-1 across it at one end, 2-3 at the other) made wider: each corner moves away from its mate by the share f of the width. */
    private static float[] widen(float[] q, float f) {
        float[] out = new float[12];
        for (int c = 0; c < 4; c++) {
            int mate = (c ^ 1) * 3;
            for (int x = 0; x < 3; x++) out[c * 3 + x] = q[c * 3 + x] + (q[c * 3 + x] - q[mate + x]) * f;
        }
        return out;
    }

    /** One quad, written both ways round (the glow type culls back faces). */
    private static void quadOnce(VertexConsumer vc, PoseStack.Pose pose, float[] p, int r, int g, int b, int a, float u0, float u1) {
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
     * The exit marker of a Return, as in the footage (down.mov): a dim teal pool close round the rider and two cyan rings that waver like the
     * edge of water, thick in places and thin in others, with strands that branch off them and trail away. Ripples leave the outer ring one
     * after another: each spreads to a fixed distance and fades away as it gets there. Nothing in it starts over visibly.
     */
    private static void whirl(VertexConsumer vc, PoseStack.Pose pose, float now, float grow, float fade) {
        // measured from the footage (the exit marker on grass): broad see-through teal sheets swirling round the rider (they lift the grass
        // from (175, 205, 120) to about (130, 230, 145)), each edged on its outer side by a thin bright cyan line (120, 255, 250). Each sheet
        // is born near the rider, winds outward and fades while the next follows; none stands still.
        fan(vc, pose, 0.023f, 1.45f * grow, 30, 220, 210, (int) (150 * fade * grow)); // the cyan underglow over the whole area, fading out at its edge
        // four at a time: a new one every 9.5 ticks, each living 38. Every ripple is its own: where it starts, how far round it
        // winds, how wide it is, how fast it spreads and how its edge wobbles are all picked afresh for it.
        float period = 38f, turn = now * 0.02f;
        for (int ring = 0; ring < 3; ring++) {
            float life = now + ring * period / 3f, u = (life % period) / period;
            int gen = (int) Math.floor(life / period) * 3 + ring;
            float seed = gen * 1.7f + hash(gen, 70) * 9f, speed = 0.85f + 0.3f * hash(gen, 71);
            float uu = Math.min(1f, u * speed), base = (0.3f + 1.15f * uu) * grow, strength = smooth(u / 0.1f) * (1f - smooth((uu - 0.5f) / 0.5f)) * fade * grow;
            if (strength <= 0.01f) continue;
            float start = hash(gen, 77) * Mth.TWO_PI - turn, span = Mth.TWO_PI, widthK = 0.75f + 0.5f * hash(gen, 73), inset = 1f; // a closed ring
            int n = 80;
            for (int i = 0; i < n; i++) {
                float f0 = (float) i / n, f1 = (float) (i + 1) / n, a0 = start - span * f0, a1 = start - span * f1; // counter-clockwise, widening outward
                float r0 = base * (inset + (1f - inset) * f0) * ringShape(a0, now, seed), r1 = base * (inset + (1f - inset) * f1) * ringShape(a1, now, seed);
                float taper = (float) Math.sqrt(Math.sin(Math.PI * (f0 + f1) * 0.5));
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                int a = (int) (255 * strength * taper);
                taper = 1f; // closed: no ends to taper
                float lw = (0.018f + 0.014f * ringBlob(a0, seed)) * widthK; // each ripple is its own thin wavy line, never joined to the others
                band(vc, pose, c0, s0, c1, s1, r0, r1, lw * 2.5f, lw * 2.5f, 0.029f, 40, 230, 215, a * 2 / 5, 0f, 1f); // its soft glow
                band(vc, pose, c0, s0, c1, s1, r0, r1, lw, lw, 0.031f, 110, 255, 245, Math.min(255, a), 0.1f, 0.9f); // the bright line
            }
        }
    }

    /** The radius of a marker ring at an angle, as a share of its mean: a slow, shallow wave that drifts. */
    private static float ringShape(float a, float now, float seed) {
        return 1f + 0.07f * (float) Math.sin(a * 3 + now * 0.04f + seed) + 0.045f * (float) Math.sin(a * 7 - now * 0.06f + seed) + 0.02f * (float) Math.sin(a * 13 + seed * 2f);
    }

    /** 0..1: where a marker ring is thick (the water piles up) and where it is thin. Fixed to the ring, so it only moves as the ring creeps round. */
    private static float ringBlob(float a, float seed) {
        return (float) Math.pow(0.5 + 0.5 * Math.sin(a * 5 + seed), 2);
    }

    /** One piece of a marker ring between two angles: deep cyan, a little lighter where it is thick. */
    private static void ringQuad(VertexConsumer vc, PoseStack.Pose pose, float a0, float a1, float r0, float r1, float w0, float w1, int al, float blob) {
        // measured from the footage (a frame of the exit marker): the line is saturated cyan (30, 240, 235), about a twentieth of the ring's
        // width, with a paler core (110, 255, 250) a third as wide and a teal glow (20, 190, 170) round it about two and a half times as wide
        if (al <= 2) return;
        float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
        band(vc, pose, c0, s0, c1, s1, r0, r1, w0 * 2.5f, w1 * 2.5f, 0.029f, 20, 190, 170, al * 2 / 5, 0f, 1f); // the glow
        band(vc, pose, c0, s0, c1, s1, r0, r1, w0, w1, 0.03f, 30, 240, 235, al, 0.1f, 0.9f); // the line
        band(vc, pose, c0, s0, c1, s1, r0, r1, w0 / 3f, w1 / 3f, 0.031f, 70, 255, 245, al, 0f, 1f); // its core
    }

    /** A straight piece of ripple line on the ground between two points, with the same glow, line and core as {@link #ringQuad}. */
    private static void stroke(VertexConsumer vc, PoseStack.Pose pose, float x0, float z0, float x1, float z1, float w, int al) {
        if (al <= 2) return;
        float dx = x1 - x0, dz = z1 - z0, len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-5f) return;
        float px = -dz / len, pz = dx / len; // across the piece
        float[][] layers = {{2.5f, 0.029f, 20, 190, 170, 0.4f}, {1f, 0.03f, 30, 240, 235, 1f}, {1f / 3f, 0.031f, 70, 255, 245, 1f}};
        for (float[] L : layers) {
            float ww = w * L[0], y = L[1];
            quadOnce(vc, pose, new float[]{x0 - px * ww, y, z0 - pz * ww, x0 + px * ww, y, z0 + pz * ww, x1 + px * ww, y, z1 + pz * ww, x1 - px * ww, y, z1 - pz * ww},
                    (int) L[2], (int) L[3], (int) L[4], (int) (al * L[5]), 0.1f, 0.9f);
        }
    }

    private static void band(VertexConsumer vc, PoseStack.Pose pose, float c0, float s0, float c1, float s1, float r0, float r1, float w0, float w1, float y,
                             int r, int g, int b, int a, float u0, float u1) {
        quadOnce(vc, pose, new float[]{(r0 - w0) * c0, y, (r0 - w0) * s0, (r0 + w0) * c0, y, (r0 + w0) * s0, (r1 + w1) * c1, y, (r1 + w1) * s1, (r1 - w1) * c1, y, (r1 - w1) * s1}, r, g, b, a, u0, u1);
    }

    /** A flat round glow, solid at the middle and clear at the rim, whatever the texture (a fan of triangles written both ways round). */
    private static void fan(VertexConsumer vc, PoseStack.Pose pose, float y, float rad, int r, int g, int b, int a) {
        if (a <= 2) return;
        int n = 32;
        for (int i = 0; i < n; i++) {
            double a0 = i * 2 * Math.PI / n, a1 = (i + 1) * 2 * Math.PI / n;
            float x0 = rad * (float) Math.cos(a0), z0 = rad * (float) Math.sin(a0), x1 = rad * (float) Math.cos(a1), z1 = rad * (float) Math.sin(a1);
            v(vc, pose, 0, y, 0, 0.5f, 0.5f, r, g, b, a);
            v(vc, pose, x0, y, z0, 0.5f, 0.5f, r, g, b, 0);
            v(vc, pose, x1, y, z1, 0.5f, 0.5f, r, g, b, 0);
            v(vc, pose, 0, y, 0, 0.5f, 0.5f, r, g, b, a);
            v(vc, pose, 0, y, 0, 0.5f, 0.5f, r, g, b, a);
            v(vc, pose, x1, y, z1, 0.5f, 0.5f, r, g, b, 0);
            v(vc, pose, x0, y, z0, 0.5f, 0.5f, r, g, b, 0);
            v(vc, pose, 0, y, 0, 0.5f, 0.5f, r, g, b, a);
        }
    }

    /** A flat glowing blob on the ground (the soft texture does the fading). */
    private static void disc(VertexConsumer vc, PoseStack.Pose pose, float y, float rad, int r, int g, int b, int a) {
        if (a <= 2) return;
        //? if >=1.21.2 {
        /*// no texture to give the disc its round, soft edge: a fan of triangles, solid at the middle and clear at the rim, written both ways round
        int n = 24;
        for (int i = 0; i < n; i++) {
            double a0 = i * 2 * Math.PI / n, a1 = (i + 1) * 2 * Math.PI / n;
            float x0 = rad * (float) Math.cos(a0), z0 = rad * (float) Math.sin(a0), x1 = rad * (float) Math.cos(a1), z1 = rad * (float) Math.sin(a1);
            v(vc, pose, 0, y, 0, 0.5f, 0.5f, r, g, b, a);
            v(vc, pose, x0, y, z0, 0.5f, 0.5f, r, g, b, 0);
            v(vc, pose, x1, y, z1, 0.5f, 0.5f, r, g, b, 0);
            v(vc, pose, 0, y, 0, 0.5f, 0.5f, r, g, b, a);
            v(vc, pose, 0, y, 0, 0.5f, 0.5f, r, g, b, a);
            v(vc, pose, x1, y, z1, 0.5f, 0.5f, r, g, b, 0);
            v(vc, pose, x0, y, z0, 0.5f, 0.5f, r, g, b, 0);
            v(vc, pose, 0, y, 0, 0.5f, 0.5f, r, g, b, a);
        }
        if (true) return;
        *///?}
        float[] p = {-rad, y, -rad, rad, y, -rad, rad, y, rad, -rad, y, rad};
        for (int i = 0; i < 4; i++) { // full texture, whole square
            v(vc, pose, p[i * 3], p[i * 3 + 1], p[i * 3 + 2], i == 0 || i == 3 ? 0f : 1f, i < 2 ? 0f : 1f, r, g, b, a);
        }
        for (int i = 3; i >= 0; i--) {
            v(vc, pose, p[i * 3], p[i * 3 + 1], p[i * 3 + 2], i == 0 || i == 3 ? 0f : 1f, i < 2 ? 0f : 1f, r, g, b, a);
        }
    }

    /**
     * The double helix: two big ribbons crossing round the rider. Each ribbon is like a lock of long hair: fourteen separate strands, some
     * thick and most thin, that gather into three bundles and fan apart again along its length, so they join into broad bands yet stay
     * visibly separate (the footage; there is no blurred band on top of them). Strand colours as measured from the footage: emerald
     * (40, 230, 110), cyan (20, 240, 230) and pale cyan-white (150, 255, 240), all turning cyan near the ground. Each strand is a blended
     * coloured sheath, so it keeps its colour on a bright sky, with a thin core added as light down its middle (the rule for glowing
     * effects in the game-vfx skill: blended body, small additive core). {@code grow} (0..1) is how much has unwound upward; {@code now}
     * runs at a fifth of game ticks, so it turns and wanders slowly. 16 blocks tall, a crossing every 3 blocks.
     * {@code pass}: 0 the void (all added as light, on its dark background); in the world 2 draws the sheaths (blended) and 1 the cores.
     */
    private static void helix(VertexConsumer vc, PoseStack.Pose pose, float now, float grow, int pass) {
        float height = 16f * grow, twist = -1.05f, dir = -1f; // counter-clockwise seen from above, winding and turning (the footage)
        // a segment every 0.12 blocks: the ribbon turns about 7 degrees per segment, still a smooth curve. Each strand is one ribbon standing
        // up (its width vertical) and each point is worked out once and shared by the two segments that meet there: a frame's worth of
        // spiral is about a seventh of what it was (it lagged)
        int n = Math.max(48, (int) Math.ceil(height / 0.12f));
        float[] px = new float[n + 1], pz = new float[n + 1], wds = new float[n + 1];
        for (int rib = 0; rib < 2; rib++) {
            for (int st = 0; st < 10; st++) {
                int seed = st + rib * 31, group = st % 3;
                float side = hash(seed, 71) * 2f - 1f, thick = hash(seed, 72), hue = hash(seed, 73);
                float wd0 = 0.014f + 0.03f * thick * thick; // a few thick strands among many thin ones
                int sr, sg, sb; // the strand's own colour, before the ground's cyan
                if (pass != 0) { // in the world (clips): deep green strands, a few light green, few cyan; pale ones read white in daylight
                    if (hue < 0.7f) { sr = 40; sg = 215; sb = 95; } else if (hue < 0.9f) { sr = 110; sg = 250; sb = 140; } else { sr = 20; sg = 240; sb = 230; }
                } else if (hue < 0.45f) { sr = 40; sg = 230; sb = 110; } else if (hue < 0.75f) { sr = 20; sg = 240; sb = 230; } else { sr = 150; sg = 255; sb = 240; }
                for (int i = 0; i <= n; i++) {
                    float y = -0.6f + i * height / n;
                    // the bundle wanders across the ribbon; the strand sits off its bundle by an amount that breathes, so strands pinch together and fan out
                    float bundle = (float) Math.sin(y * 0.4f + group * 2.1f + rib * 1.3f + now * 0.05f);
                    float breathe = 0.5f + 0.5f * (0.5f + 0.5f * (float) Math.sin(y * 0.7f + st * 1.9f + now * 0.1f));
                    float off = 0.3f * bundle + 0.7f * side * breathe;
                    float[] pt = strand(rib, y, now, 0.3f * off * (rib == 0 ? 1 : -1), 0.12f * off * (float) Math.cos(st * 1.7f), 0, twist, dir); // all strands share the ribbon's wander (0); they part only by off
                    px[i] = pt[0];
                    pz[i] = pt[1];
                    wds[i] = wd0 * (0.7f + 0.3f * (float) Math.sin(y * 1.9f + st * 2.3f)) * (pass == 2 ? 1.3f : 1f); // pass 2: the coloured sheath round the core
                }
                for (int i = 0; i < n; i++) {
                    float y0 = -0.6f + i * height / n, y1 = -0.6f + (i + 1) * height / n;
                    float[] q = {px[i], y0 - wds[i], pz[i], px[i], y0 + wds[i], pz[i], px[i + 1], y1 + wds[i + 1], pz[i + 1], px[i + 1], y1 - wds[i + 1], pz[i + 1]};
                    float yMid = (y0 + y1) * 0.5f, k = pass == 0 ? smooth((yMid - 0.9f) / 1.8f) : smooth((yMid - 0.2f) / 0.8f); // cyan near the ground (in the world only the lowest half block, as in the clips), the strand's own colour above
                    int cr = (int) Mth.lerp(k, 30f, sr), cg = (int) Mth.lerp(k, 215f, sg), cb = (int) Mth.lerp(k, 255f, sb);
                    float fade = (1f - (float) i / n * 0.4f) * Math.min(1f, grow * 3f) * smooth((yMid + 0.6f) / 0.8f) * smooth((height - 0.6f - yMid) / 2f); // thinner air higher up; both ends fade in, no cut-off
                    if (pass == 2) {
                        quadOnce(vc, pose, q, cr, cg, cb, (int) (220 * fade), 0f, 1f);
                        continue;
                    }
                    if (pass == 0) quad(vc, pose, widen(q, 0.3f), cr, cg, cb, (int) (150 * fade), 0f, 1f); // the void has no blended pass: the sheath is added as light too
                    // each strand's own glow, following it (no band over the lot): green, cyan near the ground, as the glow round the strands in the footage
                    int gr = (int) Mth.lerp(k, 30f, 40f), gg = (int) Mth.lerp(k, 190f, 225f), gb = (int) Mth.lerp(k, 230f, 90f);
                    if (pass == 0) quad(vc, pose, widen(q, 2f), gr, gg, gb, (int) (45 * fade), 0f, 1f);
                    else { int ga = (int) (28 * fade); quad(vc, pose, widen(q, 2f), lit(gr, ga), lit(gg, ga), lit(gb, ga), ga, 0f, 1f); } // in the world the light keeps the strength it asks for on every version
                    // the core: half as wide as the strand, so its coloured sides show, and only a third of the way to white
                    if (pass == 0) quad(vc, pose, widen(q, -0.25f), cr + (255 - cr) / 3, cg + (255 - cg) / 3, cb + (255 - cb) / 3, (int) (200 * fade), 0f, 1f);
                    else { int ca = (int) (150 * fade); quad(vc, pose, widen(q, -0.25f), lit(cr / 2, ca), lit(cg, ca), lit(cb / 2, ca), ca, 0f, 1f); } // in the world the core is the strand's own green made stronger: lifted toward white it read white on a bright floor
                }
            }
        }
        if (pass == 2) return;
        // the grain: specks densest against the ribbon and thinning out away from it, so together they read as a gradient hugging the edge
        for (int rib = 0; rib < 2; rib++) {
            for (int d = 0; d < 1500; d++) { // green dust hugging each ribbon (the footage's haze round the strands)
                float y = -0.6f + height * hash(d + rib * 1500, 61);
                float[] pt = strand(rib, y, now, 0f, 0f, 0, twist, dir);
                float u = hash(d, 62), side = hash(d, 66) < 0.5f ? -1f : 1f, gk = pass == 0 ? smooth((y - 0.9f) / 1.8f) : smooth((y - 0.2f) / 0.8f);
                float out = side * (0.06f + 0.32f * u * u); // a fine dense spray hugging the ribbon // squared: dense against the ribbon, thinning out to the side
                float size = 0.01f + 0.015f * hash(d, 63) * hash(d, 63);
                float x = pt[0] + pt[2] * out, z = pt[1] + pt[3] * out, yy = y + (hash(d, 65) - 0.5f) * 0.12f;
                int a = (int) ((40 + 190 * (1f - u * u)) * (0.55f + 0.45f * (float) Math.sin(now * 0.5f + d * 1.3f)) * Math.min(1f, grow * 3f));
                int sr = (int) Mth.lerp(gk, 20f, pass == 0 ? 10f : 30f), sg = pass == 0 ? 255 : (int) Mth.lerp(gk, 255f, 230f), sb = (int) Mth.lerp(gk, 230f, pass == 0 ? 140f : 90f); // in the world the dust is green like the strands
                if (pass != 0) { sr = lit(sr, a); sg = lit(sg, a); sb = lit(sb, a); }
                quad(vc, pose, new float[]{x - size, yy - size, z, x + size, yy - size, z, x + size, yy + size, z, x - size, yy + size, z}, sr, sg, sb, a, 0.3f, 0.7f);
            }
        }
    }

    /**
     * Where a strand of a ribbon is at height y: {x, z, radial direction x, radial direction z}. The ribbons are not even: the angle
     * and radius wander along their length and drift in time, so the loops swell and pinch the way the real ones do.
     */
    private static float[] strand(int rib, float y, float now, float offA, float offR, int st, float twist, float dir) {
        float an = rib * (float) Math.PI + y * twist + dir * now * 0.3f + offA + 0.15f * (float) Math.sin(y * 1.7f + st)
                + 0.11f * (float) Math.sin(y * 2.9f + st * 1.9f + rib) + 0.07f * (float) Math.sin(y * 5.3f + now * 0.25f + st * 3.7f);
        float rad = 1.5f + 0.3f * (float) Math.sin(y * 0.5f + now * 0.15f) + offR + 0.22f * (float) Math.sin(y * 1.3f + rib * 2f - now * 0.1f) + 0.08f * (float) Math.sin(y * 4.1f + st);
        float cx = (float) Math.cos(an), cz = (float) Math.sin(an);
        return new float[] {rad * cx, rad * cz, cx, cz};
    }

    /**
     * The Return's landing (footage): long green strands, like the spiral's, sweep round the rider well out from the body, low on one side
     * and rising to about head height on the other, turning counter-clockwise; they unwind from the floor up, then fade from their tails,
     * with fine teal specks round them. Cyan near the floor, green higher up, a light core down each strand; nothing white.
     */
    private static void swirl(VertexConsumer vc, PoseStack.Pose pose, float age, float rad, int a, boolean body) {
        if (a <= 2 || age <= 0f) return;
        float grow = smooth(age / 6f), turn = -age * 0.09f;
        int n = 28;
        for (int st = 0; st < 9; st++) {
            float hi = hash(st, 81), r0 = rad * (1.25f + 0.45f * hash(st, 82)), rise = 1.1f + 0.8f * hi; // from the floor up to about head height
            float a0 = st * 0.7f + 2.4f * hash(st, 83) + turn * (0.8f + 0.4f * hash(st, 84)), span = 2.6f + 1.2f * hash(st, 85); // long sweeps, not closed rings
            float wd0 = 0.03f + 0.05f * hash(st, 86), tail = smooth((age - 10f - 4f * hash(st, 87)) / 7f); // the tail end goes first
            boolean cyan = hash(st, 88) < 0.15f;
            float[] q = new float[12];
            for (int i = 0; i < n; i++) {
                float f0 = (float) i / n * grow, f1 = (float) (i + 1) / n * grow, fm = (f0 + f1) * 0.5f;
                if (fm < tail) continue;
                for (int e = 0; e < 2; e++) {
                    float f = e == 0 ? f0 : f1, an = a0 + span * f, rr = r0 * (1f - 0.18f * f) + 0.06f * (float) Math.sin(f * 9f + st);
                    float y = 0.04f + rise * (float) Math.pow(f, 1.3) + 0.05f * (float) Math.sin(f * 7f + age * 0.3f + st), wd = wd0 * (float) Math.pow(Math.sin(f * Math.PI), 0.6);
                    float cx = (float) Math.cos(an), cz = (float) Math.sin(an);
                    // the width stands up, leaning out a little, so the strand shows from above and from the side
                    float x = rr * cx, z = rr * cz, ox = 0.45f * cx * wd, oy = 0.9f * wd, oz = 0.45f * cz * wd;
                    if (e == 0) { q[0] = x - ox; q[1] = y - oy; q[2] = z - oz; q[3] = x + ox; q[4] = y + oy; q[5] = z + oz; }
                    else { q[9] = x - ox; q[10] = y - oy; q[11] = z - oz; q[6] = x + ox; q[7] = y + oy; q[8] = z + oz; }
                }
                float k = cyan ? 0.2f : smooth((fm - 0.02f) / 0.2f), fade = a / 255f * smooth((fm - tail) / 0.12f) * Math.min(1f, (1f - fm) * 8f + 0.2f);
                int cr = (int) Mth.lerp(k, 20f, 40f), cg = (int) Mth.lerp(k, 235f, 230f), cb = (int) Mth.lerp(k, 230f, 110f);
                if (body) { // the strand's colour: deeper green, cyan at the floor
                    quadOnce(vc, pose, widen(q, 0.4f), (int) Mth.lerp(k, 20f, 25f), (int) Mth.lerp(k, 200f, 185f), (int) Mth.lerp(k, 190f, 70f), (int) (235 * fade), 0f, 1f);
                    continue;
                }
                quad(vc, pose, widen(q, 2.2f), cr / 2, cg, cb / 2, (int) (22 * fade), 0f, 1f); // its glow, faint (added light turns pale on a bright floor)
                quad(vc, pose, widen(q, -0.5f), cr + (255 - cr) / 5, cg, cb + (255 - cb) / 5, (int) (120 * fade), 0f, 1f); // a narrow light core; the colour comes from the blended body
            }
        }
        if (body) return;
        for (int d = 0; d < 160; d++) { // fine teal specks drifting round the strands
            int st = d % 9;
            float f = hash(d, 91) * grow, hi = hash(st, 81), r0 = rad * (1.25f + 0.45f * hash(st, 82)) * (1f - 0.18f * f) + (hash(d, 92) - 0.5f) * 0.5f;
            float an = st * 0.7f + 2.4f * hash(st, 83) + age * -0.09f * (0.8f + 0.4f * hash(st, 84)) + (2.6f + 1.2f * hash(st, 85)) * f;
            float y = 0.04f + (1.1f + 0.8f * hi) * (float) Math.pow(f, 1.3) + (hash(d, 93) - 0.3f) * 0.4f + age * 0.01f, size = 0.012f + 0.014f * hash(d, 94);
            float x = r0 * (float) Math.cos(an), z = r0 * (float) Math.sin(an);
            int sa = (int) (a * 0.8f * (0.5f + 0.5f * (float) Math.sin(age * 0.6f + d * 1.7f)) * (1f - smooth((age - 12f - 6f * hash(d, 95)) / 6f)));
            if (sa > 2) quad(vc, pose, new float[]{x - size, y - size, z, x + size, y - size, z, x + size, y + size, z, x - size, y + size, z}, 30, 255, 200, sa, 0.3f, 0.7f);
        }
    }

    // ------------------------------------------------------------------------------------------------ HUD

    /** Screen-space effects of the rider's own ride (white-outs, fades, speed lines), after the HUD; also called over the world-loading screen. */
    public static void hud(GuiGraphics g, float partial, boolean belowHud) {
        Minecraft mc = Minecraft.getInstance();
        MudHandClient.State s = sync(mc);
        float c = clk();
        int w = g.guiWidth(), h = g.guiHeight();
        if (s == null && !voidOn) return;
        Matrix4f m = com.botwclimb.client.GuiShapes.pose(g);
        if (voidOn) {
            if (belowHud == mc.screen instanceof ReceivingLevelScreen) return; // the void is drawn under the HUD, or over the loading screen, which covers the HUD
            float age = c - voidClk;
            if (voidAngleFor != voidClk) { // a new void: carry on from the world's turn
                voidAngleFor = voidClk;
                voidAngle = mc.player == null ? 0f : spin(mc.player.getId(), 0f);
                voidAngleClk = c;
            }
            voidAngleClk = c;
            drawVoid(g, m, w, h, age, c);
            //? if >=1.21.6
            /*drawHelixFlat(g, w, h, c, kind == 1 ? -1f : 1f);*/
            //? if <1.21.6
            g.flush();
            //? if <1.21.6
            drawHelix3d(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), c, kind == 1 ? -1f : 1f); // the Return has the helix too (footage)
            if (!(mc.screen instanceof ReceivingLevelScreen) && mc.player != null) rider(g, mc, w, h, partial);
            return;
        }
        if (s == null || belowHud) return;
        float tp = c - phaseClk, after = c - cutClk;
        float dim = 0f, haze = 0f, tint = 0f, flash = 0f;
        switch (s.phase()) {
            case 1 -> dim = s.kind() == 1 ? 0.15f * smooth((tp - 17f) / 17f) : 0f; // a Return's scene darkens a little while the marker opens; going up nothing darkens
            case 2 -> dim = s.kind() == 1 ? 0.15f : 0f;
            default -> { }
        }
        if (cutClk > 0 && after < 60f) { // back in the world: a pale haze that thins out, over a lasting green tint
            if (s.kind() == 0) {
                haze = 0.4f * (1f - smooth(after / 8f)); // a short pale haze; the lasting green tint it once left behind read as a stray overlay
            } else {
                flash = 0.35f * (1f - smooth(after / 5f));
            }
        }
        if (dim > 0.002f) g.fill(0, 0, w, h, argb((int) (dim * 255), 0, 12, 10));
        if (haze > 0.002f) g.fillGradient(0, 0, w, h, argb((int) (haze * 255), 200, 235, 205), argb((int) (haze * 200), 170, 220, 190));
        if (tint > 0.002f) g.fill(0, 0, w, h, argb((int) (tint * 255), 50, 120, 80));
        if (flash > 0.002f) g.fill(0, 0, w, h, argb((int) (flash * 255), 150, 255, 235));
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
        //? if >=1.21.6 {
        /*com.botwclimb.client.GuiShapes.quad(x0, y0, c0, x1, y1, c1, x2, y2, c2, x3, y3, c3);
        if (true) return;
        *///?}
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
        //? if >=1.21.6 {
        /*VertexConsumer vc = null; // the shapes are collected and handed to the GUI as one element (GuiShapes)
        com.botwclimb.client.GuiShapes.begin(g);
        *///?} else {
        VertexConsumer vc = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.gui()); // the buffer the GUI itself draws with
        //?}
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
            tri(vc, m, x - sz, y - sz, argb(a, r, gr, b), x + sz, y - sz, argb(a, r, gr, b), x + sz, y + sz, argb(0, r, gr, b), x - sz, y + sz, argb(0, r, gr, b)); // dots both ways, as in the footage
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
        //? if >=1.21.6
        /*com.botwclimb.client.GuiShapes.end();*/
    }

    //? if >=1.21.6 {
    /*// From 1.21.6 the GUI is drawn in one go after the screens, with no way to swap in a 3D projection, so the helix is projected by hand:
    // the quads it writes are caught here, turned into screen positions with the same 70 degree camera, and handed to the GUI as flat shapes.
    private static final class Flat implements VertexConsumer {
        private final float w, h, focal;
        private final float[] sx = new float[4], sy = new float[4];
        private final int[] col = new int[4];
        private final boolean[] ok = new boolean[4];
        private float px, py;
        private boolean pok;
        private int n, quads;

        Flat(float w, float h) {
            this.w = w;
            this.h = h;
            this.focal = (float) (1.0 / Math.tan(Math.toRadians(35)));
        }

        public VertexConsumer addVertex(float x, float y, float z) {
            pok = z < -0.05f; // the camera looks down -z; nothing behind or on it is drawn
            if (pok) {
                px = w * 0.5f * (1f + x * focal / -z / (w / h));
                py = h * 0.5f * (1f - y * focal / -z);
            }
            return this;
        }

        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor(Mth.clamp(a, 0, 255) << 24 | r << 16 | g << 8 | b);
        }

        public VertexConsumer setColor(int argb) {
            sx[n] = px;
            sy[n] = py;
            ok[n] = pok;
            col[n] = argb;
            if (++n == 4) {
                n = 0;
                if ((quads++ & 1) == 0 && ok[0] && ok[1] && ok[2] && ok[3]) { // every quad is written twice, once each way round: keep one
                    com.botwclimb.client.GuiShapes.quad(sx[0], sy[0], col[0], sx[1], sy[1], col[1], sx[2], sy[2], col[2], sx[3], sy[3], col[3]);
                }
            }
            return this;
        }

        public VertexConsumer setUv(float u, float v) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setUv3(float u, float v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
        public VertexConsumer setLineWidth(float width) { return this; }
    }

    private static void drawHelixFlat(GuiGraphics g, int w, int h, float time, float dir) {
        PoseStack ps = new PoseStack();
        ps.translate(0, 0, -7f);
        ps.scale(1.8f, 1f, 1.8f);
        ps.translate(0, -13f + dir * (time * 0.01f % 5f), 0); // as drawHelix3d placed it
        com.botwclimb.client.GuiShapes.begin(g);
        helix(new Flat(w, h), ps.last(), time * 0.2f, 2.4f, 0); // the same spiral as in the world before the cut
        com.botwclimb.client.GuiShapes.end();
    }
    *///?}

    /**
     * The same 3D double helix as in the world, put into the void: drawn with its own perspective camera 7 blocks away (the
     * flat gui projection is swapped for it and put back), wide enough to fill about 40 % of the screen's width, much taller than
     * the screen, and turning slowly. {@code dir} makes it creep the way the rider travels.
     */
    private static void drawHelix3d(Minecraft mc, int w, int h, float time, float dir) {
        com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();
        //? if >=1.21.6 {
        /*// this version has no way to swap the projection from here: the helix is not drawn on it (see hud)
        *///?} else if >=1.21.2 {
        /*com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float) Math.toRadians(70), (float) w / h, 0.05f, 100f), com.mojang.blaze3d.ProjectionType.PERSPECTIVE);
        *///?} else {
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float) Math.toRadians(70), (float) w / h, 0.05f, 100f), com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        //?}
        var mv = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        //? if >=1.21.6 {
        /*// nothing to clear here
        *///?} else if >=1.21.5 {
        /*com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(mc.getMainRenderTarget().getDepthTexture(), 1.0); // the world's depth must not hide it
        *///?} else if >=1.21.2 {
        /*com.mojang.blaze3d.systems.RenderSystem.clear(256); // the world's depth must not hide it
        *///?} else {
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        com.mojang.blaze3d.systems.RenderSystem.clear(256, Minecraft.ON_OSX); // the world's depth must not hide it
        //?}
        PoseStack ps = new PoseStack();
        ps.translate(0, 0, -7f);
        ps.scale(1.8f, 1f, 1.8f);
        ps.translate(0, -13f + dir * (time * 0.01f % 5f), 0); // 26 blocks long, middle at the screen's middle; it creeps up to 5 blocks the way the rider travels, never far enough to show an end
        com.botwclimb.client.Buffers buffers = com.botwclimb.client.ClientCompat.immediateBuffers(mc);
        VertexConsumer vc = buffers.getBuffer(glow());
        helix(vc, ps.last(), time * 0.2f, 2.4f, 0); // the same spiral as in the world before the cut
        buffers.endBatch(glow());
        mv.popMatrix();
        //? if <1.21.2
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
            float fall = clk() - voidClk; // a Return rider turns about their own axis as they fall, about a turn in 0.9 s, upright: no tilt or sway to the sides
            float size = h * RIDER_SIZE * (kind == 1 ? 1.1f : 1f); // the Return's raised knee makes the figure shorter: drawn a tenth bigger it matches the rider going up
            Quaternionf rot = kind == 1 ? new Quaternionf().rotateZ((float) Math.PI).rotateY(fall * HOME_TURN)
                    : diveRotation(clk() - voidClk); // going up: no turn, they dive
            //? if >=1.21.9 {
            /*var dispatcher = mc.getEntityRenderDispatcher();
            var state = dispatcher.getRenderer(p).createRenderState(p, 1.0f);
            state.lightCoords = 15728880;
            state.shadowPieces.clear();
            state.outlineColor = 0;
            state.setRenderData(com.botwclimb.client.RenderData.HUD, Boolean.TRUE);
            if (state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living) {
                living.bodyRot = 0f;
                living.yRot = 0f;
                living.xRot = 0f;
                living.boundingBoxWidth /= living.scale;
                living.boundingBoxHeight /= living.scale;
                living.scale = 1f;
            }
            g.submitEntityRenderState(state, size, new Vector3f(0, p.getBbHeight() / 2f + 0.0625f, 0), rot, null, 0, 0, w, h);
            *///?} else if >=1.21.6 {
            /*InventoryScreen.renderEntityInInventory(g, 0, 0, w, h, size, new Vector3f(0, p.getBbHeight() / 2f + 0.0625f, 0), rot, null, p);
            *///?} else {
            // the footage: in the void the rider is washed in the spiral's green light (no warp)
            float cyY = riderY(h, fall);
            g.pose().pushPose();
            // the spiral's light: one green light that circles the rider with the ribbons (see hudLight). Each face of the body shines as
            // it turns toward it and falls into shadow as it turns away, like the glint on a shield; the colour is pushed past white so the
            // lit faces flash and the others stay dark.
            Vector3f mid = new Vector3f(0, p.getBbHeight() / 2f + 0.0625f, 0);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(0.55f, 1.15f, 0.8f, 1f); // past white only where both lamps meet a face
            InventoryScreen.renderEntityInInventory(g, w * 0.5f, cyY, size, mid, rot, null, p);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            g.pose().popPose();
            //?}
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

    /** Sounds already played, by rider, ride and moment, so each plays once however the ticks fall. */
    private static final java.util.Set<String> PLAYED = new java.util.HashSet<>();
    private static boolean voidWas;
    /** When the ascent's rumble started (wall clock, ticks). */
    private static float shakeClk = -1e9f;
    /** The camera's jolt with the rumble, measured frame by frame on the footage (ascent.mp4 2.33 - 2.64 s): where the picture sits, as a share of the screen height (positive is down), 60 a second. */
    private static final float[] SHAKE = {0f, 0.054f, 0.059f, 0.051f, 0.036f, 0.020f, 0.005f, 0.016f, 0.031f, 0.043f, 0.053f, 0.053f, 0.032f, 0.009f, -0.021f, -0.023f, -0.014f, -0.009f, -0.001f, 0f};

    /** Degrees to add to the camera's pitch now: the picture moves down by SHAKE's share of the screen when the camera looks up by that share of the field of view. */
    public static float shake() {
        float i = (clk() - shakeClk) * 3f; // ticks to 60ths of a second
        if (i < 0f || i >= SHAKE.length - 1) return 0f;
        int k = (int) i;
        return -Mth.lerp(i - k, SHAKE[k], SHAKE[k + 1]) * Minecraft.getInstance().options.fov().get();
    }

    /**
     * The ride's sounds, the user's recordings timed to the footage. Going up: the void (heard by the rider, who alone sees it), then
     * the touch-down and jump. Coming home: the rider hears one recording from just before the void to the landing; others hear its landing.
     */
    public static void sounds(Minecraft mc) {
        sync(mc);
        if (mc.level == null) return;
        // going up the recording starts as the rider stops dead, 24 ticks before the cut: its rumble is the hit before passing through, with the camera's jolt, and its second swell (1.3 s in) the entry into the void
        if (kind == 0 && (phase == 2 && clk() - phaseClk >= SWIM_AT || voidOn) && PLAYED.add("up:" + rideSince)) {
            hear(mc, com.botwclimb.BotwClimb.SOUND_RIDE_VOID_UP.get());
            shakeClk = clk();
        }
        if ((phase == 2 || voidOn) && kind == 1 && PLAYED.add("down:" + rideSince)) hear(mc, com.botwclimb.BotwClimb.SOUND_RIDE_VOID.get()); // coming home the recording starts as the rider starts turning in the marker, before the cut into the void
        voidWas = voidOn;
        if (PLAYED.size() > 256) PLAYED.clear();
        long now = mc.level.getGameTime();
        for (var e : MudHandClient.all().entrySet()) {
            Entity who = mc.level.getEntity(e.getKey());
            MudHandClient.State s = e.getValue();
            if (who == null) continue;
            long t = now - s.since(), tp = now - s.phaseSince();
            if (s.kind() == 0) {
                if (s.phase() == 5) { // the recording of the touch-down and jump (cut before its chime), then the swirls get the landing slashes of the way home, as their burst rises
                    if (who != mc.player) at(mc, who, s, "arrive", tp, 19, com.botwclimb.BotwClimb.SOUND_RIDE_ARRIVE.get(), 1f); // others: a mono copy placed at the rider
                    else if (tp >= 19 && tp < 23 && PLAYED.add(who.getId() + ":" + s.since() + ":arrive")) hear(mc, com.botwclimb.BotwClimb.SOUND_RIDE_ARRIVE_RIDER.get()); // the rider: in stereo, as recorded
                    if (who != mc.player) at(mc, who, s, "land", tp, (long) LANDS, com.botwclimb.BotwClimb.SOUND_RIDE_LAND.get(), 1f);
                    else if (tp >= LANDS && tp < LANDS + 4 && PLAYED.add(who.getId() + ":" + s.since() + ":land")) hear(mc, com.botwclimb.BotwClimb.SOUND_RIDE_LAND_RIDER.get());
                }
            } else if (s.phase() == 5) { // the recording's landing slashes, as the slashes rise on the ground (once the turn has stopped)
                if (who != mc.player) at(mc, who, s, "land", tp, (long) SPIN_STOP, com.botwclimb.BotwClimb.SOUND_RIDE_LAND.get(), 1f); // others: a mono copy placed at the rider
                else if (tp >= SPIN_STOP && tp < SPIN_STOP + 4 && PLAYED.add(who.getId() + ":" + s.since() + ":land")) hear(mc, com.botwclimb.BotwClimb.SOUND_RIDE_LAND_RIDER.get());
            }
        }
    }

    private static net.minecraft.client.resources.sounds.SoundInstance riderSound;

    /** The rider's own ride sound: not placed in the world and not tied to the player, who is replaced when the ride changes world. */
    private static void hear(Minecraft mc, net.minecraft.sounds.SoundEvent sound) {
        if (RECORDING) com.mojang.logging.LogUtils.getLogger().info("ride sound {} at frame {} volume {}", net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(sound), com.botwclimb.dev.AutoShots.frame(), 1f);
        riderSound = new net.minecraft.client.resources.sounds.AbstractSoundInstance(sound, net.minecraft.sounds.SoundSource.PLAYERS, mc.level.getRandom()) {{
            relative = true;
            attenuation = net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE;
        }};
        mc.getSoundManager().play(riderSound);
        // straight to the speakers as recorded: OpenAL would otherwise spread the two channels as virtual speakers, or through HRTF if it is forced on in alsoft.ini
        var handle = ((com.botwclimb.mixin.SoundEngineAccess) ((com.botwclimb.mixin.SoundManagerAccess) mc.getSoundManager()).botwclimb$engine()).botwclimb$channels().get(riderSound);
        if (handle != null) handle.execute(ch -> org.lwjgl.openal.AL10.alSourcei(((com.botwclimb.mixin.AlSourceAccess) ch).botwclimb$source(), org.lwjgl.openal.SOFTDirectChannels.AL_DIRECT_CHANNELS_SOFT, org.lwjgl.openal.AL10.AL_TRUE));
    }

    /** Whether the rider's ride sound is playing; it is kept when the world change stops all sounds (see SoundManagerMixin). */
    public static boolean riderSoundPlaying() {
        return riderSound != null && Minecraft.getInstance().getSoundManager().isActive(riderSound);
    }

    private static void at(Minecraft mc, Entity who, MudHandClient.State s, String name, long ticks, long when, net.minecraft.sounds.SoundEvent sound, float volume) {
        if (ticks >= when && ticks < when + 4 && PLAYED.add(who.getId() + ":" + s.since() + ":" + name)) play(mc, who, sound, volume);
    }

    /** A sound that follows the rider (they move fast and far in a ride). */
    private static void play(Minecraft mc, Entity who, net.minecraft.sounds.SoundEvent sound, float volume) {
        if (RECORDING) com.mojang.logging.LogUtils.getLogger().info("ride sound {} at frame {} volume {}", net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(sound), com.botwclimb.dev.AutoShots.frame(), volume); // to lay the sounds under the recorded video
        mc.getSoundManager().play(new net.minecraft.client.resources.sounds.EntityBoundSoundInstance(sound, net.minecraft.sounds.SoundSource.PLAYERS, volume, 1f, who, mc.level.getRandom().nextLong()));
    }

    /** Once per client tick, for anything that has to be stepped (particles, changes to what the rider sees of the world). */
    public static void tick(Minecraft mc) {
        sync(mc);
        if (mc.level == null) return;
        var rand = mc.level.getRandom();
        long now = mc.level.getGameTime();
        for (var e : MudHandClient.all().entrySet()) {
            Entity who = mc.level.getEntity(e.getKey());
            MudHandClient.State s = e.getValue();
            if (who == null) continue;
            float t = now - s.since(), tp = now - s.phaseSince();
            double x = who.getX(), y = who.getY(), z = who.getZ();
            boolean home = s.kind() == 1;
            if (!home && s.phase() >= 4 && who instanceof net.minecraft.world.entity.LivingEntity le) { // arriving: crouch and jump the way they look, not where the body last pointed
                le.yBodyRot = le.yBodyRotO = le.yHeadRot = le.yHeadRotO = le.getYRot(); // the view's direction: after the world change the head's turn is stale and pointed the body sideways
            }
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
                    for (int i = 0; i < 3 && tp < 12f && !home; i++) { // a Return's landing has its own specks
                        double an = rand.nextDouble() * 6.283, r = 0.3 + rand.nextDouble() * 0.8;
                        mc.level.addParticle(dust(0.3f, 1f, 0.5f), x + Math.cos(an) * r, y + 0.1, z + Math.sin(an) * r, Math.cos(an + 1.57) * 0.12, 0.05 + rand.nextDouble() * 0.05, Math.sin(an + 1.57) * 0.12);
                    }
                }
                default -> { }
            }
        }
    }

    private static DustParticleOptions dust(float r, float g, float b) {
        //? if >=1.21.2 {
        /*return new DustParticleOptions(((int) (r * 255) & 255) << 16 | ((int) (g * 255) & 255) << 8 | ((int) (b * 255) & 255), 1f);
        *///?} else {
        return new DustParticleOptions(new Vector3f(r, g, b), 1f);
        //?}
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
        return 0f; // no blur: smearing the screen up and down stretched the rider
    }

    private static float smooth(float u) {
        u = Mth.clamp(u, 0f, 1f);
        return u * u * (3 - 2 * u);
    }
}
