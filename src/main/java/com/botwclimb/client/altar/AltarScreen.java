package com.botwclimb.client.altar;

import com.botwclimb.BotwClimb;
import com.botwclimb.client.StaminaHud;
import com.botwclimb.common.altar.AltarRules;
import com.botwclimb.common.altar.AltarValues;
import com.botwclimb.common.climb.ClimbConstants;
import com.botwclimb.network.AltarOfferPayload;
import com.botwclimb.network.AltarResultPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The stamina altar screen. Nothing physically moves: you pick items from your inventory into nine offering slots (click
 * an empty slot to open a scrolling list of what you can offer), see the total against the price, and the server takes
 * the items when you confirm. Click a filled slot to take an item back out of the offer. At the last upgrade the same
 * screen lets you take leftover credit back as an item of your choice.
 */
public class AltarScreen extends Screen {
    private static final int W = 190, H = 224, GRID_Y = 116, SLOT = 22, GAP = 2, REFUND_Y = 128; // H is the height while upgrades are left; a finished altar needs far less
    // a darker tone than before; the vignette at the panel's edges is drawn in vignette()
    private static final int PANEL = 0xFF090B0E, BORDER = 0xFF171B21, SLOT_BG = 0xFF0F1216, SLOT_BORDER = 0xFF242A32, EMPTY_BORDER = 0xFF171B21;
    private static final int TEXT = 0xFFD2D8E0, MUTED = 0xFF7D8694, BRIGHT = 0xFFEDF1F6, GREEN = 0xFF00F25A, GREY = 0xFF1A1E24, AMBER = 0xFFFFD34D, DANGER = 0xFFFF6B5B;
    private static final int ROW_H = 16, ROWS = 5, PW = 170, PH = 16 + ROWS * ROW_H + 4;

    private final BlockPos pos;
    private final AltarOffer[] offer = new AltarOffer[9];
    private int pickerFor = -1;     // the offering slot the list is open for
    private long pickerOpenAt;      // when it opened, for the pop-in
    private int pickerScroll;       // where the list is heading, in rows
    private float pickerPos;        // where it is now, so scrolling glides
    private long pickerLastFrame;
    private float pickerPivotX, pickerPivotY; // the slot it grew out of
    private int refundScroll;
    private String message = "";

    /** One offering slot: which inventory slot, how many, what one is worth. */
    private record AltarOffer(int invSlot, int count, float unit, ItemStack icon) {}

    /** One row in the picker: an inventory stack that can still be offered. */
    private record Row(int invSlot, ItemStack stack, int available, float unit) {}

    public AltarScreen(BlockPos pos) {
        super(Component.translatable("gui.botwclimb.altar.title"));
        this.pos = pos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        AltarFx.onMenuClosed();
    }

    public void onResult(AltarResultPayload r) {
        if (r.ok()) {
            java.util.Arrays.fill(offer, null);
            pickerFor = -1;
            message = "";
        } else {
            message = Component.translatable("gui.botwclimb.altar.err." + r.reason()).getString();
        }
    }

    // ------------------------------------------------------------------ state

    private LocalPlayer me() {
        return Minecraft.getInstance().player;
    }

    private int upgrades() {
        return AltarFx.upgrades(me());
    }

    private float credit() {
        return Math.max(0f, me().getData(BotwClimb.ALTAR_CREDIT));
    }

    private boolean maxed() {
        return upgrades() >= ClimbConstants.STAMINA_MAX_UPGRADES;
    }

    private double offeredTotal() {
        double sum = 0;
        for (AltarOffer o : offer) if (o != null) sum += (double) o.count() * o.unit();
        return sum;
    }

    private int offeredFrom(int invSlot) {
        for (AltarOffer o : offer) if (o != null && o.invSlot() == invSlot) return o.count();
        return 0;
    }

