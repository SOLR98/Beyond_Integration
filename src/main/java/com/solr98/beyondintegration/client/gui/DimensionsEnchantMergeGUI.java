package com.solr98.beyondintegration.client.gui;

// 注意：Beyond Dimensions 本体将在下个版本更换 UI 框架，本类依赖其现有 GUI 布局/坐标/纹理，
// 待 BD 正式发布后需校对代码与新版 GUI。


import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.crafting.DimensionsEnchantMergeMenu;
import com.solr98.beyondintegration.feature.crafting.DimensionsEnchantMergeMenu.MergeOption;
import com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SubmitEnchantMergePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Quaternionf;

/**
 * 批量附魔工作站界面（面板 176×150，纵向：装备槽 + 候选列表（5 行/页）+ 操作栏）。
 * 面板背景使用标准背景贴图 standard_background.png；底部操作按钮使用原版按钮渲染；
 * 勾选框使用 BD slot_button 贴图；等级滑条为带档位（离散吸附）的滑块。
 */
public class DimensionsEnchantMergeGUI extends DimensionsStorageGUI<DimensionsEnchantMergeMenu> {
    private static final ResourceLocation STD_BG = ResourceLocation.tryParse("beyond_integration:textures/gui/standard_background.png");
    private static final ResourceLocation SLOT_BTN = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button.png");
    private static final ResourceLocation SLOT_BTN_HOVER = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png");
    private static final ResourceLocation UP_ARROW = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/up_arrow.png");

    private static final int PANEL_W = 176;
    private static final int PANEL_H = 150;
    private static final int PAGE_SIZE = 5;
    private static final int ROW_H = 15;
    private static final int ROW_Y0 = 52;
    private static final int COL_CB = 6;
    private static final int COL_NAME = 18;
    private static final int COL_SLIDER = 64;
    private static final int SLIDER_W = 58;
    private static final int COL_LV = 124;
    private static final int COL_STOCK = 142;
    private static final int FOOT_Y = 131;

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private int page = 0;
    /** 已勾选：附魔注册 id -> 所选等级（键存在即选中） */
    private final Map<Integer, Integer> chosenLevels = new HashMap<>();

    private final CheckButton[] checks = new CheckButton[PAGE_SIZE];
    private final LevelSlider[] sliders = new LevelSlider[PAGE_SIZE];
    private Button mergeBtn;
    // 经验费用文本位置（用于悬浮 tooltip 命中）
    private int xpTextX, xpTextY, xpTextW;

    public DimensionsEnchantMergeGUI(DimensionsEnchantMergeMenu c, Inventory p, Component t) { super(c, p, t); }

    @Override protected int getPanelHeight() { return PANEL_H; }

    private static String roman(int n) {
        return n > 0 && n < ROMAN.length ? ROMAN[n] : String.valueOf(Math.max(1, n));
    }

    private MergeOption optionAt(int enchId) {
        for (MergeOption o : menu.options) if (o.enchId() == enchId) return o;
        return null;
    }

    /** 当前勾选按所选等级计算的网络 XP 消耗（mB / 20 = 经验点）；无对应等级书的高等级拆分项按配置的经验消耗计 */
    private long calcTotalXp() {
        long total = 0;
        for (Map.Entry<Integer, Integer> e : chosenLevels.entrySet()) {
            MergeOption o = optionAt(e.getKey());
            if (o == null) continue;
            int s = e.getValue();
            boolean exact = s >= 1 && s <= 63 && (o.levelMask() & (1L << (s - 1))) != 0;
            if (exact) {
                Enchantment ench = BuiltInRegistries.ENCHANTMENT.byId(o.enchId());
                if (ench == null) continue;
                total += EnchantmentBookSeparatorHandler.calcCost(
                        List.of(new EnchantmentInstance(ench, s)), 1);
            } else {
                Enchantment ench = BuiltInRegistries.ENCHANTMENT.byId(o.enchId());
                if (ench == null) continue;
                int from = 0;
                for (int lv = s + 1; lv <= o.maxLevel(); lv++) {
                    if ((o.levelMask() & (1L << (lv - 1))) != 0) { from = lv; break; }
                }
                int steps = from > s ? from - s : 0;
                // 合并到装备按公式计费 + 拆分步数费用（默认 0）
                total += EnchantmentBookSeparatorHandler.calcCost(
                        List.of(new EnchantmentInstance(ench, s)), 1)
                        + (long) CommandConfig.enchantMergeSplitXpCost() * steps * 20L;
            }
        }
        return total;
    }

