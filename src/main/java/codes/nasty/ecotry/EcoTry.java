package codes.nasty.ecotry;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EcoTry implements ModInitializer {
	public static final String MOD_ID = "ecotry";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		EcoTryConfig.load();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> EcoTryCommand.register(dispatcher));
	}
}
