package com.botwclimb.client.shrine;

import com.botwclimb.client.altar.AltarScreen;
import com.botwclimb.common.shrine.PlotData;
import com.botwclimb.common.shrine.PlotTravel;
import com.botwclimb.network.TerminalActionPayload;
import com.botwclimb.network.TerminalOpenPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The shrine terminal screen, in the style of the altar's: two plot cards (claim an empty one, go to a claimed one) and, when
 * the terminal stands in the plot dimension, a Return button. A pick is only sent to the server, which checks it and starts
 * the ride; the screen closes at once.
 */
public class TerminalScreen extends Screen {
    // One grid for everything: PAD at the panel's sides, the two cards side by side with GAP between, so the panel is symmetrical.
    private static final int PAD = 14, GAP = 12, CARD_W = 84, CARD_H = 92, W = 2 * PAD + 2 * CARD_W + GAP;
    private static final int TITLE_Y = 12, CARD_Y = 30, BUTTON_H = 16, RETURN_H = 18, RETURN_GAP = 10, TOGGLE_H = 16, TOGGLE_GAP = 6, JOIN_H = 22;
    private static final int PANEL = 0xFF090B0E, BORDER = 0xFF171B21, CARD = 0xFF0F1216, CARD_BORDER = 0xFF242A32, CARD_HOVER = 0xFF3A424D;
    private static final int TEXT = 0xFFD2D8E0, MUTED = 0xFF7D8694, BRIGHT = 0xFFEDF1F6, GREEN = 0xFF00F25A, GREY = 0xFF1A1E24;
    private static final int BUTTON = 0xFF087F3C, BUTTON_HOVER = 0xFF0E9A47;

    private final TerminalOpenPayload state;

    public TerminalScreen(TerminalOpenPayload state) {
        super(Component.translatable("gui.botwclimb.terminal.title"));
        this.state = state;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The rows under the cards, top to bottom: Return and the outline switch (inside a plot), the friends switch, and one join button per open friend. */
    private static final int R_RETURN = 0, R_OUTLINE = 1, R_JOIN = 3;

    private java.util.List<int[]> rows() { // {kind, host index}
        var l = new java.util.ArrayList<int[]>();
        if (state.inPlot()) { l.add(new int[] {R_RETURN, 0}); l.add(new int[] {R_OUTLINE, 0}); }
        for (int i = 0; i < state.hosts().size(); i++) l.add(new int[] {R_JOIN, i});
        return l;
    }

    private static int rowH(int[] r) {
        return r[0] == R_RETURN ? RETURN_H : r[0] == R_JOIN ? JOIN_H : TOGGLE_H;
    }

    private int rowY(int index) {
        int y = top() + CARD_Y + CARD_H + RETURN_GAP;
        var l = rows();
        for (int i = 0; i < index; i++) y += rowH(l.get(i)) + TOGGLE_GAP;
        return y;
    }

    private int panelHeight() {
        var l = rows();
        int h = CARD_Y + CARD_H + RETURN_GAP + PAD;
        for (int[] r : l) h += rowH(r) + TOGGLE_GAP;
        return h - TOGGLE_GAP;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - panelHeight()) / 2;
    }

    private int cardX(int slot) {
        return left() + PAD + slot * (CARD_W + GAP);
    }

    private boolean claimed(int slot) {
        return (state.claimed() >> slot & 1) != 0;
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xBB000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        int x = left(), y = top(), ph = panelHeight();
        g.fill(x, y, x + W, y + ph, BORDER);
        g.fill(x + 1, y + 1, x + W - 1, y + ph - 1, PANEL);
        AltarScreen.vignette(g, x + 1, y + 1, W - 2, ph - 2);
        g.drawCenteredString(font, title, x + W / 2, y + TITLE_Y, BRIGHT);
        for (int s = 0; s < PlotData.SLOTS; s++) drawCard(g, s, mx, my);
        var l = rows();
        for (int i = 0; i < l.size(); i++) drawRow(g, l.get(i), x + PAD, rowY(i), W - 2 * PAD, mx, my);
    }