    // ── 生命周期与刷新 ──
    @Override protected void init() {
        super.init();
        int gy = getGapY();
        for (int i = 0; i < PAGE_SIZE; i++) {
            int ry = gy + ROW_Y0 + i * ROW_H;
            checks[i] = new CheckButton(this.leftPos + COL_CB, ry + 2);
            sliders[i] = new LevelSlider(this.leftPos + COL_SLIDER, ry + 1, SLIDER_W, ROW_H - 3);
            addRenderableWidget(checks[i]);
            addRenderableWidget(sliders[i]);
        }
        addRenderableWidget(new DirButton(this.leftPos + 6, gy + FOOT_Y + 2, 270,
                b -> { if (page > 0) { page--; refreshRows(); } }));
        addRenderableWidget(new DirButton(this.leftPos + 44, gy + FOOT_Y + 2, 90,
                b -> {
                    int pages = Math.max(1, (menu.options.size() + PAGE_SIZE - 1) / PAGE_SIZE);
                    if (page < pages - 1) { page++; refreshRows(); }
                }));
        addRenderableWidget(new IconBtn(this.leftPos + 118, gy + FOOT_Y + 2, new ItemStack(Items.CHEST),
                Component.translatable("gui.beyond_integration.enchant_merge.select_all"), b -> {
            for (MergeOption o : menu.options) chosenLevels.putIfAbsent(o.enchId(), o.maxLevel());
            refreshRows();
        }));
        addRenderableWidget(new IconBtn(this.leftPos + 136, gy + FOOT_Y + 2, new ItemStack(Items.BUCKET),
                Component.translatable("gui.beyond_integration.enchant_merge.clear"), b -> {
            chosenLevels.clear();
            refreshRows();
        }));
        mergeBtn = addRenderableWidget(new IconBtn(this.leftPos + 154, gy + FOOT_Y + 2, new ItemStack(Items.ANVIL),
                Component.translatable("gui.beyond_integration.enchant_merge.merge"), b -> submit()));
        refreshRows();
    }

