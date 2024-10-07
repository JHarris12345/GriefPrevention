/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention.listeners;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.data.DataStore;
import me.ryanhamshire.GriefPrevention.events.ProtectDeathDropsEvent;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import me.ryanhamshire.GriefPrevention.objects.PendingItemProtection;
import me.ryanhamshire.GriefPrevention.objects.PlayerData;
import me.ryanhamshire.GriefPrevention.objects.TextMode;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimPermission;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimSetting;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimsMode;
import me.ryanhamshire.GriefPrevention.objects.enums.Messages;
import me.ryanhamshire.GriefPrevention.utils.legacies.EntityTypeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalExitEvent;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

//handles events related to entities
public class EntityEventHandler implements Listener {
    //convenience reference for the singleton datastore
    private final DataStore dataStore;
    private final GriefPrevention instance;

    public EntityEventHandler(DataStore dataStore, GriefPrevention plugin) {
        this.dataStore = dataStore;
        instance = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityFormBlock(EntityBlockFormEvent event) {
        if (!GriefPrevention.plugin.claimsEnabledForWorld(event.getBlock().getWorld())) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        Claim claim = this.dataStore.getClaimAt(event.getBlock().getLocation(), false, null);

        if (claim == null) return;

        if (!claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.PLACE_BLOCKS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityChangeBLock(EntityChangeBlockEvent event) {
        if (!GriefPrevention.plugin.claimsEnabledForWorld(event.getBlock().getWorld())) return;

        Claim claim = this.dataStore.getClaimAt(event.getBlock().getLocation(), false, null);
        if (claim == null) return;

        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            // Don't allow crops to be trampled, except by a player with build permission
            if (event.getTo() == Material.DIRT && event.getBlock().getType() == Material.FARMLAND) {
                if (!claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.BREAK_BLOCKS)) {
                    event.setCancelled(true);
                }
            }

        } else {
            // Handle projectiles changing blocks: TNT ignition, tridents knocking down pointed dripstone, etc.
            if (event.getEntity() instanceof Projectile) {
                handleProjectileChangeBlock(event, (Projectile) event.getEntity());
            }

            // Prevent breaking lily pads via collision with a boat.
            if (event.getEntity() instanceof Vehicle && !event.getEntity().getPassengers().isEmpty()) {
                Entity driver = event.getEntity().getPassengers().get(0);

                if (driver instanceof Player) {
                    if (!claim.hasClaimPermission(driver.getUniqueId(), ClaimPermission.BREAK_BLOCKS)) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    private void handleProjectileChangeBlock(EntityChangeBlockEvent event, Projectile projectile) {
        Block block = event.getBlock();
        Claim claim = this.dataStore.getClaimAt(block.getLocation(), false, null);

        // Wilderness rules
        if (claim == null) {
            // No modification in the wilderness in creative mode.
            if (instance.creativeRulesApply(block.getLocation()) || instance.config_claims_worldModes.get(block.getWorld()) == ClaimsMode.SurvivalRequiringClaims) {
                event.setCancelled(true);
                return;
            }

            // Unclaimed area is fair game.
            return;
        }

        ProjectileSource shooter = projectile.getShooter();

        if (shooter instanceof Player) {
            Supplier<String> denial = null;

            if (!claim.hasClaimPermission(((Player) shooter).getUniqueId(), ClaimPermission.BREAK_BLOCKS)) {
                denial = ClaimPermission.BREAK_BLOCKS::getDenialMessage;
            }

            // If the player cannot place the material being broken, disallow.
            if (denial != null) {
                // Unlike entities where arrows rebound and may cause multiple alerts,
                // projectiles lodged in blocks do not continuously re-trigger events.
                GriefPrevention.sendMessage((Player) shooter, TextMode.Err, denial.get());
                event.setCancelled(true);
            }

            return;
        }

        // Allow change if projectile was shot by a dispenser in the same claim.
        if (isBlockSourceInClaim(shooter, claim))
            return;

        // Prevent change in all other cases.
        event.setCancelled(true);
    }

    static boolean isBlockSourceInClaim(@Nullable ProjectileSource projectileSource, @Nullable Claim claim) {
        return projectileSource instanceof BlockProjectileSource &&
                GriefPrevention.plugin.dataStore.getClaimAt(((BlockProjectileSource) projectileSource).getBlock().getLocation(), false, claim) == claim;
    }

    //Used by "sand cannon" fix to ignore fallingblocks that fell through End Portals
    //This is largely due to a CB issue with the above event
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onFallingBlockEnterPortal(EntityPortalEnterEvent event) {
        if (event.getEntityType() != EntityType.FALLING_BLOCK)
            return;
        event.getEntity().removeMetadata("GP_FALLINGBLOCK", instance);
    }

    //Don't let people drop in TNT through end portals
    //Necessarily this shouldn't be an issue anyways since the platform is obsidian...
    @EventHandler(ignoreCancelled = true)
    void onTNTExitPortal(EntityPortalExitEvent event) {
        if (event.getEntityType() != EntityTypeUtils.of("TNT"))
            return;
        if (event.getTo().getWorld().getEnvironment() != Environment.THE_END)
            return;
        event.getEntity().remove();
    }

    //don't allow zombies to break down doors
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onZombieBreakDoor(EntityBreakDoorEvent event) {
        if (!GriefPrevention.plugin.config_zombiesBreakDoors) event.setCancelled(true);
    }

    //don't allow entities to trample crops
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityInteract(EntityInteractEvent event) {
        Material material = event.getBlock().getType();
        if (material == Material.FARMLAND) {
            if (!GriefPrevention.plugin.config_creaturesTrampleCrops) {
                event.setCancelled(true);
            }
            else {
                Entity rider = event.getEntity().getPassenger();
                if (rider != null && rider.getType() == EntityType.PLAYER) {
                    event.setCancelled(true);
                }
            }
        }
    }

    //when an item spawns...
    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        //if in a creative world, cancel the event (don't drop items on the ground)
        if (GriefPrevention.plugin.creativeRulesApply(event.getLocation())) {
            event.setCancelled(true);
        }

        //if item is on watch list, apply protection
        ArrayList<PendingItemProtection> watchList = GriefPrevention.plugin.pendingItemWatchList;
        Item newItem = event.getEntity();
        Long now = null;
        for (int i = 0; i < watchList.size(); i++) {
            PendingItemProtection pendingProtection = watchList.get(i);
            //ignore and remove any expired pending protections
            if (now == null) now = System.currentTimeMillis();
            if (pendingProtection.expirationTimestamp < now) {
                watchList.remove(i--);
                continue;
            }
            //skip if item stack doesn't match
            if (pendingProtection.itemStack.getAmount() != newItem.getItemStack().getAmount() ||
                    pendingProtection.itemStack.getType() != newItem.getItemStack().getType()) {
                continue;
            }

            //skip if new item location isn't near the expected spawn area
            Location spawn = event.getLocation();
            Location expected = pendingProtection.location;
            if (!spawn.getWorld().equals(expected.getWorld()) ||
                    spawn.getX() < expected.getX() - 5 ||
                    spawn.getX() > expected.getX() + 5 ||
                    spawn.getZ() < expected.getZ() - 5 ||
                    spawn.getZ() > expected.getZ() + 5 ||
                    spawn.getY() < expected.getY() - 15 ||
                    spawn.getY() > expected.getY() + 3) {
                continue;
            }

            //otherwise, mark item with protection information
            newItem.setMetadata("GP_ITEMOWNER", new FixedMetadataValue(GriefPrevention.plugin, pendingProtection.owner));

            //and remove pending protection data
            watchList.remove(i);
            break;
        }
    }

    //when an experience bottle explodes...
    @EventHandler(priority = EventPriority.LOWEST)
    public void onExpBottle(ExpBottleEvent event) {
        //if in a creative world, cancel the event (don't drop exp on the ground)
        if (GriefPrevention.plugin.creativeRulesApply(event.getEntity().getLocation())) {
            event.setExperience(0);
        }
    }

    //when a creature spawns...
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntitySpawn(CreatureSpawnEvent event) {
        Claim claim = GriefPrevention.plugin.dataStore.getClaimAt(event.getLocation(), true, null);
        if (claim != null && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            // If the entity is a monster
            if (event.getEntity() instanceof Monster || event.getEntity() instanceof Phantom
                    || event.getEntity() instanceof Ghast || event.getEntity() instanceof Slime) {
                if (!claim.isSettingEnabled(ClaimSetting.NATURAL_MONSTER_SPAWNS)) {
                    event.setCancelled(true);
                    return;
                }
            }

            // If the entity is not a monster
            if (event.getEntity() instanceof Mob && !(event.getEntity() instanceof Monster)
                    && !(event.getEntity() instanceof Phantom) && !(event.getEntity() instanceof Ghast) && !(event.getEntity() instanceof Slime)) {
                if (!claim.isSettingEnabled(ClaimSetting.NATURAL_ANIMAL_SPAWNS)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        //these rules apply only to creative worlds
        if (!GriefPrevention.plugin.creativeRulesApply(event.getLocation())) return;

        //chicken eggs and breeding could potentially make a mess in the wilderness, once griefers get involved
        SpawnReason reason = event.getSpawnReason();
        if (reason != SpawnReason.SPAWNER_EGG && reason != SpawnReason.BUILD_IRONGOLEM && reason != SpawnReason.BUILD_SNOWMAN && event.getEntityType() != EntityType.ARMOR_STAND) {
            event.setCancelled(true);
            return;
        }

        //otherwise, just apply the limit on total entities per claim (and no spawning in the wilderness!)
        if (claim == null || claim.allowMoreEntities(true) != null) {
            event.setCancelled(true);
            return;
        }
    }

    //when a painting is broken
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onHangingBreak(HangingBreakEvent event) {
        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.plugin.claimsEnabledForWorld(event.getEntity().getWorld())) return;

        // Ignore item frames as we have a separate event for them
        if (event.getEntity() instanceof ItemFrame) return;

        //Ignore cases where itemframes should break due to no supporting blocks
        if (event.getCause() == RemoveCause.PHYSICS) return;

        Claim claim = this.dataStore.getClaimAt(event.getEntity().getLocation(), false, null);
        if (claim == null) return;

        //explosions don't destroy hangings
        if (event.getCause() == RemoveCause.EXPLOSION) {
            event.setCancelled(true);
            return;
        }

        //only allow players to break paintings, not anything else (like water and explosions)
        if (!(event instanceof HangingBreakByEntityEvent)) {
            event.setCancelled(true);
            return;
        }

        HangingBreakByEntityEvent entityEvent = (HangingBreakByEntityEvent) event;

        //who is removing it?
        Entity remover = entityEvent.getRemover();

        //again, making sure the breaker is a player
        if (remover.getType() != EntityType.PLAYER) {
            event.setCancelled(true);
            return;
        }

        //if the player doesn't have build permission, don't allow the breakage
        Player playerRemover = (Player) entityEvent.getRemover();

        if (!claim.hasClaimPermission(playerRemover.getUniqueId(), ClaimPermission.BREAK_BLOCKS)) {
            event.setCancelled(true);
            GriefPrevention.sendMessage(playerRemover, TextMode.Err, ClaimPermission.BREAK_BLOCKS.getDenialMessage());
        }
    }

    //when a painting is placed...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPaintingPlace(HangingPlaceEvent event) {
        //don't track in worlds where claims are not enabled
        if (!GriefPrevention.plugin.claimsEnabledForWorld(event.getBlock().getWorld())) return;

        Claim claim = this.dataStore.getClaimAt(event.getEntity().getLocation(), false, null);
        if (claim == null) return;

        //if the player doesn't have permission, don't allow the placement
        if (!claim.hasClaimPermission(event.getPlayer().getUniqueId(), ClaimPermission.BREAK_BLOCKS)) {
            event.setCancelled(true);
            GriefPrevention.sendMessage(event.getPlayer(), TextMode.Err, ClaimPermission.BREAK_BLOCKS.getDenialMessage());
        }
    }

    // Prevent item frame breaking
    @EventHandler
    public void onItemFrame(HangingBreakByEntityEvent e) {
        if (!(e.getEntity() instanceof ItemFrame)) return;

        // Always protect from explosions
        if (e.getCause() == RemoveCause.EXPLOSION) {
            e.setCancelled(true);
            return;
        }

        Player player = null;
        if (e.getRemover() instanceof Player) {
            player = (Player) e.getRemover();
        }

        if (player == null) {
            if (e.getRemover() instanceof Projectile) {
                Projectile projectile = (Projectile) e.getRemover();
                if (projectile.getShooter() instanceof Player) {
                    player = (Player) projectile.getShooter();
                }
            }
        }

        if (player == null) return;

        Claim claim = this.dataStore.getClaimAt(e.getEntity().getLocation(), false, null);
        if (claim == null) return;

        if (!claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.MODIFY_ITEM_FRAMES)) {
            e.setCancelled(true);
            GriefPrevention.sendMessage(player, TextMode.Err, ClaimPermission.MODIFY_ITEM_FRAMES.getDenialMessage());
        }
    }

    // Prevent item frame breaking from obstructions
    @EventHandler
    public void onItemFrame(HangingBreakEvent e) {
        if (e.getCause() != RemoveCause.OBSTRUCTION) return;
        if (!(e.getEntity() instanceof ItemFrame)) return;

        e.setCancelled(true);
    }

    @EventHandler
    public void onEntityPickUpItem(@NotNull EntityPickupItemEvent event) {
        // Hostiles are allowed to equip death drops to preserve the challenge of item retrieval.
        if (event.getEntity() instanceof Monster) return;

        Player player = null;
        if (event.getEntity() instanceof Player) {
            player = (Player) event.getEntity();
        }

        //FEATURE: Lock dropped items to player who dropped them.
        protectLockedDrops(event, player);

        // FEATURE: Protect freshly-spawned players from PVP.
        preventPvpSpawnCamp(event, player);
    }

    private void protectLockedDrops(@NotNull EntityPickupItemEvent event, @Nullable Player player) {
        Item item = event.getItem();
        List<MetadataValue> data = item.getMetadata("GP_ITEMOWNER");

        // Ignore absent or invalid data.
        if (data.isEmpty() || !(data.get(0).value() instanceof UUID ownerID)) return;

        // Get owner from stored UUID.
        OfflinePlayer owner = instance.getServer().getOfflinePlayer(ownerID);

        // Owner must be online and can pick up their own drops.
        if (!owner.isOnline() || Objects.equals(player, owner)) return;

        PlayerData playerData = this.dataStore.getPlayerData(ownerID);

        // If drops are unlocked, allow pick up.
        if (playerData.dropsAreUnlocked) return;

        // Block pick up.
        event.setCancelled(true);

        // Non-players (dolphins, allays) do not need to generate prompts.
        if (player == null) {
            return;
        }

        // If the owner hasn't been instructed how to unlock, send explanatory messages.
        if (!playerData.receivedDropUnlockAdvertisement) {
            GriefPrevention.sendMessage(owner.getPlayer(), TextMode.Instr, Messages.DropUnlockAdvertisement);
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.PickupBlockedExplanation, GriefPrevention.lookupPlayerName(ownerID));
            playerData.receivedDropUnlockAdvertisement = true;
        }
    }

    private void preventPvpSpawnCamp(@NotNull EntityPickupItemEvent event, @Nullable Player player) {
        // This is specific to players in pvp worlds.
        if (player == null || !instance.pvpRulesApply(player.getWorld())) return;

        //if we're preventing spawn camping and the player was previously empty handed...
        if (instance.config_pvp_protectFreshSpawns && (instance.getItemInHand(player, EquipmentSlot.HAND).getType() == Material.AIR)) {
            //if that player is currently immune to pvp
            PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
            if (playerData.pvpImmune) {
                //if it's been less than 10 seconds since the last time he spawned, don't pick up the item
                long now = Calendar.getInstance().getTimeInMillis();
                long elapsedSinceLastSpawn = now - playerData.lastSpawn;
                if (elapsedSinceLastSpawn < 10000) {
                    event.setCancelled(true);
                    return;
                }

                //otherwise take away his immunity. he may be armed now.  at least, he's worth killing for some loot
                playerData.pvpImmune = false;
                GriefPrevention.sendMessage(player, TextMode.Warn, Messages.PvPImmunityEnd);
            }
        }
    }

    // Prevent wind charges doing anything in claims the player doesn't have permission on (wind charges can open doors etc)
    @EventHandler
    public void onWindCharge(EntityExplodeEvent e) {
        if (e.getEntityType() != EntityType.WIND_CHARGE) return;
        if (!(e.getEntity() instanceof Projectile projectile)) return;
        if (!(projectile.getShooter() instanceof Player player)) return;

        Claim claim = dataStore.getClaimAt(e.getLocation(), true, null);
        if (claim == null) return;

        if (!claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.INTERACT)) {
            e.setCancelled(true);
        }
    }
}
