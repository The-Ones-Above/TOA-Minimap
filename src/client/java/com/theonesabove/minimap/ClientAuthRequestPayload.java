package com.theonesabove.minimap;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server request for TOA Minimap authorization. */
public record ClientAuthRequestPayload(boolean request) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ToaMinimapClient.MOD_ID, "auth");
    public static final Type<ClientAuthRequestPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientAuthRequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            ClientAuthRequestPayload::request,
            ClientAuthRequestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
