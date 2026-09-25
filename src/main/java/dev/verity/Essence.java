package dev.verity;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/** Shape essences dropped by Shadow Knights. Plain vanilla items tagged with custom data. */
public final class Essence {
	private static final String KEY = "verity_shape";

	private Essence() {
	}

	public static ItemStack create(Shape shape) {
		ItemStack stack = new ItemStack(shape.item);
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, shape.name());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(shape.glyph + " " + shape.displayName + " Essence")
				.withStyle(style -> style.withColor(shape.color).withBold(true).withItalic(false)));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("Right-click a statue to dunk it.").withStyle(ChatFormatting.GRAY),
				Component.literal("Fades when the encounter ends.").withStyle(ChatFormatting.DARK_GRAY))));
		return stack;
	}

	/** Returns the shape of an essence stack, or null if it is not an essence. */
	public static Shape shapeOf(ItemStack stack) {
		if (stack.isEmpty()) return null;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return null;
		CompoundTag tag = data.copyTag();
		if (!tag.contains(KEY)) return null;
		try {
			return Shape.valueOf(tag.getString(KEY));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public static void clearFrom(ServerPlayer player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (shapeOf(inv.getItem(i)) != null) inv.setItem(i, ItemStack.EMPTY);
		}
	}
}
