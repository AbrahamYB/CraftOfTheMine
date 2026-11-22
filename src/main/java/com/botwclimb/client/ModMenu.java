package com.botwclimb.client;

import com.botwclimb.BotwClimb;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.joml.Matrix4f;

/**
 * The mod's own menu, reached from a small stamina-wheel button to the right of a row of the pause menu and the title screen, so
 * the settings are one click away without another mod's mod list. The idea of a button beside the vanilla menu rows comes from
 * the Create mod (by the Create Team); this is our own code and our own look, the dark panel of the altar and terminal screens.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class ModMenu {
    private static final String ISSUES = "https://github.com/AbrahamYB/CraftOfTheMine/issues";
    /** The button on the screen now (null when the screen has none), so its wheel can be drawn on top of it. */
    private static Button button;

    private ModMenu() {}

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        button = null;
        Screen screen = event.getScreen();
        String row = screen instanceof PauseScreen ? "menu.reportBugs" : screen instanceof TitleScreen ? "menu.multiplayer" : null;
        if (row == null) return;
        String label = Component.translatable(row).getString();
        for (var l : event.getListenersList()) {
            if (l instanceof AbstractWidget w && w.getMessage().getString().equals(label)) {
                button = Button.builder(Component.empty(), b -> Minecraft.getInstance().setScreen(new Menu(screen)))
                        .bounds(w.getX() + w.getWidth() + 4, w.getY(), 20, 20)
                        .tooltip(Tooltip.create(Component.translatable("gui.botwclimb.menu.title")))
                        .build();
                event.addListener(button);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (button == null || !event.getScreen().children().contains(button)) return;
        wheel(ClientCompat.gui(event), button.getX() + 10f, button.getY() + 10f, 0.36f);
    }

    /** The stamina wheel, full, as the mod's emblem. */
    static void wheel(GuiGraphics g, float cx, float cy, float s) {
        Matrix4f m = GuiShapes.pose(g);
        StaminaHud.beginDraw(Minecraft.getInstance(), g);
        float[][] r = StaminaHud.RINGS;
        StaminaHud.ring(m, cx, cy, r[0][0] * s, r[0][1] * s, 0f, 1f, 0x00F25A, 1f);
        for (int q = 0; q < 4; q++) StaminaHud.ring(m, cx, cy, r[1][0] * s, r[1][1] * s, q * 0.25f + 0.01f, (q + 1) * 0.25f - 0.01f, 0x00F25A, 1f);
        StaminaHud.endDraw();
    }

    /** The menu itself, laid out like Create's: the emblem, the name and version on a dark strip, then plain Minecraft buttons over the usual menu background. */
    public static final class Menu extends Screen {
        private static final int BTN_W = 200, BTN_H = 20;
        private final Screen parent;
        private final String version = ModList.get().getModContainerById(BotwClimb.MOD_ID).map(c -> c.getModInfo().getVersion().toString()).orElse("");

        public Menu(Screen parent) {
            super(Component.translatable("gui.botwclimb.menu.title"));
            this.parent = parent;
        }

        /** Where the first button sits; the emblem and name hang above it. */
        private int firstY() {
            return height / 4 + 48;
        }

        @Override
        protected void init() {
            int x = (width - BTN_W) / 2, y = firstY();
            addRenderableWidget(Button.builder(Component.translatable("gui.botwclimb.menu.configure"), b -> minecraft.setScreen(new BotwConfigScreen(this))).bounds(x, y, BTN_W, BTN_H).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.botwclimb.menu.guide"), b -> minecraft.setScreen(new com.botwclimb.client.guide.GuideScreen(com.botwclimb.client.guide.GuideScenes.all(), 0, this))).bounds(x, y + 24, BTN_W, BTN_H).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.botwclimb.menu.issues"), b -> ConfirmLinkScreen.confirmLinkNow(this, ISSUES)).bounds(x, y + 48, BTN_W, BTN_H).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.botwclimb.menu.back"), b -> onClose()).bounds(x, y + 80, BTN_W, BTN_H).build());
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            super.render(g, mx, my, pt);
            int cx = width / 2, y = firstY();
            wheel(g, cx, y - 66f, 1.25f);
            Component name = title.copy().withStyle(st -> st.withColor(0x00F25A)).append(Component.literal(" v" + version).withStyle(st -> st.withColor(0xFFFFFF)));
            int tw = font.width(name);
            g.fill(cx - tw / 2 - 10, y - 30, cx + tw / 2 + 10, y - 14, 0xAA000000);
            g.drawCenteredString(font, name, cx, y - 26, 0xFFFFFFFF);
            g.drawCenteredString(font, Component.translatable("gui.botwclimb.menu.credit"), cx, height - 12, 0xFFA0A0A0);
        }

        @Override
        public void onClose() {
            minecraft.setScreen(parent);
        }
    }
}
