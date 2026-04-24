package de.mcbesser.skycity.listener;

import de.mcbesser.skycity.SkyCityPlugin;
import de.mcbesser.skycity.listener.PlayerListener;
import de.mcbesser.skycity.model.AccessSettings;
import de.mcbesser.skycity.model.IslandData;
import de.mcbesser.skycity.model.ParcelData;
import de.mcbesser.skycity.service.CoreService;
import de.mcbesser.skycity.service.IslandService;
import de.mcbesser.skycity.service.SkyWorldService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Snow;
import org.bukkit.block.data.type.Bamboo;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Goat;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public class ProtectionListener implements Listener {
    private static final long GROWTH_BOOST_INTERVAL_TICKS = 20L;
    private static final long WEATHER_SNOW_INTERVAL_TICKS = 60L;
    private static final long ANIMAL_COLLISION_REFRESH_TICKS = 100L;
    private static final String ANIMAL_COLLISION_TEAM = "sky_animals";
    private static final long BABY_EMOTION_DURATION_MS = 2500L;
    private static final String[] BABY_CLICK_EMOTES = {
            "\uD83D\uDE0A",
            "\uD83D\uDE03",
            "\uD83D\uDE1B",
            "\uD83E\uDD70",
            "\uD83D\uDE0D",
            "\uD83E\uDD73",
            "\uD83C\uDF7C",
            "\uD83E\uDDF8",
            "\uD83E\uDDF8\uD83D\uDC95",
            "\uD83E\uDD7A",
            "\uD83D\uDC9E"
    };
    private static final Set<Material> GROWTH_BOOST_MATERIALS = EnumSet.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.NETHER_WART,
            Material.COCOA,
            Material.SWEET_BERRY_BUSH,
            Material.BROWN_MUSHROOM,
            Material.RED_MUSHROOM,
            Material.CHORUS_FLOWER,
            Material.CAVE_VINES,
            Material.CAVE_VINES_PLANT,
            Material.WEEPING_VINES,
            Material.WEEPING_VINES_PLANT,
            Material.TWISTING_VINES,
            Material.TWISTING_VINES_PLANT,
            Material.VINE,
            Material.PUMPKIN_STEM,
            Material.MELON_STEM,
            Material.ATTACHED_PUMPKIN_STEM,
            Material.ATTACHED_MELON_STEM,
            Material.TORCHFLOWER_CROP,
            Material.PITCHER_CROP,
            Material.OAK_SAPLING,
            Material.SPRUCE_SAPLING,
            Material.BIRCH_SAPLING,
            Material.JUNGLE_SAPLING,
            Material.ACACIA_SAPLING,
            Material.CHERRY_SAPLING,
            Material.DARK_OAK_SAPLING,
            Material.MANGROVE_PROPAGULE,
            Material.BAMBOO,
            Material.BAMBOO_SAPLING,
            Material.SUGAR_CANE,
            Material.CACTUS,
            Material.KELP,
            Material.KELP_PLANT
    );
    private static final Set<EntityType> SHEARABLE_TYPES = EnumSet.of(
            EntityType.SHEEP,
            EntityType.MOOSHROOM,
            EntityType.SNOW_GOLEM
    );

    private final SkyCityPlugin plugin;
    private final IslandService islandService;
    private final CoreService coreService;
    private final SkyWorldService skyWorldService;
    private final PlayerListener playerListener;
    private final RandomTickBridge randomTickBridge = new RandomTickBridge();

    public ProtectionListener(SkyCityPlugin plugin, IslandService islandService, CoreService coreService, SkyWorldService skyWorldService, PlayerListener playerListener) {
        this.plugin = plugin;
        this.islandService = islandService;
        this.coreService = coreService;
        this.skyWorldService = skyWorldService;
        this.playerListener = playerListener;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::runPeriodicGrowthBoosts, 38L, GROWTH_BOOST_INTERVAL_TICKS);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::runWeatherSnowSimulation, 74L, WEATHER_SNOW_INTERVAL_TICKS);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAnimalCollisions, 40L, ANIMAL_COLLISION_REFRESH_TICKS);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        Block block = event.getBlock();
        if (!skyWorldService.isSkyCityWorld(block.getWorld())) return;
        if (event.getNewState().getType() != Material.SNOW) return;
        IslandData island = islandService.getIslandAt(block.getLocation());
        if (island == null) return;
        ParcelData parcel = islandService.getParcelAt(island, block.getLocation());
        IslandService.SnowWeatherMode mode = parcel != null
                ? islandService.getParcelSnowMode(parcel)
                : islandService.getIslandSnowMode(island);
        if (mode == IslandService.SnowWeatherMode.BLOCK) {
            event.setCancelled(true);
        }
    }

    private void runWeatherSnowSimulation() {
        World world = skyWorldService.getWorld();
        if (world == null) return;
        for (Player player : world.getPlayers()) {
            if (player == null || !player.isOnline()) continue;
            Location center = player.getLocation();
            IslandData island = islandService.getIslandAt(center);
            if (island == null) continue;
            for (int i = 0; i < 4; i++) {
                int x = center.getBlockX() + ThreadLocalRandom.current().nextInt(-24, 25);
                int z = center.getBlockZ() + ThreadLocalRandom.current().nextInt(-24, 25);
                simulateSnowAt(world, island, x, z);
            }
        }
    }

    private void simulateSnowAt(World world, IslandData originIsland, int x, int z) {
        if (world == null || originIsland == null) return;
        Location probe = new Location(world, x + 0.5, SkyWorldService.SPAWN_Y, z + 0.5);
        IslandData island = islandService.getIslandAt(probe);
        if (island == null || !island.getOwner().equals(originIsland.getOwner())) return;
        ParcelData parcel = islandService.getParcelAt(island, probe);
        IslandService.IslandWeatherMode weatherMode = parcel != null
                ? islandService.getParcelWeatherMode(parcel)
                : islandService.getIslandWeatherMode(island);
        if (weatherMode != IslandService.IslandWeatherMode.RAIN && weatherMode != IslandService.IslandWeatherMode.THUNDER) {
            return;
        }
        IslandService.SnowWeatherMode snowMode = parcel != null
                ? islandService.getParcelSnowMode(parcel)
                : islandService.getIslandSnowMode(island);
        if (snowMode == IslandService.SnowWeatherMode.BLOCK) {
            return;
        }
        Biome biome = world.getBiome(x, SkyWorldService.SPAWN_Y, z);
        if (!isColdSnowBiome(biome)) return;
        Block top = world.getHighestBlockAt(x, z);
        if (top.getType() == Material.SNOW) {
            BlockData data = top.getBlockData();
            if (data instanceof Snow snow && snow.getLayers() < snow.getMaximumLayers()) {
                snow.setLayers(Math.min(snow.getMaximumLayers(), snow.getLayers() + 1));
                top.setBlockData(snow, false);
            }
            return;
        }
        Block above = top.getRelative(BlockFace.UP);
        if (!above.getType().isAir()) return;
        if (!canSnowRestOn(top)) return;
        above.setType(Material.SNOW, false);
    }

    private boolean isColdSnowBiome(Biome biome) {
        if (biome == null) return false;
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

    private boolean canSnowRestOn(Block block) {
        if (block == null) return false;
        Material material = block.getType();
        if (material == Material.ICE || material == Material.PACKED_ICE || material == Material.BLUE_ICE) return false;
        if (material == Material.WATER || material == Material.LAVA) return false;
        return material.isSolid() && material.isOccluding();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (!skyWorldService.isSkyCityWorld(block.getWorld())) return;
        boolean bypassProtection = canBypassIslandProtection(player);
        boolean placingCore = coreService.isCoreItem(event.getItemInHand());
        IslandData island = islandService.getIslandAt(block.getLocation());
        if (island == null) {
            if (placingCore) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Cores k\u00f6nnen nur auf Inseln gesetzt werden.");
                return;
            }
            if (!bypassProtection) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Nur auf Inseln bauen.");
            }
            return;
        }
        if (bypassProtection && !placingCore) {
            if (block.getType().name().endsWith("_PRESSURE_PLATE")) {
                islandService.setCheckpointPlateYaw(island, block.getLocation(), player.getLocation().getYaw());
            }
            playerListener.invalidateStructureCaches(block.getLocation(), block.getType());
            islandService.onTrackedBlockPlaced(island, block);
            return;
        }
        ParcelData parcel = islandService.getParcelAt(island, block.getLocation());
        boolean bypassParcelRights = false;
        if (parcel != null && (parcel.isPvpEnabled() || parcel.isGamesEnabled())) {
            boolean bypassAllowed = (block.getType() == Material.LADDER && parcel.getVisitorSettings().isLadderPlace())
                    || (Tag.LEAVES.isTagged(block.getType()) && parcel.getVisitorSettings().isLeavesPlace())
                    || (isSnowGameBlock(block.getType()) && parcel.getVisitorSettings().isSnowPlace())
                    || (isBannerGameBlock(block.getType()) && parcel.getVisitorSettings().isBannerPlace());
            if (!bypassAllowed) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "In einer aktiven PvP-/Games-Zone ist Bauen deaktiviert.");
                return;
            }
            bypassParcelRights = true;
        }
        if (!bypassProtection && !bypassParcelRights && !islandService.hasBuildAccess(player.getUniqueId(), island)) {
            if (parcel == null || !islandService.isParcelUser(island, parcel, player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Keine Baurechte.");
                return;
            }
        }
        if (!bypassProtection && !islandService.isChunkUnlocked(island, block.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Chunk gesperrt.");
            return;
        }

        Material type = block.getType();
        if (!bypassProtection
                && islandService.isInventoryLimitedMaterial(type)
                && islandService.getCachedInventoryBlockCount(island) + 1 > islandService.getCurrentUpgradeLimit(island, IslandService.UpgradeBranch.CONTAINER)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Beh\u00e4lterlimit erreicht: " + islandService.getCurrentUpgradeLimit(island, IslandService.UpgradeBranch.CONTAINER));
            return;
        }
        if (!bypassProtection && type == Material.HOPPER && islandService.getCachedHopperCount(island) + 1 > islandService.getCurrentLevelDef(island).getHopperLimit()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Hopperlimit erreicht: " + islandService.getCurrentLevelDef(island).getHopperLimit());
            return;
        }
        if (!bypassProtection
                && (type == Material.PISTON || type == Material.STICKY_PISTON)
                && islandService.getCachedPistonCount(island) + 1 > islandService.getCurrentLevelDef(island).getPistonLimit()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Kolbenlimit erreicht: " + islandService.getCurrentLevelDef(island).getPistonLimit());
            return;
        }
        if (!bypassProtection
                && type == Material.OBSERVER
                && islandService.getCachedObserverCount(island) + 1 > islandService.getCurrentLevelDef(island).getObserverLimit()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Observerlimit erreicht: " + islandService.getCurrentLevelDef(island).getObserverLimit());
            return;
        }
        if (!bypassProtection
                && type == Material.DISPENSER
                && islandService.getCachedDispenserCount(island) + 1 > islandService.getCurrentLevelDef(island).getDispenserLimit()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Dispenserlimit erreicht: " + islandService.getCurrentLevelDef(island).getDispenserLimit());
            return;
        }
        if (!bypassProtection
                && type == Material.CACTUS
                && islandService.getCachedCactusCount(island) + 1 > islandService.getCurrentLevelDef(island).getCactusLimit()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Kaktuslimit erreicht: " + islandService.getCurrentLevelDef(island).getCactusLimit());
            return;
        }
        if (!bypassProtection
                && (type == Material.KELP || type == Material.KELP_PLANT)
                && islandService.getCachedKelpCount(island) + 1 > islandService.getCurrentLevelDef(island).getKelpLimit()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Kelplimit erreicht: " + islandService.getCurrentLevelDef(island).getKelpLimit());
            return;
        }
        if (!bypassProtection
                && type == Material.BAMBOO
                && islandService.getCachedBambooCount(island) + 1 > islandService.getCurrentLevelDef(island).getBambooLimit()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Bambuslimit erreicht: " + islandService.getCurrentLevelDef(island).getBambooLimit());
            return;
        }
        if (type.name().endsWith("_PRESSURE_PLATE")) {
            islandService.setCheckpointPlateYaw(island, block.getLocation(), player.getLocation().getYaw());
        }
        playerListener.invalidateStructureCaches(block.getLocation(), type);

        if (placingCore) {
            if (islandService.isSpawnIsland(island)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Am Spawn gibt es keinen Core.");
                return;
            }
            coreService.markPlacedCore(block, island.getOwner());
            if (island.getCoreLocation() == null) {
                island.setCoreLocation(block.getLocation());
            } else {
                island.addCoreLocation(block.getLocation());
            }
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
        boolean bypassProtection = canBypassIslandProtection(player);
        boolean breakingCore = coreService.isCoreBlock(block);
        IslandData island = islandService.getIslandAt(block.getLocation());
        if (island == null) {
            if (!bypassProtection) {
                event.setCancelled(true);
            }
            return;
        }
        ParcelData parcel = islandService.getParcelAt(island, block.getLocation());
        if (bypassProtection && !breakingCore) {
            if (block.getType().name().endsWith("_PRESSURE_PLATE")) {
                islandService.removeCheckpointPlateYaw(island, block.getLocation());
            }
            if (parcel != null && parcel.isGamesEnabled() && parcel.isSnowballFightEnabled() && isSnowGameBlock(block.getType())) {
                int amount = block.getType() == Material.SNOW_BLOCK ? 4 : 1;
                event.setDropItems(false);
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), playerListener.createMagicSnowballItem(amount));
            }
            playerListener.invalidateStructureCaches(block.getLocation(), block.getType());
            islandService.onTrackedBlockBroken(island, block);
            return;
        }
        boolean bypassParcelRights = false;
        boolean pveParticipant = parcel != null && parcel.isPveEnabled() && islandService.isPlayerInParcelPve(player.getUniqueId(), island, parcel);
        if (parcel != null && (parcel.isPvpEnabled() || parcel.isGamesEnabled())) {
            boolean bypassAllowed = (block.getType() == Material.LADDER && parcel.getVisitorSettings().isLadderBreak())
                    || (Tag.LEAVES.isTagged(block.getType()) && parcel.getVisitorSettings().isLeavesBreak())
                    || (isSnowGameBlock(block.getType()) && parcel.getVisitorSettings().isSnowBreak())
                    || (isBannerGameBlock(block.getType()) && parcel.getVisitorSettings().isBannerBreak());
            if (!bypassAllowed) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "In einer aktiven PvP-/Games-Zone ist Abbauen deaktiviert.");
                return;
            }
            bypassParcelRights = true;
        }
        boolean hasBuild = islandService.hasBuildAccess(player.getUniqueId(), island)
                || (parcel != null && islandService.isParcelUser(island, parcel, player.getUniqueId()));
        if (!bypassProtection && ((!hasBuild && !bypassParcelRights && !pveParticipant) || !islandService.isChunkUnlocked(island, block.getLocation()))) {
            event.setCancelled(true);
            return;
        }
        if (breakingCore) {
            if (!bypassProtection && !islandService.isIslandOwner(island, player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Nur Master oder Owner k\u00f6nnen den Core abbauen.");
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
            island.removeCoreLocation(block.getLocation());
            islandService.save();
            coreService.refreshCoreDisplay(island);
            islandService.markIslandActivity(player.getUniqueId());
            return;
        }
        if (block.getType().name().endsWith("_PRESSURE_PLATE")) {
            islandService.removeCheckpointPlateYaw(island, block.getLocation());
        }
        if (parcel != null && parcel.isGamesEnabled() && parcel.isSnowballFightEnabled() && isSnowGameBlock(block.getType())) {
            int amount = block.getType() == Material.SNOW_BLOCK ? 4 : 1;
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), playerListener.createMagicSnowballItem(amount));
        }
        playerListener.invalidateStructureCaches(block.getLocation(), block.getType());
        islandService.markIslandActivity(player.getUniqueId());
        islandService.onTrackedBlockBroken(island, block);
        coreService.showIslandLimitHint(player, island, block.getType());
    }

    private boolean canBypassIslandProtection(Player player) {
        return player != null && (player.isOp() || player.getGameMode() == GameMode.CREATIVE);
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
    public void onBlockFromTo(BlockFromToEvent event) {
        if (!skyWorldService.isSkyCityWorld(event.getBlock().getWorld())) return;
        Material sourceType = event.getBlock().getType();
        Material destinationType = event.getToBlock().getType();
        if (sourceType != Material.LAVA && sourceType != Material.LAVA_CAULDRON
                && destinationType != Material.LAVA && destinationType != Material.LAVA_CAULDRON) {
            return;
        }
        playerListener.invalidateStructureCaches(event.getBlock().getLocation(), sourceType);
        playerListener.invalidateStructureCaches(event.getToBlock().getLocation(), sourceType);
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
        islandService.getGrowthBoostTier(island, relX, relZ);
    }

    private void runPeriodicGrowthBoosts() {
        var world = skyWorldService.getWorld();
        if (world == null) return;
        if (!islandService.hasAnyActiveGrowthBoosts()) return;
        Integer randomTickSpeedValue = world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED);
        int randomTickSpeed = randomTickSpeedValue == null ? 3 : Math.max(0, randomTickSpeedValue);
        for (IslandData island : islandService.getIslandsWithActiveGrowthBoosts()) {
            for (String chunkKey : List.copyOf(island.getGrowthBoostUntil().keySet())) {
                int[] relChunk = parseChunkKey(chunkKey);
                if (relChunk == null) continue;
                int tier = islandService.getGrowthBoostTier(island, relChunk[0], relChunk[1]);
                if (tier <= 0) continue;
                int worldChunkX = islandService.plotMinChunkX(island.getGridX()) + relChunk[0];
                int worldChunkZ = islandService.plotMinChunkZ(island.getGridZ()) + relChunk[1];
                if (!world.isChunkLoaded(worldChunkX, worldChunkZ)) continue;
                var chunk = world.getChunkAt(worldChunkX, worldChunkZ);
                int attemptsPerSection = islandService.getGrowthBoostExtraRandomTickAttemptsPerSection(tier, randomTickSpeed, (int) GROWTH_BOOST_INTERVAL_TICKS);
                if (attemptsPerSection <= 0) continue;
                var tickStats = randomTickBridge.tickChunk(chunk, attemptsPerSection, tier);
                if (tickStats.bridgeFailures() > 0) {
                    plugin.getLogger().warning("SkyCity Growth-Boost-Bridge meldet Fehler f\u00fcr Chunk "
                            + worldChunkX + "," + worldChunkZ + " in Welt " + world.getName()
                            + ". Zielinfos: " + tickStats.targetSummary()
                            + ", Grund: " + (tickStats.failureReason() == null ? "-" : tickStats.failureReason()));
                }
            }
        }
    }

    private int[] parseChunkKey(String chunkKey) {
        if (chunkKey == null || chunkKey.isBlank()) return null;
        String[] parts = chunkKey.split(":", 2);
        if (parts.length != 2) return null;
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private final class RandomTickBridge {
        private record GrowthTargets(int[] positions, String summary) { }
        private record TickStats(int attempts, int hits, int bridgeFailures, int targets, String targetSummary, String failureReason) { }

        private boolean initialized;
        private boolean available;
        private boolean initializationFailureLogged;
        private boolean runtimeFailureLogged;
        private Method getWorldHandleMethod;
        private Method getChunkHandleMethod;
        private Object chunkHandleStatusArgument;
        private Method getChunkSectionsMethod;
        private Method sectionIsRandomlyTickingBlocksMethod;
        private Method sectionGetBlockStateMethod;
        private Field sectionTickingListField;
        private Field sectionStatesField;
        private Method tickingListSizeMethod;
        private Method tickingListGetRawMethod;
        private Method palettedContainerGetMethod;
        private Method blockStateGetBlockMethod;
        private Constructor<?> blockPosConstructor;
        private Method isRandomlyTickingMethod;
        private Method randomTickMethod;
        private Method getRandomMethod;
        private Object[] emptySections = new Object[0];
        private int random = ThreadLocalRandom.current().nextInt();

        TickStats tickChunk(org.bukkit.Chunk chunk, int tickSpeed, int tier) {
            if (chunk == null || tickSpeed <= 0) return new TickStats(0, 0, 0, 0, "-", null);
            try {
                ensureInitialized(chunk);
                if (!available) {
                    return new TickStats(0, 0, 1, 0, "-", "RandomTickBridge initialization incomplete");
                }
                Object levelChunk = invokeChunkHandle(chunk);
                Object level = getWorldHandleMethod.invoke(chunk.getWorld());
                Object randomSource = getRandomMethod.invoke(level);
                Object[] sections = (Object[]) getChunkSectionsMethod.invoke(levelChunk);
                if (sections == null) sections = emptySections;
                if (sectionTickingListField != null && sectionStatesField != null
                        && tickingListSizeMethod != null && tickingListGetRawMethod != null
                        && palettedContainerGetMethod != null && blockStateGetBlockMethod != null) {
                    return tickChunkViaTickingList(chunk, level, randomSource, sections, tickSpeed, tier);
                }
                GrowthTargets growthTargets = collectGrowthTargets(chunk, level);
                if (growthTargets.positions().length == 0) {
                    return new TickStats(0, 0, 0, 0, growthTargets.summary(), null);
                }
                int attempts = 0;
                int hits = 0;
                for (int i = 0; i < tickSpeed; i++) {
                    attempts++;
                    int packedLocation = growthTargets.positions()[nextRandom(growthTargets.positions().length)];
                    int localX = packedLocation & 15;
                    int localZ = (packedLocation >>> 4) & 15;
                    int worldY = packedLocation >> 8;
                    int worldX = (chunk.getX() << 4) + localX;
                    int worldZ = (chunk.getZ() << 4) + localZ;
                    Object blockPos = blockPosConstructor.newInstance(worldX, worldY, worldZ);
                    Object state = getBlockState(level, blockPos);
                    Block block = chunk.getWorld().getBlockAt(worldX, worldY, worldZ);
                    if (shouldSkipGrowthAttempt(tier)) {
                        continue;
                    }
                    hits += applyRandomTickWithParticles(block, state, level, blockPos, randomSource);
                    hits += applyBambooHeadTick(block, level, randomSource);
                }

                return new TickStats(attempts, hits, 0, growthTargets.positions().length, growthTargets.summary(), null);
            } catch (Throwable throwable) {
                logRuntimeFailureOnce(chunk, throwable);
                return new TickStats(0, 0, 1, 0, "-", rootMessage(throwable));
            }
        }

        private void ensureInitialized(org.bukkit.Chunk chunk) throws Exception {
            if (initialized) return;
            initialized = true;
            try {
                getWorldHandleMethod = chunk.getWorld().getClass().getMethod("getHandle");
                getChunkHandleMethod = resolveChunkHandleMethod(chunk.getClass());
                if (getChunkHandleMethod == null) return;
                Class<?> levelChunkClass = getChunkHandleMethod.getReturnType();
                getChunkSectionsMethod = levelChunkClass.getMethod("getSections");

                Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos");
                blockPosConstructor = blockPosClass.getConstructor(int.class, int.class, int.class);

                Class<?> sectionClass = Class.forName("net.minecraft.world.level.chunk.LevelChunkSection");
                sectionIsRandomlyTickingBlocksMethod = sectionClass.getMethod("isRandomlyTickingBlocks");
                sectionGetBlockStateMethod = sectionClass.getMethod("getBlockState", int.class, int.class, int.class);
                sectionTickingListField = resolveField(sectionClass, "tickingList");
                sectionStatesField = resolveField(sectionClass, "states");

                Class<?> blockStateClass = sectionGetBlockStateMethod.getReturnType();
                isRandomlyTickingMethod = blockStateClass.getMethod("isRandomlyTicking");
                blockStateGetBlockMethod = resolveMethod(blockStateClass, "getBlock");

                Class<?> serverLevelClass = getWorldHandleMethod.getReturnType();
                Class<?> randomSourceClass = Class.forName("net.minecraft.util.RandomSource");
                randomTickMethod = resolveMethod(blockStateClass, "randomTick", serverLevelClass, blockPosClass, randomSourceClass);
                getRandomMethod = resolveMethod(serverLevelClass, "getRandom");
                if (sectionTickingListField != null) {
                    Class<?> tickingListClass = sectionTickingListField.getType();
                    tickingListSizeMethod = resolveMethod(tickingListClass, "size");
                    tickingListGetRawMethod = resolveMethod(tickingListClass, "getRaw", int.class);
                }
                if (sectionStatesField != null) {
                    palettedContainerGetMethod = resolveGetByIndexMethod(sectionStatesField.getType());
                }
            } catch (Throwable throwable) {
                logInitializationFailureOnce(chunk, throwable);
                return;
            }

            if (getChunkSectionsMethod == null
                    || sectionIsRandomlyTickingBlocksMethod == null
                    || sectionGetBlockStateMethod == null
                    || blockPosConstructor == null
                    || isRandomlyTickingMethod == null
                    || randomTickMethod == null
                    || getRandomMethod == null) {
                return;
            }

            available = true;
        }

        private TickStats tickChunkViaTickingList(org.bukkit.Chunk chunk, Object level, Object randomSource, Object[] sections, int tickSpeed, int tier) throws Exception {
            int attempts = 0;
            int hits = 0;
            int targets = 0;
            int minSection = Math.floorDiv(chunk.getWorld().getMinHeight(), 16);
            int chunkOffsetX = chunk.getX() << 4;
            int chunkOffsetZ = chunk.getZ() << 4;

            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                Object section = sections[sectionIndex];
                if (section == null) continue;
                if (!(boolean) sectionIsRandomlyTickingBlocksMethod.invoke(section)) continue;
                Object tickingList = sectionTickingListField.get(section);
                if (tickingList == null) continue;
                int tickingBlocks = ((Number) tickingListSizeMethod.invoke(tickingList)).intValue();
                if (tickingBlocks <= 0) continue;
                Object states = sectionStatesField.get(section);
                int offsetY = (sectionIndex + minSection) << 4;

                for (int i = 0; i < tickSpeed; i++) {
                    attempts++;
                    int index = nextRawIndex();
                    if (index >= tickingBlocks) {
                        continue;
                    }
                    int location = ((Number) tickingListGetRawMethod.invoke(tickingList, index)).intValue() & 0xFFFF;
                    Object state = palettedContainerGetMethod.invoke(states, location);
                    Object nmsBlock = blockStateGetBlockMethod.invoke(state);
                    Material material = toBukkitMaterial(nmsBlock);
                    if (!isPotentialGrowthTarget(chunk.getWorld(), chunkOffsetX, chunkOffsetZ, offsetY, location, material)) {
                        continue;
                    }
                    targets++;
                    int worldX = (location & 15) | chunkOffsetX;
                    int worldY = ((location >>> 8) & 15) | offsetY;
                    int worldZ = ((location >>> 4) & 15) | chunkOffsetZ;
                    Object blockPos = blockPosConstructor.newInstance(worldX, worldY, worldZ);
                    Block block = chunk.getWorld().getBlockAt(worldX, worldY, worldZ);
                    if (shouldSkipGrowthAttempt(tier)) {
                        continue;
                    }
                    hits += applyRandomTickWithParticles(block, state, level, blockPos, randomSource);
                    hits += applyBambooHeadTick(block, level, randomSource);
                }
            }

            return new TickStats(attempts, hits, 0, targets, "nms", null);
        }

        private void logInitializationFailureOnce(org.bukkit.Chunk chunk, Throwable throwable) {
            if (initializationFailureLogged) return;
            initializationFailureLogged = true;
            plugin.getLogger().warning("SkyCity RandomTickBridge konnte f\u00fcr " + describeChunk(chunk)
                    + " nicht initialisiert werden. Growth-Boost nutzt dort keinen stabilen NMS-Pfad: "
                    + rootMessage(throwable));
        }

        private void logRuntimeFailureOnce(org.bukkit.Chunk chunk, Throwable throwable) {
            if (runtimeFailureLogged) return;
            runtimeFailureLogged = true;
            plugin.getLogger().warning("SkyCity RandomTickBridge ist f\u00fcr " + describeChunk(chunk)
                    + " zur Laufzeit fehlgeschlagen. Growth-Boost kann dort ausfallen: "
                    + rootMessage(throwable));
        }

        private String describeChunk(org.bukkit.Chunk chunk) {
            if (chunk == null) return "unbekannten Chunk";
            return chunk.getWorld().getName() + " chunk " + chunk.getX() + "," + chunk.getZ();
        }

        private String rootMessage(Throwable throwable) {
            if (throwable == null) return "unbekannter Fehler";
            Throwable current = throwable;
            while (current.getCause() != null) {
                current = current.getCause();
            }
            String message = current.getMessage();
            if (message == null || message.isBlank()) {
                return current.getClass().getSimpleName();
            }
            return current.getClass().getSimpleName() + ": " + message;
        }

        private GrowthTargets collectGrowthTargets(org.bukkit.Chunk chunk, Object level) throws Exception {
            int[] targets = new int[512];
            int targetCount = 0;
            int ageableCount = 0;
            int kelpCount = 0;
            int cactusCount = 0;
            int caneCount = 0;
            int bambooCount = 0;
            int presentAgeableCount = 0;
            int presentKelpCount = 0;
            int presentCactusCount = 0;
            int presentCaneCount = 0;
            int presentBambooCount = 0;
            int chunkOffsetX = chunk.getX() << 4;
            int chunkOffsetZ = chunk.getZ() << 4;
            int minY = chunk.getWorld().getMinHeight();
            int maxY = chunk.getWorld().getMaxHeight() - 1;
            for (int worldY = minY; worldY <= maxY; worldY++) {
                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        int worldX = chunkOffsetX + localX;
                        int worldZ = chunkOffsetZ + localZ;
                        Block candidate = chunk.getWorld().getBlockAt(worldX, worldY, worldZ);
                        switch (candidate.getType()) {
                            case KELP, KELP_PLANT -> presentKelpCount++;
                            case CACTUS -> presentCactusCount++;
                            case SUGAR_CANE -> presentCaneCount++;
                            case BAMBOO, BAMBOO_SAPLING -> presentBambooCount++;
                            default -> {
                                if (candidate.getBlockData() instanceof Ageable) {
                                    presentAgeableCount++;
                                }
                            }
                        }
                        if (!isPotentialGrowthTarget(candidate)) continue;
                        Object blockPos = blockPosConstructor.newInstance(worldX, worldY, worldZ);
                        if (targetCount == targets.length) {
                            int[] grown = new int[targets.length * 2];
                            System.arraycopy(targets, 0, grown, 0, targets.length);
                            targets = grown;
                        }
                        targets[targetCount++] = localX | (localZ << 4) | (worldY << 8);
                        switch (candidate.getType()) {
                            case KELP, KELP_PLANT -> kelpCount++;
                            case CACTUS -> cactusCount++;
                            case SUGAR_CANE -> caneCount++;
                            case BAMBOO, BAMBOO_SAPLING -> bambooCount++;
                            default -> ageableCount++;
                        }
                    }
                }
            }
            int[] compact = new int[targetCount];
            System.arraycopy(targets, 0, compact, 0, targetCount);
            String summary = "a" + ageableCount + " k" + kelpCount + " c" + cactusCount + " s" + caneCount + " b" + bambooCount
                    + " pa" + presentAgeableCount + " pk" + presentKelpCount + " pc" + presentCactusCount
                    + " ps" + presentCaneCount + " pb" + presentBambooCount;
            return new GrowthTargets(compact, summary);
        }

        private boolean isPotentialGrowthTarget(Block block) {
            if (block == null || !GROWTH_BOOST_MATERIALS.contains(block.getType())) return false;

            Material type = block.getType();

            if (type == Material.PUMPKIN_STEM || type == Material.MELON_STEM) {
                return true;
            }
            if (type == Material.ATTACHED_PUMPKIN_STEM || type == Material.ATTACHED_MELON_STEM) {
                return false;
            }

            if (block.getBlockData() instanceof Ageable ageable) {
                return ageable.getAge() < ageable.getMaximumAge();
            }
            Block above = block.getRelative(BlockFace.UP);

            return switch (type) {
                case OAK_SAPLING, SPRUCE_SAPLING, BIRCH_SAPLING, JUNGLE_SAPLING,
                        ACACIA_SAPLING, CHERRY_SAPLING, DARK_OAK_SAPLING,
                        MANGROVE_PROPAGULE, BROWN_MUSHROOM, RED_MUSHROOM,
                        CHORUS_FLOWER, VINE, WEEPING_VINES, WEEPING_VINES_PLANT,
                        TWISTING_VINES, TWISTING_VINES_PLANT -> true;
                case BAMBOO, BAMBOO_SAPLING -> above.getType() == Material.AIR;
                case SUGAR_CANE -> above.getType() == Material.AIR;
                case CACTUS -> isCactusGrowthTarget(block, above);
                case KELP, KELP_PLANT -> isKelpGrowthTarget(block, above);
                default -> false;
            };
        }

        private boolean isPotentialGrowthTarget(org.bukkit.World world, int chunkOffsetX, int chunkOffsetZ, int offsetY, int location, Material material) {
            if (material == null || !GROWTH_BOOST_MATERIALS.contains(material)) return false;
            int worldX = (location & 15) | chunkOffsetX;
            int worldY = ((location >>> 8) & 15) | offsetY;
            int worldZ = ((location >>> 4) & 15) | chunkOffsetZ;
            return isPotentialGrowthTarget(world.getBlockAt(worldX, worldY, worldZ));
        }

        private boolean isCactusGrowthTarget(Block block, Block above) {
            if (block.getType() != Material.CACTUS || above.getType() != Material.AIR) return false;
            if (block.getRelative(BlockFace.NORTH).getType().isSolid()) return false;
            if (block.getRelative(BlockFace.SOUTH).getType().isSolid()) return false;
            if (block.getRelative(BlockFace.EAST).getType().isSolid()) return false;
            if (block.getRelative(BlockFace.WEST).getType().isSolid()) return false;
            if (block.getRelative(BlockFace.DOWN).getType() == Material.CACTUS
                    && block.getRelative(BlockFace.UP).getType() == Material.AIR) {
                int height = 1;
                Block cursor = block.getRelative(BlockFace.DOWN);
                while (cursor.getType() == Material.CACTUS) {
                    height++;
                    if (height >= 3) {
                        return false;
                    }
                    cursor = cursor.getRelative(BlockFace.DOWN);
                }
            }
            return true;
        }

        private boolean isKelpGrowthTarget(Block block, Block above) {
            if (above.getType() != Material.WATER) return false;
            return switch (block.getType()) {
                case KELP -> true;
                case KELP_PLANT -> true;
                default -> false;
            };
        }

        private Object getBlockState(Object level, Object blockPos) throws Exception {
            Method method = resolveMethod(level.getClass(), "getBlockState", blockPos.getClass());
            return method.invoke(level, blockPos);
        }

        private boolean shouldSkipGrowthAttempt(int tier) {
            int skipChance = switch (tier) {
                case 1 -> 99;
                case 2 -> 95;
                case 3 -> 90;
                default -> 100;
            };
            return nextRandom(100) < skipChance;
        }

        private int applyRandomTickWithParticles(Block block, Object state, Object level, Object blockPos, Object randomSource) throws Exception {
            if (block == null) return 0;
            Block above = block.getRelative(BlockFace.UP);
            Material beforeType = block.getType();
            String beforeData = block.getBlockData().getAsString();
            Material beforeAboveType = above.getType();
            String beforeAboveData = above.getBlockData().getAsString();

            randomTickMethod.invoke(state, level, blockPos, randomSource);

            if (!didGrowthBlockChange(block, beforeType, beforeData, above, beforeAboveType, beforeAboveData)) {
                return 0;
            }

            spawnGrowthBoostParticles(selectParticleBlock(block, beforeType, beforeData, above, beforeAboveType, beforeAboveData));
            return 1;
        }

        private int applyBambooHeadTick(Block block, Object level, Object randomSource) throws Exception {
            if (block == null) return 0;
            Material type = block.getType();
            if (type != Material.BAMBOO && type != Material.BAMBOO_SAPLING) {
                return 0;
            }
            Block head = resolveBambooHead(block);
            if (head == null || !isPotentialGrowthTarget(head)) {
                return 0;
            }
            if (head.getType() == Material.BAMBOO_SAPLING) {
                head.setBlockData(createBambooData(Bamboo.Leaves.SMALL, false), false);
                spawnGrowthBoostParticles(head);
                return 1;
            }
            if (!growBambooNaturally(head)) {
                return 0;
            }
            spawnGrowthBoostParticles(head.getRelative(BlockFace.UP).getType() == Material.BAMBOO ? head.getRelative(BlockFace.UP) : head);
            return 1;
        }

        private Block resolveBambooHead(Block block) {
            Block current = block;
            while (current.getRelative(BlockFace.UP).getType() == Material.BAMBOO
                    || current.getRelative(BlockFace.UP).getType() == Material.BAMBOO_SAPLING) {
                current = current.getRelative(BlockFace.UP);
            }
            return current;
        }

        private boolean growBambooNaturally(Block head) {
            if (head == null || head.getType() != Material.BAMBOO) return false;
            if (head.getRelative(BlockFace.UP).getType() != Material.AIR) return false;

            int height = getBambooHeight(head);
            int maxHeight = getBambooTargetHeight(head);
            if (height >= maxHeight) return false;

            Block above = head.getRelative(BlockFace.UP);
            above.setBlockData(createBambooData(Bamboo.Leaves.SMALL, height + 1 >= maxHeight), false);
            updateBambooLeaves(above);

            return true;
        }

        private int getBambooTargetHeight(Block head) {
            Block base = head;
            while (base.getRelative(BlockFace.DOWN).getType() == Material.BAMBOO
                    || base.getRelative(BlockFace.DOWN).getType() == Material.BAMBOO_SAPLING) {
                base = base.getRelative(BlockFace.DOWN);
            }

            long mixed = 1469598103934665603L;
            mixed ^= base.getX();
            mixed *= 1099511628211L;
            mixed ^= (long) base.getY() << 21;
            mixed *= 1099511628211L;
            mixed ^= (long) base.getZ() << 42;
            mixed *= 1099511628211L;

            // Natural-looking spread without visible +1 stair patterns between nearby stalks.
            return 7 + (int) Math.floorMod(mixed ^ (mixed >>> 32), 10L);
        }

        private int getBambooHeight(Block head) {
            int height = 0;
            Block current = head;
            while (current.getType() == Material.BAMBOO || current.getType() == Material.BAMBOO_SAPLING) {
                height++;
                current = current.getRelative(BlockFace.DOWN);
            }
            return height;
        }

        private BlockData createBambooData(Bamboo.Leaves leaves, boolean mature) {
            Bamboo data = (Bamboo) Material.BAMBOO.createBlockData();
            data.setLeaves(leaves == null ? Bamboo.Leaves.NONE : leaves);
            data.setStage(mature ? 1 : 0);
            return data;
        }

        private void updateBambooLeaves(Block head) {
            if (head == null) return;
            Block top = resolveBambooHead(head);
            if (top == null) return;

            Block current = top;
            int indexFromTop = 0;
            while (current.getType() == Material.BAMBOO || current.getType() == Material.BAMBOO_SAPLING) {
                if (current.getType() == Material.BAMBOO && current.getBlockData() instanceof Bamboo bambooData) {
                    Bamboo.Leaves leaves = switch (indexFromTop) {
                        case 0 -> Bamboo.Leaves.LARGE;
                        case 1, 2 -> Bamboo.Leaves.SMALL;
                        default -> Bamboo.Leaves.NONE;
                    };
                    bambooData.setLeaves(leaves);
                    bambooData.setStage(indexFromTop == 0 ? 1 : 0);
                    current.setBlockData(bambooData, false);
                }
                current = current.getRelative(BlockFace.DOWN);
                indexFromTop++;
            }
        }

        private boolean didGrowthBlockChange(Block block, Material beforeType, String beforeData, Block above, Material beforeAboveType, String beforeAboveData) {
            return isRelevantGrowthChange(beforeType, beforeData, block.getType(), block.getBlockData().getAsString())
                    || isRelevantGrowthChange(beforeAboveType, beforeAboveData, above.getType(), above.getBlockData().getAsString());
        }

        private boolean isRelevantGrowthChange(Material beforeType, String beforeData, Material afterType, String afterData) {
            if (beforeType == afterType && beforeData.equals(afterData)) {
                return false;
            }
            return GROWTH_BOOST_MATERIALS.contains(beforeType) || GROWTH_BOOST_MATERIALS.contains(afterType);
        }

        private Block selectParticleBlock(Block block, Material beforeType, String beforeData, Block above, Material beforeAboveType, String beforeAboveData) {
            if (isRelevantGrowthChange(beforeAboveType, beforeAboveData, above.getType(), above.getBlockData().getAsString())) {
                return above;
            }
            return block;
        }

        private void spawnGrowthBoostParticles(Block block) {
            if (block == null) return;
            World world = block.getWorld();
            world.spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    block.getX() + 0.5D,
                    block.getY() + 0.65D,
                    block.getZ() + 0.5D,
                    5,
                    0.18D,
                    0.22D,
                    0.18D,
                    0.01D
            );
        }

        private int nextRandom(int bound) {
            random = random * 3 + 1013904223;
            return ((random >>> 2) & Integer.MAX_VALUE) % bound;
        }

        private Object invokeChunkHandle(org.bukkit.Chunk chunk) throws Exception {
            if (chunkHandleStatusArgument == null) {
                return getChunkHandleMethod.invoke(chunk);
            }
            return getChunkHandleMethod.invoke(chunk, chunkHandleStatusArgument);
        }

        private Method resolveChunkHandleMethod(Class<?> chunkClass) {
            try {
                chunkHandleStatusArgument = null;
                return chunkClass.getMethod("getHandle");
            } catch (NoSuchMethodException ignored) {
            }

            for (Method method : chunkClass.getMethods()) {
                if (!method.getName().equals("getHandle") || method.getParameterCount() != 1) continue;
                Object status = resolveFullChunkStatus(method.getParameterTypes()[0]);
                if (status == null) continue;
                chunkHandleStatusArgument = status;
                return method;
            }
            return null;
        }

        private Object resolveFullChunkStatus(Class<?> statusClass) {
            if (statusClass == null) return null;
            if (statusClass.isEnum()) {
                for (Object constant : statusClass.getEnumConstants()) {
                    if ("FULL".equals(String.valueOf(constant))) {
                        return constant;
                    }
                }
            }
            try {
                Field fullField = statusClass.getField("FULL");
                return fullField.get(null);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private Method resolveMethod(Class<?> type, String name, Class<?>... parameterTypes) {
            Class<?> current = type;
            while (current != null) {
                try {
                    Method method = current.getDeclaredMethod(name, parameterTypes);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                    current = current.getSuperclass();
                }
            }
            try {
                return type.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }

        private Field resolveField(Class<?> type, String name) {
            Class<?> current = type;
            while (current != null) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
            return null;
        }

        private Method resolveGetByIndexMethod(Class<?> type) {
            Class<?> current = type;
            while (current != null) {
                for (Method method : current.getDeclaredMethods()) {
                    if (!method.getName().equals("get")) continue;
                    if (method.getParameterCount() != 1) continue;
                    Class<?> parameterType = method.getParameterTypes()[0];
                    if (parameterType != int.class && parameterType != Integer.class) continue;
                    method.setAccessible(true);
                    return method;
                }
                current = current.getSuperclass();
            }
            return null;
        }

        private int nextRawIndex() {
            random = random * 3 + 1013904223;
            return (random >>> 2) & 0xFFF;
        }

        private Material toBukkitMaterial(Object nmsBlock) {
            if (nmsBlock == null) return null;
            String path = String.valueOf(nmsBlock);
            int separator = path.lastIndexOf(':');
            if (separator >= 0 && separator + 1 < path.length()) {
                path = path.substring(separator + 1);
            }
            path = path.toUpperCase(java.util.Locale.ROOT);
            try {
                return Material.valueOf(path);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!skyWorldService.isSkyCityWorld(event.getBlock().getWorld())) return;
        if (event.getEntity() instanceof Sheep
                && event.getBlock().getType() == Material.GRASS_BLOCK
                && event.getTo() == Material.DIRT) {
            return;
        }
        if (event.getBlock().getType() == Material.FARMLAND && event.getTo() == Material.DIRT) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        if (player.isOp() || !skyWorldService.isSkyCityWorld(target.getWorld())) return;
        if (!islandService.canUseBuildSetting(player.getUniqueId(), target.getLocation(), AccessSettings::isBuckets)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Buckets sind hier gesperrt.");
            return;
        }
        IslandData island = islandService.getIslandAt(target.getLocation());
        if (island == null || !islandService.isChunkUnlocked(island, target.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Chunk gesperrt.");
            return;
        }
        islandService.markIslandActivity(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Block source = event.getBlockClicked();
        if (player.isOp() || !skyWorldService.isSkyCityWorld(source.getWorld())) return;
        if (!islandService.canUseBuildSetting(player.getUniqueId(), source.getLocation(), AccessSettings::isBuckets)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Buckets sind hier gesperrt.");
            return;
        }
        IslandData island = islandService.getIslandAt(source.getLocation());
        if (island == null || !islandService.isChunkUnlocked(island, source.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Chunk gesperrt.");
            return;
        }
        islandService.markIslandActivity(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.isOp()) return;
        if (!skyWorldService.isSkyCityWorld(event.getEntity().getWorld())) return;
        Location location = event.getEntity().getLocation();
        if (!islandService.canUseBuildSetting(player.getUniqueId(), location, AccessSettings::isDecorations)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Deko/Frames sind hier gesperrt.");
            return;
        }
        IslandData island = islandService.getIslandAt(location);
        if (island == null || !islandService.isChunkUnlocked(island, location)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Chunk gesperrt.");
            return;
        }
        islandService.markIslandActivity(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!(event.getRemover() instanceof Player player) || player.isOp()) return;
        if (!skyWorldService.isSkyCityWorld(event.getEntity().getWorld())) return;
        if (!islandService.canUseBuildSetting(player.getUniqueId(), event.getEntity().getLocation(), AccessSettings::isDecorations)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Deko/Frames sind hier gesperrt.");
            return;
        }
        islandService.markIslandActivity(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() || !skyWorldService.isSkyCityWorld(event.getRightClicked().getWorld())) return;
        if (!islandService.canUseBuildSetting(player.getUniqueId(), event.getRightClicked().getLocation(), AccessSettings::isDecorations)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Deko/Frames sind hier gesperrt.");
            return;
        }
        islandService.markIslandActivity(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() || !skyWorldService.isSkyCityWorld(event.getBed().getWorld())) return;
        if (!islandService.canUseBuildSetting(player.getUniqueId(), event.getBed().getLocation(), AccessSettings::isFarmUse)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Betten sind hier gesperrt.");
            return;
        }
        islandService.markIslandActivity(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTakeLecternBook(PlayerTakeLecternBookEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() || !skyWorldService.isSkyCityWorld(event.getLectern().getWorld())) return;
        if (!islandService.canUseBuildSetting(player.getUniqueId(), event.getLectern().getLocation(), AccessSettings::isFarmUse)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Lecterns sind hier gesperrt.");
            return;
        }
        islandService.markIslandActivity(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.isOp() || !skyWorldService.isSkyCityWorld(event.getEntity().getWorld())) return;
        if (!canUseLeash(player, event.getEntity().getLocation(), event.getEntity() instanceof AbstractVillager)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Anleinen ist hier gesperrt.");
            return;
        }
        islandService.markIslandActivity(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUnleash(PlayerUnleashEntityEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.isOp() || !skyWorldService.isSkyCityWorld(event.getEntity().getWorld())) return;
        if (!canUseLeash(player, event.getEntity().getLocation(), event.getEntity() instanceof AbstractVillager)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Anleinen ist hier gesperrt.");
            return;
        }
        islandService.markIslandActivity(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDisplayInteract(PlayerInteractAtEntityEvent event) {
        if (!skyWorldService.isSkyCityWorld(event.getRightClicked().getWorld())) return;
        if (coreService.handleDisplayInteraction(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
            return;
        }
        if (coreService.handleParcelOfferInteraction(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (skyWorldService.isSkyCityWorld(event.getRightClicked().getWorld())
            && coreService.handleDisplayInteraction(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
            return;
        }
        if (skyWorldService.isSkyCityWorld(event.getRightClicked().getWorld())
            && coreService.handleParcelOfferInteraction(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
            return;
        }
        Player player = event.getPlayer();
        if (player.isOp() || !skyWorldService.isSkyCityWorld(event.getRightClicked().getWorld())) return;
        if (event.getRightClicked() instanceof Hanging) {
            if (!islandService.canUseBuildSetting(player.getUniqueId(), event.getRightClicked().getLocation(), AccessSettings::isDecorations)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Deko/Frames sind hier gesperrt.");
                return;
            }
            islandService.markIslandActivity(player.getUniqueId());
            return;
        }
        if (event.getRightClicked() instanceof AbstractVillager) {
            if (!islandService.canUseBuildSetting(player.getUniqueId(), event.getRightClicked().getLocation(), AccessSettings::isVillagers)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Villager sind hier gesperrt.");
                return;
            }
            islandService.markIslandActivity(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim) {
            LivingEntity pveAttacker = resolveDamagingLivingEntity(event);
            if (pveAttacker != null) {
                islandService.recordPveMobPlayerHit(pveAttacker, victim);
            }
        }
        if (event.getEntity() instanceof Interaction interaction) {
            Player attacker = resolveDamagingPlayer(event);
            if (attacker == null || !skyWorldService.isSkyCityWorld(interaction.getWorld())) return;
            if (coreService.handleCoreDisplayToggle(attacker, interaction)) {
                event.setCancelled(true);
                return;
            }
            if (coreService.handleParcelOfferInteraction(attacker, interaction)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getEntity() instanceof ArmorStand stand) {
            Player attacker = resolveDamagingPlayer(event);
            if (attacker == null || attacker.isOp() || !skyWorldService.isSkyCityWorld(stand.getWorld())) return;
            if (!islandService.canUseBuildSetting(attacker.getUniqueId(), stand.getLocation(), AccessSettings::isDecorations)) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "Deko/Frames sind hier gesperrt.");
                return;
            }
            islandService.markIslandActivity(attacker.getUniqueId());
            return;
        }
        if (event.getEntity() instanceof Hanging hanging) {
            Player attacker = resolveDamagingPlayer(event);
            if (attacker == null || attacker.isOp() || !skyWorldService.isSkyCityWorld(hanging.getWorld())) return;
            if (!islandService.canUseBuildSetting(attacker.getUniqueId(), hanging.getLocation(), AccessSettings::isDecorations)) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "Deko/Frames sind hier gesperrt.");
                return;
            }
            islandService.markIslandActivity(attacker.getUniqueId());
            return;
        }
        if (event.getEntity() instanceof Vehicle vehicle) {
            Player attacker = resolveDamagingPlayer(event);
            if (attacker == null || attacker.isOp() || !skyWorldService.isSkyCityWorld(vehicle.getWorld())) return;
            if (!islandService.canUseBuildSetting(attacker.getUniqueId(), vehicle.getLocation(), AccessSettings::isVehicleDestroy)) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "Fahrzeuge sind hier gesch\u00fctzt.");
                return;
            }
            islandService.markIslandActivity(attacker.getUniqueId());
            return;
        }
        if (event.getEntity() instanceof Animals animals) {
            Player attacker = resolveDamagingPlayer(event);
            if (attacker == null || attacker.isOp() || !skyWorldService.isSkyCityWorld(animals.getWorld())) return;
            IslandData island = islandService.getIslandAt(animals.getLocation());
            if (island == null) return;
            ParcelData parcel = islandService.getParcelAt(island, animals.getLocation());
            boolean hasIslandBuild = islandService.hasBuildAccess(attacker.getUniqueId(), island);
            boolean isParcelUser = parcel != null && islandService.isParcelUser(island, parcel, attacker.getUniqueId());
            if (parcel == null) {
                if (!hasIslandBuild) {
                    event.setCancelled(true);
                    attacker.sendMessage(ChatColor.RED + "Du darfst auf fremden Inseln keine Tiere t\u00f6ten.");
                    return;
                }
                islandService.markIslandActivity(attacker.getUniqueId());
                return;
            }
            if (islandService.isParcelOwner(island, parcel, attacker.getUniqueId()) || hasIslandBuild) {
                islandService.markIslandActivity(attacker.getUniqueId());
                return;
            }
            if (!islandService.isParcelMember(island, parcel, attacker.getUniqueId()) || !parcel.isMemberAnimalKill()) {
                if (isParcelUser) {
                    event.setCancelled(true);
                    pushAnimalAway(attacker, animals);
                    islandService.markIslandActivity(attacker.getUniqueId());
                } else {
                    event.setCancelled(true);
                    attacker.sendMessage(ChatColor.RED + "Du darfst hier keine Tiere t\u00f6ten.");
                }
                return;
            }
            if (parcel.isMemberAnimalKeepTwo() && islandService.countAnimalsInParcelByType(parcel, animals.getType()) <= 2) {
                event.setCancelled(true);
                pushAnimalAway(attacker, animals);
                attacker.sendMessage(ChatColor.RED + "Es m\u00fcssen mindestens 2 Tiere dieser Art im Plot bleiben.");
                return;
            }
            islandService.markIslandActivity(attacker.getUniqueId());
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveDamagingPlayer(event);
        if (attacker == null) return;
        if (event.getDamager() instanceof org.bukkit.entity.Snowball snowball && playerListener.isMagicSnowballProjectile(snowball)) {
            return;
        }
        if (!skyWorldService.isSkyCityWorld(victim.getWorld())) return;
        if (islandService.canPlayersFightAt(victim.getLocation(), attacker.getUniqueId(), victim.getUniqueId())) return;
        event.setCancelled(true);
        if (!attacker.getUniqueId().equals(victim.getUniqueId())) {
            attacker.sendMessage(ChatColor.RED + "PvP ist in SkyCity nur auf aktiven GS-PvP-Zonen erlaubt.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!(event.getAttacker() instanceof Player player) || player.isOp()) return;
        if (!skyWorldService.isSkyCityWorld(event.getVehicle().getWorld())) return;
        if (!islandService.canUseBuildSetting(player.getUniqueId(), event.getVehicle().getLocation(), AccessSettings::isVehicleDestroy)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Fahrzeuge sind hier gesch\u00fctzt.");
            return;
        }
        islandService.markIslandActivity(player.getUniqueId());
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
        if (playerListener.isActiveCtfShelfLocation(block.getLocation())) {
            event.setCancelled(true);
            event.setUseInteractedBlock(Result.DENY);
            event.setUseItemInHand(Result.DENY);
            player.sendMessage(ChatColor.RED + "CTF-Regale sind gesperrt.");
            return;
        }
        ItemStack item = event.getItem();

        if (item != null && item.getType() == Material.ARMOR_STAND) {
            Location placeLocation = block.getRelative(event.getBlockFace()).getLocation();
            if (canBypassIslandProtection(player)) {
                if (!placeArmorStand(player, event.getHand(), placeLocation)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Hier kann kein Ruestungsstaender platziert werden.");
                    return;
                }
                event.setCancelled(true);
                event.setUseInteractedBlock(Result.DENY);
                event.setUseItemInHand(Result.DENY);
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
                player.sendMessage(ChatColor.RED + "R\u00fcstungsst\u00e4nderlimit erreicht: " + islandService.getCurrentLevelDef(island).getArmorStandLimit());
                return;
            }
            if (!placeArmorStand(player, event.getHand(), placeLocation)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Hier kann kein R\u00fcstungsst\u00e4nder platziert werden.");
                return;
            }
            event.setCancelled(true);
            event.setUseInteractedBlock(Result.DENY);
            event.setUseItemInHand(Result.DENY);
            islandService.markIslandActivity(player.getUniqueId());
            coreService.showArmorStandLimitHint(player, island);
            return;
        }

        if (item != null && (isMinecartItem(item.getType()) || isBoatItem(item.getType()))) {
            if (player.isOp()) return;
            Location placeLocation = isMinecartItem(item.getType())
                    ? block.getLocation()
                    : block.getRelative(event.getBlockFace()).getLocation();
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
            if (isMinecartItem(item.getType()) && !islandService.isWithinMinecartLimit(island)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Minecartlimit erreicht: " + islandService.getCurrentLevelDef(island).getMinecartLimit());
                return;
            }
            if (isBoatItem(item.getType()) && !islandService.isWithinBoatLimit(island)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Bootlimit erreicht: " + islandService.getCurrentLevelDef(island).getBoatLimit());
                return;
            }
            islandService.markIslandActivity(player.getUniqueId());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (isMinecartItem(item.getType())) {
                    coreService.showMinecartLimitHint(player, island);
                } else {
                    coreService.showBoatLimitHint(player, island);
                }
            });
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

        if (block.getType() == Material.ENDER_CHEST) {
            if (!islandService.canUseContainerSetting(player.getUniqueId(), block.getLocation(), AccessSettings::isContainers)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Keine Container-Rechte.");
                return;
            }
        }
        if (isDoor(block.getType())) {
            if (!islandService.canUseBuildSetting(player.getUniqueId(), block.getLocation(), AccessSettings::isDoors)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "T\u00fcren sind hier gesperrt.");
                return;
            }
        }
        if (isTrapdoor(block.getType())) {
            if (!islandService.canUseBuildSetting(player.getUniqueId(), block.getLocation(), AccessSettings::isTrapdoors)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Trapdoors sind hier gesperrt.");
                return;
            }
        }
        if (isFenceGate(block.getType())) {
            if (!islandService.canUseBuildSetting(player.getUniqueId(), block.getLocation(), AccessSettings::isFenceGates)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Zauntore sind hier gesperrt.");
                return;
            }
        }
        if (isButton(block.getType())) {
            if (!islandService.canUseBuildSetting(player.getUniqueId(), block.getLocation(), AccessSettings::isButtons)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Buttons sind hier gesperrt.");
                return;
            }
        }
        if (isLever(block.getType())) {
            if (!islandService.canUseBuildSetting(player.getUniqueId(), block.getLocation(), AccessSettings::isLevers)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Hebel sind hier gesperrt.");
                return;
            }
        }
        if (isPressurePlate(block.getType())) {
            if (!islandService.canUseBuildSetting(player.getUniqueId(), block.getLocation(), AccessSettings::isPressurePlates)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Druckplatten sind hier gesperrt.");
                return;
            }
        }
        if (isFarmUseBlock(block.getType())) {
            if (!islandService.canUseBuildSetting(player.getUniqueId(), block.getLocation(), AccessSettings::isFarmUse)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Nutzbl\u00f6cke/Farmaktionen sind hier gesperrt.");
                return;
            }
        }
        if (isRedstoneControl(block.getType())) {
            if (!islandService.canUseRedstoneSetting(player.getUniqueId(), block.getLocation(), AccessSettings::isRedstoneUse)) {
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
        Location inventoryLocation = inventoryHolderLocation(event.getInventory().getHolder());
        if (playerListener.isActiveCtfShelfLocation(inventoryLocation)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "CTF-Regale k\u00f6nnen nicht direkt ge\u00f6ffnet werden.");
            return;
        }
        Object holder = event.getInventory().getHolder();
        Location location = event.getInventory().getLocation();
        if (location == null && holder instanceof Entity entity) {
            location = entity.getLocation();
        }
        if (location == null) return;
        IslandData island = islandService.getIslandAt(location);
        if (island == null) return;
        if (holder instanceof Container || holder instanceof DoubleChest) {
            if (!islandService.canUseContainerSetting(player.getUniqueId(), location, AccessSettings::isContainers)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Keine Container-Rechte.");
                return;
            }
            islandService.markIslandActivity(player.getUniqueId());
            return;
        }
        if (holder instanceof AbstractVillager) {
            if (!islandService.canUseBuildSetting(player.getUniqueId(), location, AccessSettings::isVillagers)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Villager sind hier gesperrt.");
                return;
            }
            islandService.markIslandActivity(player.getUniqueId());
            return;
        }
        if (holder instanceof Entity entity && !(holder instanceof Player)) {
            if (!islandService.canUseContainerSetting(player.getUniqueId(), entity.getLocation(), AccessSettings::isContainers)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Keine Container-Rechte.");
                return;
            }
            islandService.markIslandActivity(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player.isOp()) return;
        if (event.getClickedInventory() == null) return;
        Location inventoryLocation = inventoryHolderLocation(event.getView().getTopInventory().getHolder());
        if (!playerListener.isActiveCtfShelfLocation(inventoryLocation)) return;
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "Aus CTF-Regalen k\u00f6nnen keine Items entnommen werden.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player.isOp()) return;
        Location inventoryLocation = inventoryHolderLocation(event.getView().getTopInventory().getHolder());
        if (!playerListener.isActiveCtfShelfLocation(inventoryLocation)) return;
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (!touchesTop) return;
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "CTF-Regale sind f\u00fcr Spieler gesperrt.");
    }

    private Location inventoryHolderLocation(Object holder) {
        if (holder instanceof BlockState blockState) {
            return blockState.getLocation();
        }
        if (holder instanceof DoubleChest doubleChest) {
            return doubleChest.getLocation();
        }
        if (holder instanceof Entity entity) {
            return entity.getLocation();
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isOp()) return;
        if (!(event.getMount() instanceof Vehicle) && !(event.getMount() instanceof org.bukkit.entity.LivingEntity)) return;
        if (!islandService.canUseBuildSetting(player.getUniqueId(), event.getMount().getLocation(), AccessSettings::isRide)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Reiten ist hier gesperrt.");
            return;
        }
        islandService.markIslandActivity(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleCreate(VehicleCreateEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (!skyWorldService.isSkyCityWorld(vehicle.getWorld())) return;
        IslandData island = islandService.getIslandAt(vehicle.getLocation());
        if (island == null) {
            event.setCancelled(true);
            return;
        }
        if (vehicle instanceof org.bukkit.entity.Minecart && !islandService.isWithinMinecartLimit(island)) {
            event.setCancelled(true);
            return;
        }
        if (vehicle instanceof org.bukkit.entity.Boat && !islandService.isWithinBoatLimit(island)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!skyWorldService.isSkyCityWorld(event.getLocation().getWorld())) return;
        if (event.getEntity() instanceof Monster) {
            if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM && islandService.isLocationInActivePveZone(event.getLocation())) {
                return;
            }
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
        if (!event.isCancelled() && event.getEntity() instanceof Animals animals) {
            ensureAnimalCollision(animals);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!skyWorldService.isSkyCityWorld(event.getEntity().getWorld())) return;
        IslandData island = islandService.getIslandAt(event.getEntity().getLocation());
        if (island == null || !islandService.isWithinAnimalLimit(island)) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Animals)) return;
        ParcelData parcel = islandService.getParcelAt(island, event.getEntity().getLocation());
        if (parcel == null) {
            if (event.getBreeder() instanceof Player player && !player.isOp() && !islandService.hasBuildAccess(player.getUniqueId(), island)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Du darfst auf fremden Inseln keine Tiere vermehren.");
                return;
            }
        } else {
            if (event.getBreeder() instanceof Player player
                    && !player.isOp()
                    && !islandService.isParcelOwner(island, parcel, player.getUniqueId())
                    && !islandService.hasBuildAccess(player.getUniqueId(), island)
                    && (!islandService.isParcelMember(island, parcel, player.getUniqueId()) || !parcel.isMemberAnimalBreed())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Du darfst hier keine Tiere vermehren.");
                return;
            }
        }
        org.bukkit.entity.LivingEntity mother = event.getMother();
        org.bukkit.entity.LivingEntity father = event.getFather();
        
        String motherPairIcon = (Math.random() < 0.5D) ? "\uD83D\uDE18" : "\uD83D\uDE0F";
        String fatherPairIcon = (Math.random() < 0.5D) ? "\uD83D\uDE18" : "\uD83D\uDE0F";
        coreService.setTemporaryEntityEmotion(mother, ChatColor.LIGHT_PURPLE + motherPairIcon, 5000L);
        coreService.setTemporaryEntityEmotion(father, ChatColor.LIGHT_PURPLE + fatherPairIcon, 5000L);

        org.bukkit.Bukkit.getScheduler().runTaskLater(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()), () -> {
            if (mother.isValid()) coreService.setEntityEmotionUntilBreedReady(mother, "\uD83D\uDE0E");
            if (father.isValid()) coreService.setEntityEmotionUntilBreedReady(father, "\uD83D\uDE0E");
        }, 60L);

        if (event.getEntity() instanceof Animals baby) {
            ensureAnimalCollision(baby);
        }
        ChatColor babyColor = Math.random() < 0.5D ? ChatColor.AQUA : ChatColor.LIGHT_PURPLE;
        coreService.setEntityEmotionUntilAdult(event.getEntity(), babyColor + "\u2728\uD83D\uDC76\u2728");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShear(PlayerInteractEntityEvent event) {
        if (event.getHand() == null || !SHEARABLE_TYPES.contains(event.getRightClicked().getType())) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack usedItem = player.getInventory().getItem(event.getHand());
        if (usedItem == null || usedItem.getType() != Material.SHEARS) return;
        if (!skyWorldService.isSkyCityWorld(event.getRightClicked().getWorld())) return;
        IslandData island = islandService.getIslandAt(event.getRightClicked().getLocation());
        if (island == null) return;
        ParcelData parcel = islandService.getParcelAt(island, event.getRightClicked().getLocation());
        if (!canBypassIslandProtection(player) && parcel == null) {
            if (!islandService.hasBuildAccess(player.getUniqueId(), island)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Du darfst auf fremden Inseln keine Tiere scheren.");
                return;
            }
        } else if (!canBypassIslandProtection(player)) {
            if (!islandService.isParcelOwner(island, parcel, player.getUniqueId())
                    && !islandService.hasBuildAccess(player.getUniqueId(), island)
                    && (!islandService.isParcelMember(island, parcel, player.getUniqueId()) || !parcel.isMemberAnimalShear())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Du darfst hier nicht scheren.");
                return;
            }
        }
        coreService.setTemporaryEntityEmotion(event.getRightClicked(), ChatColor.WHITE + "\u2702", 4000L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnimalInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Animals animal)) return;
        Player player = event.getPlayer();
        if (!skyWorldService.isSkyCityWorld(animal.getWorld())) return;
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item != null && item.getType() == Material.SHEARS) return;
        IslandData island = islandService.getIslandAt(animal.getLocation());
        if (island == null) return;
        ParcelData parcel = islandService.getParcelAt(island, animal.getLocation());
        boolean breedingItem = item != null && animal.isBreedItem(item);
        
        if (!canBypassIslandProtection(player) && parcel == null) {
            if (!islandService.hasBuildAccess(player.getUniqueId(), island)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Du darfst auf fremden Inseln nicht mit Tieren interagieren.");
                return;
            }
        } else if (!canBypassIslandProtection(player)) {
            boolean hasAccess = islandService.isParcelOwner(island, parcel, player.getUniqueId()) || islandService.hasBuildAccess(player.getUniqueId(), island)
                    || (islandService.isParcelMember(island, parcel, player.getUniqueId()) && (breedingItem ? parcel.isMemberAnimalBreed() : parcel.isMemberAnimalShear()));
            if (!hasAccess) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Du darfst in diesem Plot nicht mit Tieren interagieren.");
                return;
            }
        }

        if (breedingItem) {
            if (!animal.isAdult()) {
                playRandomBabyClickEmotion(animal);
            } else if (animal.canBreed()) {
                coreService.setTemporaryEntityEmotion(animal, ChatColor.LIGHT_PURPLE + "\uD83D\uDE18", 5000L);
            } else if (!coreService.hasEntityEmotionUntilBreedReady(animal)) {
                coreService.setEntityEmotionUntilBreedReady(animal, "\uD83D\uDE0E");
            }
            return;
        }
        if (item != null && item.getType() == Material.BUCKET && (animal instanceof Cow || animal instanceof MushroomCow || animal instanceof Goat)) {
            if (coreService.hasEntityEmotionUntilBreedReady(animal)) {
                return;
            }
            coreService.setTemporaryEntityEmotion(animal, ChatColor.WHITE + "\uD83E\uDD5B\uD83D\uDE0B", 3500L);
            return;
        }
        if (!animal.isAdult()) {
            playRandomBabyClickEmotion(animal);
        } else if (item == null || item.getType().isAir()) {
            if (coreService.hasEntityEmotionUntilBreedReady(animal)) {
                return;
            }
            coreService.setTemporaryEntityEmotion(animal, ChatColor.RED + "\u2764", 2500L);
        }
    }

    private void playRandomBabyClickEmotion(Animals animal) {
        if (animal == null || animal.isAdult()) {
            return;
        }
        String emote = BABY_CLICK_EMOTES[ThreadLocalRandom.current().nextInt(BABY_CLICK_EMOTES.length)];
        ChatColor babyColor = getStoredBabyEmotionColor(animal);
        if (babyColor == null) {
            babyColor = ThreadLocalRandom.current().nextBoolean() ? ChatColor.AQUA : ChatColor.LIGHT_PURPLE;
        }
        coreService.setTemporaryEntityEmotion(animal, babyColor + emote, BABY_EMOTION_DURATION_MS);
    }

    private ChatColor getStoredBabyEmotionColor(Animals animal) {
        String text = coreService.getEntityEmotionUntilAdultText(animal);
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text != null && text.startsWith(ChatColor.LIGHT_PURPLE.toString())) {
            return ChatColor.LIGHT_PURPLE;
        }
        if (text != null && text.startsWith(ChatColor.AQUA.toString())) {
            return ChatColor.AQUA;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityEmotionDamage(EntityDamageEvent event) {
        if (!skyWorldService.isSkyCityWorld(event.getEntity().getWorld())) return;
        if (!(event.getEntity() instanceof Animals)
                && !islandService.isTrackedGolem(event.getEntityType())) {
            return;
        }
        coreService.setTemporaryEntityEmotion(event.getEntity(), ChatColor.RED + "\uD83D\uDE16", 2500L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity living = event.getEntity();
        if (!skyWorldService.isSkyCityWorld(living.getWorld())) return;
        if (islandService.handlePveMobDeath(living, living.getKiller())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        Location sourceLocation = event.getSource().getLocation();
        Location destinationLocation = event.getDestination().getLocation();
        if (sourceLocation == null || destinationLocation == null) return;
        if (!skyWorldService.isSkyCityWorld(sourceLocation.getWorld()) && !skyWorldService.isSkyCityWorld(destinationLocation.getWorld())) return;

        IslandData sourceIsland = islandService.getIslandAt(sourceLocation);
        IslandData destinationIsland = islandService.getIslandAt(destinationLocation);
        if (sourceIsland == null || destinationIsland == null) {
            event.setCancelled(true);
            return;
        }
        if (!sourceIsland.getOwner().equals(destinationIsland.getOwner())) {
            event.setCancelled(true);
            return;
        }

        ParcelData sourceParcel = islandService.getParcelAt(sourceIsland, sourceLocation);
        ParcelData destinationParcel = islandService.getParcelAt(destinationIsland, destinationLocation);
        if (!isSameParcelContext(sourceParcel, destinationParcel)) {
            event.setCancelled(true);
        }
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

            ParcelData sourceParcel = islandService.getParcelAt(sourceIsland, moved.getLocation());
            IslandData destinationIsland = islandService.getIslandAt(destination.getLocation());
            if (destinationIsland == null) return false;
            if (!destinationIsland.getOwner().equals(sourceIsland.getOwner())) return false;
            if (!islandService.isChunkUnlocked(sourceIsland, destination.getLocation())) return false;
            ParcelData destinationParcel = islandService.getParcelAt(destinationIsland, destination.getLocation());
            if (!isSameParcelContext(sourceParcel, destinationParcel)) return false;
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
                    GRINDSTONE, FLETCHING_TABLE, CRAFTING_TABLE, ENCHANTING_TABLE,
                    BREWING_STAND, BEACON, LECTERN, JUKEBOX, BELL,
                    RESPAWN_ANCHOR, DECORATED_POT, CRAFTER, BIG_DRIPLEAF -> true;
            default -> false;
        };
    }

    private boolean isSnowGameBlock(Material type) {
        return type == Material.SNOW || type == Material.SNOW_BLOCK;
    }

    private boolean isBannerGameBlock(Material type) {
        return type != null && type.name().endsWith("_BANNER");
    }

    private boolean canUseLeash(Player player, Location location, boolean villagerTarget) {
        if (player == null || location == null) return false;
        Predicate<AccessSettings> predicate = villagerTarget ? AccessSettings::isVillagers : AccessSettings::isRide;
        return islandService.canUseBuildSetting(player.getUniqueId(), location, predicate);
    }

    private boolean isSameParcelContext(ParcelData sourceParcel, ParcelData destinationParcel) {
        if (sourceParcel == null || destinationParcel == null) {
            return sourceParcel == null && destinationParcel == null;
        }
        return sourceParcel.getChunkKey().equals(destinationParcel.getChunkKey());
    }

    private Player resolveDamagingPlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private LivingEntity resolveDamagingLivingEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity living) return living;
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity living) return living;
        return null;
    }

    private void refreshAnimalCollisions() {
        World world = skyWorldService.getWorld();
        if (world == null) return;
        for (Animals animal : world.getEntitiesByClass(Animals.class)) {
            ensureAnimalCollision(animal);
        }
    }

    private void ensureAnimalCollision(Animals animal) {
        if (animal == null || !animal.isValid()) return;
        animal.setCollidable(true);
        attachAnimalCollisionTeam(animal);
    }

    private void attachAnimalCollisionTeam(Animals animal) {
        if (animal == null || Bukkit.getScoreboardManager() == null) return;
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        if (scoreboard == null) return;
        Team team = scoreboard.getTeam(ANIMAL_COLLISION_TEAM);
        if (team == null) {
            team = scoreboard.registerNewTeam(ANIMAL_COLLISION_TEAM);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);
        }
        String entry = animal.getUniqueId().toString();
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
    }

    private void pushAnimalAway(Player attacker, Animals animal) {
        if (attacker == null || animal == null || !animal.isValid()) return;
        Vector push = animal.getLocation().toVector().subtract(attacker.getLocation().toVector());
        push.setY(0.0);
        if (push.lengthSquared() < 1.0E-4) {
            push = attacker.getLocation().getDirection().clone();
            push.setY(0.0);
        }
        if (push.lengthSquared() < 1.0E-4) return;
        Vector velocity = push.normalize().multiply(0.42).setY(0.18);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (animal.isValid()) {
                animal.setVelocity(velocity);
            }
        });
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

    private boolean isMinecartItem(Material type) {
        return type == Material.MINECART
                || type == Material.CHEST_MINECART
                || type == Material.FURNACE_MINECART
                || type == Material.HOPPER_MINECART
                || type == Material.TNT_MINECART
                || type == Material.COMMAND_BLOCK_MINECART;
    }

    private boolean isBoatItem(Material type) {
        return type == Material.OAK_BOAT
                || type == Material.OAK_CHEST_BOAT
                || type == Material.SPRUCE_BOAT
                || type == Material.SPRUCE_CHEST_BOAT
                || type == Material.BIRCH_BOAT
                || type == Material.BIRCH_CHEST_BOAT
                || type == Material.JUNGLE_BOAT
                || type == Material.JUNGLE_CHEST_BOAT
                || type == Material.ACACIA_BOAT
                || type == Material.ACACIA_CHEST_BOAT
                || type == Material.DARK_OAK_BOAT
                || type == Material.DARK_OAK_CHEST_BOAT
                || type == Material.MANGROVE_BOAT
                || type == Material.MANGROVE_CHEST_BOAT
                || type == Material.CHERRY_BOAT
                || type == Material.CHERRY_CHEST_BOAT
                || type == Material.BAMBOO_RAFT
                || type == Material.BAMBOO_CHEST_RAFT;
    }
}



