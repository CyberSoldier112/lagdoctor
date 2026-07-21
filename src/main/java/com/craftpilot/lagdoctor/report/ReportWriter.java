package com.craftpilot.lagdoctor.report;

import com.craftpilot.lagdoctor.Messages;
import com.craftpilot.lagdoctor.rules.Finding;
import com.craftpilot.lagdoctor.scan.ChunkStats;
import com.craftpilot.lagdoctor.scan.ScanResult;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Writes timestamped markdown reports to plugins/LagDoctor/reports/ off the
 * main thread and rotates old files past the configured limit.
 */
public final class ReportWriter {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter HUMAN_STAMP = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final Messages messages;

    public ReportWriter(JavaPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    /**
     * Builds the markdown on the calling (main) thread — cheap string work —
     * then performs all file IO async. {@code onDone} runs back on the main
     * thread with the saved path, or null on failure.
     */
    public void writeAsync(ScanResult result, int maxSavedReports, Consumer<String> onDone) {
        String content = render(result);
        String fileName = "scan-" + FILE_STAMP.format(result.timestamp) + ".md";
        File reportsDir = new File(plugin.getDataFolder(), "reports");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String savedPath = null;
            try {
                if (reportsDir.isDirectory() || reportsDir.mkdirs()) {
                    File target = new File(reportsDir, fileName);
                    Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
                    savedPath = target.getPath();
                    rotate(reportsDir, maxSavedReports);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not write report file: " + e.getMessage());
            }
            String finalPath = savedPath;
            Bukkit.getScheduler().runTask(plugin, () -> onDone.accept(finalPath));
        });
    }

    private void rotate(File reportsDir, int maxSavedReports) {
        File[] files = reportsDir.listFiles((dir, name) ->
                name.startsWith("scan-") && name.endsWith(".md"));
        if (files == null || files.length <= maxSavedReports) {
            return;
        }
        // timestamped names sort chronologically
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (int i = 0; i < files.length - maxSavedReports; i++) {
            if (!files[i].delete()) {
                plugin.getLogger().warning("Could not delete old report: " + files[i].getName());
            }
        }
    }

    private String render(ScanResult result) {
        StringBuilder md = new StringBuilder();
        md.append("# LagDoctor Scan Report — ").append(HUMAN_STAMP.format(result.timestamp)).append("\n\n");
        md.append("- ").append(messages.plain("file.worlds")).append(": ")
                .append(String.join(", ", result.worlds)).append('\n');
        md.append("- ").append(messages.plain("file.chunks-scanned")).append(": ")
                .append(result.chunks.size()).append('\n');
        md.append(String.format(Locale.ROOT,
                        "- MSPT avg %.1f | p95 %.1f | max %.1f | TPS %.1f | %s: %d%n",
                        result.tick.avgMspt(), result.tick.p95Mspt(), result.tick.maxMspt(),
                        result.tick.tps(), messages.plain("file.spikes"), result.tick.spikeCount()))
                .append('\n');

        md.append("## ").append(messages.plain("file.findings"))
                .append(" (").append(result.findings.size()).append(")\n\n");
        if (result.findings.isEmpty()) {
            md.append(messages.plain("scan.no-issues")).append('\n');
        } else {
            int index = 1;
            for (Finding finding : result.findings) {
                md.append(index++).append(". **[").append((int) Math.round(finding.severity()))
                        .append("]** ").append(messages.plain(finding.key() + ".cause", finding.args()))
                        .append('\n');
                md.append("   - ").append(messages.plain(finding.key() + ".action", finding.args()))
                        .append('\n');
                if (finding.hasChunk()) {
                    md.append("   - `/lagdoctor tp ").append(finding.world()).append(' ')
                            .append(finding.chunkX()).append(' ').append(finding.chunkZ()).append("`\n");
                }
            }
        }
        md.append('\n');

        appendTopTable(md, result, false);
        appendTopTable(md, result, true);
        return md.toString();
    }

    private void appendTopTable(StringBuilder md, ScanResult result, boolean hoppers) {
        List<ChunkStats> top = result.chunks.stream()
                .sorted(hoppers
                        ? Comparator.comparingInt((ChunkStats c) -> c.hoppers).reversed()
                        : Comparator.comparingInt((ChunkStats c) -> c.totalEntities).reversed())
                .limit(10)
                .toList();
        if (top.isEmpty()) {
            return;
        }
        md.append("## ").append(messages.plain(hoppers ? "file.top-hoppers" : "file.top-entities"))
                .append("\n\n");
        md.append("| # | World | Chunk | Entities | Items | Tile entities | Hoppers |\n");
        md.append("|---|-------|-------|----------|-------|---------------|--------|\n");
        int index = 1;
        for (ChunkStats c : top) {
            md.append(String.format(Locale.ROOT, "| %d | %s | %d,%d | %d | %d | %d | %d |%n",
                    index++, c.world, c.x, c.z,
                    c.totalEntities, c.droppedItems, c.tileEntities, c.hoppers));
        }
        md.append('\n');
    }
}
