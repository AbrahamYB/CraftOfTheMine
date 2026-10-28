package com.botwclimb.common.shrine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Saved with the world: which plots each player has claimed ({@link #SLOTS} of them) and where each player's last trip into the
 * plots started, so the way back knows where to land.
 */
public final class PlotData extends SavedData {
    public static final int SLOTS = 2;
    private static final String NAME = "botwclimb_plots";

    /** Where a ride into the plots began: the terminal that was used and the spot the player stood on. */
    public record Return(ResourceKey<Level> dim, BlockPos terminal, double x, double y, double z, float yaw) {}

    private int next;
    private final Map<UUID, int[]> plots = new HashMap<>();
    private final Map<UUID, Return> returns = new HashMap<>();
    /** The plots (by number) friends may join, and the plot each guest last joined. */
    private final Set<Integer> open = new HashSet<>();
    private final Map<UUID, Integer> guests = new HashMap<>();

    public boolean isOpen(int plot) {
        return open.contains(plot);
    }

    /** Flips whether friends may join this one plot (a player's other plot is not affected); returns the new state. */
    public boolean toggleOpen(int plot) {
        boolean now = open.add(plot);
        if (!now) open.remove(plot);
        setDirty();
        return now;
    }

    /** The plot's own player, or a guest of a plot that is open to friends. */
    public boolean mayUse(UUID player, int plot) {
        return slotOf(player, plot) >= 0 || open.contains(plot) && guests.getOrDefault(player, -1) == plot;
    }

    public int guestPlot(UUID player) {
        return guests.getOrDefault(player, -1);
    }

    public void setGuest(UUID player, int plot) {
        guests.put(player, plot);
        setDirty();
    }

    public static PlotData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(PlotData::new, PlotData::load, null), NAME);
    }

    /** The plot number in this slot, or -1 when the slot is still free. */
    public int plot(UUID player, int slot) {
        int[] p = plots.get(player);
        return p == null ? -1 : p[slot];
    }

    /** Claims the slot with the next free plot number and returns it; an already claimed slot keeps its plot. */
    public int claim(UUID player, int slot) {
        int[] p = plots.computeIfAbsent(player, k -> {
            int[] a = new int[SLOTS];
            Arrays.fill(a, -1);
            return a;
        });
        if (p[slot] < 0) {
            p[slot] = next++;
            setDirty();
        }
        return p[slot];
    }

    /** Which of the player's slots holds this plot number, or -1. */
    public int slotOf(UUID player, int plot) {
        for (int s = 0; s < SLOTS; s++) if (plot(player, s) == plot) return s;
        return -1;
    }

    public Return returnPoint(UUID player) {
        return returns.get(player);
    }

    public void setReturn(UUID player, Return r) {
        returns.put(player, r);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("next", next);
        ListTag players = new ListTag();
        for (var e : plots.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", e.getKey());
            t.putIntArray("plots", e.getValue());
            players.add(t);
        }
        tag.put("players", players);
        ListTag rets = new ListTag();
        for (var e : returns.entrySet()) {
            Return r = e.getValue();
            CompoundTag t = new CompoundTag();
            t.putUUID("id", e.getKey());
            t.putString("dim", r.dim().location().toString());
            t.putLong("terminal", r.terminal().asLong());
            t.putDouble("x", r.x());
            t.putDouble("y", r.y());
            t.putDouble("z", r.z());
            t.putFloat("yaw", r.yaw());
            rets.add(t);
        }
        tag.put("returns", rets);
        ListTag o = new ListTag();
        for (int plot : open) {
            CompoundTag t = new CompoundTag();
            t.putInt("plot", plot);
            o.add(t);
        }
        tag.put("open", o);
        ListTag gs = new ListTag();
        for (var e : guests.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", e.getKey());
            t.putInt("plot", e.getValue());
            gs.add(t);
        }
        tag.put("guests", gs);
        return tag;
    }

    public static PlotData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlotData d = new PlotData();
        d.next = tag.getInt("next");
        for (Tag raw : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            int[] saved = t.getIntArray("plots");
            int[] a = new int[SLOTS];
            Arrays.fill(a, -1);
            System.arraycopy(saved, 0, a, 0, Math.min(saved.length, SLOTS));
            d.plots.put(t.getUUID("id"), a);
        }
        for (Tag raw : tag.getList("returns", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
            if (dim == null) continue;
            d.returns.put(t.getUUID("id"), new Return(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(t.getLong("terminal")),
                    t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("yaw")));
        }
        for (Tag raw : tag.getList("open", Tag.TAG_COMPOUND)) if (((CompoundTag) raw).contains("plot")) d.open.add(((CompoundTag) raw).getInt("plot"));
        for (Tag raw : tag.getList("guests", Tag.TAG_COMPOUND)) d.guests.put(((CompoundTag) raw).getUUID("id"), ((CompoundTag) raw).getInt("plot"));
        return d;
    }
}
