package com.solr98.beyondintegration.client.gui;

// 注意：Beyond Dimensions 本体将在下个版本更换 UI 框架，本类依赖其现有 GUI 布局/坐标/纹理，
// 待 BD 正式发布后需校对代码与新版 GUI。


import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.ClientConfig;
import com.solr98.beyondintegration.feature.crafting.DimensionsEnchantMergeMenu;
import com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SubmitEnchantMergePacket;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;

/**
 * 批量附魔工作站界面（面板 176×150，纵向：装备槽 + 候选列表（5 行/页）+ 操作栏）。
 * <p><b>候选列表由客户端构建</b>：基于装备的可附魔列表（适用附魔）逐项显示，
 * 网络内没有对应单附魔书的项、与装备已有附魔冲突的项、以及无可提升项的显示为禁用状态且不可点击；
 * 装备已有该附魔时滑块默认停在已有等级刻度。服务端只接受提交的合并操作。</p>
 * 条目背景使用三态条目贴图（禁用/高亮/正常）；底部操作按钮使用 BD slot_button 贴图 + 物品图标。
 */
public class DimensionsEnchantMergeGUI extends DimensionsStorageGUI<DimensionsEnchantMergeMenu> {
    private static final ResourceLocation STD_BG = ResourceLocation.tryParse("beyond_integration:textures/gui/standard_background.png");
    private static final ResourceLocation SLOT_TEX = ResourceLocation.tryParse("beyond_integration:textures/gui/common_slots.png");
    private static final ResourceLocation SLOT_BTN = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button.png");
    private static final ResourceLocation SLOT_BTN_HOVER = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png");
    private static final ResourceLocation SLOT_BTN_DISABLED = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button_disabled.png");
    private static final ResourceLocation UP_ARROW = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/up_arrow.png");
    /** 附魔条目背景：200×48，自上而下 = 禁用(v=0) / 高亮(v=16) / 正常(v=32)，每条 16px */
    private static final ResourceLocation ENTRY_TEX = ResourceLocation.tryParse("beyond_integration:textures/gui/enchant_merge_entry.png");

    private static final int PANEL_W = 176;
    private static final int MAX_ROWS = 10;
    private static final int ROW_H = 16;
    private static final int ROW_Y0 = 30;
    private static final int COL_CB = 6;
    private static final int COL_NAME = 20;
    private static final int COL_SLIDER = 66;
    private static final int SLIDER_W = 80;
    private static final int COL_LV = 150;
    /** 网络视图刷新间隔（tick） */
    private static final int REBUILD_INTERVAL = 10;

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    /** 客户端候选：可附魔项 + 网络该书信息 + 装备已有等级 */
    public record Entry(Enchantment ench, int maxLevel, long levelMask, int existing, int stock, boolean enabled,
                        Map<Integer, Integer> books, ItemStack bookStack) {}

    private int page = 0;
    /** 已选中：附魔注册 id -> 所选等级（LinkedHashMap 记录启用顺序，用于按序写入附魔） */
    private final Map<Integer, Integer> chosenLevels = new LinkedHashMap<>();
    /** 滑块记忆等级：取消勾选时保留，重新勾选沿用（不影响复选框状态） */
    private final Map<Integer, Integer> sliderLevels = new LinkedHashMap<>();
    /** 当前客户端的可附魔候选（由 rebuild() 构建） */
    private final List<Entry> entries = new ArrayList<>();
    private ItemStack lastInput = ItemStack.EMPTY;
    private int rebuildCooldown = 0;

    private final CheckButton[] checks = new CheckButton[MAX_ROWS];
    private final LevelSlider[] sliders = new LevelSlider[MAX_ROWS];
    private Button mergeBtn;
    // 经验费用文本位置（用于悬浮 tooltip 命中）
    private int xpTextX, xpTextY, xpTextW;

    public DimensionsEnchantMergeGUI(DimensionsEnchantMergeMenu c, Inventory p, Component t) { super(c, p, t); }

    /** 每页候选行数（客户端配置，默认 5，最高 10；改动需重开界面生效）。 */
    private int pageSize() { return Math.min(MAX_ROWS, Math.max(1, ClientConfig.enchantMergeRows())); }
    private int rowBottom() { return ROW_Y0 + pageSize() * ROW_H; }
    private int footY() { return rowBottom() + 3; }

    @Override protected int getPanelHeight() { return menu.getPanelHeight(); }
    @Override protected int rebuildImageHeight() { int ph = getPanelHeight(); return 24 + 18 + (menu.getLines() - 2) * 18 + menu.bottomStripHeight() + ph + menu.connectionSeparatorHeight() + 89; }
    @Override protected void rebuildLabelHeight() { int ph = getPanelHeight(); this.titleLabelY = 8; this.inventoryLabelY = 24 + menu.getLines() * 18 + 5 + ph + menu.connectionSeparatorHeight(); }
    @Override protected int calMaxLines() { int ph = getPanelHeight(); return (int) ((this.height - 36 - (24 + 18 + menu.bottomStripHeight() + ph + menu.connectionSeparatorHeight() + 89)) / 18 + 2); }

