package com.botwclimb.common.paraglider;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Learning a paraglider fabric by crafting it from the fabric menu. The ingredients are used up and the fabric is learned on the
 * spot: there is no cloth item, so a fabric cannot be handed round.
 */
public final class ClothCrafting {
    private ClothCrafting() {}

    /**
     * Which inventory slot supplies each ingredient (one item each, a slot may supply several), or null when something is
     * missing. The same check runs on the client for the button and on the server for real.
     */
    public static int[] plan(Inventory inv, List<Item> ingredients) {
        int[] used = new int[inv.items.size()], slotOf = new int[ingredients.size()];
        for (int k = 0; k < ingredients.size(); k++) {
            slotOf[k] = -1;
            for (int s = 0; s < inv.items.size(); s++) {
                ItemStack st = inv.items.get(s);
                if (!st.isEmpty() && st.getCount() - used[s] > 0 && st.is(ingredients.get(k))) {
                    used[s]++;
                    slotOf[k] = s;
                    break;
                }
            }
            if (slotOf[k] < 0) return null;
        }
        return slotOf;
    }

    public static void craft(ServerPlayer p, int index) {
        if (index <= 0 || index >= FabricRegistry.all().size() || FabricUnlocks.has(p, index)) return; // nothing to learn
        int[] slots = plan(p.getInventory(), FabricRegistry.byIndex(index).recipe());
        if (slots == null) return; // something is missing: nothing is taken
        for (int s : slots) p.getInventory().items.get(s).shrink(1);
        FabricUnlocks.unlock(p, index);
        p.displayClientMessage(Component.translatable("message.botwclimb.cloth_unlocked", Component.translatable("fabric.botwclimb." + FabricRegistry.byIndex(index).id())), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.PLAYERS, 1f, 1.1f);
        p.inventoryMenu.broadcastChanges();
    }
}
