package com.solr98.beyondintegration.feature.enchant;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 网络附魔台的逻辑菜单（普通/原版规则，不含神化）。
 * <p>与 0.7.30 的 {@code DimensionsEnchantMenu} 同一套算法：功率来自配置
 * （固定值 / 网络 XP / 玩家等级，可解限到 100），三档费用与原版一致；
 * 执行附魔时扣<b>网络 XP 流体</b>（点数制，不足时由玩家经验补足）与青金石，
 * 结果直接写回装备槽（原版附魔台无产物槽）。
 * <p>仅作逻辑使用，不作为玩家菜单打开；由 {@link EnchantTableWidget} 嵌入存储界面工作台。
 */
public class EnchantTableMenu extends EnchantmentMenu implements com.wintercogs.beyonddimensions.common.menu.widget.WorkstationWidget.Inputs {

    /** 输入槽（装备）在 {@code slots} 里的下标。 */
    public static final int SLOT_ITEM = 0;
    /** 燃料槽（青金石 / #forge:enchanting_fuels）下标。 */
    public static final int SLOT_FUEL = 1;

    private final net.minecraft.world.entity.player.Player owner;
    private final RandomSource biRandom = RandomSource.create();
    private int refreshTick = 0;
    private Runnable inputsChanged = () -> {
    };
    private boolean fillingFuel = false;

    /** 三档的完整预览附魔列表（服务端计算，供预览同步）。 */
    @SuppressWarnings("unchecked")
    private final List<EnchantmentInstance>[] previews =
            new List[]{new java.util.ArrayList<>(), new java.util.ArrayList<>(), new java.util.ArrayList<>()};

    public EnchantTableMenu(Inventory inventory, ContainerLevelAccess access) {
        super(-1, inventory, access);
        this.owner = inventory.player;
    }

    // ── 输入变化回调（Widget 用） ──

    @Override
    public void onInputsChanged(Runnable listener) {
        this.inputsChanged = listener;
    }

    // ── 功率 / 费用 ──

    private int powerCap() {
        return CommandConfig.enchantUncapPower() ? 100 : 15;
    }

    /** 附魔功率来源（配置）。 */
    public int power() {
        int cap = powerCap();
        return switch (CommandConfig.enchantPowerMode()) {
            case FIXED -> Math.min(cap, Math.max(0, CommandConfig.enchantFixedPower()));
            case NETWORK_XP -> {
                long xp = 0;
                DimensionsNet net = com.solr98.beyondintegration.handler.NetMenuResolver.resolve(owner);
                if (net != null) {
                    xp = net.getUnifiedStorage().extract(
                            EnchantmentBookSeparatorHandler.xpFluidKey(), Long.MAX_VALUE, true, false).amount();
                }
                yield (int) Math.min(cap, xp / Math.max(1, CommandConfig.enchantXpPerPower()));
            }
            case PLAYER_LEVEL -> (int) Math.min(cap, Math.max(0, owner.experienceLevel) / Math.max(1, CommandConfig.enchantLevelPerPower()));
        };
    }

    private static int vanillaCost(RandomSource rnd, int slot, int power, ItemStack stack) {
        int ench = stack.getEnchantmentValue();
        if (ench <= 0) return 0;
        int j = rnd.nextInt(8) + 1 + (power >> 1) + rnd.nextInt(power + 1);
        return switch (slot) {
            case 0 -> Math.max(j / 3, 1);
            case 1 -> j * 2 / 3 + 1;
            default -> Math.max(j, power * 2);
        };
    }

    /** 是否允许“已附魔物品继续附魔”。 */
    private static boolean enchantedAllowed(ItemStack s) {
        return CommandConfig.enchantIgnoreEnchanted() && !EnchantmentHelper.getEnchantments(s).isEmpty();
    }

    /** 是否可作为可附魔物品（原版判定 + 宽松配置）。 */
    public boolean canEnchant(ItemStack s) {
        if (s.isEmpty()) return false;
        if (!s.getItem().isEnchantable(s)) return false;
        if (!s.isEnchanted()) return true;
        return enchantedAllowed(s);
    }

    // ── 重算（槽变化 / 刷新 / 打开时） ──

