package com.botwclimb.client.shrine;

import com.botwclimb.client.altar.AltarScreen;
import com.botwclimb.common.shrine.PlotTravel;
import com.botwclimb.network.TerminalActionPayload;
import com.botwclimb.network.TerminalOpenPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The shrine terminal screen, in the style of the altar's: the player's plot (claim it, or go to it) beside a box of friends
 * whose plots are open to join, and, when the terminal stands in the plot dimension, a Return button and the outline switch.
 * A pick is only sent to the server, which checks it and starts the ride; the screen closes at once.
 */
public class TerminalScreen extends Screen {
    // One grid for everything: PAD at the panel's sides, the plot card and the friends box side by side with GAP between, so the panel is symmetrical.
    private static final int PAD = 14, GAP = 12, CARD_W = 96, CARD_H = 92, W = 2 * PAD + 2 * CARD_W + GAP;
    private static final int TITLE_Y = 12, CARD_Y = 30, BUTTON_H = 16, RETURN_H = 18, RETURN_GAP = 10, TOGGLE_H = 16, TOGGLE_GAP = 6, FRIEND_Y = 21, FRIEND_H = 16, FRIEND_STEP = 17, FRIEND_ROWS = 4;
    private static final int CARD = com.botwclimb.client.GlassUi.WELL, CARD_BORDER = com.botwclimb.client.GlassUi.WELL_EDGE, CARD_HOVER = com.botwclimb.client.GlassUi.WELL_HOVER;
    private static final int TEXT = 0xFFD2D8E0, MUTED = 0xFF7D8694, BRIGHT = 0xFFEDF1F6, GREEN = 0xFF00F25A, GREY = com.botwclimb.client.GlassUi.QUIET;

    private final TerminalOpenPayload state;
    /** The friends box scrolls like the altar's list: where it is heading (rows) and where it is drawn (eased toward that). */
    private int friendScroll;
    private float friendPos;
    private long friendLastFrame = net.minecraft.Util.getMillis();

