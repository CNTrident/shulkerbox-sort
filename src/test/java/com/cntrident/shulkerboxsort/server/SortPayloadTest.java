package com.cntrident.shulkerboxsort.server;

import com.cntrident.shulkerboxsort.network.SortRequestPayload;
import com.cntrident.shulkerboxsort.network.SortResultPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SortPayloadTest {
    @BeforeAll static void bootstrap() { ServerInventorySorterTest.bootstrap(); }
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
    @Test void requestAndResultRoundTrip() {
        var buffer = buffer();
        try {
            var request = new SortRequestPayload(23, List.of(0, 971, 54));
            SortRequestPayload.CODEC.encode(buffer, request);
            assertEquals(request, SortRequestPayload.CODEC.decode(buffer));
            buffer.clear();
            var result = new SortResultPayload(23, "message.shulkerbox_sort.server_completed", 36, 3, 33, 2, 5000);
            SortResultPayload.CODEC.encode(buffer, result);
            assertEquals(result, SortResultPayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void rejectsUnboundedReferenceAllocation() {
        var buffer = buffer();
        try {
            buffer.writeVarInt(0); buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> SortRequestPayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void rejectsInvalidSlotReferences() {
        for (int slot : new int[]{-1, 972, Integer.MAX_VALUE}) {
            var buffer = buffer();
            try {
                buffer.writeVarInt(0); buffer.writeVarInt(1); buffer.writeVarInt(slot);
                assertThrows(IllegalArgumentException.class, () -> SortRequestPayload.CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
    }
}
