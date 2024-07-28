package me.ryanhamshire.GriefPrevention.listeners;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimSetting;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;

public class WorldEventHandler implements Listener {

    private GriefPrevention plugin;

    public WorldEventHandler(GriefPrevention plugin) {
        this.plugin = plugin;
    }


    // Prevent leaves decaying if the setting is false
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDecay(LeavesDecayEvent e) {
        Claim claim = GriefPrevention.plugin.dataStore.getClaimAt(e.getBlock().getLocation(), true, null);

        if (claim == null) return;
        if (claim.isSettingEnabled(ClaimSetting.LEAF_DECAY)) return;

        e.setCancelled(true);
    }
}
