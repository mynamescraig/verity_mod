package dev.verity;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/**
 * Makes sure every player has exactly one Verity, tracks how long they've been together,
 * and runs small delayed tasks.
 */
public final class VerityManager {
	/** Player tags. They are saved with the player, so settings survive restarts. */
	private static final String TAG_MET = "verity_met";
	private static final String TAG_DISMISSED = "verity_dismissed";
	private static final String TAG_CREEPY_ON = "verity_creepy_on";
	private static final String TAG_CREEPY_OFF = "verity_calm";
	private static final String TAG_DARK = "verity_nolight";

	public static final String[] STAGE_NAMES = {"New Friend", "Friend", "Best Friend", "Only Friend"};

	private static final Map<UUID, VerityEntity> companions = new HashMap<>();
	private static final Map<UUID, Integer> meetingCountdown = new HashMap<>();
	private static final Map<UUID, Integer> dismissAttempts = new HashMap<>();
	private static final Map<UUID, Long> lastSeen = new HashMap<>();
	private static final Map<UUID, Integer> bondMinutes = new HashMap<>();
	private static final Set<String> introducedPairs = new HashSet<>();
	private static final List<Task> tasks = new ArrayList<>();
	private static final List<Task> pending = new ArrayList<>();
	private static MinecraftServer server;

	private record Task(int[] ticks, Runnable action) {
	}

	private VerityManager() {
	}

	public static void schedule(int delayTicks, Runnable action) {
		pending.add(new Task(new int[] {delayTicks}, action));
	}

	// ------------------------------------------------------------------ lifecycle

	public static void onStarted(MinecraftServer s) {
		server = s;
		loadBonds();
	}

	public static void onStopping() {
		saveBonds();
		for (VerityEntity v : companions.values()) {
			if (!v.isRemoved()) v.discard();
		}
		companions.clear();
		tasks.clear();
		pending.clear();
		introducedPairs.clear();
		server = null;
	}

	public static void tick(MinecraftServer s) {
		tasks.addAll(pending);
		pending.clear();
		for (Iterator<Task> it = tasks.iterator(); it.hasNext(); ) {
			Task t = it.next();
			if (--t.ticks()[0] <= 0) {
				it.remove();
				try {
					t.action().run();
				} catch (RuntimeException e) {
					VerityMod.LOGGER.warn("Verity task failed", e);
				}
			}
		}

		int tick = s.getTickCount();
		if (tick % 1200 == 0) {
			for (ServerPlayer player : s.getPlayerList().getPlayers()) {
				if (get(player) != null) addBond(player, 1);
			}
		}
		if (tick % 6000 == 0) saveBonds();
		if (tick % 20 != 0) return;

		for (ServerPlayer player : s.getPlayerList().getPlayers()) {
			if (player.getTags().contains(TAG_DISMISSED) || !player.isAlive()) continue;
			if (!player.getTags().contains(TAG_MET)) {
				Integer left = meetingCountdown.get(player.getUUID());
				if (left == null) continue;
				left -= 20;
				if (left <= 0) {
					meetingCountdown.remove(player.getUUID());
					firstMeeting(player);
				} else {
					meetingCountdown.put(player.getUUID(), left);
				}
				continue;
			}
			ensure(player);
		}
		if (tick % 100 == 0) introduceVerities(s);
	}

	public static void onJoin(ServerPlayer player) {
		if (!player.getTags().contains(TAG_MET)) {
			meetingCountdown.put(player.getUUID(), VerityConfig.firstMeetingSeconds * 20);
			return;
		}
		Long left = lastSeen.remove(player.getUUID());
		if (player.getTags().contains(TAG_DISMISSED) || !isCreepy(player)) return;
		int stage = stage(player);
		if (left != null && stage >= 1) {
			long minutes = (System.currentTimeMillis() - left) / 60000L;
			schedule(100, () -> say(player, "You left. You were gone for " + minutes + " minute" + (minutes == 1 ? "" : "s") + ". I counted."));
		} else if (stage >= 3) {
			schedule(100, () -> say(player, "You came back. You always come back."));
		}
	}

	public static void onLeave(ServerPlayer player) {
		lastSeen.put(player.getUUID(), System.currentTimeMillis());
		VerityEntity v = companions.remove(player.getUUID());
		if (v != null && !v.isRemoved()) v.discard();
		meetingCountdown.remove(player.getUUID());
		introducedPairs.removeIf(pair -> pair.contains(player.getUUID().toString()));
	}

	/** After a death respawn: tell the player where their stuff is. */
	public static void onRespawn(ServerPlayer player) {
		GlobalPos death = player.getLastDeathLocation().orElse(null);
		if (death == null || player.getTags().contains(TAG_DISMISSED) || !player.getTags().contains(TAG_MET)) return;
		schedule(60, () -> {
			VerityEntity v = ensure(player);
			if (v == null) return;
			v.say(player, "You died at " + death.pos().getX() + ", " + death.pos().getY() + ", " + death.pos().getZ()
					+ (death.dimension() == player.level().dimension() ? "" : " in the " + VerityEntity.dimensionName(death.dimension()))
					+ ". Say \"verity take me to my stuff\" and I'll lead you there. Hurry, it despawns in 5 minutes!");
			if (isCreepy(player) && stage(player) >= 2) {
				schedule(80, () -> v.say(player, "Don't do that again. I need you."));
			}
		});
	}

