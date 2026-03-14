package de.mcbesser.skycity.model;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;

public class IslandLevelDefinition {
    private static final int TOTAL_ISLAND_CHUNKS = 4096;
    private static final int START_UNLOCKED_CHUNKS = 4;
    private static final int UPGRADE_STEPS = TOTAL_ISLAND_CHUNKS - START_UNLOCKED_CHUNKS; // 4092

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
        for (int level = 2; level <= (UPGRADE_STEPS + 1); level++) {
            int step = level - 1;
            defs.put(level, new IslandLevelDefinition(
                    level,
                    buildRequirements(step),
                    1,
                    computeAnimalLimit(step),
                    computeGolemLimit(step),
                    computeVillagerLimit(step),
                    computeHopperLimit(step),
                    computePistonLimit(step),
                    computeArmorStandLimit(step),
                    computeObserverLimit(step),
                    computeDispenserLimit(step),
                    computeCactusLimit(step),
                    computeKelpLimit(step),
                    computeBambooLimit(step)
            ));
        }
        return defs;
    }

    private static Map<Material, Integer> buildRequirements(int step) {
        Map<Material, Integer> req = new LinkedHashMap<>();

        // Early game: simple base resources.
        req.put(Material.COBBLESTONE, scale(step, 96, 4.8, 0.90, 1.18));
        req.put(Material.STONE, gated(step, 6, 32, 2.2, 0.55, 1.12));
        req.put(Material.OAK_LOG, gated(step, 10, 24, 0.55, 0.18, 1.08));
        req.put(Material.ANDESITE, gated(step, 40, 12, 0.95, 0.18, 1.08));
        req.put(Material.DIORITE, gated(step, 70, 12, 0.92, 0.18, 1.08));
        req.put(Material.GRANITE, gated(step, 100, 12, 0.90, 0.18, 1.08));
        req.put(Material.COAL, gated(step, 130, 20, 0.82, 0.16, 1.08));

        // Mid game: technical and metal progression.
        req.put(Material.IRON_INGOT, gated(step, 180, 20, 0.92, 0.25, 1.10));
        req.put(Material.REDSTONE, gated(step, 260, 20, 0.78, 0.22, 1.10));
        req.put(Material.GOLD_INGOT, gated(step, 420, 12, 0.45, 0.15, 1.10));
        req.put(Material.QUARTZ, gated(step, 620, 12, 0.55, 0.15, 1.10));
        req.put(Material.LAPIS_LAZULI, gated(step, 820, 12, 0.52, 0.15, 1.10));

        // Late game: rare materials, kept moderate to stay achievable.
        req.put(Material.EMERALD, gated(step, 1300, 6, 0.22, 0.09, 1.10));
        req.put(Material.DIAMOND, gated(step, 1800, 4, 0.16, 0.07, 1.10));
        req.put(Material.OBSIDIAN, gated(step, 2400, 16, 0.45, 0.12, 1.08));
        req.put(Material.OBSERVER, gated(step, 3000, 2, 0.05, 0.05, 1.05));

        req.entrySet().removeIf(e -> e.getValue() <= 0);
        return req;
    }

    private static int computeAnimalLimit(int step) {
        return Math.min(72, 12 + (step / 96));
    }

    private static int computeVillagerLimit(int step) {
        return Math.min(24, 2 + (step / 180));
    }

    private static int computeGolemLimit(int step) {
        return Math.min(36, 2 + (step / 120));
    }

    private static int computeHopperLimit(int step) {
        return Math.min(96, 8 + (step / 46));
    }

    private static int computePistonLimit(int step) {
        return Math.min(160, 16 + (step / 28));
    }

    private static int computeArmorStandLimit(int step) {
        return Math.min(32, 4 + (step / 144));
    }

    private static int computeObserverLimit(int step) {
        return Math.min(128, 12 + (step / 34));
    }

    private static int computeDispenserLimit(int step) {
        return Math.min(160, 8 + (step / 30));
    }

    private static int computeCactusLimit(int step) {
        return Math.min(1024, 32 + (step / 5));
    }

    private static int computeKelpLimit(int step) {
        return Math.min(2048, 64 + (step / 3));
    }

    private static int computeBambooLimit(int step) {
        return Math.min(2048, 64 + (step / 3));
    }

    private static int gated(int step, int startStep, int base, double linear, double powScale, double powExp) {
        if (step < startStep) return 0;
        int local = step - startStep + 1;
        return scale(local, base, linear, powScale, powExp);
    }

    private static int scale(int value, int base, double linear, double powScale, double powExp) {
        double out = base + (value * linear) + (Math.pow(value, powExp) * powScale);
        return Math.max(base, (int) Math.floor(out));
    }
}



