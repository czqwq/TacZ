package com.tacz.guns.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.util.time.GunTime;

/**
 * 搬运自上游 1.20.1 {@code mixin/client/AbstractButtonMixin.java}。
 *
 * <p>
 * <b>上游用途</b>：上游 {@code AbstractButtonMixin.java:19-22} 在
 * {@code AbstractButton#onClick(DD)V} 的 HEAD 注入，记录「玩家点击了 GUI 按钮」的时间戳
 * {@code LocalPlayerDataHolder.clientClickButtonTimestamp}，供客户端射击逻辑判断「是不是刚点完按钮」，
 * 避免关闭 GUI 的那一次点击被当成开火。
 *
 * <p>
 * <b>1.7.10 等价改写</b>：
 * <ul>
 * <li>{@code net.minecraft.client.gui.components.AbstractButton}（1.19.4+ 才有）→
 * {@link GuiButton}（1.7.10 唯一的按钮基类，位于
 * {@code build/rfg/minecraft-src/java/net/minecraft/client/gui/GuiButton.java}）。</li>
 * <li>上游的点击回调 {@code onClick(DD)V} 在 1.7.10 <b>不存在</b>：1.7.10 的按钮没有回调，由
 * {@code GuiScreen#mouseClicked} 轮询 {@code buttonList} 并逐个调用
 * {@code GuiButton#mousePressed(Minecraft,int,int)Z}（GuiButton.java:125-128）来判断命中。
 * 因此注入点改为 {@code mousePressed} 的 RETURN。</li>
 * <li>1.7.10 的 {@code mousePressed} 对 <b>列表里的每个按钮</b> 都会被调用一次，
 * 所以必须看返回值：只有真正命中（返回 {@code true}）才记录时间戳，
 * 否则「点在按钮外面」也会被当成点了按钮，语义会比上游过于宽松。</li>
 * </ul>
 *
 * <p>
 * <b>生效状态</b>：完全等价，无降级。
 */
@Mixin(GuiButton.class)
public class AbstractButtonMixin {

    /**
     * 记录点击按钮的时间，后续方便给予射击冷却，防止点击按钮后误触开火。
     *
     * <p>
     * <b>形参只声明真正需要的部分</b>：Mixin 允许回调只声明目标方法的「前 N 个形参 + CallbackInfo」，
     * 所以这里不写 {@code (Minecraft, int, int)} —— 两个鼠标坐标用不到。
     * 这也是规避「形参类型与目标描述符不完全一致 → apply 失败 → 启动崩」的零风险写法：
     * Mixin 对回调签名<b>要求精确匹配</b>，父类 / 接口 / 宽化都不接受。
     *
     * @param cir 返回「是否命中该按钮」
     */
    @Inject(method = "mousePressed", at = @At("RETURN"))
    public void onClickHead(Minecraft mc, int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            LocalPlayerDataHolder.clientClickButtonTimestamp = GunTime.nowMillis();
        }
    }
}
