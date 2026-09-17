package io.github.brainage04.simpletwitchchat.fabric;

import io.github.brainage04.simpletwitchchat.SimpleTwitchChat;
import io.github.brainage04.simpletwitchchat.twitch.InstalledChatbot;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class SimpleTwitchChatFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SimpleTwitchChat.initialize();
		ClientLifecycleEvents.CLIENT_STOPPING.register(ignored -> InstalledChatbot.close());
		FabricModCommands.initialize();
		FabricWorldEvents.initialize();
	}
}
