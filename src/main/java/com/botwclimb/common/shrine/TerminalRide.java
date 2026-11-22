package com.botwclimb.common.shrine;

import com.botwclimb.BotwClimb;
import com.botwclimb.network.TerminalRidePayload;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The terminal ride, server side: magic mud wraps the player, lifts them up, and at the top (where the clients show nothing but
 * mud and sky) the player is moved to where the trip goes, from which the mud sets them down. Nothing is placed in the world;
 * the mud is drawn by the clients from the phase this class announces.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID)
public final class TerminalRide {
    /** Ticks the cocoon takes to form and shut (the clients draw the bloom over the same time), the longest rise, the pause at the top, the set-down, and the whole-ride limit. */
    private static final int GRAB_UP = 44, RISE_MAX = 60, HOLD = 6, TIMEOUT = 400;
    /** Coming home (all of it a little quicker than going up): ticks before the marker pulls the rider under, held in the void, and on the ground after landing. */
    public static final int GRAB_HOME = 36, HOLD_HOME = 5, LANDED_HOME = 32;
    /** How high the ride goes before the world changes, in blocks. */
    public static final int RISE_HEIGHT = 60;
    /** How far a Return ride sinks through the ground before the world changes. */
    public static final int SINK_DEPTH = 16;
    /** Going up: ticks the rider floats up through the ribbons in the world before the climb begins (the clients cut to the void as it ends). */
    public static final int FLOAT_TICKS = 57;
    /** Going up: ticks of the float the rider rises for; then they stop dead, as if they hit something (the rumble and the camera's jolt), and hang until they pass through (the cut). */
    public static final int FLOAT_RISE = 33;
    /** Going up: ticks the rider is held at the top in the void before the world changes (the void's sound covers about 3.8 s of void). */
    public static final int HOLD_UP = HOLD + 14;
    /** The float's speed: FLOAT_SPEED at first, FLOAT_ACCEL more each tick (about 7 blocks in all, through three crossings of the ribbons). */
    public static final double FLOAT_SPEED = 0.02, FLOAT_ACCEL = 0.008;
    /** Ticks a Return ride waits, wrapped, over the unravelled ground before it starts to sink. */
    /** Ticks a rider arriving at a plot is held after touching down: they crouch in a ripple, jump, land, and the landing glow plays (TotkRide). */
    public static final int ARRIVE = 88; // TotkRide's jump lands 44 ticks in (after the wait); the flames' last flakes are gone 44 ticks later
    public static final int SINK_DELAY = 11; // long enough for the rider's turn and the arms going up to be seen (at 6 it read as a glitch); 16, then 13, felt too slow
    /** Blocks per tick: the rise starts at START_SPEED, gains ACCEL each tick, and tops out at MAX_SPEED, both ways. */
    public static final double MAX_SPEED = 2.6, START_SPEED = 0.5, ACCEL = 0.32;
    private static final Logger LOG = LogUtils.getLogger();
    private static final int DESCENT_MAX = 120;
    /** Blocks above the ground at which a Return rider leaves the void and drifts the rest of the way down in the world (TotkRide.land). */
    public static final float HOME_SLOW = 7f; // high enough that they are seen coming down while they turn, not only touching down
    /** Blocks above the ground a Return rider comes out of the world change: just over HOME_SLOW, so the void lasts about 0.9 s, as in the recording of its sound. */
    private static final double HOME_DROP = 9.5;
    /** Ticks the drop home waits at most for the rider's game to load the world (a client without the mod never says). */
    private static final int LOAD_WAIT = 60;
    private static final String RIDING_TAG = BotwClimb.MOD_ID + ":terminal_ride";
    private static final Map<UUID, Ride> RIDES = new HashMap<>();

    private static final class Ride {
        final int target;
        /** Returning from a plot: the ground opens and the cocoon goes straight down, where every other ride goes up. */
        final boolean sink;
        /** The ground the rider comes down to; the start of the ride until the trip has moved them. */
        double x, y, z;
        int phase = 1, t, phaseT;
        /** Coming home: the rider's game has loaded the world they were moved to; until then (at most LOAD_WAIT ticks) the drop waits at the top, or a slow load would hide most of it. */
        boolean loaded;
        int waited;

        Ride(int target, ServerPlayer p) {
            this.target = target;
            this.sink = target == PlotTravel.RETURN; // the plots are up in the sky: Return goes down, every other ride up
            x = p.getX();
            y = p.getY();
            z = p.getZ();
        }
    }

    private TerminalRide() {}

