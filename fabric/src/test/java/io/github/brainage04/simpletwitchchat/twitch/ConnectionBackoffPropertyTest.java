package io.github.brainage04.simpletwitchchat.twitch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.constraints.IntRange;

/**
 * {@link ConnectionBackoff} against twitch4j 1.26.0's ExponentialBackoffStrategy with its defaults
 * (immediate first try with up to 5 s of jitter, then 1 s doubling to at most 2 minutes, jitter
 * doubling at most, no retry limit) and against the way WebsocketConnection uses it: a failed
 * connect or a lost connection asks {@code get()} for the delay and gives up and resets on a
 * negative one, and 30 s of connection reset it.
 */
class ConnectionBackoffPropertyTest {
    enum Op {
        GET,
        RESET,
        FIRST_CONNECTION_DONE
    }

    @Provide
    Arbitrary<List<Op>> ops() {
        return Arbitraries.frequencyOf(
                        Tuple.of(8, Arbitraries.just(Op.GET)),
                        Tuple.of(1, Arbitraries.just(Op.RESET)),
                        Tuple.of(1, Arbitraries.just(Op.FIRST_CONNECTION_DONE)))
                .list()
                .ofMaxSize(60);
    }

    @Property
    void givesUpOnlyBeforeTheFirstConnectionAndOnlyAfterTheRetriesAreSpent(
            @ForAll("ops") List<Op> ops) {
        AtomicInteger gaveUp = new AtomicInteger();
        ConnectionBackoff backoff = new ConnectionBackoff(gaveUp::incrementAndGet);
        int failures = 0;
        int expectedGaveUp = 0;
        boolean connected = false;
        for (Op op : ops) {
            switch (op) {
                case GET -> {
                    int before = failures++;
                    long delay = backoff.get();
                    if (!connected && failures > Bot.CONNECTION_RETRIES) {
                        expectedGaveUp++;
                        assertEquals(-1, delay, "try " + failures);
                    } else {
                        assertTrue(
                                delay >= 0 && delay <= maximumDelay(before),
                                delay + " ms after " + before + " failures");
                    }
                }
                case RESET -> {
                    backoff.reset();
                    failures = 0;
                }
                case FIRST_CONNECTION_DONE -> {
                    backoff.firstConnectionDone();
                    connected = true;
                }
            }
            assertEquals(failures, backoff.getFailures());
            assertEquals(expectedGaveUp, gaveUp.get());
        }
    }

    /** The longest delay ExponentialBackoffStrategy.get returns after {@code failures} failures. */
    private static long maximumDelay(int failures) {
        if (failures == 0) return 5_000;
        double delay = Math.min(Math.pow(2, failures - 1) * 1_000, 120_000);
        return Math.round(delay * 2);
    }

    @Property
    void aChatThatNeverConnectsGivesUpOnceAfterItsRetries(
            @ForAll @IntRange(min = 1, max = 3) int outages) {
        AtomicInteger gaveUp = new AtomicInteger();
        ConnectionBackoff backoff = new ConnectionBackoff(gaveUp::incrementAndGet);
        for (int outage = 1; outage <= outages; outage++) {
            // WebsocketConnection.connect: each failed try asks for the delay, and a negative one
            // resets the strategy and ends the tries.
            int tries = 0;
            while (backoff.get() >= 0) tries++;
            backoff.reset();
            assertEquals(Bot.CONNECTION_RETRIES, tries);
            assertEquals(outage, gaveUp.get());
        }
    }

    @Property
    void aChatThatConnectedOnceKeepsReconnecting(@ForAll @IntRange(min = 0, max = 200) int tries) {
        AtomicInteger gaveUp = new AtomicInteger();
        ConnectionBackoff backoff = new ConnectionBackoff(gaveUp::incrementAndGet);
        backoff.firstConnectionDone();
        for (int attempt = 0; attempt < tries; attempt++) {
            assertTrue(backoff.get() >= 0, "try " + attempt);
        }
        assertEquals(0, gaveUp.get());
    }
}
