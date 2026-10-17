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

`ui.snapshot` is the single UI inspection command. It returns a snapshot object, not a flat list:

- `frame`, `screen`, `screenId`, `coordinateSpace="gui"`, `viewport`, and `window` identify the layout and coordinate transform.
- `elements` contains the bounded widget tree (paths, identities, labels/keys, native flags, values, role and Cloth `expanded` state). Geometry is `layoutBounds`, `hitBounds`, `clippedBounds`, and `boundsSource`. Unknown geometry is null. `clippedBounds` intersects the hit region with ancestor list clips and the GUI viewport; it does not establish occlusion.
- `slots` contains active/inactive and empty container slots: `menuSlot`, `containerSlot`, item ID/count, content/hit/clipped bounds. Slot numbers address the currently open container menu, not the server inventory snapshot numbering.
- `render` contains its own completed-frame number and screen ID, `draws` for standard GUI text/items, installed hooks, capture failure and dropped-count diagnostics. Text is visual-order text; native render-state bounds include font layout, transforms and scissor clipping. These draw records are observation data, not widget selectors. Custom shaders or drawing paths outside these native states require a dedicated adapter.

`visible` reflects widget/ancestor state, including Cloth display requirements. It does not prove absence of occlusion. Semantic actions can operate outside scroll clipping; `input.click` requires a nonempty clipped hit region and no overlay. Cloth category clicks target the native header region and verify that expansion changed. `ui.expand` sets the requested state idempotently. There is no old UI command alias.

## Commands

| Command | Parameters and behavior |
| --- | --- |
| `agent.status` | Cached lifecycle state/phase, awaited condition, screen/overlay, actual focus, pause/readiness, heartbeat/snapshot age and last failure. Never waits for the game thread or action lock. |
| `session.inspect` | Game/Java/loader/mod versions, development flag, screen/title/overlay, language, frame/fps/timing and player/dimension when present. |
| `session.commands` | Descriptions and parameter schemas. |
| `session.capabilities` | Supported runtime versions, installed frame/render hooks, input isolation/background policy and explicit experimental switches. |
| `session.close` | Ends interactive session; refuses while a scenario is running. Closing rejects queued/new ordinary actions with `SESSION_CLOSED`; cached status/discovery remain available during cleanup. |
| `ui.snapshot` | `limit=8192` native widgets, range 1–8192, max depth 32; optional artifact `name` writes `ui/<name>.json`. Waits for a completed frame before sampling. Render capture retains at most 8192 native states per frame. |
| `ui.expand` | Required `selector`, `expanded`; set and verify a Cloth category state. |
| `assert.widget`, `wait.widget` | Required `selector`; require one enabled/visible match. Wait retries missing/disabled widgets, but ambiguity fails immediately. |
| `ui.activate` | Required `selector`; calls native `onPress` semantic action. |
| `ui.text` | Required `selector`, `value`; calls edit box `setValue`. |
| `ui.settings` | `modId` via Mod Menu/NeoForge, or `configClass` for Cloth AutoConfig. Factory must already exist. |
| `ui.title` | Opens title screen; disconnect a loaded world first. |
| `input.click` | Exactly one target: `selector`, `slot` (menu index), or both GUI-scaled `x`,`y`. Optional `screenId` rejects stale screens; `part="header"` selects a Cloth category header (`hit` is default). `button="LEFT"`, `modifiers=0`. Dispatches native screen movement, press and paired release. |
| `input.scroll` | `x=0`,`y=0`,`horizontal=0`,`vertical=0`, GUI coordinates; returns native consumed result. |
| `input.key` | `key="ESCAPE"`, `action="TAP"`, `scanCode=0`, `modifiers=0`, `frames=1` (1–1200). `TAP` presses, holds completed frames and releases even on timeout; `PRESS`/`RELEASE` support explicit chords and longer holds. Modifier bits are event metadata; press named modifier keys to affect native state polling. |
| `input.text` | Required `value`; native keyboard text/character callback. |
| `input.isolation.acquire` | `ttlMillis=60000` (100–3,600,000); returns an owned `leaseId` and actual hook status. |
| `input.isolation.renew` | Required `leaseId`, optional `ttlMillis`; renew a live interactive lease. |
| `input.isolation.release` | Required `leaseId`; releases only that interactive lease. Other leases and the check's session lease remain active. |
| `input.isolation.status` | Cached isolation/background policy, leases, required/installed hooks, dropped-event count, expiry and hook failure; bypasses action locking. |
| `render.await` | `frames=2` (1–1200), completed render frames. |
| `screenshot.capture` | `name` (generated if omitted), `frames=2` (1–120); returns absolute PNG path, width/height, paired `uiSnapshot` JSON path, `requestedAfterFrame` and `completedAtFrame`. The interval is explicit; file completion is not a new pixel-frame assertion. |
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
| `scenario.run` | Optional `class` or `plan`; otherwise configured scenarios/plan. Asynchronous acceptance; shares the busy guard with finite checks and refuses concurrent scenarios. |
| `scenario.status` | `IDLE`, `RUNNING`, `PASS` or `FAIL`, `running`, error text. Wait for `running=false` before closing. |
| `java.invoke` | Only when `unsafeReflection=true`; required `class`,`method`, optional `arguments`; explicit public static invocation on client thread. Trusted debugging only. |
| `custom.*` | Explicitly registered extension handlers; generic object schemas, bounded JSON results. |

