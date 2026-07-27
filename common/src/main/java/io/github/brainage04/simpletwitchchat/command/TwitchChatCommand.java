package io.github.brainage04.simpletwitchchat.command;

import io.github.brainage04.simpletwitchchat.SimpleTwitchChatState;
import io.github.brainage04.simpletwitchchat.twitch.InstalledChatbot;
import io.github.brainage04.simpletwitchchat.util.feedback.FeedbackUtils;
import io.github.brainage04.simpletwitchchat.util.feedback.MessageType;
import net.minecraft.client.player.LocalPlayer;

public final class TwitchChatCommand {
	private TwitchChatCommand() {
	}

	public static boolean toggleTwitchChat() {
		SimpleTwitchChatState.twitchChatToggled = !SimpleTwitchChatState.twitchChatToggled;
		return SimpleTwitchChatState.twitchChatToggled;
	}

	public static int execute(LocalPlayer player) {
		String chat = toggleTwitchChat() ? "Twitch" : "Minecraft";
		FeedbackUtils.sendMessage(player, "Chat messages will now be sent through %s chat by default.".formatted(chat), MessageType.INFO);
		return 1;
	}

	public static int execute(LocalPlayer player, String message) {
		InstalledChatbot.getBot().sendChatMessage(player, message);
		return 1;
	}
}
