package com.craftpilot.lagdoctor.scan;

import java.util.HashMap;
import java.util.Map;

/** Per-chunk counters collected during a scan. */
public final class ChunkStats {

    public final String world;
    public final int x;
    public final int z;

    public int totalEntities;
    public int livingEntities;
    public int droppedItems;
    public int tileEntities;
    public int hoppers;
    public int spawners;
    public int furnaces;

    public final Map<String, Integer> entityTypes = new HashMap<>();
    public final Map<String, Integer> livingTypes = new HashMap<>();

    public ChunkStats(String world, int x, int z) {
        this.world = world;
        this.x = x;
        this.z = z;
    }

    public int regionX() {
        return x >> 5;
    }

    public int regionZ() {
        return z >> 5;
    }

    /** Most common living entity type in this chunk, or null if none. */
    public Map.Entry<String, Integer> topLivingType() {
        Map.Entry<String, Integer> top = null;
        for (Map.Entry<String, Integer> entry : livingTypes.entrySet()) {
            if (top == null || entry.getValue() > top.getValue()) {
                top = entry;
            }
        }
        return top;
    }
}
