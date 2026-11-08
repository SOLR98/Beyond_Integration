# 批量附魔工作站 · 界面元素规格

> 对应实现：`feature/crafting/DimensionsEnchantMergeMenu`（Forge）/ `init/DimensionsEnchantMergeMenu`（NeoForge）、
> `client/gui/DimensionsEnchantMergeGUI`（两分支）。坐标均为**面板局部坐标**（原点 = `leftPos, getGapY()`），
> 除标注“屏幕坐标”外。

## 0. 基础

| 项 | 值 |
|---|---|
| 面板尺寸 | `176 × 135`（高度由菜单 `getPanelHeight()` 提供；GUI `getPanelHeight()` 以其为唯一来源） |
| 面板背景 | `beyond_integration:textures/gui/standard_background.png`（176×62，纵向拉伸到面板高） |
| 常量 | `PANEL_W=176`、`ROW_H=16`、`ROW_Y0=35`、`PAGE_SIZE=5`、`FOOT_Y=118` |
| 列坐标 | `COL_CB=6`、`COL_NAME=20`、`COL_SLIDER=66`、`SLIDER_W=80`、`COL_LV=150` |
| 菜单槽位 | 装备槽 `WEQ_X=8`、`WEQ_Y=15`（`ey(15)`），尺寸 18×18 |
| 文本高度 | 统一 9px（原版字体 lineHeight） |
| 边框 | **无**（已移除全部元素边框与列表表头） |

## 1. 标题

| 元素 | 类型 | 位置 | 颜色 |
|---|---|---|---|
| 「附魔合并」 | 文本 9px | (6, 4) | `0x404040` |

## 2. 装备信息区（无边框，与标题间隔 3px）

- 装备槽：`common_slots.png`（**18×18**）绘制于 (7, 15)；物品（16×16）落在 (8, 16)，与贴图内容对齐。
- 装备名：文本 (30, 15)，色 `0x404040`（空槽时显示「放入可附魔装备」 (30, 20) 色 `0x707070`）。
- 经验费用：文本 (30, 24)，色 `8453920`；其下垫**与铁砧同款暗色半透明底** `0x4F000000`（`fill(x-2, y-2, x+宽+2, y+10)`）。
  - 仅在勾选 ≥1 项时显示；悬停显示公式/明细 tooltip，末尾追加「≈ 玩家从 0 级升到 X 级」。

## 3. 附魔候选行（无表头，行高 16，每页 5 行）

- 行顶：`y = 35 + i×16`（i=0..4 → 35/51/67/83/99），行间**无间隔**（满高绘制）。
- 条目贴图：`beyond_integration:textures/gui/enchant_merge_entry.png`（**200×48**）
  - **左侧 16px = 复选框按钮**，三态：选中 `v=16` / 未选 `v=32` / 禁用 `v=0`
  - **右侧 184px = 条目背景**，三态：悬停 `v=16` / 正常 `v=32` / 禁用 `v=0`
  - 分两段绘制（16×16 与 184×16 源区，目标 16×16 与 `(170-16)×16`）：
    `blit(ENTRY_TEX, 3, 行顶, 16, 16, 0, vBtn, 16, 16, 200, 48)` +
    `blit(ENTRY_TEX, 19, 行顶, 154, 16, 16, vRow, 184, 16, 200, 48)`
- 复选框内叠加**状态物品图标**（缩放 0.7 居中，`renderFakeItem`）：
  - 禁用 → `minecraft:barrier`；已选 → `minecraft:enchanted_book`（**附魔光效**，1.21 用 `ENCHANTMENT_GLINT_OVERRIDE`、1.20.1 写入附魔）；未选 → `minecraft:book`。
- 选中交互：**点击条目（滑条/复选框以外区域）或复选框切换选中/取消**；滑条拖动/滚轮自动选中。
  - 取消勾选（已有附魔）= 提交清除；**不改变滑条所在的等级**（`sliderLevels` 记忆）。

| 元素 | 类型/贴图 | 绘制尺寸 | 位置（相对行顶） |
|---|---|---|---|
| 附魔名称 | 文本 9px | 截断到 44px | x=20，y=+4 |
| 等级滑条控件 | 纯色（带刻度） | 80×13 | x=66，y=+1 |
| ├ 轨道 | 纯色 | 80×4 暗 `0xFF373737` + 80×2 亮 `0xFF8B8B8B` | 垂直居中 |
| ├ 档位刻度 | 纯色 | 1×6 `0xFF555555` × 档位数 | 两端对齐轨道 |
| └ 手柄 | 纯色 | 6×(h-2) 外 `0xFF555555`、内 `0xFFC6C6C6`/悬浮 `0xFFFFFFFF` | 中心对准当前档位 |
| 等级文字（罗马） | 文本 9px | — | x=`150+(14-宽)/2+2`，y=+4 |

