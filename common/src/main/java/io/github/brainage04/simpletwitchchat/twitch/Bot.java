package io.github.brainage04.simpletwitchchat.twitch;

import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.CredentialManagerBuilder;
import com.github.philippheuer.credentialmanager.authcontroller.DeviceFlowController;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.ITwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.auth.domain.TwitchScopes;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.common.util.ThreadUtils;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class Bot {
    private static final String CLIENT_ID = "0bw1jo9kaot6nn6utin49if1n6pjj4";
    private static final String NOT_AUTHORISED =
            "You have not authorised the SimpleTwitchChat bot! Please do so before trying to send messages to Twitch chat.";

    private final TwitchIdentityProvider identityProvider;
    private final ScheduledThreadPoolExecutor executor;
    private final DeviceFlowController controller;
    private final CredentialManager credentialManager;
    private final Function<OAuth2Credential, ITwitchClient> clientFactory;
    private final BiConsumer<String, String> messages;
    private final Consumer<String> errors;
    private ITwitchClient client;
    private String username;
    private boolean closed;

    public Bot() {
        this(
                ThreadUtils.getDefaultScheduledThreadPoolExecutor("t4j-bot", 2),
                null,
                Bot::appendTwitchMessage,
                Bot::appendError);
    }

    Bot(
            ScheduledThreadPoolExecutor executor,
            Function<OAuth2Credential, ITwitchClient> clientFactory,
            BiConsumer<String, String> messages,
            Consumer<String> errors) {
        this.executor = executor;
        this.messages = messages;
        this.errors = errors;
        identityProvider = new TwitchIdentityProvider(CLIENT_ID, null, null);
        controller = new DeviceFlowController(executor, 0);
        credentialManager =
                CredentialManagerBuilder.builder().withAuthenticationController(controller).build();
        credentialManager.registerIdentityProvider(identityProvider);
        this.clientFactory = clientFactory != null ? clientFactory : this::createClient;
    }

    public URI authorize(Consumer<OAuth2Credential> authorized, Consumer<String> failed) {
        var request =
                controller.startOAuth2DeviceAuthorizationGrantType(
                        identityProvider,
                        List.of(TwitchScopes.CHAT_READ, TwitchScopes.CHAT_EDIT),
                        response -> {
                            if (response.getCredential() == null) {
                                failed.accept(
                                        "Twitch authorisation failed. Use /regenerateauthurl to try again.");
                            } else {
                                authorized.accept(response.getCredential());
                            }
                        });
        return URI.create(request.getCompleteUri());
    }

    private ITwitchClient createClient(OAuth2Credential credential) {
        return TwitchClientBuilder.builder()
                .withClientId(CLIENT_ID)
                .withScheduledThreadPoolExecutor(executor)
                .withCredentialManager(credentialManager)
                .withEnableChat(true)
                .withChatAccount(credential)
                .withEnableHelix(true)
                .withDefaultAuthToken(credential)
                .build();
    }

    public synchronized void start(OAuth2Credential credential) {
        if (closed) {
            return;
        }
        String account = credential.getUserName();
        if (account == null || account.isBlank()) {
            throw new IllegalArgumentException("Twitch did not provide an account name.");
        }
        client = clientFactory.apply(credential);
        username = account;
        client.getEventManager()
                .onEvent(
                        ChannelMessageEvent.class,
                        event -> receiveMessage(event.getUser().getName(), event.getMessage()));
        client.getChat().joinChannel(username);
    }

    synchronized void receiveMessage(String sender, String message) {
        if (!closed) {
            messages.accept(sender, message);
        }
    }

    public synchronized boolean sendChatMessage(String message) {
        if (closed || client == null || username == null) {
            errors.accept(NOT_AUTHORISED);
            return false;
        }
        if (!client.getChat().isChannelJoined(username)) {
            errors.accept("Twitch chat is not connected yet. Please try again shortly.");
            return false;
        }
        try {
            client.getChat().sendMessage(username, message);
            messages.accept(username, message);
            return true;
        } catch (RuntimeException exception) {
            errors.accept("Could not send your Twitch message. Please try again shortly.");
            return false;
        }
    }

    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        username = null;
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            client = null;
            controller.close();
            executor.shutdownNow();
        }
    }

    private static void appendTwitchMessage(String username, String message) {
        appendClientMessage(
                Component.empty()
                        .append(
                                Component.literal("[%s] ".formatted(username))
                                        .withStyle(ChatFormatting.DARK_PURPLE))
                        .append(message));
    }

    static void appendError(String message) {
        appendClientMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    private static void appendClientMessage(Component message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(
                () -> {
                    if (client.player != null) {
                        client.player.sendSystemMessage(message);
                    }
                });
    }
}
