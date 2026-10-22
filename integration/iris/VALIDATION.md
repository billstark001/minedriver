# Iris P2 follow-up validation — 2026-10-23

This revision changes only this integration fixture. MineDriver core modules,
public APIs and the execution model are unchanged.

## Changes checked

- Marker presence still requires at least 99% matching pixels in each sampled
  region. Absence independently requires at most 1% residue in **both** regions;
  large or single-color marker fragments no longer count as absent.
- Shader and HUD restoration are attempted independently. If both fail, the first
  error retains the second as suppressed. With a scene failure, the scene remains
  primary and retains the entire cleanup error chain. Failed cleanup does not
  produce a successful `restored.json`.
- World setup now only creates the isolated flat world needed to activate Iris's
  world pipeline. Gold blocks, camera placement and time/weather commands were
  removed, along with their unrelated server block assertion.

## Local regression checks

`ShaderChecksTest` calls the actual scenario helpers using synthetic 640x480 PNGs
and a `Driver` test double. It never invokes a native game callback.

| Revision under test | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Before the two fixes, with the new regression tests | 13 | 9 | 0 | 0 |
| After the fixes | 13 | 0 | 0 | 0 |

The nine initial failures reproduced all six partial-marker cases, both
simultaneous-cleanup cases (runtime exceptions and errors), and loss of the
shader restoration error beneath an original scene failure. The passing checks
also verify full presence/absence, individual cleanup failures, both restoration
attempts and exception identity/suppression order.

Raw JUnit XML is retained locally at
`build/reports/p2-regressions-before.xml` and
`build/reports/p2-regressions-after.xml`. The current JUnit HTML report is
`build/reports/tests/test/index.html`.

Repository `:spotlessJavaCheck` also passed. Existing pending files outside
`integration/iris` were checked against their pre-change hashes and left unchanged.

## Real clients

Environment: Windows, JDK 25.0.2, Gradle 9.5.0, Minecraft 26.2, Fabric Loader
0.19.5, Loom 1.17.11, Iris **1.11.4+mc26.2**, Sodium **0.9.2+mc26.2**, and Sodium's
required Fabric API modules from 0.161.0+26.2. Actual GL queries reported
**Intel(R) UHD Graphics**, vendor Intel, OpenGL
`3.3.0 - Build 32.0.101.7088`.

After the local tests passed, `mineDriverMatrix` ran both clients sequentially
and exited successfully. Both reports have new run IDs matching their launch
pointers, start after this invocation began, and have `complete=true` and
`status=PASS`. The existing Gradle verifier also requires successful client exits.

| Task | Fresh run ID | Result |
| --- | --- | --- |
| `mineDriverActiveCheck` | `1792692672457-134e2336-37e4-4049-b663-7f2d1939a9b1` | PASS |
| `mineDriverCompileFailureCheck` | `1792692705033-f51989b1-24e8-43ae-b71a-655d08aee31f` | PASS |

| Capture | Green match | Magenta match | Active | Fallback |
| --- | ---: | ---: | --- | --- |
| `shaders-off` | 0% | 0% | false | false |
| `shaders-active` | 100% | 100% | true | false |
| `before-compile-failure` | 100% | 100% | true | false |
| `compile-failure-fallback` | 0% | 0% | false | true |
| `recovered-active` | 100% | 100% | true | false |

The negative scenario observed an actual
`net.irisshaders.iris.gl.shader.ShaderCompileException` naming
`MINEDRIVER_EXPECTED_COMPILE_FAILURE`. Shaders were still requested on, while
Iris reported inactive shaders, explicit fallback and `VanillaRenderingPipeline`.
The shared required-active assertion rejected that state with `ASSERTION_FAILED`;
reloading the valid pack restored both pixel markers. This scenario's PASS means
the failure and its rejection were verified, not that the broken pack was accepted.

Both runs verified that shader activation/configuration and HUD visibility were
restored to their initial off/visible state, then disconnected their owned worlds
and exited. Neither trace contains `world.fixture` or `world.snapshot`.

Per-run evidence is under `build/reports/minedriver/<task>/<runId>/`:

- `result.json`, `junit.xml`, `report.html` and `client.log`;
- `shader-inputs.json` with runtime, GPU identity and input hashes;
- game-frame PNGs and paired UI JSON in `screenshots/`;
- per-capture JSON with pipeline state and both marker fractions;
- `compile-failure.json`, `expected-rejection.json` and `restored.json` as applicable.

The active and fallback game-frame PNGs were visually inspected. No native
window activation or desktop capture was needed. A compact local summary is
`build/reports/p2-real-clients.json`.

## Reproduce and limits

Set the two local JAR paths as described in [README.md](README.md), then run from
the MineDriver repository root:

```powershell
.\gradlew.bat -p integration/iris test "-PirisJar=$irisJar" "-PsodiumJar=$sodiumJar"
.\gradlew.bat -p integration/iris mineDriverMatrix "-PirisJar=$irisJar" "-PsodiumJar=$sodiumJar"
```

The simultaneous restoration failures are injected JVM regressions, not native
thread-failure tests. These checks do not promise restoration after a stalled
game thread, whole-session restoration in interactive use, third-party shader
compatibility, renderer geometry correctness or temporal convergence. The separate
required-active fault-injection command in the README was not rerun in this
follow-up; the two real-client runs above are the fresh evidence for this revision.
