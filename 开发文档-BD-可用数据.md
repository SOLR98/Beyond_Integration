# Beyond Dimensions 可用数据文档（面向 BI 1.21.1）

> 适用分支：`1.21.1_NEOFORGE`
> 依赖：BD `9224058`、CompixelUI `0.1.12` / `9223999`
> 配套文档：`开发文档-BD-API.md`、`开发文档-工作台GUI.md`
> 说明：本文回答“在什么位置、什么线程、能拿到哪些数据、怎么拿”。

---

## 0. 侧与线程速查

| 位置 | 侧 | 线程 | 能拿到 |
|---|---|---|---|
| `BdPage.snapshot` | 客户端 | 游戏线程 | `context.menu`、`context.screen`、玩家 |
| `BdPage.handle` | 客户端 | 游戏线程 | 同上；可发网络包/`context.request` |
| `BdPage.Content` | 客户端 | Compose 线程 | 只能读 `State`、`scope`；可读 `binding.value`（Compose） |
| `StorageController.snapshot/handle` | 客户端 | 游戏线程 | 菜单、`StorageState/Action` |
| `BdSlotAdapter.resourceMenu` | 客户端 | 游戏线程 | 槽位 id、菜单、资源 |
| `SlotClick`（服务端交互） | 服务端 | 服务端线程 | 菜单、玩家、槽位、点击数据 |
| `DimensionsNet` | 服务端 | 服务端线程 | 网络、成员、存储、挂接数据 |
| `UnifiedStorage` | 服务端为主 | 服务端线程 | 资源键、数量、规则 |
| `StorageState` | 客户端 | 游戏线程（Compose 读） | 界面状态快照 |
| `ClientConfigRuntime` | 客户端 | 主线程 | 客户端配置 |
| `Inventory`/`ItemStack` | 双端 | 各自线程 | 原版数据 |

原则：

- 不要在 Compose 线程访问菜单、物品堆、网络；
- 需要动作时用 `scope.send` 交回 `handle`；
- 需要服务端权威数据时通过 payload/`BdMenuActions` 请求。

---

## 1. 网络数据

### 1.1 玩家与网络

```java
DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
DimensionsNet any = DimensionsNet.getNetFromPlayer(player);
List<DimensionsNet> all = DimensionsNet.getAllNetFromPlayer(player);
DimensionsNet byId = DimensionsNet.getNetFromId(id);
```

可读取：

| 数据 | API |
|---|---|
| 网络 id | `net.getId()` |
| 自定义名 | `net.getCustomName()` / `hasCustomName()` |
| 显示名 | `net.getNetworkName()` |
| 所有者 UUID | `net.getOwner()` |
| 管理员集合 | `net.getManagers()` |
| 成员集合 | `net.getPlayers()` |
| 是否所有者/管理员 | `isOwner` / `isManager` |
| 玩家权限信息 | `getPlayerPermissionInfoMap(server)` |
| 存储 | `net.getUnifiedStorage()` |
| 自定义挂接数据 | `getOrCreateData` / `getExistingData` / `setData` / `updateData` / `removeData` / `hasData` |
| 是否已销毁 | `deleted` |

### 1.2 网络挂接数据

`NetDataType<T>` 是网络上的自定义数据槽：

```java
public static final NetDataType<MyData> MY_DATA = NetDataType
    .<MyData>builder(MyData::new)
    .codec(MyData.CODEC)
    .merge(MyData::merge)
    .build();
```

- 持久化需要 `codec`；
- 合并网络必须提供 `merge`；
- 用 `net.getOrCreateData(MY_DATA)` 读写；
- `DimensionsNetEvent.Destroyed#getData(MY_DATA)` 可在销毁时取回。

### 1.3 权限数据

- `NetPermissionlevel`：`Member` / `Manager` / `Owner`
- `PlayerPermissionInfo`
- `PrimaryNetOption`：`netId` / `permission` / `customName`
- `PrimaryNetSwitchHelper.findNextPrimaryNetId(...)`
- `PrimaryNetSwitchHelper.sortNetIds(...)`

---

## 2. 存储数据

### 2.1 全量/单项

