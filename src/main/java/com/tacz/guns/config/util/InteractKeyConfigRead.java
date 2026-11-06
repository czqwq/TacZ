package com.tacz.guns.config.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAnvil;
import net.minecraft.block.BlockBeacon;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBrewingStand;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockEnchantmentTable;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.BlockHopper;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockSign;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockWorkbench;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.item.EntityMinecartChest;
import net.minecraft.entity.item.EntityMinecartEmpty;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.util.EntityIdHelper;

/**
 * 把 {@code interact_key.*} 配置解析成方块/实体的白名单与黑名单。
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/config/util/InteractKeyConfigRead.java}
 *
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>上游除配置列表外还有数据包 tag：{@code tacz:interact_key/whitelist|blacklist}（方块/实体各一份，
 * 默认内容见 {@code data/tacz/tags/blocks/interact_key/whitelist.json} 与
 * {@code data/tacz/tags/entity_types/interact_key/whitelist.json}）。1.7.10 没有 tag 系统，
 * 因此把<b>默认数据</b>按类型内置（{@link #isDefaultWhitelistedBlock}/{@link #isDefaultWhitelistedEntity}），
 * 第三方枪包追加的 tag 内容无法加载（平台差异，已记录）。</li>
 * <li>方块用 {@link Block} + metadata（无 {@code BlockState}）；实体 id 的 1.20 风格换算统一走
 * {@link EntityIdHelper#getConfigCandidates(Entity)}（同时返回 1.20 id 与 1.7.10 旧形态候选，
 * 兼容按两种写法填写的配置），见该类对 {@code "Zombie"} / {@code "modid.name"} 形态的说明。</li>
 * <li>1.7.10 没有的 1.20 tag 项（{@code barrel}/{@code blast_furnace}/{@code shulker_boxes}/
 * {@code all_hanging_signs} 等方块，{@code camel}/{@code glow_item_frame}/{@code server 端 interaction}/
 * {@code chest_boat}/{@code wandering_trader} 等实体）无等价物，不内置。</li>
 * </ul>
 */
public class InteractKeyConfigRead {

    /** 上游默认方块 tag 里属于本模组的部分（{@code target} 不在上游 tag 里）。按注册名比对，避免早于方块注册时解析失败。 */
    private static final Set<String> DEFAULT_WHITELIST_BLOCK_IDS = new HashSet<>(
        Arrays.asList(
            "tacz:gun_smith_table",
            "tacz:workbench_a",
            "tacz:workbench_b",
            "tacz:workbench_c",
            "tacz:statue",
            "tacz:gun_pegboard",
            "tacz:glow_gun_pegboard",
            "tacz:gun_display_case",
            "tacz:ammo_storage",
            "tacz:ammo_storage_gold",
            "tacz:ammo_storage_diamond"));

    private static final EnumMap<Type, List<ResourceLocation>> WHITELIST = new EnumMap<>(Type.class);
    private static final EnumMap<Type, List<ResourceLocation>> BLACKLIST = new EnumMap<>(Type.class);

    public static void init() {
        WHITELIST.clear();
        BLACKLIST.clear();
        handleConfigData(SyncConfig.INTERACT_KEY_WHITELIST_BLOCKS.get(), WHITELIST, Type.BLOCK);
        handleConfigData(SyncConfig.INTERACT_KEY_WHITELIST_ENTITIES.get(), WHITELIST, Type.ENTITY);
        handleConfigData(SyncConfig.INTERACT_KEY_BLACKLIST_BLOCKS.get(), BLACKLIST, Type.BLOCK);
        handleConfigData(SyncConfig.INTERACT_KEY_BLACKLIST_ENTITIES.get(), BLACKLIST, Type.ENTITY);
    }

    public static boolean canInteractBlock(Block block, int metadata) {
        if (block == null) {
            return false;
        }
        ResourceLocation blockId = getBlockId(block);
        // 先检查黑名单（上游顺序：配置 → tag；默认 blacklist tag 为空）
        if (isListed(BLACKLIST, Type.BLOCK, blockId)) {
            return false;
        }
        // 再检查白名单（配置 → 默认 tag 内容）
        if (isListed(WHITELIST, Type.BLOCK, blockId)) {
            return true;
        }
        return isDefaultWhitelistedBlock(block);
    }

