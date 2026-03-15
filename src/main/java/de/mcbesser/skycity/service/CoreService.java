package de.mcbesser.skycity.service;

import de.mcbesser.skycity.SkyCityPlugin;
import de.mcbesser.skycity.model.AccessSettings;
import de.mcbesser.skycity.model.IslandData;
import de.mcbesser.skycity.model.IslandLevelDefinition;
import de.mcbesser.skycity.model.ParcelData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ClickEvent.Action;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

public class CoreService {
   public static final String CORE_MENU_TITLE = "SkyCity Core";
   public static final String ISLAND_MENU_TITLE = "SkyCity Insel";
   public static final int[] INPUT_SLOTS = new int[]{27, 28, 29, 30, 31, 32, 33, 34, 35};
   private static final List<Integer> GRID_SLOTS = List.of(
      0,
      1,
      2,
      3,
      4,
      5,
      6,
      7,
      8,
      9,
      10,
      11,
      12,
      13,
      14,
      15,
      16,
      17,
      18,
      19,
      20,
      21,
      22,
      23,
      24,
      25,
      26,
      27,
      28,
      29,
      30,
      31,
      32,
      33,
      34,
      35,
      36,
      37,
      38,
      39,
      40,
      41,
      42,
      43,
      44
   );
   private static final int ISLAND_CHUNK_SIDE = 64;
   private static final int TOTAL_CHUNKS = ISLAND_CHUNK_SIDE * ISLAND_CHUNK_SIDE;
   private static final List<int[]> CHUNK_SPIRAL_ORDER = buildChunkSpiralOrder();
   private static final List<Biome> BIOME_OPTIONS = Arrays.asList(
      Biome.PLAINS,
      Biome.FOREST,
      Biome.BIRCH_FOREST,
      Biome.DARK_FOREST,
      Biome.JUNGLE,
      Biome.SPARSE_JUNGLE,
      Biome.BAMBOO_JUNGLE,
      Biome.TAIGA,
      Biome.SNOWY_PLAINS,
      Biome.ICE_SPIKES,
      Biome.DESERT,
      Biome.SAVANNA,
      Biome.SAVANNA_PLATEAU,
      Biome.BADLANDS,
      Biome.WOODED_BADLANDS,
      Biome.MUSHROOM_FIELDS,
      Biome.SWAMP,
      Biome.MANGROVE_SWAMP,
      Biome.MEADOW,
      Biome.GROVE,
      Biome.CHERRY_GROVE,
      Biome.WINDSWEPT_HILLS,
      Biome.WINDSWEPT_FOREST,
      Biome.STONY_PEAKS,
      Biome.JAGGED_PEAKS
   );
   private static final Map<Material, String> DE_MATERIAL_NAMES = new HashMap<>();
   private static final Map<Biome, String> DE_BIOME_NAMES = new HashMap<>();
   private static final Map<Material, Double> BLOCK_VALUE_MAP = new LinkedHashMap<>();
   private static final double BLOCK_VALUE_DEFAULT = 1.0E-4;
   private static final Set<Material> BLOCK_VALUE_BLACKLIST = EnumSet.noneOf(Material.class);
   private static final long LIMIT_HINT_DURATION_TICKS = 50L;
   private final SkyCityPlugin plugin;
   private final IslandService islandService;
   private final Set<UUID> visibleAnimalLookTargets = ConcurrentHashMap.newKeySet();
   private final Map<UUID, UUID> playerAnimalLookTargets = new ConcurrentHashMap<>();
   private final Map<UUID, BossBar> limitHintBossBars = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> limitHintHideTasks = new ConcurrentHashMap<>();
   private final Map<UUID, UUID> pendingIslandTitleInput = new ConcurrentHashMap<>();
   private final Map<UUID, UUID> pendingIslandWarpInput = new ConcurrentHashMap<>();
   private final Map<UUID, String> pendingParcelRenameInput = new ConcurrentHashMap<>();
   private int displayTaskId = -1;
   private int animalLookTaskId = -1;

   public CoreService(SkyCityPlugin plugin, IslandService islandService) {
      this.plugin = plugin;
      this.islandService = islandService;
   }

   private static void loadPrototypeBlockValues() {
      addPrototypeBlockValue("BEACON", 100.0);
      addPrototypeBlockValue("DIAMOND_BLOCK", 5.0);
      addPrototypeBlockValue("EMERALD_BLOCK", 2.5);
      addPrototypeBlockValue("ENDER_CHEST", 2.0);
      addPrototypeBlockValue("GOLD_BLOCK", 1.5);
      addPrototypeBlockValue("PLAYER_HEAD", 1.0);
      addPrototypeBlockValue("COAL_BLOCK", 0.5);
      addPrototypeBlockValue("IRON_BLOCK", 0.5);
      addPrototypeBlockValue("REDSTONE_BLOCK", 0.5);
      addPrototypeBlockValue("DIAMOND_ORE", 0.4);
      addPrototypeBlockValue("LAPIS_BLOCK", 0.3);
      addPrototypeBlockValue("LAPIS_ORE", 0.3);
      addPrototypeBlockValue("REDSTONE_ORE", 0.3);
      addPrototypeBlockValue("EMERALD_ORE", 0.2);
      addPrototypeBlockValue("OBSIDIAN", 0.2);
      addPrototypeBlockValue("ENCHANTING_TABLE", 0.15);
      addPrototypeBlockValue("ANVIL", 0.15);
      addPrototypeBlockValue("GOLD_ORE", 0.12);
      addPrototypeBlockValue("COAL_ORE", 0.1);
      addPrototypeBlockValue("MUSHROOM_BLOCK", 0.1);
      addPrototypeBlockValue("QUARTZ_BLOCK", 0.1);
      addPrototypeBlockValue("QUARTZ_SLAB", 0.1);
      addPrototypeBlockValue("SEA_LANTERN", 0.1);
      addPrototypeBlockValue("SAND", 0.08);
      addPrototypeBlockValue("RED_SAND", 0.08);
      addPrototypeBlockValue("RED_SANDSTONE", 0.08);
      addPrototypeBlockValue("CHISELED_RED_SANDSTONE", 0.08);
      addPrototypeBlockValue("SMOOTH_RED_SANDSTONE", 0.08);
      addPrototypeBlockValue("RED_SANDSTONE_STAIRS", 0.08);
      addPrototypeBlockValue("TNT", 0.08);
      addPrototypeBlockValue("STICKY_PISTON", 0.06);
      addPrototypeBlockValue("CARPET", 0.06);
      addPrototypeBlockValue("STAINED_CARPET", 0.06);
      addPrototypeBlockValue("GLASS", 0.05);
      addPrototypeBlockValue("STAINED_GLASS", 0.05);
      addPrototypeBlockValue("WOOL", 0.05);
      addPrototypeBlockValue("STAINED_WOOL", 0.05);
      addPrototypeBlockValue("BOOKSHELF", 0.05);
      addPrototypeBlockValue("COBWEB", 0.05);
      addPrototypeBlockValue("ICE", 0.05);
      addPrototypeBlockValue("JUKEBOX", 0.05);
      addPrototypeBlockValue("CAKE", 0.05);
      addPrototypeBlockValue("NETHER_QUARTZ_ORE", 0.05);
      addPrototypeBlockValue("PURPUR_BLOCK", 0.05);
      addPrototypeBlockValue("PURPUR_PILLAR", 0.05);
      addPrototypeBlockValue("NOTE_BLOCK", 0.05);
      addPrototypeBlockValue("PISTON", 0.04);
      addPrototypeBlockValue("FARMLAND", 0.04);
      addPrototypeBlockValue("REDSTONE_LAMP", 0.04);
      addPrototypeBlockValue("BRICKS", 0.04);
      addPrototypeBlockValue("BRICK_STAIRS", 0.04);
      addPrototypeBlockValue("RED_SANDSTONE_SLAB", 0.04);
      addPrototypeBlockValue("PACKED_ICE", 0.03);
      addPrototypeBlockValue("PURPUR_STAIRS", 0.03);
      addPrototypeBlockValue("IRON_ORE", 0.03);
      addPrototypeBlockValue("MOSSY_COBBLESTONE", 0.03);
      addPrototypeBlockValue("PURPUR_SLAB", 0.025);
      addPrototypeBlockValue("PRISMARINE", 0.02);
      addPrototypeBlockValue("HAY_BLOCK", 0.02);
      addPrototypeBlockValue("SPONGE", 0.02);
      addPrototypeBlockValue("DIRT", 0.02);
      addPrototypeBlockValue("GRASS_BLOCK", 0.02);
      addPrototypeBlockValue("MYCELIUM", 0.02);
      addPrototypeBlockValue("SLIME_BLOCK", 0.02);
      addPrototypeBlockValue("GRAVEL", 0.02);
      addPrototypeBlockValue("SANDSTONE", 0.02);
      addPrototypeBlockValue("SANDSTONE_STAIRS", 0.02);
      addPrototypeBlockValue("POWERED_RAIL", 0.02);
      addPrototypeBlockValue("DETECTOR_RAIL", 0.02);
      addPrototypeBlockValue("ACTIVATOR_RAIL", 0.02);
      addPrototypeBlockValue("IRON_BARS", 0.02);
      addPrototypeBlockValue("GLASS_PANE", 0.02);
      addPrototypeBlockValue("STAINED_GLASS_PANE", 0.02);
      addPrototypeBlockValue("JACK_O_LANTERN", 0.02);
      addPrototypeBlockValue("NETHER_BRICKS", 0.02);
      addPrototypeBlockValue("NETHER_BRICK_STAIRS", 0.02);
      addPrototypeBlockValue("STONE_BRICKS", 0.02);
      addPrototypeBlockValue("STONE_BRICK_STAIRS", 0.02);
      addPrototypeBlockValue("STONE_SLAB", 0.02);
      addPrototypeBlockValue("GLOWSTONE", 0.02);
      addPrototypeBlockValue("END_ROD", 0.02);
      addPrototypeBlockValue("NETHERRACK", 0.02);
      addPrototypeBlockValue("NETHER_BRICK", 0.02);
      addPrototypeBlockValue("NETHER_BRICK_SLAB", 0.02);
      addPrototypeBlockValue("SOUL_SAND", 0.02);
      addPrototypeBlockValue("END_STONE", 0.02);
      addPrototypeBlockValue("SNOW", 0.02);
      addPrototypeBlockValue("TERRACOTTA", 0.02);
      addPrototypeBlockValue("STAINED_TERRACOTTA", 0.02);
      addPrototypeBlockValue("CLAY", 0.02);
      addPrototypeBlockValue("OAK_LOG", 0.02);
      addPrototypeBlockValue("SPRUCE_LOG", 0.02);
      addPrototypeBlockValue("BIRCH_LOG", 0.02);
      addPrototypeBlockValue("JUNGLE_LOG", 0.02);
      addPrototypeBlockValue("ACACIA_LOG", 0.02);
      addPrototypeBlockValue("DARK_OAK_LOG", 0.02);
      addPrototypeBlockValue("VINE", 0.01);
      addPrototypeBlockValue("PUMPKIN", 0.01);
      addPrototypeBlockValue("MELON", 0.01);
      addPrototypeBlockValue("CACTUS", 0.01);
      addPrototypeBlockValue("CHORUS_FLOWER", 0.01);
      addPrototypeBlockValue("CHORUS_PLANT", 0.01);
      addPrototypeBlockValue("COBBLESTONE_STAIRS", 0.01);
      addPrototypeBlockValue("LEAVES", 0.01);
      addPrototypeBlockValue("OAK_PLANKS", 0.01);
      addPrototypeBlockValue("SPRUCE_PLANKS", 0.01);
      addPrototypeBlockValue("BIRCH_PLANKS", 0.01);
      addPrototypeBlockValue("JUNGLE_PLANKS", 0.01);
      addPrototypeBlockValue("ACACIA_PLANKS", 0.01);
      addPrototypeBlockValue("DARK_OAK_PLANKS", 0.01);
      addPrototypeBlockValue("COBBLESTONE", 0.005);
      addPrototypeBlockValue("COBBLESTONE_WALL", 0.005);
   }

   private static void addPrototypeBlockValue(String key, double value) {
      switch (key) {
         case "CARPET":
         case "STAINED_CARPET":
            addValueToAll(
               value,
               "WHITE_CARPET",
               "LIGHT_GRAY_CARPET",
               "GRAY_CARPET",
               "BLACK_CARPET",
               "BROWN_CARPET",
               "RED_CARPET",
               "ORANGE_CARPET",
               "YELLOW_CARPET",
               "LIME_CARPET",
               "GREEN_CARPET",
               "CYAN_CARPET",
               "LIGHT_BLUE_CARPET",
               "BLUE_CARPET",
               "PURPLE_CARPET",
               "MAGENTA_CARPET",
               "PINK_CARPET"
            );
            break;
         case "WOOL":
         case "STAINED_WOOL":
            addValueToAll(
               value,
               "WHITE_WOOL",
               "LIGHT_GRAY_WOOL",
               "GRAY_WOOL",
               "BLACK_WOOL",
               "BROWN_WOOL",
               "RED_WOOL",
               "ORANGE_WOOL",
               "YELLOW_WOOL",
               "LIME_WOOL",
               "GREEN_WOOL",
               "CYAN_WOOL",
               "LIGHT_BLUE_WOOL",
               "BLUE_WOOL",
               "PURPLE_WOOL",
               "MAGENTA_WOOL",
               "PINK_WOOL"
            );
            break;
         case "STAINED_GLASS":
            addValueToAll(
               value,
               "WHITE_STAINED_GLASS",
               "LIGHT_GRAY_STAINED_GLASS",
               "GRAY_STAINED_GLASS",
               "BLACK_STAINED_GLASS",
               "BROWN_STAINED_GLASS",
               "RED_STAINED_GLASS",
               "ORANGE_STAINED_GLASS",
               "YELLOW_STAINED_GLASS",
               "LIME_STAINED_GLASS",
               "GREEN_STAINED_GLASS",
               "CYAN_STAINED_GLASS",
               "LIGHT_BLUE_STAINED_GLASS",
               "BLUE_STAINED_GLASS",
               "PURPLE_STAINED_GLASS",
               "MAGENTA_STAINED_GLASS",
               "PINK_STAINED_GLASS"
            );
            break;
         case "STAINED_GLASS_PANE":
            addValueToAll(
               value,
               "WHITE_STAINED_GLASS_PANE",
               "LIGHT_GRAY_STAINED_GLASS_PANE",
               "GRAY_STAINED_GLASS_PANE",
               "BLACK_STAINED_GLASS_PANE",
               "BROWN_STAINED_GLASS_PANE",
               "RED_STAINED_GLASS_PANE",
               "ORANGE_STAINED_GLASS_PANE",
               "YELLOW_STAINED_GLASS_PANE",
               "LIME_STAINED_GLASS_PANE",
               "GREEN_STAINED_GLASS_PANE",
               "CYAN_STAINED_GLASS_PANE",
               "LIGHT_BLUE_STAINED_GLASS_PANE",
               "BLUE_STAINED_GLASS_PANE",
               "PURPLE_STAINED_GLASS_PANE",
               "MAGENTA_STAINED_GLASS_PANE",
               "PINK_STAINED_GLASS_PANE"
            );
            break;
         case "STAINED_TERRACOTTA":
            addValueToAll(
               value,
               "WHITE_TERRACOTTA",
               "LIGHT_GRAY_TERRACOTTA",
               "GRAY_TERRACOTTA",
               "BLACK_TERRACOTTA",
               "BROWN_TERRACOTTA",
               "RED_TERRACOTTA",
               "ORANGE_TERRACOTTA",
               "YELLOW_TERRACOTTA",
               "LIME_TERRACOTTA",
               "GREEN_TERRACOTTA",
               "CYAN_TERRACOTTA",
               "LIGHT_BLUE_TERRACOTTA",
               "BLUE_TERRACOTTA",
               "PURPLE_TERRACOTTA",
               "MAGENTA_TERRACOTTA",
               "PINK_TERRACOTTA"
            );
            break;
         case "LEAVES":
            addValueToAll(
               value,
               "OAK_LEAVES",
               "SPRUCE_LEAVES",
               "BIRCH_LEAVES",
               "JUNGLE_LEAVES",
               "ACACIA_LEAVES",
               "DARK_OAK_LEAVES",
               "MANGROVE_LEAVES",
               "CHERRY_LEAVES",
               "AZALEA_LEAVES",
               "FLOWERING_AZALEA_LEAVES"
            );
            break;
         case "MUSHROOM_BLOCK":
            addValueToAll(value, "RED_MUSHROOM_BLOCK", "BROWN_MUSHROOM_BLOCK", "MUSHROOM_STEM");
            break;
         default:
            addValueToMaterial(key, value);
      }
   }

   private static void addValueToAll(double value, String... materialNames) {
      for (String name : materialNames) {
         addValueToMaterial(name, value);
      }
   }

   private static void addValueToMaterial(String materialName, double value) {
      Material material = Material.matchMaterial(materialName);
      if (material != null) {
         BLOCK_VALUE_MAP.put(material, value);
      }
   }

   private static List<int[]> buildChunkSpiralOrder() {
      List<int[]> order = new ArrayList<>(TOTAL_CHUNKS);
      int centerX = ISLAND_CHUNK_SIDE / 2 - 1;
      int centerZ = ISLAND_CHUNK_SIDE / 2 - 1;
      int x = centerX;
      int z = centerZ;
      int dx = 1;
      int dz = 0;
      int segmentLength = 1;
      int segmentProgress = 0;
      int segmentRepeats = 0;

      while (order.size() < TOTAL_CHUNKS) {
         if (x >= 0 && x < ISLAND_CHUNK_SIDE && z >= 0 && z < ISLAND_CHUNK_SIDE) {
            order.add(new int[]{x, z});
         }

         x += dx;
         z += dz;
         segmentProgress++;
         if (segmentProgress == segmentLength) {
            segmentProgress = 0;
            int oldDx = dx;
            dx = -dz;
            dz = oldDx;
            segmentRepeats++;
            if (segmentRepeats == 2) {
               segmentRepeats = 0;
               segmentLength++;
            }
         }
      }

      return order;
   }

   public void registerRecipe() {
      NamespacedKey key = new NamespacedKey(this.plugin, "skycity_core");
      ShapedRecipe recipe = new ShapedRecipe(key, this.createCoreItem());
      recipe.shape(new String[]{"CCC", "CSC", "CCC"});
      recipe.setIngredient('C', Material.COBBLESTONE);
      recipe.setIngredient('S', Material.CHEST);
      Bukkit.removeRecipe(key);
      Bukkit.addRecipe(recipe);
   }

   public ItemStack createCoreItem() {
      ItemStack item = new ItemStack(Material.SHULKER_BOX);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(ChatColor.AQUA + "SkyCity Core");
      meta.addEnchant(Enchantment.DURABILITY, 1, true);
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
      meta.getPersistentDataContainer().set(this.plugin.getCoreItemKey(), PersistentDataType.BYTE, (byte)1);
      item.setItemMeta(meta);
      return item;
   }

   public boolean isCoreItem(ItemStack item) {
      if (item != null && item.hasItemMeta()) {
         Byte b = (Byte)item.getItemMeta().getPersistentDataContainer().get(this.plugin.getCoreItemKey(), PersistentDataType.BYTE);
         return b != null && b == 1;
      } else {
         return false;
      }
   }

