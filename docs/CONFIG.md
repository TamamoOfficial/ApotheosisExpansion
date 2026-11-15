# 配置与构建

**中文** | [English](CONFIG_en.md)

[← 返回 README](../README.md) · [功能详解](FEATURES.md)

---

## 配置文件

位于 `config/apotheosis_expansion-common.toml`，共 **4 个段、37 个配置项**
（22 个字面配置 + 15 个按实体开关，后者仅在对应模组安装时注册）。

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
| `crafting` | `inherit_on_repair_combine` | 合并修复时继承词条（bug 修复开关） |
| `rarity` | `min_rarity` | 抽取稀有度下限 |
| `rarity` | `max_rarity` | 抽取稀有度上限 |
| `rarity` | `respect_world_tier` | 上限是否再受世界层级约束 |
| `spawner` | `enable_warden_unlock` | 允许坚守者刷怪蛋进入刷怪笼 |
| `spawner` | `enable_elder_guardian_unlock` | 允许远古守卫者刷怪蛋进入刷怪笼 |
| `gems` | `enable_gem_tweaks` | 宝石掉落调整总开关 |
| `gems` | `gem_drop_chance_multiplier` | 掉落概率整体倍率 |
| `gems` | `gem_weight_multipliers` | `"id=倍率"` 列表 |
| `compat/thermal` | `enable_thermal_invaders` | 热力元素生物联动总开关 |
| `compat/thermal` | `enable_basalz_invader` | 岩石人 |
| `compat/thermal` | `enable_blizz_invader` | 暴雪人 |
| `compat/thermal` | `enable_blitz_invader` | 狂风人 |
| `compat/mekanism` | `enable_mekanism_invaders` | Mekanism 宝宝联动总开关 |
| `compat/mekanism` | `enable_baby_enderman_invader` | 末影人宝宝 |
| `compat/mekanism` | `enable_baby_skeleton_invader` | 骷髅宝宝 |
| `compat/mekanism` | `enable_baby_stray_invader` | 流髑宝宝 |
| `compat/mekanism` | `enable_baby_bogged_invader` | 沼骸宝宝 |
| `compat/mekanism` | `enable_baby_wither_skeleton_invader` | 凋灵骷髅宝宝 |
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

| 改动 | 需要什么 |
|---|---|
| 制作 / 刷怪蛋 / 宝石倍率 | 游戏内配置界面即时生效 |
| 各类入侵者开关 | **重载数据**（`F3+T` 或重进存档） |
| 增删数据文件 | 同上 |

入侵者开关作用于**数据包条件**，仅在数据包重载时求值，所以只重载配置不够。

---

## 从源码构建

**环境**：JDK 21、Gradle 8.x

```bash
./gradlew build
```

产物在 `build/libs/ApotheosisExpansion-1.21.1-neoforge-1.0.0.jar`。

**依赖说明**：Apotheosis / Placebo / Apothic Attributes 通过本地 `libs/` 目录的 flatDir 仓库引用
（这些 jar 在公共 Maven 上不稳定可得）。若改用公共仓库，请相应修改 `build.gradle` 的 `repositories`。

### 本机 Gradle 无法启动

开发机上所有 Gradle 版本都因 `Failed to load native library 'native-platform.dll'` 无法启动
（环境问题），因此实际使用 `javac` 直接编译：

```powershell
javac -encoding UTF-8 -proc:none --release 21 -sourcepath <空目录> -classpath $cp -d build/classes <源文件...>
```

**`-sourcepath <空目录>` 不能省。** 否则 javac 的隐式源码发现会把 classpath 里 jar 的类反编译重编到
输出目录——实测会混进 1081 个 NeoForge 类。

打包：

```bash
jar --create --file <目标>.jar --manifest <清单> -C <暂存目录> .
```

### 校验脚本

`build/` 下有 10 个静态校验脚本，覆盖数据合法性、mixin 目标、条件 codec、过滤器谓词、README 链接等：

```bash
node build/validate_invaders.js thermal
node build/verify_mixin.js
node build/test_filter_predicate.js
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
└── gems/
    ├── GemWeightTweaker.java       反射改 Gem.weights
    ├── GemChanceTweaker.java       反射改 GemLootModifier.entries
    └── GemTweakApplier.java        数据包同步 / 配置重载时调度
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
