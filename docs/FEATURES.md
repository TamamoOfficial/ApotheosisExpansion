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
- [七、Mekanism: Tools 联动](#七mekanism-tools-联动)
- [八、Ars Nouveau 联动](#八ars-nouveau-联动)
- [九、AllTheModium 联动](#九allthemodium-联动)
- [十、原版三大 BOSS](#十原版三大-boss)
- [十一、Bug 修复：BOSS 不锁定创造/旁观玩家](#十一bug-修复boss-不锁定创造旁观玩家)
- [十二、与神化刷怪笼的关系](#十二与神化刷怪笼的关系)
- [十三、多人共享刷怪额度](#十三多人共享刷怪额度)
- [十四、刷怪笼符文的 tooltip](#十四刷怪笼符文的-tooltip)
- [十五、宝石图鉴（JEI/EMI）](#十五宝石图鉴jeiemi)
- [十六、冒险：命名生物的两项保护](#十六冒险命名生物的两项保护)

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

原版 `RepairItemRecipe#assemble` 造一个全新的 `ItemStack(item)`，只带走耐久与诅咒附魔，
神化的词条、稀有度、槽位全部丢失。产物因此在 `skip_if_already_affixed` 眼里是「干净的新装备」，
**会重新随机一套词条**——两件满词条镐合并，roll 出来的属性被洗掉。

### 修复

在 `clicked` 的 `HEAD` 处快照合成表，并向配方表问出**这一次点击实际命中的配方**；
在 `TAIL` 处按配方类型分别处理：

| 命中的配方 | 处理 |
|---|---|
| `RepairItemRecipe`（原版修复合并） | **永不重掷**。输入带词条 → 继承最优的一件；输入都没有词条 → 产物保持普通 |
| 其它任何配方（有序/无序/模组/其它特殊配方） | 正常掷词条，仍受 `skip_if_already_affixed` 约束 |

### 为什么必须「按配方」判定，而不是「看物品像不像」

早期实现只比较「产物与某个输入是不是同一种物品」。这个判断会误伤普通合成：2×2、3×3 的
有序/无序配方同样存在「产物与输入同种物品」的情况，而真正的合并配方才是唯一会**吃掉两个输入、
重造一个干净物品**的配方。误判的后果是输入上的词条泄漏到一件真正新做的装备上。

### 为什么合并产物不能重掷（漏洞修复）

把合并产物当成「新做的装备」来掷词条，等于**两件便宜的白板破损装备每合并一次就白送一件带词条装备**。
这既是词条来源的漏洞，也绕开了 `skip_if_already_affixed` 的全部设计意图。因此：

- 「合并产物不重掷」**不可配置**——能被玩家关掉的修 bug 开关不算修复；
- `inherit_on_repair_combine` 只决定「输入的词条要不要继承」，关闭后产物是普通物品，而不是重新随机。

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

## 七、Mekanism: Tools 联动

为热力工具模块（`mekanismtools`）的材质装备添加三项内容：**词缀战利品池**、**回收台配方**、
**入侵者套装**。另外单独处理一个问题：热力本体的**能量装备**默认不参与打造词条。

### 模块与入口

`mekanismtools` 是独立模组，和第六节的 `mekanismadditions` 是两个不同的 mod id。
所以三组数据全部带 `neoforge:mod_loaded: mekanismtools`，卸载后自动消失；
能量装备那组带的是 `neoforge:mod_loaded: mekanism`。
每个开关各有一个本模组的条件类型，改动后**需要重载数据**（F3+T 或重进存档）。

### 为什么"词缀兼容"几乎不需要写代码

神化的 `LootCategory.forItem` 是按 NeoForge 的 `ItemAbility` 判定的。逐一核对热力工具模块
全部 72 件装备的继承链后，结论是**分类早就通了**：

| 装备 | 继承自 | 自动判成 |
|---|---|---|
| 剑 | `SwordItem` | `melee_weapon` |
| 斧 | `AxeItem` | `melee_weapon`（靠攻击力 > 1 命中） |
| 镐 / 铲 | `PickaxeItem` / `ShovelItem` | `breaker` |
| 镐斧（paxel） | `DiggerItem` | `breaker`（同时具备镐/铲/斧/剑能力，按优先级先命中它） |
| 盾 | `ShieldItem` | `shield` |
| 四件护甲 | `ArmorItem` | `helmet` / `chestplate` / `leggings` / `boots` |
| **锄** | `HoeItem` | **`none`**（原版锄头也是这个分类） |

也就是说：玩家自己打造这些装备，本模组的 `[crafting]` 功能**本来就会**给它们挂词条。
真正缺的是**掉落**——它们不在 `apotheosis:affix_loot_entries` 里，
永远不会作为带词条的箱子/BOSS 掉落物出现。

锄头按约定跳过：神化本身没有锄头分类，强行加会引入一个原版装备也没有的分类。

### 一、词缀战利品池

一件物品一个文件（`AffixLootEntry` 的 codec 只放得下一个 `stack`），按材质分目录：

```
data/apotheosis/affix_loot_entries/mekanism_tools/
├── lapis_lazuli/       10 件
├── bronze/             10 件
├── steel/              10 件
├── osmium/             10 件
├── refined_glowstone/  10 件
├── refined_obsidian/   10 件
└── vanilla_paxels/      6 件（木/石/金/铁/钻石/下界合金镐斧）
```

共 66 个条目。**权重定为 2**（每件、每档），这是有意克制：神化现有各档总权重是
haven 280 / frontier 720 / ascent 940 / summit 1005 / pinnacle 545，
每件加 2 是让这批装备可见，而不是把原版和暮色森林的条目稀释掉。

材质分层按实际强度对标神化自带的原版条目：

| 材质 | 档位 | 参照 |
|---|---|---|
| 青金石 | haven + frontier | 早期材质（耐久约 160） |
| 青铜 | haven + frontier + ascent | 对标铁（耐久约 375） |
| 钢 | frontier + ascent + summit | 略强于钻石（耐久 500） |
| 锇 | ascent + summit + pinnacle | 对标钻石（耐久 1024） |
| 强化荧石 | ascent + summit + pinnacle | 伤害高、耐久中等（1105） |
| 强化黑曜石 | summit + pinnacle | 本模组最强（1200，伤害 +8） |

### 二、回收台配方

回收台是**纯配方驱动**的：`apotheosis:salvaging` 用 `input`（Ingredient）+ `outputs`（`min~max` 范围），
产物数量还会按**剩余耐久**线性缩放（`SalvagingMenu.getSalvageCounts`），
所以毁损严重的装备自动产出更少——这是神化自身机制，不是本模组加的。

写法**照抄神化本体**：一条配方把一个类别里的所有物品放进同一个 `input` 数组，
和上游的 `iron_armor.json`（四件铁甲一条）、`iron_tools.json`（五件铁工具一条）完全同构。
共 24 条：6 种材质 × 3 个类别（护甲 / 工具 / 盾）+ 6 条原版材质镐斧。

这个分组是必须的：神化返回**所有**匹配的配方并逐条相加，
所以同一件物品绝不能出现在两条配方里，否则产物直接翻倍。

产出比例沿用神化自带原版配方的口径（护甲 1~3、工具 0~1）：

| 类别 | 组内物品 | 产出 | 数量 |
|---|---|---|---|
| 护甲 | 头盔 / 胸甲 / 护腿 / 靴 | 对应锭 | 1~3 |
| 工具 | 剑 / 镐 / 斧 / 铲 / 镐斧 | 对应锭 | 0~1 |
| 盾 | 盾 | 对应锭 | 1~2（配方要四块锭加一面盾） |

镐斧并入工具组，与上游 `iron_tools` 把锄头也放进同一组的做法一致。

青金石套产出 `minecraft:lapis_lazuli`（它没有对应的热力锭）；
原版材质镐斧各自产出对应的原版材料（木板 / 圆石 / 金锭 / 铁锭 / 钻石 / 下界合金碎片），
六种产出互不相同，因此无法共用一条配方。

### 三、入侵者套装

`GearSet` 没有"档位"字段，档位**由它所在的目录表达**（`gear_sets/` 下的层级目录），
所以每种材质在每个适用档位各一个文件，共 16 个。

关键设计：**复用神化已有的 `<tier>_melee` 标签**，而不是新造一个 `mekanism_melee` 标签。
入侵者用标签声明自己接受哪些套装（如 `"#haven_melee"`），套装在自己的 `tags` 里声明加入哪些池子。
复用现有标签意味着**一个入侵者文件都不用改**就能让它们穿上这些套装；
若新造标签，则要改 60+ 个入侵者文件，diff 大得多且风险更高。

只挂 `_melee`、不挂 `_ranged`：热力工具模块**没有弓**（电动弓在热力本体里，不在本次范围），
挂进远程池会让远程入侵者抽到一把远程用不了的近战武器。

盾不进套装（`offhands` 留空）：神化自己的近战套装也没有副手条目，怪物不会格挡，
拿面盾只是碍眼。盾仍然可以掉落和回收。

套装权重 30（现有套装在 5~80 之间）。效果是**装备种类更丰富**，
而不是每只 BOSS 都穿锇甲——神化仍会在该档位的整个套装池里抽一套。

### 四、能量装备拦截

这是本次发现的一个**既有问题**。热力本体的机甲服、机甲工具、原子分解器、电动弓、喷气背包、
自由跑鞋、潜水装备、防化服、鞘翅、火焰喷射器、水壶，全都是 `ArmorItem` / `DiggerItem` 的子类，
神化因此把它们识别成普通盔甲与工具。玩家打造它们时，本模组的 `[crafting]` 功能会给它们挂上
**按耐久缩放**的词条——可这些装备走能量，根本没有耐久。

修法用的是神化自己的机制：**战利品分类数据表**把这些物品钉成 `apotheosis:none`
（神化原版就用它把 `minecraft:shulker_shell` 钉成 none）。`none` 就是"神化完全不处理这件物品"。

```
data/apotheosis/data_maps/item/loot_category_overrides.json
```

两个容易写错的地方：

1. 挂在 `minecraft:item` 注册表上，不是神化的 `loot_category` 注册表——数据表是"往某注册表的条目上挂值"，
   这里挂的是物品，值是分类。所以目录是 `data_maps/item/`。
2. 文件名必须是 `loot_category_overrides`，且命名空间必须是 `apotheosis`——
   `DataMapLoader` 按文件 id 查数据表类型，换个命名空间会被当作"不存在的数据表类型"跳过。

这不会覆盖神化自己那份内容：NeoForge 读取整个资源栈并合并，且这个数据表没有自定义合并器，
**只有同一物品键**才会被后者覆盖。神化自带的 `minecraft:iron_sword` / `minecraft:shulker_shell`
因此原样保留，校验脚本会断言两者键集合不相交。

### 顺带修掉的一个漏点

拦截排查时发现 `CraftedAffixRoller.applySocketsOnly` 有个漏：它在物品分类为 `none` 时仍会走到
"稀有度没给槽位就用配置范围兜底"的分支，于是 `[crafting] allow_non_equipment = true` 时，
方块和能量装备能被打上宝石槽。现在两条路径都统一为"分类 none 就完全不处理"。

### 校验怎么做的

`node build/validate_mek_tools.js` 拿**真实 jar** 当基准，而不是自己跟自己比：

| 检查 | 基准 |
|---|---|
| 物品 id 是否存在 | 热力工具模块 jar 的 lang 键（72 件） |
| 锭类产出是否存在 | 热力本体 jar 的 lang 键 |
| 条件类型是否注册 | 本模组 `ThermalCompat` 的注册表 |
| 字段名是否正确 | `javap` 反汇编 Placebo / 神化的 codec |
| 矩阵是否完整 | 生成器的材质 × 种类矩阵 |
| 回收输入是否重复 | 重复会导致产物翻倍 |
| 数据表是否碰撞 | 神化 jar 里真实的同名文件 |
| 生成结果是否最新 | 重跑生成器比对 |

数据本身由 `node build/gen_mek_tools.js` 产出（107 个文件：66 条战利品 + 24 条回收 + 16 个套装 + 1 张数据表），矩阵在脚本里是单一事实来源，
避免出现"战利品池有、回收配方漏了"这种不一致。

---

## 八、Ars Nouveau 联动

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

## 九、AllTheModium 联动

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

## 十、原版三大 BOSS

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

## 十一、Bug 修复：BOSS 不锁定创造/旁观玩家

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

## 十二、与神化刷怪笼的关系

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

## 十三、多人共享刷怪额度

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

## 十四、刷怪笼符文的 tooltip

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

---

## 十五、宝石图鉴（JEI/EMI）

神化的帕秋莉手册只说宝石「可能在箱子战利品中找到，也可能在杀怪时少量找到」——**哪种宝石掉在哪个
维度、哪个世界层级才会开始掉、每个纯度的增幅到底是多少**，这些规则只存在于数据文件里，游戏内没有任何
一处能查到。本节给 JEI 与 EMI 各加一套「宝石图鉴」页面，把这三件事直接摊开。

### 页面内容

**每种宝石一页**（不是每个纯度一页——那样类别列表里会堆出几十条几乎相同的条目）：

- **掉落来源**：能掉在哪些维度（核心宝石全维度可掉，主世界系只在主世界，末地系只在末地……；
  暮色森林系的 `twilightforest:twilight_forest` 这类模组维度直接显示 id），以及哪些世界层级
  （权重为 0 的层级不显示——那一级根本不会掉）；
- **附加限制**：受生物群系或游戏阶段（gamestages）限制的宝石会明确标出，页面不会悄悄漏掉一道门槛；
- **增幅表**：每条增幅一行，行内按纯度顺序（有瑕 → 完美，只列该宝石 `min_purity` 以上的纯度）
  把各纯度的**数值**用「/」并列，公共文字只写一次——例如「轻型武器：+1 / +2 / +3.5 / +5 / +7 / +10 攻击伤害」；
  压缩在数字边界处切开，不会把「10%」劈成「1 / 0%」。某条增幅不覆盖的纯度显示「-」，列不会错位；
  不随纯度变化的增幅只显示一个数值、不带斜杠。措辞与数值**就是神化自己 tooltip 里的那份**——
  页面直接调用 `GemBonus#getSocketBonusTooltip`，而不是自己重新实现一遍格式。

### 数据从哪来

**不写死，读已加载的宝石注册表。** 页面在查看器重建索引时（进存档、`/reload`、F3+T）从
`GemRegistry` 现场读取，所以：整合包改过权重或约束、别的模组自带宝石、甚至本模组
`gem_weight_multipliers` 把某颗宝石调到 0——图鉴显示的都是**当下真正会掉的那份规则**。

### JEI 与 EMI 同时安装时：只有 EMI 生效

EMI 自带 JEMI 桥接，会把 JEI 插件的页面**原样导入**自己的索引。如果两边都注册，每颗宝石会出现
两份一模一样的页面。因此裁决规则是：

| 安装情况 | 结果 |
| --- | --- |
| 只有 JEI | JEI 页面 |
| 只有 EMI | EMI 页面 |
| **两个都装** | **只有 EMI 页面，JEI 插件整体静默** |

裁决在 `GemCompendiumGate`：先按类资源探测（不 `Class.forName`，与本模组其它联动探测同一套做法），
`ModList` 作第二意见。JEI 插件类仍然会被 JEI 实例化——这是注解发现机制决定的——但它的每个注册方法
都先问 `useJei()`，两个都装时直接返回，什么都不注册。

### 页面怎么打开

- JEI：宝石物品本身是页面的输入与（隐藏）输出槽，对着任意宝石按 U/R 即可翻到它的页面；
  页面布局复用 JEI 自带信息页（170×125、顶部槽位、下方滚动文本框）；
- EMI：同一套输出链接（宝石页的输出槽就是那颗宝石），长页自动分页，
  翻页按钮与 EMI 自带信息页同款。

`gems.enable_gem_compendium`（默认开）可以整体关掉；该项只在装了 JEI 或 EMI 的客户端才会出现在
配置文件里——服务端与没装查看器的客户端根本不注册这个键。改动后 F3+T 或重进存档生效。

`build/verify_gem_compendium.js` 静态守住这些约定：EMI 优先裁决是否覆盖每个注册入口、探测目标类是否
真实存在于 JEI/EMI jar、编译产物上的发现注解是否还在、页面用到的每个翻译键在两份语言文件里是否都有、
以及用神化 jar 里的 23 颗实装宝石逐一核对维度/层级/最低纯度规则。

---

## 十六、冒险：命名生物的两项保护

`[adventure]` 段下的两个开关解决同一类问题：**玩家亲自用命名牌命名的生物，不该被原版的
全局规则悄悄删掉/换掉**。两者共用同一个判定——实体标签 `apotheosis_expansion.named`，
由 `NameTagItemMixin` 在玩家成功使用命名牌的那一刻打上。

### 为什么是标签，而不是 `hasCustomName`

`hasCustomName` 的含义远比「玩家给它起过名字」宽：神化给每个入侵者和小头目自动生成名字，
刷怪蛋可以携带名称组件，史莱姆分裂时子体会继承父体名称。按名称判断会连带保护玩家从未碰过的
生物。 scoreboard 标签则是原版的、随存档保存、`/data get entity` 可见、`/tag` 命令可增删，
而且在本模组里只有命名牌这一条写入路径。

### 十五·一 命名生物和平保护（`enable_named_peaceful_protection`）

原版 `Mob#checkDespawn` 把和平模式判定排在持久化标记**之前**：

```java
if (this.level().getDifficulty() == Difficulty.PEACEFUL && this.shouldDespawnInPeaceful()) {
    this.discard();              // <- 这条分支不看 isPersistenceRequired
} else if (!this.isPersistenceRequired() && ...) {
    ...距离清理...                // <- 命名牌 setPersistenceRequired 只影响这条
}
```

所以命名牌对「切和平」这件事完全无效——名字保不住宠物。修复走 NeoForge 官方的
`MobDespawnEvent`：`checkDespawn` 开头就有 `EventHooks.checkMobDespawn(this)`，其契约明确
包含「可用于阻止生物在和平模式下消失」，返回 `DENY` 即跳过删除。用 `LOWEST` 优先级是因为
这是个 Result 型事件（所有处理器依次写同一个字段，最后写的赢），本模组只抬高 `DENY`、
从不写 `ALLOW`，排最后不会抢走任何人的判定。

### 十五·二 命名猪灵不变僵尸猪灵（`enable_named_piglin_no_zombify`）

猪灵的转化不看任何「玩家意图」：`AbstractPiglin#isConverting` 的条件只有三条——

```java
return !this.level().dimensionType().piglinSafe()   // 主世界、末地都不 safe
    && !this.isImmuneToZombification()              // 只有 NBT 字段 IsImmuneToZombification ...
    && !this.isNoAi();                              // ... 和下界传送门会给免疫
```

`customServerAiStep` 在这个门控上累加 `timeInOverworld`，超过 300 tick（15 秒）就完成转化。
牵一只猪灵宠物过传送门来主世界，15 秒后它就变成了僵尸猪灵，名字之外的一切（装备、标签、
仇恨逻辑）都被换掉。

`NamedPiglinConversionMixin` 注入 `isConverting` 头部：带命名标签时直接返回 false。
效果是**计时根本不会启动**——比取消 `LivingConversionEvent.Pre` 更干净：事件从第 300 tick
起每 tick 触发一次且拦不住计时器继续走，而 `PiglinAi` 的「转化前撤退」行为也读同一个门控，
短路之后猪灵连后退表现都没有，完全恢复正常行为。去掉标签或关闭开关后，15 秒计时从零重新开始。

范围按需求只圈猪灵系（`AbstractPiglin` = 猪灵 + 猪灵蛮兵）。疣猪兽（Hoglin）有一个自己声明的
同名 `isConverting`、不继承自 AbstractPiglin，本 mixin 结构上就够不到它，不受影响。
`remap = false` 的理由与全项目一致：无 refmap 构建，锚点必须写生产名。

### 热更新

两项都是每 tick / 每次事件实时读配置：改 TOML 保存即生效，不需要 `/reload`，
对场上已有的生物立即起作用。

`build/verify_named_piglin_mixin.js` 静态守住第二项的全部前提：`isConverting()Z` 的生产名、
`customServerAiStep` 确实经此门控且阈值为 300、Hoglin 不在继承链上、注入器形态
（cancellable + `CallbackInfoReturnable<Boolean>`）、以及配置键 / mixin 注册 / 语言键的接线。
