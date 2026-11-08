# Beyond Dimensions API 文档（面向 BI 1.21.1）

> 适用分支：`1.21.1_NEOFORGE`
> 项目实际依赖：`curse.maven:beyond-dimensions-1222890:9224058`
> CompixelUI：编译 `0.1.12`，运行时 `9223999`
> 源码参考：`../new_BD/BeyondDimensions-1.21.1`（可能比依赖版本新，签名以实际 jar / `javap` 为准）
> 包前缀：`com.wintercogs.beyonddimensions`
> 相关文档：`开发文档-BD-可用数据.md`、`开发文档-工作台GUI.md`

---

## 0. 最常用入口

```java
// 玩家主网络
DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
if (net == null) return;

// 网络存储
UnifiedStorage storage = net.getUnifiedStorage();

// 物品键
ItemStackKey key = new ItemStackKey(stack);

// 查数量
long amount = storage.getStackByKey(key).amount();

// 插入：返回“没插进去的余量”
KeyAmount leftover = storage.insert(key, stack.getCount(), false);

// 提取：返回“实际取出的量”
KeyAmount got = storage.extract(key, 64, true);
```

客户端 UI 入口：

```kotlin
// BdPage.snapshot / handle 里：context.screen 就是 BD StorageScreen
val screen = context.screen as ComposeInventoryScreen<DimensionsNetMenu, StorageState, StorageAction>
val binding = ContentStateHelper.storageState(screen) // Java 桥接 internal getContentState
val inventory = screen.inventory
val adapter = inventory.adapter as? BdSlotAdapter<*>
// binding.value 只能在 @Composable 里读
```

> `getContentState` 在 CompixelUI 的 Kotlin 模块里是 `internal`，Kotlin 模块外不能直接调用；BI 用 `ContentStateHelper.java` 桥接。详见 5.3。

---

## 1. 网络 API

### 1.1 `api.dimensionnet.DimensionsNet`

服务端权威网络对象，继承 `SavedData`。

静态查找/创建：

| 方法 | 说明 |
|---|---|
| `getNetFromId(int id)` | 按 id 取网络，可能为 null |
| `getNetFromPlayer(Player)` | 玩家所属的某个网络 |
| `getPrimaryNetFromPlayer(Player)` | 玩家主网络，可能为 null |
| `getAllNetFromPlayer(Player)` | 玩家所属全部网络 |
| `hasAnyNet(Player)` / `hasPrimaryNet(Player)` | 是否加入/有主网络 |
| `createNewNetForPlayer(Player, long slotCap, int slotMax)` | 为玩家创建网络 |
| `setPrimaryNetForPlayer(Player, @Nullable DimensionsNet)` | 设置主网络 |
| `clearPrimaryNetForPlayer(Player)` | 清除主网络 |
| `getNetworkName(int netId, @Nullable String customName)` | 显示名 |

实例数据：

| 方法 | 说明 |
|---|---|
| `getId()` | 网络 id |
| `getCustomName()` / `hasCustomName()` / `setCustomName(String)` | 自定义名 |
| `getNetworkName()` | 显示名 Component |
| `getOwner()` / `setOwner(UUID)` | 所有者 |
| `getManagers()` / `addManager(UUID)` / `removeManager(UUID)` | 管理员 |
| `getPlayers()` / `addPlayer(UUID)` / `removePlayer(UUID)` / `leavePlayer(UUID)` | 成员 |
| `isOwner(Player/UUID)` / `isManager(Player/UUID)` | 权限判断 |
| `getPlayerPermissionInfoMap(server)` | 玩家权限信息 |
| `getUnifiedStorage()` | 统一存储，非 null |
| `mergeOtherNet(DimensionsNet)` | 合并 |
| `destroySelf()` | 销毁 |
| `getOrCreateData(NetDataType<T>)` | 取/建挂接数据 |
| `getExistingData(NetDataType<T>)` | 取已有挂接数据 Optional |
| `hasData` / `setData` / `updateData` / `removeData` | 挂接数据读写 |

