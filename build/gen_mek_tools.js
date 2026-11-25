// Generates the Mekanism: Tools integration data: affix loot entries, salvaging recipes and invader gear sets.
//
// Why a generator rather than 90 hand-written files: the three data sets are all driven by the same
// (material x item-kind) matrix. Writing them by hand makes it easy for one file to disagree with the
// others - a paxel present in the loot pool but missing from the salvaging recipes, or an armour set in
// one world tier but not the next. The matrix below is the single source of truth, and
// build/validate_mek_tools.js re-checks the generated output against the real Mekanism: Tools jar.
//
// Design decisions (user-confirmed):
//   Q1 : only mekanismtools (material gear), NOT Mekanism's own energy gear for loot/salvage
//   Q2 : Mekanism's energy gear is blocked from affixes
//   Q3 : weight 2 per affix-loot entry (light dilution of the existing pools)
//   Q4 : hoes are skipped (Apotheosis has no hoe category at all - vanilla hoes are 'none' too)
//   Q5 : invaders may wear the Mekanism: Tools sets
//
// Output paths are relative to the repository root.
//
// Usage:  node build/gen_mek_tools.js [--check]
//         --check verifies the files on disk match what the generator would write, without writing.

const fs = require('fs');
const path = require('path');
const { ROOT, rel } = require('./lib_paths');

// --------------------------------------------------------------------------- material matrix

/**
 * Every material Mekanism: Tools ships, in progression order.
 *
 * `tiers` are the WorldTier names the affix-loot entry is registered for. The mapping follows the material's
 * in-game power level, benchmarked against the vanilla entries Apotheosis already ships:
 *   haven    - leather / chainmail / stone / gold
 *   frontier - iron
 *   ascent   - diamond
 *   summit   - diamond+, netherite-adjacent
 *   pinnacle - netherite top end
 *
 * `salvage` is the item the set breaks down into, mirroring what the set is crafted from (checked against
 * mekanismtools' own recipe files in build/validate_mek_tools.js).
 */
const MATERIALS = [
    {
        id: 'lapis_lazuli',
        name: 'Lapis Lazuli',
        tiers: ['haven', 'frontier'],
        salvage: 'minecraft:lapis_lazuli',
    },
    {
        id: 'bronze',
        name: 'Bronze',
        tiers: ['haven', 'frontier', 'ascent'],
        salvage: 'mekanism:ingot_bronze',
    },
    {
        id: 'steel',
        name: 'Steel',
        tiers: ['frontier', 'ascent', 'summit'],
        salvage: 'mekanism:ingot_steel',
    },
    {
        id: 'osmium',
        name: 'Osmium',
        tiers: ['ascent', 'summit', 'pinnacle'],
        salvage: 'mekanism:ingot_osmium',
    },
    {
        id: 'refined_glowstone',
        name: 'Refined Glowstone',
        tiers: ['ascent', 'summit', 'pinnacle'],
        salvage: 'mekanism:ingot_refined_glowstone',
    },
    {
        id: 'refined_obsidian',
        name: 'Refined Obsidian',
        tiers: ['summit', 'pinnacle'],
        salvage: 'mekanism:ingot_refined_obsidian',
    },
];

/**
 * The vanilla-material paxels. They are the only mekanismtools items not covered by MATERIALS: they reuse
 * vanilla metals, so they take their tiers from the matching vanilla entry and break down into the vanilla
 * material (not into a Mekanism ingot, which does not exist for them).
 */
const VANILLA_PAXELS = [
    { id: 'wood', tiers: ['haven'], salvage: 'minecraft:oak_planks' },
    { id: 'stone', tiers: ['haven'], salvage: 'minecraft:cobblestone' },
    { id: 'gold', tiers: ['haven', 'frontier'], salvage: 'minecraft:gold_ingot' },
    { id: 'iron', tiers: ['frontier', 'ascent'], salvage: 'minecraft:iron_ingot' },
    { id: 'diamond', tiers: ['ascent', 'summit'], salvage: 'minecraft:diamond' },
    { id: 'netherite', tiers: ['summit', 'pinnacle'], salvage: 'minecraft:netherite_scrap' },
];

