package io.github.brainage04.simpletwitchchat.twitch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.philippheuer.credentialmanager.authcontroller.DeviceFlowController;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.chat.TwitchChat;
import com.github.twitch4j.chat.events.ChatConnectionStateEvent;
import com.github.twitch4j.client.websocket.domain.WebsocketConnectionState;
import com.github.twitch4j.util.IBackoffStrategy;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import org.mockito.ArgumentMatchers;

/**
 * The login checks of a started {@link Bot} over any order of checks, Twitch answers, connection
 * changes and messages, against a model of what the README and the comments in Bot promise:
 *
 * <ul>
 *   <li>A check that Twitch answers with a valid login, or cannot answer, confirms the login; an
 *       invalid one asks for a renewal.
 *   <li>A renewal that lands while connected reconnects the chat with it; one that lands while
 *       connecting waits for that connect to finish, and no check confirms the login meanwhile.
 *   <li>A refused renewal asks for a new login once; an unclear one is retried after five minutes,
 *       with at most one retry waiting.
 *   <li>Every new connection needs a check before messages go out again, and a message goes out
 *       only while connected, joined and confirmed.
 *   <li>A check confirms the login only if no new connection and no renewal happened while it
 *       waited for Twitch.
 * </ul>
 *
 * <p>The connection states and the CONNECTED event follow twitch4j 1.26.0: WebsocketConnection sets
 * CONNECTING, then CONNECTED, and TwitchChat publishes a ChatConnectionStateEvent for each change.
 */
class BotLoginPropertyTest {
    sealed interface Step {}

    /** The hourly check runs. */
    record HourlyCheck() implements Step {}

    /** The retry an unclear renewal scheduled runs, if one waits. */
    record RetryRuns() implements Step {}

    /** twitch4j moves the connection to {@code state}. */
    record ConnectionTo(WebsocketConnectionState state) implements Step {}

    /** From now on Twitch answers the login check and the renewal like this. */
    record TwitchAnswers(Optional<Boolean> valid, Renewal.Outcome renewal) implements Step {}

    /** The player sends a message. */
    record Send() implements Step {}

    /**
     * The account's channel joins or leaves. twitch4j 1.26.0 counts a channel as joined from the
     * moment joinChannel is called (TwitchChat.java:745, 913-914) and drops it only on
     * leaveChannel, or after a ban or failed joins with removeChannelOnJoinFailure
     * (TwitchChat.java:495, 518), which TwitchChatBuilder leaves off (TwitchChatBuilder.java:248).
     */
    record ChannelJoined(boolean joined) implements Step {}

    @Provide
    Arbitrary<List<Step>> steps() {
        Arbitrary<Step> answers =
                Arbitraries.of(Optional.of(true), Optional.of(false), Optional.<Boolean>empty())
                        .flatMap(
                                valid ->
                                        Arbitraries.of(Renewal.Outcome.class)
                                                .map(outcome -> new TwitchAnswers(valid, outcome)));
        Arbitrary<Step> connection =
                Arbitraries.of(
                                WebsocketConnectionState.CONNECTED,
                                WebsocketConnectionState.CONNECTING,
                                WebsocketConnectionState.RECONNECTING,
                                WebsocketConnectionState.LOST)
                        .map(ConnectionTo::new);
        return Arbitraries.frequencyOf(
                        Tuple.of(3, Arbitraries.just(new HourlyCheck())),
                        Tuple.of(2, Arbitraries.just(new RetryRuns())),
                        Tuple.of(3, connection),
                        Tuple.of(3, answers),
                        Tuple.of(3, Arbitraries.just(new Send())),
                        Tuple.of(1, Arbitraries.of(true, false).map(ChannelJoined::new)))
                .list()
                .ofMaxSize(30);
    }

