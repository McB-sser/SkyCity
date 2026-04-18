package de.mcbesser.skycity.listener;

import de.mcbesser.skycity.model.IslandData;
import de.mcbesser.skycity.model.IslandPlot;
import de.mcbesser.skycity.model.ParcelData;
import de.mcbesser.skycity.listener.PlayerListener;
import de.mcbesser.skycity.service.CoreService;
import de.mcbesser.skycity.service.IslandService;
import de.mcbesser.skycity.service.ParticlePreviewService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CoreMenuListener implements Listener {
    private static final Set<Integer> CORE_INPUT_SLOTS = Set.of(27, 28, 29, 30, 31, 32, 33, 34, 35);
    private static final String ISLAND_SHOP_PERMISSION_MESSAGE = ChatColor.RED + "Als Member kannst du im Insel-Shop nichts kaufen. Nur Master oder Owner.";

    private final IslandService islandService;
    private final CoreService coreService;
    private final ParticlePreviewService particlePreviewService;
    private final PlayerListener playerListener;
    private final Map<UUID, Integer> claimStatusTasks = new HashMap<>();

    private boolean isDecorativePane(ItemStack item) {
        return item != null && item.getType().name().endsWith("_STAINED_GLASS_PANE");
    }

    public CoreMenuListener(IslandService islandService, CoreService coreService, ParticlePreviewService particlePreviewService, PlayerListener playerListener) {
        this.islandService = islandService;
        this.coreService = coreService;
        this.particlePreviewService = particlePreviewService;
        this.playerListener = playerListener;
    }

    private int accessSettingIndex(int rawSlot) {
        for (int i = 0; i < CoreService.ACCESS_SETTING_SLOTS.length; i++) {
            if (CoreService.ACCESS_SETTING_SLOTS[i] == rawSlot) {
                return i;
            }
        }
        return switch (rawSlot) {
            case 27 -> 18;
            case 28 -> 19;
            case 29 -> 20;
            case 30 -> 21;
            case 31 -> 22;
            default -> -1;
        };
    }

    private boolean toggleAccessSetting(de.mcbesser.skycity.model.AccessSettings settings, int settingIndex) {
        if (settings == null) return false;
        switch (settingIndex) {
            case 0 -> settings.setDoors(!settings.isDoors());
            case 1 -> settings.setTrapdoors(!settings.isTrapdoors());
            case 2 -> settings.setFenceGates(!settings.isFenceGates());
            case 3 -> settings.setButtons(!settings.isButtons());
            case 4 -> settings.setLevers(!settings.isLevers());
            case 5 -> settings.setPressurePlates(!settings.isPressurePlates());
            case 6 -> settings.setContainers(!settings.isContainers());
            case 7 -> settings.setFarmUse(!settings.isFarmUse());
            case 8 -> settings.setRide(!settings.isRide());
            case 9 -> settings.setLadderPlace(!settings.isLadderPlace());
            case 10 -> settings.setTeleport(!settings.isTeleport());
            case 11 -> settings.setLadderBreak(!settings.isLadderBreak());
            case 12 -> settings.setLeavesPlace(!settings.isLeavesPlace());
            case 13 -> settings.setLeavesBreak(!settings.isLeavesBreak());
            case 14 -> settings.setRedstoneUse(!settings.isRedstoneUse());
            case 15 -> settings.setBuckets(!settings.isBuckets());
            case 16 -> settings.setDecorations(!settings.isDecorations());
            case 17 -> settings.setVillagers(!settings.isVillagers());
            case 18 -> settings.setVehicleDestroy(!settings.isVehicleDestroy());
            case 19 -> settings.setSnowPlace(!settings.isSnowPlace());
            case 20 -> settings.setSnowBreak(!settings.isSnowBreak());
            case 21 -> settings.setBannerPlace(!settings.isBannerPlace());
            case 22 -> settings.setBannerBreak(!settings.isBannerBreak());
            default -> { return false; }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof CoreService.CoreInventoryHolder holder) {
            handleCoreMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.UpgradeProgressInventoryHolder holder) {
            handleUpgradeProgressClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.IslandInventoryHolder holder) {
            handleIslandMenuClick(event, player, holder.islandOwner());
        } else if (top.getHolder() instanceof CoreService.IslandOverviewInventoryHolder holder) {
            handleIslandOverviewMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.ParcelsInventoryHolder holder) {
            handleParcelsMenuClick(event, player, holder.islandOwner());
        } else if (top.getHolder() instanceof CoreService.ChunkMapInventoryHolder holder) {
            handleChunkMapClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.ChunkSettingsInventoryHolder holder) {
            handleChunkSettingsMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.BiomeInventoryHolder holder) {
            handleBiomeMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.IslandSettingsInventoryHolder holder) {
            handleIslandSettingsClick(event, player, holder.islandOwner());
        } else if (top.getHolder() instanceof CoreService.VisitorSettingsInventoryHolder holder) {
            handleVisitorSettingsClick(event, player, holder.islandOwner());
        } else if (top.getHolder() instanceof CoreService.ParcelInventoryHolder holder) {
            handleParcelMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.ParcelVisitorSettingsInventoryHolder holder) {
            handleParcelVisitorSettingsClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.ParcelMemberSettingsInventoryHolder holder) {
            handleParcelMemberSettingsClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.ParcelMarketInventoryHolder holder) {
            handleParcelMarketMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.TeleportInventoryHolder holder) {
            handleTeleportMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.PermissionsHubInventoryHolder holder) {
            handlePermissionsHubMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.PermissionsActionInventoryHolder holder) {
            handlePermissionsActionMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.PermissionPlayerListInventoryHolder holder) {
            handlePermissionPlayerListMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.PermissionMemberDetailInventoryHolder holder) {
            handlePermissionMemberDetailMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.IslandTrustMembersInventoryHolder holder) {
            handleIslandTrustMembersMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.IslandOwnersInventoryHolder holder) {
            handleIslandOwnersMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.IslandMasterMenuInventoryHolder holder) {
            handleIslandMasterMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.IslandMasterInviteInventoryHolder holder) {
            handleIslandMasterInviteMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.ParcelMembersInventoryHolder holder) {
            handleParcelMembersMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.ParcelModerationInventoryHolder holder) {
            handleParcelModerationMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.BlockValueInventoryHolder holder) {
            handleBlockValueMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.IslandBlocksInventoryHolder holder) {
            handleIslandBlocksMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.IslandShopInventoryHolder holder) {
            handleIslandShopMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.TimeModeShopInventoryHolder holder) {
            handleTimeModeShopMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.WeatherShopInventoryHolder holder) {
            handleWeatherShopMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.NightVisionShopInventoryHolder holder) {
            handleNightVisionShopMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.ParcelShopInventoryHolder holder) {
            handleParcelShopMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.ParcelBiomeInventoryHolder holder) {
            handleParcelBiomeMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.ParcelTimeModeShopInventoryHolder holder) {
            handleParcelTimeModeShopMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.ParcelWeatherShopInventoryHolder holder) {
            handleParcelWeatherShopMenuClick(event, player, holder);
        } else if (top.getHolder() instanceof CoreService.ParcelNightVisionShopInventoryHolder holder) {
            handleParcelNightVisionShopMenuClick(event, player, holder);
        }
    }

    private void handleCoreMenuClick(InventoryClickEvent event, Player player, CoreService.CoreInventoryHolder holder) {
        if (event.getClickedInventory() == null) return;
        boolean inTop = event.getClickedInventory().equals(event.getView().getTopInventory());
        if (inTop && event.getRawSlot() != 11 && event.getRawSlot() != 13 && event.getRawSlot() != 15 && event.getRawSlot() != 16 && !CORE_INPUT_SLOTS.contains(event.getRawSlot())) event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        var coreLocation = holder.coreLocation();
        if (inTop && event.getRawSlot() == 11) {
            event.setCancelled(true);
            if (!islandService.hasContainerAccess(player.getUniqueId(), island)) {
                player.sendMessage(ChatColor.RED + "Keine Rechte.");
                return;
            }
            if (event.isShiftClick()) {
                player.openInventory(coreService.createIslandBlocksMenu(island, 0, "core"));
            } else {
                player.openInventory(coreService.createBlockValueMenu(island, 0, "core"));
            }
            return;
        }
        if (inTop && event.getRawSlot() == 16) {
            event.setCancelled(true);
            if (!islandService.hasContainerAccess(player.getUniqueId(), island)) {
                player.sendMessage(ChatColor.RED + "Keine Rechte.");
                return;
            }
            if (event.isLeftClick()) {
                coreService.depositPlayerExperience(player, island);
            } else if (event.isRightClick()) {
                coreService.withdrawPlayerExperience(player, island, event.isShiftClick() ? 10 : 1);
            }
            player.openInventory(coreService.createCoreMenu(player, island, coreLocation));
            return;
        }
        if (inTop && event.getRawSlot() == 15) {
            event.setCancelled(true);
            if (!islandService.hasContainerAccess(player.getUniqueId(), island)) {
                player.sendMessage(ChatColor.RED + "Keine Rechte.");
                return;
            }
            CoreService.CoreDisplayMode mode = coreService.cycleCoreDisplayMode(island, coreLocation);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Core-Anzeige: " + ChatColor.WHITE + coreService.displayModeLabel(mode));
            player.openInventory(coreService.createCoreMenu(player, island, coreLocation));
            return;
        }
        if (inTop && event.getRawSlot() == 13) {
            event.setCancelled(true);
            if (!islandService.hasContainerAccess(player.getUniqueId(), island)) {
                player.sendMessage(ChatColor.RED + "Keine Rechte.");
                return;
            }
            player.openInventory(coreService.createUpgradeProgressMenu(island, 0));
            return;
        }
    }

    private void handleUpgradeProgressClick(InventoryClickEvent event, Player player, CoreService.UpgradeProgressInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        if (event.getRawSlot() == 49) {
            player.openInventory(coreService.createCoreMenu(player, island));
            return;
        }
        String action = coreService.readHiddenAction(event.getCurrentItem());
        if ("milestone".equalsIgnoreCase(action)) {
            if (event.isLeftClick()) {
                islandService.setPinnedMilestone(island);
                player.sendMessage(ChatColor.AQUA + "Display fokussiert jetzt: " + ChatColor.WHITE + "Meilensteinpfad");
            } else if (event.isRightClick()) {
                ItemStack clickedItem = event.getCurrentItem();
                if (islandService.levelUp(island)) {
                    int achievedLevel = Math.max(0, island.getLevel() - 1);
                    player.sendMessage(ChatColor.GREEN + "Meilenstein freigeschaltet. Stufe " + achievedLevel);
                    coreService.broadcastMilestoneAchievement(player, achievedLevel, clickedItem);
                } else {
                    player.sendMessage(ChatColor.RED + "Meilensteinbedingungen nicht erf\u00fcllt.");
                    coreService.sendUpgradeStatusChat(player, island);
                }
            }
            coreService.refreshCoreDisplay(island);
            player.openInventory(coreService.createUpgradeProgressMenu(island, 0));
            return;
        }
        IslandService.UpgradeBranch branch = coreService.readUpgradeBranch(event.getCurrentItem());
        if (branch == null) return;
        if (event.isRightClick()) {
            ItemStack clickedItem = event.getCurrentItem();
            if (islandService.unlockUpgrade(island, branch)) {
                player.sendMessage(ChatColor.GREEN + branch.displayName() + " ausgebaut.");
                coreService.broadcastUpgradeAchievement(player, branch, clickedItem);
            } else {
                player.sendMessage(ChatColor.RED + "Upgradebedingungen nicht erf\u00fcllt.");
                islandService.setPinnedUpgrade(island, branch);
                coreService.sendUpgradeStatusChat(player, island);
            }
        } else {
            islandService.setPinnedUpgrade(island, branch);
            player.sendMessage(ChatColor.AQUA + "Display fokussiert jetzt: " + ChatColor.WHITE + branch.displayName());
        }
        coreService.refreshCoreDisplay(island);
        player.openInventory(coreService.createUpgradeProgressMenu(island, 0));
    }

    private void handleIslandMenuClick(InventoryClickEvent event, Player player, UUID islandOwner) {
        event.setCancelled(true);
        if (islandOwner == null) {
            switch (event.getRawSlot()) {
                case 11 -> player.performCommand("is create");
                case 13 -> player.openInventory(coreService.createIslandOverviewMenu(player));
                case 15 -> player.openInventory(coreService.createTeleportMenu(player.getUniqueId(), 0));
                default -> {
                }
            }
            return;
        }
        IslandData island = islandService.getIsland(islandOwner).orElse(null);
        if (island == null || !islandService.hasBuildAccess(player.getUniqueId(), island)) return;
        switch (event.getRawSlot()) {
            case 11 -> player.openInventory(coreService.createIslandSettingsMenu(player, island));
            case 13 -> player.openInventory(coreService.createChunkSettingsMenu(player, island));
            case 15 -> player.openInventory(coreService.createParcelsMenu(player, island));
            case 29 -> player.openInventory(coreService.createIslandShopMenu(player, island));
            case 31 -> player.openInventory(coreService.createTeleportMenu(player.getUniqueId(), 0));
            case 33 -> player.openInventory(coreService.createIslandOverviewMenu(player, island));
            default -> {
            }
        }
    }

    private void handleIslandOverviewMenuClick(InventoryClickEvent event, Player player, CoreService.IslandOverviewInventoryHolder holder) {
        event.setCancelled(true);
        IslandData ownIsland = holder.islandOwner() == null ? null : islandService.getIsland(holder.islandOwner()).orElse(null);
        if (ownIsland != null && !islandService.hasBuildAccess(player.getUniqueId(), ownIsland)) return;
        int raw = event.getRawSlot();
        if (raw == 49) {
            if (ownIsland != null) {
                player.openInventory(coreService.createIslandMenu(player, ownIsland));
            } else {
                player.openInventory(coreService.createIslandMenu(player));
            }
            return;
        }
        if (raw < 0 || raw > 44) return;

        int row = raw / 9;
        int col = raw % 9;
        if (row > 4 || col < 2 || col > 6) return;

        int offsetX = col - 4;
        int offsetZ = row - 2;
        int targetGridX = holder.centerGridX() + offsetX;
        int targetGridZ = holder.centerGridZ() + offsetZ;
        if (targetGridX == 0 && targetGridZ == 0) return;
        IslandData target = islandService.getIslandByGrid(targetGridX, targetGridZ).orElse(null);
        if (target == null) {
            if (holder.claimMode()) {
                claimIslandFromOverview(player, targetGridX, targetGridZ);
            }
            return;
        }
        if (target.getIslandSpawn() == null) return;
        if (!islandService.canTeleportToIsland(target, player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Zu dieser Insel kannst du dich aktuell nicht teleportieren.");
            return;
        }
        player.teleport(target.getIslandSpawn());
        player.closeInventory();
    }

    private void claimIslandFromOverview(Player player, int gridX, int gridZ) {
        if (islandService.getIsland(player.getUniqueId()).isPresent()) {
            player.sendMessage(ChatColor.YELLOW + "Du hast bereits eine Insel.");
            return;
        }
        if (islandService.isIslandCreationPending(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Deine Insel wird bereits vorbereitet.");
            return;
        }
        if (!islandService.isPlotAvailable(gridX, gridZ)) {
            player.sendMessage(ChatColor.RED + "Dieser Slot ist nicht mehr frei.");
            return;
        }
        String creationThrottleMessage = islandService.getIslandCreationThrottleMessage(player.getUniqueId());
        if (creationThrottleMessage != null) {
            player.sendMessage(ChatColor.YELLOW + creationThrottleMessage);
            return;
        }
        String recreationWaitMessage = islandService.getRecreationQueueWaitMessage(player.getUniqueId());
        if (recreationWaitMessage != null) {
            player.sendMessage(ChatColor.YELLOW + recreationWaitMessage);
            return;
        }
        player.closeInventory();
        player.teleport(islandService.getSpawnLocation());
        boolean queued = islandService.queueIslandCreation(player.getUniqueId(), new IslandPlot(gridX, gridZ), created -> {
            islandService.ensureCentralSpawnAndCoreSafe(created);
            coreService.ensureCorePlaced(created);
            islandService.queuePregeneration(created);
            islandService.ensureTemplateAtLocation(created, created.getIslandSpawn());
            Bukkit.broadcastMessage(ChatColor.GOLD + "Willkommen " + ChatColor.YELLOW + player.getName()
                    + ChatColor.GOLD + " auf der neuen Insel" + ChatColor.YELLOW + " " + created.getGridX() + ":" + created.getGridZ()
                    + ChatColor.GOLD + "!");
            Player online = Bukkit.getPlayer(created.getOwner());
            if (online == null || !online.isOnline()) return;
            online.sendMessage(ChatColor.GREEN + "Deine Insel wurde auf Slot " + created.getGridX() + ":" + created.getGridZ() + " geclaimt.");
            online.teleport(created.getIslandSpawn());
            online.sendMessage(ChatColor.YELLOW + "Weitere Chunks werden jetzt im Hintergrund generiert.");
            startClaimStatusMessages(online);
        });
        if (!queued) {
            player.sendMessage(ChatColor.RED + "Dieser Slot ist nicht mehr frei.");
            return;
        }
        int pregenerationQueuePos = islandService.getIslandPregenerationQueuePosition(player.getUniqueId());
        if (pregenerationQueuePos > 1) {
            player.sendMessage(ChatColor.GOLD + "Insel-Claim gestartet f\u00fcr Slot " + gridX + ":" + gridZ + ". Startchunks sind bereit, Pregeneration Platz " + pregenerationQueuePos + ".");
        } else {
            player.sendMessage(ChatColor.GOLD + "Insel-Claim gestartet f\u00fcr Slot " + gridX + ":" + gridZ + "...");
        }
    }

    private void startClaimStatusMessages(Player player) {
        UUID playerId = player.getUniqueId();
        stopClaimStatusMessages(playerId);
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(CoreMenuListener.class);
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player live = Bukkit.getPlayer(playerId);
            if (live == null || !live.isOnline()) {
                stopClaimStatusMessages(playerId);
                return;
            }
            IslandData own = islandService.getPrimaryIsland(playerId).orElse(null);
            if (own == null && !islandService.isIslandCreationPending(playerId)) {
                stopClaimStatusMessages(playerId);
                return;
            }
            if (islandService.isIslandReady(playerId)) {
                live.sendMessage(ChatColor.GREEN + "Deine Insel ist vollst\u00e4ndig generiert.");
                stopClaimStatusMessages(playerId);
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
        }, 60L, 100L);
        claimStatusTasks.put(playerId, taskId);
    }

    private void stopClaimStatusMessages(UUID playerId) {
        Integer taskId = claimStatusTasks.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void handleIslandSettingsClick(InventoryClickEvent event, Player player, UUID islandOwner) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(islandOwner).orElse(null);
        if (island == null || !islandService.hasBuildAccess(player.getUniqueId(), island)) return;
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
        if (event.getRawSlot() != 40 && (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR)) return;
        boolean canManagePermissions = islandService.isIslandOwner(island, player.getUniqueId()) || player.isOp();
        switch (event.getRawSlot()) {
            case 10 -> player.openInventory(coreService.createCoreMenu(player, island));
            case 11 -> player.openInventory(coreService.createIslandBlocksMenu(island, 0, "island"));
            case 12 -> player.openInventory(coreService.createBlockValueMenu(island, 0, "island"));
            case 13 -> {
                island.setIslandSpawn(player.getLocation().clone());
                islandService.save();
                player.sendMessage(ChatColor.GREEN + "Inselspawn gesetzt.");
                player.openInventory(coreService.createIslandSettingsMenu(player, island));
            }
            case 14 -> {
                if (!islandService.isIslandOwner(island, player.getUniqueId()) && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "Nur Master oder Owner.");
                    player.openInventory(coreService.createIslandSettingsMenu(player, island));
                    return;
                }
                coreService.beginIslandTitleInput(player, island);
            }
            case 15 -> {
                if (!islandService.isIslandOwner(island, player.getUniqueId()) && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "Nur Master oder Owner.");
                    player.openInventory(coreService.createIslandSettingsMenu(player, island));
                    return;
                }
                coreService.beginIslandWarpInput(player, island);
            }
            case 19 -> {
                int relX = islandService.relativeChunkX(island, player.getLocation().getChunk().getX());
                int relZ = islandService.relativeChunkZ(island, player.getLocation().getChunk().getZ());
                player.openInventory(coreService.createBiomeMenu(player, island, 0, relX, relZ, 0));
            }
            case 20 -> {
                if (!canManagePermissions) {
                    player.openInventory(coreService.createIslandSettingsMenu(player, island));
                    return;
                }
                player.openInventory(coreService.createPermissionsHubMenu(player, island));
            }
            case 24 -> player.openInventory(coreService.createVisitorSettingsMenu(island));
            case 40 -> player.openInventory(coreService.createIslandMenu(player, island));
            default -> {
            }
        }
    }

    private void handlePermissionsHubMenuClick(InventoryClickEvent event, Player player, CoreService.PermissionsHubInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null || !islandService.isIslandOwner(island, player.getUniqueId()) && !player.isOp()) return;
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
        if (event.getRawSlot() != 22 && (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR)) return;
        if (event.getRawSlot() != 22 && isDecorativePane(event.getCurrentItem())) return;
        switch (event.getRawSlot()) {
            case 11 -> {
                boolean canOpenMasterMenu = islandService.isIslandOwner(island, player.getUniqueId())
                        || islandService.getPendingMasterInviteIsland(player.getUniqueId()) != null
                        || player.isOp();
                if (!canOpenMasterMenu) {
                    player.openInventory(coreService.createIslandSettingsMenu(player, island));
                    return;
                }
                player.openInventory(coreService.createPermissionsActionMenu(player, island, "MASTER"));
            }
            case 13 -> player.openInventory(coreService.createPermissionsActionMenu(player, island, "OWNER"));
            case 15 -> player.openInventory(coreService.createPermissionsActionMenu(player, island, "MEMBER"));
            case 22 -> player.openInventory(coreService.createIslandSettingsMenu(player, island));
        }
    }

    private void handlePermissionsActionMenuClick(InventoryClickEvent event, Player player, CoreService.PermissionsActionInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null || !islandService.isIslandOwner(island, player.getUniqueId()) && !player.isOp()) return;
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
        if (event.getRawSlot() != 22 && (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR)) return;
        if (event.getRawSlot() != 22 && isDecorativePane(event.getCurrentItem())) return;
        switch (event.getRawSlot()) {
            case 11 -> {
                boolean canAdd = switch (holder.role()) {
                    case "MASTER" -> islandService.canInviteMaster(island, player.getUniqueId()) || player.isOp();
                    case "OWNER" -> islandService.canAddOwner(island, player.getUniqueId()) || player.isOp();
                    case "MEMBER" -> islandService.canManageMembers(island, player.getUniqueId()) || player.isOp();
                    default -> false;
                };
                if (!canAdd) {
                    player.openInventory(coreService.createPermissionsActionMenu(player, island, holder.role()));
                    return;
                }
                player.openInventory(coreService.createPermissionPlayerListMenu(player, island, holder.role(), true, null, 0));
            }
            case 13 -> {
                if ("MASTER".equals(holder.role())) {
                    IslandData inviteIsland = islandService.getPendingMasterInviteIsland(player.getUniqueId());
                    if (inviteIsland != null && island.getOwner().equals(inviteIsland.getOwner())) {
                        if (islandService.acceptMasterInvite(player.getUniqueId())) {
                            player.sendMessage(ChatColor.GREEN + "Master-Einladung angenommen. Deine alte Insel wurde ggf. aufgel\u00f6st.");
                            IslandData newIsland = islandService.getIsland(player.getUniqueId()).orElse(island);
                            player.openInventory(coreService.createIslandMenu(player, newIsland));
                            return;
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "Keine offene Master-Einladung.");
                    }
                    player.openInventory(coreService.createPermissionsActionMenu(player, island, "MASTER"));
                }
            }
            case 15 -> {
                if ("MASTER".equals(holder.role())) {
                    if (islandService.leaveMasterRole(island, player.getUniqueId())) {
                        player.sendMessage(ChatColor.YELLOW + "Du bist als Master von der Insel ausgetreten.");
                        player.teleport(islandService.getSpawnLocation());
                        sendNoIslandHelp(player);
                    } else {
                        player.sendMessage(ChatColor.RED + "Du bist auf keiner Insel als zus\u00e4tzlicher Master.");
                    }
                    IslandData own = islandService.getIsland(player.getUniqueId()).orElse(null);
                    if (own != null) player.openInventory(coreService.createIslandMenu(player, own));
                    else player.closeInventory();
                } else {
                    player.openInventory(coreService.createPermissionPlayerListMenu(player, island, holder.role(), false, null, 0));
                }
            }
            case 22 -> player.openInventory(coreService.createPermissionsHubMenu(player, island));
        }
    }

    private void handlePermissionPlayerListMenuClick(InventoryClickEvent event, Player player, CoreService.PermissionPlayerListInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null || !islandService.isIslandOwner(island, player.getUniqueId()) && !player.isOp()) return;
        if (event.getRawSlot() == 46) {
            coreService.beginPlayerPermissionSearch(player, island, holder.role(), holder.adding());
            return;
        } else if (event.getRawSlot() == 48) {
            player.openInventory(coreService.createPermissionPlayerListMenu(player, island, holder.role(), holder.adding(), holder.searchFilter(), holder.page() - 1));
            return;
        } else if (event.getRawSlot() == 49) {
            player.openInventory(coreService.createPermissionsActionMenu(player, island, holder.role()));
            return;
        } else if (event.getRawSlot() == 50) {
            player.openInventory(coreService.createPermissionPlayerListMenu(player, island, holder.role(), holder.adding(), holder.searchFilter(), holder.page() + 1));
            return;
        }
        
        org.bukkit.inventory.ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) clicked.getItemMeta();
        if (meta == null || meta.getOwningPlayer() == null) return;
        
        org.bukkit.OfflinePlayer target = meta.getOwningPlayer();
        UUID targetId = target.getUniqueId();
        
        if (holder.adding()) {
            if ("MASTER".equals(holder.role())) {
                if (!islandService.canInviteMaster(island, player.getUniqueId()) && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "Nur Master k\u00f6nnen Master einladen.");
                    player.openInventory(coreService.createPermissionsActionMenu(player, island, holder.role()));
                    return;
                }
                islandService.queueMasterInvite(island, player.getUniqueId(), targetId);
                Player tPlayer = target.getPlayer();
                if (tPlayer != null) {
                    tPlayer.sendMessage("");
                    tPlayer.sendMessage(ChatColor.DARK_RED.toString() + ChatColor.BOLD + "ACHTUNG: MASTER-EINLADUNG ERHALTEN!");
                    tPlayer.sendMessage(ChatColor.RED + player.getName() + " hat dich eingeladen, Master seiner Insel zu werden.");
                    tPlayer.sendMessage(ChatColor.RED + "Gehe in deine EIGENEN Insel-Einstellungen -> Berechtigungen -> Master-Rechte, um sie anzunehmen!");
                    tPlayer.sendMessage(ChatColor.DARK_RED.toString() + ChatColor.BOLD + "WENN DU ANNNIMMST, WIRD DEINE EIGENE INSEL UNWIDERRUFLICH GEL\u00d6SCHT!");
                    tPlayer.sendMessage(ChatColor.RED + "Du kannst nur EINER Insel als Master/Owner angeh\u00f6ren.");
                    tPlayer.sendMessage("");
                }
                player.sendMessage(ChatColor.GREEN + "Master-Einladung verschickt.");
                player.openInventory(coreService.createPermissionPlayerListMenu(player, island, holder.role(), true, holder.searchFilter(), holder.page()));
            } else if ("OWNER".equals(holder.role())) {
                if (!islandService.canAddOwner(island, player.getUniqueId()) && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "Nur Master oder Owner k\u00f6nnen Owner hinzuf\u00fcgen.");
                    player.openInventory(coreService.createPermissionsActionMenu(player, island, holder.role()));
                    return;
                }
                boolean add = islandService.grantOwnerRole(island, player.getUniqueId(), targetId);
                if (add) {
                    Player tPlayer = target.getPlayer();
                    if (tPlayer != null) tPlayer.sendMessage(ChatColor.GREEN + "Du wurdest als Owner zu " + player.getName() + "'s Insel hinzugef\u00fcgt!");
                    player.sendMessage(ChatColor.GREEN + "Owner hinzugef\u00fcgt.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Keine \u00c4nderung.");
                }
                player.openInventory(coreService.createPermissionPlayerListMenu(player, island, holder.role(), true, holder.searchFilter(), holder.page()));
            } else if ("MEMBER".equals(holder.role())) {
                if (!islandService.canManageMembers(island, player.getUniqueId()) && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "Nur Master oder Owner k\u00f6nnen Member verwalten.");
                    player.openInventory(coreService.createPermissionsActionMenu(player, island, holder.role()));
                    return;
                }
                player.openInventory(coreService.createPermissionMemberDetailMenu(player, island, targetId));
            }
        } else {
            if ("MASTER".equals(holder.role())) {
                player.sendMessage(ChatColor.RED + "Master k\u00f6nnen nicht ausgetragen werden.");
                player.openInventory(coreService.createPermissionsActionMenu(player, island, holder.role()));
                return;
            } else if ("OWNER".equals(holder.role())) {
                if (!islandService.canRemoveOwner(island, player.getUniqueId()) && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "Nur Master k\u00f6nnen Owner entfernen.");
                    player.openInventory(coreService.createPermissionsActionMenu(player, island, holder.role()));
                    return;
                }
                if (islandService.revokeOwnerRole(island, player.getUniqueId(), targetId)) {
                    Player tPlayer = target.getPlayer();
                    if (tPlayer != null) tPlayer.sendMessage(ChatColor.RED + "Du wurdest als Owner von " + player.getName() + "'s Insel entfernt.");
                    player.sendMessage(ChatColor.RED + "Owner entfernt.");
                }
            } else if ("MEMBER".equals(holder.role())) {
                if (!islandService.canManageMembers(island, player.getUniqueId()) && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "Nur Master oder Owner k\u00f6nnen Member verwalten.");
                    player.openInventory(coreService.createPermissionsActionMenu(player, island, holder.role()));
                    return;
                }
                player.openInventory(coreService.createPermissionMemberDetailMenu(player, island, targetId));
                return;
            }
            player.openInventory(coreService.createPermissionPlayerListMenu(player, island, holder.role(), false, holder.searchFilter(), holder.page()));
        }
    }

    private void handlePermissionMemberDetailMenuClick(InventoryClickEvent event, Player player, CoreService.PermissionMemberDetailInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null || !islandService.isIslandOwner(island, player.getUniqueId()) && !player.isOp()) return;
        UUID targetId = holder.targetPlayer();
        switch (event.getRawSlot()) {
            case 9 -> {
                boolean changed = islandService.grantMemberPermission(island, targetId, IslandService.TrustPermission.ALL);
                if (changed) {
                    Player tPlayer = Bukkit.getOfflinePlayer(targetId).getPlayer();
                    if (tPlayer != null) tPlayer.sendMessage(ChatColor.GREEN + "Deine Rechte auf " + player.getName() + "'s Insel wurden auf Vollzugriff gesetzt!");
                    player.sendMessage(ChatColor.GREEN + "Alle Rechte zugewiesen.");
                }
                player.openInventory(coreService.createPermissionMemberDetailMenu(player, island, targetId));
            }
            case 11 -> {
                boolean changed = island.getMemberBuildAccess().contains(targetId)
                        ? islandService.revokeMemberPermission(island, targetId, IslandService.TrustPermission.BUILD)
                        : islandService.grantMemberPermission(island, targetId, IslandService.TrustPermission.BUILD);
                if (!changed) player.sendMessage(ChatColor.YELLOW + "Keine Änderung.");
                player.openInventory(coreService.createPermissionMemberDetailMenu(player, island, targetId));
            }
            case 13 -> {
                boolean changed = island.getMemberContainerAccess().contains(targetId)
                        ? islandService.revokeMemberPermission(island, targetId, IslandService.TrustPermission.CONTAINER)
                        : islandService.grantMemberPermission(island, targetId, IslandService.TrustPermission.CONTAINER);
                if (!changed) player.sendMessage(ChatColor.YELLOW + "Keine Änderung.");
                player.openInventory(coreService.createPermissionMemberDetailMenu(player, island, targetId));
            }
            case 15 -> {
                boolean changed = island.getMemberRedstoneAccess().contains(targetId)
                        ? islandService.revokeMemberPermission(island, targetId, IslandService.TrustPermission.REDSTONE)
                        : islandService.grantMemberPermission(island, targetId, IslandService.TrustPermission.REDSTONE);
                if (!changed) player.sendMessage(ChatColor.YELLOW + "Keine Änderung.");
                player.openInventory(coreService.createPermissionMemberDetailMenu(player, island, targetId));
            }
            case 17 -> {
                boolean rmBuild = island.getMemberBuildAccess().remove(targetId);
                boolean rmCont = island.getMemberContainerAccess().remove(targetId);
                boolean rmRed = island.getMemberRedstoneAccess().remove(targetId);
                if (rmBuild || rmCont || rmRed) {
                    org.bukkit.OfflinePlayer t = Bukkit.getOfflinePlayer(targetId);
                    Player tPlayer = t.getPlayer();
                    if (tPlayer != null) tPlayer.sendMessage(ChatColor.RED + "Deine Member-Rechte (" + player.getName() + ") wurden komplett entfernt.");
                    player.sendMessage(ChatColor.RED + "Member entfernt.");
                    islandService.save();
                }
                player.openInventory(coreService.createPermissionPlayerListMenu(player, island, "MEMBER", false, null, 0));
            }
            case 22 -> player.openInventory(coreService.createPermissionPlayerListMenu(player, island, "MEMBER", false, null, 0));
        }
    }

    private void handleChunkSettingsMenuClick(InventoryClickEvent event, Player player, CoreService.ChunkSettingsInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null || !islandService.hasBuildAccess(player.getUniqueId(), island)) return;
        switch (event.getRawSlot()) {
            case 11 -> {
                int relX = islandService.relativeChunkX(island, player.getLocation().getChunk().getX());
                int relZ = islandService.relativeChunkZ(island, player.getLocation().getChunk().getZ());
                int displayX = islandService.displayChunkX(relX);
                int displayZ = islandService.displayChunkZ(relZ);
                IslandService.ChunkUnlockResult result = islandService.unlockChunk(island, player.getUniqueId(), relX, relZ);
                switch (result) {
                    case SUCCESS -> player.sendMessage(ChatColor.GREEN + "Aktueller Chunk freigeschaltet: " + displayX + ":" + displayZ);
                    case ALREADY_UNLOCKED -> player.sendMessage(ChatColor.YELLOW + "Dieser Chunk ist bereits freigeschaltet.");
                    case NO_UNLOCKS_LEFT -> player.sendMessage(ChatColor.RED + "Chunk konnte nicht freigeschaltet werden (keine Unlocks frei).");
                    case NEEDS_NEIGHBOR_APPROVAL -> {
                        player.sendMessage(ChatColor.GOLD + "Grenz-Chunk: Anfrage an Nachbar gesendet.");
                        player.sendMessage(ChatColor.GRAY + "Risiko bei Verbindung: Fl\u00fcssigkeiten, Items und Mobs k\u00f6nnen \u00fcbertreten.");
                    }
                    case PENDING_NEIGHBOR_APPROVAL -> player.sendMessage(ChatColor.YELLOW + "Freigabe vom Nachbarn steht noch aus.");
                    default -> player.sendMessage(ChatColor.RED + "Chunk konnte nicht freigeschaltet werden.");
                }
                player.openInventory(coreService.createChunkSettingsMenu(player, island));
            }
            case 13 -> player.openInventory(coreService.createChunkMapMenu(player, island, 0));
            case 15 -> {
                boolean nowActive = particlePreviewService.toggle(player);
                if (nowActive) {
                    player.sendMessage(ChatColor.AQUA + "Chunkanzeige aktiviert.");
                    coreService.sendCurrentChunkStatusWithUnlock(player, island);
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Chunkanzeige deaktiviert.");
                }
                player.openInventory(coreService.createChunkSettingsMenu(player, island));
            }
            case 22 -> player.openInventory(coreService.createIslandMenu(player, island));
            default -> {
            }
        }
    }

    private void handleParcelsMenuClick(InventoryClickEvent event, Player player, UUID islandOwner) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(islandOwner).orElse(null);
        if (island == null) return;
        var currentParcel = islandService.getParcelAt(island, player.getLocation());
        boolean hasParcelAccess = currentParcel != null && islandService.isParcelUser(island, currentParcel, player.getUniqueId());
        if (!islandService.hasBuildAccess(player.getUniqueId(), island) && !hasParcelAccess) return;
        switch (event.getRawSlot()) {
            case 10 -> {
                var parcel = islandService.getParcelAt(island, player.getLocation());
                if (parcel == null) {
                    player.sendMessage(ChatColor.YELLOW + "Du stehst aktuell in keinem Grundst\u00fcck.");
                    player.openInventory(coreService.createParcelsMenu(player, island));
                    return;
                }
                int relX = islandService.relativeChunkX(island, player.getLocation().getChunk().getX());
                int relZ = islandService.relativeChunkZ(island, player.getLocation().getChunk().getZ());
                openParcelMenu(player, island, relX, relZ);
            }
            case 12 -> {
                player.sendMessage(ChatColor.GOLD + "Grundst\u00fcck erstellen:");
                player.sendMessage(ChatColor.YELLOW + "1) Grundst\u00fccks-Stab holen");
                player.sendMessage(ChatColor.YELLOW + "2) Pos1/Pos2 setzen");
                player.sendMessage(ChatColor.YELLOW + "3) /is plot create");
                player.openInventory(coreService.createParcelsMenu(player, island));
            }
            case 14 -> {
                player.getInventory().addItem(islandService.createPlotWand());
                player.sendMessage(ChatColor.GREEN + "Grundst\u00fccks-Stab erhalten.");
                player.sendMessage(ChatColor.GRAY + "Setze Pos1/Pos2 und nutze /is plot create.");
                player.openInventory(coreService.createParcelsMenu(player, island));
            }
            case 16 -> player.openInventory(coreService.createTeleportMenu(player.getUniqueId(), 0, "parcels"));
            case 40 -> player.openInventory(coreService.createIslandMenu(player, island));
            default -> {
            }
        }
    }

    private void openParcelMenu(Player player, IslandData island, int relX, int relZ) {
        Inventory inventory = coreService.createParcelMenu(player, island, relX, relZ);
        var parcel = islandService.getParcel(island, relX, relZ);
        if (parcel != null) {
            inventory.setItem(20, namedItem(
                    Material.KNOWLEDGE_BOOK,
                    ChatColor.GOLD + "Plot-Markt",
                    java.util.List.of(
                            ChatColor.GRAY + "Verkauf / Miete festlegen",
                            ChatColor.YELLOW + "Klick = \u00f6ffnen"
                    )));
            inventory.setItem(22, namedItem(
                    Material.ANVIL,
                    ChatColor.GOLD + "GS umbenennen",
                    java.util.List.of(
                            ChatColor.GRAY + "Aktuell: " + ChatColor.WHITE + islandService.getParcelDisplayName(parcel),
                            ChatColor.YELLOW + "Klick = Name im Chat setzen"
                    )));
            inventory.setItem(34, namedItem(
                    parcel.isPvpEnabled() ? Material.DIAMOND_SWORD : Material.WOODEN_SWORD,
                    (parcel.isPvpEnabled() ? ChatColor.RED : ChatColor.GRAY) + "GS-PvP",
                    java.util.List.of(
                            ChatColor.GRAY + "Status: " + (parcel.isPvpEnabled() ? ChatColor.RED + "aktiv" : ChatColor.GREEN + "aus"),
                            ChatColor.GRAY + "PvP wird erst beim Betreten aktiv",
                            ChatColor.YELLOW + "Klick = umschalten"
                    )));
            inventory.setItem(36, namedItem(
                    Material.PAPER,
                    ChatColor.GOLD + "PvP-Rangliste resetten",
                    java.util.List.of(
                            ChatColor.GRAY + "Kills auf diesem GS zur\u00fccksetzen",
                            ChatColor.YELLOW + "Nur f\u00fcr Plot-Owner"
                    )));
            inventory.setItem(38, namedItem(
                    Material.IRON_SWORD,
                    ChatColor.RED + "PvP-Whitelist",
                    java.util.List.of(
                            ChatColor.GRAY + "Zul\u00e4ssige PvP-Spieler verwalten",
                            ChatColor.YELLOW + "Klick = GUI \u00f6ffnen"
                    )));
            inventory.setItem(35, namedItem(
                    parcel.isPveEnabled() ? Material.NETHER_STAR : Material.GRAY_WOOL,
                    (parcel.isPveEnabled() ? ChatColor.DARK_GREEN : ChatColor.GRAY) + "GS-PvE",
                    java.util.List.of(
                            ChatColor.GRAY + "Status: " + (parcel.isPveEnabled() ? ChatColor.DARK_GREEN + "aktiv" : ChatColor.GREEN + "aus"),
                            ChatColor.GRAY + "Wei\u00dfe Wolle = Startzone, andere Wolle = Spawner",
                            ChatColor.GRAY + "Gro\u00dfe Zone = mehr Mobs, Wellen und St\u00e4rke",
                            ChatColor.YELLOW + "Klick = umschalten"
                    )));
            inventory.setItem(37, namedItem(
                    Material.BOOK,
                    ChatColor.GOLD + "PvE-Anleitung",
                    java.util.List.of(
                            ChatColor.GRAY + "1) Wei\u00dfe Wolle = Startzone",
                            ChatColor.GRAY + "2) Ausgang nur an der Startzonen-Seite",
                            ChatColor.GRAY + "3) Ausgang darf breiter sein, wenn zusammenh\u00e4ngend",
                            ChatColor.GRAY + "4) Innen + aussen 3 hoch frei",
                            ChatColor.GRAY + "5) LIGHT_GRAY = Zombie-Familie mit Varianten",
                            ChatColor.GRAY + "6) GREEN = Spinnen, YELLOW = Skelette, ORANGE = W\u00fcste",
                            ChatColor.GRAY + "7) BLUE = Hafen, RED = Sprengtrupp, BLACK = Nachtwache"
                    )));
        }
        player.openInventory(inventory);
    }

    private boolean openParcelMarketInRentMode(ParcelData parcel) {
        return parcel != null && (parcel.isRentOfferEnabled() || (parcel.getRenter() != null && parcel.getRentUntil() > System.currentTimeMillis()));
    }

    private ItemStack namedItem(Material material, String name, java.util.List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void broadcastSkyCityChat(String message) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getWorld() != null && "skycity_world".equalsIgnoreCase(online.getWorld().getName())) {
                online.sendMessage(message);
            }
        }
    }

    private void handleBlockValueMenuClick(InventoryClickEvent event, Player player, CoreService.BlockValueInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        if (event.getRawSlot() == 49) {
            if ("core".equalsIgnoreCase(holder.backTarget())) {
                player.openInventory(coreService.createCoreMenu(player, island));
            } else {
                player.openInventory(coreService.createIslandMenu(player, island));
            }
        } else if (event.getRawSlot() == 48 && holder.page() > 0) {
            player.openInventory(coreService.createBlockValueMenu(island, holder.page() - 1, holder.backTarget()));
        } else if (event.getRawSlot() == 50) {
            player.openInventory(coreService.createBlockValueMenu(island, holder.page() + 1, holder.backTarget()));
        }
    }

    private void handleIslandBlocksMenuClick(InventoryClickEvent event, Player player, CoreService.IslandBlocksInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        if (event.getRawSlot() == 49) {
            if ("core".equalsIgnoreCase(holder.backTarget())) {
                player.openInventory(coreService.createCoreMenu(player, island));
            } else {
                player.openInventory(coreService.createIslandMenu(player, island));
            }
        } else if (event.getRawSlot() == 48 && holder.page() > 0) {
            player.openInventory(coreService.createIslandBlocksMenu(island, holder.page() - 1, holder.backTarget()));
        } else if (event.getRawSlot() == 50) {
            player.openInventory(coreService.createIslandBlocksMenu(island, holder.page() + 1, holder.backTarget()));
        }
    }

    private void handleChunkMapClick(InventoryClickEvent event, Player player, CoreService.ChunkMapInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null || !islandService.hasBuildAccess(player.getUniqueId(), island)) return;
        CoreService.ChunkMapMode mode = CoreService.ChunkMapMode.from(holder.mode());
        int page = holder.page();

        int raw = event.getRawSlot();
        if (raw >= 0 && raw <= 44) {
            int relX;
            int relZ;
            if (mode == CoreService.ChunkMapMode.ALL) {
                int index = (page * 45) + raw;
                if (index >= 4096) return;
                relX = coreService.chunkMapRelXByIndex(index);
                relZ = coreService.chunkMapRelZByIndex(index);
            } else {
                int playerRelX = islandService.relativeChunkX(island, player.getLocation().getChunk().getX());
                int playerRelZ = islandService.relativeChunkZ(island, player.getLocation().getChunk().getZ());
                int offsetX = (raw % 9) - 4;
                int offsetZ = (raw / 9) - 2;
                relX = playerRelX + offsetX;
                relZ = playerRelZ + offsetZ;
                if (relX < 0 || relX >= 64 || relZ < 0 || relZ >= 64) return;
            }
                if (event.getClick() == ClickType.RIGHT) {
                    player.openInventory(coreService.createBiomeMenu(player, island, 0, relX, relZ, page));
                    return;
                }
                if (event.getClick() == ClickType.SHIFT_RIGHT) {
                    openParcelMenu(player, island, relX, relZ);
                    return;
                }
                if (!islandService.isChunkUnlocked(island, relX, relZ)) {
                    IslandService.ChunkUnlockResult result = islandService.unlockChunk(island, player.getUniqueId(), relX, relZ);
                    int displayX = islandService.displayChunkX(relX);
                    int displayZ = islandService.displayChunkZ(relZ);
                    switch (result) {
                        case SUCCESS -> player.sendMessage(ChatColor.GREEN + "Chunk freigeschaltet: " + displayX + ":" + displayZ);
                        case NEEDS_NEIGHBOR_APPROVAL -> {
                            player.sendMessage(ChatColor.GOLD + "Grenz-Chunk: Anfrage an Nachbar gesendet.");
                            player.sendMessage(ChatColor.GRAY + "Risiko bei Verbindung: Fl\u00fcssigkeiten, Items und Mobs k\u00f6nnen \u00fcbertreten.");
                        }
                        case PENDING_NEIGHBOR_APPROVAL -> player.sendMessage(ChatColor.YELLOW + "Freigabe vom Nachbarn steht noch aus.");
                        case NO_UNLOCKS_LEFT -> player.sendMessage(ChatColor.RED + "Keine freien Chunk-Unlocks.");
                        case ALREADY_UNLOCKED -> player.sendMessage(ChatColor.YELLOW + "Chunk ist bereits freigeschaltet.");
                        default -> player.sendMessage(ChatColor.RED + "Chunk konnte nicht freigeschaltet werden.");
                    }
                  } else if (islandService.getParcel(island, relX, relZ) == null) {
                      player.sendMessage(ChatColor.YELLOW + "In diesem Chunk ist noch kein Grundst\u00fcck.");
                      player.sendMessage(ChatColor.GRAY + "Nutze /is plot wand und /is plot create f\u00fcr freie Quader.");
                      openParcelMenu(player, island, relX, relZ);
                      return;
                  } else {
                      openParcelMenu(player, island, relX, relZ);
                      return;
                  }
                player.openInventory(coreService.createChunkMapMenu(player, island, page, mode));
                return;
        }
        if (raw == 45) {
            player.openInventory(coreService.createIslandMenu(player, island));
        } else if (raw == 48 && mode == CoreService.ChunkMapMode.ALL && page > 0) {
            player.openInventory(coreService.createChunkMapMenu(player, island, page - 1, mode));
        } else if (raw == 50 && mode == CoreService.ChunkMapMode.ALL) {
            player.openInventory(coreService.createChunkMapMenu(player, island, page + 1, mode));
        } else if (raw == 53) {
            if (mode == CoreService.ChunkMapMode.ALL) {
                player.openInventory(coreService.createChunkMapMenu(player, island, 0, CoreService.ChunkMapMode.LOCAL));
            } else {
                player.openInventory(coreService.createChunkMapMenu(player, island, 0, CoreService.ChunkMapMode.ALL));
            }
        }
    }

    private void handleBiomeMenuClick(InventoryClickEvent event, Player player, CoreService.BiomeInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null || !islandService.hasBuildAccess(player.getUniqueId(), island)) return;
        int raw = event.getRawSlot();
        if (raw >= 0 && raw <= 44) {
            int index = holder.page() * 45 + raw;
            if (index < coreService.getBiomeOptions().size()) {
                Biome biome = coreService.getBiomeOptions().get(index);
                if (!canUseIslandShop(player, island)) {
                    return;
                }
                if (event.getClick() == ClickType.SHIFT_RIGHT) {
                    long cost = islandService.getBiomeChangeCost(true);
                    if (island.getStoredExperience() < cost) {
                        player.sendMessage(ChatColor.RED + "Nicht genug Core-Erfahrung. Ben\u00f6tigt: " + cost);
                        return;
                    }
                    if (!islandService.spendStoredExperience(island, cost)) {
                        player.sendMessage(ChatColor.RED + "Nicht genug Core-Erfahrung.");
                        return;
                    }
                    int changed = islandService.setBiomeForIsland(island, biome, true);
                    player.sendMessage(ChatColor.GREEN + "Biom gesetzt: " + coreService.biomeDisplayNameDe(biome)
                            + " f\u00fcr " + changed + " freigeschaltete Chunks. Kosten: " + cost);
                } else {
                    if (!islandService.isChunkUnlocked(island, holder.relChunkX(), holder.relChunkZ())) {
                        player.sendMessage(ChatColor.RED + "Ziel-Chunk ist gesperrt.");
                        return;
                    }
                    long cost = islandService.getBiomeChangeCost(false);
                    if (island.getStoredExperience() < cost) {
                        player.sendMessage(ChatColor.RED + "Nicht genug Core-Erfahrung. Ben\u00f6tigt: " + cost);
                        return;
                    }
                    if (!islandService.spendStoredExperience(island, cost)) {
                        player.sendMessage(ChatColor.RED + "Nicht genug Core-Erfahrung.");
                        return;
                    }
                    islandService.setBiomeForChunk(island, holder.relChunkX(), holder.relChunkZ(), biome);
                    int displayX = islandService.displayChunkX(holder.relChunkX());
                    int displayZ = islandService.displayChunkZ(holder.relChunkZ());
                    player.sendMessage(ChatColor.GREEN + "Biom gesetzt: " + coreService.biomeDisplayNameDe(biome)
                            + " f\u00fcr Chunk " + displayX + ":" + displayZ + ". Kosten: " + cost);
                }
                triggerBiomeVisualReload(player, coreService.createBiomeMenu(player, island, holder.page(), holder.relChunkX(), holder.relChunkZ(), holder.returnPage()));
                return;
            }
        }
        if (raw == 45) {
            player.openInventory(coreService.createIslandShopMenu(player, island));
        } else if (raw == 48 && holder.page() > 0) {
            player.openInventory(coreService.createBiomeMenu(player, island, holder.page() - 1, holder.relChunkX(), holder.relChunkZ(), holder.returnPage()));
        } else if (raw == 50) {
            player.openInventory(coreService.createBiomeMenu(player, island, holder.page() + 1, holder.relChunkX(), holder.relChunkZ(), holder.returnPage()));
        }
    }

    private void handleIslandShopMenuClick(InventoryClickEvent event, Player player, CoreService.IslandShopInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null || !islandService.hasBuildAccess(player.getUniqueId(), island)) return;
        int raw = event.getRawSlot();
        if (raw == 40) {
            player.openInventory(coreService.createIslandMenu(player, island));
            return;
        }
        if (raw == 10) {
            if (!canUseIslandShop(player, island)) {
                return;
            }
            player.openInventory(coreService.createBiomeMenu(player, island, 0, holder.relChunkX(), holder.relChunkZ(), 0));
            return;
        }
        if (raw == 12) {
            if (!canUseIslandShop(player, island)) {
                return;
            }
            player.openInventory(coreService.createTimeModeShopMenu(island, "shop"));
            return;
        }
        if (raw == 13) {
            if (!canUseIslandShop(player, island)) {
                return;
            }
            player.openInventory(coreService.createWeatherShopMenu(island, "shop"));
            return;
        }
        if (raw == 14) {
            if (!canUseIslandShop(player, island)) {
                return;
            }
            int tier = event.getClick() == ClickType.SHIFT_RIGHT ? 3 : (event.isRightClick() ? 2 : 1);
            if (!islandService.buyGrowthBoost(island, holder.relChunkX(), holder.relChunkZ(), tier)) {
                player.sendMessage(ChatColor.RED + "Wachstumsboost konnte nicht gekauft werden.");
                return;
            }
            player.sendMessage(ChatColor.GREEN + "Wachstum Stufe " + tier + " gekauft.");
            player.openInventory(coreService.createIslandShopMenu(player, island));
            return;
        }
        if (raw == 16) {
            if (!canUseIslandShop(player, island)) {
                return;
            }
            int amount = event.isShiftClick() ? 16 : 1;
            coreService.fillExperienceBottles(player, island, amount);
            player.openInventory(coreService.createIslandShopMenu(player, island));
            return;
        }
        if (raw == 30) {
            if (!canUseIslandShop(player, island)) {
                return;
            }
            player.openInventory(coreService.createNightVisionShopMenu(player, island));
        }
    }

    private void handleTimeModeShopMenuClick(InventoryClickEvent event, Player player, CoreService.TimeModeShopInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        if (!canUseIslandShop(player, island)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw == 40) {
            player.openInventory(coreService.createIslandShopMenu(player, island));
            return;
        }
        IslandService.IslandTimeMode target = switch (raw) {
            case 11 -> IslandService.IslandTimeMode.DAY;
            case 13 -> IslandService.IslandTimeMode.SUNSET;
            case 15 -> IslandService.IslandTimeMode.MIDNIGHT;
            case 22 -> IslandService.IslandTimeMode.NORMAL;
            default -> null;
        };
        if (target == null) return;
        IslandService.IslandTimeMode current = islandService.getIslandTimeMode(island);
        if (current == target) {
            player.sendMessage(ChatColor.YELLOW + "Dieser Zeitmodus ist bereits aktiv.");
            player.openInventory(coreService.createTimeModeShopMenu(island, holder.backTarget()));
            return;
        }
        long cost = islandService.getTimeModeChangeCost();
        if (!islandService.spendStoredExperience(island, cost)) {
            player.sendMessage(ChatColor.RED + "Nicht genug Core-Erfahrung. Ben\u00f6tigt: " + cost);
            return;
        }
        islandService.setIslandTimeMode(island, target);
        player.sendMessage(ChatColor.GREEN + "Zeitmodus gesetzt: " + islandService.islandTimeModeLabel(target) + " (Kosten: " + cost + ")");
        player.openInventory(coreService.createTimeModeShopMenu(island, holder.backTarget()));
    }

    private void handleWeatherShopMenuClick(InventoryClickEvent event, Player player, CoreService.WeatherShopInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        if (!canUseIslandShop(player, island)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw == 40) {
            player.openInventory(coreService.createIslandShopMenu(player, island));
            return;
        }
        IslandService.IslandWeatherMode target = switch (raw) {
            case 11 -> IslandService.IslandWeatherMode.CLEAR;
            case 13 -> IslandService.IslandWeatherMode.RAIN;
            case 15 -> IslandService.IslandWeatherMode.THUNDER;
            case 22 -> IslandService.IslandWeatherMode.NORMAL;
            default -> null;
        };
        IslandService.SnowWeatherMode snowTarget = switch (raw) {
            case 29 -> IslandService.SnowWeatherMode.ALLOW;
            case 33 -> IslandService.SnowWeatherMode.BLOCK;
            default -> null;
        };
        if (snowTarget != null) {
            if (islandService.getIslandSnowMode(island) == snowTarget) {
                player.sendMessage(ChatColor.YELLOW + "Dieser Schnee-Modus ist bereits aktiv.");
                player.openInventory(coreService.createWeatherShopMenu(island, holder.backTarget()));
                return;
            }
            long cost = islandService.getWeatherModeChangeCost();
            if (!islandService.spendStoredExperience(island, cost)) {
                player.sendMessage(ChatColor.RED + "Nicht genug Core-Erfahrung. Benötigt: " + cost);
                return;
            }
            islandService.setIslandSnowMode(island, snowTarget);
            if (snowTarget == IslandService.SnowWeatherMode.BLOCK) {
                islandService.clearWeatherSnowForIsland(island);
            }
            player.sendMessage(ChatColor.GREEN + "Schnee-Modus gesetzt: " + islandService.snowWeatherModeLabel(snowTarget) + " (Kosten: " + cost + ")");
            player.openInventory(coreService.createWeatherShopMenu(island, holder.backTarget()));
            return;
        }
        if (target == null) return;
        IslandService.IslandWeatherMode current = islandService.getIslandWeatherMode(island);
        if (current == target) {
            player.sendMessage(ChatColor.YELLOW + "Dieser Wettermodus ist bereits aktiv.");
            player.openInventory(coreService.createWeatherShopMenu(island, holder.backTarget()));
            return;
        }
        long cost = islandService.getWeatherModeChangeCost();
        if (!islandService.spendStoredExperience(island, cost)) {
            player.sendMessage(ChatColor.RED + "Nicht genug Core-Erfahrung. Benötigt: " + cost);
            return;
        }
        islandService.setIslandWeatherMode(island, target);
        player.sendMessage(ChatColor.GREEN + "Wetter gesetzt: " + islandService.islandWeatherModeLabel(target) + " (Kosten: " + cost + ")");
        player.openInventory(coreService.createWeatherShopMenu(island, holder.backTarget()));
    }

    private void handleNightVisionShopMenuClick(InventoryClickEvent event, Player player, CoreService.NightVisionShopInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null || !islandService.hasBuildAccess(player.getUniqueId(), island)) return;
        int raw = event.getRawSlot();
        if (raw == 40) {
            player.openInventory(coreService.createIslandShopMenu(player, island));
            return;
        }
        if (raw == 11) {
            if (!canUseIslandShop(player, island)) {
                return;
            }
            long cost = islandService.getNightVisionCost(false);
            if (!islandService.buyChunkNightVision(island, holder.relChunkX(), holder.relChunkZ())) {
                player.sendMessage(ChatColor.RED + "Chunk-Nachtsicht konnte nicht gekauft werden.");
                return;
            }
            player.sendMessage(ChatColor.GREEN + "Chunk-Nachtsicht aktiviert. Kosten: " + cost);
            player.openInventory(coreService.createNightVisionShopMenu(player, island));
            return;
        }
        if (raw == 15) {
            if (!canUseIslandShop(player, island)) {
                return;
            }
            long cost = islandService.getNightVisionCost(true);
            if (!islandService.buyIslandNightVision(island)) {
                player.sendMessage(ChatColor.RED + "Inselweite Nachtsicht konnte nicht gekauft werden.");
                return;
            }
            player.sendMessage(ChatColor.GREEN + "Inselweite Nachtsicht aktiviert. Kosten: " + cost);
            player.openInventory(coreService.createNightVisionShopMenu(player, island));
            return;
        }
        if (raw == 29) {
            if (!canUseIslandShop(player, island)) {
                return;
            }
            if (!islandService.disableChunkNightVision(island, holder.relChunkX(), holder.relChunkZ())) {
                player.sendMessage(ChatColor.RED + "Chunk-Nachtsicht war nicht aktiv.");
                return;
            }
            player.sendMessage(ChatColor.YELLOW + "Chunk-Nachtsicht deaktiviert.");
            player.openInventory(coreService.createNightVisionShopMenu(player, island));
            return;
        }
        if (raw == 33) {
            if (!canUseIslandShop(player, island)) {
                return;
            }
            if (!islandService.disableIslandNightVision(island)) {
                player.sendMessage(ChatColor.RED + "Inselweite Nachtsicht war nicht aktiv.");
                return;
            }
            player.sendMessage(ChatColor.YELLOW + "Inselweite Nachtsicht deaktiviert.");
            player.openInventory(coreService.createNightVisionShopMenu(player, island));
        }
    }

    private boolean canUseIslandShop(Player player, IslandData island) {
        if (player == null || island == null) return false;
        if (player.isOp() || islandService.isIslandOwner(island, player.getUniqueId())) {
            return true;
        }
        player.sendMessage(ISLAND_SHOP_PERMISSION_MESSAGE);
        return false;
    }

    private boolean canUseParcelShop(Player player, IslandData island, ParcelData parcel) {
        if (player == null || island == null || parcel == null) return false;
        if (player.isOp() || islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
            return true;
        }
        player.sendMessage(ChatColor.RED + "Nur Master, Owner oder Plot-Owner können im GS-Shop kaufen.");
        return false;
    }

    private void handleVisitorSettingsClick(InventoryClickEvent event, Player player, UUID islandOwner) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(islandOwner).orElse(null);
        if (island == null || !islandService.isIslandOwner(island, player.getUniqueId())) return;
        if (event.getRawSlot() == 44) {
            player.openInventory(coreService.createIslandMenu(player, island));
            return;
        }
        if (!toggleAccessSetting(island.getIslandVisitorSettings(), accessSettingIndex(event.getRawSlot()))) {
            return;
        }
        islandService.save();
        player.openInventory(coreService.createVisitorSettingsMenu(island));
    }

    private void handleParcelMenuClick(InventoryClickEvent event, Player player, CoreService.ParcelInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        var targetParcel = resolveHolderParcel(island, holder.parcelKey(), holder.relChunkX(), holder.relChunkZ());
        boolean hasParcelAccess = targetParcel != null && islandService.isParcelUser(island, targetParcel, player.getUniqueId());
        if (!islandService.hasBuildAccess(player.getUniqueId(), island) && !hasParcelAccess) return;
        switch (event.getRawSlot()) {
            case 22 -> {
                var parcel = targetParcel;
                if (parcel == null) {
                    player.getInventory().addItem(islandService.createPlotWand());
                    player.sendMessage(ChatColor.GREEN + "Grundst\u00fccks-Stab erhalten.");
                    player.sendMessage(ChatColor.GRAY + "Setze Pos1/Pos2 und nutze /is plot create.");
                } else if (islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    coreService.beginParcelRenameInput(player, island, parcel);
                    return;
                }
            }
            case 10 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    parcel.setSpawn(player.getLocation().clone());
                    islandService.save();
                    player.sendMessage(ChatColor.GREEN + "GS-Spawn gesetzt.");
                }
            }
            case 11 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    player.openInventory(coreService.createParcelMemberSettingsMenu(island, holder.relChunkX(), holder.relChunkZ()));
                    return;
                }
            }
            case 12 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    player.openInventory(coreService.createParcelVisitorSettingsMenu(island, holder.relChunkX(), holder.relChunkZ()));
                    return;
                }
            }
            case 14 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    player.openInventory(coreService.createParcelMembersMenu(player, island, holder.relChunkX(), holder.relChunkZ(), IslandService.ParcelRole.OWNER, 0));
                    return;
                }
            }
            case 16 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    player.openInventory(coreService.createParcelMembersMenu(player, island, holder.relChunkX(), holder.relChunkZ(), IslandService.ParcelRole.MEMBER, 0));
                    return;
                }
            }
            case 20 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    player.openInventory(coreService.createParcelMarketMenu(island, holder.relChunkX(), holder.relChunkZ(), openParcelMarketInRentMode(parcel)));
                    return;
                }
            }
            case 24 -> {
                var parcel = targetParcel;
                if (parcel != null && canUseParcelShop(player, island, parcel)) {
                    player.openInventory(coreService.createParcelShopMenu(island, holder.relChunkX(), holder.relChunkZ()));
                    return;
                }
            }
            case 28 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    player.openInventory(coreService.createParcelModerationMenu(player, island, holder.relChunkX(), holder.relChunkZ(), CoreService.ParcelModerationAction.KICK, 0));
                    return;
                }
            }
            case 30 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    player.openInventory(coreService.createParcelModerationMenu(player, island, holder.relChunkX(), holder.relChunkZ(), CoreService.ParcelModerationAction.BAN, 0));
                    return;
                }
            }
            case 32 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    player.openInventory(coreService.createParcelModerationMenu(player, island, holder.relChunkX(), holder.relChunkZ(), CoreService.ParcelModerationAction.UNBAN, 0));
                    return;
                }
            }
            case 33 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    boolean enabled = !parcel.isGamesEnabled();
                    if (islandService.setParcelGames(island, parcel, player.getUniqueId(), enabled)) {
                        if (!enabled) {
                            playerListener.resetParcelSnowballFight(island, parcel);
                        }
                        player.sendMessage((enabled ? ChatColor.AQUA : ChatColor.GREEN) + "GS-Games " + (enabled ? "aktiviert." : "deaktiviert."));
                    }
                }
            }
            case 34 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    boolean enabled = !parcel.isPvpEnabled();
                    if (islandService.setParcelPvp(island, parcel, player.getUniqueId(), enabled)) {
                        player.sendMessage((enabled ? ChatColor.RED : ChatColor.GREEN) + "GS-PvP " + (enabled ? "aktiviert." : "deaktiviert."));
                        broadcastSkyCityChat(ChatColor.GOLD + player.getName() + ChatColor.GRAY + " hat GS-PvP auf " + islandService.getParcelDisplayName(parcel) + " " + (enabled ? ChatColor.RED + "aktiviert" : ChatColor.GREEN + "deaktiviert") + ChatColor.GRAY + ".");
                    }
                }
            }
            case 35 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    boolean enabled = !parcel.isPveEnabled();
                    if (enabled) {
                        var validation = islandService.validateParcelPve(island, parcel);
                        if (validation.isPresent()) {
                            player.sendMessage(ChatColor.RED + validation.get());
                            return;
                        }
                    }
                    if (islandService.setParcelPve(island, parcel, player.getUniqueId(), enabled)) {
                        player.sendMessage((enabled ? ChatColor.DARK_GREEN : ChatColor.GREEN) + "GS-PvE " + (enabled ? "aktiviert." : "deaktiviert."));
                    }
                }
            }
            case 36 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    if (islandService.resetParcelPvpStats(island, parcel, player.getUniqueId())) {
                        player.sendMessage(ChatColor.YELLOW + "GS-PvP-Rangliste wurde zur\u00fcckgesetzt.");
                        broadcastSkyCityChat(ChatColor.GOLD + player.getName() + ChatColor.GRAY + " hat die PvP-Rangliste von " + islandService.getParcelDisplayName(parcel) + " zur\u00fcckgesetzt.");
                    } else {
                        player.sendMessage(ChatColor.GRAY + "Es gibt noch keine PvP-Eintr\u00e4ge auf diesem GS.");
                    }
                }
            }
            case 38 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    player.openInventory(coreService.createParcelMembersMenu(player, island, holder.relChunkX(), holder.relChunkZ(), IslandService.ParcelRole.PVP, 0));
                    return;
                }
            }
            case 39 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    boolean enabled = !parcel.isPvpCompassEnabled();
                    if (islandService.setParcelPvpCompassEnabled(island, parcel, player.getUniqueId(), enabled)) {
                        player.sendMessage((enabled ? ChatColor.AQUA : ChatColor.RED) + "PvP-Kompass " + (enabled ? "aktiviert." : "deaktiviert."));
                    }
                }
            }
            case 41 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    boolean enabled = !parcel.isCtfEnabled();
                    if (enabled && !parcel.isGamesEnabled()) {
                        player.sendMessage(ChatColor.RED + "CTF ben\u00f6tigt zuerst GS-Games.");
                        return;
                    }
                    if (islandService.setParcelCtfEnabled(island, parcel, player.getUniqueId(), enabled)) {
                        playerListener.resetParcelCtf(island, parcel);
                        player.sendMessage((enabled ? ChatColor.GOLD : ChatColor.GREEN) + "Capture The Flag " + (enabled ? "aktiviert." : "deaktiviert."));
                    }
                }
            }
            case 42 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    playerListener.resetParcelCtf(island, parcel);
                    player.sendMessage(ChatColor.YELLOW + "CTF wurde zur\u00fcckgesetzt.");
                }
            }
            case 44 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    int delta = event.isShiftClick() ? -300 : -30;
                    int targetSeconds = parcel.getCountdownDurationSeconds() + delta;
                    if (islandService.setParcelCountdownDurationSeconds(island, parcel, player.getUniqueId(), targetSeconds)) {
                        player.sendMessage(ChatColor.YELLOW + "Countdown-Zeit: " + ChatColor.WHITE + formatParcelCountdownSeconds(parcel.getCountdownDurationSeconds()));
                    }
                }
            }
            case 45 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    int delta = event.isShiftClick() ? 300 : 30;
                    int targetSeconds = parcel.getCountdownDurationSeconds() + delta;
                    if (islandService.setParcelCountdownDurationSeconds(island, parcel, player.getUniqueId(), targetSeconds)) {
                        player.sendMessage(ChatColor.YELLOW + "Countdown-Zeit: " + ChatColor.WHITE + formatParcelCountdownSeconds(parcel.getCountdownDurationSeconds()));
                    }
                }
            }
            case 46 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    if (islandService.startParcelCountdown(island, parcel, player.getUniqueId())) {
                        playerListener.startParcelCountdown(island, parcel);
                        player.sendMessage(ChatColor.GOLD + "Parcel-Countdown gestartet.");
                    }
                }
            }
            case 47 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    if (islandService.stopParcelCountdown(island, parcel, player.getUniqueId())) {
                        playerListener.stopParcelCountdown(island, parcel);
                        player.sendMessage(ChatColor.RED + "Parcel-Countdown gestoppt.");
                    }
                }
            }
            case 50 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    boolean enabled = !parcel.isSnowballFightEnabled();
                    if (enabled && !parcel.isGamesEnabled()) {
                        player.sendMessage(ChatColor.RED + "Schneeballschlacht benötigt zuerst GS-Games.");
                        return;
                    }
                    if (islandService.setParcelSnowballFightEnabled(island, parcel, player.getUniqueId(), enabled)) {
                        if (!enabled) {
                            playerListener.resetParcelSnowballFight(island, parcel);
                        }
                        player.sendMessage((enabled ? ChatColor.AQUA : ChatColor.GREEN) + "Schneeballschlacht " + (enabled ? "aktiviert." : "deaktiviert."));
                    }
                }
            }
            case 51 -> {
                var parcel = targetParcel;
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    playerListener.resetParcelSnowballFight(island, parcel);
                    player.sendMessage(ChatColor.YELLOW + "Schneeball-Teamwertung wurde zurückgesetzt.");
                }
            }
            case 49 -> {
                player.openInventory(coreService.createIslandMenu(player, island));
                return;
            }
            default -> {
            }
        }
        openParcelMenu(player, island, holder.relChunkX(), holder.relChunkZ());
    }

    private void handleParcelShopMenuClick(InventoryClickEvent event, Player player, CoreService.ParcelShopInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        ParcelData parcel = resolveHolderParcel(island, holder.parcelKey(), holder.relChunkX(), holder.relChunkZ());
        if (!canUseParcelShop(player, island, parcel)) return;
        switch (event.getRawSlot()) {
            case 10 -> player.openInventory(coreService.createParcelBiomeMenu(island, holder.relChunkX(), holder.relChunkZ(), 0));
            case 12 -> player.openInventory(coreService.createParcelWeatherShopMenu(island, holder.relChunkX(), holder.relChunkZ()));
            case 14 -> player.openInventory(coreService.createParcelTimeModeShopMenu(island, holder.relChunkX(), holder.relChunkZ()));
            case 30 -> player.openInventory(coreService.createParcelNightVisionShopMenu(island, holder.relChunkX(), holder.relChunkZ()));
            case 40 -> player.openInventory(coreService.createParcelMenu(player, island, holder.relChunkX(), holder.relChunkZ()));
            default -> {
            }
        }
    }

    private void handleParcelBiomeMenuClick(InventoryClickEvent event, Player player, CoreService.ParcelBiomeInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        ParcelData parcel = resolveHolderParcel(island, holder.parcelKey(), holder.relChunkX(), holder.relChunkZ());
        if (!canUseParcelShop(player, island, parcel)) return;
        int raw = event.getRawSlot();
        if (raw == 45) {
            player.openInventory(coreService.createParcelShopMenu(island, holder.relChunkX(), holder.relChunkZ()));
            return;
        }
        if (raw == 48 && holder.page() > 0) {
            player.openInventory(coreService.createParcelBiomeMenu(island, holder.relChunkX(), holder.relChunkZ(), holder.page() - 1));
            return;
        }
        if (raw == 50) {
            player.openInventory(coreService.createParcelBiomeMenu(island, holder.relChunkX(), holder.relChunkZ(), holder.page() + 1));
            return;
        }
        if (raw < 0 || raw >= 45) return;
        int index = holder.page() * 45 + raw;
        if (index < 0 || index >= coreService.getBiomeOptionCount()) return;
        Biome biome = coreService.biomeOptionAt(index);
        if (biome == null) return;
        long cost = islandService.getBiomeChangeCost(false);
        if (!islandService.spendStoredExperience(island, cost)) {
            player.sendMessage(ChatColor.RED + "Nicht genug Core-Erfahrung. Benötigt: " + cost);
            return;
        }
        if (!islandService.setBiomeForParcel(island, parcel, biome)) {
            player.sendMessage(ChatColor.RED + "Parcel-Biom konnte nicht gesetzt werden.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "Parcel-Biom gesetzt: " + coreService.biomeDisplayNameDe(biome) + " (Kosten: " + cost + ")");
        triggerBiomeVisualReload(player, coreService.createParcelBiomeMenu(island, holder.relChunkX(), holder.relChunkZ(), holder.page()));
    }

    private void triggerBiomeVisualReload(Player player, org.bukkit.inventory.Inventory nextMenu) {
        org.bukkit.Location original = player.getLocation();
        player.teleport(islandService.getSpawnLocation());
        org.bukkit.Bukkit.getScheduler().runTaskLater(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()), () -> {
            if (player.isOnline()) {
                player.teleport(original);
                if (nextMenu != null) player.openInventory(nextMenu);
            }
        }, 5L);
    }

    private void handleParcelTimeModeShopMenuClick(InventoryClickEvent event, Player player, CoreService.ParcelTimeModeShopInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        ParcelData parcel = resolveHolderParcel(island, holder.parcelKey(), holder.relChunkX(), holder.relChunkZ());
        if (!canUseParcelShop(player, island, parcel)) return;
        int raw = event.getRawSlot();
        if (raw == 40) {
            player.openInventory(coreService.createParcelShopMenu(island, holder.relChunkX(), holder.relChunkZ()));
            return;
        }
        IslandService.IslandTimeMode target = switch (raw) {
            case 11 -> IslandService.IslandTimeMode.DAY;
            case 13 -> IslandService.IslandTimeMode.SUNSET;
            case 15 -> IslandService.IslandTimeMode.MIDNIGHT;
            case 22 -> IslandService.IslandTimeMode.NORMAL;
            default -> null;
        };
        if (target == null) return;
        if (islandService.getParcelTimeMode(parcel) == target) {
            player.sendMessage(ChatColor.YELLOW + "Dieser Zeitmodus ist bereits auf dem Grundstück aktiv.");
            return;
        }
        long cost = islandService.getTimeModeChangeCost();
        if (!islandService.spendStoredExperience(island, cost)) {
            player.sendMessage(ChatColor.RED + "Nicht genug Core-Erfahrung. Benötigt: " + cost);
            return;
        }
        if (!islandService.setParcelTimeMode(island, parcel, player.getUniqueId(), target)) {
            player.sendMessage(ChatColor.RED + "Parcel-Zeitmodus konnte nicht gesetzt werden.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "Parcel-Zeit gesetzt: " + islandService.islandTimeModeLabel(target) + " (Kosten: " + cost + ")");
        player.openInventory(coreService.createParcelTimeModeShopMenu(island, holder.relChunkX(), holder.relChunkZ()));
    }

    private void handleParcelWeatherShopMenuClick(InventoryClickEvent event, Player player, CoreService.ParcelWeatherShopInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        ParcelData parcel = resolveHolderParcel(island, holder.parcelKey(), holder.relChunkX(), holder.relChunkZ());
        if (!canUseParcelShop(player, island, parcel)) return;
        int raw = event.getRawSlot();
        if (raw == 40) {
            player.openInventory(coreService.createParcelShopMenu(island, holder.relChunkX(), holder.relChunkZ()));
            return;
        }
        IslandService.IslandWeatherMode target = switch (raw) {
            case 11 -> IslandService.IslandWeatherMode.CLEAR;
            case 13 -> IslandService.IslandWeatherMode.RAIN;
            case 15 -> IslandService.IslandWeatherMode.THUNDER;
            case 22 -> IslandService.IslandWeatherMode.NORMAL;
            default -> null;
        };
        IslandService.SnowWeatherMode snowTarget = switch (raw) {
            case 29 -> IslandService.SnowWeatherMode.ALLOW;
            case 33 -> IslandService.SnowWeatherMode.BLOCK;
            default -> null;
        };
        if (snowTarget != null) {
            if (islandService.getParcelSnowMode(parcel) == snowTarget) {
                player.sendMessage(ChatColor.YELLOW + "Dieser Schnee-Modus ist bereits auf dem Grundstück aktiv.");
                return;
            }
            long cost = islandService.getWeatherModeChangeCost();
            if (!islandService.spendStoredExperience(island, cost)) {
                player.sendMessage(ChatColor.RED + "Nicht genug Core-Erfahrung. Benötigt: " + cost);
                return;
            }
            if (!islandService.setParcelSnowMode(island, parcel, player.getUniqueId(), snowTarget)) {
                player.sendMessage(ChatColor.RED + "Parcel-Schnee-Modus konnte nicht gesetzt werden.");
                return;
            }
            if (snowTarget == IslandService.SnowWeatherMode.BLOCK) {
                islandService.clearWeatherSnowForParcel(island, parcel);
            }
            player.sendMessage(ChatColor.GREEN + "Parcel-Schnee gesetzt: " + islandService.snowWeatherModeLabel(snowTarget) + " (Kosten: " + cost + ")");
            player.openInventory(coreService.createParcelWeatherShopMenu(island, holder.relChunkX(), holder.relChunkZ()));
            return;
        }
        if (target == null) return;
        if (islandService.getParcelWeatherMode(parcel) == target) {
            player.sendMessage(ChatColor.YELLOW + "Dieser Wettermodus ist bereits auf dem Grundstück aktiv.");
            return;
        }
        long cost = islandService.getWeatherModeChangeCost();
        if (!islandService.spendStoredExperience(island, cost)) {
            player.sendMessage(ChatColor.RED + "Nicht genug Core-Erfahrung. Benötigt: " + cost);
            return;
        }
        if (!islandService.setParcelWeatherMode(island, parcel, player.getUniqueId(), target)) {
            player.sendMessage(ChatColor.RED + "Parcel-Wetter konnte nicht gesetzt werden.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "Parcel-Wetter gesetzt: " + islandService.islandWeatherModeLabel(target) + " (Kosten: " + cost + ")");
        player.openInventory(coreService.createParcelWeatherShopMenu(island, holder.relChunkX(), holder.relChunkZ()));
    }

    private void handleParcelNightVisionShopMenuClick(InventoryClickEvent event, Player player, CoreService.ParcelNightVisionShopInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        ParcelData parcel = resolveHolderParcel(island, holder.parcelKey(), holder.relChunkX(), holder.relChunkZ());
        if (!canUseParcelShop(player, island, parcel)) return;
        int raw = event.getRawSlot();
        if (raw == 40) {
            player.openInventory(coreService.createParcelShopMenu(island, holder.relChunkX(), holder.relChunkZ()));
            return;
        }
        if (raw == 11) {
            long cost = islandService.getNightVisionCost(false);
            if (!islandService.buyParcelNightVision(island, parcel, player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Parcel-Nachtsicht konnte nicht gekauft werden.");
                return;
            }
            player.sendMessage(ChatColor.GREEN + "Parcel-Nachtsicht aktiviert. Kosten: " + cost);
            player.openInventory(coreService.createParcelNightVisionShopMenu(island, holder.relChunkX(), holder.relChunkZ()));
            return;
        }
        if (raw == 29) {
            if (!islandService.disableParcelNightVision(island, parcel, player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Parcel-Nachtsicht war nicht aktiv.");
                return;
            }
            player.sendMessage(ChatColor.YELLOW + "Parcel-Nachtsicht deaktiviert.");
            player.openInventory(coreService.createParcelNightVisionShopMenu(island, holder.relChunkX(), holder.relChunkZ()));
        }
    }

    private ParcelData resolveHolderParcel(IslandData island, String parcelKey, int relChunkX, int relChunkZ) {
        ParcelData parcel = islandService.getParcelByKey(island, parcelKey);
        return parcel != null ? parcel : islandService.getParcel(island, relChunkX, relChunkZ);
    }

    private String formatParcelCountdownSeconds(int totalSeconds) {
        int seconds = Math.max(0, totalSeconds);
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int restSeconds = seconds % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + restSeconds + "s";
        return restSeconds + "s";
    }

    private void handleParcelMarketMenuClick(InventoryClickEvent event, Player player, CoreService.ParcelMarketInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        var parcel = islandService.getParcel(island, holder.relChunkX(), holder.relChunkZ());
        if (parcel == null || !islandService.isParcelOwner(island, parcel, player.getUniqueId())) return;
        boolean rentMode = holder.rentMode();
        switch (event.getRawSlot()) {
            case 10 -> {
                player.openInventory(coreService.createParcelMarketMenu(island, holder.relChunkX(), holder.relChunkZ(), !rentMode));
                return;
            }
            case 12 -> adjustParcelMarketPrice(island, parcel, player, rentMode, event, 1L);
            case 14 -> adjustParcelMarketPrice(island, parcel, player, rentMode, event, 10L);
            case 16 -> {
                long step = event.isShiftClick() ? 1000L : 100L;
                adjustParcelMarketPrice(island, parcel, player, rentMode, event, step);
            }
            case 19 -> {
                if (!rentMode) {
                    return;
                }
                int step = event.isShiftClick() ? 10 : 1;
                int next = event.isLeftClick() ? parcel.getRentDurationAmount() + step : Math.max(0, parcel.getRentDurationAmount() - step);
                islandService.configureParcelRentOffer(island, parcel, player.getUniqueId(), parcel.getRentPrice(), next, parcel.getRentDurationUnit(), parcel.isRentOfferEnabled());
            }
            case 21 -> {
                if (!rentMode) {
                    return;
                }
                ParcelData.RentDurationUnit nextUnit = switch (parcel.getRentDurationUnit()) {
                    case MINUTES -> ParcelData.RentDurationUnit.HOURS;
                    case HOURS -> ParcelData.RentDurationUnit.DAYS;
                    case DAYS -> ParcelData.RentDurationUnit.MINUTES;
                };
                islandService.configureParcelRentOffer(island, parcel, player.getUniqueId(), parcel.getRentPrice(), parcel.getRentDurationAmount(), nextUnit, parcel.isRentOfferEnabled());
            }
            case 22 -> {
                if (!rentMode) {
                    boolean enable = !parcel.isSaleOfferEnabled();
                    if (enable && parcel.getSalePrice() <= 0L) {
                        player.sendMessage(ChatColor.RED + "Setze zuerst einen Verkaufspreis.");
                        return;
                    }
                    islandService.configureParcelSaleOffer(island, parcel, player.getUniqueId(), parcel.getSalePrice(), enable);
                } else {
                    boolean enable = !parcel.isRentOfferEnabled();
                    if (enable && (parcel.getRentPrice() <= 0L || parcel.getRentDurationAmount() <= 0)) {
                        player.sendMessage(ChatColor.RED + "Setze zuerst Mietpreis und Mietda\u00fcr.");
                        return;
                    }
                    islandService.configureParcelRentOffer(island, parcel, player.getUniqueId(), parcel.getRentPrice(), parcel.getRentDurationAmount(), parcel.getRentDurationUnit(), enable);
                }
            }
            case 24 -> {
                if (islandService.isParcelVaultAvailable()) {
                    ParcelData.MarketPaymentType nextType = parcel.getPaymentType() == ParcelData.MarketPaymentType.EXPERIENCE
                            ? ParcelData.MarketPaymentType.VAULT
                            : ParcelData.MarketPaymentType.EXPERIENCE;
                    islandService.configureParcelPaymentType(island, parcel, player.getUniqueId(), nextType);
                } else {
                    player.sendMessage(ChatColor.RED + "CraftTaler sind aktuell nicht verf\u00fcgbar.");
                }
            }
            case 25 -> {
                if (rentMode) {
                    if (islandService.manageParcelRent(island, parcel, player.getUniqueId(), false)) {
                        player.sendMessage(ChatColor.YELLOW + "Miete beendet.");
                    }
                } else {
                    if (islandService.manageParcelSale(island, parcel, player.getUniqueId(), false)) {
                        player.sendMessage(ChatColor.RED + "Plot enteignet.");
                    }
                }
            }
            case 26 -> {
                if (rentMode) {
                    if (islandService.manageParcelRent(island, parcel, player.getUniqueId(), true)) {
                        player.sendMessage(ChatColor.GREEN + "Miete storniert und erstattet.");
                    }
                } else {
                    if (islandService.manageParcelSale(island, parcel, player.getUniqueId(), true)) {
                        player.sendMessage(ChatColor.GREEN + "Kauf storniert und erstattet.");
                    }
                }
            }
            case 31 -> {
                openParcelMenu(player, island, holder.relChunkX(), holder.relChunkZ());
                return;
            }
            default -> {
                return;
            }
        }
        player.openInventory(coreService.createParcelMarketMenu(island, holder.relChunkX(), holder.relChunkZ(), rentMode));
    }

    private void adjustParcelMarketPrice(IslandData island, ParcelData parcel, Player player, boolean rentMode, InventoryClickEvent event, long step) {
        long current = rentMode ? parcel.getRentPrice() : parcel.getSalePrice();
        long next = event.isLeftClick() ? current + step : Math.max(0L, current - step);
        if (rentMode) {
            islandService.configureParcelRentOffer(island, parcel, player.getUniqueId(), next, parcel.getRentDurationAmount(), parcel.getRentDurationUnit(), parcel.isRentOfferEnabled());
        } else {
            islandService.configureParcelSaleOffer(island, parcel, player.getUniqueId(), next, parcel.isSaleOfferEnabled());
        }
    }

    private void handleParcelVisitorSettingsClick(InventoryClickEvent event, Player player, CoreService.ParcelVisitorSettingsInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        var parcel = islandService.getParcel(island, holder.relChunkX(), holder.relChunkZ());
        if (parcel == null || !islandService.isParcelOwner(island, parcel, player.getUniqueId())) return;
        if (event.getRawSlot() == 44) {
            openParcelMenu(player, island, holder.relChunkX(), holder.relChunkZ());
            return;
        }
        if (!toggleAccessSetting(parcel.getVisitorSettings(), accessSettingIndex(event.getRawSlot()))) {
            return;
        }
        islandService.save();
        player.openInventory(coreService.createParcelVisitorSettingsMenu(island, holder.relChunkX(), holder.relChunkZ()));
    }

    private void handleParcelMemberSettingsClick(InventoryClickEvent event, Player player, CoreService.ParcelMemberSettingsInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        var parcel = islandService.getParcel(island, holder.relChunkX(), holder.relChunkZ());
        if (parcel == null || !islandService.isParcelOwner(island, parcel, player.getUniqueId())) return;
        if (event.getRawSlot() == 44) {
            openParcelMenu(player, island, holder.relChunkX(), holder.relChunkZ());
            return;
        }
        if (toggleAccessSetting(parcel.getMemberSettings(), accessSettingIndex(event.getRawSlot()))) {
            islandService.save();
            player.openInventory(coreService.createParcelMemberSettingsMenu(island, holder.relChunkX(), holder.relChunkZ()));
            return;
        }
        switch (event.getRawSlot()) {
            case 36 -> parcel.setMemberAnimalBreed(!parcel.isMemberAnimalBreed());
            case 37 -> parcel.setMemberAnimalKill(!parcel.isMemberAnimalKill());
            case 38 -> parcel.setMemberAnimalKeepTwo(!parcel.isMemberAnimalKeepTwo());
            case 39 -> parcel.setMemberAnimalShear(!parcel.isMemberAnimalShear());
            default -> { return; }
        }
        islandService.save();
        player.openInventory(coreService.createParcelMemberSettingsMenu(island, holder.relChunkX(), holder.relChunkZ()));
    }

    private void handleTeleportMenuClick(InventoryClickEvent event, Player player, CoreService.TeleportInventoryHolder holder) {
        event.setCancelled(true);
        if (event.getRawSlot() == 49) {
            IslandData own = islandService.getIsland(player.getUniqueId()).orElse(null);
            if (own != null) player.openInventory(coreService.createIslandMenu(player, own));
            else {
                player.closeInventory();
                sendNoIslandHelp(player);
            }
            return;
        }
        if (event.getRawSlot() == 48 && holder.page() > 0) {
            player.openInventory(coreService.createTeleportMenu(player.getUniqueId(), holder.page() - 1, holder.filter()));
            return;
        }
        if (event.getRawSlot() == 50) {
            player.openInventory(coreService.createTeleportMenu(player.getUniqueId(), holder.page() + 1, holder.filter()));
            return;
        }
        if (event.getRawSlot() == 45) { player.openInventory(coreService.createTeleportMenu(player.getUniqueId(), 0, "all")); return; }
        if (event.getRawSlot() == 46) { player.openInventory(coreService.createTeleportMenu(player.getUniqueId(), 0, "islands")); return; }
        if (event.getRawSlot() == 47) { player.openInventory(coreService.createTeleportMenu(player.getUniqueId(), 0, "parcels")); return; }
        if (event.getRawSlot() == 51) { player.openInventory(coreService.createTeleportMenu(player.getUniqueId(), 0, "mine")); return; }
        if (event.getRawSlot() == 52) { player.openInventory(coreService.createTeleportMenu(player.getUniqueId(), 0, "warps")); return; }
        if (event.getRawSlot() < 0 || event.getRawSlot() > 44) return;
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta() || event.getCurrentItem().getItemMeta().getLore() == null) return;
        for (String line : event.getCurrentItem().getItemMeta().getLore()) {
            String plain = ChatColor.stripColor(line);
            if (plain == null) continue;
            if (plain.startsWith("island:") || plain.startsWith("parcel:") || plain.startsWith("warp:")) {
                for (IslandService.TeleportTarget target : islandService.getTeleportTargetsFor(player.getUniqueId())) {
                    if (target.id().equals(plain)) {
                        player.teleport(target.location());
                        player.closeInventory();
                        return;
                    }
                }
            }
        }
    }

    private void sendNoIslandHelp(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Du hast aktuell keine Insel.");
        player.sendMessage(ChatColor.GRAY + "Nutze " + ChatColor.AQUA + "/is create" + ChatColor.GRAY + " zum Erstellen.");
        player.sendMessage(ChatColor.GRAY + "Oder nutze " + ChatColor.AQUA + "/is islands" + ChatColor.GRAY + " f\u00fcr freie Slots in deiner N\u00e4he.");
        player.sendMessage(ChatColor.GRAY + "Danach kommst du mit " + ChatColor.AQUA + "/is home" + ChatColor.GRAY + " direkt zur Insel.");
    }

    private void handleIslandTrustMembersMenuClick(InventoryClickEvent event, Player player, CoreService.IslandTrustMembersInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null || !islandService.isIslandOwner(island, player.getUniqueId())) return;
        IslandService.TrustPermission permission;
        try {
            permission = IslandService.TrustPermission.valueOf(holder.permission());
        } catch (Exception ex) {
            permission = IslandService.TrustPermission.BUILD;
        }

        if (event.getRawSlot() == 49) {
            player.openInventory(coreService.createIslandMenu(player, island));
            return;
        }
        if (event.getRawSlot() == 48 && holder.page() > 0) {
            player.openInventory(coreService.createIslandTrustMenu(player, island, permission, holder.page() - 1, holder.filter()));
            return;
        }
        if (event.getRawSlot() == 50) {
            player.openInventory(coreService.createIslandTrustMenu(player, island, permission, holder.page() + 1, holder.filter()));
            return;
        }
        if (event.getRawSlot() == 45) { player.openInventory(coreService.createIslandTrustMenu(player, island, permission, 0, "all")); return; }
        if (event.getRawSlot() == 46) { player.openInventory(coreService.createIslandTrustMenu(player, island, permission, 0, "online")); return; }
        if (event.getRawSlot() == 47) { player.openInventory(coreService.createIslandTrustMenu(player, island, permission, 0, "members")); return; }
        if (event.getRawSlot() == 51) { player.openInventory(coreService.createIslandTrustMenu(player, island, permission, 0, "nonmembers")); return; }
        if (event.getRawSlot() == 53) {
            IslandService.TrustPermission next = switch (permission) {
                case BUILD -> IslandService.TrustPermission.CONTAINER;
                case CONTAINER -> IslandService.TrustPermission.REDSTONE;
                case REDSTONE -> IslandService.TrustPermission.ALL;
                case ALL -> IslandService.TrustPermission.BUILD;
            };
            player.openInventory(coreService.createIslandTrustMenu(player, island, next, 0, holder.filter()));
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() > 44) return;
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;
        String targetName = ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());
        if (targetName == null || targetName.isBlank()) return;
        var target = player.getServer().getOfflinePlayer(targetName);
        if (target == null || target.getUniqueId() == null || islandService.isPrimaryMaster(island, target.getUniqueId())) return;

        boolean changed = event.isRightClick()
                ? islandService.revokeMemberPermission(island, target.getUniqueId(), permission)
                : islandService.grantMemberPermission(island, target.getUniqueId(), permission);
        if (!changed) player.sendMessage(ChatColor.YELLOW + "Keine \u00c4nderung.");
        islandService.save();
        player.openInventory(coreService.createIslandTrustMenu(player, island, permission, holder.page(), holder.filter()));
    }

    private void handleIslandOwnersMenuClick(InventoryClickEvent event, Player player, CoreService.IslandOwnersInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        boolean canAddOwner = islandService.canAddOwner(island, player.getUniqueId()) || player.isOp();
        boolean canRemoveOwner = islandService.canRemoveOwner(island, player.getUniqueId()) || player.isOp();
        if (event.getRawSlot() == 49) {
            player.openInventory(coreService.createIslandMenu(player, island));
            return;
        }
        if (event.getRawSlot() == 48 && holder.page() > 0) {
            player.openInventory(coreService.createIslandOwnersMenu(player, island, holder.page() - 1, holder.filter()));
            return;
        }
        if (event.getRawSlot() == 50) {
            player.openInventory(coreService.createIslandOwnersMenu(player, island, holder.page() + 1, holder.filter()));
            return;
        }
        if (event.getRawSlot() == 45) { player.openInventory(coreService.createIslandOwnersMenu(player, island, 0, "all")); return; }
        if (event.getRawSlot() == 46) { player.openInventory(coreService.createIslandOwnersMenu(player, island, 0, "online")); return; }
        if (event.getRawSlot() == 47) { player.openInventory(coreService.createIslandOwnersMenu(player, island, 0, "members")); return; }
        if (event.getRawSlot() == 51) { player.openInventory(coreService.createIslandOwnersMenu(player, island, 0, "nonmembers")); return; }
        if (event.getRawSlot() < 0 || event.getRawSlot() > 44) return;
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;
        String targetName = ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());
        if (targetName == null || targetName.isBlank()) return;
        var target = player.getServer().getOfflinePlayer(targetName);
        if (target == null || target.getUniqueId() == null) return;

        boolean selfRemove = event.isRightClick() && target.getUniqueId().equals(player.getUniqueId());
        if (event.isLeftClick() && !canAddOwner) {
            player.sendMessage(ChatColor.RED + "Du kannst keine Owner hinzuf\u00fcgen.");
            player.openInventory(coreService.createIslandOwnersMenu(player, island, holder.page(), holder.filter()));
            return;
        }
        if (event.isRightClick() && !selfRemove && !canRemoveOwner) {
            player.sendMessage(ChatColor.RED + "Nur Master k\u00f6nnen andere Owner entfernen.");
            player.openInventory(coreService.createIslandOwnersMenu(player, island, holder.page(), holder.filter()));
            return;
        }
        boolean changed = event.isRightClick()
                ? islandService.revokeOwnerRole(island, player.getUniqueId(), target.getUniqueId())
                : islandService.grantOwnerRole(island, player.getUniqueId(), target.getUniqueId());
        if (!changed) {
            player.sendMessage(ChatColor.YELLOW + "Keine \u00c4nderung.");
        } else if (selfRemove) {
            player.sendMessage(ChatColor.YELLOW + "Du bist als Owner ausgetragen.");
        }
        player.openInventory(coreService.createIslandOwnersMenu(player, island, holder.page(), holder.filter()));
    }

    private void handleIslandMasterMenuClick(InventoryClickEvent event, Player player, CoreService.IslandMasterMenuInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
        if (event.getRawSlot() != 22 && (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR)) return;
        if (event.getRawSlot() != 22 && isDecorativePane(event.getCurrentItem())) return;
        switch (event.getRawSlot()) {
            case 11 -> {
                if (!islandService.canInviteMaster(island, player.getUniqueId()) && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "Nur Master k\u00f6nnen Master einladen.");
                    player.openInventory(coreService.createIslandMasterMenu(player, island));
                    return;
                }
                player.openInventory(coreService.createIslandMasterInviteMenu(player, island, 0));
            }
            case 13 -> {
                IslandData inviteIsland = islandService.getPendingMasterInviteIsland(player.getUniqueId());
                boolean ok = inviteIsland != null
                        && island.getOwner().equals(inviteIsland.getOwner())
                        && islandService.acceptMasterInvite(player.getUniqueId());
                if (ok) {
                    player.sendMessage(ChatColor.GREEN + "Du bist der Insel als Master beigetreten.");
                    if (inviteIsland != null && inviteIsland.getIslandSpawn() != null) player.teleport(inviteIsland.getIslandSpawn());
                } else {
                    player.sendMessage(ChatColor.RED + "Keine offene Master-Einladung.");
                }
                player.openInventory(coreService.createIslandMasterMenu(player, island));
            }
            case 15 -> {
                if (islandService.leaveMasterRole(island, player.getUniqueId())) {
                    player.sendMessage(ChatColor.YELLOW + "Du bist als Master von der Insel ausgetreten.");
                    player.teleport(islandService.getSpawnLocation());
                    sendNoIslandHelp(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Du bist auf keiner Insel als zus\u00e4tzlicher Master.");
                }
                IslandData own = islandService.getIsland(player.getUniqueId()).orElse(null);
                if (own != null) player.openInventory(coreService.createIslandMenu(player, own));
                else player.closeInventory();
            }
            case 22 -> player.openInventory(coreService.createIslandMenu(player, island));
            default -> { }
        }
    }

    private void handleIslandMasterInviteMenuClick(InventoryClickEvent event, Player player, CoreService.IslandMasterInviteInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null || !islandService.isIslandMaster(island, player.getUniqueId())) return;
        if (event.getRawSlot() == 49) {
            player.openInventory(coreService.createIslandMasterMenu(player, island));
            return;
        }
        if (event.getRawSlot() == 48 && holder.page() > 0) {
            player.openInventory(coreService.createIslandMasterInviteMenu(player, island, holder.page() - 1));
            return;
        }
        if (event.getRawSlot() == 50) {
            player.openInventory(coreService.createIslandMasterInviteMenu(player, island, holder.page() + 1));
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() > 44) return;
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta() || event.getCurrentItem().getItemMeta().getLore() == null) return;

        UUID targetId = null;
        for (String line : event.getCurrentItem().getItemMeta().getLore()) {
            String plain = ChatColor.stripColor(line);
            if (plain != null && plain.startsWith("uuid:")) {
                try {
                    targetId = UUID.fromString(plain.substring("uuid:".length()));
                } catch (IllegalArgumentException ignored) { }
                break;
            }
        }
        if (targetId == null) return;
        islandService.queueMasterInvite(island, player.getUniqueId(), targetId);
        var target = player.getServer().getPlayer(targetId);
        String targetName = target != null ? target.getName() : String.valueOf(targetId);
        player.sendMessage(ChatColor.GREEN + "Master-Einladung gesendet an " + targetName + ".");
        if (target != null) {
            target.sendMessage(ChatColor.GOLD + player.getName() + " m\u00f6chte dich als Master einladen.");
            target.sendMessage(ChatColor.YELLOW + "Nutze /is masteraccept zum Best\u00e4tigen.");
        }
        player.openInventory(coreService.createIslandMasterInviteMenu(player, island, holder.page()));
    }

    private void handleParcelMembersMenuClick(InventoryClickEvent event, Player player, CoreService.ParcelMembersInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        var parcel = islandService.getParcel(island, holder.relChunkX(), holder.relChunkZ());
        if (parcel == null || !islandService.isParcelOwner(island, parcel, player.getUniqueId())) return;
        IslandService.ParcelRole role = IslandService.ParcelRole.valueOf(holder.role());

        if (event.getRawSlot() == 49) {
            openParcelMenu(player, island, holder.relChunkX(), holder.relChunkZ());
            return;
        }
        if (event.getRawSlot() == 48 && holder.page() > 0) {
            player.openInventory(coreService.createParcelMembersMenu(player, island, holder.relChunkX(), holder.relChunkZ(), role, holder.page() - 1, holder.filter()));
            return;
        }
        if (event.getRawSlot() == 50) {
            player.openInventory(coreService.createParcelMembersMenu(player, island, holder.relChunkX(), holder.relChunkZ(), role, holder.page() + 1, holder.filter()));
            return;
        }
        if (event.getRawSlot() == 45) { player.openInventory(coreService.createParcelMembersMenu(player, island, holder.relChunkX(), holder.relChunkZ(), role, 0, "all")); return; }
        if (event.getRawSlot() == 46) { player.openInventory(coreService.createParcelMembersMenu(player, island, holder.relChunkX(), holder.relChunkZ(), role, 0, "online")); return; }
        if (event.getRawSlot() == 47) { player.openInventory(coreService.createParcelMembersMenu(player, island, holder.relChunkX(), holder.relChunkZ(), role, 0, "members")); return; }
        if (event.getRawSlot() == 51) { player.openInventory(coreService.createParcelMembersMenu(player, island, holder.relChunkX(), holder.relChunkZ(), role, 0, "nonmembers")); return; }
        if (event.getRawSlot() < 0 || event.getRawSlot() > 44) return;
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;
        String targetName = ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());
        if (targetName == null || targetName.isBlank()) return;
        var target = player.getServer().getOfflinePlayer(targetName);
        if (target == null || target.getUniqueId() == null) return;
        boolean changed = event.isRightClick()
                ? islandService.revokeParcelRole(island, parcel, player.getUniqueId(), target.getUniqueId(), role)
                : islandService.grantParcelRole(island, parcel, player.getUniqueId(), target.getUniqueId(), role);
        if (!changed) player.sendMessage(ChatColor.YELLOW + "Keine \u00c4nderung.");
        player.openInventory(coreService.createParcelMembersMenu(player, island, holder.relChunkX(), holder.relChunkZ(), role, holder.page(), holder.filter()));
    }

    private void handleParcelModerationMenuClick(InventoryClickEvent event, Player player, CoreService.ParcelModerationInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        var parcel = islandService.getParcel(island, holder.relChunkX(), holder.relChunkZ());
        if (parcel == null || !islandService.isParcelOwner(island, parcel, player.getUniqueId())) return;
        CoreService.ParcelModerationAction action = CoreService.ParcelModerationAction.from(holder.action());

        if (event.getRawSlot() == 49) {
            openParcelMenu(player, island, holder.relChunkX(), holder.relChunkZ());
            return;
        }
        if (event.getRawSlot() == 48 && holder.page() > 0) {
            player.openInventory(coreService.createParcelModerationMenu(player, island, holder.relChunkX(), holder.relChunkZ(), action, holder.page() - 1));
            return;
        }
        if (event.getRawSlot() == 50) {
            player.openInventory(coreService.createParcelModerationMenu(player, island, holder.relChunkX(), holder.relChunkZ(), action, holder.page() + 1));
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() > 44) return;
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta() || event.getCurrentItem().getItemMeta().getLore() == null) return;

        UUID targetId = null;
        for (String line : event.getCurrentItem().getItemMeta().getLore()) {
            String plain = ChatColor.stripColor(line);
            if (plain != null && plain.startsWith("uuid:")) {
                try {
                    targetId = UUID.fromString(plain.substring("uuid:".length()));
                } catch (IllegalArgumentException ignored) { }
                break;
            }
        }
        if (targetId == null) return;

        switch (action) {
            case KICK -> {
                Player target = player.getServer().getPlayer(targetId);
                if (target != null && islandService.kickFromParcel(island, parcel, target)) {
                    player.sendMessage(ChatColor.GREEN + "Spieler vom GS gekickt.");
                } else {
                    player.sendMessage(ChatColor.RED + "Spieler nicht auf diesem GS.");
                }
            }
            case BAN -> {
                if (islandService.setParcelBan(island, parcel, player.getUniqueId(), targetId, true)) {
                    Player target = player.getServer().getPlayer(targetId);
                    if (target != null) islandService.kickFromParcel(island, parcel, target);
                    player.sendMessage(ChatColor.GREEN + "Spieler vom GS gebannt.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Keine \u00c4nderung.");
                }
            }
            case UNBAN -> {
                if (islandService.setParcelBan(island, parcel, player.getUniqueId(), targetId, false)) {
                    player.sendMessage(ChatColor.YELLOW + "Spieler vom GS entbannt.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Keine \u00c4nderung.");
                }
            }
        }
        player.openInventory(coreService.createParcelModerationMenu(player, island, holder.relChunkX(), holder.relChunkZ(), action, holder.page()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof CoreService.IslandInventoryHolder
                || top.getHolder() instanceof CoreService.IslandOverviewInventoryHolder
                || top.getHolder() instanceof CoreService.UpgradeProgressInventoryHolder
                || top.getHolder() instanceof CoreService.ParcelsInventoryHolder
                || top.getHolder() instanceof CoreService.ChunkMapInventoryHolder
                || top.getHolder() instanceof CoreService.BiomeInventoryHolder
                || top.getHolder() instanceof CoreService.VisitorSettingsInventoryHolder
                || top.getHolder() instanceof CoreService.ParcelInventoryHolder
                || top.getHolder() instanceof CoreService.ParcelVisitorSettingsInventoryHolder
                || top.getHolder() instanceof CoreService.ParcelMemberSettingsInventoryHolder
                || top.getHolder() instanceof CoreService.ParcelMarketInventoryHolder
                || top.getHolder() instanceof CoreService.TeleportInventoryHolder
                || top.getHolder() instanceof CoreService.IslandTrustMembersInventoryHolder
                || top.getHolder() instanceof CoreService.IslandOwnersInventoryHolder
                || top.getHolder() instanceof CoreService.IslandMasterMenuInventoryHolder
                || top.getHolder() instanceof CoreService.IslandMasterInviteInventoryHolder
                || top.getHolder() instanceof CoreService.ParcelMembersInventoryHolder
                || top.getHolder() instanceof CoreService.ParcelModerationInventoryHolder
                || top.getHolder() instanceof CoreService.IslandShopInventoryHolder
                || top.getHolder() instanceof CoreService.TimeModeShopInventoryHolder
                || top.getHolder() instanceof CoreService.WeatherShopInventoryHolder
                || top.getHolder() instanceof CoreService.NightVisionShopInventoryHolder
                || top.getHolder() instanceof CoreService.ParcelShopInventoryHolder
                || top.getHolder() instanceof CoreService.ParcelBiomeInventoryHolder
                || top.getHolder() instanceof CoreService.ParcelTimeModeShopInventoryHolder
                || top.getHolder() instanceof CoreService.ParcelWeatherShopInventoryHolder
                || top.getHolder() instanceof CoreService.ParcelNightVisionShopInventoryHolder) {
            event.setCancelled(true);
            return;
        }
        if (!(top.getHolder() instanceof CoreService.CoreInventoryHolder)) return;
        for (int raw : event.getRawSlots()) {
            if (raw < top.getSize() && !CORE_INPUT_SLOTS.contains(raw)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        if (inv.getHolder() instanceof CoreService.CoreInventoryHolder holder) {
            IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
            if (island == null) return;
            coreService.processCoreInputInventory(inv, island, player);
            return;
        }
        if (inv.getHolder() instanceof ShulkerBox && inv.getLocation() != null) {
            IslandData island = islandService.findIslandByCoreLocation(inv.getLocation()).orElse(null);
            if (island == null) return;
            coreService.processCoreShulkerInventory(inv, island, player);
        }
    }
}



