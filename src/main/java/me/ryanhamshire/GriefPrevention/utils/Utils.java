package me.ryanhamshire.GriefPrevention.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimRole;
import me.ryanhamshire.GriefPrevention.utils.legacies.ItemFlagUtils;
import me.ryanhamshire.GriefPrevention.utils.legacies.ParticleUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static GriefPrevention plugin = GriefPrevention.getInstance();

    private static HashMap<String, World> worldMap = new HashMap<>(); // A cache for getting worlds from their string names

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

    public static World getWorld(String worldName) {
        World world = worldMap.getOrDefault(worldName, null);
        if (world != null) return world;

        world = Bukkit.getWorld(worldName);
        worldMap.put(worldName, world);

        return world;
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

    public static boolean isPlayerBedrock(UUID uuid) {
        return  (uuid.toString().startsWith("00000000-0000-0000"));
    }

    public static void sendConsoleCommand(String commandNoSlash) {
        ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
        Bukkit.dispatchCommand(console, commandNoSlash);
    }

    public static ItemStack createItemStack(Material material, String base64Value, String owningPlayerName, String displayName, ArrayList<String> lore, int amount) {
        ItemStack item = new ItemStack(Material.GRASS_BLOCK);
        item.setType(material);
        item.setAmount(amount);

        // It is not a player head
        if (base64Value == null && owningPlayerName == null) {
            ItemMeta meta = item.getItemMeta();
            meta.setLore(lore);
            meta.setDisplayName(displayName);
            item.setItemMeta(meta);

            meta.addItemFlags(ItemFlag.HIDE_DYE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE,
                    ItemFlag.HIDE_ENCHANTS, ItemFlagUtils.of("HIDE_ADDITIONAL_TOOLTIP"), ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_PLACED_ON);

            return item;

            // It is a player head
        } else {
            SkullMeta headMeta = (SkullMeta) item.getItemMeta();
            headMeta.setLore(lore);
            headMeta.setDisplayName(displayName);

            // Base64Value
            if (owningPlayerName == null) {
                giveHeadItemBase64Value(headMeta, base64Value);
                item.setItemMeta(headMeta);
                return item;

                // Owning player
            } else {
                headMeta.setOwningPlayer(Bukkit.getOfflinePlayer(owningPlayerName));
                item.setItemMeta(headMeta);
                return item;
            }
        }
    }
    public static boolean hasPlayerMoved1Block (PlayerMoveEvent e) {
        int fromX = e.getFrom().getBlockX();
        int toX = e.getTo().getBlockX();
        int fromY = e.getFrom().getBlockY();
        int toY = e.getTo().getBlockY();
        int fromZ = e.getFrom().getBlockZ();
        int toZ = e.getTo().getBlockZ();

        return !(fromX == toX && fromZ == toZ && fromY == toY);
    }

    public static void showClaimOutline(Claim claim, Player player, int playerX, int playerY, int playerZ) {
        Color colour = (claim.parent == null) ? (claim.getPlayerRole(player.getUniqueId()) == ClaimRole.PUBLIC) ? Color.RED : (claim.ownerID.equals(player.getUniqueId()) ? Color.AQUA : Color.LIME) : Color.FUCHSIA;
        playerY += 1; // We want the particles to start at the player's head and not feet

        double minX = claim.getLesserBoundaryCorner().x;
        double maxX = claim.getGreaterBoundaryCorner().x + 1; // +1 because we need it to spawn on the block 1 further away as it appears on the inside face

        double minZ = claim.getLesserBoundaryCorner().z;
        double maxZ = claim.getGreaterBoundaryCorner().z + 1; // +1 because we need it to spawn on the block 1 further away as it appears on the inside face

        for (double x=minX; x<=maxX; x++) {
            for (double z=minZ; z<=maxZ; z++) {
                if (x != minX && x != maxX &&
                        z != minZ && z != maxZ) continue;

                // We don't want to spawn particles over X blocks away
                int maxDistance = 15;
                if (Math.abs(x - playerX) > maxDistance || Math.abs(z - playerZ) > maxDistance) continue;

                int maxY = 3;
                for (int y=playerY - maxY; y<=playerY + maxY; y++) {
                    Location location = new Location(player.getWorld(), x, y, z);
                    player.spawnParticle(ParticleUtils.of("DUST"), location, 5, new Particle.DustOptions(colour, 1));
                }
            }
        }
    }
}
