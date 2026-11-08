package com.solr98.beyondintegration.feature.enchant;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 神化附魔台网络级数据（按网络 ID）：
 * <ul>
 *   <li>参数上限：献祭书架累加的 eterna/quanta/arcana/rectification；</li>
 *   <li>宝藏附魔：是否献祭过允许宝藏的书架；</li>
 *   <li>过滤附魔：从书架读取的黑名单 + 是否启用。</li>
 * </ul>
 * 存档名 {@code beyond_integration_apoth_stats}；懒加载。
 */
public class ApothStatData extends SavedData {

    private static final String NAME = "beyond_integration_apoth_stats";
    private static final String STATS_TAG = "stats";
    private static final String TREASURE_TAG = "treasure";
    private static final String FILTERED_TAG = "filtered";
    private static final String FILTER_ENABLED_TAG = "filterEnabled";

    private static final Map<Integer, int[]> DATA = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> TREASURE = new ConcurrentHashMap<>();
    private static final Map<Integer, Set<String>> FILTERED = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> FILTER_ENABLED = new ConcurrentHashMap<>();
    private static ApothStatData instance;

    public ApothStatData() {
    }

    public static ApothStatData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        DATA.clear();
        TREASURE.clear();
        FILTERED.clear();
        FILTER_ENABLED.clear();

        CompoundTag stats = tag.getCompound(STATS_TAG);
        for (String key : stats.getAllKeys()) {
            try {
                DATA.put(Integer.parseInt(key), stats.getIntArray(key));
            } catch (NumberFormatException ignored) {
            }
        }
        CompoundTag treasure = tag.getCompound(TREASURE_TAG);
        for (String key : treasure.getAllKeys()) {
            try {
                TREASURE.put(Integer.parseInt(key), treasure.getBoolean(key));
            } catch (NumberFormatException ignored) {
            }
        }
        CompoundTag filtered = tag.getCompound(FILTERED_TAG);
        for (String key : filtered.getAllKeys()) {
            try {
                int id = Integer.parseInt(key);
                Set<String> set = new HashSet<>();
                for (Tag t : filtered.getList(key, Tag.TAG_STRING)) set.add(t.getAsString());
                FILTERED.put(id, set);
            } catch (NumberFormatException ignored) {
            }
        }
        CompoundTag enabled = tag.getCompound(FILTER_ENABLED_TAG);
        for (String key : enabled.getAllKeys()) {
            try {
                FILTER_ENABLED.put(Integer.parseInt(key), enabled.getBoolean(key));
            } catch (NumberFormatException ignored) {
            }
        }
        if (instance == null) instance = new ApothStatData();
        return instance;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag stats = new CompoundTag();
        DATA.forEach((id, v) -> stats.put(String.valueOf(id), new IntArrayTag(v)));
        tag.put(STATS_TAG, stats);
        CompoundTag treasure = new CompoundTag();
        TREASURE.forEach((id, v) -> treasure.putBoolean(String.valueOf(id), v));
        tag.put(TREASURE_TAG, treasure);
        CompoundTag filtered = new CompoundTag();
        FILTERED.forEach((id, set) -> {
            ListTag list = new ListTag();
            set.forEach(s -> list.add(StringTag.valueOf(s)));
            filtered.put(String.valueOf(id), list);
        });
        tag.put(FILTERED_TAG, filtered);
        CompoundTag enabled = new CompoundTag();
        FILTER_ENABLED.forEach((id, v) -> enabled.putBoolean(String.valueOf(id), v));
        tag.put(FILTER_ENABLED_TAG, enabled);
        return tag;
    }

    private static ApothStatData ensure() {
        if (instance != null) return instance;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        instance = server.overworld().getDataStorage()
                .computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                        ApothStatData::new, ApothStatData::load, null), NAME);
        return instance;
    }

    // ── 参数上限 ──

    public static int[] get(int netId) {
        ensure();
        return DATA.getOrDefault(netId, new int[]{0, 0, 0, 0});
    }

    public static void set(int netId, int[] values) {
        ensure();
        DATA.put(netId, values);
        markDirty();
    }

    // ── 宝藏附魔 ──

    public static boolean allowsTreasure(int netId) {
        ensure();
        return TREASURE.getOrDefault(netId, false);
    }

    public static void setTreasure(int netId, boolean value) {
        ensure();
        TREASURE.put(netId, value);
        markDirty();
    }

    // ── 过滤附魔 ──

    /** 从书架读取到的黑名单附魔 ID。 */
    public static Set<String> filtered(int netId) {
        ensure();
        return FILTERED.getOrDefault(netId, Set.of());
    }

    /** 献祭书架时加入其黑名单，并自动启用过滤。 */
    public static void addFiltered(int netId, Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        ensure();
        FILTERED.computeIfAbsent(netId, k -> new HashSet<>()).addAll(ids);
        markDirty();
    }

    /** 手动移除一个被过滤的附魔。 */
    public static void removeFiltered(int netId, String id) {
        if (id == null) return;
        ensure();
        Set<String> set = FILTERED.get(netId);
        if (set != null && set.remove(id)) markDirty();
    }

    public static boolean filterEnabled(int netId) {
        ensure();
        return FILTER_ENABLED.getOrDefault(netId, false);
    }

    public static void setFilterEnabled(int netId, boolean value) {
        ensure();
        FILTER_ENABLED.put(netId, value);
        markDirty();
    }

    public static void markDirty() {
        ApothStatData data = ensure();
        if (data != null) data.setDirty();
    }
}