    @Override
    public void slotsChanged(@NotNull net.minecraft.world.Container container) {
        if (owner.level().isClientSide()) return;
        if (fillingFuel) return;
        ItemStack item = slots.get(SLOT_ITEM).getItem();
        if (canEnchant(item)) {
            recalc(item);
        } else {
            for (int i = 0; i < 3; i++) {
                this.costs[i] = 0;
                this.enchantClue[i] = -1;
                this.levelClue[i] = -1;
                this.previews[i].clear();
            }
        }
        inputsChanged.run();
    }

    /** 服务端重算三档费用与预览附魔。 */
    private void recalc(ItemStack item) {
        int power = power();
        boolean uncapped = CommandConfig.enchantUncapPower() && power > 15;
        this.biRandom.setSeed((long) (this.owner.getEnchantmentSeed() + refreshTick));
        for (int k = 0; k < 3; k++) {
            this.costs[k] = uncapped
                    ? vanillaCost(this.biRandom, k, power, item)
                    : EnchantmentHelper.getEnchantmentCost(this.biRandom, k, power, item);
            this.enchantClue[k] = -1;
            this.levelClue[k] = -1;
            if (this.costs[k] < k + 1) this.costs[k] = 0;
            this.costs[k] = net.minecraftforge.event.ForgeEventFactory.onEnchantmentLevelSet(
                    owner.level(), owner.blockPosition(), k, power, item, this.costs[k]);
        }
        for (int l = 0; l < 3; l++) {
            this.previews[l].clear();
            if (this.costs[l] <= 0) continue;
            List<EnchantmentInstance> list = selectList(item, l, this.costs[l]);
            this.previews[l].addAll(list);
            if (!list.isEmpty()) {
                EnchantmentInstance main = list.get(this.biRandom.nextInt(list.size()));
                this.enchantClue[l] = BuiltInRegistries.ENCHANTMENT.getId(main.enchantment);
                this.levelClue[l] = main.level;
            }
        }
    }

    /** 某档的完整预览附魔列表（服务端；空档返回空列表）。 */
    public List<EnchantmentInstance> preview(int slot) {
        if (slot < 0 || slot >= 3) return List.of();
        return List.copyOf(this.previews[slot]);
    }

    // ── 选魔 ──

    private List<EnchantmentInstance> selectList(ItemStack item, int slot, int cost) {
        this.biRandom.setSeed((long) (this.owner.getEnchantmentSeed() + slot + refreshTick));
        List<EnchantmentInstance> list = enchantedAllowed(item)
                ? selectLoose(item, cost, CommandConfig.enchantAllowTreasure())
                : EnchantmentHelper.selectEnchantment(
                        this.biRandom, item, cost, CommandConfig.enchantAllowTreasure());
        java.util.Set<net.minecraft.world.item.enchantment.Enchantment> filtered =
                CommandConfig.filteredEnchantments();
        if (!filtered.isEmpty() && list != null && !list.isEmpty()) {
            list = new java.util.ArrayList<>(list);
            list.removeIf(ei -> filtered.contains(ei.enchantment));
        }
        return list;
    }

