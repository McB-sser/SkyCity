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
import java.util.List;
import java.util.Locale;

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
            player.sendMessage(ChatColor.RED + "Verwendung: /ignore <spieler> [chat|commands|all|ban|remove]");
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

        String typeStr = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "all";
        IslandService.IgnoreType type = null;
        boolean isBan = false;

        switch (typeStr) {
            case "chat" -> type = IslandService.IgnoreType.CHAT;
            case "commands" -> type = IslandService.IgnoreType.COMMANDS;
            case "ban" -> {
                type = IslandService.IgnoreType.ALL;
                isBan = true;
            }
            case "remove", "clear", "none" -> type = null;
            default -> type = IslandService.IgnoreType.ALL;
        }

        if (type == null) {
            islandService.removeIgnore(player.getUniqueId(), target.getUniqueId());
            player.sendMessage(ChatColor.GREEN + target.getName() + " wird nicht mehr ignoriert.");
            return true;
        }

        islandService.setIgnore(player.getUniqueId(), target.getUniqueId(), type);
        
        if (isBan) {
            IslandData island = islandService.getIsland(player.getUniqueId()).orElse(null);
            if (island != null) {
                islandService.setIslandBan(island, target.getUniqueId(), true);
                Player online = Bukkit.getPlayer(target.getUniqueId());
                if (online != null) islandService.kickFromIsland(island, online);
                player.sendMessage(ChatColor.GREEN + target.getName() + " wird auf allen Ebenen ignoriert und von deiner Insel gebannt.");
            } else {
                player.sendMessage(ChatColor.GREEN + target.getName() + " wird auf allen Ebenen ignoriert. (Du hast keine eigene Insel f\u00fcr einen Bann)");
            }
        } else {
            player.sendMessage(ChatColor.GREEN + target.getName() + " wird nun ignoriert f\u00fcr: " + type.name());
        }

        return true;
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
            return List.of("chat", "commands", "all", "ban", "remove");
        }
        return List.of();
    }
}
