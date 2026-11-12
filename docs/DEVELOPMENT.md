# Development

Use Java 25. Check `heavy status` before starting Gradle, and run Gradle through `heavy` with `--no-daemon`.

```sh
heavy --label simpletwitchchat -- ./gradlew --no-daemon build runAllGameTests
```

`:fabric:test` runs the unit tests. `:fabric:runProductionClientGameTest` runs the client GameTest headlessly through the conventions plugin. NeoForge server GameTests are disabled because this mod is client-only.

The film shots of the client GameTest recording (opening the chat and the captions) are in `fabric/src/recording`, so `fabric/src/gametest` holds only the test.

`:fabric:pitest` runs mutation tests on `ChatbotSession` against its unit tests. It is not part of `check`; the report is written to `fabric/build/reports/pitest`.

Keep `minecraftmodconventions_version` and the reusable-workflow versions in `.github/workflows/` aligned.
