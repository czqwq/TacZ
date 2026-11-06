package com.tacz.guns.config.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.config.sync.SyncConfig;

/**
 * 解析 {@code misc.HeadShotAABB} 配置：为指定生物覆盖爆头判定用的碰撞箱。
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/config/util/HeadShotAABBConfigRead.java}
 *
 * <p>
 * 1.7.10 差异：{@code net.minecraft.world.phys.AABB} → {@link AxisAlignedBB}（构造参数顺序一致：
 * minX, minY, minZ, maxX, maxY, maxZ）。返回值可为 {@code null}（表示该生物没有自定义爆头箱），
 * 调用方必须判空 —— 与上游一致。
 */
public class HeadShotAABBConfigRead {

    private static final Map<ResourceLocation, AxisAlignedBB> AABB_CHECK = new HashMap<>();
    // 书写格式：touhou_little_maid:maid [-0.5, 1.0, -0.5, 0.5, 1.5, 0.5]
    // 生物 ID + 碰撞箱
    private static final Pattern REG = Pattern.compile(
        "^([a-z0-9_.-]+:[a-z0-9/._-]+)\\s*?\\[([-+]?[0-9]*\\.?[0-9]+),\\s*?([-+]?[0-9]*\\.?[0-9]+),\\s*?([-+]?[0-9]*\\.?[0-9]+),\\s*?([-+]?[0-9]*\\.?[0-9]+),\\s*?([-+]?[0-9]*\\.?[0-9]+),\\s*?([-+]?[0-9]*\\.?[0-9]+),*?\\s*?]");

    public static void init() {
        AABB_CHECK.clear();
        List<String> configData = SyncConfig.HEAD_SHOT_AABB.get();
        if (configData == null) {
            return;
        }
        for (String text : configData) {
            addCheck(text);
        }
    }

    public static void addCheck(String text) {
        if (text == null) {
            return;
        }
        Matcher matcher = REG.matcher(text);
        if (matcher.find()) {
            ResourceLocation id = new ResourceLocation(matcher.group(1));
            double x1 = Double.parseDouble(matcher.group(2));
            double y1 = Double.parseDouble(matcher.group(3));
            double z1 = Double.parseDouble(matcher.group(4));
            double x2 = Double.parseDouble(matcher.group(5));
            double y2 = Double.parseDouble(matcher.group(6));
            double z2 = Double.parseDouble(matcher.group(7));
            AABB_CHECK.put(id, AxisAlignedBB.getBoundingBox(x1, y1, z1, x2, y2, z2));
        }
    }

    public static void clearAABB() {
        AABB_CHECK.clear();
    }

    public static AxisAlignedBB getAABB(ResourceLocation id) {
        return id == null ? null : AABB_CHECK.get(id);
    }

    /**
     * 便利重载：上游调用点（例如 {@code util/EntityUtil}）手里只有字符串形式的实体 id。
     */
    public static AxisAlignedBB getAABB(String id) {
        if (id == null) {
            return null;
        }
        try {
            return getAABB(new ResourceLocation(id));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
