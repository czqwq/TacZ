package com.tacz.guns.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.mcshim.EntityPose;
import com.tacz.guns.mcshim.EntityStateHelper;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 驱动枪械动画状态机的「移动状态输入」（run/walk/idle），以及第三人称的每帧视觉更新。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/TickAnimationEvent.java}
 * （{@code @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)}）。
 *
 * <p>
 * 平台替换（相对上游）：
 * <ul>
 * <li>{@code LocalPlayer} → {@link EntityPlayerSP}，{@code Minecraft#player} → {@code thePlayer}，
 * {@code getMainHandItem()} → {@code getHeldItem()}；</li>
 * <li>{@code player.input}（{@code Input}）→ {@code player.movementInput}（{@code MovementInput}，
 * 1.7.10 的等价输入容器；同样可能为 null，故保留上游的空判定分支）；</li>
 * <li>{@code player.input.getMoveVector().length() > 0.01} →
 * {@code Math.hypot(moveStrafe, moveForward) > 0.01}（1.7.10 的 {@code MovementInput} 直接给出
 * 模拟量 {@code moveStrafe/moveForward}，与 1.20 {@code Input#getMoveVector} 同量纲）；</li>
 * <li>{@code player.isMovingSlowly()} → {@code player.isSneaking()} 或「趴下姿势」
 * （1.20 {@code LocalPlayer#isMovingSlowly()} = {@code isCrouching() || isVisuallyCrawling()}；
 * 1.7.10 的「趴下」由 {@code EntityStateHelper} 的强制姿势表达，见 {@code LocalPlayerCrawl}）；</li>
 * <li>{@code IClientItemExtensions.of(...).getCustomRenderer()} →
 * {@code MinecraftForgeClient#getItemRenderer(stack, EQUIPPED_FIRST_PERSON)} +
 * {@code instanceof AnimateGeoItemRenderer}；</li>
 * <li>{@code Minecraft#options.getCameraType().isFirstPerson()} → {@code gameSettings.thirdPersonView == 0}；</li>
 * <li>{@code TickEvent.ClientTickEvent}/{@code RenderTickEvent} 与上游同名同语义，1.7.10 注册在
 * {@code FMLCommonHandler.instance().bus()}。</li>
 * </ul>
 *
 * <p>
 * <b>1.7.10 特有（相对上游）</b>：{@code @SubscribeEvent} 的<b>处理方法必须是实例方法</b> —— FML 的
 * {@code ASMEventHandler}（{@code cpw/mods/fml/common/eventhandler/ASMEventHandler.java:115}）用
 * {@code INVOKEVIRTUAL} 回调目标方法，静态方法会在事件派发时抛 {@code IncompatibleClassChangeError}。
 * 因此上游的 {@code public static void 处理方法(...)} 在这里改为 {@code public void 处理方法(...)}
 * （类名 / 方法名 / 参数 / 语义一律不变；非处理方法的静态工具方法与字段仍保持 {@code static}）。
 * 本类也因为「{@code EventBus#register(Class)} 在 1.7.10 无效」而提供 public 无参构造，
 * 由 {@code client/ClientProxy} 反射 {@code new} 后注册到正确的总线。
 */
public class TickAnimationEvent {

    @SubscribeEvent
    public void tickAnimation(TickEvent.ClientTickEvent event) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        ItemStack mainHandItem = player.getHeldItem();
        TimelessAPI.getGunDisplay(mainHandItem)
            .ifPresent(gunIndex -> {
                var animationStateMachine = gunIndex.getAnimationStateMachine();
                if (animationStateMachine == null) {
                    return;
                }
                // 群组服切世界导致的特殊 BUG 处理，正常情况不会遇到此问题
                if (player.movementInput == null) {
                    animationStateMachine.trigger(GunAnimationConstant.INPUT_IDLE);
                    return;
                }
                // 上游 player.isMovingSlowly() = isCrouching() || isVisuallyCrawling()
                // （1.20 LocalPlayer.java:622-624）；1.7.10 的「趴下」由 EntityStateHelper 的强制姿势表达，
                // isVisuallyCrawling = 姿势 SWIMMING 且不在水里（同 Entity#isVisuallyCrawling）。
                boolean movingSlowly = player.isSneaking()
                    || (!player.isInWater() && EntityStateHelper.getPose(player) == EntityPose.SWIMMING);
                if (!movingSlowly && player.isSprinting()) {
                    // 如果玩家正在移动，播放移动动画，否则播放 idle 动画
                    animationStateMachine.trigger(GunAnimationConstant.INPUT_RUN);
                } else if (!movingSlowly
                    && Math.hypot(player.movementInput.moveStrafe, player.movementInput.moveForward) > 0.01) {
                        animationStateMachine.trigger(GunAnimationConstant.INPUT_WALK);
                    } else {
                        animationStateMachine.trigger(GunAnimationConstant.INPUT_IDLE);
                    }
            });
    }

    @SubscribeEvent
    public void tickAnimation(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            return;
        }
        if (Minecraft.getMinecraft().gameSettings.thirdPersonView == 0) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        ItemStack mainHandItem = player.getHeldItem();
        // 渲染相关内容整理到物品的自定义渲染器上了，这个接口有待进一步抽象
        IItemRenderer renderer = getItemRenderer(mainHandItem);
        if (renderer instanceof AnimateGeoItemRenderer<?, ?>geoRenderer) {
            // 如果物品不一样了，先尝试初始化状态机
            if (geoRenderer.needReInit(mainHandItem)) {
                geoRenderer.tryInit(mainHandItem, player, event.renderTickTime);
            }
            geoRenderer.visualUpdate(mainHandItem);
        }
    }

    /** 1.7.10 的「物品自定义渲染器」查询（替代 {@code IClientItemExtensions#getCustomRenderer()}）。 */
    private static IItemRenderer getItemRenderer(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        return MinecraftForgeClient.getItemRenderer(stack, IItemRenderer.ItemRenderType.EQUIPPED);
    }
}
