package com.tamamo.apotheosisexpansion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

/**
 * Configuration for Apotheosis Expansion.
 * <p>
 * <b>Bilingual comments.</b> Every comment is written as an English line immediately followed by its Chinese
 * translation, so the generated {@code .toml} reads the same in either language without needing two files. This is safe
 * because night-config writes config files as UTF-8 unconditionally
 * ({@code ConfigWriter.write(config, file, mode)} delegates to the {@code UTF_8} overload), and it reads them back with
 * {@code Files.newBufferedReader(path)}, which is also UTF-8. Comment equality is re-checked with newlines normalised
 * ({@code stringsMatchNormalizingNewLines}), so the bilingual text does not make NeoForge rewrite or back up the file
 * on every launch.
 * <p>
 * Note that {@code defineInRange} appends its own auto-generated {@code Default:} / {@code Range:} lines; those come
 * from NeoForge and stay English.
 * <p>
 * All values are declared without {@code worldRestart()} / {@code gameRestart()}, which means NeoForge clears their
 * cached values on every {@code ModConfigEvent.Reloading}. That is what makes the in-game toggles take effect
 * immediately, both when edited through the mod list config screen and when the TOML file is edited by hand
 * (FML's {@code ConfigTracker} installs a night-config {@code FileWatcher} on the file).
 * <p>
 * A {@code COMMON} config is used so that the values are available on both the logical client and a dedicated server.
 * The generation itself only ever happens server-side, so the client copy is only used to render the config UI.
 * <p>
 * <b>Layout.</b> Everything lives under three top-level sections, {@code [crafting]}, {@code [spawner]} and
 * {@code [gems]}. NeoForge's config screen renders a non-empty sub-section as its own "Edit" button, so this produces a
 * two-level menu.
 * <p>
 * <b>Translation keys.</b> Every value and section sets its key explicitly via {@code translation()}. NeoForge's
 * fallback is {@code modId + ".configuration." + <local key>}, which drops the section path entirely — relying on it
 * makes nested keys impossible to translate.
 */
public final class Config {

    public static final ModConfigSpec SPEC;

    // ==== [crafting] =======================================================

    // ---- Feature toggles -------------------------------------------------

    public static final BooleanValue ENABLE_AFFIXES;
    public static final BooleanValue ENABLE_SOCKETS;

    // ---- Independent chances --------------------------------------------

    public static final DoubleValue AFFIX_CHANCE;
    public static final DoubleValue SOCKET_CHANCE;

    // ---- Behaviour ------------------------------------------------------

    public static final BooleanValue SKIP_IF_ALREADY_AFFIXED;
    public static final BooleanValue ALLOW_NON_EQUIPMENT;
    public static final BooleanValue INHERIT_ON_REPAIR_COMBINE;

    // ---- Rarity bounds --------------------------------------------------

    public static final ConfigValue<String> MIN_RARITY;
    public static final ConfigValue<String> MAX_RARITY;
    public static final BooleanValue RESPECT_WORLD_TIER;

    // ---- Sockets --------------------------------------------------------

    public static final IntValue SOCKET_MIN;
    public static final IntValue SOCKET_MAX;

    // ==== [spawner] ========================================================

    public static final BooleanValue ENABLE_WARDEN_UNLOCK;
    public static final BooleanValue ENABLE_ELDER_GUARDIAN_UNLOCK;

    /**
     * Not {@code final}: the {@code [spawner/runes]} sub-section is only registered when Apothic Spawners is installed,
     * so on a pack without it this stays null and {@link #enableSpawnerRuneTooltips()} reports false.
     */
    public static BooleanValue ENABLE_SPAWNER_RUNE_TOOLTIPS;

    // ==== [player] =========================================================

    public static final BooleanValue ENABLE_RESPAWN_HEALTH_FIX;

    // ---- multiplayer monster cap ------------------------------------------

    public static final BooleanValue ENABLE_MOB_CAP_SCALING;
    public static final BooleanValue MOB_CAP_COUNT_ALL_TIERS;
    public static final DoubleValue MOB_CAP_BONUS_PER_PLAYER;
    public static final IntValue MOB_CAP_MAX_PLAYERS;
    public static final BooleanValue SYNC_MOB_CAP_TO_HUD;
    public static final BooleanValue FAKE_PLAYERS_INHERIT_WORLD_TIER;
    public static final BooleanValue DEBUG_MOB_CAP;

    // ---- extra spawn lanes ------------------------------------------------

    public static final BooleanValue ENABLE_SPAWN_LANES;
    public static final DoubleValue SPAWN_LANES_MIN_REGIONAL_DIFFICULTY;
    public static final BooleanValue SPAWN_LANES_REAL_PLAYERS_ONLY;

    // ==== [adventure] ======================================================

    public static final BooleanValue ENABLE_NAMED_PEACEFUL_PROTECTION;
    public static final BooleanValue ENABLE_NAMED_PIGLIN_NO_ZOMBIFY;

    // ==== [gems] ===========================================================

    public static final BooleanValue ENABLE_GEM_TWEAKS;
    public static final DoubleValue GEM_DROP_CHANCE_MULTIPLIER;
    public static final ConfigValue<List<? extends String>> GEM_WEIGHT_MULTIPLIERS;

    /**
     * Not {@code final}: the gem compendium is a recipe-viewer feature, so its key is only registered when a viewer
     * is installed. On a dedicated server, and on a client with neither JEI nor EMI, the key never appears in the
     * file and no empty toggle shows in the config screen.
     */
    public static BooleanValue ENABLE_GEM_COMPENDIUM;

    // ==== [compat] =========================================================

    public static BooleanValue ENABLE_THERMAL_INVADERS; /* not final: a mod-gated sub-section may never assign it */
    public static BooleanValue ENABLE_MEKANISM_INVADERS; /* not final: a mod-gated sub-section may never assign it */
    public static BooleanValue ENABLE_ARS_NOUVEAU_INVADERS; /* not final: a mod-gated sub-section may never assign it */
    public static BooleanValue ENABLE_ALLTHEMODIUM_INVADERS; /* not final: mod-gated sub-section */
    public static BooleanValue ENABLE_VANILLA_BOSSES; /* not final: a mod-gated sub-section may never assign it */

    // ---- Mekanism: Tools integration (separate mod id from mekanismadditions above) ----

    public static BooleanValue ENABLE_MEKANISM_TOOLS_AFFIXES; /* not final: mod-gated sub-section */
    public static BooleanValue ENABLE_MEKANISM_TOOLS_SALVAGING; /* not final: mod-gated sub-section */
    public static BooleanValue ENABLE_MEKANISM_TOOLS_GEAR_SETS; /* not final: mod-gated sub-section */

    // ---- Mekanism (main mod) energy gear: excluded from this mod's crafted-affix roll ----

    public static BooleanValue BLOCK_MEKANISM_ENERGY_GEAR_AFFIXES; /* not final: mod-gated sub-section */

    // ---- per-entity switches (the group toggles above are master switches) ----

    public static BooleanValue ENABLE_BASALZ_INVADER; /* not final: a mod-gated sub-section may never assign it */
    public static BooleanValue ENABLE_BLIZZ_INVADER; /* not final: a mod-gated sub-section may never assign it */
    public static BooleanValue ENABLE_BLITZ_INVADER; /* not final: a mod-gated sub-section may never assign it */
    public static BooleanValue ENABLE_BABY_ENDERMAN_INVADER; /* not final: a mod-gated sub-section may never assign it */
    public static BooleanValue ENABLE_BABY_SKELETON_INVADER; /* not final: a mod-gated sub-section may never assign it */
    public static BooleanValue ENABLE_BABY_STRAY_INVADER; /* not final: a mod-gated sub-section may never assign it */
    public static BooleanValue ENABLE_BABY_BOGGED_INVADER; /* not final: a mod-gated sub-section may never assign it */
    public static BooleanValue ENABLE_BABY_WITHER_SKELETON_INVADER; /* not final: a mod-gated sub-section may never assign it */
    public static BooleanValue ENABLE_WILDEN_HUNTER_INVADER; /* not final: a mod-gated sub-section may never assign it */
    public static BooleanValue ENABLE_WILDEN_STALKER_INVADER; /* not final: a mod-gated sub-section may never assign it */
    public static BooleanValue ENABLE_WILDEN_GUARDIAN_INVADER; /* not final: a mod-gated sub-section may never assign it */
    public static BooleanValue ENABLE_PIGLICH_BOSS; /* not final: mod-gated sub-section */
    public static BooleanValue ENABLE_WARDEN_BOSS; /* not final: a mod-gated sub-section may never assign it */
    public static BooleanValue ENABLE_WITHER_BOSS; /* not final: a mod-gated sub-section may never assign it */
    public static BooleanValue ENABLE_ENDER_DRAGON_BOSS; /* not final: a mod-gated sub-section may never assign it */

    /** entity id -> BooleanValue, filled in the static block below. */
    private static final Map<String, BooleanValue> ENTITY_INVADER_TOGGLES = new LinkedHashMap<>();

    private static final String KEY = "apotheosis_expansion.configuration.";

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        // ---------------------------------------------------------------- crafting
        b.comment(
            "Apotheosis affixes and gem sockets on items crafted by players.",
            "玩家制作装备的神化词条与宝石槽位。")
            .translation(KEY + "crafting")
            .push("crafting");

        // ---- affixes
        b.comment(
            "Whether items crafted by a player receive random Apotheosis affixes.",
            "玩家制作的装备是否随机附带神化词条。",
            "Changes take effect immediately; no game restart is required.",
            "修改后立即生效，无需重启游戏。")
            .translation(KEY + "crafting.affixes")
            .push("affixes");
        ENABLE_AFFIXES = b
            .comment("Master switch for affix generation on crafted items.",
                "玩家制作装备附带词条的总开关。")
            .translation(KEY + "crafting.affixes.enable_affixes")
            .define("enable_affixes", true);
        AFFIX_CHANCE = b
            .comment("Independent chance (0.0 - 1.0) that a crafted item receives affixes.",
                "制作出的物品获得词条的独立概率（0.0 - 1.0）。",
                "Rolled separately from the socket chance.",
                "与槽位概率分别投掷，互不影响。")
            .translation(KEY + "crafting.affixes.affix_chance")
            .defineInRange("affix_chance", 1.0D, 0.0D, 1.0D);
        b.pop();

