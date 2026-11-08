# 为 Beyond Dimensions 存储界面增加第三方加工页面

适用基线：Minecraft 1.21.1 / NeoForge，BD `1.21.1` 分支提交 `736edc2bcd508389d78a1801e6c552e7c25188c2`，项目版本 0.8.0、CompixelUI 0.1.12。本文是接入设计与代码骨架，没有实现或实机验证一个完整附属模组。标注为 `Addon*`、`Workstation*` 的类型由附属自行实现，不能当作 BD 已有 API。

实现 Compose 页面需要启用 Kotlin 与 Compose 编译插件；该基线使用 2.4.10。依赖声明可参照 BD 的 `build.gradle`，保持与所用 CompixelUI 版本一致；运行时由独立的 CompixelUI 模组提供，不要在附属里重复打包一份。

## 1. 推荐的接入方式

保留现有 `DimensionsNetMenu` 菜单和它的存储同步，在同一菜单中追加第三方输入槽、结果槽，再通过 `BdPages` 增加页签。新页继续显示原菜单的存储槽和玩家背包，中间改放砂轮、切石机或第三方工作台的操作区。

这对应三个部分：

| 部分 | 实现方式 |
| --- | --- |
| 页签、页面生命周期、快照和客户端动作 | BD 的 `BdPage` / `BdPages` |
| 存储格、背包格、数量显示及点击 | 原菜单的槽位 + `scope.slots` + `BdSlotGrid` / `BdPlayerInventory` |
| 第三方加工与材料归属 | 附属自己的服务器后端；复用原模组的容器和结果槽语义 |

当前有一个明确限制：`StorageScreen.kt` 的 `StorageView`、`Toolbar`、`CategoryTabs`、`CraftSection` 等函数仍是 `private`。**页面 API 可以加一整页，但没有“原存储视图 + 替换合成区”这样的公开插槽。** 注册页面不会自动带出存储区域，也不能把另一个 Screen 当作 Compose 控件嵌进去。

因此，基于当前代码，最少的做法是：用一个客户端 Accessor 复用原来的 `StorageController`，用公开槽位组件组成新页。搜索、排序、分类的行为可以继续交给这个控制器；要完全复刻原页的分类编辑、右键菜单和布局，则还需要移植相应的私有 UI 代码。

如果可以同时修改 BD，建议先把不含外壳和页签的存储工作区抽成公开的内容组件，允许传入加工区内容与尺寸。这比对 Compose 编译后的私有函数进行 Mixin 更适合作为长期接口。这里描述的是建议新增的扩展点，当前分支并不存在这个组件。

## 2. 页面注册与线程边界

客户端初始化时注册，例如放在 `FMLClientSetupEvent` 的 `enqueueWork` 中：

```kotlin
BdPages.register<DimensionsNetMenu>(
    ResourceLocation.fromNamespaceAndPath("example", "grindstone"),
    GrindstonePage,
    BdPages.last(),
)
```

`GrindstonePage` 是附属实现的 `BdPage<DimensionsNetMenu, WorkstationState, WorkstationAction>`。注册给 `DimensionsNetMenu` 后，也能匹配 `DimensionsCraftMenu`、`DimensionsCraftMenuTerminal`；通常不必给三个类重复注册。`appliesTo` 可进一步限制出现条件。

接口的执行位置如下：

| 方法 | 执行位置 | 应做的事 |
| --- | --- | --- |
| `appliesTo`、`title` | 客户端游戏线程，挂载时一次 | 判断是否展示、取得提示文字 |
| `snapshot` | 客户端游戏线程，可能频繁调用 | 读取已同步的数据，生成不可变页面状态 |
| `Content` | Compose 线程 | 使用快照排版，通过 `scope.send` 提交动作 |
| `handle` | 客户端游戏线程 | 处理本地显示操作，或通过 `context.request` 请求服务器 |
| `MenuAction` 处理器 | 服务器线程 | 校验并执行真实物品操作 |

`handle` 并不是服务器回调。实际扣料、发放产物不能写在 `Content` 或客户端 `handle` 中。`scope.shown` 是页面显示状态，也不是服务器权限证明。`appliesTo` 只在挂载时判断，动态解锁仍应进入快照和服务器校验。