### 1.2 网络挂接数据 `NetDataType<T>`

```java
public static final NetDataType<MyData> MY_DATA = NetDataType
    .<MyData>builder(MyData::new)
    .codec(MyData.CODEC)          // 不提供时只在内存中
    .merge(MyData::merge)         // 必须提供
    .build();
```

- 用 `net.getOrCreateData(MY_DATA)` / `updateData` / `getExistingData`。
- 网络合并时会调用 `merge`。
- 网络销毁事件可通过 `Destroyed#getData(MY_DATA)` 取到数据。

### 1.3 权限

`NetPermissionlevel`：`Member`、`Manager`、`Owner`。

相关类：

- `PlayerPermissionInfo`
- `PrimaryNetOption` / `PrimaryNetSwitchHelper`
- `NetControlAction` / `PrimaryNetSwitchAction`
- `PlayerNetIndex` / `NetRegistryIndex`
- `NetTickTasks`

### 1.4 事件

`api.event.dimensionnet`：

| 事件 | 说明 |
|---|---|
| `DimensionsNetEvent.Created` | 网络创建完成；可放初始物资/调容量 |
| `DimensionsNetEvent.Destroyed` | 网络销毁；带 id/名字/存储/所有者/管理员/成员/挂接数据 |
| `DimensionsNetEvent.OwnerChanged` | 所有权在同一网络内变更 |
| `DimensionsNetEvent.MemberChanged` | 加入/退出/踢出/升降级 |
| `NetedBlockEvent.Bound` / `Unbound` | 网络方块绑定/解绑（仅服务端） |
| `NetedItemEvent.Bound` / `Unbound` | 网络物品主动绑定/解绑（仅服务端） |

---

## 2. 存储 API

### 2.1 `IStackHandler`

统一存储/机器存储的基础接口：

| 方法 | 说明 |
|---|---|
| `List<KeyAmount> getStorage()` | 当前全部资源及数量 |
| `int getSlots()` | 槽位数量 |
| `void clearStorage()` | 清空 |
| `boolean hasStack(IStackKey<?>)` | 是否有这种资源 |
| `void setStackDirectly(int slot, IStackKey<?>, long amount)` | 直接设槽 |
| `void addStackDirectly(IStackKey<?>, long amount)` | 直接加 |
| `KeyAmount insert(int slot, IStackKey<?>, long amount, boolean simulate)` | 指定槽插入，返回余量 |
| `KeyAmount insert(IStackKey<?>, long amount, boolean simulate)` | 任意槽插入，返回余量 |
| `KeyAmount extract(int slot, long amount, boolean simulate)` | 指定槽取出，返回实际取出量 |
| `KeyAmount extract(IStackKey<?>, long amount, boolean simulate[, boolean fuzzy])` | 按资源取出，返回实际取出量 |
| `List<KeyAmount> getFuzzyMatches(IStackKey<?>)` | 模糊匹配的所有变体 |
| `long getSlotCapacity(int slot)` | 单槽容量 |
| `boolean isStackValid(int slot, IStackKey<?>)` | 槽位是否接受 |
| `boolean isEmpty()` | 是否为空 |
| `void onChange()` | 通知变化 |

**返回值约定（重要）：**

- `insert` 返回 **余量**（没插进去的部分）；
- `extract` 返回 **实际取出的量**。

### 2.2 `AbstractUnorderedStackHandler`

无序存储实现，`UnifiedStorage` 的基类。额外能力：