    @Property(tries = 400)
    void theBotFollowsTheLoginModel(
            @ForAll("answersAtStart") TwitchAnswers atStart, @ForAll("steps") List<Step> steps) {
        Harness harness = new Harness(atStart);
        Model model = new Model();
        try {
            harness.start();
            model.connected(harness.answers);
            model.joined = true;
            harness.compare(model, "start");
            for (Step step : steps) {
                switch (step) {
                    case HourlyCheck ignored -> {
                        harness.hourlyCheck();
                        model.check(harness.answers);
                    }
                    case RetryRuns ignored -> {
                        boolean waited = harness.runRetry();
                        assertEquals(model.retryWaiting, waited, "a retry waited");
                        if (waited) {
                            model.retryWaiting = false;
                            model.check(harness.answers);
                        }
                    }
                    case ConnectionTo connection -> {
                        harness.connectionTo(connection.state());
                        model.state = connection.state();
                        if (connection.state() == WebsocketConnectionState.CONNECTED) {
                            model.connected(harness.answers);
                        }
                    }
                    case TwitchAnswers answers -> harness.answers = answers;
                    case Send ignored ->
                            assertEquals(model.canSend(), harness.send(), "message sent");
                    case ChannelJoined channel -> {
                        harness.joined = channel.joined();
                        model.joined = channel.joined();
                    }
                }
                harness.compare(model, step.toString());
            }
        } finally {
            harness.close();
        }
    }

    @Example
    void aRenewalDuringAConnectReconnectsOnceThatConnectFinishes() {
        theBotFollowsTheLoginModel(
                new TwitchAnswers(Optional.of(true), Renewal.Outcome.RENEWED),
                List.of(
                        new ConnectionTo(WebsocketConnectionState.CONNECTING),
                        new TwitchAnswers(Optional.of(false), Renewal.Outcome.RENEWED),
                        new HourlyCheck(),
                        new TwitchAnswers(Optional.of(true), Renewal.Outcome.RENEWED),
                        new HourlyCheck(),
                        new Send(),
                        new ConnectionTo(WebsocketConnectionState.CONNECTED),
                        new Send(),
                        new ConnectionTo(WebsocketConnectionState.CONNECTED),
                        new Send()));
    }

    @Example
    void aRenewalDuringAConnectThatDropsWaitsForTheNextConnection() {
        theBotFollowsTheLoginModel(
                new TwitchAnswers(Optional.of(false), Renewal.Outcome.UNKNOWN),
                List.of(
                        new ConnectionTo(WebsocketConnectionState.CONNECTING),
                        new TwitchAnswers(Optional.of(false), Renewal.Outcome.RENEWED),
                        new RetryRuns(),
                        new ConnectionTo(WebsocketConnectionState.LOST),
                        new HourlyCheck(),
                        new ConnectionTo(WebsocketConnectionState.CONNECTED),
                        new Send(),
                        new HourlyCheck(),
                        new Send()));
    }

    /**
     * The chat's first connect went anonymous, which twitch4j does once a connect has failed more
     * than once and finds the login invalid (TwitchChat.java:543-546). An anonymous connect does
     * not join the account's channel (TwitchChat.java:570-572), so it is joined only when start
     * calls joinChannel, after it has queued the first check, which may run first.
     */
    @Example
    void aRenewalAndASendBeforeTheChannelIsJoined() {
        Harness harness =
                new Harness(new TwitchAnswers(Optional.of(false), Renewal.Outcome.RENEWED));
        Model model = new Model();
        harness.beforeJoin =
                () -> {
                    harness.executor.runQueued();
                    model.connected(harness.answers);
                    harness.compare(model, "the first check");
                    assertEquals(1, model.reconnects, "the renewal reconnects");
                    assertFalse(harness.send(), "a message before the join");
                };
        try {
            harness.start();
            model.joined = true;
            harness.compare(model, "the join");
            assertTrue(harness.send(), "a message after the join");
        } finally {
            harness.close();
        }
    }

    /**
     * The hourly check and the check a reconnect queues run on the bot's two executor threads
     * (Bot.newExecutor) and both find the login expired. The one that loses the race for the
     * renewal returns while the other renews.
     */
    @Example
    void twoChecksThatFindTheLoginExpiredAtOnceRenewItOnce() throws InterruptedException {
        Harness harness =
                new Harness(new TwitchAnswers(Optional.of(true), Renewal.Outcome.RENEWED));
        try {
            harness.start();
            Runnable reconnected = harness.reconnectedCheck();
            harness.answers = new TwitchAnswers(Optional.of(false), Renewal.Outcome.RENEWED);
            CountDownLatch bothChecked = new CountDownLatch(2);
            CountDownLatch oneReturned = new CountDownLatch(1);
            harness.twitchCall =
                    call -> {
                        if (call.equals("check")) {
                            bothChecked.countDown();
                            await(bothChecked);
                        } else {
                            await(oneReturned);
                        }
                    };
            Runnable hourly = harness.executor.periodic.getFirst().task;
            harness.race(
                    () -> {
                        hourly.run();
                        oneReturned.countDown();
                    },
                    () -> {
                        reconnected.run();
                        oneReturned.countDown();
                    });
            assertEquals(3, harness.checks, "login checks, the first at start");
            assertEquals(1, harness.renewals, "renewals");
            assertEquals(1, harness.reconnects, "reconnects");
            assertEquals("fresh1", harness.login.getAccessToken());
            assertTrue(harness.send(), "a message after the renewal");
        } finally {
            harness.close();
        }
    }

