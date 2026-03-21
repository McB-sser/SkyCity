package de.mcbesser.skycity.model;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;

public class IslandLevelDefinition {
    private final int level;
    private final Map<Material, Integer> requirements;
    private final int chunkUnlocksGranted;
    private final int animalLimit;
    private final int golemLimit;
    private final int villagerLimit;
    private final int hopperLimit;
    private final int pistonLimit;
    private final int armorStandLimit;
    private final int observerLimit;
    private final int dispenserLimit;
    private final int cactusLimit;
    private final int kelpLimit;
    private final int bambooLimit;

    public IslandLevelDefinition(int level, Map<Material, Integer> requirements, int chunkUnlocksGranted,
                                 int animalLimit, int golemLimit, int villagerLimit, int hopperLimit, int pistonLimit, int armorStandLimit, int observerLimit,
                                 int dispenserLimit, int cactusLimit, int kelpLimit, int bambooLimit) {
        this.level = level;
        this.requirements = requirements;
        this.chunkUnlocksGranted = chunkUnlocksGranted;
        this.animalLimit = animalLimit;
        this.golemLimit = golemLimit;
        this.villagerLimit = villagerLimit;
        this.hopperLimit = hopperLimit;
        this.pistonLimit = pistonLimit;
        this.armorStandLimit = armorStandLimit;
        this.observerLimit = observerLimit;
        this.dispenserLimit = dispenserLimit;
        this.cactusLimit = cactusLimit;
        this.kelpLimit = kelpLimit;
        this.bambooLimit = bambooLimit;
    }

    public int getLevel() { return level; }
    public Map<Material, Integer> getRequirements() { return requirements; }
    public int getChunkUnlocksGranted() { return chunkUnlocksGranted; }
    public int getAnimalLimit() { return animalLimit; }
    public int getGolemLimit() { return golemLimit; }
    public int getVillagerLimit() { return villagerLimit; }
    public int getHopperLimit() { return hopperLimit; }
    public int getPistonLimit() { return pistonLimit; }
    public int getArmorStandLimit() { return armorStandLimit; }
    public int getObserverLimit() { return observerLimit; }
    public int getDispenserLimit() { return dispenserLimit; }
    public int getCactusLimit() { return cactusLimit; }
    public int getKelpLimit() { return kelpLimit; }
    public int getBambooLimit() { return bambooLimit; }

    public static Map<Integer, IslandLevelDefinition> defaults() {
        Map<Integer, IslandLevelDefinition> defs = new LinkedHashMap<>();
        defs.put(1, new IslandLevelDefinition(1, Map.of(), 0, 12, 2, 2, 8, 16, 4, 12, 8, 32, 64, 64));
        for (int level = 2; level <= 12; level++) {
            int step = level - 1;
            defs.put(level, new IslandLevelDefinition(
                    level,
                    buildRequirements(step),
                    level <= 4 ? 1 : 2,
                    12,
                    2,
                    2,
                    8,
                    16,
                    4,
                    12,
                    8,
                    32,
                    64,
                    64
            ));
        }
        return defs;
    }

    private static Map<Material, Integer> buildRequirements(int step) {
        Map<Material, Integer> req = new LinkedHashMap<>();
        req.put(Material.COBBLESTONE, 64 + (step * 32));
        req.put(Material.STONE, 24 + (step * 16));
        req.put(Material.OAK_LOG, 12 + (step * 8));
        if (step >= 2) req.put(Material.IRON_INGOT, 8 + (step * 6));
        if (step >= 3) req.put(Material.REDSTONE, 8 + (step * 6));
        if (step >= 5) req.put(Material.GOLD_INGOT, 4 + (step * 3));
        if (step >= 6) req.put(Material.QUARTZ, 6 + (step * 3));
        if (step >= 7) req.put(Material.EMERALD, 2 + step);
        if (step >= 9) req.put(Material.DIAMOND, 2 + (step / 2));

        req.entrySet().removeIf(e -> e.getValue() <= 0);
        return req;
    }
}



