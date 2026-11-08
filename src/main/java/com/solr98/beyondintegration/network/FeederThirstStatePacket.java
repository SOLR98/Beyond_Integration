package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.feeder.FeederThirstClientState;
import com.solr98.beyondintegration.feature.feeder.FeederThirstSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 → 客户端：当前网络喂食器的口渴/饱食阈值（打开界面时 / 设置变更后下发）。
 * <p>设置存在<b>手持物品的 NBT</b> 上，客户端手里那份是本地副本、不会自动更新，
 * 所以用本包把权威值推给客户端，供 BdPage 读取显示。
 */
public record FeederThirstStatePacket(int thirst, int quenched, int food, int saturation)
        implements CustomPacketPayload {

    public static final Type<FeederThirstStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "feeder_thirst_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FeederThirstStatePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FeederThirstStatePacket::thirst,
                    ByteBufCodecs.VAR_INT, FeederThirstStatePacket::quenched,
                    ByteBufCodecs.VAR_INT, FeederThirstStatePacket::food,
                    ByteBufCodecs.VAR_INT, FeederThirstStatePacket::saturation,
                    FeederThirstStatePacket::new);

    /** 从喂食物品读取当前值构造。 */
    public static FeederThirstStatePacket of(ItemStack stack) {
        return new FeederThirstStatePacket(
                clamp(FeederThirstSettings.thirstThreshold(stack)),
                clamp(FeederThirstSettings.quenchedThreshold(stack)),
                clamp(FeederThirstSettings.foodThreshold(stack)),
                clamp(FeederThirstSettings.saturationThreshold(stack)));
    }

    /** 便捷：向某玩家推送其手持喂食器的当前设置。 */
    public static void sendTo(ServerPlayer player, ItemStack stack) {
        PacketHandler.sendToPlayer(player, of(stack));
    }

    public static void handle(final FeederThirstStatePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                FeederThirstClientState.set(packet.thirst(), packet.quenched(),
                        packet.food(), packet.saturation());
            }
        });
    }

    private static int clamp(int value) {
        return Math.max(FeederThirstSettings.MIN, Math.min(FeederThirstSettings.MAX, value));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
