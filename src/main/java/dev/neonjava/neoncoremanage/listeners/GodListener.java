package dev.neonjava.neoncoremanage.listeners;

import dev.neonjava.neoncoremanage.NeonCoreManage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

public class GodListener implements Listener {
    private final NeonCoreManage plugin;

    public GodListener(NeonCoreManage plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!plugin.getConfig().getBoolean("modules.god", true)) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            if (plugin.getGodPlayers().contains(player.getUniqueId())) {
                event.setDamage(0.0);
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!plugin.getConfig().getBoolean("modules.god", true)) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            if (plugin.getGodPlayers().contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }
}
