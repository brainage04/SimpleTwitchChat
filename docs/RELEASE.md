# Create a new release

1. Update `mod_version` in `gradle.properties`.
2. Commit that change.
3. Push that commit.
4. Create a matching annotated git tag in the form `vX.Y.Z` so the tag message becomes the GitHub release notes.
5. For a short release note, run `git tag -a v1.0.1 -m "Summarise the release here"`.
6. For longer release notes, put them in a file and run `git tag -a v1.0.1 -F RELEASE_NOTES.md`.
7. Push the tag with `git push origin v1.0.1`.

The FabricModdingConventions reusable release workflow prepares the exact release artifact and resolves the release notes from the annotated tag, falling back to generated GitHub release notes when necessary.

If `MODRINTH_TOKEN` and `MODRINTH_PROJECT_ID` are configured, the reusable workflow publishes that prepared artifact and its release notes to the configured Modrinth project.
