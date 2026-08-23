package com.solr98.beyondintegration.handler;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.server.ServerLifecycleHooks;
import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * {@code beyond_integration_data.dat} 的读取/保存入口（BD 网络关联数据的唯一持久化文件）。
 *
 * <p>文件位置：{@code <世界目录>/data/beyond_integration_data.dat}，GZIP 压缩的 NBT，
 * 根结构为 {@code {"data": {...}}}，与 Minecraft {@code DimensionDataStorage}
 * 的存储格式完全一致；本类为 BI 全部 BD 网络关联数据的读写唯一入口：
 * SW 虚拟弹药、TACZ 创造弹药箱记录与附魔分离开关。
 *
 * <p>NBT 内容格式（与 1.21.1 版本统一，由 {@link NetworkAmmoData} 负责序列化/反序列化）：
 * <pre>
 * root
 * └── "data" (CompoundTag)
 *     ├── "networkAmmo" (CompoundTag)         网络 ID → 弹药映射
 *     │   └── "&lt;netId&gt;" (CompoundTag)
 *     │       └── "ammo" (CompoundTag)
 *     │           └── "&lt;弹药类型id&gt;" (Long)
 *     ├── "enchantSeparation" (CompoundTag)   网络 ID → 附魔分离开关
 *     │   └── "&lt;netId&gt;" (Boolean)
 *     ├── "creativeTypes" (CompoundTag)       网络 ID → TACZ 创造弹药类型集合
 *     │   └── "&lt;netId&gt;" (CompoundTag)
 *     │       ├── "size" (Int)
 *     │       └── "0".."size-1" (String)
 *     └── "creativeAllType" (CompoundTag)     网络 ID → 全类型创造标记（仅 true 写入）
 *         └── "&lt;netId&gt;" (Boolean)
 * </pre>
 *
 * <p>兼容迁移：旧版 {@code beyond_integration_attachments.dat}（Networks 结构）在
 * 读取存档时自动转化并落盘为统一的新键文件，旧文件删除（{@link #migrateLegacy}）。
 *
 * <p>读写时机约定：
 * <ul>
 *   <li>读取：主世界 {@code LevelEvent.Load} 时经 {@link #migrateLegacy} 兼容迁移后
 *       {@code NetworkAmmoData.initialize} 从 DataStorage 懒加载（静态单实例）；</li>
 *   <li>保存：数据变更走 {@code NetworkAmmoData.markDirty()}，由 MC 定时自动写盘；
 *       服务器停止时 {@link #saveNow()} 兜底强制落盘（已挂 ServerStoppingEvent）。</li>
 * </ul>
 *
 * <p>与 MC 原生规则一致：每次写盘前把旧文件轮转为 {@code .dat_old} 备份，
 * 主文件损坏时可回滚；写盘失败仅 WARN，不影响服务器运行。
 */
public final class NetworkDataStore {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 存档键名（对应文件 {@code <世界目录>/data/beyond_integration_data.dat}，两版本统一） */
    public static final String DATA_NAME = "beyond_integration_data";
    /** 旧版键名（1.20.1 早期 {@code beyond_integration_attachments}，Networks 结构），读取时自动迁移 */
    public static final String LEGACY_DATA_NAME = "beyond_integration_attachments";

    private NetworkDataStore() {}

    /**
     * 读取（兜底加载）：
     * <ul>
     *   <li>读取前先执行旧存档兼容迁移（{@link #migrateLegacy}），确保数据在新键名下；</li>
     *   <li>服务端：若主世界已加载而实例未初始化（极端时序），按现有初始化流程
     *       从 DataStorage 加载一次并复用缓存实例（保证全服单实例）；</li>
     *   <li>无服务器（客户端/开发环境）：返回临时空实例，仅保证内存读写安全，不落盘。</li>
     * </ul>
     *
     * @return 网络关联数据的活动实例（未就绪时返回临时空实例）
     */
    public static NetworkAmmoData load() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return new NetworkAmmoData();
        migrateLegacy(server);
        NetworkAmmoData data = NetworkAmmoData.activeInstance();
        if (data == null) {
            ServerLevel overworld = server.overworld();
            if (overworld != null) {
                NetworkAmmoData.initialize(overworld);
                data = NetworkAmmoData.activeInstance();
            }
        }
        return data != null ? data : new NetworkAmmoData();
    }

    /**
     * 旧存档兼容迁移：读取时发现旧键文件（{@code beyond_integration_attachments.dat}，
     * Networks 结构）而新键文件不存在时，解析旧结构并转化为统一的 {@link #DATA_NAME}
     * 新格式落盘，随后删除旧文件（迁移失败仅 WARN，不影响存档加载）。
     * 幂等：新文件已存在或旧文件不存在时直接返回。需在主世界 DataStorage 初始化前调用。
     */
    public static void migrateLegacy(MinecraftServer server) {
        if (server == null) return;
        try {
            // data 目录为世界根目录下的 "data" 子目录
            File dir = server.getWorldPath(LevelResource.ROOT).resolve("data").toFile();
            Files.createDirectories(dir.toPath());
            File newFile = new File(dir, DATA_NAME + ".dat");
            File oldFile = new File(dir, LEGACY_DATA_NAME + ".dat");
            if (newFile.exists() || !oldFile.exists()) return;

            // 解析旧格式（Networks 结构）并填充内存缓存
            CompoundTag root = NbtIo.readCompressed(oldFile);
            CompoundTag dataTag = root.contains("data") ? root.getCompound("data") : new CompoundTag();
            NetworkAmmoData migrated = NetworkAmmoData.loadLegacy(dataTag);

            // 以统一新格式落盘，随后删除旧文件
            CompoundTag out = new CompoundTag();
            out.put("data", migrated.save(new CompoundTag()));
            NbtIo.writeCompressed(out, newFile);
            Files.delete(oldFile.toPath());
            LOGGER.info("NetworkDataStore: 已将旧存档 {} 迁移为 {}", LEGACY_DATA_NAME, DATA_NAME);
        } catch (IOException e) {
            LOGGER.warn("NetworkDataStore: 迁移旧存档 {} 失败 ({})", LEGACY_DATA_NAME, e.getMessage());
        }
    }

    /**
     * 立即保存（兜底落盘）：服务器停止等时机调用，绕过 MC 定时保存直接写入磁盘。
     * <ul>
     *   <li>写入格式：根 {@code {"data": <NetworkAmmoData.save() 结果>}}，GZIP 压缩；</li>
     *   <li>备份机制：旧 {@code .dat} 先轮转为 {@code .dat_old}，再写新文件，
     *       与 MC 原生 {@code DimensionDataStorage} 行为一致；</li>
     *   <li>异常处理：任何 IO 失败仅记录 WARN，不向上抛，不影响服务器停止流程。</li>
     * </ul>
     */
    public static void saveNow() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        NetworkAmmoData data = NetworkAmmoData.activeInstance();
        if (data == null) return; // 主世界未加载过，无数据可存
        try {
            File dir = server.getWorldPath(LevelResource.ROOT).resolve("data").toFile();
            Files.createDirectories(dir.toPath());
            File newFile = new File(dir, DATA_NAME + ".dat");
            File oldFile = new File(dir, DATA_NAME + ".dat_old");

            // 把上次的 .dat 轮转为 .dat_old 备份（与 MC 原生保存规则一致）
            if (newFile.exists()) {
                Files.deleteIfExists(oldFile.toPath());
                Files.move(newFile.toPath(), oldFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            // 组装根结构 {"data": {...}} 并 GZIP 压缩写盘
            CompoundTag root = new CompoundTag();
            root.put("data", data.save(new CompoundTag()));
            NbtIo.writeCompressed(root, newFile);
        } catch (IOException e) {
            LOGGER.warn("NetworkDataStore: 保存 {} 失败 ({})", DATA_NAME, e.getMessage());
        }
    }
}
