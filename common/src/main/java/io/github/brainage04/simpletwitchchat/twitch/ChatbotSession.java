package io.github.brainage04.simpletwitchchat.twitch;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;

import java.net.URI;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns one device request and its chat connection for the lifetime of the client. */
final class ChatbotSession {
    private final Supplier<Bot> bots;
    private final Executor authorizationExecutor;
    private final Runnable prompt;
    private final Runnable authorized;
    private final Consumer<String> errors;
    private Bot bot;
    private URI activationUri;
    private boolean completed;

    ChatbotSession(Supplier<Bot> bots, Executor authorizationExecutor, Runnable prompt,
            Runnable authorized, Consumer<String> errors) {
        this.bots = bots;
        this.authorizationExecutor = authorizationExecutor;
        this.prompt = prompt;
        this.authorized = authorized;
        this.errors = errors;
    }

    synchronized URI getActivationUri() {
        return activationUri;
    }

    synchronized void initialize() {
        if (bot == null) {
            regenerate();
        }
    }

    synchronized void regenerate() {
        close();
        Bot requestBot = bots.get();
        bot = requestBot;
        authorizationExecutor.execute(() -> requestAuthorization(requestBot));
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
                if (bot == requestBot && !completed) {
                    activationUri = uri;
                    prompt.run();
                }
            }
        } catch (RuntimeException exception) {
            fail(requestBot, "Could not contact Twitch. Use /regenerateauthurl to try again.");
        }

    }

    private synchronized void complete(Bot requestBot, OAuth2Credential token) {
        if (bot != requestBot || completed) {
            return;
        }
        completed = true;
        activationUri = null;
        try {
            requestBot.start(token);
            authorized.run();
        } catch (RuntimeException exception) {
            fail(requestBot, "Could not connect to Twitch chat. Use /regenerateauthurl to try again.");
        }
    }

    private synchronized void fail(Bot requestBot, String message) {
        if (bot == requestBot) {
            close();
            errors.accept(message);
        }
    }

    synchronized boolean sendChatMessage(String message) {
        if (bot == null) {
            errors.accept("Twitch chat is not authorised. Use /regenerateauthurl to try again.");
            return false;
        }
        return bot.sendChatMessage(message);
    }

    synchronized void close() {
        Bot previous = bot;
        bot = null;
        activationUri = null;
        completed = false;
        if (previous != null) {
            previous.close();
        }
    }
}
