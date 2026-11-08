# 1.20.1-FORGE 与 NEW_FORGE_1.20.1 功能差异清单

> 生成日期：2026-10-25
> 对比范围：两目录的 `src/main`（Java + 资源），不含 `libs/`、`build/`、`run/`
> 说明：本文只记录**差异**，不涉及移植。图例：`➖ 仅本分支（1.20.1-FORGE）`、`➕ 仅 NEW_FORGE`、`🟡 都有但实现不同`
> 相关文档：`计划项目/待办/2026-10-25-NEW_FORGE适配BD0.8.3记录.md`（NEW_FORGE 侧的实施记录）

## 0. 概况

| 项目 | 1.20.1-FORGE（本分支） | NEW_FORGE_1.20.1 |
| --- | --- | --- |
| 定位 | 正式分支，可发布 | BD 0.8.3 适配实验区 |
| 依赖 BD | 0.7.x 发布版（`maven.modrinth`） | NEW_BD 本地源码 `0.8.3`（`buildLocalBD` 构建 + `fg.deobf`） |
| 统一存储 API | `UnifiedStorageBeforeInsertHandler` | `UnifiedStorageRules` / `InsertPlan` / `StorageRequest` |
| Java 源码 | 411 个 | 312 个（+1 新增） |
| Mixin | 89 个 | 64 个 |
| 网络包 | 45 个 | 31 个 |
| 自绘工作台界面 | 有（`client/gui/Dimensions*GUI` + `feature/crafting/*Menu`） | **已删除**（待按 BD 工作台体系重做） |
| 配置界面 | 有（`client/config/ModConfigScreen`，Cloth Config） | **先前已删，本次已从本分支搬回并裁剪** |

## 1. ➕ NEW_FORGE 独有（本分支没有）

### 1.1 网络仪式绕过（Goety 仪式扩展，本次新增，**未移植到本分支**）

| 能力 | 实现落点（NEW_FORGE） | 说明 |
| --- | --- | --- |
| 统一前置条件 | `handler/RitualTrigger` | 祭坛半径内 `net_pathway ≥ pathway_count` 且 `dimensional_connect_block ≥ connect_count`；**环境绕过与结构补块共用同一判定** |
| 仪式结构方块由网络提供 | `handler/RitualNetworkStructure` + `mixin/RitualCheckerMixin` | 原版 `RitualChecker.hasBlocks` 判定失败时，用网络里的方块物品补**缺口**（不是补总量）。网络清单只扫描一次并缓存（网络 ID → 存在的物品集合），数量一律用**精确键** `ItemStackKey(物品)` 查询，**不展开 NBT 变种** |
| 网络来源网络 | 同上 | 取**仪式灵魂源网络**（`NetworkSoulSource.usableNetOf`，与仪式扣魂同源）；拿不到玩家或网络不可用则跳过 |
| 花盆折算 | 同上 | 原版 predicate 要求「花盆装着内容物」，网络里是「花盆本体 + 内容物」两件，故拆成 `minecraft:flower_pot` 与内容物两组精确键，取较小值 |
| 结构补块额外扣魂 | `CommandConfig.RITUAL_STRUCTURE_SOUL_COST`（`goety_ritual.structure_soul_cost`，默认 100） | 每次结构检查最多扣一次；魂不足则不补块（结构判定保持原版失败）。扣魂走 `RitualEnvSoulCharge`：牢笼原生优先、不足由网络补 |
| 环境绕过也发提示 | `handler/RitualNetworkStructure.notifyEnvBypass` | 环境绕过生效时也给玩家 action bar 提示（4s 节流） |
| 配置分类改名 | lang：`beyond_integration.config.goety_ritual` =**网络仪式绕过** | 分类内含：环境绕过开关、环境绕过魂消耗、通道数、连接方块数、半径、通道替代牢笼、**结构方块由网络提供**、**结构补块魂消耗** |

> 本分支现状：只有「仪式环境绕过」（`RitualRequirementsMixin` + `RitualEnvSoulCharge` + `goety_ritual.*` 六个配置项），**没有**结构方块由网络提供、没有环境绕过提示、没有结构补块魂消耗、没有 `RitualTrigger` 共用判定。

### 1.2 其它 NEW_FORGE 独有

