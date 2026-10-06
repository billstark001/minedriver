# Command reference

`commands` in the CLI, or `session.commands` over RPC, is the authoritative discovery endpoint: each entry contains its name, description, channel, read-only hint and JSON input schema. Required/ranged parameters are validated by their handlers; the schemas describe the API and are not a general-purpose schema-validation engine.

## Plans and selectors

```json
{
  "steps": [
    {"command": "assert.widget", "parameters": {"selector": {"key": "menu.options"}}},
    {"command": "input.click", "parameters": {"selector": {"key": "menu.options"}}},
    {"command": "screenshot.capture", "parameters": {"name": "options"}},
    {"command": "input.key", "parameters": {"key": "ESCAPE"}}
  ]
}
```

Selectors accept any combination of `key`, `label`, `type`, `path`, `stableId`, with AND/exact matching. At least one must be present. `type` is the full Java class name; `path` begins at `0` and follows child indices, for example `0/3/1`. Prefer translation keys or explicit stable IDs for multilingual tests. Labels and tree paths can change with language/layout. Zero matches, multiple matches and hidden/disabled matches are distinct failures.

`ui.tree` is a bounded flat list with paths and native flags, coordinates, size, focus and available text values. `visible` reflects widget/ancestor flags; it does not prove pixel visibility or lack of occlusion. Semantic activation may bypass scroll clipping. GUI input is checked against window bounds and must be consumed by the native screen. Custom rendered controls without native children need an extension or coordinates.

## Commands

| Command | Parameters and behavior |
| --- | --- |
| `agent.status` | Available during startup; state and run ID. |
| `session.inspect` | Game/Java/loader/mod versions, development flag, screen/title/overlay, language, frame/fps/timing and player/dimension when present. |
| `session.commands` | Descriptions and parameter schemas. |
| `session.capabilities` | Supported runtime versions, installed frame barrier and explicit experimental switches. |
| `session.close` | Ends interactive session; refuses while a scenario is running. |
| `ui.tree` | `limit=8192`, range 1–8192; max recursion depth 32. |
| `assert.widget`, `wait.widget` | Required `selector`; require one enabled/visible match. Wait retries missing/disabled widgets, but ambiguity fails immediately. |
| `ui.activate` | Required `selector`; calls native `onPress` semantic action. |
| `ui.text` | Required `selector`, `value`; calls edit box `setValue`. |
| `ui.settings` | `modId` via Mod Menu/NeoForge, or `configClass` for Cloth AutoConfig. Factory must already exist. |
| `ui.title` | Opens title screen; disconnect a loaded world first. |
| `input.click` | `selector`, or GUI-scaled `x`,`y`; `button="LEFT"`, `modifiers=0`. Dispatches native screen movement, press and release. |
| `input.scroll` | `x=0`,`y=0`,`horizontal=0`,`vertical=0`, GUI coordinates; returns native consumed result. |
| `input.key` | `key="ESCAPE"`, `scanCode=0`, `modifiers=0`, `frames=1` (1–1200); keyboard handler press, frame hold and finally release. |
| `input.text` | Required `value`; native keyboard text/character callback. |
| `render.await` | `frames=2` (1–1200), completed render frames. |
| `screenshot.capture` | `name` (generated if omitted), `frames=2` (1–120); returns absolute PNG path, width, height and frame. |
| `screenshot.compare` | Required `actual`,`expected` PNG paths; generated `name` if omitted, optional `region`, `masks`, `channelTolerance=0`, `maxChangedFraction=0`. Returns metrics/diff path or fails `VISUAL_MISMATCH`. |
| `resources.language` | Required `code`, such as `en_us`, `zh_cn`, `zh_tw`, `ja_jp`; await native resource reload, overlay dismissal and frames. |
| `window.configure` | Optional `width` (320–7680), `height` (240–4320), `guiScale` (0–16); returns physical and GUI size. If resizing, omitted dimension defaults to 1280×720. |
| `world.create` | `name` (run-specific), `seed="1"`, `mode="CREATIVE"` or `SURVIVAL`, `flat=true`; enables commands and awaits a ready isolated singleplayer world. |
| `world.fixture` | Optional `position`, `blocks`, `inventory`, `commands`; executes authoritative integrated-server operations. |
| `world.snapshot` | `blocks` positions, `slots` inventory indices 0–35; returns authoritative block IDs/states, item IDs/counts and server tick. |
| `world.command` | Required `command`; integrated-server Brigadier dispatcher with server command source, returns command result and propagates parse errors. |
| `world.disconnect` | Saves/disconnects; awaits no local world/server and the captured server stopping, then completed frames. |
| `world.connect` | Required `address`; native multiplayer connection screen and world-ready wait. Experimental, not validated against a dedicated server. |
| `network.inspect` | Play connection status/address, native average sent/received packet counters and online-player count. |
| `network.command` | Required `command`; sends ordinary client command packet. `sent=true` is not server acknowledgement; assert its outcome separately. |
| `profile.start` | `jfr=false`; starts sampling, optional bounded 256 MiB / 30-minute JFR recording. |
| `profile.stop` | `name="profile"`; interval/render-method p50/p95/max/mean, observed/retained/dropped frames, GC/heap and local-server average tick time; optional JFR path. |
| `assert.state`, `wait.state` | Required dotted `path` in `session.inspect`, and `equals` JSON value; exact JSON equality. |
| `scenario.run` | Optional `class` or `plan`; otherwise configured scenarios/plan. Asynchronous acceptance; refuses concurrent scenarios. |
| `scenario.status` | `IDLE`, `RUNNING`, `PASS` or `FAIL`, `running`, error text. Wait for `running=false` before closing. |
| `java.invoke` | Only when `unsafeReflection=true`; required `class`,`method`, optional `arguments`; explicit public static invocation on client thread. Trusted debugging only. |
| `custom.*` | Explicitly registered extension handlers; generic object schemas, bounded JSON results. |

