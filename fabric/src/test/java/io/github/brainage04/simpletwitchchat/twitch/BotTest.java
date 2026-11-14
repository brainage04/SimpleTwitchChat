package io.github.brainage04.simpletwitchchat.twitch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.philippheuer.credentialmanager.authcontroller.DeviceFlowController;
import com.github.philippheuer.credentialmanager.domain.DeviceAuthorization;
import com.github.philippheuer.credentialmanager.domain.DeviceTokenResponse;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.chat.TwitchChat;
import com.github.twitch4j.chat.TwitchChatBuilder;
import com.github.twitch4j.chat.events.ChatConnectionStateEvent;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.chat.events.channel.ChannelNoticeEvent;
import com.github.twitch4j.client.websocket.domain.WebsocketConnectionState;
import com.github.twitch4j.util.IBackoffStrategy;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentMatchers;

class BotTest {
    private final TwitchChat client = mock(TwitchChat.class, RETURNS_DEEP_STUBS);
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private final List<String> messages = new CopyOnWriteArrayList<>();
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final List<String> sessionEvents = new CopyOnWriteArrayList<>();
    private final Map<Class<?>, Consumer<Object>> listeners = new HashMap<>();
    private final AtomicReference<Optional<Boolean>> loginValid =
            new AtomicReference<>(Optional.of(true));
    private final AtomicReference<Renewal> renewalAnswer = new AtomicReference<>(Renewal.REFUSED);
    private final List<OAuth2Credential> renewals = new CopyOnWriteArrayList<>();
    private final Bot bot =
            new Bot(
                    executor,
                    null,
                    (token, backoff) -> client,
                    token -> loginValid.get(),
                    this::renew,
                    (sender, message) -> messages.add(sender + ": " + message),
                    errors::add);

    @BeforeEach
    void captureListeners() {
        var events = client.getEventManager();
        doAnswer(
                        invocation -> {
                            listeners.put(invocation.getArgument(0), invocation.getArgument(1));
                            return null;
                        })
                .when(events)
                .onEvent(
                        ArgumentMatchers.<Class<Object>>any(),
                        ArgumentMatchers.<Consumer<Object>>any());
    }

    private OAuth2Credential token() {
        OAuth2Credential token = mock(OAuth2Credential.class);
        when(token.getUserName()).thenReturn("streamer");
        return token;
    }

    private Renewal renew(OAuth2Credential credential) {
        renewals.add(credential);
        return renewalAnswer.get();
    }

    private void start() {
        startWith(token());
    }

    private void startWith(OAuth2Credential credential) {
        bot.start(
                credential,
                () -> sessionEvents.add("connection lost"),
                () -> sessionEvents.add("login expired"));
        when(client.getState()).thenReturn(WebsocketConnectionState.CONNECTED);
        when(client.isChannelJoined("streamer")).thenReturn(true);
        when(client.sendMessage(any(), any())).thenReturn(true);
        runQueuedTasks();
    }

    private static OAuth2Credential liveLogin(String accessToken, String refreshToken) {
        return new OAuth2Credential(
                "twitch", accessToken, refreshToken, null, "streamer", 3600, List.of());
    }