| 方法 | 说明 |
|---|---|
| `getStackBySlot(int)` / `getStackByKey(IStackKey<?>)` | 按槽/键查询 |
| `setAmountByKey(IStackKey<?>, long)` | 设数量 |
| `getCreationTimeMap()` / `getLastModifiedTimeMap()` | 资源加入/最后修改时间 |
| `setCreationTime` / `setLastModifiedTime` | 设置时间 |
| `subscribeAny(owner, listener)` | 任意变化订阅 |
| `subscribeDelta(owner, listener)` | 增量变化订阅 |
| `subscribeAnyWeak` / `subscribeDeltaWeak` | 弱引用订阅 |
| `setSlotCapacity(long)` / `setSlotMaxSize(int)` | 容量/槽数上限 |
| `getZeroPolicy` / `setZeroPolicy` | 数量为 0 时保留/移除 |
| `getUiTimestampPolicy` / `setUiTimestampPolicy` | UI 时间戳策略 |
| `serializeNBT` / `deserializeNBT` | 持久化 |

### 2.3 `UnifiedStorage`

网络统一存储，`extends UnorderedStackHandlerRemoveZero`。

| 方法 | 说明 |
|---|---|
| `getNet()` | 所属网络 |
| `onChange()` | 通知网络变更 |
| `insert(key, amount, simulate[, admits])` | 插入；返回余量 |
| `extract(slot, amount, simulate)` | 按槽取出 |
| `extract(key, amount, simulate, fuzzy)` | 按资源取出 |
| `extract(tagKey, amount, simulate)` | 按标签取出 |
| `extractInto(...)` | 带接收/放置回调的取出 |
| `storedAmount(key)` / `slotsInUse()` | 数量/占用槽数 |

### 2.4 存储规则

`api.dimensionnet.rule`：

- `UnifiedStorageRules.insert(id, key, rule, placement)`
- `UnifiedStorageRules.insertMatching(id, predicate, rule, placement)`
- `UnifiedStorageRules.extract(...)`
- `UnifiedStorageRules.extractMatching(...)`
- `UnifiedStorageRules.removeInsert(id)` / `removeExtract(id)`
- `UnifiedStorageRules.insertIds()` / `extractIds()` / `insertTable()` / `extractTable()`

规则接口：

- `InsertRule.plan(StorageRequest) -> @Nullable InsertPlan`
- `ExtractRule.plan(StorageRequest) -> @Nullable ExtractPlan`
- `StorageEffect` / `StorageRequest` / `InsertPlan` / `ExtractPlan`

约束：规则只能读取，不能直接改存储；变化写在计划里，副作用放 `onCommit`；模拟和执行都会调用，同样状态必须给出同样计划。

---

## 3. 资源键 API

### 3.1 `IStackKey<T>`

| 方法 | 说明 |
|---|---|
| `getTypeId()` | 资源类型 id |
| `getReadOnlyStack()` | 只读原版堆叠 |
| `getStackClass()` / `getSourceClass()` | 原版堆叠/来源类型 |
| `getModId()` | 来源模组 |
| `getSourceId()` | 来源注册 id |
| `isEmpty()` | 是否空 |
| `copyStack()` / `copyStackWithCount(long)` | 复制 |
| `getVanillaMaxStackSize()` / `getCustomMaxStackSize()` | 最大堆叠 |
| `hasTag(TagKey<?>)` / `getTags()` | 标签 |
| `isSame(IStackKey<?>)` | 同类型（模糊） |
| `isSameTypeSameComponents(IStackKey<?>)` | 同类型且组件相同 |
| `getRender()` | 渲染器 |
| `serialize(buf)` | 网络序列化 |

编解码：

- `IStackKey.CODEC` / `IStackKey.STRICT_CODEC`
- `IStackKey.STREAM_CODEC`

### 3.2 内置实现

| 类 | 用途 |
|---|---|
| `ItemStackKey` | 物品 |
| `FluidStackKey` | 流体 |
| `EnergyStackKey` / `LongStackKey` | 能量/长整型资源 |
| `EmptyStackKey` | 空资源 |

常用：

```java
ItemStackKey itemKey = new ItemStackKey(stack);
ItemStackKey itemKey1 = new ItemStackKey(stack.copyWithCount(1));
long max = itemKey.getVanillaMaxStackSize();

FluidStackKey fluidKey = new FluidStackKey(new FluidStack(fluid, 1));
EnergyStackKey energyKey = EnergyStackKey.INSTANCE;
```

