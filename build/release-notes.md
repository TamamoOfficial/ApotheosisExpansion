# Apotheosis Expansion 1.1.1

## 新增功能

**宝石图鉴（JEI/EMI）**

在配方查看器里为每颗神化宝石生成一页图鉴：掉在哪些维度与世界层级、有无群系/进度门、
每条增幅在各纯度的数值（按游戏内顺序以「/」并列，文字直接复用神化自己的 tooltip 方法）。
页面在查看器重建索引时从**已加载的宝石注册表**现场生成，改数值的数据包、别的模组自带的
宝石都会正确显示。JEI 与 EMI 同装时只注册 EMI 页（EMI 的 JEMI 桥会导入 JEI 插件，两边都注册
会重复）；两者都没装（含专用服务器）时配置项 `gems.enable_gem_compendium` 根本不出现在文件里。

**额外刷怪游走（spawn lanes）**

`[player] enable_spawn_lanes`（默认关）：区域难度 ≥ 阈值（默认 5.5，即 F3 本地难度那条数）时
每区块对敌对分类多跑一轮刷怪尝试。复用同一个 SpawnState，**存量上限仍然生效**——提高的是
产量而不是上限。实测在刷怪塔上约 +0.21 ms/tick 换 1.97× 产量。
`spawn_lanes_real_players_only` 决定难度判定是否计入假人。

**漏洞修复：合并产物不再凭空生成神化词条**

两件白板破损装备合并修复后，产物会被重新掷一次词条——零成本材料变词条制造机。
现在修复与合并只**继承**输入装备的稀有度/名称/槽位数据，永不重掷；
`[crafting] inherit_on_repair_combine` 可关掉继承本身（不关掉「不重掷」）。
配方判定改为按配方表匹配（`RepairCombineInheritance`），不再用「产物与输入同种物品」的启发式。

**命名猪灵不变僵尸猪灵**（`[adventure] enable_named_piglin_no_zombify`，默认开，保存即生效）

原版的转化条件只看维度：`AbstractPiglin#isConverting` = 非 piglinSafe 维度（主世界、末地）且无免疫
标记，15 秒（300 tick）后猪灵 / 猪灵蛮兵必变成僵尸猪灵。免疫只有两个来源——NBT 字段
`IsImmuneToZombification` 与穿过下界传送门——命名牌完全不起作用。

现在带 `apotheosis_expansion.named` 标签（玩家使用命名牌时打上，与和平保护共用同一判定）的
猪灵 / 猪灵蛮兵不再转化：直接在 `isConverting` 头部短路返回 false，计时根本不会启动，
也就没有转化前的后退表现。去掉标签或关闭开关后，15 秒计时重新从零开始。
疣猪兽有自己独立的 `isConverting`，不在本功能范围内。

实现为 `NamedPiglinConversionMixin`（`@Inject` + `HEAD` + `cancellable`，`remap = false`），
静态校验脚本 `node build/verify_named_piglin_mixin.js`。

---

# Apotheosis Expansion 1.1.0

## 新增功能

**刷怪笼符文的数值直接写进 tooltip**

当前版本的神化把刷怪笼强化换成了符文，但物品只写一句「略微提升刷怪笼的性能」，
具体数值要打开 JEI/EMI 的用途页、再逐条悬停才看得到。现在 tooltip 直接列出改动：

```
拓荒者刷怪笼升级符文
略微提升刷怪笼的性能

最小生成延迟：150
最大生成延迟：600
生成数量：7
最大实体数量：12
激活范围：24
```

数值**直接读取当前数据包的 `apothic_spawners:spawner_modifier` 配方**，
所以改过数值的数据包、或别的模组自带的符文，都会自动正确显示，无需改本模组代码。

| 配方写法 | 显示 |
|---|---|
| `set` + 数字 | `最小生成延迟：150` |
| `set` + 布尔 | `启用：无视光照` |
| `add` 模式 | `+2 生成范围（上限 32）` |

- 上限/下限也会显示，因为它是真实行为（生成范围最多到 32）
- 副手的反向配方（符文 + 石英关掉效果）不参与显示，否则 tooltip 会自相矛盾

