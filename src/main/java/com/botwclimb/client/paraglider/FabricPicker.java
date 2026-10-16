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
 * Side menu for the paraglider fabric. While an inventory screen is open and the player has an Elytra in it, a narrow
 * panel as tall as the screen sits beside the inventory. Scroll it to see every fabric and click one to use it. The
 * choice is saved on the player only: the Elytra item and other players' view of it are not changed.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class FabricPicker {
    private static final int PANEL_W = 104, ROW_H = 24, PAD = 4, HEADER_H = 16, MARGIN = 4;
    private static int scroll; // index of the first visible row

    private FabricPicker() {}

    private static int visibleRows(AbstractContainerScreen<?> screen) {
        return Math.max(1, (screen.height - 2 * MARGIN - HEADER_H - PAD) / ROW_H);
    }

    private static int maxScroll(AbstractContainerScreen<?> screen) {
        return Math.max(0, FabricRegistry.all().size() - visibleRows(screen));
    }

    private static int panelX(AbstractContainerScreen<?> screen) {
        // beside the inventory when there is room, otherwise on the right edge
        return Math.min(screen.getGuiLeft() + 184, screen.width - PANEL_W - MARGIN);
    }

    private static boolean hasElytra(Player player) {
        return player.getInventory().contains(stack -> stack.canElytraFly(player));
    }

    private static boolean shown(ScreenEvent event) {
        var mc = Minecraft.getInstance();
        return event.getScreen() instanceof AbstractContainerScreen<?> && mc.player != null && hasElytra(mc.player);
    }

    private static boolean inside(AbstractContainerScreen<?> screen, double mx, double my) {
        return mx >= panelX(screen) && mx < panelX(screen) + PANEL_W && my >= MARGIN && my < screen.height - MARGIN;
    }

    /** Index of the fabric row under the mouse, or -1. */
    private static int rowAt(AbstractContainerScreen<?> screen, double my) {
        double y = my - MARGIN - HEADER_H;
        if (y < 0) return -1;
        int row = scroll + (int) (y / ROW_H);
        return row < FabricRegistry.all().size() && (y % ROW_H) < ROW_H - 2 && row - scroll < visibleRows(screen)
                ? row : -1;
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!shown(event)) return;
        var screen = (AbstractContainerScreen<?>) event.getScreen();
        GuiGraphics g = event.getGuiGraphics();
        var mc = Minecraft.getInstance();
        int x = panelX(screen), top = MARGIN, h = screen.height - 2 * MARGIN;
        int selected = mc.player.getData(BotwClimb.PARAGLIDER).fabric;
        int mx = event.getMouseX(), my = event.getMouseY();
        List<FabricDefinition> all = FabricRegistry.all();

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x, top, x + PANEL_W, top + h, 0xC0101018);
        g.renderOutline(x, top, PANEL_W, h, 0xFF8A8A9A);
        g.drawString(mc.font, Component.translatable("gui.botwclimb.fabric_picker"), x + PAD, top + 4, 0xFFFFFF, false);

        int hover = rowAt(screen, my);
        int rows = visibleRows(screen);
        for (int r = 0; r < rows && scroll + r < all.size(); r++) {
            int i = scroll + r;
            int ry = top + HEADER_H + PAD + r * ROW_H;
            if (i == hover && inside(screen, mx, my)) g.fill(x + 2, ry - 1, x + PANEL_W - 2, ry + ROW_H - 3, 0x50FFFFFF);
            if (i == selected) g.renderOutline(x + 2, ry - 1, PANEL_W - 4, ROW_H - 2, 0xFFFFD35A);
            // flat preview of the canopy (40x16 at texture (0,46)), then the name
            g.blit(all.get(i).texture(), x + PAD + 2, ry + 2, 40, 16, 0f, 46f, 40, 16, 128, 64);
            g.drawString(mc.font, Component.translatable("fabric.botwclimb." + all.get(i).id()),
                    x + PAD + 46, ry + 6, 0xFFFFFF, false);
        }
        if (all.size() > rows) { // scroll hint
            g.drawString(mc.font, (scroll + 1) + "-" + Math.min(all.size(), scroll + rows) + "/" + all.size(),
                    x + PAD, top + h - 10, 0x9A9AA5, false);
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
        event.setCanceled(true); // clicks in the menu never reach the inventory slots behind it
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