    private static String roman(int n) {
        return n > 0 && n < ROMAN.length ? ROMAN[n] : String.valueOf(Math.max(1, n));
    }

    private Entry entryAt(int enchId) {
        for (Entry e : entries) if (BuiltInRegistries.ENCHANTMENT.getId(e.ench()) == enchId) return e;
        return null;
    }

    /** 是否选中：已有附魔默认选中（保留）；用户取消后为等级 0（清除） */
    private boolean isChecked(Entry e) {
        Integer v = chosenLevels.get(BuiltInRegistries.ENCHANTMENT.getId(e.ench()));
        if (v != null) return v > 0;
        return e.existing() > 0;
    }

    /** 滑块显示/提交等级：优先取滑块记忆等级，其次装备已有等级，最后网络最高等级（与是否勾选无关） */
    private int levelOf(Entry e) {
        Integer s = sliderLevels.get(BuiltInRegistries.ENCHANTMENT.getId(e.ench()));
        if (s != null) return s;
        return e.existing() > 0 ? e.existing() : Math.max(1, e.maxLevel());
    }

    /** 滑条范围：允许低于已有等级（降级）或高于（升级） */
    private int sliderMax(Entry e) {
        return Math.max(1, Math.max(e.maxLevel(), e.existing()));
    }

    /** 切换选中：已有附魔取消 → 等级 0（提交清除，不消耗/不返还书）；再次勾选恢复默认等级 */
    private void toggle(Entry e) {
        int id = BuiltInRegistries.ENCHANTMENT.getId(e.ench());
        if (isChecked(e)) chosenLevels.put(id, 0);
        else chosenLevels.put(id, levelOf(e));
        refreshConflicts();
        refreshRows();
    }

    /** 当前生效的附魔集合：装备已有（未被清除）+ 已勾选（等级>0） */
    private java.util.Set<Enchantment> activeEnchantments() {
        java.util.Set<Enchantment> active = new java.util.HashSet<>();
        for (Entry e : entries) {
            Integer v = chosenLevels.get(BuiltInRegistries.ENCHANTMENT.getId(e.ench()));
            boolean on = v == null ? e.existing() > 0 : v > 0;
            if (on) active.add(e.ench());
        }
        return active;
    }

    /** 目标附魔是否与集合中任一（除自身外）冲突 */
    private static boolean containsConflict(Enchantment ench, java.util.Set<Enchantment> active) {
        for (Enchantment h : active) {
            if (h != ench && !ench.isCompatibleWith(h)) return true;
        }
        return false;
    }

