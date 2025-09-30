package tj.playerStatsLogger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStatsPlugin implements Listener {

    private HikariDataSource mysqlPool;
    private JedisPool redisPool;
    private final Set<String> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private JavaPlugin plugin;

    public PlayerStatsPlugin() {
        // 默认构造函数
    }

    public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getLogger().info("PlayerStatsLogger 正在启动...");

        // 初始化 MySQL 连接池
        setupMySQL();
        // 初始化 Redis 连接池
        setupRedis();
        // 创建数据表
        createTableIfNotExists();

        // 注册事件监听器
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // 启动定时任务
        startAutoSaveTask();

        plugin.getLogger().info("PlayerStatsLogger 已成功启动！");
    }

    public void onDisable() {
        if (plugin == null) return;
        
        plugin.getLogger().info("PlayerStatsLogger 正在关闭...");
        flushAllToMySQL();
        if (redisPool != null) {
            redisPool.close();
        }
        if (mysqlPool != null) {
            mysqlPool.close();
        }
        plugin.getLogger().info("PlayerStatsLogger 已成功关闭。");
    }

    private void setupMySQL() {
        String host = plugin.getConfig().getString("database.host");
        int port = plugin.getConfig().getInt("database.port");
        String name = plugin.getConfig().getString("database.name");
        String username = plugin.getConfig().getString("database.username");
        String password = plugin.getConfig().getString("database.password");
        boolean useSSL = plugin.getConfig().getBoolean("database.use_ssl");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + name + "?useSSL=" + useSSL + "&autoReconnect=true");
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        mysqlPool = new HikariDataSource(config);
        plugin.getLogger().info("MySQL 连接池已初始化。");
    }

    private void setupRedis() {
        String host = plugin.getConfig().getString("redis.host");
        int port = plugin.getConfig().getInt("redis.port");
        String password = plugin.getConfig().getString("redis.password");
        int database = plugin.getConfig().getInt("redis.database");
        int timeout = plugin.getConfig().getInt("redis.timeout_ms");

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        
        // 设置连接池参数
        poolConfig.setMaxTotal(plugin.getConfig().getInt("redis.max_total", 20));
        poolConfig.setMaxIdle(plugin.getConfig().getInt("redis.max_idle", 10));
        poolConfig.setMinIdle(plugin.getConfig().getInt("redis.min_idle", 2));
        poolConfig.setMaxWaitMillis(plugin.getConfig().getInt("redis.max_wait_ms", 3000));
        poolConfig.setTestOnBorrow(plugin.getConfig().getBoolean("redis.test_on_borrow", true));
        poolConfig.setTestOnReturn(plugin.getConfig().getBoolean("redis.test_on_return", true));
        poolConfig.setTestWhileIdle(plugin.getConfig().getBoolean("redis.test_while_idle", true));
        
        // 设置空闲连接检查参数
        poolConfig.setTimeBetweenEvictionRunsMillis(60000); // 每分钟检查一次空闲连接
        poolConfig.setMinEvictableIdleTimeMillis(300000);    // 空闲5分钟以上可以被回收
        poolConfig.setNumTestsPerEvictionRun(3);             // 每次检查3个连接

        redisPool = new JedisPool(poolConfig, host, port, timeout, password, database);
        plugin.getLogger().info("Redis 连接池已初始化，连接池配置：");
        plugin.getLogger().info("- 最大连接数: " + poolConfig.getMaxTotal());
        plugin.getLogger().info("- 最大空闲连接: " + poolConfig.getMaxIdle());
        plugin.getLogger().info("- 最小空闲连接: " + poolConfig.getMinIdle());
    }

    private void createTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS player_statistics (" +
                "player_name VARCHAR(16) PRIMARY KEY," +
                "blocks_broken INT NOT NULL DEFAULT 0," +
                "blocks_placed INT NOT NULL DEFAULT 0," +
                "mobs_killed INT NOT NULL DEFAULT 0," +
                "last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (Connection conn = mysqlPool.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
            plugin.getLogger().info("数据表 'player_statistics' 已检查/创建。");
        } catch (SQLException e) {
            plugin.getLogger().severe("创建数据表失败: " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(plugin);
        }
    }

    private void startAutoSaveTask() {
        int minutes = plugin.getConfig().getInt("auto_save_interval_minutes", 2);
        long ticks = minutes * 60 * 20L; // 转换为 Minecraft tick

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (dirtyPlayers.isEmpty()) return;
            plugin.getLogger().info("开始自动保存 " + dirtyPlayers.size() + " 名玩家的数据...");
            for (String playerName : new java.util.HashSet<>(dirtyPlayers)) { // 使用副本避免并发修改问题
                savePlayerToMySQL(playerName);
            }
        }, ticks, ticks);

        plugin.getLogger().info("已启动自动保存任务，每 " + minutes + " 分钟执行一次。");
    }

    private void flushAllToMySQL() {
        plugin.getLogger().info("正在将所有剩余数据刷新到 MySQL...");
        if (dirtyPlayers.isEmpty()) {
            plugin.getLogger().info("没有需要保存的数据。");
            return;
        }
        for (String playerName : new java.util.HashSet<>(dirtyPlayers)) {
            savePlayerToMySQL(playerName);
        }
        plugin.getLogger().info("所有数据已刷新到 MySQL。");
    }

    private void updateRedis(String playerName, String field, int delta) {
        try (Jedis jedis = redisPool.getResource()) {
            jedis.hincrBy("player_stats:" + playerName, field, delta);
            dirtyPlayers.add(playerName); // 标记为待同步
        } catch (Exception ex) {
            if (plugin.getConfig().getBoolean("debug")) {
                plugin.getLogger().warning("Redis error for " + playerName + ": " + ex.getMessage());
            }
        }
    }

    private int parseInt(String s) {
        return s == null || s.isEmpty() ? 0 : Integer.parseInt(s);
    }

    public void savePlayerToMySQL(String playerName) {
        Map<String, String> data;
        try (Jedis jedis = redisPool.getResource()) {
            data = jedis.hgetAll("player_stats:" + playerName);
        } catch (Exception e) {
            plugin.getLogger().severe("无法从 Redis 读取 " + playerName + ": " + e.getMessage());
            return;
        }

        if (data.isEmpty()) {
            dirtyPlayers.remove(playerName);
            return;
        }

        try (Connection conn = mysqlPool.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO player_statistics (player_name, blocks_broken, blocks_placed, mobs_killed) " +
                            "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                            "blocks_broken = blocks_broken + VALUES(blocks_broken), " +
                            "blocks_placed = blocks_placed + VALUES(blocks_placed), " +
                            "mobs_killed = mobs_killed + VALUES(mobs_killed)"
            );

            ps.setString(1, playerName);
            ps.setInt(2, parseInt(data.get("blocks_broken")));
            ps.setInt(3, parseInt(data.get("blocks_placed")));
            ps.setInt(4, parseInt(data.get("mobs_killed")));
            ps.executeUpdate();

            // 清理 Redis 已保存数据
            try (Jedis jedis = redisPool.getResource()) {
                jedis.hdel("player_stats:" + playerName, "blocks_broken", "blocks_placed", "mobs_killed");
            }
            dirtyPlayers.remove(playerName);
            if (plugin.getConfig().getBoolean("debug")) {
                plugin.getLogger().info("玩家 " + playerName + " 的数据已成功保存到 MySQL。");
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("数据库写入失败 " + playerName + "：" + e.getMessage());
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        updateRedis(p.getName(), "blocks_broken", 1);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        updateRedis(p.getName(), "blocks_placed", 1);
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        // 根据用户反馈，所有被玩家击杀的生物都算
        updateRedis(killer.getName(), "mobs_killed", 1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        String name = e.getPlayer().getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> savePlayerToMySQL(name));
    }
}
