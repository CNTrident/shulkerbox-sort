package com.cntrident.shulkerboxsort.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SortResultPayload(int requestId, String messageKey, int sorted, int emptied,
                                int remaining, int merged, long micros) implements CustomPacketPayload {
    public static final Type<SortResultPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
            "shulkerbox_sort", "sort_result_v3"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SortResultPayload> CODEC =
            CustomPacketPayload.codec((p, b) -> {
                b.writeVarInt(p.requestId()); b.writeUtf(p.messageKey(), 128);
                b.writeVarInt(p.sorted()); b.writeVarInt(p.emptied()); b.writeVarInt(p.remaining());
                b.writeVarInt(p.merged()); b.writeLong(p.micros());
            }, b -> new SortResultPayload(b.readVarInt(), b.readUtf(128), b.readVarInt(),
                    b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readLong()));
    @Override public Type<SortResultPayload> type() { return TYPE; }
}
