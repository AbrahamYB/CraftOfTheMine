package com.botwclimb.client.guide;

import com.botwclimb.BotwClimb;
import com.botwclimb.client.ClientCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;

/** Hovering the altar or the terminal in an inventory and holding W opens its guide, as the tooltip says. */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class GuideHooks {
    private static final int HOLD = 14;
    private static int held;

    private GuideHooks() {}

    /** The guide scene an item opens, or -1. */
    private static int sceneFor(ItemStack stack) {
        Item i = stack.getItem();
        return i == BotwClimb.ALTAR_ITEM.get() ? 0 : i == BotwClimb.TERMINAL_ITEM.get() ? 1 : -1;
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (sceneFor(event.getItemStack()) < 0) return;
        String text = Component.translatable("gui.botwclimb.guide.hold").getString();
        int done = Math.round(text.length() * Math.min(1f, held / (float) HOLD)); // the line fills green while W is held
        event.getToolTip().add(Component.literal(text.substring(0, done)).withStyle(ChatFormatting.GREEN)
                .append(Component.literal(text.substring(done)).withStyle(ChatFormatting.DARK_GRAY)));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        int scene = -1;
        if (mc.screen instanceof AbstractContainerScreen<?> s && s.getSlotUnderMouse() != null) scene = sceneFor(s.getSlotUnderMouse().getItem());
        if (scene < 0 || !ClientCompat.keyDown(GLFW.GLFW_KEY_W)) {
            held = 0;
            return;
        }
        if (++held >= HOLD) {
            held = 0;
            mc.setScreen(new GuideScreen(GuideScenes.all(), scene, mc.screen));
        }
    }
}
