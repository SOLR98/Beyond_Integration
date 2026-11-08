package com.solr98.beyondintegration.maid;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.xp.XpRepairSettings;
import com.solr98.beyondintegration.handler.XpAutoRepairHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import com.wintercogs.beyonddimensions.common.item.XpExchangeItem;
import com.wintercogs.beyonddimensions.common.item.XpExchangeSettings;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * 经验棒的女仆饰品行为（TLM {@link IMaidBauble}，由女仆饰品槽每 tick 调用）。
 * <p>女仆佩戴的经验棒使用自身绑定网络的经验：
 * <ul>
 *   <li>女仆经验条按 keep 目标等级保持（目标经验 = 等级 × 120，超出存网、不足补足；keep 关闭时全量入网）；</li>
 *   <li>按经验棒上的修复范围修复女仆装备（盔甲 / 背包 / 主副手 / 女仆 Curios / 女仆饰品），
 *       仅经验修补物品（排除 TLM 黑名单），档位 / 间隔取经验棒设置，模式取服务端配置。</li>
 * </ul>
 */
public class XpExchangeBauble implements IMaidBauble {

    /** 女仆每级所需经验（TLM 女仆界面等级条：exp / 120）。 */
    private static final int MAID_XP_PER_LEVEL = 120;

    @Override
    public void onTick(EntityMaid maid, ItemStack rod) {
        if (maid == null || maid.level().isClientSide) return;
        if (!CommandConfig.xpAutoRepairEnabled()) return;
        if (!XpRepairSettings.isEnabled(rod)) return;

        DimensionsNet net = NetedItem.getNet(rod);
        if (net == null) return;

        // 女仆经验 → 经验棒绑定网络：保持目标值（keep 关闭时目标为 0，即全量入网）
        int keepTarget = XpExchangeItem.getOrDefaultXpNetKeepMode(rod, false)
                ? XpExchangeSettings.getTargetLevel(rod) : 0;
        keepMaidXpLevel(maid, net, keepTarget);

        com.solr98.beyondintegration.feature.xp.XpRepairTier tier =
                com.solr98.beyondintegration.feature.xp.XpRepairTiers.byIndex(XpRepairSettings.getTierIndex(rod));
        int interval = tier.interval();
        if (interval > 1 && maid.tickCount % interval != 0) return;

        CommandConfig.XpRepairAmountMode mode = CommandConfig.xpRepairMode();
        int percent = tier.percent();
        int absolute = CommandConfig.xpAutoRepairAbsolute();
        if (mode == CommandConfig.XpRepairAmountMode.PERCENT ? percent <= 0 : absolute <= 0) return;

        List<ItemStack> targets = collectTargets(maid, rod);
        if (targets.isEmpty()) return;

        // 收集完先统计总需求，一次性提取（批量修复）
        int totalNeedXp = 0;
        for (ItemStack stack : targets) {
            int damage = stack.getDamageValue();
            if (damage <= 0) continue;
            int targetRepair = mode == CommandConfig.XpRepairAmountMode.PERCENT
                    ? (int) ((long) stack.getMaxDamage() * percent / 100)
                    : absolute;
            if (targetRepair <= 0) continue;
            totalNeedXp += (Math.min(damage, targetRepair) + 1) / 2;
        }
        if (totalNeedXp <= 0) return;

        int rate = XpExchangeItem.getConversionRate();
        long units = XpAutoRepairHandler.extractXpUnits(net, (long) totalNeedXp * rate);
        int gainedXp = (int) (units / rate);
        long odd = units % rate;
        if (odd > 0) net.getUnifiedStorage().insert(XpAutoRepairHandler.xpFluidKey(), odd, false);
        if (gainedXp <= 0) return;

        int remainingXp = gainedXp;
        for (ItemStack stack : targets) {
            if (remainingXp <= 0) break;
            int damage = stack.getDamageValue();
            if (damage <= 0) continue;
            int targetRepair = mode == CommandConfig.XpRepairAmountMode.PERCENT
                    ? (int) ((long) stack.getMaxDamage() * percent / 100)
                    : absolute;
            if (targetRepair <= 0) continue;
            int maxRepair = Math.min(damage, Math.min(targetRepair, remainingXp * 2));
            int usedXp = (maxRepair + 1) / 2;
            int repair = Math.min(maxRepair, usedXp * 2);
            if (repair <= 0) continue;
            stack.setDamageValue(damage - repair);
            remainingXp -= usedXp;
        }

        if (remainingXp > 0) {
            net.getUnifiedStorage().insert(XpAutoRepairHandler.xpFluidKey(), (long) remainingXp * rate, false);
        }
        net.setDirty();
    }

