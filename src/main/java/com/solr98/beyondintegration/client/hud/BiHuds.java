package com.solr98.beyondintegration.client.hud;

import com.solr98.beyondintegration.client.PrimaryNetClientStorage;
import com.solr98.beyondintegration.client.SoulEnergyState;
import com.solr98.beyondintegration.client.render.SoulHudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 可自定义位置的 HUD 元素预览注册表（1.21.1 客户端）。
 */
public final class BiHuds {

    private static final Map<HudElement, HudPreview> PREVIEWS = new EnumMap<>(HudElement.class);

    private BiHuds() {
    }

    static {
        register(HudElement.SOUL, new HudPreview() {
            @Override
            public boolean isAvailable() {
                return ModList.get().isLoaded("goety");
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
                g.drawString(Minecraft.getInstance().font, netSyncSample(), x, y, 0xFF55FFFF, true);
            }

            @Override
            public int defaultY(int sw, int sh) {
                return sh - 45;
            }
        });

        register(HudElement.NET_MANA, new HudPreview() {
            @Override
            public boolean isAvailable() {
                return ModList.get().isLoaded("irons_spellbooks");
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
                return (sw - NetManaHudOverlay.WIDTH) / 2;
            }

            @Override
            public int defaultY(int sw, int sh) {
                return sh - 45;
            }
        });
    }

    private static String netSyncSample() {
        return Component.translatable("hud.beyond_integration.net_sync.sample").getString();
    }

    public static void register(HudElement e, HudPreview preview) {
        PREVIEWS.put(e, preview);
    }

    public static HudPreview preview(HudElement e) {
        return PREVIEWS.get(e);
    }

    /** 编辑器可编辑的元素列表（过滤依赖缺失元素）。 */
    public static List<HudElement> elements() {
        List<HudElement> out = new ArrayList<>();
        for (Map.Entry<HudElement, HudPreview> entry : PREVIEWS.entrySet()) {
            HudPreview preview = entry.getValue();
            if (preview != null && preview.isAvailable()) out.add(entry.getKey());
        }
        return List.copyOf(out);
    }
}
