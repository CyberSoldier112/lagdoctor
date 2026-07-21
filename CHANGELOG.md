# Changelog

## 1.0.0 — 2026-07-21

Initial release.

- Tick sampler built on Paper's `ServerTickStartEvent`/`ServerTickEndEvent`
  with a fixed-size ring buffer (default 6000 ticks): MSPT average, p95, worst
  tick and spike counter.
- Command-triggered, time-sliced chunk scanner (default 20 chunks/tick):
  per-chunk entity counts by type, dropped items, and tile entities (hoppers,
  spawners, furnaces).
- Read-only config auditor covering ~15 critical values across
  `server.properties`, `bukkit.yml`, `spigot.yml` and
  `config/paper-world-defaults.yml`.
- Rule-based diagnosis engine (~15 rules) producing severity-ranked findings,
  each with a one-line cause and a concrete action — including hopper density,
  mob/item accumulation, redstone-heavy chunk correlation, oversized view
  distance, and simulation-distance vs. RAM checks.
- Paginated in-game report with clickable chunk teleport buttons, plus
  timestamped markdown reports under `plugins/LagDoctor/reports/` with
  automatic rotation (`max-saved-reports`).
- `/lagdoctor scan | report | top | tps | tp | reload` with per-subcommand
  permissions and tab completion.
- Full localization: Turkish and English message files, selectable via
  `language` in `config.yml`, hot-reloadable with `/lagdoctor reload`.

Supported: Paper 1.20.4 – 1.21.x, Java 21.