    private List<Row> pickerRows() {
        List<Row> rows = new ArrayList<>();
        var items = com.botwclimb.common.Compat.items(me().getInventory());
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            float unit = AltarValues.unitValue(s);
            int available = s.getCount() - offeredFrom(i);
            if (unit > 0f && available > 0) rows.add(new Row(i, s, available, unit));
        }
        rows.sort(Comparator.<Row>comparingDouble(r -> -r.unit()).thenComparingInt(Row::invSlot));
        return rows;
    }

    // ------------------------------------------------------------------ layout

    private int left() {
        return (width - W) / 2;
    }

    /** The whole panel: while upgrades are left it holds the nine slots; when everything is bought it shrinks to what is left to show. */
    private int panelHeight() {
        if (!maxed()) return H;
        return credit() > 0.01f ? 176 : 106; // with leftover credit the refund list is shown too
    }

    private int top() {
        return (height - panelHeight()) / 2;
    }

    private int slotX(int i) {
        return left() + (W - 3 * SLOT - 2 * GAP) / 2 + (i % 3) * (SLOT + GAP);
    }

    private int slotY(int i) {
        return top() + GRID_Y + (i / 3) * (SLOT + GAP);
    }

    private int buttonX() {
        return left() + 12;
    }

    private int buttonY() {
        return top() + panelHeight() - 26;
    }

    private int buttonW() {
        return W - 24;
    }

    private boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xBB000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        int x = left(), y = top();
        int ph = panelHeight();
        g.fill(x, y, x + W, y + ph, BORDER);
        g.fill(x + 1, y + 1, x + W - 1, y + ph - 1, PANEL);
        vignette(g, x + 1, y + 1, W - 2, ph - 2);
        int u = upgrades();
        if (maxed()) { // a finished altar says only that, centred
            g.drawCenteredString(font, Component.translatable("gui.botwclimb.altar.maxed"), x + W / 2, y + 10, GREEN);
        } else g.drawString(font, title, x + 12, y + 10, BRIGHT, false);
        String right = maxed() ? Component.translatable("gui.botwclimb.altar.maxed").getString()
                : Component.translatable("gui.botwclimb.altar.upgrade", u + 1, ClimbConstants.STAMINA_MAX_UPGRADES).getString();
        if (!maxed()) g.drawString(font, right, x + W - 12 - font.width(right), y + 10, MUTED, false); // (a finished altar already said it, centred)

        drawSlots(g, mx, my);
        drawSummary(g);
        drawWheel(g, u);
        if (!maxed()) drawButton(g, mx, my); // nothing left to press once everything is bought
        if (!message.isEmpty()) g.drawString(font, trim(message, W - 24), x + 12, y + 91, DANGER, false);
        if (pickerFor >= 0) drawPicker(g, mx, my);
        else if (maxed() && credit() > 0.01f) drawRefund(g, mx, my);
        drawSlotTooltip(g, mx, my);
    }

    /** Darkens the panel towards its edges and corners, drawn into the panel itself (no screen effect). */
    public static void vignette(GuiGraphics g, int x, int y, int w, int h) {
        int layers = 14;
        for (int i = 0; i < layers; i++) {
            float t = 1f - (float) i / layers;
            int a = (int) (0xB0 * t * t * t);
            if (a < 2) continue;
            int c = a << 24;
            g.fill(x + i, y + i, x + w - i, y + i + 1, c);
            g.fill(x + i, y + h - i - 1, x + w - i, y + h - i, c);
            g.fill(x + i, y + i + 1, x + i + 1, y + h - i - 1, c);
            g.fill(x + w - i - 1, y + i + 1, x + w - i, y + h - i - 1, c);
        }
    }

    private void drawSlots(GuiGraphics g, int mx, int my) {
        boolean locked = maxed();
        if (locked) return; // the refund list takes this place
        for (int i = 0; i < 9; i++) {
            int sx = slotX(i), sy = slotY(i);
            AltarOffer o = offer[i];
            g.fill(sx, sy, sx + SLOT, sy + SLOT, o != null ? (i == pickerFor ? GREEN : SLOT_BORDER) : (i == pickerFor ? GREEN : EMPTY_BORDER));
            g.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, SLOT_BG);
            if (o != null) {
                g.renderItem(o.icon(), sx + 3, sy + 3);
                g.renderItemDecorations(font, new ItemStack(o.icon().getItem(), o.count()), sx + 3, sy + 3);
            } else if (!locked) {
                g.fill(sx + SLOT / 2 - 4, sy + SLOT / 2 - 1, sx + SLOT / 2 + 4, sy + SLOT / 2 + 1, i == pickerFor ? GREEN : GREY);
                g.fill(sx + SLOT / 2 - 1, sy + SLOT / 2 - 4, sx + SLOT / 2 + 1, sy + SLOT / 2 + 4, i == pickerFor ? GREEN : GREY);
            }
            if (locked) g.fill(sx, sy, sx + SLOT, sy + SLOT, 0x88000000);
        }
    }

    private void drawSummary(GuiGraphics g) {
        int x = left() + 12, w = W - 24, y = top() + 72;
        if (maxed()) {
            int cx = left() + W / 2, top = top();
            // "2,250 Stamina capacity" on one line, both the same size and on the same baseline, the pair centred as one
            Component label = Component.translatable("gui.botwclimb.altar.capacity");
            float k = 1.25f, gap = 5f;
            float total = (font.width("2,250") + gap + font.width(label)) * k;
            g.pose().pushPose();
            g.pose().translate(cx - total / 2f, top + 82, 0);
            g.pose().scale(k, k, 1f);
            g.drawString(font, "2,250", 0, 0, BRIGHT, false);
            g.drawString(font, label, (int) (font.width("2,250") + gap), 0, MUTED, false);
            g.pose().popPose();
            if (credit() > 0.01f) g.drawCenteredString(font, Component.translatable("gui.botwclimb.altar.credit", fmt(credit())), cx, top + 100, MUTED);
            return;
        }
        double price = AltarRules.price(upgrades()), offered = offeredTotal(), cr = credit();
        g.drawString(font, Component.translatable("gui.botwclimb.altar.offered", fmt(offered)), x, y, BRIGHT, false);
        String pr = Component.translatable("gui.botwclimb.altar.price", fmt(price)).getString();
        g.drawString(font, pr, x + w - font.width(pr), y, MUTED, false);
        g.fill(x, y + 11, x + w, y + 15, GREY);
        float frac = (float) Mth.clamp((offered + cr) / price, 0, 1);
        g.fill(x, y + 11, x + (int) (w * frac), y + 15, GREEN);
        double need = price - cr - offered;
        if (need > 0.0001 && message.isEmpty()) g.drawString(font, Component.translatable("gui.botwclimb.altar.needed", fmt(need)), x, y + 19, MUTED, false);
        if (cr > 0.001) g.drawString(font, Component.translatable("gui.botwclimb.altar.credit", fmt(cr)), x, y + 30, MUTED, false); // its own line, so it never runs into the note above
    }

    private void drawWheel(GuiGraphics g, int u) {
        Minecraft mc = Minecraft.getInstance();
        float cx = left() + W / 2f, cy = top() + (maxed() ? 46 : 44), s = 0.9f; // fits between the title row and the offered line, even with every wheel
        Matrix4f m = g.pose().last().pose();
        StaminaHud.beginDraw(mc);
        float[][] radii = StaminaHud.RINGS; // the same rings as in the game
        StaminaHud.ring(m, cx, cy, radii[0][0] * s, radii[0][1] * s, 0f, 1f, GREY & 0xFFFFFF, 1f);
        StaminaHud.ring(m, cx, cy, radii[0][0] * s, radii[0][1] * s, 0f, 1f, 0x00F25A, 1f);
        for (int wheel = 0; wheel < 2; wheel++) {
            float r0 = radii[wheel + 1][0] * s, r1 = radii[wheel + 1][1] * s;
            for (int q = 0; q < 4; q++) {
                int idx = wheel * 4 + q; // the upgrade this quarter belongs to
                boolean have = u > idx, next = !maxed() && u == idx;
                int rgb = have ? 0x00F25A : next ? 0xFFD34D : 0x262B33;
                StaminaHud.ring(m, cx, cy, r0, r1, q * 0.25f + 0.004f, (q + 1) * 0.25f - 0.004f, rgb, 1f);
            }
        }
        StaminaHud.endDraw();
        if (maxed()) return; // the wheel stands alone: the capacity is written under it
        String cap = String.valueOf((int) (ClimbConstants.STAMINA_WHEEL + ClimbConstants.STAMINA_PER_UPGRADE * u));
        g.drawString(font, cap, (int) cx - 32 - font.width(cap), (int) cy - 4, MUTED, false);
        if (!maxed()) g.drawString(font, "+" + (int) ClimbConstants.STAMINA_PER_UPGRADE, (int) cx + 32, (int) cy - 4, AMBER, false);
    }

    private void drawButton(GuiGraphics g, int mx, int my) {
        int bx = buttonX(), by = buttonY(), bw = buttonW();
        boolean can = canOffer();
        boolean hover = over(mx, my, bx, by, bw, 18);
        // a deep green with white text (about 5:1), brighter on hover, instead of dark text on the bright green
        g.fill(bx, by, bx + bw, by + 18, can ? (hover ? 0xFF0E9A47 : 0xFF087F3C) : GREY);
        if (can) g.fill(bx, by, bx + bw, by + 1, 0x3300F25A); // a thin highlight on the top edge
        Component label = maxed() ? Component.translatable("gui.botwclimb.altar.maxed") : Component.translatable("gui.botwclimb.altar.button");
        g.drawCenteredString(font, label, bx + bw / 2, by + 5, can ? 0xFFFFFFFF : 0xFF7F8998);
    }

    private boolean canOffer() {
        if (maxed()) return false;
        return offeredTotal() + credit() + 0.0001 >= AltarRules.price(upgrades());
    }

    private int pickerX() {
        return left() + (W - PW) / 2;
    }

    private int pickerY() {
        return top() + GRID_Y - PH - 3; // sits just above the slots, so they stay clickable
    }

    private void drawPicker(GuiGraphics g, int mx, int my) {
        List<Row> rows = pickerRows();
        int x = pickerX(), y = pickerY(), w = PW, h = PH, ly = y + 16;
        pickerScroll = Mth.clamp(pickerScroll, 0, Math.max(0, rows.size() - ROWS));
        long now = net.minecraft.Util.getMillis();
        float diff = pickerScroll - pickerPos;
        pickerPos = Math.abs(diff) < 0.004f ? pickerScroll : pickerPos + diff * Math.min(1f, (now - pickerLastFrame) / 70f);
        pickerLastFrame = now;
        float p = Mth.clamp((now - pickerOpenAt) / 160f, 0f, 1f);
        p = 1f - (1f - p) * (1f - p) * (1f - p); // eased out: fast at first, settling
        float scale = 0.25f + 0.75f * p;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.pose().translate(pickerPivotX, pickerPivotY, 0); // grows out of the slot that was clicked
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-pickerPivotX, -pickerPivotY, 0);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, fade(0xFF242A32, p));
        g.fill(x, y, x + w, y + h, fade(0xFF07090C, p));
        vignette(g, x, y, w, h);
        boolean text = p > 0.5f;
        if (text) {
            g.drawString(font, Component.translatable("gui.botwclimb.altar.pick"), x + 8, y + 4, fade(MUTED, p), false);
        }
        if (rows.isEmpty() && text) g.drawString(font, "-", x + 8, ly + 4, MUTED, false);
        boolean clip = p >= 1f;
        if (clip) g.enableScissor(x, ly, x + w, ly + ROWS * ROW_H);
        int first = (int) Math.floor(pickerPos);
        for (int i = first; i <= first + ROWS && i < rows.size(); i++) {
            int ry = ly + Math.round((i - pickerPos) * ROW_H);
            if (!clip && (ry < ly || ry + ROW_H > ly + ROWS * ROW_H)) continue;
            Row r = rows.get(i);
            boolean hover = over(mx, my, x + 3, Math.max(ry, ly), w - 6, ROW_H) && my >= ly && my < ly + ROWS * ROW_H;
            if (hover) g.fill(x + 3, ry, x + w - 3, ry + ROW_H, fade(0xFF161A20, p));
            g.renderItem(r.stack(), x + 5, ry);
            if (!text) continue;
            String val = AltarValues.isGear(r.stack()) || r.available() == 1 ? fmt(r.unit()) : Component.translatable("gui.botwclimb.altar.each", fmt(r.unit())).getString();
            g.drawString(font, trim(r.stack().getHoverName().getString() + " x" + r.available(), w - 40 - font.width(val)), x + 25, ry + 4, fade(TEXT, p), false);
            g.drawString(font, val, x + w - 8 - font.width(val), ry + 4, fade(MUTED, p), false);
        }
        if (clip) g.disableScissor();
        if (rows.size() > ROWS) { // scroll bar
            int trackH = ROWS * ROW_H, barH = Math.max(10, trackH * ROWS / rows.size());
            int barY = ly + Math.round((trackH - barH) * pickerPos / (rows.size() - ROWS));
            g.fill(x + w - 3, ly, x + w - 1, ly + trackH, GREY);
            g.fill(x + w - 3, barY, x + w - 1, barY + barH, MUTED);
        }
        g.pose().popPose();
    }

    /** The items the credit left after the last upgrade can be taken as, best fit first. */
    private static int fade(int argb, float p) {
        return ((int) ((argb >>> 24) * p) << 24) | (argb & 0xFFFFFF);
    }

    private List<Item> refundItems() {
        List<Item> out = new ArrayList<>();
        float credit = credit();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack sample = new ItemStack(item);
            if (AltarValues.isGear(sample)) continue;
            float unit = AltarValues.looseValue(item);
            if (unit > 0f && AltarRules.refundCount(credit, unit) >= 1) out.add(item);
        }
        out.sort(Comparator.comparingDouble(i -> -AltarValues.looseValue(i)));
        return out;
    }

    private void drawRefund(GuiGraphics g, int mx, int my) {
        List<Item> items = refundItems();
        int x = left() + 12, y = top() + REFUND_Y, w = W - 24;
        g.drawString(font, Component.translatable("gui.botwclimb.altar.refund_title"), x, y - 11, MUTED, false);
        refundScroll = Mth.clamp(refundScroll, 0, Math.max(0, items.size() - 2));
        for (int i = 0; i < 2 && refundScroll + i < items.size(); i++) {
            Item item = items.get(refundScroll + i);
            int count = AltarRules.refundCount(credit(), AltarValues.looseValue(item));
            int ry = y + i * ROW_H;
            if (over(mx, my, x, ry, w, ROW_H)) g.fill(x, ry, x + w, ry + ROW_H, 0xFF1D2128);
            g.renderItem(new ItemStack(item), x + 2, ry + 1);
            g.drawString(font, trim(new ItemStack(item).getHoverName().getString() + " x" + count, w - 28), x + 24, ry + 5, TEXT, false);
        }
    }

    private void drawSlotTooltip(GuiGraphics g, int mx, int my) {
        if (pickerFor >= 0) return;
        for (int i = 0; i < 9; i++) {
            AltarOffer o = offer[i];
            if (o != null && over(mx, my, slotX(i), slotY(i), SLOT, SLOT)) {
                g.renderTooltip(font, List.of(o.icon().getHoverName(),
                        Component.translatable("gui.botwclimb.altar.points", fmt(o.count() * o.unit())),
                        Component.translatable("gui.botwclimb.altar.take_back").withStyle(net.minecraft.ChatFormatting.GRAY)), java.util.Optional.empty(), mx, my);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (pickerFor >= 0) {
            List<Row> rows = pickerRows();
            int x = pickerX(), y = pickerY(), w = PW, h = PH, ly = y + 16;
            if (over(mx, my, x, y, w, h)) {
                if (button != 0) return true;
                int i = (int) Math.floor((my - ly) / ROW_H + pickerPos);
                if (my >= ly && my < ly + ROWS * ROW_H && i >= 0 && i < rows.size()) {
                    Row r = rows.get(i);
                    boolean whole = hasShiftDown() || AltarValues.isGear(r.stack());
                    int add = whole ? r.available() : 1; // click: one more; shift-click: the whole stack
                    AltarOffer cur = offer[pickerFor];
                    int count = cur != null && cur.invSlot() == r.invSlot() ? cur.count() + add : add;
                    offer[pickerFor] = new AltarOffer(r.invSlot(), count, r.unit(), r.stack().copyWithCount(1));
                    message = "";
                    pickerFor = -1; // always closes once the item is in its box
                }
                return true;
            }
            pickerFor = -1; // clicked outside: close the list (and treat the click normally below)
        }
        if (maxed() && credit() > 0.01f && pickerFor < 0) {
            List<Item> items = refundItems();
            int x = left() + 12, y = top() + REFUND_Y, w = W - 24;
            for (int i = 0; i < 2 && refundScroll + i < items.size(); i++) {
                if (over(mx, my, x, y + i * ROW_H, w, ROW_H)) {
                    com.botwclimb.client.ClientCompat.sendToServer(new AltarOfferPayload(pos, List.of(), BuiltInRegistries.ITEM.getKey(items.get(refundScroll + i))));
                    return true;
                }
            }
        }
        for (int i = 0; i < 9; i++) {
            if (over(mx, my, slotX(i), slotY(i), SLOT, SLOT)) {
                if (maxed()) return true;
                if (button == 1) { // right-click: take one back out
                    AltarOffer o = offer[i];
                    if (o != null) offer[i] = o.count() <= 1 ? null : new AltarOffer(o.invSlot(), o.count() - 1, o.unit(), o.icon());
                    return true;
                }
                if (button == 0) { // left-click: open the list to add to this slot
                    pickerFor = i;
                    pickerScroll = 0;
                    pickerPos = 0f;
                    pickerOpenAt = pickerLastFrame = net.minecraft.Util.getMillis();
                    pickerPivotX = slotX(i) + SLOT / 2f;
                    pickerPivotY = slotY(i) + SLOT / 2f;
                }
                return true;
            }
        }
        if (canOffer() && over(mx, my, buttonX(), buttonY(), buttonW(), 18)) {
            List<AltarOfferPayload.Entry> entries = new ArrayList<>();
            for (AltarOffer o : offer) if (o != null) entries.add(new AltarOfferPayload.Entry(o.invSlot(), o.count()));
            com.botwclimb.client.ClientCompat.sendToServer(new AltarOfferPayload(pos, entries, AltarOfferPayload.NO_REFUND));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int d = (int) -Math.signum(sy);
        if (pickerFor >= 0) {
            pickerScroll += d;
            return true;
        }
        if (maxed()) {
            refundScroll += d;
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    // ------------------------------------------------------------------ small helpers

    private String trim(String s, int maxWidth) {
        if (font.width(s) <= maxWidth) return s;
        while (s.length() > 1 && font.width(s + "...") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, v < 10 && Math.abs(v - Math.round(v)) > 0.001 ? "%.2f" : "%.1f", v);
    }
}
