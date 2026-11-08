package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.xp.XpRepairSettings;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import com.wintercogs.beyonddimensions.common.item.XpExchangeItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 经验棒（{@code beyonddimensions:xp_exchange_item}）自动修复装备。
 * <p>物品 tick（{@code XpExchangeItem.inventoryTick}，Mixin 见 {@code XpExchangeItemMixin}）时，
 * 用其绑定网络的经验修复玩家盔甲 4 槽 + 主手 + 副手（仅“经验修补”附魔物品），每 1 XP 修复 2 点耐久。
 * <p>分工：服务端配置决定可用集合（模式开关 / 最高档位 / 修复数值），
 * 经验棒界面按钮决定实际使用（开关 / 模式 / 档位，保存在物品 NBT）。
 */
public final class XpAutoRepairHandler {

    private XpAutoRepairHandler() {}

    /** 物品 tick 入口（客户端/服务端都会调用，内部仅服务端执行）。 */
    public static void onInventoryTick(ItemStack rod, Level level, Player player) {
        if (level.isClientSide() || !(player instanceof ServerPlayer)) return;
        // 配置总开关（是否允许使用该功能）
        if (!CommandConfig.xpAutoRepairEnabled()) return;
        // 界面开关（该经验棒是否实际启用）
        if (!XpRepairSettings.isEnabled(rod)) return;

        CommandConfig.XpRepairAmountMode mode = CommandConfig.xpRepairMode();

        com.solr98.beyondintegration.feature.xp.XpRepairTier tier =
                com.solr98.beyondintegration.feature.xp.XpRepairTiers.byIndex(XpRepairSettings.getTierIndex(rod));
        int interval = tier.interval();
        if (interval > 1 && player.tickCount % interval != 0) return;

        DimensionsNet net = NetedItem.getNet(rod);
        if (net == null) return;

        // 按界面选择的修复范围收集目标（仅带“经验修补”附魔；同一物品去重）
        java.util.Set<ItemStack> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<ItemStack> targets = new ArrayList<>(8);
        if (XpRepairSettings.isArmorEnabled(rod)) {
            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET}) {
                addTarget(targets, seen, player.getItemBySlot(slot));
            }
        }
        if (XpRepairSettings.isInventoryEnabled(rod)) {
            for (ItemStack stack : player.getInventory().items) {
                addTarget(targets, seen, stack);
            }
        }
        if (XpRepairSettings.isHandsEnabled(rod)) {
            addTarget(targets, seen, player.getMainHandItem());
            addTarget(targets, seen, player.getOffhandItem());
        }
        if (XpRepairSettings.isCuriosEnabled(rod)) {
            try {
                top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
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
        if (targets.isEmpty()) return;

        // 百分比/固定值为 0 视为禁用
        int percent = tier.percent();
        int absolute = CommandConfig.xpAutoRepairAbsolute();
        if (mode == CommandConfig.XpRepairAmountMode.PERCENT ? percent <= 0 : absolute <= 0) return;

        // 收集完先统计总需求，一次性提取（批量修复，受网络经验限制）
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
        long units = extractXpUnits(net, (long) totalNeedXp * rate);
        int gainedXp = (int) (units / rate);
        long odd = units % rate;
        if (odd > 0) net.getUnifiedStorage().insert(xpFluidKey(), odd, false);
        if (gainedXp <= 0) return;

        // 按顺序批量修复：每件修复量 = 百分比（最大耐久）或固定值，再受剩余损伤与剩余经验限制
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

        // 未用完的经验回插网络
        if (remainingXp > 0) {
            net.getUnifiedStorage().insert(xpFluidKey(), (long) remainingXp * rate, false);
        }
        net.setDirty();
    }

    /** 是否可修复：有耐久、已损耗、带“经验修补”附魔。 */
    private static boolean needsRepair(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem() || stack.getDamageValue() <= 0) {
            return false;
        }
        // 经验修复附魔：仅“经验修补（Mending）”物品
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MENDING, stack) > 0;
    }

    /** 收集目标（去重，避免物品栏与主手/副手重复）。 */
    private static void addTarget(List<ItemStack> targets, java.util.Set<ItemStack> seen, ItemStack stack) {
        if (needsRepair(stack) && seen.add(stack)) targets.add(stack);
    }

    /** 从网络提取经验流体（先自家 XP 流体，再 #c:experience 其他流体），返回实际提取量（mB）。 */
    public static long extractXpUnits(DimensionsNet net, long wantUnits) {
        if (wantUnits <= 0) return 0;
        var storage = net.getUnifiedStorage();
        long got = storage.extract(xpFluidKey(), wantUnits, false, false).amount();
        if (got >= wantUnits || XpExchangeItem.xpFluids == null) return got;
        for (var fluid : XpExchangeItem.xpFluids) {
            if (got >= wantUnits) break;
            try {
                got += storage.extract(new FluidStackKey(new FluidStack(fluid, 1)), wantUnits - got, false, false).amount();
            } catch (Throwable ignored) {
            }
        }
        return got;
    }

    /** 统一经验流体键（与 BI 其他经验逻辑一致）。 */
    public static FluidStackKey xpFluidKey() {
        return com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler.xpFluidKey();
    }
}
