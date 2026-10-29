package codes.nasty.ecotry.test;

import codes.nasty.ecotry.EcoTry;
import codes.nasty.ecotry.EcoTryConfig;
import codes.nasty.ecotry.client.config.ConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.terraformersmc.modmenu.ModMenu;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import me.andy.ecobal.api.EconomyManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.CommandBlockEditScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * A shop button built from command blocks in a real client (English, creative, 1920x1080): the first press buys a
 * diamond with /ecotry take, the second one fails for lack of money and the chain takes the "else" branch. Then the
 * settings are changed through the screen behind Mod Menu's config button.
 * Run with {@code gradlew runClientGameTest}; screenshots land in build/run/clientGameTest/screenshots.
 */
public class EcoTryClientGameTest implements FabricClientGameTest {
	private static final String TAKE = "execute store success score #result eco_ok run ecotry take @p 100 notify";
	private static final String SUCCESS = "execute if score #result eco_ok matches 1 run give @p minecraft:diamond";
	private static final String FAILURE = "execute if score #result eco_ok matches 0 run tellraw @p {\"text\":\"Not enough money for a diamond!\",\"color\":\"red\"}";

	/** The impulse command block; set on the server thread before anything reads it. */
	private static volatile BlockPos shop;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1920, 1080);
		context.runOnClient(client -> client.getLanguageManager().setSelected("en_us"));
		context.runOnClient(client -> client.reloadResourcePacks());
		context.waitFor(client -> client.getOverlay() == null);

		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.setAllowCommands(true))
			.create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("scoreboard objectives add eco_ok dummy");
			server.runOnServer(EcoTryClientGameTest::buildShop);
			context.waitTicks(20);

			// Right-clicking the impulse block shows the command, as an operator in creative mode.
			lookAt(server, Direction.SOUTH);
			context.waitTicks(5);
			clearHud(context);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(5);
			context.waitForScreen(CommandBlockEditScreen.class);
			context.waitTicks(10);
			screenshot(context, "ecotry_01_command_block");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(null);

			// First press: 150 money is enough, the diamond comes from the "then" branch.
			lookAt(server, null);
			context.waitTicks(5);
			clearHud(context);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(30);
			assertBalance(server, 50.0, "after buying the diamond");
			assertTrue(server.computeOnServer(s -> player(s).getInventory().countItem(Items.DIAMOND)) == 1, "the chain gave one diamond");
			screenshot(context, "ecotry_02_bought");

			// Second press: 50 left, take fails, nothing is taken and the "else" branch speaks up.
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(30);
			assertBalance(server, 50.0, "after the failed purchase");
			assertTrue(server.computeOnServer(s -> player(s).getInventory().countItem(Items.DIAMOND)) == 1, "no second diamond");
			screenshot(context, "ecotry_03_not_enough_money");

			checkConfigScreen(context, server);
			EcoTry.LOGGER.info("[gametest] all checks passed");
		}
	}

	/**
	 * A wall with a button on an impulse command block and two chain command blocks behind it (then/else),
	 * a sign with the price above. The player stands in front with 150 money.
	 */
	private static void buildShop(MinecraftServer server) {
		ServerPlayer player = player(server);
		player.setGameMode(GameType.CREATIVE);
		Inventory inventory = player.getInventory();
		inventory.clearContent();
		inventory.setSelectedSlot(0);
		UUID id = player.getUUID();
		EconomyManager.updatePlayerNameCache(id, player.getGameProfile().name());
		EconomyManager.setBalance(id, 150.0);

		ServerLevel level = server.overworld();
		level.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
		level.getGameRules().set(GameRules.COMMAND_BLOCK_OUTPUT, false, server);
		level.getGameRules().set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false, server);
		BlockPos feet = player.blockPosition();
		shop = feet.east(4).above();
		for (int y = -1; y <= 2; y++) {
			for (int z = -2; z <= 2; z++) {
				level.setBlockAndUpdate(shop.offset(0, y, z), Blocks.POLISHED_ANDESITE.defaultBlockState());
			}
		}
		// Chain: impulse at the wall, then two chain blocks going east (away from the player).
		level.setBlockAndUpdate(shop, Blocks.COMMAND_BLOCK.defaultBlockState().setValue(CommandBlock.FACING, Direction.EAST));
		level.setBlockAndUpdate(shop.east(), Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState().setValue(CommandBlock.FACING, Direction.EAST));
		level.setBlockAndUpdate(shop.east(2), Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState().setValue(CommandBlock.FACING, Direction.EAST));
		command(level, shop, TAKE, false);
		command(level, shop.east(), SUCCESS, true);
		command(level, shop.east(2), FAILURE, true);
		level.setBlockAndUpdate(shop.west(), Blocks.STONE_BUTTON.defaultBlockState()
			.setValue(ButtonBlock.FACE, AttachFace.WALL)
			.setValue(ButtonBlock.FACING, Direction.WEST));

		// A second impulse block next to it, for a look at the command inside.
		BlockPos display = shop.south(2);
		level.setBlockAndUpdate(display, Blocks.COMMAND_BLOCK.defaultBlockState().setValue(CommandBlock.FACING, Direction.EAST));
		command(level, display, TAKE, false);

		BlockPos signPos = shop.above().west();
		level.setBlockAndUpdate(signPos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.WEST));
		if (level.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
			sign.setText(new SignText()
				.setMessage(0, Component.literal("Diamond"))
				.setMessage(1, Component.literal("100 $"))
				.setMessage(2, Component.literal("press the button")), true);
		}
		player.connection.teleport(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, -90.0F, 20.0F);
	}

	private static void command(ServerLevel level, BlockPos pos, String command, boolean alwaysActive) {
		CommandBlockEntity block = (CommandBlockEntity) level.getBlockEntity(pos);
		block.getCommandBlock().setCommand(command);
		block.setAutomatic(alwaysActive);
		block.setChanged();
	}

	/** Empties the chat and the toasts, so only what the next step prints is on the screenshot. */
	private static void clearHud(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.getToastManager().clear();
			client.gui.getChat().clearMessages(false);
		});
	}

	/** A screenshot without toasts (recipe unlocked, advancements) covering the picture. */
	private static void screenshot(ClientGameTestContext context, String name) {
		context.runOnClient(client -> client.getToastManager().clear());
		context.waitTicks(2);
		context.takeScreenshot(name);
	}

	/**
	 * Turns the player towards the button (null) or the display command block (south). Aims at the middle of the
	 * west face, so the crosshair never grazes the wall block next to the target.
	 */
	private static void lookAt(TestServerContext server, Direction towards) {
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			BlockPos target = towards == null ? shop : shop.south(2);
			double dx = target.getX() - player.getX();
			double dy = target.getY() + 0.5 - player.getEyeY();
			double dz = target.getZ() + 0.5 - player.getZ();
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
			player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, pitch);
		});
	}

	/** Switches the server language and the player feedback in the settings screen, saves and checks the result. */
	private static void checkConfigScreen(ClientGameTestContext context, TestServerContext server) {
		context.setScreen(() -> ModMenu.getConfigScreen(EcoTry.MOD_ID, null));
		context.waitForScreen(ConfigScreen.class);
		context.waitTicks(5);
		CycleButton<?> feedback = widget(context, CycleButton.class, "options.on", "options.off", 0);
		hoverWidget(context, feedback);
		context.takeScreenshot("ecotry_04_config");

		// Player feedback on (first toggle), language German (the third button in the list).
		clickWidget(context, feedback);
		CycleButton<?> language = widget(context, CycleButton.class, null, null, 2);
		assertTrue(language.getValue().equals("en_us"), "the language starts as English");
		clickWidget(context, language);
		assertTrue(language.getValue().equals("de_de"), "one click switches to German");
		context.clickScreenButton("gui.done");
		context.waitForScreen(null);
		context.waitTicks(10);

		EcoTryConfig config = server.computeOnServer(s -> EcoTryConfig.get());
		assertTrue(config.playerFeedback && config.language.equals("de_de"), "feedback and language saved through the screen");
		assertTrue(config.messages.takeSuccess.contains("Kontostand"), "unchanged messages switched to German, got " + config.messages.takeSuccess);
		String file = readConfigFile();
		assertTrue(file.contains("\"language\": \"de_de\"") && file.contains("Kontostand"), "the screen wrote config/ecotry.json");

		// Esc with unsaved changes asks first; discarding keeps the saved values.
		context.setScreen(() -> ModMenu.getConfigScreen(EcoTry.MOD_ID, null));
		context.waitForScreen(ConfigScreen.class);
		EditBox message = widget(context, EditBox.class, "config.ecotry.messages.takeSuccess", null, 0);
		context.runOnClient(client -> message.setValue("&a-{amount}"));
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(ConfirmScreen.class);
		context.clickScreenButton("config.ecotry.ui.discard.yes");
		context.waitForScreen(null);
		context.waitTicks(5);
		assertTrue(server.computeOnServer(s -> EcoTryConfig.get().messages.takeSuccess).contains("Kontostand"), "discarded changes are not saved");

		server.runOnServer(s -> EcoTryConfig.update(EcoTryConfig.defaults()));
	}

	private static String readConfigFile() {
		try {
			return Files.readString(FabricLoader.getInstance().getConfigDir().resolve(EcoTry.MOD_ID + ".json"));
		} catch (IOException e) {
			throw new AssertionError("config file not readable", e);
		}
	}

	/**
	 * The {@code index}-th widget of the current screen (also inside lists, in screen order) whose message has one of
	 * the translation keys; with no key, the {@code index}-th widget of that type.
	 */
	private static <T extends AbstractWidget> T widget(ClientGameTestContext context, Class<T> type, String key, String otherKey, int index) {
		return context.computeOnClient(client -> {
			List<T> found = new ArrayList<>();
			collect(client.screen.children(), type, key, otherKey, found);
			if (found.size() <= index) throw new AssertionError("only " + found.size() + " " + type.getSimpleName() + " with message " + key);
			return found.get(index);
		});
	}

	private static <T extends AbstractWidget> void collect(List<? extends GuiEventListener> children, Class<T> type, String key, String otherKey, List<T> found) {
		for (GuiEventListener child : children) {
			if (type.isInstance(child)) {
				String childKey = ((AbstractWidget) child).getMessage().getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
				if (key == null || key.equals(childKey) || (otherKey != null && otherKey.equals(childKey))) {
					found.add(type.cast(child));
				}
			}
			if (child instanceof ContainerEventHandler container) {
				collect(container.children(), type, key, otherKey, found);
			}
		}
	}

	private static void hoverWidget(ClientGameTestContext context, AbstractWidget widget) {
		double[] cursor = context.computeOnClient(client -> {
			double scale = (double) client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
			return new double[] {(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
		});
		context.getInput().setCursorPos(cursor[0], cursor[1]);
		context.waitTicks(3);
	}

	private static void clickWidget(ClientGameTestContext context, AbstractWidget widget) {
		hoverWidget(context, widget);
		context.getInput().pressMouse(0);
		context.waitTicks(3);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void assertBalance(TestServerContext server, double expected, String when) {
		double actual = server.computeOnServer(s -> EconomyManager.getBalance(player(s).getUUID()));
		assertTrue(Math.abs(actual - expected) < 0.001, "balance " + when + ": expected " + expected + ", got " + actual);
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
