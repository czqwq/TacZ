package com.tacz.guns.client.renderer.block;

import net.minecraftforge.common.util.ForgeDirection;

import com.tacz.guns.block.BlockMetadata;
import com.tacz.guns.mcshim.Axis;
import com.tacz.guns.mcshim.PoseStack;

/**
 * 展示容器的槽位变换；输出是调用 ItemRenderer.renderStatic 之前的坐标空间。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/renderer/block/DisplayItemTransforms.java}
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>{@code net.minecraft.core.Direction} → {@link ForgeDirection}（1.7.10 没有 {@code Direction}）。</li>
 * <li>上游 {@code facing.get2DDataValue()} 的 1.7.10 等价物是
 * {@link BlockMetadata#facing2D(ForgeDirection)}。本工程的方块 metadata 低 2 bit 就是按
 * {@code get2DDataValue()} 的编码顺序（0=NORTH、1=SOUTH、2=WEST、3=EAST）存朝向，
 * 因此这里的数值与上游完全一致（见 {@link BlockMetadata} 类注释）。</li>
 * <li>{@code PoseStack} / {@code Axis} 换成 {@code mcshim} 里 JOML 支撑的同名类。</li>
 * </ul>
 */
public class DisplayItemTransforms {

    private DisplayItemTransforms() {}

    public static void apply(PoseStack pose, ForgeDirection facing, double x, double y, double offset, int rotation) {
        pose.translate(0.5D, 0.5D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - BlockMetadata.facing2D(facing) * 90.0F));
        pose.translate(x, y, offset);
        pose.mulPose(Axis.ZP.rotationDegrees(rotation * 45.0F));
        pose.scale(0.5F, 0.5F, 0.5F);
    }
}
