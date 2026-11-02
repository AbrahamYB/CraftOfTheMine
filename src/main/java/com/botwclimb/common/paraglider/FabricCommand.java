package com.botwclimb.common.paraglider;

import com.botwclimb.BotwClimb;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.stream.Stream;

/** {@code /cotm cloth unlock <fabric|all>} and {@code /cotm cloth reset}: for testing and for admins. Ops only. */
@EventBusSubscriber(modid = BotwClimb.MOD_ID)
public final class FabricCommand {
    private FabricCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cotm").requires(com.botwclimb.common.Compat.gamemaster())
                .then(Commands.literal("cloth")
                        .then(Commands.literal("unlock").then(Commands.argument("fabric", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Stream.concat(Stream.of("all"), FabricRegistry.all().stream().map(FabricDefinition::id)), b))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(ctx, "fabric");
                                    if (id.equals("all")) {
                                        FabricUnlocks.unlockAll(p);
                                        ctx.getSource().sendSuccess(() -> Component.literal("Learned every paraglider fabric"), false);
                                        return 1;
                                    }
                                    int index = FabricRegistry.indexOf(id);
                                    if (index < 0) {
                                        ctx.getSource().sendFailure(Component.literal("Unknown fabric: " + id));
                                        return 0;
                                    }
                                    FabricUnlocks.unlock(p, index);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Learned the " + id + " fabric"), false);
                                    return 1;
                                })))
                        .then(Commands.literal("reset").executes(ctx -> {
                            FabricUnlocks.reset(ctx.getSource().getPlayerOrException());
                            ctx.getSource().sendSuccess(() -> Component.literal("Paraglider fabrics reset: only the ordinary one is known"), false);
                            return 1;
                        }))));
    }
}
