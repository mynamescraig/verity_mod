package dev.verity;

import java.util.Optional;

import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;

/** "I know where everything is." Finds structures by the words players use for them. */
public final class Locator {
	/** A structure the player can ask about: the words that name it, and either a tag or a single structure. */
	public record Target(String name, String[] words, TagKey<Structure> tag, ResourceKey<Structure> key) {
	}

	// Longer phrases first so "end city" wins over "city", "jungle temple" over "temple".
	private static final Target[] TARGETS = {
			new Target("ancient city", new String[] {"ancient city", "deep dark", "warden"}, null, BuiltinStructures.ANCIENT_CITY),
			new Target("end city", new String[] {"end city", "elytra"}, null, BuiltinStructures.END_CITY),
			new Target("jungle temple", new String[] {"jungle temple", "jungle pyramid"}, null, BuiltinStructures.JUNGLE_TEMPLE),
			new Target("desert temple", new String[] {"desert temple", "desert pyramid", "pyramid", "temple"}, null, BuiltinStructures.DESERT_PYRAMID),
			new Target("witch hut", new String[] {"witch hut", "swamp hut", "witch"}, null, BuiltinStructures.SWAMP_HUT),
			new Target("trial chambers", new String[] {"trial chamber", "trial"}, StructureTags.ON_TRIAL_CHAMBERS_MAPS, null),
			new Target("stronghold", new String[] {"stronghold", "end portal"}, StructureTags.EYE_OF_ENDER_LOCATED, null),
			new Target("ruined portal", new String[] {"ruined portal", "portal"}, StructureTags.RUINED_PORTAL, null),
			new Target("buried treasure", new String[] {"buried treasure", "treasure"}, StructureTags.ON_TREASURE_MAPS, null),
			new Target("pillager outpost", new String[] {"outpost", "pillager"}, null, BuiltinStructures.PILLAGER_OUTPOST),
			new Target("woodland mansion", new String[] {"mansion"}, StructureTags.ON_WOODLAND_EXPLORER_MAPS, null),
			new Target("ocean monument", new String[] {"monument"}, StructureTags.ON_OCEAN_EXPLORER_MAPS, null),
			new Target("nether fortress", new String[] {"fortress", "blaze"}, null, BuiltinStructures.FORTRESS),
			new Target("bastion", new String[] {"bastion"}, null, BuiltinStructures.BASTION_REMNANT),
			new Target("shipwreck", new String[] {"shipwreck"}, StructureTags.SHIPWRECK, null),
			new Target("mineshaft", new String[] {"mineshaft", "mine shaft"}, StructureTags.MINESHAFT, null),
			new Target("igloo", new String[] {"igloo"}, null, BuiltinStructures.IGLOO),
			new Target("village", new String[] {"village", "villager", "town"}, StructureTags.VILLAGE, null),
	};

	private Locator() {
	}

	/** The structure named in a chat message, or null. */
	public static Target match(String message) {
		for (Target t : TARGETS) {
			for (String w : t.words()) {
				if (message.contains(w)) return t;
			}
		}
		return null;
	}

	/** Nearest structure of that kind, or null if there's none within range (or none in this dimension). */
	public static BlockPos find(ServerLevel level, BlockPos from, Target target) {
		if (target.tag() != null) {
			return level.findNearestMapStructure(target.tag(), from, 64, false);
		}
		Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
		Optional<Holder.Reference<Structure>> holder = registry.getHolder(target.key());
		if (holder.isEmpty()) return null;
		HolderSet<Structure> set = HolderSet.direct(holder.get());
		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, set, from, 64, false);
		return found == null ? null : found.getFirst();
	}
}