| 能力 | 说明 |
| --- | --- |
| BD 0.8.3 新规则 API 迁移 | 插入规则全部改为 `InsertRule.plan` + `UnifiedStorageRules.insertMatching`（黑名单 / 桶分离 / TACZ 弹药箱 / 弹药库 / SW 弹药 / 附魔分书） |
| SW 弹药改为**弹药物品存储** | 网络里存散装弹药物品（`superbwarfare:*_ammo`，1 个 = 1 发），盒装按转化规则拆散；`SwAmmoStorage` / `SwAmmoMigration`（旧存档虚拟计数 → 物品，中文日志）/ `handler/BdNetDeltaHook` |
| `MachineSettingsHelper` | 替代 `BaseMachineItem.getXxxModeOrDefault`（BD 0.8 设置改为 `MachineItem.settings(stack)`） |
| `NetMenuType` 收敛 | 只剩 `NET_MENU` / `NET_TERMINAL`（合成入口并入存储界面工作台） |
| HUD 位置编辑器 | `client/gui/HudPositionScreen` + 精简 `client/BDKeyBindings`（仅 `OPEN_SOUL_HUD_EDITOR`） |
| Goety 灵魂条独立 HUD | `mixin/SoulEnergyGuiMixin`（在原版 `SoulEnergyGui.drawHUD` 后追加第三条网络魂条 + 施法进度 + 增减弹字） |

## 2. ➖ 仅本分支（1.20.1-FORGE）独有

| 能力 | 落点 | 备注 |
| --- | --- | --- |
| 自绘工作台界面 | `client/gui/Dimensions*GUI`（存储/铁砧/切割/研磨/锻造/合成/附魔/附魔合并）、`client/widget/*` | NEW_FORGE 已删除，待按 BD 工作台体系（`StorageWorkbenchTypes` / `BdPage`）重做 |
| 工作台容器与菜单 | `feature/crafting/Dimensions*Menu`、`init/ModMenus`、`common/menu/MagnetMenu`、`common/menu/NetPathwayFilterMenu` | 同上 |
| 工作台激活体系 | `feature/workstation/WorkstationActivation`、`command/network/NetworkActivationCommand`、`client/WorkstationActivationCache` | 同上 |
| 工作台相关网络包 | `OpenStorageMenuPacket`、`RecipeFillPacket`、`SetAnvilNamePacket`、`CleanWorkstationPacket`、`RefreshEnchantPacket`、`EnchantCluesPacket`、`ActivateWorkstationPacket`、`RequestWorkstationActivation/ResyncPacket`、`WorkstationActivationSyncPacket`、`SubmitEnchantMergePacket`、`TaczCraftPacket`、`EmiBdActionPacket` | NEW_FORGE 已删 |
| 界面/菜单 Mixin | `DimensionsNetGUIMixin`、`DimensionsCraftGUIMixin`、`DimensionsTerminalCraftGUIMixin`、`NetHopperGUITierMixin`、`NetHopperMenuTierMixin`、`FeederThirstGUIMixin`、`FeederThirstMenu*`、`XpExchangeRepairGUIMixin`、`XpExchangeMenuMixin`、`SoulEnergyGuiMixin`（旧版实现）、`BDBaseGUIRepairClickMixin`、`BDMenuAccessor`、`NetMagnetItemOpenMixin`、`GunSmithTableScreenMixin`、`TaczaddonCraftBridgeMixin`、`BdBucketCraftTransferMixin`、`RsIntegrationMachineHubMixin`、`RsIntegrationAutoEatMixin`、`AbstractGunItemHasInventoryAmmoMixin` | NEW_FORGE 已删（SW 的 `hasInventoryAmmo` 注入并入 `AbstractGunItemMixin`）；**`NetPathwayBlockEntityFilterMixin`、`JeiIngredient*Mixin`(4) 已回移** |
| JEI / EMI 集成 | `jei/BDJeiPlugin`、`BDSidebarGuiHandler`、`BeyondJeiNetworkHelper`、`CraftRecipeTransferHandler`、`CutRecipeTransferHandler`、`NetworkCountOverlay`、`client/EmiBdShortcuts` | **数量角标 + 点击取物已回移**（`BiJeiPlugin` + `BeyondJeiNetworkHelper`（数据源改 `PrimaryNetClientStorage`）+ `NetworkCountOverlay` + `JeiExtractInputHandler` + 4 个 `JeiIngredient*Mixin`）；配方转移/侧边栏避让不再需要（BD 自带 `WorkbenchRecipeTransferHandler`）；`EmiBdShortcuts` 不可移植 |
| 对外 SPI | `api/IBeforeInsertExtension`、`api/IDimensionsNetGUIExtension` | NEW_FORGE 已删（基于已删接口/新规则 API） |
| 完整配置界面 | `client/config/ModConfigScreen`（1197 行，20 个分类） | **本次已搬到 NEW_FORGE 并裁剪工作台相关项** |
| 网络通道过滤界面 | `common/menu/NetPathwayFilterMenu`、`client/gui/NetPathwayFilterGUI`、`mixin/NetPathwayBlockEntityFilterMixin`、`init/ModMenus` | **菜单+Mixin 已回移**（按 BD 0.8.3 的 `BDBaseMenu`/`SyncedMenu` 重写：`SyncSchema` 同步三开关 + `MenuAction` 提交）；界面改为普通 `AbstractContainerScreen`（`NetPathwayFilterScreen`，不再依赖已删的 `BDBaseGUI`） |
| Kotlin / Compose 编译链 | ➖ 无（纯 Java） | ➕ **已加**：`org.jetbrains.kotlin.jvm` + `kotlin.plugin.compose`（2.4.10）、`kotlin { jvmToolchain(17) }`、`src/main/kotlin`；用于后续用 BD 的 `BdPage` 注入机器页面（磁铁/漏斗/喂食器/经验棒）。`org.jetbrains:annotations` 的排除已收窄为仅运行期配置 |

