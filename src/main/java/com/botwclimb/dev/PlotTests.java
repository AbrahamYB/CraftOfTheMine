package com.botwclimb.dev;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.shrine.PlotData;
import com.botwclimb.common.shrine.PlotTravel;
import com.botwclimb.common.shrine.PlotWorld;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Dev-only headless checks of the plot dimension, the saved plot data and the terminal ride between worlds (run: gradlew runGameTestServer). */
@GameTestHolder(BotwClimb.MOD_ID)
@PrefixGameTestTemplate(false)
public class PlotTests {
    private static String read(GameTestHelper h, String path) {
        try (var in = PlotTests.class.getResourceAsStream(path)) {
            if (in == null) h.fail("missing resource " + path);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The plot dimension's two data files must decode with the game's own codecs (the test server has no plot dimension to load, so this is the check). */
    @GameTest(template = "empty")
    public static void plotDimensionFilesDecode(GameTestHelper h) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, h.getLevel().registryAccess());
        var type = DimensionType.DIRECT_CODEC.parse(ops, JsonParser.parseString(read(h, "/data/botwclimb/dimension_type/plot.json")));
        type.error().ifPresent(e -> h.fail("dimension_type/plot.json: " + e.message()));
        if (type.result().get().hasCeiling() || type.result().get().minY() != -64) h.fail("dimension type decoded to something else than written");
        // the dimension itself, with a vanilla type swapped in for ours (ours is only in the real game's registry)
        String stem = read(h, "/data/botwclimb/dimension/plot.json").replace("botwclimb:plot", "minecraft:overworld");
        var level = LevelStem.CODEC.parse(ops, JsonParser.parseString(stem));
        level.error().ifPresent(e -> h.fail("dimension/plot.json: " + e.message()));
        h.succeed();
    }

    /** The island is 64 wide (16 chunks) with the terminal in the middle, and a removed terminal comes back (built on the test level, far from anything). */
    @GameTest(template = "empty")
    public static void plotIslandAndTerminal(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        PlotWorld.buildIsland(level, 3);
        BlockPos t = PlotWorld.terminalPos(3);
        if (!level.getBlockState(t.below()).is(Blocks.GRASS_BLOCK)) h.fail("no island under the terminal");
        if (!level.getBlockState(new BlockPos((PlotWorld.ORIGIN_X + 3 * PlotWorld.SPACING) + 31, PlotWorld.Y, PlotWorld.ORIGIN_Z - 32)).is(Blocks.GRASS_BLOCK)) h.fail("island corner missing");
        if (!level.getBlockState(new BlockPos((PlotWorld.ORIGIN_X + 3 * PlotWorld.SPACING) + 32, PlotWorld.Y, PlotWorld.ORIGIN_Z)).isAir()) h.fail("island is wider than 64 blocks");
        if (!level.getBlockState(t).is(BotwClimb.TERMINAL_BLOCK.get())) h.fail("no terminal on the island");
        level.setBlock(t, Blocks.AIR.defaultBlockState(), 3);
        PlotWorld.ensureTerminal(level, 3);
        if (!level.getBlockState(t).is(BotwClimb.TERMINAL_BLOCK.get())) h.fail("a removed terminal did not come back");
        if (!PlotWorld.isTerminalSpot(t) || PlotWorld.isTerminalSpot(t.above()) || PlotWorld.isTerminalSpot(t.east())) h.fail("terminal spot test is wrong");
        if (!level.getBlockState(t.below(2)).is(Blocks.DIRT) || !level.getBlockState(t.below(3)).is(Blocks.DEEPSLATE) || !level.getBlockState(t.below(4)).isAir()) h.fail("the island should be grass, dirt, deepslate and nothing under that");
        if (!PlotWorld.inSquare((PlotWorld.ORIGIN_X + 3 * PlotWorld.SPACING) + 31.5, PlotWorld.ORIGIN_Z - 31.5) || PlotWorld.inSquare((PlotWorld.ORIGIN_X + 3 * PlotWorld.SPACING) + 32.5, PlotWorld.ORIGIN_Z) || PlotWorld.inSquare((PlotWorld.ORIGIN_X + 3 * PlotWorld.SPACING), PlotWorld.ORIGIN_Z + 32.5)) h.fail("square test is wrong");
        double[] c = PlotWorld.clampToSquare((PlotWorld.ORIGIN_X + 3 * PlotWorld.SPACING) + 50, PlotWorld.ORIGIN_Z - 50, 0.31);
        if (c[0] != (PlotWorld.ORIGIN_X + 3 * PlotWorld.SPACING) + 31.69 || c[1] != PlotWorld.ORIGIN_Z - 31.69) h.fail("clamp should land just inside the corner: " + java.util.Arrays.toString(c));
        if (PlotWorld.plotAt(t.offset(5, 0, 7)) != 3) h.fail("plotAt near plot 3 should be 3");
        h.succeed();
    }

