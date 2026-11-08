package com.solr98.beyondintegration.client.hud;

/**
 * 本模组可自定义位置的 HUD 元素（1.21.1 客户端）。
 * <p>新增元素：在此加枚举项，并在 {@link BiHuds} 注册其预览渲染；HUD 绘制处用
 * {@link HudLayout#resolveX}/{@link HudLayout#resolveY} 取实际坐标。
 */
public enum HudElement {
    /** 网络灵魂条（第三条，Goety 联动）。 */
    SOUL("soul"),
    /** 主网络同步状态 HUD。 */
    NET_SYNC("net_sync"),
    /** 网络魔力（铁魔法联动）：维度发生器图标 + 数值。 */
    NET_MANA("net_mana");

    private final String id;

    HudElement(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static HudElement byId(String id) {
        for (HudElement e : values()) {
            if (e.id.equals(id)) return e;
        }
        return null;
    }

    /** 名称本地化键。 */
    public String nameKey() {
        return "hud.beyond_integration.pos.element." + id;
    }
}
