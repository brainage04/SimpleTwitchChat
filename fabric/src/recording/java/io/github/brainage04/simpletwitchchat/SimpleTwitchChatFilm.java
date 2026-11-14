package io.github.brainage04.simpletwitchchat;

import io.github.brainage04.minecraftmodconventions.ClientGameTestRecorder;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;

public final class SimpleTwitchChatFilm {
    private static final int SHOT_TICKS = 50;

    private SimpleTwitchChatFilm() {}

    public static void showDefaultChat(ClientGameTestContext context, boolean twitchDefault) {
        context.setScreen(() -> new ChatScreen("", false));
        if (twitchDefault) {
            ClientGameTestRecorder.showStepAndHold(
                    context,
                    "chat.twitch-default",
                    "Twitch is the default chat",
                    "The /twitchchat command confirms that new chat messages use Twitch by default.",
                    SHOT_TICKS);
        } else {
            ClientGameTestRecorder.showStepAndHold(
                    context,
                    "chat.minecraft-default",
                    "Minecraft is the default chat",
                    "The /twc alias confirms that new chat messages return to Minecraft by default.",
                    SHOT_TICKS);
        }
    }
}
