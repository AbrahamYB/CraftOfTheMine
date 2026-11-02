package com.botwclimb.common.bullettime;

import com.botwclimb.BotwClimb;
import com.botwclimb.network.BulletPositionsPayload;
import com.botwclimb.network.BulletPositionsPayload.Snap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server side of bullet time. While a player is in bullet time the server keeps a per-tick history of the hitboxes of
 * the living entities around them, and a bullet arrow is tested before each move against the hitboxes the shooter's
 * screen was showing at that moment (their reported lag, kept up to date every tick, plus their ping). Blocks still stop
 * the arrow. A hit applies arrow damage once (with crit and flame) and discards the arrow. Nothing is recorded or
 * scanned while nobody is in bullet time.
 * shortcut: no knockback, piercing or tipped-arrow effects on these hits (those hooks are protected in vanilla).
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID)
public final class BulletShots {
    private static final int RING = BulletConstants.SERVER_RING, MAX_LAG = BulletConstants.MAX_LAG;
    private static final int FRESH = 5, SCAN_RADIUS = 48, SEND_RADIUS = 64, KEEP_AFTER = 100, ARROW_LIFE = 400;
    private static final double MOVE_EPS = 0.001, HIT_MARGIN = 0.3; // vanilla arrows hit within about 0.3 blocks of a box
    private static final float YAW_EPS = 0.5f;

    private static final class Hist {
        final LivingEntity entity;
        final AABB[] box = new AABB[RING];
        final long[] at = new long[RING]; // the game tick each slot holds, so an old box is never reused
        final long first;                 // the first tick recorded: a look-back older than this uses it
        long seen = -1;

        Hist(LivingEntity entity, long first) {
            this.entity = entity;
            this.first = first;
        }
    }

    private record Tag(ResourceKey<Level> dimension, UUID owner, int lagAtShot, long expires) {}

    private static final Map<Integer, Hist> HISTORY = new HashMap<>();
    private static final Map<UUID, int[]> LAG = new HashMap<>();   // player -> {lag, game tick it was reported}
    private static final Map<UUID, Map<Integer, Snap>> SENT = new HashMap<>();
    private static final Map<UUID, Tag> TAGGED = new HashMap<>();  // bullet arrow -> who shot it and when
    private static long lastRunning = Long.MIN_VALUE / 2;

    private BulletShots() {}

    /**
     * The client reports how many ticks behind its drawn picture is. Only believed while the player really could be in
     * bullet time (in the air with a bow in hand): a client cannot get compensation by just sending numbers.
     */
    public static void lag(ServerPlayer p, int lag) {
        if (!BulletFall.allowed(p)) return;
        report(p.getUUID(), lag, p.level().getGameTime());
    }

    /** Stores a player's reported lag at a game tick (the part of {@link #lag} that does not need a real connection). */
    public static void report(UUID player, int lag, long now) {
        LAG.put(player, new int[]{Mth.clamp(lag, 0, MAX_LAG), (int) now});
    }

