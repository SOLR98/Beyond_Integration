package com.solr98.beyondintegration.maid;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 东方 Little Maid 女仆饰物扩展。
 * 通过 LittleMaidExtension 注解自动注册，将 Beyond Dimensions 的网络终端与
 * 经验棒物品绑定为可被女仆使用的饰物。
 */
@LittleMaidExtension
public class MaidBaubleExtension implements ILittleMaid {
    /**
     * 绑定女仆饰物：网络终端（女仆网络）与经验棒（网络经验修复女仆装备）。
     * 物品不存在时跳过。
     */
    @Override
    public void bindMaidBauble(BaubleManager manager) {
        Item terminal = ForgeRegistries.ITEMS.getValue(
                ResourceLocation.tryParse("beyonddimensions:net_terminal_item"));
        if (terminal != null && terminal != Items.AIR) {
            manager.bind(terminal, new IMaidBauble() {});
        }
        Item xpRod = ForgeRegistries.ITEMS.getValue(
                ResourceLocation.tryParse("beyonddimensions:xp_exchange_item"));
        if (xpRod != null && xpRod != Items.AIR) {
            manager.bind(xpRod, new com.solr98.beyondintegration.maid.XpExchangeBauble());
        }
    }
}
