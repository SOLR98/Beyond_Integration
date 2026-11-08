package com.solr98.beyondintegration.feature.soul;

import com.Polarice3.Goety.common.ritual.ModRitualFactory;
import com.solr98.beyondintegration.BeyondIntegration;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * 本模组向 Goety 的 {@code goety:ritual_factory} 注册表贡献的仪式工厂。
 * <p>仅当 {@code goety} 加载时由主类注册该 DeferredRegister。
 */
public final class ModRitualFactories {

    /** 复用 Goety 的 ritual_factory 注册表名（跨模组共享）。 */
    public static final DeferredRegister<ModRitualFactory> RITUALS =
            DeferredRegister.create(ResourceLocation.fromNamespaceAndPath("goety", "ritual_factory"), BeyondIntegration.MODID);

    /** 网络灵魂源激活仪式工厂（id：beyond_integration:soul_source）。 */
    public static final DeferredHolder<ModRitualFactory, ModRitualFactory> SOUL_SOURCE =
            RITUALS.register("soul_source", () -> new ModRitualFactory(SoulSourceRitual::new));

    private ModRitualFactories() {}
}
