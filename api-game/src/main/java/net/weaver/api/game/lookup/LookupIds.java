package net.weaver.api.game.lookup;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

/**
 * One lookup per kind and id. Asking again with the same types returns the
 * same lookup, so two mods can share a lookup by id without depending on each
 * other; asking with different types fails.
 */
final class LookupIds {
    private record Entry(Class<?> type, Class<?> context, Object lookup) {
    }

    private static final Map<String, Entry> LOOKUPS = new HashMap<>();

    private LookupIds() {
    }

    @SuppressWarnings("unchecked")
    static synchronized <L> L intern(String kind, Identifier id, Class<?> type, Class<?> context, Supplier<L> factory) {
        String key = kind + " " + id;
        Entry existing = LOOKUPS.get(key);
        if (existing != null) {
            if (existing.type() != type || existing.context() != context) {
                throw new IllegalArgumentException(kind + " lookup " + id + " already exists for "
                        + existing.type().getName() + " with context " + existing.context().getName());
            }
            return (L) existing.lookup();
        }
        L created = factory.get();
        LOOKUPS.put(key, new Entry(type, context, created));
        return created;
    }
}
