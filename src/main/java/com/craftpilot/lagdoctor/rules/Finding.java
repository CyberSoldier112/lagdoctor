package com.craftpilot.lagdoctor.rules;

/**
 * A ranked diagnosis. {@code key} is a message-key base; the renderer resolves
 * {@code key + ".cause"} and {@code key + ".action"} from the language files.
 * {@code args} is a flat key,value,key,value... placeholder array.
 */
public final class Finding {

    private final double severity;
    private final String key;
    private final String[] args;
    private final String world;
    private final Integer chunkX;
    private final Integer chunkZ;

    public Finding(double severity, String key, String[] args) {
        this(severity, key, args, null, null, null);
    }

    public Finding(double severity, String key, String[] args, String world, Integer chunkX, Integer chunkZ) {
        this.severity = severity;
        this.key = key;
        this.args = args;
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public double severity() {
        return severity;
    }

    public String key() {
        return key;
    }

    public String[] args() {
        return args;
    }

    public boolean hasChunk() {
        return world != null && chunkX != null && chunkZ != null;
    }

    public String world() {
        return world;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }
}