    public TerminalScreen(TerminalOpenPayload state) {
        super(Component.translatable("gui.botwclimb.terminal.title"));
        this.state = state;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The rows under the cards, top to bottom: Return and the outline switch (inside a plot). */
    private static final int R_RETURN = 0, R_OUTLINE = 1;

    private java.util.List<int[]> rows() { // {kind}
        var l = new java.util.ArrayList<int[]>();
        if (state.inPlot()) { l.add(new int[] {R_RETURN}); l.add(new int[] {R_OUTLINE}); }
        return l;
    }

    private static int rowH(int[] r) {
        return r[0] == R_RETURN ? RETURN_H : TOGGLE_H;
    }

    private int rowY(int index) {
        int y = top() + CARD_Y + CARD_H + RETURN_GAP;
        var l = rows();
        for (int i = 0; i < index; i++) y += rowH(l.get(i)) + TOGGLE_GAP;
        return y;
    }

    private int panelHeight() {
        var l = rows();
        if (l.isEmpty()) return CARD_Y + CARD_H + PAD;
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

    /** The plot card's left edge (column 0) or the friends box's (column 1). */
    private int cardX(int column) {
        return left() + PAD + column * (CARD_W + GAP);
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt); // the game's own blur behind the glass
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        int x = left(), y = top(), ph = panelHeight();
        com.botwclimb.client.GlassUi.panel(g, x, y, W, ph);
        g.drawCenteredString(font, title, x + W / 2, y + TITLE_Y, BRIGHT);
        drawCard(g, mx, my);
        drawFriends(g, mx, my);
        var l = rows();
        for (int i = 0; i < l.size(); i++) drawRow(g, l.get(i), x + PAD, rowY(i), W - 2 * PAD, mx, my);
    }

    private void drawRow(GuiGraphics g, int[] r, int bx, int by, int bw, int mx, int my) {
        if (r[0] == R_RETURN) {
            boolean hover = over(mx, my, bx, by, bw, RETURN_H);
            com.botwclimb.client.GlassUi.button(g, bx, by, bw, RETURN_H, hover, true);
            g.drawCenteredString(font, Component.translatable("gui.botwclimb.terminal.return"), bx + bw / 2, by + (RETURN_H - 8) / 2, 0xFFFFFFFF);
        } else { // a switch: a quiet bar with the state at its right, lit green while on
            boolean on = PlotOutline.on(), hover = over(mx, my, bx, by, bw, TOGGLE_H);
            com.botwclimb.client.GlassUi.well(g, bx, by, bw, TOGGLE_H, hover, false);
            g.drawString(font, Component.translatable("gui.botwclimb.terminal.outline"), bx + 8, by + (TOGGLE_H - 8) / 2, TEXT, false);
            int sx = bx + bw - 8 - 22, sy = by + (TOGGLE_H - 8) / 2;
            g.fill(sx, sy, sx + 22, sy + 8, on ? GREEN : GREY);
            g.fill(on ? sx + 14 : sx, sy, on ? sx + 22 : sx + 8, sy + 8, on ? BRIGHT : MUTED);
        }
    }

    /** The player's plot: a little island on top, its name and state under it, and the button at the bottom, all on the card's centre line. */
    private void drawCard(GuiGraphics g, int mx, int my) {
        int x = cardX(0), y = top() + CARD_Y, cx = x + CARD_W / 2;
        boolean here = state.here(), has = state.claimed();
        boolean hover = !here && over(mx, my, x, y, CARD_W, CARD_H);
        com.botwclimb.client.GlassUi.well(g, x, y, CARD_W, CARD_H, hover, here);
        // the island: grass over earth that narrows to a point, dim until the plot is claimed; every row is symmetrical about cx
        int grass = has ? 0xFF2FA84F : 0xFF2A3038, earth = has ? 0xFF6B4A2E : 0xFF20252C, top = y + 20;
        g.fill(cx - 22, top, cx + 22, top + 4, grass);
        g.fill(cx - 20, top + 4, cx + 20, top + 8, earth);
        g.fill(cx - 14, top + 8, cx + 14, top + 11, earth);
        g.fill(cx - 7, top + 11, cx + 7, top + 14, earth);
        g.drawCenteredString(font, Component.translatable("gui.botwclimb.terminal.plot"), cx, y + 42, TEXT);
        if (has) { // the plot's friends switch: a line that lights green while friends may join
            boolean on = state.open();
            g.drawCenteredString(font, Component.translatable("gui.botwclimb.terminal.friends." + (on ? "on" : "off")), cx, y + 54, on ? GREEN : MUTED);
        } else g.drawCenteredString(font, Component.translatable("gui.botwclimb.terminal.unclaimed"), cx, y + 54, MUTED);
        int bx = x + 8, bw = CARD_W - 16, by = y + CARD_H - BUTTON_H - 8;
        com.botwclimb.client.GlassUi.button(g, bx, by, bw, BUTTON_H, hover, !here);
        String key = here ? "here" : has ? "go" : "claim";
        g.drawCenteredString(font, Component.translatable("gui.botwclimb.terminal." + key), cx, by + (BUTTON_H - 8) / 2, here ? 0xFF7F8998 : 0xFFFFFFFF);
    }

    /** Beside the plot card: the friends whose plots are open, a face and a name each, one click to join. */
    private void drawFriends(GuiGraphics g, int mx, int my) {
        int x = cardX(1), y = top() + CARD_Y, cx = x + CARD_W / 2;
        com.botwclimb.client.GlassUi.well(g, x, y, CARD_W, CARD_H, false, false);
        g.drawCenteredString(font, Component.translatable("gui.botwclimb.terminal.friends"), cx, y + 8, TEXT);
        var hosts = state.hosts();
        if (hosts.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.botwclimb.terminal.no_friends"), cx, y + CARD_H / 2 + 2, MUTED);
            return;
        }
        friendScroll = net.minecraft.util.Mth.clamp(friendScroll, 0, Math.max(0, hosts.size() - FRIEND_ROWS));
        long now = net.minecraft.Util.getMillis();
        float diff = friendScroll - friendPos;
        friendPos = Math.abs(diff) < 0.004f ? friendScroll : friendPos + diff * Math.min(1f, (now - friendLastFrame) / 70f);
        friendLastFrame = now;
        boolean bar = hosts.size() > FRIEND_ROWS;
        int ly = y + FRIEND_Y, viewH = (FRIEND_ROWS - 1) * FRIEND_STEP + FRIEND_H, rw = CARD_W - 8 - (bar ? 4 : 0);
        g.enableScissor(x + 1, ly, x + CARD_W - 1, ly + viewH);
        for (int i = (int) Math.floor(friendPos); i <= friendPos + FRIEND_ROWS && i < hosts.size(); i++) {
            var host = hosts.get(i);
            int rx = x + 4, ry = friendRowY(i);
            com.botwclimb.client.GlassUi.button(g, rx, ry, rw, FRIEND_H, over(mx, my, rx, ry, rw, FRIEND_H) && my >= ly && my < ly + viewH, true);
            var conn = net.minecraft.client.Minecraft.getInstance().getConnection();
            var info = conn == null ? null : conn.getPlayerInfo(host.id());
            net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, info != null ? info.getSkin() : net.minecraft.client.resources.DefaultPlayerSkin.get(host.id()), rx + 2, ry + 2, 12);
            g.drawString(font, font.plainSubstrByWidth(host.name(), rw - 22), rx + 18, ry + (FRIEND_H - 8) / 2, 0xFFFFFFFF, false);
        }
        g.disableScissor();
        if (bar) { // the scroll bar, as on the altar's list
            int barH = Math.max(10, viewH * FRIEND_ROWS / hosts.size()), barY = ly + Math.round((viewH - barH) * friendPos / (hosts.size() - FRIEND_ROWS));
            g.fill(x + CARD_W - 5, ly, x + CARD_W - 3, ly + viewH, GREY);
            g.fill(x + CARD_W - 5, barY, x + CARD_W - 3, barY + barH, MUTED);
        }
    }