页面状态保存槽位编号、字符串、布尔值和 BD 的显示快照即可，不要把菜单、玩家、可变 `ItemStack` 或第三方后端对象带进 Compose。每个菜单自己的后端也不要放在单例 `GrindstonePage` 的字段里。

## 3. 最小 Mixin：追加槽位、桥接控制器

### 3.1 在菜单构造时固定追加槽位

建议给 `DimensionsNetMenu` 的四参数构造函数注入 `RETURN`，通过附属自己的接口暴露后端：

```java
public interface AddonMenuAccess {
    AddonWorkspaces bdaddonWorkspaces();
}

@Mixin(value = DimensionsNetMenu.class, remap = false)
public abstract class DimensionsNetMenuMixin extends BDBaseMenu
        implements AddonMenuAccess {
    @Unique
    private AddonWorkspaces bdaddon$workspaces;

    protected DimensionsNetMenuMixin(MenuType<?> type, int id, Inventory inv) {
        super(type, id, inv);
    }

    @Inject(
        method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lcom/wintercogs/beyonddimensions/api/storage/handler/impl/AbstractUnorderedStackHandler;)V",
        at = @At("RETURN")
    )
    private void bdaddon$append(MenuType<?> type, int id, Inventory inv,
                               AbstractUnorderedStackHandler storage,
                               CallbackInfo ci) {
        var menu = (DimensionsNetMenu) (Object) this;
        bdaddon$workspaces = AddonWorkspaces.create(menu, this::addSlot);
    }

    @Override
    public AddonWorkspaces bdaddonWorkspaces() {
        return bdaddon$workspaces;
    }
}
```

这里省略 imports。`AddonWorkspaces.create` 是需要自行实现的工厂，第二个参数可设计为 `Function<Slot, Slot>`。它负责建立加工容器，用传入的 `addSlot` 追加真正的输入槽和专用结果槽，并记录各槽 `index`。此时仅完成构造，避免调用依赖子类已初始化字段的方法，也不要主动开始合成。

注意这个注入点位于**父类构造结束**：创建 `DimensionsCraftMenu` 时，附属槽会先追加，随后 BD 再追加自己的 3×3 槽。只要两端顺序相同、使用记录下来的实际编号，这种排列可以成立。不要预设“附属槽一定排在原生合成槽之后”。

槽位约定：

- 两端在构造时追加相同数量、相同顺序的槽。页面打开、关闭、换页都不改变 `menu.slots`。
- 保留 BD 原有的 891 个存储槽及其边界，不移动原槽、不改 `storageStartIndex` / `storageEndIndex` 来包住新槽。
- 按确定顺序为每个后端记录输入槽编号列表和结果槽编号。可选模组条件也必须两端一致，不能仅按客户端配置决定是否追加槽位。
- 沿用 `BDBaseMenu.addSlot`，让原版槽位跟踪列表和 BD 快速转移缓存正常维护。普通槽位置可填 `0, 0`，最终位置由 Compose 排版决定。
- 同菜单方案不另外打开 `GrindstoneMenu` 或 `TierCraftMenu`，也不搬入另一个菜单自己的背包槽、菜单编号和同步通道。

当前 `DimensionsNetMenu.createQuickMoveRoutes()` 已把存储边界后的普通槽位归入额外的 `crafting` 分组，并允许转回存储。只需“新输入槽 Shift 退回网络”时通常可以沿用它；背包 Shift 默认仍进存储，不会自动改成往当前加工区填料。若要改变路线，应在 `createQuickMoveRoutes` 中保留原有路线并补充新规则。

### 3.2 客户端取得现有控制器

```java
@Mixin(value = StorageScreen.class, remap = false)
public interface StorageScreenAccessor {
    @Accessor("controller")
    @org.jetbrains.annotations.Nullable
    StorageController getBdaddonStorageController();
}
```

把这个 Accessor 放进附属 Mixin 配置的 `client` 数组；菜单 Mixin 放进 `mixins` 数组。公共初始化代码不要加载 `StorageScreen`、页面对象或 Compose 类。

**首次快照需要允许控制器暂时为 null。** `BdInventoryScreen` 的父类初始化期间就会创建 `BdPageHost`，挂载页面并调用 `snapshot`，而 `StorageScreen.controller` 随后才初始化。因此首次快照应返回 `ready = false` 的空状态，待控制器可用后再正常显示；不能在 `appliesTo` 中因控制器暂时为空而永久排除页面。

