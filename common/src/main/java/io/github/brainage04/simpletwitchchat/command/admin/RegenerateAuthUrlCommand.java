package io.github.brainage04.simpletwitchchat.command.admin;

import io.github.brainage04.simpletwitchchat.twitch.InstalledChatbot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class RegenerateAuthUrlCommand {
	private RegenerateAuthUrlCommand() {
	}

	public static int execute(LocalPlayer player) {
		player.sendSystemMessage(Component.literal("Restarting installed chatbot..."));
		Thread.ofPlatform()
				.name("simpletwitchchat-device-authorization")
				.daemon(true)
				.start(InstalledChatbot::regenerate);
		return 1;
	}
}
