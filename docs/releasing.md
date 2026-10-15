# Building and releasing

`version` in the root `gradle.properties` controls Gradle artifact versions and embedded agent metadata. Before changing it, review `CHANGELOG.md`, move the completed work into a dated `## [X.Y.Z]` section, and keep an empty Unreleased section for later changes. Current releases use stable `MAJOR.MINOR.PATCH` versions and matching `vX.Y.Z` tags.

Update the agent's inspected version in `GameRuntime`, the MCP server's version in `McpServer`, the versioned JAR paths in `integration/external.init.gradle`, and current README examples together. Consumer init scripts such as Latticium's must select the new versioned plugin and protocol JARs too. Historical validation entries retain the version actually tested.

```powershell
.\gradlew.bat format check build :cli:installDist
.\scripts\build-release.ps1 -SkipBuild
```

The collector verifies the dated changelog and every input before writing `build/release/<version>/`. It retains other versions and audit reports. The seven distribution files are API, protocol, hooks, standalone shaded agent and Gradle-plugin JARs, plus ZIP/TAR CLI distributions. `SHA256SUMS.txt` covers all seven. The CLI archives include their runtime dependencies and launchers; the thin CLI JAR is not a standalone command-line distribution. The Gradle plugin embeds its agent and API artifacts, while its normal plugin dependencies still need Gradle resolution.

After validation, push commits and an annotated matching tag. The Release workflow verifies tag/version/changelog agreement, runs the full JVM checks and build, collects the distributions, and publishes a GitHub Release. It can be retried with `workflow_dispatch` against the same existing tag. Do not move a published tag. GitHub also provides source archives for composite-build consumers.

GitHub distribution does not publish Maven coordinates or register the plugin with the Gradle Plugin Portal. Use a checkout with `includeBuild`, or `publishToMavenLocal` and `mavenLocal()` as described in the README. Remote Maven/Plugin Portal publication needs a separately configured destination and credentials.
