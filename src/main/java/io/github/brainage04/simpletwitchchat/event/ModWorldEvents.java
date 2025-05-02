package io.github.brainage04.simpletwitchchat.event;

import io.github.brainage04.simpletwitchchat.twitch.InstalledChatbot;
import io.github.brainage04.simpletwitchchat.util.SourceUtils;
import io.github.brainage04.simpletwitchchat.util.feedback.FeedbackUtils;
import io.github.brainage04.simpletwitchchat.util.feedback.MessageType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

public class ModWorldEvents {
    public static void initialize() {
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> updateModState(client));
        ClientPlayConnectionEvents.JOIN.register((clientPlayNetworkHandler, packetSender, client) -> updateModState(client));
    }

    private static void updateModState(MinecraftClient client) {
        if (client.player == null) return;
        if (InstalledChatbot.getActivationUri() == null) return;

        FeedbackUtils.sendMessage(
                SourceUtils.getSource(client.player),
                InstalledChatbot.getAuthText(),
                MessageType.INFO
        );
        FeedbackUtils.sendMessage(
                SourceUtils.getSource(client.player),
                InstalledChatbot.getRegenText(),
                MessageType.INFO
        );
    }
}
