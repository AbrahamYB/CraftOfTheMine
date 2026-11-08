package com.botwclimb.common.bullettime;

import com.botwclimb.BotwClimb;
import com.botwclimb.network.BulletSlowPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of bullet time. While a player is in bullet time, the living things around them really run slowed (mobs by skipping their ticks, other players through their attributes):
 * each gets one tick in every {@link BulletConstants#PERIOD}, so what the shooter sees is what the server has, and an arrow
 * hits the hitbox that is drawn. The slowed mobs' ids go to the clients, which only smooth the picture. Nothing is tracked
 * while nobody is in bullet time.
 * shortcut: knockback and AI timers slow with the mob's own ticks; a slowed mob's limbs still swing by the distance it covers.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID)
public final class BulletSlow {
    private record Slowed(long until, ResourceKey<Level> dimension) {}

    private static final Map<UUID, Long> ACTIVE = new HashMap<>();      // shooter -> game tick of their last heartbeat
    private static final Map<Integer, Slowed> SLOWED = new HashMap<>(); // mob id -> when it stops counting as slowed
    private static final Map<UUID, Budget> BUDGET = new HashMap<>();   // shooter -> what the server lets them run
    private static final Map<UUID, Long> PLAYERS = new HashMap<>();     // other players near a shooter -> game tick their slowdown runs until
    private static final String PLAYER_SLOW = "bullet_slow";
    private static final Map<ResourceKey<Level>, int[]> SENT = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> SENT_AT = new HashMap<>();

    /** Mods whose entities are machinery or carry other things (Create contraptions and seats, physics ships): never touched, whatever they are. */
    private static final java.util.Set<String> EXEMPT = java.util.Set.of("create", "sable", "simulated", "aeronautics", "offroad");

    private BulletSlow() {}

    /**
     * Only a free-standing living thing that is not a player can be slowed. Anything riding or carrying something (a mob on a Create
     * contraption seat, a player's horse, a boat's passenger) is left alone, so nothing that moves because of another entity is held back.
     */
    public static boolean eligible(Entity e) {
        return e instanceof LivingEntity && !(e instanceof Player) && e.isAlive() && !e.isPassenger() && !e.isVehicle()
                && !EXEMPT.contains(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace());
    }

    /** The client says it is in bullet time. Only believed while the player really could be (in the air with a bow in hand). */
    public static void heartbeat(ServerPlayer p) {
        accept(p);
    }

    /** What {@link #heartbeat} does for any player (the tests use players that have no connection). True when bullet time is kept running. */
    public static boolean accept(Player p) {
        if (!BulletFall.allowed(p) || !reallyInTheAir(p)) return false;
        long now = p.level().getGameTime();
        Budget b = BUDGET.computeIfAbsent(p.getUUID(), k -> new Budget(BulletConstants.SERVER_BUDGET, now - 1));
        if (now != b.last) { // a heartbeat buys STALE ticks, so it costs the ticks since the last one (at most STALE); only rest beyond that refills
            long gap = now - b.last;
            long idle = gap - BulletConstants.STALE;
            if (idle > 0) b.left = Math.min(BulletConstants.SERVER_BUDGET, b.left + idle * BulletConstants.SERVER_REFILL);
            b.left -= Math.min(gap, BulletConstants.STALE);
            b.last = now;
        }
        if (b.left <= 0) return false; // spent: no more slowing until it has rested
        markActive(p.getUUID(), now);
        return true;
    }

    /**
     * What the client says about being airborne is only a flag it sends, so the server looks for itself: nothing solid just under the
     * feet, falling (the server counts the fall itself, so a jump on the way up does not count), and not riding, swimming or on a ladder.
     * A client cannot hold bullet time on while standing on the ground, nor use its slow fall to jump far higher.
     */
    public static boolean reallyInTheAir(Player p) {
        return p.fallDistance > 0 && !p.isPassenger() && !p.isInWater() && !p.isInLava() && !p.onClimbable() && !p.isSpectator()
                && p.level().noCollision(p, p.getBoundingBox().move(0, -0.2, 0));
    }

    private static final class Budget {
        float left;
        long last;

        Budget(float left, long last) {
            this.left = left;
            this.last = last;
        }
    }

    /** Records a heartbeat at a game tick (the part of {@link #heartbeat} that needs no real connection). */
    public static void markActive(UUID shooter, long now) {
        ACTIVE.put(shooter, now);
    }

    /** True while the player's last heartbeat is fresh. */
    public static boolean active(Player p, long now) {
        Long at = ACTIVE.get(p.getUUID());
        return at != null && now - at <= BulletConstants.STALE;
    }

    /** Marks a mob as slowed until {@link BulletConstants#GRACE} ticks after {@code now}. */
    public static void slow(Entity e, long now) {
        if (!eligible(e)) return;
        SLOWED.put(e.getId(), new Slowed(now + BulletConstants.GRACE, e.level().dimension()));
    }

    public static boolean isSlowed(Entity e, long now) {
        if (SLOWED.isEmpty()) return false; // the common case: this runs for every entity every tick
        Slowed s = SLOWED.get(e.getId());
        return s != null && now <= s.until();
    }

