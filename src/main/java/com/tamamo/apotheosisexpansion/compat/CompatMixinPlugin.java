package com.tamamo.apotheosisexpansion.compat;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.neoforged.fml.ModList;

/**
 * Applies the third-party compat mixins only when the target mod is actually installed.
 *
 * <h2>Why a plugin instead of {@code required: false}</h2>
 * These mixins target classes that live in <em>other</em> mods (MiniHUD, Servux, carpet). If the target jar is absent,
 * Mixin would fail to resolve the class. A config-level {@code "required": false} silences the config as a whole but
 * still logs per-mixin errors, and "mixin apply failed" is exactly what a user would report as this mod's bug.
 * {@link IMixinConfigPlugin#shouldApplyMixin} is consulted before any bytecode work, so a missing mod means the mixin
 * is never attempted - no warning, no error, no cost.
 *
 * <h2>Why presence is detected two ways</h2>
 * {@code ModList.isLoaded} is the documented answer, but it is a NeoForge service that is populated at a specific
 * point during loading. This plugin runs during Mixin config preparation, which is early, and it also runs on the
 * client and on a dedicated server for two different mod sets. Relying on it alone produced a silent failure once: the
 * plugin returned {@code false} for every target and not a single compat mixin ever applied.
 * <p>
 * So presence is decided by whichever signal is available:
 * <ol>
 * <li><b>{@code ModList.isLoaded}</b> - authoritative once FML has scanned the mods.</li>
 * <li><b>Class lookup on the target class</b> - the real question this plugin is asking is "can Mixin resolve this
 * class", and asking the class loader directly is exactly that. It works before the mod list is usable, and it does
 * not depend on guessing the mod id.</li>
 * </ol>
 * A mixin is applied when <em>either</em> signal says the target exists; applying a mixin whose class is genuinely
 * missing produces a clear "target class not found" error, which is far easier to diagnose than a mixin that silently
 * never ran.
 *
 * <h2>Fail-open on unknown mixins</h2>
 * A mixin not listed in {@link #TARGETS} is applied normally, so adding one to the json without updating this class
 * cannot silently disable it.
 */
public final class CompatMixinPlugin implements IMixinConfigPlugin {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /**
     * One gated mixin: the mixin's simple class name, the mod id that provides its target, and the target class the
     * mixin is written against (used for the class-loader check).
     */
    private record Entry(String mixinClass, String modId, String targetClass) {}

    private static final List<Entry> TARGETS = List.of(
        new Entry("MiniHudMobCapMixin", "minihud", "fi.dy.masa.minihud.data.MobCapDataHandler"),
        new Entry("ServuxMobCapMixin", "servux", "fi.dy.masa.servux.loggers.DataLoggerMobCaps"),
        new Entry("CarpetPlayerCommandMixin", "carpet", "carpet.commands.PlayerCommand"));

    @Override
    public void onLoad(String mixinPackage) {
        // Nothing to prepare: the decision is a lookup made per mixin below.
    }

    @Override
    public String getRefMapperConfig() {
        // This mod ships no refmap; the Apotheosis family and these targets are never obfuscated.
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);

        for (Entry entry : TARGETS) {
            if (!entry.mixinClass().equals(simpleName)) {
                continue;
            }

            if (isModLoaded(entry.modId())) {
                LOGGER.info("[ApotheosisExpansion] Mixin {} enabled: mod '{}' is loaded",
                    simpleName, entry.modId());
                return true;
            }

            // ModList was not usable (or the id differs from the jar's): fall back to asking the class loader, which is
            // the question that actually matters for whether the mixin can be applied at all.
            if (isClassPresent(entry.targetClass())) {
                LOGGER.info("[ApotheosisExpansion] Mixin {} enabled: target class {} is present (ModList had no entry for '{}')",
                    simpleName, entry.targetClass(), entry.modId());
                return true;
            }

            LOGGER.info("[ApotheosisExpansion] Mixin {} skipped: neither mod '{}' nor class {} was found",
                simpleName, entry.modId(), entry.targetClass());
            return false;
        }

        // An unknown mixin should not be silently dropped: let Mixin try it and report normally.
        return true;
    }

    /** Never throws: an unavailable {@code ModList} means "cannot tell", not "not installed". */
    private static boolean isModLoaded(String modId) {
        try {
            ModList modList = ModList.get();
            return modList != null && modList.isLoaded(modId);
        }
        catch (RuntimeException | LinkageError ex) {
            return false;
        }
    }

    /**
     * Asks whether the target class can be seen from this mod's context, <b>without loading it</b>.
     *
     * <p><b>Why this must not use {@code Class.forName}.</b> Loading the target class here would hand it to the JVM
     * before Mixin gets a chance to transform it: the class lands in the loader's cache untransformed, and every later
     * {@code Mixing ... into ...} step is skipped because the class is already defined. That is exactly what happened -
     * the plugin reported the targets as present and then not a single compat mixin was applied, with no error
     * anywhere. A presence check must therefore read the resource, not define the class.
     *
     * <p>Resolving {@code <binary name>.class} as a resource answers the same question - "would Mixin find this class"
     * - while leaving the class loader's cache untouched.
     */
    private static boolean isClassPresent(String className) {
        String resource = className.replace('.', '/') + ".class";
        try {
            ClassLoader loader = CompatMixinPlugin.class.getClassLoader();
            return loader != null && loader.getResource(resource) != null;
        }
        catch (RuntimeException | LinkageError ex) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // No cross-config target sharing needed.
    }

    @Override
    public List<String> getMixins() {
        // The mixin list stays declared in the json so the file remains the single source of truth.
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // No bytecode pre-processing required.
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // No bytecode post-processing required.
    }
}
