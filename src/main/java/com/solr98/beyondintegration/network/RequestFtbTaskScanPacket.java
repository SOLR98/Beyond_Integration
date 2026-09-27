package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 手动触发 FTB 任务检测扫描（C2S，空载荷）：任务界面「任务检测扫描」按钮点击时发送，
 * 服务端清空检测缓存并立即执行一次检测（把主网络资源计入检测类任务），结果以 actionbar 提示。
 */
public class RequestFtbTaskScanPacket {

    public RequestFtbTaskScanPacket() {}

    /** 空载荷，无数据写入 */
    public static void encode(RequestFtbTaskScanPacket msg, FriendlyByteBuf buf) {}

    /** 空载荷，直接还原 */
    public static RequestFtbTaskScanPacket decode(FriendlyByteBuf buf) {
        return new RequestFtbTaskScanPacket();
    }

    /** 服务端执行：清缓存并触发一次任务检测 */
    public static void handle(RequestFtbTaskScanPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!ModList.get().isLoaded("ftbquests")) return;
            if (!FtbIntegrationHelper.isEnabled()) return;
            boolean ok = FtbIntegrationHelper.scanTasks(player);
            player.displayClientMessage(Component.translatable(ok
                    ? "beyond_integration.ftb.scan_done"
                    : "beyond_integration.ftb.scan_failed"), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
