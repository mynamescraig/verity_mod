package dev.verity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class VerityMod implements ModInitializer {
	public static final String MOD_ID = "verity";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final EntityType<VerityEntity> VERITY = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceLocation.fromNamespaceAndPath(MOD_ID, "verity"),
			EntityType.Builder.<VerityEntity>of(VerityEntity::new, MobCategory.MISC)
					.sized(0.4f, 0.4f)
					.clientTrackingRange(10)
					.updateInterval(1)
					.build("verity"));

	@Override
	public void onInitialize() {
		VerityConfig.load();
		FabricDefaultAttributeRegistry.register(VERITY, VerityEntity.createAttributes());

		ServerTickEvents.END_SERVER_TICK.register(VerityManager::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(VerityManager::onStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> VerityManager.onStopping());
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) VerityManager.onRespawn(newPlayer);
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> VerityManager.onJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> VerityManager.onLeave(handler.getPlayer()));
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
				VerityManager.onChat(sender, message.signedContent()));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));

		LOGGER.info("Verity is online. Hello.");
	}

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("verity")
				.executes(VerityMod::help)
				.then(Commands.literal("help").executes(VerityMod::help))
				.then(Commands.literal("summon").executes(ctx -> {
					VerityManager.summon(ctx.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("dismiss").executes(ctx -> {
					VerityManager.dismiss(ctx.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("stay").executes(ctx -> stay(ctx, true)))
				.then(Commands.literal("follow").executes(ctx -> stay(ctx, false)))
				.then(Commands.literal("friendship")
						.executes(VerityMod::friendship)
						.then(Commands.literal("set").requires(src -> src.hasPermission(2))
								.then(Commands.argument("minutes", IntegerArgumentType.integer(0)).executes(ctx -> {
									ServerPlayer p = ctx.getSource().getPlayerOrException();
									VerityManager.setBond(p, IntegerArgumentType.getInteger(ctx, "minutes"));
									return friendship(ctx);
								}))))
				.then(Commands.literal("creepy")
						.then(Commands.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							boolean on = BoolArgumentType.getBool(ctx, "enabled");
							VerityManager.setCreepy(p, on);
							reply(ctx, on ? "Creepy mode on. Verity is... Verity." : "Creepy mode off. Verity is just a nice little orb now.");
							return 1;
						})))
				.then(Commands.literal("light")
						.then(Commands.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							boolean on = BoolArgumentType.getBool(ctx, "enabled");
							VerityManager.setLight(p, on);
							reply(ctx, on ? "Verity will light up the dark." : "Verity won't place light.");
							return 1;
						}))));
	}

	private static int friendship(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		int minutes = VerityManager.bond(p);
		int stage = VerityManager.stage(p);
		boolean creepy = VerityManager.isCreepy(p);
		StringBuilder hearts = new StringBuilder();
		for (int i = 0; i < 4; i++) hearts.append(i <= stage ? (creepy && stage == 3 ? "∞" : "♥") : "♡");
		String title = creepy ? VerityManager.STAGE_NAMES[stage] : (stage == 0 ? "New Friend" : "Friend");
		ctx.getSource().sendSuccess(() -> Component.literal("Verity's friendship: ").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(hearts + " ").withStyle(creepy && stage == 3 ? ChatFormatting.DARK_RED : ChatFormatting.RED))
				.append(Component.literal(title).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
				.append(Component.literal("  (" + minutes + " minutes together)").withStyle(ChatFormatting.GRAY)), false);
		return 1;
	}

	private static int stay(CommandContext<CommandSourceStack> ctx, boolean stay) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		VerityEntity v = VerityManager.get(p);
		if (v == null) {
			ctx.getSource().sendFailure(Component.literal("Verity isn't with you. Try /verity summon."));
			return 0;
		}
		v.setStaying(stay, p);
		return 1;
	}

	private static int help(CommandContext<CommandSourceStack> ctx) {
		String[] lines = {
				"/verity summon  (call Verity to you)",
				"/verity dismiss  (send Verity away)",
				"/verity stay | follow",
				"/verity friendship  (how close you two are)",
				"/verity creepy true|false",
				"/verity light true|false",
				"Or say \"verity help\" in chat."
		};
		ctx.getSource().sendSuccess(() -> Component.literal("Verity").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), false);
		for (String line : lines) ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static void reply(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.YELLOW), false);
	}
}
