package com.tamamo.apotheosisexpansion;

import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;

/**
 * Keeps a mob tagged with {@link #PROTECTED_TAG} alive when the world is switched to Peaceful.
 *
 * <p><b>The problem with the obvious approach.</b> {@code Mob#checkDespawn} tests Peaceful <i>before</i> it looks
 * at the persistence flag:
 * <pre>
 *   if (this.level().getDifficulty() == Difficulty.PEACEFUL &amp;&amp; this.shouldDespawnInPeaceful()) {
 *       this.discard();                                  // &lt;- no persistence check on this branch
 *   } else if (!this.isPersistenceRequired() &amp;&amp; !this.requiresCustomPersistence()) {
 *       ...distance culling...
 *   }
 * </pre>
 * A name tag only calls {@code Mob#setPersistenceRequired} ({@code NameTagItem}), which feeds the <i>second</i>
 * branch, so a named zombie is still deleted the moment the difficulty becomes Peaceful. Nothing vanilla does
 * can save it.
 *
 * <p><b>Why a tag and not the custom name.</b> {@code Entity#hasCustomName} is true for far more entities than
 * "a player named this": Apotheosis stamps a generated name on every invader and elite
 * ({@code Invader#initBoss} → {@code BasicBossData#applyEntityName} → {@code NameHelper}), spawn eggs can carry a
 * name component, and {@code Slime#remove} copies the parent's name onto every split child. A plain
 * {@code hasCustomName} rule would therefore silently cover bosses the player never touched. A scoreboard tag is
 * only ever set by {@link com.tamamo.apotheosisexpansion.mixin.NameTagItemMixin} - i.e. by a player applying a
 * name tag - so the rule matches exactly what was asked for.
 *
 * <p><b>Why an entity tag specifically.</b> Tags are vanilla, persist through save/load as the {@code Tags} NBT
 * list ({@code Entity#addAdditionalSaveData}), are visible in {@code /data get entity}, and can be added or removed
 * by command for scripted setups:
 * <pre>
 *   /tag @e[type=zombie,limit=1] add apotheosis_expansion.named
 *   /tag @e[type=zombie] remove apotheosis_expansion.named
 * </pre>
 *
 * <p><b>Why the event and not a mixin on checkDespawn.</b> {@code Mob#checkDespawn} begins with
 * {@code EventHooks.checkMobDespawn(this)}, whose contract explicitly includes "may be used to keep mobs from
 * despawning in peaceful mode"; returning {@code Result.DENY} skips the deletion and resets {@code noActionTime},
 * which is exactly the semantics wanted. The only two classes with their own {@code checkDespawn} -
 * {@code WitherBoss} and {@code EnderDragon} - call the same hook or do nothing at all, so no override is missed.
 *
 * <p><b>What a protected mob can still do.</b> Peaceful keeps {@code LivingEntity#canAttack} and
 * {@code TargetingConditions} from ever selecting a player, and {@code DamageScaling}'s default function scales
 * player damage to zero, so a protected mob cannot hurt the player. It can still attack villagers and animals, a
 * creeper can still break blocks (block interaction is gated by the {@code mobGriefing} game rule, not by
 * difficulty), and a warden can still dig.
 *
 * <p><b>Known limits.</b> The tag lives on the entity, so it is lost when vanilla replaces the entity rather than
 * moving it: {@code Mob#convertTo} (zombie → drowned, villager → witch) and {@code Slime#remove}'s split children
 * do not copy tags. Re-tagging the result is left to the player; the alternative - hooking both paths - would mean
 * covering every modded conversion as well, which is not worth it for a cosmetic preference.
 */
public final class NamedMobPeacefulProtection {

    /**
     * Marker set by a player applying a name tag.
     * <p>
     * Deliberately uses only characters the {@code /tag} command accepts: its argument is
     * {@code StringArgumentType.word()}, which reads an unquoted string of {@code [0-9A-Za-z_.+-]}. A colon -
     * as in a {@code modid:path} resource location - is <em>not</i> accepted there, which is why this is a dotted
     * name rather than a namespaced id.
     */
    public static final String PROTECTED_TAG = "apotheosis_expansion.named";

    /**
     * Denies the Peaceful despawn of a tagged mob.
     * <p>
     * <b>Ordering.</b> The difficulty test comes first and costs one field read, because vanilla fires this event
     * unconditionally at the top of {@code checkDespawn} for every mob on every tick - not only in Peaceful.
     * Anything more expensive before that guard would show up in server tick times on a mob-heavy world.
     * <p>
     * <b>No mob-type test.</b> The tag is the whole condition, matching the feature's definition. For a mob that
     * already survives Peaceful (piglin, hoglin, shulker, ender dragon) the result is the same as vanilla, and the
     * {@code DENY} only restates the persistence a name tag already granted; it cannot make a non-hostile mob
     * behave worse than it already did, because {@code Mob#setPersistenceRequired} already suppressed its distance
     * culling.
     * <p>
     * <b>Priority.</b> {@code MobDespawnEvent} is a {@code Result}-based event, not a cancellable one: every handler
     * writes the same field and the <i>last</i> writer wins, so {@code LOWEST} is what makes this reliable. At
     * {@code HIGHEST} any later handler could silently undo it. This mod only ever raises {@code DENY}, never
     * {@code ALLOW}, so being last takes nothing away from anyone.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMobDespawn(MobDespawnEvent event) {
        if (!Config.enableNamedPeacefulProtection()) {
            return;
        }
        if (event.getLevel().getDifficulty() != Difficulty.PEACEFUL) {
            return;
        }

        Mob mob = event.getEntity();
        if (!mob.getTags().contains(PROTECTED_TAG)) {
            return;
        }

        event.setResult(MobDespawnEvent.Result.DENY);
    }
}
