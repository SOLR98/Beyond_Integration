package com.solr98.beyondintegration.network.payload;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.client.gui.DimensionsEnchantMergeGUI;
import com.solr98.beyondintegration.init.DimensionsEnchantMergeMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 批量附魔工作站候选列表（服务端 → 客户端）。
 * 装备槽变化时服务端重算并下发：附魔 Holder / 网络最高可合并等级 / 该等级书数量 / 装备已有等级。
 * TYPE: beyond_integration:enchant_merge_list。
 */
public record EnchantMergeListPayload(int containerId, List<DimensionsEnchantMergeMenu.MergeOption> options) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EnchantMergeListPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":enchant_merge_list"));

    private static final StreamCodec<RegistryFriendlyByteBuf, DimensionsEnchantMergeMenu.MergeOption> OPTION =
            StreamCodec.composite(
                    ByteBufCodecs.holderRegistry(Registries.ENCHANTMENT), DimensionsEnchantMergeMenu.MergeOption::holder,
                    ByteBufCodecs.VAR_INT, DimensionsEnchantMergeMenu.MergeOption::maxLevel,
                    ByteBufCodecs.VAR_INT, DimensionsEnchantMergeMenu.MergeOption::stock,
                    ByteBufCodecs.VAR_INT, DimensionsEnchantMergeMenu.MergeOption::existing,
                    ByteBufCodecs.VAR_LONG, DimensionsEnchantMergeMenu.MergeOption::levelMask,
                    DimensionsEnchantMergeMenu.MergeOption::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, EnchantMergeListPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, EnchantMergeListPayload::containerId,
                    OPTION.apply(ByteBufCodecs.list()), EnchantMergeListPayload::options,
                    EnchantMergeListPayload::new);

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(EnchantMergeListPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof DimensionsEnchantMergeGUI gui && gui.getMenu().containerId == p.containerId()) {
                gui.getMenu().acceptOptions(p.options());
                gui.onOptionsUpdated();
            }
        });
    }
}
