package com.theonesabove.minimap;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One-byte server -> client handshake used by the TOAMinimap Paper companion plugin.
 * The Bukkit plugin sends a single boolean byte on the same custom payload channel.
 */
public record ServerAuthPayload(boolean authorized) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ToaMinimapClient.MOD_ID, "auth");
    public static final Type<ServerAuthPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerAuthPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            ServerAuthPayload::authorized,
            ServerAuthPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
