package com.botwclimb.common.paraglider;

import java.util.ArrayList;
import java.util.List;

/** The known fabrics. Adding one = a PNG + one line here (+ its entry in tools/make_fabrics.py). */
public final class FabricRegistry {
    private static final List<FabricDefinition> ALL = new ArrayList<>();

    static {
        add("ordinary", true);
        add("zonai", true);
        add("royal_hyrulean", true);
        add("sheikah", true);
        add("gerudo", true);
        add("zora", true);
        add("goron", true);
        add("yiga", true);
        add("cece", true);
        add("addison", false); // hand-drawn sign fabric: no long ribbons
        add("chuchu", true);
        add("aerocuda", true);
        add("korok", true);
        add("champion", true);
        add("majora", true);
        add("demon_king", true);
    }

    private FabricRegistry() {}

    private static void add(String id, boolean ribbons) {
        ALL.add(new FabricDefinition(id, ribbons));
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
