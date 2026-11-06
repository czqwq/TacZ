package com.tacz.guns.client.gameplay;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;

/**
 * 读取「背包供弹」方式下的可用弹药数（不修改枪械 ItemStack）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/gameplay/ClientAmmoCount.java}。
 *
 * <p>
 * 平台替换（相对上游）：{@code LocalPlayer} → {@link EntityPlayerSP}、
 * {@code Inventory} → {@link InventoryPlayer}、{@code inventory.getContainerSize()} →
 * {@code getSizeInventory()}、{@code inventory.getItem(slot)} → {@code getStackInSlot(slot)}、
 * {@code stack.getCount()} → {@code stack.stackSize}。
 *
 * <p>
 * <b>1.7.10 差异（空栈）：</b>1.7.10 没有 {@code ItemStack.EMPTY}，空槽位是 {@code null}
 * （见 {@code docs/PORT_STATUS.md} 的 api 跨包契约第 1 条）。上游循环体里假定
 * {@code inventory.getItem(slot)} 永不为 {@code null}（EMPTY 也能安全 {@code getItem()}），
 * 这里必须先判 {@code null} 再取 {@code getItem()}，否则会 NPE。
 */
public final class ClientAmmoCount {

    public static final int MAX_DISPLAY_COUNT = 9999;

    private ClientAmmoCount() {}

    /** 显示侧共用一份短命缓存；玩法侧直接用 {@link #get}。 */
    public static int getCached(EntityPlayerSP player, ItemStack gun, IGun iGun) {
        return IClientPlayerGunOperator.fromLocalPlayer(player)
            .getDataHolder()
            .getCachedAmmoCount(gun, iGun);
    }

    public static int get(EntityPlayerSP player, ItemStack gun, IGun iGun) {
        // 1.7.10 判空（本轮同类崩溃专项）：本方法是跨包共享入口（gameplay/overlay/animation/model 都调），
        // 而 gun/iGun 都是「由可能为 null 的手持栈推导出来的」——
        // 上游 1.20 的 ItemStack.EMPTY 非 null、getItem 恒可调；1.7.10 空手就是 null，
        // 调用方若把 getIGunOrNull(null) 的结果直接传进来，下面的 iGun.useDummyAmmo(gun) 会 NPE。
        if (player == null || gun == null || iGun == null) {
            return 0;
        }
        if (!IGunOperator.fromLivingEntity(player)
            .needCheckAmmo()) {
            return MAX_DISPLAY_COUNT;
        }
        if (iGun.useDummyAmmo(gun)) {
            return Math.max(0, Math.min(iGun.getDummyAmmoAmount(gun), MAX_DISPLAY_COUNT));
        }

        int count = 0;
        InventoryPlayer inventory = player.inventory;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            // 1.7.10：空槽位是 null，不是 ItemStack.EMPTY
            if (stack == null) {
                continue;
            }
            if (stack.getItem() instanceof IAmmo ammo && ammo.isAmmoOfGun(gun, stack)) {
                count += Math.min(stack.stackSize, MAX_DISPLAY_COUNT - count);
            }
            if (stack.getItem() instanceof IAmmoBox box && box.isAmmoBoxOfGun(gun, stack)) {
                if (box.isAllTypeCreative(stack) || box.isCreative(stack)) {
                    return MAX_DISPLAY_COUNT;
                }
                count += Math.min(Math.max(box.getAmmoCount(stack), 0), MAX_DISPLAY_COUNT - count);
            }
            if (count >= MAX_DISPLAY_COUNT) {
                return MAX_DISPLAY_COUNT;
            }
        }
        return count;
    }
}
