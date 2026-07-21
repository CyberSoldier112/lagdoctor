package com.craftpilot.lagdoctor.sampler;

/**
 * Immutable snapshot of the tick ring buffer.
 *
 * @param sampleCount   number of ticks currently in the buffer
 * @param avgMspt       average milliseconds per tick
 * @param p95Mspt       95th percentile MSPT
 * @param maxMspt       worst tick in the window
 * @param spikeCount    ticks above the configured spike threshold
 * @param tps           estimated TPS (capped at 20)
 * @param windowSeconds approximate real-time span covered by the buffer
 */
public record TickStats(int sampleCount, double avgMspt, double p95Mspt, double maxMspt,
                        int spikeCount, double tps, double windowSeconds) {

    public static TickStats empty() {
        return new TickStats(0, 0, 0, 0, 0, 20.0, 0);
    }
}
