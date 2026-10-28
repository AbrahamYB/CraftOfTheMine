package com.botwclimb.common.shrine;

import com.botwclimb.BotwClimb;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Where the plots are. The plot dimension is empty void (see data/botwclimb/dimension/plot.json); every plot is a grass island
 * of its own, {@link #SPACING} blocks from the next along x, with an unbreakable shrine terminal in the middle. Plot number n
 * is the n-th plot ever claimed on this world, whoever claimed it.
 */
public final class PlotWorld {
    public static final ResourceKey<Level> DIM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "plot"));
    /** Distance between two plots. Nothing stops a walker covering it; it is only far enough that nobody stumbles into another plot. */
    public static final int SPACING = 5120;
    /** Where plot 0 is: well away from the dimension's origin, the first place anyone would look. */
    public static final int ORIGIN_X = 48_016, ORIGIN_Z = -36_032;
    /** Height of the island's grass layer. */
    public static final int Y = 100;
    /** The island is a square of 16 chunks (4 x 4, 64 x 64 blocks) lined up with the chunk grid: it covers HALF blocks below its middle block and HALF - 1 above. */
    public static final int HALF = 32;
    /** The island is three layers: grass, dirt, deepslate. (An earlier, deeper island has the stone below them cleared out, down to OLD_DEPTH.) */
    private static final int OLD_DEPTH = 28;

    private PlotWorld() {}

    public static BlockPos terminalPos(int plot) {
        return new BlockPos(ORIGIN_X + plot * SPACING, Y + 1, ORIGIN_Z);
    }

    /** Which plot a position in the plot dimension lies in (the nearest middle). */
    public static int plotAt(BlockPos pos) {
        return Math.max(0, Math.round((pos.getX() - ORIGIN_X) / (float) SPACING));
    }

    public static boolean isTerminalSpot(BlockPos pos) {
        return pos.getY() == Y + 1 && pos.getZ() == ORIGIN_Z && pos.getX() >= ORIGIN_X && (pos.getX() - ORIGIN_X) % SPACING == 0;
    }

    /** True when the point lies over some plot's square. */
    public static boolean inSquare(double x, double z) {
        double dx = x - ORIGIN_X - Math.max(0, Math.round((x - ORIGIN_X) / SPACING)) * (double) SPACING, dz = z - ORIGIN_Z;
        return dx >= -HALF && dx < HALF && dz >= -HALF && dz < HALF;
    }

    /** The closest point of the square of the plot the position is nearest to, kept {@code margin} blocks inside its edge. */
    public static double[] clampToSquare(double x, double z, double margin) {
        double cx = ORIGIN_X + Math.max(0, Math.round((x - ORIGIN_X) / SPACING)) * (double) SPACING;
        return new double[] {Math.max(cx - HALF + margin, Math.min(cx + HALF - margin, x)), Math.max(ORIGIN_Z - HALF + margin, Math.min(ORIGIN_Z + HALF - margin, z))};
    }

    /** Whether the island is the 64 x 64 one of three layers (a plot made smaller or deeper is put right the next time someone travels there). */
    public static boolean isBuilt(ServerLevel level, int plot) {
        BlockPos corner = new BlockPos(ORIGIN_X + plot * SPACING + HALF - 1, Y, ORIGIN_Z + HALF - 1);
        return !level.getBlockState(corner).isAir() && level.getBlockState(corner.below(3)).isAir() && level.getBlockState(corner.below(2)).is(Blocks.DEEPSLATE);
    }

    /**
     * The island: a layer of grass over a layer of dirt over a layer of deepslate. Only air is filled (and the dirt or stone of an
     * older, deeper island is cleared), so nothing a player built is overwritten, and it can be run again to put an island right.
     */
    public static void buildIsland(ServerLevel level, int plot) {
        int cx = ORIGIN_X + plot * SPACING;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = cx - HALF; x < cx + HALF; x++) {
            for (int z = ORIGIN_Z - HALF; z < ORIGIN_Z + HALF; z++) {
                for (int d = 0; d <= OLD_DEPTH; d++) {
                    p.set(x, Y - d, z);
                    var now = level.getBlockState(p);
                    if (d <= 2) {
                        if (now.isAir() || d == 2 && (now.is(Blocks.DIRT) || now.is(Blocks.STONE))) {
                            level.setBlock(p, (d == 0 ? Blocks.GRASS_BLOCK : d == 1 ? Blocks.DIRT : Blocks.DEEPSLATE).defaultBlockState(), 2 | 16);
                        }
                    } else if (now.is(Blocks.DIRT) || now.is(Blocks.STONE)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                }
            }
        }
        ensureTerminal(level, plot);
    }

    /** The plot's terminal is the way home, so it comes back if anything removed it. */
    public static void ensureTerminal(ServerLevel level, int plot) {
        BlockPos at = terminalPos(plot);
        if (!level.getBlockState(at).is(BotwClimb.TERMINAL_BLOCK.get())) {
            level.setBlock(at, BotwClimb.TERMINAL_BLOCK.get().defaultBlockState(), 3);
        }
    }
}
