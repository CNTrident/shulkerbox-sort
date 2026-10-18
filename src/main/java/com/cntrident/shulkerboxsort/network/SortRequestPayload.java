package com.cntrident.shulkerboxsort.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.List;

/** Legacy slot references remain decodable for v3 clients; the server ignores them. */
public record SortRequestPayload(int requestId, List<Integer> orderedSlots) implements CustomPacketPayload {
    public static final int MAX_REFERENCES = 36 * 27;
    public static final Type<SortRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
            "shulkerbox_sort", "sort_request_v3"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SortRequestPayload> CODEC =
            CustomPacketPayload.codec((payload, buf) -> {
                buf.writeVarInt(payload.requestId());
                buf.writeVarInt(payload.orderedSlots().size());
                for (int slot : payload.orderedSlots()) buf.writeVarInt(slot);
            }, buf -> {
                int id = buf.readVarInt();
                int size = buf.readVarInt();
                if (size < 0 || size > MAX_REFERENCES) throw new IllegalArgumentException("Too many sort references");
                var slots = new java.util.ArrayList<Integer>(size);
                for (int i = 0; i < size; i++) {
                    int slot = buf.readVarInt();
                    if (slot < 0 || slot >= MAX_REFERENCES) throw new IllegalArgumentException("Invalid sort reference");
                    slots.add(slot);
                }
                return new SortRequestPayload(id, slots);
            });
    public SortRequestPayload { orderedSlots = List.copyOf(orderedSlots); }
    @Override public Type<SortRequestPayload> type() { return TYPE; }
}
