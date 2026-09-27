package com.solr98.beyondintegration.network.payload;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.init.DimensionsEnchantMergeMenu;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 批量附魔工作站提交合并（客户端 → 服务端）。
 * 客户端发送勾选的附魔 Holder 与所选等级，服务端校验并消耗单附魔书 + 网络 XP 流体后合并到装备。
 * TYPE: beyond_integration:submit_enchant_merge。
 */
public record SubmitEnchantMergePayload(int containerId, List<Holder<Enchantment>> holders, List<Integer> levels) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SubmitEnchantMergePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":submit_enchant_merge"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Holder<Enchantment>> HOLDER =
            ByteBufCodecs.holderRegistry(Registries.ENCHANTMENT);

    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitEnchantMergePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SubmitEnchantMergePayload::containerId,
                    HOLDER.apply(ByteBufCodecs.list()), SubmitEnchantMergePayload::holders,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), SubmitEnchantMergePayload::levels,
                    SubmitEnchantMergePayload::new);

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SubmitEnchantMergePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof DimensionsEnchantMergeMenu menu
                    && menu.containerId == p.containerId()) {
                menu.handleSubmit(sp, p.holders(), p.levels());
            }
        });
    }
}
