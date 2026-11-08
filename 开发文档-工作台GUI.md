# Beyond Integration 1.21.1 — 工作台 GUI 开发文档

> 适用分支：`1.21.1_NEOFORGE`
> 模组版本：0.7.0（本文只描述实现，不代表升版本）
> 最后更新：2026-10-19
> 相关文档：`../两分支功能实现整理.md`、`../分支差异-功能覆盖清单.md`
> BD API：`开发文档-BD-API.md`
> BD 可用数据：`开发文档-BD-可用数据.md`
> 上游 UI：Beyond Dimensions 新版 CompixelUI / Compose；上游 API 说明见 BD 的 `docs/zh-CN/screens.md`

---

## 1. 总体结构

1.21.1 的工作台 GUI 已经不再使用旧版自绘 `Dimensions*GUI`，改为：

- **BD `StorageScreen`**：完整存储界面（搜索、排序、分类、翻页、右键取物、设置、网络切换）。
- **BI 工作台菜单**：`DimensionsAnvilMenu`、`DimensionsCutMenu`、`DimensionsGrindMenu`、`DimensionsSmithMenu`、`DimensionsEnchantMenu`、`DimensionsEnchantMergeMenu`、`DimensionsApothEnchantMenu`。
- **BI `BdPage` 页面**：六个页面既是 BD 终端左侧的工作台入口，也是对应工作台界面里的内容页。
- **工作台页面内容**：存储视图 + 工作台槽位 + 玩家背包。

三者的关系：

```text
BD 终端 / BI 工作台菜单
        │
        ▼
BD StorageScreen（完整存储界面）
        │  注册：RegisterMenuScreensEvent
        │  BI 菜单全部映射到 StorageScreen
        ▼
BdPageHost（注入页）
        │  注册：BdPages.register
        ▼
AnvilWorkstationPage / CutWorkstationPage / ...
        │
        ├─ 在 BD 终端：作为入口，点击发 OpenStorageMenuPayload
        └─ 在对应 BI 菜单：作为内容页，显示存储 + 工作台 + 背包
```

旧版 1.20.1 仍然是旧自绘 GUI；本文只针对 1.21.1。

---

## 2. 关键文件

| 文件 | 作用 |
|---|---|
| `client/gui/workstation/WorkstationPage.kt` | 工作台页面基类、`State`/`Action`、`snapshot`/`handle`/`Content`、六个入口注册 |
| `client/gui/workstation/AnvilWorkstationPage.kt` | 铁砧页面 |
| `client/gui/workstation/CutWorkstationPage.kt` | 切石页面 |
| `client/gui/workstation/GrindWorkstationPage.kt` | 砂轮页面 |
| `client/gui/workstation/SmithWorkstationPage.kt` | 锻造页面 |
| `client/gui/workstation/EnchantWorkstationPage.kt` | 附魔页面 |
| `client/gui/workstation/EnchantMergeWorkstationPage.kt` | 附魔合并页面 |
| `client/gui/workstation/WorkstationStorageSection.kt` | 工作台页内的存储视图 UI（搜索/排序/分类/网格/翻页/右键菜单） |
| `client/gui/workstation/WorkstationPageInit.java` | 打开 BI 工作台菜单时，记录默认选中哪个工作台页 |
| `client/gui/workstation/ContentStateHelper.java` | 用 Java 桥接 CompixelUI 的 internal `contentState` |
| `mixin/BdPageSelectionMixin.java` | 设置 BD `BdPageSelection` 的初始页 |
| `feature/crafting/DimensionsStorageMenu.java` | 工作台菜单基类；`beginWorkstationSlots` 记录工作台槽位 |
| `init/ModMenus.java` | BI 菜单类型注册 |
| `network/payload/OpenStorageMenuPayload.java` | 客户端请求服务端打开工作台菜单 |
| `resources/beyond_integration.mixins.json` | 客户端 Mixin 列表 |

---

## 3. 运行流程

### 3.1 从 BD 终端进入工作台

1. BD 终端（`DimensionsNetMenu` 或 BD 合成菜单）显示六个注入页签。
2. 点击某个工作台页签，`BdPageSelection` 切到该页。
3. `WorkstationPage.Content` 发现 `state.current == false`，执行：
   ```kotlin
   LaunchedEffect(Unit) { scope.send(Action.Open) }
   ```
