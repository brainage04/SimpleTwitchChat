package io.github.brainage04.simpletwitchchat.mixin;

import io.github.brainage04.simpletwitchchat.SimpleTwitchChatState;
import io.github.brainage04.simpletwitchchat.twitch.InstalledChatbot;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPlayNetworkHandler {
    @Shadow @Final private ClientCommandSource commandSource;

    @Inject(at = @At("HEAD"), method = "sendChatMessage", cancellable = true)
    private void sendChatMessage(String content, CallbackInfo ci) {
        if (SimpleTwitchChatState.twitchChatToggled) {
            InstalledChatbot.getBot().sendChatMessage((FabricClientCommandSource) commandSource, content);
            ci.cancel();
        }
    }
}
