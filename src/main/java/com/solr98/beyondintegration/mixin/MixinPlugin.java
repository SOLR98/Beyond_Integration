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
        // CompixelUI 物品图标刷新兜底：目标内部类不存在（Compixel 缺失或重构）时跳过，
        // 避免 mixin apply 失败；注入点本身还有 require = 0 兜底。此判定不依赖 ModList。
        if (targetClassName.startsWith("dev.compixel.")) {
            return classResourcePresent("dev/compixel/forge/item/NativeItemAtlas$Pages.class");
        }
        // 经验棒界面输入范围的 Kotlin 文件类（XpExchangeScreenKt）：字段名随 BD 版本可能变，
        // 缺失时跳过，避免 @Shadow 失败
        if (targetClassName.equals("com.wintercogs.beyonddimensions.client.ui.machine.XpExchangeScreenKt")) {
            return classResourcePresent("com/wintercogs/beyonddimensions/client/ui/machine/XpExchangeScreenKt.class");
        }
        var modList = ModList.get();
        if (modList == null) {
            // FML 早期阶段 ModList 可能尚未就绪：改用类资源探测（不触发类加载）判定 TACZ API 版本，
            // 避免 1.1.4 应用 1.1.5+ 专用 Mixin 崩溃，也避免误禁 1.1.5+ 的基础功能。
            if (targetClassName.startsWith("com.tacz.guns.")) {
                if (!classResourcePresent(TACZ_ABSTRACT_GUN)) return false;
                return isTaczApi115() || isTaczBaseMixin(mixinClassName);
            }
            if (targetClassName.startsWith("euphy.upo.sentrymechanicalarm.")) {
                String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
                if ("SentryArmReloadMixin".equals(simple)) return isTaczApi115();
                if ("SentryFakePlayerSyncMixin".equals(simple)) {
                    return classResourcePresent(TACZ_ABSTRACT_GUN);
                }
                return true;
            }
            return true;
        }
        // EmiLink 与本模组争夺同一批 EMI / BD-EMI 交互钩子（网络槽提取、Space 批量转移、
        // 结果槽批量合成、合成网格清理）。检测到 EmiLink 时本模组对所有 EMI 目标类一律不注入，
        // 由 EmiLink 独占，避免同一处点击被两个模组重复处理。
        // 常量在编译期内联，此处不会触发 EmiBdCompat 的类加载。
        if (targetClassName.startsWith("dev.emi.emi.")
                || targetClassName.startsWith("com.wintercogs.beyonddimensions.integration.module.emi.")) {
            return !modList.isLoaded(com.solr98.beyondintegration.compat.EmiBdCompat.CONFLICT_MOD_ID);
        }
        // BD 的 JEI 集成模块类：仅 JEI 加载时应用（未装 JEI 时该 target 不存在，
        // 且 mixin 引用 JEI 类型，过早应用会 NoClassDefFoundError）
        if (targetClassName.startsWith("com.wintercogs.beyonddimensions.integration.module.jei.")) {
            return modList.isLoaded("jei");
        }
        // 喂食器标记槽的内部 StackHandler（NetFeederMenu$1）：BD 版本可能没有这个匿名类，
        // 缺失时跳过本模组的放行 Mixin，避免整类 Mixin apply 失败导致启动崩溃。
        if (targetClassName.startsWith("com.wintercogs.beyonddimensions.common.menu.NetFeederMenu")) {
            return classResourcePresent("com/wintercogs/beyonddimensions/common/menu/NetFeederMenu.class");
        }
        if (targetClassName.startsWith("euphy.upo.sentrymechanicalarm.")) {
            if (!modList.isLoaded("sentrymechanicalarm")) return false;
            String sentryMixin = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
            // 哨戒换弹 Mixin 直接调用 IGun.useInventoryAmmo，仅 TACZ 1.1.5+ 存在
            if ("SentryArmReloadMixin".equals(sentryMixin)) {
                return modList.isLoaded("tacz") && isTaczApi115();
            }
            // 假玩家同步 Mixin 的参数类型引用 TACZ 的 IGun，需 TACZ 已加载
            if ("SentryFakePlayerSyncMixin".equals(sentryMixin)) {
                return modList.isLoaded("tacz");
            }
            return true;
        }
        if (targetClassName.startsWith("com.tacz.guns.")) {
            if (!modList.isLoaded("tacz")) return false;
            // TACZ 1.1.4 无 IGun.useInventoryAmmo / AbstractGunItem.hasInventoryAmmo 等 API：
            // 仅应用不依赖这些 API 的基础 Mixin；1.1.5+（含 1.2.0）应用全部
            if (isTaczApi115()) return true;
            return isTaczBaseMixin(mixinClassName);
        }
        if (targetClassName.startsWith("com.mafuyu404.taczaddon.")) {
            // taczaddon 自 1.1.8.1 起大幅重构；接受 1.1.8.1 起的所有版本
            // （1.1.8.1 / 1.1.8.1-hotfix* / 1.1.8.2 等），旧版 1.1.8 不注入
            return modList.isLoaded("taczaddon")
                    && modList.getModContainerById("taczaddon")
                    .map(c -> isTaczAddonCompatible(c.getModInfo().getVersion().toString()))
                    .orElse(false);
        }
        if (targetClassName.startsWith("io.redspace.ironsspellbooks.")) {
            return modList.isLoaded("irons_spellbooks");
        }
        if (targetClassName.startsWith("com.atsuishio.superbwarfare.")) {
            return modList.isLoaded("superbwarfare");
        }
        if (targetClassName.startsWith("com.Polarice3.Goety.")) {
            return modList.isLoaded("goety");
        }
        if (targetClassName.startsWith("com.github.tartaricacid.touhoulittlemaid.")) {
            // 女仆 P 点：祭坛消耗直接扣网络
            return modList.isLoaded("touhou_little_maid");
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

    /** TACZ 核心类资源路径（用于不加载类的前提下探测 API 版本）。 */
    private static final String TACZ_ABSTRACT_GUN = "com/tacz/guns/api/item/gun/AbstractGunItem.class";

    /** 缓存 TACZ API 探测结果：true = 含 useInventoryAmmo（1.1.5+）。 */
    private static Boolean taczApi115;

    /**
     * TACZ 是否为 1.1.5+（含 1.2.x）：
     * 读取 {@code AbstractGunItem} 类字节中的 {@code useInventoryAmmo(ItemStack)Z} 方法。
     * <p>不触发类加载（避免 Mixin 阶段 ReEntrantTransformerError），也不依赖 ModList 是否就绪。
     */
    private static boolean isTaczApi115() {
        Boolean cached = taczApi115;
        if (cached != null) return cached;
        boolean found = false;
        try (var in = MixinPlugin.class.getClassLoader().getResourceAsStream(TACZ_ABSTRACT_GUN)) {
            if (in != null) {
                ClassNode node = new ClassNode();
                new org.objectweb.asm.ClassReader(in)
                        .accept(node, org.objectweb.asm.ClassReader.SKIP_CODE
                                | org.objectweb.asm.ClassReader.SKIP_DEBUG
                                | org.objectweb.asm.ClassReader.SKIP_FRAMES);
                for (var m : node.methods) {
                    if ("useInventoryAmmo".equals(m.name)
                            && "(Lnet/minecraft/world/item/ItemStack;)Z".equals(m.desc)) {
                        found = true;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        taczApi115 = found;
        return found;
    }

    /** 类资源是否存在（不触发类加载）。 */
    private static boolean classResourcePresent(String path) {
        try (var in = MixinPlugin.class.getClassLoader().getResourceAsStream(path)) {
            return in != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * TACZ 1.1.4（< 1.1.5）可应用的基础 Mixin：
     * 这些注入目标在 1.1.4 存在；其余（Reload/Shoot/ReloadKey）依赖 1.1.5+ 的
     * {@code IGun.useInventoryAmmo}，在 1.1.4 下跳过。
     */
    private static boolean isTaczBaseMixin(String mixinClassName) {
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        return switch (simple) {
            case "AbstractGunItemMixin", "ModernKineticGunScriptAPIMixin",
                 "GunAnimationStateContextMixin", "GunHudOverlayMixin",
                 "GunSmithTableScreenMixin" -> true;
            default -> false;
        };
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