```java
UnifiedStorage storage = net.getUnifiedStorage();

List<KeyAmount> all = storage.getStorage();
KeyAmount one = storage.getStackByKey(itemKey);
long amount = one.amount();
boolean has = storage.hasStack(itemKey);
int slots = storage.getSlots();
boolean empty = storage.isEmpty();
```

`KeyAmount`：

- `key()`：`IStackKey<?>`
- `amount()`：`long`
- `isEmpty()`
- `toStack()`：转原版对象

### 2.2 插入/提取

```java
// insert 返回余量（没插进去的部分）
KeyAmount leftover = storage.insert(itemKey, 1000, false);
long inserted = 1000 - leftover.amount();

// extract 返回实际取出量
KeyAmount got = storage.extract(itemKey, 64, true);
long available = got.amount();
```

- `simulate=true` 只计算，不改存储；
- `extract(key, amount, simulate, fuzzy)` 支持模糊匹配（取数量最多的变体）；
- `extract(TagKey, amount, simulate)` 按标签批量取。

### 2.3 槽位数据

```java
KeyAmount bySlot = storage.getStackBySlot(slot);
long cap = storage.getSlotCapacity(slot);
boolean valid = storage.isStackValid(slot, key);
int slots = storage.getSlots();
long slotCapacity = storage.slotCapacity;
int slotMaxSize = storage.slotMaxSize;
```

### 2.4 时间戳/变化订阅

`AbstractUnorderedStackHandler`：

| 数据 | API |
|---|---|
| 加入时间 | `getCreationTimeMap()` / `setCreationTime(key, millis)` |
| 最后修改 | `getLastModifiedTimeMap()` / `setLastModifiedTime(key, millis)` |
| 任意变化 | `subscribeAny(owner, listener)` |
| 增量变化 | `subscribeDelta(owner, listener)`（key, amount, added） |
| 0 数量策略 | `getZeroPolicy()` / `setZeroPolicy()` |
| UI 时间戳策略 | `getUiTimestampPolicy()` / `setUiTimestampPolicy()` |
| 容量/上限 | `setSlotCapacity` / `setSlotMaxSize` |

### 2.5 规则数据

- `UnifiedStorageRules.insertIds()` / `extractIds()`
- `insertTable()` / `extractTable()`
- `InsertRule`/`ExtractRule` 的 `plan(StorageRequest)`
- `InsertPlan` / `ExtractPlan` / `StorageEffect` / `StorageRequest`

规则只能读状态，不能直接改存储；变化写在计划，副作用放 `onCommit`。

### 2.6 客户端镜像 `ClientNetStorage`

`DimensionsNetMenu.clientNetStorage`（客户端可空）：

| 数据/方法 | 说明 |
|---|---|
| `CategoryFilter(Query, Set<IStackKey<?>>)` | 分类过滤 |
| `setCategory(filter)` | 设置分类 |
| `count(filter)` | 分类匹配种数 |
| `setSearchText(text)` | 搜索 |
| `markForceAllUpdate()` | 强制全量刷新 |
| `resolvePendingOrAllUpdate(onlyAmountUpdate)` | 刷新 |
| `buildSortedIndex(primary, secondary, reverse)` | 排序后的索引 |
| `refreshContext()` / `sourceChanged(keys)` | 刷新/源变化 |
| `getStorage()` / `getStackByKey` | 继承自 `AbstractUnorderedStackHandler` |

---

## 3. 资源键数据

### 3.1 通用 `IStackKey<?>`

| 数据 | API |
|---|---|
| 类型 id | `getTypeId()` |
| 原版堆叠 | `getReadOnlyStack()` |
| 来源模组 | `getModId()` |
| 来源 id | `getSourceId()` |
| 原版类型/来源类型 | `getStackClass()` / `getSourceClass()` |
| 是否空 | `isEmpty()` |
| 复制 | `copyStack()` / `copyStackWithCount(long)` |
| 最大堆叠 | `getVanillaMaxStackSize()` / `getCustomMaxStackSize()` |
| 标签 | `hasTag(TagKey<?>)` / `getTags()` |
| 模糊同类型 | `isSame(other)` |
| 同类型同组件 | `isSameTypeSameComponents(other)` |
| 渲染 | `getRender()` |
| 编解码 | `IStackKey.CODEC` / `STREAM_CODEC` |

