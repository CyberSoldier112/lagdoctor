package com.craftpilot.lagdoctor.sampler;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Arrays;

/**
 * Measures tick duration via Paper's tick start/end events into a fixed-size
 * ring buffer. Everything runs on the main thread; per-tick cost is two
 * nanoTime calls and one array store.
 */
public final class TickSampler implements Listener {

    private long[] samples;
    private int size;
    private int head;
    private long spikeThresholdNanos;
    private long tickStartNanos = -1L;

    public TickSampler(int historyTicks, double spikeThresholdMs) {
        configure(historyTicks, spikeThresholdMs);
    }

    public void configure(int historyTicks, double spikeThresholdMs) {
        this.samples = new long[Math.max(100, historyTicks)];
        this.size = 0;
        this.head = 0;
        this.spikeThresholdNanos = (long) (Math.max(1.0, spikeThresholdMs) * 1_000_000L);
        this.tickStartNanos = -1L;
    }

    public double spikeThresholdMs() {
        return spikeThresholdNanos / 1_000_000.0;
    }

    @EventHandler
    public void onTickStart(ServerTickStartEvent event) {
        tickStartNanos = System.nanoTime();
    }

    @EventHandler
    public void onTickEnd(ServerTickEndEvent event) {
        if (tickStartNanos < 0) {
            return;
        }
        samples[head] = System.nanoTime() - tickStartNanos;
        head = (head + 1) % samples.length;
        if (size < samples.length) {
            size++;
        }
    }

    public TickStats snapshot() {
        if (size == 0) {
            return TickStats.empty();
        }
        long[] copy = new long[size];
        System.arraycopy(samples, 0, copy, 0, size);

        long sum = 0;
        long max = 0;
        int spikes = 0;
        for (long sample : copy) {
            sum += sample;
            if (sample > max) {
                max = sample;
            }
            if (sample > spikeThresholdNanos) {
                spikes++;
            }
        }
        Arrays.sort(copy);
        int p95Index = Math.min(copy.length - 1, Math.max(0, (int) Math.ceil(copy.length * 0.95) - 1));

        double avgMspt = sum / (double) size / 1_000_000.0;
        double tps = avgMspt <= 0 ? 20.0 : Math.min(20.0, 1000.0 / avgMspt);
        return new TickStats(size, avgMspt,
                copy[p95Index] / 1_000_000.0,
                max / 1_000_000.0,
                spikes, tps, size / 20.0);
    }
}
