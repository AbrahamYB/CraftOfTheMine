package com.botwclimb.dev;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.climb.ClimbController;
import com.botwclimb.common.paraglider.InputChord;
import com.botwclimb.common.paraglider.ParagliderConstants;
import com.botwclimb.common.paraglider.ParagliderController;
import com.botwclimb.common.paraglider.ParagliderData;
import com.botwclimb.common.climb.ClimbData;
import com.botwclimb.common.climb.ClimbState;
import com.botwclimb.common.climb.JumpPlan;
import com.botwclimb.common.climb.JumpPlanner;
import com.botwclimb.common.climb.SurfaceSampler;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Dev-only headless checks of the jump planner against real world collision (run: gradlew runGameTestServer).
 * Wall plane: z = 8, wall blocks at z = 8, the player hangs on the -z side of it facing SOUTH.
 */
@GameTestHolder(BotwClimb.MOD_ID)
@PrefixGameTestTemplate(false)
public class ClimbPlannerTests {
    private static final double FLUSH_Z = 8 - 0.3 - 0.02;

    private static void wall(GameTestHelper h, int topY) {
        for (int x = 0; x < 12; x++) for (int y = 1; y <= topY; y++) h.setBlock(x, y, 8, Blocks.STONE);
    }

    private static void box(GameTestHelper h, int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++)
            h.setBlock(x, y, z, Blocks.STONE);
    }

    private record Result(JumpPlan plan, Vec3 start, Vec3 end) {}

    private static Result plan(GameTestHelper h, double feetY) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        Vec3 start = h.absoluteVec(new Vec3(5.5, feetY, FLUSH_Z));
        JumpPlan plan = JumpPlanner.plan(h.getLevel(), p, start, Direction.SOUTH);
        AABB b0 = p.getDimensions(Pose.STANDING).makeBoundingBox(start);
        for (Vec3 s : plan.steps()) {
            if (SurfaceSampler.blocked(h.getLevel(), p, b0.move(s).deflate(0.01))) {
                h.fail("plan clips into a block at " + s);
            }
        }
        Vec3 end = plan.end();
        BotwClimb_log(plan, end);
        return new Result(plan, start, end);
    }

    private static void BotwClimb_log(JumpPlan plan, Vec3 end) {
        System.out.printf("[botwclimb-test] goal=%s ticks=%d end=(%.2f, %.2f, %.2f) ledgeY=%.2f%n",
                plan.goal(), plan.length(), end.x, end.y, end.z, plan.ledgeY());
    }

    @GameTest(template = "empty")
    public static void openWall(GameTestHelper h) {
        wall(h, 14);
        Result r = plan(h, 4.0);
        if (r.plan.goal() != JumpPlan.Goal.WALL || r.end.y < 2.5 || Math.abs(r.end.z) > 0.3) {
            h.fail("open wall jump should rise >= 2.5 and stay on the wall");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oneBlockLip(GameTestHelper h) {
        wall(h, 14);
        h.setBlock(5, 7, 7, Blocks.STONE); // the lip, one block out from the wall
        Result r = plan(h, 5.2); // head right under the lip
        // the feet must end above the lip top (y = 8) and back at the wall
        if (r.plan.goal() != JumpPlan.Goal.WALL || r.start.y + r.end.y < h.absoluteVec(new Vec3(0, 8.0, 0)).y
                || Math.abs(r.end.z) > 0.3) {
            h.fail("one-block lip must be climb-jumpable");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lipWithWallTop(GameTestHelper h) {
        wall(h, 7); // wall ends in the lip row
        h.setBlock(5, 7, 7, Blocks.STONE);
        Result r = plan(h, 5.2);
        if (r.plan.goal() != JumpPlan.Goal.LEDGE) h.fail("jump should end at a ledge on top of the lip");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lipTooFarBelow(GameTestHelper h) {
        wall(h, 14);
        h.setBlock(5, 7, 7, Blocks.STONE);
        Result r = plan(h, 3.0); // far below: the jump rises until the lip stops it, then re-grabs under it
        if (r.start.y + r.end.y + 1.8 > h.absoluteVec(new Vec3(0, 7.0, 0)).y + 0.01) h.fail("must not cross a lip that is out of reach");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void threeBlockObstruction(GameTestHelper h) {
        wall(h, 14);
        box(h, 4, 6, 7, 9, 5, 7); // 3 thick, 3 tall, 3 wide
        Result r = plan(h, 5.2);
        if (r.plan.goal() != JumpPlan.Goal.NONE) h.fail("3-block obstruction must not be crossable");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void twoBlockDeepLip(GameTestHelper h) {
        wall(h, 14);
        box(h, 4, 6, 7, 7, 6, 7); // 2 blocks out
        Result r = plan(h, 5.2);
        if (r.plan.goal() != JumpPlan.Goal.NONE) h.fail("2-block deep overhang must not be crossable");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lowCeiling(GameTestHelper h) {
        wall(h, 14);
        box(h, 0, 11, 7, 7, 0, 7); // huge roof directly above the head
        Result r = plan(h, 5.2);
        if (r.plan.goal() != JumpPlan.Goal.NONE || r.end.y > 0.05) h.fail("no room: the jump must not move into the roof");
        h.succeed();
    }

    // ---------------- full controller simulation (no screen needed) ----------------

    private static Player sim(GameTestHelper h, double feetY, boolean wall14) {
        if (wall14) wall(h, 14);
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        Vec3 pos = h.absoluteVec(new Vec3(5.5, feetY, FLUSH_Z));
        p.setPos(pos);
        p.setYRot(0); // facing south, towards the wall
        p.setNoGravity(true);
        return p;
    }

    private static boolean climbKey;

    private static void tick(Player p, ClimbData d, float fwd, float str, boolean jump, boolean sneak, boolean sprint) {
        ClimbController.stepLocal(p, d, fwd, str, jump, climbKey, sneak, sprint);
        if (d.active()) p.move(MoverType.SELF, p.getDeltaMovement()); else p.setDeltaMovement(Vec3.ZERO);
    }

    /** Holds the climb key next to the wall until the climb starts, then releases it. */
    private static void grab(Player p, ClimbData d) {
        climbKey = true;
        for (int i = 0; i < 13; i++) tick(p, d, 0, 0, false, false, false);
        climbKey = false;
        tick(p, d, 0, 0, false, false, false);
    }

    @GameTest(template = "empty")
    public static void simTapJumpDoesNotClimb(GameTestHelper h) {
        Player p = sim(h, 4.0, true);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        climbKey = false;
        for (int i = 0; i < 40; i++) tick(p, d, 1, 0, i < 3 || i > 20, false, false); // walk into the wall, tap and hold Space
        if (d.state != ClimbState.NONE) h.fail("Space and walking into the wall must not start climbing");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void simClimbAndRelease(GameTestHelper h) {
        Player p = sim(h, 4.0, true);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        double y0 = p.getY();
        grab(p, d);
        for (int i = 0; i < 30; i++) tick(p, d, 1, 0, false, false, false);
        if (d.state != ClimbState.CLIMBING) h.fail("did not attach/climb, state " + d.state);
        if (p.getY() - y0 < 2.5) h.fail("climbed too little: " + (p.getY() - y0));
        for (int i = 0; i < 10; i++) tick(p, d, 0, 1, false, false, false);
        if (d.state != ClimbState.CLIMBING) h.fail("sideways broke climbing");
        tick(p, d, 0, 0, false, false, true);
        if (d.state != ClimbState.NONE) h.fail("sprint must release");
        d.attachCooldown = 0;
        grab(p, d);
        if (d.state != ClimbState.CLIMBING) h.fail("could not regrab");
        tick(p, d, 0, 0, false, true, false);
        if (d.state != ClimbState.NONE) h.fail("sneak must release");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void simLipStopAndJump(GameTestHelper h) {
        wall(h, 14);
        h.setBlock(5, 9, 7, Blocks.STONE); // lip, underside at y = 9
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        grab(p, d);
        for (int i = 0; i < 60; i++) tick(p, d, 1, 0, false, false, false);
        double under = h.absoluteVec(new Vec3(0, 9, 0)).y;
        if (p.getY() + 1.8 > under + 0.001) h.fail("climbed into the lip");
        if (d.state != ClimbState.CLIMBING) h.fail("should still hang under the lip, state " + d.state);
        // jump: press, release, run until it finishes
        tick(p, d, 0, 0, true, false, false);
        for (int i = 0; i < 40 && d.state != ClimbState.CLIMBING || i < 3; i++) {
            tick(p, d, 0, 0, false, false, false);
            if (d.state == ClimbState.NONE) h.fail("fell off during lip jump at tick " + i);
        }
        double top = h.absoluteVec(new Vec3(0, 10, 0)).y;
        if (p.getY() < top - 0.01) h.fail("did not get above the lip, y=" + p.getY() + " need " + top);
        System.out.println("[botwclimb-test] lip jump ended state=" + d.state + " y=" + p.getY());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void simBigObstructionFalls(GameTestHelper h) {
        wall(h, 14);
        box(h, 4, 6, 9, 11, 5, 7);
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        grab(p, d);
        for (int i = 0; i < 60; i++) tick(p, d, 1, 0, false, false, false);
        double under = h.absoluteVec(new Vec3(0, 9, 0)).y;
        tick(p, d, 0, 0, true, false, false);
        for (int i = 0; i < 30; i++) {
            tick(p, d, 0, 0, false, false, false);
            if (p.getY() + 1.8 > under + 0.001) h.fail("passed into the obstruction");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void simHoldSpaceOneJump(GameTestHelper h) {
        Player p = sim(h, 3.0, true);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        grab(p, d);
        for (int i = 0; i < 5; i++) tick(p, d, 1, 0, false, false, false);
        int jumps = 0; ClimbState prev = d.state;
        for (int i = 0; i < 12; i++) {
            tick(p, d, 0, 0, true, false, false);
            if (d.state == ClimbState.CLIMB_JUMP && prev != ClimbState.CLIMB_JUMP) jumps++;
            prev = d.state;
        }
        if (jumps != 1) h.fail("held Space produced " + jumps + " jumps");
        h.succeed();
    }

    // ---------------- paraglider (movement and the ordered chord) ----------------

    @GameTest(template = "empty")
    public static void gliderChordIsOrdered(GameTestHelper h) {
        InputChord c = new InputChord();
        c.onPress(false);                                   // Space first, Shift afterwards
        if (c.takePending()) h.fail("Space then Shift must not count as the chord");
        c.onRelease();
        c.onPress(true);                                    // Shift already down when Space goes down
        if (!c.takePending()) h.fail("Shift then Space must count");
        if (c.takePending()) h.fail("one press must give one toggle");
        c.consume();
        if (!c.consumed()) h.fail("press should be consumed");
        c.clearIfKeyUp(true);
        if (!c.consumed()) h.fail("consumption must last while Space is still held");
        c.onRelease();
        if (c.consumed()) h.fail("releasing Space must give Space back immediately");
        // hold timing: 8 ticks, once, and a short tap never fires
        c.onPress(true);
        c.takePending();
        c.begin();
        boolean fired = false;
        for (int i = 0; i < 7; i++) fired |= c.tickHold(true, 8);
        if (fired) h.fail("must not fire before 8 ticks");
        if (!c.tickHold(true, 8)) h.fail("must fire on the 8th tick");
        if (c.tickHold(true, 8)) h.fail("must fire only once per hold");
        if (!c.consumed()) h.fail("press stays consumed until release even after firing");
        c.onRelease();
        c.begin();
        c.tickHold(true, 8);
        if (c.tickHold(false, 8) || c.holding()) h.fail("letting go early cancels the hold");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void gliderRules(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(5.5, 8, 5.5)));
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ELYTRA));
        if (p.isFallFlying()) h.fail("setup: not fall flying");
        if (p.onGround() || !ParagliderController.canDeploy(p)) h.fail("airborne player wearing an Elytra may deploy");
        if (p.isFallFlying()) h.fail("deploy check must not start the Elytra");
        p.getData(BotwClimb.CLIMB).state = ClimbState.CLIMBING;
        if (ParagliderController.canDeploy(p)) h.fail("no paraglider while climbing");
        p.getData(BotwClimb.CLIMB).state = ClimbState.CLIMB_JUMP;
        if (ParagliderController.canDeploy(p)) h.fail("no paraglider during a climb jump");
        p.getData(BotwClimb.CLIMB).state = ClimbState.NONE;
        p.startFallFlying();
        if (ParagliderController.canDeploy(p)) h.fail("a plain deploy is not for Elytra flight");
        if (!ParagliderController.canSwitchFromElytra(p)) h.fail("Elytra flight can switch to the glider");
        p.stopFallFlying();
        if (p.isFallFlying()) h.fail("stopFallFlying must end the Elytra state");
        if (ParagliderController.canSwitchFromElytra(p)) h.fail("nothing to switch from once the Elytra stopped");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void gliderMovement(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(5.5, 60, 5.5)));
        p.setYRot(0);
        ParagliderData g = p.getData(BotwClimb.PARAGLIDER);
        g.gliding = true;
        g.fresh = true;
        p.setDeltaMovement(0, -2.5, 0);
        double last = -2.5, peak = -9;
        for (int i = 0; i < 8; i++) {
            ParagliderController.step(p, g, 0, 0);
            double vy = p.getDeltaMovement().y;
            if (vy < last - 1e-9) h.fail("fall must only slow down, vy " + vy);
            last = vy;
            p.move(MoverType.SELF, p.getDeltaMovement());
            if (i == 5 && Math.abs(vy - ParagliderConstants.DESCENT_SPEED) > 0.02) h.fail("not caught within 6 ticks: " + vy);
        }
        for (int i = 0; i < 5; i++) {
            ParagliderController.step(p, g, 1, 0);
            p.move(MoverType.SELF, p.getDeltaMovement());
        }
        double early = Math.hypot(p.getDeltaMovement().x, p.getDeltaMovement().z);
        if (early > 0.17 || early <= 0) h.fail("must accelerate smoothly, speed after 5 ticks " + early);
        for (int i = 0; i < 25; i++) {
            ParagliderController.step(p, g, 1, 0);
            p.move(MoverType.SELF, p.getDeltaMovement());
        }
        double top = Math.hypot(p.getDeltaMovement().x, p.getDeltaMovement().z);
        if (Math.abs(top - ParagliderConstants.MAX_HORIZONTAL_SPEED) > 0.01) h.fail("cruise speed " + top);
        if (p.getDeltaMovement().z <= 0) h.fail("yaw 0 + W should glide towards +z");
        if (Math.abs(p.getDeltaMovement().y - ParagliderConstants.DESCENT_SPEED) > 0.01) h.fail("keeps descending");
        for (int i = 0; i < 6; i++) {
            ParagliderController.step(p, g, 0, 0);
            p.move(MoverType.SELF, p.getDeltaMovement());
        }
        double mid = Math.hypot(p.getDeltaMovement().x, p.getDeltaMovement().z);
        if (mid >= top || mid <= 0.05) h.fail("momentum should decay gradually, got " + mid);
        System.out.printf("[botwclimb-test] glide early=%.3f top=%.3f afterRelease=%.3f%n", early, top, mid);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void gliderKeepsElytraMomentum(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(5.5, 60, 5.5)));
        ParagliderData g = p.getData(BotwClimb.PARAGLIDER);
        g.gliding = true;
        g.fresh = true;
        p.setDeltaMovement(0, 0.6, 1.2); // rocket-boosted Elytra: 1.2 b/tick forward, climbing
        ParagliderController.step(p, g, 0, 0);
        if (Math.abs(p.getDeltaMovement().z - 1.2) > 0.06) h.fail("first glide tick must keep the Elytra speed: " + p.getDeltaMovement());
        double prevZ = p.getDeltaMovement().z, prevY = p.getDeltaMovement().y;
        for (int i = 0; i < 12; i++) {
            p.move(MoverType.SELF, p.getDeltaMovement());
            ParagliderController.step(p, g, 1, 0);
            if (p.getDeltaMovement().z > prevZ + 1e-9) h.fail("speed must only bleed off");
            if (p.getDeltaMovement().y > prevY + 1e-9) h.fail("rise must only fade");
            prevZ = p.getDeltaMovement().z;
            prevY = p.getDeltaMovement().y;
        }
        if (prevZ < 0.6) h.fail("momentum drained too quickly: " + prevZ);
        for (int i = 0; i < 120; i++) {
            p.move(MoverType.SELF, p.getDeltaMovement());
            ParagliderController.step(p, g, 1, 0);
        }
        if (Math.abs(Math.hypot(p.getDeltaMovement().x, p.getDeltaMovement().z) - ParagliderConstants.MAX_HORIZONTAL_SPEED) > 0.01)
            h.fail("must settle on the cruise speed");
        h.succeed();
    }
}