### 3.2 物品 `ItemStackKey`

```java
ItemStackKey key = new ItemStackKey(stack);
ItemStack readOnly = key.getReadOnlyStack();
ItemStack copy = key.copyStack();
long max = key.getVanillaMaxStackSize();
long customMax = key.getCustomMaxStackSize();
```

- `ItemStackKey.EMPTY`
- `ItemStackKey.ID`（`beyonddimensions:stack_type/item`）
- 额外：`getRenderStack()`、`getSource()`

### 3.3 流体 `FluidStackKey`

```java
FluidStackKey key = new FluidStackKey(new FluidStack(fluid, 1));
FluidStack stack = key.getReadOnlyStack();
```

- `FluidStackKey.EMPTY`
- `FluidStackKey.ID`（`beyonddimensions:stack_type/fluid`）
- 额外：`getRenderStack()`、`getSource()`

### 3.4 能量 `EnergyStackKey`

```java
EnergyStackKey key = EnergyStackKey.INSTANCE;
```

- 数量单位是 FE；
- `EnergyStackKey.ID`；
- 继承 `LongStackKey<EnergyType>`。

### 3.5 容器内容 `ResourceContents`

```java
IStackKey<?> contents = ResourceContents.of(itemStack);
```

- 经验棒 → 经验流体；
- 带存储能力的容器 → 第一份内容物；
- 无内容 → null。

### 3.6 自定义资源

- 实现 `IStackKey<T>`；
- 通过 `StackKeyRegistry.registerType(type)` 注册；
- 需要渲染时实现 `IStackRender`；
- 能力接入用 `CapabilityHelper`。

---

## 4. 菜单数据

### 4.1 `BDBaseMenu`

| 数据 | API |
|---|---|
| 玩家 | `player` |
| 命令 | `commands()` |
| 资源 | `resources()` |
| 菜单同步 | `menuSync()` |
| 玩家背包范围 | `inventoryStartIndex` / `inventoryEndIndex` |
| 槽位组同步 | `slotGroupSyncs` |
| 快速移动路由 | `quickMoveRoutes()` |

### 4.2 `DimensionsNetMenu`（客户端/服务端）

| 数据 | API |
|---|---|
| 存储槽范围 | `getStorageStartIndex()` / `getStorageEndIndex()` |
| 玩家背包范围 | `inventoryStartIndex` / `inventoryEndIndex` |
| 列数 | `getColumns()` |
| 行数 | `getLines()` / `setLines(int)` |
| 当前首行 | `lineData` |
| 最大首行 | `maxLineData` |
| 存储处理器 | `storage` |
| 客户端镜像 | `clientNetStorage` |
| 网络 id/名 | `networkId()` / `networkName()` |
| Shift 状态 | `hasShiftDown` |
| 视口 | `setViewport(columns, rows)` |
| 索引 | `buildIndexList()` / `loadIndexList(list)` |
| 搜索 | `loadSearchText(text)` |
| 分类 | `setCategory(ClientNetStorage.CategoryFilter)` |
| 刷新 | `updateViewerStorage(onlyAmount)` / `markForceAllUpdateClientView()` |
| 滚动 | `updateScrollLineData(dataSize)` |

### 4.3 `DimensionsCraftMenu`

| 数据 | API |
|---|---|
| 合成格容器 | `craftSlots` |
| 结果容器 | `resultSlots` |
| 合成格槽范围 | `craftSlotStartIndex` / `craftSlotEndIndex` |
| 结果槽 | `resultSlotIndex` |
| 结果/合成格判断 | `isCraftResultSlot` / `isCraftGridSlot` |
| 玩家合成格 | `PlayerCraftingGrid.of(player)` |
| 剩余物品 | `remainingItems(recipe, input)` |
| 批量合成 | `craftBatch(Runnable)` |
| 配方填充 | `transferRecipe(keys, amounts[, compress])` |
| 清空合成格 | `clearCraftGrid()` / `cleanCraftSlots(toStorageFirst)` |

