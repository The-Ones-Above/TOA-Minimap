package com.theonesabove.minimap;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client inventory entitlement state for TOA Minimap access items. */
public record ItemAccessPayload(boolean hasCompass, boolean hasCityMap) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ToaMinimapClient.MOD_ID, "state");
    public static final Type<ItemAccessPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemAccessPayload> CODEC = new StreamCodec<>() {
        @Override
        public ItemAccessPayload decode(RegistryFriendlyByteBuf buf) {
            return new ItemAccessPayload(buf.readBoolean(), buf.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, ItemAccessPayload value) {
            buf.writeBoolean(value.hasCompass());
            buf.writeBoolean(value.hasCityMap());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
