package de.mcbesser.skycity.placeholder;

import de.mcbesser.skycity.SkyCityPlugin;
import de.mcbesser.skycity.model.IslandData;
import de.mcbesser.skycity.model.ParcelData;
import de.mcbesser.skycity.service.IslandService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class SkyCityPlaceholderExpansion extends PlaceholderExpansion {
    private static final String PVP_TEAM_WOOL_METADATA = "skycity_pvp_team_wool";

    private final SkyCityPlugin plugin;
    private final IslandService islandService;

    public SkyCityPlaceholderExpansion(SkyCityPlugin plugin, IslandService islandService) {
        this.plugin = plugin;
        this.islandService = islandService;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "skycity";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Codex";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        IslandData island = resolveContextIsland(player);
        ParcelData parcel = resolveContextParcel(player, island);
        String key = params.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "island_title" -> island == null ? "Keine Insel" : islandService.getIslandTitleDisplay(island);
            case "island_viewer_role" -> island == null ? "&7-" : roleLabel(island, player.getUniqueId());
            case "pvp_team_square" -> parcelPvpTeamSquare(player, island, parcel);
            case "pvp_team_square_suffix" -> parcelPvpTeamSquareSuffix(player, island, parcel);
            case "island_master_header" -> roleHeader("Master", getMasterIds(island).size());
            case "island_owner_header" -> roleHeader("Owner", getOwnerIds(island).size());
            case "island_member_header" -> roleHeader("Member", getMemberIds(island).size());
            case "island_master_more" -> formatMore(getMasterIds(island), 4);
            case "island_owner_more" -> formatMore(getOwnerIds(island), 4);
            case "island_member_more" -> formatMore(getMemberIds(island), 6);
            case "pvp_zone_header" -> parcelPvpHeader(parcel);
            case "pvp_zone_name" -> parcel == null ? "&8-" : "&c" + islandService.getParcelDisplayName(parcel);
            case "pvp_zone_team" -> parcelPvpTeamLabel(player, island, parcel);
            case "pvp_zone_player_header" -> parcelPvpPlayerHeader(player, island, parcel);
            case "pvp_zone_more" -> formatMore(getParcelPvpPlayers(island, parcel), 8);
            default -> dynamicValue(player, island, parcel, key);
        };
    }

    private String dynamicValue(Player player, IslandData island, ParcelData parcel, String key) {
        if (key.startsWith("island_master_")) {
            Integer index = parseIndex(key, "island_master_");
            if (index != null) return formatRoleEntry(getMasterIds(island), index);
        }
        if (key.startsWith("island_owner_")) {
            Integer index = parseIndex(key, "island_owner_");
            if (index != null) return formatRoleEntry(getOwnerIds(island), index);
        }
        if (key.startsWith("island_member_")) {
            Integer index = parseIndex(key, "island_member_");
            if (index != null) return formatRoleEntry(getMemberIds(island), index);
        }
        if (key.startsWith("pvp_zone_player_")) {
            Integer index = parseIndex(key, "pvp_zone_player_");
            if (index != null) return formatParcelPvpEntry(island, parcel, index);
        }
        return null;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player instanceof Player online) {
            return onPlaceholderRequest(online, params);
        }
        return "";
    }

    private IslandData resolveContextIsland(Player player) {
        if (player == null) return null;
        IslandData atLocation = islandService.getIslandAt(player.getLocation());
        if (atLocation != null) return atLocation;
        return islandService.getIsland(player.getUniqueId()).orElse(null);
    }

    private ParcelData resolveContextParcel(Player player, IslandData island) {
        if (player == null || island == null) return null;
        ParcelData atLocation = islandService.getParcelAt(island, player.getLocation());
        if (atLocation != null && (atLocation.isPvpEnabled() || atLocation.isGamesEnabled())) return atLocation;
        return null;
    }

    private List<UUID> getMasterIds(IslandData island) {
        if (island == null) return List.of();
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        ids.add(island.getOwner());
        ids.addAll(island.getMasters());
        return sortedPlayers(ids);
    }

    private List<UUID> getOwnerIds(IslandData island) {
        if (island == null) return List.of();
        return sortedPlayers(new LinkedHashSet<>(island.getOwners()));
    }

    private List<UUID> getMemberIds(IslandData island) {
        if (island == null) return List.of();
        Set<UUID> ids = new LinkedHashSet<>();
        ids.addAll(island.getMemberBuildAccess());
        ids.addAll(island.getMemberContainerAccess());
        ids.addAll(island.getMemberRedstoneAccess());
        ids.remove(island.getOwner());
        ids.removeAll(island.getMasters());
        ids.removeAll(island.getOwners());
        return sortedPlayers(ids);
    }

    private List<UUID> sortedPlayers(Set<UUID> ids) {
        List<UUID> result = new ArrayList<>(ids);
        result.sort(
                Comparator.<UUID, Boolean>comparing(id -> !isOnline(id))
                        .thenComparing(this::playerName, String.CASE_INSENSITIVE_ORDER)
        );
        return result;
    }

    private String formatRoleEntry(List<UUID> ids, int oneBasedIndex) {
        if (ids.isEmpty() || oneBasedIndex <= 0 || oneBasedIndex > ids.size()) {
            return "&8-";
        }
        UUID id = ids.get(oneBasedIndex - 1);
        String name = playerName(id);
        return isOnline(id) ? "&aOn &f" + name : "&7Off &f" + name;
    }

    private String formatMore(List<UUID> ids, int visibleEntries) {
        if (ids.isEmpty() || ids.size() <= visibleEntries) {
            return "&8-";
        }
        return "&7+" + (ids.size() - visibleEntries) + " weitere";
    }

    private String roleHeader(String label, int count) {
        return "&6" + label + " &7(" + count + ")";
    }

    private String roleLabel(IslandData island, UUID playerId) {
        if (island == null || playerId == null) return "&7-";
        if (islandService.isIslandMaster(island, playerId)) return "&eMaster";
        if (islandService.isIslandOwner(island, playerId)) return "&eOwner";
        if (getMemberIds(island).contains(playerId)) return "&eMember";
        return "&7Besucher";
    }

    private String parcelPvpHeader(ParcelData parcel) {
        if (parcel == null) return "&8Zone";
        return parcel.isGamesEnabled() && !parcel.isPvpEnabled() ? "&b&lGames-Zone" : "&c&lPvP-Zone";
    }

    private String parcelPvpPlayerHeader(Player viewer, IslandData island, ParcelData parcel) {
        List<UUID> players = getParcelPvpPlayers(island, parcel);
        if (viewer == null || island == null || parcel == null || players.isEmpty()) return "&8Mitspieler";
        String color = parcel.isGamesEnabled() && !parcel.isPvpEnabled() ? "&b" : "&c";
        return color + "Mitspieler &7(" + players.size() + ")";
    }

    private String parcelPvpTeamLabel(Player viewer, IslandData island, ParcelData parcel) {
        if (viewer == null || island == null || parcel == null) return "&8-";
        Material wool = playerPvpTeamWool(viewer, island, parcel);
        if (wool == null) return "&7Kein Team";
        return woolColorCode(wool) + woolLabel(wool);
    }

    private String parcelPvpTeamSquare(Player viewer, IslandData island, ParcelData parcel) {
        if (viewer == null || island == null || parcel == null) return "";
        Material wool = playerPvpTeamWool(viewer, island, parcel);
        if (wool == null) return "";
        return woolColorCode(wool) + "\u25a0";
    }

    private String parcelPvpTeamSquareSuffix(Player viewer, IslandData island, ParcelData parcel) {
        if (viewer == null || island == null || parcel == null) return "";
        Material wool = playerPvpTeamWool(viewer, island, parcel);
        if (wool == null) return "";
        return woolColorCode(wool) + "\u25a0";
    }

    private String formatParcelPvpEntry(IslandData island, ParcelData parcel, int oneBasedIndex) {
        List<UUID> ids = getParcelPvpPlayers(island, parcel);
        if (ids.isEmpty() || oneBasedIndex <= 0 || oneBasedIndex > ids.size()) {
            return "&8-";
        }
        UUID id = ids.get(oneBasedIndex - 1);
        Player player = Bukkit.getPlayer(id);
        String name = playerName(id);
        Material teamWool = player == null ? null : playerPvpTeamWool(player, island, parcel);
        String square = teamWool == null ? "&7■ " : woolColorCode(teamWool) + "■ ";
        String teamLabel = teamWool == null ? "&7-" : woolColorCode(teamWool) + woolLabel(teamWool);
        return (isOnline(id) ? "&aOn " : "&7Off ") + square + "&f" + name + " &8| " + teamLabel;
    }

    private List<UUID> getParcelPvpPlayers(IslandData island, ParcelData parcel) {
        if (island == null || parcel == null || (!parcel.isPvpEnabled() && !parcel.isGamesEnabled())) return List.of();
        List<UUID> ids = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == null || !online.isOnline()) continue;
            if (islandService.getIslandAt(online.getLocation()) != island) continue;
            if (islandService.getParcelAt(island, online.getLocation()) != parcel) continue;
            if (parcel.isPvpEnabled() && !parcel.isGamesEnabled() && !islandService.hasParcelPvpConsent(online.getUniqueId(), island, parcel)) continue;
            ids.add(online.getUniqueId());
        }
        ids.sort(
                Comparator.<UUID, String>comparing(id -> {
                    Player player = Bukkit.getPlayer(id);
                    Material wool = player == null ? null : playerPvpTeamWool(player, island, parcel);
                    return wool == null ? "zzz" : wool.name();
                }).thenComparing(this::playerName, String.CASE_INSENSITIVE_ORDER)
        );
        return ids;
    }

    private Material playerPvpTeamWool(Player player, IslandData island, ParcelData parcel) {
        if (player == null || island == null || parcel == null) return null;
        Location location = player.getLocation();
        if (islandService.getIslandAt(location) != island) return null;
        if (islandService.getParcelAt(island, location) != parcel) return null;
        for (MetadataValue metadata : player.getMetadata(PVP_TEAM_WOOL_METADATA)) {
            if (metadata == null || metadata.getOwningPlugin() != plugin) continue;
            String woolName = metadata.asString();
            try {
                Material wool = Material.valueOf(woolName);
                if (isWool(wool)) return wool;
            } catch (IllegalArgumentException ignored) {
            }
        }
        Material feet = location.getBlock().getType();
        if (isWool(feet)) return feet;
        Material below = location.getBlock().getRelative(0, -1, 0).getType();
        if (isWool(below)) return below;
        Material belowTwo = location.getBlock().getRelative(0, -2, 0).getType();
        return isWool(belowTwo) ? belowTwo : null;
    }

    private boolean isWool(Material material) {
        return material != null && material.name().endsWith("_WOOL");
    }

    private String woolLabel(Material wool) {
        if (wool == null) return "-";
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
            default -> ChatColor.stripColor(wool.name().replace("_WOOL", "").toLowerCase(Locale.ROOT));
        };
    }

    private String woolColorCode(Material wool) {
        if (wool == null) return "&7";
        return switch (wool) {
            case WHITE_WOOL -> "&f";
            case ORANGE_WOOL -> "&6";
            case MAGENTA_WOOL -> "&d";
            case LIGHT_BLUE_WOOL -> "&b";
            case YELLOW_WOOL -> "&e";
            case LIME_WOOL -> "&a";
            case PINK_WOOL -> "&d";
            case GRAY_WOOL -> "&8";
            case LIGHT_GRAY_WOOL -> "&7";
            case CYAN_WOOL -> "&3";
            case PURPLE_WOOL -> "&5";
            case BLUE_WOOL -> "&9";
            case BROWN_WOOL -> "&6";
            case GREEN_WOOL -> "&2";
            case RED_WOOL -> "&c";
            case BLACK_WOOL -> "&0";
            default -> "&7";
        };
    }

    private Integer parseIndex(String key, String prefix) {
        try {
            return Integer.parseInt(key.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isOnline(UUID id) {
        Player player = Bukkit.getPlayer(id);
        return player != null && player.isOnline();
    }

    private String playerName(UUID id) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(id);
        String name = offlinePlayer.getName();
        return name == null || name.isBlank() ? id.toString().substring(0, 8) : name;
    }
}
