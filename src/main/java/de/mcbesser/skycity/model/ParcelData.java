package de.mcbesser.skycity.model;

import org.bukkit.Location;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ParcelData {
    public enum MarketPaymentType {
        EXPERIENCE,
        VAULT
    }

    public enum RentDurationUnit {
        MINUTES,
        HOURS,
        DAYS
    }

    private final String chunkKey;
    private String name;
    private final Set<UUID> owners = new HashSet<>();
    private final Set<UUID> users = new HashSet<>();
    private final Set<UUID> banned = new HashSet<>();
    private final Set<UUID> pvpWhitelist = new HashSet<>();
    private final Map<UUID, Integer> pvpKills = new HashMap<>();
    private final AccessSettings visitorSettings = new AccessSettings();
    private final AccessSettings memberSettings = new AccessSettings();
    private boolean pvpEnabled;
    private boolean pveEnabled;
    private boolean memberAnimalBreed;
    private boolean memberAnimalKill;
    private boolean memberAnimalKeepTwo = true;
    private boolean memberAnimalShear;
    private boolean saleOfferEnabled;
    private long salePrice;
    private boolean rentOfferEnabled;
    private long rentPrice;
    private String paymentType = MarketPaymentType.EXPERIENCE.name();
    private int rentDurationAmount;
    private String rentDurationUnit = RentDurationUnit.DAYS.name();
    private UUID renter;
    private long rentUntil;
    private UUID lastSaleBuyer;
    private long lastSalePrice;
    private String lastSalePaymentType = MarketPaymentType.EXPERIENCE.name();
    private UUID lastRentBuyer;
    private long lastRentPrice;
    private String lastRentPaymentType = MarketPaymentType.EXPERIENCE.name();
    private Location spawn;
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    public ParcelData(String chunkKey) {
        this.chunkKey = chunkKey;
        this.name = chunkKey;
    }

    public String getChunkKey() { return chunkKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Set<UUID> getOwners() { return owners; }
    public Set<UUID> getUsers() { return users; }
    public Set<UUID> getBanned() { return banned; }
    public Set<UUID> getPvpWhitelist() { return pvpWhitelist; }
    public Map<UUID, Integer> getPvpKills() { return pvpKills; }
    public AccessSettings getVisitorSettings() { return visitorSettings; }
    public AccessSettings getMemberSettings() { return memberSettings; }
    public boolean isPvpEnabled() { return pvpEnabled; }
    public void setPvpEnabled(boolean pvpEnabled) { this.pvpEnabled = pvpEnabled; }
    public boolean isPveEnabled() { return pveEnabled; }
    public void setPveEnabled(boolean pveEnabled) { this.pveEnabled = pveEnabled; }
    public boolean isMemberAnimalBreed() { return memberAnimalBreed; }
    public void setMemberAnimalBreed(boolean memberAnimalBreed) { this.memberAnimalBreed = memberAnimalBreed; }
    public boolean isMemberAnimalKill() { return memberAnimalKill; }
    public void setMemberAnimalKill(boolean memberAnimalKill) { this.memberAnimalKill = memberAnimalKill; }
    public boolean isMemberAnimalKeepTwo() { return memberAnimalKeepTwo; }
    public void setMemberAnimalKeepTwo(boolean memberAnimalKeepTwo) { this.memberAnimalKeepTwo = memberAnimalKeepTwo; }
    public boolean isMemberAnimalShear() { return memberAnimalShear; }
    public void setMemberAnimalShear(boolean memberAnimalShear) { this.memberAnimalShear = memberAnimalShear; }
    public boolean isSaleOfferEnabled() { return saleOfferEnabled; }
    public void setSaleOfferEnabled(boolean saleOfferEnabled) { this.saleOfferEnabled = saleOfferEnabled; }
    public long getSalePrice() { return salePrice; }
    public void setSalePrice(long salePrice) { this.salePrice = Math.max(0L, salePrice); }
    public boolean isRentOfferEnabled() { return rentOfferEnabled; }
    public void setRentOfferEnabled(boolean rentOfferEnabled) { this.rentOfferEnabled = rentOfferEnabled; }
    public long getRentPrice() { return rentPrice; }
    public void setRentPrice(long rentPrice) { this.rentPrice = Math.max(0L, rentPrice); }
    public MarketPaymentType getPaymentType() {
        try {
            return MarketPaymentType.valueOf(paymentType == null ? MarketPaymentType.EXPERIENCE.name() : paymentType);
        } catch (IllegalArgumentException ignored) {
            return MarketPaymentType.EXPERIENCE;
        }
    }
    public void setPaymentType(MarketPaymentType paymentType) {
        this.paymentType = paymentType == null ? MarketPaymentType.EXPERIENCE.name() : paymentType.name();
    }
    public int getRentDurationAmount() { return rentDurationAmount; }
    public void setRentDurationAmount(int rentDurationAmount) { this.rentDurationAmount = Math.max(0, rentDurationAmount); }
    public RentDurationUnit getRentDurationUnit() {
        try {
            return RentDurationUnit.valueOf(rentDurationUnit == null ? RentDurationUnit.DAYS.name() : rentDurationUnit);
        } catch (IllegalArgumentException ignored) {
            return RentDurationUnit.DAYS;
        }
    }
    public void setRentDurationUnit(RentDurationUnit rentDurationUnit) {
        this.rentDurationUnit = rentDurationUnit == null ? RentDurationUnit.DAYS.name() : rentDurationUnit.name();
    }
    public int getRentDurationDays() { return getRentDurationUnit() == RentDurationUnit.DAYS ? rentDurationAmount : 0; }
    public void setRentDurationDays(int rentDurationDays) {
        this.rentDurationAmount = Math.max(0, rentDurationDays);
        this.rentDurationUnit = RentDurationUnit.DAYS.name();
    }
    public UUID getRenter() { return renter; }
    public void setRenter(UUID renter) { this.renter = renter; }
    public long getRentUntil() { return rentUntil; }
    public void setRentUntil(long rentUntil) { this.rentUntil = Math.max(0L, rentUntil); }
    public UUID getLastSaleBuyer() { return lastSaleBuyer; }
    public void setLastSaleBuyer(UUID lastSaleBuyer) { this.lastSaleBuyer = lastSaleBuyer; }
    public long getLastSalePrice() { return lastSalePrice; }
    public void setLastSalePrice(long lastSalePrice) { this.lastSalePrice = Math.max(0L, lastSalePrice); }
    public MarketPaymentType getLastSalePaymentType() {
        try {
            return MarketPaymentType.valueOf(lastSalePaymentType == null ? MarketPaymentType.EXPERIENCE.name() : lastSalePaymentType);
        } catch (IllegalArgumentException ignored) {
            return MarketPaymentType.EXPERIENCE;
        }
    }
    public void setLastSalePaymentType(MarketPaymentType paymentType) {
        this.lastSalePaymentType = paymentType == null ? MarketPaymentType.EXPERIENCE.name() : paymentType.name();
    }
    public UUID getLastRentBuyer() { return lastRentBuyer; }
    public void setLastRentBuyer(UUID lastRentBuyer) { this.lastRentBuyer = lastRentBuyer; }
    public long getLastRentPrice() { return lastRentPrice; }
    public void setLastRentPrice(long lastRentPrice) { this.lastRentPrice = Math.max(0L, lastRentPrice); }
    public MarketPaymentType getLastRentPaymentType() {
        try {
            return MarketPaymentType.valueOf(lastRentPaymentType == null ? MarketPaymentType.EXPERIENCE.name() : lastRentPaymentType);
        } catch (IllegalArgumentException ignored) {
            return MarketPaymentType.EXPERIENCE;
        }
    }
    public void setLastRentPaymentType(MarketPaymentType paymentType) {
        this.lastRentPaymentType = paymentType == null ? MarketPaymentType.EXPERIENCE.name() : paymentType.name();
    }
    public Location getSpawn() { return spawn; }
    public void setSpawn(Location spawn) { this.spawn = spawn; }
    public int getMinX() { return minX; }
    public void setMinX(int minX) { this.minX = minX; }
    public int getMinY() { return minY; }
    public void setMinY(int minY) { this.minY = minY; }
    public int getMinZ() { return minZ; }
    public void setMinZ(int minZ) { this.minZ = minZ; }
    public int getMaxX() { return maxX; }
    public void setMaxX(int maxX) { this.maxX = maxX; }
    public int getMaxY() { return maxY; }
    public void setMaxY(int maxY) { this.maxY = maxY; }
    public int getMaxZ() { return maxZ; }
    public void setMaxZ(int maxZ) { this.maxZ = maxZ; }

    public void setBounds(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public boolean hasBounds() {
        return maxX >= minX && maxY >= minY && maxZ >= minZ;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean intersects(ParcelData other) {
        if (other == null) return false;
        return this.maxX >= other.minX && this.minX <= other.maxX
                && this.maxY >= other.minY && this.minY <= other.maxY
                && this.maxZ >= other.minZ && this.minZ <= other.maxZ;
    }
}



