package xyz.upperlevel.uppercore.config;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.util.Vector;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import xyz.upperlevel.uppercore.config.exceptions.WrongValueConfigException;
import xyz.upperlevel.uppercore.sound.CompatibleSound;

import java.util.Arrays;

import static xyz.upperlevel.uppercore.config.ConfigParser.checkNodeTag;

public class StandardExternalDeclarator implements ConfigExternalDeclarator {
    @ConfigConstructor
    private Vector parsePosition(@ConfigProperty(name = "x") double x,
                                 @ConfigProperty(name = "y") double y,
                                 @ConfigProperty(name = "z") double z) {
        return new Vector(x, y, z);
    }

    @ConfigConstructor
    private Color parseColor(Node node) {// Raw constructor
        checkNodeTag(node, Tag.STR);
        try {
            return ConfigUtil.parseColor(((ScalarNode) node).getValue());
        } catch (Exception e) {
            throw new WrongValueConfigException(node, ((ScalarNode) node).getValue(), "r;g;b or hex color");
        }
    }

    @ConfigConstructor(inlineable = true)
    private Sound parseSound(String raw) {
        return CompatibleSound.get(raw);
    }

    @ConfigConstructor
    private Material parseMaterial(Node rawNode) {
        checkNodeTag(rawNode, Arrays.asList(Tag.STR, Tag.INT));
        ScalarNode node = (ScalarNode) rawNode;
        Material res;
        if (node.getTag() == Tag.INT) {
            res = Material.getMaterial(Integer.parseInt(node.getValue()));
        } else {// node.getTag() == Tag.STR
            res = Material.getMaterial(node.getValue().replace(' ', '_').toUpperCase());
        }
        if (res == null) {
            throw new WrongValueConfigException(node, node.getValue(), "Material");
        }
        return res;
    }

    @ConfigConstructor(inlineable = true)
    private Location parseLocation(@ConfigProperty(name = "world") String rawWorld,
                                   @ConfigProperty(name = "x") double x,
                                   @ConfigProperty(name = "y") double y,
                                   @ConfigProperty(name = "z") double z,
                                   @ConfigProperty(name = "yaw", optional = true) Float yaw,
                                   @ConfigProperty(name = "pitch", optional = true) Float pitch) {
        World world = Bukkit.getWorld(rawWorld);
        if (world == null) {
            throw new IllegalArgumentException("Cannot find world '" + rawWorld + "'");
        }
        return new Location(world, x, y, z, yaw != null ? yaw : 0.0f, pitch != null ? pitch : 0.0f);
    }

    @ConfigConstructor
    private Enchantment parseEnchantment(Node rawNode) {
        checkNodeTag(rawNode, Arrays.asList(Tag.STR, Tag.INT));
        ScalarNode node = (ScalarNode) rawNode;
        Enchantment res;
        if (node.getTag() == Tag.INT) {
            res = Enchantment.getById(Integer.parseInt(node.getValue()));
        } else {// node.getTag() == Tag.STR
            res = Enchantment.getByName(node.getValue().replace(' ', '_').toUpperCase());
        }
        if (res == null) {
            throw new WrongValueConfigException(node, node.getValue(), "Enchantment");
        }
        return res;
    }
}