    /** Where a ride home comes down: the spot it began at, less rise under a low roof, and the world spawn when the terminal is gone. */
    @GameTest(template = "empty")
    public static void returnArrival(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "plot-test"), ClientInformation.createDefault());
        BlockPos term = h.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(term.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(term, BotwClimb.TERMINAL_BLOCK.get().defaultBlockState(), 3);
        double x = term.getX() + 2.5, y = term.getY(), z = term.getZ() + 0.5;
        level.setBlock(BlockPos.containing(x, y - 1, z), Blocks.STONE.defaultBlockState(), 3);
        p.setPos(x, y, z);
        PlotData.get(level.getServer()).setReturn(p.getUUID(), new PlotData.Return(Level.OVERWORLD, term, x, y, z, 45f));
        PlotTravel.Arrival a = PlotTravel.arrival(p, PlotTravel.RETURN, 10);
        if (a == null || a.level() != level || a.x() != x || a.y() != y || a.z() != z || a.yaw() != 45f) h.fail("return should land where the ride began: " + a);
        if (a.rise() != 10) h.fail("open sky above: the whole rise of 10 should fit, got " + a.rise());
        level.setBlock(BlockPos.containing(x, y + 6, z), Blocks.STONE.defaultBlockState(), 3);
        a = PlotTravel.arrival(p, PlotTravel.RETURN, 10);
        if (a.rise() != 4) h.fail("a roof 6 blocks up leaves a rise of 4 for a 1.8 tall rider, got " + a.rise());
        level.setBlock(term, Blocks.AIR.defaultBlockState(), 3);
        a = PlotTravel.arrival(p, PlotTravel.RETURN, 0);
        if (a == null || a.level() != level.getServer().overworld() || a.x() == x) h.fail("with the terminal gone the ride should come down at the world spawn: " + a);
        if (PlotTravel.arrival(p, 0, 0) != null && level.getServer().getLevel(PlotWorld.DIM) == null) h.fail("a trip to a plot that was never claimed must have no destination");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void plotDataSaves(GameTestHelper h) {
        PlotData d = new PlotData();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        if (d.claim(a, 0) != 0 || d.claim(b, 0) != 1 || d.claim(a, 1) != 2) h.fail("plots are numbered in the order they are claimed");
        if (d.claim(a, 0) != 0) h.fail("claiming a claimed slot must keep its plot");
        if (d.plot(b, 1) != -1 || d.slotOf(a, 2) != 1 || d.slotOf(a, 1) != -1) h.fail("slot lookups are wrong");
        d.setReturn(a, new PlotData.Return(Level.OVERWORLD, new BlockPos(1, 2, 3), 1.5, 2.0, 3.5, 90f));
        PlotData back = PlotData.load(d.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
        if (back.plot(a, 0) != 0 || back.plot(a, 1) != 2 || back.plot(b, 0) != 1 || back.plot(b, 1) != -1) h.fail("plots were lost in saving");
        PlotData.Return r = back.returnPoint(a);
        if (r == null || !r.terminal().equals(new BlockPos(1, 2, 3)) || r.x() != 1.5 || r.yaw() != 90f || r.dim() != Level.OVERWORLD) h.fail("return point was lost in saving: " + r);
        if (back.claim(b, 1) != 3) h.fail("the plot counter was lost in saving");
        h.succeed();
    }

    /** The cocoon: closed it is the ellipsoid, a quarter turn open a petal lies flat and points outward, and every normal is a unit vector. */
    @GameTest(template = "empty")
    public static void cocoonShape(GameTestHelper h) {
        double[] p = new double[3], n = new double[3];
        double r = 1.2, cy = 0.9;
        for (int j = 0; j < com.botwclimb.common.shrine.CocoonMath.PETALS; j++) {
            double mid = j * 2 * Math.PI / com.botwclimb.common.shrine.CocoonMath.PETALS;
            com.botwclimb.common.shrine.CocoonMath.point(Math.PI / 2, mid, mid, 0, r, r, r, cy, 0, p);
            if (Math.abs(p[0] - r * Math.cos(mid)) > 1e-9 || Math.abs(p[1] - cy) > 1e-9 || Math.abs(p[2] - r * Math.sin(mid)) > 1e-9) h.fail("closed petal " + j + " is off the sphere: " + java.util.Arrays.toString(p));
            com.botwclimb.common.shrine.CocoonMath.point(Math.PI, mid, mid, 0, r, r, r, cy, 0, p);
            if (Math.abs(p[1] - (cy + r)) > 1e-9 || Math.hypot(p[0], p[2]) > 1e-9) h.fail("closed top is not on the axis");
            // a quarter turn open: the top of the petal lies flat at the height of the hinge, pointing out along the petal
            com.botwclimb.common.shrine.CocoonMath.point(Math.PI, mid, mid, Math.PI / 2, r, r, r, cy, 0, p);
            if (Math.abs(p[1] - (cy - r)) > 1e-9 || Math.abs(p[0] - 2 * r * Math.cos(mid)) > 1e-9 || Math.abs(p[2] - 2 * r * Math.sin(mid)) > 1e-9)
                h.fail("open petal " + j + " does not lie flat and outward: " + java.util.Arrays.toString(p));
            for (double open : new double[] {0, 0.6, 1.85}) {
                com.botwclimb.common.shrine.CocoonMath.normal(1.1, mid + 0.2, mid, open, r, r, r, 0.05, n);
                if (Math.abs(Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]) - 1) > 1e-9) h.fail("normal is not a unit vector");
            }
        }
        h.succeed();
    }
}
