package com.botwclimb.common.climb;

import net.minecraft.world.phys.Vec3;

/**
 * A short collision-checked trajectory for one climb jump. {@code steps[k]} is the player's feet position after
 * tick k+1, relative to the jump start.
 */
public record JumpPlan(Vec3[] steps, Goal goal, double ledgeY) {

    public enum Goal {
        /** Nothing climbable reachable: the jump is a plain hop and the player falls unless the old wall is still there. */
        NONE,
        /** Ends against a wall that can be grabbed again. */
        WALL,
        /** Ends beside the top edge of the wall with room to climb onto it. */
        LEDGE
    }

    public int length() {
        return steps.length;
    }

    public Vec3 end() {
        return steps.length == 0 ? Vec3.ZERO : steps[steps.length - 1];
    }
}
