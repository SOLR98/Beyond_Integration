package com.solr98.beyondintegration.handler;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.soul.NetworkSoulSource;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 仪式结构方块的「网络来源」：原版结构检测失败时，用网络里的方块物品补齐缺口。
 * <p>
 * 流程（{@code RitualRequirementsMixin} + {@code RitualCheckerMixin}）：
 * <ol>
 *   <li>{@code RitualRequirements.getStructures} 进入时记录施法者（{@link #begin}，支持嵌套）；</li>
 *   <li>原版 {@code RitualChecker.hasBlocks} 判定<b>失败时</b>才查网络（成功路径零开销）；</li>
 *   <li><b>不扫描网络桶</b>：按 predicate 在方块注册表里筛出匹配方块，再对每个方块用
 *       <b>组装键</b> {@code ItemStackKey(物品)} 做精确查询（{@code getStackByKey}，O(1)），
 *       累计够需求量即提前结束；</li>
 *   <li>不展开 NBT 变种（只查精确键）；</li>
 *   <li>结构检查结束时（{@link #end}）：通过 → 提示「由网络提供」；不通过 → 输出缺少清单。</li>
 * </ol>
 * <p><b>网络选取</b>：优先用仪式灵魂源网络（{@link NetworkSoulSource#usableNetOf}，
 * 与仪式扣魂同源）；拿不到就跳过。
 * <p><b>花盆</b>：原版 predicate 要求「花盆里装着内容物」（{@code FlowerPotBlock} 且
 * {@code content != AIR}），而网络里是「花盆本体 + 内容物」两件物品，故该 predicate
 * 被分解为 {@code minecraft:flower_pot} 与内容物两组精确键，取二者较小值。
 */
public final class RitualNetworkStructure {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 当前结构检查的施法者（null = 不在检查流程内，不启用网络来源）。 */
    private static final ThreadLocal<Player> CURRENT = new ThreadLocal<>();
    /** 结构检查嵌套深度（{@code getProperStructure} 内可能重复调用 {@code getStructures}）。 */
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    /** 本次结构检查中是否有方块由网络提供。 */
    private static final ThreadLocal<Boolean> USED_NETWORK = ThreadLocal.withInitial(() -> Boolean.FALSE);
    /** 本次结构检查中未满足的结构条件（用于给玩家输出缺少清单）。 */
    private static final ThreadLocal<List<Missing>> MISSING = ThreadLocal.withInitial(ArrayList::new);
    /** 最近一次 {@link #hasBlocks} 查到的网络可用量（供缺少清单展示）。 */
    private static final ThreadLocal<Long> LAST_AVAILABLE = ThreadLocal.withInitial(() -> 0L);
    /** 最近一次查询命中的代表方块名（供缺少清单展示，避免再遍历注册表）。 */
    private static final ThreadLocal<List<String>> LAST_NAMES =
            ThreadLocal.withInitial(() -> new ArrayList<>(3));

    /** 一条未满足的结构条件：需求数、场地已有数、网络可用数、代表方块名。 */
    private record Missing(int required, int found, long network, List<String> names) {
    }

    /** 可作为花盆内容物的方块（惰性构建一次）。 */
    private static Set<Block> potContents;

    /** 提示节流：玩家 → 上次提示时间（毫秒）。 */
    private static final Map<UUID, Long> LAST_NOTIFY = new ConcurrentHashMap<>();
    private static final long NOTIFY_INTERVAL_MS = 4000L;

    /** 缺少清单节流（聊天栏比 action bar 更吵，间隔更长）。 */
    private static final Map<UUID, Long> LAST_MISSING = new ConcurrentHashMap<>();
    private static final long MISSING_INTERVAL_MS = 8000L;
    /** 缺少清单单次最多列出的条目数。 */
    private static final int MISSING_MAX_ROWS = 6;

    private RitualNetworkStructure() {
    }

    /** 进入结构检查（记录施法者；嵌套调用时保留最外层施法者）。 */
    public static void begin(Player player) {
        int depth = DEPTH.get();
        if (depth == 0) {
            USED_NETWORK.set(Boolean.FALSE);
            MISSING.get().clear();
            CURRENT.set(player);
        } else if (player != null) {
            CURRENT.set(player);
        }
        DEPTH.set(depth + 1);
    }

    /**
     * 结束结构检查：
     * <ul>
     *   <li>结构通过且用过网络方块 → 提示「由网络提供」；</li>
     *   <li>结构<b>未通过</b> → 输出「缺少清单」给玩家（哪些方块、缺多少、场地/网络各有几个）。</li>
     * </ul>
     */
    public static void end(boolean structureOk) {
        int depth = Math.max(0, DEPTH.get() - 1);
        DEPTH.set(depth);
        if (depth > 0) return; // 仍在嵌套内

        Player player = CURRENT.get();
        boolean used = Boolean.TRUE.equals(USED_NETWORK.get());
        List<Missing> missing = new ArrayList<>(MISSING.get());
        reset();
        if (player == null) return;

        if (structureOk) {
            if (used) notifyPlayer(player);
            return;
        }
        if (!missing.isEmpty()) reportMissing(player, missing);
    }

    /**
     * 记录一条未满足的结构条件（需求来自 Goety 的 {@code RitualChecker.hasBlocks} 入参）。
     *
     * @return {@code true} 表示已记录（调用方应让检查流程继续以便收集完整清单）；
     *         {@code false} 表示未启用该功能（调用方保持原版短路行为）
     */
    public static boolean recordMissing(Predicate<BlockState> predicate, int required, int found) {
        if (!CommandConfig.ritualNetworkStructure()) return false;
        MISSING.get().add(new Missing(required, found, LAST_AVAILABLE.get(),
                List.copyOf(LAST_NAMES.get())));
        return true;
    }

    /** 本次结构检查是否已登记了缺口（收尾时据此强制判失败）。 */
    public static boolean hasMissing() {
        List<Missing> list = MISSING.get();
        return list != null && !list.isEmpty();
    }

    /** 清理上下文（未走完整流程时兜底调用）。 */
    public static void reset() {
        CURRENT.remove();
        DEPTH.remove();
        USED_NETWORK.remove();
        MISSING.remove();
        LAST_AVAILABLE.remove();
        LAST_NAMES.remove();
        CHARGED.remove();
    }

    /** 清空缓存（服务器停止时调用；本类已不再缓存网络桶，保留以兼容既有调用点）。 */
    public static void clear() {
        LAST_NAMES.remove();
    }

    /** 网络销毁（本类不缓存网络桶，保留以兼容既有调用点）。 */
    public static void clear(int netId) {
        // 无缓存可清
    }

    /**
     * 用网络清单补齐原版判定：{@code need} 个满足 {@code predicate} 的方块是否可由网络提供。
     * <p>仅在原版结构检查失败后调用；<b>前置条件与「环境绕过」等同</b>
     * （祭坛附近的网络通道 / 维度连接方块数量达标，见 {@link RitualTrigger#met}）。
     * <p>首次真正启用网络补块时按 {@code goety_ritual.structure_soul_cost} 扣一次魂
     * （与「环境绕过」同样走 {@link RitualEnvSoulCharge}：牢笼原生优先、不足由网络补）；
     * 魂不足则本次不补块（结构判定保持原版失败）。
     */
    public static boolean hasBlocks(Level level, BlockPos altarPos,
                                    Predicate<BlockState> predicate, int need) {
        if (need <= 0) return false;
        if (level == null || level.isClientSide()) return false;
        if (!CommandConfig.ritualNetworkStructure()) return false;

        Player player = CURRENT.get();
        if (player == null) return false; // 拿不到玩家：跳过网络来源

        // 前置条件：与仪式环境绕过一致（网络通道 / 维度连接方块数量）
        if (!RitualTrigger.met(level, altarPos)) return false;

        // 网络来源：优先仪式灵魂源网络（与扣魂同源），不可用则跳过
        DimensionsNet net = NetworkSoulSource.usableNetOf(player);
        if (net == null) return false;

        long available = countAvailable(net, predicate, need);
        LAST_AVAILABLE.set(available);
        if (available < need) return false;

        // 补块的额外魂消耗：本次结构检查只扣一次；魂不足则不补块
        if (!chargeOnce(level, altarPos)) return false;

        USED_NETWORK.set(Boolean.TRUE);
        LOGGER.debug("[BI-Ritual] structure requirement x{} satisfied by net #{} (available {})",
                need, net.getId(), available);
        return true;
    }

    /** 本次结构检查是否已扣过「网络补块」魂（避免逐 predicate 重复扣）。 */
    private static final ThreadLocal<Boolean> CHARGED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 网络补块的额外魂消耗（每次结构检查最多一次）；{@code cost<=0} 或不支持扣魂时直接放行。 */
    private static boolean chargeOnce(Level level, BlockPos altarPos) {
        if (Boolean.TRUE.equals(CHARGED.get())) return true;
        long cost = CommandConfig.ritualStructureSoulCost();
        if (cost <= 0L) {
            CHARGED.set(Boolean.TRUE);
            return true;
        }
        if (level.getBlockEntity(altarPos) instanceof RitualEnvSoulCharge charger) {
            if (!charger.beyond$chargeEnvBypassSoul(cost)) return false; // 魂不足 → 本次不补块
        }
        CHARGED.set(Boolean.TRUE);
        return true;
    }

    /**
     * 统计网络可提供的该类方块总数。
     * <p><b>不扫描网络桶</b>：按 predicate 在<b>方块注册表</b>里筛出匹配方块，再对每个方块
     * 组装键 {@code ItemStackKey(物品)} 做精确查询（{@code getStackByKey}，O(1) 哈希），
     * 累计够 {@code need} 即提前结束。
     * <p>花盆：predicate 要求「装着内容物」时，额外取
     * {@code minecraft:flower_pot} 与内容物两组精确键，取较小值。
     */
    private static long countAvailable(DimensionsNet net, Predicate<BlockState> predicate, int need) {
        long total = 0L;
        List<String> names = new ArrayList<>(3);

        for (Block block : BuiltInRegistries.BLOCK) {
            Item item = block.asItem();
            if (item == Items.AIR) continue; // 无物品形态的方块无法入网
            if (!matches(predicate, block)) continue;

            // 名字与「网络有没有货」无关：缺少清单也要能说清是哪种方块（同类物品去重）
            if (names.size() < 3) {
                String display = block.getName().getString();
                if (!names.contains(display)) names.add(display);
            }

            long n = exactCount(net, item);
            if (n > 0L) total += n;
            if (total >= need) { // 已够用量：提前结束
                LAST_NAMES.set(names);
                return total;
            }
        }

        // 花盆本体 + 内容物：两组精确键，取较小值
        if (isFilledPotPredicate(predicate)) {
            long pots = exactCount(net, Items.FLOWER_POT);
            if (pots > 0L) {
                long contents = 0L;
                for (Block content : potContents()) {
                    if (contents >= pots) break;
                    Item item = content.asItem();
                    if (item == Items.AIR) continue;
                    contents += exactCount(net, item);
                }
                if (contents > 0L) {
                    total += Math.min(pots, contents);
                    if (names.isEmpty()) names.add(Blocks.FLOWER_POT.getName().getString());
                }
            }
        }

        LAST_NAMES.set(names);
        return total;
    }

    /** 安全地测试 predicate（个别方块状态可能让 predicate 抛异常）。 */
    private static boolean matches(Predicate<BlockState> predicate, Block block) {
        try {
            return predicate.test(block.defaultBlockState());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 精确键查询：该物品在网络里的数量（不匹配 NBT 变种）。 */
    private static long exactCount(DimensionsNet net, Item item) {
        return net.getUnifiedStorage()
                .getStackByKey(new ItemStackKey(new ItemStack(item)))
                .amount();
    }

    /**
     * predicate 是否要求「花盆里装着内容物」：用已知的非空花盆与空花盆探针判定，
     * 不依赖 Goety 的具体实现。
     */
    private static boolean isFilledPotPredicate(Predicate<BlockState> predicate) {
        try {
            if (!predicate.test(Blocks.POTTED_DANDELION.defaultBlockState())) return false;
            return !predicate.test(Blocks.FLOWER_POT.defaultBlockState());
        } catch (Throwable t) {
            return false;
        }
    }

    /** 可放入花盆的方块集合（遍历注册表构建一次）。 */
    private static Set<Block> potContents() {
        Set<Block> cached = potContents;
        if (cached != null) return cached;
        Set<Block> set = new HashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof FlowerPotBlock pot) {
                Block content = pot.getContent();
                if (content != Blocks.AIR) set.add(content);
            }
        }
        potContents = set;
        return set;
    }

    /** 结构由网络补齐时给玩家一条提示（节流，避免仪式 tick 刷屏）。 */
    private static void notifyPlayer(Player player) {
        notify(player, "message.beyond_integration.ritual.network_structure",
                "structure blocks supplied by network");
    }

    /** 环境绕过生效时给玩家一条提示（与结构补块共用节流通道）。 */
    public static void notifyEnvBypass(Player player) {
        if (player == null) return;
        notify(player, "message.beyond_integration.ritual.env_bypass",
                "ritual environment bypassed");
    }

    /** 统一提示（action bar，按玩家节流）。 */
    private static void notify(Player player, String key, String debugText) {
        long now = System.currentTimeMillis();
        Long last = LAST_NOTIFY.get(player.getUUID());
        if (last != null && now - last < NOTIFY_INTERVAL_MS) return;
        LAST_NOTIFY.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable(key), true);
        LOGGER.debug("[BI-Ritual] {} ({})", debugText, Objects.toString(player.getName().getString(), "?"));
    }

    /**
     * 输出「缺少清单」（聊天栏，多行；按玩家节流）。
     * <p>每行一条未满足的结构条件：显示需求 / 场地已有 / 网络可用；方块名取自
     * {@link #LAST_NAMES}（查询阶段已顺手收集，不再额外遍历注册表）。
     */
    private static void reportMissing(Player player, List<Missing> missing) {
        long now = System.currentTimeMillis();
        Long last = LAST_MISSING.get(player.getUUID());
        if (last != null && now - last < MISSING_INTERVAL_MS) return;
        LAST_MISSING.put(player.getUUID(), now);

        player.sendSystemMessage(Component.translatable(
                "message.beyond_integration.ritual.structure_missing", missing.size()));
        int rows = Math.min(missing.size(), MISSING_MAX_ROWS);
        for (int i = 0; i < rows; i++) {
            Missing m = missing.get(i);
            int deficit = Math.max(0, m.required() - m.found());
            String name = m.names().isEmpty() ? "?" : String.join(" / ", m.names());
            player.sendSystemMessage(Component.translatable(
                    "message.beyond_integration.ritual.structure_missing.entry",
                    name, deficit, m.found(), m.network()));
        }
        if (missing.size() > rows) {
            player.sendSystemMessage(Component.translatable(
                    "message.beyond_integration.ritual.structure_missing.more", missing.size() - rows));
        }
        LOGGER.debug("[BI-Ritual] reported {} missing structure condition(s) to {}",
                missing.size(), Objects.toString(player.getName().getString(), "?"));
    }
}
