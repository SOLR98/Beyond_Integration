package com.solr98.beyondintegration.feature.enchant;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 附魔台燃料（青金石）自动补充：燃料槽不足时，从玩家主网络的存储中提取青金石补入。
 */
public final class EnchantLapisHelper {

    private EnchantLapisHelper() {
    }

    /**
     * 确保燃料槽至少有 {@code need} 个青金石；不足部分从网络提取补入。
     *
     * @return 补足后燃料是否达到 {@code need}
     */
    public static boolean ensureLapis(Player owner, Slot fuelSlot, int need) {
        if (need <= 0) return true;
        if (!(owner instanceof ServerPlayer sp)) return false;
        ItemStack fuel = fuelSlot.getItem();
        int have = fuel.isEmpty() ? 0 : fuel.getCount();
        if (have >= need) return true;

        DimensionsNet net = com.solr98.beyondintegration.handler.NetMenuResolver.resolve(sp);
        if (net == null) return false;

        int missing = need - have;
        // 单槽最多 64：只取能放下的数量
        int room = 64 - have;
        if (room <= 0) return false;
        int request = Math.min(missing, room);

        ItemStackKey key = new ItemStackKey(new ItemStack(Items.LAPIS_LAZULI));
        long got = net.getUnifiedStorage().extract(key, request, false, false).amount();
        if (got <= 0L) return false;

        int add = (int) Math.min(got, room);
        if (fuel.isEmpty()) {
            fuelSlot.set(new ItemStack(Items.LAPIS_LAZULI, add));
        } else {
            fuel.grow(add);
            fuelSlot.setChanged();
        }
        return fuelSlot.getItem().getCount() >= need;
    }
}
