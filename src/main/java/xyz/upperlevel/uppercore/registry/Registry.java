package xyz.upperlevel.uppercore.registry;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import xyz.upperlevel.uppercore.registry.RegistryVisitor.VisitResult;
import xyz.upperlevel.uppercore.util.CollectionUtil;

import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class Registry<T> {
    @Getter
    private final RegistryRoot root;

    @Getter
    private final String name;

    @Getter
    private final Registry parent;

    private Map<String, Child> children = new HashMap<>();

    public <O> Registry<O> registerChild(String registryName) {
        registryName = registryName.toLowerCase();
        Registry<O> child = new Registry<>(root, registryName, this);
        Child entry = new Child(false, child);
        boolean conflict = children.putIfAbsent(registryName, entry) != null;

        if (conflict) {
            throw new IllegalArgumentException("Child with name '" + registryName + "' already present");
        }
        return child;
    }

    public void register(String name, T object) {
        name = name.toLowerCase();
        Child entry = new Child(true, object);
        boolean conflict = children.putIfAbsent(name, entry) != null;
        if (conflict) {
            throw new IllegalArgumentException("Entry with name '" + name + "' already present");
        }
    }

    @SuppressWarnings("unchecked")
    public T get(String name) {
        name = name.toLowerCase();
        Child entry = children.get(name);
        return entry == null ? null : entry.leaf ? (T) entry.value : null;
    }

    public Registry<?> getChild(String name) {
        name = name.toLowerCase();
        Child entry = children.get(name);
        return entry == null ? null : entry.leaf ? null : (Registry<?>) entry.value;
    }

    public Collection<Registry<?>> getChildren() {
        return children.values().stream()
                .filter(c -> !c.leaf)
                .map(c -> (Registry<?>) c.value)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public Map<String, T> getRegistered() {
        return children.entrySet().stream()
                .filter(c -> c.getValue().leaf)
                .map(c -> new AbstractMap.SimpleEntry<>(c.getKey(), (T) c.getValue().value))
                .collect(CollectionUtil.toMap());
    }

    public Object find(String path) {
        if (path.indexOf(RegistryRoot.PLUGIN_PATH_DIVIDER) > 0) {
            return root.find(path);
        }
        int dividerIndex = path.indexOf('.');
        if (dividerIndex < 0) {
            return get(path);
        } else {
            String localPath = path.substring(0, dividerIndex);
            Child entry = children.get(localPath);
            if (entry == null) {
                throw new IllegalArgumentException("Cannot find registry '" + localPath + "':" + children.keySet());
            }
            if (entry.leaf) {
                throw new IllegalArgumentException("'" + localPath + "' is not a Registry");
            }
            return ((Registry<?>)entry.value).find(path.substring(dividerIndex + 1));
        }
    }

    public VisitResult visit(RegistryVisitor visitor) {
        VisitResult selfRes = visitor.preVisitRegistry(this);
        if (selfRes == VisitResult.TERMINATE) return VisitResult.TERMINATE;
        if (selfRes == VisitResult.SKIP) return VisitResult.CONTINUE;
        // Get folders and files
        // Sort the stream so that folders come before files
        List<Map.Entry<String, Child>> l = children.entrySet().stream()
                .sorted((a, b) -> Boolean.compare(a.getValue().leaf, b.getValue().leaf))
                .collect(Collectors.toList());
        // Iterate all the "folders" (registries)
        Iterator<Map.Entry<String, Child>> i = l.iterator();
        Map.Entry<String, Child> e = null;
        while (!(e = i.next()).getValue().leaf) {
            VisitResult res = ((Registry<?>)e.getValue().value).visit(visitor);
            if (res == VisitResult.TERMINATE) return VisitResult.TERMINATE;
            if (!i.hasNext()) return visitor.postVisitRegistry(this);
        }
        // Iterate all the "files" (entries)
        while (e != null) {
            VisitResult res = visitor.visitEntry(e.getKey(), e.getValue().value);
            if (res == VisitResult.TERMINATE) return VisitResult.TERMINATE;
            e = i.hasNext() ? i.next() : null;
        }
        return visitor.postVisitRegistry(this);
    }

    @Override
    public String toString() {
        return "{" +
                children.entrySet().stream()
                        .map(c -> c.getKey() + "=" + c.getValue().value)
                        .collect(Collectors.joining(",")) +
                "}";
    }


    @RequiredArgsConstructor
    @Getter
    private static final class Child {
        private final boolean leaf;
        private final Object value;

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Child && Objects.equals(((Child)other).value, value);
        }
    }
}