### 4.4 BI 工作台菜单数据

`DimensionsStorageMenu` 新增：

```java
beginWorkstationSlots(int inputs, boolean hasResult)
getWorkstationStart()
getWorkstationInputs()
getWorkstationResult()
```

| 菜单 | 输入数 | 结果 |
|---|---|---|
| Anvil | 2 | 有 |
| Cut | 1 | 有 |
| Grind | 2 | 有 |
| Smith | 3 | 有 |
| Enchant | 2 | 无 |
| EnchantMerge | 1 | 无 |

`ICleanableWorkstation.cleanSlots(boolean toStorage)`：关闭时归还工作台槽位。

### 4.5 `PlayerCraftingGrid`

| 数据/API | 说明 |
|---|---|
| `SIZE = 9` | 3×3 |
| `of(player)` | 取玩家合成格 |
| `items()` | `NonNullList<ItemStack>` |
| `keep()` | 关闭时是否保留 |
| `returnToStorage()` | 清空时是否网络优先 |
| `setPreference(toStorage, keep)` | 设置偏好 |
| `serializeNBT` / `deserializeNBT` | 玩家数据持久化 |

---

## 5. 客户端 UI 数据

### 5.1 `StorageState`（不可变快照）

| 字段 | 说明 |
|---|---|
| `networkName` / `networkId` | 网络信息 |
| `columns` / `rows` | 当前视口 |
| `preferredColumns` / `preferredRows` | 偏好 |
| `firstRow` / `totalRows` | 滚动 |
| `stored` | 存储种数 |
| `categories` | `List<CategoryTab>` |
| `category` | 当前分类索引 |
| `editor` | `CategoryEditor?` |
| `slotMenu` | `SlotMenu?` |
| `search` | 搜索文本 |
| `sort` / `secondarySort` / `reverse` | 排序 |
| `keepSearch` / `syncSearch` | 搜索偏好 |
| `keepCrafting` | 保留合成格 |
| `craft` | `CraftState?`（仅合成菜单） |

### 5.2 `StorageAction`（全部动作）

- `Search(text)`
- `Scroll(rows)` / `ScrollTo(row)`
- `Viewport(columns, rows)` / `PreferredSize(columns, rows)`
- `SelectCategory(index)` / `AddCategory` / `EditCategory(index)` / `CloseEditor`
- `DeleteCategory(index)` / `MoveCategory(from, to)`
- `RenameCategory(name)` / `CategorySearch(text)`
- `CategoryIcon(right)`
- `CategoryKeyClick(index, right)`
- `AppendToSearch(text)`
- `TakeFromMenu(amount)` / `AddToCategory(index)` / `CloseSlotMenu`
- `Sort(policy)` / `SecondarySort(policy?)` / `Reverse(enabled)`
- `KeepSearch(enabled)` / `SyncSearch(enabled)`
- `ToggleCraft` / `OpenSwitcher`
- `ClearCraft(toStorage)` / `ReturnPreference(toStorage)` / `KeepCrafting(enabled)`
- `ChooseRecipe(id)`

### 5.3 `StorageLayout`

| 字段 | 说明 |
|---|---|
| `storageStart` | 存储槽起始索引 |
| `playerSlots` | 玩家背包槽位 |
| `craftSlots` | 合成格槽位（非合成菜单为空） |
| `resultSlot` | 结果槽（-1 表示无） |
| `icon` | 网络终端图标 |
| `showSwitcher` | 是否显示网络切换 |
| `text` | `StorageText` |

### 5.4 `StorageText`

可直接读的文案字段：

`search`、`searchTitle`、`searchHelp`、`searchSyntax`、`searchRule`、
`addCategory`、`categoryList`、`edit`、`delete`、`moveLeft/Right/Up/Down`、
`editorTitle`、`name`、`iconHint`、`rule`、`rulePlaceholder`、`ruleHelp`、
`matches`、`keys`、`keysHint`、`syntax`、`syntaxHint`、
`takeOne`、`take`、`takeUnit`、`addTo`、`storage`、`crafting`、`switcher`、
`view`、`sort`、`secondarySort`、`none`、`reverse`、`layout`、`columns`、`rows`、
`keepSearch`、`syncSearch`、`syncSearchHint`、`inventory`、
`clearToStorage`、`clearToInventory`、`returnTitle`、`returnOnClear`、
`keepCrafting`、`keepCraftingHint`、`returnStorage`、`returnInventory`、
`recipe`、`sortNames`。

