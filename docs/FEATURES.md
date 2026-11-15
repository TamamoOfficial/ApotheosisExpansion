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
