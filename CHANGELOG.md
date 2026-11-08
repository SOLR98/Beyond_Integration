# Beyond Integration 更新日志

## 0.7.3（2026-10-19）

> 适用分支：`1.20.1-FORGE`。`1.21.1-NEOFORGE` 因重大改动尚未完成，本版本暂不同步。
> 本版本以 0.7.2 发布包为基线。

### 🔧 行为变更

- **Goety 网络灵魂源改为「原版优先、网络兜底」**：
  - 消耗：Arca/图腾足够时不触碰网络；不足时先扣光原版，余量再由网络补（按 `soulDiscount` 折算原版可承担量）。
  - 吸收：击杀/获得灵魂时先填满原版容器，**仅溢出部分**按 `kill_ratio` 沉淀入网。
  - 诅咒牢笼、暗黑祭坛、网络复活的扣魂顺序同步改为原生优先。
  - `goety_soul.network_discount` 仅控制网络补扣余量是否参与折扣。
- **网络复活**：原版 Arca/图腾已复活时不介入；两者均无法复活时才由网络兜底（合计魂量 ≥ 原版 `MaxSouls`）。
- **手动转化**：只允许把原版魂转入网络，避免网络魂“自转”。

### ✨ 新增

- **铁魔法（Iron's Spells）网络魔力源**：
  - 新网络键 `beyond_integration:stack_type/iron_mana`；旧键 `beyond_integration:stack_type/mana` 仍注册并在服务器启动时自动迁移。
  - 施法消耗优先扣玩家魔力，缺口由网络补；`canBeCastedBy` 校验计入网络魔力。
  - 自然回蓝满蓝沉淀、瞬间回复溢出沉淀、固定网络回蓝、网络独立回蓝。
  - 网络魔力独立 HUD（可拖动）+ 增量同步。
- **Goety RI 灵魂键**：支持 `beyonddimensions:stack_type/goety_soul`（配置 `goety_soul.use_ri_key`），RI 缺席时自动兜底注册，并在启用时迁移旧数据。
- **灵魂增减弹字**：网络魂 `+N/-N` 弹字始终显示；旧版 Goety 无自带弹字时由 BI 补显示原版魂弹字。
- **配置界面**：
  - 新增「网络魔力源（铁魔法）」分区（10 项服务端配置）。
  - 客户端新增「网络魔力 HUD」开关；「主网络同步状态 HUD」默认改为关闭。
- **HUD 位置框架**：编辑器保存改为屏幕相对比例 `RATIO`，窗口/分辨率缩小后按比例跟随；兼容旧 `RELATIVE`/`ABSOLUTE` 格式，非法值自动回退默认。

### 🐛 修复

- **Goety ≥ 2.5.59 兼容**：新增 `decreaseSoulsNoDiscount` 兼容注入（`require=0` + 反射），旧版自动跳过。
- **旧版铁魔法兼容（3.4.0.9）**：`ServerConfigs` 缺失 `CREATIVE_MANA_COST` / `MANA_REGEN_MULTIPLIER` 时回退旧语义，避免 `NoSuchFieldError`。
- **HUD 编辑器**：修正按钮点击被 HUD 拖动抢占、预览覆盖按钮、窗口缩放丢失未保存位置、NET_SYNC 预览宽度与偏移不一致等问题。
- **HUD 依赖缺失**：未安装 Goety / 铁魔法时编辑器自动隐藏对应元素，避免加载缺失类导致客户端崩溃。

### 📝 其他

- 版本升级至 **0.7.3**。文档中 HUD 布局、Goety 联动与配置分区说明同步更新。

---

## 0.6.0（2026-08-17）

> 适用分支：`1.20.1-FORGE`（Forge 47.4.0）与 `1.21.1-NEOFORGE`（NeoForge 21.1.219）同步发布，版本号统一对齐。
> 本版本变更以 0.5.0 发布包为基线。

### ✨ 新增

- **统一 BD 订阅中心 `BdSubscriptionHub`**：集中管理 Beyond Dimensions 网络存储订阅——
  统一弱引用订阅入口、owner 注册表（重复订阅自动替换、GC 惰性清理）、网络销毁/服务器停止批量退订、订阅失败记录 WARN 日志
