# Development

Use Java 25. Check `heavy status` before starting Gradle, and run Gradle through `heavy` with `--no-daemon`.

```sh
heavy --label simpletwitchchat -- ./gradlew --no-daemon build runAllGameTests -Pfabricmoddingconventions.devClientOptions=/nonexistent
```

`:fabric:test` runs the unit tests. `:fabric:runProductionClientGameTest` runs the client GameTest headlessly through the conventions plugin. NeoForge server GameTests are disabled because this mod is client-only.

Pass `-Pfabricmoddingconventions.devClientOptions=/nonexistent` to client runs so they do not import personal launcher options. Keep `fabricmoddingconventions_version` and the reusable-workflow versions in `.github/workflows/` aligned.
