package com.tacz.guns.client.event;

import java.util.HashMap;
import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.entity.sync.core.DataHolder;
import com.tacz.guns.entity.sync.core.SyncedEntityData;
import com.tacz.guns.util.DelayedTask;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 当玩家跨越维度时，客户端需要刷新一次玩家的配件属性缓存。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/RefreshClonePlayerDataEvent.java}
 * （{@code @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)}，
 * 监听 {@code ClientPlayerNetworkEvent.Clone}）。
 *
 * <p>
 * 平台替换 / 降级（相对上游）：
 * <ul>
 * <li><b>1.7.10 没有 {@code ClientPlayerNetworkEvent.Clone}</b>（客户端玩家实体在 respawn 包后被重建，
 * 但 FML/Forge 不提供事件）。因此改为在 {@code TickEvent.ClientTickEvent} 里做<b>引用变化检测</b>：
 * {@code Minecraft#thePlayer} 实例换了就说明客户端玩家实体被重建，等价于上游 Clone 的触发时机
 * （{@code NetHandlerPlayClient#handleRespawn} 会 new 一个 {@code EntityClientPlayerMP} 并赋给
 * {@code thePlayer}）。见 {@link #onClientTick(TickEvent.ClientTickEvent)}；
 * 逻辑体整体搬到 {@link #onClientPlayerClone(EntityPlayerSP, EntityPlayerSP)}，规则与上游一致。</li>
 * <li>{@code oldPlayer.reviveCaps()} / {@code invalidateCaps()} 删除：1.7.10 没有 Capability 系统，
 * 上游这两句只是为了在 Capability 可写期间搬运数据，1.7.10 的 {@code SyncedEntityData} 是自研静态管理器
 * （WeakHashMap），不需要唤醒/失效。</li>
 * <li>{@code LocalPlayer} → {@link EntityPlayerSP}；{@code event.getOldPlayer()/getNewPlayer()} →
 * 本方法的两个参数。</li>
 * <li>{@code TickEvent.ClientTickEvent} 与上游同名同语义，但 1.7.10 在
 * {@code FMLCommonHandler.instance().bus()} 上（{@code ClientProxy} 已按此注册）。</li>
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
public class RefreshClonePlayerDataEvent {

    /** 上一次 tick 看到的客户端玩家实体，用于检测「客户端玩家实体被重建」。 */
    private static EntityPlayerSP lastPlayer = null;

    /**
     * 客户端玩家实体重建时的数据搬运（= 上游 {@code ClientPlayerNetworkEvent.Clone} 的处理体）。
     */
    public static void onClientPlayerClone(EntityPlayerSP oldPlayer, EntityPlayerSP newPlayer) {
        // 服务端只在实体重新加入世界时全量同步 SyncedEntityData。如果只有客户端重建了玩家实体（例如换皮肤模组/插件
        // 发送的 respawn 包），服务端不会重新同步，新实体会退回默认值（-1 的切枪/近战冷却），导致无法开火。
        // 因此把旧实体的数据复制过来；死亡重生、跨维度时服务端随后仍会全量同步覆盖。
        DataHolder oldHolder = SyncedEntityData.instance()
            .getDataHolder(oldPlayer);
        DataHolder newHolder = SyncedEntityData.instance()
            .getDataHolder(newPlayer);
        if (oldHolder != null && newHolder != null) {
            newHolder.dataMap = new HashMap<>(oldHolder.dataMap);
        }
        // 基时间戳同理，只在服务端下发同步消息时更新，丢失会导致开火包的时间戳校验失败
        IClientPlayerGunOperator.fromLocalPlayer(newPlayer)
            .getDataHolder().clientBaseTimestamp = IClientPlayerGunOperator.fromLocalPlayer(oldPlayer)
                .getDataHolder().clientBaseTimestamp;
        // 但是这个事件触发时，玩家的背包并未同步，导致无法读取枪械数据进行配件属性缓存的刷新
        // 延迟 10 tick 执行缓存刷新就好了
        DelayedTask.add(
            () -> IGunOperator.fromLivingEntity(newPlayer)
                .initialData(),
            10);
    }

    /**
     * 延迟执行是通过这个方法执行的；同时承担「客户端玩家实体重建」的检测（移植特有）。
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            try {
                DelayedTask.SUPPLIERS.removeIf(BooleanSupplier::getAsBoolean);
            } catch (Exception e) {
                DelayedTask.SUPPLIERS.clear();
                GunMod.LOGGER.catching(e);
            }
            // 移植特有：1.7.10 无 ClientPlayerNetworkEvent.Clone，用 thePlayer 引用变化检测「客户端玩家实体重建」
            EntityPlayerSP currentPlayer = Minecraft.getMinecraft().thePlayer;
            if (currentPlayer != null && currentPlayer != lastPlayer) {
                if (lastPlayer != null) {
                    onClientPlayerClone(lastPlayer, currentPlayer);
                }
                lastPlayer = currentPlayer;
            } else if (currentPlayer == null) {
                lastPlayer = null;
            }
        }
    }
}
