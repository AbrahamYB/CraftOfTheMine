package com.botwclimb.common.paraglider;

import com.botwclimb.BotwClimb;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Which paraglider fabrics a player has learned. One bit per fabric, in the order of {@link FabricRegistry}; the first
 * fabric (ordinary) is known from the start. The mask is saved with the player and synced to them, so the picker can show
 * it at any time, not only while gliding.
 */
public final class FabricUnlocks {
    private FabricUnlocks() {}

    public static boolean has(int mask, int index) {
        return index == 0 || (index > 0 && index < 31 && (mask >> index & 1) != 0);
    }

    public static boolean has(Player p, int index) {
        return has(p.getData(BotwClimb.FABRIC_UNLOCKS), index);
    }

    public static void unlock(ServerPlayer p, int index) {
        if (index > 0 && index < 31) p.setData(BotwClimb.FABRIC_UNLOCKS, p.getData(BotwClimb.FABRIC_UNLOCKS) | (1 << index));
    }

    public static void unlockAll(ServerPlayer p) {
        p.setData(BotwClimb.FABRIC_UNLOCKS, (1 << FabricRegistry.all().size()) - 1);
    }

    /** Back to the start: only the ordinary fabric, and it is worn. */
    public static void reset(ServerPlayer p) {
        p.setData(BotwClimb.FABRIC_UNLOCKS, 0);
        ParagliderController.forceFabric(p, 0);
    }
}
