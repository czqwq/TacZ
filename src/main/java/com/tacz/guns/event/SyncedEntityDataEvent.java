package com.tacz.guns.event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import com.tacz.guns.entity.sync.core.DataEntry;
import com.tacz.guns.entity.sync.core.DataHolder;
import com.tacz.guns.entity.sync.core.SyncedDataKey;
import com.tacz.guns.entity.sync.core.SyncedEntityData;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageUpdateEntityData;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * {@code entity/sync} 数据（{@link SyncedEntityData}）的事件接线：
 * 追踪开始时同步一次、玩家进入世界时同步自身、玩家 clone 时搬运数据、服务端 tick 时下发脏数据。
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/SyncedEntityDataEvent.java}
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>{@code @Mod.EventBusSubscriber} → {@link #register()}，同时注册到
 * <b>Forge 总线</b>（{@link EntityJoinWorldEvent} / {@link PlayerEvent}）与
 * <b>FML 总线</b>（{@link TickEvent.ServerTickEvent}）；同一个实例可安全注册到两个总线；</li>
 * <li><b>没有 Capability 系统</b>：上游 {@code AttachCapabilitiesEvent} 挂
 * {@code DataHolderCapabilityProvider}；1.7.10 改为在 {@link EntityJoinWorldEvent} 时确保
 * {@link DataHolder} 存在（{@code SyncedEntityData#getDataHolder} 的 1.7.10 实现需按需创建/挂接）。
 * 玩家数据的 NBT 持久化同样由 entity/sync 层负责（本工程全部 key 均 {@code save=false}，故无需持久化，
 * 见类尾「原 TODO(port) 已关闭」）；</li>
 * <li>{@code AttachCapabilitiesEvent} 的 {@code event.addListener(provider::invalidate)}（capability 失效清理）
 * 在 1.7.10 无对应 → 删除；{@code original.reviveCaps()/invalidateCaps()} 同样删除；</li>
 * <li>{@code EntityJoinLevelEvent} → {@link EntityJoinWorldEvent}；{@code Level#isClientSide()} →
 * {@code World#isRemote}（{@code entity.worldObj}）；{@code entity.getId()} →
 * {@code entity.getEntityId()}；{@code Player} → {@link EntityPlayer}；{@code ServerPlayer} →
 * {@link EntityPlayerMP}；{@code LogicalSide} → {@link Side}；</li>
 * <li>{@code PlayerEvent.StartTracking.getEntity()/getTarget()} → 字段 {@code entityPlayer} / {@code target}；
 * {@code PlayerEvent.Clone.getOriginal()/getEntity()/isWasDeath()} →
 * 字段 {@code original} / {@code entityPlayer} / {@code wasDeath}；</li>
 * <li>{@code NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(...))} →
 * {@code NetworkHandler.sendToClientPlayer(msg, player)}；
 * {@code PacketDistributor.TRACKING_ENTITY.with(...)} →
 * {@code NetworkHandler.sendToTrackingEntity(msg, centerEntity)}。</li>
 * </ul>
 * <p>
 * <b>原 TODO(port) 已关闭（r2）</b>：上游 {@code SyncedEntityDataEvent.java:28-37} 用
 * {@code AttachCapabilitiesEvent} 挂 {@code DataHolderCapabilityProvider}，并给非 {@code ServerPlayer}
 * 注册 {@code invalidate} 监听。1.7.10 无 capability，这部分已由 {@code entity/sync} 层按当时建议的形态实现：
 * <ul>
 * <li>{@code SyncedEntityData} 用静态 {@code WeakHashMap<Entity, DataHolder>} 按需创建/自然回收
 * （{@code SyncedEntityData.java:66-75, 195-214}），登出由 WeakHashMap 自然回收；</li>
 * <li>服务端写 → {@code dirtyEntities} → 本类 {@code onServerTick} 按 {@code syncMode} 下发
 * {@code ServerMessageUpdateEntityData} → 客户端 {@code set}；startTracking / 进世界全量同步、clone 拷贝
 * 均已接线；</li>
 * <li>持久化：本工程全部 synced key 都是 {@code save=false}（{@code ModSyncedEntityData}），
 * 与上游一致，因此**不需要** NBT 持久化路径。</li>
 * </ul>
 * 本类只在实体进入世界时「确保 holder 存在」，不负责真正的挂接实现（与上游分工一致）。
 */
public final class SyncedEntityDataEvent {

    public static void register() {
        SyncedEntityDataEvent instance = new SyncedEntityDataEvent();
        MinecraftForge.EVENT_BUS.register(instance);
        FMLCommonHandler.instance()
            .bus()
            .register(instance);
    }

    /**
     * 上游 {@code attachCapabilities} 的 1.7.10 形态：实体进入世界时确保 {@link DataHolder} 存在。
     * 1.7.10 没有 capability，真正的 holder 创建由 entity/sync 层负责（{@code SyncedEntityData} 的
     * 静态 {@code WeakHashMap} 按需创建；见类注释「原 TODO(port) 已关闭」）。
     */
    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        Entity entity = event.entity;
        if (entity == null || !SyncedEntityData.instance()
            .hasSyncedDataKey(entity)) {
            return;
        }
        // 上游在此处挂 capability；1.7.10 由 getDataHolder 内部按需创建（契约见类注释）。
        SyncedEntityData.instance()
            .getDataHolder(entity);
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        EntityPlayer tracker = event.entityPlayer;
        if (tracker.worldObj == null || tracker.worldObj.isRemote) {
            return;
        }
        Entity entity = event.target;
        DataHolder holder = SyncedEntityData.instance()
            .getDataHolder(entity);
        if (holder == null) {
            return;
        }
        List<DataEntry<?, ?>> entries = holder.gatherAll();
        entries.removeIf(
            entry -> !entry.getKey()
                .syncMode()
                .isTracking());
        if (!entries.isEmpty() && tracker instanceof EntityPlayerMP playerMP) {
            NetworkHandler
                .sendToClientPlayer(new ServerMessageUpdateEntityData(entity.getEntityId(), entries), playerMP);
        }
    }

    @SubscribeEvent
    public void onPlayerJoinWorld(EntityJoinWorldEvent event) {
        Entity entity = event.entity;
        if (!(entity instanceof EntityPlayer player) || event.world == null || event.world.isRemote) {
            return;
        }
        DataHolder holder = SyncedEntityData.instance()
            .getDataHolder(player);
        if (holder == null) {
            return;
        }
        List<DataEntry<?, ?>> entries = holder.gatherAll();
        if (!entries.isEmpty() && player instanceof EntityPlayerMP playerMP) {
            NetworkHandler
                .sendToClientPlayer(new ServerMessageUpdateEntityData(player.getEntityId(), entries), playerMP);
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        EntityPlayer original = event.original;
        if (original == null) {
            return;
        }
        // TODO(port): 上游在拿到旧 holder 前调 original.reviveCaps()、之后 invalidateCaps()；
        // 1.7.10 无 capability，这两步已删除（旧实体此时仍可访问，见 entity/sync 层实现）。
        DataHolder oldHolder = SyncedEntityData.instance()
            .getDataHolder(original);
        if (oldHolder == null) {
            return;
        }
        EntityPlayer player = event.entityPlayer;
        DataHolder newHolder = SyncedEntityData.instance()
            .getDataHolder(player);
        if (newHolder == null) {
            return;
        }
        Map<SyncedDataKey<?, ?>, DataEntry<?, ?>> dataMap = new HashMap<>(oldHolder.dataMap);
        if (event.wasDeath) {
            dataMap.entrySet()
                .removeIf(
                    entry -> !entry.getKey()
                        .persistent());
        }
        newHolder.dataMap = dataMap;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        SyncedEntityData instance = SyncedEntityData.instance();
        if (event.side != Side.SERVER) {
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!instance.isDirty()) {
            return;
        }
        List<Entity> dirtyEntities = instance.getDirtyEntities();
        if (dirtyEntities.isEmpty()) {
            instance.setDirty(false);
            return;
        }
        for (Entity entity : dirtyEntities) {
            DataHolder holder = instance.getDataHolder(entity);
            if (holder == null || !holder.isDirty()) {
                continue;
            }
            List<DataEntry<?, ?>> entries = holder.gatherDirty();
            if (entries.isEmpty()) {
                continue;
            }
            List<DataEntry<?, ?>> selfEntries = entries.stream()
                .filter(
                    entry -> entry.getKey()
                        .syncMode()
                        .isSelf())
                .collect(Collectors.toList());
            if (!selfEntries.isEmpty() && entity instanceof EntityPlayerMP playerMP) {
                NetworkHandler
                    .sendToClientPlayer(new ServerMessageUpdateEntityData(entity.getEntityId(), selfEntries), playerMP);
            }
            List<DataEntry<?, ?>> trackingEntries = entries.stream()
                .filter(
                    entry -> entry.getKey()
                        .syncMode()
                        .isTracking())
                .collect(Collectors.toList());
            if (!trackingEntries.isEmpty()) {
                NetworkHandler.sendToTrackingEntity(
                    new ServerMessageUpdateEntityData(entity.getEntityId(), trackingEntries),
                    entity);
            }
            holder.clean();
        }
        dirtyEntities.clear();
        instance.setDirty(false);
    }
}