   public void markPlacedCore(Block block, UUID owner) {
      if (block.getState() instanceof ShulkerBox shulker) {
         PersistentDataContainer pdc = shulker.getPersistentDataContainer();
         pdc.set(this.plugin.getCoreBlockKey(), PersistentDataType.STRING, owner.toString());
         if (!pdc.has(this.plugin.getCoreDisplayModeKey(), PersistentDataType.STRING)) {
            pdc.set(this.plugin.getCoreDisplayModeKey(), PersistentDataType.STRING, CoreService.CoreDisplayMode.ALL.name());
         }

         shulker.setCustomName("SkyCity Core");
         shulker.update(true, false);
      }
   }

   public boolean isCoreBlock(Block block) {
      return block.getState() instanceof ShulkerBox shulker
         ? shulker.getPersistentDataContainer().has(this.plugin.getCoreBlockKey(), PersistentDataType.STRING)
         : false;
   }

   public Optional<UUID> getCoreOwner(Block block) {
      if (block.getState() instanceof ShulkerBox shulker) {
         String var6 = (String)shulker.getPersistentDataContainer().get(this.plugin.getCoreBlockKey(), PersistentDataType.STRING);

         try {
            return var6 == null ? Optional.empty() : Optional.of(UUID.fromString(var6));
         } catch (Exception var5) {
            return Optional.empty();
         }
      } else {
         return Optional.empty();
      }
   }

   public void ensureCorePlaced(IslandData island) {
      if (island.getCoreLocation() != null) {
         Block b = island.getCoreLocation().getBlock();
         if (b.getType().isAir()) {
            b.setType(Material.SHULKER_BOX, false);
         }

         if (b.getType().name().endsWith("SHULKER_BOX")) {
            this.markPlacedCore(b, island.getOwner());
         }
      }
   }

   public void refreshCoreDisplay(IslandData island) {
      if (island != null) {
         this.updateDisplays(island);
      }
   }

   public void removeCoreDisplays(IslandData island) {
      if (island != null) {
         this.removeTaggedDisplaysByPrefixInIsland(island, "skycity_core_");
      }
   }

   public Inventory createCoreMenu(Player viewer, IslandData island) {
      return this.createCoreMenu(viewer, island, island == null ? null : island.getCoreLocation());
   }

   public Inventory createCoreMenu(Player viewer, IslandData island, Location selectedCoreLocation) {
      this.islandService.rebuildPlacementCaches(island);
      Location coreLoc = this.resolveSelectedCoreLocation(island, selectedCoreLocation, viewer == null ? null : viewer.getLocation());
      String worldName = coreLoc != null && coreLoc.getWorld() != null ? coreLoc.getWorld().getName() : null;
      int bx = coreLoc == null ? 0 : coreLoc.getBlockX();
      int by = coreLoc == null ? 0 : coreLoc.getBlockY();
      int bz = coreLoc == null ? 0 : coreLoc.getBlockZ();
      Inventory inv = Bukkit.createInventory(new CoreService.CoreInventoryHolder(island.getOwner(), worldName, bx, by, bz), 54, "SkyCity Core");
      this.fillWithPanes(inv);
      inv.setItem(10, this.named(Material.NETHER_STAR, ChatColor.GOLD + "Core", this.buildCoreSummaryLore(island)));
      inv.setItem(
         11,
         this.named(
            Material.LECTERN,
            ChatColor.GOLD + "Insel-Level & Blockwertigkeit",
            List.of(
               ChatColor.GREEN + "Insel-Level: " + ChatColor.WHITE + this.formatIslandLevel(this.islandService.calculateIslandLevelValue(island)),
               ChatColor.GRAY + "Klick: Blockwertigkeit anzeigen",
               ChatColor.GRAY + "Shift-Klick: Eingelagerte Inselbl\u00f6cke"
            )
         )
      );
      inv.setItem(
         16,
         this.named(
            Material.EXPERIENCE_BOTTLE,
            ChatColor.AQUA + "Erfahrungsspeicher",
            List.of(
               ChatColor.GREEN + "Gespeichert: " + ChatColor.WHITE + island.getStoredExperience(),
               ChatColor.GRAY + "Linksklick: gesamte Spieler-EXP einlagern",
               ChatColor.GRAY + "Rechtsklick: 1 Level auszahlen",
               ChatColor.GRAY + "Shift-Rechtsklick: 10 Level auszahlen"
            )
         )
      );
      inv.setItem(
         14,
         this.named(
            Material.CHEST,
            ChatColor.AQUA + "CoreBank",
            List.of(ChatColor.GRAY + "Items in Slots 27-35 legen", ChatColor.GRAY + "Upgrade wird priorisiert, Rest z\u00e4hlt als Insel-Level")
         )
      );
      inv.setItem(
         13,
         this.named(
            Material.BOOK,
            ChatColor.YELLOW + "Fortschritt",
            List.of(
               ChatColor.GRAY + "Alle Upgrades im \u00dcberblick",
               ChatColor.GRAY + "Status, Voraussetzungen, Belohnungen",
               ChatColor.YELLOW + "Klick = Fortschritt \u00f6ffnen"
            )
         )
      );
      CoreService.CoreDisplayMode mode = this.getCoreDisplayMode(island, coreLoc);
      inv.setItem(
         15,
         this.named(
            Material.END_CRYSTAL,
            ChatColor.LIGHT_PURPLE + "Core-Anzeige",
            List.of(
               ChatColor.GRAY + "Aktuell: " + ChatColor.WHITE + this.displayModeLabel(mode),
               ChatColor.YELLOW + "Klick: umschalten",
               ChatColor.GRAY + "Alles / Nur Titel / Inselinfos / Upgrade / Aus"
            )
         )
      );
      IslandLevelDefinition next = this.islandService.getNextLevelDef(island);
      if (next == null) {
         inv.setItem(12, this.named(Material.EMERALD_BLOCK, ChatColor.GREEN + "Max Level", List.of(ChatColor.GRAY + "Kein weiteres Upgrade")));
      } else {
         List<String> lore = this.buildUpgradeLore(island);
         lore.add(" ");
         lore.add(ChatColor.YELLOW + "Klick zum Upgraden");
         inv.setItem(12, this.named(Material.ANVIL, ChatColor.AQUA + "Upgrade", lore));
      }

      for (int slot : INPUT_SLOTS) {
         inv.setItem(slot, null);
      }

      return inv;
   }

