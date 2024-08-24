package me.ryanhamshire.GriefPrevention.tasks;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.logs.ClaimModificationLog;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;

public class DeleteUnBuilts extends BukkitRunnable {

    private GriefPrevention plugin;

    public DeleteUnBuilts(GriefPrevention plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        long expirationMillis = plugin.unBuiltExpirationHours * 60 * 60 * 1000;

        for (Claim claim : new ArrayList<>(plugin.dataStore.claimMap.values())) {
            if (claim.created <= 0) continue;
            if (claim.builtOn) continue;
            if (claim.parent != null) continue;
            if (System.currentTimeMillis() - claim.created < expirationMillis) continue;

            plugin.dataStore.deleteClaim(claim);
            ClaimModificationLog.logToFile("Claim " + claim.id + " was deleted due to not being built on within " + plugin.unBuiltExpirationHours + " hours", true);
        }
    }
}
