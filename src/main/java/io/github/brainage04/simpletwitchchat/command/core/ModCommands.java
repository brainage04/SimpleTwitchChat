package io.github.brainage04.simpletwitchchat.command.core;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.brainage04.simpletwitchchat.command.TwitchChatCommand;
import io.github.brainage04.simpletwitchchat.command.admin.RegenerateAuthUrlCommand;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class ModCommands {
    public static void initialize() {
        // admin commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                        literal("regenerateauthurl")
                                .executes(context ->
                                        RegenerateAuthUrlCommand.execute(
                                                context.getSource()
                                        )
                                )
                )
        );

        // other commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("tc")
                        .executes(context ->
                                TwitchChatCommand.execute(
                                        context.getSource()
                                )
                        )
                        .then(argument("message", StringArgumentType.string())
                                .executes(context ->
                                        TwitchChatCommand.execute(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "message")
                                        )
                                )
                        )
                )
        );
    }
}
