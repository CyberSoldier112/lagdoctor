package com.craftpilot.lagdoctor.scan;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Furnace;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Time-sliced scan over currently loaded chunks: at most {@code chunksPerTick}
 * chunks are inspected per tick on the main thread so a large world never
 * blocks the server for a full sweep.
 */
public final class ChunkScanner {

    private final JavaPlugin plugin;
    private BukkitTask task;

    public ChunkScanner(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return task != null;
    }

    /** Returns the number of chunks queued, or -1 if a scan is already running. */
    public int start(List<World> worlds, int chunksPerTick, Consumer<List<ChunkStats>> onComplete) {
        if (task != null) {
            return -1;
        }
        ArrayDeque<Chunk> queue = new ArrayDeque<>();
        for (World world : worlds) {
            Collections.addAll(queue, world.getLoadedChunks());
        }
        int queued = queue.size();
        List<ChunkStats> results = new ArrayList<>(queued);
        int perTick = Math.max(1, chunksPerTick);

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int processed = 0;
            while (processed < perTick && !queue.isEmpty()) {
                Chunk chunk = queue.poll();
                if (chunk.isLoaded()) {
                    results.add(scanChunk(chunk));
                    processed++;
                }
            }
            if (queue.isEmpty()) {
                stop();
                onComplete.accept(results);
            }
        }, 1L, 1L);
        return queued;
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private ChunkStats scanChunk(Chunk chunk) {
        ChunkStats stats = new ChunkStats(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player) {
                continue;
            }
            stats.totalEntities++;
            String type = entity.getType().name();
            stats.entityTypes.merge(type, 1, Integer::sum);
            if (entity instanceof Item) {
                stats.droppedItems++;
            } else if (entity instanceof LivingEntity) {
                stats.livingEntities++;
                stats.livingTypes.merge(type, 1, Integer::sum);
            }
        }
        for (BlockState state : chunk.getTileEntities(false)) {
            stats.tileEntities++;
            if (state instanceof Hopper) {
                stats.hoppers++;
            } else if (state instanceof CreatureSpawner) {
                stats.spawners++;
            } else if (state instanceof Furnace) {
                stats.furnaces++;
            }
        }
        return stats;
    }
}