    /**
     * 把女仆经验条保持到目标等级：超出部分存入网络，不足部分从网络补足。
     * 目标为 {@code 等级 × 120} 经验（与 TLM 女仆界面 {@code exp / 120} 的等级条一致）；
     * 目标等级为 0（keep 关闭）即全量入网。
     */
    private static void keepMaidXpLevel(EntityMaid maid, DimensionsNet net, int targetLevel) {
        int rate = XpExchangeItem.getConversionRate();
        int current = maid.getExperience();
        int target = targetLevel * MAID_XP_PER_LEVEL;

        if (current > target) {
            int excess = current - target;
            KeyAmount remaining = net.getUnifiedStorage().insert(
                    XpAutoRepairHandler.xpFluidKey(), (long) excess * rate, false);
            int notStored = (int) (remaining.amount() / rate);
            int stored = excess - notStored;
            if (stored > 0) {
                maid.setExperience(current - stored);
                net.setDirty();
            }
        } else if (current < target) {
            long units = XpAutoRepairHandler.extractXpUnits(net, (long) (target - current) * rate);
            int gained = (int) (units / rate);
            long odd = units % rate;
            if (odd > 0) net.getUnifiedStorage().insert(XpAutoRepairHandler.xpFluidKey(), odd, false);
            if (gained > 0) {
                maid.setExperience(current + gained);
                net.setDirty();
            }
        }
    }

    /** 按经验棒上的范围开关收集女仆目标（仅经验修补，去重）。 */
    private static List<ItemStack> collectTargets(EntityMaid maid, ItemStack rod) {
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ItemStack> targets = new ArrayList<>(8);

        if (XpRepairSettings.isArmorEnabled(rod)) {
            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                addTarget(targets, seen, maid.getItemBySlot(slot));
            }
        }
        if (XpRepairSettings.isInventoryEnabled(rod)) {
            var inv = maid.getMaidInv();
            for (int i = 0; i < inv.getSlots(); i++) {
                addTarget(targets, seen, inv.getStackInSlot(i));
            }
        }
        if (XpRepairSettings.isHandsEnabled(rod)) {
            addTarget(targets, seen, maid.getMainHandItem());
            addTarget(targets, seen, maid.getOffhandItem());
        }
        if (XpRepairSettings.isCuriosEnabled(rod)) {
            try {
                top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(maid).ifPresent(handler -> {
                    for (var stacksHandler : handler.getCurios().values()) {
                        var stacks = stacksHandler.getStacks();
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            addTarget(targets, seen, stacks.getStackInSlot(i));
                        }
                    }
                });
            } catch (Throwable ignored) {
            }
        }
        if (XpRepairSettings.isMaidEnabled(rod)) {
            try {
                var baubles = maid.getMaidBauble();
                for (int i = 0; i < baubles.getSlots(); i++) {
                    addTarget(targets, seen, baubles.getStackInSlot(i));
                }
            } catch (Throwable ignored) {
            }
        }
        return targets;
    }

    /** 可修复：有耐久、已损耗、带经验修补且不在 TLM 黑名单。 */
    private static void addTarget(List<ItemStack> targets, Set<ItemStack> seen, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem() || stack.getDamageValue() <= 0) return;
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MENDING, stack) <= 0) return;
        try {
            if (stack.is(com.github.tartaricacid.touhoulittlemaid.datagen.tag.TagItem.MAID_MENDING_BLOCKLIST_ITEM)) {
                return;
            }
        } catch (Throwable ignored) {
        }
        if (seen.add(stack)) targets.add(stack);
    }
}
