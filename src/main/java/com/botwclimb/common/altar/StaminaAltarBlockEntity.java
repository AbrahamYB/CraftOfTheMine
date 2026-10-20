package com.botwclimb.common.altar;

import com.botwclimb.BotwClimb;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds nothing: it exists so the glow can be drawn by a block entity renderer. */
public class StaminaAltarBlockEntity extends BlockEntity {
    public long lastParticleTick = Long.MIN_VALUE; // client only: the game tick the glow particles were last spawned on

    public StaminaAltarBlockEntity(BlockPos pos, BlockState state) {
        super(BotwClimb.ALTAR_BE.get(), pos, state);
    }
}
