package de.mcbesser.skycity;

import de.mcbesser.skycity.command.IslandCommand;
import de.mcbesser.skycity.command.SpawnCommand;
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
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

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
    private boolean runtimeInitialized;
    private int placeholderRetryTaskId = -1;

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
        placeholderRetryTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (placeholderExpansion != null && placeholderExpansion.isRegistered()) {
                Bukkit.getScheduler().cancelTask(placeholderRetryTaskId);
                placeholderRetryTaskId = -1;
                return;
            }
            registerPlaceholderExpansionIfAvailable();
        }, 20L, 40L);

        Bukkit.getPluginManager().registerEvents(new PlayerListener(this, islandService, skyWorldService, coreService), this);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this, islandService, coreService, skyWorldService), this);
        Bukkit.getPluginManager().registerEvents(new PlotWandListener(this, islandService, skyWorldService, coreService), this);
        Bukkit.getPluginManager().registerEvents(new CoreMenuListener(islandService, coreService, particlePreviewService), this);
        Bukkit.getPluginManager().registerEvents(new BootstrapListener(), this);

        getCommand("spawn").setExecutor(new SpawnCommand(islandService));
        IslandCommand islandCommand = new IslandCommand(this, islandService, coreService, particlePreviewService);
        getCommand("is").setExecutor(islandCommand);
        getCommand("is").setTabCompleter(islandCommand);
        getCommand("warp").setExecutor(islandCommand);
        getCommand("warp").setTabCompleter(islandCommand);

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
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
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

    private final class BootstrapListener implements Listener {
        @EventHandler
        public void onWorldLoad(WorldLoadEvent event) {
            if (SkyWorldService.WORLD_NAME.equalsIgnoreCase(event.getWorld().getName())) {
                completeRuntimeInitializationIfReady();
            }
        }
    }
}



