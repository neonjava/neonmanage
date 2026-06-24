package dev.neonjava.neoncoremanage.listeners;

import dev.neonjava.neoncoremanage.NeonCoreManage;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DeathListener implements Listener {
    private final NeonCoreManage plugin;

    public DeathListener(NeonCoreManage plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location deathLoc = player.getLocation().clone();
        plugin.getDeathLocations().put(player.getUniqueId(), deathLoc);
    }
}
