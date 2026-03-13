package de.mcbesser.skycity.model;

public class AccessSettings {
    private boolean doors = false;
    private boolean trapdoors = false;
    private boolean fenceGates = false;
    private boolean buttons = false;
    private boolean levers = false;
    private boolean pressurePlates = false;
    private boolean containers = false;
    private boolean farmUse = false;
    private boolean ride = false;
    private boolean redstoneUse = false;
    private boolean ladderPlace = false;
    private boolean ladderBreak = false;
    private boolean leavesPlace = false;
    private boolean leavesBreak = false;
    private boolean teleport = true;

    public boolean isDoors() { return doors; }
    public void setDoors(boolean doors) { this.doors = doors; }
    public boolean isTrapdoors() { return trapdoors; }
    public void setTrapdoors(boolean trapdoors) { this.trapdoors = trapdoors; }
    public boolean isFenceGates() { return fenceGates; }
    public void setFenceGates(boolean fenceGates) { this.fenceGates = fenceGates; }
    public boolean isButtons() { return buttons; }
    public void setButtons(boolean buttons) { this.buttons = buttons; }
    public boolean isLevers() { return levers; }
    public void setLevers(boolean levers) { this.levers = levers; }
    public boolean isPressurePlates() { return pressurePlates; }
    public void setPressurePlates(boolean pressurePlates) { this.pressurePlates = pressurePlates; }
    public boolean isContainers() { return containers; }
    public void setContainers(boolean containers) { this.containers = containers; }
    public boolean isFarmUse() { return farmUse; }
    public void setFarmUse(boolean farmUse) { this.farmUse = farmUse; }
    public boolean isRide() { return ride; }
    public void setRide(boolean ride) { this.ride = ride; }
    public boolean isRedstoneUse() { return redstoneUse; }
    public void setRedstoneUse(boolean redstoneUse) { this.redstoneUse = redstoneUse; }
    public boolean isLadderPlace() { return ladderPlace; }
    public void setLadderPlace(boolean ladderPlace) { this.ladderPlace = ladderPlace; }
    public boolean isLadderBreak() { return ladderBreak; }
    public void setLadderBreak(boolean ladderBreak) { this.ladderBreak = ladderBreak; }
    public boolean isLeavesPlace() { return leavesPlace; }
    public void setLeavesPlace(boolean leavesPlace) { this.leavesPlace = leavesPlace; }
    public boolean isLeavesBreak() { return leavesBreak; }
    public void setLeavesBreak(boolean leavesBreak) { this.leavesBreak = leavesBreak; }
    public boolean isTeleport() { return teleport; }
    public void setTeleport(boolean teleport) { this.teleport = teleport; }
}