Every built-in command accepts `timeoutMillis` (1–3,600,000). The default operation budget is 30 seconds, shared by command-lock admission, client/server dispatch and all nested waits; it is not restarted at each stage. Check session timeout defaults to 300 seconds. Startup has one deadline spanning first-frame and title readiness, including interactive launches. `GAME_THREAD_TIMEOUT` states whether the callback began: an already running callback has an unknown outcome and must not be blindly retried; an expired queued action is skipped. Failed key presses and taps always attempt release under an independent two-second budget; cleanup failures are suppressed under the original error. Explicit successful `PRESS` holds until release or input-controller cleanup. `agent.status` remains available while ordinary RPC requests are queued.

Checks acquire a non-expiring session input lease by default (`mineDriver.inputIsolation`, default true); interactive launches start without a lease and use the lease commands. Releasing an interactive lease cannot release the check's session policy. `mineDriver.backgroundExecution` defaults to true: it temporarily disables lost-focus pause and scopes virtual focus to automated input callbacks, while observation queries SDL/GLFW focus and GLFW visibility instead of Minecraft’s initial cached focus flag. With background execution false, unfocused input fails `FOCUS_LOST`.

Isolation covers standard Minecraft keyboard/text/IME/mouse callbacks, queued input generations, key-state polling and SDL cursor resynchronization in 26.2/26.3. Native mouse capture/warps are suppressed during isolation. Transition cleanup releases keys/buttons, dragging, preedit and movement; externally held keys/buttons are quarantined until release, including standard key polling. A native up-state query also clears key quarantine if a release callback was discarded across a transition. Lease expiry is checked at acquisition, renewal, release and input dispatch as well as by the observer. A last-lease expiry restores input and records `INPUT_LEASE_EXPIRED`; acquire a new lease before continuing automation. Missing required hooks fail `INPUT_ISOLATION_UNAVAILABLE`. Closing releases injected input even without an isolation lease and rejects new input/acquisitions with `SESSION_CLOSED`. Other processes are untouched. Mods that bypass Minecraft and read native/system devices directly are outside this guarantee. Minimized windows may stop producing rendered frames: render waits/screenshots then fail with a bounded render timeout; background execution does not fabricate frames.

