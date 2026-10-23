package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.client.hud.BiHuds;
import com.solr98.beyondintegration.client.hud.HudElement;
import com.solr98.beyondintegration.client.hud.HudLayout;
import com.solr98.beyondintegration.client.hud.HudPreview;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 通用 HUD 位置可视化调整界面：<b>所有可调 HUD 同时显示</b>，点击选中某个后拖动；
 * 「保存」把当前选中项写为<b>相对当前屏幕尺寸的比例</b>（{@code RATIO}），窗口缩放后位置按比例跟随；
 * 「重置」清除该项回到默认。
 * 「◀ / ▶」在当前元素间循环选择。保存/重置按钮自动避让<b>当前正在调整</b>的元素。
 * <p>通过快捷键打开（受客户端配置 {@code hud_visual_edit} 控制）。
 */
public class HudPositionScreen extends Screen {

    private final List<HudElement> elements;
    private final Map<HudElement, int[]> positions = new EnumMap<>(HudElement.class);

    private HudElement active;
    private boolean dragging;
    private int grabX;
    private int grabY;

    private Button prevBtn;
    private Button nextBtn;
    private Button saveBtn;
    private Button resetBtn;

    public HudPositionScreen() {
        this(HudElement.SOUL);
    }

    public HudPositionScreen(HudElement initial) {
        super(Component.translatable("screen.beyond_integration.hud_pos.title"));
        this.elements = BiHuds.elements();
        this.active = (initial != null && this.elements.contains(initial))
                ? initial
                : (this.elements.isEmpty() ? HudElement.SOUL : this.elements.get(0));
    }

    private HudPreview preview(HudElement e) {
        return BiHuds.preview(e);
    }

    private int hitW(HudElement e) {
        HudPreview p = preview(e);
        return p == null ? 100 : p.width();
    }

    private int hitH(HudElement e) {
        HudPreview p = preview(e);
        return p == null ? 9 : p.height();
    }

    private int[] posOf(HudElement e) {
        return positions.computeIfAbsent(e, k -> defaultPos(e));
    }

    private int[] defaultPos(HudElement e) {
        HudPreview p = preview(e);
        return new int[]{
                p == null ? (this.width - 100) / 2 : p.defaultX(this.width, this.height),
                p == null ? this.height / 2 : p.defaultY(this.width, this.height)
        };
    }