### 3.3 `KeyAmount`

```java
public record KeyAmount(IStackKey<?> key, long amount)
```

- `key()` / `amount()`
- `isEmpty()`
- `toStack()`
- `CODEC` / `STREAM_CODEC`

### 3.4 `ResourceContents`

`common.menu.interaction.ResourceContents`：

```java
IStackKey<?> contents = ResourceContents.of(itemStack);
```

- 经验棒 → 经验流体；
- 带存储能力的容器 → 第一份内容物；
- 无内容返回 null。

### 3.5 自定义资源类型

- `IStackKey` 实现；
- `StackKeyRegistry.registerType(IStackKey<?>)`；
- 能力/渲染等通过 `CapabilityHelper`、`IStackRender`、`StackKeyRegistry` 接入。

---

## 4. 菜单 / 服务端 API

### 4.1 `BDBaseMenu`

| 成员 | 说明 |
|---|---|
| `player` | 菜单玩家 |
| `commands()` | `BDMenuCommands` |
| `resources()` | `BDMenuResources` |
| `menuSync()` | CompixelUI `MenuSync`（`SyncedMenu`） |
| `inventoryStartIndex` / `inventoryEndIndex` | 玩家背包槽位范围 |
| `slotGroupSyncs` | 槽位组同步 |
| `quickMoveRoutes()` | 快速移动路由 |
| `customClickHandler(...)` | 自定义点击处理 |
| `clicked(...)` / `quickMoveStack(...)` | 原版容器点击/快速移动 |
| `stillValid(Player)` | 菜单有效性 |

### 4.2 `DimensionsNetMenu`

客户端/服务端共用：

| 字段/方法 | 说明 |
|---|---|
| `maxLines` / `lineData` / `maxLineData` | 行数/当前首行/最大首行 |
| `storage` | `AbstractUnorderedStackHandler` |
| `clientNetStorage` | 客户端镜像（可空） |
| `hasShiftDown` | Shift 状态 |
| `getStorageStartIndex()` / `getStorageEndIndex()` | 存储槽范围 |
| `getColumns()` / `getLines()` / `setLines(int)` | 行列 |
| `setViewport(columns, rows)` | 设置视口并重建索引 |
| `buildIndexList()` | 重建当前视图索引 |
| `loadIndexList(ArrayList<Integer>)` | 载入索引 |
| `loadSearchText(String)` | 设置搜索 |
| `markForceAllUpdateClientView()` | 强制全量刷新 |
| `setCategory(ClientNetStorage.CategoryFilter)` | 设置分类过滤 |
| `updateViewerStorage(boolean onlyAmountUpdate)` | 刷新视图 |
| `updateScrollLineData(int dataSize)` | 更新滚动 |
| `networkId()` / `networkName()` | 网络信息 |
| `stillValid(Player)` / `removed(Player)` | 生命周期 |

### 4.3 `DimensionsCraftMenu`

| 成员 | 说明 |
|---|---|
| `craftSlots` / `resultSlots` | 合成格/结果容器 |
| `craftSlotStartIndex` / `craftSlotEndIndex` | 合成格槽位范围 |
| `resultSlotIndex` | 结果槽索引 |
| `isCraftResultSlot(index)` / `isCraftGridSlot(index)` | 槽位判断 |
| `clearCraftGrid()` | 清空合成格 |
| `transferRecipe(keys, amounts[, compressOverflow])` | 从网络填配方 |
| `craftBatch(Runnable)` | 批量合成 |
| `cleanCraftSlots(boolean toStorageFirst)` | 归还合成格 |
| `PlayerCraftingGrid.of(player)` | 玩家合成格数据 |

### 4.4 `DimensionsCraftMenuTerminal`

- 终端方块/物品的合成菜单；
- 合成格使用 `PlayerCraftingGrid`；
- `stillValid` 检查网络/方块/终端物品。

### 4.5 `SwitchMenuProvider`

从一个 BD 菜单直接换到另一个：

