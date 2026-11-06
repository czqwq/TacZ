package com.tacz.guns.inventory.tooltip;

import net.minecraft.util.ResourceLocation;

/**
 * 方块物品 tooltip 的数据载体。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/inventory/tooltip/BlockItemTooltip.java}
 * 
 * <pre>
 * {@code public class BlockItemTooltip implements TooltipComponent}
 * </pre>
 *
 * <p>
 * <b>1.7.10 差异</b>：去掉 {@code TooltipComponent} 接口（1.7.10 无 tooltip 组件管线），纯数据类；
 * {@code net.minecraft.resources.ResourceLocation} → {@link ResourceLocation}。
 */
public class BlockItemTooltip {

    private final ResourceLocation blockId;

    public BlockItemTooltip(ResourceLocation blockId) {
        this.blockId = blockId;
    }

    public ResourceLocation getBlockId() {
        return blockId;
    }
}