    /** Starts keeping the history of a mob (also used by the scan below). */
    public static void track(LivingEntity e, long now) {
        HISTORY.computeIfAbsent(e.getId(), id -> new Hist(e, now));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.getOwner() instanceof ServerPlayer sp)) return;
        if (tagShot(arrow, sp, ((Level) event.getLevel()).dimension(), event.getLevel().getGameTime())) {
            PacketDistributor.sendToPlayer(sp, new com.botwclimb.network.BulletShotConfirmPayload());
        }
    }

    /** Marks an arrow as a bullet arrow if its shooter is in bullet time right now. False = an ordinary arrow. */
    public static boolean tagShot(AbstractArrow arrow, Entity owner, ResourceKey<Level> dimension, long now) {
        int[] lag = LAG.get(owner.getUUID());
        if (lag == null || now - lag[1] > FRESH) return false;
        TAGGED.put(arrow.getUUID(), new Tag(dimension, owner.getUUID(), BulletConstants.extrapolateLag(lag[0], now - lag[1]), now + ARROW_LIFE));
        return true;
    }

    /** Vanilla's hit on the real hitbox is cancelled for bullet arrows: only the drawn picture can be hit. */
    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof AbstractArrow arrow && TAGGED.containsKey(arrow.getUUID())
                && event.getRayTraceResult().getType() == HitResult.Type.ENTITY) {
            event.setCanceled(true);
        }
    }

    private record Pending(AbstractArrow arrow, LivingEntity target) {}

    @SubscribeEvent
    public static void onPre(LevelTickEvent.Pre event) {
        if (TAGGED.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        List<Pending> hits = new ArrayList<>();
        TAGGED.entrySet().removeIf(e -> {
            Tag tag = e.getValue();
            if (tag.dimension() != level.dimension()) return false;
            if (now > tag.expires() || !(level.getEntity(e.getKey()) instanceof AbstractArrow arrow) || arrow.isRemoved()) return true;
            // the shooter's picture right now: the lag keeps growing while they stay in bullet time and closes once they stop
            int[] r = LAG.get(tag.owner());
            int lag = r == null ? tag.lagAtShot() : BulletConstants.extrapolateLag(r[0], now - r[1]);
            LivingEntity target = find(level, arrow, now - lag - pingTicks(arrow));
            if (target == null) return false;
            hits.add(new Pending(arrow, target));
            return true;
        });
        for (Pending h : hits) { // damage after the loop: it can kill things and fire events that must not touch TAGGED mid-iteration
            damage(level, h.arrow(), h.target());
            h.arrow().discard();
        }
    }

    private static long pingTicks(AbstractArrow arrow) {
        return arrow.getOwner() instanceof ServerPlayer sp && sp.connection != null ? Mth.clamp(sp.connection.latency() / 50, 0, 20) : 0;
    }

    /** The entity the arrow's next move hits among the hitboxes at game tick {@code at}, or null. Blocks in the way win. */
    private static LivingEntity find(ServerLevel level, AbstractArrow arrow, long at) {
        Vec3 from = arrow.position(), to = from.add(arrow.getDeltaMovement());
        if (to.equals(from)) return null; // stuck in a block or standing still
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, arrow));
        double bestDist = block.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : from.distanceToSqr(block.getLocation());
        Entity owner = arrow.getOwner();
        LivingEntity best = null;
        for (Hist h : HISTORY.values()) {
            if (h.entity == owner || !h.entity.isAlive() || h.entity.level() != level) continue;
            long a = Math.max(at, h.first); // a mob that joined the history after that moment is where it first was seen
            int slot = (int) (a % RING);
            if (h.at[slot] != a || h.box[slot] == null) continue;
            Optional<Vec3> p = h.box[slot].inflate(HIT_MARGIN).clip(from, to);
            if (p.isPresent() && from.distanceToSqr(p.get()) < bestDist) {
                bestDist = from.distanceToSqr(p.get());
                best = h.entity;
            }
        }
        return best;
    }

    private static void damage(ServerLevel level, AbstractArrow arrow, LivingEntity target) {
        int damage = Mth.ceil(Mth.clamp(arrow.getDeltaMovement().length() * com.botwclimb.common.Compat.baseDamage(arrow), 0, Integer.MAX_VALUE / 2));
        if (arrow.isCritArrow()) damage += level.random.nextInt(damage / 2 + 2);
        if (arrow.isOnFire()) target.setRemainingFireTicks(100);
        target.hurt(level.damageSources().arrow(arrow, arrow.getOwner()), damage);
    }

    private static boolean bulletTimeRunning(long now) {
        for (int[] lag : LAG.values()) if (now - lag[1] <= 2) return true;
        return false;
    }

    /** Records the hitboxes while someone is in bullet time (and while their arrows are still flying); otherwise does nothing at all. */
    @SubscribeEvent
    public static void onPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        boolean running = bulletTimeRunning(now);
        if (running) lastRunning = now;
        if (!running && TAGGED.isEmpty() && now - lastRunning > KEEP_AFTER) { // idle: no history, no memory
            if (!HISTORY.isEmpty()) HISTORY.clear();
            SENT.clear();
            return;
        }
        if (running) {
            for (ServerPlayer sp : level.players()) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(SCAN_RADIUS),
                        e -> e != sp && e.isAlive() && !e.isPassenger() && !e.isVehicle())) {
                    track(e, now);
                }
            }
        }
        for (Hist h : HISTORY.values()) {
            if (h.entity.level() != level || !h.entity.isAlive() || h.seen == now) continue;
            h.box[(int) (now % RING)] = h.entity.getBoundingBox();
            h.at[(int) (now % RING)] = now;
            h.seen = now;
        }
        if (running) for (ServerPlayer sp : level.players()) sendPositions(sp, now);
        HISTORY.values().removeIf(h -> now - h.seen > RING || !h.entity.isAlive());
    }

    /** A player who leaves keeps no data behind, and a stopped server leaves nothing for the next one. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAG.remove(event.getEntity().getUUID());
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HISTORY.clear();
        LAG.clear();
        SENT.clear();
        TAGGED.clear();
        lastRunning = Long.MIN_VALUE / 2;
    }

    /** Real positions of the mobs around the player, only those that changed, in as many packets as it takes: nobody is starved. */
    private static void sendPositions(ServerPlayer sp, long now) {
        int[] lag = LAG.get(sp.getUUID());
        if (lag == null || now - lag[1] > 2) {
            SENT.remove(sp.getUUID());
            return;
        }
        Map<Integer, Snap> sent = SENT.computeIfAbsent(sp.getUUID(), id -> new HashMap<>());
        List<Snap> changed = new ArrayList<>();
        double maxSq = (double) SEND_RADIUS * SEND_RADIUS;
        for (Hist h : HISTORY.values()) {
            LivingEntity e = h.entity;
            if (e == sp || e.level() != sp.level() || !e.isAlive() || e.isPassenger() || e.isVehicle() || e.distanceToSqr(sp) > maxSq) continue;
            Snap before = sent.get(e.getId());
            if (before == null || Math.abs(before.x() - e.getX()) > MOVE_EPS || Math.abs(before.y() - e.getY()) > MOVE_EPS
                    || Math.abs(before.z() - e.getZ()) > MOVE_EPS || Math.abs(Mth.wrapDegrees(before.yaw() - e.yBodyRot)) > YAW_EPS) {
                Snap s = new Snap(e.getId(), e.getX(), e.getY(), e.getZ(), e.yBodyRot);
                changed.add(s);
                sent.put(e.getId(), s);
            }
        }
        for (int i = 0; i < changed.size(); i += BulletPositionsPayload.MAX_SNAPS) {
            PacketDistributor.sendToPlayer(sp, new BulletPositionsPayload(changed.subList(i, Math.min(changed.size(), i + BulletPositionsPayload.MAX_SNAPS))));
        }
        sent.keySet().removeIf(id -> !HISTORY.containsKey(id)); // forget mobs that left, so SENT cannot grow
    }
}
