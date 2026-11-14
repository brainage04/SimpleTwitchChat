package io.github.brainage04.simpletwitchchat;

import com.github.twitch4j.chat.TwitchChat;
import com.github.twitch4j.chat.TwitchChatBuilder;
import com.github.twitch4j.common.util.ExponentialBackoffStrategy;
import io.github.brainage04.minecraftmodconventions.ClientGameTestCommands;
import io.github.brainage04.minecraftmodconventions.ClientGameTestServers;
import io.github.brainage04.simpletwitchchat.twitch.InstalledChatbot;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

@SuppressWarnings("UnstableApiUsage")
public final class SimpleTwitchChatClientGameTest implements FabricClientGameTest {
    private static final String NOT_AUTHORISED = "Twitch chat is not authorised.";
    private static final String LEAK_PROBE = "must-not-reach-minecraft-chat";

    @Override
    public void runTest(ClientGameTestContext context) {
        assertBundledChatLibrariesLoad();
        InstalledChatbot.close();
        Properties serverProperties = ClientGameTestServers.flatServerProperties();

        ClientGameTestServers.withDedicatedServer(
                context,
                serverProperties,
                "SimpleTwitchChat command recording GameTest",
                // Stands in for a login error while the player is still in the menus.
                server ->
                        context.runOnClient(
                                client -> InstalledChatbot.sendChatMessage("before-join")),
                server -> {
                    try {
                        ClientGameTestServers.assertClientWorldAndPlayerAvailable(context);
                        ClientGameTestCommands.awaitChat(context, NOT_AUTHORISED);
                        context.runOnClient(
                                client -> SimpleTwitchChatState.twitchChatToggled = false);
                        context.waitTicks(20);

                        assertCommandNames(context);
                        toggleDefaultChat(context, true, "twitchchat");
                        SimpleTwitchChatFilm.showDefaultChat(context, true);
                        toggleDefaultChat(context, false, "twc");
                        SimpleTwitchChatFilm.showDefaultChat(context, false);

                        assertUnauthorizedDelivery(context);
                    } finally {
                        InstalledChatbot.close();
                        context.runOnClient(
                                client -> {
                                    SimpleTwitchChatState.twitchChatToggled = false;
                                    client.setScreenAndShow(null);
                                });
                    }
                });
    }

    // The mod builds a chat client only after a Twitch login. This builds an anonymous one
    // against a closed local port without retries, which loads the chat, websocket and
    // rate-limit classes from the bundled twitch4j libraries.
    private static void assertBundledChatLibrariesLoad() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        try {
            TwitchChat chat =
                    TwitchChatBuilder.builder()
                            .withBaseUrl("ws://127.0.0.1:1")
                            .withScheduledThreadPoolExecutor(executor)
                            .withConnectionBackoffStrategy(
                                    ExponentialBackoffStrategy.builder().maxRetries(0).build())
                            .build();
            chat.close();
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertCommandNames(ClientGameTestContext context) {
        context.runOnClient(
                client -> {
                    var root = client.getConnection().getCommands().getRoot();
                    for (String name : List.of("twitchchat", "twc")) {
                        if (root.getChild(name) == null) {
                            throw new AssertionError(
                                    "Expected /" + name + " in the client command tree.");
                        }
                    }
                    if (root.getChild("tc") != null) {
                        throw new AssertionError(
                                "/tc is still registered, so it still hides a server's own /tc"
                                        + " command.");
                    }
                });
    }

    private static void toggleDefaultChat(
            ClientGameTestContext context, boolean expectedTwitchDefault, String command) {
        ClientGameTestCommands.runCommand(context, command);
        context.runOnClient(
                client -> {
                    if (SimpleTwitchChatState.twitchChatToggled != expectedTwitchDefault) {
                        throw new AssertionError(
                                "The registered /"
                                        + command
                                        + " command did not select the expected default chat.");
                    }
                });
        ClientGameTestCommands.awaitChat(
                context,
                "Chat messages will now be sent through "
                        + (expectedTwitchDefault ? "Twitch" : "Minecraft")
                        + " chat by default.");
    }

    private static void assertUnauthorizedDelivery(ClientGameTestContext context) {
        ClientGameTestCommands.clearChat(context);
        ClientGameTestCommands.runCommand(context, "twc explicit-test-message");
        ClientGameTestCommands.awaitChat(context, NOT_AUTHORISED);

        ClientGameTestCommands.clearChat(context);
        ClientGameTestCommands.runCommand(context, "twitchchat");
        context.runOnClient(client -> client.player.connection.sendChat(LEAK_PROBE));
        context.waitTicks(10);
        ClientGameTestCommands.awaitChat(context, NOT_AUTHORISED);
        if (ClientGameTestCommands.chatLines(context).stream()
                .anyMatch(line -> line.contains(LEAK_PROBE))) {
            throw new AssertionError("Twitch-default chat leaked to the Minecraft server.");
        }
    }
}
