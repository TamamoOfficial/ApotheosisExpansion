# 功能详解

**中文** | [English](FEATURES_en.md)

[← 返回 README](../README.md)

本文档详细说明每个模块的**实现原理**与**设计取舍**。想快速了解能做什么，看 [README](../README.md) 即可。

---

## 目录

- [一、制作装备带神化属性](#一制作装备带神化属性)
- [二、刷怪蛋解锁](#二刷怪蛋解锁)
- [三、Bug 修复：合并修复配方不重掷词条](#三bug-修复合并修复配方不重掷词条)
- [四、宝石掉落调整](#四宝石掉落调整)
- [五、Thermal 联动](#五thermal-联动)
- [六、Mekanism Additions 联动](#六mekanism-additions-联动)
- [七、Ars Nouveau 联动](#七ars-nouveau-联动)
- [八、AllTheModium 联动](#八allthemodium-联动)
- [九、原版三大 BOSS](#九原版三大-boss)
- [十、Bug 修复：BOSS 不锁定创造/旁观玩家](#十bug-修复boss-不锁定创造旁观玩家)
- [十一、与神化刷怪笼的关系](#十一与神化刷怪笼的关系)
- [十二、多人共享刷怪额度](#十二多人共享刷怪额度)
- [十三、刷怪笼符文的-tooltip](#十三刷怪笼符文的-tooltip)

---

## 一、制作装备带神化属性

玩家**亲手制作**的装备会自动获得神化词条与宝石槽。

### 触发条件

- 必须是玩家合成（工作台、锻造台等）产生的物品
- 必须是装备类（可附词条的物品）
- 必须通过配置开关启用

### 稀有度范围

`min_rarity` / `max_rarity` 限定抽取区间，默认 `apotheosis:common` ~ `apotheosis:rare`。
设置 `respect_world_tier = true` 可让实际抽取上限额外受玩家当前世界层级约束。

### 跳过规则

`skip_if_already_affixed = true`（默认）时，已有词条的物品不再处理。

### 注入点为什么选 `AbstractContainerMenu.clicked`

而不是 `ItemCraftedEvent`：后者在部分合成路径（Shift 批量合成、配方书自动填充）下触发时机不稳定。
`clicked` 是「玩家从容器取出物品」的统一出口，能稳定捕获所有取出动作。

---

## 二、刷怪蛋解锁

原版刷怪笼对**坚守者**和**远古守卫者**的刷怪蛋有硬编码限制——放进去会被拒绝。
`SpawnerUnlockHandler` 移除该限制，两个解锁各自独立开关。

---

## 三、Bug 修复：合并修复配方不重掷词条

### 根因

神化的 `RepairItemRecipe` 判定「是否带属性」用的是一个模糊匹配。合并两个带词条的同款装备时，
结果物品会**重新随机一次词条**，洗掉玩家精心 roll 的属性。

### 修复

在 `clicked` 的 `HEAD` 处快照参与合并的两件物品，在 `TAIL` 处把词条原样继承给结果物品，
不再让 `assemble` 重掷。

---

## 四、宝石掉落调整

从 Gem Tweaks 模组迁移而来。按倍率缩放两项：

- 每个宝石的**掉落权重**（`gem_weight_multipliers`）
- 每个掉落来源的**触发概率**（`gem_drop_chance_multiplier`）

`gem_weight_multipliers` 是 `"id=倍率"` 列表，首次启动时自动填入当前已加载的全部宝石 id（倍率 1.0），
之后新增的宝石也会自动追加。

**生效时机**：数据包同步时与配置重载时各调度一次，`GemWeightTweaker` / `GemChanceTweaker`
通过反射修改神化的 `Gem.weights` 与 `GemLootModifier.entries`。

---

## 五、Thermal 联动

为热力（Thermal）的元素生物添加神化入侵者。

### 神化本体没有这层联动

全库搜索 `thermal` / `cofh` / `basalz` / `blizz` / `blitz` **零命中**。

### 加了什么

| 文件 | 实体 | 群系 |
|---|---|---|
| `thermal/basalz_peaks.json` | 岩石人 | 石峰 |
| `thermal/basalz_caves.json` | 岩石人 | 繁茂洞穴 |
| `thermal/basalz_nether.json` | 岩石人 | 玄武岩三角洲 |
| `thermal/blizz.json` | 暴雪人 | 积雪群系 |
| `thermal/blitz_badlands.json` | 狂风人 | 恶地 |
| `thermal/blitz_savanna.json` | 狂风人 | 热带草原 |
| `thermal/blitz_sandy.json` | 狂风人 | 沙质群系 |

**数值**：权重 40~60，稀有度按各群系的世界层级曲线。

**恶地被两条覆盖**（`blitz_badlands` 权重 50 + `blitz_sandy` 权重 40）是**有意为之**：
恶地既是「恶地」也是「沙质」群系，两条同时命中会让该群系的狂风人出现率更高。

---

## 六、Mekanism Additions 联动

为 Mekanism Additions 的宝宝实体添加神化入侵者。

### 实体从哪来

Mekanism 在 v10（1.15+）之后把实体部分拆给了官方附属 `mekanismadditions`。
宝宝实体位于那个模块里，**不在 Mekanism 主包内**。所以本联动的前置是 `mekanismadditions`，
不是 `mekanism`。

### 权重怎么定

取对应成年体入侵者权重的**三分之一**；凋灵骷髅宝宝取**四分之一**。

| 宝宝 | 权重 | 群系 |
|---|---|---|
| 末影人宝宝 | 17 | 主世界 / 末地 |
| 骷髅宝宝 | 33 | 主世界 |
| 流髑宝宝 | 40 | 主世界 / 寒冷群系 |
| 沼骸宝宝 | 40 | 主世界 / 潮湿群系 |
| 凋灵骷髅宝宝 | 25 | 下界要塞 |

### 唯一不做的一项：苦力怕宝宝

神化本体**没有苦力怕入侵者**，没有基准权重可参照，因此跳过。

### 凋灵骷髅宝宝：要塞结构刷怪的真相

一个容易搞错的地方：要塞刷出的凋灵骷髅，`MobSpawnType` 是 **`NATURAL`**，不是 `STRUCTURE`。

结构 `spawn_overrides` 只是替换群系刷怪表，运行时仍走 `NaturalSpawner` → `finalizeSpawn(NATURAL)`。
所以神化的入侵者入口**能**接住它们，数据文件可以直接生效。

---

## 七、Ars Nouveau 联动

为新生魔艺的荒野（Wilden）三兄弟添加神化入侵者。

### 这三个实体到底怎么生成的

不在 `biome_modifier` 里，而是写在**三个荒野巢穴结构的 `spawn_overrides`** 中。
与要塞同理：进入结构范围后群系刷怪表被整体替换，走 `NATURAL`。

### 加了什么

| 文件 | 实体 | 约束 |
|---|---|---|
| `ars_nouveau/wilden_hunter.json` | 荒野猎手 | `ars_nouveau:archwood_biome` |
| `ars_nouveau/wilden_stalker.json` | 荒野伏击者 | `ars_nouveau:archwood_biome` |
| `ars_nouveau/wilden_guardian.json` | 荒野守卫者 | `#c:is_tree/coniferous` |

### 一处必须说明的偏差：`haven` 层级无效

设计时计划覆盖 `haven ~ ascent`，但神化维度数据里 **`haven` 的入侵者概率本身就是 0.0**，
写进去等于永不触发。所以实际只写了 `frontier` + `ascent`。

---

## 八、AllTheModium 联动

为猪巫妖（Piglich）添加神化入侵者。

### 生成方式

猪巫妖只在**远古金字塔结构**里刷，用结构的 `spawn_overrides`：

```json
"spawn_overrides": {
  "monster": {
    "spawns": [{ "type": "allthemodium:piglich", "weight": 4500, "minCount": 1, "maxCount": 1 }]
  }
}
```

金字塔只在 `allthemodium:soul_sand_valley` 生成，属于 `allthemodium:the_other` 维度。
与要塞机制相同，走 `NATURAL`，因此**纯数据文件即可**。

### 随机装备：为什么需要事件钩子

AllTheModium 的 **1.20.x** 版本会让猪巫妖按 50% 概率逐件穿 ATM 套装：

```java
protected void populateDefaultEquipementSlots(DifficultyInstance diff) {
    if (this.isAdult()) {
        this.maybeWearArmor(EquipmentSlot.HEAD,  new ItemStack(ALLTHEMODIUM_HELMET));
        ...
    }
}
private void maybeWearArmor(EquipmentSlot slot, ItemStack stack) {
    if (this.level.random.nextFloat() < 0.5F) { this.setItemSlot(slot, stack); }
}
```

**但 1.21.1 重写时把这段连同幼年变种一起删掉了** —— `finalizeSpawn` 变成空实现，猪巫妖裸装生成。
而神化的 `GearSet.apply` 也无法表达「可能不穿」（它对每个槽位必然抽中一件）。

所以本模组用 `PiglichGearHook` 在 `FinalizeSpawnEvent` 里补回：

1. **逐槽位独立掷 50%** 穿 ATM 四件套
2. **主手 40% ATM 剑 / 60% 下界合金剑**（复刻 1.20.x 的 `createSpawnWeapon`）
3. **先穿装备，再调 `initBoss`** —— 顺序不能反，否则词条加不到装备上

只用 `allthemodium` 级别，不涉及 vibranium / unobtainium。

### 数值

猪巫妖本身属性极端（9999 生命、24 护甲、24 护甲韧性、12 攻击）：

| 稀有度 | 生命 | 攻击倍率 | 护甲 |
|---|---|---|---|
| epic | +160 ~ 300 | ×1.6 ~ 2.3 | +8 |
| mythic | +275 ~ 450 | ×2.75 ~ 3.63 | +25 |

击退抗性已 clamp 到 1.0 上限。

---

## 九、原版三大 BOSS

把**坚守者、凋灵、末影龙**变成神化 BOSS。

### 为什么必须用事件钩子，而不能用数据文件

神化只在 `FinalizeSpawnEvent` 里、且仅对 `NATURAL`/`CHUNK_GENERATION` 挑选入侵者。这三个都不满足：

| 实体 | 生成方式 | spawn type |
|---|---|---|
| 坚守者 | `SculkShriekerBlockEntity` → `SpawnUtil.trySpawnMob` | **`TRIGGERED`** |
| 凋灵 | `WitherSkullBlock` 建好后 `addFreshEntity` | 不经过 `finalizeSpawn` |
| 末影龙 | `EndDragonFight.createNewDragon` 后 `addFreshEntity` | 不经过 `finalizeSpawn` |

所以数据文件在这里**只作属性载体**，由代码按 id 查出来，再调 `Invader#initBoss` 施加到已存在的实体。

### 三个 BOSS 的设定

**坚守者**（`FinalizeSpawnEvent`，`TRIGGERED`）

- 尖啸体每次成功召唤时掷骰，**10%** 概率成为 BOSS
- 层级 **攀升 ~ 顶点**
- 稀有度只有 `epic` / `mythic`

**凋灵**（`EntityJoinLevelEvent`）

- 玩家搭建时掷骰，**10%** 概率
- **主世界**：攀升 ~ 高峰
- **下界**：攀升 ~ 顶点
- **其它维度**：保持原版

**末影龙**（`EntityJoinLevelEvent`）

- **仅本存档的第一只**，固定最高档稀有度
- 装了 Ancient Reforging 则用 `ancientreforging:ancient`（远古），否则 `apotheosis:mythic`（神话）

### 末影龙的「第一只」怎么判定

原版打龙成就**无法区分首杀**（它只看 `player_killed_entity` + 实体类型，第二只第三只一样触发）。
真正的首杀标记是 `EndDragonFight.previouslyKilled`：

```java
if (fight.hasPreviouslyKilledDragon()) {
    return;   // 不是第一只，保持原版
}
```

**只读不写**，因此不会和其它模组的龙蛋逻辑冲突——龙蛋掉落仍由原版自己决定。

### 远古稀有度的运行时选择

`ancientreforging` 是可选模组，没装时 `ancientreforging:ancient` 不存在。所以做了两条数据：

| 文件 | 稀有度 | 条件 |
|---|---|---|
| `vanilla_bosses/ender_dragon.json` | `apotheosis:mythic` | 无条件（兜底） |
| `vanilla_bosses/ender_dragon_ancient.json` | `ancientreforging:ancient` | `mod_loaded(ancientreforging)` |

运行时查 `RarityRegistry.INSTANCE.getValue()` 判断远古是否存在，存在则用远古，否则回退神话。

**为什么不能写成数据条件**：数据条件和稀有度在**同一轮注册表加载**里解析，条件看不到最终结果。

### 关于末影龙穿装备

`EnderDragon` 只继承到 `Mob`（不继承 `Monster`），但**没有重写任何装备方法**，
所以装备会正常写进 `armorItems`，属性与词条**都会生效**。三个已知特点：

- 模型上不显示（龙是自定义渲染，没有护甲层）
- 死亡不掉落（龙有自己的死亡处理）
- 护甲值确实生效（走 `getArmorValue()` → `Attributes.ARMOR`）

---

## 十、Bug 修复：BOSS 不锁定创造/旁观玩家

### 现象

神化生成的入侵者 BOSS 会主动锁定创造与旁观玩家，而原版怪物不会。

### 根因：原版过滤被 `setTarget` 绕过了

原版 AI 本身是正确的：`NearestAttackableTargetGoal` 用 `TargetingConditions.forCombat()` 筛选，
其中 `Player.canBeSeenAsEnemy()` 返回 `!getAbilities().invulnerable && ...`，
创造和旁观都 `invulnerable`，所以原版永远不会锁定他们。

问题在于神化生成时**直接 `Mob.setTarget(player)` 硬指定目标**，绕过了 AI 筛选。全库共两处，都不完整：

**缺口 1 —— 自然刷怪**

```java
if (rules.autoAggro().orElse(...) && !player.isCreative()) {   // 只挡创造，没挡旁观
    boss.setTarget(player);
}
```

**缺口 2 —— 刷怪笼**

```java
level.getEntities(PLAYER, aabb, EntitySelector.NO_CREATIVE_OR_SPECTATOR)...  // 选人时筛了
...
entity.setTarget(player);   // 40 tick 后无条件强制，没有复查
```

### 修复

用 `@Redirect` 拦掉这两处 `setTarget`，改用**原版同一个谓词**：

```java
if (target instanceof Player player && !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player)) {
    return;   // 交给原版 AI 继续忽略
}
```

对生存玩家毫无影响，难度与行为维持原样。

### 关于 `remap = false`

本项目用 `javac` 手工编译，**不产出 refmap**。运行环境是 `Service=ModLauncher`，
NeoForge 1.21.1 生产运行时使用官方类名，因此注解里写 `net.minecraft.world.entity.Mob;setTarget(...)`
可以直接匹配，`remap = false` 明确表达「不要重映射」，也避免 refmap 缺失警告。

---

## 十一、与神化刷怪笼的关系

### 神化自己的刷怪笼怎么工作

BOSS 刷怪笼方块会从入侵者注册表里**随机抽取**一条生成。本模组的联动条目也在同一个注册表里，
因此需要主动排除，否则会出现「刷怪笼里冒出一只岩石人 BOSS」这种不符合设计的情况。

### 本模组如何避免污染这个池子

`BossSpawnerTileMixin` 用 `@Redirect` 拦截刷怪笼的抽取调用，
`InvaderSpawnerFilter` 负责判断「这条是不是本模组的」：

```java
private static final Set<String> OWN_FOLDERS = Set.of(
    "thermal", "mekanism", "ars_nouveau", "allthemodium", "vanilla_bosses");
```

命中则**重掷**（最多 8 次），全部落空时保留原结果——这比不生成 BOSS（刷怪笼方块已被摧毁）要好。

只影响刷怪笼方块，自然刷怪、指令召唤、召唤器、传送门都不受影响。

### 数据目录命名

数据包里的五个目录直接以模组短名命名，与神化本体
（`overworld` / `the_nether` / `the_end` / `twilight`）的风格一致：

```
data/apotheosis/apothic_invaders/
├── thermal/          岩石人 / 暴雪人 / 狂风人
├── mekanism/         五种宝宝
├── ars_nouveau/      荒野猎手 / 伏击者 / 守卫者
├── allthemodium/     猪巫妖
└── vanilla_bosses/   坚守者 / 凋灵 / 末影龙
```

**新增联动目录时必须同步加进 `OWN_FOLDERS`**，否则该组条目会漏进刷怪笼。
`build/test_filter_predicate.js` 会双向核对：磁盘上每个目录都必须在集合里，
集合里每一项也必须在磁盘上存在。

---

## 十二、多人共享刷怪额度

### 原版上限是怎么算的

刷怪预算被**两处**同时限制，只放宽一处等于没放宽：

| 层级 | 位置 | 规则 |
|---|---|---|
| 每人 | `LocalMobCapCalculator.canSpawn` | 128 格内**每个**玩家各自有 70 只额度，**任意一人有余量**就允许刷 |
| 全维度 | `NaturalSpawner.SpawnState.canSpawnForCategory` | `70 × 所有玩家 17×17 刷怪区块框的并集 / 289` |

第二处是陷阱：它取的是**并集**。两人贴着站，两个 17×17 框几乎完全重合，并集仍约 289，
于是**还是只有一个人的量（70）**。这就是「两人一起挂机毫无收益」的根因。

### 本模组怎么改

两处上限同乘一个倍率：

```
multiplier = 1 + bonus × (n - 1)          n = 附近「同层级」玩家人数
```

默认 `bonus = 0.5`：

| 同层级人数 | 倍率 | 怪物上限 |
|---|---|---|
| 1 | ×1.0 | 70（原版） |
| 2 | ×1.5 | **105** |
| 3 | ×2.0 | **140** |
| 4 | ×2.5 | 175 |

想要「两人就满 140」，把 `mob_cap_bonus_per_player` 设成 `1.0`。

### 为什么只统计同层级

神化本身就按**最近玩家**的世界层级决定刷出的怪有多强（入侵者概率、层级增益、精英权重）。
若按总人数算，一个 haven 玩家站在 pinnacle 玩家旁边也能抬高额度，与神化的模型矛盾。
限制在单一层级内，两套系统才一致：**同层级组队有奖励，混层级组队没有**。
若附近有多个层级，取**最大的那一组**。

### 只影响敌对生物

`MobCapScaler.scaleCap` 对 `category.isFriendly()` 直接返回原值。动物、水生物沿用各自的原版上限——
放宽它们只会让被动农场通过无关的记账方式挤占敌对额度。

### 刷怪笼完全不受影响

`BaseSpawner` 走的是自己的 `MaxNearbyEntities`（默认 6），从不经过 `NaturalSpawner` 的上限判定。
本改动也不碰它。

### 实现落点

| 类 | 作用 |
|---|---|
| `spawn/MobCapScaler` | 倍率计算、同层级分组、按 (维度, tick, 区块) 的每 tick 备忘 |
| `mixin/spawn/LocalMobCapCalculatorMixin` | 在 `canSpawn` 头部压入本区块倍率 |
| `mixin/spawn/MobCountsMixin` | 重定向 `getMaxInstancesPerChunk()`，改掉每人的 70 |
| `mixin/spawn/SpawnStateMixin` | 重定向同一调用，改掉全维度的 70 |

倍率通过 `ThreadLocal<Deque<Double>>` 传递：`MobCounts` 是包私有嵌套类，只拿得到 `MobCategory`，
拿不到区块，只能由外层算好再取回。用双端队列而非单值，是为了在刷怪回调里再触发刷怪（重入）时，
外层帧的值不被内层覆盖。

倍率**保持小数**。若先取整，0.5 加成下两人会变成 ×2（140）而不是文档承诺的 ×1.5（105）。


### 与 HUD 模组的上限显示

MiniHUD 与 Servux **不读取**刷怪判定的结果，各自在代码里重算同一条公式：

```java
int capacity = category.getMaxInstancesPerChunk() * spawnableChunks / 289;
```

因此上限被放宽后，它们仍会显示 `x/70`——面板与实际不符。

`sync_mob_cap_to_hud`（**冷开关，默认关闭**）开启后，本模组用独立的
`apotheosis_expansion.compat.mixins.json` 重定向它们代码内的那一处
`getMaxInstancesPerChunk()` 调用，其余算式（乘区块数、除以 289）原样保留：

| 目标 mod | 类 → 方法 | 说明 |
|---|---|---|
| MiniHUD | `MobCapDataHandler` → `lambda$updateIntegratedServerMobCaps$1` | 单人 / 集成服本地计算 |
| Servux | `DataLoggerMobCaps` → `getResult` | 服务端推送，MiniHUD 优先读它 |

**关闭时完全没有副作用**：桥接类返回 `null`，处理函数直接回退到
`receiver.getMaxInstancesPerChunk()`，即未打补丁时的原值。

**为什么是独立配置 + 插件**：这两个 mixin 的目标类在别人的 mod 里。目标 mod 不存在时
Mixin 会解析失败。`CompatMixinPlugin` 在 `shouldApplyMixin` 里查 `ModList`，
**没装就根本不尝试**，不会产生任何报错。

**显示口径**：按维度的最大值（该维度内任意玩家所能达到的最高倍率），
与 HUD 本身的「全维度」口径一致；不是玩家脚下那一格的值。友好生物分类不缩放，
与刷怪侧完全一致。


### 假人与 HUD 兼容：三个只在运行期才暴露的坑

这三处都不会编译报错、不会在启动时报错，症状还高度相似，记录在此避免重蹈。

#### 一、mixin 包下不能放普通类

Mixin 把配置里的 `package` 视为**自己拥有的包**。包内任何类都会被当作 mixin 解析，
并且**禁止被外部直接引用**，否则运行期抛：

```
IllegalClassLoadError: ... is in a defined mixin package ... and cannot be referenced directly
```

崩溃发生在 `ServerChunkCache.tickChunks`（都是刷怪时才触发），**标题界面完全正常**，
所以「能进游戏」不能证明注入正确。

因此 `LocalMobCapAccess`、`MobCapHudBridge`、`CompatMixinPlugin` 都放在
`spawn/` 与 `compat/` 下，`mixin/` 里只留带 `@Mixin` 的类。
`build/verify_hud_compat.js` 第 1 节会强制检查这一点。

#### 二、探测目标类存在时，绝对不能用 `Class.forName`

`CompatMixinPlugin` 需要判断第三方 mod 是否在场。第一版只查 `ModList.isLoaded`，
结果在 Mixin 插件阶段它还没被填充，**三个目标全部返回 false**，所有 compat mixin 静默失效。

第二版加了 `Class.forName(className, false, loader)` 作为兜底——**这是更糟的错误**：
它会**定义并加载**该类，类进入加载器缓存后再也无法被 Mixin 转译。
插件于是正确报告 `enabled`，而 `Mixing ... into ...` 一次都不发生，
且因为配置是 `"required": false`，**没有任何报错**。

正确做法是**读资源、不定义类**：

```java
String resource = className.replace('.', '/') + ".class";
return loader.getResource(resource) != null;
```

`verify_hud_compat.js` 会剥离注释后搜索 `Class.forName`，防止这个写法回潮。

#### 三、假人的世界层级默认是 `HAVEN`

假人是真正的 `ServerPlayer`，但不走登录流程，`WorldTier` 附件停在默认 `HAVEN`。
共享额度只统计**同层级**玩家，所以假人挨着你也抬不高上限。

`fake_players_inherit_world_tier` 开启后，按以下优先级继承：

| 优先级 | 来源 | 能否覆盖已有层级 |
|---|---|---|
| 0 | `/player ... spawn` 的**发出者**（`CarpetPlayerCommandMixin` 在命令 HEAD 处捕获，命令方块也能识别） | ✅ |
| 1 | 同名的在线真人 | ✅ |
| 2 | 同 UUID 的在线真人（carpet 影子玩家） | ✅ |
| 3 | 同维度最近的真人 | ❌ 只补默认值 |

第 3 条是纯猜测，只用来补全未设置的层级；否则共享平台上的假人会在别人路过时莫名换主人。

**注意**：假人层级会写进 `playerdata`，重新召唤才更新，已存在的假人不会自动变。

### 排查手段

| 开关 | 日志标签 | 用途 |
|---|---|---|
| `debug_mob_cap` | `ApotheosisExpansion/MobCap` | 每个区块每 tick 列出附近玩家层级、分组、倍率 |
| 常开 | `ApotheosisExpansion/FakeTier` | 假人层级继承：从谁、走哪条路径、结果 |
| `debug_mob_cap` | `ApotheosisExpansion/HudHook` | MiniHUD 那次调用的存活证明（有它=注入已装且被调用） |

`HudHook` 那条尤其重要：它不出现，就说明注入没生效或 HUD 根本没走这条计算路径，
此时不要再猜「显示逻辑有问题」。

### 跨层级计数开关

默认只统计**同层级**玩家（与神化的层级模型一致）。`mob_cap_count_all_tiers`
打开后改为统计范围内**所有**非旁观玩家，即字面意义的 `70 × (1 + 0.5 × (附近人数 - 1))`：

| 场景 | 关闭（默认） | 开启 |
|---|---|---|
| 1 haven + 2 frontier | 只算 2 个 frontier → x1.5（105） | 算全部 3 个 → x2.0（140） |

两种模式共用同一套倍率公式与 `mob_cap_max_players` 上限，都会忽略旁观者与友好生物分类。

### 热更新：游戏内改完立即生效

所有怪物上限相关选项**都不需要重启、不需要重进、不需要 `/reload`**。

> **注意**：`/reload` 重载的是**数据包**（`MinecraftServer.reloadResources`
> 重建 `ReloadableServerResources`），**不会**重载模组的 TOML 配置。

生效路径有三条，任选其一：

1. **游戏内配置界面** — 本模组配置页改完点「Done」/「保存」，立即生效
2. **直接编辑 toml 文件并保存** — NeoForge 的 `ConfigTracker` 装了文件监视器，
   自动触发 `ModConfigEvent.Reloading`
3. 改完不需要任何命令

之所以能立即生效，靠的是三个设计：

| 设计 | 作用 |
|---|---|
| 选项**不**标记 `worldRestart`/`gameRestart` | 否则 NeoForge 会保留旧缓存值直到重启 |
| 每次使用都读 `Config.xxx()`，**不缓存到静态字段** | 否则值在类初始化时就被冻结 |
| 每次配置变化都 `MobCapScaler.clearCache()`，且缓存键带**世代计数** | 见下 |

**世代计数**是关键细节：只清空 Map 并不够。同一 tick 内一个区块可能被查询两次，
若清空发生在写入之前，那条旧值在结构上仍然合法、会被继续命中。
把 `GENERATION++` 折进缓存键后，旧条目变成**结构上不可能匹配**，
而不是依赖「先清空后读取」的顺序。

`build/test_hot_reload.js` 会用三项检查守住这些：无重启标记、无静态缓存、
缓存键含世代计数且 `clearCache` 会自增它。

**假人层级是个例外**：`fake_players_inherit_world_tier` 的判定发生在假人**进服那一刻**，
所以改开关后需要**重新召唤假人**才生效——这不是缓存问题，是事件本身只有一次。

---

## 十三、刷怪笼符文的 tooltip

当前版本的神化把刷怪笼的强化手段从「用一堆材料去操作调节器」换成了**符文**：每枚升级符文
（拓荒者 / 旅人 / 登山者 / 雷霆）本质上是一张把刷怪笼五项数值直接**设**为某个值的配方。
问题是物品本身只写了一句话——「略微提升刷怪笼的性能」——真正的数值藏在 JEI/EMI 的**用途页**里，
而且必须点进去、逐条悬停才看得清。

### 数值从哪来

**不写死，直接读配方。** 每一枚符文对应一条 `apothic_spawners:spawner_modifier` 配方：

```json
{
  "type": "apothic_spawners:spawner_modifier",
  "mainhand": { "item": "apotheosis:frontier_spawner_upgrade_rune" },
  "stat_changes": [
    { "type": "apothic_spawners:min_delay",           "mode": "set", "value": 150 },
    { "type": "apothic_spawners:max_delay",           "mode": "set", "value": 600 },
    { "type": "apothic_spawners:spawn_count",         "mode": "set", "value": 7   },
    { "type": "apothic_spawners:max_nearby_entities", "mode": "set", "value": 12  },
    { "type": "apothic_spawners:req_player_range",    "mode": "set", "value": 24  }
  ]
}
```

客户端本来就从服务端同步了这份配方表（JEI 用它渲染用途页），所以 tooltip 直接向
`RecipeManager.getAllRecipesFor(...)` 询问「主手是这枚符文的配方有哪些」，再把
`StatModifier` 列表解码出来。**因此改数据包就改显示**：整合包调过数值、或者别的模组
自带符文，都无需改动本模组一行代码。

这也是为什么工具提示的值**不会**过时——它每次绘制时读的都是当前生效的配方。

### 三种数值的显示方式

| 配方写法 | tooltip |
|---|---|
| `"mode": "set"`, 数值 | `最小生成延迟：150`（绿色） |
| `"mode": "set"`, 布尔 | `启用：无视光照` / `禁用：无视光照` |
| 默认 `add` 模式 | `+2 生成范围（上限 32）` |

名称与单位（刻、百分比、等级用罗马数字）都复用 Apothic Spawners 自己的 `SpawnerStat#name()`
与 `#formatValue()`，所以翻译和格式化规则只有一份。

**上限/下限也会显示**，因为它是真实行为的一部分：生成范围符文每次 +2、最多到 32，
若只写「+2」会让人以为能无限叠加。`echoing` 的 `max: 3` 同理。

### 只显示「主手单独使用」的那条

同一个符文可能有多条配方：本体给的、以及 `spawner_modifiers/_inverse/` 里那条
「主手符文 + 副手石英 = 把效果关掉」的反向配方。只描述**副手为空**的那条，
否则 tooltip 会自相矛盾（既说开启又说关闭）。

### 缓存与重载

tooltip 每帧都会重新收集，所以扫描结果按**物品**记忆化。缓存在两个时机清空：

| 事件 | 为什么需要 |
|---|---|
| `RecipesUpdatedEvent` | 客户端收到新配方表（进服、`/reload`）时，旧扫描结果立刻失效 |
| `OnDatapackSyncEvent` | 数据包重载完成；整合端时客户端缓存需要同步丢弃 |

配置开关本身**不需要**清缓存——每次绘制都读 `Config.enableSpawnerRuneTooltips()`，可直接热更新。

### 可选依赖的隔离

Apothic Spawners 是可选依赖。任何提到 `dev.shadowsoffire.apothic_spawners` 的类在链接时就会
抛 `NoClassDefFoundError`，所以所有相关引用都被关进 `spawnerrune/SpawnerRuneTooltips.java`，
而 `spawnerrune/SpawnerRuneIntegration.java`（不含任何该模组的类型）负责先查 `ModList`：

```java
if (FMLEnvironment.dist.isClient() && SpawnerRuneIntegration.isAvailable()) {
    ClientIntegration.registerSpawnerRuneTooltips();
}
```

客户端专属的 `RecipesUpdatedEvent` 因此只能注册在 `ClientIntegration` 里，不能写在公共路径上
——否则专用服务器会去解析一个客户端事件类。

未装 Apothic Spawners 时：`[spawner]` 下不会出现这个配置项，功能整体不存在，也没有报错。

`build/verify_spawner_rune_tooltips.js` 静态守住这些约定：API 是否还在、四枚升级符文是否仍是
五项全设、`spawnerrune/` 之外是否出现该模组的类型引用、以及缓存失效是否真的接在事件上。
