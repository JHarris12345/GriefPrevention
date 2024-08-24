package me.ryanhamshire.GriefPrevention.Inventories;

import com.sk89q.util.StringUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Inventories.InventoryFiles.SettingsGUIFile;
import me.ryanhamshire.GriefPrevention.logs.PermissionChangeLogs;
import me.ryanhamshire.GriefPrevention.logs.SettingsChangeLogs;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimPermission;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimSetting;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimSettingValue;
import me.ryanhamshire.GriefPrevention.objects.enums.GUIBackgroundType;
import me.ryanhamshire.GriefPrevention.utils.Utils;
import me.ryanhamshire.GriefPrevention.utils.legacies.ItemFlagUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.codehaus.plexus.util.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.UUID;

public class SettingsGUI extends GUI implements InventoryHolder, ClaimMenu {
    private static GriefPrevention plugin = GriefPrevention.getInstance();
    private Inventory inv;
    private Claim claim;
    private Player player;
    private boolean waterfall;


    public SettingsGUI(Claim claim, boolean waterfall, Player player) {
        this.inv = Bukkit.createInventory(this, super.getNeededSize(ClaimSetting.values().length), Utils.colour(SettingsGUIFile.get().getString("Title")));
        this.claim = claim;
        this.player = player;
        this.waterfall = waterfall;

        this.addContents(claim);
        claim.setOwnerRanks(true);
    }

    private void addContents(Claim claim) {
        super.addContents(inv, true, GUIBackgroundType.FILLED);

        int slot = 0;
        for (ClaimSetting claimSetting : ClaimSetting.values()) {
            Material material = Material.valueOf(SettingsGUIFile.get().getString("Settings." + claimSetting.name() + ".Item"));
            String displayName = Utils.colour(SettingsGUIFile.get().getString("Settings." + claimSetting.name() + ".Name"));
            Integer amount = 1;
            String base64Value = null;
            String owningPlayerName = null;
            ArrayList<String> lore = new ArrayList<>();

            for (String loreLine : SettingsGUIFile.get().getStringList("Settings." + claimSetting.name() + ".Lore")) {
                if (claimSetting != ClaimSetting.FORCED_TIME && claimSetting != ClaimSetting.FORCED_WEATHER) {
                    lore.add(Utils.colour(loreLine));

                } else if (claimSetting == ClaimSetting.FORCED_TIME) {
                    lore.add(Utils.colour(loreLine.replace("%forcedTime%", claim.getForcedTimeSetting().getReadable())));

                } else if (claimSetting == ClaimSetting.FORCED_WEATHER) {
                    lore.add(Utils.colour(loreLine.replace("%forcedWeather%", claim.getForcedWeatherSetting().getReadable())));
                }
            }

            if (claimSetting != ClaimSetting.FORCED_TIME && claimSetting != ClaimSetting.FORCED_WEATHER) {
                if (claim.isSettingEnabled(claimSetting)) {
                    for (String loreLine : SettingsGUIFile.get().getStringList("EnabledLore")) {
                        lore.add(Utils.colour(loreLine));
                    }
                } else {
                    for (String loreLine : SettingsGUIFile.get().getStringList("DisabledLore")) {
                        lore.add(Utils.colour(loreLine));
                    }
                }
            }

            if (SettingsGUIFile.get().isSet("Settings." + claimSetting.name() + ".Base64Value")) {
                base64Value = SettingsGUIFile.get().getString("Settings." + claimSetting.name() + ".Base64Value");
            }
            if (SettingsGUIFile.get().isSet("Settings." + claimSetting.name() + ".OwningPlayer")) {
                owningPlayerName = SettingsGUIFile.get().getString("Settings." + claimSetting.name() + ".OwningPlayer");
            }

            if (!claim.isSettingUnlocked(claimSetting)) {
                lore.add("");
                lore.add(Utils.colour("&4&lToggling LOCKED"));
                lore.add(Utils.colour("&cThe claim owner requires the " + plugin.getRankFromPermission(claimSetting.getUnlockPermission())));
                lore.add(Utils.colour("&crank to unlock toggling this setting"));
            }

            ItemStack item = Utils.createItemStack(material, base64Value, owningPlayerName, displayName, lore, amount);
            ItemMeta meta = item.getItemMeta();

            meta.addItemFlags(ItemFlagUtils.of("HIDE_ADDITIONAL_TOOLTIP"));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "setting"), PersistentDataType.STRING, claimSetting.name());

            item.setItemMeta(meta);

