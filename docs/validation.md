# Compatibility and validation

Validation records cover **2026-10-06 through 2026-10-18** on Windows, JDK 25.0.2 and Gradle 9.5.0. These are tested combinations, not a claim about all loader releases or operating systems. The original October 6 matrix used `0.1.0-SNAPSHOT`; The initial October 18 follow-up used development changes after `0.2.0`; final `0.3.0` verification is recorded separately below.

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

### 2026-10-18 input, geometry and lifecycle follow-up

`format check build :cli:installDist` passed with **41 tests, zero failures/errors/skips**. Added coverage includes real transformed JVM input polling, callback filtering and stale queue generations, held-input quarantine, Cloth header geometry and clipping, native focus overriding stale client focus caches, missing-hook cleanup, shared deadlines/cancelled queued mutations, loading warnings behind overlays, unknown-screen timeout evidence, and cached HTTP status while four action requests are blocked.

Fresh real-client checks below each have a successful Gradle exit and a matching `runId`, `complete=true`, `status="PASS"` result. Artifacts are under `integration/<loader>/build/reports/minedriver/mineDriverCheck/<runId>/`.

| Runtime and configuration | Run ID | Assertions |
| --- | --- | --- |
| NeoForge 26.3.0.48-beta, Minecraft 26.3, Cloth 26.3.159; automatic session isolation | `1792263354385-c8415684-9e75-4068-be40-7d9b3a19df70` | Native nested Cloth header expand/collapse and idempotent semantic expansion; ignored external keyboard callbacks; virtual W polling; independent leases; unfocused ESC navigation; hidden-window world entry without pause; held W moves the authoritative server player; empty/nonempty slot geometry and server-confirmed pickup/replacement; native text/item bounds; paired screenshots. |
| NeoForge 26.2.0.88, Minecraft 26.2, Cloth 26.2.155; interactive-style leases, exact metadata warning acceptance | `1792263555600-071a49d1-6907-46a2-95c4-a0e8f1fd0678` | Same runtime scenario, plus last-lease expiry and reacquisition. The known `cloth_config` metadata warning is accepted only by its exact native identity in `fixtures/accepted-cloth26.2.json`. |
| Fabric Loader 0.19.5, Minecraft 26.3, Fabric API 0.161.0+26.3; automatic session isolation | `1792263486138-7c453415-8f9e-4375-9400-3f361b4ed334` | Native title/options input, required-hook installation and paired PNG/UI JSON capture. |

The default-reject NeoForge 26.2 check `1792261868723-f6be27e9-512d-4150-b4ac-785a094d85e4` correctly produced complete FAIL with `STARTUP_WARNINGS` and native warning details instead of waiting for the watchdog. Unknown warnings, fatal issues and overlay coexistence also have deterministic unit coverage. The unified observer replaces the previous separate readiness loops; the broad warning override and old UI command are removed.

Reproduce the new native scenarios:

```powershell
.\gradlew.bat -p integration/neoforge mineDriverCheck -Pprobe=runtime
.\gradlew.bat -p integration/neoforge mineDriverCheck '-PneoVersion=26.2.0.88' '-PclothVersion=26.2.155' '-PacceptedWarningsFile=fixtures/accepted-cloth26.2.json' -Pprobe=runtime -Pisolation=false
.\gradlew.bat -p integration/fabric mineDriverCheck
```

The focus fixture hides/restores only its owned Minecraft native window and asserts unchanged 640×480 physical / 320×240 GUI dimensions. During development, a second SDL test window caused the game's single-window event loop to misroute resize events and render a small duplicate picture in the upper-left. That fixture was removed; the corrected screenshots were visually inspected. Separate-process foreground activation attempts were unreliable under Windows foreground policy and were also removed. Failed fixture artifacts remain available, including `1792261542553-638c5294-f8db-4fdd-994b-b512410c3ef7` (SDL auxiliary-window resize) and `1792263428266-e642a359-5d14-402f-b241-8d8af90ba9c6` (GLFW retained focus on a hidden window). Focus observation now queries SDL/GLFW state and accounts for GLFW visibility rather than trusting Minecraft's initial cached focus value.

Both process diagnostics recorded the current user as owner/default token owner, with the administrator role disabled. No anomalous owner was reproduced. `filesystem-gradle.json` and `filesystem-client.json` retain before/after existence, resolved path, owner, ACL and process/token evidence; no permission repair is performed.

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

## 0.3.0 release review and verification (2026-10-18)

The four input/lifecycle commits were reviewed end to end before release. The review fixed held-key polling escaping quarantine after restoration, expiry being delayed until observer sampling, missed mouse-release state across queue generations, failed presses skipping release, and input remaining held on close without a lease. Lease/input boundaries now check expiry synchronously, transitions sample native keyboard and mouse state, and cleanup preserves the primary error.

