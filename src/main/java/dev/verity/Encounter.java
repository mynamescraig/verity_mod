package dev.verity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * One run of the encounter.
 *
 * <p>Each round: the "inside" player is locked in the Shadow Realm and sees the three callouts
 * (one 2D shape per LEFT / MIDDLE / RIGHT). The "outside" player sees three statues holding 3D
 * shapes. Outside must dissect (swap 2D shapes between statues) until every statue holds the two
 * shapes that are NOT its callout. Inside must make their two offerings the shapes that are not
 * their own callout. When both are true the Witness is exposed for a short damage phase. Roles swap
 * every round. Kill the Witness within three rounds.
 */
public final class Encounter {
	public enum Phase { DISSECTION, DAMAGE }

	public static final String MOB_TAG = "verity_mob";
	public static final String KNIGHT_TAG = "verity_knight";
	public static final String WITNESS_TAG = "verity_witness";
	private static final String INSIDE_TAG = "verity_inside";

	private static final int MAX_ROUNDS = 3;
	private static final int DISSECTION_TICKS = 180 * 20;
	private static final int DAMAGE_TICKS = 25 * 20;
	private static final int MAX_MISTAKES = 3;
	private static final float WITNESS_HP_DUO = 360f;
	private static final float WITNESS_HP_SOLO = 200f;

	private static final int[][] OUTSIDE_KNIGHT_SPAWNS = {{12, -12}, {-12, -12}, {13, 0}, {-13, 0}, {12, 5}, {-12, 5}};
	private static final int[][] INSIDE_KNIGHT_SPAWNS = {{6, 0}, {-6, 0}, {6, -3}, {-6, -3}};

	private static Encounter current;

	private final MinecraftServer server;
	private final Arena arena;
	private final List<UUID> players;
	private final boolean solo;
	private final Random random = new Random();
	private final ServerBossEvent witnessBar;
	private final ServerBossEvent timerBar;
	private final List<UUID> outsideKnights = new ArrayList<>();
	private final List<UUID> insideKnights = new ArrayList<>();

	private Phase phase = Phase.DISSECTION;
	private int round;
	private int ticksLeft;
	private int phaseLength;
	private int mistakes;
	private int age;

	private UUID insideId;
	private final Shape[] callouts = new Shape[3];
	private int insideSlot;
	private final Shape[][] statues = new Shape[3][2];
	private final Shape[] hands = new Shape[2];
	private int pendingStatue = -1;
	private Shape pendingShape;
	private boolean outsideTrueAnnounced;
	private boolean insideTrueAnnounced;

	private UUID witnessId;
	private int outsideSpawnCooldown;
	private int insideSpawnCooldown;

	private Encounter(MinecraftServer server, Arena arena, List<ServerPlayer> players) {
		this.server = server;
		this.arena = arena;
		this.players = new ArrayList<>();
		for (ServerPlayer p : players) this.players.add(p.getUUID());
		this.solo = players.size() == 1;
		this.witnessBar = new ServerBossEvent(Component.literal("The Witness").withStyle(ChatFormatting.DARK_PURPLE),
				BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
		this.timerBar = new ServerBossEvent(Component.literal("Verity"), BossEvent.BossBarColor.WHITE,
				BossEvent.BossBarOverlay.PROGRESS);
		for (ServerPlayer p : players) {
			witnessBar.addPlayer(p);
			timerBar.addPlayer(p);
		}
	}

	public static Encounter current() {
		return current;
	}

	/** Starts an encounter. Returns an error message, or null on success. */
	public static Component start(MinecraftServer server, List<ServerPlayer> players) {
		if (current != null) return Component.literal("An encounter is already running. Use /verity stop first.");
		Arena arena = Arena.get();
		if (arena == null) return Component.literal("No arena yet. Stand somewhere open and run /verity build.");
		ServerLevel level = arena.level(server);
		if (level == null) return Component.literal("The arena's dimension is not loaded.");
		if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
			return Component.literal("Verity needs monsters. Set difficulty to Easy or higher.");
		}
		Encounter e = new Encounter(server, arena, players);
		current = e;
		e.begin(level);
		return null;
	}

