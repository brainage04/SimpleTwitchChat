# Development

Use Java 25. Build the mod and run every test with:

```sh
./gradlew build runAllGameTests
```

The maintainer runs Gradle through `nomad-heavy`, a job queue for his own machines. Contributors do not need it.

`:fabric:test` runs the unit tests. `:fabric:runProductionClientGameTest` runs the client GameTest headlessly through the conventions plugin. NeoForge server GameTests are disabled because this mod is client-only, and no automated test starts the NeoForge jar, so a release includes a manual NeoForge check ([Releases](RELEASE.md)).

The film shots of the client GameTest recording (opening the chat and the captions) are in `fabric/src/recording`, so `fabric/src/gametest` holds only the test.

`:fabric:propertyTest` runs the jqwik properties outside the Fabric loader, where jqwik finds no properties; `check` runs it. `BotLoginPropertyTest` drives a started `Bot` through any order of hourly checks, retries, connection changes, Twitch answers and messages and compares it with a model of the login promises: a valid or unanswered check confirms the login, an invalid one renews it, a renewal reconnects the chat once its connect has finished, a refusal asks for a new login once, an unclear renewal is retried after five minutes with at most one retry waiting, and messages go out only while connected and confirmed. `ConnectionBackoffPropertyTest` checks `ConnectionBackoff` against twitch4j's default backoff and the way its websocket connection uses it.

`:fabric:pitest` runs mutation tests on `ChatbotSession`, `Bot` and `ConnectionBackoff` against `ChatbotSessionTest` and the property tests. It is not part of `check`; the report is written to `fabric/build/reports/pitest`.

Keep `minecraftmodconventions_version` and the reusable-workflow versions in `.github/workflows/` aligned.
