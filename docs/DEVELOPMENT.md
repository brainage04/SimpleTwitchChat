# Development

The Twitch credentials live only in memory for one Minecraft client session. Restarting Minecraft requires authorisation again; no access or refresh tokens are written to disk. Persistent credentials need an owner decision about storage and lifetime.

`ChatbotSession` owns the current bot and device request. Regenerating closes the old controller and executor before creating another bot. Late responses from an old request cannot connect or change the current authorisation link. Both loaders close the session when the client stops.

The unit tests use Twitch boundary doubles, not a live account. They exercise the real session and bot logic for cancellation, shutdown, connection failures and message delivery. The Fabric client GameTest exercises the registered chat command in a dedicated-server connection.

Run builds and GameTests through `heavy`, with `./gradlew --no-daemon build runAllGameTests -Pfabricmoddingconventions.devClientOptions=/nonexistent`. A live Twitch login and a real NeoForge client shutdown are not covered by that automated suite.
