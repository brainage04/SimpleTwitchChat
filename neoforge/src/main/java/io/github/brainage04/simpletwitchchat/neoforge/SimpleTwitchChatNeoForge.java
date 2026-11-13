package io.github.brainage04.simpletwitchchat.neoforge;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.brainage04.simpletwitchchat.SimpleTwitchChat;
import io.github.brainage04.simpletwitchchat.command.TwitchChatCommand;
import io.github.brainage04.simpletwitchchat.command.admin.RegenerateAuthUrlCommand;
import io.github.brainage04.simpletwitchchat.twitch.InstalledChatbot;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = SimpleTwitchChat.MOD_ID, dist = Dist.CLIENT)
public final class SimpleTwitchChatNeoForge {
    public SimpleTwitchChatNeoForge() {
        SimpleTwitchChat.initialize();
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onClientStopping);
    }

    private void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher()
                .register(
                        Commands.literal("regenerateauthurl")
                                .executes(
                                        context ->
                                                RegenerateAuthUrlCommand.execute(
                                                        Minecraft.getInstance().player)));
        LiteralCommandNode<CommandSourceStack> twitchchat =
                event.getDispatcher()
                        .register(
                                Commands.literal("twitchchat")
                                        .executes(
                                                context ->
                                                        TwitchChatCommand.execute(
                                                                Minecraft.getInstance().player))
                                        .then(
                                                Commands.argument(
                                                                "message",
                                                                StringArgumentType.greedyString())
                                                        .executes(
                                                                context ->
                                                                        TwitchChatCommand.execute(
                                                                                StringArgumentType
                                                                                        .getString(
                                                                                                context,
                                                                                                "message")))));
        // Short alias. A redirect alone does not run when nothing follows the literal, so the bare
        // /twc form gets its own executes; everything after it goes through /twitchchat.
        event.getDispatcher()
                .register(
                        Commands.literal("twc")
                                .executes(
                                        context ->
                                                TwitchChatCommand.execute(
                                                        Minecraft.getInstance().player))
                                .redirect(twitchchat));
    }

    private void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        InstalledChatbot.onJoin();
    }

    private void onClientStopping(ClientStoppingEvent event) {
        SimpleTwitchChat.LOGGER.info("NeoForge client stopping: closing Twitch chat.");
        InstalledChatbot.close();
    }
}
