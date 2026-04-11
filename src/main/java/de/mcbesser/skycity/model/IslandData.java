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
    private final Set<UUID> memberBuildAccess = new HashSet<>();
    private final Set<UUID> memberContainerAccess = new HashSet<>();
    private final Set<UUID> memberRedstoneAccess = new HashSet<>();
    private final Set<UUID> masters = new HashSet<>();
    private final Set<UUID> owners = new HashSet<>();
    private final Set<UUID> islandBanned = new HashSet<>();
    private final Set<String> unlockedChunks = new HashSet<>();
    private final Set<String> generatedChunks = new HashSet<>();
    private final Map<String, Integer> progress = new HashMap<>();
    private final Map<String, Integer> upgradeTiers = new HashMap<>();
    private final Map<String, Integer> cachedBlockCounts = new HashMap<>();
    private final Map<String, Float> checkpointPlateYaw = new HashMap<>();
    private final Map<String, ParcelData> parcels = new HashMap<>();
    private final Map<String, Long> growthBoostUntil = new HashMap<>();
    private final Map<String, Integer> growthBoostTier = new HashMap<>();
    private final Set<String> nightVisionChunks = new HashSet<>();
    private final AccessSettings islandVisitorSettings = new AccessSettings();
    private int level = 1;
    private int availableChunkUnlocks = 0;
    private String title;
    private String warpName;
    private Location warpLocation;
    private String coreDisplayMode = "ALL";
    private String pinnedUpgradeKey = "MILESTONE";
    private String islandTimeMode = "NORMAL";
    private boolean islandNightVisionEnabled;
    private long points = 0L;
    private long storedExperience = 0L;
    private long lastActiveAt = 0L;
    private int inactivityWarningStage = 0;

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
    public Set<UUID> getMemberBuildAccess() { return memberBuildAccess; }
    public Set<UUID> getMemberContainerAccess() { return memberContainerAccess; }
    public Set<UUID> getMemberRedstoneAccess() { return memberRedstoneAccess; }
    public Set<UUID> getMasters() { return masters; }
    public Set<UUID> getOwners() { return owners; }
    public Set<UUID> getIslandBanned() { return islandBanned; }
    public Set<String> getUnlockedChunks() { return unlockedChunks; }
    public Set<String> getGeneratedChunks() { return generatedChunks; }
    public Map<String, Integer> getProgress() { return progress; }
    public Map<String, Integer> getUpgradeTiers() { return upgradeTiers; }
    public Map<String, Integer> getCachedBlockCounts() { return cachedBlockCounts; }
    public Map<String, Float> getCheckpointPlateYaw() { return checkpointPlateYaw; }
    public Map<String, ParcelData> getParcels() { return parcels; }
    public Map<String, Long> getGrowthBoostUntil() { return growthBoostUntil; }
    public Map<String, Integer> getGrowthBoostTier() { return growthBoostTier; }
    public Set<String> getNightVisionChunks() { return nightVisionChunks; }
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
    public String getPinnedUpgradeKey() { return pinnedUpgradeKey; }
    public void setPinnedUpgradeKey(String pinnedUpgradeKey) {
        this.pinnedUpgradeKey = (pinnedUpgradeKey == null || pinnedUpgradeKey.isBlank())
                ? "MILESTONE"
                : pinnedUpgradeKey.toUpperCase(java.util.Locale.ROOT);
    }
    public String getIslandTimeMode() { return islandTimeMode; }
    public void setIslandTimeMode(String islandTimeMode) {
        this.islandTimeMode = (islandTimeMode == null || islandTimeMode.isBlank())
                ? "NORMAL"
                : islandTimeMode.toUpperCase(java.util.Locale.ROOT);
    }
    public boolean isIslandNightVisionEnabled() { return islandNightVisionEnabled; }
    public void setIslandNightVisionEnabled(boolean islandNightVisionEnabled) { this.islandNightVisionEnabled = islandNightVisionEnabled; }
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
    public int getInactivityWarningStage() { return inactivityWarningStage; }
    public void setInactivityWarningStage(int inactivityWarningStage) { this.inactivityWarningStage = Math.max(0, inactivityWarningStage); }

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



