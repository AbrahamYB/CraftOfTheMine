package com.botwclimb.common.climb;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.botwclimb.common.climb.ClimbConstants.*;

/**
 * Plans a climb jump in the 2D plane (outward from the wall, up) that contains the player. A Dijkstra flood fill over
 * a coarse grid of player-sized boxes finds which cells the player can physically reach within a limited path length,
 * then picks the highest one that has a wall (or a wall top) to hold on to. Because the search is bounded by
 * {@link ClimbConstants#JUMP_MAX_OUT}, {@link ClimbConstants#JUMP_MAX_RISE} and {@link ClimbConstants#JUMP_MAX_PATH},
 * a 1-block lip can be swung around while a 3-block obstruction can not.
 */
public final class JumpPlanner {
    private JumpPlanner() {}

    private static final int NO = (int) Math.round(JUMP_MAX_OUT / JUMP_GRID) + 1;
    private static final int NY = (int) Math.round(JUMP_MAX_RISE / JUMP_GRID) + 1;

    public static JumpPlan plan(Level level, Entity e, Vec3 feet, Direction dir) {
        return plan(level, e, feet, dir, 0);
    }

    /** @param side -1 / 0 / +1: jump sideways towards the player's right / straight up / left (A or D held). */
    public static JumpPlan plan(Level level, Entity e, Vec3 feet, Direction dir, int side) {
        return plan(level, e, feet, dir, side, false);
    }

    /** @param sideOnly A / D without W: leap along the wall instead of up it (falls back to the normal jump). */
    public static JumpPlan plan(Level level, Entity e, Vec3 feet, Direction dir, int side, boolean sideOnly) {
        if (sideOnly && side != 0) {
            JumpPlan leap = sideLeap(level, e, e.getDimensions(Pose.STANDING).makeBoundingBox(feet), dir, side);
            if (leap != null) return leap;
        }
        AABB start = e.getDimensions(Pose.STANDING).makeBoundingBox(feet);
        Vec3 out = new Vec3(-dir.getStepX(), 0, -dir.getStepZ());

        AABB[] cell = new AABB[NO * NY];
        byte[] free = new byte[NO * NY]; // 0 unknown, 1 free, 2 blocked
        for (int o = 0; o < NO; o++) {
            for (int y = 0; y < NY; y++) {
                AABB b = start.move(out.x * o * JUMP_GRID, y * JUMP_GRID, out.z * o * JUMP_GRID);
                cell[o * NY + y] = o == 0 ? b : b.inflate(JUMP_CLEARANCE, 0, JUMP_CLEARANCE); // sideways margin only: heads may touch a ceiling
            }
        }

        double[] dist = new double[NO * NY];
        int[] parent = new int[NO * NY];
        boolean[] done = new boolean[NO * NY];
        java.util.Arrays.fill(dist, Double.MAX_VALUE);
        java.util.Arrays.fill(parent, -1);
        dist[0] = 0;
        free[0] = 1;

        while (true) {
            int cur = -1;
            for (int i = 0; i < dist.length; i++) {
                if (!done[i] && dist[i] < Double.MAX_VALUE && (cur < 0 || dist[i] < dist[cur])) cur = i;
            }
            if (cur < 0 || dist[cur] > JUMP_MAX_PATH) break;
            done[cur] = true;
            int co = cur / NY, cy = cur % NY;
            for (int dO = -1; dO <= 1; dO++) {
                for (int dY = -1; dY <= 1; dY++) {
                    if (dO == 0 && dY == 0) continue;
                    int no = co + dO, ny = cy + dY;
                    if (no < 0 || no >= NO || ny < 0 || ny >= NY) continue;
                    int nxt = no * NY + ny;
                    if (done[nxt]) continue;
                    double nd = dist[cur] + JUMP_GRID * Math.sqrt(dO * dO + dY * dY);
                    if (nd >= dist[nxt] || nd > JUMP_MAX_PATH) continue;
                    if (!isFree(level, e, cell, free, nxt)) continue;
                    AABB sweep = cell[cur].minmax(cell[nxt]);
                    if (SurfaceSampler.blocked(level, e, sweep)) continue;
                    dist[nxt] = nd;
                    parent[nxt] = cur;
                }
            }
        }

        int best = -1;
        double bestScore = -1;
        double bestLedge = Double.NaN;
        boolean bestIsLedge = false;
        for (int o = 0; o < NO; o++) {
            for (int y = 0; y < NY; y++) {
                int i = o * NY + y;
                double h = y * JUMP_GRID;
                if (!done[i] || h < JUMP_MIN_GAIN) continue;
                // plain jumps stop at JUMP_OPEN_RISE; only a detour round something may use the full reach
                if (h > JUMP_OPEN_RISE + 1.0E-6 && dist[i] < h + 0.5) continue;
                AABB b = start.move(out.x * o * JUMP_GRID, h, out.z * o * JUMP_GRID);
                boolean wall = o * JUMP_GRID <= JUMP_MAX_WALL_OUT && SurfaceSampler.sample(level, e, b, dir).holds(REGRAB_GAP);
                double ledge = SurfaceSampler.findLedge(level, e, b, dir);
                boolean isLedge = !Double.isNaN(ledge);
                if (!wall && !isLedge) continue;
                double score = h + (isLedge ? 0.5 : 0) - 0.15 * o * JUMP_GRID - 0.02 * dist[i];
                if (best < 0 || score > bestScore) {
                    best = i;
                    bestScore = score;
                    bestLedge = ledge;
                    bestIsLedge = isLedge;
                }
            }
        }

        if (best >= 0) {
            List<Vec3> poly = new ArrayList<>();
            for (int i = best; i >= 0; i = parent[i]) {
                int o = i / NY, y = i % NY;
                poly.add(0, new Vec3(out.x * o * JUMP_GRID, y * JUMP_GRID, out.z * o * JUMP_GRID));
            }
            Vec3[] steps = resample(poly);
            if (verify(level, e, start, steps)) {
                JumpPlan.Goal goal = bestIsLedge ? JumpPlan.Goal.LEDGE : JumpPlan.Goal.WALL;
                if (side != 0) steps = withSide(level, e, start, dir, steps, side, goal);
                return new JumpPlan(steps, goal, bestLedge);
            }
        }
        return hop(level, e, start, out);
    }

