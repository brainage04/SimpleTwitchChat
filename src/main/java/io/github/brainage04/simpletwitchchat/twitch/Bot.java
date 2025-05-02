package io.github.brainage04.simpletwitchchat.twitch;

import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.CredentialManagerBuilder;
import com.github.philippheuer.credentialmanager.authcontroller.DeviceFlowController;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.ITwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.common.util.ThreadUtils;
import io.github.brainage04.simpletwitchchat.util.feedback.FeedbackUtils;
import io.github.brainage04.simpletwitchchat.util.feedback.MessageType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.ScheduledThreadPoolExecutor;

public class Bot {
    private static final String CLIENT_ID = "0bw1jo9kaot6nn6utin49if1n6pjj4";

    private final TwitchIdentityProvider identityProvider;
    private final ScheduledThreadPoolExecutor executor;
    private final DeviceFlowController controller;
    private final CredentialManager credentialManager;
    private ITwitchClient client;
    private String username;

    public TwitchIdentityProvider getIdentityProvider() {
        return identityProvider;
    }

    public DeviceFlowController getController() {
        return controller;
    }

    public Bot() {
        identityProvider = new TwitchIdentityProvider(CLIENT_ID, null, null);
        executor = ThreadUtils.getDefaultScheduledThreadPoolExecutor("t4j-bot", Runtime.getRuntime().availableProcessors());
        controller = new DeviceFlowController(executor, 0);
        credentialManager = CredentialManagerBuilder.builder().withAuthenticationController(controller).build();
        credentialManager.registerIdentityProvider(identityProvider);
    }

    public void start(OAuth2Credential credential) {
        client = TwitchClientBuilder.builder()
                .withClientId(CLIENT_ID)
                .withScheduledThreadPoolExecutor(executor)
                .withCredentialManager(credentialManager)
                .withEnableChat(true)
                .withChatAccount(credential)
                .withEnableHelix(true)
                .withDefaultAuthToken(credential)
                .build();
        username = credential.getUserName();

        client.getEventManager().onEvent(ChannelMessageEvent.class, event -> appendTwitchMessage(event.getUser().getName(), event.getMessage()));
    }

    private static void appendTwitchMessage(String username, String message) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(
                Text.empty()
                        .append(Text.literal("[%s] ".formatted(username)).formatted(Formatting.DARK_PURPLE))
                        .append(message)
        );
    }

    public void sendChatMessage(FabricClientCommandSource source, String message) {
        if (username == null || username.isEmpty()) {
            FeedbackUtils.sendMessage(
                    source,
                    "You have not authorised the SimpleTwitchChat bot! Please do so before trying to send messages to Twitch chat.",
                    MessageType.ERROR
            );

            return;
        }

        if (!client.getChat().isChannelJoined(username)) {
            client.getChat().joinChannel(username);
        }

        client.getChat().sendMessage(username, message);
        appendTwitchMessage(username, message);
    }
}
