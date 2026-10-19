# Test review

## Frozen rubric

- Keep: exercises production behavior with an observable failure mode. A fake may stand in for Twitch or the scheduler, but must not implement the behavior being asserted.
- Redundant: another named test covers the same production path, inputs and failure mode at the same boundary. A unit test and a registered-command client test are different boundaries.
- Tautological: asserts a copied implementation, a mock's own answer, a constant or registration alone without observable behavior.

This rubric was frozen for the fleet pruning step. No tests were removed.

## Classification

All existing tests are kept:

- `TwitchChatCommandTest.toggleSwitchesBetweenMinecraftAndTwitchChat`: reversible production toggle state.
- Client scenes `chat.twitch-default` and `chat.minecraft-default`: both registered-command transitions in a real client connection; not redundant with a direct helper call.
- `ChatbotSessionTest.authorizationPublishesPromptThenConnectsAndClearsExpiredLink`: device-request completion and link lifetime.
- `ChatbotSessionTest.regenerationClosesOldRequestAndIgnoresItsLateResponse`: replacement ownership and stale callbacks.
- `ChatbotSessionTest.shutdownBeforeRequestRunsDoesNotContactTwitch`: shutdown before scheduled work starts.
- `ChatbotSessionTest.shutdownIgnoresLateAuthorizationAndIsIdempotent`: shutdown after work starts, before completion.
- `ChatbotSessionTest.oauthFailureClearsLinkClosesResourcesAndAllowsRetry`: denied device flow and recovery.
- `ChatbotSessionTest.requestConnectionFailureDoesNotEscapeOrLeaveAnActiveBot`: HTTP request failure and safe sending afterward.
- `ChatbotSessionTest.chatConnectionFailureIsReportedInsteadOfAuthorizationSuccess`: chat startup failure after OAuth succeeds.
- `BotTest.joinsOnAuthorizationBeforeFirstSendAndReceivesMessages`: receiving does not depend on sending first.
- `BotTest.sendsAndEchoesOneMessageWithoutRejoining`: outgoing delivery and one local echo.
- `BotTest.disconnectedMessagesAreRejectedAndNotEchoed`: disconnection does not look like success.
- `BotTest.unauthorizedMessagesAreRejected`: sending before authorization.
- `BotTest.sendFailureIsNotEchoedAsSuccess`: transport failure after a successful channel join.
- `BotTest.closingStopsExecutorAndIgnoresLateMessages`: transport cleanup and late incoming messages.

## Coverage gaps and additions

The pruning inventory had no assertion on actual command feedback, unauthorised explicit sends or the default-chat redirect mixin. The client test now reads the real chat history, checks both toggle messages, checks `/tc <message>` without authorisation and checks that default-Twitch chat reports an error without leaking the message to the Minecraft server.

The incoming-message test now invokes the actual Twitch event callback registered by the bot. A new keep test, `completionBeforeRequestReturnsDoesNotPublishAnExpiredLink`, covers an immediate OAuth response, duplicate callbacks and repeated initialization.

Still outside automation: the real Twitch device page and token exchange, Twitch's IRC reconnect timing, and NeoForge client startup and orderly shutdown. The shared lifecycle state machine is unit-tested, but the NeoForge hook is compiled rather than exercised by a NeoForge client suite. There are no gamerules, configuration screens or custom rendering in this mod.
