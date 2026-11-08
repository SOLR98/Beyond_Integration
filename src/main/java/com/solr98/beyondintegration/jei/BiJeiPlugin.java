package com.solr98.beyondintegration.jei;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.compat.RsIntegrationCompat;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * BI 的 JEI 插件（仅用于把 JEI 运行时交给 {@link JeiRuntimeHolder}）。
 * <p>JEI 点击取物走公开 API（物品列表 / 书签覆盖层）查询鼠标下物品，不需要注入 JEI 内部类，
 * 因此本插件只做运行时保存。
 * <p>配方转移与界面避让不再由 BI 注册：BD 0.8.3 自带 JEI 集成
 * （{@code integration.module.jei}，含 {@code WorkbenchRecipeTransferHandler}）。
 * <p><b>装了 rs_integration 时不保存运行时</b>：BI 的 JEI 角标 / 点击取物整体让路给 RI，
 * 下游入口（{@link BeyondJeiNetworkHelper}、{@code JeiExtractInputHandler}）拿不到运行时
 * 也就不会工作。
 */
@JeiPlugin
public class BiJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.tryParse(BeyondIntegration.MODID + ":jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        if (!com.solr98.beyondintegration.ClientConfig.jeiIntegration()) return;
        if (RsIntegrationCompat.isLoaded()) return;
        JeiRuntimeHolder.set(jeiRuntime);
    }
}
