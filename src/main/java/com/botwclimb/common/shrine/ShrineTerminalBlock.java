package com.botwclimb.common.shrine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The shrine terminal: a Zonai-coloured pedestal with crossed glyph discs. Using it opens the terminal screen on the client
 * (go to your plot, join a friend's, or return); the pick starts a {@link TerminalRide}.
 */
public class ShrineTerminalBlock extends Block {
    //? if <26.3 {
    public static final MapCodec<ShrineTerminalBlock> CODEC = simpleCodec(ShrineTerminalBlock::new);
    //?}
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.core.Direction> FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 3, 16), Block.box(4, 3, 4, 12, 7, 12), Block.box(2, 7, 2, 14, 10, 14));

    public ShrineTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(com.botwclimb.common.GroundShadow.all(stateDefinition.any(), true));
    }

    //? if <26.3 {
    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
    //?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        com.botwclimb.common.GroundShadow.add(builder);
    }

    //? if >=1.21.2 {
    /*@Override
    protected BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos,
                                     net.minecraft.core.Direction direction, BlockPos neighborPos, BlockState neighborState, net.minecraft.util.RandomSource random) {
        return com.botwclimb.common.GroundShadow.update(state, level, pos);
    }
    *///?} else {
    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.core.Direction direction, BlockState neighborState, net.minecraft.world.level.LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return com.botwclimb.common.GroundShadow.update(state, level, pos);
    }
    //?}

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        com.botwclimb.common.GroundShadow.settle(state, level, pos);
    }

    /** Placed facing the player, like a chest (the model's front is its south side, so the facing is the way the player looks). */
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        BlockState s = super.getStateForPlacement(ctx);
        return s == null ? null : s.setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** A plot's own terminal is the way home: it cannot be mined (PlotRules also cancels the break and keeps it out of explosions). */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level instanceof Level l && l.dimension() == PlotWorld.DIM && PlotWorld.isTerminalSpot(pos)) return 0f;
        return super.getDestroyProgress(state, player, level, pos);
    }

    /** Nor can a wither or a dragon break a plot's own terminal. */
    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.entity.Entity entity) {
        if (level instanceof Level l && l.dimension() == PlotWorld.DIM && PlotWorld.isTerminalSpot(pos)) return false;
        return super.canEntityDestroy(state, level, pos, entity);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp && !TerminalRide.isRiding(sp)) { // lit does not matter: a ride cut short can leave the terminal lit
            com.botwclimb.common.Net.toPlayer(sp, PlotTravel.openPayload(sp, pos));
        }
        return com.botwclimb.common.Compat.success(level);
    }
}
