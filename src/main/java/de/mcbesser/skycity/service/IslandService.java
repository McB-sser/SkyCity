package de.mcbesser.skycity.service;

import de.mcbesser.skycity.SkyCityPlugin;
import de.mcbesser.skycity.model.AccessSettings;
import de.mcbesser.skycity.model.IslandData;
import de.mcbesser.skycity.model.IslandLevelDefinition;
import de.mcbesser.skycity.model.IslandPlot;
import de.mcbesser.skycity.model.ParcelData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class IslandService {
    public enum TrustPermission { BUILD, CONTAINER, REDSTONE, ALL }
    public enum ParcelRole { OWNER, USER, PVP }
    public enum IslandTimeMode {
        NORMAL,
        DAY,
        SUNSET,
        MIDNIGHT;

        public static IslandTimeMode from(String raw) {
            if (raw == null || raw.isBlank()) return NORMAL;
            String normalized = raw.toUpperCase(Locale.ROOT);
            // Legacy mapping: previous NIGHT mode used sunset-like time.
            if ("NIGHT".equals(normalized)) return SUNSET;
            try {
                return IslandTimeMode.valueOf(normalized);
            } catch (IllegalArgumentException ex) {
                return NORMAL;
            }
        }

        public IslandTimeMode next() {
            IslandTimeMode[] values = IslandTimeMode.values();
            return values[(ordinal() + 1) % values.length];
        }
    }
    public enum ChunkUnlockResult {
        SUCCESS,
        ALREADY_UNLOCKED,
        NO_UNLOCKS_LEFT,
        OUT_OF_BOUNDS,
        NEEDS_NEIGHBOR_APPROVAL,
        PENDING_NEIGHBOR_APPROVAL,
        NO_PENDING_REQUEST,
        NOT_AUTHORIZED,
        APPROVAL_RECORDED
    }
    public record TeleportTarget(String id, String displayName, Location location, boolean parcel) { }
    private record ChunkTemplateDef(String id, Material top, Material filler, Material feature, Biome biome, int minRadius, int maxRadius) { }
    private record ThemeHit(long seed, double density) { }
    private record PregenerationTask(UUID islandOwner, int nextIndex) { }
    private record IslandCreationTask(UUID playerId) { }
    private record PveSpawnMarker(String id, Location markerLocation, Location spawnLocation, EntityType entityType, String displayName, int level, int rewardLevels) { }
    public record PveRuntimeSnapshot(String zoneKey, String parcelName, int currentWave, int requiredWaves, int activeMobCount, Map<String, String> spawnEntries) { }
    private static final class PveZoneRuntime {
        private final UUID islandOwner;
        private final String parcelKey;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final int floorArea;
        private final int startMinX;
        private final int startMinZ;
        private final int startMaxX;
        private final int startMaxZ;
        private final int startY;
        private final Location respawnLocation;
        private final List<PveSpawnMarker> markers;
        private final Set<UUID> participants = new HashSet<>();
        private final Set<UUID> activeMobIds = new HashSet<>();
        private final Map<UUID, Location> mobHomes = new HashMap<>();
        private final Map<UUID, String> mobLabels = new HashMap<>();
        private final Map<UUID, Integer> mobLevels = new HashMap<>();
        private final Map<UUID, Integer> pendingRewards = new HashMap<>();
        private final Map<UUID, Long> mobLastReachableAt = new HashMap<>();
        private final Map<UUID, Long> mobLastRangedHitAt = new HashMap<>();
        private final Set<UUID> invalidMobIds = new HashSet<>();
        private int currentWave = 0;
        private final int requiredWaves;

        private PveZoneRuntime(UUID islandOwner, String parcelKey, ParcelData parcel, int startMinX, int startMinZ, int startMaxX, int startMaxZ, int startY, Location respawnLocation, List<PveSpawnMarker> markers) {
            this.islandOwner = islandOwner;
            this.parcelKey = parcelKey;
            this.minX = parcel.getMinX();
            this.minY = parcel.getMinY();
            this.minZ = parcel.getMinZ();
            this.maxX = parcel.getMaxX();
            this.maxY = parcel.getMaxY();
            this.maxZ = parcel.getMaxZ();
            this.floorArea = Math.max(1, (parcel.getMaxX() - parcel.getMinX() + 1) * (parcel.getMaxZ() - parcel.getMinZ() + 1));
            this.startMinX = startMinX;
            this.startMinZ = startMinZ;
            this.startMaxX = startMaxX;
            this.startMaxZ = startMaxZ;
            this.startY = startY;
            this.respawnLocation = respawnLocation;
            this.markers = markers;
            this.requiredWaves = computePveRequiredWaves(this.floorArea);
        }

        private boolean contains(Location location) {
            return location != null
                    && location.getBlockX() >= minX && location.getBlockX() <= maxX
                    && location.getBlockY() >= minY && location.getBlockY() <= maxY
                    && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
        }

        private boolean isInStartZone(Location location) {
            return location != null
                    && location.getBlockY() >= startY
                    && location.getBlockX() >= startMinX && location.getBlockX() <= startMaxX
                    && location.getBlockZ() >= startMinZ && location.getBlockZ() <= startMaxZ;
        }
    }
    private static final class PendingBorderUnlockRequest {
        private final UUID requesterOwner;
        private final int relX;
        private final int relZ;
        private final Set<UUID> requiredNeighborOwners;
        private final Set<UUID> approvedNeighborOwners;
        private final long createdAt;

        private PendingBorderUnlockRequest(UUID requesterOwner, int relX, int relZ, Set<UUID> requiredNeighborOwners) {
            this.requesterOwner = requesterOwner;
            this.relX = relX;
            this.relZ = relZ;
            this.requiredNeighborOwners = new HashSet<>(requiredNeighborOwners);
            this.approvedNeighborOwners = new HashSet<>();
            this.createdAt = System.currentTimeMillis();
        }
    }

    private static final int ISLAND_CHUNKS = 64;
    private static final int TOTAL_CHUNKS = 4096;
    private static final long ONE_YEAR_MS = 365L * 24L * 60L * 60L * 1000L;
    private static final long BIOME_CHANGE_COST_CHUNK = 120L;
    private static final long BIOME_CHANGE_COST_ISLAND = 3000L;
    private static final long TIME_MODE_CHANGE_COST = 180L;
    private static final long XP_BOTTLE_POINTS = 10L;
    private static final long XP_BOTTLE_COST_POINTS = 11L; // 10% loss
    private static final int CENTER_A = 31;
    private static final int CENTER_B = 32;
    private static final int MACRO_CELL_SIZE_CHUNKS = 5;
    private static final String CACHE_INV = "inventory";
    private static final String CACHE_HOPPER = "hopper";
    private static final String CACHE_PISTON = "piston";
    private static final String CACHE_OBSERVER = "observer";
    private static final String CACHE_DISPENSER = "dispenser";
    private static final String CACHE_CACTUS = "cactus";
    private static final String CACHE_KELP = "kelp";
    private static final String CACHE_BAMBOO = "bamboo";
    private static final Set<Material> CACTUS_FAMILY = EnumSet.of(Material.CACTUS);
    private static final Set<Material> KELP_FAMILY = EnumSet.of(Material.KELP, Material.KELP_PLANT);
    private static final Set<Material> BAMBOO_FAMILY = EnumSet.of(Material.BAMBOO, Material.BAMBOO_SAPLING);
    private static final List<int[]> PREGEN_SPIRAL_ORDER = buildPregenerationSpiralOrder();

    private final SkyCityPlugin plugin;
    private final SkyWorldService skyWorldService;
    private final Map<UUID, IslandData> islands = new HashMap<>();
    private final Map<Integer, IslandLevelDefinition> levelDefinitions = IslandLevelDefinition.defaults();
    private final File dataFile;
    private final File templateFile;
    private final List<ChunkTemplateDef> templates = new ArrayList<>();
    private final Queue<PregenerationTask> pregenerationQueue = new ArrayDeque<>();
    private final Set<UUID> queuedPregenerationOwners = new HashSet<>();
    private final Queue<IslandCreationTask> islandCreationQueue = new ArrayDeque<>();
    private final Map<UUID, List<Consumer<IslandData>>> islandCreationCallbacks = new HashMap<>();
    private final Map<UUID, List<Consumer<IslandData>>> islandReadyCallbacks = new HashMap<>();
    private final Set<UUID> pendingIslandCreations = new HashSet<>();
    private final Map<UUID, UUID> pendingCoOwnerInvites = new HashMap<>();
    private final Map<UUID, Location> plotSelectionPos1 = new HashMap<>();
    private final Map<UUID, Location> plotSelectionPos2 = new HashMap<>();
    private final Map<String, PendingBorderUnlockRequest> pendingBorderUnlockRequests = new HashMap<>();
    private final Map<UUID, String> parcelPvpConsents = new HashMap<>();
    private final Map<String, PveZoneRuntime> activePveZones = new HashMap<>();
    private final Map<UUID, String> playerPveZones = new HashMap<>();
    private final Map<UUID, Location> pendingPveRespawns = new HashMap<>();
    private final Map<UUID, String> pveMobZones = new HashMap<>();
    private final NamespacedKey plotWandKey;
    private int pregenerationTaskId = -1;
    private int islandCreationTaskId = -1;
    private int cleanupTaskId = -1;
    private int pveTaskId = -1;

    public IslandService(SkyCityPlugin plugin, SkyWorldService skyWorldService) {
        this.plugin = plugin;
        this.skyWorldService = skyWorldService;
        this.dataFile = new File(plugin.getDataFolder(), "islands.yml");
        this.templateFile = new File(plugin.getDataFolder(), "chunk-templates.yml");
        this.plotWandKey = new NamespacedKey(plugin, "plot_wand");
        ensureTemplateFile();
        loadTemplates();
    }

    public void load() {
        islands.clear();
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection root = yaml.getConfigurationSection("islands");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID owner = UUID.fromString(key);
                ConfigurationSection sec = root.getConfigurationSection(key);
                if (sec == null) continue;
                IslandData island = new IslandData(owner);
                island.setGridX(sec.getInt("gridX"));
                island.setGridZ(sec.getInt("gridZ"));
                island.setLevel(sec.getInt("level", 1));
                island.setAvailableChunkUnlocks(sec.getInt("availableChunkUnlocks", 0));
                island.setTitle(sec.getString("title", null));
                island.setWarpName(sec.getString("warpName", null));
                island.setCoreDisplayMode(sec.getString("coreDisplayMode", "ALL"));
                island.setIslandTimeMode(sec.getString("islandTimeMode", "NORMAL"));
                island.setPoints(sec.getLong("points", 0L));
                island.setStoredExperience(sec.getLong("storedExperience", 0L));
                island.setLastActiveAt(sec.getLong("lastActiveAt", 0L));
                if (sec.isConfigurationSection("spawn")) island.setIslandSpawn(deserializeLocation(sec.getConfigurationSection("spawn")));
                if (sec.isConfigurationSection("warp")) island.setWarpLocation(deserializeLocation(sec.getConfigurationSection("warp")));
                if (sec.isConfigurationSection("core")) island.setCoreLocation(deserializeLocation(sec.getConfigurationSection("core")));
                for (String s : sec.getStringList("trusted")) island.getTrusted().add(UUID.fromString(s));
                for (String s : sec.getStringList("trustedContainers")) island.getTrustedContainers().add(UUID.fromString(s));
                for (String s : sec.getStringList("trustedRedstone")) island.getTrustedRedstone().add(UUID.fromString(s));
                for (String s : sec.getStringList("coOwners")) island.getCoOwners().add(UUID.fromString(s));
                for (String s : sec.getStringList("islandOwners")) island.getIslandOwners().add(UUID.fromString(s));
                for (String s : sec.getStringList("islandBanned")) island.getIslandBanned().add(UUID.fromString(s));
                island.getUnlockedChunks().addAll(sec.getStringList("unlockedChunks"));
                island.getGeneratedChunks().addAll(sec.getStringList("generatedChunks"));
                loadAccessSettings(sec.getConfigurationSection("visitorSettings"), island.getIslandVisitorSettings());
                ConfigurationSection progress = sec.getConfigurationSection("progress");
                if (progress != null) for (String mk : progress.getKeys(false)) island.getProgress().put(mk, progress.getInt(mk));
                ConfigurationSection cache = sec.getConfigurationSection("cacheBlocks");
                if (cache != null) for (String ck : cache.getKeys(false)) island.getCachedBlockCounts().put(ck, cache.getInt(ck));
                ConfigurationSection growthUntil = sec.getConfigurationSection("growthBoost.until");
                if (growthUntil != null) {
                    for (String chunkKey : growthUntil.getKeys(false)) {
                        island.getGrowthBoostUntil().put(chunkKey, growthUntil.getLong(chunkKey, 0L));
                    }
                }
                ConfigurationSection growthTier = sec.getConfigurationSection("growthBoost.tier");
                if (growthTier != null) {
                    for (String chunkKey : growthTier.getKeys(false)) {
                        island.getGrowthBoostTier().put(chunkKey, Math.max(0, growthTier.getInt(chunkKey, 0)));
                    }
                }
                ConfigurationSection parcels = sec.getConfigurationSection("parcels");
                if (parcels != null) {
                    for (String parcelKey : parcels.getKeys(false)) {
                        ConfigurationSection psec = parcels.getConfigurationSection(parcelKey);
                        if (psec == null) continue;
                        ParcelData parcel = new ParcelData(parcelKey);
                        parcel.setName(psec.getString("name", parcelKey));
                        for (String s : psec.getStringList("owners")) parcel.getOwners().add(UUID.fromString(s));
                        for (String s : psec.getStringList("users")) parcel.getUsers().add(UUID.fromString(s));
                        for (String s : psec.getStringList("banned")) parcel.getBanned().add(UUID.fromString(s));
                        for (String s : psec.getStringList("pvpWhitelist")) parcel.getPvpWhitelist().add(UUID.fromString(s));
                        if (psec.contains("minX") && psec.contains("maxX") && psec.contains("minY") && psec.contains("maxY") && psec.contains("minZ") && psec.contains("maxZ")) {
                            parcel.setBounds(
                                    psec.getInt("minX"),
                                    psec.getInt("minY"),
                                    psec.getInt("minZ"),
                                    psec.getInt("maxX"),
                                    psec.getInt("maxY"),
                                    psec.getInt("maxZ")
                            );
                        } else {
                            int[] legacy = legacyChunkParcelBounds(island, parcelKey);
                            if (legacy != null) {
                                parcel.setBounds(legacy[0], legacy[1], legacy[2], legacy[3], legacy[4], legacy[5]);
                            }
                        }
                        if (psec.isConfigurationSection("spawn")) parcel.setSpawn(deserializeLocation(psec.getConfigurationSection("spawn")));
                        parcel.setPvpEnabled(psec.getBoolean("pvpEnabled", false));
                        parcel.setPveEnabled(psec.getBoolean("pveEnabled", false));
                        ConfigurationSection pvpKills = psec.getConfigurationSection("pvpKills");
                        if (pvpKills != null) {
                            for (String playerKey : pvpKills.getKeys(false)) {
                                parcel.getPvpKills().put(UUID.fromString(playerKey), Math.max(0, pvpKills.getInt(playerKey, 0)));
                            }
                        }
                        loadAccessSettings(psec.getConfigurationSection("visitorSettings"), parcel.getVisitorSettings());
                        island.getParcels().put(parcelKey, parcel);
                    }
                }
                islands.put(owner, island);
            } catch (Exception ex) {
                plugin.getLogger().warning("Insel konnte nicht geladen werden: " + key + " (" + ex.getMessage() + ")");
            }
        }
        for (IslandData island : islands.values()) {
            if (island.getPoints() <= 0) island.setPoints(Math.max(1, island.getGeneratedChunks().size()));
            if (island.getLastActiveAt() <= 0) island.setLastActiveAt(System.currentTimeMillis());
            rebuildPlacementCaches(island);
            if (!isIslandFullyPregenerated(island)) {
                queuePregeneration(island);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, IslandData> entry : islands.entrySet()) {
            String p = "islands." + entry.getKey();
            IslandData i = entry.getValue();
            yaml.set(p + ".gridX", i.getGridX());
            yaml.set(p + ".gridZ", i.getGridZ());
            yaml.set(p + ".level", i.getLevel());
            yaml.set(p + ".availableChunkUnlocks", i.getAvailableChunkUnlocks());
            yaml.set(p + ".title", i.getTitle());
            yaml.set(p + ".warpName", i.getWarpName());
            yaml.set(p + ".coreDisplayMode", i.getCoreDisplayMode());
            yaml.set(p + ".islandTimeMode", i.getIslandTimeMode());
            yaml.set(p + ".points", i.getPoints());
            yaml.set(p + ".storedExperience", i.getStoredExperience());
            yaml.set(p + ".lastActiveAt", i.getLastActiveAt());
            if (i.getIslandSpawn() != null) serializeLocation(yaml, p + ".spawn", i.getIslandSpawn());
            if (i.getWarpLocation() != null) serializeLocation(yaml, p + ".warp", i.getWarpLocation());
            if (i.getCoreLocation() != null) serializeLocation(yaml, p + ".core", i.getCoreLocation());
            yaml.set(p + ".trusted", stringify(i.getTrusted()));
            yaml.set(p + ".trustedContainers", stringify(i.getTrustedContainers()));
            yaml.set(p + ".trustedRedstone", stringify(i.getTrustedRedstone()));
            yaml.set(p + ".coOwners", stringify(i.getCoOwners()));
            yaml.set(p + ".islandOwners", stringify(i.getIslandOwners()));
            yaml.set(p + ".islandBanned", stringify(i.getIslandBanned()));
            yaml.set(p + ".unlockedChunks", new ArrayList<>(i.getUnlockedChunks()));
            yaml.set(p + ".generatedChunks", new ArrayList<>(i.getGeneratedChunks()));
            saveAccessSettings(yaml, p + ".visitorSettings", i.getIslandVisitorSettings());
            for (Map.Entry<String, Integer> prog : i.getProgress().entrySet()) yaml.set(p + ".progress." + prog.getKey(), prog.getValue());
            for (Map.Entry<String, Integer> c : i.getCachedBlockCounts().entrySet()) yaml.set(p + ".cacheBlocks." + c.getKey(), c.getValue());
            for (Map.Entry<String, Long> b : i.getGrowthBoostUntil().entrySet()) yaml.set(p + ".growthBoost.until." + b.getKey(), b.getValue());
            for (Map.Entry<String, Integer> b : i.getGrowthBoostTier().entrySet()) yaml.set(p + ".growthBoost.tier." + b.getKey(), b.getValue());
            for (ParcelData parcel : i.getParcels().values()) {
                String pp = p + ".parcels." + parcel.getChunkKey();
                yaml.set(pp + ".name", getParcelDisplayName(parcel));
                yaml.set(pp + ".owners", stringify(parcel.getOwners()));
                yaml.set(pp + ".users", stringify(parcel.getUsers()));
                yaml.set(pp + ".banned", stringify(parcel.getBanned()));
                yaml.set(pp + ".pvpWhitelist", stringify(parcel.getPvpWhitelist()));
                yaml.set(pp + ".minX", parcel.getMinX());
                yaml.set(pp + ".minY", parcel.getMinY());
                yaml.set(pp + ".minZ", parcel.getMinZ());
                yaml.set(pp + ".maxX", parcel.getMaxX());
                yaml.set(pp + ".maxY", parcel.getMaxY());
                yaml.set(pp + ".maxZ", parcel.getMaxZ());
                if (parcel.getSpawn() != null) serializeLocation(yaml, pp + ".spawn", parcel.getSpawn());
                yaml.set(pp + ".pvpEnabled", parcel.isPvpEnabled());
                yaml.set(pp + ".pveEnabled", parcel.isPveEnabled());
                for (Map.Entry<UUID, Integer> pvpEntry : parcel.getPvpKills().entrySet()) {
                    yaml.set(pp + ".pvpKills." + pvpEntry.getKey(), Math.max(0, pvpEntry.getValue()));
                }
                saveAccessSettings(yaml, pp + ".visitorSettings", parcel.getVisitorSettings());
            }
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Konnte islands.yml nicht speichern: " + e.getMessage());
        }
    }

    public void startPregenerationTask() {
        stopPregenerationTask();
        pregenerationTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickPregeneration, 40L, 1L);
    }

    public void stopPregenerationTask() {
        if (pregenerationTaskId != -1) {
            Bukkit.getScheduler().cancelTask(pregenerationTaskId);
            pregenerationTaskId = -1;
        }
        if (cleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId);
            cleanupTaskId = -1;
        }
    }

    public void startIslandCreationTask() {
        stopIslandCreationTask();
        islandCreationTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickIslandCreationQueue, 20L, 2L);
        if (cleanupTaskId == -1) {
            cleanupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::runInactiveIslandCleanup, 20L * 60L, 20L * 60L * 60L);
        }
        if (pveTaskId == -1) {
            pveTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickPveZones, 20L, 20L);
        }
    }

    public void stopIslandCreationTask() {
        if (islandCreationTaskId != -1) {
            Bukkit.getScheduler().cancelTask(islandCreationTaskId);
            islandCreationTaskId = -1;
        }
        if (pveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(pveTaskId);
            pveTaskId = -1;
        }
        resetAllPveZones();
        pendingIslandCreations.clear();
        islandCreationQueue.clear();
        islandCreationCallbacks.clear();
        islandReadyCallbacks.clear();
    }

    private void tickIslandCreationQueue() {
        IslandCreationTask task = islandCreationQueue.poll();
        if (task == null) {
            return;
        }
        UUID playerId = task.playerId();
        pendingIslandCreations.remove(playerId);
        IslandData island = getOrCreateIsland(playerId);
        List<Consumer<IslandData>> callbacks = islandCreationCallbacks.remove(playerId);
        if (callbacks != null) {
            for (Consumer<IslandData> callback : callbacks) {
                try {
                    callback.accept(island);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Fehler in Insel-Callback f\u00fcr " + playerId + ": " + ex.getMessage());
                }
            }
        }
    }

    public boolean isIslandCreationPending(UUID playerId) {
        return pendingIslandCreations.contains(playerId);
    }

    public boolean isIslandReady(UUID playerId) {
        IslandData island = getIsland(playerId).orElse(null);
        return island != null && isIslandFullyPregenerated(island);
    }

    public int getIslandCreationQueuePosition(UUID playerId) {
        if (!pendingIslandCreations.contains(playerId)) return -1;
        int pos = 1;
        for (IslandCreationTask task : islandCreationQueue) {
            if (task.playerId().equals(playerId)) return pos;
            pos++;
        }
        return 1;
    }

    public int getIslandPregenerationProgress(UUID playerId) {
        IslandData island = getIsland(playerId).orElse(null);
        return island == null ? 0 : Math.min(TOTAL_CHUNKS, island.getGeneratedChunks().size());
    }

    public int getTotalIslandChunkCount() {
        return TOTAL_CHUNKS;
    }

    public void queueIslandCreation(UUID playerId, Consumer<IslandData> onReady) {
        IslandData existing = islands.get(playerId);
        if (existing != null) {
            if (onReady != null) {
                onReady.accept(existing);
            }
            return;
        }
        if (onReady != null) {
            islandCreationCallbacks.computeIfAbsent(playerId, id -> new ArrayList<>()).add(onReady);
        }
        if (pendingIslandCreations.add(playerId)) {
            islandCreationQueue.offer(new IslandCreationTask(playerId));
        }
    }

    private void tickPregeneration() {
        int budget = 2;
        int maxPerIslandTask = 1;
        while (budget > 0 && !pregenerationQueue.isEmpty()) {
            PregenerationTask task = pregenerationQueue.poll();
            queuedPregenerationOwners.remove(task.islandOwner());
            IslandData island = islands.get(task.islandOwner());
            if (island == null) continue;
            int idx = task.nextIndex();
            int processed = 0;
            while (idx < TOTAL_CHUNKS && processed < budget && processed < maxPerIslandTask) {
                int relX = pregenerationRelXByIndex(idx);
                int relZ = pregenerationRelZByIndex(idx);
                ensureChunkTemplateGenerated(island, relX, relZ);
                idx++;
                processed++;
            }
            budget -= processed;
            if (idx < TOTAL_CHUNKS) {
                if (queuedPregenerationOwners.add(task.islandOwner())) {
                    pregenerationQueue.offer(new PregenerationTask(task.islandOwner(), idx));
                }
            } else {
                dispatchIslandReadyCallbacks(island);
            }
        }
    }

    private void runInactiveIslandCleanup() {
        long now = System.currentTimeMillis();
        long oneYearMs = 365L * 24L * 60L * 60L * 1000L;
        List<IslandData> toDelete = new ArrayList<>();
        for (IslandData island : islands.values()) {
            if (island.getPoints() >= 1000L) continue;
            if (now - island.getLastActiveAt() < oneYearMs) continue;
            toDelete.add(island);
        }
        for (IslandData island : toDelete) {
            deleteIslandData(island, true);
            plugin.getLogger().info("Inaktive Insel gel\u00f6scht (Punkte<1000, >1 Jahr inaktiv): " + island.getOwner());
        }
        if (!toDelete.isEmpty()) save();
    }

    private void deleteIslandData(IslandData island, boolean createReplacementForCoOwners) {
        if (island == null) return;
        islands.remove(island.getOwner());
        pendingCoOwnerInvites.entrySet().removeIf(e -> e.getValue().equals(island.getOwner()) || e.getKey().equals(island.getOwner()));
        pendingBorderUnlockRequests.entrySet().removeIf(entry ->
                entry.getValue().requesterOwner.equals(island.getOwner())
                        || entry.getValue().requiredNeighborOwners.contains(island.getOwner()));

        for (UUID coOwner : new ArrayList<>(island.getCoOwners())) {
            pendingCoOwnerInvites.remove(coOwner);
            Player online = Bukkit.getPlayer(coOwner);
            if (online != null && online.isOnline()) {
                online.teleport(getSpawnLocation());
                online.sendMessage(ChatColor.YELLOW + "Die gemeinsame Insel wurde entfernt.");
            }
            if (createReplacementForCoOwners) {
                getOrCreateIsland(coOwner);
            }
        }

        Player primary = Bukkit.getPlayer(island.getOwner());
        if (primary != null && primary.isOnline()) {
            primary.teleport(getSpawnLocation());
            primary.sendMessage(ChatColor.YELLOW + "Deine Insel wurde entfernt.");
        }
    }

    private boolean isIslandFullyPregenerated(IslandData island) {
        return island != null && island.getGeneratedChunks().size() >= TOTAL_CHUNKS;
    }

    private void dispatchIslandReadyCallbacks(IslandData island) {
        if (island == null) return;
        List<Consumer<IslandData>> callbacks = islandReadyCallbacks.remove(island.getOwner());
        if (callbacks == null) return;
        for (Consumer<IslandData> callback : callbacks) {
            try {
                callback.accept(island);
            } catch (Exception ex) {
                plugin.getLogger().warning("Fehler in Insel-Ready-Callback f\u00fcr " + island.getOwner() + ": " + ex.getMessage());
            }
        }
    }

    public void queuePregeneration(IslandData island) {
        if (island == null) return;
        if (isIslandFullyPregenerated(island)) return;
        if (queuedPregenerationOwners.add(island.getOwner())) {
            pregenerationQueue.offer(new PregenerationTask(island.getOwner(), 0));
        }
    }

    public boolean isInitialAreaGenerated(IslandData island) {
        if (island == null) return false;
        return island.getGeneratedChunks().contains(chunkKey(CENTER_A, CENTER_A))
                && island.getGeneratedChunks().contains(chunkKey(CENTER_A, CENTER_B))
                && island.getGeneratedChunks().contains(chunkKey(CENTER_B, CENTER_A))
                && island.getGeneratedChunks().contains(chunkKey(CENTER_B, CENTER_B));
    }

    private static List<int[]> buildPregenerationSpiralOrder() {
        List<int[]> order = new ArrayList<>(TOTAL_CHUNKS);
        boolean[][] visited = new boolean[ISLAND_CHUNKS][ISLAND_CHUNKS];
        int x = CENTER_A;
        int z = CENTER_A;
        addSpiralChunk(order, visited, x, z);
        int step = 1;

        while (order.size() < TOTAL_CHUNKS) {
            for (int i = 0; i < step; i++) {
                x++;
                addSpiralChunk(order, visited, x, z);
            }
            for (int i = 0; i < step; i++) {
                z++;
                addSpiralChunk(order, visited, x, z);
            }
            step++;
            for (int i = 0; i < step; i++) {
                x--;
                addSpiralChunk(order, visited, x, z);
            }
            for (int i = 0; i < step; i++) {
                z--;
                addSpiralChunk(order, visited, x, z);
            }
            step++;
        }

        if (order.size() < TOTAL_CHUNKS) {
            for (int relZ = 0; relZ < ISLAND_CHUNKS; relZ++) {
                for (int relX = 0; relX < ISLAND_CHUNKS; relX++) {
                    addSpiralChunk(order, visited, relX, relZ);
                }
            }
        }
        return order;
    }

    private static void addSpiralChunk(List<int[]> order, boolean[][] visited, int relX, int relZ) {
        if (relX < 0 || relX >= ISLAND_CHUNKS || relZ < 0 || relZ >= ISLAND_CHUNKS) return;
        if (visited[relX][relZ]) return;
        visited[relX][relZ] = true;
        order.add(new int[]{relX, relZ});
    }

    private int pregenerationRelXByIndex(int index) {
        if (index < 0 || index >= PREGEN_SPIRAL_ORDER.size()) return 0;
        return PREGEN_SPIRAL_ORDER.get(index)[0];
    }

    private int pregenerationRelZByIndex(int index) {
        if (index < 0 || index >= PREGEN_SPIRAL_ORDER.size()) return 0;
        return PREGEN_SPIRAL_ORDER.get(index)[1];
    }

    public void ensureSpawnPlotAndSpawnPlatform() {
        World world = skyWorldService.getWorld();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            world.getBlockAt(x, SkyWorldService.SPAWN_Y, z).setType(Material.BEDROCK, false);
            for (int y = SkyWorldService.SPAWN_Y + 1; y <= SkyWorldService.SPAWN_Y + 4; y++) {
                Block b = world.getBlockAt(x, y, z);
                if (!b.getType().isAir()) b.setType(Material.AIR, false);
            }
        }
        world.setSpawnLocation(0, SkyWorldService.SPAWN_Y + 1, 0);
    }

    public IslandData getOrCreateIsland(UUID owner) {
        IslandData existing = getIsland(owner).orElse(null);
        if (existing != null) return existing;
        IslandPlot plot = findNextFreePlot();
        IslandData island = new IslandData(owner);
        island.setGridX(plot.gridX());
        island.setGridZ(plot.gridZ());
        island.setPoints(1L);
        island.setLastActiveAt(System.currentTimeMillis());
        island.getUnlockedChunks().add(chunkKey(CENTER_A, CENTER_A));
        island.getUnlockedChunks().add(chunkKey(CENTER_A, CENTER_B));
        island.getUnlockedChunks().add(chunkKey(CENTER_B, CENTER_A));
        island.getUnlockedChunks().add(chunkKey(CENTER_B, CENTER_B));
        Location center = getPlotCenter(plot.gridX(), plot.gridZ());
        island.setIslandSpawn(center.clone().add(0.5, 1, 0.5));
        island.setCoreLocation(center.clone().add(0, 1, 0));
        islands.put(owner, island);
        // Starterinsel sofort erzeugen (4 freigeschaltete Startchunks), damit keine "nackte" Plattform erscheint.
        ensureChunkTemplateGenerated(island, CENTER_A, CENTER_A);
        ensureChunkTemplateGenerated(island, CENTER_A, CENTER_B);
        ensureChunkTemplateGenerated(island, CENTER_B, CENTER_A);
        ensureChunkTemplateGenerated(island, CENTER_B, CENTER_B);
        placeCentralSpawnAndCore(island);
        rebuildPlacementCaches(island);
        queuePregeneration(island);
        save();
        return island;
    }

    private void placeCentralSpawnAndCore(IslandData island) {
        World w = skyWorldService.getWorld();
        Location c = getPlotCenter(island.getGridX(), island.getGridZ());
        int cx = c.getBlockX();
        int cz = c.getBlockZ();
        int terrainTop = findTopSolidY(w, cx, cz, SkyWorldService.SPAWN_Y + 12, SkyWorldService.SPAWN_Y - 16);
        int floorY = Math.max(SkyWorldService.SPAWN_Y, terrainTop < 0 ? SkyWorldService.SPAWN_Y : terrainTop);

        // Integrated classic starter patch (grassy, not a floating cobble plate).
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            int wx = cx + x;
            int wz = cz + z;

            for (int y = floorY - 2; y <= floorY + 4; y++) {
                if (y > floorY) {
                    w.getBlockAt(wx, y, wz).setType(Material.AIR, false);
                } else if (y == floorY) {
                    w.getBlockAt(wx, y, wz).setType(Material.GRASS_BLOCK, false);
                } else {
                    w.getBlockAt(wx, y, wz).setType(Material.DIRT, false);
                }
            }

            int supportTop = findTopSolidY(w, wx, wz, floorY - 3, SkyWorldService.SPAWN_Y - 16);
            int startFill = Math.max(SkyWorldService.SPAWN_Y - 2, supportTop + 1);
            for (int y = startFill; y < floorY - 2; y++) {
                w.getBlockAt(wx, y, wz).setType(Material.DIRT, false);
            }
        }

        // Core and player spot are on top of the island surface, with local pads and free headroom.
        w.getBlockAt(cx, floorY, cz).setType(Material.COBBLESTONE, false);
        w.getBlockAt(cx + 2, floorY, cz).setType(Material.COBBLESTONE, false);
        w.getBlockAt(cx, floorY + 1, cz).setType(Material.SHULKER_BOX, false);
        for (int y = 1; y <= 3; y++) {
            w.getBlockAt(cx, floorY + 1 + y, cz).setType(Material.AIR, false);
            w.getBlockAt(cx + 2, floorY + y, cz).setType(Material.AIR, false);
        }

        placeStarterTree(w, cx - 2, floorY, cz - 2);
        placeStarterLootChest(w, cx - 2, floorY, cz + 2);
        placeStarterPortalDecoration(w, cx + 3, floorY, cz - 3);

        island.setIslandSpawn(new Location(w, cx + 2.5, floorY + 1.0, cz + 0.5));
        island.setCoreLocation(new Location(w, cx, floorY + 1, cz));
    }

    public void ensureCentralSpawnAndCoreSafe(IslandData island) {
        if (island == null) return;
        World w = skyWorldService.getWorld();
        Location center = getPlotCenter(island.getGridX(), island.getGridZ());
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int terrainTop = findTopSolidY(w, cx, cz, SkyWorldService.SPAWN_Y + 12, SkyWorldService.SPAWN_Y - 16);
        int floorY = Math.max(SkyWorldService.SPAWN_Y, terrainTop < 0 ? SkyWorldService.SPAWN_Y : terrainTop);

        boolean changed = false;
        Location core = island.getCoreLocation();
        if (core == null || core.getWorld() == null) {
            core = new Location(w, cx, floorY + 1, cz);
            island.setCoreLocation(core);
            changed = true;
        }
        Location spawn = island.getIslandSpawn();
        if (spawn == null || spawn.getWorld() == null) {
            spawn = new Location(w, cx + 2.5, floorY + 1.0, cz + 0.5);
            island.setIslandSpawn(spawn);
            changed = true;
        }

        int minSafeY = SkyWorldService.SPAWN_Y - 10;
        if (core.getBlockY() <= minSafeY) {
            core = new Location(w, cx, floorY + 1, cz);
            island.setCoreLocation(core);
            changed = true;
        }
        if (spawn.getBlockY() <= minSafeY) {
            spawn = new Location(w, cx + 2.5, floorY + 1.0, cz + 0.5);
            island.setIslandSpawn(spawn);
            changed = true;
        }

        if (core.getBlock().getType().isAir()) {
            core.getBlock().setType(Material.SHULKER_BOX, false);
            changed = true;
        }

        for (int y = 0; y <= 2; y++) {
            w.getBlockAt(core.getBlockX(), core.getBlockY() + 1 + y, core.getBlockZ()).setType(Material.AIR, false);
            w.getBlockAt(spawn.getBlockX(), spawn.getBlockY() + y, spawn.getBlockZ()).setType(Material.AIR, false);
        }
        w.getBlockAt(core.getBlockX(), core.getBlockY() - 1, core.getBlockZ()).setType(Material.COBBLESTONE, false);
        w.getBlockAt(spawn.getBlockX(), spawn.getBlockY() - 1, spawn.getBlockZ()).setType(Material.COBBLESTONE, false);
        if (changed) {
            save();
        }
    }

    private void placeStarterTree(World w, int x, int floorY, int z) {
        w.getBlockAt(x, floorY, z).setType(Material.GRASS_BLOCK, false);
        for (int y = floorY + 1; y <= floorY + 4; y++) {
            w.getBlockAt(x, y, z).setType(Material.OAK_LOG, false);
        }
        for (int lx = -2; lx <= 2; lx++) for (int lz = -2; lz <= 2; lz++) {
            if (Math.abs(lx) == 2 && Math.abs(lz) == 2) continue;
            w.getBlockAt(x + lx, floorY + 4, z + lz).setType(Material.OAK_LEAVES, false);
        }
        for (int lx = -1; lx <= 1; lx++) for (int lz = -1; lz <= 1; lz++) {
            w.getBlockAt(x + lx, floorY + 5, z + lz).setType(Material.OAK_LEAVES, false);
        }
        w.getBlockAt(x, floorY + 6, z).setType(Material.OAK_LEAVES, false);
    }

    private void placeStarterLootChest(World w, int x, int floorY, int z) {
        w.getBlockAt(x, floorY, z).setType(Material.GRASS_BLOCK, false);
        Block chestBlock = w.getBlockAt(x, floorY + 1, z);
        if (chestBlock.getType() != Material.CHEST) {
            chestBlock.setType(Material.CHEST, false);
        }
        if (!(chestBlock.getState() instanceof Chest chest)) return;
        NamespacedKey filledKey = new NamespacedKey(plugin, "starter_chest_filled");
        Byte alreadyFilled = chest.getPersistentDataContainer().get(filledKey, PersistentDataType.BYTE);
        boolean markedFilled = alreadyFilled != null && alreadyFilled == (byte) 1;
        boolean empty = isChestEmpty(chest);
        if (markedFilled && !empty) return;
        if (!markedFilled && !empty) return; // respektiere bereits manuell befuellte Kisten

        var inv = chest.getSnapshotInventory();
        inv.clear();
        inv.setItem(0, new ItemStack(Material.ICE, 16));
        inv.setItem(1, new ItemStack(Material.LAVA_BUCKET, 1));
        inv.setItem(2, new ItemStack(Material.LAVA_BUCKET, 1));
        inv.setItem(3, new ItemStack(Material.MAGMA_BLOCK, 16));
        inv.setItem(4, new ItemStack(Material.SUNFLOWER, 1));
        inv.setItem(5, new ItemStack(Material.OAK_SAPLING, 4));
        inv.setItem(6, new ItemStack(Material.BIRCH_SAPLING, 2));
        inv.setItem(7, new ItemStack(Material.SPRUCE_SAPLING, 2));
        inv.setItem(8, new ItemStack(Material.OAK_PLANKS, 32));
        inv.setItem(9, new ItemStack(Material.BREAD, 16));
        inv.setItem(10, new ItemStack(Material.TORCH, 32));
        inv.setItem(11, new ItemStack(Material.BONE_MEAL, 16));
        inv.setItem(12, new ItemStack(Material.WOODEN_PICKAXE, 1));
        inv.setItem(13, new ItemStack(Material.WOODEN_AXE, 1));
        inv.setItem(14, new ItemStack(Material.WOODEN_SHOVEL, 1));
        inv.setItem(15, new ItemStack(Material.STONE_PICKAXE, 1));
        inv.setItem(16, new ItemStack(Material.BUCKET, 1));
        inv.setItem(17, new ItemStack(Material.WHEAT_SEEDS, 16));
        inv.setItem(18, new ItemStack(Material.STRING, 16));
        inv.setItem(19, new ItemStack(Material.REDSTONE, 8));
        inv.setItem(20, new ItemStack(Material.FLINT_AND_STEEL, 1));
        inv.setItem(21, new ItemStack(Material.EXPERIENCE_BOTTLE, 64));
        inv.setItem(22, new ItemStack(Material.EXPERIENCE_BOTTLE, 64));
        inv.setItem(26, new ItemStack(Material.POINTED_DRIPSTONE, 8));
        inv.setItem(23, new ItemStack(Material.CAULDRON, 2));
        inv.setItem(24, new ItemStack(Material.KELP, 1));
        inv.setItem(25, new ItemStack(Material.RAW_IRON, 32));
        chest.getPersistentDataContainer().set(filledKey, PersistentDataType.BYTE, (byte) 1);
        chest.update(true, false);
    }

    private void placeStarterPortalDecoration(World w, int x, int floorY, int z) {
        Material frame = Material.POLISHED_ANDESITE;
        int width = 4;  // inside width 2
        int height = 5; // inside height 3

        // Small base pad
        for (int dx = -1; dx <= width; dx++) {
            w.getBlockAt(x + dx, floorY, z).setType(Material.POLISHED_ANDESITE, false);
        }

        // Clear area around decoration
        for (int dx = 0; dx < width; dx++) {
            for (int dy = 1; dy <= height + 1; dy++) {
                w.getBlockAt(x + dx, floorY + dy, z).setType(Material.AIR, false);
            }
        }

        // Frame
        for (int dy = 1; dy <= height; dy++) {
            w.getBlockAt(x, floorY + dy, z).setType(frame, false);
            w.getBlockAt(x + width - 1, floorY + dy, z).setType(frame, false);
        }
        for (int dx = 0; dx < width; dx++) {
            w.getBlockAt(x + dx, floorY + 1, z).setType(frame, false);
            w.getBlockAt(x + dx, floorY + height, z).setType(frame, false);
        }

        // "Portalfl\u00e4che" bleibt leer (deko only)
        for (int dx = 1; dx <= width - 2; dx++) {
            for (int dy = 2; dy <= height - 1; dy++) {
                w.getBlockAt(x + dx, floorY + dy, z).setType(Material.AIR, false);
            }
        }
    }

    private boolean isChestEmpty(Chest chest) {
        for (ItemStack item : chest.getBlockInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) return false;
        }
        return true;
    }

    public Optional<IslandData> getPrimaryIsland(UUID owner) { return Optional.ofNullable(islands.get(owner)); }
    public Optional<IslandData> getIsland(UUID ownerOrCoOwner, boolean includeCoOwners) {
        IslandData direct = islands.get(ownerOrCoOwner);
        if (direct != null || !includeCoOwners) return Optional.ofNullable(direct);
        return islands.values().stream().filter(i -> i.getCoOwners().contains(ownerOrCoOwner)).findFirst();
    }
    public Optional<IslandData> getIsland(UUID owner) { return getIsland(owner, true); }
    public List<IslandData> getAllIslands() { return new ArrayList<>(islands.values()); }

    public String getIslandTitleDisplay(IslandData island) {
        if (island == null) return "Unbekannte Insel";
        String raw = island.getTitle();
        if (raw != null && !raw.isBlank()) return raw;
        String ownerName = Bukkit.getOfflinePlayer(island.getOwner()).getName();
        return "Insel von " + (ownerName == null ? "?" : ownerName);
    }

    public String getIslandWarpDisplay(IslandData island) {
        if (island == null) return "Unbekannter Warp";
        String raw = island.getWarpName();
        if (raw != null && !raw.isBlank()) return raw.trim();
        return getIslandTitleDisplay(island);
    }

    public String getIslandOwnerListDisplay(IslandData island) {
        if (island == null) return "";
        List<String> names = new ArrayList<>();
        String ownerName = Bukkit.getOfflinePlayer(island.getOwner()).getName();
        names.add(ownerName == null ? "?" : ownerName);
        for (UUID co : island.getCoOwners()) {
            String name = Bukkit.getOfflinePlayer(co).getName();
            if (name != null && !name.isBlank()) names.add(name);
        }
        String joined = String.join(", ", names);
        return joined.length() > 60 ? joined.substring(0, 57) + "..." : joined;
    }

    public String getIslandMasterDisplay(IslandData island) {
        if (island == null) return "?";
        List<String> names = new ArrayList<>();
        String ownerName = Bukkit.getOfflinePlayer(island.getOwner()).getName();
        names.add(ownerName == null ? "?" : ownerName);
        for (UUID co : island.getCoOwners()) {
            String name = Bukkit.getOfflinePlayer(co).getName();
            if (name != null && !name.isBlank()) names.add(name);
        }
        String joined = String.join(", ", names);
        return joined.length() > 60 ? joined.substring(0, 57) + "..." : joined;
    }

    public String getIslandBossBarText(IslandData island) {
        return getIslandTitleDisplay(island) + " | " + getIslandMasterDisplay(island);
    }

    public String getIslandTeleportDisplay(IslandData island) {
        return getIslandBossBarText(island);
    }

    public String getParcelTeleportDisplay(IslandData island, ParcelData parcel) {
        return getParcelDisplayName(parcel) + " | " + getIslandMasterDisplay(island);
    }

    public String getWarpTeleportDisplay(IslandData island) {
        return getIslandWarpDisplay(island) + " | " + getIslandMasterDisplay(island);
    }

    public String normalizeIslandLabel(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isIslandLabelTaken(String candidate, UUID exceptOwner, String... allowedValues) {
        String normalized = normalizeIslandLabel(candidate);
        if (normalized.isBlank()) return false;
        Set<String> allowed = new HashSet<>();
        if (allowedValues != null) {
            for (String value : allowedValues) {
                String normalizedValue = normalizeIslandLabel(value);
                if (!normalizedValue.isBlank()) {
                    allowed.add(normalizedValue);
                }
            }
        }
        for (IslandData island : islands.values()) {
            if (exceptOwner != null && exceptOwner.equals(island.getOwner())) {
                String ownTitle = normalizeIslandLabel(island.getTitle());
                String ownWarp = normalizeIslandLabel(island.getWarpName());
                if (normalized.equals(ownTitle) && allowed.contains(ownTitle)) continue;
                if (normalized.equals(ownWarp) && allowed.contains(ownWarp)) continue;
            }
            String islandTitle = normalizeIslandLabel(island.getTitle());
            if (!islandTitle.isBlank() && normalized.equals(islandTitle) && !allowed.contains(islandTitle)) return true;
            String warpName = normalizeIslandLabel(island.getWarpName());
            if (!warpName.isBlank() && normalized.equals(warpName) && !allowed.contains(warpName)) return true;
        }
        return false;
    }

    public String getIslandAdditionalOwnersDisplay(IslandData island) {
        if (island == null || island.getIslandOwners().isEmpty()) return "-";
        List<String> names = new ArrayList<>();
        for (UUID owner : island.getIslandOwners()) {
            String name = Bukkit.getOfflinePlayer(owner).getName();
            if (name != null && !name.isBlank()) names.add(name);
        }
        if (names.isEmpty()) return "-";
        String joined = String.join(", ", names);
        return joined.length() > 60 ? joined.substring(0, 57) + "..." : joined;
    }

    public boolean isIslandMaster(IslandData island, UUID playerId) {
        return island != null && playerId != null && (island.getOwner().equals(playerId) || island.getCoOwners().contains(playerId));
    }

    public boolean isIslandOwner(IslandData island, UUID playerId) {
        return island != null && playerId != null && (isIslandMaster(island, playerId) || island.getIslandOwners().contains(playerId));
    }

    public boolean isPrimaryOwner(IslandData island, UUID playerId) {
        return island != null && playerId != null && island.getOwner().equals(playerId);
    }

    public void markIslandActivity(UUID playerId) {
        IslandData island = getIsland(playerId).orElse(null);
        if (island == null) return;
        island.setLastActiveAt(System.currentTimeMillis());
    }

    public void addPoints(IslandData island, long amount) {
        if (island == null || amount == 0) return;
        island.setPoints(Math.max(0L, island.getPoints() + amount));
    }

    public void queueCoOwnerInvite(IslandData island, UUID inviter, UUID target) {
        if (island == null || inviter == null || target == null) return;
        if (!isIslandMaster(island, inviter)) return;
        if (isIslandMaster(island, target)) return;
        pendingCoOwnerInvites.put(target, island.getOwner());
    }

    public IslandData getPendingCoOwnerInviteIsland(UUID target) {
        UUID primaryOwner = pendingCoOwnerInvites.get(target);
        if (primaryOwner == null) return null;
        return islands.get(primaryOwner);
    }

    public void clearCoOwnerInvite(UUID target) {
        if (target != null) pendingCoOwnerInvites.remove(target);
    }

    public boolean acceptCoOwnerInvite(UUID target) {
        UUID primaryOwner = pendingCoOwnerInvites.remove(target);
        if (primaryOwner == null) return false;
        IslandData island = islands.get(primaryOwner);
        if (island == null) return false;
        if (target.equals(primaryOwner)) return false;

        // Master darf nur auf einer Insel sein. Owner/Member auf anderen Inseln bleiben erhalten.
        IslandData current = getIsland(target).orElse(null);
        if (current != null && current != island) {
            removeOwnerFromIsland(current, target, false);
        }
        island.getCoOwners().add(target);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        Player primary = Bukkit.getPlayer(primaryOwner);
        if (primary != null && primary.isOnline()) {
            String targetName = Bukkit.getOfflinePlayer(target).getName();
            primary.sendMessage(ChatColor.GREEN + (targetName == null ? "Ein Spieler" : targetName) + " ist jetzt Master deiner Insel.");
        }
        return true;
    }

    public boolean leaveCoOwnership(UUID playerId) {
        IslandData island = getIsland(playerId).orElse(null);
        if (island == null) return false;
        if (!isIslandMaster(island, playerId)) return false;
        removeOwnerFromIsland(island, playerId, false);
        getOrCreateIsland(playerId);
        return true;
    }

    private IslandData removeOwnerFromIsland(IslandData island, UUID playerId, boolean createReplacementForCoOwnersIfDelete) {
        if (island == null || playerId == null) return island;
        if (island.getCoOwners().remove(playerId)) {
            island.setLastActiveAt(System.currentTimeMillis());
            save();
            return island;
        }
        if (!island.getOwner().equals(playerId)) {
            return island;
        }
        if (!island.getCoOwners().isEmpty()) {
            UUID newMaster = island.getCoOwners().iterator().next();
            island.getCoOwners().remove(newMaster);
            IslandData migrated = transferIslandMaster(island, newMaster);
            migrated.setLastActiveAt(System.currentTimeMillis());
            save();
            return migrated;
        }
        deleteIslandData(island, createReplacementForCoOwnersIfDelete);
        save();
        return null;
    }

    private IslandData transferIslandMaster(IslandData island, UUID newMaster) {
        if (island == null || newMaster == null) return island;
        if (newMaster.equals(island.getOwner())) return island;

        IslandData migrated = new IslandData(newMaster);
        migrated.setGridX(island.getGridX());
        migrated.setGridZ(island.getGridZ());
        migrated.setLevel(island.getLevel());
        migrated.setAvailableChunkUnlocks(island.getAvailableChunkUnlocks());
        migrated.setTitle(island.getTitle());
        migrated.setWarpName(island.getWarpName());
        migrated.setIslandTimeMode(island.getIslandTimeMode());
        migrated.setPoints(island.getPoints());
        migrated.setLastActiveAt(island.getLastActiveAt());
        if (island.getIslandSpawn() != null) migrated.setIslandSpawn(island.getIslandSpawn().clone());
        if (island.getWarpLocation() != null) migrated.setWarpLocation(island.getWarpLocation().clone());
        if (island.getCoreLocation() != null) migrated.setCoreLocation(island.getCoreLocation().clone());
        migrated.getTrusted().addAll(island.getTrusted());
        migrated.getTrustedContainers().addAll(island.getTrustedContainers());
        migrated.getTrustedRedstone().addAll(island.getTrustedRedstone());
        migrated.getIslandOwners().addAll(island.getIslandOwners());
        migrated.getIslandBanned().addAll(island.getIslandBanned());
        migrated.getUnlockedChunks().addAll(island.getUnlockedChunks());
        migrated.getGeneratedChunks().addAll(island.getGeneratedChunks());
        migrated.getProgress().putAll(island.getProgress());
        migrated.getCachedBlockCounts().putAll(island.getCachedBlockCounts());
        migrated.getCoOwners().addAll(island.getCoOwners());
        migrated.getCoOwners().add(island.getOwner());
        migrated.getCoOwners().remove(newMaster);
        copyAccessSettings(island.getIslandVisitorSettings(), migrated.getIslandVisitorSettings());
        for (ParcelData srcParcel : island.getParcels().values()) {
            ParcelData dst = new ParcelData(srcParcel.getChunkKey());
            dst.setName(srcParcel.getName());
            dst.getOwners().addAll(srcParcel.getOwners());
            dst.getUsers().addAll(srcParcel.getUsers());
            dst.getBanned().addAll(srcParcel.getBanned());
            dst.getPvpWhitelist().addAll(srcParcel.getPvpWhitelist());
            dst.getPvpKills().putAll(srcParcel.getPvpKills());
            dst.setPvpEnabled(srcParcel.isPvpEnabled());
            dst.setPveEnabled(srcParcel.isPveEnabled());
            dst.setBounds(srcParcel.getMinX(), srcParcel.getMinY(), srcParcel.getMinZ(), srcParcel.getMaxX(), srcParcel.getMaxY(), srcParcel.getMaxZ());
            if (srcParcel.getSpawn() != null) dst.setSpawn(srcParcel.getSpawn().clone());
            copyAccessSettings(srcParcel.getVisitorSettings(), dst.getVisitorSettings());
            migrated.getParcels().put(dst.getChunkKey(), dst);
        }

        islands.remove(island.getOwner());
        islands.put(newMaster, migrated);

        List<Consumer<IslandData>> createCallbacks = islandCreationCallbacks.remove(island.getOwner());
        if (createCallbacks != null) islandCreationCallbacks.put(newMaster, createCallbacks);
        List<Consumer<IslandData>> readyCallbacks = islandReadyCallbacks.remove(island.getOwner());
        if (readyCallbacks != null) islandReadyCallbacks.put(newMaster, readyCallbacks);

        if (!pregenerationQueue.isEmpty()) {
            Queue<PregenerationTask> rebuilt = new ArrayDeque<>();
            while (!pregenerationQueue.isEmpty()) {
                PregenerationTask task = pregenerationQueue.poll();
                if (task.islandOwner().equals(island.getOwner())) rebuilt.offer(new PregenerationTask(newMaster, task.nextIndex()));
                else rebuilt.offer(task);
            }
            pregenerationQueue.addAll(rebuilt);
        }
        pendingCoOwnerInvites.replaceAll((target, ownerId) -> ownerId.equals(island.getOwner()) ? newMaster : ownerId);
        return migrated;
    }

    private void copyAccessSettings(AccessSettings src, AccessSettings dst) {
        if (src == null || dst == null) return;
        dst.setDoors(src.isDoors());
        dst.setTrapdoors(src.isTrapdoors());
        dst.setFenceGates(src.isFenceGates());
        dst.setButtons(src.isButtons());
        dst.setLevers(src.isLevers());
        dst.setPressurePlates(src.isPressurePlates());
        dst.setContainers(src.isContainers());
        dst.setFarmUse(src.isFarmUse());
        dst.setRide(src.isRide());
        dst.setRedstoneUse(src.isRedstoneUse());
        dst.setLadderPlace(src.isLadderPlace());
        dst.setLadderBreak(src.isLadderBreak());
        dst.setLeavesPlace(src.isLeavesPlace());
        dst.setLeavesBreak(src.isLeavesBreak());
        dst.setTeleport(src.isTeleport());
    }

    public IslandData getIslandAt(Location location) {
        if (location == null || location.getWorld() == null || !skyWorldService.isSkyCityWorld(location.getWorld())) return null;
        if (isInSpawnPlot(location)) return null;
        int chunkX = location.getChunk().getX();
        int chunkZ = location.getChunk().getZ();
        int gridX = Math.floorDiv(chunkX + 32, ISLAND_CHUNKS);
        int gridZ = Math.floorDiv(chunkZ + 32, ISLAND_CHUNKS);
        for (IslandData island : islands.values()) {
            if (island.getGridX() == gridX && island.getGridZ() == gridZ) return island;
        }
        return null;
    }

    public boolean isInSpawnPlot(Location location) {
        if (location == null || location.getWorld() == null || !skyWorldService.isSkyCityWorld(location.getWorld())) return false;
        Chunk c = location.getChunk();
        return c.getX() >= -32 && c.getX() <= 31 && c.getZ() >= -32 && c.getZ() <= 31;
    }

    public boolean hasBuildAccess(UUID playerId, IslandData island) {
        return island != null && (isIslandOwner(island, playerId) || island.getTrusted().contains(playerId));
    }

    public boolean hasContainerAccess(UUID playerId, IslandData island) {
        return island != null && (isIslandOwner(island, playerId)
                || island.getTrusted().contains(playerId) || island.getTrustedContainers().contains(playerId));
    }

    public boolean hasRedstoneAccess(UUID playerId, IslandData island) {
        return island != null && (isIslandOwner(island, playerId)
                || island.getTrusted().contains(playerId) || island.getTrustedRedstone().contains(playerId));
    }

    public boolean hasAccess(UUID playerId, IslandData island) { return hasBuildAccess(playerId, island); }

    public boolean grantTrust(IslandData island, UUID target, TrustPermission permission) {
        boolean changed = false;
        switch (permission) {
            case BUILD -> changed = island.getTrusted().add(target);
            case CONTAINER -> changed = island.getTrustedContainers().add(target);
            case REDSTONE -> changed = island.getTrustedRedstone().add(target);
            case ALL -> {
                changed |= island.getTrusted().add(target);
                changed |= island.getTrustedContainers().add(target);
                changed |= island.getTrustedRedstone().add(target);
            }
        }
        if (changed) {
            island.setLastActiveAt(System.currentTimeMillis());
            save();
        }
        return changed;
    }

    public boolean revokeTrust(IslandData island, UUID target, TrustPermission permission) {
        boolean changed = false;
        switch (permission) {
            case BUILD -> changed = island.getTrusted().remove(target);
            case CONTAINER -> changed = island.getTrustedContainers().remove(target);
            case REDSTONE -> changed = island.getTrustedRedstone().remove(target);
            case ALL -> {
                changed |= island.getTrusted().remove(target);
                changed |= island.getTrustedContainers().remove(target);
                changed |= island.getTrustedRedstone().remove(target);
            }
        }
        if (changed) {
            island.setLastActiveAt(System.currentTimeMillis());
            save();
        }
        return changed;
    }

    public boolean setIslandBan(IslandData island, UUID target, boolean banned) {
        boolean changed = banned ? island.getIslandBanned().add(target) : island.getIslandBanned().remove(target);
        if (changed) {
            island.setLastActiveAt(System.currentTimeMillis());
            save();
        }
        return changed;
    }

    public boolean kickFromIsland(IslandData island, Player target) {
        if (island == null || target == null) return false;
        IslandData at = getIslandAt(target.getLocation());
        if (at == null || !at.getOwner().equals(island.getOwner())) return false;
        target.teleport(getSpawnLocation());
        return true;
    }

    public boolean grantIslandOwnerRole(IslandData island, UUID actor, UUID target) {
        if (island == null || actor == null || target == null) return false;
        if (!isIslandOwner(island, actor)) return false; // Master oder Owner darf Owner hinzuf\u00fcgen
        if (isIslandMaster(island, target)) return false;
        boolean changed = island.getIslandOwners().add(target);
        if (changed) {
            island.getIslandBanned().remove(target);
            island.setLastActiveAt(System.currentTimeMillis());
            save();
        }
        return changed;
    }

    public boolean revokeIslandOwnerRole(IslandData island, UUID actor, UUID target) {
        if (island == null || actor == null || target == null) return false;
        if (!isIslandMaster(island, actor)) return false; // nur Master darf Owner austragen
        boolean changed = island.getIslandOwners().remove(target);
        if (changed) {
            island.setLastActiveAt(System.currentTimeMillis());
            save();
        }
        return changed;
    }

    public boolean isChunkUnlocked(IslandData island, Location location) {
        return isChunkUnlocked(island, relativeChunkX(island, location.getChunk().getX()), relativeChunkZ(island, location.getChunk().getZ()));
    }

    public boolean isChunkUnlocked(IslandData island, int relX, int relZ) {
        if (relX < 0 || relX >= ISLAND_CHUNKS || relZ < 0 || relZ >= ISLAND_CHUNKS) return false;
        return island.getUnlockedChunks().contains(chunkKey(relX, relZ));
    }

    public boolean unlockCurrentChunk(IslandData island, Location location) {
        return unlockChunk(island, relativeChunkX(island, location.getChunk().getX()), relativeChunkZ(island, location.getChunk().getZ()));
    }

    public boolean unlockChunk(IslandData island, int relX, int relZ) {
        return unlockChunk(island, null, relX, relZ) == ChunkUnlockResult.SUCCESS;
    }

    public ChunkUnlockResult unlockChunk(IslandData island, UUID actor, int relX, int relZ) {
        if (island == null) return ChunkUnlockResult.OUT_OF_BOUNDS;
        if (relX < 0 || relX >= ISLAND_CHUNKS || relZ < 0 || relZ >= ISLAND_CHUNKS) return ChunkUnlockResult.OUT_OF_BOUNDS;
        String key = chunkKey(relX, relZ);
        if (island.getUnlockedChunks().contains(key)) return ChunkUnlockResult.ALREADY_UNLOCKED;
        if (island.getAvailableChunkUnlocks() <= 0) return ChunkUnlockResult.NO_UNLOCKS_LEFT;

        Set<UUID> requiredNeighbors = getRequiredNeighborApprovals(island, relX, relZ);
        if (!requiredNeighbors.isEmpty()) {
            String requestKey = borderRequestKey(island.getOwner(), relX, relZ);
            PendingBorderUnlockRequest req = pendingBorderUnlockRequests.get(requestKey);
            if (req == null || !req.requiredNeighborOwners.equals(requiredNeighbors)) {
                req = new PendingBorderUnlockRequest(island.getOwner(), relX, relZ, requiredNeighbors);
                pendingBorderUnlockRequests.put(requestKey, req);
                notifyNeighborApprovalRequest(req, island, actor);
                return ChunkUnlockResult.NEEDS_NEIGHBOR_APPROVAL;
            }
            if (!req.approvedNeighborOwners.containsAll(requiredNeighbors)) {
                return ChunkUnlockResult.PENDING_NEIGHBOR_APPROVAL;
            }
        }

        island.getUnlockedChunks().add(key);
        island.setAvailableChunkUnlocks(island.getAvailableChunkUnlocks() - 1);
        ensureChunkTemplateGenerated(island, relX, relZ);
        pendingBorderUnlockRequests.remove(borderRequestKey(island.getOwner(), relX, relZ));
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return ChunkUnlockResult.SUCCESS;
    }

    public ChunkUnlockResult approveBorderChunkUnlock(UUID approver, UUID requesterOwner, int relX, int relZ) {
        if (approver == null || requesterOwner == null) return ChunkUnlockResult.NO_PENDING_REQUEST;
        String requestKey = borderRequestKey(requesterOwner, relX, relZ);
        PendingBorderUnlockRequest req = pendingBorderUnlockRequests.get(requestKey);
        if (req == null) return ChunkUnlockResult.NO_PENDING_REQUEST;
        if (!req.requesterOwner.equals(requesterOwner) || req.relX != relX || req.relZ != relZ) return ChunkUnlockResult.NO_PENDING_REQUEST;

        UUID approverNeighborOwner = findRequiredNeighborOwnerForApprover(req.requiredNeighborOwners, approver);
        if (approverNeighborOwner == null) return ChunkUnlockResult.NOT_AUTHORIZED;

        req.approvedNeighborOwners.add(approverNeighborOwner);
        if (!req.approvedNeighborOwners.containsAll(req.requiredNeighborOwners)) {
            save();
            return ChunkUnlockResult.APPROVAL_RECORDED;
        }

        IslandData requesterIsland = islands.get(requesterOwner);
        if (requesterIsland == null) {
            pendingBorderUnlockRequests.remove(requestKey);
            save();
            return ChunkUnlockResult.NO_PENDING_REQUEST;
        }
        ChunkUnlockResult result = unlockChunk(requesterIsland, null, relX, relZ);
        if (result == ChunkUnlockResult.SUCCESS) {
            notifyRequesterUnlockApproved(requesterIsland, relX, relZ);
        }
        save();
        return result;
    }

    private String borderRequestKey(UUID requesterOwner, int relX, int relZ) {
        return requesterOwner + "|" + relX + "|" + relZ;
    }

    private Set<UUID> getRequiredNeighborApprovals(IslandData island, int relX, int relZ) {
        Set<UUID> required = new HashSet<>();
        if (island == null) return required;
        if (relX == 0) addRequiredNeighborOwner(required, island.getGridX() - 1, island.getGridZ());
        if (relX == ISLAND_CHUNKS - 1) addRequiredNeighborOwner(required, island.getGridX() + 1, island.getGridZ());
        if (relZ == 0) addRequiredNeighborOwner(required, island.getGridX(), island.getGridZ() - 1);
        if (relZ == ISLAND_CHUNKS - 1) addRequiredNeighborOwner(required, island.getGridX(), island.getGridZ() + 1);
        return required;
    }

    private void addRequiredNeighborOwner(Set<UUID> required, int gridX, int gridZ) {
        IslandData neighbor = findIslandByGrid(gridX, gridZ);
        if (neighbor == null) return;
        if (isNeighborApprovalWaived(neighbor)) return;
        required.add(neighbor.getOwner());
    }

    private boolean isNeighborApprovalWaived(IslandData neighbor) {
        if (neighbor == null) return true;
        long stored = neighbor.getLastActiveAt();
        long lastPlayed = Bukkit.getOfflinePlayer(neighbor.getOwner()).getLastPlayed();
        long effectiveLastActive = Math.max(stored, lastPlayed);
        if (effectiveLastActive <= 0L) return false;
        return System.currentTimeMillis() - effectiveLastActive >= ONE_YEAR_MS;
    }

    private IslandData findIslandByGrid(int gridX, int gridZ) {
        for (IslandData island : islands.values()) {
            if (island.getGridX() == gridX && island.getGridZ() == gridZ) return island;
        }
        return null;
    }

    private UUID findRequiredNeighborOwnerForApprover(Set<UUID> requiredNeighborOwners, UUID approver) {
        for (UUID neighborOwner : requiredNeighborOwners) {
            IslandData neighborIsland = islands.get(neighborOwner);
            if (neighborIsland != null && isIslandMaster(neighborIsland, approver)) {
                return neighborOwner;
            }
        }
        return null;
    }

    private void notifyNeighborApprovalRequest(PendingBorderUnlockRequest req, IslandData requesterIsland, UUID actor) {
        int displayX = displayChunkX(req.relX);
        int displayZ = displayChunkZ(req.relZ);
        String requesterTitle = getIslandTitleDisplay(requesterIsland);
        String requesterName = Bukkit.getOfflinePlayer(requesterIsland.getOwner()).getName();
        String actorName = actor == null ? null : Bukkit.getOfflinePlayer(actor).getName();
        String by = (actorName == null || actorName.isBlank()) ? (requesterName == null ? "Unbekannt" : requesterName) : actorName;

        for (UUID neighborOwner : req.requiredNeighborOwners) {
            IslandData neighborIsland = islands.get(neighborOwner);
            if (neighborIsland == null) continue;
            for (UUID approverId : getNeighborApprovers(neighborIsland)) {
                Player online = Bukkit.getPlayer(approverId);
                if (online == null || !online.isOnline()) continue;
                online.sendMessage(ChatColor.GOLD + by + ChatColor.YELLOW + " m\u00f6chte Grenz-Chunk " + ChatColor.WHITE + displayX + ":" + displayZ
                        + ChatColor.YELLOW + " auf " + ChatColor.GOLD + requesterTitle + ChatColor.YELLOW + " freischalten.");
                online.sendMessage(ChatColor.GRAY + "Risiko: verbundene Grenze erlaubt \u00dcbertritt von Fl\u00fcssigkeiten, Items und Mobs.");

                TextComponent approve = new TextComponent(ChatColor.GREEN + "[Freigeben]");
                approve.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/is chunkapprove " + req.requesterOwner + " " + req.relX + " " + req.relZ));
                approve.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(ChatColor.YELLOW + "Klick: Grenz-Chunk freigeben").create()));

                TextComponent denyHint = new TextComponent(ChatColor.DARK_GRAY + " [Ignorieren]");
                online.spigot().sendMessage(approve, denyHint);
            }
        }
    }

    private Set<UUID> getNeighborApprovers(IslandData neighborIsland) {
        Set<UUID> approvers = new HashSet<>();
        if (neighborIsland == null) return approvers;
        approvers.add(neighborIsland.getOwner());
        approvers.addAll(neighborIsland.getCoOwners());
        return approvers;
    }

    private void notifyRequesterUnlockApproved(IslandData requesterIsland, int relX, int relZ) {
        Player requester = Bukkit.getPlayer(requesterIsland.getOwner());
        if (requester == null || !requester.isOnline()) return;
        int displayX = displayChunkX(relX);
        int displayZ = displayChunkZ(relZ);
        requester.sendMessage(ChatColor.GREEN + "Grenzfreigabe erhalten. Chunk freigeschaltet: " + displayX + ":" + displayZ);
    }

    public boolean claimParcel(IslandData island, UUID claimer, int relX, int relZ) {
        if (!isChunkUnlocked(island, relX, relZ)) return false;
        int worldChunkX = plotMinChunkX(island.getGridX()) + relX;
        int worldChunkZ = plotMinChunkZ(island.getGridZ()) + relZ;
        int minX = worldChunkX << 4;
        int minZ = worldChunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        int minY = skyWorldService.getWorld().getMinHeight();
        int maxY = skyWorldService.getWorld().getMaxHeight() - 1;
        return createParcelCuboid(island, claimer, minX, minY, minZ, maxX, maxY, maxZ) != null;
    }

    public ParcelData getParcel(IslandData island, int relX, int relZ) {
        if (island == null) return null;
        int worldChunkX = plotMinChunkX(island.getGridX()) + relX;
        int worldChunkZ = plotMinChunkZ(island.getGridZ()) + relZ;
        int minX = worldChunkX << 4;
        int minZ = worldChunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        for (ParcelData parcel : island.getParcels().values()) {
            boolean overlapX = parcel.getMaxX() >= minX && parcel.getMinX() <= maxX;
            boolean overlapZ = parcel.getMaxZ() >= minZ && parcel.getMinZ() <= maxZ;
            if (overlapX && overlapZ) return parcel;
        }
        return null;
    }

    public ParcelData getParcelAt(IslandData island, Location location) {
        if (island == null || location == null) return null;
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        for (ParcelData parcel : island.getParcels().values()) {
            if (parcel.contains(x, y, z)) return parcel;
        }
        return null;
    }

    public ParcelData createParcelCuboidFromSelection(IslandData island, UUID owner, Location pos1, Location pos2) {
        if (island == null || owner == null || pos1 == null || pos2 == null) return null;
        if (!skyWorldService.isSkyCityWorld(pos1.getWorld()) || !skyWorldService.isSkyCityWorld(pos2.getWorld())) return null;
        if (!pos1.getWorld().equals(pos2.getWorld())) return null;
        if (getIslandAt(pos1) != island || getIslandAt(pos2) != island) return null;
        return createParcelCuboid(
                island,
                owner,
                pos1.getBlockX(),
                pos1.getBlockY(),
                pos1.getBlockZ(),
                pos2.getBlockX(),
                pos2.getBlockY(),
                pos2.getBlockZ()
        );
    }

    public ParcelData createParcelCuboid(IslandData island, UUID owner, int x1, int y1, int z1, int x2, int y2, int z2) {
        if (island == null || owner == null) return null;
        if (!isIslandOwner(island, owner)) return null;
        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);
        int maxZ = Math.max(z1, z2);
        if (maxX < minX || maxY < minY || maxZ < minZ) return null;
        if (!isParcelAreaInUnlockedChunks(island, minX, minZ, maxX, maxZ)) return null;

        ParcelData probe = new ParcelData("probe");
        probe.setBounds(minX, minY, minZ, maxX, maxY, maxZ);
        for (ParcelData existing : island.getParcels().values()) {
            if (existing.intersects(probe)) return null;
        }

        String id = nextParcelId(island);
        ParcelData parcel = new ParcelData(id);
        parcel.setBounds(minX, minY, minZ, maxX, maxY, maxZ);
        parcel.getOwners().add(owner);
        parcel.setSpawn(new Location(skyWorldService.getWorld(), (minX + maxX) / 2.0 + 0.5, maxY + 1.0, (minZ + maxZ) / 2.0 + 0.5));
        island.getParcels().put(id, parcel);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return parcel;
    }

    public boolean deleteParcelAt(IslandData island, UUID actor, Location location) {
        if (island == null || actor == null || location == null) return false;
        ParcelData parcel = getParcelAt(island, location);
        if (parcel == null || !isParcelOwner(island, parcel, actor)) return false;
        if (island.getParcels().remove(parcel.getChunkKey()) != null) {
            island.setLastActiveAt(System.currentTimeMillis());
            save();
            return true;
        }
        return false;
    }

    private boolean isParcelAreaInUnlockedChunks(IslandData island, int minX, int minZ, int maxX, int maxZ) {
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        for (int worldChunkX = minChunkX; worldChunkX <= maxChunkX; worldChunkX++) {
            for (int worldChunkZ = minChunkZ; worldChunkZ <= maxChunkZ; worldChunkZ++) {
                int relX = relativeChunkX(island, worldChunkX);
                int relZ = relativeChunkZ(island, worldChunkZ);
                if (!isChunkUnlocked(island, relX, relZ)) return false;
            }
        }
        return true;
    }

    private String nextParcelId(IslandData island) {
        int i = 1;
        while (island.getParcels().containsKey("parcel-" + i)) i++;
        return "parcel-" + i;
    }

    private int[] legacyChunkParcelBounds(IslandData island, String parcelKey) {
        if (island == null || parcelKey == null) return null;
        String[] parts = parcelKey.split(":");
        if (parts.length != 2) return null;
        try {
            int relX = Integer.parseInt(parts[0]);
            int relZ = Integer.parseInt(parts[1]);
            int worldChunkX = plotMinChunkX(island.getGridX()) + relX;
            int worldChunkZ = plotMinChunkZ(island.getGridZ()) + relZ;
            int minX = worldChunkX << 4;
            int minZ = worldChunkZ << 4;
            int maxX = minX + 15;
            int maxZ = minZ + 15;
            int minY = skyWorldService.getWorld().getMinHeight();
            int maxY = skyWorldService.getWorld().getMaxHeight() - 1;
            return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public boolean isParcelOwner(IslandData island, ParcelData parcel, UUID playerId) {
        return island != null && parcel != null && (isIslandOwner(island, playerId) || parcel.getOwners().contains(playerId));
    }

    public boolean isParcelUser(IslandData island, ParcelData parcel, UUID playerId) {
        return isParcelOwner(island, parcel, playerId) || (parcel != null && parcel.getUsers().contains(playerId));
    }

    public boolean grantParcelRole(IslandData island, ParcelData parcel, UUID actor, UUID target, ParcelRole role) {
        if (!isParcelOwner(island, parcel, actor)) return false;
        boolean changed = switch (role) {
            case OWNER -> parcel.getOwners().add(target);
            case USER -> parcel.getUsers().add(target);
            case PVP -> parcel.getPvpWhitelist().add(target);
        };
        if (changed) {
            island.setLastActiveAt(System.currentTimeMillis());
            save();
        }
        return changed;
    }

    public boolean revokeParcelRole(IslandData island, ParcelData parcel, UUID actor, UUID target, ParcelRole role) {
        if (!isParcelOwner(island, parcel, actor)) return false;
        boolean changed = switch (role) {
            case OWNER -> parcel.getOwners().remove(target);
            case USER -> parcel.getUsers().remove(target);
            case PVP -> parcel.getPvpWhitelist().remove(target);
        };
        if (changed) {
            island.setLastActiveAt(System.currentTimeMillis());
            save();
        }
        return changed;
    }

    public boolean setParcelBan(IslandData island, ParcelData parcel, UUID actor, UUID target, boolean banned) {
        if (!isParcelOwner(island, parcel, actor)) return false;
        boolean changed = banned ? parcel.getBanned().add(target) : parcel.getBanned().remove(target);
        if (changed) {
            island.setLastActiveAt(System.currentTimeMillis());
            save();
        }
        return changed;
    }

    public boolean setParcelPvp(IslandData island, ParcelData parcel, UUID actor, boolean enabled) {
        if (!isParcelOwner(island, parcel, actor)) return false;
        if (parcel.isPvpEnabled() == enabled) return false;
        parcel.setPvpEnabled(enabled);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean setParcelPve(IslandData island, ParcelData parcel, UUID actor, boolean enabled) {
        if (!isParcelOwner(island, parcel, actor)) return false;
        if (parcel.isPveEnabled() == enabled) return false;
        parcel.setPveEnabled(enabled);
        if (!enabled) {
            resetPveZone(island, parcel, ChatColor.GRAY + "PvE-Zone wurde deaktiviert.");
        }
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public String getParcelPveKey(IslandData island, ParcelData parcel) {
        if (island == null || parcel == null) return null;
        return island.getOwner() + ":" + parcel.getChunkKey();
    }

    public Optional<String> validateParcelPve(IslandData island, ParcelData parcel) {
        if (buildPveZoneRuntime(island, parcel).isPresent()) {
            return Optional.empty();
        }
        return Optional.of("Zone ungueltig: wei\u00dfe Startzone oder Spawn-Wolle fehlt/ist offen.");
    }

    public boolean isParcelPveActive(IslandData island, ParcelData parcel) {
        String key = getParcelPveKey(island, parcel);
        return key != null && activePveZones.containsKey(key);
    }

    public Optional<PveRuntimeSnapshot> getParcelPveSnapshot(IslandData island, ParcelData parcel) {
        String key = getParcelPveKey(island, parcel);
        if (key == null) return Optional.empty();
        PveZoneRuntime runtime = activePveZones.get(key);
        if (runtime == null) return Optional.empty();
        Map<String, String> lines = new LinkedHashMap<>();
        int index = 1;
        for (PveSpawnMarker marker : runtime.markers) {
            long alive = runtime.activeMobIds.stream()
                    .map(Bukkit::getEntity)
                    .filter(LivingEntity.class::isInstance)
                    .filter(entity -> entity.getScoreboardTags().contains("skycity_pve_marker_" + marker.id()))
                    .count();
            lines.put(index + ". Spawn", marker.displayName() + " x" + alive);
            index++;
            if (index > 10) break;
        }
        return Optional.of(new PveRuntimeSnapshot(key, getParcelDisplayName(parcel), runtime.currentWave, runtime.requiredWaves, runtime.activeMobIds.size(), lines));
    }

    public boolean enterParcelPve(Player player, IslandData island, ParcelData parcel) {
        if (player == null || island == null || parcel == null || !parcel.isPveEnabled()) return false;
        String key = getParcelPveKey(island, parcel);
        if (key == null) return false;
        PveZoneRuntime runtime = activePveZones.get(key);
        if (runtime == null) {
            runtime = buildPveZoneRuntime(island, parcel).orElse(null);
            if (runtime == null) {
                player.sendMessage(ChatColor.RED + "Diese PvE-Zone ist ungueltig. Startzone/Marker pruefen.");
                return false;
            }
            activePveZones.put(key, runtime);
        }
        if (player.getLevel() < 5) {
            player.sendMessage(ChatColor.RED + "F\u00fcr PvE brauchst du mindestens 5 Level.");
            return false;
        }
        runtime.participants.add(player.getUniqueId());
        runtime.pendingRewards.putIfAbsent(player.getUniqueId(), 0);
        playerPveZones.put(player.getUniqueId(), key);
        player.sendMessage(ChatColor.GOLD + "PvE betreten: " + ChatColor.YELLOW + getParcelDisplayName(parcel));
        player.sendMessage(ChatColor.GRAY + "Halte " + runtime.requiredWaves + " Wellen aus, um die Belohnung zu bekommen.");
        if (runtime.currentWave <= 0 && runtime.activeMobIds.isEmpty()) {
            startNextPveWave(runtime);
        }
        return true;
    }

    public void leaveParcelPve(Player player, IslandData island, ParcelData parcel, boolean resetZone) {
        if (player == null) return;
        String key = getParcelPveKey(island, parcel);
        if (key == null) return;
        leaveParcelPve(player, key, resetZone);
    }

    public void leaveParcelPve(Player player, String zoneKey, boolean resetZone) {
        if (player == null || zoneKey == null) return;
        String key = zoneKey;
        playerPveZones.remove(player.getUniqueId());
        PveZoneRuntime runtime = activePveZones.get(key);
        if (runtime == null) return;
        runtime.participants.remove(player.getUniqueId());
        if (resetZone) {
            resetPveZone(runtime, ChatColor.RED + player.getName() + ChatColor.GRAY + " hat die PvE-Zone verlassen. Alles wurde zurueckgesetzt.");
        }
    }

    public Optional<Location> consumePendingPveRespawn(UUID playerId) {
        if (playerId == null) return Optional.empty();
        return Optional.ofNullable(pendingPveRespawns.remove(playerId));
    }

    public boolean isPlayerInParcelPve(UUID playerId, IslandData island, ParcelData parcel) {
        String key = getParcelPveKey(island, parcel);
        return playerId != null && key != null && key.equals(playerPveZones.get(playerId));
    }

    public boolean handlePvePlayerDeath(Player player, IslandData island, ParcelData parcel) {
        if (player == null || island == null || parcel == null) return false;
        String key = getParcelPveKey(island, parcel);
        PveZoneRuntime runtime = key == null ? null : activePveZones.get(key);
        if (runtime == null || !runtime.participants.contains(player.getUniqueId())) return false;
        pendingPveRespawns.put(player.getUniqueId(), runtime.respawnLocation.clone());
        return true;
    }

    public boolean handlePveMobDeath(LivingEntity entity, Player killer) {
        if (entity == null) return false;
        String zoneKey = pveMobZones.remove(entity.getUniqueId());
        if (zoneKey == null) return false;
        PveZoneRuntime runtime = activePveZones.get(zoneKey);
        if (runtime == null) return false;
        runtime.activeMobIds.remove(entity.getUniqueId());
        runtime.mobHomes.remove(entity.getUniqueId());
        boolean valid = !runtime.invalidMobIds.contains(entity.getUniqueId());
        runtime.mobLabels.remove(entity.getUniqueId());
        runtime.mobLevels.remove(entity.getUniqueId());
        runtime.mobLastReachableAt.remove(entity.getUniqueId());
        runtime.mobLastRangedHitAt.remove(entity.getUniqueId());
        runtime.invalidMobIds.remove(entity.getUniqueId());
        if (!valid) {
            broadcastPveZone(runtime, ChatColor.RED + "Ung\u00fcltiger Kill: Mob war au\u00dfer Reichweite oder ohne Pfad zum Spieler.");
        } else {
            int reward = Math.max(1, extractPveReward(entity));
            for (UUID participantId : runtime.participants) {
                runtime.pendingRewards.merge(participantId, reward, Integer::sum);
            }
        }
        if (runtime.activeMobIds.isEmpty()) {
            if (runtime.currentWave >= runtime.requiredWaves) {
                finishPveZone(runtime);
            } else {
                startNextPveWave(runtime);
            }
        }
        return true;
    }

    public void recordPveMobPlayerHit(LivingEntity attacker, Player victim) {
        if (attacker == null || victim == null) return;
        String zoneKey = pveMobZones.get(attacker.getUniqueId());
        if (zoneKey == null) return;
        PveZoneRuntime runtime = activePveZones.get(zoneKey);
        if (runtime == null || !runtime.participants.contains(victim.getUniqueId())) return;
        runtime.mobLastRangedHitAt.put(attacker.getUniqueId(), System.currentTimeMillis());
    }

    public boolean isLocationInActivePveZone(Location location) {
        if (location == null) return false;
        return activePveZones.values().stream().anyMatch(runtime -> runtime.contains(location));
    }

    public boolean resetParcelPvpStats(IslandData island, ParcelData parcel, UUID actor) {
        if (!isParcelOwner(island, parcel, actor)) return false;
        if (parcel.getPvpKills().isEmpty()) return false;
        parcel.getPvpKills().clear();
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public void recordParcelPvpKill(IslandData island, ParcelData parcel, UUID killer) {
        if (island == null || parcel == null || killer == null) return;
        parcel.getPvpKills().merge(killer, 1, Integer::sum);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
    }

    public boolean kickFromParcel(IslandData island, ParcelData parcel, Player target) {
        if (island == null || parcel == null || target == null) return false;
        if (getIslandAt(target.getLocation()) != island) return false;
        if (getParcelAt(island, target.getLocation()) != parcel) return false;
        target.teleport(island.getIslandSpawn() != null ? island.getIslandSpawn() : getSpawnLocation());
        return true;
    }

    public boolean isPlayerBannedFromLocation(UUID playerId, Location location) {
        IslandData island = getIslandAt(location);
        if (island == null) return false;
        if (island.getIslandBanned().contains(playerId)) return true;
        ParcelData parcel = getParcelAt(island, location);
        return parcel != null && parcel.getBanned().contains(playerId);
    }

    public AccessSettings getEffectiveVisitorSettings(IslandData island, Location location) {
        ParcelData parcel = getParcelAt(island, location);
        return parcel != null ? parcel.getVisitorSettings() : island.getIslandVisitorSettings();
    }

    public boolean canTeleportToIsland(IslandData island, UUID playerId) {
        if (island == null) return false;
        if (island.getIslandBanned().contains(playerId)) return false;
        return isIslandOwner(island, playerId)
                || hasBuildAccess(playerId, island)
                || hasContainerAccess(playerId, island)
                || island.getIslandVisitorSettings().isTeleport();
    }

    public boolean canTeleportToParcel(IslandData island, ParcelData parcel, UUID playerId) {
        if (island == null || parcel == null) return false;
        if (island.getIslandBanned().contains(playerId) || parcel.getBanned().contains(playerId)) return false;
        return isParcelUser(island, parcel, playerId) || parcel.getVisitorSettings().isTeleport();
    }

    public String getParcelPvpKey(IslandData island, ParcelData parcel) {
        if (island == null || parcel == null) return null;
        return island.getOwner() + ":" + parcel.getChunkKey();
    }

    public void grantParcelPvpConsent(UUID playerId, IslandData island, ParcelData parcel) {
        String key = getParcelPvpKey(island, parcel);
        if (playerId == null || key == null) return;
        parcelPvpConsents.put(playerId, key);
    }

    public void clearParcelPvpConsent(UUID playerId) {
        if (playerId == null) return;
        parcelPvpConsents.remove(playerId);
    }

    public boolean hasParcelPvpConsent(UUID playerId, IslandData island, ParcelData parcel) {
        String key = getParcelPvpKey(island, parcel);
        return playerId != null && key != null && key.equals(parcelPvpConsents.get(playerId));
    }

    public boolean canEnterParcelPvp(IslandData island, ParcelData parcel, UUID playerId) {
        if (island == null || parcel == null || playerId == null) return false;
        if (isParcelOwner(island, parcel, playerId)) return true;
        if (parcel.getPvpWhitelist().isEmpty()) return true;
        return parcel.getPvpWhitelist().contains(playerId);
    }

    public boolean canPlayersFightAt(Location location, UUID attacker, UUID victim) {
        IslandData island = getIslandAt(location);
        if (island == null) return false;
        ParcelData parcel = getParcelAt(island, location);
        if (parcel == null || !parcel.isPvpEnabled()) return false;
        if (!canEnterParcelPvp(island, parcel, attacker) || !canEnterParcelPvp(island, parcel, victim)) return false;
        return hasParcelPvpConsent(attacker, island, parcel) && hasParcelPvpConsent(victim, island, parcel);
    }

    public List<TeleportTarget> getTeleportTargetsFor(UUID playerId) {
        List<TeleportTarget> out = new ArrayList<>();
        for (IslandData island : islands.values()) {
            if (canTeleportToIsland(island, playerId) && island.getIslandSpawn() != null) {
                out.add(new TeleportTarget("island:" + island.getOwner(), getIslandTeleportDisplay(island), island.getIslandSpawn(), false));
            }
            if (canTeleportToIsland(island, playerId) && island.getWarpLocation() != null && island.getWarpName() != null && !island.getWarpName().isBlank()) {
                out.add(new TeleportTarget("warp:" + island.getOwner(), getWarpTeleportDisplay(island), island.getWarpLocation(), false));
            }
            for (ParcelData parcel : island.getParcels().values()) {
                if (parcel.getSpawn() != null && canTeleportToParcel(island, parcel, playerId)) {
                    out.add(new TeleportTarget("parcel:" + island.getOwner() + ":" + parcel.getChunkKey(), getParcelTeleportDisplay(island, parcel), parcel.getSpawn(), true));
                }
            }
        }
        return out;
    }

    public List<TeleportTarget> getWarpTargetsFor(UUID playerId) {
        return getTeleportTargetsFor(playerId).stream()
                .filter(target -> target.id().startsWith("warp:"))
                .toList();
    }

    public TeleportTarget findWarpTarget(UUID playerId, String warpName) {
        String normalized = normalizeIslandLabel(warpName);
        if (normalized.isBlank()) return null;
        for (TeleportTarget target : getWarpTargetsFor(playerId)) {
            String[] parts = target.displayName().split("\\s*\\|\\s*", 2);
            String displayWarpName = parts.length == 0 ? target.displayName() : parts[0];
            if (normalizeIslandLabel(displayWarpName).equals(normalized)) {
                return target;
            }
        }
        return null;
    }

    public String getParcelDisplayName(ParcelData parcel) {
        if (parcel == null) return "Unbekannt";
        String name = parcel.getName();
        if (name == null || name.isBlank()) return parcel.getChunkKey();
        return name.trim();
    }

    public boolean ensureChunkTemplateGenerated(IslandData island, int relChunkX, int relChunkZ) {
        if (relChunkX < 0 || relChunkX >= ISLAND_CHUNKS || relChunkZ < 0 || relChunkZ >= ISLAND_CHUNKS) return false;
        String key = chunkKey(relChunkX, relChunkZ);
        if (island.getGeneratedChunks().contains(key)) return false;
        // Starter-Chunks muessen auch dann generiert werden, wenn dort Core/Portal/Kiste bereits gesetzt wurden.
        if (!isStarterChunk(relChunkX, relChunkZ) && hasExistingBlocksInChunk(island, relChunkX, relChunkZ)) {
            island.getGeneratedChunks().add(key);
            return false;
        }
        ChunkTemplateDef def = pickTemplate(relChunkX, relChunkZ);
        generateChunkFromTemplate(island, relChunkX, relChunkZ, def);
        island.getGeneratedChunks().add(key);
        island.setPoints(Math.max(0L, island.getPoints()) + 1L);
        return true;
    }

    private boolean hasExistingBlocksInChunk(IslandData island, int relChunkX, int relChunkZ) {
        World world = skyWorldService.getWorld();
        int worldChunkX = plotMinChunkX(island.getGridX()) + relChunkX;
        int worldChunkZ = plotMinChunkZ(island.getGridZ()) + relChunkZ;
        Chunk chunk = world.getChunkAt(worldChunkX, worldChunkZ);
        int minY = Math.max(world.getMinHeight(), SkyWorldService.SPAWN_Y - 16);
        int maxY = Math.min(world.getMaxHeight() - 1, SkyWorldService.SPAWN_Y + 20);
        for (int y = minY; y <= maxY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (!chunk.getBlock(x, y, z).getType().isAir()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void ensureTemplateAtLocation(IslandData island, Location location) {
        if (island == null || location == null) return;
        int relX = relativeChunkX(island, location.getChunk().getX());
        int relZ = relativeChunkZ(island, location.getChunk().getZ());
        if (isChunkUnlocked(island, relX, relZ)) ensureChunkTemplateGenerated(island, relX, relZ);
    }

    private ChunkTemplateDef pickTemplate(int relX, int relZ) {
        if (isStarterChunk(relX, relZ)) {
            return starterClassicTemplate();
        }
        if (templates.isEmpty()) return new ChunkTemplateDef("fallback", Material.GRASS_BLOCK, Material.STONE, Material.OAK_SAPLING, Biome.PLAINS, 2, 4);
        long seed = findThemeSeedForChunk(relX, relZ);
        int idx = Math.floorMod((int) (seed ^ (seed >>> 32)), templates.size());
        return templates.get(idx);
    }

    private ChunkTemplateDef starterClassicTemplate() {
        return new ChunkTemplateDef("starter_classic", Material.GRASS_BLOCK, Material.DIRT, Material.OAK_SAPLING, Biome.PLAINS, 3, 5);
    }

    private long findThemeSeedForChunk(int relChunkX, int relChunkZ) {
        if (isStarterChunk(relChunkX, relChunkZ)) {
            return mixHash(0x5A17D00DL);
        }
        Map<Long, Double> weights = new HashMap<>();
        int basePlotX = relChunkX * 16;
        int basePlotZ = relChunkZ * 16;
        for (int sx = -2; sx <= 18; sx += 2) {
            for (int sz = -2; sz <= 18; sz += 2) {
                ThemeHit hit = findDominantMacroIslandTheme(basePlotX + sx, basePlotZ + sz);
                if (hit == null) continue;
                double centerBiasX = 1.0 - (Math.abs(sx - 8.0) / 14.0);
                double centerBiasZ = 1.0 - (Math.abs(sz - 8.0) / 14.0);
                double weight = Math.max(0.05, hit.density()) * Math.max(0.15, centerBiasX * centerBiasZ);
                weights.merge(hit.seed(), weight, Double::sum);
            }
        }
        long bestSeed = Long.MIN_VALUE;
        double bestWeight = Double.NEGATIVE_INFINITY;
        for (Map.Entry<Long, Double> entry : weights.entrySet()) {
            if (entry.getValue() > bestWeight) {
                bestWeight = entry.getValue();
                bestSeed = entry.getKey();
            }
        }
        if (bestSeed != Long.MIN_VALUE) {
            return bestSeed;
        }

        long fallback = 918273645L + (relChunkX * 341873128712L) + (relChunkZ * 132897987541L);
        return mixHash(fallback);
    }

    private ThemeHit findDominantMacroIslandTheme(int plotBlockX, int plotBlockZ) {
        final int cellSizeBlocks = MACRO_CELL_SIZE_CHUNKS * 16;
        int cellX = Math.floorDiv(plotBlockX, cellSizeBlocks);
        int cellZ = Math.floorDiv(plotBlockZ, cellSizeBlocks);
        double bestDensity = Double.NEGATIVE_INFINITY;
        long bestSeed = Long.MIN_VALUE;

        for (int gx = cellX - 1; gx <= cellX + 1; gx++) {
            for (int gz = cellZ - 1; gz <= cellZ + 1; gz++) {
                long base = macroCellBaseSeed(gx, gz);
                if (!macroCellHasIsland(base)) continue;

                int sizeChunksX = macroCellSizeChunksX(base);
                int sizeChunksZ = macroCellSizeChunksZ(base);
                int chunkOffsetX = macroCellChunkOffsetX(base, sizeChunksX);
                int chunkOffsetZ = macroCellChunkOffsetZ(base, sizeChunksZ);
                if (chunkOffsetX < 0 || chunkOffsetZ < 0) continue;

                int islandMinX = (gx * cellSizeBlocks) + (chunkOffsetX * 16);
                int islandMinZ = (gz * cellSizeBlocks) + (chunkOffsetZ * 16);
                int islandWidth = sizeChunksX * 16;
                int islandHeight = sizeChunksZ * 16;

                double centerX = islandMinX + (islandWidth * 0.5) + ((((base >>> 40) & 255L) / 255.0) - 0.5) * 8.0;
                double centerZ = islandMinZ + (islandHeight * 0.5) + ((((base >>> 48) & 255L) / 255.0) - 0.5) * 8.0;

                double rx = Math.max(5.5, (islandWidth * 0.48) + ((((base >>> 16) & 255L) / 255.0) - 0.5) * 5.0);
                double rz = Math.max(5.5, (islandHeight * 0.48) + ((((base >>> 8) & 255L) / 255.0) - 0.5) * 5.0);

                double dx = (plotBlockX - centerX) / rx;
                double dz = (plotBlockZ - centerZ) / rz;
                double ellipse = 1.0 - Math.sqrt((dx * dx) + (dz * dz));
                if (ellipse <= -0.25) continue;

                double n1 = (valueNoise2D(plotBlockX * 0.09 + gx * 3.11, plotBlockZ * 0.09 + gz * 2.73) - 0.5) * 0.45;
                double n2 = (valueNoise2D(plotBlockX * 0.17 + gx * 1.37, plotBlockZ * 0.17 + gz * 1.91) - 0.5) * 0.22;
                double lobeX = centerX + ((((base >>> 20) & 31L) - 15) * 0.6);
                double lobeZ = centerZ + ((((base >>> 52) & 31L) - 15) * 0.6);
                double lobe = 0.65 * radialStrength(plotBlockX, plotBlockZ, lobeX, lobeZ, Math.min(rx, rz) * 0.55);
                double islandDensity = (ellipse * 1.00) + n1 + n2 + lobe;

                if (islandDensity > bestDensity) {
                    bestDensity = islandDensity;
                    bestSeed = mixHash(base ^ 0x41C64E6DL);
                }
            }
        }

        return bestDensity > -0.10 ? new ThemeHit(bestSeed, bestDensity) : null;
    }

    private void generateChunkFromTemplate(IslandData island, int relChunkX, int relChunkZ, ChunkTemplateDef def) {
        World world = skyWorldService.getWorld();
        int worldChunkX = plotMinChunkX(island.getGridX()) + relChunkX;
        int worldChunkZ = plotMinChunkZ(island.getGridZ()) + relChunkZ;
        int baseX = worldChunkX << 4;
        int baseZ = worldChunkZ << 4;
        int y = SkyWorldService.SPAWN_Y;
        Biome effectiveBiome = biomeForTemplate(def);
        long seed = 918273645L + (relChunkX * 341873128712L) + (relChunkZ * 132897987541L);
        Random r = new Random(seed ^ 0x9E3779B97F4A7C15L);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            for (int yy = y; yy <= y + 10; yy++) {
                Block b = world.getBlockAt(baseX + x, yy, baseZ + z);
                if (!b.getType().isAir()) b.setType(Material.AIR, false);
            }
            for (int yy = world.getMinHeight(); yy < world.getMaxHeight(); yy += 4) world.setBiome(baseX + x, yy, baseZ + z, effectiveBiome);
        }

        int bestFeatureX = -1;
        int bestFeatureZ = -1;
        double bestFeatureScore = -9999;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int plotBlockX = (relChunkX * 16) + lx;
                int plotBlockZ = (relChunkZ * 16) + lz;
                double clusterField = computeIslandClusterField(plotBlockX, plotBlockZ);
                double noiseLow = valueNoise2D(plotBlockX * 0.065, plotBlockZ * 0.065);
                double noiseMid = valueNoise2D(plotBlockX * 0.12, plotBlockZ * 0.12);
                double ridge = 1.0 - Math.abs(valueNoise2D(plotBlockX * 0.18, plotBlockZ * 0.18) * 2.0 - 1.0);

                double density = (clusterField * 1.15)
                        + ((noiseLow - 0.5) * 0.45)
                        + ((noiseMid - 0.5) * 0.25)
                        + ((ridge - 0.5) * 0.12);

                if (density <= 0.34) {
                    continue;
                }

                int topYOffset = (int) Math.floor((density - 0.34) * 3.2 + ((noiseMid - 0.5) * 1.2));
                topYOffset = Math.max(0, Math.min(4, topYOffset));
                int topY = y + topYOffset;
                int thickness = 2 + (int) Math.floor((density - 0.34) * 5.2) + (ridge > 0.62 ? 1 : 0);
                thickness = Math.max(2, Math.min(9, thickness));

                Material topMaterial = def.top();
                if (isStarterChunk(relChunkX, relChunkZ) && isStarterSandCore(plotBlockX, plotBlockZ)) {
                    topMaterial = Material.SAND;
                }

                world.getBlockAt(baseX + lx, topY, baseZ + lz).setType(topMaterial, false);
                for (int t = 1; t <= thickness; t++) {
                    Material fill = def.filler();
                    if (isStarterChunk(relChunkX, relChunkZ)) {
                        // Starter island: thin dirt layer below grass, then stone base.
                        fill = (t <= 2) ? Material.DIRT : Material.STONE;
                        if (topMaterial == Material.SAND && t == 1) {
                            fill = Material.SANDSTONE;
                        }
                    }
                    world.getBlockAt(baseX + lx, topY - t, baseZ + lz).setType(fill, false);
                }

                double featureScore = density + (noiseLow * 0.2) + ((lx > 2 && lx < 13 && lz > 2 && lz < 13) ? 0.15 : 0.0);
                if (featureScore > bestFeatureScore) {
                    bestFeatureScore = featureScore;
                    bestFeatureX = lx;
                    bestFeatureZ = lz;
                }
            }
        }

        if (def.feature() != null && def.feature() != Material.AIR && bestFeatureX >= 0) {
            int topY = findTopSolidY(world, baseX + bestFeatureX, baseZ + bestFeatureZ, y + 8, y - 12);
            if (topY >= y - 4) {
                world.getBlockAt(baseX + bestFeatureX, topY + 1, baseZ + bestFeatureZ).setType(def.feature(), false);
            }
        }

        // Place moderate ore pockets in stone/deepslate layers.
        placeChunkOres(world, baseX, baseZ, y, relChunkX, relChunkZ, randomForChunk(island, relChunkX, relChunkZ));

        // Add natural overgrowth for less sterile-looking islands.
        decorateChunkSurface(world, baseX, baseZ, y, relChunkX, relChunkZ, effectiveBiome, r);
    }

    private Random randomForChunk(IslandData island, int relChunkX, int relChunkZ) {
        long seed = 0x5F3759DFL
                ^ (relChunkX * 341873128712L)
                ^ (relChunkZ * 132897987541L)
                ^ ((long) island.getGridX() * 0x9E3779B97F4A7C15L)
                ^ ((long) island.getGridZ() * 0xC2B2AE3D27D4EB4FL);
        return new Random(seed);
    }

    private void placeChunkOres(World world, int baseX, int baseZ, int baseY, int relChunkX, int relChunkZ, Random random) {
        boolean starter = isStarterChunk(relChunkX, relChunkZ);
        int minY = baseY - 12;
        int maxY = baseY + 1;

        int coalPockets = starter ? 5 : (2 + random.nextInt(3));
        int ironPockets = starter ? 4 : (1 + random.nextInt(3));
        int copperPockets = starter ? 3 : random.nextInt(3);
        int goldPockets = starter ? 2 : (random.nextDouble() < 0.45 ? 1 : 0);

        placeOrePockets(world, baseX, baseZ, minY, maxY, coalPockets, 3, Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, random);
        placeOrePockets(world, baseX, baseZ, minY, maxY, ironPockets, 3, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, random);
        placeOrePockets(world, baseX, baseZ, minY, maxY, copperPockets, 4, Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, random);
        placeOrePockets(world, baseX, baseZ, minY, maxY, goldPockets, 2, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, random);

        if (starter) {
            // 2 per starter chunk => at least 8 across the 2x2 starter area.
            placeGuaranteedStarterDiamonds(world, baseX, baseZ, minY, maxY, 2, random);
        } else if (random.nextDouble() < 0.20) {
            placeOrePockets(world, baseX, baseZ, minY, maxY - 2, 1, 2, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, random);
        }
    }

    private void placeOrePockets(World world, int baseX, int baseZ, int minY, int maxY, int pockets, int maxCluster,
                                 Material stoneOre, Material deepslateOre, Random random) {
        if (pockets <= 0) return;
        int safeMinY = Math.max(world.getMinHeight(), minY);
        int safeMaxY = Math.min(world.getMaxHeight() - 1, maxY);
        if (safeMinY > safeMaxY) return;

        for (int i = 0; i < pockets; i++) {
            int ox = baseX + random.nextInt(16);
            int oz = baseZ + random.nextInt(16);
            int oy = safeMinY + random.nextInt((safeMaxY - safeMinY) + 1);
            int cluster = 1 + random.nextInt(Math.max(1, maxCluster));
            for (int c = 0; c < cluster; c++) {
                int x = ox + random.nextInt(3) - 1;
                int y = oy + random.nextInt(3) - 1;
                int z = oz + random.nextInt(3) - 1;
                Block block = world.getBlockAt(x, y, z);
                if (!isOreReplaceable(block.getType())) continue;
                block.setType(isDeepslateHost(block.getType()) ? deepslateOre : stoneOre, false);
            }
        }
    }

    private void placeGuaranteedStarterDiamonds(World world, int baseX, int baseZ, int minY, int maxY, int required, Random random) {
        int placed = 0;
        int safeMinY = Math.max(world.getMinHeight(), minY);
        int safeMaxY = Math.min(world.getMaxHeight() - 1, maxY);
        for (int attempt = 0; attempt < 80 && placed < required; attempt++) {
            int x = baseX + random.nextInt(16);
            int z = baseZ + random.nextInt(16);
            int y = safeMinY + random.nextInt((safeMaxY - safeMinY) + 1);
            Block block = world.getBlockAt(x, y, z);
            if (!isOreReplaceable(block.getType())) continue;
            block.setType(isDeepslateHost(block.getType()) ? Material.DEEPSLATE_DIAMOND_ORE : Material.DIAMOND_ORE, false);
            placed++;
        }

        if (placed >= required) return;
        for (int y = safeMinY; y <= safeMaxY && placed < required; y++) {
            for (int x = 0; x < 16 && placed < required; x++) {
                for (int z = 0; z < 16 && placed < required; z++) {
                    Block block = world.getBlockAt(baseX + x, y, baseZ + z);
                    if (!isOreReplaceable(block.getType())) continue;
                    block.setType(isDeepslateHost(block.getType()) ? Material.DEEPSLATE_DIAMOND_ORE : Material.DIAMOND_ORE, false);
                    placed++;
                }
            }
        }
    }

    private boolean isOreReplaceable(Material type) {
        return switch (type) {
            case STONE, DEEPSLATE, COBBLED_DEEPSLATE, TUFF, ANDESITE, DIORITE, GRANITE -> true;
            default -> false;
        };
    }

    private boolean isDeepslateHost(Material type) {
        return type == Material.DEEPSLATE || type == Material.COBBLED_DEEPSLATE;
    }

    private void decorateChunkSurface(World world, int baseX, int baseZ, int baseY, int relChunkX, int relChunkZ, Biome biome, Random random) {
        if (isStarterChunk(relChunkX, relChunkZ)) {
            return; // keep starter center clear for spawn/core usability
        }

        int attempts = 18 + random.nextInt(18);
        for (int i = 0; i < attempts; i++) {
            int x = baseX + random.nextInt(16);
            int z = baseZ + random.nextInt(16);
            int topY = findTopSolidY(world, x, z, baseY + 8, baseY - 12);
            if (topY < baseY - 4) {
                continue;
            }

            Material ground = world.getBlockAt(x, topY, z).getType();
            if (!isNaturalDecorationGround(ground)) {
                continue;
            }
            if (!world.getBlockAt(x, topY + 1, z).getType().isAir()) {
                continue;
            }

            Material decoration = selectDecorationMaterial(biome, random, ground);
            if (decoration == null) {
                continue;
            }
            placeDecoration(world, x, topY + 1, z, decoration);
        }

        // Additional rustic/wild details depending on biome/theme.
        int rusticAttempts = 5 + random.nextInt(6);
        for (int i = 0; i < rusticAttempts; i++) {
            int x = baseX + random.nextInt(16);
            int z = baseZ + random.nextInt(16);
            int topY = findTopSolidY(world, x, z, baseY + 8, baseY - 12);
            if (topY < baseY - 4) continue;
            Material ground = world.getBlockAt(x, topY, z).getType();
            Material above = world.getBlockAt(x, topY + 1, z).getType();

            if (isColdBiome(biome) && ground == Material.SNOW_BLOCK && above.isAir() && random.nextDouble() < 0.35) {
                world.getBlockAt(x, topY + 1, z).setType(Material.PACKED_ICE, false);
                continue;
            }
            if ((biome == Biome.SWAMP || biome == Biome.MANGROVE_SWAMP) && (ground == Material.DIRT || ground == Material.GRASS_BLOCK)
                    && above.isAir() && random.nextDouble() < 0.45) {
                world.getBlockAt(x, topY + 1, z).setType(Material.MOSS_CARPET, false);
                continue;
            }
            if ((biome == Biome.FOREST || biome == Biome.BIRCH_FOREST || biome == Biome.JUNGLE || biome == Biome.SPARSE_JUNGLE)
                    && ground == Material.GRASS_BLOCK && above.isAir() && random.nextDouble() < 0.30) {
                world.getBlockAt(x, topY + 1, z).setType(Material.MOSS_CARPET, false);
                if (random.nextDouble() < 0.20 && world.getBlockAt(x, topY + 2, z).getType().isAir()) {
                    world.getBlockAt(x, topY + 2, z).setType(Material.OAK_LEAVES, false);
                }
            }
        }
    }

    private boolean isNaturalDecorationGround(Material ground) {
        return switch (ground) {
            case GRASS_BLOCK, DIRT, COARSE_DIRT, ROOTED_DIRT, PODZOL, MYCELIUM,
                    MOSS_BLOCK, SAND, RED_SAND, SNOW_BLOCK -> true;
            default -> false;
        };
    }

    private Material selectDecorationMaterial(Biome biome, Random random, Material ground) {
        if (ground == Material.SAND || ground == Material.RED_SAND) {
            return random.nextDouble() < 0.25 ? Material.DEAD_BUSH : null;
        }
        if (ground == Material.SNOW_BLOCK) {
            if (random.nextDouble() < 0.15) return Material.PACKED_ICE;
            return random.nextDouble() < 0.50 ? Material.SNOW : null;
        }
        if (ground == Material.MYCELIUM) {
            return random.nextBoolean() ? Material.RED_MUSHROOM : Material.BROWN_MUSHROOM;
        }

        boolean flowerBiome = switch (biome) {
            case FLOWER_FOREST, MEADOW, CHERRY_GROVE, PLAINS, SUNFLOWER_PLAINS, FOREST -> true;
            default -> false;
        };
        if (flowerBiome && random.nextDouble() < 0.32) {
            Material[] flowers = {
                    Material.DANDELION, Material.POPPY, Material.AZURE_BLUET, Material.ALLIUM,
                    Material.OXEYE_DAISY, Material.CORNFLOWER, Material.WHITE_TULIP,
                    Material.PINK_TULIP, Material.RED_TULIP, Material.ORANGE_TULIP
            };
            return flowers[random.nextInt(flowers.length)];
        }

        if (biome == Biome.TAIGA || biome == Biome.OLD_GROWTH_PINE_TAIGA || biome == Biome.OLD_GROWTH_SPRUCE_TAIGA) {
            if (random.nextDouble() < 0.20) return Material.SWEET_BERRY_BUSH;
            return random.nextDouble() < 0.42 ? Material.FERN : Material.LARGE_FERN;
        }

        double grassRoll = random.nextDouble();
        if (grassRoll < 0.65) return Material.SHORT_GRASS;
        if (grassRoll < 0.92) return Material.TALL_GRASS;
        return null;
    }

    private void placeDecoration(World world, int x, int y, int z, Material decoration) {
        if (decoration == Material.TALL_GRASS || decoration == Material.LARGE_FERN) {
            if (!world.getBlockAt(x, y + 1, z).getType().isAir()) {
                return;
            }
            BlockData lowerData = Bukkit.createBlockData(decoration);
            BlockData upperData = Bukkit.createBlockData(decoration);
            if (lowerData instanceof Bisected lower && upperData instanceof Bisected upper) {
                lower.setHalf(Bisected.Half.BOTTOM);
                upper.setHalf(Bisected.Half.TOP);
                world.getBlockAt(x, y, z).setBlockData(lowerData, false);
                world.getBlockAt(x, y + 1, z).setBlockData(upperData, false);
            } else {
                world.getBlockAt(x, y, z).setType(decoration, false);
            }
            return;
        }
        world.getBlockAt(x, y, z).setType(decoration, false);
    }

    private Biome biomeForTemplate(ChunkTemplateDef def) {
        if (def == null) return Biome.PLAINS;
        if (isColdThemeMaterial(def.top()) || isColdThemeMaterial(def.filler()) || isColdThemeMaterial(def.feature())) {
            return switch (def.biome()) {
                case FROZEN_OCEAN, DEEP_FROZEN_OCEAN, FROZEN_RIVER, SNOWY_PLAINS, ICE_SPIKES, SNOWY_TAIGA,
                        SNOWY_BEACH, SNOWY_SLOPES, FROZEN_PEAKS, JAGGED_PEAKS, GROVE -> def.biome();
                default -> Biome.SNOWY_PLAINS;
            };
        }
        return def.biome();
    }

    private boolean isColdThemeMaterial(Material material) {
        if (material == null) return false;
        return switch (material) {
            case ICE, PACKED_ICE, BLUE_ICE, SNOW_BLOCK, POWDER_SNOW, SNOW, FROSTED_ICE -> true;
            default -> false;
        };
    }

    private boolean isColdBiome(Biome biome) {
        return switch (biome) {
            case FROZEN_OCEAN, DEEP_FROZEN_OCEAN, FROZEN_RIVER, SNOWY_PLAINS, ICE_SPIKES, SNOWY_TAIGA,
                    SNOWY_BEACH, SNOWY_SLOPES, FROZEN_PEAKS, JAGGED_PEAKS, GROVE -> true;
            default -> false;
        };
    }

    private boolean isStarterSandCore(int plotBlockX, int plotBlockZ) {
        double cx = 31.5 * 16.0 + 8.0;
        double cz = 31.5 * 16.0 + 8.0;
        double dx = plotBlockX - cx;
        double dz = plotBlockZ - cz;
        double dist = Math.sqrt((dx * dx) + (dz * dz));
        if (dist < 6.0 || dist > 13.5) return false;
        double noise = valueNoise2D(plotBlockX * 0.22 + 91.0, plotBlockZ * 0.22 + 37.0);
        return noise > 0.42;
    }

    private double computeIslandClusterField(int plotBlockX, int plotBlockZ) {
        // Starter island: force a continuous 2x2 island around the center (chunks 31/32).
        double density = starterIslandField(plotBlockX, plotBlockZ);

        // Macro-cell based free-positioned islands. One cell can spawn 0 or 1 island, sized 1x1..3x3 chunks.
        // Using 5x5 chunk cells leaves natural air gaps and avoids a visible chunk raster.
        final int cellSizeChunks = MACRO_CELL_SIZE_CHUNKS;
        final int cellSizeBlocks = cellSizeChunks * 16;

        int cellX = Math.floorDiv(plotBlockX, cellSizeBlocks);
        int cellZ = Math.floorDiv(plotBlockZ, cellSizeBlocks);

        for (int gx = cellX - 1; gx <= cellX + 1; gx++) {
            for (int gz = cellZ - 1; gz <= cellZ + 1; gz++) {
                long base = macroCellBaseSeed(gx, gz);
                if (!macroCellHasIsland(base)) {
                    continue; // not every macro-cell has an island
                }

                int sizeChunksX = macroCellSizeChunksX(base);
                int sizeChunksZ = macroCellSizeChunksZ(base);

                // Keep at least one chunk of air margin inside the 5x5 macro-cell.
                int minOffsetX = 1;
                int minOffsetZ = 1;
                int maxOffsetX = cellSizeChunks - sizeChunksX - 1;
                int maxOffsetZ = cellSizeChunks - sizeChunksZ - 1;
                if (maxOffsetX < minOffsetX || maxOffsetZ < minOffsetZ) {
                    continue;
                }
                int chunkOffsetX = macroCellChunkOffsetX(base, sizeChunksX);
                int chunkOffsetZ = macroCellChunkOffsetZ(base, sizeChunksZ);
                if (chunkOffsetX < minOffsetX || chunkOffsetX > maxOffsetX || chunkOffsetZ < minOffsetZ || chunkOffsetZ > maxOffsetZ) {
                    continue;
                }

                int islandMinX = (gx * cellSizeBlocks) + (chunkOffsetX * 16);
                int islandMinZ = (gz * cellSizeBlocks) + (chunkOffsetZ * 16);
                int islandWidth = sizeChunksX * 16;
                int islandHeight = sizeChunksZ * 16;

                // Free placement inside the chosen chunk rectangle (prevents perfect center alignment).
                double centerX = islandMinX + (islandWidth * 0.5) + ((((base >>> 40) & 255L) / 255.0) - 0.5) * 8.0;
                double centerZ = islandMinZ + (islandHeight * 0.5) + ((((base >>> 48) & 255L) / 255.0) - 0.5) * 8.0;

                double rx = Math.max(5.5, (islandWidth * 0.48) + ((((base >>> 16) & 255L) / 255.0) - 0.5) * 5.0);
                double rz = Math.max(5.5, (islandHeight * 0.48) + ((((base >>> 8) & 255L) / 255.0) - 0.5) * 5.0);

                double dx = (plotBlockX - centerX) / rx;
                double dz = (plotBlockZ - centerZ) / rz;
                double ellipse = 1.0 - Math.sqrt((dx * dx) + (dz * dz));
                if (ellipse <= -0.25) {
                    continue;
                }

                // Small irregularities and sub-lobes to avoid linear rectangles.
                double n1 = (valueNoise2D(plotBlockX * 0.09 + gx * 3.11, plotBlockZ * 0.09 + gz * 2.73) - 0.5) * 0.45;
                double n2 = (valueNoise2D(plotBlockX * 0.17 + gx * 1.37, plotBlockZ * 0.17 + gz * 1.91) - 0.5) * 0.22;
                double lobeX = centerX + ((((base >>> 20) & 31L) - 15) * 0.6);
                double lobeZ = centerZ + ((((base >>> 52) & 31L) - 15) * 0.6);
                double lobe = 0.65 * radialStrength(plotBlockX, plotBlockZ, lobeX, lobeZ, Math.min(rx, rz) * 0.55);

                double islandDensity = (ellipse * 1.00) + n1 + n2 + lobe;
                density = Math.max(density, islandDensity);
            }
        }
        return density;
    }

    private long macroCellBaseSeed(int gx, int gz) {
        return mixHash((gx * 0x9E3779B97F4A7C15L) ^ (gz * 0xC2B2AE3D27D4EB4FL) ^ 0x7f4a7c159e3779b9L);
    }

    private boolean macroCellHasIsland(long base) {
        double spawnChance = ((base >>> 12) & 1023) / 1023.0;
        return spawnChance <= 0.58;
    }

    private int macroCellSizeChunksX(long base) {
        return 1 + (int) (((base >>> 24) & 3L) % 3L);
    }

    private int macroCellSizeChunksZ(long base) {
        return 1 + (int) (((base >>> 28) & 3L) % 3L);
    }

    private int macroCellChunkOffsetX(long base, int sizeChunksX) {
        int minOffsetX = 1;
        int maxOffsetX = MACRO_CELL_SIZE_CHUNKS - sizeChunksX - 1;
        if (maxOffsetX < minOffsetX) return -1;
        return minOffsetX + (int) (((base >>> 32) & 15L) % (maxOffsetX - minOffsetX + 1));
    }

    private int macroCellChunkOffsetZ(long base, int sizeChunksZ) {
        int minOffsetZ = 1;
        int maxOffsetZ = MACRO_CELL_SIZE_CHUNKS - sizeChunksZ - 1;
        if (maxOffsetZ < minOffsetZ) return -1;
        return minOffsetZ + (int) (((base >>> 36) & 15L) % (maxOffsetZ - minOffsetZ + 1));
    }

    private double starterIslandField(int plotBlockX, int plotBlockZ) {
        int min = CENTER_A * 16;
        int max = (CENTER_B + 1) * 16 - 1;
        if (plotBlockX < min || plotBlockX > max || plotBlockZ < min || plotBlockZ > max) {
            return -1.0;
        }
        // Center of the starter 2x2 chunks (31/32 x 31/32) in plot-local block coords.
        double cx = 31.5 * 16.0 + 8.0;
        double cz = 31.5 * 16.0 + 8.0;
        double dx = (plotBlockX - cx) / 21.5;
        double dz = (plotBlockZ - cz) / 21.5;
        double base = 1.0 - Math.sqrt(dx * dx + dz * dz);

        // Plus a cross-shaped bridge exactly over the chunk seams so there is no visible split.
        double seamX = Math.max(0.0, 1.0 - (Math.abs(plotBlockX - 512.0) / 6.0));
        double seamZ = Math.max(0.0, 1.0 - (Math.abs(plotBlockZ - 512.0) / 6.0));
        double seamBridge = Math.max(seamX * 0.75, seamZ * 0.75);

        double localNoise = (valueNoise2D(plotBlockX * 0.11 + 33.0, plotBlockZ * 0.11 + 57.0) - 0.5) * 0.20;
        return Math.max(base + seamBridge + localNoise, -1.0);
    }

    private double radialStrength(double x, double z, double cx, double cz, double r) {
        double dx = (x - cx) / Math.max(0.001, r);
        double dz = (z - cz) / Math.max(0.001, r);
        double dist = Math.sqrt((dx * dx) + (dz * dz));
        return Math.max(0.0, 1.0 - dist);
    }

    private double edgeStripStrength(int p) {
        double center = 7.5;
        double d = Math.abs(p - center);
        return Math.max(0.0, (5.0 - d) * 0.03);
    }

    private double edgeDistancePenalty(int x, int z, boolean bridgeNorth, boolean bridgeSouth, boolean bridgeWest, boolean bridgeEast,
                                       int relChunkX, int relChunkZ) {
        double penalty = 0.0;
        if (z <= 1 && !bridgeNorth) penalty += (z == 0 ? 0.35 : 0.16);
        if (z >= 14 && !bridgeSouth) penalty += (z == 15 ? 0.35 : 0.16);
        if (x <= 1 && !bridgeWest) penalty += (x == 0 ? 0.35 : 0.16);
        if (x >= 14 && !bridgeEast) penalty += (x == 15 ? 0.35 : 0.16);

        // Starter 2x2 chunks are intentionally allowed to connect.
        if (isStarterChunk(relChunkX, relChunkZ)) {
            penalty *= 0.35;
        }
        return penalty;
    }

    private boolean edgeBridge(int a, int b, int axis) {
        // Only allow merges inside a 2x2 cluster -> max 4 chunks connected.
        if (axis == 0) {
            // horizontal edge between (a,b-1) and (a,b)
            int upperChunkZ = b - 1;
            int lowerChunkZ = b;
            if ((upperChunkZ / 2) != (lowerChunkZ / 2)) return false;
        } else {
            // vertical edge between (a-1,b) and (a,b)
            int leftChunkX = a - 1;
            int rightChunkX = a;
            if ((leftChunkX / 2) != (rightChunkX / 2)) return false;
        }
        long h = mixHash(a * 73856093L + b * 19349663L + axis * 83492791L + 998244353L);
        return (h & 7L) <= 1L; // ~25%, more visible separation
    }

    private double startAreaConnectionBonus(int plotBlockX, int plotBlockZ) {
        // Force the central 2x2 starter area (chunks 31/32 x 31/32) to connect smoothly.
        if (plotBlockX < 31 * 16 || plotBlockX > (33 * 16) - 1 || plotBlockZ < 31 * 16 || plotBlockZ > (33 * 16) - 1) {
            return 0.0;
        }
        double dx = (plotBlockX - 511.5) / 26.0;
        double dz = (plotBlockZ - 511.5) / 26.0;
        double dist = Math.sqrt(dx * dx + dz * dz);
        return Math.max(0.0, 0.55 - (dist * 0.45));
    }

    private boolean isStarterChunk(int relChunkX, int relChunkZ) {
        return (relChunkX == CENTER_A || relChunkX == CENTER_B) && (relChunkZ == CENTER_A || relChunkZ == CENTER_B);
    }

    private int findTopSolidY(World world, int x, int z, int startY, int minY) {
        for (int y = startY; y >= minY; y--) {
            if (!world.getBlockAt(x, y, z).getType().isAir()) {
                return y;
            }
        }
        return -1;
    }

    private double valueNoise2D(double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        int x1 = x0 + 1;
        int z1 = z0 + 1;
        double tx = x - x0;
        double tz = z - z0;
        double sx = smoothStep(tx);
        double sz = smoothStep(tz);
        double v00 = hash01(x0, z0);
        double v10 = hash01(x1, z0);
        double v01 = hash01(x0, z1);
        double v11 = hash01(x1, z1);
        double ix0 = lerp(v00, v10, sx);
        double ix1 = lerp(v01, v11, sx);
        return lerp(ix0, ix1, sz);
    }

    private double hash01(int x, int z) {
        long h = mixHash((x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ 0x165667B19E3779F9L);
        long bits = (h >>> 11) & ((1L << 53) - 1);
        return bits / (double) (1L << 53);
    }

    private long mixHash(long x) {
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return x;
    }

    private double smoothStep(double t) {
        return t * t * (3.0 - (2.0 * t));
    }

    private double lerp(double a, double b, double t) {
        return a + ((b - a) * t);
    }

    private void clearEdgeStrip(World world, int baseX, int baseZ, int y, String edge) {
        switch (edge) {
            case "north" -> {
                for (int x = 0; x < 16; x++) for (int z = 0; z <= 1; z++) clearColumn(world, baseX + x, baseZ + z, y);
            }
            case "south" -> {
                for (int x = 0; x < 16; x++) for (int z = 14; z < 16; z++) clearColumn(world, baseX + x, baseZ + z, y);
            }
            case "west" -> {
                for (int x = 0; x <= 1; x++) for (int z = 0; z < 16; z++) clearColumn(world, baseX + x, baseZ + z, y);
            }
            case "east" -> {
                for (int x = 14; x < 16; x++) for (int z = 0; z < 16; z++) clearColumn(world, baseX + x, baseZ + z, y);
            }
            default -> {
            }
        }
    }

    private void clearColumn(World world, int x, int z, int y) {
        for (int yy = y + 8; yy >= y - 12; yy--) {
            if (yy == world.getMinHeight()) continue;
            if (!world.getBlockAt(x, yy, z).getType().isAir()) {
                world.getBlockAt(x, yy, z).setType(Material.AIR, false);
            }
        }
    }

    public boolean setBiomeForChunk(IslandData island, int relChunkX, int relChunkZ, Biome biome) {
        if (!isChunkUnlocked(island, relChunkX, relChunkZ)) return false;
        World world = skyWorldService.getWorld();
        int worldChunkX = plotMinChunkX(island.getGridX()) + relChunkX;
        int worldChunkZ = plotMinChunkZ(island.getGridZ()) + relChunkZ;
        int baseX = worldChunkX << 4;
        int baseZ = worldChunkZ << 4;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = world.getMinHeight(); y < world.getMaxHeight(); y += 4) {
            world.setBiome(baseX + x, y, baseZ + z, biome);
        }
        return true;
    }

    public int setBiomeForIsland(IslandData island, Biome biome, boolean unlockedOnly) {
        if (island == null || biome == null) return 0;
        int changed = 0;
        for (int relX = 0; relX < ISLAND_CHUNKS; relX++) {
            for (int relZ = 0; relZ < ISLAND_CHUNKS; relZ++) {
                if (unlockedOnly && !isChunkUnlocked(island, relX, relZ)) continue;
                if (setBiomeForChunkUnchecked(island, relX, relZ, biome)) changed++;
            }
        }
        return changed;
    }

    private boolean setBiomeForChunkUnchecked(IslandData island, int relChunkX, int relChunkZ, Biome biome) {
        World world = skyWorldService.getWorld();
        if (world == null) return false;
        int worldChunkX = plotMinChunkX(island.getGridX()) + relChunkX;
        int worldChunkZ = plotMinChunkZ(island.getGridZ()) + relChunkZ;
        int baseX = worldChunkX << 4;
        int baseZ = worldChunkZ << 4;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = world.getMinHeight(); y < world.getMaxHeight(); y += 4) {
            world.setBiome(baseX + x, y, baseZ + z, biome);
        }
        return true;
    }

    public Biome getBiomeForChunk(IslandData island, int relChunkX, int relChunkZ) {
        World world = skyWorldService.getWorld();
        int worldChunkX = plotMinChunkX(island.getGridX()) + relChunkX;
        int worldChunkZ = plotMinChunkZ(island.getGridZ()) + relChunkZ;
        return world.getBiome((worldChunkX << 4) + 8, SkyWorldService.SPAWN_Y, (worldChunkZ << 4) + 8);
    }

    public IslandTimeMode getIslandTimeMode(IslandData island) {
        if (island == null) return IslandTimeMode.NORMAL;
        return IslandTimeMode.from(island.getIslandTimeMode());
    }

    public IslandTimeMode cycleIslandTimeMode(IslandData island) {
        if (island == null) return IslandTimeMode.NORMAL;
        IslandTimeMode next = getIslandTimeMode(island).next();
        island.setIslandTimeMode(next.name());
        save();
        return next;
    }

    public void setIslandTimeMode(IslandData island, IslandTimeMode mode) {
        if (island == null || mode == null) return;
        island.setIslandTimeMode(mode.name());
        island.setLastActiveAt(System.currentTimeMillis());
        save();
    }

    public boolean spendStoredExperience(IslandData island, long amount) {
        if (island == null) return false;
        long cost = Math.max(0L, amount);
        if (cost <= 0L) return true;
        if (!island.takeStoredExperience(cost)) return false;
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public long getBiomeChangeCost(boolean islandWide) {
        return islandWide ? BIOME_CHANGE_COST_ISLAND : BIOME_CHANGE_COST_CHUNK;
    }

    public long getTimeModeChangeCost() {
        return TIME_MODE_CHANGE_COST;
    }

    public long getXpBottleCostPerBottle() {
        return XP_BOTTLE_COST_POINTS;
    }

    public long getXpBottlePointsPerBottle() {
        return XP_BOTTLE_POINTS;
    }

    public long getGrowthBoostCost(int tier) {
        return switch (tier) {
            case 1 -> 300L;
            case 2 -> 700L;
            case 3 -> 1500L;
            default -> 0L;
        };
    }

    public long getGrowthBoostDurationMillis(int tier) {
        return switch (tier) {
            case 1 -> 15L * 60L * 1000L;
            case 2 -> 30L * 60L * 1000L;
            case 3 -> 60L * 60L * 1000L;
            default -> 0L;
        };
    }

    public int getGrowthBoostTier(IslandData island, int relChunkX, int relChunkZ) {
        if (island == null) return 0;
        String key = chunkKey(relChunkX, relChunkZ);
        long until = island.getGrowthBoostUntil().getOrDefault(key, 0L);
        int tier = Math.max(0, island.getGrowthBoostTier().getOrDefault(key, 0));
        if (until <= System.currentTimeMillis() || tier <= 0) {
            if (until > 0L || tier > 0) {
                island.getGrowthBoostUntil().remove(key);
                island.getGrowthBoostTier().remove(key);
                save();
            }
            return 0;
        }
        return tier;
    }

    public long getGrowthBoostRemainingMillis(IslandData island, int relChunkX, int relChunkZ) {
        if (island == null) return 0L;
        String key = chunkKey(relChunkX, relChunkZ);
        long until = island.getGrowthBoostUntil().getOrDefault(key, 0L);
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0L) {
            getGrowthBoostTier(island, relChunkX, relChunkZ);
            return 0L;
        }
        return remaining;
    }

    public boolean buyGrowthBoost(IslandData island, int relChunkX, int relChunkZ, int tier) {
        if (island == null) return false;
        if (!isChunkUnlocked(island, relChunkX, relChunkZ)) return false;
        long cost = getGrowthBoostCost(tier);
        long duration = getGrowthBoostDurationMillis(tier);
        if (cost <= 0L || duration <= 0L) return false;
        if (!spendStoredExperience(island, cost)) return false;
        String key = chunkKey(relChunkX, relChunkZ);
        long now = System.currentTimeMillis();
        long base = Math.max(now, island.getGrowthBoostUntil().getOrDefault(key, now));
        island.getGrowthBoostUntil().put(key, base + duration);
        island.getGrowthBoostTier().put(key, Math.max(tier, island.getGrowthBoostTier().getOrDefault(key, 0)));
        island.setLastActiveAt(now);
        save();
        return true;
    }

    public String islandTimeModeLabel(IslandTimeMode mode) {
        if (mode == null) return "Normal";
        return switch (mode) {
            case DAY -> "Nur Tag";
            case SUNSET -> "Sonnenuntergang";
            case MIDNIGHT -> "Nacht";
            case NORMAL -> "Normal";
        };
    }

    public int plotMinChunkX(int gridX) { return gridX * ISLAND_CHUNKS - 32; }
    public int plotMinChunkZ(int gridZ) { return gridZ * ISLAND_CHUNKS - 32; }
    public int relativeChunkX(IslandData island, int worldChunkX) { return worldChunkX - plotMinChunkX(island.getGridX()); }
    public int relativeChunkZ(IslandData island, int worldChunkZ) { return worldChunkZ - plotMinChunkZ(island.getGridZ()); }
    public int displayChunkX(int relChunkX) { return relChunkX - CENTER_A; }
    public int displayChunkZ(int relChunkZ) { return relChunkZ - CENTER_A; }

    public Location getChunkCenterLocation(IslandData island, int relChunkX, int relChunkZ) {
        int worldChunkX = plotMinChunkX(island.getGridX()) + relChunkX;
        int worldChunkZ = plotMinChunkZ(island.getGridZ()) + relChunkZ;
        return new Location(skyWorldService.getWorld(), (worldChunkX << 4) + 8, SkyWorldService.SPAWN_Y, (worldChunkZ << 4) + 8);
    }

    public Location getPlotCenter(int gridX, int gridZ) {
        int centerChunkX = plotMinChunkX(gridX) + 32;
        int centerChunkZ = plotMinChunkZ(gridZ) + 32;
        return new Location(skyWorldService.getWorld(), centerChunkX << 4, SkyWorldService.SPAWN_Y, centerChunkZ << 4);
    }

    public Location getSpawnLocation() { return new Location(skyWorldService.getWorld(), 0.5, SkyWorldService.SPAWN_Y + 1, 0.5); }
    public ItemStack createPlotWand() {
        ItemStack wand = new ItemStack(Material.STICK);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Grundst\u00fccks-Stab");
        meta.setLore(List.of(
                ChatColor.GRAY + "Linksklick Block = Pos1",
                ChatColor.GRAY + "Rechtsklick Block = Pos2",
                ChatColor.AQUA + "Mittelklick = Grundst\u00fccks-Men\u00fc \u00f6ffnen",
                ChatColor.YELLOW + "/is plot create erstellt das Grundst\u00fcck",
                ChatColor.YELLOW + "/is plot delete l\u00f6scht Grundst\u00fcck am Standort"
        ));
        meta.getPersistentDataContainer().set(plotWandKey, PersistentDataType.BYTE, (byte) 1);
        meta.addEnchant(Enchantment.LUCK, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        wand.setItemMeta(meta);
        return wand;
    }

    public boolean isPlotWand(ItemStack item) {
        if (item == null || item.getType() != Material.STICK || !item.hasItemMeta()) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(plotWandKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public void setPlotSelectionPos1(UUID playerId, Location location) {
        if (playerId == null) return;
        if (location == null) plotSelectionPos1.remove(playerId);
        else plotSelectionPos1.put(playerId, location.clone());
    }

    public void setPlotSelectionPos2(UUID playerId, Location location) {
        if (playerId == null) return;
        if (location == null) plotSelectionPos2.remove(playerId);
        else plotSelectionPos2.put(playerId, location.clone());
    }

    public Location getPlotSelectionPos1(UUID playerId) {
        Location loc = plotSelectionPos1.get(playerId);
        return loc == null ? null : loc.clone();
    }

    public Location getPlotSelectionPos2(UUID playerId) {
        Location loc = plotSelectionPos2.get(playerId);
        return loc == null ? null : loc.clone();
    }

    public void clearPlotSelection(UUID playerId) {
        if (playerId == null) return;
        plotSelectionPos1.remove(playerId);
        plotSelectionPos2.remove(playerId);
    }
    public Map<Integer, IslandLevelDefinition> getLevelDefinitions() { return levelDefinitions; }
    public IslandLevelDefinition getCurrentLevelDef(IslandData island) { return levelDefinitions.getOrDefault(island.getLevel(), levelDefinitions.get(1)); }
    public IslandLevelDefinition getNextLevelDef(IslandData island) { return levelDefinitions.get(island.getLevel() + 1); }

    public boolean canLevelUp(IslandData island) {
        IslandLevelDefinition next = getNextLevelDef(island);
        if (next == null) return false;
        if (calculateIslandLevel(island) < requiredIslandLevelForUpgrade(next.getLevel())) return false;
        if (island.getStoredExperience() < requiredExperienceForLevel(next.getLevel())) return false;
        for (Map.Entry<Material, Integer> req : next.getRequirements().entrySet()) {
            if (island.getProgress(req.getKey()) < req.getValue()) return false;
        }
        return true;
    }

    public boolean levelUp(IslandData island) {
        IslandLevelDefinition next = getNextLevelDef(island);
        if (next == null || !canLevelUp(island)) return false;
        for (Map.Entry<Material, Integer> req : next.getRequirements().entrySet()) {
            island.takeProgress(req.getKey(), req.getValue());
        }
        island.takeStoredExperience(requiredExperienceForLevel(next.getLevel()));
        island.setLevel(next.getLevel());
        island.setAvailableChunkUnlocks(island.getAvailableChunkUnlocks() + next.getChunkUnlocksGranted());
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public long requiredIslandLevelForUpgrade(int level) {
        return Math.max(1L, level * 5L);
    }

    public double calculateIslandLevelValue(IslandData island) {
        if (island == null) return 0.0D;
        double total = 0.0D;
        for (Map.Entry<String, Integer> entry : island.getProgress().entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) continue;
            Material material = Material.matchMaterial(entry.getKey());
            if (material == null) continue;
            total += CoreService.blockValueFor(material) * entry.getValue();
        }
        return Math.max(0.0D, total);
    }

    public double calculateReservedUpgradeLevelValue(IslandData island) {
        if (island == null) return 0.0D;
        IslandLevelDefinition next = getNextLevelDef(island);
        if (next == null) return 0.0D;
        double reserved = 0.0D;
        for (Map.Entry<Material, Integer> req : next.getRequirements().entrySet()) {
            int cur = island.getProgress(req.getKey());
            int usedForUpgrade = Math.min(Math.max(0, cur), Math.max(0, req.getValue()));
            if (usedForUpgrade <= 0) continue;
            reserved += CoreService.blockValueFor(req.getKey()) * usedForUpgrade;
        }
        return Math.max(0.0D, reserved);
    }

    public long calculateIslandLevel(IslandData island) {
        return Math.max(0L, (long) Math.floor(calculateIslandLevelValue(island)));
    }

    public long requiredExperienceForLevel(int level) {
        return Math.max(30L, level * 30L);
    }

    public int getAnimalCount(IslandData island) { return (int) getEntitiesInIsland(island).stream().filter(e -> e instanceof Animals).count(); }
    public int getGolemCount(IslandData island) { return (int) getEntitiesInIsland(island).stream().filter(e -> isTrackedGolem(e.getType())).count(); }
    public int getVillagerCount(IslandData island) { return (int) getEntitiesInIsland(island).stream().filter(e -> e instanceof Villager).count(); }
    public int getArmorStandCount(IslandData island) {
        return (int) getEntitiesInIsland(island).stream()
                .filter(e -> e instanceof ArmorStand)
                .filter(e -> e.getScoreboardTags().stream().noneMatch(tag -> tag.startsWith("skycity_")))
                .count();
    }
    public boolean isWithinAnimalLimit(IslandData island) { return getAnimalCount(island) < getCurrentLevelDef(island).getAnimalLimit(); }
    public boolean isWithinGolemLimit(IslandData island) { return getGolemCount(island) < getCurrentLevelDef(island).getGolemLimit(); }
    public boolean isWithinVillagerLimit(IslandData island) { return getVillagerCount(island) < getCurrentLevelDef(island).getVillagerLimit(); }
    public boolean isWithinArmorStandLimit(IslandData island) { return getArmorStandCount(island) < getCurrentLevelDef(island).getArmorStandLimit(); }

    public boolean isTrackedGolem(EntityType type) {
        if (type == null) return false;
        String name = type.name();
        return type == EntityType.IRON_GOLEM
                || "SNOWMAN".equals(name)
                || "SNOW_GOLEM".equals(name)
                || "COPPER_GOLEM".equals(name);
    }

    public int getCachedInventoryBlockCount(IslandData island) { return island.getCachedBlockCounts().getOrDefault(CACHE_INV, 0); }
    public int getCachedHopperCount(IslandData island) { return island.getCachedBlockCounts().getOrDefault(CACHE_HOPPER, 0); }
    public int getCachedPistonCount(IslandData island) { return island.getCachedBlockCounts().getOrDefault(CACHE_PISTON, 0); }
    public int getCachedObserverCount(IslandData island) { return island.getCachedBlockCounts().getOrDefault(CACHE_OBSERVER, 0); }
    public int getCachedDispenserCount(IslandData island) { return island.getCachedBlockCounts().getOrDefault(CACHE_DISPENSER, 0); }
    public int getCachedCactusCount(IslandData island) { return island.getCachedBlockCounts().getOrDefault(CACHE_CACTUS, 0); }
    public int getCachedKelpCount(IslandData island) { return island.getCachedBlockCounts().getOrDefault(CACHE_KELP, 0); }
    public int getCachedBambooCount(IslandData island) { return island.getCachedBlockCounts().getOrDefault(CACHE_BAMBOO, 0); }

    private Optional<PveZoneRuntime> buildPveZoneRuntime(IslandData island, ParcelData parcel) {
        if (island == null || parcel == null) return Optional.empty();
        World world = skyWorldService.getWorld();
        if (world == null) return Optional.empty();

        List<Block> startBlocks = new ArrayList<>();
        List<PveSpawnMarker> markers = new ArrayList<>();
        int minStartX = Integer.MAX_VALUE;
        int minStartZ = Integer.MAX_VALUE;
        int maxStartX = Integer.MIN_VALUE;
        int maxStartZ = Integer.MIN_VALUE;
        Integer startY = null;

        for (int x = parcel.getMinX(); x <= parcel.getMaxX(); x++) {
            for (int y = parcel.getMinY(); y <= parcel.getMaxY(); y++) {
                for (int z = parcel.getMinZ(); z <= parcel.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (!type.name().endsWith("_WOOL")) continue;
                    if (type == Material.WHITE_WOOL) {
                        startBlocks.add(block);
                        minStartX = Math.min(minStartX, x);
                        minStartZ = Math.min(minStartZ, z);
                        maxStartX = Math.max(maxStartX, x);
                        maxStartZ = Math.max(maxStartZ, z);
                        if (startY == null) startY = y;
                    } else {
                        PveSpawnMarker marker = createPveSpawnMarker(block, markers.size() + 1);
                        if (marker != null) markers.add(marker);
                    }
                }
            }
        }

        if (startBlocks.isEmpty() || markers.isEmpty() || startY == null) return Optional.empty();
        int width = maxStartX - minStartX + 1;
        int depth = maxStartZ - minStartZ + 1;
        if (width > 5 || depth > 5) return Optional.empty();
        int rectArea = width * depth;
        if (rectArea - startBlocks.size() > 4) return Optional.empty();

        Location respawnLocation = new Location(world, (minStartX + maxStartX) / 2.0 + 0.5, startY + 1.0, (minStartZ + maxStartZ) / 2.0 + 0.5);
        if (!isPveZoneClosed(parcel, respawnLocation)) return Optional.empty();
        return Optional.of(new PveZoneRuntime(island.getOwner(), parcel.getChunkKey(), parcel, minStartX, minStartZ, maxStartX, maxStartZ, startY, respawnLocation, markers));
    }

    private boolean isPveZoneClosed(ParcelData parcel, Location startLocation) {
        if (parcel == null || startLocation == null || startLocation.getWorld() == null) return false;
        Block startBlock = startLocation.getBlock();
        if (!startBlock.isPassable()) return false;
        Set<String> visited = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(startBlock);
        while (!queue.isEmpty()) {
            Block current = queue.removeFirst();
            String key = current.getX() + ":" + current.getY() + ":" + current.getZ();
            if (!visited.add(key)) continue;
            if (current.getX() <= parcel.getMinX() || current.getX() >= parcel.getMaxX()
                    || current.getY() <= parcel.getMinY() || current.getY() >= parcel.getMaxY()
                    || current.getZ() <= parcel.getMinZ() || current.getZ() >= parcel.getMaxZ()) {
                return false;
            }
            for (int[] offset : List.of(new int[]{1, 0, 0}, new int[]{-1, 0, 0}, new int[]{0, 1, 0}, new int[]{0, -1, 0}, new int[]{0, 0, 1}, new int[]{0, 0, -1})) {
                Block next = current.getRelative(offset[0], offset[1], offset[2]);
                if (next.getX() < parcel.getMinX() || next.getX() > parcel.getMaxX()
                        || next.getY() < parcel.getMinY() || next.getY() > parcel.getMaxY()
                        || next.getZ() < parcel.getMinZ() || next.getZ() > parcel.getMaxZ()) {
                    return false;
                }
                if (next.isPassable()) {
                    queue.add(next);
                }
            }
        }
        return true;
    }

    private PveSpawnMarker createPveSpawnMarker(Block woolBlock, int index) {
        Material type = woolBlock.getType();
        EntityType entityType;
        String name;
        int level;
        int reward;
        switch (type) {
            case LIGHT_GRAY_WOOL -> {
                entityType = EntityType.ZOMBIE;
                name = "Verfallener";
                level = 1;
                reward = 1;
            }
            case GREEN_WOOL -> {
                entityType = EntityType.SPIDER;
                name = "J\u00e4ger";
                level = 2;
                reward = 1;
            }
            case YELLOW_WOOL -> {
                entityType = EntityType.SKELETON;
                name = "Scharfsch\u00fctze";
                level = 2;
                reward = 2;
            }
            case ORANGE_WOOL -> {
                entityType = EntityType.HUSK;
                name = "Brueter";
                level = 3;
                reward = 2;
            }
            case BLUE_WOOL -> {
                entityType = EntityType.DROWNED;
                name = "Flutkrieger";
                level = 3;
                reward = 2;
            }
            case RED_WOOL -> {
                entityType = EntityType.CREEPER;
                name = "Sprenger";
                level = 4;
                reward = 3;
            }
            case BLACK_WOOL -> {
                entityType = EntityType.WITHER_SKELETON;
                name = "Albtraum";
                level = 5;
                reward = 4;
            }
            default -> {
                return null;
            }
        }

        Block above = woolBlock.getRelative(0, 1, 0);
        Location spawnLocation = above.isPassable()
                ? woolBlock.getLocation().add(0.5, 1.0, 0.5)
                : above.getRelative(0, 1, 0).getLocation().add(0.5, 0.0, 0.5);
        return new PveSpawnMarker("m" + index, woolBlock.getLocation(), spawnLocation, entityType, "Stufe " + level + " " + name, level, reward);
    }

    private void startNextPveWave(PveZoneRuntime runtime) {
        if (runtime == null) return;
        runtime.currentWave++;
        broadcastPveZone(runtime, ChatColor.GOLD + "PvE-Welle " + runtime.currentWave + "/" + runtime.requiredWaves + " startet.");
        int participantCount = Math.max(1, runtime.participants.size());
        int areaTier = computePveAreaTier(runtime.floorArea);
        for (PveSpawnMarker marker : runtime.markers) {
            int amount = Math.max(1, 1 + areaTier + ((runtime.currentWave - 1) / 2) + (participantCount - 1));
            for (int i = 0; i < amount; i++) {
                spawnPveMob(runtime, marker);
            }
        }
    }

    private void spawnPveMob(PveZoneRuntime runtime, PveSpawnMarker marker) {
        if (runtime == null || marker == null || marker.spawnLocation().getWorld() == null) return;
        Entity entity = marker.spawnLocation().getWorld().spawnEntity(marker.spawnLocation(), marker.entityType());
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            return;
        }
        living.setCustomName(ChatColor.RED + marker.displayName());
        living.setCustomNameVisible(true);
        living.addScoreboardTag("skycity_pve_zone");
        living.addScoreboardTag("skycity_pve_marker_" + marker.id());
        living.addScoreboardTag("skycity_pve_reward_" + marker.rewardLevels());
        runtime.mobLevels.put(living.getUniqueId(), marker.level());
        applyPveMobScaling(runtime, living, marker.level(), true);
        if (living instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
            Player target = runtime.participants.stream().map(Bukkit::getPlayer).filter(player -> player != null && player.isOnline()).findFirst().orElse(null);
            if (target != null) {
                mob.setTarget(target);
            }
        }
        runtime.activeMobIds.add(living.getUniqueId());
        runtime.mobHomes.put(living.getUniqueId(), marker.spawnLocation().clone());
        runtime.mobLabels.put(living.getUniqueId(), marker.displayName());
        pveMobZones.put(living.getUniqueId(), runtime.islandOwner + ":" + runtime.parcelKey);
    }

    private void tickPveZones() {
        for (PveZoneRuntime runtime : new ArrayList<>(activePveZones.values())) {
            ParcelData parcel = islands.get(runtime.islandOwner) == null ? null : islands.get(runtime.islandOwner).getParcels().get(runtime.parcelKey);
            IslandData island = islands.get(runtime.islandOwner);
            if (island == null || parcel == null || !parcel.isPveEnabled()) {
                resetPveZone(runtime, null);
                continue;
            }
            runtime.participants.removeIf(playerId -> {
                Player player = Bukkit.getPlayer(playerId);
                return player == null || !player.isOnline() || getIslandAt(player.getLocation()) != island || getParcelAt(island, player.getLocation()) != parcel;
            });
            if (runtime.participants.isEmpty()) {
                resetPveZone(runtime, null);
                continue;
            }
            for (UUID mobId : new ArrayList<>(runtime.activeMobIds)) {
                Entity entity = Bukkit.getEntity(mobId);
                if (!(entity instanceof Mob mob) || !mob.isValid()) {
                    runtime.activeMobIds.remove(mobId);
                    runtime.mobHomes.remove(mobId);
                    runtime.mobLabels.remove(mobId);
                    runtime.mobLevels.remove(mobId);
                    runtime.mobLastReachableAt.remove(mobId);
                    runtime.mobLastRangedHitAt.remove(mobId);
                    runtime.invalidMobIds.remove(mobId);
                    pveMobZones.remove(mobId);
                    continue;
                }
                Integer mobLevel = runtime.mobLevels.get(mobId);
                if (mobLevel != null) {
                    applyPveMobScaling(runtime, mob, mobLevel, false);
                }
                if (!runtime.contains(mob.getLocation()) || runtime.isInStartZone(mob.getLocation())) {
                    Location home = runtime.mobHomes.get(mobId);
                    if (home != null) {
                        mob.teleport(home);
                    }
                }
                Player nearest = runtime.participants.stream()
                        .map(Bukkit::getPlayer)
                        .filter(player -> player != null && player.isOnline())
                        .min((a, b) -> Double.compare(a.getLocation().distanceSquared(mob.getLocation()), b.getLocation().distanceSquared(mob.getLocation())))
                        .orElse(null);
                if (nearest != null) {
                    mob.setTarget(nearest);
                    boolean pathReachable = mob.getPathfinder().findPath(nearest) != null;
                    if (pathReachable) {
                        runtime.mobLastReachableAt.put(mobId, System.currentTimeMillis());
                        mob.getPathfinder().moveTo(nearest, 1.0);
                    } else {
                        mob.getPathfinder().stopPathfinding();
                    }
                    long now = System.currentTimeMillis();
                    boolean recentlyReachable = now - runtime.mobLastReachableAt.getOrDefault(mobId, 0L) <= 2500L;
                    boolean validRangedWindow = isRangedPveMob(mob.getType()) && now - runtime.mobLastRangedHitAt.getOrDefault(mobId, 0L) <= 4000L;
                    boolean valid = recentlyReachable || validRangedWindow;
                    updatePveMobValidity(runtime, mob, valid);
                }
            }
            if (runtime.activeMobIds.isEmpty() && runtime.currentWave <= 0) {
                startNextPveWave(runtime);
            }
        }
    }

    private void finishPveZone(PveZoneRuntime runtime) {
        if (runtime == null) return;
        for (UUID playerId : runtime.participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;
            int reward = runtime.pendingRewards.getOrDefault(playerId, 0);
            if (reward > 0) {
                player.giveExpLevels(reward);
            }
            player.sendMessage(ChatColor.GREEN + "PvE geschafft. Belohnung: " + reward + " Level.");
        }
        resetPveZone(runtime, ChatColor.GREEN + "PvE-Zone abgeschlossen.");
    }

    private void resetPveZone(IslandData island, ParcelData parcel, String message) {
        String key = getParcelPveKey(island, parcel);
        if (key == null) return;
        resetPveZone(activePveZones.get(key), message);
    }

    private void resetPveZone(PveZoneRuntime runtime, String message) {
        if (runtime == null) return;
        for (UUID mobId : new ArrayList<>(runtime.activeMobIds)) {
            Entity entity = Bukkit.getEntity(mobId);
            if (entity != null) entity.remove();
            pveMobZones.remove(mobId);
        }
        for (UUID playerId : new ArrayList<>(runtime.participants)) {
            playerPveZones.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && message != null && !message.isBlank()) {
                player.sendMessage(message);
            }
        }
        activePveZones.remove(runtime.islandOwner + ":" + runtime.parcelKey);
    }

    private void resetAllPveZones() {
        for (PveZoneRuntime runtime : new ArrayList<>(activePveZones.values())) {
            resetPveZone(runtime, null);
        }
        pendingPveRespawns.clear();
        playerPveZones.clear();
        pveMobZones.clear();
    }

    private void broadcastPveZone(PveZoneRuntime runtime, String message) {
        if (runtime == null || message == null) return;
        for (UUID playerId : runtime.participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    private int extractPveReward(LivingEntity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith("skycity_pve_reward_")) {
                try {
                    return Integer.parseInt(tag.substring("skycity_pve_reward_".length()));
                } catch (NumberFormatException ignored) {
                    return 1;
                }
            }
        }
        return 1;
    }

    private boolean isRangedPveMob(EntityType type) {
        return type == EntityType.SKELETON || type == EntityType.DROWNED;
    }

    private void applyPveMobScaling(PveZoneRuntime runtime, LivingEntity living, int level, boolean fillHealth) {
        if (runtime == null || living == null) return;
        double playerScale = 1.0 + Math.max(0, runtime.participants.size() - 1) * 0.35;
        double areaScale = 1.0 + computePveAreaTier(runtime.floorArea) * 0.25;
        double waveScale = 1.0 + Math.max(0, runtime.currentWave - 1) * 0.12;
        double maxHealth = (10.0 + level * 6.0) * playerScale * areaScale * waveScale;
        if (living.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            double previousMax = living.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
            living.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
            if (fillHealth || previousMax <= 0.0) {
                living.setHealth(Math.min(maxHealth, maxHealth));
            } else {
                double scaledHealth = living.getHealth() * (maxHealth / previousMax);
                living.setHealth(Math.max(1.0, Math.min(maxHealth, scaledHealth)));
            }
        }
        if (living.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            double attackDamage = (2.0 + level * 1.75) * playerScale * areaScale * (1.0 + Math.max(0, runtime.currentWave - 1) * 0.08);
            living.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(attackDamage);
        }
    }

    private static int computePveAreaTier(int floorArea) {
        if (floorArea >= 1200) return 4;
        if (floorArea >= 700) return 3;
        if (floorArea >= 350) return 2;
        if (floorArea >= 160) return 1;
        return 0;
    }

    private static int computePveRequiredWaves(int floorArea) {
        return switch (computePveAreaTier(floorArea)) {
            case 4 -> 7;
            case 3 -> 6;
            case 2 -> 5;
            case 1 -> 4;
            default -> 3;
        };
    }

    private void updatePveMobValidity(PveZoneRuntime runtime, Mob mob, boolean valid) {
        if (runtime == null || mob == null) return;
        String label = runtime.mobLabels.getOrDefault(mob.getUniqueId(), ChatColor.stripColor(mob.getCustomName() == null ? mob.getType().name() : mob.getCustomName()));
        if (valid) {
            runtime.invalidMobIds.remove(mob.getUniqueId());
            mob.setCustomName(ChatColor.RED + label);
        } else {
            runtime.invalidMobIds.add(mob.getUniqueId());
            mob.setCustomName(ChatColor.DARK_GRAY + "[ungueltig] " + ChatColor.RED + label);
        }
        mob.setCustomNameVisible(true);
    }

    public void onTrackedBlockPlaced(IslandData island, Block block) {
        if (island == null || block == null) return;
        Material type = block.getType();
        if (CACTUS_FAMILY.contains(type)) {
            if (isRootedPlantBase(block, CACTUS_FAMILY)) {
                adjustTrackedCount(island, type, 1);
            }
            return;
        }
        if (KELP_FAMILY.contains(type)) {
            if (isRootedPlantBase(block, KELP_FAMILY)) {
                adjustTrackedCount(island, Material.KELP, 1);
            }
            return;
        }
        if (BAMBOO_FAMILY.contains(type)) {
            if (isRootedPlantBase(block, BAMBOO_FAMILY)) {
                adjustTrackedCount(island, Material.BAMBOO, 1);
            }
            return;
        }
        adjustTrackedCount(island, type, 1);
    }

    public void onTrackedBlockBroken(IslandData island, Block block) {
        if (island == null || block == null) return;
        Material type = block.getType();
        if (CACTUS_FAMILY.contains(type)) {
            if (isRootedPlantBase(block, CACTUS_FAMILY)) {
                adjustTrackedCount(island, type, -1);
            }
            return;
        }
        if (KELP_FAMILY.contains(type)) {
            if (isRootedPlantBase(block, KELP_FAMILY)) {
                adjustTrackedCount(island, Material.KELP, -1);
            }
            return;
        }
        if (BAMBOO_FAMILY.contains(type)) {
            if (isRootedPlantBase(block, BAMBOO_FAMILY)) {
                adjustTrackedCount(island, Material.BAMBOO, -1);
            }
            return;
        }
        adjustTrackedCount(island, type, -1);
    }

    private void adjustTrackedCount(IslandData island, Material type, int delta) {
        if (isInventoryLimitedMaterial(type)) {
            island.getCachedBlockCounts().merge(CACHE_INV, delta, Integer::sum);
            clampZero(island.getCachedBlockCounts(), CACHE_INV);
        }
        if (type == Material.HOPPER) {
            island.getCachedBlockCounts().merge(CACHE_HOPPER, delta, Integer::sum);
            clampZero(island.getCachedBlockCounts(), CACHE_HOPPER);
        }
        if (type == Material.PISTON || type == Material.STICKY_PISTON) {
            island.getCachedBlockCounts().merge(CACHE_PISTON, delta, Integer::sum);
            clampZero(island.getCachedBlockCounts(), CACHE_PISTON);
        }
        if (type == Material.OBSERVER) {
            island.getCachedBlockCounts().merge(CACHE_OBSERVER, delta, Integer::sum);
            clampZero(island.getCachedBlockCounts(), CACHE_OBSERVER);
        }
        if (type == Material.DISPENSER) {
            island.getCachedBlockCounts().merge(CACHE_DISPENSER, delta, Integer::sum);
            clampZero(island.getCachedBlockCounts(), CACHE_DISPENSER);
        }
        if (type == Material.CACTUS) {
            island.getCachedBlockCounts().merge(CACHE_CACTUS, delta, Integer::sum);
            clampZero(island.getCachedBlockCounts(), CACHE_CACTUS);
        }
        if (type == Material.KELP || type == Material.KELP_PLANT) {
            island.getCachedBlockCounts().merge(CACHE_KELP, delta, Integer::sum);
            clampZero(island.getCachedBlockCounts(), CACHE_KELP);
        }
        if (type == Material.BAMBOO) {
            island.getCachedBlockCounts().merge(CACHE_BAMBOO, delta, Integer::sum);
            clampZero(island.getCachedBlockCounts(), CACHE_BAMBOO);
        }
    }

    private void clampZero(Map<String, Integer> map, String key) {
        if (map.getOrDefault(key, 0) < 0) map.put(key, 0);
    }

    public void rebuildPlacementCaches(IslandData island) {
        island.getCachedBlockCounts().put(CACHE_INV, scanCount(island, this::isInventoryLimitedMaterial));
        island.getCachedBlockCounts().put(CACHE_HOPPER, scanCount(island, m -> m == Material.HOPPER));
        island.getCachedBlockCounts().put(CACHE_PISTON, scanCount(island, m -> m == Material.PISTON || m == Material.STICKY_PISTON));
        island.getCachedBlockCounts().put(CACHE_OBSERVER, scanCount(island, m -> m == Material.OBSERVER));
        island.getCachedBlockCounts().put(CACHE_DISPENSER, scanCount(island, m -> m == Material.DISPENSER));
        island.getCachedBlockCounts().put(CACHE_CACTUS, scanRootedPlantCount(island, CACTUS_FAMILY));
        island.getCachedBlockCounts().put(CACHE_KELP, scanRootedPlantCount(island, KELP_FAMILY));
        island.getCachedBlockCounts().put(CACHE_BAMBOO, scanRootedPlantCount(island, BAMBOO_FAMILY));
    }

    private int scanCount(IslandData island, Predicate<Material> predicate) {
        World world = skyWorldService.getWorld();
        int count = 0;
        for (int cx = 0; cx < ISLAND_CHUNKS; cx++) for (int cz = 0; cz < ISLAND_CHUNKS; cz++) {
            if (!island.getUnlockedChunks().contains(chunkKey(cx, cz))) continue;
            Chunk chunk = world.getChunkAt(plotMinChunkX(island.getGridX()) + cx, plotMinChunkZ(island.getGridZ()) + cz);
            for (int y = world.getMinHeight(); y < Math.min(world.getMaxHeight(), 256); y++) {
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) if (predicate.test(chunk.getBlock(x, y, z).getType())) count++;
            }
        }
        return count;
    }

    private int scanRootedPlantCount(IslandData island, Set<Material> family) {
        World world = skyWorldService.getWorld();
        int count = 0;
        for (int cx = 0; cx < ISLAND_CHUNKS; cx++) for (int cz = 0; cz < ISLAND_CHUNKS; cz++) {
            if (!island.getUnlockedChunks().contains(chunkKey(cx, cz))) continue;
            Chunk chunk = world.getChunkAt(plotMinChunkX(island.getGridX()) + cx, plotMinChunkZ(island.getGridZ()) + cz);
            for (int y = world.getMinHeight(); y < Math.min(world.getMaxHeight(), 256); y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        Block block = chunk.getBlock(x, y, z);
                        if (!family.contains(block.getType())) continue;
                        if (isRootedPlantBase(block, family)) count++;
                    }
                }
            }
        }
        return count;
    }

    private boolean isRootedPlantBase(Block block, Set<Material> family) {
        if (block == null || family == null || family.isEmpty()) return false;
        if (!family.contains(block.getType())) return false;
        return !family.contains(block.getRelative(0, -1, 0).getType());
    }

    public boolean isInventoryLimitedMaterial(Material m) {
        if (m == null) return false;
        return switch (m) {
            case CHEST, TRAPPED_CHEST, BARREL, FURNACE, BLAST_FURNACE, SMOKER, DROPPER, DISPENSER, BREWING_STAND,
                    SHULKER_BOX, WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX, MAGENTA_SHULKER_BOX, LIGHT_BLUE_SHULKER_BOX,
                    YELLOW_SHULKER_BOX, LIME_SHULKER_BOX, PINK_SHULKER_BOX, GRAY_SHULKER_BOX, LIGHT_GRAY_SHULKER_BOX,
                    CYAN_SHULKER_BOX, PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX, BROWN_SHULKER_BOX, GREEN_SHULKER_BOX,
                    RED_SHULKER_BOX, BLACK_SHULKER_BOX -> true;
            default -> false;
        };
    }

    public List<Entity> getEntitiesInIsland(IslandData island) {
        World world = skyWorldService.getWorld();
        int minX = plotMinChunkX(island.getGridX()) << 4;
        int minZ = plotMinChunkZ(island.getGridZ()) << 4;
        int maxX = minX + ISLAND_CHUNKS * 16 - 1;
        int maxZ = minZ + ISLAND_CHUNKS * 16 - 1;
        List<Entity> out = new ArrayList<>();
        for (Entity e : world.getEntities()) {
            Location l = e.getLocation();
            if (l.getX() >= minX && l.getX() <= maxX && l.getZ() >= minZ && l.getZ() <= maxZ) out.add(e);
        }
        return out;
    }

    public Optional<IslandData> findIslandByCoreLocation(Location location) {
        return islands.values().stream()
                .filter(i -> i.getCoreLocation() != null)
                .filter(i -> sameBlock(i.getCoreLocation(), location))
                .findFirst();
    }

    private boolean sameBlock(Location a, Location b) {
        return a != null && b != null && a.getWorld() != null && b.getWorld() != null && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    private IslandPlot findNextFreePlot() {
        Set<String> used = new HashSet<>();
        used.add("0:0");
        for (IslandData island : islands.values()) used.add(island.getGridX() + ":" + island.getGridZ());
        int x = 0, z = 0, dx = 1, dz = 0, segmentLength = 1, segmentPassed = 0, segmentChanges = 0;
        for (int i = 0; i < 50000; i++) {
            x += dx;
            z += dz;
            String key = x + ":" + z;
            if (!used.contains(key)) return new IslandPlot(x, z);
            segmentPassed++;
            if (segmentPassed == segmentLength) {
                segmentPassed = 0;
                int oldDx = dx;
                dx = -dz;
                dz = oldDx;
                segmentChanges++;
                if (segmentChanges % 2 == 0) segmentLength++;
            }
        }
        throw new IllegalStateException("Keine freie Inselposition gefunden");
    }

    public String chunkKey(int x, int z) { return x + ":" + z; }

    private void ensureTemplateFile() {
        if (templateFile.exists()) return;
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            YamlConfiguration yaml = new YamlConfiguration();
            writeTemplate(yaml, "templates.0", "plains", "GRASS_BLOCK", "STONE", "OAK_SAPLING", "PLAINS", 2, 4);
            writeTemplate(yaml, "templates.1", "desert", "SAND", "SANDSTONE", "CACTUS", "DESERT", 2, 4);
            writeTemplate(yaml, "templates.2", "forest", "MOSS_BLOCK", "STONE", "OAK_SAPLING", "FOREST", 2, 4);
            writeTemplate(yaml, "templates.3", "snow", "SNOW_BLOCK", "STONE", "SNOW", "SNOWY_PLAINS", 2, 4);
            writeTemplate(yaml, "templates.4", "mushroom", "MYCELIUM", "DIRT", "RED_MUSHROOM", "MUSHROOM_FIELDS", 2, 3);
            writeTemplate(yaml, "templates.5", "savanna", "COARSE_DIRT", "STONE", "OAK_SAPLING", "SAVANNA", 2, 4);
            writeTemplate(yaml, "templates.6", "rock", "STONE", "COBBLESTONE", "COBBLESTONE", "WINDSWEPT_HILLS", 2, 3);
            writeTemplate(yaml, "templates.7", "deep", "DEEPSLATE", "COBBLED_DEEPSLATE", "COBBLESTONE", "STONY_PEAKS", 2, 3);
            yaml.save(templateFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Konnte chunk-templates.yml nicht erstellen: " + ex.getMessage());
        }
    }

    private void writeTemplate(YamlConfiguration yaml, String path, String id, String top, String filler, String feature, String biome, int minR, int maxR) {
        yaml.set(path + ".id", id);
        yaml.set(path + ".top", top);
        yaml.set(path + ".filler", filler);
        yaml.set(path + ".feature", feature);
        yaml.set(path + ".biome", biome);
        yaml.set(path + ".minRadius", minR);
        yaml.set(path + ".maxRadius", maxR);
    }

    private void loadTemplates() {
        templates.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(templateFile);
        ConfigurationSection root = yaml.getConfigurationSection("templates");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) continue;
            try {
                Material top = Material.valueOf(sec.getString("top", "GRASS_BLOCK").toUpperCase(Locale.ROOT));
                Material filler = Material.valueOf(sec.getString("filler", "STONE").toUpperCase(Locale.ROOT));
                Material feature = Material.valueOf(sec.getString("feature", "AIR").toUpperCase(Locale.ROOT));
                Biome biome = Biome.valueOf(sec.getString("biome", "PLAINS").toUpperCase(Locale.ROOT));
                int minR = Math.max(1, sec.getInt("minRadius", 2));
                int maxR = Math.max(minR, sec.getInt("maxRadius", 4));
                templates.add(new ChunkTemplateDef(sec.getString("id", key), top, filler, feature, biome, minR, maxR));
            } catch (Exception ex) {
                plugin.getLogger().warning("Template ignoriert (" + key + "): " + ex.getMessage());
            }
        }
    }

    private List<String> stringify(Collection<UUID> uuids) {
        List<String> out = new ArrayList<>();
        for (UUID id : uuids) out.add(id.toString());
        return out;
    }

    private void saveAccessSettings(YamlConfiguration yaml, String path, AccessSettings settings) {
        yaml.set(path + ".doors", settings.isDoors());
        yaml.set(path + ".trapdoors", settings.isTrapdoors());
        yaml.set(path + ".fenceGates", settings.isFenceGates());
        yaml.set(path + ".buttons", settings.isButtons());
        yaml.set(path + ".levers", settings.isLevers());
        yaml.set(path + ".pressurePlates", settings.isPressurePlates());
        yaml.set(path + ".containers", settings.isContainers());
        yaml.set(path + ".farmUse", settings.isFarmUse());
        yaml.set(path + ".ride", settings.isRide());
        yaml.set(path + ".redstoneUse", settings.isRedstoneUse());
        yaml.set(path + ".ladderPlace", settings.isLadderPlace());
        yaml.set(path + ".ladderBreak", settings.isLadderBreak());
        yaml.set(path + ".leavesPlace", settings.isLeavesPlace());
        yaml.set(path + ".leavesBreak", settings.isLeavesBreak());
        yaml.set(path + ".teleport", settings.isTeleport());
    }

    private void loadAccessSettings(ConfigurationSection sec, AccessSettings settings) {
        if (sec == null) return;
        boolean oldDoors = sec.getBoolean("doors", settings.isDoors());
        settings.setDoors(oldDoors);
        settings.setTrapdoors(sec.getBoolean("trapdoors", oldDoors));
        settings.setFenceGates(sec.getBoolean("fenceGates", oldDoors));
        settings.setButtons(sec.getBoolean("buttons", sec.getBoolean("redstoneUse", settings.isButtons())));
        settings.setLevers(sec.getBoolean("levers", sec.getBoolean("redstoneUse", settings.isLevers())));
        settings.setPressurePlates(sec.getBoolean("pressurePlates", sec.getBoolean("redstoneUse", settings.isPressurePlates())));
        settings.setContainers(sec.getBoolean("containers", settings.isContainers()));
        settings.setFarmUse(sec.getBoolean("farmUse", settings.isFarmUse()));
        settings.setRide(sec.getBoolean("ride", settings.isRide()));
        settings.setRedstoneUse(sec.getBoolean("redstoneUse", settings.isRedstoneUse()));
        settings.setLadderPlace(sec.getBoolean("ladderPlace", settings.isLadderPlace()));
        settings.setLadderBreak(sec.getBoolean("ladderBreak", settings.isLadderBreak()));
        settings.setLeavesPlace(sec.getBoolean("leavesPlace", settings.isLeavesPlace()));
        settings.setLeavesBreak(sec.getBoolean("leavesBreak", settings.isLeavesBreak()));
        settings.setTeleport(sec.getBoolean("teleport", settings.isTeleport()));
    }

    private void serializeLocation(YamlConfiguration yaml, String path, Location loc) {
        yaml.set(path + ".world", loc.getWorld().getName());
        yaml.set(path + ".x", loc.getX());
        yaml.set(path + ".y", loc.getY());
        yaml.set(path + ".z", loc.getZ());
        yaml.set(path + ".yaw", loc.getYaw());
        yaml.set(path + ".pitch", loc.getPitch());
    }

    private Location deserializeLocation(ConfigurationSection sec) {
        World w = Bukkit.getWorld(sec.getString("world", SkyWorldService.WORLD_NAME));
        if (w == null) w = skyWorldService.getWorld();
        return new Location(w, sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"),
                (float) sec.getDouble("yaw"), (float) sec.getDouble("pitch"));
    }
}




