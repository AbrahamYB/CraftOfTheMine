package com.botwclimb.client.paraglider;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.paraglider.FabricDefinition;
import com.botwclimb.common.paraglider.FabricRegistry;
import com.botwclimb.network.ParagliderFabricPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The fabric chooser: the Fabric key (default Y) while hovering an Elytra in any inventory screen opens a small translucent panel
 * with every fabric. The choice belongs to the player, not to the item, and closes with the inventory or on a click
 * outside the panel.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class FabricPicker {
    private static final int COLS = 4, PAD = 4, CELL_W = 44, CELL_H = 20, TITLE_H = 14;

    private static boolean open;
    private static int px, py;

    private FabricPicker() {}

    private static int rows() {
        return (FabricRegistry.all().size() + COLS - 1) / COLS;
    }

    private static int panelW() {
        return COLS * CELL_W + (COLS + 1) * PAD;
    }

    private static int panelH() {
        return TITLE_H + rows() * CELL_H + (rows() + 1) * PAD;
    }

    private static int cellX(int i) {
        return px + PAD + (i % COLS) * (CELL_W + PAD);
    }

    private static int cellY(int i) {
        return py + TITLE_H + PAD + (i / COLS) * (CELL_H + PAD);
    }

    /** @return index of the fabric cell under the mouse, or -1 */
    private static int cellAt(double mx, double my) {
        for (int i = 0; i < FabricRegistry.all().size(); i++) {
            if (mx >= cellX(i) && mx < cellX(i) + CELL_W && my >= cellY(i) && my < cellY(i) + CELL_H) return i;
        }
        return -1;
    }

    private static boolean inside(double mx, double my) {
        return mx >= px && mx < px + panelW() && my >= py && my < py + panelH();
    }

    private static boolean isElytra(ItemStack stack) {
        var player = Minecraft.getInstance().player;
        return !stack.isEmpty() && player != null && stack.canElytraFly(player);
    }

    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (open || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (!ParagliderKeys.FABRIC.matches(event.getKeyCode(), event.getScanCode())) return;
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !isElytra(slot.getItem())) return;
        var win = Minecraft.getInstance().getWindow();
        double mx = Minecraft.getInstance().mouseHandler.xpos() * win.getGuiScaledWidth() / win.getScreenWidth();
        double my = Minecraft.getInstance().mouseHandler.ypos() * win.getGuiScaledHeight() / win.getScreenHeight();
        open = true;
        px = (int) Math.max(2, Math.min(mx + 6, screen.width - panelW() - 2));
        py = (int) Math.max(2, Math.min(my - panelH() / 2.0, screen.height - panelH() - 2));
        event.setCanceled(true); // the key is used up by the picker: nothing else reacts to this press
    }

    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        double mx = event.getMouseX(), my = event.getMouseY();
        if (open) {
            if (!inside(mx, my)) { // a click anywhere else closes the panel and goes on to do its normal thing
                open = false;
                return;
            }
            int cell = cellAt(mx, my);
            if (cell >= 0 && event.getButton() == 0) {
                PacketDistributor.sendToServer(new ParagliderFabricPayload(cell));
                var player = Minecraft.getInstance().player;
                if (player != null) player.getData(BotwClimb.PARAGLIDER).fabric = cell; // show it at once
                open = false;
            }
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!open || !(event.getScreen() instanceof AbstractContainerScreen<?>)) return;
        GuiGraphics g = event.getGuiGraphics();
        var mc = Minecraft.getInstance();
        int mx = event.getMouseX(), my = event.getMouseY();
        List<FabricDefinition> all = FabricRegistry.all();
        int selected = mc.player != null ? mc.player.getData(BotwClimb.PARAGLIDER).fabric : 0;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400); // above items and tooltips of the screen underneath
        g.fill(px, py, px + panelW(), py + panelH(), 0xB0101018);
        g.renderOutline(px, py, panelW(), panelH(), 0xFF8A8A9A);
        g.drawString(mc.font, Component.translatable("gui.botwclimb.fabric_picker"), px + PAD, py + 3, 0xFFFFFF, false);
        int hover = cellAt(mx, my);
        for (int i = 0; i < all.size(); i++) {
            int x = cellX(i), y = cellY(i);
            if (i == hover) g.fill(x - 1, y - 1, x + CELL_W + 1, y + CELL_H + 1, 0x60FFFFFF);
            if (i == selected) g.renderOutline(x - 1, y - 1, CELL_W + 2, CELL_H + 2, 0xFFFFD35A);
            // the flat canopy design strip stored in every fabric texture at (0,46), 40x16
            g.blit(all.get(i).texture(), x + 2, y + 2, 40, 16, 0f, 46f, 40, 16, 128, 64);
        }
        if (hover >= 0) {
            g.renderTooltip(mc.font, Component.translatable("fabric.botwclimb." + all.get(hover).id()), mx, my);
        }
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onClose(ScreenEvent.Closing event) {
        open = false;
    }
}
