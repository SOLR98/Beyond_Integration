package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.ftb.FtbItemSubmitSelectionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 打开 FTB 消耗型物品任务"选择提交"界面（S2C）：
 * 服务端在点击提交且候选种类 &gt; 1 时发送，客户端打开 {@code FtbItemSubmitSelectScreen}
 * 由玩家分配各候选的提交数量；确认后经 {@link SubmitFtbItemSelectionPacket} 提交。
 */
public record OpenFtbItemSubmitSelectPacket(long taskId, String title, long remaining, List<Entry> entries) {

    /** 单个候选：展示用物品（数量 1）+ 背包可用量 + 网络可用量 */
    public record Entry(ItemStack stack, long bag, long net) {}

    public static void encode(OpenFtbItemSubmitSelectPacket msg, FriendlyByteBuf buf) {
        buf.writeVarLong(msg.taskId);
        buf.writeUtf(msg.title == null ? "" : msg.title);
        buf.writeVarLong(msg.remaining);
        buf.writeVarInt(msg.entries.size());
        for (Entry entry : msg.entries) {
            buf.writeItem(entry.stack());
            buf.writeVarLong(entry.bag());
            buf.writeVarLong(entry.net());
        }
    }

    public static OpenFtbItemSubmitSelectPacket decode(FriendlyByteBuf buf) {
        long taskId = buf.readVarLong();
        String title = buf.readUtf();
        long remaining = buf.readVarLong();
        int size = Math.max(0, Math.min(buf.readVarInt(), FtbItemSubmitSelectionService.MAX_ENTRIES));
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = buf.readItem();
            long bag = buf.readVarLong();
            long net = buf.readVarLong();
            entries.add(new Entry(stack, bag, net));
        }
        return new OpenFtbItemSubmitSelectPacket(taskId, title, remaining, entries);
    }

    public static void handle(OpenFtbItemSubmitSelectPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.solr98.beyondintegration.client.gui.FtbItemSubmitSelectScreen.open(msg)));
        ctx.get().setPacketHandled(true);
    }
}
