package com.solr98.beyondintegration.feature.ammo.tacz;

import com.wintercogs.beyonddimensions.api.dimensionnet.rule.InsertPlan;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.InsertRule;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.StorageRequest;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.UnifiedStorageRules;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * TACZ 1.2.0 弹药库方块（{@code tacz:ammo_storage}）插入规则。
 * <p>弹药库方块物品带 {@code BlockEntityTag.Entries}（每条目 = {@code Stack} 样本 + {@code Count} 总数）：
 * 入网时按条目把弹药物品入账网络（每方块一份），空弹药库方块原样入账，避免整库被当作单一方块物品保存。
 * <p>使用注册名判断而非直接引用 TACZ 1.2.0 新类，保证低版本 TACZ（1.1.x，无该方块）不触发类加载。
 */
public final class AmmoStorageExtractHandler implements InsertRule
{
    public static final ResourceLocation ID = new ResourceLocation("beyond_integration", "tacz_ammo_storage");

    /** 方块实体 NBT 键（与 AmmoStorageInventory.save/load 一致）。 */
    private static final String BLOCK_ENTITY_TAG = "BlockEntityTag";
    private static final String ENTRIES_TAG = "Entries";
    private static final String STACK_TAG = "Stack";
    private static final String COUNT_TAG = "Count";
    /** 弹药库方块实体 ID（BlockEntityTag.id，所有容量变体共用）。 */
    private static final String AMMO_STORAGE_BE_ID = "tacz:ammo_storage";

    public static void register()
    {
        UnifiedStorageRules.insertMatching(ID, AmmoStorageExtractHandler::matches, new AmmoStorageExtractHandler());
    }

    private static boolean matches(IStackKey<?> key)
    {
        if (!(key instanceof ItemStackKey itemKey)) return false;
        ItemStack stack = itemKey.getReadOnlyStack();
        return !stack.isEmpty() && isAmmoStorage(stack);
    }

    @Override
    public @Nullable InsertPlan plan(StorageRequest request)
    {
        if (request.net() == null) return null;
        if (!(request.key() instanceof ItemStackKey itemKey)) return null;

        ItemStack stack = itemKey.copyStackWithCount(1);
        if (stack.isEmpty() || !isAmmoStorage(stack)) return null;

        CompoundTag blockEntityTag = stack.getTagElement(BLOCK_ENTITY_TAG);
        if (blockEntityTag == null)
        {
            // 空弹药库（无方块实体数据）：按普通方块物品放行
            return null;
        }

        ListTag entries = blockEntityTag.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
        if (entries.isEmpty()) return null;

        InsertPlan plan = InsertPlan.convert(1);

        // 按条目把弹药入网：每个方块物品带一份条目总数
        for (int i = 0; i < entries.size(); i++)
        {
            CompoundTag entry = entries.getCompound(i);
            ItemStack sample = ItemStack.of(entry.getCompound(STACK_TAG));
            long count = entry.getLong(COUNT_TAG);
            if (sample.isEmpty() || count <= 0L) continue;
            plan.add(new ItemStackKey(sample.copyWithCount(1)), count);
        }

        // 空弹药库方块物品入网（移除方块实体数据）
        ItemStack emptyBlock = stack.copyWithCount(1);
        emptyBlock.removeTagKey(BLOCK_ENTITY_TAG);
        plan.add(new ItemStackKey(emptyBlock), 1);

        return plan;
    }

    /** 是否为 TACZ 弹药库方块物品（按注册名/方块实体 ID 判断，避免低版本 TACZ 的类加载）。 */
    private static boolean isAmmoStorage(ItemStack stack)
    {
        // 物品注册名：tacz:ammo_storage / tacz:ammo_storage_gold / tacz:ammo_storage_diamond
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null && "tacz".equals(id.getNamespace()) && id.getPath().startsWith("ammo_storage"))
        {
            return true;
        }
        // 退路：BlockEntityTag.id 为 tacz:ammo_storage（覆盖变体物品）
        CompoundTag blockEntityTag = stack.getTagElement(BLOCK_ENTITY_TAG);
        return blockEntityTag != null
                && blockEntityTag.contains("id", Tag.TAG_STRING)
                && AMMO_STORAGE_BE_ID.equals(blockEntityTag.getString("id"));
    }
}