    /** 候选列表更新回调（网络包调用）：清理失效选择并夹取页码 */
    public void onOptionsUpdated() {
        Set<Integer> valid = new HashSet<>();
        for (MergeOption o : menu.options) valid.add(o.enchId());
        chosenLevels.keySet().removeIf(id -> !valid.contains(id));
        int pages = Math.max(1, (menu.options.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;
        if (checks[0] != null) refreshRows();
    }

    @Override public void containerTick() {
        super.containerTick();
        if (mergeBtn != null) mergeBtn.active = !chosenLevels.isEmpty();
    }

    private void refreshRows() {
        List<MergeOption> opts = menu.options;
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            if (checks[i] == null) return;
            int idx = start + i;
            if (idx >= opts.size()) {
                checks[i].visible = false;
                sliders[i].visible = false;
            } else {
                MergeOption o = opts.get(idx);
                checks[i].visible = true;
                checks[i].bind(o.enchId());
                sliders[i].visible = true;
                sliders[i].bind(o);
            }
        }
        if (mergeBtn != null) mergeBtn.active = !chosenLevels.isEmpty();
    }

    private void submit() {
        if (chosenLevels.isEmpty()) return;
        int n = chosenLevels.size();
        int[] ids = new int[n];
        int[] levels = new int[n];
        int k = 0;
        for (Map.Entry<Integer, Integer> e : chosenLevels.entrySet()) {
            ids[k] = e.getKey();
            levels[k] = e.getValue();
            k++;
        }
        PacketHandler.sendToServer(new SubmitEnchantMergePacket(menu.containerId, ids, levels));
    }

    // ── 面板渲染 ──
    @Override protected void renderWorkstationPanel(GuiGraphics g) {
        int gy = getGapY();
        var font = this.font;
        // 标准背景贴图（176×62 纵向拉伸至面板高度）
        g.blit(STD_BG, this.leftPos, gy, PANEL_W, PANEL_H, 0.0F, 0.0F, PANEL_W, 62, PANEL_W, 62);

        // 元素边框：装备信息区 / 列表区 / 操作栏
        drawBorder(g, this.leftPos + 3, gy + 10, PANEL_W - 6, 26, 0x50000000);
        drawBorder(g, this.leftPos + 3, gy + 38, PANEL_W - 6, 90, 0x50000000);
        drawBorder(g, this.leftPos + 3, gy + 130, PANEL_W - 6, 18, 0x50000000);

        g.drawString(font, Component.translatable("gui.beyond_integration.enchant_merge.title").getString(),
                this.leftPos + 6, gy + 4, 0x404040, false);

        drawSlot(g, this.leftPos + 7, gy + 14);
        ItemStack input = menu.getInput();
        if (input.isEmpty()) {
            g.drawString(font, Component.translatable("gui.beyond_integration.enchant_merge.empty").getString(),
                    this.leftPos + 30, gy + 19, 0x707070, false);
        } else {
            g.drawString(font, trim(font, input.getHoverName().getString(), 140), this.leftPos + 30, gy + 14, 0x404040, false);
        }
        // 经验消耗显示在物品名称下方（仅在勾选项时显示；悬浮显示明细）
        if (chosenLevels.isEmpty()) {
            this.xpTextW = 0;
        } else {
            String xp = Component.translatable("gui.beyond_integration.enchant_merge.xp_cost", calcTotalXp() / 20).getString();
            this.xpTextW = font.width(xp);
            this.xpTextX = this.leftPos + 30;
            this.xpTextY = gy + 24;
            g.drawString(font, xp, this.xpTextX, this.xpTextY, 8453920, false);
        }

        g.drawString(font, Component.translatable("gui.beyond_integration.enchant_merge.col.ench").getString(),
                this.leftPos + COL_NAME, gy + 39, 0x606060, false);
        g.drawString(font, Component.translatable("gui.beyond_integration.enchant_merge.col.level").getString(),
                this.leftPos + COL_SLIDER - 6, gy + 39, 0x606060, false);
        g.drawString(font, Component.translatable("gui.beyond_integration.enchant_merge.col.stock").getString(),
                this.leftPos + COL_STOCK, gy + 39, 0x606060, false);

        List<MergeOption> opts = menu.options;
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            if (idx >= opts.size()) break;
            MergeOption o = opts.get(idx);
            int ry = gy + ROW_Y0 + i * ROW_H;
            boolean sel = chosenLevels.containsKey(o.enchId());
            int bx = this.leftPos + COL_CB - 1;
            int bw = PANEL_W - 4 - (COL_CB - 1);
            if (sel) g.fill(bx, ry, bx + bw, ry + ROW_H - 1, 0x383DDC84);
            drawBorder(g, bx, ry, bw, ROW_H - 1, sel ? 0xFF3DDC84 : 0x40555555);

            Enchantment ench = BuiltInRegistries.ENCHANTMENT.byId(o.enchId());
            String name = ench != null ? Component.translatable(ench.getDescriptionId()).getString() : ("#" + o.enchId());
            int curLevel = chosenLevels.getOrDefault(o.enchId(), o.maxLevel());
            boolean upgrade = o.existing() > 0 && curLevel > o.existing();
            g.drawString(font, trim(font, name, upgrade ? 34 : 42), this.leftPos + COL_NAME, ry + 3, 0x404040, false);
            if (upgrade) g.drawString(font, "\u2191", this.leftPos + COL_NAME + 36, ry + 3, 0xB8860B, false);

            String lv = roman(curLevel);
            g.drawString(font, lv, this.leftPos + COL_LV + (14 - font.width(lv)) / 2 + 2, ry + 3, 0x404040, false);

            String st = "\u00d7" + o.stock();
            g.drawString(font, st, this.leftPos + COL_STOCK + 26 - font.width(st), ry + 3, 0x606060, false);
        }

        int pages = Math.max(1, (opts.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        String pageTxt = (page + 1) + "/" + pages;
        g.drawString(font, pageTxt, this.leftPos + 23 + (20 - font.width(pageTxt)) / 2, gy + FOOT_Y + 4, 0x404040, false);
    }

    private void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 19, y + 19, 0xFF373737);
        g.fill(x - 1, y - 1, x + 19, y, 0xFF8B8B8B);
        g.fill(x - 1, y - 1, x, y + 19, 0xFF8B8B8B);
        g.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
    }

    /** 细边框（1px；MC GUI 最小绘制单位为 1 像素） */
    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static String enchantSummary(ItemStack stack) {
        Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(stack);
        if (map.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Map.Entry<Enchantment, Integer> e : map.entrySet()) {
            if (n++ > 0) sb.append("  ");
            sb.append(Component.translatable(e.getKey().getDescriptionId()).getString()).append(' ').append(roman(e.getValue()));
            if (n >= 3) { sb.append(" ..."); break; }
        }
        return sb.toString();
    }

