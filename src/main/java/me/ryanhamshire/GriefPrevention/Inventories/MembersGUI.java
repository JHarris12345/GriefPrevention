package me.ryanhamshire.GriefPrevention.Inventories;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Inventories.InventoryFiles.GUISettingsFile;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MembersGUI extends GUI implements InventoryHolder, ClaimMenu {
    // 5 rows of heads per page. The bottom row is always reserved for the back / page buttons,
    // so heads can never overwrite them no matter how many members the claim has
    private static final int MEMBERS_PER_PAGE = 45;

    private static GriefPrevention plugin = GriefPrevention.getInstance();
    private Inventory inv;
    private Claim claim;
    private boolean waterfall;
    private int page; // 0-based
    private int totalPages;

    public MembersGUI(Claim claim, boolean waterfall) {
        this(claim, waterfall, 0);
    }

    public MembersGUI(Claim claim, boolean waterfall, int page) {
        this.claim = claim;
        this.waterfall = waterfall;

        // Members whose name can't be resolved (never joined this server) have always been hidden from
        // the GUI. Drop them before paginating so they don't leave empty slots in the middle of a page
        List<UUID> members = new ArrayList<>();
        for (UUID memberUUID : claim.getClaimMembers(true).keySet()) {
            if (Bukkit.getOfflinePlayer(memberUUID).getName() != null) members.add(memberUUID);
        }
        this.totalPages = Math.max(1, (int) Math.ceil((double) members.size() / MEMBERS_PER_PAGE));
        this.page = Math.max(0, Math.min(page, this.totalPages - 1));

        int start = this.page * MEMBERS_PER_PAGE;
        List<UUID> pageMembers = members.subList(start, Math.min(start + MEMBERS_PER_PAGE, members.size()));

        // getNeededSize always leaves one spare row under the heads, which is the button row
        this.inv = Bukkit.createInventory(this, getNeededSize(pageMembers.size()), Utils.colour(MembersGUIFile.get().getString("Title")));

        this.addContents(pageMembers);
    }

    private void addContents(List<UUID> pageMembers) {
        super.addContents(inv, true, GUIBackgroundType.FILLED);

        int slot = 0;
        for (UUID memberUUID : pageMembers) {
            if (slot >= inv.getSize() - 9) break; // Never spill into the button row

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

        // Page buttons on the bottom row: previous bottom-left, next bottom-right, back stays in the middle
        if (page > 0) inv.setItem(previousPageSlot(), pageButton("PageBackButton", "&6Previous Page"));
        if (page < totalPages - 1) inv.setItem(nextPageSlot(), pageButton("PageForwardButton", "&aNext Page"));
    }

    private int previousPageSlot() {
        return inv.getSize() - 9;
    }

    private int nextPageSlot() {
        return inv.getSize() - 1;
    }

    private ItemStack pageButton(String configKey, String defaultName) {
        Material material = Material.matchMaterial(GUISettingsFile.get().getString(configKey + ".Item", "STONE_BUTTON"));
        ItemStack item = new ItemStack(material == null ? Material.STONE_BUTTON : material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Utils.colour(GUISettingsFile.get().getString(configKey + ".Name", defaultName)));
        meta.setLore(Collections.singletonList(Utils.colour("&7Page " + (page + 1) + " of " + totalPages)));
        item.setItemMeta(meta);
        return item;
    }

    public void refreshContents(Claim claim) {
        for (UUID uuid : claim.getClaimMembers(true).keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof MembersGUI) {
                // Reopen on the page they were already looking at
                player.openInventory(new MembersGUI(claim, waterfall, ((MembersGUI) holder).page).getInventory());
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

        // Ignore clicks in the player's own inventory underneath the GUI
        if (e.getRawSlot() >= inv.getSize() || e.getCurrentItem() == null) return;

        // Page buttons
        if (e.getSlot() == previousPageSlot() && page > 0) {
            player.openInventory(new MembersGUI(claim, waterfall, page - 1).getInventory());
            return;
        }
        if (e.getSlot() == nextPageSlot() && page < totalPages - 1) {
            player.openInventory(new MembersGUI(claim, waterfall, page + 1).getInventory());
            return;
        }

        // Back to menu button method
        if (clickedBackButton(e)) {
            backButtonClickMethod(e, this.claim, waterfall);
            return;
        }

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
