package com.solr98.beyondintegration.feature.ftb;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.compat.RsIntegrationCompat;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.item.XpExchangeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * FTB Quests 集成助手：
 * <ol>
 *   <li>检测类物品任务按任务需求精准计入主网络资源（见 {@link FtbTaskNetworkScanner}）；</li>
 *   <li>消耗型任务提交时，背包与主网络合并取料（背包不足部分直接从网络扣除）；</li>
 *   <li>Shift+领取所有奖励时，物品奖励直接发放进主网络（见 {@code FtbItemRewardMixin}）。</li>
 * </ol>
 * 总开关：服务端配置 {@code ftb_integration.enable}；检测到 rs_integration（RI）时运行时让路禁用
 * （同时 MixinPlugin 在 RI 加载时不应用相关 mixin）。
 */
public final class FtbIntegrationHelper {

    /** "领取进网络"标记（服务端主线程访问；Shift+领取所有期间生效） */
    private static final Set<UUID> NETWORK_CLAIM = new HashSet<>();

    /** 批量领取简化通知统计：玩家 → [入网件数, 条目数]（服务端主线程访问） */
    private static final java.util.Map<UUID, long[]> BATCH_NOTIFY = new java.util.HashMap<>();

    private FtbIntegrationHelper() {}

    /** 集成是否启用（配置开关 + RI 让路） */
    public static boolean isEnabled() {
        try {
            return CommandConfig.ftbIntegrationEnabled() && !RsIntegrationCompat.isLoaded();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 玩家退出时清理领取标记（检测缓存按网络 ID，随网络销毁/停服清理） */
    public static void clearCache(UUID playerId) {
        NETWORK_CLAIM.remove(playerId);
        BATCH_NOTIFY.remove(playerId);
    }

    /** 开始批量领取统计（简化通知开启时由批量入口调用） */
    public static void beginBatchNotify(ServerPlayer player) {
        if (player != null) BATCH_NOTIFY.put(player.getUUID(), new long[]{0L, 0L});
    }

    /** 记录一条批量入网（inserted = 实际入网件数） */
    public static void addBatchNotify(ServerPlayer player, long inserted) {
        if (player == null || inserted <= 0) return;
        long[] stat = BATCH_NOTIFY.get(player.getUUID());
        if (stat != null) {
            stat[0] += inserted;
            stat[1] += 1;
        }
    }

    /** 结束批量领取：返回 [入网件数, 条目数]（无记录返回 null）并清理 */
    public static long[] endBatchNotify(ServerPlayer player) {
        return player == null ? null : BATCH_NOTIFY.remove(player.getUUID());
    }

    /** 是否处于批量领取统计上下文 */
    public static boolean isBatchNotify(ServerPlayer player) {
        return player != null && BATCH_NOTIFY.containsKey(player.getUUID());
    }

    // ── FTB 检测递归保护（大任务书 + 网络物品注入时，链式完成会不断递归 detect 直至栈溢出） ──

    /** FTB 检测递归深度（每完成一个任务 FTB 会递归调用 detect） */
    private static int ftbDetectDepth = 0;
    /** 本次最外层检测是否已注入网络物品（防止递归中缓存重复累加导致数量膨胀） */
    private static boolean ftbInjectedThisDetect = false;
    /** detect 递归深度上限（超过则打断，剩余任务由延迟检测在后续 tick 分批完成） */
    private static final int MAX_FTB_DETECT_DEPTH = 32;
    /** 反射缓存的 FTB 延迟检测调度方法（scheduleInventoryCheck 为包私有） */
    private static java.lang.reflect.Method ftbScheduleCheck;

    /** 进入一次 FTB 检测；返回 false = 超过递归深度上限，调用方应取消本次检测 */
    public static boolean enterFtbDetect() {
        if (ftbDetectDepth >= MAX_FTB_DETECT_DEPTH) return false;
        ftbDetectDepth++;
        if (ftbDetectDepth == 1) ftbInjectedThisDetect = false;
        return true;
    }

    /** 退出一次 FTB 检测（递归返回时递减；被取消的调用不会进入） */
    public static void exitFtbDetect() {
        ftbDetectDepth = Math.max(0, ftbDetectDepth - 1);
    }

    /** 标记本次检测已注入网络物品；返回 false = 已注入过（跳过，避免递归重复累加） */
    public static boolean markNetworkItemsInjected() {
        if (ftbInjectedThisDetect) return false;
        ftbInjectedThisDetect = true;
        return true;
    }

    /** 调度 FTB 延迟检测（包私有方法反射调用；剩余任务在后续 tick 分批完成） */
    public static void scheduleDeferredDetect(ServerPlayer player) {
        if (player == null) return;
        try {
            if (ftbScheduleCheck == null) {
                ftbScheduleCheck = Class.forName("dev.ftb.mods.ftbquests.util.DeferredInventoryDetection")
                        .getDeclaredMethod("scheduleInventoryCheck", ServerPlayer.class, int.class);
                ftbScheduleCheck.setAccessible(true);
            }
            ftbScheduleCheck.invoke(null, player, 1);
        } catch (Throwable ignored) {}
    }

    /** 批量领取结束：发送简化汇总通知（无入网条目时不发） */
    public static void sendBatchNotifySummary(ServerPlayer player) {
        long[] stat = endBatchNotify(player);
        if (stat == null || stat[1] <= 0) return;
        player.displayClientMessage(Component.translatable(
                "beyond_integration.ftb.reward.batch_notice", stat[1], stat[0])
                .withStyle(ChatFormatting.AQUA), true);
    }

    /** 网络销毁时清理该网络的检测缓存 */
    public static void clearNetCache(int netId) {
        try {
            FtbTaskNetworkScanner.clearCache(netId);
        } catch (Throwable ignored) {}
    }

    /** 服务器停止时清理全部检测缓存 */
    public static void clearAllCaches() {
        try {
            FtbTaskNetworkScanner.clearAll();
        } catch (Throwable ignored) {}
    }

    /**
     * 从主网络提取 XP 流体并折算为经验点（1 点 = 20 mB），返回实际可补充的点数。
     * 优先标准 XP 流体键，不足时遍历网络中的其他经验流体（与经验棒直设模式一致）。
     */
    public static long extractXpFluid(ServerPlayer player, long points) {
        if (player == null || points <= 0) return 0;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return 0;
        var storage = net.getUnifiedStorage();
        long needMb = points > Long.MAX_VALUE / 20 ? Long.MAX_VALUE : points * 20L;
        long gotMb = 0;
        try {
            gotMb += storage.extract(com.solr98.beyondintegration.handler.EnchantmentBookSeparatorHandler
                    .xpFluidKey(), needMb, false, false).amount();
            if (gotMb < needMb && XpExchangeItem.xpFluids != null) {
                for (Fluid f : XpExchangeItem.xpFluids) {
                    if (gotMb >= needMb) break;
                    try {
                        gotMb += storage.extract(new FluidStackKey(new FluidStack(f, 1)),
                                needMb - gotMb, false, false).amount();
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        if (gotMb > 0) net.setDirty();
        return gotMb / 20;
    }

    /** 开始一次"领取进网络"（Shift+领取所有）：期间 ItemReward.claim 将奖励插入主网络 */
    public static void beginNetworkClaim(ServerPlayer player) {
        if (player != null) NETWORK_CLAIM.add(player.getUUID());
    }

    /** 结束"领取进网络" */
    public static void endNetworkClaim(ServerPlayer player) {
        if (player != null) NETWORK_CLAIM.remove(player.getUUID());
    }

    /** 当前玩家是否处于"领取进网络" */
    public static boolean isNetworkClaim(ServerPlayer player) {
        return player != null && NETWORK_CLAIM.contains(player.getUUID());
    }

    /**
     * 手动触发一次 FTB 任务检测（把主网络资源计入检测类任务）。
     * 先强制清空检测缓存，保证本次扫描反映最新的网络资源；
     * 经反射调用 FTB 的 {@code FTBQuestsInventoryListener.detect}，FTB 未安装时安全返回 false。
     */
    public static boolean scanTasks(ServerPlayer player) {
        if (player == null) return false;
        try {
            // 手动检测语义：忽略缓存，强制重新扫描主网络
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net != null) FtbTaskNetworkScanner.clearCache(net.getId());
        } catch (Throwable ignored) {}
        try {
            Class<?> listener = Class.forName("dev.ftb.mods.ftbquests.util.FTBQuestsInventoryListener");
            listener.getMethod("detect", ServerPlayer.class, ItemStack.class, long.class)
                    .invoke(null, player, ItemStack.EMPTY, 0L);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 查询指定任务在主网络中的库存信息（null = 无网络/不可用/不支持的任务类型） */
    public static FtbTaskNetworkScanner.TaskNetworkInfo taskNetworkInfo(ServerPlayer player, long taskId) {
        try {
            return FtbTaskNetworkScanner.taskNetworkInfo(player, taskId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 从主网络直接扣除匹配流体，返回实际扣除量（mB）。
     * 用于流体任务点击提交时从网络提交（任务屏之外的补充路径）。
     */
    public static long consumeFluidFromNetwork(ServerPlayer player, Predicate<FluidStackKey> matcher, long amountMb) {
        if (player == null || matcher == null || amountMb <= 0) return 0;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return 0;
        var storage = net.getUnifiedStorage();
        long consumed = 0;
        try {
            List<KeyAmount> snapshot = new ArrayList<>(storage.getStorage());
            for (KeyAmount ka : snapshot) {
                if (consumed >= amountMb) break;
                if (!(ka.key() instanceof FluidStackKey fk)) continue;
                if (!matcher.test(fk)) continue;
                long want = Math.min(amountMb - consumed, ka.amount());
                if (want <= 0) continue;
                long got = storage.extract(fk, want, false, false).amount();
                if (got > 0) consumed += got;
            }
        } catch (Throwable ignored) {}
        if (consumed > 0) net.setDirty();
        return consumed;
    }

    /**
     * 从主网络直接扣除能量（FE），返回实际扣除量。
     * 用于能量任务点击提交时从网络提交（任务屏之外的补充路径）。
     */
    public static long consumeEnergyFromNetwork(ServerPlayer player, long amountFe) {
        if (player == null || amountFe <= 0) return 0;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return 0;
        long got = 0;
        try {
            got = net.getUnifiedStorage().extract(EnergyStackKey.INSTANCE, amountFe, false, false).amount();
        } catch (Throwable ignored) {}
        if (got > 0) net.setDirty();
        return got;
    }

    /**
     * 从主网络直接扣除匹配物品（不产生实体物品），返回实际扣除数量。
     * 用于消耗型任务提交时补足背包不足的缺口。
     */
    public static long consumeFromNetwork(ServerPlayer player, Predicate<ItemStack> matcher, long amount) {
        if (player == null || matcher == null || amount <= 0) return 0;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return 0;
        var storage = net.getUnifiedStorage();
        long consumed = 0;
        try {
            // 复制列表避免遍历期间存储变化
            List<KeyAmount> snapshot = new ArrayList<>(storage.getStorage());
            for (KeyAmount ka : snapshot) {
                if (consumed >= amount) break;
                if (!(ka.key() instanceof ItemStackKey ik)) continue;
                ItemStack stack = ik.getReadOnlyStack();
                if (stack.isEmpty() || !matcher.test(stack)) continue;
                long want = Math.min(amount - consumed, ka.amount());
                if (want <= 0) continue;
                long got = storage.extract(ik, want, false, false).amount();
                if (got > 0) consumed += got;
            }
        } catch (Throwable ignored) {}
        if (consumed > 0) net.setDirty();
        return consumed;
    }
}
