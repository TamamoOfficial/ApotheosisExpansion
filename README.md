# Apotheosis Expansion

**中文** | [English](README_en.md)

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62b47a)](https://minecraft.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.259-e89b4a)](https://neoforged.net)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

[Apotheosis](https://github.com/Shadows-of-Fire/Apotheosis)（神化）的扩展模组。补上神化本体没有做、
或做得不完整的地方，并为多个模组的实体添加神化入侵者（BOSS）联动。

所有功能都可以在游戏内配置界面独立开关。

> 详细原理与设计取舍见 **[功能详解](docs/FEATURES.md)**。

---

## 模块一览

### 制作与刷怪

| 模块 | 功能 |
|---|---|
| **制作装备带属性** | 玩家合成的装备自动获得神化词条与宝石槽 |
| **刷怪蛋解锁** | 放开刷怪笼对坚守者、远古守卫者刷怪蛋的限制 |
| **合并修复修复** | 修复合并时词条被重掷的 bug；合并产物不再凭空产出词条（漏洞修复） |
| **宝石掉落调整** | 按倍率缩放宝石掉率与权重 |
| **宝石图鉴（JEI/EMI）** | 每种宝石每个纯度一页：掉在哪些维度/层级、增幅具体数值；两者同装时只走 EMI |
| **多人共享刷怪额度** | 同处一个刷怪平台的玩家共同抬高怪物上限，可选是否跨层级计数 |
| **刷怪笼符文数值** | 在符文 tooltip 里直接列出它改动的数值，数值取自当前数据包 |

### 玩家行为修复

| 模块 | 功能 |
|---|---|
| **复活回血修复** | 复活时按真实最大生命值回满，而非固定 20 点 |
| **命名生物平和保护** | 玩家用命名牌命名的生物，切和平模式不消失 |
| **命名猪灵免转化** | 玩家命名的猪灵/猪灵蛮兵在主世界不会变成僵尸猪灵 |
| **假人层级继承** | 假人继承召唤者的世界层级，使共享刷怪额度对假人也生效 |

### 模组联动

为其他模组添加内容：多数是神化入侵者实体，Mekanism: Tools 则是装备内容（战利品池、回收配方、套装）。
**不使用任何模组作为硬前置**——未安装时整组自动隐藏。

| 模组 | 内容 | 说明 |
|---|---|---|
| **Thermal**（热力系列） | 岩石人 / 暴雪人 / 狂风人 | 7 条数据，覆盖多种群系 |
| **Mekanism Additions**（通用机械附属） | 五种宝宝 | 权重为成年体的 1/3（凋灵骷髅 1/4） |
| **Ars Nouveau** | 荒野猎手 / 伏击者 / 守卫者 | 荒野巢穴结构刷怪 |
| **AllTheModium** | 猪巫妖 | 远古金字塔结构，并补回随机 ATM 装备 |
| **Mekanism: Tools**（通用机械工具） | 材质装备（非实体） | 词缀战利品池 + 回收台配方 + 入侵者套装 |

### 原版 BOSS

| 模块 | 说明 |
|---|---|
| **坚守者** | 尖啸召唤时 10% 概率成为 BOSS |
| **凋灵** | 玩家搭建时 10% 概率，按维度分级 |
| **末影龙** | 仅本存档首只，固定最高稀有度 |
| **索敌修复** | BOSS 不再主动攻击创造/旁观玩家 |

### HUD 兼容

| 模块 | 说明 |
|---|---|
| **上限同步到 HUD** | 可让 MiniHUD / Servux 显示放宽后的真实上限，而不是原版 70 |

---

## 安装

**必需依赖**

| 模组 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.235+ |
| Apotheosis | 8.9.0+ |
| Placebo | 9.9.2+ |
| Apothic Attributes | 2.11.0+ |

**可选依赖**（不装则对应联动自动隐藏）

- Thermal Series / Mekanism Additions / Ars Nouveau / AllTheModium
- **Mekanism: Tools**（通用机械工具）— 词缀战利品池、回收台配方、入侵者套装
- **Mekanism**（通用机械，主包）— 能量装备拦截（机甲服、机甲工具等不参与打造词条）
- Ancient Reforging — 装了之后首只末影龙使用「远古」稀有度

把 jar 放进 `mods/` 即可，客户端与服务端都需要安装。

---

## 配置

配置文件 `config/apotheosis_expansion-common.toml`，共 5 个段、47 个配置项。
游戏内界面按**模组分组**，未安装的模组不会出现空开关。

完整键表见 **[配置与构建](docs/CONFIG.md)**。

**热重载**：绝大多数选项**保存后立即生效**，无需重启也无需指令。
例外是各类入侵者开关——它们作用于数据包条件，需要重载数据（`F3+T` 或 `/reload`）。
按项归类见 [配置与构建](docs/CONFIG.md#热重载)。

---

## 从源码构建

```bash
./gradlew build
```

产物：`build/libs/ApotheosisExpansion-1.21.1-neoforge-1.2.0.jar`

依赖由 Gradle 自动从 [Shadow's Maven](https://maven.shadowsoffire.dev/releases/dev/shadowsoffire/) 下载，
**克隆后无需手动准备任何 jar**。开发机的 Gradle 因 `native-platform.dll` 无法启动，实际用 `javac` 直接编译，
细节见 [配置与构建](docs/CONFIG.md#本机-gradle-无法启动)。

---

## 已知限制

1. 末影龙穿装备**在模型上不可见**（无护甲层），但属性与词条生效
2. 末影龙死亡**不掉落装备**（独立的死亡处理）
3. `pinnacle` 层级数值未经实机验证
4. 仅面向 1.21.1 / NeoForge
5. Ancient Reforging 路径未经实机验证

---

## 许可

[MIT](LICENSE) © Tamamo

本模组是 [Apotheosis](https://github.com/Shadows-of-Fire/Apotheosis) 的扩展，不包含其任何代码或资源。