    private void drawRow(GuiGraphics g, int[] r, int bx, int by, int bw, int mx, int my) {
        if (r[0] == R_RETURN) {
            boolean hover = over(mx, my, bx, by, bw, RETURN_H);
            g.fill(bx, by, bx + bw, by + RETURN_H, hover ? BUTTON_HOVER : BUTTON);
            g.fill(bx, by, bx + bw, by + 1, 0x3300F25A);
            g.drawCenteredString(font, Component.translatable("gui.botwclimb.terminal.return"), bx + bw / 2, by + (RETURN_H - 8) / 2, 0xFFFFFFFF);
        } else if (r[0] == R_JOIN) {
            // a friend whose plot is open: their face, their name, and which of their plots when they have two open
            var host = state.hosts().get(r[1]);
            boolean hover = over(mx, my, bx, by, bw, JOIN_H);
            g.fill(bx, by, bx + bw, by + JOIN_H, hover ? BUTTON_HOVER : BUTTON);
            var info = net.minecraft.client.Minecraft.getInstance().getConnection() == null ? null : net.minecraft.client.Minecraft.getInstance().getConnection().getPlayerInfo(host.id());
            net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, info != null ? info.getSkin() : net.minecraft.client.resources.DefaultPlayerSkin.get(host.id()), bx + 3, by + 3, 16);
            boolean two = state.hosts().stream().filter(h -> h.id().equals(host.id())).count() > 1;
            g.drawString(font, Component.translatable("gui.botwclimb.terminal.join", two ? host.name() + " - " + (host.slot() + 1) : host.name()), bx + 24, by + (JOIN_H - 8) / 2, 0xFFFFFFFF, false);
        } else { // a switch: a quiet bar with the state at its right, lit green while on
            boolean on = PlotOutline.on(), hover = over(mx, my, bx, by, bw, TOGGLE_H);
            g.fill(bx, by, bx + bw, by + TOGGLE_H, hover ? CARD_HOVER : CARD_BORDER);
            g.fill(bx + 1, by + 1, bx + bw - 1, by + TOGGLE_H - 1, CARD);
            g.drawString(font, Component.translatable("gui.botwclimb.terminal.outline"), bx + 8, by + (TOGGLE_H - 8) / 2, TEXT, false);
            int sx = bx + bw - 8 - 22, sy = by + (TOGGLE_H - 8) / 2;
            g.fill(sx, sy, sx + 22, sy + 8, on ? GREEN : GREY);
            g.fill(on ? sx + 14 : sx, sy, on ? sx + 22 : sx + 8, sy + 8, on ? BRIGHT : MUTED);
        }
    }

    /** One plot: a little island on top, its name and state under it, and the button at the bottom, all on the card's centre line. */
    private void drawCard(GuiGraphics g, int slot, int mx, int my) {
        int x = cardX(slot), y = top() + CARD_Y, cx = x + CARD_W / 2;
        boolean here = state.here() == slot, has = claimed(slot);
        boolean hover = !here && over(mx, my, x, y, CARD_W, CARD_H);
        g.fill(x, y, x + CARD_W, y + CARD_H, here ? GREEN : hover ? CARD_HOVER : CARD_BORDER);
        g.fill(x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1, CARD);
        // the island: grass over earth that narrows to a point, dim until the plot is claimed; every row is symmetrical about cx
        int grass = has ? 0xFF2FA84F : 0xFF2A3038, earth = has ? 0xFF6B4A2E : 0xFF20252C, top = y + 20;
        g.fill(cx - 22, top, cx + 22, top + 4, grass);
        g.fill(cx - 20, top + 4, cx + 20, top + 8, earth);
        g.fill(cx - 14, top + 8, cx + 14, top + 11, earth);
        g.fill(cx - 7, top + 11, cx + 7, top + 14, earth);
        if (has) g.fill(cx - 2, top - 6, cx + 2, top, GREEN); // the terminal standing on it
        else { // a plus: this one can be claimed
            g.fill(cx - 4, top - 5, cx + 4, top - 3, MUTED);
            g.fill(cx - 1, top - 8, cx + 1, top, MUTED);
        }
        g.drawCenteredString(font, Component.translatable("gui.botwclimb.terminal.plot", slot + 1), cx, y + 42, TEXT);
        if (has) { // the plot's own friends switch: a line that lights green while friends may join this plot
            boolean on = (state.open() >> slot & 1) != 0;
            g.drawCenteredString(font, Component.translatable("gui.botwclimb.terminal.friends." + (on ? "on" : "off")), cx, y + 54, on ? GREEN : MUTED);
        } else g.drawCenteredString(font, Component.translatable("gui.botwclimb.terminal.unclaimed"), cx, y + 54, MUTED);
        int bx = x + 8, bw = CARD_W - 16, by = y + CARD_H - BUTTON_H - 8;
        g.fill(bx, by, bx + bw, by + BUTTON_H, here ? GREY : hover ? BUTTON_HOVER : BUTTON);
        String key = here ? "here" : has ? "go" : "claim";
        g.drawCenteredString(font, Component.translatable("gui.botwclimb.terminal." + key), cx, by + (BUTTON_H - 8) / 2, here ? 0xFF7F8998 : 0xFFFFFFFF);
    }

    //? if >=1.21.11 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return mouseClicked(event.x(), event.y(), event.button());
    }
    *///?}

    //? if <1.21.11
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int s = 0; s < PlotData.SLOTS; s++) {
                if (claimed(s) && over(mx, my, cardX(s), top() + CARD_Y + 50, CARD_W, 14)) { // the friends line of a card; the server sends the screen back with the new state
                    com.botwclimb.client.ClientCompat.sendToServer(new TerminalActionPayload(state.pos(), PlotTravel.TOGGLE_OPEN + s));
                    return true;
                }
                if (state.here() != s && over(mx, my, cardX(s), top() + CARD_Y, CARD_W, CARD_H)) return pick(s);
            }
            var l = rows();
            for (int i = 0; i < l.size(); i++) {
                int[] r = l.get(i);
                if (!over(mx, my, left() + PAD, rowY(i), W - 2 * PAD, rowH(r))) continue;
                if (r[0] == R_RETURN) return pick(PlotTravel.RETURN);
                if (r[0] == R_JOIN) return pick(PlotTravel.JOIN + r[1]);
                if (r[0] == R_OUTLINE) {
                    PlotOutline.toggle();
                    return true;
                }
            }
        }
        //? if >=1.21.11 {
        /*return super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mx, my, new net.minecraft.client.input.MouseButtonInfo(button, 0)), false);
        *///?} else {
        return super.mouseClicked(mx, my, button);
        //?}
    }

    private boolean pick(int target) {
        com.botwclimb.client.ClientCompat.sendToServer(new TerminalActionPayload(state.pos(), target));
        onClose();
        return true;
    }
}
