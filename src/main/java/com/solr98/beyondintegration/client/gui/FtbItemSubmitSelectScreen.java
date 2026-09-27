package com.solr98.beyondintegration.client.gui;

// 注意：Beyond Dimensions 本体将在下个版本更换 UI 框架，本类依赖其现有 GUI 布局/坐标/纹理，
// 待 BD 正式发布后需校对代码与新版 GUI。


import com.solr98.beyondintegration.network.OpenFtbItemSubmitSelectPacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SubmitFtbItemSelectionPacket;
import com.solr98.beyondintegration.util.NumberFormatUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * FTB 消耗型物品任务"选择提交"界面（客户端）：
 * 主面板左侧列出任务目标在背包与主网络中的全部匹配物品（按类型 + NBT 合并，展示可用量），
 * 右侧栏分页显示当前已选物品列表；玩家分配数量后确认——服务端先扣背包、不足部分从网络扣除。
 * <p>
 * 交互：
 * <ul>
 *   <li>候选列表：左键 +1 / 右键 -1 / 滚轮调整 / Shift 步进 10；已选行以绿框高亮；</li>
 *   <li>数字键 1-9：对当前页对应候选 +1（Shift ×10、Ctrl 清零）；Enter 提交；</li>
 *   <li>已选栏：独立翻页；左键 -1（Shift ×10）、右键清零、滚轮调整；</li>
 *   <li>自动填充按列表顺序补满，补满仅补足已选物品；取消/提交/ESC 后返回打开前的界面。</li>
 * </ul>
 * 列表每页行数按窗口高度自适应，充分利用可用空间。
 */
public class FtbItemSubmitSelectScreen extends Screen {

    private static final int PANEL_W = 400;
    private static final int ROW_H = 22;
    private static final int LIST_W = 230;
    private static final int SEL_W = 140;
    private static final int COL_GAP = 10;

    private final long taskId;
    private final String title;
    private final long remaining;
    private final List<OpenFtbItemSubmitSelectPacket.Entry> entries;
    private final long[] selected;
    private final Screen parent;

    private int pageSize = 7;
    private int panelH = 238;
    private int page = 0;
    private int selPage = 0;
    private int panelX;
    private int panelY;
    private int listLeft;
    private int listTop;
    private int selLeft;

    public FtbItemSubmitSelectScreen(OpenFtbItemSubmitSelectPacket packet, Screen parent) {
        super(Component.translatable("beyond_integration.ftb.select.title"));
        this.taskId = packet.taskId();
        this.title = packet.title() == null ? "" : packet.title();
        this.remaining = Math.max(0, packet.remaining());
        this.entries = packet.entries();
        this.selected = new long[this.entries.size()];
        this.parent = parent;
    }

