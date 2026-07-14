package com.solr98.beyondintegration.feature.bind;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class NetworkMeters extends SavedData {

    private static final String NAME = "beyond_integration_network_meters";
    private static NetworkMeters instance;

    private final Map<Integer, NetworkMeterGroup> networks = new ConcurrentHashMap<>();

    public static void initialize(ServerLevel level) {
        instance = level.getDataStorage().computeIfAbsent(
                NetworkMeters::load, NetworkMeters::new, NAME);
    }

    public static NetworkMeters getInstance() { return instance; }
    public static void markDirty() { if (instance != null) instance.setDirty(); }

    public NetworkMeters() {}

    // ========== Interface (循环取出) ==========

    public static void recordInterfaceExtract(int netId, BlockPos pos, ResourceLocation item, long amount) {
        if (instance == null) return;
        InterfaceMeter m = instance.networks
                .computeIfAbsent(netId, k -> new NetworkMeterGroup())
                .interfaces
                .computeIfAbsent(pos, k -> new InterfaceMeter());
        m.totals.merge(item, amount, Long::sum);
        m.totalOps.incrementAndGet();
    }

    public static Map<ResourceLocation, Long> getInterfaceTotals(int netId, BlockPos pos) {
        if (instance == null) return Map.of();
        NetworkMeterGroup g = instance.networks.get(netId);
        if (g == null) return Map.of();
        InterfaceMeter m = g.interfaces.get(pos);
        if (m == null) return Map.of();
        return Map.copyOf(m.totals);
    }

    // ========== AE2 Cell (存储盘流量) ==========

    public static void recordCellFlow(int netId, UUID cellId, ResourceLocation item, long in, long out) {
        if (instance == null) return;
        CellMeter m = instance.networks
                .computeIfAbsent(netId, k -> new NetworkMeterGroup())
                .cells
                .computeIfAbsent(cellId, k -> new CellMeter());
        if (in > 0) m.inserted.merge(item, in, Long::sum);
        if (out > 0) m.extracted.merge(item, out, Long::sum);
    }

    public static CellMeter getCellFlow(int netId, UUID cellId) {
        if (instance == null) return null;
        NetworkMeterGroup g = instance.networks.get(netId);
        if (g == null) return null;
        return g.cells.get(cellId);
    }

    // ========== RS Pathway (通道流量) ==========

    public static void recordPathwayFlow(int netId, BlockPos pos, ResourceLocation item, long in, long out) {
        if (instance == null) return;
        PathwayMeter m = instance.networks
                .computeIfAbsent(netId, k -> new NetworkMeterGroup())
                .pathways
                .computeIfAbsent(pos, k -> new PathwayMeter());
        if (in > 0) m.inserted.merge(item, in, Long::sum);
        if (out > 0) m.extracted.merge(item, out, Long::sum);
    }

    public static PathwayMeter getPathwayFlow(int netId, BlockPos pos) {
        if (instance == null) return null;
        NetworkMeterGroup g = instance.networks.get(netId);
        if (g == null) return null;
        return g.pathways.get(pos);
    }

    // ========== Pump (抽取入网) ==========

    public static void recordPumpInsert(int netId, BlockPos pos, ResourceLocation item, long amount) {
        if (instance == null) return;
        MachineMeter m = instance.networks
                .computeIfAbsent(netId, k -> new NetworkMeterGroup())
                .pumps
                .computeIfAbsent(pos, k -> new MachineMeter());
        m.totals.merge(item, amount, Long::sum);
        m.totalOps.incrementAndGet();
    }

    public static Map<ResourceLocation, Long> getPumpTotals(int netId, BlockPos pos) {
        if (instance == null) return Map.of();
        NetworkMeterGroup g = instance.networks.get(netId);
        if (g == null) return Map.of();
        MachineMeter m = g.pumps.get(pos);
        if (m == null) return Map.of();
        return Map.copyOf(m.totals);
    }

    // ========== Hopper (世界收集) ==========

    public static void recordHopperCollect(int netId, BlockPos pos, ResourceLocation item, long amount) {
        if (instance == null) return;
        MachineMeter m = instance.networks
                .computeIfAbsent(netId, k -> new NetworkMeterGroup())
                .hoppers
                .computeIfAbsent(pos, k -> new MachineMeter());
        m.totals.merge(item, amount, Long::sum);
        m.totalOps.incrementAndGet();
    }

    public static Map<ResourceLocation, Long> getHopperTotals(int netId, BlockPos pos) {
        if (instance == null) return Map.of();
        NetworkMeterGroup g = instance.networks.get(netId);
        if (g == null) return Map.of();
        MachineMeter m = g.hoppers.get(pos);
        if (m == null) return Map.of();
        return Map.copyOf(m.totals);
    }

    // ========== EnergyPathway (能量推送) ==========

    public static void recordEpPush(int netId, BlockPos pos, long amount) {
        if (instance == null) return;
        MachineMeter m = instance.networks
                .computeIfAbsent(netId, k -> new NetworkMeterGroup())
                .energyPathways
                .computeIfAbsent(pos, k -> new MachineMeter());
        m.totals.merge(EnergyEP, amount, Long::sum);
        m.totalOps.incrementAndGet();
    }
    private static final ResourceLocation EnergyEP = new ResourceLocation("beyonddimensions:energy");

    public static Map<ResourceLocation, Long> getEpTotals(int netId, BlockPos pos) {
        if (instance == null) return Map.of();
        NetworkMeterGroup g = instance.networks.get(netId);
        if (g == null) return Map.of();
        MachineMeter m = g.energyPathways.get(pos);
        if (m == null) return Map.of();
        return Map.copyOf(m.totals);
    }

    // ========== Furnace (熔炼产出) ==========

    public static void recordFurnaceSmelt(int netId, BlockPos pos, ResourceLocation outputItem, long count) {
        if (instance == null) return;
        FurnaceMeter m = instance.networks
                .computeIfAbsent(netId, k -> new NetworkMeterGroup())
                .furnaces
                .computeIfAbsent(pos, k -> new FurnaceMeter());
        m.outputs.merge(outputItem, count, Long::sum);
    }

    public static Map<ResourceLocation, Long> getFurnaceOutputs(int netId, BlockPos pos) {
        if (instance == null) return Map.of();
        NetworkMeterGroup g = instance.networks.get(netId);
        if (g == null) return Map.of();
        FurnaceMeter m = g.furnaces.get(pos);
        if (m == null) return Map.of();
        return Map.copyOf(m.outputs);
    }

    // ========== Maid (女仆消耗) ==========

    public static void recordMaidExtract(int netId, UUID maidUuid, String maidName, ResourceLocation item, long amount) {
        if (instance == null) return;
        MaidMeter m = instance.networks
                .computeIfAbsent(netId, k -> new NetworkMeterGroup())
                .maids
                .computeIfAbsent(maidUuid, k -> new MaidMeter());
        m.totals.merge(item, amount, Long::sum);
        if (maidName != null) m.displayName = maidName;
    }

    // ========== Snapshot delta ==========

    public record SnapshotDelta(int netId, String sourceType, String sourceKey, ResourceLocation item, long delta) {}

    public static List<SnapshotDelta> collectDeltas() {
        List<SnapshotDelta> result = new ArrayList<>();
        if (instance == null) return result;
        long now = System.currentTimeMillis();
        for (var netEntry : instance.networks.entrySet()) {
            int netId = netEntry.getKey();
            NetworkMeterGroup g = netEntry.getValue();
            collectMapDeltas(netId, "INTERFACE", g.interfaces, result);
            collectMapDeltas(netId, "PUMP", g.pumps, result);
            collectMapDeltas(netId, "HOPPER", g.hoppers, result);
            collectMapDeltas(netId, "EP", g.energyPathways, result);
            collectFurnaceDeltas(netId, g, result);
            collectCellDeltas(netId, g, result);
            collectPathwayDeltas(netId, g, result);
            collectMaidDeltas(netId, g, result);
        }
        return result;
    }

    private static void collectMaidDeltas(int netId, NetworkMeterGroup g, List<SnapshotDelta> out) {
        for (var entry : g.maids.entrySet()) {
            UUID maidUuid = entry.getKey();
            MaidMeter m = entry.getValue();
            String displayKey = maidUuid.toString() + "|" + (m.displayName != null ? m.displayName : maidUuid.toString());
            for (var itemEntry : m.totals.entrySet()) {
                ResourceLocation item = itemEntry.getKey();
                long live = itemEntry.getValue();
                long base = m.snapshotBase.getOrDefault(item, 0L);
                long delta = live - base;
                if (delta != 0) {
                    out.add(new SnapshotDelta(netId, "MAID", displayKey, item, delta));
                    m.snapshotBase.put(item, live);
                }
            }
        }
    }

    private static void collectMapDeltas(int netId, String type, Map<BlockPos, ? extends HasSnapshot> meters, List<SnapshotDelta> out) {
        for (var entry : meters.entrySet()) {
            String key = entry.getKey().toShortString();
            HasSnapshot m = entry.getValue();
            for (var itemEntry : m.totals().entrySet()) {
                ResourceLocation item = itemEntry.getKey();
                long live = itemEntry.getValue();
                long base = m.snapshotBase().getOrDefault(item, 0L);
                long delta = live - base;
                if (delta != 0) {
                    out.add(new SnapshotDelta(netId, type, key, item, delta));
                    m.snapshotBase().put(item, live);
                }
            }
        }
    }

    private static void collectFurnaceDeltas(int netId, NetworkMeterGroup g, List<SnapshotDelta> out) {
        for (var entry : g.furnaces.entrySet()) {
            String key = entry.getKey().toShortString();
            FurnaceMeter m = entry.getValue();
            for (var itemEntry : m.outputs.entrySet()) {
                ResourceLocation item = itemEntry.getKey();
                long live = itemEntry.getValue();
                long base = m.snapshotBase.getOrDefault(item, 0L);
                long delta = live - base;
                if (delta != 0) {
                    out.add(new SnapshotDelta(netId, "FURNACE", key, item, delta));
                    m.snapshotBase.put(item, live);
                }
            }
        }
    }

    private static void collectCellDeltas(int netId, NetworkMeterGroup g, List<SnapshotDelta> out) {
        for (var entry : g.cells.entrySet()) {
            String key = entry.getKey().toString().substring(0, 8);
            CellMeter m = entry.getValue();
            for (var ie : m.inserted.entrySet()) {
                long live = ie.getValue();
                long base = m.snapshotInserted.getOrDefault(ie.getKey(), 0L);
                long delta = live - base;
                if (delta != 0) {
                    out.add(new SnapshotDelta(netId, "AE2_CELL", key, ie.getKey(), delta));
                    m.snapshotInserted.put(ie.getKey(), live);
                }
            }
            for (var ee : m.extracted.entrySet()) {
                long live = ee.getValue();
                long base = m.snapshotExtracted.getOrDefault(ee.getKey(), 0L);
                long delta = live - base;
                if (delta != 0) {
                    out.add(new SnapshotDelta(netId, "AE2_CELL", key, ee.getKey(), -delta));
                    m.snapshotExtracted.put(ee.getKey(), live);
                }
            }
        }
    }

    private static void collectPathwayDeltas(int netId, NetworkMeterGroup g, List<SnapshotDelta> out) {
        for (var entry : g.pathways.entrySet()) {
            String key = entry.getKey().toShortString();
            PathwayMeter m = entry.getValue();
            for (var ie : m.inserted.entrySet()) {
                long live = ie.getValue();
                long base = m.snapshotInserted.getOrDefault(ie.getKey(), 0L);
                long delta = live - base;
                if (delta != 0) {
                    out.add(new SnapshotDelta(netId, "RS_PATHWAY", key, ie.getKey(), delta));
                    m.snapshotInserted.put(ie.getKey(), live);
                }
            }
            for (var ee : m.extracted.entrySet()) {
                long live = ee.getValue();
                long base = m.snapshotExtracted.getOrDefault(ee.getKey(), 0L);
                long delta = live - base;
                if (delta != 0) {
                    out.add(new SnapshotDelta(netId, "RS_PATHWAY", key, ee.getKey(), -delta));
                    m.snapshotExtracted.put(ee.getKey(), live);
                }
            }
        }
    }

    // ========== Network totals ==========

    public static NetworkMeterGroup getNetworkMeters(int netId) {
        if (instance == null) return null;
        return instance.networks.get(netId);
    }

    // ========== Snapshot contract ==========

    private interface HasSnapshot {
        Map<ResourceLocation, Long> totals();
        Map<ResourceLocation, Long> snapshotBase();
    }

    // ========== Data classes ==========

    public static class NetworkMeterGroup {
        public final Map<BlockPos, InterfaceMeter> interfaces = new ConcurrentHashMap<>();
        public final Map<UUID, CellMeter> cells = new ConcurrentHashMap<>();
        public final Map<BlockPos, PathwayMeter> pathways = new ConcurrentHashMap<>();
        public final Map<BlockPos, MachineMeter> pumps = new ConcurrentHashMap<>();
        public final Map<BlockPos, MachineMeter> hoppers = new ConcurrentHashMap<>();
        public final Map<BlockPos, MachineMeter> energyPathways = new ConcurrentHashMap<>();
        public final Map<BlockPos, FurnaceMeter> furnaces = new ConcurrentHashMap<>();
        public final Map<UUID, MaidMeter> maids = new ConcurrentHashMap<>();
    }

    public static class InterfaceMeter implements HasSnapshot {
        public final Map<ResourceLocation, Long> totals = new ConcurrentHashMap<>();
        public final AtomicLong totalOps = new AtomicLong(0);
        public final Map<ResourceLocation, Long> snapshotBase = new ConcurrentHashMap<>();
        @Override public Map<ResourceLocation, Long> totals() { return totals; }
        @Override public Map<ResourceLocation, Long> snapshotBase() { return snapshotBase; }
    }

    public static class MachineMeter implements HasSnapshot {
        public final Map<ResourceLocation, Long> totals = new ConcurrentHashMap<>();
        public final AtomicLong totalOps = new AtomicLong(0);
        public final Map<ResourceLocation, Long> snapshotBase = new ConcurrentHashMap<>();
        @Override public Map<ResourceLocation, Long> totals() { return totals; }
        @Override public Map<ResourceLocation, Long> snapshotBase() { return snapshotBase; }
    }

    public static class FurnaceMeter {
        public final Map<ResourceLocation, Long> outputs = new ConcurrentHashMap<>();
        public final Map<ResourceLocation, Long> snapshotBase = new ConcurrentHashMap<>();
    }

    public static class MaidMeter {
        public final Map<ResourceLocation, Long> totals = new ConcurrentHashMap<>();
        public final Map<ResourceLocation, Long> snapshotBase = new ConcurrentHashMap<>();
        public String displayName;
    }

    public static class CellMeter {
        public final Map<ResourceLocation, Long> inserted = new ConcurrentHashMap<>();
        public final Map<ResourceLocation, Long> extracted = new ConcurrentHashMap<>();
        public final Map<ResourceLocation, Long> snapshotInserted = new ConcurrentHashMap<>();
        public final Map<ResourceLocation, Long> snapshotExtracted = new ConcurrentHashMap<>();
    }

    public static class PathwayMeter {
        public final Map<ResourceLocation, Long> inserted = new ConcurrentHashMap<>();
        public final Map<ResourceLocation, Long> extracted = new ConcurrentHashMap<>();
        public final Map<ResourceLocation, Long> snapshotInserted = new ConcurrentHashMap<>();
        public final Map<ResourceLocation, Long> snapshotExtracted = new ConcurrentHashMap<>();
    }

    // ========== NBT ==========

    public static NetworkMeters load(CompoundTag tag) {
        NetworkMeters m = new NetworkMeters();
        ListTag nets = tag.getList("networks", 10);
        for (int i = 0; i < nets.size(); i++) {
            CompoundTag nt = nets.getCompound(i);
            int netId = nt.getInt("id");
            NetworkMeterGroup g = new NetworkMeterGroup();

            ListTag ifList = nt.getList("interfaces", 10);
            for (int j = 0; j < ifList.size(); j++) {
                CompoundTag it = ifList.getCompound(j);
                BlockPos pos = BlockPos.of(it.getLong("pos"));
                InterfaceMeter im = new InterfaceMeter();
                im.totalOps.set(it.getLong("ops"));
                CompoundTag totals = it.getCompound("totals");
                for (String key : totals.getAllKeys()) {
                    im.totals.put(ResourceLocation.tryParse(key), totals.getLong(key));
                }
                g.interfaces.put(pos, im);
            }

            ListTag cellList = nt.getList("cells", 10);
            for (int j = 0; j < cellList.size(); j++) {
                CompoundTag ct = cellList.getCompound(j);
                CellMeter cm = new CellMeter();
                UUID cellId = ct.getUUID("cellId");
                putMap(ct, "inserted", cm.inserted);
                putMap(ct, "extracted", cm.extracted);
                g.cells.put(cellId, cm);
            }

            ListTag pwList = nt.getList("pathways", 10);
            for (int j = 0; j < pwList.size(); j++) {
                CompoundTag pt = pwList.getCompound(j);
                BlockPos pos = BlockPos.of(pt.getLong("pos"));
                PathwayMeter pm = new PathwayMeter();
                putMap(pt, "inserted", pm.inserted);
                putMap(pt, "extracted", pm.extracted);
                g.pathways.put(pos, pm);
            }

            ListTag pumpList = nt.getList("pumps", 10);
            for (int j = 0; j < pumpList.size(); j++) {
                CompoundTag pt = pumpList.getCompound(j);
                MachineMeter mm = new MachineMeter();
                mm.totalOps.set(pt.getLong("ops"));
                putMap(pt, "totals", mm.totals);
                g.pumps.put(BlockPos.of(pt.getLong("pos")), mm);
            }

            ListTag hopList = nt.getList("hoppers", 10);
            for (int j = 0; j < hopList.size(); j++) {
                CompoundTag pt = hopList.getCompound(j);
                MachineMeter mm = new MachineMeter();
                mm.totalOps.set(pt.getLong("ops"));
                putMap(pt, "totals", mm.totals);
                g.hoppers.put(BlockPos.of(pt.getLong("pos")), mm);
            }

            ListTag epList = nt.getList("energyPathways", 10);
            for (int j = 0; j < epList.size(); j++) {
                CompoundTag pt = epList.getCompound(j);
                MachineMeter mm = new MachineMeter();
                mm.totalOps.set(pt.getLong("ops"));
                putMap(pt, "totals", mm.totals);
                g.energyPathways.put(BlockPos.of(pt.getLong("pos")), mm);
            }

            ListTag furList = nt.getList("furnaces", 10);
            for (int j = 0; j < furList.size(); j++) {
                CompoundTag pt = furList.getCompound(j);
                FurnaceMeter fm = new FurnaceMeter();
                putMap(pt, "outputs", fm.outputs);
                g.furnaces.put(BlockPos.of(pt.getLong("pos")), fm);
            }

            ListTag maidList = nt.getList("maids", 10);
            for (int j = 0; j < maidList.size(); j++) {
                CompoundTag mt = maidList.getCompound(j);
                MaidMeter mm = new MaidMeter();
                mm.displayName = mt.contains("name") ? mt.getString("name") : null;
                putMap(mt, "totals", mm.totals);
                g.maids.put(mt.getUUID("uuid"), mm);
            }

            m.networks.put(netId, g);
        }
        return m;
    }

    private static void putMap(CompoundTag tag, String key, Map<ResourceLocation, Long> target) {
        CompoundTag sub = tag.getCompound(key);
        for (String k : sub.getAllKeys()) {
            target.put(ResourceLocation.tryParse(k), sub.getLong(k));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag nets = new ListTag();
        for (var netEntry : networks.entrySet()) {
            CompoundTag nt = new CompoundTag();
            nt.putInt("id", netEntry.getKey());
            NetworkMeterGroup g = netEntry.getValue();

            ListTag ifList = new ListTag();
            for (var ifEntry : g.interfaces.entrySet()) {
                CompoundTag it = new CompoundTag();
                it.putLong("pos", ifEntry.getKey().asLong());
                it.putLong("ops", ifEntry.getValue().totalOps.get());
                CompoundTag tot = new CompoundTag();
                for (var te : ifEntry.getValue().totals.entrySet()) {
                    tot.putLong(te.getKey().toString(), te.getValue());
                }
                it.put("totals", tot);
                ifList.add(it);
            }
            nt.put("interfaces", ifList);

            ListTag cellList = new ListTag();
            for (var cellEntry : g.cells.entrySet()) {
                CompoundTag ct = new CompoundTag();
                ct.putUUID("cellId", cellEntry.getKey());
                putMapRev(ct, "inserted", cellEntry.getValue().inserted);
                putMapRev(ct, "extracted", cellEntry.getValue().extracted);
                cellList.add(ct);
            }
            nt.put("cells", cellList);

            ListTag pwList = new ListTag();
            for (var pwEntry : g.pathways.entrySet()) {
                CompoundTag pt = new CompoundTag();
                pt.putLong("pos", pwEntry.getKey().asLong());
                putMapRev(pt, "inserted", pwEntry.getValue().inserted);
                putMapRev(pt, "extracted", pwEntry.getValue().extracted);
                pwList.add(pt);
            }
            nt.put("pathways", pwList);

            ListTag pumpList = new ListTag();
            for (var e : g.pumps.entrySet()) {
                CompoundTag pt = new CompoundTag();
                pt.putLong("pos", e.getKey().asLong());
                pt.putLong("ops", e.getValue().totalOps.get());
                putMapRev(pt, "totals", e.getValue().totals);
                pumpList.add(pt);
            }
            nt.put("pumps", pumpList);

            ListTag hopList = new ListTag();
            for (var e : g.hoppers.entrySet()) {
                CompoundTag pt = new CompoundTag();
                pt.putLong("pos", e.getKey().asLong());
                pt.putLong("ops", e.getValue().totalOps.get());
                putMapRev(pt, "totals", e.getValue().totals);
                hopList.add(pt);
            }
            nt.put("hoppers", hopList);

            ListTag epList = new ListTag();
            for (var e : g.energyPathways.entrySet()) {
                CompoundTag pt = new CompoundTag();
                pt.putLong("pos", e.getKey().asLong());
                pt.putLong("ops", e.getValue().totalOps.get());
                putMapRev(pt, "totals", e.getValue().totals);
                epList.add(pt);
            }
            nt.put("energyPathways", epList);

            ListTag furList = new ListTag();
            for (var e : g.furnaces.entrySet()) {
                CompoundTag pt = new CompoundTag();
                pt.putLong("pos", e.getKey().asLong());
                putMapRev(pt, "outputs", e.getValue().outputs);
                furList.add(pt);
            }
            nt.put("furnaces", furList);

            ListTag maidList = new ListTag();
            for (var e : g.maids.entrySet()) {
                CompoundTag mt = new CompoundTag();
                mt.putUUID("uuid", e.getKey());
                putMapRev(mt, "totals", e.getValue().totals);
                if (e.getValue().displayName != null) mt.putString("name", e.getValue().displayName);
                maidList.add(mt);
            }
            nt.put("maids", maidList);

            nets.add(nt);
        }
        tag.put("networks", nets);
        return tag;
    }

    private static void putMapRev(CompoundTag tag, String key, Map<ResourceLocation, Long> source) {
        CompoundTag sub = new CompoundTag();
        for (var e : source.entrySet()) {
            sub.putLong(e.getKey().toString(), e.getValue());
        }
        tag.put(key, sub);
    }
}
