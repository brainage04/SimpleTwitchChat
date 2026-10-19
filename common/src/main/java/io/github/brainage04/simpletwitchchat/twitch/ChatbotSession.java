package io.github.brainage04.simpletwitchchat.twitch;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;

import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns one device request and its chat connection for the lifetime of the client. */
final class ChatbotSession {
    private final Supplier<Bot> bots;
    private final Runnable prompt;
    private final Runnable authorized;
    private final Consumer<String> errors;
    private Bot bot;
    private URI activationUri;
    private boolean completed;

    ChatbotSession(Supplier<Bot> bots, Runnable prompt, Runnable authorized,
            Consumer<String> errors) {
        this.bots = bots;
        this.prompt = prompt;
        this.authorized = authorized;
        this.errors = errors;
    }

    synchronized URI getActivationUri() {
        return activationUri;
    }

    void initialize() {
        replace(true);
    }

    void regenerate() {
        replace(false);
    }

    private void replace(boolean onlyIfAbsent) {
        Bot previous;
        Bot requestBot;
        synchronized (this) {
            if (onlyIfAbsent && bot != null) {
                return;
            }
            previous = bot;
            requestBot = bots.get();
            bot = requestBot;
            activationUri = null;
            completed = false;
        }
        closeBot(previous);
        try {
            requestBot.executeAuthorization(() -> requestAuthorization(requestBot));
        } catch (RuntimeException exception) {
            fail(requestBot, "Could not start Twitch authorisation. Use /regenerateauthurl to try again.");
        }
    }

    private void requestAuthorization(Bot requestBot) {
        synchronized (this) {
            if (bot != requestBot) {
                return;
            }
        }
        try {
            URI uri = requestBot.authorize(token -> complete(requestBot, token),
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
            requestBot.start(token);
            synchronized (this) {
                if (bot == requestBot) {
                    authorized.run();
                }
            }
        } catch (RuntimeException exception) {
            fail(requestBot, "Could not connect to Twitch chat. Use /regenerateauthurl to try again.");
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

    private void closeBot(Bot previous) {
        if (previous != null) {
            try {
                previous.close();
            } catch (RuntimeException exception) {
                errors.accept("Could not close Twitch chat cleanly.");
            }
        }
    }
}