    private static String trim(net.minecraft.client.gui.Font font, String s, int maxWidth) {
        if (font.width(s) <= maxWidth) return s;
        String out = s;
        while (!out.isEmpty() && font.width(out + "...") > maxWidth) out = out.substring(0, out.length() - 1);
        return out + "...";
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        CostTooltipHelper.render(g, this.font, mx, my, xpTextX, xpTextY, xpTextW, 9, xpTooltip());
    }

    /** 经验费用悬浮框：计费公式 + 当前配置 + 逐项明细 */
    private List<Component> xpTooltip() {
        List<Component> list = new ArrayList<>();
        list.add(Component.translatable("gui.beyond_integration.enchant_merge.xp.tooltip").withStyle(ChatFormatting.GOLD));
        if (CommandConfig.useFormula()) {
            list.add(Component.translatable("gui.beyond_integration.enchant_merge.xp.formula_custom", CommandConfig.costFormula()));
        }
        for (Map.Entry<Integer, Integer> e : chosenLevels.entrySet()) {
            MergeOption o = optionAt(e.getKey());
            if (o == null) continue;
            int s = e.getValue();
            boolean exact = s >= 1 && s <= 63 && (o.levelMask() & (1L << (s - 1))) != 0;
            Enchantment ench = BuiltInRegistries.ENCHANTMENT.byId(o.enchId());
            if (ench == null) continue;
            if (exact) {
                long pts = EnchantmentBookSeparatorHandler.calcCost(
                        List.of(new EnchantmentInstance(ench, s)), 1) / 20;
                list.add(Component.translatable("gui.beyond_integration.enchant_merge.xp.item",
                        Component.translatable(ench.getDescriptionId()), roman(s), pts));
            } else {
                int from = 0;
                for (int lv = s + 1; lv <= o.maxLevel(); lv++) {
                    if ((o.levelMask() & (1L << (lv - 1))) != 0) { from = lv; break; }
                }
                int steps = from > s ? from - s : 0;
                long pts = EnchantmentBookSeparatorHandler.calcCost(
                        List.of(new EnchantmentInstance(ench, s)), 1) / 20
                        + (long) CommandConfig.enchantMergeSplitXpCost() * steps;
                int books = steps * (CommandConfig.enchantMergeConsumeBook() ? 2 : 1);
                list.add(Component.translatable("gui.beyond_integration.enchant_merge.xp.item_split",
                        Component.translatable(ench.getDescriptionId()), roman(s), pts, books));
            }
        }
        return list;
    }