- **网络工作站体系**（快捷键 + 6 种远程工作站）：
  - `ModMenus` 注册 合成台 / 铁砧 / 切石机 / 砂轮 / 锻造台 / 存储 6 种菜单
  - 6 个对应 GUI（`Dimensions*GUI`）与渲染辅助（`WorkstationRenderHelper` / `WorkstationTransferHelper` / `WorkstationModeConstants`）
  - 存储界面扩展（`StorageMenuExtension`）、附魔分离开关按钮（`EnchantToggleBtn`）、5 个打开快捷键（`BDKeyBindings`）
- **自动图腾 `AutoTotemHandler`**：死亡时自动从网络（仅网络）消耗不死图腾救援，效果对齐原版
  （回血 / 清效果 / buff / 图腾动画），支持伤害黑名单（空列表 = 全部伤害类型可触发）与冷却
- **配方驱动定向扫描组件 `RecipeMaterialScanner`**：通用两阶段扫描
  （`IDENTITY` 材料精确键 O(1) 零桶扫描；`forge:partial_nbt` 材料先定向、不足时全桶兜底）
- **TACZ 枪械台网络合成**：背包 + 网络混合取料批量合成（单次上限 64），产物可入背包或回存网络，
  支持 partial_nbt 材料（枪械升阶、特定弹药/配件），JEI 配方一键转移（`CraftRecipeTransferHandler` / `CutRecipeTransferHandler`）
- **ywzj 载具支持**（1.21.1）：网络绑定 / 能量充电 / 弹药补给 / HUD 显示（`YwzjVehicleSyncHandler` + `mixin/ywzj_vehicle/*`）
- **网络弹药体系重构**（NeoForge 侧从旧 `feature.ammo.*` 平铺结构迁移至 `handler.*`）：
  SW 虚拟弹药追踪（`SwAmmoTracker` / `SwAmmoPollingService`）、TACZ 弹药追踪（`TaczAmmoTracker` / `TaczAmmoPollingService` / `PlayerNetUsageTracker`）
- **新网络包**：`OpenStorageMenuPayload` / `RecipeFillPayload` / `SetAnvilNamePayload` / `ProtectItemPayload` / `CleanWorkstationPayload`、
  `SuperbAmmoDeltaS2CPacket`（SW 差值推送）、`TaczAmmoPushPayload` / `TaczAmmoPushS2CPacket`（TACZ 轮询推送）、`RequestItemAmmoPacket` / `ItemAmmoResponsePacket`
- **成员权限命令**：`/bdtools member addMembers / addManagers / removePlayers / removeManagers`
- **其他**：`FakePlayerNetMarker`（哨戒假玩家网络标记）、`SentryNetIdAccessor`、`SentryArmNetMixin`、
  `VehicleNetSyncMixin`、`SlotAccessor`、`MaidCacheCleanupMixin`（女仆缓存清理）、`PlaceboCacheWriteDisableMixin`（整合包稳定性）

### 🚀 优化

- **TACZ 弹药查询缓存化（`TaczAmmoTracker` → 查询缓存）**：
  - 热路径（射击/装填/判定/HUD）全部 O(1) 读缓存；缓存 miss 走精确键快速路径 + 惰性全量回填（一次 O(桶)）
  - 创造箱计数收敛为单一权威：只读虚拟计数（BD 统一输入输出 delta 事件维护并持久化），物理扫描仅在对账（首次创建）时执行一次
  - 全量扫描仅首次创建；之后 delta 增量维护；轮询无变化不推送（减少网络流量）
  - 追踪器 5 分钟闲置回收（网络销毁/停服清理保留）
- **TACZ 工作台配方驱动定向扫描**：合成/界面计数按配方材料定向查询（零桶扫描），
  partial_nbt 材料标准形态直接精确键命中；NBT 变种场景定向不足时全桶兜底保证正确
- **扫描实现**：`countAllAmmoInNetwork` / `countAmmoByKey` 改用 `getStorage()` 单次遍历（消除桶遍历 + 逐 key 二次哈希）

### 🐛 修复

