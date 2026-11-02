package com.botwclimb.client;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.climb.ClimbConstants;
import com.botwclimb.common.climb.ClimbController;
import com.botwclimb.common.climb.ClimbData;
import com.botwclimb.common.climb.ClimbState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * The stamina meter. The centre wheel is a thick ring around an empty hole; every further wheel is a thinner ring outside
 * it, set off by a thin white line. A ring's length is that wheel's capacity (it grows with the upgrades), the bright
 * part is what is left. Plain 2D with screen-pixel anti-aliased edges. A jump flashes the arc it took and then drains it,
 * a held bow shot shows its cost, and a finished refill glows.
 */
public final class StaminaHud {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "stamina");
    /** inner / outer radius of each wheel in pixels. */
    public static final float[][] RINGS = {{4.6f, 13.4f}, {15.2f, 20.2f}, {21.8f, 26.8f}}; // thick centre wheel, then a solid ring per extra wheel, a clear gap between them
    private static final int GREEN = 0x00F25A, GREY = 0x262626, RED = 0xFF3B1D, WHITE = 0xFFFFFF, FLASH = 0xFF1F1F, PALE = 0xE7FFFF, GLOWG = 0x0D8158, GLOW = 0xFFE5E5; // colours sampled from the reference wheel

    private static float shown = -1f, trail = -1f, prevStamina = -1f; // eased level; the red "being spent" end of the arc; level one tick ago
    private static int drainTicks, lastTick = -1;
    private static float flashFrom, flashTo; // the arc a jump just spent (units: new and old level)
    private static boolean flashActive, manualFlash, wasHeld;
    private static float manualProg, heldTop;
    private static int lastJumpSeq, lastShotSeq;
    private static boolean wasFull = true;
    private static float fullGlow = 99f; // ticks since the wheel finished refilling: drives the glow
    private static final float MIN_FLASH_UNITS = 24f; // about 3% of a wheel
    private static int lastBusy = -1000;
    private static int passes = 1;
    private static float feather = 0.3f; // half the width of the soft edge, in GUI pixels
    private static float appear;                   // 0..1 pop-in

    private StaminaHud() {}

    /** For other screens that draw the same wheel arcs: sets up the shader, blending and edge softness. Pair with {@link #endDraw()}. */
    public static void beginDraw(Minecraft mc) {
        feather = (float) (0.55 / Math.max(1.0, mc.getWindow().getGuiScale()));
        passes = GL11.glIsEnabled(GL11.GL_CULL_FACE) ? 2 : 1;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ClientCompat.positionColorShader();
    }

    public static void endDraw() {
        RenderSystem.disableBlend();
    }

    @EventBusSubscriber(modid = BotwClimb.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent
        public static void layers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(LAYER, StaminaHud::render);
        }
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || !p.hasData(BotwClimb.CLIMB)) return;
        ClimbData d = p.getData(BotwClimb.CLIMB);
        if (d.stamina < 0) return;
        float cap = ClimbController.maxStamina(p);
        boolean busy = d.stamina < cap - 0.5f || d.state != ClimbState.NONE || d.staminaFlash > 0 || d.exhausted;
        if (busy) lastBusy = p.tickCount;
        float dt = Math.min(delta.getRealtimeDeltaTicks(), 4f);
        float vis = busy ? 1f : Mth.clamp(1f - (p.tickCount - lastBusy - 30) / 10f, 0f, 1f); // stays a moment once full, then fades
        appear = Mth.clamp(appear + (vis > 0f ? 0.18f : -0.18f) * dt, 0f, 1f);
        if (vis <= 0f && appear <= 0f) { shown = trail = -1f; return; }
        if (shown < 0f) { shown = trail = d.stamina; prevStamina = d.stamina; lastJumpSeq = d.jumpSpentSeq; lastShotSeq = d.shotSpentSeq; }
        if (d.jumpSpentSeq != lastJumpSeq) { // a jump just took stamina, however little there was left: flash exactly that arc
            lastJumpSeq = d.jumpSpentSeq;
            flashFrom = d.stamina;
            flashTo = d.stamina + Math.max(d.jumpSpent, MIN_FLASH_UNITS); // never smaller than a visible piece
            flashActive = true;
            manualFlash = false;
        }

        if (p.tickCount != lastTick) { // once a tick: is stamina being spent right now (climbing or a jump)?
            lastTick = p.tickCount;
            if (d.stamina < prevStamina - 0.05f) drainTicks = 6;
            else if (drainTicks > 0) drainTicks--;
            prevStamina = d.stamina;
        }
        boolean draining = drainTicks > 0;
        // the jump's red arc: all of it at once and bright; once the jump animation is 25% done it drains smoothly, gone when the jump has settled
        float flashFrac = 0f, flashGlow = 0f;
        boolean held = d.bowHold > 0f;
        if (held) { // holding a shot: the arc it will take stays lit and bright (the same hold as a jump's flash) until it is fired
            flashFrom = Math.max(0f, d.stamina - d.bowHold);
            flashTo = heldTop = d.stamina;
            flashActive = true; manualFlash = false;
            flashFrac = 1f;
            flashGlow = 0.5f + 0.5f * Mth.cos((p.tickCount + delta.getGameTimeDeltaPartialTick(false)) * 0.9f);
        } else if (wasHeld && d.stamina < heldTop - 0.5f) { // fired: now the drain plays, from the held arc down to nothing
            flashFrom = d.stamina; flashTo = heldTop;
            flashActive = true; manualFlash = true; manualProg = 0.25f;
        } else if (wasHeld) flashActive = false;
        wasHeld = held;
        if (d.shotSpentSeq != lastShotSeq) { // a shot was paid: drain exactly that arc, however little (or all) of the wheel it was
            lastShotSeq = d.shotSpentSeq;
            flashFrom = d.stamina;
            flashTo = d.stamina + Math.max(d.shotSpent, MIN_FLASH_UNITS);
            flashActive = true; manualFlash = true; manualProg = 0.25f;
        }
        if (flashActive && !held) {
            float prog = 1f;
            if (manualFlash) {
                manualProg += dt / 14f;
                prog = manualProg;
            } else if (d.state == ClimbState.CLIMB_JUMP && d.plan != null) {
                float total = ClimbConstants.JUMP_WINDUP_TICKS + d.plan.length() + 1f;
                prog = (d.jumpTick + delta.getGameTimeDeltaPartialTick(false)) * (float) d.jumpSpeed / total; // jump time, not ticks
            } else if (d.state == ClimbState.LEDGE_TRANSITION) { // a ledge jump: its length is about 28 ticks
                prog = (d.ledgeTicks + delta.getGameTimeDeltaPartialTick(false)) / 28f;
            }
            if (prog >= 1f) flashActive = false;
            else {
                float x = Mth.clamp((prog - 0.25f) / 0.75f, 0f, 1f);
                flashFrac = 1f - x * x * (3f - 2f * x);
                if (prog < 0.25f) flashGlow = 0.5f + 0.5f * Mth.cos(prog / 0.25f * (float) Math.PI * 3f); // 1.5 pulses of white-hot: it FLASHES, then settles to red and drains
            }
        }
        boolean flashing = flashActive && flashFrac > 0f;
        // the displayed level eases to the real one
        float before = shown;
        // going up it fills at a steady pace every frame (recovery only changes once a tick, so following it directly looks steppy),
        // slightly faster than the recovery itself so it is never more than a tick behind and closes the moment it is full; drops are eased
        if (d.stamina >= shown) shown = Math.min(d.stamina, shown + ClimbConstants.STAMINA_REGEN * 1.25f * dt);
        else shown += (d.stamina - shown) * Math.min(1f, 0.4f * dt);
        boolean fullNow = shown >= cap - 1.0f; // judged on what is DRAWN: the ring must have visibly closed first
        if (fullNow && !wasFull && d.state == ClimbState.NONE && !d.exhausted) { fullGlow = 0f; lastBusy = p.tickCount; } // it just finished refilling
        wasFull = fullNow;
        fullGlow = Math.min(99f, fullGlow + dt);
        // full strength immediately, then a quick fade (about 0.4 s): 0 = no glow, 1 = full
        float glowI = fullGlow >= 10f ? 0f : (float) Math.pow(1f - fullGlow / 10f, 1.4); // full strength the very instant it fills, gone in 0.5 s
        // the red part: what has just been spent. It stays a sliver behind the green while you keep spending, a jump leaves its whole
        // cost there to shrink away smoothly, and once you stop it drops back down to the green.
        if (trail < before) trail = before;
        if (draining) trail = Math.max(trail - (trail - shown) * Math.min(1f, 0.03f * dt), shown + 6f);
        else trail += (shown - trail) * Math.min(1f, 0.16f * dt);
        if (flashing) trail = shown; // the jump has its own flash
        trail = Math.min(trail, shown + 40f); // the red stays a thin piece, even after a whole jump
        if (!draining && trail - shown < 0.5f) trail = shown;

        float alpha = Math.min(vis, appear);
        float scale = 0.82f + 0.18f * Mth.sin(appear * (float) Math.PI / 2f); // pops in
        float time = (p.tickCount + delta.getGameTimeDeltaPartialTick(false));
        // green; completely red with 10% of the first wheel (75 units) or less left, whatever the capacity, and the whole time it recovers from running out
        boolean red = d.exhausted || d.staminaFlash > 0 || shown <= ClimbConstants.STAMINA_WHEEL * 0.10f;
        int color = red ? RED : lerpRgb(GREEN, PALE, glowI); // turns pale cyan-white while it glows
        float blink = 0.5f + 0.5f * Mth.sin(time * 0.7875f); // the red overlay on the part being spent blinks (another 25% slower: 1.05 * 0.75)

        // above and to the left of the player, who is in the middle of the screen in third person
        // size and place by the window's real pixels, not by GUI units, so the wheel is the same share of the screen windowed, fullscreen
        // and at every GUI scale (GUI units are whole-number multiples of pixels, so they jump between sizes)
        float gs = (float) Math.max(1.0, mc.getWindow().getGuiScale()), px = mc.getWindow().getHeight();
        float cx = g.guiWidth() / 2f - 0.128f * px / gs, cy = g.guiHeight() / 2f - 0.090f * px / gs; // nudged down and right as it shrank, so its place on screen feels the same
        // never touches the culling state: with culling on, every strip is also emitted with the opposite winding, so one of the two is always front-facing
        passes = GL11.glIsEnabled(GL11.GL_CULL_FACE) ? 2 : 1;
        feather = (float) (0.55 / Math.max(1.0, mc.getWindow().getGuiScale())); // about half a screen pixel on each side of an edge
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ClientCompat.positionColorShader();
        float s = scale * (0.0476f * px / (gs * RINGS[2][1])); // the outermost of three wheels reaches 4.76% of the window height (15% smaller than 5.6%)
        for (int i = 0; i < RINGS.length; i++) {
            float lo = ClimbConstants.STAMINA_WHEEL * i;
            float wheelCap = Mth.clamp(cap - lo, 0f, ClimbConstants.STAMINA_WHEEL);
            if (wheelCap <= 0f) continue;
            float r0 = RINGS[i][0] * s, r1 = RINGS[i][1] * s;
            float full = wheelCap / ClimbConstants.STAMINA_WHEEL;
            float left = Mth.clamp(shown - lo, 0f, wheelCap) / ClimbConstants.STAMINA_WHEEL;
            ring(m, cx, cy, r0, r1, 0f, full, GREY, 0.7f * alpha);                   // what the wheel can hold
            float tr = Mth.clamp(trail - lo, 0f, wheelCap) / ClimbConstants.STAMINA_WHEEL;
            if (glowI > 0.01f && left > 0f) { // the refill glow: dark-green light spreading off both edges of the lit arc (outside, the hollow in the middle, between wheels)
                glow(m, cx, cy, r0, r1, left, GLOWG, 8.5f * s, 0.5f * glowI * alpha); // dark green: see-through, fading out the farther from the ring
                glow(m, cx, cy, r0, r1, left, PALE, 1.8f * s, 0.4f * glowI * alpha);  // a thin pale edge right against the ring
            }
            if (left > 0f) ring(m, cx, cy, r0, r1, 0f, left, color, alpha);          // what is left
            // the part being spent: a blinking red overlay on top of the green end (steady and complete once it is all red anyway)
            if (!red && tr > left && left > 0f) ring(m, cx, cy, r0, r1, Math.max(0f, left - (tr - left)), left, RED, alpha * blink);
            else if (!red && tr > left) ring(m, cx, cy, r0, r1, left, tr, RED, alpha * blink);
            // only a wheel the lost arc actually touches gets the flash: elsewhere its arc is empty, but the glow and outline grown around it would still be drawn as a spike
            float flashLo = flashFrom - lo, flashHi = flashFrom + (flashTo - flashFrom) * flashFrac - lo;
            if (flashing && flashHi > 0f && flashLo < wheelCap) { // a quick flash of the arc that was just lost: right where it was, thin, gone in a few ticks
                float c0 = Mth.clamp(flashFrom - lo, 0f, wheelCap) / ClimbConstants.STAMINA_WHEEL;
                float c1 = Mth.clamp(flashFrom + (flashTo - flashFrom) * flashFrac - lo, 0f, wheelCap) / ClimbConstants.STAMINA_WHEEL;
                // the lost arc lit up: a solid pink shape with a red outline and a red glow all the way round it (outer edge, inner edge and both sides)
                float mid = (r0 + r1) / 2f;
                float turn = (float) (2 * Math.PI * mid);                            // circumference at the middle of the ring, to turn pixels into a fraction of the circle
                float fit = Mth.clamp((r1 - r0) / (8.8f * s), 0.4f, 1f);              // the glow of a thinner ring is smaller, so it cannot flood the rings beside it
                float[] grow = {3.4f * s * fit, 2.4f * s * fit, 1.5f * s * fit};      // glow layers, widest and faintest first
                float[] glowA = {0.14f, 0.22f, 0.36f};
                float sync = 0.55f + 0.45f * flashGlow; // the red outline and glow flash together with the pink
                for (int k = 0; k < grow.length; k++) ring(m, cx, cy, r0 - grow[k], r1 + grow[k], c0 - grow[k] / turn, c1 + grow[k] / turn, FLASH, Math.min(1f, glowA[k] * 1.3f * sync) * alpha);
                float o = 0.6f * s * fit;
                ring(m, cx, cy, r0 - o, r1 + o, c0 - o / turn, c1 + o / turn, FLASH, 0.7f * sync * alpha); // the outline, 30% more transparent
                int pink = lerpRgb(GLOW, 0xFFFFFF, 0.6f * flashGlow);                          // one solid bright arc that flashes whiter
                ring(m, cx, cy, r0, r1, c0, c1, pink, alpha);
            }
        }
        RenderSystem.disableBlend();
    }

    /** A ring segment from fraction {@code from} to {@code to} of a full turn (counter-clockwise from the top): hard edges, no blur. */
    public static void ring(Matrix4f m, float cx, float cy, float r0, float r1, float from, float to, int rgb, float alpha) {
        if (to - from <= 0.0005f) return;
        // anti-aliasing: the edges fade over about one screen pixel (feather is in GUI pixels, so it is the same at every GUI scale)
        float f = feather;
        strip(m, cx, cy, r0 + f, r1 - f, from, to, rgb, alpha, alpha);
        strip(m, cx, cy, Math.max(0f, r0 - f), r0 + f, from, to, rgb, 0f, alpha);
        strip(m, cx, cy, r1 - f, r1 + f, from, to, rgb, alpha, 0f);
    }

    private static void strip(Matrix4f m, float cx, float cy, float ra, float rb, float from, float to, int rgb, float aIn, float aOut) {
        if (rb <= ra) return;
        int n = Math.max(3, (int) Math.ceil(208 * (to - from)));
        int in = argb(rgb, aIn), out = argb(rgb, aOut);
        for (int pass = 0; pass < passes; pass++) {
            BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= n; i++) {
                double ang = -Math.PI / 2 - 2 * Math.PI * (from + (to - from) * i / n); // counter-clockwise from the top
                float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
                if (pass == 0) {
                    buf.addVertex(m, cx + cos * ra, cy + sin * ra, 0f).setColor(in);
                    buf.addVertex(m, cx + cos * rb, cy + sin * rb, 0f).setColor(out);
                } else { // same strip, opposite winding
                    buf.addVertex(m, cx + cos * rb, cy + sin * rb, 0f).setColor(out);
                    buf.addVertex(m, cx + cos * ra, cy + sin * ra, 0f).setColor(in);
                }
            }
            BufferUploader.drawWithShader(buf.buildOrThrow());
        }
    }

    private static float smooth01(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    /** Light spreading off both edges of an arc: strongest at the ring, fading smoothly (quadratically) to nothing at width {@code w}. */
    private static void glow(Matrix4f m, float cx, float cy, float r0, float r1, float to, int rgb, float w, float peak) {
        int steps = 7;
        for (int k = 0; k < steps; k++) {
            float t0 = (float) k / steps, t1 = (float) (k + 1) / steps;
            float a0 = peak * (1f - t0) * (1f - t0), a1 = peak * (1f - t1) * (1f - t1);
            strip(m, cx, cy, r1 + w * t0, r1 + w * t1, 0f, to, rgb, a0, a1);                                   // outside the ring
            strip(m, cx, cy, Math.max(0.5f, r0 - w * t1), Math.max(0.5f, r0 - w * t0), 0f, to, rgb, a1, a0); // inside it (the hollow, and between wheels)
        }
    }

    private static int lerpRgb(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255), gg = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (gg << 8) | bl;
    }

    private static int argb(int rgb, float alpha) {
        return (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }
}
