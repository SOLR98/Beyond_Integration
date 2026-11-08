package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.enchant.EnchantMergeService;
import com.solr98.beyondintegration.handler.NetMenuResolver;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * C2S：提交「附魔合并」选择（装备 + 每项附魔的目标等级）。
 * <p>服务端重新扫描网络并校验库存/费用（{@link EnchantMergeService#merge}），
 * 执行后回推新的 {@link EnchantMergeStatePacket} 与一条结果消息。
 */
public class SubmitEnchantMergePacket {

    private final ItemStack equipment;
    private final List<EnchantMergeService.Selection> selections;

    public SubmitEnchantMergePacket(ItemStack equipment, List<EnchantMergeService.Selection> selections) {
        this.equipment = equipment == null ? ItemStack.EMPTY : equipment.copyWithCount(1);
        this.selections = List.copyOf(selections == null ? List.of() : selections);
    }

    public static void encode(SubmitEnchantMergePacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.equipment);
        buf.writeVarInt(msg.selections.size());
        for (EnchantMergeService.Selection s : msg.selections) {
            buf.writeVarInt(s.enchId());
            buf.writeVarInt(s.level());
        }
    }

    public static SubmitEnchantMergePacket decode(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        int n = buf.readVarInt();
        List<EnchantMergeService.Selection> list = new ArrayList<>(Math.max(0, n));
        for (int i = 0; i < n; i++) {
            list.add(new EnchantMergeService.Selection(buf.readVarInt(), buf.readVarInt()));
        }
        return new SubmitEnchantMergePacket(stack, list);
    }

    public static void handle(SubmitEnchantMergePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || msg.equipment.isEmpty()) return;
            DimensionsNet net = NetMenuResolver.resolve(player);
            if (net == null) return;
            EnchantMergeService.Outcome outcome = EnchantMergeService.merge(
                    net, new ItemStackKey(msg.equipment.copyWithCount(1)), msg.selections);
            if (outcome.messageKey() != null) {
                player.sendSystemMessage(Component.translatable(outcome.messageKey()));
            }
            EnchantMergeStatePacket.sendTo(player, net, msg.equipment);
        });
        ctx.get().setPacketHandled(true);
    }
}
