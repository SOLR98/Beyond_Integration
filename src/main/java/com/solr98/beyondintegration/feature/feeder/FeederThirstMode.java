package com.solr98.beyondintegration.feature.feeder;

/**
 * 网络喂食器「补水档位」：与 BD 喂食档位（hunger）同款语义，只是作用于口渴。
 * <ul>
 *   <li>{@link #SATURATION_KEEP}：始终保持水饱和（quenched ≤ 0 就补）。</li>
 *   <li>{@link #CRAZY}：口渴一降就喝（thirst &lt; 20）。</li>
 *   <li>{@link #NORMAL}：口渴低于一半才喝（thirst ≤ 10）。</li>
 *   <li>{@link #HUNGER_TO_EAT}：口渴低于 10% 才喝（thirst ≤ 2）。</li>
 * </ul>
 */
public enum FeederThirstMode
{
    SATURATION_KEEP,
    CRAZY,
    NORMAL,
    HUNGER_TO_EAT;

    /** 当前口渴/水饱和是否达到该档位的补水时机 */
    public boolean matches(int thirst, int quenched)
    {
        return switch (this)
        {
            case SATURATION_KEEP -> quenched <= 0;
            case CRAZY -> thirst < 20;
            case NORMAL -> thirst <= 10;
            case HUNGER_TO_EAT -> thirst <= 2;
        };
    }
}
