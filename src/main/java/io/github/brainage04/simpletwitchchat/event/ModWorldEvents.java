package io.github.brainage04.simpletwitchchat.event;

import io.github.brainage04.simpletwitchchat.twitch.InstalledChatbot;
import io.github.brainage04.simpletwitchchat.util.feedback.FeedbackUtils;
import io.github.brainage04.simpletwitchchat.util.feedback.MessageType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

public class ModWorldEvents {
    public static void initialize() {
        ClientPlayConnectionEvents.JOIN.register((clientPlayNetworkHandler, packetSender, client) -> updateModState(client));
    }

    private static void updateModState(Minecraft client) {
        if (client.player == null) return;
        if (InstalledChatbot.getActivationUri() == null) return;

        FeedbackUtils.sendMessage(
                client.player,
                InstalledChatbot.getAuthText(),
                MessageType.INFO
        );
        FeedbackUtils.sendMessage(
                client.player,
                InstalledChatbot.getRegenText(),
                MessageType.INFO
        );
    }
}