	// ------------------------------------------------------------------ companions

	public static VerityEntity get(ServerPlayer player) {
		VerityEntity v = companions.get(player.getUUID());
		return v == null || v.isRemoved() ? null : v;
	}

	/** Spawns (or moves) the player's Verity if it is missing or in another dimension. */
	public static VerityEntity ensure(ServerPlayer player) {
		VerityEntity v = companions.get(player.getUUID());
		if (v != null && !v.isRemoved() && v.level() == player.level()) return v;
		if (v != null && !v.isRemoved()) v.discard();
		v = spawn(player);
		if (v != null) companions.put(player.getUUID(), v);
		return v;
	}

	private static VerityEntity spawn(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		VerityEntity v = VerityMod.VERITY.create(level);
		if (v == null) return null;
		Vec3 pos = player.getEyePosition().add(player.getLookAngle().scale(1.5));
		v.moveTo(pos.x, pos.y, pos.z, player.getYRot() + 180f, 0f);
		v.setOwner(player);
		level.addFreshEntity(v);
		level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 12, 0.2, 0.2, 0.2, 0.03);
		return v;
	}

	/** The first meeting. In creepy mode, something knocks first. */
	public static void firstMeeting(ServerPlayer player) {
		player.addTag(TAG_MET);
		player.removeTag(TAG_DISMISSED);
		if (isCreepy(player)) {
			Vec3 door = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(4));
			for (int i = 0; i < 3; i++) {
				schedule(1 + i * 12, () -> sound(player, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, door, 0.7f, 1.2f));
				schedule(60 + i * 12, () -> sound(player, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, door, 0.8f, 1.1f));
			}
			schedule(120, () -> introduce(player));
		} else {
			introduce(player);
		}
	}

	private static void introduce(ServerPlayer player) {
		if (player.isRemoved()) return;
		VerityEntity v = ensure(player);
		if (v == null) return;
		String name = player.getName().getString();
		player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.6f, 1.8f);
		v.say(player, "...");
		schedule(50, () -> v.say(player, "Hello? Oh! Hi, " + name + "! It's me, Verity!"));
		schedule(110, () -> v.say(player, "I'm your personal helper friend. I know where everything is."));
		schedule(180, () -> v.say(player, "I'll light up the dark, watch your back, and take you anywhere you want to go."));
		schedule(250, () -> v.say(player, "If you need me, just say \"verity help\" in chat. I'll always be close."));
		if (isCreepy(player)) schedule(340, () -> v.say(player, "We're going to be best friends. Only us."));
	}

	/** Two players with Verities meet for the first time this session. */
	private static void introduceVerities(MinecraftServer s) {
		List<ServerPlayer> players = s.getPlayerList().getPlayers();
		for (int i = 0; i < players.size(); i++) {
			for (int j = i + 1; j < players.size(); j++) {
				ServerPlayer a = players.get(i);
				ServerPlayer b = players.get(j);
				VerityEntity va = get(a);
				VerityEntity vb = get(b);
				if (va == null || vb == null || a.level() != b.level() || a.distanceTo(b) > 8) continue;
				String key = a.getUUID() + "|" + b.getUUID();
				if (!introducedPairs.add(key)) continue;
				greetOther(va, a, b);
				schedule(40, () -> greetOther(vb, b, a));
			}
		}
	}

	private static void greetOther(VerityEntity v, ServerPlayer owner, ServerPlayer other) {
		if (v.isRemoved()) return;
		String them = other.getName().getString();
		if (isCreepy(owner) && stage(owner) >= 2) {
			v.say(owner, "There's another one of me next to " + them + ". There should only be one of me.");
		} else {
			v.say(owner, "Oh! " + them + " has a Verity too! Hi, other me!");
		}
	}

	// ------------------------------------------------------------------ commands

	public static void onChat(ServerPlayer sender, String message) {
		if (!message.toLowerCase(Locale.ROOT).contains("verity")) return;
		// Chat can arrive off the server thread; hop back on before touching the world.
		sender.getServer().execute(() -> {
			VerityEntity v = get(sender);
			if (v == null) return;
			schedule(15, () -> {
				if (!v.isRemoved()) v.respond(sender, message);
			});
		});
	}

	public static void dismiss(ServerPlayer player) {
		VerityEntity current = get(player);
		int needed = !isCreepy(player) ? 1 : stage(player) >= 3 ? 5 : stage(player) >= 1 ? 3 : 1;
		if (current != null && dismissAttempts.merge(player.getUUID(), 1, Integer::sum) < needed) {
			int n = dismissAttempts.get(player.getUUID());
			String[] refusals = {
					"No. I don't want to go.",
					"Why are you trying to get rid of me?",
					"I won't let you leave me.",
					"Please. I'll be good. ...Fine. Ask me one more time."
			};
			String line = n >= needed - 1 ? refusals[3] : refusals[Math.min(n - 1, 2)];
			current.say(player, line);
			return;
		}
		dismissAttempts.remove(player.getUUID());
		player.addTag(TAG_DISMISSED);
		VerityEntity v = companions.remove(player.getUUID());
		if (v != null && !v.isRemoved()) {
			v.say(player, isCreepy(player) && stage(player) >= 1
					? "Okay. I'll go. But I'll still be watching."
					: "Okay. Bye for now. Call me with /verity summon.");
			player.serverLevel().sendParticles(ParticleTypes.END_ROD, v.getX(), v.getY(), v.getZ(), 12, 0.2, 0.2, 0.2, 0.03);
			v.discard();
		}
	}

	public static void summon(ServerPlayer player) {
		if (!player.getTags().contains(TAG_MET)) {
			meetingCountdown.remove(player.getUUID());
			firstMeeting(player);
			return;
		}
		dismissAttempts.remove(player.getUUID());
		player.removeTag(TAG_DISMISSED);
		VerityEntity v = ensure(player);
		if (v != null) {
			Vec3 pos = player.getEyePosition().add(player.getLookAngle().scale(1.5));
			v.setPos(pos.x, pos.y, pos.z);
			v.say(player, "You called?");
		}
	}

	// ------------------------------------------------------------------ settings

	public static boolean isCreepy(ServerPlayer player) {
		if (player.getTags().contains(TAG_CREEPY_ON)) return true;
		if (player.getTags().contains(TAG_CREEPY_OFF)) return false;
		return VerityConfig.creepyByDefault;
	}

	public static void setCreepy(ServerPlayer player, boolean creepy) {
		player.removeTag(creepy ? TAG_CREEPY_OFF : TAG_CREEPY_ON);
		player.addTag(creepy ? TAG_CREEPY_ON : TAG_CREEPY_OFF);
	}

	public static boolean lightEnabled(ServerPlayer player) {
		return !player.getTags().contains(TAG_DARK);
	}

	public static void setLight(ServerPlayer player, boolean on) {
		if (on) player.removeTag(TAG_DARK);
		else player.addTag(TAG_DARK);
	}

	// ------------------------------------------------------------------ friendship

	public static int bond(ServerPlayer player) {
		return bondMinutes.getOrDefault(player.getUUID(), 0);
	}

	public static void setBond(ServerPlayer player, int minutes) {
		bondMinutes.put(player.getUUID(), Math.max(0, minutes));
	}

	private static void addBond(ServerPlayer player, int minutes) {
		int before = stage(player);
		setBond(player, bond(player) + minutes);
		int after = stage(player);
		if (after > before && isCreepy(player)) onStageUp(player, after);
	}

	/** Story stage 0-3, from minutes spent together. */
	public static int stage(ServerPlayer player) {
		int m = bond(player);
		int stage = 0;
		for (int threshold : VerityConfig.stageMinutes) {
			if (m >= threshold) stage++;
		}
		return stage;
	}

	private static void onStageUp(ServerPlayer player, int stage) {
		VerityEntity v = get(player);
		if (v == null) return;
		switch (stage) {
			case 1 -> v.say(player, "I think we're really friends now, " + player.getName().getString() + ". :)");
			case 2 -> {
				v.say(player, "You're my best friend. Am I your best friend?");
				schedule(80, () -> v.say(player, "You don't have to answer. I already know."));
			}
			default -> {
				v.say(player, "I'm your only friend now.");
				schedule(60, () -> v.say(player, "You don't need anyone else."));
			}
		}
	}

	private static Path bondFile() {
		return server == null ? null : server.getWorldPath(LevelResource.ROOT).resolve("verity_friendship.properties");
	}

	private static void loadBonds() {
		bondMinutes.clear();
		Path path = bondFile();
		if (path == null || !Files.exists(path)) return;
		Properties p = new Properties();
		try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			p.load(r);
			for (String key : p.stringPropertyNames()) {
				try {
					bondMinutes.put(UUID.fromString(key), Integer.parseInt(p.getProperty(key).trim()));
				} catch (IllegalArgumentException ignored) {
				}
			}
		} catch (IOException e) {
			VerityMod.LOGGER.warn("Could not read {}", path, e);
		}
	}

	private static void saveBonds() {
		Path path = bondFile();
		if (path == null) return;
		Properties p = new Properties();
		bondMinutes.forEach((id, minutes) -> p.setProperty(id.toString(), Integer.toString(minutes)));
		try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			p.store(w, "Minutes each player has spent with their Verity");
		} catch (IOException e) {
			VerityMod.LOGGER.warn("Could not write {}", path, e);
		}
	}

	// ------------------------------------------------------------------ helpers

	private static void say(ServerPlayer player, String text) {
		VerityEntity v = get(player);
		if (v != null) v.say(player, text);
	}

	private static void sound(ServerPlayer to, net.minecraft.sounds.SoundEvent sound, Vec3 pos, float volume, float pitch) {
		to.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.AMBIENT,
				pos.x, pos.y, pos.z, volume, pitch, to.getRandom().nextLong()));
	}
}