    /** 依据附魔选择列表刷新“冲突禁用”：已有附魔永不因冲突禁用，仅禁用与当前生效集合冲突的新附魔 */
    private void refreshConflicts() {
        if (!CommandConfig.enchantMergeCheckConflict()) return;
        java.util.Set<Enchantment> active = activeEnchantments();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            boolean enabled = e.existing() > 0
                    || (e.maxLevel() > e.existing() && !containsConflict(e.ench(), active));
            if (enabled != e.enabled()) {
                entries.set(i, new Entry(e.ench(), e.maxLevel(), e.levelMask(), e.existing(), e.stock(), enabled, e.books(), e.bookStack()));
            }
        }
    }

    // ── 候选构建（客户端）──
    /** 基于装备可附魔列表 + 客户端网络视图重建候选；清理失效选择并刷新绑定 */
    public void rebuild() {
        List<Entry> out = new ArrayList<>();
        ItemStack item = menu.getInput();
        if (!item.isEmpty() && DimensionsEnchantMergeMenu.beyond$canMergeItem(item)) {
            Map<Enchantment, Integer> existing = EnchantmentHelper.getEnchantments(item);
            // 网络单附魔书聚合：附魔 → 等级 → 数量；并记录最高等级那本的实际物品
            Map<Enchantment, Map<Integer, Integer>> bookCounts = new HashMap<>();
            Map<Enchantment, ItemStack> bestBooks = new HashMap<>();
            Map<Enchantment, Integer> bestLv = new HashMap<>();
            var storage = menu.clientNetStorage;
            if (storage != null) {
                for (KeyAmount ka : storage.getStorage()) {
                    if (!(ka.key() instanceof ItemStackKey ik)) continue;
                    ItemStack s = ik.getReadOnlyStack();
                    if (!(s.getItem() instanceof EnchantedBookItem)) continue;
                    List<EnchantmentInstance> list = EnchantmentBookSeparatorHandler.extractStoredEnchantments(s);
                    if (list.size() != 1) continue; // 仅单附魔书
                    EnchantmentInstance ei = list.get(0);
                    if (ei.level <= 0) continue;
                    int amt = (int) Math.min(Integer.MAX_VALUE, ka.amount());
                    bookCounts.computeIfAbsent(ei.enchantment, k -> new HashMap<>())
                            .merge(ei.level, amt, (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
                    if (ei.level > bestLv.getOrDefault(ei.enchantment, 0)) { bestLv.put(ei.enchantment, ei.level); bestBooks.put(ei.enchantment, s.copy()); }
                }
            }
            boolean checkConflict = CommandConfig.enchantMergeCheckConflict();
            boolean assumeAll = CommandConfig.enchantMergeAssumeAll();
            for (Enchantment ench : BuiltInRegistries.ENCHANTMENT) {
                if (!ench.isDiscoverable()) continue;
                if (!ench.canApplyAtEnchantingTable(item) && !ench.canEnchant(item)) continue; // 仅列可附魔项
                int cur = existing.getOrDefault(ench, 0);
                boolean conflict = false;
                if (cur == 0 && checkConflict) {
                    for (Enchantment ex : existing.keySet()) {
                        if (!ench.isCompatibleWith(ex)) { conflict = true; break; }
                    }
                }
                Map<Integer, Integer> byLevel = bookCounts.get(ench);
                long mask = 0; int maxLevel = 0; int stock = 0;
                if (byLevel != null) {
                    for (Map.Entry<Integer, Integer> en : byLevel.entrySet()) {
                        int lv = en.getKey();
                        if (lv >= 1 && lv <= 63) mask |= 1L << (lv - 1);
                        if (lv > maxLevel) { maxLevel = lv; stock = en.getValue(); }
                        else if (lv == maxLevel) stock = (int) Math.min(Integer.MAX_VALUE, (long) stock + en.getValue());
                    }
                }
                // 视为拥有：网络无书时用附魔自身最大等级（按高额经验计费）
                if (assumeAll && mask == 0) {
                    maxLevel = Math.max(maxLevel, ench.getMaxLevel());
                }
                // 可交互：已有附魔可降级/清除；或可升级且不冲突
                boolean interactive = cur > 0 || (maxLevel > cur && !conflict);
                out.add(new Entry(ench, maxLevel, mask, cur, stock, interactive, byLevel == null ? Map.of() : byLevel,
                        bestBooks.getOrDefault(ench, ItemStack.EMPTY)));
            }
        }
        entries.clear();
        entries.addAll(out);
        refreshConflicts(); // 依据当前选择刷新冲突禁用
        // 清理失效/禁用选择
        chosenLevels.keySet().removeIf(id -> {
            Entry e = entryAt(id);
            return e == null || !e.enabled();
        });
        int pages = Math.max(1, (entries.size() + pageSize() - 1) / pageSize());
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;
        if (sliders[0] != null) refreshRows();
        if (mergeBtn != null) mergeBtn.active = !chosenLevels.isEmpty();
    }

    // ── 生命周期与刷新 ──
    @Override protected void init() {
        super.init();
        int gy = getGapY();
        for (int i = 0; i < pageSize(); i++) {
            int ry = gy + ROW_Y0 + i * ROW_H;
            checks[i] = new CheckButton(this.leftPos + COL_CB, ry + 2);
            sliders[i] = new LevelSlider(this.leftPos + COL_SLIDER, ry + 1, SLIDER_W, ROW_H - 3);
            addRenderableWidget(checks[i]);
            addRenderableWidget(sliders[i]);
        }
        addRenderableWidget(new DirButton(this.leftPos + 6,         gy + footY() - 1, 270,
                b -> { if (page > 0) { page--; refreshRows(); } }));
        addRenderableWidget(new DirButton(this.leftPos + 44,         gy + footY() - 1, 90,
                b -> {
                    int pages = Math.max(1, (entries.size() + pageSize() - 1) / pageSize());
                    if (page < pages - 1) { page++; refreshRows(); }
                }));
        addRenderableWidget(new IconBtn(this.leftPos + 100,         gy + footY() - 1, new ItemStack(Items.NETHER_STAR),
                Component.translatable("gui.beyond_integration.enchant_merge.max_level"), b -> {
            java.util.Set<Enchantment> picked = activeEnchantments();
            boolean checkConflict = CommandConfig.enchantMergeCheckConflict();
            for (Entry e : entries) {
                if (!e.enabled()) continue;
                int max = sliderMax(e);
                if (max <= e.existing()) continue;                                // 无可提升
                if (checkConflict && containsConflict(e.ench(), picked)) continue;  // 跳过与已选/已有冲突项
                int id = BuiltInRegistries.ENCHANTMENT.getId(e.ench());
                chosenLevels.put(id, max);
                sliderLevels.put(id, max);
                picked.add(e.ench());
            }
            refreshConflicts();
            refreshRows();
        }));
        addRenderableWidget(new IconBtn(this.leftPos + 118,         gy + footY() - 1, new ItemStack(Items.BUCKET),
                Component.translatable("gui.beyond_integration.enchant_merge.clear"), b -> {
            chosenLevels.clear();
            for (Entry e : entries) {
                // 清除：已有附魔标记为移除（等级 0），提交后清空装备附魔
                if (e.existing() > 0) chosenLevels.put(BuiltInRegistries.ENCHANTMENT.getId(e.ench()), 0);
            }
            refreshConflicts();
            refreshRows();
        }));
        addRenderableWidget(new IconBtn(this.leftPos + 136,         gy + footY() - 1, new ItemStack(Items.RECOVERY_COMPASS),
                Component.translatable("gui.beyond_integration.enchant_merge.reset"), b -> {
            chosenLevels.clear(); // 还原：回到装备已有附魔的初始选择
            sliderLevels.clear();
            refreshConflicts();
            refreshRows();
        }));
        mergeBtn = addRenderableWidget(new IconBtn(this.leftPos + 154,         gy + footY() - 1, new ItemStack(Items.ANVIL),
                Component.translatable("gui.beyond_integration.enchant_merge.merge"), b -> submit()));
        rebuild();
    }

    @Override public void containerTick() {
        super.containerTick();
        ItemStack input = menu.getInput();
        if (!ItemStack.matches(lastInput, input) || ++rebuildCooldown >= REBUILD_INTERVAL) {
            lastInput = input.copy();
            rebuildCooldown = 0;
            rebuild();
        }
        if (mergeBtn != null) {
            mergeBtn.active = !chosenLevels.isEmpty();
            mergeBtn.setTooltip(Tooltip.create(joinLines(mergePreview())));
        }
    }

    /** 合并结果预览：渲染“操作后物品”的悬浮框，并在原附魔条目行尾以彩色后缀标出变化（[↑ 旧→新]/[↓ 旧→新]/[+]；移除项 ✗ 删除线） */
    private List<Component> mergePreview() {
        ItemStack input = menu.getInput();
        if (input.isEmpty()) return java.util.Collections.emptyList();
        Map<Enchantment, Integer> target = new LinkedHashMap<>(EnchantmentHelper.getEnchantments(input));
        for (Map.Entry<Integer, Integer> e : chosenLevels.entrySet()) {
            Enchantment ench = BuiltInRegistries.ENCHANTMENT.byId(e.getKey());
            if (ench == null) continue;
            int lv = e.getValue();
            if (lv > 0) target.put(ench, lv); else target.remove(ench); // 0 = 清除该附魔
        }
        // 与服务端一致：按等级降序写入
        List<Map.Entry<Enchantment, Integer>> merged = new ArrayList<>(target.entrySet());
        merged.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        Map<Enchantment, Integer> sorted = new LinkedHashMap<>();
        for (Map.Entry<Enchantment, Integer> en : merged) sorted.put(en.getKey(), en.getValue());
        ItemStack result = input.copy();
        EnchantmentHelper.setEnchantments(sorted, result);
        List<Component> lines = new ArrayList<>(
                result.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.Default.NORMAL));

        // 变化后缀：以附魔行文本为键，在行尾追加彩色标注；被移除项以删除线追加
        Map<String, Component> suffixes = new LinkedHashMap<>();
        List<Component> removed = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : chosenLevels.entrySet()) {
            Entry o = entryAt(e.getKey());
            if (o == null) continue;
            int ex = o.existing();
            int lv = e.getValue();
            if (lv <= 0) {
                if (ex > 0) {
                    removed.add(Component.literal("\u2717 ").withStyle(ChatFormatting.RED).append(
                            o.ench().getFullname(ex).copy().withStyle(ChatFormatting.STRIKETHROUGH)));
                }
                continue;
            }
            if (lv == ex) continue;
            String full = o.ench().getFullname(lv).getString();
            Component suffix;
            if (ex == 0) suffix = Component.literal(" [+]").withStyle(ChatFormatting.GREEN);
            else if (lv > ex) suffix = Component.literal(" [\u2191 " + roman(ex) + "\u2192" + roman(lv) + "]").withStyle(ChatFormatting.GREEN);
            else suffix = Component.literal(" [\u2193 " + roman(ex) + "\u2192" + roman(lv) + "]").withStyle(ChatFormatting.RED);
            suffixes.put(full, suffix);
        }
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i).getString();
            for (Map.Entry<String, Component> m : suffixes.entrySet()) {
                if (s.startsWith(m.getKey())) { lines.set(i, lines.get(i).copy().append(m.getValue().copy())); break; }
            }
        }
        lines.addAll(removed);
        return lines;
    }

    private static Component joinLines(List<Component> lines) {
        var out = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out.append(Component.literal("\n"));
            out.append(lines.get(i));
        }
        return out;
    }

    private void refreshRows() {
        int start = page * pageSize();
        for (int i = 0; i < pageSize(); i++) {
            if (checks[i] == null || sliders[i] == null) return;
            int idx = start + i;
            if (idx >= entries.size()) {
                checks[i].visible = false;
                sliders[i].visible = false;
            } else {
                checks[i].visible = true;
                checks[i].bind(entries.get(idx));
                sliders[i].visible = true;
                sliders[i].bind(entries.get(idx));
            }
        }
        if (mergeBtn != null) mergeBtn.active = !chosenLevels.isEmpty();
    }

    private void submit() {
        if (chosenLevels.isEmpty()) return;
        List<Integer> ids = new ArrayList<>();
        List<Integer> levels = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : chosenLevels.entrySet()) {
            Entry en = entryAt(e.getKey());
            if (en == null) continue;
            int lv = e.getValue();
            if (lv <= 0) {
                if (en.existing() > 0) { ids.add(e.getKey()); levels.add(0); } // 清除该附魔
            } else {
                ids.add(e.getKey());
                levels.add(Math.min(lv, sliderMax(en)));
            }
        }
        if (ids.isEmpty()) return;
        int n = ids.size();
        int[] a = new int[n];
        int[] b = new int[n];
        for (int i = 0; i < n; i++) { a[i] = ids.get(i); b[i] = levels.get(i); }
        PacketHandler.sendToServer(new SubmitEnchantMergePacket(menu.containerId, a, b));
    }

    // ── 面板渲染 ──
    @Override protected void renderWorkstationPanel(GuiGraphics g) {
        int gy = getGapY();
        var font = this.font;
        // 标准背景贴图（176×62 纵向拉伸至面板高度）
        g.blit(STD_BG, this.leftPos, gy, PANEL_W, getPanelHeight(), 0.0F, 0.0F, PANEL_W, 62, PANEL_W, 62);

        g.drawString(font, Component.translatable("gui.beyond_integration.enchant_merge.title").getString(),
                this.leftPos + 6, gy - 8, 0x404040, false);

        drawSlot(g, this.leftPos + 7, gy + 10);
        ItemStack input = menu.getInput();
        if (input.isEmpty()) {
            g.drawString(font, Component.translatable("gui.beyond_integration.enchant_merge.empty").getString(),
                    this.leftPos + 30, gy + 15, 0x707070, false);
        } else {
            g.drawString(font, trim(font, input.getHoverName().getString(), 140), this.leftPos + 30, gy + 10, 0x404040, false);
        }
        // 经验消耗显示在物品名称下方（仅在选中项时显示；悬浮显示明细）
        if (chosenLevels.isEmpty()) {
            this.xpTextW = 0;
        } else {
            String xp = Component.translatable("gui.beyond_integration.enchant_merge.xp_cost", calcTotalXp() / 20).getString();
            this.xpTextW = font.width(xp);
            this.xpTextX = this.leftPos + 30;
            this.xpTextY = gy + 19;
            // 与铁砧同款暗色背景，突出经验费用
            g.fill(this.xpTextX - 2, this.xpTextY - 2, this.xpTextX + this.xpTextW + 2, this.xpTextY + 10, 0x4F000000);
            g.drawString(font, xp, this.xpTextX, this.xpTextY, 8453920, false);
        }

        int start = page * pageSize();
        for (int i = 0; i < pageSize(); i++) {
            int idx = start + i;
            if (idx >= entries.size()) break;
            Entry e = entries.get(idx);
            int id = BuiltInRegistries.ENCHANTMENT.getId(e.ench());
            int ry = gy + ROW_Y0 + i * ROW_H;
            int bx = this.leftPos + 3;
            int bw = PANEL_W - 6;
            boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= ry && mouseY < ry + ROW_H;
            // 贴图布局：左 16px = 复选框按钮（选中 v=16 / 未选 v=32 / 禁用 v=0），右 184px = 条目背景（悬停 v=16 / 正常 v=32 / 禁用 v=0）
            int vBtn = !e.enabled() ? 0 : (isChecked(e) ? 16 : 32); // 禁用仅体现在左侧按钮
            int vRow = hover ? 16 : 32;                            // 条目背景保持正常，不随禁用隐藏
            g.blit(ENTRY_TEX, bx, ry, 16, ROW_H, 0.0F, (float) vBtn, 16, 16, 200, 48);
            g.blit(ENTRY_TEX, bx + 16, ry, bw - 16, ROW_H, 16.0F, (float) vRow, 184, 16, 200, 48);

            int nameColor = 0x1A1A1A; // 禁用不隐藏文本/等级
            g.drawString(font, trim(font, Component.translatable(e.ench().getDescriptionId()).getString(), 44),
                    this.leftPos + COL_NAME, ry + 4, nameColor, false);

            int curLevel = levelOf(e);
            String lv = roman(curLevel);
            g.drawString(font, lv, this.leftPos + COL_LV + (14 - font.width(lv)) / 2 + 2, ry + 4, nameColor, false);
        }

        int pages = Math.max(1, (entries.size() + pageSize() - 1) / pageSize());
        String pageTxt = (page + 1) + "/" + pages;
        g.drawString(font, pageTxt, this.leftPos + 23 + (20 - font.width(pageTxt)) / 2, gy + footY() + 3, 0x404040, false);
    }

    private void drawSlot(GuiGraphics g, int x, int y) {
        g.blit(SLOT_TEX, x, y, 18, 18, 0.0F, 0.0F, 18, 18, 18, 18);
    }

    private static String trim(net.minecraft.client.gui.Font font, String s, int maxWidth) {
        if (font.width(s) <= maxWidth) return s;
        String out = s;
        while (!out.isEmpty() && font.width(out + "...") > maxWidth) out = out.substring(0, out.length() - 1);
        return out + "...";
    }

    // ── 经验费用（客户端按候选与选择计算）──
    private long calcTotalXp() {
        long total = 0;
        long extra = CommandConfig.enchantMergeExtraCostPerEnchant();
        for (Map.Entry<Integer, Integer> e : chosenLevels.entrySet()) {
            Entry en = entryAt(e.getKey());
            if (en == null || !en.enabled()) continue;
            int s = e.getValue();
            if (s <= en.existing()) continue; // 降级/清除免费，且不涉及附魔书
            long mergeCost = EnchantmentBookSeparatorHandler.calcCost(
                    List.of(new EnchantmentInstance(en.ench(), s)), 1);
            if (en.levelMask() == 0) {
                // 视为拥有：无书，按高额经验计费
                total += mergeCost + CommandConfig.enchantMergeAssumeAllExtraCost() + extra;
                continue;
            }
            boolean exact = s >= 1 && s <= 63 && (en.levelMask() & (1L << (s - 1))) != 0;
            if (exact) {
                total += mergeCost + extra;
            } else {
                int from = 0;
                for (int lv = s + 1; lv <= en.maxLevel(); lv++) {
                    if ((en.levelMask() & (1L << (lv - 1))) != 0) { from = lv; break; }
                }
                int steps = from > s ? from - s : 0;
                total += mergeCost + (long) CommandConfig.enchantMergeSplitXpCost() * steps * 20L + extra;
            }
        }
        return scaleCost(total);
    }

    /** 费用缩放：统一乘倍率与百分比加成。 */
    private static long scaleCost(long base) {
        double mult = CommandConfig.enchantMergeCostMultiplier();
        int pct = CommandConfig.enchantMergeCostPercentBonus();
        return Math.max(0L, Math.round(base * mult * (1.0D + pct / 100.0D)));
    }

    // ── 交互 ──
    @Override public boolean mouseClicked(double mx, double my, int button) {
        // 条目点击需在 super 之前处理：BD 容器/滚动条逻辑可能先行消费点击。
        // 滑条区域让给滑条（拖拽调级），其余条目区域切换选中/取消（仅启用项可点）。
        if (button == 0) {
            int gy = getGapY();
            if (mx >= this.leftPos + 3 && mx < this.leftPos + PANEL_W - 3
                    && my >= gy + ROW_Y0 && my < gy + ROW_Y0 + pageSize() * ROW_H) {
                boolean onSlider = false;
                for (LevelSlider s : sliders) {
                    if (s != null && s.visible && mx >= s.getX() && mx < s.getX() + s.getWidth()
                            && my >= s.getY() && my < s.getY() + s.getHeight()) { onSlider = true; break; }
                }
                // 复选框区域交给 CheckButton widget 处理
                int i0 = (int) ((my - (gy + ROW_Y0)) / ROW_H);
                int cbY = gy + ROW_Y0 + i0 * ROW_H + 1;
                boolean onCheck = i0 >= 0 && i0 < pageSize()
                        && mx >= this.leftPos + COL_CB && mx < this.leftPos + COL_CB + 11
                        && my >= cbY && my < cbY + 13;
                if (!onSlider && !onCheck) {
                    int i = i0;
                    int idx = page * pageSize() + i;
                    if (i >= 0 && i < pageSize() && idx < entries.size()) {
                        Entry e = entries.get(idx);
                        if (e.enabled()) {
                            toggle(e);
                            return true;
                        }
                        return true; // 禁用项吞掉点击，不穿透
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        // 面板区域优先处理：BD 的存储滚动条可能全局消费滚轮，需在 super 之前拦截。
        // 滑条上 = 增减该附魔等级；其余面板区域 = 翻页。
        int gy = getGapY();
        if (mx >= this.leftPos && mx < this.leftPos + PANEL_W && my >= gy && my < gy + getPanelHeight()) {
            for (LevelSlider s : sliders) {
                if (s != null && s.mouseScrolled(mx, my, delta)) return true;
            }
            if (delta > 0) {
                if (page > 0) { page--; refreshRows(); }
            } else if (delta < 0) {
                int pages = Math.max(1, (entries.size() + pageSize() - 1) / pageSize());
                if (page < pages - 1) { page++; refreshRows(); }
            }
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        CostTooltipHelper.render(g, this.font, mx, my, xpTextX, xpTextY, xpTextW, 9, xpTooltip());
        renderEntryTooltip(g, mx, my);
    }

    /** 悬浮附魔条目：以网络内最高等级附魔书物品的 tooltip 为本体，后接库存数字（横向）。 */
    private void renderEntryTooltip(GuiGraphics g, int mx, int my) {
        int gy = getGapY();
        if (mx < this.leftPos + 3 || mx >= this.leftPos + PANEL_W - 3) return;
        if (my < gy + ROW_Y0 || my >= gy + ROW_Y0 + pageSize() * ROW_H) return;
        int i = (int) ((my - (gy + ROW_Y0)) / ROW_H);
        int idx = page * pageSize() + i;
        if (i < 0 || i >= pageSize() || idx >= entries.size()) return;
        Entry e = entries.get(idx);
        // 本体：对应附魔书物品的 tooltip（网络内最高等级那本；无则按最高等级合成一本）
        ItemStack book = e.bookStack();
        if (book.isEmpty()) {
            book = new ItemStack(Items.ENCHANTED_BOOK);
            EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(e.ench(), Math.max(1, e.maxLevel())));
        }
        List<Component> lines = new ArrayList<>(book.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.Default.NORMAL));
        // 库存数字：按等级降序 罗马×数量，横向一行
        if (e.books().isEmpty()) {
            lines.add(Component.translatable("gui.beyond_integration.enchant_merge.tip.no_book").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("gui.beyond_integration.enchant_merge.tip.header").withStyle(ChatFormatting.GRAY));
            List<Integer> levels = new ArrayList<>(e.books().keySet());
            levels.sort(java.util.Comparator.reverseOrder());
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (int lv : levels) {
                if (n > 0) sb.append("  ");
                sb.append(roman(lv)).append("×").append(e.books().get(lv));
                if (++n == 5) { lines.add(Component.literal(sb.toString()).withStyle(ChatFormatting.WHITE)); sb.setLength(0); n = 0; }
            }
            if (n > 0) lines.add(Component.literal(sb.toString()).withStyle(ChatFormatting.WHITE));
        }
        g.renderComponentTooltip(this.font, lines, mx, my);
    }

    /** 经验费用悬浮框：计费公式 + 逐项明细（拆分行含普通书消耗） */
    private List<Component> xpTooltip() {
        List<Component> list = new ArrayList<>();
        list.add(Component.translatable("gui.beyond_integration.enchant_merge.xp.tooltip").withStyle(ChatFormatting.GOLD));
        if (CommandConfig.useFormula()) {
            list.add(Component.translatable("gui.beyond_integration.enchant_merge.xp.formula_custom", CommandConfig.costFormula()));
        }
        long extra = CommandConfig.enchantMergeExtraCostPerEnchant();
        for (Map.Entry<Integer, Integer> e : chosenLevels.entrySet()) {
            Entry en = entryAt(e.getKey());
            if (en == null || !en.enabled()) continue;
            int s = e.getValue();
            if (s <= en.existing()) continue; // 降级/清除免费
            long mergeCost = EnchantmentBookSeparatorHandler.calcCost(
                    List.of(new EnchantmentInstance(en.ench(), s)), 1);
            if (en.levelMask() == 0) {
                long pts = scaleCost(mergeCost + CommandConfig.enchantMergeAssumeAllExtraCost() + extra) / 20;
                list.add(Component.translatable("gui.beyond_integration.enchant_merge.xp.item_nobook",
                        Component.translatable(en.ench().getDescriptionId()), roman(s), pts));
                continue;
            }
            boolean exact = s >= 1 && s <= 63 && (en.levelMask() & (1L << (s - 1))) != 0;
            if (exact) {
                long pts = scaleCost(mergeCost + extra) / 20;
                list.add(Component.translatable("gui.beyond_integration.enchant_merge.xp.item",
                        Component.translatable(en.ench().getDescriptionId()), roman(s), pts));
            } else {
                int from = 0;
                for (int lv = s + 1; lv <= en.maxLevel(); lv++) {
                    if ((en.levelMask() & (1L << (lv - 1))) != 0) { from = lv; break; }
                }
                int steps = from > s ? from - s : 0;
                long base = mergeCost + (long) CommandConfig.enchantMergeSplitXpCost() * steps * 20L + extra;
                long pts = scaleCost(base) / 20;
                int books = steps * (CommandConfig.enchantMergeConsumeBook() ? 2 : 1);
                list.add(Component.translatable("gui.beyond_integration.enchant_merge.xp.item_split",
                        Component.translatable(en.ench().getDescriptionId()), roman(s), pts, books));
            }
        }
        // 附加费用提示（仅在非默认时显示）
        double mult = CommandConfig.enchantMergeCostMultiplier();
        int pct = CommandConfig.enchantMergeCostPercentBonus();
        if (extra > 0 || mult != 1.0D || pct != 0) {
            list.add(Component.translatable("gui.beyond_integration.enchant_merge.xp.modifiers",
                    extra, multipToString(mult), pct).withStyle(ChatFormatting.DARK_GRAY));
        }
        long total = calcTotalXp() / 20;
        if (total > 0) {
            list.add(Component.translatable("gui.beyond_integration.enchant_merge.xp.level_equiv",
                    levelFromXp(total)).withStyle(ChatFormatting.GRAY));
        }
        return list;
    }

    private static String multipToString(double v) {
        return v == Math.floor(v) && !Double.isInfinite(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** 由经验点估算“玩家从 0 级升到该级”对应的等级（不超过 xp 的最大等级） */
    private static int levelFromXp(long xp) {
        int level = 0;
        long remaining = xp;
        while (level < 100000) {
            long need = level <= 15 ? 2L * level + 7 : (level <= 30 ? 5L * level - 38 : 9L * level - 158);
            if (remaining < need) break;
            remaining -= need;
            level++;
        }
        return level;
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

    /** 复选框热区：外观由条目贴图左侧 16px 绘制，本控件仅负责点击切换 */
    private class CheckButton extends Button {
        private int enchId = -1;
        private boolean allowed = false;
        CheckButton(int x, int y) {
            super(x, y, 11, 11, Component.empty(), b -> {}, DEFAULT_NARRATION);
        }
        void bind(Entry e) {
            this.enchId = BuiltInRegistries.ENCHANTMENT.getId(e.ench());
            this.allowed = e.enabled();
            this.active = allowed;
        }
        @Override public void onPress() {
            if (enchId < 0 || !allowed) return;
            Entry e = entryAt(enchId);
            if (e != null) toggle(e);
        }
        @Override public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            // 按钮外观由条目贴图左侧 16px 绘制；此处叠加状态物品图标（禁用=barrier / 已选=附魔书(带光效) / 未选=书）
            Entry e = enchId >= 0 ? entryAt(enchId) : null;
            if (e == null) return;
            ItemStack icon;
            if (!allowed) {
                icon = new ItemStack(Items.BARRIER);
            } else if (isChecked(e)) {
                icon = new ItemStack(Items.ENCHANTED_BOOK);
                EnchantedBookItem.addEnchantment(icon, new EnchantmentInstance(e.ench(), 1)); // 真实附魔书，自带光效
            } else {
                icon = new ItemStack(Items.BOOK);
            }
            var pose = g.pose();
            pose.pushPose();
            pose.translate(getX() + getWidth() / 2.0, getY() + getHeight() / 2.0, 0.0);
            pose.scale(0.7f, 0.7f, 1.0f);
            g.renderFakeItem(icon, -8, -8);
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

    /** 等级滑条：带档位刻度并在拖动/滚轮时吸附到整数等级；禁用的附魔项不可操作 */
    private class LevelSlider extends AbstractSliderButton {
        private int enchId = -1;
        private int maxLevel = 1;
        private boolean allowed = false;
        LevelSlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), 0.0); }
        void bind(Entry e) {
            this.enchId = BuiltInRegistries.ENCHANTMENT.getId(e.ench());
            this.maxLevel = sliderMax(e);
            this.allowed = e.enabled();
            int lv = Math.min(levelOf(e), maxLevel);
            this.value = maxLevel <= 1 ? 0.5 : (lv - 1) / (double) (maxLevel - 1);
            this.active = allowed && maxLevel > 1; // 未启用/单档不可拖动
        }
        int currentLevel() {
            return maxLevel <= 1 ? 1 : (int) Math.round(value * (maxLevel - 1)) + 1;
        }
        @Override public void updateMessage() {}
        @Override public void applyValue() {
            if (enchId < 0 || !allowed) return;
            int lv = currentLevel();
            this.value = maxLevel <= 1 ? 0.0 : (lv - 1) / (double) (maxLevel - 1); // 档位吸附
            sliderLevels.put(enchId, lv);
            chosenLevels.put(enchId, lv);
            refreshConflicts();
            refreshRows();
        }
        @Override public void onClick(double mouseX, double mouseY) {
            if (!allowed) return;
            this.value = Math.max(0.0, Math.min(1.0, (mouseX - getX()) / (double) getWidth()));
            applyValue();
        }
        @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (!allowed) return false;
            this.value = Math.max(0.0, Math.min(1.0, (mouseX - getX()) / (double) getWidth()));
            applyValue();
            return true;
        }
        /** 鼠标位于滑条上时滚轮增减一级（到边界仍消费，避免误触翻页） */
        @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (maxLevel <= 1 || !this.visible || !allowed) return false;
            if (mouseX < getX() || mouseX >= getX() + getWidth()
                    || mouseY < getY() || mouseY >= getY() + getHeight()) return false;
            int lv = currentLevel();
            int nl = Math.max(1, Math.min(maxLevel, lv + (delta > 0 ? 1 : -1)));
            if (nl == lv) return true;
            this.value = (nl - 1) / (double) (maxLevel - 1);
            applyValue();
            return true;
        }
        @Override public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            int midY = y + h / 2;
            int trackDark = allowed ? 0xFF373737 : 0xFF555555;
            int trackLight = allowed ? 0xFF8B8B8B : 0xFF777777;
            g.fill(x, midY - 2, x + w, midY + 2, trackDark);
            g.fill(x, midY - 1, x + w, midY + 1, trackLight);
            int hx;
            if (maxLevel <= 1) {
                for (int k = 0; k < 3; k++) {
                    int tx = x + (int) Math.round(k / 2.0 * w);
                    g.fill(tx, midY - 3, tx + 1, midY + 3, 0xFF555555);
                }
                hx = x + w / 2 - 3;
            } else {
                for (int k = 0; k < maxLevel; k++) {
                    int tx = x + (int) Math.round(k / (double) (maxLevel - 1) * w);
                    g.fill(tx, midY - 3, tx + 1, midY + 3, 0xFF555555);
                }
                hx = x + (int) Math.round(value * w) - 3;
            }
            int c = !allowed ? 0xFF9A9A9A : (this.isHovered || isFocused() ? 0xFFFFFFFF : 0xFFC6C6C6);
            g.fill(hx, y + 1, hx + 6, y + h - 1, 0xFF555555);
            g.fill(hx + 1, y + 2, hx + 5, y + h - 2, c);
        }
    }
}
