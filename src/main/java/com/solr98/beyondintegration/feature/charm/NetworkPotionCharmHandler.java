package com.solr98.beyondintegration.feature.charm;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.core.subscribe.BdSubscriptionHub;
import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler;
import com.solr98.beyondintegration.handler.PotionCharmAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网络药水护符：把神化（Apotheosis）药水护符的效果经由维度网络，作用于"将网络设为主网络"的在线玩家。
 *
 * <p>规则：
 * <ul>
 *   <li>只有"经验修补（Mending）"或"无法破坏（Unbreakable）"的护符才能在网络中生效；
 *       网络内若出现"单条附魔=经验修补"的附魔书，则将其<b>献祭</b>（消耗该书）并网络级持久化解锁，
 *       此后视为该网络内所有药水护符都拥有经验修补。</li>
 *   <li>无法破坏：不消耗。经验修补（含献祭解锁）：每次施加消耗网络 XP 流体
 *       （默认原版比例 2 耐久=1 XP，1 XP=20 mB，倍率可配）；XP 不足时本次不施加。</li>
 *   <li>网络内所有启用的护符都会各自生效（按效果去重）。</li>
 * </ul>
 *
 * <p><b>性能</b>：不每次结算全量扫描存储。按网络去重（同一网络的多个玩家只处理一次），
 * 并把护符状态（效果/增幅/时长/是否无法破坏/是否自身经验修补）与经验修补书记录缓存于
 * {@link NetRecord}；通过 {@link BdSubscriptionHub} 订阅该网络的存储 delta，仅当
 * "护符/附魔书"相关物品发生增删时才将缓存标记为脏、下次结算时重扫。
 */
public final class NetworkPotionCharmHandler {

    private static final String APOTHEOSIS = "apotheosis";

    /** 网络 ID → 护符记录缓存（含订阅句柄） */
    private static final Map<Integer, NetRecord> CACHE = new ConcurrentHashMap<>();

    private NetworkPotionCharmHandler() {}

