package io.github.brainage04.simpletwitchchat.twitch;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class ChatbotSession {
    static final String CONNECTION_FAILED =
            "Could not connect to Twitch chat. Use /regenerateauthurl to try again.";
    static final String LOGIN_EXPIRED =
            "Your Twitch login has expired. Open the new link below to log in again.";

    private final Supplier<Bot> bots;
    private final Runnable prompt;
    private final Runnable authorized;
    private final Consumer<String> errors;
    private Bot bot;
    private URI activationUri;
    private boolean completed;

    ChatbotSession(
            Supplier<Bot> bots, Runnable prompt, Runnable authorized, Consumer<String> errors) {
        this.bots = bots;
        this.prompt = prompt;
        this.authorized = authorized;
        this.errors = errors;
    }

    synchronized URI getActivationUri() {
        return activationUri;
    }

    void initialize() {
        replace(current -> current == null, null);
    }

    void regenerate() {
        replace(current -> true, null);
    }

    private void expire(Bot requestBot) {
        replace(current -> current == requestBot, LOGIN_EXPIRED);
    }

    private void replace(Predicate<Bot> replaces, String notice) {
        Bot previous;
        Bot requestBot;
        synchronized (this) {
            if (!replaces.test(bot)) {
                return;
            }
            previous = bot;
            requestBot = bots.get();
            bot = requestBot;
            activationUri = null;
            completed = false;
        }
        closeBot(previous);
        if (notice != null) {
            errors.accept(notice);
        }
        try {
            requestBot.executeAuthorization(() -> requestAuthorization(requestBot));
        } catch (RuntimeException exception) {
            fail(
                    requestBot,
                    "Could not start Twitch authorisation. Use /regenerateauthurl to try again.");
        }
    }

    private void requestAuthorization(Bot requestBot) {
        synchronized (this) {
            if (bot != requestBot) {
                return;
            }
        }
        try {
            URI uri =
                    requestBot.authorize(
                            token -> complete(requestBot, token),
                            message -> fail(requestBot, message));
            synchronized (this) {
                // A response can finish before the initial HTTP request returns.
                if (bot != requestBot || completed) {
                    return;
                }
                activationUri = uri;
            }
            prompt.run();
        } catch (RuntimeException exception) {
            fail(requestBot, "Could not contact Twitch. Use /regenerateauthurl to try again.");
        }
    }

    private void complete(Bot requestBot, OAuth2Credential token) {
        synchronized (this) {
            if (bot != requestBot || completed) {
                return;
            }
            completed = true;
            activationUri = null;
        }
        try {
            requestBot.start(
                    token, () -> fail(requestBot, CONNECTION_FAILED), () -> expire(requestBot));
            synchronized (this) {
                if (bot == requestBot) {
                    authorized.run();
                }
            }
        } catch (RuntimeException exception) {
            fail(requestBot, CONNECTION_FAILED);
        }
    }

    private void fail(Bot requestBot, String message) {
        synchronized (this) {
            if (bot != requestBot) {
                return;
            }
            bot = null;
            activationUri = null;
            completed = false;
        }
        closeBot(requestBot);
        errors.accept(message);
    }

    boolean sendChatMessage(String message) {
        Bot current;
        synchronized (this) {
            current = bot;
        }
        if (current == null) {
            errors.accept("Twitch chat is not authorised. Use /regenerateauthurl to try again.");
            return false;
        }
        return current.sendChatMessage(message);
    }

    void close() {
        Bot previous;
        synchronized (this) {
            previous = bot;
            bot = null;
            activationUri = null;
            completed = false;
        }
        closeBot(previous);
    }

    private static void closeBot(Bot previous) {
        if (previous != null) {
            previous.close();
        }
    }
}
