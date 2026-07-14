package com.solr98.beyondintegration.maid;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

@LittleMaidExtension
public class MaidBaubleExtension implements ILittleMaid {
    @Override
    public void bindMaidBauble(BaubleManager manager) {
        Item terminal = ForgeRegistries.ITEMS.getValue(
                ResourceLocation.tryParse("beyonddimensions:net_terminal_item"));
        if (terminal != null && terminal != Items.AIR) {
            manager.bind(terminal, new NetTerminalBauble());
        }
    }
}
