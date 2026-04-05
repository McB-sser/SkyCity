package de.mcbesser.skycity.placeholder;

import de.mcbesser.skycity.SkyCityPlugin;
import de.mcbesser.skycity.model.IslandData;
import de.mcbesser.skycity.service.IslandService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class SkyCityPlaceholderExpansion extends PlaceholderExpansion {

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
        String key = params.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "island_title" -> island == null ? "Keine Insel" : islandService.getIslandTitleDisplay(island);
            case "island_viewer_role" -> island == null ? "&7-" : roleLabel(island, player.getUniqueId());
            case "island_master_header" -> roleHeader("Master", getMasterIds(island).size());
            case "island_owner_header" -> roleHeader("Owner", getOwnerIds(island).size());
            case "island_member_header" -> roleHeader("Member", getMemberIds(island).size());
            case "island_master_more" -> formatMore(getMasterIds(island), 4);
            case "island_owner_more" -> formatMore(getOwnerIds(island), 4);
            case "island_member_more" -> formatMore(getMemberIds(island), 6);
            default -> dynamicValue(island, key);
        };
    }

    private String dynamicValue(IslandData island, String key) {
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
        return null;
    }

    private IslandData resolveContextIsland(Player player) {
        if (player == null) return null;
        IslandData atLocation = islandService.getIslandAt(player.getLocation());
        if (atLocation != null) return atLocation;
        return islandService.getIsland(player.getUniqueId()).orElse(null);
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
