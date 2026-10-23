package com.solr98.beyondintegration.network;

import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.SlotGroupSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Supplier;

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
 * 对应 1.21.1 分支的 {@code network.payload.RequestWorkstationResyncPayload}。
 */
public class RequestWorkstationResyncPacket {

    /** 空参构造：该请求包不携带数据 */
    public RequestWorkstationResyncPacket() {}

    /** 无数据可写 */
    public static void encode(RequestWorkstationResyncPacket msg, FriendlyByteBuf buf) {}

    /** 无数据可读，直接还原空包 */
    public static RequestWorkstationResyncPacket decode(FriendlyByteBuf buf) {
        return new RequestWorkstationResyncPacket();
    }

    /** 服务端执行：强制当前网络菜单的所有槽位同步器做一次全量重发 */
    public static void handle(RequestWorkstationResyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;
            if (!(sp.containerMenu instanceof DimensionsNetMenu menu)) return;
            for (SlotGroupSync sync : menu.slotGroupSyncs) {
                forceFullResend(sync);
            }
        });
        ctx.get().setPacketHandled(true);
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
