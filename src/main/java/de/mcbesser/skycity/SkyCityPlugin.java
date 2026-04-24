package de.mcbesser.skycity;

import de.mcbesser.skycity.command.IslandCommand;
import de.mcbesser.skycity.command.SpawnCommand;
import de.mcbesser.skycity.listener.CraftingListener;
import de.mcbesser.skycity.listener.CoreMenuListener;
import de.mcbesser.skycity.listener.PlayerListener;
import de.mcbesser.skycity.listener.PlotWandListener;
import de.mcbesser.skycity.listener.ProtectionListener;
import de.mcbesser.skycity.placeholder.SkyCityPlaceholderExpansion;
import de.mcbesser.skycity.service.CoreSidebar;
import de.mcbesser.skycity.service.CoreService;
import de.mcbesser.skycity.service.IslandService;
import de.mcbesser.skycity.service.ParticlePreviewService;
import de.mcbesser.skycity.service.SkyWorldService;
import de.mcbesser.skycity.world.VoidBarrierChunkGenerator;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

public class SkyCityPlugin extends JavaPlugin {
    private NamespacedKey coreItemKey;
    private NamespacedKey coreBlockKey;
    private NamespacedKey coreDisplayModeKey;
    private SkyWorldService skyWorldService;
    private IslandService islandService;
    private CoreService coreService;
    private CoreSidebar coreSidebar;
    private ParticlePreviewService particlePreviewService;
    private SkyCityPlaceholderExpansion placeholderExpansion;
    private PlayerListener playerListener;
    private boolean runtimeInitialized;
    private int placeholderRetryTaskId = -1;
    private int vaultRetryTaskId = -1;
    private Economy vaultEconomy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        coreItemKey = new NamespacedKey(this, "core_item");
        coreBlockKey = new NamespacedKey(this, "core_block");
        coreDisplayModeKey = new NamespacedKey(this, "core_display_mode");

