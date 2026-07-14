package com.solr98.beyondintegration.feature.bind;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class MeterSnapshotTicker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SNAPSHOT_INTERVAL = 6000;
    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new MeterSnapshotTicker());
        LOGGER.info("[MeterSnapshot] Registered, interval={} ticks (5min)", SNAPSHOT_INTERVAL);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        if (server.getTickCount() % SNAPSHOT_INTERVAL != 0) return;

        List<NetworkMeters.SnapshotDelta> deltas = NetworkMeters.collectDeltas();
        if (deltas.isEmpty()) return;

        long now = System.currentTimeMillis();
        List<AuditEntry> batch = new ArrayList<>(deltas.size());
        for (NetworkMeters.SnapshotDelta d : deltas) {
            String detail = d.item().toString() + ":" + d.delta();
            if (d.sourceType().equals("MAID") && d.sourceKey().contains("|")) {
                // sourceKey format: "uuid|displayName" for maids
                detail = d.item().toString() + ":" + d.delta() + "|maid:" + d.sourceKey().substring(d.sourceKey().indexOf('|') + 1);
            }
            batch.add(new AuditEntry(
                    now,
                    "METER_SNAPSHOT",
                    "-", null,
                    d.netId(),
                    "METER",
                    d.sourceType() + "|" + d.sourceKey(),
                    true,
                    detail
            ));
        }

        BindingAuditLog.logBatch(batch);
        NetworkMeters.markDirty();
    }
}
