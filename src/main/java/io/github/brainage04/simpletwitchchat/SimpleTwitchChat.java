package io.github.brainage04.simpletwitchchat;

import io.github.brainage04.simpletwitchchat.command.core.ModCommands;
import io.github.brainage04.simpletwitchchat.event.ModWorldEvents;
import io.github.brainage04.simpletwitchchat.twitch.InstalledChatbot;
import net.fabricmc.api.ClientModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleTwitchChat implements ClientModInitializer {
	public static final String MOD_ID = "simpletwitchchat";
	public static final String MOD_NAME = "SimpleTwitchChat";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("{} initializing...", MOD_NAME);

		ModCommands.initialize();
		ModWorldEvents.initialize();
		InstalledChatbot.intitialize();

		LOGGER.info("{} initialized.", MOD_NAME);
	}
}