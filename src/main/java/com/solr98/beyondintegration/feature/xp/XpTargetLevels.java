package com.solr98.beyondintegration.feature.xp;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.common.item.XpExchangeSettings;

/**
 * 经验棒目标等级上限的唯一口径。
 * <p>BI 增强开关（{@code bd_tweaks.xp_rod_enabled}）关闭时用 BD 原上限
 * （{@link XpExchangeSettings#MAX_TARGET_LEVEL}，21863）；开启时用配置
 * {@code bd_tweaks.max_target_level}。
 * <p>所有放宽路径（{@code sanitizeTargetLevel}、菜单的 {@code requestTarget}/SET_TARGET、
 * 经验棒界面的输入范围）都必须用这个值，否则会出现「输入框能填但提交被拒」的不一致。
 */
public final class XpTargetLevels {

    private XpTargetLevels() {
    }

    /** 当前生效的目标等级上限。 */
    public static int max() {
        try {
            return CommandConfig.xpRodTweaksEnabled()
                    ? CommandConfig.xpRodMaxTargetLevel()
                    : XpExchangeSettings.MAX_TARGET_LEVEL;
        } catch (Throwable ignored) {
            // 配置尚未加载（类初始化早于配置）：退回 BD 上限
            return XpExchangeSettings.MAX_TARGET_LEVEL;
        }
    }
}
