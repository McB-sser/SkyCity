package de.mcbesser.skycity.model;

import org.bukkit.Location;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ParcelData {
    private final String chunkKey;
    private String name;
    private final Set<UUID> owners = new HashSet<>();
    private final Set<UUID> users = new HashSet<>();
    private final Set<UUID> banned = new HashSet<>();
    private final Set<UUID> pvpWhitelist = new HashSet<>();
    private final Map<UUID, Integer> pvpKills = new HashMap<>();
    private final AccessSettings visitorSettings = new AccessSettings();
    private boolean pvpEnabled;
    private Location spawn;
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    public ParcelData(String chunkKey) {
        this.chunkKey = chunkKey;
        this.name = chunkKey;
    }

    public String getChunkKey() { return chunkKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Set<UUID> getOwners() { return owners; }
    public Set<UUID> getUsers() { return users; }
    public Set<UUID> getBanned() { return banned; }
    public Set<UUID> getPvpWhitelist() { return pvpWhitelist; }
    public Map<UUID, Integer> getPvpKills() { return pvpKills; }
    public AccessSettings getVisitorSettings() { return visitorSettings; }
    public boolean isPvpEnabled() { return pvpEnabled; }
    public void setPvpEnabled(boolean pvpEnabled) { this.pvpEnabled = pvpEnabled; }
    public Location getSpawn() { return spawn; }
    public void setSpawn(Location spawn) { this.spawn = spawn; }
    public int getMinX() { return minX; }
    public void setMinX(int minX) { this.minX = minX; }
    public int getMinY() { return minY; }
    public void setMinY(int minY) { this.minY = minY; }
    public int getMinZ() { return minZ; }
    public void setMinZ(int minZ) { this.minZ = minZ; }
    public int getMaxX() { return maxX; }
    public void setMaxX(int maxX) { this.maxX = maxX; }
    public int getMaxY() { return maxY; }
    public void setMaxY(int maxY) { this.maxY = maxY; }
    public int getMaxZ() { return maxZ; }
    public void setMaxZ(int maxZ) { this.maxZ = maxZ; }

    public void setBounds(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public boolean hasBounds() {
        return maxX >= minX && maxY >= minY && maxZ >= minZ;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean intersects(ParcelData other) {
        if (other == null) return false;
        return this.maxX >= other.minX && this.minX <= other.maxX
                && this.maxY >= other.minY && this.minY <= other.maxY
                && this.maxZ >= other.minZ && this.minZ <= other.maxZ;
    }
}



