package com.solr98.beyondintegration.feature.soul;

import com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity;
import com.Polarice3.Goety.common.blocks.entities.PedestalBlockEntity;
import com.Polarice3.Goety.common.crafting.RitualRecipe;
import com.Polarice3.Goety.common.ritual.Ritual;
import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 网络灵魂源激活仪式（本模组专属仪式类型 {@code beyond_integration:soul_source}）。
 * <p>成功执行配方时，使用<b>仪式激活物品</b>（置于祭坛的便携网络终端）所绑定的网络，
 * 激活该网络的灵魂源（解锁其作为 Goety 魂源）。
 * <p>不消耗激活物品、不产出物品（仅播放完成效果）。
 */
public class SoulSourceRitual extends Ritual {

    public SoulSourceRitual(RitualRecipe recipe) {
        super(recipe);
    }

    @Override
    public boolean identify(Level world, BlockPos darkAltarPos, Player player, ItemStack activationItem) {
        // 前置 3：activation_item 必须是便携终端且已绑定网络
        if (!(activationItem.getItem() instanceof NetedItem)) return false;
        int netId = activationItem.getOrDefault(com.wintercogs.beyonddimensions.common.init.BDDataComponents.NET_ID_DATA, -1);
        if (netId < 0) return false;
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) return false;
        // 前置 2：执行者须为该网络的成员 / 管理员 / 所有者
        if (!(net.isOwner(player) || net.isManager(player) || net.getPlayers().contains(player.getUUID()))) {
            return false;
        }
        // 前置 1：该网络灵魂存储尚未激活，否则阻止仪式并提示
        if (NetworkAmmoData.getOrCreate(netId).isSoulArkActivated()) {
            if (player != null) {
                player.displayClientMessage(
                        Component.translatable("message.beyond_integration.soul_ritual.already_active"), false);
            }
            return false;
        }
        return super.identify(world, darkAltarPos, player, activationItem);
    }

    /**
     * 祭品净消耗：不返还 {@code getCraftingRemainingItem}。否则像 {@code goety:totem_of_souls}
     * 这类带“合成剩余物”的物品会退回（扣魂后的图腾，或枯竭图腾），而非被消耗。
     * <p>本仪式要求配方内所有基座材料（含图腾、方舟等）全部被献祭。
     */
    @Override
    protected boolean consumeAdditionalIngredientFromPedestal(Level world, PedestalBlockEntity pedestal,
                                                              Ingredient ingredient,
                                                              List<ItemStack> consumedIngredients) {
        ItemStack stack = pedestal.itemStackHandler.extractItem(0, 1, true);
        if (!ingredient.test(stack)) return false;
        ItemStack extracted = pedestal.itemStackHandler.extractItem(0, 1, false);
        consumedIngredients.add(extracted);
        pedestal.itemStackHandler.setStackInSlot(0, ItemStack.EMPTY);
        world.playSound(null, pedestal.getBlockPos(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7F, 0.7F);
        return true;
    }

    @Override
    public void finish(Level world, BlockPos darkAltarPos, DarkAltarBlockEntity tileEntity,
                       Player castingPlayer, ItemStack activationItem) {
        // 基类：craftType 钩子（本仪式 craftType 为空 → 无）+ 完成音效
        super.finish(world, darkAltarPos, tileEntity, castingPlayer, activationItem);

        if (!(world instanceof ServerLevel)) return;

        if ((activationItem.getItem() instanceof NetedItem)) {
            int netId = activationItem.getOrDefault(com.wintercogs.beyonddimensions.common.init.BDDataComponents.NET_ID_DATA, -1);
            if (netId >= 0) {
                DimensionsNet net = DimensionsNet.getNetFromId(netId);
                if (net != null) {
                    // 发射信标光柱（2s）后由 SoulRitualAnimation 正式激活
                    SoulRitualAnimation.start((ServerLevel) world, darkAltarPos, net, castingPlayer);
                }
            }
        }
        // 仪式“吃下”激活物品（便携终端被消耗）
        activationItem.shrink(1);
    }
}