    /**
     * The check a reconnect queues asks Twitch about the login, and the hourly check finds it
     * expired and renews it before that answer arrives. The answer is about the old login, so it
     * must not renew the new one: each renewal spends the refresh token and reconnects the chat.
     */
    @Example
    void aCheckAnsweredAfterTheRenewalDoesNotRenewAgain() throws InterruptedException {
        Harness harness =
                new Harness(new TwitchAnswers(Optional.of(true), Renewal.Outcome.RENEWED));
        try {
            harness.start();
            Runnable reconnected = harness.reconnectedCheck();
            harness.answers = new TwitchAnswers(Optional.of(false), Renewal.Outcome.RENEWED);
            CountDownLatch reconnectedAsked = new CountDownLatch(1);
            CountDownLatch hourlyDone = new CountDownLatch(1);
            harness.twitchCall =
                    call -> {
                        if (Thread.currentThread().getName().equals("reconnected")) {
                            reconnectedAsked.countDown();
                            await(hourlyDone);
                        } else if (call.equals("renewal")) {
                            await(reconnectedAsked);
                        }
                    };
            Runnable hourly = harness.executor.periodic.getFirst().task;
            harness.race(
                    () -> {
                        hourly.run();
                        hourlyDone.countDown();
                    },
                    () -> {
                        Thread.currentThread().setName("reconnected");
                        reconnected.run();
                    });
            assertEquals(3, harness.checks, "login checks, the first at start");
            assertEquals(1, harness.renewals, "renewals");
            assertEquals(1, harness.reconnects, "reconnects");
            assertEquals("fresh1", harness.login.getAccessToken());
            assertTrue(harness.send(), "a message after the renewal");
        } finally {
            harness.close();
        }
    }

    /**
     * A check asks Twitch about the login while another check renews it during a connect, and that
     * connect finishes before the answer arrives. The connect may have gone anonymous, so the late
     * answer must not confirm the login before the reconnect the renewal waits for. The other check
     * runs inside this check's call to Twitch, which orders them as the bot's two executor threads
     * could.
     */
    @Example
    void aCheckAnsweredAfterARenewalDuringAConnectDoesNotConfirmIt() {
        Harness harness =
                new Harness(new TwitchAnswers(Optional.of(true), Renewal.Outcome.RENEWED));
        try {
            harness.start();
            harness.connectionTo(WebsocketConnectionState.CONNECTING);
            harness.twitchCall =
                    call -> {
                        harness.twitchCall = ignored -> {};
                        harness.answers =
                                new TwitchAnswers(Optional.of(false), Renewal.Outcome.RENEWED);
                        harness.bot.checkLogin();
                        harness.answers =
                                new TwitchAnswers(Optional.of(true), Renewal.Outcome.RENEWED);
                        harness.announce(WebsocketConnectionState.CONNECTED);
                    };
            harness.executor.periodic.getFirst().task.run();
            assertEquals("fresh1", harness.login.getAccessToken());
            assertFalse(harness.send(), "a message before the reconnect");
            harness.executor.runQueued();
            assertEquals(1, harness.reconnects, "reconnects");
            harness.connectionTo(WebsocketConnectionState.CONNECTED);
            assertTrue(harness.send(), "a message after the reconnect");
        } finally {
            harness.close();
        }
    }

    /**
     * twitch4j reconnects while a check waits for Twitch's answer, and the login expires meanwhile.
     * That reconnect may have gone anonymous (TwitchChat.java:543-546), so only the check its
     * CONNECTED event queues may confirm the login for it.
     */
    @Example
    void aCheckThatStartedBeforeAReconnectDoesNotConfirmIt() {
        Harness harness =
                new Harness(new TwitchAnswers(Optional.of(true), Renewal.Outcome.RENEWED));
        try {
            harness.start();
            List<Runnable> reconnected = new ArrayList<>();
            harness.twitchCall =
                    call -> {
                        harness.twitchCall = ignored -> {};
                        reconnected.add(harness.reconnectedCheck());
                    };
            harness.executor.periodic.getFirst().task.run();
            assertFalse(harness.send(), "a message before the reconnect is checked");
            harness.answers = new TwitchAnswers(Optional.of(false), Renewal.Outcome.RENEWED);
            reconnected.getFirst().run();
            assertEquals(1, harness.renewals, "renewals");
            assertEquals(1, harness.reconnects, "reconnects");
            harness.answers = new TwitchAnswers(Optional.of(true), Renewal.Outcome.RENEWED);
            harness.connectionTo(WebsocketConnectionState.CONNECTED);
            assertTrue(harness.send(), "a message after the renewed login connected");
        } finally {
            harness.close();
        }
    }

