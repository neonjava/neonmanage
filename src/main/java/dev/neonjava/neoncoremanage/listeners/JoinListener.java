package dev.neonjava.neoncoremanage.listeners;

import dev.neonjava.neoncoremanage.NeonCoreManage;
import dev.neonjava.neoncoremanage.database.DatabaseManager.BanEntry;
import dev.neonjava.neoncoremanage.utils.TimeUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.UUID;

public class JoinListener implements Listener {
    private final NeonCoreManage plugin;

    public JoinListener(NeonCoreManage plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        BanEntry ban = plugin.getDb().getBan(uuid).join();

        if (ban != null) {
            if (ban.expiresAt() > 0 && ban.expiresAt() < System.currentTimeMillis()) {
                plugin.getDb().removeBan(uuid);
            } else {
                String remaining = TimeUtils.formatRemaining(ban.expiresAt());
                
                Component kickMessage = MiniMessage.miniMessage().deserialize(
                        "<red><bold>YOU ARE BANNED FROM THE SERVER</bold></red>\n\n" +
                        "<gray>Reason: <white>" + ban.reason() + "\n" +
                        "<gray>Time left: <white>" + remaining + "\n\n" +
                        "<yellow>Appeal on - <underlined>discord.ninemc.fun</underlined></yellow>"
                );
                
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);
            }
        }
    }
}
