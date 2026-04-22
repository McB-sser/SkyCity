package de.mcbesser.skycity.service;

import de.mcbesser.skycity.SkyCityPlugin;
import de.mcbesser.skycity.model.IslandData;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Color;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ParticlePreviewService {
    private final SkyCityPlugin plugin;
    private final IslandService islandService;
    private final Set<UUID> activePreviews = new HashSet<>();
    private final Map<UUID, Long> lastPreviewChunk = new HashMap<>();
    private int taskId = -1;

    public ParticlePreviewService(SkyCityPlugin plugin, IslandService islandService) {
        this.plugin = plugin;
        this.islandService = islandService;
    }

    public void activate(Player player, int seconds) {
        activePreviews.add(player.getUniqueId());
        lastPreviewChunk.put(player.getUniqueId(), chunkKey(player.getLocation().getChunk().getX(), player.getLocation().getChunk().getZ()));
    }

    public boolean isActive(Player player) {
        return player != null && activePreviews.contains(player.getUniqueId());
    }

    public boolean toggle(Player player) {
        if (player == null) return false;
        if (isActive(player)) {
            deactivate(player);
            return false;
        }
        activate(player, 0);
        return true;
    }

    public void deactivate(Player player) {
        if (player == null) return;
        activePreviews.remove(player.getUniqueId());
        lastPreviewChunk.remove(player.getUniqueId());
    }

    public void startTask() {
        stopTask();
        taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 18L, 10L);
    }

    public void stopTask() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void tick() {
        Iterator<UUID> it = activePreviews.iterator();
        while (it.hasNext()) {
            UUID playerId = it.next();
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                lastPreviewChunk.remove(playerId);
                it.remove();
                continue;
            }
            IslandData island = islandService.getIsland(player.getUniqueId()).orElse(null);
            if (island == null) continue;
            IslandData islandAtPlayer = islandService.getIslandAt(player.getLocation());
            if (islandAtPlayer == null || !islandAtPlayer.getOwner().equals(island.getOwner())) {
                lastPreviewChunk.remove(player.getUniqueId());
                continue;
            }
            checkAndSendChunkStatusOnChange(player);
            draw(player, island);
        }
    }

    private void draw(Player player, IslandData island) {
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        int relX = islandService.relativeChunkX(island, chunkX);
        int relZ = islandService.relativeChunkZ(island, chunkZ);
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        double y = player.getLocation().getY() + 1.0;
        for (int i = 0; i <= 16; i++) {
            player.spawnParticle(Particle.END_ROD, baseX + i, y, baseZ, 1, 0, 0, 0, 0);
            player.spawnParticle(Particle.END_ROD, baseX + i, y, baseZ + 16, 1, 0, 0, 0, 0);
            player.spawnParticle(Particle.END_ROD, baseX, y, baseZ + i, 1, 0, 0, 0, 0);
            player.spawnParticle(Particle.END_ROD, baseX + 16, y, baseZ + i, 1, 0, 0, 0, 0);
        }
        Particle.DustOptions dust = new Particle.DustOptions(
                islandService.isChunkUnlocked(island, player.getLocation()) ? Color.LIME : Color.RED, 1.3f);

        // Additional colored border highlight (green = unlocked, red = locked)
        for (int i = 0; i <= 16; i += 2) {
            player.spawnParticle(Particle.DUST, baseX + i, y + 0.05, baseZ, 1, 0, 0, 0, 0, dust);
            player.spawnParticle(Particle.DUST, baseX + i, y + 0.05, baseZ + 16, 1, 0, 0, 0, 0, dust);
            player.spawnParticle(Particle.DUST, baseX, y + 0.05, baseZ + i, 1, 0, 0, 0, 0, dust);
            player.spawnParticle(Particle.DUST, baseX + 16, y + 0.05, baseZ + i, 1, 0, 0, 0, 0, dust);
        }
        drawIslandBoundaryMarkers(player, baseX, baseZ, y, relX, relZ);

    }

    private void drawIslandBoundaryMarkers(Player player, int baseX, int baseZ, double y, int relX, int relZ) {
        Particle.DustOptions orange = new Particle.DustOptions(Color.ORANGE, 1.5f);
        double yUpper = y + 0.8;
        double yLower = y - 0.8;
        for (int i = 0; i <= 16; i += 2) {
            if (relZ == 0) {
                player.spawnParticle(Particle.DUST, baseX + i, yUpper, baseZ, 1, 0, 0, 0, 0, orange);
                player.spawnParticle(Particle.DUST, baseX + i, yLower, baseZ, 1, 0, 0, 0, 0, orange);
            }
            if (relZ == 63) {
                player.spawnParticle(Particle.DUST, baseX + i, yUpper, baseZ + 16, 1, 0, 0, 0, 0, orange);
                player.spawnParticle(Particle.DUST, baseX + i, yLower, baseZ + 16, 1, 0, 0, 0, 0, orange);
            }
            if (relX == 0) {
                player.spawnParticle(Particle.DUST, baseX, yUpper, baseZ + i, 1, 0, 0, 0, 0, orange);
                player.spawnParticle(Particle.DUST, baseX, yLower, baseZ + i, 1, 0, 0, 0, 0, orange);
            }
            if (relX == 63) {
                player.spawnParticle(Particle.DUST, baseX + 16, yUpper, baseZ + i, 1, 0, 0, 0, 0, orange);
                player.spawnParticle(Particle.DUST, baseX + 16, yLower, baseZ + i, 1, 0, 0, 0, 0, orange);
            }
        }
    }

    private void checkAndSendChunkStatusOnChange(Player player) {
        if (player == null) return;
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        long current = chunkKey(chunkX, chunkZ);
        long previous = lastPreviewChunk.getOrDefault(player.getUniqueId(), Long.MIN_VALUE);
        if (previous == current) return;
        lastPreviewChunk.put(player.getUniqueId(), current);

        IslandData islandAtLocation = islandService.getIslandAt(player.getLocation());
        if (islandAtLocation == null) return;
        int relX = islandService.relativeChunkX(islandAtLocation, chunkX);
        int relZ = islandService.relativeChunkZ(islandAtLocation, chunkZ);
        int displayX = islandService.displayChunkX(relX);
        int displayZ = islandService.displayChunkZ(relZ);
        boolean unlocked = islandService.isChunkUnlocked(islandAtLocation, relX, relZ);
        player.sendMessage(ChatColor.AQUA + "Aktueller Chunk: " + ChatColor.WHITE + displayX + ":" + displayZ
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.AQUA + "Status: " + (unlocked ? ChatColor.GREEN + "freigeschaltet" : ChatColor.RED + "gesperrt"));
        TextComponent actions = new TextComponent("");
        if (!unlocked) {
            TextComponent unlock = new TextComponent(ChatColor.GOLD + "[Chunk freischalten]");
            unlock.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/is chunkunlock"));
            unlock.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ChatColor.YELLOW + "Klick: aktuellen Chunk freischalten").create()));
            actions.addExtra(unlock);
            actions.addExtra(new TextComponent(" "));
        }
        TextComponent disable = new TextComponent(ChatColor.RED + "[Anzeige aus]");
        disable.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/is hidechunks"));
        disable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.YELLOW + "Klick: Chunkanzeige deaktivieren").create()));
        actions.addExtra(disable);
        player.spigot().sendMessage(actions);
    }

    private long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }
}



