package me.ryanhamshire.GriefPrevention.Inventories;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Inventories.InventoryFiles.MenuGUIFile;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import me.ryanhamshire.GriefPrevention.objects.enums.GUIBackgroundType;
import me.ryanhamshire.GriefPrevention.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.UUID;

public class MenuGUI extends GUI implements InventoryHolder, ClaimMenu {
    private static GriefPrevention plugin = GriefPrevention.getInstance();
    private Inventory inv;

    public MenuGUI(Claim claim) {
        inv = Bukkit.createInventory(this, MenuGUIFile.get().getInt("Size"), Utils.colour(MenuGUIFile.get().getString("Title")));
        addContents(claim);
    }

    private void addContents(Claim claim) {
        super.addBackground(inv, false, GUIBackgroundType.FILLED);
        ItemStack item = new ItemStack(Material.STONE);
        ArrayList<String> lore = new ArrayList<>();
        int slot = 0;

        for (String key : MenuGUIFile.get().getConfigurationSection("Icons").getKeys(false)) {
            item.setType(Material.valueOf(MenuGUIFile.get().getString("Icons." + key + ".Item")));
            ItemMeta meta = item.getItemMeta();

            meta.setDisplayName(Utils.colour(MenuGUIFile.get().getString("Icons." + key + ".Name")));

            lore.clear();
            for (String loreLine : MenuGUIFile.get().getStringList("Icons." + key + ".Lore")) {
                lore.add(Utils.colour(loreLine));
            }
            meta.setLore(lore);

            slot = MenuGUIFile.get().getInt("Icons." + key + ".Slot");

            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "icon"), PersistentDataType.STRING, key);

            if (item.getType() == Material.PLAYER_HEAD) {
                SkullMeta headMeta = (SkullMeta) meta.clone();

                if (MenuGUIFile.get().isSet("Icons." + key + ".Base64Value")) {
                    Utils.giveHeadItemBase64Value(headMeta, MenuGUIFile.get().getString("Icons." + key + ".Base64Value"));
                }

                if (MenuGUIFile.get().isSet("Icons." + key + ".HeadOwner")) {
                    String playerUUID = MenuGUIFile.get().getString("Icons." + key + ".HeadOwner").replaceAll("%claimOwner%", claim.ownerID.toString());
                    headMeta.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(playerUUID)));
                }

                item.setItemMeta(headMeta);

            } else {
                item.setItemMeta(meta);
            }

            inv.setItem(slot, item);
        }
    }


    public void refreshContents(Claim claim) {
        addContents(claim);
    }


    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }


    @Override
    public void handleClick(InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        String command = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "command"), PersistentDataType.STRING);
        if (command == null) return;

        Bukkit.dispatchCommand(e.getWhoClicked(), command);
    }
}
