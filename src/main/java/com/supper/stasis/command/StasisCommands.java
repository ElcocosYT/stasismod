package com.supper.stasis.command;

import com.supper.stasis.StasisManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class StasisCommands {
    private StasisCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, net.minecraft.command.CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("stasis")
                .then(CommandManager.literal("break")
                        .executes(context -> breakStasis(context.getSource()))));
    }

    private static int breakStasis(ServerCommandSource source) {
        StasisManager manager = StasisManager.getInstance();
        if (!manager.getPhase().isRunning()) {
            source.sendError(Text.literal("Stasis is not active."));
            return 0;
        }

        ServerPlayerEntity sourcePlayer = source.getPlayer();
        ServerPlayerEntity activatingPlayer = manager.getActivatingPlayer(source.getServer());
        if (sourcePlayer != null
                && activatingPlayer != null
                && !sourcePlayer.getUuid().equals(activatingPlayer.getUuid())
                && !source.hasPermissionLevel(2)) {
            source.sendError(Text.literal("Only the activating player or an operator can break Stasis."));
            return 0;
        }

        if (!manager.emergencyBreak(source.getServer())) {
            source.sendError(Text.literal("Unable to break Stasis."));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Stasis broken."), true);
        return 1;
    }
}
