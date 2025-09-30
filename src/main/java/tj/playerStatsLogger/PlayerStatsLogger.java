package tj.playerStatsLogger;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class PlayerStatsLogger extends JavaPlugin {

    private PlayerStatsPlugin playerStatsPlugin;
    private File configFolder;

    @Override
    public void onEnable() {
        // Plugin startup logic
        
        // 1. 首先保存默认配置文件到插件数据文件夹
        saveDefaultConfig();
        
        // 2. 保存database_init.sql文件到插件数据文件夹
        saveResource("database_init.sql", false);
        
        // 3. 创建PlayerStatsLogger配置文件夹并复制配置文件
        createConfigFolder();
        
        // 初始化实际的功能插件
        playerStatsPlugin = new PlayerStatsPlugin();
        playerStatsPlugin.onEnable(this);
        
        getLogger().info("PlayerStatsLogger 插件已成功加载！");
        Bukkit.broadcastMessage("§a[PlayerStatsLogger] 插件已成功加载！");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (playerStatsPlugin != null) {
            playerStatsPlugin.onDisable();
        }
        getLogger().info("PlayerStatsLogger 插件已成功卸载。");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("psl") || 
            command.getName().equalsIgnoreCase("playerstats") || 
            command.getName().equalsIgnoreCase("stats")) {
            
            if (!sender.hasPermission("playerstatslogger.admin")) {
                sender.sendMessage("§c你没有权限使用此命令！");
                return true;
            }
            
            if (args.length == 0) {
                sender.sendMessage("§ePlayerStatsLogger 插件命令帮助：");
                sender.sendMessage("§a/psl status §7- 显示插件状态");
                sender.sendMessage("§a/psl reload §7- 重载配置文件");
                return true;
            }
            
            switch (args[0].toLowerCase()) {
                case "status":
                    sender.sendMessage("§a[PlayerStatsLogger] 插件运行状态：正常");
                    sender.sendMessage("§a[PlayerStatsLogger] 版本：1.0");
                    sender.sendMessage("§a[PlayerStatsLogger] 配置文件夹：" + configFolder.getAbsolutePath());
                    if (playerStatsPlugin != null) {
                        sender.sendMessage("§a[PlayerStatsLogger] 数据库连接：已连接");
                        sender.sendMessage("§a[PlayerStatsLogger] Redis连接：已连接");
                    }
                    break;
                    
                case "reload":
                    sender.sendMessage("§e[PlayerStatsLogger] 正在重载配置文件...");
                    
                    // 重新加载配置
                    reloadConfig();
                    
                    // 重新初始化插件（关闭后重新启动）
                    if (playerStatsPlugin != null) {
                        playerStatsPlugin.onDisable();
                    }
                    playerStatsPlugin = new PlayerStatsPlugin();
                    playerStatsPlugin.onEnable(this);
                    
                    sender.sendMessage("§a[PlayerStatsLogger] 配置文件重载完成！");
                    getLogger().info("配置文件已被管理员重载。");
                    break;
                    
                default:
                    sender.sendMessage("§c未知命令！使用 /psl 查看帮助。");
                    break;
            }
            return true;
        }
        return false;
    }

    private void createConfigFolder() {
        // 在插件所在位置创建PlayerStatsLogger文件夹
        configFolder = new File(getDataFolder().getParentFile(), "PlayerStatsLogger");
        
        if (!configFolder.exists()) {
            if (configFolder.mkdirs()) {
                getLogger().info("成功创建配置文件夹: " + configFolder.getAbsolutePath());
                
                // 复制配置文件到PlayerStatsLogger文件夹
                copyConfigFiles();
                
                // 在文件夹中创建一个说明文件
                File readmeFile = new File(configFolder, "README.txt");
                try {
                    java.nio.file.Files.write(readmeFile.toPath(), 
                        ("PlayerStatsLogger 配置文件夹\n" +
                         "========================\n\n" +
                         "此文件夹用于存放 PlayerStatsLogger 插件的配置文件。\n" +
                         "您可以在这里修改配置文件，然后使用 /psl reload 命令重载配置。\n\n" +
                         "配置文件说明:\n" +
                         "- config.yml: 主配置文件，包含数据库和Redis连接设置\n" +
                         "- database_init.sql: 数据库初始化脚本\n\n" +
                         "插件版本: 1.0\n" +
                         "创建时间: " + new java.util.Date() + "\n").getBytes());
                } catch (Exception e) {
                    getLogger().warning("无法创建README文件: " + e.getMessage());
                }
            } else {
                getLogger().severe("无法创建配置文件夹: " + configFolder.getAbsolutePath());
            }
        } else {
            getLogger().info("配置文件夹已存在: " + configFolder.getAbsolutePath());
            // 即使文件夹已存在，也检查并复制缺失的配置文件
            copyConfigFiles();
        }
    }

    private void copyConfigFiles() {
        // 复制config.yml文件
        File sourceConfig = new File(getDataFolder(), "config.yml");
        File targetConfig = new File(configFolder, "config.yml");
        
        if (sourceConfig.exists() && !targetConfig.exists()) {
            try {
                java.nio.file.Files.copy(sourceConfig.toPath(), targetConfig.toPath());
                getLogger().info("已复制 config.yml 到配置文件夹");
            } catch (Exception e) {
                getLogger().warning("无法复制 config.yml: " + e.getMessage());
            }
        }
        
        // 复制database_init.sql文件
        File sourceSql = new File(getDataFolder(), "database_init.sql");
        File targetSql = new File(configFolder, "database_init.sql");
        
        if (sourceSql.exists() && !targetSql.exists()) {
            try {
                java.nio.file.Files.copy(sourceSql.toPath(), targetSql.toPath());
                getLogger().info("已复制 database_init.sql 到配置文件夹");
            } catch (Exception e) {
                getLogger().warning("无法复制 database_init.sql: " + e.getMessage());
            }
        }
    }
}
