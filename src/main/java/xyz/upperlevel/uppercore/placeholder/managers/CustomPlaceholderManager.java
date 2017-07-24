package xyz.upperlevel.uppercore.placeholder.managers;

import lombok.Getter;
import org.bukkit.plugin.Plugin;
import xyz.upperlevel.uppercore.placeholder.Placeholder;
import xyz.upperlevel.uppercore.placeholder.PlaceholderRegistry;
import xyz.upperlevel.uppercore.placeholder.managers.customs.*;

import java.util.HashMap;
import java.util.Map;

import static xyz.upperlevel.uppercore.Uppercore.get;

public class CustomPlaceholderManager extends BasePlaceholderManager {

    private Map<String, Placeholder> placeholders = new HashMap<>();

    @Getter
    private final PlaceholderRegistry registry = new CustomPlaceholderRegistry();

    public CustomPlaceholderManager() {
        addDefaults();
    }

    @Override
    public void register(Plugin plugin, Placeholder placeholder) {
        placeholders.put(placeholder.getId(), placeholder);
    }

    @Override
    protected Placeholder find(String id) {
        return placeholders.get(id);
    }


    public void addDefaults() {
        register(get(), new PlayerDisplayNamePlaceholder());
        register(get(), new PlayerFoodPlaceholder());
        register(get(), new PlayerHealthPlaceholder());
        register(get(), new PlayerLevelPlaceholder());
        register(get(), new PlayerNamePlaceholder());
        register(get(), new PlayerSaturationPlaceholder());
        register(get(), new VaultBalancePlaceholder());
        register(get(), new PlayerWorldPlaceholder());
    }

    private class CustomPlaceholderRegistry implements PlaceholderRegistry<CustomPlaceholderRegistry> {
        public PlaceholderRegistry getParent() {
            return null;
        }

        public void setParent(PlaceholderRegistry parent) {
            throw new UnsupportedOperationException();
        }

        public Placeholder getLocal(String key) {
            return placeholders.get(key);
        }

        public Placeholder get(String key) {
            return placeholders.get(key);
        }

        public CustomPlaceholderRegistry set(Placeholder placeholder) {
            throw new UnsupportedOperationException("Use PlaceholderUtil.register o PlacholderManager#register instead!");
        }

        public boolean has(String id) {
            return placeholders.containsKey(id);
        }


        public boolean hasLocal(String id) {
            return placeholders.containsKey(id);
        }
    }
}