4. `handle(Action.Open)` 发送 `OpenStorageMenuPayload(mode, apoth)`。
5. 服务端 `OpenStorageMenuPayload.handle` 打开对应 BI 菜单：
   - 校验工作台是否启用/已激活；
   - 从玩家主网络取存储；
   - `sp.openMenu(...)`。
6. 客户端收到菜单后，`WorkstationGui.registerScreens` 注册的工厂创建 **BD `StorageScreen`**。

### 3.2 打开界面时默认选中工作台页

1. 工厂在构造 `StorageScreen` 前调用：
   ```kotlin
   WorkstationPageInit.prepare(menu)
   ```
   `prepare` 用 `instanceof` 判断 BI 菜单类型，得到页面 id：
   - `beyond_integration:workstation/anvil`
   - `.../cut`
   - `.../grind`
   - `.../smith`
   - `.../enchant`（包含 `DimensionsApothEnchantMenu`）
   - `.../enchant_merge`
   - 普通 `DimensionsStorageMenu`（STORAGE）返回 `null`，保持 `BdPages.MAIN`。
2. `BdPageSelectionMixin` 在 `BdPageSelection` 构造末尾 `take()` 这个 id，并调用 `open(id)`。
3. 因此 BI 工作台界面打开时，默认页就是对应工作台页，而不是 BD 存储主页。

### 3.3 工作台页内容

`WorkstationPage.snapshot` 在游戏线程执行：

- 判断当前菜单是不是本工作台（`instanceof`）。
- 取 `DimensionsStorageMenu` 的工作台槽位信息：
  - `workstationStart`
  - `workstationInputs`
  - `workstationResult`
- 取玩家背包槽位范围。
- 当前实现通过公共 `ComposeInventoryScreen` 取：
  - `contentState`（`UiStateBinding<StorageState, StorageAction>`，经 `ContentStateHelper` Java 桥接）
  - `inventory.adapter`（`BdSlotAdapter`）
  - `StorageLayout(menu)`（自己构造，提供 `storageStart`/`text`/玩家槽位）

`Content` 在 Compose 线程执行：

1. `WorkstationStorageSection`：存储视图（搜索/排序/分类/编辑/网格/滚动条/右键菜单）。
2. `renderWorkbench`：当前工作台的专属区域。
3. `WorkbenchInventory`：玩家背包。

布局顺序与旧截图一致：**存储 → 工作台 → 背包**。

### 3.4 页面切换

| 切换 | 行为 |
|---|---|
| 存储主页 → 当前工作台页 | BD 页面切换，瞬间完成，不重开菜单 |
| 当前工作台页 → 存储主页 | BD 页面切换；`BdPageSelection` 切到 `BdPages.MAIN` |
| 工作台 A → 工作台 B | 点击 B 的入口页，发 `OpenStorageMenuPayload`，服务端换菜单（菜单级切换） |
| 当前工作台自己的入口 | 页签已选中，点击不重新打开 |
| 设置页 | 屏幕左侧底部的 BD 设置页签 |

工作台之间的切换仍是菜单切换，会触发旧菜单的 `removed()`；各 BI 菜单的 `removed` 会调用 `cleanSlots` 归还工作台槽位物品。这是当前行为，不是无缝切换。

---

## 4. 状态与动作

### 4.1 `WorkstationPage.State`

```kotlin
data class State(
    val label: String,
    val current: Boolean,
    val start: Int,
    val inputs: Int,
    val result: Int,
    val playerSlots: List<Int>,
    val binding: UiStateBinding<StorageState, StorageAction>? = null,
    val layout: StorageLayout? = null,
    val slotMenu: SlotMenu? = null,
    val adapter: BdSlotAdapter<*>? = null,
    val menu: DimensionsNetMenu? = null,
    val extra: Any? = null,
)
```

说明：

- `binding` / `layout` / `adapter` / `menu` 只在 `current == true` 时填充；
- `extra` 是各工作台自己的值，由子类 `readExtra` 生成；
- `Content` 用 `binding.value`（Compose）读 `StorageState`，不再把 `storage` 放进 `State`；
- `State` 仍包含少量游戏对象引用（`binding`、`layout`、`adapter`、`menu`），这是复用 BD 存储界面的内部实现；普通页面建议只放不可变值。

### 4.1.1 传值给工作台页面

`WorkstationPage` 提供：

