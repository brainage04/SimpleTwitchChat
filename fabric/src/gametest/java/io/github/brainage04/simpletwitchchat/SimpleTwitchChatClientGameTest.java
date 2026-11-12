package io.github.brainage04.simpletwitchchat;

import io.github.brainage04.minecraftmodconventions.ClientGameTestCommands;
import io.github.brainage04.minecraftmodconventions.ClientGameTestServers;
import io.github.brainage04.simpletwitchchat.twitch.InstalledChatbot;
import java.util.Properties;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

@SuppressWarnings("UnstableApiUsage")
public final class SimpleTwitchChatClientGameTest implements FabricClientGameTest {
    private static final String NOT_AUTHORISED = "Twitch chat is not authorised.";
    private static final String LEAK_PROBE = "must-not-reach-minecraft-chat";

    @Override
    public void runTest(ClientGameTestContext context) {
        InstalledChatbot.close();
        Properties serverProperties = ClientGameTestServers.flatServerProperties();

        ClientGameTestServers.withDedicatedServer(
                context,
                serverProperties,
                "SimpleTwitchChat command recording GameTest",
                server -> {
                    try {
                        ClientGameTestServers.assertClientWorldAndPlayerAvailable(context);
                        context.runOnClient(
                                client -> SimpleTwitchChatState.twitchChatToggled = false);
                        context.waitTicks(20);

                        toggleDefaultChat(context, true);
                        SimpleTwitchChatFilm.showDefaultChat(context, true);
                        toggleDefaultChat(context, false);
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

    private static void toggleDefaultChat(
            ClientGameTestContext context, boolean expectedTwitchDefault) {
        ClientGameTestCommands.runCommand(context, "tc");
        context.runOnClient(
                client -> {
                    if (SimpleTwitchChatState.twitchChatToggled != expectedTwitchDefault) {
                        throw new AssertionError(
                                "The registered /tc command did not select the expected default chat.");
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
        ClientGameTestCommands.runCommand(context, "tc explicit-test-message");
        ClientGameTestCommands.awaitChat(context, NOT_AUTHORISED);

        ClientGameTestCommands.clearChat(context);
        ClientGameTestCommands.runCommand(context, "tc");
        context.runOnClient(client -> client.player.connection.sendChat(LEAK_PROBE));
        context.waitTicks(10);
        ClientGameTestCommands.awaitChat(context, NOT_AUTHORISED);
        if (ClientGameTestCommands.chatLines(context).stream()
                .anyMatch(line -> line.contains(LEAK_PROBE))) {
            throw new AssertionError("Twitch-default chat leaked to the Minecraft server.");
        }
    }
}
