# Create a new release

1. Update `mod_version` in `gradle.properties`.
2. Start the NeoForge jar (`neoforge/build/libs`) on a NeoForge client, join a world, run `/tc`, `/tc hello` and `/regenerateauthurl`, and quit. No automated test starts the NeoForge jar.
3. Commit the version change.
4. Push that commit.
5. Create a matching annotated git tag in the form `vX.Y.Z` so the tag message becomes the GitHub release notes.
6. For a short release note, run `git tag -a v1.0.1 -m "Summarise the release here"`.
7. For longer release notes, put them in a file and run `git tag -a v1.0.1 -F RELEASE_NOTES.md`.
8. Push the tag with `git push origin v1.0.1`.

The release workflow reads the annotated tag message and uses it as the GitHub release body.
If the tag has no annotation text, GitHub auto-generated release notes are used as a fallback.
GitHub Actions checks out tag pushes in a way that can obscure annotated tag contents, so the workflow fetches the remote tag object before reading the notes.

The MinecraftModConventions reusable release workflow builds both loader JARs and attaches the Fabric and NeoForge artifacts to the GitHub Release.
If `MODRINTH_TOKEN` and `MODRINTH_PROJECT_ID` are configured, it publishes both loader JARs and the release notes to the configured Modrinth project. If both `CURSEFORGE_TOKEN` and `CURSEFORGE_PROJECT_ID` are configured, it publishes both loader JARs to CurseForge.
All destinations use the same tag notes. Missing third-party credentials skip only that destination; the GitHub Release still proceeds.
