package com.solr98.beyondintegration.feature.ftb;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.task.EnergyTask;
import dev.ftb.mods.ftbquests.quest.task.FluidTask;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * FTB 检测用网络物品收集器：按"任务需求"从玩家主网络精准收集匹配的物品。
 * <p>
 * 检测类物品任务只关心其目标物品（含标签/过滤器任务），因此这里遍历一次网络存储，
 * 仅保留能被任一非消耗物品任务匹配的键：普通任务按目标 Item 分组快速筛选，
 * 过滤器（标签）任务单独匹配。避免把整个网络全量注入 FTB 背包汇总。
 * <p>
 * 缓存按网络 ID（同一主网络的玩家共享扫描结果）；收集种类上限与缓存时长
 * 均为服务器全局配置（{@code ftb_integration.detect_max_item_types} /
 * {@code detect_cache_ticks}）。本类引用 FTB 类，仅在 ftbquests 已加载时调用。
 */
public final class FtbTaskNetworkScanner {

    /** 检测结果缓存（按网络 ID）：同一主网络的玩家共享扫描结果 */
    private static final Map<Integer, CachedItems> CACHE = new HashMap<>();
    /** 任务目标索引缓存（全局）：普通任务按目标 Item 分组 + 过滤器任务列表 */
    private static volatile TaskIndex TASK_INDEX = null;
    private static long TASK_INDEX_TIME = 0L;
    /** 任务索引有效期（ms），任务文件重载后最多 5 秒生效 */
    private static final long TASK_INDEX_TTL_MS = 5000L;

    private record CachedItems(List<KeyAmount> items, long tick) {}
    /** 任务目标索引：普通任务按目标 Item 分组；过滤器任务为编译后的匹配器（Predicate） */
    private record TaskIndex(Map<Item, List<ItemTask>> byItem, List<Predicate<ItemStack>> filters) {}

    private FtbTaskNetworkScanner() {}

    /** 网络销毁时清理指定网络的检测缓存 */
    public static void clearCache(int netId) {
        CACHE.remove(netId);
    }

    /** 服务器停止时清理全部检测缓存 */
    public static void clearAll() {
        CACHE.clear();
    }

    /** 缓存有效期（逻辑刻，配置项 {@code ftb_integration.detect_cache_ticks}；0 = 不缓存） */
    private static long cacheTtl() {
        try {
            return Math.max(0, CommandConfig.ftbDetectCacheTicks());
        } catch (Throwable ignored) {
            return 20L;
        }
    }

    /** 收集种类上限（配置项 {@code ftb_integration.detect_max_item_types}） */
    private static int maxItemTypes() {
        try {
            return Math.max(64, CommandConfig.ftbDetectMaxItemTypes());
        } catch (Throwable ignored) {
            return 8192;
        }
    }

    /** 按当前任务需求收集玩家主网络中匹配的物品键（无网络/无任务返回空列表） */
    public static List<KeyAmount> collect(ServerPlayer player) {
        if (player == null) return List.of();
        DimensionsNet net;
        try {
            net = DimensionsNet.getPrimaryNetFromPlayer(player);
        } catch (Throwable ignored) {
            return List.of();
        }
        if (net == null) return List.of();
        int netId = net.getId();
        long tick = player.level().getGameTime();
        long ttl = cacheTtl();
        CachedItems cached = CACHE.get(netId);
        if (ttl > 0 && cached != null && tick - cached.tick() < ttl) {
            return cached.items();
        }
        int maxTypes = maxItemTypes();
        List<KeyAmount> items = new ArrayList<>();
        try {
            // 任务目标索引（全局缓存）：普通任务按目标 Item 分组；过滤器（标签）任务单独收集
            TaskIndex index = taskIndex();
            if (index == null) return cache(netId, items, tick);
            for (KeyAmount ka : net.getUnifiedStorage().getStorage()) {
                if (ka == null || ka.amount() <= 0) continue;
                if (!(ka.key() instanceof ItemStackKey ik)) continue;
                ItemStack stack = ik.getReadOnlyStack();
                if (stack.isEmpty()) continue;
                if (matchesAny(stack, index.byItem(), index.filters())) {
                    items.add(ka);
                    if (items.size() >= maxTypes) break;
                }
            }
        } catch (Throwable ignored) {}
        return cache(netId, items, tick);
    }

