package io.github.brainage04.simpletwitchchat.twitch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatbotSessionTest {
    private static final URI URI_ONE = URI.create("https://id.twitch.tv/activate?code=one");
    private final Bot first = mock(Bot.class);
    private final Bot second = mock(Bot.class);
    private final ArrayDeque<Bot> bots = new ArrayDeque<>(List.of(first, second));
    private final ArrayDeque<Runnable> jobs = new ArrayDeque<>();
    private final List<String> events = new ArrayList<>();
    private final ChatbotSession session =
            new ChatbotSession(
                    bots::remove,
                    () -> events.add("prompt"),
                    () -> events.add("authorized"),
                    events::add);
    private Consumer<OAuth2Credential> success;
    private Consumer<String> failure;
    private Runnable connectionLost;
    private Runnable loginExpired;

    @BeforeEach
    void scheduleRequests() {
        for (Bot bot : List.of(first, second)) {
            doAnswer(
                            invocation -> {
                                jobs.add(invocation.getArgument(0));
                                return null;
                            })
                    .when(bot)
                    .executeAuthorization(any());
            doAnswer(
                            invocation -> {
                                connectionLost = invocation.getArgument(1);
                                loginExpired = invocation.getArgument(2);
                                return null;
                            })
                    .when(bot)
                    .start(any(), any(), any());
        }
    }

    private void request() {
        when(first.authorize(any(), any()))
                .thenAnswer(
                        invocation -> {
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
        verify(first).start(eq(token), any(), any());
        assertNull(session.getActivationUri());
        assertEquals(List.of("prompt", "authorized"), events);
    }

    private void connect() {
        request();
        success.accept(mock(OAuth2Credential.class));
        events.clear();
    }

    @Test
    void sendingWhileConnectedGoesThroughTheBot() {
        connect();
        when(first.sendChatMessage("hello")).thenReturn(true);
        when(first.sendChatMessage("rejected")).thenReturn(false);
        assertTrue(session.sendChatMessage("hello"));
        assertFalse(session.sendChatMessage("rejected"));
        assertTrue(events.isEmpty());
    }

    @Test
    void expiredLoginIsReportedOnceAndRequestsANewLink() {
        connect();
        loginExpired.run();
        verify(first).close();
        assertEquals(List.of(ChatbotSession.LOGIN_EXPIRED), events);
        assertEquals(1, jobs.size());
        loginExpired.run();
        assertEquals(1, jobs.size());
        assertEquals(List.of(ChatbotSession.LOGIN_EXPIRED), events);
        when(second.authorize(any(), any())).thenReturn(URI_ONE);
        jobs.remove().run();
        assertEquals(URI_ONE, session.getActivationUri());
        assertEquals(List.of(ChatbotSession.LOGIN_EXPIRED, "prompt"), events);
    }

    @Test
    void lostConnectionIsReportedAndClosesTheBot() {
        connect();
        connectionLost.run();
        connectionLost.run();
        verify(first).close();
        assertEquals(List.of(ChatbotSession.CONNECTION_FAILED), events);
        assertFalse(session.sendChatMessage("hello"));
        verify(first, never()).sendChatMessage(any());
    }

    @Test
    void connectionThatGivesUpWhileStartingIsNotReportedAsAuthorised() {
        request();
        doAnswer(
                        invocation -> {
                            Runnable lost = invocation.getArgument(1);
                            lost.run();
                            return null;
                        })
                .when(first)
                .start(any(), any(), any());
        success.accept(mock(OAuth2Credential.class));
        verify(first).close();
        assertEquals(List.of("prompt", ChatbotSession.CONNECTION_FAILED), events);
    }

    @Test
    void regenerationClosesOldRequestAndIgnoresItsLateResponse() {
        request();
        session.regenerate();
        verify(first).close();
        success.accept(mock(OAuth2Credential.class));
        failure.accept("old failure");
        verify(first, never()).start(any(), any(), any());
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
        verify(first, never()).start(any(), any(), any());
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
        doThrow(new IllegalStateException("offline")).when(first).start(any(), any(), any());
        success.accept(mock(OAuth2Credential.class));
        verify(first).close();
        assertFalse(events.contains("authorized"));
        assertTrue(events.getLast().contains("Could not connect to Twitch chat"));
    }

    @Test
    void completionBeforeRequestReturnsDoesNotPublishAnExpiredLink() {
        OAuth2Credential token = mock(OAuth2Credential.class);
        when(first.authorize(any(), any()))
                .thenAnswer(
                        invocation -> {
                            Consumer<OAuth2Credential> callback = invocation.getArgument(0);
                            callback.accept(token);
                            callback.accept(token);
                            return URI_ONE;
                        });
        session.initialize();
        session.initialize();
        assertEquals(1, jobs.size());
        jobs.remove().run();
        verify(first).start(eq(token), any(), any());
        assertNull(session.getActivationUri());
        assertEquals(List.of("authorized"), events);
    }

    @Test
    void shutdownDoesNotHoldSessionMonitorWhileTransportCloses() {
        request();
        doAnswer(
                        invocation -> {
                            FutureTask<Void> callback =
                                    new FutureTask<>(
                                            () -> {
                                                success.accept(mock(OAuth2Credential.class));
                                                return null;
                                            });
                            Thread.ofPlatform().daemon(true).start(callback);
                            callback.get(2, TimeUnit.SECONDS);
                            return null;
                        })
                .when(first)
                .close();
        session.close();
        verify(first, never()).start(any(), any(), any());
        assertEquals(List.of("prompt"), events);
    }

    @Test
    void closeFailureDoesNotPreventReplacementAuthorization() {
        request();
        doThrow(new IllegalStateException("close failed")).when(first).close();
        session.regenerate();
        assertEquals(1, jobs.size());
        assertTrue(events.getLast().contains("Could not close Twitch chat"));
    }

    @Test
    void sendingBeforeAuthorizationReportsTheMissingLogin() {
        assertFalse(session.sendChatMessage("hello"));
        assertEquals(
                List.of("Twitch chat is not authorised. Use /regenerateauthurl to try again."),
                events);
    }

    @Test
    void authorizationThatCannotStartIsReportedClosedAndRetryable() {
        doThrow(new IllegalStateException("executor stopped"))
                .when(first)
                .executeAuthorization(any());
        session.initialize();
        verify(first).close();
        assertEquals(
                List.of(
                        "Could not start Twitch authorisation. Use /regenerateauthurl to try again."),
                events);
        session.initialize();
        assertEquals(1, jobs.size());
    }
}
