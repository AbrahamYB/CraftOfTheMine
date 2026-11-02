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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The shrine terminal: a Zonai-coloured pedestal with crossed glyph discs. Using it opens the terminal screen on the client
 * (choose one of your two plots, or return); the pick starts a {@link TerminalRide}, and the block shows as active while the
 * ride lasts.
 */
public class ShrineTerminalBlock extends Block {
    public static final MapCodec<ShrineTerminalBlock> CODEC = simpleCodec(ShrineTerminalBlock::new);
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 3, 16), Block.box(4, 3, 4, 12, 7, 12), Block.box(2, 7, 2, 14, 10, 14));

    public ShrineTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && !TerminalRide.isRiding(sp)) { // lit does not matter: a ride cut short can leave the terminal lit
            PacketDistributor.sendToPlayer(sp, PlotTravel.openPayload(sp, pos));
        }
        return com.botwclimb.common.Compat.success(level);
    }
}
