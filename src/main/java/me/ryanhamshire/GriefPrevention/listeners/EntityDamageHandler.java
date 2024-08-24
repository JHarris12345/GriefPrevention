package me.ryanhamshire.GriefPrevention.listeners;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.data.DataStore;
import me.ryanhamshire.GriefPrevention.events.PreventPvPEvent;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import me.ryanhamshire.GriefPrevention.objects.PlayerData;
import me.ryanhamshire.GriefPrevention.objects.TextMode;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimPermission;
import me.ryanhamshire.GriefPrevention.objects.enums.Messages;
import me.ryanhamshire.GriefPrevention.utils.legacies.EntityTypeUtils;
import me.ryanhamshire.GriefPrevention.utils.legacies.PotionEffectTypeUtils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Donkey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Explosive;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Mule;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class EntityDamageHandler implements Listener {

    private static final Set<PotionEffectType> GRIEF_EFFECTS = Set.of(
            // Damaging effects
            PotionEffectTypeUtils.of("INSTANT_DAMAGE"),
            PotionEffectType.POISON,
            PotionEffectType.WITHER,
            // Effects that could remove entities from normally-secure pens
            PotionEffectTypeUtils.of("JUMP_BOOST"),
            PotionEffectType.LEVITATION
    );
    private static final Set<PotionEffectType> POSITIVE_EFFECTS = Set.of(
            PotionEffectType.ABSORPTION,
            PotionEffectType.CONDUIT_POWER,
            PotionEffectTypeUtils.of("RESISTANCE"),
            PotionEffectType.DOLPHINS_GRACE,
            PotionEffectTypeUtils.of("HASTE"),
            PotionEffectType.FIRE_RESISTANCE,
            PotionEffectTypeUtils.of("INSTANT_HEALTH"),
            PotionEffectType.HEALTH_BOOST,
            PotionEffectType.HERO_OF_THE_VILLAGE,
            PotionEffectTypeUtils.of("STRENGTH"),
            PotionEffectType.INVISIBILITY,
            PotionEffectTypeUtils.of("JUMP_BOOST"),
            PotionEffectType.LUCK,
            PotionEffectType.NIGHT_VISION,
            PotionEffectType.REGENERATION,
            PotionEffectType.SATURATION,
            PotionEffectType.SLOW_FALLING,
            PotionEffectType.SPEED,
            PotionEffectType.WATER_BREATHING
    );
    private final @NotNull DataStore dataStore;
    private final @NotNull GriefPrevention instance;
    private final @NotNull NamespacedKey luredByPlayer;

    public EntityDamageHandler(@NotNull DataStore dataStore, @NotNull GriefPrevention plugin) {
        this.dataStore = dataStore;
        instance = plugin;
        luredByPlayer = new NamespacedKey(plugin, "lured_by_player");
    }

    // Tag passive animals that can become aggressive so that we can tell whether they are hostile later
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityTarget(@NotNull EntityTargetEvent event) {
        if (!instance.claimsEnabledForWorld(event.getEntity().getWorld())) return;

        EntityType entityType = event.getEntityType();
        if (entityType != EntityType.HOGLIN && entityType != EntityType.POLAR_BEAR)
            return;

        if (event.getReason() == EntityTargetEvent.TargetReason.TEMPT)
            event.getEntity().getPersistentDataContainer().set(luredByPlayer, PersistentDataType.BYTE, (byte) 1);
        else
            event.getEntity().getPersistentDataContainer().remove(luredByPlayer);

    }

    //when an entity is damaged
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        this.handleEntityDamageEvent(event, true);
    }

    //when an entity is set on fire
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityCombustByEntity(@NotNull EntityCombustByEntityEvent event) {
        //handle it just like we would an entity damge by entity event, except don't send player messages to avoid double messages
        //in cases like attacking with a flame sword or flame arrow, which would ALSO trigger the direct damage event handler

        EntityDamageByEntityEvent eventWrapper = new EntityDamageByEntityEvent(event.getCombuster(), event.getEntity(), EntityDamageEvent.DamageCause.FIRE_TICK, event.getDuration());
        this.handleEntityDamageEvent(eventWrapper, false);
        event.setCancelled(eventWrapper.isCancelled());
    }

    private void handleEntityDamageEvent(@NotNull EntityDamageEvent event, boolean sendMessages) {
        // JHarris - Ignore NPCs
        if (event.getEntity().hasMetadata("NPC")) return;

        //monsters are never protected
        if (isHostile(event.getEntity())) return;

        //horse protections can be disabled
        if (event.getEntity() instanceof Horse && !instance.config_claims_protectHorses) return;
        if (event.getEntity() instanceof Donkey && !instance.config_claims_protectDonkeys) return;
        if (event.getEntity() instanceof Mule && !instance.config_claims_protectDonkeys) return;
        if (event.getEntity() instanceof Llama && !instance.config_claims_protectLlamas) return;

        // Handle environmental damage to tamed animals that could easily be caused maliciously.
        if (handlePetDamageByEnvironment(event)) return;

        // Handle entity damage by block explosions.
        if (handleEntityDamageByBlockExplosion(event)) return;

        //the rest is only interested in entities damaging entities (ignoring environmental damage)
        if (!(event instanceof EntityDamageByEntityEvent subEvent)) return;

        if (subEvent.getDamager() instanceof LightningStrike && subEvent.getDamager().hasMetadata("GP_TRIDENT")) {
            event.setCancelled(true);
            return;
        }

        //determine which player is attacking, if any
        Player attacker = null;
        Projectile arrow = null;
        Entity damageSource = subEvent.getDamager();
        if (damageSource instanceof Player damager) {
            attacker = damager;
        }
        else if (damageSource instanceof Projectile projectile) {
            arrow = projectile;
            if (arrow.getShooter() instanceof Player shooter) {
                attacker = shooter;
            }
        }

        //don't track in worlds where claims are not enabled
        if (!instance.claimsEnabledForWorld(event.getEntity().getWorld())) return;

        //if the damaged entity is a claimed item frame or armor stand, the damager needs to be a player with build trust in the claim
        if (handleClaimedBuildTrustDamageByEntity(subEvent, attacker, sendMessages)) return;

        //if the entity is a non-monster creature (remember monsters disqualified above), or a vehicle
        if (handleCreatureDamageByEntity(subEvent, attacker, arrow, sendMessages)) return;
    }

    /**
     * Check if an {@link Entity} is considered hostile.
     *
     * @param entity the {@code Entity}
     * @return true if the {@code Entity} is hostile
     */
    private boolean isHostile(@NotNull Entity entity) {
        if (entity instanceof Monster) return true;

        EntityType type = entity.getType();
        if (type == EntityType.GHAST || type == EntityType.MAGMA_CUBE || type == EntityType.SHULKER)
            return true;

        if (entity instanceof Slime slime) return slime.getSize() > 0;

        if (entity instanceof Rabbit rabbit) return rabbit.getRabbitType() == Rabbit.Type.THE_KILLER_BUNNY;

        if (entity instanceof Panda panda) return panda.getMainGene() == Panda.Gene.AGGRESSIVE;

        if ((type == EntityType.HOGLIN || type == EntityType.POLAR_BEAR) && entity instanceof Mob mob)
            return !entity.getPersistentDataContainer().has(luredByPlayer, PersistentDataType.BYTE) && mob.getTarget() != null;

        return false;
    }

    /**
     * Handle damage to {@link Tameable} entities by environmental sources.
     *
     * @param event the {@link EntityDamageEvent}
     * @return true if the damage is handled
     */
    private boolean handlePetDamageByEnvironment(@NotNull EntityDamageEvent event) {
        // The damaged entity is not a pet, or the pet has no owner, allow.
        if (!(event.getEntity() instanceof Tameable tameable) || !tameable.isTamed()) return false;

        switch (event.getCause()) {
            // Block environmental and easy-to-cause damage sources.
            case BLOCK_EXPLOSION,
                 ENTITY_EXPLOSION,
                 FALLING_BLOCK,
                 FIRE,
                 FIRE_TICK,
                 LAVA,
                 SUFFOCATION,
                 CONTACT,
                 DROWNING -> {
                event.setCancelled(true);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Handle entity damage caused by block explosions.
     *
     * @param event the {@link EntityDamageEvent}
     * @return true if the damage is handled
     */
    private boolean handleEntityDamageByBlockExplosion(@NotNull EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return false;

        Entity entity = event.getEntity();

        // Skip players - does allow players to use block explosions to bypass PVP protections,
        // but also doesn't disable self-damage.
        if (entity instanceof Player) return false;

        Claim claim = dataStore.getClaimAt(entity.getLocation(), false, null);

        // Only block explosion damage inside claims.
        if (claim == null) return false;

        event.setCancelled(true);
        return true;
    }

    /**
     * Handle PVP damage to an owned pet.
     *
     * @param event the {@link EntityDamageByEntityEvent}
     * @param pet the potential pet being damaged
     * @param attacker the attacking {@link Player}
     * @param sendMessages whether to send denial messages to users involved
     * @return true if the damage is handled
     */
    private boolean handlePvpPetDamageByPlayer(
            @NotNull EntityDamageByEntityEvent event,
            @NotNull Tameable pet,
            @NotNull Player attacker,
            boolean sendMessages) {

        if (!pet.isTamed()) return false;

        AnimalTamer owner = pet.getOwner();
        if (owner == null) return false;

        // If the player interacting is the owner, always allow.
        if (attacker.equals(owner)) return true;

        // Allow admin override.
        PlayerData attackerData = this.dataStore.getPlayerData(attacker.getUniqueId());
        if (attackerData.ignoreClaims) return true;

        // Disallow provocations while PVP-immune.
        if (attackerData.pvpImmune) {
            event.setCancelled(true);
            if (sendMessages)
                GriefPrevention.sendMessage(attacker, TextMode.Err, Messages.CantFightWhileImmune);
            return true;
        }

        if (instance.config_pvp_protectPets) {
            // Wolves are exempt from pet protections in PVP worlds due to their offensive nature.
            if (event.getEntity().getType() == EntityType.WOLF) return true;

            PreventPvPEvent pvpEvent = new PreventPvPEvent(new Claim(null, DataStore.locationToClaimCorner(event.getEntity().getLocation()), DataStore.locationToClaimCorner(event.getEntity().getLocation()), null, new HashMap<>(), new HashMap<>(), new ArrayList<>(), null), attacker, pet);
            Bukkit.getPluginManager().callEvent(pvpEvent);
            if (!pvpEvent.isCancelled()) {
                event.setCancelled(true);
                if (sendMessages) {
                    String ownerName = GriefPrevention.lookupPlayerName(owner);
                    String message = dataStore.getMessage(Messages.NoDamageClaimedEntity, ownerName);
                    if (attacker.hasPermission("griefprevention.ignoreclaims"))
                        message += "  " + dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
                    GriefPrevention.sendMessage(attacker, TextMode.Err, message);
                }
            }
        }
        return true;
    }

    /**
     * Handle actions requiring build trust.
     *
     * @param event the {@link EntityDamageByEntityEvent}
     * @param attacker the attacking {@link Player}, if any
     * @param sendMessages whether to send denial messages to users involved
     * @return true if the damage is handled
     */
    private boolean handleClaimedBuildTrustDamageByEntity(
            @NotNull EntityDamageByEntityEvent event,
            @Nullable Player attacker,
            boolean sendMessages) {
        EntityType entityType = event.getEntityType();
        if (entityType != EntityType.ITEM_FRAME
                && entityType != EntityType.GLOW_ITEM_FRAME
                && entityType != EntityType.ARMOR_STAND
                && entityType != EntityType.VILLAGER
                && entityType != EntityTypeUtils.of("END_CRYSTAL")) {
            return false;
        }

        if (entityType == EntityType.VILLAGER
                // Allow disabling villager protections in the config.
                && (!instance.config_claims_protectCreatures
                // Always allow zombies and raids to target villagers.
                //why exception?  so admins can set up a village which can't be CHANGED by players, but must be "protected" by players.
                || event.getDamager() instanceof Zombie
                || event.getDamager() instanceof Raider
                || event.getDamager() instanceof Vex
                || event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Raider
                || event.getDamager() instanceof EvokerFangs fangs && fangs.getOwner() instanceof Raider)) {
            return true;
        }

        // Use attacker's cached claim to speed up lookup.
        Claim cachedClaim = null;
        if (attacker != null) {
            PlayerData playerData = this.dataStore.getPlayerData(attacker.getUniqueId());
            cachedClaim = playerData.lastClaim;
        }

        Claim claim = this.dataStore.getClaimAt(event.getEntity().getLocation(), false, cachedClaim);

        // If the area is not claimed, do not handle.
        if (claim == null) return false;

        // If attacker isn't a player, cancel.
        if (attacker == null) {
            event.setCancelled(true);
            return true;
        }

        if (!claim.hasClaimPermission(attacker.getUniqueId(), ClaimPermission.INTERACT)) {
            event.setCancelled(true);
            if (sendMessages) GriefPrevention.sendMessage(attacker, TextMode.Err, ClaimPermission.INTERACT.getDenialMessage());
            return true;
        }

        return false;
    }

    /**
     * Handle damage to a {@link Creature} by an {@link Entity}. Because monsters are
     * already discounted, any qualifying entity is livestock or a pet.
     *
     * @param event the {@link EntityDamageByEntityEvent}
     * @param attacker the attacking {@link Player}, if any
     * @param arrow the {@link Projectile} dealing the damage, if any
     * @param sendMessages whether to send denial messages to users involved
     * @return true if the damage is handled
     */
    private boolean handleCreatureDamageByEntity(
            @NotNull EntityDamageByEntityEvent event,
            @Nullable Player attacker,
            @Nullable Projectile arrow,
            boolean sendMessages) {
        if (!(event.getEntity() instanceof Creature) || !instance.config_claims_protectCreatures)
            return false;

        //if entity is tameable and has an owner, apply special rules
        if (handlePetDamageByEntity(event, attacker, sendMessages)) return true;

        Entity damageSource = event.getDamager();
        EntityType damageSourceType = damageSource.getType();
        //if not a player, explosive, or ranged/area of effect attack, allow
        if (attacker == null
                && damageSourceType != EntityType.CREEPER
                && damageSourceType != EntityType.WITHER
                && damageSourceType != EntityTypeUtils.of("END_CRYSTAL")
                && damageSourceType != EntityType.AREA_EFFECT_CLOUD
                && damageSourceType != EntityType.WITCH
                && !(damageSource instanceof Projectile)
                && !(damageSource instanceof Explosive)
                && !(damageSource instanceof ExplosiveMinecart)) {
            return true;
        }

        Claim cachedClaim = null;
        PlayerData playerData = null;
        if (attacker != null) {
            playerData = this.dataStore.getPlayerData(attacker.getUniqueId());
            cachedClaim = playerData.lastClaim;
        }

        Claim claim = this.dataStore.getClaimAt(event.getEntity().getLocation(), false, cachedClaim);

        // Require a claim to handle.
        if (claim == null) return false;

        // If damaged by anything other than a player, cancel the event.
        if (attacker == null) {
            event.setCancelled(true);
            // Always remove projectiles shot by non-players.
            if (arrow != null) arrow.remove();
            return true;
        }

        //cache claim for later
        playerData.lastClaim = claim;

        // Do not message players about fireworks to prevent spam due to multi-hits.
        sendMessages &= damageSourceType != EntityTypeUtils.of("FIREWORK_ROCKET");

        Supplier<String> override = null;
        if (sendMessages) {
            final Player finalAttacker = attacker;
            override = () ->
            {
                String message = dataStore.getMessage(Messages.NoDamageClaimedEntity, claim.getOwnerName());
                if (finalAttacker.hasPermission("griefprevention.ignoreclaims"))
                    message += "  " + dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
                return message;
            };
        }

        if (!claim.hasClaimPermission(attacker.getUniqueId(), ClaimPermission.HURT_ANIMALS)) {
            event.setCancelled(true);

            // Prevent projectiles from bouncing infinitely.
            preventInfiniteBounce(arrow, event.getEntity());

            if (sendMessages) GriefPrevention.sendMessage(attacker, TextMode.Err, ClaimPermission.HURT_ANIMALS.getDenialMessage());
        }

        return true;
    }

    /**
     * Handle damage to a {@link Tameable} by a {@link Player}.
     *
     * @param event the {@link EntityDamageByEntityEvent}
     * @param attacker the attacking {@link Player}, if any
     * @param sendMessages whether to send denial messages to users involved
     * @return true if the damage is handled
     */
    private boolean handlePetDamageByEntity(
            @NotNull EntityDamageByEntityEvent event,
            @Nullable Player attacker,
            boolean sendMessages) {
        if (!(event.getEntity() instanceof Tameable tameable) || !tameable.isTamed()) return false;

        AnimalTamer owner = tameable.getOwner();
        if (owner == null) return false;

        //limit attacks by players to owners and admins in ignore claims mode
        if (attacker == null) return false;

        //if the player interacting is the owner, always allow
        if (attacker.equals(owner)) return true;

        //allow for admin override
        PlayerData attackerData = this.dataStore.getPlayerData(attacker.getUniqueId());
        if (attackerData.ignoreClaims) return true;

        // Allow players to attack wolves (dogs) if under attack by them.
        if (tameable.getType() == EntityType.WOLF && tameable.getTarget() != null) {
            if (tameable.getTarget() == attacker) return true;
        }

        event.setCancelled(true);
        if (sendMessages) {
            String ownerName = GriefPrevention.lookupPlayerName(owner);
            String message = dataStore.getMessage(Messages.NoDamageClaimedEntity, ownerName);
            if (attacker.hasPermission("griefprevention.ignoreclaims"))
                message += "  " + dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
            GriefPrevention.sendMessage(attacker, TextMode.Err, message);
        }
        return true;
    }

    /**
     * Prevent infinite bounces for cancelled projectile hits by removing or grounding {@link Projectile Projectiles}
     * as necessary.
     *
     * @param projectile the {@code Projectile} that has been prevented from hitting
     * @param entity the {@link Entity} being hit
     */
    private void preventInfiniteBounce(@Nullable Projectile projectile, @NotNull Entity entity) {
        if (projectile != null) {
            if (projectile.getType() == EntityType.TRIDENT) {
                // Instead of removing a trident, teleport it to the entity's foot location and remove velocity.
                projectile.teleport(entity);
                projectile.setVelocity(new Vector());
            }
            // Otherwise remove the projectile.
            else projectile.remove();
        }
    }

    //when a vehicle is damaged
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onVehicleDamage(@NotNull VehicleDamageEvent event) {
        //all of this is anti theft code
        if (!instance.config_claims_preventTheft) return;

        //don't track in worlds where claims are not enabled
        if (!instance.claimsEnabledForWorld(event.getVehicle().getWorld())) return;

        //determine which player is attacking, if any
        Player attacker = null;
        Projectile arrow = null;
        Entity damageSource = event.getAttacker();
        EntityType damageSourceType = null;

        //if damage source is null or a creeper, don't allow the damage when the vehicle is in a land claim
        if (damageSource != null) {
            damageSourceType = damageSource.getType();

            if (damageSource instanceof Player player) {
                attacker = player;
            }
            else if (damageSource instanceof Projectile projectile) {
                arrow = projectile;
                if (arrow.getShooter() instanceof Player shooter) {
                    attacker = shooter;
                }
            }
        }

        //if not a player and not an explosion, always allow
        if (attacker == null && damageSourceType != EntityType.CREEPER && damageSourceType != EntityType.WITHER && damageSourceType != EntityTypeUtils.of("TNT")) {
            return;
        }

        //NOTE: vehicles can be pushed around.
        //so unless precautions are taken by the owner, a resourceful thief might find ways to steal anyway
        Claim cachedClaim = null;
        PlayerData playerData = null;

        if (attacker != null) {
            playerData = this.dataStore.getPlayerData(attacker.getUniqueId());
            cachedClaim = playerData.lastClaim;
        }

        Claim claim = this.dataStore.getClaimAt(event.getVehicle().getLocation(), false, cachedClaim);

        // Require a claim.
        if (claim == null) return;

        //if damaged by anything other than a player, cancel the event
        if (attacker == null) {
            event.setCancelled(true);
            if (arrow != null) arrow.remove();
            return;
        }

        //otherwise the player damaging the entity must have permission
        final Player finalAttacker = attacker;
        Supplier<String> override = () ->
        {
            String message = dataStore.getMessage(Messages.NoDamageClaimedEntity, claim.getOwnerName());
            if (finalAttacker.hasPermission("griefprevention.ignoreclaims"))
                message += "  " + dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
            return message;
        };

        if (!claim.hasClaimPermission(attacker.getUniqueId(), ClaimPermission.BREAK_BLOCKS)) {
            event.setCancelled(true);
            preventInfiniteBounce(arrow, event.getVehicle());
            GriefPrevention.sendMessage(attacker, TextMode.Err, ClaimPermission.BREAK_BLOCKS.getDenialMessage());
        }

        //cache claim for later
        playerData.lastClaim = claim;
    }

    //when a splash potion affects one or more entities...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPotionSplash(@NotNull PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();

        ProjectileSource projectileSource = potion.getShooter();
        // Ignore potions with no source.
        if (projectileSource == null) return;
        final Player thrower;
        if ((projectileSource instanceof Player))
            thrower = (Player) projectileSource;
        else thrower = null;
        AtomicBoolean messagedPlayer = new AtomicBoolean(false);

        if (thrower == null) return;

        Collection<PotionEffect> effects = potion.getEffects();
        for (PotionEffect effect : effects) {
            PotionEffectType effectType = effect.getType();

            // Restrict some potions on claimed villagers and animals.
            // Griefers could use potions to kill entities or steal them over fences.
            if (GRIEF_EFFECTS.contains(effectType)) {
                Claim cachedClaim = null;
                for (LivingEntity affected : event.getAffectedEntities()) {
                    // Always impact the thrower.
                    if (affected == thrower) continue;

                    if (affected.getType() == EntityType.VILLAGER || affected instanceof Animals) {
                        Claim claim = this.dataStore.getClaimAt(affected.getLocation(), false, cachedClaim);
                        if (claim != null) {
                            cachedClaim = claim;

                            if (thrower == null) {
                                // Non-player source: Witches, dispensers, etc.
                                if (!EntityEventHandler.isBlockSourceInClaim(projectileSource, claim)) {
                                    // If the source is not a block in the same claim as the affected entity, disallow.
                                    event.setIntensity(affected, 0);
                                }
                            }
                            else {
                                // Source is a player. Determine if they have permission to access entities in the claim.
                                if (!claim.hasClaimPermission(thrower.getUniqueId(), ClaimPermission.HURT_ANIMALS)) {
                                    event.setIntensity(affected, 0);
                                    if (messagedPlayer.compareAndSet(false, true)) {
                                        GriefPrevention.sendMessage(thrower, TextMode.Err, ClaimPermission.HURT_ANIMALS.getDenialMessage());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
