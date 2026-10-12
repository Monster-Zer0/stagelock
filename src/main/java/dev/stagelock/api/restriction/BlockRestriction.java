package dev.stagelock.api.restriction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.stagelock.api.StageRequirement;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * {@code "type": "stagelock:block"} - blocks interacting with matching blocks.
 *
 * <pre>{@code
 * {
 *   "type": "stagelock:block",
 *   "requires": "mypack:nuclear",
 *   "blocks": ["#c:ores/uranium"],
 *   "actions": { "break": true, "interact": true, "place": true, "drops": false },
 *   "disguise_as": "minecraft:stone"
 * }
 * }</pre>
 * {@code drops: true} lets locked players break the block but suppresses its drops instead.
 * {@code disguise_as} makes the block look (and drop, and mine) like another block for locked players.
 */
public record BlockRestriction(
        StageRequirement requires,
        List<RegistryMatcher<Block>> blocks,
        List<RegistryMatcher<Block>> except,
        Actions actions,
        Optional<Block> disguiseAs,
        Optional<Component> message
) implements Restriction {

    public static final MapCodec<BlockRestriction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            StageRequirement.CODEC.fieldOf("requires").forGetter(BlockRestriction::requires),
            RegistryMatcher.listCodec(Registries.BLOCK).fieldOf("blocks").forGetter(BlockRestriction::blocks),
            RegistryMatcher.listCodec(Registries.BLOCK).optionalFieldOf("except", List.of()).forGetter(BlockRestriction::except),
            Actions.CODEC.optionalFieldOf("actions", Actions.DEFAULT).forGetter(BlockRestriction::actions),
            net.minecraft.core.registries.BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("disguise_as").forGetter(BlockRestriction::disguiseAs),
            ComponentSerialization.CODEC.optionalFieldOf("message").forGetter(BlockRestriction::message)
    ).apply(i, BlockRestriction::new));

    @Override
    public RestrictionType<?> type() {
        return RestrictionTypes.BLOCK;
    }

    public boolean matches(BlockState state) {
        var holder = state.getBlockHolder();
        return RegistryMatcher.anyMatch(blocks, holder) && !RegistryMatcher.anyMatch(except, holder);
    }

    /** Disguised blocks can be broken; they mine and drop like their disguise instead. */
    public boolean blocksBreaking() {
        return actions.breakBlock() && disguiseAs.isEmpty();
    }

    public record Actions(boolean breakBlock, boolean interact, boolean place, boolean drops) {

        public static final Actions DEFAULT = new Actions(true, true, true, false);

        public static final Codec<Actions> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("break", true).forGetter(Actions::breakBlock),
                Codec.BOOL.optionalFieldOf("interact", true).forGetter(Actions::interact),
                Codec.BOOL.optionalFieldOf("place", true).forGetter(Actions::place),
                Codec.BOOL.optionalFieldOf("drops", false).forGetter(Actions::drops)
        ).apply(i, Actions::new));
    }
}
