package com.botwclimb.common.climb;

import com.botwclimb.BotwClimb;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static com.botwclimb.common.climb.ClimbConstants.*;

/**
 * The climbing simulation for the locally controlled player (runs on the client, once per tick, right after the
 * movement input is read). It only decides state and sets the velocity for this tick; vanilla {@code move()} then
 * applies it with full collision, so the player can never end up inside a block. The server runs
 * {@link ServerClimb} to validate the result.
 */
public final class ClimbController {
    private ClimbController() {}

    public static boolean canClimb(Player p) {
        return !p.isSpectator() && !p.isDeadOrDying() && !p.getAbilities().flying && !p.isFallFlying()
                && !p.isPassenger() && !p.isSleeping() && !p.isSwimming() && !p.isUnderWater()
                && !p.isInLava() && !p.onClimbable();
    }

    /**
     * @param fwd/str  raw movement impulses (forward and left positive)
     * @param sneak/sprint  the player is trying to get off the wall
     */
    public static void stepLocal(Player p, ClimbData d, float fwd, float str, boolean jumpDown,
                                 boolean climbKey, boolean sneak, boolean sprint) {
        if (d.attachCooldown > 0) d.attachCooldown--;
        if (d.jumpCooldown > 0) d.jumpCooldown--;
        boolean jumpPressed = jumpDown && !d.jumpHeld; // edge trigger: holding Space never re-fires
        d.jumpHeld = jumpDown;
        d.stateTicks++;

        if (d.state == ClimbState.NONE) {
            tryAttach(p, d, climbKey, sneak, sprint);
            return;
        }
        p.fallDistance = 0;
        if (sneak || sprint || !canClimb(p)) {
            release(p, d, ATTACH_COOLDOWN, true);
            return;
        }
        switch (d.state) {
            case CLIMBING -> stepClimbing(p, d, fwd, str, jumpPressed);
            case CLIMB_JUMP -> stepJump(p, d);
            case LEDGE_TRANSITION -> stepLedge(p, d);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ attach / release

    /** Holding the climb key next to a wall for CLIMB_HOLD_TICKS grabs it. Space is untouched until you are on the wall. */
    private static void tryAttach(Player p, ClimbData d, boolean keyDown, boolean sneak, boolean sprint) {
        if (!keyDown || d.attachCooldown > 0 || sneak || sprint || p.isSprinting() || !canClimb(p)) {
            d.keyHold = 0;
            return;
        }
        Direction dir = findWall(p);
        if (dir == null) {
            d.keyHold = 0;
            return;
        }
        if (++d.keyHold < CLIMB_HOLD_TICKS) return;
        d.keyHold = 0;
        d.dir = dir;
        d.setState(ClimbState.CLIMBING);
        d.cornerTicks = 0;
        p.setSprinting(false);
        p.setDeltaMovement(Vec3.ZERO);
    }

    /** The wall next to the player that is closest to where they are looking, or null. */
    public static Direction findWall(Player p) {
        Direction best = null;
        float bestDiff = Float.MAX_VALUE;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (!SurfaceSampler.sample(p.level(), p, p.getBoundingBox(), dir).canAttach(ATTACH_GAP)) continue;
            float diff = Math.abs(Mth.wrapDegrees(p.getYRot() - dir.toYRot()));
            if (diff < bestDiff) {
                bestDiff = diff;
                best = dir;
            }
        }
        return best;
    }

    public static void release(Player p, ClimbData d, int attachCooldown, boolean stop) {
        d.setState(ClimbState.NONE);
        d.attachCooldown = attachCooldown;
        d.cornerTicks = 0;
        d.plan = null;
        if (stop) p.setDeltaMovement(Vec3.ZERO);
    }

    // ------------------------------------------------------------------ normal climbing

    private static void stepClimbing(Player p, ClimbData d, float fwd, float str, boolean jumpPressed) {
        Level level = p.level();
        Direction dir = d.dir;
        AABB box = p.getBoundingBox();

        if (d.cornerTicks > 0) {
            stepCorner(p, d, box);
            return;
        }

        SurfaceContact c = SurfaceSampler.sample(level, p, box, dir);
        double up = Math.abs(fwd) < 0.1f ? 0 : Math.signum(fwd);
        double side = Math.abs(str) < 0.1f ? 0 : Math.signum(str);

        if (jumpPressed && d.jumpCooldown == 0 && c.holds(HOLD_GAP)) {
            startJump(p, d);
            return;
        }
        if (up < 0 && p.onGround() && d.stateTicks > 2) {
            release(p, d, ATTACH_COOLDOWN, true); // climbed back down to the floor
            return;
        }
        if (up > 0) {
            double ledge = SurfaceSampler.findLedge(level, p, box, dir);
            if (!Double.isNaN(ledge)) {
                startLedge(d, ledge);
                return;
            }
        }
        if (!c.holds(HOLD_GAP)) {
            release(p, d, 5, false); // the wall is gone: fall with whatever momentum is left
            return;
        }

        Vec3 vel = new Vec3(0, up * CLIMB_SPEED, 0);
        if (side != 0) {
            Direction lat = side > 0 ? dir.getCounterClockWise() : dir.getClockWise(); // str > 0 is left
            Vec3 step = new Vec3(lat.getStepX(), 0, lat.getStepZ()).scale(SIDE_SPEED);
            AABB moved = box.move(step);
            if (SurfaceSampler.blocked(level, p, moved)) {
                // inner corner: a perpendicular wall is in the way, turn onto it
                if (SurfaceSampler.sample(level, p, box, lat).canAttach(HOLD_GAP)) {
                    d.dir = lat;
                    p.setDeltaMovement(Vec3.ZERO);
                    return;
                }
            } else {
                if (!SurfaceSampler.sample(level, p, moved, dir).holds(HOLD_GAP)) {
                    // wall ends this way: look for the face around an outer corner
                    d.cornerTicks = CORNER_MAX_TICKS;
                    d.cornerTo = lat.getOpposite();
                    p.setDeltaMovement(Vec3.ZERO);
                    return;
                }
                vel = vel.add(step);
            }
        }
        double pull = Mth.clamp(c.gap() - ADHESION_GAP, -0.05, ADHESION_MAX_PULL);
        vel = vel.add(dir.getStepX() * pull, 0, dir.getStepZ() * pull);
        p.setDeltaMovement(vel);
    }

    /** Outer corner: slide round the end of the wall until the side face of that last block is in reach. */
    private static void stepCorner(Player p, ClimbData d, AABB box) {
        if (SurfaceSampler.sample(p.level(), p, box, d.cornerTo).holds(HOLD_GAP)) {
            d.dir = d.cornerTo;
            d.cornerTicks = 0;
            p.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (--d.cornerTicks <= 0) {
            release(p, d, 5, true);
            return;
        }
        p.setDeltaMovement(d.dir.getStepX() * CORNER_SPEED, 0, d.dir.getStepZ() * CORNER_SPEED);
    }

    // ------------------------------------------------------------------ climb jump

    private static void startJump(Player p, ClimbData d) {
        d.plan = JumpPlanner.plan(p.level(), p, p.position(), d.dir);
        d.jumpStart = p.position();
        d.jumpTick = 0;
        d.setState(ClimbState.CLIMB_JUMP);
        if (BotwClimb.DEBUG && p.level().isClientSide) debugPlan(p, d);
    }

    private static void stepJump(Player p, ClimbData d) {
        JumpPlan plan = d.plan;
        if (plan == null) {
            release(p, d, ATTACH_COOLDOWN, false);
            return;
        }
        int k = d.jumpTick++;
        if (k < JUMP_WINDUP_TICKS) { // crouch against the wall, the animation loads the legs
            p.setDeltaMovement(Vec3.ZERO);
            return;
        }
        int idx = k - JUMP_WINDUP_TICKS;
        if (idx < plan.length()) {
            // follow the planned waypoint; vanilla collision still has the last word
            if (p.level().isClientSide) BotwClimb.LOGGER.info("jump tick {} rel=({}, {}) plan=({}, {})", idx, fmt(p.getY() - d.jumpStart.y), fmt(p.getZ() - d.jumpStart.z), fmt(plan.steps()[idx].y), fmt(plan.steps()[idx].z));
            Vec3 v = d.jumpStart.add(plan.steps()[idx]).subtract(p.position());
            double len = v.length();
            if (len > 1.0) v = v.scale(1.0 / len);
            p.setDeltaMovement(v);
            return;
        }
        // trajectory finished: grab whatever is there
        int extra = idx - plan.length();
        AABB box = p.getBoundingBox();
        if (plan.goal() == JumpPlan.Goal.LEDGE) {
            double ledge = SurfaceSampler.findLedge(p.level(), p, box, d.dir);
            if (!Double.isNaN(ledge)) {
                startLedge(d, ledge);
                return;
            }
        }
        SurfaceContact c = SurfaceSampler.sample(p.level(), p, box, d.dir);
        if (c.holds(REGRAB_GAP)) {
            d.setState(ClimbState.CLIMBING);
            d.jumpCooldown = JUMP_COOLDOWN;
            d.plan = null;
            double pull = Mth.clamp(c.gap() - ADHESION_GAP, 0, ADHESION_MAX_PULL);
            p.setDeltaMovement(d.dir.getStepX() * pull, 0, d.dir.getStepZ() * pull);
            return;
        }
        if (plan.goal() != JumpPlan.Goal.NONE && extra < JUMP_SEARCH_TICKS) {
            p.setDeltaMovement(Vec3.ZERO); // short window to settle onto the wall
            return;
        }
        // nothing to grab: lose the wall and fall
        Vec3 last = plan.length() > 1 ? plan.steps()[plan.length() - 1].subtract(plan.steps()[plan.length() - 2]) : Vec3.ZERO;
        release(p, d, ATTACH_COOLDOWN, false);
        d.jumpCooldown = JUMP_COOLDOWN;
        p.setDeltaMovement(last.scale(0.4));
    }

    // ------------------------------------------------------------------ top edge

    private static void startLedge(ClimbData d, double ledgeY) {
        d.ledgeY = ledgeY;
        d.ledgeTicks = 0;
        d.plan = null;
        d.setState(ClimbState.LEDGE_TRANSITION);
    }

    private static void stepLedge(Player p, ClimbData d) {
        if (++d.ledgeTicks > LEDGE_MAX_TICKS) {
            release(p, d, ATTACH_COOLDOWN, true);
            return;
        }
        Direction dir = d.dir;
        double feet = p.getY();
        if (feet < d.ledgeY - 0.005) {
            double rise = Math.min(LEDGE_RISE_SPEED, d.ledgeY - feet + 0.02);
            p.setDeltaMovement(dir.getStepX() * 0.03, rise, dir.getStepZ() * 0.03);
        } else {
            p.setDeltaMovement(dir.getStepX() * LEDGE_FORWARD_SPEED, -0.04, dir.getStepZ() * LEDGE_FORWARD_SPEED);
            if (p.onGround() && d.ledgeTicks > 2) {
                release(p, d, ATTACH_COOLDOWN, true);
            }
        }
    }

    private static String fmt(double v) {
        return String.format("%.2f", v);
    }

    private static void debugPlan(Player p, ClimbData d) {
        for (Vec3 s : d.plan.steps()) {
            Vec3 w = d.jumpStart.add(s);
            p.level().addParticle(ParticleTypes.END_ROD, w.x, w.y + 0.9, w.z, 0, 0, 0);
        }
    }
}
