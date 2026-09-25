package dev.verity;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/** Settings from config/verity.properties. Written with defaults on first launch. */
public final class VerityConfig {
	public static int firstMeetingSeconds = 60;
	public static boolean creepyByDefault = true;
	public static double creepyFrequency = 1.0;
	/** Minutes spent together before each story stage: Friend, Best Friend, Only Friend. */
	public static int[] stageMinutes = {30, 90, 180};
	public static boolean jealousy = true;
	public static boolean helperEffects = true;
	public static boolean oreSense = true;
	public static boolean itemMagnet = true;
	public static float zapDamage = 3f;
	public static int lightLevel = 13;

	private VerityConfig() {
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("verity.properties");
		Properties p = new Properties();
		if (Files.exists(path)) {
			try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				p.load(r);
			} catch (IOException e) {
				VerityMod.LOGGER.warn("Could not read {}", path, e);
			}
		}
		firstMeetingSeconds = getInt(p, "firstMeetingSeconds", firstMeetingSeconds);
		creepyByDefault = getBool(p, "creepyByDefault", creepyByDefault);
		creepyFrequency = Math.max(0.05, getDouble(p, "creepyFrequency", creepyFrequency));
		stageMinutes = getInts(p, "stageMinutes", stageMinutes);
		jealousy = getBool(p, "jealousy", jealousy);
		helperEffects = getBool(p, "helperEffects", helperEffects);
		oreSense = getBool(p, "oreSense", oreSense);
		itemMagnet = getBool(p, "itemMagnet", itemMagnet);
		zapDamage = (float) getDouble(p, "zapDamage", zapDamage);
		lightLevel = Math.max(0, Math.min(15, getInt(p, "lightLevel", lightLevel)));

		p.setProperty("firstMeetingSeconds", Integer.toString(firstMeetingSeconds));
		p.setProperty("creepyByDefault", Boolean.toString(creepyByDefault));
		p.setProperty("creepyFrequency", Double.toString(creepyFrequency));
		p.setProperty("stageMinutes", stageMinutes[0] + "," + stageMinutes[1] + "," + stageMinutes[2]);
		p.setProperty("jealousy", Boolean.toString(jealousy));
		p.setProperty("helperEffects", Boolean.toString(helperEffects));
		p.setProperty("oreSense", Boolean.toString(oreSense));
		p.setProperty("itemMagnet", Boolean.toString(itemMagnet));
		p.setProperty("zapDamage", Float.toString(zapDamage));
		p.setProperty("lightLevel", Integer.toString(lightLevel));
		try {
			Files.createDirectories(path.getParent());
			try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				p.store(w, "Verity settings. creepyFrequency: 2.0 = twice as many creepy events, 0.5 = half.\n"
						+ " stageMinutes: minutes together before Verity becomes Friend, Best Friend, Only Friend.");
			}
		} catch (IOException e) {
			VerityMod.LOGGER.warn("Could not write {}", path, e);
		}
	}

	private static int getInt(Properties p, String key, int def) {
		try {
			return Integer.parseInt(p.getProperty(key, "").trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static double getDouble(Properties p, String key, double def) {
		try {
			return Double.parseDouble(p.getProperty(key, "").trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static boolean getBool(Properties p, String key, boolean def) {
		String v = p.getProperty(key);
		return v == null ? def : Boolean.parseBoolean(v.trim());
	}

	private static int[] getInts(Properties p, String key, int[] def) {
		String v = p.getProperty(key);
		if (v == null) return def;
		String[] parts = v.split(",");
		if (parts.length != 3) return def;
		int[] out = new int[3];
		try {
			for (int i = 0; i < 3; i++) out[i] = Math.max(0, Integer.parseInt(parts[i].trim()));
		} catch (NumberFormatException e) {
			return def;
		}
		return out;
	}
}
