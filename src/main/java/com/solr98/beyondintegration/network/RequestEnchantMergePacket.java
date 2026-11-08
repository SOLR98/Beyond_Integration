package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.enchant.EnchantMergeService;
import com.solr98.beyondintegration.handler.NetMenuResolver;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S：请求「附魔合并」页的候选（携带网络里的那件装备）。
 * <p>服务端按当前打开的网络菜单（回退主网络）扫描网络里的单附魔书，回推
 * {@link EnchantMergeStatePacket}（候选 + XP 余额）。页面只在客户端做展示与选择。
 */
public class RequestEnchantMergePacket {

    private final ItemStack equipment;

    public RequestEnchantMergePacket(ItemStack equipment) {
        this.equipment = equipment == null ? ItemStack.EMPTY : equipment.copyWithCount(1);
    }

    public static void encode(RequestEnchantMergePacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.equipment);
    }

    public static RequestEnchantMergePacket decode(FriendlyByteBuf buf) {
        return new RequestEnchantMergePacket(buf.readItem());
    }

    public static void handle(RequestEnchantMergePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || msg.equipment.isEmpty()) return;
            DimensionsNet net = NetMenuResolver.resolve(player);
            if (net == null) return;
            EnchantMergeStatePacket.sendTo(player, net, msg.equipment);
        });
        ctx.get().setPacketHandled(true);
    }
}