    /** The rider's game says it has loaded the world with this key (hashed); only the world they are in now counts, so an old message cannot start the drop early. */
    public static void loaded(ServerPlayer p, int dimension) {
        Ride r = RIDES.get(p.getUUID());
        if (r != null && r.phase == 4 && dimension == p.level().dimension().toString().hashCode()) r.loaded = true;
    }

    public static boolean isRiding(ServerPlayer p) {
        return RIDES.containsKey(p.getUUID());
    }

    /** @param target {@link PlotTravel#OWN}, {@link PlotTravel#VISIT} or {@link PlotTravel#RETURN} */
    public static void start(ServerPlayer p, int target) {
        if (isRiding(p)) return;
        Ride r = new Ride(target, p);
        RIDES.put(p.getUUID(), r);
        p.setNoGravity(true);
        p.getPersistentData().putBoolean(RIDING_TAG, true); // no gravity is saved with the player, so a ride cut short must be undone on the next login
        send(p, r);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Ride r = RIDES.get(p.getUUID());
        if (r == null) return;
        r.t++;
        r.phaseT++;
        p.fallDistance = 0;
        p.setPose(net.minecraft.world.entity.Pose.STANDING); // sunk in the ground the game would make the rider crouch or crawl, which jerks the drawn model
        if (p.connection != null) p.connection.aboveGroundTickCount = 0; // a dedicated server kicks anyone who floats for 80 ticks
        switch (r.phase) {
            case 1 -> {
                hold(p);
                if (r.t >= (r.sink ? GRAB_HOME : GRAB_UP)) next(p, r, 2);
            }
            case 2 -> {
                if (r.sink) { // the rider's own client moves them straight down, through anything in the way
                    p.noPhysics = true;
                    double sunk = r.y - p.getY();
                    if (sunk >= SINK_DEPTH || r.phaseT >= RISE_MAX + SINK_DELAY || r.phaseT > 6 + SINK_DELAY && sunk < 0.5 * (r.phaseT - SINK_DELAY)) next(p, r, 3);
                } else {
                    int climb = r.phaseT - FLOAT_TICKS; // first the rider only floats up a little (the clients cut to the void as that ends), then climbs
                    lift(p, climb <= 0 ? (r.phaseT <= FLOAT_RISE ? FLOAT_SPEED + FLOAT_ACCEL * r.phaseT : 0.0) : Math.min(MAX_SPEED, START_SPEED + ACCEL * climb));
                    double risen = p.getY() - r.y;
                    boolean stuck = climb > 6 && risen < 0.5 * climb; // a ceiling stops the rise
                    if (risen >= RISE_HEIGHT || climb >= RISE_MAX || stuck) next(p, r, 3);
                }
            }
            case 3 -> {
                hold(p);
                if (r.phaseT >= (r.sink ? HOLD_HOME : HOLD_UP)) {
                    travel(p, r);
                    next(p, r, 4);
                }
            }
            case 4 -> {
                if (r.sink && !r.loaded && r.waited++ < LOAD_WAIT) { hold(p); r.phaseT = 0; break; }
                double remaining = p.getY() - r.y;
                double rem = Math.max(remaining, 0);
                // fast from high up, gentle for the last few blocks; coming home the descent is a quarter shorter, and its last blocks (seen in the world, out of the void) drift down, quick enough that the rider turns about 5/8 as many times as on the way down through the void's longer fall
                lift(p, -Math.min(rem, r.sink && rem < HOME_SLOW ? 0.123 + 0.16 * rem : Math.min(MAX_SPEED, (0.3 + 0.2 * rem) * (r.sink ? 1.57 : 1.0)))); // never past the floor, so it ends on it without a jump
                if (remaining <= 0.02 || p.onGround() && r.phaseT > 5 || r.phaseT >= DESCENT_MAX) { // never leave the rider hanging
                    p.teleportTo(r.x, r.y, r.z);
                    next(p, r, 5);
                }
            }
            case 5 -> {
                hold(p);
                if (r.phaseT >= (r.sink ? LANDED_HOME : ARRIVE)) end(p, r); // coming home the turn stops first (TotkRide.SPIN_STOP), then the landing plays (and its strands fade)
            }
        }
        if (RIDES.containsKey(p.getUUID()) && r.t > TIMEOUT) end(p, r);
    }

