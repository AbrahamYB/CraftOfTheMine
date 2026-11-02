package com.botwclimb.common.shrine;

import com.botwclimb.BotwClimb;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The few rules of the plot dimension: the terminal of each plot is the way home, so it cannot be broken or blown up, and
 * {@code /plot return} is the way out if it is somehow gone anyway.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID)
public final class PlotRules {
    private PlotRules() {}

    /** Whether the player may use the plot the position lies in: its owner, or a friend of an owner who has it open to friends. */
    private static boolean owns(Player p, BlockPos pos) {
        return p.getServer() != null && PlotData.get(p.getServer()).mayUse(p.getUUID(), PlotWorld.plotAt(pos));
    }

    /** Nobody but a plot's owner touches its blocks: not breaking, not placing, not opening a chest, not clicking a button. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == PlotWorld.DIM && !PlotWorld.isTerminalSpot(event.getPos()) && !owns(event.getEntity(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHit(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == PlotWorld.DIM && !owns(event.getEntity(), event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == PlotWorld.DIM
                && (PlotWorld.isTerminalSpot(event.getPos()) || !PlotWorld.inSquare(event.getPos().getX() + 0.5, event.getPos().getZ() + 0.5) || !owns(event.getPlayer(), event.getPos()))) {
            event.setCanceled(true);
        }
    }

    /** Outside the square of a plot nothing can be built. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == PlotWorld.DIM
                && (!PlotWorld.inSquare(event.getPos().getX() + 0.5, event.getPos().getZ() + 0.5)
                || event.getEntity() instanceof Player p && !owns(p, event.getPos()))) {
            event.setCanceled(true);
        }
    }

    /** A player at the square's edge is held there: the plot is the whole of their world, and the void round it is not for walking in. */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.level().dimension() != PlotWorld.DIM || TerminalRide.isRiding(p)) return;
        if (PlotWorld.inSquare(p.getX(), p.getZ()) && !owns(p, p.blockPosition())) { // found someone else's plot some other way: sent straight back out
            PlotTravel.Arrival a = PlotTravel.arrival(p, PlotTravel.RETURN, 0);
            com.botwclimb.common.Compat.teleport(p, a.level(), a.x(), a.y(), a.z(), a.yaw(), 0f);
            return;
        }
        if (PlotWorld.inSquare(p.getX(), p.getZ()) && PlotWorld.inSquare(p.getX() - 0.3, p.getZ() - 0.3) && PlotWorld.inSquare(p.getX() + 0.3, p.getZ() + 0.3)) return;
        double[] in = PlotWorld.clampToSquare(p.getX(), p.getZ(), 0.31);
        if (Math.abs(in[0] - p.getX()) + Math.abs(in[1] - p.getZ()) > PlotWorld.HALF * 2) { // far from any plot (the old spot of one that moved, a command): bring them to the nearest plot's terminal, building it if need be
            int plot = PlotWorld.plotAt(p.blockPosition());
            if (!PlotWorld.isBuilt(p.serverLevel(), plot)) PlotWorld.buildIsland(p.serverLevel(), plot);
            BlockPos t = PlotWorld.terminalPos(plot);
            p.teleportTo(t.getX() + 0.5, t.getY(), t.getZ() + 3.5);
            return;
        }
        p.teleportTo(in[0], p.getY(), in[1]);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().dimension() == PlotWorld.DIM) event.getAffectedBlocks().removeIf(PlotWorld::isTerminalSpot);
    }

    /** Takes the player back at once (no ride): to where their last trip into the plots began, or the world spawn. Anyone may use it, but only inside the plot dimension. */
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("plot").then(Commands.literal("return").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            if (p.level().dimension() != PlotWorld.DIM) {
                ctx.getSource().sendFailure(Component.translatable("message.botwclimb.plot_return_only"));
                return 0;
            }
            if (TerminalRide.isRiding(p)) {
                ctx.getSource().sendFailure(Component.translatable("message.botwclimb.plot_return_busy"));
                return 0;
            }
            PlotTravel.Arrival a = PlotTravel.arrival(p, PlotTravel.RETURN, 0);
            com.botwclimb.common.Compat.teleport(p, a.level(), a.x(), a.y(), a.z(), a.yaw(), 0f);
            return 1;
        })));
    }
}
