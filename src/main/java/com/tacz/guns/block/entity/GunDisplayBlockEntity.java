package com.tacz.guns.block.entity;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 展示方块实体（枪架 / 展示柜）的共同契约；每个展示面按网格大小的平方连续编号。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/entity/GunDisplayBlockEntity.java}
 *
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>{@code Direction} -> {@link ForgeDirection}（1.7.10 没有 {@code net.minecraft.core.Direction}）。</li>
 * <li>{@code BlockEntity} -> {@link TileEntity}（{@code getBlockEntity()} 的返回类型）。</li>
 * <li>空物品：上游用 {@code ItemStack.EMPTY}，本工程统一用 <b>null</b>（见 {@code docs/PORT_GAME.md} 第 1 节），
 * 因此实现类返回 {@code null} 表示空格。</li>
 * </ul>
 */
public interface GunDisplayBlockEntity {

    /** 槽位物品，空格返回 {@code null}（上游 {@code ItemStack.EMPTY}）。 */
    @Nullable
    ItemStack getItem(int slot);

    int getRotation(int slot);

    DisplayGunSlot getDisplaySlot(int slot);

    int getGridSize();

    /** 上游 {@code getSlotFacing(int)}：该槽位挂在哪一面上（折面枪架的第二面与主面不同）。 */
    ForgeDirection getSlotFacing(int slot);

    /** 展示物品是否使用满亮度。 */
    boolean isDisplayGlowing();

    TileEntity getBlockEntity();
}
