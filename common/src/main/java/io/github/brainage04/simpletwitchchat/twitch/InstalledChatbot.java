package io.github.brainage04.simpletwitchchat.twitch;

import com.github.philippheuer.credentialmanager.domain.DeviceAuthorization;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.auth.domain.TwitchScopes;
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
import java.util.Arrays;

public class InstalledChatbot {
    private static Bot bot;
    private static volatile URI activationUri;

    public static Bot getBot() {
        return bot;
    }

    public static URI getActivationUri() {
        return activationUri;
    }

    public static MutableComponent getAuthText() {
        return Component.literal("In order to send messages, you must authorise SimpleTwitchChat bot by clicking ")
                .append(Component.literal("here")
                        .setStyle(
                                Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(InstalledChatbot.getActivationUri()))
                                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Opens the URL to authorise SimpleTwitchChat bot in your default browser when clicked."))))
                        .withStyle(ChatFormatting.UNDERLINE))
                .append(".");
    }

    public static MutableComponent getRegenText() {
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
        bot = new Bot();
        Thread.ofPlatform()
                .name("simpletwitchchat-device-authorization")
                .daemon(true)
                .start(InstalledChatbot::requestAuthorization);
    }

    private static void requestAuthorization() {
        DeviceAuthorization req = getBot().getController().startOAuth2DeviceAuthorizationGrantType(
                getBot().getIdentityProvider(),
                Arrays.asList(TwitchScopes.CHAT_READ, TwitchScopes.CHAT_EDIT),
                response -> {
                    OAuth2Credential token = response.getCredential();
                    if (token == null) {
                        SimpleTwitchChat.LOGGER.warn(
                                "Could not obtain device flow token due to {}",
                                response.getError()
                        );
                        return;
                    }

                    getBot().start(token);
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> {
                        LocalPlayer player = client.player;
                        if (player != null) {
                            FeedbackUtils.sendMessage(
                                    player,
                                    "SimpleTwitchChat bot has now been authorised.",
                                    MessageType.SUCCESS
                            );
                        }
                    });
                }
        );

        activationUri = URI.create(req.getCompleteUri());
        SimpleTwitchChat.LOGGER.info("The user should now visit: {}", getActivationUri());
        sendAuthorizationPrompt();
    }

    private static void sendAuthorizationPrompt() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            LocalPlayer player = client.player;
            if (player == null) {
                return;
            }
            FeedbackUtils.sendMessage(player, getAuthText(), MessageType.INFO);
            FeedbackUtils.sendMessage(player, getRegenText(), MessageType.INFO);
        });
    }

    public static synchronized void close() {
        Bot currentBot = bot;
        bot = null;
        activationUri = null;
        if (currentBot != null) {
            currentBot.close();
        }
    }

    public static void regenerate() {
        requestAuthorization();
    }
}
