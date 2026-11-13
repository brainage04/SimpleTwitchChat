# SimpleTwitchChat

SimpleTwitchChat is a mod that connects your Twitch and Minecraft chats.

SimpleTwitchChat is available for both Fabric and NeoForge on Minecraft 26.3; it is client-only on both loaders.

Each time you launch Minecraft, authorise the SimpleTwitchChat bot when prompted in chat; without that authorisation you cannot send or view messages in your Twitch chat. Once a message in your Minecraft chat confirms the bot is authorised, you can view and send Twitch chat messages from Minecraft. While the game runs, the mod renews that authorisation on its own, without asking for your username or password.

## Features

- Your Twitch chat messages appear in your Minecraft chat.
- Your Minecraft chat messages appear in your Twitch chat when you use `/tc <message>`. `/tc` on its own switches the default between sending in Minecraft chat and sending in Twitch chat.
- Messages sent while Twitch chat is disconnected or your Twitch login has expired are rejected. When Twitch refuses a message, its reason appears in your Minecraft chat. When your login expires, the mod renews it with the authorisation it already holds. A new authorisation link appears only when Twitch refuses that renewal; while Twitch cannot be reached, the mod tries again at its next hourly check.

## Getting started

- Minecraft 26.3 and Java 25.
- Fabric Loader 0.19.5 or newer with Fabric API, or NeoForge 26.3.0.99 or newer.
- Install the matching JAR on the client only.

## Commands

- `/tc` - Switches between sending your chat messages in Minecraft chat versus Twitch chat by default.
- `/tc <message>` - Sends a message in your Twitch chat.
- `/regenerateauthurl` - Cancels the current authorisation and connection, then requests a new authorisation link.

## Configuration

No configuration yet.

## Compatibility

No known incompatibilities.

## Documentation

- [Development](docs/DEVELOPMENT.md) - build and test commands.
- [Releases](docs/RELEASE.md) - release procedure.
- [Modrinth publishing](docs/MODRINTH.md) - publishing configuration.
