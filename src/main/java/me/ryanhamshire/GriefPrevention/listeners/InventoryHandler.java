package me.ryanhamshire.GriefPrevention.listeners;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Inventories.ClaimMenu;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryHolder;

public class InventoryHandler implements Listener {

    GriefPrevention plugin;

    public InventoryHandler(GriefPrevention plugin) {
        this.plugin = plugin;
    }


    // Handle all GriefPrevention GUI click listeners
    @EventHandler(ignoreCancelled = true)
    public void onClaimMenuClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null) return;
        if (e.getCurrentItem() == null) return;
        if (e.getWhoClicked().getOpenInventory().getTopInventory().getType() != InventoryType.CHEST) return;

        InventoryHolder holder = e.getWhoClicked().getOpenInventory().getTopInventory().getHolder();
        if (!(holder instanceof ClaimMenu)) return;

        ((ClaimMenu) holder).handleClick(e);
    }
}