### 5.5 分类数据

```kotlin
data class StorageCategory(
    val name: String,
    val icon: IStackKey<*>? = null,
    val search: String = "",
    val keys: List<IStackKey<*>> = emptyList(),
)
```

`StorageCategories`：

- `load(player, registries)`：玩家分类
- `update(player, categories)`
- `save(player, registries)`
- `selected: HashMap<UUID, Int>`：本次游戏最后选中的分类
- 文件：`config/beyonddimensions/storage_categories.json`

### 5.6 其他界面数据

| 类型 | 字段 |
|---|---|
| `CategoryTab` | `name` / `icon` |
| `CategoryEditor` | `index` / `name` / `icon` / `search` / `matches` / `keys` / `inspected` / `chips` |
| `SlotMenu` | `serial` / `name` / `takeable` / `categories` |
| `CraftState` | `toggleable` / `returnToStorage` / `recipeChoices` |
| `RecipeChoice` | `id` / `label` |

### 5.7 CompixelUI 公共状态/槽位

```kotlin
val screen = context.screen as ComposeInventoryScreen<DimensionsNetMenu, StorageState, StorageAction>
val binding = ContentStateHelper.storageState(screen) // Java 桥接 internal getContentState
val slots = screen.inventory
val adapter = slots.adapter as? BdSlotAdapter<*>
```

- `binding.value`：Compose 读当前 `StorageState`；
- `binding.send(action)`：游戏线程发送动作；
- `slots` / `slots.adapter`：槽位与适配器；
- `adapter.resourceMenu`：右键菜单钩子。

---

## 6. 槽位数据

### 6.1 `AbstractStackTypedSlot`

| 数据/方法 | 说明 |
|---|---|
| `getStorage()` | 槽位所属 `IStackHandler` |
| `getStack()` | `KeyAmount`（当前资源+数量） |
| `getTypedStackFromUnifiedStorage()` | 网络存储资源 |
| `getItemStackFromUnifiedStorage()` | 原版物品 |
| `getVanillaActualStack()` | 实际数量 |
| `getVanillaMaxSizeStack()` | 最大数量 |
| `getSlotCap()` | 槽位容量 |
| `isFake()` | 标记槽 |
| `isActive()` | 是否激活 |
| `getSlotIndex()` | 槽位索引 |
| `quickMoveStart()` / `quickMoveEnd()` | 快速移动范围 |
| `safeInsert` / `safeExtract` | 受限插入/取出 |
| `getItem()` / `hasItem()` | 原版物品 |

### 6.2 `DisorderedStackTypedSlot`

- 无序槽（存储网格常用）；
- `isOrdered() == false`；
- `safeInsert` / `safeExtract`。

### 6.3 `BdSlotAdapter`

| 数据/API | 说明 |
|---|---|
| `resourceMenu` | 空手右键资源槽的回调 |
| `isResource(slotId)` | 是否虚拟资源槽 |
| `visual(slot, previous)` | 槽位外观 |
| `tooltip(...)` | 悬浮提示 |
| `execute(slotId, button, type)` | 点击执行 |
| `tick()` / `close()` | 生命周期 |
| `resourceIcon(key)` | 资源图标 |

支持的点击：左/右/中、Shift/空格/Ctrl/Alt、双击（250ms）。

### 6.4 `SlotClick`（服务端）

| 数据/方法 | 说明 |
|---|---|
| `menu()` / `player()` / `slot()` | 上下文 |
| `kind()` | `STORAGE` / `ORDERED` / `FLAG` / `VANILLA` |
| `isLeft` / `isRight` / `isMiddle` | 鼠标键 |
| `shift` / `ctrl` / `alt` / `space` / `doubleClick` | 修饰键 |
| `clicked()` / `sent()` / `requested()` | 资源/数量 |
| `carried()` / `setCarried(stack)` | 鼠标持有 |
| `storage()` / `storageSlot()` | 存储/槽位 |
| `insert(key, amount, simulate)` / `extract(...)` | 对点击槽位操作 |

