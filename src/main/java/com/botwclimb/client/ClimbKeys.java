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

/** The rebindable climb key (Options > Controls > Craft of the Mine). Default Shift + Space. Combos with Shift/Ctrl/Alt can be set in the Controls menu. */
//? if >=1.21.8 {
/*@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
*///?} else {
@EventBusSubscriber(modid = BotwClimb.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//?}
public final class ClimbKeys {
    //? if >=1.21.9 {
    /*public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "main"));
    *///?} else {
    public static final String CATEGORY = "key.categories.botwclimb";
    //?}

    public static final KeyMapping CLIMB = new KeyMapping("key.botwclimb.climb", KeyConflictContext.IN_GAME,
            KeyModifier.SHIFT, com.botwclimb.client.ClientCompat.KEY_TYPE, com.botwclimb.client.ClientCompat.KEY_SPACE, CATEGORY);

    private ClimbKeys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        //? if >=1.21.9
        /*event.registerCategory(CATEGORY);*/
        event.register(CLIMB);
    }
}
