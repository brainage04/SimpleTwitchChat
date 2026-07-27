package io.github.brainage04.simpletwitchchat.fabric;

import io.github.brainage04.simpletwitchchat.SimpleTwitchChat;
import net.fabricmc.api.ClientModInitializer;

public final class SimpleTwitchChatFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SimpleTwitchChat.initialize();
		FabricModCommands.initialize();
		FabricWorldEvents.initialize();
	}
}