    /**
     * The hidden move. The rider is put the same height above the destination's ground as they are above the ground here, so the
     * mud simply carries on down. If there is no such place the ride comes back down where it began.
     */
    private static void travel(ServerPlayer p, Ride r) {
        PlotTravel.Arrival a = PlotTravel.arrival(p, r.target, r.sink ? HOME_DROP : p.getY() - r.y); // both ways the rider comes down out of the sky
        if (a == null) {
            LOG.warn("Terminal ride of {} has nowhere to go (target {}); it comes back down here", p.getName().getString(), r.target);
            return;
        }
        r.loaded = false;
        r.waited = 0;
        r.x = a.x();
        r.y = a.y();
        r.z = a.z();
        if (a.level() != p.level()) { // players watching from the world the rider leaves must not keep drawing mud on nobody
            com.botwclimb.common.Net.toWatchers(p, new TerminalRidePayload(p.getId(), 0, (float) r.y, r.sink ? 1 : 0), false);
        }
        com.botwclimb.common.Compat.teleport(p, a.level(), a.x(), a.y() + a.rise(), a.z(), p.getYRot(), p.getXRot()); // the rider keeps facing the way they faced, not a fixed direction of the world
        p.noPhysics = false;
        hold(p);
    }

    /**
     * No gravity is saved with the player, so a game closed or killed mid-ride would leave them floating for good. The marker is
     * saved in the same file, so it is there whenever that happened; a survival player who floats without it (a save from before
     * the marker existed) is freed too.
     */
    /**
     * The rider sinks through solid ground on a Return and is held inside it while the world changes, and Player.tick() clears
     * the no-clip flag at the start of every tick, so the game would count the rider as stuck in a wall and hurt them (a rider with
     * little health died of it). Nothing of the ride is meant to hurt: no wall damage, and no fall damage at the end.
     */
    @SubscribeEvent
    public static void onDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && isRiding(p)
                && (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        boolean cut = p.getPersistentData().contains(RIDING_TAG);
        if (!cut) return; // only a ride that was cut off: no-gravity from another mod or command is left alone
        p.getPersistentData().remove(RIDING_TAG);
        p.setNoGravity(false);
        p.setDeltaMovement(0, 0, 0);
        setDown(p);
    }

    /** A rider who died must not keep the ride: the respawned player has the same id. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !RIDES.containsKey(p.getUUID())) return;
        RIDES.remove(p.getUUID());
        p.getPersistentData().remove(RIDING_TAG);
        p.setNoGravity(false);
        p.noPhysics = false;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Ride r = RIDES.get(p.getUUID());
        if (r != null) end(p, r);
    }

    private static void hold(ServerPlayer p) {
        p.setDeltaMovement(0, 0, 0);
        p.hurtMarked = true;
    }

    private static void lift(ServerPlayer p, double dy) {
        p.setDeltaMovement(0, dy, 0);
        p.hurtMarked = true;
    }

    private static void next(ServerPlayer p, Ride r, int phase) {
        r.phase = phase;
        r.phaseT = 0;
        LOG.info("Terminal ride of {}: phase {} at y={} (ground y={}, tick {})", p.getName().getString(), phase, p.getY(), r.y, r.t);
        send(p, r);
    }

    private static void end(ServerPlayer p, Ride r) {
        LOG.info("Terminal ride of {} ended at tick {}", p.getName().getString(), r.t);
        RIDES.remove(p.getUUID());
        p.getPersistentData().remove(RIDING_TAG);
        p.setNoGravity(false);
        p.noPhysics = false;
        p.setDeltaMovement(0, 0, 0);
        p.hurtMarked = true;
        setDown(p);
        com.botwclimb.common.Net.toWatchers(p, new TerminalRidePayload(p.getId(), 0, (float) r.y, r.sink ? 1 : 0), true);
    }

    /** A rider whose ride was cut short in the air (timeout, logout, crash) is put on the ground below, so letting go never means a long fall. */
    private static void setDown(ServerPlayer p) {
        if (p.onGround()) return;
        ServerLevel level = com.botwclimb.common.Compat.level(p);
        level.getChunkAt(p.blockPosition()); // a player just logged in may not have their chunk yet, and an unloaded chunk has no floor
        AABB box = p.getBoundingBox();
        if (!level.noCollision(box)) return; // inside something: leave it to the game
        for (int d = 1; d <= 160; d++) {
            AABB lower = box.move(0, -d, 0);
            if (!level.noCollision(lower)) { // the first step down that hits something; walk back up to just above it
                double dy = -d + 1;
                while (level.noCollision(box.move(0, dy - 0.05, 0))) dy -= 0.05;
                p.teleportTo(p.getX(), p.getY() + dy, p.getZ());
                return;
            }
        }
    }

    private static void send(ServerPlayer p, Ride r) {
        com.botwclimb.common.Net.toWatchers(p, new TerminalRidePayload(p.getId(), r.phase, (float) r.y, r.sink ? 1 : 0), true);
    }
}
