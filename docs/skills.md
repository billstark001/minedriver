# MineDriver agent skill

The [minedriver skill](../.agents/skills/minedriver/SKILL.md) teaches agents how to turn a requested mod behavior into an observed, reproducible check using MineDriver. It covers attaching to a client, UI/world inspection, assertions, screenshots, profiling, regression test authoring and failure diagnosis.

| File | Purpose |
| --- | --- |
| [SKILL.md](../.agents/skills/minedriver/SKILL.md) | Trigger description, workflow selection, shared contracts and completion evidence. |
| [references/sessions.md](../.agents/skills/minedriver/references/sessions.md) | CLI/MCP connection, inspection, native input, artifacts and failure triage. |
| [references/tests.md](../.agents/skills/minedriver/references/tests.md) | Gradle integration, JSON plans, Java scenarios/extensions and native framework verification. |
| [agents/openai.yaml](../.agents/skills/minedriver/agents/openai.yaml) | Codex presentation metadata and an example invocation. |

The folder follows the Agent Skills layout, keeps its internal references relative and carries its MIT license. It has no hardcoded MCP endpoint, account credentials or mandatory MCP dependency: the existing CLI can perform the same workflow. Command schemas come from the connected runtime; the skill avoids maintaining a second exhaustive API catalog.

## Install in the project where the agent works

Codex discovers repository skills in `.agents/skills` from the current directory up to the repository root. Working inside the MineDriver checkout makes this skill available there. A sibling mod repository does not inherit it just because its Gradle build includes MineDriver. These discovery rules and user-level locations are described in the [official Codex skills guide](https://learn.chatgpt.com/docs/build-skills).

For a mod repository, copy the complete `minedriver` skill folder into `<mod-repo>/.agents/skills/minedriver`. For example, on Windows, replace the two checkout paths below and run:

```powershell
$driverSkillSource = 'D:/Programs/minedriver/.agents/skills/minedriver'
$driverSkillParent = 'D:/mod/.agents/skills'
$driverSkillDestination = Join-Path $driverSkillParent 'minedriver'
if (Test-Path -LiteralPath $driverSkillDestination) {
    throw 'A minedriver skill already exists here; review its differences before updating.'
}
New-Item -ItemType Directory -Path $driverSkillParent -Force | Out-Null
Copy-Item -LiteralPath $driverSkillSource -Destination $driverSkillDestination -Recurse
```

On Unix, copy that same folder using your file manager or `cp -R` into the destination parent; keep the final layout `<mod-repo>/.agents/skills/minedriver/SKILL.md`. Include `references`, `agents` and `LICENSE.txt`. Review local modifications before updating a copy.

For personal use across repositories, the current Codex user-level location is `~/.agents/skills/minedriver` (on Windows, under your user profile). Older hosts/installers may use `~/.codex/skills`; follow your host's documented discovery path. Prefer a single installation scope to avoid duplicate `minedriver` entries.

Alternatively, with Codex's built-in skill installer, use this prompt:

```text
Use $skill-installer to install the minedriver skill from https://github.com/billstark001/minedriver/tree/main/.agents/skills/minedriver
```

The installable GitHub folder is [`.agents/skills/minedriver`](https://github.com/billstark001/minedriver/tree/main/.agents/skills/minedriver). Use a reviewed tag or commit URL when pinning an installation. Codex normally detects skills automatically; restart it if the new skill is absent. Installation into an agent's skill directory is separate from installing the MineDriver Gradle plugin.

## Use the skill

In Codex CLI/IDE, mention `$minedriver` or select it with `/skills`. Hosts with a skill picker may expose it as **MineDriver**. It can also be selected implicitly for tasks matching its description. Other agents supporting Agent Skills can install the same folder in their own discovery directory; `agents/openai.yaml` is optional host-specific presentation metadata. For an agent without skill discovery, explicitly ask it to read the installed `SKILL.md` and the relevant linked reference.

Provide the target project/subproject and intended behavior. If you already have a running session, provide its exact `latest.json` or `connection.json` path without pasting its contents. The agent can resolve configured tasks when those details are available from the project.

Examples:

```text
Use $minedriver in D:/mod, subproject :fabric, to verify the settings screen in en_us, zh_cn, zh_tw and ja_jp. Assert navigation and capture each language.
```

```text
Use $minedriver with D:/mod/fabric/build/reports/minedriver/mineDriverRun/latest.json to reproduce the disabled-button bug. Keep this existing session open and report the widget state and screenshot.
```

```text
Use $minedriver to turn the successful reproduction into a Java regression scenario. Assert the actual integrated-server inventory state after the UI action.
```

The skill routes these tasks to [session operations](../.agents/skills/minedriver/references/sessions.md) or [test authoring](../.agents/skills/minedriver/references/tests.md). The agent should report versions, steps/assertions, verdict and artifacts. Asynchronous acceptance, a sent packet, or an old PASS report is not a verified outcome.

## Runtime prerequisites

Use the [project setup](../README.md#add-to-a-mod-project) for the Gradle plugin. Building this snapshot uses JDK 25; client runs need native loader assets and a display/graphics environment. The verified game adapters cover Minecraft 26.2/26.3; see [validation](validation.md) for the actual tested combinations.

For interactive work, start the consuming project's `mineDriverRun`, then use the installed CLI or a host-configured MCP stdio bridge. Build the CLI with `./gradlew :cli:installDist` from the MineDriver checkout; its executable is `cli/build/install/minedriver/bin/minedriver` (`minedriver.bat` on Windows).

For MCP, configure the executable with arguments `["<absolute session latest.json path>", "mcp"]`; see [CLI/MCP usage](../README.md#interactive-cli-and-mcp). A new game needs a restarted bridge. Installing this skill grants no new tool capability and does not launch a game, edit agent settings or connect to a server by itself.

## Maintain and review

Keep the skill instruction-only while the CLI already provides the required mechanics. Put conditional detail in the two references; keep the entry point focused on choosing a workflow and interpreting results. When changing command names, report fields, source sets or session lifecycle, update the corresponding reference alongside the runtime documentation. Copies installed elsewhere must be updated explicitly.

Review the trigger with representative prompts:

| Prompt | Expected selection or decision |
| --- | --- |
| “Use MineDriver to inspect this settings screen.” | Select the skill and session reference; inspect before choosing a widget. |
| “Add a MineDriver regression check for this mod.” | Select the skill and authoring reference; use the existing loader subproject. |
| “The MineDriver scenario was accepted; is it passing?” | Wait for terminal scenario status and verify fresh completion evidence. |
| “A command was sent; did the inventory change?” | Assert the authoritative effect, rather than infer it from send success. |
| “Inspect this existing session and leave it open.” | Attach to the specified run; preserve that session. |
| “Translate the README” or “bump the mod version.” | No implicit selection based only on being a Minecraft project. |

Validate frontmatter, UI metadata, internal links and copied-folder portability. For substantive workflow changes, exercise the relevant CLI/MCP path against a real supported client and record assertions/artifacts; static validation alone does not establish agent behavior. The root simulated-client suite remains useful for protocol/lifecycle mechanics. No copied scripts, fixed remote MCP URL, or duplicate API manual are needed to install this skill.