    /**
     * Drifts the finished jump sideways along the wall (the eased progress of each tick decides how much), as far as
     * the geometry allows: every tick must stay free and the end must still be on a wall / ledge.
     */
    private static Vec3[] withSide(Level level, Entity e, AABB start, Direction dir, Vec3[] steps, int side,
                                   JumpPlan.Goal goal) {
        Direction left = dir.getCounterClockWise();
        for (double scale : new double[]{1.0, 0.6, 0.3}) {
            double reach = side * JUMP_SIDE_REACH * scale;
            Vec3[] out = new Vec3[steps.length];
            boolean ok = true;
            for (int k = 0; k < steps.length && ok; k++) {
                double frac = 1 - Math.pow(1 - (double) (k + 1) / steps.length, 1.6);
                out[k] = steps[k].add(left.getStepX() * reach * frac, 0, left.getStepZ() * reach * frac);
                ok = !SurfaceSampler.blocked(level, e, start.move(out[k]).deflate(0.01));
            }
            if (!ok) continue;
            AABB end = start.move(out[out.length - 1]);
            boolean grab = goal == JumpPlan.Goal.LEDGE
                    ? !Double.isNaN(SurfaceSampler.findLedge(level, e, end, dir))
                    : SurfaceSampler.sample(level, e, end, dir).holds(REGRAB_GAP);
            if (grab) return out;
        }
        return steps; // nothing to hold on to sideways: jump straight up instead
    }

