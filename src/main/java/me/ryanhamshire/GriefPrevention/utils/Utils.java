package me.ryanhamshire.GriefPrevention.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimRole;
import me.ryanhamshire.GriefPrevention.utils.legacies.ItemFlagUtils;
import me.ryanhamshire.GriefPrevention.utils.legacies.ParticleUtils;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
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
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Utils {

    private static GriefPrevention plugin = GriefPrevention.getInstance();

    private static HashMap<UUID, String> playerNameCache = new HashMap<>(); // A map of UUIDs and the player name so we can get offline player names fast
    private static HashMap<String, World> worldMap = new HashMap<>(); // A cache for getting worlds from their string names
    private static HashMap<UUID, Long> timedMessages = new HashMap<>(); // A map of UUID and the last time a message was sent so we know if a long enough time has passed to send another

    public static String getOfflinePlayerNameFast(OfflinePlayer player) {
        String name = playerNameCache.getOrDefault(player.getUniqueId(), null);
        if (name != null) return name;

        name = player.getName();
        playerNameCache.put(player.getUniqueId(), name);

        return name;
    }

    public static void sendTimedMessage(Player player, String message, long waitTimeMillis) {
        long lastSendTime = timedMessages.getOrDefault(player.getUniqueId(), 0L);
        long nextAllowedSend = lastSendTime + waitTimeMillis;

        if (System.currentTimeMillis() < nextAllowedSend) return;

        timedMessages.put(player.getUniqueId(), System.currentTimeMillis());
        player.sendMessage(Utils.colour(message));
    }

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

    public static List<String> tabComplete(String input, List<String> list) {
        return new ArrayList<>(list).stream()
                .filter(s -> s.toLowerCase().startsWith(input.toLowerCase())).collect(Collectors.toList());
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

    public static boolean isOutsideBorder(double x, double z, World world) {
        double borderCenterX = world.getWorldBorder().getCenter().getX();
        double borderCenterZ = world.getWorldBorder().getCenter().getZ();
        double borderRadius = world.getWorldBorder().getSize() / 2;

        double maxX = borderCenterX + borderRadius;
        double minX = borderCenterX - borderRadius;

        double maxZ = borderCenterZ + borderRadius;
        double minZ = borderCenterZ - borderRadius;

        if (x > maxX || x < minX) return true;
        if (z > maxZ || z < minZ) return true;

        return false;
    }

    // Returns true if page buttons were sent and false if not
    public static boolean sendPageButtons(CommandSender sender, int pagesNeeded, int pageNumber, String nextPageCommandWithSlash, String backPageCommandWithSlash, boolean addSpace) {
        boolean sentButtons = false;
        if (pagesNeeded > 1 && addSpace) sender.sendMessage("");

        boolean java = (sender instanceof Player player && !isPlayerBedrock(player.getUniqueId()));

        if (pagesNeeded > 1 && pageNumber == 1) {
            if (java) {
                sender.sendMessage(Utils.createClickableText("&7Next Page [»]", "Click me", nextPageCommandWithSlash));
            }
            sentButtons = true;
        }

        if (pageNumber > 1 && pageNumber < pagesNeeded) {
            if (java) {
                sender.sendMessage(Utils.createClickableText("&7[«] Previous Page", "Click me", backPageCommandWithSlash),
                        new TextComponent("         "),
                        Utils.createClickableText("&7Next Page [»]", "Click me", nextPageCommandWithSlash));
            }
            sentButtons = true;
        }

        if (pageNumber == pagesNeeded && pagesNeeded != 1) {
            if (java) {
                sender.sendMessage(Utils.createClickableText("&7[«] Previous Page", "Click me", backPageCommandWithSlash));
            }
            sentButtons = true;
        }

        // For bedrock players we send the page command but replace the page number with (page)
        if (sentButtons && !java) {
            StringBuilder commandNoPageNumber = new StringBuilder();
            String[] words = nextPageCommandWithSlash.split(" ");

            for (int i=0; i<words.length; i++) {
                if (i != words.length-1) {
                    commandNoPageNumber.append(words[i]);
                } else {
                    commandNoPageNumber.append("(page)");
                }
            }

            sender.sendMessage(Utils.colour("&7" + commandNoPageNumber));
        }

        return sentButtons;
    }

    // Set commandWithSlash to null for just hover text
    public static BaseComponent createClickableText(String text, String hoverText, String commandWithSlash) {
        TextComponent textComponent = new TextComponent();

        textComponent.setText(Utils.colour(text));
        textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Utils.colour(hoverText))));
        if (commandWithSlash != null) textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, commandWithSlash));

        return textComponent;
    }
}
