package com.botwclimb.common.climb;

import com.botwclimb.BotwClimb;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /cotm stamina <0-8>}: sets the number of stamina capacity upgrades (4 per extra wheel); {@code /cotm stamina reset} clears them and the altar credit. Ops only. */
@EventBusSubscriber(modid = BotwClimb.MOD_ID)
public final class StaminaCommand {
    private StaminaCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cotm").requires(com.botwclimb.common.Compat.gamemaster())
                .then(Commands.literal("stamina").requires(com.botwclimb.common.Compat.gamemaster())
                        .then(Commands.literal("reset").executes(ctx -> { // back to the start: no upgrades and no altar credit
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.setData(BotwClimb.STAMINA_UPGRADES, 0);
                            p.setData(BotwClimb.ALTAR_CREDIT, 0f);
                            ctx.getSource().sendSuccess(() -> Component.literal("Stamina upgrades and altar credit reset"), false);
                            return 1;
                        }))
                        .then(Commands.argument("upgrades", IntegerArgumentType.integer(0, ClimbConstants.STAMINA_MAX_UPGRADES))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            int u = IntegerArgumentType.getInteger(ctx, "upgrades");
                            p.setData(BotwClimb.STAMINA_UPGRADES, u);
                            ctx.getSource().sendSuccess(() -> Component.literal("Stamina: " + (int) (ClimbConstants.STAMINA_WHEEL + ClimbConstants.STAMINA_PER_UPGRADE * u) + " units (" + u + " upgrades)"), false);
                            return u;
                        }))));
    }
}
