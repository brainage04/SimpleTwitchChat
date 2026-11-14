# Development

Use Java 25. Build the mod and run every test with:

```sh
./gradlew build runAllGameTests
```

The maintainer runs Gradle through `nomad-heavy`, a job queue for his own machines. Contributors do not need it.

`:fabric:test` runs the unit tests. `:fabric:runProductionClientGameTest` runs the client GameTest headlessly through the conventions plugin. NeoForge server GameTests are disabled because this mod is client-only, and no automated test starts the NeoForge jar, so a release includes a manual NeoForge check ([Releases](RELEASE.md)).

The film shots of the client GameTest recording (opening the chat and the captions) are in `fabric/src/recording`, so `fabric/src/gametest` holds only the test.

`:fabric:pitest` runs mutation tests on `ChatbotSession` against its unit tests. It is not part of `check`; the report is written to `fabric/build/reports/pitest`.

Keep `minecraftmodconventions_version` and the reusable-workflow versions in `.github/workflows/` aligned.