- 等级显示：`levelOf` = 滑条记忆等级 → 否则装备已有等级 → 否则网络最高等级。
- 单档附魔（`maxLevel == 1`）：滑条固定画 3 个刻度，手柄居中且不可拖动（`active=false`）。
- 滑条拖动：`onClick`/`mouseDragged` 将鼠标 x 映射到 [0,1] 后吸附整数档位。

## 4. 操作栏（无边框）

- 页码文本：`x = 23 + (20-宽)/2`，`y = FOOT_Y + 3 = 121`，色 `0x404040`。
- 按钮统一 16×16 控件，0.85 缩放居中渲染：`translate(x+8, y+8); scale(0.85); blit 16×16 居中`。
- 按钮顶部 `y = FOOT_Y - 1 = 117`。

| 按钮 | 背景 / 图标 | 位置 x | 行为 |
|---|---|---|---|
| 上一页 ◀ | `slot_button(_hovered)` + `up_arrow` 旋转 −90° | 6 | 上一页 |
| 下一页 ▶ | 同上，旋转 +90° | 44 | 下一页 |
| 满级 | `slot_button` + `minecraft:nether_star` | 100 | 拉满可升级项（跳过早选冲突项） |
| 清除 | `slot_button` + `minecraft:bucket` | 118 | 已有附魔全部标记为移除（等级 0） |
| 还原 | `slot_button` + `minecraft:recovery_compass` | 136 | 回到已有附魔初始选择（清空改动与滑条记忆） |
| 合并 | `slot_button` + `minecraft:anvil` | 154 | 提交；无勾选时 `active=false` |

## 5. 结果预览 / 费用 tooltip

- **结果预览**（合并按钮悬浮框）：直接渲染“操作后物品”的悬浮框（`getTooltipLines`），并对发生变化的附魔条目行尾追加彩色后缀：
  - `[+]` 新增（绿）、`[↑ 旧→新]` 升级（绿）、`[↓ 旧→新]` 降级（红）
  - 被移除项在结果中不存在，以 `✗ 名称 等级` 红色**删除线**追加在末尾
- **费用 tooltip**：悬停经验费用文本显示计费公式 + 逐项明细（精确等级 / 拆分行含普通书消耗）+「≈ 玩家从 0 级升到 X 级」。

## 6. 贴图资源清单

| 资源 | 路径 | 尺寸 |
|---|---|---|
| 面板背景 | `beyond_integration:textures/gui/standard_background.png` | 176×62 |
| 输入槽 | `beyond_integration:textures/gui/common_slots.png` | 18×18 |
| 附魔条目（按钮 + 背景三态） | `beyond_integration:textures/gui/enchant_merge_entry.png` | 200×48（左 16px 按钮 + 右 184px 条目，各三态 16px 高） |
| 按钮背景 | `beyonddimensions:textures/gui/sprites/widget/slot_button.png` | 16×16 |
| 按钮悬停 | `beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png` | 16×16 |
| 翻页箭头 | `beyonddimensions:textures/gui/sprites/widget/up_arrow.png` | 16×16 |
| 物品图标 | 原版物品贴图（nether_star / bucket / recovery_compass / anvil / chest） | 16×16 |

## 7. 物品栏文本（BD 基类定位，屏幕坐标）

| 元素 | 位置 |
|---|---|
| GUI 标题标签 | `titleLabelY = 8`（相对 topPos） |
| 物品栏标签 | `inventoryLabelY = 24 + lines×18 + 5 + panelHeight(135) + 8`，x = `leftPos + 8`，高 9px |
| 主背包首行槽 | `y = 25 + 135 + (lines-1)×18 + 26 + 6 + 8`，行距 18 |
| 快捷栏 | 主背包末行 + `3×18 + 4` |

## 8. 交互与同步

- 滑条 / 底栏按钮均为 widget（`Button` / `AbstractSliderButton`）；条目点击切换选中由 `mouseClicked` 处理。
- **冲突禁用**依据当前选择列表：生效集合 = 已有附魔（未被清除） ∪ 已勾选（等级>0）；已有附魔永不因冲突禁用；选择变化后调用 `refreshConflicts()` 即时刷新（不重新扫描网络），`rebuild()` 每 10 tick 或装备变化时全量重建。
- 提交合并 → `SubmitEnchantMergePacket`（Forge，附魔 id + 等级数组）/ `SubmitEnchantMergePayload`（NeoForge，Holder + 等级列表）；服务端按**所选等级降序**写入装备。
- 预览：`mergePreview()` 渲染操作后物品悬浮框 + 变化后缀；费用 tooltip：`xpTooltip()`。
