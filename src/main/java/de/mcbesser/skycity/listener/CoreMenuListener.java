package de.mcbesser.skycity.listener;

import de.mcbesser.skycity.model.IslandData;
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

import java.util.Set;
import java.util.UUID;

public class CoreMenuListener implements Listener {
    private static final Set<Integer> CORE_INPUT_SLOTS = Set.of(27, 28, 29, 30, 31, 32, 33, 34, 35);

    private final IslandService islandService;
    private final CoreService coreService;
    private final ParticlePreviewService particlePreviewService;

    public CoreMenuListener(IslandService islandService, CoreService coreService, ParticlePreviewService particlePreviewService) {
        this.islandService = islandService;
        this.coreService = coreService;
        this.particlePreviewService = particlePreviewService;
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
        } else if (top.getHolder() instanceof CoreService.TeleportInventoryHolder holder) {
            handleTeleportMenuClick(event, player, holder);
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
        }
    }

    private void handleCoreMenuClick(InventoryClickEvent event, Player player, CoreService.CoreInventoryHolder holder) {
        if (event.getClickedInventory() == null) return;
        boolean inTop = event.getClickedInventory().equals(event.getView().getTopInventory());
        if (inTop && event.getRawSlot() != 11 && event.getRawSlot() != 12 && event.getRawSlot() != 13 && event.getRawSlot() != 15 && event.getRawSlot() != 16 && !CORE_INPUT_SLOTS.contains(event.getRawSlot())) event.setCancelled(true);
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
                player.openInventory(coreService.createIslandBlocksMenu(island));
            } else {
                player.openInventory(coreService.createBlockValueMenu(island));
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
        if (inTop && event.getRawSlot() == 12) {
            event.setCancelled(true);
            if (!islandService.hasContainerAccess(player.getUniqueId(), island)) {
                player.sendMessage(ChatColor.RED + "Keine Rechte.");
                return;
            }
            if (islandService.levelUp(island)) {
                player.sendMessage(ChatColor.GREEN + "Upgrade erfolgreich. Level " + island.getLevel());
            } else {
                player.sendMessage(ChatColor.RED + "Upgradebedingungen nicht erf\u00fcllt.");
                coreService.sendUpgradeStatusChat(player, island);
            }
            player.openInventory(coreService.createCoreMenu(player, island, coreLocation));
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
        if (event.getRawSlot() == 48 && holder.page() > 0) {
            player.openInventory(coreService.createUpgradeProgressMenu(island, holder.page() - 1));
            return;
        }
        if (event.getRawSlot() == 50) {
            player.openInventory(coreService.createUpgradeProgressMenu(island, holder.page() + 1));
        }
    }

    private void handleIslandMenuClick(InventoryClickEvent event, Player player, UUID islandOwner) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(islandOwner).orElse(null);
        if (island == null || !islandService.hasBuildAccess(player.getUniqueId(), island)) return;
        switch (event.getRawSlot()) {
            case 11 -> player.openInventory(coreService.createIslandSettingsMenu(island));
            case 13 -> player.openInventory(coreService.createChunkSettingsMenu(player, island));
            case 15 -> player.openInventory(coreService.createParcelsMenu(player, island));
            case 29 -> player.openInventory(coreService.createIslandShopMenu(player, island));
            case 31 -> player.openInventory(coreService.createTeleportMenu(player.getUniqueId(), 0));
            default -> {
            }
        }
    }

    private void handleIslandSettingsClick(InventoryClickEvent event, Player player, UUID islandOwner) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(islandOwner).orElse(null);
        if (island == null || !islandService.hasBuildAccess(player.getUniqueId(), island)) return;
        switch (event.getRawSlot()) {
            case 10 -> player.openInventory(coreService.createCoreMenu(player, island));
            case 11 -> player.openInventory(coreService.createIslandBlocksMenu(island));
            case 12 -> player.openInventory(coreService.createBlockValueMenu(island));
            case 13 -> {
                island.setIslandSpawn(player.getLocation().clone());
                islandService.save();
                player.sendMessage(ChatColor.GREEN + "Inselspawn gesetzt.");
                player.openInventory(coreService.createIslandSettingsMenu(island));
            }
            case 14 -> {
                if (!islandService.isIslandOwner(island, player.getUniqueId()) && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "Nur Inselbesitzer.");
                    player.openInventory(coreService.createIslandSettingsMenu(island));
                    return;
                }
                coreService.beginIslandTitleInput(player, island);
            }
            case 15 -> {
                if (!islandService.isIslandOwner(island, player.getUniqueId()) && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "Nur Inselbesitzer.");
                    player.openInventory(coreService.createIslandSettingsMenu(island));
                    return;
                }
                coreService.beginIslandWarpInput(player, island);
            }
            case 19 -> {
                int relX = islandService.relativeChunkX(island, player.getLocation().getChunk().getX());
                int relZ = islandService.relativeChunkZ(island, player.getLocation().getChunk().getZ());
                player.openInventory(coreService.createBiomeMenu(player, island, 0, relX, relZ, 0));
            }
            case 20 -> player.openInventory(coreService.createIslandTrustMenu(player, island, IslandService.TrustPermission.BUILD, 0));
            case 21 -> player.openInventory(coreService.createVisitorSettingsMenu(island));
            case 22 -> player.openInventory(coreService.createIslandOwnersMenu(player, island, 0, "all"));
            case 23 -> player.openInventory(coreService.createIslandMasterMenu(player, island));
            case 40 -> player.openInventory(coreService.createIslandMenu(player, island));
            default -> {
            }
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
                    player.sendMessage(ChatColor.YELLOW + "Du stehst aktuell in keinem GrundstÃ¼ck.");
                    player.openInventory(coreService.createParcelsMenu(player, island));
                    return;
                }
                int relX = islandService.relativeChunkX(island, player.getLocation().getChunk().getX());
                int relZ = islandService.relativeChunkZ(island, player.getLocation().getChunk().getZ());
                openParcelMenu(player, island, relX, relZ);
            }
            case 12 -> {
                player.sendMessage(ChatColor.GOLD + "GrundstÃ¼ck erstellen:");
                player.sendMessage(ChatColor.YELLOW + "1) GrundstÃ¼cks-Stab holen");
                player.sendMessage(ChatColor.YELLOW + "2) Pos1/Pos2 setzen");
                player.sendMessage(ChatColor.YELLOW + "3) /is plot create");
                player.openInventory(coreService.createParcelsMenu(player, island));
            }
            case 14 -> {
                player.getInventory().addItem(islandService.createPlotWand());
                player.sendMessage(ChatColor.GREEN + "GrundstÃ¼cks-Stab erhalten.");
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
                            ChatColor.YELLOW + "Nur f\u00fcr GS-Owner"
                    )));
            inventory.setItem(38, namedItem(
                    Material.IRON_SWORD,
                    ChatColor.RED + "PvP-Whitelist",
                    java.util.List.of(
                            ChatColor.GRAY + "Zul\u00e4ssige PvP-Spieler verwalten",
                            ChatColor.YELLOW + "Klick = GUI \u00f6ffnen"
                    )));
        }
        player.openInventory(inventory);
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
            player.openInventory(coreService.createIslandMenu(player, island));
        } else if (event.getRawSlot() == 48 && holder.page() > 0) {
            player.openInventory(coreService.createBlockValueMenu(island, holder.page() - 1));
        } else if (event.getRawSlot() == 50) {
            player.openInventory(coreService.createBlockValueMenu(island, holder.page() + 1));
        }
    }

    private void handleIslandBlocksMenuClick(InventoryClickEvent event, Player player, CoreService.IslandBlocksInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        if (event.getRawSlot() == 49) {
            player.openInventory(coreService.createIslandMenu(player, island));
        } else if (event.getRawSlot() == 48 && holder.page() > 0) {
            player.openInventory(coreService.createIslandBlocksMenu(island, holder.page() - 1));
        } else if (event.getRawSlot() == 50) {
            player.openInventory(coreService.createIslandBlocksMenu(island, holder.page() + 1));
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
                      player.sendMessage(ChatColor.YELLOW + "In diesem Chunk ist noch kein GrundstÃ¼ck.");
                      player.sendMessage(ChatColor.GRAY + "Nutze /is plot wand und /is plot create fÃ¼r freie Quader.");
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
                if (!islandService.isIslandOwner(island, player.getUniqueId()) && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "Nur Inselbesitzer.");
                    return;
                }
                if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                    long cost = islandService.getBiomeChangeCost(true);
                    if (island.getStoredExperience() < cost) {
                        player.sendMessage(ChatColor.RED + "Nicht genug Core-Erfahrung. BenÃ¶tigt: " + cost);
                        return;
                    }
                    if (!islandService.spendStoredExperience(island, cost)) {
                        player.sendMessage(ChatColor.RED + "Nicht genug Core-Erfahrung.");
                        return;
                    }
                    int changed = islandService.setBiomeForIsland(island, biome, true);
                    player.sendMessage(ChatColor.GREEN + "Biom gesetzt: " + coreService.biomeDisplayNameDe(biome)
                            + " fÃ¼r " + changed + " freigeschaltete Chunks. Kosten: " + cost);
                } else {
                    if (!islandService.isChunkUnlocked(island, holder.relChunkX(), holder.relChunkZ())) {
                        player.sendMessage(ChatColor.RED + "Ziel-Chunk ist gesperrt.");
                        return;
                    }
                    long cost = islandService.getBiomeChangeCost(false);
                    if (island.getStoredExperience() < cost) {
                        player.sendMessage(ChatColor.RED + "Nicht genug Core-Erfahrung. BenÃ¶tigt: " + cost);
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
                            + " fÃ¼r Chunk " + displayX + ":" + displayZ + ". Kosten: " + cost);
                }
                player.openInventory(coreService.createBiomeMenu(player, island, holder.page(), holder.relChunkX(), holder.relChunkZ(), holder.returnPage()));
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
            player.openInventory(coreService.createBiomeMenu(player, island, 0, holder.relChunkX(), holder.relChunkZ(), 0));
            return;
        }
        if (raw == 12) {
            if (!islandService.isIslandOwner(island, player.getUniqueId()) && !player.isOp()) {
                player.sendMessage(ChatColor.RED + "Nur Inselbesitzer.");
                return;
            }
            player.openInventory(coreService.createTimeModeShopMenu(island, "shop"));
            return;
        }
        if (raw == 14) {
            int tier = event.getClick() == ClickType.SHIFT_RIGHT ? 3 : (event.isRightClick() ? 2 : 1);
            if (!islandService.buyGrowthBoost(island, holder.relChunkX(), holder.relChunkZ(), tier)) {
                player.sendMessage(ChatColor.RED + "Wachstumsboost konnte nicht gekauft werden.");
                return;
            }
            player.sendMessage(ChatColor.GREEN + "Wachstumsboost gekauft: Stufe " + tier);
            player.openInventory(coreService.createIslandShopMenu(player, island));
            return;
        }
        if (raw == 16) {
            int amount = event.isShiftClick() ? 16 : 1;
            coreService.fillExperienceBottles(player, island, amount);
            player.openInventory(coreService.createIslandShopMenu(player, island));
        }
    }

    private void handleTimeModeShopMenuClick(InventoryClickEvent event, Player player, CoreService.TimeModeShopInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        if (!islandService.isIslandOwner(island, player.getUniqueId()) && !player.isOp()) {
            player.sendMessage(ChatColor.RED + "Nur Inselbesitzer.");
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
            player.sendMessage(ChatColor.RED + "Nicht genug Core-Erfahrung. BenÃ¶tigt: " + cost);
            return;
        }
        islandService.setIslandTimeMode(island, target);
        player.sendMessage(ChatColor.GREEN + "Zeitmodus gesetzt: " + islandService.islandTimeModeLabel(target) + " (Kosten: " + cost + ")");
        player.openInventory(coreService.createTimeModeShopMenu(island, holder.backTarget()));
    }

    private void handleVisitorSettingsClick(InventoryClickEvent event, Player player, UUID islandOwner) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(islandOwner).orElse(null);
        if (island == null || !islandService.isIslandOwner(island, player.getUniqueId())) return;
        switch (event.getRawSlot()) {
            case 10 -> island.getIslandVisitorSettings().setDoors(!island.getIslandVisitorSettings().isDoors());
            case 11 -> island.getIslandVisitorSettings().setTrapdoors(!island.getIslandVisitorSettings().isTrapdoors());
            case 12 -> island.getIslandVisitorSettings().setFenceGates(!island.getIslandVisitorSettings().isFenceGates());
            case 13 -> island.getIslandVisitorSettings().setButtons(!island.getIslandVisitorSettings().isButtons());
            case 14 -> island.getIslandVisitorSettings().setLevers(!island.getIslandVisitorSettings().isLevers());
            case 15 -> island.getIslandVisitorSettings().setPressurePlates(!island.getIslandVisitorSettings().isPressurePlates());
            case 16 -> island.getIslandVisitorSettings().setContainers(!island.getIslandVisitorSettings().isContainers());
            case 19 -> island.getIslandVisitorSettings().setFarmUse(!island.getIslandVisitorSettings().isFarmUse());
            case 20 -> island.getIslandVisitorSettings().setRide(!island.getIslandVisitorSettings().isRide());
            case 21 -> island.getIslandVisitorSettings().setLadderPlace(!island.getIslandVisitorSettings().isLadderPlace());
            case 22 -> island.getIslandVisitorSettings().setTeleport(!island.getIslandVisitorSettings().isTeleport());
            case 23 -> island.getIslandVisitorSettings().setLadderBreak(!island.getIslandVisitorSettings().isLadderBreak());
            case 24 -> island.getIslandVisitorSettings().setLeavesPlace(!island.getIslandVisitorSettings().isLeavesPlace());
            case 25 -> island.getIslandVisitorSettings().setLeavesBreak(!island.getIslandVisitorSettings().isLeavesBreak());
            case 35 -> {
                player.openInventory(coreService.createIslandMenu(player, island));
                return;
            }
            default -> { return; }
        }
        islandService.save();
        player.openInventory(coreService.createVisitorSettingsMenu(island));
    }

    private void handleParcelMenuClick(InventoryClickEvent event, Player player, CoreService.ParcelInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        var targetParcel = islandService.getParcel(island, holder.relChunkX(), holder.relChunkZ());
        boolean hasParcelAccess = targetParcel != null && islandService.isParcelUser(island, targetParcel, player.getUniqueId());
        if (!islandService.hasBuildAccess(player.getUniqueId(), island) && !hasParcelAccess) return;
        switch (event.getRawSlot()) {
            case 22 -> {
                player.getInventory().addItem(islandService.createPlotWand());
                player.sendMessage(ChatColor.GREEN + "GrundstÃ¼cks-Stab erhalten.");
                player.sendMessage(ChatColor.GRAY + "Setze Pos1/Pos2 und nutze /is plot create.");
            }
            case 10 -> {
                var parcel = islandService.getParcel(island, holder.relChunkX(), holder.relChunkZ());
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    parcel.setSpawn(player.getLocation().clone());
                    islandService.save();
                    player.sendMessage(ChatColor.GREEN + "GS-Spawn gesetzt.");
                }
            }
            case 12 -> {
                var parcel = islandService.getParcel(island, holder.relChunkX(), holder.relChunkZ());
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    player.openInventory(coreService.createParcelVisitorSettingsMenu(island, holder.relChunkX(), holder.relChunkZ()));
                    return;
                }
            }
            case 14 -> {
                var parcel = islandService.getParcel(island, holder.relChunkX(), holder.relChunkZ());
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    player.openInventory(coreService.createParcelMembersMenu(player, island, holder.relChunkX(), holder.relChunkZ(), IslandService.ParcelRole.OWNER, 0));
                    return;
                }
            }
            case 16 -> {
                var parcel = islandService.getParcel(island, holder.relChunkX(), holder.relChunkZ());
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    player.openInventory(coreService.createParcelMembersMenu(player, island, holder.relChunkX(), holder.relChunkZ(), IslandService.ParcelRole.USER, 0));
                    return;
                }
            }
            case 20 -> {
                var parcel = islandService.getParcel(island, holder.relChunkX(), holder.relChunkZ());
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    coreService.beginParcelRenameInput(player, island, parcel);
                    return;
                }
            }
            case 28 -> {
                var parcel = islandService.getParcel(island, holder.relChunkX(), holder.relChunkZ());
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    player.openInventory(coreService.createParcelModerationMenu(player, island, holder.relChunkX(), holder.relChunkZ(), CoreService.ParcelModerationAction.KICK, 0));
                    return;
                }
            }
            case 30 -> {
                var parcel = islandService.getParcel(island, holder.relChunkX(), holder.relChunkZ());
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    player.openInventory(coreService.createParcelModerationMenu(player, island, holder.relChunkX(), holder.relChunkZ(), CoreService.ParcelModerationAction.BAN, 0));
                    return;
                }
            }
            case 32 -> {
                var parcel = islandService.getParcel(island, holder.relChunkX(), holder.relChunkZ());
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    player.openInventory(coreService.createParcelModerationMenu(player, island, holder.relChunkX(), holder.relChunkZ(), CoreService.ParcelModerationAction.UNBAN, 0));
                    return;
                }
            }
            case 34 -> {
                var parcel = islandService.getParcel(island, holder.relChunkX(), holder.relChunkZ());
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    boolean enabled = !parcel.isPvpEnabled();
                    if (islandService.setParcelPvp(island, parcel, player.getUniqueId(), enabled)) {
                        player.sendMessage((enabled ? ChatColor.RED : ChatColor.GREEN) + "GS-PvP " + (enabled ? "aktiviert." : "deaktiviert."));
                        broadcastSkyCityChat(ChatColor.GOLD + player.getName() + ChatColor.GRAY + " hat GS-PvP auf " + islandService.getParcelDisplayName(parcel) + " " + (enabled ? ChatColor.RED + "aktiviert" : ChatColor.GREEN + "deaktiviert") + ChatColor.GRAY + ".");
                    }
                }
            }
            case 36 -> {
                var parcel = islandService.getParcel(island, holder.relChunkX(), holder.relChunkZ());
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
                var parcel = islandService.getParcel(island, holder.relChunkX(), holder.relChunkZ());
                if (parcel != null && islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
                    player.openInventory(coreService.createParcelMembersMenu(player, island, holder.relChunkX(), holder.relChunkZ(), IslandService.ParcelRole.PVP, 0));
                    return;
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

    private void handleParcelVisitorSettingsClick(InventoryClickEvent event, Player player, CoreService.ParcelVisitorSettingsInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        var parcel = islandService.getParcel(island, holder.relChunkX(), holder.relChunkZ());
        if (parcel == null || !islandService.isParcelOwner(island, parcel, player.getUniqueId())) return;
        switch (event.getRawSlot()) {
            case 10 -> parcel.getVisitorSettings().setDoors(!parcel.getVisitorSettings().isDoors());
            case 11 -> parcel.getVisitorSettings().setTrapdoors(!parcel.getVisitorSettings().isTrapdoors());
            case 12 -> parcel.getVisitorSettings().setFenceGates(!parcel.getVisitorSettings().isFenceGates());
            case 13 -> parcel.getVisitorSettings().setButtons(!parcel.getVisitorSettings().isButtons());
            case 14 -> parcel.getVisitorSettings().setLevers(!parcel.getVisitorSettings().isLevers());
            case 15 -> parcel.getVisitorSettings().setPressurePlates(!parcel.getVisitorSettings().isPressurePlates());
            case 16 -> parcel.getVisitorSettings().setContainers(!parcel.getVisitorSettings().isContainers());
            case 19 -> parcel.getVisitorSettings().setFarmUse(!parcel.getVisitorSettings().isFarmUse());
            case 20 -> parcel.getVisitorSettings().setRide(!parcel.getVisitorSettings().isRide());
            case 21 -> parcel.getVisitorSettings().setLadderPlace(!parcel.getVisitorSettings().isLadderPlace());
            case 22 -> parcel.getVisitorSettings().setTeleport(!parcel.getVisitorSettings().isTeleport());
            case 23 -> parcel.getVisitorSettings().setLadderBreak(!parcel.getVisitorSettings().isLadderBreak());
            case 24 -> parcel.getVisitorSettings().setLeavesPlace(!parcel.getVisitorSettings().isLeavesPlace());
            case 25 -> parcel.getVisitorSettings().setLeavesBreak(!parcel.getVisitorSettings().isLeavesBreak());
            case 35 -> {
                openParcelMenu(player, island, holder.relChunkX(), holder.relChunkZ());
                return;
            }
            default -> { return; }
        }
        islandService.save();
        player.openInventory(coreService.createParcelVisitorSettingsMenu(island, holder.relChunkX(), holder.relChunkZ()));
    }

    private void handleTeleportMenuClick(InventoryClickEvent event, Player player, CoreService.TeleportInventoryHolder holder) {
        event.setCancelled(true);
        if (event.getRawSlot() == 49) {
            IslandData own = islandService.getIsland(player.getUniqueId()).orElse(null);
            if (own != null) player.openInventory(coreService.createIslandMenu(player, own));
            else player.closeInventory();
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
        if (target == null || target.getUniqueId() == null || islandService.isIslandOwner(island, target.getUniqueId())) return;

        boolean changed = event.isRightClick()
                ? islandService.revokeTrust(island, target.getUniqueId(), permission)
                : islandService.grantTrust(island, target.getUniqueId(), permission);
        if (!changed) player.sendMessage(ChatColor.YELLOW + "Keine Ã„nderung.");
        islandService.save();
        player.openInventory(coreService.createIslandTrustMenu(player, island, permission, holder.page(), holder.filter()));
    }

    private void handleIslandOwnersMenuClick(InventoryClickEvent event, Player player, CoreService.IslandOwnersInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
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

        boolean changed = event.isRightClick()
                ? islandService.revokeIslandOwnerRole(island, player.getUniqueId(), target.getUniqueId())
                : islandService.grantIslandOwnerRole(island, player.getUniqueId(), target.getUniqueId());
        if (!changed) player.sendMessage(ChatColor.YELLOW + "Keine Ã„nderung.");
        player.openInventory(coreService.createIslandOwnersMenu(player, island, holder.page(), holder.filter()));
    }

    private void handleIslandMasterMenuClick(InventoryClickEvent event, Player player, CoreService.IslandMasterMenuInventoryHolder holder) {
        event.setCancelled(true);
        IslandData island = islandService.getIsland(holder.islandOwner()).orElse(null);
        if (island == null) return;
        switch (event.getRawSlot()) {
            case 11 -> player.openInventory(coreService.createIslandMasterInviteMenu(player, island, 0));
            case 13 -> {
                IslandData inviteIsland = islandService.getPendingCoOwnerInviteIsland(player.getUniqueId());
                boolean ok = islandService.acceptCoOwnerInvite(player.getUniqueId());
                if (ok) {
                    player.sendMessage(ChatColor.GREEN + "Du bist der Insel als Master beigetreten.");
                    if (inviteIsland != null && inviteIsland.getIslandSpawn() != null) player.teleport(inviteIsland.getIslandSpawn());
                } else {
                    player.sendMessage(ChatColor.RED + "Keine offene Master-Einladung.");
                }
                player.openInventory(coreService.createIslandMasterMenu(player, island));
            }
            case 15 -> {
                if (islandService.leaveCoOwnership(player.getUniqueId())) {
                    player.sendMessage(ChatColor.YELLOW + "Du bist als Master von der Insel ausgetreten.");
                    player.teleport(islandService.getSpawnLocation());
                } else {
                    player.sendMessage(ChatColor.RED + "Du bist auf keiner Insel als zusÃ¤tzlicher Master.");
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
        islandService.queueCoOwnerInvite(island, player.getUniqueId(), targetId);
        var target = player.getServer().getPlayer(targetId);
        String targetName = target != null ? target.getName() : String.valueOf(targetId);
        player.sendMessage(ChatColor.GREEN + "Master-Einladung gesendet an " + targetName + ".");
        if (target != null) {
            target.sendMessage(ChatColor.GOLD + player.getName() + " mÃ¶chte dich als Master einladen.");
            target.sendMessage(ChatColor.YELLOW + "Nutze /is masteraccept zum BestÃ¤tigen.");
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
        if (!changed) player.sendMessage(ChatColor.YELLOW + "Keine Ã„nderung.");
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
                    player.sendMessage(ChatColor.YELLOW + "Keine Ã„nderung.");
                }
            }
            case UNBAN -> {
                if (islandService.setParcelBan(island, parcel, player.getUniqueId(), targetId, false)) {
                    player.sendMessage(ChatColor.YELLOW + "Spieler vom GS entbannt.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Keine Ã„nderung.");
                }
            }
        }
        player.openInventory(coreService.createParcelModerationMenu(player, island, holder.relChunkX(), holder.relChunkZ(), action, holder.page()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof CoreService.IslandInventoryHolder
                || top.getHolder() instanceof CoreService.UpgradeProgressInventoryHolder
                || top.getHolder() instanceof CoreService.ParcelsInventoryHolder
                || top.getHolder() instanceof CoreService.ChunkMapInventoryHolder
                || top.getHolder() instanceof CoreService.BiomeInventoryHolder
                || top.getHolder() instanceof CoreService.VisitorSettingsInventoryHolder
                || top.getHolder() instanceof CoreService.ParcelInventoryHolder
                || top.getHolder() instanceof CoreService.ParcelVisitorSettingsInventoryHolder
                || top.getHolder() instanceof CoreService.TeleportInventoryHolder
                || top.getHolder() instanceof CoreService.IslandTrustMembersInventoryHolder
                || top.getHolder() instanceof CoreService.IslandOwnersInventoryHolder
                || top.getHolder() instanceof CoreService.IslandMasterMenuInventoryHolder
                || top.getHolder() instanceof CoreService.IslandMasterInviteInventoryHolder
                || top.getHolder() instanceof CoreService.ParcelMembersInventoryHolder
                || top.getHolder() instanceof CoreService.ParcelModerationInventoryHolder
                || top.getHolder() instanceof CoreService.IslandShopInventoryHolder
                || top.getHolder() instanceof CoreService.TimeModeShopInventoryHolder) {
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



