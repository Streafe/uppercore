package xyz.upperlevel.uppercore.gui.config.action.actions;

import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import xyz.upperlevel.uppercore.Uppercore;
import xyz.upperlevel.uppercore.gui.Gui;
import xyz.upperlevel.uppercore.gui.GuiSystem;
import xyz.upperlevel.uppercore.gui.config.action.Action;
import xyz.upperlevel.uppercore.gui.config.action.BaseActionType;
import xyz.upperlevel.uppercore.gui.config.action.Parser;
import xyz.upperlevel.uppercore.placeholder.PlaceholderUtil;
import xyz.upperlevel.uppercore.placeholder.PlaceholderValue;

import java.util.Map;

public class GuiChangeAction extends Action<GuiChangeAction> {

    public static final GuiChangeActionType TYPE = new GuiChangeActionType();

    @Getter
    private final PlaceholderValue<String> guiId;

    public GuiChangeAction(Plugin plugin, PlaceholderValue<String> guiId) {
        super(plugin, TYPE);
        this.guiId = guiId;
    }

    @Override
    public void run(Player player) {
        String guiId = this.guiId.get(player);
        Gui gui = GuiSystem.get(guiId);
        if (gui == null) {
            Uppercore.logger().severe("Cannot find gui \"" + guiId + "\"");
            return;
        }

        GuiSystem.change(player, gui);
    }


    public static class GuiChangeActionType extends BaseActionType<GuiChangeAction> {

        public GuiChangeActionType() {
            super("change-gui");
            setParameters(
                    Parameter.of("id", Parser.strValue(), true)
            );
        }

        @Override
        public GuiChangeAction create(Plugin plugin, Map<String, Object> pars) {
            return new GuiChangeAction(
                    plugin,
                    PlaceholderUtil.process((String) pars.get("id"))
            );
        }

        @Override
        public Map<String, Object> read(GuiChangeAction action) {
            return ImmutableMap.of(
                    "id", action.guiId.toString()
            );
        }
    }
}