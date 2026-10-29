package codes.nasty.ecotry;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import me.andy.ecobal.api.EconomyManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /ecotry take|give|has <targets> <amount> [notify|silent]}, {@code /ecotry balance <player>} and {@code /ecotry reload}.
 * <p>
 * Unlike EcoBal's own {@code /eco take}, a command that changes nothing throws, and only a thrown error counts as
 * failed for Minecraft: {@code execute store success} stores 0, conditional chain command blocks don't fire and
 * comparators stay off. With several targets every player is handled on its own; the result is the number of
 * players it worked for, and the command only fails when it worked for nobody.
 */
public final class EcoTryCommand {
	/** Smallest amount, same as EcoBal's /eco and /pay. */
	private static final double MIN_AMOUNT = 0.01;

	private enum Action {
		TAKE, GIVE, HAS
	}

	private EcoTryCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal(EcoTry.MOD_ID)
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(moneyCommand("take", Action.TAKE))
			.then(moneyCommand("give", Action.GIVE))
			.then(moneyCommand("has", Action.HAS))
			.then(Commands.literal("balance")
				.then(Commands.argument("player", EntityArgument.player())
					.executes(EcoTryCommand::balance)))
			.then(Commands.literal("reload")
				.executes(EcoTryCommand::reload)));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> moneyCommand(String name, Action action) {
		return Commands.literal(name)
			.then(Commands.argument("targets", EntityArgument.players())
				.then(Commands.argument("amount", DoubleArgumentType.doubleArg(MIN_AMOUNT, Double.MAX_VALUE))
					.executes(context -> run(context, action, EcoTryConfig.get().playerFeedback))
					.then(Commands.literal("notify").executes(context -> run(context, action, true)))
					.then(Commands.literal("silent").executes(context -> run(context, action, false)))));
	}

	private static int run(CommandContext<CommandSourceStack> context, Action action, boolean notify) throws CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
		double amount = DoubleArgumentType.getDouble(context, "amount");
		int succeeded = 0;
		for (ServerPlayer player : targets) {
			boolean success = apply(action, player, amount);
			if (success) succeeded++;
			if (notify) notifyPlayer(player, action, success, amount);
		}

		Component message = targets.size() == 1
			? singleMessage(action, succeeded > 0, targets.iterator().next(), amount)
			: multiMessage(action, succeeded, targets.size(), amount);
		if (succeeded == 0) throw failure(message);
		context.getSource().sendSuccess(() -> message, false);
		return succeeded;
	}

	/** Does the action for one player; any EcoBal error counts as "did not work" instead of escaping the command. */
	private static boolean apply(Action action, ServerPlayer player, double amount) {
		UUID id = player.getUUID();
		try {
			EconomyManager.updatePlayerNameCache(id, name(player));
			return switch (action) {
				case TAKE -> EconomyManager.silentWithdraw(id, amount);
				case GIVE -> {
					// silentDeposit returns nothing, so check that the money actually arrived.
					double before = EconomyManager.getBalance(id);
					EconomyManager.silentDeposit(id, amount);
					yield EconomyManager.getBalance(id) > before;
				}
				// Same comparison as EcoBal's withdraw, so "has" and "take" always agree.
				case HAS -> EconomyManager.getBalance(id) >= amount;
			};
		} catch (RuntimeException e) {
			EcoTry.LOGGER.error("EcoBal {} of {} for {} failed", action, amount, name(player), e);
			return false;
		}
	}

	private static int balance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(context, "player");
		double balance = balanceOf(player);
		if (Double.isNaN(balance)) throw failure(ServerLang.translatable("ecotry.command.balance.failure", name(player)));
		context.getSource().sendSuccess(() -> ServerLang.translatable("ecotry.command.balance", name(player), format(balance)), false);
		// Rounded down, so 99.99 never passes a "matches 100.." check.
		return (int) Math.min(Math.floor(balance), Integer.MAX_VALUE);
	}

	private static int reload(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		try {
			EcoTryConfig.reload();
		} catch (Exception e) {
			throw failure(ServerLang.translatable("ecotry.command.reload_failed", e.getMessage()));
		}
		context.getSource().sendSuccess(() -> ServerLang.translatable("ecotry.command.reloaded"), true);
		return 1;
	}

	private static void notifyPlayer(ServerPlayer player, Action action, boolean success, double amount) {
		EcoTryConfig config = EcoTryConfig.get();
		String template = switch (action) {
			case TAKE -> success ? config.messages.takeSuccess : config.messages.takeFailure;
			case GIVE -> success ? config.messages.giveSuccess : config.messages.giveFailure;
			case HAS -> success ? config.messages.hasSuccess : config.messages.hasFailure;
		};
		if (template.isEmpty()) return;
		Map<String, String> placeholders = Map.of("player", name(player), "amount", format(amount), "balance", format(balanceOf(player)));
		player.sendSystemMessage(TextFormat.parse(TextFormat.fill(template, placeholders)), config.actionBar);
	}

	private static Component singleMessage(Action action, boolean success, ServerPlayer player, double amount) {
		String name = name(player);
		String money = format(amount);
		String balance = format(balanceOf(player));
		return switch (action) {
			case TAKE -> success
				? ServerLang.translatable("ecotry.command.take.success", money, name, balance)
				: ServerLang.translatable("ecotry.command.not_enough", name, money, balance);
			case GIVE -> success
				? ServerLang.translatable("ecotry.command.give.success", money, name, balance)
				: ServerLang.translatable("ecotry.command.give.failure", money, name);
			case HAS -> ServerLang.translatable(success ? "ecotry.command.has.success" : "ecotry.command.not_enough", name, money, balance);
		};
	}

	private static Component multiMessage(Action action, int succeeded, int total, double amount) {
		String money = format(amount);
		if (succeeded == 0) {
			return action == Action.GIVE
				? ServerLang.translatable("ecotry.command.give.failure.multi", money, total)
				: ServerLang.translatable("ecotry.command.not_enough.multi", total, money);
		}
		return switch (action) {
			case TAKE -> ServerLang.translatable("ecotry.command.take.multi", succeeded, total, money);
			case GIVE -> ServerLang.translatable("ecotry.command.give.multi", succeeded, total, money);
			case HAS -> ServerLang.translatable("ecotry.command.has.multi", succeeded, total, money);
		};
	}

	/** EcoBal balance, or NaN when EcoBal fails (an exception would leave "execute store" targets untouched). */
	private static double balanceOf(ServerPlayer player) {
		try {
			return EconomyManager.getBalance(player.getUUID());
		} catch (RuntimeException e) {
			EcoTry.LOGGER.error("Could not read the EcoBal balance of {}", name(player), e);
			return Double.NaN;
		}
	}

	/** Money in EcoBal's format (currency symbol, two decimals). */
	private static String format(double amount) {
		if (Double.isNaN(amount)) return "?";
		try {
			return EconomyManager.formatBalance(amount);
		} catch (RuntimeException e) {
			return String.format("%,.2f", amount);
		}
	}

	private static String name(ServerPlayer player) {
		return player.getGameProfile().name();
	}

	private static CommandSyntaxException failure(Component message) {
		return new SimpleCommandExceptionType(message).create();
	}
}
