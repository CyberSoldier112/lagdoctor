package com.craftpilot.lagdoctor.audit;

/** A config value found outside its recommended range. */
public record ConfigFinding(ConfigCheck check, String value) {
}