```kotlin
protected open fun readExtra(context: BdPageContext<DimensionsNetMenu>): Any? = null
```

子类在 `readExtra` 里把工作台自己的值放进 `State.extra`，`Content` / `renderWorkbench` 里取出：

```kotlin
class AnvilWorkstationPage : WorkstationPage(Workstation.ANVIL) {
    data class AnvilExtra(val name: String, val level: Int)

    override fun readExtra(context: BdPageContext<DimensionsNetMenu>): Any? =
        (context.menu as? DimensionsAnvilMenu)?.let { AnvilExtra(it.anvName, it.anvLevel) }

    @Composable
    override fun ColumnScope.renderWorkbench(
        state: WorkstationPage.State,
        scope: BdPageScope<WorkstationPage.Action>,
    ) {
        val extra = state.extra as? AnvilExtra
        if (extra != null) {
            BdSectionLabel("名称：${extra.name.ifEmpty { "（无）" }} · 等级：${extra.level}")
        }
        WorkbenchSlotRow("铁砧 · 工作台", state, scope)
    }
}
```

- `readExtra` 只在当前工作台页（`state.current == true`）调用，运行在游戏线程；
- 值来自菜单/玩家/缓存都可以；
- 需要回传动作时在 `Action` 增加类型，`handle` 处理；
- 需要服务端数据时用 `context.request(BdMenuActions...)` 或 BI payload 同步后再读。

如果工作台值很多，建议把 `WorkstationPage` 改成按工作台泛型状态（`BaseState` + 各页自己的 `data class`），避免 `Any?` 强转；当前先用 `extra` 保持改动小。

### 4.2 `Action`

```kotlin
sealed interface Action {
    data object Open : Action
    data class Storage(val action: StorageAction) : Action
}
```

- `Open`：入口页请求打开对应工作台菜单。
- `Storage`：把存储视图动作交给 `StorageScreen` 的 `contentState`（`binding.send`）。

`handle`：

```kotlin
override fun handle(context: BdPageContext<DimensionsNetMenu>, action: Action) {
    when (action) {
        Action.Open -> {
            if (menuClass().isInstance(context.menu)) return
            PacketHandler.sendToServer(OpenStorageMenuPayload(...))
        }
        is Action.Storage -> {
            // 当前实现：交给 StorageScreen 的公共 contentState
            bindingOf(context.screen)?.send(action.action)
        }
    }
}
```

### 4.3 线程约定

| 位置 | 线程 |
|---|---|
| `BdPage.snapshot` | 游戏线程 |
| `BdPage.handle` | 游戏线程 |
| `BdPage.Content` | Compose 线程 |
| `UiStateBinding.send` | 游戏线程 |
| `UiStateBinding.value` | Compose 线程（只读） |
| `BdSlotAdapter.resourceMenu` | 游戏线程 |
| `PacketHandler.sendToServer` | 客户端游戏线程/输入线程 |

不要在 `Content` 里直接访问菜单、物品堆、控制器；需要动作时用 `scope.send(Action...)` 交回 `handle`。

---

## 5. 工作台槽位元数据

`DimensionsStorageMenu` 新增：

```java
protected int beginWorkstationSlots(int inputs, boolean hasResult)
public int getWorkstationStart()
public int getWorkstationInputs()
public int getWorkstationResult()
```

约定：子类在添加工作台槽位前调用，先加 `inputs` 个输入槽，`hasResult=true` 时紧接着加 1 个结果槽。

当前调用：

| 菜单 | 调用 |
|---|---|
| `DimensionsAnvilMenu` | `beginWorkstationSlots(2, true)` |
| `DimensionsCutMenu` | `beginWorkstationSlots(1, true)` |
| `DimensionsGrindMenu` | `beginWorkstationSlots(2, true)` |
| `DimensionsSmithMenu` | `beginWorkstationSlots(3, true)` |
| `DimensionsEnchantMenu` | `beginWorkstationSlots(2, false)` |
| `DimensionsEnchantMergeMenu` | `beginWorkstationSlots(1, false)` |
| `DimensionsApothEnchantMenu` | 继承 `DimensionsEnchantMenu` 的记录 |

注意：槽位起始索引是在 `super(...)` 添加完网络存储槽和玩家背包之后才记录的；不要假设从 0 开始。

---

## 6. 新增一个工作台

以“新增 `XXX` 工作台”为例：

