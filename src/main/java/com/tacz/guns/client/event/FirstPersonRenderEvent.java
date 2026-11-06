package com.tacz.guns.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderHandEvent;

import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.compat.oculus.OculusCompat;

/**
 * 第一人称手持枪械渲染的入口（状态机生命周期）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/FirstPersonRenderEvent.java}
 * （{@code @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)}，但
 * {@code onRenderHand} 上的 {@code @SubscribeEvent} <b>在 1.20.1 源码里就是注释掉的</b>；
 * 1.7.10 这里同样保持「不注册」）。
 *
 * <p>
 * 平台替换 / 降级（相对上游）：
 * <ul>
 * <li><b>1.7.10 的 {@code RenderHandEvent} 不携带物品/PoseStack/光照</b>
 * （{@code net.minecraftforge.client.event.RenderHandEvent} 只有 {@code context}/{@code partialTicks}/
 * {@code renderPass}），因此上游 {@code event.getItemStack()} → 取
 * {@link KeepingItemRenderer#getRenderer()}{@code .getCurrentItem()}（1.7.10 里「当前正在渲染的手持物」，
 * 由 {@code ItemRenderer} 的 mixin 维护）；{@code event.getHand()} 的两分支 → 1.7.10 <b>无双持</b>，
 * 只保留 MAIN_HAND 语义；{@code event.getPoseStack()/getMultiBufferSource()/getPackedLight()} 无对应。</li>
 * <li><b>实际的第一人称模型绘制不在这里</b>：1.7.10 由 vendored {@code simplebedrockmodel} 的
 * {@code FirstPersonRenderHandler#onRenderHand(RenderHandEvent)} 完成（它会
 * {@code renderer.renderFirstPerson(...)} 并 cancel 事件，避免原版再画一遍手持物）。
 * 如果本类也执行 {@code renderFirstPerson}，会出现<b>双重绘制</b>。因此本类只保留上游的
 * 「状态机生命周期 + Oculus 批次收尾」职责，见 {@code // TODO(L2)} 注释。</li>
 * <li>{@code IClientItemExtensions#getCustomRenderer()} →
 * {@code MinecraftForgeClient#getItemRenderer(stack, EQUIPPED_FIRST_PERSON)} +
 * {@code instanceof AnimateGeoItemRenderer}；</li>
 * <li>{@code ItemDisplayContext.FIRST_PERSON_RIGHT_HAND/LEFT_HAND} → mcshim 的
 * {@code com.tacz.guns.mcshim.ItemDisplayContext}，右手分支保留；</li>
 * <li>{@code ItemStack.matches(a,b)} → 本类私有 {@link #matches(ItemStack, ItemStack)}；
 * {@code ItemStack.EMPTY} → {@code null}；</li>
 * <li>{@code Minecraft#renderBuffers().bufferSource()} →
 * {@link OculusCompat#getHandBufferSource()}（1.7.10 没有全局 BufferSource，
 * compat 层提供了手部渲染用的那个）。</li>
 * </ul>
 *
 * <p>
 * <b>1.7.10 特有（相对上游）</b>：本类<b>没有</b> {@code @SubscribeEvent} 处理方法（上游 {@code onRenderHand}
 * 的注解本来就是注释掉的），因此注册到总线也是空转；若日后恢复注册，该处理方法必须是<b>实例方法</b>
 * （FML {@code ASMEventHandler} 用 {@code INVOKEVIRTUAL} 回调，静态方法会
 * {@code IncompatibleClassChangeError}），故此处已写成 {@code public void}。
 */
public class FirstPersonRenderEvent {

    private static AnimationStateMachine<?> lastStateMachine = null;

    // @SubscribeEvent ← 上游同样未注册（见类注释）；1.7.10 的第一人称绘制入口是
    // simplebedrockmodel 的 FirstPersonRenderHandler
    public void onRenderHand(RenderHandEvent event) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        // 上游 OFF_HAND 分支：1.7.10 没有副手，整段不适用
        // 事件给的是被延长渲染修改过后的物品，不是玩家实际手持的
        ItemStack stack = KeepingItemRenderer.getRenderer()
            .getCurrentItem();
        if (stack == null) {
            return;
        }

        // 上游还有：transformType = event.getHand() == MAIN_HAND ? FIRST_PERSON_RIGHT_HAND
        // : FIRST_PERSON_LEFT_HAND —— 1.7.10 只有主手 pass，见下方 TODO(L2)

        // 渲染相关内容整理到物品的自定义渲染器上了，这个接口有待进一步抽象
        IItemRenderer renderer = MinecraftForgeClient
            .getItemRenderer(stack, IItemRenderer.ItemRenderType.EQUIPPED_FIRST_PERSON);
        if (renderer instanceof AnimateGeoItemRenderer<?, ?>geoRenderer) {
            // 如果旧的状态机已经不再使用且未正常退出，使其静默退出
            AnimationStateMachine<?> machine = geoRenderer.getStateMachine(stack);
            if (machine != lastStateMachine) {
                if (lastStateMachine != null && lastStateMachine.isInitialized()) {
                    lastStateMachine.exit();
                }
                lastStateMachine = machine;
            }
            // 物品处于后台时，阻止状态机初始化
            boolean flag = matches(player.getHeldItem(), stack);
            if (flag && geoRenderer.needReInit(stack)) {
                geoRenderer.tryInit(stack, player, event.partialTicks);
            }

            // 防止内存泄漏
            OculusCompat.endBatch(OculusCompat.getHandBufferSource());

            // TODO(L2): 上游此处以 transformType = FIRST_PERSON_RIGHT_HAND
            // （1.7.10 无双持，故固定右手）调用
            // renderer.renderFirstPerson(player, stack, transformType, event.getPoseStack(),
            // event.getMultiBufferSource(), event.getPackedLight(), partialTick);
            // 并 event.setCanceled(true)。1.7.10 的这段绘制由 vendored simplebedrockmodel 的
            // FirstPersonRenderHandler#onRenderHand(RenderHandEvent) 承担（同一个 RenderHandEvent，
            // 同一个 renderer.renderFirstPerson，并负责 cancel 原版手持物渲染），
            // 在这里再画一次会双重绘制，故只保留状态机生命周期与 Oculus 批次收尾。
        }
    }

    /**
     * 1.20 {@code ItemStack.matches(a, b)} 的 1.7.10 等价实现（count + item/metadata + NBT）。
     */
    private static boolean matches(ItemStack a, ItemStack b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.stackSize == b.stackSize && ItemStack.areItemStacksEqual(a, b)
            && ItemStack.areItemStackTagsEqual(a, b);
    }

}
