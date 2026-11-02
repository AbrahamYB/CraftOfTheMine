package com.botwclimb.client.paraglider;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.paraglider.ParagliderData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Two pale wind streaks behind the canopy's trailing corners. Each streak is a ribbon through the last few
 * emitter positions (world space, so a turn leaves an arc), shown only above a speed threshold. Purely client-side:
 * every client derives it from the glider state and position of each gliding player.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class WindTrails {
    private static final int POINTS = 24;
    /** Emitters relative to the player: sideways, behind the travel direction, height above the feet (blocks). */
    private static final double SIDE = 0.85, BACK = 0.55, HEIGHT = 2.70;
    /** Horizontal speed (blocks/tick) -> strength and length. Cruise is 0.312, rocket momentum goes far past it. */
    private static final float[] SPEED = {0.10f, 0.30f, 0.65f};
    private static final float[] ALPHA = {0.55f, 0.95f, 1.0f};
    private static final float[] LENGTH = {1.6f, 3.4f, 6.5f};
    private static final float HALF_WIDTH = 0.16f;
    /** A soft pale-blue band behind the white core, so a streak shows against bright sky as well as dark ground. */
    private static final float HALO_WIDTH = 2.1f, HALO_ALPHA = 0.5f;

    private static final Map<UUID, Trail> TRAILS = new HashMap<>();

    private static final class Trail {
        final Vec3[][] pts = new Vec3[2][POINTS]; // [left, right][newest..oldest]
        int n;
        Vec3 prev;
        float alpha, length, fade;
    }

    private WindTrails() {}

    /** Trails of players who left (or when the world is left) would never fade out by themselves: drop them. */
    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        TRAILS.clear();
    }

    private static float table(float[] ys, float v) {
        if (v <= SPEED[0]) return ys[0];
        for (int i = 1; i < SPEED.length; i++) {
            if (v <= SPEED[i]) return Mth.lerp((v - SPEED[i - 1]) / (SPEED[i] - SPEED[i - 1]), ys[i - 1], ys[i]);
        }
        return ys[ys.length - 1];
    }

    private static Vec3 emitter(Vec3 base, float heading, int side) {
        double h = Math.toRadians(heading), fx = -Math.sin(h), fz = Math.cos(h);
        double s = side == 0 ? -SIDE : SIDE; // left, right of the travel direction
        return base.add(-fx * BACK + -fz * s, HEIGHT, -fz * BACK + fx * s);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (!p.level().isClientSide() || !(p instanceof AbstractClientPlayer) || !p.hasData(BotwClimb.PARAGLIDER)) return;
        ParagliderData g = p.getData(BotwClimb.PARAGLIDER);
        Trail t = TRAILS.get(p.getUUID());
        if (t == null) {
            if (!g.gliding) return;
            t = new Trail();
            TRAILS.put(p.getUUID(), t);
        }
        Vec3 pos = p.position();
        double speed = t.prev == null ? 0 : Math.hypot(pos.x - t.prev.x, pos.z - t.prev.z);
        t.prev = pos;
        boolean emit = g.gliding && g.deploy > 0.5f && !p.onGround() && !p.isInWater() && speed >= SPEED[0];
        if (emit) {
            for (int s = 0; s < 2; s++) {
                System.arraycopy(t.pts[s], 0, t.pts[s], 1, POINTS - 1);
                t.pts[s][0] = emitter(pos, g.heading, s);
                // vanilla puffs along the same streaks: they are drawn by the game's own particle pass, so wind shows even if the ribbon does not
                p.level().addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, t.pts[s][0].x, t.pts[s][0].y, t.pts[s][0].z, 0, 0, 0);
            }
            t.n = Math.min(t.n + 1, POINTS);
            t.alpha += (table(ALPHA, (float) speed) - t.alpha) * 0.3f;
            t.length += (table(LENGTH, (float) speed) - t.length) * 0.3f;
            t.fade = 1f;
        } else {
            t.fade -= 0.2f; // fades out quickly, the points stay where the air was
            if (t.fade <= 0f) TRAILS.remove(p.getUUID());
        }
    }

    //? if >=1.21.11 {
    /*@SubscribeEvent
    public static void onRender(RenderLevelStageEvent.AfterParticles event) {
        if (TRAILS.isEmpty()) return;
    *///?} else {
    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || TRAILS.isEmpty()) return;
    //?}
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = com.botwclimb.client.ClientCompat.partialTick(false);
        Vec3 cam = com.botwclimb.client.ClientCompat.cameraPos();
        PoseStack ps = com.botwclimb.client.ClientCompat.levelPose(event);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        boolean any = false;
        for (Player p : mc.level.players()) {
            Trail t = TRAILS.get(p.getUUID());
            if (t == null || t.n < 1 || (p == mc.player && mc.options.getCameraType().isFirstPerson())) continue;
            ParagliderData g = p.getData(BotwClimb.PARAGLIDER);
            boolean live = g.gliding && t.fade >= 1f;
            Vec3 base = new Vec3(Mth.lerp(pt, p.xo, p.getX()), Mth.lerp(pt, p.yo, p.getY()), Mth.lerp(pt, p.zo, p.getZ()));
            float heading = Mth.rotLerp(pt, g.headingO, g.heading);
            for (int s = 0; s < 2; s++) {
                Vec3 head = live ? emitter(base, heading, s) : null;
                any |= ribbon(vc, ps, cam, t, s, head, HALO_WIDTH, HALO_ALPHA, 0.58f, 0.76f, 0.95f); // pale-blue band first
                any |= ribbon(vc, ps, cam, t, s, head, 1f, 1f, 0.97f, 0.99f, 1f);                     // white core on top
            }
        }
        if (any) buffers.endBatch(RenderType.debugQuads());
        TRAILS.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null); // a player who unloaded mid-trail leaves no entry behind
    }

    /** Draws one streak from the (optional) live head back through the stored points, cut off at the trail length. */
    private static boolean ribbon(VertexConsumer vc, PoseStack ps, Vec3 cam, Trail t, int side, Vec3 head,
                                  float widthScale, float alphaScale, float cr, float cg, float cb) {
        float strength = t.alpha * Mth.clamp(t.fade, 0f, 1f);
        if (strength < 0.01f || t.length <= 0.05f) return false;
        Vec3 a = head != null ? head : t.pts[side][0];
        int next = head != null ? 0 : 1;
        float dist = 0f;
        Vec3 sideA = null;
        float alphaA = strength * alphaScale, widthA = HALF_WIDTH * widthScale;
        boolean drew = false;
        for (int i = next; i < t.n && dist < t.length; i++) {
            Vec3 b = t.pts[side][i];
            double seg = a.distanceTo(b);
            if (seg < 1.0e-4) continue;
            float end = dist + (float) seg;
            if (end > t.length) { // cut the last segment at the trail length
                b = a.lerp(b, (t.length - dist) / seg);
                end = t.length;
            }
            Vec3 dir = b.subtract(a).normalize();
            Vec3 view = a.subtract(cam);
            Vec3 w = dir.cross(view);
            if (w.lengthSqr() < 1.0e-8) { a = b; dist = end; continue; }
            w = w.normalize();
            float k = end / t.length;
            float alphaB = strength * alphaScale * (1f - k), widthB = HALF_WIDTH * widthScale * (1f - 0.85f * k);
            if (sideA == null) sideA = w;
            quad(vc, ps, cam, a, b, sideA, w, widthA, widthB, alphaA, alphaB, cr, cg, cb);
            sideA = w;
            a = b;
            dist = end;
            alphaA = alphaB;
            widthA = widthB;
            drew = true;
        }
        return drew;
    }

    private static void quad(VertexConsumer vc, PoseStack ps, Vec3 cam, Vec3 a, Vec3 b, Vec3 wa, Vec3 wb,
                             float widthA, float widthB, float alphaA, float alphaB, float cr, float cg, float cb) {
        var m = ps.last().pose();
        Vec3 a1 = a.add(wa.scale(widthA)), a2 = a.subtract(wa.scale(widthA));
        Vec3 b1 = b.add(wb.scale(widthB)), b2 = b.subtract(wb.scale(widthB));
        // both windings, since the strip faces the camera whichever way it was built 
        float aa = alphaA, ab = alphaB;
        vert(vc, m, cam, a1, aa, cr, cg, cb); vert(vc, m, cam, a2, aa, cr, cg, cb); vert(vc, m, cam, b2, ab, cr, cg, cb); vert(vc, m, cam, b1, ab, cr, cg, cb);
        vert(vc, m, cam, b1, ab, cr, cg, cb); vert(vc, m, cam, b2, ab, cr, cg, cb); vert(vc, m, cam, a2, aa, cr, cg, cb); vert(vc, m, cam, a1, aa, cr, cg, cb);
    }

    private static void vert(VertexConsumer vc, org.joml.Matrix4f m, Vec3 cam, Vec3 v, float alpha, float cr, float cg, float cb) {
        vc.addVertex(m, (float) (v.x - cam.x), (float) (v.y - cam.y), (float) (v.z - cam.z)).setColor(cr, cg, cb, Mth.clamp(alpha, 0f, 1f));
    }
}
