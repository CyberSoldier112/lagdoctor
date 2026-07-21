package com.craftpilot.lagdoctor.scan;

import com.craftpilot.lagdoctor.rules.Finding;
import com.craftpilot.lagdoctor.sampler.TickStats;

import java.time.Instant;
import java.util.List;

/** The last completed diagnosis, kept in memory only. */
public final class ScanResult {

    public final Instant timestamp;
    public final List<String> worlds;
    public final TickStats tick;
    public final List<ChunkStats> chunks;
    public final List<Finding> findings;

    private volatile String reportFile;

    public ScanResult(Instant timestamp, List<String> worlds, TickStats tick,
                      List<ChunkStats> chunks, List<Finding> findings) {
        this.timestamp = timestamp;
        this.worlds = worlds;
        this.tick = tick;
        this.chunks = chunks;
        this.findings = findings;
    }

    public String reportFile() {
        return reportFile;
    }

    public void reportFile(String path) {
        this.reportFile = path;
    }
}
