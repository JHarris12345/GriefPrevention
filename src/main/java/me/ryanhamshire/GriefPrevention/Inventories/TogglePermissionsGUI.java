package me.ryanhamshire.GriefPrevention.Inventories;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Inventories.InventoryFiles.RolePermissionsGUIFile;
import me.ryanhamshire.GriefPrevention.Inventories.InventoryFiles.RoleSelectGUIFile;
import me.ryanhamshire.GriefPrevention.logs.PermissionChangeLogs;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimPermission;
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
import java.util.UUID;

public class TogglePermissionsGUI extends GUI implements InventoryHolder, ClaimMenu {

    private static GriefPrevention plugin = GriefPrevention.getInstance();
    private Inventory inv;
    private ClaimRole role;
    private int guiSize;
    private Claim claim;


    public TogglePermissionsGUI(Claim claim, ClaimRole role) {
        this.guiSize = super.getNeededSize(ClaimPermission.values().length);
        this.inv = Bukkit.createInventory(this, guiSize, Utils.colour(RoleSelectGUIFile.get().getString("Roles." + role.name() + ".GUIName")));
        this.role = role;
        this.claim = claim;

        this.addContents(claim);
    }

    private void addContents(Claim claim) {
        super.addContents(inv, true, GUIBackgroundType.FILLED);

        ItemStack item = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        ArrayList<String> lore = new ArrayList<>();
        int slot = 0;

        for (ClaimPermission claimPermission : ClaimPermission.values()) {
            if (claim.doesRoleHavePermission(role, claimPermission)) {
                item.setType(Material.valueOf(RolePermissionsGUIFile.get().getString("Buttons.Enabled.Item")));
            } else {
                item.setType(Material.valueOf(RolePermissionsGUIFile.get().getString("Buttons.Disabled.Item")));
            }

            meta.setDisplayName(Utils.colour(RolePermissionsGUIFile.get().getString("Permissions." + claimPermission.name() + ".Name")));

            lore.clear();
            for (String loreLine : RolePermissionsGUIFile.get().getStringList("Permissions." + claimPermission.name() + ".Lore")) {
                lore.add(Utils.colour(loreLine));
            }

            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "permission"), PersistentDataType.STRING, claimPermission.name());

            meta.setLore(lore);
            item.setItemMeta(meta);

            inv.setItem(slot, item);
            slot++;
        }
    }


    public void refreshContents(Claim claim) {
        for (UUID uuid : claim.getClaimMembers(true).keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof TogglePermissionsGUI) {
                player.openInventory(new TogglePermissionsGUI(claim, role).getInventory());
            }
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
        backButtonClickMethodPermissions(e, claim);

        // If it's owner permissions, return as they can't be changed
        if (role == ClaimRole.OWNER) return;

        // Prevent an admin modifying the island
        if (isAdminClicking(player, e)) return;

        String permissionName = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "permission"), PersistentDataType.STRING);
        if (permissionName == null) return;

        ClaimPermission permission = ClaimPermission.valueOf(permissionName);

        // If the player doesn't have permission to change permissions
        if (!claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.MANAGE_PERMISSIONS)) {
            player.sendMessage(Utils.colour(ClaimPermission.MANAGE_PERMISSIONS.getDenialMessage()));
            return;
        }

        boolean currentBoolean = claim.doesRoleHavePermission(role, permission);
        boolean newBoolean = !(currentBoolean);

        if (newBoolean) {
            claim.addPermissionToRole(permission, role);
        } else {
            claim.removePermissionFromRole(permission, role);
        }

        PermissionChangeLogs.logToFile(player.getName() + " set the " + permissionName + " permission to " + newBoolean +
                " for the " + role.name() + " role for claim " + claim.id, true);

        refreshContents(claim);
    }
}
