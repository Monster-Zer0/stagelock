package dev.stagelock.network;

import dev.stagelock.StageLock;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.LinkedHashSet;
import java.util.Set;

/** Server → client: the player's full effective stage set. Sent at most once per player per tick. */
public record SyncStagesPayload(Set<String> stages) implements CustomPacketPayload {

    public static final Type<SyncStagesPayload> TYPE = new Type<>(StageLock.id("sync_stages"));

    public static final StreamCodec<ByteBuf, SyncStagesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(LinkedHashSet::new, ByteBufCodecs.stringUtf8(64)), SyncStagesPayload::stages,
            SyncStagesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