/**
 * The item kinds every material ships, and how each one is salvaged.
 *
 * `salvageMin/Max` follow the yields Apotheosis already ships for vanilla (see
 * data/apotheosis/recipe/salvaging/other/): armour 1-3, tools 0-1. Paxels and shields are not covered by a
 * vanilla recipe, so they are set one step above tools - they cost three and four ingots respectively.
 *
 * `category` is the slot family used when building the gear sets; it is NOT an Apotheosis LootCategory.
 */
const KINDS = [
    { id: 'helmet', slot: 'helmets', inGearSet: true },
    { id: 'chestplate', slot: 'chestplates', inGearSet: true },
    { id: 'leggings', slot: 'leggings', inGearSet: true },
    { id: 'boots', slot: 'boots', inGearSet: true },
    { id: 'sword', slot: 'mainhands', inGearSet: true },
    { id: 'pickaxe', slot: 'mainhands', inGearSet: true },
    { id: 'axe', slot: 'mainhands', inGearSet: true },
    { id: 'shovel', slot: 'mainhands', inGearSet: true },
    // 'hoe' is deliberately absent: Apotheosis has no hoe loot category (vanilla hoes are 'none'), so a hoe
    // entry would be dropped by AffixLootRegistry's "Items without a valid loot category are not permitted"
    // precondition. Salvaging a hoe works without a category, but skipping it keeps the whole set consistent.
    { id: 'paxel', slot: 'mainhands', inGearSet: true },
    // Shields are lootable and salvageable but never worn: Apotheosis' own melee gear sets have no offhand
    // entries at all, and a mob cannot actually block, so a shield in hand would be pure clutter.
    { id: 'shield', slot: 'offhands', inGearSet: false },
];

/**
 * The salvaging recipe groups, mirroring how Apotheosis itself groups the vanilla recipes.
 *
 * Upstream ships `iron_armor` (all four pieces in one `input` array), `iron_tools` (sword/pickaxe/axe/shovel/hoe)
 * and so on - one recipe per (material family, item kind), not one per item. The grouped form is what the
 * frozen reference in this conversation's screenshot shows, so this mod follows the same shape instead of
 * emitting 66 single-item files.
 *
 * The shields get their own group because they are not part of the "tools" family visually or in cost: a
 * Mekanism shield costs four ingots plus a vanilla shield, so it yields one step above the tools.
 */
const SALVAGE_GROUPS = [
    {
        id: 'armor',
        kinds: ['helmet', 'chestplate', 'leggings', 'boots'],
        min: 1,
        max: 3,
    },
    {
        id: 'tools',
        kinds: ['sword', 'pickaxe', 'axe', 'shovel', 'paxel'],
        min: 0,
        max: 1,
    },
    {
        id: 'shield',
        kinds: ['shield'],
        min: 1,
        max: 2,
    },
];

/** User-confirmed choice Q3: light dilution, 2 per entry. */
const AFFIX_WEIGHT = 2;

/**
 * The weight every gear set carries inside its world tier.
 *
 * Existing sets sit at 5-80 per tier (see data/apotheosis/gear_sets/). 30 keeps a Mekanism set a real but not
 * dominant option: an invader still picks from the tier's whole pool, so the result is variety rather than
 * every boss wearing osmium.
 */
const GEAR_SET_WEIGHT = 30;

/** `quality` on the gear set: scales the rarity/affix budget of the roll, same meaning as in the vanilla sets. */
const GEAR_SET_QUALITY = 1.0;

// --------------------------------------------------------------------------- conditions

/** Present on every file: vanishing when the mod is absent is the whole point of a compat pack. */
const MOD_LOADED = { type: 'neoforge:mod_loaded', modid: 'mekanismtools' };

