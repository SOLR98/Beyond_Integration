package com.solr98.beyondintegration.client.gui;

// 注意：Beyond Dimensions 本体将在下个版本更换 UI 框架，本类依赖其现有 GUI 布局/坐标/纹理，
// 待 BD 正式发布后需校对代码与新版 GUI。


import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SubmitFtbRewardSelectionPacket;
import dev.ftb.mods.ftblibrary.icon.Icon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * FTB 奖励领取选择界面（客户端，卡片网格 + 详情侧栏）：
 * 按章节/任务列出当前所有可领取奖励（未领取且未排除"领取所有"）。
 * <p>
 * 视图：
 * <ul>
 *   <li><b>合并视图</b>：同款奖励（相同物品栈或相同标题）聚合为一张卡片，数量累加；</li>
 *   <li><b>展开视图</b>：每个原始奖励一张卡片（来源为单个任务）。</li>
 * </ul>
 * 勾选与去向按奖励 ID 存储，切换视图不丢失；合并卡片上的操作批量应用到其全部来源。
 * <p>
 * 详情侧栏：大图标 + 名称/总数量 + 去向切换 + 相关任务列表（按章节分组，章节头含数量小计，可滚动）。
 * 工具栏：搜索、排序（名称/数量/来源数）、视图切换；网格可滚动。
 * <p>
 * 确认后经 {@code SubmitFtbRewardSelectionPacket} 提交（按当前勾选展开为全部奖励 ID），服务端逐条领取。
 * 触发：任务界面 Shift+点击"收集奖励"。取消/领取/ESC 后返回打开前的界面。
 */
public class FtbRewardSelectScreen extends Screen {

    /** 原始奖励条目（展开视图的卡片；同时作为合并来源）；mergeKey 决定同款合并分组 */
    public record RawEntry(long id, String title, long count, Icon icon,
                           String questTitle, String chapterTitle, boolean networkCapable, String mergeKey) {}

    /** 合并后的奖励条目：同款奖励聚合；ids 为全部来源奖励 ID */
    public record Entry(List<Long> ids, String title, long totalCount, Icon icon,
                        boolean networkCapable, List<RawEntry> sources) {}

    /** 奖励条目上限（与服务端一致；按原始奖励数计） */
    public static final int MAX_ENTRIES = 128;

    private static final int PANEL_W = 480;
    private static final int DETAIL_W = 150;
    private static final int CARD_W = 54;
    private static final int CARD_H = 52;
    private static final int CARD_GAP = 4;
    private static final int LIST_ROW_H = 18;
    private static final int TOOLBAR_H = 22;
    /** 底部区高度（汇总行 + 按钮行；含区域边框留白） */
    private static final int FOOTER_H = 40;

    private final List<RawEntry> rawEntries;
    private final List<Entry> mergedEntries;
    private final Set<Long> selectedIds = new HashSet<>();
    private final Map<Long, Boolean> destNetwork = new LinkedHashMap<>();
    private final Screen parent;

    /** 视图：true = 合并，false = 展开 */
    private boolean mergedView = true;
    /** 样式：true = 列表，false = 网格 */
    private boolean listStyle = false;
    /** 排序：0 = 名称，1 = 数量，2 = 来源数 */
    private int sortMode = 0;
    /** 搜索关键字（小写） */
    private String searchText = "";

    /** 详情侧栏当前展示的卡片（视图切换后失效则回退首个可见卡片） */
    private Object detailCard = null;
    /** 本次渲染中鼠标悬浮的卡片（用于悬浮提示框） */
    private Object hoveredCard = null;
    /** 网格滚动（像素） */
    private double gridScroll = 0;
    /** 详情来源列表滚动（像素） */
    private double detailScroll = 0;

    private int panelH = 300;
    private int panelX;
    private int panelY;
    private int gridLeft;
    private int gridTop;
    private int gridW;
    private int gridH;
    private int detailLeft;
    private int detailTop;

    private EditBox searchBox;

    public FtbRewardSelectScreen(List<RawEntry> rawEntries, Screen parent) {
        super(Component.translatable("beyond_integration.ftb.reward.title"));
        this.rawEntries = rawEntries;
        this.mergedEntries = buildMerged(rawEntries);
        for (RawEntry e : rawEntries) {
            selectedIds.add(e.id());
            destNetwork.put(e.id(), e.networkCapable());
        }
        this.parent = parent;
    }

