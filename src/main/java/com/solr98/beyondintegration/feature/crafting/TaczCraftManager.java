package com.solr98.beyondintegration.feature.crafting;

import com.solr98.beyondintegration.api.ICraftingIntegration;
import com.solr98.beyondintegration.network.NetworkItemCountsPacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestNetworkItemsPacket;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModRecipe;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.*;

/**
 * TACZ 枪械工作台合成管理器（单例）：实现 ICraftingIntegration。
 * 从网络存储匹配/消耗材料批量合成（背包优先 → 网络补充），消耗前先 simulate 预检防中途失败，
 * 产物可入网络或掉落；结束后同步客户端物品计数与合成状态。
 */
public final class TaczCraftManager implements ICraftingIntegration {

    private static final TaczCraftManager INSTANCE = new TaczCraftManager();

    private TaczCraftManager() {}

    public static TaczCraftManager get() { return INSTANCE; }

    // 所属模组 ID：tacz
    @Override
    public String modId() { return "tacz"; }

    // 收集枪械工作台全部配方 ID
    @Override
    public Collection<ResourceLocation> getRecipeIds(RecipeManager manager) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (GunSmithTableRecipe recipe : manager.getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get())) {
            ids.add(recipe.getId());
        }
        return ids;
    }

    // 判定配方是否属于枪械工作台合成
    @Override
    public boolean canCraft(ResourceLocation recipeId, RecipeManager manager) {
        return manager.byKey(recipeId).map(r -> r instanceof GunSmithTableRecipe).orElse(false);
    }

    // 执行批量合成：预扫网络建索引，最多 64 轮，材料不足即停并提示；产物入网络或掉落，结束后同步客户端
    @Override
    public CraftResult executeCraft(ServerPlayer player, DimensionsNet net,
                                     ResourceLocation recipeId, int requested, boolean toNetwork) {
        var recipeOpt = player.getServer().getRecipeManager().byKey(recipeId);
        if (recipeOpt.isEmpty() || !(recipeOpt.get() instanceof GunSmithTableRecipe recipe))
            return new CraftResult(0, ItemStack.EMPTY, Collections.emptyMap());

        List<GunSmithTableIngredient> inputs = recipe.getInputs();
        if (inputs == null || inputs.isEmpty())
            return new CraftResult(0, ItemStack.EMPTY, Collections.emptyMap());

        RequestNetworkItemsPacket.ensureIndex(player);

        // 配方驱动定向扫描：构建材料输入清单（无 NBT → IDENTITY 定向精确键；
        // partial_nbt 候选 → PARTIAL_NBT 先定向、不足时全桶兜底）
        List<RecipeMaterialScanner.MaterialInput> materialInputs = new ArrayList<>();
        for (int ii = 0; ii < inputs.size(); ii++) {
            GunSmithTableIngredient gi = inputs.get(ii);
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
            materialInputs.add(new RecipeMaterialScanner.MaterialInput(ii, List.of(ing.getItems()),
                    hasNbt ? RecipeMaterialScanner.MatchMode.PARTIAL_NBT : RecipeMaterialScanner.MatchMode.IDENTITY,
                    ing, gi.getCount()));
        }

        var scanResult = RecipeMaterialScanner.scan(net.getUnifiedStorage(), materialInputs);
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
            if (!consumeInputs(player, net, inputs, ingredientKeys, slotTotals)) break;

            ItemStack result = recipe.getResultItem(player.level().registryAccess());
            if (!result.isEmpty()) {
                if (toNetwork) {
                    long left = net.getUnifiedStorage()
                            .insert(new ItemStackKey(result), result.getCount(), false).amount();
                    if (left > 0) {
                        // 网络容量不足：余量掉落兜底，避免产物丢失
                        ItemStack drop = result.copy();
                        drop.setCount((int) left);
                        var entity = new net.minecraft.world.entity.item.ItemEntity(
                                player.level(), player.getX(), player.getY() + 0.5, player.getZ(), drop);
                        entity.setPickUpDelay(0);
                        player.level().addFreshEntity(entity);
                    }
                } else {
                    var entity = new net.minecraft.world.entity.item.ItemEntity(
                            player.level(), player.getX(), player.getY() + 0.5, player.getZ(), result.copy());
                    entity.setPickUpDelay(0);
                    player.level().addFreshEntity(entity);
                }
            }
            crafted++;
        }

        updateClient(player);

        // 回传客户端：按界面读取键 "recipeId|idx"（与 scanNetworkItems 输出格式一致）
        Map<String, Long> clientCounts = new HashMap<>();
        for (var e : slotTotals.entrySet()) {
            clientCounts.put(recipeId + "|" + e.getKey(), e.getValue());
        }
        ItemStack toastItem = crafted > 0 ? recipe.getResultItem(player.level().registryAccess()) : ItemStack.EMPTY;
        PacketHandler.sendToPlayer(player, new NetworkItemCountsPacket(clientCounts, true, true,
                net.getId(), toastItem, crafted));

        return new CraftResult(crafted, toastItem, clientCounts);
    }

    // 校验每种输入：背包存量 + 网络可用（槽位定向/兜底聚合总量）是否足够，不足返回本地化缺失提示
    private static String checkIngredients(ServerPlayer player, List<GunSmithTableIngredient> inputs,
                                            Map<Integer, Long> slotTotals) {
        for (int i = 0; i < inputs.size(); i++) {
            GunSmithTableIngredient gi = inputs.get(i);
            if (gi == null) continue;
            Ingredient ing = gi.getIngredient();
            int need = gi.getCount();
            if (ing == null || ing.isEmpty() || need <= 0) continue;

            int inInv = 0;
            for (int j = 0; j < player.getInventory().getContainerSize(); j++) {
                ItemStack stack = player.getInventory().getItem(j);
                if (!stack.isEmpty() && ing.test(stack)) inInv += stack.getCount();
            }

            long inNet = slotTotals.getOrDefault(i, 0L);
            if (inInv + inNet < need) {
                ItemStack ex = ing.getItems().length > 0 ? ing.getItems()[0] : ItemStack.EMPTY;
                return Component.translatable("message.beyond_integration.material_insufficient",
                        ex.isEmpty() ? Component.translatable("command.beyond_integration.error.unknown") : ex.getHoverName()).getString();
            }
        }
        return null;
    }

    // 真实消耗材料：先 simulate 预检（同一网络 key 跨输入精确累积），再背包→网络按序扣减并更新槽位计数；失败提示并返回 false
    private static boolean consumeInputs(ServerPlayer player, DimensionsNet net,
                                          List<GunSmithTableIngredient> inputs,
                                          Map<Integer, List<ItemStackKey>> ingredientKeys,
                                          Map<Integer, Long> slotTotals) {
        var storage = net.getUnifiedStorage();
        // 预检：按真实抽取的同一顺序模拟扣减（simulate 不改状态），
        // 跨输入共享同一网络 key 时也精确校验（simUsed 累积），通过则真实抽取必成功
        Map<ItemStackKey, Long> simUsed = new HashMap<>();
        for (int i = 0; i < inputs.size(); i++) {
            GunSmithTableIngredient gi = inputs.get(i);
            if (gi == null) continue;
            Ingredient ing = gi.getIngredient();
            int need = gi.getCount();
            if (ing == null || ing.isEmpty() || need <= 0) continue;
            int inInv = 0;
            for (int j = 0; j < player.getInventory().getContainerSize(); j++) {
                ItemStack stack = player.getInventory().getItem(j);
                if (!stack.isEmpty() && ing.test(stack)) inInv += stack.getCount();
            }
            need -= inInv;
            if (need <= 0) continue;
            List<ItemStackKey> keys = ingredientKeys.get(i);
            if (keys == null || keys.isEmpty()) {
                ItemStack ex0 = ing.getItems().length > 0 ? ing.getItems()[0] : ItemStack.EMPTY;
                player.sendSystemMessage(Component.translatable("message.beyond_integration.material_insufficient",
                        ex0.isEmpty() ? Component.translatable("command.beyond_integration.error.unknown") : ex0.getHoverName()));
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
            if (need > 0) {
                ItemStack ex1 = ing.getItems().length > 0 ? ing.getItems()[0] : ItemStack.EMPTY;
                player.sendSystemMessage(Component.translatable("message.beyond_integration.material_insufficient",
                        ex1.isEmpty() ? Component.translatable("command.beyond_integration.error.unknown") : ex1.getHoverName()));
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
                    KeyAmount extractResult = storage.extract(ik, take, false, false);
                    long extracted = extractResult.amount();
                    if (extracted > 0) {
                        need -= extracted;
                        // 扣减槽位网络总量（回传客户端用）
                        slotTotals.merge(i, -extracted, Long::sum);
                    }
                }
            }
            if (need > 0) {
                // 预检已保证网络可抽量，走到此处说明网络状态在抽取过程中变化，提示而非静默失败
                ItemStack ex = ing.getItems().length > 0 ? ing.getItems()[0] : ItemStack.EMPTY;
                player.sendSystemMessage(Component.translatable("message.beyond_integration.material_insufficient",
                        ex.isEmpty() ? Component.translatable("command.beyond_integration.error.unknown") : ex.getHoverName()));
                return false;
            }
        }
        net.setDirty();
        return true;
    }

    // 若玩家正打开枪械工作台 GUI：广播背包全量状态并通知客户端刷新合成界面
    private static void updateClient(ServerPlayer player) {
        if (player.containerMenu instanceof com.tacz.guns.inventory.GunSmithTableMenu menu) {
            player.inventoryMenu.broadcastFullState();
            com.tacz.guns.network.NetworkHandler.sendToClientPlayer(
                    new com.tacz.guns.network.message.ServerMessageCraft(menu.containerId), player);
        }
    }
}