后续快照使用 `controller.snapshot()`，本地动作使用 `controller.handle(StorageAction...)`。不要再创建第二个 `StorageController`：原 Screen 已负责它的 tick、分类保存和搜索收尾，两个控制器会争用同一个菜单的过滤状态。

## 4. 新页面怎样显示原存储槽

`BdPageScope.slots` 已经是当前 Screen 的槽位宿主。无需另外建存储副本，也不需要自己为存储格实现点击包。

以下是 `Content` 内的核心布局片段。约定 `state.storage` 是共享控制器的 `StorageState`，其余字段是游戏线程从当前菜单和后端取得的不可变数据；`state.ready` 表示首次快照准备完毕：

```kotlin
if (!state.ready) return
val slots = scope.slots ?: return
val storage = state.storage

// 页面外层已由 BdPageHost 提供，不要再嵌套一个 Screen 或 BdTabbedWindow。
BdSectionLabel("存储")
Row {
    BdSlotGrid(
        slots,
        List(storage.columns * storage.rows) { state.storageStart + it },
        storage.columns,
    )
    if (storage.totalRows > storage.rows) {
        BdScrollbar(
            first = storage.firstRow,
            visible = storage.rows,
            total = storage.totalRows,
            onScrollTo = {
                if (scope.shown) scope.send(
                    WorkstationAction.Storage(StorageAction.ScrollTo(it))
                )
            },
            modifier = Modifier.width(7.dp)
                .height((storage.rows * SLOT_PITCH).dp),
        )
    }
}

BdSectionLabel(state.workstationTitle)
BdSlotGrid(slots, state.inputIds, state.inputColumns)
BdSlot(slots, state.resultId)

BdInventorySection("背包", state.playerIds, slots)
```

`storageStart` 来自 `menu.getStorageStartIndex()`；`playerIds` 来自 `[inventoryStartIndex, inventoryEndIndex)`；加工槽编号来自附属后端。`WorkstationAction.Storage` 是附属包装 `StorageAction` 的动作类型，在 `handle` 中转交共享控制器。

这段只演示存储、加工区和背包。补齐完整交互时：

- 搜索使用 `BdSearchField`，把文本变化转为 `StorageAction.Search`；仿照原页处理本地编辑值与快照回传，避免快速输入被旧快照覆盖。
- 排序、分类选择继续转交 `StorageAction.Sort` / `SelectCategory` 等。完整分类编辑和右键菜单的 UI 目前需要自行组合或移植原页对应代码。
- 原 Screen 的 `adapter.resourceMenu` 仍会打开共享控制器的右键菜单状态。新页要显示对应弹窗；尚未实现时，应按当前页路由或停用这个回调，避免空手右键只被截获却没有反馈。
- 使用 BD 的槽位组件，让隐藏页的槽位按 `LocalBdPageVisibility` 停止响应。自定义按钮、搜索框等也应按 `scope.shown` 禁止隐藏页操作。
- 主页面仍参与布局并决定窗口尺寸。第一版可直接使用它的当前行列；不要让隐藏主页面和新页面不断提交不同的 `Viewport`。要每页独立调整存储行数，应先统一视口所有权和可用空间计算。

如果新页要完整接管 Tab 搜索焦点、右键菜单等 Screen 级行为，再增加针对当前页面的客户端路由。仅取得控制器，并不等于原页的这些外围行为都会自动迁移。

## 5. 服务器动作：通过 BdMenuActions 扩展

槽位本身的拿起、放入沿用原版槽位和 BD 适配器；“清空这个加工区”“按配方填入材料”等按钮则注册自己的菜单动作。

以下省略 imports，后端方法由附属实现。这个例子展示清空砂轮输入的链路：

