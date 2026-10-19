package io.github.brainage04.simpletwitchchat.neoforge;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.brainage04.simpletwitchchat.SimpleTwitchChat;
import io.github.brainage04.simpletwitchchat.command.TwitchChatCommand;
import io.github.brainage04.simpletwitchchat.command.admin.RegenerateAuthUrlCommand;
import io.github.brainage04.simpletwitchchat.twitch.InstalledChatbot;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = SimpleTwitchChat.MOD_ID, dist = Dist.CLIENT)
public final class SimpleTwitchChatNeoForge {
	public SimpleTwitchChatNeoForge(IEventBus modEventBus) {
		SimpleTwitchChat.initialize();
		NeoForge.EVENT_BUS.addListener(this::registerCommands);
		NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
		NeoForge.EVENT_BUS.addListener(this::onClientStopping);
	}

	private void registerCommands(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("regenerateauthurl").executes(context -> RegenerateAuthUrlCommand.execute(Minecraft.getInstance().player)));
		event.getDispatcher().register(Commands.literal("tc").executes(context -> TwitchChatCommand.execute(Minecraft.getInstance().player)).then(Commands.argument("message", StringArgumentType.greedyString()).executes(context -> TwitchChatCommand.execute(StringArgumentType.getString(context, "message")))));
	}

	private void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
		InstalledChatbot.sendAuthorizationPrompt();
	}

	private void onClientStopping(ClientStoppingEvent event) {
		InstalledChatbot.close();
	}
}
