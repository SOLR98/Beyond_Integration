# Beyond Integration · UI 组件指南（BD 0.8.x 前置）

> 适用：`NEW_FORGE_1.20.1` / `NEW_NEOFORGE_1.21.1`（BD 0.8.x + CompixelUI）。
> 0.7.30 的两条工程**不适用**（它们用 BD 0.7 自绘 GUI + `CommonTextures`）。
> 组件来源（本地参考）：`NEW_BD/CompixelUI-main`（ui-ore / minecraft-forge）、
> `NEW_BD/BeyondDimensions-1.20.1-ui-port`（`client/ui/kit`、`client/ui/base`、`api/**`）。

## 分层

```text
CompixelUI（dev.compixel.*）        ← 像素风基础控件 + 集成层（屏幕/槽位/同步）
      ↑ BD kit（com.wintercogs...client.ui.kit）   ← BD 主题化组件（窗口/页签/设置行…）
      ↑ BD base（client.ui.base）                  ← 屏幕基类、槽位视图、BdPage 宿主
      ↑ BI 页面（com.solr98...client.page）        ← BdPage / BdInventoryScreen 子类
```

---

## 1. CompixelUI · 基础控件（`dev.compixel.ui.ore.*`）

| 组件 | 包 | 用途 / 要点 |
| --- | --- | --- |
| `OreText(text, modifier, color, fontSize, fontWeight, textAlign, maxLines, overflow, style)` | `ore.display` | **就是 `BasicText`**：给足宽度会自动软换行；不需要手算宽度 |
| `OreGlyph`（enum）、`OreIcon(art/glyph, modifier, color)`、`OrePixelArt(cells)` | `ore.display` | 内置像素图标 / 自定义 10×10 像素图 |
| `OreProgressBar(progress: Float, modifier)` | `ore.display` | 细进度条 |
| `OreSlider(value, onValueChange, modifier, valueRange, enabled)` | `ore.input` | 像素滑条（BD 设置页同款） |
| `OreTextField / OreIntField / OreLongField / OreDoubleField / OreNumberEditor` | `ore.input` | 文本框与数字编辑器（`OreNumberEditor` 带步进） |
| `OreColorPicker` | `ore.input` | 颜色选择器 |
| `OreSlot(...)`、`OreSlotAmount(text, modifier, contentSize)` | `ore.inventory` | 槽位底纹 / 数量标签（BD 的 `BdSlot` 基于它） |
| `OreButton`、`OreButtonStyle`、`OreIconButton` | `ore.button` | 按钮 / 图标按钮 |
| `OrePanel / OreScreen / OreSurface / OreSurfaceStyle / OreDivider` | `ore.layout` | 面板、屏幕、表面样式、分隔线 |
| `OreListItem / OreTab / OreTreeView / OreTreeNode` | `ore.navigation` | 列表项、页签、树形视图 |
| `OreDialog / OreMenu(OreMenuItem) / OreWindow(rememberOreWindowState) / OreTooltip(OreTooltipMode) / OreContextMenuArea` | `ore.overlay` | 弹窗、菜单、窗口、tooltip、右键菜单区 |
| `OreCheckbox` | `ore.selection` | 复选框 |
| `OreScrollbar / OreScrollTrack` | `ore.scroll` | 滚动条 |
| `OreColorEditor / OreColorEditorText / OreColorExport` | `ore.editor` | 配色编辑器（开发期工具） |

**主题**：`OreTheme`、`OreTypography`（`body` 8sp / `title` 10sp / `caption` 6sp，行高 11/13/9）。

## 2. CompixelUI · Forge 集成层（`dev.compixel.forge.*`）

