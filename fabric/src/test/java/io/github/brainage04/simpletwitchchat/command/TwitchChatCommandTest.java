package io.github.brainage04.simpletwitchchat.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brainage04.simpletwitchchat.SimpleTwitchChatState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TwitchChatCommandTest {
    @AfterEach
    void resetState() {
        SimpleTwitchChatState.twitchChatToggled = false;
    }

    @Test
    void toggleSwitchesBetweenMinecraftAndTwitchChat() {
        assertFalse(SimpleTwitchChatState.twitchChatToggled);
        assertTrue(TwitchChatCommand.toggleTwitchChat());
        assertFalse(TwitchChatCommand.toggleTwitchChat());
    }
}
