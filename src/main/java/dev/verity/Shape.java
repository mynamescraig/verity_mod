package dev.verity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** The three 2D shapes. Two of them together make a 3D shape. */
public enum Shape {
	CIRCLE("Circle", "●", ChatFormatting.GOLD, Items.HEART_OF_THE_SEA),
	TRIANGLE("Triangle", "▲", ChatFormatting.AQUA, Items.AMETHYST_SHARD),
	SQUARE("Square", "■", ChatFormatting.GREEN, Items.BRICK);

	public final String displayName;
	public final String glyph;
	public final ChatFormatting color;
	public final Item item;

	Shape(String displayName, String glyph, ChatFormatting color, Item item) {
		this.displayName = displayName;
		this.glyph = glyph;
		this.color = color;
		this.item = item;
	}

	public MutableComponent glyphText() {
		return Component.literal(glyph).withStyle(color, ChatFormatting.BOLD);
	}

	public MutableComponent nameText() {
		return Component.literal(glyph + " " + displayName).withStyle(color, ChatFormatting.BOLD);
	}

	/** The two shapes that are not {@code this}. */
	public Shape[] complement() {
		Shape[] out = new Shape[2];
		int i = 0;
		for (Shape s : values()) {
			if (s != this) out[i++] = s;
		}
		return out;
	}

	/** True if the pair {a, b} is the same multiset as {x, y}. */
	public static boolean sameSet(Shape a, Shape b, Shape x, Shape y) {
		return (a == x && b == y) || (a == y && b == x);
	}

	public static String solidName(Shape a, Shape b) {
		if (a == b) {
			return switch (a) {
				case CIRCLE -> "Sphere";
				case TRIANGLE -> "Pyramid";
				case SQUARE -> "Cube";
			};
		}
		boolean c = a == CIRCLE || b == CIRCLE;
		boolean t = a == TRIANGLE || b == TRIANGLE;
		if (c && t) return "Cone";
		if (c) return "Cylinder";
		return "Prism";
	}
}