- **自动图腾虚空死亡**：`damage_blacklist` 为空列表时所有伤害类型均可触发（含虚空/命令）；
  新增 `auto_totem.respect_bypasses_invulnerability` 配置（默认 `false` = 虚空/命令也可触发，`true` = 恢复原版语义）
- **附魔分离刷取修复**：`/bdtools enchant separate` 的书籍/物品分支先扣除原物品并校验
  extract 返回值（不足则退回已扣并跳过），防止"只产出不扣书"的刷取
- **合成产物丢失兜底**：产物回存网络时检查 insert 余量，网络容量不足时余量以掉落物归还玩家（不再凭空消失）
- **弹药箱拆箱插入余量告警**：网络容量不足导致弹药/空箱无法全部入库时记录 WARN（不再静默丢失）
- **合成后枪械台界面计数不刷新**：`NetworkItemCountsPacket` 回传键统一为 `"recipeId|idx"`（与界面读取键对齐）
- **持久化**：修复跨存档静态数据残留（防止旧世界数据写入新存档）；存档损坏的非法网络 ID 键跳过而非世界加载崩溃
- **客户端弹药请求永久卡死**：`TaczAmmoCache` 请求去重加 2 秒超时复位（服务端无响应时不再卡死）
- **HUD 弹药量残留（换弹拿空后卡在旧数量）**：`TaczAmmoCache` 应用快照时——网络切换直接清空旧缓存；
  同网络时先移除快照中已消失的弹药键再合并（弹药拿空后 HUD 归零，不再误判可换弹/开火导致动画异常）
- **SW/工作台客户端缓存清理挂点**：进服/登出时清空 `SuperbAmmoCache`（快照表）与 `NetworkItemCache`，
  防止旧网络快照残留（netId 复用显示旧数据、内存增长）
- **SW ITEM 现查来源竞态**：玩家与载具交错发起 ITEM 现查时，响应来源标记按请求 netId 匹配（原单标志会标错来源导致计数读错）
- **构建**：开发环境 `mixins.json` 自动同步进 classes 输出（mixin bootstrap 读取），jar 打包去重（duplicatesStrategy EXCLUDE）

### 🔧 行为变更

- **网络弹药寻找范围限制为仅玩家主网络**（TACZ 与 SW 路径统一）：射击/装填/判定/HUD 不再遍历其他网络兜底；
  载具/女仆等非玩家来源仍使用各自绑定的网络，不受影响
- **扣弹后即时推送弹药更新**：利用 extract 返回值（实际提取量）在扣弹成功后立即推送弹药快照给玩家——
  TACZ 经 `pushSnapshotToPlayer`（不等 10 tick 轮询），SW 的 ITEM 弹药路径补齐 `pushUpdate` 差分推送（玩家/载具路径已有）；
  HUD 弹药量实时更新

### ♻️ 移除 / 重构（相对 0.5.0 发布包）

- **旧配置体系**（NeoForge）：`config.BlacklistConfig` / `EnchantConfig` / `GeneralConfig` / `VehicleConfig` → 统一并入 `CommandConfig`
- **旧弹药结构**（NeoForge）：`feature.ammo.common.AbstractAmmoInsertHandler`、`SwPlayerAmmoSyncer` / `SwVehicleAmmoSyncer`、
  `YwzjCreativeInsertHandler`、`EnchantCostCalculator`、`cache.AbstractCache` → 新 handler/feature 分包
- **旧命令**：`ProtectCommand` → 工作站物品保护（`ProtectItemPacket`）
- **Forge**：`EnchantSeparationExtension`（并入 `StorageMenuExtension`）、`ReflectionHelper`、`SwPlayerAmmoSyncer`、
  `VehicleNetStorage`、`NetTerminalBauble`、`NetInterfaceExtractMixin`（重构清理）

### 📝 其他

- 版本统一升级至 **0.6.0**（两分支对齐）
- 待办（暂不实施）：SW 工作台网络合成（`计划项目/待办/2026-08-17-SW工作台网络合成.md`）；Tacz-Unidict 兼容适配已完成（`计划项目/已完成/2026-08-17-Tacz-Unidict兼容适配.md`）

---

## 历史版本

### 0.5.0
- 维度网络命令系统与基础功能（以发布包为准）
