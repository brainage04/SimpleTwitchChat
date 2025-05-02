package io.github.brainage04.simpletwitchchat.twitch;

import com.github.philippheuer.credentialmanager.domain.DeviceAuthorization;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.auth.domain.TwitchScopes;
import io.github.brainage04.simpletwitchchat.SimpleTwitchChat;
import io.github.brainage04.simpletwitchchat.util.SourceUtils;
import io.github.brainage04.simpletwitchchat.util.feedback.FeedbackUtils;
import io.github.brainage04.simpletwitchchat.util.feedback.MessageType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

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

    public static MutableText getAuthText() {
        return Text.literal("In order to send messages, you must authorise SimpleTwitchChat bot by clicking ")
                .append(Text.literal("here")
                        .setStyle(
                                Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(InstalledChatbot.getActivationUri()))
                                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Opens the URL to authorise SimpleTwitchChat bot in your default browser when clicked."))))
                        .formatted(Formatting.UNDERLINE))
                .append(".");
    }

    public static MutableText getRegenText() {
        return Text.literal("\"")
                .append(Text.literal("INCORRECT CODE!").formatted(Formatting.RED))
                .append("\"? Request a new one by clicking ")
                .append(Text.literal("here")
                        .setStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/regenerateauthurl"))
                                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Runs \"/regenerateauthurl\" when clicked."))))
                        .formatted(Formatting.UNDERLINE))
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

                        ClientPlayerEntity player = MinecraftClient.getInstance().player;
                        if (player != null) {
                            FeedbackUtils.sendMessage(
                                    SourceUtils.getSource(player),
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

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        FeedbackUtils.sendMessage(
                SourceUtils.getSource(player),
                getAuthText(),
                MessageType.INFO
        );
        FeedbackUtils.sendMessage(
                SourceUtils.getSource(player),
                getRegenText(),
                MessageType.INFO
        );
    }
}
