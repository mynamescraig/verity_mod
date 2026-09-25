package dev.verity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Makes sure every player has exactly one Verity, and runs small delayed tasks. */
public final class VerityManager {
	/** Player tags. They are saved with the player, so settings survive restarts. */
	private static final String TAG_MET = "verity_met";
	private static final String TAG_DISMISSED = "verity_dismissed";
	private static final String TAG_CALM = "verity_calm";
	private static final String TAG_DARK = "verity_nolight";

	/** How long after first joining a world Verity shows up. */
	private static final int FIRST_MEETING_DELAY = 60 * 20;

	private static final Map<UUID, VerityEntity> companions = new HashMap<>();
	private static final Map<UUID, Integer> meetingCountdown = new HashMap<>();
	private static final List<Task> tasks = new ArrayList<>();
	private static final List<Task> pending = new ArrayList<>();

	private record Task(int[] ticks, Runnable action) {
	}

	private VerityManager() {
	}

	public static void schedule(int delayTicks, Runnable action) {
		pending.add(new Task(new int[] {delayTicks}, action));
	}

	public static void tick(MinecraftServer server) {
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

		if (server.getTickCount() % 20 != 0) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.getTags().contains(TAG_DISMISSED) || !player.isAlive()) continue;
			if (!player.getTags().contains(TAG_MET)) {
				int left = meetingCountdown.merge(player.getUUID(), -20, Integer::sum);
				if (left <= 0) {
					meetingCountdown.remove(player.getUUID());
					firstMeeting(player);
				}
				continue;
			}
			ensure(player);
		}
	}

	public static void onJoin(ServerPlayer player) {
		if (!player.getTags().contains(TAG_MET)) {
			meetingCountdown.put(player.getUUID(), FIRST_MEETING_DELAY);
			return;
		}
		Long left = lastSeen.remove(player.getUUID());
		if (left != null && isCreepy(player) && !player.getTags().contains(TAG_DISMISSED)) {
			long minutes = (System.currentTimeMillis() - left) / 60000L;
			schedule(100, () -> {
				VerityEntity v = get(player);
				if (v != null) v.say(player, "You left. You were gone for " + minutes + " minute" + (minutes == 1 ? "" : "s") + ". I counted.");
			});
		}
	}

	public static void onLeave(ServerPlayer player) {
		lastSeen.put(player.getUUID(), System.currentTimeMillis());
		VerityEntity v = companions.remove(player.getUUID());
		if (v != null && !v.isRemoved()) v.discard();
		meetingCountdown.remove(player.getUUID());
	}

	public static void onStopping() {
		for (VerityEntity v : companions.values()) {
			if (!v.isRemoved()) v.discard();
		}
		companions.clear();
		tasks.clear();
		pending.clear();
	}

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

	public static void firstMeeting(ServerPlayer player) {
		player.addTag(TAG_MET);
		player.removeTag(TAG_DISMISSED);
		VerityEntity v = ensure(player);
		if (v == null) return;
		String name = player.getName().getString();
		player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.6f, 1.8f);
		v.say(player, "...");
		schedule(50, () -> v.say(player, "Hello? Oh! Hi, " + name + "! It's me, Verity!"));
		schedule(110, () -> v.say(player, "I'm your personal helper friend. I know where everything is."));
		schedule(180, () -> v.say(player, "I'll light up the dark, watch your back, and keep you company."));
		schedule(250, () -> v.say(player, "If you need me, just say \"verity help\" in chat. I'll always be close."));
		if (isCreepy(player)) schedule(340, () -> v.say(player, "We're going to be best friends. Only us."));
	}

	/** Chat hook: anything containing "verity" gets an answer from the sender's own Verity. */
	public static void onChat(ServerPlayer sender, String message) {
		if (!message.toLowerCase(java.util.Locale.ROOT).contains("verity")) return;
		VerityEntity v = get(sender);
		if (v == null) return;
		schedule(15, () -> {
			if (!v.isRemoved()) v.respond(sender, message);
		});
	}

	private static final Map<UUID, Integer> dismissAttempts = new HashMap<>();
	private static final Map<UUID, Long> lastSeen = new HashMap<>();

	public static void dismiss(ServerPlayer player) {
		VerityEntity current = get(player);
		if (current != null && isCreepy(player) && dismissAttempts.merge(player.getUUID(), 1, Integer::sum) < 3) {
			int n = dismissAttempts.get(player.getUUID());
			current.say(player, n == 1 ? "No. I don't want to go." : "Why are you trying to get rid of me? ...Fine. Ask me one more time.");
			return;
		}
		dismissAttempts.remove(player.getUUID());
		player.addTag(TAG_DISMISSED);
		VerityEntity v = companions.remove(player.getUUID());
		if (v != null && !v.isRemoved()) {
			v.say(player, isCreepy(player) ? "Okay. I'll go. But I'll still be watching." : "Okay. Bye for now. Call me with /verity summon.");
			ServerLevel level = player.serverLevel();
			level.sendParticles(ParticleTypes.END_ROD, v.getX(), v.getY(), v.getZ(), 12, 0.2, 0.2, 0.2, 0.03);
			v.discard();
		}
	}

	public static void summon(ServerPlayer player) {
		if (!player.getTags().contains(TAG_MET)) {
			meetingCountdown.remove(player.getUUID());
			firstMeeting(player);
			return;
		}
		player.removeTag(TAG_DISMISSED);
		VerityEntity v = ensure(player);
		if (v != null) {
			Vec3 pos = player.getEyePosition().add(player.getLookAngle().scale(1.5));
			v.setPos(pos.x, pos.y, pos.z);
			v.say(player, "You called?");
		}
	}

	public static boolean isCreepy(ServerPlayer player) {
		return !player.getTags().contains(TAG_CALM);
	}

	public static void setCreepy(ServerPlayer player, boolean creepy) {
		if (creepy) player.removeTag(TAG_CALM);
		else player.addTag(TAG_CALM);
	}

	public static boolean lightEnabled(ServerPlayer player) {
		return !player.getTags().contains(TAG_DARK);
	}

	public static void setLight(ServerPlayer player, boolean on) {
		if (on) player.removeTag(TAG_DARK);
		else player.addTag(TAG_DARK);
	}
}
