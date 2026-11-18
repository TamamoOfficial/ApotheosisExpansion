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
