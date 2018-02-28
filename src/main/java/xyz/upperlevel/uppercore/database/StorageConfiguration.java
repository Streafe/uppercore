package xyz.upperlevel.uppercore.database;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.Plugin;
import xyz.upperlevel.uppercore.config.Config;
import xyz.upperlevel.uppercore.config.ConfigUtil;

import java.io.File;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class StorageConfiguration {
    private static final String CONFIG_FILENAME = "storage.yml";

    @Getter
    private Storage storage;

    @Getter
    private Config config;

    @Getter
    private Plugin plugin;

    public StorageConfiguration(Storage storage, Config config, Plugin plugin) {
        this.storage = storage;
        this.config = config;
        this.plugin = plugin;
    }

    public Database connect() {
        if (!storage.isSupported()) {
            storage.download();
        }
        return storage.connect(
                config.getString("host"),
                config.getInt("port", storage.getDefaultPort()),
                config.getString("database", plugin.getName()),
                config.getString("username"),
                config.getString("password")
        );
    }

    public static StorageConfiguration load(StorageManager storageManager, Plugin plugin) {
        Logger logger = plugin.getLogger();
        File file = new File(plugin.getDataFolder(), CONFIG_FILENAME);
        if (!file.exists()) {
            logger.info(CONFIG_FILENAME + " does not exist");
            if (plugin.getResource(CONFIG_FILENAME) != null) {
                plugin.saveResource(CONFIG_FILENAME, false);
                logger.info(CONFIG_FILENAME + " extracted from jar");
            } else {
                logger.severe("Cannot find resource " + CONFIG_FILENAME + " in jar");
            }
        }
        Config config = Config.wrap(ConfigUtil.loadConfig(file)).getConfigRequired("storage");
        String storageName = config.getStringRequired("type");
        Storage storage = storageManager.getStorage(storageName);
        if (storage == null) {
            throw new IllegalArgumentException("No storage found for: " + storageName);
        }
        return new StorageConfiguration(storage, config, plugin);
    }
}