        // ---- sockets
        b.comment(
            "Whether items crafted by a player receive Apotheosis gem sockets.",
            "玩家制作的装备是否附带神化宝石槽位。")
            .translation(KEY + "crafting.sockets")
            .push("sockets");
        ENABLE_SOCKETS = b
            .comment("Master switch for socket generation on crafted items.",
                "玩家制作装备附带宝石槽位的总开关。")
            .translation(KEY + "crafting.sockets.enable_sockets")
            .define("enable_sockets", true);
        SOCKET_CHANCE = b
            .comment("Independent chance (0.0 - 1.0) that a crafted item receives gem sockets.",
                "制作出的物品获得宝石槽位的独立概率（0.0 - 1.0）。",
                "Rolled separately from the affix chance.",
                "与词条概率分别投掷，互不影响。")
            .translation(KEY + "crafting.sockets.socket_chance")
            .defineInRange("socket_chance", 1.0D, 0.0D, 1.0D);

        b.comment(
            "Number of sockets rolled when the item's rarity has no socket rule of its own.",
            "当该稀有度本身没有槽位规则时，使用的槽位数量。",
            "Apotheosis rarities normally define their own socket counts, so this is only a fallback.",
            "神化各稀有度通常自带槽位规则，因此这里只是备用值。")
            .translation(KEY + "crafting.sockets.socket_range")
            .push("socket_range");
        SOCKET_MIN = b
            .comment("Minimum sockets.", "最少槽位数。")
            .translation(KEY + "crafting.sockets.socket_range.min")
            .defineInRange("min", 0, 0, 16);
        SOCKET_MAX = b
            .comment("Maximum sockets.", "最多槽位数。")
            .translation(KEY + "crafting.sockets.socket_range.max")
            .defineInRange("max", 2, 0, 16);
        b.pop();
        b.pop();

        // ---- general
        b.comment("General generation behaviour.", "通用生成行为。")
            .translation(KEY + "crafting.general")
            .push("general");
        SKIP_IF_ALREADY_AFFIXED = b
            .comment("When true, items that already carry affixes or sockets are left untouched.",
                "为 true 时，已带有词条或槽位的物品不会被再次修改。",
                "This prevents the crafting grid from being used to re-roll existing Apotheosis loot.",
                "可防止玩家用合成台给已有的神化装备反复刷新词条。")
            .translation(KEY + "crafting.general.skip_if_already_affixed")
            .define("skip_if_already_affixed", true);
        ALLOW_NON_EQUIPMENT = b
            .comment("When false (default), only items that have an Apotheosis loot category are modified.",
                "为 false（默认）时，只有具备神化战利品分类的物品才会被修改。",
                "Blocks, food and plain crafting materials are never given affixes.",
                "方块、食物和普通合成材料永远不会被加上词条。")
            .translation(KEY + "crafting.general.allow_non_equipment")
            .define("allow_non_equipment", false);
        INHERIT_ON_REPAIR_COMBINE = b
            .comment("When true (default), combining two items of the same material (the vanilla",
                "为 true（默认）时，把两件相同物品放在一起合成（原版的「修复物品」配方，",
                "'repair item' crafting recipe, e.g. two damaged pickaxes) inherits the Apotheosis",
                "例如两把破损的镐）会继承输入装备上的神化词条与槽位，",
                "affixes and sockets from the inputs instead of rolling new ones.",
                "而不是重新随机一套。",
                "",
                "Vanilla's repair recipe builds a brand-new plain ItemStack and only carries over",
                "原版修复配方会造一个全新的干净物品，只继承耐久和诅咒附魔，",
                "durability and curse enchantments, so without this the merged tool would look",
                "因此若不处理，合并后的工具看起来像新做的普通装备，",
                "unaffixed to the skip rule and get a fresh random roll - losing the affixes the",
                "进而被当成新装备重新随机一套词条，白白丢掉输入装备的词条。",
                "inputs had. With this toggle on, the affixes/sockets/rarity/name of the most",
                "开启后，取输入中稀有度最高的一件，继承它的词条、槽位、",
                "valuable input (highest rarity, then most sockets) is copied onto the output.",
                "稀有度和名称（多件输入时按稀有度优先、其次槽位数择优）。",
                "",
                "Set to false to stop carrying the inputs' data over.",
                "设为 false 则不再继承输入装备的数据。",
                "",
                "Note: a merge output is never given a fresh random roll either way - two plain items",
                "注意：无论如何，合并产物都不会被重新随机一次——两件无词条装备合并",
                "must not be able to generate affixes. That part is not configurable.",
                "不能凭空产出词条，这一条不可配置。")
            .translation(KEY + "crafting.general.inherit_on_repair_combine")
            .define("inherit_on_repair_combine", true);
        b.pop();

        // ---- rarity
        b.comment(
            "Rarity bounds for generated affixes.",
            "生成词条的稀有度上下限。",
            "Rarities are ordered by their 'sort_index' in the Apotheosis data files:",
            "稀有度按神化数据文件中的 sort_index 排序：",
            "  apotheosis:common=300, apotheosis:uncommon=400, apotheosis:rare=500,",
            "  apotheosis:epic=600, apotheosis:mythic=700.",
            "  apotheosis:common=300、apotheosis:uncommon=400、apotheosis:rare=500、",
            "  apotheosis:epic=600、apotheosis:mythic=700。",
            "A rarity outside [min_rarity, max_rarity] is resolved to the nearest bound,",
            "超出 [min_rarity, max_rarity] 范围的掷点会被压回到最近的边界值，",
            "so a roll that would have been mythic with max_rarity=rare produces a rare item.",
            "例如上限设为 rare 时，原本会掷出的 mythic 会变成 rare。")
            .translation(KEY + "crafting.rarity")
            .push("rarity");
        MIN_RARITY = b
            .comment("Lowest allowed rarity id.", "允许的最低稀有度 id。")
            .translation(KEY + "crafting.rarity.min_rarity")
            .define("min_rarity", "apotheosis:common");
        MAX_RARITY = b
            .comment("Highest allowed rarity id.", "允许的最高稀有度 id。")
            .translation(KEY + "crafting.rarity.max_rarity")
            .define("max_rarity", "apotheosis:rare");
        RESPECT_WORLD_TIER = b
            .comment("When true, the rarity roll uses the crafting player's World Tier and luck,",
                "为 true 时，稀有度按制作玩家的世界层级与幸运值抽取，",
                "exactly like Apotheosis loot generation does. The min/max bounds above still apply.",
                "与神化战利品生成完全一致；上面的上下限依然生效。",
                "When false, every rarity within the bounds is equally likely.",
                "为 false 时，上下限范围内的每个稀有度等概率出现。")
            .translation(KEY + "crafting.rarity.respect_world_tier")
            .define("respect_world_tier", true);
        b.pop();

        b.pop(); // crafting

        // ---------------------------------------------------------------- spawner
        b.comment("Apotheosis spawner restrictions.", "神化对刷怪笼的限制。")
            .translation(KEY + "spawner")
            .push("spawner");
        ENABLE_WARDEN_UNLOCK = b
            .comment(
                "Allows the vanilla Warden spawn egg to be applied to mob spawners.",
                "允许将原版监守者刷怪蛋用于刷怪笼。",
                "Apotheosis refuses this by cancelling the block interaction, and prints a",
                "神化通过取消方块交互来禁止这一操作，",
                "'Cannot be used on a Spawner' line on the egg. When enabled, that refusal is undone",
                "并在刷怪蛋上显示「Cannot be used on a Spawner」提示。",
                "for the Warden egg only.",
                "开启后，该限制仅对监守者刷怪蛋解除。",
                "",
                "Every other blacklisted entry (the #c:bosses tag, mods that add their own entries)",
                "其它黑名单条目（#c:bosses 标签、模组自行添加的条目）",
                "keeps being refused. No Apotheosis config file or data pack is touched.",
                "仍然保持禁用。本功能不修改神化的配置文件或数据包。")
            .translation(KEY + "spawner.enable_warden_unlock")
            .define("enable_warden_unlock", true);
        ENABLE_ELDER_GUARDIAN_UNLOCK = b
            .comment(
                "Allows the vanilla Elder Guardian spawn egg to be applied to mob spawners.",
                "允许将原版远古守卫者刷怪蛋用于刷怪笼。",
                "Same mechanism as enable_warden_unlock: Apothic Spawners blacklists",
                "机制与 enable_warden_unlock 相同：Apothic Spawners 把",
                "minecraft:elder_guardian in its apothic_spawners:blacklisted_from_spawners tag,",
                "minecraft:elder_guardian 列入 apothic_spawners:blacklisted_from_spawners 标签，",
                "and this toggle undoes that refusal for this one mob.",
                "本开关只对该生物解除限制。",
                "",
                "Independent of enable_warden_unlock - either can be enabled without the other.",
                "与 enable_warden_unlock 相互独立，可以只开其中一个。")
            .translation(KEY + "spawner.enable_elder_guardian_unlock")
            .define("enable_elder_guardian_unlock", true);

        // ---- rune tooltips: the sub-section only exists when Apothic Spawners is installed, because the whole
        // feature is derived from that mod's spawner_modifier recipes.
        if (isModLoaded("apothic_spawners")) {
            ENABLE_SPAWNER_RUNE_TOOLTIPS = b
                .comment(
                "Shows what a spawner rune actually changes, inside the rune's own tooltip.",
                "在符文自己的 tooltip 里显示它实际改动了哪些数值。",
                "",
                "Apotheosis describes its runes with one vague line ('Slightly improves a Mob Spawner') and",
                "神化只用一句模糊的描述（「略微提升刷怪笼的性能」），",
                "leaves the numbers to the JEI/EMI recipe page, where they can only be found by opening the",
                "具体数值放在 JEI/EMI 的用途页里，必须打开用途界面、",
                "rune's uses page and hovering each line. This adds them to the tooltip directly.",
                "再逐条悬停才能看到。本项把这些数值直接写进 tooltip。",
                "",
                "The values are read from the loaded data pack's apothic_spawners:spawner_modifier recipes on",
                "数值直接读取当前数据包里的 apothic_spawners:spawner_modifier 配方，",
                "the fly, so a data pack that changes the numbers, or a mod that ships its own runes, is",
                "因此修改了数值的数据包、或自带符文的模组，",
                "described correctly with no code change here.",
                "无需改动本模组代码即可正确显示。",
                "",
                "Client-side only; the server does not need this setting. Hot-reloadable.",
                "仅客户端生效，服务端不需要该设置。可热更新。")
                .translation(KEY + "spawner.enable_spawner_rune_tooltips")
                .define("enable_spawner_rune_tooltips", true);
        }