---

## 7. 配置数据

`ClientConfigRuntime`：

| 字段 | 默认/说明 |
|---|---|
| `disableMultiNetworkSwitching` | 禁用多网络切换 |
| `storageSort` | 主排序（`StorageSort`） |
| `storageUseSecondarySort` | 是否启用次排序 |
| `storageSecondarySort` | 次排序 |
| `storageReverse` | 倒序 |
| `storageRows` / `storageColumns` | 偏好行列 |
| `storageKeepSearch` / `storageSearch` | 保留搜索/搜索内容 |
| `storageOpenCrafting` | 快捷键是否打开合成区 |
| `craftingReturnToStorage` | 合成格清空时网络优先 |
| `craftingKeepGrid` | 关闭时保留合成格 |
| `recipeViewerSyncSearch` | 与配方查看器同步搜索 |
| `recipeViewerEmiNetworkContents` | EMI 网络内容 |

对应 `Config.INSTANCE.clientConfig` 配置项，可用 `set(...).save()` 写回。

`StorageSort` 枚举：

`NAME`、`QUANTITY`、`MODID`、`CREATIVE_TAB`、`MAX_STACK`、`INSERTED_TIME`、`MODIFIED_TIME`。

---

## 8. 配方查看器 / Polymorph

| 类 | API |
|---|---|
| `RecipeViewerSearch` | `get()` / `set(text)` |
| `ComposeRecipeChoices` | `snapshot(DimensionsCraftMenu)` / `select(menu, id)` |
| `ComposeRecipeChoices.Choice` | `id()` / `label()` |
| `ModPresence` | `isLoaded(modId)` |
| `OtherModIds` | `JEI` / `EMI` / `POLYMORPH` / `CURIOS` 等 |

---

## 9. 事件数据

| 事件 | 数据 |
|---|---|
| `DimensionsNetEvent.Created` | `getNet()` |
| `DimensionsNetEvent.Destroyed` | `getDestroyedId` / `getNetName` / `getDestroyedStorage` / `getOwner` / `getManagers` / `getMembers` / `getData(type)` |
| `OwnerChanged` | `getNet` / `getBeforeOwner` / `getAfterOwner` |
| `MemberChanged` | `getNet` / `getChangedPlayer` / `getChangeState` |
| `NetedBlockEvent.Bound/Unbound` | `getNetId` / `getLevel` / `getPos` / `getBlockState` / `getBlock` / `getBlockEntity` |
| `NetedItemEvent.Bound/Unbound` | `getNetId` / `getPlayer` / `getStack` / `getItem` |

---

## 10. ID / 数据组件

| 数据 | 来源 |
|---|---|
| 模组 id | `BDConstants.MODID` |
| 方块注册名 | `BDBlockIds` |
| 物品注册名 | `BDItemIds` |
| 菜单类型 | `BDMenus` |
| 方块/物品 Holder | `BDBlocks` / `BDItems` |
| 数据组件 | `BDDataComponents` |
| 自定义注册表 | `BDRegistries` |

常用数据组件：

- `NET_ID_DATA`：绑定网络 id
- `LONG_DATA` / `TIME_LINE`
- `MACHINE_SETTINGS` / `MACHINE_CONFIG_CARD`
- `CRAFT_SLOTS` / `ISTACK_SLOTS`
- `CONTROL_MODE` / `FILTER_MODE` / `FUZZY_MODE` / `RECEIVE_MODE`
- `HOPPER_*` / `FEEDER_MODE` / `XP_*`

---

## 11. 常用读取配方

### 11.1 取玩家主网络并查物品数量

```java
DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
if (net == null) return;
ItemStackKey key = new ItemStackKey(stack);
long amount = net.getUnifiedStorage().getStackByKey(key).amount();
```

### 11.2 模拟提取

```java
KeyAmount got = net.getUnifiedStorage()
    .extract(new ItemStackKey(stack), 64, true);
if (got.amount() >= 64) { /* 足够 */ }
```

