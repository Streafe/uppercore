package xyz.upperlevel.uppercore.nms;

import org.bukkit.Bukkit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NmsVersion {
    public static final String VERSION;
    public static final int MAJOR;
    public static final int MINOR;
    public static final int RELEASE;

    static {
        VERSION = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        Pattern pattern = Pattern.compile("v([0-9]+)_([0-9]+)_R([0-9]+)");
        Matcher m = pattern.matcher(VERSION);
        if (!m.matches()) {
            throw new IllegalStateException("Cannot parse version \"" + VERSION + "\", make sure it follows \"v<major>_<minor>...\"");
        }
        MAJOR = Integer.parseInt(m.group(1));
        MINOR = Integer.parseInt(m.group(2));
        RELEASE = Integer.parseInt(m.group(3));
    }

}