    /** 物品是否与任一检测类任务目标匹配（自动检测的快速过滤；异常/无任务返回 false） */
    public static boolean isRelevant(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            TaskIndex index = taskIndex();
            if (index == null) return false;
            return matchesAny(stack, index.byItem(), index.filters());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 构建/获取任务目标索引（缓存 {@link #TASK_INDEX_TTL_MS}；无任务返回 null） */
    private static TaskIndex taskIndex() {
        long now = System.currentTimeMillis();
        TaskIndex index = TASK_INDEX;
        if (index != null && now - TASK_INDEX_TIME < TASK_INDEX_TTL_MS) {
            return index;
        }
        try {
            ServerQuestFile file = ServerQuestFile.INSTANCE;
            if (file == null) return null;
            List<Task> submitTasks = file.getSubmitTasks();
            if (submitTasks.isEmpty()) return null;
            Map<Item, List<ItemTask>> byItem = new HashMap<>();
            List<Predicate<ItemStack>> filters = new ArrayList<>();
            for (Task task : submitTasks) {
                if (!(task instanceof ItemTask itemTask)) continue;
                ItemStack target = itemTask.getItemStack();
                if (target.isEmpty()) continue;
                if (ItemMatchingSystem.INSTANCE.isItemFilter(target)) {
                    // 过滤器任务：经 FTB 适配器 API 编译为匹配器（重复匹配更高效；失败回退 task::test）
                    filters.add(compileFilter(itemTask, target, file.holderLookup()));
                } else {
                    byItem.computeIfAbsent(target.getItem(), k -> new ArrayList<>()).add(itemTask);
                }
            }
            if (byItem.isEmpty() && filters.isEmpty()) return null;
            index = new TaskIndex(byItem, filters);
            TASK_INDEX = index;
            TASK_INDEX_TIME = now;
            return index;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 查询指定物品任务目标在玩家主网络中的匹配数量（任务匹配语义）。
     * 无网络/任务不存在/非物品任务返回 -1（供客户端 tooltip 显示）。
     */
    /** 任务网络信息：库存数量 + 来源网络（ID / 展示标识，供 tooltip 显示） */
    public record TaskNetworkInfo(long count, int netId, String netName) {}

    /**
     * 查询指定任务在主网络中的库存信息（物品按任务匹配语义、流体按 fluid+components 精确匹配、
     * 能量为网络 FE 库存）。无网络/任务不存在/不支持的任务类型返回 null。
     */
    public static TaskNetworkInfo taskNetworkInfo(ServerPlayer player, long taskId) {
        try {
            ServerQuestFile file = ServerQuestFile.INSTANCE;
            if (file == null) return null;
            Task task = file.getTask(taskId);
            if (task == null) return null;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return null;

            long count;
            if (task instanceof ItemTask itemTask) {
                // 过滤器任务用编译匹配器（与扫描路径一致）
                Predicate<ItemStack> matcher = itemTask::test;
                ItemStack target = itemTask.getItemStack();
                if (!target.isEmpty() && ItemMatchingSystem.INSTANCE.isItemFilter(target)) {
                    matcher = compileFilter(itemTask, target, file.holderLookup());
                }
                count = countMatching(net, matcher);
            } else if (task instanceof FluidTask fluidTask) {
                count = countFluid(net, fluidTask);
            } else if (task instanceof EnergyTask) {
                count = countEnergy(net);
            } else {
                return null;
            }
            if (count < 0) return null;
            String name = net.hasCustomName() ? net.getCustomName() : String.valueOf(net.getId());
            return new TaskNetworkInfo(count, net.getId(), name == null ? "" : name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 统计主网络中匹配指定流体任务的数量（mB；失败返回 -1） */
    private static long countFluid(DimensionsNet net, FluidTask task) {
        if (net == null || task == null) return -1;
        FluidStackKey taskKey;
        try {
            taskKey = new FluidStackKey(new FluidStack(Holder.direct(task.getFluid()), 1,
                    task.getFluidDataComponentPatch()));
        } catch (Throwable ignored) {
            return -1;
        }
        long total = 0;
        try {
            for (KeyAmount ka : net.getUnifiedStorage().getStorage()) {
                if (ka == null || ka.amount() <= 0) continue;
                if (!(ka.key() instanceof FluidStackKey fk)) continue;
                if (!fk.isSameTypeSameComponents(taskKey)) continue;
                total += ka.amount();
                if (total < 0) return Long.MAX_VALUE;  // 溢出保护
            }
        } catch (Throwable ignored) {
            return -1;
        }
        return total;
    }

    /** 统计主网络能量库存（FE；失败返回 -1） */
    private static long countEnergy(DimensionsNet net) {
        if (net == null) return -1;
        try {
            return net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /** 遍历主网络累加匹配数量（失败返回 -1） */
    private static long countMatching(DimensionsNet net, Predicate<ItemStack> matcher) {
        if (net == null || matcher == null) return -1;
        long total = 0;
        try {
            for (KeyAmount ka : net.getUnifiedStorage().getStorage()) {
                if (ka == null || ka.amount() <= 0) continue;
                if (!(ka.key() instanceof ItemStackKey ik)) continue;
                ItemStack stack = ik.getReadOnlyStack();
                if (stack.isEmpty() || !matcher.test(stack)) continue;
                total += ka.amount();
                if (total < 0) return Long.MAX_VALUE;  // 溢出保护
            }
        } catch (Throwable ignored) {
            return -1;
        }
        return total;
    }

    /** 编译过滤器任务为匹配器（FTB 适配器 API；异常/空返回回退 task::test） */
    private static Predicate<ItemStack> compileFilter(ItemTask task, ItemStack target, HolderLookup.Provider lookup) {
        try {
            return ItemMatchingSystem.INSTANCE.getFilterAdapter(target)
                    .map(adapter -> (Predicate<ItemStack>) adapter.getMatcher(target, lookup))
                    .orElse(task::test);
        } catch (Throwable ignored) {
            return task::test;
        }
    }

    /** 物品是否被任一任务目标匹配（普通任务先按 Item 分组筛选；过滤器任务用编译匹配器） */
    private static boolean matchesAny(ItemStack stack, Map<Item, List<ItemTask>> byItem, List<Predicate<ItemStack>> filters) {
        List<ItemTask> candidates = byItem.get(stack.getItem());
        if (candidates != null) {
            for (ItemTask task : candidates) {
                if (task.test(stack)) return true;
            }
        }
        for (Predicate<ItemStack> matcher : filters) {
            if (matcher.test(stack)) return true;
        }
        return false;
    }

    /** 写入网络缓存并返回结果 */
    private static List<KeyAmount> cache(int netId, List<KeyAmount> items, long tick) {
        CACHE.put(netId, new CachedItems(items, tick));
        return items;
    }
}