    /** 由 S2C 数据包处理调用：打开选择界面并记录当前界面用于回退 */
    public static void open(OpenFtbItemSubmitSelectPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new FtbItemSubmitSelectScreen(packet, minecraft.screen));
    }

    @Override
    protected void init() {
        // 每页行数按窗口高度自适应（3-12 行），充分利用垂直空间
        this.pageSize = Math.max(3, Math.min(12, (this.height - 96) / ROW_H));
        this.panelH = 34 + this.pageSize * ROW_H + 46;
        this.panelX = (this.width - PANEL_W) / 2;
        this.panelY = Math.max(8, (this.height - this.panelH) / 2);
        this.listLeft = panelX + 10;
        this.listTop = panelY + 34;
        this.selLeft = listLeft + LIST_W + COL_GAP;

        int bottom = panelY + panelH - 26;
        int bx = panelX + 40;
        addRenderableWidget(Button.builder(Component.translatable("beyond_integration.ftb.select.auto"), b -> autoFill())
                .bounds(bx, bottom, 70, 20).build());
        bx += 76;
        addRenderableWidget(Button.builder(Component.translatable("beyond_integration.ftb.select.fill"),
                b -> fillBySelection()).bounds(bx, bottom, 56, 20).build());
        bx += 62;
        addRenderableWidget(Button.builder(Component.translatable("beyond_integration.ftb.select.clear"),
                b -> Arrays.fill(selected, 0L)).bounds(bx, bottom, 50, 20).build());
        bx += 56;
        addRenderableWidget(Button.builder(Component.translatable("beyond_integration.ftb.select.submit"), b -> submit())
                .bounds(bx, bottom, 64, 20).build());
        bx += 70;
        addRenderableWidget(Button.builder(Component.translatable("beyond_integration.ftb.select.cancel"),
                b -> this.onClose()).bounds(bx, bottom, 56, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xC8101010);
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + 1, 0xFF606060);
        graphics.fill(panelX, panelY + panelH - 1, panelX + PANEL_W, panelY + panelH, 0xFF606060);

        graphics.drawCenteredString(this.font,
                this.font.plainSubstrByWidth(this.title, PANEL_W - 20), panelX + PANEL_W / 2, panelY + 8, 0xFFFFFF);
        graphics.drawString(this.font,
                Component.translatable("beyond_integration.ftb.select.remaining", NumberFormatUtil.compact(remaining)),
                panelX + 10, panelY + 22, 0xA0A0A0, false);
        graphics.drawString(this.font,
                Component.translatable("beyond_integration.ftb.select.selected",
                        NumberFormatUtil.compact(selectedTotal()), NumberFormatUtil.compact(remaining)),
                panelX + PANEL_W - 130, panelY + 22, 0x55FF55, false);

        renderCandidates(graphics, mouseX, mouseY);
        renderPageRow(graphics, mouseX, mouseY);
        renderSelected(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
        int hoveredIndex = hoveredCandidateIndex(mouseX, mouseY);
        if (hoveredIndex < 0) {
            hoveredIndex = hoveredSelectedIndex(mouseX, mouseY);
        }
        if (hoveredIndex >= 0) {
            showEntryTooltip(graphics, hoveredIndex, mouseX, mouseY);
        }
    }

    /** 条目悬浮框：物品原生 tooltip + 完整千分位数值（背包 / 网络 / 已选） */
    private void showEntryTooltip(GuiGraphics graphics, int index, int mouseX, int mouseY) {
        OpenFtbItemSubmitSelectPacket.Entry entry = entries.get(index);
        List<Component> lines = new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), entry.stack()));
        lines.add(Component.empty());
        lines.add(Component.translatable("beyond_integration.ftb.select.tip_bag",
                NumberFormatUtil.grouped(entry.bag())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("beyond_integration.ftb.select.tip_net",
                NumberFormatUtil.grouped(entry.net())).withStyle(ChatFormatting.GRAY));
        if (selected[index] > 0) {
            lines.add(Component.translatable("beyond_integration.ftb.select.tip_selected",
                    NumberFormatUtil.grouped(selected[index])).withStyle(ChatFormatting.GREEN));
        }
        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    /** 左侧候选列表：两行显示（名称 / 背包 + 网络可用量），已选行绿框高亮 */
    private void renderCandidates(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < visibleCount(); i++) {
            int index = page * pageSize + i;
            OpenFtbItemSubmitSelectPacket.Entry entry = entries.get(index);
            int rowY = listTop + i * ROW_H;
            boolean picked = selected[index] > 0;
            if (picked) {
                graphics.fill(listLeft, rowY, listLeft + LIST_W, rowY + ROW_H, 0x2855FF55);
                outline(graphics, listLeft, rowY, LIST_W, ROW_H, 0xA055FF55);
            }
            boolean hover = mouseX >= listLeft && mouseX < listLeft + LIST_W
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (hover) {
                graphics.fill(listLeft, rowY, listLeft + LIST_W, rowY + ROW_H, 0x38FFFFFF);
            }
            graphics.renderItem(entry.stack(), listLeft + 3, rowY + 3);
            String name = this.font.plainSubstrByWidth(entry.stack().getHoverName().getString(), LIST_W - 54);
            graphics.drawString(this.font, name, listLeft + 24, rowY + 3, 0xFFFFFF, false);
            String available = Component.translatable("beyond_integration.ftb.select.bag_net",
                    NumberFormatUtil.compact(entry.bag()), NumberFormatUtil.compact(entry.net())).getString();
            graphics.drawString(this.font, this.font.plainSubstrByWidth(available, LIST_W - 54),
                    listLeft + 24, rowY + 12, 0x909090, false);
            String count = picked ? NumberFormatUtil.compact(selected[index]) : "-";
            graphics.drawString(this.font, count, listLeft + LIST_W - 26, rowY + 7,
                    picked ? 0x55FF55 : 0x606060, false);
        }
        graphics.fill(selLeft - COL_GAP / 2, listTop, selLeft - COL_GAP / 2 + 1,
                listTop + pageSize * ROW_H, 0x40FFFFFF);
    }

    /** 页码行：左侧快捷键提示，候选栏右侧翻页箭头与页码 */
    private void renderPageRow(GuiGraphics graphics, int mouseX, int mouseY) {
        int pageY = listTop + pageSize * ROW_H + 4;
        String hint = this.font.plainSubstrByWidth(
                Component.translatable("beyond_integration.ftb.select.hint").getString(), LIST_W - 100);
        graphics.drawString(this.font, hint, panelX + 10, pageY, 0x606060, false);

        boolean prevHover = inRect(mouseX, mouseY, pagePrevX(), pageY - 3, 14, 14);
        boolean nextHover = inRect(mouseX, mouseY, pageNextX(), pageY - 3, 14, 14);
        graphics.drawString(this.font, "<", pagePrevX() + 3, pageY, prevHover ? 0xFFFFFF : 0x808080, false);
        graphics.drawString(this.font, ">", pageNextX() + 3, pageY, nextHover ? 0xFFFFFF : 0x808080, false);
        graphics.drawCenteredString(this.font,
                Component.translatable("beyond_integration.ftb.select.page", page + 1, pageCount()),
                listLeft + LIST_W - 52, pageY, 0xA0A0A0);
    }

    /** 右侧栏：已选物品列表（独立分页） */
    private void renderSelected(GuiGraphics graphics, int mouseX, int mouseY) {
        int picked = pickedCount();
        if (picked == 0) {
            graphics.drawString(this.font, Component.translatable("beyond_integration.ftb.select.none"),
                    selLeft + 4, listTop + 4, 0x707070, false);
            return;
        }
        if (selPage >= selPageCount()) selPage = selPageCount() - 1;
        int start = selPage * pageSize;
        int display = Math.min(pageSize, picked - start);
        for (int i = 0; i < display; i++) {
            int index = nthPicked(start + i);
            if (index < 0) break;
            OpenFtbItemSubmitSelectPacket.Entry entry = entries.get(index);
            int rowY = listTop + i * ROW_H;
            boolean hover = mouseX >= selLeft && mouseX < selLeft + SEL_W
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (hover) {
                graphics.fill(selLeft, rowY, selLeft + SEL_W, rowY + ROW_H, 0x38FFFFFF);
            }
            graphics.renderItem(entry.stack(), selLeft + 3, rowY + 3);
            String name = this.font.plainSubstrByWidth(entry.stack().getHoverName().getString(), SEL_W - 60);
            graphics.drawString(this.font, name, selLeft + 24, rowY + 7, 0xFFFFFF, false);
            graphics.drawString(this.font, NumberFormatUtil.compact(selected[index]), selLeft + SEL_W - 26, rowY + 7,
                    0x55FF55, false);
        }
        if (selPageCount() > 1) {
            int pageY = listTop + pageSize * ROW_H + 4;
            boolean prevHover = inRect(mouseX, mouseY, selPrevX(), pageY - 3, 14, 14);
            boolean nextHover = inRect(mouseX, mouseY, selNextX(), pageY - 3, 14, 14);
            graphics.drawString(this.font, "<", selPrevX() + 3, pageY, prevHover ? 0xFFFFFF : 0x808080, false);
            graphics.drawString(this.font, ">", selNextX() + 3, pageY, nextHover ? 0xFFFFFF : 0x808080, false);
            graphics.drawCenteredString(this.font,
                    Component.translatable("beyond_integration.ftb.select.page", selPage + 1, selPageCount()),
                    selLeft + SEL_W / 2, pageY, 0xA0A0A0);
        }
    }

    /** 命中测试：候选列表 hover 的条目下标（无返回 -1） */
    private int hoveredCandidateIndex(double mouseX, double mouseY) {
        if (mouseX < listLeft || mouseX >= listLeft + LIST_W) return -1;
        int row = (int) ((mouseY - listTop) / ROW_H);
        if (row < 0 || row >= visibleCount()) return -1;
        return page * pageSize + row;
    }

    /** 命中测试：已选栏 hover 的条目下标（无返回 -1） */
    private int hoveredSelectedIndex(double mouseX, double mouseY) {
        if (mouseX < selLeft || mouseX >= selLeft + SEL_W) return -1;
        int row = (int) ((mouseY - listTop) / ROW_H);
        if (row < 0 || row >= pageSize) return -1;
        return nthPicked(selPage * pageSize + row);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handlePageClick(mouseX, mouseY)) return true;
        if (handleSelectedClick(mouseX, mouseY, button)) return true;
        if (handleRowClick(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (scrollAdjust(mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** 快捷键：数字键 1-9 对当前页候选 +1（Shift ×10、Ctrl 清零），Enter 提交 */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int row = -1;
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            row = keyCode - GLFW.GLFW_KEY_1;
        } else if (keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_9) {
            row = keyCode - GLFW.GLFW_KEY_KP_1;
        }
        if (row >= 0) {
            int index = page * pageSize + row;
            if (row < visibleCount() && index < entries.size()) {
                if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                    selected[index] = 0;
                } else {
                    addToCandidate(index, (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1);
                }
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 滚轮调整鼠标所在列表行的数量：上滚 +1、下滚 -1（Shift 步进 10） */
    private boolean scrollAdjust(double mouseX, double mouseY, double delta) {
        if (delta == 0) return false;
        long step = (hasShiftDown() ? 10 : 1) * (delta > 0 ? 1 : -1);
        int row = (int) ((mouseY - listTop) / ROW_H);
        if (row < 0 || row >= pageSize) return false;
        if (mouseX >= listLeft && mouseX < listLeft + LIST_W) {
            int index = page * pageSize + row;
            if (index < 0 || index >= entries.size()) return false;
            addToCandidate(index, step);
            return true;
        }
        if (mouseX >= selLeft && mouseX < selLeft + SEL_W) {
            int index = nthPicked(selPage * pageSize + row);
            if (index < 0) return false;
            selected[index] = Math.max(0, selected[index] + step);
            return true;
        }
        return false;
    }

    /** 候选行点击：左键增加、右键减少，Shift 步进 10；总量不超过剩余需求与可用量 */
    private boolean handleRowClick(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) return false;
        if (mouseX < listLeft || mouseX >= listLeft + LIST_W) return false;
        int row = (int) ((mouseY - listTop) / ROW_H);
        if (row < 0 || row >= visibleCount()) return false;
        int index = page * pageSize + row;
        if (index < 0 || index >= entries.size()) return false;
        long step = hasShiftDown() ? 10 : 1;
        if (button == 0) {
            addToCandidate(index, step);
        } else {
            selected[index] = Math.max(0, selected[index] - step);
        }
        return true;
    }

    /** 增加候选的已选数量：不超过可用量与剩余需求 */
    private void addToCandidate(int index, long delta) {
        if (delta <= 0) return;
        OpenFtbItemSubmitSelectPacket.Entry entry = entries.get(index);
        long available = entry.bag() + entry.net();
        long room = Math.min(available - selected[index], remaining - selectedTotal());
        if (room > 0) selected[index] += Math.min(delta, room);
    }

    /** 已选栏行点击：左键减少（Shift 步进 10）、右键清零 */
    private boolean handleSelectedClick(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) return false;
        if (mouseX < selLeft || mouseX >= selLeft + SEL_W) return false;
        int row = (int) ((mouseY - listTop) / ROW_H);
        if (row < 0 || row >= pageSize) return false;
        int index = nthPicked(selPage * pageSize + row);
        if (index < 0) return false;
        if (button == 0) {
            long step = hasShiftDown() ? 10 : 1;
            selected[index] = Math.max(0, selected[index] - step);
        } else {
            selected[index] = 0;
        }
        return true;
    }

    /** 翻页箭头点击（候选栏 + 已选栏） */
    private boolean handlePageClick(double mouseX, double mouseY) {
        int pageY = listTop + pageSize * ROW_H + 4;
        if (inRect(mouseX, mouseY, pagePrevX(), pageY - 3, 14, 14)) {
            page = Math.max(0, page - 1);
            return true;
        }
        if (inRect(mouseX, mouseY, pageNextX(), pageY - 3, 14, 14)) {
            page = Math.min(pageCount() - 1, page + 1);
            return true;
        }
        if (selPageCount() > 1) {
            if (inRect(mouseX, mouseY, selPrevX(), pageY - 3, 14, 14)) {
                selPage = Math.max(0, selPage - 1);
                return true;
            }
            if (inRect(mouseX, mouseY, selNextX(), pageY - 3, 14, 14)) {
                selPage = Math.min(selPageCount() - 1, selPage + 1);
                return true;
            }
        }
        return false;
    }

    /** 按列表顺序补满剩余需求（背包 + 网络可用量） */
    private void autoFill() {
        Arrays.fill(selected, 0L);
        long total = 0;
        for (int i = 0; i < entries.size() && total < remaining; i++) {
            OpenFtbItemSubmitSelectPacket.Entry entry = entries.get(i);
            long take = Math.min(entry.bag() + entry.net(), remaining - total);
            selected[i] = take;
            total += take;
        }
    }

    /** 按当前选择项补满剩余需求：仅补满已选物品至其可用量上限，不额外选择其他候选 */
    private void fillBySelection() {
        long total = selectedTotal();
        if (total >= remaining) return;
        for (int i = 0; i < entries.size() && total < remaining; i++) {
            if (selected[i] <= 0) continue;
            OpenFtbItemSubmitSelectPacket.Entry entry = entries.get(i);
            long available = entry.bag() + entry.net();
            long add = Math.min(available - selected[i], remaining - total);
            if (add > 0) {
                selected[i] += add;
                total += add;
            }
        }
    }

    /** 发送选择提交并关闭界面（回退到打开前的界面） */
    private void submit() {
        List<ItemStack> stacks = new ArrayList<>();
        List<Long> amounts = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (selected[i] <= 0) continue;
            stacks.add(entries.get(i).stack().copyWithCount(1));
            amounts.add(selected[i]);
        }
        if (stacks.isEmpty()) return;
        PacketHandler.sendToServer(new SubmitFtbItemSelectionPacket(taskId, stacks, amounts));
        this.onClose();
    }

    private long selectedTotal() {
        long total = 0;
        for (long value : selected) total += value;
        return total;
    }

    /** 已选条目数（不同物品类型） */
    private int pickedCount() {
        int count = 0;
        for (long value : selected) {
            if (value > 0) count++;
        }
        return count;
    }

    /** 第 n 个已选条目在候选列表中的下标（无返回 -1） */
    private int nthPicked(int n) {
        if (n < 0) return -1;
        int count = 0;
        for (int i = 0; i < selected.length; i++) {
            if (selected[i] <= 0) continue;
            if (count == n) return i;
            count++;
        }
        return -1;
    }

    private int pageCount() {
        return Math.max(1, (entries.size() + pageSize - 1) / pageSize);
    }

    private int selPageCount() {
        return Math.max(1, (pickedCount() + pageSize - 1) / pageSize);
    }

    private int visibleCount() {
        return Math.min(pageSize, entries.size() - page * pageSize);
    }

    private int pagePrevX() {
        return listLeft + LIST_W - 86;
    }

    private int pageNextX() {
        return listLeft + LIST_W - 18;
    }

    private int selPrevX() {
        return selLeft + 2;
    }

    private int selNextX() {
        return selLeft + SEL_W - 16;
    }

    private static boolean inRect(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** 绘制 1px 矩形边框 */
    private static void outline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
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
