# 配置与构建

**中文** | [English](CONFIG_en.md)

[← 返回 README](../README.md) · [功能详解](FEATURES.md)

---

## 配置文件

位于 `config/apotheosis_expansion-common.toml`，共 **5 个段、47 个配置项**
（31 个字面配置 + 16 个按模组开关，后者仅在对应模组安装时注册）。

---

## 多级菜单

游戏内配置界面是**每个模组一个子段**：

```
神化扩展 — 设置
└── 联动 (compat)
    ├── Thermal 联动            ← 子段
    │   ├── enable_thermal_invaders    总开关
    │   ├── enable_basalz_invader      岩石人
    │   ├── enable_blizz_invader       暴雪人
    │   └── enable_blitz_invader       狂风人
    ├── Mekanism Additions 联动 ← 子段
    │   ├── enable_mekanism_invaders   总开关
    │   └── ... 5 个宝宝开关
    ├── Ars Nouveau 联动        ← 子段
    │   └── ... 3 个荒野开关
    ├── AllTheModium 联动       ← 子段
    │   └── enable_piglich_boss
    └── 原版 BOSS 联动          ← 子段
        ├── enable_vanilla_bosses      总开关
        ├── enable_warden_boss
        ├── enable_wither_boss
        └── enable_ender_dragon_boss
```

### 未安装的模组不注册

子段只在对应模组已安装时才注册。所以没装 Mekanism Additions 的整合包**完全看不到** Mek 那一组，
不会出现「开关存在但永远无效」的假象。

代价是配置文件在不同环境下结构不同——换到没装该模组的环境，该子段的值会消失。

---

## 配置项完整清单

| 段 | 键 | 说明 |
|---|---|---|
| `crafting` | `enable_affixes` | 制作装备是否自动带神化词条 |
| `crafting` | `affix_chance` | 触发概率（0~1） |
| `crafting` | `enable_sockets` | 制作装备是否自动带宝石槽 |
| `crafting/socket_range` | `min` / `max` | 最少 / 最多槽位数 |
| `crafting` | `socket_chance` | 触发概率（0~1） |
| `crafting` | `skip_if_already_affixed` | 已带词条的物品跳过 |
| `crafting` | `allow_non_equipment` | 是否也处理非装备物品 |
| `crafting` | `inherit_on_repair_combine` | 合并修复时继承输入词条（合并产物本身永不重掷，不可配置） |
| `rarity` | `min_rarity` | 抽取稀有度下限 |
| `rarity` | `max_rarity` | 抽取稀有度上限 |
| `rarity` | `respect_world_tier` | 上限是否再受世界层级约束 |
| `spawner` | `enable_warden_unlock` | 允许坚守者刷怪蛋进入刷怪笼 |
| `spawner` | `enable_elder_guardian_unlock` | 允许远古守卫者刷怪蛋进入刷怪笼 |
| `spawner` | `enable_spawner_rune_tooltips` | 在符文 tooltip 里列出它改动的数值（需装 Apothic Spawners；仅客户端） |
| `player` | `enable_respawn_health_fix` | 复活时按真实最大生命值回满血 |
| `player` | `enable_mob_cap_scaling` | 按同层级玩家人数成倍放宽怪物上限 |
| `player` | `mob_cap_count_all_tiers` | 统计附近全部玩家、忽略层级（默认关） |
| `player` | `mob_cap_bonus_per_player` | 每多一名玩家的上限倍率（默认 0.5） |
| `player` | `mob_cap_max_players` | 参与倍率计算的玩家人数上限 |
| `player` | `sync_mob_cap_to_hud` | 把放宽后的上限同步给 MiniHUD / Servux（冷开关） |
| `player` | `fake_players_inherit_world_tier` | 假人继承召唤者的世界层级 |
| `player` | `debug_mob_cap` | 记录上限倍率的计算过程（排查用） |
| `player` | `enable_spawn_lanes` | 区域难度足够高时每区块多跑一轮刷怪尝试 |
| `player` | `spawn_lanes_min_regional_difficulty` | 开启第二轮刷怪所需的区域难度（默认 5.5） |
| `player` | `spawn_lanes_real_players_only` | 难度判定只统计真实玩家（默认开） |
| `adventure` | `enable_named_peaceful_protection` | 带 `apotheosis_expansion.named` 标签的生物在和平模式不消失 |
| `adventure` | `enable_named_piglin_no_zombify` | 命名猪灵/猪灵蛮兵在主世界不会变僵尸猪灵 |
| `gems` | `enable_gem_compendium` | 在 JEI/EMI 中加入宝石图鉴页（仅装有查看器时出现该项） |
| `gems` | `enable_gem_tweaks` | 宝石掉落调整总开关 |
| `gems` | `gem_drop_chance_multiplier` | 掉落概率整体倍率 |
| `gems` | `gem_weight_multipliers` | `"id=倍率"` 列表 |
| `compat/thermal` | `enable_thermal_invaders` | 热力元素生物联动总开关 |
| `compat/thermal` | `enable_basalz_invader` | 岩石人 |
| `compat/thermal` | `enable_blizz_invader` | 暴雪人 |
| `compat/thermal` | `enable_blitz_invader` | 狂风人 |
| `compat/mekanism` | `enable_mekanism_invaders` | 通用机械宝宝联动总开关 |
| `compat/mekanism` | `enable_baby_enderman_invader` | 末影人宝宝 |
| `compat/mekanism` | `enable_baby_skeleton_invader` | 骷髅宝宝 |
| `compat/mekanism` | `enable_baby_stray_invader` | 流髑宝宝 |
| `compat/mekanism` | `enable_baby_bogged_invader` | 沼骸宝宝 |
| `compat/mekanism` | `enable_baby_wither_skeleton_invader` | 凋灵骷髅宝宝 |
| `compat/mekanism_tools` | `enable_mekanism_tools_affixes` | 工具模块装备进词缀战利品池 |
| `compat/mekanism_tools` | `enable_mekanism_tools_salvaging` | 工具模块装备的回收台配方 |
| `compat/mekanism_tools` | `enable_mekanism_tools_gear_sets` | 入侵者穿戴工具模块套装 |
| `compat/mekanism_energy_gear` | `block_mekanism_energy_gear_affixes` | 能量装备不参与打造词条 |
| `compat/ars_nouveau` | `enable_ars_nouveau_invaders` | 新生魔艺荒野联动总开关 |
| `compat/ars_nouveau` | `enable_wilden_hunter_invader` | 荒野猎手 |
| `compat/ars_nouveau` | `enable_wilden_stalker_invader` | 荒野伏击者 |
| `compat/ars_nouveau` | `enable_wilden_guardian_invader` | 荒野守卫者 |
| `compat/allthemodium` | `enable_allthemodium_invaders` | AllTheModium 猪巫妖联动总开关 |
| `compat/allthemodium` | `enable_piglich_boss` | 猪巫妖 |
| `compat/vanilla_bosses` | `enable_vanilla_bosses` | 原版三大 BOSS 联动总开关 |
| `compat/vanilla_bosses` | `enable_warden_boss` | 坚守者（10% 概率） |
| `compat/vanilla_bosses` | `enable_wither_boss` | 凋灵（10% 概率） |
| `compat/vanilla_bosses` | `enable_ender_dragon_boss` | 末影龙（仅首杀） |

