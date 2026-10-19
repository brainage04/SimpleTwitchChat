package io.github.brainage04.simpletwitchchat;

import io.github.brainage04.fabricmoddingconventions.ClientGameTestRecorder;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestServers;
import io.github.brainage04.simpletwitchchat.gametest.mixin.ChatComponentAccessor;
import io.github.brainage04.simpletwitchchat.twitch.InstalledChatbot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import net.minecraft.client.gui.screens.ChatScreen;

import java.util.Properties;

@SuppressWarnings("UnstableApiUsage")
public final class SimpleTwitchChatClientGameTest implements FabricClientGameTest {
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
                        context.runOnClient(client -> SimpleTwitchChatState.twitchChatToggled = false);
                        context.waitTicks(20);

                        ClientGameTestRecorder.startRecording(context);
                        toggleAndShowFeedback(
                                context,
                                "chat.twitch-default",
                                "Twitch is the default chat",
                                "The /tc command confirms that new chat messages use Twitch by default.",
                                true
                        );
                        context.waitTicks(50);

                        toggleAndShowFeedback(
                                context,
                                "chat.minecraft-default",
                                "Minecraft is the default chat",
                                "The same /tc command confirms that new chat messages return to Minecraft by default.",
                                false
                        );
                        context.waitTicks(50);

                        assertUnauthorizedDelivery(context);
                    } finally {
                        InstalledChatbot.close();
                        context.runOnClient(client -> {
                            SimpleTwitchChatState.twitchChatToggled = false;
                            client.setScreenAndShow(null);
                        });
                    }
                }
        );
    }

    private static void toggleAndShowFeedback(
            ClientGameTestContext context,
            String stepId,
            String title,
            String description,
            boolean expectedTwitchDefault
    ) {
        context.runOnClient(client -> {
            client.player.connection.sendCommand("tc");
            if (SimpleTwitchChatState.twitchChatToggled != expectedTwitchDefault) {
                throw new AssertionError("The registered /tc command did not select the expected default chat.");
            }
            client.setScreenAndShow(new ChatScreen("", false));
        });
        context.waitTicks(2);
        assertChatContains(context, "Chat messages will now be sent through "
                + (expectedTwitchDefault ? "Twitch" : "Minecraft") + " chat by default.");
        ClientGameTestRecorder.showStep(context, stepId, title, description);
    }

    private static void assertUnauthorizedDelivery(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.gui.hud.getChat().clearMessages(false);
            client.player.connection.sendCommand("tc explicit-test-message");
        });
        context.waitTicks(2);
        assertChatContains(context, "Twitch chat is not authorised.");

        context.runOnClient(client -> {
            client.gui.hud.getChat().clearMessages(false);
            client.player.connection.sendCommand("tc");
            client.player.connection.sendChat("must-not-reach-minecraft-chat");
        });
        context.waitTicks(10);
        assertChatContains(context, "Twitch chat is not authorised.");
        context.runOnClient(client -> {
            if (((ChatComponentAccessor) client.gui.hud.getChat()).simpletwitchchat$getMessages()
                    .stream().anyMatch(message ->
                            message.content().getString().contains("must-not-reach-minecraft-chat"))) {
                throw new AssertionError("Twitch-default chat leaked to the Minecraft server.");
            }
        });
    }

    private static void assertChatContains(ClientGameTestContext context, String expected) {
        context.runOnClient(client -> {
            if (((ChatComponentAccessor) client.gui.hud.getChat()).simpletwitchchat$getMessages()
                    .stream().noneMatch(message -> message.content().getString().contains(expected))) {
                throw new AssertionError("Missing chat feedback: " + expected);
            }
        });
    }
}
