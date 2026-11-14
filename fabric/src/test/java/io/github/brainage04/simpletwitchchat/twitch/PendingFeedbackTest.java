package io.github.brainage04.simpletwitchchat.twitch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PendingFeedbackTest {
    private final PendingFeedback<List<String>> feedback = new PendingFeedback<>();
    private final List<String> chat = new ArrayList<>();

    @Test
    void feedbackWithoutAPlayerIsShownInOrderOnTheNextJoin() {
        feedback.send(null, player -> player.add("authorisation expired"));
        feedback.send(null, player -> player.add("could not contact Twitch"));
        assertTrue(chat.isEmpty());
        feedback.flush(chat);
        feedback.flush(chat);
        assertEquals(List.of("authorisation expired", "could not contact Twitch"), chat);
    }

    @Test
    void feedbackWithAPlayerIsShownAfterAnyThatWaited() {
        feedback.send(null, player -> player.add("waiting"));
        feedback.send(chat, player -> player.add("now"));
        assertEquals(List.of("waiting", "now"), chat);
    }
}
