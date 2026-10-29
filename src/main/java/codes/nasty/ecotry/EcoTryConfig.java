package codes.nasty.ecotry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Settings in {@code config/ecotry.json}. Unknown keys are ignored and missing keys fall back to the defaults below,
 * so the file is rewritten on every successful load to pick up new options.
 */
public final class EcoTryConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(EcoTry.MOD_ID + ".json");
	private static EcoTryConfig instance = new EcoTryConfig();

	/**
	 * Whether take/give/has send the affected player a short message.
	 * A trailing {@code notify} or {@code silent} on the command overrides this for that one call.
	 */
	public boolean playerFeedback = false;
	/** Show the player message above the hotbar instead of in the chat. */
	public boolean actionBar = false;
	/**
	 * Language of the texts for players who don't have the mod installed ("en_us" or "de_de"), see {@link ServerLang}.
	 * Messages below that still have the default text of another language switch along.
	 */
	public String language = ServerLang.LANGUAGES.getFirst();
	public Messages messages = new Messages();

	/**
	 * Player messages. Supports {@code &} color codes and {@code &#RRGGBB}; an empty text is not sent.
	 * Placeholders: {player}, {amount}, {balance} (balance after the command).
	 * Missing texts get the default of {@link #language} (language files, keys {@code ecotry.message.*}).
	 */
	public static final class Messages {
		public String takeSuccess;
		public String takeFailure;
		public String giveSuccess;
		public String giveFailure;
		public String hasSuccess;
		public String hasFailure;
	}

	public static EcoTryConfig get() {
		return instance;
	}

	/** Loads the config at startup; a broken file is reported and the defaults are used instead. */
	public static void load() {
		try {
			reload();
		} catch (IOException | JsonParseException e) {
			EcoTry.LOGGER.error("Could not read {}, using default settings", PATH, e);
			apply(new EcoTryConfig());
		}
	}

	public static void reload() throws IOException {
		EcoTryConfig config = new EcoTryConfig();
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
				EcoTryConfig parsed = GSON.fromJson(reader, EcoTryConfig.class);
				if (parsed != null) {
					config = parsed;
				}
			}
		}
		apply(config);
		save(config);
	}

	/** Activates a config without touching the file (also used by the game tests). */
	public static void apply(EcoTryConfig config) {
		config.sanitize();
		instance = config;
		ServerLang.select(config.language);
	}

	/** Activates settings changed in the config screen and writes them to the file. */
	public static void update(EcoTryConfig config) {
		apply(config);
		save(config);
	}

	/** The default settings with every message filled in. */
	public static EcoTryConfig defaults() {
		EcoTryConfig config = new EcoTryConfig();
		config.sanitize();
		return config;
	}

	/** A deep copy, for the config screen to change. */
	public EcoTryConfig copy() {
		return GSON.fromJson(GSON.toJsonTree(this), EcoTryConfig.class);
	}

	private static void save(EcoTryConfig config) {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			EcoTry.LOGGER.error("Could not write {}", PATH, e);
		}
	}

	private void sanitize() {
		language = ServerLang.supported(language);
		if (messages == null) messages = new Messages();
		messages.takeSuccess = ServerLang.template(language, "ecotry.message.take_success", messages.takeSuccess);
		messages.takeFailure = ServerLang.template(language, "ecotry.message.take_failure", messages.takeFailure);
		messages.giveSuccess = ServerLang.template(language, "ecotry.message.give_success", messages.giveSuccess);
		messages.giveFailure = ServerLang.template(language, "ecotry.message.give_failure", messages.giveFailure);
		messages.hasSuccess = ServerLang.template(language, "ecotry.message.has_success", messages.hasSuccess);
		messages.hasFailure = ServerLang.template(language, "ecotry.message.has_failure", messages.hasFailure);
	}
}
