# 校验脚本说明

`build/` 下的脚本分三类，用途不同。

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
| `verify_mob_cap.js` | 多人刷怪上限三个注入点的目标、handler 元数、倍率算术 |
| `verify_hud_compat.js` | MiniHUD / Servux 第三方注入目标、冷开关、未安装时跳过 |
| `test_fake_tier_sync.js` | 假人层级继承的四个场景与解析优先级 |
| `test_hot_reload.js` | 配置热更新：无重启标记、无静态缓存、缓存键含世代计数 |
| `verify_creative_filter.js` | 索敌修复的两个 @Redirect 目标 |
| `verify_vanilla_bosses.js` | 原版三大 BOSS 的设定与风险点 |
| `verify_piglich_gear.js` | 猪巫妖装备钩子的顺序与概率 |
| `test_filter_predicate.js` | 刷怪笼过滤器的白名单行为 |
| `verify_spawner_rune_tooltips.js` | 符文 tooltip：Apothic Spawners API 是否还在、配方是否仍五项全设、可选依赖隔离、缓存失效接线 |
| `test_spawner_rune_tooltips.js` | 符文 tooltip 的格式化行为表（用真实配方驱动：set/布尔/add/上下限/反向配方跳过） |
| `parity_check.js` | 数据字段与上游 codec 的对应 |
| `check_baby_derivation.js` | 宝宝权重是否为成年体的 1/3 |
| `verify_readme.js` | 文档链接、锚点、中英配对、配置键覆盖 |

**需要外部 jar 的脚本**另有依赖，见下文「环境变量」。

## 二类：一次性生成脚本（保留作参考，不保证可移植）

`gen_*.js` 当初用于从上游解包的参考数据批量生成 JSON。它们依赖 `APOTH_REF_DIR`
指向的 dance 目录，正常情况下不需要再运行——数据文件已在仓库里。保留是为了让参数
（权重、稀有度曲线）可追溯，而不是让人重跑。

---

## 三类：本机构建脚本（Gradle 起不来时用）

本机 Gradle 因 `native-platform.dll` 无法启动，所以编译与打包也做成了脚本：

```bash
node build/compile.js     # 编译 src/main/java -> build/classes
node build/package.js     # 打包 build/classes + 资源 -> build/libs/
```

`compile.js` 会自动找 JDK 21 的 `javac`、从 `build/cp.txt` 与 `libs/*.jar` 拼 classpath，
并强制 `-sourcepath` 指向空目录（否则 javac 会把 jar 里的类反编译重编到输出目录）。

---

## 环境变量

需要外部数据的脚本会读这些变量；未设置时报错退出，不会静默跳过：

| 变量 | 指向 | 被谁使用 |
|---|---|---|
| `APOTH_JAR` | Apotheosis 的 jar | 多数 `verify_*` |
| `PLACEBO_JAR` | Placebo 的 jar | `verify_mixin.js` |
| `APOTH_REF_DIR` | 解包后的 `data/apotheosis/apothic_invaders` 参考目录 | `gen_*.js` |
| `MC_SOURCES_JAR` | NeoForge 的 `neoforge-*-sources.jar` | 少数检查原版行为的脚本 |
| `MC_JAR` | NeoForge 的 `neoforge-*-merged.jar` | `verify_mob_cap.js`（要反汇编原版字节码） |
| `JAVAP` | `javap` 可执行文件路径 | 依赖字节码的脚本 |
| `APOTHIC_SPAWNERS_JAR` | Apothic Spawners 的 jar | `verify_spawner_rune_tooltips.js`（默认取 `libs/`） |

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