    private void runQueuedTasks() {
        try {
            executor.submit(() -> {}).get(2, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void fire(Class<?> type, Object event) {
        listeners.get(type).accept(event);
    }

    @AfterEach
    void close() {
        bot.close();
    }

    @Test
    void joinsOnAuthorizationBeforeFirstSendAndReceivesMessages() {
        start();
        verify(client).joinChannel("streamer");
        ChannelMessageEvent event = mock(ChannelMessageEvent.class, RETURNS_DEEP_STUBS);
        when(event.getUser().getName()).thenReturn("viewer");
        when(event.getMessage()).thenReturn("hello");
        fire(ChannelMessageEvent.class, event);
        assertEquals(List.of("viewer: hello"), messages);
    }

    @Test
    void sendsAndEchoesOneMessageWithoutRejoining() {
        start();
        assertTrue(bot.sendChatMessage("hello"));
        verify(client).sendMessage("streamer", "hello");
        verify(client).joinChannel("streamer");
        assertEquals(List.of("streamer: hello"), messages);
        assertTrue(errors.isEmpty());
    }

    @Test
    void messageTwitchDoesNotQueueIsReportedAndNotEchoed() {
        start();
        when(client.sendMessage("streamer", "lost")).thenReturn(false);
        assertFalse(bot.sendChatMessage("lost"));
        assertTrue(messages.isEmpty());
        assertEquals(List.of(Bot.NOT_SENT), errors);
    }

    @Test
    void twitchNoticesForTheOwnChannelAreShown() {
        start();
        ChannelNoticeEvent own = mock(ChannelNoticeEvent.class, RETURNS_DEEP_STUBS);
        when(own.getChannel().getName()).thenReturn("streamer");
        when(own.getMessage()).thenReturn("§cYour message was not sent because it is identical.");
        ChannelNoticeEvent other = mock(ChannelNoticeEvent.class, RETURNS_DEEP_STUBS);
        when(other.getChannel().getName()).thenReturn("someone");
        when(other.getMessage()).thenReturn("elsewhere");
        fire(ChannelNoticeEvent.class, own);
        fire(ChannelNoticeEvent.class, other);
        assertEquals(List.of("Twitch: Your message was not sent because it is identical."), errors);
    }

    @Test
    void formattingCodesFromTwitchAreNotRendered() {
        start();
        bot.receiveMessage("vie§kwer", "§kx§r§ehi §");
        assertTrue(bot.sendChatMessage("§cred"));
        assertEquals(List.of("viewer: xhi ", "streamer: red"), messages);
    }

    @ParameterizedTest
    @EnumSource(
            value = WebsocketConnectionState.class,
            names = {"DISCONNECTED", "RECONNECTING", "LOST"})
    void disconnectedMessagesAreRejectedAndNotEchoed(WebsocketConnectionState state) {
        start();
        when(client.getState()).thenReturn(state);
        assertFalse(bot.sendChatMessage("lost"));
        verify(client, never()).sendMessage("streamer", "lost");
        assertTrue(messages.isEmpty());
        assertTrue(errors.getFirst().contains("not connected"));
    }

    @Test
    void connectedButUnjoinedMessagesAreRejectedAndNotEchoed() {
        start();
        when(client.isChannelJoined("streamer")).thenReturn(false);
        assertFalse(bot.sendChatMessage("lost"));
        verify(client, never()).sendMessage("streamer", "lost");
        assertTrue(messages.isEmpty());
        assertTrue(errors.getFirst().contains("not connected"));
    }

    @Test
    void unauthorizedMessagesAreRejected() {
        assertFalse(bot.sendChatMessage("lost"));
        assertTrue(errors.getFirst().contains("not authorised"));
        verify(client, never()).sendMessage(any(), any());
    }

    @Test
    void sendFailureIsNotEchoedAsSuccess() {
        start();
        doThrow(new IllegalStateException("offline")).when(client).sendMessage("streamer", "lost");
        assertFalse(bot.sendChatMessage("lost"));
        assertTrue(messages.isEmpty());
        assertTrue(errors.getFirst().contains("Could not send"));
    }

    @Test
    void expiredLoginStopsSendingAndIsReportedOnce() {
        start();
        loginValid.set(Optional.of(false));
        bot.checkLogin();
        bot.checkLogin();
        assertEquals(List.of("login expired"), sessionEvents);
        assertFalse(bot.sendChatMessage("lost"));
        verify(client, never()).sendMessage("streamer", "lost");
        assertTrue(messages.isEmpty());
        assertEquals(List.of(Bot.NOT_CONNECTED), errors);
    }

    @Test
    void reconnectChecksTheLoginBeforeSendingAgain() {
        start();
        loginValid.set(Optional.of(false));
        fire(
                ChatConnectionStateEvent.class,
                new ChatConnectionStateEvent(
                        WebsocketConnectionState.CONNECTING,
                        WebsocketConnectionState.CONNECTED,
                        client));
        runQueuedTasks();
        assertEquals(List.of("login expired"), sessionEvents);
        assertFalse(bot.sendChatMessage("anonymous"));
        verify(client, never()).sendMessage("streamer", "anonymous");
    }

    @Test
    void loginThatTwitchCannotCheckKeepsChatUsable() {
        start();
        loginValid.set(Optional.empty());
        fire(
                ChatConnectionStateEvent.class,
                new ChatConnectionStateEvent(
                        WebsocketConnectionState.CONNECTING,
                        WebsocketConnectionState.CONNECTED,
                        client));
        runQueuedTasks();
        assertTrue(sessionEvents.isEmpty());
        assertTrue(renewals.isEmpty(), "a login Twitch cannot check must not be renewed");
        assertTrue(bot.sendChatMessage("hello"));
        loginValid.set(Optional.of(false));
        bot.checkLogin();
        assertEquals(List.of("login expired"), sessionEvents);
    }

    @Test
    void expiredLoginIsRenewedAndSendingContinues() {
        OAuth2Credential login = liveLogin("expired", "refresh-one");
        startWith(login);
        loginValid.set(Optional.of(false));
        renewalAnswer.set(Renewal.renewed(liveLogin("fresh", "refresh-two")));
        bot.checkLogin();
        assertEquals(List.of(login), renewals);
        assertTrue(sessionEvents.isEmpty());
        assertEquals("fresh", login.getAccessToken());
        assertEquals("refresh-two", login.getRefreshToken());
        assertTrue(bot.sendChatMessage("hello"));
        assertTrue(errors.isEmpty());
    }

    @Test
    void failedRenewalFallsBackToANewLoginLink() {
        OAuth2Credential login = liveLogin("expired", "refresh-spent");
        startWith(login);
        loginValid.set(Optional.of(false));
        renewalAnswer.set(Renewal.REFUSED);
        bot.checkLogin();
        bot.checkLogin();
        assertEquals(List.of("login expired"), sessionEvents);
        assertEquals("expired", login.getAccessToken());
        assertFalse(bot.sendChatMessage("lost"));
        verify(client, never()).sendMessage("streamer", "lost");
        verify(client, never()).reconnect();
        assertEquals(List.of(Bot.NOT_CONNECTED), errors);
    }

    @Test
    void renewedLoginReconnectsTheAnonymousSession() {
        OAuth2Credential login = liveLogin("expired", "refresh-one");
        startWith(login);
        loginValid.set(Optional.of(false));
        renewalAnswer.set(Renewal.renewed(liveLogin("fresh", "refresh-two")));
        // twitch4j has just reconnected, which is when it drops to anonymous if the token expired.
        fire(
                ChatConnectionStateEvent.class,
                new ChatConnectionStateEvent(
                        WebsocketConnectionState.CONNECTING,
                        WebsocketConnectionState.CONNECTED,
                        client));
        runQueuedTasks();
        assertEquals("fresh", login.getAccessToken());
        verify(client).reconnect();
        assertTrue(bot.sendChatMessage("hello"));
    }

    @Test
    void renewalDuringAConnectReconnectsWhenThatConnectFinishes() {
        OAuth2Credential login = liveLogin("expired", "refresh-one");
        startWith(login);
        // twitch4j reads the credential and picks login or anonymous inside onConnected, while
        // the state is still CONNECTING (WebsocketConnection.java:54-55).
        when(client.getState()).thenReturn(WebsocketConnectionState.CONNECTING);
        loginValid.set(Optional.of(false));
        renewalAnswer.set(Renewal.renewed(liveLogin("fresh", "refresh-two")));
        bot.checkLogin();
        assertEquals("fresh", login.getAccessToken());
        verify(client, never()).reconnect();
        // That connect finished and went anonymous, and the fresh token validates, so only the
        // renewal that waited for the connect can still reconnect the chat.
        loginValid.set(Optional.of(true));
        when(client.getState()).thenReturn(WebsocketConnectionState.CONNECTED);
        fire(
                ChatConnectionStateEvent.class,
                new ChatConnectionStateEvent(
                        WebsocketConnectionState.CONNECTING,
                        WebsocketConnectionState.CONNECTED,
                        client));
        runQueuedTasks();
        verify(client).reconnect();
    }

    @Test
    void checkBeforeThePendingReconnectDoesNotConfirmTheAnonymousSession() {
        OAuth2Credential login = liveLogin("expired", "refresh-one");
        startWith(login);
        when(client.getState()).thenReturn(WebsocketConnectionState.CONNECTING);
        loginValid.set(Optional.of(false));
        renewalAnswer.set(Renewal.renewed(liveLogin("fresh", "refresh-two")));
        bot.checkLogin();
        // The hourly check runs before that connect finishes; the fresh token validates, but the
        // socket may still be the anonymous one the pending reconnect replaces.
        loginValid.set(Optional.of(true));
        bot.checkLogin();
        // twitch4j has set CONNECTED, and its CONNECTED event has not reached the bot yet.
        when(client.getState()).thenReturn(WebsocketConnectionState.CONNECTED);
        assertFalse(bot.sendChatMessage("anonymous"));
        verify(client, never()).sendMessage("streamer", "anonymous");
        fire(
                ChatConnectionStateEvent.class,
                new ChatConnectionStateEvent(
                        WebsocketConnectionState.CONNECTING,
                        WebsocketConnectionState.CONNECTED,
                        client));
        runQueuedTasks();
        verify(client).reconnect();
    }

    @Test
    void renewalWithoutAConnectionLeavesReconnectingToTheLibrary() {
        OAuth2Credential login = liveLogin("expired", "refresh-one");
        startWith(login);
        when(client.getState()).thenReturn(WebsocketConnectionState.RECONNECTING);
        loginValid.set(Optional.of(false));
        renewalAnswer.set(Renewal.renewed(liveLogin("fresh", "refresh-two")));
        bot.checkLogin();
        assertEquals("fresh", login.getAccessToken());
        verify(client, never()).reconnect();
    }

    @Test
    void renewalTwitchCannotAnswerKeepsTheLoginForTheNextCheck() {
        OAuth2Credential login = liveLogin("expired", "refresh-one");
        startWith(login);
        loginValid.set(Optional.of(false));
        renewalAnswer.set(Renewal.UNKNOWN);
        bot.checkLogin();
        assertTrue(sessionEvents.isEmpty(), "a 429 or a 5xx must not ask for a new login");
        verify(client, never()).reconnect();
        assertFalse(bot.sendChatMessage("lost"));
        verify(client, never()).sendMessage("streamer", "lost");
        assertEquals("expired", login.getAccessToken());
        renewalAnswer.set(Renewal.renewed(liveLogin("fresh", "refresh-two")));
        bot.checkLogin();
        assertEquals("fresh", login.getAccessToken());
        assertTrue(bot.sendChatMessage("hello"));
        assertTrue(sessionEvents.isEmpty());
    }

    @Test
    void renewalTwitchCannotAnswerIsRetriedEveryFewMinutes() {
        OAuth2Credential login = liveLogin("expired", "refresh-one");
        startWith(login);
        loginValid.set(Optional.of(false));
        renewalAnswer.set(Renewal.UNKNOWN);
        bot.checkLogin();
        bot.checkLogin();
        List<RunnableScheduledFuture<?>> retries = pendingRetries();
        assertEquals(1, retries.size(), "two unclear answers must not stack retries");
        long delay = retries.getFirst().getDelay(TimeUnit.MINUTES);
        assertTrue(delay > 3 && delay <= 5, "retry in " + delay + " minutes");
        // Twitch still cannot answer when the retry runs, so the retry schedules the next one.
        retries.getFirst().run();
        assertEquals(3, renewals.size());
        List<RunnableScheduledFuture<?>> next = pendingRetries();
        assertEquals(1, next.size(), "an unclear retry must schedule the next one");
        assertNotSame(retries.getFirst(), next.getFirst());
        renewalAnswer.set(Renewal.renewed(liveLogin("fresh", "refresh-two")));
        next.getFirst().run();
        assertEquals("fresh", login.getAccessToken());
        assertTrue(pendingRetries().isEmpty(), "a renewed login needs no retry");
        assertTrue(bot.sendChatMessage("hello"));
    }

    private List<RunnableScheduledFuture<?>> pendingRetries() {
        List<RunnableScheduledFuture<?>> retries = new ArrayList<>();
        for (Runnable task : executor.getQueue()) {
            if (task instanceof RunnableScheduledFuture<?> future
                    && !future.isPeriodic()
                    && !future.isDone()) {
                retries.add(future);
            }
        }
        return retries;
    }

    @Test
    void onlyOneRenewalRunsAtATime() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Bot renewingBot =
                new Bot(
                        executor,
                        null,
                        (token, backoff) -> client,
                        token -> Optional.of(false),
                        token -> {
                            calls.incrementAndGet();
                            entered.countDown();
                            awaitUninterruptibly(release);
                            return Renewal.renewed(liveLogin("fresh", "refresh-two"));
                        },
                        (sender, message) -> messages.add(sender + ": " + message),
                        errors::add);
        renewingBot.start(
                liveLogin("expired", "refresh-one"),
                () -> {},
                () -> sessionEvents.add("login expired"));
        when(client.getState()).thenReturn(WebsocketConnectionState.CONNECTED);
        when(client.isChannelJoined("streamer")).thenReturn(true);
        when(client.sendMessage(any(), any())).thenReturn(true);
        assertTrue(entered.await(2, TimeUnit.SECONDS), "the start-up check must renew the login");
        renewingBot.checkLogin();
        assertEquals(1, calls.get(), "a second check must not spend the refresh token again");
        release.countDown();
        runQueuedTasks();
        assertEquals(1, calls.get());
        assertTrue(sessionEvents.isEmpty());
        assertTrue(renewingBot.sendChatMessage("hello"));
        renewingBot.close();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
    }

    @Test
    void loginIsCheckedEveryHour() {
        start();
        List<RunnableScheduledFuture<?>> periodic = new ArrayList<>();
        for (Runnable task : executor.getQueue()) {
            if (task instanceof RunnableScheduledFuture<?> future && future.isPeriodic()) {
                periodic.add(future);
            }
        }
        assertEquals(1, periodic.size());
        long delay = periodic.getFirst().getDelay(TimeUnit.MINUTES);
        assertTrue(delay > 58 && delay <= 60, "first check in " + delay + " minutes");
        assertEquals(Duration.ofHours(1), Bot.LOGIN_CHECK_PERIOD);
    }

    @Test
    void connectingClientDoesNotBlockSendingOrClosing() throws Exception {
        CountDownLatch connecting = new CountDownLatch(1);
        CountDownLatch connected = new CountDownLatch(1);
        Bot slow =
                new Bot(
                        executor,
                        null,
                        (token, backoff) -> {
                            connecting.countDown();
                            awaitUninterruptibly(connected);
                            return client;
                        },
                        token -> Optional.of(true),
                        this::renew,
                        (sender, message) -> messages.add(message),
                        errors::add);
        Thread starter =
                Thread.ofPlatform()
                        .daemon(true)
                        .start(() -> slow.start(token(), () -> {}, () -> {}));
        assertTrue(connecting.await(2, TimeUnit.SECONDS));
        assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> {
                    assertFalse(slow.sendChatMessage("early"));
                    slow.close();
                });
        assertFalse(executor.isShutdown());
        connected.countDown();
        starter.join(2000);
        verify(client).close();
        verify(client, never()).joinChannel(any());
        assertTrue(executor.isShutdown());
    }

    @Test
    void connectionThatGivesUpWhileConnectingIsReportedOnce() {
        Bot giving =
                new Bot(
                        executor,
                        null,
                        (token, strategy) -> {
                            for (int attempt = 0; attempt < Bot.CONNECTION_RETRIES; attempt++) {
                                assertTrue(strategy.get() >= 0);
                            }
                            assertEquals(Bot.CONNECTION_RETRIES, strategy.getFailures());
                            runQueuedTasks();
                            assertTrue(sessionEvents.isEmpty());
                            assertTrue(strategy.get() < 0);
                            strategy.reset();
                            assertEquals(0, strategy.getFailures());
                            for (int attempt = 0; attempt <= Bot.CONNECTION_RETRIES; attempt++) {
                                strategy.get();
                            }
                            return client;
                        },
                        token -> Optional.of(true),
                        this::renew,
                        (sender, message) -> messages.add(message),
                        errors::add);
        giving.start(token(), () -> sessionEvents.add("connection lost"), () -> {});
        runQueuedTasks();
        assertEquals(List.of("connection lost"), sessionEvents);
        giving.close();
    }

    @Test
    void establishedSessionKeepsReconnecting() {
        AtomicReference<IBackoffStrategy> backoff = new AtomicReference<>();
        Bot reconnecting =
                new Bot(
                        executor,
                        null,
                        (token, strategy) -> {
                            backoff.set(strategy);
                            return client;
                        },
                        token -> Optional.of(true),
                        this::renew,
                        (sender, message) -> messages.add(message),
                        errors::add);
        reconnecting.start(token(), () -> sessionEvents.add("connection lost"), () -> {});
        IBackoffStrategy strategy = backoff.get();
        for (int attempt = 0; attempt <= 2 * Bot.CONNECTION_RETRIES; attempt++) {
            assertTrue(strategy.get() >= 0, "attempt " + attempt);
        }
        runQueuedTasks();
        assertTrue(sessionEvents.isEmpty());
        reconnecting.close();
    }

    @Test
    void buildFailureAfterCloseStopsTheExecutor() {
        AtomicReference<Bot> building = new AtomicReference<>();
        Bot failing =
                new Bot(
                        executor,
                        null,
                        (token, backoff) -> {
                            building.get().close();
                            throw new IllegalStateException("offline");
                        },
                        token -> Optional.of(true),
                        this::renew,
                        (sender, message) -> messages.add(message),
                        errors::add);
        building.set(failing);
        assertThrows(IllegalStateException.class, () -> failing.start(token(), () -> {}, () -> {}));
        assertTrue(executor.isShutdown());
    }

    @Test
    void buildFailureWithoutCloseLeavesClosingToTheSession() throws Exception {
        Bot failing =
                new Bot(
                        executor,
                        null,
                        (token, backoff) -> {
                            throw new IllegalStateException("offline");
                        },
                        token -> Optional.of(true),
                        this::renew,
                        (sender, message) -> messages.add(message),
                        errors::add);
        assertThrows(IllegalStateException.class, () -> failing.start(token(), () -> {}, () -> {}));
        assertFalse(executor.isShutdown());
        failing.close();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
    }

    @Test
    void connectionThreadsDoNotKeepTheGameRunning() throws Exception {
        ScheduledThreadPoolExecutor botExecutor = Bot.newExecutor();
        try {
            assertTrue(botExecutor.submit(() -> Thread.currentThread().isDaemon()).get());
            assertTrue(botExecutor.getRemoveOnCancelPolicy());
        } finally {
            botExecutor.shutdownNow();
        }
    }

    @Test
    void deviceFlowResultIsPassedOn() {
        DeviceFlowController controller = mock(DeviceFlowController.class);
        DeviceAuthorization request = mock(DeviceAuthorization.class);
        when(request.getCompleteUri()).thenReturn("https://id.twitch.tv/activate?code=test");
        AtomicReference<Consumer<DeviceTokenResponse>> response = new AtomicReference<>();
        when(controller.startOAuth2DeviceAuthorizationGrantType(any(), any(), any()))
                .thenAnswer(
                        invocation -> {
                            response.set(invocation.getArgument(2));
                            return request;
                        });
        Bot controlled =
                new Bot(
                        executor,
                        controller,
                        (token, backoff) -> client,
                        token -> Optional.of(true),
                        this::renew,
                        (sender, message) -> messages.add(message),
                        errors::add);
        OAuth2Credential token = token();
        controlled.authorize(credential -> messages.add(credential.getUserName()), errors::add);
        DeviceTokenResponse approved = mock(DeviceTokenResponse.class);
        when(approved.getCredential()).thenReturn(token);
        response.get().accept(approved);
        response.get().accept(mock(DeviceTokenResponse.class));
        assertEquals(List.of("streamer"), messages);
        assertEquals(
                List.of("Twitch authorisation failed. Use /regenerateauthurl to try again."),
                errors);
        controlled.close();
    }

    @Test
    void closingStopsExecutorAndIgnoresLateMessages() throws Exception {
        start();
        bot.close();
        bot.close();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        verify(client).close();
        bot.receiveMessage("viewer", "late");
        assertTrue(messages.isEmpty());
        assertFalse(bot.sendChatMessage("late"));
    }

    @Test
    void cancelledDeviceFlowAcceptsTheLibraryNullResponse() {
        DeviceFlowController controller = mock(DeviceFlowController.class);
        DeviceAuthorization request = mock(DeviceAuthorization.class);
        when(request.getCompleteUri()).thenReturn("https://id.twitch.tv/activate?code=test");
        AtomicReference<Consumer<DeviceTokenResponse>> response = new AtomicReference<>();
        when(controller.startOAuth2DeviceAuthorizationGrantType(any(), any(), any()))
                .thenAnswer(
                        invocation -> {
                            response.set(invocation.getArgument(2));
                            return request;
                        });
        Bot controlled =
                new Bot(
                        executor,
                        controller,
                        (token, backoff) -> client,
                        token -> Optional.of(true),
                        this::renew,
                        (sender, message) -> messages.add(message),
                        errors::add);
        assertEquals(
                URI.create(request.getCompleteUri()),
                controlled.authorize(token -> messages.add("authorized"), errors::add));
        controlled.close();
        response.get().accept(null);
        assertTrue(messages.isEmpty());
        assertTrue(errors.isEmpty());
    }

    @Test
    void closeReleasesMonitorBeforeWaitingForTransportCallbacks() throws Exception {
        start();
        doAnswer(
                        invocation -> {
                            FutureTask<Void> callback =
                                    new FutureTask<>(
                                            () -> {
                                                bot.receiveMessage("viewer", "late");
                                                return null;
                                            });
                            Thread.ofPlatform().daemon(true).start(callback);
                            callback.get(2, TimeUnit.SECONDS);
                            return null;
                        })
                .when(client)
                .close();
        bot.close();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        assertTrue(messages.isEmpty());
    }

    @Test
    void transportCloseFailureIsReportedAndStillStopsTheExecutor() throws Exception {
        start();
        doThrow(new IllegalStateException("close failed")).when(client).close();
        bot.close();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        assertEquals(List.of(Bot.CLOSE_FAILED), errors);
    }

    @Test
    void closeDuringAReconnectReturnsAtOnce() throws Exception {
        ScheduledThreadPoolExecutor botExecutor = Bot.newExecutor();
        CountDownLatch drop = new CountDownLatch(1);
        CountDownLatch reconnecting = new CountDownLatch(1);
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        Thread.ofPlatform().daemon(true).start(() -> acceptOnceThenDrop(server, drop));
        Bot real =
                new Bot(
                        botExecutor,
                        null,
                        (token, backoff) -> {
                            TwitchChat chat =
                                    TwitchChatBuilder.builder()
                                            .withBaseUrl("ws://127.0.0.1:" + server.getLocalPort())
                                            .withScheduledThreadPoolExecutor(botExecutor)
                                            .withConnectionBackoffStrategy(backoff)
                                            .build();
                            chat.getEventManager()
                                    .onEvent(
                                            ChatConnectionStateEvent.class,
                                            event -> {
                                                if (event.getState()
                                                        == WebsocketConnectionState.RECONNECTING) {
                                                    reconnecting.countDown();
                                                }
                                            });
                            return chat;
                        },
                        token -> Optional.of(true),
                        this::renew,
                        (sender, message) -> messages.add(message),
                        errors::add);
        real.start(token(), () -> sessionEvents.add("connection lost"), () -> {});
        drop.countDown();
        assertTrue(reconnecting.await(30, TimeUnit.SECONDS));
        long started = System.nanoTime();
        real.close();
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        System.out.println(
                "Bot.close during a twitch4j reconnect returned after " + millis + " ms");
        assertTrue(millis < 200, "Bot.close took " + millis + " ms");
        assertTrue(botExecutor.awaitTermination(30, TimeUnit.SECONDS));
        assertTrue(sessionEvents.isEmpty());
        assertTrue(errors.isEmpty());
    }

    // Answers one WebSocket handshake (RFC 6455 section 4.2.2), refuses every later connection and
    // drops the open one on request, which starts twitch4j's reconnect loop.
    private static void acceptOnceThenDrop(ServerSocket server, CountDownLatch drop) {
        try (server;
                Socket socket = server.accept()) {
            server.close();
            BufferedReader request =
                    new BufferedReader(
                            new InputStreamReader(
                                    socket.getInputStream(), StandardCharsets.ISO_8859_1));
            String key = null;
            for (String line = request.readLine();
                    line != null && !line.isEmpty();
                    line = request.readLine()) {
                if (line.regionMatches(true, 0, "Sec-WebSocket-Key:", 0, 18)) {
                    key = line.substring(18).trim();
                }
            }
            byte[] digest =
                    MessageDigest.getInstance("SHA-1")
                            .digest(
                                    (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                            .getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream()
                    .write(
                            ("HTTP/1.1 101 Switching Protocols\r\n"
                                            + "Upgrade: websocket\r\n"
                                            + "Connection: Upgrade\r\n"
                                            + "Sec-WebSocket-Accept: "
                                            + Base64.getEncoder().encodeToString(digest)
                                            + "\r\n\r\n")
                                    .getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            drop.await();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void newSessionSendsWhileThePreviousOneIsStillClosing() throws Exception {
        start();
        CountDownLatch closing = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            awaitUninterruptibly(closing);
                            return null;
                        })
                .when(client)
                .close();
        assertTimeoutPreemptively(Duration.ofMillis(200), bot::close);
        TwitchChat nextClient = mock(TwitchChat.class, RETURNS_DEEP_STUBS);
        when(nextClient.getState()).thenReturn(WebsocketConnectionState.CONNECTED);
        when(nextClient.isChannelJoined("streamer")).thenReturn(true);
        when(nextClient.sendMessage(any(), any())).thenReturn(true);
        // One thread runs the start-up login check before the empty task below.
        ScheduledThreadPoolExecutor nextExecutor = new ScheduledThreadPoolExecutor(1);
        Bot next =
                new Bot(
                        nextExecutor,
                        null,
                        (token, backoff) -> nextClient,
                        token -> Optional.of(true),
                        this::renew,
                        (sender, message) -> messages.add(sender + ": " + message),
                        errors::add);
        next.start(token(), () -> {}, () -> {});
        nextExecutor.submit(() -> {}).get(2, TimeUnit.SECONDS);
        assertTrue(next.sendChatMessage("hello"));
        assertFalse(bot.sendChatMessage("twice"));
        bot.receiveMessage("viewer", "shown twice");
        verify(client, never()).sendMessage(any(), any());
        assertEquals(List.of("streamer: hello"), messages);
        closing.countDown();
        verify(client, timeout(2000)).close();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        next.close();
        assertTrue(nextExecutor.awaitTermination(2, TimeUnit.SECONDS));
    }

    @Test
    void readsAccountNameAfterClientConstructionEnrichesCredential() {
        OAuth2Credential token = mock(OAuth2Credential.class);
        Bot controlled =
                new Bot(
                        executor,
                        null,
                        (credential, backoff) -> {
                            when(credential.getUserName()).thenReturn("streamer");
                            return client;
                        },
                        credential -> Optional.of(true),
                        this::renew,
                        (sender, message) -> messages.add(message),
                        errors::add);
        controlled.start(token, () -> {}, () -> {});
        verify(client).joinChannel("streamer");
        controlled.close();
    }

    @Test
    void controllerCloseFailureIsReportedAndStillStopsTheExecutor() throws Exception {
        DeviceFlowController controller = mock(DeviceFlowController.class);
        Bot controlled =
                new Bot(
                        executor,
                        controller,
                        (token, backoff) -> client,
                        token -> Optional.of(true),
                        this::renew,
                        (sender, message) -> messages.add(message),
                        errors::add);
        doThrow(new IllegalStateException("controller close failed")).when(controller).close();
        controlled.close();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        assertEquals(List.of(Bot.CLOSE_FAILED), errors);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
