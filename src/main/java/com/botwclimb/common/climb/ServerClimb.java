package com.botwclimb.common.climb;

import com.botwclimb.BotwClimb;
import com.botwclimb.network.ClimbStatePayload;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.botwclimb.common.climb.ClimbConstants.*;

/**
 * Server side of climbing. Movement itself follows vanilla's player movement trust model (the client reports its
 * position and vanilla validates collision), so the server does not re-simulate. It instead decides whether the
 * client's claimed climb state is legal: a wall must really be there, a jump needs a real collision-free plan and
 * the player must stay on that plan, and a ledge climb has to stay close to where it started. Illegal claims are
 * rejected, the player is put back (if they moved illegally) and told to let go.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID)
public final class ServerClimb {
    private static final int MAX_BAD_TICKS = 70; // shortcut: going round a block leaves the wall for ~2 s; upgrade when the server tracks it

    private ServerClimb() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer sp) tick(sp);
    }

    /** Being hit while on a wall knocks you off. The client plays the fall (see NetworkHandler). */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.getData(BotwClimb.CLIMB).active()) {
            ClimbData d = p.getData(BotwClimb.CLIMB);
            d.claim = ClimbState.NONE;
            reject(p, d);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer target) {
            ClimbData d = target.getData(BotwClimb.CLIMB);
            if (d.active()) {
                PacketDistributor.sendToPlayer(viewer, new ClimbStatePayload(target.getId(), d.state, d.dir, false));
            }
        }
    }

    private static void tick(ServerPlayer p) {
        ClimbData d = p.getData(BotwClimb.CLIMB);
        d.stateTicks++;
        if (d.jumpCooldown > 0) d.jumpCooldown--;
        if (d.grace > 0) d.grace--;
        if (d.active()) p.fallDistance = 0;

        ClimbState claim = d.claim;
        // Sneak only lets go of a wall that has been held for a moment: the packet that clears the client's own
        // Shift (it is part of the Shift+Space climb key) is still on its way when a new climb is claimed.
        if (claim == ClimbState.NONE || !ClimbController.canClimb(p)
                || (d.active() && p.isShiftKeyDown() && d.stateTicks > SERVER_GRACE_TICKS)) {
            if (d.active()) setState(p, d, ClimbState.NONE, false);
            d.claim = ClimbState.NONE;
            return;
        }

        Level level = p.level();
        AABB box = p.getBoundingBox();
        Vec3 pos = p.position();
        double gap = HOLD_GAP + SERVER_EXTRA_GAP;

        if (d.state == ClimbState.NONE) { // any claim starts by hanging on a wall; later states are checked below
            if (SurfaceSampler.sample(level, p, box, d.claimDir).holds(gap)) {
                d.dir = d.claimDir;
                d.lastValidPos = pos;
                enter(p, d, ClimbState.CLIMBING);
            } else {
                reject(p, d);
                return;
            }
        }

        switch (claim) {
            case CLIMBING -> {
                boolean hanging = SurfaceSampler.sample(level, p, box, d.claimDir).holds(gap)
                        || !Double.isNaN(SurfaceSampler.findLedge(level, p, box, d.claimDir));
                if (hanging || d.grace > 0) {
                    if (hanging) {
                        d.badTicks = 0;
                        d.lastValidPos = pos;
                    }
                    d.dir = d.claimDir;
                    if (d.state != ClimbState.CLIMBING) enter(p, d, ClimbState.CLIMBING);
                } else if (++d.badTicks > MAX_BAD_TICKS) {
                    reject(p, d);
                }
            }
            case CLIMB_JUMP -> {
                if (d.state == ClimbState.CLIMBING) {
                    if (d.jumpCooldown > 0) {
                        reject(p, d);
                        return;
                    }
                    d.dir = d.claimDir;
                    d.jumpStart = pos; // the player holds still during the wind-up, so this is where the client started
                    d.plan = JumpPlanner.plan(level, p, d.jumpStart, d.dir);
                    d.jumpTick = 0;
                    d.warned = false;
                    d.jumpCooldown = JUMP_COOLDOWN;
                    enter(p, d, ClimbState.CLIMB_JUMP);
                } else if (d.state == ClimbState.CLIMB_JUMP) {
                    if (++d.jumpTick > 40) { // a jump is at most ~18 ticks; this only catches a stuck claim
                        reject(p, d);
                    } else if (offPlan(d, pos) && !d.warned) {
                        // Logged only: rejecting here threw legitimate jumps back because the server's view of the
                        // position lags. Vanilla movement checks still catch real speed/collision cheating.
                        d.warned = true;
                        BotwClimb.LOGGER.info("jump outside reach envelope at {} (start {})", pos, d.jumpStart);
                    }
                } else {
                    reject(p, d);
                }
            }
            case LEDGE_TRANSITION -> {
                if (d.state == ClimbState.CLIMBING || d.state == ClimbState.CLIMB_JUMP) {
                    d.jumpStart = pos;
                    d.ledgeTicks = 0;
                    enter(p, d, ClimbState.LEDGE_TRANSITION);
                } else if (d.state == ClimbState.LEDGE_TRANSITION) {
                    if (++d.ledgeTicks > LEDGE_MAX_TICKS + 40) { // shortcut: the block climb-up is longer than a wall ledge; upgrade when the server tracks it
                        reject(p, d);
                    }
                } else {
                    reject(p, d);
                }
            }
            default -> {}
        }
    }

    /**
     * Legal-jump envelope: the player may only be within the box a climb jump can reach from where it started
     * (a little up/out/down, never sideways). Collision itself is already enforced by vanilla movement validation.
     * Exact path matching is deliberately not used: the server's position lags the client's and its own plan can
     * differ by a hair, which wrongly threw players back.
     */
    private static boolean offPlan(ClimbData d, Vec3 pos) {
        Vec3 rel = pos.subtract(d.jumpStart);
        Direction dir = d.dir;
        double out = -(rel.x * dir.getStepX() + rel.z * dir.getStepZ());
        double lateral = Math.abs(rel.x * dir.getStepZ() - rel.z * dir.getStepX());
        return out < -0.6 || out > JUMP_MAX_OUT + 0.6 || lateral > JUMP_SIDE_LEAP + 0.8
                || rel.y < -1.0 || rel.y > JUMP_MAX_RISE + 1.6; // plan rise + ledge climb + position lag
    }

    private static void enter(ServerPlayer p, ClimbData d, ClimbState s) {
        d.grace = SERVER_GRACE_TICKS;
        d.badTicks = 0;
        if (s == ClimbState.CLIMBING) d.lastValidPos = p.position();
        setState(p, d, s, false);
    }

    /** Rejecting never moves the player: the client is told to let go and falls from where it is. */
    private static void reject(ServerPlayer p, ClimbData d) {
        d.claim = ClimbState.NONE;
        setState(p, d, ClimbState.NONE, true);
    }

    private static void setState(ServerPlayer p, ClimbData d, ClimbState s, boolean correction) {
        d.setState(s);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new ClimbStatePayload(p.getId(), s, d.dir, correction));
    }
}
