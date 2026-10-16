package com.botwclimb.client.paraglider;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.paraglider.FabricDefinition;
import com.botwclimb.common.paraglider.FabricRegistry;
import com.botwclimb.network.ParagliderFabricPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Side menu for the paraglider fabric, drawn to the left of the inventory in the vanilla panel style and no taller than
 * the inventory itself. Shown while an inventory screen is open and the player has an Elytra in it. Hover a swatch for
 * its name, scroll for more, click to use it. The choice is saved on the player only.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class FabricPicker {
    private static final int PANEL_W = 50, ROW_H = 20, PAD = 4, HEADER_H = 12, INV_H = 166;
    // vanilla GUI colours
    private static final int PANEL = 0xFFC6C6C6, DARK = 0xFF373737, LIGHT = 0xFFFFFFFF, TEXT = 0xFF404040;
    private static int scroll; // index of the first visible row

    private FabricPicker() {}

    private static int panelH(AbstractContainerScreen<?> screen) {
        return Math.min(INV_H, screen.height - 8);
    }

    private static int visibleRows(AbstractContainerScreen<?> screen) {
        return Math.max(1, (panelH(screen) - HEADER_H - PAD * 2) / ROW_H);
    }

    private static int maxScroll(AbstractContainerScreen<?> screen) {
        return Math.max(0, FabricRegistry.all().size() - visibleRows(screen));
    }

    private static int panelX(AbstractContainerScreen<?> screen) {
        return Math.max(2, screen.getGuiLeft() - PANEL_W - 4); // left of the inventory, never off screen
    }

    private static int panelY(AbstractContainerScreen<?> screen) {
        return Math.max(2, Math.min(screen.getGuiTop(), screen.height - panelH(screen) - 2));
    }

    private static boolean hasElytra(Player player) {
        return player.getInventory().contains(stack -> stack.canElytraFly(player));
    }

    private static boolean shown(ScreenEvent event) {
        var mc = Minecraft.getInstance();
        return event.getScreen() instanceof AbstractContainerScreen<?> && mc.player != null && hasElytra(mc.player);
    }

    private static boolean inside(AbstractContainerScreen<?> screen, double mx, double my) {
        int x = panelX(screen), y = panelY(screen);
        return mx >= x && mx < x + PANEL_W && my >= y && my < y + panelH(screen);
    }

    /** Index of the fabric row under the mouse, or -1. */
    private static int rowAt(AbstractContainerScreen<?> screen, double my) {
        double y = my - panelY(screen) - HEADER_H - PAD;
        if (y < 0) return -1;
        int r = (int) (y / ROW_H);
        if (r >= visibleRows(screen) || y % ROW_H > ROW_H - 2) return -1;
        int row = scroll + r;
        return row < FabricRegistry.all().size() ? row : -1;
    }

    private static void bevel(GuiGraphics g, int x, int y, int w, int h, boolean sunken) {
        int tl = sunken ? DARK : LIGHT, br = sunken ? LIGHT : DARK;
        g.fill(x, y, x + w, y + 1, tl);
        g.fill(x, y, x + 1, y + h, tl);
        g.fill(x, y + h - 1, x + w, y + h, br);
        g.fill(x + w - 1, y, x + w, y + h, br);
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!shown(event)) return;
        var screen = (AbstractContainerScreen<?>) event.getScreen();
        GuiGraphics g = event.getGuiGraphics();
        var mc = Minecraft.getInstance();
        int x = panelX(screen), y = panelY(screen), h = panelH(screen);
        int selected = mc.player.getData(BotwClimb.PARAGLIDER).fabric;
        int mx = event.getMouseX(), my = event.getMouseY();
        List<FabricDefinition> all = FabricRegistry.all();

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x, y, x + PANEL_W, y + h, PANEL);
        bevel(g, x, y, PANEL_W, h, false);
        g.drawString(mc.font, Component.translatable("gui.botwclimb.fabric_picker_short"), x + PAD, y + PAD, TEXT, false);

        int hover = inside(screen, mx, my) ? rowAt(screen, my) : -1;
        int rows = visibleRows(screen);
        for (int r = 0; r < rows && scroll + r < all.size(); r++) {
            int i = scroll + r;
            int ry = y + HEADER_H + PAD + r * ROW_H;
            int rx = x + PAD;
            bevel(g, rx - 1, ry - 1, 42, 18, true); // vanilla slot frame around the swatch
            g.blit(all.get(i).texture(), rx, ry, 40, 16, 0f, 46f, 40, 16, 128, 64);
            if (i == selected) g.renderOutline(rx - 2, ry - 2, 44, 20, 0xFFFFFFFF);
            if (i == hover) g.fill(rx, ry, rx + 40, ry + 16, 0x80FFFFFF);
        }
        if (hover >= 0) {
            g.renderTooltip(mc.font, Component.translatable("fabric.botwclimb." + all.get(hover).id()), mx, my);
        }
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!shown(event) || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (!inside(screen, event.getMouseX(), event.getMouseY())) return;
        scroll = Math.max(0, Math.min(maxScroll(screen), scroll - (int) Math.signum(event.getScrollDeltaY())));
        event.setCanceled(true); // scrolling the menu must not also scroll anything under it
    }

    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!shown(event) || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (!inside(screen, event.getMouseX(), event.getMouseY())) return;
        event.setCanceled(true); // clicks in the menu never reach the inventory slots
        int row = rowAt(screen, event.getMouseY());
        if (row < 0 || event.getButton() != 0) return;
        PacketDistributor.sendToServer(new ParagliderFabricPayload(row));
        var player = Minecraft.getInstance().player;
        if (player != null) player.getData(BotwClimb.PARAGLIDER).fabric = row; // show it straight away
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?>) scroll = 0;
    }
}
