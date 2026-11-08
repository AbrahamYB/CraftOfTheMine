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
        return InteractionResult.sidedSuccess(level.isClientSide());
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

    /** The level a server player is in. */
    public static ServerLevel level(ServerPlayer p) {
        //? if >=1.21.6 {
        /*return p.level();
        *///?} else {
        return p.serverLevel();
        //?}
    }

    /** Who may use the admin commands (game masters, level 2). */
    public static java.util.function.Predicate<net.minecraft.commands.CommandSourceStack> gamemaster() {
        //? if >=1.21.9 {
        /*return net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS);
        *///?} else {
        return s -> s.hasPermission(2);
        //?}
    }

    /** The 36 main inventory slots (hotbar and storage). */
    public static net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items(net.minecraft.world.entity.player.Inventory inv) {
        //? if >=1.21.5 {
        /*return inv.getNonEquipmentItems();
        *///?} else {
        return inv.items;
        //?}
    }

    /** The push reaction that keeps a block from being moved by pistons (renamed in 26.3). */
    //? if >=26.3 {
    /*public static final net.minecraft.world.level.material.PushReaction IMMOVABLE = net.minecraft.world.level.material.PushReaction.IMMOVEABLE;
    *///?} else {
    public static final net.minecraft.world.level.material.PushReaction IMMOVABLE = net.minecraft.world.level.material.PushReaction.BLOCK;
    //?}

    /** How a saved number is written for an attachment (a plain codec, or a map codec from 1.21.11 on). */
    //? if >=1.21.6 {
    /*public static <T> com.mojang.serialization.MapCodec<T> attach(com.mojang.serialization.Codec<T> codec) {
        return codec.fieldOf("value");
    }
    *///?} else {
    // Writes {value: x} like 1.21.6+ so a world moves up a version; still reads the bare value older builds wrote.
    public static <T> com.mojang.serialization.Codec<T> attach(com.mojang.serialization.Codec<T> codec) {
        return com.mojang.serialization.Codec.either(codec.fieldOf("value").codec(), codec)
                .xmap(e -> e.map(a -> a, b -> b), com.mojang.datafixers.util.Either::left);
    }
    //?}

    /** Also syncs an attachment the old NeoForge way (removed in 21.2; PlayerStateSync covers every later version), so a build here still agrees with builds from before PlayerStateSync. */
    public static <T> net.neoforged.neoforge.attachment.AttachmentType.Builder<T> sync(net.neoforged.neoforge.attachment.AttachmentType.Builder<T> b,
            net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, T> codec) {
        //? if <1.21.2 {
        return b.sync(codec);
        //?} else {
        /*return b;
        *///?}
    }

    /** The player's name. */
    public static String name(ServerPlayer p) {
        //? if >=1.21.9 {
        /*return p.getGameProfile().name();
        *///?} else {
        return p.getGameProfile().getName();
        //?}
    }

    /** Where the world's spawn is. */
    public static net.minecraft.core.BlockPos worldSpawn(ServerLevel level) {
        //? if >=1.21.9 {
        /*return level.getRespawnData().pos();
        *///?} else {
        return level.getSharedSpawnPos();
        //?}
    }

    /** The damage an arrow is set to do (before speed). */
    public static double baseDamage(net.minecraft.world.entity.projectile.AbstractArrow arrow) {
        //? if >=1.21.5 {
        /*return arrow.baseDamage; // public through this version's access transformer
        *///?} else {
        return arrow.getBaseDamage();
        //?}
    }

    /** What a command source with this permission level (0 none, 2 game master) may do; for the tests. */
    //? if >=1.21.11 {
    /*public static net.minecraft.server.permissions.PermissionSet permission(int level) {
        return level >= 2 ? net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER : net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS;
    }
    *///?} else {
    public static int permission(int level) {
        return level;
    }
    //?}

    /** A short message above the hotbar. */
    public static void overlay(net.minecraft.world.entity.player.Player p, net.minecraft.network.chat.Component message) {
        //? if >=26.1 {
        /*p.sendOverlayMessage(message);
        *///?} else {
        p.displayClientMessage(message, true);
        //?}
    }
}