Observer waits now have independent registrations, frame heartbeat comes from the completed-frame hook, and predicate failures or already-expired entries preserve evidence and restore deadline scope. Terminal status survives concurrent wait completion and report cleanup; cached environment/version metadata is retained without querying a stalled client. Finite and asynchronous scenarios share ownership and close guards. Constructor failures restore options and detach resources. Superseded state reads, setters, screenshot/FML probing, the unused agent framework mode, old UI alias and broad warning override are absent. Unknown fields/backends fail explicitly; the two supported native API adapters remain deliberate contracts.

The final root `check build :cli:installDist` passed **55 tests with zero failures/errors/skips**, including 14 new regressions since the initial 41-test follow-up. Build metadata is generated from the sole `gradle.properties` version, consumed by Agent/MCP identity and checked against Gradle in tests. The external init script's metadata-derived classpath also passed a real Gradle `help` invocation.

Fresh candidate client runs each completed with the matching run ID, complete PASS result and successful Gradle exit:

| Runtime | Run ID | Coverage |
| --- | --- | --- |
| NeoForge 26.3.0.48-beta / Cloth 26.3.159 | `1792265169336-d4547610-b124-45f5-882e-44b4cf0de43f` | Full runtime scenario with automatic isolation, native state polling/restoration, Cloth geometry, unfocused navigation/world movement and server-confirmed slot actions. |
| NeoForge 26.2.0.88 / Cloth 26.2.155 | `1792265218783-756efaa7-bdaf-4fdc-a546-359e9fe8236a` | Full runtime scenario with explicit leases/expiry, native restoration and exact diagnosed metadata warning acceptance. |
| Fabric Loader 0.19.5 / Fabric API 0.161.0+26.3 | `1792265270863-557b82c6-9846-4f8d-abe3-59f4a2df8af7` | Title/options native input and paired screenshot/UI capture; generated version identity and terminal cached environment. |

Default warning rejection was rerun as `1792265330294-9c2a69a6-b080-4b19-948d-46197ca112a4`: it correctly returned complete FAIL / `STARTUP_WARNINGS`, with Agent 0.3.0 and FAILED observer evidence. It did not reach scenarios. Fresh native framework invocations also passed: Fabric **2 GameTests**, NeoForge **1 JUnit test**, with zero failures/skips and newly generated reports.

Reproduction commands are the October 18 runtime commands above, plus `mineDriverFrameworkCheck` in `integration/fabric` and `integration/neoforge-tests`. The local release collector verifies the dated changelog and collects seven versioned distributions with `SHA256SUMS.txt`; generated protocol/standalone Agent/nested plugin metadata and CLI distributions are checked for the same configured version. The tag-driven Release workflow performs its own clean build and distribution collection. See [release and upgrade instructions](releasing.md).

## 0.2.0 release verification (2026-10-16)

After reviewing and dating the changelog, the version was advanced to 0.2.0. `format check build :cli:installDist` passed with 28 tests and zero failures/errors/skips. The release collector produced five versioned JARs and both CLI distribution archives, with SHA-256 checksums. Agent inspection and MCP identification now report 0.2.0; the consuming-project init script selects the matching versioned plugin/protocol artifacts. Earlier native evidence above retains the snapshot version actually used. See [release instructions](releasing.md) for distribution and publication boundaries.

## Resolved issues and remaining boundaries

The agent relocates ASM/Gson because a standalone unrelocated ASM copy on the Java-agent system classpath conflicts with Fabric's discovery. Launch cloning preserves NeoForge's deferred classpath and Loom's native launch prerequisites; missing those previously prevented actual client startup. NeoForge settings use the native factory lookup including its built-in config fallback. Input names resolve against each runtime because GLFW and SDL numeric codes differ. Reflection retains visibility bridges and explicitly handles 26.2's private keyboard callbacks.

Startup avoids initializing Minecraft on an agent thread; checks suppress Fabric's Swing failure dialog; lifecycle/watchdog outcomes and run-ID verification prevent missing/stale reports or zero exits from becoming false passes. Screenshots remove a prior same-name capture before calling the native asynchronous API. Profiling snapshots fix a concurrent-frame accounting error and report ring-buffer overflow explicitly. Integrated-server disconnect waits for saving/stopping before completion.

Two external test conditions remain explicit:

- Music Control's ordinary-client world creation with the tested Fabric API's GameTest modules loaded without a configured test run hit a native dynamic-test initialization error. That fixture opts into `fabric.debug.disableModIds` for `fabric-gametest-api-v1` and `fabric-client-gametest-api-v1`. MineDriver does not automatically disable them; the correctly configured native GameTest project runs them successfully.
- World Mirror's optional Xaero bridge declared a required map dependency absent in that checkout. Core UI validation disables `xaero_world_map_bridge`, `xaeroworldmap` and `xaerominimap`; map behavior is outside this test.

Production/unclassified runtimes and unknown versions require explicit opt-ins and remain experimental. Dedicated-server connection, other operating systems, direct device access by mods, GPU timing, versions outside the explicit matrix, production/intermediary names, hot reload and broad live-launch configuration-cache compatibility are not claimed. Standard Windows game-input isolation and background execution have the October 18 coverage above; it does not establish all OS focus transitions or minimized rendering behavior. Native UI flags and standard text/item render states do not capture arbitrary custom-rendered controls or occlusion. Stable visual baselines require controlled resources/language/layout/world conditions and review.

## Extended runtime matrix (October 18, unreleased)

The compatibility fixtures in `integration/compatibility` exercise working-tree changes after 0.3.0 using JDK 25.0.2, Gradle 9.5.0, Loom 1.17.11 / Fabric Loader 0.19.5 or ModDevGradle 2.0.147. Their shared scenarios compile against each actual runtime. No production or unsupported-version opt-in is enabled.

Each run asserts title/options navigation, semantic activation, native character input into an EditBox, external ESC filtering, virtual key polling/release, complete version-specific isolation hooks, rendered text/item geometry, validated PNG plus UI capture, Chinese/English resource reload, actual hidden-window focus loss, background server-confirmed movement, fixture blocks/inventory, server-confirmed slot pickup/replacement, world saving/disconnection, and native/semantic Cloth category expansion. Cloth versions are 15.0.140, 21.11.153, 26.1.154, 26.2.155 and 26.3.159 respectively. The NeoForge 26.2 row accepts only the already diagnosed exact Cloth metadata warning from `integration/neoforge/fixtures/accepted-cloth26.2.json`.

Run the matrices sequentially from the repository root:

```powershell
.\scripts\verify-compatibility.ps1 -Loader fabric
.\scripts\verify-compatibility.ps1 -Loader neoforge
# Optional subset:
.\scripts\verify-compatibility.ps1 -Loader fabric -MinecraftVersions '1.21.1','1.21.11','26.1.2'
```

The runner checks a successful Gradle/client exit and a matching run ID with `complete=true` / `status=PASS`, and writes summaries/logs under `build/reports/minedriver/compatibility/<loader>/`. Per-run reports and screenshots remain in each fixture's `build/reports/minedriver/mineDriverCheck/<runId>/`. Run root builds and composite fixtures sequentially because they share compiler outputs; the matrix runner rejects overlapping matrix processes.