    @Example
    void aCheckAnsweredAfterARefusedRenewalDoesNotConfirmIt() {
        aCheckAnsweredAfterAFailedRenewalDoesNotConfirmIt(Renewal.Outcome.REFUSED);
    }

    @Example
    void aCheckAnsweredAfterAnUnclearRenewalDoesNotConfirmIt() {
        aCheckAnsweredAfterAFailedRenewalDoesNotConfirmIt(Renewal.Outcome.UNKNOWN);
    }

    /**
     * A check asks Twitch about the login just before it expires, and another check finds it
     * expired and cannot renew it before that answer arrives. Both answers are about the same
     * token, so the late one must not confirm the login the renewal gave up on.
     */
    private void aCheckAnsweredAfterAFailedRenewalDoesNotConfirmIt(Renewal.Outcome outcome) {
        Harness harness = new Harness(new TwitchAnswers(Optional.of(true), outcome));
        try {
            harness.start();
            harness.twitchCall =
                    call -> {
                        harness.twitchCall = ignored -> {};
                        harness.answers = new TwitchAnswers(Optional.of(false), outcome);
                        harness.bot.checkLogin();
                        harness.answers = new TwitchAnswers(Optional.of(true), outcome);
                    };
            harness.executor.periodic.getFirst().task.run();
            assertEquals(1, harness.renewals, "renewals");
            assertFalse(harness.send(), "a message after the failed renewal");
            if (outcome == Renewal.Outcome.REFUSED) {
                assertEquals(1, harness.newLoginRequests, "new logins");
            } else {
                assertTrue(harness.runRetry(), "a retry waited");
                assertTrue(harness.send(), "a message after the retry confirmed the login");
            }
        } finally {
            harness.close();
        }
    }

