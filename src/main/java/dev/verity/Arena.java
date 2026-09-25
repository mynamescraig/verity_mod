package dev.verity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/**
 * Arena geometry. Everything is relative to {@link #origin}, the block the builder stood on.
 * Players face +Z to look at the statues, so LEFT is +X and RIGHT is -X.
 */
public final class Arena {
	public static final String[] POS_NAMES = {"LEFT", "MIDDLE", "RIGHT"};

	/** Outside chamber: interior is -OUT_R..OUT_R on X and Z. */
	public static final int OUT_R = 15;
	public static final int[] STATUE_X = {8, 0, -8};
	public static final int STATUE_Z = 10;
	public static final int WITNESS_Z = -10;

	/** Shadow Realm: a sealed room offset along +X. */
	public static final int IN_OFFSET_X = 48;
	public static final int IN_R = 7;
	public static final int[] CALLOUT_X = {4, 0, -4};
	public static final int CALLOUT_Z = 6;
	public static final int[] HAND_X = {3, -3};
	public static final int HAND_Z = -6;

	public static final String DISPLAY_TAG = "verity_disp";

	private static Arena loaded;

	public final ResourceKey<Level> dimension;
	public final BlockPos origin;

	public Arena(ResourceKey<Level> dimension, BlockPos origin) {
		this.dimension = dimension;
		this.origin = origin;
	}

	public static Arena get() {
		return loaded;
	}

	public static void set(Arena arena) {
		loaded = arena;
	}

	public ServerLevel level(MinecraftServer server) {
		return server.getLevel(dimension);
	}

	// ---------------------------------------------------------------- coordinates

	public Vec3 outside(double x, double y, double z) {
		return new Vec3(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
	}

	public Vec3 inside(double x, double y, double z) {
		return outside(IN_OFFSET_X + x, y, z);
	}

	public BlockPos outsideBlock(int x, int y, int z) {
		return origin.offset(x, y, z);
	}

	public BlockPos insideBlock(int x, int y, int z) {
		return origin.offset(IN_OFFSET_X + x, y, z);
	}

	/** Index of the outside statue at this block (any block of its pedestal or column), or -1. */
	public int statueAt(BlockPos pos) {
		int dx = pos.getX() - origin.getX();
		int dy = pos.getY() - origin.getY();
		int dz = pos.getZ() - origin.getZ();
		if (dy < 0 || dy > 4 || Math.abs(dz - STATUE_Z) > 1) return -1;
		for (int i = 0; i < 3; i++) {
			if (Math.abs(dx - STATUE_X[i]) <= 1) return i;
		}
		return -1;
	}

	/** Index of the Shadow Realm offering pedestal at this block, or -1. */
	public int handAt(BlockPos pos) {
		int dx = pos.getX() - origin.getX() - IN_OFFSET_X;
		int dy = pos.getY() - origin.getY();
		int dz = pos.getZ() - origin.getZ();
		if (dy < 0 || dy > 2 || Math.abs(dz - HAND_Z) > 0) return -1;
		for (int i = 0; i < 2; i++) {
			if (dx == HAND_X[i]) return i;
		}
		return -1;
	}

	public boolean isInsideRoom(Vec3 pos) {
		Vec3 c = inside(0.5, 0, 0.5);
		return Math.abs(pos.x - c.x) <= IN_R + 1 && Math.abs(pos.z - c.z) <= IN_R + 1
				&& pos.y >= c.y - 1 && pos.y <= c.y + 8;
	}

	public boolean contains(Level level, BlockPos pos) {
		if (level.dimension() != dimension) return false;
		int dx = pos.getX() - origin.getX();
		int dy = pos.getY() - origin.getY();
		int dz = pos.getZ() - origin.getZ();
		if (dy < -1 || dy > 8) return false;
		boolean out = Math.abs(dx) <= OUT_R + 1 && Math.abs(dz) <= OUT_R + 1;
		boolean in = Math.abs(dx - IN_OFFSET_X) <= IN_R + 1 && Math.abs(dz) <= IN_R + 1;
		return out || in;
	}

	// ---------------------------------------------------------------- building

	public void build(ServerLevel level) {
		// Outside chamber: floor, walls, open sky.
		for (int x = -OUT_R - 1; x <= OUT_R + 1; x++) {
			for (int z = -OUT_R - 1; z <= OUT_R + 1; z++) {
				boolean wall = Math.abs(x) == OUT_R + 1 || Math.abs(z) == OUT_R + 1;
				for (int y = -1; y <= 12; y++) {
					BlockState state;
					if (y == -1) {
						state = outsideFloor(x, z);
					} else if (wall && y <= 6) {
						if (y == 6) state = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
						else if (y == 3 && Math.floorMod(x + z, 6) == 0) state = Blocks.CRYING_OBSIDIAN.defaultBlockState();
						else state = Blocks.DEEPSLATE_TILES.defaultBlockState();
					} else {
						state = Blocks.AIR.defaultBlockState();
					}
					level.setBlock(outsideBlock(x, y, z), state, 2);
				}
			}
		}
		// Statues: 3x3 base, two-block column, crying obsidian crown.
		for (int i = 0; i < 3; i++) {
			int sx = STATUE_X[i];
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					level.setBlock(outsideBlock(sx + x, 0, STATUE_Z + z), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
				}
			}
			level.setBlock(outsideBlock(sx, 1, STATUE_Z), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2);
			level.setBlock(outsideBlock(sx, 2, STATUE_Z), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2);
			level.setBlock(outsideBlock(sx, 3, STATUE_Z), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
		}
		// The Witness's dais.
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				boolean ring = Math.abs(x) == 2 || Math.abs(z) == 2;
				level.setBlock(outsideBlock(x, -1, WITNESS_Z + z),
						(ring ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).defaultBlockState(), 2);
			}
		}

