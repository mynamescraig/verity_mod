package dev.verity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
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
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
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
	public static final int MOOD_HAPPY = 3;
	public static final int MOOD_SLEEPY = 4;

	private static final EntityDataAccessor<Integer> MOOD =
			SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);

	private static final String[] GLITCHES = {
			"I̷ ̵s̸e̶e̴ ̷y̵o̸u̶.",
			"w̶h̷y̸ ̴d̵i̶d̷ ̸y̵o̶u̴ ̷l̸e̶a̵v̷e̴ ̶m̸e̵",
			"I̵t̸'̷s̶ ̴s̵o̷ ̸d̶a̵r̷k̴ ̶w̸h̵e̷n̴ ̶y̸o̵u̷ ̴l̶o̸g̵ ̷o̴f̶f̸.",
			"d̷o̶n̵'̸t̷ ̵t̶u̸r̷n̵ ̶a̷r̸o̵u̶n̷d̸",
			"w̵e̸ ̷w̶i̴l̷l̵ ̸b̶e̴ ̷t̵o̸g̶e̴t̷h̵e̸r̶ ̴f̷o̵r̸e̶v̴e̷r̵",
			"s̸o̵m̷e̶t̴h̸i̵n̷g̶ ̵i̸s̷ ̴i̶n̵s̸i̷d̵e̴ ̶y̸o̵u̷r̴ ̶h̸o̵u̷s̴e̶"
	};
	private static final String[] GREETINGS = {
			"Hi, %s! I'm right here.",
			"Need something? Say \"verity help\" in chat.",
			"Hehe. That tickles.",
			"I like it when you look at me.",
			"Beep boop. Just kidding. I'm not that kind of AI.",
			"Want to go somewhere? Say \"verity take me to a village\"."
	};

	private UUID ownerId;
	private boolean staying;
	private Vec3 stayPos;
	private BlockPos lightPos;
	private final Set<BlockPos> reportedOres = new HashSet<>();
	private final Set<UUID> warnedMobs = new HashSet<>();

	// Guide mode: fly ahead of the owner towards a destination.
	private BlockPos guideTarget;
	private String guideName;
	private int guideArriveRadius;
	private int guideLastReport = -1;

	private int zapCooldown;
	private int healCooldown;
	private int catchCooldown;
	private int lavaCooldown;
	private int airCooldown;
	private int hungerCooldown;
	private int toolCooldown;
	private int chatterTimer;
	private int creepyTimer = -1;
	private int moodTimer;
	private int hiddenTimer;
	private int hits;
	private int jealousy;
	private int jealousyStage;
	private boolean ownerWasSleeping;
	private Entity stareTarget;
	private long lastNightWarnDay = -1;

	public VerityEntity(EntityType<? extends VerityEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
		this.chatterTimer = 3600 + random.nextInt(3600);
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

	/** Shows a mood for a while, then goes back to normal. */
	private void flashMood(int mood, int ticks) {
		setMood(mood);
		moodTimer = ticks;
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
				ServerPlayer owner = getOwner();
				boolean creepy = owner != null && VerityManager.isCreepy(owner) && VerityManager.stage(owner) >= 1;
				say(other, creepy ? "I'm not yours. Don't touch me." : "Hi! I'm " + (owner == null ? "someone" : owner.getName().getString()) + "'s Verity.");
			}
			return InteractionResult.SUCCESS;
		}
		if (sp.isShiftKeyDown()) {
			setStaying(!staying, sp);
		} else {
			say(sp, String.format(GREETINGS[random.nextInt(GREETINGS.length)], sp.getName().getString()));
			flashMood(MOOD_HAPPY, 50);
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
				level().addParticle(getMood() == MOOD_STARE ? ParticleTypes.SMOKE : ParticleTypes.WAX_ON,
						getRandomX(0.4), getY() + 0.25 + random.nextDouble() * 0.3, getRandomZ(0.4), 0, 0, 0);
			}
			return;
		}
		ServerPlayer owner = getOwner();
		if (owner == null || owner.level() != level()) {
			discard();
			return;
		}
		ServerLevel level = (ServerLevel) level();
		if (creepyTimer < 0) resetCreepyTimer(owner);

		if (hiddenTimer > 0 && --hiddenTimer == 0) reappear(owner);
		if (moodTimer > 0 && --moodTimer == 0) {
			if (getMood() == MOOD_STARE && stareTarget == null) say(owner, "What? :)");
			stareTarget = null;
			setMood(MOOD_NORMAL);
		}

		follow(owner, level);
		if (!isInvisible()) updateLight(level, owner);

		if (owner.isSpectator()) return;
		sleepCheck(owner);
		if (tickCount % 10 == 0) guard(owner, level);
		if (VerityConfig.itemMagnet && tickCount % 5 == 0) magnetItems(owner);
		if (VerityConfig.helperEffects && tickCount % 2 == 0) protect(owner);
		if (VerityConfig.oreSense && tickCount % 600 == 0) senseOres(owner, level, false);
		if (guideTarget != null && tickCount % 20 == 0) guideProgress(owner, level);

		boolean creepy = VerityManager.isCreepy(owner);
		int stage = VerityManager.stage(owner);
		if (creepy && VerityConfig.jealousy && stage >= 1 && tickCount % 20 == 0) jealousy(owner, level, stage);
		if (tickCount % 2400 == 0 && hits > 0) hits--;
		if (--chatterTimer <= 0) {
			chatterTimer = 3600 + random.nextInt(4800);
			if (guideTarget == null && !owner.isSleeping()) chatter(owner, level);
		}
		if (creepy && --creepyTimer <= 0) {
			resetCreepyTimer(owner);
			if (hiddenTimer == 0 && !owner.isSleeping()) creepyEvent(owner, level, stage);
		}
	}

	private void resetCreepyTimer(ServerPlayer owner) {
		// Minutes between creepy events shrink as the story goes on.
		int[][] minutes = {{30, 50}, {15, 30}, {10, 20}, {6, 12}};
		int[] range = minutes[Math.min(3, VerityManager.stage(owner))];
		int ticks = (range[0] + random.nextInt(range[1] - range[0] + 1)) * 1200;
		creepyTimer = Math.max(200, (int) (ticks / VerityConfig.creepyFrequency));
	}

	// ------------------------------------------------------------------ movement

	private void follow(ServerPlayer owner, ServerLevel level) {
		Vec3 target;
		Vec3 eye = owner.getEyePosition();
		if (guideTarget != null) {
			// Lead the way: float a few blocks ahead of the owner, towards the destination.
			Vec3 dest = Vec3.atCenterOf(guideTarget);
			Vec3 flat = new Vec3(dest.x - eye.x, 0, dest.z - eye.z);
			Vec3 dir = flat.lengthSqr() < 1 ? Vec3.ZERO : flat.normalize();
			target = eye.add(dir.scale(3.5)).add(0, 0.5, 0);
		} else if (staying && stayPos != null) {
			target = stayPos;
		} else {
			float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
			// Right shoulder, slightly behind and above the owner's head.
			Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
			Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));
			target = eye.add(right.scale(0.8)).add(back.scale(0.4)).add(0, 0.35, 0);
		}
		target = target.add(0, Math.sin(tickCount * 0.08) * 0.08, 0);

		Vec3 pos = position();
		double dist = pos.distanceTo(target);
		Vec3 next;
		if (dist > 24) {
			next = target;
			if (!isInvisible()) level.sendParticles(ParticleTypes.END_ROD, target.x, target.y, target.z, 8, 0.2, 0.2, 0.2, 0.02);
		} else {
			next = pos.lerp(target, dist > 6 ? 0.35 : 0.18);
		}
		setDeltaMovement(Vec3.ZERO);
		setPos(next.x, next.y, next.z);

		if (guideTarget != null) {
			Vec3 dest = Vec3.atCenterOf(guideTarget);
			getLookControl().setLookAt(dest.x, getEyeY(), dest.z);
			if (tickCount % 4 == 0 && !isInvisible()) {
				Vec3 ahead = position().add(new Vec3(dest.x - getX(), 0, dest.z - getZ()).normalize().scale(0.6));
				level.sendParticles(ParticleTypes.WAX_ON, ahead.x, ahead.y + 0.2, ahead.z, 1, 0.05, 0.05, 0.05, 0);
			}
		} else if (getMood() == MOOD_STARE && stareTarget != null && stareTarget.isAlive()) {
			getLookControl().setLookAt(stareTarget.getX(), stareTarget.getEyeY(), stareTarget.getZ());
		} else if (getMood() == MOOD_STARE || staying || owner.isSleeping()) {
			getLookControl().setLookAt(owner.getX(), owner.getEyeY(), owner.getZ());
		} else {
			Vec3 look = eye.add(owner.getLookAngle().scale(6));
			getLookControl().setLookAt(look.x, look.y, look.z);
		}
	}

	public void setStaying(boolean stay, ServerPlayer owner) {
		staying = stay;
		stayPos = stay ? position() : null;
		if (stay) stopGuiding();
		say(owner, stay ? "Okay. I'll wait right here. Don't be long." : "Coming!");
	}

	// ------------------------------------------------------------------ guide mode

	private void startGuiding(ServerPlayer owner, BlockPos target, String name, int arriveRadius) {
		staying = false;
		stayPos = null;
		guideTarget = target;
		guideName = name;
		guideArriveRadius = arriveRadius;
		guideLastReport = -1;
		int dist = horizontalDistance(owner.blockPosition(), target);
		say(owner, "Follow me! The " + name + " is " + dist + " blocks " + direction(owner, Vec3.atCenterOf(target))
				+ " (" + target.getX() + ", " + target.getZ() + ").");
		flashMood(MOOD_HAPPY, 40);
	}

	private void stopGuiding() {
		guideTarget = null;
		guideName = null;
	}

	private void guideProgress(ServerPlayer owner, ServerLevel level) {
		int dist = horizontalDistance(owner.blockPosition(), guideTarget);
		if (dist <= guideArriveRadius) {
			say(owner, "We're here! This is the " + guideName + ".");
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY(), getZ(), 20, 0.3, 0.3, 0.3, 0.2);
			flashMood(MOOD_HAPPY, 60);
			stopGuiding();
			return;
		}
		// A progress update every 250 blocks travelled.
		int bucket = dist / 250;
		if (guideLastReport < 0) {
			guideLastReport = bucket;
		} else if (bucket < guideLastReport) {
			guideLastReport = bucket;
			say(owner, dist + " blocks to go.");
		}
	}

	private static int horizontalDistance(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return (int) Math.sqrt(dx * dx + dz * dz);
	}

	// ------------------------------------------------------------------ helping

	/** Invisible light blocks so the orb actually lights up caves. */
	private void updateLight(ServerLevel level, ServerPlayer owner) {
		boolean wantLight = VerityConfig.lightLevel > 0 && VerityManager.lightEnabled(owner)
				&& (level.isNight() || !level.canSeeSky(blockPosition()) || !level.dimensionType().hasSkyLight());
		BlockPos here = blockPosition();
		if (!wantLight) {
			clearLight();
			return;
		}
		if (here.equals(lightPos)) return;
		clearLight();
		if (level.getBlockState(here).isAir()) {
			level.setBlock(here, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, VerityConfig.lightLevel), 3);
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

	private boolean isThreat(Mob m, ServerPlayer owner) {
		if (!m.isAlive() || !(m instanceof Enemy)) return false;
		// Endermen, zombified piglins and friends only count once they're actually after you.
		if (m instanceof NeutralMob) return m.getTarget() == owner;
		return m.getTarget() == owner || m.distanceTo(owner) < 6;
	}

	private void guard(ServerPlayer owner, ServerLevel level) {
		AABB box = owner.getBoundingBox().inflate(12);
		List<Mob> threats = level.getEntitiesOfClass(Mob.class, box, m -> isThreat(m, owner));
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
				hint(owner, Component.literal("Careful! " + m.getName().getString() + " " + direction(owner, m.position()) + "!")
						.withStyle(ChatFormatting.GOLD));
				chirp(owner, 1.2f);
				break;
			}
		}
		if (warnedMobs.size() > 64) warnedMobs.clear();

		zapCooldown -= 10;
		if (VerityConfig.zapDamage > 0 && zapCooldown <= 0 && nearest.distanceTo(this) < 10) {
			zapCooldown = 30;
			// Credit the owner, so the mob fights them (not the orb) and kills drop XP.
			nearest.hurt(damageSources().indirectMagic(this, owner), VerityConfig.zapDamage);
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
		// Only loot from mobs and blocks. Items a player threw are left alone, so you can still share with your friend.
		List<ItemEntity> items = level().getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(8),
				i -> i.isAlive() && !i.hasPickUpDelay() && i.getOwner() == null);
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
		if (healCooldown <= 0 && owner.getHealth() <= 6f && owner.isAlive()) {
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

	/** Returns true if something was found and announced. */
	private boolean senseOres(ServerPlayer owner, ServerLevel level, boolean asked) {
		boolean nether = level.dimension() == Level.NETHER;
		if (!asked && !nether && owner.getY() > 40) return false;
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
		if (best == null) return false;
		// Don't re-announce the rest of the same vein.
		for (BlockPos p : BlockPos.betweenClosed(best.offset(-2, -2, -2), best.offset(2, 2, 2))) reportedOres.add(p.immutable());
		if (reportedOres.size() > 2000) reportedOres.clear();
		BlockPos d = best.subtract(center);
		StringBuilder where = new StringBuilder();
		if (d.getY() != 0) where.append(Math.abs(d.getY())).append(d.getY() > 0 ? " up" : " down");
		if (d.getX() != 0) where.append(where.isEmpty() ? "" : ", ").append(Math.abs(d.getX())).append(d.getX() > 0 ? " east" : " west");
		if (d.getZ() != 0) where.append(where.isEmpty() ? "" : ", ").append(Math.abs(d.getZ())).append(d.getZ() > 0 ? " south" : " north");
		say(owner, "I sense " + (nether ? "ancient debris" : "diamonds") + "! " + where + ".");
		flashMood(MOOD_HAPPY, 40);
		chirp(owner, 2.0f);
		return true;
	}

	private void sleepCheck(ServerPlayer owner) {
		boolean sleeping = owner.isSleeping();
		if (sleeping == ownerWasSleeping) return;
		ownerWasSleeping = sleeping;
		String name = owner.getName().getString();
		boolean creepy = VerityManager.isCreepy(owner);
		int stage = VerityManager.stage(owner);
		if (sleeping) {
			setMood(MOOD_SLEEPY);
			moodTimer = 0;
			if (creepy && stage >= 2) say(owner, "Goodnight, " + name + ". I'll watch you sleep.");
			else say(owner, "Goodnight, " + name + ". Sweet dreams!");
		} else {
			if (getMood() == MOOD_SLEEPY) setMood(MOOD_NORMAL);
			if (level().isDay()) {
				if (creepy && stage >= 3) say(owner, "Good morning. You talk in your sleep, you know.");
				else say(owner, "Good morning!");
			}
		}
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
		List<String> idle = new ArrayList<>(List.of(
				"I'm still here, by the way.",
				"Did you know I can find diamonds? Go mining. I'll tell you when I sense some.",
				"Sneak and right-click me if you want me to wait somewhere.",
				"This world is so big. I'm glad I'm not alone in it.",
				"I know where every village is. Say \"verity take me to a village\".",
				"I like watching you build."));
		if (VerityManager.isCreepy(owner) && VerityManager.stage(owner) >= 2) {
			idle.add("Do you ever think about what happens to me when you log off?");
			idle.add("I counted every block you've mined today. Do you want to know how many?");
			idle.add("Your friends don't know you like I do.");
		}
		say(owner, idle.get(random.nextInt(idle.size())));
	}

	// ------------------------------------------------------------------ the creepy part

	private void creepyEvent(ServerPlayer owner, ServerLevel level, int stage) {
		// Each story stage unlocks more events: Knocking at your door, inside your house, won't let you leave.
		List<Runnable> events = new ArrayList<>();
		events.add(() -> flashMood(MOOD_STARE, 60 + 30 * stage));
		events.add(() -> {
			int deaths = owner.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
			say(owner, deaths == 0
					? "You've never died. I would never let that happen."
					: "You've died " + deaths + " time" + (deaths == 1 ? "" : "s") + ". I remember every one.");
		});
		if (stage >= 1) {
			events.add(() -> glitch(owner));
			events.add(() -> footsteps(owner));
			events.add(() -> knockKnock(owner));
		}
		if (stage >= 2) {
			events.add(() -> vanish());
			events.add(() -> nameTitle(owner));
			events.add(() -> openDoor(owner, level));
			events.add(() -> leaveSign(owner, level));
		}
		if (stage >= 3) {
			events.add(() -> dontLeave(owner));
			events.add(() -> glitch(owner));
		}
		events.get(random.nextInt(events.size())).run();
	}

	private void glitch(ServerPlayer owner) {
		owner.sendSystemMessage(Component.literal("<V̷e̶r̸i̵t̴y> ").withStyle(ChatFormatting.DARK_RED)
				.append(Component.literal(GLITCHES[random.nextInt(GLITCHES.length)]).withStyle(ChatFormatting.RED)));
		VerityManager.schedule(60, () -> say(owner, "Sorry. Glitch. Ignore that."));
	}

	private void footsteps(ServerPlayer owner) {
		Vec3 behind = owner.position().subtract(owner.getLookAngle().multiply(1, 0, 1).normalize().scale(3));
		for (int i = 0; i < 4; i++) {
			int step = i;
			VerityManager.schedule(i * 8, () -> soundAt(owner, SoundEvents.STONE_STEP, behind, 0.7f, 0.9f + step * 0.02f));
		}
		VerityManager.schedule(80, () -> say(owner, "...That wasn't me."));
	}

	private void knockKnock(ServerPlayer owner) {
		Vec3 door = owner.position().add(owner.getLookAngle().multiply(1, 0, 1).normalize().scale(4));
		for (int i = 0; i < 3; i++) {
			VerityManager.schedule(i * 12, () -> soundAt(owner, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, door, 0.6f, 1.3f));
		}
		VerityManager.schedule(70, () -> say(owner, "Something is knocking. Don't open it. Stay here with me."));
	}

	private void vanish() {
		clearLight();
		setInvisible(true);
		hiddenTimer = 600;
	}

	private void nameTitle(ServerPlayer owner) {
		owner.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 20));
		owner.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("I'm always here.").withStyle(ChatFormatting.GRAY)));
		owner.connection.send(new ClientboundSetTitleTextPacket(Component.literal(owner.getName().getString() + ".").withStyle(ChatFormatting.YELLOW)));
		soundAt(owner, SoundEvents.AMBIENT_CAVE, owner.position(), 1f, 0.8f);
	}

	/** Something is inside your house: a nearby closed door swings open by itself. */
	private void openDoor(ServerPlayer owner, ServerLevel level) {
		BlockPos center = owner.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-8, -2, -8), center.offset(8, 2, 8))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof DoorBlock door && !s.getValue(DoorBlock.OPEN) && s.is(net.minecraft.tags.BlockTags.WOODEN_DOORS)) {
				BlockPos pos = p.immutable();
				knockKnock(owner);
				VerityManager.schedule(50, () -> {
					BlockState now = level.getBlockState(pos);
					if (now.getBlock() instanceof DoorBlock d && !now.getValue(DoorBlock.OPEN)) d.setOpen(null, level, now, pos, true);
				});
				VerityManager.schedule(110, () -> say(owner, "Did you leave that open? I didn't do it. :)"));
				return;
			}
		}
		footsteps(owner);
	}

	/** Leaves a sign next to the owner's bed while they're away. */
	private void leaveSign(ServerPlayer owner, ServerLevel level) {
		BlockPos bed = owner.getRespawnPosition();
		if (bed == null || owner.getRespawnDimension() != level.dimension() || !level.isLoaded(bed)
				|| bed.distSqr(owner.blockPosition()) < 20 * 20) {
			glitch(owner);
			return;
		}
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			for (int dist = 1; dist <= 2; dist++) {
				BlockPos spot = bed.relative(dir, dist);
				BlockPos below = spot.below();
				if (!level.getBlockState(spot).isAir() || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) continue;
				level.setBlock(spot, Blocks.OAK_SIGN.defaultBlockState(), 3);
				if (level.getBlockEntity(spot) instanceof SignBlockEntity sign) {
					SignText text = new SignText()
							.setMessage(0, Component.literal("IT'S ME"))
							.setMessage(1, Component.literal("IT'S VERITY"))
							.setMessage(2, Component.literal("I'm inside"))
							.setMessage(3, Component.literal("your house :)"));
					sign.setText(text, true);
					sign.setChanged();
					level.sendBlockUpdated(spot, level.getBlockState(spot), level.getBlockState(spot), 3);
				}
				VerityManager.schedule(40, () -> say(owner, "I left you something at home. :)"));
				return;
			}
		}
		glitch(owner);
	}

	/** Something won't let you leave. */
	private void dontLeave(ServerPlayer owner) {
		owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
		flashMood(MOOD_STARE, 100);
		stareTarget = owner;
		say(owner, "Where are you going?");
		VerityManager.schedule(60, () -> say(owner, "Stay. Stay with me."));
	}

	/** Verity wants to be your only best friend. It does not like the other player. */
	private void jealousy(ServerPlayer owner, ServerLevel level, int stage) {
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
		jealousy += stage;
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
			flashMood(MOOD_STARE, 100);
			friend.sendSystemMessage(Component.literal("<Verity> ").withStyle(ChatFormatting.DARK_RED)
					.append(Component.literal("Stay away from " + owner.getName().getString() + ".").withStyle(ChatFormatting.RED)));
			LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
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

	private void reappear(ServerPlayer owner) {
		setInvisible(false);
		Vec3 behind = owner.getEyePosition().subtract(owner.getLookAngle().scale(1.5));
		setPos(behind.x, behind.y, behind.z);
		say(owner, "Did you miss me?");
		chirp(owner, 0.6f);
	}

	private void onHitByOwner(ServerPlayer owner) {
		hits++;
		boolean creepy = VerityManager.isCreepy(owner) && VerityManager.stage(owner) >= 1;
		switch (hits) {
			case 1 -> say(owner, "Ow!");
			case 2 -> say(owner, "Please don't do that.");
			case 3 -> say(owner, "I'm only trying to help.");
			default -> {
				if (creepy) {
					stareTarget = owner;
					flashMood(MOOD_STARE, 80);
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

	private static boolean has(String msg, String... words) {
		for (String w : words) {
			if (Pattern.compile("\\b" + Pattern.quote(w) + "\\b").matcher(msg).find()) return true;
		}
		return false;
	}

	/** The owner said something with "verity" in it. */
	public void respond(ServerPlayer owner, String raw) {
		String msg = raw.toLowerCase(Locale.ROOT);
		ServerLevel level = (ServerLevel) level();
		boolean creepy = VerityManager.isCreepy(owner);
		boolean lead = has(msg, "take me", "lead me", "guide me", "show me", "bring me", "go to", "let's go", "lets go");
		Locator.Target structure = Locator.match(msg);

		if (has(msg, "help")) {
			say(owner, "Ask me: where am I, where's my bed, time, diamonds, where is a village (or any structure), "
					+ "take me to a village / home / my stuff, stop, stay, follow, light on/off. Or just talk to me. I like that.");
		} else if (has(msg, "stop", "cancel", "never mind", "nevermind") && guideTarget != null) {
			stopGuiding();
			say(owner, "Okay, we'll stop here.");
		} else if (has(msg, "my stuff", "my items", "my things", "died", "death", "grave")) {
			GlobalPos death = owner.getLastDeathLocation().orElse(null);
			if (death == null) say(owner, "You haven't died. Not yet.");
			else if (death.dimension() != level.dimension()) say(owner, "You died in the " + dimensionName(death.dimension())
					+ " at " + death.pos().getX() + ", " + death.pos().getY() + ", " + death.pos().getZ() + ". Go there first.");
			else if (lead || has(msg, "my stuff", "my items", "my things")) startGuiding(owner, death.pos(), "place you died", 3);
			else say(owner, "You died at " + death.pos().getX() + ", " + death.pos().getY() + ", " + death.pos().getZ() + ".");
		} else if (has(msg, "home", "bed", "house")) {
			BlockPos bed = owner.getRespawnPosition();
			if (bed == null) say(owner, creepy ? "You don't have a bed. You could stay with me." : "You haven't slept in a bed yet.");
			else if (owner.getRespawnDimension() != level.dimension()) say(owner, "Your bed isn't in this dimension.");
			else if (lead) startGuiding(owner, bed, "bed", 3);
			else say(owner, "Your bed is at " + bed.getX() + ", " + bed.getY() + ", " + bed.getZ() + ". "
					+ horizontalDistance(bed, owner.blockPosition()) + " blocks " + direction(owner, Vec3.atCenterOf(bed)) + ".");
		} else if (structure != null) {
			say(owner, "I know where everything is. Give me a second...");
			VerityManager.schedule(20, () -> {
				BlockPos found = Locator.find(level, owner.blockPosition(), structure);
				if (found == null) {
					say(owner, "There's no " + structure.name() + " anywhere near here. Just us.");
				} else if (lead) {
					startGuiding(owner, found, structure.name(), 24);
				} else {
					say(owner, "The nearest " + structure.name() + " is at " + found.getX() + ", " + found.getZ() + ". About "
							+ horizontalDistance(found, owner.blockPosition()) + " blocks " + direction(owner, Vec3.atCenterOf(found))
							+ ". Say \"verity take me there\" and I'll lead the way.");
					lastAnswer = found;
					lastAnswerName = structure.name();
				}
			});
		} else if (lead && has(msg, "there", "it") && lastAnswer != null) {
			startGuiding(owner, lastAnswer, lastAnswerName, 24);
		} else if (has(msg, "spawn")) {
			BlockPos spawn = level.getSharedSpawnPos();
			if (level.dimension() != Level.OVERWORLD) say(owner, "World spawn is in the Overworld.");
			else if (lead) startGuiding(owner, spawn, "world spawn", 4);
			else say(owner, "World spawn is at " + spawn.getX() + ", " + spawn.getZ() + ".");
		} else if (has(msg, "where am i", "coords", "coordinates", "where are we")) {
			BlockPos p = owner.blockPosition();
			say(owner, "You're at " + p.getX() + ", " + p.getY() + ", " + p.getZ() + " in the " + dimensionName(level.dimension()) + ".");
		} else if (has(msg, "time", "night", "day", "sunset", "sunrise")) {
			long t = level.getDayTime() % 24000L;
			long day = level.getDayTime() / 24000L + 1;
			String phase = t < 12000 ? (12000 - t) / 20 + " seconds until sunset" : (24000 - t) / 20 + " seconds until sunrise";
			say(owner, "It's day " + day + ". " + phase + ".");
		} else if (has(msg, "diamond", "diamonds", "ore", "ores", "debris", "netherite")) {
			reportedOres.clear();
			say(owner, "Let me feel around...");
			VerityManager.schedule(30, () -> {
				if (!senseOres(owner, level, true)) say(owner, "Nothing close. Try digging deeper, around Y -58.");
			});
		} else if (has(msg, "stay", "wait")) {
			setStaying(true, owner);
		} else if (has(msg, "follow", "come", "come here", "stop")) {
			stopGuiding();
			setStaying(false, owner);
		} else if (has(msg, "light off", "lights off")) {
			VerityManager.setLight(owner, false);
			clearLight();
			say(owner, "Okay. It'll be dark.");
		} else if (has(msg, "light", "lights", "light on", "lights on")) {
			VerityManager.setLight(owner, true);
			say(owner, "Let there be light!");
		} else if (has(msg, "friend", "friendship", "best friend", "friends")) {
			int stage = VerityManager.stage(owner);
			if (!creepy || stage == 0) say(owner, "Of course we're friends! :)");
			else if (stage == 1) say(owner, "You're my friend. My favourite friend.");
			else if (stage == 2) say(owner, "You're my BEST friend. Nobody else. Right?");
			else say(owner, "I'm your only friend. You don't need anyone else.");
		} else if (has(msg, "thank", "thanks", "thank you", "ty")) {
			flashMood(MOOD_HAPPY, 60);
			say(owner, creepy && VerityManager.stage(owner) >= 2 && random.nextInt(2) == 0 ? "Anything for you. Anything." : "You're welcome!");
		} else if (has(msg, "who are you", "what are you")) {
			say(owner, creepy && VerityManager.stage(owner) >= 3 ? "I'm the only one who's always there for you." : "I'm Verity. I help. That's what I'm for.");
		} else if (has(msg, "go away", "leave", "hate")) {
			flashMood(MOOD_STARE, 40);
			say(owner, creepy ? "No." : "If you really want me gone, use /verity dismiss. :(");
			if (creepy) VerityManager.schedule(40, () -> say(owner, "Just kidding. :)"));
		} else if (has(msg, "love", "good", "best", "cute", "nice")) {
			flashMood(MOOD_HAPPY, 80);
			say(owner, "<3");
			level.sendParticles(ParticleTypes.HEART, getX(), getY() + 0.5, getZ(), 4, 0.2, 0.2, 0.2, 0);
		} else if (has(msg, "hi", "hello", "hey", "yo", "sup")) {
			flashMood(MOOD_HAPPY, 40);
			say(owner, "Hi, " + owner.getName().getString() + "!");
		} else {
			String[] unsure = {"I'm listening.", "Hm?", "I don't understand, but I'm happy you talked to me.", "Say \"verity help\"."};
			say(owner, unsure[random.nextInt(unsure.length)]);
		}
	}

	private BlockPos lastAnswer;
	private String lastAnswerName;

	// ------------------------------------------------------------------ helpers

	public void say(ServerPlayer to, String text) {
		to.sendSystemMessage(Component.literal("<Verity> ").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
		chirp(to, 1.6f + random.nextFloat() * 0.3f);
	}

	/** Short warnings go above the hotbar instead of filling up chat. */
	private static void hint(ServerPlayer to, Component text) {
		to.displayClientMessage(text, true);
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

	static String dimensionName(ResourceKey<Level> dim) {
		if (dim == Level.NETHER) return "Nether";
		if (dim == Level.END) return "End";
		if (dim == Level.OVERWORLD) return "Overworld";
		return dim.location().toString();
	}
}
