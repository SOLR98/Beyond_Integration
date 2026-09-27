package com.solr98.beyondintegration.feature.ftb;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * FTB 消耗型物品任务"选择提交"服务：当玩家背包 + 主网络中存在多种可提交物品
 * （典型为标签/过滤器任务）时，把候选物品汇总为列表发送客户端选择界面；
 * 玩家确认后按选择的数量提交——先扣背包、不足部分从主网络扣除，最后统一计入进度。
 * <p>
 * 选择界面仅在候选种类 &gt; 1 时弹出（单一候选保持原自动提交路径）；候选按
 * 物品类型 + NBT 合并，同一行展示"背包 N / 网络 M"的总可用量。
 * <p>
 * 本类引用 FTB / BD 类，仅在 ftbquests 已加载且集成启用时由数据包与 Mixin 调用。
 */
public final class FtbItemSubmitSelectionService {

    /** 选择界面候选种类上限（超出部分不展示） */
    public static final int MAX_ENTRIES = 128;

    /** 单个候选：展示用物品（数量 1）+ 背包可用量 + 网络可用量 */
    public record Entry(ItemStack stack, long bag, long net) {}

    /** Task 基类顺序检查方法句柄（@Shadow 无法定位父类方法，故用反射缓存） */
    private static Method checkSequenceMethod;

    private FtbItemSubmitSelectionService() {}

    /** 调用 Task.checkTaskSequence（反射失败时放行，保持可提交） */
    public static boolean checkTaskSequence(Task task, TeamData teamData) {
        if (task == null || teamData == null) return true;
        try {
            if (checkSequenceMethod == null) {
                Method m = Task.class.getDeclaredMethod("checkTaskSequence", TeamData.class);
                m.setAccessible(true);
                checkSequenceMethod = m;
            }
            return (boolean) checkSequenceMethod.invoke(task, teamData);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * 汇总玩家背包与主网络中匹配任务目标的物品（按类型 + NBT 合并），
     * 无候选返回空列表；任务不可提交时调用方应先校验。
     */
    public static List<Entry> collectEntries(ServerPlayer player, ItemTask task) {
        List<Entry> entries = new ArrayList<>();
        if (player == null || task == null) return entries;
        try {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.isEmpty() || !task.test(stack)) continue;
                merge(entries, stack, stack.getCount(), 0L);
            }
        } catch (Throwable ignored) {}
        try {
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net != null) {
                for (KeyAmount ka : net.getUnifiedStorage().getStorage()) {
                    if (entries.size() >= MAX_ENTRIES) break;
                    if (ka == null || ka.amount() <= 0) continue;
                    if (!(ka.key() instanceof ItemStackKey ik)) continue;
                    ItemStack stack = ik.getReadOnlyStack();
                    if (stack.isEmpty() || !task.test(stack)) continue;
                    merge(entries, stack, 0L, ka.amount());
                }
            }
        } catch (Throwable ignored) {}
        return entries;
    }

    /** 按类型 + NBT 合并到候选列表（超过上限的候选忽略） */
    private static void merge(List<Entry> entries, ItemStack stack, long bag, long net) {
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (ItemStack.isSameItemSameTags(e.stack(), stack)) {
                entries.set(i, new Entry(e.stack(), e.bag() + bag, e.net() + net));
                return;
            }
        }
        if (entries.size() < MAX_ENTRIES) {
            entries.add(new Entry(stack.copyWithCount(1), bag, net));
        }
    }

    /**
     * 处理玩家选择提交：按选择顺序先扣背包、不足部分从主网络扣除并计入进度。
     * 返回实际提交量；-1 表示任务状态校验失败（任务不存在/非消耗型/已完成/顺序不允许）。
     */
    public static long submitSelection(ServerPlayer player, long taskId, List<ItemStack> stacks, List<Long> amounts) {
        if (player == null || stacks == null || amounts == null) return -1;
        if (stacks.size() != amounts.size() || stacks.size() > MAX_ENTRIES) return -1;
        if (!FtbIntegrationHelper.isEnabled()) return -1;
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null) return -1;
        Task raw = file.getTask(taskId);
        if (!(raw instanceof ItemTask task)) return -1;
        if (!task.consumesResources() || task.isTaskScreenOnly()) return -1;
        TeamData teamData = TeamData.get(player);
        if (teamData == null || teamData.isCompleted(task)) return -1;
        if (!checkTaskSequence(task, teamData)) return -1;
        long remaining = task.getMaxProgress() - teamData.getProgress(task);
        if (remaining <= 0) return -1;

        long consumed = 0;
        for (int i = 0; i < stacks.size() && consumed < remaining; i++) {
            ItemStack want = stacks.get(i);
            Long amount = amounts.get(i);
            if (want == null || want.isEmpty() || amount == null || amount <= 0) continue;
            if (!task.test(want)) continue;
            long need = Math.min(amount, remaining - consumed);
            long got = takeFromInventory(player, want, need);
            if (got < need) {
                got += FtbIntegrationHelper.consumeFromNetwork(player,
                        s -> ItemStack.isSameItemSameTags(s, want), need - got);
            }
            consumed += got;
        }
        if (consumed > 0) {
            teamData.addProgress(task, consumed);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        return consumed;
    }

    /** 从背包按槽位顺序扣除与选择一致的物品，返回实际扣除量 */
    private static long takeFromInventory(ServerPlayer player, ItemStack want, long amount) {
        long got = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && got < amount; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !ItemStack.isSameItemSameTags(stack, want)) continue;
            int take = (int) Math.min(amount - got, stack.getCount());
            if (take <= 0) continue;
            stack.shrink(take);
            got += take;
            if (stack.isEmpty()) inventory.setItem(i, ItemStack.EMPTY);
        }
        return got;
    }
}