    public static boolean canInteractBlock(Block block) {
        return canInteractBlock(block, 0);
    }

    public static boolean canInteractEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        // 先检查黑名单（上游顺序：配置 → tag；默认 blacklist tag 为空）
        if (isEntityListed(BLACKLIST, entity)) {
            return false;
        }
        // 再检查白名单（配置 → 默认 tag 内容）
        if (isEntityListed(WHITELIST, entity)) {
            return true;
        }
        return isDefaultWhitelistedEntity(entity);
    }

    /**
     * 上游 {@code data/tacz/tags/blocks/interact_key/whitelist.json} 的 1.7.10 等价物（无 tag 系统）：
     * 原版可交互方块按类型判定 + 本模组方块按注册名判定。
     */
    private static boolean isDefaultWhitelistedBlock(Block block) {
        if (block instanceof BlockChest || block instanceof BlockWorkbench
            || block instanceof BlockFurnace
            || block instanceof BlockEnchantmentTable
            || block instanceof BlockBrewingStand
            || block instanceof BlockEnderChest
            || block instanceof BlockHopper
            || block instanceof BlockBeacon
            || block instanceof BlockLever
            || block instanceof BlockAnvil
            || block instanceof BlockBed
            || block instanceof BlockFenceGate
            || block instanceof BlockSign
            || block instanceof BlockButton) {
            return true;
        }
        // 上游 tag 的 #minecraft:wooden_doors / #minecraft:wooden_trapdoors（1.7.10 铁门/铁活板门不受理）
        if (block instanceof BlockDoor || block instanceof BlockTrapDoor) {
            return block.getMaterial() == Material.wood;
        }
        return DEFAULT_WHITELIST_BLOCK_IDS.contains(getBlockIdString(block));
    }

    /**
     * 上游 {@code data/tacz/tags/entity_types/interact_key/whitelist.json} 的 1.7.10 等价物：
     * minecart / chest_minecart / item_frame / boat / mule / villager / horse（1.7.10 的 {@link EntityHorse}
     * 同时覆盖 horse/mule/donkey）。
     */
    private static boolean isDefaultWhitelistedEntity(Entity entity) {
        return entity instanceof EntityMinecartEmpty || entity instanceof EntityMinecartChest
            || entity instanceof EntityItemFrame
            || entity instanceof EntityBoat
            || entity instanceof EntityHorse
            || entity instanceof EntityVillager;
    }

    private static boolean isListed(EnumMap<Type, List<ResourceLocation>> map, Type type, ResourceLocation id) {
        if (id == null || !map.containsKey(type)) {
            return false;
        }
        return map.get(type)
            .contains(id);
    }

    /** 实体 id 的候选形态（1.20 id / 1.7.10 旧形态）逐个与配置列表比对，见 {@link EntityIdHelper}。 */
    private static boolean isEntityListed(EnumMap<Type, List<ResourceLocation>> map, Entity entity) {
        if (!map.containsKey(Type.ENTITY)) {
            return false;
        }
        List<ResourceLocation> ids = map.get(Type.ENTITY);
        for (String candidate : EntityIdHelper.getConfigCandidates(entity)) {
            try {
                if (ids.contains(new ResourceLocation(candidate))) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // 非法候选：跳过
            }
        }
        return false;
    }

    private static ResourceLocation getBlockId(Block block) {
        if (block == null) {
            return null;
        }
        // 1.7.10 的 RegistryNamespaced#getNameForObject 返回 String（1.20 返回 ResourceLocation）
        String name = getBlockIdString(block);
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return new ResourceLocation(name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String getBlockIdString(Block block) {
        return block == null ? null : Block.blockRegistry.getNameForObject(block);
    }

    private static void handleConfigData(List<String> configData, EnumMap<Type, List<ResourceLocation>> storeList,
        Type type) {
        if (configData == null) {
            return;
        }
        configData.forEach(data -> {
            if (data == null || data.trim()
                .isEmpty()) {
                return;
            }
            try {
                ResourceLocation id = new ResourceLocation(data.trim());
                storeList.computeIfAbsent(type, t -> new ArrayList<>())
                    .add(id);
            } catch (RuntimeException e) {
                GunMod.LOGGER.warn("Ignoring invalid id '{}' in interact_key config", data);
            }
        });
    }

    public enum Type {
        BLOCK,
        ENTITY
    }
}
