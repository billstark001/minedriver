# Iris scenarios using the existing MineDriver API

This separate Fabric build exercises **Minecraft 26.2, Fabric Loader 0.19.5,
Iris 1.11.4+mc26.2 and Sodium 0.9.2+mc26.2**. It requires JDK 25 and a working
OpenGL display. Root `check` does not launch it.

The two scenarios use only `Scenario`, `DriverExtension`, `ProbeContext`, ordinary
commands and the existing profile matrix. There is no new execution model,
public API, core Iris dependency or production mod initializer. Scenario and
extension sources are in `minedriver`; local JUnit regressions are in `test`.
Only Sodium's required Fabric API
modules are loaded, avoiding unrelated GameTest initialization.

## Run

Supply local JARs from an instance you own. Only those two named files are copied
to each disposable game directory; the source instance is never launched or
modified. The JARs are not included in this repository or its distributions.

From the MineDriver repository root:

```powershell
$irisJar = 'C:/path/to/iris-fabric-1.11.4+mc26.2.jar'
$sodiumJar = 'C:/path/to/sodium-fabric-0.9.2+mc26.2.jar'
.\gradlew.bat -p integration/iris mineDriverMatrix "-PirisJar=$irisJar" "-PsodiumJar=$sodiumJar"
# Individual checks; each starts its own isolated client:
.\gradlew.bat -p integration/iris mineDriverActiveCheck "-PirisJar=$irisJar" "-PsodiumJar=$sodiumJar"
.\gradlew.bat -p integration/iris mineDriverCompileFailureCheck "-PirisJar=$irisJar" "-PsodiumJar=$sodiumJar"
```

`mineDriverCheck` also runs the active scenario. Development/version/input guards
remain enabled. No runtime or production override is needed.

## What is asserted

- **Active:** create an isolated flat world. Capture shaders off, enable the valid
  pack, require the requested name, actual `IrisApi.isShaderPackInUse()`, no
  fallback and no reload error, then check
  the pack's green/magenta final-pass marker in the game-frame PNG.
- **Compile failure:** first prove the valid pack renders, then select the invalid
  pack. Require shaders still requested on, an actual `ShaderCompileException`
  naming `MINEDRIVER_EXPECTED_COMPILE_FAILURE`, vanilla fallback and inactive
  shaders. The **same active-pack assertion** must reject that state. The old
  marker must disappear, and reloading the valid pack must restore it.

The compile-failure scenario returns PASS only when all of those negative-case
assertions succeed. It does not convert arbitrary errors or timeouts to success.
To prove a required-active finite check cannot silently pass after fallback:

```powershell
# Expected nonzero Gradle exit and complete FAIL / ASSERTION_FAILED result:
.\gradlew.bat -p integration/iris mineDriverActiveCheck "-PirisJar=$irisJar" "-PsodiumJar=$sodiumJar" -PactivePack=minedriver-invalid
```

The tiny packs are project-owned test fixtures. The valid pack preserves the
world on the left and writes a fixed two-color marker on the right. Its region
predicates require at least 99% coverage in each region when present and at most
1% residue in **each** region when absent. Residue above that limit fails even
when the other region is empty. These predicates are not a shared color baseline
for arbitrary third-party shader packs. The invalid fragment is
otherwise valid but references an undefined identifier, triggering GLSL compiler
failure rather than a missing-file or preprocessor error.

`IrisExtension` registers just `custom.iris.status` and `custom.iris.select`.
It uses the public API for actual activation and pins version-specific Iris
configuration/reload/fallback access to this fixture. Iris 1.11.4's error getter
consumes the error and does not retain in-world failures. A scoped Iris logger
appender captures the actual throwable during each reload and is removed before
the command returns; status reads only the cached diagnostic and is repeatable.
Native GL compilation occurs in Iris's render-thread reload; all condition/frame
waits and image/file work stay on the scenario worker.
Both scenarios restore the initial shader selection and GUI visibility through a
scope-owned cleanup object. Both restoration steps are attempted independently;
if both fail, the shader restoration error retains the HUD restoration error as
suppressed. An original scene failure remains primary, with that cleanup error
chain attached. Failed cleanup cannot write a successful `restored.json`.
All configuration files and worlds belong to the isolated run. Explicit world
disconnection is on the successful path; finite checks stop their owned client
on failure. This fixture does not promise whole-session restoration after a
stalled game thread.

## Local regression checks

```powershell
.\gradlew.bat -p integration/iris test "-PirisJar=$irisJar" "-PsodiumJar=$sodiumJar"
```

The 13 JUnit checks exercise the actual `ShaderChecks` helpers with synthetic PNGs
and a `Driver` test double; no game or native callback is started. They cover a
nearly complete residual marker (100% green / 98.61% magenta and its converse),
each color remaining alone, small residues in either region, full presence and
absence, simultaneous cleanup exceptions/errors, preservation of the original
scene failure, and individual cleanup failures. Native shader behavior still
requires the two real-client scenarios above.

Run IDs, observed pixels and test results for this revision are in
[VALIDATION.md](VALIDATION.md).

## Evidence and limits

Each task writes its normal reports under
`build/reports/minedriver/<task>/<runId>/`, including:

- `result.json`, `junit.xml`, `report.html` and `client.log`;
- game-frame PNGs and paired UI JSON under `screenshots/`;
- `shader-inputs.json` with runtime/mod versions, actual GL vendor/renderer/version
  and SHA-256 hashes of the copied mods, packs and configuration;
- per-capture state, frame interval metadata and marker fractions;
- `compile-failure.json` and `expected-rejection.json` in the negative case.
- `restored.json` confirming the initial shader policy/activation and HUD visibility
  after both success and assertion failure.

Acceptance still requires the matching current run ID, `complete=true`,
`status=PASS` and successful Gradle/client exit. Screenshot callback frame
intervals are not exact GPU pixel timestamps. These checks establish activation,
compiler-failure detection, final output, repeatable diagnostics and recovery for
this pinned combination; they do not establish renderer-mod geometry correctness,
third-party pack compatibility, temporal convergence or minimized rendering.