---

## 热重载

先区分两件事：

- **模组 TOML 配置**：NeoForge 的 `ConfigTracker` 监视文件，**保存即生效**，不需要 `/reload`，也不需要重启。
- **数据包**：`/reload`（或客户端 `F3+T`）重载的是资源包与数据包，**与 TOML 无关**。

### 按类型归类

| 类别 | 需要的操作 | 为什么 |
|---|---|---|
| 制作装备（词条 / 宝石槽 / 稀有度 / 修复合并） | **保存即生效** | 事件触发时实时读配置 |
| 刷怪蛋解锁（坚守者 / 远古守卫者） | **保存即生效** | 交互时实时读配置 |
| 复活回血 / 命名生物和平保护 | **保存即生效** | 事件触发时实时读配置 |
| 命名猪灵免转化 | **保存即生效** | Mixin 每 tick 实时读配置 |
| 怪物上限（缩放 / 层级 / 倍率 / 上限人数） | **保存即生效** | 每次刷怪判定实时读，缓存键含世代计数 |
| HUD 同步 / 调试日志 | **保存即生效** | 每次调用实时读 |
| 宝石掉落倍率 | **保存即生效** | 配置重载与数据包重载时自动重建 |
| **各类入侵者开关**（含 15 个单实体开关） | **重载数据包** | 作用于数据包条件，只在解析时求值一次 |
| 假人层级继承 | **保存即生效，但需重新召唤假人** | 判定发生在假人进服那一刻 |
| 增删数据文件 | **重载数据包** | 数据包内容只在重载时重新解析 |

### 为什么入侵者开关属于特例

它们写在 TOML 里，却必须重载数据包：这些开关通过 `ICondition` 决定
「某个入侵者 JSON 条目要不要注册进注册表」。数据包解析完成后注册表已定型，
改开关不会回滚已注册的条目，所以必须重新解析一遍。

### 不需要重启的保证从哪来

三个设计约束，缺一不可：

| 约束 | 若不满足 |
|---|---|
| 选项**不**标记 `worldRestart` / `gameRestart` | NeoForge 会保留旧缓存值直到重启 |
| 每次使用读 `Config.xxx()`，**不缓存到静态字段** | 值在类初始化时被冻结 |
| 缓存类数据（如上限倍率）在配置变化时清空，且缓存键含**世代计数** | 旧值可能在同一 tick 内继续命中 |