```java
public static final MenuAction<DimensionsNetMenu, Boolean> RETURN_GRINDSTONE =
        MenuAction.of("example:return_grindstone", SyncCodecs.BOOLEAN,
                (menu, player, toStorage) -> {
                    if (player.containerMenu != menu || !player.isAlive()
                            || player.isSpectator() || !menu.stillValid(player))
                        return false;
                    var backend = ((AddonMenuAccess) menu)
                            .bdaddonWorkspaces().grindstone();
                    if (!backend.canUse(player)) return false;
                    backend.returnInputs(toStorage);
                    menu.broadcastChanges();
                    return true;
                });

// 两端公共初始化时注册，必须早于任何目标菜单的创建。
BdMenuActions.register(DimensionsNetMenu.class, RETURN_GRINDSTONE);
```

页面按钮先 `scope.send(...)`，然后在 `BdPage.handle` 中调用：

```kotlin
context.request(AddonActions.RETURN_GRINDSTONE, true)
```

`request` 需要菜单已有同步快照，返回 false 时不能假定服务器已处理。动作注册给父类也会附加到子类；每个动作 ID 只注册一次。

不要调用 BD 自带 `commands().returnCrafting(...)` 来清空第三方加工区。它当前明确操作 `DimensionsCraftMenu` 的原生合成格，`recipe(...)` 和合成偏好命令同样不是任意加工区的通用接口。

## 6. 砂轮等第三方加工的后端

对于原版砂轮，操作区是两个输入槽和一个结果槽。砂轮处理并不是 `RecipeType.CRAFTING` 配方；不能仅调用 BD 的合成结果计算，再把界面文字改成砂轮。

适配后端应保留原版/目标模组的完整规则：输入限制、修复和附魔处理、结果计算、取出时扣料、经验和声音，以及 NeoForge 的加工事件。砂轮的 NeoForge 补丁包含 `CommonHooks.onGrindstoneChange` 和 `onGrindstoneTake`，直接重写一套简化公式会遗漏这些扩展行为。

推荐用后端适配层复用目标逻辑，必要时只桥接其容器或计算方法。若选择包装原 `GrindstoneMenu`，应逐项处理它的槽位编号、变更回调、取出回调和清理逻辑；不要直接搬入整套菜单后再同时运行两套点击/关闭流程。服务端需要有实际 `ContainerLevelAccess` 或明确等价的效果实现，`ContainerLevelAccess.NULL` 会让依赖该上下文的执行逻辑不运行。

结果槽必须负责真实的取出语义：`mayPlace` 返回 false；`mayPickup` 校验当前输入、权限和结果；成功取出后只扣一次材料、只发一次经验，并重新计算结果。预览结果不能当作一堆独立库存物品保存。

普通输入槽的 Shift 转移可沿用 BD 现有路线。想支持连续合成时，用 `SlotInteractions.register` 为**附属结果槽**注册规则，并放在 `beyonddimensions:quick_move_vanilla` 前；只匹配自己的菜单扩展和实际槽位编号，成功处理返回 true，未发生任何变化才可返回 false。每轮先确认产物可完整放下，再通过结果槽的 `safeTake` 等正规路径取得产物，保证执行 `onTake`。

不要把 BD 的 `CraftableMenu` 当作多加工区注册器。它目前只描述菜单级的结果槽、输入槽、批处理和清空操作；现有 `DimensionsCraftMenu` 的实现只认识原生 3×3 区。若要让新增槽支持空格点击，还需要让客户端 `BdSlotAdapter` 识别这些槽并发送自定义动作，再由服务器分发到对应后端。仅注册服务器交互规则，客户端不会自动为新槽发送空格操作。

## 7. 无尽贪婪的 9×9 页面

以下具体名称以 Re:Avaritia 的 `neo/1.21.1` 分支提交 `0e54d24e63d7a9d334874f7d50a83f948c028cef` 为例。其他无尽贪婪移植版应替换对应适配层，并核对实际安装版本的接口。

### 7.1 81 格独立材料区与专用配方

建立独立的 81 格输入区和 1 个结果槽。不要扩大 BD 原本 `PlayerCraftingGrid` 的九格，也不要仅把原 `BdSlotGrid(..., 3)` 的列数改为 9。

该版本可以复用的类型是：

| 类型 | 用途 |
| --- | --- |
| `ItemStackWrapper` | 材料的底层存储 |
| `ModCraftContainer` | 带宽高和菜单变更回调的加工容器 |
| `TierInput` | 配方输入，包含等级与裁剪后的偏移 |
| `ITierCraftingRecipe` | 该模组工作台配方接口 |
| `ModRecipeTypes.CRAFTING_TABLE_RECIPE` | 服务端查找配方用的类型 |
| `ModCraftResultSlot` | 取出产物、扣料、处理剩余物和合成事件 |

