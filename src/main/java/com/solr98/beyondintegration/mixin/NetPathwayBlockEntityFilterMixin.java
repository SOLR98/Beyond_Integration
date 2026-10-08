package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.common.menu.NetPathwayFilterMenu;
import com.solr98.beyondintegration.feature.netpathway.NetPathwayFilterAccess;
import com.solr98.beyondintegration.feature.netpathway.NetPathwayFilteredStorage;
import com.wintercogs.beyonddimensions.api.capability.helper.CapabilityHelper;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.util.CapCtx;
import com.wintercogs.beyonddimensions.api.util.USHandler;
import com.wintercogs.beyonddimensions.common.block.entity.NetPathwayBlockEntity;
import com.wintercogs.beyonddimensions.common.init.BDBlockEntities;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * 给 BD 维度网络通道（net_pathway）附加“标记槽白名单过滤”（NeoForge 1.21.1）。
 * 严格白名单：启用过滤时替换为 {@link NetPathwayFilteredStorage} 视图（标记槽为空则不暴露任何物品）。
 */
@Mixin(value = NetPathwayBlockEntity.class, remap = false)
public abstract class NetPathwayBlockEntityFilterMixin implements NetPathwayFilterAccess, MenuProvider {

    @Unique private boolean beyond$filterEnabled = false;
    @Unique private boolean beyond$onlyInput = false;
    @Unique private boolean beyond$fuzzy = false;
    @Unique private StackHandler beyond$filterSlots;

    @Override
    public boolean beyond$isFilterEnabled() {
        return beyond$filterEnabled;
    }

    @Override
    public void beyond$setFilterEnabled(boolean value) {
        beyond$filterEnabled = value;
    }

    @Override
    public boolean beyond$isOnlyInput() {
        return beyond$onlyInput;
    }

    @Override
    public void beyond$setOnlyInput(boolean value) {
        beyond$onlyInput = value;
    }

    @Override
    public boolean beyond$isFuzzy() {
        return beyond$fuzzy;
    }

    @Override
    public void beyond$setFuzzy(boolean value) {
        beyond$fuzzy = value;
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

    /**
     * BD 1.21.1 的能力改由 {@code RegisterCapabilitiesEvent} 的静态 lambda 构造，实例方法
     * {@code getCapability} 已不存在（旧注入点失效且被 defaultRequire=0 静默跳过）。
     * 这里在 {@code registerCapability} 头部接管注册：沿用同样的处理器，但把网络存储换成过滤视图。
     */
    @Inject(method = "registerCapability", at = @At("HEAD"), cancellable = true, remap = false)
    private static void beyond$registerFilteredCapability(RegisterCapabilitiesEvent event, CallbackInfo ci) {
        CapabilityHelper.BlockCapabilityMap.forEach((id, cap) -> {
            USHandler handler = CapabilityHelper.USHandlerMap.get(id);
            event.registerBlockEntity(
                    (BlockCapability<? super Object, ? extends Direction>) cap,
                    BDBlockEntities.NET_PATHWAY_BLOCK_ENTITY.get(),
                    (be, side) -> {
                        DimensionsNet net = be.getNet();
                        if (net == null || handler == null) {
                            return null;
                        }
                        UnifiedStorage storage = ((NetPathwayFilterAccess) be).beyond$wrapStorage(net.getUnifiedStorage());
                        if (handler.isContextual()) {
                            return handler.apply(storage, new CapCtx(be.getLevel(), be.getBlockPos(), be));
                        }
                        return handler.apply(storage, null);
                    });
        });
        ci.cancel();
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