1. **菜单**
   - 新建 `DimensionsXxxMenu extends DimensionsStorageMenu`；
   - 构造时先 `super(...)`，再调用 `beginWorkstationSlots(n, hasResult)`，然后添加输入/结果槽；
   - 实现 `ICleanableWorkstation`（如果关闭时需要归还物品）；
   - 在 `ModMenus` 注册 `MenuType`。

2. **页面**
   - 在 `WorkstationPage.Workstation` 增加枚举项（`label`/`icon`/`id`）；
   - 新建 `XxxWorkstationPage : WorkstationPage(Workstation.XXX)`；
   - 重写 `renderWorkbench`，默认可以调用 `WorkbenchSlotRow` + `WorkbenchInventory` 的拆分版本；
   - 页面 id 自动为 `beyond_integration:workstation/<id>`。

3. **注册**
   - `WorkstationGui.onClientSetup` 里 `registerPage(DimensionsNetMenu::class.java, XxxWorkstationPage())`；
   - `WorkstationGui.registerScreens` 里把 `ModMenus.XXX` 注册到 BD `StorageScreen`。

4. **默认页**
   - `WorkstationPageInit.pageId` 增加 `menu instanceof DimensionsXxxMenu -> "xxx"`；
   - 注意判断顺序，子类要放在父类前面。

5. **文案/图标**
   - 在 `lang/zh_cn.json`、`en_us.json` 增加对应翻译；
   - 枚举里的 `label` 目前是硬编码中文，后续可改为 translation key。

6. **编译**
   - 使用 JDK 21，执行 `.\gradlew.bat classes`。

---

## 7. 自定义工作台界面

在对应的 `XxxWorkstationPage` 中重写：

```kotlin
@Composable
override fun ColumnScope.renderWorkbench(
    state: WorkstationPage.State,
    scope: BdPageScope<WorkstationPage.Action>,
) {
    // 自己的界面
}
```

可用元素：

- `state.start/inputs/result`：工作台槽位索引；
- `state.playerSlots`：玩家背包槽位；
- `scope.slots`：`ComposeMenuSlots`；
- `BdSlot` / `BdSlotGrid` / `BdInventorySection`；
- `BdSectionLabel`、`BdIcon`、`BdGlyphButton`、`BdChip`、`BdToggle`、`BdNumberEditor` 等 BD 控件；
- `WorkbenchSlotRow(...)`、`WorkbenchInventory(...)` 作为通用辅助。

如果工作台需要额外数据/动作：

1. 在 `State` 增加只读快照字段（在 `snapshot` 里从菜单读取）。
2. 在 `Action` 增加动作类型。
3. 在 `handle` 中处理动作（游戏线程）。
4. 需要服务端操作时：
   - 使用 `context.request(BdMenuActions...)`，并在客户端/服务端注册 `BdMenuActions`；
   - 或复用 BI 现有 payload（如 `SetAnvilNamePayload`、`SubmitEnchantMergePayload`、`RefreshEnchantPayload` 等）。

不要直接在 `Content` 里调用 `context.menu` 或发送网络包。

---

## 8. 存储视图实现

### 8.1 当前实现（公共 API，无 accessor Mixin）

工作台页通过 CompixelUI 的公共入口复用 BD `StorageScreen` 的存储状态与槽位：

- `ComposeInventoryScreen.getInventory()` → `ComposeMenuSlots`
- `ComposeMenuSlots.getAdapter()` → `BdSlotAdapter`
- `contentState` → `UiStateBinding<StorageState, StorageAction>`
- `StorageLayout(menu)` → `storageStart` / `text` / 玩家槽位

实现方式：

- `ContentStateHelper.java` 用 Java 调用 CompixelUI 的 internal `contentState`（见 8.2）；
- `WorkstationPage.snapshot` 取 `binding`、`layout`、`adapter`、`menu`；
- `WorkstationPage.Content` 用 `binding.value` 读 `StorageState`；
- `WorkstationStorageSection.kt` 渲染搜索/排序/分类/编辑/网格/翻页/右键菜单；
- `Action.Storage` 由 `WorkstationPage.handle` 调用 `binding.send(action)`；
- 右键菜单使用页面本地菜单（避免下层隐藏的 BD 主页面重复弹层）：
  - 页面显示时接管 `adapter.resourceMenu`；
  - 取出/加入分类时调用 adapter 原本的 `resourceMenu`（设置 controller 目标），再 `binding.send(Take/Add)` + `binding.send(Close)`。

