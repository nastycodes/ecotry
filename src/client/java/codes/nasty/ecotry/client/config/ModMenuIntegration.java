package codes.nasty.ecotry.client.config;

import codes.nasty.ecotry.EcoTry;
import codes.nasty.ecotry.EcoTryConfig;
import codes.nasty.ecotry.ServerLang;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The config button in Mod Menu's mod list. Mod Menu is optional; without it this class is never loaded. */
public class ModMenuIntegration implements ModMenuApi {
	private static final int MESSAGE_LENGTH = 256;

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ModMenuIntegration::createScreen;
	}

	public static Screen createScreen(Screen parent) {
		return new ConfigScreen<>(EcoTry.MOD_ID, parent, EcoTryConfig.get().copy(), EcoTryConfig.defaults(), EcoTryConfig::update)
			.section("general")
			.toggle("playerFeedback", c -> c.playerFeedback, (c, v) -> c.playerFeedback = v)
			.toggle("actionBar", c -> c.actionBar, (c, v) -> c.actionBar = v)
			.choice("language", ServerLang.LANGUAGES, code -> Component.literal(ServerLang.text(code, "ecotry.language.name")),
				c -> c.language, (c, v) -> c.language = v)
			.section("messages")
			.text("messages.takeSuccess", MESSAGE_LENGTH, c -> c.messages.takeSuccess, (c, v) -> c.messages.takeSuccess = v)
			.text("messages.takeFailure", MESSAGE_LENGTH, c -> c.messages.takeFailure, (c, v) -> c.messages.takeFailure = v)
			.text("messages.giveSuccess", MESSAGE_LENGTH, c -> c.messages.giveSuccess, (c, v) -> c.messages.giveSuccess = v)
			.text("messages.giveFailure", MESSAGE_LENGTH, c -> c.messages.giveFailure, (c, v) -> c.messages.giveFailure = v)
			.text("messages.hasSuccess", MESSAGE_LENGTH, c -> c.messages.hasSuccess, (c, v) -> c.messages.hasSuccess = v)
			.text("messages.hasFailure", MESSAGE_LENGTH, c -> c.messages.hasFailure, (c, v) -> c.messages.hasFailure = v);
	}
}
