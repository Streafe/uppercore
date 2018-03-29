package xyz.upperlevel.uppercore.registry;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.configuration.file.YamlConfiguration;
import xyz.upperlevel.uppercore.config.Config;
import xyz.upperlevel.uppercore.config.exceptions.InvalidConfigException;
import xyz.upperlevel.uppercore.registry.RegistryVisitor.VisitResult;
import xyz.upperlevel.uppercore.util.CollectionUtil;
import xyz.upperlevel.uppercore.util.FileUtil;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class Registry<T> {
    @Getter
    private final RegistryRoot root;

    @Getter
    private final Class<?> type;

    @Getter
    private final String name;

    @Getter
    private final Registry parent;

    private Map<String, Child> children = new HashMap<>();

    public Registry(RegistryRoot root, Class<?> type, String name, Registry parent) {
        this.root = root;
        this.type = type;
        this.name = name;
        this.parent = parent;
        if (type != null) {
            root.onChildCreate(this);// Update type registers
        }
    }

    public Registry<?> registerChild(String registryName) {
        return registerChild(registryName, null);
    }

    public <O> Registry<O> registerChild(String registryName, Class<O> type) {
        registryName = registryName.toLowerCase();
        Registry<O> child = new Registry<>(root, type, registryName, this);
        Child entry = new Child(false, child);
        boolean conflict = children.putIfAbsent(registryName, entry) != null;

        if (conflict) {
            throw new IllegalArgumentException("Child with name '" + registryName + "' already present");
        }
        return child;
    }

    public void register(String name, T object) {
        if (type == null) throw new IllegalStateException("Cannot register object in a folder registry (" + getPath() + ")");
        name = name.toLowerCase();
        Child entry = new Child(true, object);
        boolean conflict = children.putIfAbsent(name, entry) != null;
        if (conflict) {
            throw new IllegalArgumentException("Entry with name '" + name + "' already present");
        }
    }

    public void registerFile(File file, RegistryLoader<T> loader) {
        Config config = Config.wrap(YamlConfiguration.loadConfiguration(file));
        String id = FileUtil.getName(file).toLowerCase();
        T object;
        try {
            object = loader.load(this, id, config);
        } catch (InvalidConfigException e) {
            e.addLocation("in file " + file.getPath());
            e.addLocation("from registry " + getPath());
            throw e;
        }
        register(id, object);
    }

    public void registerFolder(File file, RegistryLoader<T> loader, boolean recursive) {
        File[] files = file.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                if (!recursive) continue;
                registerFolder(f, loader, true);
            } else {
                registerFile(file, loader);
            }
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
        Map.Entry<String, Child> e;
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

    public String getPath() {
        Deque<String> names = new ArrayDeque<>();
        Registry<?> current = this;
        while (current.getParent() != null) {
            names.push(current.getName());
            current = current.getParent();
        }
        // When the cycle is done the only register left is
        // the plugin root (because it's the only one without a parent)
        return current.getName() + RegistryRoot.PLUGIN_PATH_DIVIDER + String.join(".", names);
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
