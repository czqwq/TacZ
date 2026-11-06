package com.tacz.guns.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.util.ResourceLocationHelper;

/**
 * 移植辅助：把 1.7.10 的实体注册名（{@link EntityList#getEntityString(Entity)}）解析成 1.20 风格的实体 id。
 *
 * <p>
 * <b>为什么需要</b>：上游用 {@code ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())} 得到
 * {@code minecraft:item_frame} / {@code tacz:xxx} 这类 1.20 id，再与配置键（{@code HeadShotAABB}、
 * {@code BulletDamageIgnoreList}、热成像 whitelist/blacklist 等）比对。1.7.10 的注册名有三种形态，
 * 直接 {@code new ResourceLocation(name.toLowerCase())} 会把后两种解析错：
 * <ul>
 * <li>原版：{@code "Zombie"}（build/rfg/.../EntityList.java:279-338），多词名是驼峰（{@code "ItemFrame"}），
 * 不是 1.20 的下划线 id（{@code item_frame}）；</li>
 * <li>FML 模组实体：{@code "<modid>.<entityName>"}（cpw/mods/fml/common/registry/EntityRegistry.java:162-167），
 * 不是 {@code "modid:name"}；</li>
 * <li>自行注册命名的模组实体：可能本来就叫 {@code "modid:name"}。</li>
 * </ul>
 *
 * <p>
 * {@link #getConfigCandidates(Entity)} 同时返回「1.20 风格 id」和「1.7.10 旧实现产生的形态」，
 * 配置比对时两者都查：前者让按 1.20 习惯写的配置生效，后者不破坏已经按旧形态写好的配置。
 */
public final class EntityIdHelper {

    /** 1.7.10 原版注册名 → 1.20 EntityType id；只收录「小写注册名 ≠ 路径」的项。 */
    private static final Map<String, String> VANILLA_MODERN_IDS = new HashMap<>();

    /** 按实体类缓存候选 id（EntityList 的 name 只由类决定）。 */
    private static final Map<Class<?>, String[]> CANDIDATE_CACHE = new ConcurrentHashMap<>();

    static {
        VANILLA_MODERN_IDS.put("XPOrb", "minecraft:experience_orb");
        VANILLA_MODERN_IDS.put("LeashKnot", "minecraft:leash_knot");
        VANILLA_MODERN_IDS.put("SmallFireball", "minecraft:small_fireball");
        VANILLA_MODERN_IDS.put("ThrownEnderpearl", "minecraft:ender_pearl");
        VANILLA_MODERN_IDS.put("EyeOfEnderSignal", "minecraft:eye_of_ender");
        VANILLA_MODERN_IDS.put("ThrownPotion", "minecraft:potion");
        VANILLA_MODERN_IDS.put("ThrownExpBottle", "minecraft:experience_bottle");
        VANILLA_MODERN_IDS.put("ItemFrame", "minecraft:item_frame");
        VANILLA_MODERN_IDS.put("WitherSkull", "minecraft:wither_skull");
        VANILLA_MODERN_IDS.put("PrimedTnt", "minecraft:tnt");
        VANILLA_MODERN_IDS.put("FallingSand", "minecraft:falling_block");
        VANILLA_MODERN_IDS.put("FireworksRocketEntity", "minecraft:firework_rocket");
        VANILLA_MODERN_IDS.put("MinecartRideable", "minecraft:minecart");
        VANILLA_MODERN_IDS.put("MinecartChest", "minecraft:chest_minecart");
        VANILLA_MODERN_IDS.put("MinecartFurnace", "minecraft:furnace_minecart");
        VANILLA_MODERN_IDS.put("MinecartTNT", "minecraft:tnt_minecart");
        VANILLA_MODERN_IDS.put("MinecartHopper", "minecraft:hopper_minecart");
        VANILLA_MODERN_IDS.put("MinecartSpawner", "minecraft:spawner_minecart");
        VANILLA_MODERN_IDS.put("MinecartCommandBlock", "minecraft:command_block_minecart");
        VANILLA_MODERN_IDS.put("PigZombie", "minecraft:zombified_piglin");
        VANILLA_MODERN_IDS.put("CaveSpider", "minecraft:cave_spider");
        VANILLA_MODERN_IDS.put("LavaSlime", "minecraft:magma_cube");
        VANILLA_MODERN_IDS.put("EnderDragon", "minecraft:ender_dragon");
        VANILLA_MODERN_IDS.put("WitherBoss", "minecraft:wither");
        VANILLA_MODERN_IDS.put("MushroomCow", "minecraft:mooshroom");
        VANILLA_MODERN_IDS.put("SnowMan", "minecraft:snow_golem");
        VANILLA_MODERN_IDS.put("Ozelot", "minecraft:ocelot");
        VANILLA_MODERN_IDS.put("VillagerGolem", "minecraft:iron_golem");
        VANILLA_MODERN_IDS.put("EntityHorse", "minecraft:horse");
        VANILLA_MODERN_IDS.put("EnderCrystal", "minecraft:end_crystal");
    }

