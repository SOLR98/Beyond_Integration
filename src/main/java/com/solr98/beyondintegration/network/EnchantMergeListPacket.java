package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.gui.DimensionsEnchantMergeGUI;
import com.solr98.beyondintegration.feature.crafting.DimensionsEnchantMergeMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 批量附魔工作站候选列表（S2C）：装备槽变化时服务端重算并下发，
 * 每项包含附魔注册 id / 网络最高可合并等级 / 该等级书数量 / 装备已有等级。
 */
public record EnchantMergeListPacket(int containerId, List<DimensionsEnchantMergeMenu.MergeOption> options) {
    public static void encode(EnchantMergeListPacket m, FriendlyByteBuf b) {
        b.writeInt(m.containerId);
        List<DimensionsEnchantMergeMenu.MergeOption> list = m.options == null ? List.of() : m.options;
        b.writeInt(list.size());
        for (DimensionsEnchantMergeMenu.MergeOption o : list) {
            b.writeInt(o.enchId());
            b.writeInt(o.maxLevel());
            b.writeInt(o.stock());
            b.writeInt(o.existing());
            b.writeLong(o.levelMask());
        }
    }

    public static EnchantMergeListPacket decode(FriendlyByteBuf b) {
        int containerId = b.readInt();
        int n = b.readInt();
        List<DimensionsEnchantMergeMenu.MergeOption> list = new ArrayList<>(Math.max(0, n));
        for (int i = 0; i < n; i++) {
            list.add(new DimensionsEnchantMergeMenu.MergeOption(b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readLong()));
        }
        return new EnchantMergeListPacket(containerId, list);
    }

    public static void handle(EnchantMergeListPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof DimensionsEnchantMergeGUI gui && gui.getMenu().containerId == m.containerId) {
                gui.getMenu().acceptOptions(m.options());
                gui.onOptionsUpdated();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