| API | 包 | 用途 / 要点 |
| --- | --- | --- |
| `ComposeInventoryScreen<M,S,A>(menu, title, inventory, nativeItemOptions, design)` | `dev.compixel.forge` | 带槽位屏幕基类；抽象 `snapshot()` / `handle(action)` / `Content(state, slots)` |
| `ComposeMenuScreen<M,S,A>(menu, title, nativeItemOptions, design)` | 同上 | 无槽位屏幕基类 |
| `ComposeMenuSlots<M>(menu, adapter)`、`ComposeMenuSlots.areaModifier()` | `forge.slots` | 槽位状态容器；`areaModifier()` 给原生槽位定位 |
| `MenuSlotVisual / MenuSlotState / VanillaMenuSlotAdapter / MenuSlotAdapter / SlotBehavior` | `forge.slots` | 槽位显示与点击行为适配（BD 的 `BdSlotAdapter` 继承 `VanillaMenuSlotAdapter`） |
| `SyncedMenu`（`menuSync()`）、`MenuSync.bind(menu, schema)`、`MenuAction.of(id, codec, handler)` | `forge.sync` | 菜单同步/动作；**0.1.16 起 action id 只能是 `[a-zA-Z0-9_.-]`（不能带冒号）** |
| `SyncSchema.builder(id, version)`、`SyncCodecs.*`、`SyncCodec.of(id, write, read)` | `dev.compixel.sync.state` | 字段 schema / 编解码 |
| `ItemIcon.snapshot(stack, refresh)`、`ItemIcon.drawn(desc, draw, refresh)`、`MinecraftItemIcon(icon, modifier)` | `forge.item` | 物品图标 / 自绘图标；`NativeRefresh.AUTO/STATIC/GAME_TICK/ON_CHANGE` |
| `NativeItemOptions(imageSize, cacheCapacity, preparationsPerFrame)` | `forge.item` | 图标缓存与每帧预算（BD: `cacheCapacity=512`, `preparationsPerFrame=512`） |

## 3. BD kit（`com.wintercogs.beyonddimensions.client.ui.kit`）

### 窗口与页面
| 组件 | 签名要点 |
| --- | --- |
| `BdScreenFrame(window)` | 所有 BD 界面的骨架（背景压暗 + 窗口进退场） |
| `BdTabbedWindow(modifier, header, tabs, pinned, width, pages)` | 标准窗口；`width = SLOT_WINDOW_WIDTH.dp` 常用 |
| `BdRailTab(label, icon, selected, active, onClick)` | 左侧竖条页签 |
| `BdMainPage(shown) { }` / `BdTabPage(shown, verticalArrangement, size) { }` | 主页面 / 子页面（`size` 可 `BdPageSize.*`） |
| `BdWindow / BdHeader(icon, title, onClose, tag)` | 简单窗口与标题栏 |

### 文本与按钮
| 组件 | 签名要点 |
| --- | --- |
| `BdSectionLabel(text, modifier, trailing)` | 分组标题（单行 `overline` 样式） |
| `BdAmountPill(text, modifier)` | 数量标签（带投影） |
| `BdSearchField / BdTextField / BdTextArea` | 搜索框 / 单行框 / 多行框 |
| `BdChip(onClick, modifier, enabled, active, content)` | 小按钮（`content: RowScope.() -> Unit`） |
| `BdSegmented(options, selected, onSelect, modifier, enabled, fill)` | 分段选择 |
| `BdGlyphButton(icon, description, onClick, modifier, enabled, selected, size)` | 图标按钮 |
| `BdIcon(icon, modifier, color)` | 画一个 `BdPixelIcon` |
| `BdPixelIcon(vararg rows: String)` | 10×10 像素图（`.` 空 / `#` 实） |

### 设置控件
| 组件 | 签名要点 |
| --- | --- |
| `BdToggle(checked, onCheckedChange, enabled)` | 开关。**参数顺序固定，尾随 lambda 会落到 `enabled` 上**，要写 `onCheckedChange = { }` 或位置参数 |
| `BdSettingRow(title, description, control)` | 一行设置（左标题+说明，右控件）；`description` 最多 2 行 |
| `BdChoiceSetting(title, description, control)` | 标题在上、控件独占一行（**`BdCycler`/`BdTextArea` 用这个**） |
| `BdCycler(options, selected, enabled, onSelect)` | 循环选择；内部 `fillMaxWidth()`，不要塞进 `BdSettingRow` |
| `BdDropdown(options, selected, enabled, onSelect)` | 下拉菜单 |
| `BdNumberEditor(value(Int/Long), onValueChange, range, enabled)` | 数字编辑 |

### 滚动与覆盖层
| 组件 | 要点 |
| --- | --- |
| `BdScrollColumn(modifier, verticalArrangement, wheelStep, content)` | 可滚动列（自动带滚动条） |
| `BdScrollbar(first, visible, total, onScrollTo)` / `BdScrollbar(state)` / `Modifier.bdWheelStep(state, step)` | 滚动条与滚轮步长 |
| `BdTooltip(help, modifier, enabled, content)`、`BdPopover(onDismissRequest, alignStart, content)`、`BdPointMenu`、`BdMenuRow` | 提示与浮层 |

