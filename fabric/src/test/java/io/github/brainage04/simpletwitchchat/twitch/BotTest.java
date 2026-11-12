package io.github.brainage04.simpletwitchchat.twitch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.philippheuer.credentialmanager.authcontroller.DeviceFlowController;
import com.github.philippheuer.credentialmanager.domain.DeviceAuthorization;
import com.github.philippheuer.credentialmanager.domain.DeviceTokenResponse;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.chat.TwitchChat;
import com.github.twitch4j.chat.events.ChatConnectionStateEvent;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.chat.events.channel.ChannelNoticeEvent;
import com.github.twitch4j.client.websocket.domain.WebsocketConnectionState;
import com.github.twitch4j.util.IBackoffStrategy;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
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
    private final Bot bot =
            new Bot(
                    executor,
                    null,
                    (token, backoff) -> client,
                    token -> loginValid.get(),
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

    private void start() {
        bot.start(
                token(),
                () -> sessionEvents.add("connection lost"),
                () -> sessionEvents.add("login expired"));
        when(client.getState()).thenReturn(WebsocketConnectionState.CONNECTED);
        when(client.isChannelJoined("streamer")).thenReturn(true);
        when(client.sendMessage(any(), any())).thenReturn(true);
        runQueuedTasks();
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
        assertTrue(bot.sendChatMessage("hello"));
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
    void connectionThatGivesUpIsReportedOnce() {
        AtomicReference<IBackoffStrategy> backoff = new AtomicReference<>();
        Bot giving =
                new Bot(
                        executor,
                        null,
                        (token, strategy) -> {
                            backoff.set(strategy);
                            return client;
                        },
                        token -> Optional.of(true),
                        (sender, message) -> messages.add(message),
                        errors::add);
        giving.start(token(), () -> sessionEvents.add("connection lost"), () -> {});
        IBackoffStrategy strategy = backoff.get();
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
        runQueuedTasks();
        assertEquals(List.of("connection lost"), sessionEvents);
        giving.close();
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
                        (sender, message) -> messages.add(message),
                        errors::add);
        building.set(failing);
        assertThrows(IllegalStateException.class, () -> failing.start(token(), () -> {}, () -> {}));
        assertTrue(executor.isShutdown());
    }

    @Test
    void buildFailureWithoutCloseLeavesClosingToTheSession() {
        Bot failing =
                new Bot(
                        executor,
                        null,
                        (token, backoff) -> {
                            throw new IllegalStateException("offline");
                        },
                        token -> Optional.of(true),
                        (sender, message) -> messages.add(message),
                        errors::add);
        assertThrows(IllegalStateException.class, () -> failing.start(token(), () -> {}, () -> {}));
        assertFalse(executor.isShutdown());
        failing.close();
        assertTrue(executor.isShutdown());
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
    void closingStopsExecutorAndIgnoresLateMessages() {
        start();
        bot.close();
        bot.close();
        verify(client).close();
        assertTrue(executor.isShutdown());
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
    void closeReleasesMonitorBeforeWaitingForTransportCallbacks() {
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
        assertTrue(messages.isEmpty());
        assertTrue(executor.isShutdown());
    }

    @Test
    void transportCloseFailureStillStopsTheExecutor() {
        start();
        doThrow(new IllegalStateException("close failed")).when(client).close();
        assertThrows(IllegalStateException.class, bot::close);
        assertTrue(executor.isShutdown());
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
                        (sender, message) -> messages.add(message),
                        errors::add);
        controlled.start(token, () -> {}, () -> {});
        verify(client).joinChannel("streamer");
        controlled.close();
    }

    @Test
    void controllerCloseFailureStillStopsTheExecutor() {
        DeviceFlowController controller = mock(DeviceFlowController.class);
        Bot controlled =
                new Bot(
                        executor,
                        controller,
                        (token, backoff) -> client,
                        token -> Optional.of(true),
                        (sender, message) -> messages.add(message),
                        errors::add);
        doThrow(new IllegalStateException("controller close failed")).when(controller).close();
        assertThrows(IllegalStateException.class, controlled::close);
        assertTrue(executor.isShutdown());
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
