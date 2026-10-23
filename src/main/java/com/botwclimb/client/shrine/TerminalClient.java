package com.botwclimb.client.shrine;

import com.botwclimb.network.TerminalOpenPayload;
import net.minecraft.client.Minecraft;

/** Client entry point for the shrine terminal: the server says the player used one, so its screen opens. */
public final class TerminalClient {
    private TerminalClient() {}

    public static void open(TerminalOpenPayload p) {
        Minecraft.getInstance().setScreen(new TerminalScreen(p));
    }
}
