package me.ryanhamshire.GriefPrevention.Inventories;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Inventories.InventoryFiles.MembersGUIFile;
import me.ryanhamshire.GriefPrevention.logs.MemberModificationLogs;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimPermission;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimRole;
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
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class MembersGUI extends GUI implements InventoryHolder, ClaimMenu {
    private static GriefPrevention plugin = GriefPrevention.getInstance();
    private Inventory inv;
    private Claim claim;
    private boolean waterfall;

    public MembersGUI(Claim claim, boolean waterfall) {
        this.inv = Bukkit.createInventory(this, getNeededSize(claim.members.size() + 1), Utils.colour(MembersGUIFile.get().getString("Title")));
        this.claim = claim;
        this.waterfall = waterfall;

        this.addContents(claim);
    }

    private void addContents(Claim claim) {
        super.addContents(inv, true, GUIBackgroundType.FILLED);
        HashMap<UUID, ClaimRole> members = claim.getClaimMembers(true);

        int slot = 0;
        for (UUID memberUUID : members.keySet()) {
            if (slot >= inv.getSize()) break;

            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            List<String> lore = new ArrayList<>();

            OfflinePlayer player = Bukkit.getOfflinePlayer(memberUUID);
            ClaimRole claimRole = claim.getPlayerRole(player.getUniqueId());
            String role = claimRole.name().substring(0, 1).toUpperCase() + claimRole.name().substring(1).toLowerCase();

            if (player.getName() == null) continue;
            meta.setDisplayName(Utils.colour(MembersGUIFile.get().getString("Head.Name").replaceAll("%name%", player.getName())));

            for (String loreLine : MembersGUIFile.get().getStringList("Head.Lore")) {
                lore.add(Utils.colour(loreLine.replaceAll("%role%", role)));
            }

            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "uuid"), PersistentDataType.STRING, memberUUID.toString());
            meta.setOwningPlayer(player);
            meta.setLore(lore);
            item.setItemMeta(meta);

            inv.setItem(slot, item);
            slot++;
        }
    }

    public void refreshContents(Claim claim) {
        for (UUID uuid : claim.getClaimMembers(true).keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof MembersGUI) {
                player.openInventory(new MembersGUI(claim, waterfall).getInventory());
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

        // Back to menu button method
        backButtonClickMethod(e, this.claim, waterfall);

        // Prevent an admin modifying the claim
        if (isAdminClicking(player, e, claim)) return;

        String memberUUID = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "uuid"), PersistentDataType.STRING);
        if (memberUUID == null) return;

        OfflinePlayer target = Bukkit.getOfflinePlayer(UUID.fromString(memberUUID));
        ClaimRole targetRole = claim.getPlayerRole(target.getUniqueId());
        ClaimRole playerRole = claim.getPlayerRole(player.getUniqueId());

        // Left click to promote
        if (e.getClick() == ClickType.LEFT) {
            if (!claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.PROMOTE_DEMOTE)) {
                player.sendMessage(Utils.colour(ClaimPermission.PROMOTE_DEMOTE.getDenialMessage()));
                return;
            }

            if (target.getName().equalsIgnoreCase(player.getName())) {
                player.sendMessage(Utils.colour("&cYou can't change your own role"));
                return;
            }

            if (!ClaimRole.isRole1HigherThanRole2(playerRole, targetRole)) {
                player.sendMessage(Utils.colour("&cYou can't change the role of someone with the same role as you or higher"));
                return;
            }

            ClaimRole nextRoleUp = ClaimRole.getHigherRole(targetRole);
            if (nextRoleUp == playerRole && nextRoleUp != ClaimRole.OWNER) {
                player.sendMessage(Utils.colour("&cYou can't promote members to the same role as you"));
                return;
            }

            if (targetRole == ClaimRole.MANAGER) {
                player.sendMessage(Utils.colour("&cYou can't promote a claim manager to claim owner. If you'd like to transfer this claim to them use &o/transferclaim " + target.getName()));
                return;
            }

            if (targetRole == ClaimRole.OWNER) {
                player.sendMessage(Utils.colour("&cYou can't promote the owner of the claim"));
                return;
            }

            claim.setClaimRole(target.getUniqueId(), nextRoleUp);
            player.sendMessage(Utils.colour("&aYou promoted " + target.getName() + " to the " + nextRoleUp + " role on this claim"));

            if (target.isOnline()) {
                target.getPlayer().sendMessage(Utils.colour("&a" + player.getName() + " promoted you to the " + nextRoleUp + " role on their claim"));
            }

            if (waterfall) {
                for (Claim sub : claim.children) {
                    sub.setClaimRole(target.getUniqueId(), nextRoleUp);
                }
            }

            // Log it
            MemberModificationLogs.logToFile(player.getName() + " promoted " + target.getName() + " to " + nextRoleUp + " on claim " + claim.id + ((waterfall) ? " and all its subclaims" : ""), true);

            refreshContents(claim);
        }


        // Right click to demote
        if (e.getClick() == ClickType.RIGHT) {
            if (!claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.PROMOTE_DEMOTE)) {
                player.sendMessage(Utils.colour(ClaimPermission.PROMOTE_DEMOTE.getDenialMessage()));
                return;
            }

            if (target.getName().equalsIgnoreCase(player.getName())) {
                player.sendMessage(Utils.colour("&cYou can't change your own role"));
                return;
            }

            if (!ClaimRole.isRole1HigherThanRole2(playerRole, targetRole)) {
                player.sendMessage(Utils.colour("&cYou can't change the role of someone with the same role as you or higher"));
                return;
            }

            if (targetRole == ClaimRole.OWNER) {
                player.sendMessage("&cYou can't demote the owner of the claim");
                return;
            }

            if (targetRole == ClaimRole.GUEST) {
                player.sendMessage(Utils.colour("&cYou can't demote someone from the lowest role. To untrust them from the claim use &o/untrust " + target.getName()));
                return;
            }

            ClaimRole nextRoleDown = ClaimRole.getLowerRole(targetRole);

            claim.setClaimRole(target.getUniqueId(), nextRoleDown);
            player.sendMessage(Utils.colour("&aYou demoted " + target.getName() + " to the " + nextRoleDown + " role on this claim"));

            if (target.isOnline()) {
                target.getPlayer().sendMessage(Utils.colour("&a" + player.getName() + " demoted you to the " + nextRoleDown + " role on their claim"));
            }

            if (waterfall) {
                for (Claim sub : claim.children) {
                    sub.setClaimRole(target.getUniqueId(), nextRoleDown);
                }
            }

            // Log it
            MemberModificationLogs.logToFile(player.getName() + " demoted " + target.getName() + " to " + nextRoleDown + " on claim " + claim.id + ((waterfall) ? " and all its subclaims" : ""), true);

            refreshContents(claim);
        }
    }
}
