package com.botwclimb.client.paraglider;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.paraglider.ClothCrafting;
import com.botwclimb.common.paraglider.FabricDefinition;
import com.botwclimb.common.paraglider.FabricRegistry;
import com.botwclimb.common.paraglider.FabricUnlocks;
import com.botwclimb.network.ClothCraftPayload;
import com.botwclimb.network.ParagliderFabricPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The paraglider cloth menu, in the player's own inventory screen (survival, and the creative inventory tab), whether or not
 * they are gliding. A button the size of one slot sits above the off-hand slot in survival, and mirrors the
 * off-hand slot on the other side of the player in creative. It opens a side menu of fabric swatches beside the inventory (on
 * the right while the recipe book is open, so it never covers it). Left-click a fabric to wear it, if it has been learned;
 * clicking any fabric (or right-clicking) shows its recipe in a card next to the menu. Fabrics that have not been learned are
 * dimmed. The menu closes with the inventory.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class FabricPicker {
    private static final int BUTTON_W = 18, BUTTON_H = 18; // exactly one slot frame, so it lines up with the off-hand slot
    private static final int PANEL_W = 50, ROW_H = 20, PAD = 4, CARD_HEAD = 12;
    private static final int CARD_W = 116, SLOT = 18, BTN_H = 16, PER_ROW = 4;
    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button"),
            BUTTON_ON = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    // a dark modern look, the same family as the stamina altar screen
    private static final int PANEL = 0xFF0C0F13, BORDER = 0xFF272E38, FRAME = 0xFF2C343F, SLOT_BG = 0xFF12161C,
            TEXT = 0xFFD2D8E0, MUTED = 0xFF7D8694, WORN = 0xFF00F25A, PICKED = 0xFFFFD34D;
    private static int scroll;       // the row the list is heading for
    private static float scrollPos;  // where it is now, so scrolling glides
    private static long scrollAt;
    private static boolean open;
    private static int info = -1; // the fabric whose recipe is showing
    private static long infoAt;   // when it appeared, for the pop-in

    private FabricPicker() {}

    private static boolean shown(ScreenEvent event) {
        var s = event.getScreen();
        if (Minecraft.getInstance().player == null) return false;
        if (s instanceof InventoryScreen) return true;
        return s instanceof CreativeModeInventoryScreen c && c.isInventoryOpen();
    }

    // ------------------------------------------------------------------ layout

    /** Just the swatches and their frame: no header, no spare room. */
    private static int panelH(AbstractContainerScreen<?> screen) {
        return visibleRows(screen) * ROW_H + 2 * PAD - 2;
    }

    private static int visibleRows(AbstractContainerScreen<?> screen) {
        int room = Math.min(screen.getYSize(), screen.height - 8) - 2 * PAD + 2;
        return Math.max(1, Math.min(FabricRegistry.all().size(), room / ROW_H));
    }

    private static int maxScroll(AbstractContainerScreen<?> screen) {
        return Math.max(0, FabricRegistry.all().size() - visibleRows(screen));
    }

    private static boolean bookOpen(AbstractContainerScreen<?> screen) {
        return screen instanceof InventoryScreen inv && com.botwclimb.client.ClientCompat.recipeBookOpen(inv);
    }

    /** Left of the inventory, or right of it while the recipe book fills the left; never off screen. */
    private static int panelX(AbstractContainerScreen<?> screen) {
        int left = screen.getGuiLeft() - PANEL_W - 4, right = screen.getGuiLeft() + screen.getXSize() + 4;
        if (bookOpen(screen) && right + PANEL_W <= screen.width - 2) return right;
        return Math.max(2, left);
    }

    private static int panelY(AbstractContainerScreen<?> screen) {
        int centred = screen.getGuiTop() + (screen.getYSize() - panelH(screen)) / 2 + (screen instanceof CreativeModeInventoryScreen ? 2 : 0); // level with the middle of the inventory frame (creative's is shorter, and sits a little low)
        return Math.max(2, Math.min(centred, screen.height - panelH(screen) - 2));
    }

    private static boolean panelOnRight(AbstractContainerScreen<?> screen) {
        return panelX(screen) > screen.getGuiLeft();
    }

    private static int cardX(AbstractContainerScreen<?> screen) {
        int px = panelX(screen);
        if (panelOnRight(screen)) return Math.min(px + PANEL_W + 2, Math.max(2, screen.width - CARD_W - 2));
        int x = px - CARD_W - 2;
        return x >= 2 ? x : px + PANEL_W + 2; // no room beside it: over the inventory, just right of the menu
    }

    private static Slot inventorySlot(AbstractContainerScreen<?> screen, int containerIndex) {
        var inv = Minecraft.getInstance().player.getInventory();
        for (Slot slot : screen.getMenu().slots) if (slot.container == inv && slot.getContainerSlot() == containerIndex) return slot;
        return null;
    }

    /** Where the button is on screen (x, y). */
    private static int[] buttonPos(AbstractContainerScreen<?> screen) {
        if (screen instanceof CreativeModeInventoryScreen) { // the off-hand slot is left of the player: the button mirrors it on the right
            return new int[]{screen.getGuiLeft() + 126, screen.getGuiTop() + 19}; // same row as the off-hand slot frame
        }
        Slot offhand = inventorySlot(screen, 40); // survival: right above the off-hand slot, where the free space is
        int x = offhand != null ? offhand.x - 1 : 76, y = offhand != null ? offhand.y - BUTTON_H - 1 : 43;
        return new int[]{screen.getGuiLeft() + x, screen.getGuiTop() + y};
    }

    private static boolean overButton(AbstractContainerScreen<?> screen, double mx, double my) {
        int[] b = buttonPos(screen);
        return mx >= b[0] && mx < b[0] + BUTTON_W && my >= b[1] && my < b[1] + BUTTON_H;
    }

    private static boolean insidePanel(AbstractContainerScreen<?> screen, double mx, double my) {
        int x = panelX(screen), y = panelY(screen);
        return open && mx >= x && mx < x + PANEL_W && my >= y && my < y + panelH(screen);
    }

    /** Index of the fabric row under the mouse, or -1. */
    private static int rowAt(AbstractContainerScreen<?> screen, double my) {
        double y = my - panelY(screen) - PAD - 1;
        if (y < 0 || y >= visibleRows(screen) * ROW_H) return -1;
        double at = y / ROW_H + scrollPos; // the list may be part-way through a glide
        if ((at - Math.floor(at)) * ROW_H > ROW_H - 2) return -1;
        int row = (int) Math.floor(at);
        return row >= 0 && row < FabricRegistry.all().size() ? row : -1;
    }

    /** A flat dark panel with a thin border. */
    private static void panel(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private record Recipe(List<ItemStack> grouped, List<Item> raw) {}

    /** What a fabric is learned from: one stack per kind of ingredient, and the plain list. Null for the fabric everyone starts with. */
    private static Recipe recipeOf(int fabric) {
        List<Item> raw = FabricRegistry.byIndex(fabric).recipe();
        if (raw.isEmpty()) return null;
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (Item item : raw) counts.merge(item, 1, Integer::sum);
        return new Recipe(counts.entrySet().stream().map(e -> new ItemStack(e.getKey(), e.getValue())).toList(), raw);
    }

    private static int have(Item item) {
        int n = 0;
        for (ItemStack st : Minecraft.getInstance().player.getInventory().items) if (st.is(item)) n += st.getCount();
        return n;
    }

    /** Where everything in the recipe card is, worked out once for drawing and clicking. */
    private record Card(int x, int y, int w, int h, Recipe recipe, List<FormattedCharSequence> sub, int slotsY, int btnY, boolean learned, boolean craftable) {
        boolean overButton(double mx, double my) {
            return recipe != null && mx >= x + PAD && mx < x + w - PAD && my >= btnY && my < btnY + BTN_H;
        }
    }

    private static Card card(AbstractContainerScreen<?> screen) {
        var mc = Minecraft.getInstance();
        Recipe recipe = recipeOf(info);
        boolean learned = FabricUnlocks.has(mc.player, info);
        Component subtitle = Component.translatable(info == 0 ? "gui.botwclimb.recipe_start" : recipe == null ? "gui.botwclimb.recipe_none"
                : learned ? "gui.botwclimb.recipe_known" : "gui.botwclimb.recipe_title");
        List<FormattedCharSequence> sub = mc.font.split(subtitle, CARD_W - PAD * 2);
        int y = panelY(screen), slotsY = y + PAD + 11 + sub.size() * 10 + 3;
        int h = slotsY - y + PAD;
        int btnY = 0;
        boolean craftable = false;
        if (recipe != null) {
            int rows = Math.max(1, (recipe.grouped().size() + PER_ROW - 1) / PER_ROW);
            btnY = slotsY + rows * SLOT + 5;
            h = btnY - y + BTN_H + PAD;
            craftable = !learned && ClothCrafting.plan(mc.player.getInventory(), recipe.raw()) != null;
        }
        return new Card(cardX(screen), y, CARD_W, h, recipe, sub, slotsY, btnY, learned, craftable);
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!shown(event)) return;
        var screen = (AbstractContainerScreen<?>) event.getScreen();
        GuiGraphics g = event.getGuiGraphics();
        var mc = Minecraft.getInstance();
        int mx = event.getMouseX(), my = event.getMouseY();
        int selected = mc.player.getData(BotwClimb.PARAGLIDER).fabric;
        int unlocked = mc.player.getData(BotwClimb.FABRIC_UNLOCKS);

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        Component tooltipTitle = null;
        List<Component> tooltip = null;
        ItemStack itemTip = null;
        if (open) {
            int x = panelX(screen), y = panelY(screen), h = panelH(screen);
            List<FabricDefinition> all = FabricRegistry.all();
            panel(g, x, y, PANEL_W, h, PANEL);
            int hover = insidePanel(screen, mx, my) ? rowAt(screen, my) : -1;
            long now = net.minecraft.Util.getMillis();
            float diff = scroll - scrollPos;
            scrollPos = Math.abs(diff) < 0.004f ? scroll : scrollPos + diff * Math.min(1f, (now - scrollAt) / 70f);
            scrollAt = now;
            int listTop = y + PAD + 1, listH = visibleRows(screen) * ROW_H;
            g.enableScissor(x + 1, y + 1, x + PANEL_W - 1, y + h - 1);
            int first = (int) Math.floor(scrollPos);
            for (int i = first; i <= first + visibleRows(screen) && i < all.size(); i++) {
                if (i < 0) continue;
                int ry = listTop + Math.round((i - scrollPos) * ROW_H), rx = x + (PANEL_W - 40) / 2; // the swatch is centred in the panel
                g.fill(rx - 1, ry - 1, rx + 41, ry + 17, FRAME);
                com.botwclimb.client.ClientCompat.blit(g, all.get(i).texture(), rx, ry, 40, 16, 0f, 46f, 40, 16, 128, 64);
                if (!FabricUnlocks.has(unlocked, i)) { // not learned yet: dimmed, with a question mark
                    g.fill(rx, ry, rx + 40, ry + 16, 0xC00C0F13);
                    g.drawCenteredString(mc.font, "?", rx + 20, ry + 4, MUTED);
                }
                if (i == selected) g.renderOutline(rx - 2, ry - 2, 44, 20, WORN);
                if (i == info) g.renderOutline(rx - 1, ry - 1, 42, 18, PICKED);
                if (i == hover) g.fill(rx, ry, rx + 40, ry + 16, 0x30FFFFFF);
            }
            g.disableScissor();
            if (hover >= 0) {
                tooltipTitle = Component.translatable("fabric.botwclimb." + all.get(hover).id());
                tooltip = List.of(tooltipTitle, Component.translatable(FabricUnlocks.has(unlocked, hover) ? "gui.botwclimb.fabric_wear" : "gui.botwclimb.fabric_locked").withStyle(ChatFormatting.GRAY));
            }
            if (info >= 0) { // the recipe card, popping out from the menu side
                Card c = card(screen);
                float p = Mth.clamp((net.minecraft.Util.getMillis() - infoAt) / 160f, 0f, 1f);
                p = 1f - (1f - p) * (1f - p) * (1f - p);
                float scale = 0.8f + 0.2f * p;
                float px = panelOnRight(screen) ? c.x() : c.x() + c.w(), py = c.y() + 12;
                g.pose().pushPose();
                g.pose().translate(px, py, 0);
                g.pose().scale(scale, scale, 1f);
                g.pose().translate(-px, -py, 0);
                panel(g, c.x(), c.y(), c.w(), c.h(), PANEL);
                if (p > 0.5f) {
                    g.drawString(mc.font, mc.font.plainSubstrByWidth(Component.translatable("fabric.botwclimb." + all.get(info).id()).getString(), c.w() - PAD * 2), c.x() + PAD, c.y() + PAD, TEXT, false);
                    for (int n = 0; n < c.sub().size(); n++) g.drawString(mc.font, c.sub().get(n), c.x() + PAD, c.y() + PAD + 11 + n * 10, MUTED, false);
                    if (c.recipe() != null) {
                        List<ItemStack> ingredients = c.recipe().grouped();
                        for (int n = 0; n < ingredients.size(); n++) {
                            int sx = c.x() + PAD + (n % PER_ROW) * SLOT, sy = c.slotsY() + (n / PER_ROW) * SLOT;
                            ItemStack need = ingredients.get(n);
                            boolean enough = have(need.getItem()) >= need.getCount();
                            g.fill(sx, sy, sx + SLOT, sy + SLOT, enough || c.learned() ? FRAME : 0xFF7A2A2A); // a missing ingredient has a red frame
                            g.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, SLOT_BG);
                            g.renderItem(need, sx + 1, sy + 1);
                            g.renderItemDecorations(mc.font, need, sx + 1, sy + 1);
                            if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) itemTip = need;
                        }
                        int ax = c.x() + PAD + PER_ROW * SLOT + 3, ay = c.slotsY() + (SLOT - 8) / 2;
                        g.drawString(mc.font, "\u2192", ax, ay, MUTED, false);
                        int rx = ax + 10, ry = c.slotsY();
                        g.fill(rx, ry, rx + SLOT, ry + SLOT, FRAME);
                        g.fill(rx + 1, ry + 1, rx + SLOT - 1, ry + SLOT - 1, SLOT_BG);
                        com.botwclimb.client.ClientCompat.blit(g, all.get(info).texture(), rx + 1, ry + 3, 16, 12, 10f, 48f, 20, 14, 128, 64); // the fabric itself is what you get
                        // the Craft button: it only crafts when pressed, and only when everything is in the inventory
                        boolean hot = c.craftable() && c.overButton(mx, my);
                        int bx = c.x() + PAD, bw = c.w() - PAD * 2;
                        g.fill(bx, c.btnY(), bx + bw, c.btnY() + BTN_H, c.craftable() ? (hot ? 0xFF0E9A47 : 0xFF087F3C) : 0xFF1A1E24);
                        g.drawCenteredString(mc.font, Component.translatable(c.learned() ? "gui.botwclimb.recipe_button_known" : c.craftable() ? "gui.botwclimb.recipe_button" : "gui.botwclimb.recipe_button_missing"),
                                bx + bw / 2, c.btnY() + (BTN_H - 8) / 2, c.craftable() ? 0xFFFFFFFF : MUTED);
                    }
                }
                g.pose().popPose();
            }
        }
        // the button: a vanilla button with the worn fabric's pattern on it, pressed in while the menu is open
        int[] bp = buttonPos(screen);
        boolean overButton = overButton(screen, mx, my);
        com.botwclimb.client.ClientCompat.blitSprite(g, open || overButton ? BUTTON_ON : BUTTON, bp[0], bp[1], BUTTON_W, BUTTON_H);
        com.botwclimb.client.ClientCompat.blit(g, FabricRegistry.byIndex(selected).texture(), bp[0] + 3, bp[1] + 3, 12, 12, 10f, 48f, 20, 14, 128, 64);
        if (itemTip != null) g.renderTooltip(mc.font, itemTip, mx, my);
        else if (tooltip != null) g.renderTooltip(mc.font, tooltip, Optional.empty(), mx, my);
        else if (overButton && !open) g.renderTooltip(mc.font, Component.translatable("gui.botwclimb.cloth_button_tip"), mx, my);
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!shown(event) || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (!insidePanel(screen, event.getMouseX(), event.getMouseY())) return;
        scroll = Math.max(0, Math.min(maxScroll(screen), scroll - (int) Math.signum(event.getScrollDeltaY())));
        event.setCanceled(true); // scrolling the menu must not also scroll anything under it
    }

    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!shown(event) || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        double mx = event.getMouseX(), my = event.getMouseY();
        if (overButton(screen, mx, my)) {
            event.setCanceled(true);
            if (event.getButton() == 0) {
                open = !open;
                info = -1;
                scroll = 0;
            }
            return;
        }
        if (open && info >= 0) { // the recipe card
            Card c = card(screen);
            if (mx >= c.x() && mx < c.x() + c.w() && my >= c.y() && my < c.y() + c.h()) {
                event.setCanceled(true);
                if (event.getButton() == 0 && c.craftable() && c.overButton(mx, my)) PacketDistributor.sendToServer(new ClothCraftPayload(info));
                return;
            }
        }
        if (!insidePanel(screen, mx, my)) return; // the rest of the inventory works as usual
        event.setCanceled(true); // clicks in the menu never reach the inventory slots
        int row = rowAt(screen, my);
        if (row < 0) return;
        if (info != row) infoAt = net.minecraft.Util.getMillis();
        info = row; // any click shows the recipe
        var player = Minecraft.getInstance().player;
        if (event.getButton() == 0 && FabricUnlocks.has(player, row)) {
            PacketDistributor.sendToServer(new ParagliderFabricPayload(row));
            player.getData(BotwClimb.PARAGLIDER).fabric = row; // show it straight away
        }
    }

    /** Closing the inventory closes the menu with it. */
    @SubscribeEvent
    public static void onClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?>) {
            open = false;
            info = -1;
        }
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?>) scroll = 0;
        scrollPos = 0;
    }
}
