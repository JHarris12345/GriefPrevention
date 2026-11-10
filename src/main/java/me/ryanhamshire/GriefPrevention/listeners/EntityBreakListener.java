package me.ryanhamshire.GriefPrevention.listeners;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.data.DataStore;
import me.ryanhamshire.GriefPrevention.objects.Claim;
import me.ryanhamshire.GriefPrevention.objects.TextMode;
import me.ryanhamshire.GriefPrevention.objects.enums.ClaimPermission;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityEvent;

import java.lang.reflect.Method;

/**
 * Protects block-attached entities that are not hanging entities, which in practice means cushions.
 *
 * A cushion is an entity, not a block, so breaking one fires neither BlockBreakEvent nor
 * HangingBreakEvent. It fires EntityBreakByEntityEvent, which did not exist in the API version this
 * plugin compiles against, so the event is looked up by name at runtime: the protection applies on
 * servers new enough to have cushions and costs nothing on servers that are not.
 *
 * Hanging entities are deliberately skipped. They fire the hanging events as well, and those are
 * already handled in EntityEventHandler with their own permissions, so claiming them here too would
 * change what trust is needed to break an item frame.
 */
public class EntityBreakListener implements Listener {

    private static final String EVENT_CLASS_NAME = "io.papermc.paper.event.entity.EntityBreakByEntityEvent";

    private final DataStore dataStore;

    private EntityBreakListener(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @SuppressWarnings("unchecked")
    public static void register(GriefPrevention plugin, DataStore dataStore) {
        Class<?> eventClass;
        Method getRemover;

        try {
            eventClass = Class.forName(EVENT_CLASS_NAME);
            getRemover = eventClass.getMethod("getRemover");
        } catch (ClassNotFoundException | NoSuchMethodException ex) {
            //this server predates the event, so it has nothing that fires it either
            return;
        }

        if (!Event.class.isAssignableFrom(eventClass)) return;

        EntityBreakListener listener = new EntityBreakListener(dataStore);

        plugin.getServer().getPluginManager().registerEvent(
                (Class<? extends Event>) eventClass,
                listener,
                EventPriority.LOWEST,
                (ignored, event) -> listener.handle(event, getRemover),
                plugin,
                true);
    }

    private void handle(Event event, Method getRemover) {
        if (!(event instanceof EntityEvent) || !(event instanceof Cancellable)) return;

        Entity broken = ((EntityEvent) event).getEntity();

        //already covered by the hanging break handlers, with their own permissions
        if (broken instanceof Hanging) return;

        if (!GriefPrevention.instance.claimsEnabledForWorld(broken.getWorld())) return;

        Object remover;
        try {
            remover = getRemover.invoke(event);
        } catch (ReflectiveOperationException ex) {
            return;
        }

        if (!(remover instanceof Player)) return;
        Player player = (Player) remover;

        Claim claim = this.dataStore.getClaimAt(broken.getLocation(), false, null);
        if (claim == null) return;

        if (!claim.hasClaimPermission(player.getUniqueId(), ClaimPermission.BREAK_BLOCKS)) {
            ((Cancellable) event).setCancelled(true);
            GriefPrevention.sendMessage(player, TextMode.Err, ClaimPermission.BREAK_BLOCKS.getDenialMessage());
        }
    }
}
