package de.mcbesser.skycity.command;

import de.mcbesser.skycity.SkyCityPlugin;
import de.mcbesser.skycity.model.IslandData;
import de.mcbesser.skycity.service.CoreService;
import de.mcbesser.skycity.service.IslandService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class IgnoreCommand implements CommandExecutor, TabCompleter {

    private final SkyCityPlugin plugin;
    private final IslandService islandService;
    private final CoreService coreService;

    public IgnoreCommand(SkyCityPlugin plugin, IslandService islandService, CoreService coreService) {
        this.plugin = plugin;
        this.islandService = islandService;
        this.coreService = coreService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur f\u00fcr Spieler.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Verwendung: /ignore <spieler> [chat|commands|ban|remove|unban]");
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
        if (target == null) {
            Player online = Bukkit.getPlayer(targetName);
            if (online != null) {
                target = online;
            } else {
                target = Bukkit.getOfflinePlayer(targetName);
            }
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Du kannst dich nicht selbst ignorieren.");
            return true;
        }
        
        if (target.isOp() && !player.isOp()) {
            player.sendMessage(ChatColor.RED + "Du kannst keine Teammitglieder ignorieren.");
            return true;
        }

        IslandData commandIsland = resolveIgnoreIsland(player);
        if (args.length == 1) {
            player.openInventory(coreService.createIgnoreOptionsMenu(player, target, commandIsland));
            return true;
        }

        String typeStr = args[1].toLowerCase(Locale.ROOT);
        switch (typeStr) {
            case "chat" -> {
                islandService.setIgnore(player.getUniqueId(), target.getUniqueId(), IslandService.IgnoreType.CHAT);
                player.sendMessage(ChatColor.GREEN + target.getName() + " wird nun ignoriert f\u00fcr: " + formatIgnoreTypes(player.getUniqueId(), target.getUniqueId()));
            }
            case "commands" -> {
                islandService.setIgnore(player.getUniqueId(), target.getUniqueId(), IslandService.IgnoreType.COMMANDS);
                player.sendMessage(ChatColor.GREEN + target.getName() + " wird nun ignoriert f\u00fcr: " + formatIgnoreTypes(player.getUniqueId(), target.getUniqueId()));
            }
            case "remove", "clear", "none" -> {
                islandService.removeIgnore(player.getUniqueId(), target.getUniqueId());
                player.sendMessage(ChatColor.GREEN + target.getName() + " wird nicht mehr ignoriert.");
            }
            case "ban" -> {
                if (commandIsland == null) {
                    player.sendMessage(ChatColor.RED + "Du hast aktuell keine Insel f\u00fcr einen Insel-Bann.");
                    return true;
                }
                if (!islandService.isIslandOwner(commandIsland, player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Nur Master oder Owner k\u00f6nnen Insel-Banns vergeben.");
                    return true;
                }
                islandService.setIslandBan(commandIsland, target.getUniqueId(), true);
                Player online = Bukkit.getPlayer(target.getUniqueId());
                if (online != null) {
                    islandService.kickFromIsland(commandIsland, online);
                }
                player.sendMessage(ChatColor.GREEN + target.getName() + " wurde nur von der Insel gebannt.");
            }
            case "unban" -> {
                if (commandIsland == null) {
                    player.sendMessage(ChatColor.RED + "Du hast aktuell keine Insel f\u00fcr einen Insel-Entbann.");
                    return true;
                }
                if (!islandService.isIslandOwner(commandIsland, player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Nur Master oder Owner k\u00f6nnen Insel-Banns verwalten.");
                    return true;
                }
                islandService.setIslandBan(commandIsland, target.getUniqueId(), false);
                player.sendMessage(ChatColor.YELLOW + target.getName() + " wurde von der Insel entbannt.");
            }
            default -> {
                player.openInventory(coreService.createIgnoreOptionsMenu(player, target, commandIsland));
            }
        }

        return true;
    }

    private IslandData resolveIgnoreIsland(Player player) {
        IslandData currentIsland = islandService.getIslandAt(player.getLocation());
        if (currentIsland != null && islandService.isIslandOwner(currentIsland, player.getUniqueId())) {
            return currentIsland;
        }
        return islandService.getIsland(player.getUniqueId()).orElse(null);
    }

    private String formatIgnoreTypes(UUID actorId, UUID targetId) {
        EnumSet<IslandService.IgnoreType> types = islandService.getIgnoreTypes(actorId, targetId);
        List<String> labels = new ArrayList<>();
        if (types.contains(IslandService.IgnoreType.CHAT)) {
            labels.add("CHAT");
        }
        if (types.contains(IslandService.IgnoreType.COMMANDS)) {
            labels.add("COMMANDS");
        }
        return labels.isEmpty() ? "KEINE" : String.join(", ", labels);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return names;
        }
        if (args.length == 2) {
            return List.of("chat", "commands", "ban", "unban", "remove");
        }
        return List.of();
    }
}
