package com.botwclimb.common.shrine;

import com.botwclimb.BotwClimb;
import com.botwclimb.network.TerminalRidePayload;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
    private static final int GRAB_HOME = 42, GRAB_UP = 44, RISE_MAX = 60, HOLD = 6, RELEASE = 18, TIMEOUT = 400;
    /** How high the ride goes before the world changes, in blocks. */
    public static final int RISE_HEIGHT = 60;
    /** How far a Return ride sinks through the ground before the world changes. */
    public static final int SINK_DEPTH = 16;
    /** Ticks a Return ride waits, wrapped, over the unravelled ground before it starts to sink. */
    public static final int SINK_DELAY = 6;
    /** Blocks per tick: the rise starts at START_SPEED, gains ACCEL each tick, and tops out at MAX_SPEED, both ways. */
    public static final double MAX_SPEED = 2.6, START_SPEED = 0.5, ACCEL = 0.32;
    private static final Logger LOG = LogUtils.getLogger();
    private static final int DESCENT_MAX = 120;
    private static final String RIDING_TAG = BotwClimb.MOD_ID + ":terminal_ride";
    private static final Map<UUID, Ride> RIDES = new HashMap<>();

    private static final class Ride {
        final BlockPos terminal;
        final ResourceKey<Level> origin;
        final int target;
        /** Returning from a plot: the ground opens and the cocoon goes straight down, where every other ride goes up. */
        final boolean sink;
        /** The ground the rider comes down to; the start of the ride until the trip has moved them. */
        double x, y, z;
        int phase = 1, t, phaseT;

        Ride(BlockPos terminal, int target, ServerPlayer p) {
            this.terminal = terminal;
            this.target = target;
            this.sink = target == PlotTravel.RETURN;
            origin = p.level().dimension();
            x = p.getX();
            y = p.getY();
            z = p.getZ();
        }
    }

    private TerminalRide() {}

    public static boolean isRiding(ServerPlayer p) {
        return RIDES.containsKey(p.getUUID());
    }

    /** @param target a plot slot, or {@link PlotTravel#RETURN} */
    public static void start(ServerPlayer p, BlockPos terminal, int target) {
        if (isRiding(p)) return;
        Ride r = new Ride(terminal, target, p);
        RIDES.put(p.getUUID(), r);
        p.setNoGravity(true);
        p.getPersistentData().putBoolean(RIDING_TAG, true); // no gravity is saved with the player, so a ride cut short must be undone on the next login
        setLit(p, r, true);
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
                    lift(p, Math.min(MAX_SPEED, START_SPEED + ACCEL * r.phaseT));
                    double risen = p.getY() - r.y;
                    boolean stuck = r.phaseT > 6 && risen < 0.5 * r.phaseT; // a ceiling stops the rise
                    if (risen >= RISE_HEIGHT || r.phaseT >= RISE_MAX || stuck) next(p, r, 3);
                }
            }
            case 3 -> {
                hold(p);
                if (r.phaseT >= HOLD) {
                    travel(p, r);
                    next(p, r, 4);
                }
            }
            case 4 -> {
                double remaining = p.getY() - r.y;
                lift(p, -Math.min(MAX_SPEED, 0.3 + 0.2 * Math.max(remaining, 0))); // fast from high up, gentle for the last few blocks
                if (remaining <= 0.3 || p.onGround() && r.phaseT > 5 || r.phaseT >= DESCENT_MAX) { // never leave the rider hanging
                    p.teleportTo(r.x, r.y, r.z);
                    next(p, r, 5);
                }
            }
            case 5 -> {
                hold(p);
                if (r.phaseT >= RELEASE) end(p, r);
            }
        }
        if (RIDES.containsKey(p.getUUID()) && r.t > TIMEOUT) end(p, r);
    }

    /**
     * The hidden move. The rider is put the same height above the destination's ground as they are above the ground here, so the
     * mud simply carries on down. If there is no such place the ride comes back down where it began.
     */
    private static void travel(ServerPlayer p, Ride r) {
        PlotTravel.Arrival a = PlotTravel.arrival(p, r.target, r.sink ? RISE_HEIGHT : p.getY() - r.y); // both ways the rider comes down out of the sky
        if (a == null) {
            LOG.warn("Terminal ride of {} has nowhere to go (target {}); it comes back down here", p.getName().getString(), r.target);
            return;
        }
        r.x = a.x();
        r.y = a.y();
        r.z = a.z();
        if (a.level() != p.level()) { // players watching from the world the rider leaves must not keep drawing mud on nobody
            PacketDistributor.sendToPlayersTrackingEntity(p, new TerminalRidePayload(p.getId(), 0, (float) r.y, r.sink ? 1 : 0));
        }
        com.botwclimb.common.Compat.teleport(p, a.level(), a.x(), a.y() + a.rise(), a.z(), a.yaw(), 0f);
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
        if (!cut && !(p.isNoGravity() && !p.isCreative() && !p.isSpectator())) return;
        p.getPersistentData().remove(RIDING_TAG);
        p.setNoGravity(false);
        p.setDeltaMovement(0, 0, 0);
        setDown(p);
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
        setLit(p, r, false);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new TerminalRidePayload(p.getId(), 0, (float) r.y, r.sink ? 1 : 0));
    }

    /** A rider whose ride was cut short in the air (timeout, logout, crash) is put on the ground below, so letting go never means a long fall. */
    private static void setDown(ServerPlayer p) {
        if (p.onGround()) return;
        ServerLevel level = p.serverLevel();
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
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new TerminalRidePayload(p.getId(), r.phase, (float) r.y, r.sink ? 1 : 0));
    }

    /** The terminal the ride began at (it may be in another dimension by now). */
    private static void setLit(ServerPlayer p, Ride r, boolean on) {
        ServerLevel level = p.server.getLevel(r.origin);
        if (level == null) return;
        BlockState s = level.getBlockState(r.terminal);
        if (s.is(BotwClimb.TERMINAL_BLOCK.get())) level.setBlock(r.terminal, s.setValue(ShrineTerminalBlock.ACTIVE, on), 3);
    }
}
