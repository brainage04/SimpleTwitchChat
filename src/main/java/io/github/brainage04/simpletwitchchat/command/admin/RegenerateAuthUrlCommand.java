package io.github.brainage04.simpletwitchchat.command.admin;

import io.github.brainage04.simpletwitchchat.twitch.InstalledChatbot;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

@SuppressWarnings("SameReturnValue")
public class RegenerateAuthUrlCommand {
    public static int execute(FabricClientCommandSource source) {
        source.sendFeedback(Text.literal("Restarting installed chatbot..."));

        // init installed chatbot on new thread
        // this is done because the game will freeze while the function is running if this method is called on the main thread
        new Thread(InstalledChatbot::regenerate).start();

        return 1;
    }
}