构造与匹配的核心代码如下，不含槽位添加、生命周期和结果校验的完整实现：

```java
// 实际项目优先传入玩家 attachment 或方块持有的材料存储。
// 这里的临时存储仅演示构造签名。
var materials = new ItemStackWrapper(81, slot -> {});
var matrix = new ModCraftContainer(menu, materials, 9);
var result = new ResultContainer();

// 在服务器初始化完成后，以及材料改变时执行：
TierInput input = matrix.asCraftInput();
var recipe = menu.player.level().getRecipeManager().getRecipeFor(
        ModRecipeTypes.CRAFTING_TABLE_RECIPE.get(),
        input,
        menu.player.level()
);
result.setItem(0, recipe
        .map(r -> r.value().assemble(input, menu.player.registryAccess()))
        .orElse(ItemStack.EMPTY));
```

`ModCraftContainer.asCraftInput()` 会按边长计算等级：9×9 对应 tier 4，不是 tier 9。若自行构造，应使用 `TierInput.of(9, 9, materials.getStacks(), 4)`，不能改用普通 `CraftingInput`。

### 7.2 结果槽可以复用，但必须补校验

`ModCraftResultSlot` 构造参数接受 `AbstractContainerMenu`，因此可以挂到 BD 菜单上；它本身已实现剩余物和合成事件。不过该版本的 `mayPickup` 只在宿主为 `TierCraftMenu` 时调用原菜单的 `canTakeCraftingResult()`，换成 BD 菜单就不会执行这层校验。

因此应派生一个附属结果槽，覆写 `mayPickup`：客户端按同步结果处理；服务器验证当前玩家仍在该 BD 菜单、页面功能仍可用，并按当前材料重新匹配配方，核对结果的物品、组件和数量。校验失败就刷新结果并拒绝取出。普通点击、Shift、批量路径都要经过这个检查，而不只是检查某个自定义按钮。

让 `ModCraftContainer` 的变更回调能到达附属后端。当前 `DimensionsNetMenu` 没有声明自己的 `slotsChanged(Container)`，因此不能把它当作该类已有的方法直接写一个 `@Inject` 目标。可在前面的菜单 Mixin 中新增覆写，保留父类调用，再分发给附属后端：

```java
@Override
public void slotsChanged(Container changed) {
    super.slotsChanged(changed);
    if (bdaddon$workspaces != null)
        bdaddon$workspaces.onSlotsChanged(changed);
}
```

`onSlotsChanged` 只处理属于自身后端的输入容器，构造阶段尚未准备完就跳过，并防止结果刷新产生递归。BD 合成菜单自身的覆写会调用父类，原生 3×3 的结果刷新仍应保留。持久材料的首次结果刷新应安排在整个菜单构造完成后的首次服务器广播或刻更新，不能在父构造注入点就触发尚未初始化的子类回调。批量修改可合并刷新，但取出结果前仍要进行实时校验。

不要直接复用 BD 的 `AutoRefillResultSlot`。它绑定 `DimensionsCraftMenu`，查询 `CraftingRecipe` 和原版合成配方类型，不能代替 Re:Avaritia 的工作台结果逻辑。

### 7.3 偏移、剩余物与自动补料

`TierInput` 会裁去外围空行空列。扣料和剩余物不能简单从 0 到 80 对应，裁剪输入中的坐标应映射回：

```java
int sourceIndex = (x + input.left()) + (y + input.top()) * 9;
int remainderIndex = x + y * input.width();
```

剩余物应由**实际产出这份结果的配方**提供，保留完整数量和组件，包括模组自定义转换器、不消耗催化剂、工具磨损等规则。`ModCraftResultSlot` 已提供基础实现；自定义自动补料时也必须保留这些语义。

建议先实现“真实 81 格材料 + 原生结果槽”，再加入 BD 网络自动补料。第二阶段按实际配方的消耗和剩余物补充缺口，不能把所有输入一律按每格消耗 1 个向网络索取；会返回的催化剂、会变更组件的工具必须单独处理。

### 7.4 JEI / EMI 配方填充需要独立接入