    /** 宽松选魔：已附魔物品可升级/覆盖，冲突由配置决定。 */
    private List<EnchantmentInstance> selectLoose(ItemStack stack, int cost, boolean allowTreasure) {
        List<EnchantmentInstance> chosen = new ArrayList<>();
        int i = stack.getEnchantmentValue();
        if (i <= 0) i = 15;
        int level = cost;
        level += 1 + this.biRandom.nextInt(i / 4 + 1) + this.biRandom.nextInt(i / 4 + 1);
        float f = (this.biRandom.nextFloat() + this.biRandom.nextFloat() - 1.0F) * 0.15F;
        level = Mth.clamp(Math.round(level + level * f), 1, Integer.MAX_VALUE);
        Map<Enchantment, Integer> existing = EnchantmentHelper.getEnchantments(stack);
        boolean ignoreConflict = CommandConfig.enchantIgnoreConflict();
        boolean bookish = stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK);
        List<EnchantmentInstance> pool = new ArrayList<>();
        for (Enchantment ench : BuiltInRegistries.ENCHANTMENT) {
            if ((ench.isTreasureOnly() && !allowTreasure) || !ench.isDiscoverable()) continue;
            boolean apply = ench.canApplyAtEnchantingTable(stack) || (bookish && ench.isAllowedOnBooks());
            if (!apply) continue;
            int cur = existing.getOrDefault(ench, 0);
            if (cur > 0 && ench.getMaxLevel() <= cur) continue;
            if (cur == 0 && !ignoreConflict) {
                boolean conflict = false;
                for (Enchantment ex : existing.keySet()) {
                    if (!ench.isCompatibleWith(ex)) {
                        conflict = true;
                        break;
                    }
                }
                if (conflict) continue;
            }
            for (int lvl = ench.getMaxLevel(); lvl >= ench.getMinLevel(); lvl--) {
                if (lvl <= cur) break;
                if (level >= ench.getMinCost(lvl) && level <= ench.getMaxCost(lvl)) {
                    pool.add(new EnchantmentInstance(ench, lvl));
                    break;
                }
            }
        }
        if (pool.isEmpty()) return chosen;
        net.minecraft.util.random.WeightedRandom.getRandomItem(this.biRandom, pool).ifPresent(chosen::add);
        while (this.biRandom.nextInt(50) <= level && !pool.isEmpty()) {
            EnchantmentInstance last = chosen.get(chosen.size() - 1);
            List<EnchantmentInstance> compatible = new ArrayList<>();
            for (EnchantmentInstance ei : pool) {
                if (ei.enchantment == last.enchantment) continue;
                if (!last.enchantment.isCompatibleWith(ei.enchantment)) continue;
                compatible.add(ei);
            }
            if (compatible.isEmpty()) break;
            net.minecraft.util.random.WeightedRandom.getRandomItem(this.biRandom, compatible)
                    .filter(ei -> chosen.stream().noneMatch(c -> !c.enchantment.isCompatibleWith(ei.enchantment)))
                    .ifPresent(chosen::add);
            if (chosen.size() >= 5) break;
        }
        return chosen;
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
        boolean gateIgnore = CommandConfig.enchantLevelGateIgnore();
        // 提示里的“需求等级”是前置等级：未解除门槛时必须达到
        if (!creative && !gateIgnore
                && (player.experienceLevel < lapisNeed || player.experienceLevel < this.costs[slot])) return false;
        if (player.level().isClientSide()) return true;

        List<EnchantmentInstance> list = selectList(item, slot, this.costs[slot]);
        if (list.isEmpty()) return false;
        if (!creative) {
            // 经验消耗 = 前置等级 与 “前置等级 - 该档固定消耗等级(1/2/3)” 之间的累计经验差，
            // 即等价于扣掉 1/2/3 级所损失的经验点数；解除门槛（levelGateIgnore）时按前置等级整算
            int costLevel = this.costs[slot];
            int remainLevel = gateIgnore ? 0 : Math.max(0, costLevel - lapisNeed);
            int pts = scaleCost((int) Math.max(0L,
                    xpTotalCost(costLevel) - xpTotalCost(remainLevel)));
            if (!chargePoints((ServerPlayer) player, pts)) return false;
            player.onEnchantmentPerformed(item, 0);
        }
        ItemStack enchanted = apply(item, list);
        slots.get(SLOT_ITEM).set(enchanted);
        if (!noLapis && !creative) {
            fuel.shrink(lapisNeed);
            if (fuel.isEmpty()) slots.get(SLOT_FUEL).set(ItemStack.EMPTY);
        }
        player.awardStat(Stats.ENCHANT_ITEM);
        if (player instanceof ServerPlayer sp) {
            net.minecraft.advancements.CriteriaTriggers.ENCHANTED_ITEM.trigger(sp, enchanted, lapisNeed);
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
            if (in.getTag() != null) out.setTag(in.getTag().copy());
        }
        for (EnchantmentInstance ei : list) {
            if (book) EnchantedBookItem.addEnchantment(out, ei);
            else out.enchant(ei.enchantment, ei.level);
        }
        return out;
    }

    // ── 刷新（消耗青金石换一组结果） ──

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
        slotsChanged(slots.get(SLOT_ITEM).container);
        return true;
    }

    // ── 经验点数扣费（网络 XP 优先，玩家补足） ──

    private static int xpNeededForLevel(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }

    private static long xpTotalCost(int level) {
        long sum = 0;
        for (int l = 0; l < level; l++) sum += xpNeededForLevel(l);
        return sum;
    }

    private static int scaleCost(int pts) {
        return Math.max(1, (int) Math.round(pts * CommandConfig.enchantCostPercent() / 100.0));
    }

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
            return false;
        }
        sp.giveExperiencePoints(-(int) Math.min(missingPts, Integer.MAX_VALUE));
        return true;
    }
}
