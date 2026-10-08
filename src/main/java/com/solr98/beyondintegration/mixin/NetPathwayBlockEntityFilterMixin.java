package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.netpathway.NetPathwayFilterAccess;
import com.solr98.beyondintegration.feature.netpathway.NetPathwayFilteredStorage;
import com.solr98.beyondintegration.common.menu.NetPathwayFilterMenu;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.common.block.entity.NetPathwayBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.HashSet;
import java.util.Set;

/**
 * 给 BD 维度网络通道（net_pathway）附加“标记槽白名单过滤”：
 * <ul>
 *   <li>新增标记槽（尺寸 = 全局行数×9）、每方块开关（启用过滤 / 仅接收输入 / 模糊过滤）；</li>
 *   <li>对邻居暴露的能力在启用过滤时替换为 {@link NetPathwayFilteredStorage} 视图（严格白名单：标记槽为空则不暴露任何物品）。</li>
 * </ul>
 */
@Mixin(value = NetPathwayBlockEntity.class, remap = false)
public abstract class NetPathwayBlockEntityFilterMixin implements NetPathwayFilterAccess, MenuProvider {

    @Unique private boolean beyond$filterEnabled = false;
    @Unique private boolean beyond$onlyInput = false;
    @Unique private boolean beyond$fuzzy = false;
    @Unique private StackHandler beyond$filterSlots;

    @Shadow(remap = false)
    private void clearCapCache() {
        throw new AssertionError();
    }

    @Override
    public boolean beyond$isFilterEnabled() {
        return beyond$filterEnabled;
    }

    @Override
    public void beyond$setFilterEnabled(boolean value) {
        beyond$filterEnabled = value;
        clearCapCache();
    }

    @Override
    public boolean beyond$isOnlyInput() {
        return beyond$onlyInput;
    }

    @Override
    public void beyond$setOnlyInput(boolean value) {
        beyond$onlyInput = value;
        clearCapCache();
    }

    @Override
    public boolean beyond$isFuzzy() {
        return beyond$fuzzy;
    }

    @Override
    public void beyond$setFuzzy(boolean value) {
        beyond$fuzzy = value;
        clearCapCache();
    }

    @Override
    public StackHandler beyond$getFilterSlots() {
        if (beyond$filterSlots == null) {
            int rows = Math.max(1, Math.min(6, CommandConfig.netPathwayFilterRows()));
            final NetPathwayBlockEntityFilterMixin self = this;
            beyond$filterSlots = new StackHandler(rows * 9) {
                @Override
                public void onChange() {
                    NetPathwayBlockEntity be = (NetPathwayBlockEntity) (Object) self;
                    if (be.getLevel() != null && !be.getLevel().isClientSide()) {
                        be.setChanged();
                    }
                    self.clearCapCache();
                }
            };
        }
        return beyond$filterSlots;
    }

    @Override
    public UnifiedStorage beyond$wrapStorage(UnifiedStorage original) {
        if (!beyond$filterEnabled) return original;
        Set<IStackKey<?>> whitelist = new HashSet<>();
        for (KeyAmount ka : beyond$getFilterSlots().getStorage()) {
            if (!ka.isEmpty()) whitelist.add(ka.key());
        }
        return new NetPathwayFilteredStorage(original, whitelist, beyond$onlyInput, beyond$fuzzy);
    }

    @ModifyArg(method = "getCapability",
            at = @At(value = "INVOKE",
                    target = "Lcom/wintercogs/beyonddimensions/api/util/USHandler;apply(Lcom/wintercogs/beyonddimensions/api/dimensionnet/UnifiedStorage;Lcom/wintercogs/beyonddimensions/api/util/CapCtx;)Ljava/lang/Object;"),
            index = 0, remap = false)
    private UnifiedStorage beyond$filterStorage(UnifiedStorage original) {
        return beyond$wrapStorage(original);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("menu.beyond_integration.net_pathway_filter");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new NetPathwayFilterMenu(containerId, inventory, (NetPathwayFilterAccess) (Object) this);
    }
}
