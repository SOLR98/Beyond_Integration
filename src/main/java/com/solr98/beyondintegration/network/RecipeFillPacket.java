package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.crafting.DimensionsCraftMenu;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 配方填充请求包（C2S）：客户端把选中配方的物品键列表与数量发送给服务端，由合成菜单自动填充材料 */
public record RecipeFillPacket(List<IStackKey<?>> keys, List<Long> amounts) {

    /** 将物品键列表与数量列表写入缓冲区 */
    public static void encode(RecipeFillPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.keys.size());
        for (IStackKey<?> k : msg.keys) IStackKey.serializeCommon(buf, k);
        buf.writeVarInt(msg.amounts.size());
        for (long v : msg.amounts) buf.writeLong(v);
    }

    /** 从缓冲区读取并还原数据包 */
    public static RecipeFillPacket decode(FriendlyByteBuf buf) {
        int ks = buf.readVarInt();
        List<IStackKey<?>> keys = new ArrayList<>(ks);
        for (int i = 0; i < ks; i++) keys.add(IStackKey.deserializeCommon(buf));
        int as = buf.readVarInt();
        List<Long> amounts = new ArrayList<>(as);
        for (int i = 0; i < as; i++) amounts.add(buf.readLong());
        return new RecipeFillPacket(keys, amounts);
    }

    /** 服务端执行：若玩家打开的是维度合成菜单，则将配方材料填入 */
    public static void handle(RecipeFillPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            if (p.containerMenu instanceof DimensionsCraftMenu menu) {
                menu.transferRecipe(msg.keys, msg.amounts);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
