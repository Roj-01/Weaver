package net.weaver.patched;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Side-band attachment storage used by patched game classes.
 *
 * <p>Holders are keyed by identity and weakly referenced. The value entry keeps
 * its {@link IdentityKey} strongly reachable so the key wrapper cannot disappear
 * while the holder is alive; the holder itself is only weakly reachable through
 * the key. When a holder is collected its key is enqueued and the entry is
 * expunged on the next operation. Inner maps are synchronized so reads and
 * writes from different threads are safe.</p>
 */
public final class WeaverAttachmentStore {
    private static final ReferenceQueue<Object> COLLECTED = new ReferenceQueue<>();
    private static final Map<IdentityKey, Map.Entry<IdentityKey, Map<String, Object>>> ATTACHMENTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private WeaverAttachmentStore() {
    }

    public static Object get(Object holder, String id) {
        Objects.requireNonNull(holder, "holder");
        String key = requireId(id);
        synchronized (ATTACHMENTS) {
            expunge();
            Map.Entry<IdentityKey, Map<String, Object>> attachments =
                    ATTACHMENTS.get(new IdentityKey(holder));
            return attachments == null ? null : attachments.getValue().get(key);
        }
    }

    public static Object set(Object holder, String id, Object value) {
        Objects.requireNonNull(holder, "holder");
        String key = requireId(id);
        synchronized (ATTACHMENTS) {
            expunge();
            return findOrCreate(holder).getValue().put(key, value);
        }
    }

    public static Object remove(Object holder, String id) {
        Objects.requireNonNull(holder, "holder");
        String key = requireId(id);
        synchronized (ATTACHMENTS) {
            expunge();
            Map.Entry<IdentityKey, Map<String, Object>> attachments =
                    ATTACHMENTS.get(new IdentityKey(holder));
            if (attachments == null) {
                return null;
            }
            Object previous = attachments.getValue().remove(key);
            if (attachments.getValue().isEmpty()) {
                ATTACHMENTS.remove(attachments.getKey());
            }
            return previous;
        }
    }

    public static boolean has(Object holder, String id) {
        Objects.requireNonNull(holder, "holder");
        String key = requireId(id);
        synchronized (ATTACHMENTS) {
            expunge();
            Map.Entry<IdentityKey, Map<String, Object>> attachments =
                    ATTACHMENTS.get(new IdentityKey(holder));
            return attachments != null && attachments.getValue().containsKey(key);
        }
    }

    public static void clear(Object holder) {
        Objects.requireNonNull(holder, "holder");
        synchronized (ATTACHMENTS) {
            expunge();
            Map.Entry<IdentityKey, Map<String, Object>> attachments =
                    ATTACHMENTS.get(new IdentityKey(holder));
            if (attachments != null) {
                ATTACHMENTS.remove(attachments.getKey());
            }
        }
    }

    public static int size() {
        synchronized (ATTACHMENTS) {
            expunge();
            return ATTACHMENTS.size();
        }
    }

    private static Map.Entry<IdentityKey, Map<String, Object>> findOrCreate(Object holder) {
        IdentityKey lookup = new IdentityKey(holder);
        Map.Entry<IdentityKey, Map<String, Object>> attachments = ATTACHMENTS.get(lookup);
        if (attachments == null) {
            IdentityKey key = new IdentityKey(holder, COLLECTED);
            attachments = Map.entry(key, Collections.synchronizedMap(new HashMap<>()));
            ATTACHMENTS.put(key, attachments);
        }
        return attachments;
    }

    private static void expunge() {
        Object reference;
        while ((reference = COLLECTED.poll()) != null) {
            ATTACHMENTS.remove((IdentityKey) reference);
        }
    }

    private static String requireId(String id) {
        return Objects.requireNonNull(id, "id");
    }

    /**
     * Weak identity key. The wrapper is a weak reference to the real holder;
     * the holder's attachment entry strongly references the wrapper, keeping
     * the weak-map key alive until the holder itself is collected.
     */
    private static final class IdentityKey extends WeakReference<Object> {
        private final int hash;

        private IdentityKey(Object holder) {
            super(holder);
            this.hash = System.identityHashCode(holder);
        }

        private IdentityKey(Object holder, ReferenceQueue<Object> queue) {
            super(holder, queue);
            this.hash = System.identityHashCode(holder);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof IdentityKey key)) {
                return false;
            }
            Object holder = get();
            return holder != null && holder == key.get();
        }
    }
}
