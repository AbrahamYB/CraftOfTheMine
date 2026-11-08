package com.botwclimb.client;

import com.botwclimb.client.ClientConfig.RideStyle;
import com.botwclimb.client.altar.AltarScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * The mod's settings, in the look of the altar and terminal screens: one dark panel, rows of a name on the left and its control on
 * the right (a switch, a value that cycles, or a slider), a line under the panel saying what the row under the pointer does.
 * Every change is applied and saved at once; Done only closes. All the rows are listed in {@link #buildRows()}, so adding a
 * setting is one line there plus its entry in ClientConfig and en_us.json.
 */
public class BotwConfigScreen extends Screen {
    /** Panel width, its side padding, row and heading heights, space for the title and for the buttons, the controls' width, and how far in from a row's edge they sit. */
    private static final int W = 320, PAD = 16, ROW_H = 26, HEAD_H = 22, TITLE_H = 40, FOOT_H = 54, CONTROL_W = 132, INSET = 8, TRACK_GAP = 42;
    private static final int PANEL = 0xFF090B0E, BORDER = 0xFF171B21, CARD = 0xFF0F1216, CARD_BORDER = 0xFF242A32, CARD_HOVER = 0xFF3A424D;
    private static final int TEXT = 0xFFD2D8E0, MUTED = 0xFF7D8694, BRIGHT = 0xFFEDF1F6, GREEN = 0xFF00F25A, GREY = 0xFF1A1E24;
    private static final int BUTTON = 0xFF087F3C, BUTTON_HOVER = 0xFF0E9A47, BUTTON_QUIET = 0xFF1B2028;

    /** One line of the panel: a section heading, or a setting. */
    private sealed interface Row permits Heading, Toggle, Cycle, Slider {}

    private record Heading(String key) implements Row {}

    private record Toggle(String key, ModConfigSpec.BooleanValue value) implements Row {}

    private record Cycle(String key, ModConfigSpec.EnumValue<RideStyle> value) implements Row {}

    private record Slider(String key, ModConfigSpec.IntValue value, int min, int max) implements Row {}

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private Slider dragging;

    public BotwConfigScreen(Screen parent) {
        super(Component.translatable("gui.botwclimb.config.title"));
        this.parent = parent;
        buildRows();
    }

    /** The whole menu, top to bottom. */
    private void buildRows() {
        rows.add(new Heading("ride"));
        rows.add(new Cycle("ride_style", ClientConfig.rideStyleValue()));
        rows.add(new Slider("motion_blur", ClientConfig.motionBlurValue(), 0, 100));
        rows.add(new Toggle("speed_lines", ClientConfig.speedLinesValue()));
        rows.add(new Toggle("ground_opening", ClientConfig.groundOpeningValue()));
        rows.add(new Heading("glider"));
        rows.add(new Toggle("glider_camera", ClientConfig.gliderCameraValue()));
        rows.add(new Heading("plots"));
        rows.add(new Toggle("plot_outline", ClientConfig.plotOutlineValue()));
    }

    // ---- layout: everything hangs off the panel's top-left, nothing is placed by eye

    private int panelH() {
        int h = TITLE_H + FOOT_H;
        for (Row r : rows) h += r instanceof Heading ? HEAD_H : ROW_H;
        return h;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - panelH() - 22) / 2;
    }

    private int rowY(int index) {
        int y = top() + TITLE_H;
        for (int i = 0; i < index; i++) y += rows.get(i) instanceof Heading ? HEAD_H : ROW_H;
        return y;
    }

    /** Where every control ends: all of them share this right edge, one inset inside their row. */
    private int controlRight() {
        return left() + W - PAD - INSET;
    }

    private int controlX() {
        return controlRight() - CONTROL_W;
    }

    private int footY() {
        return top() + panelH() - FOOT_H + 14;
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int rowAt(double mx, double my) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) instanceof Heading) continue;
            if (over(mx, my, left() + PAD, rowY(i), W - 2 * PAD, ROW_H)) return i;
        }
        return -1;
    }

    // ---- drawing

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xBB000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        int x = left(), y = top(), ph = panelH();
        g.fill(x, y, x + W, y + ph, BORDER);
        g.fill(x + 1, y + 1, x + W - 1, y + ph - 1, PANEL);
        AltarScreen.vignette(g, x + 1, y + 1, W - 2, ph - 2);
        g.drawCenteredString(font, title, x + W / 2, y + 16, BRIGHT);
        int hovered = rowAt(mx, my);
        for (int i = 0; i < rows.size(); i++) {
            int ry = rowY(i);
            switch (rows.get(i)) {
                case Heading h -> {
                    g.drawString(font, Component.translatable("gui.botwclimb.config.section." + h.key()).getString().toUpperCase(), x + PAD, ry + 8, MUTED, false);
                    g.fill(x + PAD, ry + HEAD_H - 3, x + W - PAD, ry + HEAD_H - 2, CARD_BORDER);
                }
                case Toggle t -> {
                    rowBase(g, i, ry, hovered, t.key());
                    boolean on = t.value().get();
                    int sx = controlRight() - 24, sy = ry + (ROW_H - 10) / 2;
                    g.fill(sx, sy, sx + 24, sy + 10, on ? GREEN : GREY);
                    g.fill(on ? sx + 14 : sx + 2, sy + 2, on ? sx + 22 : sx + 10, sy + 8, on ? BRIGHT : MUTED);
                }
                case Cycle c -> {
                    rowBase(g, i, ry, hovered, c.key());
                    int bx = controlX(), by = ry + 5, bh = ROW_H - 10;
                    boolean hover = over(mx, my, bx, by, CONTROL_W, bh);
                    g.fill(bx, by, bx + CONTROL_W, by + bh, hover ? BUTTON_HOVER : BUTTON);
                    g.drawCenteredString(font, Component.translatable("gui.botwclimb.config.value." + c.value().get().name().toLowerCase()), bx + CONTROL_W / 2, by + (bh - 8) / 2, 0xFFFFFFFF);
                }
                case Slider s -> {
                    rowBase(g, i, ry, hovered, s.key());
                    int tx = controlX(), tw = CONTROL_W - TRACK_GAP, ty = ry + ROW_H / 2 - 1;
                    float f = (s.value().get() - s.min()) / (float) (s.max() - s.min());
                    g.fill(tx, ty, tx + tw, ty + 2, GREY);
                    g.fill(tx, ty, tx + (int) (tw * f), ty + 2, GREEN);
                    int kx = tx + (int) (tw * f);
                    g.fill(kx - 2, ty - 4, kx + 2, ty + 6, dragging == s || hovered == i ? BRIGHT : TEXT);
                    g.drawString(font, s.value().get() + "%", controlRight() - font.width(s.value().get() + "%"), ry + (ROW_H - 8) / 2, TEXT, false);
                }
            }
        }
        // what the row under the pointer does
        if (hovered >= 0) {
            String key = switch (rows.get(hovered)) {
                case Toggle t -> t.key();
                case Cycle c -> c.key();
                case Slider s -> s.key();
                default -> null;
            };
            if (key != null) g.drawCenteredString(font, Component.translatable("gui.botwclimb.config.help." + key), x + W / 2, y + ph + 8, MUTED);
        }
        // the two buttons at the foot
        int by = footY(), bw = (W - 2 * PAD - 8) / 2;
        button(g, mx, my, x + PAD, by, bw, "reset", BUTTON_QUIET);
        button(g, mx, my, x + PAD + bw + 8, by, bw, "done", BUTTON);
    }

    private void rowBase(GuiGraphics g, int index, int ry, int hovered, String key) {
        int x = left() + PAD;
        g.fill(x, ry + 1, x + W - 2 * PAD, ry + ROW_H - 1, hovered == index ? CARD_HOVER : CARD_BORDER);
        g.fill(x + 1, ry + 2, x + W - 2 * PAD - 1, ry + ROW_H - 2, CARD);
        g.drawString(font, Component.translatable("gui.botwclimb.config." + key), x + INSET + 2, ry + (ROW_H - 8) / 2, TEXT, false);
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, String key, int colour) {
        boolean hover = over(mx, my, x, y, w, 20);
        g.fill(x, y, x + w, y + 20, hover ? (colour == BUTTON ? BUTTON_HOVER : CARD_HOVER) : colour);
        g.drawCenteredString(font, Component.translatable("gui.botwclimb.config." + key), x + w / 2, y + 6, 0xFFFFFFFF);
    }

    // ---- input

    //? if >=1.21.9 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return mouseClicked(event.x(), event.y(), event.button());
    }
    *///?}

    //? if <1.21.9
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int by = footY(), bw = (W - 2 * PAD - 8) / 2;
        if (button == 0 && over(mx, my, left() + PAD, by, bw, 20)) {
            ClientConfig.resetAll();
            return true;
        }
        if (button == 0 && over(mx, my, left() + PAD + bw + 8, by, bw, 20)) {
            onClose();
            return true;
        }
        int i = rowAt(mx, my);
        if (i < 0 || button > 1) //? if >=1.21.9 {
        /*return super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mx, my, new net.minecraft.client.input.MouseButtonInfo(button, 0)), false);
        *///?} else {
        return super.mouseClicked(mx, my, button);
        //?}
        switch (rows.get(i)) {
            case Toggle t -> ClientConfig.set(t.value(), !t.value().get());
            case Cycle c -> {
                RideStyle[] all = RideStyle.values();
                int next = (c.value().get().ordinal() + (button == 0 ? 1 : all.length - 1)) % all.length;
                ClientConfig.set(c.value(), all[next]);
            }
            case Slider s -> {
                dragging = s;
                drag(s, mx);
            }
            default -> { }
        }
        return true;
    }

    //? if >=1.21.9 {
    /*@Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        return mouseDragged(event.x(), event.y(), event.button(), dx, dy);
    }
    *///?}

    //? if <1.21.9
    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            drag(dragging, mx);
            return true;
        }
        //? if >=1.21.9 {
        /*return super.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(mx, my, new net.minecraft.client.input.MouseButtonInfo(button, 0)), dx, dy);
        *///?} else {
        return super.mouseDragged(mx, my, button, dx, dy);
        //?}
    }

    //? if >=1.21.9 {
    /*@Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        return mouseReleased(event.x(), event.y(), event.button());
    }
    *///?}

    //? if <1.21.9
    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        //? if >=1.21.9 {
        /*return super.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(mx, my, new net.minecraft.client.input.MouseButtonInfo(button, 0)));
        *///?} else {
        return super.mouseReleased(mx, my, button);
        //?}
    }

    private void drag(Slider s, double mx) {
        float f = Mth.clamp((float) (mx - controlX()) / (CONTROL_W - TRACK_GAP), 0f, 1f);
        int v = s.min() + Math.round(f * (s.max() - s.min()));
        if (v != s.value().get()) ClientConfig.set(s.value(), v);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
