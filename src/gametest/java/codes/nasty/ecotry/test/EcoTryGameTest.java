package codes.nasty.ecotry.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import me.andy.ecobal.api.EconomyManager;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

/** Runs /ecotry the way command blocks and functions do and checks what "execute store" sees. */
public class EcoTryGameTest {
	private static final String OBJECTIVE = "ecotry_test";

	@GameTest
	public void takeFailsWhenTheBalanceIsTooLow(GameTestHelper helper) {
		ServerPlayer player = fakePlayer(helper, "et_take");
		EconomyManager.setBalance(player.getUUID(), 150.0);

		helper.assertValueEqual(store(helper, "success", "ecotry take et_take 100"), 1, "First take should succeed");
		assertBalance(helper, player, 50.0);
		helper.assertValueEqual(store(helper, "success", "ecotry take et_take 100"), 0, "Take with too little money should fail");
		helper.assertValueEqual(store(helper, "result", "ecotry take et_take 100"), 0, "Failed take should return 0");
		assertBalance(helper, player, 50.0);
		helper.assertValueEqual(store(helper, "result", "ecotry take et_take 50 silent"), 1, "Taking the exact balance should succeed");
		assertBalance(helper, player, 0.0);

		removePlayer(player);
		helper.succeed();
	}

	@GameTest
	public void giveAddsMoney(GameTestHelper helper) {
		ServerPlayer player = fakePlayer(helper, "et_give");
		EconomyManager.setBalance(player.getUUID(), 10.0);

		helper.assertValueEqual(store(helper, "success", "ecotry give et_give 2.5"), 1, "Give should succeed");
		helper.assertValueEqual(store(helper, "result", "ecotry give @a[name=et_give] 7.5 notify"), 1, "Give with notify should succeed");
		assertBalance(helper, player, 20.0);

		removePlayer(player);
		helper.succeed();
	}

	@GameTest
	public void hasChecksWithoutTakingMoney(GameTestHelper helper) {
		ServerPlayer player = fakePlayer(helper, "et_has");
		EconomyManager.setBalance(player.getUUID(), 50.0);

		helper.assertValueEqual(store(helper, "success", "ecotry has et_has 50"), 1, "has 50 with 50 should succeed");
		helper.assertValueEqual(store(helper, "success", "ecotry has et_has 50.01 notify"), 0, "has 50.01 with 50 should fail");
		assertBalance(helper, player, 50.0);

		removePlayer(player);
		helper.succeed();
	}

	@GameTest
	public void balanceIsTheRoundedDownResult(GameTestHelper helper) {
		ServerPlayer player = fakePlayer(helper, "et_bal");
		EconomyManager.setBalance(player.getUUID(), 123.99);

		helper.assertValueEqual(store(helper, "result", "ecotry balance et_bal"), 123, "Balance 123.99 should be stored as 123");
		EconomyManager.setBalance(player.getUUID(), 0.0);
		helper.assertValueEqual(store(helper, "result", "ecotry balance et_bal"), 0, "Empty balance should be stored as 0");
		helper.assertValueEqual(store(helper, "success", "ecotry balance et_bal"), 1, "Reading an empty balance still succeeds");

		removePlayer(player);
		helper.succeed();
	}

	@GameTest
	public void severalTargetsCountSuccessfulPlayers(GameTestHelper helper) {
		ServerPlayer rich = fakePlayer(helper, "et_rich");
		ServerPlayer poor = fakePlayer(helper, "et_poor");
		rich.addTag("et_multi");
		poor.addTag("et_multi");
		EconomyManager.setBalance(rich.getUUID(), 200.0);
		EconomyManager.setBalance(poor.getUUID(), 50.0);

		helper.assertValueEqual(store(helper, "result", "ecotry take @a[tag=et_multi] 100"), 1, "Only the rich player can pay");
		assertBalance(helper, rich, 100.0);
		assertBalance(helper, poor, 50.0);
		helper.assertValueEqual(store(helper, "result", "ecotry has @a[tag=et_multi] 50"), 2, "Both players have 50");
		helper.assertValueEqual(store(helper, "result", "ecotry give @a[tag=et_multi] 1"), 2, "Give pays both");
		helper.assertValueEqual(store(helper, "success", "ecotry take @a[tag=et_multi] 500"), 0, "Nobody can pay 500");
		helper.assertValueEqual(store(helper, "success", "ecotry take @a[tag=et_nobody] 1"), 0, "No matching player is a failure");

		removePlayer(rich);
		removePlayer(poor);
		helper.succeed();
	}

