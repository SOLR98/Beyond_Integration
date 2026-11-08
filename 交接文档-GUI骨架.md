# Beyond Integration GUI 骨架交接文档

> 项目：`NEW_NEOFORGE_1.21.1`
> 当前模组版本：`1.0-SNAPSHOT`（未升版本）
> 基线：Minecraft 1.21.1 / NeoForge 21.1.238 / JDK 21 / Kotlin 2.4.10
> BD：0.8.0；CompixelUI：0.1.12
> 最后更新：2026-10-20

---

## 1. 项目定位

Beyond Integration（BI）是 Beyond Dimensions（BD）存储终端的附属扩展，不再单独实现一套终端。

当前路线：

```text
保留 BD 原生 DimensionsNetMenu / StorageScreen
+ 在 BD 存储菜单上追加工作台槽位
+ 用 BdPage 注入 6 个工作台页
+ 工作台页复用 BD 的 StorageController、槽位组件和分类 UI
```

旧项目仍在：

```text
C:\Users\16268\IdeaProjects\Beyond_Integration_all\1.21.1_NEOFORGE
```

作者扩展指南：

```text
NEW_NEOFORGE_1.21.1/BD-storage-page-integration-guide.md
```

---

## 2. 构建与运行

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.11"
.\gradlew.bat compileJava compileKotlin --console=plain
.\gradlew.bat build --console=plain
```

`runClient` 需要 JDK 21；PATH 默认的 JDK 26 会让 Gradle 报 `Unsupported class file major version 70`。

运行时依赖：

- Beyond Dimensions 0.8.0
- CompixelUI 0.1.12
- Kotlin for Forge 5.12.0

调试用 AutoJS：

```text
run/mods/AutoJS-neoforge-1.21.1-1.0.0.jar
run/mods/rhino-2101.2.7-build.81.jar
MCP: http://127.0.0.1:25585/mcp
```

---

## 3. 当前 GUI 结构

```text
BD StorageScreen（原生，不再替换）
├─ 左侧：存储 / 合成 / 设置 / 切换网络
├─ 6 个注入的工作台页（BdPage）
│  ├─ 搜索 / 排序
│  ├─ 完整分类标签栏
│  │  ├─ 标签、图标/漏斗、选中发光边
│  │  ├─ 拖动排序
│  │  ├─ 右键：编辑 / 移动 / 删除
│  │  ├─ 下拉列表
│  │  └─ 新建分类
│  ├─ 分类编辑区（右侧 Popup）
│  │  ├─ 图标 / 名称
│  │  ├─ 搜索式
│  │  ├─ 符合种数
│  │  ├─ 特定补充格
│  │  └─ 写法 chips
│  ├─ 存储网络网格（行数按本页可用高度计算）
│  ├─ 工作台槽位
│  │  ├─ 切石：候选配方列表 + 选中配方
│  │  ├─ 附魔：3 个附魔选项（名称/等级/门槛）
│  │  └─ 铁砧/砂轮/锻造/附魔合并：暂未接加工后端
│  └─ 背包（居中）
└─ 分类编辑区打开时窗口向右加宽
```

没有：

- 独立的 `WorkstationScreen`
- 替换 BD `StorageScreen` 的 `BDScreensMixin`
- 另一个 `StorageController`

---

## 4. 关键类

### 4.1 页面

| 文件 | 作用 |
| --- | --- |
| `bi/gui/WorkbenchPage.kt` | 6 个工作台页；`WorkbenchPageLayout` / `WorkbenchSlotRow` 是页面模板骨架 |
| `bi/gui/StorageSections.kt` | 搜索（含语法帮助）、主/次排序、存储网格、完整右键菜单 |
| `bi/gui/BdCategories.kt` | 移植 BD 的完整分类标签栏、下拉、右键菜单、编辑区 |
| `bi/gui/BiComposeInput.kt` | 后台 Compose 输入：点击 / 键盘 / 字符 |
| `bi/gui/BiPageNavigation.java` | 外部切换注入页；`BdPageSelectionMixin` 登记最近的选择对象 |

### 4.1.1 工作台后端

| 文件 | 作用 |
| --- | --- |
| `bi/workbench/WorkbenchServer.java` | 切石配方、附魔选项与执行、结果槽扣料 |
| `bi/workbench/BiWorkstationDataSlot.java` | 服务端当前工作台类型的 DataSlot |
| `bi/workbench/BiEnchantSeedDataSlot.java` | 附魔随机种子的 DataSlot |
| `bi/workbench/BiWorkbenchInputSlot.java` | 输入槽：附魔物品槽单件、青金石槽限制 |
| `bi/workbench/BiWorkbenchResultSlot.java` | 结果槽：只能取出，取出时扣料 |
| `bi/workbench/BiWorkbenchInputContainer.java` | 输入变化时通知 `WorkbenchServer` |
| `bi/workbench/BiWorkstations.java` | 六种工作台 id 与序号 |
| `bi/api/BiWorkbenchActions.java` | `SET_STATION` / `SELECT_CUT_RECIPE` / `ENCHANT` 菜单动作 |

### 4.2 Mixin

`src/main/resources/beyond_integration.mixins.json`：

```json
"mixins": [
  "AbstractContainerMenuAccessor",
  "BDBaseMenuAccessor",
  "DimensionsNetMenuMixin"
],
"client": [
  "BdPageSelectionMixin",
  "BdPageHostMixin",
  "BdMountedMixin",
  "BiComposeInputAccessor",
  "StorageScreenControllerAccessor"
]
```

| Mixin | 目标 | 作用 |
| --- | --- | --- |
| `AbstractContainerMenuAccessor` | `AbstractContainerMenu` | `@Invoker("addDataSlot")`，注册附魔/工作台类型的同步槽 |
| `BDBaseMenuAccessor` | `BDBaseMenu` | `@Invoker("addSlot")`，供菜单追加槽位 |
| `DimensionsNetMenuMixin` | `DimensionsNetMenu` | 构造末尾追加 3 输入 + 1 结果槽；注册 DataSlot；记录 BD 视口行列；`removed` 返还输入、丢弃结果预览 |
| `StorageScreenControllerAccessor` | `StorageScreen` | `@Accessor("controller")` 取共享 `StorageController` |
| `BiComposeInputAccessor` | `ComposeInventoryScreen` | `@Accessor("layer")` 取 CompixelUI 输入层 |
| `BdPageSelectionMixin` | `BdPageSelection` | 登记选择对象给 `BiPageNavigation`，并应用旧默认页 |
| `BdPageHostMixin` / `BdMountedMixin` | `BdPageHost` / `Mounted` | 注入页高度汇总，目前闲置 |

`DimensionsNetMenuMixin` 已移到 common `mixins`，否则服务端不会追加工作台槽位，会出现客户端 931、服务端 927 的槽位不一致。

### 4.3 API 接口

```text
bi/api/BiWorkbenchHolder.java   工作台槽位与工作台 id
bi/api/BiBDBaseMenu.java        addSlot 调用
bi/api/BiPage.java              （旧）页面高度声明
bi/api/BiPageHost.java          （旧）注入页高度汇总
bi/api/BiMounted.java           （旧）Mounted.page
```

---

## 5. 数据与线程

| 位置 | 线程 | 做什么 |
| --- | --- | --- |
| `WorkbenchPage.snapshot` | 客户端游戏线程 | 取 `StorageController.snapshot()`、工作台槽位号、背包槽位号、本页网格行数 |
| `WorkbenchPage.handle` | 客户端游戏线程 | 把 `StorageAction` 转交共享 `StorageController`；工作台动作转成菜单动作 |
| `Content` | Compose 线程 | 排版，通过 `scope.send` 提交动作 |
| `MenuAction` 处理器 | 服务器线程 | `SET_STATION` / `SELECT_CUT_RECIPE` / `ENCHANT`，由 `WorkbenchServer` 校验并执行 |

约定：

- 页面首次挂载时 `StorageScreen.controller` 还没初始化，`snapshot` 返回 `ready=false`，页面显示“正在准备存储网络…”。
- 不创建第二个 `StorageController`。
- 页面状态只放不可变值，不放菜单、玩家、可变 `ItemStack` 或后端对象。
- 搜索框使用本地输入缓冲，避免旧快照覆盖快速输入。
- 工作台类型、附魔费用/线索/等级、附魔随机种子通过 DataSlot 同步；切石候选列表由客户端按配方管理器计算，选中后发配方 id 给服务端校验。

---

## 6. 终端设置参数

工作台页共享终端的 `StorageController`，因此：

- 列数：`storage.columns`
- 行数：主页面为 `storage.rows`；工作台页不再沿用主页面的 viewport，而是按 `guiScaledHeight - SCREEN_MARGIN*2`、BD 的 `chromeHeight(false)=182`、BI 设定的工作台区高度（切石 +24、附魔 +83、其他 0）和 16px 余量自行计算，上限为用户设定的 `preferredRows`
- 搜索：`StorageAction.Search`
- 排序：`StorageAction.Sort` / `Reverse`
- 分类：`StorageAction.SelectCategory`
- 关闭后保留搜索、同步配方查看器：继续走 BD 原配置

这样从存储视图或 3×3 合成视图切到工作台页时，网格行数一致；页面区高度仍由主页面当前模式决定，切换前若停在 3×3 视图，工作台页下方可能多出一截空白（BD 的 `BdTabPage` 用 `matchParentSize`，注入页改不了）。

分类编辑区打开时，BD `StorageView` 会因为 `state.editor != null` 自动加宽窗口；工作台页在右侧留出编辑区宽度，并用独立 Popup 渲染编辑区，避免 `BdTabPage` 的滚动/命中层影响输入。

---

## 7. 工作台槽位

`DimensionsNetMenuMixin` 在 `DimensionsNetMenu` 四参数构造 `RETURN` 追加：

```text
3 个输入槽（固定 3 格，按工作台类型显示前 N 个）
1 个结果槽
```

记录：

```text
biWorkbenchStart
biWorkbenchInputs = 3
biWorkbenchResult
```

关闭菜单时 `removed` 把输入容器的物品还给玩家，避免 `SimpleContainer` 随菜单丢失；结果槽只是按输入算出的预览，关闭时直接丢弃，不能返还，否则会和输入材料一起被复制。

加工逻辑：

- 切石：客户端按输入物品列出切石配方，点击后发配方 id；服务端校验配方、把产物写进结果槽，取出时扣 1 个输入并播放切石音效。
- 附魔：服务端按输入物品与固定功率 15 计算三个选项（等级门槛 + 附魔线索），通过 DataSlot 同步；点击选项时校验经验等级与青金石数量，执行 `onEnchantmentPerformed`、消耗青金石、应用附魔并刷新选项。
- 铁砧 / 砂轮 / 锻造 / 附魔合并：结果槽暂时不产出，只保留槽位。

当前限制：

- 输入槽没有持久化到玩家存档或真实材料区。
- 快速转移仍使用 BD 原有路线，工作台槽位会被归到 `crafting` 组。
- 切石取下产物会立刻用剩余输入重算；没有连续合成/批量取出逻辑。

---

## 8. 调试流程

脚本：

```text
NEW_NEOFORGE_1.21.1/scripts/ai-debug-client.ps1
```

用法：

```powershell
# 复用已运行客户端
powershell -ExecutionPolicy Bypass -File scripts\ai-debug-client.ps1 -NoLaunch -ShotName workbench

