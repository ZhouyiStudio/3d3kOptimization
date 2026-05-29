# 3d3kOptimization - README
# Purpur 1.21.1 服务器性能优化插件

## 概述

3d3kOptimization 是一个专为 **3d3kmc** 服务器设计的 Purpur 1.21.1 性能优化插件。
集成多种优化策略，全方位提升服务器性能。

## 功能特性

### 🎯 实体优化
- **掉落物合并** - 自动合并同类型掉落物，减少实体数
- **经验球合并** - 合并附近经验球
- **物品存活管理** - 延长有价值的物品存活时间
- **实体统计** - 实时查看各世界实体分布

### 🔴 红石优化
- **高频红石检测** - 自动检测并阻止高频红石脉冲
- **红石惩罚机制** - 对高频红石施加临时惩罚
- **活塞禁用** - 可选全局禁用活塞（缓解卡顿）

### 🗺️ 区块优化
- **智能区块卸载** - 自动卸载远离玩家的无用区块
- **视距优化** - 控制实体视距减少服务器负担
- **区块统计** - 查看各世界区块加载情况

### 👾 生物限制
- **每区块生物上限** - 控制区块内生物密度
- **每种生物限制** - 单独控制每种生物数量
- **全局实体上限** - 全服级生物数量控制
- **刷怪抑制** - 实体过多时自动停止刷怪

### 📊 性能监控
- **TPS 实时监控** - 跟踪服务器 Tick 速率
- **内存监控** - 监控 JVM 堆内存使用
- **低 TPS 告警** - TPS 过低时自动告警
- **性能报告** - 定时输出性能报告

### 🛠️ 管理命令
- `/3d3k help` - 显示帮助
- `/3d3k status` - 查看服务器状态
- `/3d3k entities` - 查看实体统计
- `/3d3k chunks` - 查看区块统计
- `/3d3k reload` - 重载配置（管理员）
- `/3d3k gc` - 手动 GC（管理员）

## 安装

1. 从 [Releases](https://github.com/AtomGit/3d3kOptimization/releases) 下载最新版本
2. 将 `3d3kOptimization-1.0.0.jar` 放入服务器的 `plugins` 文件夹
3. 重启服务器
4. 配置文件会自动生成在 `plugins/3d3kOptimization/config.yml`

## 构建

### 前置要求
- JDK 21+
- Maven 3.8+

### 编译步骤
```bash
git clone https://github.com/AtomGit/3d3kOptimization.git
cd 3d3kOptimization
mvn clean package
```

编译后的 JAR 文件位于 `target/3d3kOptimization-1.0.0.jar`

## 配置

配置文件位于 `plugins/3d3kOptimization/config.yml`，所有选项均有中文注释。
修改配置后执行 `/3d3k reload` 即可生效，无需重启服务器。

### 推荐配置（高性能模式）
```yaml
entity:
  activation-range:
    monster: 24
    animal: 32
    water: 12

chunk:
  view-distance: 6
  entity-view-distance: 3

mob-limiter:
  max-mobs-per-chunk: 20
  global:
    monsters: 150
```

## 依赖

- **Purpur 1.21.1**（或兼容的 Paper 1.21.1 服务端）
- 无其他外部依赖

## 许可

本项目使用 MIT 许可证。
