package com.botwclimb.common.shrine;

import com.botwclimb.BotwClimb;
import com.botwclimb.network.TerminalActionPayload;
import com.botwclimb.network.TerminalOpenPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The server's side of the trip to the plots: what the terminal screen shows, what a pick does, and where a ride ends. It
 * believes nothing the client sends beyond which button was pressed, and checks that again.
 */
public final class PlotTravel {
    /** The "Return" button: back to where the last trip into the plots began. */
    public static final int RETURN = -1;
    /** The "friends can join" switch, a ride to a friend's plot (what the ride is told once the friend is picked), and the first of the join buttons (one per open host, in the order the screen was sent). */
    public static final int TOGGLE_OPEN = 50, VISIT = 55, JOIN = 60, MAX_HOSTS = 4;

    /** Where a ride ends: the standing spot, and how far above it the rider may come down from (limited by what is overhead). */
    public record Arrival(ServerLevel level, double x, double y, double z, float yaw, double rise) {}

    private PlotTravel() {}

    // ------------------------------------------------------------------ the screen

    public static TerminalOpenPayload openPayload(ServerPlayer p, BlockPos terminal) {
        PlotData data = PlotData.get(p.level().getServer());
        UUID id = p.getUUID();
        int claimed = 0;
        for (int s = 0; s < PlotData.SLOTS; s++) if (data.plot(id, s) >= 0) claimed |= 1 << s;
        boolean inPlot = p.level().dimension() == PlotWorld.DIM;
        int here = inPlot ? data.slotOf(id, PlotWorld.plotAt(terminal)) : -1;
        int open = 0;
        for (int s = 0; s < PlotData.SLOTS; s++) if (data.plot(id, s) >= 0 && data.isOpen(data.plot(id, s))) open |= 1 << s;
        return new TerminalOpenPayload(terminal, claimed, here, inPlot, open, hosts(p).stream().map(h -> new TerminalOpenPayload.Host(h.player().getUUID(), com.botwclimb.common.Compat.name(h.player()), h.slot())).toList());
    }

    private record Host(ServerPlayer player, int slot, int plot) {}

    /** Every plot of another online player that is open to friends, in a fixed order the join buttons refer to. */
    private static List<Host> hosts(ServerPlayer p) {
        PlotData data = PlotData.get(p.level().getServer());
        List<Host> out = new ArrayList<>();
        for (ServerPlayer o : p.level().getServer().getPlayerList().getPlayers()) {
            if (o == p) continue;
            for (int s = 0; s < PlotData.SLOTS; s++) {
                int plot = data.plot(o.getUUID(), s);
                if (plot >= 0 && data.isOpen(plot) && out.size() < MAX_HOSTS) out.add(new Host(o, s, plot));
            }
        }
        return out;
    }

