package com.solr98.beyondintegration.network.payload;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 手动触发 FTB 任务检测扫描（C2S，空载荷）：任务界面「任务检测扫描」按钮点击时发送，
 * 服务端清空检测缓存并立即执行一次检测（把主网络资源计入检测类任务），结果以 actionbar 提示。
 */
public record RequestFtbTaskScanPayload() implements CustomPacketPayload {
    public static final Type<RequestFtbTaskScanPayload> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":request_ftb_task_scan"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestFtbTaskScanPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestFtbTaskScanPayload());

    public static void handle(final RequestFtbTaskScanPayload packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!ModList.get().isLoaded("ftbquests")) return;
            if (!FtbIntegrationHelper.isEnabled()) return;
            boolean ok = FtbIntegrationHelper.scanTasks(player);
            player.displayClientMessage(Component.translatable(ok
                    ? "beyond_integration.ftb.scan_done"
                    : "beyond_integration.ftb.scan_failed"), true);
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
