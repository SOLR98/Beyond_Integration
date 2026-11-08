package com.solr98.beyondintegration.feature.blacklist;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.InsertPlan;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.InsertRule;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.StorageRequest;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.UnifiedStorageRules;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 物品黑名单插入规则：根据配置的物品黑名单列表拦截被禁物品（如屏障、命令方块等）插入网络。
 * 默认阻止规则：条目匹配注册名（支持省略命名空间，自动补 minecraft:）。
 */
public final class ItemBlacklistHandler implements InsertRule
{
    public static final ResourceLocation ID = new ResourceLocation("beyond_integration", "item_blacklist");

    public static void register()
    {
        UnifiedStorageRules.insertMatching(ID, ItemBlacklistHandler::matches, new ItemBlacklistHandler());
    }

    private static boolean matches(IStackKey<?> key)
    {
        if (!CommandConfig.enableItemBlacklist()) return false;
        if (!(key instanceof ItemStackKey itemKey)) return false;
        ItemStack stack = itemKey.getReadOnlyStack();
        if (stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && blacklisted(id);
    }

    @Override
    public @Nullable InsertPlan plan(StorageRequest request)
    {
        if (!CommandConfig.enableItemBlacklist()) return null;
        if (!(request.key() instanceof ItemStackKey itemKey)) return null;

        ItemStack itemStack = itemKey.getReadOnlyStack();
        if (itemStack.isEmpty()) return null;

        // 取物品注册名（ResourceLocation），用于与黑名单条目比对
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        if (itemId == null || !blacklisted(itemId)) return null;

        // 命中黑名单：整个请求拒收
        return InsertPlan.reject();
    }

    /** 逐条比对黑名单条目（无命名空间时补全 minecraft: 前缀） */
    private static boolean blacklisted(ResourceLocation itemId)
    {
        String full = itemId.toString();
        for (String entry : CommandConfig.itemBlacklist())
        {
            if (entry == null || entry.trim().isEmpty()) continue;
            String entryKey = entry.contains(":") ? entry : "minecraft:" + entry;
            if (entryKey.equals(full)) return true;
        }
        return false;
    }
}
