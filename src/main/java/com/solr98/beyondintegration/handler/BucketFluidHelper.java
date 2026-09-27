package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * 网络桶装流体通用工具：从网络存储分别取出流体与空桶，组合成装满流体的桶。
 * <p>
 * 供 BD 终端槽位点击（{@code BdBucketFluidSlotMixin}）及其他需要"用网络流体与空桶组装桶"的场景复用；
 * 数量受网络流体（每桶 1000 mB）、空桶存量与桶物品最大堆叠共同限制，实际扣除以取出结果为准，
 * 异常差额会回插网络，不产生资源丢失。
 */
public final class BucketFluidHelper {

    /** 一桶对应的流体量（mB） */
    public static final long MB_PER_BUCKET = 1000L;

    private BucketFluidHelper() {}

    /** 该流体是否有对应桶物品（无则无法组装） */
    public static boolean hasBucketItem(FluidStackKey fluidKey) {
        return fluidKey != null && fluidKey.getSource().getBucket() != Items.AIR;
    }

    /**
     * 该容器物品能否由网络组装（宽松语义：仅要求网络流体 ≥ 每份所需；
     * 空容器可选——有则一并消耗，没有也允许，容器物品按流体量生成）。
     */
    public static boolean canCraftFromNetwork(IStackHandler storage, ItemStack template) {
        return countSubstitutable(storage, template) > 0L;
    }

    /**
     * 从网络消耗流体组装成指定的容器物品（如各类桶；供 JEI/工作站合成填充等需要实体物品的场景）。
     * <p>
     * 宽松语义：流体足够即可组装；网络若有对应空容器则一并扣除（最多实际组装数量，有多少扣多少），
     * 没有空容器也允许（仅消耗流体）。
     *
     * @param storage  网络统一存储
     * @param template 目标容器物品（含流体；空容器取其合成剩余物，原版桶回退空桶）
     * @param count    期望数量（上限受网络流体存量与物品最大堆叠限制）
     * @return 组装出的容器物品（不可用或流体不足时返回 {@link ItemStack#EMPTY}）
     */
    public static ItemStack craftContainer(IStackHandler storage, ItemStack template, int count) {
        if (storage == null || template == null || template.isEmpty() || count <= 0) return ItemStack.EMPTY;
        FluidStack fluid = getContainedFluid(storage, template);
        if (fluid.isEmpty() || fluid.getAmount() <= 0) return ItemStack.EMPTY;

        long perCraft = fluid.getAmount();
        FluidStackKey fluidKey = new FluidStackKey(fluid.copyWithAmount(1));
        long fluidAvailable = storage.getStackByKey(fluidKey).amount() / perCraft;
        long maxStack = template.getMaxStackSize();
        long make = Math.min(fluidAvailable, Math.min(count, maxStack));
        if (make <= 0L) return ItemStack.EMPTY;

        // 先取流体（空容器可选，随后按实际组装数量尽量扣除）
        long gotFluid = storage.extract(fluidKey, make * perCraft, false, false).amount();
        long actual = gotFluid / perCraft;
        long leftoverFluid = gotFluid - actual * perCraft;
        if (leftoverFluid > 0L) storage.insert(fluidKey, leftoverFluid, false);
        if (actual > 0L) {
            ItemStack empty = emptyContainerOf(template);
            if (!empty.isEmpty()) {
                ItemStackKey emptyKey = new ItemStackKey(empty.copyWithCount(1));
                long takeEmpty = Math.min(actual, storage.getStackByKey(emptyKey).amount());
                if (takeEmpty > 0L) storage.extract(emptyKey, takeEmpty, false, false);
            }
        }
        if (actual <= 0L) return ItemStack.EMPTY;

        return template.copyWithCount((int) actual);
    }

    /** 网络流体可替代的容器数量（仅受流体限制：流体量 / 每份所需） */
    public static long countSubstitutable(IStackHandler storage, ItemStack template) {
        if (storage == null || template == null || template.isEmpty()) return 0L;
        FluidStack fluid = getContainedFluid(storage, template);
        if (fluid.isEmpty() || fluid.getAmount() <= 0) return 0L;
        FluidStackKey fluidKey = new FluidStackKey(fluid.copyWithAmount(1));
        return storage.getStackByKey(fluidKey).amount() / fluid.getAmount();
    }

