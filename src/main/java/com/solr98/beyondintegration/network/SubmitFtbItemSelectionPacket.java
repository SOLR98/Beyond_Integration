package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.ftb.FtbItemSubmitSelectionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * FTB 消耗型物品任务"选择提交"请求（C2S）：
 * 客户端选择界面确认后发送各候选物品与提交数量，服务端校验后先扣背包、
 * 不足部分从主网络扣除并计入进度；状态校验失败时回发提示消息。
 */
public record SubmitFtbItemSelectionPacket(long taskId, List<ItemStack> stacks, List<Long> amounts) {

    public static void encode(SubmitFtbItemSelectionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarLong(msg.taskId);
        buf.writeVarInt(msg.stacks.size());
        for (int i = 0; i < msg.stacks.size(); i++) {
            buf.writeItem(msg.stacks.get(i));
            buf.writeVarLong(msg.amounts.get(i));
        }
    }

    public static SubmitFtbItemSelectionPacket decode(FriendlyByteBuf buf) {
        long taskId = buf.readVarLong();
        int size = Math.max(0, Math.min(buf.readVarInt(), FtbItemSubmitSelectionService.MAX_ENTRIES));
        List<ItemStack> stacks = new ArrayList<>(size);
        List<Long> amounts = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            stacks.add(buf.readItem());
            amounts.add(buf.readVarLong());
        }
        return new SubmitFtbItemSelectionPacket(taskId, stacks, amounts);
    }

    public static void handle(SubmitFtbItemSelectionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!ModList.get().isLoaded("ftbquests")) return;
            long submitted = FtbItemSubmitSelectionService.submitSelection(
                    player, msg.taskId, msg.stacks, msg.amounts);
            if (submitted < 0) {
                player.displayClientMessage(
                        Component.translatable("beyond_integration.ftb.select.failed"), false);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
