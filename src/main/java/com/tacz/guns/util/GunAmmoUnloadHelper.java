package com.tacz.guns.util;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.FeedType;

/**
 * 枪械物品的基础卸弹结算，不改变拆卸弹匣配件时的既有退弹规则。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>{@code net.minecraft.world.entity.LivingEntity/player.Player} →
 * {@link EntityLivingBase}/{@link EntityPlayer}。</li>
 * <li>{@code shooter.level().isClientSide()} → {@code shooter.worldObj.isRemote}。</li>
 * <li><b>物品栏访问是本文件最大的降级点</b>：上游用
 * {@code shooter.getCapability(ForgeCapabilities.ITEM_HANDLER, null)}（Forge 能力系统），
 * 1.7.10 <b>没有</b> {@code IItemHandler}/{@code ForgeCapabilities}/{@code ItemHandlerHelper}
 * （那是 1.8+ Forge 的 Capability 体系）。1.7.10 的对应物只有
 * {@link EntityPlayer#inventory}（{@link InventoryPlayer}）：
 * <ul>
 * <li>{@link #returnToAmmoBoxes} 改为遍历 {@code InventoryPlayer#mainInventory}（36 格快捷栏+背包）——
 * 与上游 {@code IItemHandler} 的玩家槽位集合一致（1.7.10 玩家没有副手/饰品槽）；</li>
 * <li>{@code ItemHandlerHelper.giveItemToPlayer(player, stack)} 改为
 * {@link InventoryPlayer#addItemStackToInventory(ItemStack)}，
 * 未被收下的余量用 {@link EntityPlayer#dropPlayerItemWithRandomChoice} 掉出
 * （等价上游「塞不进就掉在脚下」）；</li>
 * <li>{@code ItemHandlerHelper.insertItemStacked(...)} 不存在，非玩家实体直接
 * {@link EntityLivingBase#entityDropItem(ItemStack, float)} 掉出，
 * 与上游「inventory == null 时掉出」的分支一致。</li>
 * </ul>
 * 因此<b>非玩家生物不再有「塞回自己背包」的路径</b>（1.7.10 里除玩家外本来也没有通用物品栏）。</li>
 * <li>{@code player instanceof Player player} 的模式匹配改成显式 {@code instanceof} + 强转。</li>
 * <li>逻辑 <b>判弹 / 保留枪膛弹 / 虚拟备弹 / 无限弹匣 / 燃料供弹</b> 分支与上游逐行一致。</li>
 * <li>这是 {@code api/**} 的编译期契约类（{@code AbstractGunItem#unloadAmmo} 直接调用），
 * <b>公开类名与方法签名保持不变</b>。</li>
 * </ul>
 */
public final class GunAmmoUnloadHelper {

    private GunAmmoUnloadHelper() {}

    public static int unloadAmmo(EntityLivingBase shooter, ItemStack stack, AbstractGunItem gun, CommonGunIndex index,
        boolean unloadBarrel, int amount) {
        if (amount < -1 || shooter.worldObj.isRemote || gun.useInventoryAmmo(stack)) {
            return 0;
        }
        int currentAmmo = Math.max(0, gun.getCurrentAmmoCount(stack));
        int magazineAmmo = amount == -1 ? currentAmmo : Math.min(currentAmmo, amount);
        boolean chambered = unloadBarrel && index.getGunData()
            .getBolt() != Bolt.OPEN_BOLT && gun.hasBulletInBarrel(stack);
        int unloaded = magazineAmmo + (chambered ? 1 : 0);
        if (unloaded == 0) {
            return 0;
        }

        boolean returnAmmo = IGunOperator.fromLivingEntity(shooter)
            .needCheckAmmo()
            && !index.getGunData()
                .getReloadData()
                .isInfinite()
            && index.getGunData()
                .getReloadData()
                .getType() != FeedType.FUEL;
        boolean dummyAmmo = gun.useDummyAmmo(stack);
        ResourceLocation ammoId = index.getGunData()
            .getAmmoId();
        CommonAmmoIndex ammoIndex = TimelessAPI.getCommonAmmoIndex(ammoId)
            .orElse(null);
        // 普通弹药缺少定义时不执行卸弹，避免清空后无法生成返还物品。
        if (returnAmmo && !dummyAmmo && (ammoIndex == null || ammoIndex.getStackSize() <= 0)) {
            return 0;
        }

        if (magazineAmmo > 0) {
            gun.setCurrentAmmoCount(stack, currentAmmo - magazineAmmo);
        }
        if (chambered) {
            gun.setBulletInBarrel(stack, false);
        }
        if (!returnAmmo) {
            return unloaded;
        }
        if (dummyAmmo) {
            gun.addDummyAmmoAmount(stack, unloaded);
            return unloaded;
        }

        // 1.7.10：只有玩家有物品栏。
        EntityPlayer player = shooter instanceof EntityPlayer ? (EntityPlayer) shooter : null;
        int remaining = player == null ? unloaded
            : returnToAmmoBoxes(player.inventory, ammoId, ammoIndex.getStackSize(), unloaded);
        while (remaining > 0) {
            int count = Math.min(remaining, ammoIndex.getStackSize());
            ItemStack ammoStack = AmmoItemBuilder.create()
                .setId(ammoId)
                .setCount(count)
                .build();
            if (player != null) {
                player.inventory.addItemStackToInventory(ammoStack);
                // 1.7.10 的 addItemStackToInventory 会把未被收下的余量留在 stackSize 里，需要掉出。
                if (ammoStack != null && ammoStack.stackSize > 0) {
                    player.dropPlayerItemWithRandomChoice(ammoStack, false);
                }
            } else {
                shooter.entityDropItem(ammoStack, 0.0F);
            }
            remaining -= count;
        }
        return unloaded;
    }

    /**
     * 把弹药还进玩家物品栏里的弹匣盒。
     *
     * <p>
     * 1.7.10 差异：上游参数是 {@code IItemHandler}（含任意槽位），这里换成
     * {@link InventoryPlayer} 并只遍历 {@code mainInventory}（玩家实际能放物品的 36 格）。
     */
    private static int returnToAmmoBoxes(InventoryPlayer inventory, ResourceLocation ammoId, int stackSize,
        int amount) {
        int remaining = amount;
        ItemStack[] slots = inventory.mainInventory;
        for (int slot = 0; slot < slots.length && remaining > 0; slot++) {
            ItemStack boxStack = slots[slot];
            if (boxStack == null || boxStack.getItem() == null) {
                continue;
            }
            if (!(boxStack.getItem() instanceof IAmmoBox)) {
                continue;
            }
            IAmmoBox box = (IAmmoBox) boxStack.getItem();
            if (box.isCreative(boxStack) || box.isAllTypeCreative(boxStack)
                || !ammoId.equals(box.getAmmoId(boxStack))) {
                continue;
            }
            int current = box.getAmmoCount(boxStack);
            // 异常负数计数不能作为返还目标，剩余弹药继续交给背包或掉落。
            if (current < 0) {
                continue;
            }
            long capacity = (long) stackSize * SyncConfig.AMMO_BOX_STACK_SIZE.get()
                * ((long) box.getAmmoLevel(boxStack) + 1);
            long space = Math.max(0, Math.min(capacity, Integer.MAX_VALUE) - current);
            int accepted = (int) Math.min(space, remaining);
            if (accepted > 0) {
                box.setAmmoCount(boxStack, current + accepted);
                remaining -= accepted;
            }
        }
        return remaining;
    }
}
