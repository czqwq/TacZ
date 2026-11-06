package com.tacz.guns.client.gameplay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunDrawEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessagePlayerDrawGun;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.relauncher.Side;

/**
 * 客户端切枪（draw）逻辑。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/gameplay/LocalPlayerDraw.java}。
 *
 * <p>
 * 平台替换（相对上游）：
 * <ul>
 * <li>{@code LocalPlayer} → {@link EntityPlayerSP}、{@code getMainHandItem()} → {@code getHeldItem()}；</li>
 * <li>{@code LogicalSide.CLIENT} → {@link Side#CLIENT}
 * （1.7.10 的 api 事件 {@code GunDrawEvent} 用 cpw 的 {@code Side}，见 api 跨包契约）；</li>
 * <li>{@code IClientItemExtensions#getCustomRenderer()} → {@code MinecraftForgeClient.getItemRenderer(stack,
 * ItemRenderType.EQUIPPED_FIRST_PERSON)}
 * （1.7.10 的物品渲染器注册点是 {@code MinecraftForgeClient.registerItemRenderer} + {@code IItemRenderer}，
 * 没有 1.20 的 {@code IClientItemExtensions}）；</li>
 * <li>{@code Minecraft#gameMode.ensureHasSentCarriedItem()} → 直接发 {@code C09PacketHeldItemChange}：
 * 1.7.10 的等价方法是 {@code PlayerControllerMP#syncCurrentPlayItem()}，但它是 <b>private</b>，
 * 而该方法体本身就是「选中槽位变化时发 C09」（{@code PlayerControllerMP.java:345-353}），
 * 因此这里复刻同样的报文。</li>
 * </ul>
 * 无其它降级点。
 */
public class LocalPlayerDraw {

    private final LocalPlayerDataHolder data;
    private final EntityPlayerSP player;
    public boolean readyToDraw = false;

    public LocalPlayerDraw(LocalPlayerDataHolder data, EntityPlayerSP player) {
        this.data = data;
        this.player = player;
    }

    public void draw(ItemStack lastItem) {
        // 重置各种参数
        this.resetData();

        // 获取各种数据
        ItemStack currentItem = player.getHeldItem();
        long drawTime = GunTime.nowMillis() - data.clientDrawTimestamp;
        IGun currentGun = IGun.getIGunOrNull(currentItem);
        IGun lastGun = IGun.getIGunOrNull(lastItem);

        // 计算 draw 时长和 putAway 时长
        if (drawTime >= 0) {
            drawTime = getDrawTime(lastItem, lastGun, drawTime);
        }
        long putAwayTime = Math.abs(drawTime);

        // 发包通知服务器
        ensureHasSentCarriedItem();
        NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerDrawGun());
        MinecraftForge.EVENT_BUS.post(new GunDrawEvent(player, lastItem, currentItem, Side.CLIENT));

        // // 不处于收枪状态时才能收枪
        // if (drawTime >= 0) {
        // doPutAway(lastItem, putAwayTime);
        // }

        // 异步放映抬枪动画
        if (currentGun != null) {
            doDraw(currentItem, putAwayTime);
            // 刷新配件数据
            AttachmentPropertyManager.postChangeEvent(player, currentItem);
        }
    }

    /** 复刻 1.7.10 {@code PlayerControllerMP#syncCurrentPlayItem()}（private）。 */
    private void ensureHasSentCarriedItem() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() != null) {
            mc.getNetHandler()
                .addToSendQueue(new C09PacketHeldItemChange(player.inventory.currentItem));
        }
    }

    private static IItemRenderer getItemRenderer(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        return MinecraftForgeClient.getItemRenderer(stack, IItemRenderer.ItemRenderType.EQUIPPED_FIRST_PERSON);
    }

    private void doDraw(ItemStack currentItem, long putAwayTime) {
        TimelessAPI.getGunDisplay(currentItem)
            .ifPresent(display -> {
                // 取消预定中的 draw 行为
                if (data.drawFuture != null) {
                    data.drawFuture.cancel(false);
                }
                // 根据 put away time 预定 draw 行为（仅播放音效，状态机的初始化为了保证一致性已经移动）
                // data.drawFuture = LocalPlayerDataHolder.SCHEDULED_EXECUTOR_SERVICE.schedule(() -> {
                // Minecraft.getInstance().submitAsync(() -> {
                // SoundPlayManager.stopPlayGunSound();
                // SoundPlayManager.playDrawSound(player, display);
                // });
                // }, putAwayTime, TimeUnit.MILLISECONDS);
            });
    }

    private void doPutAway(ItemStack lastItem, long putAwayTime) {
        if (getItemRenderer(lastItem) instanceof AnimateGeoItemRenderer<?, ?>renderer) {
            renderer.tryExit(lastItem, putAwayTime);
            TimelessAPI.getGunDisplay(lastItem)
                .ifPresent(display -> {
                    SoundPlayManager.stopPlayGunSound();
                    SoundPlayManager.playPutAwaySound(player, display);
                });
        }
    }

    private long getDrawTime(ItemStack lastItem, IGun lastGun, long drawTime) {
        if (getItemRenderer(lastItem) instanceof AnimateGeoItemRenderer<?, ?>renderer) {
            long putAwayTime = renderer.getPutAwayTime(lastItem);
            if (drawTime > putAwayTime) {
                drawTime = putAwayTime;
            }
            data.clientDrawTimestamp = GunTime.nowMillis() + drawTime;
        } else {
            drawTime = 0;
            data.clientDrawTimestamp = GunTime.nowMillis();
        }
        return drawTime;
    }

    private void resetData() {
        data.autoReloadSuppressed = false;
        // 锁上状态锁
        data.lockState(operator -> operator.getSynDrawCoolDown() > 0);
        // 重置客户端的 shoot 时间戳
        data.isShootRecorded = true;
        data.clientShootTimestamp = -1;
        data.chargeProgress = 0;
        data.isCharging = false;
        // 重置客户端瞄准状态
        data.clientIsAiming = false;
        data.clientAimingProgress = 0;
        LocalPlayerDataHolder.oldAimingProgress = 0;
        // 重置拉栓状态
        data.isBolting = false;
        data.clientReloadStarting = false;
        // 更新切枪时间戳
        if (data.clientDrawTimestamp == -1) {
            data.clientDrawTimestamp = GunTime.nowMillis();
        }
    }
}