        b.pop(); // spawner

        // ---------------------------------------------------------------- player
        b.comment(
            "Player behaviour fixes and multiplayer spawn tuning.",
            "玩家行为修复与多人刷怪调整。",
            "",
            "The first group corrects problems that come from Apotheosis' attribute-based max",
            "第一组选项修正由神化「基于属性的最大生命值」引起的问题，",
            "health colliding with vanilla assumptions that max health is always 20.",
            "这些问题的根源是原版假设最大生命值恒为 20。",
            "",
            "The second group widens the vanilla monster cap so that players who share one",
            "第二组选项放宽原版怪物上限，让同处一个刷怪平台的同层级玩家",
            "spawn area gain something from being together.",
            "能因为组队而获得收益。")
            .translation(KEY + "player")
            .push("player");
        ENABLE_RESPAWN_HEALTH_FIX = b
            .comment(
                "Respawn at full health measured against your real maximum, instead of a flat 20 HP.",
                "复活时按真实最大生命值回满血，而不是固定 20 点血。",
                "",
                "Vanilla ServerPlayer#restoreFrom copies only attribute BASE values, then calls",
                "原版 ServerPlayer#restoreFrom 只复制属性的「基础值」，随后调用",
                "setHealth(getMaxHealth()) and only afterwards puts the inventory back. Item",
                "setHealth(getMaxHealth())，之后才把物品栏还原。装备提供的属性修饰符",
                "attribute modifiers are re-applied on the next tick, so at the moment health is",
                "要到下一刻才会重新应用，因此读取最大生命值时装备加成还没生效，",
                "read the gear is not counted yet and the player always returns at 20 HP.",
                "玩家总是以 20 点血复活。",
                "Apotheosis' own +max health armor affixes are exactly such modifiers, so a",
                "神化护甲的 +最大生命词缀正属于这类修饰符，所以穿着 +40 最大生命的装备",
                "player wearing them still respawns at 20/60.",
                "死亡后复活仍然是 20/60。",
                "",
                "When enabled, vanilla's own respawn rule (set to full health) is re-applied on",
                "开启后，会在复活后的第一个游戏刻（此时装备加成已恢复）重新执行原版的",
                "the first tick after respawn, once the gear modifiers are back - so a player",
                "「复活即回满血」规则。这样玩家会按自己的最大生命值回满血，",
                "respawns at their real maximum. Only death respawns are affected; travelling",
                "而不是固定 20 点。只影响死亡复活；",
                "between dimensions keeps vanilla carry-over behaviour.",
                "跨维度传送仍按原版规则保留当前血量。")
            .translation(KEY + "player.enable_respawn_health_fix")
            .define("enable_respawn_health_fix", true);
        ENABLE_MOB_CAP_SCALING = b
            .comment(
                "Scale the monster cap by how many same-tier players share the area.",
                "按同层级玩家数量成倍放宽怪物上限。",
                "",
                "Vanilla caps hostile mobs twice: each player within 128 blocks of a chunk gets 70",
                "原版对敌对生物有两道上限：每个刷怪区块 128 格内的玩家各自有 70 只的",
                "(checked per player), and the whole level is capped at",
                "额度（逐个玩家判定），整个维度又被",
                "  70 x (union of every player's 17x17 spawn boxes) / 289",
                "  70 ×（所有玩家 17x17 刷怪区块框的并集）/ 289",
                "monsters at once. That union is why two players standing together still get",
                "限制着。正因为是「并集」，两名玩家挨着站时得到的仍然是",
                "roughly one player's worth - their boxes overlap almost completely.",
                "一个人的量，因为两人的区块框几乎完全重合。",
                "",
                "This option multiplies both limits by 1 + bonus x (n - 1), where n is the size of",
                "本选项把两道上限同乘 1 + 倍率 × (n - 1)，n 是刷怪点附近",
                "the largest group of nearby players that have the SAME world tier:",
                "「世界层级相同」的最大一群玩家人数：",
                "",
                "  n = 1 -> x1.0   (70, unchanged - vanilla behaviour)",
                "  n = 2 -> x1.5   (105)",
                "  n = 3 -> x2.0   (140)",
                "",
                "Only same-tier players count, because Apotheosis already picks mob strength from",
                "只统计同层级玩家，因为神化本就按最近玩家的层级决定刷出的怪物强弱，",
                "the nearest player's tier. A mixed-tier pair would otherwise inflate the budget",
                "若按总人数计算，层级不同的一对人也能抬高额度，",
                "without matching that model; keeping it to one tier keeps the two systems consistent.",
                "那与神化的模型不符；限制在单一层级内才能让两套系统保持一致。",
                "",
                "Only hostile categories are affected. Animals and water creatures keep their own",
                "只影响敌对生物分类。动物与水生物沿用各自的原版上限，",
                "vanilla caps, and mob spawners are not affected at all.",
                "刷怪笼也完全不受影响。",
                "",
                "Server-authoritative; no data reload needed.",
                "服务端生效，改动无需重载数据。")
            .translation(KEY + "player.enable_mob_cap_scaling")
            .define("enable_mob_cap_scaling", false);
        MOB_CAP_COUNT_ALL_TIERS = b
            .comment(
                "Count every nearby player, ignoring world tier.",
                "统计附近所有玩家，忽略世界层级。",
                "",
                "OFF (default): only the largest group of players sharing a world tier is counted,",
                "关闭（默认）：只统计「世界层级相同」的最大一群玩家，",
                "because Apotheosis already derives mob strength from the nearest player's tier.",
                "因为神化本就按最近玩家的层级决定刷怪强弱。",
                "",
                "ON: every non-spectator player within range counts, so a mixed-tier group also",
                "开启：范围内所有非旁观玩家都计入，因此混层级组队也能抬高上限，",
                "raises the cap. This matches a plain reading of 70 x (1 + bonus x (nearby - 1)).",
                "与 70 × (1 + 倍率 × (附近人数 - 1)) 的字面含义一致。",
                "",
                "  same-tier OFF: 1 haven + 2 frontier -> counts the 2 frontier players -> x1.5",
                "  同层级 关：1 个 haven + 2 个 frontier → 只算 2 个 frontier → x1.5",
                "  same-tier ON : 1 haven + 2 frontier -> counts all 3            -> x2.0",
                "  同层级 开：1 个 haven + 2 个 frontier → 算 3 个 → x2.0",
                "",
                "Either way the multiplier is still capped by mob_cap_max_players and still",
                "无论哪种，倍率仍受 mob_cap_max_players 限制，且仍",
                "ignores spectators and friendly categories.",
                "忽略旁观者与友好生物分类。",
                "",
                "Server-authoritative, and hot-reloadable: editing the file takes effect on the",
                "服务端生效，且可热更新：保存文件后，下一个区块刷怪判定",
                "next chunk pass without rejoining or restarting.",
                "就按新值计算，无需重进或重启。")
            .translation(KEY + "player.mob_cap_count_all_tiers")
            .define("mob_cap_count_all_tiers", false);
        MOB_CAP_BONUS_PER_PLAYER = b
            .comment(
                "Extra monster-cap multiplier per additional same-tier player.",
                "每多一名同层级玩家，怪物上限额外增加的倍率。",
                "",
                "Final multiplier = 1 + bonus x (n - 1), capped at mob_cap_max_players.",
                "最终倍率 = 1 + 倍率 × (n - 1)，n 受 mob_cap_max_players 限制。",
                "",
                "  0.5 -> 70 / 105 / 140 for 1 / 2 / 3 players (the default)",
                "  1.0 -> 70 / 140 / 210 - one extra player doubles the cap",
                "  0.0 -> no bonus at all, even with the master switch on",
                "",
                "Only read while enable_mob_cap_scaling is on.",
                "仅在 enable_mob_cap_scaling 开启时读取。",
                "",
                "Server-authoritative.",
                "服务端生效。")
            .translation(KEY + "player.mob_cap_bonus_per_player")
            .defineInRange("mob_cap_bonus_per_player", 0.5D, 0.0D, 16.0D);
        MOB_CAP_MAX_PLAYERS = b
            .comment(
                "Largest player count the bonus is paid for.",
                "参与计算的玩家数量上限。",
                "",
                "Acts as the ceiling of n in 1 + bonus x (n - 1), so a large server cannot scale",
                "作为 1 + 倍率 × (n - 1) 中 n 的上限，避免大服务器把怪物上限推到",
                "the cap without bound. Players beyond this number still spawn and fight normally,",
                "失控。超出的玩家照常刷怪、照常战斗，",
                "they simply stop adding budget.",
                "只是不再增加额度。",
                "",
                "Server-authoritative.",
                "服务端生效。")
            .translation(KEY + "player.mob_cap_max_players")
            .defineInRange("mob_cap_max_players", 8, 2, 64);
        SYNC_MOB_CAP_TO_HUD = b
            .comment(
                "Show the scaled cap in MiniHUD / Servux instead of the vanilla 70.",
                "在 MiniHUD / Servux 中显示放宽后的上限，而不是原版 70。",
                "",
                "Those HUDs do not read the spawn check's result; each recomputes",
                "这些 HUD 并不读取刷怪判定的结果，而是各自在客户端/服务端重算",
                "  capacity = getMaxInstancesPerChunk() * spawnableChunks / 289",
                "  capacity = getMaxInstancesPerChunk() × 可刷区块 / 289",
                "in its own code, so they keep printing x/70 no matter what the spawn",
                "因此无论刷怪侧放宽到多少，它们都会继续显示 x/70。",
                "side actually allows. With this on, the mod redirects that one call inside",
                "开启后，本模组会重定向它们代码内的那一处调用，",
                "their code and substitutes the real multiplier.",
                "改用真实的倍率。",
                "",
                "COLD SWITCH: off by default. With it off nothing is substituted and both",
                "冷开关：默认关闭。关闭时不做任何替换，两个",
                "HUDs behave exactly like an unpatched install, numbers included.",
                "HUD 的行为与未打补丁时完全一致，包括显示的数字。",
                "",
                "The displayed number is per-dimension (the maximum multiplier anywhere",
                "显示的数字按维度统计（取该维度内的最大倍率），",
                "in that dimension), matching the HUD's own level-wide granularity - not the",
                "与 HUD 本身的「全维度」口径一致，而不是",
                "value for the block you are standing on.",
                "你脚下这一格的值。",
                "",
                "Only affects HUDs that are actually installed. Requires",
                "只影响实际安装了的 HUD；需同时开启",
                "enable_mob_cap_scaling. Server-authoritative (inputs), but the HUD side",
                "enable_mob_cap_scaling。数值计算在服务端，但 HUD 侧",
                "must be able to read the option, so it is a COMMON option.",
                "需要读到该选项，因此放在 COMMON 配置中。")
            .translation(KEY + "player.sync_mob_cap_to_hud")
            .define("sync_mob_cap_to_hud", false);
        FAKE_PLAYERS_INHERIT_WORLD_TIER = b
            .comment(
                "Fake players take their summoner's world tier.",
                "假人继承召唤者的世界层级。",
                "",
                "Apotheosis keeps the world tier in an attachment that only its own setter writes.",
                "神化把世界层级存在一个只由它自己写入的附件里。",
                "A fake player is a real ServerPlayer that never logs in, so it keeps the",
                "假人是真正的 ServerPlayer，但从不走登录流程，所以它始终停在",
                "default HAVEN - and the shared monster cap only counts players of the SAME",
                "默认的 HAVEN——而共享刷怪额度只统计「同层级」玩家，",
                "tier, so a fake standing next to you never raises the cap.",
                "因此挨着你站的假人永远抬不高上限。",
                "",
                "When enabled, a fake player adopts the tier of:",
                "开启后，假人会取以下来源的层级：",
                "  1. an online player with the same name  (/player Steve spawn)",
                "  1. 同名的在线玩家（/player Steve spawn）",
                "  2. an online player with the same UUID   (carpet shadow players)",
                "  2. 同 UUID 的在线玩家（carpet 影子玩家）",
                "  3. the nearest real player in the same dimension",
                "  3. 同一维度内最近的真实玩家",
                "",
                "Sources 1 and 2 are authoritative and always win, so a fake re-spawned by a",
                "来源 1 与 2 是权威的，永远优先：假人被 A 召唤过、退出后再由 B 召唤时，",
                "different player follows its new owner. That is what makes the tier saved in",
                "会改成 B 的层级。假人退出时层级会写进 playerdata，这条规则就是用来",
                "the fake's playerdata harmless across re-spawns.",
                "让那份存档数据不碍事的。",
                "",
                "Source 3 is only a guess based on distance, so it fills in a missing or",
                "来源 3 只是按距离猜的，因此它只用来补全「尚未设置」或仍为默认值的层级，",
                "still-default tier but never overwrites a deliberate one. Otherwise a fake",
                "绝不覆盖已经明确设过的层级——否则共享平台上的假人会在",
                "parked on a busy platform would change owner whenever another player walked by.",
                "别的玩家路过时莫名其妙地换主人。",
                "",
                "Server-authoritative. Has no effect when no fake-player mod is installed.",
                "服务端生效。未安装假人模组时完全无影响。")
            .translation(KEY + "player.fake_players_inherit_world_tier")
            .define("fake_players_inherit_world_tier", false);
        DEBUG_MOB_CAP = b
            .comment(
                "Log the monster-cap multiplier calculation.",
                "记录怪物上限倍率的计算过程。",
                "",
                "Prints one INFO line per chunk per tick listing every nearby player with",
                "每个区块每 tick 打一条 INFO，列出附近每个玩家及其",
                "the world tier it resolved to, the largest same-tier group, and the resulting",
                "解析出的世界层级、最大同层级组的大小、以及最终倍率。",
                "multiplier. That is the only way to see why the bonus did or did not apply.",
                "这是唯一能看清加成为何生效/不生效的办法。",
                "",
                "Very loud - enable it, read the log, then turn it off.",
                "非常吵——排查时打开，看完就关掉。",
                "",
                "Server-authoritative.",
                "服务端生效。")
            .translation(KEY + "player.debug_mob_cap")
            .define("debug_mob_cap", false);

