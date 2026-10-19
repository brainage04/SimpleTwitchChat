package io.github.brainage04.simpletwitchchat.twitch;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.philippheuer.credentialmanager.authcontroller.DeviceFlowController;
import com.github.philippheuer.credentialmanager.domain.DeviceAuthorization;
import com.github.philippheuer.credentialmanager.domain.DeviceTokenResponse;
import com.github.twitch4j.ITwitchClient;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BotTest {
    private final ITwitchClient client = mock(ITwitchClient.class, RETURNS_DEEP_STUBS);
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private final List<String> messages = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final Bot bot = new Bot(executor, null, token -> client,
            (sender, message) -> messages.add(sender + ": " + message), errors::add);

    private void start() {
        OAuth2Credential token = mock(OAuth2Credential.class);
        when(token.getUserName()).thenReturn("streamer");
        bot.start(token);
    }

    @AfterEach
    void close() {
        bot.close();
    }

    @Test
    void joinsOnAuthorizationBeforeFirstSendAndReceivesMessages() {
        AtomicReference<Consumer<ChannelMessageEvent>> listener = new AtomicReference<>();
        doAnswer(invocation -> {
            listener.set(invocation.getArgument(1));
            return null;
        }).when(client.getEventManager()).onEvent(eq(ChannelMessageEvent.class), any());
        start();
        verify(client.getChat()).joinChannel("streamer");
        ChannelMessageEvent event = mock(ChannelMessageEvent.class, RETURNS_DEEP_STUBS);
        when(event.getUser().getName()).thenReturn("viewer");
        when(event.getMessage()).thenReturn("hello");
        listener.get().accept(event);
        assertEquals(List.of("viewer: hello"), messages);
    }

    @Test
    void sendsAndEchoesOneMessageWithoutRejoining() {
        start();
        when(client.getChat().isChannelJoined("streamer")).thenReturn(true);
        assertTrue(bot.sendChatMessage("hello"));
        verify(client.getChat()).sendMessage("streamer", "hello");
        verify(client.getChat()).joinChannel("streamer");
        assertEquals(List.of("streamer: hello"), messages);
        assertTrue(errors.isEmpty());
    }

    @Test
    void disconnectedMessagesAreRejectedAndNotEchoed() {
        start();
        assertFalse(bot.sendChatMessage("lost"));
        verify(client.getChat(), never()).sendMessage("streamer", "lost");
        assertTrue(messages.isEmpty());
        assertTrue(errors.getFirst().contains("not connected"));
    }

    @Test
    void unauthorizedMessagesAreRejected() {
        assertFalse(bot.sendChatMessage("lost"));
        assertTrue(errors.getFirst().contains("not authorised"));
        verifyNoInteractions(client);
    }

    @Test
    void sendFailureIsNotEchoedAsSuccess() {
        start();
        when(client.getChat().isChannelJoined("streamer")).thenReturn(true);
        doThrow(new IllegalStateException("offline")).when(client.getChat())
                .sendMessage("streamer", "lost");
        assertFalse(bot.sendChatMessage("lost"));
        assertTrue(messages.isEmpty());
        assertTrue(errors.getFirst().contains("Could not send"));
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
                .thenAnswer(invocation -> {
                    response.set(invocation.getArgument(2));
                    return request;
                });
        Bot controlled = new Bot(executor, controller, token -> client,
                (sender, message) -> messages.add(message), errors::add);
        assertEquals(URI.create(request.getCompleteUri()),
                controlled.authorize(token -> messages.add("authorized"), errors::add));
        controlled.close();
        response.get().accept(null);
        assertTrue(messages.isEmpty());
        assertTrue(errors.isEmpty());
    }

    @Test
    void closeReleasesMonitorBeforeWaitingForTransportCallbacks() {
        start();
        doAnswer(invocation -> {
            FutureTask<Void> callback = new FutureTask<>(() -> {
                bot.receiveMessage("viewer", "late");
                return null;
            });
            Thread.ofPlatform().daemon(true).start(callback);
            callback.get(2, TimeUnit.SECONDS);
            return null;
        }).when(client).close();
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
        Bot controlled = new Bot(executor, null, credential -> {
            when(credential.getUserName()).thenReturn("streamer");
            return client;
        }, (sender, message) -> messages.add(message), errors::add);
        controlled.start(token);
        verify(client.getChat()).joinChannel("streamer");
        controlled.close();
    }

    @Test
    void controllerCloseFailureStillStopsTheExecutor() {
        DeviceFlowController controller = mock(DeviceFlowController.class);
        Bot controlled = new Bot(executor, controller, token -> client,
                (sender, message) -> messages.add(message), errors::add);
        doThrow(new IllegalStateException("controller close failed")).when(controller).close();
        assertThrows(IllegalStateException.class, controlled::close);
        assertTrue(executor.isShutdown());
    }
}