需要 Apothic Spawners（可选依赖）；未安装时该配置项不出现，功能整体不存在。
配置项 `[spawner] enable_spawner_rune_tooltips`，默认开启，可热更新，仅客户端生效。

## 构建

本机 Gradle 因 `native-platform.dll` 无法启动，编译与打包改为脚本：

```bash
node build/compile.js     # 编译到 build/classes
node build/package.js     # 打包到 build/libs/
```

## 校验

新增 2 个校验脚本，全套 15 个静态校验通过：

```bash
node build/verify_spawner_rune_tooltips.js   # API 是否还在、配方结构、依赖隔离、缓存失效
node build/test_spawner_rune_tooltips.js     # 用真实配方驱动的格式化行为表
```

---

# Apotheosis Expansion 1.1.0 (English)

## New feature

**A spawner rune now says what it changes**

Apotheosis replaced "feed items to the spawner" with runes, but the item only says
"Slightly improves a Mob Spawner" - the numbers live behind the JEI/EMI uses page. The tooltip
now lists them directly:

```
Frontiersman's Spawner Upgrade Rune
Slightly improves a Mob Spawner

Min Spawn Delay: 150
Max Spawn Delay: 600
Spawn Count: 7
Max Nearby Entities: 12
Required Player Range: 24
```

The values are **read from the loaded data pack's `apothic_spawners:spawner_modifier` recipes**,
so a pack that retunes the numbers, or another mod that ships its own runes, displays correctly
with no code change.

| Recipe form | Tooltip |
|---|---|
| `set` + numeric | `Min Spawn Delay: 150` |
| `set` + boolean | `Enables: Ignore Light` |
| `add` mode | `+2 Spawn Range (max 32)` |

- bounds are shown because they are real behaviour (spawn range stops at 32)
- the off-hand inverse recipes (rune + quartz turns the effect off) are skipped, so a rune never
  reads as both on and off

Requires Apothic Spawners (optional). Without it the option does not appear and the feature does
not exist. Config key `[spawner] enable_spawner_rune_tooltips`, on by default, hot-reloadable,
client-side only.

## Building

Gradle cannot start on the development machine (`native-platform.dll`), so compilation and
packaging are scripted:

```bash
node build/compile.js     # compile into build/classes
node build/package.js     # package into build/libs/
```

## Validation

Two new validators; all 15 static checks pass:

```bash
node build/verify_spawner_rune_tooltips.js   # API surface, recipe structure, dependency isolation, cache invalidation
node build/test_spawner_rune_tooltips.js     # formatter behaviour table driven by the real recipes
```

---

## 修复（v1.1.0 重新发布）

首版 v1.1.0 的符文 tooltip 不生效：`Config` 中 `define()` 的返回值未赋给对应字段，
导致开关恒为 `false`。已修复，并在实机确认：

| 符文 | 匹配到的数值条数 |
|---|---|
| 拓荒者 / 旅人 / 登山者 / 雷霆（升级符文） | 5 |
| 焚祭 / 回响 / 光辉 / 抗性 / 虚弱 等单效果符文 | 1 |
| 刷怪笼符文 / 灌注刷怪笼符文（基础符文，本身不改数值） | 0 |

同时修正副手判断（改为 `isEmpty()`），避免 codec 对缺失 `offhand` 字段新建实例、
引用比较误判而跳过全部配方。

## Fix (v1.1.0 republished)

The first v1.1.0 build did not work: the `define()` return value was never assigned to the
backing field in `Config`, so the switch read `false` forever. Fixed and verified in-game:

| Rune | Stat changes matched |
|---|---|
| Frontier / Ascent / Summit / Pinnacle (upgrade runes) | 5 |
| Burning / Echoing / Ignore Light / ... (single-effect runes) | 1 |
| Spawner Rune / Infused Spawner Rune (base runes, no stats) | 0 |

The off-hand check was also corrected to use `isEmpty()`, since the codec creates a fresh
instance for a missing `offhand` field and an identity comparison could skip every recipe.