    /** 服务端 tick：按配置间隔为"主网络"玩家施加网络内药水护符效果。 */
    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (!ModList.get().isLoaded(APOTHEOSIS)) return;
        if (!CommandConfig.potionCharmEnabled()) return;
        int interval = CommandConfig.potionCharmInterval();
        if (interval <= 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.tickCount % interval != 0) continue;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) continue;
            if (!PotionCharmMode.of(getMode(net)).affectsPlayers()) continue;
            applyToEntity(net, player);
        }
    }

    /**
     * 对单个实体施加该网络的护符效果。
     * <p>玩家由 {@link #tick} 调用；女仆等绑定网络的实体由 {@code MaidPotionCharmHandler}
     * （TLM 加载时）经 {@code MaidNetworkHelper.findTerminal} 解析其网络后调用。
     * 护符记录按网络缓存，仅在护符相关物品变动时重扫，因此多实体共用同一网络时开销很小。
     *
     * @return 是否对该实体执行了结算（未装神化/总开关关闭/网络关闭/无护符时为 {@code false}）
     */
    public static boolean applyToEntity(DimensionsNet net, LivingEntity target) {
        if (net == null || target == null) return false;
        if (!ModList.get().isLoaded(APOTHEOSIS)) return false;
        if (!CommandConfig.potionCharmEnabled()) return false;

        NetRecord record = recordFor(net);
        if (record.charms.isEmpty()) return false;

        boolean mendingUnlocked = NetworkAmmoData.getOrCreate(net.getId()).isPotionCharmMending();
        UnifiedStorage storage = net.getUnifiedStorage();
        long[] xpPool = { storage.getStackByKey(EnchantmentBookSeparatorHandler.xpFluidKey()).amount() };

        // record.charms 已在"标记/扫描"时按效果去重保留最高等级；此处仅按当前解锁状态过滤可生效者后施加
        for (CharmEffect charm : record.charms) {
            if (!isEligible(charm, mendingUnlocked)) continue;
            applyCharm(target, net, storage, charm, mendingUnlocked, xpPool);
        }
        return true;
    }

    /** 该护符在当前网络是否可生效：无法破坏 / 自身经验修补 / 已献祭解锁。 */
    private static boolean isEligible(CharmEffect charm, boolean mendingUnlocked) {
        return charm.unbreakable() || charm.ownMending() || mendingUnlocked;
    }

    /** 同一效果下是否为更优：先比等级；同级优先"无法破坏"（不消耗），其次"经验修补"。 */
    private static boolean isBetter(CharmEffect candidate, CharmEffect current) {
        if (candidate.amplifier() != current.amplifier()) return candidate.amplifier() > current.amplifier();
        return potency(candidate) > potency(current);
    }

    private static int potency(CharmEffect charm) {
        return (charm.unbreakable() ? 2 : 0) + (charm.ownMending() ? 1 : 0);
    }

    /**
     * 献祭（由按钮/包触发）：消耗网络内一本"单条附魔=经验修补"的附魔书，持久化解锁该网络的经验修补。
     *
     * @return 已解锁或本次献祭成功返回 {@code true}；网络中找不到合格的书返回 {@code false}
     */
    public static boolean sacrificeMending(DimensionsNet net) {
        if (net == null) return false;
        NetworkAmmoData.Attachment data = NetworkAmmoData.getOrCreate(net.getId());
        if (data.isPotionCharmMending()) return true;

        ItemStackKey bookKey = null;
        for (KeyAmount ka : net.getUnifiedStorage().getStorage()) {
            if (!(ka.key() instanceof ItemStackKey itemKey)) continue;
            ItemStack stack = itemKey.getReadOnlyStack();
            if (stack == null || stack.isEmpty()) continue;
            if (isSingleMendingBook(stack)) {
                bookKey = itemKey;
                break;
            }
        }
        if (bookKey == null) return false;

        net.getUnifiedStorage().extract(bookKey, 1L, false, false);
        data.setPotionCharmMending(true);
        NetworkAmmoData.markDirty();
        net.setDirty();
        return true;
    }

    // ───────────────────────── 缓存 / 扫描 ─────────────────────────

    /** 取得该网络的护符记录（首次或脏时扫描一次；首次会订阅存储变更使缓存失效）。 */
    private static NetRecord recordFor(DimensionsNet net) {
        int id = net.getId();
        NetRecord record = CACHE.computeIfAbsent(id, k -> {
            NetRecord r = new NetRecord();
            // 订阅方仅捕获 netId，避免闭包强引用网络对象导致订阅随对象存活
            r.subscription = BdSubscriptionHub.subscribe(net, r, (key, size, insert) -> {
                if (isRelevantDelta(key)) markDirty(id);
            });
            return r;
        });
        if (record.dirty) {
            scan(net, record);
        }
        return record;
    }

    /**
     * 全量扫描一次存储，解析护符并在<b>标记阶段</b>按效果去重：
     * 同一效果只保留最高等级（同级优先"无法破坏"，其次"经验修补"），避免低等级重复施加/重复扣费。
     */
    private static void scan(DimensionsNet net, NetRecord record) {
        Map<MobEffect, CharmEffect> best = new HashMap<>();
        for (KeyAmount ka : net.getUnifiedStorage().getStorage()) {
            if (!(ka.key() instanceof ItemStackKey itemKey)) continue;
            ItemStack stack = itemKey.getReadOnlyStack();
            if (stack == null || stack.isEmpty()) continue;
            if (ApothCharmSupport.isPotionCharm(stack)
                    && ApothCharmSupport.hasEffect(stack) && ApothCharmSupport.isEnabled(stack)) {
                MobEffectInstance inst = ApothCharmSupport.getEffect(stack);
                if (inst != null) {
                    CharmEffect candidate = new CharmEffect(inst.getEffect(), inst.getAmplifier(), inst.getDuration(),
                            isUnbreakable(stack), hasMending(stack));
                    CharmEffect prev = best.get(candidate.effect());
                    if (prev == null || isBetter(candidate, prev)) {
                        best.put(candidate.effect(), candidate);
                    }
                }
            }
        }
        record.charms = new ArrayList<>(best.values());
        record.dirty = false;
    }

    /** 该 delta 是否与护符/附魔书相关（XP 流体等无关变更不触发重扫）。 */
    private static boolean isRelevantDelta(IStackKey<?> key) {
        if (!(key instanceof ItemStackKey itemKey)) return false;
        ItemStack stack = itemKey.getReadOnlyStack();
        if (stack == null || stack.isEmpty()) return false;
        return ApothCharmSupport.isPotionCharm(stack);
    }

    private static void markDirty(int netId) {
        NetRecord record = CACHE.get(netId);
        if (record != null) record.dirty = true;
    }

    /** 网络销毁：移除该网络的记录与订阅（幂等）。 */
    public static void onNetDestroyed(int netId) {
        NetRecord record = CACHE.remove(netId);
        if (record != null) record.close();
    }

    /** 服务器停止：清理全部记录与订阅。 */
    public static void clearCaches() {
        for (NetRecord record : CACHE.values()) {
            record.close();
        }
        CACHE.clear();
    }

    // ───────────────────────── 施加 ─────────────────────────

    private static void applyCharm(LivingEntity target, DimensionsNet net, UnifiedStorage storage,
                                   CharmEffect charm, boolean mendingUnlocked, long[] xpPool) {
        boolean unbreakable = charm.unbreakable();
        boolean mending = unbreakable || charm.ownMending() || mendingUnlocked;
        if (!mending) return; // 门槛：Unbreakable / 自身经验修补 / 已献祭解锁

        MobEffect effect = charm.effect();
        // 到"消失前 lead tick"内（或已消失）才重新给予，可配置，默认 10
        int lead = CommandConfig.potionCharmRefreshLeadTicks();
        MobEffectInstance active = target.getEffect(effect);
        if (active != null && active.getDuration() > lead) return;

        // Mending（非无法破坏）：预检网络 XP，不足则本次不施加
        long costMb = 0;
        if (!unbreakable) {
            costMb = mendingCostMb();
            if (xpPool[0] < costMb) return;
        }

        int durationOffset = lead;
        if (effect == MobEffects.REGENERATION) {
            durationOffset += 50 >> charm.amplifier();
        }
        MobEffectInstance newEffect = new MobEffectInstance(effect,
                (int) Math.ceil(charm.duration() / 24D) + durationOffset,
                charm.amplifier(), false, false);
        target.addEffect(newEffect);

        if (costMb > 0) {
            storage.extract(EnchantmentBookSeparatorHandler.xpFluidKey(), costMb, false, false);
            xpPool[0] -= costMb;
            net.setDirty();
        }
    }

    /** Mending 费用（mB XP 流体）：每次施加固定消耗配置的经验点数（1 XP = 20 mB）。 */
    private static long mendingCostMb() {
        double xp = Math.max(0.0, CommandConfig.potionCharmMendingXpCost());
        return Math.max(0L, Math.round(xp * 20.0));
    }

    // ───────────────────────── 判定 ─────────────────────────

    /** 读取网络药水护符生效目标（无访问器默认 0=仅玩家）。 */
    public static int getMode(DimensionsNet net) {
        return net instanceof PotionCharmAccessor acc ? acc.beyond$getPotionCharmMode() : 0;
    }

    /** 是否为"单条附魔=经验修补"的附魔书（书内恰好只有 Mending 一条附魔）。 */
    private static boolean isSingleMendingBook(ItemStack stack) {
        if (!(stack.getItem() instanceof EnchantedBookItem)) return false;
        Map<Enchantment, Integer> ench = EnchantmentHelper.getEnchantments(stack);
        return ench.size() == 1 && ench.containsKey(Enchantments.MENDING);
    }

    private static boolean hasMending(ItemStack stack) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MENDING, stack) > 0;
    }

    private static boolean isUnbreakable(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean("Unbreakable");
    }

    // ───────────────────────── 内部类型 ─────────────────────────

    /** 解析后的护符效果记录（不含物品引用，便于长期缓存与去重）。 */
    private record CharmEffect(MobEffect effect, int amplifier, int duration, boolean unbreakable, boolean ownMending) {}

    /** 单个网络的护符记录缓存。 */
    private static final class NetRecord {
        volatile boolean dirty = true;
        volatile List<CharmEffect> charms = List.of();
        AutoCloseable subscription;

        void close() {
            if (subscription != null) {
                try { subscription.close(); } catch (Exception ignored) {}
                subscription = null;
            }
        }
    }
}
