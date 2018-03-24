package xyz.upperlevel.uppercore.registry;

import org.bukkit.plugin.Plugin;
import xyz.upperlevel.uppercore.registry.RegistryVisitor.VisitResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class RegistryRoot {
    public static final char PLUGIN_PATH_DIVIDER = '@';
    private Map<Plugin, Registry> pluginRoots = new HashMap<>();
    private Map<String, Registry> rootsByName = new HashMap<>();

    public <T> Registry<T> register(Plugin plugin) {
        Registry<T> pluginRoot = new Registry<>(this, plugin.getName().toLowerCase(), null);
        boolean conflict = rootsByName.putIfAbsent(pluginRoot.getName(), pluginRoot) != null;
        // Check if there are name conflicts
        if (conflict) {
            // Check whether the conflict is made by the same plugin or another one with a similar name (case-insensitive)
            if (pluginRoots.containsKey(plugin)) {
                throw new IllegalArgumentException("Plugin already registered");
            } else {
                throw new IllegalStateException("Name conflict detected: multiple plugins are called '"
                        + pluginRoot.getName() + "' (case-insensitive)");
            }
        }
        // No conflicts occurred
        // Register the root by the plugin and return it
        pluginRoots.put(plugin, pluginRoot);
        return pluginRoot;
    }

    public Registry get(Plugin plugin) {
        return pluginRoots.get(plugin);
    }

    public Map<Plugin, Registry> getChildren() {
        return Collections.unmodifiableMap(pluginRoots);
    }

    public Object find(String location) {
        int divider = location.indexOf(PLUGIN_PATH_DIVIDER);
        if (divider == -1) {
            throw new IllegalArgumentException("Cannot find divider, check that the location is in format 'plugin" + PLUGIN_PATH_DIVIDER + "path'");
        }
        String plugin = location.substring(0, divider);
        String path = location.substring(divider + 1);
        return find(plugin, path);
    }

    public Object find(String plugin, String path) {
        Registry pluginRoot = rootsByName.get(plugin);
        if (pluginRoot == null) {
            throw new IllegalArgumentException("Cannot find plugin '" + plugin + "'");
        }
        return pluginRoot.find(path);
    }

    public VisitResult visit(RegistryVisitor visitor) {
        for (Registry<?> reg : pluginRoots.values()) {
            VisitResult res = reg.visit(visitor);
            if (res == VisitResult.TERMINATE) return VisitResult.TERMINATE;
        }
        return VisitResult.CONTINUE;
    }

    @Override
    public String toString() {
        return "{" +
                rootsByName.entrySet().stream()
                        .map(c -> c.getKey() + "=" + c.getValue())
                        .collect(Collectors.joining(",")) +
                "}";
    }
}
