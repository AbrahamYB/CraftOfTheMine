package com.botwclimb.common.paraglider;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** The known fabrics. Adding one = a PNG + one line here (its recipe) (+ its entry in tools/make_fabrics.py). */
public final class FabricRegistry {
    private static final List<FabricDefinition> ALL = new ArrayList<>();

    static {
        add("ordinary", true);
        // each recipe is chosen by how the fabric looks: its main colour as wool, its second colour as a dye, its trim as a metal or
        // gem, and an item that tells its story. Two wool and two string always make up the cloth itself.
        add("zonai", true, Items.WHITE_WOOL, Items.YELLOW_DYE, Items.COPPER_INGOT);
        add("royal_hyrulean", true, Items.RED_WOOL, Items.BROWN_DYE, Items.GOLD_INGOT);
        add("sheikah", true, Items.GRAY_WOOL, Items.WHITE_DYE, Items.ENDER_PEARL);
        add("gerudo", true, Items.CYAN_WOOL, Items.YELLOW_DYE, Items.GOLD_INGOT, Items.SAND);
        add("zora", true, Items.CYAN_WOOL, Items.LIGHT_BLUE_DYE, Items.PRISMARINE_SHARD);
        add("goron", true, Items.RED_WOOL, Items.ORANGE_DYE, Items.MAGMA_CREAM);
        add("yiga", true, Items.YELLOW_WOOL, Items.BLACK_DYE, Items.IRON_NUGGET);
        add("cece", true, Items.GRAY_WOOL, Items.PURPLE_DYE, Items.PINK_PETALS);
        add("addison", false, Items.WHITE_WOOL, Items.CYAN_DYE, Items.YELLOW_DYE, Items.PAPER); // hand-drawn sign fabric: no long ribbons
        add("chuchu", true, Items.LIGHT_BLUE_WOOL, Items.ORANGE_DYE, Items.SLIME_BALL);
        add("aerocuda", true, Items.YELLOW_WOOL, Items.GREEN_DYE, Items.FEATHER);
        add("korok", true, Items.WHITE_WOOL, Items.GREEN_DYE, Items.OAK_SAPLING);
        add("champion", true, Items.LIGHT_BLUE_WOOL, Items.WHITE_DYE, Items.LEATHER);
        add("majora", true, Items.PURPLE_WOOL, Items.YELLOW_DYE, Items.ENDER_EYE);
        add("demon_king", true, Items.GRAY_WOOL, Items.BLACK_DYE, Items.BLAZE_POWDER);
    }

    private FabricRegistry() {}

    /** @param wool the main colour and what comes after it; the first item also fills the second wool slot, and two string are added. */
    private static void add(String id, boolean ribbons, Item... recipe) {
        List<Item> items = new ArrayList<>();
        if (recipe.length > 0) {
            items.add(recipe[0]);
            items.add(recipe[0]);
            items.add(Items.STRING);
            items.add(Items.STRING);
            items.addAll(List.of(recipe).subList(1, recipe.length));
        }
        ALL.add(new FabricDefinition(id, ribbons, List.copyOf(items)));
    }

    public static List<FabricDefinition> all() {
        return ALL;
    }

    public static FabricDefinition byIndex(int index) {
        return ALL.get(Math.floorMod(index, ALL.size()));
    }

    /** @return the index of the fabric with this id, or -1 */
    public static int indexOf(String id) {
        for (int i = 0; i < ALL.size(); i++) if (ALL.get(i).id().equals(id)) return i;
        return -1;
    }
}
