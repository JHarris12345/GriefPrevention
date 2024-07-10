package me.ryanhamshire.GriefPrevention.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static GriefPrevention plugin = GriefPrevention.getInstance();

    public static String colour(String string) {
        Pattern pattern = Pattern.compile("&?#[A-Fa-f0-9]{6}");
        Matcher matcher = pattern.matcher(string);
        String output = ChatColor.translateAlternateColorCodes('&', string);

        while (matcher.find()) {
            String color = string.substring(matcher.start(), matcher.end());
            output = output.replace(color, "" + net.md_5.bungee.api.ChatColor.of(color.replace("&", "")));
        }

        return output;
    }

    public static void giveHeadItemBase64Value(ItemMeta headMeta, String base64Value) {
        SkullMeta skullMeta = (SkullMeta) headMeta;
        String uuidString = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
        UUID uuid = UUID.fromString(uuidString);
        PlayerProfile profile = Bukkit.createProfile(uuid);
        ProfileProperty textureProperty = new ProfileProperty("textures", base64Value);
        profile.setProperty(textureProperty);
        skullMeta.setPlayerProfile(profile);
    }
}
