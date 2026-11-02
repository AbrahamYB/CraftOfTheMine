package com.botwclimb.common.shrine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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

    //? if >=1.21.11 {
    /*private static final com.mojang.serialization.Codec<PlotData> CODEC = CompoundTag.CODEC.xmap(PlotData::read, d -> d.write(new CompoundTag()));
    private static final net.minecraft.world.level.saveddata.SavedDataType<PlotData> TYPE =
            new net.minecraft.world.level.saveddata.SavedDataType<>(NAME, PlotData::new, CODEC, null);

    public static PlotData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }
    *///?} else {
    public static PlotData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(PlotData::new, PlotData::load, null), NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return write(tag);
    }

    public static PlotData load(CompoundTag tag, HolderLookup.Provider registries) {
        return read(tag);
    }
    //?}

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

    /** A copy made by saving and loading (what the world does between sessions). */
    public static PlotData roundTrip(PlotData d) {
        return read(d.write(new CompoundTag()));
    }

    private CompoundTag write(CompoundTag tag) {
        tag.putInt("next", next);
        ListTag players = new ListTag();
        for (var e : plots.entrySet()) {
            CompoundTag t = new CompoundTag();
            com.botwclimb.common.Nbt.putUuid(t, "id", e.getKey());
            t.putIntArray("plots", e.getValue());
            players.add(t);
        }
        tag.put("players", players);
        ListTag rets = new ListTag();
        for (var e : returns.entrySet()) {
            Return r = e.getValue();
            CompoundTag t = new CompoundTag();
            com.botwclimb.common.Nbt.putUuid(t, "id", e.getKey());
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
            com.botwclimb.common.Nbt.putUuid(t, "id", e.getKey());
            t.putInt("plot", e.getValue());
            gs.add(t);
        }
        tag.put("guests", gs);
        return tag;
    }

    private static PlotData read(CompoundTag tag) {
        PlotData d = new PlotData();
        d.next = com.botwclimb.common.Nbt.getInt(tag, "next");
        for (CompoundTag t : com.botwclimb.common.Nbt.list(tag, "players")) {
            int[] saved = com.botwclimb.common.Nbt.getIntArray(t, "plots");
            int[] a = new int[SLOTS];
            Arrays.fill(a, -1);
            System.arraycopy(saved, 0, a, 0, Math.min(saved.length, SLOTS));
            d.plots.put(com.botwclimb.common.Nbt.getUuid(t, "id"), a);
        }
        for (CompoundTag t : com.botwclimb.common.Nbt.list(tag, "returns")) {
            ResourceLocation dim = ResourceLocation.tryParse(com.botwclimb.common.Nbt.getString(t, "dim"));
            if (dim == null) continue;
            d.returns.put(com.botwclimb.common.Nbt.getUuid(t, "id"), new Return(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(com.botwclimb.common.Nbt.getLong(t, "terminal")),
                    com.botwclimb.common.Nbt.getDouble(t, "x"), com.botwclimb.common.Nbt.getDouble(t, "y"), com.botwclimb.common.Nbt.getDouble(t, "z"), com.botwclimb.common.Nbt.getFloat(t, "yaw")));
        }
        for (CompoundTag t : com.botwclimb.common.Nbt.list(tag, "open")) if (t.contains("plot")) d.open.add(com.botwclimb.common.Nbt.getInt(t, "plot"));
        for (CompoundTag t : com.botwclimb.common.Nbt.list(tag, "guests")) d.guests.put(com.botwclimb.common.Nbt.getUuid(t, "id"), com.botwclimb.common.Nbt.getInt(t, "plot"));
        return d;
    }
}
