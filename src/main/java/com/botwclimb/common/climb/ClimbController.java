package com.botwclimb.common.climb;

import com.botwclimb.BotwClimb;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import static com.botwclimb.common.climb.ClimbConstants.*;

/**
 * The climbing simulation for the locally controlled player (runs on the client, once per tick, right after the
 * movement input is read). It only decides state and sets the velocity for this tick; vanilla {@code move()} then
 * applies it with full collision, so the player can never end up inside a block. The server runs
 * {@link ServerClimb} to validate the result.
 */
public final class ClimbController {
    private ClimbController() {}

    public static final ResourceKey<Enchantment> CLIMB_SPEED_ENCHANT =
            ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "climb_speed"));

    /** Boots with the Climb Speed enchantment (a data-driven enchantment, so it is looked up in the registry). */
    public static boolean hasClimbSpeed(Player p) {
        Holder<Enchantment> h = com.botwclimb.common.Compat.holder(p.level(), CLIMB_SPEED_ENCHANT).orElse(null);
        return h != null && EnchantmentHelper.getEnchantmentLevel(h, p) > 0;
    }

    public static float maxStamina(Player p) {
        int u = Mth.clamp(p.getData(BotwClimb.STAMINA_UPGRADES), 0, STAMINA_MAX_UPGRADES);
        return STAMINA_WHEEL + STAMINA_PER_UPGRADE * u;
    }

    public static boolean canClimb(Player p) {
        return !p.isSpectator() && !p.isDeadOrDying() && !p.getAbilities().flying && !p.isFallFlying()
                && !p.isPassenger() && !p.isSleeping() && !p.isSwimming() && !p.isUnderWater()
                && !p.isInLava() && !p.onClimbable();
    }

    /**
     * @param fwd/str  raw movement impulses (forward and left positive)
     * @param sneak  the player is trying to get off the wall
     */
    public static void stepLocal(Player p, ClimbData d, float fwd, float str, boolean jumpDown,
                                 boolean climbKey, boolean sneak) {
        if (d.attachCooldown > 0) d.attachCooldown--;
        if (d.jumpCooldown > 0) d.jumpCooldown--;
        boolean jumpPressed = jumpDown && !d.jumpHeld; // edge trigger: holding Space never re-fires
        d.jumpHeld = jumpDown;
        d.stateTicks++;
        d.climbSpeed = hasClimbSpeed(p);
        float maxStamina = maxStamina(p);
        if (d.stamina < 0 || d.stamina > maxStamina) d.stamina = maxStamina;
        // joining: the saved upgrades reach the client a moment after its first tick, so a full wheel would look half empty and refill on screen
        else if (p.tickCount < 200 && d.lastMax > 0 && maxStamina > d.lastMax && d.stamina >= d.lastMax - 0.5f) d.stamina = maxStamina;
        d.lastMax = maxStamina;
        if (d.staminaFlash > 0) d.staminaFlash--;
        // recovers on the ground, while flying in creative and during an ordinary jump, but not in a real fall (4+ blocks) or
        // on a wall: emptying the wheel in mid-air means waiting until you land before it refills
        boolean recovers = p.onGround() || p.getAbilities().flying || p.fallDistance < STAMINA_FALL_NO_REGEN;
        if (d.state == ClimbState.NONE && recovers) {
            if (d.staminaDelay > 0) d.staminaDelay--;
            else if (d.stamina < maxStamina) d.stamina = Math.min(maxStamina, d.stamina + STAMINA_REGEN);
        }
        if (d.exhausted && d.stamina >= maxStamina) d.exhausted = false; // the wheel is full again

        if (d.state == ClimbState.NONE) {
            // plain Space next to a wall with a block just over your head (not attached yet) is the ledge jump, not a normal jump
            if (jumpPressed && fwd > 0.1f && !climbKey && d.attachCooldown == 0 && !d.exhausted && d.stamina >= STAMINA_ATTACH_MIN && canClimb(p) && !sneak
                    && blockInReach(p.level(), p, p.getBoundingBox())) { // only a block within jumping reach, not any overhang up to 3.5 above
                Direction dir = findWall(p);
                if (dir != null && ledgeDepth(p.level(), p, p.getBoundingBox(), dir) > 0) {
                    d.dir = dir;
                    d.setState(ClimbState.CLIMBING);
                    d.pendingBlockJump = true;
                    p.setDeltaMovement(Vec3.ZERO);
                    return;
                }
            }
            tryAttach(p, d, climbKey, sneak);
            return;
        }
        p.fallDistance = 0;
        if (sneak || !canClimb(p)) {
            release(p, d, ATTACH_COOLDOWN, true);
            return;
        }
        if (d.state != ClimbState.CLIMBING) d.cornerPlan = null; // a slide round a corner never outlives the climb (a server correction can end it)
        // a Space pressed while a hop is in the air (or just landed, still on its short cooldown) is not lost: the ledge jumps use it as soon as they may
        if (d.jumpBuffer > 0) d.jumpBuffer--;
        if (jumpPressed && (d.state == ClimbState.CLIMB_JUMP || (d.state == ClimbState.CLIMBING && d.jumpCooldown > 0 && d.latchTicks == 0))) d.jumpBuffer = LEDGE_BUFFER_TICKS;
        switch (d.state) {
            case CLIMBING -> stepClimbing(p, d, fwd, str, jumpPressed);
            case CLIMB_JUMP -> stepJump(p, d);
            case LEDGE_TRANSITION -> stepLedge(p, d);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ attach / release

    /** Holding the climb key next to a wall for CLIMB_HOLD_TICKS grabs it. Space is untouched until you are on the wall. */
    private static void tryAttach(Player p, ClimbData d, boolean keyDown, boolean sneak) {
        boolean tired = d.exhausted || d.stamina < STAMINA_ATTACH_MIN;
        if (keyDown && tired && d.attachCooldown == 0 && d.staminaFlash == 0) d.staminaFlash = 20; // too tired to grab
        if (!keyDown || d.attachCooldown > 0 || sneak || !canClimb(p) || tired) {
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
        d.latchTicks = LATCH_PROFILE.length; // the hop onto the wall
        d.jumpCooldown = LATCH_PROFILE.length + 4; // a stray or held Space right after grabbing is not a jump (no double jump, no sudden ledge jump)
        p.setSprinting(false);
        p.setDeltaMovement(Vec3.ZERO);
    }

    private static final float ATTACH_ANGLE = 60f; // degrees either side of the wall's direction

    /** The wall next to the player that is closest to where they are looking, or null. */
    public static Direction findWall(Player p) {
        Direction best = null;
        float bestDiff = Float.MAX_VALUE;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            float diff = Math.abs(Mth.wrapDegrees(p.getYRot() - dir.toYRot()));
            if (diff > ATTACH_ANGLE) continue; // a wall off to the side or behind is not one you are facing: no grab, Shift + Space stays a plain jump
            if (!SurfaceSampler.sample(p.level(), p, p.getBoundingBox(), dir).canAttach(ATTACH_GAP)) continue;
            if (diff < bestDiff) {
                bestDiff = diff;
                best = dir;
            }
        }
        return best;
    }

    public static void release(Player p, ClimbData d, int attachCooldown, boolean stop) {
        d.setState(ClimbState.NONE);
        d.staminaDelay = Math.max(d.staminaDelay, STAMINA_REGEN_DELAY);
        d.attachCooldown = attachCooldown;
        d.cornerTicks = 0;
        d.cornerPlan = null;
        d.plan = null;
        if (stop) p.setDeltaMovement(Vec3.ZERO);
    }

    // ------------------------------------------------------------------ normal climbing

    private static void stepClimbing(Player p, ClimbData d, float fwd, float str, boolean jumpPressed) {
        if (d.exhausted && d.stamina <= 0f) { // the last of it went into a jump: the grip is gone as soon as it lands
            release(p, d, ATTACH_COOLDOWN, false);
            return;
        }
        Level level = p.level();
        Direction dir = d.dir;
        AABB box = p.getBoundingBox();

        if (d.cornerPlan != null) {
            stepCornerSlide(p, d);
            return;
        }

        SurfaceContact c = SurfaceSampler.sample(level, p, box, dir);
        double up = Math.abs(fwd) < 0.1f ? 0 : Math.signum(fwd);
        double side = Math.abs(str) < 0.1f ? 0 : Math.signum(str);
        boolean ledgePress = jumpPressed || d.jumpBuffer > 0; // the ledge jumps also take a Space pressed just before, during a hop
        if (jumpPressed && level.isClientSide()) { // diagnostics for a ledge jump that does not start: what the controller saw when Space went down
            BotwClimb.LOGGER.info("[ledge] Space down: up={} side={} cooldown={} blockInReach={} blockAbove={} wallTop={} holds={} state={}", up, side, d.jumpCooldown,
                    blockInReach(level, p, box), blockAbove(level, p, box), wallTopAbove(level, p, box, dir), c.holds(HOLD_GAP), d.state);
        }

        // W+A / W+D with the block up and to that side (not straight above): the whole jump and climb-up go diagonally to it
        Direction latDir = side > 0 ? dir.getCounterClockWise() : dir.getClockWise();
        AABB sideBox = box.move(latDir.getStepX(), 0, latDir.getStepZ());
        boolean diagonal = side != 0 && up > 0 && ledgePress && !blockInReach(level, p, box) && blockInReach(level, p, sideBox)
                && !SurfaceSampler.blocked(level, p, box.expandTowards(latDir.getStepX(), 0, latDir.getStepZ()));
        if (d.pendingBlockJump || (d.jumpCooldown == 0 && (diagonal || (ledgePress && up > 0 && blockInReach(level, p, box))))) { // only with W (or W+A / W+D)
            d.jumpBuffer = 0;
            // a block (or block and a half) above: the only thing Space does here is the jump onto it.
            // The old climb jump is never used under a block.
            d.pendingBlockJump = false;
            AABB tb = diagonal ? sideBox : box;
            int depth = ledgeDepth(level, p, tb, dir);
            if (level.isClientSide()) BotwClimb.LOGGER.info("[ledge] block jump: depth={} stamina={} exhausted={}", depth, d.stamina, d.exhausted);
            if (depth == 0) return; // no way out past it (too deep, or too thick for a long jump)
            if (!spendJump(d, depth > 1 ? STAMINA_LONG_LEDGE_COST : STAMINA_LEDGE_COST)) return;
            double top = box.maxY + 0.3, bottom = Double.MAX_VALUE;
            for (VoxelShape shape : level.getBlockCollisions(p, tb.expandTowards(0, JUMP_MAX_RISE, 0))) bottom = Math.min(bottom, shape.bounds().minY); // the underside of the overhang
            // a long jump (2 or 3 deep) climbs onto the farthest block, so its top is the target: a block stacked on a nearer one is beside it, not in the way
            AABB landing = depth >= 2 ? tb.move(-dir.getStepX() * (depth - 1), 0, -dir.getStepZ() * (depth - 1)) : tb;
            for (VoxelShape shape : level.getBlockCollisions(p, landing.expandTowards(0, JUMP_MAX_RISE, 0))) {
                if (depth >= 2 && shape.bounds().minY > bottom + 1.01) continue; // not part of the layer that is climbed onto
                top = Math.max(top, shape.bounds().maxY);
            }
            startLedge(d, top);
            d.ledgeBlockMin = bottom;
            d.ledgeSide = (int) side;
            d.ledgeLat = 0;
            d.ledgeLatTotal = diagonal ? 1.0 : (side != 0 ? LEDGE_LATERAL : 0);
            d.ledgeOutNeed = depth + 0.05; // out past the front edge of the last block
            d.ledgeOutSpeed = depth > 1 ? LEDGE_LONG_SPEED : LEDGE_OUT_SPEED;
            d.ledgeOut = 0;
            return;
        }
        if (ledgePress && d.jumpCooldown == 0 && (up > 0 || side != 0) && !blockInReach(level, p, box)) {
            // A / D with Space counts here too: climbing along a wall and pressing Space under its top edge is a jump up onto it, drifting that way
            // the wall itself ends just above and there is nothing past it but air to walk on: the same jump, hang and pull-up onto its top edge
            double wt = wallTopAbove(level, p, box, dir);
            if (!Double.isNaN(wt) && spendJump(d, STAMINA_LEDGE_COST)) {
                d.jumpBuffer = 0;
                startLedge(d, wt);
                d.ledgeBlockMin = Double.MAX_VALUE;
                d.ledgeSide = (int) side; // W+A / W+D here: the climb up the wall top drifts that way too
                d.ledgeLat = 0;
                d.ledgeLatTotal = side != 0 ? 0.8 : 0;
                d.ledgeOutNeed = d.ledgeOut = 0.01; // nothing to clear: no dash out, straight up to the hang
                d.ledgeOutSpeed = LEDGE_OUT_SPEED;
                return;
            }
        }
        if (jumpPressed && d.jumpCooldown == 0 && c.holds(HOLD_GAP) && !(up > 0 && blockInReach(level, p, box))) { // a block further up (an overhang) does not stop the normal wall jumps
            startJump(p, d, (int) side, up == 0);
            return;
        }
        if (up < 0 && p.onGround() && d.stateTicks > 2) {
            release(p, d, ATTACH_COOLDOWN, true); // climbed back down to the floor
            return;
        }
        if (d.latchTicks > 0) { // the hop onto the wall: crouch, burst up (fast then slowing), then the climb takes over
            int i = LATCH_PROFILE.length - d.latchTicks;
            d.latchTicks--;
            double away = LATCH_OUT[i]; // kick back off the wall first, then pull in onto it
            double wobble = 0.015 * Math.sin(i * 1.3) * (d.latchTicks + 1) / LATCH_PROFILE.length; // a little effort, not a shake
            p.setDeltaMovement(dir.getOpposite().getStepX() * away + dir.getStepZ() * 0 + wobble * dir.getStepZ(), LATCH_PROFILE[i], dir.getOpposite().getStepZ() * away + wobble * dir.getStepX());
            return;
        }
        if ((up > 0 || d.jumpCooldown > 0) && !blockAbove(level, p, box)) { // with a block over you, only Space climbs it: no automatic grab. Just after a hop it finishes without W too: letting go of W must not drop you at the top
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

        // Constant climbing pace. The wavy feel lives in the animation only (ClimbAnimator), never in movement.
        // one speed in every direction: a diagonal (up + sideways) is scaled down so it is not faster than straight
        if (up != 0 || side != 0) { // moving on the wall costs stamina; at zero you lose your grip
            d.stamina -= STAMINA_CLIMB_DRAIN;
            if (d.stamina <= 0) {
                release(p, d, ATTACH_COOLDOWN, false);
                Stamina.exhaust(d); // BOTW style: no climbing until it has recovered completely
                return;
            }
        }
        double k = up != 0 && side != 0 ? Math.sqrt(0.5) : 1;
        if (d.climbSpeed) k *= ENCHANT_CLIMB_MULT;
        Vec3 vel = new Vec3(0, up * CLIMB_SPEED * k, 0);
        if (side != 0) {
            Direction lat = side > 0 ? dir.getCounterClockWise() : dir.getClockWise(); // str > 0 is left
            Vec3 step = new Vec3(lat.getStepX(), 0, lat.getStepZ()).scale(SIDE_SPEED * k);
            AABB moved = box.move(step);
            if (SurfaceSampler.blocked(level, p, moved)) {
                // inner corner: a perpendicular wall is in the way, turn onto it. A missing row in it, or a block over the head, must not stop the
                // turn (it used to need the head and torso both, and failed the first time): the same grip test as for hanging, with the wider reach
                if (SurfaceSampler.sample(level, p, box, lat).holds(REGRAB_GAP)) {
                    d.dir = lat;
                    p.setDeltaMovement(Vec3.ZERO);
                    return;
                }
                vel = vel.add(step); // blocked by something else (an overhang): vanilla collision slides along it instead of standing still
            } else {
                if (!SurfaceSampler.sample(level, p, moved, dir).holds(HOLD_GAP)) {
                    // wall ends this way: go round an outer corner if there is a face to hold on the other side. Otherwise this is just
                    // the end of the wall: stay hanging at the edge (no sideways step), never let go because of it
                    JumpPlan round = JumpPlanner.cornerSlide(level, p, p.position(), dir, (int) side);
                    if (round != null) {
                        d.cornerPlan = round;
                        d.cornerIdx = 0;
                        d.jumpStart = p.position();
                        p.setDeltaMovement(Vec3.ZERO);
                        return;
                    }
                } else {
                    vel = vel.add(step);
                }
            }
        }
        double pull = Mth.clamp(c.gap() - ADHESION_GAP, -0.05, ADHESION_MAX_PULL);
        vel = vel.add(dir.getStepX() * pull, 0, dir.getStepZ() * pull);
        p.setDeltaMovement(vel);
    }

    /** The slide round the end of a wall: follows the planned path, and the face it ends on becomes the wall. */
    private static void stepCornerSlide(Player p, ClimbData d) {
        JumpPlan plan = d.cornerPlan;
        if (d.cornerIdx >= plan.length()) {
            d.dir = plan.around();
            d.cornerPlan = null;
            p.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (d.cornerIdx >= plan.length() * 0.3) d.dir = plan.around(); // the body starts turning early, so it turns along with the slide, not after it
        Vec3 want = d.jumpStart.add(plan.steps()[d.cornerIdx++]);
        Vec3 v = want.subtract(p.position());
        double len = v.length();
        p.setDeltaMovement(len > 0.2 ? v.scale(0.2 / len) : v); // vanilla collision still has the last word
    }

    /** Upward velocity per tick of the latch hop: two ticks crouched, then a burst that slows as the body lifts. */
    private static final double[] LATCH_PROFILE = {0, 0, 0.42, 0.30, 0.16, 0.06};
    /** Sideways (away from the wall) per tick of the latch: a little kick back, then into the wall. */
    private static final double[] LATCH_OUT = {0, 0, 0.14, 0.10, -0.06, -0.14};

    private static final double LEDGE_TOP_REACH = 2.5; // ... or the block's top edge this far above the head
    private static final double LEDGE_REACH = 1.5;   // the block's underside may be at most this far above the head
    private static final double AROUND_OUT = 1.05;   // how far out the body goes to clear the block

    /** A block (or block and a half) is over the head, close enough that Space means the jump onto it. */
    /** A block just over the head, close enough to jump up and grab: Space is the ledge jump there, a normal wall jump otherwise. */
    private static boolean blockInReach(Level level, Player p, AABB box) {
        // reachable if its underside is within LEDGE_REACH of the head, or its top edge within LEDGE_TOP_REACH (a slab on the top half of the space has its underside further up)
        for (VoxelShape shape : level.getBlockCollisions(p, box.expandTowards(0, LEDGE_TOP_REACH, 0))) {
            AABB s = shape.bounds();
            if (s.minY - box.maxY <= LEDGE_REACH || s.maxY - box.maxY <= LEDGE_TOP_REACH) return true;
        }
        return false;
    }

    /**
     * The top of the wall being climbed when it ends just above the head with room to stand on it (nothing above the
     * wall): the Y to climb onto, or NaN. Used by the ledge jump on a wall's own top edge.
     */
    private static double wallTopAbove(Level level, Player p, AABB box, Direction dir) {
        double top = Double.NaN;
        AABB col = box.move(dir.getStepX() * 0.3, 0, dir.getStepZ() * 0.3).expandTowards(0, LEDGE_REACH + 1.0, 0);
        for (VoxelShape shape : level.getBlockCollisions(p, col)) top = Double.isNaN(top) ? shape.bounds().maxY : Math.max(top, shape.bounds().maxY);
        if (Double.isNaN(top) || top - box.maxY < 0.05 || top - box.maxY > LEDGE_REACH) return Double.NaN;
        AABB dest = box.move(dir.getStepX() * MANTLE_FORWARD, top - box.minY + 0.02, dir.getStepZ() * MANTLE_FORWARD);
        if (SurfaceSampler.blocked(level, p, dest) || !SurfaceSampler.blocked(level, p, dest.move(0, -0.1, 0))) return Double.NaN; // no room, or nothing to stand on
        return top;
    }

    private static boolean blockAbove(Level level, Player p, AABB box) {
        return SurfaceSampler.blocked(level, p, box.expandTowards(0, JUMP_MAX_RISE, 0)); // anything the old jump could reach
    }

    /**
     * How many blocks deep the overhang above is (1 to 3) when the player can jump out past its front edge and climb
     * onto it, else 0. One deep may be any thickness; two deep only one block thick (2x2 is not climbable).
     */
    private static int refuse(Level level, String why) {
        if (BotwClimb.DEBUG && level.isClientSide()) BotwClimb.LOGGER.info("ledge jump refused: {}", why);
        return 0;
    }

    private static int ledgeDepth(Level level, Player p, AABB box, Direction dir) {
        Direction out = dir.getOpposite();
        double b0 = Double.MAX_VALUE;
        for (VoxelShape shape : level.getBlockCollisions(p, box.expandTowards(0, LEDGE_TOP_REACH, 0))) b0 = Math.min(b0, shape.bounds().minY);
        // the next column out counts only if it has a block at the same height as this one (not something higher up)
        AABB band = new AABB(box.minX + 0.05, b0 + 0.02, box.minZ + 0.05, box.maxX - 0.05, b0 + 0.98, box.maxZ - 0.05);
        int depth = 1;
        while (depth < 4 && SurfaceSampler.blocked(level, p, band.move(out.getStepX() * depth, 0, out.getStepZ() * depth))) depth++;
        if (depth > 3) {
            StringBuilder cols = new StringBuilder();
            for (int n = 0; n <= 4; n++) cols.append(SurfaceSampler.blocked(level, p, band.move(out.getStepX() * n, 0, out.getStepZ() * n)) ? 'X' : '.');
            return refuse(level, "overhang 4+ deep, columns out from the player [" + cols + "] dir=" + dir + " pos=" + p.position() + " block bottom y=" + b0);
        }
        if (depth >= 2) {
            double bottom = Double.MAX_VALUE;
            java.util.List<AABB> stack = new java.util.ArrayList<>();
            // the climb-up is onto the farthest block, so it is that column that must be one thick: a block stacked on a nearer one is not in the way
            AABB far = box.move(out.getStepX() * (depth - 1), 0, out.getStepZ() * (depth - 1));
            for (VoxelShape shape : level.getBlockCollisions(p, far.expandTowards(0, JUMP_MAX_RISE, 0))) {
                bottom = Math.min(bottom, shape.bounds().minY);
                stack.add(shape.bounds());
            }
            double top = bottom;
            for (AABB b : stack) if (b.minY <= bottom + 1.01) top = Math.max(top, b.maxY); // only blocks stacked on it count, not something higher up
            if (top - bottom > 1.01) return refuse(level, depth + " deep but " + (top - bottom) + " thick");
            for (int n = 1; n < depth; n++) { // the dash out runs along under the overhang
                if (SurfaceSampler.blocked(level, p, box.move(out.getStepX() * (n + 0.05), 0, out.getStepZ() * (n + 0.05)))) return refuse(level, "no room to dash out under it");
            }
        }
        double dist = depth + 0.05;
        for (double h : new double[]{0, 1.0, 2.0}) {
            if (SurfaceSampler.blocked(level, p, box.move(out.getStepX() * dist, h, out.getStepZ() * dist))) return refuse(level, "depth " + depth + ": no room past the front edge at +" + h);
        }
        return depth;
    }

    // ------------------------------------------------------------------ climb jump

    /**
     * A jump (wall jump, ledge jump or long ledge jump) takes its cost, or whatever is left, as long as there is any stamina. Taking the
     * last of it leaves you exhausted. Tells the HUD what was taken so it can flash that arc. False = refused (exhausted).
     */
    private static boolean spendJump(ClimbData d, float cost) {
        if (d.exhausted || d.stamina <= 0) {
            d.staminaFlash = 20;
            return false;
        }
        d.jumpSpent = Stamina.take(d, cost);
        d.jumpSpentSeq++;
        if (!d.exhausted) d.staminaDelay = Math.max(d.staminaDelay, STAMINA_REGEN_DELAY * 2);
        return true;
    }

    private static void startJump(Player p, ClimbData d, int side, boolean sideOnly) {
        JumpPlan plan = JumpPlanner.plan(p.level(), p, p.position(), d.dir, side, sideOnly);
        if (plan == null) return; // A / D with nowhere to hop to: stay on the wall, do not jump up instead
        if (!spendJump(d, STAMINA_JUMP_COST)) return; // nothing left to jump with
        d.plan = plan;
        d.jumpSpeed = JUMP_BASE_SPEED * (d.climbSpeed ? ENCHANT_JUMP_MULT : 1); // wall jumps only; the ledge jumps never use it
        d.jumpStart = p.position();
        d.jumpTick = 0;
        d.setState(ClimbState.CLIMB_JUMP);
        if (BotwClimb.DEBUG && p.level().isClientSide()) debugPlan(p, d);
    }

    private static void stepJump(Player p, ClimbData d) {
        JumpPlan plan = d.plan;
        if (plan == null) {
            release(p, d, ATTACH_COOLDOWN, false);
            return;
        }
        int k = d.jumpTick++;
        double nominal = k * d.jumpSpeed; // jump time: everything (crouch, path, animation) runs on it, so a slower jump stretches all of it
        if (nominal < JUMP_WINDUP_TICKS) { // crouch against the wall, the animation loads the legs
            p.setDeltaMovement(Vec3.ZERO);
            return;
        }
        double idxF = nominal - JUMP_WINDUP_TICKS; // fractional position along the plan
        int idx = (int) idxF;
        if (idx < plan.length()) {
            // follow the planned waypoint; vanilla collision still has the last word
            if (BotwClimb.DEBUG && p.level().isClientSide()) BotwClimb.LOGGER.info("jump tick {} rel=({}, {}) plan=({}, {})", idx, fmt(p.getY() - d.jumpStart.y), fmt(p.getZ() - d.jumpStart.z), fmt(plan.steps()[idx].y), fmt(plan.steps()[idx].z));
            Vec3 at = plan.steps()[idx];
            if (idx + 1 < plan.length()) at = at.lerp(plan.steps()[idx + 1], idxF - idx);
            Vec3 v = d.jumpStart.add(at).subtract(p.position());
            double len = v.length();
            if (len > 1.0) v = v.scale(1.0 / len);
            p.setDeltaMovement(v);
            return;
        }
        // trajectory finished: grab whatever is there
        if (plan.around() != null) d.dir = plan.around(); // a hop round a pillar's corner: the next face is the wall now
        int extra = idx - plan.length();
        AABB box = p.getBoundingBox();
        // no ledge grab at the end of a wall jump: the old jump never puts you on a block's top (that is the block climb)
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
        d.ledgeFwd = 0;
        d.ledgeU = 0;
        d.ledgeOutNeed = 0;
        d.ledgeOut = 0;
        d.ledgeGrabbed = false;
        d.mantleTick = 0;
        d.ledgeSide = 0;
        d.mantleT = d.mantleTO = 0;
        d.ledgeHang = 0;
        d.plan = null;
        d.setState(ClimbState.LEDGE_TRANSITION);
    }

    private static final int LEDGE_BUFFER_TICKS = 10; // half a second
    private static final double LEDGE_OUT_SPEED = 0.22;
    private static final double LEDGE_LONG_SPEED = 0.24; // the long horizontal jump under a 2-deep overhang
    private static final double LEDGE_JUMP_UP = 0.5;
    private static final int LEDGE_HANG_TICKS = 5;
    private static final int MANTLE_TICKS = 22;
    private static final double LEDGE_LATERAL = 0.4; // blocks of sideways drift for a W+A / W+D jump, small enough to stay over the block
    /** From hanging (body 0.35 off the face) to standing 0.45 in over the top. shortcut: fixed for a full-depth block, a thinner shelf would need the measured depth. */
    private static final double MANTLE_FORWARD = 0.8;

    private static double smoothStep(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static void stepLedge(Player p, ClimbData d) {
        if (++d.ledgeTicks > LEDGE_MAX_TICKS) {
            release(p, d, ATTACH_COOLDOWN, true);
            return;
        }
        Direction dir = d.dir;
        double feet = p.getY();
        d.ledgeGapO = d.ledgeGap;
        d.ledgeGap = (float) (d.ledgeY - 1.375 - feet); // shoulder pivot height, for the pose
        if (d.ledgeTicks == 1) d.ledgeY0 = feet;
        if (d.ledgeOutNeed > 0 && !d.ledgeGrabbed) {
            // the jump: up and out together along an arc that ends with the hands on the block's top edge
            Direction out = dir.getOpposite();
            double headTop = p.getBoundingBox().maxY;
            boolean outDone = d.ledgeOut >= d.ledgeOutNeed;
            double hx = 0, hz = 0;
            if (!outDone) {
                double q = Mth.clamp(d.ledgeOut / d.ledgeOutNeed, 0, 1);
                double sp = d.ledgeOutSpeed * (d.ledgeOutNeed > 1.5 ? 0.45 + 0.9 * Math.sin(Math.PI * q) : 1); // the long dash: a heave off, fastest mid-way, easing into the catch
                d.ledgeOut += sp;
                hx = out.getStepX() * sp;
                hz = out.getStepZ() * sp;
            }
            double reachY = d.ledgeY - 0.3; // hanging by the hands: arms straight up, so the hands are right on the top edge and the head is a bit below it
            double rise = 0;
            if (headTop < reachY) {
                rise = LEDGE_JUMP_UP * Mth.clamp((reachY - headTop) * 0.5 + 0.2, 0.2, 1); // fast off the ground, easing as the hands near the edge
                if (!outDone) rise = Math.min(rise, Math.max(0, d.ledgeBlockMin - headTop - 0.05)); // never up into the block's underside
            }
            d.ledgeU = (float) (0.3 * Mth.clamp(1 - (reachY - headTop) / 3.0, 0, 1));
            if (outDone && headTop >= reachY - 0.02) {
                d.ledgeGrabbed = true;
                d.ledgeHang = LEDGE_HANG_TICKS;
            }
            if (d.ledgeSide != 0 && d.ledgeLat < d.ledgeLatTotal * 0.5) { // coming in diagonally: the first half of the sideways travel is in the jump
                Direction lat = d.ledgeSide > 0 ? dir.getCounterClockWise() : dir.getClockWise();
                double dl = Math.min(0.1, d.ledgeLatTotal * 0.5 - d.ledgeLat);
                d.ledgeLat += dl;
                hx += lat.getStepX() * dl;
                hz += lat.getStepZ() * dl;
            }
            p.setDeltaMovement(hx, rise, hz);
            return;
        }
        if (d.ledgeOutNeed > 0 && d.ledgeHang > 0) { // hanging from the edge for a beat before pulling up
            d.ledgeHang--;
            d.ledgeU = 0.35f;
            p.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (d.ledgeOutNeed > 0) { // the climb-up: one progress value drives both this movement and the pose
            if (d.mantleTick == 0) {
                d.mantleStart = p.position();
                d.jumpSide = -d.jumpSide; // the lead leg alternates
            }
            d.mantleTick++;
            d.mantleTO = d.mantleT;
            d.mantleT = Math.min(1f, d.mantleTick / (float) MANTLE_TICKS);
            float t = d.mantleT;
            // up: strong while pulling, easing once the chest is over the edge. Forward: starts late, overlaps the rise,
            // so the path curves round the corner (the block itself holds the body back until the feet clear the edge).
            // a kick, not a glide: sag a little to load, then a hard burst up (most of the height), then ease in over the top
            // it has weight: a deep draw-in (~0.4 blocks), a fast thrust that overshoots the top by ~0.3, then it settles onto it
            double load = -0.19 * Math.sin(Math.PI * Mth.clamp(t / 0.30f, 0, 1));
            double xb = Mth.clamp((t - 0.26f) / 0.34f, 0, 1);
            double fy = load + 1.14 * (1 - Math.pow(1 - xb, 3)) - 0.14 * smoothStep((t - 0.55f) / 0.35f);
            fy += 0.03 * Math.sin(t * 17) * Math.sin(Math.PI * t); // pulls come in uneven heaves, not one smooth glide
            double ff = smoothStep((t - 0.45f) / 0.5f);
            double endY = d.ledgeY + 0.05;
            Direction lat = d.ledgeSide > 0 ? dir.getCounterClockWise() : dir.getClockWise();
            double sl = d.ledgeSide == 0 ? 0 : d.ledgeLatTotal * 0.5 * smoothStep((t - 0.05f) / 0.75f); // the rest of it, along with the climb
            Vec3 want = d.mantleStart.add(dir.getStepX() * MANTLE_FORWARD * ff + lat.getStepX() * sl, (endY - d.mantleStart.y) * fy, dir.getStepZ() * MANTLE_FORWARD * ff + lat.getStepZ() * sl);
            p.setDeltaMovement(want.subtract(p.position()));
            double fwdDone = (p.getX() - d.mantleStart.x) * dir.getStepX() + (p.getZ() - d.mantleStart.z) * dir.getStepZ();
            if (t >= 1f && ((feet >= d.ledgeY - 0.02 && fwdDone >= MANTLE_FORWARD - 0.08) || d.mantleTick > MANTLE_TICKS + 8)) {
                release(p, d, ATTACH_COOLDOWN, true);
            }
            return;
        }
        if (feet < d.ledgeY - 0.005) {
            d.ledgeU = (float) (0.55 * Mth.clamp((feet - d.ledgeY0) / Math.max(0.05, d.ledgeY - d.ledgeY0), 0, 1));
            double rise = Math.min(LEDGE_RISE_SPEED, d.ledgeY - feet + 0.02);
            p.setDeltaMovement(dir.getStepX() * 0.03, rise, dir.getStepZ() * 0.03);
        } else {
            d.ledgeU = 0.55f + 0.45f * Math.min(1f, ++d.ledgeFwd / 6f);
            p.setDeltaMovement(dir.getStepX() * LEDGE_FORWARD_SPEED, -0.04, dir.getStepZ() * LEDGE_FORWARD_SPEED);
            if (d.ledgeFwd >= 6) { // pulled up: stand on the top
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
