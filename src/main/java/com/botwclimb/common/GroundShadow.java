package com.botwclimb.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Which sides of the terminal and the altar get the soft shadow on the ground around them. A side only has one when there is a floor
 * under it: on a pillar, beside a cliff or over a hole the shadow would hang in the air, so that side has none. The block models draw a
 * strip per side (and a corner when both of its sides are on), chosen by these four properties.
 * <p>
 * shortcut: the floor under a side is only looked at when the block is placed or one of its direct neighbours changes, so a block placed
 * or broken diagonally below it is noticed on the next update, not at once.
 */
public final class GroundShadow {
    public static final BooleanProperty NORTH = BooleanProperty.create("shadow_north"), EAST = BooleanProperty.create("shadow_east"),
            SOUTH = BooleanProperty.create("shadow_south"), WEST = BooleanProperty.create("shadow_west");

    private GroundShadow() {}

    public static void add(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    public static BlockState all(BlockState state, boolean on) {
        return state.setValue(NORTH, on).setValue(EAST, on).setValue(SOUTH, on).setValue(WEST, on);
    }

    /** The state with each side set to whether a floor lies under it. */
    public static BlockState update(BlockState state, LevelReader level, BlockPos pos) {
        boolean grounded = floor(level, pos);
        return state.setValue(NORTH, grounded && floor(level, pos.relative(Direction.NORTH)))
                .setValue(EAST, grounded && floor(level, pos.relative(Direction.EAST)))
                .setValue(SOUTH, grounded && floor(level, pos.relative(Direction.SOUTH)))
                .setValue(WEST, grounded && floor(level, pos.relative(Direction.WEST)));
    }

    /** Called when the block is placed (also by setBlock from code): works the sides out once. */
    public static void settle(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        BlockState next = update(state, level, pos);
        if (next != state) level.setBlock(pos, next, 2);
    }

    private static boolean floor(LevelReader level, BlockPos at) {
        BlockPos below = at.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }
}
