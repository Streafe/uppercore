package xyz.upperlevel.uppercore.placeholder.managers.customs;

import org.bukkit.entity.Player;
import xyz.upperlevel.uppercore.placeholder.Placeholder;

public class PlayerLevelPlaceholder implements Placeholder {

    @Override
    public String getId() {
        return "player_level";
    }

    @Override
    public String resolve(Player player, String id) {
        return Integer.toString(player.getLevel());
    }
}
