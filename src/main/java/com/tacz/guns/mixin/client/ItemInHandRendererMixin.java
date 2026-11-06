package com.tacz.guns.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.util.time.GunTime;

/**
 * 搬运自上游 1.20.1 {@code mixin/client/ItemInHandRendererMixin.java}。
 *
 * <p>
 * <b>上游用途</b>：
 * <ol>
 * <li>在 {@code ItemInHandRenderer#renderHandsWithItems} 的 HEAD 抛出 {@link BeforeRenderHandEvent}，
 * 让相机动画在渲染手部之前改矩阵栈；</li>
 * <li>{@code implements KeepingItemRenderer}，提供一个「切枪后仍把旧物品多渲染若干毫秒」的覆盖层，
 * 供 {@code KeepingItemRenderer#getRenderer()} 从 {@code Minecraft#getItemInHandRenderer()} 强转使用；</li>
 * <li>{@code tick()} 里原本有一段「阻止收枪进度」的逻辑，<b>上游已整段注释掉</b>，等价于空方法。</li>
 * </ol>
 *
 * <p>
 * <b>1.7.10 等价改写</b>（对照 {@code build/rfg/minecraft-src/java/net/minecraft/client/renderer/ItemRenderer.java}）：
 * <table border="1">
 * <tr>
 * <th>上游</th>
 * <th>1.7.10</th>
 * </tr>
 * <tr>
 * <td>{@code net.minecraft.client.renderer.ItemInHandRenderer}</td>
 * <td>{@link ItemRenderer}（第一人称手持渲染器，经 {@code Minecraft#entityRenderer#itemRenderer} 持有，
 * 正是 {@code KeepingItemRenderer#getRenderer()} 强转的对象）</td>
 * </tr>
 * <tr>
 * <td>{@code renderHandsWithItems(F, PoseStack, MultiBufferSource$BufferSource, LocalPlayer, I)</td>
 * <td>{@code renderItemInFirstPerson(F)V}（第 264 行）—— 1.7.10 没有 buffer source / 光照参数，
 * 手部渲染只有一个 partialTicks 参数</td>
 * </tr>
 * <tr>
 * <td>{@code tick()}</td>
 * <td>{@code updateEquippedItem()}（第 720 行）—— 每 tick 推进收/持枪进度</td>
 * </tr>
 * <tr>
 * <td>{@code mainHandHeight / oMainHandHeight}</td>
 * <td>{@code equippedProgress / prevEquippedProgress}（第 49-50 行）</td>
 * </tr>
 * <tr>
 * <td>{@code mainHandItem}</td>
 * <td>{@code itemToRender}（第 47 行）</td>
 * </tr>
 * <tr>
 * <td>{@code Minecraft.getInstance().player}</td>
 * <td>{@code Minecraft.getMinecraft().thePlayer}</td>
 * </tr>
 * </table>
 *
 * <p>
 * <b>PoseStack 的处理（1.7.10 差异）</b>：1.7.10 没有矩阵栈参数，
 * 因此这里每帧新建一个 {@link PoseStack}（初始为单位矩阵）投递给事件；事件返回后再
 * {@link PoseStack#applyToModelView()}（内部是 {@code GL11.glMultMatrix}，单位矩阵等于空操作）。
 * 这样订阅者只要在栈上 push/translate，就会真的叠到固定管线的 MODELVIEW 上；
 * 不 push 也不会破坏原版状态。
 *
 * <p>
 * <b>生效状态</b>：① / ② 完全可用；③ 上游本身就是空方法，这里保留为带注释的空注入点，语义一致。
 */
@Mixin(ItemRenderer.class)
public class ItemInHandRendererMixin implements KeepingItemRenderer {

    /**
     * 上游 {@code mainHandItem}。
     *
     * <p>
     * 上游还有两个 {@code @Shadow}：{@code mainHandHeight} → {@code ItemRenderer#equippedProgress}
     * （{@code ItemRenderer.java:49}）与 {@code oMainHandHeight} → {@code ItemRenderer#prevEquippedProgress}
     * （第 50 行）。因为上游仅在被注释掉的 {@code tick()} 里用到它们，这里不再声明不必要的
     * {@code @Shadow}（少一个运行期校验失败点）。将来若恢复那段逻辑，补上：
     * 
     * <pre>
     * 
     * {
     *     &#64;code
     *     &#64;Shadow
     *     private float equippedProgress;
     *     &#64;Shadow
     *     private float prevEquippedProgress;
     * }
     * </pre>
     */
    @Shadow
    private ItemStack itemToRender;

    @Unique
    private ItemStack tacz$KeepItem;
    @Unique
    private long tacz$KeepTimeMs;
    @Unique
    private long tacz$KeepTimestamp;
    @Unique
    private long tacz$KeepEpoch = -1;

    @Inject(method = "renderItemInFirstPerson", at = @At("HEAD"))
    public void beforeHandRender(float partialTicks, CallbackInfo ci) {
        PoseStack poseStack = new PoseStack();
        MinecraftForge.EVENT_BUS.post(new BeforeRenderHandEvent(poseStack));
        // 单位矩阵时是 no-op；订阅者压栈后这里才真正生效（见类 javadoc 的 PoseStack 处理）
        poseStack.applyToModelView();
    }

    /**
     * 1.7.10 对应上游 {@code ItemInHandRenderer#tick} 的注入。
     *
     * <p>
     * 上游 {@code ItemInHandRendererMixin.java:48-66} 的方法体<b>整段被注释掉</b>（保留了「若是枪则把
     * {@code mainHandHeight} 顶到 1.0」的废弃实现），因此这里也保持空实现 ——
     * 保真优先：不引入上游没有打开的行为。
     */
    @Inject(method = "updateEquippedItem", at = @At("HEAD"))
    public void cancelEquippedProgress(CallbackInfo ci) {
        // 上游此方法体整段注释掉，等价于空方法；字段映射见类 javadoc
        // （mainHandHeight → equippedProgress，oMainHandHeight → prevEquippedProgress，
        // mainHandItem → itemToRender）。
    }

    @Override
    public void keep(ItemStack itemStack, long timeMs) {
        if (this.tacz$KeepItem != null && this.tacz$KeepEpoch == GunTime.epoch()
            && GunTime.isWithin(this.tacz$KeepTimestamp, this.tacz$KeepTimeMs)) {
            return;
        }
        this.tacz$KeepTimeMs = timeMs;
        this.tacz$KeepTimestamp = GunTime.nowMillis();
        this.tacz$KeepEpoch = GunTime.epoch();
        this.tacz$KeepItem = itemStack;
        this.itemToRender = itemStack;
    }

    @Override
    public ItemStack getCurrentItem() {
        if (Minecraft.getMinecraft().thePlayer == null) {
            return this.itemToRender;
        }
        if (this.tacz$KeepItem != null) {
            if (this.tacz$KeepEpoch == GunTime.epoch()
                && GunTime.isWithin(this.tacz$KeepTimestamp, this.tacz$KeepTimeMs)) {
                return this.tacz$KeepItem;
            } else {
                this.tacz$KeepItem = null;
            }
        }
        return this.itemToRender;
    }
}