        // ---- extra spawn lanes ------------------------------------------------
        //
        // Kept separate from the cap options because they solve different farms: the cap options raise how many mobs
        // may EXIST (helps a farm that is capped), while an extra lane raises how many spawn ATTEMPTS happen per tick
        // (helps a farm whose mobs die before the cap is reached). Measured on a wandering tower: 1.97x output for
        // about +0.21 ms/tick.
        ENABLE_SPAWN_LANES = b
            .comment(
                "Run a second monster spawn pass per chunk when the area is dangerous enough.",
                "区域内区域难度较高时，每个区块额外执行一次刷怪尝试。",
                "",
                "Reuses the same SpawnState, so the population cap still holds - this raises",
                "复用同一个 SpawnState，存量上限仍然生效——提高的是产量，",
                "throughput, not the cap. Only hostile categories repeat; spawners are untouched.",
                "不是上限。只重复敌对分类，刷怪笼不受影响。",
                "",
                "Server-authoritative; hot-reloadable.",
                "服务端生效，可热更新。")
            .translation(KEY + "player.enable_spawn_lanes")
            .define("enable_spawn_lanes", false);
        SPAWN_LANES_MIN_REGIONAL_DIFFICULTY = b
            .comment(
                "Regional difficulty required before the second pass runs.",
                "开启第二条刷怪游走所需的区域难度。",
                "",
                "This is vanilla's Regional Difficulty - the value behind the F3 \"Local Difficulty\"",
                "这是原版的区域难度——即 F3「本地难度」显示的那个数，",
                "line, range 0.0-6.75, not the Apotheosis world tier. It rises with world time,",
                "范围 0.0–6.75，不是神化的世界层级。它随世界时间、区块已居住时间",
                "chunk inhabited time and nearby player count, and it already decides how strong",
                "与附近玩家人数增长，本来也决定刷出怪物的强弱，用它做门槛",
                "spawned mobs are, so gating on it keeps the two consistent.",
                "能让两套系统保持一致。",
                "",
                "The default 5.5 is a late-game gate a fresh area cannot reach.",
                "默认 5.5 属后期门槛，新区达不到。",
                "Set to 0.0 to always allow it while the feature is on.",
                "设为 0.0 则功能开启时始终允许。",
                "",
                "Server-authoritative; hot-reloadable.",
                "服务端生效，可热更新。")
            .translation(KEY + "player.spawn_lanes_min_regional_difficulty")
            .defineInRange("spawn_lanes_min_regional_difficulty", 5.5D, 0.0D, 6.75D);
        SPAWN_LANES_REAL_PLAYERS_ONLY = b
            .comment(
                "Count only real players when checking regional difficulty.",
                "判定区域难度时只统计真实玩家。",
                "",
                "Regional difficulty counts every ServerPlayer, fake players included, so with",
                "区域难度会统计所有 ServerPlayer，包括假人，因此关闭本项时",
                "this off a few parked bots could push an empty area over the threshold.",
                "几个挂机假人就能把空区域推过门槛。",
                "",
                "ON (default): only real connections count.",
                "开启（默认）：只统计真实连接。",
                "OFF: fake players count too, which is convenient for testing.",
                "关闭：假人同等计入，方便测试。",
                "",
                "Server-authoritative; hot-reloadable.",
                "服务端生效，可热更新。")
            .translation(KEY + "player.spawn_lanes_real_players_only")
            .define("spawn_lanes_real_players_only", true);
        b.pop(); // player

