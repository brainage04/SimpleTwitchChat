package io.github.brainage04.simpletwitchchat.twitch;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatbotSessionTest {
    private static final URI URI_ONE = URI.create("https://id.twitch.tv/activate?code=one");
    private final Bot first = mock(Bot.class);
    private final Bot second = mock(Bot.class);
    private final ArrayDeque<Bot> bots = new ArrayDeque<>(List.of(first, second));
    private final ArrayDeque<Runnable> jobs = new ArrayDeque<>();
    private final List<String> events = new ArrayList<>();
    private final ChatbotSession session = new ChatbotSession(bots::remove, jobs::add,
            () -> events.add("prompt"), () -> events.add("authorized"), events::add);
    private Consumer<OAuth2Credential> success;
    private Consumer<String> failure;

    private void request() {
        when(first.authorize(any(), any())).thenAnswer(invocation -> {
            success = invocation.getArgument(0);
            failure = invocation.getArgument(1);
            return URI_ONE;
        });
        session.initialize();
        jobs.remove().run();
    }

    @Test
    void authorizationPublishesPromptThenConnectsAndClearsExpiredLink() {
        request();
        assertEquals(URI_ONE, session.getActivationUri());
        OAuth2Credential token = mock(OAuth2Credential.class);
        success.accept(token);
        verify(first).start(token);
        assertNull(session.getActivationUri());
        assertEquals(List.of("prompt", "authorized"), events);
    }

    @Test
    void regenerationClosesOldRequestAndIgnoresItsLateResponse() {
        request();
        session.regenerate();
        verify(first).close();
        success.accept(mock(OAuth2Credential.class));
        failure.accept("old failure");
        verify(first, never()).start(any());
        verify(second, never()).close();
        assertNull(session.getActivationUri());
        assertEquals(List.of("prompt"), events);
    }

    @Test
    void shutdownBeforeRequestRunsDoesNotContactTwitch() {
        session.initialize();
        session.close();
        jobs.remove().run();
        verify(first).close();
        verify(first, never()).authorize(any(), any());
        assertNull(session.getActivationUri());
    }

    @Test
    void shutdownIgnoresLateAuthorizationAndIsIdempotent() {
        request();
        session.close();
        session.close();
        success.accept(mock(OAuth2Credential.class));
        verify(first).close();
        verify(first, never()).start(any());
        assertEquals(List.of("prompt"), events);
    }

    @Test
    void oauthFailureClearsLinkClosesResourcesAndAllowsRetry() {
        request();
        failure.accept("denied");
        verify(first).close();
        assertNull(session.getActivationUri());
        assertEquals(List.of("prompt", "denied"), events);
        session.regenerate();
        assertEquals(1, jobs.size());
    }

    @Test
    void requestConnectionFailureDoesNotEscapeOrLeaveAnActiveBot() {
        when(first.authorize(any(), any())).thenThrow(new IllegalStateException("offline"));
        session.initialize();
        jobs.remove().run();
        verify(first).close();
        assertFalse(session.sendChatMessage("hello"));
        assertTrue(events.getFirst().contains("Could not contact Twitch"));
    }

    @Test
    void chatConnectionFailureIsReportedInsteadOfAuthorizationSuccess() {
        request();
        doThrow(new IllegalStateException("offline")).when(first).start(any());
        success.accept(mock(OAuth2Credential.class));
        verify(first).close();
        assertFalse(events.contains("authorized"));
        assertTrue(events.getLast().contains("Could not connect to Twitch chat"));
    }
    @Test
    void completionBeforeRequestReturnsDoesNotPublishAnExpiredLink() {
        OAuth2Credential token = mock(OAuth2Credential.class);
        when(first.authorize(any(), any())).thenAnswer(invocation -> {
            Consumer<OAuth2Credential> callback = invocation.getArgument(0);
            callback.accept(token);
            callback.accept(token);
            return URI_ONE;
        });
        session.initialize();
        session.initialize();
        assertEquals(1, jobs.size());
        jobs.remove().run();
        verify(first).start(token);
        assertNull(session.getActivationUri());
        assertEquals(List.of("authorized"), events);
    }
}