The original older Fabric matrix uses `net.fabricmc.fabric-loom-remap` and `loom.officialMojangMappings()`, following the [upstream Loom plugin distinction](https://docs.fabricmc.net/develop/loom/#plugin-ids). The additional Yarn validation below uses the same plugin with the project's native Yarn mappings. Production/intermediary runtimes are not adapted. Minecraft 1.21.1's rendered geometry comes from its immediate GUI calls and current pose/scissor; later versions use extracted native render states. Legacy Cloth entries lacking a native layout rectangle retain null layout bounds; category hit rectangles and native child-widget geometry remain available.

<!-- compatibility-matrix-runs -->

| Minecraft | Fabric run ID | NeoForge | NeoForge run ID |
| --- | --- | --- | --- |
| 1.21.1 | `1792320173199-0d8756b8-4ef9-4ad0-87df-4fa2bc5d0d7a` | 21.1.262 | `1792320417576-3ba05753-654c-4f60-aeaa-419a074dee58` |
| 1.21.11 | `1792320210292-4b44f1ce-d4ca-4c96-a4fd-19908441acd1` | 21.11.44 | `1792320595762-d34ad897-48e0-4106-8868-ecd14a4fcbd9` |
| 26.1.2 | `1792320262688-397b410a-2d8e-446b-813a-c627d2f69a78` | 26.1.2.87 | `1792320777651-7512fe57-4dbe-44ee-8676-88c648280fd4` |
| 26.2 | `1792320313773-6d17ecc2-eedc-4e57-936f-edcd78e42c19` | 26.2.0.88 | `1792320821669-16974c1f-ced5-40f8-bd2a-b66236bbac65` |
| 26.3 | `1792320366730-96cb9a72-d363-4831-8677-647e2fef3fb4` | 26.3.0.48-beta | `1792320875451-2743da0a-374b-4b8d-ab94-3e9626cb549a` |

All ten runs completed with matching fresh PASS results and successful process exits. The final root `check build` passed all 64 JVM tests with zero failures/errors; regression coverage includes legacy callback verification/quarantine, chopped polling frames, remapped covariant methods, DevLaunch argument expansion and native launcher environment preservation.

<!-- /compatibility-matrix-runs -->

## Yarn development mappings (October 18, unreleased)

After the runtime-extension commits, Fabric 1.21.1 and 1.21.11 additionally passed with their native Yarn mappings. The fixtures compile the separate `src/yarn/java` scenarios directly against `MinecraftClient`, `TextFieldWidget`, `InputUtil` and the version's native keyboard callbacks. They run the same input, UI/render, resource, background movement, world/inventory and Cloth assertions as the original matrix, plus checks for an active `named` bridge, actual Yarn class names and canonical Mojang type selectors. Development/version/input guards remain enabled. The generated mapping data stays under the consuming fixture's build directory.

```powershell
.\scripts\verify-compatibility.ps1 -Loader fabric -Mappings yarn
# Single project/version:
.\gradlew.bat -p integration/compatibility/fabric mineDriverCheck -PmcVersion=1.21.11 -Pmappings=yarn
# Rebuild the mapping bridge offline after its official mappings have been cached:
.\gradlew.bat -p integration/compatibility/fabric mineDriverMappings -PmcVersion=1.21.11 -Pmappings=yarn --offline --rerun-tasks
```

The matrix runner verifies the requested mapping namespace as well as runtime/version, matching run ID, complete PASS and successful process exit. Yarn summaries/logs are separate at `build/reports/minedriver/compatibility/fabric/yarn/`; Mojang summaries remain under `fabric/`. Screenshots and paired UI JSON are in each run's `screenshots/` directory.

| Minecraft | Yarn | Fabric Loader | Cloth | Complete PASS run ID |
| --- | --- | --- | --- | --- |
| 1.21.1 | 1.21.1+build.3 | 0.19.5 | 15.0.140 | `1792323408978-59682c2a-6a3b-4a7d-afbd-64e08cdb2ca7` |
| 1.21.11 | 1.21.11+build.6 | 0.19.5 | 21.11.153 | `1792323502941-3144fb16-9bc5-43ae-b517-cea7f9b8a9d1` |

The JVM regressions additionally cover canonical reflection and mod overrides, round-trip frame/polling transformation without changing the runtime namespace, long window-handle locals, descriptor/inherited Tiny joins, identity mappings without a download, and rejection of a tampered cache in offline preparation.

Additional Mojang-name regressions completed with fresh matching PASS results and successful process exits:

| Loader | Minecraft | Complete PASS run ID |
| --- | --- | --- |
| Fabric | 1.21.1 | `1792323725959-4231f6b3-3183-49f4-aa30-2d65115f06a1` |
| Fabric | 1.21.11 | `1792323759644-6f017087-db0c-415a-a7d1-c62f627f04a1` |
| Fabric | 26.1.2 | `1792323796534-582a831a-91bd-4e66-8d08-df1dc7fdebea` |
| Fabric | 26.2 | `1792323847715-0b7a14bd-da2f-4e75-b55d-749c3d34df8d` |
| Fabric | 26.3 | `1792324407909-1b01dae4-80b6-4cb7-a548-cb44124c790f` |
| NeoForge | 1.21.1 | `1792324479553-4b0fcc1b-0ece-49c9-99d0-59217d5c886a` |
| NeoForge | 26.3 | `1792324530664-8ef54f12-a2a5-413a-beff-4260440703c4` |

During this regression, Fabric 26.3 runs `1792323894824-476de4a7-4a62-4309-8671-3440c46ee03f`, `1792323978146-3a8086d1-b030-44ee-b368-9eaecec04487` and `1792324334329-b32fda50-6791-425b-8182-d30e1ae7e4d7` exited during resource loading without a result. Windows Application Error events recorded native access violations (`0xc0000005`, unknown module / `jvm.dll`); these attempts remain failures. A control using the prior agent passed (`1792324235614-55bd3be2-5083-4c67-ab66-87f25eba94fd`). The implementation now keeps Mojang identity names on the original direct transformer/reflection/callback paths; the subsequent Fabric and NeoForge 26.3 runs above passed. The exact native crash mechanism was not established; the successful reruns do not constitute a general JDK/SDL stability guarantee. Per-attempt client logs remain under their run IDs.

The actual 1.21.11 Yarn bridge also regenerated successfully with `--offline --rerun-tasks` using its verified cached official mappings.

The final root `format check build` passed all 68 JVM tests with zero failures/errors. No mapping data or connection secrets are included in the commits.
