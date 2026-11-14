package io.github.brainage04.simpletwitchchat.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FabricModCommandsTest {
    private final CommandDispatcher<FabricClientCommandSource> dispatcher =
            new CommandDispatcher<>();

    @BeforeAll
    static void subscribe() {
        FabricModCommands.initialize();
    }

    @BeforeEach
    void buildCommandTree() {
        ClientCommandRegistrationCallback.EVENT.invoker().register(dispatcher, null);
    }

    @Test
    void registersTwitchChatUnderItsOwnNameAndAShortAlias() {
        CommandNode<FabricClientCommandSource> twitchchat =
                dispatcher.getRoot().getChild("twitchchat");
        assertNotNull(twitchchat, "/twitchchat must be registered");
        CommandNode<FabricClientCommandSource> alias = dispatcher.getRoot().getChild("twc");
        assertNotNull(alias, "/twc must be registered");
        assertSame(twitchchat, alias.getRedirect(), "/twc must redirect into /twitchchat");
        assertEquals(
                List.of("message"),
                twitchchat.getChildren().stream().map(CommandNode::getName).toList());
    }

    @Test
    void noLongerHidesAServersOwnTcCommand() {
        assertNull(dispatcher.getRoot().getChild("tc"));
    }
}
