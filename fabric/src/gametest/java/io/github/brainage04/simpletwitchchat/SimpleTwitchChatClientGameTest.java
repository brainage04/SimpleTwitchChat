package io.github.brainage04.simpletwitchchat;

import io.github.brainage04.fabricmoddingconventions.ClientGameTestRecorder;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestServers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import net.minecraft.client.gui.screens.ChatScreen;

import java.util.Properties;

@SuppressWarnings("UnstableApiUsage")
public final class SimpleTwitchChatClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Properties serverProperties = ClientGameTestServers.flatServerProperties();

        ClientGameTestServers.withDedicatedServer(context, serverProperties, "SimpleTwitchChat command recording GameTest", server -> { try {
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
        } finally {
            context.runOnClient(client -> {
                SimpleTwitchChatState.twitchChatToggled = false;
                client.setScreenAndShow(null);
            });
            ;
        } });
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
        ClientGameTestRecorder.showStep(context, stepId, title, description);
    }
}
