# MineDriver

MineDriver 是 Minecraft mod 开发和调试工具，主体为 **Gradle 插件 + Java Agent**，包名和插件 ID 均为 `io.github.billstark001.minedriver`，版本为 `0.1.0-SNAPSHOT`。

它克隆已有 Loom / ModDevGradle 的客户端启动配置，保留依赖准备、classpath、参数和环境，在独立游戏目录中注入 Agent。工具本身没有 mod 元数据或初始化器。原生框架示例的元数据仅属于框架的测试对象。

面向 agent 的 [minedriver 技能](../.agents/skills/minedriver/SKILL.md) 位于 `.agents/skills/minedriver`，涵盖客户端观察、UI / 世界复现、回归测试和失败诊断。见 [技能安装和使用指引](skills.md)。

## 当前能力

| 功能 | 接口 |
| --- | --- |
| 游戏状态、屏幕、控件树 | `session.inspect`、`ui.tree`；翻译键、文字、类型、路径、状态、玩家信息 |
| 控件操作 | 精确选择翻译键、标签、类型、路径或稳定 ID；歧义、禁用、隐藏时明确报错 |
| 设置入口 | Fabric Mod Menu、NeoForge 设置工厂、Cloth AutoConfig；不自动安装依赖 |
| 输入 | GUI 点击/滚动、键盘按下/保持/释放、字符输入；与语义操作分别记录 |
| 截图和多语言 | 完整帧屏障、异步 PNG、裁剪/遮罩/容差比较、差异图、资源重载等待 |
| 世界夹具 | 独立单人世界、方块/背包/位置设置、本地服务端权威快照 |
| 命令、网络和性能 | Brigadier、普通客户端命令包、实验性连接、帧/GC/堆/tick/JFR |
| 自动化与扩展 | JSON 计划、Java 场景、`custom.*` 命令、稳定 ID、CLI、MCP tools |
| 原生框架与报告 | Fabric GameTest / NeoForge JUnit；新生成的 XML、JSON/HTML 报告、失败截图和日志 |

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
}
```

复制 [标题页示例](../examples/plans/title-smoke.json) 到计划路径。Java 场景放在 `src/minedriver/java` 并实现 `Scenario`；扩展实现 `DriverExtension`。此 source set 可访问 Minecraft 和主 mod 编译依赖，但不进入主 mod JAR。JSON 计划先执行，再运行 Java 场景。扩展列表名为 `extensionClasses`，避免与 Gradle 的 `extensions` 属性冲突。

`mineDriverCheck` 自动执行并退出；`mineDriverRun` 开启交互；`mineDriverMatrix` 按 profiles 顺序运行独立会话。多版本、多 loader 分别接入各自子项目。

```text
build/minedriver/runs/<task>/<runId>/
build/reports/minedriver/<task>/<runId>/
build/reports/minedriver/<task>/latest.json
```

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
.\cli\build\install\minedriver\bin\minedriver.bat D:/mod/build/reports/minedriver/mineDriverRun/latest.json call ui.tree
.\cli\build\install\minedriver\bin\minedriver.bat D:/mod/build/reports/minedriver/mineDriverRun/latest.json call session.close
```

`call <命令> <JSON|@文件>` 传参，Windows 推荐 `@文件` 避免 shell 引号问题。`commands` 列出命令和 schema，`watch` 周期输出状态。交互模式默认无整体超时；等待 `scenario.status.running=false` 再关闭，异步场景失败会进入最终失败报告。单条交互 RPC 的错误记录在步骤中，最终状态依据场景和生命周期结果。

MCP stdio server 使用上述程序，参数为 `latest.json` 路径和 `mcp`。实现 MCP **2025-11-25** 初始化与 tools，截图返回 PNG image content。需先启动游戏，换会话后重启桥接；尚无 resources、prompts、取消或自动重连。Windows host 若不能直接启动 batch，可用 shell 包装。

## 使用边界

明确适配 Minecraft 26.2、26.3 的已验证组合。生产或无法识别的环境默认拒绝，`allowProduction=true` 可实验性尝试，未知版本还需 `allowUnsupportedRuntime=true`。旧版混淆环境需要额外适配。

语义操作直接调用控件行为，输入操作调用游戏事件处理，都不是系统鼠标/键盘事件。visible 标志不代表像素级无遮挡；完成帧屏障不等于 GPU 完成同步。多人连接和生产环境尚未纳入真实测试矩阵。

`connection.json` 含会话密钥，应留在本机。分享报告时去掉密钥、私人日志和账户配置。Agent 和 Java 扩展有游戏进程内完整代码执行能力，不是沙箱。

项目有验证 CI，没有发布工作流、自动发布或发布标签。详见 [完整英文说明](../README.md)、[命令](commands.md)、[架构](architecture.md)、[原生框架](frameworks.md)、[验证记录](validation.md)。
