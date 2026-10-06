# MineDriver

MineDriver is a Minecraft mod development and debugging toolkit. Its Gradle plugin launches an isolated client with a Java agent; a local CLI, JSON plans, Java scenarios and an MCP stdio bridge inspect and control that client.

**Package/plugin ID:** `io.github.billstark001.minedriver` · **Version:** `0.1.0-SNAPSHOT` · **License:** MIT

[简体中文](docs/README.zh-CN.md) · [Agent skill](docs/skills.md) · [Commands](docs/commands.md) · [Architecture/extensions](docs/architecture.md) · [Native frameworks](docs/frameworks.md) · [Compatibility/validation](docs/validation.md)

## Features

- Native screen/widget inspection and exact selectors: translation key, label, type, tree path or stable ID. Semantic activation/text setters and native game input are distinct channels.
- Fabric Mod Menu, NeoForge settings factories and Cloth AutoConfig screen entry points, without bundling those mods.
- Completed-frame barriers, asynchronous validated PNG capture, resource/language reload and screenshot comparison with crop, masks, tolerance and diff artifacts.
- Isolated world creation, server fixtures, authoritative block/inventory snapshots, Brigadier commands and ordinary client command packets.
- Frame intervals, render-method duration, GC, heap, local-server tick sampling and optional JFR recording.
- JSON plans, Java scenarios, `custom.*` extensions, CLI and MCP tools with schemas and screenshot image content.
- Existing Fabric/NeoForge test tasks plus fresh JUnit XML validation; JSON/JUnit XML/HTML run reports, logs and failure screenshots.

The tool ships **no loader mod metadata or mod initializer**. Its separate `minedriver` source set stays out of your mod's main JAR. Native framework fixtures contain metadata belonging to their test subject, not the driver.

## Try it

Use JDK 25. The wrapper pins Gradle 9.5.0. Client runs need a working display/graphics environment; the first integration run downloads Minecraft dependencies and assets.

```powershell
.\gradlew.bat check build :cli:installDist
.\gradlew.bat -p integration/neoforge mineDriverCheck
.\gradlew.bat -p integration/neoforge '-Pplan=../../examples/plans/neoforge-settings.json' '-Pprobe=fixture' mineDriverCheck
.\gradlew.bat -p integration/fabric mineDriverFrameworkCheck
.\gradlew.bat -p integration/neoforge-tests mineDriverFrameworkCheck
```

Use `./gradlew` on Unix. In Linux CI run the whole client invocation under `xvfb-run -a`; the cloned task does not reproduce Loom's internal Xvfb wrapper. Root CI uses a simulated client to verify the driver lifecycle; real game integration tests are separate.

## Add to a mod project

This snapshot is usable through a composite build; it has not been published to the Plugin Portal. Add the checkout to the existing `pluginManagement` block in your project's `settings.gradle`:

```groovy
pluginManagement {
    includeBuild('../minedriver')
    // Keep your existing loader repositories and settings.
}
```

Alternatively, run `publishToMavenLocal` in this checkout, add `mavenLocal()` to the consuming project's plugin repositories, and apply `id 'io.github.billstark001.minedriver' version '0.1.0-SNAPSHOT'`. The local plugin marker and a complete forked client launch through that published plugin were verified. No remote publication is performed.

In the loader subproject, apply MineDriver alongside your existing Loom/ModDevGradle plugin:

```groovy
plugins {
    // Your existing loader plugin.
    id 'io.github.billstark001.minedriver'
}
mineDriver {
    sourceRunTask.set('runClient')
    plan.set(layout.projectDirectory.file('driver/title-smoke.json'))
    timeoutMillis.set(300000L)
    operationTimeoutMillis.set(30000L)
}
```

Copy [title-smoke.json](examples/plans/title-smoke.json) to the plan path, then run `mineDriverCheck`. In a multi-project build configure the loader subproject and invoke its qualified task, such as `:versions:fabric-26.3:mineDriverCheck`.

The source must be a `JavaExec` client task. MineDriver copies its prerequisites, classpath, main class, Java launcher, arguments and environment, then replaces the game directory and injects its agent. It does not also execute the source task. Applying the plugin does not change ordinary `runClient`, `check` or release tasks.

Every invocation creates its own directories:

```text
build/minedriver/runs/<task>/<runId>/       game directory and saves
build/reports/minedriver/<task>/<runId>/   connection.json, client.log, reports and artifacts
build/reports/minedriver/<task>/latest.json
```

A check requires a nonempty plan or a Java scenario. Success requires a complete PASS result carrying the current run ID and a successful process exit. Zero exit, stale/missing reports, startup failure and timeouts cannot substitute for a passing test.

## Agent skill

