package de.mcbesser.skycity.listener;

import de.mcbesser.skycity.SkyCityPlugin;
import de.mcbesser.skycity.model.IslandData;
import de.mcbesser.skycity.model.ParcelData;
import de.mcbesser.skycity.service.CoreService;
import de.mcbesser.skycity.service.IslandService;
import de.mcbesser.skycity.service.SkyWorldService;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class PlotWandListener implements Listener {
    private static final double MAX_PREVIEW_DISTANCE = 96.0;
    private final SkyCityPlugin plugin;
    private final IslandService islandService;
    private final SkyWorldService skyWorldService;
    private final CoreService coreService;
    private int taskId = -1;

    public PlotWandListener(SkyCityPlugin plugin, IslandService islandService, SkyWorldService skyWorldService, CoreService coreService) {
        this.plugin = plugin;
        this.islandService = islandService;
        this.skyWorldService = skyWorldService;
        this.coreService = coreService;
        startTask();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWandMiddleClick(PlayerPickItemEvent event) {
        Player player = event.getPlayer();
        if (!islandService.isPlotWand(player.getInventory().getItemInMainHand())) return;
        if (!skyWorldService.isSkyCityWorld(player.getWorld())) return;

        IslandData ownIsland = islandService.getIsland(player.getUniqueId()).orElse(null);
        IslandData atIsland = islandService.getIslandAt(player.getLocation());
        if (ownIsland == null && atIsland == null) {
            player.sendMessage(ChatColor.RED + "Keine Insel gefunden.");
            return;
        }

        event.setCancelled(true);
        player.openInventory(coreService.createParcelsMenu(player, ownIsland != null ? ownIsland : atIsland));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWandInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!islandService.isPlotWand(player.getInventory().getItemInMainHand())) return;
        if (!skyWorldService.isSkyCityWorld(player.getWorld())) return;
        IslandData island = islandService.getIslandAt(player.getLocation());
        if (island == null) {
            player.sendMessage(ChatColor.RED + "Du bist auf keiner Insel.");
            event.setCancelled(true);
            return;
        }
        ParcelData currentParcel = islandService.getParcelAt(island, player.getLocation());
        boolean hasBuild = islandService.hasBuildAccess(player.getUniqueId(), island);
        boolean hasParcelRights = currentParcel != null && islandService.isParcelUser(island, currentParcel, player.getUniqueId());
        if (!hasBuild && !hasParcelRights) {
            player.sendMessage(ChatColor.RED + "Keine Rechte f\u00fcr den Grundst\u00fccks-Stab an diesem Standort.");
            event.setCancelled(true);
            return;
        }
        Location target = event.getClickedBlock().getLocation();
        if (islandService.getIslandAt(target) != island) {
            player.sendMessage(ChatColor.RED + "Nur innerhalb derselben Insel.");
            event.setCancelled(true);
            return;
        }
        if (!hasBuild) {
            ParcelData targetParcel = islandService.getParcelAt(island, target);
            if (targetParcel == null || !islandService.isParcelUser(island, targetParcel, player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Nur innerhalb eines Grundst\u00fccks mit deinen Rechten.");
                event.setCancelled(true);
                return;
            }
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            islandService.setPlotSelectionPos1(player.getUniqueId(), target);
            player.sendMessage(ChatColor.GREEN + "Pos1 gesetzt: " + target.getBlockX() + ", " + target.getBlockY() + ", " + target.getBlockZ());
        } else {
            islandService.setPlotSelectionPos2(player.getUniqueId(), target);
            player.sendMessage(ChatColor.GREEN + "Pos2 gesetzt: " + target.getBlockX() + ", " + target.getBlockY() + ", " + target.getBlockZ());
        }
        event.setCancelled(true);
    }

    private void startTask() {
        taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (!player.isOnline()) continue;
                if (!skyWorldService.isSkyCityWorld(player.getWorld())) continue;
                if (!islandService.isPlotWand(player.getInventory().getItemInMainHand())) continue;
                IslandData island = islandService.getIslandAt(player.getLocation());
                if (island == null) continue;
                ParcelData currentParcel = islandService.getParcelAt(island, player.getLocation());
                boolean hasBuild = islandService.hasBuildAccess(player.getUniqueId(), island);
                boolean hasParcelRights = currentParcel != null && islandService.isParcelUser(island, currentParcel, player.getUniqueId());
                if (!hasBuild && !hasParcelRights) continue;
                for (ParcelData parcel : island.getParcels().values()) {
                    if (!hasBuild && !islandService.isParcelUser(island, parcel, player.getUniqueId())) continue;
                    if (!isNear(player.getLocation(), parcel)) continue;
                    drawCuboidEdges(player.getWorld(), parcel.getMinX(), parcel.getMinY(), parcel.getMinZ(), parcel.getMaxX(), parcel.getMaxY(), parcel.getMaxZ());
                }
                Location pos1 = islandService.getPlotSelectionPos1(player.getUniqueId());
                Location pos2 = islandService.getPlotSelectionPos2(player.getUniqueId());
                if (pos1 != null && pos2 != null && pos1.getWorld() != null && pos1.getWorld().equals(player.getWorld()) && pos2.getWorld() != null && pos2.getWorld().equals(player.getWorld())) {
                    drawCuboidEdges(
                            player.getWorld(),
                            Math.min(pos1.getBlockX(), pos2.getBlockX()),
                            Math.min(pos1.getBlockY(), pos2.getBlockY()),
                            Math.min(pos1.getBlockZ(), pos2.getBlockZ()),
                            Math.max(pos1.getBlockX(), pos2.getBlockX()),
                            Math.max(pos1.getBlockY(), pos2.getBlockY()),
                            Math.max(pos1.getBlockZ(), pos2.getBlockZ())
                    );
                }
            }
        }, 5L, 8L);
    }

    private boolean isNear(Location playerLoc, ParcelData parcel) {
        double cx = (parcel.getMinX() + parcel.getMaxX()) / 2.0;
        double cy = (parcel.getMinY() + parcel.getMaxY()) / 2.0;
        double cz = (parcel.getMinZ() + parcel.getMaxZ()) / 2.0;
        return playerLoc.distanceSquared(new Location(playerLoc.getWorld(), cx, cy, cz)) <= MAX_PREVIEW_DISTANCE * MAX_PREVIEW_DISTANCE;
    }

    private void drawCuboidEdges(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            spawnEdge(world, x, minY, minZ);
            spawnEdge(world, x, minY, maxZ);
            spawnEdge(world, x, maxY, minZ);
            spawnEdge(world, x, maxY, maxZ);
        }
        for (int y = minY; y <= maxY; y++) {
            spawnEdge(world, minX, y, minZ);
            spawnEdge(world, minX, y, maxZ);
            spawnEdge(world, maxX, y, minZ);
            spawnEdge(world, maxX, y, maxZ);
        }
        for (int z = minZ; z <= maxZ; z++) {
            spawnEdge(world, minX, minY, z);
            spawnEdge(world, maxX, minY, z);
            spawnEdge(world, minX, maxY, z);
            spawnEdge(world, maxX, maxY, z);
        }
    }

    private void spawnEdge(World world, int x, int y, int z) {
        world.spawnParticle(Particle.END_ROD, x + 0.5, y + 0.5, z + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
    }
}