        // ---------------------------------------------------------------- adventure
        b.comment(
            "Adventure behaviour tweaks.",
            "冒险行为调整。",
            "",
            "Currently: keeping player-named mobs alive when the world is switched to Peaceful,",
            "当前包含：切换到和平模式时，保留被玩家命名的生物，",
            "and keeping a player-named piglin from zombifying outside the nether.",
            "以及被玩家命名的猪灵在主世界不会变成僵尸猪灵。")
            .translation(KEY + "adventure")
            .push("adventure");
        ENABLE_NAMED_PEACEFUL_PROTECTION = b
            .comment(
                "Mobs a player named with a name tag survive Peaceful.",
                "玩家用命名牌命名的生物，在和平模式下不会消失。",
                "",
                "Vanilla deletes hostile mobs on Peaceful before it ever looks at the persistence flag,",
                "原版在和平模式下删除敌对生物，这个判断排在持久化标记之前，",
                "so a name tag - which only sets that flag - does nothing. This cannot be fixed from",
                "而命名牌只设置持久化标记，所以完全无效。",
                "",
                "The rule keys off the entity tag 'apotheosis_expansion.named', which is applied when a",
                "本规则依据实体标签 'apotheosis_expansion.named'，该标签只在玩家成功使用命名牌时，",
                "player successfully applies a name tag. It is not the custom name, because Apotheosis",
                "才会被打上。它不看自定义名称，因为神化会给每个入侵者和小头目",
                "generates a name for every invader and elite, and slime splits inherit the parent name;",
                "自动生成名字，史莱姆分裂也会继承父体名字，",
                "using the name would protect mobs the player never touched.",
                "用名称判断会连带保护玩家从未命名过的生物。",
                "",
                "The tag persists in the save and can be managed by command:",
                "该标签会随存档保存，也可以用命令增删：",
                "/tag @e[type=zombie] add apotheosis_expansion.named",
                "/tag @e[type=zombie] add apotheosis_expansion.named",
                "",
                "A protected mob still cannot target or damage a player in Peaceful, but it can attack",
                "受保护的生物在和平模式下仍然无法把玩家设为目标、也打不出伤害，但它可以攻击",
                "villagers and animals, and creepers/withers can still break blocks.",
                "村民和动物，苦力怕和凋灵也照样能破坏方块。",
                "",
                "Note: the tag is lost on mob conversion (zombie to drowned, villager to witch) and on",
                "注意：生物转换（僵尸变溺尸、村民变女巫）和史莱姆分裂时标签会丢失，",
                "slime splitting; re-tag the result if needed.",
                "需要时请对结果重新打标。")
            .translation(KEY + "adventure.enable_named_peaceful_protection")
            .define("enable_named_peaceful_protection", true);
        ENABLE_NAMED_PIGLIN_NO_ZOMBIFY = b
            .comment(
                "A player-named piglin or piglin brute never turns into a zombified piglin.",
                "玩家命名的猪灵 / 猪灵蛮兵在主世界不会变成僵尸猪灵。",
                "",
                "Vanilla starts the conversion whenever the dimension is not piglinSafe (the Overworld and",
                "原版的转化条件只看维度是否 piglinSafe（主世界与末地都不是），",
                "the End) and finishes 300 ticks (15 seconds) later; only the NBT flag",
                "300 tick（15 秒）后完成转化；只有存档字段 IsImmuneToZombification",
                "IsImmuneToZombification or walking through a nether portal grants immunity -",
                "和穿过下界传送门才能获得免疫，",
                "a name tag does nothing, so a pet piglin followed over to the Overworld is lost.",
                "命名牌完全不起作用，牵过来的宠物猪灵 15 秒后就没了。",
                "",
                "Same condition as enable_named_peaceful_protection: the entity tag",
                "判定条件与 enable_named_peaceful_protection 相同：实体标签",
                "'apotheosis_expansion.named', applied when a player uses a name tag.",
                "'apotheosis_expansion.named'，即玩家使用命名牌成功命名时打上。",
                "",
                "The tagged piglin keeps its normal behaviour instead of retreating from its own",
                "conversion; removing the tag or this toggle starts the 15-second count from zero.",
                "被打标的猪灵保持正常行为，不会一边准备转化一边后退；",
                "去掉标签或关闭本开关后，15 秒计时会从头重新开始。",
                "",
                "Piglin and piglin brute only, matching the request: hoglins have a separate",
                "只针对猪灵与猪灵蛮兵（与需求一致）：疣猪兽有独立的镜像规则，",
                "(mirrored) vanilla rule and are not touched.",
                "本功能不影响它。")
            .translation(KEY + "adventure.enable_named_piglin_no_zombify")
            .define("enable_named_piglin_no_zombify", true);
        b.pop(); // adventure

        // ---------------------------------------------------------------- gems
        b.comment(
            "Gem drop tweaks, ported from the Gem Tweaks mod.",
            "宝石掉落调整，迁移自 Gem Tweaks mod。",
            "",
            "Everything lives in this one file - there is no separate gemtweaks.toml any more.",
            "所有设置都在本文件中，不再有独立的 gemtweaks.toml。",
            "Note: the per-gem weight list is only populated while a world is loaded, because the set of",
            "注意：每种宝石的权重列表只在进入存档后才会被填充，因为宝石 id 来自数据包。",
            "gem ids comes from data packs. Newly added gems are appended automatically.",
            "新出现的宝石会被自动追加。")
            .translation(KEY + "gems")
            .push("gems");
        ENABLE_GEM_TWEAKS = b
            .comment("Master switch for all gem drop tweaks below.",
                "下方所有宝石掉落调整的总开关。",
                "When false, gems drop exactly as Apotheosis ships them.",
                "为 false 时，宝石完全按神化原本的规则掉落。")
            .translation(KEY + "gems.enable_gem_tweaks")
            .define("enable_gem_tweaks", true);
        GEM_DROP_CHANCE_MULTIPLIER = b
            .comment(
                "Multiplier applied to the drop chance of every gem loot table entry.",
                "作用于每条宝石掉落表条目概率的倍率。",
                "This scales the chance field of Apotheosis' GemLootModifier entries, which is the",
                "它缩放的是神化 GemLootModifier 的 chance 字段，也就是",
                "'did a gem drop at all' roll (monster kills, chest loot, ...).",
                "「到底掉不掉宝石」这次判定（击杀怪物、宝箱战利品等）。",
                "",
                "  2.0 = twice as likely",
                "  0.5 = half as likely",
                "  0.0 = gems never drop from these tables",
                "  2.0 = 概率翻倍",
                "  0.5 = 概率减半",
                "  0.0 = 这些表不再掉落宝石",
                "",
                "Values are clamped to [0, 1000000]. Resulting chances are clamped to [0, 1].",
                "取值范围限制在 [0, 1000000]，最终概率限制在 [0, 1]。")
            .translation(KEY + "gems.gem_drop_chance_multiplier")
            .defineInRange("gem_drop_chance_multiplier", 1.0D, 0.0D, 1000000.0D);
        GEM_WEIGHT_MULTIPLIERS = b
            .comment(
                "Per-gem relative drop weight multipliers.",
                "每种宝石的相对掉落权重倍率。",
                "",
                "After a drop is rolled, the concrete gem is picked by weight:",
                "判定掉落之后，具体是哪一颗宝石按权重随机选出：",
                "  final chance = own weight x multiplier / sum(all weights x multipliers)",
                "  最终概率 = 自身权重 x 倍率 / 所有（权重 x 倍率）之和",
                "",
                "  0   = this gem never drops",
                "  >1  = more likely",
                "  <1  = less likely",
                "  0   = 该宝石永不掉落",
                "  >1  = 更容易掉落",
                "  <1  = 更难掉落",
                "A gem that is not listed keeps its original weight.",
                "未列出的宝石保持原版权重。",
                "",
                "Format: \"<gem id>=<multiplier>\", one entry per line.",
                "格式：\"<宝石 id>=<倍率>\"，每项一行。",
                "Short forms are accepted too: the namespaced path (core/ballast) or the bare file",
                "也支持简写：命名空间内路径（core/ballast）或仅文件名（ballast）。",
                "name (ballast). Entries that match no loaded gem are ignored.",
                "匹配不到任何已加载宝石的条目会被忽略。",
                "",
                "This list is seeded with every loaded gem id while a world is running; newly added",
                "进入存档后本列表会自动列出全部已加载宝石 id；",
                "gems are appended automatically with a multiplier of 1.0.",
                "新增的宝石会以 1.0 的倍率自动追加。")
            .translation(KEY + "gems.gem_weight_multipliers")
            .defineListAllowEmpty("gem_weight_multipliers", List.of(), () -> "apotheosis:core/ballast=1.0",
                Config::isValidWeightEntry);
        // The compendium only means anything with a recipe viewer installed, so like the spawner-rune toggle the
        // key is registered conditionally: no JEI and no EMI means no empty switch in the config screen. The probe
        // is a class-resource lookup plus the mod list, both safe at config-construction time and on a server.
        if (com.tamamo.apotheosisexpansion.gemcompendium.GemCompendiumGate.anyViewerPresent()) {
            ENABLE_GEM_COMPENDIUM = b
                .comment(
                "Adds a gem compendium to JEI / EMI: which dimensions a gem drops in, and what each",
                "在 JEI / EMI 中加入「宝石图鉴」：每种宝石能掉在哪些维度，以及每条增幅",
                "bonus gives at every purity, side by side on one page per gem.",
                "在各个纯度下的数值（每颗宝石一页，纯度数值以「/」并列）。",
                "",
                "Apotheosis' own Patchouli entry only says gems 'may be found in chest loot and rarely",
                "神化自带的帕秋莉条目只说宝石「可能在箱子战利品中找到，也可能在杀怪时少量找到」，",
                "from mobs', and its data files - the only place the real rules live - are not readable",
                "而真正的规则只存在于数据文件里，玩家无从查看。",
                "in game. The pages added here are generated from the loaded gem registry, so a data",
                "本功能直接读取已加载的宝石注册表生成页面，因此数据包改动、",
                "pack that retunes a gem, or a mod that ships its own gems, is described correctly",
                "或其它模组自带的宝石，都会被正确显示，",
                "with no code change here.",
                "无需改动本模组代码。",
                "",
                "The two recipe viewers are handled as one feature, not two. When JEI and EMI are both",
                "两个配方查看器被当作同一个功能处理，而不是两套：",
                "installed only the EMI pages are registered, because EMI already imports JEI plugins",
                "同时安装 JEI 与 EMI 时只注册 EMI 页面——EMI 的 JEMI 桥接本来就会导入 JEI 插件，",
                "through JEMI and registering both would show every page twice. With only JEI, or only",
                "两边都注册会让每个页面重复出现。只装 JEI 或只装 EMI 时，",
                "EMI, that viewer gets the pages on its own.",
                "则由该查看器单独提供页面。",
                "",
                "Client-side only; a dedicated server does not need this setting. The pages are built",
                "仅客户端生效，服务端不需要该设置。页面在查看器重建索引时生成，",
                "when the viewer rebuilds its index, which happens on world join and on every resource",
                "即进入存档与每次重载资源（F3+T）时，",
                "reload (F3+T), so the toggle takes effect without restarting the game.",
                "因此开关无需重启游戏即可生效。")
                .translation(KEY + "gems.enable_gem_compendium")
                .define("enable_gem_compendium", true);
        }
        b.pop(); // gems

        // ---------------------------------------------------------------- compat
        b.comment(
            "Mod integrations.",
            "模组联动。",
            "",
            "Each mod gets its own sub-section, and a sub-section only appears when that mod is",
            "每个模组各有独立子段；未安装的模组不会注册该项，界面上也就不会出现空开关。",
            "installed. Within a sub-section, the first switch is the group master switch and the",
            "子段内第一个开关是该组总开关，其余为该组内各生物的独立开关。",
            "rest are per-mob switches. Turning the group off wins over any per-mob switch.",
            "总开关关闭时，单个开关一律不生效。",
            "",
            "All of these are data-pack conditions, so changing one needs a data reload",
            "这些开关都作用于数据包条件，改动后需要重载数据（F3+T 或重进存档），",
            "(F3+T or rejoining a world), not just a config reload.",
            "仅重载配置不够。")
            .translation(KEY + "compat")
            .push("compat");

