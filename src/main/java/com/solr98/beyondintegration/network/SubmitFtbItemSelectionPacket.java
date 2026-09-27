package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.feature.ftb.FtbItemSubmitSelectionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * FTB 消耗型物品任务"选择提交"请求（C2S）：
 * 客户端选择界面确认后发送各候选物品与提交数量，服务端校验后先扣背包、
 * 不足部分从主网络扣除并计入进度；状态校验失败时回发提示消息。
 */
public record SubmitFtbItemSelectionPacket(long taskId, List<ItemStack> stacks, List<Long> amounts)
        implements CustomPacketPayload {

    public static final Type<SubmitFtbItemSelectionPacket> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":submit_ftb_item_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitFtbItemSelectionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, SubmitFtbItemSelectionPacket::taskId,
                    ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(FtbItemSubmitSelectionService.MAX_ENTRIES)),
                    SubmitFtbItemSelectionPacket::stacks,
                    ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(FtbItemSubmitSelectionService.MAX_ENTRIES)),
                    SubmitFtbItemSelectionPacket::amounts,
                    SubmitFtbItemSelectionPacket::new);

    public static void handle(final SubmitFtbItemSelectionPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!ModList.get().isLoaded("ftbquests")) return;
            long submitted = FtbItemSubmitSelectionService.submitSelection(
                    player, packet.taskId(), packet.stacks(), packet.amounts());
            if (submitted < 0) {
                player.displayClientMessage(
                        Component.translatable("beyond_integration.ftb.select.failed"), false);
            }
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