	@GameTest
	public void commandBlocksOnlyCountSuccess(GameTestHelper helper) {
		ServerPlayer player = fakePlayer(helper, "et_block");
		EconomyManager.setBalance(player.getUUID(), 100.0);

		BaseCommandBlock first = runCommandBlock(helper, new BlockPos(1, 1, 1), "ecotry take @p[name=et_block] 60");
		helper.assertValueEqual(first.getSuccessCount(), 1, "Command block take with enough money");
		BaseCommandBlock second = runCommandBlock(helper, new BlockPos(3, 1, 1), "ecotry take @p[name=et_block] 60");
		helper.assertValueEqual(second.getSuccessCount(), 0, "Command block take with too little money");
		helper.assertTrue(second.getLastOutput().getString().contains("not have enough money"),
			"The command block should show the error, got: " + second.getLastOutput().getString());
		assertBalance(helper, player, 40.0);

		removePlayer(player);
		helper.succeed();
	}

	@GameTest
	public void worksFromFunctions(GameTestHelper helper) {
		ServerPlayer player = fakePlayer(helper, "et_func");
		EconomyManager.setBalance(player.getUUID(), 50.0);
		MinecraftServer server = helper.getLevel().getServer();
		ensureObjective(server);

		run(server, "scoreboard players set #et_func " + OBJECTIVE + " -1");
		run(server, "function ecotry-gametest:take");
		helper.assertValueEqual(score(helper, "#et_func"), 1, "Function take with enough money");
		run(server, "scoreboard players set #et_func " + OBJECTIVE + " -1");
		run(server, "function ecotry-gametest:take");
		helper.assertValueEqual(score(helper, "#et_func"), 0, "Function take with too little money");
		assertBalance(helper, player, 20.0);

		removePlayer(player);
		helper.succeed();
	}

	/**
	 * Runs {@code execute store <mode> score <holder> ecotry_test run <command>} from the console and returns the stored score.
	 * The score is set to -1 first, so a store that never happened shows up as -1.
	 */
	private static int store(GameTestHelper helper, String mode, String command) {
		MinecraftServer server = helper.getLevel().getServer();
		ensureObjective(server);
		String holder = "#store";
		run(server, "scoreboard players set " + holder + " " + OBJECTIVE + " -1");
		run(server, "execute store " + mode + " score " + holder + " " + OBJECTIVE + " run " + command);
		return score(helper, holder);
	}

	private static int score(GameTestHelper helper, String holder) {
		Scoreboard scoreboard = helper.getLevel().getServer().getScoreboard();
		return scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(holder), scoreboard.getObjective(OBJECTIVE)).value();
	}

	private static void ensureObjective(MinecraftServer server) {
		Objective objective = server.getScoreboard().getObjective(OBJECTIVE);
		if (objective == null) run(server, "scoreboard objectives add " + OBJECTIVE + " dummy");
	}

	private static void run(MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
	}

	/** Places a command block, runs it once like a redstone pulse would and returns its state. */
	private static BaseCommandBlock runCommandBlock(GameTestHelper helper, BlockPos pos, String command) {
		helper.setBlock(pos, Blocks.COMMAND_BLOCK);
		BaseCommandBlock block = helper.getBlockEntity(pos, CommandBlockEntity.class).getCommandBlock();
		block.setCommand(command);
		helper.assertTrue(block.performCommand(helper.getLevel()), "Command block did not run (command blocks disabled?)");
		return block;
	}

	private static ServerPlayer fakePlayer(GameTestHelper helper, String name) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		BlockPos standing = helper.absolutePos(new BlockPos(2, 1, 2));
		player.snapTo(standing.getX() + 0.5, standing.getY(), standing.getZ() + 0.5);
		return player;
	}

	private static void removePlayer(ServerPlayer player) {
		player.level().getServer().getPlayerList().remove(player);
	}

	private static void assertBalance(GameTestHelper helper, ServerPlayer player, double expected) {
		double actual = EconomyManager.getPlayerBalance(player.getUUID());
		helper.assertTrue(Math.abs(actual - expected) < 0.001, "Expected balance " + expected + " but was " + actual);
	}
}
