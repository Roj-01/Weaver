package net.weaver.api.command;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-wide, thread-safe permission registry and decision service.
 *
 * <p>The service combines three inputs, in this order:
 *
 * <ol>
 *   <li>an explicit per-subject decision for the requested node, one of its
 *       ancestors, or the namespace wildcard;</li>
 *   <li>the registered policy of the exact requested node;</li>
 *   <li>the subject's op level relative to the registered required op
 *       level.</li>
 * </ol>
 *
 * <p>Explicit decisions therefore always win: an explicit deny overrides an
 * {@link PermissionPolicy#ALLOW} registration and an explicit allow overrides a
 * {@link PermissionPolicy#DENY} registration. Registrations are
 * reference-counted by node, policy, and required op level. A node that has
 * never been registered is denied by default.
 *
 * <p>All methods are thread-safe. Reads use concurrent maps without holding a
 * global lock; registration and unregistration update one node's reference
 * count atomically. {@link #install(PermissionResolver)} swaps a volatile
 * resolver and does not clear existing registrations or decisions.
 * {@link #install(PermissionResolver, PermissionStore)} also loads a persisted
 * snapshot when a store is supplied. {@link #reset()} clears everything and is
 * intended for shutdown and tests; it is not atomic with respect to
 * registrations that race with it.
 *
 * <p>Explicit decisions are persisted best-effort when a {@link PermissionStore}
 * is installed. A persistence failure is isolated and logged at most once; it
 * never propagates to a caller or to command execution. Without a store,
 * decisions stay in-memory.
 *
 * <p>Resolver failures are contained. If {@link PermissionResolver#subject}
 * throws, the raw source is used as the subject; if
 * {@link PermissionResolver#opLevel} throws, the op level is treated as zero.
 * Permission evaluation itself never throws for a resolver failure.
 */
public final class PermissionService
{
    private static final Object NULL_SUBJECT = new Object();

    private static final ConcurrentHashMap<PermissionNode, RegistrationRecord> REGISTRATIONS =
            new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<Object, ConcurrentHashMap<PermissionNode, Boolean>>
            DECISIONS = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>> PERSISTED =
            new ConcurrentHashMap<>();

    private static final AtomicBoolean STORE_LOAD_FAILURE = new AtomicBoolean();
    private static final AtomicBoolean STORE_SAVE_FAILURE = new AtomicBoolean();

    private static volatile PermissionResolver resolver = PermissionResolver.simple();
    private static volatile PermissionStore store;

    private PermissionService()
    {
    }

    /**
     * Installs the resolver used to map sources to subjects and op levels.
     *
     * <p>Existing registrations and explicit decisions are retained. Decisions
     * created under a previous resolver remain keyed by the subject that
     * resolver produced; a resolver that changes subject identity should be
     * paired with a decision reset.
     *
     * @param newResolver the resolver, not {@code null}
     * @throws NullPointerException when {@code newResolver} is {@code null}
     */
    public static void install(PermissionResolver newResolver)
    {
        resolver = Objects.requireNonNull(newResolver, "resolver");
    }

    /**
     * Installs a resolver and a persistence store together.
     *
     * <p>The store's current snapshot is loaded into the persisted decision
     * map. Existing object-keyed decisions and registrations are retained. A
     * load failure is logged once and treated as an empty snapshot.
     *
     * @param newResolver the resolver, not {@code null}
     * @param newStore    the store, not {@code null}
     * @throws NullPointerException when either argument is {@code null}
     */
    public static void install(PermissionResolver newResolver, PermissionStore newStore)
    {
        resolver = Objects.requireNonNull(newResolver, "resolver");
        PermissionStore installed = Objects.requireNonNull(newStore, "store");
        store = installed;
        Map<String, Map<String, Boolean>> loaded;
        try
        {
            loaded = installed.load();
        }
        catch (Throwable failure)
        {
            if (STORE_LOAD_FAILURE.compareAndSet(false, true))
            {
                logLine("[weaver] PERMISSION STORE LOAD FAILED reason=" + failure);
            }
            loaded = Map.of();
        }
        replacePersisted(loaded);
    }

    /**
     * The currently installed resolver.
     *
     * @return the resolver, never {@code null}
     */
    public static PermissionResolver resolver()
    {
        return resolver;
    }

    /**
     * Whether a {@link PermissionStore} is currently installed.
     *
     * <p>Used by the core backend to avoid replacing a resolver that the
     * loader-facing permission store installer already configured.
     *
     * @return {@code true} when a store is installed
     */
    public static boolean hasStore()
    {
        return store != null;
    }

    /**
     * Returns a deep, deterministically ordered copy of the persisted
     * decisions.
     *
     * <p>The outer map is keyed by subject key and the inner map by permission
     * node text. The snapshot is intended for tests and diagnostics.
     *
     * @return an immutable snapshot, never {@code null}
     */
    public static Map<String, Map<String, Boolean>> snapshot()
    {
        Map<String, Map<String, Boolean>> copy = new TreeMap<>();
        for (Map.Entry<String, ConcurrentHashMap<String, Boolean>> entry : PERSISTED.entrySet())
        {
            Map<String, Boolean> nodes = new TreeMap<>(entry.getValue());
            copy.put(entry.getKey(), Collections.unmodifiableMap(nodes));
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Clears all registrations and explicit decisions, clears the persisted
     * map and store reference, and restores {@link PermissionResolver#simple()}.
     *
     * <p>This is a shutdown and test helper. It does not write an empty
     * snapshot to the store's backing file; the file is left untouched. It is
     * not atomic with respect to registrations or decisions that race with it.
     */
    public static void reset()
    {
        REGISTRATIONS.clear();
        DECISIONS.clear();
        PERSISTED.clear();
        store = null;
        resolver = PermissionResolver.simple();
        STORE_LOAD_FAILURE.set(false);
        STORE_SAVE_FAILURE.set(false);
    }

    /**
     * Registers a permission node with a fallback policy and required op level.
     *
     * <p>A duplicate registration with the same node, policy, and op level
     * increments the shared reference count and returns a new handle. A
     * duplicate with a different policy or op level is rejected.
     *
     * @param node    the node to register, not {@code null}
     * @param policy  the fallback policy, not {@code null}
     * @param opLevel the op level required when the policy is
     *                {@link PermissionPolicy#DENY}; values at or below zero
     *                disable the op fallback
     * @return a new registration handle, never {@code null}
     * @throws NullPointerException     when {@code node} or {@code policy} is
     *                                  {@code null}
     * @throws IllegalArgumentException when the node is already registered with
     *                                  a different policy or op level
     */
    public static PermissionRegistration register(PermissionNode node,
            PermissionPolicy policy, int opLevel)
    {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(policy, "policy");
        RegistrationRecord[] created = new RegistrationRecord[1];
        REGISTRATIONS.compute(node, (key, existing) ->
        {
            if (existing == null)
            {
                RegistrationRecord record = new RegistrationRecord(policy, opLevel);
                record.count = 1;
                created[0] = record;
                return record;
            }
            if (existing.policy != policy || existing.opLevel != opLevel)
            {
                throw new IllegalArgumentException("Permission node " + key
                        + " is already registered with policy " + existing.policy
                        + " and op level " + existing.opLevel
                        + "; requested policy " + policy + " and op level " + opLevel);
            }
            existing.count++;
            created[0] = existing;
            return existing;
        });
        return new RegistrationHandle(node, created[0]);
    }

    /**
     * Evaluates a permission node for a source.
     *
     * <p>The exact algorithm is:
     *
     * <ol>
     *   <li>Compute the subject with
     *       {@link PermissionResolver#subject(Object)}; a {@code null} result
     *       falls back to the raw source.</li>
     *   <li>Look for an explicit decision on the node, then on its ancestors
     *       nearest first, then on the node's namespace wildcard and the
     *       wildcard's ancestors. The first explicit decision wins. An explicit
     *       deny therefore wins over a node policy and op level, and an
     *       explicit allow wins over a deny policy.</li>
     *   <li>If the node is not registered, deny.</li>
     *   <li>If the registered policy is {@link PermissionPolicy#ALLOW}, allow.</li>
     *   <li>If the registered policy is {@link PermissionPolicy#DENY}, allow
     *       only when the registered required op level is positive and the
     *       source's op level is greater than or equal to it.</li>
     * </ol>
     *
     * @param source the raw source, possibly {@code null}
     * @param node   the node to evaluate, not {@code null}
     * @return {@code true} when the decision is allow
     * @throws NullPointerException when {@code node} is {@code null}
     */
    public static boolean isAllowed(Object source, PermissionNode node)
    {
        Objects.requireNonNull(node, "node");
        Object subject = resolveSubject(source);
        Boolean explicit = findExplicitDecision(subject, node);
        if (explicit != null)
        {
            return explicit;
        }
        RegistrationRecord record = REGISTRATIONS.get(node);
        if (record == null)
        {
            return false;
        }
        if (record.policy == PermissionPolicy.ALLOW)
        {
            return true;
        }
        if (record.opLevel <= 0)
        {
            return false;
        }
        return resolveOpLevel(source) >= record.opLevel;
    }

    /**
     * Records an explicit allow for a subject and node.
     *
     * <p>The decision applies to the exact node and, through ancestor and
     * wildcard lookup, to descendants of that node.
     *
     * @param source the raw source, possibly {@code null}
     * @param node   the node to allow, not {@code null}
     * @throws NullPointerException when {@code node} is {@code null}
     */
    public static void allow(Object source, PermissionNode node)
    {
        setExplicitDecision(source, node, Boolean.TRUE);
    }

    /**
     * Records an explicit deny for a subject and node.
     *
     * <p>The decision applies to the exact node and, through ancestor and
     * wildcard lookup, to descendants of that node. An explicit deny overrides
     * both an allow policy and an op level fallback.
     *
     * @param source the raw source, possibly {@code null}
     * @param node   the node to deny, not {@code null}
     * @throws NullPointerException when {@code node} is {@code null}
     */
    public static void deny(Object source, PermissionNode node)
    {
        setExplicitDecision(source, node, Boolean.FALSE);
    }

    /**
     * Removes the explicit decision for one subject and exact node.
     *
     * <p>After this call the registered policy and op fallback apply again.
     * Ancestor and wildcard decisions are not touched.
     *
     * @param source the raw source, possibly {@code null}
     * @param node   the node whose explicit decision is removed, not
     *               {@code null}
     * @throws NullPointerException when {@code node} is {@code null}
     */
    public static void clear(Object source, PermissionNode node)
    {
        Objects.requireNonNull(node, "node");
        Object subject = resolveSubject(source);
        Object key = decisionKey(subject);
        ConcurrentHashMap<PermissionNode, Boolean> decisions = DECISIONS.get(key);
        if (decisions != null)
        {
            decisions.remove(node);
            if (decisions.isEmpty())
            {
                DECISIONS.remove(key, decisions);
            }
        }
        String persistedKey = resolveSubjectKey(subject);
        if (persistedKey != null)
        {
            ConcurrentHashMap<String, Boolean> persisted = PERSISTED.get(persistedKey);
            if (persisted != null)
            {
                persisted.remove(node.toString());
                if (persisted.isEmpty())
                {
                    PERSISTED.remove(persistedKey, persisted);
                }
            }
        }
        persistQuietly();
    }

    /**
     * Removes every explicit decision for a subject.
     *
     * @param source the raw source, possibly {@code null}
     */
    public static void clearAll(Object source)
    {
        Object subject = resolveSubject(source);
        DECISIONS.remove(decisionKey(subject));
        String persistedKey = resolveSubjectKey(subject);
        if (persistedKey != null)
        {
            PERSISTED.remove(persistedKey);
        }
        persistQuietly();
    }

    /**
     * Returns the op level of a source.
     *
     * @param source the raw source, possibly {@code null}
     * @return the resolver's op level, or zero when the resolver fails
     */
    public static int opLevel(Object source)
    {
        return resolveOpLevel(source);
    }

    /**
     * Whether an explicit decision currently applies to a source and node.
     *
     * <p>The check uses the same lookup as {@link #isAllowed(Object,
     * PermissionNode)}: the exact node, its ancestors, and the namespace
     * wildcard are consulted. It does not consider the registered policy.
     *
     * @param source the raw source, possibly {@code null}
     * @param node   the node to inspect, not {@code null}
     * @return {@code true} when an explicit allow or deny applies
     * @throws NullPointerException when {@code node} is {@code null}
     */
    public static boolean hasExplicitDecision(Object source, PermissionNode node)
    {
        Objects.requireNonNull(node, "node");
        return findExplicitDecision(resolveSubject(source), node) != null;
    }

    private static void setExplicitDecision(Object source, PermissionNode node, Boolean decision)
    {
        Objects.requireNonNull(node, "node");
        Object subject = resolveSubject(source);
        Object key = decisionKey(subject);
        DECISIONS.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).put(node, decision);
        String persistedKey = resolveSubjectKey(subject);
        if (persistedKey != null)
        {
            PERSISTED.computeIfAbsent(persistedKey, ignored -> new ConcurrentHashMap<>())
                    .put(node.toString(), decision);
        }
        persistQuietly();
    }

    private static Object resolveSubject(Object source)
    {
        try
        {
            Object subject = resolver.subject(source);
            return subject != null ? subject : source;
        }
        catch (Throwable failure)
        {
            return source;
        }
    }

    private static int resolveOpLevel(Object source)
    {
        try
        {
            return resolver.opLevel(source);
        }
        catch (Throwable failure)
        {
            return 0;
        }
    }

    private static Object decisionKey(Object subject)
    {
        return subject != null ? subject : NULL_SUBJECT;
    }

    private static Boolean findExplicitDecision(Object subject, PermissionNode node)
    {
        Boolean decision = lookupDecision(DECISIONS.get(decisionKey(subject)), node, false);
        if (decision != null)
        {
            return decision;
        }
        String persistedKey = resolveSubjectKey(subject);
        if (persistedKey == null)
        {
            return null;
        }
        return lookupDecision(PERSISTED.get(persistedKey), node, true);
    }

    private static Boolean lookupDecision(Map<?, Boolean> decisions, PermissionNode node,
            boolean textKeys)
    {
        if (decisions == null)
        {
            return null;
        }
        Boolean decision = decisions.get(textKeys ? node.toString() : node);
        if (decision != null)
        {
            return decision;
        }
        for (PermissionNode ancestor : node.ancestors())
        {
            decision = decisions.get(textKeys ? ancestor.toString() : ancestor);
            if (decision != null)
            {
                return decision;
            }
        }
        PermissionNode wildcard = node.wildcard();
        decision = decisions.get(textKeys ? wildcard.toString() : wildcard);
        if (decision != null)
        {
            return decision;
        }
        for (PermissionNode wildcardAncestor : wildcard.ancestors())
        {
            decision = decisions.get(textKeys ? wildcardAncestor.toString() : wildcardAncestor);
            if (decision != null)
            {
                return decision;
            }
        }
        return null;
    }

    private static String resolveSubjectKey(Object subject)
    {
        if (subject == null)
        {
            return null;
        }
        try
        {
            String key = resolver.subjectKey(subject);
            return key == null || key.isBlank() ? null : key;
        }
        catch (Throwable failure)
        {
            return null;
        }
    }

    private static void replacePersisted(Map<String, Map<String, Boolean>> loaded)
    {
        PERSISTED.clear();
        if (loaded == null)
        {
            return;
        }
        for (Map.Entry<String, Map<String, Boolean>> subject : loaded.entrySet())
        {
            String key = subject.getKey();
            Map<String, Boolean> nodes = subject.getValue();
            if (key == null || key.isBlank() || nodes == null || nodes.isEmpty())
            {
                continue;
            }
            ConcurrentHashMap<String, Boolean> copy = new ConcurrentHashMap<>();
            for (Map.Entry<String, Boolean> node : nodes.entrySet())
            {
                if (node.getKey() != null && node.getValue() != null)
                {
                    copy.put(node.getKey(), node.getValue());
                }
            }
            if (!copy.isEmpty())
            {
                PERSISTED.put(key, copy);
            }
        }
    }

    private static void persistQuietly()
    {
        PermissionStore current = store;
        if (current == null)
        {
            return;
        }
        try
        {
            current.save(snapshot());
        }
        catch (Throwable failure)
        {
            if (STORE_SAVE_FAILURE.compareAndSet(false, true))
            {
                logLine("[weaver] PERMISSION STORE SAVE FAILED reason=" + failure);
            }
        }
    }

    private static void logLine(String message)
    {
        try
        {
            System.err.println(message);
        }
        catch (Throwable ignored)
        {
        }
    }

    /**
     * One reference-counted registration. The mutable count is only changed
     * while holding the owning node's {@link ConcurrentHashMap} bin lock.
     */
    private static final class RegistrationRecord
    {
        private final PermissionPolicy policy;
        private final int opLevel;
        private int count;

        private RegistrationRecord(PermissionPolicy policy, int opLevel)
        {
            this.policy = policy;
            this.opLevel = opLevel;
        }
    }

    /**
     * The per-register-call handle.
     */
    private static final class RegistrationHandle implements PermissionRegistration
    {
        private final PermissionNode node;
        private final RegistrationRecord record;
        private volatile boolean active = true;

        private RegistrationHandle(PermissionNode node, RegistrationRecord record)
        {
            this.node = node;
            this.record = record;
        }

        @Override
        public PermissionNode node()
        {
            return node;
        }

        @Override
        public boolean isActive()
        {
            return active && REGISTRATIONS.get(node) == record;
        }

        @Override
        public synchronized void unregister()
        {
            if (!active)
            {
                return;
            }
            active = false;
            REGISTRATIONS.computeIfPresent(node, (key, current) ->
            {
                if (current != record)
                {
                    return current;
                }
                current.count--;
                return current.count <= 0 ? null : current;
            });
        }
    }
}
