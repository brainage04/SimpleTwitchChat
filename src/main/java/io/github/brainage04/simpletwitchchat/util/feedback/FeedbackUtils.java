package io.github.brainage04.simpletwitchchat.util.feedback;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class FeedbackUtils {
    public static void sendMessage(FabricClientCommandSource source, String message, MessageType messageType) {
        sendMessage(source, Component.literal(message), messageType);
    }

    public static void sendMessage(FabricClientCommandSource source, MutableComponent message, MessageType messageType) {
        sendMessage(source.getPlayer(), message, messageType);
    }

    public static void sendMessage(LocalPlayer player, String message, MessageType messageType) {
        sendMessage(player, Component.literal(message), messageType);
    }

    public static void sendMessage(LocalPlayer player, MutableComponent message, MessageType messageType) {
        switch (messageType) {
            case INFO -> message = message.withStyle(ChatFormatting.YELLOW);
            case ERROR -> message = message.withStyle(ChatFormatting.RED);
            case SUCCESS -> message = message.withStyle(ChatFormatting.GREEN);
        }

        player.sendSystemMessage(message);
        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1, 1);
    }
}
