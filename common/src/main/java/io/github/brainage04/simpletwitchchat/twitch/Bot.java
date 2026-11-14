package io.github.brainage04.simpletwitchchat.twitch;

import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.CredentialManagerBuilder;
import com.github.philippheuer.credentialmanager.authcontroller.DeviceFlowController;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.auth.domain.TwitchScopes;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import com.github.twitch4j.chat.TwitchChat;
import com.github.twitch4j.chat.TwitchChatBuilder;
import com.github.twitch4j.chat.events.ChatConnectionStateEvent;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.chat.events.channel.ChannelNoticeEvent;
import com.github.twitch4j.client.websocket.domain.WebsocketConnectionState;
import com.github.twitch4j.common.util.ExponentialBackoffStrategy;
import com.github.twitch4j.util.IBackoffStrategy;
import io.github.brainage04.simpletwitchchat.SimpleTwitchChat;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;

public class Bot {
    private static final String CLIENT_ID = "0bw1jo9kaot6nn6utin49if1n6pjj4";
    private static final String NOT_AUTHORISED =
            "You have not authorised the SimpleTwitchChat bot! Please do so before trying to send messages to Twitch chat.";
    static final String NOT_CONNECTED =
            "Twitch chat is not connected yet. Please try again shortly.";
    static final String NOT_SENT = "Could not send your Twitch message. Please try again shortly.";
    static final String CLOSE_FAILED = "Could not close Twitch chat cleanly.";
    static final int CONNECTION_RETRIES = 8;
    // Twitch requires apps to validate a user token at start and hourly after that:
    // https://dev.twitch.tv/docs/authentication/validate-tokens/
    static final Duration LOGIN_CHECK_PERIOD = Duration.ofHours(1);
    static final Duration RENEWAL_RETRY_DELAY = Duration.ofMinutes(5);

    private final TwitchLogin identityProvider;
    private final ScheduledThreadPoolExecutor executor;
    private final DeviceFlowController controller;
    private final CredentialManager credentialManager;
    private final BiFunction<OAuth2Credential, IBackoffStrategy, TwitchChat> clientFactory;
    private final Function<OAuth2Credential, Optional<Boolean>> loginCheck;
    private final Function<OAuth2Credential, Renewal> renewal;
    private final BiConsumer<String, String> messages;
    private final Consumer<String> errors;
    private final AtomicBoolean renewing = new AtomicBoolean();
    private TwitchChat client;
    private String username;
    private OAuth2Credential credential;
    private Runnable connectionLost;
    private Runnable loginExpired;
    private boolean loginConfirmed;
    private boolean renewedDuringConnect;
    private boolean building;
    private volatile boolean closed;
    private ScheduledFuture<?> renewalRetry;

    public Bot() {
        this(newExecutor(), null, null, null, null, Bot::appendTwitchMessage, Bot::appendError);
    }

    Bot(
            ScheduledThreadPoolExecutor executor,
            DeviceFlowController controller,
            BiFunction<OAuth2Credential, IBackoffStrategy, TwitchChat> clientFactory,
            Function<OAuth2Credential, Optional<Boolean>> loginCheck,
            Function<OAuth2Credential, Renewal> renewal,
            BiConsumer<String, String> messages,
            Consumer<String> errors) {
        this.executor = executor;
        this.messages = messages;
        this.errors = errors;
        identityProvider = new TwitchLogin(CLIENT_ID, TwitchIdentityProvider.OFFICIAL_BASE_URL);
        this.controller = controller != null ? controller : new DeviceFlowController(executor, 0);
        credentialManager =
                CredentialManagerBuilder.builder()
                        .withAuthenticationController(this.controller)
                        .build();
        credentialManager.registerIdentityProvider(identityProvider);
        this.clientFactory = clientFactory != null ? clientFactory : this::createClient;
        this.loginCheck = loginCheck != null ? loginCheck : identityProvider::isCredentialValid;
        this.renewal = renewal != null ? renewal : identityProvider::renew;
    }

