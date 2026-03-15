package de.mcbesser.skycity.listener;

import de.mcbesser.skycity.model.IslandData;
import de.mcbesser.skycity.model.ParcelData;
import de.mcbesser.skycity.service.CoreService;
import de.mcbesser.skycity.service.IslandService;
import de.mcbesser.skycity.service.SkyWorldService;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ProtectionListener implements Listener {
    private final IslandService islandService;
    private final CoreService coreService;
    private final SkyWorldService skyWorldService;

    public ProtectionListener(IslandService islandService, CoreService coreService, SkyWorldService skyWorldService) {
        this.islandService = islandService;
        this.coreService = coreService;
        this.skyWorldService = skyWorldService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (!skyWorldService.isSkyCityWorld(block.getWorld())) return;
        if (player.isOp()) {
            IslandData island = islandService.getIslandAt(block.getLocation());
            if (island != null) {
                islandService.onTrackedBlockPlaced(island, block);
            }
            return;
        }
        if (islandService.isInSpawnPlot(block.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Spawn ist gesch\u00fctzt.");
            return;
        }
        IslandData island = islandService.getIslandAt(block.getLocation());
        if (island == null) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Nur auf Inseln bauen.");
            return;
        }
        ParcelData parcel = islandService.getParcelAt(island, block.getLocation());
        boolean bypassParcelRights = false;
        if (parcel != null && parcel.isPvpEnabled()) {
            boolean bypassAllowed = (block.getType() == Material.LADDER && parcel.getVisitorSettings().isLadderPlace())
                    || (Tag.LEAVES.isTagged(block.getType()) && parcel.getVisitorSettings().isLeavesPlace());
            if (!bypassAllowed) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "In einer aktiven PvP-Zone ist Bauen deaktiviert.");
                return;
            }
            bypassParcelRights = true;
        }
        if (!bypassParcelRights && !islandService.hasBuildAccess(player.getUniqueId(), island)) {
            if (parcel == null || !islandService.isParcelUser(island, parcel, player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Keine Baurechte.");
                return;
            }
        }
        if (!islandService.isChunkUnlocked(island, block.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Chunk gesperrt.");
            return;
        }

        Material type = block.getType();
        if (islandService.isInventoryLimitedMaterial(type) && islandService.getCachedInventoryBlockCount(island) + 1 > 100) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Limit 100 Inventarbloecke erreicht.");
            return;
        }
        if (type == Material.HOPPER && islandService.getCachedHopperCount(island) + 1 > islandService.getCurrentLevelDef(island).getHopperLimit()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Hopperlimit erreicht: " + islandService.getCurrentLevelDef(island).getHopperLimit());
            return;
        }
        if ((type == Material.PISTON || type == Material.STICKY_PISTON)
                && islandService.getCachedPistonCount(island) + 1 > islandService.getCurrentLevelDef(island).getPistonLimit()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Kolbenlimit erreicht: " + islandService.getCurrentLevelDef(island).getPistonLimit());
            return;
        }
        if (type == Material.OBSERVER
                && islandService.getCachedObserverCount(island) + 1 > islandService.getCurrentLevelDef(island).getObserverLimit()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Observerlimit erreicht: " + islandService.getCurrentLevelDef(island).getObserverLimit());
            return;
        }
        if (type == Material.DISPENSER
                && islandService.getCachedDispenserCount(island) + 1 > islandService.getCurrentLevelDef(island).getDispenserLimit()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Dispenserlimit erreicht: " + islandService.getCurrentLevelDef(island).getDispenserLimit());
            return;
        }
        if (type == Material.CACTUS
                && islandService.getCachedCactusCount(island) + 1 > islandService.getCurrentLevelDef(island).getCactusLimit()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Kaktuslimit erreicht: " + islandService.getCurrentLevelDef(island).getCactusLimit());
            return;
        }
        if ((type == Material.KELP || type == Material.KELP_PLANT)
                && islandService.getCachedKelpCount(island) + 1 > islandService.getCurrentLevelDef(island).getKelpLimit()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Kelplimit erreicht: " + islandService.getCurrentLevelDef(island).getKelpLimit());
            return;
        }
        if (type == Material.BAMBOO
                && islandService.getCachedBambooCount(island) + 1 > islandService.getCurrentLevelDef(island).getBambooLimit()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Bambuslimit erreicht: " + islandService.getCurrentLevelDef(island).getBambooLimit());
            return;
        }

        if (coreService.isCoreItem(event.getItemInHand())) {
            coreService.markPlacedCore(block, island.getOwner());
            island.setCoreLocation(block.getLocation());
            islandService.save();
            coreService.refreshCoreDisplay(island);
        }
        islandService.markIslandActivity(player.getUniqueId());
        islandService.onTrackedBlockPlaced(island, block);
        coreService.showIslandLimitHint(player, island, type);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (!skyWorldService.isSkyCityWorld(block.getWorld())) return;
        if (player.isOp()) {
            IslandData island = islandService.getIslandAt(block.getLocation());
            if (island != null) {
                islandService.onTrackedBlockBroken(island, block);
            }
            return;
        }
        if (islandService.isInSpawnPlot(block.getLocation())) {
            event.setCancelled(true);
            return;
        }
        IslandData island = islandService.getIslandAt(block.getLocation());
        if (island == null) {
            event.setCancelled(true);
            return;
        }
        ParcelData parcel = islandService.getParcelAt(island, block.getLocation());
        boolean bypassParcelRights = false;
        if (parcel != null && parcel.isPvpEnabled()) {
            boolean bypassAllowed = (block.getType() == Material.LADDER && parcel.getVisitorSettings().isLadderBreak())
                    || (Tag.LEAVES.isTagged(block.getType()) && parcel.getVisitorSettings().isLeavesBreak());
            if (!bypassAllowed) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "In einer aktiven PvP-Zone ist Abbauen deaktiviert.");
                return;
            }
            bypassParcelRights = true;
        }
        boolean hasBuild = islandService.hasBuildAccess(player.getUniqueId(), island)
                || (parcel != null && islandService.isParcelUser(island, parcel, player.getUniqueId()));
        if ((!hasBuild && !bypassParcelRights) || !islandService.isChunkUnlocked(island, block.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (coreService.isCoreBlock(block)) {
            if (!islandService.isIslandOwner(island, player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Nur der Insel-Owner kann den Core abbauen.");
                return;
            }
            event.setDropItems(false);
            if (block.getState() instanceof ShulkerBox shulker) {
                for (ItemStack item : shulker.getInventory().getContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), item.clone());
                    }
                }
            }
            block.setType(Material.AIR, false);
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), coreService.createCoreItem());
            if (island.getCoreLocation() != null && island.getCoreLocation().getBlock().equals(block)) {
                island.setCoreLocation(null);
                islandService.save();
            }
            coreService.removeCoreDisplays(island);
            islandService.markIslandActivity(player.getUniqueId());
            return;
        }
        islandService.markIslandActivity(player.getUniqueId());
        islandService.onTrackedBlockBroken(island, block);
        coreService.showIslandLimitHint(player, island, block.getType());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!skyWorldService.isSkyCityWorld(event.getBlock().getWorld())) return;
        if (event.getCause() == BlockIgniteEvent.IgniteCause.LAVA) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (!skyWorldService.isSkyCityWorld(event.getBlock().getWorld())) return;
        if (event.getSource().getType() == Material.FIRE || event.getBlock().getType() == Material.FIRE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!skyWorldService.isSkyCityWorld(event.getBlock().getWorld())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (!skyWorldService.isSkyCityWorld(event.getBlock().getWorld())) return;
        IslandData island = islandService.getIslandAt(event.getBlock().getLocation());
        if (island == null) return;
        int relX = islandService.relativeChunkX(island, event.getBlock().getChunk().getX());
        int relZ = islandService.relativeChunkZ(island, event.getBlock().getChunk().getZ());
        int tier = islandService.getGrowthBoostTier(island, relX, relZ);
        if (tier <= 0) return;
        if (!(event.getNewState().getBlockData() instanceof Ageable ageable)) return;
        int currentAge = ageable.getAge();
        int maxAge = ageable.getMaximumAge();
        if (currentAge >= maxAge) return;
        int boosted = Math.min(maxAge, currentAge + tier);
        ageable.setAge(boosted);
        event.getNewState().setBlockData(ageable);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!skyWorldService.isSkyCityWorld(event.getBlock().getWorld())) return;
        if (event.getBlock().getType() == Material.FARMLAND && event.getTo() == Material.DIRT) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveDamagingPlayer(event);
        if (attacker == null) return;
        if (!skyWorldService.isSkyCityWorld(victim.getWorld())) return;
        if (islandService.canPlayersFightAt(victim.getLocation(), attacker.getUniqueId(), victim.getUniqueId())) return;
        event.setCancelled(true);
        if (!attacker.getUniqueId().equals(victim.getUniqueId())) {
            attacker.sendMessage(ChatColor.RED + "PvP ist in SkyCity nur auf aktiven GS-PvP-Zonen erlaubt.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) return;
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock().getType() == Material.FARMLAND) {
            if (skyWorldService.isSkyCityWorld(event.getClickedBlock().getWorld())) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (!skyWorldService.isSkyCityWorld(block.getWorld())) return;

        if (event.getItem() != null && event.getItem().getType() == Material.ARMOR_STAND) {
            if (player.isOp()) return;
            Location placeLocation = block.getRelative(event.getBlockFace()).getLocation();
            if (islandService.isInSpawnPlot(placeLocation)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Spawn ist geschuetzt.");
                return;
            }
            IslandData island = islandService.getIslandAt(placeLocation);
            if (island == null) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Nur auf Inseln bauen.");
                return;
            }
            ParcelData parcel = islandService.getParcelAt(island, placeLocation);
            if (parcel != null && parcel.isPvpEnabled()) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "In einer aktiven PvP-Zone ist Bauen deaktiviert.");
                return;
            }
            boolean hasBuild = islandService.hasBuildAccess(player.getUniqueId(), island)
                    || (parcel != null && islandService.isParcelUser(island, parcel, player.getUniqueId()));
            if (!hasBuild) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Keine Baurechte.");
                return;
            }
            if (!islandService.isChunkUnlocked(island, placeLocation)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Chunk gesperrt.");
                return;
            }
            if (!islandService.isWithinArmorStandLimit(island)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Ruestungsstaenderlimit erreicht: " + islandService.getCurrentLevelDef(island).getArmorStandLimit());
                return;
            }
            if (!placeArmorStand(player, event.getHand(), placeLocation)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Hier kann kein Ruestungsstaender platziert werden.");
                return;
            }
            event.setCancelled(true);
            event.setUseInteractedBlock(Result.DENY);
            event.setUseItemInHand(Result.DENY);
            islandService.markIslandActivity(player.getUniqueId());
            coreService.showArmorStandLimitHint(player, island);
            return;
        }

        if (coreService.isCoreBlock(block)) {
            IslandData island = islandService.findIslandByCoreLocation(block.getLocation()).orElse(null);
            if (island == null) return;
            if (!player.isOp() && !islandService.hasContainerAccess(player.getUniqueId(), island)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Keine Rechte am Core.");
                return;
            }
            event.setCancelled(true);
            islandService.markIslandActivity(player.getUniqueId());
            if (player.isSneaking() && block.getState() instanceof ShulkerBox shulker) {
                player.openInventory(shulker.getInventory());
            } else {
                player.openInventory(coreService.createCoreMenu(player, island, block.getLocation()));
            }
            return;
        }

        if (player.isOp()) return;

        if (block.getType() == Material.END_PORTAL_FRAME
                && event.getItem() != null
                && event.getItem().getType() == Material.ENDER_EYE) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Vanilla End-Portale sind in SkyCity deaktiviert.");
            return;
        }

        IslandData island = islandService.getIslandAt(block.getLocation());
        if (island == null) return;

        ParcelData parcel = islandService.getParcelAt(island, block.getLocation());
        boolean parcelUser = parcel != null && islandService.isParcelUser(island, parcel, player.getUniqueId());
        if (isDoor(block.getType())) {
            boolean allowed = islandService.hasBuildAccess(player.getUniqueId(), island)
                    || parcelUser
                    || islandService.getEffectiveVisitorSettings(island, block.getLocation()).isDoors();
            if (!allowed) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "T\u00fcren sind hier gesperrt.");
                return;
            }
        }
        if (isTrapdoor(block.getType())) {
            boolean allowed = islandService.hasBuildAccess(player.getUniqueId(), island)
                    || parcelUser
                    || islandService.getEffectiveVisitorSettings(island, block.getLocation()).isTrapdoors();
            if (!allowed) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Trapdoors sind hier gesperrt.");
                return;
            }
        }
        if (isFenceGate(block.getType())) {
            boolean allowed = islandService.hasBuildAccess(player.getUniqueId(), island)
                    || parcelUser
                    || islandService.getEffectiveVisitorSettings(island, block.getLocation()).isFenceGates();
            if (!allowed) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Zauntore sind hier gesperrt.");
                return;
            }
        }
        if (isButton(block.getType())) {
            boolean allowed = islandService.hasBuildAccess(player.getUniqueId(), island)
                    || parcelUser
                    || islandService.getEffectiveVisitorSettings(island, block.getLocation()).isButtons();
            if (!allowed) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Buttons sind hier gesperrt.");
                return;
            }
        }
        if (isLever(block.getType())) {
            boolean allowed = islandService.hasBuildAccess(player.getUniqueId(), island)
                    || parcelUser
                    || islandService.getEffectiveVisitorSettings(island, block.getLocation()).isLevers();
            if (!allowed) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Hebel sind hier gesperrt.");
                return;
            }
        }
        if (isPressurePlate(block.getType())) {
            boolean allowed = islandService.hasBuildAccess(player.getUniqueId(), island)
                    || parcelUser
                    || islandService.getEffectiveVisitorSettings(island, block.getLocation()).isPressurePlates();
            if (!allowed) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Druckplatten sind hier gesperrt.");
                return;
            }
        }
        if (isFarmUseBlock(block.getType())) {
            boolean allowed = islandService.hasBuildAccess(player.getUniqueId(), island)
                    || parcelUser
                    || islandService.getEffectiveVisitorSettings(island, block.getLocation()).isFarmUse();
            if (!allowed) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Nutzbloecke/Farmaktionen sind hier gesperrt.");
                return;
            }
        }
        if (isRedstoneControl(block.getType())) {
            boolean allowed = islandService.hasRedstoneAccess(player.getUniqueId(), island)
                    || parcelUser
                    || islandService.getEffectiveVisitorSettings(island, block.getLocation()).isRedstoneUse();
            if (!allowed) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Keine Redstone-Rechte.");
            }
        }
        if (!event.isCancelled()) {
            islandService.markIslandActivity(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (player.isOp()) return;
        if (event.getInventory().getLocation() == null) return;
        IslandData island = islandService.getIslandAt(event.getInventory().getLocation());
        if (island == null) return;
        if (event.getInventory().getHolder() instanceof Container) {
            ParcelData parcel = islandService.getParcelAt(island, event.getInventory().getLocation());
            boolean parcelUser = parcel != null && islandService.isParcelUser(island, parcel, player.getUniqueId());
            boolean allowed = islandService.hasContainerAccess(player.getUniqueId(), island)
                    || parcelUser
                    || islandService.getEffectiveVisitorSettings(island, event.getInventory().getLocation()).isContainers();
            if (!allowed) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Keine Container-Rechte.");
                return;
            }
            islandService.markIslandActivity(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isOp()) return;
        if (!(event.getMount() instanceof Vehicle) && !(event.getMount() instanceof org.bukkit.entity.LivingEntity)) return;
        IslandData island = islandService.getIslandAt(event.getMount().getLocation());
        if (island == null) return;
        ParcelData parcel = islandService.getParcelAt(island, event.getMount().getLocation());
        boolean parcelUser = parcel != null && islandService.isParcelUser(island, parcel, player.getUniqueId());
        boolean allowed = islandService.hasBuildAccess(player.getUniqueId(), island) || parcelUser
                || islandService.getEffectiveVisitorSettings(island, event.getMount().getLocation()).isRide();
        if (!allowed) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Reiten ist hier gesperrt.");
            return;
        }
        islandService.markIslandActivity(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!skyWorldService.isSkyCityWorld(event.getLocation().getWorld())) return;
        if (event.getEntity() instanceof Monster) {
            event.setCancelled(true);
            return;
        }
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CHUNK_GEN
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.DEFAULT) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getEntity() instanceof Animals)
                && event.getEntityType() != EntityType.VILLAGER
                && !islandService.isTrackedGolem(event.getEntityType())) return;
        IslandData island = islandService.getIslandAt(event.getLocation());
        if (island == null) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntityType() == EntityType.VILLAGER) {
            if (!islandService.isWithinVillagerLimit(island)) event.setCancelled(true);
        } else if (islandService.isTrackedGolem(event.getEntityType())) {
            if (!islandService.isWithinGolemLimit(island)) event.setCancelled(true);
        } else if (!islandService.isWithinAnimalLimit(island)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!skyWorldService.isSkyCityWorld(event.getEntity().getWorld())) return;
        IslandData island = islandService.getIslandAt(event.getEntity().getLocation());
        if (island == null || !islandService.isWithinAnimalLimit(island)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (event.getSource().getLocation() == null) return;
        if (!skyWorldService.isSkyCityWorld(event.getSource().getLocation().getWorld())) return;
        if (islandService.getIslandAt(event.getSource().getLocation()) == null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!isPistonMoveAllowed(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!isPistonMoveAllowed(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    private boolean isPistonMoveAllowed(Block pistonBase, List<Block> movedBlocks, BlockFace moveDirection) {
        if (!skyWorldService.isSkyCityWorld(pistonBase.getWorld())) return true;
        IslandData sourceIsland = islandService.getIslandAt(pistonBase.getLocation());
        if (sourceIsland == null) return false;

        for (Block moved : movedBlocks) {
            if (coreService.isCoreBlock(moved)) return false;
            Block destination = moved.getRelative(moveDirection);
            if (coreService.isCoreBlock(destination)) return false;

            IslandData destinationIsland = islandService.getIslandAt(destination.getLocation());
            if (destinationIsland == null) return false;
            if (!destinationIsland.getOwner().equals(sourceIsland.getOwner())) return false;
            if (!islandService.isChunkUnlocked(sourceIsland, destination.getLocation())) return false;
        }
        return true;
    }

    private boolean isRedstoneControl(Material type) {
        String n = type.name();
        return n.endsWith("_BUTTON")
                || n.endsWith("_PRESSURE_PLATE")
                || type == Material.LEVER
                || type == Material.REPEATER
                || type == Material.COMPARATOR
                || type == Material.DAYLIGHT_DETECTOR
                || type == Material.TARGET
                || type == Material.TRIPWIRE_HOOK
                || type == Material.NOTE_BLOCK;
    }

    private boolean isDoor(Material type) {
        String n = type.name();
        return n.endsWith("_DOOR") && !n.endsWith("_TRAPDOOR");
    }

    private boolean isTrapdoor(Material type) {
        return type.name().endsWith("_TRAPDOOR");
    }

    private boolean isFenceGate(Material type) {
        return type.name().endsWith("_FENCE_GATE");
    }

    private boolean isButton(Material type) {
        return type.name().endsWith("_BUTTON");
    }

    private boolean isLever(Material type) {
        return type == Material.LEVER;
    }

    private boolean isPressurePlate(Material type) {
        return type.name().endsWith("_PRESSURE_PLATE");
    }

    private boolean isFarmUseBlock(Material type) {
        return switch (type) {
            case COMPOSTER, CAULDRON, WATER_CAULDRON, LAVA_CAULDRON, POWDER_SNOW_CAULDRON,
                    ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL,
                    STONECUTTER, LOOM, CARTOGRAPHY_TABLE, SMITHING_TABLE,
                    GRINDSTONE, FLETCHING_TABLE -> true;
            default -> false;
        };
    }

    private Player resolveDamagingPlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private boolean placeArmorStand(Player player, EquipmentSlot hand, Location baseLocation) {
        if (baseLocation.getWorld() == null) return false;
        Block targetBlock = baseLocation.getBlock();
        if (!targetBlock.isPassable()) return false;

        Location spawnLocation = targetBlock.getLocation().add(0.5, 0.0, 0.5);
        if (!baseLocation.getWorld().getNearbyEntities(spawnLocation.clone().add(0.0, 0.9, 0.0), 0.3, 0.9, 0.3).isEmpty()) {
            return false;
        }

        ItemStack item = hand == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (item == null || item.getType() != Material.ARMOR_STAND) return false;

        ArmorStand stand = baseLocation.getWorld().spawn(spawnLocation, ArmorStand.class, spawned -> {
            spawned.setRotation(player.getLocation().getYaw(), 0.0f);
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                spawned.customName(meta.displayName());
                spawned.setCustomNameVisible(true);
            }
        });
        if (stand == null) return false;

        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
        return true;
    }
}



