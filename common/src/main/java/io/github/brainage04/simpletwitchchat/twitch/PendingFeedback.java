package io.github.brainage04.simpletwitchchat.twitch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Feedback for the player that waits for the next world join when no player exists yet. */
final class PendingFeedback<P> {
    private final List<Consumer<P>> pending = new ArrayList<>();

    void send(P player, Consumer<P> message) {
        if (player == null) {
            pending.add(message);
            return;
        }
        flush(player);
        message.accept(player);
    }

    void flush(P player) {
        List<Consumer<P>> waiting = List.copyOf(pending);
        pending.clear();
        waiting.forEach(message -> message.accept(player));
    }
}