### 修饰符与常量
- `Modifier.bdRaised(...)`（凸起）、`bdInset(...)`（凹陷）、`bdSlotFrame(colors)`、`bdScreen(...)`、`bdKnob(...)`、`brackets(color, arm, stroke)`、`bdClickable(...)`。
- 常量：`SIDE_RAIL_WIDTH = 24`、`PAGE_PADDING_X = 8`、`PAGE_PADDING_TOP = 7`、`PAGE_PADDING_BOTTOM = 8`、
  `SCREEN_INSET_*`。
- 主题：`Bd.colors[BdColors.*]`、`Bd.caption / Bd.body / Bd.overline`。

## 4. BD base（`client.ui.base`）

| API | 用途 / 要点 |
| --- | --- |
| `BdInventoryScreen<M : BDBaseMenu, S, A>(menu, title)` | 带槽位 BD 屏幕：内置 `adapter`、`pages`（`BdPageHost`）、`inventoryTick()`、`afterInput()` |
| `BdMenuScreen<M, S, A>(menu, title)` | 无槽位 BD 屏幕（如网络控制界面） |
| `BdSlot(slots, id, modifier, underlay)`、`BdSlotGrid(slots, ids, columns)` | 画槽位 |
| `BdSlotSection(title, ids, slots, columns = 9, hint)`、`BdInventorySection(title, playerSlots, slots)` | 带框的槽位分组 / 玩家背包区 |
| `BDBaseMenu.playerSlotIds()`、`flagSlotIds()`、`resourceSlotIds()` | 取槽位 id 列表（标记槽 = fake 槽） |
| `BdPageHost(menu, screen, slots, requestClose)`、`BdPageSelection`、`rememberPageSelection()`、`InjectedTabs` / `InjectedPages` | 注入页的宿主与页签/页面渲染 |
| `tr(key, vararg args)` | 取翻译字符串（游戏线程/Compose 线程都可） |
| `MachineSettingRow` / `MachineSettingControl` / `MachineSettingPick` / `machineSettingRows(...)` / `BdMachineSettings(rows, shown, onPick)` | BD 机器设置行（BI 的喂食器页用过） |

## 5. BdPage 页面模板

```kotlin
object MyPage : BdPage<SomeMenu, MyState, MyAction> {
    private const val PAGE_WIDTH = SLOT_WINDOW_WIDTH - SIDE_RAIL_WIDTH   // 与主页面同宽
    override val icon = BdPixelIcon(
        "..........", "...#......", "..###.....", ".#...#....", "..###.....",
        "...#......", "...#......", "..###.....", "..........", "..........",
    )
    override fun title(context: BdPageContext<SomeMenu>) =
        Component.translatable("beyond_integration.page.my.title")

    override fun size(context: BdPageContext<SomeMenu>): BdPageSize =
        BdPageSize.Content(maxWidth = PAGE_WIDTH)          // 高度随内容

    override fun snapshot(context: BdPageContext<SomeMenu>) = MyState(/* 游戏线程读菜单/缓存 */)

    override fun handle(action: MyAction) { /* 游戏线程处理动作 */ }

    @Composable
    override fun ColumnScope.Content(state: MyState, scope: BdPageScope<MyAction>) {
        Column(Modifier.width(PAGE_WIDTH.dp)) {             // Content 尺寸需要确定宽
            BdSectionLabel("标题")
            BdWrappedText("说明（自动折行）")
            BdSettingRow("开关", "说明") { BdToggle(state.on, onCheckedChange = { send(MyAction.Set(it)) }) }
        }
    }
}
```

注册（`ClientSetup`）：

```java
BdPages.register(new ResourceLocation(MODID, "my_page"),
        SomeMenu.class, MyPage.INSTANCE, BdPages.INSTANCE.last());
```

- 页面菜单泛型必须**精确匹配**（终端 `DimensionsNetTerminalMenu` 继承 `DimensionsNetMenu`，注册父类即可覆盖两者）。
- `snapshot/handle` 在游戏线程；`Content` 在 Compose 线程，**只能读快照**。
- 需要读“当前网络/设置”时优先用客户端缓存类（如 `PrimaryNetClientStorage`、`EnchantSeparationState`），不要直接摸菜单里的游戏对象。

