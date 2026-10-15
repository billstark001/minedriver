# Changelog

## [Unreleased]

## [0.2.0] - 2026-10-16

### Fixed

- World creation and connection now wait for loading-screen dismissal as well as player/level availability, preventing immediate gameplay shortcuts from being lost during world entry.
- NeoForge loading issues fail promptly with structured diagnostics. Exact `acceptedStartupWarnings` entries permit diagnosed warnings and record them in the report; errors never proceed. Continue-button activation supports NeoForge's eagerly translated literal labels.
- Fresh native client crash reports before the first completed frame terminate startup with `STARTUP_CRASH`, including loader-handled failures that never reach the uncaught-exception handler.

### Added

- Tag-driven GitHub releases package the standalone agent, API, protocol, hooks, Gradle plugin and CLI distributions with SHA-256 checksums. This does not publish the plugin to the Gradle Plugin Portal.

## 0.1.0-SNAPSHOT

- Initial Gradle-plugin/Java-agent architecture, loader-neutral scenario and extension API, local authenticated RPC, CLI and MCP tools.
- Explicit Minecraft 26.2/26.3 native UI/input/settings/resource adapters; isolated launch directories, completed-frame hooks and structured failure handling.
- World fixtures and authoritative snapshots, visual comparison, performance/JFR sampling and native-framework report orchestration.
- Real Fabric/NeoForge client integration fixtures, native Fabric GameTest and NeoForge JUnit examples, cross-platform driver CI and regression tests.
- English/Chinese usage documentation, extension contracts, compatibility evidence and bundled dependency notices.

This is an unpublished development snapshot. No release tag or automatic release workflow is created.

[Unreleased]: https://github.com/billstark001/minedriver/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/billstark001/minedriver/releases/tag/v0.2.0