    /** 载入所有元素的当前位置（无布局则用默认）。 */
    private void loadPositions() {
        positions.clear();
        for (HudElement e : elements) {
            HudPreview p = preview(e);
            if (p == null) continue;
            int defX = p.defaultX(this.width, this.height);
            int defY = p.defaultY(this.width, this.height);
            try {
                positions.put(e, new int[]{
                        HudLayout.resolveX(e, defX, this.width),
                        HudLayout.resolveY(e, defY, this.height)
                });
            } catch (Throwable ignored) {
                // 布局值无法识别：回退默认位置
                positions.put(e, new int[]{defX, defY});
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        // 仅在首次打开时载入；窗口尺寸变化重建控件时保留未保存的拖动结果
        if (positions.isEmpty()) loadPositions();

        int bw = 74;
        int gap = 4;
        int total = bw * 4 + gap * 3;
        int startX = this.width / 2 - total / 2;
        int y0 = this.height - 28;

        this.prevBtn = addRenderableWidget(Button.builder(Component.literal("◀"), b -> cycle(-1))
                .bounds(startX, y0, bw, 20).build());
        this.nextBtn = addRenderableWidget(Button.builder(Component.literal("▶"), b -> cycle(1))
                .bounds(startX + (bw + gap), y0, bw, 20).build());
        this.saveBtn = addRenderableWidget(Button.builder(
                        Component.translatable("screen.beyond_integration.hud_pos.save"), b -> save())
                .bounds(startX + (bw + gap) * 2, y0, bw, 20).build());
        this.resetBtn = addRenderableWidget(Button.builder(
                        Component.translatable("screen.beyond_integration.hud_pos.reset"), b -> reset())
                .bounds(startX + (bw + gap) * 3, y0, bw, 20).build());

        boolean multi = elements.size() > 1;
        this.prevBtn.visible = multi;
        this.nextBtn.visible = multi;
    }

    private void cycle(int d) {
        if (elements.size() <= 1) return;
        int i = elements.indexOf(active);
        active = elements.get((i + d + elements.size()) % elements.size());
    }

    private void save() {
        if (this.width <= 0 || this.height <= 0) return;
        int[] p = posOf(active);
        // 按当前屏幕比例保存：窗口/分辨率变化后按相对位置缩放
        HudLayout.setRatio(active,
                HudLayout.pixelToRatio(p[0], this.width),
                HudLayout.pixelToRatio(p[1], this.height));
    }

    private void reset() {
        HudLayout.clear(active);
        positions.put(active, defaultPos(active));
    }

    /** HUD 位置编辑：不绘制背景（避免模糊/半透明遮罩，保持游戏画面可见）。 */
    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        // 按钮自动避让“当前正在调整”的元素
        int rowY = beyond$buttonRowY();
        this.prevBtn.setY(rowY);
        this.nextBtn.setY(rowY);
        this.saveBtn.setY(rowY);
        this.resetBtn.setY(rowY);

        // 屏幕中心十字参考线
        int cx = this.width / 2;
        int cy = this.height / 2;
        g.fill(cx, 0, cx + 1, this.height, 0x40FFFFFF);
        g.fill(0, cy, this.width, cy + 1, 0x40FFFFFF);

        // 所有元素预览（非活动项半透明描边，活动项橙色描边）；先画预览，按钮稍后覆盖其上
        for (HudElement e : elements) {
            HudPreview p = preview(e);
            if (p == null) continue;
            int[] pos = posOf(e);
            p.render(g, pos[0], pos[1], partialTicks);
            boolean on = e == active;
            int color = on ? 0xFFFFAA00 : 0x80FFFFFF;
            g.renderOutline(pos[0] - 1, pos[1] - 1, hitW(e) + 2, hitH(e) + 2, color);
        }

        // 按钮最后绘制，保证不被 HUD 预览遮挡
        super.render(g, mouseX, mouseY, partialTicks);

        g.drawString(this.font, Component.translatable("screen.beyond_integration.hud_pos.hint").getString(),
                8, 8, 0xFFFFFF);
        int[] ap = posOf(active);
        HudLayout.Mode layoutMode = HudLayout.mode(active);
        String mode = layoutMode == null ? "DEFAULT" : layoutMode.name();
        g.drawString(this.font,
                "[" + Component.translatable(active.nameKey()).getString() + "]  x=" + ap[0] + "  y=" + ap[1] + "  " + mode,
                8, 20, 0xFFFFDD55);
    }

    /** 按钮行 Y：默认底部；与“当前调整项”重叠则切到顶部；两端都叠则取重叠更小的一侧。 */
    private int beyond$buttonRowY() {
        int bottom = this.height - 28;
        int top = 30;
        int ob = beyond$buttonOverlap(bottom);
        if (ob == 0) return bottom;
        int ot = beyond$buttonOverlap(top);
        if (ot == 0) return top;
        return ob <= ot ? bottom : top;
    }

    /** 按钮行（水平居中，宽度覆盖全部按钮）与“当前调整项”矩形的交面积。 */
    private int beyond$buttonOverlap(int buttonY) {
        int bw = 74;
        int gap = 4;
        int total = bw * 4 + gap * 3;
        int bx1 = this.width / 2 - total / 2;
        int bx2 = bx1 + total;
        int by1 = buttonY;
        int by2 = buttonY + 20;
        int[] ap = posOf(active);
        int ix = Math.max(0, Math.min(bx2, ap[0] + hitW(active)) - Math.max(bx1, ap[0]));
        int iy = Math.max(0, Math.min(by2, ap[1] + hitH(active)) - Math.max(by1, ap[1]));
        return ix * iy;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 先交给按钮等控件，避免与 HUD 预览区域重叠时抢走按钮点击
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0) {
            // 先判活动项（便于拖动），再判其它项（点击即选中）
            HudElement hit = hitTest(mx, my, active);
            if (hit == null) {
                for (HudElement e : elements) {
                    hit = hitTest(mx, my, e);
                    if (hit != null) break;
                }
            }
            if (hit != null) {
                active = hit;
                int[] p = posOf(hit);
                dragging = true;
                grabX = (int) mx - p[0];
                grabY = (int) my - p[1];
                return true;
            }
        }
        return false;
    }

    private HudElement hitTest(double mx, double my, HudElement e) {
        if (preview(e) == null) return null;
        int[] p = posOf(e);
        if (mx >= p[0] && mx <= p[0] + hitW(e) && my >= p[1] && my <= p[1] + hitH(e)) return e;
        return null;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            int[] p = posOf(active);
            p[0] = Math.max(0, Math.min(Math.max(0, this.width - hitW(active)), (int) mx - grabX));
            p[1] = Math.max(0, Math.min(Math.max(0, this.height - hitH(active)), (int) my - grabY));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }
}