## 6. 菜单与同步（BD 0.8.x）

| API | 用途 |
| --- | --- |
| `BDBaseMenu` | 基类；提供 `menuSync()`、`resources()`、`commands()`、`inventoryStartIndex/EndIndex` |
| `BDMenuResources.bind(menu, viewSchema)` | 资源/有序槽同步（标记槽内容、`CLICK/TAKE/GHOST` 命令） |
| `BDMenuCommands.inventory(sync)` | 注册槽位点击命令（`CLICK/TAKE/GHOST`） |
| `BdMenuActions.attach(menu, sync)` / `BdMenuActions.register(menuClass, action)` | 注入动作 |
| `MenuSync.bind(menu, schema)` + `MenuAction.of(id, codec, handler)` | 字段同步与动作（id 规则见上） |
| `StackHandler(size)` | 有序槽存储 |
| `FlagStackTypedSlot(menu, storage, index, x, y)` | 标记槽（fake 槽） |
| `DisorderedStackTypedSlot` | 无序存储网格槽（终端） |
| `ItemStackKey(stack)` / `FluidStackKey(fluidStack)` / `KeyAmount(key, amount)` | 资源键与数量 |
| `UnifiedStorage` / `DimensionsNet` | 网络存储与网络对象（插入规则见 `UnifiedStorageRules`） |

## 7. 常见坑

1. **组件所在包**：`BdSlotSection`/`BdInventorySection`/`BdSlot` 在 `client.ui.base`（`BdSlotViews.kt`），不在 `kit`。
2. **`BdToggle` 参数顺序**：`(checked, onCheckedChange, enabled)`——最后是 `enabled`，尾随 lambda 会被当成 `enabled`（类型不符时报“缺 onCheckedChange”）；写命名参数最稳。
3. **`BdCycler` 自带 `fillMaxWidth()`**：塞进 `BdSettingRow` 会把左侧标题挤没；用 `BdChoiceSetting` 让它独占一行。
4. **`BdSettingRow` 的 description 最多 2 行**：长说明改用 `BdWrappedText`（BI 的通用折行组件，单次 `OreText` 自动换行）。
5. **`BdPageSize.Content` 需要内容有确定宽度**：给内容列加 `Modifier.width(PAGE_WIDTH.dp)`，否则固有宽度会按文本单行撑开。
6. **不要手算文本宽度折行**：`OreText` 就是 `BasicText`，会自动断行（中文按字符断、标点不落行首）。
7. **action id 不能带冒号**（CompixelUI 0.1.16+）：用 `beyond_integration.xxx` 或纯下划线命名。
8. **`BdSlotAdapter` 的点击要走 BD 命令链**：菜单没有 `commands().inventory(BDMenuResources.bind(...))` 时，槽位点击在服务端无人处理。
9. **图标创建线程**：`ItemIcon.snapshot/drawn` 要求渲染线程（屏幕构造时创建）；`BdPixelIcon` 无此限制。
10. **BD 0.8 的 Compixel 版本**：`NEW_FORGE_1.20.1` 运行时 0.1.16（`curse.maven:compixel:9330608`），
    `NEW_NEOFORGE_1.21.1` 0.1.12；跨版本写组件前先看 `NEW_BD/CompixelUI-main` 对应模块。

## 8. 组件预览调试页

`NEW_FORGE_1.20.1` 有一个**开发环境专用**的页面 `client/page/ComponentGalleryPage.kt`
（注册 id `beyond_integration:component_gallery`，仅 `!FMLEnvironment.production` 时注册）：
打开 BD 存储界面即可在页签里看到「组件预览（调试）」，里面把下列组件各渲染一份并带本地状态：

- 按钮：`BdGlyphButton`（启用/禁用）、`BdChip`（普通/active/禁用）、`BdSegmented`、
  `OreButton`（Primary/Secondary/Destructive/Quiet/禁用）、`OreIconButton`（启用/禁用）；
- 选项：`BdToggle`（启用/禁用）、`BdCycler`、`BdDropdown`、`BdNumberEditor`、
  `OreCheckbox`（带 label）、`OreSlider`、`OreIntField`/`OreLongField`/`OreDoubleField`/`OreTextField`、`OreTab`。

改组件样式/主题后可先在这里对照，不必进正式功能页。
