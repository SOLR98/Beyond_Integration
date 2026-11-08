package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.feeder.FeederThirstSettings;
import com.wintercogs.beyonddimensions.common.menu.NetFeederMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：设置当前打开的网络喂食器的四项阈值
 * （口渴 / 水饱和 / 饱食度 / 饥饿饱和度）。
 * <p>服务端校验玩家当前菜单确实是 {@link NetFeederMenu}，写入手持物品 NBT 后
 * 通过 {@link FeederThirstStatePacket} 回推权威值（客户端不做乐观更新）。
 */
public record SetFeederThirstPacket(int thirst, int quenched, int food, int saturation)
        implements CustomPacketPayload {

    public static final Type<SetFeederThirstPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "set_feeder_thirst"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFeederThirstPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetFeederThirstPacket::thirst,
                    ByteBufCodecs.VAR_INT, SetFeederThirstPacket::quenched,
                    ByteBufCodecs.VAR_INT, SetFeederThirstPacket::food,
                    ByteBufCodecs.VAR_INT, SetFeederThirstPacket::saturation,
                    SetFeederThirstPacket::new);

    public static void handle(final SetFeederThirstPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof NetFeederMenu menu)) return;
            ItemStack stack = menu.menuStack;
            if (stack == null || stack.isEmpty()) return;

            // 「饱和维持模式」判定：后两项任一 > 0 时，前两项强制为上限（补给触发即补满，
            // 口渴/饱食度本就一直贴上限，允许调小只会造成误解），服务端做权威兜底。
            boolean holdMode = packet.quenched() > 0 || packet.saturation() > 0;
            int thirst = holdMode ? FeederThirstSettings.MAX : packet.thirst();
            int food = holdMode ? FeederThirstSettings.MAX : packet.food();

            FeederThirstSettings.setThirstThreshold(stack, thirst);
            FeederThirstSettings.setQuenchedThreshold(stack, packet.quenched());
            FeederThirstSettings.setFoodThreshold(stack, food);
            FeederThirstSettings.setSaturationThreshold(stack, packet.saturation());
            menu.broadcastChanges();
            PacketDistributor.sendToPlayer(player, FeederThirstStatePacket.of(stack));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
