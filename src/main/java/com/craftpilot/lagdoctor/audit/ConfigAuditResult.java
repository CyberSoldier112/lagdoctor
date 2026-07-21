package com.craftpilot.lagdoctor.audit;

import java.util.List;
import java.util.Map;

/**
 * @param findings values outside their recommended range
 * @param values   all successfully read values keyed by check id (booleans as 0/1)
 */
public record ConfigAuditResult(List<ConfigFinding> findings, Map<String, Double> values) {

    public static ConfigAuditResult empty() {
        return new ConfigAuditResult(List.of(), Map.of());
    }
}
