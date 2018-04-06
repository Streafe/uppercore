package xyz.upperlevel.uppercore.config;

import com.google.common.collect.ImmutableList;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.junit.Test;
import xyz.upperlevel.uppercore.util.Position;

import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ConfigTest {

    public static class ConfigLoaderExample {
        @ConfigConstructor
        public ConfigLoaderExample(@ConfigProperty(name = "str") String str,
                          @ConfigProperty(name = "count") int count,
                          @ConfigProperty(name = "enum") List<ItemFlag> flags,
                          @ConfigProperty(name = "type") Material type,
                          @ConfigProperty(name = "center") Position center,
                          @ConfigProperty(name = "center2") Position center2) {

            assertEquals("Stringa", str);
            assertEquals(129, count);
            assertEquals(ImmutableList.of(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES), flags);
            assertEquals(Material.getMaterial(55), type);
            assertEquals(new Position(15, 30, 60), center);
            assertEquals(new Position(1, 2, 3), center2);
        }
    }

    @Test
    public void basicTest() {
        ConfigParser.fromClass(ConfigLoaderExample.class)
                .parse(new StringReader(
                        "str: Stringa\n" +
                                "count: 129\n" +
                                "enum: [hide enchants, hide attributes]\n" +
                                "type: 55\n" +
                                "center: [15, 30, 60]\n" +
                                "center2:\n" +
                                "  x: 1\n" +
                                "  y: 2\n" +
                                "  z: 3\n"
                ));
    }
}
