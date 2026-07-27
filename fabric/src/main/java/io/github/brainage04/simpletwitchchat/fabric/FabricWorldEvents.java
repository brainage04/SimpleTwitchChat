package io.github.brainage04.simpletwitchchat.fabric;

import io.github.brainage04.simpletwitchchat.twitch.InstalledChatbot;
import io.github.brainage04.simpletwitchchat.util.feedback.FeedbackUtils;
import io.github.brainage04.simpletwitchchat.util.feedback.MessageType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class FabricWorldEvents {
	private FabricWorldEvents() {
	}

	public static void initialize() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (client.player != null && InstalledChatbot.getActivationUri() != null) {
				FeedbackUtils.sendMessage(client.player, InstalledChatbot.getAuthText(), MessageType.INFO);
				FeedbackUtils.sendMessage(client.player, InstalledChatbot.getRegenText(), MessageType.INFO);
			}
		});
	}
}
