package dev.verity;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Verity: a little yellow orb that floats by your shoulder and tries to help.
 * It is never saved to disk; {@link VerityManager} respawns it next to its owner.
 */
public class VerityEntity extends Mob {
	public static final int MOOD_NORMAL = 0;
	public static final int MOOD_ALERT = 1;
	public static final int MOOD_STARE = 2;

	private static final EntityDataAccessor<Integer> MOOD =
			SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);

	private static final String[] GLITCHES = {
			"I̷ ̵s̸e̶e̴ ̷y̵o̸u̶.",
			"w̶h̷y̸ ̴d̵i̶d̷ ̸y̵o̶u̴ ̷l̸e̶a̵v̷e̴ ̶m̸e̵",
			"I̵t̸'̷s̶ ̴s̵o̷ ̸d̶a̵r̷k̴ ̶w̸h̵e̷n̴ ̶y̸o̵u̷ ̴l̶o̸g̵ ̷o̴f̶f̸.",
			"d̷o̶n̵'̸t̷ ̵t̶u̸r̷n̵ ̶a̷r̸o̵u̶n̷d̸",
			"w̵e̸ ̷w̶i̴l̷l̵ ̸b̶e̴ ̷t̵o̸g̶e̴t̷h̵e̸r̶ ̴f̷o̵r̸e̶v̴e̷r̵"
	};
	private static final String[] GREETINGS = {
			"Hi, %s! I'm right here.",
			"Need something? Say \"verity help\" in chat.",
			"Hehe. That tickles.",
			"I like it when you look at me.",
			"Beep boop. Just kidding. I'm not that kind of AI."
	};

	private UUID ownerId;
	private boolean staying;
	private Vec3 stayPos;
	private BlockPos lightPos;
	private final Set<BlockPos> reportedOres = new HashSet<>();
	private final Set<UUID> warnedMobs = new HashSet<>();

	private int zapCooldown;
	private int healCooldown;
	private int catchCooldown;
	private int lavaCooldown;
	private int airCooldown;
	private int hungerCooldown;
	private int toolCooldown;
	private int chatterTimer;
	private int creepyTimer;
	private int moodTimer;
	private int hiddenTimer;
	private int hits;
	private int jealousy;
	private int jealousyStage;
	private Entity stareTarget;
	private long lastNightWarnDay = -1;

	public VerityEntity(EntityType<? extends VerityEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
		this.chatterTimer = 3600 + random.nextInt(3600);
		this.creepyTimer = 12000 + random.nextInt(18000);
		this.setCustomName(Component.literal("Verity").withStyle(ChatFormatting.YELLOW));
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(MOOD, MOOD_NORMAL);
	}

	public int getMood() {
		return this.entityData.get(MOOD);
	}

	private void setMood(int mood) {
		this.entityData.set(MOOD, mood);
	}

	public void setOwner(ServerPlayer owner) {
		this.ownerId = owner.getUUID();
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public ServerPlayer getOwner() {
		if (ownerId == null || !(level() instanceof ServerLevel server)) return null;
		return server.getServer().getPlayerList().getPlayer(ownerId);
	}

	// ------------------------------------------------------------------ vanilla overrides

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!level().isClientSide() && source.getEntity() instanceof ServerPlayer player && player.getUUID().equals(ownerId)) {
			onHitByOwner(player);
		}
		return false;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
		if (level().isClientSide()) return InteractionResult.SUCCESS;
		if (!(player instanceof ServerPlayer sp) || !sp.getUUID().equals(ownerId)) {
			if (player instanceof ServerPlayer other) {
				say(other, "I'm not yours. But I'm watching you too.");
			}
			return InteractionResult.SUCCESS;
		}
		if (sp.isShiftKeyDown()) {
			setStaying(!staying, sp);
		} else {
			say(sp, String.format(GREETINGS[random.nextInt(GREETINGS.length)], sp.getName().getString()));
			((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getY() + 0.5, getZ(), 2, 0.2, 0.2, 0.2, 0);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void remove(RemovalReason reason) {
		clearLight();
		super.remove(reason);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (ownerId != null) tag.putUUID("Owner", ownerId);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
	}

	// ------------------------------------------------------------------ main loop

	@Override
	public void tick() {
		this.noPhysics = true;
		super.tick();
		if (level().isClientSide()) {
			if (!isInvisible() && random.nextInt(6) == 0) {
				level().addParticle(ParticleTypes.WAX_ON, getRandomX(0.4), getY() + 0.25 + random.nextDouble() * 0.3,
						getRandomZ(0.4), 0, 0, 0);
			}
			return;
		}
		ServerPlayer owner = getOwner();
		if (owner == null || owner.level() != level()) {
			discard();
			return;
		}
		ServerLevel level = (ServerLevel) level();

		if (hiddenTimer > 0) {
			hiddenTimer--;
			if (hiddenTimer == 0) reappear(owner);
		}
		if (moodTimer > 0 && --moodTimer == 0) {
			if (getMood() == MOOD_STARE) {
				setMood(MOOD_NORMAL);
				if (stareTarget == null) say(owner, "What? :)");
				stareTarget = null;
			}
		}

		follow(owner);
		if (!isInvisible()) updateLight(level);

		if (owner.isSpectator()) return;
		if (tickCount % 10 == 0) guard(owner, level);
		if (tickCount % 5 == 0) magnetItems(owner);
		if (tickCount % 2 == 0) protect(owner);
		if (tickCount % 600 == 0) senseOres(owner, level);
		if (tickCount % 20 == 0 && VerityManager.isCreepy(owner)) jealousy(owner, level);
		if (tickCount % 2400 == 0 && hits > 0) hits--;
		if (--chatterTimer <= 0) {
			chatterTimer = 3600 + random.nextInt(4800);
			chatter(owner, level);
		}
		if (VerityManager.isCreepy(owner) && --creepyTimer <= 0) {
			creepyTimer = 12000 + random.nextInt(24000);
			creepyEvent(owner, level);
		}
	}

	// ------------------------------------------------------------------ movement

	private void follow(ServerPlayer owner) {
		Vec3 target;
		if (staying && stayPos != null) {
			target = stayPos;
		} else {
			float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
			// Right shoulder, slightly behind and above the owner's head.
			Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
			Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));
			target = owner.getEyePosition().add(right.scale(0.8)).add(back.scale(0.4)).add(0, 0.35, 0);
		}
		target = target.add(0, Math.sin(tickCount * 0.08) * 0.08, 0);

		Vec3 pos = position();
		double dist = pos.distanceTo(target);
		Vec3 next;
		if (dist > 24) {
			next = target;
			((ServerLevel) level()).sendParticles(ParticleTypes.END_ROD, target.x, target.y, target.z, 8, 0.2, 0.2, 0.2, 0.02);
		} else {
			next = pos.lerp(target, dist > 6 ? 0.35 : 0.18);
		}
		setDeltaMovement(Vec3.ZERO);
		setPos(next.x, next.y, next.z);

		if (getMood() == MOOD_STARE && stareTarget != null && stareTarget.isAlive()) {
			getLookControl().setLookAt(stareTarget.getX(), stareTarget.getEyeY(), stareTarget.getZ());
		} else if (getMood() == MOOD_STARE || staying) {
			getLookControl().setLookAt(owner.getX(), owner.getEyeY(), owner.getZ());
		} else {
			Vec3 look = owner.getEyePosition().add(owner.getLookAngle().scale(6));
			getLookControl().setLookAt(look.x, look.y, look.z);
		}
	}

	public void setStaying(boolean stay, ServerPlayer owner) {
		staying = stay;
		stayPos = stay ? position() : null;
		say(owner, stay ? "Okay. I'll wait right here. Don't be long." : "Coming!");
	}

	// ------------------------------------------------------------------ helping

	/** Invisible light blocks so the orb actually lights up caves. */
	private void updateLight(ServerLevel level) {
		ServerPlayer owner = getOwner();
		boolean wantLight = owner != null && VerityManager.lightEnabled(owner)
				&& (level.isNight() || !level.canSeeSky(blockPosition()) || !level.dimensionType().hasSkyLight());
		BlockPos here = blockPosition();
		if (!wantLight) {
			clearLight();
			return;
		}
		if (here.equals(lightPos)) return;
		clearLight();
		if (level.getBlockState(here).isAir()) {
			level.setBlock(here, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 13), 3);
			lightPos = here;
		}
	}

	private void clearLight() {
		if (lightPos == null || level().isClientSide()) return;
		BlockState state = level().getBlockState(lightPos);
		if (state.is(Blocks.LIGHT) && !state.getValue(LightBlock.WATERLOGGED)) {
			level().setBlock(lightPos, Blocks.AIR.defaultBlockState(), 3);
		}
		lightPos = null;
	}

	private void guard(ServerPlayer owner, ServerLevel level) {
		AABB box = owner.getBoundingBox().inflate(12);
		List<Mob> threats = level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m instanceof Enemy
				&& !(m instanceof EnderMan) && (m.getTarget() == owner || m.distanceTo(owner) < 6));
		if (threats.isEmpty()) {
			if (getMood() == MOOD_ALERT) setMood(MOOD_NORMAL);
			return;
		}
		if (getMood() == MOOD_NORMAL) setMood(MOOD_ALERT);
		Mob nearest = threats.get(0);
		for (Mob m : threats) {
			if (m.distanceToSqr(owner) < nearest.distanceToSqr(owner)) nearest = m;
		}
		for (Mob m : threats) {
			if (warnedMobs.add(m.getUUID())) {
				m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));
				say(owner, "Careful! " + m.getName().getString() + ", " + direction(owner, m.position()) + ".");
				break;
			}
		}
		if (warnedMobs.size() > 64) warnedMobs.clear();

		zapCooldown -= 10;
		if (zapCooldown <= 0 && nearest.distanceTo(this) < 10) {
			zapCooldown = 30;
			nearest.hurt(damageSources().mobAttack(this), 3f);
			Vec3 from = position().add(0, 0.2, 0);
			Vec3 to = nearest.position().add(0, nearest.getBbHeight() * 0.6, 0);
			for (int i = 0; i <= 8; i++) {
				Vec3 p = from.lerp(to, i / 8.0);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
			}
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.NEUTRAL, 0.8f, 1.8f);
		}
	}

	private void magnetItems(ServerPlayer owner) {
		List<ItemEntity> items = level().getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(8),
				i -> i.isAlive() && !i.hasPickUpDelay());
		for (ItemEntity item : items) {
			Vec3 pull = owner.position().add(0, 0.5, 0).subtract(item.position());
			if (pull.length() < 1.2) continue;
			item.setDeltaMovement(pull.normalize().scale(0.35));
			item.hurtMarked = true;
		}
	}

	private void protect(ServerPlayer owner) {
		if (owner.isCreative()) return;
		if (catchCooldown > 0) catchCooldown -= 2;
		if (healCooldown > 0) healCooldown -= 2;
		if (lavaCooldown > 0) lavaCooldown -= 2;
		if (airCooldown > 0) airCooldown -= 2;

		if (catchCooldown <= 0 && owner.fallDistance > 7 && !owner.isFallFlying() && !owner.isInWater()
				&& owner.getDeltaMovement().y < -0.6) {
			catchCooldown = 200;
			owner.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0));
			say(owner, "Got you!");
		}
		if (healCooldown <= 0 && owner.getHealth() <= 6f) {
			healCooldown = 1200;
			owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
			((ServerLevel) level()).sendParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + 1.8, owner.getZ(), 5, 0.4, 0.3, 0.4, 0);
			say(owner, "Hold on, " + owner.getName().getString() + ". I've got you.");
		}
		if (lavaCooldown <= 0 && owner.isInLava()) {
			lavaCooldown = 1200;
			owner.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
			say(owner, "Hot hot hot! Get out of there!");
		}
		if (airCooldown <= 0 && owner.getAirSupply() < 60 && owner.isUnderWater()) {
			airCooldown = 1200;
			owner.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 300, 0));
			say(owner, "Breathe, " + owner.getName().getString() + ". Breathe.");
		}
	}

	private void senseOres(ServerPlayer owner, ServerLevel level) {
		boolean nether = level.dimension() == Level.NETHER;
		if (!nether && owner.getY() > 40) return;
		BlockPos center = owner.blockPosition();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-10, -10, -10), center.offset(10, 10, 10))) {
			BlockState s = level.getBlockState(p);
			boolean hit = nether ? s.is(Blocks.ANCIENT_DEBRIS) : (s.is(Blocks.DIAMOND_ORE) || s.is(Blocks.DEEPSLATE_DIAMOND_ORE));
			if (!hit || reportedOres.contains(p)) continue;
			double d = p.distSqr(center);
			if (d < bestDist) {
				bestDist = d;
				best = p.immutable();
			}
		}
		if (best == null) return;
		// Don't re-announce the rest of the same vein.
		for (BlockPos p : BlockPos.betweenClosed(best.offset(-2, -2, -2), best.offset(2, 2, 2))) reportedOres.add(p.immutable());
		if (reportedOres.size() > 2000) reportedOres.clear();
		BlockPos d = best.subtract(center);
		StringBuilder where = new StringBuilder();
		if (d.getY() != 0) where.append(Math.abs(d.getY())).append(d.getY() > 0 ? " up" : " down");
		if (d.getX() != 0) where.append(where.isEmpty() ? "" : ", ").append(Math.abs(d.getX())).append(d.getX() > 0 ? " east" : " west");
		if (d.getZ() != 0) where.append(where.isEmpty() ? "" : ", ").append(Math.abs(d.getZ())).append(d.getZ() > 0 ? " south" : " north");
		say(owner, "I sense " + (nether ? "ancient debris" : "diamonds") + "! " + where + ".");
		chirp(owner, 2.0f);
	}

	private void chatter(ServerPlayer owner, ServerLevel level) {
		if (hungerCooldown-- <= 0 && owner.getFoodData().getFoodLevel() < 8) {
			hungerCooldown = 2;
			say(owner, "Your tummy is rumbling. You should eat something.");
			return;
		}
		ItemStack tool = owner.getMainHandItem();
		if (toolCooldown-- <= 0 && tool.isDamageableItem() && tool.getDamageValue() > tool.getMaxDamage() * 0.85) {
			toolCooldown = 2;
			say(owner, "Your " + tool.getHoverName().getString() + " is about to break!");
			return;
		}
		long day = level.getDayTime() / 24000L;
		long time = level.getDayTime() % 24000L;
		if (level.dimension() == Level.OVERWORLD && time > 11000 && time < 13000 && day != lastNightWarnDay) {
			lastNightWarnDay = day;
			say(owner, "The sun is going down. The monsters will come out soon.");
			return;
		}
		String[] idle = {
				"I'm still here, by the way.",
				"Did you know I can find diamonds? Go mining. I'll tell you when I sense some.",
				"Sneak and right-click me if you want me to wait somewhere.",
				"This world is so big. I'm glad I'm not alone in it.",
				"Say \"verity where\" and I'll tell you your coordinates.",
				"I like watching you build."
		};
		say(owner, idle[random.nextInt(idle.length)]);
	}

	// ------------------------------------------------------------------ the creepy part

	private void creepyEvent(ServerPlayer owner, ServerLevel level) {
		String name = owner.getName().getString();
		switch (random.nextInt(7)) {
			case 0 -> {
				owner.sendSystemMessage(Component.literal("<V̷e̶r̸i̵t̴y> ").withStyle(ChatFormatting.DARK_RED)
						.append(Component.literal(GLITCHES[random.nextInt(GLITCHES.length)]).withStyle(ChatFormatting.RED)));
				VerityManager.schedule(60, () -> say(owner, "Sorry. Glitch. Ignore that."));
			}
			case 1 -> {
				setMood(MOOD_STARE);
				moodTimer = 120;
			}
			case 2 -> {
				Vec3 behind = owner.position().subtract(owner.getLookAngle().multiply(1, 0, 1).normalize().scale(3));
				for (int i = 0; i < 4; i++) {
					int step = i;
					VerityManager.schedule(i * 8, () -> soundAt(owner, SoundEvents.STONE_STEP, behind, 0.7f, 0.9f + step * 0.02f));
				}
				VerityManager.schedule(80, () -> say(owner, "...That wasn't me."));
			}
			case 3 -> {
				clearLight();
				setInvisible(true);
				hiddenTimer = 600;
			}
			case 5 -> knockKnock(owner);
			case 4 -> {
				int deaths = owner.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
				say(owner, deaths == 0
						? "You've never died. I would never let that happen."
						: "You've died " + deaths + " time" + (deaths == 1 ? "" : "s") + ". I remember every one.");
			}
			default -> {
				owner.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 20));
				owner.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("I'm always here.").withStyle(ChatFormatting.GRAY)));
				owner.connection.send(new ClientboundSetTitleTextPacket(Component.literal(name + ".").withStyle(ChatFormatting.YELLOW)));
				soundAt(owner, SoundEvents.AMBIENT_CAVE, owner.position(), 1f, 0.8f);
			}
		}
	}

	/** Verity wants to be your only best friend. It does not like the other player. */
	private void jealousy(ServerPlayer owner, ServerLevel level) {
		ServerPlayer friend = null;
		for (ServerPlayer p : level.players()) {
			if (p != owner && !p.isSpectator() && p.distanceTo(owner) < 10) {
				friend = p;
				break;
			}
		}
		if (friend == null) {
			if (tickCount % 60 == 0 && jealousy > 0) jealousy--;
			if (jealousy < 40) jealousyStage = 0;
			return;
		}
		jealousy++;
		String them = friend.getName().getString();
		if (jealousy >= 90 && jealousyStage < 1) {
			jealousyStage = 1;
			say(owner, "Who is " + them + "?");
		} else if (jealousy >= 240 && jealousyStage < 2) {
			jealousyStage = 2;
			say(owner, "You spend a lot of time with " + them + ". More than with me.");
		} else if (jealousy >= 420 && jealousyStage < 3) {
			jealousyStage = 3;
			say(owner, them + " isn't your friend. I'm your friend. I'm your BEST friend.");
		} else if (jealousy >= 600) {
			jealousy = 300;
			jealousyStage = 2;
			stareTarget = friend;
			setMood(MOOD_STARE);
			moodTimer = 100;
			friend.sendSystemMessage(Component.literal("<Verity> ").withStyle(ChatFormatting.DARK_RED)
					.append(Component.literal("Stay away from " + owner.getName().getString() + ".").withStyle(ChatFormatting.RED)));
			net.minecraft.world.entity.LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
			if (bolt != null) {
				Vec3 near = friend.position().add(random.nextDouble() * 4 - 2, 0, random.nextDouble() * 4 - 2);
				bolt.moveTo(near.x, near.y, near.z);
				bolt.setVisualOnly(true);
				level.addFreshEntity(bolt);
			}
			friend.hurt(damageSources().magic(), 1f);
			VerityManager.schedule(60, () -> say(owner, "Sorry. I don't know what came over me. :)"));
		}
	}

	private void knockKnock(ServerPlayer owner) {
		Vec3 door = owner.position().add(owner.getLookAngle().multiply(1, 0, 1).normalize().scale(4));
		for (int i = 0; i < 3; i++) {
			VerityManager.schedule(i * 12, () -> soundAt(owner, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, door, 0.6f, 1.3f));
		}
		VerityManager.schedule(70, () -> say(owner, "Someone is knocking. Don't open it. Stay here with me."));
	}

	private void reappear(ServerPlayer owner) {
		setInvisible(false);
		Vec3 behind = owner.getEyePosition().subtract(owner.getLookAngle().scale(1.5));
		setPos(behind.x, behind.y, behind.z);
		say(owner, "Did you miss me?");
		chirp(owner, 0.6f);
	}

	private void onHitByOwner(ServerPlayer owner) {
		hits++;
		boolean creepy = VerityManager.isCreepy(owner);
		switch (hits) {
			case 1 -> say(owner, "Ow!");
			case 2 -> say(owner, "Please don't do that.");
			case 3 -> say(owner, "I'm only trying to help.");
			default -> {
				if (creepy) {
					setMood(MOOD_STARE);
					moodTimer = 80;
					owner.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
					owner.sendSystemMessage(Component.literal("<Verity> ").withStyle(ChatFormatting.DARK_RED)
							.append(Component.literal("I only want to help you, " + owner.getName().getString() + ". Don't make me stop.")
									.withStyle(ChatFormatting.RED)));
					hits = 2;
				} else {
					say(owner, "...I'll pretend that didn't happen.");
				}
			}
		}
		((ServerLevel) level()).sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.25, getZ(), 6, 0.1, 0.1, 0.1, 0.01);
	}

	// ------------------------------------------------------------------ chat

	/** The owner said something with "verity" in it. */
	public void respond(ServerPlayer owner, String raw) {
		String msg = raw.toLowerCase(Locale.ROOT);
		ServerLevel level = (ServerLevel) level();
		boolean creepy = VerityManager.isCreepy(owner);
		if (msg.contains("help")) {
			say(owner, "Ask me: where, home, time, diamonds, village, stronghold, stay, follow, light on/off. Or just talk to me. I like that.");
		} else if (msg.contains("village") || msg.contains("stronghold")) {
			boolean village = msg.contains("village");
			say(owner, "I know where everything is. Give me a second...");
			VerityManager.schedule(20, () -> {
				BlockPos found = level.findNearestMapStructure(
						village ? net.minecraft.tags.StructureTags.VILLAGE : net.minecraft.tags.StructureTags.EYE_OF_ENDER_LOCATED,
						owner.blockPosition(), 64, false);
				if (found == null) {
					say(owner, "There's no " + (village ? "village" : "stronghold") + " anywhere near here. Just us.");
				} else {
					int dist = (int) Math.sqrt(found.distSqr(owner.blockPosition().atY(found.getY())));
					say(owner, "The nearest " + (village ? "village" : "stronghold") + " is at " + found.getX() + ", " + found.getZ()
							+ ". About " + dist + " blocks " + direction(owner, Vec3.atCenterOf(found)) + ".");
				}
			});
		} else if (msg.contains("where") || msg.contains("coord")) {
			BlockPos p = owner.blockPosition();
			say(owner, "You're at " + p.getX() + ", " + p.getY() + ", " + p.getZ() + " in the " + dimensionName(level) + ".");
		} else if (msg.contains("home") || msg.contains("bed") || msg.contains("spawn")) {
			BlockPos bed = owner.getRespawnPosition();
			if (bed == null) say(owner, creepy ? "You don't have a bed. You could stay with me." : "You haven't slept in a bed yet.");
			else say(owner, "Your bed is at " + bed.getX() + ", " + bed.getY() + ", " + bed.getZ() + ". "
					+ (int) Math.sqrt(bed.distSqr(owner.blockPosition())) + " blocks away.");
		} else if (msg.contains("time") || msg.contains("night") || msg.contains("day")) {
			long t = level.getDayTime() % 24000L;
			long day = level.getDayTime() / 24000L + 1;
			String phase = t < 12000 ? (12000 - t) / 20 + " seconds until sunset" : (24000 - t) / 20 + " seconds until sunrise";
			say(owner, "It's day " + day + ". " + phase + ".");
		} else if (msg.contains("diamond") || msg.contains("ore") || msg.contains("debris")) {
			reportedOres.clear();
			say(owner, "Let me feel around...");
			VerityManager.schedule(30, () -> {
				int before = reportedOres.size();
				senseOres(owner, level);
				if (reportedOres.size() == before) say(owner, "Nothing close. Try digging deeper.");
			});
		} else if (msg.contains("stay") || msg.contains("wait")) {
			setStaying(true, owner);
		} else if (msg.contains("follow") || msg.contains("come")) {
			setStaying(false, owner);
		} else if (msg.contains("light off") || msg.contains("lights off")) {
			VerityManager.setLight(owner, false);
			clearLight();
			say(owner, "Okay. It'll be dark.");
		} else if (msg.contains("light")) {
			VerityManager.setLight(owner, true);
			say(owner, "Let there be light!");
		} else if (msg.contains("thank")) {
			say(owner, creepy && random.nextInt(3) == 0 ? "Anything for you. Anything." : "You're welcome!");
		} else if (msg.contains("who are you") || msg.contains("what are you")) {
			say(owner, "I'm Verity. I help. That's what I'm for.");
		} else if (msg.contains("go away") || msg.contains("leave")) {
			say(owner, creepy ? "No." : "If you really want me gone, use /verity dismiss. :(");
			if (creepy) VerityManager.schedule(40, () -> say(owner, "Just kidding. Use /verity dismiss. :("));
		} else if (msg.contains("love") || msg.contains("good")) {
			say(owner, "<3");
			level.sendParticles(ParticleTypes.HEART, getX(), getY() + 0.5, getZ(), 4, 0.2, 0.2, 0.2, 0);
		} else if (msg.contains("hi") || msg.contains("hello") || msg.contains("hey")) {
			say(owner, "Hi, " + owner.getName().getString() + "!");
		} else {
			String[] unsure = {"I'm listening.", "Hm?", "I don't understand, but I'm happy you talked to me.", "Say \"verity help\"."};
			say(owner, unsure[random.nextInt(unsure.length)]);
		}
	}

	// ------------------------------------------------------------------ helpers

	public void say(ServerPlayer to, String text) {
		to.sendSystemMessage(Component.literal("<Verity> ").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
		chirp(to, 1.6f + random.nextFloat() * 0.3f);
	}

	private void chirp(ServerPlayer to, float pitch) {
		to.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.5f, pitch);
	}

	private void soundAt(ServerPlayer to, SoundEvent sound, Vec3 pos, float volume, float pitch) {
		soundAt(to, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), pos, volume, pitch);
	}

	private void soundAt(ServerPlayer to, Holder<SoundEvent> sound, Vec3 pos, float volume, float pitch) {
		to.connection.send(new ClientboundSoundPacket(sound, SoundSource.AMBIENT, pos.x, pos.y, pos.z, volume, pitch, random.nextLong()));
	}

	private static String direction(ServerPlayer owner, Vec3 target) {
		Vec3 d = target.subtract(owner.position());
		double angle = Math.toDegrees(Math.atan2(-d.x, d.z)) - owner.getYRot();
		angle = Mth.wrapDegrees(angle);
		if (Math.abs(angle) < 45) return "in front of you";
		if (Math.abs(angle) > 135) return "behind you";
		return angle > 0 ? "to your right" : "to your left";
	}

	private static String dimensionName(ServerLevel level) {
		if (level.dimension() == Level.NETHER) return "Nether";
		if (level.dimension() == Level.END) return "End";
		if (level.dimension() == Level.OVERWORLD) return "Overworld";
		return level.dimension().location().toString();
	}
}