    /**
     * 网络流体可替代的容器数量（快照版：供客户端 JEI 池判定等只有 {@link KeyAmount} 列表的场景使用）。
     *
     * @param snapshot 网络存储快照（客户端镜像的 KeyAmount 列表）
     */
    public static long countSubstitutable(java.util.List<KeyAmount> snapshot, ItemStack template) {
        if (snapshot == null || template == null || template.isEmpty()) return 0L;
        FluidStack fluid = getContainedFluid(template);
        if (fluid.isEmpty() || fluid.getAmount() <= 0) return 0L;
        FluidStackKey fluidKey = new FluidStackKey(fluid.copyWithAmount(1));
        long total = 0L;
        for (KeyAmount ka : snapshot) {
            if (ka == null || ka.isEmpty()) continue;
            if (ka.key() instanceof FluidStackKey fk && fk.isSameTypeSameComponents(fluidKey)) {
                total += ka.amount();
            }
        }
        return total / fluid.getAmount();
    }

    /**
     * 用网络流体替代容器（宽松语义，供无实体槽位的合成如 TACZ 枪械台使用）：
     * 按每份所需扣除流体；网络若有对应空容器则一并扣除（最多实际替代数量，有多少扣多少）。
     *
     * @return 实际替代数量（受流体量限制）
     */
    public static long substituteWithFluid(IStackHandler storage, ItemStack template, long count) {
        if (storage == null || template == null || template.isEmpty() || count <= 0L) return 0L;
        FluidStack fluid = getContainedFluid(storage, template);
        if (fluid.isEmpty() || fluid.getAmount() <= 0) return 0L;
        long perCraft = fluid.getAmount();
        FluidStackKey fluidKey = new FluidStackKey(fluid.copyWithAmount(1));
        long actual = Math.min(count, storage.getStackByKey(fluidKey).amount() / perCraft);
        if (actual <= 0L) return 0L;
        long gotFluid = storage.extract(fluidKey, actual * perCraft, false, false).amount();
        long done = gotFluid / perCraft;
        long leftoverFluid = gotFluid - done * perCraft;
        if (leftoverFluid > 0L) storage.insert(fluidKey, leftoverFluid, false);
        if (done > 0L) {
            ItemStack empty = emptyContainerOf(template);
            if (!empty.isEmpty()) {
                ItemStackKey emptyKey = new ItemStackKey(empty.copyWithCount(1));
                long takeEmpty = Math.min(done, storage.getStackByKey(emptyKey).amount());
                if (takeEmpty > 0L) storage.extract(emptyKey, takeEmpty, false, false);
            }
        }
        return done;
    }

    /**
     * 用网络流体把空容器重新装填为指定容器物品（空容器由调用方提供，不再额外扣除）。
     * 用于合成返回物为空的容器时恢复为流体桶（保持桶循环）。
     *
     * @param filledTemplate 目标容器物品（含流体，决定流体类型与产物）
     * @param count          期望装填数量
     * @return 实际装填出的容器物品（流体不足时数量少于 count；不可用返回 EMPTY）
     */
    public static ItemStack refillContainer(IStackHandler storage, ItemStack filledTemplate, int count) {
        if (storage == null || filledTemplate == null || filledTemplate.isEmpty() || count <= 0) return ItemStack.EMPTY;
        FluidStack fluid = getContainedFluid(storage, filledTemplate);
        if (fluid.isEmpty() || fluid.getAmount() <= 0) return ItemStack.EMPTY;
        long perCraft = fluid.getAmount();
        FluidStackKey fluidKey = new FluidStackKey(fluid.copyWithAmount(1));
        long make = Math.min(count, storage.getStackByKey(fluidKey).amount() / perCraft);
        if (make <= 0L) return ItemStack.EMPTY;
        long gotFluid = storage.extract(fluidKey, make * perCraft, false, false).amount();
        long actual = gotFluid / perCraft;
        long leftoverFluid = gotFluid - actual * perCraft;
        if (leftoverFluid > 0L) storage.insert(fluidKey, leftoverFluid, false);
        if (actual <= 0L) return ItemStack.EMPTY;
        return filledTemplate.copyWithCount((int) actual);
    }

