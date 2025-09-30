# PlayerStatsLogger

一个简单的Minecraft Bukkit/Spigot插件，用于收集和记录玩家的基本统计数据。

## 功能简介

PlayerStatsLogger是一个轻量级的统计数据收集插件，专门用于记录以下三项基本玩家数据：

- 🟫 **挖掘方块数量** - 记录玩家破坏的方块总数
- 🟩 **放置方块数量** - 记录玩家放置的方块总数  
- ⚔️ **击杀怪物数量** - 记录玩家击杀的所有生物数量

### 注意事项
- 本插件**仅负责数据收集**，不提供数据分析、排行榜或可视化功能
- 数据会自动保存到MySQL数据库，便于其他插件或工具进行查询和分析
- 使用Redis作为缓存层，提高性能并减少数据库压力

## 系统要求

### 服务器
- Minecraft 1.21+ (支持Bukkit/Spigot/Paper等服务端)
- Java 17或更高版本

### 外部服务
- **MySQL数据库** (版本5.7+ 或 8.0+)
- **Redis服务器** (版本5.0+)

## 安装说明

### 1. 下载插件
将`PlayerStatsLogger.jar`文件放入服务器的`plugins`文件夹

### 2. 启动服务器
启动服务器，插件会自动：
- 创建`PlayerStatsLogger`配置文件夹
- 生成默认配置文件
- 创建必要的数据表

### 3. 配置数据库
编辑`PlayerStatsLogger/config.yml`文件，配置数据库连接信息：

```yaml
# MySQL 数据库配置
database:
  host: "localhost"        # 数据库地址
  port: 3306              # 数据库端口
  name: "minecraft_stats" # 数据库名称
  username: "mc_stats"    # 数据库用户名
  password: "your_password" # 数据库密码
  use_ssl: false         # 是否使用SSL连接
  auto_reconnect: true   # 自动重连

# Redis 缓存配置
redis:
  host: "localhost"       # Redis地址
  port: 6379             # Redis端口
  password: ""           # Redis密码（无密码留空）
  database: 0            # Redis数据库编号
  timeout_ms: 2000       # 连接超时时间
```

### 4. 重载插件
使用命令重载配置：
```
/psl reload
```

## 配置说明

### 主配置文件 (config.yml)

```yaml
# MySQL 数据库配置
database:
  host: "localhost"
  port: 3306
  name: "minecraft_stats"
  username: "mc_stats"
  password: "secure_password_123"
  use_ssl: false
  auto_reconnect: true

# Redis 缓存配置
redis:
  host: "localhost"
  port: 6379
  password: ""
  database: 0
  timeout_ms: 2000
  # 连接池配置
  max_total: 20
  max_idle: 10
  min_idle: 2
  max_wait_ms: 3000
  test_on_borrow: true
  test_on_return: true
  test_while_idle: true

# 插件运行参数
debug: false                    # 调试模式（建议日常关闭）
auto_save_interval_minutes: 2   # 自动保存间隔（分钟）
```

### 配置参数说明

#### 数据库配置
- `database.host`: MySQL服务器地址
- `database.port`: MySQL服务器端口
- `database.name`: 数据库名称
- `database.username`: 数据库用户名
- `database.password`: 数据库密码
- `database.use_ssl`: 是否启用SSL连接
- `database.auto_reconnect`: 是否自动重连

#### Redis配置
- `redis.host`: Redis服务器地址
- `redis.port`: Redis服务器端口
- `redis.password`: Redis密码
- `redis.database`: Redis数据库编号
- `redis.timeout_ms`: 连接超时时间（毫秒）
- `redis.max_total`: 最大连接数
- `redis.max_idle`: 最大空闲连接数
- `redis.min_idle`: 最小空闲连接数
- `redis.max_wait_ms`: 获取连接最大等待时间

#### 插件参数
- `debug`: 调试模式开关，开启后会输出详细日志
- `auto_save_interval_minutes`: Redis数据自动同步到MySQL的时间间隔

## 数据库结构

插件会自动创建以下数据表：

