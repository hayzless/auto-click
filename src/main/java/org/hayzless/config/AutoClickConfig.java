package org.hayzless.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.hayzless.AutoClick;
import org.hayzless.input.ClickScheduler;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** The mod's on-disk settings. Every field is clamped on load so a hand-edited file cannot misbehave. */
public final class AutoClickConfig {
	public static final int MIN_CPS = 1;
	public static final int MAX_CPS = 100;
	public static final int MIN_CLICKS_PER_TICK = 1;
	public static final int MAX_CLICKS_PER_TICK = 6;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("auto-click.json");

	public boolean enabled = true;
	public boolean leftClick = true;
	public boolean rightClick = true;
	public int cpsMin = 10;
	public int cpsMax = 13;
	public int clicksPerTick = 5;
	/** When set, right-click use stays inside vanilla's 4 tick cooldown (about 5 uses per second). */
	public boolean respectUseCooldown = false;

	public static AutoClickConfig load() {
		if (Files.isRegularFile(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				AutoClickConfig config = GSON.fromJson(reader, AutoClickConfig.class);
				if (config != null) {
					config.sanitise();
					// Rewrite so a file from an older build drops fields that no longer exist.
					config.save();
					return config;
				}
			} catch (IOException | RuntimeException exception) {
				AutoClick.LOGGER.warn("Could not read {}, falling back to defaults", PATH, exception);
			}
		}

		AutoClickConfig config = new AutoClickConfig();
		config.save();
		return config;
	}

	public void save() {
		sanitise();

		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException exception) {
			AutoClick.LOGGER.warn("Could not write {}", PATH, exception);
		}
	}

	public void sanitise() {
		cpsMin = clamp(cpsMin, MIN_CPS, MAX_CPS);
		cpsMax = clamp(cpsMax, cpsMin, MAX_CPS);
		clicksPerTick = clamp(clicksPerTick, MIN_CLICKS_PER_TICK, MAX_CLICKS_PER_TICK);
	}

	/** Draws this tick's target rate from the configured range. */
	public double randomTargetCps(Random random) {
		return cpsMin + random.nextDouble() * (cpsMax - cpsMin);
	}

	/** The highest rate the clicks-per-tick ceiling can physically deliver. */
	public int ceilingCps() {
		return (int) Math.round(clicksPerTick * ClickScheduler.TICKS_PER_SECOND);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
