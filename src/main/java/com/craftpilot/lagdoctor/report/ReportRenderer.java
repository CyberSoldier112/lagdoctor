package com.craftpilot.lagdoctor.report;

import com.craftpilot.lagdoctor.Messages;
import com.craftpilot.lagdoctor.rules.Finding;
import com.craftpilot.lagdoctor.sampler.TickStats;
import com.craftpilot.lagdoctor.scan.ChunkStats;
import com.craftpilot.lagdoctor.scan.ScanResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandSender;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Renders paginated chat reports with clickable chunk-teleport buttons. */
public final class ReportRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Messages messages;

    public ReportRenderer(Messages messages) {
        this.messages = messages;
    }

    public void sendPage(CommandSender sender, ScanResult result, int page, int perPage) {
        List<Finding> findings = result.findings;
        int pages = Math.max(1, (int) Math.ceil(findings.size() / (double) Math.max(1, perPage)));
        int current = Math.min(Math.max(1, page), pages);

        sender.sendMessage(messages.msg("report.header", "date", DATE.format(result.timestamp)));
        if (findings.isEmpty()) {
            sender.sendMessage(messages.msg("scan.no-issues"));
        } else {
            int from = (current - 1) * perPage;
            int to = Math.min(findings.size(), from + perPage);
            for (int i = from; i < to; i++) {
                sendFinding(sender, findings.get(i), i + 1);
            }
            sender.sendMessage(pageFooter(current, pages));
        }
        if (result.reportFile() != null) {
            sender.sendMessage(messages.msg("report.file-line", "path", result.reportFile()));
        }
    }

    private void sendFinding(CommandSender sender, Finding finding, int index) {
        int severity = (int) Math.round(finding.severity());
        Component line = messages.msg("report.finding",
                merge(finding.args(),
                        "index", String.valueOf(index),
                        "severity", String.valueOf(severity),
                        "color", severityColor(severity),
                        "cause", messages.raw(finding.key() + ".cause", finding.args())));
        if (finding.hasChunk()) {
            line = line.append(teleportButton(finding.world(), finding.chunkX(), finding.chunkZ()));
        }
        sender.sendMessage(line);
        sender.sendMessage(messages.msg("report.action",
                "action", messages.raw(finding.key() + ".action", finding.args())));
    }

    private Component pageFooter(int page, int pages) {
        Component footer = Component.empty();
        if (page > 1) {
            footer = footer.append(messages.msg("report.prev-page")
                    .clickEvent(ClickEvent.runCommand("/lagdoctor report " + (page - 1))));
        }
        footer = footer.append(messages.msg("report.footer",
                "page", String.valueOf(page), "pages", String.valueOf(pages)));
        if (page < pages) {
            footer = footer.append(messages.msg("report.next-page")
                    .clickEvent(ClickEvent.runCommand("/lagdoctor report " + (page + 1))));
        }
        return footer;
    }

    public Component teleportButton(String world, int chunkX, int chunkZ) {
        return messages.msg("report.chunk-tag")
                .hoverEvent(HoverEvent.showText(messages.msg("report.chunk-hover",
                        "world", world,
                        "x", String.valueOf(chunkX),
                        "z", String.valueOf(chunkZ))))
                .clickEvent(ClickEvent.runCommand(
                        "/lagdoctor tp " + world + " " + chunkX + " " + chunkZ));
    }

    public void sendTop(CommandSender sender, ScanResult result, String metric, int count) {
        boolean hoppers = metric.equalsIgnoreCase("hoppers");
        Comparator<ChunkStats> order = hoppers
                ? Comparator.comparingInt((ChunkStats c) -> c.hoppers).reversed()
                : Comparator.comparingInt((ChunkStats c) -> c.totalEntities).reversed();
        String metricLabel = messages.raw(hoppers ? "top.metric-hoppers" : "top.metric-entities");

        sender.sendMessage(messages.msg("top.header", "metric", metricLabel));
        List<ChunkStats> top = result.chunks.stream().sorted(order).limit(count).toList();
        int index = 1;
        for (ChunkStats c : top) {
            int value = hoppers ? c.hoppers : c.totalEntities;
            Component line = messages.msg("top.line",
                    "index", String.valueOf(index++),
                    "world", c.world,
                    "x", String.valueOf(c.x),
                    "z", String.valueOf(c.z),
                    "count", String.valueOf(value),
                    "metric", metricLabel);
            sender.sendMessage(line.append(teleportButton(c.world, c.x, c.z)));
        }
    }

    public void sendTickSummary(CommandSender sender, TickStats tick, double spikeThresholdMs) {
        if (tick.sampleCount() == 0) {
            sender.sendMessage(messages.prefixed("tps.no-data"));
            return;
        }
        sender.sendMessage(messages.msg("tps.header"));
        sender.sendMessage(messages.msg("tps.line-tps",
                "color", tick.tps() >= 19.5 ? "&a" : tick.tps() >= 17 ? "&e" : "&c",
                "tps", fmt(tick.tps())));
        sender.sendMessage(messages.msg("tps.line-mspt",
                "avg", fmt(tick.avgMspt()),
                "p95", fmt(tick.p95Mspt()),
                "max", fmt(tick.maxMspt())));
        sender.sendMessage(messages.msg("tps.line-spikes",
                "spikes", String.valueOf(tick.spikeCount()),
                "threshold", String.valueOf((int) spikeThresholdMs),
                "window", String.valueOf((int) tick.windowSeconds())));
    }

    private static String severityColor(int severity) {
        if (severity >= 80) {
            return "&c";
        }
        if (severity >= 60) {
            return "&6";
        }
        if (severity >= 40) {
            return "&e";
        }
        return "&7";
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String[] merge(String[] base, String... extra) {
        String[] merged = new String[base.length + extra.length];
        System.arraycopy(base, 0, merged, 0, base.length);
        System.arraycopy(extra, 0, merged, base.length, extra.length);
        return merged;
    }
}
