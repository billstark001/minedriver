# Compatibility and validation

Recorded on **2026-10-06**, Windows, JDK 25.0.2 and Gradle 9.5.0. These are tested combinations, not a claim about all loader releases or operating systems. Tool version is `0.1.0-SNAPSHOT`.

## Real game checks

| Target | Versions | Validated behavior |
| --- | --- | --- |
| Music Control / Fabric | Minecraft 26.3, Loader 0.19.5, Loom 1.17.11, Fabric API 0.161.0+26.3 | Title/options input and PNG capture; Mod Menu/Cloth settings in `en_us`, `zh_cn`, `zh_tw`, `ja_jp`; resource reload; flat fixture with position/camera/blocks/inventory; frame/GC/server profile and real JFR artifact; save/disconnect. |
| World Mirror / Fabric | Minecraft 26.2, Loader 0.19.5, Loom 1.17.11, Fabric API 0.152.2+26.2 | Native title/options screen input, screenshots and private GLFW keyboard callback; core UI startup with the optional Xaero bridge disabled. |
| Native NeoForge client | Minecraft 26.2, NeoForge 26.2.0.88, FML 11.0.16, ModDevGradle 2.0.147 | Title/options, native built-in settings factory, Java extension screen, flat world creation, authoritative gold-block/stone-stack assertions and normal command packet send. |
| Native NeoForge client | Minecraft 26.3, NeoForge 26.3.0.48-beta, FML 12.0.8, ModDevGradle 2.0.147 | Same settings/fixture flow; stable-ID button semantic activation and native input separately asserted; packet counters and save/disconnect. |
| Fabric native GameTest | Minecraft 26.3 / versions above | Native gold-block assertion plus framework verification: **2 tests, 0 failures, 0 skipped**, fresh XML checked. |
| NeoForge native JUnit | Minecraft 26.2 / versions above | Real FML-loaded registry/block-state assertions: **1 test, 0 failures, 0 skipped**, fresh XML checked. |

Native NeoForge client fixtures have no `@Mod` class or tool-mod metadata. Native framework fixtures use their framework's own test source set/test-subject metadata. Tests do not publish or modify the tested mods.

