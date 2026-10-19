package io.github.brainage04.simpletwitchchat.twitch;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.ITwitchClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
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
    private final Bot bot = new Bot(executor, token -> client,
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
        start();
        verify(client.getChat()).joinChannel("streamer");
        bot.receiveMessage("viewer", "hello");
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
}
