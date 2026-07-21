package com.craftpilot.lagdoctor;

import com.craftpilot.lagdoctor.audit.ConfigAuditResult;
import com.craftpilot.lagdoctor.audit.ConfigAuditor;
import com.craftpilot.lagdoctor.report.ReportRenderer;
import com.craftpilot.lagdoctor.report.ReportWriter;
import com.craftpilot.lagdoctor.rules.Finding;
import com.craftpilot.lagdoctor.rules.RuleEngine;
import com.craftpilot.lagdoctor.sampler.TickSampler;
import com.craftpilot.lagdoctor.sampler.TickStats;
import com.craftpilot.lagdoctor.scan.ChunkScanner;
import com.craftpilot.lagdoctor.scan.ChunkStats;
import com.craftpilot.lagdoctor.scan.ScanResult;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.time.Instant;
import java.util.List;

/**
 * Orchestrates a full diagnosis: time-sliced chunk scan on the main thread,
 * config audit off-thread, then rule evaluation, chat report and async
 * markdown export.
 */
public final class ScanService {

    private final LagDoctorPlugin plugin;
    private final TickSampler sampler;
    private final Messages messages;
    private final ChunkScanner scanner;
    private final RuleEngine ruleEngine = new RuleEngine();
    private final ReportRenderer renderer;
    private final ReportWriter writer;

    private ScanResult lastResult;

    public ScanService(LagDoctorPlugin plugin, TickSampler sampler, Messages messages) {
        this.plugin = plugin;
        this.sampler = sampler;
        this.messages = messages;
        this.scanner = new ChunkScanner(plugin);
        this.renderer = new ReportRenderer(messages);
        this.writer = new ReportWriter(plugin, messages);
    }

    public ScanResult lastResult() {
        return lastResult;
    }

    public ReportRenderer renderer() {
        return renderer;
    }

    public int findingsPerPage() {
        return Math.max(1, plugin.getConfig().getInt("report.findings-per-page", 8));
    }

    public void startScan(CommandSender sender, List<World> worlds) {
        if (scanner.isRunning()) {
            sender.sendMessage(messages.prefixed("scan.already-running"));
            return;
        }
        List<String> worldNames = worlds.stream().map(World::getName).toList();
        int chunksPerTick = Math.max(1, plugin.getConfig().getInt("scan.chunks-per-tick", 20));

        int queued = scanner.start(worlds, chunksPerTick,
                chunks -> onChunksScanned(sender, worldNames, chunks));
        sender.sendMessage(messages.prefixed("scan.started",
                "chunks", String.valueOf(queued),
                "per", String.valueOf(chunksPerTick)));
    }

    private void onChunksScanned(CommandSender sender, List<String> worldNames, List<ChunkStats> chunks) {
        TickStats tick = sampler.snapshot();
        ConfigAuditor auditor = new ConfigAuditor(
                plugin.getServer().getWorldContainer(), plugin.getLogger());
        // config files are read off-thread, then the rule pass runs back on main
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ConfigAuditResult audit;
            try {
                audit = auditor.audit();
            } catch (Exception e) {
                plugin.getLogger().warning("Config audit failed: " + e.getMessage());
                audit = ConfigAuditResult.empty();
            }
            ConfigAuditResult finalAudit = audit;
            Bukkit.getScheduler().runTask(plugin, () ->
                    finishScan(sender, worldNames, chunks, tick, finalAudit));
        });
    }

    private void finishScan(CommandSender sender, List<String> worldNames,
                            List<ChunkStats> chunks, TickStats tick, ConfigAuditResult audit) {
        List<Finding> findings = ruleEngine.run(tick, chunks, audit, plugin.getConfig());
        ScanResult result = new ScanResult(Instant.now(), worldNames, tick, chunks, findings);
        lastResult = result;

        sender.sendMessage(messages.prefixed("scan.complete",
                "chunks", String.valueOf(chunks.size()),
                "findings", String.valueOf(findings.size())));
        renderer.sendPage(sender, result, 1, findingsPerPage());

        if (plugin.getConfig().getBoolean("report.save-to-file", true)) {
            int maxSaved = Math.max(1, plugin.getConfig().getInt("report.max-saved-reports", 20));
            writer.writeAsync(result, maxSaved, path -> {
                if (path != null) {
                    result.reportFile(path);
                    sender.sendMessage(messages.prefixed("scan.file-saved", "path", path));
                }
            });
        }
    }

    public void shutdown() {
        scanner.stop();
    }
}