const condAffixes = () => [
    MOD_LOADED,
    { type: 'apotheosis_expansion:mekanism_tools_affixes_enabled' },
];

const condSalvaging = () => [
    MOD_LOADED,
    { type: 'apotheosis_expansion:mekanism_tools_salvaging_enabled' },
];

const condGearSets = () => [
    MOD_LOADED,
    { type: 'apotheosis_expansion:mekanism_tools_gear_sets_enabled' },
];

// --------------------------------------------------------------------------- affix loot entries

/**
 * One file per item: Apotheosis' AffixLootEntry codec holds exactly one `stack`, so several items cannot share
 * an entry. Files are grouped into a per-material directory purely for readability.
 */
function affixEntry(itemId, tiers) {
    const weights = {};
    for (const tier of tiers) {
        weights[tier] = { weight: AFFIX_WEIGHT };
    }
    return {
        'neoforge:conditions': condAffixes(),
        type: 'apotheosis:affix_loot_entry',
        stack: { count: 1, id: itemId },
        weights,
    };
}

// --------------------------------------------------------------------------- salvaging recipes

/**
 * Builds one `apotheosis:salvaging` recipe with several items in a single `input` array.
 *
 * This is exactly the shape Apotheosis ships for vanilla: `iron_armor.json` lists all four iron armour pieces in
 * one `input` array and yields 1-3 iron ingots; `iron_tools.json` lists all five iron tools and yields 0-1.
 * Grouping by category is what lets the armour group return 1-3 while the tool group returns 0-1, without
 * needing one file per item.
 *
 * The items must be mutually exclusive across files - Apotheosis returns EVERY matching recipe and sums them,
 * so an item listed in two recipes would double its yield. build/validate_mek_tools.js enforces that.
 */
function salvagingRecipe(itemIds, salvageId, min, max) {
    return {
        'neoforge:conditions': condSalvaging(),
        type: 'apotheosis:salvaging',
        input: itemIds.map(id => ({ item: id })),
        outputs: [
            {
                max_count: max,
                min_count: min,
                stack: { count: 1, id: salvageId },
            },
        ],
    };
}

// --------------------------------------------------------------------------- gear sets

/**
 * One gear set per (material, world tier).
 *
 * A separate file per tier rather than one file with all tiers: GearSet has no tier field at all - it is a
 * single weighted entry in a flat registry, and the tier is expressed by the directory it lives in
 * (`gear_sets/<tier>/...`), which is how Apotheosis' own files do it.
 */
function gearSet(material, tier, kinds) {
    const set = {
        'neoforge:conditions': condGearSets(),
        type: 'placebo:gear_set',
        weight: GEAR_SET_WEIGHT,
        quality: GEAR_SET_QUALITY,
    };
    for (const slot of ['mainhands', 'offhands', 'boots', 'leggings', 'chestplates', 'helmets']) {
        const entries = kinds
            .filter(k => k.inGearSet && k.slot === slot)
            .map(k => ({
                stack: { count: 1, id: `mekanismtools:${material.id}_${k.id}` },
                weight: 10,
            }));
        // Explicit [] rather than leaving the key out: GearSet#apply draws one entry per slot from
        // getPotentials, and an absent list means the mob silently keeps whatever it already had.
        set[slot] = entries;
    }
    return set;
}

// --------------------------------------------------------------------------- the tag wiring

/**
 * Tags the gear sets are discoverable under.
 *
 * Apotheosis' invaders name their gear by tag (`"#haven_melee"`), and a GearSet earns a tag by listing it in its
 * own `tags` array. Reusing the existing `<tier>_melee` names - rather than inventing `mekanism_melee` and
 * editing 60+ invader files - is what makes every mob pick these sets up without touching a single invader
 * entry. Those invader files are already covered by other validators, so a new tag would mean a much larger and
 * riskier diff for exactly the same result.
 *
 * Melee only, deliberately. The `<tier>_ranged` pools are bow/crossbow sets, and Mekanism: Tools ships no bow
 * (the electric bow lives in the main Mekanism mod, which is out of scope here), so a ranged tag would make a
 * ranged invader draw a melee weapon it cannot use at range.
 */
