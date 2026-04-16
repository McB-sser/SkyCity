package de.mcbesser.skycity.service;

import de.mcbesser.skycity.SkyCityPlugin;
import de.mcbesser.skycity.model.IslandData;
import de.mcbesser.skycity.model.IslandLevelDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class CoreSidebar {
    private static final String OBJECTIVE_NAME = "skycity_core";
    private static final int TOTAL_LINES = 14;

    private final SkyCityPlugin plugin;
    private final IslandService islandService;
    private final CoreService coreService;
    private final ConcurrentMap<UUID, BoardState> activeBoards = new ConcurrentHashMap<>();
    private final Set<UUID> pendingRefreshes = ConcurrentHashMap.newKeySet();
    private BukkitTask refreshTask;

    public CoreSidebar(SkyCityPlugin plugin, IslandService islandService, CoreService coreService) {
        this.plugin = plugin;
        this.islandService = islandService;
        this.coreService = coreService;
    }

    public void start() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 1L, 20L);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        clearAll();
    }

    public void refresh(Player player) {
        if (player == null || !player.isOnline() || Bukkit.getScoreboardManager() == null) {
            return;
        }

        LookedCore lookedCore = resolveLookedCore(player);
        if (lookedCore == null) {
            clear(player);
            return;
        }

        BoardState boardState = resolveBoardState(player);
        Scoreboard scoreboard = boardState.scoreboard();
        if (player.getScoreboard() != scoreboard) {
            player.setScoreboard(scoreboard);
        }

        List<RenderedLine> nextLines = buildLines(player, lookedCore.island());
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, "dummy", buildTitle(lookedCore.island()));
        }
        if (objective.getDisplaySlot() != DisplaySlot.SIDEBAR) {
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        objective.setDisplayName(buildTitle(lookedCore.island()));

        List<String> renderedKeys = boardState.renderedKeys();
        for (int lineIndex = 0; lineIndex < nextLines.size(); lineIndex++) {
            RenderedLine line = nextLines.get(lineIndex);
            String entry = uniqueEntry(lineIndex);
            Team team = getOrCreateTeam(scoreboard, "coreline" + lineIndex, entry);
            if (!line.key().equals(renderedKeys.get(lineIndex))) {
                team.setPrefix(line.text());
                renderedKeys.set(lineIndex, line.key());
            }
            objective.getScore(entry).setScore(nextLines.size() - lineIndex);
        }

        for (int lineIndex = nextLines.size(); lineIndex < TOTAL_LINES; lineIndex++) {
            String entry = uniqueEntry(lineIndex);
            scoreboard.resetScores(entry);
            Team team = scoreboard.getTeam("coreline" + lineIndex);
            if (team != null) {
                team.setPrefix("");
            }
            renderedKeys.set(lineIndex, null);
        }
    }

    public void scheduleRefresh(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!pendingRefreshes.add(playerId)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingRefreshes.remove(playerId);
            if (player.isOnline()) {
                refresh(player);
            }
        });
    }

    public void clear(Player player) {
        if (player == null) {
            return;
        }
        pendingRefreshes.remove(player.getUniqueId());
        BoardState active = activeBoards.remove(player.getUniqueId());
        if (active == null) {
            return;
        }
        removeSidebar(active.scoreboard());
        if (player.getScoreboard() == active.scoreboard()) {
            Scoreboard previous = active.previousScoreboard();
            player.setScoreboard(previous != null ? previous : Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private void clearAll() {
        pendingRefreshes.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
        activeBoards.clear();
    }

    private void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    private LookedCore resolveLookedCore(Player player) {
        Block target = player.getTargetBlockExact(8);
        if (target == null || !coreService.isCoreBlock(target)) {
            return null;
        }
        IslandData island = islandService.getIslandAt(target.getLocation());
        if (island == null || island.getCoreLocation() == null || !sameBlock(island.getCoreLocation(), target.getLocation())) {
            return null;
        }
        return island == null ? null : new LookedCore(island);
    }

    private boolean sameBlock(org.bukkit.Location a, org.bukkit.Location b) {
        return a != null && b != null
            && a.getWorld() != null && b.getWorld() != null
            && a.getWorld().equals(b.getWorld())
            && a.getBlockX() == b.getBlockX()
            && a.getBlockY() == b.getBlockY()
            && a.getBlockZ() == b.getBlockZ();
    }

    private List<RenderedLine> buildLines(Player player, IslandData island) {
        List<RenderedLine> lines = new ArrayList<>(TOTAL_LINES);
        IslandLevelDefinition current = islandService.getCurrentLevelDef(island);
        double islandLevelScore = islandService.calculateIslandLevelValue(island);
        double reservedLevelScore = islandService.calculateReservedUpgradeLevelValue(island);
        lines.add(line("level_spacer", ChatColor.DARK_GRAY + " "));
        lines.add(line(
            "level:" + island.getLevel() + ":" + islandLevelScore + ":" + reservedLevelScore,
            ChatColor.YELLOW + "Level: " + ChatColor.WHITE + formatIslandLevelWithReserved(islandLevelScore, reservedLevelScore)
        ));
        lines.add(line(
            "xp:" + island.getStoredExperience(),
            ChatColor.YELLOW + "EXP: " + ChatColor.WHITE + island.getStoredExperience()
        ));
        lines.add(line(
            "chunks:" + island.getUnlockedChunks().size() + ":" + island.getAvailableChunkUnlocks(),
            ChatColor.YELLOW + "Chunks: " + ChatColor.WHITE + island.getUnlockedChunks().size()
                + ChatColor.GRAY + " (+" + island.getAvailableChunkUnlocks() + ")"
                + ChatColor.WHITE + "/" + islandService.getTotalIslandChunkCount()
        ));
        lines.add(line(
            "milestone:" + island.getLevel(),
            ChatColor.YELLOW + "Meilenst.: " + ChatColor.WHITE + Math.max(0, island.getLevel() - 1)
        ));
        lines.add(line("spacer", ChatColor.DARK_GRAY + " "));
        lines.add(line("limits_title", ChatColor.GOLD + "Insellimits"));
        lines.add(limitLine(
            "living",
            "Tier", islandService.getAnimalCount(island), current.getAnimalLimit(),
            "Vill", islandService.getVillagerCount(island), current.getVillagerLimit()
        ));
        lines.add(limitLine(
            "golem",
            "Golem", islandService.getGolemCount(island), current.getGolemLimit(),
            "Stand", islandService.getArmorStandCount(island), current.getArmorStandLimit()
        ));
        lines.add(limitLine(
            "vehicles",
            "Cart", islandService.getMinecartCount(island), current.getMinecartLimit(),
            "Boot", islandService.getBoatCount(island), current.getBoatLimit()
        ));
        lines.add(limitLine(
            "store",
            "Behl", islandService.getCachedInventoryBlockCount(island), islandService.getCurrentUpgradeLimit(island, IslandService.UpgradeBranch.CONTAINER),
            "Hop", islandService.getCachedHopperCount(island), current.getHopperLimit()
        ));
        lines.add(limitLine(
            "redstone",
            "Kolb", islandService.getCachedPistonCount(island), current.getPistonLimit(),
            "Obs", islandService.getCachedObserverCount(island), current.getObserverLimit()
        ));
        lines.add(limitLine(
            "farm1",
            "Disp", islandService.getCachedDispenserCount(island), current.getDispenserLimit(),
            "Kakt", islandService.getCachedCactusCount(island), current.getCactusLimit()
        ));
        lines.add(limitLine(
            "farm2",
            "Kelp", islandService.getCachedKelpCount(island), current.getKelpLimit(),
            "Bamb", islandService.getCachedBambooCount(island), current.getBambooLimit()
        ));
        return lines;
    }

    private RenderedLine limitLine(String keyPrefix, String leftLabel, int leftUsed, int leftLimit, String rightLabel, int rightUsed, int rightLimit) {
        String text = ChatColor.GREEN + leftLabel + " " + ChatColor.WHITE + leftUsed + "/" + leftLimit
            + ChatColor.GRAY + padRight("", Math.max(1, 14 - plainLength(leftLabel + " " + leftUsed + "/" + leftLimit)))
            + "| "
            + ChatColor.GREEN + rightLabel + " " + ChatColor.WHITE + rightUsed + "/" + rightLimit;
        return line(
            keyPrefix + ":" + leftUsed + ":" + leftLimit + ":" + rightUsed + ":" + rightLimit,
            trimLine(text)
        );
    }

    private String buildTitle(IslandData island) {
        String plain = ChatColor.stripColor(islandService.getIslandTitleDisplay(island) + " Core");
        if (plain.length() > 28) {
            plain = plain.substring(0, 28) + "...";
        }
        return ChatColor.GOLD + "" + ChatColor.BOLD + plain;
    }

    private String formatIslandLevelWithReserved(double total, double reserved) {
        String base = String.format(Locale.US, "%.2f", Math.max(0.0, total));
        if (reserved <= 0.0) {
            return base;
        }
        return base + ChatColor.GRAY + " (-" + String.format(Locale.US, "%.2f", Math.max(0.0, reserved)) + ChatColor.GRAY + ")";
    }

    private RenderedLine line(String key, String text) {
        return new RenderedLine(key, trimLine(text));
    }

    private String trimLine(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 64 ? text : text.substring(0, 64);
    }

    private String padRight(String text, int minLength) {
        if (text.length() >= minLength) {
            return text;
        }
        return text + " ".repeat(minLength - text.length());
    }

    private int plainLength(String text) {
        return text == null ? 0 : ChatColor.stripColor(text).length();
    }

    private BoardState resolveBoardState(Player player) {
        BoardState existing = activeBoards.get(player.getUniqueId());
        Scoreboard current = player.getScoreboard();
        if (existing != null) {
            if (current != existing.scoreboard()) {
                existing.updatePreviousScoreboard(current);
            }
            return existing;
        }
        BoardState next = new BoardState(current, Bukkit.getScoreboardManager().getNewScoreboard());
        activeBoards.put(player.getUniqueId(), next);
        return next;
    }

    private Team getOrCreateTeam(Scoreboard scoreboard, String name, String entry) {
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
        }
        if (!team.hasEntry(entry)) {
            for (String existing : List.copyOf(team.getEntries())) {
                team.removeEntry(existing);
            }
            team.addEntry(entry);
        }
        return team;
    }

    private void removeSidebar(Scoreboard scoreboard) {
        if (scoreboard == null) {
            return;
        }
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective != null) {
            objective.unregister();
        }
        for (int lineIndex = 0; lineIndex < TOTAL_LINES; lineIndex++) {
            Team team = scoreboard.getTeam("coreline" + lineIndex);
            if (team != null) {
                team.unregister();
            }
        }
    }

    private String uniqueEntry(int index) {
        return ChatColor.values()[index].toString();
    }

    private record LookedCore(IslandData island) {
    }

    private record RenderedLine(String key, String text) {
    }

    private static final class BoardState {
        private Scoreboard previousScoreboard;
        private final Scoreboard scoreboard;
        private final List<String> renderedKeys;

        private BoardState(Scoreboard previousScoreboard, Scoreboard scoreboard) {
            this.previousScoreboard = previousScoreboard;
            this.scoreboard = scoreboard;
            this.renderedKeys = new ArrayList<>(Collections.nCopies(TOTAL_LINES, null));
        }

        private Scoreboard previousScoreboard() {
            return previousScoreboard;
        }

        private void updatePreviousScoreboard(Scoreboard previousScoreboard) {
            if (previousScoreboard != null && previousScoreboard != scoreboard) {
                this.previousScoreboard = previousScoreboard;
            }
        }

        private Scoreboard scoreboard() {
            return scoreboard;
        }

        private List<String> renderedKeys() {
            return renderedKeys;
        }
    }
}
