package com.botwclimb.common.paraglider;

/**
 * Ordered key chord (modifier first, then the key) with single-press consumption.
 * The client feeds it real key events, which carry the modifier state at the instant of the press, so
 * "Space first, then Shift" never counts. A consumed press stays consumed until the key is released, because
 * vanilla treats Space as a new press whenever last tick's jump flag was false.
 */
public final class InputChord {
    private boolean pending;
    private boolean consumed;
    private boolean holding;
    private int holdTicks;

    /** @param modifiersHeld whether the chord's modifier was already down when the key went down */
    public void onPress(boolean modifiersHeld) {
        if (modifiersHeld) pending = true;
    }

    public void onRelease() {
        consumed = false;
        holding = false;
    }

    /** The press is ours: swallow it for the whole hold and start timing. */
    public void begin() {
        holding = true;
        holdTicks = 0;
        consumed = true;
    }

    public boolean holding() {
        return holding;
    }

    /** Call once per tick while holding. Returns true exactly once, when the key has been held long enough. */
    public boolean tickHold(boolean stillHeld, int ticksNeeded) {
        if (!holding) return false;
        if (!stillHeld) {
            holding = false;
            return false;
        }
        if (++holdTicks < ticksNeeded) return false;
        holding = false;
        return true;
    }

    /** True once per chord press. */
    public boolean takePending() {
        boolean p = pending;
        pending = false;
        return p;
    }

    public void consume() {
        consumed = true;
    }

    public boolean consumed() {
        return consumed;
    }

    /** Safety net for a release that was never seen (screen opened, very fast tap). */
    public void clearIfKeyUp(boolean keyPhysicallyDown) {
        if (!keyPhysicallyDown) {
            consumed = false;
            holding = false;
        }
    }
}