	public Phase phase() {
		return phase;
	}

	// ================================================================= lifecycle

	private void begin(ServerLevel level) {
		Arena.removeTagged(level, MOB_TAG);
		arena.clearDisplays(level);
		for (ServerPlayer p : onlinePlayers()) Essence.clearFrom(p);
		spawnWitness(level);
		round = 1;
		broadcast(Component.literal("━━━━━━━━ VERITY ━━━━━━━━").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
		if (solo) {
			broadcast(Component.literal("Solo practice: you play outside. The callouts are written on the statues for you.")
					.withStyle(ChatFormatting.GRAY));
		} else {
			broadcast(Component.literal("Roles swap every round. Talk to each other!").withStyle(ChatFormatting.GRAY));
		}
		beginRound(level);
	}

	private void beginRound(ServerLevel level) {
		phase = Phase.DISSECTION;
		phaseLength = DISSECTION_TICKS;
		ticksLeft = DISSECTION_TICKS;
		mistakes = 0;
		pendingStatue = -1;
		pendingShape = null;
		outsideTrueAnnounced = false;
		insideTrueAnnounced = false;
		outsideSpawnCooldown = 60;
		insideSpawnCooldown = 80;

		insideId = solo ? null : players.get(round % 2);

		// Callouts: one of each shape across LEFT / MIDDLE / RIGHT.
		List<Shape> order = new ArrayList<>(Arrays.asList(Shape.values()));
		Collections.shuffle(order, random);
		for (int i = 0; i < 3; i++) callouts[i] = order.get(i);
		insideSlot = random.nextInt(3);

		// Outside statues: two of every shape spread over three statues, never already solved.
		do {
			List<Shape> pool = new ArrayList<>();
			for (Shape s : Shape.values()) {
				pool.add(s);
				pool.add(s);
			}
			Collections.shuffle(pool, random);
			for (int i = 0; i < 3; i++) {
				statues[i][0] = pool.get(i * 2);
				statues[i][1] = pool.get(i * 2 + 1);
			}
		} while (outsideTrue());

		// Offerings start wrong.
		if (solo) {
			Shape[] want = callouts[insideSlot].complement();
			hands[0] = want[0];
			hands[1] = want[1];
		} else {
			do {
				hands[0] = Shape.values()[random.nextInt(3)];
				hands[1] = Shape.values()[random.nextInt(3)];
			} while (insideTrue());
		}

		// Positions.
		findWitness(level).ifPresent(w -> {
			w.setNoAi(true);
			w.removeEffect(MobEffects.GLOWING);
			Vec3 home = witnessHome();
			w.teleportTo(home.x, home.y, home.z);
		});
		for (ServerPlayer p : onlinePlayers()) {
			Essence.clearFrom(p);
			if (p.getUUID().equals(insideId)) {
				Vec3 spot = arena.inside(0.5, 0, 0.5);
				p.teleportTo(level, spot.x, spot.y, spot.z, 0f, 0f);
				title(p, Component.literal("THE SHADOW REALM").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
						Component.literal("Round " + round + " — call out what you see").withStyle(ChatFormatting.GRAY));
				p.playNotifySound(SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.6f, 0.8f);
				p.sendSystemMessage(Component.literal("You are trapped inside. ").withStyle(ChatFormatting.LIGHT_PURPLE)
						.append(Component.literal("Tell your partner the shape above each LEFT / MIDDLE / RIGHT statue. "
								+ "Then kill Shadow Knights and dunk shapes into your two offerings until they hold the two "
								+ "shapes that are NOT your own (the one marked YOU).").withStyle(ChatFormatting.GRAY)));
			} else {
				Vec3 spot = arena.outside(0.5, 0, 0.5);
				p.teleportTo(level, spot.x, spot.y, spot.z, 0f, 0f);
				title(p, Component.literal("DISSECT").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
						Component.literal("Round " + round + " — make every statue speak the Truth").withStyle(ChatFormatting.GRAY));
				p.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1f, 0.7f);
				p.sendSystemMessage(Component.literal("You are outside. ").withStyle(ChatFormatting.GOLD)
						.append(Component.literal("Each statue must end up holding the two shapes that are NOT its callout. "
								+ "Kill Shadow Knights for essences. Dunk one shape into a statue, then a different shape "
								+ "into another statue: the two shapes swap. Dunking a shape a statue doesn't hold causes Dissonance.")
								.withStyle(ChatFormatting.GRAY)));
			}
		}
		refreshDisplays(level);
	}

