package com.solr98.beyondintegration.feature.blacklist;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 物品黑名单处理器：作为维度网络"插入前"拦截器，根据配置的
 * 物品黑名单列表拦截被禁物品（如屏障、命令方块等）插入网络。
 * 默认阻止规则：条目匹配注册名（支持省略命名空间，自动补 minecraft:）。
 */
public class ItemBlacklistHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {


    /**
     * 插入前拦截入口：若目标插入的物品命中黑名单，则返回取消标记（true），
     * 否则放行。非物品键、空堆、未启用黑名单时均直接放行。
     */
    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert,
            @NotNull KeyAmount tryInsert,
            com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet net) {

        if (!CommandConfig.enableItemBlacklist()) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        if (!(tryInsert.key() instanceof ItemStackKey itemStackKey)) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        ItemStack itemStack = itemStackKey.getReadOnlyStack();
        if (itemStack.isEmpty()) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        // 取物品注册名（ResourceLocation），用于与黑名单条目比对
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        if (itemId == null) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        // 逐条比对黑名单条目（无命名空间时补全 minecraft: 前缀）
        for (String entry : CommandConfig.itemBlacklist()) {
            if (entry == null || entry.trim().isEmpty()) continue;
            String entryKey = entry.contains(":") ? entry : "minecraft:" + entry;
            if (entryKey.equals(itemId.toString())) {
                return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, true);
            }
        }

        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
    }
}
