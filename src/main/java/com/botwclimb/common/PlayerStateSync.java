package com.botwclimb.common;

import com.botwclimb.BotwClimb;
import com.botwclimb.network.PlayerStatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends a player their saved values (fabric unlocks, stamina upgrades, altar credit) when they join and whenever one of them changes, so
 * nothing that sets them has to remember to sync. The check is three reads per player per tick.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID)
public final class PlayerStateSync {
    private PlayerStateSync() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        var d = p.getData(BotwClimb.CLIMB); // server side only, so it also remembers what was last sent
        int unlocks = p.getData(BotwClimb.FABRIC_UNLOCKS), upgrades = p.getData(BotwClimb.STAMINA_UPGRADES);
        float credit = p.getData(BotwClimb.ALTAR_CREDIT);
        if (d.sentUnlocks == unlocks && d.sentUpgrades == upgrades && Float.compare(d.sentCredit, credit) == 0) return;
        boolean joining = d.sentUnlocks == -1;
        d.sentUnlocks = unlocks;
        d.sentUpgrades = upgrades;
        d.sentCredit = credit;
        if (p.connection.hasChannel(PlayerStatePayload.TYPE)) // an older client has no such channel; it gets the values through attachment sync instead
            com.botwclimb.common.Net.toPlayer(p, new PlayerStatePayload(unlocks, upgrades, credit));
        if (joining) { // what was left on the wheel when they last left comes back as it was, for the server's account too
            float left = p.getData(BotwClimb.STAMINA_LEFT);
            boolean tired = p.getData(BotwClimb.STAMINA_TIRED);
            if (left >= 0f) {
                d.sStamina = left;
                d.sExhausted = tired;
            }
            com.botwclimb.common.Net.toPlayer(p, new com.botwclimb.network.StaminaPayload(left, tired));
        }
    }
}
