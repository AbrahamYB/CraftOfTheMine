package com.botwclimb.common;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The few calls whose shape differs between the Minecraft versions this mod is built for, each in one place (Stonecutter picks the right
 * branch per version). Everything else in the mod calls these instead of putting version conditions in its own code.
 */
public final class Compat {
    private Compat() {}

    /** Teleports a player to a position and facing, in any level. */
    public static void teleport(ServerPlayer p, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        //? if >=1.21.2 {
        /*p.teleportTo(level, x, y, z, java.util.Set.of(), yaw, pitch, false);
        *///?} else {
        p.teleportTo(level, x, y, z, yaw, pitch);
        //?}
    }

    /** The result of a block use that did something. */
    public static InteractionResult success(Level level) {
        //? if >=1.21.2 {
        /*return InteractionResult.SUCCESS;
        *///?} else {
        return InteractionResult.sidedSuccess(level.isClientSide);
        //?}
    }

    /** A registry of the level (enchantments, for instance). */
    public static <T> Registry<T> registry(Level level, ResourceKey<? extends Registry<T>> key) {
        //? if >=1.21.2 {
        /*return level.registryAccess().lookupOrThrow(key);
        *///?} else {
        return level.registryAccess().registryOrThrow(key);
        //?}
    }

    /** The registry entry for a key (enchantments and the like), if there is one. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> java.util.Optional<net.minecraft.core.Holder.Reference<T>> holder(Level level, ResourceKey<T> key) {
        Registry<T> r = registry(level, (ResourceKey) key.registryKey());
        //? if >=1.21.2 {
        /*return r.get(key);
        *///?} else {
        return r.getHolder(key);
        //?}
    }

    /** A new, not yet added, entity of a type. */
    public static <T extends Entity> T create(EntityType<T> type, ServerLevel level) {
        //? if >=1.21.2 {
        /*return type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        *///?} else {
        return type.create(level);
        //?}
    }

    /** The lowest block y of a level. */
    public static int minY(Level level) {
        //? if >=1.21.2 {
        /*return level.getMinY();
        *///?} else {
        return level.getMinBuildHeight();
        //?}
    }
}
