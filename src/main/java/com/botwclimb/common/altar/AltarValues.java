package com.botwclimb.common.altar;

import com.botwclimb.BotwClimb;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What an item is worth at the stamina altar, in points. Loose valuables come from the data map
 * {@code data/botwclimb/data_maps/item/altar_value.json} (change it with a datapack, no code needed); anything not listed is
 * worth 0. Tools and armor are worked out from what they were made of: the diamonds in the recipe times the value of a
 * diamond, a flat bonus for netherite, a flat bonus for a level 4-5 enchantment, all scaled by the durability left, and
 * they need more than half of it left. Iron and gold gear are worth their ingots per part. Everything else is 0.
 */
public final class AltarValues {
    public static final DataMapType<Item, Float> ALTAR_VALUE = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "altar_value"), Registries.ITEM, Codec.FLOAT)
            .synced(Codec.FLOAT, false).build();

    /** Parts in a tool or armor piece: the ingots or diamonds in its crafting recipe. */
    private static final Map<String, Integer> PARTS = Map.of("sword", 2, "pickaxe", 3, "axe", 3, "shovel", 1, "hoe", 2,
            "helmet", 5, "chestplate", 8, "leggings", 7, "boots", 4);
    private static final Pattern GEAR = Pattern.compile("^(diamond|netherite|golden|iron)_(sword|pickaxe|axe|shovel|hoe|helmet|chestplate|leggings|boots)$");
    private static final float NETHERITE_TOOL_BONUS = 3f, NETHERITE_ARMOR_BONUS = 4f, ENCHANT_BONUS = 2f;
    private static final float DIAMOND_FALLBACK = 2f, IRON_FALLBACK = 0.1f, GOLD_FALLBACK = 0.2f;

    private AltarValues() {}

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(ALTAR_VALUE);
    }

    /** The data map value of a loose item (0 when it is not listed). */
    public static float looseValue(Item item) {
        Float v = item.builtInRegistryHolder().getData(ALTAR_VALUE);
        return v == null || !Float.isFinite(v) || v <= 0f ? 0f : Math.min(v, 1000f); // a data pack value that is NaN, infinite or absurd must not size the altar search
    }

    public static boolean isGear(ItemStack stack) {
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.getNamespace().equals("minecraft") && GEAR.matcher(key.getPath()).matches(); // another mod's iron_pickaxe is not worth vanilla gear
    }

    /** Durability left as a fraction, 1 for items that do not wear. */
    public static float durabilityLeft(ItemStack stack) {
        if (!stack.isDamageableItem()) return 1f;
        return (float) (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
    }

    /** What one item of this stack is worth in points (0 = the altar will not take it). Gear is one item per stack. */
    public static float unitValue(ItemStack stack) {
        if (stack.isEmpty()) return 0f;
        float left = durabilityLeft(stack);
        if (left <= 0.5f) return 0f; // worn out: not accepted
        if (!isGear(stack)) return looseValue(stack.getItem()) * left; // elytra and tridents wear too
        Matcher m = GEAR.matcher(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
        if (!m.matches()) return 0f;
        String material = m.group(1), type = m.group(2);
        int parts = PARTS.get(type);
        boolean tool = type.equals("sword") || type.equals("pickaxe") || type.equals("axe") || type.equals("shovel") || type.equals("hoe");
        float base = switch (material) {
            case "diamond" -> parts * partValue(Items.DIAMOND, DIAMOND_FALLBACK);
            case "netherite" -> parts * partValue(Items.DIAMOND, DIAMOND_FALLBACK) + (tool ? NETHERITE_TOOL_BONUS : NETHERITE_ARMOR_BONUS);
            case "golden" -> parts * partValue(Items.GOLD_INGOT, GOLD_FALLBACK);
            default -> parts * partValue(Items.IRON_INGOT, IRON_FALLBACK);
        };
        if (hasStrongEnchant(stack)) base += ENCHANT_BONUS;
        return base * left;
    }

    private static float partValue(Item item, float fallback) {
        float v = looseValue(item);
        return v > 0f ? v : fallback;
    }

    /** At least one enchantment of level 4 or 5. */
    private static boolean hasStrongEnchant(ItemStack stack) {
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> e : enchants.entrySet()) {
            if (e.getIntValue() >= 4) return true;
        }
        return false;
    }
}
