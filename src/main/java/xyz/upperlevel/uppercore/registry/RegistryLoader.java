package xyz.upperlevel.uppercore.registry;

import xyz.upperlevel.uppercore.config.Config;

public interface RegistryLoader<T> {
    T load (Registry parent, String id, Config config);
}
