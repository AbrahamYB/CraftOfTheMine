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

    /** Blocks per tick: five times the fastest legitimate wall movement (enchanted boots, 0.14), a few ticks of it in a row is not lag. */
    private static final double MAX_CLIMB_STEP = 0.7;
    private static final int MAX_FAST_TICKS = 3, MAX_OFF_TICKS = 8;
    /** A jump or ledge may start while the wall has been out of reach for this many ticks (a late packet puts the server's view a moment behind): half a second of grace for a slow connection. */
    private static final int ENTRY_BAD_TICKS = 10;

    private ServerClimb() {}

    /** Moving along a held wall far faster than any climb: true once it has gone on for more than a few ticks. */
    public static boolean tooFast(ClimbData d, double stepSqr) {
        if (d.state == ClimbState.CLIMBING && d.grace == 0 && stepSqr > MAX_CLIMB_STEP * MAX_CLIMB_STEP) return ++d.fastTicks > MAX_FAST_TICKS;
        d.fastTicks = 0;
        return false;
    }

    /** Outside the box a jump can reach for more than a few ticks in a row (lag gives a tick or two, never this). */
    public static boolean offEnvelope(ClimbData d, Vec3 pos) {
        if (!offPlan(d, pos)) {
            d.offTicks = 0;
            return false;
        }
        return ++d.offTicks > MAX_OFF_TICKS;
    }

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
                com.botwclimb.common.Net.toPlayer(viewer, new ClimbStatePayload(target.getId(), d.state, d.dir, false));
            }
        }
    }

    private static void tick(ServerPlayer p) {
        ClimbData d = p.getData(BotwClimb.CLIMB);
        d.stateTicks++;
        if (d.jumpCooldown > 0) d.jumpCooldown--;
        if (d.grace > 0) d.grace--;
        // the server's own stamina account: moving on a wall and gliding drain it, a modified client cannot cheat it away
        double stepSqr = p.position().distanceToSqr(d.sPos);
        boolean moved = stepSqr > 0.0004;
        d.sPos = p.position();
        Stamina.serverStep(d, ClimbController.maxStamina(p), d.state == ClimbState.CLIMBING && moved, p.getData(BotwClimb.PARAGLIDER).gliding,
                p.onGround() || p.getAbilities().flying);
        if (d.active() && d.sExhausted) { // out of stamina on the server: let go, whatever the client claims
            d.claim = ClimbState.NONE;
            reject(p, d);
            return;
        }
        if (d.active() && tooFast(d, stepSqr)) { // a wall climb has a top speed: a modified client could otherwise crawl up or along a wall as fast as vanilla's own checks allow
            d.claim = ClimbState.NONE;
            reject(p, d);
            return;
        }
        if (d.active() && d.badTicks == 0) { // only while the wall is really there: a claim without one gets no fall protection
            p.fallDistance = 0;
            if (p.connection != null) p.connection.aboveGroundTickCount = 0; // a validated climb hangs in the air as far as the fly check can tell (a dedicated server kicks after 80 ticks)
        }

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

        if (d.sExhausted) { // no new climb while the server's account is empty
            reject(p, d);
            return;
        }
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
                if (hanging || d.grace > 0 && d.state != ClimbState.LEDGE_TRANSITION) { // the grace of a fresh attach covers the latch hop; a ledge state never gets a free regrab
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
                    if (d.jumpCooldown > 0 || d.badTicks > ENTRY_BAD_TICKS) { // a jump starts from a wall that is really being held
                        reject(p, d);
                        return;
                    }
                    d.dir = d.claimDir;
                    d.jumpStart = pos; // the player holds still during the wind-up, so this is where the client started
                    Stamina.serverSpend(d, STAMINA_JUMP_COST);
                    d.jumpTick = 0;
                    d.offTicks = 0;
                    d.jumpCooldown = JUMP_COOLDOWN;
                    enter(p, d, ClimbState.CLIMB_JUMP);
                } else if (d.state == ClimbState.CLIMB_JUMP) {
                    if (++d.jumpTick > 40) { // a jump is at most ~18 ticks; this only catches a stuck claim
                        reject(p, d);
                    } else if (offEnvelope(d, pos)) {
                        // a tick or two outside is the server's position lagging the client's; staying out for longer is not a jump
                        BotwClimb.LOGGER.info("jump outside reach envelope at {} (start {}): let go", pos, d.jumpStart);
                        reject(p, d);
                    }
                } else {
                    reject(p, d);
                }
            }
            case LEDGE_TRANSITION -> {
                if (d.state == ClimbState.CLIMBING && d.badTicks <= ENTRY_BAD_TICKS) { // the client starts a ledge only from a wall hold; chaining jump -> ledge -> grab -> ledge in open air was free flight
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
        d.grace = s == ClimbState.CLIMBING ? SERVER_GRACE_TICKS : 0; // only a hold earns grace: it must not be re-armed by jump or ledge claims
        d.badTicks = 0;
        if (s == ClimbState.CLIMBING) d.lastValidPos = p.position();
        setState(p, d, s, false);
    }

    /** Rejecting never moves the player: the client is told to let go and falls from where it is. */
    private static void reject(ServerPlayer p, ClimbData d) {
        d.claim = ClimbState.NONE;
        if (d.active()) {
            setState(p, d, ClimbState.NONE, true);
        } else if (p.tickCount % 8 == 0) { // nothing changed: a spammed claim costs one small correction to the sender now and then, not a broadcast per packet
            com.botwclimb.common.Net.toPlayer(p, new ClimbStatePayload(p.getId(), ClimbState.NONE, d.dir, true));
        }
    }

    private static void setState(ServerPlayer p, ClimbData d, ClimbState s, boolean correction) {
        d.setState(s);
        com.botwclimb.common.Net.toWatchers(p, new ClimbStatePayload(p.getId(), s, d.dir, correction), true);
    }
}
