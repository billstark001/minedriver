# Building and releasing

`version` in the root `gradle.properties` controls Gradle artifact versions and embedded agent metadata. Before changing it, review `CHANGELOG.md`, move the completed work into a dated `## [X.Y.Z]` section, and keep an empty Unreleased section for later changes. Current releases use stable `MAJOR.MINOR.PATCH` versions and matching `vX.Y.Z` tags.

`gradle.properties` is the single version source. The protocol resource task generates `BuildInfo.VERSION` metadata consumed by Agent inspection and MCP identification; JAR manifests and distribution names use the same Gradle version. `integration/external.init.gradle` reads that properties file to select its versioned JARs. Update current documentation examples and the dated changelog together; historical validation entries retain the version actually tested. External consumer scripts should read the same metadata instead of copying a version constant.

```powershell
.\gradlew.bat format check build :cli:installDist
.\scripts\build-release.ps1 -SkipBuild
```

The collector verifies the dated changelog and every input before writing `build/release/<version>/`. It retains other versions and audit reports. The seven distribution files are API, protocol, hooks, standalone shaded agent and Gradle-plugin JARs, plus ZIP/TAR CLI distributions. `SHA256SUMS.txt` covers all seven. The CLI archives include their runtime dependencies and launchers; the thin CLI JAR is not a standalone command-line distribution. The Gradle plugin embeds its agent and API artifacts, while its normal plugin dependencies still need Gradle resolution.

After validation, push commits and an annotated matching tag. The Release workflow verifies tag/version/changelog agreement, runs the full JVM checks and build, collects the distributions, and publishes a GitHub Release. It can be retried with `workflow_dispatch` against the same existing tag. Do not move a published tag. GitHub also provides source archives for composite-build consumers.

GitHub distribution does not publish Maven coordinates or register the plugin with the Gradle Plugin Portal. Use a checkout with `includeBuild`, or `publishToMavenLocal` and `mavenLocal()` as described in the README. Remote Maven/Plugin Portal publication needs a separately configured destination and credentials.

## Upgrading to 0.3.0

This release intentionally removes the previous UI command and broad startup-warning switch. Migrate UI consumers to the `ui.snapshot` object (`elements`, `slots`, `render.draws` and explicit bounds); use exact diagnosed `acceptedStartupWarnings` identities. Unknown agent configuration fields and the unused agent `framework` mode are rejected. Native framework execution remains `mineDriverFrameworkCheck`.

Custom implementations of the Java `Driver` interface must implement `await(...)` and `isGameThread()`; there are no compatibility defaults. Finite checks isolate standard game input by default, so simulated runtimes without complete native hooks must explicitly configure `inputIsolation=false`. Supported 26.2 and 26.3 API differences remain explicit adapters; unknown window/input versions fail instead of guessing a GLFW path.
