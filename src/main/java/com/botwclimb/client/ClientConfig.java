package com.botwclimb.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The mod's settings for this player (client side, saved in config/botwclimb-client.toml). Everything is read through the
 * static getters, which fall back to the default while the file is not loaded yet, and written through {@link #set}, which saves.
 * The settings screen (BotwConfigScreen) is the way to change them.
 */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue MOTION_BLUR;
    private static final ModConfigSpec.BooleanValue PLOT_OUTLINE, GLIDER_CAMERA;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("ride");
        MOTION_BLUR = b.comment("Motion blur while riding, in percent (0 turns it off)").defineInRange("motionBlur", 75, 0, 100);
        b.pop();
        b.push("glider");
        GLIDER_CAMERA = b.comment("Switch to third person by itself while gliding (off: the view you picked, with F5 or first person, is left alone)").define("autoThirdPerson", true);
        b.pop();
        b.push("plots");
        PLOT_OUTLINE = b.comment("Draw the outline of the plot's 16 chunks while you are inside a plot").define("outline", true);
        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {}

    /** 0..1 */
    public static float motionBlur() {
        return get(MOTION_BLUR, 75) / 100f;
    }


    public static boolean gliderCamera() {
        return get(GLIDER_CAMERA, true);
    }

    public static ModConfigSpec.BooleanValue gliderCameraValue() {
        return GLIDER_CAMERA;
    }

    public static boolean plotOutline() {
        return get(PLOT_OUTLINE, true);
    }

    private static <T> T get(ModConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) { // the file is not loaded yet
            return fallback;
        }
    }

    // ---- the settings screen's handles on the values: read, write (and save) and reset

    public static ModConfigSpec.IntValue motionBlurValue() {
        return MOTION_BLUR;
    }

    public static ModConfigSpec.BooleanValue plotOutlineValue() {
        return PLOT_OUTLINE;
    }

    public static <T> void set(ModConfigSpec.ConfigValue<T> value, T v) {
        value.set(v);
        SPEC.save();
    }

    public static void resetAll() {
        for (ModConfigSpec.ConfigValue<?> v : new ModConfigSpec.ConfigValue<?>[] {MOTION_BLUR, PLOT_OUTLINE, GLIDER_CAMERA}) reset(v);
        SPEC.save();
    }

    private static <T> void reset(ModConfigSpec.ConfigValue<T> v) {
        v.set(v.getDefault());
    }
}
