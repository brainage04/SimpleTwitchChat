package io.github.brainage04.simpletwitchchat.fabric;

import io.github.brainage04.simpletwitchchat.twitch.InstalledChatbot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class FabricWorldEvents {
	private FabricWorldEvents() {
	}

	public static void initialize() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				InstalledChatbot.sendAuthorizationPrompt());
	}
}
