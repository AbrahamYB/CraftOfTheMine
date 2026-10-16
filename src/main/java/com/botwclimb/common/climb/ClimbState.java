package com.botwclimb.common.climb;

public enum ClimbState {
    NONE,
    CLIMBING,
    CLIMB_JUMP,
    LEDGE_TRANSITION;

    private static final ClimbState[] VALUES = values();

    public static ClimbState byId(int id) {
        return VALUES[Math.floorMod(id, VALUES.length)];
    }
}
