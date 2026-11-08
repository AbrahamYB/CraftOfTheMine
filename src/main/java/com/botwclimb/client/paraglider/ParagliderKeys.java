package com.botwclimb.client.paraglider;

import com.botwclimb.BotwClimb;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * The paraglider shortcut (Options > Controls > Craft of the Mine). Default Shift + Space.
 * The KeyModifier only lets the Controls menu show and rebind the combo; {@link ChordInputHandler} does the real
 * ordered detection and the Space consumption.
 */
//? if >=1.21.8 {
/*@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
*///?} else {
@EventBusSubscriber(modid = BotwClimb.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//?}
public final class ParagliderKeys {
    public static final KeyMapping PARAGLIDE = new KeyMapping("key.botwclimb.paraglide", KeyConflictContext.IN_GAME,
            KeyModifier.SHIFT, com.botwclimb.client.ClientCompat.KEY_TYPE, com.botwclimb.client.ClientCompat.KEY_SPACE, com.botwclimb.client.ClimbKeys.CATEGORY);

    private ParagliderKeys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(PARAGLIDE);
    }
}
