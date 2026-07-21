# LagDoctor

**Diagnosis + prescription for server lag — no profiler jargon.**

LagDoctor measures your server from the inside (tick times, entity/tile-entity
density per chunk, server config values) and turns the results into **ranked,
actionable recommendations** instead of raw profiler data. Where a profiler
shows you flame graphs, LagDoctor tells you things like:

> *The chunk at 5,-3 in region r.0.-1 contains 412 hoppers — raise
> `ticks-per.hopper-check` in spigot.yml to 8.*

Everything runs inside the server: no external services, no web API, no spark
dependency. Output language is selectable (English / Turkish).

## Features

- **Tick sampler** — measures every tick via Paper's tick events into a
  fixed-size ring buffer (default: last 6000 ticks ≈ 5 minutes). Reports MSPT
  average, p95, worst tick and spike count. No extra threads, negligible cost.
- **Chunk scanner** — command-triggered, *time-sliced* scan (a limited number
  of chunks per tick) so it never freezes the server. Counts entities per type,
  dropped items, and tile entities (hoppers, spawners, furnaces) per loaded chunk.
- **Config auditor** — reads ~15 known-critical values from
  `server.properties`, `bukkit.yml`, `spigot.yml` and
  `config/paper-world-defaults.yml` (strictly read-only, never modified) and
  flags anything outside recommended ranges.
- **Rule-based diagnosis engine** — a fixed rule set (hopper density, mob
  accumulation, item pile-ups, redstone-heavy chunk signals, oversized view
  distance, simulation distance vs. available RAM, and more) converts all
  measurements into findings **sorted by severity**, each with a one-line cause
  and a concrete action.
- **Reports** — paginated in-game chat report with clickable `[TP]` buttons to
  teleport straight to a problem chunk, plus a timestamped markdown file under
  `plugins/LagDoctor/reports/` (old files rotate automatically).
- **Localization** — every diagnosis string lives in `messages_en.yml` /
  `messages_tr.yml`; the engine itself only produces message keys.

Intentionally out of scope: continuous background profiling, method-level
profiling (that's spark's job), web panels, automatic config editing, and
entity removal/cleanup.

## Installation

1. Download the LagDoctor jar and drop it into your server's `plugins/` folder.
2. Restart the server (or start it for the first time).
3. Optionally edit `plugins/LagDoctor/config.yml` and run `/lagdoctor reload`.

**Requirements:** Paper (or a Paper fork) 1.20.4 – 1.21.x, Java 21.
Spigot is *not* supported — the tick-duration events LagDoctor relies on are
Paper-only.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/lagdoctor scan [world]` | Full diagnosis: tick stats + chunk scan + config audit, ranked report | `lagdoctor.scan` |
| `/lagdoctor report [page]` | Re-show the last scan report / print the report file path | `lagdoctor.scan` |
| `/lagdoctor top [entities\|hoppers]` | List the 10 busiest chunks (click to teleport) | `lagdoctor.scan` |
| `/lagdoctor tps` | Instant MSPT/TPS summary and spike count (lightweight, safe anytime) | `lagdoctor.tps` |
| `/lagdoctor tp <world> <chunkX> <chunkZ>` | Teleport to a chunk from a report (target of the clickable text) | `lagdoctor.teleport` |
| `/lagdoctor reload` | Reload config and message files | `lagdoctor.admin` |

All commands also work from the console (except `tp`). Everything defaults to
OP-only. `/ld` is available as an alias.

## Configuration (`config.yml`)

```yaml
language: tr                # tr | en — language of all output
sampler:
  history-ticks: 6000       # ring buffer size (~5 min at 20 TPS)
  spike-threshold-ms: 100   # a tick longer than this counts as a "spike"
scan:
  chunks-per-tick: 20       # scan speed; lower = less impact, slower scan
  top-chunk-count: 10       # busiest chunks listed by /lagdoctor top
report:
  save-to-file: true        # write markdown reports to plugins/LagDoctor/reports/
  max-saved-reports: 20     # older report files are deleted automatically
  findings-per-page: 8      # findings per chat page
thresholds:                 # rule thresholds (advanced tuning)
  hoppers-per-chunk: 60
  entities-per-chunk: 150
  dropped-items-per-chunk: 200
  tile-entities-per-chunk: 100
```

### Messages

`messages_tr.yml` and `messages_en.yml` are copied into the plugin folder on
first start and can be edited freely. A missing key falls back to the bundled
default; if that's missing too, the key name itself is printed (handy for
spotting typos).

## Data & privacy

- **No database.** Measurements live in memory; only the last scan is kept.
- Reports are written as `plugins/LagDoctor/reports/scan-YYYYMMDD-HHmmss.md`;
  files beyond `max-saved-reports` are deleted, oldest first.
- Server config files are opened **read-only** — LagDoctor never modifies them.

## FAQ

**Does scanning lag the server?** No. The scan processes at most
`chunks-per-tick` chunks per tick (20 by default), so even a world with
thousands of loaded chunks is swept without a visible MSPT increase — it just
takes a few seconds longer.

**How is this different from spark?** spark shows you *where* the time goes at
the method level; LagDoctor tells you *what to do about it* at the
gameplay/config level. They complement each other.
