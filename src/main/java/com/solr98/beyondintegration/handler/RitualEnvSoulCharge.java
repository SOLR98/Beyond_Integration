package com.solr98.beyondintegration.handler;

/**
 * 仪式「环境绕过」额外魂消耗执行器：由 {@code DarkAltarBlockEntityMixin} 实现，
 * 供 {@code RitualRequirementsMixin} 在环境绕过生效时按“仪式扣魂”方式扣一次额外魂。
 * <p>扣取来源与仪式扣魂一致：诅咒牢笼（原生优先，不足由网络补）→ 网络通道绑定的网络。
 */
public interface RitualEnvSoulCharge {

    /**
     * 环境绕过生效时调用。仅在仪式<b>起始阶段</b>（祭坛当前无进行中的仪式）真正扣魂；
     * 仪式进行中（tick 期间）不再重复扣。
     *
     * @param amount 需要扣除的魂量
     * @return {@code true} = 允许绕过（起始阶段已足量扣除，或仪式进行中无需再扣）；
     *         {@code false} = 阻止绕过（起始阶段魂量不足，且已不扣除任何魂）
     */
    boolean beyond$chargeEnvBypassSoul(long amount);
}
