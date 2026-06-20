package dev.neonjava.neoncoremanage.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.neonjava.neoncoremanage.NeonCoreManage;
import dev.neonjava.neoncoremanage.database.DatabaseManager.BanEntry;
import dev.neonjava.neoncoremanage.database.DatabaseManager.MuteEntry;
import dev.neonjava.neoncoremanage.utils.TimeUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public class CoreCommands {
    private final NeonCoreManage plugin;

    public CoreCommands(NeonCoreManage plugin) {
        this.plugin = plugin;
    }

    public void registerAll(Commands registrar) {
        registrar.register(buildGiverank(), "Gives a rank to a player", Collections.emptyList());
        registrar.register(buildCancelrank(), "Cancels a pending rank for a player", Collections.emptyList());
        registrar.register(buildNeonmanage(), "Plugin management commands", Collections.emptyList());
        registrar.register(buildBan(), "Bans a player permanently", Collections.emptyList());
        registrar.register(buildTempban(), "Bans a player temporarily", Collections.emptyList());
        registrar.register(buildUnban(), "Unbans a player", Collections.emptyList());
        registrar.register(buildMute(), "Mutes a player permanently", Collections.emptyList());
        registrar.register(buildTempmute(), "Mutes a player temporarily", Collections.emptyList());
        registrar.register(buildUnmute(), "Unmutes a player", Collections.emptyList());
    }

    private LiteralCommandNode<CommandSourceStack> buildGiverank() {
        return Commands.literal("giverank")
                .requires(stack -> stack.getSender().hasPermission("neoncore.giverank"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("rank", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    Set<String> ranks = plugin.getCmdsConfig().getConfigurationSection("ranks").getKeys(false);
                                    for (String rank : ranks) {
                                        builder.suggest(rank);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String playerName = StringArgumentType.getString(ctx, "player");
                                    String rankName = StringArgumentType.getString(ctx, "rank");
                                    
                                    org.bukkit.configuration.ConfigurationSection rankSection = plugin.getCmdsConfig().getConfigurationSection("ranks." + rankName);
                                    if (rankSection == null) {
                                        ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>Rank not found in config!"));
                                        return 0;
                                    }

                                    String staffName = ctx.getSource().getSender().getName();
                                    Player targetPlayer = Bukkit.getPlayerExact(playerName);

                                    if (targetPlayer != null && targetPlayer.isOnline()) {
                                        // Execute immediately since player is online
                                        List<String> commands = rankSection.getStringList("commands");
                                        CompletableFuture.runAsync(() -> {
                                            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                                                for (String cmd : commands) {
                                                    String finalCmd = cmd.replace("{player}", targetPlayer.getName());
                                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                                                }
                                            });
                                            plugin.getWebhook().sendModerationLog("GIVERANK", targetPlayer.getName(), staffName, "Rank: " + rankName + " (Applied immediately)", "N/A");
                                        });

                                        ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                                                "<green>Successfully applied rank <yellow>" + rankName + "</yellow> to <yellow>" + targetPlayer.getName() + "</yellow>."
                                        ));
                                    } else {
                                        // Queue for when player logs in
                                        plugin.getDb().addPendingRank(playerName, rankName, staffName);
                                        ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                                                "<green>Player <yellow>" + playerName + "</yellow> is offline. Rank <yellow>" + rankName + "</yellow> has been queued and will be applied when they join."
                                        ));
                                    }
                                    return 1;
                                })))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildBan() {
        return Commands.literal("ban")
                .requires(stack -> stack.getSender().hasPermission("neoncore.ban"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeBan(ctx, -1))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> executeBan(ctx, -1))))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildTempban() {
        return Commands.literal("tempban")
                .requires(stack -> stack.getSender().hasPermission("neoncore.ban"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("duration", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("30d");
                                    builder.suggest("7d");
                                    builder.suggest("1d");
                                    builder.suggest("12h");
                                    builder.suggest("1h");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> executeBan(ctx, 1))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> executeBan(ctx, 2)))))
                .build();
    }

    private int executeBan(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int mode) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String reason = "Banned by administrator";
        long durationMs = -1;

        if (mode == 1 || mode == 2) {
            String durStr = StringArgumentType.getString(ctx, "duration");
            durationMs = TimeUtils.parseDuration(durStr);
            if (durationMs <= 0) {
                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>Invalid duration format!"));
                return 0;
            }
        }

        try {
            reason = StringArgumentType.getString(ctx, "reason");
        } catch (IllegalArgumentException e) {
            // Keep default reason
        }

        long expiresAt = durationMs > 0 ? System.currentTimeMillis() + durationMs : -1;
        String finalReason = reason;
        long finalExpiresAt = expiresAt;
        String durFormatted = durationMs > 0 ? TimeUtils.formatRemaining(expiresAt) : "Permanent";

        CompletableFuture.runAsync(() -> {
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            UUID uuid = target.getUniqueId();
            String staff = ctx.getSource().getSender().getName();

            plugin.getDb().addBan(new BanEntry(uuid.toString(), target.getName(), staff, finalReason, finalExpiresAt)).join();

            Player online = target.getPlayer();
            if (online != null) {
                online.getScheduler().run(plugin, task -> online.kick(MiniMessage.miniMessage().deserialize(
                        "<red><bold>YOU ARE BANNED FROM THE SERVER</bold></red>\n\n" +
                        "<gray>Reason: <white>" + finalReason + "\n" +
                        "<gray>Time left: <white>" + durFormatted + "\n\n" +
                        "<yellow>Appeal on - <underlined>discord.ninemc.fun</underlined></yellow>"
                )), null);
            }

            plugin.getWebhook().sendModerationLog("BAN", target.getName(), staff, finalReason, durFormatted);
            ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                    "<green>Successfully banned <yellow>" + target.getName() + "</yellow> for: <gray>" + finalReason
            ));
        });

        return 1;
    }

    private LiteralCommandNode<CommandSourceStack> buildUnban() {
        return Commands.literal("unban")
                .requires(stack -> stack.getSender().hasPermission("neoncore.ban"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .executes(ctx -> {
                            String playerName = StringArgumentType.getString(ctx, "player");
                            CompletableFuture.runAsync(() -> {
                                @SuppressWarnings("deprecation")
                                OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
                                plugin.getDb().removeBan(target.getUniqueId()).join();
                                String staff = ctx.getSource().getSender().getName();
                                plugin.getWebhook().sendModerationLog("UNBAN", target.getName(), staff, "Unbanned", "N/A");
                                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                                        "<green>Successfully unbanned <yellow>" + target.getName() + "</yellow>."
                                ));
                            });
                            return 1;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildMute() {
        return Commands.literal("mute")
                .requires(stack -> stack.getSender().hasPermission("neoncore.mute"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeMute(ctx, -1))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> executeMute(ctx, -1))))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildTempmute() {
        return Commands.literal("tempmute")
                .requires(stack -> stack.getSender().hasPermission("neoncore.mute"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("duration", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("30d");
                                    builder.suggest("7d");
                                    builder.suggest("1d");
                                    builder.suggest("12h");
                                    builder.suggest("1h");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> executeMute(ctx, 1))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> executeMute(ctx, 2)))))
                .build();
    }

    private int executeMute(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int mode) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String reason = "Muted by administrator";
        long durationMs = -1;

        if (mode == 1 || mode == 2) {
            String durStr = StringArgumentType.getString(ctx, "duration");
            durationMs = TimeUtils.parseDuration(durStr);
            if (durationMs <= 0) {
                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>Invalid duration format!"));
                return 0;
            }
        }

        try {
            reason = StringArgumentType.getString(ctx, "reason");
        } catch (IllegalArgumentException e) {
            // Keep default reason
        }

        long expiresAt = durationMs > 0 ? System.currentTimeMillis() + durationMs : -1;
        String finalReason = reason;
        long finalExpiresAt = expiresAt;
        String durFormatted = durationMs > 0 ? TimeUtils.formatRemaining(expiresAt) : "Permanent";

        CompletableFuture.runAsync(() -> {
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            UUID uuid = target.getUniqueId();
            String staff = ctx.getSource().getSender().getName();

            plugin.getDb().addMute(new MuteEntry(uuid.toString(), target.getName(), staff, finalReason, finalExpiresAt)).join();

            plugin.getWebhook().sendModerationLog("MUTE", target.getName(), staff, finalReason, durFormatted);
            ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                    "<green>Successfully muted <yellow>" + target.getName() + "</yellow> for: <gray>" + finalReason
            ));
        });

        return 1;
    }

    private LiteralCommandNode<CommandSourceStack> buildUnmute() {
        return Commands.literal("unmute")
                .requires(stack -> stack.getSender().hasPermission("neoncore.mute"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .executes(ctx -> {
                            String playerName = StringArgumentType.getString(ctx, "player");
                            CompletableFuture.runAsync(() -> {
                                @SuppressWarnings("deprecation")
                                OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
                                plugin.getDb().removeMute(target.getUniqueId()).join();
                                String staff = ctx.getSource().getSender().getName();
                                plugin.getWebhook().sendModerationLog("UNMUTE", target.getName(), staff, "Unmuted", "N/A");
                                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                                        "<green>Successfully unmuted <yellow>" + target.getName() + "</yellow>."
                                ));
                            });
                            return 1;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildCancelrank() {
        return Commands.literal("cancelrank")
                .requires(stack -> stack.getSender().hasPermission("neoncore.giverank"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .executes(ctx -> {
                            String playerName = StringArgumentType.getString(ctx, "player");
                            CompletableFuture.runAsync(() -> {
                                plugin.getDb().removePendingRank(playerName).join();
                                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                                        "<green>Successfully cancelled any pending ranks for <yellow>" + playerName + "</yellow>."
                                ));
                            });
                            return 1;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildNeonmanage() {
        return Commands.literal("neonmanage")
                .requires(stack -> stack.getSender().hasPermission("neoncore.admin"))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                                plugin.reloadConfigs();
                                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                                        "<green>NeonCoreManage configuration files reloaded successfully!"
                                ));
                            });
                            return 1;
                        }))
                .build();
    }
}
