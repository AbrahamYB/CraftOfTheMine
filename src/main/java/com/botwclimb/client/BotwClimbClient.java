package com.botwclimb.client;

import com.botwclimb.BotwClimb;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** The client half of the mod's entry point: puts the settings screen behind the mod list's Config button. */
@Mod(value = BotwClimb.MOD_ID, dist = Dist.CLIENT)
public final class BotwClimbClient {
    public BotwClimbClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new BotwConfigScreen(parent));
    }
}
