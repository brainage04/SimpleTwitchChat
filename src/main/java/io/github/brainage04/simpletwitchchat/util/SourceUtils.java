package io.github.brainage04.simpletwitchchat.util;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;

public class SourceUtils {
    public static FabricClientCommandSource getSource(ClientPlayerEntity player) {
        return (FabricClientCommandSource) player.networkHandler.getCommandSource();
    }
}
