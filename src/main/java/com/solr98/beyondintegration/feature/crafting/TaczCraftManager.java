package com.solr98.beyondintegration.feature.crafting;

import com.solr98.beyondintegration.api.ICraftingIntegration;
import com.solr98.beyondintegration.feature.crafting.RecipeMaterialScanner.MatchMode;
import com.solr98.beyondintegration.feature.crafting.RecipeMaterialScanner.MaterialInput;
import com.solr98.beyondintegration.network.NetworkItemCountsPacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestNetworkItemsPacket;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.*;

/**
 * TACZ 枪械工作台网络合成管理器（单例，实现 {@link ICraftingIntegration}）。
 * <p>从网络存储匹配/消耗材料批量合成（背包优先 → 网络补充），消耗前先预检防中途失败，
 * 产物可入网络或掉落；结束后同步客户端物品计数与合成状态。
 * <p>与 1.20.1 的 {@code TaczCraftManager} 同构：{@link TaczCraftPacket} 只做解码与委托，
 * 具体逻辑集中在本类（配方扫描、预检、扣料、产物、回传）。
 */
public final class TaczCraftManager implements ICraftingIntegration {

    private static final TaczCraftManager INSTANCE = new TaczCraftManager();

    private TaczCraftManager() {
    }

    public static TaczCraftManager get() {
        return INSTANCE;
    }

    @Override
    public String modId() {
        return "tacz";
    }

