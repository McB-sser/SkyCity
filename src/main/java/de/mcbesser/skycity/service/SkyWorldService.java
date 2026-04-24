package de.mcbesser.skycity.service;

import de.mcbesser.skycity.SkyCityPlugin;
import de.mcbesser.skycity.world.VoidBarrierChunkGenerator;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

public class SkyWorldService {
    public static final String WORLD_NAME = "skycity_world";
    public static final int PLOT_SIZE_CHUNKS = 64;
    public static final int SPAWN_Y = 64;

    private final SkyCityPlugin plugin;
    private World world;
    private boolean newlyCreated;

    public SkyWorldService(SkyCityPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initializeWorld() {
        World existing = plugin.getServer().getWorld(WORLD_NAME);
        newlyCreated = existing == null;
        if (existing != null) {
            this.world = existing;
            configureWorld(existing);
            return true;
        }
        WorldCreator creator = new WorldCreator(WORLD_NAME);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.FLAT);
        creator.generator(new VoidBarrierChunkGenerator());
        try {
            world = plugin.getServer().createWorld(creator);
        } catch (IllegalStateException ex) {
            // Paper STARTUP phase: default world is created later; generator hook still works.
            if (ex.getMessage() != null && ex.getMessage().contains("STARTUP")) {
                return false;
            }
            throw ex;
        }
        if (world == null) {
            return false;
        }
        configureWorld(world);
        return true;
    }

    private void configureWorld(World world) {
        world.setDifficulty(Difficulty.NORMAL);
        world.setKeepSpawnInMemory(false);
        world.setGameRule(GameRule.DO_INSOMNIA, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.MOB_GRIEFING, true);
        world.setSpawnLocation(0, SPAWN_Y + 1, 0);
    }

    public World getWorld() { return world; }
    public boolean isNewlyCreated() { return newlyCreated; }
    public boolean isSkyCityWorld(World other) { return other != null && world != null && other.getUID().equals(world.getUID()); }
}