function gearSetTags(tier) {
    return [`${tier}_melee`];
}

// --------------------------------------------------------------------------- build the file map

/** Relative output path -> file contents. */
const files = {};

const DATA = 'src/main/resources/data';

for (const material of MATERIALS) {
    const kinds = KINDS;

    // ---- affix loot entries: one per item
    for (const kind of kinds) {
        const itemId = `mekanismtools:${material.id}_${kind.id}`;
        files[`${DATA}/apotheosis/affix_loot_entries/mekanism_tools/${material.id}/${kind.id}.json`] =
            affixEntry(itemId, material.tiers);
    }

    // ---- salvaging: one recipe per category group, exactly like Apotheosis' own iron_armor / iron_tools
    for (const group of SALVAGE_GROUPS) {
        const itemIds = group.kinds.map(kindId => `mekanismtools:${material.id}_${kindId}`);
        files[`${DATA}/apotheosis/recipe/salvaging/mekanism_tools/${material.id}/${group.id}.json`] =
            salvagingRecipe(itemIds, material.salvage, group.min, group.max);
    }

    // ---- gear sets: one per tier the material is valid in
    for (const tier of material.tiers) {
        const set = gearSet(material, tier, kinds);
        set.tags = gearSetTags(tier);
        files[`${DATA}/apotheosis/gear_sets/${tier}/mekanism_tools/${material.id}.json`] = set;
    }
}

// ---- vanilla-material paxels: loot pool + salvaging, no gear sets (they are vanilla materials, and the
// existing vanilla sets already cover those slots)
//
// One recipe each, because none of them can share an `input` array with another: every vanilla-material paxel
// returns its own metal (oak_planks / cobblestone / gold / iron / diamond / netherite scrap), unlike the
// Mekanism materials where all armour pieces of one material break down into the same ingot.
for (const paxel of VANILLA_PAXELS) {
    files[`${DATA}/apotheosis/affix_loot_entries/mekanism_tools/vanilla_paxels/${paxel.id}.json`] =
        affixEntry(`mekanismtools:${paxel.id}_paxel`, paxel.tiers);

    files[`${DATA}/apotheosis/recipe/salvaging/mekanism_tools/vanilla_paxels/${paxel.id}.json`] =
        salvagingRecipe([`mekanismtools:${paxel.id}_paxel`], paxel.salvage, 0, 1);
}

// --------------------------------------------------------------------------- energy gear block

/**
 * Mekanism's own energy-powered gear, pinned to {@code apotheosis:none}.
 *
 * These are all {@code ArmorItem}/{@code DiggerItem}/{@code BowItem}/{@code ShieldItem} subclasses, so
 * {@code LootCategory.forItem} resolves them to ordinary armour/tool categories and Apotheosis happily attaches
 * affixes that scale off durability - except these items run on energy and have no durability at all. Pinning
 * them to {@code none} is the same mechanism Apotheosis itself uses for `minecraft:shulker_shell`.
 *
 * Listing them by id rather than by class means this file has no compile-time dependency on Mekanism, and a
 * future gear item is a one-line addition here.
 */
const ENERGY_GEAR = [
    'mekanism:mekasuit_helmet',
    'mekanism:mekasuit_bodyarmor',
    'mekanism:mekasuit_pants',
    'mekanism:mekasuit_boots',
    'mekanism:meka_tool',
    'mekanism:atomic_disassembler',
    'mekanism:electric_bow',
    'mekanism:jetpack',
    'mekanism:jetpack_armored',
    'mekanism:free_runners',
    'mekanism:free_runners_armored',
    'mekanism:scuba_mask',
    'mekanism:scuba_tank',
    'mekanism:hazmat_mask',
    'mekanism:hazmat_gown',
    'mekanism:hazmat_pants',
    'mekanism:hazmat_boots',
    'mekanism:hdpe_elytra',
    'mekanism:flamethrower',
    'mekanism:canteen',
];

