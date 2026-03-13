package de.mcbesser.skycity.model;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class IslandData {
    private final UUID owner;
    private int gridX;
    private int gridZ;
    private Location islandSpawn;
    private Location coreLocation;
    private final Set<UUID> trusted = new HashSet<>();
    private final Set<UUID> trustedContainers = new HashSet<>();
    private final Set<UUID> trustedRedstone = new HashSet<>();
    private final Set<UUID> coOwners = new HashSet<>();
    private final Set<UUID> islandOwners = new HashSet<>();
    private final Set<UUID> islandBanned = new HashSet<>();
    private final Set<String> unlockedChunks = new HashSet<>();
    private final Set<String> generatedChunks = new HashSet<>();
    private final Map<String, Integer> progress = new HashMap<>();
    private final Map<String, Integer> cachedBlockCounts = new HashMap<>();
    private final Map<String, ParcelData> parcels = new HashMap<>();
    private final Map<String, Long> growthBoostUntil = new HashMap<>();
    private final Map<String, Integer> growthBoostTier = new HashMap<>();
    private final AccessSettings islandVisitorSettings = new AccessSettings();
    private int level = 1;
    private int availableChunkUnlocks = 0;
    private String title;
    private String warpName;
    private Location warpLocation;
    private String coreDisplayMode = "ALL";
    private String islandTimeMode = "NORMAL";
    private long points = 0L;
    private long storedExperience = 0L;
    private long lastActiveAt = 0L;

    public IslandData(UUID owner) {
        this.owner = owner;
    }

    public UUID getOwner() { return owner; }
    public int getGridX() { return gridX; }
    public void setGridX(int gridX) { this.gridX = gridX; }
    public int getGridZ() { return gridZ; }
    public void setGridZ(int gridZ) { this.gridZ = gridZ; }
    public Location getIslandSpawn() { return islandSpawn; }
    public void setIslandSpawn(Location islandSpawn) { this.islandSpawn = islandSpawn; }
    public Location getCoreLocation() { return coreLocation; }
    public void setCoreLocation(Location coreLocation) { this.coreLocation = coreLocation; }
    public Set<UUID> getTrusted() { return trusted; }
    public Set<UUID> getTrustedContainers() { return trustedContainers; }
    public Set<UUID> getTrustedRedstone() { return trustedRedstone; }
    public Set<UUID> getCoOwners() { return coOwners; }
    public Set<UUID> getIslandOwners() { return islandOwners; }
    public Set<UUID> getIslandBanned() { return islandBanned; }
    public Set<String> getUnlockedChunks() { return unlockedChunks; }
    public Set<String> getGeneratedChunks() { return generatedChunks; }
    public Map<String, Integer> getProgress() { return progress; }
    public Map<String, Integer> getCachedBlockCounts() { return cachedBlockCounts; }
    public Map<String, ParcelData> getParcels() { return parcels; }
    public Map<String, Long> getGrowthBoostUntil() { return growthBoostUntil; }
    public Map<String, Integer> getGrowthBoostTier() { return growthBoostTier; }
    public AccessSettings getIslandVisitorSettings() { return islandVisitorSettings; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public int getAvailableChunkUnlocks() { return availableChunkUnlocks; }
    public void setAvailableChunkUnlocks(int availableChunkUnlocks) { this.availableChunkUnlocks = availableChunkUnlocks; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getWarpName() { return warpName; }
    public void setWarpName(String warpName) { this.warpName = warpName; }
    public Location getWarpLocation() { return warpLocation; }
    public void setWarpLocation(Location warpLocation) { this.warpLocation = warpLocation; }
    public String getCoreDisplayMode() { return coreDisplayMode; }
    public void setCoreDisplayMode(String coreDisplayMode) {
        this.coreDisplayMode = (coreDisplayMode == null || coreDisplayMode.isBlank())
                ? "ALL"
                : coreDisplayMode.toUpperCase(java.util.Locale.ROOT);
    }
    public String getIslandTimeMode() { return islandTimeMode; }
    public void setIslandTimeMode(String islandTimeMode) {
        this.islandTimeMode = (islandTimeMode == null || islandTimeMode.isBlank())
                ? "NORMAL"
                : islandTimeMode.toUpperCase(java.util.Locale.ROOT);
    }
    public long getPoints() { return points; }
    public void setPoints(long points) { this.points = points; }
    public long getStoredExperience() { return storedExperience; }
    public void setStoredExperience(long storedExperience) { this.storedExperience = Math.max(0L, storedExperience); }
    public void addStoredExperience(long amount) { this.storedExperience = Math.max(0L, this.storedExperience + Math.max(0L, amount)); }
    public boolean takeStoredExperience(long amount) {
        long req = Math.max(0L, amount);
        if (storedExperience < req) return false;
        storedExperience -= req;
        return true;
    }
    public long getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(long lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public void addProgress(Material material, int amount) {
        progress.merge(material.name(), amount, Integer::sum);
    }

    public int getProgress(Material material) {
        return progress.getOrDefault(material.name(), 0);
    }

    public int takeProgress(Material material, int amount) {
        if (material == null || amount <= 0) return 0;
        String key = material.name();
        int current = progress.getOrDefault(key, 0);
        int taken = Math.min(current, amount);
        int remaining = current - taken;
        if (remaining > 0) progress.put(key, remaining);
        else progress.remove(key);
        return taken;
    }
}



