package io.github.brainage04.simpletwitchchat.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.brainage04.simpletwitchchat.command.TwitchChatCommand;
import io.github.brainage04.simpletwitchchat.command.admin.RegenerateAuthUrlCommand;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class FabricModCommands {
	private FabricModCommands() {
	}

	public static void initialize() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(literal("regenerateauthurl").executes(context -> RegenerateAuthUrlCommand.execute(context.getSource().getPlayer())));
			dispatcher.register(literal("tc").executes(context -> TwitchChatCommand.execute(context.getSource().getPlayer())).then(argument("message", StringArgumentType.string()).executes(context -> TwitchChatCommand.execute(context.getSource().getPlayer(), StringArgumentType.getString(context, "message")))));
		});
	}
}
