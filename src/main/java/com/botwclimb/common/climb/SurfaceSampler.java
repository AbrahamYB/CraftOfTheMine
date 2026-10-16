package com.botwclimb.common.climb;

import com.botwclimb.BotwClimb;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/** Short-range collision probing around an arbitrary player-sized box. Everything uses real VoxelShape collision. */
public final class SurfaceSampler {
    public static final TagKey<Block> NON_CLIMBABLE =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "non_climbable"));

    private SurfaceSampler() {}

    public static SurfaceContact sample(Level level, Entity e, AABB box, Direction dir) {
        double[] gaps = new double[3];
        for (int i = 0; i < 3; i++) {
            gaps[i] = bandGap(level, e, box, dir, box.minY + ClimbConstants.BANDS[i][0],
                    box.minY + ClimbConstants.BANDS[i][1], ClimbConstants.PROBE_DEPTH, null);
        }
        return new SurfaceContact(dir, gaps[0], gaps[1], gaps[2]);
    }

    /** True if the box overlaps any block collision (includes non-climbable blocks and the world border). */
    public static boolean blocked(Level level, Entity e, AABB box) {
        return level.getBlockCollisions(e, box).iterator().hasNext();
    }

    /**
     * Distance from the box face looking at {@code dir} to the nearest climbable collision in the slab
     * [y0,y1] ahead of that face, or NaN. If {@code hits} is given it receives the shapes found.
     */
    private static double bandGap(Level level, Entity e, AABB box, Direction dir, double y0, double y1,
                                  double depth, List<VoxelShape> hits) {
        double s = 0.02;
        boolean x = dir.getAxis() == Direction.Axis.X;
        AABB base = new AABB(box.minX + (x ? 0 : s), y0, box.minZ + (x ? s : 0),
                box.maxX - (x ? 0 : s), y1, box.maxZ - (x ? s : 0));
        AABB region = base.expandTowards(dir.getStepX() * depth, 0, dir.getStepZ() * depth);
        List<VoxelShape> shapes = new ArrayList<>(4);
        collect(level, e, region, shapes);
        if (shapes.isEmpty()) return Double.NaN;
        double step = dir.getAxisDirection().getStep() * depth;
        double moved = Shapes.collide(dir.getAxis(), base, shapes, step);
        double gap = Math.abs(moved);
        if (gap >= depth - 1.0E-7) return Double.NaN;
        if (hits != null) hits.addAll(shapes);
        return gap;
    }

    private static void collect(Level level, Entity e, AABB region, List<VoxelShape> out) {
        CollisionContext ctx = CollisionContext.of(e);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x0 = Mth.floor(region.minX), x1 = Mth.floor(region.maxX - 1.0E-7);
        int y0 = Mth.floor(region.minY), y1 = Mth.floor(region.maxY - 1.0E-7);
        int z0 = Mth.floor(region.minZ), z1 = Mth.floor(region.maxZ - 1.0E-7);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    pos.set(x, y, z);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState st = level.getBlockState(pos);
                    if (st.isAir() || st.is(NON_CLIMBABLE)) continue;
                    VoxelShape shape = st.getCollisionShape(level, pos, ctx);
                    if (shape.isEmpty()) continue;
                    out.add(shape.move(x, y, z));
                }
            }
        }
    }

    /**
     * Wall top reached: there is wall in the lower part of the body slab but none above it, and there is standing
     * room on top. Returns the Y of the surface to climb onto, or NaN.
     */
    public static double findLedge(Level level, Entity e, AABB box, Direction dir) {
        List<VoxelShape> lower = new ArrayList<>(4);
        double lowerGap = bandGap(level, e, box, dir, box.minY + 0.05, box.minY + 1.0, ClimbConstants.HOLD_GAP, lower);
        if (Double.isNaN(lowerGap)) return Double.NaN;
        double upperGap = bandGap(level, e, box, dir, box.minY + 1.0, box.maxY, ClimbConstants.HOLD_GAP, null);
        if (!Double.isNaN(upperGap)) return Double.NaN;
        double top = Double.NEGATIVE_INFINITY;
        for (VoxelShape s : lower) top = Math.max(top, s.max(Direction.Axis.Y));
        double rise = top - box.minY;
        if (rise < 0.1 || rise > 1.05) return Double.NaN;
        double fwd = lowerGap + 0.35;
        AABB dest = box.move(dir.getStepX() * fwd, rise, dir.getStepZ() * fwd);
        if (blocked(level, e, dest)) return Double.NaN;
        if (!blocked(level, e, dest.move(0, -0.06, 0))) return Double.NaN; // nothing to stand on
        return top;
    }
}
