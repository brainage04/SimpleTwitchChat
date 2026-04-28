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
    private static URI activationUri = null;

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

    public static void intitialize() {
        // https://gist.github.com/iProdigy/76bc18a8e601243aa021f31fb2a4d121
        bot = new Bot();

        DeviceAuthorization req = getBot().getController().startOAuth2DeviceAuthorizationGrantType(
                getBot().getIdentityProvider(),
                Arrays.asList(TwitchScopes.CHAT_READ, TwitchScopes.CHAT_EDIT),
                resp -> {
                    OAuth2Credential token = resp.getCredential();
                    if (token != null) {
                        getBot().start(token);

                        LocalPlayer player = Minecraft.getInstance().player;
                        if (player != null) {
                            FeedbackUtils.sendMessage(
                                    player,
                                    "SimpleTwitchChat bot has now been authorised.",
                                    MessageType.SUCCESS
                            );
                        }
                    } else {
                        SimpleTwitchChat.LOGGER.warn("Could not obtain device flow token due to {}", resp.getError());
                    }
                }
        );

        activationUri = URI.create(req.getCompleteUri());

        SimpleTwitchChat.LOGGER.info("The user should now visit: {}", getActivationUri());
    }

    public static void regenerate() {
        intitialize();

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        FeedbackUtils.sendMessage(
                player,
                getAuthText(),
                MessageType.INFO
        );
        FeedbackUtils.sendMessage(
                player,
                getRegenText(),
                MessageType.INFO
        );
    }
}
