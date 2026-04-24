package de.mcbesser.skycity.command;

import de.mcbesser.skycity.SkyCityPlugin;
import de.mcbesser.skycity.model.IslandData;
import de.mcbesser.skycity.service.CoreService;
import de.mcbesser.skycity.service.IslandService;
import de.mcbesser.skycity.service.ParticlePreviewService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class IslandCommand implements CommandExecutor, TabCompleter {
    private final SkyCityPlugin plugin;
    private final IslandService islandService;
    private final CoreService coreService;
    private final ParticlePreviewService particlePreviewService;
    private final Map<UUID, Integer> generationStatusTasks = new HashMap<>();
    private final Map<UUID, Integer> initialTeleportTasks = new HashMap<>();
    private final Map<UUID, PendingSelfDowngradeConfirmation> pendingSelfDowngradeConfirmations = new HashMap<>();

    public IslandCommand(SkyCityPlugin plugin, IslandService islandService, CoreService coreService, ParticlePreviewService particlePreviewService) {
        this.plugin = plugin;
        this.islandService = islandService;
        this.coreService = coreService;
        this.particlePreviewService = particlePreviewService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler.");
            return true;
        }
        if ("warp".equalsIgnoreCase(command.getName())) {
            return handleWarpCommand(player, args);
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if ("confirm".equals(sub)) {
            handleConfirmCommand(player);
            return true;
        }
        if ("cancel".equals(sub)) {
            handleCancelConfirmCommand(player);
            return true;
        }
        if (islandService.isIslandCreationPending(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Deine Insel wird gerade vorbereitet. Bitte warte kurz.");
            return true;
        }
        IslandData personalIsland = islandService.getIsland(player.getUniqueId()).orElse(null);
        IslandData island = resolveCommandIsland(player, sub, personalIsland);
        if ("create".equals(sub)) {
            if (personalIsland != null) {
                player.sendMessage(ChatColor.YELLOW + "Du hast bereits eine eigene Insel oder bist auf einer Insel Master.");
                player.openInventory(coreService.createIslandMenu(player, personalIsland));
                return true;
            }
            String creationThrottleMessage = islandService.getIslandCreationThrottleMessage(player.getUniqueId());
            if (creationThrottleMessage != null) {
                player.sendMessage(ChatColor.YELLOW + creationThrottleMessage);
                return true;
            }
            String recreationWaitMessage = islandService.getRecreationQueueWaitMessage(player.getUniqueId());
            if (recreationWaitMessage != null) {
                player.sendMessage(ChatColor.YELLOW + recreationWaitMessage);
                return true;
            }
            player.teleport(islandService.getSpawnLocation());
            boolean queued = islandService.queueIslandCreation(player.getUniqueId(), null, created -> {
                islandService.ensureCentralSpawnAndCoreSafe(created);
                coreService.ensureCorePlaced(created);
                islandService.queuePregeneration(created);
                Bukkit.broadcastMessage(ChatColor.GOLD + "Willkommen " + ChatColor.YELLOW + player.getName()
                        + ChatColor.GOLD + " auf der neuen Insel" + ChatColor.YELLOW + " " + created.getGridX() + ":" + created.getGridZ()
                        + ChatColor.GOLD + "!");
                Player online = plugin.getServer().getPlayer(created.getOwner());
                if (online == null || !online.isOnline()) return;
                online.sendMessage(ChatColor.YELLOW + "Deine Insel wird vorbereitet (Startbereich).");
                startGenerationStatusMessages(online);
                startInitialTeleportWhenReady(online);
            });
            if (!queued) {
                player.sendMessage(ChatColor.RED + "Inselerstellung konnte nicht gestartet werden.");
                return true;
            }
            int pregenerationQueuePos = islandService.getIslandPregenerationQueuePosition(player.getUniqueId());
            if (pregenerationQueuePos > 1) {
                player.sendMessage(ChatColor.GOLD + "Inselerstellung gestartet. Startchunks sind bereit, Pregeneration Platz " + pregenerationQueuePos + ".");
            } else {
                player.sendMessage(ChatColor.GOLD + "Inselerstellung gestartet...");
            }
            return true;
        }
        if ("masteraccept".equals(sub)) {
            handleMasterCommands(player, island, new String[]{"masteraccept"});
            return true;
        }
        if ("islands".equals(sub) && island == null) {
            player.openInventory(coreService.createIslandOverviewMenu(player));
            return true;
        }
        if (island == null) {
            if (args.length == 0) {
                player.openInventory(coreService.createIslandMenu(player));
            } else {
                sendNoIslandHelp(player);
            }
            return true;
        }
        if (args.length == 0) {
            player.openInventory(coreService.createIslandMenu(player, island));
            return true;
        }
        switch (sub) {
            case "home" -> player.teleport(island.getIslandSpawn());
            case "setspawn" -> {
                island.setIslandSpawn(player.getLocation().clone());
                islandService.save();
                player.sendMessage(ChatColor.GREEN + "Inselspawn gesetzt.");
            }
            case "showchunks" -> {
                particlePreviewService.activate(player, 0);
                player.sendMessage(ChatColor.AQUA + "Chunkanzeige aktiviert.");
                coreService.sendCurrentChunkStatusWithUnlock(player, island);
            }
            case "islands" -> player.openInventory(coreService.createIslandOverviewMenu(player, island));
            case "hidechunks" -> {
                particlePreviewService.deactivate(player);
                player.sendMessage(ChatColor.YELLOW + "Chunkanzeige deaktiviert.");
            }
            case "chunkunlock" -> {
                if (islandService.hasBuildAccess(player.getUniqueId(), island)) {
                    int relX = islandService.relativeChunkX(island, player.getLocation().getChunk().getX());
                    int relZ = islandService.relativeChunkZ(island, player.getLocation().getChunk().getZ());
                    int displayX = islandService.displayChunkX(relX);
                    int displayZ = islandService.displayChunkZ(relZ);
                    IslandService.ChunkUnlockResult result = islandService.unlockChunk(island, player.getUniqueId(), relX, relZ);
                    switch (result) {
                        case SUCCESS -> player.sendMessage(ChatColor.GREEN + "Aktueller Chunk freigeschaltet: " + displayX + ":" + displayZ);
                        case ALREADY_UNLOCKED -> player.sendMessage(ChatColor.YELLOW + "Dieser Chunk ist bereits freigeschaltet.");
                        case NO_UNLOCKS_LEFT -> player.sendMessage(ChatColor.RED + "Chunk konnte nicht freigeschaltet werden (keine Unlocks frei).");
                        case NEEDS_NEIGHBOR_APPROVAL -> {
                            player.sendMessage(ChatColor.GOLD + "Grenz-Chunk: Anfrage an Nachbar gesendet.");
                            player.sendMessage(ChatColor.GRAY + "Risiko bei Verbindung: Fl\u00fcssigkeiten, Items und Mobs k\u00f6nnen \u00fcbertreten.");
                        }
                        case PENDING_NEIGHBOR_APPROVAL -> player.sendMessage(ChatColor.YELLOW + "Freigabe vom Nachbarn steht noch aus.");
                        default -> player.sendMessage(ChatColor.RED + "Chunk konnte nicht freigeschaltet werden.");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Keine Rechte auf dieser Insel.");
                }
                coreService.sendCurrentChunkStatusWithUnlock(player, island);
            }
            case "chunkapprove" -> {
                if (args.length < 4) {
                    player.sendMessage(ChatColor.RED + "Nutze /is chunkapprove <insel-owner-uuid> <relX> <relZ>");
                    return true;
                }
                UUID requesterOwner;
                int relX;
                int relZ;
                try {
                    requesterOwner = UUID.fromString(args[1]);
                    relX = Integer.parseInt(args[2]);
                    relZ = Integer.parseInt(args[3]);
                } catch (Exception ex) {
                    player.sendMessage(ChatColor.RED + "Ung\u00fcltige Parameter.");
                    return true;
                }
                IslandService.ChunkUnlockResult result = islandService.approveBorderChunkUnlock(player.getUniqueId(), requesterOwner, relX, relZ);
                switch (result) {
                    case SUCCESS -> player.sendMessage(ChatColor.GREEN + "Freigabe erteilt. Chunk wurde sofort freigeschaltet.");
                    case APPROVAL_RECORDED -> player.sendMessage(ChatColor.GREEN + "Freigabe gespeichert. Weitere Nachbar-Freigaben fehlen noch.");
                    case NO_PENDING_REQUEST -> player.sendMessage(ChatColor.YELLOW + "Keine offene Anfrage f\u00fcr diesen Chunk.");
                    case NOT_AUTHORIZED -> player.sendMessage(ChatColor.RED + "Du darfst diese Anfrage nicht freigeben.");
                    case ALREADY_UNLOCKED -> player.sendMessage(ChatColor.YELLOW + "Chunk ist bereits freigeschaltet.");
                    case NO_UNLOCKS_LEFT -> player.sendMessage(ChatColor.RED + "Anfrage ist ung\u00fcltig: keine freien Unlocks beim Anfrager.");
                    default -> player.sendMessage(ChatColor.RED + "Freigabe konnte nicht verarbeitet werden.");
                }
            }
            case "title" -> handleTitleCommand(player, island, args);
            case "masterinvite", "masteraccept", "masterleave" -> handleMasterCommands(player, island, args);
            case "owner" -> handleIslandOwnerRoleCommand(player, island, args);
            case "member", "unmember" -> handleTrustCommand(player, island, args, args[0].equalsIgnoreCase("member"));
            case "kick", "ban", "unban" -> handleIslandKickBan(player, island, args);
            case "pkick", "pban", "punban" -> handleParcelKickBan(player, island, args);
            case "plot" -> handlePlotRole(player, island, args);
            default -> player.sendMessage(ChatColor.RED + "Unbekannter Unterbefehl.");
        }
        return true;
    }

    private IslandData resolveCommandIsland(Player player, String sub, IslandData personalIsland) {
        if (player == null) return personalIsland;
        if (!usesCurrentIslandContext(sub)) return personalIsland;
        IslandData currentIsland = islandService.getIslandAt(player.getLocation());
        return currentIsland != null ? currentIsland : personalIsland;
    }

    private boolean usesCurrentIslandContext(String sub) {
        return switch (sub) {
            case "", "setspawn", "showchunks", "hidechunks", "chunkunlock", "title",
                 "masterinvite", "owner", "member", "unmember",
                 "kick", "ban", "unban", "pkick", "pban", "punban", "plot" -> true;
            default -> false;
        };
    }

    private void handleTitleCommand(Player player, IslandData island, String[] args) {
        if (!islandService.isIslandOwner(island, player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Nur Master oder Owner.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Aktueller Titel: " + ChatColor.GOLD + islandService.getIslandTitleDisplay(island));
            player.sendMessage(ChatColor.RED + "Nutze /is title <text> oder /is title clear");
            return;
        }
        if ("clear".equalsIgnoreCase(args[1])) {
            island.setTitle(null);
            islandService.save();
            player.sendMessage(ChatColor.GREEN + "Inseltitel zur\u00fcckgesetzt.");
            return;
        }
        String title = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (title.isBlank()) {
            player.sendMessage(ChatColor.RED + "Titel darf nicht leer sein.");
            return;
        }
        if (title.length() > 40) {
            player.sendMessage(ChatColor.RED + "Titel darf max. 40 Zeichen haben.");
            return;
        }
        if (islandService.isIslandLabelTaken(title, island.getOwner(), island.getTitle())) {
            player.sendMessage(ChatColor.RED + "Name ist bereits als Inselname oder Warp vergeben.");
            return;
        }
        island.setTitle(title);
        islandService.save();
        player.sendMessage(ChatColor.GREEN + "Inseltitel gesetzt: " + ChatColor.GOLD + title);
    }

    private boolean handleWarpCommand(Player player, String[] args) {
        if (args.length == 0) {
            List<IslandService.TeleportTarget> warps = islandService.getWarpTargetsFor(player.getUniqueId());
            if (warps.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "Es sind aktuell keine Warps verf\u00fcgbar.");
                return true;
            }
            player.sendMessage(ChatColor.GOLD + "Verf\u00fcgbare Warps:");
            for (IslandService.TeleportTarget target : warps) {
                String[] parts = target.displayName().split("\\s*\\|\\s*", 2);
                player.sendMessage(ChatColor.AQUA + "- " + parts[0]);
            }
            return true;
        }
        String requested = String.join(" ", args).trim();
        IslandService.TeleportTarget target = islandService.findWarpTarget(player.getUniqueId(), requested);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Warp nicht gefunden.");
            return true;
        }
        player.teleport(target.location());
        player.sendMessage(ChatColor.GREEN + "Teleportiert zu " + ChatColor.GOLD + target.displayName());
        return true;
    }

    private void handleMasterCommands(Player player, IslandData island, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (islandService.isSpawnIsland(island)) {
            player.sendMessage(ChatColor.RED + "Am Spawn gibt es keine Master-Rechte.");
            return;
        }
        switch (sub) {
            case "masterinvite" -> {
                if (island == null) {
                    player.sendMessage(ChatColor.RED + "Du hast keine eigene Insel.");
                    sendNoIslandHelp(player);
                    return;
                }
                if (!islandService.isIslandMaster(island, player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Nur Master k\u00f6nnen Master einladen.");
                    return;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Nutze /is masterinvite <spieler>");
                    return;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target.getUniqueId().equals(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Du bist bereits Master.");
                    return;
                }
                boolean queued = islandService.queueMasterInvite(island, player.getUniqueId(), target.getUniqueId());
                if (!queued) {
                    player.sendMessage(ChatColor.YELLOW + "Keine \u00c4nderung.");
                    return;
                }
                player.sendMessage(ChatColor.GREEN + "Master-Einladung gesendet an " + (target.getName() == null ? "?" : target.getName()) + ".");
                Player online = Bukkit.getPlayer(target.getUniqueId());
                if (online != null) {
                    sendMasterInviteMessage(online, player.getName());
                }
            }
            case "masteraccept" -> {
                IslandData inviteIsland = islandService.getPendingMasterInviteIsland(player.getUniqueId());
                if (inviteIsland == null) {
                    player.sendMessage(ChatColor.RED + "Keine offene Master-Einladung.");
                    return;
                }
                boolean ok = islandService.acceptMasterInvite(player.getUniqueId());
                if (!ok) {
                    player.sendMessage(ChatColor.RED + "Einladung konnte nicht angenommen werden.");
                    return;
                }
                player.sendMessage(ChatColor.GREEN + "Du bist der Insel als Master beigetreten.");
                if (inviteIsland.getIslandSpawn() != null) player.teleport(inviteIsland.getIslandSpawn());
            }
            case "masterleave" -> {
                requestSelfDowngradeConfirmation(
                        player,
                        island,
                        PendingSelfDowngradeAction.MASTER_LEAVE,
                        null,
                        "Du wirst deinen Master-Rang auf " + islandService.getIslandTitleDisplay(island) + " verlieren."
                );
            }
            default -> {
            }
        }
    }

    private void handleIslandOwnerRoleCommand(Player player, IslandData island, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Nutze /is owner <add|remove> <spieler>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        switch (action) {
            case "add" -> {
                if (!islandService.canAddOwner(island, player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Nur Master oder Owner k\u00f6nnen Owner hinzuf\u00fcgen.");
                    return;
                }
                if (islandService.isPrimaryMaster(island, target.getUniqueId())) {
                    player.sendMessage(ChatColor.YELLOW + "Der prim\u00e4re Master kann nicht umgestuft werden.");
                    return;
                }
                boolean changed = islandService.grantOwnerRole(island, player.getUniqueId(), target.getUniqueId());
                if (!changed) {
                    player.sendMessage(ChatColor.YELLOW + "Keine \u00c4nderung.");
                    return;
                }
                String islandName = islandService.getIslandTitleDisplay(island);
                String targetName = target.getName() == null ? "?" : target.getName();
                player.sendMessage(ChatColor.GREEN + "Owner-Rechte an " + targetName + " auf " + islandName + " vergeben.");
                Player online = Bukkit.getPlayer(target.getUniqueId());
                if (online != null) {
                    online.sendMessage(ChatColor.GREEN + player.getName() + " hat dir auf " + islandName + " Owner-Rechte gegeben.");
                }
            }
            case "remove" -> {
                boolean selfRemove = target.getUniqueId().equals(player.getUniqueId());
                if (selfRemove && !island.getOwners().contains(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Du bist auf dieser Insel nicht als Owner eingetragen.");
                    return;
                }
                if (!selfRemove && !islandService.canRemoveOwner(island, player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Nur Master k\u00f6nnen Owner entfernen.");
                    return;
                }
                if (!selfRemove && !island.getOwners().contains(target.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Dieser Spieler ist auf dieser Insel kein Owner.");
                    return;
                }
                String islandName = islandService.getIslandTitleDisplay(island);
                String targetName = target.getName() == null ? "?" : target.getName();
                if (selfRemove) {
                    requestSelfDowngradeConfirmation(
                            player,
                            island,
                            PendingSelfDowngradeAction.OWNER_SELF_REMOVE,
                            null,
                            "Du wirst deine Owner-Rechte auf " + islandName + " verlieren."
                    );
                    return;
                }
                boolean changed = islandService.revokeOwnerRole(island, player.getUniqueId(), target.getUniqueId());
                if (!changed) {
                    player.sendMessage(ChatColor.RED + "Owner konnte auf dieser Insel nicht entfernt werden.");
                    return;
                }
                player.sendMessage(ChatColor.YELLOW + "Owner-Rechte von " + targetName + " auf " + islandName + " entfernt.");
                Player online = Bukkit.getPlayer(target.getUniqueId());
                if (online != null) {
                    online.sendMessage(ChatColor.RED + player.getName() + " hat dir auf " + islandName + " die Owner-Rechte entzogen.");
                }
            }
            default -> player.sendMessage(ChatColor.RED + "Nutze /is owner <add|remove> <spieler>");
        }
    }

    private void handleIslandKickBan(Player player, IslandData island, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Nutze /is kick|ban|unban <spieler>");
            return;
        }
        if (!islandService.isIslandOwner(island, player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Nur Master oder Owner.");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (islandService.isIslandOwner(island, target.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Master und Owner k\u00f6nnen nicht gebannt oder gekickt werden.");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "kick" -> {
                Player online = Bukkit.getPlayer(target.getUniqueId());
                if (online != null && islandService.kickFromIsland(island, online)) player.sendMessage(ChatColor.GREEN + "Spieler gekickt.");
                else player.sendMessage(ChatColor.RED + "Spieler nicht auf deiner Insel.");
            }
            case "ban" -> {
                islandService.setIslandBan(island, target.getUniqueId(), true);
                Player online = Bukkit.getPlayer(target.getUniqueId());
                if (online != null) islandService.kickFromIsland(island, online);
                player.sendMessage(ChatColor.GREEN + "Spieler von Insel gebannt.");
            }
            case "unban" -> {
                islandService.setIslandBan(island, target.getUniqueId(), false);
                player.sendMessage(ChatColor.YELLOW + "Spieler entbannt.");
            }
            default -> { }
        }
    }

    private void handleParcelKickBan(Player player, IslandData island, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Nutze /is pkick|pban|punban <spieler>");
            return;
        }
        int relX = islandService.relativeChunkX(island, player.getLocation().getChunk().getX());
        int relZ = islandService.relativeChunkZ(island, player.getLocation().getChunk().getZ());
        var parcel = islandService.getParcel(island, relX, relZ);
        if (parcel == null || !islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Du bist hier kein Plot-Owner.");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "pkick" -> {
                Player online = Bukkit.getPlayer(target.getUniqueId());
                if (online != null && islandService.kickFromParcel(island, parcel, online)) player.sendMessage(ChatColor.GREEN + "Spieler vom GS gekickt.");
                else player.sendMessage(ChatColor.RED + "Spieler nicht auf diesem GS.");
            }
            case "pban" -> {
                islandService.setParcelBan(island, parcel, player.getUniqueId(), target.getUniqueId(), true);
                Player online = Bukkit.getPlayer(target.getUniqueId());
                if (online != null) islandService.kickFromParcel(island, parcel, online);
                player.sendMessage(ChatColor.GREEN + "Spieler vom GS gebannt.");
            }
            case "punban" -> {
                islandService.setParcelBan(island, parcel, player.getUniqueId(), target.getUniqueId(), false);
                player.sendMessage(ChatColor.YELLOW + "Spieler vom GS entbannt.");
            }
            default -> { }
        }
    }

    private void handlePlotRole(Player player, IslandData island, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Nutze /is plot <wand|create|delete|list|buy|rent|owner|member> ...");
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if ("wand".equals(sub)) {
            player.getInventory().addItem(islandService.createPlotWand());
            player.sendMessage(ChatColor.GREEN + "Grundst\u00fccks-Stab erhalten.");
            return;
        }
        if ("create".equals(sub)) {
            if (!islandService.isIslandOwner(island, player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Nur Master oder Owner.");
                return;
            }
            Location pos1 = islandService.getPlotSelectionPos1(player.getUniqueId());
            Location pos2 = islandService.getPlotSelectionPos2(player.getUniqueId());
            if (pos1 == null || pos2 == null) {
                player.sendMessage(ChatColor.RED + "Setze zuerst Pos1 und Pos2 mit dem Grundst\u00fccks-Stab.");
                return;
            }
            var created = islandService.createParcelCuboidFromSelection(island, player.getUniqueId(), pos1, pos2, player.getLocation());
            if (created == null) {
                player.sendMessage(ChatColor.RED + "Grundst\u00fcck konnte nicht erstellt werden.");
                player.sendMessage(ChatColor.GRAY + "Pr\u00fcfe: nur freigeschaltete Chunks, keine \u00dcberschneidung, gleiche Insel.");
                return;
            }
            islandService.clearPlotSelection(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Grundst\u00fcck erstellt: " + islandService.getParcelDisplayName(created));
            return;
        }
        if ("delete".equals(sub)) {
            if (islandService.deleteParcelAt(island, player.getUniqueId(), player.getLocation())) {
                player.sendMessage(ChatColor.GREEN + "Grundst\u00fcck gel\u00f6scht.");
            } else {
                player.sendMessage(ChatColor.RED + "Kein l\u00f6schbares Grundst\u00fcck an deiner Position.");
            }
            return;
        }
        if ("list".equals(sub)) {
            if (island.getParcels().isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "Keine Grundst\u00fccke vorhanden.");
                return;
            }
            player.sendMessage(ChatColor.GOLD + "Grundst\u00fccke (" + island.getParcels().size() + "):");
            for (var entry : island.getParcels().entrySet()) {
                var p = entry.getValue();
                player.sendMessage(ChatColor.AQUA + "- " + islandService.getParcelDisplayName(p) + ChatColor.DARK_GRAY + " (" + entry.getKey() + ")" + ChatColor.GRAY
                        + " [" + p.getMinX() + "," + p.getMinY() + "," + p.getMinZ()
                        + " -> " + p.getMaxX() + "," + p.getMaxY() + "," + p.getMaxZ() + "]");
            }
            return;
        }
        if ("buy".equals(sub) || "rent".equals(sub)) {
            IslandData targetIsland = islandService.getIslandAt(player.getLocation());
            if (targetIsland == null) {
                player.sendMessage(ChatColor.RED + "Du stehst auf keiner Insel.");
                return;
            }
            var parcel = islandService.getParcelAt(targetIsland, player.getLocation());
            if (parcel == null) {
                player.sendMessage(ChatColor.RED + "Du musst im gew\u00fcnschten Plot stehen.");
                return;
            }
            IslandService.ParcelMarketResult result = "buy".equals(sub)
                    ? islandService.buyParcel(targetIsland, parcel, player.getUniqueId())
                    : islandService.rentParcel(targetIsland, parcel, player.getUniqueId());
            switch (result) {
                case SUCCESS -> player.sendMessage(ChatColor.GREEN + ("buy".equals(sub)
                        ? "Plot gekauft: " + islandService.getParcelDisplayName(parcel)
                        : "Plot gemietet: " + islandService.getParcelDisplayName(parcel)));
                case NOT_AVAILABLE -> player.sendMessage(ChatColor.RED + ("buy".equals(sub) ? "Dieser Plot steht nicht zum Verkauf." : "Dieser Plot steht nicht zur Miete."));
                case NO_BUYER_ISLAND -> player.sendMessage(ChatColor.RED + "Du brauchst eine eigene Insel.");
                case NOT_ENOUGH_EXPERIENCE -> player.sendMessage(ChatColor.RED + "Nicht genug gespeicherte Erfahrung auf deiner Insel.");
                case NOT_ENOUGH_MONEY -> player.sendMessage(ChatColor.RED + "Nicht genug CraftTaler.");
                case VAULT_UNAVAILABLE -> player.sendMessage(ChatColor.RED + "CraftTaler sind aktuell nicht verf\u00fcgbar.");
                case ALREADY_RENTED -> player.sendMessage(ChatColor.RED + "Dieser Plot ist aktuell bereits vermietet.");
                case INVALID_CONFIGURATION -> player.sendMessage(ChatColor.RED + "Das Mietangebot ist noch nicht vollst\u00e4ndig konfiguriert.");
                case NOT_AUTHORIZED -> player.sendMessage(ChatColor.RED + "Master oder Owner dieser Insel k\u00f6nnen das nicht nutzen.");
                default -> player.sendMessage(ChatColor.RED + "Aktion konnte nicht ausgef\u00fchrt werden.");
            }
            return;
        }
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Nutze /is plot <owner|member|buy|rent> ...");
            return;
        }
        var parcel = islandService.getParcelAt(island, player.getLocation());
        if (parcel == null || !islandService.isParcelOwner(island, parcel, player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Du bist hier kein Plot-Owner.");
            return;
        }
        IslandService.ParcelRole role = switch (sub) {
            case "owner" -> IslandService.ParcelRole.OWNER;
            case "member" -> IslandService.ParcelRole.MEMBER;
            default -> null;
        };
        if (role == null) {
            player.sendMessage(ChatColor.RED + "Rolle muss owner oder member sein.");
            return;
        }
        Boolean add = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "add" -> true;
            case "remove" -> false;
            default -> null;
        };
        if (add == null) {
            player.sendMessage(ChatColor.RED + "Aktion muss add oder remove sein.");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
        boolean changed = add
                ? islandService.grantParcelRole(island, parcel, player.getUniqueId(), target.getUniqueId(), role)
                : islandService.revokeParcelRole(island, parcel, player.getUniqueId(), target.getUniqueId(), role);
        player.sendMessage(changed ? ChatColor.GREEN + "Plot-Recht aktualisiert." : ChatColor.YELLOW + "Keine \u00c4nderung.");
    }

    private void handleTrustCommand(Player player, IslandData island, String[] args, boolean grant) {
        if (!grant && args.length >= 2) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target.getUniqueId().equals(player.getUniqueId())) {
                IslandService.TrustPermission permission = IslandService.TrustPermission.ALL;
                if (args.length >= 3) {
                    try {
                        permission = IslandService.TrustPermission.valueOf(args[2].toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        player.sendMessage(ChatColor.RED + "Recht muss build, container, redstone oder all sein.");
                        return;
                    }
                }
                String permissionName = switch (permission) {
                    case BUILD -> "Bauen";
                    case CONTAINER -> "Kisten";
                    case REDSTONE -> "Redstone";
                    case ALL -> "alle Member-Rechte";
                };
                boolean hasPermission = switch (permission) {
                    case BUILD -> island.getMemberBuildAccess().contains(target.getUniqueId());
                    case CONTAINER -> island.getMemberContainerAccess().contains(target.getUniqueId());
                    case REDSTONE -> island.getMemberRedstoneAccess().contains(target.getUniqueId());
                    case ALL -> island.getMemberBuildAccess().contains(target.getUniqueId())
                            || island.getMemberContainerAccess().contains(target.getUniqueId())
                            || island.getMemberRedstoneAccess().contains(target.getUniqueId());
                };
                if (!hasPermission) {
                    player.sendMessage(ChatColor.YELLOW + "Du hast auf dieser Insel dieses Member-Recht nicht.");
                    return;
                }
                requestSelfDowngradeConfirmation(
                        player,
                        island,
                        PendingSelfDowngradeAction.MEMBER_SELF_REMOVE,
                        permission,
                        "Du wirst dir auf " + islandService.getIslandTitleDisplay(island) + " " + permissionName + " entziehen."
                );
                return;
            }
        }
        if (!islandService.isIslandOwner(island, player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Nur Master oder Owner.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Nutze /is " + (grant ? "member" : "unmember") + " <spieler> [build|container|redstone|all]");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Du bist bereits Master oder Owner.");
            return;
        }
        if (islandService.isPrimaryMaster(island, target.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Der prim\u00e4re Master kann nicht als Member eingetragen werden.");
            return;
        }
        IslandService.TrustPermission permission = IslandService.TrustPermission.ALL;
        if (args.length >= 3) {
            try {
                permission = IslandService.TrustPermission.valueOf(args[2].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                player.sendMessage(ChatColor.RED + "Recht muss build, container, redstone oder all sein.");
                return;
            }
        }
        boolean changed = grant
                ? islandService.grantMemberPermission(island, target.getUniqueId(), permission)
                : islandService.revokeMemberPermission(island, target.getUniqueId(), permission);
        if (!changed) {
            player.sendMessage(ChatColor.YELLOW + "Keine \u00c4nderung.");
            return;
        }
        String islandName = islandService.getIslandTitleDisplay(island);
        String targetName = target.getName() == null ? "?" : target.getName();
        String permissionName = switch (permission) {
            case BUILD -> "Bauen";
            case CONTAINER -> "Kisten";
            case REDSTONE -> "Redstone";
            case ALL -> "Vollzugriff";
        };
        player.sendMessage((grant ? ChatColor.GREEN : ChatColor.YELLOW)
                + permissionName + "-Recht "
                + (grant ? "an " : "von ")
                + targetName + " auf " + islandName + " "
                + (grant ? "vergeben." : "entfernt."));
        Player online = Bukkit.getPlayer(target.getUniqueId());
        if (online != null) {
            online.sendMessage((grant ? ChatColor.GREEN : ChatColor.RED)
                    + player.getName() + " hat dir auf " + islandName + " das Recht " + permissionName + " "
                    + (grant ? "gegeben." : "entzogen."));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if ("warp".equalsIgnoreCase(command.getName())) {
            if (args.length == 1 && sender instanceof Player player) {
                List<String> names = new ArrayList<>();
                for (IslandService.TeleportTarget target : islandService.getWarpTargetsFor(player.getUniqueId())) {
                    names.add(target.displayName().split("\\s*\\|\\s*", 2)[0]);
                }
                return names;
            }
            return List.of();
        }
        if (args.length == 1) {
            if (!(sender instanceof Player player)) return List.of("create", "home", "islands");
            List<String> suggestions = new ArrayList<>(List.of("create", "home", "islands", "setspawn", "showchunks", "hidechunks", "chunkunlock", "chunkapprove", "title", "confirm", "cancel", "masteraccept", "masterleave", "kick", "ban", "unban", "pkick", "pban", "punban", "plot"));
            IslandData island = resolveCommandIsland(player, "", islandService.getIsland(player.getUniqueId()).orElse(null));
            if (island != null) {
                if (islandService.canInviteMaster(island, player.getUniqueId())) suggestions.add("masterinvite");
                if (islandService.canAddOwner(island, player.getUniqueId()) || islandService.canRemoveOwner(island, player.getUniqueId())) suggestions.add("owner");
                if (islandService.canManageMembers(island, player.getUniqueId())) {
                    suggestions.add("member");
                    suggestions.add("unmember");
                }
            }
            return suggestions;
        }
        if (args.length == 2 && "title".equalsIgnoreCase(args[0])) return List.of("clear");
        if (args.length == 2 && "masterinvite".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) return List.of();
            IslandData island = resolveCommandIsland(player, args[0].toLowerCase(Locale.ROOT), islandService.getIsland(player.getUniqueId()).orElse(null));
            if (island == null || !islandService.canInviteMaster(island, player.getUniqueId())) return List.of();
            List<String> names = new ArrayList<>();
            for (Player online : plugin.getServer().getOnlinePlayers()) names.add(online.getName());
            return names;
        }
        if (args.length == 2 && ("member".equalsIgnoreCase(args[0]) || "unmember".equalsIgnoreCase(args[0]))) {
            if (!(sender instanceof Player player)) return List.of();
            IslandData island = resolveCommandIsland(player, args[0].toLowerCase(Locale.ROOT), islandService.getIsland(player.getUniqueId()).orElse(null));
            if (island == null || !islandService.canManageMembers(island, player.getUniqueId())) return List.of();
            List<String> names = new ArrayList<>();
            for (Player online : plugin.getServer().getOnlinePlayers()) names.add(online.getName());
            return names;
        }
        if (args.length == 2 && "plot".equalsIgnoreCase(args[0])) return List.of("wand", "create", "delete", "list", "buy", "rent", "owner", "member");
        if (args.length == 2 && "owner".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) return List.of();
            IslandData island = resolveCommandIsland(player, args[0].toLowerCase(Locale.ROOT), islandService.getIsland(player.getUniqueId()).orElse(null));
            if (island == null) return List.of();
            List<String> actions = new ArrayList<>();
            if (islandService.canAddOwner(island, player.getUniqueId())) actions.add("add");
            if (islandService.canRemoveOwner(island, player.getUniqueId())) actions.add("remove");
            return actions;
        }
        if (args.length == 3 && "plot".equalsIgnoreCase(args[0]) && ("owner".equalsIgnoreCase(args[1]) || "member".equalsIgnoreCase(args[1]))) return List.of("add", "remove");
        if (args.length == 2 && List.of("kick","ban","unban","pkick","pban","punban").contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> names = new ArrayList<>();
            for (Player online : plugin.getServer().getOnlinePlayers()) names.add(online.getName());
            return names;
        }
        if (args.length == 4 && "plot".equalsIgnoreCase(args[0]) && ("owner".equalsIgnoreCase(args[1]) || "member".equalsIgnoreCase(args[1])) ) {
            List<String> names = new ArrayList<>();
            for (Player online : plugin.getServer().getOnlinePlayers()) names.add(online.getName());
            return names;
        }
        if (args.length == 3 && "owner".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) return List.of();
            IslandData island = resolveCommandIsland(player, args[0].toLowerCase(Locale.ROOT), islandService.getIsland(player.getUniqueId()).orElse(null));
            if (island == null) return List.of();
            String action = args[1].toLowerCase(Locale.ROOT);
            if ("add".equals(action) && !islandService.canAddOwner(island, player.getUniqueId())) return List.of();
            if ("remove".equals(action) && !islandService.canRemoveOwner(island, player.getUniqueId())) return List.of();
            List<String> names = new ArrayList<>();
            for (Player online : plugin.getServer().getOnlinePlayers()) names.add(online.getName());
            return names;
        }
        if (args.length == 3 && ("member".equalsIgnoreCase(args[0]) || "unmember".equalsIgnoreCase(args[0]))) {
            if (!(sender instanceof Player player)) return List.of();
            IslandData island = resolveCommandIsland(player, args[0].toLowerCase(Locale.ROOT), islandService.getIsland(player.getUniqueId()).orElse(null));
            if (island == null || !islandService.canManageMembers(island, player.getUniqueId())) return List.of();
            return List.of("all", "build", "container", "redstone");
        }
        return List.of();
    }

    private void startGenerationStatusMessages(Player player) {
        UUID playerId = player.getUniqueId();
        stopGenerationStatusMessages(playerId);
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player live = plugin.getServer().getPlayer(playerId);
            if (live == null || !live.isOnline()) {
                stopGenerationStatusMessages(playerId);
                return;
            }
            IslandData own = islandService.getPrimaryIsland(playerId).orElse(null);
            if (own == null && !islandService.isIslandCreationPending(playerId)) {
                stopGenerationStatusMessages(playerId);
                return;
            }
            if (islandService.isIslandReady(playerId)) {
                live.sendMessage(ChatColor.GREEN + "Insel-Generierung abgeschlossen.");
                stopGenerationStatusMessages(playerId);
                return;
            }
            int pregenerationQueuePos = islandService.getIslandPregenerationQueuePosition(playerId);
            if (pregenerationQueuePos > 1) {
                live.sendMessage(ChatColor.YELLOW + "Deine Startchunks sind bereit. Weitere Generierung: Pregeneration Platz " + pregenerationQueuePos + ".");
                return;
            }
            int progress = islandService.getIslandPregenerationProgress(playerId);
            int total = islandService.getTotalIslandChunkCount();
            live.sendMessage(ChatColor.GOLD + "Deine Insel wird generiert: " + progress + "/" + total + " Chunks.");
        }, 20L, 60L);
        generationStatusTasks.put(playerId, taskId);
    }

    private void stopGenerationStatusMessages(UUID playerId) {
        Integer taskId = generationStatusTasks.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void startInitialTeleportWhenReady(Player player) {
        UUID playerId = player.getUniqueId();
        stopInitialTeleportTask(playerId);
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player live = plugin.getServer().getPlayer(playerId);
            if (live == null || !live.isOnline()) {
                stopInitialTeleportTask(playerId);
                return;
            }
            IslandData own = islandService.getPrimaryIsland(playerId).orElse(null);
            if (own == null) {
                stopInitialTeleportTask(playerId);
                stopGenerationStatusMessages(playerId);
                return;
            }
            if (!islandService.isInitialAreaGenerated(own)) {
                islandService.queuePregeneration(own);
                return;
            }
            islandService.ensureCentralSpawnAndCoreSafe(own);
            islandService.ensureTemplateAtLocation(own, own.getIslandSpawn());
            live.teleport(own.getIslandSpawn());
            live.sendMessage(ChatColor.GREEN + "Deine Insel ist bereit (Startbereich). Du wurdest teleportiert.");
            if (!islandService.isIslandReady(playerId)) {
                live.sendMessage(ChatColor.YELLOW + "Weitere Chunks werden jetzt im Hintergrund generiert.");
                startGenerationStatusMessages(live);
            } else {
                stopGenerationStatusMessages(playerId);
            }
            stopInitialTeleportTask(playerId);
        }, 2L, 2L);
        initialTeleportTasks.put(playerId, taskId);
    }

    private void stopInitialTeleportTask(UUID playerId) {
        Integer taskId = initialTeleportTasks.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void sendNoIslandHelp(Player player) {
        player.sendMessage(ChatColor.RED + "Du hast noch keine Insel und bist auf keiner Insel Member.");
        player.sendMessage(ChatColor.YELLOW + "Nutze " + ChatColor.AQUA + "/is create" + ChatColor.YELLOW + ", um direkt eine Insel zu erstellen.");
        player.sendMessage(ChatColor.GRAY + "Oder nutze " + ChatColor.AQUA + "/is islands" + ChatColor.GRAY + ", um freie Orte selbst zu w\u00e4hlen.");
        player.sendMessage(ChatColor.GRAY + "Das ist praktisch, wenn du bewusst neben jemandem wohnen m\u00f6chtest.");
        player.sendMessage(ChatColor.GRAY + "Mit " + ChatColor.AQUA + "/is" + ChatColor.GRAY + " \u00f6ffnest du trotzdem schon dein Anf\u00e4nger-Men\u00fc mit Teleport und Insel\u00fcbersicht.");
    }

    private void sendMasterInviteMessage(Player target, String inviterName) {
        target.sendMessage("");
        target.sendMessage(ChatColor.DARK_RED.toString() + ChatColor.BOLD + "ACHTUNG: MASTER-EINLADUNG ERHALTEN!");
        target.sendMessage(ChatColor.RED + inviterName + " hat dich eingeladen, Master seiner Insel zu werden.");
        target.sendMessage(ChatColor.YELLOW + "Annehmen: /is masteraccept oder im Men\u00fc unter Insel > Berechtigungen > Master-Rechte.");
        target.sendMessage(ChatColor.DARK_RED.toString() + ChatColor.BOLD + "WICHTIG: DU KANNST NUR AUF EINER INSEL MASTER SEIN!");
        target.sendMessage(ChatColor.RED + "Wenn du bereits Master einer anderen Insel bist, verl\u00e4sst du sie beim Annehmen.");
        target.sendMessage(ChatColor.RED + "Bleibt dort danach kein Master mehr \u00fcbrig, wird diese Insel gel\u00f6scht.");
        target.sendMessage("");
    }

    private void requestSelfDowngradeConfirmation(Player player, IslandData island, PendingSelfDowngradeAction action, IslandService.TrustPermission permission, String warning) {
        pendingSelfDowngradeConfirmations.put(
                player.getUniqueId(),
                new PendingSelfDowngradeConfirmation(island.getOwner(), action, permission, System.currentTimeMillis() + 15000L)
        );
        player.sendMessage(ChatColor.DARK_RED + "Best\u00e4tigung erforderlich.");
        player.sendMessage(ChatColor.RED + warning);
        player.sendMessage(ChatColor.YELLOW + "Best\u00e4tigen: /is confirm");
        player.sendMessage(ChatColor.GRAY + "Abbrechen: /is cancel");
    }

    private void handleConfirmCommand(Player player) {
        PendingSelfDowngradeConfirmation pending = pendingSelfDowngradeConfirmations.remove(player.getUniqueId());
        if (pending == null || pending.expiresAt() < System.currentTimeMillis()) {
            player.sendMessage(ChatColor.RED + "Keine offene Best\u00e4tigung.");
            return;
        }
        IslandData island = islandService.getIsland(pending.islandOwner()).orElse(null);
        if (island == null) {
            player.sendMessage(ChatColor.RED + "Die betroffene Insel wurde nicht mehr gefunden.");
            return;
        }
        switch (pending.action()) {
            case MASTER_LEAVE -> {
                if (islandService.leaveMasterRole(player.getUniqueId())) {
                    stopGenerationStatusMessages(player.getUniqueId());
                    stopInitialTeleportTask(player.getUniqueId());
                    player.sendMessage(ChatColor.YELLOW + "Du bist als Master von der Insel ausgetreten.");
                    player.teleport(islandService.getSpawnLocation());
                    sendNoIslandHelp(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Du bist auf keiner Insel als zus\u00e4tzlicher Master eingetragen oder bist nicht Master.");
                }
            }
            case OWNER_SELF_REMOVE -> {
                if (islandService.revokeOwnerRole(island, player.getUniqueId(), player.getUniqueId())) {
                    player.sendMessage(ChatColor.YELLOW + "Du hast dich auf " + islandService.getIslandTitleDisplay(island) + " als Owner ausgetragen.");
                } else {
                    player.sendMessage(ChatColor.RED + "Owner konnte auf dieser Insel nicht entfernt werden.");
                }
            }
            case MEMBER_SELF_REMOVE -> {
                IslandService.TrustPermission permission = pending.permission() == null ? IslandService.TrustPermission.ALL : pending.permission();
                boolean changed = islandService.revokeMemberPermission(island, player.getUniqueId(), permission);
                if (!changed) {
                    player.sendMessage(ChatColor.YELLOW + "Keine \u00c4nderung.");
                    return;
                }
                String permissionName = switch (permission) {
                    case BUILD -> "Bauen";
                    case CONTAINER -> "Kisten";
                    case REDSTONE -> "Redstone";
                    case ALL -> "alle Member-Rechte";
                };
                player.sendMessage(ChatColor.YELLOW + "Du hast dir auf " + islandService.getIslandTitleDisplay(island) + " " + permissionName + " entzogen.");
            }
        }
    }

    private void handleCancelConfirmCommand(Player player) {
        if (pendingSelfDowngradeConfirmations.remove(player.getUniqueId()) != null) {
            player.sendMessage(ChatColor.YELLOW + "Best\u00e4tigung abgebrochen.");
        } else {
            player.sendMessage(ChatColor.RED + "Keine offene Best\u00e4tigung.");
        }
    }

    private enum PendingSelfDowngradeAction {
        MASTER_LEAVE,
        OWNER_SELF_REMOVE,
        MEMBER_SELF_REMOVE
    }

    private record PendingSelfDowngradeConfirmation(UUID islandOwner, PendingSelfDowngradeAction action, IslandService.TrustPermission permission, long expiresAt) {}
}





