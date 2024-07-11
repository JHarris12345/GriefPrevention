package me.ryanhamshire.GriefPrevention.Inventories;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Inventories.InventoryFiles.MembersGUIFile;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import me.ryanhamshire.GriefPrevention.objects.enums.GUIBackgroundType;
import me.ryanhamshire.GriefPrevention.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MembersGUI extends GUI implements InventoryHolder, ClaimMenu {
    private static GriefPrevention plugin = GriefPrevention.getInstance();
    private Inventory inv;

    public MembersGUI(Claim claim) {
        inv = Bukkit.createInventory(this, getNeededSize(claim.members.size() + 1), Utils.colour(MembersGUIFile.get().getString("Title")));
        this.addContents(claim);
    }

    private void addContents(Claim claim) {
        super.addContents(inv, true, GUIBackgroundType.FILLED);

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        List<String> lore = new ArrayList<>();
        List<String> members = claim.members; // List of "uuid:role" for each member

        int slot = 0;
        for (String memberUUID : members) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(memberUUID));
            RoleRelation roleRelation = RoleRelationManager.getPlayerRole(player);
            String role = roleRelation.name().substring(0, 1).toUpperCase() + roleRelation.name().substring(1).toLowerCase();

            if (player.getName() == null) continue;
            meta.setDisplayName(Utils.colour(MembersGUIFile.get().getString("Head.Name").replaceAll("%name%", player.getName())));

            if (UserManager.getUserFromPlayer(player) == null) continue;

            lore.clear();
            for (String loreLine : MembersGUIFile.get().getStringList("Head.Lore")) {
                lore.add(Utils.colour(loreLine.replaceAll("%role%", role)));
            }

            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "uuid"), PersistentDataType.STRING, memberUUID);
            meta.setOwningPlayer(player);
            meta.setLore(lore);
            item.setItemMeta(meta);

            inv.setItem(slot, item);
            slot++;
        }
    }

    public void refreshContents(Faction faction) {
        inv = Bukkit.createInventory(this, getNeededSize(faction.getMembers()), Utils.colour(MembersGUIFile.get().getString("Title")));
        this.addContents(faction);

        for (String uuidString : faction.getMembers()) {
            Player factionPlayer = Bukkit.getPlayer(UUID.fromString(uuidString));
            if (factionPlayer != null && factionPlayer.getOpenInventory().getTopInventory().getHolder() instanceof MembersGUI) {
                factionPlayer.openInventory(faction.getMembersGUI().getInventory());
            }
        }
    }


    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }


    @Override
    public void handleClick(InventoryClickEvent e) {
        e.setCancelled(true);

        Player player = (Player) e.getWhoClicked();
        Faction faction = FactionManager.getFactionViaPlayer(player, false);

        // Back to menu button method
        backButtonClickMethod(e);

        // Prevent an admin modifying the faction
        if (isAdminClicking(player, e)) return;

        String memberUUID = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "uuid"), PersistentDataType.STRING);
        if (memberUUID == null) return;

        OfflinePlayer target = Bukkit.getOfflinePlayer(UUID.fromString(memberUUID));
        RoleRelation targetRole = RoleRelationManager.getPlayerRole(target);
        RoleRelation playerRole = RoleRelationManager.getPlayerRole(player);


        // Left click to promote
        if (e.getClick() == ClickType.LEFT) {
            if (!PermissionsManager.isPermissionEnabled(faction, player, PermissionName.PROMOTE_MEMBERS)) {
                player.sendMessage(MessagesManager.messages.get("NoFactionPermission"));
                return;
            }

            if (target.getName().equalsIgnoreCase(player.getName())) {
                player.sendMessage(MessagesManager.messages.get("CannotChangeOwnRole"));
                return;
            }

            if (playerRole.name().equalsIgnoreCase(targetRole.name()) || !RoleRelationManager.isRoleHigherThanRole(playerRole, targetRole)) {
                player.sendMessage(MessagesManager.messages.get("CannotChangeRoleForSameOrHigher"));
                return;
            }

            RoleRelation nextRoleUp = RoleRelationManager.getNextRoleUp(targetRole);
            if (nextRoleUp == playerRole && nextRoleUp != RoleRelation.LEADER) {
                player.sendMessage(MessagesManager.messages.get("CannotPromoteToSameRole"));
                return;
            }

            if (targetRole == RoleRelation.ADMINISTRATOR) {
                player.sendMessage(MessagesManager.messages.get("CannotPromoteAdministrator"));
                return;
            }

            if (targetRole == RoleRelation.LEADER) {
                player.sendMessage(MessagesManager.messages.get("CannotPromoteLeader"));
                return;
            }

            FactionManager.promotePlayer(faction, target);
            RoleRelation newRole = RoleRelationManager.getPlayerRole(target);
            player.sendMessage(MessagesManager.messages.get("PlayerPromotedMember").replaceAll("%name%", target.getName()).replaceAll("%role%", newRole.name().toLowerCase()));
            FactionManager.sendMessageToAllFactionMembers(faction, MessagesManager.messages.get("PromotedMemberFactionMessage").replaceAll("%name%", target.getName()).replaceAll("%role%", newRole.name().toLowerCase()).replaceAll("%player%", player.getName()),
                    null, new ArrayList<>(Arrays.asList(target.getUniqueId().toString(), player.getUniqueId().toString())));

            if (target.isOnline()) {
                Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
                onlineTarget.sendMessage(MessagesManager.messages.get("GotPromoted").replaceAll("%role%", newRole.name().toLowerCase()));
            }
        }


        // Right click to demote
        if (e.getClick() == ClickType.RIGHT) {
            if (!PermissionsManager.isPermissionEnabled(faction, player, PermissionName.DEMOTE_MEMBERS)) {
                player.sendMessage(MessagesManager.messages.get("NoFactionPermission"));
                return;
            }

            if (target.getName().equalsIgnoreCase(player.getName())) {
                player.sendMessage(MessagesManager.messages.get("CannotChangeOwnRole"));
                return;
            }

            if (playerRole.name().equalsIgnoreCase(targetRole.name()) || !RoleRelationManager.isRoleHigherThanRole(playerRole, targetRole)) {
                player.sendMessage(MessagesManager.messages.get("CannotChangeRoleForSameOrHigher"));
                return;
            }

            if (targetRole == RoleRelation.LEADER) {
                player.sendMessage(MessagesManager.messages.get("CannotDemoteLeader"));
                return;
            }

            if (targetRole == RoleRelation.RECRUIT) {
                player.sendMessage(MessagesManager.messages.get("CannotDemoteRecruit"));
                return;
            }

            FactionManager.demotePlayer(faction, target);
            RoleRelation newRole = RoleRelationManager.getPlayerRole(target);
            player.sendMessage(MessagesManager.messages.get("PlayerDemotedMember").replaceAll("%name%", target.getName()).replaceAll("%role%", newRole.name().toLowerCase()));
            FactionManager.sendMessageToAllFactionMembers(faction, MessagesManager.messages.get("DemotedMemberFactionMessage").replaceAll("%name%", target.getName()).replaceAll("%role%", newRole.name().toLowerCase()).replaceAll("%player%",
                    player.getName()), null, new ArrayList<>(Arrays.asList(target.getUniqueId().toString(), player.getUniqueId().toString())));

            if (target.isOnline()) {
                Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
                onlineTarget.sendMessage(MessagesManager.messages.get("GotDemoted").replaceAll("%role%", newRole.name().toLowerCase()));
            }
        }
    }
}
