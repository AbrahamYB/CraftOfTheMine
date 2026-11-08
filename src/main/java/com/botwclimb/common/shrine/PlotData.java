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
    /** The plots whose island has been built; a built island is never put right again, so nothing a player dug out is refilled or cleared. */
    private final Set<Integer> built = new HashSet<>();
    private final Map<UUID, int[]> plots = new HashMap<>();
    private final Map<UUID, Return> returns = new HashMap<>();
    /** The plots (by number) friends may join, and the plot each guest last joined. */
    private final Set<Integer> open = new HashSet<>();
    private final Map<UUID, Integer> guests = new HashMap<>();

    /** How many plot numbers have been handed out. */
    public int claimed() {
        return next;
    }

    public boolean isBuilt(int plot) {
        return built.contains(plot);
    }

    public void markBuilt(int plot) {
        if (built.add(plot)) setDirty();
    }

    /** Closes the plots of one player to friends (they log out), so no friend stays on in a plot whose owner is away. */
    public void closeOf(UUID owner) {
        int[] p = plots.get(owner);
        if (p == null) return;
        for (int plot : p) if (plot >= 0 && open.remove(plot)) setDirty();
    }

    public void closeAll() {
        if (!open.isEmpty()) {
            open.clear();
            setDirty();
        }
    }

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

    //? if >=1.21.5 {
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
            if (next >= PlotWorld.MAX_PLOTS) return -1; // the world is full of plots
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
        tag.putIntArray("built", built.stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }

    /** Reads saved plots. One broken entry is skipped (and logged), never the whole file, so a bad byte cannot hand out the plots again. */
    public static PlotData read(CompoundTag tag) {
        PlotData d = new PlotData();
        int max = PlotWorld.MAX_PLOTS;
        d.next = Math.max(0, Math.min(max, com.botwclimb.common.Nbt.getInt(tag, "next")));
        Set<Integer> seen = new HashSet<>();
        for (CompoundTag t : com.botwclimb.common.Nbt.list(tag, "players")) {
            try {
                UUID owner = com.botwclimb.common.Nbt.getUuid(t, "id"); // first: an entry without an owner must not use up plot numbers
                int[] saved = com.botwclimb.common.Nbt.getIntArray(t, "plots");
                int[] a = new int[SLOTS];
                Arrays.fill(a, -1);
                for (int i = 0; i < Math.min(saved.length, SLOTS); i++) {
                    if (saved[i] >= 0 && saved[i] < max && seen.add(saved[i])) { // a plot number belongs to one player only
                        a[i] = saved[i];
                        d.next = Math.max(d.next, saved[i] + 1);
                    }
                }
                d.plots.put(owner, a);
            } catch (RuntimeException e) {
                com.botwclimb.BotwClimb.LOGGER.warn("Skipped an unreadable plot owner entry: {}", e.toString());
            }
        }
        for (CompoundTag t : com.botwclimb.common.Nbt.list(tag, "returns")) {
            try {
                ResourceLocation dim = ResourceLocation.tryParse(com.botwclimb.common.Nbt.getString(t, "dim"));
                double x = com.botwclimb.common.Nbt.getDouble(t, "x"), y = com.botwclimb.common.Nbt.getDouble(t, "y"), z = com.botwclimb.common.Nbt.getDouble(t, "z");
                float yaw = com.botwclimb.common.Nbt.getFloat(t, "yaw");
                ResourceKey<Level> key = dim == null ? null : ResourceKey.create(Registries.DIMENSION, dim);
                if (key == null || key.equals(PlotWorld.DIM) || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)
                        || Math.abs(x) > 3e7 || Math.abs(z) > 3e7 || y < -2048 || y > 4096) continue;
                d.returns.put(com.botwclimb.common.Nbt.getUuid(t, "id"), new Return(key, BlockPos.of(com.botwclimb.common.Nbt.getLong(t, "terminal")), x, y, z, yaw));
            } catch (RuntimeException e) {
                com.botwclimb.BotwClimb.LOGGER.warn("Skipped an unreadable return point: {}", e.toString());
            }
        }
        for (CompoundTag t : com.botwclimb.common.Nbt.list(tag, "open")) {
            int plot = t.contains("plot") ? com.botwclimb.common.Nbt.getInt(t, "plot") : -1;
            if (plot >= 0 && plot < max) d.open.add(plot);
        }
        for (CompoundTag t : com.botwclimb.common.Nbt.list(tag, "guests")) {
            try {
                int plot = com.botwclimb.common.Nbt.getInt(t, "plot");
                if (plot >= 0 && plot < max) d.guests.put(com.botwclimb.common.Nbt.getUuid(t, "id"), plot);
            } catch (RuntimeException e) {
                com.botwclimb.BotwClimb.LOGGER.warn("Skipped an unreadable guest entry: {}", e.toString());
            }
        }
        if (tag.contains("built")) {
            for (int plot : com.botwclimb.common.Nbt.getIntArray(tag, "built")) if (plot >= 0 && plot < max) d.built.add(plot);
        } else {
            for (int plot = 0; plot < d.next; plot++) d.built.add(plot); // a world from before this flag: every plot claimed so far is already built
        }
        return d;
    }
}
