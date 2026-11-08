package com.solr98.beyondintegration.feature.enchant;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentMenu;
import dev.shadowsoffire.apothic_enchanting.table.infusion.InfusionRecipe;
import dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentHelper;
import dev.shadowsoffire.apothic_enchanting.util.MiscUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 网络附魔台的逻辑菜单（<b>神化/Apothic 规则</b>）。
 * <p>与普通版 {@link EnchantTableMenu} 的区别：
 * <ul>
 *   <li>三个可调参数 <b>eterna / quanta / arcana</b>（0~100），由工作台页面调整；</li>
 *   <li>费用用 {@link ApothEnchantmentHelper#getEnchantmentCost}（以 eterna 为功率），
 *       候选用 {@link ApothEnchantmentHelper#selectEnchantment}（quanta / arcana 参与分布）；</li>
 *   <li>先匹配神化的附魔台配方 {@link EnchantingRecipe#findMatch}（特殊产出），否则按原版规则附魔；</li>
 *   <li>经验费用用 Apothic 点数制 {@link MiscUtil#getExpCostForSlot}：该档消耗 cost、cost-1、… 共 1/2/3 个单级经验，扣<b>网络 XP 流体</b>
 *       （不足时玩家经验补足），燃料/青金石照原版。</li>
 * </ul>
 * 仅作逻辑使用，由 {@link ApothEnchantTableWidget} 嵌入存储界面工作台。
 */
public class ApothEnchantTableMenu extends EnchantmentMenu {

    public static final int SLOT_ITEM = 0;
    public static final int SLOT_FUEL = 1;

    private final Player owner;
    private final RandomSource biRandom = RandomSource.create();
    private int refreshTick = 0;

    /** 三档的完整预览附魔列表（服务端计算，供预览同步）。 */
    @SuppressWarnings("unchecked")
    private final List<EnchantmentInstance>[] previews =
            new List[]{new java.util.ArrayList<>(), new java.util.ArrayList<>(), new java.util.ArrayList<>()};


    /** 四个可调参数（0~100）。 */
    private int eterna = 30;
    private int quanta = 0;
    private int arcana = 0;
    /**
     * 稳定（1.21.1 的 {@code EnchantmentTableStats.stable}）。
     * <p>旧版用 rectification 数值表达「校准」；1.21.1 改为布尔：稳定时不再滚出负向波动。
     */
    private boolean stable = false;

    /** 已献祭（存入网络）书架提供的属性上限（0~100；quanta 可为负）。 */
    private int maxEterna = 0;
    private int maxQuanta = 0;
    private int maxArcana = 0;
    /** 网络是否已有「提供稳定」的书架被献祭（布尔上限：有即稳定）。 */
    private boolean maxStable = false;
    /** quanta 的负向下限（=-配置硬上限），让负贡献书架能抵消正向 quanta。 */
    private int minQuanta = 0;

    public ApothEnchantTableMenu(Inventory inventory, ContainerLevelAccess access) {
        super(-1, inventory, access);
        this.owner = inventory.player;
    }

    public int eterna() {
        return eterna;
    }

    public int quanta() {
        return quanta;
    }

    public int arcana() {
        return arcana;
    }

    public boolean stable() {
        return stable;
    }

    public int maxEterna() {
        return maxEterna;
    }

    public int maxQuanta() {
        return maxQuanta;
    }

    public int maxArcana() {
        return maxArcana;
    }

    public boolean maxStable() {
        return maxStable;
    }

    /** 设置参数并重算（服务端动作调用）。 */
    public void setStats(int eterna, int quanta, int arcana, boolean stable) {
        updateShelfLimits();
        this.eterna = Math.max(0, Math.min(maxEterna, eterna));
        // quanta 允许负值（负贡献书架削减波动），下界由 minQuanta 约束
        this.quanta = Math.max(minQuanta, Math.min(maxQuanta, quanta));
        this.arcana = Math.max(0, Math.min(maxArcana, arcana));
        this.stable = maxStable && stable;
        if (!owner.level().isClientSide()) {
            recalc();
        }
    }

    /**
     * 从网络存档读取“参数刷新”扫描到的书架属性上限（{@link ApothStatData}），
     * 再分别受四项配置硬上限约束。
     * <p>关闭“需要献祭”时不再读取网络存档，上限直接取配置硬上限（默认即全开）。
     */
    private void updateShelfLimits() {
        int capEterna = CommandConfig.enchantApothStatHardCapEterna();
        int capQuanta = CommandConfig.enchantApothStatHardCapQuanta();
        int capArcana = CommandConfig.enchantApothStatHardCapArcana();
        // 第 4 位不再是 rectification 数值，而是「稳定」标记（0/1）：1.21.1 用 providesStability()
        int[] v = new int[]{0, 0, 0, 0};
        if (CommandConfig.enchantApothRequireSacrifice()) {
            if (owner instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet net =
                        com.solr98.beyondintegration.handler.NetMenuResolver.resolve(sp);
                if (net != null) v = ApothStatData.get(net.getId());
            }
        } else {
            v = new int[]{capEterna, capQuanta, capArcana, 1}; // 不要求献祭：视为已具备稳定
        }
        maxEterna = Math.min(v[0], capEterna);
        // quanta 双向范围：v[1] 为负时得到负向下限（如 geode_shelf 的 -15），为正时得到正向上限
        maxQuanta = Math.min(Math.max(v[1], 0), capQuanta);
        minQuanta = Math.max(Math.min(v[1], 0), -capQuanta);
        // 只要网络里献祭过「提供稳定」的书架，就具备稳定能力（OR 语义）；受配置开关约束
        maxStable = CommandConfig.enchantApothShelfStability() && v[3] > 0;
    }

    /** 是否必须通过献祭书架来提升属性上限（关闭时直接按配置上限）。 */
    public boolean requireSacrifice() {
        return CommandConfig.enchantApothRequireSacrifice();
    }

    /** 重新读取上限并把当前参数夹到新上限（服务端）。 */
    public void refreshLimits() {
        if (!owner.level().isClientSide()) {
            recalc();
        }
    }

    public boolean canEnchant(ItemStack s) {
        if (s.isEmpty()) return false;
        if (!s.getItem().isEnchantable(s)) return false;
        if (!s.isEnchanted()) return true;
        return CommandConfig.enchantIgnoreEnchanted() && !EnchantmentHelper.getEnchantmentsForCrafting(s).isEmpty();
    }

    @Override
    public void slotsChanged(@NotNull net.minecraft.world.Container container) {
        if (owner.level().isClientSide()) return;
        recalc();
    }

    /** 按 eterna/quanta/arcana 重算三档费用与预览附魔。 */
    private void recalc() {
        updateShelfLimits();
        this.eterna = Math.min(this.eterna, maxEterna);
        this.quanta = Math.max(minQuanta, Math.min(this.quanta, maxQuanta));
        this.arcana = Math.min(this.arcana, maxArcana);
        this.stable = this.stable && maxStable;
        ItemStack item = slots.get(SLOT_ITEM).getItem();
        if (!canEnchant(item)) {
            for (int i = 0; i < 3; i++) {
                this.costs[i] = 0;
                this.enchantClue[i] = -1;
                this.levelClue[i] = -1;
                this.previews[i].clear();
            }
            return;
        }
        this.biRandom.setSeed((long) (this.owner.getEnchantmentSeed() + refreshTick));
        for (int slot = 0; slot < 3; slot++) {
            int cost = ApothEnchantmentHelper.getEnchantmentCost(this.biRandom, slot, eterna, item);
            if (cost < slot + 1) cost = 0;
            this.costs[slot] = cost;
            this.enchantClue[slot] = -1;
            this.levelClue[slot] = -1;
        }
        for (int slot = 0; slot < 3; slot++) {
            this.previews[slot].clear();
            if (this.costs[slot] <= 0) continue;
            List<EnchantmentInstance> list = selectList(item, this.costs[slot]);
            this.previews[slot].addAll(list);
            if (!list.isEmpty()) {
                EnchantmentInstance main = list.get(this.biRandom.nextInt(list.size()));
                this.enchantClue[slot] = EnchantRegistryCompat.idOf(main.enchantment);
                this.levelClue[slot] = main.level;
            }
        }
    }

    /** 某档的完整预览附魔列表（服务端；空档返回空列表）。 */
    public List<EnchantmentInstance> preview(int slot) {
        if (slot < 0 || slot >= 3) return List.of();
        return List.copyOf(this.previews[slot]);
    }

    /** 预览版本号（刷新后变化，供客户端判断是否需要重新拉取）。 */
    public int refreshTick() {
        return refreshTick;
    }

    private List<EnchantmentInstance> selectList(ItemStack item, int cost) {
        var reg = EnchantRegistryCompat.registry();
        if (reg == null) return List.of();
        var stats = new dev.shadowsoffire.apothic_enchanting.table.EnchantmentTableStats(
                this.eterna, this.quanta, this.arcana, 1, apothFiltered(),
                apothAllowsTreasure(), this.stable);
        return ApothEnchantmentHelper.selectEnchantment(this.biRandom, item, cost, stats, reg.asLookup());
    }

    /** 宝藏附魔：全局配置开启，或本网络献祭过允许宝藏的书架。 */
    private boolean apothAllowsTreasure() {
        if (CommandConfig.enchantAllowTreasure()) return true;
        if (owner instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet net =
                    com.solr98.beyondintegration.handler.NetMenuResolver.resolve(sp);
            if (net != null) return ApothStatData.allowsTreasure(net.getId());
        }
        return false;
    }

    private java.util.Set<Holder<Enchantment>> apothFiltered() {
        java.util.Set<Holder<Enchantment>> out = new java.util.HashSet<>(CommandConfig.filteredEnchantments());
        if (owner instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet dimNet =
                    com.solr98.beyondintegration.handler.NetMenuResolver.resolve(sp);
            if (dimNet != null && ApothStatData.filterEnabled(dimNet.getId())) {
                for (String id : ApothStatData.filtered(dimNet.getId())) {
                    net.minecraft.resources.ResourceLocation rl =
                            net.minecraft.resources.ResourceLocation.tryParse(id);
                    if (rl == null) continue;
                    Holder<Enchantment> e = EnchantRegistryCompat.byKey(rl);
                    if (e != null) out.add(e);
                }
            }
        }
        return out;
    }

    // ── 执行附魔 ──

    @Override
    public boolean clickMenuButton(@NotNull Player player, int slot) {
        if (slot < 0 || slot >= 3) return false;
        ItemStack item = slots.get(SLOT_ITEM).getItem();
        int lapisNeed = slot + 1;
        boolean noLapis = CommandConfig.enchantNoLapis();
        boolean creative = player.getAbilities().instabuild;
        if (!noLapis && !creative && CommandConfig.enchantAutoLapis()) {
            EnchantLapisHelper.ensureLapis(player, slots.get(SLOT_FUEL), lapisNeed);
        }
        ItemStack fuel = slots.get(SLOT_FUEL).getItem();
        if (!noLapis && !creative && (fuel.isEmpty() || fuel.getCount() < lapisNeed)) return false;
        if (this.costs[slot] <= 0 || item.isEmpty()) return false;
        if (player.level().isClientSide()) return true;

        List<EnchantmentInstance> list = selectList(item, this.costs[slot]);
        if (list.isEmpty()) return false;

        // Apothic 点数制：该档消耗 cost、cost-1、…、cost-slot 共 1/2/3 个单级经验（与神化原生附魔台一致）
        int points = Math.max(0, MiscUtil.getExpCostForSlot(this.costs[slot], slot));
        points = Math.max(1, (int) Math.round(points * CommandConfig.enchantCostPercent() / 100.0));
        if (!creative && !chargePoints((ServerPlayer) player, points)) return false;


        ItemStack result = apply(item, list);
        slots.get(SLOT_ITEM).set(result);
        if (!noLapis && !creative) {
            fuel.shrink(lapisNeed);
            if (fuel.isEmpty()) slots.get(SLOT_FUEL).set(ItemStack.EMPTY);
        }
        player.awardStat(Stats.ENCHANT_ITEM);
        if (player instanceof ServerPlayer sp) {
            net.minecraft.advancements.CriteriaTriggers.ENCHANTED_ITEM.trigger(sp, result, lapisNeed);
        }
        slots.get(SLOT_ITEM).setChanged();
        slots.get(SLOT_FUEL).setChanged();
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.BLOCKS, 1.0F, player.level().random.nextFloat() * 0.1F + 0.9F);
        return true;
    }

    private static ItemStack apply(ItemStack in, List<EnchantmentInstance> list) {
        ItemStack out = in;
        boolean book = in.is(Items.BOOK);
        if (book) {
            out = new ItemStack(Items.ENCHANTED_BOOK);
            var nbt = com.solr98.beyondintegration.core.util.ItemNbt.readOrNull(in);
            if (nbt != null) com.solr98.beyondintegration.core.util.ItemNbt.write(out, nbt.copy());
            ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            for (EnchantmentInstance ei : list) mut.set(ei.enchantment, ei.level);
            out.set(DataComponents.STORED_ENCHANTMENTS, mut.toImmutable());
        } else {
            ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(
                    EnchantmentHelper.getEnchantmentsForCrafting(out));
            for (EnchantmentInstance ei : list) mut.set(ei.enchantment, ei.level);
            EnchantmentHelper.setEnchantments(out, mut.toImmutable());
        }
        return out;
    }

    // ── 刷新 ──

    public boolean refreshEnabled() {
        return CommandConfig.enchantRefreshEnabled();
    }

    public boolean doRefresh(ServerPlayer sp) {
        if (sp.level().isClientSide() || !refreshEnabled()) return false;
        boolean noLapis = CommandConfig.enchantNoLapis();
        boolean creative = sp.getAbilities().instabuild;
        int need = CommandConfig.enchantRefreshLapis();
        if (!noLapis && !creative && need > 0 && CommandConfig.enchantAutoLapis()) {
            EnchantLapisHelper.ensureLapis(sp, slots.get(SLOT_FUEL), need);
        }
        ItemStack fuel = slots.get(SLOT_FUEL).getItem();
        if (!noLapis && !creative && need > 0 && (fuel.isEmpty() || fuel.getCount() < need)) return false;
        if (!noLapis && !creative && need > 0) {
            fuel.shrink(need);
            if (fuel.isEmpty()) slots.get(SLOT_FUEL).set(ItemStack.EMPTY);
        }
        refreshTick++;
        recalc();
        return true;
    }

    // ── 经验点数扣费（网络 XP 优先，玩家补足） ──

    private static boolean chargePoints(ServerPlayer sp, int pts) {

        if (pts <= 0) return true;
        long mb = (long) pts * 20L;
        long got = 0;
        DimensionsNet net = com.solr98.beyondintegration.handler.NetMenuResolver.resolve(sp);
        FluidStackKey xpKey = EnchantmentBookSeparatorHandler.xpFluidKey();
        if (net != null) got = net.getUnifiedStorage().extract(xpKey, mb, false, false).amount();
        long missingPts = Math.max(0, (mb - got) / 20L);
        if (missingPts == 0) return true;
        if (sp.totalExperience < missingPts) {
            if (net != null) net.getUnifiedStorage().insert(xpKey, got, false);
            sp.sendSystemMessage(Component.translatable(
                    "gui.beyond_integration.enchant.need_xp_points", pts));
            return false;
        }
        sp.giveExperiencePoints(-(int) Math.min(missingPts, Integer.MAX_VALUE));
        return true;
    }
}