        // ---- Thermal: the three elemental invaders (+ a fourth for the nether biome)
        if (isModLoaded("thermal")) {
            b.comment(
                "Thermal Series integration.",
                "热力（Thermal）联动。")
                .translation(KEY + "compat.thermal")
                .push("thermal");
            ENABLE_THERMAL_INVADERS = b
                .comment(
                    "Adds Apotheosis Invaders for the Thermal elementals, which Apotheosis does not cover.",
                    "为热力（Thermal）的元素生物添加神化入侵者；神化本体完全没有这部分内容。",
                    "",
                    "Covers thermal:basalz, thermal:blizz and thermal:blitz.",
                    "覆盖岩石人、暴雪人、狂风人。",
                    "",
                    "Every entry also carries a mod_loaded condition, so this switch is only a second gate;",
                    "每条数据还带有 mod_loaded 条件，因此本开关只是第二道门；",
                    "removing the mod makes the entries vanish regardless of this setting.",
                    "卸载热力后无论本开关如何，条目都不会载入。")
                .translation(KEY + "compat.thermal.enable_thermal_invaders")
                .define("enable_thermal_invaders", true);
            ENABLE_BASALZ_INVADER = entitySwitch(b, "thermal:basalz", "Basalz", "岩石人", true);
            ENABLE_BLIZZ_INVADER = entitySwitch(b, "thermal:blizz", "Blizz", "暴雪人", true);
            ENABLE_BLITZ_INVADER = entitySwitch(b, "thermal:blitz", "Blitz", "狂风人", true);
            b.pop(); // thermal
        }

        // ---- Mekanism Additions: the baby mobs
        if (isModLoaded("mekanismadditions")) {
            b.comment(
                "Mekanism Additions integration.",
                "热力（Mekanism）附属 Additions 联动。",
                "",
                "The baby mobs live in the separate mekanismadditions module, not in the main Mekanism jar,",
                "宝宝实体位于独立的 mekanismadditions 模块，不在 Mekanism 主包内，",
                "so this sub-section only appears when that module is installed.",
                "因此只有装了该模块时才会出现本子段。")
                .translation(KEY + "compat.mekanism")
                .push("mekanism");
            ENABLE_MEKANISM_INVADERS = b
                .comment(
                    "Adds Apotheosis Invaders for the Mekanism Additions baby mobs.",
                    "为热力（Mekanism）附属 Additions 的宝宝实体添加神化入侵者。",
                    "",
                    "Covers baby_enderman, baby_skeleton, baby_stray, baby_bogged and baby_wither_skeleton.",
                    "覆盖末影人、骷髅、流髑、沼骸、凋灵骷髅五种宝宝。",
                    "Weights are one third of the matching adult invader (one quarter for the wither skeleton).",
                    "权重取对应成年体入侵者的三分之一（凋灵骷髅为四分之一）。",
                    "",
                    "baby_creeper is not covered: Apotheosis has no creeper invader to scale from.",
                    "不覆盖苦力怕宝宝：神化没有苦力怕入侵者可作基准。")
                .translation(KEY + "compat.mekanism.enable_mekanism_invaders")
                .define("enable_mekanism_invaders", true);
            ENABLE_BABY_ENDERMAN_INVADER = entitySwitch(b, "mekanismadditions:baby_enderman", "Baby Enderman", "末影人宝宝", true);
            ENABLE_BABY_SKELETON_INVADER = entitySwitch(b, "mekanismadditions:baby_skeleton", "Baby Skeleton", "骷髅宝宝", true);
            ENABLE_BABY_STRAY_INVADER = entitySwitch(b, "mekanismadditions:baby_stray", "Baby Stray", "流髑宝宝", true);
            ENABLE_BABY_BOGGED_INVADER = entitySwitch(b, "mekanismadditions:baby_bogged", "Baby Bogged", "沼骸宝宝", true);
            ENABLE_BABY_WITHER_SKELETON_INVADER = entitySwitch(b, "mekanismadditions:baby_wither_skeleton", "Baby Wither Skeleton", "凋灵骷髅宝宝", true);
            b.pop(); // mekanism
        }

        // ---- Mekanism: Tools (material gear: osmium / bronze / steel / lapis / glowstone / obsidian) ----
        if (isModLoaded("mekanismtools")) {
            b.comment(
                "Mekanism: Tools integration.",
                "热力（Mekanism）工具模块联动。",
                "",
                "mekanismtools is a separate mod from the mekanismadditions module above: it ships the",
                "mekanismtools 与上面的 mekanismadditions 是两个独立模块：前者提供",
                "material tool and armour sets (osmium, bronze, steel, lapis lazuli, refined glowstone,",
                "按材质的工具与盔甲套装（锇、青铜、钢、青金石、强化荧石、",
                "refined obsidian) plus the paxels. This sub-section only appears when it is installed.",
                "强化黑曜石）以及镐斧。因此只有装了该模块时才会出现本子段。")
                .translation(KEY + "compat.mekanism_tools")
                .push("mekanism_tools");
            ENABLE_MEKANISM_TOOLS_AFFIXES = b
                .comment(
                    "Adds the Mekanism: Tools equipment to Apotheosis' affix loot pool.",
                    "把热力工具模块的装备加入神化的词缀战利品池。",
                    "",
                    "Without this the sets are still affixed when a player crafts them (that is the [crafting]",
                    "不开启时，玩家自己打造这些装备仍会带词条（那是 [crafting] 段的功能），",
                    "section), but they can never drop as affixed loot from chests or bosses.",
                    "但它们永远不会作为带词条的箱子/BOSS 掉落物出现。",
                    "",
                    "Weights sit at 2 per item, so the sets stay visible without diluting the vanilla and",
                    "权重定为每件 2，既能看见这批装备，又不会明显稀释原版与",
                    "Twilight Forest entries that already fill these pools.",
                    "暮色森林已有的条目。",
                    "",
                    "Takes effect on the next data reload (F3+T or rejoining a world).",
                    "改动后需重载数据（F3+T 或重进存档）才会生效。")
                .translation(KEY + "compat.mekanism_tools.enable_mekanism_tools_affixes")
                .define("enable_mekanism_tools_affixes", true);
            ENABLE_MEKANISM_TOOLS_SALVAGING = b
                .comment(
                    "Adds salvaging table recipes for the Mekanism: Tools equipment.",
                    "为热力工具模块的装备添加回收台配方。",
                    "",
                    "Yields follow the vanilla pattern Apotheosis already ships: armour returns 1-3 ingots,",
                    "产出沿用神化自带原版配方的比例：护甲 1~3 个锭，",
                    "tools 0-1, paxels and shields 1-2. Damaged items yield proportionally less - that",
                    "工具 0~1，镐斧与盾 1~2。耐久越低产出越少，这是",
                    "scaling is Apotheosis' own, not something this mod adds.",
                    "神化自身的机制，不是本模组加的。",
                    "",
                    "Takes effect on the next data reload (F3+T or rejoining a world).",
                    "改动后需重载数据（F3+T 或重进存档）才会生效。")
                .translation(KEY + "compat.mekanism_tools.enable_mekanism_tools_salvaging")
                .define("enable_mekanism_tools_salvaging", true);
            ENABLE_MEKANISM_TOOLS_GEAR_SETS = b
                .comment(
                    "Lets Apotheosis invaders and bosses wear Mekanism: Tools equipment.",
                    "允许神化入侵者与 BOSS 穿戴热力工具模块的装备。",
                    "",
                    "Adds gear sets to every Apotheosis world tier and attaches them to the existing",
                    "为神化的每个世界层级添加套装，并挂到现有的",
                    "invader gear-set tags, so the mobs can pick these sets alongside the vanilla ones.",
                    "入侵者套装标签上，使怪物能与原版套装一同抽取这些装备。",
                    "",
                    "The sets are only a candidate: Apotheosis still rolls one set per mob from the whole",
                    "这些套装只是候选：神化仍会在该层级的全部套装中",
                    "tier's pool, so the effect is a wider variety rather than every boss wearing osmium.",
                    "为每只怪物抽一套，因此效果是装备种类更丰富，而不是每只 BOSS 都穿锇甲。",
                    "",
                    "Takes effect on the next data reload (F3+T or rejoining a world).",
                    "改动后需重载数据（F3+T 或重进存档）才会生效。")
                .translation(KEY + "compat.mekanism_tools.enable_mekanism_tools_gear_sets")
                .define("enable_mekanism_tools_gear_sets", true);
            b.pop(); // mekanism_tools
        }

        // ---- Mekanism (main mod): keep energy gear out of the crafted-affix roll ----
        if (isModLoaded("mekanism")) {
            b.comment(
                "Mekanism energy equipment.",
                "热力（Mekanism）本体能量装备。",
                "",
                "MekaSuit, MekaTool, the atomic disassembler, jetpacks, free runners, the scuba gear and",
                "机甲服、机甲工具、原子分解器、喷气背包、自由跑鞋、潜水装备、",
                "the hazmat suit are all ArmorItem/DiggerItem subclasses, so Apotheosis classifies them as",
                "防化服都是 ArmorItem/DiggerItem 的子类，神化因此把它们识别为",
                "regular armour and tools. Left alone, crafting one would attach affixes that scale off",
                "普通盔甲与工具。不处理的话，打造时会挂上按耐久缩放的词条，",
                "durability - on gear that runs on energy and has none.",
                "而这些装备走能量而非耐久。")
                .translation(KEY + "compat.mekanism_energy_gear")
                .push("mekanism_energy_gear");
            BLOCK_MEKANISM_ENERGY_GEAR_AFFIXES = b
                .comment(
                    "Keeps Mekanism's energy-powered gear out of the crafted-affix roll.",
                    "让热力本体的能量装备不参与打造词条。",
                    "",
                    "Implemented as an Apotheosis loot-category data map that pins each of these items to",
                    "实现方式是神化的战利品分类数据表，把这些物品钉成",
                    "'apotheosis:none'. 'none' is the same category vanilla hoes and other non-equipment",
                    "'apotheosis:none'。原版锄头等非装备物品也是这个分类，",
                    "already use, so the result is exactly 'Apotheosis ignores this item'.",
                    "所以效果就是「神化完全不处理这件物品」。",
                    "",
                    "Covers mekasuit_*, meka_tool, atomic_disassembler, electric_bow, jetpack(+_armored),",
                    "覆盖 mekasuit_*、meka_tool、atomic_disassembler、electric_bow、jetpack(+_armored)、",
                    "free_runners(+_armored), scuba_mask/tank, hazmat_*, hdpe_elytra, flamethrower, canteen.",
                    "free_runners(+_armored)、scuba_mask/tank、hazmat_*、hdpe_elytra、flamethrower、canteen。",
                    "",
                    "This is a data map, so it needs a data reload (F3+T or rejoining a world).",
                    "这是数据表，改动后需重载数据（F3+T 或重进存档）。")
                .translation(KEY + "compat.mekanism_energy_gear.block_mekanism_energy_gear_affixes")
                .define("block_mekanism_energy_gear_affixes", true);
            b.pop(); // mekanism_energy_gear
        }

