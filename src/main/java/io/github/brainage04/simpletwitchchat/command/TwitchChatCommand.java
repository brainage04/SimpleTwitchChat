package io.github.brainage04.simpletwitchchat.command;

import io.github.brainage04.simpletwitchchat.SimpleTwitchChatState;
import io.github.brainage04.simpletwitchchat.twitch.InstalledChatbot;
import io.github.brainage04.simpletwitchchat.util.feedback.FeedbackUtils;
import io.github.brainage04.simpletwitchchat.util.feedback.MessageType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public class TwitchChatCommand {
    public static boolean toggleTwitchChat() {
        SimpleTwitchChatState.twitchChatToggled = !SimpleTwitchChatState.twitchChatToggled;
        return SimpleTwitchChatState.twitchChatToggled;
    }

    @SuppressWarnings("SameReturnValue")
    public static int execute(FabricClientCommandSource source) {
        String message = toggleTwitchChat()
                ? "Twitch"
                : "Minecraft";

        FeedbackUtils.sendMessage(
                source,
                "Chat messages will now be sent through %s chat by default.".formatted(message),
                MessageType.INFO
        );

        return 1;
    }

    @SuppressWarnings("SameReturnValue")
    public static int execute(FabricClientCommandSource source, String message) {
        InstalledChatbot.getBot().sendChatMessage(source, message);

        return 1;
    }
}
