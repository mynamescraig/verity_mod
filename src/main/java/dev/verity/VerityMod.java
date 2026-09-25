package dev.verity;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

public final class VerityMod implements ModInitializer {
	public static final String MOD_ID = "verity";
	public static final String TAG = "verity";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register(Arena::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Encounter e = Encounter.current();
			if (e != null) e.stop();
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Encounter e = Encounter.current();
			if (e != null) e.tick();
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Encounter e = Encounter.current();
			if (e != null && entity.getTags().contains(Encounter.KNIGHT_TAG)) e.onKnightDeath(entity);
		});

		// The Witness only takes damage while exposed.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!entity.getTags().contains(Encounter.WITNESS_TAG)) return true;
			Encounter e = Encounter.current();
			return e != null && e.phase() == Encounter.Phase.DAMAGE;
		});

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			Encounter e = Encounter.current();
			if (e == null || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			return e.onUseBlock(sp, hit.getBlockPos());
		});

		// Survival players can't dig out of (or into) the arena.
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			Arena arena = Arena.get();
			return arena == null || player.isCreative() || !arena.contains(world, pos);
		});

		LOGGER.info("Verity loaded. The Witness is watching.");
	}

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("verity")
				.executes(ctx -> help(ctx.getSource()))
				.then(Commands.literal("help").executes(ctx -> help(ctx.getSource())))
				.then(Commands.literal("build").requires(s -> s.hasPermission(2)).executes(ctx -> build(ctx.getSource())))
				.then(Commands.literal("clear").requires(s -> s.hasPermission(2)).executes(ctx -> clear(ctx.getSource())))
				.then(Commands.literal("start").requires(s -> s.hasPermission(2))
						.executes(ctx -> startAuto(ctx.getSource()))
						.then(Commands.argument("partner", EntityArgument.player())
								.executes(ctx -> start(ctx.getSource(), List.of(
										ctx.getSource().getPlayerOrException(),
										EntityArgument.getPlayer(ctx, "partner"))))))
				.then(Commands.literal("solo").requires(s -> s.hasPermission(2))
						.executes(ctx -> start(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException()))))
				.then(Commands.literal("stop").requires(s -> s.hasPermission(2)).executes(ctx -> stop(ctx.getSource())))
				.then(Commands.literal("kit").requires(s -> s.hasPermission(2))
						.executes(ctx -> kit(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException())))
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(ctx -> kit(ctx.getSource(), new ArrayList<>(EntityArgument.getPlayers(ctx, "targets")))))));
	}

	private static int help(CommandSourceStack source) {
		String[] lines = {
				"/verity build — build the arena where you stand (needs ~70x35 blocks of space)",
				"/verity start — start with the other online player (or /verity start <partner>)",
				"/verity solo — practice alone (you play outside, callouts shown on statues)",
				"/verity kit — gear up (iron armor, sword, bow, food)",
				"/verity stop — end the encounter",
				"/verity clear — remove the arena"
		};
		source.sendSuccess(() -> Component.literal("Verity").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
		for (String line : lines) source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int build(CommandSourceStack source) throws CommandSyntaxException {
		if (Encounter.current() != null) {
			source.sendFailure(Component.literal("Stop the encounter before rebuilding."));
			return 0;
		}
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = player.serverLevel();
		Arena old = Arena.get();
		if (old != null && old.level(source.getServer()) != null) old.clearDisplays(old.level(source.getServer()));
		Arena arena = new Arena(level.dimension(), player.blockPosition());
		arena.build(level);
		Arena.set(arena);
		arena.save(source.getServer());
		source.sendSuccess(() -> Component.literal("Arena built. The Shadow Realm is 48 blocks east. Run /verity start when ready.")
				.withStyle(ChatFormatting.LIGHT_PURPLE), true);
		return 1;
	}

	private static int clear(CommandSourceStack source) {
		if (Encounter.current() != null) {
			source.sendFailure(Component.literal("Stop the encounter first."));
			return 0;
		}
		Arena arena = Arena.get();
		if (arena == null) {
			source.sendFailure(Component.literal("There is no arena."));
			return 0;
		}
		ServerLevel level = arena.level(source.getServer());
		if (level != null) arena.demolish(level);
		Arena.forget(source.getServer());
		source.sendSuccess(() -> Component.literal("Arena removed."), true);
		return 1;
	}

	private static int startAuto(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer self = source.getPlayerOrException();
		List<ServerPlayer> others = new ArrayList<>(source.getServer().getPlayerList().getPlayers());
		others.remove(self);
		if (others.isEmpty()) return start(source, List.of(self));
		if (others.size() == 1) return start(source, List.of(self, others.get(0)));
		source.sendFailure(Component.literal("More than two players online. Use /verity start <partner>."));
		return 0;
	}

	private static int start(CommandSourceStack source, List<ServerPlayer> players) {
		if (players.size() == 2 && players.get(0) == players.get(1)) {
			source.sendFailure(Component.literal("Pick someone other than yourself (or use /verity solo)."));
			return 0;
		}
		Component error = Encounter.start(source.getServer(), players);
		if (error != null) {
			source.sendFailure(error);
			return 0;
		}
		return 1;
	}

	private static int stop(CommandSourceStack source) {
		Encounter e = Encounter.current();
		if (e == null) {
			source.sendFailure(Component.literal("No encounter is running."));
			return 0;
		}
		e.stop();
		source.sendSuccess(() -> Component.literal("Encounter stopped."), true);
		return 1;
	}

	private static int kit(CommandSourceStack source, List<ServerPlayer> targets) {
		for (ServerPlayer p : targets) {
			equip(p, EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			equip(p, EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			equip(p, EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
			equip(p, EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			equip(p, EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			Encounter.give(p, new ItemStack(Items.IRON_SWORD));
			ItemStack bow = new ItemStack(Items.BOW);
			Holder<Enchantment> infinity = p.serverLevel().registryAccess()
					.registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.INFINITY);
			bow.enchant(infinity, 1);
			Encounter.give(p, bow);
			Encounter.give(p, new ItemStack(Items.ARROW, 1));
			Encounter.give(p, new ItemStack(Items.COOKED_BEEF, 32));
			Encounter.give(p, new ItemStack(Items.GOLDEN_APPLE, 4));
		}
		source.sendSuccess(() -> Component.literal("Geared up " + targets.size() + " Guardian(s)."), true);
		return targets.size();
	}

	private static void equip(ServerPlayer p, EquipmentSlot slot, ItemStack stack) {
		if (p.getItemBySlot(slot).isEmpty()) p.setItemSlot(slot, stack);
		else Encounter.give(p, stack);
	}
}
