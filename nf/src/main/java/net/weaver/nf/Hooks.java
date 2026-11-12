package net.weaver.nf;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.HitResult;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.AnimalTameEvent;
import net.weaver.api.game.event.world.MobGriefingEvent;
import net.weaver.api.game.event.world.ProjectileImpactEvent;

/**
 * Checks a mod's own code makes where vanilla would fire an event, as NeoForge's
 * {@code EventHooks}: they fire Weaver's events, so other mods' listeners apply.
 */
public final class Hooks {
    private Hooks() {
    }

    /** Whether {@code entity} may change blocks (the {@code mobGriefing} rule and its listeners). */
    public static boolean canEntityGrief(ServerLevel level, Entity entity) {
        boolean rule = level.getGameRules().get(GameRules.MOB_GRIEFING);
        return GameEvents.post(new MobGriefingEvent(level, entity, rule), level).canGrief();
    }

    /** True when a listener canceled the impact of {@code projectile} (a projectile only). */
    public static boolean onProjectileImpact(Entity projectile, HitResult hit) {
        return projectile instanceof Projectile real
                && GameEvents.canceled(GameEvents.post(new ProjectileImpactEvent(real, hit), projectile.level()));
    }

    /** True when a listener canceled {@code tamer} taming {@code animal}. */
    public static boolean onAnimalTame(Mob animal, Player tamer) {
        return GameEvents.canceled(GameEvents.post(new AnimalTameEvent(animal, tamer), animal.level()));
    }
}
