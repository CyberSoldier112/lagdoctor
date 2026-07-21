package com.craftpilot.lagdoctor.command;

import com.craftpilot.lagdoctor.LagDoctorPlugin;
import com.craftpilot.lagdoctor.Messages;
import com.craftpilot.lagdoctor.ScanService;
import com.craftpilot.lagdoctor.sampler.TickSampler;
import com.craftpilot.lagdoctor.scan.ScanResult;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LagDoctorCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS =
            List.of("scan", "report", "top", "tps", "tp", "reload");

    private final LagDoctorPlugin plugin;
    private final TickSampler sampler;
    private final Messages messages;
    private final ScanService scanService;

    public LagDoctorCommand(LagDoctorPlugin plugin, TickSampler sampler,
                            Messages messages, ScanService scanService) {
        this.plugin = plugin;
        this.sampler = sampler;
        this.messages = messages;
        this.scanService = scanService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(messages.prefixed("general.unknown-command"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "scan" -> scan(sender, args);
            case "report" -> report(sender, args);
            case "top" -> top(sender, args);
            case "tps" -> tps(sender);
            case "tp" -> teleport(sender, args);
            case "reload" -> reload(sender);
            default -> sender.sendMessage(messages.prefixed("general.unknown-command"));
        }
        return true;
    }

    private boolean denied(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(messages.prefixed("general.no-permission"));
            return true;
        }
        return false;
    }

    private void scan(CommandSender sender, String[] args) {
        if (denied(sender, "lagdoctor.scan")) {
            return;
        }
        List<World> worlds;
        if (args.length >= 2) {
            World world = findWorld(args[1]);
            if (world == null) {
                sender.sendMessage(messages.prefixed("scan.unknown-world", "world", args[1]));
                return;
            }
            worlds = List.of(world);
        } else {
            worlds = Bukkit.getWorlds();
        }
        scanService.startScan(sender, worlds);
    }

    private void report(CommandSender sender, String[] args) {
        if (denied(sender, "lagdoctor.scan")) {
            return;
        }
        ScanResult result = scanService.lastResult();
        if (result == null) {
            sender.sendMessage(messages.prefixed("report.none"));
            return;
        }
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                // fall back to page 1
            }
        }
        scanService.renderer().sendPage(sender, result, page, scanService.findingsPerPage());
    }

    private void top(CommandSender sender, String[] args) {
        if (denied(sender, "lagdoctor.scan")) {
            return;
        }
        ScanResult result = scanService.lastResult();
        if (result == null) {
            sender.sendMessage(messages.prefixed("top.none"));
            return;
        }
        String metric = args.length >= 2 ? args[1] : "entities";
        int count = Math.max(1, plugin.getConfig().getInt("scan.top-chunk-count", 10));
        scanService.renderer().sendTop(sender, result, metric, count);
    }

    private void tps(CommandSender sender) {
        if (denied(sender, "lagdoctor.tps")) {
            return;
        }
        scanService.renderer().sendTickSummary(sender, sampler.snapshot(), sampler.spikeThresholdMs());
    }

    private void teleport(CommandSender sender, String[] args) {
        if (denied(sender, "lagdoctor.teleport")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.prefixed("general.players-only"));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(messages.prefixed("tp.usage"));
            return;
        }
        World world = findWorld(args[1]);
        if (world == null) {
            sender.sendMessage(messages.prefixed("scan.unknown-world", "world", args[1]));
            return;
        }
        int chunkX;
        int chunkZ;
        try {
            chunkX = Integer.parseInt(args[2]);
            chunkZ = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(messages.prefixed("tp.bad-number"));
            return;
        }
        world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk -> {
            int blockX = (chunkX << 4) + 8;
            int blockZ = (chunkZ << 4) + 8;
            int y = world.getHighestBlockYAt(blockX, blockZ) + 1;
            player.teleportAsync(new Location(world, blockX + 0.5, y, blockZ + 0.5))
                    .thenRun(() -> player.sendMessage(messages.prefixed("tp.done",
                            "world", world.getName(),
                            "x", String.valueOf(chunkX),
                            "z", String.valueOf(chunkZ))));
        });
    }

    private void reload(CommandSender sender) {
        if (denied(sender, "lagdoctor.admin")) {
            return;
        }
        plugin.reloadAll();
        sender.sendMessage(messages.prefixed("general.reload-done"));
    }

    private static World findWorld(String name) {
        World world = Bukkit.getWorld(name);
        if (world != null) {
            return world;
        }
        for (World candidate : Bukkit.getWorlds()) {
            if (candidate.getName().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "scan", "tp" -> {
                    return filter(Bukkit.getWorlds().stream().map(World::getName).toList(), args[1]);
                }
                case "top" -> {
                    return filter(List.of("entities", "hoppers"), args[1]);
                }
                default -> {
                    return List.of();
                }
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
