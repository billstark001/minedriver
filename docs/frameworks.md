# Native framework integration

MineDriver uses the test lifecycle of the existing loader/build plugin. It can orchestrate tasks and validate their newly produced JUnit XML; the agent is **not** injected into native framework tasks. Server tests should continue to use GameTest/FML/JUnit assertions directly, while UI scenarios use the agent-launched client.

The upstream configuration contracts are documented in [Fabric automated testing](https://docs.fabricmc.net/develop/automatic-testing), [ModDevGradle JUnit support](https://github.com/neoforged/ModDevGradle#unit-testing-with-junit) and [NeoForge's test framework](https://github.com/neoforged/NeoForge/blob/26.2.x/docs/TESTFRAMEWORK.md).

## Fabric GameTest

The included [Fabric integration project](../integration/fabric/build.gradle) pins Loom 1.17.11, Minecraft 26.3, Loader 0.19.5 and Fabric API 0.161.0+26.3. It creates Fabric's gametest source set, registers a native test that sets/asserts a gold block, and sets an explicit XML report:

```groovy
fabricApi.configureTests {
    createSourceSet = true
    modId = 'minedriver_fixture'
    enableClientGameTests = false
    enableGameTests = true
}
loom.runs.gameTest {
    systemProperties.put('fabric-api.gametest.report-file',
        layout.buildDirectory.file('native-gametest.xml').get().asFile.absolutePath)
}
mineDriver {
    frameworkTasks.add('runGameTest')
    frameworkReports.from(layout.buildDirectory.file('native-gametest.xml'))
}
```

Run from the MineDriver checkout:

```powershell
.\gradlew.bat -p integration/fabric mineDriverFrameworkCheck
```

The native report contains two passing tests in the recorded run: the explicit fixture test and Fabric's native verification test. The source-set metadata is Fabric's test subject, and is not packaged in any MineDriver artifact.

## NeoForge native JUnit

The [NeoForge JUnit project](../integration/neoforge-tests/build.gradle) uses ModDevGradle 2.0.147's native `unitTest` support and a metadata-only test subject. Its real Minecraft registry/block-state assertions run in the FML test classloader:

```groovy
neoForge {
    version = '26.2.0.88'
    mods { minedriver_fixture { sourceSet sourceSets.main } }
    unitTest {
        enable()
        testedMod = mods.minedriver_fixture
    }
}
tasks.named('test') { useJUnitPlatform() }
mineDriver {
    frameworkTasks.add('test')
    frameworkReports.from(layout.buildDirectory.file(
        'test-results/test/TEST-example.NativeRegistryTest.xml'))
}
```

Use your existing tested mod and native test dependencies in a real project. Registry bootstrap alone is not a loaded world/resource environment: for example, creating an ItemStack in 26.2 also requires bound item components. The fixture deliberately asserts registry identities/block states; loaded-world inventory assertions are covered by the client/server scenario.

```powershell
.\gradlew.bat -p integration/neoforge-tests mineDriverFrameworkCheck
```

One native test passes in the recorded run. NeoForge GameTest server tasks and its ephemeral-server/JUnit extensions can use the same exact-report mechanism once configured using the native framework. They are not separately executed by this snapshot's integration fixture.

## Report contract

Configure `frameworkTasks` with tasks in the current subproject and `frameworkReports` with **exact, disposable generated XML files**, not directories, glob patterns or hand-authored files. Before running, MineDriver removes only those report files and forces the configured tasks to execute again. Thus a prior passing XML cannot conceal a missing current run.

Each XML is parsed with DTD/external entities disabled. MineDriver counts native `testcase` elements, failures/errors and skipped cases, writes `build/reports/minedriver/framework-summary.json`, and rejects missing reports, zero tests, all skipped or any failure. A native task's failure also propagates directly through Gradle. Framework task prerequisites and source sets remain the loader's responsibility.

You can opt into your normal verification graph explicitly:

```groovy
tasks.named('check') { dependsOn tasks.named('mineDriverFrameworkCheck') }
```

For a suite with many dynamic JUnit XML filenames, point the native test runner at a stable aggregate JUnit XML or maintain an explicit known list. This version intentionally does not infer deleted/stale reports from a glob after execution.