BD 当前的 `TransferHelper` 对普通填充有九格限制，通用转移则允许把超出原生合成格的材料压入物质球。**这个通用转移不是 9×9 工作台支持。**

为实际使用的 BD `MenuType` 注册针对 Re:Avaritia 工作台分类的专用转移处理器；覆盖三种 BD 存储菜单时分别适配。该版本 JEI 分类类型位于 `ExtremeCraftingTableCategory.RECIPE_TYPE`。不要直接复用 Re:Avaritia 为自身 `TierCraftMenu` 注册的固定槽位范围。

填充请求使用自己的 `MenuAction`，携带目标后端、配方标识及必要的材料选择。服务端重新解析并验证配方，保持有序配方的 9×9 位置与空位，不得压平后顺序塞入。需要检查材料变体、组件、数量和槽位限制；同一种材料分布在多格时要累计需求。`doTransfer = false` 只做材料检查，不扣料。

需要复用材料核算算法时，可参考 BD 的 `RecipeFillPlan`。其 `Pool.capture` 当前只接受 `DimensionsCraftMenu` 并读取原生合成格，应改成采集实际 81 格、玩家背包和网络库存；每个实际来源只计一次。

原 `BdPageScope.open` 可以从页内切页，但 `BdPages.register` 不提供外部全局跳页接口。第一版可以让用户手动选中 9×9 页；配方填充后自动跳页，需要另加客户端选择状态桥接。

### 7.5 布局与保存

`BdSlotGrid(slots, inputIds, 9)` 可以排出 9×9，BD 槽距为 18，所以格子本体为 162×162，尚未计入边框与标题。最窄九列存储窗口中，结果槽放在网格下方更容易适配；放在右侧需要额外宽度。

`BdPageHost` 的注入页使用 `BdTabPage`，内容超出时可以在页内滚动，但它与主页面同尺寸，不会自动把窗口扩大成 9×9 工作台所需的高度。需要存储区、81 格和背包同时可见时，应重新计算主窗口布局或改为左右布局，不能只改合成格列数。显示与命中区域继续使用 Compose 槽位的裁剪结果。

材料保存使用附属自己的玩家 attachment 或真实方块存储，分别为砂轮和 9×9 建立稳定的数据身份。服务器菜单直接引用这份材料数据；结果预览不持久化。不要修改 BD 的九格存档结构来容纳 81 格。

切页只切显示，不清空材料。正常关闭时若选择退回，可参考 `DimensionsCraftMenu.returnStack` 的“存储 / 背包 / 最后掉落”逻辑，只退回真实输入。断线时要遵守已有保存顺序：BD 当前玩家数据会先于菜单关闭保存；若材料已进入玩家 attachment，再在断线关闭回调中退回一次，便可能留下重复物品。因此应沿用“断线保留在持久材料区，重进后继续使用”的策略，而不是只靠临时菜单字段保存输入。

## 8. 建议的实现顺序与验收

1. 注册一个空的 `BdPage`，确认三类存储菜单的页签和隐藏交互正常。
2. 用共享控制器和原菜单槽位，完成新页的存储、背包、搜索与滚动。
3. 接入砂轮两个输入槽、专用结果槽和正常关闭时的材料归还。
4. 接入持久的 81 格容器，先完成 Re:Avaritia 原生配方与结果槽，再做自动补料和 JEI / EMI。
5. 最后处理完整分类编辑、右键菜单、空格批量操作、自动切页及专用布局。

实际附属应重点验证：首次快照与专用服务端启动；不同入口的槽位顺序；主页面与注入页来回切换；普通点击和 Shift 取出；背包与网络均满时的行为；断线重进与正常退出；9×9 四角偏置配方、空行空列、有序/无序配方、组件不同的材料、桶和工具剩余物、不消耗催化剂；数据包重载后旧结果被拒绝；JEI 检查与实际填充不会误走物质球路径。

## 源码依据

