package me.ryanhamshire.GriefPrevention.objects.enums;

import me.ryanhamshire.GriefPrevention.Inventories.InventoryFiles.RolePermissionsGUIFile;
import me.ryanhamshire.GriefPrevention.Inventories.InventoryFiles.SettingsGUIFile;

public enum ClaimSetting {

    LEAF_DECAY(ClaimSettingValue.TRUE),
    VINE_GROWTH(ClaimSettingValue.TRUE),
    CROP_GROWTH(ClaimSettingValue.TRUE),
    GRASS_SPREAD(ClaimSettingValue.TRUE),
    ICE_FORM(ClaimSettingValue.TRUE),
    ICE_MELT(ClaimSettingValue.TRUE),
    SNOW_FORM(ClaimSettingValue.TRUE),
    SNOW_MELT(ClaimSettingValue.TRUE),
    CORAL_DRY(ClaimSettingValue.TRUE),
    COPPER_WEATHERING(ClaimSettingValue.TRUE),
    NATURAL_MONSTER_SPAWNS(ClaimSettingValue.TRUE),
    NATURAL_ANIMAL_SPAWNS(ClaimSettingValue.TRUE),
    FORCED_TIME(ClaimSettingValue.NONE),
    FORCED_WEATHER(ClaimSettingValue.NONE);

    private final ClaimSettingValue settingDefaultValue;

    ClaimSetting(ClaimSettingValue settingDefaultValue) {this.settingDefaultValue = settingDefaultValue;}

    public ClaimSettingValue getDefaultValue() {
        return settingDefaultValue;
    }

    public int getUnlockCost() {
        return (SettingsGUIFile.get().getInt("Settings." + this.name() + ".iCoins"));
    }

    public String getUnlockPermission() {
        return (SettingsGUIFile.get().getString("Settings." + this.name() + ".Permission"));
    }
}
