# About
SimpleTwitchChat is a mod that connects your Twitch and Minecraft chats.
Your Twitch chat messages will appear in your Minecraft chat,
and your Minecraft chat messages will appear in your Twitch chat
(when using `/tc <message>` or `/tc` to toggle between
sending messages in Minecraft chat or Twitch chat by default)

Upon joining a world for the first time, you will be prompted to
authorise the SimpleTwitchChat bot/STC Application. This is required
to send and view messages in your Twitch chat. Once a message has
been sent in your Minecraft chat confirming the bot has been authorised,
you will be able to view and send Twitch chat messages from Minecraft!

SimpleTwitchChat is available for both Fabric and NeoForge on Minecraft 26.2; it is client-only on both loaders.

## Migrating from the Fabric-only release

Install exactly one SimpleTwitchChat JAR matching your loader—Fabric or NeoForge—and remove the old JAR before switching loaders. The mod remains client-only and keeps the `simpletwitchchat` mod ID. Install the matching loader and its required loader API/dependencies; do not install the Fabric and NeoForge JARs together. Root `./gradlew build` emits both production artifacts in `build/libs`.

# Commands
`/tc` - Switches between sending your chat messages in Minecraft chat
versus Twitch chat by default.

`/tc <message>` - Sends a message in your Twitch chat.