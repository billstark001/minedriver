# Test authoring and integration

## Select the smallest useful check

| Requested behavior | Check |
| --- | --- |
| Screen navigation, settings, language, screenshot or simple fixture | JSON plan with state/widget assertions and bounded waits. |
| Mod-specific behavior requiring typed APIs or compound assertions | Java `Scenario` in the separate `minedriver` source set. |
| Interactive mod diagnostics or stable widget IDs | `DriverExtension` exposing explicit `custom.*` commands. |
| Server logic or registry assertions already using GameTest/FML/JUnit | Existing native framework task plus fresh JUnit XML verification. |

Start with the installed command schemas and actual inspected state. A screenshot-only plan may pass while the intended behavior fails; assert the effect. Use [session workflow](sessions.md) to reproduce and diagnose first.

## Integrate with the consuming project

Identify the loader subproject and its existing `JavaExec` client task. This snapshot uses JDK 25 and explicitly supports Minecraft 26.2/26.3; verify other versions against the local checkout's compatibility documentation. It is not yet published to the Plugin Portal.

Merge into the existing `pluginManagement` block in the mod project's `settings.gradle`:

```groovy
pluginManagement {
    includeBuild('../minedriver') // actual path to the MineDriver checkout
    // Retain existing loader repositories and settings.
}
```

Apply alongside the loader plugin in the consuming subproject:

```groovy
plugins {
    id 'io.github.billstark001.minedriver'
}
mineDriver {
    sourceRunTask.set('runClient')
    plan.set(layout.projectDirectory.file('driver/options.json'))
    timeoutMillis.set(300000L)
    operationTimeoutMillis.set(30000L)
}
```

MineDriver clones the source task's launch configuration and prerequisites; it does not execute a second source client. Its fresh game directory avoids using the normal saves. Applying it does not wire into `check` or release tasks. On Linux run the entire client Gradle invocation under `xvfb-run -a` when a display is unavailable; Loom's internal wrapper is not cloned. Loom launches currently do not support Gradle configuration cache.

## JSON plans

Write `driver/options.json` as an object with a `steps` array. This example assumes the inspected runtime uses the mapped OptionsScreen class shown below:

```json
{
  "steps": [
    {"command":"assert.widget","parameters":{"selector":{"key":"menu.options"}}},
    {"command":"input.click","parameters":{"selector":{"key":"menu.options"}}},
    {"command":"wait.state","parameters":{"path":"screen","equals":"net.minecraft.client.gui.screens.options.OptionsScreen"}},
    {"command":"screenshot.capture","parameters":{"name":"options"}},
    {"command":"input.key","parameters":{"key":"ESCAPE"}}
  ]
}
```

Run the consuming project's qualified `mineDriverCheck` task. Plans execute before Java scenarios. A check requires nonempty steps or at least one scenario.

Profiles in `mineDriver.profiles` inherit the global plan/settings and override `sourceRunTask`, `plan` and `scenarios`; `mineDriverMatrix` runs profiles sequentially in separate sessions. To make a profile scenario-only, explicitly point its plan at `{"steps":[]}` and configure a nonempty scenario list. Cross-version/loader checks use their respective native subprojects. Copy only explicitly needed config through `seedFiles.from(...)`; those files/directories are copied by basename and symlinks are rejected.

## Java scenarios and extensions

Put Java files under the consuming subproject's `src/minedriver/java`. They compile against mod/Minecraft/API dependencies and stay outside the main mod JAR. A scenario has a public no-argument constructor and implements `io.github.billstark001.minedriver.api.Scenario.run(ProbeContext)`.

```groovy
mineDriver {
    scenarios.add('example.ModScenario')
    extensionClasses.add('example.ModExtension') // only if needed
}
```

Use `ProbeContext.call`, `click`, `activate`, `text`, `screenshot`, `require` and bounded `await` from the scenario worker. For typed mod access, `onClient(Callable)` and `onServer(Callable)` schedule **short, nonblocking** actions. Do not call commands, wait for frames/conditions, do long IO or wait for another game thread inside these callbacks. Return immutable snapshots; `onServer` requires an integrated server. An already running callback cannot safely be preempted when its deadline expires.

An extension has a public no-argument constructor and implements `DriverExtension.register(ExtensionRegistry)`. Register unique `custom.*` handlers with accurate read-only flags, validate their parameters and schedule native access through the context. Registration itself runs off the game thread. Return JSON-compatible values only: null, strings, booleans, finite numbers, string-keyed maps or iterables; cycles, live game objects, depth over 32 or more than 8192 values fail.

Use `extensionClasses`, not the Gradle-reserved `extensions` property. Identify actual widget instances with `registry.identify(widget, "mod.screen.action")` on the client thread when constructing or reinitializing the screen. Stable IDs are weakly held and must be assigned again to replacement widgets. Prefer a narrow extension over enabling unsafe reflection.

Read matching-version [Java examples](https://github.com/billstark001/minedriver/tree/main/examples/scenarios) and [extension contracts](https://github.com/billstark001/minedriver/blob/main/docs/architecture.md) for implementation details.

## Native framework checks

Keep native framework source sets, dependencies and assertions owned by Loom/ModDevGradle. Configure already available tasks and **exact disposable generated XML files**:

```groovy
mineDriver {
    frameworkTasks.add('runGameTest') // actual native task in this subproject
    frameworkReports.from(layout.buildDirectory.file('native-gametest.xml'))
}
```

Configure the native runner to write that path, then invoke `mineDriverFrameworkCheck`. The agent is not injected into these tasks. MineDriver removes only the specified reports before forcing fresh tasks, and rejects missing XML, zero tests, all-skipped tests or any failure/error. Directories, globs or manually written XML do not satisfy the freshness contract. For dynamic filenames use a native stable aggregate report or an explicit known file list. Refer to [framework setup examples](https://github.com/billstark001/minedriver/blob/main/docs/frameworks.md) for Fabric GameTest and NeoForge JUnit.

## Validate the result

Run the smallest relevant real check, inspect fresh artifacts and record the runtime, task and assertions. Root MineDriver simulated-client tests prove driver contracts only; they do not establish a new Minecraft version's compatibility. Preserve failure evidence and fix the predicate/setup rather than removing assertions or accepting current screenshots as new baselines. Wire into normal verification only when requested by the project. Report remaining unsupported behavior explicitly.
