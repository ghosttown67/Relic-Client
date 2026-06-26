package relic.client.api.packet;

import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RawCustomPayload(Identifier channel, byte[] data) implements CustomPayload {

    @Override
    public CustomPayload.Id<RawCustomPayload> getId() {

        return new CustomPayload.Id<>(channel);
    }
}