   public Inventory createUpgradeProgressMenu(IslandData island, int page) {
      List<IslandLevelDefinition> defs = this.islandService.getLevelDefinitions().values().stream().sorted((a, b) -> Integer.compare(a.getLevel(), b.getLevel())).toList();
      int totalPages = Math.max(1, (int)Math.ceil((double)defs.size() / 45.0));
      int safePage = Math.max(0, Math.min(totalPages - 1, page));
      Inventory inv = Bukkit.createInventory(new UpgradeProgressInventoryHolder(island.getOwner(), safePage), 54, "Fortschritt " + (safePage + 1) + "/" + totalPages);
      this.fillWithPanes(inv);
      int start = safePage * 45;
      double islandLevelScore = this.islandService.calculateIslandLevelValue(island);
      double reservedLevelScore = this.islandService.calculateReservedUpgradeLevelValue(island);

      for (int i = 0; i < 45; i++) {
         int idx = start + i;
         if (idx >= defs.size()) break;
         IslandLevelDefinition def = defs.get(idx);
         inv.setItem(GRID_SLOTS.get(i), this.createUpgradeProgressItem(island, def, islandLevelScore, reservedLevelScore));
      }
      if (safePage > 0) inv.setItem(48, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Vorherige Seite", List.of()));
      inv.setItem(49, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of(ChatColor.GRAY + "Zum Core")));
      if (safePage < totalPages - 1) inv.setItem(50, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "N\u00e4chste Seite", List.of()));
      return inv;
   }

   private ItemStack createUpgradeProgressItem(IslandData island, IslandLevelDefinition def, double islandLevelScore, double reservedLevelScore) {
      int currentLevel = island.getLevel();
      boolean completed = def.getLevel() < currentLevel;
      boolean current = def.getLevel() == currentLevel;
      long reqIslandLevel = this.islandService.requiredIslandLevelForUpgrade(def.getLevel());
      long reqXp = this.islandService.requiredExperienceForLevel(def.getLevel());
      List<String> lore = new ArrayList<>();
      lore.add(ChatColor.GRAY + "Stufe: " + ChatColor.WHITE + Math.max(0, def.getLevel() - 1));
      lore.add(ChatColor.GRAY + "Status: " + (completed ? ChatColor.GREEN + "erledigt" : current ? ChatColor.YELLOW + "aktuell" : ChatColor.RED + "offen"));
      lore.add(" ");
      lore.add(ChatColor.GOLD + "Voraussetzungen");
      if (def.getLevel() <= 1) {
         lore.add(ChatColor.GREEN + "Startstufe");
      } else {
         ChatColor islandLevelColor = islandLevelScore >= (double)reqIslandLevel ? ChatColor.GREEN : ChatColor.RED;
         ChatColor xpColor = island.getStoredExperience() >= reqXp ? ChatColor.GREEN : ChatColor.RED;
         lore.add(ChatColor.GRAY + "Insel-Level: " + islandLevelColor + this.formatIslandLevelWithReserved(islandLevelScore, reservedLevelScore) + ChatColor.WHITE + "/" + reqIslandLevel);
         lore.add(ChatColor.GRAY + "Core-EXP: " + xpColor + island.getStoredExperience() + ChatColor.WHITE + "/" + reqXp);
         if (def.getRequirements().isEmpty()) {
            lore.add(ChatColor.GRAY + "Items: " + ChatColor.GREEN + "keine");
         } else {
            for (Entry<Material, Integer> req : def.getRequirements().entrySet()) {
               int cur = island.getProgress(req.getKey());
               ChatColor curColor = cur >= req.getValue() ? ChatColor.GREEN : ChatColor.RED;
               lore.add(ChatColor.GRAY + this.materialDisplayNameDe(req.getKey()) + ": " + curColor + cur + ChatColor.WHITE + "/" + req.getValue());
            }
         }
      }
      lore.add(" ");
      lore.add(ChatColor.GOLD + "Belohnungen");
      lore.add(ChatColor.GRAY + "Chunk-Unlocks: " + ChatColor.AQUA + "+" + def.getChunkUnlocksGranted());
      IslandLevelDefinition prev = def.getLevel() <= 1 ? def : this.islandService.getLevelDefinitions().getOrDefault(def.getLevel() - 1, def);
      if (def.getAnimalLimit() != prev.getAnimalLimit()) lore.add(ChatColor.GRAY + "Tierlimit: " + ChatColor.AQUA + prev.getAnimalLimit() + " -> " + def.getAnimalLimit());
      if (def.getGolemLimit() != prev.getGolemLimit()) lore.add(ChatColor.GRAY + "Golemlimit: " + ChatColor.AQUA + prev.getGolemLimit() + " -> " + def.getGolemLimit());
      if (def.getVillagerLimit() != prev.getVillagerLimit()) lore.add(ChatColor.GRAY + "Villagerlimit: " + ChatColor.AQUA + prev.getVillagerLimit() + " -> " + def.getVillagerLimit());
      if (def.getHopperLimit() != prev.getHopperLimit()) lore.add(ChatColor.GRAY + "Hopperlimit: " + ChatColor.AQUA + prev.getHopperLimit() + " -> " + def.getHopperLimit());
      if (def.getPistonLimit() != prev.getPistonLimit()) lore.add(ChatColor.GRAY + "Kolbenlimit: " + ChatColor.AQUA + prev.getPistonLimit() + " -> " + def.getPistonLimit());
      if (def.getArmorStandLimit() != prev.getArmorStandLimit()) lore.add(ChatColor.GRAY + "Ruestungsstaenderlimit: " + ChatColor.AQUA + prev.getArmorStandLimit() + " -> " + def.getArmorStandLimit());
      if (def.getObserverLimit() != prev.getObserverLimit()) lore.add(ChatColor.GRAY + "Observerlimit: " + ChatColor.AQUA + prev.getObserverLimit() + " -> " + def.getObserverLimit());
      if (def.getDispenserLimit() != prev.getDispenserLimit()) lore.add(ChatColor.GRAY + "Dispenserlimit: " + ChatColor.AQUA + prev.getDispenserLimit() + " -> " + def.getDispenserLimit());
      if (def.getCactusLimit() != prev.getCactusLimit()) lore.add(ChatColor.GRAY + "Kaktuslimit: " + ChatColor.AQUA + prev.getCactusLimit() + " -> " + def.getCactusLimit());
      if (def.getKelpLimit() != prev.getKelpLimit()) lore.add(ChatColor.GRAY + "Kelplimit: " + ChatColor.AQUA + prev.getKelpLimit() + " -> " + def.getKelpLimit());
      if (def.getBambooLimit() != prev.getBambooLimit()) lore.add(ChatColor.GRAY + "Bambuslimit: " + ChatColor.AQUA + prev.getBambooLimit() + " -> " + def.getBambooLimit());

      Material icon = completed ? Material.LIME_STAINED_GLASS : current ? Material.CLOCK : Material.ANVIL;
      return this.named(icon, (completed ? ChatColor.GREEN : current ? ChatColor.YELLOW : ChatColor.AQUA) + "Upgrade " + Math.max(0, def.getLevel() - 1), lore);
   }

   private List<String> buildCoreSummaryLore(IslandData island) {
      IslandLevelDefinition current = this.islandService.getCurrentLevelDef(island);
      int upgradeLevel = Math.max(0, island.getLevel() - 1);
      double islandLevelScore = this.islandService.calculateIslandLevelValue(island);
      double reservedLevelScore = this.islandService.calculateReservedUpgradeLevelValue(island);
      List<String> lore = new ArrayList<>();
      lore.add(ChatColor.GREEN + "Insel-Level: " + ChatColor.WHITE + this.formatIslandLevelWithReserved(islandLevelScore, reservedLevelScore));
      lore.add(ChatColor.GREEN + "Erfahrung: " + ChatColor.WHITE + island.getStoredExperience());
      lore.add(
         ChatColor.GREEN
            + "Chunks: "
            + ChatColor.WHITE
            + island.getUnlockedChunks().size()
            + ChatColor.GRAY
            + " ("
            + island.getAvailableChunkUnlocks()
            + ")"
            + ChatColor.WHITE
            + " / "
            + this.islandService.getTotalIslandChunkCount()
      );
      lore.add(ChatColor.GREEN + "Tierlimit: " + ChatColor.WHITE + this.islandService.getAnimalCount(island) + "/" + current.getAnimalLimit());
      lore.add(ChatColor.GREEN + "Golemlimit: " + ChatColor.WHITE + this.islandService.getGolemCount(island) + "/" + current.getGolemLimit());
      lore.add(ChatColor.GREEN + "Villagerlimit: " + ChatColor.WHITE + this.islandService.getVillagerCount(island) + "/" + current.getVillagerLimit());
      lore.add(ChatColor.GREEN + "Upgrade-Level: " + ChatColor.WHITE + upgradeLevel);
      lore.add(ChatColor.GREEN + "Beh\u00e4lter: " + ChatColor.WHITE + this.islandService.getCachedInventoryBlockCount(island) + "/100");
      lore.add(ChatColor.GREEN + "Trichter: " + ChatColor.WHITE + this.islandService.getCachedHopperCount(island) + "/" + current.getHopperLimit());
      lore.add(ChatColor.GREEN + "Kolben: " + ChatColor.WHITE + this.islandService.getCachedPistonCount(island) + "/" + current.getPistonLimit());
      lore.add(ChatColor.GREEN + "Ruestungsstaender: " + ChatColor.WHITE + this.islandService.getArmorStandCount(island) + "/" + current.getArmorStandLimit());
      lore.add(ChatColor.GREEN + "Observer: " + ChatColor.WHITE + this.islandService.getCachedObserverCount(island) + "/" + current.getObserverLimit());
      lore.add(ChatColor.GREEN + "Dispenser: " + ChatColor.WHITE + this.islandService.getCachedDispenserCount(island) + "/" + current.getDispenserLimit());
      lore.add(ChatColor.GREEN + "Kaktus: " + ChatColor.WHITE + this.islandService.getCachedCactusCount(island) + "/" + current.getCactusLimit());
      lore.add(ChatColor.GREEN + "Kelp: " + ChatColor.WHITE + this.islandService.getCachedKelpCount(island) + "/" + current.getKelpLimit());
      lore.add(ChatColor.GREEN + "Bambus: " + ChatColor.WHITE + this.islandService.getCachedBambooCount(island) + "/" + current.getBambooLimit());
      return lore;
   }

   private List<String> buildUpgradeLore(IslandData island) {
      List<String> lines = new ArrayList<>();
      IslandLevelDefinition next = this.islandService.getNextLevelDef(island);
      int upgradeLevel = Math.max(0, island.getLevel() - 1);
      double islandLevelScore = this.islandService.calculateIslandLevelValue(island);
      double reservedLevelScore = this.islandService.calculateReservedUpgradeLevelValue(island);
      if (next == null) {
         lines.add(ChatColor.GOLD + "Upgrade-Level");
         lines.add("" + ChatColor.WHITE + upgradeLevel);
         lines.add(" ");
         lines.add(ChatColor.GREEN + "Max Level erreicht");
         return lines;
      } else {
         lines.add(ChatColor.GOLD + "Upgrade-Level");
         lines.add("" + ChatColor.WHITE + upgradeLevel + " -> " + Math.max(0, next.getLevel() - 1));
         lines.add(" ");
         lines.add(ChatColor.GOLD + "Erreiche");
         long reqIslandLevel = this.islandService.requiredIslandLevelForUpgrade(next.getLevel());
         ChatColor islandLevelColor = islandLevelScore >= (double)reqIslandLevel ? ChatColor.GREEN : ChatColor.RED;
         lines.add(
            ChatColor.GREEN
               + "Insel-Level: "
               + islandLevelColor
               + this.formatIslandLevelWithReserved(islandLevelScore, reservedLevelScore)
               + ChatColor.WHITE
               + "/"
               + reqIslandLevel
         );
         lines.add(" ");
         lines.add(ChatColor.GOLD + "Ben\u00f6tigt");
         long reqXp = this.islandService.requiredExperienceForLevel(next.getLevel());
         ChatColor xpColor = island.getStoredExperience() >= reqXp ? ChatColor.GREEN : ChatColor.RED;
         lines.add(ChatColor.GREEN + "Erfahrung: " + xpColor + island.getStoredExperience() + ChatColor.WHITE + "/" + reqXp);
         lines.add(" ");
         lines.add(ChatColor.GOLD + "Ben\u00f6tigte Gegenst\u00e4nde");

         for (Entry<Material, Integer> req : next.getRequirements().entrySet()) {
            int cur = island.getProgress(req.getKey());
            ChatColor curColor = cur >= req.getValue() ? ChatColor.GREEN : ChatColor.RED;
            lines.add(
               ChatColor.GREEN + this.materialDisplayNameDe(req.getKey()) + ChatColor.WHITE + ": " + curColor + cur + ChatColor.WHITE + "/" + req.getValue()
            );
         }

         return lines;
      }
   }

   public Inventory createIslandMenu(Player viewer, IslandData island) {
      Inventory inv = Bukkit.createInventory(new CoreService.IslandInventoryHolder(island.getOwner()), 54, "SkyCity Insel");
      this.fillWithPanes(inv);
      int relX = this.islandService.relativeChunkX(island, viewer.getLocation().getChunk().getX());
      int relZ = this.islandService.relativeChunkZ(island, viewer.getLocation().getChunk().getZ());
      int displayX = this.islandService.displayChunkX(relX);
      int displayZ = this.islandService.displayChunkZ(relZ);
      boolean currentUnlocked = this.islandService.isChunkUnlocked(island, relX, relZ);

      // Oben: Core + Inselbezug
      inv.setItem(10, this.named(Material.SHULKER_BOX, ChatColor.LIGHT_PURPLE + "Core \u00f6ffnen", List.of(ChatColor.GRAY + "Core-Men\u00fc mit Upgrades und CoreBank")));
      inv.setItem(11, this.named(Material.CHEST_MINECART, ChatColor.AQUA + "Inselbl\u00f6cke", List.of(ChatColor.GRAY + "Gesammelte Core-Items / Mengen")));
      inv.setItem(12, this.named(Material.LECTERN, ChatColor.GOLD + "Blockwertigkeit", List.of(ChatColor.GRAY + "Wert pro Block f\u00fcr Insel-Level")));
      inv.setItem(13, this.named(Material.RESPAWN_ANCHOR, ChatColor.GREEN + "Inselspawn setzen", List.of(ChatColor.GRAY + "Setzt Inselspawn auf deine Position")));
      inv.setItem(
         14,
         this.named(
            Material.NAME_TAG,
            ChatColor.GOLD + "Inseltitel setzen",
            List.of(
               ChatColor.GRAY + "Aktuell: " + this.islandService.getIslandTitleDisplay(island),
               ChatColor.YELLOW + "Klick = Titel per Chat eingeben",
               ChatColor.GRAY + "Schreibe 'clear' oder 'abbrechen'"
            )
         )
      );
      inv.setItem(
         15,
         this.named(
            Material.EMERALD,
            ChatColor.GREEN + "Insel-Shop",
            List.of(
               ChatColor.GRAY + "K\u00e4ufe mit Core-Erfahrung",
               ChatColor.GRAY + "Biome, Zeit, Wachstum, XP-Flaschen",
               ChatColor.YELLOW + "Klick = Shop \u00f6ffnen"
            )
         )
      );
      inv.setItem(
         16,
         this.named(
            currentUnlocked ? Material.LIME_DYE : Material.TRIPWIRE_HOOK,
            (currentUnlocked ? ChatColor.GREEN : ChatColor.YELLOW) + "Aktuellen Chunk freischalten",
            List.of(
               ChatColor.GRAY + "Chunk: " + displayX + ":" + displayZ,
               ChatColor.GRAY + "Status: " + (currentUnlocked ? "bereits frei" : "gesperrt"),
               ChatColor.GRAY + "Freie Unlocks: " + island.getAvailableChunkUnlocks(),
               currentUnlocked ? ChatColor.DARK_GRAY + "Nichts zu tun" : ChatColor.YELLOW + "Klick = freischalten"
            )
         )
      );

      inv.setItem(
         19,
         this.named(
            Material.MAP,
            ChatColor.YELLOW + "Chunks",
            List.of(ChatColor.GRAY + "Freischalten, claimen, Biome", ChatColor.GRAY + "\u00dcbersicht \u00fcber 64x64 Chunks")
         )
      );
      inv.setItem(20, this.named(Material.BLAZE_POWDER, ChatColor.AQUA + "Chunkgrenzen anzeigen", List.of(ChatColor.GRAY + "Partikel an Chunkr\u00e4ndern", ChatColor.GRAY + "Status je Chunkwechsel im Chat", ChatColor.YELLOW + "Klick = an/aus umschalten")));

      // Links unten: Berechtigungen
      inv.setItem(36, this.named(Material.PLAYER_HEAD, ChatColor.GOLD + "Mitglieder-Rechte", List.of(ChatColor.GRAY + "Build/Container/Redstone/All", ChatColor.GRAY + "Rechte per GUI verwalten")));
      inv.setItem(37, this.named(Material.OAK_DOOR, ChatColor.YELLOW + "Besucherrechte Insel", List.of(ChatColor.GRAY + "T\u00fcren, Container, Farmen, Reiten", ChatColor.GRAY + "f\u00fcr Besucher ohne Rechte")));
      inv.setItem(
         38,
         this.named(
            Material.WRITABLE_BOOK,
            ChatColor.YELLOW + "Owner-Rechte",
            List.of(ChatColor.GRAY + "Owner add/remove", ChatColor.YELLOW + "Klick = GUI \u00f6ffnen")
         )
      );
      inv.setItem(
         39,
         this.named(
            Material.NETHER_STAR,
            ChatColor.GOLD + "Master-Rechte",
            List.of(ChatColor.GRAY + "Einladen / Annehmen / Austreten", ChatColor.YELLOW + "Klick = GUI \u00f6ffnen")
         )
      );
      inv.setItem(
         40,
         this.named(
            Material.NAME_TAG,
            ChatColor.GOLD + "Grundst\u00fccke",
            List.of(
               ChatColor.GRAY + "Alle Grundst\u00fccks-Funktionen zentral",
               ChatColor.GRAY + "inkl. aktueller Position",
               ChatColor.YELLOW + "Klick = Grundst\u00fccks-Men\u00fc"
            )
         )
      );

      inv.setItem(10, this.named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "-", List.of()));
      inv.setItem(11, this.named(Material.GRASS_BLOCK, ChatColor.GREEN + "Insel", List.of(ChatColor.GRAY + "Spawn, Titel, Rechte, Core", ChatColor.YELLOW + "Klick = Inselmen\u00fc")));
      inv.setItem(12, this.named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "-", List.of()));
      inv.setItem(13, this.named(Material.MAP, ChatColor.YELLOW + "Chunks", List.of(ChatColor.GRAY + "Freischalten, Karte, Grenzen", ChatColor.YELLOW + "Klick = Chunkmen\u00fc")));
      inv.setItem(14, this.named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "-", List.of()));
      inv.setItem(15, this.named(Material.NAME_TAG, ChatColor.GOLD + "Grundst\u00fccke", List.of(ChatColor.GRAY + "GS, Rechte, Bans, PvP", ChatColor.YELLOW + "Klick = Grundst\u00fccks-Men\u00fc")));
      inv.setItem(16, this.named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "-", List.of()));
      inv.setItem(19, this.named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "-", List.of()));
      inv.setItem(20, this.named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "-", List.of()));
      inv.setItem(29, this.named(Material.EMERALD, ChatColor.GREEN + "Insel-Shop", List.of(ChatColor.GRAY + "Hybrid-Funktion au\u00dferhalb der Kategorien")));
      inv.setItem(31, this.named(Material.COMPASS, ChatColor.AQUA + "Teleport-Men\u00fc", List.of(ChatColor.GRAY + "Hybrid-Funktion au\u00dferhalb der Kategorien")));
      inv.setItem(36, this.named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "-", List.of()));
      inv.setItem(37, this.named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "-", List.of()));
      inv.setItem(38, this.named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "-", List.of()));
      inv.setItem(39, this.named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "-", List.of()));
      inv.setItem(40, this.named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "-", List.of()));
      inv.setItem(44, this.named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "-", List.of()));
      return inv;
   }

   public Inventory createBlockValueMenu(IslandData island) {
      return this.createBlockValueMenu(island, 0);
   }

   public Inventory createBlockValueMenu(IslandData island, int page) {
      int totalPages = Math.max(1, (int)Math.ceil((double)BLOCK_VALUE_MAP.size() / 45.0));
      int safePage = Math.max(0, Math.min(totalPages - 1, page));
      Inventory inv = Bukkit.createInventory(
         new CoreService.BlockValueInventoryHolder(island.getOwner(), safePage), 54, "Blockwertigkeit " + (safePage + 1) + "/" + totalPages
      );
      this.fillWithPanes(inv);
      List<Entry<Material, Double>> entries = new ArrayList<>(BLOCK_VALUE_MAP.entrySet());
      int start = safePage * 45;

      for (int i = 0; i < 45; i++) {
         int idx = start + i;
         if (idx >= entries.size()) {
            break;
         }

         Entry<Material, Double> entry = entries.get(idx);
         inv.setItem(
            GRID_SLOTS.get(i),
            this.namedMaterialLocalized(
               entry.getKey(),
               NamedTextColor.GOLD,
               List.of(ChatColor.GRAY + "Wertigkeit: " + ChatColor.WHITE + this.formatBlockValue(entry.getValue()))
            )
         );
      }

      inv.setItem(
         53,
         this.named(
            Material.PAPER,
            ChatColor.GOLD + "Prototype-Standard",
            List.of(
               ChatColor.GRAY + "Default: " + ChatColor.WHITE + this.formatBlockValue(BLOCK_VALUE_DEFAULT),
               ChatColor.GRAY + "Blacklist: " + ChatColor.WHITE + "Bedrock, Barrier, Erfahrungsfl\u00e4schchen"
            )
         )
      );
      if (safePage > 0) {
         inv.setItem(48, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Vorherige Seite", List.of()));
      }

      inv.setItem(49, this.named(Material.BARRIER, ChatColor.YELLOW + "Zur\u00fcck", List.of(ChatColor.GRAY + "Zur Inselansicht")));
      if (safePage < totalPages - 1) {
         inv.setItem(50, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "N\u00e4chste Seite", List.of()));
      }

      return inv;
   }

   public Inventory createIslandBlocksMenu(IslandData island) {
      return this.createIslandBlocksMenu(island, 0);
   }

   public Inventory createIslandBlocksMenu(IslandData island, int page) {
      List<Entry<String, Integer>> entries = new ArrayList<>(island.getProgress().entrySet());
      entries.removeIf(e -> e.getValue() == null || e.getValue() <= 0);
      entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
      int totalPages = Math.max(1, (int)Math.ceil((double)entries.size() / 45.0));
      int safePage = Math.max(0, Math.min(totalPages - 1, page));
      Inventory inv = Bukkit.createInventory(
         new CoreService.IslandBlocksInventoryHolder(island.getOwner(), safePage), 54, "Inselbl\u00f6cke " + (safePage + 1) + "/" + totalPages
      );
      this.fillWithPanes(inv);
      int start = safePage * 45;
      int shown = 0;

      for (int i = 0; i < 45; i++) {
         int idx = start + i;
         if (idx >= entries.size()) {
            break;
         }

         Entry<String, Integer> entry = entries.get(idx);
         Material material = Material.matchMaterial(entry.getKey());
         if (material != null) {
            List<String> lore = List.of(
               ChatColor.GRAY + "Menge: " + ChatColor.WHITE + entry.getValue(),
               ChatColor.GRAY + "Wertigkeit: " + ChatColor.WHITE + this.formatBlockValue(this.getBlockValue(material))
            );
            inv.setItem(GRID_SLOTS.get(i), this.namedMaterialLocalized(material, NamedTextColor.AQUA, lore));
            shown++;
         }
      }

      if (shown == 0) {
         inv.setItem(22, this.named(Material.BARRIER, ChatColor.RED + "Noch keine Inselbl\u00f6cke", List.of(ChatColor.GRAY + "Lege Upgrade-Items in den Core.")));
      }

      if (safePage > 0) {
         inv.setItem(48, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Vorherige Seite", List.of()));
      }

      inv.setItem(49, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of(ChatColor.GRAY + "Zur Inselansicht")));
      if (safePage < totalPages - 1) {
         inv.setItem(50, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "N\u00e4chste Seite", List.of()));
      }

      return inv;
   }

   private String formatBlockValue(double value) {
      String s = String.format(Locale.US, "%.4f", value);
      while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) {
         s = s.substring(0, s.length() - 1);
      }
      return s;
   }

   private String formatIslandLevel(double value) {
      return String.format(Locale.US, "%.2f", Math.max(0.0, value));
   }

   private String formatIslandLevelWithReserved(double total, double reserved) {
      String base = this.formatIslandLevel(total);
      return reserved <= 0.0 ? base : base + ChatColor.GRAY + " (-" + this.formatIslandLevel(reserved) + ")";
   }

   private double getBlockValue(Material material) {
      return material != null && !material.isAir() && !BLOCK_VALUE_BLACKLIST.contains(material) ? BLOCK_VALUE_MAP.getOrDefault(material, BLOCK_VALUE_DEFAULT) : 0.0;
   }

   public static double blockValueFor(Material material) {
      return material != null && !material.isAir() && !BLOCK_VALUE_BLACKLIST.contains(material) ? BLOCK_VALUE_MAP.getOrDefault(material, BLOCK_VALUE_DEFAULT) : 0.0;
   }

   public int chunkMapRelXByIndex(int index) {
      if (index < 0 || index >= CHUNK_SPIRAL_ORDER.size()) {
         return -1;
      }
      return CHUNK_SPIRAL_ORDER.get(index)[0];
   }

   public int chunkMapRelZByIndex(int index) {
      if (index < 0 || index >= CHUNK_SPIRAL_ORDER.size()) {
         return -1;
      }
      return CHUNK_SPIRAL_ORDER.get(index)[1];
   }

   public Inventory createChunkMapMenu(Player player, IslandData island, int page) {
      return this.createChunkMapMenu(player, island, page, CoreService.ChunkMapMode.LOCAL);
   }

   public Inventory createChunkMapMenu(Player player, IslandData island, int page, CoreService.ChunkMapMode mode) {
      CoreService.ChunkMapMode safeMode = mode == null ? CoreService.ChunkMapMode.LOCAL : mode;
      int totalPages = Math.max(1, (int)Math.ceil((double)TOTAL_CHUNKS / 45.0));
      int safePage = Math.max(0, Math.min(totalPages - 1, page));
      String title = safeMode == CoreService.ChunkMapMode.ALL ? ("Chunkkarte " + (safePage + 1) + "/" + totalPages) : "Chunkkarte Umgebung";
      Inventory inv = Bukkit.createInventory(new CoreService.ChunkMapInventoryHolder(island.getOwner(), safePage, safeMode.name()), 54, title);
      this.fillWithPanes(inv);
      int playerRelX = this.islandService.relativeChunkX(island, player.getLocation().getChunk().getX());
      int playerRelZ = this.islandService.relativeChunkZ(island, player.getLocation().getChunk().getZ());

      for (int i = 0; i < 45; i++) {
         int relX;
         int relZ;
         if (safeMode == CoreService.ChunkMapMode.ALL) {
            int index = safePage * 45 + i;
            if (index >= TOTAL_CHUNKS) {
               inv.setItem(GRID_SLOTS.get(i), null);
               continue;
            }
            relX = this.chunkMapRelXByIndex(index);
            relZ = this.chunkMapRelZByIndex(index);
         } else {
            int offsetX = (i % 9) - 4;
            int offsetZ = (i / 9) - 2;
            relX = playerRelX + offsetX;
            relZ = playerRelZ + offsetZ;
            if (relX < 0 || relX >= ISLAND_CHUNK_SIDE || relZ < 0 || relZ >= ISLAND_CHUNK_SIDE) {
               inv.setItem(GRID_SLOTS.get(i), null);
               continue;
            }
         }

         boolean unlocked = this.islandService.isChunkUnlocked(island, relX, relZ);
         ParcelData parcel = this.islandService.getParcel(island, relX, relZ);
         boolean current = relX == playerRelX && relZ == playerRelZ;
         Material mat = unlocked ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
         if (parcel != null) {
            mat = Material.CYAN_STAINED_GLASS_PANE;
         }
         if (current) {
            mat = unlocked ? Material.LIME_WOOL : Material.RED_WOOL;
         }

         int displayX = this.islandService.displayChunkX(relX);
         int displayZ = this.islandService.displayChunkZ(relZ);
         List<String> lore = new ArrayList<>();
         lore.add(ChatColor.GRAY + "Chunk: " + displayX + ":" + displayZ);
         lore.add(ChatColor.GRAY + "Status: " + (unlocked ? "frei" : "gesperrt"));
         lore.add(ChatColor.GRAY + "GS: " + (parcel == null ? "nicht geclaimt" : "geclaimt"));
         lore.add(ChatColor.GRAY + "Biom: " + this.biomeDisplayNameDe(this.islandService.getBiomeForChunk(island, relX, relZ)));
         if (!unlocked) {
            lore.add(ChatColor.YELLOW + "Linksklick = freischalten");
         }
         if (unlocked && parcel == null) {
            lore.add(ChatColor.YELLOW + "Linksklick = Grundst\u00fcck claimen");
         }
         if (unlocked) {
            lore.add(ChatColor.AQUA + "Rechtsklick = Biom-Men\u00fc");
         }
         if (parcel != null) {
            lore.add(ChatColor.GOLD + "Shift-Rechtsklick = GS-Men\u00fc");
         }
         if (current) {
            lore.add(ChatColor.AQUA + "Du stehst hier");
         }

         inv.setItem(GRID_SLOTS.get(i), this.named(mat, (unlocked ? ChatColor.GREEN : ChatColor.RED) + "Chunk " + displayX + ":" + displayZ, lore));
      }

      inv.setItem(45, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of(ChatColor.GRAY + "Zur Inselansicht")));
      if (safeMode == CoreService.ChunkMapMode.ALL && safePage > 0) {
         inv.setItem(48, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Vorherige Seite", List.of()));
      }
      inv.setItem(49, this.named(Material.NETHER_STAR, ChatColor.GOLD + "Freie Unlocks: " + island.getAvailableChunkUnlocks(), List.of()));
      if (safeMode == CoreService.ChunkMapMode.ALL && safePage < totalPages - 1) {
         inv.setItem(50, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "N\u00e4chste Seite", List.of()));
      }
      String switchText = safeMode == CoreService.ChunkMapMode.ALL ? "Modus: Umgebung" : "Modus: Alle Chunks";
      inv.setItem(53, this.named(Material.BEACON, ChatColor.AQUA + switchText, List.of(ChatColor.GRAY + "Klick = Modus wechseln")));
      return inv;
   }

   public Inventory createBiomeMenu(Player player, IslandData island, int page, int relChunkX, int relChunkZ, int returnPage) {
      int totalPages = Math.max(1, (int)Math.ceil((double)BIOME_OPTIONS.size() / 45.0));
      int safePage = Math.max(0, Math.min(totalPages - 1, page));
      Inventory inv = Bukkit.createInventory(new BiomeInventoryHolder(island.getOwner(), safePage, relChunkX, relChunkZ, returnPage), 54, "Biome " + (safePage + 1) + "/" + totalPages);
      this.fillWithPanes(inv);
      int start = safePage * 45;
      int displayX = this.islandService.displayChunkX(relChunkX);
      int displayZ = this.islandService.displayChunkZ(relChunkZ);
      long biomeChunkCost = this.islandService.getBiomeChangeCost(false);
      long biomeIslandCost = this.islandService.getBiomeChangeCost(true);

      for (int i = 0; i < 45; ++i) {
         int idx = start + i;
         if (idx >= BIOME_OPTIONS.size()) {
            inv.setItem(GRID_SLOTS.get(i), null);
         } else {
            Biome biome = BIOME_OPTIONS.get(idx);
            boolean selected = this.islandService.getBiomeForChunk(island, relChunkX, relChunkZ) == biome;
            Material icon = switch (biome) {
               case DESERT -> Material.SAND;
               case BADLANDS, WOODED_BADLANDS -> Material.RED_SAND;
               case SWAMP, MANGROVE_SWAMP -> Material.MANGROVE_ROOTS;
               case SNOWY_PLAINS, ICE_SPIKES -> Material.SNOW_BLOCK;
               case MUSHROOM_FIELDS -> Material.RED_MUSHROOM_BLOCK;
               case JUNGLE, BAMBOO_JUNGLE, SPARSE_JUNGLE -> Material.JUNGLE_LEAVES;
               case CHERRY_GROVE -> Material.CHERRY_LEAVES;
               case SAVANNA, SAVANNA_PLATEAU -> Material.ACACIA_LOG;
               case TAIGA -> Material.SPRUCE_LOG;
               default -> Material.GRASS_BLOCK;
            };
            inv.setItem(
               GRID_SLOTS.get(i),
               this.named(
                  selected ? Material.EMERALD_BLOCK : icon,
                  (selected ? ChatColor.GREEN : ChatColor.AQUA) + this.biomeDisplayNameDe(biome),
                  List.of(
                     ChatColor.GRAY + "Ziel-Chunk: " + displayX + ":" + displayZ,
                     ChatColor.GRAY + "Kosten Chunk: " + ChatColor.WHITE + biomeChunkCost,
                     ChatColor.GRAY + "Kosten Inselweit: " + ChatColor.WHITE + biomeIslandCost,
                     ChatColor.YELLOW + "Linksklick = f\u00fcr Chunk setzen",
                     ChatColor.GOLD + "Rechtsklick = auf Insel anwenden"
                  )
               )
            );
         }
      }

      inv.setItem(45, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck zum Insel-Shop", List.of()));
      if (safePage > 0) {
         inv.setItem(48, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Vorherige Seite", List.of()));
      }
      if (safePage < totalPages - 1) {
         inv.setItem(50, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "N\u00e4chste Seite", List.of()));
      }

      inv.setItem(49, this.named(Material.GRASS_BLOCK, ChatColor.GREEN + "Aktueller Chunk " + displayX + ":" + displayZ, List.of(ChatColor.GRAY + "Rechtsklick auf ein Biom = inselweit")));
      return inv;
   }

   public Inventory createIslandSettingsMenu(IslandData island) {
      Inventory inv = Bukkit.createInventory(new IslandSettingsInventoryHolder(island.getOwner()), 45, "Insel");
      this.fillWithPanes(inv);
      if (System.currentTimeMillis() >= 0L) {
         inv.setItem(10, this.named(Material.SHULKER_BOX, ChatColor.LIGHT_PURPLE + "Core \u00f6ffnen", List.of(ChatColor.GRAY + "Core-Men\u00fc mit Upgrades und CoreBank")));
         inv.setItem(11, this.named(Material.CHEST_MINECART, ChatColor.AQUA + "Inselbl\u00f6cke", List.of(ChatColor.GRAY + "Gesammelte Core-Items / Mengen")));
         inv.setItem(12, this.named(Material.LECTERN, ChatColor.GOLD + "Blockwertigkeit", List.of(ChatColor.GRAY + "Wert pro Block f\u00fcr Insel-Level")));
         inv.setItem(13, this.named(Material.RESPAWN_ANCHOR, ChatColor.GREEN + "Inselspawn setzen", List.of(ChatColor.GRAY + "Setzt Inselspawn auf deine Position")));
         inv.setItem(14, this.named(Material.NAME_TAG, ChatColor.GOLD + "Inseltitel setzen", List.of(ChatColor.GRAY + "Aktuell: " + this.islandService.getIslandTitleDisplay(island), ChatColor.YELLOW + "Klick = Titel per Chat eingeben")));
         inv.setItem(15, this.named(Material.ENDER_PEARL, ChatColor.AQUA + "Warp setzen", List.of(ChatColor.GRAY + "Aktuell: " + this.islandService.getIslandWarpDisplay(island), ChatColor.YELLOW + "Klick = Warpname und Position per Chat setzen")));
         inv.setItem(19, this.named(Material.GRASS_BLOCK, ChatColor.GREEN + "Biom-Men\u00fc", List.of(ChatColor.GRAY + "Chunkweise und inselweit setzen")));
         inv.setItem(20, this.named(Material.PLAYER_HEAD, ChatColor.GOLD + "Mitglieder-Rechte", List.of(ChatColor.GRAY + "Build/Container/Redstone/All")));
         inv.setItem(21, this.named(Material.OAK_DOOR, ChatColor.YELLOW + "Besucherrechte Insel", List.of(ChatColor.GRAY + "T\u00fcren, Container, Farmen, Reiten")));
         inv.setItem(22, this.named(Material.WRITABLE_BOOK, ChatColor.YELLOW + "Owner-Rechte", List.of(ChatColor.GRAY + "Owner add/remove")));
         inv.setItem(23, this.named(Material.NETHER_STAR, ChatColor.GOLD + "Master-Rechte", List.of(ChatColor.GRAY + "Einladen / Annehmen / Austreten")));
         inv.setItem(40, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of(ChatColor.GRAY + "Zur Inselansicht")));
         return inv;
      }
      IslandService.IslandTimeMode timeMode = this.islandService.getIslandTimeMode(island);
      Material timeIcon = switch (timeMode) {
         case DAY -> Material.SUNFLOWER;
         case SUNSET -> Material.ORANGE_DYE;
         case MIDNIGHT -> Material.ENDER_PEARL;
         case NORMAL -> Material.CLOCK;
      };
      inv.setItem(
         11,
         this.named(
            timeIcon,
            ChatColor.GOLD + "Tag/Nacht-Zyklus",
            List.of(
               ChatColor.GRAY + "Aktuell: " + ChatColor.WHITE + this.islandService.islandTimeModeLabel(timeMode),
               ChatColor.YELLOW + "Klick = Zeitmodus-Shop",
               ChatColor.GRAY + "Wirkt nur auf diese Insel"
            )
         )
      );
      inv.setItem(15, this.named(Material.GRASS_BLOCK, ChatColor.GREEN + "Biom-Men\u00fc", List.of(ChatColor.GRAY + "Chunkweise und inselweit setzen")));
      inv.setItem(22, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of(ChatColor.GRAY + "Zur Inselansicht")));
      inv.setItem(10, this.named(Material.SHULKER_BOX, ChatColor.LIGHT_PURPLE + "Core \u00f6ffnen", List.of(ChatColor.GRAY + "Core-Men\u00fc mit Upgrades und CoreBank")));
      inv.setItem(11, this.named(Material.CHEST_MINECART, ChatColor.AQUA + "Inselbl\u00f6cke", List.of(ChatColor.GRAY + "Gesammelte Core-Items / Mengen")));
      inv.setItem(12, this.named(Material.LECTERN, ChatColor.GOLD + "Blockwertigkeit", List.of(ChatColor.GRAY + "Wert pro Block f\u00fcr Insel-Level")));
      inv.setItem(13, this.named(Material.RESPAWN_ANCHOR, ChatColor.GREEN + "Inselspawn setzen", List.of(ChatColor.GRAY + "Setzt Inselspawn auf deine Position")));
      inv.setItem(14, this.named(Material.NAME_TAG, ChatColor.GOLD + "Inseltitel setzen", List.of(ChatColor.GRAY + "Aktuell: " + this.islandService.getIslandTitleDisplay(island), ChatColor.YELLOW + "Klick = Titel per Chat eingeben")));
      inv.setItem(19, this.named(Material.PLAYER_HEAD, ChatColor.GOLD + "Mitglieder-Rechte", List.of(ChatColor.GRAY + "Build/Container/Redstone/All")));
      inv.setItem(20, this.named(Material.OAK_DOOR, ChatColor.YELLOW + "Besucherrechte Insel", List.of(ChatColor.GRAY + "T\u00fcren, Container, Farmen, Reiten")));
      inv.setItem(21, this.named(Material.WRITABLE_BOOK, ChatColor.YELLOW + "Owner-Rechte", List.of(ChatColor.GRAY + "Owner add/remove")));
      inv.setItem(22, this.named(Material.NETHER_STAR, ChatColor.GOLD + "Master-Rechte", List.of(ChatColor.GRAY + "Einladen / Annehmen / Austreten")));
      inv.setItem(15, this.named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "-", List.of()));
      inv.setItem(40, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of(ChatColor.GRAY + "Zur Inselansicht")));
      return inv;
   }

   public Inventory createChunkSettingsMenu(Player player, IslandData island) {
      Inventory inv = Bukkit.createInventory(new ChunkSettingsInventoryHolder(island.getOwner()), 27, "Chunks");
      this.fillWithPanes(inv);
      int relX = this.islandService.relativeChunkX(island, player.getLocation().getChunk().getX());
      int relZ = this.islandService.relativeChunkZ(island, player.getLocation().getChunk().getZ());
      int displayX = this.islandService.displayChunkX(relX);
      int displayZ = this.islandService.displayChunkZ(relZ);
      boolean currentUnlocked = this.islandService.isChunkUnlocked(island, relX, relZ);
      inv.setItem(11, this.named(currentUnlocked ? Material.LIME_DYE : Material.TRIPWIRE_HOOK, (currentUnlocked ? ChatColor.GREEN : ChatColor.YELLOW) + "Aktuellen Chunk freischalten", List.of(ChatColor.GRAY + "Chunk: " + displayX + ":" + displayZ, ChatColor.GRAY + "Status: " + (currentUnlocked ? "bereits frei" : "gesperrt"), ChatColor.GRAY + "Freie Unlocks: " + island.getAvailableChunkUnlocks())));
      inv.setItem(13, this.named(Material.MAP, ChatColor.YELLOW + "Chunk-Karte", List.of(ChatColor.GRAY + "\u00dcbersicht \u00fcber 64x64 Chunks")));
      inv.setItem(15, this.named(Material.BLAZE_POWDER, ChatColor.AQUA + "Chunkgrenzen anzeigen", List.of(ChatColor.GRAY + "Partikel an Chunkr\u00e4ndern", ChatColor.YELLOW + "Klick = an/aus umschalten")));
      inv.setItem(22, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of(ChatColor.GRAY + "Zur Inselansicht")));
      return inv;
   }

   public Inventory createIslandShopMenu(Player player, IslandData island) {
      int relX = this.islandService.relativeChunkX(island, player.getLocation().getChunk().getX());
      int relZ = this.islandService.relativeChunkZ(island, player.getLocation().getChunk().getZ());
      int displayX = this.islandService.displayChunkX(relX);
      int displayZ = this.islandService.displayChunkZ(relZ);
      long stored = Math.max(0L, island.getStoredExperience());
      int activeTier = this.islandService.getGrowthBoostTier(island, relX, relZ);
      long remainingMs = this.islandService.getGrowthBoostRemainingMillis(island, relX, relZ);
      String activeBoost = activeTier <= 0 ? "kein Boost" : ("Stufe " + activeTier + " (" + this.formatMillisShort(remainingMs) + ")");
      Inventory inv = Bukkit.createInventory(new IslandShopInventoryHolder(island.getOwner(), relX, relZ), 45, "Insel-Shop");
      this.fillWithPanes(inv);
      inv.setItem(
         4,
         this.named(
            Material.EXPERIENCE_BOTTLE,
            ChatColor.AQUA + "Core-Erfahrung",
            List.of(ChatColor.GRAY + "Gespeichert: " + ChatColor.WHITE + stored)
         )
      );
      long biomeChunkCost = this.islandService.getBiomeChangeCost(false);
      long biomeIslandCost = this.islandService.getBiomeChangeCost(true);
      inv.setItem(
         10,
         this.named(
            Material.GRASS_BLOCK,
            ChatColor.GREEN + "Biom umstellen",
            List.of(
               ChatColor.GRAY + "Kosten Chunk: " + ChatColor.WHITE + biomeChunkCost,
               ChatColor.GRAY + "Kosten Inselweit: " + ChatColor.WHITE + biomeIslandCost,
               ChatColor.YELLOW + "Klick = Biom-Men\u00fc \u00f6ffnen"
            )
         )
      );
      long timeCost = this.islandService.getTimeModeChangeCost();
      inv.setItem(
         12,
         this.named(
            Material.CLOCK,
            ChatColor.GOLD + "Zeitmodus w\u00e4hlen",
            List.of(
               ChatColor.GRAY + "Aktuell: " + ChatColor.WHITE + this.islandService.islandTimeModeLabel(this.islandService.getIslandTimeMode(island)),
               ChatColor.GRAY + "Kosten: " + ChatColor.WHITE + timeCost + " Erfahrung",
               ChatColor.YELLOW + "Klick = Modus-Shop \u00f6ffnen"
            )
         )
      );
      inv.setItem(
         14,
         this.named(
            Material.WHEAT,
            ChatColor.GREEN + "Wachstumsboost Chunk " + displayX + ":" + displayZ,
            List.of(
               ChatColor.GRAY + "Aktiv: " + ChatColor.WHITE + activeBoost,
               ChatColor.GRAY + "Stufe 1: + Wachstum, 15m, " + this.islandService.getGrowthBoostCost(1) + " XP",
               ChatColor.GRAY + "Stufe 2: + Wachstum, 30m, " + this.islandService.getGrowthBoostCost(2) + " XP",
               ChatColor.GRAY + "Stufe 3: + Wachstum, 60m, " + this.islandService.getGrowthBoostCost(3) + " XP",
               ChatColor.YELLOW + "Linksklick Stufe 1 | Rechtsklick Stufe 2 | Shift-Rechtsklick Stufe 3"
            )
         )
      );
      inv.setItem(
         16,
         this.named(
            Material.HONEY_BOTTLE,
            ChatColor.LIGHT_PURPLE + "XP-Flaschen abf\u00fcllen",
            List.of(
               ChatColor.GRAY + "Pro Flasche: " + ChatColor.WHITE + this.islandService.getXpBottlePointsPerBottle() + " XP",
               ChatColor.GRAY + "Kosten: " + ChatColor.WHITE + this.islandService.getXpBottleCostPerBottle() + " XP (10% Verlust)",
               ChatColor.YELLOW + "Linksklick = 1 Flasche, Shift-Klick = 16 Flaschen"
            )
         )
      );
      inv.setItem(40, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of(ChatColor.GRAY + "Zur Inselansicht")));
      return inv;
   }

   public Inventory createTimeModeShopMenu(IslandData island, String backTarget) {
      String safeBack = backTarget == null ? "settings" : backTarget.toLowerCase(Locale.ROOT);
      IslandService.IslandTimeMode current = this.islandService.getIslandTimeMode(island);
      long cost = this.islandService.getTimeModeChangeCost();
      Inventory inv = Bukkit.createInventory(new TimeModeShopInventoryHolder(island.getOwner(), safeBack), 45, "Zeitmodus-Shop");
      this.fillWithPanes(inv);
      inv.setItem(4, this.named(Material.CLOCK, ChatColor.GOLD + "Zeitmodus", List.of(ChatColor.GRAY + "Aktuell: " + ChatColor.WHITE + this.islandService.islandTimeModeLabel(current))));
      inv.setItem(11, this.named(Material.SUNFLOWER, (current == IslandService.IslandTimeMode.DAY ? ChatColor.GREEN : ChatColor.YELLOW) + "Nur Tag",
              List.of(ChatColor.GRAY + "Kosten: " + ChatColor.WHITE + cost, ChatColor.YELLOW + "Klick = aktivieren")));
      inv.setItem(13, this.named(Material.ORANGE_DYE, (current == IslandService.IslandTimeMode.SUNSET ? ChatColor.GREEN : ChatColor.YELLOW) + "Sonnenuntergang",
              List.of(ChatColor.GRAY + "Kosten: " + ChatColor.WHITE + cost, ChatColor.YELLOW + "Klick = aktivieren")));
      inv.setItem(15, this.named(Material.ENDER_PEARL, (current == IslandService.IslandTimeMode.MIDNIGHT ? ChatColor.GREEN : ChatColor.YELLOW) + "Nacht",
              List.of(ChatColor.GRAY + "Kosten: " + ChatColor.WHITE + cost, ChatColor.YELLOW + "Klick = aktivieren")));
      inv.setItem(22, this.named(Material.CLOCK, (current == IslandService.IslandTimeMode.NORMAL ? ChatColor.GREEN : ChatColor.YELLOW) + "Normal",
              List.of(ChatColor.GRAY + "Kosten: " + ChatColor.WHITE + cost, ChatColor.YELLOW + "Klick = aktivieren")));
      String backName = "shop".equals(safeBack) ? "Zum Insel-Shop" : "Zu Insel-Einstellungen";
      inv.setItem(40, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of(ChatColor.GRAY + backName)));
      return inv;
   }
   public Inventory createVisitorSettingsMenu(IslandData island) {
      Inventory inv = Bukkit.createInventory(new CoreService.VisitorSettingsInventoryHolder(island.getOwner()), 36, "Besucherrechte Insel");
      this.fillWithPanes(inv);
      this.fillSettings(inv, island.getIslandVisitorSettings());
      inv.setItem(35, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of()));
      return inv;
   }

   public Inventory createParcelsMenu(Player player, IslandData island) {
      IslandData contextIsland = island;
      Location at = player.getLocation();
      IslandData atIsland = this.islandService.getIslandAt(at);
      if (atIsland != null) {
         ParcelData atParcel = this.islandService.getParcelAt(atIsland, at);
         if (atParcel != null && this.islandService.isParcelUser(atIsland, atParcel, player.getUniqueId())) {
            contextIsland = atIsland;
         }
      }

      Inventory inv = Bukkit.createInventory(new CoreService.ParcelsInventoryHolder(contextIsland.getOwner()), 45, "Grundst\u00fccke");
      this.fillWithPanes(inv);
      int x = player.getLocation().getBlockX();
      int y = player.getLocation().getBlockY();
      int z = player.getLocation().getBlockZ();
      ParcelData parcel = this.islandService.getParcelAt(contextIsland, player.getLocation());
      String status = parcel == null ? "Du stehst in keinem Grundst\u00fcck" : ("Du stehst in " + this.islandService.getParcelDisplayName(parcel));
      ChatColor statusColor = parcel == null ? ChatColor.YELLOW : ChatColor.GREEN;
      inv.setItem(
         10,
         this.named(
            Material.NAME_TAG,
            ChatColor.GOLD + "Grundst\u00fcck aktuelle Position",
            List.of(
               ChatColor.GRAY + "Position: " + x + ", " + y + ", " + z,
               ChatColor.GRAY + "Status: " + statusColor + status,
               ChatColor.YELLOW + "Klick = \u00f6ffnen"
            )
         )
      );
      inv.setItem(
         12,
         this.named(
            Material.BOOK,
            ChatColor.YELLOW + "Grundst\u00fcck erstellen",
            List.of(
               ChatColor.GRAY + "Mit Stab Pos1/Pos2 setzen",
               ChatColor.GRAY + "Dann: /is plot create",
               ChatColor.YELLOW + "Klick = Anleitung im Chat"
            )
         )
      );
      inv.setItem(
         14,
         this.named(
            Material.STICK,
            ChatColor.GOLD + "Grundst\u00fccks-Stab",
            List.of(ChatColor.GRAY + "Freie Quader markieren", ChatColor.YELLOW + "Klick = Stab erhalten")
         )
      );
      inv.setItem(
         16,
         this.named(
            Material.COMPASS,
            ChatColor.AQUA + "Grundst\u00fccke besuchen",
            List.of(ChatColor.GRAY + "Teleportfilter: nur Grundst\u00fccke", ChatColor.YELLOW + "Klick = \u00f6ffnen")
         )
      );
      inv.setItem(
         22,
         this.named(
            Material.WRITABLE_BOOK,
            ChatColor.YELLOW + "Men\u00fchilfe",
            List.of(
               ChatColor.GRAY + "1) Grundst\u00fccks-Stab holen",
               ChatColor.GRAY + "2) Pos1/Pos2 setzen + /is plot create",
               ChatColor.GRAY + "3) Owner/User/Bans/Spawn im GS-Men\u00fc"
            )
         )
      );
      inv.setItem(40, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of(ChatColor.GRAY + "Zur Inselansicht")));
      return inv;
   }

   public Inventory createParcelMenu(Player player, IslandData island, int relX, int relZ) {
      int displayX = this.islandService.displayChunkX(relX);
      int displayZ = this.islandService.displayChunkZ(relZ);
      ParcelData parcel = this.islandService.getParcel(island, relX, relZ);
      String title = parcel == null ? ("Grundst\u00fcck " + displayX + ":" + displayZ) : ("Grundst\u00fcck " + this.islandService.getParcelDisplayName(parcel));
      Inventory inv = Bukkit.createInventory(new CoreService.ParcelInventoryHolder(island.getOwner(), relX, relZ), 54, title);
      this.fillWithPanes(inv);
      boolean unlocked = this.islandService.isChunkUnlocked(island, relX, relZ);
      if (!unlocked) {
         inv.setItem(22, this.named(Material.BARRIER, ChatColor.RED + "Chunk ist gesperrt", List.of()));
         return inv;
      } else if (parcel == null) {
         inv.setItem(
            22,
            this.named(
               Material.STICK,
               ChatColor.GOLD + "Grundst\u00fccks-Stab holen",
               List.of(ChatColor.GRAY + "Freie Quader setzen statt Chunk-Claim", ChatColor.YELLOW + "Klick = Stab erhalten")
            )
         );
         inv.setItem(49, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of()));
         return inv;
      } else {
         inv.setItem(10, this.named(Material.RESPAWN_ANCHOR, ChatColor.GREEN + "GS-Spawn setzen", List.of(ChatColor.GRAY + "Setzt Spawn auf deine Position")));
         inv.setItem(12, this.named(Material.OAK_DOOR, ChatColor.YELLOW + "Besucherrechte GS", List.of(ChatColor.GRAY + "Toggles f\u00fcr Fremde")));
         inv.setItem(14, this.named(Material.PLAYER_HEAD, ChatColor.AQUA + "Owner vergeben", List.of(ChatColor.YELLOW + "Klick = GUI \u00f6ffnen")));
         inv.setItem(16, this.named(Material.NAME_TAG, ChatColor.AQUA + "User vergeben", List.of(ChatColor.YELLOW + "Klick = GUI \u00f6ffnen")));
         inv.setItem(28, this.named(Material.IRON_BOOTS, ChatColor.RED + "Spieler kicken", List.of(ChatColor.YELLOW + "Klick = GUI \u00f6ffnen")));
         inv.setItem(30, this.named(Material.BARRIER, ChatColor.RED + "Spieler bannen", List.of(ChatColor.YELLOW + "Klick = GUI \u00f6ffnen")));
         inv.setItem(32, this.named(Material.MILK_BUCKET, ChatColor.GREEN + "Spieler entbannen", List.of(ChatColor.YELLOW + "Klick = GUI \u00f6ffnen")));
         inv.setItem(35, this.named(parcel.isPveEnabled() ? Material.NETHER_STAR : Material.GRAY_WOOL, (parcel.isPveEnabled() ? ChatColor.DARK_GREEN : ChatColor.GRAY) + "GS-PvE", List.of(ChatColor.GRAY + "Aktiviert Wellenkampf auf diesem GS", ChatColor.GRAY + "Skaliert mit Grundfl\u00e4che und Spielerzahl", ChatColor.YELLOW + "Klick = umschalten")));
         inv.setItem(37, this.named(Material.BOOK, ChatColor.GOLD + "PvE-Anleitung", List.of(
            ChatColor.GRAY + "Wei\u00dfe Wolle = Startzone (max 5x5)",
            ChatColor.GRAY + "Bis zu 1 Loch 2x2 in der Startzone erlaubt",
            ChatColor.GRAY + "Ausgang nur an der Startzonen-Seite erlaubt",
            ChatColor.GRAY + "Der Ausgang darf breiter sein, aber nur zusammenhaengend",
            ChatColor.GRAY + "Ausgang und Aussenkante muessen 3 hoch frei sein",
            ChatColor.GRAY + "LIGHT_GRAY = Zombie Stufe 1",
            ChatColor.GRAY + "GREEN = Spider Stufe 2",
            ChatColor.GRAY + "YELLOW = Skeleton Stufe 2",
            ChatColor.GRAY + "ORANGE = Husk Stufe 3",
            ChatColor.GRAY + "BLUE = Drowned Stufe 3",
            ChatColor.GRAY + "RED = Creeper Stufe 4",
            ChatColor.GRAY + "BLACK = Wither Skeleton Stufe 5"
         )));
         inv.setItem(49, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of()));
         return inv;
      }
   }

   public Inventory createParcelVisitorSettingsMenu(IslandData island, int relX, int relZ) {
      Inventory inv = Bukkit.createInventory(new CoreService.ParcelVisitorSettingsInventoryHolder(island.getOwner(), relX, relZ), 36, "Besucherrechte GS");
      this.fillWithPanes(inv);
      ParcelData parcel = this.islandService.getParcel(island, relX, relZ);
      if (parcel != null) {
         this.fillSettings(inv, parcel.getVisitorSettings());
      }

      inv.setItem(35, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of()));
      return inv;
   }

   public Inventory createTeleportMenu(UUID viewerId, int page) {
      return this.createTeleportMenu(viewerId, page, "all");
   }

   public Inventory createTeleportMenu(UUID viewerId, int page, String filter) {
      String safeFilter = this.normalizeFilter(filter);
      if (System.currentTimeMillis() >= 0L) {
         List<IslandService.TeleportTarget> targets = this.islandService.getTeleportTargetsFor(viewerId).stream().filter(t -> {
            return switch (safeFilter) {
               case "islands" -> t.id().startsWith("island:");
               case "parcels" -> t.id().startsWith("parcel:");
               case "warps" -> t.id().startsWith("warp:");
               case "mine" -> t.id().contains(viewerId.toString());
               default -> true;
            };
         }).toList();
         int totalPages = Math.max(1, (int)Math.ceil((double)targets.size() / 45.0));
         int safePage = Math.max(0, Math.min(totalPages - 1, page));
         Inventory inv = Bukkit.createInventory(new CoreService.TeleportInventoryHolder(viewerId, safePage, safeFilter), 54, "Teleport-Men\u00fc " + (safePage + 1) + "/" + totalPages);
         this.fillWithPanes(inv);
         int start = safePage * 45;

         for (int slot = 0; slot < 45; slot++) {
            int idx = start + slot;
            if (idx >= targets.size()) {
               break;
            }

            IslandService.TeleportTarget target = targets.get(idx);
            Material icon = target.id().startsWith("warp:")
               ? Material.ENDER_PEARL
               : (target.parcel() ? Material.NAME_TAG : Material.COMPASS);
            inv.setItem(
               GRID_SLOTS.get(slot),
               this.named(
                  icon,
                  ChatColor.AQUA + target.displayName(),
                  List.of(ChatColor.GRAY + "Klick = teleportieren", ChatColor.DARK_GRAY + target.id())
               )
            );
         }

         if (safePage > 0) {
            inv.setItem(48, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Vorherige Seite", List.of()));
         }

         inv.setItem(49, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of()));
         if (safePage < totalPages - 1) {
            inv.setItem(50, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "N\u00e4chste Seite", List.of()));
         }

         inv.setItem(45, this.named(Material.HOPPER, ChatColor.AQUA + "Filter all", List.of(ChatColor.GRAY + "Alle Ziele")));
         inv.setItem(46, this.named(Material.COMPASS, ChatColor.AQUA + "Filter islands", List.of(ChatColor.GRAY + "Nur Inseln")));
         inv.setItem(47, this.named(Material.NAME_TAG, ChatColor.AQUA + "Filter parcels", List.of(ChatColor.GRAY + "Nur Grundst\u00fccke")));
         inv.setItem(51, this.named(Material.PLAYER_HEAD, ChatColor.AQUA + "Filter mine", List.of(ChatColor.GRAY + "Nur eigene")));
         inv.setItem(52, this.named(Material.ENDER_PEARL, ChatColor.AQUA + "Filter warps", List.of(ChatColor.GRAY + "Nur Warps")));
         return inv;
      }
      List<IslandService.TeleportTarget> targets = this.islandService.getTeleportTargetsFor(viewerId).stream().filter(t -> {
         return switch (safeFilter) {
            case "islands" -> !t.parcel();
            case "parcels" -> t.parcel();
            case "mine" -> t.id().contains(viewerId.toString());
            default -> true;
         };
      }).toList();
      int totalPages = Math.max(1, (int)Math.ceil((double)targets.size() / 45.0));
      int safePage = Math.max(0, Math.min(totalPages - 1, page));
      Inventory inv = Bukkit.createInventory(
         new CoreService.TeleportInventoryHolder(viewerId, safePage, safeFilter), 54, "Teleport-Men\u00fc " + (safePage + 1) + "/" + totalPages
      );
      this.fillWithPanes(inv);
      int start = safePage * 45;

      for (int slot = 0; slot < 45; slot++) {
         int idx = start + slot;
         if (idx >= targets.size()) {
            break;
         }

         IslandService.TeleportTarget target = targets.get(idx);
         inv.setItem(
            GRID_SLOTS.get(slot),
            this.named(
               target.parcel() ? Material.NAME_TAG : Material.COMPASS,
               ChatColor.AQUA + target.displayName(),
               List.of(ChatColor.GRAY + "Klick = teleportieren", ChatColor.DARK_GRAY + target.id())
            )
         );
      }

      if (safePage > 0) {
         inv.setItem(48, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Vorherige Seite", List.of()));
      }

      inv.setItem(49, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of()));
      if (safePage < totalPages - 1) {
         inv.setItem(50, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "N\u00e4chste Seite", List.of()));
      }

      inv.setItem(45, this.named(Material.HOPPER, ChatColor.AQUA + "Filter all", List.of(ChatColor.GRAY + "Alle Ziele")));
      inv.setItem(46, this.named(Material.COMPASS, ChatColor.AQUA + "Filter islands", List.of(ChatColor.GRAY + "Nur Inseln")));
      inv.setItem(47, this.named(Material.NAME_TAG, ChatColor.AQUA + "Filter parcels", List.of(ChatColor.GRAY + "Nur Grundst\u00fccke")));
      inv.setItem(51, this.named(Material.PLAYER_HEAD, ChatColor.AQUA + "Filter mine", List.of(ChatColor.GRAY + "Nur eigene")));
      return inv;
   }

   private void fillSettings(Inventory inv, AccessSettings settings) {
      inv.setItem(10, this.toggleItem(Material.OAK_DOOR, "T\u00fcren", settings.isDoors()));
      inv.setItem(11, this.toggleItem(Material.OAK_TRAPDOOR, "Trapdoors", settings.isTrapdoors()));
      inv.setItem(12, this.toggleItem(Material.OAK_FENCE_GATE, "Zauntore", settings.isFenceGates()));
      inv.setItem(13, this.toggleItem(Material.STONE_BUTTON, "Buttons", settings.isButtons()));
      inv.setItem(14, this.toggleItem(Material.LEVER, "Hebel", settings.isLevers()));
      inv.setItem(15, this.toggleItem(Material.STONE_PRESSURE_PLATE, "Druckplatten", settings.isPressurePlates()));
      inv.setItem(16, this.toggleItem(Material.CHEST, "Container", settings.isContainers()));
      inv.setItem(19, this.toggleItem(Material.COMPOSTER, "Farm/Nutzung", settings.isFarmUse()));
      inv.setItem(20, this.toggleItem(Material.SADDLE, "Reiten", settings.isRide()));
      inv.setItem(21, this.toggleItem(Material.LADDER, "Leiter setzen", settings.isLadderPlace()));
      inv.setItem(22, this.toggleItem(Material.ENDER_PEARL, "Teleport", settings.isTeleport()));
      inv.setItem(23, this.toggleItem(Material.IRON_PICKAXE, "Leiter abbauen", settings.isLadderBreak()));
      inv.setItem(24, this.toggleItem(Material.OAK_LEAVES, "Laub setzen", settings.isLeavesPlace()));
      inv.setItem(25, this.toggleItem(Material.SHEARS, "Laub abbauen", settings.isLeavesBreak()));
   }

   public Inventory createParcelModerationMenu(Player viewer, IslandData island, int relX, int relZ, CoreService.ParcelModerationAction action, int page) {
      ParcelData parcel = this.islandService.getParcel(island, relX, relZ);
      CoreService.ParcelModerationAction safeAction = action == null ? CoreService.ParcelModerationAction.KICK : action;
      Inventory inv = Bukkit.createInventory(
         new CoreService.ParcelModerationInventoryHolder(island.getOwner(), relX, relZ, safeAction.name(), page),
         54,
         "GS " + (parcel == null ? safeAction.name().toLowerCase(Locale.ROOT) : this.islandService.getParcelDisplayName(parcel) + " " + safeAction.name().toLowerCase(Locale.ROOT)) + " " + (page + 1)
      );
      this.fillWithPanes(inv);
      if (parcel == null) {
         inv.setItem(49, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of()));
         return inv;
      }

      List<OfflinePlayer> candidates = new ArrayList<>();
      if (safeAction == CoreService.ParcelModerationAction.UNBAN) {
         for (UUID id : parcel.getBanned()) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(id);
            if (target.getName() != null && !target.getName().isBlank()) {
               candidates.add(target);
            }
         }
      } else {
         for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(viewer.getUniqueId())) {
               continue;
            }

            if (this.islandService.getIslandAt(online.getLocation()) != island) {
               continue;
            }

            if (this.islandService.getParcelAt(island, online.getLocation()) != parcel) {
               continue;
            }

            if (safeAction == CoreService.ParcelModerationAction.BAN && parcel.getBanned().contains(online.getUniqueId())) {
               continue;
            }

            candidates.add(online);
         }
      }

      candidates.sort((a, b) -> {
         String na = a.getName() == null ? "" : a.getName();
         String nb = b.getName() == null ? "" : b.getName();
         return na.compareToIgnoreCase(nb);
      });

      int totalPages = Math.max(1, (int)Math.ceil((double)candidates.size() / 45.0));
      int safePage = Math.max(0, Math.min(totalPages - 1, page));
      int start = safePage * 45;

      for (int i = 0; i < 45; i++) {
         int idx = start + i;
         if (idx >= candidates.size()) {
            break;
         }

         OfflinePlayer target = candidates.get(idx);
         String name = target.getName() == null || target.getName().isBlank() ? target.getUniqueId().toString() : target.getName();
         List<String> lore = new ArrayList<>();
         lore.add(ChatColor.GRAY + "Online: " + (target.isOnline() ? "ja" : "nein"));
         lore.add(ChatColor.YELLOW + "Klick = " + switch (safeAction) {
            case KICK -> "kicken";
            case BAN -> "bannen";
            case UNBAN -> "entbannen";
         });
         lore.add(ChatColor.DARK_GRAY + "uuid:" + target.getUniqueId());
         Material icon = switch (safeAction) {
            case KICK -> Material.IRON_BOOTS;
            case BAN -> Material.BARRIER;
            case UNBAN -> Material.MILK_BUCKET;
         };
         inv.setItem(GRID_SLOTS.get(i), this.named(icon, ChatColor.AQUA + name, lore));
      }

      if (safePage > 0) {
         inv.setItem(48, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Vorherige Seite", List.of()));
      }

      inv.setItem(49, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of(ChatColor.GRAY + "Zum Grundst\u00fccks-Men\u00fc")));
      if (safePage < totalPages - 1) {
         inv.setItem(50, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "N\u00e4chste Seite", List.of()));
      }
      return inv;
   }

   public Inventory createParcelMembersMenu(Player viewer, IslandData island, int relX, int relZ, IslandService.ParcelRole role, int page) {
      return this.createParcelMembersMenu(viewer, island, relX, relZ, role, page, "all");
   }

   public Inventory createIslandTrustMenu(Player viewer, IslandData island, IslandService.TrustPermission permission, int page) {
      return this.createIslandTrustMenu(viewer, island, permission, page, "all");
   }

   public Inventory createIslandTrustMenu(Player viewer, IslandData island, IslandService.TrustPermission permission, int page, String filter) {
      String safeFilter = this.normalizeFilter(filter);
      Inventory inv = Bukkit.createInventory(
         new CoreService.IslandTrustMembersInventoryHolder(island.getOwner(), permission.name(), page, safeFilter),
         54,
         "Insel-Member " + permission.name().toLowerCase(Locale.ROOT) + " " + (page + 1)
      );
      this.fillWithPanes(inv);
      List<OfflinePlayer> candidates = new ArrayList<>(Arrays.asList(this.plugin.getServer().getOfflinePlayers()));
      candidates.removeIf(px -> px.getName() == null || px.getName().isBlank() || this.islandService.isIslandOwner(island, px.getUniqueId()));
      candidates.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
      List<OfflinePlayer> filtered = candidates.stream().filter(px -> {
         boolean memberx = this.islandHasTrustPermission(island, px.getUniqueId(), permission);

         return switch (safeFilter) {
            case "online" -> px.isOnline();
            case "members" -> memberx;
            case "nonmembers" -> !memberx;
            default -> true;
         };
      }).toList();
      int totalPages = Math.max(1, (int)Math.ceil((double)filtered.size() / 45.0));
      int safePage = Math.max(0, Math.min(totalPages - 1, page));
      int start = safePage * 45;

      for (int i = 0; i < 45; i++) {
         int idx = start + i;
         if (idx >= filtered.size()) {
            break;
         }

         OfflinePlayer p = filtered.get(idx);
         boolean member = this.islandHasTrustPermission(island, p.getUniqueId(), permission);
         List<String> lore = new ArrayList<>();
         lore.add(ChatColor.GRAY + "Online: " + (p.isOnline() ? "ja" : "nein"));
         lore.add(ChatColor.GRAY + "Status: " + (member ? "ist Member (" + permission.name().toLowerCase(Locale.ROOT) + ")" : "kein Member-Recht"));
         lore.add(ChatColor.YELLOW + "Linksklick = vergeben");
         lore.add(ChatColor.YELLOW + "Rechtsklick = entfernen");
         inv.setItem(
            GRID_SLOTS.get(i), this.named(member ? Material.LIME_DYE : Material.GRAY_DYE, (member ? ChatColor.GREEN : ChatColor.AQUA) + p.getName(), lore)
         );
      }

      inv.setItem(45, this.named(Material.HOPPER, ChatColor.AQUA + "Filter all", List.of(ChatColor.GRAY + "Alle Spieler")));
      inv.setItem(46, this.named(Material.CLOCK, ChatColor.AQUA + "Filter online", List.of(ChatColor.GRAY + "Nur online")));
      inv.setItem(47, this.named(Material.LIME_DYE, ChatColor.AQUA + "Filter members", List.of(ChatColor.GRAY + "Nur Member")));
      inv.setItem(51, this.named(Material.GRAY_DYE, ChatColor.AQUA + "Filter nonmembers", List.of(ChatColor.GRAY + "Nur keine Member")));
      if (safePage > 0) {
         inv.setItem(48, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Vorherige Seite", List.of()));
      }

      inv.setItem(49, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of(ChatColor.GRAY + "Zur Inselansicht")));
      if (safePage < totalPages - 1) {
         inv.setItem(50, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "N\u00e4chste Seite", List.of()));
      }

      inv.setItem(
         52,
         this.named(
            Material.PAPER,
            ChatColor.GOLD + "Recht: " + permission.name().toLowerCase(Locale.ROOT),
            List.of(ChatColor.GRAY + "Build / Container / Redstone / All")
         )
      );
      inv.setItem(53, this.named(Material.COMPARATOR, ChatColor.YELLOW + "Recht wechseln", List.of(ChatColor.GRAY + "Klick = n\u00e4chstes Recht")));
      return inv;
   }

   public Inventory createIslandOwnersMenu(Player viewer, IslandData island, int page, String filter) {
      String safeFilter = this.normalizeFilter(filter);
      Inventory inv = Bukkit.createInventory(new CoreService.IslandOwnersInventoryHolder(island.getOwner(), page, safeFilter), 54, "Insel-Owner " + (page + 1));
      this.fillWithPanes(inv);
      List<OfflinePlayer> candidates = new ArrayList<>(Arrays.asList(this.plugin.getServer().getOfflinePlayers()));
      candidates.removeIf(px -> px.getName() == null || px.getName().isBlank() || px.getUniqueId().equals(island.getOwner()));
      candidates.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
      List<OfflinePlayer> filtered = candidates.stream().filter(px -> {
         boolean ownerx = island.getIslandOwners().contains(px.getUniqueId());
         return switch (safeFilter) {
            case "online" -> px.isOnline();
            case "members" -> ownerx;
            case "nonmembers" -> !ownerx;
            default -> true;
         };
      }).toList();
      int totalPages = Math.max(1, (int)Math.ceil((double)filtered.size() / 45.0));
      int safePage = Math.max(0, Math.min(totalPages - 1, page));
      int start = safePage * 45;

      for (int i = 0; i < 45; i++) {
         int idx = start + i;
         if (idx >= filtered.size()) {
            break;
         }
         OfflinePlayer p = filtered.get(idx);
         boolean owner = island.getIslandOwners().contains(p.getUniqueId());
         List<String> lore = new ArrayList<>();
         lore.add(ChatColor.GRAY + "Online: " + (p.isOnline() ? "ja" : "nein"));
         lore.add(ChatColor.GRAY + "Status: " + (owner ? "ist Owner" : "kein Owner"));
         lore.add(ChatColor.YELLOW + "Linksklick = Owner vergeben");
         lore.add(ChatColor.YELLOW + "Rechtsklick = Owner entfernen");
         inv.setItem(GRID_SLOTS.get(i), this.named(owner ? Material.LIME_DYE : Material.GRAY_DYE, (owner ? ChatColor.GREEN : ChatColor.AQUA) + p.getName(), lore));
      }

      inv.setItem(45, this.named(Material.HOPPER, ChatColor.AQUA + "Filter all", List.of(ChatColor.GRAY + "Alle Spieler")));
      inv.setItem(46, this.named(Material.CLOCK, ChatColor.AQUA + "Filter online", List.of(ChatColor.GRAY + "Nur online")));
      inv.setItem(47, this.named(Material.LIME_DYE, ChatColor.AQUA + "Filter members", List.of(ChatColor.GRAY + "Nur Owner")));
      inv.setItem(51, this.named(Material.GRAY_DYE, ChatColor.AQUA + "Filter nonmembers", List.of(ChatColor.GRAY + "Nur keine Owner")));
      if (safePage > 0) {
         inv.setItem(48, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Vorherige Seite", List.of()));
      }

      inv.setItem(49, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of(ChatColor.GRAY + "Zur Inselansicht")));
      if (safePage < totalPages - 1) {
         inv.setItem(50, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "N\u00e4chste Seite", List.of()));
      }
      return inv;
   }

   public Inventory createIslandMasterMenu(Player viewer, IslandData island) {
      Inventory inv = Bukkit.createInventory(new CoreService.IslandMasterMenuInventoryHolder(island.getOwner()), 27, "Master-Rechte");
      this.fillWithPanes(inv);
      boolean hasInvite = this.islandService.getPendingCoOwnerInviteIsland(viewer.getUniqueId()) != null;
      inv.setItem(11, this.named(Material.PLAYER_HEAD, ChatColor.GOLD + "Master einladen", List.of(ChatColor.YELLOW + "Klick = Spieler w\u00e4hlen")));
      inv.setItem(13, this.named(Material.EMERALD, (hasInvite ? ChatColor.GREEN : ChatColor.YELLOW) + "Einladung annehmen", List.of(ChatColor.GRAY + (hasInvite ? "Einladung vorhanden" : "Keine offene Einladung"), ChatColor.YELLOW + "Klick = annehmen")));
      inv.setItem(15, this.named(Material.BARRIER, ChatColor.RED + "Als Master austreten", List.of(ChatColor.YELLOW + "Klick = austreten")));
      inv.setItem(22, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of(ChatColor.GRAY + "Zur Inselansicht")));
      return inv;
   }

   public Inventory createIslandMasterInviteMenu(Player viewer, IslandData island, int page) {
      Inventory inv = Bukkit.createInventory(new CoreService.IslandMasterInviteInventoryHolder(island.getOwner(), page), 54, "Master einladen " + (page + 1));
      this.fillWithPanes(inv);
      List<? extends Player> candidates = Bukkit.getOnlinePlayers().stream()
         .filter(px -> px.getName() != null && !px.getName().isBlank())
         .filter(px -> !px.getUniqueId().equals(viewer.getUniqueId()))
         .filter(px -> !this.islandService.isIslandMaster(island, px.getUniqueId()))
         .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
         .toList();
      int totalPages = Math.max(1, (int)Math.ceil((double)candidates.size() / 45.0));
      int safePage = Math.max(0, Math.min(totalPages - 1, page));
      int start = safePage * 45;

      for (int i = 0; i < 45; i++) {
         int idx = start + i;
         if (idx >= candidates.size()) {
            break;
         }
         Player target = candidates.get(idx);
         List<String> lore = new ArrayList<>();
         lore.add(ChatColor.YELLOW + "Klick = Einladung senden");
         lore.add(ChatColor.DARK_GRAY + "uuid:" + target.getUniqueId());
         inv.setItem(GRID_SLOTS.get(i), this.named(Material.PLAYER_HEAD, ChatColor.AQUA + target.getName(), lore));
      }
      if (safePage > 0) {
         inv.setItem(48, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Vorherige Seite", List.of()));
      }
      inv.setItem(49, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of(ChatColor.GRAY + "Zu Master-Rechte")));
      if (safePage < totalPages - 1) {
         inv.setItem(50, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "N\u00e4chste Seite", List.of()));
      }
      return inv;
   }

   private boolean islandHasTrustPermission(IslandData island, UUID playerId, IslandService.TrustPermission permission) {
      return switch (permission) {
         case ALL -> island.getTrusted().contains(playerId)
         || island.getTrustedContainers().contains(playerId)
         || island.getTrustedRedstone().contains(playerId);
         case BUILD -> island.getTrusted().contains(playerId);
         case CONTAINER -> island.getTrustedContainers().contains(playerId);
         case REDSTONE -> island.getTrustedRedstone().contains(playerId);
      };
   }

   public Inventory createParcelMembersMenu(Player viewer, IslandData island, int relX, int relZ, IslandService.ParcelRole role, int page, String filter) {
      ParcelData parcel = this.islandService.getParcel(island, relX, relZ);
      String safeFilter = this.normalizeFilter(filter);
      Inventory inv = Bukkit.createInventory(
         new CoreService.ParcelMembersInventoryHolder(island.getOwner(), relX, relZ, role.name(), page, safeFilter),
         54,
         (parcel == null ? "GS " : "GS " + this.islandService.getParcelDisplayName(parcel) + " ")
            + (role == IslandService.ParcelRole.OWNER ? "Owner " : role == IslandService.ParcelRole.USER ? "User " : "PvP ")
            + (page + 1)
      );
      this.fillWithPanes(inv);
      if (parcel == null) {
         return inv;
      } else {
         List<OfflinePlayer> candidates = new ArrayList<>(Arrays.asList(this.plugin.getServer().getOfflinePlayers()));
         candidates.removeIf(px -> px.getName() == null || px.getName().isBlank());
         candidates.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
         List<OfflinePlayer> filtered = candidates.stream()
            .filter(
               px -> {
                  boolean memberx = role == IslandService.ParcelRole.OWNER
                     ? parcel.getOwners().contains(px.getUniqueId())
                     : role == IslandService.ParcelRole.USER
                     ? parcel.getUsers().contains(px.getUniqueId())
                     : parcel.getPvpWhitelist().contains(px.getUniqueId());

                  return switch (safeFilter) {
                     case "online" -> px.isOnline();
                     case "members" -> memberx;
                     case "nonmembers" -> !memberx;
                     default -> true;
                  };
               }
            )
            .toList();
         int totalPages = Math.max(1, (int)Math.ceil((double)filtered.size() / 45.0));
         int safePage = Math.max(0, Math.min(totalPages - 1, page));
         int start = safePage * 45;

         for (int i = 0; i < 45; i++) {
            int idx = start + i;
            if (idx >= filtered.size()) {
               break;
            }

            OfflinePlayer p = filtered.get(idx);
            boolean member = role == IslandService.ParcelRole.OWNER
               ? parcel.getOwners().contains(p.getUniqueId())
               : role == IslandService.ParcelRole.USER
               ? parcel.getUsers().contains(p.getUniqueId())
               : parcel.getPvpWhitelist().contains(p.getUniqueId());
            boolean self = p.getUniqueId().equals(viewer.getUniqueId());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Online: " + (p.isOnline() ? "ja" : "nein"));
            lore.add(ChatColor.GRAY + "Status: " + (member ? "hat Rolle" : "hat keine Rolle"));
            lore.add(ChatColor.YELLOW + "Linksklick = hinzuf\u00fcgen");
            lore.add(ChatColor.YELLOW + "Rechtsklick = entfernen");
            if (self) {
               lore.add(ChatColor.DARK_GRAY + "Du selbst");
            }

            inv.setItem(
               GRID_SLOTS.get(i), this.named(member ? Material.LIME_DYE : Material.GRAY_DYE, (member ? ChatColor.GREEN : ChatColor.AQUA) + p.getName(), lore)
            );
         }

         inv.setItem(45, this.named(Material.HOPPER, ChatColor.AQUA + "Filter all", List.of(ChatColor.GRAY + "Alle Spieler")));
         inv.setItem(46, this.named(Material.CLOCK, ChatColor.AQUA + "Filter online", List.of(ChatColor.GRAY + "Nur online")));
         inv.setItem(47, this.named(Material.LIME_DYE, ChatColor.AQUA + "Filter members", List.of(ChatColor.GRAY + "Nur mit Rolle")));
         inv.setItem(51, this.named(Material.GRAY_DYE, ChatColor.AQUA + "Filter nonmembers", List.of(ChatColor.GRAY + "Nur ohne Rolle")));
         if (safePage > 0) {
            inv.setItem(48, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Vorherige Seite", List.of()));
         }

         inv.setItem(49, this.named(Material.ARROW, ChatColor.YELLOW + "Zur\u00fcck", List.of()));
         if (safePage < totalPages - 1) {
            inv.setItem(50, this.named(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "N\u00e4chste Seite", List.of()));
         }

         return inv;
      }
   }

   private String normalizeFilter(String filter) {
      String f = filter == null ? "all" : filter.toLowerCase(Locale.ROOT);

      return switch (f) {
         case "all", "islands", "parcels", "warps", "mine", "online", "members", "nonmembers" -> f;
         default -> "all";
      };
   }

   private ItemStack toggleItem(Material material, String name, boolean state) {
      return this.named(
         state ? Material.LIME_DYE : Material.RED_DYE,
         (state ? ChatColor.GREEN : ChatColor.RED) + name + ": " + (state ? "AN" : "AUS"),
         List.of(ChatColor.GRAY + "Klick zum Umschalten")
      );
   }

   public List<Biome> getBiomeOptions() {
      return BIOME_OPTIONS;
   }

   public String biomeDisplayNameDe(Biome biome) {
      String mapped = DE_BIOME_NAMES.get(biome);
      if (mapped != null) {
         return mapped;
      } else {
         String[] parts = biome.name().toLowerCase(Locale.ROOT).split("_");
         StringBuilder sb = new StringBuilder();

         for (String part : parts) {
            if (!part.isBlank()) {
               if (!sb.isEmpty()) {
                  sb.append(' ');
               }

               sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
         }

         return sb.toString();
      }
   }

   public void beginIslandTitleInput(Player player, IslandData island) {
      if (player != null && island != null) {
         this.pendingIslandTitleInput.put(player.getUniqueId(), island.getOwner());
         player.closeInventory();
         player.sendMessage(ChatColor.GOLD + "Inseltitel-Eingabe gestartet.");
         player.sendMessage(ChatColor.YELLOW + "Schreibe jetzt den Titel in den Chat.");
         player.sendMessage(ChatColor.GRAY + "Mit 'clear' l\u00f6schst du den Titel, mit 'abbrechen' brichst du ab.");
      }
   }

   public boolean isAwaitingIslandTitleInput(UUID playerId) {
      return playerId != null && this.pendingIslandTitleInput.containsKey(playerId);
   }

   public void beginIslandWarpInput(Player player, IslandData island) {
      if (player != null && island != null) {
         this.pendingIslandWarpInput.put(player.getUniqueId(), island.getOwner());
         player.closeInventory();
         player.sendMessage(ChatColor.GOLD + "Warp-Eingabe gestartet.");
         player.sendMessage(ChatColor.YELLOW + "Schreibe jetzt den Warpnamen in den Chat.");
         player.sendMessage(ChatColor.GRAY + "Mit 'clear' l\u00f6schst du den Warp, mit 'abbrechen' brichst du ab.");
      }
   }

   public boolean isAwaitingIslandWarpInput(UUID playerId) {
      return playerId != null && this.pendingIslandWarpInput.containsKey(playerId);
   }

   public void beginParcelRenameInput(Player player, IslandData island, ParcelData parcel) {
      if (player != null && island != null && parcel != null) {
         this.pendingParcelRenameInput.put(player.getUniqueId(), island.getOwner() + ":" + parcel.getChunkKey());
         player.closeInventory();
         player.sendMessage(ChatColor.GOLD + "GS-Umbenennung gestartet.");
         player.sendMessage(ChatColor.YELLOW + "Schreibe jetzt den neuen GS-Namen in den Chat.");
         player.sendMessage(ChatColor.GRAY + "Mit 'reset' setzt du auf den Chunknamen zur\u00fcck, mit 'abbrechen' brichst du ab.");
      }
   }

   public boolean isAwaitingParcelRenameInput(UUID playerId) {
      return playerId != null && this.pendingParcelRenameInput.containsKey(playerId);
   }

   public void handleIslandTitleChatInput(Player player, String message) {
      if (player != null) {
         UUID islandOwner = this.pendingIslandTitleInput.remove(player.getUniqueId());
         if (islandOwner != null) {
            IslandData island = this.islandService.getIsland(islandOwner).orElse(null);
            if (island == null) {
               player.sendMessage(ChatColor.RED + "Insel nicht gefunden.");
            } else if (!this.islandService.isIslandOwner(island, player.getUniqueId()) && !player.isOp()) {
               player.sendMessage(ChatColor.RED + "Nur Inselbesitzer.");
            } else {
               String msg = message == null ? "" : message.trim();
               if (msg.equalsIgnoreCase("abbrechen") || msg.equalsIgnoreCase("cancel")) {
                  player.sendMessage(ChatColor.YELLOW + "Titel-Eingabe abgebrochen.");
               } else if (msg.equalsIgnoreCase("clear")) {
                  island.setTitle(null);
                  this.islandService.save();
                  player.sendMessage(ChatColor.GREEN + "Inseltitel zur\u00fcckgesetzt.");
               } else if (msg.isBlank()) {
                  player.sendMessage(ChatColor.RED + "Titel darf nicht leer sein.");
               } else if (msg.length() > 40) {
                  player.sendMessage(ChatColor.RED + "Titel darf max. 40 Zeichen haben.");
               } else {
                  island.setTitle(msg);
                  this.islandService.save();
                  player.sendMessage(ChatColor.GREEN + "Inseltitel gesetzt: " + ChatColor.GOLD + msg);
               }
            }
         }
      }
   }

   public void handleIslandTitleChatInputSafe(Player player, String message) {
      if (player == null) {
         return;
      }
      UUID islandOwner = this.pendingIslandTitleInput.remove(player.getUniqueId());
      if (islandOwner == null) {
         return;
      }
      IslandData island = this.islandService.getIsland(islandOwner).orElse(null);
      if (island == null) {
         player.sendMessage(ChatColor.RED + "Insel nicht gefunden.");
         return;
      }
      if (!this.islandService.isIslandOwner(island, player.getUniqueId()) && !player.isOp()) {
         player.sendMessage(ChatColor.RED + "Nur Inselbesitzer.");
         return;
      }
      String msg = message == null ? "" : message.trim();
      if (msg.equalsIgnoreCase("abbrechen") || msg.equalsIgnoreCase("cancel")) {
         player.sendMessage(ChatColor.YELLOW + "Titel-Eingabe abgebrochen.");
         return;
      }
      if (msg.equalsIgnoreCase("clear")) {
         island.setTitle(null);
         this.islandService.save();
         player.sendMessage(ChatColor.GREEN + "Inseltitel zur\u00fcckgesetzt.");
         return;
      }
      if (msg.isBlank()) {
         player.sendMessage(ChatColor.RED + "Titel darf nicht leer sein.");
         return;
      }
      if (msg.length() > 40) {
         player.sendMessage(ChatColor.RED + "Titel darf max. 40 Zeichen haben.");
         return;
      }
      if (this.islandService.isIslandLabelTaken(msg, island.getOwner(), island.getTitle())) {
         player.sendMessage(ChatColor.RED + "Name ist bereits als Inselname oder Warp vergeben.");
         return;
      }
      island.setTitle(msg);
      this.islandService.save();
      player.sendMessage(ChatColor.GREEN + "Inseltitel gesetzt: " + ChatColor.GOLD + msg);
   }

   public void handleIslandWarpChatInput(Player player, String message) {
      if (player == null) {
         return;
      }
      UUID islandOwner = this.pendingIslandWarpInput.remove(player.getUniqueId());
      if (islandOwner == null) {
         return;
      }
      IslandData island = this.islandService.getIsland(islandOwner).orElse(null);
      if (island == null) {
         player.sendMessage(ChatColor.RED + "Insel nicht gefunden.");
         return;
      }
      if (!this.islandService.isIslandOwner(island, player.getUniqueId()) && !player.isOp()) {
         player.sendMessage(ChatColor.RED + "Nur Inselbesitzer.");
         return;
      }
      String msg = message == null ? "" : message.trim();
      if (msg.equalsIgnoreCase("abbrechen") || msg.equalsIgnoreCase("cancel")) {
         player.sendMessage(ChatColor.YELLOW + "Warp-Eingabe abgebrochen.");
         return;
      }
      if (msg.equalsIgnoreCase("clear")) {
         island.setWarpName(null);
         island.setWarpLocation(null);
         this.islandService.save();
         player.sendMessage(ChatColor.GREEN + "Warp zur\u00fcckgesetzt.");
         return;
      }
      if (msg.isBlank()) {
         player.sendMessage(ChatColor.RED + "Warpname darf nicht leer sein.");
         return;
      }
      if (msg.length() > 40) {
         player.sendMessage(ChatColor.RED + "Warpname darf max. 40 Zeichen haben.");
         return;
      }
      if (this.islandService.isIslandLabelTaken(msg, island.getOwner(), island.getWarpName())) {
         player.sendMessage(ChatColor.RED + "Name ist bereits als Inselname oder Warp vergeben.");
         return;
      }
      island.setWarpName(msg);
      island.setWarpLocation(player.getLocation().clone());
      this.islandService.save();
      player.sendMessage(ChatColor.GREEN + "Warp gesetzt: " + ChatColor.GOLD + msg);
   }

   public void handleParcelRenameChatInput(Player player, String message) {
      if (player == null) {
         return;
      }
      String target = this.pendingParcelRenameInput.remove(player.getUniqueId());
      if (target == null) {
         return;
      }
      int split = target.lastIndexOf(':');
      if (split <= 0 || split >= target.length() - 1) {
         player.sendMessage(ChatColor.RED + "Grundst\u00fcck nicht gefunden.");
         return;
      }
      UUID islandOwner;
      try {
         islandOwner = UUID.fromString(target.substring(0, split));
      } catch (IllegalArgumentException ex) {
         player.sendMessage(ChatColor.RED + "Grundst\u00fcck nicht gefunden.");
         return;
      }
      IslandData island = this.islandService.getIsland(islandOwner).orElse(null);
      ParcelData parcel = island == null ? null : island.getParcels().get(target.substring(split + 1));
      if (island == null || parcel == null) {
         player.sendMessage(ChatColor.RED + "Grundst\u00fcck nicht gefunden.");
      } else if (!this.islandService.isParcelOwner(island, parcel, player.getUniqueId()) && !player.isOp()) {
         player.sendMessage(ChatColor.RED + "Nur GS-Owner.");
      } else {
         String msg = message == null ? "" : message.trim();
         if (msg.equalsIgnoreCase("abbrechen") || msg.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.YELLOW + "GS-Umbenennung abgebrochen.");
         } else if (msg.equalsIgnoreCase("reset") || msg.equalsIgnoreCase("clear")) {
            parcel.setName(parcel.getChunkKey());
            this.islandService.save();
            player.sendMessage(ChatColor.GREEN + "GS-Name zur\u00fcckgesetzt: " + ChatColor.GOLD + parcel.getChunkKey());
         } else if (msg.isBlank()) {
            player.sendMessage(ChatColor.RED + "Name darf nicht leer sein.");
         } else if (msg.length() > 32) {
            player.sendMessage(ChatColor.RED + "Name darf max. 32 Zeichen haben.");
         } else {
            parcel.setName(msg);
            this.islandService.save();
            player.sendMessage(ChatColor.GREEN + "GS umbenannt zu: " + ChatColor.GOLD + msg);
         }
      }
   }

   public void processCoreInputInventory(Inventory inventory, IslandData island, Player player) {
      this.processCoreItemsFromSlots(inventory, island, player, INPUT_SLOTS);
   }

   public void processCoreShulkerInventory(Inventory inventory, IslandData island, Player player) {
      if (inventory != null && island != null) {
         int size = inventory.getSize();
         int[] slots = new int[size];
         int i = 0;

         while (i < size) {
            slots[i] = i++;
         }

         this.processCoreItemsFromSlots(inventory, island, player, slots);
      }
   }

   private void processCoreItemsFromSlots(Inventory inventory, IslandData island, Player player, int[] slots) {
      if (inventory != null && island != null && slots != null) {
         IslandLevelDefinition next = this.islandService.getNextLevelDef(island);
         Map<Material, Integer> mergedUpgrade = new LinkedHashMap<>();
         Map<Material, Integer> mergedIslandLevel = new LinkedHashMap<>();
         Map<Material, Integer> mergedNotRequired = new LinkedHashMap<>();

         for (int slot : slots) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
               if (next == null) {
                  int accepted = this.acceptAsCoreValue(island, item.getType(), item.getAmount());
                  if (accepted > 0) {
                     island.addProgress(item.getType(), accepted);
                     inventory.setItem(slot, null);
                     mergedIslandLevel.merge(item.getType(), accepted, Integer::sum);
                  } else if (player != null) {
                     player.getInventory().addItem(new ItemStack[]{item});
                     inventory.setItem(slot, null);
                  }
               } else {
                  Integer req = next.getRequirements().get(item.getType());
                  if (req == null) {
                     int accepted = this.acceptAsCoreValue(island, item.getType(), item.getAmount());
                     if (accepted > 0) {
                        island.addProgress(item.getType(), accepted);
                        inventory.setItem(slot, null);
                        mergedIslandLevel.merge(item.getType(), accepted, Integer::sum);
                     } else if (player != null) {
                        player.getInventory().addItem(new ItemStack[]{item});
                        inventory.setItem(slot, null);
                        mergedNotRequired.merge(item.getType(), item.getAmount(), Integer::sum);
                     }
                  } else {
                     int current = island.getProgress(item.getType());
                     int missing = Math.max(0, req - current);
                     int amount = item.getAmount();
                     int acceptedUpgrade = Math.min(Math.max(0, missing), amount);
                     if (acceptedUpgrade > 0) {
                        island.addProgress(item.getType(), acceptedUpgrade);
                        amount -= acceptedUpgrade;
                        mergedUpgrade.merge(item.getType(), acceptedUpgrade, Integer::sum);
                     }

                     int acceptedValue = 0;
                     if (amount > 0) {
                        acceptedValue = this.acceptAsCoreValue(island, item.getType(), amount);
                        amount -= acceptedValue;
                        if (acceptedValue > 0) {
                           island.addProgress(item.getType(), acceptedValue);
                           mergedIslandLevel.merge(item.getType(), acceptedValue, Integer::sum);
                        }
                     }

                     if (amount <= 0) {
                        inventory.setItem(slot, null);
                     } else {
                        item.setAmount(amount);
                        inventory.setItem(slot, item);
                     }
                  }
               }
            }
         }

         if (player != null) {
            for (Entry<Material, Integer> entry : mergedUpgrade.entrySet()) {
               this.sendItemCountMessage(player, island, "Upgrade verbucht: ", entry.getValue(), entry.getKey(), NamedTextColor.GREEN);
            }

            for (Entry<Material, Integer> entry : mergedIslandLevel.entrySet()) {
               this.sendItemCountMessage(player, island, "Als Insel-Level verbucht: ", entry.getValue(), entry.getKey(), NamedTextColor.GREEN);
            }

            for (Entry<Material, Integer> entry : mergedNotRequired.entrySet()) {
               this.sendItemNotRequiredMessage(player, entry.getKey(), entry.getValue());
            }
         }

         this.islandService.save();
      }
   }

   private void sendItemCountMessage(Player player, IslandData island, String prefix, int amount, Material material, NamedTextColor prefixColor) {
      if (player != null && material != null) {
         double totalLevel = island == null ? 0.0 : this.islandService.calculateIslandLevelValue(island);
         double reservedLevel = island == null ? 0.0 : this.islandService.calculateReservedUpgradeLevelValue(island);
         String levelText = this.formatIslandLevel(totalLevel);
         Component message = ((TextComponent)((TextComponent)((TextComponent)((TextComponent)Component.text(prefix, prefixColor)
                        .append(Component.text(String.valueOf(Math.max(0, amount)), NamedTextColor.WHITE)))
                     .append(Component.text("x ", NamedTextColor.WHITE)))
                  .append(Component.translatable(material.translationKey(), NamedTextColor.WHITE)))
               .append(Component.text(" | Insel-Level: ", NamedTextColor.GREEN)))
            .append(Component.text(levelText, NamedTextColor.WHITE));
         if (reservedLevel > 0.0) {
            message = message.append(Component.text(" (", NamedTextColor.GRAY))
               .append(Component.text("-" + this.formatIslandLevel(reservedLevel), NamedTextColor.GRAY))
               .append(Component.text(")", NamedTextColor.GRAY));
         }

         player.sendMessage(message);
      }
   }

   private void sendItemNotRequiredMessage(Player player, Material material, int amount) {
      if (player != null && material != null) {
         Component message = ((TextComponent)Component.text(Math.max(0, amount) + "x ", NamedTextColor.RED)
               .append(Component.translatable(material.translationKey(), NamedTextColor.RED)))
            .append(Component.text(" wird aktuell nicht ben\u00f6tigt.", NamedTextColor.RED));
         player.sendMessage(message);
      }
   }

   public void depositPlayerExperience(Player player, IslandData island) {
      if (player != null && island != null) {
         int total = Math.max(0, player.getTotalExperience());
         if (total <= 0) {
            player.sendMessage(ChatColor.RED + "Du hast keine Erfahrung zum Einlagern.");
         } else {
            island.addStoredExperience((long)total);
            this.setPlayerTotalExperience(player, 0);
            this.islandService.save();
            player.sendMessage(ChatColor.GREEN + "Eingelagert: " + total + " Erfahrung.");
         }
      }
   }

   public void withdrawPlayerExperience(Player player, IslandData island, int levels) {
      if (player != null && island != null) {
         int safeLevels = Math.max(1, levels);
         int xpPoints = this.totalExperienceForLevelDelta(player.getLevel(), safeLevels);
         if (xpPoints > 0) {
            if (island.getStoredExperience() < (long)xpPoints) {
               player.sendMessage(ChatColor.RED + "Nicht genug gespeicherte Erfahrung.");
            } else {
               island.takeStoredExperience((long)xpPoints);
               this.setPlayerTotalExperience(player, player.getTotalExperience() + xpPoints);
               this.islandService.save();
               player.sendMessage(ChatColor.GREEN + "Ausgezahlt: " + xpPoints + " Erfahrung.");
            }
         }
      }
   }

   public boolean fillExperienceBottles(Player player, IslandData island, int count) {
      if (player == null || island == null) return false;
      int safeCount = Math.max(1, count);
      int amount = Math.min(64, safeCount);
      int addable = this.addableAmount(player, Material.EXPERIENCE_BOTTLE, amount);
      if (addable <= 0) {
         player.sendMessage(ChatColor.RED + "Kein Platz im Inventar.");
         return false;
      }
      long realCost = this.islandService.getXpBottleCostPerBottle() * addable;
      if (island.getStoredExperience() < realCost) {
         player.sendMessage(ChatColor.RED + "Nicht genug gespeicherte Erfahrung.");
         return false;
      }
      if (!this.islandService.spendStoredExperience(island, realCost)) {
         player.sendMessage(ChatColor.RED + "Nicht genug gespeicherte Erfahrung.");
         return false;
      }
      player.getInventory().addItem(new ItemStack(Material.EXPERIENCE_BOTTLE, addable));
      player.sendMessage(ChatColor.GREEN + "Abgefuellt: " + addable + " XP-Flaschen (Kosten: " + realCost + ").");
      return true;
   }

   private int totalExperienceForLevelDelta(int currentLevel, int levelDelta) {
      int from = Math.max(0, currentLevel);
      int to = Math.max(from, from + Math.max(0, levelDelta));
      return Math.max(0, this.cumulativeExperienceForLevel(to) - this.cumulativeExperienceForLevel(from));
   }

   private int cumulativeExperienceForLevel(int level) {
      int lv = Math.max(0, level);
      if (lv <= 16) {
         return lv * lv + 6 * lv;
      } else {
         return lv <= 31
            ? (int)(2.5 * (double)lv * (double)lv - 40.5 * (double)lv + 360.0)
            : (int)(4.5 * (double)lv * (double)lv - 162.5 * (double)lv + 2220.0);
      }
   }

   private void setPlayerTotalExperience(Player player, int amount) {
      int safe = Math.max(0, amount);
      player.setExp(0.0F);
      player.setLevel(0);
      player.setTotalExperience(0);
      player.giveExp(safe);
   }

   private String formatMillisShort(long ms) {
      long safe = Math.max(0L, ms);
      long totalSec = safe / 1000L;
      long min = totalSec / 60L;
      long sec = totalSec % 60L;
      return min + "m " + sec + "s";
   }

   private int addableAmount(Player player, Material material, int requested) {
      if (player == null || material == null) return 0;
      int remaining = Math.max(0, requested);
      int maxStack = material.getMaxStackSize();
      ItemStack[] contents = player.getInventory().getStorageContents();
      for (ItemStack item : contents) {
         if (remaining <= 0) break;
         if (item == null || item.getType() == Material.AIR) {
            remaining -= maxStack;
            continue;
         }
         if (item.getType() != material) continue;
         remaining -= Math.max(0, maxStack - item.getAmount());
      }
      return Math.max(0, requested - Math.max(0, remaining));
   }

   private int acceptAsCoreValue(IslandData island, Material material, int amount) {
      if (island != null && material != null && amount > 0) {
         double value = this.getBlockValue(material);
         if (value <= 0.0) {
            return 0;
         } else {
            long gain = Math.max(1L, Math.round(value * (double)amount * 100.0));
            this.islandService.addPoints(island, gain);
            return amount;
         }
      } else {
         return 0;
      }
   }

   private String materialDisplayNameDe(Material material) {
      String mapped = DE_MATERIAL_NAMES.get(material);
      if (mapped != null) {
         return this.sanitizeLegacyText(mapped);
      } else {
         String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
         StringBuilder sb = new StringBuilder();

         for (String part : parts) {
            if (!part.isBlank()) {
               if (!sb.isEmpty()) {
                  sb.append(' ');
               }

               sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
         }

         return this.sanitizeLegacyText(sb.toString());
      }
   }

   public void startDisplayTask() {
      this.stopDisplayTask();
      this.displayTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, () -> {
         for (IslandData island : this.islandService.getAllIslands()) {
            this.ensureCorePlaced(island);
            this.updateDisplays(island);
         }
      }, 20L, 100L);
      this.animalLookTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, () -> {
         this.updateAnimalLookDisplays();
      }, 1L, 1L);
   }

   public void stopDisplayTask() {
      if (this.displayTaskId != -1) {
         Bukkit.getScheduler().cancelTask(this.displayTaskId);
         this.displayTaskId = -1;
      }

      if (this.animalLookTaskId != -1) {
         Bukkit.getScheduler().cancelTask(this.animalLookTaskId);
         this.animalLookTaskId = -1;
      }
   }

   private void updateDisplays(IslandData island) {
      if (island.getCoreLocation() != null && island.getCoreLocation().getWorld() != null) {
         this.processCoreShulkerBuffer(island);
         Location coreTop = island.getCoreLocation().clone().add(0.5, 1.01, 0.5);
         List<String> lines = this.buildCoreDisplayLines(island);
         this.removeTaggedDisplaysByPrefixInIsland(island, "skycity_core_line_");
         double yOffset = 0.0;
         int lineIndex = 0;

         for (int i = lines.size() - 1; i >= 0; i--) {
            String line = this.sanitizeLegacyText(lines.get(i));
            if (line != null && !line.isBlank()) {
               this.ensureText(coreTop.clone().add(0.0, yOffset, 0.0), "skycity_core_line_" + lineIndex, line);
               yOffset += 0.28;
               lineIndex++;
            } else {
               yOffset += 0.18;
            }
         }

         this.removeStaleCoreDisplays(island, coreTop, lines);
      } else {
         this.removeCoreDisplays(island);
      }
   }

   private void processCoreShulkerBuffer(IslandData island) {
      if (island != null && island.getCoreLocation() != null) {
         Block block = island.getCoreLocation().getBlock();
         if (block.getState() instanceof ShulkerBox shulker) {
            this.processCoreShulkerInventory(shulker.getInventory(), island, null);
         }
      }
   }

   private void removeStaleCoreDisplays(IslandData island, Location coreDisplayBase, List<String> lines) {
      int nonBlankLines = 0;
      if (lines != null) {
         for (String line : lines) {
            if (line != null && !line.isBlank()) {
               nonBlankLines++;
            }
         }
      }

      double maxDist = Math.max(4.2, (double)nonBlankLines * 0.32 + 1.2);

      for (Entity e : this.islandService.getEntitiesInIsland(island)) {
         if (e instanceof ArmorStand) {
            ArmorStand stand = (ArmorStand)e;
            boolean coreTag = false;

            for (String tag : stand.getScoreboardTags()) {
               if (tag.startsWith("skycity_core_")) {
                  coreTag = true;
                  break;
               }
            }

            if (coreTag) {
               if (!stand.getWorld().equals(coreDisplayBase.getWorld())) {
                  stand.remove();
               } else if (stand.getLocation().distanceSquared(coreDisplayBase) > maxDist * maxDist) {
                  stand.remove();
               }
            }
         }
      }
   }

   private void ensureText(Location location, String tag, String text) {
      for (Entity e : location.getWorld().getNearbyEntities(location, 0.6, 0.8, 0.6)) {
         if (e instanceof ArmorStand stand && e.getScoreboardTags().contains(tag)) {
            stand.setCustomNameVisible(true);
            stand.setCustomName(text);
            if (stand.getLocation().distanceSquared(location) > 0.04) {
               stand.teleport(location);
            }

            return;
         }
      }

      ArmorStand stand = (ArmorStand)location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
      stand.setVisible(false);
      stand.setMarker(true);
      stand.setGravity(false);
      stand.setSmall(true);
      stand.setCustomNameVisible(true);
      stand.setCustomName(text);
      stand.addScoreboardTag(tag);
   }

   private void removeTaggedDisplaysInIsland(IslandData island, String tag) {
      for (Entity e : this.islandService.getEntitiesInIsland(island)) {
         if (e instanceof ArmorStand && e.getScoreboardTags().contains(tag)) {
            e.remove();
         }
      }
   }

   private void removeTaggedDisplaysByPrefixInIsland(IslandData island, String prefix) {
      for (Entity e : this.islandService.getEntitiesInIsland(island)) {
         if (e instanceof ArmorStand) {
            ArmorStand stand = (ArmorStand)e;

            for (String tag : stand.getScoreboardTags()) {
               if (tag.startsWith(prefix)) {
                  stand.remove();
                  break;
               }
            }
         }
      }
   }

   private List<String> buildCoreDisplayLines(IslandData island) {
      List<String> lines = new ArrayList<>();
      CoreService.CoreDisplayMode mode = this.getCoreDisplayMode(island, island == null ? null : island.getCoreLocation());
      if (mode == CoreService.CoreDisplayMode.OFF) {
         return lines;
      } else {
         String title = island.getTitle() != null && !island.getTitle().isBlank() ? ChatColor.stripColor(island.getTitle()) : "Core";
         switch (mode) {
            case ALL:
               lines.add(ChatColor.GOLD + title);
               lines.addAll(this.buildCoreSummaryLore(island));
               lines.add(" ");
               lines.addAll(this.buildUpgradeLore(island));
               break;
            case TITLE_ONLY:
               lines.add(ChatColor.GOLD + title);
               break;
            case ISLAND_INFO_ONLY:
               lines.add(ChatColor.GOLD + title);
               lines.addAll(this.buildCoreSummaryLore(island));
               break;
            case UPGRADE_ONLY:
               lines.addAll(this.buildUpgradeLore(island));
               break;
            case OFF:
               return lines;
         }

         return lines;
      }
   }

   public CoreService.CoreDisplayMode cycleCoreDisplayMode(IslandData island, Location selectedCoreLocation) {
      if (island == null) {
         return CoreService.CoreDisplayMode.ALL;
      } else {
         Location coreLoc = this.resolveSelectedCoreLocation(island, selectedCoreLocation, null);
         CoreService.CoreDisplayMode next = this.getCoreDisplayMode(island, coreLoc).next();
         this.setCoreDisplayMode(coreLoc, next);
         this.islandService.save();
         this.refreshCoreDisplay(island);
         return next;
      }
   }

   public CoreService.CoreDisplayMode getCoreDisplayMode(IslandData island, Location selectedCoreLocation) {
      if (island == null) {
         return CoreService.CoreDisplayMode.ALL;
      } else {
         Location coreLoc = this.resolveSelectedCoreLocation(island, selectedCoreLocation, null);
         if (coreLoc == null) {
            return CoreService.CoreDisplayMode.ALL;
         } else {
            Block block = coreLoc.getBlock();
            if (block.getState() instanceof ShulkerBox shulker) {
               String raw = (String)shulker.getPersistentDataContainer().get(this.plugin.getCoreDisplayModeKey(), PersistentDataType.STRING);
               return CoreService.CoreDisplayMode.from(raw);
            } else {
               return CoreService.CoreDisplayMode.ALL;
            }
         }
      }
   }

   public String displayModeLabel(CoreService.CoreDisplayMode mode) {
      if (mode == null) {
         return "Alles";
      } else {
         return switch (mode) {
            case ALL -> "Alles";
            case TITLE_ONLY -> "Nur Titel";
            case ISLAND_INFO_ONLY -> "Nur Inselinfos";
            case UPGRADE_ONLY -> "Nur Upgrade";
            case OFF -> "Aus";
         };
      }
   }

   private void setCoreDisplayMode(Location coreLocation, CoreService.CoreDisplayMode mode) {
      if (coreLocation != null && coreLocation.getWorld() != null && mode != null) {
         Block block = coreLocation.getBlock();
         if (block.getState() instanceof ShulkerBox shulker) {
            shulker.getPersistentDataContainer().set(this.plugin.getCoreDisplayModeKey(), PersistentDataType.STRING, mode.name());
            shulker.update(true, false);
         }
      }
   }

   private Location resolveSelectedCoreLocation(IslandData island, Location selectedCoreLocation, Location fallback) {
      Location loc = selectedCoreLocation;
      if (selectedCoreLocation == null && island != null) {
         loc = island.getCoreLocation();
      }

      if (loc == null) {
         loc = fallback;
      }

      return loc != null && loc.getWorld() != null ? loc.clone() : null;
   }

   public void sendUpgradeStatusChat(Player player, IslandData island) {
      if (player != null && island != null) {
         String title = island.getTitle() != null && !island.getTitle().isBlank() ? ChatColor.stripColor(island.getTitle()) : "Core";
         player.sendMessage("" + ChatColor.GOLD + ChatColor.BOLD + title);

         for (String line : this.buildCoreSummaryLore(island)) {
            player.sendMessage(this.sanitizeLegacyText(line));
         }

         player.sendMessage(" ");

         for (String line : this.buildUpgradeLore(island)) {
            player.sendMessage(this.sanitizeLegacyText(line));
         }
      }
   }

   public void sendCurrentChunkStatusWithUnlock(Player player, IslandData island) {
      if (player != null && island != null) {
         IslandData islandAtPlayer = this.islandService.getIslandAt(player.getLocation());
         if (islandAtPlayer == null || !islandAtPlayer.getOwner().equals(island.getOwner())) {
            player.sendMessage(ChatColor.YELLOW + "Chunkanzeige funktioniert nur innerhalb deiner Insel.");
            return;
         }
         int relX = this.islandService.relativeChunkX(island, player.getLocation().getChunk().getX());
         int relZ = this.islandService.relativeChunkZ(island, player.getLocation().getChunk().getZ());
         int displayX = this.islandService.displayChunkX(relX);
         int displayZ = this.islandService.displayChunkZ(relZ);
         boolean unlocked = this.islandService.isChunkUnlocked(island, relX, relZ);
         player.sendMessage(
            ChatColor.AQUA
               + "Aktueller Chunk: "
               + ChatColor.WHITE
               + displayX
               + ":"
               + displayZ
               + ChatColor.DARK_GRAY
               + " | "
               + ChatColor.AQUA
               + "Status: "
               + (unlocked ? ChatColor.GREEN + "freigeschaltet" : ChatColor.RED + "gesperrt")
         );
         if (!unlocked) {
            net.md_5.bungee.api.chat.TextComponent unlock = new net.md_5.bungee.api.chat.TextComponent(ChatColor.GOLD + "[Chunk freischalten]");
            unlock.setClickEvent(new ClickEvent(Action.RUN_COMMAND, "/is chunkunlock"));
            unlock.setHoverEvent(
               new HoverEvent(
                  net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.YELLOW + "Klick: aktuellen Chunk freischalten").create()
               )
            );
            player.spigot().sendMessage(unlock);
         }
      }
   }

   private void updateAnimalLookDisplays() {
      Set<UUID> activeTargets = ConcurrentHashMap.newKeySet();

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (!player.isOnline()) continue;
         RayTraceResult trace = player.getWorld()
            .rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 12.0, 0.2, entity -> entity instanceof Animals || this.islandService.isTrackedGolem(entity.getType()));
         Entity target = trace == null ? null : trace.getHitEntity();
         if ((target instanceof Animals || target != null && this.islandService.isTrackedGolem(target.getType())) && target.isValid()) {
            activeTargets.add(target.getUniqueId());
            this.playerAnimalLookTargets.put(player.getUniqueId(), target.getUniqueId());
         } else {
            this.playerAnimalLookTargets.remove(player.getUniqueId());
         }
      }

      for (UUID entityId : activeTargets) {
         Entity entity = Bukkit.getEntity(entityId);
         if (!(entity instanceof Animals) && (entity == null || !this.islandService.isTrackedGolem(entity.getType()))) continue;
         if (!entity.isValid()) continue;
         IslandData island = this.islandService.getIslandAt(entity.getLocation());
         if (island == null) continue;
         String tag = this.animalLookTag(entityId);
         this.ensureText(entity.getLocation().clone().add(0.0, 1.4, 0.0), tag, this.buildEntityLimitLookText(entity, island));
      }

      for (UUID entityId : new ArrayList<>(this.visibleAnimalLookTargets)) {
         if (!activeTargets.contains(entityId)) {
            this.removeAnimalLookDisplay(entityId);
         }
      }

      this.visibleAnimalLookTargets.clear();
      this.visibleAnimalLookTargets.addAll(activeTargets);
   }

   private String buildEntityLimitLookText(Entity entity, IslandData island) {
      if (entity != null && this.islandService.isTrackedGolem(entity.getType())) {
         return ChatColor.GREEN + "Golems: " + this.islandService.getGolemCount(island) + "/" + this.islandService.getCurrentLevelDef(island).getGolemLimit();
      }
      return ChatColor.GREEN + "Tiere: " + this.islandService.getAnimalCount(island) + "/" + this.islandService.getCurrentLevelDef(island).getAnimalLimit();
   }

   private void removeAnimalLookDisplay(UUID animalId) {
      String tag = this.animalLookTag(animalId);

      for (World world : Bukkit.getWorlds()) {
         for (Entity e : world.getEntities()) {
            if (e instanceof ArmorStand && e.getScoreboardTags().contains(tag)) {
               e.remove();
            }
         }
      }
   }

   private String animalLookTag(UUID animalId) {
      return "skycity_animal_look_" + animalId.toString().substring(0, 8);
   }

   public void showIslandLimitHint(Player player, IslandData island, Material type) {
      if (player == null || island == null || type == null) return;
      String label;
      int used;
      int limit;

      if (this.islandService.isInventoryLimitedMaterial(type)) {
         label = "Behaelter";
         used = this.islandService.getCachedInventoryBlockCount(island);
         limit = 100;
      } else if (type == Material.HOPPER) {
         label = "Trichter";
         used = this.islandService.getCachedHopperCount(island);
         limit = this.islandService.getCurrentLevelDef(island).getHopperLimit();
      } else if (type == Material.PISTON || type == Material.STICKY_PISTON) {
         label = "Kolben";
         used = this.islandService.getCachedPistonCount(island);
         limit = this.islandService.getCurrentLevelDef(island).getPistonLimit();
      } else if (type == Material.OBSERVER) {
         label = "Observer";
         used = this.islandService.getCachedObserverCount(island);
         limit = this.islandService.getCurrentLevelDef(island).getObserverLimit();
      } else if (type == Material.DISPENSER) {
         label = "Dispenser";
         used = this.islandService.getCachedDispenserCount(island);
         limit = this.islandService.getCurrentLevelDef(island).getDispenserLimit();
      } else if (type == Material.CACTUS) {
         label = "Kaktus";
         used = this.islandService.getCachedCactusCount(island);
         limit = this.islandService.getCurrentLevelDef(island).getCactusLimit();
      } else if (type == Material.KELP || type == Material.KELP_PLANT) {
         label = "Kelp";
         used = this.islandService.getCachedKelpCount(island);
         limit = this.islandService.getCurrentLevelDef(island).getKelpLimit();
      } else if (type == Material.BAMBOO) {
         label = "Bambus";
         used = this.islandService.getCachedBambooCount(island);
         limit = this.islandService.getCurrentLevelDef(island).getBambooLimit();
      } else {
         return;
      }

      this.showLimitHint(player, label, used, limit);
   }

   public void showArmorStandLimitHint(Player player, IslandData island) {
      if (player == null || island == null) return;
      this.showLimitHint(player, "Ruestungsstaender", this.islandService.getArmorStandCount(island), this.islandService.getCurrentLevelDef(island).getArmorStandLimit());
   }

   private void showLimitHint(Player player, String label, int used, int limit) {
      if (player == null || !player.isOnline()) return;
      double progress = limit <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, (double) used / (double) limit));
      BarColor color = progress >= 0.9 ? BarColor.RED : progress >= 0.65 ? BarColor.YELLOW : BarColor.GREEN;
      BossBar bar = this.limitHintBossBars.computeIfAbsent(player.getUniqueId(), id -> {
         BossBar created = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
         created.addPlayer(player);
         return created;
      });
      if (!bar.getPlayers().contains(player)) {
         bar.addPlayer(player);
      }
      bar.setTitle(ChatColor.AQUA + label + ChatColor.GRAY + ": " + ChatColor.WHITE + used + "/" + limit);
      bar.setColor(color);
      bar.setStyle(BarStyle.SOLID);
      bar.setProgress(progress <= 0.0 ? 0.01 : progress);
      bar.setVisible(true);

      Integer oldTask = this.limitHintHideTasks.remove(player.getUniqueId());
      if (oldTask != null) {
         Bukkit.getScheduler().cancelTask(oldTask);
      }
      int hideTask = Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, () -> this.hideLimitHint(player.getUniqueId()), LIMIT_HINT_DURATION_TICKS);
      this.limitHintHideTasks.put(player.getUniqueId(), hideTask);
   }

   private void hideLimitHint(UUID playerId) {
      this.limitHintHideTasks.remove(playerId);
      BossBar bar = this.limitHintBossBars.remove(playerId);
      if (bar != null) {
         bar.removeAll();
         bar.setVisible(false);
      }
   }

   private void fillWithPanes(Inventory inv) {
      ItemStack pane = this.named(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());

      for (int i = 0; i < inv.getSize(); i++) {
         inv.setItem(i, pane);
      }
   }

   public ItemStack named(Material material, String name, List<String> lore) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(this.cleanDisplayText(name));
      if (lore == null) {
         meta.setLore(null);
      } else {
         List<String> cleanLore = new ArrayList<>(lore.size());
         for (String line : lore) {
            cleanLore.add(this.cleanDisplayText(line));
         }
         meta.setLore(cleanLore);
      }
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack namedMaterialLocalized(Material material, NamedTextColor color, List<String> lore) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(Component.translatable(material.translationKey()).color(color));
      if (lore == null) {
         meta.lore(null);
      } else {
         List<Component> loreComponents = new ArrayList<>(lore.size());
         for (String line : lore) {
            loreComponents.add(LegacyComponentSerializer.legacySection().deserialize(this.cleanDisplayText(line)));
         }
         meta.lore(loreComponents);
      }
      item.setItemMeta(meta);
      return item;
   }

   private String cleanDisplayText(String text) {
      if (text == null || text.isEmpty()) {
         return text;
      }
      return text
         .replace("Ã¤", "\u00e4")
         .replace("Ã¶", "\u00f6")
         .replace("Ã¼", "\u00fc")
         .replace("Ã„", "\u00c4")
         .replace("Ã–", "\u00d6")
         .replace("Ãœ", "\u00dc")
         .replace("ÃŸ", "\u00df")
         .replace("ÃƒÂ¤", "\u00e4")
         .replace("ÃƒÂ¶", "\u00f6")
         .replace("ÃƒÂ¼", "\u00fc")
         .replace("Ãƒâ€ž", "\u00c4")
         .replace("Ãƒâ€“", "\u00d6")
         .replace("ÃƒÅ“", "\u00dc")
         .replace("ÃƒÅ¸", "\u00df");
   }

   private String sanitizeLegacyText(String text) {
      return cleanDisplayText(text);
   }

   static {
      DE_MATERIAL_NAMES.put(Material.COBBLESTONE, "Bruchstein");
      DE_MATERIAL_NAMES.put(Material.OAK_LOG, "Eichenstamm");
      DE_MATERIAL_NAMES.put(Material.IRON_INGOT, "Eisenbarren");
      DE_MATERIAL_NAMES.put(Material.GOLD_INGOT, "Goldbarren");
      DE_MATERIAL_NAMES.put(Material.REDSTONE, "Redstone");
      DE_MATERIAL_NAMES.put(Material.DIAMOND, "Diamant");
      DE_MATERIAL_NAMES.put(Material.REDSTONE_BLOCK, "Redstoneblock");
      DE_MATERIAL_NAMES.put(Material.OBSERVER, "Beobachter");
      DE_MATERIAL_NAMES.put(Material.RAW_IRON, "Roheisen");
      DE_MATERIAL_NAMES.put(Material.RAW_GOLD, "Rohgold");
      DE_BIOME_NAMES.put(Biome.PLAINS, "Ebene");
      DE_BIOME_NAMES.put(Biome.FOREST, "Wald");
      DE_BIOME_NAMES.put(Biome.BIRCH_FOREST, "Birkenwald");
      DE_BIOME_NAMES.put(Biome.DARK_FOREST, "Dunkelwald");
      DE_BIOME_NAMES.put(Biome.JUNGLE, "Dschungel");
      DE_BIOME_NAMES.put(Biome.SPARSE_JUNGLE, "Lichter Dschungel");
      DE_BIOME_NAMES.put(Biome.BAMBOO_JUNGLE, "Bambusdschungel");
      DE_BIOME_NAMES.put(Biome.TAIGA, "Taiga");
      DE_BIOME_NAMES.put(Biome.SNOWY_PLAINS, "Schneebene");
      DE_BIOME_NAMES.put(Biome.ICE_SPIKES, "Eiszapfen");
      DE_BIOME_NAMES.put(Biome.DESERT, "W\u00fcste");
      DE_BIOME_NAMES.put(Biome.SAVANNA, "Savanne");
      DE_BIOME_NAMES.put(Biome.SAVANNA_PLATEAU, "Savannenplateau");
      DE_BIOME_NAMES.put(Biome.BADLANDS, "Badlands");
      DE_BIOME_NAMES.put(Biome.WOODED_BADLANDS, "Bewaldete Badlands");
      DE_BIOME_NAMES.put(Biome.MUSHROOM_FIELDS, "Pilzinsel");
      DE_BIOME_NAMES.put(Biome.SWAMP, "Sumpf");
      DE_BIOME_NAMES.put(Biome.MANGROVE_SWAMP, "Mangrovensumpf");
      DE_BIOME_NAMES.put(Biome.MEADOW, "Blumenwiese");
      DE_BIOME_NAMES.put(Biome.GROVE, "Schneehain");
      DE_BIOME_NAMES.put(Biome.CHERRY_GROVE, "Kirschhain");
      DE_BIOME_NAMES.put(Biome.WINDSWEPT_HILLS, "Windige H\u00fcgel");
      DE_BIOME_NAMES.put(Biome.WINDSWEPT_FOREST, "Windiger Wald");
      DE_BIOME_NAMES.put(Biome.STONY_PEAKS, "Steingipfel");
      DE_BIOME_NAMES.put(Biome.JAGGED_PEAKS, "Zackige Gipfel");
      BLOCK_VALUE_BLACKLIST.add(Material.BEDROCK);
      BLOCK_VALUE_BLACKLIST.add(Material.EXPERIENCE_BOTTLE);
      BLOCK_VALUE_BLACKLIST.add(Material.BARRIER);
      loadPrototypeBlockValues();
   }

   public static record BiomeInventoryHolder(UUID islandOwner, int page, int relChunkX, int relChunkZ, int returnPage) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static record BlockValueInventoryHolder(UUID islandOwner, int page) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static record UpgradeProgressInventoryHolder(UUID islandOwner, int page) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static record ChunkMapInventoryHolder(UUID islandOwner, int page, String mode) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static record ChunkSettingsInventoryHolder(UUID islandOwner) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static enum ChunkMapMode {
      LOCAL,
      ALL;

      public static CoreService.ChunkMapMode from(String raw) {
         if (raw == null || raw.isBlank()) {
            return LOCAL;
         } else {
            try {
               return valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var2) {
               return LOCAL;
            }
         }
      }
   }

   public static enum CoreDisplayMode {
      ALL,
      TITLE_ONLY,
      ISLAND_INFO_ONLY,
      UPGRADE_ONLY,
      OFF;

      public static CoreService.CoreDisplayMode from(String raw) {
         if (raw != null && !raw.isBlank()) {
            try {
               return valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var2) {
               return ALL;
            }
         } else {
            return ALL;
         }
      }

      public CoreService.CoreDisplayMode next() {
         CoreService.CoreDisplayMode[] values = values();
         return values[(this.ordinal() + 1) % values.length];
      }
   }

   public static record CoreInventoryHolder(UUID islandOwner, String coreWorld, int coreX, int coreY, int coreZ) implements InventoryHolder {
      public Location coreLocation() {
         if (this.coreWorld != null && !this.coreWorld.isBlank()) {
            World world = Bukkit.getWorld(this.coreWorld);
            return world == null ? null : new Location(world, (double)this.coreX, (double)this.coreY, (double)this.coreZ);
         } else {
            return null;
         }
      }

      public Inventory getInventory() {
         return null;
      }
   }

   public static record IslandBlocksInventoryHolder(UUID islandOwner, int page) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static record IslandInventoryHolder(UUID islandOwner) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static record IslandSettingsInventoryHolder(UUID islandOwner) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static enum ParcelModerationAction {
      KICK,
      BAN,
      UNBAN;

      public static CoreService.ParcelModerationAction from(String raw) {
         if (raw == null || raw.isBlank()) {
            return KICK;
         } else {
            try {
               return valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var2) {
               return KICK;
            }
         }
      }
   }

   public static record IslandShopInventoryHolder(UUID islandOwner, int relChunkX, int relChunkZ) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static record TimeModeShopInventoryHolder(UUID islandOwner, String backTarget) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static record IslandTrustMembersInventoryHolder(UUID islandOwner, String permission, int page, String filter) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static record IslandOwnersInventoryHolder(UUID islandOwner, int page, String filter) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static record IslandMasterMenuInventoryHolder(UUID islandOwner) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static record IslandMasterInviteInventoryHolder(UUID islandOwner, int page) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static record ParcelsInventoryHolder(UUID islandOwner) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static record ParcelInventoryHolder(UUID islandOwner, int relChunkX, int relChunkZ) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static record ParcelMembersInventoryHolder(UUID islandOwner, int relChunkX, int relChunkZ, String role, int page, String filter)
      implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static record ParcelModerationInventoryHolder(UUID islandOwner, int relChunkX, int relChunkZ, String action, int page) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static record ParcelVisitorSettingsInventoryHolder(UUID islandOwner, int relChunkX, int relChunkZ) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static record TeleportInventoryHolder(UUID viewer, int page, String filter) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   public static record VisitorSettingsInventoryHolder(UUID islandOwner) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }
}




