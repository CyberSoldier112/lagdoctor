package com.craftpilot.lagdoctor.audit;

/**
 * One known server config value with its acceptable numeric range.
 * Booleans are mapped to 0/1 before comparison.
 */
public record ConfigCheck(String id, String file, String path, double min, double max) {
}
