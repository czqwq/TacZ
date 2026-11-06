package com.tacz.guns.config.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;
import com.tacz.guns.config.common.AmmoConfig;

/**
 * 把 {@code ammo.PassThroughBlocks / DestroyBlocks} 这两条配置解析成方块集合。
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/config/util/AmmoBlockConfigRead.java}
 *
 * <p>
 * 1.7.10 差异（重要）：
 * <ul>
 * <li>上游可以用 {@code TagKey<Block>} + {@code state.is(tag)} 让配置里的 {@code #forge:glass} 随数据包动态生效；
 * 1.7.10 <b>没有 tag 系统</b>（1.13+ 才有），因此这里内置一张「已知原版 tag → 方块集合」的展开表
 * （{@link #EXPANDED_VANILLA_TAGS}），未识别的 {@code #tag} 只打一条 WARN 并跳过。</li>
 * <li>上游签名 {@code canPassThrough(BlockState)} / {@code canDestroy(BlockState)}；
 * 1.7.10 无 {@code BlockState}，改收 {@link Block} + {@code metadata}。</li>
 * <li>方块 id 解析：1.20 用 {@code ForgeRegistries.BLOCKS}，1.7.10 用 {@link Block#blockRegistry}。</li>
 * </ul>
 */
public final class AmmoBlockConfigRead {

    /**
     * 1.20 tag → 1.7.10 方块集合的展开表。
     * <p>
     * 只收录上游默认配置真正用到的两个 tag（{@code #forge:glass} / {@code #forge:glass_panes}），
     * 外加常见的 {@code #minecraft:...} 同义项，避免用户按 1.20 习惯写配置时全部失效。
     */
    private static final Map<String, List<Block>> EXPANDED_VANILLA_TAGS = new HashMap<>();

    static {
        List<Block> glass = new ArrayList<>();
        glass.add(Blocks.glass);
        glass.add(Blocks.stained_glass);
        EXPANDED_VANILLA_TAGS.put("forge:glass", glass);
        EXPANDED_VANILLA_TAGS.put("minecraft:glass", glass);

        List<Block> panes = new ArrayList<>();
        panes.add(Blocks.glass_pane);
        panes.add(Blocks.stained_glass_pane);
        EXPANDED_VANILLA_TAGS.put("forge:glass_panes", panes);
        EXPANDED_VANILLA_TAGS.put("minecraft:glass_panes", panes);
        EXPANDED_VANILLA_TAGS.put("forge:glass_pane", panes);
    }

    private static volatile Set<Block> passThroughBlocks = Collections.emptySet();
    private static volatile Set<Block> destroyBlocks = Collections.emptySet();

    private AmmoBlockConfigRead() {}

    public static void init() {
        passThroughBlocks = readBlockList(AmmoConfig.PASS_THROUGH_BLOCKS.get(), "PassThroughBlocks");
        destroyBlocks = readBlockList(AmmoConfig.DESTROY_BLOCKS.get(), "DestroyBlocks");
    }

    public static boolean canPassThrough(Block block, int metadata) {
        return passThroughBlocks.contains(block);
    }

    public static boolean canDestroy(Block block, int metadata) {
        return destroyBlocks.contains(block);
    }

    /**
     * 1.7.10 里 metadata 只影响少数方块（羊毛/染色玻璃等），上游 {@code BlockState} 语义在这里退化为「只看方块类型」，
     * 与 {@code DestroyGlass} 这类配置的用途一致。
     */
    public static boolean canPassThrough(Block block) {
        return canPassThrough(block, 0);
    }

    public static boolean canDestroy(Block block) {
        return canDestroy(block, 0);
    }

    private static Set<Block> readBlockList(List<String> entries, String configName) {
        Set<Block> blocks = new HashSet<>();
        if (entries == null) {
            return blocks;
        }
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            String value = entry.trim();
            if (value.isEmpty()) {
                continue;
            }
            boolean isTag = value.startsWith("#");
            String name = isTag ? value.substring(1) : value;
            if (isTag) {
                List<Block> expanded = EXPANDED_VANILLA_TAGS.get(name);
                if (expanded == null) {
                    GunMod.LOGGER.warn(
                        "Ignoring tag '{}' in ammo.{}: 1.7.10 has no block tag system, only {} are expanded",
                        entry,
                        configName,
                        new LinkedHashSet<>(EXPANDED_VANILLA_TAGS.keySet()));
                    continue;
                }
                blocks.addAll(expanded);
                continue;
            }
            Block block = resolveBlock(name);
            if (block == null) {
                GunMod.LOGGER.warn("Ignoring unknown block ID '{}' in ammo.{}", entry, configName);
                continue;
            }
            blocks.add(block);
        }
        return blocks;
    }

    @SuppressWarnings("unchecked")
    private static Block resolveBlock(String name) {
        try {
            ResourceLocation id = new ResourceLocation(name);
            if (!Block.blockRegistry.containsKey(id)) {
                return null;
            }
            Object block = Block.blockRegistry.getObject(id);
            return block instanceof Block ? (Block) block : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
