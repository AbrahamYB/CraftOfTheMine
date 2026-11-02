package com.botwclimb.client.bullettime;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.bullettime.BulletConstants;
import com.botwclimb.common.bullettime.BulletFall;
import com.botwclimb.common.climb.ClimbConstants;
import com.botwclimb.common.climb.ClimbData;
import com.botwclimb.common.climb.Stamina;
import com.botwclimb.common.paraglider.ParagliderData;
import com.botwclimb.network.BulletFallPayload;
import com.botwclimb.network.BulletPositionsPayload;
import com.botwclimb.network.BulletPositionsPayload.Snap;
import com.botwclimb.network.BulletShotPayload;
import com.botwclimb.network.ParagliderTogglePayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * Bullet time on the client. Holding a bow or crossbow in the air slows nearby mobs and players on this screen to 10%, by drawing
 * them where the slowed playback clock says they were. Only the picture changes: the server's positions and hitboxes
 * are untouched. Each shot costs 161.25 stamina, and the fall is slowed to 7.5% of normal gravity.
 * shortcut: the limbs of slowed mobs still swing at normal speed.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class BulletTime {
    // the numbers the server's hit test relies on live in BulletConstants, so the picture and the hits cannot drift apart
    private static final float SLOW = BulletConstants.SLOW, CATCH_UP = BulletConstants.CATCH_UP, SHOT_COST = BulletConstants.SHOT_COST;
    private static final int RADIUS = 48, RING = BulletConstants.CLIENT_RING; // 30 s of history per character, so the slowdown needs no time cap

    /** One nearby character: position and body yaw per tick, in a fixed ring. */
    private static final class Track {
        final int firstRt;
        final double[] pos = new double[RING * 3];
        final float[] yaw = new float[RING];
        Track(int firstRt) {
            this.firstRt = firstRt;
        }
    }

    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    private static final Map<Integer, Snap> SERVER = new HashMap<>(); // the server's real position of each mob
    private static boolean active, on;   // active = bullet time running; on = running or still catching up
    private static int rt;               // real ticks since it began
    private static float pb, pbO;        // visual playback clock, behind rt while slowed
    private static boolean wasDrawing, wasCharged, crossbowShot;
    private static int paidTicks;        // a release just paid for its shot: the server's confirmation must not pay again
    private static final int PAID_TICKS = 40;
    private static int pendingShot;      // ticks left to wait for the server to confirm a released shot
    private static boolean pushed;
    private static final int PENDING_TICKS = 20;

    private BulletTime() {}

    /** True while the picture is slowed (bullet time running): the sound slows with it. */
    public static boolean slowedNow() {
        return active;
    }

    /** Leaving the world (or respawning) mid bullet time: forget everything, so nothing from it is applied to the next mobs. */
    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    @SubscribeEvent
    public static void onRespawn(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.Clone event) {
        reset();
    }

    private static boolean looseSeen, looseAmmo; // diagnostics for the last release
    private static float loosePower;

    private static void reset() {
        active = on = pushed = false;
        rt = 0;
        pb = pbO = 0;
        TRACKS.clear();
        SERVER.clear();
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p != Minecraft.getInstance().player) return;
        ClimbData d = p.getData(BotwClimb.CLIMB);
        ParagliderData g = p.getData(BotwClimb.PARAGLIDER);
        boolean bowInHand = BulletFall.isShooter(p.getMainHandItem().getItem()); // a bow or a crossbow
        boolean drawing = p.isUsingItem() && BulletFall.isShooter(p.getUseItem().getItem());
        boolean bowDrawing = drawing && p.getUseItem().getItem() instanceof BowItem;
        boolean charged = p.getMainHandItem().getItem() instanceof CrossbowItem && CrossbowItem.isCharged(p.getMainHandItem());
        boolean airborne = !p.onGround() && !p.isInWater() && !p.isFallFlying() && !p.getAbilities().flying && !d.active();
        boolean can = airborne && (g.gliding || active || p.getDeltaMovement().y < 0) && bowInHand && d.stamina > 0 && !d.exhausted; // an emptied wheel must refill completely first

        if (wasDrawing && !bowDrawing && active) {
            if (paidTicks <= 0) pendingShot = PENDING_TICKS; // released without the release event having paid: the server confirms the arrow, or it never flew
            BotwClimb.LOGGER.info("[bullet] bow let go: loose event seen {} (ammo {}, power {}), stamina {}", looseSeen, looseAmmo, loosePower, d.stamina);
        }
        if (!drawing) looseSeen = false;
        wasDrawing = bowDrawing;
        if (wasCharged && !charged && active && p.getMainHandItem().getItem() instanceof CrossbowItem) { // a loaded crossbow was just fired: it pays like a bow shot, once however many bolts leave it
            crossbowShot = true;
            pay(p);
            paidTicks = PAID_TICKS;
        }
        wasCharged = charged;
        if (pendingShot > 0) pendingShot--;
        if (paidTicks > 0) paidTicks--;
        if (active && !can) BotwClimb.LOGGER.info("[bullet] ended: airborne {} bow {} stamina {} exhausted {}", airborne, bowInHand, d.stamina, d.exhausted);
        if (active && !can) {
            active = false; // leaving the bow, landing or an empty wheel: catch up from here
            setFall(p, false);
        } else if (!active && can) {
            start(p);
            setFall(p, true);
        }
        d.bowHold = (active && (drawing || charged)) || pendingShot > 0 ? Math.min(SHOT_COST, d.stamina) : 0f; // the HUD previews the cost until the shot is paid or dropped
        if (active) {
            if (drawing || charged) d.staminaDelay = Math.max(d.staminaDelay, ClimbConstants.STAMINA_REGEN_DELAY); // no recovery while drawing in bullet time
            PacketDistributor.sendToServer(new BulletShotPayload(Math.round(rt - pb))); // how far behind the drawn picture is
        }

        if (!on) return;
        rt++;
        record(p);
        pbO = pb;
        pb = Math.max(Math.min(rt, pb + (active ? SLOW : CATCH_UP)), rt - RING + 2);
        if (!active && pb >= rt) { // caught up: back to the normal picture
            on = false;
            TRACKS.clear();
            SERVER.clear();
        }
    }

    /**
     * The moment the bow is let go with enough draw to shoot (the client runs the release too), in bullet time: the shot
     * takes its stamina right now, even when less than a full shot is left (it takes the rest), like a climbing jump.
     * The server's confirmation below is only a backup, so a shot is never paid twice and never gets away free.
     */
    @SubscribeEvent
    public static void onArrowLoose(net.neoforged.neoforge.event.entity.player.ArrowLooseEvent event) {
        Player p = Minecraft.getInstance().player;
        if (p == null || event.getEntity() != p || !event.getLevel().isClientSide() || !active) return;
        looseSeen = true;
        looseAmmo = event.hasAmmo();
        loosePower = BowItem.getPowerForTime(event.getCharge());
        if (!event.hasAmmo() || BowItem.getPowerForTime(event.getCharge()) < 0.1f) return; // no arrow leaves the bow
        BotwClimb.LOGGER.info("[bullet] released with enough draw: stamina {} exhausted {}", p.getData(BotwClimb.CLIMB).stamina, p.getData(BotwClimb.CLIMB).exhausted);
        crossbowShot = false;
        pay(p);
        paidTicks = PAID_TICKS;
    }

    /** The server confirmed a bullet arrow left the bow: pay for it unless the release already did. */
    public static void onShotConfirmed() {
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        if (paidTicks > 0) { // already paid at the release
            if (!crossbowShot) paidTicks = 0; // a multishot crossbow confirms several bolts for the one shot
            return;
        }
        pay(p);
    }

    /** What a shot takes: one shot's cost, but everything if what would be left could not pay for another shot (the last shot empties the wheel, like a jump). */
    private static float shotTake(float stamina) {
        return stamina - SHOT_COST < SHOT_COST ? stamina : SHOT_COST;
    }

    private static void pay(Player p) {
        ClimbData d = p.getData(BotwClimb.CLIMB);
        float before = d.stamina;
        d.shotSpent = Stamina.take(d, shotTake(d.stamina));
        BotwClimb.LOGGER.info("[bullet] shot paid: {} -> {} (took {}), exhausted {}", before, d.stamina, d.shotSpent, d.exhausted); // a shot takes what is left, at most one shot's cost
        d.shotSpentSeq++;
        d.bowHold = 0f; // the preview and the drain end on the same tick
        pendingShot = 0;
    }

    private static void setFall(Player p, boolean on) {
        BulletFall.apply(p, on);
        PacketDistributor.sendToServer(new BulletFallPayload(on));
    }

    private static void start(Player p) {
        ParagliderData g = p.getData(BotwClimb.PARAGLIDER);
        if (g.gliding) { // bullet time takes the glider away: the slowed fall takes over
            g.gliding = false;
            PacketDistributor.sendToServer(new ParagliderTogglePayload(false));
        }
        if (on) { // still catching up from the last time: carry on from where the picture is, so nothing jumps
            active = true;
            return;
        }
        active = on = true;
        rt = 0;
        pb = pbO = 0;
        TRACKS.clear();
        SERVER.clear();
        record(p);
    }

    /** The server's real positions. A mob keeps its last one until the server sends a new one. */
    public static void onPositions(BulletPositionsPayload payload) {
        for (Snap s : payload.snaps()) SERVER.put(s.id(), s);
    }

    /** Stores this tick's position and yaw of every tracked character. A mob that cannot be found holds its last spot. */
    private static void record(Player p) {
        int slot = rt % RING, prev = (rt + RING - 1) % RING;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(RADIUS),
                e -> e != p && e.isAlive())) {
            TRACKS.computeIfAbsent(e.getId(), id -> new Track(rt));
        }
        for (Map.Entry<Integer, Track> entry : TRACKS.entrySet()) {
            Track t = entry.getValue();
            Entity found = p.level().getEntity(entry.getKey());
            if (found instanceof LivingEntity e && e.isAlive()) {
                Snap s = SERVER.get(e.getId());
                t.pos[slot * 3] = s != null ? s.x() : e.getX();
                t.pos[slot * 3 + 1] = s != null ? s.y() : e.getY();
                t.pos[slot * 3 + 2] = s != null ? s.z() : e.getZ();
                t.yaw[slot] = s != null ? s.yaw() : e.yBodyRot;
            } else {
                System.arraycopy(t.pos, prev * 3, t.pos, slot * 3, 3);
                t.yaw[slot] = t.yaw[prev];
            }
        }
    }

    /** Draws a living character where the slowed playback clock says it was, not where it is now. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST) // last, so a canceled render never leaves a pose pushed
    public static void onRenderPre(RenderLivingEvent.Pre event) {
        if (!on) return;
        LivingEntity e = com.botwclimb.client.RenderData.entity(event);
        if (e == null) return;
        Track t = TRACKS.get(e.getId());
        if (t == null) return;
        float pt = event.getPartialTick();
        float time = Mth.clamp(Mth.lerp(pt, pbO, pb), t.firstRt, rt);
        int i = (int) time;
        float f = time - i;
        int a = i % RING, b = i < rt ? (i + 1) % RING : a;
        double dx = Mth.lerp(f, t.pos[a * 3], t.pos[b * 3]) - Mth.lerp(pt, e.xo, e.getX());
        double dy = Mth.lerp(f, t.pos[a * 3 + 1], t.pos[b * 3 + 1]) - Mth.lerp(pt, e.yo, e.getY());
        double dz = Mth.lerp(f, t.pos[a * 3 + 2], t.pos[b * 3 + 2]) - Mth.lerp(pt, e.zo, e.getZ());
        float yaw = Mth.wrapDegrees(Mth.rotLerp(f, t.yaw[a], t.yaw[b]) - Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot));

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(dx, dy, dz);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        pushed = true;
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onRenderPost(RenderLivingEvent.Post event) {
        if (pushed) {
            event.getPoseStack().popPose();
            pushed = false;
        }
    }
}
