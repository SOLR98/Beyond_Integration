package com.solr98.beyondintegration.feature.mana;

import com.solr98.beyondintegration.core.log.BiLog;
import com.mojang.logging.LogUtils;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
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
 * 魔力键 ID 迁移：把旧键 {@code beyond_integration:stack_type/mana}
 * 的余额转入新键 {@code beyond_integration:stack_type/iron_mana}。
 * <p>服务器启动后执行一次（幂等）；目标键放不下的部分退回旧键，绝不丢弃。
 */
public final class ManaKeyMigration {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** BD 网络存档文件名：BDNet_&lt;id&gt;.dat */
    private static final Pattern NET_FILE =
            Pattern.compile(Pattern.quote("BDNet_") + "(\\d+)\\.dat");

    private ManaKeyMigration() {
    }

    /** 遍历 BD 网络存档并迁移旧魔力键；可重复调用。 */
    public static synchronized void migrate(MinecraftServer server) {
        if (server == null) return;
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
                long amount = storage.getStackByKey(ManaStackKey.LEGACY).amount();
                if (amount <= 0L) continue;

                KeyAmount extracted = storage.extract(ManaStackKey.LEGACY, amount, false, false);
                long got = extracted == null ? 0L : Math.max(0L, extracted.amount());
                if (got <= 0L) continue;

                KeyAmount remainder = storage.insert(ManaStackKey.INSTANCE, got, false);
                long left = remainder == null ? 0L : Math.max(0L, remainder.amount());
                if (left > 0L) {
                    // 新键放不下的部分退回旧键，避免数据丢失
                    storage.insert(ManaStackKey.LEGACY, left, false);
                }
                long moved = got - left;
                if (moved > 0L) {
                    net.setDirty();
                    movedTotal += moved;
                    migratedNets++;
                }
            }
        } catch (IOException e) {
            LOGGER.warn(BiLog.t("[BI-Mana] 迁移旧魔力键失败: {}", "[BI-Mana] failed to migrate the legacy mana key: {}"), e.toString());
            return;
        }

        if (movedTotal > 0L) {
            LOGGER.info(BiLog.t("[BI-Mana] 已将 {} 点魔力从旧键迁移到 iron_mana（{} 个网络）", "[BI-Mana] migrated {} mana from the legacy key to iron_mana ({} network(s))"),
                    movedTotal, migratedNets);
        }
    }
}
