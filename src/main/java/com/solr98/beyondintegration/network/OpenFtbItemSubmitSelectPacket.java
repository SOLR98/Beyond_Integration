package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.feature.ftb.FtbItemSubmitSelectionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 打开 FTB 消耗型物品任务"选择提交"界面（S2C）：
 * 服务端在点击提交且候选种类 &gt; 1 时发送，客户端打开 {@code FtbItemSubmitSelectScreen}
 * 由玩家分配各候选的提交数量；确认后经 {@link SubmitFtbItemSelectionPacket} 提交。
 */
public record OpenFtbItemSubmitSelectPacket(long taskId, String title, long remaining, List<Entry> entries)
        implements CustomPacketPayload {

    /** 单个候选：展示用物品（数量 1）+ 背包可用量 + 网络可用量 */
    public record Entry(ItemStack stack, long bag, long net) {}

    public static final Type<OpenFtbItemSubmitSelectPacket> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":open_ftb_item_submit_select"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, Entry::stack,
            ByteBufCodecs.VAR_LONG, Entry::bag,
            ByteBufCodecs.VAR_LONG, Entry::net,
            Entry::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFtbItemSubmitSelectPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, OpenFtbItemSubmitSelectPacket::taskId,
                    ByteBufCodecs.STRING_UTF8, OpenFtbItemSubmitSelectPacket::title,
                    ByteBufCodecs.VAR_LONG, OpenFtbItemSubmitSelectPacket::remaining,
                    ENTRY_CODEC.apply(ByteBufCodecs.list(FtbItemSubmitSelectionService.MAX_ENTRIES)),
                    OpenFtbItemSubmitSelectPacket::entries,
                    OpenFtbItemSubmitSelectPacket::new);

    public static void handle(final OpenFtbItemSubmitSelectPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> com.solr98.beyondintegration.client.gui.FtbItemSubmitSelectScreen.open(packet));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
