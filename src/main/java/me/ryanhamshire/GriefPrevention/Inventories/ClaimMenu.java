package me.ryanhamshire.GriefPrevention.Inventories;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public interface ClaimMenu {

    void handleClick(InventoryClickEvent e);

}
