package de.mcbesser.skycity.listener;

import de.mcbesser.skycity.service.CoreService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

public class CraftingListener implements Listener {
    private final CoreService coreService;

    public CraftingListener(CoreService coreService) {
        this.coreService = coreService;
    }

    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        for (ItemStack ingredient : inventory.getMatrix()) {
            if (coreService.isCoreItem(ingredient)) {
                inventory.setResult(null);
                return;
            }
        }
    }
}