    /** A button pressed on the terminal screen. */
    public static void handle(ServerPlayer p, TerminalActionPayload a) {
        if (!p.isAlive() || p.isSpectator() || p.isPassenger() || p.isVehicle() || TerminalRide.isRiding(p) || p.distanceToSqr(Vec3.atCenterOf(a.pos())) > 64
                || !p.level().getBlockState(a.pos()).is(BotwClimb.TERMINAL_BLOCK.get())) return;
        boolean inPlot = p.level().dimension() == PlotWorld.DIM;
        PlotData pd = PlotData.get(p.level().getServer());
        if (a.target() >= TOGGLE_OPEN && a.target() < TOGGLE_OPEN + PlotData.SLOTS) { // the switch on one of the player's own plots; the screen comes back showing the new state
            int plot = pd.plot(p.getUUID(), a.target() - TOGGLE_OPEN);
            if (plot < 0) return;
            pd.toggleOpen(plot);
            com.botwclimb.common.Net.toPlayer(p, openPayload(p, a.pos()));
            return;
        }
        int target = a.target();
        if (target >= JOIN) { // a friend's plot: the picked host must still be open
            List<Host> hs = hosts(p);
            if (target - JOIN >= hs.size()) return;
            int plot = hs.get(target - JOIN).plot();
            if (inPlot && PlotWorld.plotAt(a.pos()) == plot) return;
            pd.setGuest(p.getUUID(), plot);
            target = VISIT;
            a = new TerminalActionPayload(a.pos(), VISIT);
        }
        if (a.target() == RETURN ? !inPlot : inPlot && PlotData.get(p.level().getServer()).slotOf(p.getUUID(), PlotWorld.plotAt(a.pos())) == a.target()) return; // return only from a plot; no ride to the plot you are in
        if (!prepare(p, a.pos(), a.target())) return;
        p.level().playSound(null, a.pos(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.4f);
        TerminalRide.start(p, a.pos(), a.target());
    }

    /** Claims the plot if it is new (building its island) and remembers where the trip began. False if the plots cannot be reached. */
    private static boolean prepare(ServerPlayer p, BlockPos terminal, int target) {
        MinecraftServer server = p.level().getServer();
        ServerLevel plotLevel = server.getLevel(PlotWorld.DIM);
        if (plotLevel == null) {
            BotwClimb.LOGGER.warn("The plot dimension is missing; terminal trips are off");
            return false;
        }
        PlotData data = PlotData.get(server);
        if (target >= 0 && target < PlotData.SLOTS) {
            int plot = data.claim(p.getUUID(), target);
            if (plot < 0) { // the world has no plots left
                com.botwclimb.common.Compat.overlay(p, net.minecraft.network.chat.Component.translatable("message.botwclimb.plots_full"));
                return false;
            }
            if (!data.isBuilt(plot)) {
                PlotWorld.buildIsland(plotLevel, plot);
                data.markBuilt(plot);
            }
        }
        if (p.level().dimension() != PlotWorld.DIM) { // a trip from one plot to the other keeps the way home from before
            data.setReturn(p.getUUID(), new PlotData.Return(p.level().dimension(), terminal, p.getX(), p.getY(), p.getZ(), p.getYRot()));
        }
        return true;
    }

    // ------------------------------------------------------------------ where a ride ends

    /** Where the ride toward {@code target} comes down; null if there is no such place (the ride then just comes back down where it began). */
    public static Arrival arrival(ServerPlayer p, int target, double wantedRise) {
        MinecraftServer server = p.level().getServer();
        PlotData data = PlotData.get(server);
        UUID id = p.getUUID();
        if (target >= 0) {
            ServerLevel level = server.getLevel(PlotWorld.DIM);
            int plot = target == VISIT ? (data.mayUse(id, data.guestPlot(id)) ? data.guestPlot(id) : -1) : data.plot(id, target);
            if (level == null || plot < 0) return null;
            PlotWorld.ensureTerminal(level, plot);
            BlockPos t = PlotWorld.terminalPos(plot);
            double cx = t.getX() + 0.5, cz = t.getZ() + 0.5, y = t.getY();
            Arrival a = pick(p, level, wantedRise,
                    new double[] {cx, y, cz + 3, 180}, new double[] {cx + 3, y, cz, 90},
                    new double[] {cx - 3, y, cz, -90}, new double[] {cx, y, cz - 3, 0});
            return a != null ? a : new Arrival(level, cx, y + 10 / 16.0, cz, 180, 0); // everything round the terminal is built over: stand on top of it
        }
        PlotData.Return ret = data.returnPoint(id);
        if (ret != null) {
            ServerLevel level = server.getLevel(ret.dim());
            if (level != null && level.getBlockState(ret.terminal()).is(BotwClimb.TERMINAL_BLOCK.get())) {
                Arrival a = pick(p, level, wantedRise, new double[] {ret.x(), ret.y(), ret.z(), ret.yaw()});
                if (a != null) return a;
            }
        }
        return worldSpawn(p, wantedRise);
    }

    /** The first spot where the rider can stand and has room, with how much of the wanted rise fits above it. */
    private static Arrival pick(ServerPlayer p, ServerLevel level, double wantedRise, double[]... spots) {
        for (double[] s : spots) {
            AABB box = standingBox(p, level, s[0], s[1], s[2]);
            if (!level.noCollision(box) || level.noCollision(box.move(0, -0.05, 0))) continue; // blocked, or nothing to stand on
            return new Arrival(level, s[0], s[1], s[2], (float) s[3], riseThatFits(level, box, wantedRise));
        }
        return null;
    }

    private static Arrival worldSpawn(ServerPlayer p, double wantedRise) {
        ServerLevel level = p.level().getServer().overworld();
        BlockPos spawn = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, com.botwclimb.common.Compat.worldSpawn(level));
        double x = spawn.getX() + 0.5, z = spawn.getZ() + 0.5;
        return new Arrival(level, x, spawn.getY(), z, 0, riseThatFits(level, standingBox(p, level, x, spawn.getY(), z), wantedRise));
    }

    /** The player's box at a spot, with the chunks it touches loaded so that collisions there are real. */
    private static AABB standingBox(ServerPlayer p, ServerLevel level, double x, double y, double z) {
        for (int dx = -1; dx <= 1; dx += 2) for (int dz = -1; dz <= 1; dz += 2) level.getChunk(Mth.floor(x + dx * 0.4) >> 4, Mth.floor(z + dz * 0.4) >> 4);
        return p.getDimensions(Pose.STANDING).makeBoundingBox(x, y, z);
    }

    /** How far straight up the box can go before it meets a block, in whole blocks and at most {@code wanted}. */
    private static double riseThatFits(ServerLevel level, AABB box, double wanted) {
        int k = 0;
        while (k < Math.ceil(wanted) && level.noCollision(box.move(0, k + 1, 0))) k++;
        return Math.min(wanted, k);
    }
}
