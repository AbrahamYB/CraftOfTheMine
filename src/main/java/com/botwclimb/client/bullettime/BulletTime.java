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
import com.botwclimb.network.BulletSlowPayload;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Bullet time on the client. Holding a bow or crossbow in the air slows everyone else on your screen. Mobs really run slowed on the
 * server (see BulletSlow), so what you see is what the server has; a slowed mob only moves a small step every few ticks and this
 * class glides the picture between those steps. Other players cannot be slowed on the server, so they are drawn where a slowed
 * playback clock says they were (10% speed), and the server rewinds arrow hits against them to match (see BulletShots). Each shot
 * costs 161.25 stamina, and the fall is slowed to 7.5% of normal gravity.
 * shortcut: the limbs of slowed mobs swing by the distance they really cover, and those of other players at normal speed.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class BulletTime {
    private static final float SHOT_COST = BulletConstants.SHOT_COST;
    private static final float START_FALL = 2.1f; // free space needed below you for bullet time to start, so it begins at once on a real drop and never on a plain jump
    /** How much of the way to the real position the picture covers each tick: gentle while slowed, quick once time is back. */
    private static final float FOLLOW_SLOW = 0.2f, FOLLOW_FREE = 0.45f;
    private static final double SNAP_SQ = 6.0 * 6.0, DONE_SQ = 0.0001; // a jump over 6 blocks is a teleport, drawn at once
    private static final int STALE_LIST = 60;  // the server repeats its list every 20 ticks; after 3 s of silence it is forgotten

    private static final int RING = BulletConstants.CLIENT_RING; // 30 s of history per player, so the slowdown needs no time cap
    private static final float SLOW = BulletConstants.SLOW, CATCH_UP = BulletConstants.CATCH_UP;

    /** One nearby player: position and body yaw per tick, in a fixed ring. */
    private static final class Track {
        final int firstRt;
        final double[] pos = new double[RING * 3];
        final float[] yaw = new float[RING];

        Track(int firstRt) {
            this.firstRt = firstRt;
        }
    }

    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    private static final Map<Integer, Snap> SERVER = new HashMap<>(); // the server's real position of each player
    private static boolean on;       // active, or still catching up
    private static int rt;           // real ticks since it began
    private static float pb, pbO;    // visual playback clock of other players, behind rt while slowed

    /** Where one slowed mob is drawn, this tick and the one before, so a frame between them can be interpolated. */
    private static final class Glide {
        double x, y, z, px, py, pz;
        float yaw, pyaw;
        boolean placed;
    }

    private static final Map<Integer, Glide> GLIDES = new HashMap<>();
    private static Set<Integer> slowed = Set.of(); // the ids the server says are slowed
    private static long slowedAt = Long.MIN_VALUE / 2;
    private static boolean active;       // bullet time running on this screen
    private static boolean wasDrawing, wasCharged, crossbowShot;
    private static int paidTicks;        // a release just paid for its shot: the server's confirmation must not pay again
    private static final int PAID_TICKS = 40;
    private static int pendingShot;      // ticks left to wait for the server to confirm a released shot
    private static boolean pushed;
    private static final int PENDING_TICKS = 20;

    private BulletTime() {}

    /** True while bullet time runs: the sound slows with it. */
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
        slowed = Set.of();
        GLIDES.clear();
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
        boolean can = airborne && (g.gliding || active || (p.getDeltaMovement().y < 0 && p.level().noCollision(p, p.getBoundingBox().expandTowards(0, -START_FALL, 0)))) && bowInHand && d.stamina > 0 && !d.exhausted; // an emptied wheel must refill completely first
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
            active = false; // leaving the bow, landing or an empty wheel: the server lets time run again
            setFall(p, false);
        } else if (!active && can) {
            start(p);
            setFall(p, true);
        }
        d.bowHold = (active && (drawing || charged)) || pendingShot > 0 ? Math.min(SHOT_COST, d.stamina) : 0f; // the HUD previews the cost until the shot is paid or dropped
        if (active) {
            if (drawing || charged) d.staminaDelay = Math.max(d.staminaDelay, ClimbConstants.STAMINA_REGEN_DELAY); // no recovery while drawing in bullet time
            com.botwclimb.client.ClientCompat.sendToServer(new BulletShotPayload(0)); // the heartbeat (the server runs the mobs slowed while it comes), with how far behind the drawn players are
        }
        glide(p);
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
        com.botwclimb.client.ClientCompat.sendToServer(new BulletFallPayload(on));
    }

    private static void start(Player p) {
        ParagliderData g = p.getData(BotwClimb.PARAGLIDER);
        if (g.gliding) { // bullet time takes the glider away: the slowed fall takes over
            g.gliding = false;
            com.botwclimb.client.ClientCompat.sendToServer(new ParagliderTogglePayload(false));
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

    /** The server's real positions of the other players. A player keeps their last one until the server sends a new one. */
    public static void onPositions(BulletPositionsPayload payload) {
        if (!on) return; // only while bullet time runs, and never more than a few hundred entries: nothing a server sends can grow this without end
        for (Snap s : payload.snaps()) if (SERVER.size() < 512 || SERVER.containsKey(s.id())) SERVER.put(s.id(), s);
    }

    /** Stores this tick's position and yaw of every nearby other player. One who cannot be found holds their last spot. */
    private static void record(Player p) {
        int slot = rt % RING, prev = (rt + RING - 1) % RING;
        // other players are slowed for real on the server now (see BulletSlow), so none are drawn from a slowed clock any more
        for (Map.Entry<Integer, Track> entry : TRACKS.entrySet()) {
            Track t = entry.getValue();
            Entity found = p.level().getEntity(entry.getKey());
            if (found instanceof Player e && e.isAlive()) {
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

    /** The server's list of slowed mobs (sent when it changes, and every second while it is not empty). */
    public static void onSlowed(BulletSlowPayload payload) {
        Set<Integer> ids = new HashSet<>();
        for (int id : payload.ids()) ids.add(id);
        slowed = ids;
        Minecraft mc = Minecraft.getInstance();
        slowedAt = mc.level == null ? Long.MIN_VALUE / 2 : mc.level.getGameTime();
    }

    /**
     * Each tick, moves every slowed mob's drawn place part of the way to where the server has it. A mob that stops being slowed keeps
     * gliding, faster, until it has arrived, so nothing ever jumps when time runs again.
     */
    private static void glide(Player p) {
        boolean live = !slowed.isEmpty() && p.level().getGameTime() - slowedAt <= STALE_LIST;
        if (live) for (int id : slowed) GLIDES.computeIfAbsent(id, i -> new Glide());
        Iterator<Map.Entry<Integer, Glide>> it = GLIDES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Glide> entry = it.next();
            Glide g = entry.getValue();
            if (!(p.level().getEntity(entry.getKey()) instanceof LivingEntity e) || !e.isAlive()) {
                it.remove();
                continue;
            }
            g.px = g.x;
            g.py = g.y;
            g.pz = g.z;
            g.pyaw = g.yaw;
            if (!g.placed) { // first sight: start where it is, so it does not fly in from the origin
                g.x = g.px = e.getX();
                g.y = g.py = e.getY();
                g.z = g.pz = e.getZ();
                g.yaw = g.pyaw = e.yBodyRot;
                g.placed = true;
                continue;
            }
            boolean slowedNow = live && slowed.contains(entry.getKey());
            float k = slowedNow ? FOLLOW_SLOW : FOLLOW_FREE;
            double dx = e.getX() - g.x, dy = e.getY() - g.y, dz = e.getZ() - g.z;
            float dyaw = Mth.wrapDegrees(e.yBodyRot - g.yaw);
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > SNAP_SQ) {
                g.x = g.px = e.getX();
                g.y = g.py = e.getY();
                g.z = g.pz = e.getZ();
                g.yaw = g.pyaw = e.yBodyRot;
            } else {
                g.x += dx * k;
                g.y += dy * k;
                g.z += dz * k;
                g.yaw += dyaw * k;
            }
            if (!slowedNow && distSq < DONE_SQ && Math.abs(dyaw) < 0.5f) it.remove(); // arrived: back to the ordinary picture
        }
    }

    /** Draws a slowed mob where the glide has it, instead of where its last, small step put it. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST) // last, so a canceled render never leaves a pose pushed
    public static void onRenderPre(RenderLivingEvent.Pre event) {
        if (GLIDES.isEmpty() && !on) return;
        LivingEntity e = com.botwclimb.client.RenderData.entity(event);
        if (e == null) return;
        float pt = event.getPartialTick();
        Track t = on ? TRACKS.get(e.getId()) : null;
        if (t != null) { // another player: drawn where the slowed playback clock says they were
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
            return;
        }
        Glide g = GLIDES.get(e.getId());
        if (g == null || !g.placed) return;
        double dx = Mth.lerp(pt, g.px, g.x) - Mth.lerp(pt, e.xo, e.getX());
        double dy = Mth.lerp(pt, g.py, g.y) - Mth.lerp(pt, e.yo, e.getY());
        double dz = Mth.lerp(pt, g.pz, g.z) - Mth.lerp(pt, e.zo, e.getZ());
        float yaw = Mth.wrapDegrees(Mth.rotLerp(pt, g.pyaw, g.yaw) - Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot));
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