`filesystem-gradle.json` and `filesystem-client.json` record process identity, inherited-primary-token SID/default owner/admin status on Windows, directory existence, resolved paths, owner and ACL at creation/seed/launch/finish stages. Diagnostic failures are recorded or logged. These files never change ownership or ACLs; they are evidence for a future reproducible incident, not an ownership repair.

Startup is ready only at the title screen after overlays finish. NeoForge loading issues fail promptly and are recorded in `startup.warnings`, with the usual failure UI tree and screenshot. To accept a diagnosed warning, configure `mineDriver.acceptedStartupWarnings.add('modId:exact translationKey')`; keys can be literal templates in NeoForge's native issue model. The affected mod and key must both match, every issue must be a warning, and every warning must be accepted. Unknown warnings and fatal errors block execution. A successful warning acceptance does not establish gameplay readiness or a passing scenario.

Before the first completed frame, the agent also checks its isolated run directory for fresh `crash-*-client.txt` reports. A loader-handled crash can bypass the uncaught-exception hook and leave startup waiting; this path now fails as `STARTUP_CRASH` with the native report path. Older seeded reports are ignored. A pre-frame crash has no rendered UI to capture.

Keyboard/mouse symbolic names resolve `InputConstants` in the actual game version. Prefer `ESCAPE`, `RETURN`, `W`, `F3`, `LEFT`, `RIGHT`, `MIDDLE`. Raw integer codes are version-dependent: 26.2 uses GLFW, while 26.3 uses SDL. Unknown native window/input versions fail `UNSUPPORTED_API` even when the experimental version guard is bypassed; no other backend is guessed. These are native game callbacks, not OS input injection; arbitrary GUI input is not a substitute for server-state assertions.

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

World creation and connection wait for a player, level, and dismissal of both the loading screen and resource overlay before returning. A player/level alone does not establish gameplay input readiness: a shortcut sent while the loading screen is still open can be ignored. A later resource reload or screen transition still needs its own state assertion.

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
{"jsonrpc":"2.0","id":1,"method":"ui.snapshot","params":{}}
```

Parameters must be an object; requests are limited to 1 MiB. Replies use the same ID, a `result`, or JSON-RPC `error` with `data.code`. Authentication, malformed envelopes, unknown commands and invalid arguments are distinguished. JSON-RPC notifications execute without a response body. The CLI validates loopback connections and reply identities and does not accept credentials as command-line arguments.

Additional input/UI/lifecycle categories include `COMMAND_BUSY`, `OPERATION_TIMEOUT`, `FOCUS_LOST`, `INPUT_ISOLATION_UNAVAILABLE`, `INPUT_LEASE_EXPIRED`, `INPUT_LEASE_NOT_FOUND`, `UI_CHANGED`, `WIDGET_CLIPPED`, `UI_EFFECT_NOT_OBSERVED`, `SLOT_NOT_FOUND`, `SLOT_DISABLED`, `STARTUP_WARNINGS`, `STARTUP_ERRORS`, `STARTUP_BLOCKED`, `STARTUP_CRASH`, `STARTUP_TIMEOUT`, `WORLD_LOAD_FAILED`.

Typical driver categories: `ASSERTION_FAILED`, `VISUAL_MISMATCH`, `WIDGET_NOT_FOUND`, `AMBIGUOUS_WIDGET`, `WIDGET_DISABLED`, `INPUT_NOT_CONSUMED`, `INPUT_OUTSIDE_VIEWPORT`, `UNSUPPORTED_API`, `UNSUPPORTED_RUNTIME`, `PRODUCTION_DISABLED`, `NO_LOCAL_SERVER`, `GAME_THREAD_TIMEOUT`, `RENDER_TIMEOUT`, `CONDITION_TIMEOUT`, `SCENARIO_BUSY`, `SCENARIO_FAILED`, `INVALID_EXTENSION_RESULT`, `CLIENT_EXITED`, `CLIENT_CRASHED`, `SESSION_TIMEOUT`.