            inv.setItem(slot, item);
            slot++;
        }
    }


    public void refreshContents(Claim claim) {
        for (UUID uuid : claim.getClaimMembers(true).keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof SettingsGUI) {
                player.openInventory(new SettingsGUI(claim, waterfall, player).getInventory());
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
        backButtonClickMethod(e, claim, waterfall);

        // Prevent an admin modifying the claim
        if (isAdminClicking(player, e, claim)) return;

        if (e.getCurrentItem() == null) return;
        String settingName = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "setting"), PersistentDataType.STRING);

        // If they did not click on a setting icon
        if (settingName == null) return;
        ClaimSetting setting = ClaimSetting.valueOf(settingName);

        if (!claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.MANAGE_SETTINGS)) {
            player.sendMessage(Utils.colour(ClaimPermission.MANAGE_SETTINGS.getDenialMessage()));
            return;
        }

        // If the setting hasn't been unlocked OR unlock it
        if (e.getClick() != ClickType.RIGHT) {
            if (!claim.isSettingUnlocked(setting)) {
                player.sendMessage(Utils.colour("&cYou must unlock this setting before you can toggle it"));
                return;
            }

        } /*else {
            if (!claim.isSettingUnlocked(setting)) {
                int iCoinsBalance = Integer.parseInt(PlaceholderAPI.setPlaceholders(player, "%icore_insanitypoints_iCoins%"));
                if (iCoinsBalance < setting.getUnlockCost()) {
                    player.sendMessage(Utils.colour("&cYou don't have enough iCoins to unlock this setting. Get iCoins from the &o/shop"));
                    return;
                }

                claim.unlockClaimSetting(setting);
                player.sendMessage(Utils.colour("&aYou just unlocked the " + settingName + " setting for this claim"));
                Utils.sendConsoleCommand("ipoints remove " + player.getName() + " iCoins " + setting.getUnlockCost());
                refreshContents(claim);

                long id = (claim.parent == null) ? claim.id : claim.parent.id;
                SettingsChangeLogs.logToFile(player.getName() + " unlocked the " + settingName + " setting" +
                        " for claim " + id, true);
                return;
            }
        }*/

        // Handling for all the true / false settings and then handling for the weather and time settings
        if (setting != ClaimSetting.FORCED_TIME && setting != ClaimSetting.FORCED_WEATHER) {
            if (claim.isSettingEnabled(setting)) {
                claim.disableSetting(setting);
                SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to FALSE" +
                        " for claim " + claim.id + ((waterfall) ? " and all its subclaims" : ""), true);

                if (waterfall) {
                    for (Claim sub : claim.children) {
                        sub.disableSetting(setting);
                    }
                }

            } else {
                claim.enableSetting(setting);
                SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to TRUE" +
                        " for claim " + claim.id + ((waterfall) ? " and all its subclaims" : ""), true);

                if (waterfall) {
                    for (Claim sub : claim.children) {
                        sub.enableSetting(setting);
                    }
                }
            }

        } else if (setting == ClaimSetting.FORCED_TIME) {
            ClaimSettingValue currentTime = claim.getForcedTimeSetting();
            switch (currentTime) {
                case NONE:
                    claim.setForcedTimeSetting(ClaimSettingValue.DAY);
                    SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to DAY" +
                            " for claim " + claim.id + ((waterfall) ? " and all its subclaims" : ""), true);

                    if (waterfall) {
                        for (Claim sub : claim.children) {
                            sub.setForcedTimeSetting(ClaimSettingValue.DAY);
                        }
                    }
                    break;

                case DAY:
                    claim.setForcedTimeSetting(ClaimSettingValue.NIGHT);
                    SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to NIGHT" +
                            " for claim " + claim.id + ((waterfall) ? " and all its subclaims" : ""), true);

                    if (waterfall) {
                        for (Claim sub : claim.children) {
                            sub.setForcedTimeSetting(ClaimSettingValue.NIGHT);
                        }
                    }
                    break;

                case NIGHT:
                    claim.setForcedTimeSetting(ClaimSettingValue.NONE);
                    SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to NONE" +
                            " for claim " + claim.id + ((waterfall) ? " and all its subclaims" : ""), true);

                    if (waterfall) {
                        for (Claim sub : claim.children) {
                            sub.setForcedTimeSetting(ClaimSettingValue.NONE);
                        }
                    }
                    break;
            }

        } else if (setting == ClaimSetting.FORCED_WEATHER) {
            ClaimSettingValue currentWeather = claim.getForcedWeatherSetting();
            switch (currentWeather) {
                case NONE:
                    claim.setForcedWeatherSetting(ClaimSettingValue.SUNNY);
                    SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to SUNNY" +
                            " for claim " + claim.id + ((waterfall) ? " and all its subclaims" : ""), true);

                    if (waterfall) {
                        for (Claim sub : claim.children) {
                            sub.setForcedWeatherSetting(ClaimSettingValue.SUNNY);
                        }
                    }
                    break;

                case SUNNY:
                    claim.setForcedWeatherSetting(ClaimSettingValue.RAINY);
                    SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to RAINY" +
                            " for claim " + claim.id + ((waterfall) ? " and all its subclaims" : ""), true);

                    if (waterfall) {
                        for (Claim sub : claim.children) {
                            sub.setForcedWeatherSetting(ClaimSettingValue.RAINY);
                        }
                    }
                    break;

                case RAINY:
                    claim.setForcedWeatherSetting(ClaimSettingValue.NONE);
                    SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to NONE" +
                            " for claim " + claim.id + ((waterfall) ? " and all its subclaims" : ""), true);

                    if (waterfall) {
                        for (Claim sub : claim.children) {
                            sub.setForcedWeatherSetting(ClaimSettingValue.NONE);
                        }
                    }
                    break;
            }
        }

        refreshContents(claim);
    }
}

