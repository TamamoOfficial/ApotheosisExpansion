// Validates the data files' condition list through NeoForge's real registry-dispatch codec.
// The Condition registry is empty outside a running game (FML bootstrap normally fills it), so the
// two entries these files need are registered here exactly the way DeferredRegister would.
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;

import com.tamamo.apotheosisexpansion.compat.ConfigConditions;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ConditionCodecCheck {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Registry<MapCodec<? extends ICondition>> reg =
            (Registry<MapCodec<? extends ICondition>>) (Registry<?>) NeoForgeRegistries.CONDITION_SERIALIZERS;

        // Stand in for what DeferredRegister does in a real launch.
        register(reg, "neoforge", "mod_loaded", ModLoadedCondition.CODEC);
        register(reg, "apotheosis_expansion", "thermal_invaders_enabled", ConfigConditions.THERMAL_INVADERS);
        register(reg, "apotheosis_expansion", "mekanism_invaders_enabled", ConfigConditions.MEKANISM_INVADERS);
        register(reg, "apotheosis_expansion", "ars_nouveau_invaders_enabled", ConfigConditions.ARS_NOUVEAU_INVADERS);
        register(reg, "apotheosis_expansion", "entity_invader_enabled", com.tamamo.apotheosisexpansion.compat.EntityInvaderEnabled.CODEC);
        System.out.println("registry seeded: " + reg.keySet());

        @SuppressWarnings("unchecked")
        RegistryAccess.Frozen access = RegistryAccess.fromRegistryOfRegistries(
            (Registry<? extends Registry<?>>) (Registry<?>) reg);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);

        int failures = 0;
        for (String file : args) {
            System.out.println("== " + Path.of(file).getFileName());
            JsonElement el = JsonParser.parseString(Files.readString(Path.of(file)));
            JsonElement conds = el.getAsJsonObject().get("neoforge:conditions");

            var decoded = ICondition.LIST_CODEC.parse(ops, conds);
            if (decoded.error().isPresent()) {
                System.out.println("  FAIL decode: " + decoded.error().get().message());
                failures++;
                continue;
            }
            List<ICondition> list = decoded.getOrThrow((String m) -> new RuntimeException(m));
            for (ICondition c : list) {
                System.out.println("  " + c.getClass().getSimpleName() + " -> " + c);
            }
            String expectedMod = file.contains("mekanism") ? "mekanismadditions" : file.contains("ars_nouveau") ? "ars_nouveau" : "thermal";
            if (!(list.get(0) instanceof ModLoadedCondition mlc) || !expectedMod.equals(mlc.modid())) {
                System.out.println("  FAIL first condition is not mod_loaded(thermal)");
                failures++;
            }
            if (list.size() < 3 || !(list.get(list.size() - 1) instanceof com.tamamo.apotheosisexpansion.compat.EntityInvaderEnabled)) {
                System.out.println("  FAIL per-entity condition did not resolve");
                failures++;
            }

            var back = ICondition.LIST_CODEC.encodeStart(ops, list);
            if (back.error().isPresent()) {
                System.out.println("  FAIL encode: " + back.error().get().message());
                failures++;
                continue;
            }
            String encoded = back.getOrThrow((String m) -> new RuntimeException(m)).toString();
            String expectedCfg = file.contains("mekanism") ? "apotheosis_expansion:mekanism_invaders_enabled" : file.contains("ars_nouveau") ? "apotheosis_expansion:ars_nouveau_invaders_enabled" : "apotheosis_expansion:thermal_invaders_enabled";
            for (String expected : new String[] { "neoforge:mod_loaded", expectedCfg }) {
                if (!encoded.contains("\"" + expected + "\"")) {
                    System.out.println("  FAIL round-trip lost " + expected + " in " + encoded);
                    failures++;
                }
            }

            try {
                System.out.println("  config gate evaluates to: " + list.get(1).test(ICondition.IContext.EMPTY));
            }
            catch (Throwable t) {
                System.out.println("  FAIL condition threw " + t);
                failures++;
            }
        }

        System.out.println(failures == 0 ? "\nCONDITION CODEC OK" : "\n" + failures + " FAILURE(S)");
        System.exit(failures == 0 ? 0 : 1);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void register(Registry<MapCodec<? extends ICondition>> reg, String ns, String path,
        MapCodec<? extends ICondition> codec) {
        ((WritableRegistry) reg).register(
            ResourceKey.create((ResourceKey) reg.key(), ResourceLocation.fromNamespaceAndPath(ns, path)),
            codec, RegistrationInfo.BUILT_IN);
    }
}
