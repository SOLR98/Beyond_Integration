package com.solr98.beyondintegration.jei;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.feature.crafting.DimensionsCraftMenu;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI 集成插件：注册本模组工作台菜单的配方转移处理器（合成台 / 切石机），
 * 并为 BD 终端 GUI 注册避让区域（模式按钮列 / 弹药面板 / 左侧溢出列）。
 */
@JeiPlugin
public class BDJeiPlugin implements IModPlugin {

    // 插件唯一标识（beyond_integration:jei_plugin）
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.tryParse(BeyondIntegration.MODID + ":jei_plugin");
    }

    // 注册配方转移处理器：合成配方 → DimensionsCraftMenu，切石配方 → DimensionsCutMenu
    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new CraftRecipeTransferHandler(), RecipeTypes.CRAFTING);
        registration.addRecipeTransferHandler(new CutRecipeTransferHandler(), RecipeTypes.STONECUTTING);
    }

    // 注册 GUI 避让：把本模组在 BD 终端 GUI 上额外绘制的区域告知 JEI
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGenericGuiContainerHandler(DimensionsNetGUI.class, new BDSidebarGuiHandler());
    }

    // JEI 运行时就绪：保存实例供客户端公开 API 查询（物品列表/书签鼠标下物品）
    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiRuntimeHolder.set(jeiRuntime);
    }

    // JEI 运行时卸载：清空引用
    @Override
    public void onRuntimeUnavailable() {
        JeiRuntimeHolder.set(null);
    }
}