    /** A low arc along the wall: JUMP_SIDE_LEAP blocks sideways, a little up, ending on the wall again. */
    private static JumpPlan sideLeap(Level level, Entity e, AABB start, Direction dir, int side) {
        Direction left = dir.getCounterClockWise();
        Vec3 out = new Vec3(-dir.getStepX(), 0, -dir.getStepZ());
        int ticks = 8;
        for (double scale : new double[]{1.0, 0.67, 0.33}) {
            Vec3[] steps = new Vec3[ticks];
            boolean ok = true;
            for (int k = 1; k <= ticks && ok; k++) {
                double u = (double) k / ticks;
                double lat = side * JUMP_SIDE_LEAP * scale * (1 - Math.pow(1 - u, 1.6));
                double up = JUMP_SIDE_RISE * Math.sin(Math.PI * u); // a flat slide along the wall, not a hop
                double o = 0.12 * Math.sin(Math.PI * u);
                steps[k - 1] = new Vec3(left.getStepX() * lat + out.x * o, up, left.getStepZ() * lat + out.z * o);
                ok = !SurfaceSampler.blocked(level, e, start.move(steps[k - 1]).deflate(0.01));
            }
            if (ok && SurfaceSampler.sample(level, e, start.move(steps[ticks - 1]), dir).holds(REGRAB_GAP)) {
                return new JumpPlan(steps, JumpPlan.Goal.WALL, Double.NaN);
            }
        }
        return null;
    }

    private static boolean isFree(Level level, Entity e, AABB[] cell, byte[] free, int i) {
        if (free[i] == 0) free[i] = (byte) (SurfaceSampler.blocked(level, e, cell[i]) ? 2 : 1);
        return free[i] == 1;
    }

    /** Evenly-eased walk along the polyline, so the jump bursts off the wall and slows into the grab. */
    private static Vec3[] resample(List<Vec3> poly) {
        double total = 0;
        for (int i = 1; i < poly.size(); i++) total += poly.get(i).distanceTo(poly.get(i - 1));
        int ticks = Mth.clamp((int) Math.ceil(total / JUMP_AVG_SPEED), JUMP_MIN_TICKS, JUMP_MAX_TICKS);
        Vec3[] steps = new Vec3[ticks];
        for (int k = 1; k <= ticks; k++) {
            double u = (double) k / ticks;
            double s = total * (1 - Math.pow(1 - u, 1.6));
            steps[k - 1] = pointAt(poly, s);
        }
        return steps;
    }

    private static Vec3 pointAt(List<Vec3> poly, double s) {
        for (int i = 1; i < poly.size(); i++) {
            Vec3 a = poly.get(i - 1), b = poly.get(i);
            double len = a.distanceTo(b);
            if (s <= len || i == poly.size() - 1) {
                return len < 1.0E-9 ? b : a.lerp(b, Math.min(1, s / len));
            }
            s -= len;
        }
        return poly.get(poly.size() - 1);
    }

    /**
     * Every tick position must be free. In between, vanilla {@code move()} resolves collision axis by axis (Y first),
     * so a tick that grazes a corner is clipped instead of cutting through it.
     */
    private static boolean verify(Level level, Entity e, AABB start, Vec3[] steps) {
        for (Vec3 p : steps) {
            if (SurfaceSampler.blocked(level, e, start.move(p).deflate(0.01))) return false;
        }
        return true;
    }

    /** Nothing to grab: go as high as the space allows with a small push away from the wall, then fall. */
    private static JumpPlan hop(Level level, Entity e, AABB start, Vec3 out) {
        double rise = 0;
        for (double y = JUMP_GRID; y <= HOP_RISE + 1.0E-6; y += JUMP_GRID) {
            AABB sweep = start.minmax(start.move(0, y, 0));
            if (SurfaceSampler.blocked(level, e, sweep)) break;
            rise = y;
        }
        int ticks = 8;
        Vec3[] steps = new Vec3[ticks];
        for (int k = 1; k <= ticks; k++) {
            double u = (double) k / ticks;
            double o = HOP_OUT * Math.sin(Math.PI * u);
            steps[k - 1] = new Vec3(out.x * o, rise * (1 - Math.pow(1 - u, 1.6)), out.z * o);
        }
        if (!verify(level, e, start, steps)) {
            for (int k = 0; k < ticks; k++) steps[k] = new Vec3(0, steps[k].y, 0);
        }
        return new JumpPlan(steps, JumpPlan.Goal.NONE, Double.NaN);
    }
}