Wait, render, screenshot, resource reload, world creation/fixture/disconnect/connect operations accept `timeoutMillis` (1–3,600,000). Default operation timeout is 30 seconds. Check session timeout defaults to 300 seconds. Some multi-stage operations have separate waits; the overall session watchdog bounds checks. `onClient`/`onServer` actions already running cannot safely be preempted after a timeout; queued actions cancelled before starting are skipped.

Keyboard/mouse symbolic names resolve `InputConstants` in the actual game version. Prefer `ESCAPE`, `RETURN`, `W`, `F3`, `LEFT`, `RIGHT`, `MIDDLE`. Raw integer codes are version-dependent: 26.2 uses GLFW, while 26.3 uses SDL. These are native game callbacks, not OS input injection; arbitrary GUI input is not a substitute for server-state assertions.

Artifact names are simple ASCII basenames without path separators or `..`. Files remain under the session's output directory. Reusing a name replaces that artifact; choose unique names for captures you want to keep. Screenshot comparison paths refer to local files, with relative paths resolved against the isolated process working directory.

## Fixtures and visual comparison

```json
{
  "position": {"x": 0, "y": 5, "z": 0, "yaw": 0, "pitch": 0},
  "blocks": [{"x": 1, "y": 5, "z": 0, "state": "minecraft:gold_block"}],
  "inventory": [{"slot": 0, "item": "minecraft:stone", "count": 16}],
  "commands": ["weather clear", "time set noon"]
}
```

Inventory fixtures address hotbar slots 0–8. Snapshots can read all 36 main inventory slots. Position is applied using server teleport; wait/assert client effects where needed. Block/item IDs are explicit namespaced identifiers; blocks may include simple state properties. The native command parser enforces the runtime's rules.

```json
{
  "expected": "D:/baselines/options.png",
  "actual": "D:/reports/screenshots/options.png",
  "name": "options-diff",
  "region": {"x": 0, "y": 0, "width": 800, "height": 400},
  "masks": [{"x": 0, "y": 0, "width": 200, "height": 20}],
  "channelTolerance": 4,
  "maxChangedFraction": 0.001
}
```

Mask coordinates are relative to the comparison region. Comparisons reject invalid regions, size mismatches and entirely masked images. A diff is retained on visual mismatch. Pin language, window/GUI scale, resources, world time/weather/camera and warm-up before adopting baselines; animated backgrounds/chat/tooltips may need masks. Baselines should be reviewed and versioned independently, rather than automatically accepting the current output.

Frame sampling retains up to 4096 frames and reports dropped samples. Render-method duration is CPU wall time in the hooked method, not a GPU timer; frame intervals include pacing. Screenshot callback/file completion and complete-frame waiting do not promise pixel identity across graphics drivers.

## Local RPC

`connection.json` contains protocol 1, run ID, random `http://127.0.0.1:<port>` endpoint, bearer token and owned PID. POST newline-free JSON to `/rpc` with `Authorization: Bearer <token>` and `Content-Type: application/json`:

```json
{"jsonrpc":"2.0","id":1,"method":"ui.tree","params":{}}
```

Parameters must be an object; requests are limited to 1 MiB. Replies use the same ID, a `result`, or JSON-RPC `error` with `data.code`. Authentication, malformed envelopes, unknown commands and invalid arguments are distinguished. JSON-RPC notifications execute without a response body. The CLI validates loopback connections and reply identities and does not accept credentials as command-line arguments.

Typical driver categories: `ASSERTION_FAILED`, `VISUAL_MISMATCH`, `WIDGET_NOT_FOUND`, `AMBIGUOUS_WIDGET`, `WIDGET_DISABLED`, `INPUT_NOT_CONSUMED`, `INPUT_OUTSIDE_VIEWPORT`, `UNSUPPORTED_API`, `UNSUPPORTED_RUNTIME`, `PRODUCTION_DISABLED`, `NO_LOCAL_SERVER`, `GAME_THREAD_TIMEOUT`, `RENDER_TIMEOUT`, `CONDITION_TIMEOUT`, `SCENARIO_BUSY`, `SCENARIO_FAILED`, `INVALID_EXTENSION_RESULT`, `CLIENT_EXITED`, `CLIENT_CRASHED`, `SESSION_TIMEOUT`.
