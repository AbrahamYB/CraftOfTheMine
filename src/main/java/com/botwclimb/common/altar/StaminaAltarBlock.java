package com.botwclimb.common.altar;

import com.botwclimb.BotwClimb;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;

/**
 * The stamina altar: a low obsidian-like slab. Using it opens the offering screen on the client; everything that matters
 * (what is taken, which upgrade is bought) happens on the server when the screen sends its offer. Its glowing rings and
 * runes are drawn by a block entity renderer from the viewing player's own upgrades, so every player sees their own progress.
 */
public class StaminaAltarBlock extends BaseEntityBlock {
    //? if <26.3 {
    public static final MapCodec<StaminaAltarBlock> CODEC = simpleCodec(StaminaAltarBlock::new);
    //?}
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.core.Direction> FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

    public StaminaAltarBlock(Properties properties) {
        super(properties);
        registerDefaultState(com.botwclimb.common.GroundShadow.all(stateDefinition.any(), true));
    }

    //? if <26.3 {
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
    //?}

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
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

    /** Placed facing the player, like a chest. */
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        BlockState s = super.getStateForPlacement(ctx);
        return s == null ? null : s.setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StaminaAltarBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) com.botwclimb.client.altar.AltarClient.open(pos);
        return com.botwclimb.common.Compat.success(level);
    }
}
