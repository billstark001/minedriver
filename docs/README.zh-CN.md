# MineDriver

MineDriver 是 Minecraft mod 开发和调试工具，主体为 **Gradle 插件 + Java Agent**，包名和插件 ID 均为 `io.github.billstark001.minedriver`，版本统一由 [gradle.properties](../gradle.properties) 定义并生成运行时元数据。

它克隆已有 Loom / ModDevGradle 的客户端启动配置，保留依赖准备、classpath、参数和环境，在独立游戏目录中注入 Agent。工具本身没有 mod 元数据或初始化器。原生框架示例的元数据仅属于框架的测试对象。

面向 agent 的 [minedriver 技能](../.agents/skills/minedriver/SKILL.md) 位于 `.agents/skills/minedriver`，涵盖客户端观察、UI / 世界复现、回归测试和失败诊断。见 [技能安装和使用指引](skills.md)。

## 当前能力

| 功能 | 接口 |
| --- | --- |
| 游戏状态和完整 UI 快照 | `session.inspect`、`ui.snapshot`；控件、Cloth 分类、槽位、文字/物品绘制边界、帧号和屏幕标识 |
| 控件操作 | 精确选择翻译键、标签、类型、路径或稳定 ID；歧义、禁用、隐藏时明确报错 |
| 设置入口 | Fabric Mod Menu、NeoForge 设置工厂、Cloth AutoConfig；不自动安装依赖 |
| 输入与隔离 | GUI 点击/滚动、键盘 TAP/PRESS/RELEASE、字符输入；外部输入屏蔽租约、虚拟状态查询和独立后台策略 |
| 阻塞和目录诊断 | `agent.status` 缓存状态、真实焦点/暂停、心跳和失败阶段；Gradle/客户端分别记录所有者、ACL 和身份 |
| 截图和多语言 | 完整帧屏障、异步 PNG、裁剪/遮罩/容差比较、差异图、资源重载等待 |
| 世界夹具 | 独立单人世界、方块/背包/位置设置、本地服务端权威快照 |
| 命令、网络和性能 | Brigadier、普通客户端命令包、实验性连接、帧/GC/堆/tick/JFR |
| 自动化与扩展 | JSON 计划、Java 场景、`custom.*` 命令、稳定 ID、CLI、MCP tools |
| 原生框架与报告 | Fabric GameTest / NeoForge JUnit；新生成的 XML、JSON/HTML 报告、失败截图和日志 |

`ui.snapshot` 是唯一 UI 检查接口，返回对象：`elements` 为原生控件树，`slots` 包括空容器槽位，`render.draws` 提供标准 GUI 文字和物品的实际绘制边界。控件区分 `layoutBounds`、`hitBounds`、`clippedBounds`，未知范围为 null；裁剪信息不保证没有遮挡。Cloth 分类暴露 `expanded`，可用 `ui.expand` 幂等设置，也可通过原生标题点击验证状态变化。点击可携带 `screenId` 拒绝过期屏幕，槽位点击使用当前菜单的 `slot` 编号。

检查模式默认屏蔽该实例的标准外部输入；交互模式通过 `input.isolation.acquire/renew/release/status` 管理租约；输入入口会立即检查到期，恢复后长按隔离也覆盖按键状态轮询。后台策略暂时关闭失焦暂停，自动输入回调内使用虚拟焦点，状态观察仍返回真实焦点。按下回调失败也会尝试释放，关闭时清理无隔离租约的自动输入。关闭后台策略后，失焦输入明确返回 `FOCUS_LOST`。直接读取原生设备的 mod 不在标准路径隔离保证内；最小化后没有渲染帧时，截图和帧等待仍会有界失败。

## 快速运行

准备 JDK 25。项目使用 Gradle 9.5.0 wrapper，真实客户端需要显示和图形环境，首次运行会下载依赖和游戏资源。

