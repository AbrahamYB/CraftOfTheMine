package com.botwclimb.dev;

import net.minecraft.gametest.framework.GameTestHelper;

/** GameTestHelper.fail takes a Component from 1.21.5 to 1.21.8 and a String before and after that. */
public final class TestCompat {
    private TestCompat() {}

    public static void fail(GameTestHelper helper, String message) {
        //? if >=1.21.5 && <1.21.9 {
        /*helper.fail(net.minecraft.network.chat.Component.literal(message));
        *///?} else {
        helper.fail(message);
        //?}
    }
}
