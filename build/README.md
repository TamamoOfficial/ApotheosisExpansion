# 校验脚本说明

`build/` 下的脚本分两类，用途不同。

## 一类：校验脚本（可移植，随仓库分发）

直接读取仓库内的源码与数据文件，不依赖本机绝对路径。在项目根目录运行：

```bash
node build/validate_invaders.js thermal
node build/verify_readme.js
```

| 脚本 | 检查内容 |
|---|---|
| `validate_invaders.js` | 入侵者数据文件的结构、约束、条件链（5 个 profile） |
| `verify_entity_switches.js` | 实体开关 ↔ 数据文件 ↔ 语言键 三方一致 |
| `verify_mixin.js` | BossSpawnerTileMixin 的目标方法与描述符 |
| `verify_creative_filter.js` | 索敌修复的两个 @Redirect 目标 |
| `verify_vanilla_bosses.js` | 原版三大 BOSS 的设定与风险点 |
| `verify_piglich_gear.js` | 猪巫妖装备钩子的顺序与概率 |
| `test_filter_predicate.js` | 刷怪笼过滤器的白名单行为 |
| `parity_check.js` | 数据字段与上游 codec 的对应 |
| `check_baby_derivation.js` | 宝宝权重是否为成年体的 1/3 |
| `verify_readme.js` | 文档链接、锚点、中英配对、配置键覆盖 |

**需要外部 jar 的脚本**另有依赖，见下文「环境变量」。

## 二类：一次性生成脚本（保留作参考，不保证可移植）

`gen_*.js` 当初用于从上游解包的参考数据批量生成 JSON。它们依赖 `APOTH_REF_DIR`
指向的 dance 目录，正常情况下不需要再运行——数据文件已在仓库里。保留是为了让参数
（权重、稀有度曲线）可追溯，而不是让人重跑。

---

## 环境变量

需要外部数据的脚本会读这些变量；未设置时报错退出，不会静默跳过：

| 变量 | 指向 | 被谁使用 |
|---|---|---|
| `APOTH_JAR` | Apotheosis 的 jar | 多数 `verify_*` |
| `PLACEBO_JAR` | Placebo 的 jar | `verify_mixin.js` |
| `APOTH_REF_DIR` | 解包后的 `data/apotheosis/apothic_invaders` 参考目录 | `gen_*.js` |
| `MC_SOURCES_JAR` | NeoForge 的 `neoforge-*-sources.jar` | 少数检查原版行为的脚本 |
| `JAVAP` | `javap` 可执行文件路径 | 依赖字节码的脚本 |

**依赖 jar 从哪里来**：脚本按以下顺序查找，通常不需要设置任何变量：

1. `APOTH_JAR` / `PLACEBO_JAR` 环境变量
2. 仓库根的 `libs/` 目录（离线工作时可手动放置）
3. **Gradle 依赖缓存** —— 跑过一次 `gradlew build` 就自动有了

`MC_SOURCES_JAR` 来自 NeoForge 的 moddev 产物，通常位于
`~/.gradle/caches/` 或项目的 `build/moddev/artifacts/`。

示例（PowerShell）：

```powershell
# 通常只需这一行：jar 会从 Gradle 缓存自动找到
$env:JAVAP = "javap"                # 若 JDK 在 PATH 上
node build/verify_mixin.js

# 没有跑过 gradlew build、也没有 libs/ 时，手动指定：
$env:APOTH_JAR   = "C:/path/to/Apotheosis-1.21.1-8.9.0.jar"
```

示例（bash）：

```bash
export JAVAP=javap
node build/verify_mixin.js          # jar 从 Gradle 缓存自动找到
```

---

## 为什么不做实机测试

本模组依赖大量游戏内行为（刷怪流程、AI、数据包重载时机）。自动化实机测试需要启动
完整客户端，成本远高于收益。因此验证以**静态检查**为主：

- 数据文件：结构、字段取值范围、条件链
- mixin：用 `javap` 确认目标方法与描述符逐字符匹配
- 逻辑：把判定规则复制成 JS 镜像做行为表测试

这套办法能抓出「目标方法不存在」「条件类型没注册」「过滤规则漏了某个目录」这类
**会静默失效**的问题，但抓不出「数值不平衡」这种需要手感的问题。
