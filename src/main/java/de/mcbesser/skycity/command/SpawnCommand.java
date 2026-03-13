package de.mcbesser.skycity.command;

import de.mcbesser.skycity.service.IslandService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {
    private final IslandService islandService;

    public SpawnCommand(IslandService islandService) {
        this.islandService = islandService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler.");
            return true;
        }
        player.teleport(islandService.getSpawnLocation());
        player.sendMessage(ChatColor.GREEN + "Zum Spawn teleportiert.");
        return true;
    }
}



