package io.github.brainage04.simpletwitchchat.twitch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TwitchLoginTest {
    private static final String RENEWED =
            "{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\","
                    + "\"expires_in\":5011271,\"scope\":[\"chat:read\"],\"token_type\":\"bearer\"}";
    private final AtomicInteger status = new AtomicInteger();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final AtomicInteger tokenStatus = new AtomicInteger(200);
    private final AtomicReference<String> tokenAnswer = new AtomicReference<>(RENEWED);
    private final List<String> tokenQueries = new CopyOnWriteArrayList<>();
    private final List<String> tokenBodies = new CopyOnWriteArrayList<>();
    private final OAuth2Credential credential = new OAuth2Credential("twitch", "token");
    private final OAuth2Credential refreshable =
            new OAuth2Credential(
                    "twitch", "expired", "refresh-one", null, "streamer", 3600, List.of());
    private HttpServer server;
    private TwitchLogin login;

    @BeforeEach
    void serveValidation() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext(
                "/oauth2/validate",
                exchange -> {
                    authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
                    exchange.sendResponseHeaders(status.get(), -1);
                    exchange.close();
                });
        server.createContext(
                "/oauth2/token",
                exchange -> {
                    tokenQueries.add(exchange.getRequestURI().getQuery());
                    tokenBodies.add(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    byte[] answer = tokenAnswer.get().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(tokenStatus.get(), answer.length);
                    exchange.getResponseBody().write(answer);
                    exchange.close();
                });
        server.start();
        login =
                new TwitchLogin(
                        "client", "http://127.0.0.1:" + server.getAddress().getPort() + "/oauth2");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void acceptedTokenIsValid() {
        status.set(200);
        assertEquals(Optional.of(true), login.isCredentialValid(credential));
        assertEquals(List.of("OAuth token"), authorizations);
    }

    @Test
    void rejectedTokenHasExpired() {
        status.set(401);
        assertEquals(Optional.of(false), login.isCredentialValid(credential));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 429, 500, 503})
    void otherAnswersMeanTheLoginCouldNotBeChecked(int answer) {
        status.set(answer);
        assertEquals(Optional.empty(), login.isCredentialValid(credential));
    }

    @Test
    void unreachableTwitchMeansTheLoginCouldNotBeChecked() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            closedPort = socket.getLocalPort();
        }
        TwitchLogin unreachable =
                new TwitchLogin("client", "http://127.0.0.1:" + closedPort + "/oauth2");
        assertEquals(Optional.empty(), unreachable.isCredentialValid(credential));
    }

    @Test
    void renewalReturnsTheNewAccessAndRefreshTokens() {
        Renewal renewal = login.renew(refreshable);
        assertEquals(Renewal.Outcome.RENEWED, renewal.outcome());
        assertEquals("new-access", renewal.credential().getAccessToken());
        assertEquals("new-refresh", renewal.credential().getRefreshToken());
        assertEquals(Integer.valueOf(5011271), renewal.credential().getExpiresIn());
    }

    @Test
    void renewalPostsThePublicClientRequestInTheBody() {
        login.renew(refreshable);
        String body = tokenBodies.getFirst();
        assertTrue(body.contains("grant_type=refresh_token"), body);
        assertTrue(body.contains("refresh_token=refresh-one"), body);
        assertTrue(body.contains("client_id=client"), body);
        assertFalse(
                body.contains("client_secret"), "a public client sends no client secret: " + body);
        assertNull(tokenQueries.getFirst(), "the refresh token must not travel in the URL");
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401})
    void refusedRefreshTokenNeedsANewLogin(int answer) {
        tokenStatus.set(answer);
        tokenAnswer.set("{\"status\":400,\"message\":\"Invalid refresh token\"}");
        assertEquals(Renewal.Outcome.REFUSED, login.renew(refreshable).outcome());
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 429, 500, 503})
    void refreshAnswerThatSaysNothingAboutTheTokenIsUnknown(int answer) {
        tokenStatus.set(answer);
        tokenAnswer.set("{\"status\":429,\"message\":\"Too Many Requests\"}");
        assertEquals(Renewal.Outcome.UNKNOWN, login.renew(refreshable).outcome());
    }

    @Test
    void answerWithoutAnAccessTokenIsUnknown() {
        tokenAnswer.set("{\"status\":500,\"message\":\"Internal Server Error\"}");
        assertEquals(Renewal.Outcome.UNKNOWN, login.renew(refreshable).outcome());
        tokenAnswer.set("{\"refresh_token\":\"new-refresh\"}");
        assertEquals(Renewal.Outcome.UNKNOWN, login.renew(refreshable).outcome());
    }

    @Test
    void unreachableTokenEndpointIsUnknown() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            closedPort = socket.getLocalPort();
        }
        TwitchLogin unreachable =
                new TwitchLogin("client", "http://127.0.0.1:" + closedPort + "/oauth2");
        assertEquals(Renewal.Outcome.UNKNOWN, unreachable.renew(refreshable).outcome());
    }

    @Test
    void loginWithoutARefreshTokenCannotBeRenewed() {
        assertEquals(Renewal.Outcome.REFUSED, login.renew(credential).outcome());
        assertTrue(tokenBodies.isEmpty());
    }

    @Test
    void renewalWritesNoFile(@TempDir Path home) throws Exception {
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            assertEquals(Renewal.Outcome.RENEWED, login.renew(refreshable).outcome());
            tokenStatus.set(401);
            assertEquals(Renewal.Outcome.REFUSED, login.renew(refreshable).outcome());
        } finally {
            System.setProperty("user.home", previousHome);
        }
        try (Stream<Path> written = Files.list(home)) {
            assertEquals(List.of(), written.toList(), "tokens must stay in memory");
        }
    }
}
