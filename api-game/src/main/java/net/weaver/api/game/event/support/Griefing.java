package net.weaver.api.game.event.support;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.weaver.api.game.event.world.MobGriefingEvent;

/**
 * Shared logic of the mob-griefing hooks. Every vanilla site reads the rule
 * through {@code GameRules#get}; each hook wraps that call and names the
 * entity, either directly, from an AI goal's mob field, or from the
 * per-thread context a method-entry hook pushed for a method whose entity is
 * a parameter.
 */
public final class Griefing {
    private static final ThreadLocal<List<Entity>> CONTEXT = ThreadLocal.withInitial(ArrayList::new);
    private static final Map<Class<?>, Field> GOAL_FIELDS = new ConcurrentHashMap<>();
    private static final Field NONE;

    static {
        try {
            NONE = Griefing.class.getDeclaredField("NONE");
        } catch (NoSuchFieldException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }

    private Griefing() {
    }

    /**
     * Returns the rule value to use: vanilla's for every other rule, and for
     * {@code mobGriefing} the answer of {@link MobGriefingEvent}.
     */
    public static Object check(Object vanilla, GameRule<?> rule, Entity entity) {
        if (rule != GameRules.MOB_GRIEFING || !(vanilla instanceof Boolean allowed) || entity == null
                || !GameEvents.hasListeners(MobGriefingEvent.class)) {
            return vanilla;
        }
        Entity griefer = entity;
        if (entity instanceof Projectile projectile && projectile.getOwner() != null) {
            griefer = projectile.getOwner();
        }
        if (!(griefer.level() instanceof ServerLevel level)) {
            return vanilla;
        }
        return GameEvents.post(new MobGriefingEvent(level, griefer, allowed), level).canGrief();
    }

    /** Names the entity for the method that is starting; pair with {@link #pop()}. */
    public static void push(Entity entity) {
        CONTEXT.get().add(entity);
    }

    public static void pop() {
        List<Entity> stack = CONTEXT.get();
        if (!stack.isEmpty()) {
            stack.remove(stack.size() - 1);
        }
    }

    /** The entity of the innermost method that pushed one, or {@code null}. */
    public static Entity context() {
        List<Entity> stack = CONTEXT.get();
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    /**
     * The mob that owns an AI goal: the first entity-typed instance field in
     * the goal's class hierarchy (the goal's {@code mob} field, or the outer
     * entity of an inner goal class). Looked up once per class, and only when
     * a griefing listener exists.
     */
    public static Entity goalOwner(Object goal) {
        if (!GameEvents.hasListeners(MobGriefingEvent.class)) {
            return null;
        }
        Field field = GOAL_FIELDS.computeIfAbsent(goal.getClass(), Griefing::findEntityField);
        if (field == NONE) {
            return null;
        }
        try {
            return (Entity) field.get(goal);
        } catch (IllegalAccessException failure) {
            return null;
        }
    }

    private static Field findEntityField(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        && Entity.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        return field;
                    } catch (RuntimeException inaccessible) {
                        return NONE;
                    }
                }
            }
        }
        return NONE;
    }
}