    // ── widget ──
    /** 翻页方向按钮：BD slot_button 背景 + up_arrow 旋转成左右箭头 */
    private class DirButton extends Button {
        private final float angle;
        DirButton(int x, int y, float angle, OnPress onPress) {
            super(x, y, 16, 16, Component.empty(), onPress, DEFAULT_NARRATION);
            this.angle = angle;
        }
        @Override public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            var pose = g.pose();
            pose.pushPose();
            pose.translate(getX() + 8.0, getY() + 8.0, 0.0);
            pose.scale(0.85f, 0.85f, 1.0f);
            g.blit(this.isHovered ? SLOT_BTN_HOVER : SLOT_BTN, -8, -8, 0, 0, 16, 16, 16, 16);
            pose.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(angle)));
            g.blit(UP_ARROW, -8, -8, 0, 0, 16, 16, 16, 16);
            pose.popPose();
        }
    }

    /** 图标按钮：BD slot_button 背景 + 物品图标（代替文字） */
    private class IconBtn extends Button {
        private final ItemStack icon;
        IconBtn(int x, int y, ItemStack icon, Component tooltip, OnPress onPress) {
            super(x, y, 16, 16, Component.empty(), onPress, DEFAULT_NARRATION);
            this.icon = icon;
            setTooltip(Tooltip.create(tooltip));
        }
        @Override public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            var pose = g.pose();
            pose.pushPose();
            pose.translate(getX() + 8.0, getY() + 8.0, 0.0);
            pose.scale(0.85f, 0.85f, 1.0f);
            g.blit(this.isHovered ? SLOT_BTN_HOVER : SLOT_BTN, -8, -8, 0, 0, 16, 16, 16, 16);
            g.renderFakeItem(icon, -8, -8);
            pose.popPose();
        }
    }

    /** 勾选框：BD slot_button 贴图 + 勾选标记 */
    private class CheckButton extends Button {
        private int enchId = -1;
        CheckButton(int x, int y) {
            super(x, y, 10, 10, Component.empty(), b -> {}, DEFAULT_NARRATION);
        }
        void bind(int id) { this.enchId = id; }
        @Override public void onPress() {
            if (enchId < 0) return;
            if (chosenLevels.containsKey(enchId)) {
                chosenLevels.remove(enchId);
            } else {
                MergeOption o = optionAt(enchId);
                if (o != null) chosenLevels.put(enchId, o.maxLevel());
            }
            mergeBtn.active = !chosenLevels.isEmpty();
        }
        @Override public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            ItemStack icon = (enchId >= 0 && chosenLevels.containsKey(enchId))
                    ? new ItemStack(Items.LIME_DYE) : new ItemStack(Items.GRAY_DYE);
            var pose = g.pose();
            pose.pushPose();
            pose.translate(0.0, 0.5, 0.0); // 下移 1px（相对上次上移 0.5）
            g.blit(SLOT_BTN, getX(), getY(), 10, 10, 0.0F, 0.0F, 16, 16, 16, 16);
            pose.translate(getX() + 1, getY() + 1, 1);
            pose.scale(0.5f, 0.5f, 1.0f);
            g.renderFakeItem(icon, 0, 0);
            pose.popPose();
        }
    }

    /** 等级滑条：带档位刻度并在拖动时吸附到整数等级 */
    private class LevelSlider extends AbstractSliderButton {
        private int enchId = -1;
        private int maxLevel = 1;
        LevelSlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), 0.0); }
        void bind(MergeOption o) {
            this.enchId = o.enchId();
            this.maxLevel = Math.max(1, o.maxLevel());
            int lv = chosenLevels.getOrDefault(enchId, maxLevel);
            this.value = maxLevel <= 1 ? 0.5 : (lv - 1) / (double) (maxLevel - 1);
            this.active = maxLevel > 1; // 仅 I 级：单档不可拖动
        }
        int currentLevel() {
            return maxLevel <= 1 ? 1 : (int) Math.round(value * (maxLevel - 1)) + 1;
        }
        @Override public void updateMessage() {}
        @Override public void applyValue() {
            if (enchId < 0) return;
            int lv = currentLevel();
            this.value = maxLevel <= 1 ? 0.0 : (lv - 1) / (double) (maxLevel - 1); // 档位吸附
            chosenLevels.put(enchId, lv);
            if (mergeBtn != null) mergeBtn.active = !chosenLevels.isEmpty();
        }
        @Override public void onClick(double mouseX, double mouseY) {
            // 手柄中心范围覆盖整条轨道：鼠标 x 直接映射到 [0,1]
            this.value = Math.max(0.0, Math.min(1.0, (mouseX - getX()) / (double) getWidth()));
            applyValue();
        }
        @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            this.value = Math.max(0.0, Math.min(1.0, (mouseX - getX()) / (double) getWidth()));
            applyValue();
            return true;
        }
        @Override public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            int midY = y + h / 2;
            // 轨道
            g.fill(x, midY - 2, x + w, midY + 2, 0xFF373737);
            g.fill(x, midY - 1, x + w, midY + 1, 0xFF8B8B8B);
            int hx;
            if (maxLevel <= 1) {
                // 仅 I 级：固定 3 个刻度，手柄居中且不可移动
                for (int k = 0; k < 3; k++) {
                    int tx = x + (int) Math.round(k / 2.0 * w);
                    g.fill(tx, midY - 3, tx + 1, midY + 3, 0xFF555555);
                }
                hx = x + w / 2 - 3;
            } else {
                // 档位刻度：最小/最大分别位于轨道两端
                for (int k = 0; k < maxLevel; k++) {
                    int tx = x + (int) Math.round(k / (double) (maxLevel - 1) * w);
                    g.fill(tx, midY - 3, tx + 1, midY + 3, 0xFF555555);
                }
                // 滑块手柄：中心对准当前档位刻度
                hx = x + (int) Math.round(value * w) - 3;
            }
            int c = this.isHovered || isFocused() ? 0xFFFFFFFF : 0xFFC6C6C6;
            g.fill(hx, y + 1, hx + 6, y + h - 1, 0xFF555555);
            g.fill(hx + 1, y + 2, hx + 5, y + h - 2, c);
        }
    }
}
