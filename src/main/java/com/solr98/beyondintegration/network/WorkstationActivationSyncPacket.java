package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.WorkstationActivationCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 工作台状态同步包（S2C）：
 * 下发献祭激活开关、当前网络已激活的工作台 ID 列表，以及服务端启用的工作台列表
 * （配置权威值，供客户端工作站按钮的显示/隐藏与锁定状态判断）。
 */
public class WorkstationActivationSyncPacket {

    /** 献祭激活是否启用（false 时客户端不做锁定显示） */
    private final boolean enabled;
    /** 已激活的工作台 ID 列表 */
    private final List<String> activated;
    /** 服务端启用的工作台 ID 列表（workstations.enabled 权威值） */
    private final List<String> enabledWorkstations;

    public WorkstationActivationSyncPacket(boolean enabled, List<String> activated, List<String> enabledWorkstations) {
        this.enabled = enabled;
        this.activated = activated == null ? List.of() : new ArrayList<>(activated);
        this.enabledWorkstations = enabledWorkstations == null ? List.of() : new ArrayList<>(enabledWorkstations);
    }

    /** 写入开关、已激活列表与服务端启用列表 */
    public static void encode(WorkstationActivationSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enabled);
        buf.writeVarInt(msg.activated.size());
        for (String id : msg.activated) buf.writeUtf(id);
        buf.writeVarInt(msg.enabledWorkstations.size());
        for (String id : msg.enabledWorkstations) buf.writeUtf(id);
    }

    /** 读取开关、已激活列表与服务端启用列表 */
    public static WorkstationActivationSyncPacket decode(FriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean();
        int size = buf.readVarInt();
        List<String> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) ids.add(buf.readUtf());
        int wsSize = buf.readVarInt();
        List<String> enabledWorkstations = new ArrayList<>(wsSize);
        for (int i = 0; i < wsSize; i++) enabledWorkstations.add(buf.readUtf());
        return new WorkstationActivationSyncPacket(enabled, ids, enabledWorkstations);
    }

    /** 客户端收到后更新本模组工作台状态缓存 */
    public static void handle(WorkstationActivationSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                WorkstationActivationCache.update(msg.enabled, msg.activated, msg.enabledWorkstations);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