        // ---- Ars Nouveau: the Wilden trio
        if (isModLoaded("ars_nouveau")) {
            b.comment(
                "Ars Nouveau integration.",
                "新生魔艺（Ars Nouveau）联动。")
                .translation(KEY + "compat.ars_nouveau")
                .push("ars_nouveau");
            ENABLE_ARS_NOUVEAU_INVADERS = b
                .comment(
                    "Adds Apotheosis Invaders for the Ars Nouveau Wilden mobs.",
                    "为新生魔艺的荒野（Wilden）实体添加神化入侵者。",
                    "",
                    "Covers wilden_hunter, wilden_stalker and wilden_guardian, which spawn inside",
                    "覆盖荒野猎手、荒野伏击者、荒野守卫者；它们只在荒野巢穴结构里刷，",
                    "the Wilden Den structures. wilden_boss is excluded - it is already a boss.",
                    "荒野奇美拉不在其中，它本身就是 BOSS。")
                .translation(KEY + "compat.ars_nouveau.enable_ars_nouveau_invaders")
                .define("enable_ars_nouveau_invaders", true);
            ENABLE_WILDEN_HUNTER_INVADER = entitySwitch(b, "ars_nouveau:wilden_hunter", "Wilden Hunter", "荒野猎手", true);
            ENABLE_WILDEN_STALKER_INVADER = entitySwitch(b, "ars_nouveau:wilden_stalker", "Wilden Stalker", "荒野伏击者", true);
            ENABLE_WILDEN_GUARDIAN_INVADER = entitySwitch(b, "ars_nouveau:wilden_guardian", "Wilden Guardian", "荒野守卫者", true);
            b.pop(); // ars_nouveau
        }

        // ---- AllTheModium: the Piglich structure boss
        if (isModLoaded("allthemodium")) {
            b.comment(
                "AllTheModium integration.",
                "AllTheModium 联动。")
                .translation(KEY + "compat.allthemodium")
                .push("allthemodium");
            ENABLE_ALLTHEMODIUM_INVADERS = b
                .comment(
                    "Adds an Apotheosis Invader for the AllTheModium Piglich.",
                    "为 AllTheModium 的猪巫妖添加神化入侵者。",
                    "",
                    "The Piglich spawns only inside the Ancient Pyramid structure, which the structure",
                    "猪巫妖只在远古金字塔结构中生成，该结构的 spawn_overrides 会替换所在群系的刷怪表；",
                    "overrides replace the biome spawn table with. Those spawns reach finalizeSpawn as",
                    "结构刷怪的 spawn type 是 NATURAL，因此走神化常规的入侵者流程。",
                    "NATURAL, so the normal invader path applies.",
                    "",
                    "The Piglich already has 9999 health, so the boss stages boost damage, armor and",
                    "猪巫妖本身已有 9999 生命，因此 boss 阶段只加强伤害、护甲与击退抗性，",
                    "knockback resistance rather than health.",
                    "不再增加生命。")
                .translation(KEY + "compat.allthemodium.enable_allthemodium_invaders")
                .define("enable_allthemodium_invaders", true);
            ENABLE_PIGLICH_BOSS = entitySwitch(b, "allthemodium:piglich", "Piglich", "猪巫妖", true);
            b.pop(); // allthemodium
        }

        // ---- Vanilla bosses: always available, no third-party mod needed
        {
            b.comment(
                "Vanilla boss integration.",
                "原版 BOSS 联动。",
                "",
                "Converts the warden, wither and ender dragon into Apotheosis bosses.",
                "把坚守者、凋灵、末影龙变成神化 BOSS。",
                "Always shown: it needs no third-party mod.")
                .translation(KEY + "compat.vanilla_bosses")
                .push("vanilla_bosses");
            ENABLE_VANILLA_BOSSES = b
                .comment(
                    "Master switch for the three vanilla boss conversions.",
                    "原版三大 BOSS 转换的总开关。",
                    "",
                    "Warden: a shrieker-summoned warden has a 10% chance (ascent..pinnacle).",
                    "坚守者：尖啸体召唤的坚守者有 10% 概率（攀升~顶点）。",
                    "Wither: a player-built wither has a 10% chance; overworld is ascent..summit,",
                    "凋灵：玩家搭建的凋灵有 10% 概率；主世界为攀升~高峰，",
                    "the nether is ascent..pinnacle, other dimensions stay vanilla.",
                    "下界为攀升~顶点，其它维度保持原版。",
                    "Ender dragon: only the world's first dragon, always the highest rarity available.",
                    "末影龙：仅本存档第一只，固定为可用的最高稀有度。",
                    "",
                    "None of the three appears in an Apotheosis boss spawner block.",
                    "这三者都不会出现在神化 BOSS 刷怪笼中。")
                .translation(KEY + "compat.vanilla_bosses.enable_vanilla_bosses")
                .define("enable_vanilla_bosses", true);
            ENABLE_WARDEN_BOSS = entitySwitch(b, "minecraft:warden", "Warden", "坚守者", true);
            ENABLE_WITHER_BOSS = entitySwitch(b, "minecraft:wither", "Wither", "凋灵", true);
            ENABLE_ENDER_DRAGON_BOSS = entitySwitch(b, "minecraft:ender_dragon", "Ender Dragon", "末影龙", true);
            b.pop(); // vanilla_bosses
        }

        b.pop(); // compat

