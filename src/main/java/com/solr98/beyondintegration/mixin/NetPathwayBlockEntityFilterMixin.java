package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.common.menu.NetPathwayFilterMenu;
import com.solr98.beyondintegration.feature.netpathway.NetPathwayFilterAccess;
import com.solr98.beyondintegration.feature.netpathway.NetPathwayFilteredStorage;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.common.block.entity.NetPathwayBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * 给 BD 维度网络通道（net_pathway）附加「标记槽白名单过滤」，并让它成为 {@link MenuProvider}：
 * <ul>
 *   <li>新增标记槽（尺寸 = 全局行数×9）、每方块开关（启用过滤 / 仅接收输入 / 模糊过滤）；</li>
 *   <li>过滤视图由 {@code NetPathwayCapabilityWrapper} 在 BD 能力暴露时套用
 *       （BD 0.8 统一走 {@code CapabilityHelper.USHandlerMap}，本类只提供包装入口）；</li>
 *   <li>实现 {@link MenuProvider}：{@code NetPathwayBlockUseMixin} 里打开过滤界面依赖本接口。</li>
 * </ul>
 * 配置与标记槽随方块实体 NBT 存档（{@code bi_net_pathway_filter} 段）。
 */
@Mixin(value = NetPathwayBlockEntity.class, remap = false)
public abstract class NetPathwayBlockEntityFilterMixin implements NetPathwayFilterAccess, MenuProvider {

    @Unique private boolean beyond$filterEnabled = false;
    @Unique private boolean beyond$onlyInput = false;
    @Unique private boolean beyond$fuzzy = false;
    @Unique private StackHandler beyond$filterSlots;

    /** 配置变化：标脏存档；BD 的能力缓存一并清理（反射，缺失即忽略）。 */
    @Unique
    private void beyond$markChanged() {
        NetPathwayBlockEntity self = (NetPathwayBlockEntity) (Object) this;
        self.setChanged();
        try {
            var method = NetPathwayBlockEntity.class.getDeclaredMethod("clearCapCache");
            method.setAccessible(true);
            method.invoke(self);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean beyond$isFilterEnabled() {
        return beyond$filterEnabled;
    }

    @Override
    public void beyond$setFilterEnabled(boolean value) {
        beyond$filterEnabled = value;
        beyond$markChanged();
    }

    @Override
    public boolean beyond$isOnlyInput() {
        return beyond$onlyInput;
    }

    @Override
    public void beyond$setOnlyInput(boolean value) {
        beyond$onlyInput = value;
        beyond$markChanged();
    }

    @Override
    public boolean beyond$isFuzzy() {
        return beyond$fuzzy;
    }

    @Override
    public void beyond$setFuzzy(boolean value) {
        beyond$fuzzy = value;
        beyond$markChanged();
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
                    if (be.getLevel() != null && !be.getLevel().isClientSide) {
                        be.setChanged();
                    }
                    self.beyond$markChanged();
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

    @Inject(method = "load", at = @At("TAIL"), remap = false)
    private void beyond$readFilter(CompoundTag tag, CallbackInfo ci) {
        if (!tag.contains("bi_net_pathway_filter", net.minecraft.nbt.Tag.TAG_COMPOUND)) return;
        CompoundTag config = tag.getCompound("bi_net_pathway_filter");
        beyond$filterEnabled = config.getBoolean("enabled");
        beyond$onlyInput = config.getBoolean("only_input");
        beyond$fuzzy = config.getBoolean("fuzzy");
        if (config.contains("slots", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            beyond$getFilterSlots().deserializeNBT(config.getCompound("slots"));
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = false)
    private void beyond$writeFilter(CompoundTag tag, CallbackInfo ci) {
        CompoundTag config = new CompoundTag();
        config.putBoolean("enabled", beyond$filterEnabled);
        config.putBoolean("only_input", beyond$onlyInput);
        config.putBoolean("fuzzy", beyond$fuzzy);
        config.put("slots", beyond$getFilterSlots().serializeNBT());
        tag.put("bi_net_pathway_filter", config);
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