这样状态与 BD 主页面完全共享：搜索、排序、分类、翻页、设置一致，且不依赖 accessor Mixin。

### 8.2 为什么需要 Java 桥接 / 版本风险

`StorageScreen` 继承 `ComposeInventoryScreen`，后者在 Kotlin 里把 `contentState` 声明为 `internal`，字节码虽然 public，但 Kotlin 模块外不能直接调用。因此：

- `ContentStateHelper.java` 用 Java 调用：
  ```java
  getContentState$dev_compixel_compixel_neoforge_1_21_1()
  ```
- 方法名带 CompixelUI 内部后缀：
  - CompixelUI 0.1.12 / Curse 9223999：`..._1_21_1`
  - 9224005：`..._26_3`
  - 换 CompixelUI 版本时需要同步修改 `ContentStateHelper`，或做反射兼容。

其他注意：

- `binding.value` 是 Compose 专用 getter，只能在 `@Composable` 里读；
- `binding.send` 应在游戏线程调用（`BdPage.handle` 或 adapter 回调），不要在 Compose 里直接发；
- `StorageScreen.controller` / `layout` / `StorageView` 仍是 private；当前不再依赖它们；
- `BdPageSelectionMixin` 仍需保留，因为 `BdPageSelection` 是 `StorageView` 内的局部 Compose 状态，没有公共 accessor；
- `StorageView` 等 UI composable 仍是 private，`WorkstationStorageSection.kt` 是复制/重建的 UI。

### 8.3 存储视图包含的元素

已实现：

- 搜索框 + 搜索语法帮助
- 主排序 / 次排序 / 倒序
- 分类标签、新建、下拉列表、拖动排序、右键编辑/移动/删除
- 分类编辑（名称、图标、搜索式、手选资源、语法 chip，内联面板）
- 存储网格、行滚动条
- 存储格右键：取 1 / 取指定数量 / 加入分类
- 玩家背包

未包含（仍在 BD 设置页/主页面）：

- 设置页的列/行、保留搜索、同步搜索、保留合成格
- 网络切换按钮
- BD 3×3 合成区（点击 BD 的“合成”页签仍会打开 BD 合成菜单）

---

## 9. 当前限制与 TODO

### 9.1 工作台专属 UI/动作尚未移植

目前六个页面只有“槽位行 + 背包”，旧版 GUI 的这些功能还没搬：

| 工作台 | 待补 |
|---|---|
| 铁砧 | 改名输入、等级/点数费用、错误提示、费用模式 |
| 切石 | 配方列表、选中索引、滚动、结果 |
| 砂轮 | 结果、经验、错误状态 |
| 锻造 | 产物预览、空槽提示、模板/底座/附加槽语义 |
| 附魔 | 三项费用、随机谜名、等级需求、3D 书本；神化三属性 |
| 附魔合并 | 候选附魔、等级选择、可合并性、费用、滑块 |

### 9.2 切换反馈

- 服务端禁用/未激活时，入口页会停在“正在打开…”；
- 需要增加失败反馈/回退或超时。

### 9.3 菜单切换清理

- 工作台 A → 工作台 B 是换菜单；
- 旧菜单 `removed()` 会调用 `cleanSlots`，工作台槽位物品会被退回/清空；
- 如果需要无缝切换并保留物品，需要统一 hub 菜单或持久化槽位。

### 9.4 动态高度

BD 3×3 的动态高度成立是因为 3×3 在**主页面内容**里，`chromeHeight(craftShown)`/`fitRows` 会计入它。

BI 工作台是注入页（`BdTabPage`，`matchParentSize`），不参与窗口高度；只改 `state.rows` 只会让主页面和注入页一起变矮，工作台仍然多出高度并滚动。因此：

- B+ 下不能直接做 BD 3×3 那种动态高度；
- 要做，必须让工作台内容进入主页面内容：
  1. 改回 BI 自有 `WorkstationScreen`（存储 + 工作台同一主内容）；
  2. 或给 BD `StorageScreen` 加主页面 section 扩展点。

### 9.5 其他

