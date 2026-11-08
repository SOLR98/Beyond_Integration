package com.solr98.beyondintegration.core.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * 1.21.1 物品 NBT 兼容层。
 *
 * <p>1.20.1 的 {@code ItemStack.getTag()/getOrCreateTag()/setTag()} 在 1.21.1 已被移除，
 * 自定义 NBT 统一走 {@code DataComponents.CUSTOM_DATA}（{@link CustomData}）。
 * 本类提供等价的「读一份 / 改后写回」两个入口，方便从 Forge 版代码平移。
 */
public final class ItemNbt {

    private ItemNbt() {}

    /** 读取自定义 NBT 的副本；无数据时返回空 CompoundTag（不会返回 null）。 */
    public static CompoundTag read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new CompoundTag();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    /** 读取自定义 NBT 的副本；无数据时返回 null（对齐 1.20.1 的 {@code getTag()}）。 */
    @Nullable
    public static CompoundTag readOrNull(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    /** 就地修改自定义 NBT（内部写回组件）。 */
    public static void update(ItemStack stack, Consumer<CompoundTag> op) {
        if (stack == null || stack.isEmpty()) return;
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(op));
    }

    /** 整体写入自定义 NBT；{@code tag} 为 null 或空时移除组件。 */
    public static void write(ItemStack stack, @Nullable CompoundTag tag) {
        if (stack == null || stack.isEmpty()) return;
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    /** 读取自定义 NBT 下的子 CompoundTag；不存在返回 null（对齐 {@code getTagElement}）。 */
    @Nullable
    public static CompoundTag readChild(ItemStack stack, String key) {
        CompoundTag tag = readOrNull(stack);
        if (tag == null || !tag.contains(key, 10)) return null;
        return tag.getCompound(key);
    }
}