---

# 漏洞修复：合并产物不再凭空生成神化词条

## 现象

把两件**都没有神化词条**的破损装备放进 2×2 / 3×3 合成表合并修复，产物**仍然会获得随机神化词条**。
由于白板破损装备几乎零成本，这等于让合成台变成一台词条制造机。

## 根因

原版 `RepairItemRecipe#assemble` 造一个全新的 `ItemStack(item)`，只带走耐久与诅咒附魔。神化的
词条、稀有度、槽位全部丢失——产物在 `skip_if_already_affixed` 眼里是「干净的新装备」，于是走正常
的掷词条流程。也就是说，**合并被当成了合成**。

更早的实现还用一个启发式判断合并：「产物与某个输入是同一种物品」。但有序/无序配方同样会出现
「产物与输入同种物品」的情形，词条会从被消耗的材料泄漏到真正新做的装备上。

## 修复

`clicked` 的 `HEAD` 处同时快照合成表并**问配方表这一次实际命中了哪个配方**，`TAIL` 处按配方分流：

| 命中的配方 | 处理 |
|---|---|
| `RepairItemRecipe`（原版修复合并） | **永不重掷**：输入带词条 → 继承最优的一件；输入都是白板 → 产物保持白板 |
| 其它任何配方（有序 / 无序 / 模组 / 其它特殊配方） | 正常掷词条，仍受 `skip_if_already_affixed` 约束 |

「合并产物不重掷」**不可配置**——能被玩家关掉的修 bug 开关不算修复。`inherit_on_repair_combine`
现在只决定「输入的词条要不要继承」，关闭后产物是白板，而不是重新随机。

## 校验

```bash
node build/verify_repair_combine.js
```

检查原版 `RepairItemRecipe` 是否仍然丢弃全部组件（bug 是否还在）、合并判定是否基于**真实命中的
配方类**而非物品比较、合并产物是否存在任何可绕过配置的重掷路径，并跑一张行为表覆盖
「两白板合并 / 带词条合并 / 普通合成 / 2×2、3×3 方块合成」等 9 种情形。

---

# Exploit fix: a merge no longer mints Apotheosis affixes

## Symptom

Combining two **completely unaffixed** damaged items in a 2×2 / 3×3 crafting grid still produced an item
with random Apotheosis affixes. Plain damaged gear costs nothing, so the crafting table effectively became
an affix generator.

## Root cause

Vanilla `RepairItemRecipe#assemble` builds a brand-new `ItemStack(item)` and only carries over durability
and curse enchantments. Every Apotheosis component — affixes, rarity, sockets — is dropped, so
`skip_if_already_affixed` sees a clean new item and the output goes through the normal affix roll. In other
words, **a merge was being treated as a craft**.

An earlier implementation also guessed at merges with "is the output the same item as an input?". Shaped and
shapeless recipes match that too, so affixes could leak from a consumed ingredient onto a genuinely new craft.

## Fix

At `HEAD` of `clicked` the grid is snapshotted **and the recipe that actually matched is resolved**. At
`TAIL` the treatment branches on it:

| Matched recipe | Treatment |
|---|---|
| `RepairItemRecipe` (vanilla repair combine) | **Never re-rolled**: affixed inputs → inherit the best one; plain inputs → output stays plain |
| Any other recipe (shaped / shapeless / modded / other custom) | Rolls normally, still bounded by `skip_if_already_affixed` |

"A merge never re-rolls" is **not configurable** — a bug fix a player can switch off is not a fix.
`inherit_on_repair_combine` now only decides whether the inputs' data is carried over; with it off the output
is plain, not re-rolled.

## Validation

```bash
node build/verify_repair_combine.js
```

Checks that vanilla `RepairItemRecipe` still drops every component (the bug is still real), that merge
detection keys on the **matched recipe class** rather than an item comparison, that no config-switchable path
can re-roll a merge output, and runs a 9-case behaviour table (both-plain merge / affixed merge / ordinary
craft / 2×2 and 3×3 block crafts).