/**
 * NeoForge data maps live at {@code <pack>/data_maps/<registry namespace>/<registry path>/<map id>.json}.
 *
 * Two details matter here and both are easy to get wrong:
 *
 * 1. <b>The registry is {@code minecraft:item}, not Apotheosis' {@code loot_category} registry.</b> A data map
 *    attaches values to entries of a registry - here, to items - and the loot category is the value being
 *    attached. So the folder is {@code data_maps/item/}, which is exactly where Apotheosis ships its own file.
 *
 * 2. <b>The file id must be {@code apotheosis:loot_category_overrides}.</b> DataMapLoader resolves the data map
 *    type by the file's resource location, so a file named in this mod's namespace would be reported as "data
 *    map file for non-existent data map type" and skipped. The file therefore has to live under the
 *    {@code apotheosis} namespace.
 *
 * That does <i>not</i> overwrite Apotheosis' content: NeoForge reads the whole resource stack
 * ({@code listMatchingResourceStacks}) and merges every file, and this data map has no custom merger, so the
 * later value wins only for the <i>same item key</i>. Apotheosis' {@code minecraft:iron_sword} and
 * {@code minecraft:shulker_shell} entries therefore survive untouched. build/validate_mek_tools.js asserts the
 * two key sets stay disjoint, so this assumption cannot rot silently.
 */
const energyGearBlock = {
    values: Object.fromEntries(ENERGY_GEAR.map(id => [
        id,
        {
            'neoforge:conditions': [
                { type: 'neoforge:mod_loaded', modid: 'mekanism' },
                { type: 'apotheosis_expansion:mekanism_energy_gear_blocked' },
            ],
            value: 'apotheosis:none',
        },
    ])),
};

files[`${DATA}/apotheosis/data_maps/item/loot_category_overrides.json`] = energyGearBlock;

// --------------------------------------------------------------------------- write / check

const serialise = (value) => JSON.stringify(value, null, 4) + '\n';

function main() {
    const check = process.argv.includes('--check');
    let written = 0;
    let stale = 0;

    for (const [relPath, value] of Object.entries(files).sort(([a], [b]) => a.localeCompare(b))) {
        const abs = path.join(ROOT, relPath);
        const text = serialise(value);
        if (check) {
            const current = fs.existsSync(abs) ? fs.readFileSync(abs, 'utf8') : null;
            if (current !== text) {
                console.log('  STALE ' + relPath);
                stale++;
            }
            continue;
        }
        fs.mkdirSync(path.dirname(abs), { recursive: true });
        fs.writeFileSync(abs, text);
        written++;
    }

    if (check) {
        const total = Object.keys(files).length;
        console.log(stale === 0
            ? `  OK    all ${total} generated files are up to date`
            : `  FAIL  ${stale}/${total} files differ from the generator`);
        process.exit(stale === 0 ? 0 : 1);
    }

    console.log(`  wrote ${written} files`);
    const counts = { affix: 0, salvage: 0, gear: 0 };
    for (const p of Object.keys(files)) {
        if (p.includes('/affix_loot_entries/')) counts.affix++;
        else if (p.includes('/recipe/salvaging/')) counts.salvage++;
        else if (p.includes('/gear_sets/')) counts.gear++;
    }
    console.log(`    affix loot entries : ${counts.affix}`);
    console.log(`    salvaging recipes  : ${counts.salvage}`);
    console.log(`    gear sets          : ${counts.gear}`);
}

// Only run when invoked directly (`node build/gen_mek_tools.js`). validate_mek_tools.js requires this module to
// read the MATERIALS/KINDS matrix, and an unconditional main() would make that require WRITE the data files -
// which would silently turn the validator's own "is the output current?" check into a tautology.
if (require.main === module) {
    main();
}

module.exports = { MATERIALS, VANILLA_PAXELS, KINDS, ENERGY_GEAR };
