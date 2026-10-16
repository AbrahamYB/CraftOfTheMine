package com.botwclimb;

import com.botwclimb.common.climb.ClimbData;
import com.botwclimb.network.NetworkHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

@Mod(BotwClimb.MOD_ID)
public class BotwClimb {
    public static final String MOD_ID = "botwclimb";

    /** Set with -Dbotwclimb.debug=true (dev only): draws the planned jump path with particles. */
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static final boolean DEBUG = Boolean.getBoolean("botwclimb.debug");

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    /** Not serialized and not synced by NeoForge: client and server each keep their own copy. */
    public static final Supplier<AttachmentType<ClimbData>> CLIMB =
            ATTACHMENTS.register("climb", () -> AttachmentType.builder(ClimbData::new).build());

    public static final Supplier<AttachmentType<com.botwclimb.common.paraglider.ParagliderData>> PARAGLIDER =
            ATTACHMENTS.register("paraglider",
                    () -> AttachmentType.builder(com.botwclimb.common.paraglider.ParagliderData::new).build());

    /** The player's chosen paraglider fabric (index into FabricRegistry). Saved with the player. */
    public static final Supplier<AttachmentType<Integer>> FABRIC =
            ATTACHMENTS.register("fabric", () -> AttachmentType.builder(() -> 0)
                    .serialize(com.mojang.serialization.Codec.INT).copyOnDeath().build());

    public BotwClimb(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(NetworkHandler::register);
    }
}
