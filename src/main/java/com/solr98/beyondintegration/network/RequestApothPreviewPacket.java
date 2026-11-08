package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.enchant.ApothEnchantTableWidget;
import com.solr98.beyondintegration.feature.enchant.EnchantTableWidget;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S：请求当前神化附魔台工作台的完整预览附魔列表。 */
public class RequestApothPreviewPacket {

    public RequestApothPreviewPacket() {
    }

    public static void encode(RequestApothPreviewPacket msg, FriendlyByteBuf buf) {
    }

    public static RequestApothPreviewPacket decode(FriendlyByteBuf buf) {
        return new RequestApothPreviewPacket();
    }

    public static void handle(RequestApothPreviewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.containerMenu instanceof BDBaseMenu base) {
                ApothEnchantTableWidget apoth = base.widget(ApothEnchantTableWidget.class);
                if (apoth != null) {
                    apoth.sendPreview(player);
                    return;
                }
                EnchantTableWidget vanilla = base.widget(EnchantTableWidget.class);
                if (vanilla != null) vanilla.sendPreview(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
