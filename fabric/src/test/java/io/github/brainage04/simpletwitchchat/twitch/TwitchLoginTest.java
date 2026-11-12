package io.github.brainage04.simpletwitchchat.twitch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TwitchLoginTest {
    private final AtomicInteger status = new AtomicInteger();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final OAuth2Credential credential = new OAuth2Credential("twitch", "token");
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
}
