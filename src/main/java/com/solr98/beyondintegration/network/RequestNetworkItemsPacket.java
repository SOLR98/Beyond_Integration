package com.solr98.beyondintegration.network;

import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.solr98.beyondintegration.feature.ammo.tacz.TaczRecipeIndex;
import com.solr98.beyondintegration.feature.ammo.tacz.TaczRecipeIndex.TaczIngredient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Supplier;

/**
 * C2S：客户端请求"主网络"中所有与 TACZ 枪械工作台配方相关的物品计数，
 * 服务端扫描网络存储后回发 NetworkItemCountsPacket 全量数据。
 * <p>配方索引 {@link TaczRecipeIndex} 预构建缓存；本类仅负责按索引做存储扫描。
 */
public class RequestNetworkItemsPacket {

    public static void encode(RequestNetworkItemsPacket msg, FriendlyByteBuf buf) {}

    public static RequestNetworkItemsPacket decode(FriendlyByteBuf buf) {
        return new RequestNetworkItemsPacket();
    }

    /** 服务端处理：取玩家主网络，按预构建配方索引扫描相关物品计数，回发 NetworkItemCountsPacket */
    public static void handle(RequestNetworkItemsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) {
                PacketHandler.sendToPlayer(player, new NetworkItemCountsPacket(new HashMap<>(), true, false, -1));
                return;
            }

            Map<String, List<TaczIngredient>> index = TaczRecipeIndex.ensure(player.getServer().getRecipeManager());
            Map<String, Long> counts = scanNetworkItems(net.getUnifiedStorage(), index);

            PacketHandler.sendToPlayer(player, new NetworkItemCountsPacket(counts, true, true, net.getId()));
        });
        ctx.get().setPacketHandled(true);
    }

    /** 便捷重载：服务端从网络对象取存储（保持旧签名兼容）。 */
    public static Map<String, Long> scanNetworkItems(DimensionsNet net) {
        if (net == null) return new HashMap<>();
        return scanNetworkItems(net.getUnifiedStorage(), TaczRecipeIndex.index());
    }

    /**
     * 配方驱动定向扫描（两端通用：服务端传 {@code UnifiedStorage}，客户端传主网络镜像）。
     * <p>索引无 partial_nbt（hasNbt）条目 → 全部 itemId 定向精确键（O(材料) 个 O(1)，零桶扫描）；
     * 存在 hasNbt 条目 → 全桶遍历按 ingredient.test 聚合（partial_nbt 子集匹配，保证正确）。
     */
    public static Map<String, Long> scanNetworkItems(AbstractUnorderedStackHandler storage,
                                                     Map<String, List<TaczIngredient>> index) {
        Map<String, Long> counts = new HashMap<>();
        if (storage == null || index == null || index.isEmpty()) return counts;

        boolean anyNbt = index.values().stream()
                .flatMap(List::stream)
                .anyMatch(TaczIngredient::hasNbt);

        if (!anyNbt) {
            for (String itemId : index.keySet()) {
                ResourceLocation id = ResourceLocation.tryParse(itemId);
                if (id == null) continue;
                Item item = ForgeRegistries.ITEMS.getValue(id);
                if (item == null) continue;
                long amount = storage.getStackByKey(new ItemStackKey(new ItemStack(item))).amount();
                long substitutable = com.solr98.beyondintegration.handler.BucketFluidHelper
                        .countSubstitutable(storage, new ItemStack(item));
                long total = amount + substitutable;
                if (total <= 0) continue;
                for (TaczIngredient ti : index.get(itemId)) {
                    counts.merge(ti.recipeId() + "|" + ti.idx(), total, Long::sum);
                }
            }
            return counts;
        }

        storage.getBucket(ItemStackKey.ID).ifPresent(bucket -> {
            for (int i = 0; i < bucket.size(); i++) {
                IStackKey<?> rawKey = bucket.get(i);
                if (!(rawKey instanceof ItemStackKey ik)) continue;
                long amount = storage.getStackByKey(ik).amount();
                if (amount <= 0) continue;
                ItemStack stored = ik.getReadOnlyStack();
                if (stored.isEmpty()) continue;
                List<TaczIngredient> related = index.get(stored.getItem().toString());
                if (related != null) {
                    for (TaczIngredient ti : related) {
                        if (ti.ingredient().test(stored)) {
                            counts.merge(ti.recipeId() + "|" + ti.idx(), amount, Long::sum);
                        }
                    }
                }
            }
        });

        for (String itemId : index.keySet()) {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null) continue;
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null) continue;
            long substitutable = com.solr98.beyondintegration.handler.BucketFluidHelper
                    .countSubstitutable(storage, new ItemStack(item));
            if (substitutable <= 0) continue;
            for (TaczIngredient ti : index.get(itemId)) {
                counts.merge(ti.recipeId() + "|" + ti.idx(), substitutable, Long::sum);
            }
        }
        return counts;
    }
}
