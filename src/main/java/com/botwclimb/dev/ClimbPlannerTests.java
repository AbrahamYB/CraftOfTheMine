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
import com.botwclimb.common.climb.ServerClimb;
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

    /** A / D with Space on a 1x1 pillar hops round the corner onto the next face; on a wide wall it stays a hop along the wall. */
    @GameTest(template = "empty")
    public static void pillarHopRoundsTheCorner(GameTestHelper h) {
        for (int y = 1; y <= 10; y++) h.setBlock(5, y, 8, Blocks.STONE);
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        Vec3 start = h.absoluteVec(new Vec3(5.5, 4.0, FLUSH_Z));
        JumpPlan left = JumpPlanner.plan(h.getLevel(), p, start, Direction.SOUTH, 1, true);
        JumpPlan right = JumpPlanner.plan(h.getLevel(), p, start, Direction.SOUTH, -1, true);
        if (left == null || left.around() != Direction.WEST) h.fail("A on a pillar should end holding its east face (wall to the west): " + (left == null ? null : left.around()));
        if (right == null || right.around() != Direction.EAST) h.fail("D on a pillar should end holding its west face (wall to the east): " + (right == null ? null : right.around()));
        AABB b0 = p.getDimensions(Pose.STANDING).makeBoundingBox(start);
        AABB end = b0.move(left.end());
        if (!SurfaceSampler.sample(h.getLevel(), p, end, Direction.WEST).holds(com.botwclimb.common.climb.ClimbConstants.REGRAB_GAP)) h.fail("the hop must end on the east face");
        for (int y = 1; y <= 10; y++) for (int x = 0; x < 12; x++) h.setBlock(x, y, 8, Blocks.STONE); // now a wide wall
        JumpPlan along = JumpPlanner.plan(h.getLevel(), p, start, Direction.SOUTH, 1, true);
        if (along == null || along.around() != null) h.fail("a wide wall is hopped along, not round");
        h.succeed();
    }

    /** Every fabric after the first has a recipe; the first needs none and is always known; unlock bits behave. */
    @GameTest(template = "empty")
    public static void everyFabricHasARecipe(GameTestHelper h) {
        var fabrics = com.botwclimb.common.paraglider.FabricRegistry.all();
        if (!fabrics.get(0).recipe().isEmpty()) h.fail("the first fabric is free");
        for (int i = 1; i < fabrics.size(); i++) {
            if (fabrics.get(i).recipe().size() < 5) h.fail("the recipe of " + fabrics.get(i).id() + " needs wool, string and something of its own");
        }
        if (!com.botwclimb.common.paraglider.FabricUnlocks.has(0, 0)) h.fail("the first fabric is always known");
        if (com.botwclimb.common.paraglider.FabricUnlocks.has(0, 3)) h.fail("nothing else is known at the start");
        if (!com.botwclimb.common.paraglider.FabricUnlocks.has(1 << 3, 3) || com.botwclimb.common.paraglider.FabricUnlocks.has(1 << 3, 4)) h.fail("one bit per fabric");
        h.succeed();
    }

    /** The craft check wants every ingredient of the recipe in the inventory, one item each, and names the stacks to take from. */
    @GameTest(template = "empty")
    public static void clothCraftPlanNeedsEverything(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        var ingredients = com.botwclimb.common.paraglider.FabricRegistry.byIndex(com.botwclimb.common.paraglider.FabricRegistry.indexOf("korok")).recipe();
        var inv = p.getInventory();
        inv.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHITE_WOOL, 2));
        inv.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STRING, 2));
        inv.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GREEN_DYE, 1));
        if (com.botwclimb.common.paraglider.ClothCrafting.plan(inv, ingredients) != null) h.fail("no plan while the sapling is missing");
        inv.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_SAPLING, 1));
        int[] slots = com.botwclimb.common.paraglider.ClothCrafting.plan(inv, ingredients);
        if (slots == null || slots.length != ingredients.size()) h.fail("a full inventory should give a plan");
        for (int k = 0; k < slots.length; k++) if (!com.botwclimb.common.Compat.items(inv).get(slots[k]).is(ingredients.get(k))) h.fail("each slot must hold its ingredient");
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
        StringBuilder trace = new StringBuilder("after grab y=" + (p.getY() - h.absoluteVec(Vec3.ZERO).y) + " " + d.state);
        int steps = 0;
        for (int i = 0; i < 60 && d.state == ClimbState.CLIMBING && p.getY() < h.absoluteVec(new Vec3(0, 8.9, 0)).y; i++) {
            tick(p, d, 1, 0, false, false, false);
            steps++;
            if (i % 10 == 0) trace.append(" | ").append(i).append(": y=").append(p.getY() - h.absoluteVec(Vec3.ZERO).y).append(" ").append(d.state).append(" stam=").append(d.stamina);
        }
        trace.append(" steps=").append(steps);
        double top = h.absoluteVec(new Vec3(0, 10.0, 0)).y;
        tick(p, d, 1, 0, true, false, false); // Space
        if (d.state != ClimbState.LEDGE_TRANSITION) h.fail("Space under the block did not start the jump onto it, state " + d.state + " " + trace + " goal=" + (d.plan == null ? "none" : d.plan.goal()));
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

    /** A block stacked on the NEAR block of a 2-deep overhang is not in the way: the long jump climbs onto the far block. */
    @GameTest(template = "empty")
    public static void longJumpWithBlockStackedOnTheNearOne(GameTestHelper h) {
        wall(h, 14);
        h.setBlock(5, 9, 7, Blocks.STONE);
        h.setBlock(5, 9, 6, Blocks.STONE);
        h.setBlock(5, 10, 7, Blocks.STONE); // on top of the near block only
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        grab(p, d);
        for (int i = 0; i < 60 && d.state == ClimbState.CLIMBING && p.getY() < h.absoluteVec(new Vec3(0, 6.6, 0)).y; i++) tick(p, d, 1, 0, false, false, false);
        tick(p, d, 1, 0, true, false, false);
        if (d.state != ClimbState.LEDGE_TRANSITION) h.fail("the long jump must not be refused because of a block on the near one, state " + d.state);
        for (int i = 0; i < 80; i++) tick(p, d, 0, 0, false, false, false);
        double top = h.absoluteVec(new Vec3(0, 10.0, 0)).y, faceZ = h.absoluteVec(new Vec3(0, 0, 6)).z;
        if (p.getY() < top - 0.02 || p.getY() > top + 0.5) h.fail("should end on the far block (top " + top + "), y=" + p.getY());
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

    /** The wall ends just above with only air past it: Space does the jump, hang and pull-up onto its top edge. */
    @GameTest(template = "empty")
    public static void ledgeJumpOntoWallTop(GameTestHelper h) {
        wall(h, 9);
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        grab(p, d);
        for (int i = 0; i < 60 && d.state == ClimbState.CLIMBING && p.getY() < h.absoluteVec(new Vec3(0, 7.2, 0)).y; i++) tick(p, d, 1, 0, false, false, false);
        if (d.state != ClimbState.CLIMBING) h.fail("left the wall before the jump, state " + d.state);
        float before = d.stamina;
        tick(p, d, 1, 0, true, false, false);
        if (d.state != ClimbState.LEDGE_TRANSITION) h.fail("Space under the wall top did not start the ledge jump, state " + d.state);
        if (Math.abs((before - d.stamina) - 97.5f) > 0.01f || d.jumpSpentSeq != 1) h.fail("a ledge jump must cost a jump's stamina and tell the HUD: " + (before - d.stamina) + " / " + d.jumpSpentSeq);
        for (int i = 0; i < 80; i++) tick(p, d, 0, 0, false, false, false);
        double top = h.absoluteVec(new Vec3(0, 10.0, 0)).y, faceZ = h.absoluteVec(new Vec3(0, 0, 8)).z;
        if (p.getY() < top - 0.02) h.fail("did not end on the wall top, y=" + p.getY());
        if (p.getZ() < faceZ + 0.3) h.fail("did not end over the wall top, z offset " + (p.getZ() - faceZ));
        h.succeed();
    }

    /** The end of a one-block-wide wall (one block across, long away from the player): the climb up its end must finish onto the top too. */
    @GameTest(template = "empty")
    public static void ledgeJumpOntoThinWallEnd(GameTestHelper h) {
        for (int z = 8; z <= 15; z++) for (int y = 1; y <= 9; y++) h.setBlock(5, y, z, Blocks.STONE);
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        grab(p, d);
        for (int i = 0; i < 60 && d.state == ClimbState.CLIMBING && p.getY() < h.absoluteVec(new Vec3(0, 7.2, 0)).y; i++) tick(p, d, 1, 0, false, false, false);
        if (d.state != ClimbState.CLIMBING) h.fail("left the thin wall before the jump, state " + d.state);
        tick(p, d, 1, 0, true, false, false);
        if (d.state != ClimbState.LEDGE_TRANSITION) h.fail("Space under the end of a thin wall did not start the ledge jump, state " + d.state);
        for (int i = 0; i < 80; i++) tick(p, d, 0, 0, false, false, false);
        double top = h.absoluteVec(new Vec3(0, 10.0, 0)).y;
        if (p.getY() < top - 0.02) h.fail("did not end on the thin wall's top, y=" + p.getY());
        h.succeed();
    }

    /** Climbing straight up the end of a one-block-wide wall with W only: it pulls itself up over the top by itself. */
    @GameTest(template = "empty")
    public static void climbingUpThinWallEndPullsUp(GameTestHelper h) {
        for (int z = 8; z <= 15; z++) for (int y = 1; y <= 9; y++) h.setBlock(5, y, z, Blocks.STONE);
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        grab(p, d);
        for (int i = 0; i < 160 && d.state != ClimbState.NONE; i++) tick(p, d, 1, 0, false, false, false);
        double top = h.absoluteVec(new Vec3(0, 10.0, 0)).y;
        if (d.state != ClimbState.NONE || p.getY() < top - 0.05) h.fail("climbing up should end standing on top, state " + d.state + " y=" + p.getY() + " top=" + top);
        h.succeed();
    }

    /** Climbing along a wall and pressing A + Space under its top edge (no block above): a ledge jump up onto it, not a sideways hop. */
    @GameTest(template = "empty")
    public static void sideSpaceUnderWallTopJumpsUp(GameTestHelper h) {
        wall(h, 9);
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        grab(p, d);
        for (int i = 0; i < 60 && d.state == ClimbState.CLIMBING && p.getY() < h.absoluteVec(new Vec3(0, 7.2, 0)).y; i++) tick(p, d, 1, 0, false, false, false);
        tick(p, d, 0, 1, true, false, false);
        if (d.state != ClimbState.LEDGE_TRANSITION) h.fail("A + Space under the wall top should be the ledge jump, state " + d.state);
        for (int i = 0; i < 80; i++) tick(p, d, 0, 0, false, false, false);
        double top = h.absoluteVec(new Vec3(0, 10.0, 0)).y;
        if (p.getY() < top - 0.02) h.fail("did not end on the wall top, y=" + p.getY());
        h.succeed();
    }

    /** Tops reached from awkward places: a lone one-block pillar, and the outermost column of a wide wall. Space and W-only must both finish on top. */
    @GameTest(template = "empty")
    public static void topsFromAwkwardPlaces(GameTestHelper h) {
        double top = h.absoluteVec(new Vec3(0, 10.0, 0)).y;
        for (int variant = 0; variant < 4; variant++) {
            for (int x = 0; x < 12; x++) for (int y = 1; y <= 12; y++) for (int z = 5; z <= 12; z++) h.setBlock(x, y, z, Blocks.AIR);
            double startX = 5.5;
            if (variant < 2) for (int y = 1; y <= 9; y++) h.setBlock(5, y, 8, Blocks.STONE); // a lone 1x1x9 pillar
            else {
                wall(h, 9); // a wide wall, climbed in its outermost column
                startX = 0.5;
            }
            Player p = sim(h, 4.0, false);
            p.setPos(h.absoluteVec(new Vec3(startX, 4.0, FLUSH_Z)));
            ClimbData d = p.getData(BotwClimb.CLIMB);
            grab(p, d);
            boolean space = variant % 2 == 0;
            if (space) {
                for (int i = 0; i < 60 && d.state == ClimbState.CLIMBING && p.getY() < h.absoluteVec(new Vec3(0, 7.2, 0)).y; i++) tick(p, d, 1, 0, false, false, false);
                tick(p, d, 1, 0, true, false, false);
                if (d.state != ClimbState.LEDGE_TRANSITION) h.fail("variant " + variant + ": Space under the top did not start the ledge jump, state " + d.state);
                for (int i = 0; i < 80; i++) tick(p, d, 0, 0, false, false, false);
            } else {
                for (int i = 0; i < 160 && d.state != ClimbState.NONE; i++) tick(p, d, 1, 0, false, false, false);
            }
            if (p.getY() < top - 0.05) h.fail("variant " + variant + " (pillar=" + (variant < 2) + ", space=" + space + ") did not end on top: state " + d.state + " y=" + p.getY() + " top=" + top);
            d.setState(ClimbState.NONE);
        }
        h.succeed();
    }

    /** The one-block jump onto a block above you still works when a slab or a stair (any facing, either half) sits right under that block. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void jumpOntoBlockWithSlabOrStairUnderIt(GameTestHelper h) {
        var slab = net.minecraft.world.level.block.SlabBlock.TYPE;
        var stairs = (net.minecraft.world.level.block.StairBlock) Blocks.STONE_STAIRS;
        java.util.List<net.minecraft.world.level.block.state.BlockState> under = new java.util.ArrayList<>();
        under.add(Blocks.STONE_SLAB.defaultBlockState());
        under.add(Blocks.STONE_SLAB.defaultBlockState().setValue(slab, net.minecraft.world.level.block.state.properties.SlabType.TOP));
        for (var f : net.minecraft.core.Direction.Plane.HORIZONTAL) for (var half : net.minecraft.world.level.block.state.properties.Half.values()) {
            under.add(stairs.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, f).setValue(net.minecraft.world.level.block.StairBlock.HALF, half));
        }
        double top = h.absoluteVec(new Vec3(0, 10.0, 0)).y;
        StringBuilder bad = new StringBuilder();
        java.util.List<net.minecraft.world.level.block.state.BlockState[]> combos = new java.util.ArrayList<>();
        for (var u : under) combos.add(new net.minecraft.world.level.block.state.BlockState[]{Blocks.STONE.defaultBlockState(), u}); // full block with the piece under it
        for (var u : under) combos.add(new net.minecraft.world.level.block.state.BlockState[]{u, Blocks.AIR.defaultBlockState()});   // the piece itself is the ledge
        for (var u : under) combos.add(new net.minecraft.world.level.block.state.BlockState[]{u, u});                                 // the piece on a piece
        for (var combo : combos) {
            var below = combo[1];
            for (int x = 0; x < 12; x++) for (int y = 1; y <= 14; y++) for (int z = 5; z <= 8; z++) h.setBlock(x, y, z, Blocks.AIR);
            wall(h, 14);
            h.setBlock(5, 9, 7, combo[0]);
            h.setBlock(5, 8, 7, below);
            Player p = sim(h, 4.0, false);
            ClimbData d = p.getData(BotwClimb.CLIMB);
            grab(p, d);
            for (int i = 0; i < 80 && d.state == ClimbState.CLIMBING && p.getY() < h.absoluteVec(new Vec3(0, 6.2, 0)).y; i++) tick(p, d, 1, 0, false, false, false);
            tick(p, d, 1, 0, true, false, false);
            boolean started = d.state == ClimbState.LEDGE_TRANSITION;
            for (int i = 0; i < 80; i++) tick(p, d, 0, 0, false, false, false);
            double expect = h.absoluteVec(new Vec3(0, 9.0 + h.getLevel().getBlockState(h.absolutePos(new net.minecraft.core.BlockPos(5, 9, 7))).getCollisionShape(h.getLevel(), h.absolutePos(new net.minecraft.core.BlockPos(5, 9, 7))).max(net.minecraft.core.Direction.Axis.Y), 0)).y;
            if (!started || p.getY() < expect - 0.05) bad.append(" | top=").append(combo[0]).append(" under=").append(below).append(": started=").append(started).append(" y=").append(p.getY()).append(" top=").append(top);
            d.setState(ClimbState.NONE);
        }
        if (bad.length() > 0) h.fail("the one-block jump failed with:" + bad);
        h.succeed();
    }

    /** A slab in the top half of its space has its underside higher: the ledge jump must still work. */
    @GameTest(template = "empty")
    public static void ledgeJumpOntoTopSlab(GameTestHelper h) {
        wall(h, 14);
        h.setBlock(5, 9, 7, Blocks.STONE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        grab(p, d);
        for (int i = 0; i < 60 && d.state == ClimbState.CLIMBING && p.getY() < h.absoluteVec(new Vec3(0, 5.9, 0)).y; i++) tick(p, d, 1, 0, false, false, false);
        tick(p, d, 1, 0, true, false, false);
        if (d.state != ClimbState.LEDGE_TRANSITION) h.fail("Space under a top slab did not start the ledge jump, state " + d.state);
        for (int i = 0; i < 80; i++) tick(p, d, 0, 0, false, false, false);
        double top = h.absoluteVec(new Vec3(0, 10.0, 0)).y;
        if (p.getY() < top - 0.02) h.fail("did not end on the slab, y=" + p.getY());
        h.succeed();
    }

    /** Right below a block, Space with only A (no W) is the sideways hop, never the ledge jump. */
    @GameTest(template = "empty")
    public static void sideOnlyUnderBlockIsNormalHop(GameTestHelper h) {
        wall(h, 14);
        h.setBlock(5, 9, 7, Blocks.STONE);
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        grab(p, d);
        for (int i = 0; i < 60 && d.state == ClimbState.CLIMBING && p.getY() < h.absoluteVec(new Vec3(0, 6.6, 0)).y; i++) tick(p, d, 1, 0, false, false, false);
        tick(p, d, 0, 1, true, false, false);
        if (d.state == ClimbState.LEDGE_TRANSITION) h.fail("A + Space started the ledge jump");
        if (d.state != ClimbState.CLIMB_JUMP) h.fail("A + Space under a block should be the normal hop, state " + d.state);
        if (d.plan == null || d.plan.end().y > 0.7) h.fail("the sideways hop right under a block went upward, plan " + (d.plan == null ? "none" : d.plan.end()));
        if (Math.abs(d.plan.end().x) < 0.8) h.fail("the sideways hop did not go sideways, plan " + d.plan.end());
        h.succeed();
    }

    /** {climb distance over 20 ticks, ticks a W+A wall jump takes} with or without boots of Climb Speed. */
    private static double[] speedRun(GameTestHelper h, boolean enchanted) {
        wall(h, 20);
        Player p = sim(h, 4.0, false);
        if (enchanted) {
            var boots = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_BOOTS);
            var holder = com.botwclimb.common.Compat.holder(h.getLevel(), com.botwclimb.common.climb.ClimbController.CLIMB_SPEED_ENCHANT).orElseThrow();
            boots.enchant(holder, 1);
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, boots);
        }
        ClimbData d = p.getData(BotwClimb.CLIMB);
        grab(p, d);
        for (int i = 0; i < 12; i++) tick(p, d, 0, 0, false, false, false);
        double y0 = p.getY();
        for (int i = 0; i < 20; i++) tick(p, d, 1, 0, false, false, false);
        double dy = p.getY() - y0;
        tick(p, d, 1, 1, true, false, false);
        double speed = d.jumpSpeed;
        int ticks = 0;
        while (d.state == ClimbState.CLIMB_JUMP && ticks < 80) { tick(p, d, 0, 0, false, false, false); ticks++; }
        return new double[]{dy, ticks, speed};
    }

    /** Boots with Climb Speed: 25% faster wall climbing, ~15% quicker wall jumps. */
    @GameTest(template = "empty")
    public static void climbSpeedEnchantment(GameTestHelper h) {
        double[] plain = speedRun(h, false);
        double[] fast = speedRun(h, true);
        double ratio = fast[0] / plain[0];
        if (ratio < 1.2 || ratio > 1.3) h.fail("climb speed ratio should be ~1.25, got " + ratio + " (" + plain[0] + " vs " + fast[0] + ")");
        if (Math.abs(plain[2] - 0.8) > 1e-9 || Math.abs(fast[2] - 0.8 * 1.15) > 1e-9) h.fail("jump playback speed should be 0.8 / 0.92, got " + plain[2] + " / " + fast[2]);
        if (fast[1] > plain[1]) h.fail("the faster wall jump must not take longer: " + plain[1] + " ticks vs " + fast[1]); // whole ticks are too coarse to check 15% exactly
        h.succeed();
    }

    /** Stamina: 36.5/s while moving on the wall, 150 per climbing jump, and you fall when it runs out. */
    @GameTest(template = "empty")
    public static void staminaDrainJumpAndExhaustion(GameTestHelper h) {
        wall(h, 30);
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        grab(p, d);
        for (int i = 0; i < 12; i++) tick(p, d, 0, 0, false, false, false); // hanging still: no cost
        float start = d.stamina;
        if (Math.abs(start - 750f) > 0.01) h.fail("should start with one full wheel (750), has " + start);
        for (int i = 0; i < 40; i++) tick(p, d, 1, 0, false, false, false);
        float spent = start - d.stamina;
        if (Math.abs(spent - 40 * 36.5f / 20f) > 0.5f) h.fail("40 ticks of climbing should cost 73 units, cost " + spent);
        for (int i = 0; i < 12; i++) tick(p, d, 0, 0, false, false, false);
        float before = d.stamina;
        tick(p, d, 1, 1, true, false, false); // a wall jump
        if (Math.abs((before - d.stamina) - 150f) > 0.01f) h.fail("a climbing jump should cost 150, cost " + (before - d.stamina));
        for (int i = 0; i < 40 && d.state == ClimbState.CLIMB_JUMP; i++) tick(p, d, 0, 0, false, false, false);
        d.stamina = 3f;
        d.state = d.state == ClimbState.NONE ? ClimbState.NONE : d.state;
        for (int i = 0; i < 5 && d.state == ClimbState.CLIMBING; i++) tick(p, d, 1, 0, false, false, false);
        if (d.state == ClimbState.CLIMBING) h.fail("out of stamina should drop you off the wall");
        if (!d.exhausted) h.fail("running out should leave you exhausted");
        grab(p, d); // the climb key held next to the wall while exhausted
        if (d.state != ClimbState.NONE) h.fail("must not be able to climb while exhausted, state " + d.state);
        h.succeed();
    }

    /** With less stamina than a jump costs you can still jump; it takes what is left and you are exhausted. */
    @GameTest(template = "empty")
    public static void jumpWithLittleStaminaStillJumps(GameTestHelper h) {
        wall(h, 30);
        Player p = sim(h, 4.0, false);
        ClimbData d = p.getData(BotwClimb.CLIMB);
        grab(p, d);
        for (int i = 0; i < 12; i++) tick(p, d, 0, 0, false, false, false);
        d.stamina = 50f;
        tick(p, d, 1, 1, true, false, false);
        if (d.state != ClimbState.CLIMB_JUMP) h.fail("a jump with 50 stamina left should still happen, state " + d.state);
        if (d.stamina != 0f || !d.exhausted) h.fail("it should have used up the rest: stamina " + d.stamina + " exhausted " + d.exhausted);
        if (d.jumpSpent != 50f || d.jumpSpentSeq != 1) h.fail("the HUD must be told what the jump took: " + d.jumpSpent + " / " + d.jumpSpentSeq);
        h.succeed();
    }

    /** A wall climb has a top speed and a jump a reach, but a tick or two of lag over them is allowed. */
    @GameTest(template = "empty")
    public static void serverClimbLimits(GameTestHelper h) {
        ClimbData d = new ClimbData();
        d.state = ClimbState.CLIMBING;
        double legit = 0.14 * 0.14, fast = 1.0;
        for (int i = 0; i < 20; i++) if (ServerClimb.tooFast(d, legit)) h.fail("a normal climb must never be too fast");
        for (int i = 0; i < 3; i++) if (ServerClimb.tooFast(d, fast)) h.fail("three fast ticks in a row are lag, not a cheat");
        ServerClimb.tooFast(d, legit); // back to normal resets the count
        for (int i = 0; i < 3; i++) if (ServerClimb.tooFast(d, fast)) h.fail("the count must have reset");
        if (!ServerClimb.tooFast(d, fast)) h.fail("a fourth fast tick in a row must be refused");
        d.grace = 5;
        d.fastTicks = 0;
        for (int i = 0; i < 10; i++) if (ServerClimb.tooFast(d, fast)) h.fail("a fresh attach is exempt (the latch hop)");
        d.state = ClimbState.CLIMB_JUMP; // not a wall hold: the jump envelope applies instead
        d.grace = 0;
        d.dir = Direction.SOUTH;
        d.jumpStart = Vec3.ZERO;
        Vec3 inside = new Vec3(0, 2.5, -1.0), outside = new Vec3(0, 12, 0); // facing south, away from the wall is "out"
        for (int i = 0; i < 30; i++) if (ServerClimb.offEnvelope(d, inside)) h.fail("a position inside the reach is fine");
        for (int i = 0; i < 8; i++) if (ServerClimb.offEnvelope(d, outside)) h.fail("up to eight ticks outside are tolerated");
        if (!ServerClimb.offEnvelope(d, outside)) h.fail("staying outside the reach must end the jump");
        ServerClimb.offEnvelope(d, inside);
        if (ServerClimb.offEnvelope(d, outside)) h.fail("coming back inside resets the count");
        h.succeed();
    }

    /** The dev commands must be op-only whichever class registered /cotm first. */
    @GameTest(template = "empty")
    public static void devCommandsAreOpOnly(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var root = server.getCommands().getDispatcher().getRoot().getChild("cotm");
        if (root == null) h.fail("/cotm is not registered");
        var nobody = server.createCommandSourceStack().withPermission(com.botwclimb.common.Compat.permission(0));
        var op = server.createCommandSourceStack().withPermission(com.botwclimb.common.Compat.permission(2));
        for (String sub : new String[]{"stamina", "paraglider"}) {
            var node = root.getChild(sub);
            if (node == null) h.fail("/cotm " + sub + " is missing");
            if (node.canUse(nobody)) h.fail("/cotm " + sub + " must not be usable without op");
            if (!node.canUse(op)) h.fail("/cotm " + sub + " must be usable by an op");
        }
        h.succeed();
    }

    /** The slow fall is the server's decision: no bow or on the ground means no slow fall, whatever the client sent. */
    @GameTest(template = "empty")
    public static void bulletFallNeedsABowInTheAir(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "bullet_fall");
        var gravity = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
        p.setOnGround(false);
        com.botwclimb.common.bullettime.BulletFall.apply(p, true);
        if (gravity.hasModifier(id)) h.fail("slow fall applied without a bow");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
        p.setPos(h.absoluteVec(new Vec3(5.5, 30.0, 5.5)));
        com.botwclimb.common.bullettime.BulletFall.apply(p, true);
        if (gravity.hasModifier(id)) h.fail("slow fall must not apply on the way up (nothing has fallen yet): a client could jump far higher with it");
        p.fallDistance = 1f;
        com.botwclimb.common.bullettime.BulletFall.apply(p, true);
        if (!gravity.hasModifier(id)) h.fail("slow fall should apply with a bow in the air");
        p.setOnGround(true);
        com.botwclimb.common.bullettime.BulletFall.apply(p, true);
        if (gravity.hasModifier(id)) h.fail("slow fall must end on the ground even if the client says it is on");
        if (com.botwclimb.common.bullettime.BulletFall.allowed(p)) h.fail("allowed() must be false on the ground");
        h.succeed();
    }

    /** Packets decode back to what was sent, and an absurd list size is refused. */
    @GameTest(template = "empty")
    public static void payloadsRoundTripAndAreBounded(GameTestHelper h) {
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        for (ClimbState st : ClimbState.values()) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                com.botwclimb.network.ClimbInputPayload.CODEC.encode(buf, new com.botwclimb.network.ClimbInputPayload(st, dir));
                var back = com.botwclimb.network.ClimbInputPayload.CODEC.decode(buf);
                if (back.state() != st || back.dir() != dir) h.fail("climb input changed in transit: " + st + "/" + dir + " -> " + back);
            }
        }
        com.botwclimb.network.BulletSlowPayload.CODEC.encode(buf, new com.botwclimb.network.BulletSlowPayload(new int[]{7, 300, 70000}));
        var got = com.botwclimb.network.BulletSlowPayload.CODEC.decode(buf).ids();
        if (got.length != 3 || got[0] != 7 || got[1] != 300 || got[2] != 70000) h.fail("slowed ids changed in transit: " + java.util.Arrays.toString(got));
        buf.clear();
        buf.writeVarInt(1_000_000);
        try {
            com.botwclimb.network.BulletSlowPayload.CODEC.decode(buf);
            h.fail("a huge slowed list must be refused");
        } catch (io.netty.handler.codec.DecoderException expected) {
            // good
        }
        h.succeed();
    }

    /** The server's account: hanging still is free, moving drains, running dry blocks everything until full. */
    @GameTest(template = "empty")
    public static void serverStaminaAccount(GameTestHelper h) {
        ClimbData d = new ClimbData();
        float max = 750f;
        d.state = ClimbState.CLIMBING;
        for (int i = 0; i < 100; i++) com.botwclimb.common.climb.Stamina.serverStep(d, max, false, false);
        if (d.sStamina != max) h.fail("hanging still must cost nothing, has " + d.sStamina);
        for (int i = 0; i < 100; i++) com.botwclimb.common.climb.Stamina.serverStep(d, max, true, false);
        if (d.sStamina >= max || d.sExhausted) h.fail("100 ticks of climbing should drain some, not all: " + d.sStamina);
        for (int i = 0; i < 2000; i++) com.botwclimb.common.climb.Stamina.serverStep(d, max, true, false);
        if (!d.sExhausted || d.sStamina != 0f) h.fail("climbing forever must run the account dry");
        d.state = ClimbState.NONE;
        com.botwclimb.common.climb.Stamina.serverStep(d, max, false, false);
        if (!d.sExhausted) h.fail("a little recovery must not lift the block");
        for (int i = 0; i < 200; i++) com.botwclimb.common.climb.Stamina.serverStep(d, max, false, false);
        if (d.sExhausted || d.sStamina != max) h.fail("it must unblock only once full: " + d.sStamina + " exhausted " + d.sExhausted);
        for (int i = 0; i < 1000; i++) com.botwclimb.common.climb.Stamina.serverStep(d, max, false, true);
        if (!d.sExhausted) h.fail("gliding forever must run the account dry too");
        h.succeed();
    }

    /** Ticks each entity really ran in these tests: NeoForge fires the Post event only for a tick that was not cancelled. */
    private static final java.util.Map<Integer, Integer> RAN = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.tick.EntityTickEvent.Post e) -> RAN.merge(e.getEntity().getId(), 1, Integer::sum));
    }

    private static net.minecraft.world.entity.Mob baby(GameTestHelper h, Vec3 at) {
        net.minecraft.world.entity.Mob cow = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW, at);
        cow.setNoGravity(true); // the empty test area has no floor on every version: a falling mob would leave the world
        return cow;
    }

    /** How many ticks an entity has really run so far. */
    private static int ran(net.minecraft.world.entity.Entity e) {
        return RAN.getOrDefault(e.getId(), 0);
    }

    /**
     * A slowed mob gets one tick in every 11 while the clock around it keeps going: its age (one per tick it runs) grows about a
     * tenth as fast as a free mob's, its tick count does not stop, and its hurt timer still runs out on the real clock.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bulletSlowRunsOneTickInEleven(GameTestHelper h) {
        var level = h.getLevel();
        var free = baby(h, new Vec3(3.5, 2.0, 3.5));
        var slow = baby(h, new Vec3(6.5, 2.0, 6.5));
        int freeAge = ran(free), slowAge = ran(slow), freeTicks = free.tickCount, slowTicks = slow.tickCount;
        int[] t = {0};
        h.onEachTick(() -> {
            t[0]++;
            com.botwclimb.common.bullettime.BulletSlow.slow(slow, level.getGameTime()); // what the shooter's scan does every tick
            if (t[0] == 1) slow.invulnerableTime = 20;
            if (t[0] == 33) {
                int ranFree = ran(free) - freeAge, ranSlow = ran(slow) - slowAge;
                if (ranFree < 30) h.fail("the free mob should run nearly every tick, ran " + ranFree);
                if (ranSlow < 3 || ranSlow > 6) h.fail("33 ticks slowed should give about 5 turns (1 in " + com.botwclimb.common.bullettime.BulletConstants.PERIOD + "), got " + ranSlow);
                int ticked = (slow.tickCount - slowTicks) - (free.tickCount - freeTicks);
                if (Math.abs(ticked) > 1) h.fail("a slowed mob's tick count must keep counting with the clock, differs by " + ticked);
                if (slow.invulnerableTime > 2) h.fail("a slowed mob's hurt immunity must run out on the real clock, still " + slow.invulnerableTime);
                h.succeed();
            }
        });
    }

    /** When bullet time ends the mob runs every tick again at once: nothing is left half stuck. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bulletSlowEndsAtOnce(GameTestHelper h) {
        var level = h.getLevel();
        var cow = baby(h, new Vec3(3.5, 2.0, 3.5));
        int[] age = new int[3];
        int[] t = {0};
        h.onEachTick(() -> {
            t[0]++;
            if (t[0] == 1) age[0] = ran(cow);
            if (t[0] <= 22) com.botwclimb.common.bullettime.BulletSlow.slow(cow, level.getGameTime());
            if (t[0] == 22 + com.botwclimb.common.bullettime.BulletConstants.GRACE) age[1] = ran(cow); // the grace has run out
            if (t[0] == 22 + com.botwclimb.common.bullettime.BulletConstants.GRACE + 11) {
                age[2] = ran(cow);
                int slowed = age[1] - age[0], after = age[2] - age[1];
                if (slowed > 5) h.fail("it should have been slowed, ran " + slowed + " ticks in " + (22 + com.botwclimb.common.bullettime.BulletConstants.GRACE));
                if (after < 10) h.fail("11 ticks after bullet time it must run every one of them, ran " + after);
                h.succeed();
            }
        });
    }

    /** Only mobs are slowed, only on the ticks that are not their turn, and only for a moment after the shooter's last scan. */
    @GameTest(template = "empty")
    public static void bulletSlowSkipRules(GameTestHelper h) {
        var cow = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW, new Vec3(2.5, 2.0, 2.5));
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        long now = 5000;
        var slow = com.botwclimb.common.bullettime.BulletSlow.class;
        if (com.botwclimb.common.bullettime.BulletSlow.shouldSkip(cow, now)) h.fail("a mob nobody slowed must not be skipped");
        com.botwclimb.common.bullettime.BulletSlow.slow(cow, now);
        cow.tickCount = 5;
        if (!com.botwclimb.common.bullettime.BulletSlow.shouldSkip(cow, now)) h.fail("a slowed mob sits out a tick that is not its turn");
        cow.tickCount = 11 * 7;
        if (com.botwclimb.common.bullettime.BulletSlow.shouldSkip(cow, now)) h.fail("a slowed mob runs the tick that is its turn");
        cow.tickCount = 5;
        if (com.botwclimb.common.bullettime.BulletSlow.shouldSkip(cow, now + com.botwclimb.common.bullettime.BulletConstants.GRACE + 1)) h.fail("it must run freely once the grace is over");
        com.botwclimb.common.bullettime.BulletSlow.slow(player, now);
        player.tickCount = 5;
        if (com.botwclimb.common.bullettime.BulletSlow.shouldSkip(player, now)) h.fail("a player is never slowed");
        h.succeed();
    }

    /** A player held in the air with a bow, in the level: what a shooter in bullet time looks like to the server. */
    private static Player airborneShooter(GameTestHelper h, Vec3 at) {
        var sp = h.makeMockPlayer(GameType.SURVIVAL);
        sp.setPos(h.absoluteVec(at));
        sp.setNoGravity(true);
        sp.setOnGround(false);
        sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
        return sp;
    }

    /**
     * The server loop with a shooter: while its heartbeat comes, the mobs within range run about 1 tick in 11 and mobs out of range
     * do not; players are never slowed; and once the heartbeat stops everything runs freely again.
     */
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void bulletTimeScanSlowsMobsInRangeAndNeverPlayers(GameTestHelper h) {
        var level = h.getLevel();
        var shooter = airborneShooter(h, new Vec3(5.5, 3.0, 5.5));
        var other = airborneShooter(h, new Vec3(8.5, 3.0, 5.5));
        var near = baby(h, new Vec3(7.5, 2.0, 7.5));
        near.setNoGravity(true);
        var far = baby(h, new Vec3(5.5, 2.0, 5.5 + 20)); // beyond the range
        far.setNoGravity(true);
        int nearAge = ran(near);
        int[] mark = new int[2];
        int[] t = {0};
        h.onEachTick(() -> {
            t[0]++;
            long now = level.getGameTime();
            if (t[0] <= 33) {
                shooter.fallDistance = 1f; // the server checks for itself that a shooter is falling
                com.botwclimb.common.bullettime.BulletSlow.markActive(shooter.getUUID(), now); // the client's heartbeat
                com.botwclimb.common.bullettime.BulletSlow.scan(level, java.util.List.of(shooter, other), now, 8.0); // what the level tick does, with a small range so parallel tests are left alone
            }
            if (t[0] == 33) {
                int ranNear = ran(near) - nearAge;
                if (ranNear > 5) h.fail("a mob near the shooter should be slowed (about 3 of 33 ticks), it ran " + ranNear);
                if (com.botwclimb.common.bullettime.BulletSlow.isSlowed(far, now)) h.fail("a mob out of range must not be slowed"); // (it sits in an unloaded chunk, so its age says nothing)
                if (com.botwclimb.common.bullettime.BulletSlow.isSlowed(other, now)) h.fail("another player must never be slowed on the server");
                if (com.botwclimb.common.bullettime.BulletSlow.isSlowed(shooter, now)) h.fail("the shooter must never be slowed");
                mark[0] = ran(near);
            }
            if (t[0] == 33 + 12) { // the heartbeat stopped 12 ticks ago: the grace is long over
                mark[1] = ran(near);
                if (mark[1] - mark[0] < 10) h.fail("the mob must run every tick once the heartbeat has stopped, it ran " + (mark[1] - mark[0]) + " of 12");
                h.succeed();
            }
        });
    }

    /** Another player near a shooter is slowed through attributes and freed again, a shooter in bullet time or a creative player is not. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void bulletTimeSlowsOtherPlayersThroughAttributes(GameTestHelper h) {
        var level = h.getLevel();
        var shooter = airborneShooter(h, new Vec3(5.5, 3.0, 5.5));
        var other = airborneShooter(h, new Vec3(8.5, 3.0, 5.5));
        var creative = h.makeMockPlayer(GameType.CREATIVE);
        creative.setPos(h.absoluteVec(new Vec3(6.5, 3.0, 5.5)));
        long now = level.getGameTime();
        com.botwclimb.common.bullettime.BulletSlow.markActive(shooter.getUUID(), now);
        com.botwclimb.common.bullettime.BulletSlow.scan(level, java.util.List.of(shooter, other, creative), now, 8.0);
        com.botwclimb.common.bullettime.BulletSlow.markPlayer(other, now); // mock players are not in the level, so the range query cannot find them
        com.botwclimb.common.bullettime.BulletSlow.markPlayer(shooter, now);
        com.botwclimb.common.bullettime.BulletSlow.markPlayer(creative, now);
        if (!com.botwclimb.common.bullettime.BulletSlow.playerSlowed(other, now)) h.fail("a player near the shooter should be slowed");
        if (com.botwclimb.common.bullettime.BulletSlow.playerSlowed(shooter, now)) h.fail("the shooter must not be slowed");
        if (com.botwclimb.common.bullettime.BulletSlow.playerSlowed(creative, now)) h.fail("a creative player must not be slowed");
        double base = other.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        com.botwclimb.common.bullettime.BulletSlow.applyToPlayer(other, true);
        double slowed = other.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (slowed > base * 0.2) h.fail("walking should be about 1/7 of normal, was " + slowed + " of " + base);
        com.botwclimb.common.bullettime.BulletSlow.applyToPlayer(other, false);
        if (other.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) != base) h.fail("the slowdown must come off completely");
        h.succeed();
    }

    /** A player without the mod can be on the server (its connection knows none of the mod's payloads); nothing the server does may throw because of it. */
    @GameTest(template = "empty")
    public static void packetsToAPlayerWithoutTheModAreSkipped(GameTestHelper h) {
        //? if <1.21.2 {
        // NeoForge 21.1 syncs our attachments itself and throws at login for a connection that knows no channels at all, so there is no such player here
        if (true) {
            h.succeed();
            return;
        }
        //?}
        var modless = h.makeMockServerPlayerInLevel(); // a connection with no channels, like a vanilla client's
        try {
            com.botwclimb.common.paraglider.ParagliderController.broadcast(modless, true, false);
            com.botwclimb.common.Net.toPlayer(modless, new com.botwclimb.network.BulletSlowPayload(new int[0]));
            com.botwclimb.common.Net.toWatchers(modless, new com.botwclimb.network.BulletSlowPayload(new int[0]), true);
            com.botwclimb.common.shrine.TerminalRide.start(modless, h.absolutePos(new net.minecraft.core.BlockPos(5, 1, 5)), 0);
            com.botwclimb.common.shrine.TerminalRide.onLogout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(modless));
        } catch (RuntimeException e) {
            h.fail("sending to a player without the mod threw: " + e);
        }
        h.succeed();
    }

    /** A client that says it is in the air while it stands on the ground gets no bullet time, and bullet time does not run for ever. */
    @GameTest(template = "empty", timeoutTicks = 520)
    public static void bulletHeartbeatIsCheckedByTheServer(GameTestHelper h) {
        var level = h.getLevel();
        var bow = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW);
        var grounded = h.makeMockPlayer(GameType.SURVIVAL); // not in the level: another test's mobs are not slowed by it
        h.setBlock(5, 0, 5, Blocks.STONE); // a floor to stand on (the empty template has none)
        grounded.setPos(h.absoluteVec(new Vec3(5.5, 1.0, 5.5)));
        grounded.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bow.copy());
        grounded.setOnGround(false); // the lie: "airborne", while the floor is right under the feet
        var flying = h.makeMockPlayer(GameType.SURVIVAL);
        flying.setPos(h.absoluteVec(new Vec3(8.5, 40.0, 5.5)));
        flying.setNoGravity(true);
        flying.setOnGround(false);
        flying.fallDistance = 1f; // it has been falling
        flying.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bow.copy());
        int[] t = {0};
        h.onEachTick(() -> {
            t[0]++;
            long now = level.getGameTime();
            grounded.setOnGround(false);
            flying.setOnGround(false);
            com.botwclimb.common.bullettime.BulletSlow.accept(grounded);
            com.botwclimb.common.bullettime.BulletSlow.accept(flying);
            if (t[0] == 20) {
                if (com.botwclimb.common.bullettime.BulletSlow.active(grounded, now)) h.fail("standing on the ground must not give bullet time, whatever the client says");
                if (!com.botwclimb.common.bullettime.BulletSlow.active(flying, now)) h.fail("a player really in the air with a bow must get bullet time");
            }
            if (t[0] == 500) {
                if (com.botwclimb.common.bullettime.BulletSlow.active(flying, now)) h.fail("bullet time must run out after its budget of " + com.botwclimb.common.bullettime.BulletConstants.SERVER_BUDGET + " ticks");
                h.succeed();
            }
        });
    }

    /** A slowed mob is where the server has it: an arrow of a shooter in bullet time hits its real hitbox the ordinary way. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bulletArrowHitsASlowedMobWhereItIs(GameTestHelper h) {
        var level = h.getLevel();
        var shooter = airborneShooter(h, new Vec3(1.5, 3.0, 5.5));
        var cow = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW, new Vec3(5.5, 2.0, 5.5));
        cow.setNoGravity(true);
        Vec3 spot = h.absoluteVec(new Vec3(5.5, 2.0, 5.5));
        int[] t = {0};
        h.onEachTick(() -> {
            t[0]++;
            long now = level.getGameTime();
            shooter.fallDistance = 1f; // the server checks for itself that a shooter is falling
            com.botwclimb.common.bullettime.BulletSlow.markActive(shooter.getUUID(), now);
            com.botwclimb.common.bullettime.BulletSlow.scan(level, java.util.List.of(shooter), now, 8.0);
            if (t[0] == 15) {
                var arrow = com.botwclimb.common.Compat.create(net.minecraft.world.entity.EntityType.ARROW, level);
                arrow.setOwner(shooter);
                arrow.setNoGravity(true);
                arrow.setPos(spot.add(-3.0, 0.6, 0.0));
                arrow.setDeltaMovement(1.5, 0, 0);
                arrow.setBaseDamage(2.0);
                level.addFreshEntity(arrow);
            }
            if (t[0] == 40) {
                if (!com.botwclimb.common.bullettime.BulletSlow.isSlowed(cow, now)) h.fail("the cow should be slowed");
                if (cow.getHealth() >= cow.getMaxHealth()) h.fail("an arrow aimed at a slowed mob's real position must hit it");
                h.succeed();
            }
        });
    }

    /** Nothing that rides or carries something is slowed (a mob on a Create contraption seat, a boat), and machinery never is. */
    @GameTest(template = "empty")
    public static void bulletSlowLeavesRidersAndMachinesAlone(GameTestHelper h) {
        var cow = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW, new Vec3(2.5, 2.0, 2.5));
        var boat = h.spawn(net.minecraft.world.entity.EntityType.MINECART, new Vec3(5.5, 2.0, 5.5));
        var rider = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW, new Vec3(5.5, 2.0, 5.5));
        rider.startRiding(boat);
        var free = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW, new Vec3(8.5, 2.0, 8.5));
        long now = 7000;
        var bs = com.botwclimb.common.bullettime.BulletSlow.class;
        com.botwclimb.common.bullettime.BulletSlow.slow(boat, now);
        com.botwclimb.common.bullettime.BulletSlow.slow(rider, now);
        com.botwclimb.common.bullettime.BulletSlow.slow(free, now);
        free.tickCount = 5;
        boat.tickCount = 5;
        rider.tickCount = 5;
        if (com.botwclimb.common.bullettime.BulletSlow.isSlowed(boat, now)) h.fail("a minecart (like a contraption) is not a living thing and must never be slowed");
        if (com.botwclimb.common.bullettime.BulletSlow.shouldSkip(boat, now)) h.fail("a vehicle's tick must never be skipped");
        if (com.botwclimb.common.bullettime.BulletSlow.shouldSkip(rider, now)) h.fail("a passenger must never be slowed: it moves with what carries it");
        if (!com.botwclimb.common.bullettime.BulletSlow.shouldSkip(free, now)) h.fail("a free-standing mob should be slowed");
        rider.startRiding(free); // a slowed mob that picks up a passenger is released at once
        if (com.botwclimb.common.bullettime.BulletSlow.shouldSkip(free, now)) h.fail("a mob that now carries something must run freely");
        h.succeed();
    }

    private static net.minecraft.world.item.ItemStack stack(net.minecraft.world.item.Item i) {
        return new net.minecraft.world.item.ItemStack(i);
    }

    private static void near(GameTestHelper h, String what, float got, float want) {
        if (Math.abs(got - want) > 0.005f) h.fail(what + ": expected " + want + ", got " + got);
    }

    /** What items are worth at the altar: the listed valuables, diamond and netherite gear, enchant bonus, durability. */
    @GameTest(template = "empty")
    public static void altarValues(GameTestHelper h) {
        var items = net.minecraft.world.item.Items.class;
        near(h, "diamond", com.botwclimb.common.altar.AltarValues.unitValue(stack(net.minecraft.world.item.Items.DIAMOND)), 2f);
        near(h, "iron ingot", com.botwclimb.common.altar.AltarValues.unitValue(stack(net.minecraft.world.item.Items.IRON_INGOT)), 0.1f);
        near(h, "dirt is not valuable", com.botwclimb.common.altar.AltarValues.unitValue(stack(net.minecraft.world.item.Items.DIRT)), 0f);
        near(h, "diamond block is 9 diamonds", com.botwclimb.common.altar.AltarValues.unitValue(stack(net.minecraft.world.item.Items.DIAMOND_BLOCK)), 18f);
        near(h, "diamond sword", com.botwclimb.common.altar.AltarValues.unitValue(stack(net.minecraft.world.item.Items.DIAMOND_SWORD)), 4f);
        near(h, "diamond chestplate", com.botwclimb.common.altar.AltarValues.unitValue(stack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE)), 16f);
        near(h, "netherite sword", com.botwclimb.common.altar.AltarValues.unitValue(stack(net.minecraft.world.item.Items.NETHERITE_SWORD)), 7f);
        near(h, "netherite chestplate", com.botwclimb.common.altar.AltarValues.unitValue(stack(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE)), 20f);
        near(h, "iron sword", com.botwclimb.common.altar.AltarValues.unitValue(stack(net.minecraft.world.item.Items.IRON_SWORD)), 0.2f);
        near(h, "leather chestplate", com.botwclimb.common.altar.AltarValues.unitValue(stack(net.minecraft.world.item.Items.LEATHER_CHESTPLATE)), 0f);
        var worn = stack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE);
        worn.setDamageValue((int) (worn.getMaxDamage() * 0.2));
        float eighty = com.botwclimb.common.altar.AltarValues.unitValue(worn); // the damage is a whole number, so allow a hair
        if (Math.abs(eighty - 12.8f) > 0.05f) h.fail("80% durability: expected about 12.8, got " + eighty);
        worn.setDamageValue((int) (worn.getMaxDamage() * 0.6));
        near(h, "worn below half is refused", com.botwclimb.common.altar.AltarValues.unitValue(worn), 0f);
        var holder = h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION);
        var strong = stack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE);
        strong.enchant(holder, 4);
        near(h, "level 4 enchant adds 2", com.botwclimb.common.altar.AltarValues.unitValue(strong), 18f);
        var weak = stack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE);
        weak.enchant(holder, 3);
        near(h, "level 3 adds nothing", com.botwclimb.common.altar.AltarValues.unitValue(weak), 16f);
        h.succeed();
    }

    /** Prices, the fewest-overshoot choice of items, credit, and the rounded refund. */
    @GameTest(template = "empty")
    public static void altarRules(GameTestHelper h) {
        var R = com.botwclimb.common.altar.AltarRules.class;
        float[] want = {10, 14, 20, 28, 38, 50, 64, 80};
        for (int i = 0; i < 8; i++) near(h, "price " + (i + 1), com.botwclimb.common.altar.AltarRules.price(i), want[i]);
        // 12 diamonds, price 10: exactly 5 diamonds, no credit
        var r = com.botwclimb.common.altar.AltarRules.evaluate(0, 0f, java.util.List.of(new com.botwclimb.common.altar.AltarRules.Offered(0, 12, 2f)));
        if (!r.ok() || r.take().size() != 1 || r.take().get(0).count() != 5) h.fail("12 diamonds for a price of 10 should take exactly 5: " + r);
        near(h, "no credit", r.creditAfter(), 0f);
        // not enough
        if (com.botwclimb.common.altar.AltarRules.evaluate(0, 0f, java.util.List.of(new com.botwclimb.common.altar.AltarRules.Offered(0, 4, 2f))).ok()) h.fail("8 points must not buy a price of 10");
        // credit pays first
        var r2 = com.botwclimb.common.altar.AltarRules.evaluate(0, 7f, java.util.List.of(new com.botwclimb.common.altar.AltarRules.Offered(0, 5, 2f)));
        if (!r2.ok() || r2.take().get(0).count() != 2) h.fail("7 credit + 2 diamonds covers 10: " + r2);
        // only a big piece can finish it: the overshoot becomes credit
        var r3 = com.botwclimb.common.altar.AltarRules.evaluate(0, 0f, java.util.List.of(
                new com.botwclimb.common.altar.AltarRules.Offered(0, 3, 2f), new com.botwclimb.common.altar.AltarRules.Offered(1, 1, 16f)));
        if (!r3.ok()) h.fail("should be possible: " + r3);
        near(h, "overshoot becomes credit", r3.creditAfter(), 6f);
        // small items that sum exactly are preferred over a big overshoot
        var r4 = com.botwclimb.common.altar.AltarRules.evaluate(0, 0f, java.util.List.of(
                new com.botwclimb.common.altar.AltarRules.Offered(0, 5, 2f), new com.botwclimb.common.altar.AltarRules.Offered(1, 1, 16f)));
        near(h, "exact beats overshoot", r4.creditAfter(), 0f);
        if (com.botwclimb.common.altar.AltarRules.evaluate(8, 0f, java.util.List.of()).ok()) h.fail("nothing can be bought past the last upgrade");
        if (com.botwclimb.common.altar.AltarRules.refundCount(0.9f, 0.25f) != 4) h.fail("0.9 credit as emeralds rounds to 4");
        if (com.botwclimb.common.altar.AltarRules.refundCount(4.2f, 2f) != 2) h.fail("4.2 credit as diamonds is 2");
        if (com.botwclimb.common.altar.AltarRules.refundCount(0.9f, 2f) != 0) h.fail("0.9 credit is not enough for a diamond");
        if (com.botwclimb.common.altar.AltarRules.refundCount(1.0f, 2f) != 1) h.fail("a credit that reaches half a diamond rounds up to one");
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
        for (int i = 0; i < 3; i++) tick(p, d, 1, 0, i == 0, false, false);
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