    /** 收集全部枪械工作台配方 ID（按 GunSmithTableRecipe 类型过滤，避免依赖 TACZ 的配方类型注册名）。 */
    @Override
    public Collection<ResourceLocation> getRecipeIds(RecipeManager manager) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (holder.value() instanceof GunSmithTableRecipe) {
                ids.add(holder.id());
            }
        }
        return ids;
    }

    @Override
    public boolean canCraft(ResourceLocation recipeId, RecipeManager manager) {
        return manager.byKey(recipeId)
                .map(holder -> holder.value() instanceof GunSmithTableRecipe)
                .orElse(false);
    }

    /** 执行批量合成：预扫网络建索引，最多 64 轮，材料不足即停并提示；产物入网络或掉落，结束后同步客户端。 */
    @Override
    public CraftResult executeCraft(ServerPlayer player, DimensionsNet net,
                                    ResourceLocation recipeId, int requested, boolean toNetwork) {
        var recipeOpt = player.getServer().getRecipeManager().byKey(recipeId);
        if (recipeOpt.isEmpty() || !(recipeOpt.get().value() instanceof GunSmithTableRecipe recipe)) {
            player.sendSystemMessage(Component.literal("[BI] TACZ 配方未找到: " + recipeId));
            return new CraftResult(0, ItemStack.EMPTY, Collections.emptyMap());
        }

        List<GunSmithTableIngredient> inputs = recipe.getInputs();
        if (inputs == null || inputs.isEmpty()) {
            player.sendSystemMessage(Component.literal("[BI] TACZ 配方无输入: " + recipeId));
            return new CraftResult(0, ItemStack.EMPTY, Collections.emptyMap());
        }

        RequestNetworkItemsPacket.ensureIndex(player);

        // 配方驱动定向扫描：构建材料输入清单（无 NBT → IDENTITY 定向精确键；
        // partial_nbt 候选 → PARTIAL_NBT 先定向、不足时全桶兜底）
        List<MaterialInput> materialInputs = new ArrayList<>();
        for (int ii = 0; ii < inputs.size(); ii++) {
            GunSmithTableIngredient gi = inputs.get(ii);
            if (gi == null) continue;
            Ingredient ing = gi.getIngredient();
            if (ing == null || ing.isEmpty()) continue;
            boolean hasNbt = false;
            for (ItemStack m : ing.getItems()) {
                if (!m.isEmpty() && m.has(DataComponents.CUSTOM_DATA) && !m.get(DataComponents.CUSTOM_DATA).isEmpty()) {
                    hasNbt = true;
                    break;
                }
            }
            materialInputs.add(new MaterialInput(ii, List.of(ing.getItems()),
                    hasNbt ? MatchMode.PARTIAL_NBT : MatchMode.IDENTITY, ing, gi.getCount()));
        }

        UnifiedStorage storage = net.getUnifiedStorage();
        var scanResult = RecipeMaterialScanner.scan(storage, materialInputs);
        Map<Integer, Long> slotTotals = scanResult.slotTotals();
        Map<Integer, List<ItemStackKey>> ingredientKeys = scanResult.slotKeys();

        int crafted = 0;
        for (int c = 0; c < 64; c++) {
            if (requested > 0 && crafted >= requested) break;

            String missing = checkIngredients(player, inputs, slotTotals);
            if (missing != null) {
                if (crafted == 0) player.sendSystemMessage(Component.literal(missing));
                break;
            }
            if (!consumeInputs(player, storage, inputs, ingredientKeys, slotTotals)) break;

            ItemStack result = recipe.getResultItem(player.level().registryAccess());
            if (!result.isEmpty()) {
                if (toNetwork) {
                    long left = storage.insert(new ItemStackKey(result), result.getCount(), false).amount();
                    if (left > 0) {
                        // 网络容量不足：余量掉落兜底，避免产物丢失
                        ItemStack drop = result.copy();
                        drop.setCount((int) left);
                        dropToPlayer(player, drop);
                    }
                } else {
                    dropToPlayer(player, result.copy());
                }
            }
            crafted++;
        }

        updateClient(player);
        net.setDirty();

        // 回传客户端：按界面读取键 "recipeId|idx"（与 scanNetworkItems 输出格式一致）
        Map<String, Long> clientCounts = new HashMap<>();
        for (var e : slotTotals.entrySet()) {
            clientCounts.put(recipeId + "|" + e.getKey(), e.getValue());
        }
        ItemStack toastItem = crafted > 0 ? recipe.getResultItem(player.level().registryAccess()) : ItemStack.EMPTY;
        PacketHandler.sendToPlayer(player, new NetworkItemCountsPacket(clientCounts, true, true,
                net.getId(), net.getCustomName(), toastItem, crafted));

        return new CraftResult(crafted, toastItem, clientCounts);
    }

    /** 校验每种输入：背包存量 + 网络可用（槽位定向/兜底聚合总量）是否足够，不足返回缺失提示。 */
    private static String checkIngredients(ServerPlayer player, List<GunSmithTableIngredient> inputs,
                                           Map<Integer, Long> slotTotals) {
        for (int i = 0; i < inputs.size(); i++) {
            GunSmithTableIngredient gi = inputs.get(i);
            if (gi == null) continue;
            Ingredient ing = gi.getIngredient();
            int need = gi.getCount();
            if (ing == null || ing.isEmpty() || need <= 0) continue;

            int inInv = countInInventory(player, ing);
            long inNet = slotTotals.getOrDefault(i, 0L);
            if (inInv + inNet < need) {
                ItemStack ex = ing.getItems().length > 0 ? ing.getItems()[0] : ItemStack.EMPTY;
                return Component.translatable("message.beyond_integration.material_insufficient",
                        ex.isEmpty() ? "?" : ex.getHoverName().getString()).getString();
            }
        }
        return null;
    }

    /** 真实消耗材料：先模拟预检（同一网络 key 跨输入精确累积），再背包→网络按序扣减并更新槽位计数。 */
    private static boolean consumeInputs(ServerPlayer player, UnifiedStorage storage,
                                          List<GunSmithTableIngredient> inputs,
                                          Map<Integer, List<ItemStackKey>> ingredientKeys,
                                          Map<Integer, Long> slotTotals) {
        // 预检：按真实抽取的同一顺序模拟扣减（simulate 不改状态），跨输入共享同一网络 key 时也精确校验
        Map<ItemStackKey, Long> simUsed = new HashMap<>();
        for (int i = 0; i < inputs.size(); i++) {
            GunSmithTableIngredient gi = inputs.get(i);
            if (gi == null) continue;
            Ingredient ing = gi.getIngredient();
            int need = gi.getCount();
            if (ing == null || ing.isEmpty() || need <= 0) continue;

            need -= countInInventory(player, ing);
            if (need <= 0) continue;
            List<ItemStackKey> keys = ingredientKeys.get(i);
            if (keys == null || keys.isEmpty()) {
                sendInsufficient(player, ing);
                return false;
            }
            for (ItemStackKey ik : keys) {
                if (need <= 0) break;
                long total = storage.extract(ik, Long.MAX_VALUE, true, false).amount();
                long avail = total - simUsed.getOrDefault(ik, 0L);
                if (avail <= 0) continue;
                long take = Math.min(need, avail);
                simUsed.merge(ik, take, Long::sum);
                need -= (int) take;
            }
            // 桶装流体替代：缺口按网络流体可替代量计入（宽松语义，空容器可选）
            if (need > 0) {
                for (ItemStack candidate : ing.getItems()) {
                    if (need <= 0) break;
                    long sub = com.solr98.beyondintegration.handler.BucketFluidHelper
                            .countSubstitutable(storage, candidate);
                    if (sub > 0) need -= (int) Math.min(need, sub);
                }
            }
            if (need > 0) {
                sendInsufficient(player, ing);
                return false;
            }
        }

        for (int i = 0; i < inputs.size(); i++) {
            GunSmithTableIngredient gi = inputs.get(i);
            if (gi == null) continue;
            Ingredient ing = gi.getIngredient();
            int need = gi.getCount();
            if (ing == null || ing.isEmpty() || need <= 0) continue;

            for (int j = 0; j < player.getInventory().getContainerSize() && need > 0; j++) {
                ItemStack stack = player.getInventory().getItem(j);
                if (stack.isEmpty() || !ing.test(stack)) continue;
                int take = Math.min(need, stack.getCount());
                stack.shrink(take);
                need -= take;
            }
            if (need <= 0) continue;

            List<ItemStackKey> keys = ingredientKeys.get(i);
            if (keys != null) {
                for (ItemStackKey ik : keys) {
                    if (need <= 0) break;
                    long avail = storage.getStackByKey(ik).amount();
                    if (avail <= 0) continue;
                    long take = Math.min(need, avail);
                    KeyAmount extracted = storage.extract(ik, take, false, false);
                    long got = extracted.amount();
                    if (got > 0) {
                        need -= (int) got;
                        slotTotals.merge(i, -got, Long::sum);
                    }
                }
            }
            // 桶装流体替代：桶物品不足的缺口改为消耗网络流体
            if (need > 0) {
                for (ItemStack candidate : ing.getItems()) {
                    if (need <= 0) break;
                    long sub = com.solr98.beyondintegration.handler.BucketFluidHelper
                            .substituteWithFluid(storage, candidate, need);
                    if (sub > 0) {
                        need -= (int) sub;
                        slotTotals.merge(i, -sub, Long::sum);
                    }
                }
            }
            if (need > 0) {
                sendInsufficient(player, ing);
                return false;
            }
        }
        return true;
    }

    private static int countInInventory(ServerPlayer player, Ingredient ing) {
        int total = 0;
        for (int j = 0; j < player.getInventory().getContainerSize(); j++) {
            ItemStack stack = player.getInventory().getItem(j);
            if (!stack.isEmpty() && ing.test(stack)) total += stack.getCount();
        }
        return total;
    }

    private static void sendInsufficient(ServerPlayer player, Ingredient ing) {
        ItemStack ex = ing.getItems().length > 0 ? ing.getItems()[0] : ItemStack.EMPTY;
        player.sendSystemMessage(Component.translatable("message.beyond_integration.material_insufficient",
                ex.isEmpty() ? "?" : ex.getHoverName().getString()));
    }

    private static void dropToPlayer(ServerPlayer player, ItemStack stack) {
        var entity = new net.minecraft.world.entity.item.ItemEntity(
                player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
        entity.setPickUpDelay(0);
        player.level().addFreshEntity(entity);
    }

    /** 若玩家正打开枪械工作台 GUI：广播背包全量状态并通知客户端刷新合成界面。 */
    private static void updateClient(ServerPlayer player) {
        if (player.containerMenu instanceof com.tacz.guns.inventory.GunSmithTableMenu menu) {
            player.inventoryMenu.broadcastFullState();
            com.tacz.guns.network.NetworkHandler.sendToClientPlayer(
                    new com.tacz.guns.network.message.ServerMessageCraft(menu.containerId), player);
        }
    }
}
