# Changelog

## [Unreleased]

## [0.3.0] - 2026-10-18

- Derive agent/MCP identity, manifests, artifact names and consumer init-script paths from the single `gradle.properties` version through generated build metadata.
- Breaking Java API: custom `Driver` implementations must provide `await(...)` and `isGameThread()`; compatibility defaults are removed. See the [upgrade notes](https://github.com/billstark001/minedriver/blob/v0.3.0/docs/releasing.md#upgrading-to-030).
- Replace the UI inspection API with a single `ui.snapshot` object: widget hit/layout/clipped bounds, Cloth expansion, container slots, native rendered text/items, frame/screen identity and paired screenshot geometry. No legacy command alias is registered.
- Add scoped standard-game input isolation, virtual key polling, expiring owned leases, queue generations, input cleanup and independent background execution. Missing required hooks fail explicitly.
- Consolidate startup/title/world waits into a lifecycle observer with cached status, actual focus/pause, heartbeat/last-failure evidence and shared operation deadlines. Remove the broad warning override; only diagnosed exact warning identities may proceed.
- Record directory owners, ACLs and process/token identity from Gradle and the client without modifying permissions.
- Preserve successful shutdown-deadline exit codes. Real focus regressions hide/restore the owned native window and assert unchanged dimensions, background input and world entry.
- Enforce lease expiry at input/acquire/renew/release boundaries, quarantine held-key polling after restoration, and release injected keys on callback failure and session close. Closed input controllers reject new automation.
- Make observer waits independent under concurrency, derive heartbeat age from completed frames, retain failures from predicates/watchdogs/shutdown, and preserve terminal evidence through cleanup.
- Share scenario ownership between finite checks and interactive starts; prevent queued actions and new scenarios after close. Restore options and close resources when extension initialization fails.
- Remove superseded state sampling, reflection setters, screenshot target fallback and obsolete FML production-field probing. Supported 26.2/26.3 input differences use explicit native adapters. Reject unknown configuration fields and the unused agent framework mode; native framework checks remain separate tasks.

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

[Unreleased]: https://github.com/billstark001/minedriver/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/billstark001/minedriver/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/billstark001/minedriver/releases/tag/v0.2.0
