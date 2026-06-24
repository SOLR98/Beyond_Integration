package com.solr98.beyondintegration.feature.blacklist;
import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.NotNull;

public class ItemBlacklistHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {
    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert, @NotNull KeyAmount tryInsert, DimensionsNet net) {
        if (!CommandConfig.enableItemBlacklist()) return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        if (tryInsert.key() instanceof ItemStackKey itemKey) {
            var item = itemKey.getSource();
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null && CommandConfig.itemBlacklist().contains(id.toString()))
                return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, true);
        }
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
    }
}