```powershell
.\gradlew.bat check build :cli:installDist
.\gradlew.bat -p integration/neoforge mineDriverCheck
.\gradlew.bat -p integration/neoforge '-Pplan=../../examples/plans/neoforge-settings.json' '-Pprobe=fixture' mineDriverCheck
.\gradlew.bat -p integration/fabric mineDriverFrameworkCheck
.\gradlew.bat -p integration/neoforge-tests mineDriverFrameworkCheck
```

Linux 客户端测试应把整个 Gradle 命令放在 `xvfb-run -a` 下。根 CI 用模拟客户端验证生命周期；真实游戏兼容性单独验证，见 [记录](validation.md)。

## 接入已有 mod

当前可以 composite build 使用，尚未发布到 Plugin Portal。在 mod 项目已有 `settings.gradle` 的 `pluginManagement` 块加入：

```groovy
pluginManagement {
    includeBuild('../minedriver')
    // 保留原有 loader 仓库和配置。
}
```

在 loader 子项目应用插件：

```groovy
plugins {
    // 保留已有 Loom / ModDevGradle 插件。
    id 'io.github.billstark001.minedriver'
}
mineDriver {
    sourceRunTask.set('runClient')
    plan.set(layout.projectDirectory.file('driver/title-smoke.json'))
    scenarios.add('example.Smoke') // 可选
    extensionClasses.add('example.ScreenExtension') // 可选
    timeoutMillis.set(300000L)
    inputIsolation.set(true)
    backgroundExecution.set(true)
}
```

复制 [标题页示例](../examples/plans/title-smoke.json) 到计划路径。Java 场景放在 `src/minedriver/java` 并实现 `Scenario`；扩展实现 `DriverExtension`。此 source set 可访问 Minecraft 和主 mod 编译依赖，但不进入主 mod JAR。JSON 计划先执行，再运行 Java 场景。扩展列表名为 `extensionClasses`，避免与 Gradle 的 `extensions` 属性冲突。

`mineDriverCheck` 自动执行并退出；`mineDriverRun` 开启交互；`mineDriverMatrix` 按 profiles 顺序运行独立会话。多版本、多 loader 分别接入各自子项目。

```text
build/minedriver/runs/<task>/<runId>/
build/reports/minedriver/<task>/<runId>/
build/reports/minedriver/<task>/latest.json
```

目录异常可检查报告目录中的 `filesystem-gradle.json` 和 `filesystem-client.json`：记录创建前后、seed 复制、启动及结束时的进程身份、Windows 默认所有者 SID、目录所有者和 ACL，不会执行提权、修改所有者或重置权限。

检查模式要求非空计划或 Java 场景。只有本次 run ID 的完整 PASS 报告和成功退出码才能通过；加载失败、旧或缺失报告、超时、断言失败都使 Gradle 失败。应用插件不会自动改变普通运行、构建或发布任务。

## Agent 技能

Codex 在本仓库中可发现 `.agents/skills/minedriver`。在其它 mod 项目工作时，将这个**完整文件夹**复制到目标项目的 `.agents/skills`，或按 [安装指引](skills.md) 从 GitHub 安装；不要只复制 `SKILL.md`，其按需加载的参考文件也需要保留。技能本身不会安装 Gradle 插件，也不会自动配置 MCP server。

安装后可以这样请求：

```text
使用 $minedriver 验证此 mod 的设置界面在英文、简繁中文和日文下的导航与行为，检查断言并报告截图和失败原因。
```

技能支持 CLI 和 MCP 两种方式，包含会话选择、精确控件选择、异步测试结果验证、服务端权威断言及报告检查。安装路径、其它 agent 的使用方式和维护说明见 [skills.md](skills.md)。

## 交互和 MCP

先在 mod 项目执行 `mineDriverRun`，另开终端使用 MineDriver 安装后的 CLI：

