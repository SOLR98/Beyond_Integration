package com.solr98.beyondintegration.network.payload;

import com.solr98.beyondintegration.BeyondIntegration;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.SlotGroupSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 工作站存储视图重同步请求（客户端 → 服务端，无负载）。
 * <p>
 * BD 的网络存储视图（{@code ClientNetStorage} / {@code DisorderedSlotGroupSync}）在菜单打开后的首个 tick
 * 全量下发一次；若该包在客户端菜单尚未就绪时到达会被丢弃，而 BD 的同步基线已经推进、后续只发增量，
 * 于是工作站界面的网络存储内容会一直为空（附魔台等切换打开的工作站尤易命中该竞态）。
 * <p>
 * 客户端在工作站界面初始化时（以及视图仍为空时按节流重试）发送本包，服务端强制对应同步器做一次
 * 全量重发，确保网络物品正常显示。反射访问 BD 私有字段以兼容 0.7.27 / 0.7.30。
 * <p>
 * TYPE: {@code beyond_integration:request_workstation_resync}。
 */
public record RequestWorkstationResyncPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestWorkstationResyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":request_workstation_resync"));
    public static final StreamCodec<FriendlyByteBuf, RequestWorkstationResyncPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestWorkstationResyncPayload());

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestWorkstationResyncPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof DimensionsNetMenu menu)) return;
            for (SlotGroupSync sync : menu.slotGroupSyncs) {
                forceFullResend(sync);
            }
        });
    }

    /** 清空同步器基线并标记全量重扫，随后触发一次发送。 */
    private static void forceFullResend(SlotGroupSync sync) {
        if (sync == null) return;
        try {
            Class<?> c = sync.getClass();
            while (c != null && c != Object.class) {
                if (c.getSimpleName().equals("DisorderedSlotGroupSync")) {
                    Field last = c.getDeclaredField("lastStorage");
                    last.setAccessible(true);
                    if (last.get(sync) instanceof List<?> l) l.clear();
                    Field dirty = c.getDeclaredField("dirtyFullRescan");
                    dirty.setAccessible(true);
                    dirty.setBoolean(sync, true);
                    Field init = c.getDeclaredField("initialized");
                    init.setAccessible(true);
                    init.setBoolean(sync, true);
                    break;
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {}
        try { sync.updateChange(); } catch (Throwable ignored) {}
    }
}