```java
player.openMenu(new SwitchMenuProvider(
    (id, inv, p) -> new DimensionsNetMenu(...),
    Component.translatable("menu.title.beyonddimensions.dimensionnetmenu")
));
```

`shouldTriggerClientSideContainerClosingOnOpen() == false`，避免鼠标被收起/回中。

### 4.6 `BDMenuCommands`

| 方法 | 说明 |
|---|---|
| `click(slot, KeyAmount, button, modifiers)` | 槽位点击 |
| `take(slot, IStackKey<?>, amount)` | 取出 |
| `ghost(slot, KeyAmount)` | 幽灵槽 |
| `recipe(keys, amounts, compress)` | 配方填充 |
| `returnCrafting(boolean toStorage)` | 归还合成格 |
| `preference(boolean toStorage, boolean keep)` | 合成格偏好 |
| `flushPreference()` | 提交偏好 |

### 4.7 槽位交互

`api.menu.interaction`：

```java
SlotInteractions.register(
    ResourceLocation.fromNamespaceAndPath("example", "my_click"),
    new SlotInteractions.Interaction() {
        public boolean applies(SlotClick click) { ... }
        public boolean perform(SlotClick click) { ... }
    },
    SlotInteractions.before(ResourceLocation.fromNamespaceAndPath("beyonddimensions", "quick_move_vanilla"))
);
```

`SlotClick` 可用数据见 `开发文档-BD-可用数据.md`。

### 4.8 `BdMenuActions`

```java
MenuAction<DimensionsNetMenu, Integer> PING =
    MenuAction.of("example:ping", SyncCodecs.INT, (menu, player, value) -> {
        // 服务端线程
        return true;
    });

BdMenuActions.register(DimensionsNetMenu.class, PING);
```

页面中：

```kotlin
context.request(PING, 3)
```

---

## 5. 客户端 UI API

### 5.1 `BdPage` / `BdPages`

