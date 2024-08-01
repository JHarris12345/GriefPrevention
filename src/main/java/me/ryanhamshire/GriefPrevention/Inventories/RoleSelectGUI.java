package me.ryanhamshire.GriefPrevention.Inventories;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Inventories.InventoryFiles.RoleSelectGUIFile;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimRole;
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
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class RoleSelectGUI extends GUI implements InventoryHolder, ClaimMenu {
    private static GriefPrevention plugin = GriefPrevention.getInstance();
    private Inventory inv;
    private Claim claim;
    private boolean waterfall;


    public RoleSelectGUI(Claim claim, boolean waterfall) {
        this.inv = Bukkit.createInventory(this, RoleSelectGUIFile.get().getInt("Size"), Utils.colour(RoleSelectGUIFile.get().getString("Title")));
        this.claim = claim;
        this.waterfall = waterfall;

        this.addContents();
    }

    private void addContents() {
        super.addContents(inv, true, GUIBackgroundType.FILLED);

        ItemStack item = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        ArrayList<String> lore = new ArrayList<>();

        for (String key : RoleSelectGUIFile.get().getConfigurationSection("Roles").getKeys(false)) {
            item.setType(Material.valueOf(RoleSelectGUIFile.get().getString("Roles." + key + ".Item")));
            meta.setDisplayName(Utils.colour(RoleSelectGUIFile.get().getString("Roles." + key + ".Name")));

            lore.clear();
            for (String loreLine : RoleSelectGUIFile.get().getStringList("Roles." + key + ".Lore")) {
                lore.add(Utils.colour(loreLine));
            }

            meta.setLore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "role"), PersistentDataType.STRING, key);

            item.setItemMeta(meta);
            inv.setItem(RoleSelectGUIFile.get().getInt("Roles." + key + ".Slot"), item);
        }
    }


    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }


    @Override
    public void handleClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();

        e.setCancelled(true);

        // Back to menu button method
        backButtonClickMethod(e, claim, waterfall);

        String roleName = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "role"), PersistentDataType.STRING);
        if (roleName == null) return;

        ClaimRole role = ClaimRole.valueOf(roleName);
        player.openInventory(new TogglePermissionsGUI(claim, role, waterfall).getInventory());
    }
}
