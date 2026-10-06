# Session workflow

## Locate or launch

Keep two paths distinct: the **mod project** owns the client task and reports; the **MineDriver checkout** supplies the CLI. Prefer an already configured session for the intended project. Otherwise launch its qualified `mineDriverRun` task for interaction or `mineDriverCheck` for a configured finite test. Follow the [test authoring guide](tests.md) if integration is missing.

For example, from the mod project on Windows:

```powershell
.\gradlew.bat :fabric:mineDriverRun
```

Replace `:fabric` with the actual loader subproject; for a single project use `mineDriverRun`. Keep this process running while using another terminal. Interactive sessions default to no overall timeout. Checks default to 300 seconds; individual operations default to 30 seconds.

Build a missing CLI from the MineDriver checkout using `./gradlew :cli:installDist` (`.\gradlew.bat` on Windows). The executable is `cli/build/install/minedriver/bin/minedriver` or `minedriver.bat`.

The consuming subproject has:

```text
build/minedriver/runs/<task>/<runId>/       isolated game directory
build/reports/minedriver/<task>/<runId>/   reports and artifacts
build/reports/minedriver/<task>/latest.json
```

Resolve the exact project's task pointer, not an arbitrary `latest.json` from a recursive search. A pointer identifies `runId`, `directory` and `connectionFile`. Confirm `agent.status` matches this ID; during startup use its state (`STARTING`, `READY`, `FAILED`, `FINISHED`) rather than calling ready-only inspection repeatedly.

## Connect through CLI or MCP

These example paths must be replaced with actual checkout and consuming-subproject paths:

```powershell
$driverCli = 'D:/Programs/minedriver/cli/build/install/minedriver/bin/minedriver.bat'
$driverSession = 'D:/mod/fabric/build/reports/minedriver/mineDriverRun/latest.json'
& $driverCli $driverSession call agent.status
& $driverCli $driverSession inspect
& $driverCli $driverSession commands
& $driverCli $driverSession call session.capabilities
& $driverCli $driverSession call ui.tree
```

CLI syntax is `minedriver <connection.json|latest.json> <inspect|commands|call|watch|mcp> [method] [JSON|@file]`. For parameters, write a UTF-8 JSON object to a local file and use `@file`, especially on Windows:

```powershell
# options-click.json contains {"selector":{"key":"menu.options"}}
& $driverCli $driverSession call input.click '@D:/mod/driver/options-click.json'
```

For an MCP host, use that executable with arguments `["<absolute latest.json path>", "mcp"]`. Launch the game first. If the host cannot launch `.bat`, use its supported shell wrapper. Discover tools through the host, inspect schemas, and use the bridge's tools; it supports screenshot PNG image content. Resources, prompts, cancellation and automatic reconnection are not implemented. Restart the bridge after every new game session; it resolves the pointer once at startup. Configuring the skill does not configure an MCP server.

## Exercise the requested behavior

1. Inspect screen, overlay, language, in-world state and loaded mods. Record the initial state relevant to the test. For settings, use `ui.settings` with the actual `modId` or Cloth AutoConfig `configClass`; the target mod must already provide its factory/dependency.
2. Read `ui.tree` and choose one enabled, visible widget. Prefer `key` or `stableId`; `label` depends on language, `path` on layout, and `type` is a full Java class name. All supplied selector fields must match. A visible flag does not prove pixel visibility or lack of occlusion.
3. Use `ui.activate`/`ui.text` for semantic behavior. Use `input.click`/`input.key`/`input.text` for native event behavior. Coordinates are GUI-scaled, not physical screenshot pixels; obtain window/GUI sizes with `window.configure`. Prefer symbolic keys such as `ESCAPE` or `RETURN`; raw 26.2 GLFW and 26.3 SDL codes differ.
4. Verify the effect using `wait.widget`, `wait.state` or an explicit scenario assertion. Native input being consumed alone does not prove the intended mod effect. Reinspect before the next action.
5. Capture with `screenshot.capture` using a unique ASCII basename without path separators or `..`. It awaits completed frames and a validated PNG. Reusing a name overwrites the artifact.

For language checks use `resources.language` with `en_us`, `zh_cn`, `zh_tw` or `ja_jp`; it awaits resource reload, overlay dismissal and frames. Rediscover widgets after each reload. For visual comparisons pin physical window size, GUI scale, language, resources, camera, time/weather and warm-up. Use explicitly reviewed baselines; comparison masks are relative to the selected region. Retain the diff on failure instead of silently widening tolerance or replacing the baseline.

For world checks create an isolated `world.create`, apply explicit `world.fixture` positions/blocks/hotbar inventory/commands, and verify `world.snapshot` block states or inventory IDs/counts from the integrated server. `network.command` returns `sent=true` and `serverResultVerified=false`; assert the effect separately. `world.connect` is experimental and dedicated-server behavior is not in the real validation matrix. Remote private server state cannot be obtained through `onServer` or local snapshots.

For performance bracket the requested workload with `profile.start`/`profile.stop`; JFR is optional. Report workload and retained/dropped samples. Frame intervals include pacing; render-method duration is CPU time, not GPU timing. The bounded ring retains 4096 samples, so a summary is not an unlimited whole-session recording.

## Finish or diagnose

`scenario.run` returns asynchronous acceptance. Poll `scenario.status` at a bounded interval until `running=false`; treat FAIL and timeout as failures. Then call `session.close` only for the session owned by your test or explicitly requested closure. It refuses while a scenario is running. Wait for the original Gradle process to finish and check its result marker as described in `SKILL.md`.

For a failure, inspect this run's `result.json` failure and step trace, `report.html`, failure screenshot if available, and the relevant `client.log` section. Keep the original artifacts when rerunning; each launch gets a new ID. Classify before changing configuration:

| Evidence | Next action |
| --- | --- |
| Missing/stale report or connection refused | Check the original Gradle process, exact task path, run ID and startup log; restart a stale bridge for the new session. |
| Startup/mod loading failure | Fix the identified dependency or loader configuration in the requested project. Do not broadly disable test mods or accept warnings as a substitute. |
| `WIDGET_NOT_FOUND`, `AMBIGUOUS_WIDGET`, `WIDGET_DISABLED` | Inspect screen/overlay and tree; refine a selector or wait for expected readiness. |
| `INPUT_NOT_CONSUMED`, `INPUT_OUTSIDE_VIEWPORT` | Check GUI coordinates, focus, scroll clipping and current screen; inspect the actual effect. |
| `NO_LOCAL_SERVER` | Create the intended isolated world, or use the native/server-side test framework for that assertion. |
| `UNSUPPORTED_RUNTIME`, `UNSUPPORTED_API`, `PRODUCTION_DISABLED` | Check capabilities and documented support; report the limitation or add a tested adapter within scope. |
| Render/game-thread/condition timeout | Inspect client health and the awaited predicate; fix blocking callbacks or a wrong condition before increasing timeouts. |

Full command details: [upstream command reference](https://github.com/billstark001/minedriver/blob/main/docs/commands.md). Supported combinations: [validation record](https://github.com/billstark001/minedriver/blob/main/docs/validation.md). Prefer matching-version documentation from a local MineDriver checkout when available.
