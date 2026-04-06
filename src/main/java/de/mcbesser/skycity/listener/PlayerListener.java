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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Transformation;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener {
    private static final long PVP_KILL_TIMEOUT_MS = 15_000L;
    private static final long LAVA_RESCUE_PROTECTION_MS = 2_000L;
    private static final String CHECKPOINT_HOLO_PREFIX = "skycity_checkpoint_holo_";
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
    private final int islandActionbarTaskId;

    private record CombatTag(UUID attackerId, String parcelKey, long createdAt) { }
    private record CheckpointMarker(Material woolType, Material plateType, Location plateLocation) { }

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
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
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
            clearParcelPveState(event.getPlayer().getUniqueId());
            return;
        }
        updateCheckpoint(event.getPlayer(), event.getTo());
        if (tryHandleLavaRescue(event.getPlayer(), event.getTo())) return;

        boolean changedBlock = event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ();
        if (changedBlock && tryHandleLinkedCheckpointTeleport(event.getPlayer(), event.getTo())) return;
        if (changedBlock) {
            handleParcelBanEntryCountdown(event.getPlayer(), event.getTo());
            handleParcelPvpWhitelistCountdown(event.getPlayer(), event.getTo());
            updateParcelPvpState(event.getPlayer(), event.getTo());
            updateParcelPveState(event.getPlayer(), event.getTo());
        }

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
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || !skyWorldService.isSkyCityWorld(event.getTo().getWorld())) {
            clearParcelPvpState(event.getPlayer().getUniqueId());
            clearParcelPveState(event.getPlayer().getUniqueId());
            return;
        }
        tryHandleLavaRescue(event.getPlayer(), event.getTo());
        updateParcelPvpState(event.getPlayer(), event.getTo());
        updateParcelPveState(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnvironmentDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!skyWorldService.isSkyCityWorld(player.getWorld())) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.LAVA
                && cause != EntityDamageEvent.DamageCause.FIRE
                && cause != EntityDamageEvent.DamageCause.FIRE_TICK) {
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
            broadcastParcelPvpMessage(ChatColor.YELLOW + victim.getName() + ChatColor.GRAY + " ist in der PvP-Zone " + parcelName + " gestorben und beh\u00e4lt alles.");
            return;
        }

        ItemStack transferred = transferRandomInventoryItem(victim, killer);
        islandService.recordParcelPvpKill(island, parcel, killer.getUniqueId());
        refreshParcelPvpScoreboards(island, parcel);
        if (transferred == null) {
            broadcastParcelPvpMessage(ChatColor.RED + victim.getName() + ChatColor.GRAY + " wurde von " + ChatColor.GOLD + killer.getName() + ChatColor.GRAY + " get\u00f6tet. Kein Item wurde \u00fcbertragen.");
            return;
        }
        broadcastParcelPvpMessage(ChatColor.RED + victim.getName() + ChatColor.GRAY + " wurde von " + ChatColor.GOLD + killer.getName() + ChatColor.GRAY + " get\u00f6tet. \u00dcbertragen: " + ChatColor.AQUA + formatItemName(transferred) + ChatColor.GRAY + ".");
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

    private boolean tryHandleLinkedCheckpointTeleport(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) return false;
        if (isLinkedCheckpointTeleportProtected(player.getUniqueId())) return false;
        CheckpointMarker marker = findCheckpointMarker(location);
        if (marker == null) return false;
        IslandData island = islandService.getIslandAt(location);
        if (island == null) return false;
        ParcelData regionParcel = islandService.getParcelAt(island, location);
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

    private java.util.List<Location> findMatchingCheckpointPlates(IslandData island, ParcelData regionParcel, Material woolType, Material plateType) {
        java.util.List<Location> matches = new java.util.ArrayList<>();
        if (island == null || woolType == null || plateType == null) return matches;
        for (String chunkKey : island.getUnlockedChunks()) {
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
            if (skyWorldService.getWorld() == null) continue;
            int chunkX = islandService.plotMinChunkX(island.getGridX()) + relChunkX;
            int chunkZ = islandService.plotMinChunkZ(island.getGridZ()) + relChunkZ;
            org.bukkit.Chunk chunk = skyWorldService.getWorld().getChunkAt(chunkX, chunkZ);
            int minY = chunk.getWorld().getMinHeight();
            int maxY = chunk.getWorld().getMaxHeight();
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int y = minY; y < maxY; y++) {
                        Block block = chunk.getBlock(localX, y, localZ);
                        if (block.getType() != plateType) continue;
                        if (hasLavaDirectlyAbove(block)) continue;
                        ParcelData candidateParcel = islandService.getParcelAt(island, block.getLocation());
                        if (regionParcel != null) {
                            if (candidateParcel == null || candidateParcel != regionParcel) continue;
                        } else if (candidateParcel != null) {
                            continue;
                        }
                        CheckpointMarker marker = checkpointMarkerFromPlate(block);
                        if (marker == null || marker.woolType() != woolType) continue;
                        matches.add(marker.plateLocation());
                        if (matches.size() > 2) return matches;
                    }
                }
            }
        }
        return matches;
    }

    private java.util.List<Location> findMatchingCheckpointPlatesByWool(IslandData island, ParcelData regionParcel, Material woolType) {
        java.util.List<Location> matches = new java.util.ArrayList<>();
        if (island == null || woolType == null) return matches;
        for (String chunkKey : island.getUnlockedChunks()) {
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
            if (skyWorldService.getWorld() == null) continue;
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
                        ParcelData candidateParcel = islandService.getParcelAt(island, block.getLocation());
                        if (regionParcel != null) {
                            if (candidateParcel == null || candidateParcel != regionParcel) continue;
                        } else if (candidateParcel != null) {
                            continue;
                        }
                        CheckpointMarker marker = checkpointMarkerFromPlate(block);
                        if (marker == null || marker.woolType() != woolType) continue;
                        matches.add(marker.plateLocation());
                        if (matches.size() > 1) return matches;
                    }
                }
            }
        }
        return matches;
    }

    private String checkpointKey(Material woolType, Material plateType) {
        return woolType.name() + "|" + plateType.name();
    }

    private Material findWoolMarker(Location location) {
        if (location == null || location.getWorld() == null) return null;
        Block feet = location.getBlock();
        Material wool = woolFromBlock(feet);
        if (wool != null) return wool;
        wool = woolFromBlock(feet.getRelative(0, -1, 0));
        if (wool != null) return wool;
        return woolFromBlock(feet.getRelative(0, -2, 0));
    }

    private Material woolFromBlock(Block block) {
        if (block == null) return null;
        return isWool(block.getType()) ? block.getType() : null;
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

    private java.util.Set<String> spawnNearbyLinkedCheckpointParticles(Player player, IslandData island) {
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
        for (Entity entity : location.getWorld().getNearbyEntities(displayLocation, 0.4, 0.6, 0.4)) {
            if (entity instanceof ArmorStand stand && stand.getScoreboardTags().contains(tag)) {
                stand.remove();
                continue;
            }
            if (entity instanceof ItemDisplay display && display.getScoreboardTags().contains(tag)) {
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
    }

    private void removeStaleCheckpointDisplays(java.util.Set<String> activeTags) {
        if (skyWorldService.getWorld() == null) return;
        for (Entity entity : skyWorldService.getWorld().getEntities()) {
            if (!(entity instanceof ItemDisplay) && !(entity instanceof ArmorStand)) continue;
            for (String tag : entity.getScoreboardTags()) {
                if (!tag.startsWith(CHECKPOINT_HOLO_PREFIX)) continue;
                if (!activeTags.contains(tag)) {
                    entity.remove();
                }
                break;
            }
        }
    }

    private String checkpointHoloTag(Location location) {
        return CHECKPOINT_HOLO_PREFIX
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
            if (islandService.isIslandReady(playerId)) {
                stopPreparationStatusMessages(playerId);
                return;
            }

            int queuePos = islandService.getIslandCreationQueuePosition(playerId);
            if (queuePos > 0) {
                live.sendMessage(ChatColor.YELLOW + "Deine Insel wird vorbereitet... Warteschlange Platz " + queuePos + ".");
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
        if (parcel == null || !parcel.isPvpEnabled() || islandService.canEnterParcelPvp(island, parcel, playerId)) {
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
                        && nowParcel.isPvpEnabled()
                        && parcelKey != null
                        && parcelKey.equals(islandService.getParcelPvpKey(nowIsland, nowParcel))
                        && !islandService.canEnterParcelPvp(nowIsland, nowParcel, playerId);

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
        String nextKey = parcel != null && parcel.isPvpEnabled() ? islandService.getParcelPvpKey(island, parcel) : null;
        String previousKey = parcelPvpStates.get(playerId);
        if (parcel != null && parcel.isPvpEnabled() && !islandService.canEnterParcelPvp(island, parcel, playerId)) {
            islandService.clearParcelPvpConsent(playerId);
            clearPvpScoreboard(player);
            return;
        }
        if (previousKey != null && !previousKey.equals(nextKey)) {
            parcelPvpStates.remove(playerId);
            islandService.clearParcelPvpConsent(playerId);
            clearPvpScoreboard(player);
            player.sendMessage(ChatColor.GREEN + "Du verl\u00e4sst die PvP-Zone.");
            broadcastParcelPvpMessage(ChatColor.GRAY + player.getName() + " hat die PvP-Zone verlassen.");
        }
        if (nextKey == null || nextKey.equals(previousKey)) {
            return;
        }
        parcelPvpStates.put(playerId, nextKey);
        islandService.grantParcelPvpConsent(playerId, island, parcel);
        showParcelPvpScoreboard(player, island, parcel);
        String parcelName = islandService.getParcelDisplayName(parcel);
        player.sendMessage(ChatColor.RED + "Du trittst PvP auf dem Grundst\u00fcck " + parcelName + " bei.");
        player.sendMessage(ChatColor.YELLOW + "Du kannst jetzt k\u00e4mpfen und angegriffen werden, solange du in der Zone bist.");
        broadcastParcelPvpMessage(ChatColor.GOLD + player.getName() + ChatColor.GRAY + " ist der PvP-Zone " + parcelName + " beigetreten.");
    }

    private void clearParcelPvpState(UUID playerId) {
        parcelPvpStates.remove(playerId);
        islandService.clearParcelPvpConsent(playerId);
        parcelPvpCombatTags.remove(playerId);
        stopParcelPvpExitCountdown(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            clearPvpScoreboard(player);
        }
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
            player.sendMessage(ChatColor.GRAY + "Du verlaesst die PvE-Zone.");
        }
        if (nextKey == null) {
            return;
        }
        if (nextKey.equals(previousKey)) {
            showParcelPveScoreboard(player, island, parcel);
            return;
        }
        if (islandService.enterParcelPve(player, island, parcel)) {
            parcelPveStates.put(playerId, nextKey);
            showParcelPveScoreboard(player, island, parcel);
        }
    }

    private void clearParcelPveState(UUID playerId) {
        String previousKey = parcelPveStates.remove(playerId);
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

    private void clearPvpScoreboard(Player player) {
        if (Bukkit.getScoreboardManager() == null) return;
        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard == null) return;
        Objective parcelPvp = scoreboard.getObjective("parcelpvp");
        Objective parcelPve = scoreboard.getObjective("parcelpve");
        if (parcelPvp == null && parcelPve == null) return;
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
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
        player.sendMessage(ChatColor.GRAY + "Nutze /is create oder klicke hier:");
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline()) continue;
            if (!skyWorldService.isSkyCityWorld(player.getWorld())) {
                applyIslandTimeMode(player, null);
                applyIslandNightVision(player, null);
                removeIslandBossBar(player);
                removeChunkEffectBossBar(player);
                continue;
            }
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
            activeCheckpointDisplays.addAll(spawnNearbyLinkedCheckpointParticles(player, island));
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
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 220, 0, false, false, false));
            skyCityNightVisionApplied.put(player.getUniqueId(), true);
            return;
        }
        clearSkyCityNightVision(player);
    }

    private void clearSkyCityNightVision(Player player) {
        if (player == null) return;
        if (!Boolean.TRUE.equals(skyCityNightVisionApplied.remove(player.getUniqueId()))) return;
        PotionEffect active = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (active != null && active.getAmplifier() == 0 && active.getDuration() <= 260) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
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




