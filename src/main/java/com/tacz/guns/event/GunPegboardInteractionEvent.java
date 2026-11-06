package com.tacz.guns.event;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.tacz.guns.block.GunPegboardBlock;
import com.tacz.guns.block.GunPegboardSlotHelper;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 枪械挂板（{@link GunPegboardBlock}）左键取枪：创造模式不丢弃，非创造模式掉落。
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/GunPegboardInteractionEvent.java}
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>{@code @Mod.EventBusSubscriber} → {@link #register()}；{@code @SubscribeEvent} 换包；</li>
 * <li>{@code PlayerInteractEvent.LeftClickBlock} → 单一 {@link PlayerInteractEvent} +
 * {@code event.action == Action.LEFT_CLICK_BLOCK}（1.7.10 无 START/STOP，天然等价 START 分支）；</li>
 * <li>{@code BlockState} → {@code World#getBlock(x,y,z)}；{@code BlockPos} → {@code x/y/z}；
 * {@code BlockEntity} → {@link TileEntity}；</li>
 * <li>{@code entity.pick(6.0D, 0.0F, false)} → {@link #pickBlock}（1.7.10 手动射线）；</li>
 * <li>{@code ItemStack.isEmpty()} → {@code stack == null || stack.getItem() == null}；</li>
 * <li>{@code SoundEvents.ITEM_FRAME_REMOVE_ITEM} + {@code SoundSource.BLOCKS} →
 * {@code World#playSoundEffect(...,"random.pop",...)}；{@code setCancellationResult} 丢弃，只 {@code setCanceled}。</li>
 * </ul>
 * <p>
 * TODO(port): 上游 GunPegboardInteractionEvent.java:24 的 {@code Items.DEBUG_STICK}（调试棒）判断在 1.7.10 无对应
 * 物品，已删除。
 */
public class GunPegboardInteractionEvent {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new GunPegboardInteractionEvent());
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
            return;
        }
        World world = event.world;
        int x = event.x;
        int y = event.y;
        int z = event.z;
        if (!(world.getBlock(x, y, z) instanceof GunPegboardBlock)) {
            return;
        }
        EntityPlayer player = event.entityPlayer;
        MovingObjectPosition picked = pickBlock(player, 6.0D);
        if (picked == null || picked.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }
        if (picked.blockX != x || picked.blockY != y || picked.blockZ != z) {
            return;
        }
        // 依赖 block 层：上游 GunPegboardSlotHelper.getSlot(BlockState, BlockPos, Vec3)，
        // 1.7.10 形态 (World, int x, int y, int z, Vec3 hitLocation)。
        int slot = GunPegboardSlotHelper.getSlot(world.getBlockMetadata(x, y, z), x, y, z, picked.hitVec);
        if (slot < 0) {
            return;
        }
        TileEntity blockEntity = world.getTileEntity(x, y, z);
        if (!(blockEntity instanceof GunPegboardBlockEntity pegboard)) {
            return;
        }
        ItemStack stored = pegboard.getItem(slot);
        if (stored == null || stored.getItem() == null) {
            return;
        }
        event.setCanceled(true);
        boolean shouldDrop = !player.capabilities.isCreativeMode || player.isSneaking();
        if (!world.isRemote && (shouldDrop ? pegboard.dropItem(slot) : pegboard.removeItem(slot))) {
            world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.pop", 1.0F, 1.0F);
        }
    }

    /**
     * 1.7.10 版的 {@code Entity#pick(double, float, boolean)}：从玩家眼睛沿视线做 6 格方块射线。
     */
    private static MovingObjectPosition pickBlock(EntityPlayer player, double distance) {
        Vec3 start = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLookVec();
        Vec3 end = start.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
        return player.worldObj.rayTraceBlocks(start, end);
    }
}