        skyWorldService = new SkyWorldService(this);
        islandService = new IslandService(this, skyWorldService);
        coreService = new CoreService(this, islandService);
        coreSidebar = new CoreSidebar(this, islandService, coreService);
        particlePreviewService = new ParticlePreviewService(this, islandService);
        registerPlaceholderExpansionIfAvailable();
        registerVaultEconomyIfAvailable();
        placeholderRetryTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (placeholderExpansion != null && placeholderExpansion.isRegistered()) {
                Bukkit.getScheduler().cancelTask(placeholderRetryTaskId);
                placeholderRetryTaskId = -1;
                return;
            }
            registerPlaceholderExpansionIfAvailable();
        }, 23L, 40L);
        vaultRetryTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (vaultEconomy != null) {
                Bukkit.getScheduler().cancelTask(vaultRetryTaskId);
                vaultRetryTaskId = -1;
                return;
            }
            registerVaultEconomyIfAvailable();
        }, 37L, 40L);

        playerListener = new PlayerListener(this, islandService, skyWorldService, coreService);
        Bukkit.getPluginManager().registerEvents(playerListener, this);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this, islandService, coreService, skyWorldService, playerListener), this);
        Bukkit.getPluginManager().registerEvents(new PlotWandListener(this, islandService, skyWorldService, coreService), this);
        Bukkit.getPluginManager().registerEvents(new CoreMenuListener(islandService, coreService, particlePreviewService, playerListener), this);
        Bukkit.getPluginManager().registerEvents(new CraftingListener(coreService), this);
        Bukkit.getPluginManager().registerEvents(new BootstrapListener(), this);

        getCommand("spawn").setExecutor(new SpawnCommand(islandService));
        IslandCommand islandCommand = new IslandCommand(this, islandService, coreService, particlePreviewService);
        getCommand("is").setExecutor(islandCommand);
        getCommand("is").setTabCompleter(islandCommand);
        getCommand("warp").setExecutor(islandCommand);
        getCommand("warp").setTabCompleter(islandCommand);
        getCommand("accept").setExecutor(islandCommand);
        getCommand("cancel").setExecutor(islandCommand);

        completeRuntimeInitializationIfReady();
        Bukkit.getScheduler().runTask(this, this::completeRuntimeInitializationIfReady);
    }

    @Override
    public void onDisable() {
        if (coreService != null) {
            coreService.stopDisplayTask();
        }
        if (coreSidebar != null) {
            coreSidebar.stop();
        }
        if (particlePreviewService != null) {
            particlePreviewService.stopTask();
        }
        if (placeholderRetryTaskId != -1) {
            Bukkit.getScheduler().cancelTask(placeholderRetryTaskId);
            placeholderRetryTaskId = -1;
        }
        if (vaultRetryTaskId != -1) {
            Bukkit.getScheduler().cancelTask(vaultRetryTaskId);
            vaultRetryTaskId = -1;
        }
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }
        if (playerListener != null) {
            playerListener.resetAllParcelCtfStates();
        }
        if (islandService != null) {
            islandService.stopIslandCreationTask();
            islandService.stopPregenerationTask();
            islandService.shutdown();
        }
    }

    public NamespacedKey getCoreItemKey() { return coreItemKey; }
    public NamespacedKey getCoreBlockKey() { return coreBlockKey; }
    public NamespacedKey getCoreDisplayModeKey() { return coreDisplayModeKey; }
    public CoreSidebar getCoreSidebar() { return coreSidebar; }
    public boolean hasVaultEconomy() { return vaultEconomy != null; }

    public double getVaultBalance(UUID playerId) {
        if (vaultEconomy == null || playerId == null) {
            return 0.0D;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return vaultEconomy.getBalance(player);
    }

    public boolean withdrawVault(UUID playerId, double amount) {
        if (vaultEconomy == null || playerId == null || amount < 0.0D) {
            return false;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return vaultEconomy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public void depositVault(UUID playerId, double amount) {
        if (vaultEconomy == null || playerId == null || amount <= 0.0D) {
            return;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        vaultEconomy.depositPlayer(player, amount);
    }

    public String getVaultCurrencyName(double amount) {
        if (vaultEconomy == null) {
            return "CraftTaler";
        }
        String name = amount == 1.0D ? vaultEconomy.currencyNameSingular() : vaultEconomy.currencyNamePlural();
        return name == null || name.isBlank() ? "CraftTaler" : name;
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (SkyWorldService.WORLD_NAME.equalsIgnoreCase(worldName)) {
            return new VoidBarrierChunkGenerator();
        }
        return null;
    }

    private void completeRuntimeInitializationIfReady() {
        if (runtimeInitialized) {
            return;
        }
        if (!skyWorldService.initializeWorld() || skyWorldService.getWorld() == null) {
            return;
        }
        islandService.load();
        islandService.startIslandCreationTask();
        islandService.startPregenerationTask();
        islandService.ensureSpawnPlotAndSpawnPlatform();
        coreService.registerRecipe();
        NamespacedKey coreRecipeKey = coreService.getRecipeKey();
        for (var player : Bukkit.getOnlinePlayers()) {
            player.discoverRecipe(coreRecipeKey);
        }
        if (playerListener != null) {
            playerListener.resetAllParcelCtfStates();
        }
        coreService.startDisplayTask();
        coreSidebar.start();
        particlePreviewService.startTask();
        runtimeInitialized = true;
        getLogger().info("SkyCity Runtime initialisiert.");
    }

    private void registerPlaceholderExpansionIfAvailable() {
        if (placeholderExpansion != null && placeholderExpansion.isRegistered()) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        if (placeholderExpansion == null) {
            placeholderExpansion = new SkyCityPlaceholderExpansion(this, islandService);
        }
        if (!placeholderExpansion.isRegistered()) {
            placeholderExpansion.register();
        }
    }

    private void registerVaultEconomyIfAvailable() {
        if (vaultEconomy != null) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            return;
        }
        vaultEconomy = registration.getProvider();
    }

    private final class BootstrapListener implements Listener {
        @EventHandler
        public void onWorldLoad(WorldLoadEvent event) {
            if (SkyWorldService.WORLD_NAME.equalsIgnoreCase(event.getWorld().getName())) {
                completeRuntimeInitializationIfReady();
            }
        }
    }
}



