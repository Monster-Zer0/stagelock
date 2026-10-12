package dev.stagelock.network;

import com.mojang.serialization.Codec;
import dev.stagelock.StageLock;
import dev.stagelock.api.restriction.Restriction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Server → client: every active restriction rule, sent on join and after {@code /reload}. */
public record SyncRestrictionsPayload(Map<ResourceLocation, Restriction> rules) implements CustomPacketPayload {

    public static final Type<SyncRestrictionsPayload> TYPE = new Type<>(StageLock.id("sync_restrictions"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncRestrictionsPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(Codec.unboundedMap(ResourceLocation.CODEC, Restriction.CODEC))
                    .map(SyncRestrictionsPayload::new, SyncRestrictionsPayload::rules);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
