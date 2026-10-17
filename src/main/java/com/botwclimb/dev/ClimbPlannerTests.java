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
        ClimbController.stepLocal(p, d, fwd, str, jump, climbKey, sneak);
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
        d.attachCooldown = 0;
        grab(p, d);
        if (d.state != ClimbState.CLIMBING) h.fail("could not regrab");
        tick(p, d, 0, 0, false, true, false);
        if (d.state != ClimbState.NONE) h.fail("sneak must release");
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

    @GameTest(template = "empty")
    public static void sidewaysJumpDriftsAlongWall(GameTestHelper h) {
        wall(h, 14);
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        Vec3 start = h.absoluteVec(new Vec3(5.5, 4.0, FLUSH_Z));
        JumpPlan left = JumpPlanner.plan(h.getLevel(), p, start, Direction.SOUTH, 1);
        JumpPlan right = JumpPlanner.plan(h.getLevel(), p, start, Direction.SOUTH, -1);
        JumpPlan leap = JumpPlanner.plan(h.getLevel(), p, start, Direction.SOUTH, 1, true);
        if (leap.end().x < 2.0 || Math.abs(leap.end().y) > 0.1) h.fail("A alone should slide sideways at the same height: " + leap.end());
        for (Vec3 st : leap.steps()) if (st.y > 0.2) h.fail("A alone must not rise above 0.2: " + st);
        // facing south, the player's left is +x (east)
        if (left.end().x < 1.0 || right.end().x > -1.0) h.fail("A/D jump should drift ~1.5 blocks: " + left.end() + " " + right.end());
        if (left.end().y < 2.0) h.fail("drifting jump must still rise");
        // a wall that ends to the left: no sideways drift into thin air
        for (int y = 1; y <= 14; y++) for (int x = 7; x < 12; x++) h.setBlock(x, y, 8, Blocks.AIR);
        JumpPlan blocked = JumpPlanner.plan(h.getLevel(), p, start, Direction.SOUTH, 1);
        System.out.printf("[botwclimb-test] side jumps: L=%s R=%s edge-limited=%s%n", left.end(), right.end(), blocked.end());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fabricTexturesExist(GameTestHelper h) throws java.io.IOException {
        for (var f : com.botwclimb.common.paraglider.FabricRegistry.all()) {
            try (var in = ClimbPlannerTests.class.getResourceAsStream("/assets/botwclimb/textures/entity/paraglider/" + f.id() + ".png")) {
                if (in == null) h.fail("missing texture for fabric " + f.id());
                byte[] head = in.readNBytes(24);
                int w = java.nio.ByteBuffer.wrap(head, 16, 4).getInt(), hh = java.nio.ByteBuffer.wrap(head, 20, 4).getInt();
                if (w != 128 || hh != 64) h.fail(f.id() + " must be 128x64, is " + w + "x" + hh);
            }
        }
        if (com.botwclimb.common.paraglider.FabricRegistry.indexOf("zonai") < 0) h.fail("zonai registered");
        h.succeed();
    }

    /** Grabbing from the ground: a crouch, then a real hop up onto the wall. */
    @GameTest(template = "empty")
    public static void latchIsAHop(GameTestHelper h) {
        wall(h, 14);
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        double y0 = p.getY();
        grab(p, d);
        if (d.state != ClimbState.CLIMBING) h.fail("did not latch on, state " + d.state);
        if (p.getY() - y0 < 0.6) h.fail("latch hop too small, rise " + (p.getY() - y0));
        h.succeed();
    }

    /** A block directly above the head: Space jumps out past it and grabs its top edge, then you stand up on it. */
    @GameTest(template = "empty")
    public static void jumpOntoBlockAbove(GameTestHelper h) {
        wall(h, 14);
        h.setBlock(5, 9, 7, Blocks.STONE);
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        grab(p, d);
        for (int i = 0; i < 60 && d.state == ClimbState.CLIMBING && p.getY() < 8.9; i++) tick(p, d, 1, 0, false, false, false);
        double top = h.absoluteVec(new Vec3(0, 10.0, 0)).y;
        tick(p, d, 1, 0, true, false, false); // Space
        if (d.state != ClimbState.LEDGE_TRANSITION) h.fail("Space under the block did not start the jump onto it, state " + d.state);
        double best = p.getY();
        for (int i = 0; i < 60; i++) {
            tick(p, d, 0, 0, false, false, false);
            best = Math.max(best, p.getY());
            if (d.state == ClimbState.NONE && best < top) h.fail("dropped at tick " + i + " y=" + p.getY() + " best=" + best);
        }
        if (best <= top) h.fail("did not get up onto the block, best y=" + best);
        double faceZ = h.absoluteVec(new Vec3(0, 0, 7)).z;
        if (p.getZ() < faceZ + 0.3) h.fail("did not end up standing over the block, z offset " + (p.getZ() - faceZ));
        if (p.getY() < top - 0.02) h.fail("ended below the top, y=" + p.getY());
        h.succeed();
    }

    /** W+A with the block up and to the left: the whole jump goes diagonally and ends standing on that block. */
    @GameTest(template = "empty")
    public static void diagonalJumpOntoBlock(GameTestHelper h) {
        wall(h, 14);
        h.setBlock(6, 9, 7, Blocks.STONE); // one column to the left (+x when facing south)
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        grab(p, d);
        for (int i = 0; i < 60 && d.state == ClimbState.CLIMBING && p.getY() < h.absoluteVec(new Vec3(0, 6.6, 0)).y; i++) tick(p, d, 1, 0, false, false, false);
        double top = h.absoluteVec(new Vec3(0, 10.0, 0)).y;
        double x0 = p.getX();
        tick(p, d, 1, 1, true, false, false);
        if (d.state != ClimbState.LEDGE_TRANSITION) h.fail("W+A+Space did not start the diagonal jump, state " + d.state);
        for (int i = 0; i < 60; i++) tick(p, d, 0, 0, false, false, false);
        if (p.getY() < top - 0.02) h.fail("did not end on top, y=" + p.getY());
        if (p.getX() - x0 < 0.8) h.fail("did not travel sideways, dx=" + (p.getX() - x0));
        h.succeed();
    }

    /** A 2-deep, 1-thick overhang: a long jump out past its far edge, then up onto it. */
    @GameTest(template = "empty")
    public static void longJumpOntoTwoDeepOverhang(GameTestHelper h) {
        wall(h, 14);
        h.setBlock(5, 9, 7, Blocks.STONE);
        h.setBlock(5, 9, 6, Blocks.STONE);
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        grab(p, d);
        for (int i = 0; i < 60 && d.state == ClimbState.CLIMBING && p.getY() < h.absoluteVec(new Vec3(0, 6.6, 0)).y; i++) tick(p, d, 1, 0, false, false, false);
        double top = h.absoluteVec(new Vec3(0, 10.0, 0)).y;
        tick(p, d, 1, 0, true, false, false);
        if (d.state != ClimbState.LEDGE_TRANSITION) h.fail("Space under a 2-deep overhang did not start the long jump, state " + d.state);
        for (int i = 0; i < 80; i++) tick(p, d, 0, 0, false, false, false);
        double faceZ = h.absoluteVec(new Vec3(0, 0, 6)).z; // front face of the far block
        if (p.getY() < top - 0.02) h.fail("did not end on top, y=" + p.getY());
        if (p.getZ() < faceZ + 0.3 || p.getZ() > faceZ + 1.0) h.fail("did not end on the far block, z offset " + (p.getZ() - faceZ));
        h.succeed();
    }

    /** 2 deep and 2 thick is not climbable: no jump at all. */
    @GameTest(template = "empty")
    public static void twoByTwoOverhangIsNotClimbable(GameTestHelper h) {
        wall(h, 14);
        for (int z = 6; z <= 7; z++) for (int y = 9; y <= 10; y++) h.setBlock(5, y, z, Blocks.STONE);
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        grab(p, d);
        for (int i = 0; i < 60 && d.state == ClimbState.CLIMBING && p.getY() < h.absoluteVec(new Vec3(0, 6.6, 0)).y; i++) tick(p, d, 1, 0, false, false, false);
        tick(p, d, 1, 0, true, false, false);
        if (d.state == ClimbState.LEDGE_TRANSITION) h.fail("a 2x2 overhang must not start the ledge jump");
        h.succeed();
    }

    /** Space well below a block: too far for the ledge jump, so it is the normal wall jump. */
    @GameTest(template = "empty")
    public static void spaceBelowBlockIsBlockClimb(GameTestHelper h) {
        wall(h, 14);
        h.setBlock(5, 9, 7, Blocks.STONE);
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        grab(p, d);
        tick(p, d, 0, 0, true, false, false);
        if (d.state == ClimbState.LEDGE_TRANSITION) h.fail("block is out of the ledge jump's reach, it should not start");
        if (d.state != ClimbState.CLIMB_JUMP) h.fail("an overhang far above must not stop the normal wall jump, state " + d.state);
        h.succeed();
    }

    /** Plain Space next to a wall with a block over you, not attached yet: the block climb, not the normal jump. */
    @GameTest(template = "empty")
    public static void spaceFromGroundUnderBlockClimbs(GameTestHelper h) {
        wall(h, 14);
        h.setBlock(5, 9, 7, Blocks.STONE);
        Player p = sim(h, 6.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        for (int i = 0; i < 3; i++) tick(p, d, 0, 0, i == 0, false, false);
        if (d.state != ClimbState.LEDGE_TRANSITION) h.fail("Space next to the wall under a block did not climb it, state " + d.state);
        h.succeed();
    }

    /** A block high overhead is not a reason to start climbing: plain Space next to a wall is just a jump. */
    @GameTest(template = "empty")
    public static void spaceFarBelowOverhangDoesNotClimb(GameTestHelper h) {
        wall(h, 14);
        h.setBlock(5, 9, 7, Blocks.STONE);
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        for (int i = 0; i < 3; i++) tick(p, d, 0, 0, i == 0, false, false);
        if (d.state != ClimbState.NONE) h.fail("Space on the ground started a climb, state " + d.state);
        h.succeed();
    }

    /** The climb key contains Space. Holding it past the grab must not count as a jump press. */
    @GameTest(template = "empty")
    public static void heldSpaceAfterGrabIsNotAJump(GameTestHelper h) {
        wall(h, 14);
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        climbKey = true;
        for (int i = 0; i < 12; i++) tick(p, d, 0, 0, true, false, false); // Space held the whole time, as part of the combo
        climbKey = false;
        if (d.state != ClimbState.CLIMBING) h.fail("did not attach, state " + d.state);
        for (int i = 0; i < 10; i++) {
            tick(p, d, 1, 0, true, false, false); // still holding Space after the grab
            if (d.state == ClimbState.CLIMB_JUMP || d.state == ClimbState.LEDGE_TRANSITION) {
                h.fail("the held Space fired a jump right after the grab, state " + d.state);
            }
        }
        h.succeed();
    }
}