    /** 同款合并：按 mergeKey（物品栈或标题）聚合，保持首次出现顺序 */
    private static List<Entry> buildMerged(List<RawEntry> raw) {
        Map<String, Entry> merged = new LinkedHashMap<>();
        for (RawEntry e : raw) {
            Entry existing = merged.get(e.mergeKey());
            if (existing == null) {
                List<Long> ids = new ArrayList<>();
                ids.add(e.id());
                List<RawEntry> sources = new ArrayList<>();
                sources.add(e);
                merged.put(e.mergeKey(), new Entry(ids, e.title(), e.count(), e.icon(),
                        e.networkCapable(), sources));
            } else {
                existing.ids().add(e.id());
                existing.sources().add(e);
                merged.put(e.mergeKey(), new Entry(existing.ids(), existing.title(),
                        existing.totalCount() + e.count(), existing.icon(),
                        existing.networkCapable() && e.networkCapable(), existing.sources()));
            }
        }
        return new ArrayList<>(merged.values());
    }

    /** 由入口 Mixin 调用：从客户端任务数据构建可领取奖励列表并打开界面 */
    public static void openFromClient() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        List<RawEntry> raw = new ArrayList<>();
        try {
            if (minecraft.player != null && dev.ftb.mods.ftbquests.client.ClientQuestFile.exists()) {
                var file = dev.ftb.mods.ftbquests.client.ClientQuestFile.INSTANCE;
                var data = file.selfTeamData;
                UUID uuid = minecraft.player.getUUID();
                int[] count = {0};
                file.forAllChapters(chapter -> {
                    String chapterTitle = chapter.getTitle().getString();
                    for (var quest : chapter.getQuests()) {
                        String questTitle = quest.getTitle().getString();
                        for (var reward : quest.getRewards()) {
                            if (count[0] >= MAX_ENTRIES) return;
                            if (reward.getExcludeFromClaimAll()) continue;
                            if (!data.getClaimType(uuid, reward).canClaim()) continue;
                            count[0]++;
                            // 可进网络：直接物品奖励，以及按输出结果结算的随机/战利品/全表奖励（子物品随标记入网）；
                            // 选择奖励需玩家交互（异步），保持原版
                            boolean capable = reward instanceof dev.ftb.mods.ftbquests.quest.reward.ItemReward
                                    || reward instanceof dev.ftb.mods.ftbquests.quest.reward.RandomReward
                                    || reward instanceof dev.ftb.mods.ftbquests.quest.reward.LootReward
                                    || reward instanceof dev.ftb.mods.ftbquests.quest.reward.AllTableReward;
                            String title = reward.getAltTitle().getString();
                            long amount = 1L;
                            String mergeKey;
                            if (reward instanceof dev.ftb.mods.ftbquests.quest.reward.ItemReward itemReward) {
                                ItemStack stack = itemReward.getItem();
                                amount = Math.max(1, itemReward.getCount());
                                title = stack.getHoverName().getString();
                                // 1.21.1：以数据组件集合作为同款判定（等价于 1.20.1 的 NBT 比较）
                                String nbt = stack.getComponents().toString();
                                mergeKey = "item|" + BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|" + nbt;
                            } else {
                                mergeKey = "title|" + title;
                            }
                            raw.add(new RawEntry(reward.getId(), title, amount, reward.getIcon(),
                                    questTitle, chapterTitle, capable, mergeKey));
                        }
                    }
                });
            }
        } catch (Throwable ignored) {}
        minecraft.setScreen(new FtbRewardSelectScreen(raw, parent));
    }

    @Override
    protected void init() {
        this.panelH = Math.max(200, Math.min(400, this.height - 40));
        this.panelX = (this.width - PANEL_W) / 2;
        this.panelY = Math.max(8, (this.height - panelH) / 2);
        this.gridLeft = panelX + 8;
        this.gridTop = panelY + TOOLBAR_H + 6;
        this.gridW = PANEL_W - DETAIL_W - 22;
        this.gridH = panelH - TOOLBAR_H - FOOTER_H - 12;
        this.detailLeft = panelX + PANEL_W - DETAIL_W - 8;
        this.detailTop = panelY + TOOLBAR_H + 6;

        // 搜索框
        this.searchBox = new EditBox(this.font, panelX + 8, panelY + 4, 110, 14,
                Component.translatable("beyond_integration.ftb.reward.search_hint"));
        this.searchBox.setHint(Component.translatable("beyond_integration.ftb.reward.search_hint"));
        this.searchBox.setResponder(s -> {
            this.searchText = s == null ? "" : s.trim().toLowerCase();
            this.gridScroll = 0;
            this.detailScroll = 0;
            this.detailCard = null;
        });
        addRenderableWidget(this.searchBox);

        int bx = panelX + 124;
        Button sortBtn = Button.builder(sortLabel(), b -> {
            sortMode = (sortMode + 1) % 3;
            b.setMessage(sortLabel());
            this.gridScroll = 0;
            this.detailCard = null;
        }).bounds(bx, panelY + 4, 78, 14).build();
        sortBtn.setTooltip(Tooltip.create(Component.translatable("beyond_integration.ftb.reward.tip_sort")));
        addRenderableWidget(sortBtn);
        bx += 84;
        Button viewBtn = Button.builder(viewLabel(), b -> {
            mergedView = !mergedView;
            b.setMessage(viewLabel());
            this.gridScroll = 0;
            this.detailScroll = 0;
            this.detailCard = null;
        }).bounds(bx, panelY + 4, 62, 14).build();
        viewBtn.setTooltip(Tooltip.create(Component.translatable("beyond_integration.ftb.reward.tip_view")));
        addRenderableWidget(viewBtn);
        bx += 68;
        Button styleBtn = Button.builder(styleLabel(), b -> {
            listStyle = !listStyle;
            b.setMessage(styleLabel());
            this.gridScroll = 0;
        }).bounds(bx, panelY + 4, 62, 14).build();
        styleBtn.setTooltip(Tooltip.create(Component.translatable("beyond_integration.ftb.reward.tip_style")));
        addRenderableWidget(styleBtn);

        int bottom = panelY + panelH - 22;
        bx = panelX + 8;
        addRenderableWidget(Button.builder(Component.translatable("beyond_integration.ftb.reward.select_all"),
                b -> setAllSelected(true)).bounds(bx, bottom, 46, 18).build());
        bx += 50;
        addRenderableWidget(Button.builder(Component.translatable("beyond_integration.ftb.reward.select_none"),
                b -> setAllSelected(false)).bounds(bx, bottom, 56, 18).build());
        bx += 60;
        addRenderableWidget(Button.builder(Component.translatable("beyond_integration.ftb.reward.all_network"),
                b -> setAllDest(true)).bounds(bx, bottom, 60, 18).build());
        bx += 64;
        addRenderableWidget(Button.builder(Component.translatable("beyond_integration.ftb.reward.all_bag"),
                b -> setAllDest(false)).bounds(bx, bottom, 60, 18).build());
        bx = panelX + PANEL_W - 108;
        addRenderableWidget(Button.builder(Component.translatable("beyond_integration.ftb.reward.submit"), b -> submit())
                .bounds(bx, bottom, 50, 18).build());
        bx += 54;
        addRenderableWidget(Button.builder(Component.translatable("beyond_integration.ftb.reward.cancel"),
                b -> this.onClose()).bounds(bx, bottom, 50, 18).build());
    }

    private Component sortLabel() {
        return Component.translatable(switch (sortMode) {
            case 1 -> "beyond_integration.ftb.reward.sort_count";
            case 2 -> "beyond_integration.ftb.reward.sort_sources";
            default -> "beyond_integration.ftb.reward.sort_name";
        });
    }

    private Component viewLabel() {
        return Component.translatable(mergedView
                ? "beyond_integration.ftb.reward.view_merged"
                : "beyond_integration.ftb.reward.view_raw");
    }

    private Component styleLabel() {
        return Component.translatable(listStyle
                ? "beyond_integration.ftb.reward.style_list"
                : "beyond_integration.ftb.reward.style_grid");
    }

    /** 当前视图 + 搜索 + 排序后的显示卡片列表（元素为 Entry 或 RawEntry） */
    private List<Object> visibleCards() {
        List<Object> out = new ArrayList<>();
        if (mergedView) {
            for (Entry e : mergedEntries) {
                if (matchesSearch(e.title())) out.add(e);
            }
            out.sort((a, b) -> compareCards((Entry) a, (Entry) b));
        } else {
            for (RawEntry e : rawEntries) {
                if (matchesSearch(e.title())) out.add(e);
            }
            out.sort((a, b) -> compareRaw((RawEntry) a, (RawEntry) b));
        }
        return out;
    }

    private boolean matchesSearch(String title) {
        return searchText.isEmpty() || (title != null && title.toLowerCase().contains(searchText));
    }

    private int compareCards(Entry a, Entry b) {
        return switch (sortMode) {
            case 1 -> Long.compare(b.totalCount(), a.totalCount());
            case 2 -> Integer.compare(b.sources().size(), a.sources().size());
            default -> String.CASE_INSENSITIVE_ORDER.compare(String.valueOf(a.title()), String.valueOf(b.title()));
        };
    }

    private int compareRaw(RawEntry a, RawEntry b) {
        return switch (sortMode) {
            case 1 -> Long.compare(b.count(), a.count());
            case 2 -> String.CASE_INSENSITIVE_ORDER.compare(String.valueOf(a.chapterTitle()), String.valueOf(b.chapterTitle()));
            default -> String.CASE_INSENSITIVE_ORDER.compare(String.valueOf(a.title()), String.valueOf(b.title()));
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xC8101010);
        // 面板四边完整边框
        outline(graphics, panelX, panelY, PANEL_W, panelH, 0xFF606060);
        // 区域边框：工具栏 / 网格 / 详情 / 底部（汇总 + 按钮）
        outline(graphics, panelX + 4, panelY + 2, PANEL_W - 8, TOOLBAR_H, 0x30FFFFFF);
        outline(graphics, gridLeft - 2, gridTop - 2, gridW + 6, gridH + 4, 0x30FFFFFF);
        outline(graphics, detailLeft - 2, detailTop - 2, DETAIL_W + 4, gridH + 4, 0x30FFFFFF);
        outline(graphics, panelX + 4, panelY + panelH - FOOTER_H, PANEL_W - 8, FOOTER_H - 2, 0x30FFFFFF);

        this.hoveredCard = null;
        List<Object> cards = visibleCards();
        if (listStyle) renderList(graphics, cards, mouseX, mouseY);
        else renderGrid(graphics, cards, mouseX, mouseY);
        renderDetail(graphics, cards, mouseX, mouseY);

        // 汇总行（底部区域内）：口径 = 当前视图条目数 / 奖励条数 / 选中条目数 / 选中条数
        graphics.drawString(this.font,
                Component.translatable("beyond_integration.ftb.reward.summary",
                        cards.size(), visibleRewardCount(cards), selectedCardCount(cards), selectedCount()),
                panelX + 10, panelY + panelH - FOOTER_H + 4, 0xA0A0A0, false);
        graphics.drawString(this.font, destSummary(), panelX + 250, panelY + panelH - FOOTER_H + 4, 0x808080, false);

        super.render(graphics, mouseX, mouseY, partialTick);

        // 悬浮提示框：卡片悬停时显示完整信息与操作提示
        if (this.hoveredCard != null) {
            renderCardTooltip(graphics, this.hoveredCard, mouseX, mouseY);
        }
    }

    /** 卡片悬浮提示框：名称/数量 + 相关章节·任务数 + 前几条来源 + 操作提示 */
    private void renderCardTooltip(GuiGraphics graphics, Object card, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        long count = cardCount(card);
        lines.add(Component.literal(count > 1 ? cardTitle(card) + " ×" + count : cardTitle(card))
                .withStyle(net.minecraft.ChatFormatting.WHITE));
        List<RawEntry> sources = cardSources(card);
        Set<String> chapters = new HashSet<>();
        for (RawEntry s : sources) chapters.add(String.valueOf(s.chapterTitle()));
        lines.add(Component.translatable("beyond_integration.ftb.reward.chapters_tasks",
                chapters.size(), sources.size()).withStyle(net.minecraft.ChatFormatting.GRAY));
        int show = Math.min(sources.size(), 5);
        for (int i = 0; i < show; i++) {
            RawEntry s = sources.get(i);
            lines.add(Component.literal("· " + s.chapterTitle() + " · " + s.questTitle()
                            + (s.count() > 1 ? " ×" + s.count() : ""))
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        if (sources.size() > show) {
            lines.add(Component.translatable("beyond_integration.ftb.reward.sources_more",
                    sources.size() - show).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.translatable("beyond_integration.ftb.reward.tip_card_toggle")
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
        if (cardNetworkCapable(card)) {
            lines.add(Component.translatable("beyond_integration.ftb.reward.tip_card_dest")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    /** 卡片网格（可滚动；裁剪到网格区域） */
    private void renderGrid(GuiGraphics graphics, List<Object> cards, int mouseX, int mouseY) {
        graphics.enableScissor(gridLeft, gridTop, gridLeft + gridW, gridTop + gridH);
        try {
            int cols = Math.max(1, (gridW + CARD_GAP) / (CARD_W + CARD_GAP));
            int rows = Math.max(1, (cards.size() + cols - 1) / cols);
            int contentH = rows * (CARD_H + CARD_GAP);
            double maxScroll = Math.max(0, contentH - gridH);
            gridScroll = Math.max(0, Math.min(gridScroll, maxScroll));

            if (cards.isEmpty()) {
                graphics.drawString(this.font, Component.translatable("beyond_integration.ftb.reward.empty"),
                        gridLeft + 4, gridTop + 4, 0x707070, false);
                return;
            }
            for (int i = 0; i < cards.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int x = gridLeft + col * (CARD_W + CARD_GAP);
                int y = gridTop + row * (CARD_H + CARD_GAP) - (int) gridScroll;
                boolean hover = inRect(mouseX, mouseY, x, y, CARD_W, CARD_H);
                if (hover) this.hoveredCard = cards.get(i);
                boolean selected = isCardSelected(cards.get(i));
                boolean partial = isCardPartial(cards.get(i));
                // 背景 + 边框（选中绿 / 半选黄 / 未选灰，悬浮加亮）
                graphics.fill(x, y, x + CARD_W, y + CARD_H, hover ? 0x30FFFFFF : 0x20000000);
                outline(graphics, x, y, CARD_W, CARD_H,
                        selected ? 0xFF55FF55 : partial ? 0xFFFFD166 : (hover ? 0x90FFFFFF : 0x40FFFFFF));
                // 图标
                Icon icon = cardIcon(cards.get(i));
                if (icon != null && !icon.isEmpty()) {
                    icon.draw(graphics, x + (CARD_W - 24) / 2, y + 4, 24, 24);
                }
                // 数量角标（右上）
                long count = cardCount(cards.get(i));
                if (count > 1) {
                    String badge = "×" + count;
                    graphics.drawString(this.font, badge, x + CARD_W - this.font.width(badge) - 3, y + 3,
                            0xFFFFFF, true);
                }
                // 选中勾（左上）
                if (selected) {
                    graphics.drawString(this.font, "✔", x + 3, y + 3, 0xFF55FF55, false);
                }
                // 名称（居中，截断）
                String name = cardTitle(cards.get(i));
                String clipped = this.font.plainSubstrByWidth(name, CARD_W - 4);
                graphics.drawString(this.font, clipped, x + (CARD_W - this.font.width(clipped)) / 2,
                        y + CARD_H - 21, selected ? 0xFFFFFF : 0xB0B0B0, false);
                // 章节/任务数小字
                String sub = cardSubLabel(cards.get(i));
                if (!sub.isEmpty()) {
                    graphics.drawString(this.font, sub, x + (CARD_W - this.font.width(sub)) / 2,
                            y + CARD_H - 10, 0x808080, false);
                }
            }
            // 滚动条（内容超出时）
            if (maxScroll > 0) {
                int barH = Math.max(12, (int) (gridH * gridH / contentH));
                int barY = gridTop + (int) ((gridH - barH) * (gridScroll / maxScroll));
                graphics.fill(gridLeft + gridW + 2, barY, gridLeft + gridW + 4, barY + barH, 0x80FFFFFF);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    /** 列表样式：每行一个条目（勾选框 + 图标 + 名称/数量 + 章节·任务 + 去向），可滚动 */
    private void renderList(GuiGraphics graphics, List<Object> cards, int mouseX, int mouseY) {
        graphics.enableScissor(gridLeft, gridTop, gridLeft + gridW, gridTop + gridH);
        try {
            if (cards.isEmpty()) {
                graphics.drawString(this.font, Component.translatable("beyond_integration.ftb.reward.empty"),
                        gridLeft + 4, gridTop + 4, 0x707070, false);
                return;
            }
            int contentH = cards.size() * LIST_ROW_H;
            double maxScroll = Math.max(0, contentH - gridH);
            gridScroll = Math.max(0, Math.min(gridScroll, maxScroll));
            for (int i = 0; i < cards.size(); i++) {
                int y = gridTop + i * LIST_ROW_H - (int) gridScroll;
                if (y + LIST_ROW_H < gridTop || y > gridTop + gridH) continue;
                Object card = cards.get(i);
                boolean hover = inRect(mouseX, mouseY, gridLeft, y, gridW, LIST_ROW_H - 1);
                if (hover) this.hoveredCard = card;
                boolean selected = isCardSelected(card);
                boolean partial = isCardPartial(card);
                if (hover) graphics.fill(gridLeft, y, gridLeft + gridW, y + LIST_ROW_H - 1, 0x28FFFFFF);
                outline(graphics, gridLeft, y, gridW, LIST_ROW_H - 1,
                        selected ? 0xFF55FF55 : partial ? 0xFFFFD166 : (hover ? 0x90FFFFFF : 0x40FFFFFF));
                // 勾选框
                graphics.fill(gridLeft + 2, y + 4, gridLeft + 12, y + 14,
                        selected ? 0xFF55FF55 : partial ? 0xFFFFD166 : 0x30FFFFFF);
                outline(graphics, gridLeft + 2, y + 4, 10, 10, 0xFF909090);
                // 图标
                Icon icon = cardIcon(card);
                if (icon != null && !icon.isEmpty()) icon.draw(graphics, gridLeft + 16, y + 1, 16, 16);
                // 名称 + 数量（右侧预留去向与章节·任务小字宽度）
                long count = cardCount(card);
                String nameLine = count > 1 ? cardTitle(card) + " ×" + count : cardTitle(card);
                String destText = !cardNetworkCapable(card)
                        ? Component.translatable("beyond_integration.ftb.reward.vanilla").getString()
                        : Component.translatable(cardToNetwork(card)
                        ? "beyond_integration.ftb.reward.to_network"
                        : "beyond_integration.ftb.reward.to_bag").getString();
                int destW = this.font.width(destText) + 6;
                String sub = cardSubLabel(card);
                int subW = sub.isEmpty() ? 0 : this.font.width(sub) + 6;
                String clipped = this.font.plainSubstrByWidth(nameLine, gridW - 40 - destW - subW);
                graphics.drawString(this.font, clipped, gridLeft + 36, y + 5, selected ? 0xFFFFFF : 0xB0B0B0, false);
                if (!sub.isEmpty()) {
                    graphics.drawString(this.font, sub, gridLeft + gridW - destW - this.font.width(sub) - 4,
                            y + 5, 0x808080, false);
                }
                int destColor = !cardNetworkCapable(card) ? 0x707070 : cardToNetwork(card) ? 0x55FF55 : 0xAAAAAA;
                graphics.drawString(this.font, destText, gridLeft + gridW - this.font.width(destText) - 4,
                        y + 5, destColor, false);
            }
            if (maxScroll > 0) {
                int barH = Math.max(12, (int) (gridH * gridH / contentH));
                int barY = gridTop + (int) ((gridH - barH) * (gridScroll / maxScroll));
                graphics.fill(gridLeft + gridW + 2, barY, gridLeft + gridW + 4, barY + barH, 0x80FFFFFF);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    /** 详情侧栏：大图标 + 名称/数量 + 去向 + 相关任务（按章节分组，可滚动） */
    private void renderDetail(GuiGraphics graphics, List<Object> cards, int mouseX, int mouseY) {
        graphics.fill(detailLeft, detailTop, detailLeft + DETAIL_W, detailTop + gridH, 0x40000000);
        outline(graphics, detailLeft, detailTop, DETAIL_W, gridH, 0x30FFFFFF);
        if (cards.isEmpty()) return;
        Object card = detailCard != null && cards.contains(detailCard) ? detailCard : cards.get(0);

        int x = detailLeft + 4;
        int y = detailTop + 4;
        Icon icon = cardIcon(card);
        if (icon != null && !icon.isEmpty()) {
            icon.draw(graphics, detailLeft + (DETAIL_W - 32) / 2, y, 32, 32);
        }
        y += 36;
        String title = cardTitle(card);
        long total = cardCount(card);
        String titleLine = this.font.plainSubstrByWidth(total > 1 ? title + " ×" + total : title, DETAIL_W - 8);
        graphics.drawString(this.font, titleLine, detailLeft + (DETAIL_W - this.font.width(titleLine)) / 2, y,
                0xFFFFFF, false);
        y += 12;
        // 去向（整卡统一）
        boolean capable = cardNetworkCapable(card);
        boolean toNet = cardToNetwork(card);
        Component dest = capable
                ? Component.translatable(toNet
                        ? "beyond_integration.ftb.reward.to_network"
                        : "beyond_integration.ftb.reward.to_bag")
                : Component.translatable("beyond_integration.ftb.reward.vanilla");
        int destColor = !capable ? 0x707070 : toNet ? 0x55FF55 : 0xAAAAAA;
        boolean destHover = inRect(mouseX, mouseY, x, y - 2, DETAIL_W - 8, 12);
        if (capable && destHover) graphics.fill(x, y - 2, x + DETAIL_W - 8, y + 10, 0x30FFFFFF);
        // 去向小按钮边框（可点时更亮）
        outline(graphics, x, y - 2, DETAIL_W - 8, 12,
                capable ? (destHover ? 0x80FFFFFF : 0x50FFFFFF) : 0x30FFFFFF);
        graphics.drawString(this.font, dest, x + 4, y, destColor, false);
        y += 16;
        graphics.fill(x, y, x + DETAIL_W - 8, y + 1, 0x30FFFFFF);
        y += 4;

        // 相关任务：按章节分组
        List<RawEntry> sources = cardSources(card);
        Map<String, List<RawEntry>> byChapter = new LinkedHashMap<>();
        for (RawEntry s : sources) {
            byChapter.computeIfAbsent(String.valueOf(s.chapterTitle()), k -> new ArrayList<>()).add(s);
        }
        graphics.drawString(this.font, Component.translatable("beyond_integration.ftb.reward.chapters_tasks",
                byChapter.size(), sources.size()), x, y, 0x808080, false);
        y += 12;

        // 内容高度与滚动
        int contentH = 0;
        for (Map.Entry<String, List<RawEntry>> g : byChapter.entrySet()) {
            contentH += 11 + g.getValue().size() * 10;
        }
        int clipTop = y;
        int clipBottom = detailTop + gridH;
        int viewH = Math.max(1, clipBottom - clipTop - 2);
        double maxScroll = Math.max(0, contentH - viewH);
        detailScroll = Math.max(0, Math.min(detailScroll, maxScroll));

        graphics.enableScissor(detailLeft + 1, clipTop, detailLeft + DETAIL_W - 1, clipBottom);
        try {
            int drawY = y - (int) detailScroll;
            for (Map.Entry<String, List<RawEntry>> g : byChapter.entrySet()) {
                long chapterSum = 0;
                for (RawEntry s : g.getValue()) chapterSum += s.count();
                String head = this.font.plainSubstrByWidth("▾ " + g.getKey() + "  ×" + chapterSum, DETAIL_W - 12);
                graphics.drawString(this.font, head, x, drawY, 0xFFD166, false);
                drawY += 11;
                for (RawEntry s : g.getValue()) {
                    String line = this.font.plainSubstrByWidth("· " + s.questTitle()
                            + (s.count() > 1 ? " ×" + s.count() : ""), DETAIL_W - 14);
                    graphics.drawString(this.font, line, x + 6, drawY, 0x909090, false);
                    drawY += 10;
                }
            }
        } finally {
            graphics.disableScissor();
        }
        if (maxScroll > 0) {
            int barH = Math.max(10, (int) (viewH * viewH / Math.max(1, contentH)));
            int barY = clipTop + (int) ((viewH - barH) * (detailScroll / maxScroll));
            graphics.fill(detailLeft + DETAIL_W - 4, barY, detailLeft + DETAIL_W - 2, barY + barH, 0x80FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        List<Object> cards = visibleCards();
        // 列表样式：按行命中（右侧去向区域切换去向，其余切换勾选）
        if (listStyle) {
            if (mouseX >= gridLeft && mouseX < gridLeft + gridW
                    && mouseY >= gridTop && mouseY < gridTop + gridH) {
                int idx = (int) ((mouseY - gridTop + gridScroll) / LIST_ROW_H);
                if (idx >= 0 && idx < cards.size()) {
                    Object card = cards.get(idx);
                    detailCard = card;
                    if (mouseX >= gridLeft + gridW - 60 && cardNetworkCapable(card)) {
                        toggleDest(card);
                    } else {
                        detailScroll = 0;
                        toggleCard(card);
                    }
                    return true;
                }
            }
        } else {
            // 网格卡片：选中并切换勾选
            int cols = Math.max(1, (gridW + CARD_GAP) / (CARD_W + CARD_GAP));
            for (int i = 0; i < cards.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int x = gridLeft + col * (CARD_W + CARD_GAP);
                int y = gridTop + row * (CARD_H + CARD_GAP) - (int) gridScroll;
                if (inRect(mouseX, mouseY, x, y, CARD_W, CARD_H)) {
                    detailCard = cards.get(i);
                    detailScroll = 0;
                    toggleCard(cards.get(i));
                    return true;
                }
            }
        }
        // 详情面板：去向行点击切换，其余区域仅聚焦
        if (inRect(mouseX, mouseY, detailLeft, detailTop, DETAIL_W, gridH) && !cards.isEmpty()) {
            Object card = detailCard != null && cards.contains(detailCard) ? detailCard : cards.get(0);
            if (cardNetworkCapable(card) && inRect(mouseX, mouseY, detailLeft + 4, detailTop + 48, DETAIL_W - 8, 12)) {
                toggleDest(card);
                return true;
            }
            detailCard = card;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inRect(mouseX, mouseY, detailLeft, detailTop, DETAIL_W, gridH)) {
            detailScroll -= scrollY * 12;
            return true;
        }
        if (inRect(mouseX, mouseY, gridLeft, gridTop, gridW, gridH)) {
            gridScroll -= scrollY * 24;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.searchBox.setFocused(false);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── 卡片访问器（兼容合并 / 展开两种视图） ──

    private Icon cardIcon(Object card) {
        return card instanceof Entry e ? e.icon() : ((RawEntry) card).icon();
    }

    private String cardTitle(Object card) {
        return card instanceof Entry e ? e.title() : ((RawEntry) card).title();
    }

    private long cardCount(Object card) {
        return card instanceof Entry e ? e.totalCount() : ((RawEntry) card).count();
    }

    private boolean cardNetworkCapable(Object card) {
        return card instanceof Entry e ? e.networkCapable() : ((RawEntry) card).networkCapable();
    }

    private List<Long> cardIds(Object card) {
        if (card instanceof Entry e) return e.ids();
        return List.of(((RawEntry) card).id());
    }

    private List<RawEntry> cardSources(Object card) {
        if (card instanceof Entry e) return e.sources();
        return List.of((RawEntry) card);
    }

    private boolean cardToNetwork(Object card) {
        for (long id : cardIds(card)) {
            if (!Boolean.TRUE.equals(destNetwork.get(id))) return false;
        }
        return true;
    }

    private boolean isCardSelected(Object card) {
        for (long id : cardIds(card)) {
            if (!selectedIds.contains(id)) return false;
        }
        return true;
    }

    private boolean isCardPartial(Object card) {
        boolean any = false;
        for (long id : cardIds(card)) {
            if (selectedIds.contains(id)) any = true;
        }
        return any && !isCardSelected(card);
    }

    private String cardSubLabel(Object card) {
        List<RawEntry> sources = cardSources(card);
        if (sources.size() <= 1) return "";
        Set<String> chapters = new HashSet<>();
        for (RawEntry s : sources) chapters.add(String.valueOf(s.chapterTitle()));
        return Component.translatable("beyond_integration.ftb.reward.card_sub",
                chapters.size(), sources.size()).getString();
    }

    private void toggleCard(Object card) {
        boolean select = !isCardSelected(card);
        for (long id : cardIds(card)) {
            if (select) selectedIds.add(id);
            else selectedIds.remove(id);
        }
    }

    private void toggleDest(Object card) {
        boolean next = !cardToNetwork(card);
        for (long id : cardIds(card)) {
            destNetwork.put(id, next && cardNetworkCapable(card));
        }
    }

    /** 全选/全不选（作用于全部原始奖励） */
    private void setAllSelected(boolean value) {
        selectedIds.clear();
        if (value) {
            for (RawEntry e : rawEntries) selectedIds.add(e.id());
        }
    }

    /** 全部设为网络/背包（仅可进网络的奖励受影响） */
    private void setAllDest(boolean network) {
        for (RawEntry e : rawEntries) {
            destNetwork.put(e.id(), network && e.networkCapable());
        }
    }

    /** 提交选择并关闭界面：按原始奖励顺序提交勾选条目 */
    private void submit() {
        List<Long> ids = new ArrayList<>();
        List<Boolean> flags = new ArrayList<>();
        for (RawEntry e : rawEntries) {
            if (!selectedIds.contains(e.id())) continue;
            ids.add(e.id());
            flags.add(Boolean.TRUE.equals(destNetwork.get(e.id())) && e.networkCapable());
        }
        if (ids.isEmpty()) return;
        PacketHandler.sendToServer(new SubmitFtbRewardSelectionPacket(ids, flags));
        this.onClose();
    }

    private int selectedCount() {
        return selectedIds.size();
    }

    /** 当前视图的奖励条数（合并条目展开后的总数） */
    private int visibleRewardCount(List<Object> cards) {
        int n = 0;
        for (Object card : cards) n += cardIds(card).size();
        return n;
    }

    /** 当前视图中完全选中的条目数 */
    private int selectedCardCount(List<Object> cards) {
        int n = 0;
        for (Object card : cards) {
            if (isCardSelected(card)) n++;
        }
        return n;
    }

    /** 勾选奖励的去向汇总：网络 / 背包 / 原版 */
    private Component destSummary() {
        int net = 0;
        int bag = 0;
        int vanilla = 0;
        for (RawEntry e : rawEntries) {
            if (!selectedIds.contains(e.id())) continue;
            if (!e.networkCapable()) {
                vanilla++;
            } else if (Boolean.TRUE.equals(destNetwork.get(e.id()))) {
                net++;
            } else {
                bag++;
            }
        }
        return Component.translatable("beyond_integration.ftb.reward.dest_summary", net, bag, vanilla);
    }

    private static boolean inRect(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** 绘制 1px 矩形边框 */
    private static void outline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 关闭时回退到打开前的界面（无则回到游戏） */
    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