    // Daemon threads: a connection attempt that is still retrying must not keep the game process
    // alive after quitting, where the post-main watchdog would end it with a crash report
    // (Main.java:290).
    static ScheduledThreadPoolExecutor newExecutor() {
        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(
                        2, Thread.ofPlatform().name("t4j-bot-", 0).daemon(true).factory());
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    void executeAuthorization(Runnable task) {
        executor.execute(task);
    }

    public URI authorize(Consumer<OAuth2Credential> authorized, Consumer<String> failed) {
        var request =
                controller.startOAuth2DeviceAuthorizationGrantType(
                        identityProvider,
                        List.of(TwitchScopes.CHAT_READ, TwitchScopes.CHAT_EDIT),
                        response -> {
                            // CredentialManager uses null to report a cancelled device flow.
                            if (response == null) {
                                return;
                            }
                            if (response.getCredential() == null) {
                                failed.accept(
                                        "Twitch authorisation failed. Use /regenerateauthurl to try again.");
                            } else {
                                authorized.accept(response.getCredential());
                            }
                        });
        return URI.create(request.getCompleteUri());
    }

    private TwitchChat createClient(OAuth2Credential credential, IBackoffStrategy backoff) {
        return TwitchChatBuilder.builder()
                .withClientId(CLIENT_ID)
                .withScheduledThreadPoolExecutor(executor)
                .withCredentialManager(credentialManager)
                .withChatAccount(credential)
                .withConnectionBackoffStrategy(backoff)
                .build();
    }

    /**
     * Connects to Twitch chat on the calling thread, which the connection attempts can block for
     * minutes, so callers must not be the client thread. The bot's monitor is not held meanwhile.
     */
    public void start(OAuth2Credential credential, Runnable connectionLost, Runnable loginExpired) {
        synchronized (this) {
            if (closed) {
                return;
            }
            building = true;
            this.connectionLost = connectionLost;
            this.loginExpired = loginExpired;
        }
        TwitchChat built;
        ConnectionBackoff backoff = new ConnectionBackoff(this::connectionGaveUp);
        try {
            built = clientFactory.apply(credential, backoff);
        } catch (RuntimeException exception) {
            finishBuild();
            throw exception;
        }
        // TwitchChat connects in its constructor (TwitchChat.java:237) and retries on this thread
        // until it connects or gives up (WebsocketConnection.connect sleeps and calls reconnect
        // inside its lock), so every later attempt reconnects a session that was established.
        backoff.firstConnectionDone();
        if (!publish(built)) {
            try {
                built.close();
            } finally {
                executor.shutdownNow();
            }
            return;
        }
        String account = credential.getUserName();
        if (account == null || account.isBlank()) {
            throw new IllegalArgumentException("Twitch did not provide an account name.");
        }
        var events = built.getEventManager();
        events.onEvent(
                ChannelMessageEvent.class,
                event -> receiveMessage(event.getUser().getName(), event.getMessage()));
        events.onEvent(ChannelNoticeEvent.class, this::receiveNotice);
        events.onEvent(ChatConnectionStateEvent.class, this::connectionChanged);
        synchronized (this) {
            if (closed) {
                return;
            }
            username = account;
            this.credential = credential;
            executor.execute(this::checkLogin);
            long period = LOGIN_CHECK_PERIOD.toMinutes();
            executor.scheduleAtFixedRate(this::checkLogin, period, period, TimeUnit.MINUTES);
        }
        built.joinChannel(account);
    }

    private void finishBuild() {
        boolean closedMeanwhile;
        synchronized (this) {
            building = false;
            closedMeanwhile = closed;
        }
        if (closedMeanwhile) {
            executor.shutdownNow();
        }
    }

    private synchronized boolean publish(TwitchChat built) {
        building = false;
        if (closed) {
            return false;
        }
        client = built;
        return true;
    }

    void receiveMessage(String sender, String message) {
        if (!closed) {
            messages.accept(plainText(sender), plainText(message));
        }
    }

    private void receiveNotice(ChannelNoticeEvent event) {
        String channel;
        synchronized (this) {
            channel = username;
        }
        if (!closed && channel != null && channel.equalsIgnoreCase(event.getChannel().getName())) {
            errors.accept("Twitch: " + plainText(event.getMessage()));
        }
    }

    private void connectionChanged(ChatConnectionStateEvent event) {
        if (event.getState() != WebsocketConnectionState.CONNECTED) {
            return;
        }
        synchronized (this) {
            if (closed || credential == null) {
                return;
            }
            // After failed reconnects twitch4j logs in anonymously when it finds the token
            // invalid (TwitchChat.java:351-354), so a connection says nothing about the login.
            loginConfirmed = false;
            executor.execute(this::checkLogin);
        }
    }

    void checkLogin() {
        OAuth2Credential checked;
        TwitchChat reconnecting = null;
        synchronized (this) {
            if (closed || credential == null) {
                return;
            }
            checked = credential;
            // A renewal that landed while a connection was being made cannot reconnect during it:
            // twitch4j sends the rest of that socket's handshake through the connection, which by
            // then carries the new one (TwitchChat.java:476-486, WebsocketConnection.java:305-311).
            // Its CONNECTED event lands here after the handshake, so the reconnect happens here.
            if (renewedDuringConnect
                    && client != null
                    && client.getState() == WebsocketConnectionState.CONNECTED) {
                renewedDuringConnect = false;
                reconnecting = client;
            }
        }
        if (reconnecting != null) {
            reconnectWithRenewedLogin(reconnecting);
            return;
        }
        // Empty means Twitch could not be asked (TwitchLogin.isCredentialValid), so the next check
        // asks again.
        if (loginCheck.apply(checked).orElse(true)) {
            confirmLogin(checked);
            return;
        }
        // Each refresh spends the refresh token and returns a new one, so two threads renewing the
        // same login would break it:
        // https://dev.twitch.tv/docs/authentication/refresh-tokens/
        if (!renewing.compareAndSet(false, true)) {
            return;
        }
        try {
            renewLogin(checked);
        } finally {
            renewing.set(false);
        }
    }

    private synchronized void confirmLogin(OAuth2Credential checked) {
        if (!closed && credential == checked) {
            loginConfirmed = true;
        }
    }

    /** Renews the login once: a refusal asks for a new login, an unclear answer waits. */
    private void renewLogin(OAuth2Credential checked) {
        Renewal renewal;
        try {
            renewal = this.renewal.apply(checked);
        } catch (RuntimeException exception) {
            renewal = Renewal.UNKNOWN;
        }
        Runnable expired = null;
        TwitchChat reconnecting = null;
        synchronized (this) {
            if (closed || credential != checked) {
                return;
            }
            switch (renewal.outcome()) {
                case RENEWED -> {
                    checked.updateCredential(renewal.credential());
                    // twitch4j decides per connection whether to log in or to go anonymous, and it
                    // reads the credential while the socket is still CONNECTING
                    // (WebsocketConnection.java:54-55), so a renewal that lands during a connect
                    // waits for that connect to finish before it reconnects the chat.
                    WebsocketConnectionState state = client == null ? null : client.getState();
                    renewedDuringConnect = state == WebsocketConnectionState.CONNECTING;
                    loginConfirmed = !renewedDuringConnect;
                    if (state == WebsocketConnectionState.CONNECTED) {
                        reconnecting = client;
                    }
                }
                case REFUSED -> {
                    loginConfirmed = false;
                    expired = loginExpired;
                    loginExpired = null;
                }
                case UNKNOWN -> {
                    loginConfirmed = false;
                    if (renewalRetry == null || renewalRetry.isDone()) {
                        renewalRetry =
                                executor.schedule(
                                        this::checkLogin,
                                        RENEWAL_RETRY_DELAY.toMinutes(),
                                        TimeUnit.MINUTES);
                    }
                }
            }
        }
        if (reconnecting != null) {
            reconnectWithRenewedLogin(reconnecting);
            return;
        }
        if (renewal.outcome() == Renewal.Outcome.RENEWED) {
            SimpleTwitchChat.LOGGER.info("Twitch login renewed.");
            return;
        }
        if (renewal.outcome() == Renewal.Outcome.UNKNOWN) {
            SimpleTwitchChat.LOGGER.warn(
                    "The Twitch login could not be renewed, so it is asked again in {} minutes.",
                    RENEWAL_RETRY_DELAY.toMinutes());
            return;
        }
        SimpleTwitchChat.LOGGER.warn("The Twitch login could not be renewed.");
        if (expired != null) {
            expired.run();
        }
    }

    private void reconnectWithRenewedLogin(TwitchChat chat) {
        SimpleTwitchChat.LOGGER.info("Twitch login renewed, reconnecting the chat with it.");
        reconnect(chat);
    }

    private void reconnect(TwitchChat chat) {
        try {
            chat.reconnect();
        } catch (RuntimeException exception) {
            SimpleTwitchChat.LOGGER.warn(
                    "Reconnecting Twitch chat after a renewal failed.", exception);
        }
    }

    private void connectionGaveUp() {
        Runnable lost;
        synchronized (this) {
            if (closed || connectionLost == null) {
                return;
            }
            lost = connectionLost;
            connectionLost = null;
            // Called from inside WebsocketConnection.connect and its disconnect handler, while
            // closing the bot closes that same connection.
            executor.execute(lost);
        }
    }

    public synchronized boolean sendChatMessage(String message) {
        if (closed || client == null || username == null) {
            errors.accept(NOT_AUTHORISED);
            return false;
        }
        if (client.getState() != WebsocketConnectionState.CONNECTED
                || !client.isChannelJoined(username)
                || !loginConfirmed) {
            errors.accept(NOT_CONNECTED);
            return false;
        }
        try {
            if (!client.sendMessage(username, message)) {
                errors.accept(NOT_SENT);
                return false;
            }
            messages.accept(plainText(username), plainText(message));
            return true;
        } catch (RuntimeException exception) {
            errors.accept(NOT_SENT);
            return false;
        }
    }

    public void close() {
        TwitchChat previous;
        boolean stopExecutor;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            username = null;
            credential = null;
            previous = client;
            client = null;
            // A connection still being built finishes on its own thread, then closes itself
            // and stops the executor (start).
            stopExecutor = !building;
        }
        // TwitchChat.close waits for the connection's lock, which a reconnect holds through each
        // connect attempt and backoff sleep (WebsocketConnection.connect, reconnect, disconnect),
        // for minutes during an outage. Quitting and /regenerateauthurl run this on the client
        // thread, so the transport closes on a thread of its own. `closed` already stops this bot
        // from showing or sending messages.
        Thread.ofPlatform()
                .name("t4j-bot-close")
                .daemon(true)
                .start(() -> closeResources(previous, stopExecutor));
    }

