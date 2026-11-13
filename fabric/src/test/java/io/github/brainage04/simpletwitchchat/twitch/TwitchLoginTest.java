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
        Optional<OAuth2Credential> renewed = login.renew(refreshable);
        assertTrue(renewed.isPresent());
        assertEquals("new-access", renewed.get().getAccessToken());
        assertEquals("new-refresh", renewed.get().getRefreshToken());
        assertEquals(Integer.valueOf(5011271), renewed.get().getExpiresIn());
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
    @ValueSource(ints = {400, 401, 403, 429, 500})
    void rejectedRefreshDoesNotRenew(int answer) {
        tokenStatus.set(answer);
        tokenAnswer.set("{\"status\":400,\"message\":\"Invalid refresh token\"}");
        assertTrue(login.renew(refreshable).isEmpty());
    }

    @Test
    void answerWithoutAnAccessTokenDoesNotRenew() {
        tokenAnswer.set("{\"status\":400,\"message\":\"Invalid refresh token\"}");
        assertTrue(login.renew(refreshable).isEmpty());
        tokenAnswer.set("{\"refresh_token\":\"new-refresh\"}");
        assertTrue(login.renew(refreshable).isEmpty());
    }

    @Test
    void unreachableTokenEndpointDoesNotRenew() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            closedPort = socket.getLocalPort();
        }
        TwitchLogin unreachable =
                new TwitchLogin("client", "http://127.0.0.1:" + closedPort + "/oauth2");
        assertTrue(unreachable.renew(refreshable).isEmpty());
    }

    @Test
    void loginWithoutARefreshTokenIsNotRenewed() {
        assertTrue(login.renew(credential).isEmpty());
        assertTrue(tokenBodies.isEmpty());
    }

    @Test
    void renewalWritesNoFile(@TempDir Path home) throws Exception {
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            assertTrue(login.renew(refreshable).isPresent());
            tokenStatus.set(401);
            assertTrue(login.renew(refreshable).isEmpty());
        } finally {
            System.setProperty("user.home", previousHome);
        }
        try (Stream<Path> written = Files.list(home)) {
            assertEquals(List.of(), written.toList(), "tokens must stay in memory");
        }
    }
}
