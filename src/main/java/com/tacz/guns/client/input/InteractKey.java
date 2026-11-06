package com.tacz.guns.client.input;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;

import org.lwjgl.input.Keyboard;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.block.AmmoStorageBlock;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.client.event.GunDisplayCaseClientInteractionEvent;
import com.tacz.guns.config.util.InteractKeyConfigRead;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessageAmmoStorageInteract;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 交互键（O）：持枪时也能对特定方块/实体执行交互。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/input/InteractKey.java}。
 *
 * <p>
 * <b>1.7.10 映射</b>：
 * <ul>
 * <li>{@code KeyMapping → KeyBinding}（{@code GLFW_KEY_O → Keyboard.KEY_O}）；</li>
 * <li>{@code mc.hitResult}（{@code HitResult}）→ {@code mc.objectMouseOver}（{@link MovingObjectPosition}）；
 * {@code BlockHitResult}/{@code EntityHitResult} 的子类型判定 →
 * {@code typeOfHit == MovingObjectType.BLOCK/ENTITY}；{@code BlockPos} → 三个 int；</li>
 * <li>{@code player.level().getBlockState(pos)} → {@code player.worldObj.getBlock(x,y,z)} +
 * {@code getBlockMetadata(x,y,z)}（1.7.10 无 {@code BlockState}）；</li>
 * <li>{@code ClientMessageAmmoStorageInteract(BlockPos)} → {@code (int x, int y, int z)}；</li>
 * <li>{@code InteractKeyConfigRead#canInteractBlock(BlockState)} →
 * {@code canInteractBlock(Block, int metadata)}；</li>
 * <li>{@code mc.startUseItem()} → 1.7.10 没有公开的 {@code startUseItem/rightClickMouse}，
 * 等价的公开入口是 {@code PlayerControllerMP#onPlayerRightClick(...)}（方块）与
 * {@code interactWithEntitySendPacket(...)}（实体）——这两者正是 {@code Minecraft#rightClickMouse()}
 * 内部实现里对每种 {@code objectMouseOver} 分支所做的事（见
 * {@code Minecraft.java} 的 {@code objectMouseOver.typeOfHit} 分支）。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class InteractKey {

    public static final KeyBinding INTERACT_KEY = new KeyBinding(
        "key.tacz.interact.desc",
        Keyboard.KEY_O,
        "key.category.tacz");

    private InteractKey() {}

    public static boolean onInteractControllerPress(boolean isPress) {
        return isPress && onInteractPress();
    }

    public static boolean onInteractPress() {
        if (!isInGame()) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) {
            return false;
        }
        if (!IGun.mainHandHoldGun(player)) {
            return false;
        }
        MovingObjectPosition hitResult = mc.objectMouseOver;
        if (hitResult == null) {
            return false;
        }
        if (hitResult.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            interactBlock(hitResult, player, mc);
            return true;
        }
        if (hitResult.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && hitResult.entityHit != null) {
            interactEntity(hitResult, mc);
            return true;
        }
        return false;
    }

    private static void interactBlock(MovingObjectPosition blockHitResult, EntityPlayerSP player, Minecraft mc) {
        int x = blockHitResult.blockX;
        int y = blockHitResult.blockY;
        int z = blockHitResult.blockZ;
        Block block = player.worldObj.getBlock(x, y, z);
        int metadata = player.worldObj.getBlockMetadata(x, y, z);
        if (block instanceof GunDisplayCaseBlock) {
            GunDisplayCaseClientInteractionEvent.showHintForTarget(mc, true);
        }
        if (block instanceof AmmoStorageBlock) {
            NetworkHandler.CHANNEL.sendToServer(new ClientMessageAmmoStorageInteract(x, y, z));
            return;
        }
        if (InteractKeyConfigRead.canInteractBlock(block, metadata)) {
            ItemStack held = player.getHeldItem();
            mc.playerController.onPlayerRightClick(
                player,
                player.worldObj,
                held,
                x,
                y,
                z,
                blockHitResult.sideHit,
                blockHitResult.hitVec);
        }
    }

    private static void interactEntity(MovingObjectPosition entityHitResult, Minecraft mc) {
        if (InteractKeyConfigRead.canInteractEntity(entityHitResult.entityHit)) {
            mc.playerController.interactWithEntitySendPacket(mc.thePlayer, entityHitResult.entityHit);
        }
    }
}
