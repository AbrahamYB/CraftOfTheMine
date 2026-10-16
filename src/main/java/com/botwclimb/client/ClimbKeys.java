package com.botwclimb.client;

import com.botwclimb.BotwClimb;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/** The rebindable climb key (Options > Controls > BOTW Climb). Default Shift + Space. Combos with Shift/Ctrl/Alt can be set in the Controls menu. */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClimbKeys {
    public static final KeyMapping CLIMB = new KeyMapping("key.botwclimb.climb", KeyConflictContext.IN_GAME,
            KeyModifier.SHIFT, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, "key.categories.botwclimb");

    private ClimbKeys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CLIMB);
    }
}
