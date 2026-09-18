package com.theonesabove.minimap;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

public record FactionAreasPayload(String data) implements CustomPacketPayload {
    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(ToaMinimapClient.MOD_ID, "faction_areas");
    public static final Type<FactionAreasPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, FactionAreasPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public FactionAreasPayload decode(RegistryFriendlyByteBuf buf) {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    return new FactionAreasPayload(new String(bytes, StandardCharsets.UTF_8));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, FactionAreasPayload value) {
                    buf.writeBytes(value.data().getBytes(StandardCharsets.UTF_8));
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