The portable [minedriver skill](.agents/skills/minedriver/SKILL.md) guides agents through client inspection, UI/world reproductions, regression tests and failure triage. It loads session and test-authoring references only when needed, and distinguishes observed behavior from provisional or stale test results.

Codex discovers it from this checkout's `.agents/skills`. For work in another mod repository, copy the **whole** `.agents/skills/minedriver` folder into that repository's `.agents/skills`, or install it using the [installation guide](docs/skills.md). The skill requires a usable MineDriver installation/session; it does not install the Gradle plugin or configure an MCP server automatically.

Example prompt after installation:

```text
Use $minedriver to verify this mod's settings in English, Simplified/Traditional Chinese and Japanese. Assert navigation and report screenshots and failures.
```

See [skill setup, examples and maintenance](docs/skills.md) for discovery paths, installation from GitHub and CLI/MCP prerequisites.

## Interactive CLI and MCP

Start `mineDriverRun` in your mod project, then use the installed CLI from this checkout in another terminal:

```powershell
.\cli\build\install\minedriver\bin\minedriver.bat D:/mod/build/reports/minedriver/mineDriverRun/latest.json inspect
.\cli\build\install\minedriver\bin\minedriver.bat D:/mod/build/reports/minedriver/mineDriverRun/latest.json commands
.\cli\build\install\minedriver\bin\minedriver.bat D:/mod/build/reports/minedriver/mineDriverRun/latest.json call ui.tree
.\cli\build\install\minedriver\bin\minedriver.bat D:/mod/build/reports/minedriver/mineDriverRun/latest.json call session.close
```

`call <method> <JSON|@file>` supplies parameters; prefer `@file` on Windows to avoid quoting problems. `watch` prints state once per second. Interactive sessions default to no overall timeout. `session.close` writes reports and stops the owned client; wait for `scenario.status.running=false` first. A failed asynchronous scenario fails the session on close. Individual interactive RPC errors remain in the step trace; the overall verdict follows the scenario/lifecycle outcome.

For an MCP host, configure a stdio server using the installed `minedriver` executable with arguments `["D:/mod/build/reports/minedriver/mineDriverRun/latest.json", "mcp"]`. Use `minedriver.bat` on Windows, or a shell wrapper if your host cannot launch batch files. Start the game first, and restart the bridge after launching another game.

The bridge implements MCP **2025-11-25** initialization and tools, including input schemas and PNG image content. Stdout contains protocol messages only. Resources, prompts, cancellation and automatic reconnection are not implemented.

## Java scenarios, extensions and profiles

Put classes in `src/minedriver/java`; they compile against your mod/Minecraft dependencies and remain outside the production JAR:

```java
package example;
import io.github.billstark001.minedriver.api.*;
public final class Smoke implements Scenario {
    public void run(ProbeContext context) {
        context.click(Selector.key("menu.options"));
        context.screenshot("options");
    }
}
```

```groovy
mineDriver {
    scenarios.add('example.Smoke')
    extensionClasses.add('example.ScreenExtension')
    seedFiles.from(layout.projectDirectory.file('driver/options.txt')) // optional
}
mineDriver.profiles {
    create('settings') { plan.set(layout.projectDirectory.file('driver/settings.json')) }
    create('fixture') { scenarios.set(['example.FixtureAssertions']) }
}
```

The JSON plan runs before Java scenarios. Use `extensionClasses`, because Gradle reserves `extensions` for its extension container. See [examples](examples/scenarios) and the [extension contract](docs/architecture.md).

`mineDriverMatrix` runs `mineDriverSettingsCheck` and `mineDriverFixtureCheck` sequentially with independent sessions. Profiles inherit the global plan; for scenario-only profiles supply `{"steps":[]}` alongside a nonempty scenario list. Cross-version/loader matrices use separate loader subprojects and native runtimes.

## Support and development

Current runtime adapters support **Minecraft 26.2 and 26.3**, with the tested combinations in [validation](docs/validation.md). Driver artifacts target Java 17 bytecode; building this project and running the tested game integrations uses JDK 25. Older obfuscated versions require additional mappings/adapters.

Development detection is required by default. `allowProduction=true` opts into experimental production/unclassified use; `allowUnsupportedRuntime=true` separately opts into unknown versions. Neither provides compatibility guarantees or a sandbox. Java scenarios execute trusted code in the game process.

The random loopback endpoint uses a per-session bearer secret in `connection.json`. Keep it local and omit it, private logs and copied account configuration when sharing reports. Use test accounts/servers/worlds appropriate for the scenario.

```powershell
.\gradlew.bat format
.\gradlew.bat check build
.\gradlew.bat publishToMavenLocal # optional local artifacts and Gradle plugin marker
```

CI performs verification only. No release workflow, automatic publication or release tag is configured.