    /** Where friend row i is drawn now, scrolled. */
    private int friendRowY(int i) {
        return top() + CARD_Y + FRIEND_Y + Math.round((i - friendPos) * FRIEND_STEP);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (over(mx, my, cardX(1), top() + CARD_Y, CARD_W, CARD_H)) {
            friendScroll += (int) -Math.signum(sy);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    //? if >=1.21.9 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return mouseClicked(event.x(), event.y(), event.button());
    }
    *///?}

    //? if <1.21.9
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (state.claimed() && over(mx, my, cardX(0), top() + CARD_Y + 50, CARD_W, 14)) { // the plot card's friends line; the server sends the screen back with the new state
                com.botwclimb.client.ClientCompat.sendToServer(new TerminalActionPayload(state.pos(), PlotTravel.TOGGLE_OPEN));
                return true;
            }
            if (!state.here() && over(mx, my, cardX(0), top() + CARD_Y, CARD_W, CARD_H)) return pick(PlotTravel.OWN);
            int ly = top() + CARD_Y + FRIEND_Y, viewH = (FRIEND_ROWS - 1) * FRIEND_STEP + FRIEND_H;
            if (my >= ly && my < ly + viewH) for (int i = 0; i < state.hosts().size(); i++) // a friend in the friends box (only where the box shows them)
                if (over(mx, my, cardX(1) + 4, friendRowY(i), CARD_W - 12, FRIEND_H)) return pick(PlotTravel.JOIN + i);
            var l = rows();
            for (int i = 0; i < l.size(); i++) {
                int[] r = l.get(i);
                if (!over(mx, my, left() + PAD, rowY(i), W - 2 * PAD, rowH(r))) continue;
                if (r[0] == R_RETURN) return pick(PlotTravel.RETURN);
                if (r[0] == R_OUTLINE) {
                    PlotOutline.toggle();
                    return true;
                }
            }
        }
        //? if >=1.21.9 {
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
