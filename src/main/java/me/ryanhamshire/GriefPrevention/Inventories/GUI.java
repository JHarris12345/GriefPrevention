package me.ryanhamshire.GriefPrevention.Inventories;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Inventories.InventoryFiles.GUISettingsFile;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import me.ryanhamshire.GriefPrevention.objects.enums.GUIBackgroundType;
import me.ryanhamshire.GriefPrevention.utils.Utils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public class GUI {

    private GriefPrevention plugin = GriefPrevention.getInstance();

    public void addContents(Inventory inventory, boolean addBackButton, GUIBackgroundType backgroundType) {
        addBackground(inventory, addBackButton, backgroundType);
    }

    public void addBackground(Inventory inventory, boolean addBackButton, GUIBackgroundType backgroundType) {
        // Clear the inventory first
        ItemStack item = new ItemStack(Material.AIR);
        for (int i = 0; i<inventory.getSize(); i++) {
            inventory.setItem(i, item);
        }

        // Make the background item
        item.setType(Material.valueOf(GUISettingsFile.get().getString("Background.Item")));
        ItemMeta itemMeta = item.getItemMeta();
        itemMeta.setDisplayName(" ");
        item.setItemMeta(itemMeta);

        // Set the entire inventory to the background item
        if (backgroundType == GUIBackgroundType.FILLED) {
            for (int i = 0; i<inventory.getSize(); i++) {
                inventory.setItem(i, item);
            }
        }

        // Set just the edges of the inventory to the background item
        if (backgroundType == GUIBackgroundType.BORDER) {
            for (int i = 0; i<9; i++) {
                inventory.setItem(i, item);
            }

            if (inventory.getSize() >= 27) {
                inventory.setItem(9, item);
                inventory.setItem(17, item);
            }
            if (inventory.getSize() >= 36) {
                inventory.setItem(18, item);
                inventory.setItem(26, item);
            }
            if (inventory.getSize() >= 45) {
                inventory.setItem(27, item);
                inventory.setItem(35, item);
            }
            if (inventory.getSize() >= 54) {
                inventory.setItem(36, item);
                inventory.setItem(44, item);
            }

            for (int i = 1; i<10; i++) {
                inventory.setItem(inventory.getSize()-i, item);
            }
        }

        // Set just the bottom row to the background item
        if (backgroundType == GUIBackgroundType.BOTTOM) {
            for (int i = 1; i<10; i++) {
                inventory.setItem(inventory.getSize()-i, item);
            }
        }

        // Add a back button if it is wanted
        if (addBackButton) {
            item.setType(Material.valueOf(GUISettingsFile.get().getString("BackButton.Item")));
            SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
            Utils.giveHeadItemBase64Value(skullMeta, GUISettingsFile.get().getString("BackButton.Base64Value"));
            skullMeta.setDisplayName(Utils.colour(GUISettingsFile.get().getString("BackButton.Name")));
            item.setItemMeta(skullMeta);
            inventory.setItem(inventory.getSize()-5, item);
        }
    }

    public void backButtonClickMethod(InventoryClickEvent e, Claim claim) {
        Player player = (Player) e.getWhoClicked();
        if (claim == null) return;

        if (e.getSlot() == e.getInventory().getSize()-5) {
            player.openInventory(new MenuGUI(claim).getInventory());
        }

        return;
    }

    public boolean clickedBackButton(InventoryClickEvent e) {
        if (e.getSlot() == e.getInventory().getSize()-5) return true;
        return false;
    }

    public void backButtonClickMethodPermissions(InventoryClickEvent e, Claim claim) {
        Player player = (Player) e.getWhoClicked();
        if (!clickedBackButton(e)) return;

        player.openInventory(new RoleSelectGUI(claim).getInventory());
    }

    public boolean isAdminClicking(Player player, InventoryClickEvent e) {
        if (plugin.adminViewers.containsKey(player.getUniqueId()) && !clickedBackButton(e)) {
            e.setCancelled(true);
            player.sendMessage(Utils.colour("&7You can't modify a claim from the admin GUI"));
            return true;
        }

        return false;
    }

    public int getNeededSize(List population) {
        if (population == null) return 18;
        if (population.size() <= 9) return 18;
        if (population.size() <= 18 && population.size() > 9) return 27;
        if (population.size() <= 27 && population.size() > 18) return 36;
        if (population.size() <= 36 && population.size() > 27) return 45;
        if (population.size() > 36) return 54;

        return 0;
    }

    public int getNeededSize(int populationSize) {
        if (populationSize <= 9) return 18;
        if (populationSize <= 18 && populationSize > 9) return 27;
        if (populationSize <= 27 && populationSize > 18) return 36;
        if (populationSize <= 36 && populationSize > 27) return 45;
        if (populationSize > 36) return 54;

        return 0;
    }
}