- [BD 页面接口 BdPage.kt](https://github.com/Frostbite-time/BeyondDimensions/blob/736edc2bcd508389d78a1801e6c552e7c25188c2/src/main/kotlin/com/wintercogs/beyonddimensions/api/ui/page/BdPage.kt)、[注册表 BdPages.kt](https://github.com/Frostbite-time/BeyondDimensions/blob/736edc2bcd508389d78a1801e6c552e7c25188c2/src/main/kotlin/com/wintercogs/beyonddimensions/api/ui/page/BdPages.kt)。
- [StorageScreen.kt](https://github.com/Frostbite-time/BeyondDimensions/blob/736edc2bcd508389d78a1801e6c552e7c25188c2/src/main/kotlin/com/wintercogs/beyonddimensions/client/ui/storage/StorageScreen.kt)、[StorageController.kt](https://github.com/Frostbite-time/BeyondDimensions/blob/736edc2bcd508389d78a1801e6c552e7c25188c2/src/main/kotlin/com/wintercogs/beyonddimensions/client/ui/storage/StorageController.kt)、[BdScreens.kt](https://github.com/Frostbite-time/BeyondDimensions/blob/736edc2bcd508389d78a1801e6c552e7c25188c2/src/main/kotlin/com/wintercogs/beyonddimensions/client/ui/base/BdScreens.kt)、[BdPageHost.kt](https://github.com/Frostbite-time/BeyondDimensions/blob/736edc2bcd508389d78a1801e6c552e7c25188c2/src/main/kotlin/com/wintercogs/beyonddimensions/client/ui/base/BdPageHost.kt)。
- [DimensionsNetMenu.java](https://github.com/Frostbite-time/BeyondDimensions/blob/736edc2bcd508389d78a1801e6c552e7c25188c2/src/main/java/com/wintercogs/beyonddimensions/common/menu/DimensionsNetMenu.java)、[BdMenuActions.java](https://github.com/Frostbite-time/BeyondDimensions/blob/736edc2bcd508389d78a1801e6c552e7c25188c2/src/main/java/com/wintercogs/beyonddimensions/api/menu/BdMenuActions.java)、[SlotInteractions.java](https://github.com/Frostbite-time/BeyondDimensions/blob/736edc2bcd508389d78a1801e6c552e7c25188c2/src/main/java/com/wintercogs/beyonddimensions/api/menu/interaction/SlotInteractions.java)。
- [DimensionsCraftMenu.java](https://github.com/Frostbite-time/BeyondDimensions/blob/736edc2bcd508389d78a1801e6c552e7c25188c2/src/main/java/com/wintercogs/beyonddimensions/common/menu/DimensionsCraftMenu.java)、[AutoRefillResultSlot.java](https://github.com/Frostbite-time/BeyondDimensions/blob/736edc2bcd508389d78a1801e6c552e7c25188c2/src/main/java/com/wintercogs/beyonddimensions/common/menu/widget/slot/AutoRefillResultSlot.java)、[TransferHelper.java](https://github.com/Frostbite-time/BeyondDimensions/blob/736edc2bcd508389d78a1801e6c552e7c25188c2/src/main/java/com/wintercogs/beyonddimensions/integration/module/jei/transfer/TransferHelper.java)。
- [NeoForge 1.21.1 菜单文档](https://docs.neoforged.net/docs/1.21.1/gui/menus/)、[砂轮补丁](https://github.com/neoforged/NeoForge/blob/1.21.1/patches/net/minecraft/world/inventory/GrindstoneMenu.java.patch)。
- [Re:Avaritia TierCraftMenu.java](https://github.com/Nova-Committee/Re-Avaritia/blob/0e54d24e63d7a9d334874f7d50a83f948c028cef/src/main/java/committee/nova/mods/avaritia/common/menu/TierCraftMenu.java)、[ModCraftContainer.java](https://github.com/Nova-Committee/Re-Avaritia/blob/0e54d24e63d7a9d334874f7d50a83f948c028cef/src/main/java/committee/nova/mods/avaritia/common/container/ModCraftContainer.java)、[ModCraftResultSlot.java](https://github.com/Nova-Committee/Re-Avaritia/blob/0e54d24e63d7a9d334874f7d50a83f948c028cef/src/main/java/committee/nova/mods/avaritia/common/container/slot/ModCraftResultSlot.java)、[TierInput.java](https://github.com/Nova-Committee/Re-Avaritia/blob/0e54d24e63d7a9d334874f7d50a83f948c028cef/src/main/java/committee/nova/mods/avaritia/api/common/crafting/TierInput.java)。