    static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "the other thread did not get there");
        } catch (InterruptedException exception) {
            throw new AssertionError(exception);
        }
    }

    @Provide
    Arbitrary<TwitchAnswers> answersAtStart() {
        return Arbitraries.of(Optional.of(true), Optional.of(false), Optional.<Boolean>empty())
                .flatMap(
                        valid ->
                                Arbitraries.of(Renewal.Outcome.class)
                                        .map(outcome -> new TwitchAnswers(valid, outcome)));
    }

    /** What the bot should have done so far. */
    private static final class Model {
        WebsocketConnectionState state = WebsocketConnectionState.CONNECTED;
        boolean joined;
        boolean confirmed;
        boolean renewalWaitsForConnect;
        boolean retryWaiting;
        int checks;
        int renewals;
        int reconnects;
        int newLoginRequests;
        int renewed;

        /** A CONNECTED event: the bot forgets its confirmation and checks again. */
        void connected(TwitchAnswers answers) {
            confirmed = false;
            check(answers);
        }

        void check(TwitchAnswers answers) {
            if (renewalWaitsForConnect) {
                if (state == WebsocketConnectionState.CONNECTED) {
                    renewalWaitsForConnect = false;
                    reconnects++;
                }
                return;
            }
            checks++;
            if (answers.valid().orElse(true)) {
                confirmed = true;
                return;
            }
            renewals++;
            switch (answers.renewal()) {
                case RENEWED -> {
                    renewed++;
                    renewalWaitsForConnect = state == WebsocketConnectionState.CONNECTING;
                    confirmed = !renewalWaitsForConnect;
                    if (state == WebsocketConnectionState.CONNECTED) reconnects++;
                }
                case REFUSED -> {
                    confirmed = false;
                    if (newLoginRequests == 0) newLoginRequests++;
                }
                case UNKNOWN -> {
                    confirmed = false;
                    retryWaiting = true;
                }
            }
        }

        boolean canSend() {
            return state == WebsocketConnectionState.CONNECTED && joined && confirmed;
        }
    }

    /** A started bot whose executor runs nothing until the test says so. */
    private static final class Harness {
        final ManualExecutor executor = new ManualExecutor();
        final TwitchChat client = mock(TwitchChat.class, RETURNS_DEEP_STUBS);
        final Map<Class<?>, Consumer<Object>> listeners = new HashMap<>();
        final List<String> errors = new ArrayList<>();
        final List<String> echoes = new ArrayList<>();
        final OAuth2Credential login = login("first", "r0");
        IBackoffStrategy backoff;
        TwitchAnswers answers;
        WebsocketConnectionState state = WebsocketConnectionState.CONNECTED;
        volatile boolean joined;
        Runnable beforeJoin = () -> {};
        volatile Consumer<String> twitchCall = call -> {};
        int checks;
        int renewals;
        int reconnects;
        int newLoginRequests;
        int renewedLogins;
        final Bot bot;

        Harness(TwitchAnswers atStart) {
            answers = atStart;
            bot =
                    new Bot(
                            executor,
                            mock(DeviceFlowController.class),
                            (token, strategy) -> {
                                backoff = strategy;
                                return client;
                            },
                            token -> {
                                twitchCall.accept("check");
                                synchronized (this) {
                                    checks++;
                                    return answers.valid();
                                }
                            },
                            token -> {
                                twitchCall.accept("renewal");
                                synchronized (this) {
                                    renewals++;
                                    return switch (answers.renewal()) {
                                        case RENEWED -> {
                                            renewedLogins++;
                                            yield Renewal.renewed(
                                                    login(
                                                            "fresh" + renewedLogins,
                                                            "r" + renewedLogins));
                                        }
                                        case REFUSED -> Renewal.REFUSED;
                                        case UNKNOWN -> Renewal.UNKNOWN;
                                    };
                                }
                            },
                            (sender, message) -> echoes.add(sender + ": " + message),
                            errors::add);
            var events = client.getEventManager();
            doAnswer(
                            invocation -> {
                                listeners.put(invocation.getArgument(0), invocation.getArgument(1));
                                return null;
                            })
                    .when(events)
                    .onEvent(
                            ArgumentMatchers.<Class<Object>>any(),
                            ArgumentMatchers.<Consumer<Object>>any());
            when(client.getState()).thenAnswer(invocation -> state);
            when(client.isChannelJoined("streamer")).thenAnswer(invocation -> joined);
            doAnswer(
                            invocation -> {
                                beforeJoin.run();
                                joined = true;
                                return null;
                            })
                    .when(client)
                    .joinChannel("streamer");
            when(client.sendMessage(any(), any())).thenReturn(true);
            doAnswer(
                            invocation -> {
                                synchronized (this) {
                                    reconnects++;
                                }
                                return null;
                            })
                    .when(client)
                    .reconnect();
        }

        void start() {
            bot.start(login, () -> {}, () -> newLoginRequests++);
            executor.runQueued();
            verify(client).joinChannel("streamer");
            // Once connected the chat must keep reconnecting through any outage.
            for (int attempt = 0; attempt <= 2 * Bot.CONNECTION_RETRIES; attempt++) {
                assertTrue(backoff.get() >= 0, "reconnect " + attempt);
            }
            backoff.reset();
        }

        void hourlyCheck() {
            assertEquals(1, executor.periodic.size(), "one hourly check");
            ManualExecutor.Task hourly = executor.periodic.getFirst();
            assertEquals(Bot.LOGIN_CHECK_PERIOD.toMinutes(), hourly.delayMinutes);
            assertEquals(Bot.LOGIN_CHECK_PERIOD.toMinutes(), hourly.periodMinutes);
            hourly.task.run();
            executor.runQueued();
        }

        boolean runRetry() {
            List<ManualExecutor.Task> waiting = executor.waitingRetries();
            if (waiting.isEmpty()) return false;
            assertEquals(1, waiting.size(), "one retry waits");
            ManualExecutor.Task retry = waiting.getFirst();
            assertEquals(Duration.ofMinutes(5).toMinutes(), retry.delayMinutes);
            retry.ran = true;
            retry.task.run();
            executor.runQueued();
            return true;
        }

        void connectionTo(WebsocketConnectionState next) {
            announce(next);
            executor.runQueued();
        }

        private void announce(WebsocketConnectionState next) {
            WebsocketConnectionState previous = state;
            state = next;
            listeners
                    .get(ChatConnectionStateEvent.class)
                    .accept(new ChatConnectionStateEvent(previous, next, client));
        }

        /** twitch4j reconnects; returns the check its CONNECTED event queued, not yet run. */
        Runnable reconnectedCheck() {
            announce(WebsocketConnectionState.RECONNECTING);
            announce(WebsocketConnectionState.CONNECTED);
            assertEquals(1, executor.queued.size(), "checks queued by the reconnect");
            return executor.queued.poll();
        }

        /** Runs both tasks on threads of their own at once and waits for them. */
        void race(Runnable first, Runnable second) throws InterruptedException {
            List<Throwable> failures = new ArrayList<>();
            List<Thread> threads = new ArrayList<>();
            for (Runnable task : List.of(first, second)) {
                Thread thread = new Thread(task);
                thread.setUncaughtExceptionHandler(
                        (failed, failure) -> {
                            synchronized (failures) {
                                failures.add(failure);
                            }
                        });
                threads.add(thread);
            }
            threads.forEach(Thread::start);
            for (Thread thread : threads) {
                thread.join(Duration.ofSeconds(20));
                assertFalse(thread.isAlive(), "a racing check is stuck");
            }
            if (!failures.isEmpty()) {
                throw new AssertionError(failures.getFirst());
            }
            assertTrue(executor.queued.isEmpty(), "tasks queued by the race");
        }

        boolean send() {
            int echoed = echoes.size();
            int failed = errors.size();
            boolean sent = bot.sendChatMessage("hello");
            if (sent) {
                assertEquals(List.of("streamer: hello"), echoes.subList(echoed, echoes.size()));
                assertEquals(failed, errors.size());
            } else {
                assertEquals(echoed, echoes.size());
                assertEquals(List.of(Bot.NOT_CONNECTED), errors.subList(failed, errors.size()));
            }
            return sent;
        }

        void compare(Model model, String after) {
            assertEquals(model.checks, checks, "login checks after " + after);
            assertEquals(model.renewals, renewals, "renewals after " + after);
            assertEquals(model.reconnects, reconnects, "reconnects after " + after);
            assertEquals(model.newLoginRequests, newLoginRequests, "new logins after " + after);
            assertEquals(
                    model.renewed == 0 ? "first" : "fresh" + model.renewed,
                    login.getAccessToken(),
                    "access token after " + after);
            assertEquals(
                    model.retryWaiting ? 1 : 0,
                    executor.waitingRetries().size(),
                    "retries waiting after " + after);
        }

        void close() {
            bot.close();
        }

        static OAuth2Credential login(String accessToken, String refreshToken) {
            return new OAuth2Credential(
                    "twitch", accessToken, refreshToken, null, "streamer", 3600, List.of());
        }
    }

    /** Keeps every task the bot hands it, so the test decides when each runs. */
    private static final class ManualExecutor extends ScheduledThreadPoolExecutor {
        final Deque<Runnable> queued = new ArrayDeque<>();
        final List<Task> periodic = new ArrayList<>();
        final List<Task> delayed = new ArrayList<>();

        ManualExecutor() {
            super(1);
        }

        @Override
        public void execute(Runnable command) {
            queued.add(command);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            Task task = new Task(command, unit.toMinutes(delay), 0);
            delayed.add(task);
            return task;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(
                Runnable command, long initialDelay, long period, TimeUnit unit) {
            Task task = new Task(command, unit.toMinutes(initialDelay), unit.toMinutes(period));
            periodic.add(task);
            return task;
        }

        void runQueued() {
            while (!queued.isEmpty()) queued.poll().run();
        }

        List<Task> waitingRetries() {
            return delayed.stream().filter(task -> !task.ran).toList();
        }

        static final class Task implements ScheduledFuture<Object> {
            final Runnable task;
            final long delayMinutes;
            final long periodMinutes;
            boolean ran;

            Task(Runnable task, long delayMinutes, long periodMinutes) {
                this.task = task;
                this.delayMinutes = delayMinutes;
                this.periodMinutes = periodMinutes;
            }

            @Override
            public long getDelay(TimeUnit unit) {
                return unit.convert(delayMinutes, TimeUnit.MINUTES);
            }

            @Override
            public int compareTo(Delayed other) {
                return Long.compare(getDelay(TimeUnit.MINUTES), other.getDelay(TimeUnit.MINUTES));
            }

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return ran;
            }

            @Override
            public Object get() {
                return null;
            }

            @Override
            public Object get(long timeout, TimeUnit unit) {
                return null;
            }
        }
    }
}
