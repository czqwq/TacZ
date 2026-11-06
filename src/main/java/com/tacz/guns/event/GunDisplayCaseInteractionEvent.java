package com.tacz.guns.event;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.GunDisplayCaseSlotHelper;
import com.tacz.guns.block.entity.GunDisplayCaseBlockEntity;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 枪械展示柜（{@link GunDisplayCaseBlock}）的交互：
 * <ul>
 * <li>左键：从展示柜格子里取出枪（创造模式不丢弃，非创造模式掉落）；</li>
 * <li>右键：潜行时切换上锁状态（上游要求「副手拿枪」才允许切换）——<b>1.7.10 无双持，未移植</b>，
 * 见下方 TODO。</li>
 * </ul>
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/GunDisplayCaseInteractionEvent.java}
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>{@code @Mod.EventBusSubscriber} → {@link #register()}；{@code @SubscribeEvent} 换包；</li>
 * <li>1.7.10 只有单一 {@link PlayerInteractEvent}：{@code RightClickBlock}/{@code LeftClickBlock} 子类 →
 * {@code event.action == Action.RIGHT_CLICK_BLOCK / LEFT_CLICK_BLOCK}；</li>
 * <li>1.7.10 无 {@code LeftClickBlock.Action.START/STOP}：{@code LEFT_CLICK_BLOCK} 只触发一次，
 * 等价上游的 START 分支，「STOP 不处理」天然成立；</li>
 * <li>{@code BlockState} → {@code World#getBlock(x,y,z)} + {@code metadata}（由 block 包自行解释）；
 * {@code BlockPos} → {@code x/y/z}；{@code BlockEntity} → {@link TileEntity}；</li>
 * <li>{@code entity.pick(6.0D, 0.0F, false)}（1.13+ 的射线）→ 1.7.10 手动
 * {@code Vec3} + {@code World#rayTraceBlocks(Vec3, Vec3)}，见 {@link #pickBlock}；</li>
 * <li>{@code ItemStack.isEmpty()} → {@code stack == null || stack.getItem() == null}；</li>
 * <li>{@code SoundEvents.ITEM_FRAME_REMOVE_ITEM} + {@code SoundSource.BLOCKS} → 1.7.10 无 SoundEvent 注册表、
 * 无 SoundSource → {@code World#playSoundEffect(x,y,z,"random.pop",1,1)}（物品被取走的原版音效）；</li>
 * <li>上游 {@code InteractionResult.sidedSuccess(...)} + {@code setCancellationResult} → 1.7.10 无该机制，
 * 只 {@code setCanceled(true)}（取消即 useBlock/useItem = DENY）。</li>
 * </ul>
 * <p>
 * TODO(port): 上游 GunDisplayCaseInteractionEvent.java:25-45 的「右键切换上锁」完全依赖副手拿枪
 * （{@code player.getOffhandItem()}）。1.7.10 无双持，也没有与「放置/旋转展示柜里的枪」不冲突的替代触发方式，
 * 因此本分支降级为<b>不处理</b>；{@code GunDisplayCaseBlock#toggleLockFromInteraction} /
 * {@code GunDisplayCaseBlock#isLocked} 的 1.7.10 形态仍保留给 block 层（例如潜行空手右键，由 block 层自行决定）。
 */
public class GunDisplayCaseInteractionEvent {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new GunDisplayCaseInteractionEvent());
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
        if (!(world.getBlock(x, y, z) instanceof GunDisplayCaseBlock displayCaseBlock)) {
            return;
        }
        // 依赖 block 层：上游是 state.getValue(GunDisplayCaseBlock.LOCKED)（BlockState 布尔属性），
        // 1.7.10 用 metadata 表达，由 block 类提供读取入口。
        if (GunDisplayCaseBlock.isLocked(world.getBlockMetadata(x, y, z))) {
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
        // 依赖 block 层：上游 GunDisplayCaseSlotHelper.getSlot(BlockState, BlockPos, Vec3)，
        // 1.7.10 形态 (World, int x, int y, int z, Vec3 hitLocation)。
        int slot = GunDisplayCaseSlotHelper.getSlot(world.getBlockMetadata(x, y, z), x, y, z, picked.hitVec);
        if (slot < 0) {
            return;
        }
        TileEntity blockEntity = world.getTileEntity(x, y, z);
        if (!(blockEntity instanceof GunDisplayCaseBlockEntity displayCase)) {
            return;
        }
        ItemStack stored = displayCase.getItem(slot);
        if (stored == null || stored.getItem() == null) {
            return;
        }
        event.setCanceled(true);
        boolean shouldDrop = !player.capabilities.isCreativeMode || player.isSneaking();
        if (!world.isRemote && (shouldDrop ? displayCase.dropItem(slot) : displayCase.removeItem(slot))) {
            world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.pop", 1.0F, 1.0F);
        }
    }

    /**
     * 1.7.10 版的 {@code Entity#pick(double, float, boolean)}：从玩家眼睛沿视线做 6 格方块射线。
     * 上游传入 {@code false}（忽略流体），{@link World#rayTraceBlocks(Vec3, Vec3)} 默认即忽略流体。
     */
    private static MovingObjectPosition pickBlock(EntityPlayer player, double distance) {
        Vec3 start = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLookVec();
        Vec3 end = start.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
        return player.worldObj.rayTraceBlocks(start, end);
    }
}
