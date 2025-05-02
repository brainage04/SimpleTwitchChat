package io.github.brainage04.simpletwitchchat.util.feedback;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class FeedbackUtils {
    public static void sendMessage(FabricClientCommandSource source, String message, MessageType messageType) {
        sendMessage(source, Text.literal(message), messageType);
    }

    public static void sendMessage(FabricClientCommandSource source, MutableText message, MessageType messageType) {
        switch (messageType) {
            case INFO -> message = message.formatted(Formatting.YELLOW);
            case ERROR -> message = message.formatted(Formatting.RED);
            case SUCCESS -> message = message.formatted(Formatting.GREEN);
        }

        source.sendFeedback(message);
        source.getPlayer().playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1, 1);
    }
}
