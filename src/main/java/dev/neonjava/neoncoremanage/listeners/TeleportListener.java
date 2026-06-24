package dev.neonjava.neoncoremanage.listeners;

import dev.neonjava.neoncoremanage.NeonCoreManage;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TeleportListener implements Listener {
    private final NeonCoreManage plugin;

    public TeleportListener(NeonCoreManage plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        ScheduledTask task = plugin.getActiveTeleports().remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>Teleportation cancelled because you moved!"
            ));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        java.util.UUID uuid = player.getUniqueId();
        
        ScheduledTask task = plugin.getActiveTeleports().remove(uuid);
        if (task != null) {
            task.cancel();
        }

        plugin.getTpaRequests().remove(uuid);
        plugin.getTpaRequests().values().removeIf(req -> req.sender().equals(uuid) || req.receiver().equals(uuid));
        plugin.getGodPlayers().remove(uuid);
        plugin.getIgnoreCache().remove(uuid);
    }
}
