package net.weaver.core.modops;

import net.weaver.core.VersionRange;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Dependency graph over a set of {@link ModListEntry} values.
 *
 * <p>Nodes are entries with a non-blank id; when two entries share an id the
 * first one wins. Edges point from a mod to the mods it depends on. Required
 * dependencies are included whenever the target exists. Optional dependencies
 * are included only when the target exists and its version satisfies the
 * declared range, matching the loader's ordering rules.
 *
 * <p>All exposed collections are immutable and deterministically ordered.
 */
public final class ModDependencyGraph {

    private final Map<String, ModListEntry> nodes;
    private final Map<String, Set<String>> dependencies;
    private final Map<String, Set<String>> dependents;
    private final List<List<String>> cycles;

    /**
     * Builds a graph from entries.
     *
     * @param entries entries to include; duplicate ids keep the first occurrence
     * @throws NullPointerException if {@code entries} is {@code null}
     */
    public ModDependencyGraph(Collection<ModListEntry> entries) {
        Map<String, ModListEntry> byId = new TreeMap<>();
        for (ModListEntry entry : Objects.requireNonNull(entries, "entries")) {
            if (entry.id().isBlank()) {
                continue;
            }
            byId.putIfAbsent(entry.id(), entry);
        }
        this.nodes = Collections.unmodifiableMap(new TreeMap<>(byId));

        Map<String, Set<String>> forward = new TreeMap<>();
        Map<String, Set<String>> reverse = new TreeMap<>();
        for (String id : byId.keySet()) {
            forward.put(id, new TreeSet<>());
            reverse.put(id, new TreeSet<>());
        }
        for (ModListEntry entry : byId.values()) {
            for (String dependency : entry.dependencies().keySet()) {
                addEdge(entry.id(), dependency, byId, forward, reverse);
            }
            for (Map.Entry<String, String> optional : entry.optional().entrySet()) {
                ModListEntry target = byId.get(optional.getKey());
                if (target != null && VersionRange.matches(target.version(), optional.getValue())) {
                    addEdge(entry.id(), optional.getKey(), byId, forward, reverse);
                }
            }
        }
        this.dependencies = freeze(forward);
        this.dependents = freeze(reverse);
        this.cycles = List.copyOf(findCycles());
    }

    /**
     * Builds a graph from the loaded entries of a report.
     *
     * @param list report to read accepted entries from
     * @return graph over {@link ModList#accepted()}
     * @throws NullPointerException if {@code list} is {@code null}
     */
    public static ModDependencyGraph from(ModList list) {
        return new ModDependencyGraph(Objects.requireNonNull(list, "list").accepted());
    }

    /**
     * Returns every node id in sorted order.
     *
     * @return immutable node id set
     */
    public Set<String> nodes() {
        return nodes.keySet();
    }

    /**
     * Returns the direct dependencies of a node that exist in this graph.
     *
     * @param id node id
     * @return immutable, sorted dependency ids; empty when the node is unknown
     */
    public List<String> dependenciesOf(String id) {
        return List.copyOf(dependencies.getOrDefault(id, Set.of()));
    }

    /**
     * Returns the nodes that directly depend on {@code id}.
     *
     * @param id node id
     * @return immutable, sorted dependent ids; empty when the node is unknown
     */
    public List<String> dependentsOf(String id) {
        return List.copyOf(dependents.getOrDefault(id, Set.of()));
    }

    /**
     * Returns the dependency cycles in this graph.
     *
     * <p>Each element is the sorted list of ids in one strongly connected
     * component that contains at least one cycle. A self-dependency is reported
     * as a single-element list. Cycles are sorted by their joined ids so the
     * result is stable across runs.
     *
     * @return immutable list of cycles
     */
    public List<List<String>> cycles() {
        return cycles;
    }

    /**
     * Returns a deterministic load order with dependencies before dependents.
     *
     * <p>Nodes that cannot be ordered because of a cycle are appended in sorted
     * id order, mirroring the loader's fallback behavior.
     *
     * @return immutable topological order containing every node exactly once
     */
    public List<String> topologicalOrder() {
        Map<String, Integer> indegree = new TreeMap<>();
        for (String id : nodes.keySet()) {
            indegree.put(id, dependencies.getOrDefault(id, Set.of()).size());
        }
        ArrayDeque<String> ready = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                ready.addLast(entry.getKey());
            }
        }
        List<String> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.removeFirst();
            ordered.add(id);
            for (String dependent : dependents.getOrDefault(id, Set.of())) {
                int remaining = indegree.merge(dependent, -1, Integer::sum);
                if (remaining == 0) {
                    ready.addLast(dependent);
                }
            }
        }
        if (ordered.size() != nodes.size()) {
            Set<String> seen = new HashSet<>(ordered);
            for (String id : nodes.keySet()) {
                if (seen.add(id)) {
                    ordered.add(id);
                }
            }
        }
        return List.copyOf(ordered);
    }

    private List<List<String>> findCycles() {
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> lowLink = new HashMap<>();
        Set<String> onStack = new HashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>();
        List<List<String>> found = new ArrayList<>();
        int[] counter = {0};
        for (String id : nodes.keySet()) {
            if (!index.containsKey(id)) {
                strongConnect(id, index, lowLink, onStack, stack, found, counter);
            }
        }
        found.sort(Comparator.comparing(cycle -> String.join("\u0000", cycle)));
        return found;
    }

    private void strongConnect(String id, Map<String, Integer> index,
                               Map<String, Integer> lowLink, Set<String> onStack,
                               ArrayDeque<String> stack, List<List<String>> found,
                               int[] counter) {
        index.put(id, counter[0]);
        lowLink.put(id, counter[0]);
        counter[0]++;
        stack.push(id);
        onStack.add(id);

        for (String dependency : dependencies.getOrDefault(id, Set.of())) {
            if (!index.containsKey(dependency)) {
                strongConnect(dependency, index, lowLink, onStack, stack, found, counter);
                lowLink.put(id, Math.min(lowLink.get(id), lowLink.get(dependency)));
            } else if (onStack.contains(dependency)) {
                lowLink.put(id, Math.min(lowLink.get(id), index.get(dependency)));
            }
        }

        if (lowLink.get(id).equals(index.get(id))) {
            List<String> component = new ArrayList<>();
            String member;
            do {
                member = stack.pop();
                onStack.remove(member);
                component.add(member);
            } while (!member.equals(id));
            boolean selfDependency = dependencies.getOrDefault(id, Set.of()).contains(id);
            if (component.size() > 1 || selfDependency) {
                component.sort(Comparator.naturalOrder());
                found.add(List.copyOf(component));
            }
        }
    }

    private static void addEdge(String id, String dependency, Map<String, ModListEntry> byId,
                                Map<String, Set<String>> forward,
                                Map<String, Set<String>> reverse) {
        if (!byId.containsKey(dependency)) {
            return;
        }
        forward.get(id).add(dependency);
        reverse.get(dependency).add(id);
    }

    private static Map<String, Set<String>> freeze(Map<String, Set<String>> source) {
        Map<String, Set<String>> frozen = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            frozen.put(entry.getKey(),
                    Collections.unmodifiableSet(new TreeSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(frozen);
    }
}
