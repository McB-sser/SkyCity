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
import org.bukkit.block.BlockState;
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
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Transformation;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.RedstoneWire;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

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
    private final Map<UUID, String> islandPresenceState = new HashMap<>();
    private final Map<UUID, BossBar> islandBossBars = new HashMap<>();
    private final Map<UUID, BossBar> chunkEffectBossBars = new HashMap<>();
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
    private final int islandActionbarTaskId;

    private record CombatTag(UUID attackerId, String parcelKey, long createdAt) { }
    private record CheckpointMarker(Material woolType, Material plateType, Location plateLocation) { }
    private record CheckpointIndexCache(long builtAt, Set<String> unlockedChunks, Map<String, List<Location>> exactMatches, Map<String, List<Location>> woolMatches) { }
    private record TeamScoreCache(long builtAt, List<TeamScoreEntry> entries) { }
    private record TeamScoreEntry(Material wool, int points) { }
    private record CombatScoreboardState(String zoneKey, long refreshedAt) { }
    public PlayerListener(SkyCityPlugin plugin, IslandService islandService, SkyWorldService skyWorldService, CoreService coreService) {
        this.plugin = plugin;
        this.islandService = islandService;
        this.skyWorldService = skyWorldService;
        this.coreService = coreService;
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
            stopParcelBanCountdown(event.getPlayer().getUniqueId());
            stopParcelPvpExitCountdown(event.getPlayer().getUniqueId());
            clearParcelPvpState(event.getPlayer().getUniqueId());
            clearParcelGamesState(event.getPlayer().getUniqueId());
            clearParcelPveState(event.getPlayer().getUniqueId());
            islandPresenceState.remove(event.getPlayer().getUniqueId());
            removeIslandBossBar(event.getPlayer());
            removeChunkEffectBossBar(event.getPlayer());
        } else {
            updateParcelPvpState(event.getPlayer(), event.getPlayer().getLocation());
            updateParcelPveState(event.getPlayer(), event.getPlayer().getLocation());
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
        if (!skyWorldService.isSkyCityWorld(event.getTo().getWorld())) {
            stopParcelBanCountdown(event.getPlayer().getUniqueId());
            stopParcelPvpExitCountdown(event.getPlayer().getUniqueId());
            clearParcelPvpState(event.getPlayer().getUniqueId());
            clearParcelGamesState(event.getPlayer().getUniqueId());
            clearParcelPveState(event.getPlayer().getUniqueId());
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
        if (event.getTo() == null || !skyWorldService.isSkyCityWorld(event.getTo().getWorld())) {
            clearParcelPvpState(event.getPlayer().getUniqueId());
            clearParcelGamesState(event.getPlayer().getUniqueId());
            clearParcelPveState(event.getPlayer().getUniqueId());
            return;
        }
        tryHandleLavaRescue(event.getPlayer(), event.getTo());
        updateParcelPvpState(event.getPlayer(), event.getTo());
        updateParcelPvpCompass(event.getPlayer(), event.getTo());
        updateParcelPvpTeam(event.getPlayer(), event.getTo());
        updateParcelPveState(event.getPlayer(), event.getTo());
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
        if (isWool(feet.getType())) return feet;
        Block below = feet.getRelative(0, -1, 0);
        if (isWool(below.getType())) return below;
        Block belowTwo = feet.getRelative(0, -2, 0);
        if (isSolidWoolBridgeBlock(below) && isWool(belowTwo.getType())) return belowTwo;
        return null;
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

    private void showParcelGamesScoreboard(Player player, IslandData island, ParcelData parcel) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getNewScoreboard();
        if (scoreboard == null) return;
        Objective objective = scoreboard.registerNewObjective("parcelgames", "dummy", ChatColor.AQUA + "Games " + islandService.getParcelDisplayName(parcel));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        int score = 15;
        objective.getScore(ChatColor.WHITE + "GS: " + ChatColor.YELLOW + islandService.getParcelDisplayName(parcel)).setScore(score--);
        Material teamWool = parcelPvpTeamWool.get(player.getUniqueId());
        objective.getScore(ChatColor.WHITE + "Team: " + formatPvpTeamLabel(teamWool)).setScore(score--);
        score = appendParcelTeamPoints(objective, score, island, parcel);
        objective.getScore(ChatColor.WHITE + "Modus: " + ChatColor.AQUA + "Games").setScore(score--);
        objective.getScore(ChatColor.GRAY + "Kein Spielerschaden").setScore(score--);
        player.setScoreboard(scoreboard);
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
        List<TeamScoreEntry> entries = activeScores.entrySet().stream()
                .map(entry -> new TeamScoreEntry(entry.getKey(), entry.getValue()))
                .toList();
        if (parcelKey != null) {
            parcelTeamScoreCache.put(parcelKey, new TeamScoreCache(now, entries));
        }
        return entries;
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
            showParcelPvpScoreboard(online, island, parcel);
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
                applyIslandNightVision(player, null);
                removeIslandBossBar(player);
                removeChunkEffectBossBar(player);
                continue;
            }
            updateParcelPvpState(player, player.getLocation());
            updateParcelPvpCompass(player, player.getLocation());
            updateParcelPvpTeam(player, player.getLocation());
            updateParcelPveState(player, player.getLocation());
            if (islandService.isInSpawnPlot(player.getLocation())) {
                applyIslandTimeMode(player, null);
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
                applyIslandNightVision(player, null);
                removeIslandBossBar(player);
                removeChunkEffectBossBar(player);
                continue;
            }
            applyIslandTimeMode(player, island);
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
        switch (mode) {
            case DAY -> player.setPlayerTime(1000L, false);
            case SUNSET -> player.setPlayerTime(13000L, false);
            case MIDNIGHT -> player.setPlayerTime(18000L, false);
            case NORMAL -> player.resetPlayerTime();
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
            int relChunkX = islandService.relativeChunkX(island, player.getLocation().getChunk().getX());
            int relChunkZ = islandService.relativeChunkZ(island, player.getLocation().getChunk().getZ());
            shouldHaveNightVision = islandService.hasNightVision(island, relChunkX, relChunkZ);
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




