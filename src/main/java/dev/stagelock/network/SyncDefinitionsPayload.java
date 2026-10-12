package dev.stagelock.network;

import com.mojang.serialization.Codec;
import dev.stagelock.StageLock;
import dev.stagelock.api.StageDefinition;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Map;

/** Server → client: stage definitions (display names, icons, prerequisites). */
public record SyncDefinitionsPayload(Map<String, StageDefinition> definitions) implements CustomPacketPayload {

    public static final Type<SyncDefinitionsPayload> TYPE = new Type<>(StageLock.id("sync_definitions"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncDefinitionsPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(Codec.unboundedMap(Codec.STRING, StageDefinition.CODEC))
                    .map(SyncDefinitionsPayload::new, SyncDefinitionsPayload::definitions);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