```kotlin
interface BdPage<M : AbstractContainerMenu, S, A> {
    val icon: BdPixelIcon
    fun title(context: BdPageContext<M>): Component
    fun appliesTo(context: BdPageContext<M>): Boolean = true
    fun snapshot(context: BdPageContext<M>): S
    fun handle(context: BdPageContext<M>, action: A) {}
    @Composable fun ColumnScope.Content(state: S, scope: BdPageScope<A>)
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

`BdPageContext`：

- `menu`：客户端菜单
- `screen`：当前 `Screen`
- `minecraft` / `player`
- `request(action, value)`：发送 `BdMenuActions`
- `requestClose()`

`BdPageScope`：

- `shown`
- `slots: ComposeMenuSlots<*>?`
- `send(action)`
- `open(pageId)`
- `requestClose()`

### 5.2 客户端屏幕基类

| 类 | 用途 |
|---|---|
| `BdInventoryScreen<M,S,A>` | 带槽位的 BD 菜单界面（继承 CompixelUI） |
| `BdMenuScreen<M,S,A>` | 不带槽位的 BD 菜单界面 |
| `BdPageHost` | 挂载注入页、页签、页面 |
| `BdSlotAdapter` | 虚拟资源槽适配、右键菜单钩子 |
| `BdSlotViews` | `BdSlot` / `BdSlotGrid` / `BdPlayerInventory` / `BdInventorySection` / `playerSlotIds` 等 |

### 5.3 CompixelUI 公共状态/槽位

`StorageScreen` / `BdInventoryScreen` 继承 `ComposeInventoryScreen`，公开：

```java
public final ComposeMenuSlots<M> getInventory();
public final UiStateBinding<S, A> getContentState$dev_compixel_compixel_neoforge_1_21_1();
```

用途：

- `getInventory()` → 当前界面的 `ComposeMenuSlots`；
- `getInventory().getAdapter()` → `MenuSlotAdapter`（可转 `BdSlotAdapter`）；
- `getContentState...()` → `UiStateBinding<StorageState, StorageAction>`；
  - `binding.value`（Compose 读）→ 当前 `StorageState`；
  - `binding.send(action)`（游戏线程）→ 交给 `StorageScreen.handle`。

注意：

- `contentState` 在 Kotlin 里是 `internal`，Kotlin 模块外不能直接写 `screen.`getContentState$...`()`；
  - Java 可以直接调用（字节码 public）；
  - BI 用 `ContentStateHelper.java` 桥接（见 `开发文档-工作台GUI.md` 8.2）；
  - 也可以用反射，但不推荐。
- 方法名带 CompixelUI 内部后缀，版本变化可能改（9224005 为 `_26_3`）。

### 5.4 存储界面类

| 类 | 用途 |
|---|---|
| `StorageScreen` | 三种存储菜单共用的 BD 界面（final） |
| `StorageController` | 存储状态/动作控制器（游戏线程） |
| `StorageState` | 不可变界面状态 |
| `StorageAction` | 存储动作 sealed interface |
| `StorageLayout` | 槽位索引、玩家槽位、图标、文字 |
| `StorageText` | 界面文案 |
| `StorageCategories` | 客户端分类持久化 |
| `CategoryTab` / `CategoryEditor` / `SlotMenu` / `CraftState` / `RecipeChoice` | 存储界面数据 |

私有/未公开：

- `StorageScreen.controller` / `layout`（private）
- `StorageScreen.StorageView` / `Toolbar` / `CategoryTabs` / `SlotMenuPopup` 等（private）
- `BdPageSelection`（StorageView 局部 Compose 状态，无公共 accessor）

### 5.5 客户端控件

`client.ui.kit`：

- `BdTabbedWindow` / `BdRailTab` / `BdMainPage` / `BdTabPage`
- `BdWindow` / `BdHeader` / `BdSectionLabel`
- `BdSearchField` / `BdTextField` / `BdTextArea`
- `BdChip` / `BdSegmented` / `BdToggle` / `BdDropdown` / `BdCycler`
- `BdNumberEditor` / `BdScrollbar` / `BdScrollColumn`
- `BdPopover` / `BdPointMenu` / `BdMenuRow` / `BdTooltip`
- `BdGlyphButton` / `BdIcon` / `BdIcons` / `BdPixelIcon`
- `bdInset` / `bdRaised` / `bdSlotFrame` / `bdClickable` / `brackets` / `glowLine`（部分 internal）
- `BdBackdrop` / `BdScreenFrame`

主题：`client.ui.theme.Bd` / `BdColors` / `BdTheme` / `BdDesign`。

---

## 6. 网络包 / 快捷键

| 类 | 用途 |
|---|---|
| `OpenNetGuiPacket(String uuid, NetMenuType target)` | 打开网络界面 |
| `NetMenuType` | `NET_MENU` / `NET_CRAFT_MENU` / `NET_CRAFT_TERMINAL` |
| `OpenPrimaryNetSwitcherPacket` | 打开主网络切换 |
| `BDShortKeys.OPEN_GUI_KEY` | 默认 O |
| `BDShortKeys.OPEN_TERMINAL_QUICK_KEY` | 默认 P |
| `BDShortKeys.MAIN_HAND_ITEM_TRANSFER_KEY` | 鼠标中键 |
| `BDShortKeys.TOGGLE_MAGNET_*` / `OPEN_MAGNET_GUI_KEY` | 磁铁 |
| `BDShortKeys.OPEN_PRIMARY_NET_SWITCHER_KEY` / `CYCLE_PRIMARY_NET_KEY` | 主网络切换 |

---

## 7. ID / 注册表 / 数据组件

| 类 | 内容 |
|---|---|
| `BDConstants.MODID` | `"beyonddimensions"` |
| `BDBlockIds` | 方块注册名（net_control/net_interface/net_pathway/net_terminal_block...） |
| `BDItemIds` | 物品注册名（net_terminal_item/net_creater/net_magnet_item...） |
| `BDMenus` | 菜单类型（Dimensions_Net_Menu / Dimensions_Craft_Menu / Dimensions_Craft_Menu_Terminal...） |
| `BDBlocks` / `BDItems` | DeferredHolder |
| `BDDataComponents` | `NET_ID_DATA` / `LONG_DATA` / `TIME_LINE` / `MACHINE_SETTINGS` / `MACHINE_CONFIG_CARD` / `CRAFT_SLOTS` / `ISTACK_SLOTS` 等 |
| `BDRegistries` | 自定义注册表 |

---

## 8. 配置 / 集成 API

### 8.1 配置

- `ClientConfigRuntime`：运行时客户端配置（见数据文档）。
- `Config.INSTANCE.clientConfig` / `serverConfig`：配置项。
- `Config.INSTANCE.clientConfig.STORAGE_*` / `CRAFTING_*` 等可 `set` + `save`。

### 8.2 集成

| 类 | 用途 |
|---|---|
| `ModPresence.isLoaded(modId)` | 模组存在判断 |
| `OtherModIds` | 常用模组 id 常量 |
| `RecipeViewerSearch.get()/set(String)` | 配方查看器搜索同步 |
| `ComposeRecipeChoices.snapshot(menu)` / `select(menu, id)` | Polymorph 配方选择 |
| `UIDataHelper.isTransfer` / `currentPage` | BD 界面切换时保留滚动位置 |
| `CapabilityHelper` | 资源能力注册 |

---

## 9. 机器 API

`api.machine`：

| 类 | 用途 |
|---|---|
| `Machine` | 机器设置读写/配置卡导入导出 |
| `MachineType` / `MachineType.Entry` | 机器类型与设置项 |
| `MachineSettings` | 设置集合（`get` / `with` / `toTag` / `fromTag`） |
| `MachineSetting` | 单个设置定义 |
| `Configurable` | `exportConfig` / `applyConfig` |
| `SettingEditor` / `MachineItem` | 设置编辑与物品侧接口 |

---

## 10. 公共 / 私有边界

### 可以直接用

- `api.**` 公共类；
- `common.menu.**` 公共菜单/命令/交互；
- `client.ui.base.**` 公共屏幕/槽位/页面；
- `client.ui.storage.**` 公共存储状态/控制器/布局/分类；
- `client.ui.kit.**` 公共控件；
- `ComposeInventoryScreen.getInventory()` / `getContentState...()`；
- `UiStateBinding` / `UiBinding`。

### 私有 / 需要额外手段

| 数据 | 状态 | 方案 |
|---|---|---|
| `StorageScreen.controller` | private | `contentState` + `StorageLayout(menu)`；`contentState` 是 Kotlin internal，用 Java 桥接（BI 的 `ContentStateHelper`）或 Mixin |
| `StorageScreen.layout` | private | 同上 |
| `StorageView`/`Toolbar`/`CategoryTabs` 等 UI | private | 复制/重建（BI 的 `WorkstationStorageSection.kt`） |
| `BdPageSelection` | StorageView 局部状态 | 默认页 Mixin（BI 已用） |
| `BdSlotAdapter` | 通过 `getInventory().getAdapter()` 可取 | 无需 Mixin |
| `StorageController` | 类 public，但 screen 的实例 private | 用 binding 共享状态/动作 |

---

## 11. 版本与兼容注意

- 项目依赖的 BD jar：`9224058`。
- CompixelUI 编译：`0.1.12`；运行时：`9223999`。
- `getContentState` 在 Kotlin 里是 internal（Java 可调用）；BI 用 Java 桥接。
- `getContentState` 后缀随 CompixelUI/模块名变化：
  - `9223999` / `0.1.12`：`getContentState$dev_compixel_compixel_neoforge_1_21_1`
  - `9224005`：`getContentState$dev_compixel_compixel_neoforge_26_3`
- `new_BD` 源码可能比依赖新；写代码前用 `javap` 或反编译实际 jar 校对。
- 编译验证：JDK 21 + `.\gradlew.bat classes`。
