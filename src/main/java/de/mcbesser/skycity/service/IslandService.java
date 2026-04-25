package de.mcbesser.skycity.service;

import de.mcbesser.skycity.SkyCityPlugin;
import de.mcbesser.skycity.model.AccessSettings;
import de.mcbesser.skycity.model.IslandData;
import de.mcbesser.skycity.model.IslandLevelDefinition;
import de.mcbesser.skycity.model.IslandPlot;
import de.mcbesser.skycity.model.ParcelData;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.common.Constants;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.mvstore.MVStoreModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.block.Beehive;
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
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class IslandService {
    public enum TrustPermission { BUILD, CONTAINER, REDSTONE, ALL }
    public enum ParcelRole { OWNER, MEMBER, PVP }
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
    public enum IslandWeatherMode {
        NORMAL,
        CLEAR,
        RAIN,
        THUNDER;

        public static IslandWeatherMode from(String raw) {
            if (raw == null || raw.isBlank()) return NORMAL;
            String normalized = raw.toUpperCase(Locale.ROOT);
            if ("SUN".equals(normalized) || "SUNSHINE".equals(normalized) || "SONNE".equals(normalized)) return CLEAR;
            if ("STORM".equals(normalized) || "GEWITTER".equals(normalized)) return THUNDER;
            try {
                return IslandWeatherMode.valueOf(normalized);
            } catch (IllegalArgumentException ex) {
                return NORMAL;
            }
        }
    }
    public enum SnowWeatherMode {
        NORMAL,
        ALLOW,
        BLOCK;

        public static SnowWeatherMode from(String raw) {
            if (raw == null || raw.isBlank()) return NORMAL;
            String normalized = raw.toUpperCase(Locale.ROOT);
            if ("SNOW".equals(normalized) || "ALLOW_SNOW".equals(normalized)) return ALLOW;
            if ("NO_SNOW".equals(normalized) || "CLEAR_SNOW".equals(normalized) || "SCHNEEFREI".equals(normalized)) return BLOCK;
            try {
                return SnowWeatherMode.valueOf(normalized);
            } catch (IllegalArgumentException ex) {
                return NORMAL;
            }
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
    public enum UpgradeBranch {
        ANIMAL("animal", "Tierlimit", Material.HAY_BLOCK, 12, 4, 20),
        CHUNKS("chunks", "Chunkpakete", Material.MAP, 0, 0, 264),
        GOLEM("golem", "Golemlimit", Material.CARVED_PUMPKIN, 2, 1, 18),
        VILLAGER("villager", "Villagerlimit", Material.EMERALD, 2, 1, 18),
        CONTAINER("container", "Beh\u00e4lter", Material.BARREL, 100, 25, 12),
        HOPPER("hopper", "Trichter", Material.HOPPER, 8, 4, 24),
        PISTON("piston", "Kolben", Material.PISTON, 16, 4, 24),
        ARMOR_STAND("armor_stand", "R\u00fcstungsst\u00e4nder", Material.ARMOR_STAND, 4, 2, 14),
        MINECART("minecart", "Minecartlimit", Material.MINECART, 8, 4, 9),
        BOAT("boat", "Bootlimit", Material.OAK_BOAT, 6, 3, 20),
        OBSERVER("observer", "Observer", Material.OBSERVER, 12, 6, 20),
        DISPENSER("dispenser", "Dispenser", Material.DISPENSER, 8, 4, 20),
        CACTUS("cactus", "Kaktus", Material.CACTUS, 32, 32, 24),
        KELP("kelp", "Kelp", Material.KELP, 64, 48, 24),
        BAMBOO("bamboo", "Bambus", Material.BAMBOO, 64, 48, 24);

        private final String key;
        private final String displayName;
        private final Material icon;
        private final int baseLimit;
        private final int step;
        private final int maxTier;

        UpgradeBranch(String key, String displayName, Material icon, int baseLimit, int step, int maxTier) {
            this.key = key;
            this.displayName = displayName;
            this.icon = icon;
            this.baseLimit = baseLimit;
            this.step = step;
            this.maxTier = maxTier;
        }

        public String key() { return key; }
        public String displayName() { return displayName; }
        public Material icon() { return icon; }
        public int baseLimit() { return baseLimit; }
        public int step() { return step; }
        public int maxTier() { return maxTier; }

        public static UpgradeBranch fromKey(String raw) {
            if (raw == null || raw.isBlank()) return ANIMAL;
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            for (UpgradeBranch branch : values()) {
                if (branch.name().equals(normalized) || branch.key.equalsIgnoreCase(raw)) {
                    return branch;
                }
            }
            return ANIMAL;
        }
    }
    public record UpgradeRequirement(long islandLevel, long experience, Map<Material, Integer> materials, int chunkUnlocksGranted) { }
    public record MilestoneRequirement(int milestone, long islandLevel, long experience, Map<Material, Integer> materials, int chunkUnlocksGranted) { }
    public record TeleportTarget(String id, String displayName, Location location, boolean parcel) { }
    public record IslandLoadBreakdown(int totalPercent, int blockPercent, int entityPercent) { }
    private record ChunkTemplateDef(String id, Material top, Material filler, Material feature, Biome biome, int minRadius, int maxRadius) { }
    private record ThemeHit(long seed, double density) { }
    public record PregenerationTask(UUID islandOwner, int nextIndex) { }
    private record IslandCreationTask(UUID playerId) { }
    public record BarrierFloorRepairTask(int gridX, int gridZ, int nextChunkIndex) { }
    private record PveMobArchetype(
            String key,
            EntityType entityType,
            String singularName,
            String pluralName,
            int level,
            int rewardLevels,
            ChatColor nameColor,
            double movementSpeed,
            double healthMultiplier,
            double damageMultiplier,
            Color helmetColor,
            Color chestColor,
            Color legColor,
            Color bootColor
    ) { }
    private record PveSpawnMarker(String id, Location markerLocation, Location spawnLocation, String familyName, List<PveMobArchetype> archetypes, int level, int rewardLevels) { }
    public record PveRuntimeSnapshot(
            String zoneKey,
            String parcelName,
            int currentWave,
            int requiredWaves,
            int activeMobCount,
            int participantCount,
            int pendingRewardLevels,
            String objectiveText,
            Map<String, String> spawnEntries
    ) { }
    private enum PveObjectiveMode { KILL_ALL_OF_TYPE, SPARE_TYPE }
    private static final long PVE_SPARE_SURVIVAL_MS = 15000L;
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
        private final Map<UUID, PveMobArchetype> mobArchetypes = new HashMap<>();
        private final Map<UUID, Integer> mobLevels = new HashMap<>();
        private final Map<UUID, Integer> pendingRewards = new HashMap<>();
        private final Map<UUID, Long> mobLastReachableAt = new HashMap<>();
        private final Map<UUID, Long> mobLastRangedHitAt = new HashMap<>();
        private final Set<UUID> invalidMobIds = new HashSet<>();
        private int currentWave = 0;
        private final int requiredWaves;
        private PveObjectiveMode objectiveMode = PveObjectiveMode.KILL_ALL_OF_TYPE;
        private String objectiveArchetypeKey = "zombie_opa";
        private String objectiveSingularName = "Opa";
        private String objectivePluralName = "Opas";
        private long spareSurvivalDeadline = 0L;

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

        private String objectiveText() {
            String base = switch (objectiveMode) {
                case KILL_ALL_OF_TYPE -> "T\u00f6te alle " + objectivePluralName;
                case SPARE_TYPE -> "T\u00f6te keine " + objectivePluralName;
            };
            if (objectiveMode != PveObjectiveMode.SPARE_TYPE || spareSurvivalDeadline <= 0L) {
                return base;
            }
            long remainingMs = Math.max(0L, spareSurvivalDeadline - System.currentTimeMillis());
            long remainingSeconds = (remainingMs + 999L) / 1000L;
            return base + " [" + remainingSeconds + "s \u00fcberleben]";
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
    private static final class IslandCreationThrottleState {
        private int requestCount;
        private long lastRequestAt;
        private long cooldownUntil;

        private IslandCreationThrottleState(int requestCount, long lastRequestAt, long cooldownUntil) {
            this.requestCount = requestCount;
            this.lastRequestAt = lastRequestAt;
            this.cooldownUntil = cooldownUntil;
        }
    }
    public record IslandAreaCleanupTask(UUID islandOwner, int gridX, int gridZ, int nextChunkIndex) { }

    private static final int ISLAND_CHUNKS = 64;
    private static final int TOTAL_CHUNKS = 4096;
    private static final UUID SPAWN_ISLAND_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final long ONE_YEAR_MS = 365L * 24L * 60L * 60L * 1000L;
    private static final long ONE_DAY_MS = 24L * 60L * 60L * 1000L;
    private static final long INACTIVITY_WARNING_30_DAYS_MS = 30L * ONE_DAY_MS;
    private static final long INACTIVITY_WARNING_7_DAYS_MS = 7L * ONE_DAY_MS;
    private static final long INACTIVITY_WARNING_1_DAY_MS = ONE_DAY_MS;
    private static final long CREATION_REQUEST_RESET_WINDOW_MS = 7L * ONE_DAY_MS;
    private static final long BIOME_CHANGE_COST_CHUNK = 120L;
    private static final long BIOME_CHANGE_COST_ISLAND = 3000L;
    private static final long TIME_MODE_CHANGE_COST = 180L;
    private static final long WEATHER_MODE_CHANGE_COST = 180L;
    private static final long NIGHT_VISION_COST_CHUNK = 220L;
    private static final long NIGHT_VISION_COST_ISLAND = 2400L;
    private static final long XP_BOTTLE_POINTS = 10L;
    private static final long XP_BOTTLE_COST_POINTS = 11L; // 10% loss
    private static final long GROWTH_DEBUG_WINDOW_MILLIS = 5_000L;
    private static final int BARRIER_FLOOR_REPAIR_CHUNKS_PER_TICK = 1;
    private static final int TEXT_DISPLAY_LOAD_LIMIT = 32;
    private static final int INTERACTIVE_ITEM_DISPLAY_LOAD_LIMIT = 24;
    private static final int DISPLAY_HITBOX_LOAD_LIMIT = 32;
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
    private final Set<UUID> activeGrowthBoostIslands = new HashSet<>();
    private final Map<String, Long> growthDebugWindowStart = new HashMap<>();
    private final Map<String, Integer> growthDebugAttempts = new HashMap<>();
    private final Map<String, Integer> growthDebugHits = new HashMap<>();
    private final Map<String, Integer> growthDebugBridgeFailures = new HashMap<>();
    private final Map<String, Integer> growthDebugTargets = new HashMap<>();
    private final Map<String, String> growthDebugTargetSummary = new HashMap<>();
    private final Map<Integer, IslandLevelDefinition> levelDefinitions = IslandLevelDefinition.defaults();
    private final File legacyDataFile;
    private final File databaseFile;
    private final File templateFile;
    private final Nitrite database;
    private final NitriteCollection islandCollection;
    private final NitriteCollection cleanupCollection;
    private final List<ChunkTemplateDef> templates = new ArrayList<>();
    private final Queue<PregenerationTask> pregenerationQueue = new ArrayDeque<>();
    private final Set<UUID> queuedPregenerationOwners = new HashSet<>();
    private final Map<UUID, Integer> pregenerationProgressByOwner = new HashMap<>();
    private final Queue<IslandCreationTask> islandCreationQueue = new ArrayDeque<>();
    private final Queue<BarrierFloorRepairTask> barrierFloorRepairQueue = new ArrayDeque<>();
    private final Set<String> queuedBarrierFloorRepairPlots = new HashSet<>();
    private final Map<UUID, List<Consumer<IslandData>>> islandCreationCallbacks = new HashMap<>();
    private final Map<UUID, List<Consumer<IslandData>>> islandReadyCallbacks = new HashMap<>();
    private final Set<UUID> pendingIslandCreations = new HashSet<>();
    private final Map<UUID, IslandPlot> requestedIslandPlots = new HashMap<>();
    private final Set<String> reservedCreationPlots = new HashSet<>();
    private final Set<UUID> recreationCooldownOwners = new HashSet<>();
    private final Map<UUID, IslandCreationThrottleState> islandCreationThrottleStates = new HashMap<>();
    public static class MasterInvite {
        public final UUID primaryOwner;
        public final long expiresAt;
        public int taskId = -1;

        public MasterInvite(UUID primaryOwner, long expiresAt) {
            this.primaryOwner = primaryOwner;
            this.expiresAt = expiresAt;
        }
    }
    private final Map<UUID, MasterInvite> pendingMasterInvites = new HashMap<>();
    private final Map<UUID, Location> plotSelectionPos1 = new HashMap<>();
    private final Map<UUID, Location> plotSelectionPos2 = new HashMap<>();
    private final Map<String, PendingBorderUnlockRequest> pendingBorderUnlockRequests = new HashMap<>();
    private final Map<UUID, String> parcelPvpConsents = new HashMap<>();
    private final Map<String, PveZoneRuntime> activePveZones = new HashMap<>();
    private final Map<UUID, String> playerPveZones = new HashMap<>();
    private final Map<UUID, Location> pendingPveRespawns = new HashMap<>();
    private final Map<UUID, String> pveMobZones = new HashMap<>();
    private final Queue<IslandAreaCleanupTask> islandAreaCleanupQueue = new ArrayDeque<>();
    private final Set<UUID> queuedIslandAreaCleanupOwners = new HashSet<>();
    private final Set<String> reservedCleanupPlots = new HashSet<>();
    private final Map<String, Integer> cleanupProgressByPlot = new HashMap<>();
    private final NamespacedKey plotWandKey;
    private int pregenerationTaskId = -1;
    private int islandCreationTaskId = -1;
    private int cleanupTaskId = -1;
    private int islandAreaCleanupTaskId = -1;
    private int pveTaskId = -1;

    public IslandService(SkyCityPlugin plugin, SkyWorldService skyWorldService) {
        this.plugin = plugin;
        this.skyWorldService = skyWorldService;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.legacyDataFile = new File(plugin.getDataFolder(), "islands.yml");
        this.databaseFile = new File(plugin.getDataFolder(), "skycity-data.db");
        this.templateFile = new File(plugin.getDataFolder(), "chunk-templates.yml");
        MVStoreModule storeModule = MVStoreModule.withConfig()
                .filePath(databaseFile.getAbsolutePath())
                .build();
        this.database = Nitrite.builder()
                .loadModule(storeModule)
                .openOrCreate();
        this.islandCollection = database.getCollection("islands");
        this.cleanupCollection = database.getCollection("island_cleanup");
        this.plotWandKey = new NamespacedKey(plugin, "plot_wand");
        ensureTemplateFile();
        loadTemplates();
    }

    public void load() {
        islands.clear();
        islandAreaCleanupQueue.clear();
        queuedIslandAreaCleanupOwners.clear();
        reservedCleanupPlots.clear();
        cleanupProgressByPlot.clear();
        pregenerationProgressByOwner.clear();
        islandCreationThrottleStates.clear();
        if (islandCollection.size() == 0 && legacyDataFile.exists()) {
            loadLegacyYamlData();
            save();
            backupLegacyYaml();
            return;
        }
        loadNitriteData();
        loadCleanupReservations();
        ensureSpawnIsland();
    }

    private void loadLegacyYamlData() {
        if (!legacyDataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacyDataFile);
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
                island.setPinnedUpgradeKey(sec.getString("pinnedUpgradeKey", "MILESTONE"));
                island.setIslandTimeMode(sec.getString("islandTimeMode", "NORMAL"));
                island.setIslandWeatherMode(sec.getString("islandWeatherMode", "NORMAL"));
                island.setIslandSnowMode(sec.getString("islandSnowMode", "NORMAL"));
                island.setIslandNightVisionEnabled(sec.getBoolean("islandNightVisionEnabled", false));
                island.setPoints(sec.getLong("points", 0L));
                island.setStoredExperience(sec.getLong("storedExperience", 0L));
                island.setLastActiveAt(sec.getLong("lastActiveAt", 0L));
                if (sec.isConfigurationSection("spawn")) island.setIslandSpawn(deserializeLocation(sec.getConfigurationSection("spawn")));
                if (sec.isConfigurationSection("warp")) island.setWarpLocation(deserializeLocation(sec.getConfigurationSection("warp")));
                if (sec.isConfigurationSection("core")) island.setCoreLocation(deserializeLocation(sec.getConfigurationSection("core")));
                for (String s : sec.getStringList("memberBuildAccess")) island.getMemberBuildAccess().add(UUID.fromString(s));
                for (String s : sec.getStringList("trusted")) island.getMemberBuildAccess().add(UUID.fromString(s));
                for (String s : sec.getStringList("memberContainerAccess")) island.getMemberContainerAccess().add(UUID.fromString(s));
                for (String s : sec.getStringList("trustedContainers")) island.getMemberContainerAccess().add(UUID.fromString(s));
                for (String s : sec.getStringList("memberRedstoneAccess")) island.getMemberRedstoneAccess().add(UUID.fromString(s));
                for (String s : sec.getStringList("trustedRedstone")) island.getMemberRedstoneAccess().add(UUID.fromString(s));
                for (String s : sec.getStringList("masters")) island.getMasters().add(UUID.fromString(s));
                for (String s : sec.getStringList("coOwners")) island.getMasters().add(UUID.fromString(s));
                for (String s : sec.getStringList("owners")) island.getOwners().add(UUID.fromString(s));
                for (String s : sec.getStringList("islandOwners")) island.getOwners().add(UUID.fromString(s));
                for (String s : sec.getStringList("islandBanned")) island.getIslandBanned().add(UUID.fromString(s));
                island.getUnlockedChunks().addAll(sec.getStringList("unlockedChunks"));
                island.getGeneratedChunks().addAll(sec.getStringList("generatedChunks"));
                loadAccessSettings(sec.getConfigurationSection("visitorSettings"), island.getIslandVisitorSettings());
                ConfigurationSection progress = sec.getConfigurationSection("progress");
                if (progress != null) for (String mk : progress.getKeys(false)) island.getProgress().put(mk, progress.getInt(mk));
                ConfigurationSection upgradeTiers = sec.getConfigurationSection("upgradeTiers");
                if (upgradeTiers != null) for (String uk : upgradeTiers.getKeys(false)) island.getUpgradeTiers().put(uk.toUpperCase(Locale.ROOT), Math.max(0, upgradeTiers.getInt(uk)));
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
                island.getNightVisionChunks().addAll(sec.getStringList("nightVisionChunks"));
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
                        applyParcelCombatMode(
                                parcel,
                                psec.getString("combatMode", null),
                psec.getBoolean("pvpEnabled", false),
                psec.getBoolean("gamesEnabled", false)
        );
        parcel.setCtfEnabled(psec.getBoolean("ctfEnabled", false));
        parcel.setSnowballFightEnabled(psec.getBoolean("snowballFightEnabled", false));
        parcel.setTimeMode(psec.getString("timeMode", "NORMAL"));
        parcel.setWeatherMode(psec.getString("weatherMode", "NORMAL"));
        parcel.setSnowMode(psec.getString("snowMode", "NORMAL"));
        parcel.setNightVisionEnabled(psec.getBoolean("nightVisionEnabled", false));
        parcel.setCountdownDurationSeconds(psec.getInt("countdownDurationSeconds", 300));
        parcel.setCountdownStartAt(psec.getLong("countdownStartAt", 0L));
        parcel.setCountdownEndsAt(psec.getLong("countdownEndsAt", 0L));
        parcel.setPvpCompassEnabled(psec.getBoolean("pvpCompassEnabled", true));
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
        finalizeLoadedIslands();
    }

    private void loadNitriteData() {
        for (Document document : islandCollection.find()) {
            try {
                IslandData island = fromDocument(document);
                islands.put(island.getOwner(), island);
            } catch (Exception ex) {
                plugin.getLogger().warning("Insel konnte nicht aus Nitrite geladen werden: " + ex.getMessage());
            }
        }
        finalizeLoadedIslands();
    }

    private void loadCleanupReservations() {
        for (Document document : cleanupCollection.find()) {
            try {
                String type = document.get("type", String.class);
                if ("cooldown".equals(type)) {
                    UUID blockedOwner = UUID.fromString(document.get("owner", String.class));
                    recreationCooldownOwners.add(blockedOwner);
                    continue;
                }
                if ("creation_rate_limit".equals(type)) {
                    UUID playerId = UUID.fromString(document.get("playerId", String.class));
                    int requestCount = Math.max(0, intValue(document.get("requestCount")));
                    long lastRequestAt = longValue(document.get("lastRequestAt"));
                    long cooldownUntil = longValue(document.get("cooldownUntil"));
                    islandCreationThrottleStates.put(playerId, new IslandCreationThrottleState(requestCount, lastRequestAt, cooldownUntil));
                    continue;
                }
                UUID islandOwner = UUID.fromString(document.get("islandOwner", String.class));
                int gridX = intValue(document.get("gridX"));
                int gridZ = intValue(document.get("gridZ"));
                int nextChunkIndex = Math.max(0, Math.min(TOTAL_CHUNKS, intValue(document.get("nextChunkIndex"))));
                reservedCleanupPlots.add(plotKey(gridX, gridZ));
                cleanupProgressByPlot.put(plotKey(gridX, gridZ), nextChunkIndex);
                recreationCooldownOwners.add(islandOwner);
                if (queuedIslandAreaCleanupOwners.add(islandOwner)) {
                    islandAreaCleanupQueue.offer(new IslandAreaCleanupTask(islandOwner, gridX, gridZ, nextChunkIndex));
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Cleanup-Reservierung konnte nicht geladen werden: " + ex.getMessage());
            }
        }
        healCleanupReservations(false);
    }

    private void finalizeLoadedIslands() {
        activeGrowthBoostIslands.clear();
        for (IslandData island : islands.values()) {
            if (island.getPoints() <= 0) island.setPoints(Math.max(1, island.getGeneratedChunks().size()));
            if (island.getLastActiveAt() <= 0) island.setLastActiveAt(System.currentTimeMillis());
            refreshGrowthBoostTracking(island);
            rebuildPlacementCaches(island);
            if (!isIslandFullyPregenerated(island)) {
                queuePregeneration(island);
            }
            queueBarrierFloorRepair(island);
        }
    }

    public boolean hasAnyActiveGrowthBoosts() {
        return !activeGrowthBoostIslands.isEmpty();
    }

    public List<IslandData> getIslandsWithActiveGrowthBoosts() {
        List<IslandData> result = new ArrayList<>(activeGrowthBoostIslands.size());
        for (UUID owner : activeGrowthBoostIslands) {
            IslandData island = islands.get(owner);
            if (island != null) {
                result.add(island);
            }
        }
        return result;
    }

    private void refreshGrowthBoostTracking(IslandData island) {
        if (island == null) return;
        if (island.getGrowthBoostUntil().isEmpty() || island.getGrowthBoostTier().isEmpty()) {
            activeGrowthBoostIslands.remove(island.getOwner());
            return;
        }
        activeGrowthBoostIslands.add(island.getOwner());
    }

    public void save() {
        islandCollection.remove(Filter.ALL);
        for (IslandData island : islands.values()) {
            islandCollection.insert(toDocument(island));
        }
        saveCleanupReservations(false);
        database.commit();
    }

    private void saveCleanupReservations(boolean commit) {
        cleanupCollection.remove(Filter.ALL);
        for (IslandAreaCleanupTask task : islandAreaCleanupQueue) {
            cleanupCollection.insert(cleanupTaskDocument(task));
        }
        for (UUID blockedOwner : recreationCooldownOwners) {
            cleanupCollection.insert(cleanupCooldownDocument(blockedOwner));
        }
        for (Map.Entry<UUID, IslandCreationThrottleState> entry : islandCreationThrottleStates.entrySet()) {
            cleanupCollection.insert(creationRateLimitDocument(entry.getKey(), entry.getValue()));
        }
        if (commit) {
            database.commit();
        }
    }

    public void shutdown() {
        save();
        database.close();
    }

    public void startPregenerationTask() {
        if (pregenerationTaskId != -1) {
            Bukkit.getScheduler().cancelTask(pregenerationTaskId);
        }
        pregenerationTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickBackgroundWork, 40L, 1L);
    }

    public void stopPregenerationTask() {
        if (pregenerationTaskId != -1) {
            Bukkit.getScheduler().cancelTask(pregenerationTaskId);
            pregenerationTaskId = -1;
        }
    }

    public void startIslandCreationTask() {
        stopIslandCreationTask();
        islandCreationTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickIslandCreationQueue, 26L, 2L);
        if (cleanupTaskId == -1) {
            cleanupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::runInactiveIslandCleanup, 20L * 60L + 73L, 20L * 60L * 60L);
        }
        if (islandAreaCleanupTaskId == -1) {
            islandAreaCleanupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickIslandAreaCleanupQueue, 3L, 1L);
        }
        if (pveTaskId == -1) {
            pveTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickPveZones, 33L, 20L);
        }
    }

    public void stopIslandCreationTask() {
        if (islandCreationTaskId != -1) {
            Bukkit.getScheduler().cancelTask(islandCreationTaskId);
            islandCreationTaskId = -1;
        }
        pendingIslandCreations.clear();
        islandCreationQueue.clear();
        islandCreationCallbacks.clear();
        islandReadyCallbacks.clear();
        if (pveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(pveTaskId);
            pveTaskId = -1;
        }
        resetAllPveZones();
    }

    private void tickBackgroundWork() {
        if (!pregenerationQueue.isEmpty()) {
            tickPregeneration();
            return;
        }
        if (!islandAreaCleanupQueue.isEmpty()) {
            tickIslandAreaCleanupQueue();
            return;
        }
        if (!barrierFloorRepairQueue.isEmpty()) {
            tickBarrierFloorRepairQueue();
        }
    }

    private void tickIslandCreationQueue() {
        IslandCreationTask task = islandCreationQueue.poll();
        if (task == null) {
            return;
        }
        UUID playerId = task.playerId();
        pendingIslandCreations.remove(playerId);
        IslandPlot requestedPlot = requestedIslandPlots.remove(playerId);
        if (requestedPlot != null) {
            reservedCreationPlots.remove(plotKey(requestedPlot.gridX(), requestedPlot.gridZ()));
        }
        IslandData island = getOrCreateIsland(playerId, requestedPlot);
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
        if (island == null) return 0;
        int generated = Math.min(TOTAL_CHUNKS, island.getGeneratedChunks().size());
        int indexed = Math.min(TOTAL_CHUNKS, pregenerationProgressByOwner.getOrDefault(playerId, generated));
        return Math.max(generated, indexed);
    }

    public int getTotalIslandChunkCount() {
        return TOTAL_CHUNKS;
    }

    public void queueIslandCreation(UUID playerId, Consumer<IslandData> onReady) {
        queueIslandCreation(playerId, null, onReady);
    }

    public boolean queueIslandCreation(UUID playerId, IslandPlot requestedPlot, Consumer<IslandData> onReady) {
        IslandData existing = islands.get(playerId);
        if (existing != null) {
            if (onReady != null) {
                onReady.accept(existing);
            }
            return true;
        }
        if (mustWaitForRecreationQueue(playerId)) {
            return false;
        }
        if (requestedPlot != null && !isPlotAvailable(requestedPlot.gridX(), requestedPlot.gridZ())) {
            return false;
        }
        if (isIslandCreationRateLimited(playerId)) {
            return false;
        }
        registerIslandCreationRequest(playerId);
        IslandData created = getOrCreateIsland(playerId, requestedPlot);
        if (onReady != null) {
            onReady.accept(created);
        }
        return true;
    }

    private void tickPregeneration() {
        int budget = 1;
        int maxPerIslandTask = 1;
        while (budget > 0 && !pregenerationQueue.isEmpty()) {
            PregenerationTask task = pregenerationQueue.poll();
            queuedPregenerationOwners.remove(task.islandOwner());
            IslandData island = islands.get(task.islandOwner());
            if (island == null) continue;
            int idx = task.nextIndex();
            pregenerationProgressByOwner.put(task.islandOwner(), Math.max(0, Math.min(TOTAL_CHUNKS, idx)));
            int processed = 0;
            while (idx < TOTAL_CHUNKS && processed < budget && processed < maxPerIslandTask) {
                int relX = pregenerationRelXByIndex(idx);
                int relZ = pregenerationRelZByIndex(idx);
                ensureChunkTemplateGenerated(island, relX, relZ);
                idx++;
                processed++;
            }
            pregenerationProgressByOwner.put(task.islandOwner(), Math.max(0, Math.min(TOTAL_CHUNKS, idx)));
            if (idx >= TOTAL_CHUNKS || idx % 32 == 0) {
                save();
            }
            budget -= processed;
            if (idx < TOTAL_CHUNKS) {
                if (queuedPregenerationOwners.add(task.islandOwner())) {
                    pregenerationQueue.offer(new PregenerationTask(task.islandOwner(), idx));
                }
            } else {
                pregenerationProgressByOwner.remove(task.islandOwner());
                dispatchIslandReadyCallbacks(island);
            }
        }
    }

    private void runInactiveIslandCleanup() {
        long now = System.currentTimeMillis();
        List<IslandData> toDelete = new ArrayList<>();
        boolean changed = false;
        for (IslandData island : islands.values()) {
            if (island.getPoints() >= 1000L) continue;
            long inactiveFor = Math.max(0L, now - island.getLastActiveAt());
            long remainingMillis = ONE_YEAR_MS - inactiveFor;
            int warningStage = determineInactivityWarningStage(remainingMillis);
            if (warningStage > island.getInactivityWarningStage()) {
                notifyInactivityWarning(island, remainingMillis, warningStage);
                island.setInactivityWarningStage(warningStage);
                changed = true;
            }
            if (inactiveFor < ONE_YEAR_MS) continue;
            toDelete.add(island);
        }
        for (IslandData island : toDelete) {
            deleteIslandData(island, true);
            plugin.getLogger().info("Inaktive Insel gel\u00f6scht (Punkte<1000, >1 Jahr inaktiv): " + island.getOwner());
        }
        if (changed || !toDelete.isEmpty()) save();
    }

    private int determineInactivityWarningStage(long remainingMillis) {
        if (remainingMillis <= INACTIVITY_WARNING_1_DAY_MS) {
            return 3;
        }
        if (remainingMillis <= INACTIVITY_WARNING_7_DAYS_MS) {
            return 2;
        }
        if (remainingMillis <= INACTIVITY_WARNING_30_DAYS_MS) {
            return 1;
        }
        return 0;
    }

    private void notifyInactivityWarning(IslandData island, long remainingMillis, int warningStage) {
        if (island == null || warningStage <= 0) return;
        String islandTitle = getIslandTitleDisplay(island);
        String remainingText = formatDurationShort(Math.max(0L, remainingMillis));
        for (UUID playerId : getInactivityWarningRecipients(island)) {
            Player online = Bukkit.getPlayer(playerId);
            if (online == null || !online.isOnline()) continue;
            online.sendMessage(ChatColor.GOLD + "Warnung: " + ChatColor.YELLOW + "Die Insel " + ChatColor.WHITE + islandTitle
                    + ChatColor.YELLOW + " wird bei weiterer Inaktivit\u00e4t in " + ChatColor.GOLD + remainingText
                    + ChatColor.YELLOW + " automatisch gel\u00f6scht.");
            online.sendMessage(ChatColor.GRAY + "Ein Login oder Aktivit\u00e4t auf der Insel setzt den Inaktivit\u00e4ts-Timer zur\u00fcck.");
        }
    }

    private Set<UUID> getInactivityWarningRecipients(IslandData island) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (island == null) {
            return recipients;
        }
        recipients.add(island.getOwner());
        recipients.addAll(island.getMasters());
        recipients.addAll(island.getOwners());
        recipients.addAll(island.getMemberBuildAccess());
        recipients.addAll(island.getMemberContainerAccess());
        recipients.addAll(island.getMemberRedstoneAccess());
        return recipients;
    }

    private void deleteIslandData(IslandData island, boolean createReplacementForMasters) {
        if (island == null) return;
        recreationCooldownOwners.add(island.getOwner());
        removeIslandRuntimeState(island);
        evacuateIslandPlayers(island);
        scheduleIslandAreaCleanup(island);
        islands.remove(island.getOwner());
        activeGrowthBoostIslands.remove(island.getOwner());
        pendingMasterInvites.entrySet().removeIf(e -> {
            if (e.getValue().primaryOwner.equals(island.getOwner()) || e.getKey().equals(island.getOwner())) {
                if (e.getValue().taskId != -1) Bukkit.getScheduler().cancelTask(e.getValue().taskId);
                return true;
            }
            return false;
        });
        pendingBorderUnlockRequests.entrySet().removeIf(entry ->
                entry.getValue().requesterOwner.equals(island.getOwner())
                        || entry.getValue().requiredNeighborOwners.contains(island.getOwner()));

        for (UUID master : new ArrayList<>(island.getMasters())) {
            MasterInvite removed = pendingMasterInvites.remove(master);
            if (removed != null && removed.taskId != -1) Bukkit.getScheduler().cancelTask(removed.taskId);
        }
        save();
    }

    private void scheduleIslandAreaCleanup(IslandData island) {
        if (island == null) return;
        World world = skyWorldService.getWorld();
        if (world == null) return;
        reservedCleanupPlots.add(plotKey(island.getGridX(), island.getGridZ()));
        cleanupProgressByPlot.put(plotKey(island.getGridX(), island.getGridZ()), 0);

        for (Entity entity : new ArrayList<>(getEntitiesInIsland(island))) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }

        if (queuedIslandAreaCleanupOwners.add(island.getOwner())) {
            islandAreaCleanupQueue.offer(new IslandAreaCleanupTask(island.getOwner(), island.getGridX(), island.getGridZ(), 0));
            saveCleanupReservations(true);
        }
    }

    public void forceIslandAreaCleanup(int gridX, int gridZ) {
        String plotKey = plotKey(gridX, gridZ);
        reservedCleanupPlots.add(plotKey);
        cleanupProgressByPlot.put(plotKey, 0);
        healCleanupReservations(true);
    }

    private void removeIslandRuntimeState(IslandData island) {
        queuedPregenerationOwners.remove(island.getOwner());
        pregenerationQueue.removeIf(task -> task.islandOwner().equals(island.getOwner()));
        pendingIslandCreations.remove(island.getOwner());
        islandCreationQueue.removeIf(task -> task.playerId().equals(island.getOwner()));
        islandCreationCallbacks.remove(island.getOwner());
        islandReadyCallbacks.remove(island.getOwner());
        pregenerationProgressByOwner.remove(island.getOwner());
        IslandPlot requestedPlot = requestedIslandPlots.remove(island.getOwner());
        if (requestedPlot != null) {
            reservedCreationPlots.remove(plotKey(requestedPlot.gridX(), requestedPlot.gridZ()));
        }
        growthDebugWindowStart.entrySet().removeIf(entry -> entry.getKey().startsWith(island.getOwner() + ":"));
        growthDebugAttempts.entrySet().removeIf(entry -> entry.getKey().startsWith(island.getOwner() + ":"));
        growthDebugHits.entrySet().removeIf(entry -> entry.getKey().startsWith(island.getOwner() + ":"));
        growthDebugBridgeFailures.entrySet().removeIf(entry -> entry.getKey().startsWith(island.getOwner() + ":"));
        growthDebugTargets.entrySet().removeIf(entry -> entry.getKey().startsWith(island.getOwner() + ":"));
        growthDebugTargetSummary.entrySet().removeIf(entry -> entry.getKey().startsWith(island.getOwner() + ":"));
        parcelPvpConsents.entrySet().removeIf(entry -> entry.getValue() != null && entry.getValue().startsWith(island.getOwner() + ":"));
        resetPveZonesForIsland(island.getOwner());
    }

    private void resetPveZonesForIsland(UUID islandOwner) {
        if (islandOwner == null) return;
        for (PveZoneRuntime runtime : new ArrayList<>(activePveZones.values())) {
            if (runtime.islandOwner.equals(islandOwner)) {
                resetPveZone(runtime, null);
            }
        }
    }

    private void tickIslandAreaCleanupQueue() {
        healCleanupReservations(true);
        int budget = 1;
        while (budget > 0 && !islandAreaCleanupQueue.isEmpty()) {
            IslandAreaCleanupTask task = islandAreaCleanupQueue.poll();
            try {
                queuedIslandAreaCleanupOwners.remove(task.islandOwner());
                int nextIndex = clearIslandAreaChunkBatch(task.gridX(), task.gridZ(), task.nextChunkIndex(), 1);
                budget--;
                if (nextIndex < TOTAL_CHUNKS) {
                    cleanupProgressByPlot.put(plotKey(task.gridX(), task.gridZ()), nextIndex);
                    if (queuedIslandAreaCleanupOwners.add(task.islandOwner())) {
                        islandAreaCleanupQueue.offer(new IslandAreaCleanupTask(task.islandOwner(), task.gridX(), task.gridZ(), nextIndex));
                    }
                    saveCleanupReservations(true);
                } else {
                    reservedCleanupPlots.remove(plotKey(task.gridX(), task.gridZ()));
                    cleanupProgressByPlot.remove(plotKey(task.gridX(), task.gridZ()));
                    saveCleanupReservations(true);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Cleanup-Tick fehlgeschlagen f\u00fcr Plot " + task.gridX() + ":" + task.gridZ() + ": " + ex.getMessage());
                if (queuedIslandAreaCleanupOwners.add(task.islandOwner())) {
                    islandAreaCleanupQueue.offer(task);
                }
                saveCleanupReservations(true);
                return;
            }
        }
    }

    private void healCleanupReservations(boolean persistIfChanged) {
        Map<String, IslandAreaCleanupTask> queuedByPlot = new HashMap<>();
        Set<UUID> queuedOwners = new HashSet<>();
        for (IslandAreaCleanupTask task : islandAreaCleanupQueue) {
            queuedByPlot.put(plotKey(task.gridX(), task.gridZ()), task);
            queuedOwners.add(task.islandOwner());
        }

        boolean changed = false;
        for (String plotKey : new HashSet<>(reservedCleanupPlots)) {
            IslandAreaCleanupTask queuedTask = queuedByPlot.get(plotKey);
            int trackedProgress = Math.max(0, Math.min(TOTAL_CHUNKS, cleanupProgressByPlot.getOrDefault(plotKey, 0)));
            if (queuedTask != null) {
                cleanupProgressByPlot.put(plotKey, Math.max(trackedProgress, queuedTask.nextChunkIndex()));
                continue;
            }

            String[] parts = plotKey.split(":");
            if (parts.length != 2) continue;
            int gridX;
            int gridZ;
            try {
                gridX = Integer.parseInt(parts[0]);
                gridZ = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
                continue;
            }

            if (trackedProgress >= TOTAL_CHUNKS || isPlotLikelyCleared(gridX, gridZ)) {
                reservedCleanupPlots.remove(plotKey);
                cleanupProgressByPlot.remove(plotKey);
                changed = true;
                plugin.getLogger().info("Cleanup-Selbstheilung aktiv: Plot " + plotKey + " war bereits leer und wurde aus der L\u00f6sch-Reservierung entfernt.");
                continue;
            }

            UUID syntheticOwner = UUID.nameUUIDFromBytes(("cleanup:" + plotKey).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!queuedOwners.add(syntheticOwner)) {
                continue;
            }
            int nextChunkIndex = trackedProgress;
            islandAreaCleanupQueue.offer(new IslandAreaCleanupTask(syntheticOwner, gridX, gridZ, nextChunkIndex));
            queuedIslandAreaCleanupOwners.add(syntheticOwner);
            changed = true;
            plugin.getLogger().info("Cleanup-Selbstheilung aktiv: Plot " + plotKey + " erneut in die L\u00f6sch-Q\u00fc\u00fc eingetragen.");
        }

        if (changed && persistIfChanged) {
            saveCleanupReservations(true);
        }
    }

    private boolean isPlotLikelyCleared(int gridX, int gridZ) {
        World world = skyWorldService.getWorld();
        if (world == null) return true;
        int minChunkX = plotMinChunkX(gridX);
        int minChunkZ = plotMinChunkZ(gridZ);
        int minY = Math.max(world.getMinHeight(), SkyWorldService.SPAWN_Y - 16);
        int maxY = Math.min(world.getMaxHeight() - 1, SkyWorldService.SPAWN_Y + 20);

        for (int chunkOffsetX = 0; chunkOffsetX < ISLAND_CHUNKS; chunkOffsetX += 8) {
            for (int chunkOffsetZ = 0; chunkOffsetZ < ISLAND_CHUNKS; chunkOffsetZ += 8) {
                Chunk chunk = world.getChunkAt(minChunkX + chunkOffsetX, minChunkZ + chunkOffsetZ);
                for (int localX = 0; localX < 16; localX += 5) {
                    for (int localZ = 0; localZ < 16; localZ += 5) {
                        for (int y = minY; y <= maxY; y += 2) {
                            if (!chunk.getBlock(localX, y, localZ).getType().isAir()) {
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    private void evacuateIslandPlayers(IslandData island) {
        if (island == null) return;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.isOnline()) continue;
            if (getIslandAt(online.getLocation()) != island) continue;
            online.teleport(getSpawnLocation());
            if (online.getUniqueId().equals(island.getOwner())) {
                online.sendMessage(ChatColor.YELLOW + "Deine Insel wurde entfernt.");
            } else if (island.getMasters().contains(online.getUniqueId())) {
                online.sendMessage(ChatColor.YELLOW + "Die gemeinsame Insel wurde entfernt.");
            } else {
                online.sendMessage(ChatColor.YELLOW + "Diese Insel wurde entfernt.");
            }
        }
    }

    private int clearIslandAreaChunkBatch(int gridX, int gridZ, int startIndex, int maxChunks) {
        World world = skyWorldService.getWorld();
        if (world == null) return TOTAL_CHUNKS;

        int minChunkX = plotMinChunkX(gridX);
        int minChunkZ = plotMinChunkZ(gridZ);

        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int index = Math.max(0, startIndex);
        int processed = 0;
        while (index < TOTAL_CHUNKS && processed < Math.max(1, maxChunks)) {
            int relChunkX = index % ISLAND_CHUNKS;
            int relChunkZ = index / ISLAND_CHUNKS;
            int chunkX = minChunkX + relChunkX;
            int chunkZ = minChunkZ + relChunkZ;
            Chunk chunk = world.getChunkAt(chunkX, chunkZ);
            int baseX = chunkX << 4;
            int baseZ = chunkZ << 4;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minY; y < maxY; y++) {
                        Block block = chunk.getBlock(x, y, z);
                        if (y == minY) {
                            if (block.getType() != Material.BARRIER) {
                                block.setType(Material.BARRIER, false);
                            }
                        } else if (!block.getType().isAir()) {
                            block.setType(Material.AIR, false);
                        }
                    }
                    for (int y = minY; y < maxY; y += 4) {
                        world.setBiome(baseX + x, y, baseZ + z, Biome.THE_VOID);
                    }
                }
            }
            index++;
            processed++;
        }
        return index;
    }

    private void queueBarrierFloorRepair(IslandData island) {
        if (island == null) return;
        String plotKey = plotKey(island.getGridX(), island.getGridZ());
        if (queuedBarrierFloorRepairPlots.add(plotKey)) {
            barrierFloorRepairQueue.offer(new BarrierFloorRepairTask(island.getGridX(), island.getGridZ(), 0));
        }
    }

    private void tickBarrierFloorRepairQueue() {
        int budget = BARRIER_FLOOR_REPAIR_CHUNKS_PER_TICK;
        while (budget > 0 && !barrierFloorRepairQueue.isEmpty()) {
            BarrierFloorRepairTask task = barrierFloorRepairQueue.poll();
            String plotKey = plotKey(task.gridX(), task.gridZ());
            int nextIndex = repairBarrierFloorChunkBatch(task.gridX(), task.gridZ(), task.nextChunkIndex(), 1);
            budget--;
            if (nextIndex < TOTAL_CHUNKS) {
                barrierFloorRepairQueue.offer(new BarrierFloorRepairTask(task.gridX(), task.gridZ(), nextIndex));
            } else {
                queuedBarrierFloorRepairPlots.remove(plotKey);
                plugin.getLogger().info("Barrier-Boden repariert fuer Inselplot " + plotKey + ".");
            }
        }
    }

    private int repairBarrierFloorChunkBatch(int gridX, int gridZ, int startIndex, int maxChunks) {
        World world = skyWorldService.getWorld();
        if (world == null) return TOTAL_CHUNKS;
        int minChunkX = plotMinChunkX(gridX);
        int minChunkZ = plotMinChunkZ(gridZ);
        int index = Math.max(0, startIndex);
        int processed = 0;
        while (index < TOTAL_CHUNKS && processed < Math.max(1, maxChunks)) {
            int relChunkX = index % ISLAND_CHUNKS;
            int relChunkZ = index / ISLAND_CHUNKS;
            int chunkX = minChunkX + relChunkX;
            int chunkZ = minChunkZ + relChunkZ;
            ensureChunkBarrierFloor(world, chunkX << 4, chunkZ << 4);
            index++;
            processed++;
        }
        return index;
    }

    private boolean isIslandFullyPregenerated(IslandData island) {
        return island != null && island.getGeneratedChunks().size() >= TOTAL_CHUNKS;
    }

    private void dispatchIslandReadyCallbacks(IslandData island) {
        if (island == null) return;
        if (isIslandPlotPendingDeletion(island)) {
            islandReadyCallbacks.remove(island.getOwner());
            return;
        }
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
        if (isIslandPlotPendingDeletion(island)) return;
        if (isIslandFullyPregenerated(island)) return;
        pregenerationProgressByOwner.put(island.getOwner(), Math.max(
                pregenerationProgressByOwner.getOrDefault(island.getOwner(), 0),
                Math.min(TOTAL_CHUNKS, island.getGeneratedChunks().size())
        ));
        if (queuedPregenerationOwners.add(island.getOwner())) {
            pregenerationQueue.offer(new PregenerationTask(island.getOwner(), pregenerationProgressByOwner.get(island.getOwner())));
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
        ensureSpawnIsland();
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

    private void ensureSpawnIsland() {
        IslandData island = islands.get(SPAWN_ISLAND_OWNER);
        boolean created = false;
        if (island == null) {
            island = new IslandData(SPAWN_ISLAND_OWNER);
            islands.put(SPAWN_ISLAND_OWNER, island);
            created = true;
        }

        island.setGridX(0);
        island.setGridZ(0);
        island.setTitle("Spawn");
        if (island.getIslandSpawn() == null) {
            island.setIslandSpawn(getSpawnLocation());
        }
        if (island.getPoints() <= 0L) {
            island.setPoints(1L);
        }
        if (island.getLastActiveAt() <= 0L) {
            island.setLastActiveAt(System.currentTimeMillis());
        }
        island.getMasters().clear();
        island.getIslandVisitorSettings().setTeleport(true);
        island.getUnlockedChunks().add(chunkKey(CENTER_A, CENTER_A));
        island.getUnlockedChunks().add(chunkKey(CENTER_A, CENTER_B));
        island.getUnlockedChunks().add(chunkKey(CENTER_B, CENTER_A));
        island.getUnlockedChunks().add(chunkKey(CENTER_B, CENTER_B));
        ensureChunkTemplateGenerated(island, CENTER_A, CENTER_A);
        ensureChunkTemplateGenerated(island, CENTER_A, CENTER_B);
        ensureChunkTemplateGenerated(island, CENTER_B, CENTER_A);
        ensureChunkTemplateGenerated(island, CENTER_B, CENTER_B);
        refreshGrowthBoostTracking(island);
        rebuildPlacementCaches(island);
        if (created) {
            save();
        }
    }

    public IslandData getOrCreateIsland(UUID owner) {
        return getOrCreateIsland(owner, null);
    }

    public IslandData getOrCreateIsland(UUID owner, IslandPlot requestedPlot) {
        IslandData existing = getIsland(owner).orElse(null);
        if (existing != null) return existing;
        IslandPlot plot = requestedPlot != null && isPlotAvailable(requestedPlot.gridX(), requestedPlot.gridZ())
                ? requestedPlot
                : findNextFreePlot();
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
        org.bukkit.block.data.type.Leaves leaves = (org.bukkit.block.data.type.Leaves) org.bukkit.Bukkit.createBlockData(Material.OAK_LEAVES);
        leaves.setPersistent(true);
        for (int lx = -2; lx <= 2; lx++) for (int lz = -2; lz <= 2; lz++) {
            if (Math.abs(lx) == 2 && Math.abs(lz) == 2) continue;
            w.getBlockAt(x + lx, floorY + 4, z + lz).setBlockData(leaves, false);
        }
        for (int lx = -1; lx <= 1; lx++) for (int lz = -1; lz <= 1; lz++) {
            w.getBlockAt(x + lx, floorY + 5, z + lz).setBlockData(leaves, false);
        }
        w.getBlockAt(x, floorY + 6, z).setBlockData(leaves, false);
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
    public Optional<IslandData> getIsland(UUID ownerOrMaster, boolean includeMasters) {
        IslandData direct = islands.get(ownerOrMaster);
        if (direct != null || !includeMasters) return Optional.ofNullable(direct);
        return islands.values().stream().filter(i -> i.getMasters().contains(ownerOrMaster)).findFirst();
    }
    public Optional<IslandData> getIsland(UUID owner) { return getIsland(owner, true); }
    public List<IslandData> getAllIslands() { return new ArrayList<>(islands.values()); }
    public Optional<IslandData> getIslandByGrid(int gridX, int gridZ) {
        return islands.values().stream()
                .filter(island -> island.getGridX() == gridX && island.getGridZ() == gridZ)
                .findFirst();
    }

    public String getIslandTitleDisplay(IslandData island) {
        if (island == null) return "Unbekannte Insel";
        if (isSpawnIsland(island)) return "Spawn";
        String raw = island.getTitle();
        if (raw != null && !raw.isBlank()) return raw;
        String ownerName = Bukkit.getOfflinePlayer(island.getOwner()).getName();
        return "Insel von " + (ownerName == null ? "?" : ownerName);
    }

    public String getIslandWarpDisplay(IslandData island) {
        if (island == null) return "Unbekannter Warp";
        if (isSpawnIsland(island) && (island.getWarpName() == null || island.getWarpName().isBlank())) return "Spawn";
        String raw = island.getWarpName();
        if (raw != null && !raw.isBlank()) return raw.trim();
        return getIslandTitleDisplay(island);
    }

    public String getIslandOwnerListDisplay(IslandData island) {
        if (island == null) return "";
        List<String> names = new ArrayList<>();
        String ownerName = Bukkit.getOfflinePlayer(island.getOwner()).getName();
        names.add(ownerName == null ? "?" : ownerName);
        for (UUID master : island.getMasters()) {
            String name = Bukkit.getOfflinePlayer(master).getName();
            if (name != null && !name.isBlank()) names.add(name);
        }
        String joined = String.join(", ", names);
        return joined.length() > 60 ? joined.substring(0, 57) + "..." : joined;
    }

    public String getIslandMasterDisplay(IslandData island) {
        if (island == null) return "?";
        if (isSpawnIsland(island)) return "Kein Master";
        String ownerName = Bukkit.getOfflinePlayer(island.getOwner()).getName();
        String primary = ownerName == null || ownerName.isBlank() ? "?" : ownerName;
        int extraMasters = island.getMasters().size();
        return extraMasters <= 0 ? primary : primary + " +" + extraMasters;
    }

    public String getIslandMasterDisplay(IslandData island, long rotationStep) {
        if (island == null) return "?";
        if (isSpawnIsland(island)) return "Kein Master";
        List<String> names = new ArrayList<>();
        String ownerName = Bukkit.getOfflinePlayer(island.getOwner()).getName();
        names.add(ownerName == null || ownerName.isBlank() ? "?" : ownerName);
        for (UUID master : island.getMasters()) {
            String name = Bukkit.getOfflinePlayer(master).getName();
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        if (names.size() <= 2) {
            return String.join(", ", names);
        }
        int start = (int) Math.floorMod(rotationStep, names.size());
        String first = names.get(start);
        String second = names.get((start + 1) % names.size());
        return first + ", " + second + " +" + (names.size() - 2);
    }

    public String getIslandMasterTickerDisplay(IslandData island, long rotationStep, int windowSize) {
        if (island == null) return "?";
        List<String> names = new ArrayList<>();
        String ownerName = Bukkit.getOfflinePlayer(island.getOwner()).getName();
        names.add(ownerName == null || ownerName.isBlank() ? "?" : ownerName);
        for (UUID master : island.getMasters()) {
            String name = Bukkit.getOfflinePlayer(master).getName();
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        String joined = String.join(", ", names);
        if (names.size() <= 2 || joined.length() <= windowSize) {
            return joined;
        }
        String suffix = " " + names.size() + "M";
        int safeWindow = Math.max(8, windowSize);
        int textWindow = Math.max(4, safeWindow - suffix.length());
        if (joined.length() <= textWindow) {
            return joined + suffix;
        }
        String spacer = "   ";
        String loop = joined + spacer + joined;
        int start = (int) Math.floorMod(rotationStep, joined.length() + spacer.length());
        String visible = loop.substring(start, start + textWindow).trim();
        if (visible.isBlank()) {
            visible = joined.substring(0, Math.min(joined.length(), textWindow));
        }
        return visible + suffix;
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

    public int getIslandLoadPercent(IslandData island) {
        return getIslandLoadBreakdown(island).totalPercent();
    }

    public IslandLoadBreakdown getIslandLoadBreakdown(IslandData island) {
        if (island == null) return new IslandLoadBreakdown(0, 0, 0);
        List<Entity> entities = getEntitiesInIsland(island);
        int animals = (int) entities.stream().filter(e -> e instanceof Animals).count();
        int golems = (int) entities.stream().filter(e -> isTrackedGolem(e.getType())).count();
        int villagers = (int) entities.stream().filter(e -> e instanceof Villager).count();
        int armorStands = (int) entities.stream()
                .filter(e -> e instanceof ArmorStand)
                .filter(e -> e.getScoreboardTags().stream().noneMatch(tag -> tag.startsWith("skycity_")))
                .count();
        int minecarts = (int) entities.stream().filter(e -> e instanceof org.bukkit.entity.Minecart).count();
        int boats = (int) entities.stream().filter(e -> e instanceof org.bukkit.entity.Boat).count();
        int players = (int) entities.stream().filter(e -> e instanceof Player).count();
        int textDisplays = (int) entities.stream().filter(e -> e instanceof TextDisplay).count();
        int interactiveItemDisplays = (int) entities.stream()
                .filter(e -> e instanceof ItemDisplay)
                .filter(e -> hasDisplayHitbox(e, entities))
                .count();
        int displayHitboxes = (int) entities.stream()
                .filter(e -> e instanceof Interaction)
                .filter(e -> hasLinkedDisplay(e, entities))
                .count();

        double entityLoad = 0.0;
        entityLoad += weightedLoadComponent(animals, getCurrentUpgradeLimit(island, UpgradeBranch.ANIMAL), 0.18);
        entityLoad += weightedLoadComponent(golems, getCurrentUpgradeLimit(island, UpgradeBranch.GOLEM), 0.08);
        entityLoad += weightedLoadComponent(villagers, getCurrentUpgradeLimit(island, UpgradeBranch.VILLAGER), 0.18);
        entityLoad += weightedLoadComponent(armorStands, getCurrentUpgradeLimit(island, UpgradeBranch.ARMOR_STAND), 0.08);
        entityLoad += weightedLoadComponent(minecarts, getCurrentUpgradeLimit(island, UpgradeBranch.MINECART), 0.04);
        entityLoad += weightedLoadComponent(boats, getCurrentUpgradeLimit(island, UpgradeBranch.BOAT), 0.03);
        entityLoad += weightedLoadComponent(players, 6, 0.04);
        entityLoad += weightedLoadComponent(textDisplays, TEXT_DISPLAY_LOAD_LIMIT, 0.04);
        entityLoad += weightedLoadComponent(interactiveItemDisplays, INTERACTIVE_ITEM_DISPLAY_LOAD_LIMIT, 0.03);
        entityLoad += weightedLoadComponent(displayHitboxes, DISPLAY_HITBOX_LOAD_LIMIT, 0.03);

        double blockLoad = 0.0;
        blockLoad += weightedLoadComponent(getCachedInventoryBlockCount(island), getCurrentUpgradeLimit(island, UpgradeBranch.CONTAINER), 0.10);
        blockLoad += weightedLoadComponent(getCachedHopperCount(island), getCurrentUpgradeLimit(island, UpgradeBranch.HOPPER), 0.16);
        blockLoad += weightedLoadComponent(getCachedPistonCount(island), getCurrentUpgradeLimit(island, UpgradeBranch.PISTON), 0.08);
        blockLoad += weightedLoadComponent(getCachedObserverCount(island), getCurrentUpgradeLimit(island, UpgradeBranch.OBSERVER), 0.04);
        blockLoad += weightedLoadComponent(getCachedDispenserCount(island), getCurrentUpgradeLimit(island, UpgradeBranch.DISPENSER), 0.03);
        blockLoad += weightedLoadComponent(island.getUnlockedChunks().size(), ISLAND_CHUNKS * ISLAND_CHUNKS, 0.06);

        return new IslandLoadBreakdown(
                percentFromLoad(blockLoad + entityLoad),
                percentFromLoad(blockLoad),
                percentFromLoad(entityLoad)
        );
    }

    private double weightedLoadComponent(int used, int limit, double weight) {
        if (limit <= 0 || weight <= 0.0) return 0.0;
        return Math.min(1.0, Math.max(0.0, used / (double) limit)) * weight;
    }

    private boolean hasDisplayHitbox(Entity display, List<Entity> entities) {
        if (display == null || entities == null || entities.isEmpty()) return false;
        for (Entity entity : entities) {
            if (!(entity instanceof Interaction)) continue;
            if (!sharesSkycityDisplayTag(display, entity)) continue;
            if (display.getLocation().distanceSquared(entity.getLocation()) <= 4.0D) return true;
        }
        return false;
    }

    private boolean hasLinkedDisplay(Entity interaction, List<Entity> entities) {
        if (!(interaction instanceof Interaction) || entities == null || entities.isEmpty()) return false;
        for (Entity entity : entities) {
            if (!(entity instanceof TextDisplay) && !(entity instanceof ItemDisplay)) continue;
            if (!sharesSkycityDisplayTag(interaction, entity)) continue;
            if (interaction.getLocation().distanceSquared(entity.getLocation()) <= 4.0D) return true;
        }
        return false;
    }

    private boolean sharesSkycityDisplayTag(Entity first, Entity second) {
        if (first == null || second == null) return false;
        for (String tag : first.getScoreboardTags()) {
            if (tag == null || !tag.startsWith("skycity_")) continue;
            if (second.getScoreboardTags().contains(tag)) return true;
        }
        return false;
    }

    private int percentFromLoad(double load) {
        return (int) Math.max(0, Math.min(100, Math.round(load * 100.0)));
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

    public String getIslandOwnerDisplay(IslandData island) {
        if (island == null || island.getOwners().isEmpty()) return "-";
        List<String> names = new ArrayList<>();
        for (UUID owner : island.getOwners()) {
            String name = Bukkit.getOfflinePlayer(owner).getName();
            if (name != null && !name.isBlank()) names.add(name);
        }
        if (names.isEmpty()) return "-";
        String joined = String.join(", ", names);
        return joined.length() > 60 ? joined.substring(0, 57) + "..." : joined;
    }

    public boolean isIslandMaster(IslandData island, UUID playerId) {
        if (isSpawnIsland(island)) return false;
        if (playerId != null && Bukkit.getOfflinePlayer(playerId).isOp()) return true;
        return island != null && playerId != null && (island.getOwner().equals(playerId) || island.getMasters().contains(playerId));
    }

    public boolean isIslandOwner(IslandData island, UUID playerId) {
        if (playerId != null && Bukkit.getOfflinePlayer(playerId).isOp()) return true;
        return island != null && playerId != null
                && (isIslandMaster(island, playerId)
                || island.getOwners().contains(playerId));
    }

    public boolean isIslandAssociated(IslandData island, UUID playerId) {
        if (island == null || playerId == null) return false;
        if (org.bukkit.Bukkit.getOfflinePlayer(playerId).isOp()) return true;
        return isIslandOwner(island, playerId) || hasAnyMemberPermission(island, playerId);
    }

    public boolean hasAnyIslandAssociation(UUID playerId) {
        return playerId != null && islands.values().stream().anyMatch(island -> isIslandAssociated(island, playerId));
    }

    public boolean isPrimaryMaster(IslandData island, UUID playerId) {
        if (isSpawnIsland(island)) return false;
        return island != null && playerId != null && island.getOwner().equals(playerId);
    }

    public void markIslandActivity(UUID playerId) {
        IslandData island = getIsland(playerId).orElse(null);
        if (island == null) return;
        island.setLastActiveAt(System.currentTimeMillis());
        island.setInactivityWarningStage(0);
    }

    public void addPoints(IslandData island, long amount) {
        if (island == null || amount == 0) return;
        island.setPoints(Math.max(0L, island.getPoints() + amount));
    }

    public boolean queueMasterInvite(IslandData island, UUID inviter, UUID target) {
        if (island == null || inviter == null || target == null) return false;
        if (isSpawnIsland(island)) return false;
        if (!isIslandMaster(island, inviter) && !Bukkit.getOfflinePlayer(inviter).isOp()) return false;
        if (island.getOwner().equals(target) || island.getMasters().contains(target)) return false;

        long expiresAt = System.currentTimeMillis() + 60000L;
        MasterInvite invite = new MasterInvite(island.getOwner(), expiresAt);

        MasterInvite old = pendingMasterInvites.put(target, invite);
        if (old != null && old.taskId != -1) Bukkit.getScheduler().cancelTask(old.taskId);

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player p = Bukkit.getPlayer(target);
            if (p != null && p.isOnline()) {
                long remaining = (invite.expiresAt - System.currentTimeMillis()) / 1000L;
                if (remaining <= 0) {
                    clearMasterInvite(target);
                    p.sendMessage(org.bukkit.ChatColor.RED + "Deine Master-Einladung ist abgelaufen.");
                } else {
                    p.sendTitle(org.bukkit.ChatColor.GOLD + "Master Einladung!", org.bukkit.ChatColor.YELLOW + "Noch " + remaining + " Sekunden...", 0, 30, 0);
                }
            } else if (System.currentTimeMillis() > invite.expiresAt) {
                clearMasterInvite(target);
            }
        }, 0L, 20L);
        invite.taskId = taskId;

        return true;
    }

    public IslandData getPendingMasterInviteIsland(UUID target) {
        MasterInvite invite = pendingMasterInvites.get(target);
        if (invite == null) return null;
        return islands.get(invite.primaryOwner);
    }

    public void clearMasterInvite(UUID target) {
        if (target != null) {
            MasterInvite removed = pendingMasterInvites.remove(target);
            if (removed != null && removed.taskId != -1) Bukkit.getScheduler().cancelTask(removed.taskId);
        }
    }

    public boolean acceptMasterInvite(UUID target) {
        MasterInvite invite = pendingMasterInvites.remove(target);
        if (invite == null) return false;
        if (invite.taskId != -1) Bukkit.getScheduler().cancelTask(invite.taskId);
        UUID primaryOwner = invite.primaryOwner;
        IslandData island = islands.get(primaryOwner);
        if (island == null) return false;
        if (isSpawnIsland(island)) return false;
        if (target.equals(primaryOwner)) return false;

        // Master darf nur auf einer Insel sein. Owner/Member auf anderen Inseln bleiben erhalten.
        IslandData current = getIsland(target).orElse(null);
        if (current != null && current != island) {
            removeMasterFromIsland(current, target, false);
        }
        island.getOwners().remove(target);
        clearMemberPermissions(island, target);
        island.getMasters().add(target);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        Player primary = Bukkit.getPlayer(primaryOwner);
        if (primary != null && primary.isOnline()) {
            String targetName = Bukkit.getOfflinePlayer(target).getName();
            primary.sendMessage(ChatColor.GREEN + (targetName == null ? "Ein Spieler" : targetName) + " ist jetzt Master deiner Insel.");
        }
        return true;
    }

    public boolean leaveMasterRole(UUID playerId) {
        IslandData island = getIsland(playerId).orElse(null);
        if (island == null) return false;
        if (isSpawnIsland(island)) return false;
        if (!isIslandMaster(island, playerId)) return false;
        removeMasterFromIsland(island, playerId, false);
        return true;
    }

    public boolean leaveMasterRole(IslandData island, UUID playerId) {
        if (island == null || playerId == null) return false;
        if (isSpawnIsland(island)) return false;
        if (!island.getMasters().contains(playerId)) return false;
        removeMasterFromIsland(island, playerId, false);
        return true;
    }

    private IslandData removeMasterFromIsland(IslandData island, UUID playerId, boolean createReplacementForMastersIfDelete) {
        if (island == null || playerId == null) return island;
        if (isSpawnIsland(island)) return island;
        if (island.getMasters().remove(playerId)) {
            island.setLastActiveAt(System.currentTimeMillis());
            save();
            return island;
        }
        if (!island.getOwner().equals(playerId)) {
            return island;
        }
        if (!island.getMasters().isEmpty()) {
            UUID newMaster = island.getMasters().iterator().next();
            island.getMasters().remove(newMaster);
            IslandData migrated = transferIslandMaster(island, newMaster);
            migrated.getMasters().remove(playerId);
            migrated.setLastActiveAt(System.currentTimeMillis());
            save();
            return migrated;
        }
        deleteIslandData(island, createReplacementForMastersIfDelete);
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
        migrated.setPinnedUpgradeKey(island.getPinnedUpgradeKey());
        migrated.setIslandTimeMode(island.getIslandTimeMode());
        migrated.setIslandWeatherMode(island.getIslandWeatherMode());
        migrated.setIslandSnowMode(island.getIslandSnowMode());
        migrated.setIslandNightVisionEnabled(island.isIslandNightVisionEnabled());
        migrated.setPoints(island.getPoints());
        migrated.setStoredExperience(island.getStoredExperience());
        migrated.setLastActiveAt(island.getLastActiveAt());
        if (island.getIslandSpawn() != null) migrated.setIslandSpawn(island.getIslandSpawn().clone());
        if (island.getWarpLocation() != null) migrated.setWarpLocation(island.getWarpLocation().clone());
        if (island.getCoreLocation() != null) migrated.setCoreLocation(island.getCoreLocation().clone());
        migrated.getMemberBuildAccess().addAll(island.getMemberBuildAccess());
        migrated.getMemberContainerAccess().addAll(island.getMemberContainerAccess());
        migrated.getMemberRedstoneAccess().addAll(island.getMemberRedstoneAccess());
        migrated.getOwners().addAll(island.getOwners());
        migrated.getIslandBanned().addAll(island.getIslandBanned());
        migrated.getUnlockedChunks().addAll(island.getUnlockedChunks());
        migrated.getGeneratedChunks().addAll(island.getGeneratedChunks());
        migrated.getNightVisionChunks().addAll(island.getNightVisionChunks());
        migrated.getProgress().putAll(island.getProgress());
        migrated.getUpgradeTiers().putAll(island.getUpgradeTiers());
        migrated.getCachedBlockCounts().putAll(island.getCachedBlockCounts());
        migrated.getMasters().addAll(island.getMasters());
        migrated.getMasters().add(island.getOwner());
        migrated.getMasters().remove(newMaster);
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
            dst.setGamesEnabled(srcParcel.isGamesEnabled());
            dst.setCtfEnabled(srcParcel.isCtfEnabled());
            dst.setSnowballFightEnabled(srcParcel.isSnowballFightEnabled());
            dst.setTimeMode(srcParcel.getTimeMode());
            dst.setWeatherMode(srcParcel.getWeatherMode());
            dst.setSnowMode(srcParcel.getSnowMode());
            dst.setNightVisionEnabled(srcParcel.isNightVisionEnabled());
            dst.setCountdownDurationSeconds(srcParcel.getCountdownDurationSeconds());
            dst.setCountdownStartAt(srcParcel.getCountdownStartAt());
            dst.setCountdownEndsAt(srcParcel.getCountdownEndsAt());
            dst.setPveEnabled(srcParcel.isPveEnabled());
            dst.setSaleOfferEnabled(srcParcel.isSaleOfferEnabled());
            dst.setSalePrice(srcParcel.getSalePrice());
            dst.setRentOfferEnabled(srcParcel.isRentOfferEnabled());
            dst.setRentPrice(srcParcel.getRentPrice());
            dst.setRentDurationAmount(srcParcel.getRentDurationAmount());
            dst.setRentDurationUnit(srcParcel.getRentDurationUnit());
            dst.setRenter(srcParcel.getRenter());
            dst.setRentUntil(srcParcel.getRentUntil());
            dst.setBounds(srcParcel.getMinX(), srcParcel.getMinY(), srcParcel.getMinZ(), srcParcel.getMaxX(), srcParcel.getMaxY(), srcParcel.getMaxZ());
            if (srcParcel.getSpawn() != null) dst.setSpawn(srcParcel.getSpawn().clone());
            copyAccessSettings(srcParcel.getVisitorSettings(), dst.getVisitorSettings());
            copyAccessSettings(srcParcel.getMemberSettings(), dst.getMemberSettings());
            dst.setMemberAnimalBreed(srcParcel.isMemberAnimalBreed());
            dst.setMemberAnimalKill(srcParcel.isMemberAnimalKill());
            dst.setMemberAnimalKeepTwo(srcParcel.isMemberAnimalKeepTwo());
            dst.setMemberAnimalShear(srcParcel.isMemberAnimalShear());
            migrated.getParcels().put(dst.getChunkKey(), dst);
        }

        islands.remove(island.getOwner());
        activeGrowthBoostIslands.remove(island.getOwner());
        islands.put(newMaster, migrated);
        refreshGrowthBoostTracking(migrated);

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
        pendingMasterInvites.values().forEach(invite -> {
            if (invite.primaryOwner.equals(island.getOwner())) {
                // Java records/fields are immutable if final, but primaryOwner is final. 
                // We must remove and re-add. Actually, to avoid ConcurrentModificationException,
                // let's just clear invitations for this island on transfer or update them safely.
                // For simplicity, we just cancel and remove them.
                if (invite.taskId != -1) Bukkit.getScheduler().cancelTask(invite.taskId);
            }
        });
        pendingMasterInvites.entrySet().removeIf(e -> e.getValue().primaryOwner.equals(island.getOwner()));
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
        dst.setBuckets(src.isBuckets());
        dst.setDecorations(src.isDecorations());
        dst.setVillagers(src.isVillagers());
        dst.setVehicleDestroy(src.isVehicleDestroy());
        dst.setSnowPlace(src.isSnowPlace());
        dst.setSnowBreak(src.isSnowBreak());
        dst.setBannerPlace(src.isBannerPlace());
        dst.setBannerBreak(src.isBannerBreak());
        dst.setTeleport(src.isTeleport());
    }

    public IslandData getIslandAt(Location location) {
        if (location == null || location.getWorld() == null || !skyWorldService.isSkyCityWorld(location.getWorld())) return null;
        if (isInSpawnPlot(location)) return islands.get(SPAWN_ISLAND_OWNER);
        int gridX = gridXFromLocation(location);
        int gridZ = gridZFromLocation(location);
        for (IslandData island : islands.values()) {
            if (island.getGridX() == gridX && island.getGridZ() == gridZ) return island;
        }
        return null;
    }

    public int gridXFromLocation(Location location) {
        if (location == null || location.getWorld() == null || !skyWorldService.isSkyCityWorld(location.getWorld())) return 0;
        if (isInSpawnPlot(location)) return 0;
        return Math.floorDiv(location.getChunk().getX() + 32, ISLAND_CHUNKS);
    }

    public int gridZFromLocation(Location location) {
        if (location == null || location.getWorld() == null || !skyWorldService.isSkyCityWorld(location.getWorld())) return 0;
        if (isInSpawnPlot(location)) return 0;
        return Math.floorDiv(location.getChunk().getZ() + 32, ISLAND_CHUNKS);
    }

    public boolean isInSpawnPlot(Location location) {
        if (location == null || location.getWorld() == null || !skyWorldService.isSkyCityWorld(location.getWorld())) return false;
        Chunk c = location.getChunk();
        return c.getX() >= -32 && c.getX() <= 31 && c.getZ() >= -32 && c.getZ() <= 31;
    }

    public boolean hasBuildAccess(UUID playerId, IslandData island) {
        if (playerId != null && org.bukkit.Bukkit.getOfflinePlayer(playerId).isOp()) return true;
        return island != null && (isIslandOwner(island, playerId) || island.getMemberBuildAccess().contains(playerId));
    }

    public boolean hasContainerAccess(UUID playerId, IslandData island) {
        if (playerId != null && org.bukkit.Bukkit.getOfflinePlayer(playerId).isOp()) return true;
        return island != null && (isIslandOwner(island, playerId)
                || island.getMemberBuildAccess().contains(playerId) || island.getMemberContainerAccess().contains(playerId));
    }

    public boolean hasRedstoneAccess(UUID playerId, IslandData island) {
        if (playerId != null && org.bukkit.Bukkit.getOfflinePlayer(playerId).isOp()) return true;
        return island != null && (isIslandOwner(island, playerId)
                || island.getMemberBuildAccess().contains(playerId) || island.getMemberRedstoneAccess().contains(playerId));
    }

    public boolean hasSettingAccess(UUID playerId, IslandData island, ParcelData parcel, Location location,
                                    Predicate<AccessSettings> predicate,
                                    BiFunction<UUID, IslandData, Boolean> baseAccess) {
        if (playerId == null || island == null || location == null || predicate == null || baseAccess == null) return false;
        return baseAccess.apply(playerId, island)
                || (parcel != null && isParcelOwner(island, parcel, playerId))
                || hasParcelMemberSetting(island, parcel, playerId, predicate)
                || predicate.test(getEffectiveVisitorSettings(island, location));
    }

    public boolean canUseBuildSetting(UUID playerId, Location location, Predicate<AccessSettings> predicate) {
        IslandData island = getIslandAt(location);
        if (island == null) return false;
        return hasSettingAccess(playerId, island, getParcelAt(island, location), location, predicate, this::hasBuildAccess);
    }

    public boolean canUseContainerSetting(UUID playerId, Location location, Predicate<AccessSettings> predicate) {
        IslandData island = getIslandAt(location);
        if (island == null) return false;
        return hasSettingAccess(playerId, island, getParcelAt(island, location), location, predicate, this::hasContainerAccess);
    }

    public boolean canUseRedstoneSetting(UUID playerId, Location location, Predicate<AccessSettings> predicate) {
        IslandData island = getIslandAt(location);
        if (island == null) return false;
        return hasSettingAccess(playerId, island, getParcelAt(island, location), location, predicate, this::hasRedstoneAccess);
    }

    public boolean hasAccess(UUID playerId, IslandData island) { return hasBuildAccess(playerId, island); }

    public boolean hasAnyMemberPermission(IslandData island, UUID target) {
        return island != null && target != null
                && (island.getMemberBuildAccess().contains(target)
                || island.getMemberContainerAccess().contains(target)
                || island.getMemberRedstoneAccess().contains(target));
    }

    public boolean canInviteMaster(IslandData island, UUID actor) {
        if (isSpawnIsland(island)) return false;
        return isIslandMaster(island, actor);
    }

    public boolean canAddOwner(IslandData island, UUID actor) {
        return isIslandOwner(island, actor);
    }

    public boolean canRemoveOwner(IslandData island, UUID actor) {
        if (isSpawnIsland(island)) return isIslandOwner(island, actor);
        return isIslandMaster(island, actor);
    }

    public boolean canManageMembers(IslandData island, UUID actor) {
        return isIslandOwner(island, actor);
    }

    private boolean clearMemberPermissions(IslandData island, UUID target) {
        if (island == null || target == null) return false;
        boolean changed = false;
        changed |= island.getMemberBuildAccess().remove(target);
        changed |= island.getMemberContainerAccess().remove(target);
        changed |= island.getMemberRedstoneAccess().remove(target);
        return changed;
    }

    private boolean removeAdditionalMasterRole(IslandData island, UUID target) {
        return island != null && target != null && island.getMasters().remove(target);
    }

    public boolean grantMemberPermission(IslandData island, UUID target, TrustPermission permission) {
        if (island == null || target == null || permission == null) return false;
        if (isPrimaryMaster(island, target)) return false;
        boolean changed = false;
        changed |= island.getOwners().remove(target);
        changed |= removeAdditionalMasterRole(island, target);
        switch (permission) {
            case BUILD -> changed = island.getMemberBuildAccess().add(target);
            case CONTAINER -> changed = island.getMemberContainerAccess().add(target);
            case REDSTONE -> changed = island.getMemberRedstoneAccess().add(target);
            case ALL -> {
                changed |= island.getMemberBuildAccess().add(target);
                changed |= island.getMemberContainerAccess().add(target);
                changed |= island.getMemberRedstoneAccess().add(target);
            }
        }
        if (changed) {
            island.setLastActiveAt(System.currentTimeMillis());
            save();
        }
        return changed;
    }

    public boolean revokeMemberPermission(IslandData island, UUID target, TrustPermission permission) {
        if (island == null || target == null || permission == null) return false;
        boolean changed = false;
        switch (permission) {
            case BUILD -> changed = island.getMemberBuildAccess().remove(target);
            case CONTAINER -> changed = island.getMemberContainerAccess().remove(target);
            case REDSTONE -> changed = island.getMemberRedstoneAccess().remove(target);
            case ALL -> {
                changed |= island.getMemberBuildAccess().remove(target);
                changed |= island.getMemberContainerAccess().remove(target);
                changed |= island.getMemberRedstoneAccess().remove(target);
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

    public boolean grantOwnerRole(IslandData island, UUID actor, UUID target) {
        if (island == null || actor == null || target == null) return false;
        if (!canAddOwner(island, actor)) return false;
        if (isPrimaryMaster(island, target)) return false;
        boolean changed = island.getOwners().add(target);
        changed |= removeAdditionalMasterRole(island, target);
        changed |= clearMemberPermissions(island, target);
        if (changed) {
            island.getIslandBanned().remove(target);
            island.setLastActiveAt(System.currentTimeMillis());
            save();
        }
        return changed;
    }

    public boolean revokeOwnerRole(IslandData island, UUID actor, UUID target) {
        if (island == null || actor == null || target == null) return false;
        if (!actor.equals(target) && !canRemoveOwner(island, actor)) return false;
        boolean changed = island.getOwners().remove(target);
        if (changed) {
            island.setLastActiveAt(System.currentTimeMillis());
            save();
        }
        return changed;
    }

    public boolean isChunkUnlocked(IslandData island, Location location) {
        if (isSpawnIsland(island)) return true;
        return isChunkUnlocked(island, relativeChunkX(island, location.getChunk().getX()), relativeChunkZ(island, location.getChunk().getZ()));
    }

    public boolean isChunkUnlocked(IslandData island, int relX, int relZ) {
        if (isSpawnIsland(island)) return true;
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
        if (!isSpawnIsland(island) && island.getAvailableChunkUnlocks() <= 0) return ChunkUnlockResult.NO_UNLOCKS_LEFT;

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
        if (!isSpawnIsland(island)) {
            island.setAvailableChunkUnlocks(island.getAvailableChunkUnlocks() - 1);
        }
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
            if (neighborIsland != null
                    && (isIslandMaster(neighborIsland, approver)
                    || (isSpawnIsland(neighborIsland) && isIslandOwner(neighborIsland, approver)))) {
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
        approvers.addAll(neighborIsland.getMasters());
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

    public ParcelData getParcelByKey(IslandData island, String parcelKey) {
        if (island == null || parcelKey == null || parcelKey.isBlank()) return null;
        return island.getParcels().get(parcelKey);
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

    public ParcelData createParcelCuboidFromSelection(IslandData island, UUID owner, Location pos1, Location pos2, Location spawnLocation) {
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
                pos2.getBlockZ(),
                spawnLocation
        );
    }

    public ParcelData createParcelCuboid(IslandData island, UUID owner, int x1, int y1, int z1, int x2, int y2, int z2) {
        return createParcelCuboid(island, owner, x1, y1, z1, x2, y2, z2, null);
    }

    public ParcelData createParcelCuboid(IslandData island, UUID owner, int x1, int y1, int z1, int x2, int y2, int z2, Location spawnLocation) {
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
        Location defaultSpawn = new Location(skyWorldService.getWorld(), (minX + maxX) / 2.0 + 0.5, maxY + 1.0, (minZ + maxZ) / 2.0 + 0.5);
        if (spawnLocation != null
                && skyWorldService.isSkyCityWorld(spawnLocation.getWorld())
                && spawnLocation.getBlockX() >= minX && spawnLocation.getBlockX() <= maxX
                && spawnLocation.getBlockY() >= minY && spawnLocation.getBlockY() <= maxY + 1
                && spawnLocation.getBlockZ() >= minZ && spawnLocation.getBlockZ() <= maxZ) {
            parcel.setSpawn(spawnLocation.clone());
        } else {
            parcel.setSpawn(defaultSpawn);
        }
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

    public boolean isParcelMember(IslandData island, ParcelData parcel, UUID playerId) {
        return island != null && parcel != null && playerId != null && !isParcelOwner(island, parcel, playerId) && parcel.getUsers().contains(playerId);
    }

    public boolean isParcelUser(IslandData island, ParcelData parcel, UUID playerId) {
        expireParcelRentalIfNeeded(island, parcel);
        return isParcelOwner(island, parcel, playerId) || (parcel != null && parcel.getUsers().contains(playerId));
    }

    public void setCheckpointPlateYaw(IslandData island, Location location, float yaw) {
        if (island == null || location == null) return;
        island.getCheckpointPlateYaw().put(blockKey(location), normalizeYaw(yaw));
        island.setLastActiveAt(System.currentTimeMillis());
        save();
    }

    public Float getCheckpointPlateYaw(IslandData island, Location location) {
        if (island == null || location == null) return null;
        return island.getCheckpointPlateYaw().get(blockKey(location));
    }

    public void removeCheckpointPlateYaw(IslandData island, Location location) {
        if (island == null || location == null) return;
        if (island.getCheckpointPlateYaw().remove(blockKey(location)) != null) {
            island.setLastActiveAt(System.currentTimeMillis());
            save();
        }
    }

    public boolean hasParcelMemberSetting(IslandData island, ParcelData parcel, UUID playerId, java.util.function.Predicate<AccessSettings> predicate) {
        return isParcelMember(island, parcel, playerId) && parcel != null && predicate != null && predicate.test(parcel.getMemberSettings());
    }

    public int countAnimalsInParcelByType(ParcelData parcel, EntityType type) {
        if (parcel == null || type == null || skyWorldService.getWorld() == null) return 0;
        int count = 0;
        for (Entity entity : skyWorldService.getWorld().getEntities()) {
            if (!(entity instanceof Animals) || entity.getType() != type) continue;
            Location location = entity.getLocation();
            if (parcel.contains(location.getBlockX(), location.getBlockY(), location.getBlockZ())) {
                count++;
            }
        }
        return count;
    }

    public boolean isParcelRentalActive(IslandData island, ParcelData parcel) {
        expireParcelRentalIfNeeded(island, parcel);
        return island != null && parcel != null && parcel.getRenter() != null && parcel.getRentUntil() > System.currentTimeMillis();
    }

    public boolean isParcelRenter(IslandData island, ParcelData parcel, UUID playerId) {
        expireParcelRentalIfNeeded(island, parcel);
        return island != null && parcel != null && playerId != null && playerId.equals(parcel.getRenter()) && parcel.getRentUntil() > System.currentTimeMillis();
    }

    public boolean configureParcelSaleOffer(IslandData island, ParcelData parcel, UUID actor, long price, boolean enabled) {
        if (!isParcelOwner(island, parcel, actor)) return false;
        parcel.setSalePrice(Math.max(0L, price));
        parcel.setSaleOfferEnabled(enabled && parcel.getSalePrice() > 0L);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean configureParcelPaymentType(IslandData island, ParcelData parcel, UUID actor, ParcelData.MarketPaymentType paymentType) {
        if (!isParcelOwner(island, parcel, actor) || paymentType == null) return false;
        parcel.setPaymentType(paymentType);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean configureParcelRentOffer(IslandData island, ParcelData parcel, UUID actor, long price, int durationAmount, ParcelData.RentDurationUnit durationUnit, boolean enabled) {
        if (!isParcelOwner(island, parcel, actor)) return false;
        parcel.setRentPrice(Math.max(0L, price));
        parcel.setRentDurationAmount(Math.max(0, durationAmount));
        parcel.setRentDurationUnit(durationUnit);
        parcel.setRentOfferEnabled(enabled && parcel.getRentPrice() > 0L && parcel.getRentDurationAmount() > 0);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean isParcelSoldToExternalOwner(IslandData island, ParcelData parcel) {
        return island != null
                && parcel != null
                && parcel.getLastSaleBuyer() != null
                && parcel.getOwners().contains(parcel.getLastSaleBuyer())
                && !parcel.getOwners().contains(island.getOwner());
    }

    public boolean clearParcelRentState(IslandData island, ParcelData parcel, UUID actor) {
        if (!isParcelOwner(island, parcel, actor) || island == null || parcel == null) return false;
        UUID renter = parcel.getRenter();
        if (renter != null) {
            parcel.getUsers().remove(renter);
        }
        parcel.setRenter(null);
        parcel.setRentUntil(0L);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean manageParcelRent(IslandData island, ParcelData parcel, UUID actor, boolean refund) {
        if (!isIslandOwner(island, actor) || island == null || parcel == null || parcel.getRenter() == null) return false;
        UUID renter = parcel.getRenter();
        if (refund) {
            refundParcelMarketPrice(parcel.getLastRentPaymentType(), renter, parcel.getLastRentPrice());
        }
        parcel.getUsers().remove(renter);
        parcel.setRenter(null);
        parcel.setRentUntil(0L);
        parcel.setLastRentBuyer(null);
        parcel.setLastRentPrice(0L);
        parcel.setLastRentPaymentType(ParcelData.MarketPaymentType.EXPERIENCE);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean manageParcelSale(IslandData island, ParcelData parcel, UUID actor, boolean refund) {
        if (!isIslandOwner(island, actor) || island == null || parcel == null || !isParcelSoldToExternalOwner(island, parcel)) return false;
        UUID buyer = parcel.getLastSaleBuyer();
        if (refund && buyer != null) {
            refundParcelMarketPrice(parcel.getLastSalePaymentType(), buyer, parcel.getLastSalePrice());
        }
        parcel.getOwners().clear();
        parcel.getUsers().clear();
        parcel.getOwners().add(island.getOwner());
        parcel.setRenter(null);
        parcel.setRentUntil(0L);
        parcel.setSaleOfferEnabled(false);
        parcel.setRentOfferEnabled(false);
        parcel.setLastSaleBuyer(null);
        parcel.setLastSalePrice(0L);
        parcel.setLastSalePaymentType(ParcelData.MarketPaymentType.EXPERIENCE);
        parcel.setLastRentBuyer(null);
        parcel.setLastRentPrice(0L);
        parcel.setLastRentPaymentType(ParcelData.MarketPaymentType.EXPERIENCE);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public enum ParcelMarketResult {
        SUCCESS,
        NO_PARCEL,
        NOT_AVAILABLE,
        NOT_AUTHORIZED,
        NO_BUYER_ISLAND,
        NOT_ENOUGH_EXPERIENCE,
        NOT_ENOUGH_MONEY,
        VAULT_UNAVAILABLE,
        ALREADY_RENTED,
        INVALID_CONFIGURATION
    }

    public ParcelMarketResult buyParcel(IslandData island, ParcelData parcel, UUID buyerId) {
        if (island == null || parcel == null || buyerId == null) return ParcelMarketResult.NO_PARCEL;
        expireParcelRentalIfNeeded(island, parcel);
        if (!parcel.isSaleOfferEnabled() || parcel.getSalePrice() <= 0L) return ParcelMarketResult.NOT_AVAILABLE;
        if (isIslandOwner(island, buyerId)) return ParcelMarketResult.NOT_AUTHORIZED;
        IslandData buyerIsland = getIsland(buyerId).orElse(null);
        if (buyerIsland == null) return ParcelMarketResult.NO_BUYER_ISLAND;
        if (buyerIsland.getOwner().equals(island.getOwner())) return ParcelMarketResult.NOT_AUTHORIZED;
        ParcelMarketResult paymentResult = chargeParcelMarketPrice(parcel, buyerIsland, buyerId, parcel.getSalePrice());
        if (paymentResult != ParcelMarketResult.SUCCESS) return paymentResult;
        rewardParcelMarketPrice(parcel, island, parcel.getSalePrice());
        UUID renter = parcel.getRenter();
        if (renter != null) {
            parcel.getUsers().remove(renter);
        }
        parcel.getOwners().clear();
        parcel.getUsers().clear();
        parcel.getOwners().add(buyerId);
        parcel.setRenter(null);
        parcel.setRentUntil(0L);
        parcel.setLastSaleBuyer(buyerId);
        parcel.setLastSalePrice(parcel.getSalePrice());
        parcel.setLastSalePaymentType(parcel.getPaymentType());
        parcel.setLastRentBuyer(null);
        parcel.setLastRentPrice(0L);
        parcel.setLastRentPaymentType(ParcelData.MarketPaymentType.EXPERIENCE);
        parcel.setSaleOfferEnabled(false);
        parcel.setRentOfferEnabled(false);
        island.setLastActiveAt(System.currentTimeMillis());
        buyerIsland.setLastActiveAt(System.currentTimeMillis());
        save();
        return ParcelMarketResult.SUCCESS;
    }

    public ParcelMarketResult rentParcel(IslandData island, ParcelData parcel, UUID renterId) {
        if (island == null || parcel == null || renterId == null) return ParcelMarketResult.NO_PARCEL;
        expireParcelRentalIfNeeded(island, parcel);
        if (!parcel.isRentOfferEnabled()) return ParcelMarketResult.NOT_AVAILABLE;
        if (parcel.getRentPrice() <= 0L || parcel.getRentDurationAmount() <= 0) return ParcelMarketResult.INVALID_CONFIGURATION;
        if (isIslandOwner(island, renterId)) return ParcelMarketResult.NOT_AUTHORIZED;
        if (parcel.getRenter() != null && !renterId.equals(parcel.getRenter()) && parcel.getRentUntil() > System.currentTimeMillis()) {
            return ParcelMarketResult.ALREADY_RENTED;
        }
        IslandData renterIsland = getIsland(renterId).orElse(null);
        if (renterIsland == null) return ParcelMarketResult.NO_BUYER_ISLAND;
        if (renterIsland.getOwner().equals(island.getOwner())) return ParcelMarketResult.NOT_AUTHORIZED;
        ParcelMarketResult paymentResult = chargeParcelMarketPrice(parcel, renterIsland, renterId, parcel.getRentPrice());
        if (paymentResult != ParcelMarketResult.SUCCESS) return paymentResult;
        rewardParcelMarketPrice(parcel, island, parcel.getRentPrice());
        long base = parcel.getRenter() != null && renterId.equals(parcel.getRenter()) && parcel.getRentUntil() > System.currentTimeMillis()
                ? parcel.getRentUntil()
                : System.currentTimeMillis();
        long extension = rentDurationMillis(parcel);
        boolean sameRenter = parcel.getLastRentBuyer() != null && renterId.equals(parcel.getLastRentBuyer());
        parcel.setRenter(renterId);
        parcel.setRentUntil(base + extension);
        parcel.getUsers().add(renterId);
        parcel.setLastRentBuyer(renterId);
        parcel.setLastRentPrice(sameRenter ? parcel.getLastRentPrice() + parcel.getRentPrice() : parcel.getRentPrice());
        parcel.setLastRentPaymentType(parcel.getPaymentType());
        island.setLastActiveAt(System.currentTimeMillis());
        renterIsland.setLastActiveAt(System.currentTimeMillis());
        save();
        return ParcelMarketResult.SUCCESS;
    }

    public boolean expireParcelRentalIfNeeded(IslandData island, ParcelData parcel) {
        if (island == null || parcel == null) return false;
        if (parcel.getRenter() == null || parcel.getRentUntil() <= 0L) return false;
        if (parcel.getRentUntil() > System.currentTimeMillis()) return false;
        parcel.getUsers().remove(parcel.getRenter());
        parcel.setRenter(null);
        parcel.setRentUntil(0L);
        parcel.setLastRentBuyer(null);
        parcel.setLastRentPrice(0L);
        parcel.setLastRentPaymentType(ParcelData.MarketPaymentType.EXPERIENCE);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    private ParcelMarketResult chargeParcelMarketPrice(ParcelData parcel, IslandData buyerIsland, UUID playerId, long price) {
        if (parcel == null || playerId == null || price <= 0L) return ParcelMarketResult.INVALID_CONFIGURATION;
        return switch (parcel.getPaymentType()) {
            case EXPERIENCE -> {
                if (buyerIsland == null) {
                    yield ParcelMarketResult.NO_BUYER_ISLAND;
                }
                if (buyerIsland.getStoredExperience() < price) {
                    yield ParcelMarketResult.NOT_ENOUGH_EXPERIENCE;
                }
                yield spendStoredExperience(buyerIsland, price) ? ParcelMarketResult.SUCCESS : ParcelMarketResult.NOT_ENOUGH_EXPERIENCE;
            }
            case VAULT -> {
                if (!plugin.hasVaultEconomy()) {
                    yield ParcelMarketResult.VAULT_UNAVAILABLE;
                }
                if (plugin.getVaultBalance(playerId) < price) {
                    yield ParcelMarketResult.NOT_ENOUGH_MONEY;
                }
                yield plugin.withdrawVault(playerId, price) ? ParcelMarketResult.SUCCESS : ParcelMarketResult.NOT_ENOUGH_MONEY;
            }
        };
    }

    private void rewardParcelMarketPrice(ParcelData parcel, IslandData island, long price) {
        if (parcel == null || island == null || price <= 0L) return;
        switch (parcel.getPaymentType()) {
            case EXPERIENCE -> island.addStoredExperience(price);
            case VAULT -> plugin.depositVault(island.getOwner(), price);
        }
    }

    private void refundParcelMarketPrice(ParcelData.MarketPaymentType paymentType, UUID playerId, long price) {
        if (playerId == null || price <= 0L) return;
        switch (paymentType == null ? ParcelData.MarketPaymentType.EXPERIENCE : paymentType) {
            case EXPERIENCE -> {
                IslandData island = getIsland(playerId).orElse(null);
                if (island != null) {
                    island.addStoredExperience(price);
                }
            }
            case VAULT -> plugin.depositVault(playerId, price);
        }
    }

    public boolean grantParcelRole(IslandData island, ParcelData parcel, UUID actor, UUID target, ParcelRole role) {
        if (!isParcelOwner(island, parcel, actor)) return false;
        boolean changed = switch (role) {
            case OWNER -> parcel.getOwners().add(target);
            case MEMBER -> parcel.getUsers().add(target);
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
            case MEMBER -> parcel.getUsers().remove(target);
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
        parcel.setCombatMode(enabled ? ParcelData.CombatMode.PVP : ParcelData.CombatMode.NONE);
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
        return validateParcelPveDetails(island, parcel);
    }

    public boolean isParcelPveActive(IslandData island, ParcelData parcel) {
        String key = getParcelPveKey(island, parcel);
        return key != null && activePveZones.containsKey(key);
    }

    public Optional<PveRuntimeSnapshot> getParcelPveSnapshot(IslandData island, ParcelData parcel, UUID viewerId) {
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
            lines.put(index + ". Spawn", marker.familyName() + " x" + alive);
            index++;
            if (index > 10) break;
        }
        int pendingRewardLevels = viewerId == null ? 0 : runtime.pendingRewards.getOrDefault(viewerId, 0);
        return Optional.of(new PveRuntimeSnapshot(
                key,
                getParcelDisplayName(parcel),
                runtime.currentWave,
                runtime.requiredWaves,
                runtime.activeMobIds.size(),
                runtime.participants.size(),
                pendingRewardLevels,
                runtime.objectiveText(),
                lines
        ));
    }

    public boolean enterParcelPve(Player player, IslandData island, ParcelData parcel) {
        if (player == null || island == null || parcel == null || !parcel.isPveEnabled()) return false;
        String key = getParcelPveKey(island, parcel);
        if (key == null) return false;
        PveZoneRuntime runtime = activePveZones.get(key);
        if (runtime == null) {
            runtime = buildPveZoneRuntime(island, parcel).orElse(null);
            if (runtime == null) {
                player.sendMessage(ChatColor.RED + "Diese PvE-Zone ist ung\u00fcltig. Startzone/Marker pr\u00fcfen.");
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
            resetPveZone(runtime, ChatColor.RED + player.getName() + ChatColor.GRAY + " hat die PvE-Zone verlassen. Alles wurde zur\u00fcckgesetzt.");
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
        PveMobArchetype archetype = runtime.mobArchetypes.remove(entity.getUniqueId());
        runtime.mobLevels.remove(entity.getUniqueId());
        runtime.mobLastReachableAt.remove(entity.getUniqueId());
        runtime.mobLastRangedHitAt.remove(entity.getUniqueId());
        runtime.invalidMobIds.remove(entity.getUniqueId());
        if (runtime.objectiveMode == PveObjectiveMode.SPARE_TYPE && archetype != null && archetype.key().equals(runtime.objectiveArchetypeKey)) {
            resetPveZone(runtime, ChatColor.RED + "Aufgabe fehlgeschlagen: " + runtime.objectivePluralName + " durften nicht get\u00f6tet werden.");
            return true;
        }
        if (!valid) {
            broadcastPveZone(runtime, ChatColor.RED + "Ung\u00fcltiger Kill: Mob war au\u00dfer Reichweite oder ohne Pfad zum Spieler.");
        }
        if (isPveObjectiveComplete(runtime)) {
            completePveWave(runtime);
            return true;
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
        return isParcelOwner(island, parcel, playerId)
                || hasParcelMemberSetting(island, parcel, playerId, AccessSettings::isTeleport)
                || parcel.getVisitorSettings().isTeleport();
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

    public String formatParcelRentRemaining(ParcelData parcel) {
        if (parcel == null || parcel.getRentUntil() <= System.currentTimeMillis()) return "-";
        long millis = Math.max(0L, parcel.getRentUntil() - System.currentTimeMillis());
        long days = millis / 86_400_000L;
        long hours = (millis / 3_600_000L) % 24L;
        if (days > 0L) return days + "d " + hours + "h";
        long minutes = (millis / 60_000L) % 60L;
        return hours + "h " + minutes + "m";
    }

    public String formatParcelRentOffer(ParcelData parcel) {
        if (parcel == null || parcel.getRentDurationAmount() <= 0) return "-";
        String suffix = switch (parcel.getRentDurationUnit()) {
            case MINUTES -> "min";
            case HOURS -> "h";
            case DAYS -> "d";
        };
        return parcel.getRentDurationAmount() + " " + suffix;
    }

    public String formatParcelPrice(ParcelData parcel, long amount) {
        if (parcel == null) return amount + " XP";
        return switch (parcel.getPaymentType()) {
            case EXPERIENCE -> amount + " XP";
            case VAULT -> amount + " " + plugin.getVaultCurrencyName(amount);
        };
    }

    public String formatParcelPriceForType(ParcelData.MarketPaymentType paymentType, long amount) {
        return switch (paymentType == null ? ParcelData.MarketPaymentType.EXPERIENCE : paymentType) {
            case EXPERIENCE -> amount + " XP";
            case VAULT -> amount + " " + plugin.getVaultCurrencyName(amount);
        };
    }

    public String parcelPaymentTypeLabel(ParcelData parcel) {
        if (parcel == null) return "XP";
        return switch (parcel.getPaymentType()) {
            case EXPERIENCE -> "XP";
            case VAULT -> plugin.getVaultCurrencyName(2.0D);
        };
    }

    public boolean isParcelVaultAvailable() {
        return plugin.hasVaultEconomy();
    }

    private long rentDurationMillis(ParcelData parcel) {
        if (parcel == null || parcel.getRentDurationAmount() <= 0) return 0L;
        long amount = Math.max(1L, parcel.getRentDurationAmount());
        return switch (parcel.getRentDurationUnit()) {
            case MINUTES -> amount * 60_000L;
            case HOURS -> amount * 3_600_000L;
            case DAYS -> amount * 86_400_000L;
        };
    }

    public boolean ensureChunkTemplateGenerated(IslandData island, int relChunkX, int relChunkZ) {
        if (relChunkX < 0 || relChunkX >= ISLAND_CHUNKS || relChunkZ < 0 || relChunkZ >= ISLAND_CHUNKS) return false;
        String key = chunkKey(relChunkX, relChunkZ);
        ensureIslandChunkBarrierFloor(island, relChunkX, relChunkZ);
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
        ensureChunkBarrierFloor(world, baseX, baseZ);
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

    private void ensureChunkBarrierFloor(World world, int baseX, int baseZ) {
        int minY = world.getMinHeight();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Block block = world.getBlockAt(baseX + x, minY, baseZ + z);
                if (block.getType() != Material.BARRIER) {
                    block.setType(Material.BARRIER, false);
                }
            }
        }
    }

    private void ensureIslandChunkBarrierFloor(IslandData island, int relChunkX, int relChunkZ) {
        World world = skyWorldService.getWorld();
        if (world == null) return;
        int worldChunkX = plotMinChunkX(island.getGridX()) + relChunkX;
        int worldChunkZ = plotMinChunkZ(island.getGridZ()) + relChunkZ;
        ensureChunkBarrierFloor(world, worldChunkX << 4, worldChunkZ << 4);
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

        boolean flowerBiome = biome == Biome.FLOWER_FOREST
                || biome == Biome.MEADOW
                || biome == Biome.CHERRY_GROVE
                || biome == Biome.PLAINS
                || biome == Biome.SUNFLOWER_PLAINS
                || biome == Biome.FOREST;
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
            Biome biome = def.biome();
            if (biome == Biome.FROZEN_OCEAN
                    || biome == Biome.DEEP_FROZEN_OCEAN
                    || biome == Biome.FROZEN_RIVER
                    || biome == Biome.SNOWY_PLAINS
                    || biome == Biome.ICE_SPIKES
                    || biome == Biome.SNOWY_TAIGA
                    || biome == Biome.SNOWY_BEACH
                    || biome == Biome.SNOWY_SLOPES
                    || biome == Biome.FROZEN_PEAKS
                    || biome == Biome.JAGGED_PEAKS
                    || biome == Biome.GROVE) {
                return biome;
            }
            return Biome.SNOWY_PLAINS;
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
        return biome == Biome.FROZEN_OCEAN
                || biome == Biome.DEEP_FROZEN_OCEAN
                || biome == Biome.FROZEN_RIVER
                || biome == Biome.SNOWY_PLAINS
                || biome == Biome.ICE_SPIKES
                || biome == Biome.SNOWY_TAIGA
                || biome == Biome.SNOWY_BEACH
                || biome == Biome.SNOWY_SLOPES
                || biome == Biome.FROZEN_PEAKS
                || biome == Biome.JAGGED_PEAKS
                || biome == Biome.GROVE;
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

    public boolean setBiomeForParcel(IslandData island, ParcelData parcel, Biome biome) {
        World world = skyWorldService.getWorld();
        if (island == null || parcel == null || biome == null || world == null || !parcel.hasBounds()) return false;
        boolean changed = false;
        for (int x = parcel.getMinX(); x <= parcel.getMaxX(); x++) {
            for (int z = parcel.getMinZ(); z <= parcel.getMaxZ(); z++) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y += 4) {
                    world.setBiome(x, y, z, biome);
                }
                changed = true;
            }
        }
        if (!changed) return false;
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public int clearWeatherSnowForIsland(IslandData island) {
        World world = skyWorldService.getWorld();
        if (island == null || world == null) return 0;
        int cleared = 0;
        for (int relX = 0; relX < ISLAND_CHUNKS; relX++) {
            for (int relZ = 0; relZ < ISLAND_CHUNKS; relZ++) {
                if (!isChunkUnlocked(island, relX, relZ)) continue;
                int worldChunkX = plotMinChunkX(island.getGridX()) + relX;
                int worldChunkZ = plotMinChunkZ(island.getGridZ()) + relZ;
                cleared += clearWeatherSnowInChunk(world, worldChunkX, worldChunkZ);
            }
        }
        if (cleared > 0) {
            island.setLastActiveAt(System.currentTimeMillis());
            save();
        }
        return cleared;
    }

    public int clearWeatherSnowForParcel(IslandData island, ParcelData parcel) {
        World world = skyWorldService.getWorld();
        if (island == null || parcel == null || world == null || !parcel.hasBounds()) return 0;
        int cleared = 0;
        for (int x = parcel.getMinX(); x <= parcel.getMaxX(); x++) {
            for (int z = parcel.getMinZ(); z <= parcel.getMaxZ(); z++) {
                for (int y = parcel.getMinY(); y <= parcel.getMaxY(); y++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.SNOW) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                        cleared++;
                    }
                }
            }
        }
        if (cleared > 0) {
            island.setLastActiveAt(System.currentTimeMillis());
            save();
        }
        return cleared;
    }

    private int clearWeatherSnowInChunk(World world, int worldChunkX, int worldChunkZ) {
        if (world == null) return 0;
        int cleared = 0;
        int baseX = worldChunkX << 4;
        int baseZ = worldChunkZ << 4;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = world.getMaxHeight() - 1; y >= world.getMinHeight(); y--) {
                    Block block = world.getBlockAt(baseX + x, y, baseZ + z);
                    if (block.getType() == Material.SNOW) {
                        block.setType(Material.AIR, false);
                        cleared++;
                    }
                }
            }
        }
        return cleared;
    }

    public IslandTimeMode getIslandTimeMode(IslandData island) {
        if (island == null) return IslandTimeMode.NORMAL;
        return IslandTimeMode.from(island.getIslandTimeMode());
    }

    public IslandWeatherMode getIslandWeatherMode(IslandData island) {
        if (island == null) return IslandWeatherMode.NORMAL;
        return IslandWeatherMode.from(island.getIslandWeatherMode());
    }

    public SnowWeatherMode getIslandSnowMode(IslandData island) {
        if (island == null) return SnowWeatherMode.NORMAL;
        return SnowWeatherMode.from(island.getIslandSnowMode());
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

    public void setIslandWeatherMode(IslandData island, IslandWeatherMode mode) {
        if (island == null || mode == null) return;
        island.setIslandWeatherMode(mode.name());
        island.setLastActiveAt(System.currentTimeMillis());
        save();
    }

    public void setIslandSnowMode(IslandData island, SnowWeatherMode mode) {
        if (island == null || mode == null) return;
        island.setIslandSnowMode(mode.name());
        island.setLastActiveAt(System.currentTimeMillis());
        save();
    }

    public IslandTimeMode getParcelTimeMode(ParcelData parcel) {
        if (parcel == null) return IslandTimeMode.NORMAL;
        return IslandTimeMode.from(parcel.getTimeMode());
    }

    public boolean setParcelTimeMode(IslandData island, ParcelData parcel, UUID actor, IslandTimeMode mode) {
        if (!isParcelOwner(island, parcel, actor) || mode == null) return false;
        if (getParcelTimeMode(parcel) == mode) return false;
        parcel.setTimeMode(mode.name());
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public IslandWeatherMode getParcelWeatherMode(ParcelData parcel) {
        if (parcel == null) return IslandWeatherMode.NORMAL;
        return IslandWeatherMode.from(parcel.getWeatherMode());
    }

    public SnowWeatherMode getParcelSnowMode(ParcelData parcel) {
        if (parcel == null) return SnowWeatherMode.NORMAL;
        return SnowWeatherMode.from(parcel.getSnowMode());
    }

    public boolean setParcelWeatherMode(IslandData island, ParcelData parcel, UUID actor, IslandWeatherMode mode) {
        if (!isParcelOwner(island, parcel, actor) || mode == null) return false;
        if (getParcelWeatherMode(parcel) == mode) return false;
        parcel.setWeatherMode(mode.name());
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean setParcelSnowMode(IslandData island, ParcelData parcel, UUID actor, SnowWeatherMode mode) {
        if (!isParcelOwner(island, parcel, actor) || mode == null) return false;
        if (getParcelSnowMode(parcel) == mode) return false;
        parcel.setSnowMode(mode.name());
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
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

    public long getWeatherModeChangeCost() {
        return WEATHER_MODE_CHANGE_COST;
    }

    public long getNightVisionCost(boolean islandWide) {
        return islandWide ? NIGHT_VISION_COST_ISLAND : NIGHT_VISION_COST_CHUNK;
    }

    public boolean isIslandNightVisionEnabled(IslandData island) {
        return island != null && island.isIslandNightVisionEnabled();
    }

    public boolean isChunkNightVisionEnabled(IslandData island, int relChunkX, int relChunkZ) {
        return island != null && island.getNightVisionChunks().contains(chunkKey(relChunkX, relChunkZ));
    }

    public boolean isParcelNightVisionEnabled(ParcelData parcel) {
        return parcel != null && parcel.isNightVisionEnabled();
    }

    public boolean hasNightVision(IslandData island, int relChunkX, int relChunkZ) {
        return isIslandNightVisionEnabled(island) || isChunkNightVisionEnabled(island, relChunkX, relChunkZ);
    }

    public boolean buyChunkNightVision(IslandData island, int relChunkX, int relChunkZ) {
        if (island == null || !isChunkUnlocked(island, relChunkX, relChunkZ)) return false;
        if (isChunkNightVisionEnabled(island, relChunkX, relChunkZ)) return false;
        long cost = getNightVisionCost(false);
        if (!isSpawnIsland(island) && !spendStoredExperience(island, cost)) return false;
        island.getNightVisionChunks().add(chunkKey(relChunkX, relChunkZ));
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean buyParcelNightVision(IslandData island, ParcelData parcel, UUID actor) {
        if (!isParcelOwner(island, parcel, actor) || parcel == null || parcel.isNightVisionEnabled()) return false;
        if (!isSpawnIsland(island) && !spendStoredExperience(island, getNightVisionCost(false))) return false;
        parcel.setNightVisionEnabled(true);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean disableParcelNightVision(IslandData island, ParcelData parcel, UUID actor) {
        if (!isParcelOwner(island, parcel, actor) || parcel == null || !parcel.isNightVisionEnabled()) return false;
        parcel.setNightVisionEnabled(false);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean setParcelGames(IslandData island, ParcelData parcel, UUID actor, boolean enabled) {
        if (!isParcelOwner(island, parcel, actor)) return false;
        if (parcel.isGamesEnabled() == enabled) return false;
        parcel.setCombatMode(enabled ? ParcelData.CombatMode.GAMES : ParcelData.CombatMode.NONE);
        if (!enabled) {
            parcel.setCtfEnabled(false);
            parcel.setSnowballFightEnabled(false);
        }
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean setParcelSnowballFightEnabled(IslandData island, ParcelData parcel, UUID actor, boolean enabled) {
        if (!isParcelOwner(island, parcel, actor)) return false;
        if (enabled && !parcel.isGamesEnabled()) return false;
        if (parcel.isSnowballFightEnabled() == enabled) return false;
        parcel.setSnowballFightEnabled(enabled);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean setParcelCtfEnabled(IslandData island, ParcelData parcel, UUID actor, boolean enabled) {
        if (!isParcelOwner(island, parcel, actor)) return false;
        if (enabled && !parcel.isGamesEnabled()) return false;
        if (parcel.isCtfEnabled() == enabled) return false;
        parcel.setCtfEnabled(enabled);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean setParcelCountdownDurationSeconds(IslandData island, ParcelData parcel, UUID actor, int seconds) {
        if (!isParcelOwner(island, parcel, actor)) return false;
        int clamped = Math.max(30, Math.min(7200, seconds));
        if (parcel.getCountdownDurationSeconds() == clamped) return false;
        parcel.setCountdownDurationSeconds(clamped);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean startParcelCountdown(IslandData island, ParcelData parcel, UUID actor) {
        if (!isParcelOwner(island, parcel, actor)) return false;
        long now = System.currentTimeMillis();
        long startAt = now + 3000L;
        long endsAt = startAt + parcel.getCountdownDurationSeconds() * 1000L;
        parcel.setCountdownStartAt(startAt);
        parcel.setCountdownEndsAt(endsAt);
        island.setLastActiveAt(now);
        save();
        return true;
    }

    public boolean stopParcelCountdown(IslandData island, ParcelData parcel, UUID actor) {
        if (!isParcelOwner(island, parcel, actor)) return false;
        if (parcel.getCountdownStartAt() <= 0L && parcel.getCountdownEndsAt() <= 0L) return false;
        clearParcelCountdown(parcel);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean finishParcelCountdown(IslandData island, ParcelData parcel) {
        if (island == null || parcel == null) return false;
        if (parcel.getCountdownStartAt() <= 0L && parcel.getCountdownEndsAt() <= 0L) return false;
        clearParcelCountdown(parcel);
        save();
        return true;
    }

    private void clearParcelCountdown(ParcelData parcel) {
        if (parcel == null) return;
        parcel.setCountdownStartAt(0L);
        parcel.setCountdownEndsAt(0L);
    }

    public boolean setParcelPvpCompassEnabled(IslandData island, ParcelData parcel, UUID actor, boolean enabled) {
        if (!isParcelOwner(island, parcel, actor)) return false;
        if (parcel.isPvpCompassEnabled() == enabled) return false;
        parcel.setPvpCompassEnabled(enabled);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean disableChunkNightVision(IslandData island, int relChunkX, int relChunkZ) {
        if (island == null) return false;
        boolean changed = island.getNightVisionChunks().remove(chunkKey(relChunkX, relChunkZ));
        if (!changed) return false;
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean buyIslandNightVision(IslandData island) {
        if (island == null || island.isIslandNightVisionEnabled()) return false;
        long cost = getNightVisionCost(true);
        if (!isSpawnIsland(island) && !spendStoredExperience(island, cost)) return false;
        island.setIslandNightVisionEnabled(true);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean disableIslandNightVision(IslandData island) {
        if (island == null || !island.isIslandNightVisionEnabled()) return false;
        island.setIslandNightVisionEnabled(false);
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
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
            case 1, 2, 3 -> 30L * 60L * 1000L;
            default -> 0L;
        };
    }

    public double getGrowthBoostVanillaMultiplier(int tier) {
        return switch (tier) {
            case 1, 2, 3 -> 3.0D;
            default -> 1.0D;
        };
    }

    public int getGrowthBoostExtraRandomTickAttemptsPerSection(int tier, int randomTickSpeed, int intervalTicks) {
        double multiplier = Math.max(1.0D, getGrowthBoostVanillaMultiplier(tier));
        int safeRandomTickSpeed = Math.max(0, randomTickSpeed);
        int safeIntervalTicks = Math.max(1, intervalTicks);
        return (int) Math.round(safeRandomTickSpeed * Math.max(0.0D, multiplier - 1.0D) * safeIntervalTicks);
    }

    public void recordGrowthDebugAttempt(IslandData island, int relChunkX, int relChunkZ) {
        recordGrowthDebugAttempt(island, relChunkX, relChunkZ, 1);
    }

    public void recordGrowthDebugAttempt(IslandData island, int relChunkX, int relChunkZ, int amount) {
        String key = growthDebugKey(island, relChunkX, relChunkZ);
        if (key == null || amount <= 0) return;
        advanceGrowthDebugWindow(key);
        growthDebugAttempts.merge(key, amount, Integer::sum);
    }

    public void recordGrowthDebugHit(IslandData island, int relChunkX, int relChunkZ) {
        recordGrowthDebugHit(island, relChunkX, relChunkZ, 1);
    }

    public void recordGrowthDebugHit(IslandData island, int relChunkX, int relChunkZ, int amount) {
        String key = growthDebugKey(island, relChunkX, relChunkZ);
        if (key == null || amount <= 0) return;
        advanceGrowthDebugWindow(key);
        growthDebugHits.merge(key, amount, Integer::sum);
    }

    public String getGrowthDebugSummary(IslandData island, int relChunkX, int relChunkZ) {
        String key = growthDebugKey(island, relChunkX, relChunkZ);
        if (key == null) return "dbg 0/0";
        advanceGrowthDebugWindow(key);
        int attempts = growthDebugAttempts.getOrDefault(key, 0);
        int hits = growthDebugHits.getOrDefault(key, 0);
        int bridgeFailures = growthDebugBridgeFailures.getOrDefault(key, 0);
        int targets = growthDebugTargets.getOrDefault(key, 0);
        String targetSummary = growthDebugTargetSummary.getOrDefault(key, "-");
        return "dbg " + hits + "/" + attempts + " rf" + bridgeFailures + " t" + targets + " " + targetSummary + " 5s";
    }

    public void recordGrowthDebugBridgeFailure(IslandData island, int relChunkX, int relChunkZ) {
        recordGrowthDebugBridgeFailure(island, relChunkX, relChunkZ, 1);
    }

    public void recordGrowthDebugBridgeFailure(IslandData island, int relChunkX, int relChunkZ, int amount) {
        String key = growthDebugKey(island, relChunkX, relChunkZ);
        if (key == null || amount <= 0) return;
        advanceGrowthDebugWindow(key);
        growthDebugBridgeFailures.merge(key, amount, Integer::sum);
    }

    public void recordGrowthDebugTargets(IslandData island, int relChunkX, int relChunkZ, int amount, String summary) {
        String key = growthDebugKey(island, relChunkX, relChunkZ);
        if (key == null || amount < 0) return;
        advanceGrowthDebugWindow(key);
        growthDebugTargets.put(key, amount);
        growthDebugTargetSummary.put(key, summary == null || summary.isBlank() ? "-" : summary);
    }

    private void advanceGrowthDebugWindow(String key) {
        long now = System.currentTimeMillis();
        long start = growthDebugWindowStart.getOrDefault(key, 0L);
        if (start <= 0L || now - start >= GROWTH_DEBUG_WINDOW_MILLIS) {
            growthDebugWindowStart.put(key, now);
            growthDebugAttempts.put(key, 0);
            growthDebugHits.put(key, 0);
            growthDebugBridgeFailures.put(key, 0);
            growthDebugTargets.put(key, 0);
            growthDebugTargetSummary.put(key, "-");
        }
    }

    private String growthDebugKey(IslandData island, int relChunkX, int relChunkZ) {
        if (island == null) return null;
        return island.getOwner() + "|" + chunkKey(relChunkX, relChunkZ);
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
                refreshGrowthBoostTracking(island);
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
        if (!isSpawnIsland(island) && !spendStoredExperience(island, cost)) return false;
        String key = chunkKey(relChunkX, relChunkZ);
        long now = System.currentTimeMillis();
        long base = Math.max(now, island.getGrowthBoostUntil().getOrDefault(key, now));
        island.getGrowthBoostUntil().put(key, base + duration);
        island.getGrowthBoostTier().put(key, Math.max(tier, island.getGrowthBoostTier().getOrDefault(key, 0)));
        refreshGrowthBoostTracking(island);
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

    public String islandWeatherModeLabel(IslandWeatherMode mode) {
        if (mode == null) return "Normal";
        return switch (mode) {
            case CLEAR -> "Sonnenschein";
            case RAIN -> "Regen";
            case THUNDER -> "Gewitter";
            case NORMAL -> "Normal";
        };
    }

    public String snowWeatherModeLabel(SnowWeatherMode mode) {
        if (mode == null) return "Normal";
        return switch (mode) {
            case ALLOW -> "Schnee bleibt liegen";
            case BLOCK -> "Schneefrei";
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
    public boolean isSpawnIsland(IslandData island) { return island != null && SPAWN_ISLAND_OWNER.equals(island.getOwner()); }
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
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
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
    public IslandLevelDefinition getCurrentLevelDef(IslandData island) {
        if (island == null) return levelDefinitions.get(1);
        IslandLevelDefinition base = levelDefinitions.getOrDefault(island.getLevel(), levelDefinitions.get(1));
        return new IslandLevelDefinition(
                base.getLevel(),
                base.getRequirements(),
                base.getChunkUnlocksGranted(),
                getCurrentUpgradeLimit(island, UpgradeBranch.ANIMAL),
                getCurrentUpgradeLimit(island, UpgradeBranch.GOLEM),
                getCurrentUpgradeLimit(island, UpgradeBranch.VILLAGER),
                getCurrentUpgradeLimit(island, UpgradeBranch.HOPPER),
                getCurrentUpgradeLimit(island, UpgradeBranch.PISTON),
                getCurrentUpgradeLimit(island, UpgradeBranch.ARMOR_STAND),
                getCurrentUpgradeLimit(island, UpgradeBranch.MINECART),
                getCurrentUpgradeLimit(island, UpgradeBranch.BOAT),
                getCurrentUpgradeLimit(island, UpgradeBranch.OBSERVER),
                getCurrentUpgradeLimit(island, UpgradeBranch.DISPENSER),
                getCurrentUpgradeLimit(island, UpgradeBranch.CACTUS),
                getCurrentUpgradeLimit(island, UpgradeBranch.KELP),
                getCurrentUpgradeLimit(island, UpgradeBranch.BAMBOO)
        );
    }
    public IslandLevelDefinition getNextLevelDef(IslandData island) { return levelDefinitions.get(island.getLevel() + 1); }

    public UpgradeBranch getPinnedUpgrade(IslandData island) {
        return island == null ? UpgradeBranch.ANIMAL : UpgradeBranch.fromKey(island.getPinnedUpgradeKey());
    }

    public boolean isMilestonePinned(IslandData island) {
        return island != null && "MILESTONE".equalsIgnoreCase(island.getPinnedUpgradeKey());
    }

    public void setPinnedMilestone(IslandData island) {
        if (island == null) return;
        island.setPinnedUpgradeKey("MILESTONE");
        save();
    }

    public void setPinnedUpgrade(IslandData island, UpgradeBranch branch) {
        if (island == null || branch == null) return;
        island.setPinnedUpgradeKey(branch.name());
        save();
    }

    public int getUpgradeTier(IslandData island, UpgradeBranch branch) {
        if (island == null || branch == null) return 0;
        return Math.max(0, island.getUpgradeTiers().getOrDefault(branch.name(), 0));
    }

    public int getUnlockedUpgradeTierCap(IslandData island, UpgradeBranch branch) {
        if (island == null || branch == null) return 0;
        int milestone = Math.max(0, island.getLevel() - 1);
        return getUnlockedUpgradeTierCapForMilestone(milestone, branch);
    }

    public int getUnlockedUpgradeTierCapForMilestone(int milestone, UpgradeBranch branch) {
        if (branch == null) return 0;
        int cap = switch (branch) {
            case ANIMAL -> (milestone * 20 + 10) / 11;
            case CHUNKS -> milestone * 24;
            case GOLEM, VILLAGER -> Math.max(0, milestone - 1);
            case CONTAINER -> Math.max(0, milestone);
            case HOPPER, PISTON, MINECART, BOAT, OBSERVER, DISPENSER -> Math.max(0, (milestone * 2) - 1);
            case ARMOR_STAND -> Math.max(0, milestone + milestone / 2);
            case CACTUS, KELP, BAMBOO -> Math.max(0, milestone * 2);
        };
        return Math.min(branch.maxTier(), Math.max(0, cap));
    }

    public int getCurrentUpgradeLimit(IslandData island, UpgradeBranch branch) {
        if (island == null || branch == null) return 0;
        if (isSpawnIsland(island)) return Integer.MAX_VALUE;
        return branch.baseLimit() + (getUpgradeTier(island, branch) * branch.step());
    }

    private long scaledLong(int stage, long base, double growth) {
        if (stage <= 0) return Math.max(0L, base);
        double value = base * Math.pow(growth, Math.max(0, stage - 1));
        if (Double.isNaN(value) || Double.isInfinite(value)) return Long.MAX_VALUE;
        return Math.max(base, Math.min(Long.MAX_VALUE, (long) Math.floor(value)));
    }

    private int scaledInt(int stage, int base, double growth) {
        long scaled = scaledLong(stage, base, growth);
        return (int) Math.max(base, Math.min(Integer.MAX_VALUE, scaled));
    }

    private int roundRequirement(long value) {
        long normalized = Math.max(1L, value);
        long step = 1L;
        while (step <= normalized / 10L) {
            step *= 10L;
        }
        long rounded = ((normalized + step - 1L) / step) * step;
        return (int) Math.min(Integer.MAX_VALUE, rounded);
    }

    private int scaledMaterial(int stage, int base, double growth) {
        return roundRequirement(scaledLong(stage, base, growth));
    }

    private int getChunkUnlocksForTier(int tier) {
        if (tier <= 0) return 0;
        if (tier <= 24) return 12;
        if (tier <= 79) return 15;
        return 16;
    }

    public UpgradeRequirement getNextUpgradeRequirement(IslandData island, UpgradeBranch branch) {
        if (island == null || branch == null) return null;
        if (isSpawnIsland(island)) return null;
        int nextTier = getUpgradeTier(island, branch) + 1;
        if (nextTier > getUnlockedUpgradeTierCap(island, branch)) return null;
        if (nextTier > branch.maxTier()) return null;

        long islandLevelReq = switch (branch) {
            case ANIMAL -> scaledLong(nextTier, 85L, 1.19D);
            case CHUNKS -> scaledLong(nextTier, 60L, 1.06D);
            case GOLEM -> scaledLong(nextTier, 125L, 1.21D);
            case VILLAGER -> scaledLong(nextTier, 110L, 1.19D);
            case CONTAINER -> scaledLong(nextTier, 95L, 1.18D);
            case HOPPER -> scaledLong(nextTier, 110L, 1.20D);
            case PISTON -> scaledLong(nextTier, 108L, 1.20D);
            case ARMOR_STAND -> scaledLong(nextTier, 75L, 1.17D);
            case MINECART -> scaledLong(nextTier, 108L, 1.20D);
            case BOAT -> scaledLong(nextTier, 82L, 1.18D);
            case OBSERVER -> scaledLong(nextTier, 140L, 1.22D);
            case DISPENSER -> scaledLong(nextTier, 108L, 1.20D);
            case CACTUS, KELP, BAMBOO -> scaledLong(nextTier, 90L, 1.18D);
        };
        long experienceReq = switch (branch) {
            case ANIMAL -> scaledLong(nextTier, 320L, 1.26D);
            case CHUNKS -> scaledLong(nextTier, 180L, 1.08D);
            case GOLEM -> scaledLong(nextTier, 480L, 1.28D);
            case VILLAGER -> scaledLong(nextTier, 380L, 1.25D);
            case CONTAINER -> scaledLong(nextTier, 340L, 1.23D);
            case HOPPER, PISTON, MINECART, DISPENSER -> scaledLong(nextTier, 420L, 1.26D);
            case BOAT -> scaledLong(nextTier, 300L, 1.22D);
            case ARMOR_STAND -> scaledLong(nextTier, 260L, 1.22D);
            case OBSERVER -> scaledLong(nextTier, 560L, 1.29D);
            case CACTUS, KELP, BAMBOO -> scaledLong(nextTier, 280L, 1.23D);
        };
        Map<Material, Integer> materials = new LinkedHashMap<>();
        switch (branch) {
            case ANIMAL -> {
                materials.put(Material.WHEAT, scaledMaterial(nextTier, 3840, 1.50D));
                materials.put(Material.CARROT, scaledMaterial(nextTier, 2624, 1.49D));
                materials.put(Material.POTATO, scaledMaterial(nextTier, 2624, 1.49D));
                if (nextTier >= 2) materials.put(Material.HAY_BLOCK, scaledMaterial(nextTier - 1, 192, 1.32D));
                if (nextTier >= 5) materials.put(Material.PUMPKIN, scaledMaterial(nextTier - 4, 704, 1.40D));
            }
            case CHUNKS -> {
                materials.put(Material.COBBLESTONE, scaledMaterial(nextTier, 5056, 1.04D));
                materials.put(Material.STONE, scaledMaterial(nextTier, 2432, 1.039D));
                materials.put(Material.OAK_LOG, scaledMaterial(nextTier, 2240, 1.04D));
                if (nextTier >= 4) materials.put(Material.IRON_INGOT, scaledMaterial(nextTier - 3, 1024, 1.05D));
                if (nextTier >= 8) materials.put(Material.REDSTONE, scaledMaterial(nextTier - 7, 768, 1.05D));
            }
            case GOLEM -> {
                materials.put(Material.IRON_INGOT, scaledMaterial(nextTier, 8192, 1.34D));
                materials.put(Material.CARVED_PUMPKIN, scaledMaterial(nextTier, 32, 1.20D));
                materials.put(Material.REDSTONE, scaledMaterial(nextTier, 4096, 1.30D));
            }
            case VILLAGER -> {
                materials.put(Material.BREAD, scaledMaterial(nextTier, 2816, 1.47D));
                materials.put(Material.CARROT, scaledMaterial(nextTier, 3456, 1.50D));
                materials.put(Material.POTATO, scaledMaterial(nextTier, 3456, 1.50D));
                materials.put(Material.EMERALD, scaledMaterial(nextTier, 4096, 1.32D));
            }
            case CONTAINER -> {
                materials.put(Material.CHEST, scaledMaterial(nextTier, 96, 1.34D));
                materials.put(Material.BARREL, scaledMaterial(nextTier, 48, 1.32D));
                materials.put(Material.OAK_LOG, scaledMaterial(nextTier, 4224, 1.42D));
                if (nextTier >= 6) materials.put(Material.IRON_INGOT, scaledMaterial(nextTier - 5, 3072, 1.28D));
            }
            case HOPPER -> {
                materials.put(Material.IRON_INGOT, scaledMaterial(nextTier, 6144, 1.32D));
                materials.put(Material.CHEST, scaledMaterial(nextTier, 24, 1.24D));
                materials.put(Material.REDSTONE, scaledMaterial(nextTier, 4096, 1.30D));
            }
            case PISTON -> {
                materials.put(Material.COBBLESTONE, scaledMaterial(nextTier, 1792, 1.30D));
                materials.put(Material.IRON_INGOT, scaledMaterial(nextTier, 4096, 1.30D));
                materials.put(Material.REDSTONE, scaledMaterial(nextTier, 3072, 1.29D));
                if (nextTier >= 4) materials.put(Material.SLIME_BALL, scaledMaterial(nextTier - 3, 24, 1.22D));
            }
            case ARMOR_STAND -> {
                materials.put(Material.STICK, scaledMaterial(nextTier, 2624, 1.38D));
                materials.put(Material.SMOOTH_STONE_SLAB, scaledMaterial(nextTier, 320, 1.28D));
                if (nextTier >= 3) materials.put(Material.LEATHER, scaledMaterial(nextTier - 2, 72, 1.24D));
            }
            case MINECART -> {
                materials.put(Material.IRON_INGOT, scaledMaterial(nextTier, 4096, 1.30D));
                materials.put(Material.RAIL, scaledMaterial(nextTier, 2048, 1.28D));
                materials.put(Material.REDSTONE, scaledMaterial(nextTier, 2048, 1.27D));
                if (nextTier >= 4) materials.put(Material.CHEST, scaledMaterial(nextTier - 3, 24, 1.22D));
            }
            case BOAT -> {
                materials.put(Material.OAK_LOG, scaledMaterial(nextTier, 4096, 1.40D));
                materials.put(Material.CHEST, scaledMaterial(nextTier, 16, 1.22D));
                if (nextTier >= 3) materials.put(Material.BARREL, scaledMaterial(nextTier - 2, 16, 1.20D));
            }
            case OBSERVER -> {
                materials.put(Material.COBBLESTONE, scaledMaterial(nextTier, 1408, 1.28D));
                materials.put(Material.REDSTONE, scaledMaterial(nextTier, 6144, 1.31D));
                materials.put(Material.QUARTZ, scaledMaterial(nextTier, 2048, 1.28D));
            }
            case DISPENSER -> {
                materials.put(Material.COBBLESTONE, scaledMaterial(nextTier, 1792, 1.29D));
                materials.put(Material.REDSTONE, scaledMaterial(nextTier, 3072, 1.29D));
                materials.put(Material.STRING, scaledMaterial(nextTier, 2240, 1.38D));
                if (nextTier >= 4) materials.put(Material.BOW, scaledMaterial(nextTier - 3, 8, 1.20D));
            }
            case CACTUS -> {
                materials.put(Material.CACTUS, scaledMaterial(nextTier, 4224, 1.49D));
                if (nextTier >= 3) materials.put(Material.SAND, scaledMaterial(nextTier - 2, 1024, 1.34D));
            }
            case KELP -> {
                materials.put(Material.KELP, scaledMaterial(nextTier, 5248, 1.50D));
                if (nextTier >= 4) materials.put(Material.DRIED_KELP_BLOCK, scaledMaterial(nextTier - 3, 64, 1.24D));
            }
            case BAMBOO -> {
                materials.put(Material.BAMBOO, scaledMaterial(nextTier, 5248, 1.50D));
                if (nextTier >= 4) materials.put(Material.SCAFFOLDING, scaledMaterial(nextTier - 3, 64, 1.26D));
            }
        }
        int chunkUnlocksGranted = branch == UpgradeBranch.CHUNKS ? getChunkUnlocksForTier(nextTier) : 0;
        return new UpgradeRequirement(islandLevelReq, experienceReq, materials, chunkUnlocksGranted);
    }

    public boolean canUnlockUpgrade(IslandData island, UpgradeBranch branch) {
        UpgradeRequirement requirement = getNextUpgradeRequirement(island, branch);
        if (requirement == null) return false;
        if (calculateIslandLevel(island) < requirement.islandLevel()) return false;
        if (island.getStoredExperience() < requirement.experience()) return false;
        for (Map.Entry<Material, Integer> entry : requirement.materials().entrySet()) {
            if (island.getProgress(entry.getKey()) < entry.getValue()) return false;
        }
        return true;
    }

    public boolean unlockUpgrade(IslandData island, UpgradeBranch branch) {
        if (isSpawnIsland(island)) return false;
        UpgradeRequirement requirement = getNextUpgradeRequirement(island, branch);
        if (requirement == null || !canUnlockUpgrade(island, branch)) return false;
        for (Map.Entry<Material, Integer> entry : requirement.materials().entrySet()) {
            island.takeProgress(entry.getKey(), entry.getValue());
        }
        island.takeStoredExperience(requirement.experience());
        island.getUpgradeTiers().put(branch.name(), getUpgradeTier(island, branch) + 1);
        if (requirement.chunkUnlocksGranted() > 0) {
            island.setAvailableChunkUnlocks(island.getAvailableChunkUnlocks() + requirement.chunkUnlocksGranted());
        }
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public boolean canLevelUp(IslandData island) {
        if (isSpawnIsland(island)) return false;
        MilestoneRequirement next = getNextMilestoneRequirement(island);
        if (next == null) return false;
        if (calculateIslandLevel(island) < next.islandLevel()) return false;
        if (island.getStoredExperience() < next.experience()) return false;
        for (Map.Entry<Material, Integer> req : next.materials().entrySet()) {
            if (island.getProgress(req.getKey()) < req.getValue()) return false;
        }
        return true;
    }

    public boolean levelUp(IslandData island) {
        if (isSpawnIsland(island)) return false;
        MilestoneRequirement next = getNextMilestoneRequirement(island);
        if (next == null || !canLevelUp(island)) return false;
        for (Map.Entry<Material, Integer> req : next.materials().entrySet()) {
            island.takeProgress(req.getKey(), req.getValue());
        }
        island.takeStoredExperience(next.experience());
        island.setLevel(next.milestone() + 1);
        island.setAvailableChunkUnlocks(island.getAvailableChunkUnlocks() + next.chunkUnlocksGranted());
        island.setLastActiveAt(System.currentTimeMillis());
        save();
        return true;
    }

    public List<UpgradeBranch> getUpgradeBranches() {
        return List.of(UpgradeBranch.values());
    }

    public MilestoneRequirement getNextMilestoneRequirement(IslandData island) {
        if (island == null) return null;
        if (isSpawnIsland(island)) return null;
        IslandLevelDefinition next = getNextLevelDef(island);
        if (next == null) return null;
        int milestone = Math.max(0, next.getLevel() - 1);
        long islandLevelReq = scaledLong(milestone, 180L, 1.30D);
        long experienceReq = scaledLong(milestone, 900L, 1.40D);
        Map<Material, Integer> materials = new LinkedHashMap<>();

        materials.put(Material.COBBLESTONE, scaledMaterial(milestone, 50048, 1.52D));
        materials.put(Material.STONE, scaledMaterial(milestone, 22016, 1.47D));
        materials.put(Material.OAK_LOG, scaledMaterial(milestone, 42048, 1.53D));

        if (milestone >= 2) materials.put(Material.IRON_INGOT, scaledMaterial(milestone - 1, 4096, 1.30D));
        if (milestone >= 3) materials.put(Material.COAL, scaledMaterial(milestone - 2, 32768, 1.38D));
        if (milestone >= 4) materials.put(Material.REDSTONE, scaledMaterial(milestone - 3, 4096, 1.32D));
        if (milestone >= 5) materials.put(Material.GOLD_INGOT, scaledMaterial(milestone - 4, 2048, 1.28D));
        if (milestone >= 6) materials.put(Material.QUARTZ, scaledMaterial(milestone - 5, 2048, 1.28D));
        if (milestone >= 7) materials.put(Material.LAPIS_LAZULI, scaledMaterial(milestone - 6, 4096, 1.30D));
        if (milestone >= 8) materials.put(Material.EMERALD, scaledMaterial(milestone - 7, 4096, 1.32D));
        if (milestone >= 9) materials.put(Material.OBSIDIAN, scaledMaterial(milestone - 8, 4096, 1.28D));
        if (milestone >= 10) materials.put(Material.DIAMOND, scaledMaterial(milestone - 9, 2048, 1.30D));
        if (milestone >= 11) materials.put(Material.OBSERVER, scaledMaterial(milestone - 10, 64, 1.18D));

        if (milestone == 1) {
            islandLevelReq = Math.max(1L, (long) Math.ceil(islandLevelReq / 100.0D));
            experienceReq = Math.max(1L, (long) Math.ceil(experienceReq / 100.0D));
            Map<Material, Integer> reducedMaterials = new LinkedHashMap<>();
            for (Map.Entry<Material, Integer> entry : materials.entrySet()) {
                reducedMaterials.put(entry.getKey(), Math.max(1, (int) Math.ceil(entry.getValue() / 100.0D)));
            }
            materials = reducedMaterials;
        }

        return new MilestoneRequirement(
                milestone,
                islandLevelReq,
                experienceReq,
                materials,
                next.getChunkUnlocksGranted()
        );
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
        MilestoneRequirement next = getNextMilestoneRequirement(island);
        if (next == null) return 0.0D;
        double reserved = 0.0D;
        for (Map.Entry<Material, Integer> req : next.materials().entrySet()) {
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

    public int getAnimalCount(IslandData island) {
        if (island == null) return 0;
        int roamingAnimals = (int) getEntitiesInIsland(island).stream().filter(e -> e instanceof Animals).count();
        return roamingAnimals + getStoredBeeCount(island);
    }
    public int getGolemCount(IslandData island) { return (int) getEntitiesInIsland(island).stream().filter(e -> isTrackedGolem(e.getType())).count(); }
    public int getVillagerCount(IslandData island) { return (int) getEntitiesInIsland(island).stream().filter(e -> e instanceof Villager).count(); }
    public int getArmorStandCount(IslandData island) {
        return (int) getEntitiesInIsland(island).stream()
                .filter(e -> e instanceof ArmorStand)
                .filter(e -> e.getScoreboardTags().stream().noneMatch(tag -> tag.startsWith("skycity_")))
                .count();
    }
    public int getMinecartCount(IslandData island) {
        return (int) getEntitiesInIsland(island).stream()
                .filter(e -> e instanceof org.bukkit.entity.Minecart)
                .count();
    }
    public int getBoatCount(IslandData island) {
        return (int) getEntitiesInIsland(island).stream()
                .filter(e -> e instanceof org.bukkit.entity.Boat)
                .count();
    }
    public boolean isWithinAnimalLimit(IslandData island) { return getAnimalCount(island) < getCurrentLevelDef(island).getAnimalLimit(); }
    public boolean isWithinGolemLimit(IslandData island) { return getGolemCount(island) < getCurrentLevelDef(island).getGolemLimit(); }
    public boolean isWithinVillagerLimit(IslandData island) { return getVillagerCount(island) < getCurrentLevelDef(island).getVillagerLimit(); }
    public boolean isWithinArmorStandLimit(IslandData island) { return getArmorStandCount(island) < getCurrentLevelDef(island).getArmorStandLimit(); }
    public boolean isWithinMinecartLimit(IslandData island) { return getMinecartCount(island) < getCurrentLevelDef(island).getMinecartLimit(); }
    public boolean isWithinBoatLimit(IslandData island) { return getBoatCount(island) < getCurrentLevelDef(island).getBoatLimit(); }

    private int getStoredBeeCount(IslandData island) {
        World world = skyWorldService.getWorld();
        if (island == null || world == null) return 0;
        int count = 0;
        for (int cx = 0; cx < ISLAND_CHUNKS; cx++) for (int cz = 0; cz < ISLAND_CHUNKS; cz++) {
            if (!island.getUnlockedChunks().contains(chunkKey(cx, cz))) continue;
            Chunk chunk = world.getChunkAt(plotMinChunkX(island.getGridX()) + cx, plotMinChunkZ(island.getGridZ()) + cz);
            for (int y = world.getMinHeight(); y < Math.min(world.getMaxHeight(), 256); y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        Block block = chunk.getBlock(x, y, z);
                        Material type = block.getType();
                        if (type != Material.BEEHIVE && type != Material.BEE_NEST) continue;
                        if (block.getState() instanceof Beehive beehive) {
                            count += Math.max(0, beehive.getEntityCount());
                        }
                    }
                }
            }
        }
        return count;
    }

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
        if (validateParcelPveDetails(island, parcel).isPresent()) {
            return Optional.empty();
        }
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
        int rectArea = width * depth;

        Location respawnLocation = new Location(world, (minStartX + maxStartX) / 2.0 + 0.5, startY + 1.0, (minStartZ + maxStartZ) / 2.0 + 0.5);
        return Optional.of(new PveZoneRuntime(island.getOwner(), parcel.getChunkKey(), parcel, minStartX, minStartZ, maxStartX, maxStartZ, startY, respawnLocation, markers));
    }

    private Optional<String> validateParcelPveDetails(IslandData island, ParcelData parcel) {
        if (island == null || parcel == null) return Optional.of("PvE-Pr\u00fcfung fehlgeschlagen: Insel oder Grundst\u00fcck fehlt.");
        World world = skyWorldService.getWorld();
        if (world == null) return Optional.of("PvE-Pr\u00fcfung fehlgeschlagen: SkyCity-Welt ist nicht geladen.");

        List<Block> startBlocks = new ArrayList<>();
        int minStartX = Integer.MAX_VALUE;
        int minStartZ = Integer.MAX_VALUE;
        int maxStartX = Integer.MIN_VALUE;
        int maxStartZ = Integer.MIN_VALUE;
        Integer startY = null;
        int markerCount = 0;

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
                        if (startY != y) {
                            return Optional.of("Startzone ung\u00fcltig: wei\u00dfe Wolle muss auf derselben H\u00f6he liegen.");
                        }
                    } else if (createPveSpawnMarker(block, markerCount + 1) != null) {
                        markerCount++;
                    }
                }
            }
        }

        if (startBlocks.isEmpty()) {
            return Optional.of("Startzone fehlt: Es wurde keine wei\u00dfe Wolle gefunden.");
        }
        if (markerCount <= 0) {
            return Optional.of("Spawnmarker fehlen: Es wurde keine g\u00fcltige farbige Wolle f\u00fcr Mobs gefunden.");
        }

        int width = maxStartX - minStartX + 1;
        int depth = maxStartZ - minStartZ + 1;
        if (width > 5 || depth > 5) {
            return Optional.of("Startzone zu gross: Maximal 5x5 wei\u00dfe Wolle erlaubt.");
        }

        int rectArea = width * depth;
        int missingBlocks = rectArea - startBlocks.size();
        if (missingBlocks > 4) {
            return Optional.of("Startzone ung\u00fcltig: Es sind mehr als 4 Luftbl\u00f6cke im 5x5-Bereich der Startzone.");
        }

        Location respawnLocation = new Location(world, (minStartX + maxStartX) / 2.0 + 0.5, startY + 1.0, (minStartZ + maxStartZ) / 2.0 + 0.5);
        if (!respawnLocation.getBlock().isPassable()) {
            return Optional.of("Startzone blockiert: \u00dcber der wei\u00dfen Wolle ist kein freier Spawnplatz.");
        }
        Optional<String> zoneCheck = validatePveZoneShell(parcel, respawnLocation, minStartX, minStartZ, maxStartX, maxStartZ, startY);
        if (zoneCheck.isPresent()) {
            return zoneCheck;
        }
        return Optional.empty();
    }

    private Optional<String> validatePveZoneShell(ParcelData parcel, Location startLocation, int startMinX, int startMinZ, int startMaxX, int startMaxZ, int startY) {
        if (parcel == null || startLocation == null || startLocation.getWorld() == null) return Optional.of("PvE-Zone ung\u00fcltig: Interne Startpr\u00fcfung fehlgeschlagen.");
        Block startBlock = startLocation.getBlock();
        if (!startBlock.isPassable()) return Optional.of("Startzone blockiert: Der Ausgang \u00fcber der Startzone ist nicht frei.");
        Set<String> visited = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        String exitSide = null;
        queue.add(startBlock);
        while (!queue.isEmpty()) {
            Block current = queue.removeFirst();
            String key = current.getX() + ":" + current.getY() + ":" + current.getZ();
            if (!visited.add(key)) continue;
            if (current.getX() <= parcel.getMinX() || current.getX() >= parcel.getMaxX()
                    || current.getY() <= parcel.getMinY() || current.getY() >= parcel.getMaxY()
                    || current.getZ() <= parcel.getMinZ() || current.getZ() >= parcel.getMaxZ()) {
                String candidateSide = classifyValidPveExitSide(parcel, current, startMinX, startMinZ, startMaxX, startMaxZ, startY);
                if (candidateSide == null) {
                    return Optional.of("Zone ist offen: Der Ausgang muss an der Startzone liegen und bis 3 Bl\u00f6cke hoch frei sein.");
                }
                if (exitSide == null) {
                    exitSide = candidateSide;
                } else if (!exitSide.equals(candidateSide)) {
                    return Optional.of("Zone ist offen: Es ist nur ein zusammenh\u00e4ngender Ausgang an einer Startzonen-Seite erlaubt.");
                }
            }
            for (int[] offset : List.of(new int[]{1, 0, 0}, new int[]{-1, 0, 0}, new int[]{0, 1, 0}, new int[]{0, -1, 0}, new int[]{0, 0, 1}, new int[]{0, 0, -1})) {
                Block next = current.getRelative(offset[0], offset[1], offset[2]);
                if (next.getX() < parcel.getMinX() || next.getX() > parcel.getMaxX()
                        || next.getY() < parcel.getMinY() || next.getY() > parcel.getMaxY()
                        || next.getZ() < parcel.getMinZ() || next.getZ() > parcel.getMaxZ()) {
                    String candidateSide = classifyValidPveExitSide(parcel, current, startMinX, startMinZ, startMaxX, startMaxZ, startY);
                    if (candidateSide == null) {
                        return Optional.of("Zone ist offen: Der Ausgang muss an der Startzone liegen und bis 3 Bl\u00f6cke hoch frei sein.");
                    }
                    if (exitSide == null) {
                        exitSide = candidateSide;
                    } else if (!exitSide.equals(candidateSide)) {
                        return Optional.of("Zone ist offen: Es ist nur ein zusammenh\u00e4ngender Ausgang an einer Startzonen-Seite erlaubt.");
                    }
                    continue;
                }
                if (next.isPassable()) {
                    queue.add(next);
                }
            }
        }
        return Optional.empty();
    }

    private String classifyValidPveExitSide(ParcelData parcel, Block insideBoundaryBlock, int startMinX, int startMinZ, int startMaxX, int startMaxZ, int startY) {
        if (parcel == null || insideBoundaryBlock == null) return null;
        World world = insideBoundaryBlock.getWorld();
        if (world == null) return null;

        int dx = 0;
        int dz = 0;
        String side;
        if (insideBoundaryBlock.getX() <= parcel.getMinX()) {
            dx = -1;
            side = "WEST";
            if (startMinX != parcel.getMinX() || insideBoundaryBlock.getZ() < startMinZ - 1 || insideBoundaryBlock.getZ() > startMaxZ + 1) return null;
        } else if (insideBoundaryBlock.getX() >= parcel.getMaxX()) {
            dx = 1;
            side = "EAST";
            if (startMaxX != parcel.getMaxX() || insideBoundaryBlock.getZ() < startMinZ - 1 || insideBoundaryBlock.getZ() > startMaxZ + 1) return null;
        } else if (insideBoundaryBlock.getZ() <= parcel.getMinZ()) {
            dz = -1;
            side = "NORTH";
            if (startMinZ != parcel.getMinZ() || insideBoundaryBlock.getX() < startMinX - 1 || insideBoundaryBlock.getX() > startMaxX + 1) return null;
        } else if (insideBoundaryBlock.getZ() >= parcel.getMaxZ()) {
            dz = 1;
            side = "SOUTH";
            if (startMaxZ != parcel.getMaxZ() || insideBoundaryBlock.getX() < startMinX - 1 || insideBoundaryBlock.getX() > startMaxX + 1) return null;
        } else {
            return null;
        }

        for (int yOffset = 0; yOffset < 3; yOffset++) {
            int checkY = startY + 1 + yOffset;
            Block inside = world.getBlockAt(insideBoundaryBlock.getX(), checkY, insideBoundaryBlock.getZ());
            Block outside = world.getBlockAt(insideBoundaryBlock.getX() + dx, checkY, insideBoundaryBlock.getZ() + dz);
            if (!inside.isPassable() || !outside.isPassable()) {
                return null;
            }
        }
        return side;
    }

    private PveSpawnMarker createPveSpawnMarker(Block woolBlock, int index) {
        Material type = woolBlock.getType();
        String familyName;
        List<PveMobArchetype> archetypes;
        int level;
        int reward;
        switch (type) {
            case LIGHT_GRAY_WOOL -> {
                familyName = "Zombie-Familie";
                archetypes = List.of(
                        new PveMobArchetype("zombie_opa", EntityType.ZOMBIE, "Opa", "Opas", 1, 1, ChatColor.GRAY, 0.15, 1.30, 0.80, Color.fromRGB(128, 128, 128), Color.fromRGB(236, 236, 228), Color.fromRGB(52, 86, 168), Color.fromRGB(38, 28, 18)),
                        new PveMobArchetype("zombie_hausmeister", EntityType.ZOMBIE, "Hausmeister", "Hausmeister", 1, 1, ChatColor.DARK_GREEN, 0.19, 1.05, 1.10, Color.fromRGB(44, 120, 54), Color.fromRGB(180, 210, 180), Color.fromRGB(70, 70, 74), Color.fromRGB(22, 22, 22)),
                        new PveMobArchetype("zombie_siedler", EntityType.ZOMBIE, "Siedler", "Siedler", 1, 1, ChatColor.GOLD, 0.21, 0.95, 1.20, Color.fromRGB(136, 72, 44), Color.fromRGB(160, 108, 76), Color.fromRGB(214, 192, 126), Color.fromRGB(92, 54, 26))
                );
                level = 1;
                reward = 1;
            }
            case GREEN_WOOL -> {
                familyName = "Spinnen-Familie";
                archetypes = List.of(
                        new PveMobArchetype("spider_jagdspinne", EntityType.SPIDER, "Jagdspinne", "Jagdspinnen", 2, 1, ChatColor.DARK_GREEN, 0.31, 1.10, 1.00, null, null, null, null),
                        new PveMobArchetype("spider_hoehlenspinne", EntityType.CAVE_SPIDER, "H\u00f6hlenspinne", "H\u00f6hlenspinnen", 2, 1, ChatColor.DARK_AQUA, 0.34, 0.85, 1.20, null, null, null, null),
                        new PveMobArchetype("spider_hetzer", EntityType.SPIDER, "Hetzerspinne", "Hetzerspinnen", 2, 1, ChatColor.GREEN, 0.35, 0.90, 1.10, null, null, null, null)
                );
                level = 2;
                reward = 1;
            }
            case YELLOW_WOOL -> {
                familyName = "Skelett-Familie";
                archetypes = List.of(
                        new PveMobArchetype("skeleton_waldlaeufer", EntityType.SKELETON, "Waldl\u00e4ufer", "Waldl\u00e4ufer", 2, 2, ChatColor.GREEN, 0.28, 0.95, 1.15, Color.fromRGB(70, 116, 56), Color.fromRGB(190, 224, 178), Color.fromRGB(74, 108, 58), Color.fromRGB(28, 24, 18)),
                        new PveMobArchetype("skeleton_rekrut", EntityType.SKELETON, "Rekrut", "Rekruten", 2, 2, ChatColor.BLUE, 0.26, 1.15, 0.95, Color.fromRGB(110, 118, 136), Color.fromRGB(188, 196, 214), Color.fromRGB(54, 66, 118), Color.fromRGB(20, 20, 28)),
                        new PveMobArchetype("skeleton_jaeger", EntityType.SKELETON, "J\u00e4ger", "J\u00e4ger", 2, 2, ChatColor.GOLD, 0.30, 0.90, 1.25, Color.fromRGB(122, 68, 38), Color.fromRGB(214, 166, 116), Color.fromRGB(64, 96, 46), Color.fromRGB(34, 20, 12))
                );
                level = 2;
                reward = 2;
            }
            case ORANGE_WOOL -> {
                familyName = "W\u00fcste";
                archetypes = List.of(
                        new PveMobArchetype("husk_wuestenraeuber", EntityType.HUSK, "W\u00fcstenr\u00e4uber", "W\u00fcstenr\u00e4uber", 3, 2, ChatColor.GOLD, 0.24, 1.20, 1.10, Color.fromRGB(160, 114, 70), Color.fromRGB(226, 188, 102), Color.fromRGB(132, 82, 36), Color.fromRGB(68, 38, 18)),
                        new PveMobArchetype("husk_pluenderer", EntityType.HUSK, "Pl\u00fcnderer", "Pl\u00fcnderer", 3, 2, ChatColor.RED, 0.26, 1.00, 1.25, Color.fromRGB(86, 62, 38), Color.fromRGB(144, 88, 42), Color.fromRGB(98, 58, 34), Color.fromRGB(34, 22, 14)),
                        new PveMobArchetype("husk_spaeher", EntityType.HUSK, "Sp\u00e4her", "Sp\u00e4her", 3, 2, ChatColor.YELLOW, 0.29, 0.90, 1.05, Color.fromRGB(186, 176, 120), Color.fromRGB(210, 214, 172), Color.fromRGB(94, 112, 66), Color.fromRGB(40, 34, 20))
                );
                level = 3;
                reward = 2;
            }
            case BLUE_WOOL -> {
                familyName = "Hafen";
                archetypes = List.of(
                        new PveMobArchetype("drowned_kai", EntityType.DROWNED, "Kai", "Kais", 3, 2, ChatColor.AQUA, 0.23, 1.15, 1.00, Color.fromRGB(30, 78, 118), Color.fromRGB(214, 228, 236), Color.fromRGB(52, 102, 176), Color.fromRGB(18, 28, 46)),
                        new PveMobArchetype("drowned_faehrmann", EntityType.DROWNED, "F\u00e4hrmann", "F\u00e4hrm\u00e4nner", 3, 2, ChatColor.DARK_AQUA, 0.22, 1.25, 0.90, Color.fromRGB(78, 56, 34), Color.fromRGB(210, 188, 154), Color.fromRGB(58, 72, 132), Color.fromRGB(26, 18, 12)),
                        new PveMobArchetype("drowned_hafenwache", EntityType.DROWNED, "Hafenwache", "Hafenwachen", 3, 2, ChatColor.BLUE, 0.25, 1.00, 1.20, Color.fromRGB(36, 42, 86), Color.fromRGB(118, 156, 198), Color.fromRGB(42, 58, 94), Color.fromRGB(10, 14, 28))
                );
                level = 3;
                reward = 2;
            }
            case RED_WOOL -> {
                familyName = "Sprengtrupp";
                archetypes = List.of(
                        new PveMobArchetype("creeper_sprengmeister", EntityType.CREEPER, "Sprengmeister", "Sprengmeister", 4, 3, ChatColor.RED, 0.30, 1.20, 1.15, null, null, null, null),
                        new PveMobArchetype("creeper_zuender", EntityType.CREEPER, "Z\u00fcnder", "Z\u00fcnder", 4, 3, ChatColor.GOLD, 0.33, 0.95, 1.25, null, null, null, null),
                        new PveMobArchetype("creeper_sturmlaeufer", EntityType.CREEPER, "Sturml\u00e4ufer", "Sturml\u00e4ufer", 4, 3, ChatColor.YELLOW, 0.36, 0.90, 1.10, null, null, null, null)
                );
                level = 4;
                reward = 3;
            }
            case BLACK_WOOL -> {
                familyName = "Nachtwache";
                archetypes = List.of(
                        new PveMobArchetype("wither_nachtwaechter", EntityType.WITHER_SKELETON, "Nachtw\u00e4chter", "Nachtw\u00e4chter", 5, 4, ChatColor.DARK_GRAY, 0.25, 1.20, 1.10, Color.fromRGB(66, 66, 74), Color.fromRGB(142, 142, 148), Color.fromRGB(26, 26, 30), Color.fromRGB(6, 6, 6)),
                        new PveMobArchetype("wither_vorsteher", EntityType.WITHER_SKELETON, "Vorsteher", "Vorsteher", 5, 4, ChatColor.GOLD, 0.27, 1.05, 1.25, Color.fromRGB(112, 82, 46), Color.fromRGB(194, 168, 126), Color.fromRGB(58, 42, 36), Color.fromRGB(18, 12, 10)),
                        new PveMobArchetype("wither_richter", EntityType.WITHER_SKELETON, "Richter", "Richter", 5, 4, ChatColor.WHITE, 0.24, 1.35, 1.00, Color.fromRGB(96, 30, 30), Color.fromRGB(224, 224, 224), Color.fromRGB(42, 42, 46), Color.fromRGB(10, 10, 10))
                );
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
        return new PveSpawnMarker("m" + index, woolBlock.getLocation(), spawnLocation, familyName, archetypes, level, reward);
    }

    private void startNextPveWave(PveZoneRuntime runtime) {
        if (runtime == null) return;
        runtime.currentWave++;
        runtime.spareSurvivalDeadline = 0L;
        if (runtime.markers.isEmpty()) return;
        int participantCount = Math.max(1, runtime.participants.size());
        int areaTier = computePveAreaTier(runtime.floorArea);
        int totalMobs = Math.max(1, 4 + areaTier * 2 + (runtime.currentWave - 1) * 2 + (participantCount - 1) * 3);
        List<PveSpawnMarker> shuffledMarkers = new ArrayList<>(runtime.markers);
        java.util.Collections.shuffle(shuffledMarkers, new Random(System.nanoTime()));
        int markerCount = shuffledMarkers.size();
        int baseShare = totalMobs / markerCount;
        int remainder = totalMobs % markerCount;
        for (int markerIndex = 0; markerIndex < markerCount; markerIndex++) {
            PveSpawnMarker marker = shuffledMarkers.get(markerIndex);
            int amount = baseShare + (markerIndex < remainder ? 1 : 0);
            if (amount <= 0) continue;
            for (int i = 0; i < amount; i++) {
                spawnPveMob(runtime, marker);
            }
        }
        choosePveObjective(runtime);
        broadcastPveZone(runtime, ChatColor.GOLD + "PvE-Welle " + runtime.currentWave + "/" + runtime.requiredWaves + " startet.");
        broadcastPveZone(runtime, ChatColor.YELLOW + "Ziel: " + ChatColor.GOLD + runtime.objectiveText());
    }

    private void spawnPveMob(PveZoneRuntime runtime, PveSpawnMarker marker) {
        if (runtime == null || marker == null || marker.spawnLocation().getWorld() == null) return;
        List<PveMobArchetype> archetypes = marker.archetypes();
        if (archetypes == null || archetypes.isEmpty()) return;
        PveMobArchetype archetype = archetypes.get(ThreadLocalRandom.current().nextInt(archetypes.size()));
        Entity entity = marker.spawnLocation().getWorld().spawnEntity(marker.spawnLocation(), archetype.entityType());
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            return;
        }
        String displayName = "Stufe " + archetype.level() + " " + archetype.singularName();
        living.setCustomName(formatPveMobName(archetype, displayName));
        living.setCustomNameVisible(true);
        living.addScoreboardTag("skycity_pve_zone");
        living.addScoreboardTag("skycity_pve_marker_" + marker.id());
        living.addScoreboardTag("skycity_pve_reward_" + archetype.rewardLevels());
        runtime.mobLevels.put(living.getUniqueId(), archetype.level());
        runtime.mobArchetypes.put(living.getUniqueId(), archetype);
        applyPveMobScaling(runtime, living, archetype.level(), true);
        applyPveMobTheme(living, archetype);
        equipPveCombatItem(living);
        if (living instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
            mob.setCanPickupItems(false);
            Player target = runtime.participants.stream().map(Bukkit::getPlayer).filter(player -> player != null && player.isOnline()).findFirst().orElse(null);
            if (target != null) {
                mob.setTarget(target);
            }
        }
        runtime.activeMobIds.add(living.getUniqueId());
        runtime.mobHomes.put(living.getUniqueId(), marker.spawnLocation().clone());
        runtime.mobLabels.put(living.getUniqueId(), displayName);
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
                    runtime.mobArchetypes.remove(mobId);
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
                    long now = System.currentTimeMillis();
                    if (mob.getTarget() == null || !mob.getTarget().getUniqueId().equals(nearest.getUniqueId())) {
                        mob.setTarget(nearest);
                    }
                    boolean ranged = isRangedPveMob(mob.getType());
                    if (ranged) {
                        if (mob.hasLineOfSight(nearest) && mob.getLocation().distanceSquared(nearest.getLocation()) <= 256.0D) {
                            runtime.mobLastReachableAt.put(mobId, now);
                        }
                    } else {
                        boolean pathReachable = mob.getPathfinder().findPath(nearest) != null;
                        if (pathReachable) {
                            runtime.mobLastReachableAt.put(mobId, now);
                            mob.getPathfinder().moveTo(nearest, 1.0);
                        }
                    }
                    boolean recentlyReachable = now - runtime.mobLastReachableAt.getOrDefault(mobId, 0L) <= 2500L;
                    boolean validRangedWindow = ranged && now - runtime.mobLastRangedHitAt.getOrDefault(mobId, 0L) <= 4000L;
                    boolean valid = recentlyReachable || validRangedWindow;
                    updatePveMobValidity(runtime, mob, valid);
                }
            }
            if (runtime.objectiveMode == PveObjectiveMode.SPARE_TYPE && areOnlyObjectiveMobsRemaining(runtime)) {
                if (runtime.spareSurvivalDeadline <= 0L) {
                    runtime.spareSurvivalDeadline = System.currentTimeMillis() + PVE_SPARE_SURVIVAL_MS;
                    broadcastPveZone(runtime, ChatColor.YELLOW + "Nur noch " + runtime.objectivePluralName + " \u00fcbrig. Jetzt " + (PVE_SPARE_SURVIVAL_MS / 1000L) + " Sekunden \u00fcberleben.");
                } else if (System.currentTimeMillis() >= runtime.spareSurvivalDeadline) {
                    completePveWave(runtime);
                    continue;
                }
            } else if (runtime.spareSurvivalDeadline > 0L) {
                runtime.spareSurvivalDeadline = 0L;
            }
            if (runtime.activeMobIds.isEmpty() && runtime.currentWave <= 0) {
                startNextPveWave(runtime);
            }
        }
    }

    private void completePveWave(PveZoneRuntime runtime) {
        if (runtime == null) return;
        runtime.spareSurvivalDeadline = 0L;
        int waveReward = computePveWaveReward(runtime);
        for (UUID participantId : runtime.participants) {
            runtime.pendingRewards.merge(participantId, waveReward, Integer::sum);
        }
        clearRemainingPveMobs(runtime);
        if (runtime.currentWave >= runtime.requiredWaves) {
            finishPveZone(runtime);
        } else {
            startNextPveWave(runtime);
        }
    }

    private void finishPveZone(PveZoneRuntime runtime) {
        if (runtime == null) return;
        int completionBonus = computePveCompletionBonus(runtime);
        for (UUID playerId : runtime.participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;
            int reward = runtime.pendingRewards.getOrDefault(playerId, 0) + completionBonus;
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

    private int computePveWaveReward(PveZoneRuntime runtime) {
        if (runtime == null) return 0;
        return 1;
    }

    private int computePveCompletionBonus(PveZoneRuntime runtime) {
        if (runtime == null) return 0;
        return runtime.requiredWaves >= 5 ? 1 : 0;
    }

    private boolean isRangedPveMob(EntityType type) {
        return type == EntityType.SKELETON;
    }

    private void choosePveObjective(PveZoneRuntime runtime) {
        if (runtime == null || runtime.markers.isEmpty()) return;
        List<PveMobArchetype> objectivePool = runtime.activeMobIds.stream()
                .map(runtime.mobArchetypes::get)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        if (objectivePool.isEmpty()) {
            objectivePool = runtime.markers.stream()
                .flatMap(marker -> marker.archetypes().stream())
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        }
        java.util.Collections.shuffle(objectivePool, new Random(System.nanoTime()));
        PveMobArchetype chosen = objectivePool.get(0);
        runtime.objectiveArchetypeKey = chosen.key();
        runtime.objectiveSingularName = chosen.singularName();
        runtime.objectivePluralName = chosen.pluralName();
        runtime.objectiveMode = runtime.currentWave % 3 == 0 ? PveObjectiveMode.SPARE_TYPE : PveObjectiveMode.KILL_ALL_OF_TYPE;
    }

    private boolean isPveObjectiveComplete(PveZoneRuntime runtime) {
        if (runtime == null) return false;
        return switch (runtime.objectiveMode) {
            case KILL_ALL_OF_TYPE -> countActivePveMobsOfArchetype(runtime, runtime.objectiveArchetypeKey) == 0;
            case SPARE_TYPE -> false;
        };
    }

    private boolean areOnlyObjectiveMobsRemaining(PveZoneRuntime runtime) {
        if (runtime == null || runtime.objectiveMode != PveObjectiveMode.SPARE_TYPE || runtime.activeMobIds.isEmpty()) return false;
        return runtime.activeMobIds.stream()
                .map(runtime.mobArchetypes::get)
                .filter(Objects::nonNull)
                .allMatch(archetype -> archetype.key().equals(runtime.objectiveArchetypeKey));
    }

    private long countActivePveMobsOfArchetype(PveZoneRuntime runtime, String archetypeKey) {
        if (runtime == null || archetypeKey == null) return 0L;
        return runtime.activeMobIds.stream()
                .map(runtime.mobArchetypes::get)
                .filter(Objects::nonNull)
                .filter(archetype -> archetype.key().equals(archetypeKey))
                .count();
    }

    private void clearRemainingPveMobs(PveZoneRuntime runtime) {
        if (runtime == null) return;
        for (UUID mobId : new ArrayList<>(runtime.activeMobIds)) {
            Entity entity = Bukkit.getEntity(mobId);
            if (entity != null) {
                entity.remove();
            }
            runtime.mobHomes.remove(mobId);
            runtime.mobLabels.remove(mobId);
            runtime.mobArchetypes.remove(mobId);
            runtime.mobLevels.remove(mobId);
            runtime.mobLastReachableAt.remove(mobId);
            runtime.mobLastRangedHitAt.remove(mobId);
            runtime.invalidMobIds.remove(mobId);
            runtime.activeMobIds.remove(mobId);
            pveMobZones.remove(mobId);
        }
    }

    private void applyPveMobScaling(PveZoneRuntime runtime, LivingEntity living, int level, boolean fillHealth) {
        if (runtime == null || living == null) return;
        double playerScale = 1.0 + Math.max(0, runtime.participants.size() - 1) * 0.35;
        double areaScale = 1.0 + computePveAreaTier(runtime.floorArea) * 0.25;
        double waveScale = 1.0 + Math.max(0, runtime.currentWave - 1) * 0.12;
        PveMobArchetype archetype = runtime.mobArchetypes.get(living.getUniqueId());
        double archetypeHealthScale = archetype == null ? 1.0 : archetype.healthMultiplier();
        double archetypeDamageScale = archetype == null ? 1.0 : archetype.damageMultiplier();
        double maxHealth = (10.0 + level * 6.0) * playerScale * areaScale * waveScale * archetypeHealthScale;
        if (living.getAttribute(Attribute.MAX_HEALTH) != null) {
            double previousMax = living.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
            living.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth);
            if (fillHealth || previousMax <= 0.0) {
                living.setHealth(Math.min(maxHealth, maxHealth));
            } else {
                double scaledHealth = living.getHealth() * (maxHealth / previousMax);
                living.setHealth(Math.max(1.0, Math.min(maxHealth, scaledHealth)));
            }
        }
        if (living.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            double attackDamage = (2.0 + level * 1.75) * playerScale * areaScale * (1.0 + Math.max(0, runtime.currentWave - 1) * 0.08) * archetypeDamageScale;
            living.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(attackDamage);
        }
    }

    private void applyPveMobTheme(LivingEntity living, PveMobArchetype archetype) {
        if (living == null || archetype == null) return;
        if (archetype.helmetColor() != null && archetype.chestColor() != null && archetype.legColor() != null && archetype.bootColor() != null) {
            equipHumanOutfit(living, archetype.helmetColor(), archetype.chestColor(), archetype.legColor(), archetype.bootColor());
        }
        setMovementSpeed(living, archetype.movementSpeed());
    }

    private String formatPveMobName(PveMobArchetype archetype, String label) {
        ChatColor color = archetype == null || archetype.nameColor() == null ? ChatColor.RED : archetype.nameColor();
        return color + label;
    }

    private void equipHumanOutfit(LivingEntity living, Color helmetColor, Color chestColor, Color legColor, Color bootColor) {
        if (living == null || !isSunArmorMob(living.getType())) return;
        EntityEquipment equipment = living.getEquipment();
        if (equipment == null) return;
        equipment.setHelmet(createColoredLeather(Material.LEATHER_HELMET, helmetColor));
        equipment.setChestplate(createColoredLeather(Material.LEATHER_CHESTPLATE, chestColor));
        equipment.setLeggings(createColoredLeather(Material.LEATHER_LEGGINGS, legColor));
        equipment.setBoots(createColoredLeather(Material.LEATHER_BOOTS, bootColor));
        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
    }

    private void equipPveCombatItem(LivingEntity living) {
        if (living == null) return;
        EntityEquipment equipment = living.getEquipment();
        if (equipment == null) return;
        Material current = equipment.getItemInMainHand().getType();
        if (living.getType() == EntityType.SKELETON && current != Material.BOW) {
            equipment.setItemInMainHand(new ItemStack(Material.BOW));
            equipment.setItemInMainHandDropChance(0.0f);
        } else if (living.getType() == EntityType.DROWNED && current.isAir()) {
            equipment.setItemInMainHand(new ItemStack(Material.STONE_SWORD));
            equipment.setItemInMainHandDropChance(0.0f);
        }
    }

    private ItemStack createColoredLeather(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void setMovementSpeed(LivingEntity living, double speed) {
        if (living == null || living.getAttribute(Attribute.MOVEMENT_SPEED) == null) return;
        living.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(speed);
    }

    private boolean isSunArmorMob(EntityType type) {
        return type == EntityType.ZOMBIE
                || type == EntityType.SKELETON
                || type == EntityType.WITHER_SKELETON
                || type == EntityType.HUSK
                || type == EntityType.DROWNED;
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
        PveMobArchetype archetype = runtime.mobArchetypes.get(mob.getUniqueId());
        if (valid) {
            runtime.invalidMobIds.remove(mob.getUniqueId());
            String desiredName = formatPveMobName(archetype, label);
            if (!desiredName.equals(mob.getCustomName())) {
                mob.setCustomName(desiredName);
            }
        } else {
            runtime.invalidMobIds.add(mob.getUniqueId());
            String desiredName = ChatColor.DARK_GRAY + "[ung\u00fcltig] " + formatPveMobName(archetype, label);
            if (!desiredName.equals(mob.getCustomName())) {
                mob.setCustomName(desiredName);
            }
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
                .filter(i -> i.getCoreLocations().stream().anyMatch(coreLocation -> sameBlock(coreLocation, location)))
                .findFirst();
    }

    private boolean sameBlock(Location a, Location b) {
        return a != null && b != null && a.getWorld() != null && b.getWorld() != null && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    public boolean isPlotAvailable(int gridX, int gridZ) {
        String plotKey = plotKey(gridX, gridZ);
        return !"0:0".equals(plotKey)
                && reservedCleanupPlots.stream().noneMatch(plotKey::equals)
                && reservedCreationPlots.stream().noneMatch(plotKey::equals)
                && islands.values().stream().noneMatch(island -> island.getGridX() == gridX && island.getGridZ() == gridZ);
    }

    public boolean isPlotBeingCleaned(int gridX, int gridZ) {
        return reservedCleanupPlots.contains(plotKey(gridX, gridZ));
    }

    public int getPlotCleanupProgress(int gridX, int gridZ) {
        int tracked = Math.max(0, Math.min(TOTAL_CHUNKS, cleanupProgressByPlot.getOrDefault(plotKey(gridX, gridZ), 0)));
        for (IslandAreaCleanupTask task : islandAreaCleanupQueue) {
            if (task.gridX() == gridX && task.gridZ() == gridZ) {
                tracked = Math.max(tracked, Math.max(0, Math.min(TOTAL_CHUNKS, task.nextChunkIndex())));
            }
        }
        return tracked;
    }

    public double getPlotCleanupPercent(int gridX, int gridZ) {
        double percent = (getPlotCleanupProgress(gridX, gridZ) * 100.0D) / TOTAL_CHUNKS;
        if (percent > 0.0D && percent < 0.1D) {
            return 0.1D;
        }
        return Math.min(100.0D, percent);
    }

    public long getPlotCleanupEtaSeconds(int gridX, int gridZ) {
        int remainingChunks = Math.max(0, TOTAL_CHUNKS - getPlotCleanupProgress(gridX, gridZ));
        return (long) Math.ceil(remainingChunks / 40.0D);
    }

    public int getPlotCleanupQueuePosition(int gridX, int gridZ) {
        String targetKey = plotKey(gridX, gridZ);
        int pos = 1;
        for (IslandAreaCleanupTask task : islandAreaCleanupQueue) {
            if (plotKey(task.gridX(), task.gridZ()).equals(targetKey)) {
                return pos;
            }
            pos++;
        }
        return reservedCleanupPlots.contains(targetKey) ? 1 : -1;
    }

    public long getPlotCleanupQueueWaitSeconds(int gridX, int gridZ) {
        String targetKey = plotKey(gridX, gridZ);
        long remainingChunksAhead = 0L;
        for (IslandAreaCleanupTask task : islandAreaCleanupQueue) {
            if (plotKey(task.gridX(), task.gridZ()).equals(targetKey)) {
                break;
            }
            remainingChunksAhead += Math.max(0, TOTAL_CHUNKS - task.nextChunkIndex());
        }
        return (long) Math.ceil(remainingChunksAhead / 40.0D);
    }

    public int getPregenerationQueueSize() {
        return pregenerationQueue.size();
    }

    public int getCleanupQueueSize() {
        return islandAreaCleanupQueue.size();
    }

    public java.util.List<PregenerationTask> getPregenerationTasks() {
        return new java.util.ArrayList<>(pregenerationQueue);
    }

    public java.util.List<IslandAreaCleanupTask> getCleanupTasks() {
        return new java.util.ArrayList<>(islandAreaCleanupQueue);
    }

    public boolean hasBackgroundQueueWork() {
        return !pregenerationQueue.isEmpty() || !islandAreaCleanupQueue.isEmpty();
    }

    public boolean mustWaitForRecreationQueue(UUID playerId) {
        if (playerId == null || !recreationCooldownOwners.contains(playerId)) {
            return false;
        }
        if (org.bukkit.Bukkit.getOfflinePlayer(playerId).isOp()) {
            recreationCooldownOwners.remove(playerId);
            saveCleanupReservations(true);
            return false;
        }
        if (hasBackgroundQueueWork()) {
            return true;
        }
        recreationCooldownOwners.remove(playerId);
        saveCleanupReservations(true);
        return false;
    }

    public String getRecreationQueueWaitMessage(UUID playerId) {
        if (!mustWaitForRecreationQueue(playerId)) {
            return null;
        }
        return "Du kannst erst wieder eine Insel erstellen, wenn die Warteschlange leer ist. "
                + "Generierung: " + getPregenerationQueueSize()
                + ", L\u00f6schung: " + getCleanupQueueSize() + ".";
    }

    public String getIslandCreationThrottleMessage(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        IslandCreationThrottleState state = islandCreationThrottleStates.get(playerId);
        if (state == null) {
            return null;
        }
        normalizeIslandCreationThrottleState(playerId, state, true);
        state = islandCreationThrottleStates.get(playerId);
        if (state == null) {
            return null;
        }
        if (state.cooldownUntil <= System.currentTimeMillis()) {
            return null;
        }
        long remaining = Math.max(0L, state.cooldownUntil - System.currentTimeMillis());
        return "Du hast zuletzt zu oft eine Inselerstellung gestartet. Bitte warte noch " + formatDurationShort(remaining) + ".";
    }

    public int getIslandPregenerationQueuePosition(UUID owner) {
        if (owner == null) return -1;
        IslandData island = islands.get(owner);
        if (island == null || isIslandFullyPregenerated(island)) return -1;
        int pos = 1;
        for (PregenerationTask task : pregenerationQueue) {
            if (task.islandOwner().equals(owner)) {
                return pos;
            }
            pos++;
        }
        return queuedPregenerationOwners.contains(owner) ? 1 : -1;
    }

    private boolean isIslandPlotPendingDeletion(IslandData island) {
        return island != null && reservedCleanupPlots.contains(plotKey(island.getGridX(), island.getGridZ()));
    }

    private IslandPlot findNextFreePlot() {
        Set<String> used = new HashSet<>();
        used.add("0:0");
        for (IslandData island : islands.values()) used.add(island.getGridX() + ":" + island.getGridZ());
        used.addAll(reservedCleanupPlots);
        used.addAll(reservedCreationPlots);
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

    private String plotKey(int gridX, int gridZ) {
        return gridX + ":" + gridZ;
    }

    public String chunkKey(int x, int z) { return x + ":" + z; }

    private String blockKey(Location location) {
        return location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0F;
        if (normalized < 0.0F) normalized += 360.0F;
        return normalized;
    }

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

    private Document toDocument(IslandData island) {
        Document document = Document.createDocument("owner", island.getOwner().toString())
                .put("gridX", island.getGridX())
                .put("gridZ", island.getGridZ())
                .put("level", island.getLevel())
                .put("availableChunkUnlocks", island.getAvailableChunkUnlocks())
                .put("title", island.getTitle())
                .put("warpName", island.getWarpName())
                .put("coreDisplayMode", island.getCoreDisplayMode())
                .put("pinnedUpgradeKey", island.getPinnedUpgradeKey())
                .put("islandTimeMode", island.getIslandTimeMode())
                .put("islandWeatherMode", island.getIslandWeatherMode())
                .put("islandSnowMode", island.getIslandSnowMode())
                .put("islandNightVisionEnabled", island.isIslandNightVisionEnabled())
                .put("points", island.getPoints())
                .put("storedExperience", island.getStoredExperience())
                .put("lastActiveAt", island.getLastActiveAt())
                .put("inactivityWarningStage", island.getInactivityWarningStage())
                .put("spawn", locationDocument(island.getIslandSpawn()))
                .put("warp", locationDocument(island.getWarpLocation()))
                .put("core", locationDocument(island.getCoreLocation()))
                .put("cores", island.getCoreLocations().stream().map(this::locationDocument).toList())
                .put("memberBuildAccess", stringify(island.getMemberBuildAccess()))
                .put("memberContainerAccess", stringify(island.getMemberContainerAccess()))
                .put("memberRedstoneAccess", stringify(island.getMemberRedstoneAccess()))
                .put("masters", stringify(island.getMasters()))
                .put("owners", stringify(island.getOwners()))
                .put("islandBanned", stringify(island.getIslandBanned()))
                .put("unlockedChunks", new ArrayList<>(island.getUnlockedChunks()))
                .put("generatedChunks", new ArrayList<>(island.getGeneratedChunks()))
                .put("nightVisionChunks", new ArrayList<>(island.getNightVisionChunks()))
                .put("visitorSettings", accessSettingsDocument(island.getIslandVisitorSettings()))
                .put("progress", new LinkedHashMap<>(island.getProgress()))
                .put("upgradeTiers", new LinkedHashMap<>(island.getUpgradeTiers()))
                .put("cacheBlocks", new LinkedHashMap<>(island.getCachedBlockCounts()))
                .put("checkpointPlateYaw", new LinkedHashMap<>(island.getCheckpointPlateYaw()))
                .put("checkpointStructures", new LinkedHashMap<>(island.getCheckpointStructures()))
                .put("growthBoostUntil", new LinkedHashMap<>(island.getGrowthBoostUntil()))
                .put("growthBoostTier", new LinkedHashMap<>(island.getGrowthBoostTier()));

        List<Document> parcels = new ArrayList<>();
        for (ParcelData parcel : island.getParcels().values()) {
            parcels.add(parcelDocument(parcel));
        }
        document.put("parcels", parcels);
        return document;
    }

    private Document cleanupTaskDocument(IslandAreaCleanupTask task) {
        return Document.createDocument("type", "task")
                .put("islandOwner", task.islandOwner().toString())
                .put("gridX", task.gridX())
                .put("gridZ", task.gridZ())
                .put("nextChunkIndex", task.nextChunkIndex());
    }

    private Document cleanupCooldownDocument(UUID owner) {
        return Document.createDocument("type", "cooldown")
                .put("owner", owner.toString());
    }

    private Document creationRateLimitDocument(UUID playerId, IslandCreationThrottleState state) {
        return Document.createDocument("type", "creation_rate_limit")
                .put("playerId", playerId.toString())
                .put("requestCount", state.requestCount)
                .put("lastRequestAt", state.lastRequestAt)
                .put("cooldownUntil", state.cooldownUntil);
    }

    private boolean isIslandCreationRateLimited(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        if (org.bukkit.Bukkit.getOfflinePlayer(playerId).isOp()) {
            return false;
        }
        IslandCreationThrottleState state = islandCreationThrottleStates.get(playerId);
        if (state == null) {
            return false;
        }
        normalizeIslandCreationThrottleState(playerId, state, true);
        state = islandCreationThrottleStates.get(playerId);
        if (state == null) {
            return false;
        }
        return state.cooldownUntil > System.currentTimeMillis();
    }

    private void registerIslandCreationRequest(UUID playerId) {
        if (playerId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        IslandCreationThrottleState state = islandCreationThrottleStates.get(playerId);
        if (state == null) {
            state = new IslandCreationThrottleState(0, 0L, 0L);
            islandCreationThrottleStates.put(playerId, state);
        } else {
            normalizeIslandCreationThrottleState(playerId, state, false);
            state = islandCreationThrottleStates.get(playerId);
            if (state == null) {
                state = new IslandCreationThrottleState(0, 0L, 0L);
                islandCreationThrottleStates.put(playerId, state);
            }
        }
        state.requestCount++;
        state.lastRequestAt = now;
        state.cooldownUntil = now + islandCreationCooldownForRequest(state.requestCount);
        saveCleanupReservations(true);
    }

    private void normalizeIslandCreationThrottleState(UUID playerId, IslandCreationThrottleState state, boolean persistIfChanged) {
        if (playerId == null || state == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        if (state.lastRequestAt > 0L && now - state.lastRequestAt >= CREATION_REQUEST_RESET_WINDOW_MS) {
            islandCreationThrottleStates.remove(playerId);
            changed = true;
        } else if (state.cooldownUntil <= now && state.requestCount <= 0) {
            islandCreationThrottleStates.remove(playerId);
            changed = true;
        }
        if (changed && persistIfChanged) {
            saveCleanupReservations(true);
        }
    }

    private long islandCreationCooldownForRequest(int requestCount) {
        if (requestCount >= 4) {
            return 7L * ONE_DAY_MS;
        }
        if (requestCount == 3) {
            return 3L * ONE_DAY_MS;
        }
        if (requestCount == 2) {
            return ONE_DAY_MS;
        }
        return 0L;
    }

    private String formatDurationShort(long millis) {
        long safeMillis = Math.max(0L, millis);
        long totalMinutes = (safeMillis + 59_999L) / 60_000L;
        long days = totalMinutes / (24L * 60L);
        long hours = (totalMinutes / 60L) % 24L;
        long minutes = totalMinutes % 60L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1L, minutes) + "m";
    }

    @SuppressWarnings("unchecked")
    private IslandData fromDocument(Document document) {
        UUID owner = UUID.fromString(document.get("owner", String.class));
        IslandData island = new IslandData(owner);
        island.setGridX(intValue(document.get("gridX")));
        island.setGridZ(intValue(document.get("gridZ")));
        island.setLevel(Math.max(1, intValue(document.get("level"))));
        island.setAvailableChunkUnlocks(Math.max(0, intValue(document.get("availableChunkUnlocks"))));
        island.setTitle(document.get("title", String.class));
        island.setWarpName(document.get("warpName", String.class));
        island.setCoreDisplayMode(defaultString(document.get("coreDisplayMode", String.class), "ALL"));
        island.setPinnedUpgradeKey(defaultString(document.get("pinnedUpgradeKey", String.class), "MILESTONE"));
        island.setIslandTimeMode(defaultString(document.get("islandTimeMode", String.class), "NORMAL"));
        island.setIslandWeatherMode(defaultString(document.get("islandWeatherMode", String.class), "NORMAL"));
        island.setIslandSnowMode(defaultString(document.get("islandSnowMode", String.class), "NORMAL"));
        island.setIslandNightVisionEnabled(Boolean.TRUE.equals(document.get("islandNightVisionEnabled", Boolean.class)));
        island.setPoints(longValue(document.get("points")));
        island.setStoredExperience(longValue(document.get("storedExperience")));
        island.setLastActiveAt(longValue(document.get("lastActiveAt")));
        island.setInactivityWarningStage(intValue(document.get("inactivityWarningStage")));
        island.setIslandSpawn(locationFromDocument(document.get("spawn", Document.class)));
        island.setWarpLocation(locationFromDocument(document.get("warp", Document.class)));
        island.setCoreLocation(locationFromDocument(document.get("core", Document.class)));
        List<Document> coreDocuments = (List<Document>) document.get("cores", List.class);
        if (coreDocuments != null) {
            for (Document coreDocument : coreDocuments) {
                island.addCoreLocation(locationFromDocument(coreDocument));
            }
        }
        addUuidStrings((List<String>) document.get("memberBuildAccess", List.class), island.getMemberBuildAccess());
        addUuidStrings((List<String>) document.get("trusted", List.class), island.getMemberBuildAccess());
        addUuidStrings((List<String>) document.get("memberContainerAccess", List.class), island.getMemberContainerAccess());
        addUuidStrings((List<String>) document.get("trustedContainers", List.class), island.getMemberContainerAccess());
        addUuidStrings((List<String>) document.get("memberRedstoneAccess", List.class), island.getMemberRedstoneAccess());
        addUuidStrings((List<String>) document.get("trustedRedstone", List.class), island.getMemberRedstoneAccess());
        addUuidStrings((List<String>) document.get("masters", List.class), island.getMasters());
        addUuidStrings((List<String>) document.get("coOwners", List.class), island.getMasters());
        addUuidStrings((List<String>) document.get("owners", List.class), island.getOwners());
        addUuidStrings((List<String>) document.get("islandOwners", List.class), island.getOwners());
        addUuidStrings((List<String>) document.get("islandBanned", List.class), island.getIslandBanned());
        addStrings((List<String>) document.get("unlockedChunks", List.class), island.getUnlockedChunks());
        addStrings((List<String>) document.get("generatedChunks", List.class), island.getGeneratedChunks());
        addStrings((List<String>) document.get("nightVisionChunks", List.class), island.getNightVisionChunks());
        applyAccessSettings(document.get("visitorSettings", Document.class), island.getIslandVisitorSettings());
        putIntMap((Map<String, Object>) document.get("progress", Map.class), island.getProgress());
        putIntMap((Map<String, Object>) document.get("upgradeTiers", Map.class), island.getUpgradeTiers());
        putIntMap((Map<String, Object>) document.get("cacheBlocks", Map.class), island.getCachedBlockCounts());
        putFloatMap((Map<String, Object>) document.get("checkpointPlateYaw", Map.class), island.getCheckpointPlateYaw());
        putStringMap((Map<String, Object>) document.get("checkpointStructures", Map.class), island.getCheckpointStructures());
        putLongMap((Map<String, Object>) document.get("growthBoostUntil", Map.class), island.getGrowthBoostUntil());
        putIntMap((Map<String, Object>) document.get("growthBoostTier", Map.class), island.getGrowthBoostTier());
        List<Document> parcels = (List<Document>) document.get("parcels", List.class);
        if (parcels != null) {
            for (Document parcelDocument : parcels) {
                ParcelData parcel = parcelFromDocument(parcelDocument);
                island.getParcels().put(parcel.getChunkKey(), parcel);
            }
        }
        return island;
    }

    private Document parcelDocument(ParcelData parcel) {
        Document document = Document.createDocument("chunkKey", parcel.getChunkKey())
                .put("name", getParcelDisplayName(parcel))
                .put("owners", stringify(parcel.getOwners()))
                .put("users", stringify(parcel.getUsers()))
                .put("banned", stringify(parcel.getBanned()))
                .put("pvpWhitelist", stringify(parcel.getPvpWhitelist()))
                .put("spawn", locationDocument(parcel.getSpawn()))
                .put("combatMode", parcel.getCombatMode().name())
                .put("pvpEnabled", parcel.isPvpEnabled())
                .put("gamesEnabled", parcel.isGamesEnabled())
                .put("ctfEnabled", parcel.isCtfEnabled())
                .put("snowballFightEnabled", parcel.isSnowballFightEnabled())
                .put("timeMode", parcel.getTimeMode())
                .put("weatherMode", parcel.getWeatherMode())
                .put("snowMode", parcel.getSnowMode())
                .put("nightVisionEnabled", parcel.isNightVisionEnabled())
                .put("countdownDurationSeconds", parcel.getCountdownDurationSeconds())
                .put("countdownStartAt", parcel.getCountdownStartAt())
                .put("countdownEndsAt", parcel.getCountdownEndsAt())
                .put("pvpCompassEnabled", parcel.isPvpCompassEnabled())
                .put("pveEnabled", parcel.isPveEnabled())
                .put("saleOfferEnabled", parcel.isSaleOfferEnabled())
                .put("salePrice", parcel.getSalePrice())
                .put("rentOfferEnabled", parcel.isRentOfferEnabled())
                .put("rentPrice", parcel.getRentPrice())
                .put("paymentType", parcel.getPaymentType().name())
                .put("rentDurationAmount", parcel.getRentDurationAmount())
                .put("rentDurationUnit", parcel.getRentDurationUnit().name())
                .put("rentDurationDays", parcel.getRentDurationDays())
                .put("renter", parcel.getRenter() == null ? null : parcel.getRenter().toString())
                .put("rentUntil", parcel.getRentUntil())
                .put("lastSaleBuyer", parcel.getLastSaleBuyer() == null ? null : parcel.getLastSaleBuyer().toString())
                .put("lastSalePrice", parcel.getLastSalePrice())
                .put("lastSalePaymentType", parcel.getLastSalePaymentType().name())
                .put("lastRentBuyer", parcel.getLastRentBuyer() == null ? null : parcel.getLastRentBuyer().toString())
                .put("lastRentPrice", parcel.getLastRentPrice())
                .put("lastRentPaymentType", parcel.getLastRentPaymentType().name())
                .put("memberSettings", accessSettingsDocument(parcel.getMemberSettings()))
                .put("memberAnimalBreed", parcel.isMemberAnimalBreed())
                .put("memberAnimalKill", parcel.isMemberAnimalKill())
                .put("memberAnimalKeepTwo", parcel.isMemberAnimalKeepTwo())
                .put("memberAnimalShear", parcel.isMemberAnimalShear())
                .put("minX", parcel.getMinX())
                .put("minY", parcel.getMinY())
                .put("minZ", parcel.getMinZ())
                .put("maxX", parcel.getMaxX())
                .put("maxY", parcel.getMaxY())
                .put("maxZ", parcel.getMaxZ())
                .put("visitorSettings", accessSettingsDocument(parcel.getVisitorSettings()));
        Map<String, Integer> pvpKills = new LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> entry : parcel.getPvpKills().entrySet()) {
            pvpKills.put(entry.getKey().toString(), Math.max(0, entry.getValue()));
        }
        document.put("pvpKills", pvpKills);
        return document;
    }

    @SuppressWarnings("unchecked")
    private ParcelData parcelFromDocument(Document document) {
        ParcelData parcel = new ParcelData(document.get("chunkKey", String.class));
        parcel.setName(defaultString(document.get("name", String.class), parcel.getChunkKey()));
        addUuidStrings((List<String>) document.get("owners", List.class), parcel.getOwners());
        addUuidStrings((List<String>) document.get("users", List.class), parcel.getUsers());
        addUuidStrings((List<String>) document.get("banned", List.class), parcel.getBanned());
        addUuidStrings((List<String>) document.get("pvpWhitelist", List.class), parcel.getPvpWhitelist());
        parcel.setSpawn(locationFromDocument(document.get("spawn", Document.class)));
        applyParcelCombatMode(
                parcel,
                document.get("combatMode", String.class),
                Boolean.TRUE.equals(document.get("pvpEnabled", Boolean.class)),
                Boolean.TRUE.equals(document.get("gamesEnabled", Boolean.class))
        );
        parcel.setCtfEnabled(Boolean.TRUE.equals(document.get("ctfEnabled", Boolean.class)));
        parcel.setSnowballFightEnabled(Boolean.TRUE.equals(document.get("snowballFightEnabled", Boolean.class)));
        parcel.setTimeMode(defaultString(document.get("timeMode", String.class), "NORMAL"));
        parcel.setWeatherMode(defaultString(document.get("weatherMode", String.class), "NORMAL"));
        parcel.setSnowMode(defaultString(document.get("snowMode", String.class), "NORMAL"));
        parcel.setNightVisionEnabled(Boolean.TRUE.equals(document.get("nightVisionEnabled", Boolean.class)));
        int countdownDurationSeconds = intValue(document.get("countdownDurationSeconds"));
        parcel.setCountdownDurationSeconds(countdownDurationSeconds > 0 ? countdownDurationSeconds : 300);
        parcel.setCountdownStartAt(longValue(document.get("countdownStartAt")));
        parcel.setCountdownEndsAt(longValue(document.get("countdownEndsAt")));
        parcel.setPvpCompassEnabled(!Boolean.FALSE.equals(document.get("pvpCompassEnabled", Boolean.class)));
        parcel.setPveEnabled(Boolean.TRUE.equals(document.get("pveEnabled", Boolean.class)));
        parcel.setSaleOfferEnabled(Boolean.TRUE.equals(document.get("saleOfferEnabled", Boolean.class)));
        parcel.setSalePrice(longValue(document.get("salePrice")));
        parcel.setRentOfferEnabled(Boolean.TRUE.equals(document.get("rentOfferEnabled", Boolean.class)));
        parcel.setRentPrice(longValue(document.get("rentPrice")));
        String paymentType = document.get("paymentType", String.class);
        try {
            parcel.setPaymentType(paymentType == null ? ParcelData.MarketPaymentType.EXPERIENCE : ParcelData.MarketPaymentType.valueOf(paymentType));
        } catch (IllegalArgumentException ignored) {
            parcel.setPaymentType(ParcelData.MarketPaymentType.EXPERIENCE);
        }
        int rentDurationAmount = intValue(document.get("rentDurationAmount"));
        String rentDurationUnit = document.get("rentDurationUnit", String.class);
        if (rentDurationAmount > 0) {
            parcel.setRentDurationAmount(rentDurationAmount);
            try {
                parcel.setRentDurationUnit(rentDurationUnit == null ? ParcelData.RentDurationUnit.DAYS : ParcelData.RentDurationUnit.valueOf(rentDurationUnit));
            } catch (IllegalArgumentException ignored) {
                parcel.setRentDurationUnit(ParcelData.RentDurationUnit.DAYS);
            }
        } else {
            parcel.setRentDurationDays(intValue(document.get("rentDurationDays")));
        }
        String renter = document.get("renter", String.class);
        if (renter != null && !renter.isBlank()) {
            parcel.setRenter(UUID.fromString(renter));
        }
        parcel.setRentUntil(longValue(document.get("rentUntil")));
        applyAccessSettings(document.get("memberSettings", Document.class), parcel.getMemberSettings());
        parcel.setMemberAnimalBreed(Boolean.TRUE.equals(document.get("memberAnimalBreed", Boolean.class)));
        parcel.setMemberAnimalKill(Boolean.TRUE.equals(document.get("memberAnimalKill", Boolean.class)));
        Boolean memberAnimalKeepTwo = document.get("memberAnimalKeepTwo", Boolean.class);
        parcel.setMemberAnimalKeepTwo(memberAnimalKeepTwo == null || memberAnimalKeepTwo);
        parcel.setMemberAnimalShear(Boolean.TRUE.equals(document.get("memberAnimalShear", Boolean.class)));
        String lastSaleBuyer = document.get("lastSaleBuyer", String.class);
        if (lastSaleBuyer != null && !lastSaleBuyer.isBlank()) {
            parcel.setLastSaleBuyer(UUID.fromString(lastSaleBuyer));
        }
        parcel.setLastSalePrice(longValue(document.get("lastSalePrice")));
        try {
            parcel.setLastSalePaymentType(ParcelData.MarketPaymentType.valueOf(defaultString(document.get("lastSalePaymentType", String.class), ParcelData.MarketPaymentType.EXPERIENCE.name())));
        } catch (IllegalArgumentException ignored) {
            parcel.setLastSalePaymentType(ParcelData.MarketPaymentType.EXPERIENCE);
        }
        String lastRentBuyer = document.get("lastRentBuyer", String.class);
        if (lastRentBuyer != null && !lastRentBuyer.isBlank()) {
            parcel.setLastRentBuyer(UUID.fromString(lastRentBuyer));
        }
        parcel.setLastRentPrice(longValue(document.get("lastRentPrice")));
        try {
            parcel.setLastRentPaymentType(ParcelData.MarketPaymentType.valueOf(defaultString(document.get("lastRentPaymentType", String.class), ParcelData.MarketPaymentType.EXPERIENCE.name())));
        } catch (IllegalArgumentException ignored) {
            parcel.setLastRentPaymentType(ParcelData.MarketPaymentType.EXPERIENCE);
        }
        parcel.setBounds(
                intValue(document.get("minX")),
                intValue(document.get("minY")),
                intValue(document.get("minZ")),
                intValue(document.get("maxX")),
                intValue(document.get("maxY")),
                intValue(document.get("maxZ"))
        );
        applyAccessSettings(document.get("visitorSettings", Document.class), parcel.getVisitorSettings());
        Map<String, Object> pvpKills = (Map<String, Object>) document.get("pvpKills", Map.class);
        if (pvpKills != null) {
            for (Map.Entry<String, Object> entry : pvpKills.entrySet()) {
                parcel.getPvpKills().put(UUID.fromString(entry.getKey()), Math.max(0, intValue(entry.getValue())));
            }
        }
        return parcel;
    }

    private void applyParcelCombatMode(ParcelData parcel, String rawCombatMode, boolean pvpEnabled, boolean gamesEnabled) {
        if (parcel == null) return;
        if (rawCombatMode != null) {
            try {
                parcel.setCombatMode(ParcelData.CombatMode.valueOf(rawCombatMode));
                return;
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (gamesEnabled) {
            parcel.setCombatMode(ParcelData.CombatMode.GAMES);
            return;
        }
        if (pvpEnabled) {
            parcel.setCombatMode(ParcelData.CombatMode.PVP);
            return;
        }
        parcel.setCombatMode(ParcelData.CombatMode.NONE);
    }

    private Document accessSettingsDocument(AccessSettings settings) {
        if (settings == null) return null;
        return Document.createDocument("doors", settings.isDoors())
                .put("trapdoors", settings.isTrapdoors())
                .put("fenceGates", settings.isFenceGates())
                .put("buttons", settings.isButtons())
                .put("levers", settings.isLevers())
                .put("pressurePlates", settings.isPressurePlates())
                .put("containers", settings.isContainers())
                .put("farmUse", settings.isFarmUse())
                .put("ride", settings.isRide())
                .put("redstoneUse", settings.isRedstoneUse())
                .put("ladderPlace", settings.isLadderPlace())
                .put("ladderBreak", settings.isLadderBreak())
                .put("leavesPlace", settings.isLeavesPlace())
                .put("leavesBreak", settings.isLeavesBreak())
                .put("buckets", settings.isBuckets())
                .put("decorations", settings.isDecorations())
                .put("villagers", settings.isVillagers())
                .put("vehicleDestroy", settings.isVehicleDestroy())
                .put("snowPlace", settings.isSnowPlace())
                .put("snowBreak", settings.isSnowBreak())
                .put("bannerPlace", settings.isBannerPlace())
                .put("bannerBreak", settings.isBannerBreak())
                .put("teleport", settings.isTeleport());
    }

    private void applyAccessSettings(Document document, AccessSettings settings) {
        if (document == null || settings == null) return;
        settings.setDoors(Boolean.TRUE.equals(document.get("doors", Boolean.class)));
        settings.setTrapdoors(Boolean.TRUE.equals(document.get("trapdoors", Boolean.class)));
        settings.setFenceGates(Boolean.TRUE.equals(document.get("fenceGates", Boolean.class)));
        settings.setButtons(Boolean.TRUE.equals(document.get("buttons", Boolean.class)));
        settings.setLevers(Boolean.TRUE.equals(document.get("levers", Boolean.class)));
        settings.setPressurePlates(Boolean.TRUE.equals(document.get("pressurePlates", Boolean.class)));
        settings.setContainers(Boolean.TRUE.equals(document.get("containers", Boolean.class)));
        settings.setFarmUse(Boolean.TRUE.equals(document.get("farmUse", Boolean.class)));
        settings.setRide(Boolean.TRUE.equals(document.get("ride", Boolean.class)));
        settings.setRedstoneUse(Boolean.TRUE.equals(document.get("redstoneUse", Boolean.class)));
        settings.setLadderPlace(Boolean.TRUE.equals(document.get("ladderPlace", Boolean.class)));
        settings.setLadderBreak(Boolean.TRUE.equals(document.get("ladderBreak", Boolean.class)));
        settings.setLeavesPlace(Boolean.TRUE.equals(document.get("leavesPlace", Boolean.class)));
        settings.setLeavesBreak(Boolean.TRUE.equals(document.get("leavesBreak", Boolean.class)));
        settings.setBuckets(Boolean.TRUE.equals(document.get("buckets", Boolean.class)));
        settings.setDecorations(Boolean.TRUE.equals(document.get("decorations", Boolean.class)));
        settings.setVillagers(Boolean.TRUE.equals(document.get("villagers", Boolean.class)));
        settings.setVehicleDestroy(Boolean.TRUE.equals(document.get("vehicleDestroy", Boolean.class)));
        settings.setSnowPlace(Boolean.TRUE.equals(document.get("snowPlace", Boolean.class)));
        settings.setSnowBreak(Boolean.TRUE.equals(document.get("snowBreak", Boolean.class)));
        settings.setBannerPlace(Boolean.TRUE.equals(document.get("bannerPlace", Boolean.class)));
        settings.setBannerBreak(Boolean.TRUE.equals(document.get("bannerBreak", Boolean.class)));
        Boolean teleport = document.get("teleport", Boolean.class);
        settings.setTeleport(teleport == null || teleport);
    }

    private Document locationDocument(Location location) {
        if (location == null || location.getWorld() == null) return null;
        return Document.createDocument("world", location.getWorld().getName())
                .put("x", location.getX())
                .put("y", location.getY())
                .put("z", location.getZ())
                .put("yaw", location.getYaw())
                .put("pitch", location.getPitch());
    }

    private Location locationFromDocument(Document document) {
        if (document == null) return null;
        World world = Bukkit.getWorld(defaultString(document.get("world", String.class), SkyWorldService.WORLD_NAME));
        if (world == null) world = skyWorldService.getWorld();
        if (world == null) return null;
        return new Location(
                world,
                doubleValue(document.get("x")),
                doubleValue(document.get("y")),
                doubleValue(document.get("z")),
                (float) doubleValue(document.get("yaw")),
                (float) doubleValue(document.get("pitch"))
        );
    }

    private void addUuidStrings(List<String> values, Set<UUID> target) {
        if (values == null || target == null) return;
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            target.add(UUID.fromString(value));
        }
    }

    private void addStrings(List<String> values, Set<String> target) {
        if (values == null || target == null) return;
        for (String value : values) {
            if (value != null && !value.isBlank()) target.add(value);
        }
    }

    private void putIntMap(Map<String, Object> source, Map<String, Integer> target) {
        if (source == null || target == null) return;
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            target.put(entry.getKey(), intValue(entry.getValue()));
        }
    }

    private void putLongMap(Map<String, Object> source, Map<String, Long> target) {
        if (source == null || target == null) return;
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            target.put(entry.getKey(), longValue(entry.getValue()));
        }
    }

    private void putFloatMap(Map<String, Object> source, Map<String, Float> target) {
        if (source == null || target == null) return;
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            target.put(entry.getKey(), floatValue(entry.getValue()));
        }
    }

    private void putStringMap(Map<String, Object> source, Map<String, String> target) {
        if (source == null || target == null) return;
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getValue() != null) {
                target.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0D;
    }

    private float floatValue(Object value) {
        return value instanceof Number number ? number.floatValue() : 0.0F;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void backupLegacyYaml() {
        if (!legacyDataFile.exists()) return;
        File backup = new File(legacyDataFile.getParentFile(), legacyDataFile.getName() + ".migrated");
        if (backup.exists()) return;
        if (!legacyDataFile.renameTo(backup)) {
            plugin.getLogger().warning("Konnte alte islands.yml nach der Nitrite-Migration nicht umbenennen.");
        }
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
        yaml.set(path + ".buckets", settings.isBuckets());
        yaml.set(path + ".decorations", settings.isDecorations());
        yaml.set(path + ".villagers", settings.isVillagers());
        yaml.set(path + ".vehicleDestroy", settings.isVehicleDestroy());
        yaml.set(path + ".snowPlace", settings.isSnowPlace());
        yaml.set(path + ".snowBreak", settings.isSnowBreak());
        yaml.set(path + ".bannerPlace", settings.isBannerPlace());
        yaml.set(path + ".bannerBreak", settings.isBannerBreak());
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
        settings.setBuckets(sec.getBoolean("buckets", settings.isBuckets()));
        settings.setDecorations(sec.getBoolean("decorations", settings.isDecorations()));
        settings.setVillagers(sec.getBoolean("villagers", settings.isVillagers()));
        settings.setVehicleDestroy(sec.getBoolean("vehicleDestroy", settings.isVehicleDestroy()));
        settings.setSnowPlace(sec.getBoolean("snowPlace", settings.isSnowPlace()));
        settings.setSnowBreak(sec.getBoolean("snowBreak", settings.isSnowBreak()));
        settings.setBannerPlace(sec.getBoolean("bannerPlace", sec.getBoolean("woolPlace", settings.isBannerPlace())));
        settings.setBannerBreak(sec.getBoolean("bannerBreak", sec.getBoolean("woolBreak", settings.isBannerBreak())));
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