The cross-platform verification workflow runs root `check build` on Windows and Ubuntu, with a simulated client rather than a GPU. The earlier Gradle/CLI implementation commit passed both jobs. Refer to [CI](https://github.com/billstark001/minedriver/actions/workflows/ci.yml) for the status of the current commit.

## Reproduce from this repository

```powershell
.\gradlew.bat check build :cli:installDist
.\gradlew.bat -p integration/neoforge mineDriverCheck
.\gradlew.bat -p integration/neoforge '-PneoVersion=26.2.0.88' '-Pplan=../../examples/plans/neoforge-settings.json' '-Pprobe=fixture' mineDriverCheck
.\gradlew.bat -p integration/neoforge '-Pplan=../../examples/plans/neoforge-settings.json' '-Pprobe=fixture' mineDriverCheck
.\gradlew.bat -p integration/fabric mineDriverFrameworkCheck
.\gradlew.bat -p integration/neoforge-tests mineDriverFrameworkCheck
```

The native projects are intentionally separate builds with an included-build plugin dependency, matching a consuming mod project. They are not automatically run by root `check`.

`publishToMavenLocal` also succeeds. A separate consuming project resolved the versioned plugin marker from `mavenLocal()` (without `includeBuild`) and completed a forked simulated-client input/screenshot check. The agent's Maven publication intentionally uses a dependency-free POM for its standalone shaded JAR rather than thin-component Gradle module metadata.

For the existing sibling mods, build `:gradle-plugin:jar` first and use the opt-in [external init script](../integration/external.init.gradle). It configures the build in memory and leaves its tracked build scripts unchanged. From the respective mod checkout:

```powershell
# Music Control: its ordinary world-creation test excludes native GameTest modules explicitly.
.\gradlew.bat -I D:/Programs/minedriver/integration/external.init.gradle '-Dminedriver.target=fabric-26.3' '-Dminedriver.plan=D:/Programs/minedriver/examples/plans/music-control.json' '-Dminedriver.disableGametest=true' :versions:fabric-26.3:mineDriverCheck

# World Mirror: omit the unrelated, unresolved map bridge for core UI validation.
.\gradlew.bat -I D:/Programs/minedriver/integration/external.init.gradle '-Dminedriver.target=fabric-26.2' '-Dminedriver.disableMaps=true' :versions:fabric-26.2:mineDriverCheck
```

Set `-Dminedriver.root=<checkout>` and `-Dminedriver.plan=<plan>` when using another location; `minedriver.target` selects the project name. Quote complete `-D...` arguments in PowerShell. Inspect your checkout's project paths before copying qualified task names.

## Automated regression coverage

### 2026-10-16 Latticium follow-up

Latticium's NeoForge 26.3 client exposed a readiness race: `world.create` returned a live player/level while `LevelLoadingScreen` was still present, and an immediately dispatched F8 shortcut was ignored. The driver now waits for loading-screen and resource-overlay dismissal. A simulated regression covers partial world initialization, loading screens and overlays; the real rerun passed native F8, pause/resume, screen-focus shortcut exclusion, task/diagnostic navigation and Cloth settings in `en_us`, `zh_cn`, `zh_tw` and `ja_jp` at a 640×480 physical / 320×240 GUI viewport. The fresh successful Latticium run is `1792077668279-fe3177ec-d3bf-4bfe-8e17-bace843c33ec`. The corresponding earlier failure is retained as `1792077370531-00807e84-99bd-4f49-b62b-b39719184285`. Multiplayer connection uses the same readiness predicate but dedicated-server validation remains open.

Latticium's NeoForge 26.2 / 26.2.0.88 fixture also reproduced a developer warning about Cloth Config 26.2.155's `logoFile` metadata. Before the fix, run `1792078106408-8bcf3cc5-e62e-4ebf-8c2c-220510695756` waited for the 600-second watchdog without starting its scenario. The corrected default rejected it promptly with `STARTUP_WARNINGS` in `1792078700545-f150519b-e738-4b96-8d6d-e1e572b823a7`, retaining native issue details and a screenshot. Exact acceptance of that affected-mod/key pair then passed the gameplay and four-language UI flow in `1792078763097-fb0f3bf8-003e-4789-a172-127a66adb78e`. The native continue label is a literal Component generated with `fml.button.continue.launch`; the old `fml.button.proceed` key selector did not work. Rejected unknown warnings and fatal errors also have deterministic unit coverage.

An additional Forgematica run exposed a loader-handled crash before the first completed frame: MaFgLib's unsynchronized initialization-handler list failed during parallel mod construction, and the loader wrote a native crash report without invoking the agent's uncaught handler. Startup now detects fresh client crash reports in the isolated run directory and reports `STARTUP_CRASH`; older seeded reports are ignored. A forked simulated-client test deliberately writes a handled crash report and never renders a frame, proving this path terminates without the session watchdog. The consuming bridge repairs the separate source-library registration race.

The follow-up `check build :cli:installDist` passed with **28 tests, zero failures/errors/skips**, covering:

- Typed/ambiguous reflection, public visibility bridges and exact private native callbacks.
- Authenticated RPC, malformed envelopes, unknown commands and recorded RPC steps.
- Strict configuration types, invalid paths/connections and atomic JSON identity.
- Queued game actions skipped after timeout; extension results reject cycles/live objects/non-finite values.
- Frame-hook selection/idempotence, unsupported signatures and bounded frame snapshots.
- Visual mismatch/masking/diff artifacts; escaped failure XML and complete result identity.
- MCP initialization, input schemas, clean stdio and tool/protocol errors.
- Full forked agent/client/Gradle lifecycle, native-shaped input/screenshots/frame waiting, no duplicate source launch.
- Zero client exit cannot hide assertion failure; asynchronous scenario failure fails interactive close.
- Scenario classes excluded from main JAR, independent matrix sessions, basic configuration-cache reuse.
- Fresh native reports replacing stale PASS and rejection of all-skipped results.

The test-support client does not render or load real mods; real checks above establish the actual game behavior.

## 0.2.0 release verification (2026-10-16)

After reviewing and dating the changelog, the version was advanced to 0.2.0. `format check build :cli:installDist` passed with 28 tests and zero failures/errors/skips. The release collector produced five versioned JARs and both CLI distribution archives, with SHA-256 checksums. Agent inspection and MCP identification now report 0.2.0; the consuming-project init script selects the matching versioned plugin/protocol artifacts. Earlier native evidence above retains the snapshot version actually used. See [release instructions](releasing.md) for distribution and publication boundaries.

## Resolved issues and remaining boundaries

The agent relocates ASM/Gson because a standalone unrelocated ASM copy on the Java-agent system classpath conflicts with Fabric's discovery. Launch cloning preserves NeoForge's deferred classpath and Loom's native launch prerequisites; missing those previously prevented actual client startup. NeoForge settings use the native factory lookup including its built-in config fallback. Input names resolve against each runtime because GLFW and SDL numeric codes differ. Reflection retains visibility bridges and explicitly handles 26.2's private keyboard callbacks.

Startup avoids initializing Minecraft on an agent thread; checks suppress Fabric's Swing failure dialog; lifecycle/watchdog outcomes and run-ID verification prevent missing/stale reports or zero exits from becoming false passes. Screenshots remove a prior same-name capture before calling the native asynchronous API. Profiling snapshots fix a concurrent-frame accounting error and report ring-buffer overflow explicitly. Integrated-server disconnect waits for saving/stopping before completion.

Two external test conditions remain explicit:

- Music Control's ordinary-client world creation with the tested Fabric API's GameTest modules loaded without a configured test run hit a native dynamic-test initialization error. That fixture opts into `fabric.debug.disableModIds` for `fabric-gametest-api-v1` and `fabric-client-gametest-api-v1`. MineDriver does not automatically disable them; the correctly configured native GameTest project runs them successfully.
- World Mirror's optional Xaero bridge declared a required map dependency absent in that checkout. Core UI validation disables `xaero_world_map_bridge`, `xaeroworldmap` and `xaerominimap`; map behavior is outside this test.

Production/unclassified runtimes and unknown versions require explicit opt-ins and remain experimental. Dedicated-server connection, OS input/focus behavior, GPU timing, older obfuscated versions, hot reload and broad live-launch configuration-cache compatibility are not claimed. Native UI flags do not capture arbitrary custom-rendered controls or occlusion. Stable visual baselines require controlled resources/language/layout/world conditions and review.