## 3. 🟡 都有但实现不同

| 功能 | 本分支 | NEW_FORGE |
| --- | --- | --- |
| 统一存储插入拦截 | `UnifiedStorageBeforeInsertHandler.BeforeInsertHandler` | `InsertRule.plan` + `InsertPlan`（纯计划 + `onCommit`） |
| SW 网络弹药 | 虚拟计数（`NetworkAmmoData.networkAmmo` → `Map<弹药类型, 发数>`） | 散装弹药物品（精确物品键），旧数据启动时迁移 |
| 机器设置读取 | `BaseMachineItem.getXxxModeOrDefault` | `handler/MachineSettingsHelper`（`MachineItem.settings(stack)`） |
| 数量格式化 | `StringFormat.formatCount` | `StringFormat.formatCompact` |
| 灵魂条 HUD | `SoulEnergyGuiMixin`（旧版，含客户端配置位置） | 同注入点，位置走统一 `HudLayout` + HUD 编辑器 |
| 网络漏斗档位 | 字段 `hopperXxxMode` | 方法 `hopperXxxMode()`（mixin 同步） |
| `/bdtools network open` | 支持 `craft`（打开 `DimensionsCraftMenu`） | 只有存储/终端（合成并入存储界面） |
| 依赖声明 | BD 0.7.x 发布版 + `libs/` 69 项源码 | `buildLocalBD` 本地构建 + `compileOnly compixel` + `-Xmaxerrs 5000` + `exclude org.jetbrains:annotations` |

## 4. 配置项差异（`CommandConfig`）

| 分项 | 本分支 | NEW_FORGE |
| --- | --- | --- |
| `goety_ritual.network_structure` | ➖ 无 | ➕ 有（默认 true） |
| `goety_ritual.structure_soul_cost` | ➖ 无 | ➕ 有（默认 100） |
| `goety_ritual` 分类名 | 仪式环境绕过 | **网络仪式绕过** |
| 其余字段 | 155 个 | 157 个 = 155 + 上述 2 项新增 |

## 5. 需要关注的不兼容点

1. **终端工作台页面**：本分支基于 BD 0.7 自绘菜单；BD 0.8.3 已把工作台并入存储界面（`StorageWorkbenchTypes`），两套实现不兼容，不能直接互搬。
2. **所有改 BD GUI 的 Mixin** 同理不兼容（`DimensionsNetGUIMixin` 等）。
3. **`UnifiedStorageBeforeInsertHandler` → `UnifiedStorageRules`**：接口语义从「拦截后自己写存储」变为「只出计划，由框架事务提交」，逻辑必须重写而非平移。
4. **SW 弹药存储口径**：虚拟计数与散装弹药物品不可混用，跨分支切换需迁移（NEW_FORGE 侧已实现 `SwAmmoMigration`）。
