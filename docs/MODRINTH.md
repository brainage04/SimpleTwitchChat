# Modrinth publishing

Modrinth publishing is performed by the reusable `release.yml` workflows from FabricModdingConventions. The release workflow publishes the exact release artifact prepared from a matching `v<mod_version>` tag.

## Required configuration

Configure these repository settings before publishing:

- `MODRINTH_TOKEN` secret: a Modrinth token permitted to publish versions.
- `MODRINTH_PROJECT_ID` variable: the existing Modrinth project ID for SimpleTwitchChat.

The reusable workflow receives the release artifact and release notes from the preparation job. It does not create or mutate the Modrinth project and this repository contains no local Modrinth publishing scripts.

`.modrinth/project.json` remains project metadata for tooling that consumes it; release publishing is configured by the GitHub repository variable and secret above.
