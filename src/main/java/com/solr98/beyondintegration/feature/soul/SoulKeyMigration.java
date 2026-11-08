package com.solr98.beyondintegration.feature.soul;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 启用 {@code goety_soul.use_ri_key} 时的旧数据迁移：
 * 遍历世界 data 下的 BD 网络存档（{@code BDNet_<id>.dat}），把 BI 灵魂键
 * {@code beyond_integration:stack_type/soul_energy} 的余额转入 RI 键
 * {@code beyonddimensions:stack_type/goety_soul}。
 * <p>幂等；目标键放不下的部分退回原键，绝不丢弃；关闭配置不反向迁移。
 */
public final class SoulKeyMigration {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** BD 网络存档文件名：BDNet_&lt;id&gt;.dat */
    private static final Pattern NET_FILE =
            Pattern.compile(Pattern.quote("BDNet_") + "(\\d+)\\.dat");

    private SoulKeyMigration() {
    }

    /** 配置启用时执行迁移；可在配置重载/服务器启动时重复调用（幂等）。 */
    public static synchronized void migrateIfEnabled(MinecraftServer server) {
        if (server == null || !CommandConfig.soulUseRiKey()) return;
        IStackKey<?> riKey = SoulKeyResolver.riKey();
        if (riKey == null) {
            if (!net.neoforged.fml.ModList.get().isLoaded("rs_integration")) {
                LOGGER.info("[BI-Soul] use_ri_key 已启用，但未安装 rs_integration；跳过迁移（继续使用 BI 键）");
            } else {
                LOGGER.warn("[BI-Soul] use_ri_key 已启用，但未找到 RI 灵魂键 {}；跳过迁移",
                        SoulKeyResolver.RI_SOUL_ID);
            }
            return;
        }
        Path dataDir = server.getWorldPath(LevelResource.ROOT).resolve("data");
        if (!Files.isDirectory(dataDir)) return;

        long movedTotal = 0L;
        int migratedNets = 0;
        try (var paths = Files.list(dataDir)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Matcher matcher = NET_FILE.matcher(path.getFileName().toString());
                if (!matcher.matches()) continue;
                int netId;
                try {
                    netId = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                    continue;
                }

                DimensionsNet net = DimensionsNet.getNetFromId(netId);
                if (net == null) continue;
                UnifiedStorage storage = net.getUnifiedStorage();
                long amount = storage.getStackByKey(SoulEnergyStackKey.INSTANCE).amount();
                if (amount <= 0L) continue;

                KeyAmount extracted = storage.extract(
                        SoulEnergyStackKey.INSTANCE, amount, false, false);
                long got = extracted == null ? 0L : Math.max(0L, extracted.amount());
                if (got <= 0L) continue;

                KeyAmount remainder = storage.insert(riKey, got, false);
                long left = remainder == null ? 0L : Math.max(0L, remainder.amount());
                if (left > 0L) {
                    // RI 键放不下的部分退回 BI 键，避免物品/资源丢失
                    storage.insert(SoulEnergyStackKey.INSTANCE, left, false);
                }
                long moved = got - left;
                if (moved > 0L) {
                    net.setDirty();
                    movedTotal += moved;
                    migratedNets++;
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[BI-Soul] 迁移 BD 灵魂数据失败: {}", e.toString());
            return;
        }

        if (movedTotal > 0L) {
            LOGGER.info("[BI-Soul] 已将 {} 点灵魂从 BI 键迁移到 RI 键（{} 个网络）",
                    movedTotal, migratedNets);
        }
    }
}
