package com.tacz.guns.event;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.block.AmmoStorageBlock;
import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.util.AmmoStorageInteractionHelper;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 潜行 + 手持弹药盒右键弹药箱：把弹药盒里的弹药倒入弹药箱。
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/AmmoStorageInteractionEvent.java}
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>{@code @Mod.EventBusSubscriber} → {@link #register()}（由 {@code EventHandlers.registerAll()} 调用）；</li>
 * <li>{@code PlayerInteractEvent.RightClickBlock} → 1.7.10 单一 {@link PlayerInteractEvent} +
 * {@code event.action == Action.RIGHT_CLICK_BLOCK}；</li>
 * <li>{@code InteractionHand.MAIN_HAND} 判断 → 1.7.10 无双持，{@code getHeldItem()} 即主手，判断删除；</li>
 * <li>{@code BlockState#getBlock()} → {@code World#getBlock(x,y,z)}；{@code Level#isClientSide()} →
 * {@code World#isRemote}；{@code BlockPos} → {@code x/y/z}；{@code BlockEntity} → {@link TileEntity}；</li>
 * <li>{@code event.setCancellationResult(...)} 1.7.10 无对应 → 只 {@code setCanceled(true)}
 * （1.7.10 取消 {@code PlayerInteractEvent} 会把 {@code useBlock/useItem} 置 DENY，等价于「不执行原版交互」）。
 * 上游在成功与失败路径都取消事件，因此语义一致。</li>
 * </ul>
 */
public class AmmoStorageInteractionEvent {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new AmmoStorageInteractionEvent());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRightClickBlock(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EntityPlayer player = event.entityPlayer;
        if (!player.isSneaking()) {
            return;
        }
        ItemStack held = player.getHeldItem();
        if (held == null || held.getItem() == null || !(held.getItem() instanceof IAmmoBox ammoBox)) {
            return;
        }
        World world = event.world;
        int x = event.x;
        int y = event.y;
        int z = event.z;
        if (!(world.getBlock(x, y, z) instanceof AmmoStorageBlock)) {
            return;
        }
        if (world.isRemote) {
            event.setCanceled(true);
            return;
        }
        TileEntity blockEntity = world.getTileEntity(x, y, z);
        if (!(blockEntity instanceof AmmoStorageBlockEntity storage)) {
            return;
        }
        // 1.7.10 形态的 util 签名应为
        // unloadAmmoBox(AmmoStorageBlockEntity, ItemStack, IAmmoBox, EntityPlayer)；返回值 1.7.10 不需要
        // （上游 InteractionResult 只用于 setCancellationResult）。
        AmmoStorageInteractionHelper.unloadAmmoBox(storage, held, ammoBox, player);
        event.setCanceled(true);
    }
}
