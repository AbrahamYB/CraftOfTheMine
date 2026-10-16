package com.botwclimb.common.climb;

import net.minecraft.core.Direction;

/** Result of probing one wall direction at legs / torso / head height. A gap is NaN when no wall was found. */
public record SurfaceContact(Direction dir, double legs, double torso, double head) {

    private static boolean has(double gap, double max) {
        return !Double.isNaN(gap) && gap <= max;
    }

    /** Enough wall to keep hanging on: torso plus one more band, or legs plus head. */
    public boolean holds(double maxGap) {
        boolean l = has(legs, maxGap), t = has(torso, maxGap), h = has(head, maxGap);
        return (t && (l || h)) || (l && h);
    }

    /** Stricter test used to grab a wall from the ground or the air. */
    public boolean canAttach(double maxGap) {
        return has(torso, maxGap) && has(head, maxGap);
    }

    /** Best estimate of the distance from the player's wall-side face to the wall. */
    public double gap() {
        if (!Double.isNaN(torso)) return torso;
        if (!Double.isNaN(head)) return head;
        return legs;
    }

    public boolean any() {
        return !Double.isNaN(legs) || !Double.isNaN(torso) || !Double.isNaN(head);
    }
}
