package dev.stagelock.api.restriction;

import com.mojang.serialization.Codec;
import dev.stagelock.api.StageRequirement;

/**
 * A rule that gates something behind a {@link StageRequirement}. Loaded from
 * {@code data/<ns>/stagelock/restrictions/**.json} (or built by KubeJS) and dispatched on {@code "type"}.
 */
public interface Restriction {

    Codec<Restriction> CODEC = RestrictionTypes.CODEC.dispatch("type", Restriction::type, RestrictionType::codec);

    RestrictionType<?> type();

    StageRequirement requires();
}
