package com.tacz.guns.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

/**
 * 热成像的实体类型规则（哪些实体算「热源」）。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>上游用 1.20 的 <b>实体类型标签</b> {@code tacz:thermal_imaging/blacklist} /
 * {@code .../whitelist}（{@code TagKey<EntityType<?>>}，由数据包驱动，可在重载后热更新）。
 * 1.7.10 <b>没有 tag 系统</b>（1.13+ 才有），也没有 {@code EntityType} 注册表，
 * 因此退化为两张由代码填充的集合（{@link #registerBlacklist(ResourceLocation)} /
 * {@link #registerWhitelist(ResourceLocation)}）。当前无调用者，等价于上游「标签文件不存在」的状态：
 * 上游默认 blacklist 只有 {@code minecraft:armor_stand}（1.7.10 没有盔甲架）、whitelist 为空，
 * 因此对 1.7.10 已有实体而言 {@code instanceof EntityLivingBase} 就是完整判定。</li>
 * <li>实体 id：1.20 用注册表 key；1.7.10 的注册名形态不同（驼峰 / FML 的 {@code modid.name}），
 * 由 {@link EntityIdHelper} 转成 1.20 风格 id 并同时给出旧形态候选，两张集合对候选都做匹配。</li>
 * <li>{@code entity instanceof LivingEntity} → {@link EntityLivingBase}。</li>
 * <li>黑名单优先于白名单的判定顺序<b>与上游完全一致</b>。</li>
 * <li>这是纯客户端功能（热成像渲染 {@code client/renderer/scope/ScopeThermalRenderer} 调用），
 * 但判定本身只依赖实体与集合，故不需要 hook 接口。</li>
 * </ul>
 */
public final class ThermalImagingHelper {

    /** 1.20 的 {@code tacz:thermal_imaging/blacklist} 标签的 1.7.10 替代。 */
    private static final Set<String> BLACKLIST = Collections.synchronizedSet(new HashSet<>());
    /** 1.20 的 {@code tacz:thermal_imaging/whitelist} 标签的 1.7.10 替代。 */
    private static final Set<String> WHITELIST = Collections.synchronizedSet(new HashSet<>());

    private ThermalImagingHelper() {}

    /** 等价于往上游 {@code tacz:thermal_imaging/blacklist} 标签里加一项。 */
    public static void registerBlacklist(ResourceLocation entityId) {
        if (entityId != null) {
            BLACKLIST.add(entityId.toString());
        }
    }

    /** 等价于往上游 {@code tacz:thermal_imaging/whitelist} 标签里加一项。 */
    public static void registerWhitelist(ResourceLocation entityId) {
        if (entityId != null) {
            WHITELIST.add(entityId.toString());
        }
    }

    public static void clearBlacklist() {
        BLACKLIST.clear();
    }

    public static void clearWhitelist() {
        WHITELIST.clear();
    }

    /** A blacklist veto takes precedence, including when a type also appears in the whitelist. */
    public static boolean isHeatSource(Entity entity) {
        List<String> ids = EntityIdHelper.getConfigCandidates(entity);
        for (String id : ids) {
            if (BLACKLIST.contains(id)) {
                return false;
            }
        }
        if (entity instanceof EntityLivingBase) {
            return true;
        }
        for (String id : ids) {
            if (WHITELIST.contains(id)) {
                return true;
            }
        }
        return false;
    }
}