    private void closeResources(TwitchChat previous, boolean stopExecutor) {
        if (previous != null) {
            reportCloseFailure(previous::close);
        }
        reportCloseFailure(
                () -> {
                    controller.close();
                    SimpleTwitchChat.LOGGER.info("Twitch device authorisation controller closed.");
                });
        if (stopExecutor) {
            executor.shutdownNow();
            SimpleTwitchChat.LOGGER.info(
                    "Twitch bot executor shutdown requested: {}.", executor.isShutdown());
        }
    }

    private void reportCloseFailure(Runnable step) {
        try {
            step.run();
        } catch (RuntimeException exception) {
            SimpleTwitchChat.LOGGER.warn(CLOSE_FAILED, exception);
            errors.accept(CLOSE_FAILED);
        }
    }

    // The client renders section-sign codes inside literal text (SubStringSource.java:71), and
    // Twitch text comes from viewers. Vanilla refuses these codes in player chat
    // (ServerGamePacketListenerImpl.java:1714-1716, StringUtil.java:62-63).
    static String plainText(String text) {
        return StringUtil.filterText(ChatFormatting.stripFormatting(text));
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

    private static final class ConnectionBackoff implements IBackoffStrategy {
        private final IBackoffStrategy delegate = ExponentialBackoffStrategy.builder().build();
        private final Runnable gaveUp;
        private volatile boolean firstConnectionDone;

        ConnectionBackoff(Runnable gaveUp) {
            this.gaveUp = gaveUp;
        }

        void firstConnectionDone() {
            firstConnectionDone = true;
        }

        @Override
        public long get() {
            long delay = delegate.get();
            if (!firstConnectionDone && delegate.getFailures() > CONNECTION_RETRIES) {
                gaveUp.run();
                return -1;
            }
            return delay;
        }

        @Override
        public void reset() {
            delegate.reset();
        }

        @Override
        public int getFailures() {
            return delegate.getFailures();
        }
    }
}
