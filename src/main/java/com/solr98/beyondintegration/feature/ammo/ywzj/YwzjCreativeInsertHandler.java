package com.solr98.beyondintegration.feature.ammo.ywzj;

import com.solr98.beyondintegration.handler.YwzjCreativeAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Before-insert handler that detects YWZJ creative ammo items ({@code ywzj_vehicle:ammo_creative})
 * and enables infinite ammo mode on the target network.
 * <p>
 * The creative ammo item is stored physically in the network (so it still takes a slot),
 * and the network is marked with a flag so that all bound YWZJ vehicles using network ammo
 * reload to full capacity without consuming items from network storage.
 * <p>
 * When the creative ammo item is later extracted from the network, the flag is cleared
 * via the delta subscription hook in {@code DimensionsNetMixin}.
 */
public class YwzjCreativeInsertHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {

    private static final ResourceLocation AMMO_CREATIVE_ID = ResourceLocation.parse("ywzj_vehicle:ammo_creative");

    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert, @NotNull KeyAmount tryInsert, DimensionsNet net) {
        if (net == null) return pass(tryInsert);
        if (!(tryInsert.key() instanceof ItemStackKey itemKey)) return pass(tryInsert);

        ItemStack stack = itemKey.getReadOnlyStack();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!AMMO_CREATIVE_ID.equals(id)) return pass(tryInsert);

        if (net instanceof YwzjCreativeAccessor acc) {
            if (!acc.beyond$isYwzjCreativeAmmo()) {
                acc.beyond$setYwzjCreativeAmmo(true);
                net.setDirty();
            }
        }

        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
    }

    private static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo pass(KeyAmount input) {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(input, false);
    }
}
