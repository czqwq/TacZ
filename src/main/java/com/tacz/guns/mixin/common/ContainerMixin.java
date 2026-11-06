package com.tacz.guns.mixin.common;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tacz.guns.item.AmmoBoxItem;

/**
 * 搬运自上游 1.20.1 {@code item/AmmoBoxItem#overrideStackedOnOther}（上游 {@code :102-180}）的 1.7.10 落点。
 *
 * <p>
 * 上游实现的是 {@code Item#overrideStackedOnOther(ItemStack, Slot, ClickAction, Player)}：1.20 的物品栏点击
 * 管线在 {@code ClickAction.SECONDARY}（右键）时会先问「手持物品是否要接管这次点击」。1.7.10 <b>没有</b>
 * 这个物品级钩子，物品栏点击全部在 {@link Container#slotClick(int, int, int, EntityPlayer)} 里完成，
 * 因此等价落点是 {@code slotClick} 的 HEAD：命中时取消原版逻辑。
 *
 * <p>
 * <b>为什么返回值必须是「修改后的手持栈」</b>：1.7.10 的点击是「客户端预测 + 服务端权威」两段式
 * （{@code PlayerControllerMP#windowClick:475-481} 先在本地跑一次 {@code slotClick}，把返回值当
 * {@code clickedItem} 发进 {@code C0EPacketClickWindow}；服务端 {@code NetHandlerPlayServer#processClickWindow:949}
 * 起拿自己的返回值与之一致才 confirm + {@code updateHeldItem()}）。本 mixin 在两侧都会执行，返回值统一取
 * {@code player.inventory.getItemStack()}（{@link AmmoBoxItem#tryStackedOnOther} 已就地改好手持盒的 NBT），
 * 与上游 {@code overrideStackedOnOther} 返回「新手持栈」的语义一致。
 *
 * <p>
 * 前置过滤（右键 / mode 0 / slotId 界内 / 槽位属于玩家背包 / 盒内弹种判定 / 音效）全部在
 * {@link AmmoBoxItem#tryStackedOnOther(EntityPlayer, int, int, int)} 内完成，本 mixin 只做「调用 → 取消 → 返回」。
 */
@Mixin(Container.class)
public class ContainerMixin {

    /** 注入 {@code Container#slotClick(int slotId, int clickedButton, int mode, EntityPlayer player)} 的 HEAD。 */
    @Inject(method = "slotClick", at = @At("HEAD"), cancellable = true)
    private void tacz$ammoBoxStackedOnOther(int slotId, int clickedButton, int mode, EntityPlayer player,
        CallbackInfoReturnable<ItemStack> cir) {
        if (AmmoBoxItem.tryStackedOnOther(player, slotId, clickedButton, mode)) {
            cir.setReturnValue(player == null ? null : player.inventory.getItemStack());
        }
    }
}
