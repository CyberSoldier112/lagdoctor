package com.craftpilot.lagdoctor.rules;

import com.craftpilot.lagdoctor.audit.ConfigAuditResult;
import com.craftpilot.lagdoctor.audit.ConfigFinding;
import com.craftpilot.lagdoctor.sampler.TickStats;
import com.craftpilot.lagdoctor.scan.ChunkStats;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fixed rule set turning measurements into ranked findings. Rules only emit
 * message keys + placeholder args; all human-readable text lives in the
 * language files.
 */
public final class RuleEngine {

    /** How many chunks a single chunk-based rule may flag, keeps reports readable. */
    private static final int MAX_CHUNKS_PER_RULE = 3;

    private static final Map<String, Double> CONFIG_SEVERITY = Map.ofEntries(
            Map.entry("view-distance", 55.0),
            Map.entry("simulation-distance", 50.0),
            Map.entry("per-player-mob-spawns", 45.0),
            Map.entry("mob-spawn-range", 40.0),
            Map.entry("activation-range-monsters", 40.0),
            Map.entry("activation-range-animals", 38.0),
            Map.entry("max-entity-collisions", 40.0),
            Map.entry("chunk-gc", 35.0),
            Map.entry("monster-spawn-limit", 35.0),
            Map.entry("merge-radius-item", 30.0),
            Map.entry("hopper-check", 30.0),
            Map.entry("hopper-transfer", 30.0),
            Map.entry("max-auto-save", 30.0),
            Map.entry("network-compression", 25.0));

