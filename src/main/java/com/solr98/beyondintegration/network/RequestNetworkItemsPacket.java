package com.solr98.beyondintegration.network;

import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Supplier;

/**
 * C2S：客户端请求"主网络"中所有与 TACZ 枪械工作台配方相关的物品计数，
 * 服务端扫描网络存储后回发 NetworkItemCountsPacket 全量数据。
 */
public class RequestNetworkItemsPacket {

    /** 物品注册名 -> 其参与的所有 TACZ 配方条目（惰性构建的全局索引） */
    public static final Map<String, List<TaczIngredient>> TACZ_INDEX = new HashMap<>();
    // 索引按 server 实例 + 配方集合大小校验，datapack reload/换档后自动重建
    private static net.minecraft.server.MinecraftServer indexServer = null;
    private static int indexRecipeCount = -1;

    /** 一条 TACZ 配方输入记录：配方 ID、输入槽位下标、匹配用 Ingredient、是否含 NBT */
    public record TaczIngredient(ResourceLocation recipeId, int idx, Ingredient ingredient, boolean hasNbt) {}

    /** 索引失效时（换服务端或配方数量变化）重建 TACZ_INDEX */
    public static void ensureIndex(ServerPlayer player) {
        if (player.getServer() == null) return;
        int count = player.getServer().getRecipeManager().getRecipes().size();
        if (indexServer != player.getServer() || indexRecipeCount != count) {
            buildIndex(player);
        }
    }

    /** 无字段，编码为空操作 */
    public static void encode(RequestNetworkItemsPacket msg, FriendlyByteBuf buf) {}

    /** 解码：无字段，直接返回新实例 */
    public static RequestNetworkItemsPacket decode(FriendlyByteBuf buf) {
        return new RequestNetworkItemsPacket();
    }

    /** 服务端处理：取玩家主网络，扫描相关物品计数，回发 NetworkItemCountsPacket */
    public static void handle(RequestNetworkItemsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) {
                PacketHandler.sendToPlayer(player, new NetworkItemCountsPacket(new HashMap<>(), true, false, -1));
                return;
            }

            ensureIndex(player);

            Map<String, Long> counts = scanNetworkItems(net);

            PacketHandler.sendToPlayer(player, new NetworkItemCountsPacket(counts, true, true, net.getId()));
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * 配方驱动定向扫描网络物品（与合成侧一致）：
     * TACZ_INDEX 无 partial_nbt（hasNbt）条目 → 全部 itemId 定向精确键（O(材料) 个 O(1)，零桶扫描）；
     * 存在 hasNbt 条目 → 全桶遍历按 ingredient.test 聚合（partial_nbt 子集匹配，保证正确）。
     */
    public static Map<String, Long> scanNetworkItems(DimensionsNet net) {
        Map<String, Long> counts = new HashMap<>();
        boolean anyNbt = TACZ_INDEX.values().stream()
                .flatMap(List::stream)
                .anyMatch(TaczIngredient::hasNbt);

        if (!anyNbt) {
            // 定向：每个配方材料 itemId 精确键 O(1)
            for (String itemId : TACZ_INDEX.keySet()) {
                ResourceLocation id = ResourceLocation.tryParse(itemId);
                if (id == null) continue;
                Item item = ForgeRegistries.ITEMS.getValue(id);
                if (item == null) continue;
                long amount = net.getUnifiedStorage()
                        .getStackByKey(new ItemStackKey(new ItemStack(item))).amount();
                if (amount <= 0) continue;
                for (TaczIngredient ti : TACZ_INDEX.get(itemId)) {
                    counts.merge(ti.recipeId() + "|" + ti.idx(), amount, Long::sum);
                }
            }
            return counts;
        }

        // 全桶兜底（hasNbt 条目存在时）
        net.getUnifiedStorage().getBucket(ItemStackKey.ID).ifPresent(bucket -> {
            for (int i = 0; i < bucket.size(); i++) {
                IStackKey<?> rawKey = bucket.get(i);
                if (!(rawKey instanceof ItemStackKey ik)) continue;
                long amount = net.getUnifiedStorage().getStackByKey(ik).amount();
                if (amount <= 0) continue;
                ItemStack stored = ik.getReadOnlyStack();
                if (stored.isEmpty()) continue;
                List<TaczIngredient> related = TACZ_INDEX.get(stored.getItem().toString());
                if (related != null) {
                    for (var ti : related) {
                        if (ti.ingredient().test(stored)) {
                            counts.merge(ti.recipeId() + "|" + ti.idx(), amount, Long::sum);
                        }
                    }
                }
            }
        });
        return counts;
    }

    /** 遍历枪械工作台配方，按输入物品注册名建立 TACZ_INDEX 索引 */
    private static void buildIndex(ServerPlayer player) {
        TACZ_INDEX.clear();
        for (var recipe : player.getServer().getRecipeManager().getRecipes()) {
            if (!(recipe instanceof GunSmithTableRecipe taczRecipe)) continue;
            ResourceLocation rid = taczRecipe.getId();
            List<GunSmithTableIngredient> inputs = taczRecipe.getInputs();
            if (inputs == null) continue;
            for (int idx = 0; idx < inputs.size(); idx++) {
                GunSmithTableIngredient gi = inputs.get(idx);
                if (gi == null) continue;
                Ingredient ing = gi.getIngredient();
                if (ing == null || ing.isEmpty()) continue;
                boolean hasNbt = false;
                for (ItemStack m : ing.getItems()) {
                    if (!m.isEmpty() && m.hasTag() && !m.getTag().isEmpty()) {
                        hasNbt = true;
                        break;
                    }
                }
                for (ItemStack m : ing.getItems()) {
                    if (m.isEmpty()) continue;
                    String id = m.getItem().toString();
                    TACZ_INDEX.computeIfAbsent(id, k -> new ArrayList<>())
                        .add(new TaczIngredient(rid, idx, ing, hasNbt));
                }
            }
        }
        indexServer = player.getServer();
        indexRecipeCount = player.getServer().getRecipeManager().getRecipes().size();
    }
}