        SPEC = b.build();
    }

    /**
     * Validates one {@code gem_weight_multipliers} entry.
     * <p>
     * Deliberately lenient about the gem id (it can only be checked against the registry at runtime) but strict about
     * the {@code =} separator and the number, so a typo is reported when the file is loaded instead of silently
     * becoming "no change".
     */
    private static boolean isValidWeightEntry(Object candidate) {
        if (!(candidate instanceof String entry)) {
            return false;
        }
        int eq = entry.lastIndexOf('=');
        if (eq <= 0 || eq == entry.length() - 1) {
            return false;
        }
        String key = entry.substring(0, eq).trim();
        String value = entry.substring(eq + 1).trim();
        if (key.isEmpty() || value.isEmpty()) {
            return false;
        }
        try {
            double v = Double.parseDouble(value);
            return !Double.isNaN(v) && v >= 0.0D;
        }
        catch (NumberFormatException ex) {
            return false;
        }
    }

    /**
     * Declares one per-entity invader switch and records it in {@link #ENTITY_INVADER_TOGGLES}.
     * <p>
     * The key suffix comes from {@code EntityInvaderEnabled.SUPPORTED} so the config key and the condition's lookup
     * can never disagree; this method asserts that the entity really is in that map.
     * <p>
     * Each switch sits inside the existing {@code [compat]} section rather than in a new sub-section, so the in-game
     * config screen keeps its two-level layout (Compat -> this list) instead of growing another nesting level for
     * what is a flat list of mobs.
     */
    /**
     * Null-safe mod presence test.
     * <p>
     * {@code ModList.get()} is populated by FML before a mod reads its config, so this is normally non-null.
     * It is still guarded: an NPE inside {@code Config.<clinit>} would abort the whole mod, whereas treating a
     * not-yet-available ModList as "mod absent" only hides a compat sub-section.
     */
    private static boolean isModLoaded(String modId) {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(modId);
    }
    private static BooleanValue entitySwitch(ModConfigSpec.Builder b, String entityId, String enName, String zhName, boolean def) {
        String key = com.tamamo.apotheosisexpansion.compat.EntityInvaderEnabled.SUPPORTED.get(entityId);
        if (key == null) {
            throw new IllegalStateException("No config key registered for invader entity " + entityId);
        }
        BooleanValue value = b
            .comment(
                "Whether " + enName + " (" + entityId + ") may spawn as an Apotheosis Invader.",
                "是否允许" + zhName + "（" + entityId + "）作为神化入侵者生成。",
                "",
                "This is a per-mob switch: it only affects this one entity, and the group toggle above",
                "这是单实体开关：只影响这一种生物，上方的分组总开关",
                "must also be on for it to matter.",
                "也必须开启才会生效。",
                "",
                "Turning it off removes the entry on the next data reload (F3+T or rejoining a world).",
                "关闭后该条目会在下次重载数据时移除（F3+T 或重进存档）。")
            .translation(KEY + "compat." + key)
            .define(key, def);
        ENTITY_INVADER_TOGGLES.put(entityId, value);
        return value;
    }

    private Config() {}

    // ---- Typed helpers ---------------------------------------------------
    // ConfigValue.get() is cached; NeoForge clears that cache on every reload, so reading these
    // at generation time always observes the current file contents.

    public static boolean enableAffixes() {
        return ENABLE_AFFIXES.get();
    }

    public static boolean enableSockets() {
        return ENABLE_SOCKETS.get();
    }

    public static double affixChance() {
        return AFFIX_CHANCE.get();
    }

    public static double socketChance() {
        return SOCKET_CHANCE.get();
    }

    public static boolean skipIfAlreadyAffixed() {
        return SKIP_IF_ALREADY_AFFIXED.get();
    }

    public static boolean allowNonEquipment() {
        return ALLOW_NON_EQUIPMENT.get();
    }

    public static boolean inheritOnRepairCombine() {
        return INHERIT_ON_REPAIR_COMBINE.get();
    }

    public static String minRarity() {
        return MIN_RARITY.get();
    }

    public static String maxRarity() {
        return MAX_RARITY.get();
    }

    public static boolean respectWorldTier() {
        return RESPECT_WORLD_TIER.get();
    }

    public static int socketMin() {
        return SOCKET_MIN.get();
    }

    public static int socketMax() {
        return SOCKET_MAX.get();
    }

    public static boolean enableWardenUnlock() {
        return ENABLE_WARDEN_UNLOCK.get();
    }

    public static boolean enableElderGuardianUnlock() {
        return ENABLE_ELDER_GUARDIAN_UNLOCK.get();
    }

    /**
     * Whether spawner runes describe their stat changes in their tooltip.
     * <p>
     * Null when Apothic Spawners is absent: its sub-section was never registered, so the feature is off rather than
     * crashing on a null config value.
     */
    public static boolean enableSpawnerRuneTooltips() {
        return ENABLE_SPAWNER_RUNE_TOOLTIPS != null && ENABLE_SPAWNER_RUNE_TOOLTIPS.get();
    }

    public static boolean enableRespawnHealthFix() {
        return ENABLE_RESPAWN_HEALTH_FIX.get();
    }

    public static boolean enableMobCapScaling() {
        return ENABLE_MOB_CAP_SCALING.get();
    }

    public static boolean mobCapCountAllTiers() {
        return MOB_CAP_COUNT_ALL_TIERS.get();
    }

    public static double mobCapBonusPerPlayer() {
        return MOB_CAP_BONUS_PER_PLAYER.get();
    }

    public static int mobCapMaxPlayers() {
        return MOB_CAP_MAX_PLAYERS.get();
    }

    public static boolean syncMobCapToHud() {
        return SYNC_MOB_CAP_TO_HUD.get();
    }

    public static boolean fakePlayersInheritWorldTier() {
        return FAKE_PLAYERS_INHERIT_WORLD_TIER.get();
    }

    public static boolean debugMobCap() {
        return DEBUG_MOB_CAP.get();
    }

    // ---- extra spawn lanes ------------------------------------------------

    public static boolean enableSpawnLanes() {
        return ENABLE_SPAWN_LANES.get();
    }

    public static double spawnLanesMinRegionalDifficulty() {
        return SPAWN_LANES_MIN_REGIONAL_DIFFICULTY.get();
    }

    public static boolean spawnLanesRealPlayersOnly() {
        return SPAWN_LANES_REAL_PLAYERS_ONLY.get();
    }

    public static boolean enableNamedPeacefulProtection() {
        return ENABLE_NAMED_PEACEFUL_PROTECTION.get();
    }

    /**
     * Gates {@code NamedPiglinConversionMixin}: a player-named piglin / piglin brute never zombifies outside
     * piglinSafe dimensions. Read inside the mixin on every tick the mob is asked whether it is converting, so the
     * toggle is hot-reloadable.
     */
    public static boolean enableNamedPiglinNoZombify() {
        return ENABLE_NAMED_PIGLIN_NO_ZOMBIFY.get();
    }

    public static boolean enableGemTweaks() {
        return ENABLE_GEM_TWEAKS.get();
    }

    /**
     * Gates the gem compendium pages in JEI / EMI.
     * <p>
     * Null when neither viewer is installed - the key was never registered - and treated as off then. Read by the
     * viewer plugins on every index rebuild, so toggling it takes effect on the next F3+T or world join.
     */
    public static boolean enableGemCompendium() {
        return ENABLE_GEM_COMPENDIUM != null && ENABLE_GEM_COMPENDIUM.get();
    }

    /**
     * Gates the Thermal Series invader entries.
     * <p>
     * Read from the data pack loading condition in {@code compat.ConfigEnabledCondition}, so it runs while data packs
     * are parsed - that is after the COMMON config has been loaded, which is why the value is safe to read there but
     * not from mod construction.
     */
    public static boolean enableThermalInvaders() {
        // Null when thermal is absent: its sub-section was not registered. Treat as off.
        return ENABLE_THERMAL_INVADERS != null && ENABLE_THERMAL_INVADERS.get();
    }

    /**
     * Gates the Mekanism Additions baby-mob invader entries.
     * <p>
     * Same mechanism as {@link #enableThermalInvaders()}: read from a data pack loading condition, so it needs a data
     * reload rather than only a config reload.
     */
    public static boolean enableMekanismInvaders() {
        // Null when mekanismadditions is absent: its sub-section was not registered. Treat as off.
        return ENABLE_MEKANISM_INVADERS != null && ENABLE_MEKANISM_INVADERS.get();
    }

    /**
     * Gates the Mekanism: Tools affix-loot entries.
     * <p>
     * Null when {@code mekanismtools} is absent, in which case the sub-section was never registered and the data
     * files' {@code mod_loaded} condition has already removed them.
     */
    public static boolean enableMekanismToolsAffixes() {
        return ENABLE_MEKANISM_TOOLS_AFFIXES != null && ENABLE_MEKANISM_TOOLS_AFFIXES.get();
    }

    /** Gates the Mekanism: Tools salvaging recipes. Null (treated as off) when the mod is absent. */
    public static boolean enableMekanismToolsSalvaging() {
        return ENABLE_MEKANISM_TOOLS_SALVAGING != null && ENABLE_MEKANISM_TOOLS_SALVAGING.get();
    }

    /** Gates the Mekanism: Tools invader gear sets. Null (treated as off) when the mod is absent. */
    public static boolean enableMekanismToolsGearSets() {
        return ENABLE_MEKANISM_TOOLS_GEAR_SETS != null && ENABLE_MEKANISM_TOOLS_GEAR_SETS.get();
    }

    /**
     * Whether Mekanism's energy-powered gear is kept out of the crafted-affix roll.
     * <p>
     * Defaults to <b>true</b> when the key is missing: the sub-section only exists when {@code mekanism} is loaded, and
     * a missing key there means an old config file. Excluding the gear is the safer of the two behaviours (attaching
     * durability-scaled affixes to an item that has no durability is a visible bug), so absence fails closed rather
     * than open.
     */
    public static boolean blockMekanismEnergyGearAffixes() {
        return BLOCK_MEKANISM_ENERGY_GEAR_AFFIXES == null || BLOCK_MEKANISM_ENERGY_GEAR_AFFIXES.get();
    }

    /**
     * Gates the Ars Nouveau Wilden invader entries. Same mechanism as the other compat toggles: read from a data pack
     * loading condition, so it needs a data reload rather than only a config reload.
     */
    public static boolean enableArsNouveauInvaders() {
        // Null when ars_nouveau is absent: its sub-section was not registered. Treat as off.
        return ENABLE_ARS_NOUVEAU_INVADERS != null && ENABLE_ARS_NOUVEAU_INVADERS.get();
    }

    /** Master switch for the warden / wither / ender dragon conversions. */
    /** Null when allthemodium is absent (its sub-section was not registered); treat as off. */
    public static boolean enableAllthemodiumInvaders() {
        return ENABLE_ALLTHEMODIUM_INVADERS != null && ENABLE_ALLTHEMODIUM_INVADERS.get();
    }

    public static boolean enableVanillaBosses() {
        return ENABLE_VANILLA_BOSSES != null && ENABLE_VANILLA_BOSSES.get();
    }

    /**
     * Looks up a per-entity invader switch by entity id.
     * <p>
     * Unknown ids return {@code true} on purpose: a data file naming an entity this build does not know about (a pack
     * downgrading, or a hand-written entry) should keep working rather than silently vanishing because its switch is
     * missing. The group toggles still gate everything.
     */
    public static boolean isEntityInvaderEnabled(String entityId) {
        BooleanValue value = ENTITY_INVADER_TOGGLES.get(entityId);
        return value == null || value.get();
    }

    /** Exposed for the validation pass: every entity id that has a switch. */
    public static java.util.Set<String> invaderEntityIds() {
        return ENTITY_INVADER_TOGGLES.keySet();
    }

    public static double gemDropChanceMultiplier() {
        return GEM_DROP_CHANCE_MULTIPLIER.get();
    }

    /**
     * Parses {@code gem_weight_multipliers} into a gem-id to multiplier map.
     * <p>
     * Entries are {@code "<id>=<multiplier>"}. The last {@code =} is the separator, so an id containing {@code =}
     * still parses. Malformed entries are skipped; the config validator already rejects them, so this only matters for
     * a file edited while the game was closed.
     */
    public static Map<String, Double> gemWeightMultipliers() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String entry : GEM_WEIGHT_MULTIPLIERS.get()) {
            if (entry == null) {
                continue;
            }
            int eq = entry.lastIndexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) {
                continue;
            }
            String key = entry.substring(0, eq).trim();
            String value = entry.substring(eq + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            try {
                double v = Double.parseDouble(value);
                if (!Double.isNaN(v) && v >= 0.0D) {
                    out.put(key, v);
                }
            }
            catch (NumberFormatException ignored) {
                // Validator already rejected this; nothing useful to report here.
            }
        }
        return out;
    }

    /**
     * Appends the given canonical gem ids to {@code gem_weight_multipliers} with a multiplier of 1.0 and writes the
     * file back.
     * <p>
     * This is what lets a single config file list every gem even though {@link ModConfigSpec} is built before data
     * packs load: the ids only become known once a world is running, so the list is topped up then. Existing entries
     * (and their order) are preserved, so a player's edits are never lost.
     * <p>
     * The caller is responsible for deciding which ids are genuinely unrepresented — see
     * {@code GemTweakApplier#missingGemIds}, which treats a hand-written short form as already covering its gem.
     *
     * @param canonicalIds ids to append, already filtered
     * @return true when the file was modified
     */
    public static boolean appendGemWeightEntries(Collection<String> canonicalIds) {
        if (canonicalIds.isEmpty()) {
            return false;
        }
        List<String> merged = new ArrayList<>(GEM_WEIGHT_MULTIPLIERS.get());
        for (String id : canonicalIds) {
            merged.add(id + "=1.0");
        }
        GEM_WEIGHT_MULTIPLIERS.set(merged);
        SPEC.save();
        return true;
    }

    /** Exposed for the validation pass; keeps the list of known rarity ids in one place. */
    public static List<String> rarityKeys() {
        return List.of("apotheosis:common", "apotheosis:uncommon", "apotheosis:rare", "apotheosis:epic", "apotheosis:mythic");
    }
}