    public List<Finding> run(TickStats tick, List<ChunkStats> chunks,
                             ConfigAuditResult audit, FileConfiguration config) {
        int hopperThreshold = config.getInt("thresholds.hoppers-per-chunk", 60);
        int entityThreshold = config.getInt("thresholds.entities-per-chunk", 150);
        int itemThreshold = config.getInt("thresholds.dropped-items-per-chunk", 200);
        int tileThreshold = config.getInt("thresholds.tile-entities-per-chunk", 100);

        List<Finding> findings = new ArrayList<>();

        // --- tick health ---
        if (tick.sampleCount() >= 100) {
            if (tick.avgMspt() > 50.0) {
                findings.add(new Finding(100, "rule.overloaded",
                        new String[]{"avg", fmt(tick.avgMspt())}));
            } else if (tick.avgMspt() > 40.0) {
                findings.add(new Finding(70, "rule.near-capacity",
                        new String[]{"avg", fmt(tick.avgMspt())}));
            }
            if (tick.spikeCount() >= 20) {
                findings.add(new Finding(Math.min(90, 55 + tick.spikeCount() / 10.0),
                        "rule.frequent-spikes", new String[]{
                        "spikes", String.valueOf(tick.spikeCount()),
                        "window", String.valueOf((int) tick.windowSeconds())}));
            }
        }

        // --- chunk density rules ---
        String hopperCheck = currentValue(audit, "hopper-check", "1");
        topBy(chunks, c -> c.hoppers, hopperThreshold).forEach(c ->
                findings.add(chunkFinding(scale(50, c.hoppers, hopperThreshold), "rule.hopper-density", c,
                        "count", String.valueOf(c.hoppers),
                        "threshold", String.valueOf(hopperThreshold),
                        "current", hopperCheck)));

        topBy(chunks, c -> c.totalEntities, entityThreshold).forEach(c -> {
            Map.Entry<String, Integer> top = c.topLivingType();
            findings.add(chunkFinding(scale(45, c.totalEntities, entityThreshold), "rule.entity-density", c,
                    "count", String.valueOf(c.totalEntities),
                    "threshold", String.valueOf(entityThreshold),
                    "type", top != null ? top.getKey() : "-",
                    "typeCount", top != null ? String.valueOf(top.getValue()) : "0"));
        });

        int mobFarmThreshold = Math.max(40, (int) (entityThreshold * 0.6));
        topBy(chunks, c -> {
            Map.Entry<String, Integer> top = c.topLivingType();
            return top != null ? top.getValue() : 0;
        }, mobFarmThreshold).forEach(c -> {
            Map.Entry<String, Integer> top = c.topLivingType();
            if (top != null) {
                findings.add(chunkFinding(scale(40, top.getValue(), mobFarmThreshold), "rule.mob-farm", c,
                        "count", String.valueOf(top.getValue()),
                        "type", top.getKey()));
            }
        });

        topBy(chunks, c -> c.droppedItems, itemThreshold).forEach(c ->
                findings.add(chunkFinding(scale(42, c.droppedItems, itemThreshold), "rule.item-accumulation", c,
                        "count", String.valueOf(c.droppedItems),
                        "threshold", String.valueOf(itemThreshold))));

        boolean spiky = tick.spikeCount() >= 10;
        topBy(chunks, c -> c.tileEntities, tileThreshold).forEach(c -> {
            if (spiky) {
                findings.add(chunkFinding(scale(55, c.tileEntities, tileThreshold), "rule.redstone-heavy", c,
                        "count", String.valueOf(c.tileEntities),
                        "spikes", String.valueOf(tick.spikeCount())));
            } else {
                findings.add(chunkFinding(scale(35, c.tileEntities, tileThreshold), "rule.tile-entity-density", c,
                        "count", String.valueOf(c.tileEntities),
                        "threshold", String.valueOf(tileThreshold)));
            }
        });

        topBy(chunks, c -> c.spawners, 8).forEach(c ->
                findings.add(chunkFinding(scale(35, c.spawners, 8), "rule.spawner-density", c,
                        "count", String.valueOf(c.spawners))));

        // --- config audit findings ---
        for (ConfigFinding cf : audit.findings()) {
            String id = cf.check().id();
            findings.add(new Finding(CONFIG_SEVERITY.getOrDefault(id, 30.0), "config." + id,
                    new String[]{
                            "file", cf.check().file(),
                            "path", cf.check().path(),
                            "value", cf.value()}));
        }

        // --- environment correlations ---
        long maxRamMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        Double simDistance = audit.values().get("simulation-distance");
        if (maxRamMb > 0 && maxRamMb < 4096 && simDistance != null && simDistance >= 8) {
            findings.add(new Finding(60, "rule.sim-distance-ram", new String[]{
                    "ram", String.valueOf(maxRamMb),
                    "value", String.valueOf(simDistance.intValue())}));
        }

        if (chunks.size() >= 4000) {
            findings.add(new Finding(45, "rule.many-chunks", new String[]{
                    "count", String.valueOf(chunks.size())}));
        }

        findings.sort(Comparator.comparingDouble(Finding::severity).reversed());
        return findings;
    }

    private interface ChunkMetric {
        int get(ChunkStats stats);
    }

    private static List<ChunkStats> topBy(List<ChunkStats> chunks, ChunkMetric metric, int threshold) {
        return chunks.stream()
                .filter(c -> metric.get(c) > threshold)
                .sorted(Comparator.comparingInt(metric::get).reversed())
                .limit(MAX_CHUNKS_PER_RULE)
                .toList();
    }

    /** Base severity plus up to +40 as the value overshoots its threshold. */
    private static double scale(double base, int value, int threshold) {
        double over = (value - threshold) / (double) Math.max(1, threshold);
        return Math.min(95, base + Math.min(40, over * 30));
    }

    private static Finding chunkFinding(double severity, String key, ChunkStats c, String... extra) {
        String[] base = {
                "world", c.world,
                "x", String.valueOf(c.x),
                "z", String.valueOf(c.z),
                "rx", String.valueOf(c.regionX()),
                "rz", String.valueOf(c.regionZ())};
        String[] args = new String[base.length + extra.length];
        System.arraycopy(base, 0, args, 0, base.length);
        System.arraycopy(extra, 0, args, base.length, extra.length);
        return new Finding(severity, key, args, c.world, c.x, c.z);
    }

    private static String currentValue(ConfigAuditResult audit, String id, String fallback) {
        Double value = audit.values().get(id);
        return value == null ? fallback : String.valueOf(value.intValue());
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
