package com.solr98.beyondintegration.mixin;

import net.minecraftforge.fml.ModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin 配置插件：按目标类所属模组的加载状态决定是否应用对应 Mixin。
 * 仅当目标模组（sentrymechanicalarm / tacz / superbwarfare）已加载时才注入，
 * 防止在未安装这些模组的环境下因目标类缺失导致崩溃。
 */
public class MixinPlugin implements IMixinConfigPlugin {

    /** 插件初始化回调（无操作） */
    @Override
    public void onLoad(String mixinPackage) {}

    /** 返回空，不提供重映射配置 */
    @Override
    public String getRefMapperConfig() { return null; }

    /** 根据目标类前缀判断对应模组是否已加载，决定 Mixin 是否生效 */
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        var modList = ModList.get();
        if (modList == null) return true;
        // EmiLink 与本模组争夺同一批 EMI / BD-EMI 交互钩子（网络槽提取、Space 批量转移、
        // 结果槽批量合成、合成网格清理）。检测到 EmiLink 时本模组对所有 EMI 目标类一律不注入，
        // 由 EmiLink 独占，避免同一处点击被两个模组重复处理。
        // 常量在编译期内联，此处不会触发 EmiBdCompat 的类加载。
        if (targetClassName.startsWith("dev.emi.emi.")
                || targetClassName.startsWith("com.wintercogs.beyonddimensions.integration.module.emi.")) {
            return !modList.isLoaded(com.solr98.beyondintegration.compat.EmiBdCompat.CONFLICT_MOD_ID);
        }
        if (targetClassName.startsWith("euphy.upo.sentrymechanicalarm.")) {
            return modList.isLoaded("sentrymechanicalarm");
        }
        if (targetClassName.startsWith("com.tacz.guns.")) {
            return modList.isLoaded("tacz");
        }
        if (targetClassName.startsWith("com.mafuyu404.taczaddon.")) {
            // taczaddon 自 1.1.8.1 起大幅重构；接受 1.1.8.1 起的所有版本
            // （1.1.8.1 / 1.1.8.1-hotfix* / 1.1.8.2 等），旧版 1.1.8 不注入
            return modList.isLoaded("taczaddon")
                    && modList.getModContainerById("taczaddon")
                    .map(c -> isTaczAddonCompatible(c.getModInfo().getVersion().toString()))
                    .orElse(false);
        }
        if (targetClassName.startsWith("com.atsuishio.superbwarfare.")) {
            return modList.isLoaded("superbwarfare");
        }
        if (targetClassName.startsWith("mezz.jei.")) {
            // JEI 集成（物品数量角标/点击取物品）：仅 JEI 加载时应用；
            // 检测到 rs_integration（RI）时让路禁用，避免与其同类功能冲突
            if (!modList.isLoaded("jei") || modList.isLoaded("rs_integration")) return false;
            // 版本敏感 Mixin 按 JEI 布局选择：新版（15.56+）IngredientGridTooltipHelper 移入
            // .ingredients 包 → 应用 NewMixin；旧版应用原名 Mixin；其余 JEI Mixin 始终应用
            if (mixinClassName.endsWith("NewMixin")) return isNewJeiLayout();
            if (mixinClassName.endsWith("JeiIngredientElementMixin")
                    || mixinClassName.endsWith("JeiIngredientBookmarkElementMixin")) {
                return !isNewJeiLayout();
            }
            return true;
        }
        if (mixinClassName.contains("FeederThirst")) {
            // 网络喂食器口渴补水集成：Thirst 或 Legendary Survival Overhaul 任一加载时应用
            return modList.isLoaded("thirst") || modList.isLoaded("legendarysurvivaloverhaul");
        }
        return true;
    }

    /** 缓存 JEI 新版布局判定（IngredientGridTooltipHelper 是否位于 .ingredients 包） */
    private static Boolean newJeiLayout;

    /**
     * 运行时 JEI 是否为新版布局（15.56+，helper 移入 {@code mezz.jei.gui.overlay.ingredients}）。
     * 按目标类是否存在判定，避免硬编码版本号；类加载失败视为旧版布局。
     */
    private static boolean isNewJeiLayout() {
        if (newJeiLayout == null) {
            boolean found;
            try {
                Class.forName("mezz.jei.gui.overlay.ingredients.IngredientGridTooltipHelper", false,
                        MixinPlugin.class.getClassLoader());
                found = true;
            } catch (Throwable ignored) {
                found = false;
            }
            newJeiLayout = found;
        }
        return newJeiLayout;
    }

    /**
     * taczaddon 版本兼容判定：接受 1.1.8.1 起的所有重构版本。
     * 例：1.1.8.1 / 1.1.8.1-hotfix6 / 1.1.8.2；排除旧版 1.1.8。
     */
    private static boolean isTaczAddonCompatible(String version) {
        if (version == null || version.isEmpty()) return false;
        return version.startsWith("1.1.8-fix")
                || version.matches("1\\.1\\.8\\.[0-9]+.*");
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
