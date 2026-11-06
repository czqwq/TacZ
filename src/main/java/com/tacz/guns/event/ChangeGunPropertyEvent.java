package com.tacz.guns.event;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.ApiStatus;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.item.IGun;

/**
 * 配件属性缓存的求值入口。
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/ChangeGunPropertyEvent.java}
 * <p>
 * 本类上游<b>不是</b>事件监听器（没有 {@code @Mod.EventBusSubscriber}）：它由
 * {@code resource/modifier/AttachmentPropertyManager} 收到 {@link AttachmentPropertyEvent} 后直接调用。
 * 因此 1.7.10 形态同样是普通静态工具方法，<b>不</b>注册到事件总线（{@code EventHandlers} 里也不登记）。
 * <p>
 * 1.7.10 差异：{@code ResourceLocation} 换包（{@code net.minecraft.resources} → {@code net.minecraft.util}）；
 * {@code ItemStack.EMPTY} 不存在，判空用 {@code stack == null || stack.getItem() == null}；
 * {@code TimelessAPI.getCommonGunIndex} 仍按上游返回 {@code Optional<CommonGunIndex>}（common-resource 契约）。
 */
@ApiStatus.Internal
public class ChangeGunPropertyEvent {

    @ApiStatus.Internal
    public static void internalOnAttachmentPropertyEvent(AttachmentPropertyEvent event) {
        ItemStack gunItem = event.getGunItem();
        if (gunItem == null || gunItem.getItem() == null) {
            return;
        }
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            return;
        }
        ResourceLocation gunId = iGun.getGunId(gunItem);
        TimelessAPI.getCommonGunIndex(gunId)
            .ifPresent(
                gunIndex -> event.getCacheProperty()
                    .eval(gunItem, gunIndex.getGunData()));
    }
}
