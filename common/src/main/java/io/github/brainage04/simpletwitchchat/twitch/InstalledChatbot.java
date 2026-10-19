package io.github.brainage04.simpletwitchchat.twitch;

import io.github.brainage04.simpletwitchchat.SimpleTwitchChat;
import io.github.brainage04.simpletwitchchat.util.feedback.FeedbackUtils;
import io.github.brainage04.simpletwitchchat.util.feedback.MessageType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URI;

public class InstalledChatbot {
    private static final ChatbotSession SESSION = new ChatbotSession(
            Bot::new,
            InstalledChatbot::sendAuthorizationPrompt,
            () -> sendFeedback("SimpleTwitchChat bot has now been authorised.", MessageType.SUCCESS),
            message -> {
                SimpleTwitchChat.LOGGER.warn("{}", message);
                sendFeedback(message, MessageType.ERROR);
            });


    static MutableComponent getAuthText(URI activationUri) {
        return Component.literal("In order to send messages, you must authorise SimpleTwitchChat bot by clicking ")
                .append(Component.literal("here")
                        .setStyle(
                                Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(activationUri))
                                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Opens the URL to authorise SimpleTwitchChat bot in your default browser when clicked."))))
                        .withStyle(ChatFormatting.UNDERLINE))
                .append(".");
    }

    private static MutableComponent getRegenText() {
        return Component.literal("\"")
                .append(Component.literal("INCORRECT CODE!").withStyle(ChatFormatting.RED))
                .append("\"? Request a new one by clicking ")
                .append(Component.literal("here")
                        .setStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/regenerateauthurl"))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Runs \"/regenerateauthurl\" when clicked."))))
                        .withStyle(ChatFormatting.UNDERLINE))
                .append(" or with \"/regenerateauthurl\".");
    }

    public static void initialize() {
        SESSION.initialize();
    }

    public static void sendAuthorizationPrompt() {
        if (SESSION.getActivationUri() == null) {
            return;
        }
        SimpleTwitchChat.LOGGER.info("Twitch authorisation link is ready; browser approval is pending.");
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            LocalPlayer player = client.player;
            URI activationUri = SESSION.getActivationUri();
            if (player == null || activationUri == null) {
                return;
            }
            FeedbackUtils.sendMessage(player, getAuthText(activationUri), MessageType.INFO);
            FeedbackUtils.sendMessage(player, getRegenText(), MessageType.INFO);
        });
    }

    private static void sendFeedback(String message, MessageType type) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                FeedbackUtils.sendMessage(client.player, message, type);
            }
        });
    }

    public static boolean sendChatMessage(String message) {
        return SESSION.sendChatMessage(message);
    }

    public static void close() {
        SESSION.close();
    }

    public static void regenerate() {
        SESSION.regenerate();
    }
}
