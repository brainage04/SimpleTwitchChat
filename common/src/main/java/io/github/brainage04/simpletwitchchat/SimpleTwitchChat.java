package io.github.brainage04.simpletwitchchat;

import io.github.brainage04.simpletwitchchat.twitch.InstalledChatbot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SimpleTwitchChat {
	public static final String MOD_ID = "simpletwitchchat";
	public static final String MOD_NAME = "SimpleTwitchChat";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private SimpleTwitchChat() {
	}

	public static void initialize() {
		LOGGER.info("{} initializing...", MOD_NAME);
		InstalledChatbot.initialize();
		LOGGER.info("{} initialized.", MOD_NAME);
	}
}
