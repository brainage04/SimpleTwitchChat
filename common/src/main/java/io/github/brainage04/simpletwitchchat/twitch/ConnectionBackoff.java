package io.github.brainage04.simpletwitchchat.twitch;

import com.github.twitch4j.common.util.ExponentialBackoffStrategy;
import com.github.twitch4j.util.IBackoffStrategy;

/**
 * twitch4j's default backoff, which gives up after {@link Bot#CONNECTION_RETRIES} failed tries
 * until the first connection is made and never after it.
 */
final class ConnectionBackoff implements IBackoffStrategy {
    private final IBackoffStrategy delegate = ExponentialBackoffStrategy.builder().build();
    private final Runnable gaveUp;
    private volatile boolean firstConnectionDone;

    ConnectionBackoff(Runnable gaveUp) {
        this.gaveUp = gaveUp;
    }

    void firstConnectionDone() {
        firstConnectionDone = true;
    }

    @Override
    public long get() {
        long delay = delegate.get();
        if (!firstConnectionDone && delegate.getFailures() > Bot.CONNECTION_RETRIES) {
            gaveUp.run();
            return -1;
        }
        return delay;
    }

    @Override
    public void reset() {
        delegate.reset();
    }

    @Override
    public int getFailures() {
        return delegate.getFailures();
    }
}
