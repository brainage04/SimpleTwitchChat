package io.github.brainage04.simpletwitchchat.fabric;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.brainage04.simpletwitchchat.command.TwitchChatCommand;
import io.github.brainage04.simpletwitchchat.command.admin.RegenerateAuthUrlCommand;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class FabricModCommands {
    private FabricModCommands() {}

    public static void initialize() {
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> {
                    dispatcher.register(
                            literal("regenerateauthurl")
                                    .executes(
                                            context ->
                                                    RegenerateAuthUrlCommand.execute(
                                                            context.getSource().getPlayer())));
                    LiteralCommandNode<FabricClientCommandSource> twitchchat =
                            dispatcher.register(
                                    literal("twitchchat")
                                            .executes(
                                                    context ->
                                                            TwitchChatCommand.execute(
                                                                    context.getSource()
                                                                            .getPlayer()))
                                            .then(
                                                    argument(
                                                                    "message",
                                                                    StringArgumentType
                                                                            .greedyString())
                                                            .executes(
                                                                    context ->
                                                                            TwitchChatCommand
                                                                                    .execute(
                                                                                            StringArgumentType
                                                                                                    .getString(
                                                                                                            context,
                                                                                                            "message")))));
                    // Short alias. A redirect alone does not run when nothing follows the literal,
                    // so the bare /twc form gets its own executes; everything after it goes through
                    // /twitchchat.
                    dispatcher.register(
                            literal("twc")
                                    .executes(
                                            context ->
                                                    TwitchChatCommand.execute(
                                                            context.getSource().getPlayer()))
                                    .redirect(twitchchat));
                });
    }
}