    /**
     * 从网络拆解含流体的容器为"空容器 + 流体回插网络"（合成需要空容器但网络只有流体桶时使用）。
     * 匹配条件：容器推导出的空容器与目标空容器一致（类型 + 组件）；内容流体按原量回插网络，不丢失。
     *
     * @param emptyTemplate 目标空容器物品（如空桶）
     * @param count         期望数量
     * @return 拆解出的空容器物品（网络无匹配流体容器时返回 EMPTY）
     */
    public static ItemStack emptyContainerFromNetwork(IStackHandler storage, ItemStack emptyTemplate, int count) {
        if (storage == null || emptyTemplate == null || emptyTemplate.isEmpty() || count <= 0) return ItemStack.EMPTY;
        long made = 0L;
        try {
            java.util.List<KeyAmount> snapshot = new java.util.ArrayList<>(storage.getStorage());
            for (KeyAmount ka : snapshot) {
                if (made >= count) break;
                if (!(ka.key() instanceof ItemStackKey ik)) continue;
                ItemStack stored = ik.getReadOnlyStack();
                if (stored.isEmpty()) continue;
                ItemStack emptyOfStored = emptyContainerOf(stored);
                if (emptyOfStored.isEmpty()
                        || !ItemStack.isSameItemSameComponents(emptyOfStored, emptyTemplate)) continue;
                FluidStack fluid = getContainedFluid(storage, stored);
                if (fluid.isEmpty() || fluid.getAmount() <= 0) continue;
                long want = Math.min(count - made, ka.amount());
                if (want <= 0L) continue;
                FluidStackKey fluidKey = new FluidStackKey(fluid.copyWithAmount(1));
                // 流体回插预检：网络放不下则跳过该容器（不拆解，避免流体丢失）
                if (!storage.insert(fluidKey, want * fluid.getAmount(), true).isEmpty()) continue;
                long got = storage.extract(ik, want, false, false).amount();
                if (got <= 0L) continue;
                // 容器内容物回插网络（等价于倒出），空容器用于合成
                storage.insert(fluidKey, got * fluid.getAmount(), false);
                made += got;
            }
        } catch (Throwable ignored) {}
        return made <= 0L ? ItemStack.EMPTY : emptyTemplate.copyWithCount((int) made);
    }

    /** 推导容器物品对应的空容器（合成剩余物；原版桶回退为空桶；无则 EMPTY） */
    public static ItemStack emptyContainerOf(ItemStack template) {
        if (template == null || template.isEmpty()) return ItemStack.EMPTY;
        ItemStack empty = template.getCraftingRemainingItem();
        if (empty.isEmpty() && template.getItem() instanceof BucketItem) empty = new ItemStack(Items.BUCKET);
        return empty;
    }

    /**
     * 容器内流体：流体 capability（{@link FluidUtil#getFluidContained}），
     * 无 capability 的容器（如原版牛奶桶）返回空、不参与流体转换。
     */
    public static FluidStack getContainedFluid(ItemStack template) {
        if (template == null || template.isEmpty()) return FluidStack.EMPTY;
        return FluidUtil.getFluidContained(template).orElse(FluidStack.EMPTY);
    }

    /** 兼容旧签名（storage 不参与流体识别） */
    public static FluidStack getContainedFluid(IStackHandler storage, ItemStack template) {
        return getContainedFluid(template);
    }

    /**
     * 从网络取出流体与空桶并组合成装满的桶。
     *
     * @param storage  网络统一存储
     * @param fluidKey 目标流体键（其 {@code getSource().getBucket()} 决定桶物品）
     * @param count    期望数量（上限受网络流体/空桶存量与桶最大堆叠限制）
     * @return 装满流体的桶（不可用或资源不足时返回 {@link ItemStack#EMPTY}）
     */
    public static ItemStack fillBuckets(IStackHandler storage, FluidStackKey fluidKey, int count) {
        if (storage == null || fluidKey == null || count <= 0) return ItemStack.EMPTY;
        Item bucket = fluidKey.getSource().getBucket();
        if (bucket == Items.AIR) return ItemStack.EMPTY;

        ItemStackKey bucketKey = new ItemStackKey(new ItemStack(Items.BUCKET));
        long fluidAvailable = storage.getStackByKey(fluidKey).amount() / MB_PER_BUCKET;
        long bucketAvailable = storage.getStackByKey(bucketKey).amount();
        long maxStack = new ItemStack(bucket).getMaxStackSize();
        long make = Math.min(Math.min(fluidAvailable, bucketAvailable), Math.min(count, maxStack));
        if (make <= 0L) return ItemStack.EMPTY;

        // 先取空桶，再按实际空桶数取流体；差额回插，保证不丢失资源
        long gotBuckets = storage.extract(bucketKey, make, false, false).amount();
        long gotFluid = storage.extract(fluidKey, gotBuckets * MB_PER_BUCKET, false, false).amount();
        long actual = Math.min(gotBuckets, gotFluid / MB_PER_BUCKET);
        long leftoverBuckets = gotBuckets - actual;
        if (leftoverBuckets > 0L) storage.insert(bucketKey, leftoverBuckets, false);
        long leftoverFluid = gotFluid - actual * MB_PER_BUCKET;
        if (leftoverFluid > 0L) storage.insert(fluidKey, leftoverFluid, false);
        if (actual <= 0L) return ItemStack.EMPTY;

        return new ItemStack(bucket, (int) actual);
    }
}
