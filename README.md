# SimpleTwitchChat

SimpleTwitchChat is a mod that connects your Twitch and Minecraft chats.

SimpleTwitchChat is available for both Fabric and NeoForge on Minecraft 26.2; it is client-only on both loaders.

Each time you launch Minecraft, authorise the SimpleTwitchChat bot when prompted in chat. This is required to send and view messages in your Twitch chat. Once a message has been sent in your Minecraft chat confirming the bot has been authorised, you will be able to view and send Twitch chat messages from Minecraft!

## Features

- Your Twitch chat messages will appear in your Minecraft chat.
- Your Minecraft chat messages will appear in your Twitch chat when using `/tc <message>` or `/tc` to toggle between sending messages in Minecraft chat or Twitch chat by default.

## Getting started

- Minecraft 26.2 and Java 25.
- Fabric Loader 0.19.3 or newer with Fabric API, or NeoForge 26.2.0.88 or newer.
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