- 分类编辑器在内联面板中；BD 主页面的右侧编辑区仍可能因共享 `editing` 状态展开窗口宽度；
- `TemplateGui` 演示页仍注册，会出现在所有 BD/BI 存储界面；
- 旧版 Java GUI/Widget 残留（`LeftSidebarLayout`、`WorkstationRenderHelper`、`CostTooltipHelper`、`client/widget/*`）已无引用；
- 目前只做了编译验证（`classes`），未实机验证 Mixin 应用；
- 1.20.1 仍是旧自绘 GUI。

---

## 10. 构建与验证

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.11"
.\gradlew.bat classes --console=plain
```

- 本机 PATH 的 `java` 是 JDK 26，不能直接用于本项目 Gradle；
- `classes` 会同时编译 Kotlin 和 Java；
- 涉及 Mixin 的改动，编译通过不代表运行通过，最好进游戏验证：
  - 打开 BI 工作台是否默认选中工作台页；
  - 存储搜索/排序/分类/翻页/右键取物；
  - 切换存储页与工作台页；
  - 工作台 A → B 切换。

---

## 11. 快速 API 参考

### 11.1 `BdPage` 注入

```kotlin
object MyPage : BdPage<DimensionsNetMenu, MyState, MyAction> {
    override val icon = BdIcons.Info
    override fun title(context: BdPageContext<DimensionsNetMenu>) = Component.literal("My Page")
    override fun appliesTo(context: BdPageContext<DimensionsNetMenu>) = true
    override fun snapshot(context: BdPageContext<DimensionsNetMenu>): MyState = ...
    override fun handle(context: BdPageContext<DimensionsNetMenu>, action: MyAction) { ... }

    @Composable
    override fun ColumnScope.Content(state: MyState, scope: BdPageScope<MyAction>) {
        // Compose 内容
    }
}
```

注册：

```kotlin
BdPages.register(
    ResourceLocation.fromNamespaceAndPath(MODID, "my_page"),
    DimensionsNetMenu::class.java,
    MyPage,
    BdPages.last(),
)
```

### 11.2 公共存储 API

| API | 用途 |
|---|---|
| `ComposeInventoryScreen.getInventory()` | 当前界面的 `ComposeMenuSlots` |
| `ComposeMenuSlots.getAdapter()` | `MenuSlotAdapter`（可转 `BdSlotAdapter`） |
| `ComposeInventoryScreen.getContentState$...()` | `UiStateBinding<StorageState, StorageAction>` |
| `UiStateBinding.value` | Compose 读当前 `StorageState` |
| `UiStateBinding.send(action)` | 游戏线程发送 `StorageAction` |
| `StorageLayout(menu)` | 槽位起始、玩家槽位、`StorageText` |
| `StorageController` | 独立控制器（公共类；当前页面用 `contentState` 共享，不再直接取） |
| `StorageState` / `StorageAction` | 存储状态/动作 |
| `BdSlotGrid` / `BdScrollbar` / `BdSlot` | 槽位渲染/滚动 |
| `ContentStateHelper.storageState(screen)` | Java 桥接 internal `contentState` |

### 11.3 相关 Mixin / 辅助类

| 类 | 当前作用 | 备注 |
|---|---|---|
| `BdPageSelectionMixin` | 设置默认页 | 暂无公共 accessor，保留 |
| `ContentStateHelper` | Java 桥接 `contentState` | 不是 Mixin；版本名变化时改这里 |

当前已删除：

- `StorageScreenAccessor`
- `StorageScreenAccessorMixin`
- `BdInventoryScreenAccessor`

---

## 12. 文件树速查

```text
1.21.1_NEOFORGE/src/main/
├─ kotlin/com/solr98/beyondintegration/client/gui/workstation/
│  ├─ WorkstationPage.kt
│  ├─ AnvilWorkstationPage.kt
│  ├─ CutWorkstationPage.kt
│  ├─ GrindWorkstationPage.kt
│  ├─ SmithWorkstationPage.kt
│  ├─ EnchantWorkstationPage.kt
│  ├─ EnchantMergeWorkstationPage.kt
│  └─ WorkstationStorageSection.kt
├─ java/com/solr98/beyondintegration/
│  ├─ client/gui/workstation/
│  │  ├─ WorkstationPageInit.java
│  │  └─ ContentStateHelper.java
│  ├─ mixin/
│  │  └─ BdPageSelectionMixin.java
│  ├─ feature/crafting/DimensionsStorageMenu.java
│  └─ init/ModMenus.java
└─ resources/beyond_integration.mixins.json
```
