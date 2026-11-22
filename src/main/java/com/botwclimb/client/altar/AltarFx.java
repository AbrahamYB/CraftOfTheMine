package com.botwclimb.client.altar;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.climb.ClimbConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * How the altar's lights look to the local player. Nothing here is shared: every client works it out from its own
 * player's upgrades, so another player standing at the same altar sees their own progress, not yours.
 * Before the first upgrade everything is shadowed. From the first upgrade the runes light up green and each upgrade lights one
 * quarter (4 on the inner ring, then 4 on the outer one); once all are bought everything stays green.
 */
public final class AltarFx {
    public static final int GREEN = 0x00F25A, SHADOW = 0x175126; // the shadowed (lights off) green

    private AltarFx() {}

    public static int upgrades(LocalPlayer p) {
        return Mth.clamp(p.getData(BotwClimb.STAMINA_UPGRADES), 0, ClimbConstants.STAMINA_MAX_UPGRADES);
    }

    /** Is this quarter lit for the player? ring 1 = inner (upgrades 1-4), ring 0 = outer (upgrades 5-8). */
    public static boolean quarterLit(int upgrades, int ring, int quarter) {
        return upgrades - (1 - ring) * 4 > quarter;
    }
}
