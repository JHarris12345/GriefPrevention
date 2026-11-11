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
 * HangingBreakEvent. It fires EntityBreakEvent, which did not exist in the API version this plugin
 * compiles against, so the event is looked up by name at runtime: the protection applies on servers
 * new enough to have cushions and costs nothing on servers that are not.
 *
 * Both the plain event and the EntityBreakByEntityEvent subclass are handled here. They share one
 * handler list, because only the parent declares it, so one registration receives both and the
 * instance has to be checked before asking an event for a remover it may not have.
 *
 * Hanging entities are deliberately skipped. They fire the hanging events as well, and those are
 * already handled in EntityEventHandler with their own permissions, so claiming them here too would
 * change what trust is needed to break an item frame.
 */
public class EntityBreakListener implements Listener {

    private static final String BREAK_EVENT_CLASS_NAME = "io.papermc.paper.event.entity.EntityBreakEvent";
    private static final String BREAK_BY_ENTITY_EVENT_CLASS_NAME = "io.papermc.paper.event.entity.EntityBreakByEntityEvent";

    private final DataStore dataStore;
    private final Class<?> byEntityClass;
    private final Method getCause;
    private final Method getRemover;

    private EntityBreakListener(DataStore dataStore, Class<?> byEntityClass, Method getCause, Method getRemover) {
        this.dataStore = dataStore;
        this.byEntityClass = byEntityClass;
        this.getCause = getCause;
        this.getRemover = getRemover;
    }

    @SuppressWarnings("unchecked")
    public static void register(GriefPrevention plugin, DataStore dataStore) {
        Class<?> breakClass;
        Class<?> byEntityClass;
        Method getCause;
        Method getRemover;

        try {
            breakClass = Class.forName(BREAK_EVENT_CLASS_NAME);
            byEntityClass = Class.forName(BREAK_BY_ENTITY_EVENT_CLASS_NAME);
            getCause = breakClass.getMethod("getCause");
            getRemover = byEntityClass.getMethod("getRemover");
        } catch (ClassNotFoundException | NoSuchMethodException ex) {
            //this server predates the event, so it has nothing that fires it either
            return;
        }

        if (!Event.class.isAssignableFrom(breakClass)) return;

        EntityBreakListener listener = new EntityBreakListener(dataStore, byEntityClass, getCause, getRemover);

        //registered against the parent, which is where the shared handler list lives, so this sees
        //an explosion with no remover as well as a break by some entity
        plugin.getServer().getPluginManager().registerEvent(
                (Class<? extends Event>) breakClass,
                listener,
                EventPriority.LOWEST,
                (ignored, event) -> listener.handle(event),
                plugin,
                true);
    }

    private void handle(Event event) {
        if (!(event instanceof EntityEvent) || !(event instanceof Cancellable)) return;

        Entity broken = ((EntityEvent) event).getEntity();

        //already covered by the hanging break handlers, with their own permissions
        if (broken instanceof Hanging) return;

        if (!GriefPrevention.instance.claimsEnabledForWorld(broken.getWorld())) return;

        Object causeValue;
        try {
            causeValue = this.getCause.invoke(event);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return;
        }

        if (!(causeValue instanceof Enum)) return;

        //an explosion leaves nobody to check trust against. A block explosion has no remover at all,
        //and an entity one names the creeper, the TNT or the wind charge rather than whoever set it
        //off, so a claim simply keeps its cushions. Wind charges are the reason this matters: they
        //break no blocks, so a cushion was the one thing in a claim they could still take.
        //No denial message, because there is often no player to send one to, and a trusted player
        //throwing a wind charge around their own claim should not be told off for it either.
        if (((Enum<?>) causeValue).name().equals("EXPLOSION")) {
            if (this.dataStore.getClaimAt(broken.getLocation(), false, null) == null) return;

            ((Cancellable) event).setCancelled(true);
            return;
        }

        //the plain event carries no remover, and both events arrive here, so check before asking
        if (!this.byEntityClass.isInstance(event)) return;

        Object remover;
        try {
            remover = this.getRemover.invoke(event);
        } catch (ReflectiveOperationException | RuntimeException ex) {
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
