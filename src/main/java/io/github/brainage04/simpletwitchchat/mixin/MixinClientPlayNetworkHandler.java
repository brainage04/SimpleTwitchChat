package io.github.brainage04.simpletwitchchat.mixin;

import io.github.brainage04.simpletwitchchat.SimpleTwitchChatState;
import io.github.brainage04.simpletwitchchat.twitch.InstalledChatbot;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPlayNetworkHandler {
    @Inject(at = @At("HEAD"), method = "sendChat", cancellable = true)
    private void sendChatMessage(String content, CallbackInfo ci) {
        if (SimpleTwitchChatState.twitchChatToggled) {
            InstalledChatbot.getBot().sendChatMessage(content);
            ci.cancel();
        }
    }
}
