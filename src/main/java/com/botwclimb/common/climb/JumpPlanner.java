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
                return new JumpPlan(steps, bestIsLedge ? JumpPlan.Goal.LEDGE : JumpPlan.Goal.WALL, bestLedge);
            }
        }
        return hop(level, e, start, out);
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