```powershell
.\cli\build\install\minedriver\bin\minedriver.bat D:/mod/build/reports/minedriver/mineDriverRun/latest.json inspect
.\cli\build\install\minedriver\bin\minedriver.bat D:/mod/build/reports/minedriver/mineDriverRun/latest.json call ui.snapshot
.\cli\build\install\minedriver\bin\minedriver.bat D:/mod/build/reports/minedriver/mineDriverRun/latest.json call session.close
```

`call <命令> <JSON|@文件>` 传参，Windows 推荐 `@文件` 避免 shell 引号问题。`commands` 列出命令和 schema，`watch` 周期输出状态。交互模式默认无整体超时；等待 `scenario.status.running=false` 再关闭，异步场景失败会进入最终失败报告。单条交互 RPC 的错误记录在步骤中，最终状态依据场景和生命周期结果。

MCP stdio server 使用上述程序，参数为 `latest.json` 路径和 `mcp`。实现 MCP **2025-11-25** 初始化与 tools，截图返回 PNG image content。需先启动游戏，换会话后重启桥接；尚无 resources、prompts、取消或自动重连。Windows host 若不能直接启动 batch，可用 shell 包装。

## 使用边界

明确适配 Minecraft 26.2、26.3 的已验证组合。生产或无法识别的环境默认拒绝，`allowProduction=true` 可实验性尝试，未知版本还需 `allowUnsupportedRuntime=true`。旧版混淆环境需要额外适配。

统一观察器负责启动、标题页及世界加载；`agent.status` 不等待游戏线程或普通命令锁，会保留当前阻塞界面、快照/帧心跳年龄、真实焦点、暂停和最后失败阶段。并发等待各自登记和清理，帧心跳直接来自完成帧，关闭阶段拒绝排队或新动作。命令排队及各级等待共享同一截止时间；已开始的回调超时后结果可能未确定，不应直接重试。截图附带同名 `.ui.json` 和请求/完成帧号。

准备阶段必须到达标题界面并结束资源覆盖层。NeoForge 加载警告默认立即以 `STARTUP_WARNINGS` 失败，加载错误以 `STARTUP_ERRORS` 失败；先查报告里的 `startup.warnings`、`failure-ui.json`、截图和原生日志。确诊后可在 `mineDriver.acceptedStartupWarnings` 中加入精确的 `modId:translationKey`，每条警告都必须匹配，任何错误都会阻止继续。键可能是原生 issue 中的字面模板；不要按屏幕上的英文文本猜键。已接受的警告仍写入报告。模组和音效加载完成不能证明已经越过警告界面。

`world.create` 和 `world.connect` 还会等待加载界面与覆盖层消失，避免刚获得 player/level 就发送按键而丢失事件。多人连接仍需独立验证。

若 loader 在首帧前处理了崩溃但进程未退出，agent 会检查本次隔离目录中新生成的原生客户端崩溃报告，并以 `STARTUP_CRASH` 失败；旧的种子报告不会触发此检查。此时还没有可截图的界面，应查看报告给出的原生 crash-report 路径。

语义操作直接调用控件行为，输入操作调用游戏事件处理，都不是系统鼠标/键盘事件。visible 标志不代表像素级无遮挡；完成帧屏障不等于 GPU 完成同步。多人连接和生产环境尚未纳入真实测试矩阵。

`connection.json` 含会话密钥，应留在本机。分享报告时去掉密钥、私人日志和账户配置。Agent 和 Java 扩展有游戏进程内完整代码执行能力，不是沙箱。

项目的 CI 验证 Linux 和 Windows 构建；版本标签触发 GitHub Release，提供版本化 JAR、CLI 压缩包和 SHA-256 校验和。Plugin Portal 与远程 Maven 发布尚未配置。详见 [发布说明](releasing.md)、 [完整英文说明](../README.md)、[命令](commands.md)、[架构](architecture.md)、[原生框架](frameworks.md)、[验证记录](validation.md)。