    private EntityIdHelper() {}

    /**
     * 1.20 风格实体 id；实体无注册名时返回 {@code null}。
     */
    @Nullable
    public static ResourceLocation getModernId(@Nullable Entity entity) {
        List<String> candidates = getConfigCandidates(entity);
        return candidates.isEmpty() ? null : ResourceLocationHelper.tryParse(candidates.get(0));
    }

    /**
     * 配置键匹配候选：1.20 风格 id 在前、1.7.10 旧形态在后；均无时返回空列表。
     */
    public static List<String> getConfigCandidates(@Nullable Entity entity) {
        if (entity == null) {
            return Collections.emptyList();
        }
        String[] cached = CANDIDATE_CACHE.get(entity.getClass());
        if (cached != null) {
            return Arrays.asList(cached);
        }
        String[] computed = computeCandidates(entity);
        // 骷髅的 wither 变体共用同一个类，不能按类缓存
        if (!(entity instanceof EntitySkeleton)) {
            CANDIDATE_CACHE.put(entity.getClass(), computed);
        }
        return Arrays.asList(computed);
    }

    private static String[] computeCandidates(Entity entity) {
        String name = EntityList.getEntityString(entity);
        if (name == null || name.isEmpty()) {
            return new String[0];
        }
        String modern = toModernId(name, entity);
        String legacy = toLegacyId(name);
        List<String> candidates = new ArrayList<>(2);
        if (modern != null) {
            candidates.add(modern);
        }
        if (legacy != null && !legacy.equals(modern)) {
            candidates.add(legacy);
        }
        return candidates.toArray(new String[0]);
    }

    @Nullable
    private static String toModernId(String name, Entity entity) {
        // 含命名空间或 FML 的 "modid.entityName" 时，统一委托 api/util/ResourceLocationHelper#tryParseEntityName
        // （':' 走 tryParse；'.' 把最后一个 '.' 换 ':'；1.20 注册名是小写，故先整串小写再解析）。
        int dot = name.indexOf('.');
        if (name.indexOf(':') >= 0 || (dot > 0 && dot < name.length() - 1)) {
            ResourceLocation parsed = ResourceLocationHelper.tryParseEntityName(name.toLowerCase(Locale.ROOT));
            return parsed == null ? null : parsed.toString();
        }
        if (entity instanceof EntitySkeleton && ((EntitySkeleton) entity).getSkeletonType() == 1) {
            return "minecraft:wither_skeleton";
        }
        String mapped = VANILLA_MODERN_IDS.get(name);
        return mapped != null ? mapped : "minecraft:" + name.toLowerCase(Locale.ROOT);
    }

    /** 1.7.10 旧实现（{@code new ResourceLocation(name.toLowerCase())}）产生的形态，仅用于兼容旧配置。 */
    @Nullable
    private static String toLegacyId(String name) {
        if (name.indexOf(':') > 0) {
            return null;
        }
        return "minecraft:" + name.toLowerCase(Locale.ROOT);
    }
}