    /** True when a slowed entity must sit this tick out: it only gets the one in every PERIOD ticks where its own count lines up. */
    public static boolean shouldSkip(Entity e, long now) {
        return isSlowed(e, now) && eligible(e) && e.tickCount % BulletConstants.PERIOD != 0; // one that mounted something meanwhile is released at once
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        Entity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level) || !shouldSkip(e, level.getGameTime())) return;
        // a hurt mob's invulnerability must still run out on the real clock, or every arrow after the first would bounce off
        if (e.invulnerableTime > 0) e.invulnerableTime--;
        if (e instanceof LivingEntity living && living.hurtTime > 0) living.hurtTime--;
        //? if >=26.3
        /*e.tickCount++;*/ // from 26.3 the counter is advanced by the tick that is skipped here
        event.setCanceled(true);
    }

    /** Slows everything that can be slowed within range of each shooter that is in bullet time right now. */
    public static void scan(ServerLevel level, Iterable<? extends Player> shooters, long now) {
        scan(level, shooters, now, BulletConstants.RADIUS);
    }

    /** {@link #scan(ServerLevel, Iterable, long)} with a chosen range (the tests use a small one, so they do not slow each other's mobs). */
    public static void scan(ServerLevel level, Iterable<? extends Player> shooters, long now, double radius) {
        for (Player sp : shooters) {
            if (!active(sp, now) || !BulletFall.allowed(sp) || !reallyInTheAir(sp)) continue; // checked every tick, not only when the heartbeat arrived
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(radius),
                    BulletSlow::eligible)) {
                slow(e, now);
            }
            for (Player o : level.getEntitiesOfClass(Player.class, sp.getBoundingBox().inflate(radius), x -> x != sp && playerEligible(x))) {
                markPlayer(o, now);
            }
        }
    }

    /** Slows another player until {@link BulletConstants#GRACE} ticks after {@code now}. */
    public static void markPlayer(Player o, long now) {
        PLAYERS.put(o.getUUID(), now + BulletConstants.GRACE);
    }

    /** Other players cannot have their ticks skipped (they move on their own client), so they are slowed through their attributes instead. */
    public static boolean playerEligible(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative() && !p.isPassenger() && !p.isVehicle();
    }

    public static boolean playerSlowed(Player p, long now) {
        Long until = PLAYERS.get(p.getUUID());
        return until != null && now <= until && !active(p, now) && playerEligible(p); // a shooter in bullet time is never slowed by another's
    }

    /**
     * Puts the slowdown on or takes it off: walking, falling, jumping, hitting and mining all run at 1/PERIOD of normal. Transient, so
     * nothing is saved, and it is taken off on the first tick the player is no longer near a shooter.
     * shortcut: animations, bow draw time, hunger and effects keep their normal speed.
     */
    public static void applyToPlayer(Player p, boolean on) {
        double m = 1.0 / BulletConstants.PERIOD - 1.0; // multiplier added on top of the total: -(1 - 1/PERIOD)
        var id = ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, PLAYER_SLOW);
        for (var attr : new net.minecraft.core.Holder[]{Attributes.MOVEMENT_SPEED, Attributes.GRAVITY, Attributes.ATTACK_SPEED, Attributes.JUMP_STRENGTH, Attributes.BLOCK_BREAK_SPEED}) {
            @SuppressWarnings("unchecked")
            AttributeInstance inst = p.getAttribute((net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>) attr);
            if (inst == null) continue;
            if (on && !inst.hasModifier(id)) inst.addTransientModifier(new AttributeModifier(id, m, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            else if (!on && inst.hasModifier(id)) inst.removeModifier(id);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p.level().isClientSide()) return;
        applyToPlayer(p, playerSlowed(p, p.level().getGameTime()));
    }

    /** Marks everything near each shooter, forgets what left, and tells the clients when the list changes. */
    @SubscribeEvent
    public static void onLevelPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (ACTIVE.isEmpty() && SLOWED.isEmpty() && PLAYERS.isEmpty()) return; // idle: nothing recorded, nothing scanned
        scan(level, level.players(), now);
        SLOWED.values().removeIf(s -> now > s.until());
        PLAYERS.values().removeIf(until -> now > until + 40);
        ACTIVE.values().removeIf(at -> now - at > BulletConstants.STALE * 4L);
        int[] ids = SLOWED.entrySet().stream().filter(s -> s.getValue().dimension() == level.dimension())
                .mapToInt(Map.Entry::getKey).sorted().limit(BulletConstants.MAX_IDS).toArray();
        int[] before = SENT.getOrDefault(level.dimension(), new int[0]);
        long sentAt = SENT_AT.getOrDefault(level.dimension(), Long.MIN_VALUE / 2);
        if (Arrays.equals(ids, before) && (ids.length == 0 || now - sentAt < BulletConstants.KEEPALIVE)) return;
        SENT.put(level.dimension(), ids);
        SENT_AT.put(level.dimension(), now);
        for (ServerPlayer sp : level.players()) {
            if (sp.connection != null && sp.connection.hasChannel(BulletSlowPayload.TYPE)) com.botwclimb.common.Net.toPlayer(sp, new BulletSlowPayload(ids));
        }
    }

    /** Someone arriving gets the list again, whatever was sent before. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        SENT.clear();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
        BUDGET.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        reset();
    }

    /** Forgets everything (a stopped server, and the tests). */
    public static void reset() {
        ACTIVE.clear();
        BUDGET.clear();
        SLOWED.clear();
        PLAYERS.clear();
        SENT.clear();
        SENT_AT.clear();
    }
}
