package com.solr98.beyondintegration.feature.feeder;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.core.log.BiLog;
import com.solr98.beyondintegration.init.ModFluids;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fluids.FluidStack;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 存量水表示归一：把 BD 网络里「原版水 + Purity NBT」的余额迁移为对应的 BI 档流体。
 * <p>
 * 与 {@code WaterFluidNormalizeRule}（只管新插入）配套：早期由 Thirst 沙滤等路径带纯度
 * 入网的原版水，终端取水时无法还原原纯度（{@code ModFluids.purityOf(minecraft:water)} = -1，
 * 会回退到配置默认纯度）。这里在服务器启动时一次性转换，幂等；目标流体放不下的部分退回原键，不丢资源。
 */
public final class WaterPurityMigration {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** BD 网络存档文件名：BDNet_&lt;id&gt;.dat */
    private static final Pattern NET_FILE =
            Pattern.compile(Pattern.quote("BDNet_") + "(\\d+)\\.dat");

    private WaterPurityMigration() {
    }

    /** 迁移所有网络里带纯度的原版水（幂等，可在启动时重复调用）。 */
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
                if (storage == null) continue;

                long moved = migrateNet(storage);
                if (moved > 0L) {
                    net.setDirty();
                    movedTotal += moved;
                    migratedNets++;
                }
            }
        } catch (IOException e) {
            LOGGER.warn(BiLog.t("[BI-Water] 迁移 BD 网络水体失败: {}", "[BI-Water] failed to migrate BD network water: {}"), e.toString());
            return;
        }

        if (movedTotal > 0L) {
            LOGGER.info(BiLog.t("[BI-Water] 已将 {} mB 带纯度的原版水迁移为 BI 档流体（{} 个网络）",
                            "[BI-Water] migrated {} mB of purity-tagged vanilla water into BI tier fluids ({} network(s))"),
                    movedTotal, migratedNets);
        }
    }

    /** 单个网络内迁移，返回实际迁移的 mB 数。 */
    private static long migrateNet(UnifiedStorage storage) {
        long moved = 0L;
        for (KeyAmount ka : new ArrayList<>(storage.getStorage())) {
            if (ka == null || ka.isEmpty()) continue;
            if (!(ka.key() instanceof FluidStackKey fk)) continue;
            if (fk.getSource() != Fluids.WATER) continue;

            int purity = ThirstBridge.get().readFluidPurity(fk.getReadOnlyStack());
            if (purity < 0 || purity > 3) continue;

            Fluid tier = ModFluids.tierFluid(purity);
            if (tier == null) continue;
            FluidStackKey target = new FluidStackKey(new FluidStack(tier, 1));
            if (target.isSameTypeSameComponents(fk)) continue;

            long amount = ka.amount();
            if (amount <= 0L) continue;

            KeyAmount extracted = storage.extract(fk, amount, false, false);
            long got = extracted == null ? 0L : Math.max(0L, extracted.amount());
            if (got <= 0L) continue;

            KeyAmount remainder = storage.insert(target, got, false);
            long left = remainder == null ? 0L : Math.max(0L, remainder.amount());
            if (left > 0L) {
                // 目标档放不下（理论上不会）：退回原键，避免资源丢失
                storage.insert(fk, left, false);
            }
            moved += got - left;
        }
        return moved;
    }
}
