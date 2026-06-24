package com.solr98.beyondintegration.network;
import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RequestAmmoCountPacket(ResourceLocation ammoId, boolean quick) implements CustomPacketPayload {
    public static final Type<RequestAmmoCountPacket> TYPE = new Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":request_ammo_count"));
    public static final StreamCodec<FriendlyByteBuf, RequestAmmoCountPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public @NotNull RequestAmmoCountPacket decode(FriendlyByteBuf buf) {
            return new RequestAmmoCountPacket(buf.readResourceLocation(), buf.readBoolean());
        }
        @Override public void encode(FriendlyByteBuf buf, RequestAmmoCountPacket p) {
            buf.writeResourceLocation(p.ammoId);
            buf.writeBoolean(p.quick);
        }
    };

    public static void handle(final RequestAmmoCountPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) return;

            Map<Integer, AmmoCountResponsePacket.NetEntry> networks = new LinkedHashMap<>();

            DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(sp);
            if (primary != null) {
                int count = TaczAmmoExtractor.countAmmoInNetworkByAmmoId(packet.ammoId, primary);
                if (count > 0)
                    networks.put(primary.getId(), new AmmoCountResponsePacket.NetEntry(primary.getId(), primary.getCustomName(), count));
            }

            List<DimensionsNet> nets = DimensionsNet.getAllNetFromPlayer(sp);
            for (DimensionsNet net : nets) {
                if (net == primary) continue;
                int count = TaczAmmoExtractor.countAmmoInNetworkByAmmoId(packet.ammoId, net);
                if (count > 0)
                    networks.put(net.getId(), new AmmoCountResponsePacket.NetEntry(net.getId(), net.getCustomName(), count));
            }

            if (networks.isEmpty())
                networks.put(-1, new AmmoCountResponsePacket.NetEntry(-1, "", 0));

            PacketHandler.sendToPlayer(sp, new AmmoCountResponsePacket(packet.ammoId, networks, packet.quick));
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
