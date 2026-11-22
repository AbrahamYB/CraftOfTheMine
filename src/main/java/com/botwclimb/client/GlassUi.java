package com.botwclimb.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The look of the mod's panels (altar, terminal, the guide's table): smoked glass over the world instead of solid black, a thin
 * silver edge in the plot outline's silver with brighter corners, and translucent green buttons.
 */
public final class GlassUi {
    private GlassUi() {}

    public static final int SILVER = 0xE8EEF2;
    /** Wells inside a panel (slots, cards, rows): a little darker glass, a faint silver edge, brighter when hovered. */
    public static final int WELL = 0x46000000, WELL_EDGE = 0x3AE8EEF2, WELL_HOVER = 0x9AE8EEF2;
    /** Tracks and disabled things. */
    public static final int QUIET = 0x33FFFFFF;

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        panel(g, x, y, w, h, 1f);
    }

    /** A panel, faded by {@code a} (0 to 1). */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, float a) {
        g.fillGradient(x, y, x + w, y + h, fade(0xC4151D25, a), fade(0xB80A0F14, a));
        g.fill(x + 1, y + 1, x + w - 1, y + 2, fade(0x1EFFFFFF, a)); // a sheen along the top
        int edge = fade(0x66000000 | SILVER, a), corner = fade(0xE0000000 | SILVER, a);
        g.fill(x, y, x + w, y + 1, edge);
        g.fill(x, y + h - 1, x + w, y + h, edge);
        g.fill(x, y + 1, x + 1, y + h - 1, edge);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, edge);
        int c = Math.min(6, Math.min(w, h) / 4); // brighter corners
        g.fill(x, y, x + c, y + 1, corner);
        g.fill(x, y, x + 1, y + c, corner);
        g.fill(x + w - c, y, x + w, y + 1, corner);
        g.fill(x + w - 1, y, x + w, y + c, corner);
        g.fill(x, y + h - 1, x + c, y + h, corner);
        g.fill(x, y + h - c, x + 1, y + h, corner);
        g.fill(x + w - c, y + h - 1, x + w, y + h, corner);
        g.fill(x + w - 1, y + h - c, x + w, y + h, corner);
    }

    /** A well: darker glass with a silver edge, brighter when hovered, green when picked. */
    public static void well(GuiGraphics g, int x, int y, int w, int h, boolean hover, boolean picked) {
        int edge = picked ? 0xD000F25A : hover ? WELL_HOVER : WELL_EDGE;
        g.fill(x, y, x + w, y + h, WELL);
        g.fill(x, y, x + w, y + 1, edge);
        g.fill(x, y + h - 1, x + w, y + h, edge);
        g.fill(x, y + 1, x + 1, y + h - 1, edge);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, edge);
    }

    /** A button: translucent green with a light top edge, brighter when hovered; quiet glass when it cannot be pressed. */
    public static void button(GuiGraphics g, int x, int y, int w, int h, boolean hover, boolean enabled) {
        if (!enabled) {
            g.fill(x, y, x + w, y + h, QUIET);
            return;
        }
        g.fillGradient(x, y, x + w, y + h, hover ? 0xE61BC062 : 0xD012A04E, hover ? 0xE60E9A47 : 0xD0087F3C);
        g.fill(x, y, x + w, y + 1, hover ? 0x88FFFFFF : 0x55FFFFFF);
    }

    public static int fade(int argb, float a) {
        return ((int) ((argb >>> 24) * Math.max(0f, Math.min(1f, a))) << 24) | (argb & 0xFFFFFF);
    }
}
