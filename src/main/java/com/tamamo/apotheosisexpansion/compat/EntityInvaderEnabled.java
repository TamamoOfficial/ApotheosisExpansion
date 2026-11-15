package com.tamamo.apotheosisexpansion.compat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.tamamo.apotheosisexpansion.Config;

import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Per-entity invader switch, addressed by entity id inside the data file.
 * <p>
 * <b>Why one parameterised condition instead of one condition type per mob.</b> The Condition registry is a static
 * NeoForge registry, so a type-per-entity design would register eleven near-identical map codecs and add a new
 * registry entry for every mob a future compat wave covers. Taking the entity id as a field keeps the registry at one
 * entry while still letting each JSON declare which mob it belongs to:
 *
 * <pre>
 *   { "type": "apotheosis_expansion:entity_invader_enabled", "entity": "thermal:basalz" }
 * </pre>
 *
 * Several data files can share the same entity id - e.g. the three Basalz files (peaks / caves / nether) all declare
 * {@code thermal:basalz}, so one switch governs the whole mob across every biome it covers, which is what a player
 * expects from a toggle labelled with that mob's name.
 * <p>
 * <b>Unknown ids fail open.</b> If a data file names an entity this build has no switch for (a pack downgrading, or a
 * hand-written file), the condition returns true rather than silently deleting the entry. A missing switch should
 * never disable content; the group-level toggles remain the safety net.
 */
public record EntityInvaderEnabled(String entity) implements ICondition {

    /** Field name used in the JSON. Kept as a constant so the codec and the docs cannot drift apart. */
    public static final String FIELD = "entity";

    public static final MapCodec<EntityInvaderEnabled> CODEC = RecordCodecBuilder.mapCodec(inst -> inst
        .group(Codec.STRING.fieldOf(FIELD).forGetter(EntityInvaderEnabled::entity))
        .apply(inst, EntityInvaderEnabled::new));

    @Override
    public boolean test(IContext context) {
        // Reading a config value before the COMMON config is loaded throws; a data pack is always parsed after that,
        // but guard anyway so a bad load order degrades to "enabled" instead of aborting the whole data pack.
        try {
            return Config.isEntityInvaderEnabled(this.entity);
        }
        catch (IllegalStateException ex) {
            return true;
        }
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "apotheosis_expansion:entity_invader_enabled(" + this.entity + ")";
    }

    /**
     * Every entity this mod can add an invader for, mapped to the config key suffix that switches it.
     * <p>
     * The map is the single source of truth shared by {@link Config} (which builds the {@code BooleanValue}s) and the
     * condition above (which looks them up). Order is insertion order so the generated TOML reads thermal -> mek ->
     * ars, matching the README.
     */
    public static final Map<String, String> SUPPORTED = createSupported();

    private static Map<String, String> createSupported() {
        Map<String, String> m = new LinkedHashMap<>();
        // Thermal Series elementals
        m.put("thermal:basalz", "enable_basalz_invader");
        m.put("thermal:blizz", "enable_blizz_invader");
        m.put("thermal:blitz", "enable_blitz_invader");
        // Mekanism Additions babies
        m.put("mekanismadditions:baby_enderman", "enable_baby_enderman_invader");
        m.put("mekanismadditions:baby_skeleton", "enable_baby_skeleton_invader");
        m.put("mekanismadditions:baby_stray", "enable_baby_stray_invader");
        m.put("mekanismadditions:baby_bogged", "enable_baby_bogged_invader");
        m.put("mekanismadditions:baby_wither_skeleton", "enable_baby_wither_skeleton_invader");
        // Ars Nouveau Wilden
        m.put("ars_nouveau:wilden_hunter", "enable_wilden_hunter_invader");
        m.put("ars_nouveau:wilden_stalker", "enable_wilden_stalker_invader");
        m.put("ars_nouveau:wilden_guardian", "enable_wilden_guardian_invader");
        // AllTheModium
        m.put("allthemodium:piglich", "enable_piglich_boss");
        // Vanilla bosses - converted by event hooks, not by data-driven spawn selection
        m.put("minecraft:warden", "enable_warden_boss");
        m.put("minecraft:wither", "enable_wither_boss");
        m.put("minecraft:ender_dragon", "enable_ender_dragon_boss");
        // unmodifiable but order-preserving: Map.copyOf does not guarantee iteration order
        return Collections.unmodifiableMap(m);
    }
}
