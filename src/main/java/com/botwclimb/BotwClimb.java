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

    /** Which paraglider fabrics the player has learned (one bit per fabric, see FabricUnlocks). Saved, kept on death, sent to the owner by PlayerStateSync. */
    public static final Supplier<AttachmentType<Integer>> FABRIC_UNLOCKS =
            ATTACHMENTS.register("fabric_unlocks", () -> AttachmentType.builder(() -> 0)
                    .serialize(com.mojang.serialization.Codec.INT).copyOnDeath().build());

    /** Stamina capacity upgrades 0..8 (a quarter wheel, 187.5 units each, 4 per extra wheel). Saved, kept on death, sent to the owner by PlayerStateSync. */
    public static final Supplier<AttachmentType<Integer>> STAMINA_UPGRADES =
            ATTACHMENTS.register("stamina_upgrades", () -> AttachmentType.builder(() -> 0)
                    .serialize(com.mojang.serialization.Codec.INT).copyOnDeath().build());

    /** Points left over after an upgrade at the stamina altar, kept for the next one. Saved, kept on death, sent to the owner by PlayerStateSync. */
    public static final Supplier<AttachmentType<Float>> ALTAR_CREDIT =
            ATTACHMENTS.register("altar_credit", () -> AttachmentType.builder(() -> 0f)
                    .serialize(com.mojang.serialization.Codec.FLOAT).copyOnDeath().build());

    // ---- the stamina altar
    private static final net.neoforged.neoforge.registries.DeferredRegister.Blocks BLOCKS =
            net.neoforged.neoforge.registries.DeferredRegister.createBlocks(MOD_ID);
    private static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS =
            net.neoforged.neoforge.registries.DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, MOD_ID);

    public static final net.neoforged.neoforge.registries.DeferredBlock<com.botwclimb.common.altar.StaminaAltarBlock> ALTAR_BLOCK =
            BLOCKS.registerBlock("stamina_altar", com.botwclimb.common.altar.StaminaAltarBlock::new,
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ENDER_CHEST).noOcclusion().lightLevel(s -> 0));
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> ALTAR_ITEM = ITEMS.registerSimpleBlockItem(ALTAR_BLOCK);
    public static final Supplier<net.minecraft.world.level.block.entity.BlockEntityType<com.botwclimb.common.altar.StaminaAltarBlockEntity>> ALTAR_BE =
            BLOCK_ENTITIES.register("stamina_altar", () ->
                    //? if >=1.21.2 {
                    /*new net.minecraft.world.level.block.entity.BlockEntityType<>(com.botwclimb.common.altar.StaminaAltarBlockEntity::new, ALTAR_BLOCK.get()));
                    *///?} else {
                    net.minecraft.world.level.block.entity.BlockEntityType.Builder
                            .of(com.botwclimb.common.altar.StaminaAltarBlockEntity::new, ALTAR_BLOCK.get()).build(null));
                    //?}

    public static final net.neoforged.neoforge.registries.DeferredBlock<com.botwclimb.common.shrine.ShrineTerminalBlock> TERMINAL_BLOCK =
            BLOCKS.registerBlock("shrine_terminal", com.botwclimb.common.shrine.ShrineTerminalBlock::new,
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE).noOcclusion()
                            .strength(3.5f, 6f).requiresCorrectToolForDrops()
                            .lightLevel(s -> s.getValue(com.botwclimb.common.shrine.ShrineTerminalBlock.ACTIVE) ? 9 : 0));
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> TERMINAL_ITEM = ITEMS.registerSimpleBlockItem(TERMINAL_BLOCK);

    private static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.PARTICLE_TYPE, MOD_ID);
    /** The floating Sheikah letters above a lit altar (drawn client side only). */
    public static final Supplier<net.minecraft.core.particles.SimpleParticleType> GLYPH =
            PARTICLES.register("sheikah_glyph", () -> new net.minecraft.core.particles.SimpleParticleType(false));

    public BotwClimb(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, com.botwclimb.client.ClientConfig.SPEC);
        PARTICLES.register(modBus);
        ATTACHMENTS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(NetworkHandler::register);
        modBus.addListener(com.botwclimb.common.altar.AltarValues::register);
        modBus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) { e.accept(ALTAR_ITEM); e.accept(TERMINAL_ITEM); }
        });
    }
}
