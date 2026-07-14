package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.feature.bind.AuditEntry;
import com.solr98.beyondintegration.feature.bind.BindingAuditLog;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuiAuditHandler {

    private static final Map<UUID, SessionBuffer> sessions = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AbstractContainerMenu menu = event.getContainer();
        if (!(menu instanceof DimensionsNetMenu netMenu)) return;

        DimensionsNet net = resolveNet(netMenu);
        if (net == null) return;

        SessionBuffer buf = new SessionBuffer();
        AutoCloseable sub = net.getUnifiedStorage().subscribeDeltaWeak(player,
                (p, key, size, insert) -> buf.accumulate(key, size, insert));
        buf.subscription = sub;
        sessions.put(player.getUUID(), buf);
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SessionBuffer buf = sessions.remove(player.getUUID());
        if (buf == null) return;

        if (buf.subscription != null) {
            try { buf.subscription.close(); } catch (Exception ignored) {}
        }

        if (buf.delta.isEmpty()) return;

        AbstractContainerMenu menu = event.getContainer();
        if (!(menu instanceof DimensionsNetMenu netMenu)) return;
        DimensionsNet net = resolveNet(netMenu);
        if (net == null) return;

        StringBuilder detail = new StringBuilder();
        long totalDelta = 0;
        for (var e : buf.delta.entrySet()) {
            IStackKey<?> key = e.getKey();
            long delta = e.getValue();
            totalDelta += delta;
            if (detail.length() > 0) detail.append(",");
            detail.append(delta > 0 ? "+" : "").append(delta).append("x ");
            detail.append(key.getTypeId()).append(":").append(key.getSource());
        }

        String action = totalDelta >= 0 ? "GUI_INSERT" : "GUI_EXTRACT";
        BindingAuditLog.log(new AuditEntry(
                System.currentTimeMillis(),
                action,
                player.getName().getString(),
                player.getUUID(),
                net.getId(),
                "STORAGE", "",
                true,
                detail.toString()
        ));
    }

    private DimensionsNet resolveNet(DimensionsNetMenu netMenu) {
        if (netMenu instanceof INetMenuAccessor acc) {
            return acc.getBoundNet();
        }
        return null;
    }

    private static class SessionBuffer {
        final Map<IStackKey<?>, Long> delta = new ConcurrentHashMap<>();
        AutoCloseable subscription;

        void accumulate(IStackKey<?> key, long size, boolean insert) {
            delta.merge(key, insert ? size : -size, Long::sum);
        }
    }
}
