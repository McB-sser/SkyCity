package de.mcbesser.skycity.listener;

import de.mcbesser.skycity.SkyCityPlugin;
import de.mcbesser.skycity.model.IslandData;
import de.mcbesser.skycity.model.ParcelData;
import de.mcbesser.skycity.service.CoreService;
import de.mcbesser.skycity.service.IslandService;
import de.mcbesser.skycity.service.SkyWorldService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Transformation;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.RedstoneWire;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerListener implements Listener {
    private static final long PVP_KILL_TIMEOUT_MS = 15_000L;
    private static final long LAVA_RESCUE_PROTECTION_MS = 2_000L;
    private static final long CHECKPOINT_CACHE_TTL_MS = 60_000L;
    private static final long COMBAT_SCOREBOARD_REFRESH_MS = 1_000L;
    private static final long TEAM_SCORE_CACHE_TTL_MS = 1_000L;
    private static final int SKYCITY_NIGHT_VISION_DURATION_TICKS = Integer.MAX_VALUE;
    private static final int SKYCITY_NIGHT_VISION_MIN_DURATION_TICKS = 1_000_000;
    private static final String CHECKPOINT_HOLO_PREFIX = "skycity_checkpoint_holo_";
    private static final String JUMP_PAD_HOLO_PREFIX = "skycity_jump_pad_holo_";
    private static final String PVP_TEAM_WOOL_METADATA = "skycity_pvp_team_wool";
    private static final long JUMP_PAD_COOLDOWN_MS = 1_000L;
    private static final long JUMP_PAD_FALL_PROTECTION_MS = 10_000L;
    private final SkyCityPlugin plugin;
    private final IslandService islandService;
    private final SkyWorldService skyWorldService;
    private final CoreService coreService;
    private final Map<UUID, Integer> preparationStatusTasks = new HashMap<>();
    private final Map<UUID, Integer> islandCreateHintTasks = new HashMap<>();
    private final Map<UUID, Integer> parcelBanCountdownTasks = new HashMap<>();
    private final Map<UUID, String> parcelBanCountdownKeys = new HashMap<>();
    private final Map<UUID, Integer> parcelPvpExitCountdownTasks = new HashMap<>();
    private final Map<UUID, String> parcelPvpExitCountdownKeys = new HashMap<>();
    private final Map<UUID, String> parcelPvpStates = new HashMap<>();
    private final Map<UUID, String> parcelGamesStates = new HashMap<>();
    private final Map<UUID, ParcelData.CombatMode> parcelCombatModes = new HashMap<>();
    private final Map<UUID, String> parcelPveStates = new HashMap<>();
    private final Map<UUID, CombatTag> parcelPvpCombatTags = new HashMap<>();
    private final Map<String, Map<Material, Integer>> parcelManualTeamScores = new HashMap<>();
    private final Map<UUID, Long> pendingMagicSnowballThrows = new HashMap<>();
    private final Map<UUID, String> islandPresenceState = new HashMap<>();
    private final Map<UUID, BossBar> islandBossBars = new HashMap<>();
    private final Map<UUID, BossBar> chunkEffectBossBars = new HashMap<>();
    private final Map<UUID, BossBar> parcelCountdownBossBars = new HashMap<>();
    private final Map<UUID, String> parcelCountdownTitleStates = new HashMap<>();
    private final Map<UUID, Boolean> skyCityNightVisionApplied = new HashMap<>();
    private final Map<UUID, Long> lavaRescueProtectionUntil = new HashMap<>();
    private final Map<UUID, Map<String, Location>> checkpointLocations = new HashMap<>();
    private final Map<UUID, Map<Material, Location>> checkpointLocationsByWool = new HashMap<>();
    private final Map<UUID, Location> lastCheckpointLocations = new HashMap<>();
    private final Map<UUID, Long> linkedCheckpointTeleportUntil = new HashMap<>();
    private final Map<UUID, Long> checkpointParticleTickWindow = new HashMap<>();
    private final Map<UUID, String> lastActivatedCheckpointKey = new HashMap<>();
    private final Map<UUID, Long> jumpPadCooldownUntil = new HashMap<>();
    private final Map<UUID, Long> jumpPadFallProtectionUntil = new HashMap<>();
    private final Map<UUID, CheckpointIndexCache> checkpointIndexCache = new HashMap<>();
    private final Map<String, TeamScoreCache> parcelTeamScoreCache = new HashMap<>();
    private final Map<String, UUID> checkpointDisplaysByTag = new HashMap<>();
    private final Map<String, UUID> jumpPadDisplaysByTag = new HashMap<>();
    private final Map<UUID, Material> parcelPvpTeamWool = new HashMap<>();
    private final Map<UUID, Location> parcelPvpTeamRespawns = new HashMap<>();
    private final Map<UUID, Location> pendingParcelPvpRespawns = new HashMap<>();
    private final Map<UUID, Boolean> parcelPvpCompassSuppressed = new HashMap<>();
    private final Map<UUID, CombatScoreboardState> combatScoreboardStates = new HashMap<>();
    private final Map<String, CtfRuntime> parcelCtfRuntime = new HashMap<>();
    private final Map<UUID, CtfCarrierState> ctfCarrierStates = new HashMap<>();
    private final Map<UUID, UUID> ctfCarryDisplaysByPlayer = new HashMap<>();
    private final int islandActionbarTaskId;
    private final NamespacedKey magicSnowballItemKey;
    private final NamespacedKey magicSnowballProjectileKey;

    private record CombatTag(UUID attackerId, String parcelKey, long createdAt) { }
    private record CheckpointMarker(Material woolType, Material plateType, Location plateLocation) { }
    private record CheckpointIndexCache(long builtAt, Set<String> unlockedChunks, Map<String, List<Location>> exactMatches, Map<String, List<Location>> woolMatches) { }
    private record TeamScoreCache(long builtAt, List<TeamScoreEntry> entries) { }
    private record TeamScoreEntry(Material wool, int points) { }
    private record CombatScoreboardState(String zoneKey, long refreshedAt) { }
    private record CtfFlagDefinition(String id, Material teamWool, Material bannerMaterial, String bannerBlockData, Location targetLocation, Location bannerLocation) { }
    private record CtfShelfDefinition(String id, Material teamWool, Location shelfLocation, int capacity) { }
    private record CtfCarrierState(String parcelKey, String flagId, Material teamWool, Material bannerMaterial, Location baseTargetLocation, Location bannerLocation) { }
    private record CtfRuntime(Map<String, UUID> carrierByFlag, Map<String, String> shelfByFlag, Map<String, CtfFlagDefinition> hiddenFlagsById) { }
    public PlayerListener(SkyCityPlugin plugin, IslandService islandService, SkyWorldService skyWorldService, CoreService coreService) {
        this.plugin = plugin;
        this.islandService = islandService;
        this.skyWorldService = skyWorldService;
        this.coreService = coreService;
        this.magicSnowballItemKey = new NamespacedKey(plugin, "magic_snowball_item");
        this.magicSnowballProjectileKey = new NamespacedKey(plugin, "magic_snowball_projectile");
        this.islandActionbarTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickIslandActionbar, 20L, 20L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        islandService.markIslandActivity(event.getPlayer().getUniqueId());
        stopPreparationStatusMessages(event.getPlayer().getUniqueId());
        stopIslandCreateHintMessages(event.getPlayer().getUniqueId());
        IslandData existing = islandService.getIsland(event.getPlayer().getUniqueId()).orElse(null);
        if (existing != null) {
            islandService.ensureCentralSpawnAndCoreSafe(existing);
            coreService.ensureCorePlaced(existing);
            islandService.ensureTemplateAtLocation(existing, existing.getIslandSpawn());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!event.getPlayer().isOnline()) return;
                stopPreparationStatusMessages(event.getPlayer().getUniqueId());
                event.getPlayer().teleport(existing.getIslandSpawn());
                event.getPlayer().sendMessage(ChatColor.AQUA + "Du wurdest zu deiner Insel teleportiert.");
                if (!islandService.isIslandReady(event.getPlayer().getUniqueId())) {
                    startPreparationStatusMessages(event.getPlayer());
                }
            }, 1L);
            if (!islandService.isIslandReady(event.getPlayer().getUniqueId())) {
                islandService.queuePregeneration(existing);
            }
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            event.getPlayer().teleport(islandService.getSpawnLocation());
            startIslandCreateHintMessages(event.getPlayer());
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getCoreSidebar() != null) {
            plugin.getCoreSidebar().clear(event.getPlayer());
        }
        returnCtfFlagToBase(event.getPlayer().getUniqueId(), false);
        stopPreparationStatusMessages(event.getPlayer().getUniqueId());
        stopIslandCreateHintMessages(event.getPlayer().getUniqueId());
        stopParcelBanCountdown(event.getPlayer().getUniqueId());
        stopParcelPvpExitCountdown(event.getPlayer().getUniqueId());
        clearParcelPvpState(event.getPlayer().getUniqueId());
        clearParcelGamesState(event.getPlayer().getUniqueId());
        parcelPvpCombatTags.remove(event.getPlayer().getUniqueId());
        islandPresenceState.remove(event.getPlayer().getUniqueId());
        clearSkyCityNightVision(event.getPlayer());
        removeIslandBossBar(event.getPlayer());
        removeChunkEffectBossBar(event.getPlayer());
        removeParcelCountdownBossBar(event.getPlayer());
        parcelCountdownTitleStates.remove(event.getPlayer().getUniqueId());
        checkpointLocations.remove(event.getPlayer().getUniqueId());
        checkpointLocationsByWool.remove(event.getPlayer().getUniqueId());
        lastCheckpointLocations.remove(event.getPlayer().getUniqueId());
        lavaRescueProtectionUntil.remove(event.getPlayer().getUniqueId());
        linkedCheckpointTeleportUntil.remove(event.getPlayer().getUniqueId());
        checkpointParticleTickWindow.remove(event.getPlayer().getUniqueId());
        lastActivatedCheckpointKey.remove(event.getPlayer().getUniqueId());
        checkpointIndexCache.remove(event.getPlayer().getUniqueId());
        jumpPadCooldownUntil.remove(event.getPlayer().getUniqueId());
        jumpPadFallProtectionUntil.remove(event.getPlayer().getUniqueId());
        parcelPvpTeamWool.remove(event.getPlayer().getUniqueId());
        parcelPvpTeamRespawns.remove(event.getPlayer().getUniqueId());
        pendingParcelPvpRespawns.remove(event.getPlayer().getUniqueId());
        parcelPvpCompassSuppressed.remove(event.getPlayer().getUniqueId());
        combatScoreboardStates.remove(event.getPlayer().getUniqueId());
        removeCtfCarryDisplay(event.getPlayer().getUniqueId());
        event.getPlayer().removeMetadata(PVP_TEAM_WOOL_METADATA, plugin);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location pvpRespawn = pendingParcelPvpRespawns.remove(event.getPlayer().getUniqueId());
        if (pvpRespawn != null) {
            event.setRespawnLocation(pvpRespawn);
            return;
        }
        Location pveRespawn = islandService.consumePendingPveRespawn(event.getPlayer().getUniqueId()).orElse(null);
        if (pveRespawn != null) {
            event.setRespawnLocation(pveRespawn);
            return;
        }
        IslandData island = islandService.getIsland(event.getPlayer().getUniqueId()).orElse(null);
        if (island == null || islandService.isIslandCreationPending(event.getPlayer().getUniqueId())) {
            event.setRespawnLocation(islandService.getSpawnLocation());
            return;
        }
        islandService.ensureTemplateAtLocation(island, island.getIslandSpawn());
        event.setRespawnLocation(island.getIslandSpawn());
        if (!islandService.isIslandReady(event.getPlayer().getUniqueId())) {
            islandService.queuePregeneration(island);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (event.getFrom() == null || event.getFrom().getWorld() == null) return;
        if (!skyWorldService.isSkyCityWorld(event.getFrom().getWorld())) return;
        if (isLikelyFarmweltPortal(event.getFrom())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "Vanilla Nether-/End-Portale sind in SkyCity deaktiviert.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (!skyWorldService.isSkyCityWorld(event.getWorld())) return;
        if (isLikelyFarmweltPortal(event)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!skyWorldService.isSkyCityWorld(event.getPlayer().getWorld())) {
            returnCtfFlagToBase(event.getPlayer().getUniqueId(), false);
            stopParcelBanCountdown(event.getPlayer().getUniqueId());
            stopParcelPvpExitCountdown(event.getPlayer().getUniqueId());
            clearParcelPvpState(event.getPlayer().getUniqueId());
            clearParcelGamesState(event.getPlayer().getUniqueId());
            clearParcelPveState(event.getPlayer().getUniqueId());
            islandPresenceState.remove(event.getPlayer().getUniqueId());
            removeIslandBossBar(event.getPlayer());
            removeChunkEffectBossBar(event.getPlayer());
            removeParcelCountdownBossBar(event.getPlayer());
            parcelCountdownTitleStates.remove(event.getPlayer().getUniqueId());
        } else {
            updateParcelPvpState(event.getPlayer(), event.getPlayer().getLocation());
            updateParcelPveState(event.getPlayer(), event.getPlayer().getLocation());
            updateParcelCtfState(event.getPlayer(), event.getPlayer().getLocation());
            updateParcelCountdownState(event.getPlayer(), event.getPlayer().getLocation());
            updateIslandPresenceMessage(event.getPlayer(), true);
        }
        if (!skyWorldService.isSkyCityWorld(event.getPlayer().getWorld())
                && !isAllowedExternalWorld(event.getPlayer().getWorld() == null ? null : event.getPlayer().getWorld().getName())
                && event.getPlayer().getGameMode() != GameMode.SPECTATOR) {
            if (islandService.isIslandCreationPending(event.getPlayer().getUniqueId())) {
                Bukkit.getScheduler().runTask(plugin, () -> event.getPlayer().teleport(islandService.getSpawnLocation()));
                event.getPlayer().sendMessage(ChatColor.YELLOW + "Deine Insel wird vorbereitet.");
                return;
            }
            IslandData island = islandService.getIsland(event.getPlayer().getUniqueId()).orElse(null);
            if (island != null && island.getIslandSpawn() != null) {
                Bukkit.getScheduler().runTask(plugin, () -> event.getPlayer().teleport(island.getIslandSpawn()));
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> event.getPlayer().teleport(islandService.getSpawnLocation()));
                Bukkit.getScheduler().runTask(plugin, () -> startIslandCreateHintMessages(event.getPlayer()));
            }
            event.getPlayer().sendMessage(ChatColor.RED + "Nur die SkyCity-Oberwelt ist erlaubt.");
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (didViewChange(event) && plugin.getCoreSidebar() != null) {
            plugin.getCoreSidebar().scheduleRefresh(event.getPlayer());
        }
        CtfCarrierState carriedFlag = ctfCarrierStates.get(event.getPlayer().getUniqueId());
        if (carriedFlag != null && skyWorldService.isSkyCityWorld(event.getTo().getWorld())) {
            ensureCtfCarryDisplay(event.getPlayer(), carriedFlag);
        }
        if (!skyWorldService.isSkyCityWorld(event.getTo().getWorld())) {
            stopParcelBanCountdown(event.getPlayer().getUniqueId());
            stopParcelPvpExitCountdown(event.getPlayer().getUniqueId());
            clearParcelPvpState(event.getPlayer().getUniqueId());
            clearParcelGamesState(event.getPlayer().getUniqueId());
            clearParcelPveState(event.getPlayer().getUniqueId());
            removeParcelCountdownBossBar(event.getPlayer());
            parcelCountdownTitleStates.remove(event.getPlayer().getUniqueId());
            return;
        }
        boolean changedBlock = event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ();
        if (!changedBlock) return;
        updateCheckpoint(event.getPlayer(), event.getTo());
        if (tryHandleLavaRescue(event.getPlayer(), event.getTo())) return;
        if (changedBlock && tryHandleJumpPad(event.getPlayer(), event.getFrom(), event.getTo())) return;
        if (changedBlock && tryHandleLinkedCheckpointTeleport(event.getPlayer(), event.getFrom(), event.getTo())) return;
        handleParcelBanEntryCountdown(event.getPlayer(), event.getTo());
        handleParcelPvpWhitelistCountdown(event.getPlayer(), event.getTo());
        updateParcelPvpState(event.getPlayer(), event.getTo());
        updateParcelPvpCompass(event.getPlayer(), event.getTo());
        updateParcelPvpTeam(event.getPlayer(), event.getTo());
        updateParcelCtfState(event.getPlayer(), event.getTo());
        updateParcelCountdownState(event.getPlayer(), event.getTo());
        updateParcelPveState(event.getPlayer(), event.getTo());

        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) return;
        updateIslandPresenceMessage(event.getPlayer(), event.getTo(), false);
        islandService.markIslandActivity(event.getPlayer().getUniqueId());
        IslandData island = islandService.getIsland(event.getPlayer().getUniqueId()).orElse(null);
        if (island == null) return;
        stopIslandCreateHintMessages(event.getPlayer().getUniqueId());
        if (islandService.isChunkUnlocked(island, event.getTo())) {
            islandService.ensureTemplateAtLocation(island, event.getTo());
        }
        if (islandService.isPlayerBannedFromLocation(event.getPlayer().getUniqueId(), event.getTo())) {
            event.getPlayer().teleport(islandService.getSpawnLocation());
            event.getPlayer().sendMessage(ChatColor.RED + "Du bist dort gebannt.");
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!consumedItemGrantsNightVision(event.getItem())) return;
        Player player = event.getPlayer();
        if (!Boolean.TRUE.equals(skyCityNightVisionApplied.get(player.getUniqueId()))) return;
        clearSkyCityNightVision(player);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (plugin.getCoreSidebar() != null) {
            if (event.getTo() == null) {
                plugin.getCoreSidebar().clear(event.getPlayer());
            } else {
                plugin.getCoreSidebar().scheduleRefresh(event.getPlayer());
            }
        }
        if (event.getTo() == null || !skyWorldService.isSkyCityWorld(event.getTo().getWorld())) {
            returnCtfFlagToBase(event.getPlayer().getUniqueId(), false);
            clearParcelPvpState(event.getPlayer().getUniqueId());
            clearParcelGamesState(event.getPlayer().getUniqueId());
            clearParcelPveState(event.getPlayer().getUniqueId());
            return;
        }
        tryHandleLavaRescue(event.getPlayer(), event.getTo());
        updateParcelPvpState(event.getPlayer(), event.getTo());
        updateParcelPvpCompass(event.getPlayer(), event.getTo());
        updateParcelPvpTeam(event.getPlayer(), event.getTo());
        updateParcelCtfState(event.getPlayer(), event.getTo());
        updateParcelCountdownState(event.getPlayer(), event.getTo());
        updateParcelPveState(event.getPlayer(), event.getTo());
    }

    private boolean didViewChange(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return false;
        }
        if (event.getFrom().getWorld() != event.getTo().getWorld()) {
            return true;
        }
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            return true;
        }
        return Float.compare(event.getFrom().getYaw(), event.getTo().getYaw()) != 0
                || Float.compare(event.getFrom().getPitch(), event.getTo().getPitch()) != 0;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.HAND
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && isMagicSnowballItem(event.getItem())) {
            pendingMagicSnowballThrows.put(event.getPlayer().getUniqueId(), System.currentTimeMillis() + 2000L);
        }
        if (event.getClickedBlock() == null || event.getPlayer().isOp()) return;
        if (!skyWorldService.isSkyCityWorld(event.getClickedBlock().getWorld())) return;
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (tryPickupCtfFlag(event.getPlayer(), event.getClickedBlock())) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (tryResetCtfViaButton(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
            return;
        }
        if (tryCaptureCtfFlag(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) return;
        if (!(snowball.getShooter() instanceof Player player)) return;
        Long pendingUntil = pendingMagicSnowballThrows.get(player.getUniqueId());
        boolean pending = pendingUntil != null && pendingUntil >= System.currentTimeMillis();
        boolean handMatch = isMagicSnowballItem(player.getInventory().getItemInMainHand())
                || isMagicSnowballItem(player.getInventory().getItemInOffHand());
        if (!pending && !handMatch) {
            pendingMagicSnowballThrows.remove(player.getUniqueId());
            return;
        }
        snowball.getPersistentDataContainer().set(magicSnowballProjectileKey, PersistentDataType.BYTE, (byte)1);
        pendingMagicSnowballThrows.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnvironmentDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!skyWorldService.isSkyCityWorld(player.getWorld())) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.LAVA
                && cause != EntityDamageEvent.DamageCause.FIRE
                && cause != EntityDamageEvent.DamageCause.FIRE_TICK
                && cause != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (cause == EntityDamageEvent.DamageCause.FALL && isJumpPadFallProtected(player.getUniqueId())) {
            event.setCancelled(true);
            jumpPadFallProtectionUntil.remove(player.getUniqueId());
            return;
        }
        if (!isLavaRescueProtected(player.getUniqueId()) && !isTouchingLava(player.getLocation())) return;
        event.setCancelled(true);
        clearLavaEffects(player);
        if (cause == EntityDamageEvent.DamageCause.LAVA) {
            tryHandleLavaRescue(player, player.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!skyWorldService.isSkyCityWorld(player.getWorld())) return;
        if (!isLavaRescueProtected(player.getUniqueId()) && !isTouchingLava(player.getLocation())) return;
        event.setCancelled(true);
        clearLavaEffects(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCtfHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveDamagingPlayer(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;
        if (!skyWorldService.isSkyCityWorld(victim.getWorld())) return;
        IslandData island = islandService.getIslandAt(victim.getLocation());
        ParcelData parcel = island == null ? null : islandService.getParcelAt(island, victim.getLocation());
        if (parcel == null || !parcel.isGamesEnabled() || !parcel.isCtfEnabled()) return;
        CtfCarrierState carrier = ctfCarrierStates.get(victim.getUniqueId());
        if (carrier == null) return;
        String parcelKey = islandService.getParcelPvpKey(island, parcel);
        if (parcelKey == null || !parcelKey.equals(carrier.parcelKey())) return;
        event.setCancelled(true);
        returnCtfFlagToBase(victim.getUniqueId(), true);
        attacker.sendMessage(ChatColor.GREEN + "Du hast die Flagge zur\u00fcckerobert.");
        victim.sendMessage(ChatColor.RED + "Deine getragene Flagge wurde zur Basis zur\u00fcckgeschickt.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMagicSnowballHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Snowball snowball)) return;
        if (!(snowball.getShooter() instanceof Player attacker)) return;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return;
        if (!isMagicSnowballProjectile(snowball) || !skyWorldService.isSkyCityWorld(victim.getWorld())) return;
        IslandData island = islandService.getIslandAt(victim.getLocation());
        ParcelData parcel = island == null ? null : islandService.getParcelAt(island, victim.getLocation());
        if (parcel == null || !parcel.isGamesEnabled() || !parcel.isSnowballFightEnabled()) return;
        if (!isPlayerInsideParcel(attacker, island, parcel)) return;
        Material teamWool = parcelPvpTeamWool.get(attacker.getUniqueId());
        if (!isWool(teamWool)) return;
        event.setCancelled(true);
        awardParcelTeamPoint(island, parcel, teamWool, 1);
        attacker.sendMessage(ChatColor.AQUA + "Magischer Schneeball getroffen: +1 Punkt f\u00fcr " + woolChatColor(teamWool) + woolLabel(teamWool) + ChatColor.AQUA + ".");
        victim.sendMessage(ChatColor.RED + "Du wurdest von einem magischen Schneeball getroffen.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveDamagingPlayer(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;
        if (!skyWorldService.isSkyCityWorld(victim.getWorld())) return;
        IslandData island = islandService.getIslandAt(victim.getLocation());
        ParcelData parcel = island == null ? null : islandService.getParcelAt(island, victim.getLocation());
        if (parcel == null || !parcel.isPvpEnabled()) return;
        String parcelKey = islandService.getParcelPvpKey(island, parcel);
        if (parcelKey == null) return;
        parcelPvpCombatTags.put(victim.getUniqueId(), new CombatTag(attacker.getUniqueId(), parcelKey, System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        returnCtfFlagToBase(victim.getUniqueId(), false);
        if (!skyWorldService.isSkyCityWorld(victim.getWorld())) return;
        IslandData island = islandService.getIslandAt(victim.getLocation());
        ParcelData parcel = island == null ? null : islandService.getParcelAt(island, victim.getLocation());
        if (parcel != null && parcel.isPveEnabled() && islandService.isPlayerInParcelPve(victim.getUniqueId(), island, parcel)) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepLevel(false);
            event.setNewLevel(Math.max(0, victim.getLevel() - 5));
            event.setNewExp(0);
            islandService.handlePvePlayerDeath(victim, island, parcel);
            victim.sendMessage(ChatColor.RED + "PvE-Tod: 5 Level verloren. Respawn auf der Startzone.");
            return;
        }
        if (parcel == null || !parcel.isPvpEnabled()) return;

        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepLevel(true);

        String parcelKey = islandService.getParcelPvpKey(island, parcel);
        CombatTag tag = parcelPvpCombatTags.remove(victim.getUniqueId());
        Player killer = resolveKiller(tag, parcelKey);
        String parcelName = islandService.getParcelDisplayName(parcel);
        if (killer == null) {
            prepareParcelPvpRespawn(victim, island, parcel);
            broadcastParcelPvpMessage(ChatColor.YELLOW + victim.getName() + ChatColor.GRAY + " ist in der PvP-Zone " + parcelName + " gestorben und beh\u00e4lt alles.");
            return;
        }

        prepareParcelPvpRespawn(victim, island, parcel);
        ItemStack transferred = transferRandomInventoryItem(victim, killer);
        islandService.recordParcelPvpKill(island, parcel, killer.getUniqueId());
        refreshParcelPvpScoreboards(island, parcel);
        if (transferred == null) {
            broadcastParcelPvpMessage(ChatColor.RED + victim.getName() + ChatColor.GRAY + " wurde von " + ChatColor.GOLD + killer.getName() + ChatColor.GRAY + " get\u00f6tet. Kein Item wurde \u00fcbertragen.");
            return;
        }
        broadcastParcelPvpMessage(ChatColor.RED + victim.getName() + ChatColor.GRAY + " wurde von " + ChatColor.GOLD + killer.getName() + ChatColor.GRAY + " get\u00f6tet. \u00dcbertragen: " + ChatColor.AQUA + formatItemName(transferred) + ChatColor.GRAY + ".");
    }

    private void updateParcelPvpTeam(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) return;
        UUID playerId = player.getUniqueId();
        IslandData island = islandService.getIslandAt(location);
        ParcelData parcel = island == null ? null : islandService.getParcelAt(island, location);
        boolean zoneActive = isParcelCombatZone(parcel);
        boolean zoneAllowed = zoneActive && canEnterParcelCombatZone(island, parcel, playerId)
                && (getParcelCombatMode(parcel) == ParcelData.CombatMode.GAMES || islandService.hasParcelPvpConsent(playerId, island, parcel));
        if (!zoneActive || !zoneAllowed) {
            parcelPvpTeamWool.remove(playerId);
            parcelPvpTeamRespawns.remove(playerId);
            player.removeMetadata(PVP_TEAM_WOOL_METADATA, plugin);
            return;
        }
        Material wool = findWoolMarker(location);
        if (!isWool(wool)) return;
        Material previousWool = parcelPvpTeamWool.put(playerId, wool);
        parcelPvpTeamRespawns.put(playerId, centeredSpawn(locationForTouchedWool(location, wool)));
        player.setMetadata(PVP_TEAM_WOOL_METADATA, new FixedMetadataValue(plugin, wool.name()));
        if (previousWool != wool) {
            showParcelCombatScoreboard(player, island, parcel);
        }
    }

    private void prepareParcelPvpRespawn(Player player, IslandData island, ParcelData parcel) {
        if (player == null || island == null || parcel == null) return;
        Location respawn = findParcelPvpRespawnLocation(player.getUniqueId(), island, parcel);
        if (respawn != null) {
            pendingParcelPvpRespawns.put(player.getUniqueId(), respawn);
        }
    }

    private Location findParcelPvpRespawnLocation(UUID playerId, IslandData island, ParcelData parcel) {
        if (playerId == null || island == null || parcel == null) return null;
        Location whiteRespawn = findParcelWoolRespawn(island, parcel, Material.WHITE_WOOL);
        if (whiteRespawn != null) return whiteRespawn;
        Material teamWool = parcelPvpTeamWool.get(playerId);
        Location storedTeamRespawn = parcelPvpTeamRespawns.get(playerId);
        if (teamWool != null && storedTeamRespawn != null && isCurrentWoolLocation(storedTeamRespawn, teamWool)) {
            return storedTeamRespawn.clone();
        }
        if (teamWool != null) {
            Location scannedTeamRespawn = findParcelWoolRespawn(island, parcel, teamWool);
            if (scannedTeamRespawn != null) return scannedTeamRespawn;
        }
        return safeParcelFallback(parcel, island);
    }

    private Location findParcelWoolRespawn(IslandData island, ParcelData parcel, Material woolType) {
        if (island == null || parcel == null || woolType == null || !isWool(woolType) || skyWorldService.getWorld() == null) return null;
        int minY = Math.max(parcel.getMinY(), skyWorldService.getWorld().getMinHeight());
        int maxY = Math.min(parcel.getMaxY(), skyWorldService.getWorld().getMaxHeight() - 1);
        for (int x = parcel.getMinX(); x <= parcel.getMaxX(); x++) {
            for (int z = parcel.getMinZ(); z <= parcel.getMaxZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = skyWorldService.getWorld().getBlockAt(x, y, z);
                    if (block.getType() != woolType) continue;
                    if (islandService.getParcelAt(island, block.getLocation()) != parcel) continue;
                    return centeredSpawn(block.getLocation());
                }
            }
        }
        return null;
    }

    private Location safeParcelFallback(ParcelData parcel, IslandData island) {
        if (parcel != null && parcel.getSpawn() != null && isSafeRespawnLocation(parcel.getSpawn())) {
            return parcel.getSpawn().clone();
        }
        if (island != null && island.getIslandSpawn() != null && isSafeRespawnLocation(island.getIslandSpawn())) {
            return island.getIslandSpawn().clone();
        }
        return islandService.getSpawnLocation();
    }

    private Location centeredSpawn(Location base) {
        if (base == null || base.getWorld() == null) return null;
        Location location = base.clone();
        Block block = location.getBlock();
        return new Location(location.getWorld(), block.getX() + 0.5, block.getY() + 1.0, block.getZ() + 0.5);
    }

    private Location locationForTouchedWool(Location location, Material woolType) {
        if (location == null || location.getWorld() == null || woolType == null) return location;
        Block woolBlock = findTouchedWoolBlock(location);
        if (woolBlock != null && woolBlock.getType() == woolType) return woolBlock.getLocation();
        return location;
    }

    private boolean isCurrentWoolLocation(Location location, Material woolType) {
        if (location == null || location.getWorld() == null || woolType == null) return false;
        return location.getBlock().getType() == woolType;
    }

    private boolean isSafeRespawnLocation(Location location) {
        if (location == null || location.getWorld() == null) return false;
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        return feet.isPassable() && head.isPassable() && !isLavaMaterial(ground.getType()) && !isLavaMaterial(feet.getType()) && !isLavaMaterial(head.getType());
    }

    private void handleParcelBanEntryCountdown(Player player, Location to) {
        UUID playerId = player.getUniqueId();
        IslandData island = islandService.getIslandAt(to);
        if (island == null) {
            stopParcelBanCountdown(playerId);
            return;
        }
        ParcelData parcel = islandService.getParcelAt(island, to);
        if (parcel == null || !parcel.getBanned().contains(playerId)) {
            stopParcelBanCountdown(playerId);
            return;
        }

        String parcelKey = island.getOwner() + ":" + parcel.getChunkKey();
        String activeKey = parcelBanCountdownKeys.get(playerId);
        if (parcelKey.equals(activeKey) && parcelBanCountdownTasks.containsKey(playerId)) return;

        stopParcelBanCountdown(playerId);
        parcelBanCountdownKeys.put(playerId, parcelKey);
        player.sendMessage(ChatColor.RED + "Du bist auf diesem Grundst\u00fcck gebannt.");
        player.sendMessage(ChatColor.YELLOW + "Verlasse es in 5 Sekunden, sonst wirst du teleportiert.");

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            int secondsLeft = 5;

            @Override
            public void run() {
                Player live = Bukkit.getPlayer(playerId);
                if (live == null || !live.isOnline()) {
                    stopParcelBanCountdown(playerId);
                    return;
                }

                Location now = live.getLocation();
                IslandData nowIsland = islandService.getIslandAt(now);
                ParcelData nowParcel = nowIsland == null ? null : islandService.getParcelAt(nowIsland, now);
                String nowKey = nowIsland == null || nowParcel == null ? null : (nowIsland.getOwner() + ":" + nowParcel.getChunkKey());
                boolean stillInBannedParcel = nowKey != null && nowKey.equals(parcelKey) && nowParcel.getBanned().contains(playerId);

                if (!stillInBannedParcel) {
                    live.sendMessage(ChatColor.GREEN + "Du hast das gebannte Grundst\u00fcck verlassen.");
                    stopParcelBanCountdown(playerId);
                    return;
                }

                secondsLeft--;
                if (secondsLeft <= 0) {
                    IslandData own = islandService.getIsland(playerId).orElse(null);
                    Location target = own != null && own.getIslandSpawn() != null
                            ? own.getIslandSpawn()
                            : islandService.getSpawnLocation();
                    live.teleport(target);
                    live.sendMessage(ChatColor.RED + "Du wurdest vom Grundst\u00fcck entfernt.");
                    stopParcelBanCountdown(playerId);
                    return;
                }

                live.sendMessage(ChatColor.YELLOW + "Verlasse das Grundst\u00fcck in " + secondsLeft + "...");
            }
        }, 20L, 20L);
        parcelBanCountdownTasks.put(playerId, taskId);
    }

    private boolean tryHandleLavaRescue(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) return false;
        if (!skyWorldService.isSkyCityWorld(location.getWorld())) return false;
        if (!isTouchingLava(location)) return false;
        Location rescueTarget = resolveLavaRescueTarget(player, location);
        if (rescueTarget == null) return false;
        lavaRescueProtectionUntil.put(player.getUniqueId(), System.currentTimeMillis() + LAVA_RESCUE_PROTECTION_MS);
        clearLavaEffects(player);
        player.teleport(rescueTarget);
        playCheckpointTeleportSound(player, rescueTarget);
        clearLavaEffects(player);
        return true;
    }

    private Location resolveLavaRescueTarget(Location location) {
        return resolveLavaRescueTarget(null, location);
    }

    private Location resolveLavaRescueTarget(Player player, Location location) {
        if (player != null) {
            IslandData island = islandService.getIslandAt(location);
            ParcelData regionParcel = island == null ? null : islandService.getParcelAt(island, location);
            CheckpointMarker marker = findCheckpointMarker(location);
            if (marker != null) {
                if (marker.woolType() == Material.WHITE_WOOL) {
                    Location lastCheckpoint = getValidLastCheckpoint(player.getUniqueId());
                    if (isSafeFromLava(lastCheckpoint)) {
                        return orientCheckpointLocation(player, lastCheckpoint);
                    }
                }
                Location checkpoint = getValidCheckpointLocation(player.getUniqueId(), marker.woolType(), marker.plateType());
                if (isSafeFromLava(checkpoint)) {
                    return orientCheckpointLocation(player, checkpoint);
                }
                if (island != null) {
                    java.util.List<Location> exactTargets = findMatchingCheckpointPlates(island, regionParcel, marker.woolType(), marker.plateType());
                    if (exactTargets.size() == 1 && isSafeFromLava(exactTargets.get(0))) {
                        return orientCheckpointLocation(player, exactTargets.get(0));
                    }
                }
            }
            Material woolOnly = findWoolMarker(location);
            if (woolOnly != null) {
                if (woolOnly == Material.WHITE_WOOL) {
                    Location lastCheckpoint = getValidLastCheckpoint(player.getUniqueId());
                    if (isSafeFromLava(lastCheckpoint)) {
                        return orientCheckpointLocation(player, lastCheckpoint);
                    }
                }
                Location woolCheckpoint = getValidCheckpointLocationByWool(player.getUniqueId(), woolOnly);
                if (isSafeFromLava(woolCheckpoint)) {
                    return orientCheckpointLocation(player, woolCheckpoint);
                }
                if (island != null) {
                    java.util.List<Location> uniqueTargets = findMatchingCheckpointPlatesByWool(island, regionParcel, woolOnly);
                    if (uniqueTargets.size() == 1 && isSafeFromLava(uniqueTargets.get(0))) {
                        return orientCheckpointLocation(player, uniqueTargets.get(0));
                    }
                }
            }
        }
        IslandData island = islandService.getIslandAt(location);
        if (island == null) return islandService.getSpawnLocation();
        ParcelData parcel = islandService.getParcelAt(island, location);
        if (parcel != null && isSafeFromLava(parcel.getSpawn())) {
            return parcel.getSpawn();
        }
        if (isSafeFromLava(island.getIslandSpawn())) {
            return island.getIslandSpawn();
        }
        return islandService.getSpawnLocation();
    }

    private void updateCheckpoint(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) return;
        CheckpointMarker marker = findCheckpointMarker(location);
        if (marker == null) return;
        IslandData island = islandService.getIslandAt(location);
        if (island == null) return;
        ParcelData regionParcel = islandService.getParcelAt(island, location);
        java.util.List<Location> matches = findMatchingCheckpointPlates(island, regionParcel, marker.woolType(), marker.plateType());
        if (matches.size() == 2) return;
        String checkpointKey = marker.plateLocation().getBlockX() + ":" + marker.plateLocation().getBlockY() + ":" + marker.plateLocation().getBlockZ();
        if (!checkpointKey.equals(lastActivatedCheckpointKey.get(player.getUniqueId()))) {
            lastActivatedCheckpointKey.put(player.getUniqueId(), checkpointKey);
            playCheckpointActivateSound(player);
        }
        lastCheckpointLocations.put(player.getUniqueId(), marker.plateLocation());
        checkpointLocations
                .computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                .put(checkpointKey(marker.woolType(), marker.plateType()), marker.plateLocation());
        checkpointLocationsByWool
                .computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                .put(marker.woolType(), marker.plateLocation());
    }

    private boolean tryHandleLinkedCheckpointTeleport(Player player, Location from, Location to) {
        if (player == null || from == null || to == null || from.getWorld() == null) return false;
        if (isLinkedCheckpointTeleportProtected(player.getUniqueId())) return false;
        if (to.getY() <= from.getY() + 0.12D) return false;
        if (player.getVelocity().getY() <= 0.08D) return false;
        CheckpointMarker marker = findCheckpointMarker(from);
        if (marker == null) return false;
        IslandData island = islandService.getIslandAt(from);
        if (island == null) return false;
        ParcelData regionParcel = islandService.getParcelAt(island, from);
        java.util.List<Location> matches = findMatchingCheckpointPlates(island, regionParcel, marker.woolType(), marker.plateType());
        if (matches.size() != 2) return false;
        Location current = marker.plateLocation();
        Location target = null;
        for (Location candidate : matches) {
            if (!sameBlock(candidate, current)) {
                target = candidate;
                break;
            }
        }
        if (target == null || !isSafeFromLava(target.clone().add(0.0, 1.0, 0.0))) return false;
        target = orientCheckpointLocation(player, target);
        spawnLinkedCheckpointParticles(current, target);
        linkedCheckpointTeleportUntil.put(player.getUniqueId(), System.currentTimeMillis() + 1_500L);
        player.teleport(target);
        playCheckpointTeleportSound(player, target);
        return true;
    }

    private boolean tryHandleJumpPad(Player player, Location from, Location to) {
        if (player == null || from == null || to == null || from.getWorld() == null) return false;
        if (isJumpPadCoolingDown(player.getUniqueId())) return false;
        if (to.getY() <= from.getY() + 0.12D) return false;
        if (player.getVelocity().getY() <= 0.08D) return false;
        Block plateBlock = findPressurePlateAt(from);
        if (plateBlock == null || !isJumpPadPlate(plateBlock)) return false;
        Vector direction = player.getLocation().getDirection().normalize().multiply(1.35D);
        direction.setY(Math.max(0.78D, 0.68D + Math.max(0.0D, direction.getY())));
        player.setVelocity(direction);
        player.setFallDistance(0.0F);
        jumpPadCooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + JUMP_PAD_COOLDOWN_MS);
        jumpPadFallProtectionUntil.put(player.getUniqueId(), System.currentTimeMillis() + JUMP_PAD_FALL_PROTECTION_MS);
        spawnJumpPadParticles(plateBlock.getLocation().add(0.5, 0.2, 0.5));
        playJumpPadSound(player, plateBlock.getLocation().add(0.5, 0.2, 0.5));
        return true;
    }

    private CheckpointMarker findCheckpointMarker(Location location) {
        if (location == null || location.getWorld() == null) return null;
        Block feet = location.getBlock();
        CheckpointMarker marker = checkpointMarkerFromPlate(feet);
        if (marker != null) return marker;
        return checkpointMarkerFromPlate(feet.getRelative(0, -1, 0));
    }

    private CheckpointMarker checkpointMarkerFromPlate(Block plateBlock) {
        if (plateBlock == null || !isPressurePlate(plateBlock.getType())) return null;
        Block woolBlock = plateBlock.getRelative(0, -1, 0);
        if (!isWool(woolBlock.getType())) {
            woolBlock = plateBlock.getRelative(0, -2, 0);
        }
        if (!isWool(woolBlock.getType())) return null;
        Location plateLocation = plateBlock.getLocation().add(0.5, 0.0, 0.5);
        return new CheckpointMarker(woolBlock.getType(), plateBlock.getType(), plateLocation);
    }

    private Block findPressurePlateAt(Location location) {
        if (location == null || location.getWorld() == null) return null;
        Block feet = location.getBlock();
        if (isPressurePlate(feet.getType())) return feet;
        Block below = feet.getRelative(0, -1, 0);
        return isPressurePlate(below.getType()) ? below : null;
    }

    private java.util.List<Location> findMatchingCheckpointPlates(IslandData island, ParcelData regionParcel, Material woolType, Material plateType) {
        if (island == null || woolType == null || plateType == null) return new java.util.ArrayList<>();
        CheckpointIndexCache cache = getCheckpointIndex(island);
        String key = checkpointSearchKey(regionParcel, woolType, plateType);
        return new java.util.ArrayList<>(cache.exactMatches().getOrDefault(key, java.util.List.of()));
    }

    private java.util.List<Location> findMatchingCheckpointPlatesByWool(IslandData island, ParcelData regionParcel, Material woolType) {
        if (island == null || woolType == null) return new java.util.ArrayList<>();
        CheckpointIndexCache cache = getCheckpointIndex(island);
        String key = checkpointWoolSearchKey(regionParcel, woolType);
        return new java.util.ArrayList<>(cache.woolMatches().getOrDefault(key, java.util.List.of()));
    }

    private CheckpointIndexCache getCheckpointIndex(IslandData island) {
        Set<String> unlockedChunks = new HashSet<>(island.getUnlockedChunks());
        CheckpointIndexCache cached = checkpointIndexCache.get(island.getOwner());
        long now = System.currentTimeMillis();
        if (cached != null
                && now - cached.builtAt() < CHECKPOINT_CACHE_TTL_MS
                && cached.unlockedChunks().equals(unlockedChunks)) {
            return cached;
        }
        CheckpointIndexCache rebuilt = buildCheckpointIndex(island, unlockedChunks, now);
        checkpointIndexCache.put(island.getOwner(), rebuilt);
        return rebuilt;
    }

    private CheckpointIndexCache buildCheckpointIndex(IslandData island, Set<String> unlockedChunks, long builtAt) {
        Map<String, List<Location>> exactMatches = new HashMap<>();
        Map<String, List<Location>> woolMatches = new HashMap<>();
        if (island == null || skyWorldService.getWorld() == null) {
            return new CheckpointIndexCache(builtAt, unlockedChunks, exactMatches, woolMatches);
        }
        for (String chunkKey : unlockedChunks) {
            String[] parts = chunkKey.split(":");
            if (parts.length != 2) continue;
            int relChunkX;
            int relChunkZ;
            try {
                relChunkX = Integer.parseInt(parts[0]);
                relChunkZ = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
                continue;
            }
            int chunkX = islandService.plotMinChunkX(island.getGridX()) + relChunkX;
            int chunkZ = islandService.plotMinChunkZ(island.getGridZ()) + relChunkZ;
            org.bukkit.Chunk chunk = skyWorldService.getWorld().getChunkAt(chunkX, chunkZ);
            int minY = chunk.getWorld().getMinHeight();
            int maxY = chunk.getWorld().getMaxHeight();
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int y = minY; y < maxY; y++) {
                        Block block = chunk.getBlock(localX, y, localZ);
                        if (!isPressurePlate(block.getType())) continue;
                        CheckpointMarker marker = checkpointMarkerFromPlate(block);
                        if (marker == null) continue;
                        ParcelData parcel = islandService.getParcelAt(island, block.getLocation());
                        String woolKey = checkpointWoolSearchKey(parcel, marker.woolType());
                        woolMatches.computeIfAbsent(woolKey, ignored -> new java.util.ArrayList<>()).add(marker.plateLocation());
                        if (hasLavaDirectlyAbove(block)) continue;
                        String exactKey = checkpointSearchKey(parcel, marker.woolType(), marker.plateType());
                        exactMatches.computeIfAbsent(exactKey, ignored -> new java.util.ArrayList<>()).add(marker.plateLocation());
                    }
                }
            }
        }
        return new CheckpointIndexCache(builtAt, unlockedChunks, exactMatches, woolMatches);
    }

    private String checkpointSearchKey(ParcelData parcel, Material woolType, Material plateType) {
        String parcelKey = parcel == null ? "island" : parcel.getChunkKey();
        return parcelKey + "|" + woolType.name() + "|" + plateType.name();
    }

    private String checkpointWoolSearchKey(ParcelData parcel, Material woolType) {
        String parcelKey = parcel == null ? "island" : parcel.getChunkKey();
        return parcelKey + "|" + woolType.name();
    }

    private String checkpointKey(Material woolType, Material plateType) {
        return woolType.name() + "|" + plateType.name();
    }

    private Material findWoolMarker(Location location) {
        Block woolBlock = findTouchedWoolBlock(location);
        return woolBlock == null ? null : woolBlock.getType();
    }

    private Material woolFromBlock(Block block) {
        if (block == null) return null;
        return isWool(block.getType()) ? block.getType() : null;
    }

    private Block findTouchedWoolBlock(Location location) {
        if (location == null || location.getWorld() == null) return null;
        Block feet = location.getBlock();
        if (isTeamSelectableWool(feet)) return feet;
        Block below = feet.getRelative(0, -1, 0);
        if (isTeamSelectableWool(below)) return below;
        Block belowTwo = feet.getRelative(0, -2, 0);
        if (isSolidWoolBridgeBlock(below) && isTeamSelectableWool(belowTwo)) return belowTwo;
        return null;
    }

    private boolean isTeamSelectableWool(Block block) {
        if (block == null || !isWool(block.getType())) return false;
        return !isCtfShelfBlock(block.getRelative(0, 1, 0).getType());
    }

    private boolean isSolidWoolBridgeBlock(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        if (!type.isSolid() || block.isPassable()) return false;
        return !isForbiddenWoolBridgeType(type);
    }

    private boolean isForbiddenWoolBridgeType(Material type) {
        if (type == null) return true;
        if (type == Material.WATER || type == Material.LAVA) return true;
        String name = type.name();
        if (isCtfShelfBlock(type)) return true;
        return name.endsWith("_PRESSURE_PLATE")
                || name.endsWith("_BUTTON")
                || name.endsWith("_TRAPDOOR")
                || name.endsWith("_DOOR")
                || name.endsWith("_FENCE_GATE")
                || name.endsWith("_WALL_SIGN")
                || name.endsWith("_SIGN")
                || name.endsWith("_RAIL")
                || name.endsWith("_BANNER")
                || type == Material.LEVER
                || type == Material.REPEATER
                || type == Material.COMPARATOR
                || type == Material.REDSTONE_WIRE
                || type == Material.REDSTONE_TORCH
                || type == Material.REDSTONE_WALL_TORCH
                || type == Material.DAYLIGHT_DETECTOR
                || type == Material.TARGET
                || type == Material.TRIPWIRE_HOOK
                || type == Material.NOTE_BLOCK
                || type == Material.SCULK_SENSOR
                || type == Material.CALIBRATED_SCULK_SENSOR
                || type == Material.OBSERVER
                || type == Material.PISTON
                || type == Material.STICKY_PISTON;
    }

    private Location getValidLastCheckpoint(UUID playerId) {
        Location location = lastCheckpointLocations.get(playerId);
        if (isCurrentCheckpointLocation(location, null, null)) return location;
        lastCheckpointLocations.remove(playerId);
        return null;
    }

    private Location getValidCheckpointLocation(UUID playerId, Material woolType, Material plateType) {
        Map<String, Location> locations = checkpointLocations.get(playerId);
        if (locations == null) return null;
        String key = checkpointKey(woolType, plateType);
        Location location = locations.get(key);
        if (isCurrentCheckpointLocation(location, woolType, plateType)) return location;
        locations.remove(key);
        return null;
    }

    private Location getValidCheckpointLocationByWool(UUID playerId, Material woolType) {
        Map<Material, Location> locations = checkpointLocationsByWool.get(playerId);
        if (locations == null) return null;
        Location location = locations.get(woolType);
        if (isCurrentCheckpointLocation(location, woolType, null)) return location;
        locations.remove(woolType);
        return null;
    }

    private boolean isCurrentCheckpointLocation(Location location, Material expectedWool, Material expectedPlate) {
        if (location == null || location.getWorld() == null) return false;
        CheckpointMarker marker = checkpointMarkerFromPlate(location.getBlock());
        if (marker == null) return false;
        if (expectedWool != null && marker.woolType() != expectedWool) return false;
        return expectedPlate == null || marker.plateType() == expectedPlate;
    }

    private Location orientCheckpointLocation(Player player, Location location) {
        if (location == null) return null;
        Location oriented = location.clone();
        IslandData island = islandService.getIslandAt(location);
        Float yaw = island == null ? null : islandService.getCheckpointPlateYaw(island, location);
        oriented.setYaw(yaw != null ? yaw : (player == null ? oriented.getYaw() : player.getLocation().getYaw()));
        oriented.setPitch(0.0F);
        return oriented;
    }

    private boolean isSafeFromLava(Location location) {
        return location != null && location.getWorld() != null && !isTouchingLava(location);
    }

    private boolean isTouchingLava(Location location) {
        if (location == null || location.getWorld() == null) return false;
        return isLavaBlockAt(location, 0.0, 0.0, 0.0)
                || isLavaBlockAt(location, 0.0, 1.0, 0.0)
                || isLavaBlockAt(location, 0.0, 1.62, 0.0)
                || isLavaBlockAt(location, 0.3, 0.0, 0.0)
                || isLavaBlockAt(location, -0.3, 0.0, 0.0)
                || isLavaBlockAt(location, 0.0, 0.0, 0.3)
                || isLavaBlockAt(location, 0.0, 0.0, -0.3)
                || isLavaBlockAt(location, 0.3, 1.0, 0.0)
                || isLavaBlockAt(location, -0.3, 1.0, 0.0)
                || isLavaBlockAt(location, 0.0, 1.0, 0.3)
                || isLavaBlockAt(location, 0.0, 1.0, -0.3);
    }

    private boolean isLavaBlockAt(Location location, double x, double y, double z) {
        return isLavaMaterial(location.clone().add(x, y, z).getBlock().getType());
    }

    private boolean isLavaMaterial(Material material) {
        return material == Material.LAVA || material == Material.LAVA_CAULDRON;
    }

    private boolean isJumpPadPlate(Block plateBlock) {
        if (plateBlock == null || !isPressurePlate(plateBlock.getType())) return false;
        return plateBlock.getRelative(0, -1, 0).getType() == Material.SLIME_BLOCK
                || plateBlock.getRelative(0, -2, 0).getType() == Material.SLIME_BLOCK;
    }

    private void spawnJumpPadParticles(Location location) {
        if (location == null || location.getWorld() == null) return;
        location.getWorld().spawnParticle(Particle.CLOUD, location, 16, 0.22, 0.10, 0.22, 0.03);
        location.getWorld().spawnParticle(Particle.END_ROD, location.clone().add(0.0, 0.12, 0.0), 6, 0.10, 0.08, 0.10, 0.01);
    }

    private void playJumpPadSound(Player player, Location location) {
        Sound sound = resolveWindChargeSound();
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.15F, 1.0F);
            if (location != null && location.getWorld() != null) {
                location.getWorld().playSound(location, sound, 1.15F, 1.0F);
            }
        }
    }

    private Sound resolveWindChargeSound() {
        try {
            return Sound.valueOf("ENTITY_WIND_CHARGE_WIND_BURST");
        } catch (IllegalArgumentException ignored) {
        }
        try {
            return Sound.valueOf("ENTITY_WIND_CHARGE_THROW");
        } catch (IllegalArgumentException ignored) {
        }
        return Sound.ENTITY_ENDERMAN_TELEPORT;
    }

    private boolean hasLavaDirectlyAbove(Block block) {
        return block != null && isLavaMaterial(block.getRelative(0, 1, 0).getType());
    }

    private boolean isPressurePlate(Material material) {
        return material != null && material.name().endsWith("_PRESSURE_PLATE");
    }

    private boolean isWool(Material material) {
        return material != null && material.name().endsWith("_WOOL");
    }

    private boolean isLavaRescueProtected(UUID playerId) {
        Long until = lavaRescueProtectionUntil.get(playerId);
        if (until == null) return false;
        if (until < System.currentTimeMillis()) {
            lavaRescueProtectionUntil.remove(playerId);
            return false;
        }
        return true;
    }

    private boolean isLinkedCheckpointTeleportProtected(UUID playerId) {
        Long until = linkedCheckpointTeleportUntil.get(playerId);
        if (until == null) return false;
        if (until < System.currentTimeMillis()) {
            linkedCheckpointTeleportUntil.remove(playerId);
            return false;
        }
        return true;
    }

    private boolean isJumpPadCoolingDown(UUID playerId) {
        Long until = jumpPadCooldownUntil.get(playerId);
        if (until == null) return false;
        if (until < System.currentTimeMillis()) {
            jumpPadCooldownUntil.remove(playerId);
            return false;
        }
        return true;
    }

    private boolean isJumpPadFallProtected(UUID playerId) {
        Long until = jumpPadFallProtectionUntil.get(playerId);
        if (until == null) return false;
        if (until < System.currentTimeMillis()) {
            jumpPadFallProtectionUntil.remove(playerId);
            return false;
        }
        return true;
    }

    private void clearLavaEffects(Player player) {
        player.setFireTicks(0);
        player.setVisualFire(false);
        player.setNoDamageTicks(Math.max(player.getNoDamageTicks(), 20));
    }

    private void spawnLinkedCheckpointParticles(Location first, Location second) {
        spawnCheckpointParticles(first);
        spawnCheckpointParticles(second);
    }

    private void spawnCheckpointParticles(Location location) {
        if (location == null || location.getWorld() == null) return;
        location.getWorld().spawnParticle(Particle.PORTAL, location.clone().add(0.0, 0.4, 0.0), 24, 0.25, 0.2, 0.25, 0.05);
        location.getWorld().spawnParticle(Particle.END_ROD, location.clone().add(0.0, 0.9, 0.0), 8, 0.15, 0.3, 0.15, 0.0);
    }

    private java.util.Set<String> spawnNearbyLinkedCheckpointParticles(Player player, IslandData island, java.util.Set<String> activeJumpPadTags) {
        java.util.Set<String> activeTags = new java.util.HashSet<>();
        if (player == null || island == null || player.getWorld() == null) return activeTags;
        long now = System.currentTimeMillis();
        Long lastTick = checkpointParticleTickWindow.get(player.getUniqueId());
        if (lastTick != null && now - lastTick < 600L) return activeTags;
        checkpointParticleTickWindow.put(player.getUniqueId(), now);
        java.util.Set<String> shown = new java.util.HashSet<>();
        org.bukkit.Chunk center = player.getLocation().getChunk();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                org.bukkit.Chunk chunk = player.getWorld().getChunkAt(center.getX() + dx, center.getZ() + dz);
                int relChunkX = islandService.relativeChunkX(island, chunk.getX());
                int relChunkZ = islandService.relativeChunkZ(island, chunk.getZ());
                if (!islandService.isChunkUnlocked(island, relChunkX, relChunkZ)) continue;
                int minY = Math.max(player.getWorld().getMinHeight(), player.getLocation().getBlockY() - 4);
                int maxY = Math.min(player.getWorld().getMaxHeight() - 1, player.getLocation().getBlockY() + 4);
                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        for (int y = minY; y <= maxY; y++) {
                            Block block = chunk.getBlock(localX, y, localZ);
                            if (isJumpPadPlate(block)) {
                                String jumpPadTag = jumpPadHoloTag(block.getLocation());
                                activeJumpPadTags.add(jumpPadTag);
                                ensureJumpPadHolo(block.getLocation().add(0.5, 0.0, 0.5), jumpPadTag);
                            }
                            CheckpointMarker marker = checkpointMarkerFromPlate(block);
                            if (marker == null) continue;
                            ParcelData regionParcel = islandService.getParcelAt(island, block.getLocation());
                            String key = (regionParcel == null ? "island" : regionParcel.getChunkKey()) + "|" + checkpointKey(marker.woolType(), marker.plateType());
                            if (!shown.add(key)) continue;
                            java.util.List<Location> matches = findMatchingCheckpointPlates(island, regionParcel, marker.woolType(), marker.plateType());
                            if (matches.size() == 1) {
                                String tag = checkpointHoloTag(matches.get(0));
                                activeTags.add(tag);
                                ensureCheckpointHolo(matches.get(0), tag);
                                continue;
                            }
                            if (matches.size() == 2) {
                                for (Location match : matches) {
                                    spawnCheckpointGlow(player, match);
                                }
                                continue;
                            }
                            if (matches.size() >= 3) {
                                for (Location match : matches) {
                                    String tag = checkpointHoloTag(match);
                                    activeTags.add(tag);
                                    ensureCheckpointHolo(match, tag);
                                }
                            }
                        }
                    }
                }
            }
        }
        return activeTags;
    }

    private void spawnCheckpointGlow(Player player, Location location) {
        if (player == null || location == null) return;
        player.spawnParticle(Particle.PORTAL, location.clone().add(0.0, 0.25, 0.0), 6, 0.14, 0.06, 0.14, 0.02);
    }

    private void playCheckpointTeleportSound(Player player, Location target) {
        if (player == null) return;
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
        if (target != null && target.getWorld() != null) {
            target.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
        }
    }

    private void playCheckpointActivateSound(Player player) {
        if (player == null) return;
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.35F, 0.85F);
    }

    private void ensureCheckpointHolo(Location location, String tag) {
        if (location == null || location.getWorld() == null || tag == null) return;
        Location displayLocation = location.clone().add(0.0, 0.4, 0.0);
        Entity tracked = getTrackedDisplay(checkpointDisplaysByTag, tag);
        if (tracked instanceof ItemDisplay display) {
            if (display.getLocation().distanceSquared(displayLocation) > 0.01D) {
                display.teleport(displayLocation);
            }
            display.setBillboard(Display.Billboard.CENTER);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.5F, 0.5F, 0.5F), new AxisAngle4f()));
            display.setItemStack(new ItemStack(Material.ENDER_EYE));
            return;
        }
        for (Entity entity : location.getWorld().getNearbyEntities(displayLocation, 0.4, 0.6, 0.4)) {
            if (entity instanceof ArmorStand stand && stand.getScoreboardTags().contains(tag)) {
                stand.remove();
                continue;
            }
            if (entity instanceof ItemDisplay display && display.getScoreboardTags().contains(tag)) {
                checkpointDisplaysByTag.put(tag, display.getUniqueId());
                if (display.getLocation().distanceSquared(displayLocation) > 0.01D) {
                    display.teleport(displayLocation);
                }
                display.setBillboard(Display.Billboard.CENTER);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
                display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.5F, 0.5F, 0.5F), new AxisAngle4f()));
                display.setItemStack(new ItemStack(Material.ENDER_EYE));
                return;
            }
        }
        ItemDisplay display = (ItemDisplay) location.getWorld().spawnEntity(displayLocation, EntityType.ITEM_DISPLAY);
        display.setItemStack(new ItemStack(Material.ENDER_EYE));
        display.setBillboard(Display.Billboard.CENTER);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
        display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.5F, 0.5F, 0.5F), new AxisAngle4f()));
        display.addScoreboardTag(tag);
        checkpointDisplaysByTag.put(tag, display.getUniqueId());
    }

    private void removeStaleCheckpointDisplays(java.util.Set<String> activeTags) {
        java.util.Iterator<Map.Entry<String, UUID>> iterator = checkpointDisplaysByTag.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, UUID> entry = iterator.next();
            if (activeTags.contains(entry.getKey())) continue;
            Entity entity = Bukkit.getEntity(entry.getValue());
            if (entity != null) {
                entity.remove();
            }
            iterator.remove();
        }
    }

    private String checkpointHoloTag(Location location) {
        return CHECKPOINT_HOLO_PREFIX
                + location.getBlockX() + "_"
                + location.getBlockY() + "_"
                + location.getBlockZ();
    }

    public void invalidateStructureCaches(Location location, Material material) {
        if (location == null || location.getWorld() == null || material == null) return;
        IslandData island = islandService.getIslandAt(location);
        if (island == null) return;
        if (isCheckpointStructureMaterial(material)) {
            checkpointIndexCache.remove(island.getOwner());
        }
        if (isTeamScoreMaterial(material)) {
            ParcelData parcel = islandService.getParcelAt(island, location);
            String parcelKey = parcel == null ? null : islandService.getParcelPvpKey(island, parcel);
            if (parcelKey != null) {
                parcelTeamScoreCache.remove(parcelKey);
            }
        }
    }

    private boolean isCheckpointStructureMaterial(Material material) {
        return isPressurePlate(material)
                || isWool(material)
                || material == Material.LAVA
                || material == Material.LAVA_CAULDRON
                || material == Material.SLIME_BLOCK;
    }

    private boolean isTeamScoreMaterial(Material material) {
        if (material == null) return false;
        if (isWool(material)) return true;
        return switch (material) {
            case LEVER, REDSTONE_WIRE, REPEATER, COMPARATOR, REDSTONE_TORCH, REDSTONE_WALL_TORCH -> true;
            default -> false;
        };
    }

    private void ensureJumpPadHolo(Location location, String tag) {
        if (location == null || location.getWorld() == null || tag == null) return;
        Location displayLocation = location.clone().add(0.0, 0.225, 0.0);
        Entity tracked = getTrackedDisplay(jumpPadDisplaysByTag, tag);
        if (tracked instanceof ItemDisplay display) {
            if (display.getLocation().distanceSquared(displayLocation) > 0.01D) {
                display.teleport(displayLocation);
            }
            display.setBillboard(Display.Billboard.CENTER);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.52F, 0.52F, 0.52F), new AxisAngle4f()));
            display.setItemStack(new ItemStack(Material.RABBIT_FOOT));
            return;
        }
        for (Entity entity : location.getWorld().getNearbyEntities(displayLocation, 0.4, 0.6, 0.4)) {
            if (entity instanceof ArmorStand stand && stand.getScoreboardTags().contains(tag)) {
                stand.remove();
                continue;
            }
            if (entity instanceof ItemDisplay display && display.getScoreboardTags().contains(tag)) {
                jumpPadDisplaysByTag.put(tag, display.getUniqueId());
                if (display.getLocation().distanceSquared(displayLocation) > 0.01D) {
                    display.teleport(displayLocation);
                }
                display.setBillboard(Display.Billboard.CENTER);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
                display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.52F, 0.52F, 0.52F), new AxisAngle4f()));
                display.setItemStack(new ItemStack(Material.RABBIT_FOOT));
                return;
            }
        }
        ItemDisplay display = (ItemDisplay) location.getWorld().spawnEntity(displayLocation, EntityType.ITEM_DISPLAY);
        display.setItemStack(new ItemStack(Material.RABBIT_FOOT));
        display.setBillboard(Display.Billboard.CENTER);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
        display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.52F, 0.52F, 0.52F), new AxisAngle4f()));
        display.addScoreboardTag(tag);
        jumpPadDisplaysByTag.put(tag, display.getUniqueId());
    }

    private void removeStaleJumpPadDisplays(java.util.Set<String> activeTags) {
        java.util.Iterator<Map.Entry<String, UUID>> iterator = jumpPadDisplaysByTag.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, UUID> entry = iterator.next();
            if (activeTags.contains(entry.getKey())) continue;
            Entity entity = Bukkit.getEntity(entry.getValue());
            if (entity != null) {
                entity.remove();
            }
            iterator.remove();
        }
    }

    private Entity getTrackedDisplay(Map<String, UUID> trackedDisplays, String tag) {
        UUID entityId = trackedDisplays.get(tag);
        if (entityId == null) return null;
        Entity entity = Bukkit.getEntity(entityId);
        if (entity == null || !entity.isValid()) {
            trackedDisplays.remove(tag);
            return null;
        }
        return entity;
    }

    private String jumpPadHoloTag(Location location) {
        return JUMP_PAD_HOLO_PREFIX
                + location.getBlockX() + "_"
                + location.getBlockY() + "_"
                + location.getBlockZ();
    }

    private boolean sameBlock(Location first, Location second) {
        return first != null
                && second != null
                && first.getWorld() != null
                && first.getWorld().equals(second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        if (!coreService.isAwaitingIslandTitleInput(event.getPlayer().getUniqueId())
                && !coreService.isAwaitingIslandWarpInput(event.getPlayer().getUniqueId())
                && !coreService.isAwaitingParcelRenameInput(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        String msg = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (coreService.isAwaitingIslandTitleInput(event.getPlayer().getUniqueId())) {
                coreService.handleIslandTitleChatInputSafe(event.getPlayer(), msg);
                return;
            }
            if (coreService.isAwaitingIslandWarpInput(event.getPlayer().getUniqueId())) {
                coreService.handleIslandWarpChatInput(event.getPlayer(), msg);
                return;
            }
            if (coreService.isAwaitingParcelRenameInput(event.getPlayer().getUniqueId())) {
                coreService.handleParcelRenameChatInput(event.getPlayer(), msg);
            }
        });
    }

    private void startPreparationStatusMessages(Player player) {
        UUID playerId = player.getUniqueId();
        stopPreparationStatusMessages(playerId);
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player live = plugin.getServer().getPlayer(playerId);
            if (live == null || !live.isOnline()) {
                stopPreparationStatusMessages(playerId);
                return;
            }
            IslandData own = islandService.getPrimaryIsland(playerId).orElse(null);
            if (own == null && !islandService.isIslandCreationPending(playerId)) {
                stopPreparationStatusMessages(playerId);
                return;
            }
            if (islandService.isIslandReady(playerId)) {
                stopPreparationStatusMessages(playerId);
                return;
            }

            int pregenerationQueuePos = islandService.getIslandPregenerationQueuePosition(playerId);
            if (pregenerationQueuePos > 1) {
                live.sendMessage(ChatColor.YELLOW + "Deine Startchunks sind bereit. Weitere Generierung: Pregeneration Platz " + pregenerationQueuePos + ".");
                return;
            }

            int progress = islandService.getIslandPregenerationProgress(playerId);
            int total = islandService.getTotalIslandChunkCount();
            live.sendMessage(ChatColor.GOLD + "Deine Insel wird generiert: " + progress + "/" + total + " Chunks.");
        }, 10L, 60L);
        preparationStatusTasks.put(playerId, taskId);
    }

    private void stopPreparationStatusMessages(UUID playerId) {
        Integer taskId = preparationStatusTasks.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void startIslandCreateHintMessages(Player player) {
        UUID playerId = player.getUniqueId();
        stopIslandCreateHintMessages(playerId);
        sendIslandCreateHint(player);
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player live = plugin.getServer().getPlayer(playerId);
            if (live == null || !live.isOnline()) {
                stopIslandCreateHintMessages(playerId);
                return;
            }
            if (islandService.getIsland(playerId).isPresent()) {
                stopIslandCreateHintMessages(playerId);
                return;
            }
            sendIslandCreateHint(live);
        }, 20L * 90L, 20L * 90L);
        islandCreateHintTasks.put(playerId, taskId);
    }

    private void stopIslandCreateHintMessages(UUID playerId) {
        Integer taskId = islandCreateHintTasks.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void stopParcelBanCountdown(UUID playerId) {
        Integer taskId = parcelBanCountdownTasks.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        parcelBanCountdownKeys.remove(playerId);
    }

    private void handleParcelPvpWhitelistCountdown(Player player, Location to) {
        UUID playerId = player.getUniqueId();
        IslandData island = islandService.getIslandAt(to);
        ParcelData parcel = island == null ? null : islandService.getParcelAt(island, to);
        if (parcel == null || !isParcelCombatZone(parcel) || canEnterParcelCombatZone(island, parcel, playerId)) {
            stopParcelPvpExitCountdown(playerId);
            return;
        }

        String parcelKey = islandService.getParcelPvpKey(island, parcel);
        String activeKey = parcelPvpExitCountdownKeys.get(playerId);
        if (parcelKey != null && parcelKey.equals(activeKey) && parcelPvpExitCountdownTasks.containsKey(playerId)) return;

        stopParcelPvpExitCountdown(playerId);
        parcelPvpExitCountdownKeys.put(playerId, parcelKey);
        player.sendMessage(ChatColor.RED + "Du bist nicht auf der PvP-Whitelist dieses Grundst\u00fccks.");
        player.sendMessage(ChatColor.YELLOW + "Verlasse die Zone in 5 Sekunden, sonst wirst du teleportiert.");

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            int secondsLeft = 5;

            @Override
            public void run() {
                Player live = Bukkit.getPlayer(playerId);
                if (live == null || !live.isOnline()) {
                    stopParcelPvpExitCountdown(playerId);
                    return;
                }

                Location now = live.getLocation();
                IslandData nowIsland = islandService.getIslandAt(now);
                ParcelData nowParcel = nowIsland == null ? null : islandService.getParcelAt(nowIsland, now);
                boolean stillBlocked = nowIsland != null
                        && nowParcel != null
                        && isParcelCombatZone(nowParcel)
                        && parcelKey != null
                        && parcelKey.equals(islandService.getParcelPvpKey(nowIsland, nowParcel))
                        && !canEnterParcelCombatZone(nowIsland, nowParcel, playerId);

                if (!stillBlocked) {
                    live.sendMessage(ChatColor.GREEN + "Du hast die gesperrte PvP-Zone verlassen.");
                    stopParcelPvpExitCountdown(playerId);
                    return;
                }

                secondsLeft--;
                if (secondsLeft <= 0) {
                    IslandData own = islandService.getIsland(playerId).orElse(null);
                    Location target = own != null && own.getIslandSpawn() != null ? own.getIslandSpawn() : islandService.getSpawnLocation();
                    live.teleport(target);
                    live.sendMessage(ChatColor.RED + "Du wurdest aus der PvP-Zone entfernt.");
                    stopParcelPvpExitCountdown(playerId);
                    return;
                }

                live.sendMessage(ChatColor.YELLOW + "Verlasse die PvP-Zone in " + secondsLeft + "...");
            }
        }, 20L, 20L);
        parcelPvpExitCountdownTasks.put(playerId, taskId);
    }

    private void stopParcelPvpExitCountdown(UUID playerId) {
        Integer taskId = parcelPvpExitCountdownTasks.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        parcelPvpExitCountdownKeys.remove(playerId);
    }

    private void updateParcelPvpState(Player player, Location to) {
        UUID playerId = player.getUniqueId();
        IslandData island = islandService.getIslandAt(to);
        ParcelData parcel = island == null ? null : islandService.getParcelAt(island, to);
        ParcelData.CombatMode nextMode = getParcelCombatMode(parcel);
        String nextKey = nextMode != ParcelData.CombatMode.NONE ? islandService.getParcelPvpKey(island, parcel) : null;
        String previousKey = parcelPvpStates.get(playerId);
        ParcelData.CombatMode previousMode = parcelCombatModes.getOrDefault(playerId, ParcelData.CombatMode.NONE);
        if (nextMode != ParcelData.CombatMode.NONE && !canEnterParcelCombatZone(island, parcel, playerId)) {
            islandService.clearParcelPvpConsent(playerId);
            clearPvpScoreboard(player);
            return;
        }
        boolean sameZone = nextKey != null && nextKey.equals(previousKey) && nextMode == previousMode;
        if (previousKey != null && !sameZone) {
            syncActiveParcelZone(playerId, null, ParcelData.CombatMode.NONE);
            islandService.clearParcelPvpConsent(playerId);
            clearPvpScoreboard(player);
            player.sendMessage(ChatColor.GREEN + "Du verl\u00e4sst die " + combatZoneName(previousMode) + ".");
            if (previousMode == ParcelData.CombatMode.PVP) {
                broadcastParcelPvpMessage(ChatColor.GRAY + player.getName() + " hat die PvP-Zone verlassen.");
            }
        }
        if (nextKey == null) {
            return;
        }
        if (sameZone) {
            syncActiveParcelZone(playerId, nextKey, nextMode);
            if (parcel != null && shouldRefreshCombatScoreboard(playerId, nextKey)) {
                if (nextMode == ParcelData.CombatMode.GAMES) {
                    islandService.clearParcelPvpConsent(playerId);
                }
                showParcelCombatScoreboard(player, island, parcel);
            }
            return;
        }
        syncActiveParcelZone(playerId, nextKey, nextMode);
        markCombatScoreboardRefreshed(playerId, nextKey);
        String parcelName = islandService.getParcelDisplayName(parcel);
        if (nextMode == ParcelData.CombatMode.GAMES) {
            islandService.clearParcelPvpConsent(playerId);
            showParcelCombatScoreboard(player, island, parcel);
            player.sendMessage(ChatColor.AQUA + "Du betrittst Games auf dem Grundst\u00fcck " + parcelName + ".");
            player.sendMessage(ChatColor.YELLOW + "Die Zone verh\u00e4lt sich wie PvP, aber ohne Spielerschaden.");
            return;
        }
        islandService.grantParcelPvpConsent(playerId, island, parcel);
        showParcelCombatScoreboard(player, island, parcel);
        player.sendMessage(ChatColor.RED + "Du trittst PvP auf dem Grundst\u00fcck " + parcelName + " bei.");
        player.sendMessage(ChatColor.YELLOW + "Du kannst jetzt k\u00e4mpfen und angegriffen werden, solange du in der Zone bist.");
        broadcastParcelPvpMessage(ChatColor.GOLD + player.getName() + ChatColor.GRAY + " ist der PvP-Zone " + parcelName + " beigetreten.");
    }

    private void clearParcelPvpState(UUID playerId) {
        syncActiveParcelZone(playerId, null, ParcelData.CombatMode.NONE);
        combatScoreboardStates.remove(playerId);
        islandService.clearParcelPvpConsent(playerId);
        parcelPvpCombatTags.remove(playerId);
        stopParcelPvpExitCountdown(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.removeMetadata(PVP_TEAM_WOOL_METADATA, plugin);
            restoreCompassTarget(player);
            clearPvpScoreboard(player);
        }
    }

    private void clearParcelGamesState(UUID playerId) {
        String previousKey = parcelGamesStates.remove(playerId);
        combatScoreboardStates.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && previousKey != null) {
            clearPvpScoreboard(player);
        }
    }

    private void updateParcelPvpCompass(Player player, Location to) {
        if (player == null || to == null || to.getWorld() == null) return;
        UUID playerId = player.getUniqueId();
        IslandData island = islandService.getIslandAt(to);
        ParcelData parcel = island == null ? null : islandService.getParcelAt(island, to);
        ParcelData.CombatMode mode = getParcelCombatMode(parcel);
        boolean suppress = parcel != null
                && mode != ParcelData.CombatMode.NONE
                && !parcel.isPvpCompassEnabled()
                && (mode == ParcelData.CombatMode.GAMES || islandService.hasParcelPvpConsent(playerId, island, parcel));
        if (suppress) {
            player.setCompassTarget(player.getLocation());
            parcelPvpCompassSuppressed.put(playerId, true);
            return;
        }
        if (Boolean.TRUE.equals(parcelPvpCompassSuppressed.remove(playerId))) {
            restoreCompassTarget(player);
        }
    }

    private void restoreCompassTarget(Player player) {
        if (player == null || player.getWorld() == null) return;
        player.setCompassTarget(player.getWorld().getSpawnLocation());
    }

    private boolean isParcelCombatZone(ParcelData parcel) {
        return getParcelCombatMode(parcel) != ParcelData.CombatMode.NONE;
    }

    private boolean canEnterParcelCombatZone(IslandData island, ParcelData parcel, UUID playerId) {
        if (parcel == null) return false;
        if (getParcelCombatMode(parcel) == ParcelData.CombatMode.GAMES) return true;
        return islandService.canEnterParcelPvp(island, parcel, playerId);
    }

    private void updateParcelPveState(Player player, Location to) {
        UUID playerId = player.getUniqueId();
        IslandData island = islandService.getIslandAt(to);
        ParcelData parcel = island == null ? null : islandService.getParcelAt(island, to);
        String nextKey = parcel != null && parcel.isPveEnabled() ? islandService.getParcelPveKey(island, parcel) : null;
        String previousKey = parcelPveStates.get(playerId);
        if (previousKey != null && !previousKey.equals(nextKey)) {
            parcelPveStates.remove(playerId);
            islandService.leaveParcelPve(player, previousKey, true);
            clearPvpScoreboard(player);
            player.sendMessage(ChatColor.GRAY + "Du verl\u00e4sst die PvE-Zone.");
        }
        if (nextKey == null) {
            return;
        }
        if (nextKey.equals(previousKey)) {
            if (shouldRefreshCombatScoreboard(playerId, nextKey)) {
                showParcelPveScoreboard(player, island, parcel);
            }
            return;
        }
        if (islandService.enterParcelPve(player, island, parcel)) {
            parcelPveStates.put(playerId, nextKey);
            markCombatScoreboardRefreshed(playerId, nextKey);
            showParcelPveScoreboard(player, island, parcel);
        }
    }

    private void clearParcelPveState(UUID playerId) {
        String previousKey = parcelPveStates.remove(playerId);
        combatScoreboardStates.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && previousKey != null) {
            islandService.leaveParcelPve(player, previousKey, true);
        }
        if (player != null) {
            clearPvpScoreboard(player);
        }
    }

    private void showParcelPveScoreboard(Player player, IslandData island, ParcelData parcel) {
        var snapshot = islandService.getParcelPveSnapshot(island, parcel, player.getUniqueId()).orElse(null);
        if (snapshot == null) return;
        Scoreboard scoreboard = Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getNewScoreboard();
        if (scoreboard == null) return;
        Objective objective = scoreboard.registerNewObjective("parcelpve", "dummy", ChatColor.DARK_GREEN + "PvE " + snapshot.parcelName());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        int score = 15;
        objective.getScore(ChatColor.WHITE + "Welle: " + ChatColor.GREEN + snapshot.currentWave() + "/" + snapshot.requiredWaves()).setScore(score--);
        objective.getScore(ChatColor.WHITE + "Spieler: " + ChatColor.AQUA + snapshot.participantCount()).setScore(score--);
        objective.getScore(ChatColor.WHITE + "Mobs: " + ChatColor.RED + snapshot.activeMobCount()).setScore(score--);
        objective.getScore(ChatColor.WHITE + "Level: " + ChatColor.GOLD + snapshot.pendingRewardLevels()).setScore(score--);
        objective.getScore(ChatColor.YELLOW + "Ziel:").setScore(score--);
        objective.getScore(ChatColor.GOLD + snapshot.objectiveText()).setScore(score--);
        objective.getScore(ChatColor.DARK_GRAY + " ").setScore(score--);
        for (var entry : snapshot.spawnEntries().entrySet()) {
            objective.getScore(ChatColor.GOLD + entry.getKey() + ChatColor.GRAY + " " + ChatColor.WHITE + trimPveScoreboardLine(entry.getValue(), 24)).setScore(score--);
            if (score <= 0) break;
        }
        player.setScoreboard(scoreboard);
    }

    private String trimPveScoreboardLine(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        if (maxLength <= 3) return text.substring(0, Math.max(0, maxLength));
        return text.substring(0, maxLength - 3) + "...";
    }

    private void showParcelPvpScoreboard(Player player, IslandData island, ParcelData parcel) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getNewScoreboard();
        if (scoreboard == null) return;
        Objective objective = scoreboard.registerNewObjective("parcelpvp", "dummy", ChatColor.RED + "PvP " + islandService.getParcelDisplayName(parcel));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        int score = 15;
        objective.getScore(ChatColor.WHITE + "GS: " + ChatColor.YELLOW + islandService.getParcelDisplayName(parcel)).setScore(score--);
        Material teamWool = parcelPvpTeamWool.get(player.getUniqueId());
        objective.getScore(ChatColor.WHITE + "Team: " + formatPvpTeamLabel(teamWool)).setScore(score--);
        score = appendParcelTeamPoints(objective, score, island, parcel);
        objective.getScore(ChatColor.DARK_GRAY + " ").setScore(score--);
        var ranking = parcel.getPvpKills().entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5)
                .toList();
        if (ranking.isEmpty()) {
            objective.getScore(ChatColor.GRAY + "Noch keine Kills").setScore(score--);
        } else {
            int place = 1;
            for (var entry : ranking) {
                String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (name == null || name.isBlank()) name = entry.getKey().toString().substring(0, 8);
                objective.getScore(ChatColor.GOLD + "" + place + ". " + ChatColor.WHITE + name + ChatColor.GRAY + " - " + entry.getValue()).setScore(score--);
                place++;
            }
        }
        player.setScoreboard(scoreboard);
    }

    private void showParcelCombatScoreboard(Player player, IslandData island, ParcelData parcel) {
        if (getParcelCombatMode(parcel) == ParcelData.CombatMode.GAMES) {
            showParcelGamesScoreboard(player, island, parcel);
            return;
        }
        showParcelPvpScoreboard(player, island, parcel);
    }

    private ParcelData.CombatMode getParcelCombatMode(ParcelData parcel) {
        return parcel == null ? ParcelData.CombatMode.NONE : parcel.getCombatMode();
    }

    private String combatZoneName(ParcelData.CombatMode mode) {
        return mode == ParcelData.CombatMode.GAMES ? "Games-Zone" : "PvP-Zone";
    }

    private void syncActiveParcelZone(UUID playerId, String zoneKey, ParcelData.CombatMode mode) {
        if (playerId == null || zoneKey == null || mode == null || mode == ParcelData.CombatMode.NONE) {
            parcelPvpStates.remove(playerId);
            parcelGamesStates.remove(playerId);
            parcelCombatModes.remove(playerId);
            return;
        }
        parcelPvpStates.put(playerId, zoneKey);
        parcelCombatModes.put(playerId, mode);
        if (mode == ParcelData.CombatMode.GAMES) {
            parcelGamesStates.put(playerId, zoneKey);
            return;
        }
        parcelGamesStates.remove(playerId);
    }

    public void resetParcelCtf(IslandData island, ParcelData parcel) {
        if (island == null || parcel == null) return;
        String parcelKey = islandService.getParcelPvpKey(island, parcel);
        if (parcelKey == null) return;
        CtfRuntime runtime = parcelCtfRuntime.get(parcelKey);
        List<UUID> carriers = new java.util.ArrayList<>();
        for (Map.Entry<UUID, CtfCarrierState> entry : ctfCarrierStates.entrySet()) {
            if (parcelKey.equals(entry.getValue().parcelKey())) {
                carriers.add(entry.getKey());
            }
        }
        for (UUID carrierId : carriers) {
            returnCtfFlagToBase(carrierId, false);
        }
        if (runtime != null) {
            for (String flagId : new java.util.ArrayList<>(runtime.hiddenFlagsById().keySet())) {
                restoreCtfFlagBase(flagId, runtime);
            }
        }
        parcelCtfRuntime.remove(parcelKey);
        clearParcelCtfShelfVisuals(parcel);
        refreshParcelPvpScoreboards(island, parcel);
    }

    public void resetAllParcelCtfStates() {
        for (IslandData island : islandService.getAllIslands()) {
            if (island == null) continue;
            for (ParcelData parcel : island.getParcels().values()) {
                if (parcel == null || !parcel.isCtfEnabled()) continue;
                resetParcelCtf(island, parcel);
            }
        }
    }

    public boolean isActiveCtfShelfLocation(Location location) {
        if (location == null || !skyWorldService.isSkyCityWorld(location.getWorld())) return false;
        IslandData island = islandService.getIslandAt(location);
        if (island == null) return false;
        ParcelData parcel = islandService.getParcelAt(island, location);
        if (parcel == null || !parcel.isGamesEnabled() || !parcel.isCtfEnabled()) return false;
        return findCtfShelfAt(parcel, location) != null;
    }

    public void startParcelCountdown(IslandData island, ParcelData parcel) {
        if (island == null || parcel == null) return;
        String parcelKey = islandService.getParcelPvpKey(island, parcel);
        if (parcelKey == null) return;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!isPlayerInsideParcel(online, island, parcel)) continue;
            sendParcelCountdownTitle(online, parcelKey, 3);
            updateParcelCountdownState(online, online.getLocation());
        }
    }

    public void stopParcelCountdown(IslandData island, ParcelData parcel) {
        if (island == null || parcel == null) return;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!isPlayerInsideParcel(online, island, parcel)) continue;
            removeParcelCountdownBossBar(online);
            parcelCountdownTitleStates.remove(online.getUniqueId());
        }
    }

    private void updateParcelCtfState(Player player, Location location) {
        if (player == null) return;
        UUID playerId = player.getUniqueId();
        CtfCarrierState carrier = ctfCarrierStates.get(playerId);
        if (carrier == null) {
            removeCtfCarryDisplay(playerId);
            return;
        }
        IslandData island = location == null ? null : islandService.getIslandAt(location);
        ParcelData parcel = island == null ? null : islandService.getParcelAt(island, location);
        String parcelKey = parcel == null ? null : islandService.getParcelPvpKey(island, parcel);
        if (parcel == null || !parcel.isGamesEnabled() || !parcel.isCtfEnabled() || !carrier.parcelKey().equals(parcelKey)) {
            returnCtfFlagToBase(playerId, false);
            return;
        }
        ensureCtfCarryDisplay(player, carrier);
    }

    private void updateParcelCountdownState(Player player, Location location) {
        if (player == null || location == null || !skyWorldService.isSkyCityWorld(location.getWorld())) {
            if (player != null) {
                removeParcelCountdownBossBar(player);
                parcelCountdownTitleStates.remove(player.getUniqueId());
            }
            return;
        }
        IslandData island = islandService.getIslandAt(location);
        ParcelData parcel = island == null ? null : islandService.getParcelAt(island, location);
        if (island == null || parcel == null) {
            removeParcelCountdownBossBar(player);
            parcelCountdownTitleStates.remove(player.getUniqueId());
            return;
        }
        long now = System.currentTimeMillis();
        if (parcel.getCountdownEndsAt() <= 0L) {
            removeParcelCountdownBossBar(player);
            parcelCountdownTitleStates.remove(player.getUniqueId());
            return;
        }
        if (parcel.getCountdownEndsAt() <= now) {
            finishParcelCountdown(island, parcel, true);
            removeParcelCountdownBossBar(player);
            parcelCountdownTitleStates.remove(player.getUniqueId());
            return;
        }
        String parcelKey = islandService.getParcelPvpKey(island, parcel);
        if (parcelKey == null) {
            removeParcelCountdownBossBar(player);
            parcelCountdownTitleStates.remove(player.getUniqueId());
            return;
        }

        long countdownStartAt = parcel.getCountdownStartAt();
        long countdownEndsAt = parcel.getCountdownEndsAt();
        long durationMs = Math.max(1L, parcel.getCountdownDurationSeconds() * 1000L);
        if (countdownStartAt > now) {
            int countdownNumber = (int)Math.max(1L, Math.min(3L, (countdownStartAt - now + 999L) / 1000L));
            sendParcelCountdownTitle(player, parcelKey, countdownNumber);
            double progress = Math.max(0.0, Math.min(1.0, (double)(countdownStartAt - now) / 3000.0));
            showParcelCountdownBossBar(
                player,
                ChatColor.GOLD + "Start in "
                    + ChatColor.WHITE + countdownNumber
                    + ChatColor.DARK_GRAY + " | "
                    + ChatColor.WHITE + islandService.getParcelDisplayName(parcel),
                BarColor.YELLOW,
                progress <= 0.0 ? 0.01 : progress
            );
            return;
        }

        sendParcelCountdownTitle(player, parcelKey, 0);
        long remainingMs = Math.max(0L, countdownEndsAt - now);
        double progress = Math.max(0.0, Math.min(1.0, (double)remainingMs / (double)durationMs));
        showParcelCountdownBossBar(
            player,
            ChatColor.AQUA + "Countdown"
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.WHITE + islandService.getParcelDisplayName(parcel)
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.WHITE + formatMillisShort(remainingMs),
            BarColor.BLUE,
            progress <= 0.0 ? 0.01 : progress
        );
    }

    private void finishParcelCountdown(IslandData island, ParcelData parcel, boolean notifyPlayers) {
        if (island == null || parcel == null) return;
        if (notifyPlayers) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!isPlayerInsideParcel(online, island, parcel)) continue;
                removeParcelCountdownBossBar(online);
                parcelCountdownTitleStates.remove(online.getUniqueId());
                online.sendTitle(ChatColor.RED + "Zeit abgelaufen", ChatColor.GRAY + islandService.getParcelDisplayName(parcel), 0, 30, 10);
            }
        }
        islandService.finishParcelCountdown(island, parcel);
    }

    private boolean isPlayerInsideParcel(Player player, IslandData island, ParcelData parcel) {
        if (player == null || island == null || parcel == null || !player.isOnline()) return false;
        if (!skyWorldService.isSkyCityWorld(player.getWorld())) return false;
        IslandData currentIsland = islandService.getIslandAt(player.getLocation());
        if (currentIsland == null || !island.getOwner().equals(currentIsland.getOwner())) return false;
        ParcelData currentParcel = islandService.getParcelAt(island, player.getLocation());
        return currentParcel != null && parcel.getChunkKey().equals(currentParcel.getChunkKey());
    }

    private void sendParcelCountdownTitle(Player player, String parcelKey, int countdownNumber) {
        if (player == null || parcelKey == null) return;
        String stateKey = parcelKey + ":" + countdownNumber;
        if (stateKey.equals(parcelCountdownTitleStates.get(player.getUniqueId()))) return;
        parcelCountdownTitleStates.put(player.getUniqueId(), stateKey);
        if (countdownNumber > 0) {
            player.sendTitle(ChatColor.GOLD.toString() + countdownNumber, ChatColor.GRAY + "Startet gleich", 0, 15, 5);
            return;
        }
        player.sendTitle(ChatColor.GREEN + "Los!", ChatColor.GRAY + "Der Countdown l\u00e4uft", 0, 20, 8);
    }

    private boolean tryPickupCtfFlag(Player player, Block clicked) {
        if (player == null || clicked == null || clicked.getType() != Material.TARGET) return false;
        IslandData island = islandService.getIslandAt(clicked.getLocation());
        ParcelData parcel = island == null ? null : islandService.getParcelAt(island, clicked.getLocation());
        if (parcel == null || !parcel.isGamesEnabled() || !parcel.isCtfEnabled()) return false;
        Material playerTeam = parcelPvpTeamWool.get(player.getUniqueId());
        if (!isWool(playerTeam)) {
            player.sendMessage(ChatColor.RED + "Du brauchst zuerst eine Teamfarbe auf Wolle.");
            return true;
        }
        CtfFlagDefinition flag = findCtfFlagAt(parcel, clicked.getLocation());
        if (flag == null) return false;
        if (flag.teamWool() == playerTeam) {
            player.sendMessage(ChatColor.RED + "Du kannst deine eigene Flagge nicht tragen.");
            return true;
        }
        if (ctfCarrierStates.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Du tr\u00e4gst bereits eine Flagge.");
            return true;
        }
        String parcelKey = islandService.getParcelPvpKey(island, parcel);
        if (parcelKey == null) return true;
        CtfRuntime runtime = ctfRuntime(parcelKey);
        if (runtime.shelfByFlag().containsKey(flag.id())) {
            player.sendMessage(ChatColor.RED + "Diese Flagge wurde bereits abgegeben.");
            return true;
        }
        if (runtime.carrierByFlag().containsKey(flag.id())) {
            player.sendMessage(ChatColor.RED + "Diese Flagge wird bereits getragen.");
            return true;
        }
        hideCtfFlagBase(flag, runtime);
        CtfCarrierState carrier = new CtfCarrierState(parcelKey, flag.id(), flag.teamWool(), flag.bannerMaterial(), flag.targetLocation(), flag.bannerLocation());
        ctfCarrierStates.put(player.getUniqueId(), carrier);
        runtime.carrierByFlag().put(flag.id(), player.getUniqueId());
        ensureCtfCarryDisplay(player, carrier);
        player.sendMessage(ChatColor.GOLD + "Du tr\u00e4gst jetzt die " + woolLabel(flag.teamWool()) + ChatColor.GOLD + "-Flagge.");
        refreshParcelPvpScoreboards(island, parcel);
        return true;
    }

    private boolean tryCaptureCtfFlag(Player player, Block clicked) {
        if (player == null || clicked == null) return false;
        CtfCarrierState carrier = ctfCarrierStates.get(player.getUniqueId());
        if (carrier == null) return false;
        IslandData island = islandService.getIslandAt(clicked.getLocation());
        ParcelData parcel = island == null ? null : islandService.getParcelAt(island, clicked.getLocation());
        if (parcel == null || !parcel.isGamesEnabled() || !parcel.isCtfEnabled()) return false;
        String parcelKey = islandService.getParcelPvpKey(island, parcel);
        if (parcelKey == null || !parcelKey.equals(carrier.parcelKey())) return false;
        CtfShelfDefinition shelf = findCtfShelfAt(parcel, clicked.getLocation());
        if (shelf == null) return false;
        Material playerTeam = parcelPvpTeamWool.get(player.getUniqueId());
        if (!isWool(playerTeam) || shelf.teamWool() != playerTeam) {
            player.sendMessage(ChatColor.RED + "Du musst die Flagge an deinem Team-Checkpoint abgeben.");
            return true;
        }
        CtfRuntime runtime = ctfRuntime(parcelKey);
        long usedSlots = runtime.shelfByFlag().values().stream().filter(shelf.id()::equals).count();
        if (usedSlots >= shelf.capacity()) {
            player.sendMessage(ChatColor.RED + "Dieses Shelf ist bereits voll.");
            return true;
        }
        runtime.carrierByFlag().remove(carrier.flagId());
        runtime.shelfByFlag().put(carrier.flagId(), shelf.id());
        ctfCarrierStates.remove(player.getUniqueId());
        removeCtfCarryDisplay(player.getUniqueId());
        syncParcelCtfShelfVisuals(parcel, runtime);
        player.sendMessage(ChatColor.GREEN + "Flagge abgegeben.");
        Material finishedWinner = hasTeamCapturedAllForeignFlags(parcel, runtime, playerTeam) ? playerTeam : null;
        if (finishedWinner == null && areAllCtfFlagsCaptured(parcel, runtime)) {
            finishedWinner = determineCtfWinningTeam(parcel, runtime);
        }
        if (finishedWinner != null || areAllCtfFlagsCaptured(parcel, runtime)) {
            Material winner = finishedWinner;
            int winnerFlags = countCapturedFlagsForTeam(parcel, runtime, winner);
            if (isWool(winner)) {
                broadcastParcelPvpMessage(
                    ChatColor.GOLD + player.getName()
                        + ChatColor.GRAY + " hat CTF auf "
                        + islandService.getParcelDisplayName(parcel)
                        + ChatColor.GRAY + " beendet. Gewinner: "
                        + woolChatColor(winner) + woolLabel(winner)
                        + ChatColor.GRAY + " mit "
                        + ChatColor.WHITE + winnerFlags
                        + ChatColor.GRAY + " Flaggen."
                );
            } else {
                broadcastParcelPvpMessage(ChatColor.GOLD + player.getName() + ChatColor.GRAY + " hat CTF auf " + islandService.getParcelDisplayName(parcel) + ChatColor.GRAY + " beendet. Ergebnis: " + ChatColor.YELLOW + "Gleichstand" + ChatColor.GRAY + ".");
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!isPlayerInsideParcel(online, island, parcel)) continue;
                if (isWool(winner)) {
                    online.sendTitle(woolChatColor(winner) + woolLabel(winner), ChatColor.GRAY + "gewinnt mit " + winnerFlags + " Flaggen", 0, 40, 10);
                } else {
                    online.sendTitle(ChatColor.YELLOW + "Gleichstand", ChatColor.GRAY + "Keine eindeutige Siegerfarbe", 0, 40, 10);
                }
            }
        }
        refreshParcelPvpScoreboards(island, parcel);
        return true;
    }

    private boolean tryResetCtfViaButton(Player player, Block clicked) {
        if (player == null || clicked == null) return false;
        Material type = clicked.getType();
        String name = type.name();
        if (!(name.endsWith("_BUTTON") || type == Material.LEVER)) return false;
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN)) {
            Block adjacent = clicked.getRelative(face);
            if (adjacent.getType() != Material.TARGET) continue;
            IslandData island = islandService.getIslandAt(adjacent.getLocation());
            ParcelData parcel = island == null ? null : islandService.getParcelAt(island, adjacent.getLocation());
            if (parcel == null || !parcel.isCtfEnabled()) continue;
            if (!islandService.isParcelOwner(island, parcel, player.getUniqueId())) continue;
            if (findCtfFlagAt(parcel, adjacent.getLocation()) != null || adjacent.getType() == Material.TARGET) {
                resetParcelCtf(island, parcel);
                player.sendMessage(ChatColor.YELLOW + "CTF wurde zur\u00fcckgesetzt.");
                return true;
            }
        }
        return false;
    }

    private void returnCtfFlagToBase(UUID playerId, boolean announce) {
        CtfCarrierState carrier = ctfCarrierStates.remove(playerId);
        if (carrier == null) return;
        removeCtfCarryDisplay(playerId);
        CtfRuntime runtime = parcelCtfRuntime.get(carrier.parcelKey());
        if (runtime != null) {
            runtime.carrierByFlag().remove(carrier.flagId());
            restoreCtfFlagBase(carrier.flagId(), runtime);
        }
        Player player = Bukkit.getPlayer(playerId);
        if (announce && player != null) {
            player.sendMessage(ChatColor.RED + "Die Flagge ist zur Basis zur\u00fcckgekehrt.");
        }
    }

    private CtfRuntime ctfRuntime(String parcelKey) {
        return parcelCtfRuntime.computeIfAbsent(parcelKey, ignored -> new CtfRuntime(new HashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>()));
    }

    private void showParcelGamesScoreboard(Player player, IslandData island, ParcelData parcel) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getNewScoreboard();
        if (scoreboard == null) return;
        Objective objective = scoreboard.registerNewObjective("parcelgames", "dummy", ChatColor.AQUA + "Games " + islandService.getParcelDisplayName(parcel));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        int score = 15;
        objective.getScore(ChatColor.WHITE + "GS: " + ChatColor.YELLOW + islandService.getParcelDisplayName(parcel)).setScore(score--);
        Material teamWool = parcelPvpTeamWool.get(player.getUniqueId());
        objective.getScore(ChatColor.WHITE + "Team: " + formatPvpTeamLabel(teamWool)).setScore(score--);
        score = parcel.isSnowballFightEnabled()
                ? appendParcelSnowballPoints(objective, score, island, parcel)
                : appendParcelTeamPoints(objective, score, island, parcel);
        if (parcel.isCtfEnabled()) {
            score = appendParcelCtfStatus(objective, score, island, parcel, player);
        }
        String modeLabel = parcel.isSnowballFightEnabled() ? "Schneeballschlacht" : "Games";
        objective.getScore(ChatColor.WHITE + "Modus: " + ChatColor.AQUA + modeLabel).setScore(score--);
        objective.getScore(ChatColor.GRAY + "Kein Spielerschaden").setScore(score--);
        player.setScoreboard(scoreboard);
    }

    private int appendParcelCtfStatus(Objective objective, int score, IslandData island, ParcelData parcel, Player player) {
        if (objective == null || island == null || parcel == null || player == null || score <= 0) return score;
        List<CtfFlagDefinition> flags = findCtfFlags(parcel);
        String parcelKey = islandService.getParcelPvpKey(island, parcel);
        CtfRuntime runtime = parcelKey == null ? null : parcelCtfRuntime.get(parcelKey);
        int placed = runtime == null ? 0 : runtime.shelfByFlag().size();
        objective.getScore(ChatColor.GOLD + "CTF: " + ChatColor.WHITE + placed + "/" + flags.size()).setScore(score--);
        CtfCarrierState carrier = ctfCarrierStates.get(player.getUniqueId());
        if (carrier != null && score > 0) {
            objective.getScore(ChatColor.YELLOW + "Flagge: " + woolChatColor(carrier.teamWool()) + woolLabel(carrier.teamWool())).setScore(score--);
        }
        if (runtime != null && areAllCtfFlagsCaptured(parcel, runtime) && score > 0) {
            Material winner = determineCtfWinningTeam(parcel, runtime);
            if (isWool(winner)) {
                objective.getScore(ChatColor.GREEN + "Sieger: " + woolChatColor(winner) + woolLabel(winner)).setScore(score--);
            } else {
                objective.getScore(ChatColor.YELLOW + "Sieger: Gleichstand").setScore(score--);
            }
        }
        return score;
    }

    private int appendParcelTeamPoints(Objective objective, int score, IslandData island, ParcelData parcel) {
        if (objective == null || island == null || parcel == null || score <= 0) return score;
        List<TeamScoreEntry> entries = collectParcelTeamScores(island, parcel);
        if (entries.isEmpty()) return score;
        objective.getScore(ChatColor.YELLOW + "Punkte:").setScore(score--);
        for (TeamScoreEntry entry : entries) {
            if (score <= 0) break;
            objective.getScore(formatTeamScoreLine(entry)).setScore(score--);
        }
        return score;
    }

    private int appendParcelSnowballPoints(Objective objective, int score, IslandData island, ParcelData parcel) {
        if (objective == null || island == null || parcel == null || score <= 0) return score;
        List<TeamScoreEntry> entries = collectParcelSnowballScores(island, parcel);
        objective.getScore(ChatColor.AQUA + "Schneebälle:").setScore(score--);
        if (entries.isEmpty()) {
            objective.getScore(ChatColor.GRAY + "Noch keine Teams").setScore(score--);
            return score;
        }
        for (TeamScoreEntry entry : entries) {
            if (score <= 0) break;
            objective.getScore(formatTeamScoreLine(entry)).setScore(score--);
        }
        return score;
    }

    public ItemStack createMagicSnowballItem(int amount) {
        ItemStack item = new ItemStack(Material.SNOWBALL, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Magischer Schneeball");
            meta.setLore(List.of(ChatColor.GRAY + "Treffer geben 1 Punkt", ChatColor.GRAY + "f\u00fcr deine Teamfarbe."));
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(magicSnowballItemKey, PersistentDataType.BYTE, (byte)1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isMagicSnowballItem(ItemStack item) {
        if (item == null || item.getType() != Material.SNOWBALL || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte marker = meta.getPersistentDataContainer().get(magicSnowballItemKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte)1;
    }

    private boolean isMagicSnowballProjectile(Snowball snowball) {
        if (snowball == null) return false;
        Byte marker = snowball.getPersistentDataContainer().get(magicSnowballProjectileKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte)1;
    }

    public boolean isMagicSnowballProjectile(Entity entity) {
        return entity instanceof Snowball snowball && isMagicSnowballProjectile(snowball);
    }

    private CtfFlagDefinition findCtfFlagAt(ParcelData parcel, Location location) {
        if (parcel == null || location == null) return null;
        for (CtfFlagDefinition flag : findCtfFlags(parcel)) {
            if (sameBlock(flag.targetLocation(), location)) return flag;
        }
        return null;
    }

    private CtfShelfDefinition findCtfShelfAt(ParcelData parcel, Location location) {
        if (parcel == null || location == null) return null;
        for (CtfShelfDefinition shelf : findCtfShelves(parcel)) {
            if (sameBlock(shelf.shelfLocation(), location)) return shelf;
        }
        return null;
    }

    private List<CtfFlagDefinition> findCtfFlags(ParcelData parcel) {
        java.util.ArrayList<CtfFlagDefinition> flags = new java.util.ArrayList<>();
        if (parcel == null || skyWorldService.getWorld() == null) return flags;
        for (int x = parcel.getMinX(); x <= parcel.getMaxX(); x++) {
            for (int z = parcel.getMinZ(); z <= parcel.getMaxZ(); z++) {
                for (int y = parcel.getMinY(); y <= parcel.getMaxY(); y++) {
                    Block target = skyWorldService.getWorld().getBlockAt(x, y, z);
                    if (target.getType() != Material.TARGET) continue;
                    Block banner = findFlagBannerBlock(target);
                    if (banner == null) continue;
                    Material teamWool = bannerMaterialToWool(banner.getType());
                    if (!isWool(teamWool)) continue;
                    String id = target.getX() + ":" + target.getY() + ":" + target.getZ();
                    flags.add(new CtfFlagDefinition(id, teamWool, banner.getType(), banner.getBlockData().getAsString(), target.getLocation(), banner.getLocation()));
                }
            }
        }
        return flags;
    }

    private Map<String, CtfFlagDefinition> collectCtfFlagsById(ParcelData parcel, CtfRuntime runtime) {
        Map<String, CtfFlagDefinition> flagsById = new LinkedHashMap<>();
        for (CtfFlagDefinition flag : findCtfFlags(parcel)) {
            flagsById.put(flag.id(), flag);
        }
        if (runtime != null) {
            flagsById.putAll(runtime.hiddenFlagsById());
        }
        return flagsById;
    }

    private List<CtfShelfDefinition> findCtfShelves(ParcelData parcel) {
        java.util.ArrayList<CtfShelfDefinition> shelves = new java.util.ArrayList<>();
        if (parcel == null || skyWorldService.getWorld() == null) return shelves;
        for (int x = parcel.getMinX(); x <= parcel.getMaxX(); x++) {
            for (int z = parcel.getMinZ(); z <= parcel.getMaxZ(); z++) {
                for (int y = parcel.getMinY(); y <= parcel.getMaxY(); y++) {
                    Block shelf = skyWorldService.getWorld().getBlockAt(x, y, z);
                    if (!isCtfShelfBlock(shelf.getType())) continue;
                    Material wool = shelf.getRelative(0, -1, 0).getType();
                    if (!isWool(wool)) continue;
                    String id = shelf.getX() + ":" + shelf.getY() + ":" + shelf.getZ();
                    int capacity = shelf.getType() == Material.CHISELED_BOOKSHELF ? 6 : 3;
                    shelves.add(new CtfShelfDefinition(id, wool, shelf.getLocation(), capacity));
                }
            }
        }
        return shelves;
    }

    private Block findFlagBannerBlock(Block target) {
        if (target == null) return null;
        Block above = target.getRelative(0, 1, 0);
        if (isBanner(above.getType())) return above;
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            Block adjacent = target.getRelative(face);
            if (isBanner(adjacent.getType())) return adjacent;
        }
        return null;
    }

    private boolean isBanner(Material material) {
        return material != null && material.name().endsWith("_BANNER");
    }

    private boolean isCtfShelfBlock(Material material) {
        if (material == null) return false;
        return material == Material.CHISELED_BOOKSHELF || material.name().endsWith("_SHELF");
    }

    private Material bannerMaterialToWool(Material bannerMaterial) {
        if (!isBanner(bannerMaterial)) return null;
        String woolName = bannerMaterial.name().replace("_WALL_BANNER", "_WOOL").replace("_BANNER", "_WOOL");
        try {
            return Material.valueOf(woolName);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void ensureCtfCarryDisplay(Player player, CtfCarrierState carrier) {
        if (player == null || carrier == null || player.getWorld() == null) return;
        Location displayLocation = player.getLocation();
        UUID existingId = ctfCarryDisplaysByPlayer.get(player.getUniqueId());
        Entity existing = existingId == null ? null : Bukkit.getEntity(existingId);
        if (existing instanceof ItemDisplay display && display.isValid()) {
            configureCtfCarryDisplay(display, carrier.bannerMaterial());
            if (!player.getPassengers().contains(display)) {
                player.addPassenger(display);
            }
            return;
        }
        removeCtfCarryDisplay(player.getUniqueId());
        ItemDisplay display = (ItemDisplay) player.getWorld().spawnEntity(displayLocation, EntityType.ITEM_DISPLAY);
        configureCtfCarryDisplay(display, carrier.bannerMaterial());
        display.addScoreboardTag("skycity_ctf_carry_" + player.getUniqueId());
        player.addPassenger(display);
        ctfCarryDisplaysByPlayer.put(player.getUniqueId(), display.getUniqueId());
    }

    private void configureCtfCarryDisplay(ItemDisplay display, Material bannerMaterial) {
        if (display == null || bannerMaterial == null) return;
        display.setItemStack(new ItemStack(bannerMaterial));
        display.setBillboard(Display.Billboard.CENTER);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setTransformation(new Transformation(
                new Vector3f(0.0F, 0.35F, 0.0F),
                new AxisAngle4f(),
                new Vector3f(1.0F, 1.0F, 1.0F),
                new AxisAngle4f()
        ));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(0);
        display.setTeleportDuration(0);
        display.setPersistent(false);
        display.setInvulnerable(true);
    }

    private void removeCtfCarryDisplay(UUID playerId) {
        if (playerId == null) return;
        UUID displayId = ctfCarryDisplaysByPlayer.remove(playerId);
        if (displayId == null) return;
        Entity entity = Bukkit.getEntity(displayId);
        if (entity != null) entity.remove();
    }

    private void syncParcelCtfShelfVisuals(ParcelData parcel, CtfRuntime runtime) {
        if (parcel == null) return;
        clearParcelCtfShelfContents(parcel);
        if (runtime == null) return;
        Map<String, CtfFlagDefinition> flagsById = collectCtfFlagsById(parcel, runtime);
        for (CtfShelfDefinition shelf : findCtfShelves(parcel)) {
            Block block = shelf.shelfLocation().getBlock();
            BlockState state = block.getState();
            Inventory inventory = ctfShelfInventory(state);
            if (inventory == null) continue;
            clearCtfShelfInventory(inventory);
            int slot = 0;
            for (Map.Entry<String, String> entry : runtime.shelfByFlag().entrySet()) {
                if (!shelf.id().equals(entry.getValue())) continue;
                CtfFlagDefinition flag = flagsById.get(entry.getKey());
                if (flag == null || slot >= inventory.getSize()) continue;
                inventory.setItem(slot, createCtfShelfItem(block.getType(), flag));
                slot++;
            }
        }
    }

    private void clearParcelCtfShelfContents(ParcelData parcel) {
        if (parcel == null) return;
        for (CtfShelfDefinition shelf : findCtfShelves(parcel)) {
            BlockState state = shelf.shelfLocation().getBlock().getState();
            Inventory inventory = ctfShelfInventory(state);
            if (inventory == null) continue;
            clearCtfShelfInventory(inventory);
        }
    }

    private void clearParcelCtfShelfVisuals(ParcelData parcel) {
        if (parcel == null) return;
        for (CtfShelfDefinition shelf : findCtfShelves(parcel)) {
            Block block = shelf.shelfLocation().getBlock();
            resetCtfShelfBlock(block);
            pushCtfShelfResetUpdate(block);
        }
    }

    private void clearCtfShelfInventory(Inventory inventory) {
        if (inventory == null) return;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, null);
        }
    }

    private void resetCtfShelfBlock(Block block) {
        if (block == null) return;
        Material type = block.getType();
        if (!isCtfShelfBlock(type)) return;
        BlockData data = block.getBlockData().clone();
        block.setType(Material.AIR, false);
        block.setType(type, false);
        block.setBlockData(data, false);
        BlockState freshState = block.getState();
        Inventory freshInventory = ctfShelfInventory(freshState);
        if (freshInventory != null) {
            clearCtfShelfInventory(freshInventory);
        }
    }

    private void pushCtfShelfResetUpdate(Block block) {
        if (block == null || block.getWorld() == null) return;
        BlockState freshState = block.getState();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == null || !online.isOnline()) continue;
            if (!online.getWorld().equals(block.getWorld())) continue;
            if (online.getLocation().distanceSquared(block.getLocation().add(0.5, 0.5, 0.5)) > 128 * 128) continue;
            if (freshState instanceof TileState tileState) {
                online.sendBlockUpdate(block.getLocation(), tileState);
            } else {
                online.sendBlockChange(block.getLocation(), block.getBlockData());
            }
        }
    }

    private boolean areAllCtfFlagsCaptured(ParcelData parcel, CtfRuntime runtime) {
        if (parcel == null || runtime == null) return false;
        int totalFlags = collectCtfFlagsById(parcel, runtime).size();
        return totalFlags > 0 && runtime.shelfByFlag().size() >= totalFlags;
    }

    private Material determineCtfWinningTeam(ParcelData parcel, CtfRuntime runtime) {
        if (parcel == null || runtime == null) return null;
        Map<String, CtfShelfDefinition> shelvesById = new HashMap<>();
        for (CtfShelfDefinition shelf : findCtfShelves(parcel)) {
            shelvesById.put(shelf.id(), shelf);
        }
        Map<Material, Integer> captureCounts = new HashMap<>();
        for (String shelfId : runtime.shelfByFlag().values()) {
            CtfShelfDefinition shelf = shelvesById.get(shelfId);
            if (shelf == null || !isWool(shelf.teamWool())) continue;
            captureCounts.merge(shelf.teamWool(), 1, Integer::sum);
        }
        Material winner = null;
        int best = -1;
        boolean tie = false;
        for (Map.Entry<Material, Integer> entry : captureCounts.entrySet()) {
            int value = entry.getValue();
            if (value > best) {
                winner = entry.getKey();
                best = value;
                tie = false;
            } else if (value == best) {
                tie = true;
            }
        }
        return tie ? null : winner;
    }

    private int countCapturedFlagsForTeam(ParcelData parcel, CtfRuntime runtime, Material teamWool) {
        if (parcel == null || runtime == null || !isWool(teamWool)) return 0;
        Map<String, CtfShelfDefinition> shelvesById = new HashMap<>();
        for (CtfShelfDefinition shelf : findCtfShelves(parcel)) {
            shelvesById.put(shelf.id(), shelf);
        }
        int count = 0;
        for (String shelfId : runtime.shelfByFlag().values()) {
            CtfShelfDefinition shelf = shelvesById.get(shelfId);
            if (shelf != null && shelf.teamWool() == teamWool) {
                count++;
            }
        }
        return count;
    }

    private boolean hasTeamCapturedAllForeignFlags(ParcelData parcel, CtfRuntime runtime, Material teamWool) {
        if (parcel == null || runtime == null || !isWool(teamWool)) return false;
        int foreignFlags = 0;
        for (CtfFlagDefinition flag : collectCtfFlagsById(parcel, runtime).values()) {
            if (flag.teamWool() != teamWool) {
                foreignFlags++;
            }
        }
        return foreignFlags > 0 && countCapturedFlagsForTeam(parcel, runtime, teamWool) >= foreignFlags;
    }

    private void hideCtfFlagBase(CtfFlagDefinition flag, CtfRuntime runtime) {
        if (flag == null || runtime == null || flag.bannerLocation().getWorld() == null) return;
        runtime.hiddenFlagsById().put(flag.id(), flag);
        Block bannerBlock = flag.bannerLocation().getBlock();
        if (bannerBlock.getType() == flag.bannerMaterial()) {
            bannerBlock.setType(Material.AIR, false);
        }
    }

    private void restoreCtfFlagBase(String flagId, CtfRuntime runtime) {
        if (flagId == null || runtime == null) return;
        CtfFlagDefinition flag = runtime.hiddenFlagsById().remove(flagId);
        if (flag == null || flag.bannerLocation().getWorld() == null) return;
        Block bannerBlock = flag.bannerLocation().getBlock();
        bannerBlock.setType(flag.bannerMaterial(), false);
        if (flag.bannerBlockData() != null && !flag.bannerBlockData().isBlank()) {
            try {
                BlockData data = Bukkit.createBlockData(flag.bannerBlockData());
                bannerBlock.setBlockData(data, false);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private Inventory ctfShelfInventory(BlockState state) {
        if (state instanceof org.bukkit.block.ChiseledBookshelf shelfState) {
            return shelfState.getInventory();
        }
        if (state instanceof org.bukkit.block.Shelf shelfState) {
            return shelfState.getInventory();
        }
        if (state instanceof InventoryHolder holder) {
            return holder.getInventory();
        }
        try {
            Method method = state.getClass().getMethod("getInventory");
            Object value = method.invoke(state);
            if (value instanceof Inventory inventory) {
                return inventory;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private ItemStack createCtfShelfItem(Material shelfType, CtfFlagDefinition flag) {
        ItemStack item = new ItemStack(flag.bannerMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(woolChatColor(flag.teamWool()) + woolLabel(flag.teamWool()) + ChatColor.WHITE + "-Flagge");
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<TeamScoreEntry> collectParcelTeamScores(IslandData island, ParcelData parcel) {
        if (island == null || parcel == null || skyWorldService.getWorld() == null) return List.of();
        String parcelKey = islandService.getParcelPvpKey(island, parcel);
        long now = System.currentTimeMillis();
        TeamScoreCache cached = parcelKey == null ? null : parcelTeamScoreCache.get(parcelKey);
        if (cached != null && now - cached.builtAt() < TEAM_SCORE_CACHE_TTL_MS) {
            return cached.entries();
        }
        Map<Material, Integer> activeScores = new LinkedHashMap<>();
        for (int x = parcel.getMinX(); x <= parcel.getMaxX(); x++) {
            for (int y = parcel.getMinY(); y <= parcel.getMaxY(); y++) {
                for (int z = parcel.getMinZ(); z <= parcel.getMaxZ(); z++) {
                    Block block = skyWorldService.getWorld().getBlockAt(x, y, z);
                    if (!isTeamScoreComponent(block)) continue;
                    Material wool = resolveTeamScoreWool(block);
                    if (!isWool(wool)) continue;
                    activeScores.putIfAbsent(wool, 0);
                    if (isActiveTeamScoreComponent(block)) {
                        activeScores.put(wool, activeScores.get(wool) + 1);
                    }
                }
            }
        }
        if (parcelKey != null) {
            Map<Material, Integer> manualScores = parcelManualTeamScores.get(parcelKey);
            if (manualScores != null) {
                for (Map.Entry<Material, Integer> entry : manualScores.entrySet()) {
                    if (!isWool(entry.getKey()) || entry.getValue() == null) continue;
                    activeScores.merge(entry.getKey(), Math.max(0, entry.getValue()), Integer::sum);
                }
            }
        }
        List<TeamScoreEntry> entries = activeScores.entrySet().stream()
                .map(entry -> new TeamScoreEntry(entry.getKey(), entry.getValue()))
                .toList();
        if (parcelKey != null) {
            parcelTeamScoreCache.put(parcelKey, new TeamScoreCache(now, entries));
        }
        return entries;
    }

    private void awardParcelTeamPoint(IslandData island, ParcelData parcel, Material wool, int points) {
        if (island == null || parcel == null || !isWool(wool) || points <= 0) return;
        String parcelKey = islandService.getParcelPvpKey(island, parcel);
        if (parcelKey == null) return;
        Map<Material, Integer> manualScores = parcelManualTeamScores.computeIfAbsent(parcelKey, ignored -> new HashMap<>());
        manualScores.merge(wool, points, Integer::sum);
        parcelTeamScoreCache.remove(parcelKey);
        refreshParcelPvpScoreboards(island, parcel);
    }

    private List<TeamScoreEntry> collectParcelSnowballScores(IslandData island, ParcelData parcel) {
        if (island == null || parcel == null) return List.of();
        String parcelKey = islandService.getParcelPvpKey(island, parcel);
        if (parcelKey == null) return List.of();
        Map<Material, Integer> combinedScores = new HashMap<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == null || !online.isOnline()) continue;
            UUID playerId = online.getUniqueId();
            boolean inZone = parcelKey.equals(parcelPvpStates.get(playerId)) || parcelKey.equals(parcelGamesStates.get(playerId));
            if (!inZone) continue;
            Material teamWool = parcelPvpTeamWool.get(playerId);
            if (isWool(teamWool)) {
                combinedScores.putIfAbsent(teamWool, 0);
            }
        }
        Map<Material, Integer> manualScores = parcelManualTeamScores.get(parcelKey);
        if (manualScores != null) {
            for (Map.Entry<Material, Integer> entry : manualScores.entrySet()) {
                if (!isWool(entry.getKey()) || entry.getValue() == null) continue;
                combinedScores.merge(entry.getKey(), Math.max(0, entry.getValue()), Integer::sum);
            }
        }
        return combinedScores.entrySet().stream()
                .filter(entry -> isWool(entry.getKey()))
                .sorted((a, b) -> {
                    int compare = Integer.compare(b.getValue(), a.getValue());
                    if (compare != 0) return compare;
                    return woolLabel(a.getKey()).compareToIgnoreCase(woolLabel(b.getKey()));
                })
                .map(entry -> new TeamScoreEntry(entry.getKey(), entry.getValue()))
                .toList();
    }

    public void resetParcelSnowballFight(IslandData island, ParcelData parcel) {
        if (island == null || parcel == null) return;
        String parcelKey = islandService.getParcelPvpKey(island, parcel);
        if (parcelKey == null) return;
        parcelManualTeamScores.remove(parcelKey);
        parcelTeamScoreCache.remove(parcelKey);
        refreshParcelPvpScoreboards(island, parcel);
    }

    private Material resolveTeamScoreWool(Block block) {
        if (!isTeamScoreComponent(block)) return null;
        Material direct = woolFromBlock(block.getRelative(0, -1, 0));
        if (direct != null) return direct;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) continue;
                    Material wool = woolFromBlock(block.getRelative(dx, dy, dz));
                    if (wool != null) return wool;
                }
            }
        }
        return null;
    }

    private boolean isTeamScoreComponent(Block block) {
        if (block == null) return false;
        return switch (block.getType()) {
            case LEVER, REDSTONE_WIRE, REPEATER, COMPARATOR, REDSTONE_TORCH, REDSTONE_WALL_TORCH -> true;
            default -> false;
        };
    }

    private boolean isActiveTeamScoreComponent(Block block) {
        if (!isTeamScoreComponent(block)) return false;
        return switch (block.getType()) {
            case REDSTONE_TORCH, REDSTONE_WALL_TORCH -> true;
            case REDSTONE_WIRE -> block.getBlockData() instanceof RedstoneWire wire && wire.getPower() > 0;
            default -> {
                if (block.getBlockData() instanceof AnaloguePowerable analogue) {
                    yield analogue.getPower() > 0;
                }
                yield block.getBlockData() instanceof Powerable powerable && powerable.isPowered();
            }
        };
    }

    private String formatTeamScoreLine(TeamScoreEntry entry) {
        return woolChatColor(entry.wool()) + woolLabel(entry.wool()) + ChatColor.WHITE + ": " + entry.points();
    }

    private String formatPvpTeamLabel(Material wool) {
        if (!isWool(wool)) return ChatColor.GRAY + "-";
        return switch (wool) {
            case WHITE_WOOL -> ChatColor.WHITE + "Wei\u00df";
            case ORANGE_WOOL -> ChatColor.GOLD + "Orange";
            case MAGENTA_WOOL -> ChatColor.LIGHT_PURPLE + "Magenta";
            case LIGHT_BLUE_WOOL -> ChatColor.AQUA + "Hellblau";
            case YELLOW_WOOL -> ChatColor.YELLOW + "Gelb";
            case LIME_WOOL -> ChatColor.GREEN + "Lime";
            case PINK_WOOL -> ChatColor.LIGHT_PURPLE + "Pink";
            case GRAY_WOOL -> ChatColor.DARK_GRAY + "Grau";
            case LIGHT_GRAY_WOOL -> ChatColor.GRAY + "Hellgrau";
            case CYAN_WOOL -> ChatColor.DARK_AQUA + "Cyan";
            case PURPLE_WOOL -> ChatColor.DARK_PURPLE + "Lila";
            case BLUE_WOOL -> ChatColor.BLUE + "Blau";
            case BROWN_WOOL -> ChatColor.GOLD + "Braun";
            case GREEN_WOOL -> ChatColor.DARK_GREEN + "Gr\u00fcn";
            case RED_WOOL -> ChatColor.RED + "Rot";
            case BLACK_WOOL -> ChatColor.BLACK + "Schwarz";
            default -> ChatColor.GRAY + wool.name().replace("_WOOL", "");
        };
    }

    private ChatColor woolChatColor(Material wool) {
        if (!isWool(wool)) return ChatColor.GRAY;
        return switch (wool) {
            case WHITE_WOOL -> ChatColor.WHITE;
            case ORANGE_WOOL -> ChatColor.GOLD;
            case MAGENTA_WOOL, PINK_WOOL -> ChatColor.LIGHT_PURPLE;
            case LIGHT_BLUE_WOOL -> ChatColor.AQUA;
            case YELLOW_WOOL -> ChatColor.YELLOW;
            case LIME_WOOL -> ChatColor.GREEN;
            case GRAY_WOOL -> ChatColor.DARK_GRAY;
            case LIGHT_GRAY_WOOL -> ChatColor.GRAY;
            case CYAN_WOOL -> ChatColor.DARK_AQUA;
            case PURPLE_WOOL -> ChatColor.DARK_PURPLE;
            case BLUE_WOOL -> ChatColor.BLUE;
            case BROWN_WOOL -> ChatColor.GOLD;
            case GREEN_WOOL -> ChatColor.DARK_GREEN;
            case RED_WOOL -> ChatColor.RED;
            case BLACK_WOOL -> ChatColor.BLACK;
            default -> ChatColor.GRAY;
        };
    }

    private String woolLabel(Material wool) {
        if (!isWool(wool)) return "-";
        return switch (wool) {
            case WHITE_WOOL -> "Wei\u00df";
            case ORANGE_WOOL -> "Orange";
            case MAGENTA_WOOL -> "Magenta";
            case LIGHT_BLUE_WOOL -> "Hellblau";
            case YELLOW_WOOL -> "Gelb";
            case LIME_WOOL -> "Lime";
            case PINK_WOOL -> "Pink";
            case GRAY_WOOL -> "Grau";
            case LIGHT_GRAY_WOOL -> "Hellgrau";
            case CYAN_WOOL -> "Cyan";
            case PURPLE_WOOL -> "Lila";
            case BLUE_WOOL -> "Blau";
            case BROWN_WOOL -> "Braun";
            case GREEN_WOOL -> "Gr\u00fcn";
            case RED_WOOL -> "Rot";
            case BLACK_WOOL -> "Schwarz";
            default -> wool.name().replace("_WOOL", "");
        };
    }

    private void clearPvpScoreboard(Player player) {
        if (Bukkit.getScoreboardManager() == null) return;
        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard == null) return;
        Objective parcelPvp = scoreboard.getObjective("parcelpvp");
        Objective parcelGames = scoreboard.getObjective("parcelgames");
        Objective parcelPve = scoreboard.getObjective("parcelpve");
        if (parcelPvp == null && parcelGames == null && parcelPve == null) return;
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private boolean shouldRefreshCombatScoreboard(UUID playerId, String zoneKey) {
        long now = System.currentTimeMillis();
        CombatScoreboardState state = combatScoreboardStates.get(playerId);
        if (state == null || zoneKey == null || !zoneKey.equals(state.zoneKey()) || now - state.refreshedAt() >= COMBAT_SCOREBOARD_REFRESH_MS) {
            combatScoreboardStates.put(playerId, new CombatScoreboardState(zoneKey, now));
            return true;
        }
        return false;
    }

    private void markCombatScoreboardRefreshed(UUID playerId, String zoneKey) {
        if (playerId == null) return;
        combatScoreboardStates.put(playerId, new CombatScoreboardState(zoneKey, System.currentTimeMillis()));
    }

    private void refreshParcelPvpScoreboards(IslandData island, ParcelData parcel) {
        String parcelKey = islandService.getParcelPvpKey(island, parcel);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!parcelKey.equals(parcelPvpStates.get(online.getUniqueId()))) continue;
            showParcelCombatScoreboard(online, island, parcel);
        }
    }

    private void broadcastParcelPvpMessage(String message) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (skyWorldService.isSkyCityWorld(online.getWorld())) {
                online.sendMessage(message);
            }
        }
    }

    private Player resolveDamagingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private Player resolveKiller(CombatTag tag, String expectedParcelKey) {
        if (tag == null || expectedParcelKey == null) return null;
        if (!expectedParcelKey.equals(tag.parcelKey())) return null;
        if (System.currentTimeMillis() - tag.createdAt() > PVP_KILL_TIMEOUT_MS) return null;
        Player killer = Bukkit.getPlayer(tag.attackerId());
        return killer != null && killer.isOnline() ? killer : null;
    }

    private ItemStack transferRandomInventoryItem(Player victim, Player killer) {
        PlayerInventory inventory = victim.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        java.util.List<Integer> filledSlots = new java.util.ArrayList<>();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && item.getType() != Material.AIR) {
                filledSlots.add(slot);
            }
        }
        if (filledSlots.isEmpty()) return null;
        int chosenSlot = filledSlots.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(filledSlots.size()));
        ItemStack chosen = contents[chosenSlot];
        ItemStack reward = chosen.clone();
        reward.setAmount(1);
        if (chosen.getAmount() <= 1) {
            inventory.setItem(chosenSlot, null);
        } else {
            chosen.setAmount(chosen.getAmount() - 1);
            inventory.setItem(chosenSlot, chosen);
        }
        java.util.Map<Integer, ItemStack> overflow = killer.getInventory().addItem(reward);
        for (ItemStack item : overflow.values()) {
            killer.getWorld().dropItemNaturally(killer.getLocation(), item);
        }
        return reward;
    }

    private String formatItemName(ItemStack item) {
        if (item == null) return "nichts";
        if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
        return item.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    private void sendIslandCreateHint(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Du hast noch keine Insel und bist auf keiner Insel Member.");
        player.sendMessage(ChatColor.GRAY + "Nutze " + ChatColor.AQUA + "/is create" + ChatColor.GRAY + " zum Erstellen.");
        player.sendMessage(ChatColor.GRAY + "Danach kommst du mit " + ChatColor.AQUA + "/is" + ChatColor.GRAY + " oder " + ChatColor.AQUA + "/is home" + ChatColor.GRAY + " direkt zur Insel.");
        player.sendMessage(ChatColor.GRAY + "Oder klicke hier:");
        TextComponent clickable = new TextComponent(ChatColor.GOLD + "" + ChatColor.BOLD + "[Insel erstellen]");
        clickable.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/is create"));
        clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.YELLOW + "Klick zum Erstellen deiner Insel").create()));
        player.spigot().sendMessage(clickable);
    }

    private void tickIslandActionbar() {
        double serverTps = getServerTps();
        Map<UUID, Integer> islandLoadCache = new HashMap<>();
        java.util.Set<String> activeCheckpointDisplays = new java.util.HashSet<>();
        java.util.Set<String> activeJumpPadDisplays = new java.util.HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline()) continue;
            if (!skyWorldService.isSkyCityWorld(player.getWorld())) {
                applyIslandTimeMode(player, null);
                applyIslandWeather(player, null);
                applyIslandNightVision(player, null);
                removeIslandBossBar(player);
                removeChunkEffectBossBar(player);
                removeParcelCountdownBossBar(player);
                parcelCountdownTitleStates.remove(player.getUniqueId());
                continue;
            }
            updateParcelPvpState(player, player.getLocation());
            updateParcelPvpCompass(player, player.getLocation());
            updateParcelPvpTeam(player, player.getLocation());
            updateParcelCountdownState(player, player.getLocation());
            updateParcelPveState(player, player.getLocation());
            if (islandService.isInSpawnPlot(player.getLocation())) {
                applyIslandTimeMode(player, null);
                applyIslandWeather(player, null);
                applyIslandNightVision(player, null);
                String timeSegment = ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + getWorldTimeIcon(player.getWorld());
                showIslandBossBar(player, ChatColor.GOLD + "Spawn"
                        + timeSegment
                        + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "TPS " + formatTps(serverTps)
                        + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "SkyCity");
                removeChunkEffectBossBar(player);
                continue;
            }
            IslandData island = islandService.getIslandAt(player.getLocation());
            if (island == null) {
                applyIslandTimeMode(player, null);
                applyIslandWeather(player, null);
                applyIslandNightVision(player, null);
                removeIslandBossBar(player);
                removeChunkEffectBossBar(player);
                continue;
            }
            applyIslandTimeMode(player, island);
            applyIslandWeather(player, island);
            applyIslandNightVision(player, island);
            String title = islandService.getIslandTitleDisplay(island);
            String timeIcon = getWorldTimeIcon(player.getWorld());
            String timeSegment = timeIcon.isEmpty() ? "" : ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + timeIcon;
            int islandLoad = islandLoadCache.computeIfAbsent(island.getOwner(), id -> islandService.getIslandLoadPercent(island));
            String tpsSegment = ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "TPS " + formatTps(serverTps);
            String loadSegment = ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "Last " + islandLoad + "%";
            showIslandBossBar(player, ChatColor.GOLD + title + timeSegment + tpsSegment + loadSegment);
            activeCheckpointDisplays.addAll(spawnNearbyLinkedCheckpointParticles(player, island, activeJumpPadDisplays));
            int relChunkX = islandService.relativeChunkX(island, player.getLocation().getChunk().getX());
            int relChunkZ = islandService.relativeChunkZ(island, player.getLocation().getChunk().getZ());
            int displayChunkX = islandService.displayChunkX(relChunkX);
            int displayChunkZ = islandService.displayChunkZ(relChunkZ);
            int activeTier = islandService.getGrowthBoostTier(island, relChunkX, relChunkZ);
            long remainingMs = islandService.getGrowthBoostRemainingMillis(island, relChunkX, relChunkZ);
            long fullDurationMs = islandService.getGrowthBoostDurationMillis(activeTier);
            if (activeTier <= 0) {
                removeChunkEffectBossBar(player);
                continue;
            }
            String effectTitle = ChatColor.GREEN + "Wachstumsschub"
                    + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "Stufe " + activeTier
                    + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "Chunk " + displayChunkX + ":" + displayChunkZ
                    + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "Zeit " + formatMillisShort(remainingMs);
            double progress = fullDurationMs > 0L ? Math.max(0.0, Math.min(1.0, (double) remainingMs / (double) fullDurationMs)) : 1.0;
            showChunkEffectBossBar(player, effectTitle, BarColor.GREEN, progress);
        }
        removeStaleCheckpointDisplays(activeCheckpointDisplays);
        removeStaleJumpPadDisplays(activeJumpPadDisplays);
    }

    private double getServerTps() {
        try {
            Object raw = Bukkit.getServer().getClass().getMethod("getTPS").invoke(Bukkit.getServer());
            if (raw instanceof double[] tps && tps.length > 0) {
                double value = tps[0];
                if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                    return Math.max(0.0, Math.min(20.0, value));
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return 20.0;
    }

    private String formatTps(double tps) {
        return String.format(java.util.Locale.US, "%.1f", Math.max(0.0, Math.min(20.0, tps)));
    }

    private String getWorldTimeIcon(org.bukkit.World world) {
        if (world == null) return "";
        long time = world.getTime() % 24000L;
        return time >= 13000L && time < 23000L ? "\u263e" : "\u2600";
    }

    private void applyIslandTimeMode(Player player, IslandData island) {
        if (player == null) return;
        IslandService.IslandTimeMode mode = islandService.getIslandTimeMode(island);
        if (island != null) {
            ParcelData parcel = islandService.getParcelAt(island, player.getLocation());
            if (parcel != null) {
                mode = islandService.getParcelTimeMode(parcel);
            }
        }
        switch (mode) {
            case DAY -> player.setPlayerTime(1000L, false);
            case SUNSET -> player.setPlayerTime(13000L, false);
            case MIDNIGHT -> player.setPlayerTime(18000L, false);
            case NORMAL -> player.resetPlayerTime();
        }
    }

    private void applyIslandWeather(Player player, IslandData island) {
        if (player == null) return;
        if (island == null) {
            player.resetPlayerWeather();
            return;
        }
        IslandService.IslandWeatherMode mode = islandService.getIslandWeatherMode(island);
        ParcelData parcel = islandService.getParcelAt(island, player.getLocation());
        if (parcel != null) {
            mode = islandService.getParcelWeatherMode(parcel);
        }
        switch (mode) {
            case CLEAR -> player.setPlayerWeather(org.bukkit.WeatherType.CLEAR);
            case RAIN, THUNDER -> player.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL);
            case NORMAL -> player.resetPlayerWeather();
        }
    }

    private void showIslandBossBar(Player player, String title) {
        BossBar bar = islandBossBars.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar created = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SOLID);
            created.setProgress(1.0);
            created.addPlayer(player);
            created.setVisible(true);
            return created;
        });
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
        bar.setTitle(title);
        bar.setColor(BarColor.YELLOW);
        bar.setStyle(BarStyle.SOLID);
        bar.setProgress(1.0);
        bar.setVisible(true);
    }

    private void showChunkEffectBossBar(Player player, String title, BarColor color, double progress) {
        BossBar bar = chunkEffectBossBars.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar created = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
            created.setProgress(1.0);
            created.addPlayer(player);
            created.setVisible(true);
            return created;
        });
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
        bar.setTitle(title);
        bar.setColor(color == null ? BarColor.GREEN : color);
        bar.setStyle(BarStyle.SOLID);
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        bar.setVisible(true);
    }

    private void showParcelCountdownBossBar(Player player, String title, BarColor color, double progress) {
        BossBar bar = parcelCountdownBossBars.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar created = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
            created.setProgress(1.0);
            created.addPlayer(player);
            created.setVisible(true);
            return created;
        });
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
        bar.setTitle(title);
        bar.setColor(color == null ? BarColor.BLUE : color);
        bar.setStyle(BarStyle.SOLID);
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        bar.setVisible(true);
    }

    private String formatMillisShort(long ms) {
        if (ms <= 0L) return "0s";
        long totalSeconds = ms / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) return hours + "h " + minutes + "m";
        if (minutes > 0L) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    private void removeIslandBossBar(Player player) {
        if (player == null) return;
        BossBar bar = islandBossBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
            bar.setVisible(false);
        }
    }

    private void applyIslandNightVision(Player player, IslandData island) {
        if (player == null) return;
        boolean shouldHaveNightVision = false;
        if (island != null && skyWorldService.isSkyCityWorld(player.getWorld()) && !islandService.isInSpawnPlot(player.getLocation())) {
            ParcelData parcel = islandService.getParcelAt(island, player.getLocation());
            if (parcel != null && islandService.isParcelNightVisionEnabled(parcel)) {
                shouldHaveNightVision = true;
            } else {
                int relChunkX = islandService.relativeChunkX(island, player.getLocation().getChunk().getX());
                int relChunkZ = islandService.relativeChunkZ(island, player.getLocation().getChunk().getZ());
                shouldHaveNightVision = islandService.hasNightVision(island, relChunkX, relChunkZ);
            }
        }
        if (shouldHaveNightVision) {
            PotionEffect active = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
            if (active == null) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, SKYCITY_NIGHT_VISION_DURATION_TICKS, 0, false, false, false));
                skyCityNightVisionApplied.put(player.getUniqueId(), true);
                return;
            }
            if (isSkyCityNightVisionEffect(active)) {
                skyCityNightVisionApplied.put(player.getUniqueId(), true);
            } else {
                skyCityNightVisionApplied.remove(player.getUniqueId());
            }
            return;
        }
        clearSkyCityNightVision(player);
    }

    private void clearSkyCityNightVision(Player player) {
        if (player == null) return;
        if (!Boolean.TRUE.equals(skyCityNightVisionApplied.remove(player.getUniqueId()))) return;
        PotionEffect active = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (isSkyCityNightVisionEffect(active)) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    private boolean isSkyCityNightVisionEffect(PotionEffect effect) {
        return effect != null
                && effect.getType().equals(PotionEffectType.NIGHT_VISION)
                && effect.getAmplifier() == 0
                && effect.getDuration() >= SKYCITY_NIGHT_VISION_MIN_DURATION_TICKS;
    }

    private boolean consumedItemGrantsNightVision(ItemStack item) {
        if (item == null) return false;
        if (item.getItemMeta() instanceof PotionMeta potionMeta) {
            if (potionMeta.getBasePotionType() == PotionType.NIGHT_VISION) {
                return true;
            }
            for (PotionEffect effect : potionMeta.getCustomEffects()) {
                if (effect.getType().equals(PotionEffectType.NIGHT_VISION)) {
                    return true;
                }
            }
        }
        if (item.getItemMeta() instanceof SuspiciousStewMeta stewMeta) {
            for (PotionEffect effect : stewMeta.getCustomEffects()) {
                if (effect.getType().equals(PotionEffectType.NIGHT_VISION)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void removeChunkEffectBossBar(Player player) {
        if (player == null) return;
        BossBar bar = chunkEffectBossBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
            bar.setVisible(false);
        }
    }

    private void removeParcelCountdownBossBar(Player player) {
        if (player == null) return;
        BossBar bar = parcelCountdownBossBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
            bar.setVisible(false);
        }
    }

    private void updateIslandPresenceMessage(Player player, boolean forceRefreshStateOnly) {
        updateIslandPresenceMessage(player, player == null ? null : player.getLocation(), forceRefreshStateOnly);
    }

    private void updateIslandPresenceMessage(Player player, Location at, boolean forceRefreshStateOnly) {
        if (player == null || at == null || at.getWorld() == null || !player.isOnline() || !skyWorldService.isSkyCityWorld(at.getWorld())) return;
        String prev = islandPresenceState.get(player.getUniqueId());
        String current = computeIslandPresenceState(at);
        if (current == null) {
            islandPresenceState.remove(player.getUniqueId());
            return;
        }
        if (prev == null) {
            islandPresenceState.put(player.getUniqueId(), current);
            if (!forceRefreshStateOnly && current.startsWith("island:")) {
                IslandData island = islandService.getIslandAt(at);
                if (island != null) {
                    player.sendMessage(ChatColor.GOLD + "Du betrittst die Insel " + ChatColor.YELLOW + islandService.getIslandTitleDisplay(island));
                }
            }
            return;
        }
        if (prev.equals(current)) return;
        islandPresenceState.put(player.getUniqueId(), current);
        if (forceRefreshStateOnly) return;

        if (prev.startsWith("island:") && !current.startsWith("island:")) {
            player.sendMessage(ChatColor.GRAY + "Du verl\u00e4sst die Insel.");
            return;
        }
        if (!prev.startsWith("island:") && current.startsWith("island:")) {
            IslandData island = islandService.getIslandAt(at);
            if (island != null) {
                player.sendMessage(ChatColor.GOLD + "Du betrittst die Insel " + ChatColor.YELLOW + islandService.getIslandTitleDisplay(island));
            }
            return;
        }
        if (prev.startsWith("island:") && current.startsWith("island:")) {
            IslandData island = islandService.getIslandAt(at);
            player.sendMessage(ChatColor.GRAY + "Du verl\u00e4sst die Insel.");
            if (island != null) {
                player.sendMessage(ChatColor.GOLD + "Du betrittst die Insel " + ChatColor.YELLOW + islandService.getIslandTitleDisplay(island));
            }
        }
    }

    private String computeIslandPresenceState(Location location) {
        if (location == null || location.getWorld() == null || !skyWorldService.isSkyCityWorld(location.getWorld())) return null;
        if (islandService.isInSpawnPlot(location)) return "spawn";
        IslandData island = islandService.getIslandAt(location);
        if (island == null) return "skycity";
        return "island:" + island.getOwner();
    }

    private boolean isAllowedExternalWorld(String worldName) {
        if (worldName == null) return false;
        String n = worldName.toLowerCase();
        return n.equals("farm_overworld") || n.equals("farm_nether") || n.equals("farm_end") || n.startsWith("farm_");
    }

    private boolean isLikelyFarmweltPortal(PortalCreateEvent event) {
        for (BlockState state : event.getBlocks()) {
            if (state == null || state.getLocation() == null) continue;
            if (isLikelyFarmweltPortal(state.getLocation())) return true;
        }
        return false;
    }

    private boolean isLikelyFarmweltPortal(Location origin) {
        if (origin == null || origin.getWorld() == null) return false;
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        for (int x = ox - 3; x <= ox + 3; x++) {
            for (int y = oy - 3; y <= oy + 4; y++) {
                for (int z = oz - 3; z <= oz + 3; z++) {
                    Material type = origin.getWorld().getBlockAt(x, y, z).getType();
                    if (isFarmweltFrameMaterial(type)) return true;
                }
            }
        }
        return false;
    }

    private boolean isFarmweltFrameMaterial(Material type) {
        if (type == null) return false;
        return switch (type) {
            case POLISHED_ANDESITE, POLISHED_DIORITE, POLISHED_GRANITE -> true;
            default -> false;
        };
    }
}




