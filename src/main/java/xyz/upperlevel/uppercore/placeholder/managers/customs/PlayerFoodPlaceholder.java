package xyz.upperlevel.uppercore.placeholder.managers.customs;

import org.bukkit.entity.Player;
import xyz.upperlevel.uppercore.placeholder.Placeholder;

public class PlayerFoodPlaceholder implements Placeholder {

    @Override
    public String getId() {
        return "player_food_level";
    }

    @Override
    public String resolve(Player player, String id) {
        return Float.toString(player.getFoodLevel());
    }
}