`build/test_hot_reload.js` 会针对前两条与世代计数做静态校验。

---

## 从源码构建

**环境**：JDK 21、Gradle 8.x

```bash
./gradlew build
```

产物在 `build/libs/ApotheosisExpansion-1.21.1-neoforge-1.2.0.jar`。

**依赖说明**：Apotheosis / Placebo / Apothic Attributes 从
[Shadow's Maven](https://maven.shadowsoffire.dev/releases/dev/shadowsoffire/) 解析：

```gradle
compileOnly "dev.shadowsoffire:Apotheosis:${mcVersion}-${apotheosisVersion}"
compileOnly "dev.shadowsoffire:Placebo:${mcVersion}-${placeboVersion}"
compileOnly "dev.shadowsoffire:ApothicAttributes:${mcVersion}-${apothicAttributesVersion}"
```

**克隆后无需手动准备任何 jar。** 若该仓库不可达，可自行下载放进 `libs/` 并临时改回 flatDir。

### 本机 Gradle 无法启动

开发机上所有 Gradle 版本都因 `Failed to load native library 'native-platform.dll'` 无法启动
（环境问题），因此实际使用 `javac` 直接编译：

```powershell
javac -encoding UTF-8 -proc:none --release 21 -sourcepath <空目录> -classpath $cp -d build/classes <源文件...>
```

**`-sourcepath <空目录>` 不能省。** 否则 javac 的隐式源码发现会把 classpath 里 jar 的类反编译重编到
输出目录——实测会混进 1081 个 NeoForge 类。

上面两步已经脚本化，直接用：

```bash
node build/compile.js     # 编译到 build/classes（自动找 JDK 21、拼 classpath）
node build/package.js     # 打包到 build/libs/
```

打包这一步等价于手写：

```bash
jar --create --file <目标>.jar --manifest <清单> -C <暂存目录> .
```

### 校验脚本

`build/` 下的静态校验脚本覆盖数据合法性、mixin 目标、条件 codec、过滤器谓词、符文 tooltip、README 链接等：

```bash
node build/validate_invaders.js thermal
node build/verify_mixin.js
node build/test_filter_predicate.js
node build/verify_repair_combine.js
node build/verify_spawner_rune_tooltips.js
node build/test_spawner_rune_tooltips.js
node build/verify_readme.js
# ...
```

项目不做实机自动化测试，验证以静态检查为主。

---

## 源码结构

```
src/main/java/com/tamamo/apotheosisexpansion/
├── ApotheosisExpansion.java        主类，注册配置/监听/客户端界面
├── Config.java                     ModConfigSpec（4 段 + 按模组分的 compat 子段）
├── RaritySelector.java             稀有度抽取 + 上下限截断
├── CraftedAffixRoller.java         词条/槽位生成核心
├── SpawnerUnlockHandler.java       刷怪蛋解锁
├── RepairCombineInheritance.java   合并修复的词条继承
├── ClientIntegration.java          仅客户端，隔离加载
├── compat/
│   ├── ThermalCompat.java              条件类型注册
│   ├── ConfigConditions.java           把开关映射成数据包条件
│   ├── EntityInvaderEnabled.java       单实体开关条件（带 entity 参数）
│   ├── InvaderLookup.java              共享的条目查找与稀有度选择
│   ├── InvaderSpawnerFilter.java       刷怪笼排除本模组条目的判定
│   ├── VanillaBossHooks.java           给原版 BOSS 施加神化数据
│   ├── VanillaBossEvents.java          坚守者/凋灵/末影龙的生成钩子
│   └── PiglichGearHook.java            猪巫妖随机 ATM 装备 + 神化化
├── mixin/
│   ├── AbstractContainerMenuMixin.java 制作装备注入
│   ├── BossSpawnerTileMixin.java       刷怪笼抽 boss 时排除联动条目
│   └── CreativeTargetFilter.java       BOSS 不锁定创造/旁观玩家
├── gems/
│   ├── GemWeightTweaker.java       反射改 Gem.weights
│   ├── GemChanceTweaker.java       反射改 GemLootModifier.entries
│   └── GemTweakApplier.java        数据包同步 / 配置重载时调度
└── spawnerrune/
    ├── SpawnerRuneIntegration.java 可选依赖的 ModList 闸门（不含该模组类型）
    └── SpawnerRuneTooltips.java    读 spawner_modifier 配方生成 tooltip 行
```

数据包目录：

```
src/main/resources/data/apotheosis/apothic_invaders/
├── thermal/          7 条
├── mekanism/         8 条
├── ars_nouveau/      3 条
├── allthemodium/     1 条
└── vanilla_bosses/   4 条
```
