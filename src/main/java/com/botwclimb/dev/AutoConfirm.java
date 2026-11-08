package com.botwclimb.dev;

import com.botwclimb.BotwClimb;
import net.minecraft.client.Minecraft;

/**
 * Dev only: the screenshot harness joins its world by itself, but newer Minecraft versions put a question in the way (a world from an
 * older version, a backup warning). This logs which screen is up while there is no level and answers a confirmation screen with its
 * first button, the way the player would, so the harness never waits for a click that nobody makes.
 */
final class AutoConfirm {
    private static int waited;

    private AutoConfirm() {}

    static void pump(Minecraft mc) {
        if (mc.level != null) return;
        var screen = mc.screen;
        if (screen == null) return;
        if (++waited % 100 == 1) BotwClimb.LOGGER.info("[auto] no level yet, screen {}", screen.getClass().getName());
        if (waited % 40 != 0) return;
        boolean backup = screen.getClass().getSimpleName().equals("BackupConfirmScreen"); // "back up and load", "load without a backup", "cancel": take the second
        if (!backup && !(screen instanceof net.minecraft.client.gui.screens.ConfirmScreen)) return;
        int want = backup ? 1 : 0, seen = 0;
        for (var child : screen.children()) {
            if (!(child instanceof net.minecraft.client.gui.components.Button b) || seen++ != want) continue;
            BotwClimb.LOGGER.info("[auto] answering {} with its button {}", screen.getClass().getSimpleName(), b.getMessage().getString());
            try {
                var f = net.minecraft.client.gui.components.Button.class.getDeclaredField("onPress");
                f.setAccessible(true);
                ((net.minecraft.client.gui.components.Button.OnPress) f.get(b)).onPress(b);
            } catch (ReflectiveOperationException e) {
                BotwClimb.LOGGER.warn("[auto] could not press it: {}", e.toString());
            }
            return;
        }
    }
}
