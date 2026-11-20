# Create a new release

1. Update `mod_version` in `gradle.properties`.
2. Start the NeoForge jar (`neoforge/build/libs`) on a NeoForge client, join a world, run `/twitchchat`, `/twc hello` and `/regenerateauthurl`, and quit. No automated test starts the NeoForge jar.
3. Commit the version change to Git.
4. Push that commit to GitHub.
5. Create a matching annotated git tag in the form `vX.Y.Z` so the tag message becomes the GitHub release notes.

    - For a short release note, run `git tag -a v1.0.1 -m "Summarise the release here"`.

    - For longer release notes, put them in a file and run `git tag -a v1.0.1 --cleanup=verbatim -F RELEASE_NOTES.md`. Without `--cleanup=verbatim`, git deletes every line that starts with `#`, so Markdown headings would vanish from the notes.

1. Push the tag with `git push origin v1.0.1`.

The release workflow reads the annotated tag message and uses it as the GitHub release body.
If the tag has no annotation text, GitHub auto-generated release notes are used as a fallback.
GitHub Actions checks out tag pushes in a way that can obscure annotated tag contents, so the workflow fetches the remote tag object before reading the notes.

The MinecraftModConventions reusable release workflow builds both loader JARs and attaches the Fabric and NeoForge artifacts to the GitHub Release.
If `MODRINTH_TOKEN` and `MODRINTH_PROJECT_ID` are configured, it publishes both loader JARs and the release notes to the configured Modrinth project. If both `CURSEFORGE_TOKEN` and `CURSEFORGE_PROJECT_ID` are configured, it publishes both loader JARs to CurseForge.
All destinations use the same tag notes. Missing third-party credentials skip only that destination; the GitHub Release still proceeds.
