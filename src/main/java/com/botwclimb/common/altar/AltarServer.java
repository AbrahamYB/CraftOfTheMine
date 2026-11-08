package com.botwclimb.common.altar;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.climb.ClimbConstants;
import com.botwclimb.network.AltarOfferPayload;
import com.botwclimb.network.AltarResultPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The server's side of the stamina altar: it believes nothing the client says about values, prices or what is in the inventory. */
public final class AltarServer {
    private static final double REACH_SQ = 8.0 * 8.0;

    private AltarServer() {}

    public static void handle(ServerPlayer p, AltarOfferPayload offer) {
        if (!p.isAlive() || p.distanceToSqr(Vec3.atCenterOf(offer.pos())) > REACH_SQ
                || !(p.level().getBlockState(offer.pos()).getBlock() instanceof StaminaAltarBlock)) return;
        int upgrades = Mth.clamp(p.getData(BotwClimb.STAMINA_UPGRADES), 0, ClimbConstants.STAMINA_MAX_UPGRADES);
        float credit = p.getData(BotwClimb.ALTAR_CREDIT);
        if (!(credit >= 0f) || !Float.isFinite(credit)) credit = 0f; // NaN from a damaged save would be a free upgrade
        if (offer.isRefund()) {
            refund(p, upgrades, credit, offer);
            return;
        }
        Inventory inv = p.getInventory();
        List<AltarRules.Offered> offered = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (AltarOfferPayload.Entry e : offer.entries()) {
            if (e.slot() < 0 || e.slot() >= com.botwclimb.common.Compat.items(inv).size() || !seen.add(e.slot())) {
                reply(p, false, "invalid", false);
                return;
            }
            ItemStack stack = com.botwclimb.common.Compat.items(inv).get(e.slot());
            float unit = AltarValues.unitValue(stack);
            if (stack.isEmpty() || unit <= 0f || e.count() < 1 || e.count() > stack.getCount()) {
                reply(p, false, "invalid", false);
                return;
            }
            offered.add(new AltarRules.Offered(e.slot(), e.count(), unit));
        }
        AltarRules.Result r = AltarRules.evaluate(upgrades, credit, offered);
        if (!r.ok()) {
            reply(p, false, r.reason(), false);
            return;
        }
        for (AltarRules.Take t : r.take()) com.botwclimb.common.Compat.items(inv).get(t.slot()).shrink(t.count());
        p.setData(BotwClimb.STAMINA_UPGRADES, r.upgradesAfter());
        p.setData(BotwClimb.ALTAR_CREDIT, r.creditAfter());
        inv.setChanged();
        p.containerMenu.broadcastChanges();
        reply(p, true, "", r.upgradesAfter() >= ClimbConstants.STAMINA_MAX_UPGRADES);
    }

    /** At the last upgrade, what credit is left is paid back as an item of the player's choosing from the valuables list. */
    private static void refund(ServerPlayer p, int upgrades, float credit, AltarOfferPayload offer) {
        if (upgrades < ClimbConstants.STAMINA_MAX_UPGRADES) {
            reply(p, false, "not_maxed", false);
            return;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(offer.refund()).orElse(null);
        ItemStack sample = item == null ? ItemStack.EMPTY : new ItemStack(item);
        float unit = sample.isEmpty() || AltarValues.isGear(sample) ? 0f : AltarValues.looseValue(item);
        int count = AltarRules.refundCount(credit, unit);
        if (count < 1) {
            reply(p, false, "too_little", false);
            return;
        }
        p.setData(BotwClimb.ALTAR_CREDIT, 0f);
        int left = count;
        while (left > 0) {
            int n = Math.min(left, item.getDefaultMaxStackSize());
            p.getInventory().placeItemBackInInventory(new ItemStack(item, n));
            left -= n;
        }
        p.containerMenu.broadcastChanges();
        reply(p, true, "refund", false);
    }

    private static void reply(ServerPlayer p, boolean ok, String reason, boolean finale) {
        com.botwclimb.common.Net.toPlayer(p, new AltarResultPayload(ok, reason, finale));
    }
}
