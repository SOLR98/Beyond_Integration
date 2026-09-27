package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.crafting.DimensionsEnchantMergeMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 批量附魔工作站提交合并（C2S）：客户端发送勾选的附魔 id 与所选等级，
 * 服务端校验并消耗单附魔书 + 网络 XP 流体后合并到装备。
 */
public record SubmitEnchantMergePacket(int containerId, int[] enchIds, int[] levels) {
    public static void encode(SubmitEnchantMergePacket m, FriendlyByteBuf b) {
        b.writeInt(m.containerId);
        int n = m.enchIds == null ? 0 : m.enchIds.length;
        b.writeInt(n);
        for (int i = 0; i < n; i++) {
            b.writeInt(m.enchIds[i]);
            b.writeInt(m.levels != null && i < m.levels.length ? m.levels[i] : 1);
        }
    }

    public static SubmitEnchantMergePacket decode(FriendlyByteBuf b) {
        int containerId = b.readInt();
        int n = b.readInt();
        int[] ids = new int[n];
        int[] levels = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = b.readInt();
            levels[i] = b.readInt();
        }
        return new SubmitEnchantMergePacket(containerId, ids, levels);
    }

    public static void handle(SubmitEnchantMergePacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null && sp.containerMenu instanceof DimensionsEnchantMergeMenu menu
                    && menu.containerId == m.containerId) {
                menu.handleSubmit(sp, m.enchIds(), m.levels());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
