package com.solr98.beyondintegration.client.hud;

import com.solr98.beyondintegration.client.SoulEnergyState;
import com.solr98.beyondintegration.client.render.SoulHudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 可自定义位置的 HUD 元素预览注册表（客户端）。
 * <p>新增 HUD：在 {@link HudElement} 加枚举项并在此 {@code register} 其预览，即可纳入统一拖动编辑器。
 */
public final class BiHuds {

    private static final Map<HudElement, HudPreview> PREVIEWS = new EnumMap<>(HudElement.class);

    private BiHuds() {
    }

    static {
        register(HudElement.SOUL, new HudPreview() {
            @Override
            public boolean isAvailable() {
                // 灵魂条依赖 Goety（未安装时编辑器不得加载相关类/读取其配置）
                return net.minecraftforge.fml.ModList.get().isLoaded("goety");
            }

            @Override
            public int width() {
                return SoulHudRenderer.WIDTH;
            }

            @Override
            public int height() {
                return SoulHudRenderer.HEIGHT;
            }

            @Override
            public void render(GuiGraphics g, int x, int y, float partialTicks) {
                long amount = SoulEnergyState.hasSource() ? Math.max(1L, SoulEnergyState.getAmount()) : 12345L;
                long cap = SoulEnergyState.getMax();
                if (cap <= 0L) cap = 100000L;
                SoulHudRenderer.render(g, x, y, amount, cap, partialTicks);
            }

            @Override
            public int defaultX(int sw, int sh) {
                return (sw / 2) + com.Polarice3.Goety.config.MainConfig.SoulGuiHorizontal.get();
            }

            @Override
            public int defaultY(int sw, int sh) {
                // 原版条上方两行：中间一行留给灵魂增减弹字
                return (sh + com.Polarice3.Goety.config.MainConfig.SoulGuiVertical.get())
                        - 9 - SoulHudRenderer.defaultRowOffset();
            }
        });

        register(HudElement.NET_SYNC, new HudPreview() {
            @Override
            public int width() {
                return Minecraft.getInstance().font.width(netSyncSample());
            }

            @Override
            public int height() {
                return 9;
            }

            @Override
            public void render(GuiGraphics g, int x, int y, float partialTicks) {
                var font = Minecraft.getInstance().font;
                g.drawString(font, netSyncSample(), x, y, 0xFF55FFFF, true);
            }

            @Override
            public int defaultY(int sw, int sh) {
                return sh - 45;
            }
        });

        register(HudElement.NET_MANA, new HudPreview() {
            @Override
            public boolean isAvailable() {
                // 网络魔力 HUD 依赖 Iron's Spells（其纹理/状态仅在该模组存在时有意义）
                return net.minecraftforge.fml.ModList.get().isLoaded("irons_spellbooks");
            }

            @Override
            public int width() {
                return NetManaHudOverlay.WIDTH;
            }

            @Override
            public int height() {
                return NetManaHudOverlay.HEIGHT;
            }

            @Override
            public void render(GuiGraphics g, int x, int y, float partialTicks) {
                NetManaHudRenderer.renderBar(g, x, y);
            }

            @Override
            public int defaultX(int sw, int sh) {
                return NetManaHudOverlay.defaultX(sw);
            }

            @Override
            public int defaultY(int sw, int sh) {
                return NetManaHudOverlay.defaultY(sh);
            }
        });
    }

    /** 供静态初始化/扩展注册。 */
    public static void register(HudElement e, HudPreview preview) {
        PREVIEWS.put(e, preview);
    }

    /** NET_SYNC 预览文本（与 HUD 示例一致，便于宽度/居中与编辑器对齐）。 */
    private static String netSyncSample() {
        return Component.translatable("hud.beyond_integration.net_sync.sample").getString();
    }

    public static HudPreview preview(HudElement e) {
        return PREVIEWS.get(e);
    }

    /** 编辑器可编辑的元素列表（按注册顺序；过滤掉当前不可用/依赖缺失的元素）。 */
    public static List<HudElement> elements() {
        List<HudElement> out = new java.util.ArrayList<>();
        for (Map.Entry<HudElement, HudPreview> entry : PREVIEWS.entrySet()) {
            HudPreview preview = entry.getValue();
            if (preview != null && preview.isAvailable()) out.add(entry.getKey());
        }
        return List.copyOf(out);
    }
}