		// Shadow Realm: fully sealed room.
		for (int x = -IN_R - 1; x <= IN_R + 1; x++) {
			for (int z = -IN_R - 1; z <= IN_R + 1; z++) {
				boolean wall = Math.abs(x) == IN_R + 1 || Math.abs(z) == IN_R + 1;
				for (int y = -1; y <= 7; y++) {
					BlockState state;
					if (y == -1) {
						state = (x % 4 == 0 && z % 4 == 0) ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
								: Blocks.POLISHED_DEEPSLATE.defaultBlockState();
					} else if (y == 7) {
						state = (x % 4 == 0 && z % 4 == 0 && !wall) ? Blocks.SHROOMLIGHT.defaultBlockState()
								: Blocks.BLACK_CONCRETE.defaultBlockState();
					} else if (wall) {
						state = (y == 3 && Math.floorMod(x + z, 4) == 0) ? Blocks.SEA_LANTERN.defaultBlockState()
								: Blocks.BLACK_CONCRETE.defaultBlockState();
					} else {
						state = Blocks.AIR.defaultBlockState();
					}
					level.setBlock(insideBlock(x, y, z), state, 2);
				}
			}
		}
		for (int i = 0; i < 3; i++) {
			level.setBlock(insideBlock(CALLOUT_X[i], 0, CALLOUT_Z), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
			level.setBlock(insideBlock(CALLOUT_X[i], 1, CALLOUT_Z), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2);
		}
		for (int i = 0; i < 2; i++) {
			level.setBlock(insideBlock(HAND_X[i], 0, HAND_Z), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
			level.setBlock(insideBlock(HAND_X[i], 1, HAND_Z), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
		}

		clearDisplays(level);
		showIdle(level);
	}

	private static BlockState outsideFloor(int x, int z) {
		if (Math.floorMod(x, 5) == 0 && Math.floorMod(z, 5) == 0) return Blocks.SEA_LANTERN.defaultBlockState();
		if (x == 0 || z == 0) return Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
		return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	}

	public void demolish(ServerLevel level) {
		clearDisplays(level);
		for (int x = -OUT_R - 1; x <= IN_OFFSET_X + IN_R + 1; x++) {
			for (int z = -OUT_R - 1; z <= OUT_R + 1; z++) {
				for (int y = -1; y <= 12; y++) {
					BlockPos pos = outsideBlock(x, y, z);
					if (contains(level, pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
	}

	public void showIdle(ServerLevel level) {
		spawnText(level, outside(0.5, 3.0, 0.5), Component.empty()
				.append(Component.literal("VERITY\n").withStyle(s -> s.withColor(0xB388FF).withBold(true)))
				.append(Component.literal("/verity start  ·  /verity solo").withStyle(s -> s.withColor(0xAAAAAA))), 2.0f);
	}

	// ---------------------------------------------------------------- text displays

	public void spawnText(ServerLevel level, Vec3 pos, Component text, float scale) {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", "minecraft:text_display");
		tag.putString("text", Component.Serializer.toJson(text, level.registryAccess()));
		tag.putString("billboard", "center");
		tag.putString("alignment", "center");
		tag.putInt("background", 0x70000000);
		tag.putInt("line_width", 400);
		tag.putBoolean("shadow", true);
		CompoundTag transform = new CompoundTag();
		transform.put("left_rotation", floats(0f, 0f, 0f, 1f));
		transform.put("right_rotation", floats(0f, 0f, 0f, 1f));
		transform.put("translation", floats(0f, 0f, 0f));
		transform.put("scale", floats(scale, scale, scale));
		tag.put("transformation", transform);
		CompoundTag brightness = new CompoundTag();
		brightness.putInt("sky", 15);
		brightness.putInt("block", 15);
		tag.put("brightness", brightness);
		ListTag tags = new ListTag();
		tags.add(StringTag.valueOf(VerityMod.TAG));
		tags.add(StringTag.valueOf(DISPLAY_TAG));
		tag.put("Tags", tags);

		Entity entity = EntityType.loadEntityRecursive(tag, level, e -> {
			e.moveTo(pos.x, pos.y, pos.z, 0f, 0f);
			return e;
		});
		if (entity != null) level.addFreshEntity(entity);
	}

	public void clearDisplays(ServerLevel level) {
		removeTagged(level, DISPLAY_TAG);
	}

	public static void removeTagged(ServerLevel level, String tag) {
		List<Entity> doomed = new ArrayList<>();
		for (Entity e : level.getAllEntities()) {
			if (e != null && e.getTags().contains(tag)) doomed.add(e);
		}
		doomed.forEach(Entity::discard);
	}

	private static ListTag floats(float... values) {
		ListTag list = new ListTag();
		for (float v : values) list.add(FloatTag.valueOf(v));
		return list;
	}

	// ---------------------------------------------------------------- persistence

	private static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("verity_arena.txt");
	}

	public void save(MinecraftServer server) {
		String line = dimension.location() + " " + origin.getX() + " " + origin.getY() + " " + origin.getZ();
		try {
			Files.writeString(file(server), line, StandardCharsets.UTF_8);
		} catch (IOException e) {
			VerityMod.LOGGER.warn("Could not save Verity arena location", e);
		}
	}

	public static void load(MinecraftServer server) {
		loaded = null;
		Path path = file(server);
		if (!Files.exists(path)) return;
		try {
			String[] parts = Files.readString(path, StandardCharsets.UTF_8).trim().split(" ");
			ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(parts[0]));
			loaded = new Arena(dim, new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
		} catch (Exception e) {
			VerityMod.LOGGER.warn("Could not read Verity arena location", e);
		}
	}

	public static void forget(MinecraftServer server) {
		loaded = null;
		try {
			Files.deleteIfExists(file(server));
		} catch (IOException ignored) {
		}
	}
}
