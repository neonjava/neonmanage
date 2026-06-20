package dev.neonjava.neoncoremanage;

import dev.neonjava.neoncoremanage.commands.CoreCommands;
import dev.neonjava.neoncoremanage.database.DatabaseManager;
import dev.neonjava.neoncoremanage.listeners.ChatListener;
import dev.neonjava.neoncoremanage.listeners.JoinListener;
import dev.neonjava.neoncoremanage.webhook.WebhookManager;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class NeonCoreManage extends JavaPlugin {
    private DatabaseManager db;
    private WebhookManager webhook;
    
    private FileConfiguration cmdsConfig;
    private FileConfiguration bannedWordsConfig;
    private File cmdsFile;
    private File bannedWordsFile;

    @Override
    public void onEnable() {
        saveDefaultConfigs();
        
        db = new DatabaseManager(this);
        db.init();

        webhook = new WebhookManager();

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            CoreCommands coreCmds = new CoreCommands(this);
            coreCmds.registerAll(commands);
        });

        getLogger().info("NeonCoreManage loaded successfully and is Folia compatible!");
    }

    @Override
    public void onDisable() {
        if (db != null) {
            db.close();
        }
        getLogger().info("NeonCoreManage disabled successfully!");
    }

    private void saveDefaultConfigs() {
        cmdsFile = new File(getDataFolder(), "cmds.yml");
        bannedWordsFile = new File(getDataFolder(), "banned_words.yml");

        if (!cmdsFile.exists()) {
            saveResource("cmds.yml", false);
        }
        if (!bannedWordsFile.exists()) {
            saveResource("banned_words.yml", false);
        }

        cmdsConfig = YamlConfiguration.loadConfiguration(cmdsFile);
        bannedWordsConfig = YamlConfiguration.loadConfiguration(bannedWordsFile);
    }

    public DatabaseManager getDb() {
        return db;
    }

    public WebhookManager getWebhook() {
        return webhook;
    }

    public FileConfiguration getCmdsConfig() {
        return cmdsConfig;
    }

    public FileConfiguration getBannedWordsConfig() {
        return bannedWordsConfig;
    }
}