	public void tick() {
		ServerLevel level = arena.level(server);
		if (level == null) {
			stop();
			return;
		}
		age++;

		for (UUID id : players) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null) {
				broadcast(Component.literal("A Guardian left. The encounter ends.").withStyle(ChatFormatting.RED));
				stop();
				return;
			}
			if (p.isDeadOrDying()) {
				wipe(level, p.getName().getString() + " has fallen.");
				return;
			}
		}

		Evoker witness = findWitness(level).orElse(null);
		if (witness == null || !witness.isAlive()) {
			if (phase == Phase.DAMAGE) {
				victory(level);
				return;
			}
			spawnWitness(level);
			witness = findWitness(level).orElse(null);
		}
		if (witness != null) {
			witnessBar.setProgress(Math.max(0f, Math.min(1f, witness.getHealth() / witness.getMaxHealth())));
			Vec3 home = witnessHome();
			if (witness.position().distanceTo(home) > 11) witness.teleportTo(home.x, home.y, home.z);
		}

		ticksLeft--;
		updateTimerBar();

		if (phase == Phase.DISSECTION) {
			if (ticksLeft <= 0) {
				wipe(level, "Out of time. The Witness claims you.");
				return;
			}
			tickKnights(level);
			// Keep the inside player inside.
			ServerPlayer inside = insidePlayer();
			if (inside != null && !arena.isInsideRoom(inside.position())) {
				Vec3 spot = arena.inside(0.5, 0, 0.5);
				inside.teleportTo(level, spot.x, spot.y, spot.z, inside.getYRot(), inside.getXRot());
				inside.displayClientMessage(Component.literal("The Shadow Realm holds you.").withStyle(ChatFormatting.DARK_PURPLE), true);
			}
			if (pendingStatue >= 0 && age % 5 == 0) {
				Vec3 c = arena.outside(Arena.STATUE_X[pendingStatue] + 0.5, 2.5, Arena.STATUE_Z + 0.5);
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 6, 0.4, 1.0, 0.4, 0.01);
			}
			if (ticksLeft == 60 * 20 || ticksLeft == 30 * 20 || ticksLeft == 10 * 20) {
				broadcast(Component.literal((ticksLeft / 20) + " seconds remain.").withStyle(ChatFormatting.RED));
			}
		} else {
			if (witness != null && age % 4 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, witness.getX(), witness.getY() + 1.2, witness.getZ(), 3, 0.4, 0.8, 0.4, 0.02);
			}
			if (ticksLeft <= 0) endDamage(level);
		}
	}

	private void startDamage(ServerLevel level) {
		phase = Phase.DAMAGE;
		phaseLength = DAMAGE_TICKS;
		ticksLeft = DAMAGE_TICKS;
		pendingStatue = -1;
		for (UUID id : insideKnights) {
			Entity e = level.getEntity(id);
			if (e != null) e.discard();
		}
		insideKnights.clear();
		findWitness(level).ifPresent(w -> {
			w.setNoAi(false);
			w.addEffect(new MobEffectInstance(MobEffects.GLOWING, DAMAGE_TICKS + 20, 0, false, false));
		});
		for (ServerPlayer p : onlinePlayers()) {
			Essence.clearFrom(p);
			if (p.getUUID().equals(insideId)) {
				Vec3 spot = arena.outside(3.5, 0, 2.5);
				p.teleportTo(level, spot.x, spot.y, spot.z, 180f, 0f);
			}
			title(p, Component.literal("THE WITNESS IS EXPOSED").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
					Component.literal("Damage it now! " + (DAMAGE_TICKS / 20) + " seconds").withStyle(ChatFormatting.YELLOW));
			p.playNotifySound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 0.8f, 1.2f);
		}
		refreshDisplays(level);
	}

	private void endDamage(ServerLevel level) {
		round++;
		if (round > MAX_ROUNDS) {
			wipe(level, "The Final Shape is complete. (Enrage)");
			return;
		}
		broadcast(Component.literal("The Witness recoils and shifts the Truth… Round " + round + "/" + MAX_ROUNDS
				+ (solo ? "" : " — roles swap!")).withStyle(ChatFormatting.DARK_PURPLE));
		beginRound(level);
	}

	private void victory(ServerLevel level) {
		for (ServerPlayer p : onlinePlayers()) {
			title(p, Component.literal("VERITY CONQUERED").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
					Component.literal("The Witness has seen the Truth").withStyle(ChatFormatting.YELLOW));
			p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1f, 1f);
			ItemStack trophy = new ItemStack(Items.NETHER_STAR);
			trophy.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
					Component.literal("Verity's Brow").withStyle(s -> s.withColor(0xB388FF).withBold(true).withItalic(false)));
			give(p, trophy);
			give(p, new ItemStack(Items.NETHERITE_INGOT));
		}
		broadcast(Component.literal("Victory in round " + round + " of " + MAX_ROUNDS + "!").withStyle(ChatFormatting.GOLD));
		finish(level);
	}

	private void wipe(ServerLevel level, String reason) {
		for (ServerPlayer p : onlinePlayers()) {
			title(p, Component.literal("WIPE").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
					Component.literal(reason).withStyle(ChatFormatting.RED));
			p.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6f, 0.6f);
		}
		broadcast(Component.literal("Wipe: " + reason + " Run /verity start to try again.").withStyle(ChatFormatting.RED));
		finish(level);
	}

	/** Ends without fanfare. */
	public void stop() {
		ServerLevel level = arena.level(server);
		if (level != null) finish(level);
		else {
			witnessBar.removeAllPlayers();
			timerBar.removeAllPlayers();
			current = null;
		}
	}

	private void finish(ServerLevel level) {
		witnessBar.removeAllPlayers();
		timerBar.removeAllPlayers();
		Arena.removeTagged(level, MOB_TAG);
		// Stray vexes from the Witness and leftover essences on the floor.
		List<Entity> doomed = new ArrayList<>();
		for (Entity e : level.getAllEntities()) {
			if (e == null || !arena.contains(level, e.blockPosition())) continue;
			if (e instanceof Vex) doomed.add(e);
			if (e instanceof ItemEntity item && Essence.shapeOf(item.getItem()) != null) doomed.add(e);
		}
		doomed.forEach(Entity::discard);
		arena.clearDisplays(level);
		arena.showIdle(level);
		for (ServerPlayer p : onlinePlayers()) {
			Essence.clearFrom(p);
			if (p.isAlive() && arena.isInsideRoom(p.position())) {
				Vec3 spot = arena.outside(0.5, 0, 0.5);
				p.teleportTo(level, spot.x, spot.y, spot.z, 0f, 0f);
			}
		}
		current = null;
	}

	// ================================================================= dunking

	/** Right-click on a block while the encounter runs. */
	public InteractionResult onUseBlock(ServerPlayer player, BlockPos pos) {
		if (player.level().dimension() != arena.dimension) return InteractionResult.PASS;
		int statue = arena.statueAt(pos);
		int hand = arena.handAt(pos);
		if (statue < 0 && hand < 0) return InteractionResult.PASS;
		if (!players.contains(player.getUUID())) return InteractionResult.PASS;
		ServerLevel level = arena.level(server);

		if (phase != Phase.DISSECTION) {
			player.displayClientMessage(Component.literal("The statues are silent. Shoot the Witness!").withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.SUCCESS;
		}
		ItemStack held = player.getMainHandItem();
		Shape shape = Essence.shapeOf(held);
		if (shape == null) {
			player.displayClientMessage(Component.literal("Hold a shape essence (kill a Shadow Knight) and right-click to dunk.")
					.withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.SUCCESS;
		}
		if (statue >= 0) dunkStatue(level, player, statue, shape, held);
		else dunkHand(level, player, hand, shape, held);
		return InteractionResult.SUCCESS;
	}

	private void dunkStatue(ServerLevel level, ServerPlayer player, int s, Shape shape, ItemStack held) {
		String where = Arena.POS_NAMES[s];
		if (statues[s][0] != shape && statues[s][1] != shape) {
			held.shrink(1);
			dissonance(level, player, "The " + where + " " + Shape.solidName(statues[s][0], statues[s][1])
					+ " holds no " + shape.displayName + ".");
			return;
		}
		if (pendingStatue == s) {
			player.displayClientMessage(Component.literal("Dunk into a DIFFERENT statue to finish the dissection.")
					.withStyle(ChatFormatting.YELLOW), true);
			return;
		}
		held.shrink(1);
		Vec3 c = arena.outside(Arena.STATUE_X[s] + 0.5, 2.0, Arena.STATUE_Z + 0.5);
		if (pendingStatue < 0) {
			pendingStatue = s;
			pendingShape = shape;
			playAt(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, 0.6f);
			broadcast(Component.literal("Dissecting ").withStyle(ChatFormatting.GRAY)
					.append(shape.nameText())
					.append(Component.literal(" from the " + where + " statue… now dunk another shape into a different statue.")
							.withStyle(ChatFormatting.GRAY)));
			return;
		}

		int a = pendingStatue;
		Shape moving = pendingShape;
		pendingStatue = -1;
		pendingShape = null;
		// The first statue gives up `moving` and receives `shape`; this statue does the reverse.
		replaceOne(statues[a], moving, shape);
		replaceOne(statues[s], shape, moving);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 40, 0.5, 1.0, 0.5, 0.05);
		playAt(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 2f, 1.4f);
		if (moving == shape) {
			broadcast(Component.literal("Swapped a " + shape.displayName + " for a " + shape.displayName + "… nothing changes.")
					.withStyle(ChatFormatting.GRAY));
		} else {
			broadcast(Component.literal("Dissected: ").withStyle(ChatFormatting.GOLD)
					.append(solidText(statues[a])).append(Component.literal(" " + Arena.POS_NAMES[a] + "   ").withStyle(ChatFormatting.GRAY))
					.append(solidText(statues[s])).append(Component.literal(" " + where).withStyle(ChatFormatting.GRAY)));
		}
		refreshDisplays(level);
		checkTruth(level);
	}

	private void dunkHand(ServerLevel level, ServerPlayer player, int h, Shape shape, ItemStack held) {
		held.shrink(1);
		if (shape == callouts[insideSlot]) {
			dissonance(level, player, "You cannot offer your own shape.");
			return;
		}
		hands[h] = shape;
		Vec3 c = arena.inside(Arena.HAND_X[h] + 0.5, 1.5, Arena.HAND_Z + 0.5);
		level.sendParticles(ParticleTypes.WITCH, c.x, c.y, c.z, 20, 0.3, 0.5, 0.3, 0.05);
		playAt(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, 1.0f);
		refreshDisplays(level);
		checkTruth(level);
	}

	private void dissonance(ServerLevel level, ServerPlayer player, String reason) {
		mistakes++;
		player.hurt(level.damageSources().magic(), 4f);
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
		player.playNotifySound(SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1f, 0.5f);
		broadcast(Component.literal("DISSONANCE ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
				.append(Component.literal(reason + " (" + mistakes + "/" + MAX_MISTAKES + ")").withStyle(ChatFormatting.RED)));
		if (mistakes >= MAX_MISTAKES) wipe(level, "Too much Dissonance.");
	}

	private void checkTruth(ServerLevel level) {
		boolean out = outsideTrue();
		boolean in = insideTrue();
		if (out && in) {
			broadcast(Component.literal("The Truth is revealed!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
			startDamage(level);
			return;
		}
		if (out && !outsideTrueAnnounced) {
			outsideTrueAnnounced = true;
			broadcast(Component.literal("The statues speak the Truth. Waiting on the Shadow Realm…").withStyle(ChatFormatting.GREEN));
		}
		if (in && !insideTrueAnnounced && !solo) {
			insideTrueAnnounced = true;
			broadcast(Component.literal("The shadow's offerings are true. Waiting on the statues…").withStyle(ChatFormatting.GREEN));
		}
	}

	private boolean outsideTrue() {
		for (int i = 0; i < 3; i++) {
			Shape[] want = callouts[i].complement();
			if (!Shape.sameSet(statues[i][0], statues[i][1], want[0], want[1])) return false;
		}
		return true;
	}

	private boolean insideTrue() {
		Shape[] want = callouts[insideSlot].complement();
		return Shape.sameSet(hands[0], hands[1], want[0], want[1]);
	}

	private static void replaceOne(Shape[] pair, Shape from, Shape to) {
		if (pair[0] == from) pair[0] = to;
		else if (pair[1] == from) pair[1] = to;
	}

	// ================================================================= knights

	private void tickKnights(ServerLevel level) {
		outsideKnights.removeIf(id -> {
			Entity e = level.getEntity(id);
			return e == null || !e.isAlive();
		});
		insideKnights.removeIf(id -> {
			Entity e = level.getEntity(id);
			return e == null || !e.isAlive();
		});
		if (--outsideSpawnCooldown <= 0) {
			outsideSpawnCooldown = 160;
			if (outsideKnights.size() < 3) {
				int[] spot = OUTSIDE_KNIGHT_SPAWNS[random.nextInt(OUTSIDE_KNIGHT_SPAWNS.length)];
				spawnKnight(level, arena.outside(spot[0] + 0.5, 0, spot[1] + 0.5), false);
			}
		}
		if (!solo && --insideSpawnCooldown <= 0) {
			insideSpawnCooldown = 200;
			if (insideKnights.size() < 2) {
				int[] spot = INSIDE_KNIGHT_SPAWNS[random.nextInt(INSIDE_KNIGHT_SPAWNS.length)];
				spawnKnight(level, arena.inside(spot[0] + 0.5, 0, spot[1] + 0.5), true);
			}
		}
	}

	private void spawnKnight(ServerLevel level, Vec3 pos, boolean inside) {
		WitherSkeleton knight = EntityType.WITHER_SKELETON.create(level);
		if (knight == null) return;
		knight.moveTo(pos.x, pos.y, pos.z, random.nextFloat() * 360f, 0f);
		knight.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(pos)), MobSpawnType.MOB_SPAWNED, null);
		knight.setCustomName(Component.literal("Shadow Knight").withStyle(ChatFormatting.DARK_PURPLE));
		knight.setCustomNameVisible(true);
		knight.setPersistenceRequired();
		knight.setDropChance(EquipmentSlot.MAINHAND, 0f);
		knight.addTag(VerityMod.TAG);
		knight.addTag(MOB_TAG);
		knight.addTag(KNIGHT_TAG);
		if (inside) knight.addTag(INSIDE_TAG);
		level.addFreshEntity(knight);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 1, pos.z, 20, 0.3, 0.8, 0.3, 0.02);
		(inside ? insideKnights : outsideKnights).add(knight.getUUID());
	}

	/** A Shadow Knight died: drop an essence, biased towards shapes that help. */
	public void onKnightDeath(LivingEntity knight) {
		if (phase != Phase.DISSECTION) return;
		ServerLevel level = arena.level(server);
		if (level == null || knight.level() != level) return;
		boolean inside = knight.getTags().contains(INSIDE_TAG);
		List<Shape> useful = new ArrayList<>();
		if (inside) {
			for (Shape s : callouts[insideSlot].complement()) {
				if (hands[0] != s && hands[1] != s) useful.add(s);
			}
		} else {
			// Shapes some statue holds but shouldn't: those need to move.
			for (int i = 0; i < 3; i++) {
				for (Shape s : statues[i]) {
					if (s == callouts[i]) useful.add(s);
				}
			}
			for (int i = 0; i < 3; i++) {
				if (statues[i][0] == statues[i][1]) useful.add(statues[i][0]);
			}
		}
		Shape drop = !useful.isEmpty() && random.nextFloat() < 0.75f
				? useful.get(random.nextInt(useful.size()))
				: Shape.values()[random.nextInt(3)];
		ItemEntity item = new ItemEntity(level, knight.getX(), knight.getY() + 0.5, knight.getZ(), Essence.create(drop));
		item.setGlowingTag(true);
		level.addFreshEntity(item);
		level.sendParticles(ParticleTypes.ENCHANT, knight.getX(), knight.getY() + 1, knight.getZ(), 30, 0.4, 0.6, 0.4, 0.5);
	}

	// ================================================================= the witness

	private Vec3 witnessHome() {
		return arena.outside(0.5, 0, Arena.WITNESS_Z + 0.5);
	}

	private java.util.Optional<Evoker> findWitness(ServerLevel level) {
		if (witnessId == null) return java.util.Optional.empty();
		return level.getEntity(witnessId) instanceof Evoker e ? java.util.Optional.of(e) : java.util.Optional.empty();
	}

	private void spawnWitness(ServerLevel level) {
		Evoker w = EntityType.EVOKER.create(level);
		if (w == null) return;
		Vec3 home = witnessHome();
		w.moveTo(home.x, home.y, home.z, 0f, 0f);
		w.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(home)), MobSpawnType.MOB_SPAWNED, null);
		w.setCustomName(Component.literal("The Witness").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
		w.setCustomNameVisible(true);
		float hp = solo ? WITNESS_HP_SOLO : WITNESS_HP_DUO;
		var maxHealth = w.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) maxHealth.setBaseValue(hp);
		var knockback = w.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knockback != null) knockback.setBaseValue(1.0);
		w.setHealth(hp);
		w.setPersistenceRequired();
		w.setNoAi(phase != Phase.DAMAGE);
		w.addTag(VerityMod.TAG);
		w.addTag(MOB_TAG);
		w.addTag(WITNESS_TAG);
		level.addFreshEntity(w);
		witnessId = w.getUUID();
	}

	// ================================================================= displays

	private void refreshDisplays(ServerLevel level) {
		arena.clearDisplays(level);

		// Outside statues.
		for (int i = 0; i < 3; i++) {
			MutableComponent text = Component.literal(Arena.POS_NAMES[i]).withStyle(ChatFormatting.GRAY);
			if (solo) {
				text.append(Component.literal("  callout ").withStyle(ChatFormatting.DARK_GRAY)).append(callouts[i].glyphText());
			}
			text.append("\n");
			if (phase == Phase.DISSECTION) {
				text.append(Component.literal(Shape.solidName(statues[i][0], statues[i][1]).toUpperCase() + "\n")
						.withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
				text.append(statues[i][0].glyphText()).append("  ").append(statues[i][1].glyphText());
			} else {
				text.append(Component.literal("TRUE").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
			}
			arena.spawnText(level, arena.outside(Arena.STATUE_X[i] + 0.5, 4.1, Arena.STATUE_Z + 0.5), text, 1.6f);
		}

		if (solo) return;

		// Shadow Realm callouts.
		for (int i = 0; i < 3; i++) {
			MutableComponent text = Component.literal(Arena.POS_NAMES[i]).withStyle(ChatFormatting.GRAY);
			if (i == insideSlot) text.append(Component.literal(" (YOU)").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
			text.append("\n").append(callouts[i].glyphText());
			arena.spawnText(level, arena.inside(Arena.CALLOUT_X[i] + 0.5, 2.1, Arena.CALLOUT_Z + 0.5), text, 2.0f);
		}
		boolean handsTrue = insideTrue();
		String[] handNames = {"OFFERING I", "OFFERING II"};
		for (int h = 0; h < 2; h++) {
			MutableComponent text = Component.literal(handNames[h] + "\n").withStyle(ChatFormatting.GRAY)
					.append(hands[h].glyphText());
			arena.spawnText(level, arena.inside(Arena.HAND_X[h] + 0.5, 2.1, Arena.HAND_Z + 0.5), text, 1.6f);
		}
		MutableComponent header = Component.literal("You are the " + Arena.POS_NAMES[insideSlot] + " shadow. Your shape is ")
				.withStyle(ChatFormatting.GRAY).append(callouts[insideSlot].glyphText())
				.append(Component.literal("\nOfferings must be the two shapes that are not yours.").withStyle(ChatFormatting.GRAY));
		if (handsTrue) header.append(Component.literal("\nOFFERINGS TRUE ✔").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
		arena.spawnText(level, arena.inside(0.5, 3.4, -5.5), header, 0.9f);
	}

	private void updateTimerBar() {
		int secs = Math.max(0, ticksLeft / 20);
		String clock = String.format("%d:%02d", secs / 60, secs % 60);
		MutableComponent name = Component.literal("Round " + round + "/" + MAX_ROUNDS + "  ·  ").withStyle(ChatFormatting.GRAY);
		if (phase == Phase.DISSECTION) {
			name.append(Component.literal("Dissection " + clock).withStyle(ChatFormatting.WHITE))
					.append(Component.literal("  ·  Dissonance " + mistakes + "/" + MAX_MISTAKES)
							.withStyle(mistakes > 0 ? ChatFormatting.RED : ChatFormatting.GRAY));
			timerBar.setColor(ticksLeft < 30 * 20 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.WHITE);
		} else {
			name.append(Component.literal("DAMAGE " + clock).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
			timerBar.setColor(BossEvent.BossBarColor.YELLOW);
		}
		timerBar.setName(name);
		timerBar.setProgress(Math.max(0f, Math.min(1f, ticksLeft / (float) phaseLength)));
	}

	// ================================================================= helpers

	private static MutableComponent solidText(Shape[] pair) {
		return Component.literal(Shape.solidName(pair[0], pair[1]) + " ").withStyle(ChatFormatting.WHITE)
				.append(pair[0].glyphText()).append(pair[1].glyphText());
	}

	private ServerPlayer insidePlayer() {
		return insideId == null ? null : server.getPlayerList().getPlayer(insideId);
	}

	private List<ServerPlayer> onlinePlayers() {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID id : players) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) out.add(p);
		}
		return out;
	}

	private void broadcast(Component message) {
		for (ServerPlayer p : onlinePlayers()) p.sendSystemMessage(message);
	}

	private static void playAt(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.BLOCKS, volume, pitch);
	}

	static void title(ServerPlayer p, Component title, Component subtitle) {
		p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 15));
		p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		p.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	static void give(ServerPlayer p, ItemStack stack) {
		if (!p.getInventory().add(stack)) p.drop(stack, false);
	}
}