# 启动客户端并立即返回
powershell -ExecutionPolicy Bypass -File scripts\ai-debug-client.ps1
```

脚本行为：

1. 检查/补齐 `run/mods` 里的 AutoJS 与 Rhino。
2. 用 WMI 创建 `runClient`，命令立即返回，不占工具进程。
3. 等待 MCP 可用，并检查 `AutoJS.where()` 与玩家是否存在。
4. 不在世界时进入 `新的世界`。
5. 构造临时 BD 存储终端。
6. 用 `BiComposeInput` 直接点击 Compose 页签（不依赖窗口焦点）。
7. 截图到 `run/debug-shots/<名称>.png`。

注意：

- `/health` 只表示 HTTP 服务存活，不代表客户端可 eval。
- MCP 启动加载阶段 `eval_js` 可能超时；脚本用 `Wait-GameReady` 等待。
- `BiComposeInput` 走 CompixelUI `ComposeLayer`，后台/失焦仍可操作；前提是 `pauseOnLostFocus=false`，世界继续 tick。

---

## 9. 当前已知问题

1. 铁砧、砂轮、锻造、附魔合并还没有加工后端，结果槽不产出。
2. 工作台输入物品在关闭菜单时返还玩家，不是“退回存储 / 背包 / 掉落”的完整策略。
3. 存储格子右键菜单已补齐：资源名、取 1、指定数量、加入分类（分类多时滚动），位置在按下处。
4. 分类编辑区是右侧 Popup，不是 BD 主页面那种完全并排的面板。
5. 工作台页行数已改为按屏幕高度、BD `chromeHeight(false)`、BI 工作台区高度和 16px 余量计算，上限是 `preferredRows`；但页面区高度仍由主页面当前模式决定，从 3×3 合成视图切过来时下方会多出空白。
6. `BdPageHostMixin`、`BdMountedMixin`、`WorkstationPageInit`、`WorkstationLauncherPage`、`BiStorageScreen` 和 `bi/menu` 下的旧工作台菜单目前是遗留/未注册代码，可后续清理。
7. `BiPage` / `BiPageHost` 旧高度接口暂时没有生产调用方。
8. 工作台槽位在快速转移路线里归入 `crafting` 组，未单独建组。
9. 没有 JEI / EMI 的 9×9 或第三方加工转移实现。
10. 测试时新建的分类需要清理，避免污染 `config/beyonddimensions/storage_categories.json`。
11. 附魔功率固定为 15（等价满书架），配置化与书架检测待补。
12. 用 `player.openMenu` 重复打开 `DimensionsNetMenu` 时曾出现 NPE（服务端 `containerMenu` 落回 `InventoryMenu`），真实终端物品的首次打开不受影响，待进一步定位。

---

## 10. 文件树

```text
NEW_NEOFORGE_1.21.1/
├─ BD-storage-page-integration-guide.md
├─ 工作台页面模板.md
├─ scripts/ai-debug-client.ps1
├─ run/mods/
│  ├─ AutoJS-neoforge-1.21.1-1.0.0.jar
│  └─ rhino-2101.2.7-build.81.jar
└─ src/main/
   ├─ java/com/solr98/
   │  ├─ bi/api/
   │  │  ├─ BiBDBaseMenu.java
   │  │  ├─ BiMounted.java
   │  │  ├─ BiPage.java
   │  │  ├─ BiPageHost.java
   │  │  ├─ BiWorkbenchActions.java
   │  │  └─ BiWorkbenchHolder.java
   │  ├─ bi/gui/
   │  │  ├─ BiComposeInput.java
   │  │  ├─ BiPageNavigation.java
   │  │  └─ WorkstationPageInit.java
   │  ├─ bi/workbench/
   │  │  ├─ BiEnchantSeedDataSlot.java
   │  │  ├─ BiWorkbenchInputContainer.java
   │  │  ├─ BiWorkbenchInputSlot.java
   │  │  ├─ BiWorkbenchResultSlot.java
   │  │  ├─ BiWorkstationDataSlot.java
   │  │  ├─ BiWorkstations.java
   │  │  └─ WorkbenchServer.java
   │  ├─ bi/menu/
   │  │  ├─ WorkstationMenu.java
   │  │  ├─ AnvilWorkstationMenu.java
   │  │  ├─ CutWorkstationMenu.java
   │  │  ├─ GrindWorkstationMenu.java
   │  │  ├─ SmithWorkstationMenu.java
   │  │  ├─ EnchantWorkstationMenu.java
   │  │  ├─ EnchantMergeWorkstationMenu.java
   │  │  ├─ ModMenus.java
   │  │  └─ WorkstationMenuOpener.java
   │  ├─ command/GuiCommand.java
   │  └─ mixin/
   │     ├─ AbstractContainerMenuAccessor.java
   │     ├─ BDBaseMenuAccessor.java
   │     ├─ BdMountedMixin.java
   │     ├─ BdPageHostMixin.java
   │     ├─ BdPageSelectionMixin.java
   │     ├─ BiComposeInputAccessor.java
   │     ├─ DimensionsNetMenuMixin.java
   │     └─ StorageScreenControllerAccessor.java
   ├─ kotlin/com/solr98/
   │  ├─ beyondintegration.kt
   │  ├─ bi/gui/
   │  │  ├─ BdCategories.kt
   │  │  ├─ BiStorageScreen.kt
   │  │  ├─ StorageSections.kt
   │  │  ├─ WorkbenchPage.kt
   │  │  └─ WorkstationLauncherPage.kt
   │  ├─ bi/network/
   │  │  ├─ OpenWorkstationPayload.kt
   │  │  └─ PacketRegistrar.kt
   │  └─ block/ModBlocks.kt
   └─ resources/
      ├─ beyond_integration.mixins.json
      └─ assets/beyond_integration/...
```

---

## 11. 下一步

优先级：

1. 在真实终端菜单里验证切石扣料/结果刷新与附魔扣经验/青金石；定位 `player.openMenu` 重复打开时的 NPE。
2. 补铁砧、砂轮、锻造、附魔合并的加工后端。
3. 新工作台按 `工作台页面模板.md` 接入，界面骨架已统一。
4. 消除主页面模式导致的页面区高度差：切到注入页时统一回存储视图，或给 `BdTabPage` 加固定高度。
5. 清理遗留页面/Mixin/网络包。
6. 按作者指南实现 9×9 / Re:Avaritia 和 JEI/EMI 专用转移。

---

## 12. 注意事项

- 本机 PATH 默认 Java 是 JDK 26，本项目必须用 JDK 21。
- KFF 的 Kotlin `object` 事件订阅方法不要加 `@JvmStatic`。
- 不要递归遍历 `libs/`。
- 不要修改版本号，除非明确要求发布/升版本。
- 新项目包名 `com.solr98`，GUI 在 `com.solr98.bi`；旧项目是 `com.solr98.beyondintegration`，不要混用。
- 作者指南基线提交：`736edc2bcd508389d78a1801e6c552e7c25188c2`（BD 0.8.0）。
