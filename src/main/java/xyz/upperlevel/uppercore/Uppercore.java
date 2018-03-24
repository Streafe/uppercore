package xyz.upperlevel.uppercore;

import lombok.Getter;
import org.bstats.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.upperlevel.uppercore.database.StorageManager;
import xyz.upperlevel.uppercore.economy.EconomyManager;
import xyz.upperlevel.uppercore.gui.GuiManager;
import xyz.upperlevel.uppercore.placeholder.PlaceholderUtil;
import xyz.upperlevel.uppercore.placeholder.message.MessageManager;
import xyz.upperlevel.uppercore.registry.RegistryRoot;
import xyz.upperlevel.uppercore.script.ScriptManager;
import xyz.upperlevel.uppercore.update.DownloadableUpdateChecker;
import xyz.upperlevel.uppercore.update.SpigetUpdateChecker;
import xyz.upperlevel.uppercore.util.CrashUtil;

import java.io.File;
import java.util.logging.Logger;

@Getter
public class Uppercore extends JavaPlugin {
    public static final String SCRIPT_CONFIG = "script_engine.yml";
    public static final String SPIGOT_ID = "uppercore.45866";
    public static final long SPIGET_ID = 45866;

    private static Uppercore instance;

    private RegistryRoot registryRoot;
    private GuiManager guis;
    private ScriptManager scripts;
    private StorageManager storages;
    private MessageManager messages;
    private DownloadableUpdateChecker updater;

    private Metrics metrics;

    @Override
    public void onEnable() {
        instance = this;

        try {
            //Metrics setup
            metrics = new Metrics(this);
            //UpdateChecker setup
            updater = new SpigetUpdateChecker(this, SPIGOT_ID, SPIGET_ID);

            messages = MessageManager.load(this);

            PlaceholderUtil.tryHook();
            EconomyManager.enable();

            // MANAGER
            guis = new GuiManager();
            scripts = new ScriptManager();
            storages = new StorageManager();

            //ScriptManager setup
            File scriptsConfigFile = new File(getDataFolder(), SCRIPT_CONFIG);
            if (!scriptsConfigFile.exists())
                saveResource(SCRIPT_CONFIG, false);
            scripts.load(new File(getDataFolder(), "engines"), scriptsConfigFile);

            //Metrics custom data setup
            scripts.setupMetrics(metrics);
        } catch (Throwable t) {
            CrashUtil.saveCrash(this, t);
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
    }

    public File getFile() {
        return super.getFile();
    }

    public static Uppercore get() {
        return instance;
    }

    public static Logger logger() {
        return instance.getLogger();
    }

    public static RegistryRoot getRegistryRoot() {
        return instance.registryRoot;
    }

    public static GuiManager guis() {
        return instance.guis;
    }

    public static ScriptManager scripts() {
        return instance.scripts;
    }

    public static MessageManager messages() {
        return instance.messages;
    }

    public static StorageManager storages() {
        return instance.storages;
    }

}
