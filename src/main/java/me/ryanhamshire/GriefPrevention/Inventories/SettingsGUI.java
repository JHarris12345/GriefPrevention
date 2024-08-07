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
    private boolean waterfall;


    public SettingsGUI(Claim claim, boolean waterfall) {
        this.inv = Bukkit.createInventory(this, super.getNeededSize(ClaimSetting.values().length), Utils.colour(SettingsGUIFile.get().getString("Title")));
        this.claim = claim;
        this.waterfall = waterfall;

        this.addContents(claim);
    }

    private void addContents(Claim claim) {
        super.addContents(inv, true, GUIBackgroundType.FILLED);

        int slot = 0;
        for (String key : SettingsGUIFile.get().getConfigurationSection("Settings").getKeys(false)) {
            ClaimSetting claimSetting = ClaimSetting.valueOf(key);

            Material material = Material.valueOf(SettingsGUIFile.get().getString("Settings." + key + ".Item"));
            String displayName = Utils.colour(SettingsGUIFile.get().getString("Settings." + key + ".Name"));
            Integer amount = 1;
            String base64Value = null;
            String owningPlayerName = null;
            ArrayList<String> lore = new ArrayList<>();

            for (String loreLine : SettingsGUIFile.get().getStringList("Settings." + key + ".Lore")) {
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

            if (SettingsGUIFile.get().isSet("Settings." + key + ".Base64Value")) {
                base64Value = SettingsGUIFile.get().getString("Settings." + key + ".Base64Value");
            }
            if (SettingsGUIFile.get().isSet("Settings." + key + ".OwningPlayer")) {
                owningPlayerName = SettingsGUIFile.get().getString("Settings." + key + ".OwningPlayer");
            }

            int unlockCost = claimSetting.getUnlockCost();
            if (unlockCost > 0 && !claim.isSettingUnlocked(claimSetting)) {
                lore.add("");
                lore.add(Utils.colour("&4&lToggling LOCKED"));
                lore.add(Utils.colour("&c&oRight-Click &cto unlock the ability"));
                lore.add(Utils.colour("&cto toggle this for &l" + unlockCost + " iCoins"));
            }

            ItemStack item = Utils.createItemStack(material, base64Value, owningPlayerName, displayName, lore, amount);
            ItemMeta meta = item.getItemMeta();

            meta.addItemFlags(ItemFlagUtils.of("HIDE_ADDITIONAL_TOOLTIP"));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "setting"), PersistentDataType.STRING, key);

            item.setItemMeta(meta);

            inv.setItem(slot, item);
            slot++;
        }
    }


    public void refreshContents(Claim claim) {
        for (UUID uuid : claim.getClaimMembers(true).keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof SettingsGUI) {
                player.openInventory(new SettingsGUI(claim, waterfall).getInventory());
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
        if (isAdminClicking(player, e)) return;

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

        } else {
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
                return;
            }
        }

        // Handling for all the true / false settings and then handling for the weather and time settings
        if (setting != ClaimSetting.FORCED_TIME && setting != ClaimSetting.FORCED_WEATHER) {
            if (claim.isSettingEnabled(setting)) {
                claim.disableSetting(setting);
                SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to FALSE" +
                        " for claim " + claim.id, true);

                if (waterfall) {
                    for (Claim sub : claim.children) {
                        sub.disableSetting(setting);
                        SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to FALSE" +
                                " for claim " + sub.id, true);
                    }
                }

            } else {
                claim.enableSetting(setting);
                SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to TRUE" +
                        " for claim " + claim.id, true);

                if (waterfall) {
                    for (Claim sub : claim.children) {
                        sub.enableSetting(setting);
                        SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to TRUE" +
                                " for claim " + sub.id, true);
                    }
                }
            }

        } else if (setting == ClaimSetting.FORCED_TIME) {
            ClaimSettingValue currentTime = claim.getForcedTimeSetting();
            switch (currentTime) {
                case NONE:
                    claim.setForcedTimeSetting(ClaimSettingValue.DAY);
                    SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to DAY" +
                            " for claim " + claim.id, true);

                    if (waterfall) {
                        for (Claim sub : claim.children) {
                            sub.setForcedTimeSetting(ClaimSettingValue.DAY);
                            SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to DAY" +
                                    " for claim " + sub.id, true);
                        }
                    }
                    break;

                case DAY:
                    claim.setForcedTimeSetting(ClaimSettingValue.NIGHT);
                    SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to NIGHT" +
                            " for claim " + claim.id, true);

                    if (waterfall) {
                        for (Claim sub : claim.children) {
                            sub.setForcedTimeSetting(ClaimSettingValue.NIGHT);
                            SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to NIGHT" +
                                    " for claim " + sub.id, true);
                        }
                    }
                    break;

                case NIGHT:
                    claim.setForcedTimeSetting(ClaimSettingValue.NONE);
                    SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to NONE" +
                            " for claim " + claim.id, true);

                    if (waterfall) {
                        for (Claim sub : claim.children) {
                            sub.setForcedTimeSetting(ClaimSettingValue.NONE);
                            SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to NONE" +
                                    " for claim " + sub.id, true);
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
                            " for claim " + claim.id, true);

                    if (waterfall) {
                        for (Claim sub : claim.children) {
                            sub.setForcedWeatherSetting(ClaimSettingValue.SUNNY);
                            SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to SUNNY" +
                                    " for claim " + sub.id, true);
                        }
                    }
                    break;

                case SUNNY:
                    claim.setForcedWeatherSetting(ClaimSettingValue.RAINY);
                    SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to RAINY" +
                            " for claim " + claim.id, true);

                    if (waterfall) {
                        for (Claim sub : claim.children) {
                            sub.setForcedWeatherSetting(ClaimSettingValue.RAINY);
                            SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to RAINY" +
                                    " for claim " + sub.id, true);
                        }
                    }
                    break;

                case RAINY:
                    claim.setForcedWeatherSetting(ClaimSettingValue.NONE);
                    SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to NONE" +
                            " for claim " + claim.id, true);

                    if (waterfall) {
                        for (Claim sub : claim.children) {
                            sub.setForcedWeatherSetting(ClaimSettingValue.NONE);
                            SettingsChangeLogs.logToFile(player.getName() + " set the " + settingName + " setting to NONE" +
                                    " for claim " + sub.id, true);
                        }
                    }
                    break;
            }
        }

        refreshContents(claim);
    }
}

