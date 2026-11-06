package com.tacz.guns.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.GunInputHandler;
import com.tacz.guns.client.input.InteractKey;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 手持枪械时禁止「使用/交互」原版方块与物品（避免右键吃掉物品、避免客户端挥手粒子）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/ClientPreventGunClick.java}
 * （{@code @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)}，
 * 监听 {@code InputEvent.InteractionKeyMappingTriggered}）。
 *
 * <p>
 * 平台替换 / 降级（相对上游）：
 * <ul>
 * <li><b>1.7.10 没有 {@code InputEvent.InteractionKeyMappingTriggered}</b>（那是 1.19+ 的「按键→动作映射」
 * 事件）。1.7.10 的等价拦截点是 {@link PlayerInteractEvent}：{@code Minecraft#func_147121_ag()}
 * （右键处理）在调用 {@code PlayerControllerMP#onPlayerRightClick} 与 {@code sendUseItem} 之前
 * 会依次 post {@code RIGHT_CLICK_BLOCK} / {@code RIGHT_CLICK_AIR} 的 PlayerInteractEvent
 * （{@code Minecraft.java:1528} 与 {@code :1556}），取消它既能阻止物品使用，也能阻止
 * {@code thePlayer.swingItem()}（={@code :1532}，即上游 {@code setSwingHand(false)} 想抑制的客户端挥手/粒子）。</li>
 * <li>{@code InteractKey.INTERACT_KEY.isDown()}（1.20 {@code KeyMapping#isDown}）→
 * {@code GunInputHandler.isKeyDown(InteractKey.INTERACT_KEY)}（1.7.10 的键位状态来自
 * {@code GameSettings#isKeyDown}，由 client/input 统一轮询）。</li>
 * <li>{@code player.getItemInHand(InteractionHand.MAIN_HAND)} → {@code player.getHeldItem()}
 * （1.7.10 无双持）；{@code HitResult/EntityHitResult} → {@link MovingObjectPosition} +
 * {@code MovingObjectType.ENTITY}；{@code ItemFrame} → {@link EntityItemFrame}。</li>
 * <li>只处理 {@code RIGHT_CLICK_BLOCK}/{@code RIGHT_CLICK_AIR}：上游事件也只覆盖「使用」键；
 * 1.7.10 的 PlayerInteractEvent 还会带 {@code LEFT_CLICK_*}（攻击），不在上游语义内，直接放行。</li>
 * <li><b>右键实体分支不经过本事件</b>：1.7.10 的 {@code Minecraft#func_147121_ag} 对 ENTITY 命中直接调
 * {@code PlayerControllerMP#interactWithEntitySendPacket}（{@code Minecraft.java:1512-1517}），
 * 不投递 PlayerInteractEvent。该分支由 {@code mixin/client/MinecraftAttackMixin} 的
 * {@code func_147121_ag} HEAD 注入补齐（同样的「交互键放行 + 展示框放行」判据）。</li>
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
public class ClientPreventGunClick {

    @SubscribeEvent
    public void onClickInput(PlayerInteractEvent event) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null || event.entityPlayer != player || !player.worldObj.isRemote) {
            return;
        }
        // 上游 InputEvent.InteractionKeyMappingTriggered 只在使用/攻击键被映射时触发；1.7.10 这里只认「使用」
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
            && event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR) {
            return;
        }
        // 当交互键按下时，允许交互
        if (GunInputHandler.isKeyDown(InteractKey.INTERACT_KEY)) {
            return;
        }
        // 只要主手有枪，那么禁止交互
        ItemStack itemInHand = player.getHeldItem();
        if (itemInHand != null && itemInHand.getItem() instanceof IGun) {
            // 展示框可以交互
            MovingObjectPosition hitResult = Minecraft.getMinecraft().objectMouseOver;
            if (hitResult != null && hitResult.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && hitResult.entityHit instanceof EntityItemFrame) {
                return;
            }
            // 上游：event.setSwingHand(false) 阻止客户端粒子 + event.setCanceled(true)。
            // 1.7.10：取消 PlayerInteractEvent 同时阻止 onPlayerRightClick/sendUseItem 与 swingItem()，
            // 语义等价（见类注释）。
            event.setCanceled(true);
        }
    }
}
