package com.theonesabove.minimap;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/** Server -> client snapshot of TOAZones regions in the player's current world. */
public record ZoneMarkersPayload(String data) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ToaMinimapClient.MOD_ID, "zones");
    public static final Type<ZoneMarkersPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ZoneMarkersPayload> CODEC = new StreamCodec<>() {
        @Override
        public ZoneMarkersPayload decode(RegistryFriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new ZoneMarkersPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, ZoneMarkersPayload value) {
            buf.writeBytes(value.data().getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
