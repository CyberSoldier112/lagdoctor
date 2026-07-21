package com.craftpilot.lagdoctor.audit;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Reads ~15 known-critical values from server.properties, bukkit.yml,
 * spigot.yml and config/paper-world-defaults.yml (all read-only) and flags
 * anything outside the recommended range. Designed to run off the main thread.
 */
public final class ConfigAuditor {

    private static final List<ConfigCheck> CHECKS = List.of(
            new ConfigCheck("view-distance", "server.properties", "view-distance", 2, 10),
            new ConfigCheck("simulation-distance", "server.properties", "simulation-distance", 2, 10),
            new ConfigCheck("network-compression", "server.properties", "network-compression-threshold", 64, 512),
            new ConfigCheck("monster-spawn-limit", "bukkit.yml", "spawn-limits.monsters", 1, 70),
            new ConfigCheck("chunk-gc", "bukkit.yml", "chunk-gc.period-in-ticks", 1, 12000),
            new ConfigCheck("mob-spawn-range", "spigot.yml", "world-settings.default.mob-spawn-range", 1, 8),
            new ConfigCheck("activation-range-monsters", "spigot.yml",
                    "world-settings.default.entity-activation-range.monsters", 1, 32),
            new ConfigCheck("activation-range-animals", "spigot.yml",
                    "world-settings.default.entity-activation-range.animals", 1, 32),
            new ConfigCheck("merge-radius-item", "spigot.yml", "world-settings.default.merge-radius.item", 1.0, 6.0),
            new ConfigCheck("max-entity-collisions", "spigot.yml",
                    "world-settings.default.max-entity-collisions", 0, 8),
            new ConfigCheck("hopper-check", "spigot.yml", "world-settings.default.ticks-per.hopper-check", 1, 64),
            new ConfigCheck("hopper-transfer", "spigot.yml", "world-settings.default.ticks-per.hopper-transfer", 8, 64),
            new ConfigCheck("per-player-mob-spawns", "paper-world-defaults.yml",
                    "entities.spawning.per-player-mob-spawns", 1, 1),
            new ConfigCheck("max-auto-save", "paper-world-defaults.yml",
                    "chunks.max-auto-save-chunks-per-tick", 1, 24));

    private final File serverRoot;
    private final Logger logger;

    public ConfigAuditor(File serverRoot, Logger logger) {
        this.serverRoot = serverRoot;
        this.logger = logger;
    }

    public ConfigAuditResult audit() {
        Properties properties = loadProperties(new File(serverRoot, "server.properties"));
        YamlConfiguration bukkit = loadYaml(new File(serverRoot, "bukkit.yml"));
        YamlConfiguration spigot = loadYaml(new File(serverRoot, "spigot.yml"));
        YamlConfiguration paper = loadYaml(new File(serverRoot, "config/paper-world-defaults.yml"));

        List<ConfigFinding> findings = new ArrayList<>();
        Map<String, Double> values = new HashMap<>();
        for (ConfigCheck check : CHECKS) {
            Double value = switch (check.file()) {
                case "server.properties" -> parse(properties.getProperty(check.path()));
                case "bukkit.yml" -> yamlValue(bukkit, check.path());
                case "spigot.yml" -> yamlValue(spigot, check.path());
                default -> yamlValue(paper, check.path());
            };
            if (value == null) {
                continue;
            }
            values.put(check.id(), value);
            // -1 here means "let the server decide" — not a misconfiguration
            if (check.id().equals("max-auto-save") && value < 0) {
                continue;
            }
            if (value < check.min() || value > check.max()) {
                findings.add(new ConfigFinding(check, display(value)));
            }
        }
        return new ConfigAuditResult(findings, values);
    }

    private Properties loadProperties(File file) {
        Properties properties = new Properties();
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                logger.warning("Could not read " + file.getName() + ": " + e.getMessage());
            }
        }
        return properties;
    }

    private YamlConfiguration loadYaml(File file) {
        if (file.isFile()) {
            return YamlConfiguration.loadConfiguration(file);
        }
        return new YamlConfiguration();
    }

    private static Double yamlValue(YamlConfiguration config, String path) {
        Object value = config.get(path);
        if (value instanceof Boolean bool) {
            return bool ? 1.0 : 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            return parse(string);
        }
        return null;
    }

    private static Double parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("true")) {
            return 1.0;
        }
        if (trimmed.equalsIgnoreCase("false")) {
            return 0.0;
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String display(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