### 11.3 插入并计算成功量

```java
KeyAmount leftover = storage.insert(key, count, false);
long inserted = count - leftover.amount();
```

### 11.4 取客户端菜单的存储视图

```kotlin
// 游戏线程：snapshot
val screen = context.screen as ComposeInventoryScreen<DimensionsNetMenu, StorageState, StorageAction>
val binding = ContentStateHelper.storageState(screen) // Java 桥接 internal getContentState
val layout = StorageLayout(context.menu)
val slots = screen.inventory
val adapter = slots.adapter as? BdSlotAdapter<*>
```

```kotlin
// Compose 线程：Content
val storage: StorageState = binding.value
```

### 11.5 发送存储动作

```kotlin
// 游戏线程：BdPage.handle
binding.send(StorageAction.Search("diamond"))
```

### 11.6 读菜单存储/工作台槽位

```kotlin
val menu = context.menu as DimensionsNetMenu
val storageStart = menu.storageStartIndex
val storageSlots = menu.storageStartIndex until menu.storageEndIndex
val playerSlots = menu.inventoryStartIndex until menu.inventoryEndIndex

val biMenu = context.menu as? DimensionsStorageMenu
val wsStart = biMenu?.workstationStart
val wsInputs = biMenu?.workstationInputs
val wsResult = biMenu?.workstationResult
```

### 11.7 注册页面动作

```kotlin
val PING: MenuAction<DimensionsNetMenu, Int> =
    MenuAction.of("example:ping", SyncCodecs.INT) { _, player, times ->
        // 服务端
        true
    }
BdMenuActions.register(DimensionsNetMenu::class.java, PING)

// 页面内
context.request(PING, 3)
```

### 11.8 注册槽位点击

```java
SlotInteractions.register(id, interaction,
    SlotInteractions.before(
        ResourceLocation.fromNamespaceAndPath("beyonddimensions", "quick_move_vanilla")));
```

### 11.9 网络挂接数据

```java
MyData data = net.getOrCreateData(MY_DATA);
data.setSomething(...);
net.setData(MY_DATA, data);
```

---

## 12. 公共数据 vs 私有数据

### 可以直接拿

- 网络/成员/权限/存储/挂接数据；
- `IStackKey` / `KeyAmount` / `ItemStackKey` / `FluidStackKey` / `EnergyStackKey`；
- 菜单槽位索引、行列、翻页、搜索、分类；
- `StorageState` / `StorageAction` / `StorageLayout` / `StorageText` / `StorageCategories`；
- `ComposeInventoryScreen.getInventory()` / `getContentState...()`；
- `BdSlotAdapter`（通过 `getInventory().getAdapter()`）；
- `SlotClick` / `SlotInteractions` / `BdMenuActions`；
- 配置、事件、ID、数据组件、机器 API。

### 不能直接拿 / 需要额外手段

| 数据 | 原因 | 方案 |
|---|---|---|
| `StorageScreen.controller` | private | `contentState` + `StorageLayout(menu)`；`contentState` 为 Kotlin internal，需 Java 桥接（`ContentStateHelper`）或 Mixin |
| `StorageScreen.layout` | private | 同上 |
| `StorageView` / `Toolbar` / `CategoryTabs` 等 | private | 复制/重建 UI |
| `BdPageSelection` | StorageView 局部状态 | 默认页 Mixin |
| `StorageController` 的 screen 实例 | private | 用 `binding` 共享状态/动作 |
| 隐藏主页面的 Popup 复用 | BD 布局 | 页面本地菜单或 Mixin/布局改造 |

---

## 13. 版本/线程注意

- 数据以服务端 `DimensionsNet` 为准；
- 客户端 `ClientNetStorage` 是镜像，不是权威；
- `StorageState` 是快照，不是实时存储；
- `binding.value` 只能 Compose 读；
- `binding.send` 在游戏线程；
- `getContentState` 在 Kotlin 里是 internal（Java 可调用），BI 用 `ContentStateHelper` 桥接；
- `getContentState` 方法名随 CompixelUI 版本变化（`_1_21_1` / `_26_3`）；
- 编译验证：JDK 21 + `.\gradlew.bat classes`。
