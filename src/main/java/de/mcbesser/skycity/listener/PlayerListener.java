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
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener {
    private static final long PVP_KILL_TIMEOUT_MS = 15_000L;
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
    private final Map<UUID, CombatTag> parcelPvpCombatTags = new HashMap<>();
    private final Map<UUID, String> islandPresenceState = new HashMap<>();
    private final Map<UUID, BossBar> islandBossBars = new HashMap<>();
    private final int islandActionbarTaskId;

    private record CombatTag(UUID attackerId, String parcelKey, long createdAt) { }

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
        removeIslandBossBar(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
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
            islandPresenceState.remove(event.getPlayer().getUniqueId());
            removeIslandBossBar(event.getPlayer());
        } else {
            updateParcelPvpState(event.getPlayer(), event.getPlayer().getLocation());
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
            return;
        }

        boolean changedBlock = event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ();
        if (changedBlock) {
            handleParcelBanEntryCountdown(event.getPlayer(), event.getTo());
            handleParcelPvpWhitelistCountdown(event.getPlayer(), event.getTo());
            updateParcelPvpState(event.getPlayer(), event.getTo());
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
            return;
        }
        updateParcelPvpState(event.getPlayer(), event.getTo());
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
        player.sendMessage(ChatColor.RED + "Du bist auf diesem GrundstÃ¼ck gebannt.");
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
                    live.sendMessage(ChatColor.GREEN + "Du hast das gebannte GrundstÃ¼ck verlassen.");
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
                    live.sendMessage(ChatColor.RED + "Du wurdest vom GrundstÃ¼ck entfernt.");
                    stopParcelBanCountdown(playerId);
                    return;
                }

                live.sendMessage(ChatColor.YELLOW + "Verlasse das GrundstÃ¼ck in " + secondsLeft + "...");
            }
        }, 20L, 20L);
        parcelBanCountdownTasks.put(playerId, taskId);
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
        player.sendMessage(ChatColor.RED + "Du bist nicht auf der PvP-Whitelist dieses GrundstÃ¼cks.");
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
            player.sendMessage(ChatColor.GREEN + "Du verlÃ¤sst die PvP-Zone.");
            broadcastParcelPvpMessage(ChatColor.GRAY + player.getName() + " hat die PvP-Zone verlassen.");
        }
        if (nextKey == null || nextKey.equals(previousKey)) {
            return;
        }
        parcelPvpStates.put(playerId, nextKey);
        islandService.grantParcelPvpConsent(playerId, island, parcel);
        showParcelPvpScoreboard(player, island, parcel);
        String parcelName = islandService.getParcelDisplayName(parcel);
        player.sendMessage(ChatColor.RED + "Du trittst PvP auf dem GrundstÃ¼ck " + parcelName + " bei.");
        player.sendMessage(ChatColor.YELLOW + "Du kannst jetzt kÃ¤mpfen und angegriffen werden, solange du in der Zone bist.");
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
        if (Bukkit.getScoreboardManager() != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
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
        player.sendMessage(ChatColor.YELLOW + "Du hast noch keine Insel und bist auf keiner Insel Mitglied.");
        player.sendMessage(ChatColor.GRAY + "Nutze /is create oder klicke hier:");
        TextComponent clickable = new TextComponent(ChatColor.GOLD + "" + ChatColor.BOLD + "[Insel erstellen]");
        clickable.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/is create"));
        clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.YELLOW + "Klick zum Erstellen deiner Insel").create()));
        player.spigot().sendMessage(clickable);
    }

    private void tickIslandActionbar() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline()) continue;
            if (!skyWorldService.isSkyCityWorld(player.getWorld())) {
                applyIslandTimeMode(player, null);
                removeIslandBossBar(player);
                continue;
            }
            if (islandService.isInSpawnPlot(player.getLocation())) {
                applyIslandTimeMode(player, null);
                showIslandBossBar(player, ChatColor.GOLD + "Spawn" + ChatColor.GRAY + " | " + ChatColor.WHITE + "SkyCity");
                continue;
            }
            IslandData island = islandService.getIslandAt(player.getLocation());
            if (island == null) {
                applyIslandTimeMode(player, null);
                removeIslandBossBar(player);
                continue;
            }
            applyIslandTimeMode(player, island);
            String[] parts = islandService.getIslandBossBarText(island).split("\\s*\\|\\s*", 2);
            String title = parts.length > 0 ? parts[0] : islandService.getIslandTitleDisplay(island);
            String master = parts.length > 1 ? parts[1] : islandService.getIslandMasterDisplay(island);
            showIslandBossBar(player, ChatColor.GOLD + title + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + master);
        }
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

    private void removeIslandBossBar(Player player) {
        if (player == null) return;
        BossBar bar = islandBossBars.remove(player.getUniqueId());
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
            player.sendMessage(ChatColor.GRAY + "Du verlÃ¤sst die Insel.");
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
            player.sendMessage(ChatColor.GRAY + "Du verlÃ¤sst die Insel.");
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