```sql
CREATE TABLE player_statistics (
    player_name VARCHAR(16) PRIMARY KEY,
    blocks_broken INT NOT NULL DEFAULT 0,
    blocks_placed INT NOT NULL DEFAULT 0,
    mobs_killed INT NOT NULL DEFAULT 0,
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 字段说明
- `player_name`: 玩家名称（主键）
- `blocks_broken`: 挖掘方块总数
- `blocks_placed`: 放置方块总数
- `mobs_killed`: 击杀怪物总数
- `last_seen`: 最后活动时间

## 命令说明

### 主命令
```
/psl 或 /playerstats 或 /stats
```

### 子命令

#### 查看插件状态
```
/psl status
```
- 显示插件运行状态
- 显示版本信息
- 显示配置文件夹路径
- 显示数据库连接状态

#### 重载配置文件
```
/psl reload
```
- 重新加载配置文件
- 重启插件功能模块
- 重新建立数据库连接

#### 查看帮助
```
/psl
```
- 显示所有可用命令和说明

### 权限要求
所有插件命令都需要以下权限：
- `playerstatslogger.admin`

## 工作原理

### 数据收集流程
1. **玩家操作** → 玩家挖掘/放置方块或击杀怪物
2. **事件监听** → 插件捕获相关事件
3. **Redis缓存** → 数据先写入Redis提高性能
4. **定时同步** → 每隔指定时间将Redis数据同步到MySQL
5. **持久化存储** → 数据最终保存到MySQL数据库

### 性能优化
- **Redis缓存**: 减少直接数据库操作，提高响应速度
- **异步处理**: 所有数据库操作都在异步线程中执行
- **批量保存**: 定时批量保存数据，减少数据库压力
- **连接池管理**: 使用连接池优化数据库和Redis连接

## 故障排除

### 常见问题

#### 1. Redis连接错误
```
[PlayerStatsLogger] Redis error for playername: Could not get a resource from the pool
```
**解决方案**：
- 检查Redis服务器是否正常运行
- 验证Redis连接配置是否正确
- 调整Redis连接池参数（增加max_total）

#### 2. 数据库连接失败
```
[PlayerStatsLogger] 创建数据表失败: Access denied for user
```
**解决方案**：
- 检查MySQL服务器是否运行
- 验证数据库用户名和密码
- 确保数据库用户有足够权限

#### 3. 数据没有保存
**解决方案**：
- 检查`auto_save_interval_minutes`设置是否合理
- 开启debug模式查看详细日志
- 确认Redis和MySQL连接正常

### 调试方法
1. **开启调试模式**：在config.yml中设置`debug: true`
2. **重载插件**：使用`/psl reload`命令
3. **查看日志**：观察控制台输出的调试信息
4. **检查状态**：使用`/psl status`查看连接状态

## 数据查询示例

由于本插件只负责数据收集，您可能需要其他工具来查询和分析数据。以下是一些常用的SQL查询示例：

### 查询所有玩家统计数据
```sql
SELECT player_name, blocks_broken, blocks_placed, mobs_killed, last_seen
FROM player_statistics
ORDER BY last_seen DESC;
```

### 查询特定玩家数据
```sql
SELECT player_name, blocks_broken, blocks_placed, mobs_killed
FROM player_statistics
WHERE player_name = '玩家名称';
```

### 查询排行榜（挖掘方块）
```sql
SELECT player_name, blocks_broken
FROM player_statistics
ORDER BY blocks_broken DESC
LIMIT 10;
```

## 更新日志

### v1.0
- 初始版本发布
- 实现基本的玩家统计数据收集功能
- 支持MySQL + Redis双存储架构
- 添加插件管理命令
- 优化连接池配置

## 技术支持

如果您遇到问题或有建议，请：
1. 检查本文档的故障排除部分
2. 开启调试模式查看详细日志
3. 确认服务器和外部服务配置正确
4. 联系作者：**haozi@haoziwan.cn**

## 许可证

本插件采用开源许可证，详情请参阅LICENSE文件。

---

**注意**：本插件专注于数据收集功能，不包含数据分析、可视化或Web界面等高级功能。如需这些功能，建议配合其他插件或自定义开发使用。
