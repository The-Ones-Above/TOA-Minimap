package com.theonesabove.minimap;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/** Server -> client snapshot of Citizens NPCs visible to the minimap. */
public record NpcMarkersPayload(String data) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ToaMinimapClient.MOD_ID, "npcs");
    public static final Type<NpcMarkersPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcMarkersPayload> CODEC = new StreamCodec<>() {
        @Override
        public NpcMarkersPayload decode(RegistryFriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new NpcMarkersPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, NpcMarkersPayload value) {
            buf.writeBytes(value.data().getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
