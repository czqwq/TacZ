package com.tacz.guns.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.block.entity.AmmoStorageBlockEntity;

/**
 * 弹药仓储方块（{@code AmmoStorageBlock}）的「弹匣盒倒进仓储」交互结算。
 *
 * <p>
 * <b>本类是上游的唯一实现</b>：上游 {@code block/AmmoStorageBlock} 与 {@code event/AmmoStorageInteractionEvent}
 * 都调用它。1.7.10 侧 {@code event/AmmoStorageInteractionEvent} 已委托本类，
 * {@code block/AmmoStorageBlock} 仍留有一份私有副本（会产生音效/判空漂移），应改为调用本类。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li><b>返回值类型</b>：上游返回 {@code net.minecraft.world.InteractionResult}，
 * 1.7.10 <b>没有</b>该类（方块交互是 {@code boolean onBlockActivated}）。
 * 因此此处改为 {@code boolean}：{@code InteractionResult.CONSUME} → {@code true}。
 * 上游此方法的<b>所有</b>分支都返回 {@code CONSUME}，所以映射无信息损失。</li>
 * <li><b>客户端提示</b>：上游用 {@code player.displayClientMessage(Component, true)}（动作栏）。
 * 1.7.10 既没有 {@code Component} 也没有动作栏，降级为
 * {@link ChatComponentTranslation} + {@link EntityPlayer#addChatMessage}（聊天栏），
 * 文案 key {@code message.tacz.ammo_storage.unloaded} 与参数顺序保持不变。
 * {@code ammoStack.getHoverName()} → {@code ammoStack.getDisplayName()}（返回 String）。</li>
 * <li><b>音效</b>：上游 {@code level.playSound(null, blockPos, SoundEvents.BUNDLE_REMOVE_ONE, PLAYERS, ...)}；
 * 1.7.10 无 {@code SoundEvent} 注册对象，改用原版字符串音效 {@code "random.pop"}
 * （与 {@code ClientMessageAmmoStorageInteract} 里 1.7.10 侧的选择一致），
 * 音量/音调保持 0.8F / 1.0F。</li>
 * <li>{@code ItemStack.EMPTY} → {@code null}，故对 {@link AmmoStorageHelper#createAmmoStack}
 * 的返回值做了判空（上游不需要）。</li>
 * <li>NBT/ID 逻辑（{@code isAllTypeCreative / isCreative / getAmmoId / getAmmoCount /
 *       setAmmoCount / setAmmoId}）语义完全不变。</li>
 * </ul>
 */
public final class AmmoStorageInteractionHelper {

    private AmmoStorageInteractionHelper() {}

    public static boolean unloadAmmoBox(AmmoStorageBlockEntity storage, ItemStack boxStack, IAmmoBox ammoBox,
        EntityPlayer player) {
        if (ammoBox.isAllTypeCreative(boxStack) || ammoBox.isCreative(boxStack)) {
            return true;
        }
        ResourceLocation ammoId = ammoBox.getAmmoId(boxStack);
        int count = ammoBox.getAmmoCount(boxStack);
        if (ammoId == null || ammoId.equals(DefaultAssets.EMPTY_AMMO_ID) || count <= 0) {
            return true;
        }
        ItemStack ammoStack = AmmoStorageHelper.createAmmoStack(ammoId, count);
        int inserted = ammoStack == null ? 0 : storage.insertAmmo(ammoStack, false);
        if (inserted <= 0) {
            return true;
        }
        int remaining = count - inserted;
        ammoBox.setAmmoCount(boxStack, remaining);
        if (remaining <= 0) {
            ammoBox.setAmmoId(boxStack, DefaultAssets.EMPTY_AMMO_ID);
        }
        if (player != null) {
            player.addChatMessage(
                new ChatComponentTranslation(
                    "message.tacz.ammo_storage.unloaded",
                    ammoStack == null ? "" : ammoStack.getDisplayName(),
                    inserted));
            player.worldObj.playSoundAtEntity(player, "random.pop", 0.8F, 1.0F);
        }
        return true;
    }
}
