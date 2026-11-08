package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.enchant.ApothEnchantTableWidget;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 献祭预览请求（C2S 空包）：客户端悬浮「献祭」按钮时请求当前手持书架献祭后的属性变动。
 * <p>预览要读网络存档（{@code ApothStatData}）并扫描书架方块属性，因此由服务端算，
 * 客户端只负责显示。
 */
public class RequestSacrificePreviewPacket {

    public RequestSacrificePreviewPacket() {
    }

    public static void encode(RequestSacrificePreviewPacket msg, FriendlyByteBuf buf) {
    }

    public static RequestSacrificePreviewPacket decode(FriendlyByteBuf buf) {
        return new RequestSacrificePreviewPacket();
    }

    public static void handle(RequestSacrificePreviewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!(player.containerMenu instanceof BDBaseMenu base)) return;
            ApothEnchantTableWidget widget = base.widget(ApothEnchantTableWidget.class);
            if (widget == null) return;
            ApothEnchantTableWidget.SacrificeOutcome outcome = widget.previewSacrifice();
            if (outcome == null) return;
            PacketHandler.sendToPlayer(player, new SacrificePreviewPacket(
                    base.containerId, outcome.ok(), outcome.blockKey(), outcome.cur(), outcome.next()));
        });
        ctx.get().setPacketHandled(true);
    }
}
